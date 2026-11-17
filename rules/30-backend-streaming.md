# rules/30 · 接口、SSE 与并发

## 1. 接口约定

- 统一返回体；错误统一 `{"error":{"code","message","details"}}`，`code` 与 `docs/API.md` §4 一致。
- 校验用 Jakarta Validation（`@Valid` + `record` DTO），错误信息中文化。
- 所有列表接口必须分页/限量，禁止无 `LIMIT` 的 `selectList`。
- 幂等：用户消息必须带 `clientMsgId`，命中 `(discussion_id, client_msg_id)` 唯一索引时返回原记录，不重复调度。

## 2. SSE 协议（单连接多路复用）

```java
@GetMapping(value="/discussions/{id}/stream", produces=MediaType.TEXT_EVENT_STREAM_VALUE)
public SseEmitter stream(@PathVariable String id, @RequestHeader("X-Device-Id") String deviceId) {
    SseEmitter emitter = new SseEmitter(0L);   // 永不超时
    sseRegistry.register(id, emitter);         // 反注册在 onCompletion/onTimeout/onError
    return emitter;
}
```

- 发送：`emitter.send(SseEmitter.event().id(seq).name("message.delta").data(payload, APPLICATION_JSON))`。
- **delta 合并发送**：`AgentRunner` 累积 60–80ms 再发一次（`ResponseBodyEmitter` 写锁会排队）。
- **事件类型固定 13 类**：`snapshot / discussion.updated / turn.start / agent.status / message.start / message.delta / message.end / message.failed / digest.start / minutes.section / discussion.closed / error / ping`。
- 心跳：单线程 `@Scheduled(fixedRate=15000)` 发注释帧，不是每连接一个线程。
- 续传：`Last-Event-ID` + 每场 500 帧环形缓冲；缓冲不足则回退 `snapshot`。
- 必须配 `spring.mvc.async.request-timeout=-1`，并确保 `text/event-stream` 不被压缩。

**禁止**：每个 agent 一条 SSE 连接；在 `onError` 里取消生成；在事务里 `send`。

## 3. 并发与超时

| 项 | 规定 |
| --- | --- |
| 调度 | `SessionActor` 串行消费队列，**回合内** `CompletableFuture` 并行 fan-out |
| 汇总等待 | 用 `allOf(...).handle(...)`，不用 `get()`；单条异常不得中断整组 |
| 闸门 | 每场 `Semaphore(3)`，全局 `Semaphore(20)`；线程池 core 8 / max 16 / queue 200 / `CallerRunsPolicy` |
| 去重 | `inflight` 以 `seatId` 为键，`putIfAbsent` 失败即跳过（同一席位不并发两次） |
| 超时 | 首 token 45s、整条 90s；watchdog 触发 `Disposable.dispose()`，消息标 `FAILED`、`retriable=true` |
| 重试 | 单条重试沿用同一 `messageId`，`attempt+1`；最多 1 次自动 + 用户手动 |
| 预算 | 单场 120k token；80% 提示收敛，100% 拒绝继续发言但允许结束出纪要 |

## 4. 多讨论隔离（硬要求）

- `SessionActorRegistry` / `SseRegistry` 一律以 `discussionId` 为键，**不得有全局 emitter 列表**。
- 仓储层只暴露 `findByDiscussionId`，不向业务层开放全表查询。
- 纪要生成只读本场消息，落库前校验 `seatId` 属于本场。
- 前端 store 按 `discussionId` 隔离；切换场次先 `abort()` 旧 SSE 再清空。
- 验收：集成用例 I-14（两场并行，事件与消息互不串台）。

## 5. 密钥与日志

- `LLM_API_KEY` 仅后端环境变量；`application.yml` 不写明文。
- 日志字段固定：`discussionId / turnId / seatId / messageId / event / latencyMs / tokens`。
- 异步线程用 `TaskDecorator` 传 MDC，否则日志丢 `discussionId`。
- 打印上游请求前剔除 `Authorization`；异常不原样回传上游 body。

## 6. LLM 适配层

- 只暴露 `LlmClient.chatStream(...)` / `chatJson(...)` 两个方法，不泄漏 provider 细节。
- JSON 输出必须校验 + 修复 1 次（去 markdown 围栏、补尾逗号），失败抛 `LLM_JSON_INVALID`，由上层降级为预设阵容。
- 不允许业务层直接 `new WebClient()` 调模型。
