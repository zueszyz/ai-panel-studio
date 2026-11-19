import { getDeviceId, toApiError } from './api'
import { ApiError } from './api'
import { copy } from './copy'

export interface StreamFrame {
  id: number | null
  type: string
  data: unknown
}

export interface StreamOptions {
  onFrame: (frame: StreamFrame) => void
  onOpen?: () => void
  onClose?: (reason: 'closed' | 'error' | 'manual') => void
  onRetry?: (attempt: number) => void
}

const RETRY_DELAYS = [1000, 2000, 4000]
/** 服务端心跳 15s 一次；超过这个时间一帧都没有，就认为连接已死（半开连接兜底） */
const IDLE_TIMEOUT_MS = 30_000
const IDLE_CHECK_MS = 5_000

/**
 * `GET /discussions/:id/stream` 的客户端：
 * EventSource 不能带请求头、不能主动 abort，所以用 fetch + ReadableStream 自解析，
 * 断线后按 1s/2s/4s 退避重连并带 Last-Event-ID 续传（rules/30 §2）。
 */
export function connectStream(discussionId: string, options: StreamOptions) {
  let closed = false
  let attempt = 0
  let lastEventId: string | null = null
  let controller: AbortController | null = null
  let retryTimer: number | null = null
  let idleTimer: number | null = null
  let lastSignalAt = Date.now()

  async function run(): Promise<void> {
    controller = new AbortController()
    try {
      const response = await fetch(`/api/discussions/${discussionId}/stream`, {
        headers: {
          Accept: 'text/event-stream',
          'X-Device-Id': getDeviceId(),
          ...(lastEventId ? { 'Last-Event-ID': lastEventId } : {}),
        },
        signal: controller.signal,
      })
      if (!response.ok) {
        throw await toApiError(response)
      }
      if (!response.body) {
        throw new ApiError(0, 'NETWORK_ERROR', copy.errors.network)
      }
      attempt = 0
      lastSignalAt = Date.now()
      options.onOpen?.()
      startIdleWatch()
      await pump(response.body)
      if (!closed) {
        scheduleRetry()
      }
    } catch (err) {
      if (closed) {
        return
      }
      // 看门狗主动断开时 controller 已被 abort，这里仍要走重连
      if ((err as Error).name === 'AbortError' && !closed && Date.now() - lastSignalAt <= IDLE_TIMEOUT_MS) {
        return
      }
      scheduleRetry()
    } finally {
      stopIdleWatch()
    }
  }

  async function pump(body: ReadableStream<Uint8Array>): Promise<void> {
    const reader = body.getReader()
    const decoder = new TextDecoder('utf-8')
    let buffer = ''
    try {
      for (;;) {
        const { value, done } = await reader.read()
        if (done) {
          return
        }
        lastSignalAt = Date.now()
        buffer = (buffer + decoder.decode(value, { stream: true })).replace(/\r\n/g, '\n')
        let boundary = buffer.indexOf('\n\n')
        while (boundary >= 0) {
          const chunk = buffer.slice(0, boundary)
          buffer = buffer.slice(boundary + 2)
          dispatch(chunk)
          boundary = buffer.indexOf('\n\n')
        }
      }
    } finally {
      reader.releaseLock()
    }
  }

  function dispatch(chunk: string): void {
    if (!chunk.trim() || chunk.startsWith(':')) {
      return
    }
    let id: number | null = null
    let type = 'message'
    const data: string[] = []
    for (const line of chunk.split('\n')) {
      if (line.startsWith('id:')) {
        const parsed = Number.parseInt(line.slice(3).trim(), 10)
        id = Number.isNaN(parsed) ? null : parsed
      } else if (line.startsWith('event:')) {
        type = line.slice(6).trim()
      } else if (line.startsWith('data:')) {
        data.push(line.slice(5).trim())
      }
    }
    if (data.length === 0) {
      return
    }
    if (id !== null) {
      lastEventId = String(id)
    }
    try {
      options.onFrame({ id, type, data: JSON.parse(data.join('\n')) })
    } catch {
      // 单帧坏数据不影响连接
    }
  }

  /**
   * 半开连接兜底：网络掉线时 fetch 的 read() 可能长时间不返回也不报错，
   * 只能靠"多久没收到任何数据"来判断。超时就主动断开，走正常重连退避。
   */
  function startIdleWatch(): void {
    stopIdleWatch()
    idleTimer = window.setInterval(() => {
      if (closed) {
        return
      }
      if (Date.now() - lastSignalAt > IDLE_TIMEOUT_MS) {
        stopIdleWatch()
        controller?.abort()
      }
    }, IDLE_CHECK_MS)
  }

  function stopIdleWatch(): void {
    if (idleTimer !== null) {
      window.clearInterval(idleTimer)
      idleTimer = null
    }
  }

  /**
   * 一直重试到页面关闭：讨论是"现场"，放弃重连等于让用户永远看不到后续发言。
   * 退避到 4s 后不再加长，避免长时间断网后恢复太慢。
   */
  function scheduleRetry(): void {
    if (closed) {
      return
    }
    const delay = RETRY_DELAYS[Math.min(attempt, RETRY_DELAYS.length - 1)]
    attempt += 1
    options.onRetry?.(attempt)
    retryTimer = window.setTimeout(() => void run(), delay)
  }

  function close(): void {
    closed = true
    stopIdleWatch()
    if (retryTimer !== null) {
      window.clearTimeout(retryTimer)
    }
    controller?.abort()
    options.onClose?.('manual')
  }

  void run()
  return { close, retryNow: () => void run() }
}
