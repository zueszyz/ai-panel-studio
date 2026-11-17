# AGENTS.md · AI 协作约束总纲

> 本文件及 `rules/` 下的规则，是**任何 AI 工具在本仓库工作前必须读的约束**（工具无关：Cursor / CodeBuddy / Claude Code 均可读；如需专用格式，可把本文件软链为 `CLAUDE.md`，规则内容不变）。
> 规则不是建议，是**验收条件**。违反红线的产出直接打回。

---

## 1. 一句话目标

做一个「AI 圆桌演播厅」：用户输入议题 → 生成 1 位主持人 + 3 位立场互补的专家 → 多 Agent 并行流式讨论、用户可随时插话 → 收敛成**可溯源的结构化纪要**并落库。

判断标准不是"能跑"，而是：**多 Agent 并发正确、页面像一场真圆桌、过程可观测可追溯**。

---

## 2. 工作方式（最重要的一条）

**按模块逐步开发，一次只做一件事。**

```
读文档 → 定契约 → 写一个模块 → 跑测试 → 视觉自查 → 提交 → 下一模块
```

- ❌ 禁止一次性生成整个项目（PRD/交付说明都明确禁止"一键生成"）。
- ❌ 禁止跨模块预写："先把所有 Controller 都建好"属于违规。
- ✅ 每个模块必须有：入口条件、实现范围、完成定义（DoD）、验证命令、独立提交。
- ✅ 模块清单与 DoD 见 `rules/50-workflow-commit.md`。

---

## 3. 十条红线（MUST NOT）

| # | 红线 | 说明 |
| --- | --- | --- |
| 1 | 不一次性生成整个项目 | 必须按 `rules/50` 的模块顺序推进 |
| 2 | 不引入 UI 组件库 | UI 由项目自建（原生 CSS + 设计令牌）；不加 Element/AntD/Naive 等依赖 |
| 3 | 不把 API Key 放前端 | Key 只从后端环境变量读取；不写 `VITE_*`、不进构建产物、不进日志 |
| 4 | 不在事务里调 LLM / 发 SSE | SQLite 单写者，秒级等待会锁死写锁 |
| 5 | 不使用 `SELECT … FOR UPDATE` | SQLite 无行级锁；并发用 CAS（`WHERE turn_index = ?`） |
| 6 | 不让单 Agent 失败影响全场 | 必须超时 + 隔离 + 单条重试 |
| 7 | 不跨讨论共享状态 | 状态/事件流/消息/纪要必须按 `discussionId` 隔离 |
| 8 | 不写无语义的 commit | 禁止"更新""修改""fix"这类 message |
| 9 | 不跳过测试就提交 | 至少相关单测 + 该模块的集成/E2E 通过 |
| 10 | 不擅自改契约 | 改 API/表结构必须先改文档并单独提交 |

---

## 4. 十条必做（MUST）

1. 每个模块开始前，先读相关文档章节（PRD 对应 FR + API 契约 + 表结构）。
2. 新增依赖必须先说明理由并获得确认（`package.json` / `pom.xml` 变更单独提交）。
3. 所有接口错误走统一错误体与错误码（见 `docs/API.md` §4）。
4. 页面必须实现**四态**：加载（骨架）、空态、错误（可操作）、正常。
5. 中文界面：文案进 `lib/copy.ts`，不出现英文按钮/占位语。
6. 视觉必须达到 `rules/20-frontend-ui.md` 的「美化自查 12 条」，不达标不算完成。
7. 每个模块结束给出一条可复制的验证命令，并附实际输出摘要。
8. 每个模块至少 1 次独立提交，message 遵循 `type(scope): subject`。
9. 关键决策写进 `docs/PROMPT_LOG.md`（意图 / 挑战 / 修正 / 验证）。
10. 改完代码同步文档；文档与实现冲突时，先决定以谁为准并记录。

---

## 5. 规则索引

| 文件 | 管什么 | 何时读 |
| --- | --- | --- |
| [rules/00-project.md](rules/00-project.md) | 产品边界、技术栈与版本、运行方式 | 每次会话开始 |
| [rules/10-architecture.md](rules/10-architecture.md) | 分层、依赖方向、模块边界、新文件放哪 | 写代码前 |
| [rules/20-frontend-ui.md](rules/20-frontend-ui.md) | **前端视觉质量**、设计令牌、四态、动效、布局滚动、无障碍 | 写任何页面/组件前 **必读** |
| [rules/30-backend-streaming.md](rules/30-backend-streaming.md) | SSE 协议、并发闸门、超时重试、多讨论隔离、密钥 | 写接口/调度前 |
| [rules/40-data-testing.md](rules/40-data-testing.md) | Flyway、MyBatis-Plus 约定、事务、测试要求 | 写表/仓储/测试前 |
| [rules/50-workflow-commit.md](rules/50-workflow-commit.md) | 模块顺序、DoD、commit 规范、Prompt 留痕 | 每次开工与提交前 |

配套文档（事实来源，规则与它们冲突时以规则为准并回改文档）：[PRD](docs/PRD.md) · [ARCHITECTURE](docs/ARCHITECTURE.md) · [DATABASE](docs/DATABASE.md) · [API](docs/API.md) · [TESTING](docs/TESTING.md) · [WORKFLOW](docs/WORKFLOW.md)。

---

## 6. 模块地图（开发顺序，不可跳步）

| 阶段 | 模块 | 交付物 | 门禁 |
| --- | --- | --- | --- |
| M1 | 骨架 | Maven 工程、Flyway 7 表、统一异常/日志、health、Vue 壳、dev 脚本 | 删库重启能自建表；`mvn test` 通过 |
| M2 | 大厅 + 组局 | 列表/筛选/发起、席位流式生成、认领 | 集成 I-1~I-4 通过；页面达美化自查 12 条 |
| M3 | 讨论核心 | `SessionActor`、调度、并行流式、轨迹、插话 | E2E-1/2 通过；并行不阻塞有断言 |
| M4 | 收敛 | 六段纪要、落库版本化、溯源跳转 | E2E-3 通过；刷新后一致 |
| M5 | 质量 | 降级用例、断线重连、响应式走查、文档定稿 | 5 条 E2E 全绿；交付清单可勾 |

---

## 7. 规则的修改

- 改规则必须单独提交：`docs(rules): <改了什么、为什么>`。
- 规则与 PRD 冲突：先改 PRD（或规则），再改代码；不允许"代码先跑起来再说"。
