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

export interface PanelFailedEvent {
  code: string
  message?: string
  retriable: boolean
  fallbackAvailable: boolean
}
