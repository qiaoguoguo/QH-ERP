import ElementPlus from 'element-plus'
import { mount } from '@vue/test-utils'
// @ts-expect-error 当前前端 tsconfig 不包含 Node 类型，本测试只读取本地组件源码。
import { readFileSync } from 'node:fs'
// @ts-expect-error 当前前端 tsconfig 不包含 Node 类型，本测试只解析本地文件路径。
import { fileURLToPath } from 'node:url'
// @ts-expect-error 当前前端 tsconfig 不包含 Node 类型，本测试只解析本地文件路径。
import { dirname, resolve } from 'node:path'
import { describe, expect, it } from 'vitest'
import type { SalesProjectOperation } from '../../../shared/api/salesProjectApi'
import SalesProjectOperationsPanel from './SalesProjectOperationsPanel.vue'

const currentDir = dirname(fileURLToPath(import.meta.url))
const componentSource = readFileSync(resolve(currentDir, 'SalesProjectOperationsPanel.vue'), 'utf8')

const operations: SalesProjectOperation[] = [{
  action: 'SALES_PROJECT_UPDATE',
  targetType: 'SALES_PROJECT',
  targetId: 12,
  targetSummary: '更新目标成本',
  operatorUsername: 'admin',
  createdAt: '2026-07-12T10:00:00+08:00',
}, {
  action: 'SALES_PROJECT_CONTRACT_ACTIVATE',
  targetType: 'SALES_PROJECT_CONTRACT',
  targetId: 55,
  targetSummary: '生效',
  operatorUsername: 'sales_manager',
  createdAt: '2026-07-12T11:30:00+08:00',
}, {
  action: 'SALES_ORDER_PROJECT_UNLINK',
  targetType: 'SALES_PROJECT',
  targetId: 99,
  targetSummary: '销售订单 SO-202607-00000000000000000000000000000001 解除项目合同关联',
  operatorUsername: 'sales',
  createdAt: '2026-07-12T12:00:00+08:00',
}, {
  action: 'SALES_PROJECT_CANCEL',
  targetType: 'SALES_PROJECT',
  targetId: 12,
  targetSummary: '取消原因：客户取消',
  operatorUsername: 'admin',
  createdAt: '2026-07-12T12:30:00+08:00',
}, {
  action: 'INTERNAL_UNKNOWN_ACTION',
  targetType: 'SECRET_TARGET_TYPE',
  targetId: 9988776655,
  targetSummary: '未知动作摘要 ABC-000000000000000000000000000000000000000000000000，保持原文完整展示',
  operatorUsername: 'ops',
  createdAt: '2026-07-12T13:00:00+08:00',
}]

function mountPanel(records: SalesProjectOperation[]) {
  return mount(SalesProjectOperationsPanel, {
    props: {
      operations: records,
    },
    global: {
      plugins: [ElementPlus],
    },
  })
}

describe('销售项目操作记录面板', () => {
  it('按接口顺序展示中文流程时间线，不暴露技术动作、技术对象或内部 ID', () => {
    const wrapper = mountPanel(operations)
    const items = wrapper.findAll('[data-test="project-operation-item"]')

    expect(wrapper.find('[data-test="project-operation-timeline"]').classes()).toContain('project-operation-timeline')
    expect(wrapper.find('.project-operation-card').exists()).toBe(false)
    expect(items).toHaveLength(5)
    expect(items[0].find('[data-test="project-operation-object"]').text()).toBe('项目')
    expect(items[0].find('[data-test="project-operation-action"]').text()).toBe('更新')
    expect(items[0].find('[data-test="project-operation-summary"]').text()).toBe('更新目标成本')
    expect(items[0].find('[data-test="project-operation-meta"]').text()).toContain('admin')
    expect(items[0].find('[data-test="project-operation-meta"]').text()).toContain('2026-07-12 10:00')
    expect(items[2].find('[data-test="project-operation-object"]').text()).toBe('销售订单')
    expect(items[2].find('[data-test="project-operation-action"]').text()).toBe('解除项目合同')
    expect(wrapper.text()).not.toContain('SALES_PROJECT_UPDATE')
    expect(wrapper.text()).not.toContain('SALES_PROJECT_CONTRACT')
    expect(wrapper.text()).not.toContain('SALES_ORDER_PROJECT_UNLINK')
    expect(wrapper.text()).not.toContain('SECRET_TARGET_TYPE')
    expect(wrapper.text()).not.toContain('INTERNAL_UNKNOWN_ACTION')
    expect(wrapper.text()).not.toContain('#12')
    expect(wrapper.text()).not.toContain('#55')
    expect(wrapper.text()).not.toContain('#9988776655')
  })

  it('空操作记录显示空态，不渲染时间线', () => {
    const wrapper = mountPanel([])

    expect(wrapper.text()).toContain('暂无操作记录')
    expect(wrapper.find('[data-test="project-operation-timeline"]').exists()).toBe(false)
  })

  it('摘要与中文动作完全相同时省略重复摘要', () => {
    const wrapper = mountPanel([operations[1]])
    const item = wrapper.find('[data-test="project-operation-item"]')

    expect(item.classes()).toContain('project-operation-item')
    expect(item.find('[data-test="project-operation-object"]').text()).toBe('项目合同')
    expect(item.find('[data-test="project-operation-action"]').text()).toBe('生效')
    expect(item.find('[data-test="project-operation-summary"]').exists()).toBe(false)
  })

  it('项目合同摘要去重允许合同前缀等价，但保留原因、编号和变更内容', () => {
    const wrapper = mountPanel([{
      ...operations[1],
      targetSummary: '合同｜生效',
    }, {
      ...operations[1],
      targetSummary: '合同生效原因：客户确认',
      createdAt: '2026-07-12T11:40:00+08:00',
    }, {
      ...operations[1],
      targetSummary: '合同 SC-202607-001 生效',
      createdAt: '2026-07-12T11:50:00+08:00',
    }])
    const items = wrapper.findAll('[data-test="project-operation-item"]')

    expect(items[0].find('[data-test="project-operation-summary"]').exists()).toBe(false)
    expect(items[1].find('[data-test="project-operation-summary"]').text()).toBe('合同生效原因：客户确认')
    expect(items[2].find('[data-test="project-operation-summary"]').text()).toBe('合同 SC-202607-001 生效')
  })

  it('未知动作和未知对象降级展示，长摘要保留原文并放入可换行容器', () => {
    const wrapper = mountPanel([operations[4]])
    const item = wrapper.find('[data-test="project-operation-item"]')

    expect(item.classes()).toContain('project-operation-item')
    expect(item.find('[data-test="project-operation-object"]').text()).toBe('业务对象')
    expect(item.find('[data-test="project-operation-action"]').text()).toBe('其他操作')
    expect(item.find('[data-test="project-operation-summary"]').classes()).toContain('project-operation-summary')
    expect(item.find('[data-test="project-operation-summary"]').text()).toBe(operations[4].targetSummary)
    expect(item.text()).not.toContain('SECRET_TARGET_TYPE')
    expect(item.text()).not.toContain('INTERNAL_UNKNOWN_ACTION')
    expect(item.text()).not.toContain('#9988776655')
  })

  it('格式化销售订单项目合同关联旧摘要，保留订单号、项目号和合同号', () => {
    const wrapper = mountPanel([{
      ...operations[2],
      action: 'SALES_ORDER_PROJECT_LINK',
      targetSummary: '订单 SO-202607-001 项目合同关联 未关联 -> SP-202607-001/SC-202607-001',
    }, {
      ...operations[2],
      action: 'SALES_ORDER_PROJECT_LINK',
      targetSummary: '订单 SO-202607-001 项目合同关联 SP-202607-001/SC-202607-001 -> SP-202607-002/SC-202607-002',
      createdAt: '2026-07-12T12:10:00+08:00',
    }, {
      ...operations[2],
      action: 'SALES_ORDER_PROJECT_UNLINK',
      targetSummary: '订单 SO-202607-001 项目合同关联 SP-202607-002/SC-202607-002 -> 未关联',
      createdAt: '2026-07-12T12:20:00+08:00',
    }])
    const summaries = wrapper.findAll('[data-test="project-operation-summary"]').map((item) => item.text())

    expect(summaries[0]).toBe('订单 SO-202607-001：原关联：无；新关联：项目 SP-202607-001，合同 SC-202607-001')
    expect(summaries[1]).toBe('订单 SO-202607-001：原关联：项目 SP-202607-001，合同 SC-202607-001；新关联：项目 SP-202607-002，合同 SC-202607-002')
    expect(summaries[2]).toBe('订单 SO-202607-001：原关联：项目 SP-202607-002，合同 SC-202607-002；新关联：无')
    expect(wrapper.text()).not.toContain('未关联 ->')
    expect(wrapper.text()).not.toContain('SP-202607-001/SC-202607-001 -> SP-202607-002/SC-202607-002')
    expect(wrapper.text()).not.toContain('-> 未关联')
  })

  it('销售订单项目合同关联摘要脱敏、空值或异常格式保持原文', () => {
    const wrapper = mountPanel([{
      ...operations[2],
      targetSummary: '订单 SO-202607-001 项目合同关联已变更',
    }, {
      ...operations[2],
      targetSummary: '',
      createdAt: '2026-07-12T12:10:00+08:00',
    }, {
      ...operations[2],
      targetSummary: '订单 SO-202607-001 项目合同关联 SP-202607-001 -> 未关联',
      createdAt: '2026-07-12T12:20:00+08:00',
    }])
    const items = wrapper.findAll('[data-test="project-operation-item"]')

    expect(items[0].find('[data-test="project-operation-summary"]').text()).toBe('订单 SO-202607-001 项目合同关联已变更')
    expect(items[1].find('[data-test="project-operation-summary"]').exists()).toBe(false)
    expect(items[2].find('[data-test="project-operation-summary"]').text()).toBe('订单 SO-202607-001 项目合同关联 SP-202607-001 -> 未关联')
  })

  it('按动作类型设置时间线节点辅助色', () => {
    const wrapper = mountPanel(operations)
    const timelineItems = wrapper.findAllComponents({ name: 'ElTimelineItem' })

    expect((timelineItems[0].props() as Record<string, unknown>).type).toBe('primary')
    expect((timelineItems[1].props() as Record<string, unknown>).type).toBe('success')
    expect((timelineItems[2].props() as Record<string, unknown>).type).toBe('warning')
    expect((timelineItems[3].props() as Record<string, unknown>).type).toBe('danger')
    expect((timelineItems[4].props() as Record<string, unknown>).type).toBe('info')
  })

  it('元信息使用 stone 色并保持 12px', () => {
    const wrapper = mountPanel([operations[0]])
    const meta = wrapper.find('[data-test="project-operation-meta"]')

    expect(meta.text()).toBe('2026-07-12 10:00 · admin')
    expect(meta.classes()).toContain('project-operation-meta')
    expect(componentSource).toMatch(/\.project-operation-meta\s*{[^}]*color:\s*var\(--qherp-stone\);[^}]*font-size:\s*12px;/s)
  })
})
