/**
 * 视觉自查截图：按 rules/20 §11 生成桌面 / 窄屏 / 空态截图到 docs/screenshots。
 * 用法：node e2e/screenshot.mjs（先启动前端与后端；组局截图会自建一场圆桌）
 */
import { chromium } from '@playwright/test'
import { mkdirSync } from 'node:fs'

const base = process.env.E2E_BASE_URL ?? 'http://localhost:5173'
const outDir = 'docs/screenshots'
const deviceId = 'screenshot-device'
mkdirSync(outDir, { recursive: true })

const browser = await chromium.launch()

async function newContext(viewport) {
  const context = await browser.newContext({ viewport, locale: 'zh-CN' })
  await context.addInitScript(
    (id) => window.localStorage.setItem('panel-studio:device-id', id),
    deviceId,
  )
  return context
}

async function shoot(context, name, prepare, path = '/', waitUntil = 'networkidle') {
  const page = await context.newPage()
  // 现场页挂着一条常驻 SSE，永远不会 networkidle，只能等 DOM 就绪
  await page.goto(`${base}${path}`, { waitUntil })
  if (prepare) {
    await prepare(page)
  }
  await page.screenshot({ path: `${outDir}/${name}.png` })
  await page.close()
  console.log(`saved ${outDir}/${name}.png`)
}

const desktop = await newContext({ width: 1440, height: 900 })
const mobile = await newContext({ width: 390, height: 844 })

await shoot(desktop, '01-lobby', async (page) => {
  await page.waitForSelector('[data-testid="discussion-card"]')
})

await shoot(desktop, '01-lobby-dialog', async (page) => {
  await page.click('[data-testid="create-discussion"]')
  await page.fill('[data-testid="topic-input"]', '我们该不该把推荐系统从规则引擎换成向量召回')
  await page.waitForTimeout(200)
})

await shoot(desktop, '01-lobby-empty', async (page) => {
  await page.fill('[data-testid="lobby-search"]', '不存在的议题关键词')
  await page.waitForTimeout(900)
})

await shoot(mobile, '01-lobby-mobile', async (page) => {
  await page.waitForSelector('[data-testid="discussion-card"]')
})

// 四档断点：确认没有横向滚动条，并补 1920 / 1024 两张截图（放在自建圆桌之前，保持列表干净）
const breakpoints = [
  { width: 1920, height: 1080, name: '01-lobby-wide' },
  { width: 1440, height: 900, name: null },
  { width: 1024, height: 768, name: '01-lobby-tablet' },
  { width: 390, height: 844, name: null },
]

for (const point of breakpoints) {
  const context = await newContext({ width: point.width, height: point.height })
  const page = await context.newPage()
  await page.goto(base, { waitUntil: 'networkidle' })
  await page.waitForSelector('[data-testid="discussion-card"]')
  const overflow = await page.evaluate(
    () => document.documentElement.scrollWidth - document.documentElement.clientWidth,
  )
  if (overflow > 0) {
    console.error(`横向溢出 ${overflow}px @ ${point.width}`)
    process.exitCode = 1
  }
  if (point.name) {
    await page.screenshot({ path: `${outDir}/${point.name}.png` })
    console.log(`saved ${outDir}/${point.name}.png`)
  }
  console.log(`no horizontal overflow @ ${point.width}px`)
  await context.close()
}

// 组局：先截一张空面板的选座页，再自建一场 5 席圆桌并保留用户席位
const api = await desktop.request
const headers = { 'X-Device-Id': deviceId }
const picker = await api.post(`${base}/api/discussions`, {
  headers,
  data: { topic: '把年度订阅改成按量计费，是不是自欺欺人', panelSize: 5 },
})
const pickerDiscussion = await picker.json()

await shoot(
  desktop,
  '02-panel-picker',
  async (page) => {
    await page.waitForSelector('[data-testid="seat-picker"]')
  },
  `/discussions/${pickerDiscussion.id}/panel`,
)

const created = await api.post(`${base}/api/discussions`, {
  headers,
  data: { topic: '把年度订阅改成按量计费，是不是自欺欺人', panelSize: 5 },
})
const discussion = await created.json()
await api.post(`${base}/api/discussions/${discussion.id}/panel`, {
  headers,
  data: { regenerate: false, preset: true, userRole: 'TECH', autoStart: false },
})

await shoot(
  desktop,
  '02-panel',
  async (page) => {
    await page.waitForSelector('[data-testid="seat-card"]')
  },
  `/discussions/${discussion.id}/panel`,
)

await shoot(
  mobile,
  '02-panel-mobile',
  async (page) => {
    await page.waitForSelector('[data-testid="seat-card"]')
  },
  `/discussions/${discussion.id}/panel`,
)

// 现场：开场后截三栏（桌面）与单栏 + 横滑席位（窄屏）
await api.post(`${base}/api/discussions/${discussion.id}/start`, { headers, data: {} })

await shoot(
  desktop,
  '03-live',
  async (page) => {
    await page.waitForSelector('[data-testid="message-flow"]')
    await page.waitForTimeout(1200)
  },
  `/discussions/${discussion.id}`,
  'domcontentloaded',
)

await shoot(
  mobile,
  '03-live-mobile',
  async (page) => {
    await page.waitForSelector('[data-testid="message-flow"]')
    await page.waitForTimeout(1200)
  },
  `/discussions/${discussion.id}`,
  'domcontentloaded',
)

// 纪要：在当前这场里发一条用户关切，结束后现场生成，截图会带上参会事实
await api.post(`${base}/api/discussions/${discussion.id}/messages`, {
  headers,
  data: { content: '续费口径变化时，老客户怎么沟通？', clientMsgId: `shot-${Date.now()}` },
})
for (let attempt = 0; attempt < 100; attempt++) {
  const runs = await (await api.get(`${base}/api/discussions/${discussion.id}/runs`, { headers })).json()
  const items = runs.items ?? []
  const busy = items.some((item) => !['DONE', 'FAILED'].includes(item.phase ?? ''))
  const replied = items.some(
    (item) => item.trigger === 'USER_INTERRUPT' && ['DONE', 'FAILED'].includes(item.phase ?? ''),
  )
  if (!busy && replied) {
    break
  }
  await new Promise((done) => setTimeout(done, 200))
}
await api.post(`${base}/api/discussions/${discussion.id}/end`, { headers, data: {} })
await api.post(`${base}/api/discussions/${discussion.id}/minutes`, {
  headers: { ...headers, Accept: 'text/event-stream, application/json' },
  timeout: 60_000,
})

await shoot(
  desktop,
  '04-minutes',
  async (page) => {
    await page.waitForSelector('[data-testid="minutes-content"]')
    await page.waitForSelector('[data-testid="minutes-facts"]')
  },
  `/discussions/${discussion.id}/minutes`,
)

await shoot(
  mobile,
  '04-minutes-mobile',
  async (page) => {
    await page.waitForSelector('[data-testid="minutes-content"]')
  },
  `/discussions/${discussion.id}/minutes`,
)

await browser.close()
