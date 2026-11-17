import { getDeviceId, toApiError } from './api'
import { copy } from './copy'
import { ApiError } from './api'

export interface SseFrame {
  event: string
  data: string
}

function parseFrame(chunk: string): SseFrame | null {
  const lines = chunk.split('\n')
  let event = 'message'
  const data: string[] = []
  for (const line of lines) {
    if (line.startsWith('event:')) {
      event = line.slice(6).trim()
    } else if (line.startsWith('data:')) {
      data.push(line.slice(5).trim())
    }
  }
  if (data.length === 0) {
    return null
  }
  return { event, data: data.join('\n') }
}

/**
 * POST + 自解析 SSE（EventSource 不能设置请求头，也不能 POST）。
 * 帧按 \n\n 切分；跨 chunk 截断的行会留在 buffer 里等下一次读取。
 */
export async function streamPost(
  path: string,
  body: unknown,
  onFrame: (frame: SseFrame) => void,
  signal?: AbortSignal,
): Promise<void> {
  const response = await fetch(`/api${path}`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      // 同时接受 JSON：错误分支（429/409）需要按统一错误体解析
      Accept: 'text/event-stream, application/json',
      'X-Device-Id': getDeviceId(),
    },
    body: JSON.stringify(body ?? {}),
    signal,
  })

  if (!response.ok) {
    throw await toApiError(response)
  }
  if (!response.body) {
    throw new ApiError(0, 'NETWORK_ERROR', copy.errors.network)
  }

  const reader = response.body.getReader()
  const decoder = new TextDecoder('utf-8')
  let buffer = ''

  try {
    for (;;) {
      const { value, done } = await reader.read()
      if (done) {
        break
      }
      buffer = (buffer + decoder.decode(value, { stream: true })).replace(/\r\n/g, '\n')
      let boundary = buffer.indexOf('\n\n')
      while (boundary >= 0) {
        const chunk = buffer.slice(0, boundary)
        buffer = buffer.slice(boundary + 2)
        const frame = parseFrame(chunk)
        if (frame) {
          onFrame(frame)
        }
        boundary = buffer.indexOf('\n\n')
      }
    }
    const tail = parseFrame(buffer)
    if (tail) {
      onFrame(tail)
    }
  } catch (err) {
    if ((err as Error).name === 'AbortError') {
      return
    }
    throw err
  } finally {
    reader.releaseLock()
  }
}
