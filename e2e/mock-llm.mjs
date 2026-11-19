/**
 * E2E 专用的假模型服务（OpenAI 兼容）。
 *
 * 为什么不用真模型：E2E 要断言"两路同时流式""纪要六段"，真模型的延迟与内容都不可控，
 * 用例会假红。这里用固定脚本 + 可控延迟替代，服务端不加任何测试专用代码 ——
 * 只是把 LLM_BASE_URL 指过来（AGENTS.md 红线 3 的边界仍然成立：Key 只在后端）。
 *
 * 用法：node e2e/mock-llm.mjs（默认 9099，可用 MOCK_LLM_PORT 覆盖）
 */
import { createServer } from 'node:http'

const PORT = Number(process.env.MOCK_LLM_PORT ?? 9099)

/** 与 PromptTemplates 里的标记一一对应，改 Prompt 时要同步这里 */
const MARK = {
  analysis: '你是圆桌会议的策划',
  seat: 'SEAT_INDEX:',
  minutes: '你是这场圆桌会议的主笔',
  digest: '你现在是主持人做阶段收束',
  opening: '你是这场圆桌的主持人',
  speech: '你叫',
}

/** 固定阵容：姓名要真实，focus 2–4 个（SeatDraft 校验要求） */
const SEATS = [
  {
    name: '沈知远',
    title: '圆桌主持人',
    stance: '不站队，只让分歧被看见',
    focus: ['议题拆解', '节奏控制'],
    voice: '克制，只问关键问题',
    colorToken: 'slate',
  },
  {
    name: '陈立',
    title: '战略顾问',
    stance: '先算清迁移成本再谈先进性',
    focus: ['投入产出', '组织成本'],
    voice: '先结论，再给数字',
    colorToken: 'amber',
  },
  {
    name: '周敏',
    title: '行业分析师',
    stance: '同业案例比推演更可靠',
    focus: ['同业案例', '竞品动向'],
    voice: '用案例说话',
    colorToken: 'teal',
  },
  {
    name: '林恒',
    title: '技术专家',
    stance: '冷启动没有数据就没有召回',
    focus: ['冷启动', '工程可行性'],
    voice: '直给坑位',
    colorToken: 'indigo',
  },
  {
    name: '顾南',
    title: '组织顾问',
    stance: '先看谁在这件事里被改变',
    focus: ['组织阻力', '协作成本'],
    voice: '从人说回机制',
    colorToken: 'violet',
  },
  {
    name: '何蔚',
    title: '运营负责人',
    stance: '指标不涨就是没做',
    focus: ['指标口径', '落地节奏'],
    voice: '拿结果说话',
    colorToken: 'rose',
  },
]

const ANALYSIS = JSON.stringify({
  goal: '判断该不该迁移，以及在什么条件下值得迁移',
  disputes: ['冷启动数据够不够', '迁移成本多久能收回', '长尾相关性会不会掉'],
  angles: ['成本结构', '技术可行性', '同业案例', '组织承受力'],
  audience: ['技术负责人', '业务决策者'],
})

const MINUTES = JSON.stringify({
  topic: '我们该不该把推荐系统从规则引擎换成向量召回',
  goal: '判断该不该迁移，以及在什么条件下值得迁移',
  claims: [
    {
      seatId: 'unknown',
      name: '陈立',
      points: ['全量替换不划算', '先设三周判断点'],
      sourceMessageIds: [],
    },
    {
      seatId: 'unknown',
      name: '周敏',
      points: ['同业都是先双跑再灰度', '没有直接全量替换的成功案例'],
      sourceMessageIds: [],
    },
    {
      seatId: 'unknown',
      name: '林恒',
      points: ['冷启动缺行为数据', '需要相关性止损线'],
      sourceMessageIds: [],
    },
  ],
  disputes: [
    {
      question: '冷启动阶段的数据是否足以支撑向量召回',
      sideA: { seats: [], argument: '只能靠内容向量兜底，上限等于规则引擎', sources: [] },
      sideB: { seats: [], argument: '价值在第二阶段，先付影子成本换拐点', sources: [] },
      unresolvedBecause: '缺少影子流量的 A/B 数据',
    },
  ],
  consensus: ['不做全量替换，先切长尾品类', '影子流量至少跑三周再判断'],
  hypotheses: ['若三周内长尾 CTR 提升低于 3%，则方案不成立', '若相关性低于规则引擎 2 个百分点，则停止'],
  nextSteps: [
    { action: '以 5% 长尾流量开影子实验', proposedBy: '周敏' },
    { action: '补建内容向量的冷启动召回链路', proposedBy: '林恒' },
  ],
})

/** 每位席位的发言与总耗时（毫秒）：陈立故意最慢，用来证明"慢的不挡快的" */
const SPEECH = {
  陈立: {
    text: '先把迁移成本摊开：管道重写、GPU 开销、三个月的双跑人力，加起来是一笔确定的支出，而收益要到第二阶段才看得见。所以我不反对做，但必须先设一个三周的判断点，到点没数据就停。',
    totalMs: 1800,
  },
  周敏: {
    text: '同业里我跟踪到三家换了向量召回，两家半年后回滚，唯一没回滚的那家是先双跑再灰度。案例摆在这里，直接全量替换的成功率并不高。',
    totalMs: 600,
  },
  林恒: {
    text: '冷启动阶段没有行为数据，向量召回拿什么训练？只能先用内容向量兜底，而内容向量的效果上限差不多就是现在的规则引擎。要有心理准备。',
    totalMs: 240,
  },
  顾南: {
    text: '这件事真正被改变的是负责长尾品类的那两个小组：他们的考核口径、日常节奏都要重排。技术方案再漂亮，人不跟就是空转。',
    totalMs: 220,
  },
  何蔚: {
    text: '我只看一个数：长尾品类的点击率。如果三周影子期结束后这个数没动，讨论就该收尾，别再往第二阶段投人。',
    totalMs: 200,
  },
}

const OPENING =
  '今天只回答一个问题：该不该把推荐系统从规则引擎换成向量召回。陈立会盯着迁移成本，周敏会用同业案例压我们，林恒会指出冷启动的数据缺口。三十分钟内我们给出一个可执行判断。'

/** E2E-4 用：对话里出现这个短语时，让"接下来第一位发言的席位"失败一次，之后恢复正常 */
const FAIL_TRIGGER = '模拟一次失败'
let failBudget = 0
let triggerSeen = false

const DIGEST =
  '分歧点：冷启动数据够不够支撑上线。\n新出现的信息：同业两家回滚，唯一成功的是先双跑再灰度。\n下一轮焦点：影子实验的判断阈值定多少。'

function pickSeat(userPrompt) {
  const index = Number.parseInt(/SEAT_INDEX:(\d+)/.exec(userPrompt)?.[1] ?? '0', 10)
  return SEATS[index % SEATS.length]
}

function pickSpeech(userPrompt) {
  const match = /你叫([^，,]+)/.exec(userPrompt)
  const name = match?.[1]?.trim()
  return SPEECH[name] ?? {
    text: '我的判断是：这件事值得做，但要先把判断点和止损线写清楚，否则三个月后没人敢停。',
    totalMs: 200,
  }
}

function readBody(req) {
  return new Promise((resolve) => {
    const chunks = []
    req.on('data', (chunk) => chunks.push(chunk))
    req.on('end', () => {
      try {
        resolve(JSON.parse(Buffer.concat(chunks).toString('utf8')))
      } catch {
        resolve({})
      }
    })
  })
}

function json(res, content) {
  res.writeHead(200, { 'Content-Type': 'application/json; charset=utf-8' })
  res.end(
    JSON.stringify({
      id: `mock-${Date.now()}`,
      choices: [{ index: 0, message: { role: 'assistant', content }, finish_reason: 'stop' }],
      usage: { prompt_tokens: 120, completion_tokens: 60 },
    }),
  )
}

/** 按 token 切块下发，块数与总时长都可控 */
function stream(res, text, totalMs) {
  res.writeHead(200, {
    'Content-Type': 'text/event-stream; charset=utf-8',
    'Cache-Control': 'no-cache, no-transform',
    Connection: 'keep-alive',
  })
  const size = Math.max(2, Math.ceil(text.length / 8))
  const pieces = []
  for (let i = 0; i < text.length; i += size) {
    pieces.push(text.slice(i, i + size))
  }
  const gap = Math.max(8, Math.floor(totalMs / pieces.length))
  let sent = 0
  const timer = setInterval(() => {
    if (sent >= pieces.length) {
      clearInterval(timer)
      res.write('data: [DONE]\n\n')
      res.end()
      return
    }
    const payload = JSON.stringify({ choices: [{ index: 0, delta: { content: pieces[sent] } }] })
    res.write(`data: ${payload}\n\n`)
    sent += 1
  }, gap)
  res.on('close', () => clearInterval(timer))
}

const server = createServer(async (req, res) => {
  if (req.method === 'GET' && req.url?.startsWith('/v1/models')) {
    res.writeHead(200, { 'Content-Type': 'application/json' })
    res.end(JSON.stringify({ data: [{ id: 'mock-model', object: 'model' }] }))
    return
  }
  if (req.method !== 'POST' || !req.url?.includes('/chat/completions')) {
    res.writeHead(404).end()
    return
  }

  const body = await readBody(req)
  const system = body.messages?.[0]?.content ?? ''
  const user = body.messages?.[1]?.content ?? ''
  const wantsStream = body.stream === true

  if (system.includes(MARK.minutes)) {
    // 溯源要真实：从上下文里挑出本场消息 id，而不是编一个
    const ids = [...new Set(user.match(/ms_[0-9A-Za-z]{6,}/g) ?? [])].slice(0, 8)
    const payload = JSON.parse(MINUTES)
    payload.claims.forEach((claim, index) => {
      const source = ids[index] ?? ids[0]
      claim.sourceMessageIds = source ? [source] : []
    })
    payload.disputes.forEach((dispute) => {
      dispute.sideA.sources = ids[0] ? [ids[0]] : []
      dispute.sideB.sources = ids[1] ? [ids[1]] : []
    })
    json(res, JSON.stringify(payload))
    return
  }
  // SEAT_INDEX 在 user prompt 里（PromptTemplates.seatUser），不在 system
  if (user.includes(MARK.seat)) {
    json(res, JSON.stringify(pickSeat(user)))
    return
  }
  if (system.includes(MARK.analysis)) {
    json(res, ANALYSIS)
    return
  }

  if (!wantsStream) {
    json(res, DIGEST)
    return
  }
  if (system.includes(MARK.digest)) {
    stream(res, DIGEST, 160)
    return
  }
  if (system.includes(MARK.opening)) {
    stream(res, OPENING, 240)
    return
  }
  const speech = pickSpeech(system)

  // 故障注入：只失败"一条"，重试时放行 —— 正好覆盖单条重试链路
  const conversation = (body.messages ?? []).map((message) => message.content ?? '').join('\n')
  if (conversation.includes(FAIL_TRIGGER)) {
    if (!triggerSeen) {
      triggerSeen = true
      failBudget = 1
    }
    if (failBudget > 0) {
      failBudget -= 1
      res.writeHead(500, { 'Content-Type': 'application/json' })
      res.end(JSON.stringify({ error: { message: 'mock upstream failure' } }))
      return
    }
  }

  stream(res, speech.text, speech.totalMs)
})

server.listen(PORT, '127.0.0.1', () => {
  console.log(`[mock-llm] listening on http://127.0.0.1:${PORT}/v1`)
})
