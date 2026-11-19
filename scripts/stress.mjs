/**
 * 轻量压测：默认 20 场 × 4 席位，走真实 HTTP 接口，不依赖模型密钥（组局用预设阵容）。
 *
 *   node scripts/stress.mjs
 *   API_BASE=http://127.0.0.1:8080 ROOMS=20 PANEL_SIZE=4 node scripts/stress.mjs
 *
 * 前置：后端已启动（scripts/dev.* 或 java -jar）。结束后打印 /api/metrics 快照，
 * 用于把真实指标写进 README（PROJECT_PLAN M5 交付项）。
 */
import { randomUUID } from 'node:crypto'

const BASE = (process.env.API_BASE ?? 'http://127.0.0.1:8080').replace(/\/$/, '')
const ROOMS = Number.parseInt(process.env.ROOMS ?? '20', 10)
const PANEL_SIZE = Number.parseInt(process.env.PANEL_SIZE ?? '4', 10)
const TOPIC = process.env.TOPIC ?? '我们该不该把推荐系统从规则引擎换成向量召回'
const SETTLE_TIMEOUT_MS = 40_000

async function api(path, { method = 'GET', device, body } = {}) {
  const response = await fetch(`${BASE}/api${path}`, {
    method,
    headers: {
      'Content-Type': 'application/json',
      Accept: 'text/event-stream, application/json',
      ...(device ? { 'X-Device-Id': device } : {}),
    },
    body: body === undefined ? undefined : JSON.stringify(body),
  })
  if (!response.ok) {
    throw new Error(`${method} ${path} → ${response.status} ${await response.text()}`)
  }
  return response
}

async function waitSettled(id, timeoutMs = SETTLE_TIMEOUT_MS) {
  const deadline = Date.now() + timeoutMs
  while (Date.now() < deadline) {
    const response = await api(`/discussions/${id}/runs`)
    const body = await response.json()
    const items = Array.isArray(body.items) ? body.items : []
    const busy = items.some((run) => !['DONE', 'FAILED'].includes(run.phase ?? ''))
    // 无模型密钥时发言会以 FAILED 收尾：只要该插话有终态轨迹就算这轮完成。
    const replied = items.some((run) => run.trigger === 'USER_INTERRUPT'
      && ['DONE', 'FAILED'].includes(run.phase ?? ''))
    if (items.length > 0 && !busy && replied) {
      return
    }
    await new Promise((done) => setTimeout(done, 300))
  }
  throw new Error(`讨论 ${id} 在 ${timeoutMs}ms 内没有收尾`)
}

async function runRoom(index) {
  const device = randomUUID()
  const started = performance.now()
  const created = await api('/discussions', {
    method: 'POST',
    device,
    body: { topic: TOPIC, panelSize: PANEL_SIZE },
  })
  const { id } = await created.json()

  // preset 阵容不调模型：压测只考察并发调度与 SQLite 写锁，不把模型延迟混进来。
  await api(`/discussions/${id}/panel`, {
    method: 'POST',
    device,
    body: { regenerate: false, preset: true },
  }).then((response) => response.text())

  await api(`/discussions/${id}/start`, { method: 'POST', device })
  await api(`/discussions/${id}/messages`, {
    method: 'POST',
    device,
    body: { clientMsgId: `stress-${index}-${Date.now()}`, content: '从成本、数据和落地三个角度各说一句。' },
  })
  await waitSettled(id)
  return performance.now() - started
}

const health = await fetch(`${BASE}/api/health`)
if (!health.ok) {
  throw new Error(`后端不可达：${BASE}/api/health → ${health.status}。先启动后端再跑压测。`)
}

console.log(`压测开始：${ROOMS} 场 × ${PANEL_SIZE} 席位，目标 ${BASE}`)
const listStart = performance.now()
const list = await api('/discussions', { method: 'GET', device: randomUUID() }).then((r) => r.json())
const listMs = performance.now() - listStart
console.log(`列表首屏：${list.items.length} 条，${listMs.toFixed(0)}ms`)

const latencies = []
const parallel = Math.min(ROOMS, 4)
for (let offset = 0; offset < ROOMS; offset += parallel) {
  const batch = Array.from({ length: Math.min(parallel, ROOMS - offset) }, (_, i) => runRoom(offset + i))
  const done = await Promise.all(batch)
  latencies.push(...done)
  console.log(`  已完成 ${latencies.length}/${ROOMS} 场`)
}

latencies.sort((a, b) => a - b)
const p95 = latencies[Math.max(0, Math.ceil(0.95 * latencies.length) - 1)]
const metrics = await api('/metrics').then((r) => r.json())
console.log(`单场耗时 P50=${latencies[Math.floor(latencies.length / 2)].toFixed(0)}ms P95=${p95.toFixed(0)}ms`)
console.log('metrics:', JSON.stringify(metrics))
