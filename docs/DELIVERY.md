# 交付与提交 · AI Panel Studio

| 项 | 内容 |
| --- | --- |
| 用途 | 把作业要求逐项落到"谁、在哪、怎么证明"，避免交付时漏项 |
| 关联 | [PRD](PRD.md) · [ARCHITECTURE](ARCHITECTURE.md) · [DATABASE](DATABASE.md) · [API](API.md) · [TESTING](TESTING.md) · [计划](PROJECT_PLAN.md) |
| 截止 | 收到作业后 72 小时内（建议留 6 小时缓冲用于打包与录屏） |
| 提交邮箱 | handshake@gaodun.cn |

---

## 1. 必交清单（逐项对照）

作业「附件约束（硬性要求）」共 11 项，逐项落到本项目：

| # | 作业要求 | 本项目的落地 | 证据位置 | 状态 |
| --- | --- | --- | --- | --- |
| 1 | 完整可运行：README 说明 + 命令 + **数据库初始化脚本** + ≥5 张表 | Flyway `V1__schema.sql`（7 表）+ `V2__indexes.sql` + `V3__seed_demo.sql`，启动即迁移；`scripts/dev` 一条命令 | `server/src/main/resources/db/migration/` | 待编码 |
| 2 | 前端 ≥3 页面路由（讨论列表 / 讨论详情 / 复盘总结） | **4 条**：`/`、`/discussions/:id/panel`、`/discussions/:id`、`/discussions/:id/minutes` | PRD §7 | 待编码 |
| 3 | 后端 ≥8 个 API（讨论 CRUD / 消息 / SSE 流式 / 汇总） | **17 个**（REST 14 + SSE 3） | API §1 | 待编码 |
| 4 | ≥3 条端到端（E2E）测试 | **5 条**（3 条必过 + 2 条降级/断线加分） | TESTING §2 | 待编码 |
| 5 | 文档：README + 架构图 + API 文档 + 数据库设计（ER 图） | 8 份文档，含 Mermaid 架构图、状态机、ER 图、SSE 时序图 | `docs/` | ✅ 已完成 |
| 6 | Git ≥30 次 commit，递增可追溯、message 有意义 | 规划 53 条，按"一次一件事"切分 | PROJECT_PLAN §3 | 进行中（17/30+） |
| 7 | 过程留痕：保留每次 Prompt / 工具调用记录，README 说明哪些是 AI 生成、哪些自己写、改了哪些 | `PROMPT_LOG.md` 11 条记录（含技术栈变更决策）+ README §7 声明表 | `docs/PROMPT_LOG.md`、README §7 | 持续追加 |
| 8 | 交付物 1：源码（统一压缩包，与文档同主目录）+ 文档 + Git 仓库（含提交历史） | 见 §3 打包结构；附 `git-history.txt` | §3 | 待打包 |
| 9 | 交付物 2：3–5 分钟视频（走查 + 讲解） | 分镜见 §4（4 分 30 秒） | §4 | 待录制 |
| 10 | 交付物 3：README（运行说明 + 架构说明 + AI 工具使用说明） | README §5 / §3 / §7 | `README.md` | ✅ 已完成 |
| 11 | 提交方式：邮件（简历 + 作品链接 + 视频链接 + 说明） | 模板见 §5 | §5 | 待发送 |

---

## 2. 与作业描述的差异（必须主动说明，不要藏）

| 项 | 作业原文 | 本项目 | 为什么 |
| --- | --- | --- | --- |
| 后端语言 | "后端基于 Node.js 生态环境和 TypeScript（推荐）实现" | **Spring Boot 3.5.16 + JDK 17** | "推荐"非强制；三个考核点（多 Agent 调度、并行流式、错误隔离）与语言无关，且在 JDK 17 无虚拟线程的前提下实现，约束更显式、更能体现工程判断。SSE 用 Spring MVC `SseEmitter`，与前端的契约完全一致 |
| 数据库 | 未指定 | **SQLite 3.40+（WAL）** | 单文件零运维，评审 `java -jar` 即可复现；代价（单写者）已在 DATABASE §1.2 明确并以短事务 + 退避重试化解 |
| 一键启动 | 提到 "Docker Compose 一键起来" | **本地脚本 + 单个 jar**（按要求不使用容器） | SQLite 无独立服务，容器只会多一层流式代理（`proxy_buffering`）风险；`scripts/dev` 两条命令即可复现，`java -jar` 一条命令演示 |
| 表 / 接口 / 路由 / 测试数量 | ≥5 表、≥8 API、≥3 路由、≥3 E2E | 7 表、17 API、4 路由、5 E2E | 只多不少，且每一项都有验收标准（PRD 每条 FR 带 checklist） |

> 这四条差异会同时出现在 README「与作业原文的差异」一节，确保评审第一眼就能看到，而不是自己去发现。

---

## 3. 交付包结构

```text
ai-panel-studio/                     ← 压缩包根目录（源码与文档同主目录）
├── README.md                        ← 运行 / 架构 / AI 使用说明（交付物 3）
├── docs/
│   ├── PRD.md  ARCHITECTURE.md  DATABASE.md  API.md
│   ├── TESTING.md  PROJECT_PLAN.md  PROMPT_LOG.md  DELIVERY.md
│   └── git-history.txt              ← git log --pretty 导出（补足"含提交历史"）
├── server/                          ← Spring Boot 3.5.16（Maven，含 Flyway 脚本）
├── web/                             ← Vue 3 + TS（构建产物 dist 会复制进 server 静态目录）
├── e2e/                             ← Playwright 用例与配置
├── scripts/                         ← dev.sh/dev.bat · build.sh/build.bat（无容器）
├── .env.example
└── .git/                            ← 建议一并打包（体积小，能直接 git log 验证）
```

打包命令：

```bash
git log --pretty=format:'%h %ad %s' --date=short > docs/git-history.txt
zip -r ai-panel-studio.zip . -x "*/node_modules/*" "*/target/*" "data/*" ".git/objects/pack/*.tmp"
```

- **不要**打包 `node_modules/`、`server/target/`、`data/*.db`（数据文件含演示数据，体积小但可复现，改由 Flyway seed 生成）。
- 压缩包命名：`AI开发实习生-姓名-AI圆桌讨论MVP.zip`。

---

## 4. 演示视频分镜（4 分 30 秒）

| 时间 | 画面 | 讲解要点（口播） |
| --- | --- | --- |
| 0:00–0:25 | 标题 + 一句定位 | "AI Panel Studio：输入议题，召集一场你可以插话的 AI 圆桌。它解决的不是答案不够好，而是答案太单一。" |
| 0:25–1:10 | 大厅 → 发起 → 席位逐个浮现 | 议题「该不该把推荐系统换成向量召回」，4 人局；指出"1 主持 + 3 专家，立场互补，不是四个万能助手" |
| 1:10–1:50 | 认领席位 → 主持人开场 | 三色区分（琥珀/青碧/靛蓝 + 主持人中性色）；"我坐技术专家这位，接下来我说话就代表他" |
| 1:50–2:50 | 用户插话 → **两路同时流式** | 关键镜头：两个气泡同时吐字，"注意这里不是排队，A 卡住了 B 照样出"；切轨迹面板看"谁在思考、谁在说、谁在等"；点引用徽标跳回原文 |
| 2:50–3:30 | 结束 → 六段纪要 → 溯源 → 刷新 | "纪要不是 AI 单独生成的总结，每条主张都能点回原话"；**刷新页面**证明来自 SQLite 而非前端缓存 |
| 3:30–4:15 | 代码走查（IDE 全景） | 分层（api/application/orchestrator/domain/infra）→ `SessionActor` + `Semaphore` → `SseEmitter` 多路复用 → Flyway 7 表 → 3 条 E2E 绿灯 |
| 4:15–4:30 | 收尾 | 取舍与边界："SQLite 单写者用短事务兜；没做多人围观；不用容器，`java -jar` 就能跑" |

录制建议：1080p、终端字号 ≥16；**不要**快进流式过程（并行流式是本项目的核心证据）；提前把 seed 数据准备好，避免现场建库等待。

---

## 5. 提交邮件模板

```text
收件人：handshake@gaodun.cn
主题：【AI开发实习生-张三-后端开发】

正文：
您好，

附件为本次 AI 圆桌讨论 Web App MVP 的作业提交：

1. 简历：见附件
2. 源码 / 文档：<仓库或网盘链接>（压缩包内 docs/git-history.txt 为提交历史导出，共 N 次提交）
3. 演示视频：<视频链接，4 分 30 秒>

技术栈：Spring Boot 3.5.16 + JDK 17 + Vue 3 + SQLite（按项目约定不使用容器，启动方式见 README §5）
需要说明的差异：作业推荐 Node.js 后端，本项目按既定技术栈用 Spring Boot 实现；
数据库选用 SQLite（单文件，便于复现）；启动方式为本地脚本 + 单 jar，而非 Docker Compose。
以上取舍与理由见 README「与作业原文的差异」与 docs/DELIVERY.md §2。

开发过程全程保留 Prompt 与工具调用记录（docs/PROMPT_LOG.md），AI 生成与人工修改的边界见 README §7。

谢谢！
张三 / 电话 / 邮箱
```

附件清单（发送前逐项打勾）：简历 ☐ · 源码压缩包或仓库链接 ☐ · 视频链接 ☐ · 说明正文 ☐

---

## 6. 评分维度自检（满分 100）

| 维度 | 分值 | 我们靠什么拿分 | 证据 | 主要风险 |
| --- | --- | --- | --- | --- |
| 架构与工程化 | 30 | 五层分包与依赖方向；`SessionActor` 显式状态机；Flyway 迁移；统一异常 + 结构化日志（MDC 跨线程）；Actuator；`.env.example` | ARCHITECTURE §2–§3；README §4 | 分层被"图省事"打穿（Controller 直接写 Mapper） |
| 并发与流式 | 25 | 顺序调度 + 并行 fan-out；单 SSE 多路复用；两级信号量；超时 abort + 单条重试；写锁串行化（delta 合并发送）；`Last-Event-ID` 续传 | ARCHITECTURE §3.2–§3.4；API §3 | 并行写成串行（`get()` 阻塞）；一个 agent 失败拖垮全场 |
| 数据与闭环 | 20 | 7 张表覆盖席位/消息/轨迹/纪要/参与；纪要版本化落库可复盘；溯源双保险（`quote_message_id` + `sourceMessageIds`） | DATABASE 全文；PRD FR-6/FR-7 | 纪要只写前端状态、刷新即丢；SQLite 长事务卡死 |
| 前端体验 | 15 | 三色区分 + 席位状态点；三档响应式；固定槽位 + rAF 合批防跳动；轨迹面板"可观测但不说破" | PRD §8；ARCHITECTURE §3.3 | 流式抖动、自动滚动打断阅读 |
| 文档与过程 | 10 | 8 份文档（含架构图/ER/状态机/时序）；Prompt 留痕含技术栈变更决策；commit 一次一件事 | `docs/`；PROMPT_LOG | 文档与代码不一致（改代码不改契约） |

**自评目标：≥85。** 明确放弃的分（换取舍）：

- 不做多人同场围观（−3 潜在体验分），换取并发模型简洁可信；
- 不做向量检索/联网（避免"专家观点"变检索摘要，伤害产品形态）；
- 不做账号体系（匿名 deviceId 足够验证形态）。

---

## 7. 72 小时排期（含交付动作）

| 时段 | 内容 | 产出 |
| --- | --- | --- |
| H0–H3 | M0 文档（当前） | 8 份文档 + 仓库初始化 |
| H3–H8 | M1 骨架 | pom / Flyway 7 表 / health / Vue 骨架 / 脚本 |
| H8–H15 | M2 组局 | 大厅 + 席位流式 + 认领 |
| H15–H35 | M3 讨论（最重） | Actor / 调度 / 并行 SSE / 轨迹 / 插话 |
| H35–H43 | M4 收敛 | 六段纪要 + 落库 + 溯源 |
| H43–H53 | M5 质量 | E2E 5 条 + 集成 13 条 + 视觉走查 |
| H53–H58 | 交付动作 | README 定稿 → 打包 → 录屏 → 邮件发送 |
| H58–H66 | 缓冲 | 修 bug、补文档、应对演示翻车 |
| H66–H72 | 冻结 | 只改文档错字，不动代码（避免演示与仓库不一致） |
