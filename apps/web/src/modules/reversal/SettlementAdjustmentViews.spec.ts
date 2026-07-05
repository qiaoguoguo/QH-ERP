import ElementPlus from 'element-plus'
import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import type { Component } from 'vue'
import { createMemoryHistory, createRouter } from 'vue-router'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import SettlementAdjustmentDetailView from './SettlementAdjustmentDetailView.vue'
import SettlementAdjustmentFormView from './SettlementAdjustmentFormView.vue'
import SettlementAdjustmentListView from './SettlementAdjustmentListView.vue'
import { useAuthStore } from '../../stores/authStore'

const returnRefundReversalApiMock = vi.hoisted(() => ({
  settlementAdjustments: {
    list: vi.fn(),
    get: vi.fn(),
    create: vi.fn(),
    update: vi.fn(),
    post: vi.fn(),
    cancel: vi.fn(),
  },
  settlementAdjustmentSources: {
    list: vi.fn(),
  },
  traces: {
    list: vi.fn(),
  },
}))

vi.mock('../../shared/api/returnRefundReversalApi', async (importOriginal) => ({
  ...(await importOriginal<typeof import('../../shared/api/returnRefundReversalApi')>()),
  returnRefundReversalApi: returnRefundReversalApiMock,
}))

const receiptSourceView = {
  sourceType: 'RECEIPT',
  sourceId: 70,
  sourceNo: 'RCPT202607050001',
  businessDate: '2026-07-05',
  status: 'POSTED',
  amount: '60.00',
  canViewSource: true,
  restricted: false,
  resourceRouteName: 'finance-receipt-detail',
  resourceRouteParams: { id: 70 },
}

const settlementAdjustmentSourceView = {
  sourceType: 'SETTLEMENT_ADJUSTMENT',
  sourceId: 5,
  sourceNo: 'SA202607050001',
  businessDate: '2026-07-05',
  status: 'DRAFT',
  amount: '60.00',
  canViewSource: true,
  restricted: false,
  resourceRouteName: 'finance-settlement-adjustment-detail',
  resourceRouteParams: { id: 5 },
}

const settlementTrace = {
  traceKey: 'RECEIPT:70:0:SETTLEMENT_ADJUSTMENT:5:0',
  direction: 'REVERSE_TO_SOURCE',
  source: receiptSourceView,
  reverse: settlementAdjustmentSourceView,
  settlementAdjustmentId: 5,
  businessDate: '2026-07-05',
  amount: '60.00',
  status: 'POSTED',
  canViewResource: true,
  restricted: false,
  resourceRouteName: 'finance-settlement-adjustment-detail',
  resourceRouteParams: { id: 5 },
}

const restrictedSettlementTrace = {
  traceKey: 'opaque-settlement-trace',
  direction: 'REVERSE_TO_SOURCE',
  source: {
    sourceType: 'RECEIPT',
    canViewSource: false,
    restricted: true,
    restrictedMessage: '来源无查看权限',
  },
  reverse: settlementAdjustmentSourceView,
  settlementAdjustmentId: 5,
  canViewResource: false,
  restricted: true,
  restrictedMessage: '来源无查看权限',
}

const settlementAdjustmentDetail = {
  id: 5,
  adjustmentNo: 'SA202607050001',
  settlementSide: 'RECEIVABLE',
  adjustmentType: 'REFUND',
  source: receiptSourceView,
  targetId: 80,
  targetNo: 'AR202607050001',
  businessDate: '2026-07-05',
  targetOriginalAmount: '500.00',
  targetAdjustedAmountBefore: '100.00',
  targetAdjustableAmountBefore: '400.00',
  amount: '60.00',
  targetRemainingAmountAfterPost: '340.00',
  targetStatusAfterPost: 'PARTIALLY_RECEIVED',
  status: 'DRAFT',
  createdAt: '2026-07-05T14:00:00+08:00',
  updatedAt: '2026-07-05T14:00:00+08:00',
  clientRequestId: 'settlement-adjustment-client-1',
  remark: '客户退款冲减',
  traces: [settlementTrace],
}

const settlementAdjustmentSource = {
  sourceType: 'RECEIPT',
  sourceId: 70,
  sourceNo: 'RCPT202607050001',
  settlementSide: 'RECEIVABLE',
  targetId: 80,
  targetNo: 'AR202607050001',
  businessDate: '2026-07-05',
  originalAmount: '500.00',
  adjustedAmount: '100.00',
  adjustableAmount: '400.00',
  status: 'POSTED',
}

const paymentSource = {
  sourceType: 'PAYMENT',
  sourceId: 71,
  sourceNo: 'PAY202607050001',
  settlementSide: 'PAYABLE',
  targetId: 81,
  targetNo: 'AP202607050001',
  businessDate: '2026-07-05',
  originalAmount: '300.00',
  adjustedAmount: '80.00',
  adjustableAmount: '220.00',
  status: 'POSTED',
}

const page = <T>(items: T[], pageSize = 10) => ({ items, page: 1, pageSize, total: items.length })

async function mountSettlementView(component: Component, path: string, permissions: string[]) {
  const pinia = createPinia()
  setActivePinia(pinia)
  useAuthStore().setSession({
    user: { id: 1, username: 'settlement-user', displayName: '往来用户', status: 'ENABLED' },
    menus: [],
    permissions,
  })
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/', name: 'home', component: { render: () => null } },
      { path: '/finance/settlement-adjustments', name: 'finance-settlement-adjustments', component },
      { path: '/finance/settlement-adjustments/create', name: 'finance-settlement-adjustment-create', component },
      { path: '/finance/settlement-adjustments/:id', name: 'finance-settlement-adjustment-detail', component },
      { path: '/finance/settlement-adjustments/:id/edit', name: 'finance-settlement-adjustment-edit', component },
      { path: '/finance/receipts/:id', name: 'finance-receipt-detail', component: { render: () => null } },
      { path: '/finance/payments/:id', name: 'finance-payment-detail', component: { render: () => null } },
    ],
  })
  await router.push(path)
  await router.isReady()
  const wrapper = mount(component, {
    global: {
      plugins: [pinia, router, ElementPlus],
    },
  })
  await flushPromises()
  return { wrapper, router }
}

describe('往来冲减前端页面', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    returnRefundReversalApiMock.settlementAdjustments.list.mockResolvedValue(page([settlementAdjustmentDetail]))
    returnRefundReversalApiMock.settlementAdjustments.get.mockResolvedValue(settlementAdjustmentDetail)
    returnRefundReversalApiMock.settlementAdjustments.create.mockResolvedValue(settlementAdjustmentDetail)
    returnRefundReversalApiMock.settlementAdjustments.update.mockResolvedValue({ ...settlementAdjustmentDetail, amount: '70.00' })
    returnRefundReversalApiMock.settlementAdjustments.post.mockResolvedValue({ ...settlementAdjustmentDetail, status: 'POSTED' })
    returnRefundReversalApiMock.settlementAdjustments.cancel.mockResolvedValue({ ...settlementAdjustmentDetail, status: 'CANCELLED' })
    returnRefundReversalApiMock.settlementAdjustmentSources.list.mockResolvedValue(page([settlementAdjustmentSource, paymentSource], 20))
    returnRefundReversalApiMock.traces.list.mockResolvedValue([settlementTrace])
  })

  it('往来冲减列表支持筛选、创建入口和草稿权限按钮', async () => {
    const { wrapper, router } = await mountSettlementView(SettlementAdjustmentListView, '/finance/settlement-adjustments', [
      'finance:settlement-adjustment:view',
      'finance:settlement-adjustment:create',
      'finance:settlement-adjustment:update',
      'finance:settlement-adjustment:post',
      'finance:settlement-adjustment:cancel',
    ])

    expect(wrapper.text()).toContain('往来冲减')
    expect(wrapper.text()).toContain('SA202607050001')
    expect(wrapper.text()).toContain('应收冲减')
    expect(wrapper.find('[data-test="create-settlement-adjustment"]').exists()).toBe(true)
    expect(wrapper.find('[data-test="edit-settlement-adjustment"]').exists()).toBe(true)
    expect(wrapper.find('[data-test="post-settlement-adjustment"]').exists()).toBe(true)
    expect(wrapper.find('[data-test="cancel-settlement-adjustment"]').exists()).toBe(true)

    await wrapper.find('input[name="settlement-adjustment-keyword"]').setValue('SA')
    await wrapper.find('[data-test="search-settlement-adjustments"]').trigger('click')
    await flushPromises()
    expect(returnRefundReversalApiMock.settlementAdjustments.list).toHaveBeenLastCalledWith(expect.objectContaining({
      keyword: 'SA',
      page: 1,
    }))

    await wrapper.find('[data-test="create-settlement-adjustment"]').trigger('click')
    await flushPromises()
    expect(router.currentRoute.value.name).toBe('finance-settlement-adjustment-create')
  })

  it('新建往来冲减只展示允许来源，校验金额后以字符串提交', async () => {
    const { wrapper, router } = await mountSettlementView(SettlementAdjustmentFormView, '/finance/settlement-adjustments/create', ['finance:settlement-adjustment:create'])

    expect(wrapper.text()).toContain('新建往来冲减')
    expect(wrapper.text()).toContain('RCPT202607050001')
    expect(wrapper.text()).toContain('可冲金额')
    expect(wrapper.text()).toContain('收款记录')
    expect(wrapper.text()).toContain('付款记录')
    expect(wrapper.text()).not.toContain('生产退料')
    expect(wrapper.text()).not.toContain('生产补料')
    expect(returnRefundReversalApiMock.settlementAdjustmentSources.list).toHaveBeenCalledWith({
      keyword: '',
      settlementSide: 'RECEIVABLE',
      sourceType: 'RECEIPT',
      page: 1,
      pageSize: 20,
    })

    await wrapper.find('input[name="settlement-adjustment-amount"]').setValue('401.00')
    await wrapper.find('[data-test="submit-settlement-adjustment"]').trigger('click')
    await flushPromises()
    expect(wrapper.text()).toContain('冲减金额不能超过可冲金额')
    expect(returnRefundReversalApiMock.settlementAdjustments.create).not.toHaveBeenCalled()

    await wrapper.find('input[name="settlement-adjustment-business-date"]').setValue('2026-07-05')
    await wrapper.find('input[name="settlement-adjustment-amount"]').setValue('60.00')
    await wrapper.find('textarea[name="settlement-adjustment-remark"]').setValue('客户退款冲减')
    await wrapper.find('[data-test="submit-settlement-adjustment"]').trigger('click')
    await flushPromises()

    expect(returnRefundReversalApiMock.settlementAdjustments.create).toHaveBeenCalledWith(expect.objectContaining({
      settlementSide: 'RECEIVABLE',
      adjustmentType: 'REFUND',
      sourceType: 'RECEIPT',
      sourceId: 70,
      targetId: 80,
      businessDate: '2026-07-05',
      amount: '60.00',
      remark: '客户退款冲减',
    }))
    expect(router.currentRoute.value.name).toBe('finance-settlement-adjustment-detail')
  })

  it('新建往来冲减可在多候选来源之间切换并按选中来源校验提交', async () => {
    const { wrapper } = await mountSettlementView(SettlementAdjustmentFormView, '/finance/settlement-adjustments/create', ['finance:settlement-adjustment:create'])

    expect(wrapper.find('[data-test="settlement-adjustment-selected-source"]').text()).toContain('RCPT202607050001')

    await wrapper.find('[data-test="select-settlement-adjustment-source-PAYMENT-71-81"]').trigger('click')
    await flushPromises()

    expect(wrapper.find('[data-test="settlement-adjustment-selected-source"]').text()).toContain('PAY202607050001')
    expect(wrapper.find('[data-test="settlement-adjustment-selected-target"]').text()).toContain('AP202607050001')
    expect(wrapper.find('[data-test="settlement-adjustment-adjustable-amount"]').text()).toContain('220.00')

    await wrapper.find('input[name="settlement-adjustment-amount"]').setValue('221.00')
    await wrapper.find('[data-test="submit-settlement-adjustment"]').trigger('click')
    await flushPromises()

    expect(wrapper.text()).toContain('冲减金额不能超过可冲金额')
    expect(returnRefundReversalApiMock.settlementAdjustments.create).not.toHaveBeenCalled()

    await wrapper.find('input[name="settlement-adjustment-amount"]').setValue('80.00')
    await wrapper.find('[data-test="submit-settlement-adjustment"]').trigger('click')
    await flushPromises()

    expect(returnRefundReversalApiMock.settlementAdjustments.create).toHaveBeenCalledWith(expect.objectContaining({
      settlementSide: 'PAYABLE',
      sourceType: 'PAYMENT',
      sourceId: 71,
      targetId: 81,
      amount: '80.00',
    }))
  })

  it('候选来源刷新为空后不能提交旧来源', async () => {
    const { wrapper } = await mountSettlementView(SettlementAdjustmentFormView, '/finance/settlement-adjustments/create', ['finance:settlement-adjustment:create'])

    expect(wrapper.text()).toContain('RCPT202607050001')
    returnRefundReversalApiMock.settlementAdjustmentSources.list.mockResolvedValueOnce(page([]))
    await wrapper.find('[data-test="search-settlement-adjustment-sources"]').trigger('click')
    await flushPromises()

    expect(wrapper.text()).toContain('暂无可冲减来源')
    await wrapper.find('input[name="settlement-adjustment-business-date"]').setValue('2026-07-05')
    await wrapper.find('input[name="settlement-adjustment-amount"]').setValue('60.00')
    await wrapper.find('[data-test="submit-settlement-adjustment"]').trigger('click')
    await flushPromises()

    expect(wrapper.text()).toContain('请选择候选来源')
    expect(returnRefundReversalApiMock.settlementAdjustments.create).not.toHaveBeenCalled()
  })

  it('候选来源刷新为不同来源后使用当前可见来源提交', async () => {
    const { wrapper } = await mountSettlementView(SettlementAdjustmentFormView, '/finance/settlement-adjustments/create', ['finance:settlement-adjustment:create'])

    expect(wrapper.text()).toContain('RCPT202607050001')
    returnRefundReversalApiMock.settlementAdjustmentSources.list.mockResolvedValueOnce(page([paymentSource]))
    await wrapper.find('[data-test="search-settlement-adjustment-sources"]').trigger('click')
    await flushPromises()

    expect(wrapper.text()).toContain('PAY202607050001')
    expect(wrapper.text()).not.toContain('RCPT202607050001')
    await wrapper.find('input[name="settlement-adjustment-amount"]').setValue('80.00')
    await wrapper.find('[data-test="submit-settlement-adjustment"]').trigger('click')
    await flushPromises()

    expect(returnRefundReversalApiMock.settlementAdjustments.create).toHaveBeenCalledWith(expect.objectContaining({
      settlementSide: 'PAYABLE',
      sourceType: 'PAYMENT',
      sourceId: 71,
      targetId: 81,
      amount: '80.00',
    }))
    expect(returnRefundReversalApiMock.settlementAdjustments.create).not.toHaveBeenCalledWith(expect.objectContaining({
      sourceType: 'RECEIPT',
      sourceId: 70,
      targetId: 80,
    }))
  })

  it('来源受限编辑只提交可变字段且不泄露来源字段', async () => {
    returnRefundReversalApiMock.settlementAdjustments.get.mockResolvedValueOnce({
      ...settlementAdjustmentDetail,
      source: {
        sourceType: 'RECEIPT',
        canViewSource: false,
        restricted: true,
        restrictedMessage: '来源无查看权限',
      },
    })
    const { wrapper } = await mountSettlementView(SettlementAdjustmentFormView, '/finance/settlement-adjustments/5/edit', ['finance:settlement-adjustment:update'])

    expect(wrapper.text()).toContain('来源无查看权限')
    expect(wrapper.text()).not.toContain('RCPT202607050001')
    expect(wrapper.text()).not.toContain('60.00客户退款')
    await wrapper.find('input[name="settlement-adjustment-amount"]').setValue('70.00')
    await wrapper.find('textarea[name="settlement-adjustment-remark"]').setValue('受限来源更新')
    await wrapper.find('[data-test="submit-settlement-adjustment"]').trigger('click')
    await flushPromises()

    expect(returnRefundReversalApiMock.settlementAdjustments.update).toHaveBeenCalledWith('5', {
      businessDate: '2026-07-05',
      amount: '70.00',
      clientRequestId: 'settlement-adjustment-client-1',
      remark: '受限来源更新',
    })
  })

  it('已过账或已取消往来冲减不可编辑', async () => {
    returnRefundReversalApiMock.settlementAdjustments.get.mockResolvedValueOnce({ ...settlementAdjustmentDetail, status: 'POSTED' })
    const { wrapper } = await mountSettlementView(SettlementAdjustmentFormView, '/finance/settlement-adjustments/5/edit', ['finance:settlement-adjustment:update'])

    expect(wrapper.text()).toContain('当前往来冲减已过账，不可编辑')
    expect(wrapper.find('[data-test="submit-settlement-adjustment"]').exists()).toBe(false)
  })

  it('详情展示来源、目标余额、追溯和权限操作', async () => {
    returnRefundReversalApiMock.traces.list.mockResolvedValueOnce([settlementTrace, restrictedSettlementTrace])
    const { wrapper, router } = await mountSettlementView(SettlementAdjustmentDetailView, '/finance/settlement-adjustments/5', [
      'finance:settlement-adjustment:view',
      'finance:settlement-adjustment:update',
      'finance:settlement-adjustment:post',
      'finance:settlement-adjustment:cancel',
      'business:reversal:view',
    ])

    expect(wrapper.text()).toContain('往来冲减详情')
    expect(wrapper.text()).toContain('SA202607050001')
    expect(wrapper.text()).toContain('退款记录')
    expect(wrapper.text()).toContain('目标应收/应付')
    expect(wrapper.text()).toContain('冲减前后余额')
    expect(wrapper.find('[data-test="edit-settlement-adjustment-detail"]').exists()).toBe(true)
    expect(wrapper.find('[data-test="post-settlement-adjustment-detail"]').exists()).toBe(true)
    expect(wrapper.find('[data-test="cancel-settlement-adjustment-detail"]').exists()).toBe(true)

    await wrapper.find('[data-test="open-reversal-trace"]').trigger('click')
    await flushPromises()
    expect(returnRefundReversalApiMock.traces.list).toHaveBeenCalledWith({
      sourceType: 'SETTLEMENT_ADJUSTMENT',
      sourceId: 5,
      direction: 'REVERSE_TO_SOURCE',
      includeRestricted: true,
    })
    expect(wrapper.text()).toContain('往来冲减')
    expect(wrapper.text()).toContain('来源无查看权限')

    await wrapper.find('[data-test="view-source-document"]').trigger('click')
    await flushPromises()
    expect(router.currentRoute.value.name).toBe('finance-receipt-detail')
    expect(router.currentRoute.value.params.id).toBe('70')
  })
})
