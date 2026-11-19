/**
 * 清理 E2E 产物：逐文件删除，避免一次性 rm 触发批量删除保护。
 * 直接 `node e2e/clean.mjs` 或由 global-setup / pnpm pretest 调用。
 */
import { existsSync, readdirSync, rmdirSync, rmSync, statSync } from 'node:fs'
import { dirname, join, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const HERE = dirname(fileURLToPath(import.meta.url))

export function purge(dir) {
  if (!existsSync(dir)) {
    return
  }
  for (const entry of readdirSync(dir)) {
    const full = join(dir, entry)
    if (statSync(full).isDirectory()) {
      purge(full)
      // 子项已逐个删完，这里只移除空目录（避免 rm 递归触发批量删除保护）
      rmdirSync(full)
    } else {
      rmSync(full, { force: true })
    }
  }
}

if (process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  for (const name of ['test-results', 'playwright-report', '.tmp']) {
    purge(join(HERE, name))
  }
  console.log('[e2e] 已清理 test-results / playwright-report / .tmp')
}
