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

async function shoot(context, name, prepare, path = '/') {
  const page = await context.newPage()
  await page.goto(`${base}${path}`, { waitUntil: 'networkidle' })
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

// 组局：自建一场圆桌 → 预设阵容生成 → 认领一个席位 → 截图
const api = await desktop.request
const headers = { 'X-Device-Id': deviceId }
const created = await api.post(`${base}/api/discussions`, {
  headers,
  data: { topic: '把年度订阅改成按量计费，是不是自欺欺人', panelSize: 4 },
})
const discussion = await created.json()
await api.post(`${base}/api/discussions/${discussion.id}/panel`, {
  headers,
  data: { regenerate: false, preset: true },
})
const panel = await (await api.get(`${base}/api/discussions/${discussion.id}/panel`, { headers })).json()
const seat = panel.seats.find((item) => item.roleType !== 'HOST')
if (seat) {
  await api.post(`${base}/api/discussions/${discussion.id}/claim`, {
    headers,
    data: { seatId: seat.id },
  })
}

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

await browser.close()
