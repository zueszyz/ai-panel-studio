import { expect, test } from '@playwright/test'
import {
  createDiscussion,
  generatePanel,
  openLive,
  postMessage,
  startDiscussion,
  waitForRunsSettled,
} from './helpers'

/**
 * E2E-2：用户插话 → 多 Agent 并行流式。
 *
 * 断言口径不是"看起来同时在动"，而是：**慢的还在流，快的已经结束**。
 * 假模型给周敏 600ms、陈立 1800ms，如果实现是串行的，这个顺序不可能出现。
 */
test.describe('E2E-2 并行流式', () => {
  test('插话后两席同时进入流式，快席位先于慢席位完成', async ({ page, request }) => {
    const id = await createDiscussion(request)
    await generatePanel(request, id)
    await startDiscussion(request, id)

    await openLive(page, id)

    // 开场会自动触发第 1 回合：先等这一轮收尾，否则"谁在流式"会混进自动回合的发言
    await waitForRunsSettled(request, id, { minUserReply: 0 })
    const before = await page.locator('[data-testid="message-bubble"]').count()

    // 同时点名两位专家，确保两人都被选中
    await page.getByTestId('composer-input').fill('陈立、周敏，迁移成本这么高，谁来兜底？')
    await page.getByTestId('composer-send').click()

    // 1) 至少有两条消息同时在流式（并行，而不是排队）
    await expect
      .poll(async () => page.locator('[data-testid="message-bubble"][data-status="STREAMING"]').count(), {
        timeout: 20_000,
        message: '没有观察到并行流式',
      })
      .toBeGreaterThanOrEqual(2)

    // 2) 周敏先完成时，陈立必须还在流式 —— 快的不被慢的拖住
    await expect(page.locator('[data-name="周敏"][data-status="DONE"]').last()).toBeVisible({
      timeout: 20_000,
    })
    await expect(page.locator('[data-name="陈立"][data-status="STREAMING"]').last()).toBeVisible()

    // 3) 收尾后两位都落成 DONE，内容非空
    await expect(page.locator('[data-name="陈立"]').last()).toHaveAttribute('data-status', 'DONE', {
      timeout: 30_000,
    })
    await expect(page.locator('[data-name="周敏"]').last()).toContainText('同业')
    await expect(page.locator('[data-name="陈立"]').last()).toContainText('迁移成本')

    // 4) 我的插话在列表里，且它之后确实出现了新的专家发言
    const names = await page
      .locator('[data-testid="message-bubble"]')
      .evaluateAll((nodes) => nodes.map((node) => node.getAttribute('data-name')))
    expect(names).toContain('你')
    const mine = names.lastIndexOf('你')
    expect(names.length - 1 - mine).toBeGreaterThanOrEqual(2)
    const replies = names.slice(mine + 1)
    expect(replies.some((name) => name === '陈立' || name === '周敏')).toBe(true)
    // 插话没有把已有的发言挤掉：气泡只增不减
    expect(names.length).toBeGreaterThan(before)
  })
})
