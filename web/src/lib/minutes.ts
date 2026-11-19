import { api } from './api'
import { streamPost } from './sse'
import type { MinuteDoneEvent, MinuteFailedEvent, MinuteSectionEvent, MinuteView } from './types'

export function fetchMinutes(discussionId: string, version?: number): Promise<MinuteView> {
  return api.get<MinuteView>(`/discussions/${discussionId}/minutes`, { version })
}

export interface MinuteStreamHandlers {
  onSection: (payload: MinuteSectionEvent) => void
  onDone: (payload: MinuteDoneEvent) => void
  onFailed: (payload: MinuteFailedEvent) => void
}

/** 纪要生成：分节到达即渲染，失败时后端给出 keptVersion（旧版本不覆盖）。 */
export async function generateMinutes(
  discussionId: string,
  handlers: MinuteStreamHandlers,
  signal?: AbortSignal,
  interim = false,
): Promise<void> {
  await streamPost(
    `/discussions/${discussionId}/minutes${interim ? '?interim=true' : ''}`,
    {},
    (frame) => {
      let payload: unknown
      try {
        payload = JSON.parse(frame.data)
      } catch {
        return
      }
      if (frame.event === 'minutes.section') {
        handlers.onSection(payload as MinuteSectionEvent)
      } else if (frame.event === 'minutes.done') {
        handlers.onDone(payload as MinuteDoneEvent)
      } else if (frame.event === 'minutes.failed') {
        handlers.onFailed(payload as MinuteFailedEvent)
      }
    },
    signal,
  )
}
