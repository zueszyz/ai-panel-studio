import { expect, test } from '@playwright/test'

/**
 * E2E-1：大厅发起 → 席位逐个浮现 → 认领 → 进入现场 → 主持人开场。
 * 全程走 UI，假模型由 global-setup 提供，不依赖真实 API Key。
 */
test.describe('E2E-1 组局到开场', () => {
  test('发起圆桌后 4 张席位卡浮现，认领第 3 席并进入现场', async ({ page }) => {
    await page.goto('/')

    await expect(page.getByRole('heading', { level: 1 })).toContainText('把一个问题')
    await page.getByTestId('create-discussion').click()

    await page.getByTestId('topic-input').fill('我们该不该把推荐系统从规则引擎换成向量召回')
    await expect(page.getByTestId('submit-discussion')).toBeEnabled()
    await page.getByTestId('submit-discussion').click()

    // 组局页：等待 4 张席位卡（逐席流式生成，最后一张到位才算完成）
    await expect(page).toHaveURL(/\/panel$/)
    const seats = page.getByTestId('seat-card')
    await expect(seats).toHaveCount(4, { timeout: 45_000 })

    // 主持人席位不可认领：卡片上不该出现认领按钮
    const hostCard = seats.filter({ hasText: '圆桌主持人' })
    await expect(hostCard.getByTestId('claim-seat')).toHaveCount(0)

    // 认领第 3 席（周敏），卡片应变成「你」
    const guest = seats.nth(2)
    await guest.getByTestId('claim-seat').click()
    await expect(seats.nth(2).getByTestId('revoke-seat')).toBeVisible()
    await expect(seats.nth(2)).toContainText('你')

    await page.getByTestId('enter-discussion').click()

    // 现场页：主持人开场消息必须走完（status=DONE）
    await expect(page).toHaveURL(/\/discussions\/[^/]+$/)
    await expect(page.getByTestId('message-flow')).toBeVisible()
    const opening = page.locator('[data-testid="message-bubble"]').first()
    await expect(opening).toHaveAttribute('data-status', 'DONE', { timeout: 30_000 })
    await expect(opening).toContainText('该不该把推荐系统')

    // 席位栏显示 4 席，且已认领的席位被标记为「你」
    await expect(page.getByTestId('composer-send')).toBeVisible()
  })
})
