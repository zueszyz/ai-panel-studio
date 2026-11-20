# 测试设计 · AI Panel Studio

| 项 | 内容 |
| --- | --- |
| 版本 | v1.1（栈：Spring Boot 3.5.16 / JDK 17 / SQLite / Vue 3；不使用容器） |
| 层次 | 单元（纯 Java 函数）→ 集成（MockMvc + 临时 SQLite）→ E2E（Playwright） |
| 硬性要求 | E2E ≥ 3 条；本文设计并实现 5 条（前 3 条为必过项） |
| 原则 | 可重复、可离线（LLM 用 WireMock 造）、断言业务结果而非像素 |
| 运行 | 全部本机执行，不需要 Docker |

---

## 1. 测试策略

| 层 | 工具 | 覆盖对象 | 目标 |
| --- | --- | --- | --- |
| 单元 | JUnit 5 + AssertJ + Mockito | `SpeakerPolicy`、`ContextBuilder`、`DiscussionStateMachine`、JSON 修复器、ULID 生成器 | 策略可解释、边界明确、无 IO |
| 集成 | `@SpringBootTest` + MockMvc + 临时 SQLite（每类一个临时文件）+ WireMock | 19 个接口的状态码、幂等、事务、SSE 帧序列 | 契约不被破坏 |
| E2E | Playwright（Node）+ `e2e/mock-llm.mjs` | 真实浏览器 + 真实前后端（`spring-boot:run`）+ OpenAI 兼容假模型 | 5 条主链路全绿，`pnpm -C e2e test` 一条命令跑完 |
| 前端单测 | Vitest（可选） | SSE 解析器（跨 chunk 截断）、store 去重逻辑 | 流式解析不出错 |

### 1.1 LLM 如何 Mock（关键）

用 **WireMock 3** 内嵌在测试进程里，暴露一个 OpenAI 兼容端点（`/v1/chat/completions`）：

| 能力 | 实现方式 |
| --- | --- |
| 流式回放 | `ResponseTemplateTransformer` 拼 `data: {...}\n\n` 帧，`withChunkedDribbleDelay(40, 3000)` 让 token 分 40 片、总时长 3s 吐出 |
| 单席位变慢 | 按请求体里的 `seatId` 匹配 → `withFixedDelay(8000)`（验证"慢 agent 不阻塞别人"） |
| 超时 / 429 / 402 | WireMock `Scenario` + `withStatus(429)`，或故意 `withFixedDelay(60000)` 触发客户端 watchdog |
| 脏 JSON（席位生成） | 返回带 markdown 围栏 / 尾逗号的假 JSON，验证修复器与降级 |
| 录制回放 | `RecordSpec` 录一次真实响应存为 `*.json`，后续离线回放（不花钱、不抖动） |

```java
stubFor(post(urlEqualTo("/v1/chat/completions"))
    .withRequestBody(matchingJsonPath("$.metadata.seatId", equalTo("st_3")))
    .willReturn(aResponse()
        .withStatus(200)
        .withHeader("Content-Type", "text/event-stream")
        .withChunkedDribbleDelay(40, 8000)     // 慢席位：8s
        .withBodyFile("llm/slow-seat.stream")));
```

- 测试 profile：`application-test.yml` 把 `LLM_BASE_URL` 指向 `http://localhost:${wiremock.port}/v1`。
- 生产/演示：真实 provider；E2E 走假模型，保证"5 条用例永远绿"，不因模型抽风而假红。

> **E2E 用的是另一套假模型**：`e2e/mock-llm.mjs` 起一个 OpenAI 兼容端点（默认 `:9099`），
> 按 `PromptTemplates` 里的标记（`SEAT_INDEX` / `你叫…` / `你是这场圆桌会议的主笔`）分派固定脚本，
> 服务端不需要任何测试专用分支，只把 `LLM_BASE_URL` 指过去即可。
> 延迟也是脚本化的：周敏 600ms、陈立 1800ms —— 用来断言"快的先结束、慢的不挡快的"。

### 1.2 SQLite 在测试中的处理

| 项 | 做法 |
| --- | --- |
| 隔离 | 每个测试类用独立临时库：`jdbc:sqlite:${java.io.tmpdir}/panel-test-${UUID}.db`，`@AfterAll` 删除 |
| 结构 | 不手工建表：Flyway 在 `@SpringBootTest` 启动时自动迁移（顺带验证脚本可用） |
| 种子 | 集成测试用 `V3__seed_demo.sql` + 各用例自建数据；E2E 用 API 预置，`afterEach` 清理 |
| 并发用例 | 串行执行（`@Execution(SAME_THREAD)`），因为 SQLite 单写者会被并行测试互相干扰 |
| 不需要 | Testcontainers（无独立 DB 服务）、H2（方言不同，测了不算数） |

---

## 2. E2E 用例

> **状态**：5 条全部实现于 `e2e/specs/`，`pnpm -C e2e test` 约 35s 跑完（含启动前后端）。
>
> **定位策略**：断言只用两类锚点 —— ① `data-testid`（业务语义，如 `seat-card`、`message-bubble`）；② 中文文案（`getByText('发起圆桌')`）。**不断言样式 class 名**：样式由本项目手写并会随视觉调整变化，锚在语义属性上才稳。

### E2E-1 组局 → 选座 → 生成其余席位 → 自动开场（主链路 A）

| 项 | 内容 |
| --- | --- |
| 前置 | 空库或种子库；WireMock 已就绪 |
| 步骤 | 1. 打开 `/` → 点「发起圆桌」<br>2. 输入议题「我们该不该把推荐系统从规则引擎换成向量召回」<br>3. 选 5 人 → 提交<br>4. 组局页选「行业分析师」席位<br>5. 点「生成其他席位并开始」<br>6. 等待 5 张席位卡逐个浮现，自选席位显示「你」<br>7. 自动进入 `/discussions/:id`，主持人开场 |
| 断言 | 议题在详情页可见；席位 5 个且含 1 位主持人；席位卡含姓名/岗位/立场；自选席位显示「你」且不可再次认领；生成完成后自动进入现场，主持人 `OPENING` 消息流式结束后 `status=done`；控制台无 error |
| 反例 | 议题仅 5 字时提交按钮禁用并显示字数提示 |

### E2E-2 用户插话 → 多 Agent 并行流式 → 观点溯源（主链路 B）

| 项 | 内容 |
| --- | --- |
| 前置 | 复用 E2E-1 产出的 LIVE 圆桌（或 seed 的 LIVE 场次） |
| 步骤 | 1. 输入「冷启动没有行为数据，向量召回拿什么训练？」并发送<br>2. 观察 ≥2 张消息卡**同时**进入流式<br>3. 记录两张卡首个字符出现的时间差<br>4. 点击某条专家消息的引用徽标 `↩ 回应 你` |
| 断言 | 用户消息 <300ms 上屏；≥2 张卡处于 streaming 且 delta **交错**到达（不是串行）；首 token 时间差 < 3s；被引用消息滚动到视口并高亮；每条消息可见「第 N 回合 · 时间 · 发言人」；轨迹面板对应席位从 `思考中 → 发言中 → 完成` |
| 关键 | WireMock 把 `st_2` 设为 8s 慢响应、`st_4` 为 1s，断言 **`st_4` 的 `message.end` 早于 `st_2`** —— 证明不阻塞 |

### E2E-3 已结束讨论 → 生成纪要 → 刷新持久化（主链路 C）

| 项 | 内容 |
| --- | --- |
| 步骤 | 1. 用接口准备一场已结束、有专家发言的圆桌（现场点「结束讨论」会自动跳转 `?auto=1` 并生成，本用例直接验证生成与落库）<br>2. 打开 `/discussions/:id/minutes`<br>3. 点「生成纪要」，等 6 个分段依次出现<br>4. **Hard reload** 后再次断言<br>5. 点某条要点的「溯源」 |
| 断言 | 纪要六段齐全（议题/主张/分歧/共识/假设/下一步）；生成完成后状态徽标从「纪要生成中」收敛为「已结束」；刷新前后文本完全一致（来自 SQLite 而非前端缓存）；溯源跳回详情页并高亮原消息；复制/下载 Markdown 内容含 `# 圆桌纪要` |

### E2E-4 失败降级：单席位超时不影响全场（加分）

WireMock 令 `st_3` 超时（`withFixedDelay(60000)`）。断言：该卡片「发言中断 · 重试」，同回合其他卡片正常 `done`；主持人收束仍产生；点重试后成功（`Scenario` 第二次返回正常）；**整场状态仍为 `LIVE`**。

### E2E-5 断线重连：不丢消息（加分）

流式中用 `page.context().setOffline(true)` 断网 5s 再恢复。断言：「连接中断，正在恢复」提示出现；恢复后本地消息与 DB 一致（条数 + 内容）；无重复气泡（`clientMsgId` 幂等生效）。

---

## 3. 集成测试清单

| # | 用例 | 断言要点 |
| --- | --- | --- |
| I-1 | `POST /discussions` 参数校验 | 7 字议题 → 400 且 `details[0].field === 'topic'` |
| I-2 | `POST /discussions/:id/panel` 帧序列 | 首帧 `panel.analysis`，随后 N 个 `seat.added`，末帧 `panel.done`；顺序稳定；五席位选座保留用户席位并自动开场（`shouldReserveUserSeatAndAutoStart`） |
| I-3 | panel 重复调用 | 已有 `PANEL_READY` 时席位 id 不变（不重建） |
| I-4 | `claim` 冲突 | 第二人认领同席位 → 409 `SEAT_TAKEN`；开场后认领 → 409 `ALREADY_STARTED`（验证部分唯一索引与筹备阶段约束） |
| I-5 | `messages` 幂等 | 同 `clientMsgId` 两次 → 一条消息、只触发一轮调度 |
| I-6 | 非 LIVE 发言 | `DRAFT` → 409 `DISCUSSION_NOT_LIVE` |
| I-7 | `end` 后发言 | 410 `DISCUSSION_CLOSED` |
| I-8 | minutes 版本 | 连生两次 → `version` 1、2；`?version=1` 可取旧版；参会席位/回合/用户关切写入 `structured.meta`（`shouldVersionMinutes` / `shouldIncludeParticipantFactsAndUserQuestions`） |
| I-9 | minutes 失败不覆盖 | 模型连续返回非 JSON → 旧版仍 `READY`，流里含 `keptVersion`（`#shouldKeepPreviousVersionOnFailure`） |
| I-10 | 预算耗尽 | 打满 `budget` → 402 `BUDGET_EXHAUSTED`，已有消息可读（阈值由 `TokenBudgetTest` 覆盖，402 出口在 `DiscussionSessionService#postMessage`） |
| I-11 | SSE 续传 | 带 `Last-Event-ID` 重连 → 不丢不重（`SseReplayIntegrationTest#shouldReplayOnlyFramesAfterCursor`） |
| I-12 | 软删除 | 删除后列表不出现；直链 410（`DiscussionApiIntegrationTest#shouldSoftDeleteOnlyAsCreator`） |
| I-13 | SQLite 并发写 | 20 线程并发 `message.end` → 无 `SQLITE_BUSY` 泄漏到接口（重试生效），耗时 P95 < 200ms（`SqliteBusyIntegrationTest#concurrentEndWritesAreSerializedWithoutBusyLeaks`，退避序列由 `SqliteBusyRetryInterceptorTest` 断言） |
| I-14 | **多讨论隔离** | 同时开 A/B 两场，向 A 发消息：B 的 `/stream` 收不到任何 A 的事件；两场 `turnIndex` 独立推进；A 的纪要只含 A 的 `messageId` |
| I-15 | **密钥边界** | 任意响应体、错误信息、`web/dist` 产物中均不含 `sk-` / `apiKey`；前端直连 LLM 域名的请求为 0 条（`ApiHealthIntegrationTest#shouldNotLeakKeys` + `scripts/build.sh` 产物扫描） |

## 4. 单元测试清单

- `SpeakerPolicy.pick()`：被点名优先、被引用未回应优先、本回合已发言者降权、K 上限、无候选回退到"最久未发言"、主持人永不入选。
- `ContextBuilder`：最近 6 条原文 + 滚动摘要、超长引用截断、token 预算裁剪不破坏引用完整性。
- `DiscussionStateMachine`：非法迁移抛错（`CLOSED → LIVE` 拒绝）、`INTERRUPTED` 双向合法。
- `JsonRepair`：markdown 围栏包裹、尾逗号、单引号 → 均能修复；修复失败抛 `LLM_JSON_INVALID`。
- `UlidGenerator`：同毫秒内单调递增（保证 `ORDER BY id` ≈ 时间序）、前缀正确。

---

## 5. 验收清单（对照 PRD）

| PRD | 验收点 | 验证方式 |
| --- | --- | --- |
| FR-1 | 列表/筛选/发起 | E2E-1 + I-1 |
| FR-2 | 席位流式生成、换一批上限、失败降级 | I-2 / I-3 + E2E-4 |
| FR-3 | 选座/开场前认领/撤销/旁听、刷新保持、开场后拒绝换座 | E2E-1 + I-4 |
| FR-4 | 多轮、并行不阻塞、单点失败隔离 | E2E-2 + E2E-4 |
| FR-5 | 轨迹状态一致、可回放 | E2E-2 + I-11 |
| FR-6 | 溯源跳转 | E2E-2 / E2E-3 |
| FR-7 | 六段纪要、参会信息、落库、版本、完成态 | E2E-3 + I-8 / I-9 |
| FR-8 | 结束即停、软删除 | E2E-3 + I-12 |
| FR-9 | 超时/限流/断线 | E2E-4 / E2E-5 |
| 非功能 | 首 token、并发、日志 | `/api/metrics` + `scripts/stress.mjs`（20 场 × 4 席位压测） |

> 2026-11-20 补充：I-2/I-4/I-8 已覆盖五席位选座、开场后认领拒绝、纪要 `structured.meta` 与 `minutes.done.status`；E2E-1 改为选座后自动开场，E2E-3 增加完成态徽标断言。

---

## 6. 执行方式

```bash
# 后端：单元 + 集成（Flyway 建临时库，WireMock 内嵌，84 例）
mvn -f server/pom.xml test

# 前端：vue-tsc 类型检查 + 构建（Vitest 未接入，前端行为由 E2E 覆盖）
pnpm -C web build

# E2E：一条命令拉起「假模型 → 干净库 + 后端 → 前端」，跑完自动收尾
pnpm -C e2e test
```

- E2E 的环境由 `e2e/global-setup.ts` 统一拉起：删 `e2e/.tmp/e2e.db` → 假模型 `:9099` → 后端 `:8080` → 前端 `:5173`；
  跑之前确保这三个端口没被 `scripts/dev` 占用。
- 每条用例自建 `discussion`（走 API 预置），互不干扰；结束不做清理，由下一次 globalSetup 删库。
- 失败只留截图（`screenshot: 'only-on-failure'`），trace/video 默认关闭：单次失败会产生上百个小文件，
  本机安全策略会拦截批量删除，清理阶段容易卡住；需要复盘时临时在 `playwright.config.ts` 打开。
- 顺序（本地/CI 同）：`mvn test` → `pnpm -C web build` → `pnpm -C e2e test`。
- 不使用容器：E2E 依赖的"服务"就是本机三个进程（含一个假模型）。

---

## 7. 交付前自检（打包前必跑）

- [ ] `mvn -f server/pom.xml test` 全绿（单元 + 集成 84 条，含 SQLite 并发写）
- [ ] `pnpm -C e2e test` 全绿（5 条，约 35s）
- [ ] 删除 `data/panel-studio.db` 后重启，Flyway 三个脚本自动建库并 seed 成功（证明"数据库初始化脚本"可交付）
- [ ] `/swagger-ui.html` 可访问，接口数量与 [API](API.md) §1 一致（19 个）
- [ ] 压缩包不包含 `node_modules/`、`server/target/`、`data/`
