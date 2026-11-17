# 项目计划 · AI Panel Studio

| 项 | 内容 |
| --- | --- |
| 版本 | v1.0 |
| 周期 | 72 小时（作业截止） |
| 交付 | 可运行源码 + 文档 + Git 历史（≥30 次有效提交）+ 3–5 分钟演示视频 |
| 组织方式 | 文档先行 → 骨架 → 组局 → 讨论 → 收敛 → 质量 |

---

## 1. 里程碑

| 阶段 | 目标 | 完成标志 | 验收人 |
| --- | --- | --- | --- |
| **M0 文档**（当前） | 需求、架构、数据、契约定稿 | `docs/` 六份文档 + README，PRD 每条 FR 有验收标准 | 自评 + 对照评分维度 |
| **M1 骨架** | 能跑起来的空壳 | `docker compose up` 起 web+db+server；`/api/health` 200；Prisma 迁移生成 7 张表；日志与错误中间件就位 | 本地 smoke |
| **M2 组局** | 议题 → 席位 | 创建圆桌、流式生成席位、认领、进入圆桌；刷新可恢复 | 手动走查 + I-1~I-4 |
| **M3 讨论** | 多 Agent 并行流式 | 主持人开场、回合调度、并行 delta、轨迹面板、插话 | E2E-1 / E2E-2 |
| **M4 收敛** | 纪要闭环 | 结束 → 六段纪要流式 → 落库 → 溯源跳转 → 导出 | E2E-3 |
| **M5 质量** | 可交付 | 3 条 E2E 绿、降级用例绿、README 完整、视频录完、commit ≥30 | 全量回归 |

### 1.1 时间分配（小时）

| 阶段 | 时长 | 说明 |
| --- | --- | --- |
| M0 | 2.5 | 文档与契约（避免边写边改） |
| M1 | 5 | compose/迁移/日志/错误中间件/CI |
| M2 | 7 | 议题分析 prompt + 席位生成流式 + 认领 |
| M3 | 18 | 最重：Actor、调度、并行、SSE、前端流式渲染 |
| M4 | 8 | 纪要生成、结构化、落库、溯源 UI |
| M5 | 10 | E2E、降级、视觉打磨、README、录屏 |
| 缓冲 | 5 | 模型不稳定的真实开销 |

---

## 2. 任务拆解（WBS）

### M1 骨架

- [ ] monorepo（pnpm workspace：`apps/web` `apps/server` `packages/shared`）+ tsconfig base
- [ ] `docker-compose.yml`（web / server / db）+ `.env.example`
- [ ] Prisma schema（7 表）+ 首次 migration + seed
- [ ] Fastify 启动：`config`、`logger`（pino）、`errorHandler`、`requestId`
- [ ] `GET /api/health`、`GET /api/metrics` 骨架
- [ ] 前端 Vite + 路由（3 路由占位）+ CSS token（配色/间距/字号）
- [ ] ESLint + Prettier + Vitest + Playwright 脚手架

### M2 组局

- [ ] `POST /discussions`、`GET /discussions`（分页/筛选/搜索）
- [ ] `GET /discussions/:id`
- [ ] LLM Adapter（流式 + JSON 模式 + 超时 + 重试）
- [ ] 议题分析 prompt（一次结构化输出）+ zod 校验 + 修复器
- [ ] 席位生成（逐席位流式 `seat.added`）
- [ ] 预设阵容兜底（3 套）
- [ ] 前端：大厅列表/骨架/空态/发起弹层
- [ ] 前端：组局页（议题分析 + 席位卡浮现 + 换一批）
- [ ] `claim` 认领与撤销 + 部分唯一索引
- [ ] 前端：认领交互与状态保持

### M3 讨论

- [ ] `SessionActor`（状态、队列、inflight、eventBuffer）
- [ ] `SpeakerPolicy`（纯函数 + 单测 6 例）
- [ ] `TurnScheduler`（顺序决策 + `Promise.allSettled` fan-out + 信号量）
- [ ] `AgentRunner`（超时 AbortController、失败落库、重试）
- [ ] `ContextBuilder`（滚动摘要 + 引用装配 + 预算裁剪）
- [ ] SSE Hub（多路复用、buffer 上限、resync、心跳）
- [ ] `GET /stream` + `Last-Event-ID` 续传 + snapshot
- [ ] `POST /messages`（幂等 + 插话优先级）
- [ ] `POST /turns`、`POST /start`、`POST /end`
- [ ] 主持人开场 / 收束 digest prompt
- [ ] 前端：三栏布局 + 消息气泡 + 流式打字机（rAF 合批）
- [ ] 前端：SSEClient（重连/续传/分发）+ store
- [ ] 前端：轨迹面板（折叠技术细节）+ 席位状态点
- [ ] 前端：引用徽标 + 定位高亮

### M4 收敛

- [ ] 纪要 prompt（六段结构化 + `sourceMessageIds`）
- [ ] 流式分段输出（`minutes.section`）
- [ ] 落库 + 版本化 + 失败不覆盖
- [ ] `GET /minutes`（版本切换）
- [ ] 前端：纪要页（目录锚点、复制 Markdown、溯源跳转）
- [ ] 「阶段性小结」入口（讨论中生成非正式小结）

### M5 质量

- [ ] LLM Mock 服务（录制/回放/故障注入）
- [ ] E2E-1/2/3 编写并通过
- [ ] E2E-4/5 降级与断线
- [ ] 集成测试 I-1~I-12
- [ ] 压测脚本（20 场 × 4 agent）+ `/metrics` 数据入 README
- [ ] 视觉走查（三色区分、响应式断点、对比度）
- [ ] README 定稿（运行/架构/AI 使用说明/边界声明）
- [ ] 演示视频分镜 + 录制（3–5 分钟）
- [ ] Prompt 记录归档（PROMPT_LOG 补全）

---

## 3. 提交规划（≥30 次，递增可追溯）

> 约定：`<type>(<scope>): <subject>`；每个提交只做一件事；文档与代码分开提交；**不使用一次性大提交**。
> 标注 `[ai]` 表示主要由 AI 生成后经人工 Review；`[me]` 表示人工撰写/改写。

| # | 提交信息 | 阶段 |
| --- | --- | --- |
| 1 | `chore: init repo with gitignore and editor config` | M0 |
| 2 | `docs: add README with product overview and doc index` `[ai]` | M0 |
| 3 | `docs(prd): define positioning, users and pain points` `[ai]` | M0 |
| 4 | `docs(prd): add functional requirements with acceptance criteria` `[ai]` | M0 |
| 5 | `docs(prd): add state machine, NFR and risks` `[me]` | M0 |
| 6 | `docs(arch): describe layering and session actor design` `[ai]` | M0 |
| 7 | `docs(arch): add turn scheduling and SSE multiplexing diagrams` `[ai]` | M0 |
| 8 | `docs(db): add ER diagram and 7-table schema design` `[ai]` | M0 |
| 9 | `docs(api): define 15 endpoints and SSE event protocol` `[ai]` | M0 |
| 10 | `docs(test): design 3 E2E cases and 12 integration cases` `[ai]` | M0 |
| 11 | `chore: scaffold pnpm workspace with web/server/shared` | M1 |
| 12 | `chore(docker): add compose for web, server and postgres` | M1 |
| 13 | `feat(db): add prisma schema for 7 tables with migration` | M1 |
| 14 | `feat(server): bootstrap fastify with logger and error handler` | M1 |
| 15 | `feat(server): add health and metrics endpoints` | M1 |
| 16 | `feat(web): add vite shell and design tokens` | M1 |
| 17 | `feat(web): add three-route skeleton and layout` | M1 |
| 18 | `feat(server): implement llm adapter with streaming and timeout` | M2 |
| 19 | `feat(server): add topic analysis prompt with zod repair` | M2 |
| 20 | `feat(server): stream panel generation seat by seat` | M2 |
| 21 | `feat(server): add preset panel fallback` | M2 |
| 22 | `feat(web): build lobby list with filters and empty state` | M2 |
| 23 | `feat(web): add create dialog and panel page` | M2 |
| 24 | `feat(server): support seat claim with partial unique index` | M2 |
| 25 | `feat(web): add claim interaction and restore on reload` | M2 |
| 26 | `feat(server): implement session actor and speaker policy` | M3 |
| 27 | `test(server): cover speaker policy edge cases` | M3 |
| 28 | `feat(server): add turn scheduler with parallel fan-out` | M3 |
| 29 | `feat(server): isolate agent failures with abort and retry` | M3 |
| 30 | `feat(server): add context builder with rolling summary` | M3 |
| 31 | `feat(server): implement sse hub with multiplex and resync` | M3 |
| 32 | `feat(server): support last-event-id replay` | M3 |
| 33 | `feat(server): add user message endpoint with idempotency` | M3 |
| 34 | `feat(web): render streaming bubbles with rAF batching` | M3 |
| 35 | `feat(web): add sse client with reconnect backoff` | M3 |
| 36 | `feat(web): add trace panel and seat status dots` | M3 |
| 37 | `feat(web): link quotes to source messages` | M3 |
| 38 | `feat(server): generate minutes in six sections` | M4 |
| 39 | `feat(server): persist minutes with versioning` | M4 |
| 40 | `feat(web): build minutes page with export and anchors` | M4 |
| 41 | `test(e2e): add panel-to-opening flow` | M5 |
| 42 | `test(e2e): assert parallel streaming and quoting` | M5 |
| 43 | `test(e2e): verify minutes persist after reload` | M5 |
| 44 | `test: add integration cases for idempotency and versions` | M5 |
| 45 | `chore(test): add llm mock server with fault injection` | M5 |
| 46 | `fix(server): keep other agents running when one times out` | M5 |
| 47 | `style(web): tune contrast and responsive breakpoints` | M5 |
| 48 | `docs: finalize readme with runbook and ai usage notes` `[me]` | M5 |

> 实际提交按开发节奏自然增量；上表为规划基准，最终以 `git log --oneline` 为准（≥30 条）。

---

## 4. 风险与应对（执行角度）

| 风险 | 触发信号 | 应对 |
| --- | --- | --- |
| 模型输出格式不稳定 | 席位 JSON 解析反复失败 | 严格 schema + 1 次修复重问 + 预设兜底；宁可降级也不阻塞主链路 |
| 流式在前端抖动 | 文本跳动、卡顿 | rAF 合批 + 固定槽位 + 打字机限速 |
| 并行导致 provider 限流 | 429 增多 | 每场信号量 3、全局 20、失败退避 |
| E2E 不稳定 | 依赖真实模型 | 全部走 mock + 时序可控 |
| 时间不够 | M3 超时 | 优先保"并行流式 + 纪要闭环"，砍换一批/版本切换等 P1 |

---

## 5. 完成定义（DoD）

- [ ] `docker compose up` 后 3 条 E2E 全绿
- [ ] 15 个接口与 API 文档一致（含错误码）
- [ ] 7 张表有 migration 与 seed，纪要可从 DB 重新读取
- [ ] `git log` ≥ 30 条，message 可读，提交粒度合理
- [ ] README 含运行说明、架构图、AI 使用说明与边界声明
- [ ] 3–5 分钟视频覆盖：大厅 → 组局 → 讨论（并行流式）→ 纪要 → 代码结构讲解
