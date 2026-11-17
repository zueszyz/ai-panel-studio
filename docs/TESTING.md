# 测试设计 · AI Panel Studio

| 项 | 内容 |
| --- | --- |
| 版本 | v1.0 |
| 层次 | 单元（纯函数）→ 集成（API + LLM Mock）→ E2E（Playwright） |
| 硬性要求 | E2E ≥ 3 条（本文设计 5 条，前 3 条为必过项） |
| 原则 | 可重复、可离线（LLM 可 Mock）、断言业务结果而非像素 |

---

## 1. 测试策略

| 层 | 工具 | 覆盖对象 | 目标 |
| --- | --- | --- | --- |
| 单元 | Vitest | `domain/policy/speaker.ts`、`context-builder.ts`、状态机迁移、JSON 修复器 | 策略可解释、边界明确 |
| 集成 | Vitest + Fastify inject + Prisma test db | 15 个接口的状态码、幂等、事务、SSE 帧序列 | 契约不被破坏 |
| E2E | Playwright | 真实浏览器 + 真实前后端 + LLM Mock 服务 | 主链路可交付 |

### 1.1 LLM 如何 Mock（关键）

自建 `mocks/llm-server`：一个 OpenAI 兼容的极简服务，按 `fixtures/*.json` 回放，支持：

- **按时序流式吐出**（每 60–120ms 一个 chunk），让并行/流式可见、可断言；
- **可编排故障**：`?fail=st_3` 让指定席位超时、`?fail=json` 让席位生成返回脏 JSON、`?slow=8000` 拉长首 token；
- **录制模式**：真实调用一次并落盘，后续离线回放（避免测试花钱、避免不稳定）。

`docker-compose.test.yml` 只起 `db + mock-llm + server`，E2E 用 `LLM_BASE_URL=http://mock-llm:9100/v1`。

---

## 2. E2E 用例

### E2E-1 组局 → 生成席位 → 认领 → 开场（主链路 A）

| 项 | 内容 |
| --- | --- |
| 前置 | 空库或种子库；mock 已就绪 |
| 步骤 | 1. 打开 `/` → 点「发起圆桌」<br>2. 输入议题「我们该不该把推荐系统从规则引擎换成向量召回」<br>3. 选 4 人 → 提交<br>4. 断言进入 `/discussions/:id`，处于"生成中"<br>5. 等待 4 张席位卡出现（逐个浮现）<br>6. 点第 3 张「我来坐这里」<br>7. 点「进入圆桌」 |
| 断言 | 议题文本在详情页可见；席位数为 4 且含 1 位主持人；席位卡含姓名/岗位/立场；认领后头像变「你」且标识 `已认领`；开场后出现主持人 `OPENING` 消息且流式结束后 `status=done`；URL 未跳走、控制台无 error |
| 反例 | 议题仅 5 个字时提交按钮禁用并显示字数提示（同用例内追加） |

### E2E-2 用户插话 → 多 Agent 并行流式 → 观点溯源（主链路 B）

| 项 | 内容 |
| --- | --- |
| 前置 | 复用 E2E-1 产出的 LIVE 圆桌（或用 seed 的 LIVE 场次） |
| 步骤 | 1. 在输入框输入「冷启动没有行为数据，向量召回拿什么训练？」并发送<br>2. 观察至少 2 个消息卡片**同时**进入流式<br>3. 记录两张卡首个字符出现的时间差<br>4. 点击某条专家消息的引用徽标 `↩ 回应 你` |
| 断言 | 用户消息立即上屏（<300ms）；≥2 张卡片处于 streaming 且**交错**收到 delta（不是串行）；首 token 时间差 < 3s；被引用的用户消息被高亮滚动到视口；每条消息可见「第 N 回合 · 时间 · 发言人」；轨迹面板对应席位状态从 `思考中 → 发言中 → 完成` |
| 关键 | 用 mock 把 A 席位设为 8s 慢响应、B 席位 1s，验证 **B 不被 A 阻塞**（B 的 `message.end` 早于 A 的 `message.end`） |

### E2E-3 结束讨论 → 生成纪要 → 刷新持久化（主链路 C）

| 项 | 内容 |
| --- | --- |
| 步骤 | 1. 点「结束讨论」→ 二次确认<br>2. 断言输入框禁用、出现「生成纪要」主按钮<br>3. 点生成，等待 6 个分段依次出现<br>4. 打开 `/discussions/:id/minutes`<br>5. **刷新页面**（hard reload）→ 再次断言纪要内容<br>6. 点击纪要中某条要点的「溯源」 |
| 断言 | 结束后 3s 内 SSE 断开且不再有新消息；纪要六段齐全（议题/主张/分歧/共识/假设/下一步）；刷新后纪要文本与刷新前完全一致（来自 DB）；溯源点击跳回详情页并高亮原消息；复制 Markdown 后剪贴板内容含 `# 圆桌纪要` |

### E2E-4 失败降级：单席位超时不影响全场（加分）

mock 令 `st_3` 超时。断言：该卡片显示「发言中断 · 重试」，同回合其他卡片正常 `done`；主持人收束消息仍产生；点重试后该卡片成功（mock 第二次返回正常）。**整场讨论状态仍为 `LIVE`**。

### E2E-5 断线重连：不丢消息（加分）

在流式过程中用 `page.context().setOffline(true)` 断网 5s 再恢复。断言：出现「连接中断，正在恢复」提示；恢复后本地消息与 DB 一致（条数 + 内容），无重复气泡（`clientMsgId` 幂等生效）。

---

## 3. 集成测试清单

| # | 用例 | 断言要点 |
| --- | --- | --- |
| I-1 | `POST /discussions` 参数校验 | 7 字议题 → 400 且 `details[0].field === 'topic'` |
| I-2 | `POST /discussions/:id/panel` 帧序列 | 首帧 `panel.analysis`，随后 N 个 `seat.added`，末帧 `panel.done`；顺序稳定 |
| I-3 | panel 重复调用 | 已有 `PANEL_READY` 时不重建席位（席位 id 不变） |
| I-4 | `claim` 冲突 | 第二人认领同席位 → 409 `SEAT_TAKEN` |
| I-5 | `messages` 幂等 | 同 `clientMsgId` 提交两次 → 只有一条消息、只触发一轮调度 |
| I-6 | 非 LIVE 发言 | `DRAFT` 状态 → 409 `DISCUSSION_NOT_LIVE` |
| I-7 | `end` 后发言 | 410 `DISCUSSION_CLOSED` |
| I-8 | minutes 版本 | 连生两次 → `version` 1、2；`?version=1` 可取旧版 |
| I-9 | minutes 失败不覆盖 | mock 超时 → 旧版仍 `READY`，接口返回 `keptVersion` |
| I-10 | 预算耗尽 | 打满 `budget` → 402 `BUDGET_EXHAUSTED`，已有消息可读 |
| I-11 | SSE 续传 | 带 `Last-Event-ID` 重连 → 不丢事件、不重复 |
| I-12 | 软删除 | 删除后列表不出现；直链 410 |

## 4. 单元测试清单

- `speakerPolicy.pick()`：被点名优先、被引用未回应优先、本回合已发言者降权、K 上限、无候选时回退到"最久未发言"。
- `contextBuilder`：最近 6 条原文 + 滚动摘要、超长引用截断、token 预算裁剪不破坏引用完整性。
- `stateMachine`：非法迁移抛错（`CLOSED → LIVE` 拒绝）。
- `jsonRepair`：markdown 代码块包裹的 JSON、尾逗号、单引号 → 均能修复；修复失败抛 `LLM_JSON_INVALID`。

---

## 5. 验收清单（对照 PRD）

| PRD | 验收点 | 验证方式 |
| --- | --- | --- |
| FR-1 | 列表/筛选/发起 | E2E-1 + I-1 |
| FR-2 | 席位流式生成、换一批上限、失败降级 | I-2 / I-3 + E2E-4 |
| FR-3 | 认领/撤销/旁听、刷新保持 | E2E-1 + I-4 |
| FR-4 | 多轮、并行不阻塞、单点失败隔离 | E2E-2 + E2E-4 |
| FR-5 | 轨迹状态一致、可回放 | E2E-2 + I-11 |
| FR-6 | 溯源跳转 | E2E-2 / E2E-3 |
| FR-7 | 六段纪要、落库、版本 | E2E-3 + I-8 / I-9 |
| FR-8 | 结束即停、软删除 | E2E-3 + I-12 |
| FR-9 | 超时/限流/断线 | E2E-4 / E2E-5 |
| 非功能 | 首 token、并发、日志 | `/metrics` + 压测脚本 `scripts/load.ts`（20 场 × 4 agent） |

---

## 6. 执行方式

```bash
pnpm test           # 单元 + 集成（vitest，CI 必跑）
pnpm test:e2e       # Playwright（起 docker-compose.test.yml）
pnpm test:e2e:ui    # 本地调试
```

- E2E 每条用例使用独立 `discussion`（通过 API 预置），互不干扰；`afterEach` 清理。
- 失败时保留 trace/video（`trace: 'retain-on-failure'`），便于复盘。
- CI 顺序：`lint → typecheck → unit+integration → build → e2e`。
