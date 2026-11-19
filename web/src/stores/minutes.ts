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

  let controller: AbortController | null = null

  const canGenerate = computed(
    () => status.value === 'SUMMARIZING' || status.value === 'CLOSED' || status.value === 'INTERRUPTED',
  )
  const hasSections = computed(() => Object.keys(sections.value).length > 0)
  const versions = computed(() => view.value?.versions ?? [])

  async function init(id: string): Promise<void> {
    discussionId.value = id
    loading.value = true
    error.value = null
    notFound.value = false
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
    controller?.abort()
    controller = new AbortController()
    try {
      await generateMinutes(
        discussionId.value,
        {
          onSection: (payload) => {
            sections.value = { ...sections.value, [payload.key]: payload.md }
          },
          onDone: (payload) => {
            offline.value = payload.fallback === 'LOCAL'
          },
          onFailed: (payload) => {
            error.value = payload.keptVersion
              ? `${errorMessage(payload.code)}（仍可查看第 ${payload.keptVersion} 版）`
              : errorMessage(payload.code)
          },
        },
        controller.signal,
      )
      await loadMinutes()
    } catch (err) {
      error.value = err instanceof ApiError ? errorMessage(err.code, err.message) : copy.errors.network
    } finally {
      generating.value = false
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
    canGenerate,
    hasSections,
    versions,
    init,
    selectVersion,
    generate,
    copyMarkdown,
    dispose,
  }
})
