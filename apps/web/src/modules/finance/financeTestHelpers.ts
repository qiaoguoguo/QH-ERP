import ElementPlus from 'element-plus'
import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createMemoryHistory, createRouter } from 'vue-router'
import { useAuthStore } from '../../stores/authStore'

export function page<T>(items: T[] = [], pageNumber = 1, pageSize = 20) {
  return {
    items,
    page: pageNumber,
    pageSize,
    total: items.length,
    totalPages: items.length ? 1 : 0,
  }
}

export async function mountFinanceView(component: object, permissions: string[], path = '/') {
  const pinia = createPinia()
  setActivePinia(pinia)
  useAuthStore().setSession({
    user: { id: 1, username: 'finance_user', displayName: '财务用户', status: 'ENABLED' },
    menus: [],
    permissions,
  })
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/finance/receivables', name: 'finance-receivables', component },
      { path: '/finance/receivables/create', name: 'finance-receivable-create', component },
      { path: '/finance/receivables/:id', name: 'finance-receivable-detail', component },
      { path: '/finance/receivables/:id/edit', name: 'finance-receivable-edit', component },
      { path: '/finance/receivables/:id/receipts/create', name: 'finance-receipt-create', component },
      { path: '/finance/receipts', name: 'finance-receipts', component },
      { path: '/finance/receipts/:id', name: 'finance-receipt-detail', component },
      { path: '/finance/receipts/:id/edit', name: 'finance-receipt-edit', component },
      { path: '/finance/payables', name: 'finance-payables', component },
      { path: '/finance/payables/create', name: 'finance-payable-create', component },
      { path: '/finance/payables/:id', name: 'finance-payable-detail', component },
      { path: '/finance/payables/:id/edit', name: 'finance-payable-edit', component },
      { path: '/finance/payables/:id/payments/create', name: 'finance-payment-create', component },
      { path: '/finance/payments', name: 'finance-payments', component },
      { path: '/finance/payments/:id', name: 'finance-payment-detail', component },
      { path: '/finance/payments/:id/edit', name: 'finance-payment-edit', component },
      { path: '/finance/sales-invoices', name: 'finance-sales-invoices', component },
      { path: '/finance/sales-invoices/create', name: 'finance-sales-invoice-create', component },
      { path: '/finance/sales-invoices/:id', name: 'finance-sales-invoice-detail', component },
      { path: '/finance/sales-invoices/:id/edit', name: 'finance-sales-invoice-edit', component },
      { path: '/finance/purchase-invoices', name: 'finance-purchase-invoices', component },
      { path: '/finance/purchase-invoices/create', name: 'finance-purchase-invoice-create', component },
      { path: '/finance/purchase-invoices/:id', name: 'finance-purchase-invoice-detail', component },
      { path: '/finance/purchase-invoices/:id/edit', name: 'finance-purchase-invoice-edit', component },
      { path: '/finance/purchase-invoices/:id/matching', name: 'finance-purchase-invoice-matching', component },
      { path: '/finance/expenses', name: 'finance-expenses', component },
      { path: '/finance/expenses/create', name: 'finance-expense-create', component },
      { path: '/finance/expenses/:id', name: 'finance-expense-detail', component },
      { path: '/finance/expenses/:id/edit', name: 'finance-expense-edit', component },
      { path: '/finance/advance-receipts', name: 'finance-advance-receipts', component },
      { path: '/finance/advance-receipts/create', name: 'finance-advance-receipt-create', component },
      { path: '/finance/advance-receipts/:id', name: 'finance-advance-receipt-detail', component },
      { path: '/finance/advance-receipts/:id/edit', name: 'finance-advance-receipt-edit', component },
      { path: '/finance/prepayments', name: 'finance-prepayments', component },
      { path: '/finance/prepayments/create', name: 'finance-prepayment-create', component },
      { path: '/finance/prepayments/:id', name: 'finance-prepayment-detail', component },
      { path: '/finance/prepayments/:id/edit', name: 'finance-prepayment-edit', component },
      { path: '/finance/settlement-workbench', name: 'finance-settlement-workbench', component },
      { path: '/finance/settlement-workbench/allocations/:id', name: 'finance-settlement-allocation-detail', component },
      { path: '/finance/voucher-drafts', name: 'finance-voucher-drafts', component },
      { path: '/finance/voucher-drafts/:id', name: 'finance-voucher-draft-detail', component },
      { path: '/gl/vouchers/:id', name: 'gl-voucher-detail', component: { render: () => null } },
      { path: '/sales/orders/:id', name: 'sales-order-detail', component: { render: () => null } },
      { path: '/sales/shipments/:id', name: 'sales-shipment-detail', component: { render: () => null } },
      { path: '/procurement/orders/:id', name: 'procurement-order-detail', component: { render: () => null } },
      { path: '/procurement/receipts/:id', name: 'procurement-receipt-detail', component: { render: () => null } },
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

export function buttonsByText(wrapper: ReturnType<typeof mount>, text: string) {
  return wrapper.findAllComponents({ name: 'ElButton' }).filter((button) => button.text().trim() === text)
}

export async function setSelectValue(wrapper: ReturnType<typeof mount>, index: number, value: unknown) {
  const select = wrapper.findAllComponents({ name: 'ElSelect' })[index]
  select.vm.$emit('update:modelValue', value)
  await flushPromises()
}
