# rules/40 · 数据与测试

## 1. 数据库（SQLite）

| 项 | 规定 |
| --- | --- |
| 迁移 | Flyway `V1__schema.sql` / `V2__indexes.sql` / `V3__seed_demo.sql` / `V4__seed_examples.sql`；**只增不改**，已执行脚本不得修改 |
| 表 | 7 张：`users` `discussions` `agents` `messages` `minutes` `agent_runs` `discussion_participants` |
| 主键 | `TEXT`，应用层 `前缀 + ULID`（`IdType.INPUT`） |
| 时间 | `TEXT` ISO-8601 UTC（`strftime('%Y-%m-%dT%H:%M:%fZ','now')`） |
| 枚举 | `TEXT + CHECK`，Java 侧 `@EnumValue` |
| JSON | `TEXT` + `JacksonTypeHandler`（类上 `autoResultMap = true`），不用 SQLite 专有 JSON 函数 |
| 唯一/部分索引 | 一席一人用 `UNIQUE(discussion_id, seat_id) WHERE seat_id IS NOT NULL` |
| 外键 | 连接级 `PRAGMA foreign_keys=ON`；`ON DELETE CASCADE` 用于场次级子表 |

**事务纪律**

- 事务只包 DB 写，**禁止**在 `@Transactional` 内调 LLM、发 SSE、做重试等待。
- 写竞争用 CAS：`UPDATE discussions SET turn_index=? WHERE id=? AND turn_index=?`，影响 0 行即重试。
- `busy_timeout=5000` + 应用层 50/100/200ms 退避重试 3 次；Hikari `maximumPoolSize=4`。
- 批量写 ≤50 条/批（`agent_runs` 收尾可批量）。
- 软删除 `@TableLogic`，查询自动追加 `deleted_at IS NULL`。

## 2. 测试分层

| 层 | 覆盖 | 门禁 |
| --- | --- | --- |
| 单元 | `SpeakerPolicy`、`ContextBuilder`、状态机、JSON 修复器、ULID | 核心纯函数行覆盖 ≥ 80% |
| 集成 | I-1~I-15（MockMvc + 临时 SQLite + WireMock） | 全绿；SSE 帧序列断言 |
| E2E | E2E-1~5（Playwright） | 3 条必过，5 条全绿为交付标准 |

## 3. 测试写法要求

- **LLM 全部走 WireMock**：`withChunkedDribbleDelay` 造流式，`withFixedDelay` 造慢席位，`Scenario` 造重试成功。
- 禁止 `Thread.sleep` 等待流式：用条件等待（轮询断言 / Playwright 的 `expect(...).toPass()`）。
- E2E 定位只用 `data-testid` + 中文文案，不用样式 class（样式会随视觉调整变化）。
- 并行断言必须量化：比较两条消息 `message.end` 的时间先后，不写"看起来是并行"。
- 每个 E2E 用例独立造数据，`afterEach` 清理；用例之间不共享 `discussion`。

## 4. 每个模块的最小测试清单

| 模块 | 必须新增 |
| --- | --- |
| 大厅/组局 | I-1（校验）、I-2（SSE 帧序）、I-3（重复生成不重建）、I-4（认领冲突） |
| 消息/讨论 | I-5（幂等）、I-6（非 LIVE 拒绝）、E2E-2（并行 + 溯源） |
| 收敛 | I-8（版本）、I-9（失败不覆盖）、E2E-3（刷新后一致） |
| 隔离/安全 | I-13（并发写）、I-14（多讨论隔离）、I-15（密钥不泄漏） |
| 降级 | E2E-4（单席位超时）、E2E-5（断线重连） |

## 5. 数据样例（交付要求）

`V4__seed_examples.sql` 需提供 **≥5 场预设圆桌**，每场：议题 + 4 席位（1 主持 + 3 专家，立场互补）+ 开场消息。示例方向：AI 技术选型 / 定价策略 / 招聘取舍 / 内容策略 / 组织调整。样例数据要"像真的"，不要 `话题1/专家A`。
