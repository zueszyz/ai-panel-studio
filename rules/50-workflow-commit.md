# rules/50 · 按模块逐步开发、提交与留痕

> 这条规则优先级最高：**没有按模块推进，代码写得再对也不算完成。**
> 交付说明明确禁止"单一 Prompt 一键生成整个项目"。

---

## 1. 一个模块的完整流程（缺一步不算完成）

```
① 读契约（PRD 对应 FR + API + 表结构 + 本目录规则）
② 写计划（3–8 行：要改哪些文件、怎么验证）
③ 先建骨架 + 测试（红）
④ 实现到绿（一次只碰本模块文件）
⑤ 视觉自查（前端模块必跑 rules/20 的 12 条）
⑥ 跑验证命令，贴输出摘要
⑦ 提交（1 次或几次小提交，不做大杂烩）
⑧ 更新文档 / PROMPT_LOG（如契约或决策有变化）
→ 进入下一个模块
```

**禁止**：

- 一次性生成整个项目、多个模块并行开工；
- 未跑测试就提交；
- 在 A 模块的提交里顺手改 B 模块的文件；
- 跳过 ② 直接写代码（没有计划说明，视为无效产出）。

## 2. 模块顺序与门禁（不可跳步）

| # | 模块 | 交付 | 门禁（全绿才进下一步） |
| --- | --- | --- | --- |
| 1 | 骨架 | Maven、Flyway 7 表、异常/日志、health、Vue 壳、scripts | 删库重启自建表；`mvn test` 通过 |
| 2 | 大厅 | `GET/POST /discussions` + 列表页（四态 + 12 条自查） | I-1 通过；列表页截图 |
| 3 | 组局 | 议题分析 + 席位流式生成 + 认领 | I-2~I-4 通过；席位卡动效可见 |
| 4 | 调度内核 | `SessionActor` + `SpeakerPolicy` + `AgentRunner` | 单测 ≥6 例；Mock 并行不阻塞 |
| 5 | 消息流 | `/stream`、`/messages`、前端 SSE 与气泡 | E2E-2 通过（含时间戳断言） |
| 6 | 轨迹 | `agent_runs` 落库 + 轨迹面板 | 状态与消息一致；可回放 |
| 7 | 收敛 | 纪要生成/版本/落库/溯源 | E2E-3 通过（刷新后一致） |
| 8 | 质量 | 降级、断线、隔离、响应式走查 | 5 条 E2E + I-13~I-15 全绿 |

## 3. 提交规范

- 格式：`type(scope): subject`，英文小写，祈使句；body 说明"为什么"。
- 允许的 type：`feat` `fix` `test` `docs` `refactor` `chore` `style` `perf`。
- scope 用模块名：`server` `web` `db` `e2e` `docs` `rules`。
- **禁止**："更新""修改""fix bug""调整样式"这类无信息 message。
- 粒度：一个模块 ≤8 次提交；一次提交只做一件事（改样式就别顺手改 SQL）。
- 提交前自查：`git diff` 里没有调试代码、注释掉的代码、`console.log`、临时文件。

示例：

```
feat(server): implement speaker policy with decay scoring
feat(web): build message bubble with streaming text and quote badge
fix(server): keep other agents running when one times out
docs(rules): tighten frontend visual checklist to 12 items
```

## 4. 每模块的完成定义（DoD 模板）

```markdown
### 模块：<名称>
- [ ] 契约已核对（FR / API / 表结构）
- [ ] 代码只动了本模块文件（`git diff --stat` 可证）
- [ ] 单元测试 / 集成测试 / E2E 按模块清单通过
- [ ] 前端：rules/20 的 12 条自查全绿 + 3 张截图
- [ ] 错误路径已处理（超时/为空/权限/重复提交）
- [ ] 日志含 discussionId，无 Key 泄漏
- [ ] 文档/PROMPT_LOG 已同步（如有变更）
- [ ] 提交信息合规，粒度合理
```

## 5. Prompt 留痕（交付硬要求）

每个模块至少 1 条记录，写进 `docs/PROMPT_LOG.md`，包含：

1. 目标（这次要解决什么）；2. 实际 Prompt（保留关键约束词）；3. 产出（文件/章节）；
4. 人工改了什么、为什么；5. **验证方式**（命令 + 结果）；6. AI 走偏时如何修正。

只写"让 AI 生成了 XX 页面"不算留痕。

## 6. 失败处理

- 测试红：不许提交，不许注释掉测试；先修或先 `@Disabled` 并**单独提交说明原因**。
- 实现走不通：回到契约层找问题，改文档需单独提交并在 PROMPT_LOG 记一笔。
- 时间不够：按 `docs/PROJECT_PLAN.md` 的优先级砍 P1，**不许交半成品页面**（宁可少一个功能，不要多一个丑陋页面）。

## 7. 何时可以"批量"做

仅以下三种情况允许一次改多个文件：

1. 新建模块骨架（同模块内一次性建包 + 接口 + 测试）；
2. 机械重命名/格式化（单独提交，`refactor`/`style`）；
3. 文档同步（单独提交，`docs`）。

除此之外，一次只做一件事。
