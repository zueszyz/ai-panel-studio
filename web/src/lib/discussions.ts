import { api } from './api'
import type {
  ClaimResponse,
  DiscussionDetail,
  DiscussionListResponse,
  CreateDiscussionRequest,
  PanelFailedEvent,
  PanelAnalysisEvent,
  PanelDoneEvent,
  PanelSeatEvent,
  Seat,
} from './types'

export interface ListQuery {
  status?: string
  q?: string
  mine?: boolean
  cursor?: string | null
  limit?: number
}

export function fetchDiscussions(query: ListQuery): Promise<DiscussionListResponse> {
  return api.get<DiscussionListResponse>('/discussions', {
    status: query.status,
    q: query.q,
    mine: query.mine ? 'true' : undefined,
    cursor: query.cursor ?? undefined,
    limit: query.limit,
  })
}

export function createDiscussion(payload: CreateDiscussionRequest): Promise<{ id: string }> {
  return api.post<{ id: string }>('/discussions', payload)
}

export function fetchDiscussion(id: string): Promise<DiscussionDetail> {
  return api.get<DiscussionDetail>(`/discussions/${id}`)
}

/** 软删除（仅创建者）：成功后该场从列表移除，直链返回 410。 */
export function deleteDiscussion(id: string): Promise<{ id: string; deleted: boolean }> {
  return api.del<{ id: string; deleted: boolean }>(`/discussions/${id}`)
}

export function fetchSeats(id: string): Promise<{ discussionId: string; status: string; seats: Seat[] }> {
  return api.get<{ discussionId: string; status: string; seats: Seat[] }>(`/discussions/${id}/panel`)
}

export function claimSeat(id: string, seatId: string | null): Promise<ClaimResponse> {
  return api.post<ClaimResponse>(`/discussions/${id}/claim`, { seatId })
}

/** 组局流事件：逐个席位到达即渲染。 */
export type PanelEvent =
  | { type: 'panel.analysis'; data: PanelAnalysisEvent }
  | { type: 'seat.added'; data: PanelSeatEvent }
  | { type: 'panel.done'; data: PanelDoneEvent }
  | { type: 'panel.failed'; data: PanelFailedEvent }
