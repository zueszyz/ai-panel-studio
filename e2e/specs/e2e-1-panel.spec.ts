import { expect, test } from '@playwright/test'

/**
 * E2E-1：大厅发起 → 先选自己的席位 → AI 生成其余 4 席 → 自动开场。
 * 全程走 UI，假模型由 global-setup 提供，不依赖真实 API Key。
 */
test.describe('E2E-1 选座到自动开场', () => {
  test('发起圆桌后选行业分析师席位，生成 5 席并自动进入现场', async ({ page }) => {
    await page.goto('/')

    await expect(page.getByRole('heading', { level: 1 })).toContainText('把一个问题')
    await page.getByTestId('create-discussion').click()

    await page.getByTestId('topic-input').fill('我们该不该把推荐系统从规则引擎换成向量召回')
    await expect(page.getByTestId('submit-discussion')).toBeEnabled()
    await page.getByTestId('submit-discussion').click()

    // 组局页：先选座，再生成其余 AI 席位
    await expect(page).toHaveURL(/\/panel$/)
    await expect(page.getByTestId('seat-picker')).toBeVisible()
    await page.getByTestId('role-ANALYST').click()
    await page.getByTestId('generate-with-seat').click()

    // 1 主持 + 4 专家，逐席流式生成，最后一张到位才算完成
    const seats = page.getByTestId('seat-card')
    await expect(seats).toHaveCount(5, { timeout: 45_000 })

    // 主持人席位不可认领：卡片上不该出现认领按钮
    const hostCard = seats.filter({ hasText: '圆桌主持人' })
    await expect(hostCard.getByTestId('claim-seat')).toHaveCount(0)

    // 用户自选的席位显示为「你」，且不能再次被认领
    const mine = seats.filter({ has: page.getByTestId('revoke-seat') })
    await expect(mine).toHaveCount(1)
    await expect(mine).toContainText('行业分析师')
    await expect(mine).toContainText('你')

    // 自动开场：直接进入现场页，主持人开场消息必须走完（status=DONE）
    await expect(page).toHaveURL(/\/discussions\/[^/]+$/, { timeout: 30_000 })
    await expect(page.getByTestId('message-flow')).toBeVisible()
    const opening = page.locator('[data-testid="message-bubble"]').first()
    await expect(opening).toHaveAttribute('data-status', 'DONE', { timeout: 30_000 })
    await expect(opening).toContainText('该不该把推荐系统')

    await expect(page.getByTestId('composer-send')).toBeVisible()
  })
})
