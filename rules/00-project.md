# rules/00 · 项目边界与技术栈

> 每次会话开始先读这一页。它定义"做什么、不做什么、用什么做"，避免 AI 自行扩张范围。

## 1. 产品边界

**做**：输入议题 → 生成 1 主持 + 3 专家（立场互补）→ 多 Agent 并行流式讨论（用户可插话）→ 轨迹可观测 → 六段纪要可溯源并落库。

**不做**（明确出界，不要"顺手加上"）：

| 不做 | 原因 |
| --- | --- |
| 多人同场围观（presence 广播） | 需额外协议，超出 MVP |
| 账号体系 / OAuth | 用匿名 `X-Device-Id`，`users` 表占位即可 |
| 语音 / 数字人 / TTS | 与核心验证无关 |
| 知识库 / 联网检索 | 会让"专家观点"退化成检索摘要 |
| 移动端原生 App | Web 响应式足够 |

## 2. 技术栈（版本锁定，不得擅自升降）

| 层 | 选型 | 版本 |
| --- | --- | --- |
| 后端 | Spring Boot（Spring MVC） | 3.5.16 |
| 语言 | JDK | 17（无虚拟线程，线程模型要显式设计） |
| 数据访问 | MyBatis-Plus | 3.5.12（`mybatis-plus-spring-boot3-starter`） |
| 迁移 | Flyway | 11（+ `flyway-database-sqlite`） |
| 数据库 | SQLite | 3.40+（WAL / busy_timeout / foreign_keys） |
| 前端 | Vue | 3.4（Composition API + `<script setup>` + TS） |
| 构建 | Maven 3.9 / Vite 5 / pnpm 8 | — |
| 状态与路由 | Pinia 2 / Vue Router 4 | — |
| 测试 | JUnit 5 + MockMvc + WireMock + Playwright | — |

**禁止**：引入 UI 组件库（Element Plus / Element / AntD / Naive / Arco）；引入第二个 ORM；换成 WebFlux；升 JDK 21 或降 Boot 版本。

## 3. 运行方式（不使用容器）

```bash
# 开发
mvn -f server/pom.xml spring-boot:run      # :8080，启动即 Flyway 迁移
pnpm -C web dev                            # :5173，Vite 代理 /api

# 演示（单进程）
./scripts/build.sh && java -jar server/target/panel-studio.jar
```

- 不写 `Dockerfile` / `docker-compose.yml` / nginx 配置。
- 数据文件 `./data/panel-studio.db`，可被 `SQLITE_PATH` 覆盖；`data/` 不进版本库。

## 4. 界面语言

- **全中文**：按钮、占位符、空态、错误、日期、无障碍标签，统一走 `web/src/lib/copy.ts`。
- 术语固定：圆桌 / 议题 / 席位 / 认领 / 回合 / 发言 / 插话 / 轨迹 / 纪要 / 溯源 / 收敛。不许同义词混用。

## 5. 安全边界

- `LLM_API_KEY` 只从环境变量读；前端只调本项目 `/api/**`，不直连模型厂商。
- `.env` 不入库；`.env.example` 只放占位值。
- 日志不打印 Key、不打印完整上游响应体。

## 6. 仓库结构约定

```
server/   Spring Boot（api / application / orchestrator / domain / infra）
web/      Vue 3（pages / components / stores / lib / styles）
e2e/      Playwright
scripts/  dev / build（无容器）
docs/     设计与过程文档
rules/    本目录（约束文件）
data/     SQLite 文件（gitignore）
```

新增顶层目录必须先在 PRD 或 ARCHITECTURE 登记，再落地。
