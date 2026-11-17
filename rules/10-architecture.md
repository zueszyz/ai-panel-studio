# rules/10 · 架构分层与模块边界

## 1. 五层分包（后端）

```
com.panelstudio
├── api            Controller / DTO / SseEmitter 注册 / 全局异常
├── application   用例编排、事务边界（DiscussionService / PanelService / MinutesService）
├── orchestrator  调度：SessionActor / TurnScheduler / AgentRunner / ContextBuilder / TokenBudget
├── domain        实体、状态机、SpeakerPolicy（纯函数，零 IO）
└── infra         LlmClient、Mapper、Flyway、配置、日志
```

**依赖方向**：`api → application → orchestrator/domain → infra`。
`domain` 不 import `infra`，`api` 不 import `infra.mapper`，`orchestrator` 不直接拼 SQL。

**常见违规（直接打回）**

| 违规 | 正确做法 |
| --- | --- |
| Controller 里 `discussionMapper.selectById` | 走 `DiscussionService` |
| Service 里拼 prompt 字符串 | 交给 `ContextBuilder` + `PromptTemplates` |
| `domain` 里出现 `@Autowired` | 纯函数/值对象，无 Spring 依赖 |
| `SessionActor` 里写 Mapper 调用 | 通过 `application` 暴露的端口接口 |
| 全局 `static` 存会话状态 | `SessionActorRegistry`（按 discussionId） |

## 2. 模块 → 文件映射（新增代码先归位，再动手）

| 模块 | 主要文件 |
| --- | --- |
| 大厅 | `api/DiscussionController#list/create`、`web/src/pages/Lobby.vue`、`stores/discussion.ts` |
| 组局 | `api/PanelController`、`application/PanelService`、`web/src/pages/Panel.vue`、`components/SeatCard.vue` |
| 讨论 | `orchestrator/*`、`api/StreamController`、`api/MessageController`、`web/src/pages/Live.vue`、`components/MessageBubble.vue`、`lib/sse.ts` |
| 轨迹 | `api/RunController`、`web/src/components/TracePanel.vue` |
| 收敛 | `api/MinutesController`、`application/MinutesService`、`web/src/pages/Minutes.vue` |

## 3. 命名与契约

- REST 路径：`/api/discussions/{id}/...`；SSE 事件名：小写点分隔（`message.delta`）。
- 错误码：`UPPER_SNAKE`，与 `docs/API.md` §4 一致；新增错误码要同步文档。
- 主键：`前缀 + ULID`（`dk_` `st_` `ms_` `mn_` `rn_` `pt_` `ul_`），由应用层生成。
- 时间：`TEXT` ISO-8601 UTC（`2026-11-17T08:12:31.482Z`），不用 `LocalDateTime` 直接序列化。

## 4. 状态机（唯一合法迁移）

```
DRAFT → PANEL_READY → LIVE → SUMMARIZING → CLOSED
                    ↘ FAILED → DRAFT(重试)
LIVE → INTERRUPTED(重启) → LIVE / SUMMARIZING
```

迁移集中写在 `domain/statemachine/DiscussionStateMachine`，**禁止在 Service 里散落 `if (status == ...)` 改状态**。

## 5. 新增模块流程

1. 在 PRD/ARCHITECTURE 找到对应章节，确认契约；
2. 建包 + 接口/空实现 + 测试骨架（先红）；
3. 实现到绿；
4. 补前端页面与四态；
5. 跑该模块集成/E2E；
6. 单独提交。

禁止跳过第 2 步直接写实现。
