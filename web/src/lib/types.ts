/** 与 docs/API.md 对齐的前端类型定义 */

export type DiscussionStatus =
  'DRAFT' | 'PANEL_READY' | 'LIVE' | 'SUMMARIZING' | 'CLOSED' | 'INTERRUPTED' | 'FAILED'

export type SeatColorToken = 'slate' | 'amber' | 'teal' | 'indigo' | 'violet' | 'rose' | 'emerald'

export type RoleType = 'HOST' | 'STRATEGIST' | 'ANALYST' | 'TECH' | 'FINANCE' | 'RESEARCHER'

export type AgentStatus = 'ACTIVE' | 'REPLACED_BY_USER' | 'RETIRED'

export interface SeatSummary {
  id: string
  name: string
  title: string
  colorToken: SeatColorToken
  status: AgentStatus
  claimedByMe: boolean
}

export interface Seat {
  id: string
  roleType: RoleType
  name: string
  title: string
  stance: string
  focus: string[]
  voice: string
  colorToken: SeatColorToken
  orderIndex: number
  status: AgentStatus
  claimedByMe: boolean
}

export interface LastMessage {
  preview: string
  speaker: string
  at: string
}

export interface DiscussionSummary {
  id: string
  topic: string
  status: DiscussionStatus
  panelSize: number
  turnIndex: number
  seats: SeatSummary[]
  lastMessage: LastMessage | null
  updatedAt: string
}

export interface DiscussionListResponse {
  items: DiscussionSummary[]
  nextCursor: string | null
}

export interface TopicAnalysis {
  goal?: string | null
  disputes: string[]
  angles: string[]
  audience?: string[]
}

export interface DiscussionDetail {
  id: string
  topic: string
  goal: string | null
  status: DiscussionStatus
  panelSize: number
  turnIndex: number
  seats: Seat[]
  topicAnalysis: TopicAnalysis | null
  mySeatId: string | null
  createdAt: string
  updatedAt: string
}

export interface CreateDiscussionRequest {
  topic: string
  panelSize?: number
}

export interface ClaimResponse {
  seatId: string
  role: 'PLAYER' | 'VIEWER'
  displayName: string
  seatStatus: AgentStatus
}

export interface PanelAnalysisEvent {
  goal?: string
  disputes: string[]
  angles: string[]
  audience?: string[]
}

export type PanelSeatEvent = Seat

export interface PanelDoneEvent {
  discussionId: string
  status: DiscussionStatus
  seats: number
  fallback?: 'PRESET'
}

export type MessageStatus = 'PENDING' | 'STREAMING' | 'DONE' | 'FAILED' | 'PARTIAL'

export type MessageRole = 'HOST' | 'EXPERT' | 'USER'

export type MessageKind = 'OPENING' | 'SPEECH' | 'DIGEST' | 'INTERRUPT' | 'SYSTEM'

/** 讨论流里的一条消息（快照与增量共用同一形状）。 */
export interface LiveMessage {
  id: string
  seatId: string | null
  seatName: string
  seatColorToken: SeatColorToken
  role: MessageRole
  kind: MessageKind
  status: MessageStatus
  roundNo: number
  content: string
  quoteMessageId: string | null
  quotePreview: string | null
  isInterrupt: number
  tokensOut: number
  latencyMs: number | null
  attempt: number
  createdAt: string
  /** 失败原因（来自 message.failed 的 code），展示在卡片内而不是只弹全局横幅 */
  error?: string | null
}

export interface RunView {
  id: string
  agentId: string | null
  messageId: string | null
  phase: 'THINKING' | 'GENERATING' | 'DONE' | 'FAILED' | null
  trigger: string | null
  roundNo: number
  durationMs: number | null
  tokens: number
  meta: Record<string, unknown>
  startedAt: string
  endedAt: string | null
}

export interface StreamSnapshot {
  discussionId: string
  topic: string
  goal: string | null
  status: DiscussionStatus
  turnIndex: number
  mySeatId: string | null
  seats: Seat[]
  messages: LiveMessage[]
  runs: RunView[]
}

export type SeatLiveStatus = 'idle' | 'thinking' | 'generating' | 'done' | 'failed'

export interface TurnStartEvent {
  turnIndex: number
  speakers: string[]
  reason: string
}

export interface AgentStatusEvent {
  seatId: string
  status: SeatLiveStatus
}

export interface MessageStartEvent {
  messageId: string
  seatId: string | null
  role: MessageRole
  kind: MessageKind
  roundNo: number
  quoteMessageId: string | null
}

export interface MessageDeltaEvent {
  messageId: string
  seq: number
  text: string
}

export interface MessageEndEvent {
  messageId: string
  content: string
  tokens: number
  latencyMs: number
  status: MessageStatus
}

export interface MessageFailedEvent {
  messageId: string
  code: string
  retriable: boolean
}

export interface PanelFailedEvent {
  code: string
  message?: string
  retriable: boolean
  fallbackAvailable: boolean
}

// ── 纪要（docs/API.md §2.9–§2.10）────────────────────────────

export type MinuteSectionKey = 'topic' | 'claims' | 'disputes' | 'consensus' | 'hypotheses' | 'nextSteps'

export interface MinuteClaim {
  seatId: string
  name: string
  points: string[]
  sourceMessageIds: string[]
}

export interface MinuteSide {
  seats: string[]
  argument: string
  sources?: string[]
}

export interface MinuteDispute {
  question: string
  sideA: MinuteSide
  sideB: MinuteSide
  unresolvedBecause?: string
}

export interface MinuteNextStep {
  action: string
  proposedBy: string
}

export interface MinuteStructured {
  topic?: string
  goal?: string
  claims?: MinuteClaim[]
  disputes?: MinuteDispute[]
  consensus?: string[]
  hypotheses?: string[]
  nextSteps?: MinuteNextStep[]
  fallback?: string
}

export interface MinuteView {
  id: string
  discussionId: string
  version: number
  status: 'GENERATING' | 'READY' | 'FAILED'
  contentMd: string
  structured: MinuteStructured
  model: string
  tokensUsed: number
  createdAt: string
  versions: number[]
}

export interface MinuteSectionEvent {
  key: MinuteSectionKey
  index: number
  md: string
}

export interface MinuteDoneEvent {
  id: string
  version: number
  tokensUsed: number
  fallback?: 'LOCAL' | null
}

export interface MinuteFailedEvent {
  code: string
  retriable: boolean
  keptVersion: number | null
}
