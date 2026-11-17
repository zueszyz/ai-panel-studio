# 项目计划 · AI Panel Studio

| 项 | 内容 |
| --- | --- |
| 版本 | v1.1（Spring Boot 3.5.16 / JDK 17 / Vue 3 / SQLite；不使用容器） |
| 周期 | 72 小时（作业截止） |
| 交付 | 可运行源码 + 文档 + Git 历史（≥30 次有效提交）+ 3–5 分钟演示视频 + README |
| 组织方式 | 文档先行 → 骨架 → 组局 → 讨论 → 收敛 → 质量 |

---

## 1. 里程碑

| 阶段 | 目标 | 完成标志 | 验收方式 |
| --- | --- | --- | --- |
| **M0 文档**（当前） | 需求、架构、数据、契约定稿 | `docs/` 八份文档 + README；每条 FR 有验收标准；技术栈与版本明确；交付清单可勾选 | 对照评分维度自评 |
| **M1 骨架** | 能跑起来的空壳 | `mvn spring-boot:run` 起 8080；Flyway 建出 7 张表（SQLite 文件自动生成）；`/api/health` 200；Vue 起 5173 打通 `/api` 代理；统一异常 + JSON 日志就位 | 本机 smoke + I-1 |
| **M2 组局** | 议题 → 席位 | 创建圆桌、流式生成席位、认领、进入圆桌；刷新可恢复 | 手动走查 + I-1~I-4 |
| **M3 讨论** | 多 Agent 并行流式 | 主持人开场、回合调度、并行 delta、轨迹面板、用户插话 | E2E-1 / E2E-2 |
| **M4 收敛** | 纪要闭环 | 结束 → 六段纪要流式 → 落 SQLite → 溯源跳转 → 导出 | E2E-3 |
| **M5 质量** | 可交付 | 3 条 E2E 绿、降级用例绿、README 完整、视频录完、commit ≥30 | 全量回归 |

### 1.1 时间分配（小时）

| 阶段 | 时长 | 说明 |
| --- | --- | --- |
| M0 | 2.5 | 文档与契约（避免边写边改） |
| M1 | 5 | Maven/Flyway/MyBatis-Plus 配置、Vue 骨架、日志与异常、Actuator |
| M2 | 7 | 议题分析 prompt + 席位流式 + 认领 + 大厅/组局页 |
| M3 | 20 | 最重：Actor、调度、并行、`SseEmitter`、前端流式渲染 |
| M4 | 8 | 纪要生成、结构化、落库、溯源 UI |
| M5 | 10 | E2E、降级、视觉打磨、README、录屏 |
| 缓冲 | 5 | 模型不稳定、SQLite 调参的真实开销 |

> M3 比原计划多 2 小时：JDK 17 无虚拟线程，线程池/信号量/写锁串行化都要手工调，不能靠语言特性兜底。

---

## 2. 任务拆解（WBS）

### M1 骨架

- [ ] `server/pom.xml`：Spring Boot 3.5.16 parent、`spring-boot-starter-web/validation/actuator/test`、`mybatis-plus-spring-boot3-starter`、`flyway-core` + `flyway-database-sqlite`、`sqlite-jdbc`、`springdoc-openapi-starter-webmvc-ui`、`logstash-logback-encoder`、`wiremock-standalone(test)`
- [ ] `application.yml`（SQLite URL + Hikari + Flyway + 异步线程池 + 压缩/超时）+ `application-test.yml`
- [ ] Flyway `V1__schema.sql`（7 表）/ `V2__indexes.sql` / `V3__seed_demo.sql`
- [ ] `PanelStudioApplication`、`AsyncConfig`（`agentExecutor` + `MdcTaskDecorator`）、`MybatisPlusConfig`（分页 + `JacksonTypeHandler`）
- [ ] `GlobalExceptionHandler`（`@RestControllerAdvice` → 统一错误体）、`TraceIdFilter`（`X-Device-Id` / MDC）
- [ ] `GET /api/health`（自定义 `LlmHealthIndicator`）、`GET /api/metrics`
- [ ] Vue：`vite.config.ts`（`/api` 代理）、路由四页占位、`styles/tokens.css`（三色 + 间距 + 字号 + 中文字体栈）、`lib/copy.ts`（中文文案表）、Pinia 初始化；**不引入任何 UI 组件库**（[ADR-001](UI_ADR.md)）
- [ ] `scripts/dev.sh` / `dev.bat` / `build.sh` / `build.bat`（**不使用 Docker**）
- [ ] ESLint + Prettier（web）、Checkstyle（可选）、Playwright 脚手架

### M2 组局

- [ ] `DiscussionController`：`POST/GET /discussions`、`GET /discussions/:id`（分页/筛选/搜索）
- [ ] `LlmClient`：`WebClient` 流式解析（`data:` 帧）+ JSON 模式 + 首 token/总时长超时
- [ ] 议题分析 prompt + `JsonRepair` + 结构化校验（Jackson + 手写字段校验）
- [ ] `PanelController`：`POST /panel`（`SseEmitter` 逐席位推送）
- [ ] 预设阵容兜底（3 套，`PresetPanels`）
- [ ] `claim` 认领/撤销 + 部分唯一索引 + `409 SEAT_TAKEN`
- [ ] 前端：大厅列表（骨架屏/空态/筛选/搜索）
- [ ] 前端：组局页（议题分析 + 席位卡逐张浮现 + 换一批 + 认领）
- [ ] 前端：席位卡组件（含颜色 token、状态点、气场文案）

### M3 讨论

- [ ] `SessionActor` + `ActorRegistry`（状态、队列、inflight、`eventBuffer`、预算）
- [ ] `SpeakerPolicy`（纯函数 + 6 个单测）
- [ ] `TurnScheduler`（顺序决策 + `CompletableFuture` fan-out + 两级 `Semaphore`）
- [ ] `AgentRunner`（超时 watchdog、`Disposable.dispose()`、失败落库、单条重试）
- [ ] `ContextBuilder`（滚动摘要 + 引用装配 + 预算裁剪）
- [ ] `SseRegistry`（emitter 注册/反注册、广播、buffer 上限、resync、心跳）
- [ ] `GET /stream` + `Last-Event-ID` 续传 + `snapshot`
- [ ] `POST /messages`（幂等 + 插话优先级）、`/turns`、`/start`、`/end`
- [ ] 主持人开场 / 收束 digest prompt
- [ ] 前端：三栏布局 + 消息气泡 + 流式打字机（rAF 合批）
- [ ] 前端：`lib/sse.ts`（fetch + ReadableStream 解析、重连退避、`Last-Event-ID`）
- [ ] 前端：轨迹面板（折叠技术细节）+ 席位状态点呼吸动画
- [ ] 前端：引用徽标 + 定位高亮

### M4 收敛

- [ ] 纪要 prompt（六段结构化 + `sourceMessageIds`）
- [ ] 流式分段输出（`minutes.section`）
- [ ] 落库 + 版本化 + 失败不覆盖（`keptVersion`）
- [ ] `GET /minutes`（版本切换）
- [ ] 前端：纪要页（目录锚点、复制 Markdown、溯源跳转）
- [ ] 「阶段性小结」入口（讨论中生成非正式小结）

### M5 质量

- [ ] WireMock 桩（流式 dribble / 慢席位 / 429 / 脏 JSON / 录制回放）
- [ ] E2E-1/2/3 编写并通过
- [ ] E2E-4/5 降级与断线
- [ ] 集成 I-1~I-13（含 SQLite 并发写用例）
- [ ] 压测脚本（20 场 × 4 agent）+ `/api/metrics` 数据写进 README
- [ ] 视觉走查（三色区分、三档响应式、对比度、键盘可达）
- [ ] README 定稿（运行/架构/AI 使用说明/边界声明/与作业原文差异）
- [ ] 演示视频分镜 + 录制（3–5 分钟）
- [ ] Prompt 记录归档（PROMPT_LOG 补全）
- [ ] 导出提交历史 `docs/git-history.txt`，按 [DELIVERY](DELIVERY.md) §3 打包（排除 `node_modules/` `target/` `data/`）
- [ ] 按 [DELIVERY](DELIVERY.md) §5 模板发送邮件（简历 + 作品链接 + 视频链接 + 差异说明）

---

## 3. 提交规划（≥30 次，递增可追溯）

> 约定：`<type>(<scope>): <subject>`；一个提交只做一件事；文档与代码分开提交；不使用一次性大提交。
> `[ai]` = AI 生成后人工 Review，`[me]` = 人工撰写/改写。

| # | 提交信息 | 阶段 |
| --- | --- | --- |
| 1 | `chore: init repo with gitignore and env example` | M0 |
| 2 | `docs: add README with product overview and tech stack` `[ai]` | M0 |
| 3 | `docs(prd): define positioning, users and pain points` `[ai]` | M0 |
| 4 | `docs(prd): add functional requirements with acceptance criteria` `[ai]` | M0 |
| 5 | `docs(prd): pin tech stack constraints and no-container runtime` `[me]` | M0 |
| 6 | `docs(arch): describe layering and session actor design` `[ai]` | M0 |
| 7 | `docs(arch): add turn scheduling and sse multiplexing diagrams` `[ai]` | M0 |
| 8 | `docs(arch): document jdk17 thread model and sqlite write constraints` `[me]` | M0 |
| 9 | `docs(db): add er diagram and sqlite schema for 7 tables` `[ai]` | M0 |
| 10 | `docs(api): define 17 endpoints and sse event protocol` `[ai]` | M0 |
| 11 | `docs(test): design e2e cases and wiremock fault injection` `[ai]` | M0 |
| 12 | `docs(plan): add milestones, wbs and commit breakdown` `[ai]` | M0 |
| 13 | `chore(server): add maven project with spring boot 3.5.16` | M1 |
| 14 | `feat(db): add flyway v1 schema for 7 tables on sqlite` | M1 |
| 15 | `feat(db): add indexes including partial unique seat index` | M1 |
| 16 | `feat(db): seed demo discussion with seats and minutes` | M1 |
| 17 | `feat(server): configure hikari for sqlite wal and busy timeout` | M1 |
| 18 | `feat(server): add global exception handler with error codes` | M1 |
| 19 | `feat(server): add async executor with mdc propagation` | M1 |
| 20 | `feat(server): add health and metrics endpoints` | M1 |
| 21 | `chore(web): scaffold vue3 with vite, router and pinia` | M1 |
| 22 | `feat(web): add design tokens and three-color seat palette` | M1 |
| 23 | `feat(server): implement llm client with streaming and timeout` | M2 |
| 24 | `feat(server): add topic analysis prompt with json repair` | M2 |
| 25 | `feat(server): stream panel generation seat by seat` | M2 |
| 26 | `feat(server): add preset panel fallback` | M2 |
| 27 | `feat(web): build lobby list with filters and empty state` | M2 |
| 28 | `feat(web): add create dialog and panel page` | M2 |
| 29 | `feat(server): support seat claim with partial unique index` | M2 |
| 30 | `feat(web): add claim interaction and restore on reload` | M2 |
| 31 | `feat(server): implement session actor and speaker policy` | M3 |
| 32 | `test(server): cover speaker policy edge cases` | M3 |
| 33 | `feat(server): add turn scheduler with parallel fan-out` | M3 |
| 34 | `feat(server): isolate agent failures with watchdog and retry` | M3 |
| 35 | `feat(server): add context builder with rolling summary` | M3 |
| 36 | `feat(server): implement sse registry with multiplex and resync` | M3 |
| 37 | `feat(server): support last-event-id replay` | M3 |
| 38 | `feat(server): add user message endpoint with idempotency` | M3 |
| 39 | `feat(web): parse sse stream with fetch and readablestream` | M3 |
| 40 | `feat(web): render streaming bubbles with raf batching` | M3 |
| 41 | `feat(web): add trace panel and seat status dots` | M3 |
| 42 | `feat(web): link quotes to source messages` | M3 |
| 43 | `feat(server): generate minutes in six sections` | M4 |
| 44 | `feat(server): persist minutes with versioning` | M4 |
| 45 | `feat(web): build minutes page with export and anchors` | M4 |
| 46 | `test(e2e): add panel-to-opening flow` | M5 |
| 47 | `test(e2e): assert parallel streaming and quoting` | M5 |
| 48 | `test(e2e): verify minutes persist after reload` | M5 |
| 49 | `test: add integration cases for idempotency and sqlite concurrency` | M5 |
| 50 | `chore(test): add wiremock stubs with fault injection` | M5 |
| 51 | `fix(server): keep other agents running when one times out` | M5 |
| 52 | `style(web): tune contrast and responsive breakpoints` | M5 |
| 53 | `docs: finalize readme with runbook and ai usage notes` `[me]` | M5 |

> 规划 53 条，实际按开发节奏自然增量；最终以 `git log --oneline` 为准（≥30 条）。
> 补充：UI 方案评估单独提交一条 `docs(adr): evaluate element plus and decide to hand-write ui`，不占用上表编号。

---

## 4. 风险与应对（执行角度）

| 风险 | 触发信号 | 应对 |
| --- | --- | --- |
| 模型输出格式不稳定 | 席位 JSON 反复解析失败 | 严格校验 + 1 次修复重问 + 预设阵容兜底；宁可降级不阻塞主链路 |
| SQLite 写锁争抢 | 日志出现 `SQLITE_BUSY`，接口偶发 500 | 缩短事务（禁事务内调 LLM）、`busy_timeout=5000`、单条 UPDATE、应用层退避重试 |
| JDK 17 线程池打满 | SSE 首 token 变慢、`agentExecutor` 队列堆积 | core 8/max 16 + `CallerRunsPolicy` + 每场 `Semaphore(3)`；超时主动 `dispose()` |
| `SseEmitter` 写入排队 | 多 agent 同时输出时卡顿 | delta 60–80ms 合并发送；避免每 token 一次 `send` |
| 前端流式抖动 | 文本跳动、滚动乱 | 固定槽位 + rAF 合批 + 用户离开底部时不强制滚动 |
| E2E 不稳定 | 依赖真实模型 | 全部走 WireMock + dribble 时序可控 |
| 时间不够 | M3 超时 | 优先保"并行流式 + 纪要闭环"，砍换一批/版本切换等 P1 |

---

## 5. 完成定义（DoD）

- [ ] `mvn -f server/pom.xml test` 全绿；`pnpm -C web build` 成功；3 条 E2E 全绿（本机，无容器）
- [ ] `java -jar server/target/panel-studio.jar` 单进程可完整演示（含前端静态资源）
- [ ] 17 个接口与 API 文档一致（含错误码）；Swagger UI 可访问
- [ ] 7 张表由 Flyway 建出，纪要可从 SQLite 重新读取（重启后仍在）
- [ ] `git log` ≥ 30 条，message 可读，提交粒度合理
- [ ] README 含运行说明、架构图、AI 使用说明、边界声明与"不使用容器"的说明
- [ ] 3–5 分钟视频覆盖：大厅 → 组局 → 讨论（并行流式）→ 纪要 → 代码结构讲解
- [ ] 交付包完整：`README` + `docs/`（8 份） + `docs/git-history.txt` + 视频链接 + 与作业原文的 4 处差异说明

---

## 6. 评分维度与投入分配

| 维度 | 分值 | 本项目的主要得分动作 | 投入（小时） |
| --- | --- | --- | --- |
| 架构与工程化 | 30 | 五层分包 + 依赖方向约束；`SessionActor` 显式状态机；Flyway；统一异常与结构化日志；Actuator 与 `/metrics` | 14 |
| 并发与流式 | 25 | 顺序调度 + `CompletableFuture` fan-out；单 SSE 多路复用；两级信号量；超时 abort；`Last-Event-ID` 续传 | 20 |
| 数据与闭环 | 20 | 7 表 + 部分唯一索引；纪要版本化落库；双通道溯源；SQLite 单写者应对 | 10 |
| 前端体验 | 15 | 三色席位 + 状态点；三档响应式；流式渲染防抖动；轨迹面板折叠技术细节 | 9 |
| 文档与过程 | 10 | 8 份文档（架构图/ER/状态机/时序图）；Prompt 留痕含三次技术栈变更；commit 粒度 | 6 |

> 自检表与"主动放弃的分"见 [DELIVERY](DELIVERY.md) §6。
