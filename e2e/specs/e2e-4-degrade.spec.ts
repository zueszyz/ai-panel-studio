import { expect, test } from '@playwright/test'
import {
  createDiscussion,
  generatePanel,
  openLive,
  startDiscussion,
  waitForRunsSettled,
} from './helpers'

/**
 * E2E-4：单席位失败 → 卡片可重试 → 重试成功，整场仍可继续。
 * 假模型只让"接下来一条"发言返回 500，重试即放行。
 * 不假定具体是哪位专家失败 —— 调度策略会变，断言只关心"失败的这一条"。
 */
test.describe('E2E-4 单席位降级', () => {
  test('一位专家失败时只影响自己，重试后原位恢复', async ({ page, request }) => {
    const id = await createDiscussion(request)
    await generatePanel(request, id)
    await startDiscussion(request, id)

    await openLive(page, id)
    await waitForRunsSettled(request, id, { minUserReply: 0 })

    await page.getByTestId('composer-input').fill('冷启动的问题到底怎么解？这次请模拟一次失败看看。')
    await page.getByTestId('composer-send').click()

    // 1) 出现且只出现一条失败：卡片给出原因与重试入口
    const failed = page.locator('[data-testid="message-bubble"][data-status="FAILED"]')
    await expect(failed).toHaveCount(1, { timeout: 30_000 })
    await expect(failed).toContainText('模型服务暂时不可用')
    const messageId = await failed.getAttribute('data-message-id')
    expect(messageId).toBeTruthy()

    // 2) 整场没有因此结束，输入区仍然可用
    await expect(page.getByTestId('composer-send')).toBeVisible()
    await expect(page.locator('[data-testid="message-bubble"]').first()).toContainText('该不该')

    // 3) 点重试：同一条消息原位回到流式，并最终完成
    await failed.getByRole('button', { name: '重试' }).click()
    await expect(page.locator(`[data-message-id="${messageId}"]`)).toHaveAttribute(
      'data-status',
      'DONE',
      { timeout: 30_000 },
    )
    // 重试不新增气泡：同一个 messageId 在列表里只出现一次
    await expect(page.locator(`[data-message-id="${messageId}"]`)).toHaveCount(1)
  })
})
