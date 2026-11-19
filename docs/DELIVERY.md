# 交付与提交 · AI Panel Studio

| 项 | 内容 |
| --- | --- |
| 依据版本 | **现行版作业说明（演播厅版）**：数据库 SQLite、前后端框架不限、交付物为「源码 + Prompt 记录 + 工作流程说明」 |
| 用途 | 把"重要规则 5 条 / 交付物 3 项 / 提交方式"逐条落到"在哪、怎么证明" |
| 关联 | [PRD](PRD.md) · [ARCHITECTURE](ARCHITECTURE.md) · [DATABASE](DATABASE.md) · [API](API.md) · [TESTING](TESTING.md) · [PROMPT_LOG](PROMPT_LOG.md) · [WORKFLOW](WORKFLOW.md) |
| 提交邮箱 | **xulei@wisqwest.com** |
| 邮件主题 | **【远程作业提交】姓名** |
| 附件 | zip 包 + 文档（Prompt 记录 + 工作流程说明）+ GitHub/Gitee 仓库邀请 |
| 截止 | **收到作业后 72 小时** |

> 说明：作业说明存在多个版本（措辞差异见 §7）。本文档以**现行版**为准；旧版要求（如"≥30 次 commit""3–5 分钟视频"）中**不与本版冲突的部分**仍作为加分项保留。

---

## 1. 重要规则 5 条（逐条对照）

| # | 原文要点 | 本项目的做法 | 证据 |
| --- | --- | --- | --- |
| 1 | **禁止"一键生成"**：严禁用单一 Prompt 直接生成整个项目代码，必须体现工程化拆解 | 按"文档 → 骨架 → 组局 → 讨论 → 收敛 → 质量"六段推进，每段独立 Prompt + 人工评审；13 次关键交互与 7 次走偏修正留痕 | [PROMPT_LOG](PROMPT_LOG.md) |
| 2 | **技术栈**：数据库采用 SQLite；前端及后端框架不限 | SQLite 3.40（WAL）+ MyBatis-Plus + Flyway；Spring Boot 3.5.16 / JDK 17；Vue 3.4（SFC + 原生 CSS） | README §3、[DATABASE](DATABASE.md) |
| 3 | **原创性**：必须从 0 到 1 创建 | 仓库从空目录初始化，未套用脚手架模板；接口、组件与样式均由项目自行组织 | `git log` 首条为 `chore: init repo` |
| 4 | **工具限制**：主体开发必须使用某一款 Vibe Coding 工具，不能使用浏览器与 AI 的对话 | 全程在 CodeBuddy（终端/IDE 内 Agent）完成；无网页版对话产物 | PROMPT_LOG §7 工具调用记录 |
| 5 | **利用 AI 工具**：鼓励使用 skills、MCP 优化流程 | 使用文件读写/命令执行/截图裁剪等工具链；结构化产出（md + mermaid）由工具直接落盘，减少复制粘贴损耗 | PROMPT_LOG §7 |

---

## 2. 交付物三项（缺一项视为不合格）

### 2.1 完整项目源码（GitHub/Gitee 链接）

| 要求 | 本项目落地 | 状态 |
| --- | --- | --- |
| 代码 + **数据库初始化脚本** | Flyway `V1__schema.sql`（7 表）/ `V2__indexes.sql` / `V3__seed_demo.sql` / `V4__seed_examples.sql` / `V5__discussion_regen_count.sql`，启动自动迁移 | ✅ 已实现 |
| **≥5 条高质量样例数据**（预设讨论话题 + 对应嘉宾阵容） | `V3` 1 场已结束圆桌（含六段纪要）+ `V4` 5 场预设圆桌：AI 技术选型 / 定价策略 / 招聘取舍 / 内容策略 / 组织调整，每场 4 席位（1 主持 + 3 专家，立场互补） | ✅ 已实现（共 6 场） |
| **开发文档**（PRD、ER 图等，md + mermaid） | 9 份 md，含分层架构图、回合调度时序图、SSE 多路复用图、ER 图、状态机图 | ✅ 已实现 |
| **API 文档** | springdoc-openapi → `/swagger-ui.html`；手写契约 [API.md](API.md)（18 接口 + 13 类主事件流 + 4 类组局流 + 错误码） | ✅ 已实现 |
| **测试代码** | JUnit 5 单测 + MockMvc 集成（84 例全绿，覆盖 I-1~I-15）；Playwright E2E 5 条（`pnpm -C e2e test` 约 35s，假模型驱动） | ✅ 已实现 |
| **README** 含：运行指南 / 环境变量配置 / 技术选型说明 / API 列表 / 已完成能力与后续改进方向 | README §5 运行、`.env.example` 环境变量、§3 选型、§6 API 索引、§8 差异；「已完成 / 后续改进」见 §2.4 | ✅ 已实现 |
| **Git 提交历史呈层级演进**（如 `docs/schema → ui-components → tests → feat: logic`） | 提交按 `docs → chore → feat(db) → feat(server) → feat(web) → test → fix` 分层推进，共 52 次（`docs/git-history.txt` 已导出） | ✅ 已实现 |

### 2.2 核心 Prompt 记录文档（必须 ≥5 段，四范式齐全）

| 要求 | 落点 |
| --- | --- |
| 原始核心 Prompt | PROMPT_LOG R-001（需求拆解）、R-003（并发模型）为原始 Prompt 骨架 |
| **SDD 阶段**：数据建模与 API 契约生成 | R-004（ER/7 表）、R-005（API/SSE 契约）、R-008（SQLite 重设计） |
| **DDD 阶段**：前端组件与页面生成 | R-012（中文 UI 与界面规范）、PRD §8 |
| **TDD 阶段**：核心逻辑测试用例编写与业务实现 | R-006（E2E/集成用例设计）、TESTING §4（单测清单先于实现） |
| **E2E 阶段**：端到端测试与质量闭环 | TESTING §2（5 条 E2E）、§7 交付前自检 |
| 每段 Prompt 后 1–2 句说明（当时意图 / 遇到的挑战 / 如何引导修正） | 每条记录的「目标 + 人工干预 + 结论」三行即为该说明；四范式归组表见 PROMPT_LOG §4.1 |

### 2.3 开发过程思路 & 工作流程说明（1–1.5 页）

独立文档 [WORKFLOW.md](WORKFLOW.md)，含：开发流程（六段 + 每段输入输出）、工具与模型使用方式、与 AI 协作中的 3 个典型问题及解决路径、对"工程化 AI 开发"的理解、效率台账（人工估算 40h → 实际 17h）。

### 2.4 README 必含的「已完成能力 / 后续改进方向」

| 已完成 | 后续改进 |
| --- | --- |
| 圆桌大厅、组局（席位流式生成）、认领席位、多轮并行讨论、轨迹面板、观点溯源、六段纪要落库 | 多用户同场围观（presence 广播）；账号体系；纪要二次校验（引用有效性）；FTS5 trigram 搜索；单机 → 多实例（Actor 迁 Redis） |

---

## 3. 交付包结构

```text
ai-panel-studio/                     ← 压缩包根目录（源码与文档同主目录）
├── README.md                        ← 运行指南 / 环境变量 / 技术选型 / API 列表 / 已完成与改进
├── AGENTS.md                        ← AI 协作约束总纲（红线 / 必做 / 模块门禁）
├── rules/                           ← 6 份分模块约束（前端视觉 / 接口并发 / 数据测试 / 工作流…）
├── docs/
│   ├── PRD.md  ARCHITECTURE.md  DATABASE.md  API.md
│   ├── TESTING.md  PROJECT_PLAN.md  PROMPT_LOG.md  DELIVERY.md  WORKFLOW.md
│   ├── assets/notice-rules.png      ← 作业要求截图（依据留档）
│   └── screenshots/                 ← 3 张核心页面截图（列表 / 圆桌 / 纪要）
├── server/                          ← Spring Boot 3.5.16（含 Flyway 脚本，含 5 场样例数据）
├── web/                             ← Vue 3 + TS（SFC + 原生 CSS）
├── e2e/                             ← Playwright
├── scripts/                         ← dev / build（无容器）
├── .env.example
└── .git/
```

```bash
git log --pretty=format:'%h %ad %s' --date=short > docs/git-history.txt
zip -r ai-panel-studio.zip . -x "*/node_modules/*" "*/target/*" "data/*"
```

- 仓库需给评审开通访问（GitHub/Gitee 邀请链接）；压缩包仅作备份，**以仓库为准**。
- 压缩包命名：`远程作业-AI圆桌演播厅-姓名.zip`。

---

## 4. 演示视频（旧版要求，保留为加分项）

现行说明未强制视频，但旧版要求"3–5 分钟讲解 + 演示"。仍建议录一条，内容按旧版三段：代码结构讲解 / 核心功能演示 / Agent 协作模式讲解。分镜沿用原稿：

| 时间 | 画面 | 要点 |
| --- | --- | --- |
| 0:00–0:20 | 标题 + 定位 | "解决的不是答案不够好，而是答案太单一" |
| 0:20–1:00 | 大厅 → 发起 → 席位浮现 | 1 主持 + 3 专家，立场互补 |
| 1:00–1:30 | 认领席位 → 主持人开场 | 三色区分；"我坐技术专家这位" |
| 1:30–2:30 | 插话 → **两路同时流式** | 必拍：A 慢不挡 B；轨迹面板；引用跳转 |
| 2:30–3:00 | Agent 协作讲解 | 立场注入 → 选人策略 → 并行 + 信号量 → 失败隔离 |
| 3:00–3:30 | 结束 → 六段纪要 → 刷新 | 纪要可溯源、落库可复盘 |
| 3:30–4:15 | 代码结构 | 五层分包 → SessionActor → SseRegistry → Flyway → E2E |
| 4:15–4:40 | 收尾 | 取舍与边界（SQLite 单写者、无容器、未做多人围观） |

---

## 5. 提交邮件模板

```text
收件人：xulei@wisqwest.com
主题：【远程作业提交】张三

正文：
您好，

附件为「AI 圆桌演播厅 MVP」远程作业提交：

1. 源码仓库：<GitHub/Gitee 链接>（已邀请访问；提交历史见 docs/git-history.txt，共 N 次）
2. 压缩包：见附件（含代码、数据库初始化脚本、5 场样例数据、文档、测试）
3. 核心 Prompt 记录：docs/PROMPT_LOG.md（13 段，覆盖 SDD / DDD / TDD / E2E 四范式）
4. 工作流程说明：docs/WORKFLOW.md（1.5 页，含 3 个典型问题与效率台账）

技术栈：Spring Boot 3.5.16 + JDK 17 + Vue 3 + SQLite

谢谢！
张三 / 电话 / 邮箱
```

发送前清单：仓库可访问 ☐ · zip 包 ☐ · Prompt 记录 ☐ · 工作流程说明 ☐ · 3 张截图 ☐ · 主题格式 ☐

---

## 6. 核心考察点自检（现行说明 5 条）

| 考察点 | 我们的应对 | 证据 |
| --- | --- | --- |
| 在不同阶段合理使用不同开发范式，避免大模型幻觉 | 六段推进 + 四范式（SDD/DDD/TDD/E2E）；每阶段先契约后实现，AI 输出必过测试 | WORKFLOW §2、PROMPT_LOG §4.1 |
| 基于业务需求驱动数据建模，与 API 契约精确对应 | 7 表 + 18 接口 + SSE 13 类事件，一一映射到 FR-1~FR-9（DATABASE §8、PRD §4.5） | DATABASE、API、PRD |
| 以演播厅视觉与交互驱动前端组件与状态流转 | 三色席位 + 状态点 + 消息流 + 轨迹时间线；四档响应式、区域独立滚动 | PRD §8、README §2.2 |
| 核心逻辑遵循 TDD，含完整 E2E | 单测覆盖 SpeakerPolicy/ContextBuilder/状态机；84 例单测与集成 + 5 条 Playwright E2E | TESTING §2–§4 |
| 考察 Prompt 结构化表达与引导 AI 解决工程问题 | 记录含"意图/挑战/修正"；7 条走偏案例（含依据版本搞错） | PROMPT_LOG §3/§5 |
| UI 是否真的像在旁观一场演播厅讨论 | 席位/轨迹/引用/新发言提示等细节；布局与信息密度贴合讨论场景 | PRD §8 |

---

## 7. 版本差异核对（避免拿错依据）

| 项 | 旧版说明 | 现行版说明 | 本项目执行 |
| --- | --- | --- | --- |
| 数据库 | 未指定 | **SQLite** | SQLite ✅ |
| 交付物 | 11 项（含 ≥30 commit、≥3 E2E、3–5 分钟视频） | 3 项（源码+文档 / Prompt 记录 ≥5 段 / 工作流程说明） | 以本版为主，旧版数量要求作为加分项超额完成（7 表/18 API/52 commit） |
| 提交 | handshake@gaodun.cn，主题【AI开发实习生-姓名-岗位】 | **xulei@wisqwest.com**，主题【远程作业提交】姓名 | 按现行版 |

---

## 8. 交付前 24 小时检查表

- [ ] 删 `data/panel-studio.db` 重启能自动建库 + 5 场样例数据（证明初始化脚本与数据质量）
- [ ] `/swagger-ui.html` 可访问，endpoint 数量与 API.md 一致（18）
- [ ] 4 个核心页面截图入 `docs/screenshots/` 并嵌入 README §2.2（大厅 / 组局 / 现场 / 纪要，含窄屏）
- [ ] PROMPT_LOG 覆盖四范式，且每段有"意图/挑战/修正"
- [ ] `AGENTS.md` + `rules/`（6 份）随仓库交付，且与实现一致（约束不是摆设）
- [ ] WORKFLOW.md 1–1.5 页，含 3 个典型问题与效率台账
- [ ] README「已完成能力 / 后续改进方向」两节齐全
- [ ] `git log` 呈层级（docs → feat(db) → feat(server) → feat(web) → test），无"更新/修改"
- [ ] `docs/git-history.txt` 已导出；仓库已邀请评审
- [ ] zip 不含 `node_modules/` `target/` `data/`
- [ ] 邮件主题、收件人、附件无误；链接可匿名访问
