import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import { ApiError } from '@/lib/api'
import { copy, errorMessage } from '@/lib/copy'
import { claimSeat, fetchDiscussion, fetchSeats } from '@/lib/discussions'
import { streamPost, type SseFrame } from '@/lib/sse'
import type { DiscussionStatus, PanelAnalysisEvent, Seat } from '@/lib/types'

function sortByOrder(seats: Seat[]): Seat[] {
  return [...seats].sort((left, right) => left.orderIndex - right.orderIndex)
}

/** 组局页状态：席位逐个到达即渲染，落库以服务端为准。 */
export const usePanelStore = defineStore('panel', () => {
  const discussionId = ref<string | null>(null)
  const topic = ref('')
  const status = ref<DiscussionStatus>('DRAFT')
  const seats = ref<Seat[]>([])
  const analysis = ref<PanelAnalysisEvent | null>(null)
  const loading = ref(false)
  const generating = ref(false)
  const regenerating = ref(false)
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
      seats.value = sortByOrder(detail.seats)
      analysis.value = detail.topicAnalysis
        ? {
            goal: detail.topicAnalysis.goal ?? '',
            disputes: detail.topicAnalysis.disputes ?? [],
            angles: detail.topicAnalysis.angles ?? [],
            audience: detail.topicAnalysis.audience ?? [],
          }
        : null
      if (seats.value.length === 0) {
        await generate(false, false)
      }
    } catch (err) {
      error.value = err instanceof ApiError ? errorMessage(err.code, err.message) : copy.errors.network
    } finally {
      loading.value = false
    }
  }

  async function generate(regenerate: boolean, preset: boolean): Promise<void> {
    if (!discussionId.value || generating.value) {
      return
    }
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
        { regenerate, preset },
        handleFrame,
        controller.signal,
      )
    } catch (err) {
      if (err instanceof ApiError) {
        error.value = errorMessage(err.code, err.message)
        fallbackAvailable.value = err.code !== 'REGEN_LIMIT'
      } else {
        error.value = copy.errors.network
      }
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
    seats,
    analysis,
    loading,
    generating,
    regenerating,
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
    claim,
    refreshSeats,
    dispose,
  }
})
