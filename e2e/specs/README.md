# E2E 用例（已落地）

5 条用例对应 `docs/TESTING.md` §2，全部在 `e2e/specs/` 下，跑一条命令即可：

```bash
pnpm -C e2e test     # 约 35s，含启动假模型 / 后端 / 前端
pnpm -C e2e report   # 失败时看 HTML 报告
```

| 文件 | 场景 | 关键断言 |
| --- | --- | --- |
| `e2e-1-panel.spec.ts` | 发起 → 组局 → 认领 → 进入现场 | 4 张席位卡、主持人席位不可认领、认领后变「你」、开场 `data-status=DONE` |
| `e2e-2-parallel.spec.ts` | 插话 → 多 Agent 并行流式 | ≥2 条同时 `STREAMING`；周敏先 `DONE` 时陈立仍在流式（慢的不挡快的） |
| `e2e-3-minutes.spec.ts` | 结束 → 生成纪要 → 刷新 → 溯源 | 六段齐全、硬刷新后内容一致、溯源跳回原发言 |
| `e2e-4-degrade.spec.ts` | 单席位失败 → 重试 | 只失败一条、卡片给原因与重试、重试后同一 `messageId` 变成 `DONE` 且不新增气泡 |
| `e2e-5-reconnect.spec.ts` | 事件流连不上 → 恢复 | 「连接中断」横幅出现 → 自动重连后消失 → 快照补齐且无重复气泡 |

## 环境怎么来的

`global-setup.ts` 按顺序拉起，跑完自动收尾：

1. `node e2e/mock-llm.mjs` → OpenAI 兼容假模型（`:9099`）；
2. 删 `e2e/.tmp/e2e.db`，`mvn spring-boot:run` 起后端（`:8080`，Flyway 重建 6 场种子）；
3. `pnpm -C web dev` 起前端（`:5173`）。

前置：JDK 17 / Maven / pnpm 可用；**8080、5173、9099 未被占用**（先停掉 `scripts/dev`）。

## 约束

- 定位只用 `data-testid` 与中文文案，不用样式 class（样式会随视觉调整变化）。
- 每条用例自建 `discussion`，用例之间不共享；数据由下一次 globalSetup 删库清理。
- LLM 全部走假模型，不依赖真实 API Key，也不读取 `.env`。
- 失败只留截图；trace/video 默认关闭（避免 test-results 下上百个小文件触发批量删除保护）。
