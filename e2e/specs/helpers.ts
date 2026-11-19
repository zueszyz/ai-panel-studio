import { expect, type APIRequestContext, type Page } from '@playwright/test'

/** 固定议题：与假模型里的文案对应，断言时可直接引用 */
export const TOPIC = '我们该不该把推荐系统从规则引擎换成向量召回'

export interface Seat {
  id: string
  name: string
  roleType: 'HOST' | 'STRATEGIST' | 'ANALYST' | 'TECH_EXPERT' | 'USER'
  claimedByMe: boolean
}

/** 走真实接口建一场圆桌，返回 id（不经过 UI，用于给用例准备数据） */
export async function createDiscussion(
  request: APIRequestContext,
  topic = TOPIC,
  panelSize = 4,
): Promise<string> {
  const response = await request.post('/api/discussions', { data: { topic, panelSize } })
  expect(response.ok(), `创建圆桌失败：${response.status()}`).toBeTruthy()
  const body = (await response.json()) as { id: string }
  return body.id
}

/** 组局接口是 SSE，Playwright 会等到连接关闭（生成结束）再返回 */
export async function generatePanel(request: APIRequestContext, id: string): Promise<void> {
  const response = await request.post(`/api/discussions/${id}/panel`, {
    data: { regenerate: false, preset: false },
    timeout: 60_000,
    headers: { Accept: 'text/event-stream, application/json' },
  })
  expect(response.ok(), `组局失败：${response.status()}`).toBeTruthy()
  const body = await response.text()
  expect(body, '组局没有正常收尾').toContain('event:panel.done')
}

export async function fetchSeats(request: APIRequestContext, id: string): Promise<Seat[]> {
  const response = await request.get(`/api/discussions/${id}/panel`)
  expect(response.ok()).toBeTruthy()
  const body = (await response.json()) as { seats: Seat[] }
  return body.seats
}

export async function startDiscussion(request: APIRequestContext, id: string): Promise<string> {
  const response = await request.post(`/api/discussions/${id}/start`)
  expect(response.ok(), `开场失败：${response.status()}`).toBeTruthy()
  const body = (await response.json()) as { openingMessageId: string }
  return body.openingMessageId
}

export async function postMessage(
  request: APIRequestContext,
  id: string,
  content: string,
  clientMsgId = `e2e-${Date.now()}`,
): Promise<string> {
  const response = await request.post(`/api/discussions/${id}/messages`, {
    data: { content, clientMsgId },
  })
  expect(response.ok(), `发言失败：${response.status()}`).toBeTruthy()
  const body = (await response.json()) as { id: string }
  return body.id
}

export async function endDiscussion(request: APIRequestContext, id: string): Promise<void> {
  const response = await request.post(`/api/discussions/${id}/end`)
  expect(response.ok(), `结束失败：${response.status()}`).toBeTruthy()
}

/**
 * 一条龙准备"已结束、有发言、可直接出纪要"的圆桌。
 * 用例共用它，避免每个 spec 重复写 6 行断言。
 */
export async function prepareEndedDiscussion(request: APIRequestContext): Promise<string> {
  const id = await createDiscussion(request)
  await generatePanel(request, id)
  await startDiscussion(request, id)
  await postMessage(request, id, '陈立、周敏，你们怎么看迁移成本？', `seed-${id}`)
  await waitForRunsSettled(request, id)
  await endDiscussion(request, id)
  return id
}

/**
 * 等"回复已经落库"。
 *
 * 不能用 /stream 来判断：那是常驻 SSE，请求永远不会结束（3s 必超时）。
 * 轨迹表是每轮结束才写，用它判断"发言已收尾"最可靠。
 */
export async function waitForRunsSettled(
  request: APIRequestContext,
  id: string,
  options: { minUserReply?: number; minRuns?: number; timeoutMs?: number } = {},
): Promise<void> {
  // minRuns 必须为 1：agent_runs 在每次发言结束时才写，空表不能当成「已收尾」，
  // 否则紧跟着发插话时上一轮还在 inflight，插话会被「席位已在发言」跳过。
  const { minUserReply = 1, minRuns = 1, timeoutMs = 40_000 } = options
  const deadline = Date.now() + timeoutMs
  let stable = 0
  let lastSignature = ''
  while (Date.now() < deadline) {
    const response = await request.get(`/api/discussions/${id}/runs`)
    const body = (await response.json()) as {
      items: { phase: string | null; trigger: string | null }[]
    }
    const busy = body.items.some((run) => !['DONE', 'FAILED'].includes(run.phase ?? ''))
    const replies = body.items.filter(
      (run) => run.trigger === 'USER_INTERRUPT' && run.phase === 'DONE',
    ).length
    const signature = `${body.items.length}:${replies}`
    stable = signature === lastSignature ? stable + 1 : 0
    lastSignature = signature
    if (body.items.length >= minRuns && !busy && replies >= minUserReply && stable >= 1) {
      return
    }
    await new Promise((done) => setTimeout(done, 300))
  }
  throw new Error('等待发言结束超时')
}

/** 进入现场页并等首屏（snapshot）渲染完成 */
export async function openLive(page: Page, id: string): Promise<void> {
  await page.goto(`/discussions/${id}`, { waitUntil: 'domcontentloaded' })
  await expect(page.getByTestId('message-flow')).toBeVisible()
  await expect(page.locator('[data-testid="message-bubble"]').first()).toBeVisible()
}
