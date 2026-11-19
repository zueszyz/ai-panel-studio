import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import { ApiError } from '@/lib/api'
import { copy, errorMessage } from '@/lib/copy'
import { endDiscussion, fetchRuns, nextTurn, postMessage, retryMessage, startDiscussion } from '@/lib/live'
import { connectStream } from '@/lib/stream'
import type {
  AgentStatusEvent,
  DiscussionStatus,
  LiveMessage,
  MessageDeltaEvent,
  MessageEndEvent,
  MessageFailedEvent,
  MessageStartEvent,
  RunView,
  Seat,
  SeatLiveStatus,
  StreamSnapshot,
  TurnStartEvent,
} from '@/lib/types'

/**
 * 圆桌现场状态：一条 stream 连接分发到所有消息卡（按 discussionId 隔离）。
 * delta 用 rAF 合批，避免每个 token 触发一次重渲染（rules/20 第 10 条）。
 */
export const useLiveStore = defineStore('live', () => {
  const discussionId = ref<string | null>(null)
  const topic = ref('')
  const goal = ref<string | null>(null)
  const status = ref<DiscussionStatus>('PANEL_READY')
  const turnIndex = ref(0)
  const seats = ref<Seat[]>([])
  const messages = ref<LiveMessage[]>([])
  const runs = ref<RunView[]>([])
  const mySeatId = ref<string | null>(null)
  const seatStates = ref<Record<string, SeatLiveStatus>>({})
  const currentSpeakers = ref<string[]>([])
  const connection = ref<'connecting' | 'open' | 'retrying' | 'closed' | 'error'>('connecting')
  const retryAttempt = ref(0)
  const error = ref<string | null>(null)
  const sending = ref(false)
  const starting = ref(false)
  const ending = ref(false)
  const loading = ref(true)

  let stream: { close: () => void } | null = null
  let pending = new Map<string, string>()
  let frameHandle: number | null = null
  let runsHandle: number | null = null
  let runsInFlight = false

  const live = computed(() => status.value === 'LIVE')
  const closed = computed(() => status.value === 'CLOSED' || status.value === 'SUMMARIZING')
  const seatNameById = computed<Record<string, Seat>>(() => {
    const map: Record<string, Seat> = {}
    for (const seat of seats.value) {
      map[seat.id] = seat
    }
    return map
  })

  function messageById(id: string): LiveMessage | undefined {
    return messages.value.find((message) => message.id === id)
  }

  function flushDeltas(): void {
    frameHandle = null
    if (pending.size === 0) {
      return
    }
    const entries = pending
    pending = new Map()
    for (const [messageId, text] of entries) {
      const message = messageById(messageId)
      if (message) {
        message.content += text
      }
    }
  }

  function scheduleFlush(): void {
    if (frameHandle === null) {
      frameHandle = window.requestAnimationFrame(flushDeltas)
    }
  }

  /**
   * 轨迹只在快照里全量下发，回合推进后需要主动补一次，
   * 否则现场页的轨迹面板会停在上一次连接时的样子。
   */
  async function refreshRuns(): Promise<void> {
    if (!discussionId.value || runsInFlight) {
      return
    }
    runsInFlight = true
    try {
      const page = await fetchRuns(discussionId.value)
      runs.value = page.items
    } catch {
      // 轨迹刷新失败不影响主流程：快照与后续重连仍会带回全量
    } finally {
      runsInFlight = false
    }
  }

  /** 一轮里多条消息几乎同时结束，合批成一次请求。 */
  function scheduleRunsRefresh(): void {
    if (runsHandle !== null) {
      return
    }
    runsHandle = window.setTimeout(() => {
      runsHandle = null
      void refreshRuns()
    }, 400)
  }

  function handleSnapshot(snapshot: StreamSnapshot): void {
    topic.value = snapshot.topic
    goal.value = snapshot.goal
    status.value = snapshot.status
    turnIndex.value = snapshot.turnIndex
    seats.value = snapshot.seats
    messages.value = snapshot.messages
    runs.value = snapshot.runs
    mySeatId.value = snapshot.mySeatId
    // 席位状态从消息里推回来：刷新/重连后不能把"中断"显示成"等待"（FR-5 一致性）
    const states: Record<string, SeatLiveStatus> = {}
    for (const seat of snapshot.seats) {
      states[seat.id] = 'idle'
    }
    for (const message of snapshot.messages) {
      if (!message.seatId) {
        continue
      }
      if (message.status === 'STREAMING' || message.status === 'PENDING') {
        states[message.seatId] = 'generating'
      } else if (message.status === 'FAILED') {
        states[message.seatId] = 'failed'
      } else if (message.status === 'DONE') {
        states[message.seatId] = 'done'
      }
    }
    seatStates.value = states
    if (snapshot.messages.filter((message) => message.status === 'FAILED').length >= 2 && !error.value) {
      error.value = copy.live.modelUnstable
    }
  }

  /**
   * 只按 createdAt 比较，同毫秒视为相等（返回 0）——
   * 相等时按"到达顺序"追加，而到达顺序就是服务端的发布顺序。
   * 不能用 ULID 兜底：同一毫秒内随机段不保证单调，会把后发的排到前面。
   */
  function compareMessage(left: LiveMessage, right: LiveMessage): number {
    if (left.createdAt === right.createdAt) {
      return 0
    }
    return left.createdAt < right.createdAt ? -1 : 1
  }

  function upsertMessage(message: LiveMessage): void {
    const index = messages.value.findIndex((item) => item.id === message.id)
    if (index >= 0) {
      messages.value[index] = message
      return
    }
    // 用户消息的 POST 响应可能晚于专家 message.start 到达，
    // 直接 push 会让"我的提问"排在回答后面 —— 按时间 + id 落位。
    const at = messages.value.findIndex((item) => compareMessage(item, message) > 0)
    if (at < 0) {
      messages.value.push(message)
    } else {
      messages.value.splice(at, 0, message)
    }
  }

  function handleFrame(input: { type: string; data: unknown }): void {
    const { type, data } = input
    if (type === 'snapshot') {
      handleSnapshot(data as StreamSnapshot)
      loading.value = false
      return
    }
    if (type === 'discussion.updated') {
      const payload = data as { status?: DiscussionStatus; turnIndex?: number }
      if (payload.status) {
        status.value = payload.status
      }
      if (typeof payload.turnIndex === 'number') {
        turnIndex.value = payload.turnIndex
      }
      return
    }
    if (type === 'turn.start') {
      const payload = data as TurnStartEvent
      currentSpeakers.value = payload.speakers
      for (const seatId of payload.speakers) {
        seatStates.value[seatId] = 'thinking'
      }
      return
    }
    if (type === 'agent.status') {
      const payload = data as AgentStatusEvent
      seatStates.value[payload.seatId] = payload.status
      return
    }
    if (type === 'message.start') {
      const payload = data as MessageStartEvent
      const seat = payload.seatId ? seatNameById.value[payload.seatId] : null
      upsertMessage({
        id: payload.messageId,
        seatId: payload.seatId,
        seatName: seat ? seat.name : '你',
        seatColorToken: seat ? seat.colorToken : 'emerald',
        role: payload.role,
        kind: payload.kind,
        status: 'STREAMING',
        roundNo: payload.roundNo,
        content: '',
        quoteMessageId: payload.quoteMessageId,
        quotePreview: null,
        isInterrupt: payload.role === 'USER' ? 1 : 0,
        tokensOut: 0,
        latencyMs: null,
        attempt: 1,
        createdAt: new Date().toISOString(),
      })
      return
    }
    if (type === 'message.delta') {
      const payload = data as MessageDeltaEvent
      pending.set(payload.messageId, (pending.get(payload.messageId) ?? '') + payload.text)
      scheduleFlush()
      return
    }
    if (type === 'message.end') {
      const payload = data as MessageEndEvent
      const message = messageById(payload.messageId)
      if (message) {
        message.content = payload.content
        message.status = payload.status
        message.tokensOut = payload.tokens
        message.latencyMs = payload.latencyMs
      }
      scheduleRunsRefresh()
      return
    }
    if (type === 'message.failed') {
      const payload = data as MessageFailedEvent
      const message = messageById(payload.messageId)
      if (message) {
        message.status = 'FAILED'
        message.error = errorMessage(payload.code)
      }
      // PRD FR-9：连续两条失败说明模型侧不稳，值得把提示升到页面级
      const failedCount = messages.value.filter((item) => item.status === 'FAILED').length
      if (failedCount >= 2) {
        error.value = copy.live.modelUnstable
      }
      scheduleRunsRefresh()
      return
    }
    if (type === 'discussion.closed') {
      const payload = data as { status: DiscussionStatus }
      status.value = payload.status
      connection.value = 'closed'
      return
    }
    if (type === 'error') {
      const payload = data as { code: string; message?: string }
      error.value = errorMessage(payload.code, payload.message)
    }
  }

  async function open(id: string): Promise<void> {
    discussionId.value = id
    loading.value = true
    error.value = null
    stream = connectStream(id, {
      onFrame: handleFrame,
      onOpen: () => {
        connection.value = 'open'
        retryAttempt.value = 0
      },
      onRetry: (attempt) => {
        connection.value = 'retrying'
        retryAttempt.value = attempt
      },
      onClose: (reason) => {
        connection.value = reason === 'error' ? 'error' : 'closed'
        if (reason === 'error') {
          error.value = copy.live.connectionLost
        }
      },
    })
  }

  async function start(): Promise<void> {
    if (!discussionId.value || starting.value) {
      return
    }
    starting.value = true
    error.value = null
    try {
      await startDiscussion(discussionId.value)
      status.value = 'LIVE'
    } catch (err) {
      error.value = err instanceof ApiError ? errorMessage(err.code, err.message) : copy.errors.network
    } finally {
      starting.value = false
    }
  }

  async function send(content: string): Promise<boolean> {
    if (!discussionId.value || sending.value) {
      return false
    }
    const text = content.trim()
    if (!text) {
      return false
    }
    sending.value = true
    error.value = null
    try {
      await postMessage(discussionId.value, text, crypto.randomUUID())
      return true
    } catch (err) {
      error.value = err instanceof ApiError ? errorMessage(err.code, err.message) : copy.errors.network
      return false
    } finally {
      sending.value = false
    }
  }

  async function retry(messageId: string): Promise<void> {
    if (!discussionId.value) {
      return
    }
    const message = messageById(messageId)
    try {
      await retryMessage(discussionId.value, messageId)
      if (message) {
        message.status = 'STREAMING'
        message.content = ''
        message.attempt += 1
      }
      error.value = null
    } catch (err) {
      error.value = err instanceof ApiError ? errorMessage(err.code, err.message) : copy.errors.network
    }
  }

  async function requestNextRound(): Promise<void> {
    if (!discussionId.value) {
      return
    }
    try {
      await nextTurn(discussionId.value)
    } catch (err) {
      error.value = err instanceof ApiError ? errorMessage(err.code, err.message) : copy.errors.network
    }
  }

  async function end(): Promise<void> {
    if (!discussionId.value || ending.value) {
      return
    }
    ending.value = true
    try {
      await endDiscussion(discussionId.value)
      status.value = 'SUMMARIZING'
    } catch (err) {
      error.value = err instanceof ApiError ? errorMessage(err.code, err.message) : copy.errors.network
    } finally {
      ending.value = false
    }
  }

  function dispose(): void {
    stream?.close()
    stream = null
    if (frameHandle !== null) {
      window.cancelAnimationFrame(frameHandle)
      frameHandle = null
    }
    if (runsHandle !== null) {
      window.clearTimeout(runsHandle)
      runsHandle = null
    }
    pending.clear()
  }

  function clearError(): void {
    error.value = null
  }

  return {
    discussionId,
    topic,
    goal,
    status,
    turnIndex,
    seats,
    messages,
    runs,
    mySeatId,
    seatStates,
    currentSpeakers,
    connection,
    retryAttempt,
    error,
    sending,
    starting,
    ending,
    loading,
    live,
    closed,
    open,
    start,
    send,
    retry,
    requestNextRound,
    end,
    dispose,
    clearError,
  }
})
