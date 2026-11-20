import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import { ApiError } from '@/lib/api'
import { copy, errorMessage } from '@/lib/copy'
import { fetchDiscussion } from '@/lib/discussions'
import { fetchMinutes, generateMinutes } from '@/lib/minutes'
import type { DiscussionStatus, MinuteSectionKey, MinuteView } from '@/lib/types'

/**
 * 纪要页状态：已到达的分节先渲染，全文以数据库为准。
 * 生成失败不覆盖旧版本（后端返回 keptVersion），因此本地只清空"分节草稿"，不动 view。
 */
export const useMinutesStore = defineStore('minutes', () => {
  const discussionId = ref<string | null>(null)
  const topic = ref('')
  const status = ref<DiscussionStatus>('CLOSED')
  const view = ref<MinuteView | null>(null)
  const selectedVersion = ref<number | null>(null)
  const sections = ref<Partial<Record<MinuteSectionKey, string>>>({})
  const loading = ref(true)
  const generating = ref(false)
  const error = ref<string | null>(null)
  const offline = ref(false)
  const copied = ref(false)
  const notFound = ref(false)
  /** 当前展示的是「阶段性小结」（讨论中、不入库），还是正式纪要。 */
  const interim = ref(false)

  let controller: AbortController | null = null

  const canGenerate = computed(
    () => status.value === 'LIVE' || status.value === 'SUMMARIZING'
      || status.value === 'CLOSED' || status.value === 'INTERRUPTED',
  )
  const isLive = computed(() => status.value === 'LIVE')
  const hasSections = computed(() => Object.keys(sections.value).length > 0)
  const versions = computed(() => view.value?.versions ?? [])

  /**
   * @param auto 结束讨论后跳转过来（?auto=1）：没有纪要就自动生成，省掉再点一次「生成纪要」。
   */
  async function init(id: string, auto = false): Promise<void> {
    discussionId.value = id
    loading.value = true
    error.value = null
    notFound.value = false
    interim.value = false
    try {
      const detail = await fetchDiscussion(id)
      topic.value = detail.topic
      status.value = detail.status
    } catch (err) {
      error.value = err instanceof ApiError ? errorMessage(err.code, err.message) : copy.errors.network
      loading.value = false
      return
    }
    await loadMinutes()
    loading.value = false
    // 纪要已存在但讨论还停在"生成中"：说明上一次收敛没写回，主动对齐一次状态。
    if (view.value && status.value === 'SUMMARIZING') {
      await refreshStatus()
    }
    if (auto && notFound.value && canGenerate.value) {
      await generate()
    }
  }

  /** 以服务端为准刷新讨论状态；失败时保留事件里带的最终状态。 */
  async function refreshStatus(): Promise<void> {
    if (!discussionId.value) {
      return
    }
    try {
      const detail = await fetchDiscussion(discussionId.value)
      topic.value = detail.topic
      status.value = detail.status
    } catch {
      // 状态刷新失败不影响已生成的纪要，保留当前值
    }
  }

  async function loadMinutes(version?: number): Promise<void> {
    if (!discussionId.value) {
      return
    }
    try {
      const data = await fetchMinutes(discussionId.value, version)
      view.value = data
      selectedVersion.value = data.version
      notFound.value = false
      // 版本切换/刷新后，徽标与提示都以当前版本为准，避免沿用上一版的"离线""生成中"状态。
      error.value = null
      offline.value = data.model === 'local-fallback'
      sections.value = {}
    } catch (err) {
      if (err instanceof ApiError && err.code === 'MINUTES_NOT_FOUND') {
        view.value = null
        notFound.value = true
      } else {
        error.value = err instanceof ApiError ? errorMessage(err.code, err.message) : copy.errors.network
      }
    }
  }

  async function selectVersion(version: number): Promise<void> {
    interim.value = false
    copied.value = false
    await loadMinutes(version)
  }

  async function generate(): Promise<void> {
    if (!discussionId.value || generating.value) {
      return
    }
    generating.value = true
    error.value = null
    offline.value = false
    sections.value = {}
    interim.value = status.value === 'LIVE'
    controller?.abort()
    controller = new AbortController()
    try {
      await generateMinutes(
        discussionId.value,
        {
          onSection: (payload) => {
            sections.value = { ...sections.value, [payload.key]: payload.md }
            if (payload.interim !== undefined) {
              interim.value = payload.interim
            }
          },
          onDone: (payload) => {
            offline.value = payload.fallback === 'LOCAL'
            if (payload.interim !== undefined) {
              interim.value = payload.interim
            }
            // 正式纪要的 done 已带最终状态（CLOSED）：先落徽标，再异步对齐库里的状态。
            if (!interim.value && payload.status) {
              status.value = payload.status
            }
          },
          onFailed: (payload) => {
            error.value = payload.keptVersion
              ? `${errorMessage(payload.code)}（仍可查看第 ${payload.keptVersion} 版）`
              : errorMessage(payload.code)
          },
        },
        controller.signal,
        status.value === 'LIVE',
      )
    } catch (err) {
      error.value = err instanceof ApiError ? errorMessage(err.code, err.message) : copy.errors.network
      return
    } finally {
      // 流一结束就摘掉「正在整理纪要」：后续读库/对齐状态不应继续显示生成中。
      generating.value = false
    }
    // 阶段性小结没有落库版本，不重新拉取；正式纪要才以数据库为准刷新。
    if (!interim.value) {
      await loadMinutes()
      await refreshStatus()
    }
  }

  async function copyMarkdown(): Promise<void> {
    const markdown = view.value?.contentMd
    if (!markdown) {
      return
    }
    try {
      await navigator.clipboard.writeText(markdown)
      copied.value = true
      window.setTimeout(() => {
        copied.value = false
      }, 2000)
    } catch {
      error.value = copy.minutes.copyFailed
    }
  }

  /** 导出为 .md：离线归档或贴进文档时不必再手动复制。 */
  function downloadMarkdown(): void {
    const markdown = view.value?.contentMd
    if (!markdown) {
      return
    }
    const blob = new Blob([markdown], { type: 'text/markdown;charset=utf-8' })
    const url = URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url
    link.download = `圆桌纪要-${topic.value.slice(0, 20) || '未命名'}.md`
    link.click()
    URL.revokeObjectURL(url)
  }

  function dispose(): void {
    controller?.abort()
    controller = null
  }

  return {
    discussionId,
    topic,
    status,
    view,
    selectedVersion,
    sections,
    loading,
    generating,
    error,
    offline,
    copied,
    notFound,
    interim,
    isLive,
    canGenerate,
    hasSections,
    versions,
    init,
    refreshStatus,
    selectVersion,
    generate,
    copyMarkdown,
    downloadMarkdown,
    dispose,
  }
})
