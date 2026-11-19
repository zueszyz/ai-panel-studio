import { api } from './api'
import type { DiscussionStatus, RunView } from './types'

export interface StartResponse {
  status: DiscussionStatus
  turnIndex: number
  openingMessageId: string | null
}

export interface PostMessageResponse {
  id: string
  status: string
  enqueued: boolean
  expectedResponders: number
}

export interface TurnResponse {
  turnIndex: number
  speakers: string[]
}

export interface EndResponse {
  status: DiscussionStatus
  endedAt: string
}

export function startDiscussion(discussionId: string): Promise<StartResponse> {
  return api.post<StartResponse>(`/discussions/${discussionId}/start`)
}

export function postMessage(
  discussionId: string,
  content: string,
  clientMsgId: string,
  quoteMessageId?: string | null,
): Promise<PostMessageResponse> {
  return api.post<PostMessageResponse>(`/discussions/${discussionId}/messages`, {
    content,
    clientMsgId,
    quoteMessageId: quoteMessageId ?? null,
  })
}

export function nextTurn(discussionId: string): Promise<TurnResponse> {
  return api.post<TurnResponse>(`/discussions/${discussionId}/turns`)
}

export function endDiscussion(discussionId: string): Promise<EndResponse> {
  return api.post<EndResponse>(`/discussions/${discussionId}/end`)
}

export function fetchRuns(discussionId: string): Promise<{ items: RunView[] }> {
  return api.get<{ items: RunView[] }>(`/discussions/${discussionId}/runs`)
}

/** 单条重试：沿用同一 messageId，卡片原位回到流式态（PRD FR-9）。 */
export function retryMessage(
  discussionId: string,
  messageId: string,
): Promise<{ id: string; status: string; attempt: number }> {
  return api.post<{ id: string; status: string; attempt: number }>(
    `/discussions/${discussionId}/messages/${messageId}/retry`,
  )
}
