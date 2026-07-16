import type { VueWrapper } from '@vue/test-utils'
import type { ComponentPublicInstance } from 'vue'
import { expect } from 'vitest'

type MountedPage = VueWrapper<unknown>
type ComponentWrapper = VueWrapper<ComponentPublicInstance>

function componentCount(wrapper: MountedPage, name: string): number {
  return wrapper.findAllComponents({ name }).length
}

export function expectStandardListPage(wrapper: MountedPage) {
  expect(wrapper.find('.page-heading').exists()).toBe(true)
  expect(wrapper.find('.query-card').exists()).toBe(true)
  expect(wrapper.find('.query-card .query-form').exists()).toBe(true)
  expect(wrapper.find('.table-scroll').exists()).toBe(true)
  expect(componentCount(wrapper, 'ElTable')).toBeGreaterThan(0)

  const table = wrapper.findComponent({ name: 'ElTable' })
  expect(table.props('emptyText')).toMatch(/暂无|加载中|无权限|加载失败/)
  expect(wrapper.findComponent({ name: 'ElEmpty' }).exists()).toBe(false)

  const pagination = wrapper.findComponent({ name: 'ElPagination' })
  expect(pagination.exists()).toBe(true)
  expect(pagination.props('pageSizes')).toEqual([10, 20, 50, 100])
  expect(pagination.props('pageSize')).toBe(10)
}

export function expectNoBareIdFilters(wrapper: MountedPage, labels: string[]) {
  const queryCard = wrapper.find('.query-card')
  expect(queryCard.exists()).toBe(true)
  const queryText = queryCard.text()
  labels.forEach((label) => {
    expect(queryText).not.toContain(label)
  })
  const rawIdInputs = queryCard
    .findAllComponents({ name: 'ElInput' })
    .filter((input: ComponentWrapper) => {
      const props = input.props() as Record<string, unknown>
      return /(^|[^\u4e00-\u9fa5])ID($|[^\u4e00-\u9fa5])|裸 ID|原始 ID/.test(String(props.placeholder ?? ''))
    })
  expect(rawIdInputs).toHaveLength(0)
}

export function expectStandardFormPage(wrapper: MountedPage, dateFieldNames: string[] = []) {
  expect(wrapper.find('.page-heading').exists()).toBe(true)
  expect(wrapper.find('.section-block').exists()).toBe(true)

  dateFieldNames.forEach((fieldName) => {
    const datePicker = wrapper
      .findAllComponents({ name: 'ElDatePicker' })
      .find((candidate) => candidate.props('name') === fieldName)
    expect(datePicker?.exists()).toBe(true)
    expect(datePicker?.props('valueOnClear')).toBe('')
    expect(wrapper.find(`input[name="${fieldName}"][type="date"]`).exists()).toBe(false)
  })
}

export function expectStandardDetailPage(wrapper: MountedPage) {
  expect(wrapper.find('.page-heading').exists()).toBe(true)
  expect(wrapper.find('.section-block').exists()).toBe(true)
  expect(wrapper.find('.table-scroll').exists()).toBe(true)
  expect(componentCount(wrapper, 'ElTable')).toBeGreaterThan(0)
}

export function expectDrawerWideTableGoverned(wrapper: MountedPage) {
  const drawer = wrapper.findComponent({ name: 'ElDrawer' })
  expect(drawer.exists()).toBe(true)
  expect(wrapper.find('.table-scroll').exists()).toBe(true)
  expect(wrapper.find('.table-scroll').findComponent({ name: 'ElTable' }).exists()).toBe(true)
}
