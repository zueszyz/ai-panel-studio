import { expect, test } from '@playwright/test'
import {
  createDiscussion,
  generatePanel,
  startDiscussion,
  waitForRunsSettled,
} from './helpers'

/**
 * E2E-5：连接中断 → 提示"正在恢复" → 恢复后与库一致。
 *
 * 用 Playwright 的 route 拦截 `/stream` 来制造"连不上"，比断网模拟更确定：
 * 断网模拟对已经建立的 SSE 半开连接不生效（心跳仍在流动），容易假红。
 */
test.describe('E2E-5 断线重连', () => {
  test('事件流连不上时提示重连，恢复后补齐消息且不重复', async ({ page, request }) => {
    const id = await createDiscussion(request)
    await generatePanel(request, id)
    await startDiscussion(request, id)
    await waitForRunsSettled(request, id, { minUserReply: 0 })

    const streamPattern = '**/api/discussions/*/stream'
    await page.route(streamPattern, (route) => route.abort())
    await page.goto(`/discussions/${id}`, { waitUntil: 'domcontentloaded' })

    // 1) 连不上时给出可感知的提示（而不是白屏）
    await expect(page.getByTestId('connection-banner')).toBeVisible({ timeout: 20_000 })

    // 2) 放行后自动重连成功，横幅消失
    await page.unroute(streamPattern)
    await expect(page.getByTestId('connection-banner')).toHaveCount(0, { timeout: 30_000 })

    // 3) 快照补齐：主持人开场等已有消息全部出现，且没有重复
    const bubbles = page.locator('[data-testid="message-bubble"]')
    await expect(bubbles.first()).toBeVisible({ timeout: 20_000 })
    await expect(bubbles.first()).toContainText('该不该')
    const ids = await bubbles.evaluateAll((nodes) =>
      nodes.map((node) => node.getAttribute('data-message-id')),
    )
    expect(ids.length).toBeGreaterThan(0)
    expect(new Set(ids).size, '重连后出现了重复气泡').toBe(ids.length)

    // 4) 重连后还能继续用：发一条，输入区不报错
    await page.getByTestId('composer-input').fill('重新连上之后补一句')
    await page.getByTestId('composer-send').click()
    await expect(page.getByText('重新连上之后补一句')).toBeVisible({ timeout: 20_000 })
  })
})
