import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import { ApiError } from '@/lib/api'
import { copy, errorMessage } from '@/lib/copy'
import { claimSeat, fetchDiscussion, fetchSeats } from '@/lib/discussions'
import { streamPost, type SseFrame } from '@/lib/sse'
import type { DiscussionStatus, PanelAnalysisEvent, RoleType, Seat } from '@/lib/types'

function sortByOrder(seats: Seat[]): Seat[] {
  return [...seats].sort((left, right) => left.orderIndex - right.orderIndex)
}

/** 组局页状态：席位逐个到达即渲染，落库以服务端为准。 */
export const usePanelStore = defineStore('panel', () => {
  const discussionId = ref<string | null>(null)
  const topic = ref('')
  const status = ref<DiscussionStatus>('DRAFT')
  const panelSize = ref(5)
  const createdByMe = ref(false)
  const seats = ref<Seat[]>([])
  const analysis = ref<PanelAnalysisEvent | null>(null)
  const loading = ref(false)
  const generating = ref(false)
  const regenerating = ref(false)
  const starting = ref(false)
  const claimingSeatId = ref<string | null>(null)
  const error = ref<string | null>(null)
  const fallbackAvailable = ref(false)

  let controller: AbortController | null = null

  const ready = computed(
    () => status.value === 'PANEL_READY' || status.value === 'LIVE' || status.value === 'SUMMARIZING',
  )
  const mySeatId = computed(() => seats.value.find((seat) => seat.claimedByMe)?.id ?? null)
  const hostSeat = computed(() => seats.value.find((seat) => seat.roleType === 'HOST') ?? null)

  function upsertSeat(seat: Seat): void {
    const index = seats.value.findIndex((item) => item.id === seat.id)
    if (index >= 0) {
      seats.value[index] = seat
    } else {
      seats.value = sortByOrder([...seats.value, seat])
    }
  }

  function handleFrame(frame: SseFrame): void {
    try {
      if (frame.event === 'panel.analysis') {
        analysis.value = JSON.parse(frame.data) as PanelAnalysisEvent
      } else if (frame.event === 'seat.added') {
        upsertSeat(JSON.parse(frame.data) as Seat)
      } else if (frame.event === 'panel.done') {
        const payload = JSON.parse(frame.data) as { status: DiscussionStatus }
        status.value = payload.status
      } else if (frame.event === 'panel.failed') {
        const payload = JSON.parse(frame.data) as {
          code: string
          retriable: boolean
          fallbackAvailable: boolean
        }
        error.value = errorMessage(payload.code)
        fallbackAvailable.value = payload.fallbackAvailable
      }
    } catch {
      // 单帧解析失败不影响后续帧
    }
  }

  async function init(id: string): Promise<void> {
    discussionId.value = id
    loading.value = true
    error.value = null
    try {
      const detail = await fetchDiscussion(id)
      topic.value = detail.topic
      status.value = detail.status
      panelSize.value = detail.panelSize
      createdByMe.value = detail.createdByMe
      seats.value = sortByOrder(detail.seats)
      analysis.value = detail.topicAnalysis
        ? {
            goal: detail.topicAnalysis.goal ?? '',
            disputes: detail.topicAnalysis.disputes ?? [],
            angles: detail.topicAnalysis.angles ?? [],
            audience: detail.topicAnalysis.audience ?? [],
          }
        : null
      // 空面板不再自动生成：先让发起人选择自己的席位，再生成其余 AI 席位。
    } catch (err) {
      error.value = err instanceof ApiError ? errorMessage(err.code, err.message) : copy.errors.network
    } finally {
      loading.value = false
    }
  }

  /**
   * 生成席位。userRole 非空时保留该角色给用户本人，其余席位交给 AI；
   * autoStart=true 时服务端生成完立即开场。返回是否正常收到 panel.done。
   */
  async function generate(
    regenerate: boolean,
    preset: boolean,
    userRole: RoleType | null = null,
    autoStart = false,
  ): Promise<boolean> {
    if (!discussionId.value || generating.value) {
      return false
    }
    let finished = false
    generating.value = true
    regenerating.value = regenerate
    error.value = null
    fallbackAvailable.value = false
    controller?.abort()
    controller = new AbortController()
    if (regenerate) {
      seats.value = seats.value.filter((seat) => seat.claimedByMe)
    }
    try {
      await streamPost(
        `/discussions/${discussionId.value}/panel`,
        { regenerate, preset, userRole, autoStart },
        (frame) => {
          if (frame.event === 'panel.done') {
            finished = true
          }
          handleFrame(frame)
        },
        controller.signal,
      )
      if (finished) {
        await refreshSeats()
      }
      return finished
    } catch (err) {
      if (err instanceof ApiError) {
        error.value = errorMessage(err.code, err.message)
        fallbackAvailable.value = err.code !== 'REGEN_LIMIT'
      } else {
        error.value = copy.errors.network
      }
      return false
    } finally {
      generating.value = false
      regenerating.value = false
    }
  }

  async function regenerate(): Promise<void> {
    await generate(true, false)
  }

  async function retry(): Promise<void> {
    await generate(false, false)
  }

  async function usePreset(): Promise<void> {
    seats.value = []
    await generate(false, true)
  }

  /** 选好席位后生成其余 AI 席位并自动开场。 */
  async function startWithSeat(role: RoleType | null): Promise<boolean> {
    starting.value = true
    try {
      return await generate(false, false, role, true)
    } finally {
      starting.value = false
    }
  }

  async function refreshSeats(): Promise<void> {
    if (!discussionId.value) {
      return
    }
    const panel = await fetchSeats(discussionId.value)
    seats.value = sortByOrder(panel.seats)
    status.value = panel.status as DiscussionStatus
  }

  async function claim(seatId: string | null): Promise<void> {
    if (!discussionId.value) {
      return
    }
    claimingSeatId.value = seatId ?? ''
    error.value = null
    try {
      await claimSeat(discussionId.value, seatId)
      await refreshSeats()
    } catch (err) {
      error.value = err instanceof ApiError ? errorMessage(err.code, err.message) : copy.errors.network
    } finally {
      claimingSeatId.value = null
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
    panelSize,
    createdByMe,
    seats,
    analysis,
    loading,
    generating,
    regenerating,
    starting,
    claimingSeatId,
    error,
    fallbackAvailable,
    ready,
    mySeatId,
    hostSeat,
    init,
    regenerate,
    retry,
    usePreset,
    startWithSeat,
    claim,
    refreshSeats,
    dispose,
  }
})
