import { copy } from './copy'

const DEVICE_ID_KEY = 'panel-studio:device-id'
const BASE = '/api'

/** 匿名身份：首屏生成并持久化，随 X-Device-Id 上送；服务端据此识别「我参与的」。 */
export function getDeviceId(): string {
  let id = window.localStorage.getItem(DEVICE_ID_KEY)
  if (!id) {
    id = crypto.randomUUID()
    window.localStorage.setItem(DEVICE_ID_KEY, id)
  }
  return id
}

export class ApiError extends Error {
  constructor(
    readonly status: number,
    readonly code: string,
    message: string,
    readonly details?: unknown,
  ) {
    super(message)
    this.name = 'ApiError'
  }
}

/** 把后端的统一错误体转成 ApiError（SSE 通道也复用这一层）。 */
export async function toApiError(response: Response): Promise<ApiError> {
  const text = await response.text()
  if (!text) {
    return new ApiError(response.status, 'INTERNAL_ERROR', copy.errors.unknown)
  }
  try {
    const body = JSON.parse(text) as ErrorBody
    return new ApiError(
      response.status,
      body.error?.code ?? 'INTERNAL_ERROR',
      body.error?.message ?? copy.errors.unknown,
      body.error?.details,
    )
  } catch {
    return new ApiError(response.status, 'INTERNAL_ERROR', copy.errors.unknown)
  }
}

function toQuery(params?: Record<string, string | number | boolean | undefined | null>): string {
  if (!params) {
    return ''
  }
  const search = new URLSearchParams()
  Object.entries(params).forEach(([key, value]) => {
    if (value !== undefined && value !== null && value !== '') {
      search.set(key, String(value))
    }
  })
  const query = search.toString()
  return query ? `?${query}` : ''
}

interface ErrorBody {
  error?: { code?: string; message?: string; details?: unknown }
}

async function request<T>(
  path: string,
  init: RequestInit & { params?: Record<string, string | number | boolean | undefined | null> } = {},
): Promise<T> {
  const { params, headers, ...rest } = init
  let response: Response
  try {
    response = await fetch(`${BASE}${path}${toQuery(params)}`, {
      ...rest,
      headers: {
        'Content-Type': 'application/json',
        'X-Device-Id': getDeviceId(),
        ...headers,
      },
    })
  } catch {
    throw new ApiError(0, 'NETWORK_ERROR', copy.errors.network)
  }

  if (response.status === 204) {
    return undefined as T
  }

  if (!response.ok) {
    throw await toApiError(response)
  }

  const text = await response.text()
  return (text ? (JSON.parse(text) as unknown) : undefined) as T
}

export const api = {
  get<T>(path: string, params?: Record<string, string | number | boolean | undefined | null>): Promise<T> {
    return request<T>(path, { method: 'GET', params })
  },
  post<T>(path: string, body?: unknown): Promise<T> {
    return request<T>(path, { method: 'POST', body: body === undefined ? undefined : JSON.stringify(body) })
  },
  del<T>(path: string): Promise<T> {
    return request<T>(path, { method: 'DELETE' })
  },
}
