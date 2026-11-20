# Prompt 与工具调用留痕 · AI Panel Studio

> 要求：保留每一次 Prompt / 工具调用记录，并在 README 中说明「哪些是 AI 生成、哪些是自己写的、自己改了哪些」。
> 本文档为**记录模板 + 已发生的记录**；开发过程中每条重要对话向下追加，不删不改。
>
> 2026-11-20 口径调整：按最新交付要求，移除已取消的演示类交付表述；技术决策、Prompt 与验证记录保持不变。

---

## 1. 记录规范

- **粒度**：一个"意图"一条记录（如"设计 SSE 事件协议"），而非每次敲回车一条。
- **必填七项**（对应原需求对 Prompt 记录的要求）：
  1. 时间 / 阶段；
  2. 目标（这次要解决什么）；
  3. 实际 Prompt（可脱敏，保留关键约束词）；
  4. 产出物（落到哪个文件/哪一节）；
  5. 人工干预 `[ai-gen] / [ai-gen+edit] / [me]`，写清"改了哪里、为什么"；
  6. **验证方式**（跑了什么测试 / 怎么手工确认）；
  7. 结论（含遗留问题）。
- **多轮过程**：每条记录按「首次需求 → Prompt 设计 → AI 输出 → 人工评审修改 → 最终产物」五段呈现，不写"一句话结论"。
- **标注**：`[ai-gen]` 直接采用 · `[ai-gen+edit]` 生成后人工改写 · `[me]` 人工撰写。
- **脱敏**：不记录 API Key、个人身份信息。
- **失败也要记**：AI 走偏、编造、上下文遗忘的案例统一进 §5，不删。

---

## 2. 总览

| 阶段 | 记录数 | AI 参与度 | 主要产出 |
| --- | --- | --- | --- |
| M1 骨架 | 1（R-014） | `[ai-gen+edit]` | Maven 工程、Flyway 7 表、异常/日志、health、Vue 壳、脚本 |
| M2 大厅 | 1（R-015） | `[ai-gen+edit]` | 列表/创建/详情接口、大厅页四态、5 场示例圆桌 |
| M2 组局 | 1（R-016） | `[ai-gen+edit]` | LlmClient、逐席流式生成、预设兜底、认领 |
| M3 讨论 | 1（R-026 补录）+ R-019 验证 | `[ai-gen+edit]` | SessionActor/Registry、SpeakerPolicy、并行 fan-out、SseRegistry、插话死线、人类席位跳过、轨迹/引用定位 |
| M4 收敛 | 3（R-017 / R-023 / R-024） | `[ai-gen+edit]` | 六段纪要、版本化、失败不覆盖、`structured.meta`、完成态收敛、下载 Markdown |
| M5 质量 | 7（R-018 / R-019 / R-021 / R-022 / R-025 / R-027 / R-028） | `[ai-gen+edit]` | 84 例后端、5 条 E2E、20 场压测、离线模型、截图、单 jar、交付核查与 GitHub 上传 |

> M3~M5 的最终口径以 R-026 与 §4/§5 为准；R-017 / R-019 中的 57 例、35s 等是当时快照，保留用于回溯，不再代表当前测试规模。

---

## 3. 记录明细

### R-001 · 需求拆解（2026-11-17）

- **阶段**：M0
- **目标**：把原需求拆成可执行的功能清单与交付清单
- **Prompt（摘要）**：> 根据这份 AI 圆桌讨论 Web App 需求，拆解出功能模块、硬性交付项（表/接口/路由/测试/commit 数）与评分维度对应的设计重点。
- **产出**：`docs/PRD.md` §5 功能需求 FR-1~FR-9 骨架
- **人工干预** `[ai-gen+edit]`：删掉"多人围观"（超 MVP）；把前端实现方式落到技术选型；补每条 FR 的验收标准——AI 初稿只写了功能，没写怎么算做完。
- **结论**：需求边界以 PRD §4「明确不做」为准，后续编码不再扩张范围。

### R-002 · 产品定位与场景（2026-11-17）

- **阶段**：M0
- **目标**：把"多 Agent 讨论"讲成一个有用户价值的产品，而非技术演示
- **Prompt（摘要）**：> 这个产品的核心不是"多几个 AI"，而是"受控的对抗"。请据此写定位、目标用户与主场景，要求场景能落到具体人物和决策。
- **产出**：PRD §1–§3（含痛点表、与单 AI/多开窗口/AI 播客的对比）
- **人工干预** `[ai-gen+edit]`：把"圆桌价值"归纳为三条设计约束（席位固定、必须有主持人、必须收束），这三条后来直接决定了 `agents.role_type` 与主持人 digest 的设计。
- **结论**：产品叙事成立——对比表是答辩时最有说服力的一页。

### R-003 · 架构与并发模型（2026-11-17）

- **阶段**：M0
- **目标**：确定"多 Agent 并行但有序"的落法，避免流式互相阻塞
- **Prompt（摘要）**：> 后端要同时跑 3-4 个 agent 的流式输出到同一个前端，要求互不阻塞、单个失败不影响其他人。给出调度模型与 SSE 方案，说明为什么不用 WebSocket / 不用 LangChain。
- **产出**：`docs/ARCHITECTURE.md` §3（SessionActor、SpeakerPolicy、SSE 多路复用）
- **人工干预** `[me]`：**关键决策由人做**——
  1. 放弃"每个 agent 一条 SSE 连接"，改为**单连接 + messageId 命名空间**（连接数与 agent 数解耦）；
  2. 不保证跨 agent 全局有序（并行强行排序会牺牲首 token 延迟），只保证单消息内 `seq` 有序；
  3. delta 不落库，只在 `message.end` 写最终态（写量降到 1/300）。
- **结论**：这三条是本题"并发与流式"评分的主要得分点，写进 PRD 与 README 的边界声明。

### R-004 · 数据模型与 ER（2026-11-17）

- **阶段**：M0
- **目标**：7 张表覆盖"席位/消息/轨迹/纪要/参与关系"，并支撑溯源
- **Prompt（摘要）**：> 设计 ER：需求要求至少 5 张表，且要支持观点溯源、纪要版本、席位认领、轨迹回放。给出表、字段、索引与枚举。
- **产出**：`docs/DATABASE.md`（ER 图 + 字段字典 + 索引表 + DDL 片段）
- **人工干预** `[ai-gen+edit]`：
  - 增加 `agent_runs`（AI 初稿把轨迹塞进 messages 的 jsonb，会导致轨迹无法按时间线查询）；
  - 加 `UNIQUE(discussion_id, client_msg_id)` 做幂等，避免 E2E 断网重连时重复气炮；
  - 席位唯一性用**部分唯一索引**（`WHERE seat_id IS NOT NULL`），而不是普通唯一索引。
- **结论**：表数 7 > 要求 5；每张表都能对应到至少一条 PRD 需求（DATABASE §7 有映射）。

### R-005 · API 与 SSE 事件协议（2026-11-17）

- **阶段**：M0
- **目标**：先把契约定死，前后端并行开发不返工
- **Prompt（摘要）**：> 按 PRD 的 FR 定义 REST 接口与 SSE 事件表，要求：接口 ≥8、覆盖认领/插话/结束/纪要、给出错误码与续传机制。
- **产出**：`docs/API.md`（17 接口 = REST 14 + SSE 3，13 类主事件流 + 9 个错误码）
- **人工干预** `[ai-gen+edit]`：
  - 补 `Last-Event-ID` 续传与 `snapshot` 兜底（AI 初稿只写了"断线重连"没写怎么补数据）；
  - `message.start` 必须带 `quoteMessageId`，否则前端无法在气泡首帧就渲染引用徽标（会闪）；
  - `expectedResponders` 字段是加出来的——用于"正在输入"提示，属于体验细节。
- **结论**：契约冻结，后续 M1–M4 严格按此实现，接口变更必须改文档 + 记一条 R。

### R-006 · 测试设计与验收清单（2026-11-17）

- **阶段**：M0
- **目标**：让"并行流式"这种难测的东西可断言
- **Prompt（摘要）**：> 设计 3 条 E2E：组局→开场、插话→并行流式→溯源、结束→纪要→刷新持久化。要求能证明"一个 agent 慢不阻塞别人"。
- **产出**：`docs/TESTING.md`（5 条 E2E + 12 条集成 + 单测清单 + 验收矩阵）
- **人工干预** `[me]`：
  - 引入 **LLM Mock 服务 + 故障注入**（`?slow=8000`、`?fail=st_3`），否则并行/降级无法稳定复现；
  - E2E-2 的断言从"看起来是并行的"改成"B 的 `message.end` 时间戳早于 A 的 `message.end`"——可量化；
  - 补 E2E-5 断网重连，验证 `clientMsgId` 幂等。
- **结论**：测试不是最后补的，M5 只是执行；用例在 M0 就决定了实现要预留哪些钩子。

---

### R-007 · 技术栈变更：Spring Boot 3 + JDK 17 + Vue 3（2026-11-17 16:31）

- **阶段**：M0（文档已出，需回溯修订）
- **触发**：人工指令「项目采用的技术栈暂定为 Springboot 3、jdk 17、vue3，其他的技术栈根据项目进行完善，该技术栈的信息在相关的文档中明确出来」
- **目标**：把已按 Node/TS 写的文档整体迁到 Java 栈，并补齐"栈内还需什么"
- **Prompt（摘要）**：> 技术栈改为 Spring Boot 3 + JDK 17 + Vue 3，其余选型由你按项目需要补齐，并在所有相关文档中显式写出（含版本）。
- **产出**：README §3、ARCHITECTURE（分层/时序/线程模型/选型表全量重写）、PRD §4.4 技术约束、PROJECT_PLAN WBS 与提交规划
- **人工决策点** `[me]`：
  1. **Spring MVC 而非 WebFlux** —— MyBatis-Plus + SQLite 是阻塞式，混用响应式只会多一层心智负担；80 路 SSE 线程池够用。
  2. **自研 `SessionActor` 而非 Spring AI** —— 并发调度是评分核心，框架黑盒无法解释、无法精准 abort。
  3. **JDK 17 无虚拟线程 → 线程池必须显式设计**（core 8 / max 16 / `CallerRunsPolicy` + 两级 `Semaphore`），并写入架构文档，避免"代码里到处 `new Thread`"。
  4. 前端采用 Vue 3 + TypeScript，页面按业务语义拆组件，样式集中在设计令牌。
- **结论**：文档版本统一升到 v1.1，前后端契约（REST/SSE）不变，只换实现栈 —— 这也是"契约先行"的价值：换栈没改一行业务接口。

### R-008 · 数据库改为 SQLite（2026-11-17 16:34）

- **阶段**：M0
- **触发**：人工指令「其中数据库采用 SQLite 数据库」
- **目标**：把 PG 的 ER/DDL/索引/并发假设全部改到 SQLite，并说清代价
- **Prompt（摘要）**：> 数据库改为 SQLite，重写数据设计：表结构、索引（含部分唯一索引）、JSON 字段、时间类型、并发写处理与迁移路径。
- **产出**：`docs/DATABASE.md` v1.2（ER + 7 表 + SQLite DDL + PRAGMA + MyBatis-Plus 映射 + 迁移 PG 路径）
- **人工决策点** `[me]`：
  1. **枚举用 `TEXT + CHECK`**，数组/对象用 `TEXT` 存 JSON —— 不碰 SQLite 的实验性 `jsonb`，保住可迁移性；
  2. **时间统一 `TEXT` ISO-8601 UTC** —— 字典序 = 时间序，`ORDER BY` 直接可用；
  3. **单写者是最大风险**：短事务、禁事务内调 LLM、`busy_timeout=5000` + 退避重试、连接池只开 4；
  4. **"一席一人"用部分唯一索引**（`WHERE seat_id IS NOT NULL`）在库层保证，而不是应用层先查后写；
  5. 议题搜索用 `LIKE`（数据量小），FTS5 + trigram 列为 P2。
- **结论**：SQLite 够用，但它把"事务边界"从性能优化变成了正确性要求 —— 这一条写进了 PRD 技术约束与架构失败矩阵。

### R-009 · 运行方式：不使用容器（2026-11-17 16:35）

- **阶段**：M0
- **触发**：人工指令「其中无需使用docker进行部署运行等」
- **目标**：移除 Docker Compose / nginx 相关设计，给出等价的本地运行与交付方案
- **Prompt（摘要）**：> 不需要 Docker 部署运行，请调整所有文档中的启动、部署、测试与交付方式。
- **产出**：README §5、ARCHITECTURE §6（开发模式 Vite 代理 / 演示模式单 jar）、TESTING §1.2 与 §6、PROJECT_PLAN M1 与 DoD、`.env.example`
- **人工决策点** `[me]`：
  1. 开发态用 **Vite `server.proxy['/api']`** 代替 nginx 反代，天然无跨域；
  2. 演示/交付态用 **`pnpm build` → 复制 `dist` 到 server 静态目录 → `java -jar`**，单进程 8080，连 nginx 也省掉；
  3. E2E 不再依赖 compose：本机两个进程 + 内嵌 WireMock；
  4. **保留"与原需求的差异"的说明**（原文要求 Docker Compose 一键起），在 README 交付说明里写清原因：SQLite 无独立服务，容器只会多一层流式代理风险。
- **结论**：交付路径从"三个容器"变成"两条命令"，评审复现成本更低；文档中所有 `docker compose` 表述已清除。

---

### R-010 · 数据访问统一为 MyBatis-Plus（2026-11-17 16:45）

- **阶段**：M0
- **触发**：人工指令「把MyBatis换成MyBatisplus」
- **现状核实**：文档主口径此前已是 MyBatis-Plus，但架构选型表、WebFlux 论证段落、目录注释里仍混用 "MyBatis"（易被误读成原生 MyBatis + 手写 `SqlSession`）。
- **目标**：口径 100% 统一，并把"映射要点"扩成可执行的数据访问约定，避免编码阶段各写各的
- **产出**：DATABASE §6 重写（依赖/配置/实体映射/分层用法/硬规矩/与 SQLite 配合）、ARCHITECTURE 新增 §3.9、README 与 PRD 版本号对齐 3.5.12
- **人工决策点** `[me]`：
  1. **不用 JPA**：SQLite 无官方方言，Hibernate 对 JSON/部分索引支持别扭；
  2. **不用 `SELECT … FOR UPDATE`**：SQLite 没有行级锁，写了也是空转 —— 回合推进改用 `UPDATE … WHERE turn_index = ?` 的 CAS + 冲突重试；
  3. **事务只包 DB 写**：`@Transactional` 里绝不调 LLM、也不 `emitter.send`，否则写锁被秒级等待占死；
  4. **简单查询一律 `LambdaQueryWrapper`**，复杂聚合才写 XML，且 XML 不出现 SQLite 专有函数（保迁移）。
- **结论**：代码评审 checklist 固化 5 条（见 DATABASE §6.3），后续实现按此执行。

### R-011 · 按完整版原需求补交付材料（2026-11-17 16:45）

- **阶段**：M0
- **触发**：人工提供完整版原需求截图（含 11 项硬性交付约束、5 个评分维度与分值、交付约束、100 元工具额度）
- **目标**：把"要求"变成"可勾选的交付清单"，并核对现有文档是否有缺口
- **Prompt（摘要）**：> 按这份完整版需求说明，逐条核对现有文档：数量类硬指标（表/接口/路由/E2E/commit）、文档类交付物、评分维度对应的设计重点，缺什么补什么。
- **产出**：新增 `docs/DELIVERY.md`（11 项对照 / 打包结构 / 评分自检）；README §8 差异表 + §9 交付；PRD §4.5 功能要求对应 + §4.4 第 6 条；ARCHITECTURE §3.9；API 规模对照；TESTING §7 交付前自检；PROJECT_PLAN §6 投入分配
- **人工决策点** `[me]`：
  1. **4 处差异主动写在 README**（后端语言推荐 Node、数据库未指定、原文提 Docker Compose、数量只多不少）—— 藏起来只会让评审自己发现，反而扣"文档与过程"分；
  2. 评分 100 分里"并发与流式"25 分最值钱，时间投入给到 20h，高于其他维度；
  3. 明确列出**主动放弃的分**（多人围观 / 知识库 / 账号体系），证明取舍是有意的，不是做不完；
  4. 交付动作（打包、归档）写进 WBS 与 DoD，避免最后一小时手忙脚乱。
- **结论**：必交物从"心里有数"变成"表格可勾"；文档缺口仅剩代码与测试。

---

### R-012 · 必交项补齐与交付清单核对（2026-11-17 17:09）

- **阶段**：M0
- **触发**：人工提供完整版原需求（含重要规则、必交清单、交付物要求）
- **目标**：按完整版说明核对并补齐交付材料，避免漏项
- **Prompt（摘要）**：> 这张图比上一版多了 Prompt 记录细则和评分权重，把相关文档补齐。
- **核对结果**：现行说明的必交项为「源码+文档 / Prompt 记录 ≥5 段 / 工作流程说明」。
- **产出**：
  - README 增 §2.1「多 Agent 协作」+ §2.2「三页截图位」+ §3.4 架构图（满足 README 必备章节）；
  - [DELIVERY](DELIVERY.md) 重写：三条禁止项自查、必交清单（源码/5 表/SQL/OpenAPI/架构文档/截图/30 commit）、Prompt 五要素落点、效率台账（40h → 17h，约 2.3×）、评分权重改为 30/25/20/15/10、交付清单更新；
  - PROMPT_LOG 增 §1 七项必填规范、§4 决策索引、§5 走偏与修正记录。
- **人工决策点** `[me]`：
  1. 必交项按现行版重排，旧版中的数量要求作为加分项保留；
  2. 评分权重变了 → 交付优先级重排：产品完成度 30 分最高，因此"纪要闭环 + 可溯源"优先于"换一批""版本切换"等 P1 功能；
  3. Prompt 记录从"有记录"升级为"五要素可验收"，其中 d/e（验证方式、走偏修正）必须写实，否则一看就是编的。
- **验证**：README 六大必备章节逐项打勾（§1 简介 / §3.4 架构图 / §3 选型 / §5 启动 / §6 API / §2.1 多 Agent）；必交清单逐条对照。
- **结论**：必交项定稿；剩余缺口只剩代码与截图。

---

### R-013 · 生成 AI 协作约束文件（2026-11-17 17:42）

- **阶段**：M0（编码前）
- **触发**：人工要求「根据 PRD 等开发文档生成对应的约束文件，其中明确前端页面要美化些、按模块逐步开发等相关的约束」
- **目标**：把散落在 PRD / ARCHITECTURE / API / TESTING 里的要求，收敛成**AI 每次开工必读的硬条款**，避免"文档里写了但生成时忘掉"
- **Prompt（摘要）**：> 依据 PRD 与开发文档生成约束文件（一个总纲 + 分模块规则），重点约束两点：①前端页面必须达到明确的视觉标准（不是"尽量好看"）；②必须按模块逐步开发，禁止一次性生成整个项目；同时固化技术栈版本、分层边界、SSE/并发、数据与测试、提交规范。
- **产出**：
  - `AGENTS.md`：10 条红线（禁止一次性生成 / 禁止 UI 组件库 / 密钥只在后端 / 事务内不调 LLM / 不用 `FOR UPDATE` / 失败隔离…）+ 10 条必做 + 模块地图；
  - `rules/00-project.md`（边界与版本）、`10-architecture.md`（分层与模块→文件映射）、`20-frontend-ui.md`（设计令牌 + 四态 + 四档断点 + **美化自查 12 条**）、`30-backend-streaming.md`（SSE 协议/闸门/超时/隔离/密钥）、`40-data-testing.md`（Flyway/短事务/CAS/测试门禁）、`50-workflow-commit.md`（模块 Gate、DoD 模板、commit 规范、留痕要求）；
  - README §4/§7、DELIVERY 文件树与自检、PROJECT_PLAN M1 同步。
- **人工决策点** `[me]`：
  1. **"美化"必须可判定**：不写"界面要美观"，改成设计令牌（间距/字号/圆角/阴影）、四态、四档断点、禁止清单与 12 条打勾项；
  2. **"按模块开发"必须带门禁**：8 个模块顺序不可跳，每模块有入口条件与 DoD，一模块 ≤8 次提交、不跨模块改文件；
  3. **规则优先于文档**：冲突时以 `rules/` 为准，并回头改文档，避免出现两套标准；
  4. **只写可执行条款**：全篇用"必须/禁止"，不用"建议/尽量"——模糊词等于没有约束。
- **验证**：逐条对照 PRD FR-1~FR-9、API 17 接口、DATABASE 7 表检查覆盖；`grep -n "建议\|尽量" rules/` 应为空（已确认）。
- **结论**：后续每个模块开工先读 `AGENTS.md` + 对应 rules，完成时按 `rules/50` §4 的 DoD 模板打勾，再提交。

### R-014 · M1 骨架落地（2026-11-17 19:00–19:40）

- **阶段**：M1（骨架）
- **触发**：人工要求「根据相关文档与项目计划，开始实现 M1 和 M2，每个模块自检通过后再做下一个」
- **目标**：把 `mvn spring-boot:run` + `pnpm dev` 的骨架跑通：7 张表自动建库、统一异常/JSON 日志、health/metrics、Vue 壳与设计令牌
- **Prompt（摘要）**：> 按 `rules/50` 的模块顺序推进 M1：先读 PRD FR + API + DATABASE + rules，再依次落地 Maven 工程、Flyway 迁移、统一异常与 MDC 日志、health/metrics、Vue 骨架与设计令牌、脚本与 E2E 脚手架；每个模块跑测试与冒烟后再提交。
- **产出**：
  - `server/pom.xml`、`application.yml` / `application-test.yml` / `logback-spring.xml`；
  - `db/migration/V1__schema.sql`（7 表）/ `V2__indexes.sql` / `V3__seed_demo.sql`（1 场已结束圆桌：4 席位 / 12 消息 / 1 纪要 / 8 轨迹）；
  - `api/advice`（统一错误体与错误码）、`infra/logging`（TraceIdFilter + MdcTaskDecorator）、`infra/config`（AppProperties / AsyncConfig / MybatisPlusConfig / WebConfig / OpenApiConfig）、`ApiHealthController` + `HealthService` + `MetricsService`；
  - `web/`（Vite 5 + Vue 3.4 + Router 4 + Pinia 2，`tokens.css` / `base.css` / `app.css`、`lib/copy.ts` 中文文案层、`lib/api.ts` 匿名身份）；
  - `scripts/dev|build`（sh + bat，无容器）、`e2e/` Playwright 脚手架。
- **人工决策点** `[me]`：
  1. **`flyway-database-sqlite` 不存在**：核对 `flyway-core:11.7.2` jar 内含 `org/flywaydb/core/internal/database/sqlite/`，改为只依赖 `flyway-core`，并回改 DATABASE.md 的说明（文档与实现冲突时以可验证事实为准）；
  2. **MyBatis-Plus 3.5.9+ 拆分**：`PaginationInnerInterceptor` 需 `mybatis-plus-jsqlparser`，显式加依赖并注释理由（不是引入第二个 ORM）；
  3. **JSON 日志只在非 dev profile 开**：dev/test 用可读 pattern，prod/e2e 用 logstash encoder（MDC 字段固定），避免开发期日志不可读；
  4. **SPA 兜底放后端**：`SpaForwardController` 只转发 3 条前端路由，保证 `java -jar` 单进程刷新不 404。
- **验证方式（命令 + 结果）**：
  - `mvn -B test` → `Tests run: 11, Failures: 0, Errors: 0`（ULID 单调 2000 次、状态机 4 例、health/metrics/7 表集成 4 例）；
  - `java -jar server/target/panel-studio.jar` → 日志 `Successfully applied 3 migrations … now at version v3`，`/api/health` 返回 `{"status":"ok","db":"up","llm":"down","uptimeSec":9}`，`/actuator/health` 三组件 UP；
  - `pnpm -C web run build` → `vue-tsc --noEmit` 通过 + `✓ built in 2.38s`；`pnpm -C web run lint` → 0 error；
  - `scripts/build.bat` → jar 内置静态资源；`/`、`/discussions/dk_1`、`/discussions/dk_1/panel` 均 200（text/html），`/api/health` 200；
  - 开发模式：Vite 5179 + `/api` 代理 → 页面 200、`/api/health` 200。
- **结论**：M1 门禁达成。遗留：`flyway-database-sqlite` 的文档描述已修正；生产 JSON 日志待 M5 压测时取样验证。

### R-015 · M2 大厅（2026-11-17 19:40–20:30）

- **阶段**：M2（大厅，模块 2）
- **目标**：`GET/POST /discussions` + `GET /discussions/{id}` 与列表页四态，门禁 I-1 通过
- **Prompt（摘要）**：> 按 API.md §2.1/§2.2 与 PRD FR-1 实现大厅：分页限制必须显式、错误码与文档一致、列表页要有骨架/空态/错误/正常四态且符合 rules/20 的 12 条自查。
- **产出**：`domain/entity`（5 张表实体）、`infra/mapper`（列表 SQL / 最后一条发言 / 我参与的）、`application/DiscussionService`、`api/DiscussionController`、`web/pages/Lobby.vue` + 卡片/弹层/骨架/空态组件、`stores/lobby.ts`、`V4__seed_examples.sql`。
- **人工决策点** `[me]`：
  1. **排序含状态优先级 → 不能用 ULID 游标**：改为不透明游标（内部 offset），并限制 `limit ≤ 50`，避免客户端构造分页参数扫全表；
  2. **`status` 支持逗号分隔集合**：产品需要「进行中」这种分组，比前端发 4 次请求更省；
  3. **弱文本对比度不达标**：rules/20 给的 `#6F7787` 在卡片底色上约 3.8:1，按要求 ≥4.5:1 提亮到 `#8A93A6`，规则与验收项冲突时以可测量的验收项为准；
  4. **同屏主按钮**：空态动作降级为次按钮，确保页面只剩「发起圆桌」一个主按钮。
- **验证方式（命令 + 结果）**：
  - `mvn -B test` → `DiscussionApiIntegrationTest` 8 例通过（7 字议题 400 + `details[0].field=topic`、人数越界、创建 201、LIVE 优先、状态/关键词筛选、mine 过滤、非法状态 400、详情 404）；
  - `node e2e/screenshot.mjs` → `docs/screenshots/01-lobby*.png` 四张（桌面/弹层/空态/窄屏），逐张目视检查层级、间距、裁切与断点；
  - 浏览器走查：发起圆桌 → 300ms 内进入组局页（Playwright 断言 URL）。
- **结论**：大厅门禁达成。遗留：列表未做虚拟滚动（20 条一页足够，M5 压测再评估）。

### R-016 · M2 组局（2026-11-17 20:30–21:40）

- **阶段**：M2（组局，模块 3）
- **目标**：议题分析 + 席位逐个流式生成 + 预设兜底 + 认领，门禁 I-2~I-4 通过
- **Prompt（摘要）**：> 按 API.md §2.3/§2.4 实现组局：席位逐个 SSE 推送、首张要快、单席失败不能拖垮整场、失败要能降级到预设阵容、认领冲突必须由数据库唯一索引兜底。
- **产出**：`infra/llm`（LlmClient / OpenAiCompatibleClient / JsonRepair / PromptTemplates）、`application/PanelService + PanelParser + PanelPersistService + PresetPanels`、`api/PanelController`、`web/pages/Panel.vue` + `SeatCard.vue` + `lib/sse.ts` + `stores/panel.ts`、`V5__discussion_regen_count.sql`。
- **人工决策点** `[me]`：
  1. **一次调用 vs 逐席调用**：选逐席（1 次分析 + N 次生成），换来「首张卡 ~2s」与单席位可重问/可兜底；代价是总调用数变多，用 `max_tokens=800` 控成本；
  2. **失败不留半截数据**：整场生成完成后单事务落库；失败只发 `panel.failed`，由用户显式点「使用预设阵容」（`preset:true`），而不是服务端偷偷兜底；
  3. **主持人不可认领**：否则一场圆桌没有调度者，新增 `SEAT_NOT_CLAIMABLE`；
  4. **SSE 端点不声明 `produces`**：否则错误路径（429/409）无法协商回 JSON；改为让 `SseEmitter` 自己设置 `text/event-stream`，客户端 `Accept` 同时带 `application/json`；
  5. **SQLite `transaction_mode=IMMEDIATE`**：并发写触发 `BUSY_SNAPSHOT`（`busy_timeout` 对它无效），改为写事务在 BEGIN 时取写锁。
- **验证方式（命令 + 结果）**：
  - `mvn -B test` → **32 例全绿**：I-2 帧序（analysis → seat.added ×4 → done）、I-3 幂等（席位 id 不变）、I-4 冲突（409 SEAT_TAKEN / 撤销恢复 ACTIVE / 主持人 409 / 不存在 404）、换一批超限 429、WireMock 脏 JSON（围栏 + 尾逗号 + 前后闲话）能修复、上游 500 → `panel.failed` 且席位数为 0；
  - 浏览器走查（Playwright，未配 Key 的降级路径）：发起圆桌 → 组局页 → 失败卡 → 点「使用预设阵容」→ 4 张席位卡 → 认领 → **刷新后认领仍在** → 进入圆桌；控制台 0 error；
  - `node e2e/screenshot.mjs` → `02-panel*.png`（桌面 + 窄屏，含已认领席位）；
  - 删库重启：`discussions seeded: 6`，首条 `status=LIVE, seats=4`（验证 V1~V5 全部可重放）。
- **结论**：M2 门禁达成。遗留：`SQLITE_BUSY` 的应用层退避重试（rules/40 §1）暂未实现，靠 `busy_timeout + IMMEDIATE + 短事务` 兜住，M3 并发压测时若仍出现再补，已在 §5 记录（该项已于 R-020 补齐）。

---

### R-017 · 全量核查 + M4 收敛（2026-11-19 00:40–02:10）

- **阶段**：M4（收敛，模块 7）+ 一次跨模块核查
- **目标**：按 PRD/API/ARCHITECTURE/DATABASE/TESTING/rules 逐项核对现状，把「契约有、代码没有」的部分补齐，并修掉核查中暴露的正确性缺陷。
- **Prompt（摘要）**：> 以文档为验收条件核查整个项目，列出问题后动手修；不要只写报告。红线优先：不在事务里调模型、失败不能影响全场、纪要必须可溯源且失败不覆盖旧版本。
- **核查结论（问题清单）**：
  1. **纪要模块整体缺失**：`POST/GET /discussions/:id/minutes` 不存在、`minutes` 表没有实体与 Mapper、`Minutes.vue` 是占位页 → FR-7（P0）未交付，API 声明 17 个接口实际只有 15 个；
  2. **`SseRegistry` 并发缺陷**：`publish` 的 `addLast` 与 `trim` 用的是两把不同的锁（后者 `synchronized`），多 agent 并行推送时并发修改 `ArrayDeque`，会丢帧甚至抛 `ConcurrentModificationException`；
  3. **收束消息 id 对不上**：`runDigest` 先发布 `digest.start`（新生成一个 id），随后 `runSeat(host, null, …)` 又让 `AgentRunner` 生成第二个 id → 前端点轨迹定位不到这条收束；
  4. **`start()` 先改状态再校验席位**：席位为空时抛异常但状态已被写成 `LIVE`；
  5. **现场页违反布局硬约束**：`.page { height:100%; overflow-y:auto }` + 内部 `max-height:62vh`，实际是整页滚动，rules/20 §6 的「三区独立滚动 + 输入区固定」没有落实。
- **产出**：
  - 后端：`domain/entity/MinuteEntity`、`infra/mapper/MinuteMapper`、`application/minutes/MinutesService`、`api/MinutesController`、`api/dto/MinuteDtos`、`PromptTemplates.minutesSystem/minutesUser`；`SseRegistry` 并发修正；`SessionActor.runDigest` id 统一；`DiscussionSessionService.start` 顺序调整。
  - 前端：`pages/Minutes.vue`（六段渲染 + 溯源跳转 + 版本切换 + 复制 Markdown）、`lib/minutes.ts`、`stores/minutes.ts`；`pages/Live.vue` 重写为固定视口三栏 + 窄屏轨迹抽屉；`SeatRail` 支持横排。
  - 测试：`MinutesApiIntegrationTest`（I-8 / I-9 / 未结束拒绝生成）。
- **人工决策点** `[me]`：
  1. **纪要 Markdown 由服务端模板渲染**，不让模型直接产出 md：模型只给结构化 JSON，六段标题与排版由代码保证，避免"同一版本两处不一致"；
  2. **`sourceMessageIds` 落库前清洗**：模型给的 id 必须能在本场 messages 里查到，否则剔除；`seatId` 认不出时按姓名回退一次，仍认不出就丢掉这条 claim —— 宁可少一条，不可张冠李戴；
  3. **失败不写行**：生成失败只回 `minutes.failed + keptVersion`，不插入 `GENERATING/FAILED` 行，避免版本号被失败消耗、旧版本被遮住；
  4. **无密钥时给「离线摘要」**：只从本场真实发言摘录，并在 `minutes.done` 标 `fallback:"LOCAL"`、前端打「离线摘要」徽标。理由：单 jar 演示不能因为没配 Key 就完全出不了纪要；但不能伪装成模型结论。
- **验证方式（命令 + 结果）**：
  - `mvn -f server/pom.xml test` → **57 例全绿**（新增 3 例：版本自增与旧版可取、失败不覆盖且带 `keptVersion`、未结束生成返回 409 / 无纪要 404；另补 1 例单条重试）；
  - `pnpm -C web build` → 通过（`Minutes` / `Live` 分包正常产出）；
  - 断言口径：纪要点里的 `sourceMessageIds` 必须是本场消息 id（用 `/stream` 的 snapshot 反查比对），不是"看起来像"。
- **结论**：FR-7 主链路（生成 → 落库 → 版本 → 溯源 → 刷新一致）打通，FR-9 的单条重试也补齐（`POST /messages/:messageId/retry`）。仍遗留：E2E 用例（`e2e/specs` 只有 README）、`SQLITE_BUSY` 应用层退避，见 §5 新增 F-8~F-11。

---

### R-018 · 前端视觉打磨 + 文档同步（2026-11-19 02:20–03:30）

- **阶段**：M5（质量）中的视觉与文档部分
- **目标**：让四个页面达到 rules/20 的「演播厅」观感，并把文档里的状态、数量、截图口径对齐实现。
- **Prompt（摘要）**：> 对前端页面做美化，但要守住红线：不引组件库、主色不超过两种、不用位移动画堆特效、四档断点不能出横向滚动条；顺手把文档里"待编码""17 接口"这类过期口径改掉。
- **产出**：
  - 令牌与原语：`tokens.css` 增 `--glow-*`（静态灯位）、`--primary-line`、`--*-soft`、`--radius-xl`、`--text-3xl`、`--copy-max`、`--shadow-inset`；`base.css` 重写按钮/卡片/输入/横幅/徽标/eyebrow，新增 `.btn--lg`、`.badge--accent/--muted/--count`、`.banner--*`。
  - 页面：大厅改为「演播厅开场区（标题 + 3 条事实）+ 筛选 + 状态色卡片」；组局把议题分析做成议题卡（目标是主句，争议/视角是两种标签），席位卡加角色 chip、立场引号、席位色标签、认领态；现场页补席位"正在发言"高亮、失败卡片原因、状态与轨迹一致；纪要页加版本/生成时间条、分节左标尺、要点 marker。
  - 交互：路由 120ms 淡入；对话框 head/foot 分隔与统一圆角。
- **人工决策点** `[me]`：
  1. **不用组件库**：rules/20 明确禁止；四页只需要 8 个原语，自建比引入一套主题变量更可控；
  2. **背景光用静态径向渐变**：`rules/20 §8` 只允许三处动效，灯位感靠静态渐变 + 分隔高光，不做位移；
  3. **`ch` 换成 px 令牌**：中文在 `ch` 单位下的宽度失真（20ch ≈ 360px），把标题/正文的最大宽度统一到 `--reading-max` / `--copy-max`，并用 `text-wrap: balance` 消除"孤字换行"；
  4. **失败卡片显示原因而不是只弹横幅**：PRD 要求"原因 + 下一步"，卡片内显示 `message.error`，连续两条失败才升级为页面级横幅。
- **验证方式（命令 + 结果）**：
  - `pnpm -C web lint` → 0 error 0 warning；`pnpm -C web build` → 通过（`vue-tsc` 无类型错误）；
  - `node e2e/screenshot.mjs` → 8 张截图（4 页 × 桌面/窄屏），四档断点 `no horizontal overflow`；
  - 人工核对截图：大厅/组局/现场/纪要四页的层级、对比度、席位三色一致性；
  - 清空 `data/panel-studio.db` 重启 → Flyway 重建 6 场种子，截图里不再出现测试残留的重复圆桌。
- **结论**：视觉达到 rules/20 的自查口径；文档同步见 §5 的 F-12~F-13 与 DELIVERY/README 的状态更新。E2E 用例仍是唯一未落地的门禁项。

---

### R-019 · E2E 落地（M5）（2026-11-19 11:00–12:10）

- **阶段**：M5（质量）的最后一处门禁
- **目标**：把 `docs/TESTING.md` §2 的 5 条 E2E 真正跑起来，且不依赖真实模型与 API Key。
- **Prompt（摘要）**：> 用 Playwright 把主链路跑通；断言要能证明"并行不阻塞""失败可重试""断线能恢复"，不要截图式断言；整套环境一条命令拉起，跑完自己收尾。
- **产出**：
  - `e2e/mock-llm.mjs`：OpenAI 兼容假模型（`:9099`），按 Prompt 标记分派固定脚本，延迟可脚本化（周敏 600ms / 陈立 1800ms），并支持"只失败一条"的故障注入；
  - `e2e/global-setup.ts`：假模型 → 删临时库 → 后端 → 前端，按序拉起并注册收尾；
  - `e2e/specs/`：5 条用例 + `helpers.ts`（API 预置数据、等轨迹落定）；
  - `e2e/clean.mjs`：逐文件清理产物（见下方环境约束）。
- **人工决策点** `[me]`：
  1. **不用 config 里的 `webServer`**：它无法保证"先删库再启后端"的顺序，删到正在写的库会直接毁数据；改用 globalSetup 自己编排；
  2. **假模型不引 WireMock standalone**：一个 200 行的 Node 脚本就能按 Prompt 标记分派，不给前端工程加依赖；服务端零测试专用分支，只改 `LLM_BASE_URL`；
  3. **断言"快的先结束 + 慢的还在流"**，而不是"两者首字时间差 < 3s"：前者是并发正确性的直接证据，后者受机器负载影响；
  4. **E2E-5 用拦截 `/stream` 制造连不上**，不用 `setOffline`：断网模拟对已建立的长连接不生效（心跳仍在流动），会造出永远等不到断线的假红。
- **顺带修掉的真问题**（详见 §5 F-14 / F-15）：
  1. 用户插话可能被排在专家回答之后（列表靠到达顺序 push）；
  2. SSE 半开连接没有任何感知，且重连失败 3 次就永久放弃 —— 讨论现场等于"静默死亡"。
- **验证方式（命令 + 结果）**：
  - `pnpm -C e2e test` → **5 passed（35.0s）**，含启动假模型/后端/前端；
  - `mvn -f server/pom.xml test` → 57 例全绿；`pnpm -C web build` → 通过；
  - 断言落点：`data-status` / `data-name` / `data-message-id` 三个语义属性 + 中文文案，不依赖样式 class。
- **结论**：M5 门禁达成（5 条 E2E 全绿），交付清单里"测试代码"一项可勾。

---

### R-020 · SQLITE_BUSY 应用层退避重试（2026-11-19 17:20–17:40）

- **阶段**：M5（质量）收尾，补齐 R-016 留下的唯一门禁缺口
- **目标**：把 `ARCHITECTURE §3.7` / `rules/40 §1` / `TESTING I-13` 早已约定、但一直靠 `busy_timeout + IMMEDIATE + 短事务` 兜住的退避重试真正落地。
- **Prompt（摘要）**：> 按需求与开发文档核对遗留项，实现 SQLITE_BUSY 的应用层退避重试；只补这一项，最小改动，不碰契约、不引依赖、不破坏既有测试。
- **产出**：`infra/db/SqliteBusyRetryInterceptor`（MyBatis `Executor.update` 外层统一重试 50/100/200ms 三次）；`MetricsService` 增 `sqliteBusy` 计数并进 `/metrics`；`SQLITE_BUSY` 命中记 `sqlite.busy` 日志、对用户静默；`SqliteBusyRetryInterceptorTest`（退避序列/放弃/非锁异常不重试）+ `SqliteBusyIntegrationTest`（I-13 并发写）；同步 `ARCHITECTURE §3.7`、`API §2.12`、`TESTING I-13`。
- **人工决策点** `[me]`：
  1. **用 MyBatis 写拦截器而不是在每个 mapper 调用点包一层**：写路径有 17 处，逐点包装必然漏；拦截器天然覆盖所有 insert/update/delete（含 MyBatis-Plus 生成的），且只拦 `Executor.update`，读语句不碰（WAL 下读写互不阻塞）；
  2. **重试只包单条 mapper 写**：严格遵守「事务里不调 LLM/发 SSE/做等待」，退避发生在写语句外层，不扩大事务边界；
  3. **识别扩展锁码**：`SQLITE_BUSY_SNAPSHOT` 等扩展码 `busy_timeout` 无效，按 `SQLiteException#getResultCode()` 名称含 `BUSY`/`LOCKED` 判定，再兼容标准错误码 5/6 与被 Spring 翻译后的 DataAccessException 文案；
  4. **重试可观察**：每次命中累加 `sqliteBusy` 并打 `event=sqlite.busy` 告警，重试耗尽才向上抛——平时静默、异常时有痕迹，避免"重试掩盖真故障"。
- **验证方式（命令 + 结果）**：
  - `mvn -f server/pom.xml test` → 62 例全绿（新增 4 例单测 + 1 例 I-13 集成）；
  - `SqliteBusyRetryInterceptorTest` 断言：命中两次后成功（delay 50/100，计数 2）、锁一直占用则 4 次尝试后放弃（delay 50/100/200）、非锁异常 1 次即抛且不计数；
  - `SqliteBusyIntegrationTest`：20 线程 × 5 次终态 UPDATE 全部成功、无锁异常泄漏、P95 < 200ms。
- **结论**：R-016 遗留的 `SQLITE_BUSY` 应用层退避已补齐，M5 门禁项全部达成。

---

### R-021 · 文档缺口补齐与全量核查（2026-11-19 17:20–18:30）

- **阶段**：M5 收尾，按 PRD / API / TESTING / rules 逐项对照实现，补齐缺失功能并修复核查发现的问题。
- **目标**：把「文档已写、代码没有」的项补上；实现后跑全量单测/集成/E2E 与静态核对，发现问题逐项修复并分别提交。
- **Prompt（摘要）**：> 根据需求文档和开发文档把没实现的功能补上；实现完开始核查，发现的问题也要修；每实现或修复一项都提交一次。
- **补齐的功能**（每项先补 API 契约、再实现、再提交）：
  1. `DELETE /discussions/:id`：API.md 早已声明但后端缺失。补软删除、仅创建者可删、直链 410、停调度与关 SSE、前端创建者入口；
  2. 阶段性小结（FR-7）：`interim=true` 流式生成六段小结，不入库、不占版本、不收敛状态，前端以徽标与提示标注；
  3. 再来一场（FR-8）：复制议题 / 目标 / 分析 / 席位配置到新 DRAFT，消息 / 认领 / 纪要 / 回合进度不复制，现场页加入口。
- **核查发现并修复的问题**（见 §5 F-17~F-22）：
  1. 四个已声明错误码没有中文映射，前端落回通用文案；
  2. E2E `waitForRunsSettled` 把空轨迹表当成「已收尾」，插话时上一轮还在 inflight，导致 E2E-4 假红；
  3. 插话选中的席位若都还在发言会被整轮跳过，用户消息得不到回应（FR-4 硬要求）。
- **验证方式（命令 + 结果）**：
  - `mvn -f server/pom.xml test` → **66 例全绿**（新增 4 例：软删除、阶段性小结、再来一场、落库失败帧）；
  - `pnpm -C web build` → 通过；`pnpm -C e2e test` → 修复 helper 与调度后 5 条全绿；
  - 静态核对：API 19 个接口与 Controller 一致；API §4 错误码与 `lib/copy.ts` 映射一一对应；README / DELIVERY / TESTING 的数量口径同步。
- **结论**：文档声明的功能全部落地，核查发现的 3 个问题均已修复；M5 门禁保持全绿。

---

### R-022 · 内置离线模型兜底（2026-11-19 19:40–20:00）

- **阶段**：M5 收尾，解决「未配置模型密钥就无法对话」的开箱体验问题。
- **目标**：用户明确要求「所有的数据回复等均可正常对话」——零密钥机器上也要能组局、开场、并行发言、收束、纪要并落库。
- **Prompt（摘要）**：> 修复项目可能出现的问题，所有数据回复等均可正常对话。
- **产出**：`infra/llm/OfflineLlmClient`（规则生成议题分析 / 席位档案 / 发言 / 收束 / 六段纪要，紧缺真实消息 id）；`OpenAiCompatibleClient` 在 `!configured` 时委托给它；`PanelService` 去掉 `notConfigured` 拦截；`/api/health` 的 `llm` 由 `down` 改为 `local`，`LlmHealthIndicator` 返回 `mode=local`；文案与 API/README 同步。
- **人工决策点** `[me]`：
  1. **在 LlmClient 层兜底而不是给每个服务加 if**：一处委托覆盖议题分析、席位、发言、收束、纪要全部调用点，避免遗漏；
  2. **离线输出仍走既有 JSON 校验与溯源清洗**：`sourceMessageIds` 只取上下文中真实出现的 `ms_` id，不编造；
  3. **不把离线内容伪装成真实模型**：health 明确 `local`，文档写明是演示级规则输出；配置 `LLM_*` 后自动切回真实模型。
- **验证方式（命令 + 结果）**：
  - `mvn -f server/pom.xml test` → 新增 `OfflineLlmClientTest` / `OfflineFallbackIntegrationTest` 全绿；
  - 未配置 `LLM_BASE_URL` 的 Spring 上下文中 `LlmClient` 返回合法 JSON 与非空流式文本，不再抛 `LLM_NOT_CONFIGURED`。
  - 零密钥实机验证：`/api/health` → `llm=local`；`ROOMS=4 node scripts/stress.mjs` → `agentFailureRate=0`、`sqliteBusy=0`；手工走完组局 → 发言 → 结束 → 纪要，`event:minutes.done` 与 6 段 `minutes.section` 全部到达。
- **结论**：项目开箱即可完整演示；内置离线模型不替代生产模型，仅保证零密钥可用。

---

### R-023 · 五席位选座、筹备中修复与纪要增强（2026-11-19 23:00–23:40）

- **阶段**：M2/M3/M4 交互完善，按用户要求把"组局"改成先选座再生席。
- **目标**：固定 1 主持 + 4 专家；用户自选一个专家席位，点一次即生成其余 AI 席位并自动开场；其他用户可在开场前加入；未等待时剩余席位保持 AI；同时增强纪要的参会信息与导出。
- **Prompt（摘要）**：> 对生成纪要功能或模块进行更新完善；席位为 5 位，其中 1 主持与 4 专家，其中一个专家席位为用户自主选择；用户选座完毕后点击生成其他席位为 AI 后自动开始；修复筹备中的讨论；其他用户也可参加未开始的圆桌，未等待时其余席位自动 AI。
- **实现**：
  1. 新增 `UserSeatPlan`（固定专家顺序、用户席位档案）；`CreatePanelRequest` 增加 `userRole` / `autoStart`；`PanelService` 保留用户所选席位并在落库同一事务写入参与者；生成完调用 `DiscussionSessionService.start` 自动开场；
  2. 组局页改为"选座面板"：主持人固定由 AI 担任，4 个专家角色单选；未选座不再自动生成；
  3. 认领限制在筹备阶段：`LIVE` 后返回 `409 ALREADY_STARTED`；`SessionActor` 不再为 `REPLACED_BY_USER` 席位生成发言；
  4. 大厅新增「筹备中」筛选与「可加入」标记，`PANEL_READY` 卡片回到组局页，方便其他用户认领；
  5. 纪要 `structured.meta` 补参会席位、回合数、消息数、时长、用户关切；前端展示参会信息并支持下载 Markdown；结束讨论后跳转 `?auto=1` 自动生成；
  6. 修复 `MinutesApiIntegrationTest` 新增用例的测试卫生：等用户插话的回应落库再结束，避免残留模型调用拖慢后续用例。
- **验证方式（命令 + 结果）**：
  - `mvn -f server/pom.xml test` → 全绿（新增选座/自动开场、AI 跳过人类席位、纪要 meta 断言）；
  - `pnpm -C web build` → 通过；
  - `pnpm -C e2e test` → E2E-1 改为选座流程后全绿。
- **结论**：五席位与多人加入流程闭环；纪要从"只有六段"补齐为带参会事实与可导出的交付物。

---

### R-024 · 修复纪要完成后仍显示「纪要生成中」（2026-11-19 23:45–23:55）

- **阶段**：M4 收敛模块的完成态一致性修复。
- **问题**：纪要已经生成完毕、内容也已落库，但页面状态徽标仍停在「纪要生成中」；重新打开或切换版本时，「离线摘要」与错误提示也会沿用上一版的状态。
- **根因**：
  1. 后端先发 `minutes.done`、再执行 `closeOnSuccess`，前端收到完成事件立刻刷新时读到的还是 `SUMMARIZING`；
  2. 前端 `minutes` store 只在初始化时读一次讨论状态，生成完成后从不刷新；
  3. `loadMinutes` 没有重置 `offline` / `error` / 分节草稿，切换版本会继承上一版的提示。
- **修复**：
  1. 后端先收敛 `SUMMARIZING → CLOSED`，再把最终状态放进 `minutes.done.status`；收敛失败只记 warn，不把已落库的成功报成失败；
  2. 前端 `onDone` 直接用事件里的 `status` 更新徽标，并在 `loadMinutes` 后调用 `refreshStatus()` 与库对齐；打开已有纪要但状态仍是 `SUMMARIZING` 时也主动自愈；
  3. `loadMinutes` 成功时重置 `offline`（按 `model=local-fallback` 判断）、`error` 与流式分节草稿；切换版本重置复制态。
- **验证方式（命令 + 结果）**：
  - `mvn -f server/pom.xml -Dtest=MinutesApiIntegrationTest test` → 通过；`shouldVersionMinutes` 断言 `minutes.done` 含 `"status":"CLOSED"`，且随后 `GET /discussions/:id` 为 `CLOSED`；
  - `pnpm -C web build` → 通过；
  - `pnpm -C e2e test` → E2E-3 新增断言：生成完成后状态徽标必须为「已结束」。
- **结论**：完成态在一次事件内收敛，不再依赖前端猜测或二次刷新。

---

### R-025 · 计划与相关文档按当前实现同步（2026-11-20 01:30–02:10）

- **阶段**：功能稳定后的文档收口。
- **目标**：把最近几轮的五席位选座、开场前加入、纪要完成态与离线模型等变更，回写到项目计划和所有相关文档，消除"文档写 4 席、代码跑 5 席"这类口径漂移。
- **Prompt（摘要）**：> 根据项目情况对项目计划中的内容进行更新，以及其他相关文档中的内容进行更新完善。
- **更新范围**：
  1. `PROJECT_PLAN.md`：版本升到 v1.2；WBS 勾选完成项并补选座/自动开场/多人加入/纪要 meta/完成态；DoD 改为 84 例 + 5 条 E2E；新增 §8 同步摘要；
  2. `PRD.md`：v1.1；Happy Path 改为"先选专家席位"；FR-2/FR-3 补选座、开场前认领、LIVE 后拒绝换座；E2E 由 3 条更新为 5 条；
  3. `ARCHITECTURE.md`：新增 §3.11，说明 `UserSeatPlan`、选座事务、`REPLACED_BY_USER` 过滤与纪要完成态；
  4. `DATABASE.md`：说明应用默认 5 席（DB 默认 4 仅兼容旧数据）、`structured_json.meta` 形状；
  5. `DELIVERY.md` / `README.md` / `TESTING.md` / `WORKFLOW.md`：19 接口、125+ 提交、5 条 E2E、选座/自动开场/纪要下载与完成态；
  6. `e2e/screenshot.mjs`：改为 5 席选座流程，新增选座页截图位。
- **验证方式（命令 + 结果）**：
  - 全仓 `grep` 检查 `18 接口`、`3 条 E2E`、`1 主持 + 3`、`默认 4` 等旧口径，逐项消除或标注为历史样例；
  - `mvn test` 84 例、`pnpm -C web build`、`pnpm -C e2e test` 5 条均保持全绿；
  - `docs/screenshots/` 若重新生成，README 的截图位与文件一一对应。
- **结论**：文档与当前实现同口径。

---

### R-026 · M3~M5 留痕补全与交付口径收敛（2026-11-20 02:10–02:40）

- **阶段**：M3 讨论 / M4 收敛 / M5 质量（留痕补全）。
- **目标**：按当前代码与测试结果补齐 M3~M5 的留痕，去掉与实际不符的旧计数，并把交付物收敛为可验证的源码、文档与截图。
- **Prompt（摘要）**：> 根据项目情况更新 Prompt 与工具调用留痕中 M3~M5 的内容，并收敛交付物清单。
- **M3 讨论（补录）**：
  - `SessionActor` 串行消费队列、回合内 `CompletableFuture` 并行 fan-out；`inflight` 以 `seatId` 为键避免同一席位并发；每场 `Semaphore(3)`、全局 `Semaphore(20)`；
  - `SpeakerPolicy` 6 个单测覆盖点名、引用、关注点、K 上限与主持人永不入选；
  - `SseRegistry` 单连接多路复用、心跳、`Last-Event-ID` 回放；插话目标席位忙时按 30s 死线延后，不丢用户消息；
  - 用户接手的席位（`REPLACED_BY_USER`）不参与 AI 选发言人；轨迹、引用与消息定位打通。
- **M4 收敛（最终口径）**：
  - `MinutesService` 六段流式、版本化、失败不覆盖旧版；`structured.meta` 补参会席位/回合/消息数/时长/用户关切；
  - 正式纪要先 `SUMMARIZING → CLOSED` 再发 `minutes.done`，事件带 `status`；结束自动生成、支持下载 Markdown、无模型时走离线摘要。
- **M5 质量（最终口径）**：
  - 后端 **84 例**全绿；E2E **5 条**全绿；`scripts/stress.mjs` 20 场压测 `sqliteBusy=0`；单 jar 构建通过；4 个核心页面截图与选座页截图已更新；
  - 未配置外部模型时自动启用内置离线模型，组局、讨论、纪要全链路可跑通。
- **工具调用记录**：
  - `mvn -f server/pom.xml test` → 84/84；
  - `pnpm -C web build` → 通过；
  - `pnpm -C e2e test` → 5 passed；
  - `bash scripts/build.sh` → 单 jar 构建成功；
  - `node e2e/screenshot.mjs` → 13 张截图，四档断点无横向溢出；
  - `grep` 审计旧口径（18 API / 3 条 E2E / 默认 4 席 / 52 commit）→ 产品口径已消除，历史样例与 4 席压测保留并标注。
- **人工决策点** `[me]`：
  1. M3 之前没有单独记录，补一条"现状 + 关键决策"，不编造历史 Prompt；
  2. M4 以"先收敛再 done"为最终口径，R-017 的 57 例仅作历史快照；
  3. M5 交付清单只保留源码、文档与截图等可验证产物。
- **结论**：M3~M5 的留痕与当前实现一致；旧记录保留为历史快照，最终口径以本条与 §4/§5 为准。

---

### R-027 · PRD 与交付文档核查（2026-11-20 10:00–10:15）

- **阶段**：M5 质量 / 交付核查。
- **目标**：按当前实现校正 PRD 的功能与降级口径，核对交付清单，并跑完整交付门禁。
- **Prompt（摘要）**：> 根据现在项目情况更新 PRD 与交付相关内容，并做交付验证核查。
- **产出**：
  - PRD v1.2/v1.3：五席位/选座、筹备中筛选、纪要 `meta` 与完成态、降级口径、并行席位数量；验收标准逐条按实测重写并标注 I/E2E/单测证据；
  - DELIVERY：新增 §7 交付验证记录，检查表按实测打勾，Prompt 与提交计数更新；
  - PROJECT_PLAN：提交与门禁计数同步。
- **验证方式（命令 + 结果）**：
  - `mvn -f server/pom.xml test` → **84/84**；
  - `pnpm -C web build` → 通过；
  - `pnpm -C e2e test` → **5/5**（E2E-1 新增短议题反例：按钮禁用 + 字数提示，通过后补足 8 字恢复可用）；
  - `bash scripts/build.sh` → 通过，单 jar 约 **57 MB**；
  - 干净库启动 → Flyway `V1~V5` 成功、7 张业务表、6 场样例 discussion、`/api/health` 为 `db=up, llm=local`；
  - OpenAPI → 15 条 path / **19 个 operation**；Swagger 302 正常；
  - 截图 13 张；文档 9 份 md + 6 份 rules；旧口径与提交联系信息无残留。
- **人工决策点** `[me]`：
  1. PRD 保留原目标，只把与实现不一致的“暂停调度 / 500ms 重试 / 并行席位数量”等描述改为实际行为；
  2. 交付核查只记录可复现命令，不把仓库授权、zip 抽检等人工动作标为完成；
  3. 列表首屏 20 场并发约 1.26s 作为已知压力限制保留，不篡改目标值。
- **结论**：PRD / 交付文档与当前实现同口径；代码、测试、单 jar、初始化脚本、OpenAPI、截图均通过核查，剩余人工动作 2 项。

---

### R-028 · GitHub 上传与交付包生成（2026-11-20 10:17–10:25）

- **阶段**：M5 质量 / 交付执行。
- **目标**：把项目上传到 GitHub（无对应仓库则创建），按交付包结构生成压缩归档并抽检。
- **Prompt（摘要）**：> 将整个项目上传到 GitHub；若没有对应仓库则创建；上传完毕后整理并压缩交付内容。
- **产出**：
  - 仓库 `https://github.com/zueszyz/ai-panel-studio`（public，默认分支 `main`），本地 135+ 次提交全部推送，`origin/main` 与工作区同步；
  - 交付包 `D:\rep\delivery\AI圆桌演播厅.zip`（约 8.11 MB，1614 条目），含 `.git` 历史、全部源码/文档/测试/截图。
- **工具调用记录**：
  - `gh repo create ai-panel-studio --public --source=. --remote=origin --push`；
  - `git ls-files` 收集受控文件 + Python `zipfile` 写入归档；
  - `zipfile.testzip()` 与清单抽检验证完整性及排除项。
- **验证方式（命令 + 结果）**：
  - `git status --short --branch` → `## main...origin/main`，无未提交改动（仅 `?? .workbuddy/` 未纳入交付）；
  - `gh repo view` → 仓库可访问、公开、默认分支 `main`；
  - zip 完整性检查 `bad_file=None`；关键文件（README、PRD、DELIVERY、git-history、build.sh、13 张截图）齐全；
  - 排除项抽检：`node_modules/`、`target/`、`data/`、`.workbuddy/`、`.idea/`、`e2e/.tmp/`、数据库与日志均未进入归档（`.env.example` 为模板，按交付结构保留）。
- **结论**：源码与历史已上云，交付压缩就位，评审可直接访问仓库链接查看完整提交演进。

---

## 4. 关键决策点索引
| # | 决策 | 结论 | 记录 | 主要影响文件 |
| --- | --- | --- | --- | --- |
| D-1 | 后端技术栈 | Spring Boot 3.5.16 + JDK 17（非 Node） | R-007 | README §3、ARCHITECTURE 全文 |
| D-2 | 数据库 | SQLite 3.40+（WAL），非 PG/MySQL | R-008 | DATABASE 全文 |
| D-3 | 部署方式 | 本地脚本 + 单 jar，不使用容器 | R-009 | README §5、ARCHITECTURE §6 |
| D-4 | 数据访问 | MyBatis-Plus 3.5.12，不用原生 / 不用 JPA | R-010 | DATABASE §6、ARCHITECTURE §3.9 |
| D-5 | 并发模型 | 顺序调度 + 回合内 `CompletableFuture` fan-out + 两级 `Semaphore` | R-003 | ARCHITECTURE §3.2/§3.4 |
| D-6 | 实时通道 | 单 SSE 连接 + `messageId` 多路复用（非每 agent 一条） | R-003 / R-005 | ARCHITECTURE §3.3、API §3 |
| D-7 | 数据模型 | 7 表；`agent_runs` 独立建表；`(discussion_id, client_msg_id)` 幂等键 | R-004 | DATABASE §2/§5 |
| D-8 | 席位唯一性 | 部分唯一索引 `WHERE seat_id IS NOT NULL` | R-004 / R-008 | DATABASE §5.4 |
| D-10 | 交付优先级 | 产品完成度 30 分优先：纪要与溯源先于 P1 功能 | R-011 / R-012 | DELIVERY §4、PROJECT_PLAN §6 |

### 4.1 按原需求四范式归组（SDD / DDD / TDD / E2E）

| 范式 | 原需求要求 | 本项目对应记录 | 说明（意图 / 挑战 / 如何修正） |
| --- | --- | --- | --- |
| **SDD**<br/>数据建模与 API 契约生成 | 先有 schema 与 contract，再写实现 | R-004、R-005、R-008 | 意图：把 7 表与 17 接口先定死。挑战：数据库从 PG 换 SQLite，`jsonb`/`enum`/`text[]` 全要重映射。修正：枚举→`TEXT+CHECK`、JSON→`TEXT`+`JacksonTypeHandler`、部分唯一索引保留 |
| **DDD**<br/>前端组件与页面生成 | 按领域概念切组件，而非按页面堆代码 | R-012、PRD §8 | 意图：让组件名与领域词一致（Seat/Message/Trace/Minutes）。挑战：席位状态、流式气泡等交互无处可抄。修正：按场景自建组件 + `copy.ts` 统一中文文案 + 设计令牌 |
| **TDD**<br/>核心逻辑测试用例先于业务实现 | 先写断言，再写实现 | R-006、TESTING §4 | 意图：并发与策略这种"看起来对"的代码，必须先有可量化的断言。挑战：并行/超时难以稳定复现。修正：WireMock 注入延迟与故障，断言改为时间戳先后而非"感觉并行" |
| **E2E**<br/>端到端测试与质量闭环 | 跑通真实浏览器主链路 | TESTING §2（E2E-1~5）、§7 自检 | 意图：证明"刷新后纪要还在""两路同时吐字"。挑战：LLM 不稳定导致用例假红。修正：全量走 WireMock + 固定时序；定位用 `data-testid` + 中文文案 |

---

## 5. AI 走偏与修正记录

> 原需求要求写清"AI 失败/走偏时如何修正"。以下均为真实发生，保留现象与修复位置。

| # | 现象 | 影响 | 修正 | 验证 |
| --- | --- | --- | --- | --- |
| F-1 | 首版 `API.md` 标题写"15 个接口"，但表格实际列了 17 行 | 文档自相矛盾，评审一眼看出没核对 | 改为 17（REST 14 + SSE 3），并在 §1 加"规模对照"行 | 逐行数表 + 与 README/PROMPT_LOG 交叉核对 |
| F-2 | 换栈后旧表述残留（`docker compose`、`Prisma`、`Fastify`、"pnpm workspace"） | 文档互相打架，显得是"复制模板没消化" | `grep` 全量扫描后逐条替换；把"残留检查"写进 DELIVERY §6 自检 | 关键词 grep 结果为空 |
| F-3 | 文档混用 "MyBatis" 与 "MyBatis-Plus" | 易被读成"用原生 MyBatis 手写 SqlSession" | 统一为 MyBatis-Plus 3.5.12，并显式声明"不用原生 / 不用 JPA" | grep `MyBatis[^-]` 仅剩"MyBatis 原生"对比语境 |
| F-4 | 目录树编辑时误删换行，两行被合并 | 文档排版损坏 | 读取该段落 → 精确重写两行 | `sed` 查看段落确认 |
| F-5 | 早期 ER 图把轨迹塞进 `messages.extra`（JSON） | 轨迹无法按时间线查询，FR-5 会做不出来 | 独立 `agent_runs` 表（含 trigger/phase/duration） | DATABASE §2.6 + 集成用例 I-11 |
| F-6 | 手写 `@Select` 查询带 JSON 列的实体，`focus` / `meta` 读回后变空（`[]` / `{}`） | 席位卡不显示关注点、选人打分少一档；轨迹面板技术细节永远为空 | MyBatis-Plus 的 `autoResultMap` 只对内置方法生效，手写 SQL 必须显式 `@ResultMap("mybatis-plus_XxxEntity")`；同时 `updateById(patch)` 会把默认空 List 写回 `'[]'`，状态更新改用 `LambdaUpdateWrapper` 只 set 目标列 | `mvn -f server/pom.xml test` 53 项全绿；新增「认领/撤销不清空 focus」「轨迹 meta 回读」两条回归用例 |
| F-7 | dev 脚本启动后库落在 `server/data/`，根 `data/` 里是另一份旧库 | 「删 `data/panel-studio.db` 重启」自检失效；接口报 404 而库文件里明明有这条数据；手工修数据修到了错的那个库 | `spring-boot:run` 会把 JVM 工作目录切到 `server/`，相对路径 `SQLITE_PATH` 随之漂移。`scripts/dev.bat` / `dev.sh` 统一把相对路径归一化为「仓库根/data/panel-studio.db」；两份库已合并到根目录 | 启动后 `GET /api/discussions?limit=50` 返回 8 条、`server/data/` 不再生成库文件；`/api/health` 仍 `db=up` |
| F-8 | `SseRegistry` 的事件缓冲用 `ArrayDeque`，`publish` 无锁 `addLast`、`trim` 单独 `synchronized` | 并行输出时两个 agent 同时写缓冲：可能丢帧，极端情况 `ConcurrentModificationException` 直接打断一次 `publish` | 写入 + 裁剪放进同一把锁（`synchronized (buffer)`），回放也在锁内读，并注释说明"调用方必须持锁" | `mvn test` 全绿；断线续传用例（重连带 `Last-Event-ID`）回放连续 |
| F-9 | 收束消息 `digest.start` 的 id 是"预生成的一个"，实际消息又是另一个 | 前端点轨迹「主持人收束」定位不到消息（scroll 到空处），FR-5 的联动失效 | `runDigest` 先生成一次 id 并同时传给 `digest.start` 与 `runSeat`，两者必须同一个 | 轨迹条目点击后能定位；`MinutesApiIntegrationTest` 用的同一套事件流断言通过 |
| F-10 | `start()` 先 `transit(LIVE)` 再检查席位是否为空 | 席位缺失时接口报错但状态已变 LIVE，用户既开不了场也退不回 `PANEL_READY` | 校验前置：先查席位，再迁移状态 | I-6 用例 + 手工删席位场景 |
| F-11 | Live 页用 `.page{overflow-y:auto}` + `max-height:62vh` 拼出"三栏" | 实际整页滚动，rules/20 §6 的硬约束没达成；窄屏没有轨迹抽屉 | 根容器 `height:100%+overflow:hidden`，`.flow__list` 独立滚动、输入区 `flex:none`；<1280 轨迹改为覆盖式抽屉，<768 席位横排到顶部 | 四档断点手测无整页滚动、无横向滚动条；`pnpm build` 通过 |
| F-12 | 失败消息卡只有一行"发言中断"，卡片中间一大块空白；刷新后席位栏全显示"等待"，而轨迹面板写着"失败" | 用户不知道发生了什么（PRD 要求"原因 + 下一步"）；FR-5 明说"席位状态必须与消息流一致"，刷新即打脸 | 卡片内显示 `message.error`（无 code 时用"这次没有生成内容，可以重试"）；连续两条失败才升级为页面级横幅；`handleSnapshot` 从消息状态反推席位状态（STREAMING→发言中、FAILED→中断、DONE→完成） | 截图 `03-live.png` 中四个席位均显示"中断"、卡片带原因；Lint/Build 通过 |
| F-13 | 反复跑 `e2e/screenshot.mjs` 后，截图里出现同一议题的 3 张卡片 | 截图脚本每跑一次就新建一场圆桌，写进了本地库；交付截图看起来像"数据脏" | 截图前用「删库 + Flyway 重建」拿到纯种子数据（顺带验证初始化脚本可交付）；脚本里说明"lobby 截图在创建圆桌之前拍" | 重建后 `GET /api/discussions` 返回 6 条、议题唯一；截图无重复卡片 |
| F-14 | SSE 只处理"读到错误"，对半开连接无感知；且重连 3 次后就 `onClose('error')` 永久放弃 | 地铁/切网时页面会永远停在旧内容，用户以为"没人说话"，其实是流早断了；恢复网络也不自愈 | ①客户端加 30s 空闲看门狗（服务端心跳 15s）：超时主动 abort 走重连；②重连不再设上限，退避封顶 4s，横幅常驻到恢复为止 | `E2E-5`：拦截 `/stream` → 横幅出现 → 放行后自动重连、快照补齐且无重复气泡 |
| F-15 | 消息列表按 SSE 到达顺序 `push`，而用户消息的 `message.start` 可能晚于并行专家的 `message.start` | 页面出现"回答在提问之前"，可读性直接崩 | `upsertMessage` 改按 `createdAt` 落位，同毫秒返回 0（保持到达顺序）；不用 ULID 兜底 —— 同毫秒内随机段不单调，反而会排错 | `E2E-2` 断言"插话之后才有专家回复"，此前该用例正是靠这条断言发现的 |
| F-16 | R-016 只靠 `busy_timeout + IMMEDIATE` 兜写锁，`ARCHITECTURE §3.7` 约定的应用层退避一直没实现 | 并发 agent 收尾撞 `SQLITE_BUSY_SNAPSHOT` 时 `busy_timeout` 无效，个别消息落库失败（接口偶发失败） | 加 `SqliteBusyRetryInterceptor` 拦 `Executor.update`，50/100/200ms 三次退避；识别扩展锁码；命中记日志与 `sqliteBusy` 指标 | `SqliteBusyRetryInterceptorTest` 断言退避序列与放弃策略；`SqliteBusyIntegrationTest` 20 线程并发写无泄漏、P95 < 200ms |
| F-17 | API §4 的 `ALREADY_STARTED` / `DISCUSSION_STATE_INVALID` / `MINUTES_NOT_FOUND` / `SEAT_NOT_CLAIMABLE` 在 `lib/copy.ts` 没有映射 | 用户看到「出了点问题」而不是具体原因 | 补齐四条中文文案 | 错误码集合与 API §4 逐项比对一致 |
| F-18 | E2E `waitForRunsSettled` 把空轨迹表判定为「已收尾」 | 上一轮发言还在 inflight 时就发插话，插话被跳过，E2E-4 误报失败 | 加 `minRuns=1`：轨迹表至少有一条记录且无 busy 才认为收尾 | E2E-4 稳定通过 |
| F-19 | 插话选中的席位都还在发言时整轮被跳过，用户消息没有任何回应 | 违反 FR-4「任意时刻可发言、下一轮优先回应」；E2E-4 因此拿不到失败卡片 | 插话回合先检查 inflight：目标都忙时延后 300ms 重排（最多 3 次），仍忙则回 `AGENT_BUSY` 提示；不推进回合号 | 修复后 `pnpm -C e2e test` 5 条全绿；插话必有回应 |
| F-20 | E2E global teardown 用异步 `spawn('taskkill')` 后立刻退出 | java/vite/mock 残留，占住 8080/5173/9099 与 `e2e.db`，下一次 pretest 删库报 EPERM | 改用同步 `spawnSync`，确保进程退出后再返回 | 连续两次 E2E 结束后 `netstat` 三个端口均无监听 |
| F-21 | 插话延后条件用了 `allMatch`：只有部分目标席位忙时其余仍被跳过 | E2E-2「同时点名两位」只出现一条流式，断言偶发红 | 改为 `anyMatch`：只要有一位目标席位忙就整轮延后，保证被点名的专家一起回应 | E2E-2 连续两次全绿 |
| F-22 | `AgentRunner` 只 catch `LlmException`：终态落库失败时异常被上层 future 吞掉，消息卡在 STREAMING | 用户看到永远"正在生成"，与 FR-9「本条可能未保存」不符 | catch `DataAccessException`，发 `message.failed` + `DB_WRITE_FAILED`，卡片转 FAILED 并可单条重试 | `AgentRunnerTest#shouldReportDbWriteFailure` 断言失败码与事件数 |
| F-23 | LLM_NOT_CONFIGURED 文案暗示整场可离线跑，实际预设只生成席位 | 按提示操作后发言全失败、纪要 MINUTES_EMPTY | 文案与 API 错误表明确「预设仅席位」，组局失败卡补提示 | web build 通过；文案与 API.md §4 一致 |
| F-24 | SSE 客户端断开时 IOException 落到全局 handler，试图把 ApiError 写进 text/event-stream 响应，引发 HttpMessageNotWritableException 连锁 ERROR | 日志被噪音淹没（功能不受影响） | IOException 归为客户端断开仅记 debug；响应已提交或为 SSE 时 handler 不再写错误体 | ApiExceptionHandlerTest 断言 SSE 无错误体、普通响应仍 500 |
| F-25 | 插话延后只重试 3 次 × 300ms，慢席位（假模型 1.8s / 真实首 token 45s）没结束就放弃 | 用户插话被丢弃，现场没有任何回应 | 改为按 messageId 记录 30s 截止时间，期间每 300ms 重排一次 | SessionActorTest + E2E-2 全绿 |
| F-26 | E2E-2 用 /runs 判断「已收尾」，但轨迹表只在发言结束时写，看不到仍在 inflight 的发言 | 插话时上一轮还占着席位，并行断言偶发红 | 先断言页面没有 STREAMING 气泡，再发插话 | E2E-2 连续两次全绿 |
| F-27 | 大厅 `ongoing` 混入 `PANEL_READY`，卡片直接进现场，其他用户找不到加入入口 | 筹备中的圆桌无法被其他用户认领 | 新增「筹备中」筛选；`DRAFT/PANEL_READY` 卡片进组局页并显示「可加入」 | 大厅筛选与 `openSeatCount` 接口字段 |
| F-28 | `SessionActor` 只按角色过滤，用户接手的席位仍由 AI 发言 | 用户认领后现场出现"本人席位被 AI 代说" | 选发言人时排除 `REPLACED_BY_USER`；开场后禁止再换座 | `SessionActorTest#shouldSkipSeatClaimedByUser`；开场后认领返回 `ALREADY_STARTED` |
| F-29 | 组局页在空席位时自动生成，用户没有机会先选自己的席位 | 无法实现"1 主持 + 4 专家里自选一席"的产品要求 | 空面板改为选座面板；用户点「生成其他席位并开始」后由服务端保留所选角色并自动开场 | E2E-1 选座流程全绿；`PanelApiIntegrationTest#shouldReserveUserSeatAndAutoStart` |
| F-30 | 纪要 `minutes.done` 先于状态收敛发送，前端也从不刷新讨论状态；`loadMinutes` 不重置离线/错误/分节草稿 | 生成完成后徽标仍显示「纪要生成中」，切换版本会继承上一版的离线提示与错误 | 后端先收敛再发 done 并带上 `status`；前端 onDone 更新徽标 + `refreshStatus()` 自愈；`loadMinutes` 重置 `offline`/`error`/`sections` | `MinutesApiIntegrationTest#shouldVersionMinutes` 断言 CLOSED；E2E-3 断言徽标「已结束」 |

---

## 6. AI 生成 / 人工修改声明（对应 README §7）

| 内容 | 归属 | 说明 |
| --- | --- | --- |
| PRD 结构与文案初稿 | `[ai-gen]` | 模板化章节由 AI 起草 |
| 需求边界（不做什么）、验收标准 | `[ai-gen+edit]` | AI 给功能，人工定"算做完"的标准 |
| 架构分层、Actor/调度设计 | `[ai-gen+edit]` | 方案由 AI 给，取舍（单连接多路复用/不全局排序/delta 不落库）由人拍 |
| ER 表结构 | `[ai-gen+edit]` | 增 `agent_runs`、幂等键、部分唯一索引为人工补充 |
| API 契约 | `[ai-gen+edit]` | 补续传/引用字段/expectedResponders |
| 测试策略 | `[me]` | WireMock 故障注入与可量化断言为人工设计 |
| 计划与提交切分 | `[ai-gen+edit]` | 按实际开发节奏调整 |
| 技术栈（Spring Boot 3.5.16 / JDK 17 / Vue 3） | `[me]` | 既定前提；衍生选型由 AI 建议、人工拍板（R-007） |
| 数据库（SQLite + MyBatis-Plus + Flyway） | `[ai-gen+edit]` | 表结构 AI 出，单写者应对策略人工定（R-008） |
| 运行方式（不使用容器） | `[me]` | R-009；启动脚本与交付路径为人工设计 |

> 原则：**AI 负责"写得全"，人负责"判断对"**。凡涉及并发正确性、数据一致性、产品取舍的地方，都在记录里标明了人工决策点。

---

## 7. 工具调用记录（按需追加）

| 时间 | 工具 | 用途 | 结果 |
| --- | --- | --- | --- |
| 2026-11-17 16:2x | 文件系统 | 在 `D:\rep\ai-panel-studio` 建目录、`git init` | 仓库初始化成功 |
| 2026-11-17 16:2x | 文件写入 | 生成 `README.md` 与 `docs/*` 共 7 份文档 | 完成，按文档粒度分批提交（9 次） |
| 2026-11-17 16:3x | git | 按文档粒度提交（docs 阶段） | `docs(log)` … `chore` 共 9 条 |
| 2026-11-17 16:31 | 文档改写 | 技术栈迁到 Spring Boot 3 / JDK 17 / Vue 3 | README + ARCHITECTURE 重写，PRD 增 §4.4 |
| 2026-11-17 16:34 | 文档改写 | 数据库改 SQLite | DATABASE v1.2，ER/DDL/索引/并发策略全量更新 |
| 2026-11-17 16:35 | 文档改写 | 移除 Docker 部署 | ARCHITECTURE §6、README §5、TESTING、PLAN、.env.example |
| 2026-11-17 16:38 | 文档修订 | Spring Boot 版本锁定 3.5.16，同步 MyBatis-Plus 3.5.12 / Flyway 11 | 全文档版本号一致 |
| 2026-11-17 16:46 | 文档改写 | 数据访问统一 MyBatis-Plus 口径并扩写使用约定 | DATABASE §6、ARCHITECTURE §3.9 |
| 2026-11-17 16:47 | 文档新增 | 按需求说明补交付材料 | 新增 `docs/DELIVERY.md`；README §8/§9、PRD §4.5、PLAN §6、TESTING §7 |
| 2026-11-18–19 | 命令执行 / 文件写入 | M3 讨论内核：SessionActor、SpeakerPolicy、SseRegistry、插话与轨迹 | 并行/SSE 用例通过 |
| 2026-11-19 | 命令执行 / 文件写入 | M4 收敛：MinutesService 六段、版本化、离线摘要、完成态 | 纪要集成用例通过 |
| 2026-11-20 | 命令执行 / 文件写入 | M5 质量：84 例、5 条 E2E、压测、单 jar、截图与文档同步 | 全部门禁通过；截图 13 张 |
| 2026-11-20 | grep / 文档改写 | 清理旧口径，补齐 M3~M5 留痕与交付清单 | 旧口径无残留（历史样例已标注） |
