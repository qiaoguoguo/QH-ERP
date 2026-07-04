import ElementPlus from 'element-plus'
import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createMemoryHistory, createRouter } from 'vue-router'
import { useAuthStore } from '../../stores/authStore'

export function page<T>(items: T[] = []) {
  return {
    items,
    page: 1,
    pageSize: 20,
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
