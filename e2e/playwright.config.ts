import { defineConfig, devices } from '@playwright/test'

/**
 * E2E 配置：真实浏览器 + 本机前后端 + 假模型（无容器）。
 *
 * 整套环境由 global-setup.ts 拉起：假模型(9099) → 干净库 + 后端(8080) → 前端(5173)。
 * 跑之前确保 8080 / 5173 / 9099 没被其他进程占用（例如 `scripts/dev`）。
 */
export default defineConfig({
  testDir: './specs',
  globalSetup: './global-setup.ts',
  timeout: 90_000,
  expect: { timeout: 15_000 },
  fullyParallel: false,
  workers: 1,
  retries: 0,
  reporter: [['list'], ['html', { open: 'never' }]],
  use: {
    baseURL: process.env.E2E_BASE_URL ?? 'http://localhost:5173',
    // 只留失败截图：trace/video 会在 test-results 下生成上百个小文件，
    // 本机安全策略会拦截批量删除，清理阶段容易卡住。需要复盘时临时打开即可。
    trace: 'off',
    screenshot: 'only-on-failure',
    video: 'off',
    locale: 'zh-CN',
  },
  projects: [
    {
      name: 'chromium',
      use: { ...devices['Desktop Chrome'], viewport: { width: 1440, height: 900 } },
    },
  ],
})
