import { expect, test } from '@playwright/test'
import { prepareEndedDiscussion } from './helpers'

/**
 * E2E-3：结束讨论 → 生成纪要 → 六段齐全 → 刷新后一致 → 溯源可跳回原发言。
 * 数据由接口准备（helper），断言全部走 UI —— 这才是用户真正看到的路径。
 */
test.describe('E2E-3 纪要收敛', () => {
  test('生成六段纪要，刷新不丢，溯源能跳回原发言', async ({ page, request }) => {
    const id = await prepareEndedDiscussion(request)

    await page.goto(`/discussions/${id}/minutes`)
    await page.getByTestId('generate-minutes').click()

    const content = page.getByTestId('minutes-content')
    await expect(content).toBeVisible({ timeout: 45_000 })

    // 生成完毕后状态徽标必须从「纪要生成中」收敛为「已结束」，不能停留在中间态
    await expect(page.getByTestId('status-badge')).toContainText('已结束')

    // 1) 六段齐全
    const blocks = content.locator('.block')
    await expect(blocks).toHaveCount(6)
    for (const heading of ['议题与目标', '各方核心主张', '分歧点', '已达成的共识', '待验证假设', '建议下一步']) {
      await expect(content).toContainText(heading)
    }

    // 2) 主张带溯源入口（假模型从本场消息里取了真实 id）
    const traces = content.getByTestId('trace-claim')
    await expect(traces.first()).toBeVisible()

    // 3) 硬刷新后内容一致（读库，不是读内存）
    const before = await blocks.allInnerTexts()
    await page.reload({ waitUntil: 'domcontentloaded' })
    await expect(page.getByTestId('minutes-content')).toBeVisible()
    const after = await page.getByTestId('minutes-content').locator('.block').allInnerTexts()
    expect(after).toEqual(before)
    await expect(page.getByTestId('minutes-content')).toContainText('第 1 版')

    // 4) 点溯源 → 回到现场并定位到那条发言
    await page.getByTestId('trace-claim').first().click()
    await expect(page).toHaveURL(new RegExp(`/discussions/${id}\\?messageId=`))
    const target = page.locator('[data-testid="message-bubble"]').first()
    await expect(target).toBeVisible()
    const messageId = new URL(page.url()).searchParams.get('messageId')
    await expect(page.locator(`[data-message-id="${messageId}"]`)).toBeVisible()
  })
})
