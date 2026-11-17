# AI Panel Studio · AI 圆桌讨论 Web App

> 输入一个议题，召集一场由 AI 专家组成、你可以随时插话的圆桌讨论。
> 不是"问一句答一句"，而是**主持人开场 → 专家并行发言 → 相互质疑 → 你加入辩论 → 收敛成纪要**。

![status](https://img.shields.io/badge/status-docs%20phase-yellow) ![stack](https://img.shields.io/badge/stack-TypeScript%20%2B%20Node.js-blue) ![db](https://img.shields.io/badge/db-PostgreSQL%2016-336791) ![license](https://img.shields.io/badge/license-MIT-green)

---

## 1. 这是什么

`AI Panel Studio` 是一个前后端分离的 AI 圆桌讨论应用。

用户输入讨论议题并设定参会规模（默认 4 人：1 位主持人 + 3 位专家），系统调用大模型生成一组立场互补的专家角色（战略顾问 / 行业分析师 / 技术专家 …），用户可以认领其中一个席位加入讨论，也可以纯旁观。讨论由主持人开场并串联，专家之间可以互相反驳，用户可随时打断、追问、质疑。讨论结束后，系统按角色聚合观点，产出一份可溯源的结构化会议纪要并落库。

**它要解决的不是"答案不够好"，而是"答案太单一"**：单 AI 对话只有一个结论，缺少对抗性；多 Agent 圆桌把分歧显式地摆到桌面上，让判断有对比、有出处、可复盘。

## 2. 核心能力

| 能力 | 说明 |
| --- | --- |
| 圆桌大厅 | 正在进行的讨论列表，可旁观、可加入、可发起新议题 |
| 专家生成 | 根据议题实时生成 1 位主持人 + N 位专家，带角色立场与发言风格，流式呈现 |
| 认领席位 | 用户可认领某位专家席位（替换其发言权）或纯旁观 |
| 多轮自由讨论 | 不设轮次上限，主持人开场/串联，专家并行发言，用户随时插话 |
| Agent 行动轨迹 | 实时时间线：谁在思考、谁在发言、谁在等谁，技术细节可折叠 |
| 观点溯源 | 每条观点标注「发言者 + 轮次 + 时间」，可跳回上下文 |
| 会议纪要 | 讨论结束后按角色结构化输出，含分歧点与待验证假设，JSON 落库可复盘 |
| 全程流式 | SSE 单连接多路复用，多 Agent 并行输出互不阻塞，单个 Agent 失败不拖垮全场 |

## 3. 技术选型

| 层 | 选型 | 理由 |
| --- | --- | --- |
| 前端 | 原生 TypeScript + 手写 CSS（无 UI 框架 / 无组件库） | 硬性约束；手写保证视觉与交互可控 |
| 后端 | Node.js 22 + TypeScript + Fastify | 长连接与流式 IO 友好，类型贯穿前后端 |
| 实时通道 | SSE（`text/event-stream`） | 单向推送足够，比 WS 更易穿透代理、易调试 |
| 编排 | 自研 Orchestrator（回合调度 + 并行 fan-out） | 多 Agent 调度是本题核心考点，不交给框架黑盒 |
| 数据 | PostgreSQL 16 + Prisma | 需要 JSONB 存结构化纪要，事务与索引更接近生产 |
| 测试 | Vitest（单测）+ Playwright（E2E） | 覆盖 3 条端到端主链路 |
| 部署 | Docker Compose（web + db 一键起） | 交付要求 |

选型取舍详见 [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md)。

## 4. 目录结构

```text
ai-panel-studio/
├── docs/                     # 全部设计与交付文档（先于代码）
│   ├── PRD.md                # 产品需求文档
│   ├── ARCHITECTURE.md       # 技术架构与并发/流式设计
│   ├── DATABASE.md           # 数据库设计 + ER 图
│   ├── API.md                # 接口契约 + SSE 事件协议
│   ├── TESTING.md            # E2E 用例与验收清单
│   ├── PROJECT_PLAN.md       # 任务拆解、里程碑、commit 规划
│   └── PROMPT_LOG.md         # Prompt / 工具调用留痕
├── apps/
│   ├── web/                  # 前端（原生 TS + CSS）
│   └── server/               # 后端（Fastify + Orchestrator）
├── packages/shared/          # 前后端共享类型与 SSE 事件定义
├── db/                       # schema、迁移、seed
├── e2e/                      # Playwright 用例
├── docker-compose.yml
└── README.md
```

> 当前处于 **docs 阶段**：`apps/` 与代码尚未落地，文档先行是为了把"需求拆解 → 契约 → 数据模型"固化下来，后续编码严格按契约推进。

## 5. 快速开始（代码落地后可用）

```bash
git clone <repo-url> ai-panel-studio && cd ai-panel-studio
cp .env.example .env          # 填入 LLM_API_KEY / LLM_BASE_URL / DATABASE_URL
docker compose up -d          # 一键起 db + server
pnpm db:push && pnpm db:seed  # 初始化 7 张表与演示数据
pnpm dev                      # web:5173  server:8787
```

本地不依赖 Docker 时：

```bash
pnpm i && pnpm db:up && pnpm dev
```

## 6. 文档索引

| 文档 | 你会看到什么 |
| --- | --- |
| [PRD](docs/PRD.md) | 定位、用户与痛点、功能需求 FR-1~FR-9（含验收标准）、非功能需求、状态机、风险 |
| [架构](docs/ARCHITECTURE.md) | 分层图、回合调度时序、SSE 多路复用、失败隔离与降级、可观测性 |
| [数据库](docs/DATABASE.md) | ER 图、7 张表字段字典、索引与状态枚举、初始化脚本说明 |
| [API](docs/API.md) | 12 个接口契约、SSE 事件表、错误码 |
| [测试](docs/TESTING.md) | 3 条 E2E 主链路 + 降级用例 + 验收清单 |
| [计划](docs/PROJECT_PLAN.md) | 里程碑、任务拆解、30+ commit 切分方案 |
| [留痕](docs/PROMPT_LOG.md) | Prompt 记录模板、AI 生成 / 人工修改边界 |

## 7. AI 工具使用说明

本项目在 `CodeBuddy` 内完成，全程保留 Prompt 与工具调用记录（见 [PROMPT_LOG.md](docs/PROMPT_LOG.md)）。原则：

- **文档与契约由 AI 起草、人工评审后定稿**；需求取舍、并发模型、失败策略以人工判断为准。
- **代码由 AI 生成后逐文件 Review**，关键路径（Orchestrator 调度、SSE 生命周期、事务边界）人工改写并补测试。
- 每份文档与每个模块在 commit message 中标注是 `ai-gen` / `ai-gen+human-edit` / `human`。

## 8. License

MIT
