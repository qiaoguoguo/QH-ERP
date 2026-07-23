import { describe, expect, it } from 'vitest'
import type { PageResult } from '../../shared/api/accountPermissionApi'
import type { ApprovalTaskRecord, DocumentTaskRecord } from '../../shared/api/documentPlatformApi'
import {
  approvalTitle,
  clampProgress,
  documentTaskRoute,
  exceptionSeverityText,
  exceptionTypeText,
  formatWorkbenchDateTime,
  formatWorkbenchMoney,
  formatWorkbenchNumber,
  mergeTaskPages,
  taskDisplayName,
  taskStatusText,
} from './workbenchPageHelpers'

function documentTask(overrides: Partial<DocumentTaskRecord> = {}): DocumentTaskRecord {
  return {
    id: 1,
    taskNo: 'TASK-001',
    taskType: 'MATERIAL_IMPORT',
    direction: 'IMPORT',
    stage: 'VALIDATE',
    status: 'RUNNING',
    version: 1,
    ...overrides,
  }
}

function taskPage(items: DocumentTaskRecord[], total = items.length): PageResult<DocumentTaskRecord> {
  return {
    items,
    page: 1,
    pageSize: 10,
    total,
  }
}

function approvalTask(overrides: Partial<ApprovalTaskRecord> = {}): ApprovalTaskRecord {
  return {
    id: 1,
    sceneCode: 'SALES_PROJECT_CONTRACT_ACTIVATION',
    objectType: 'SALES_PROJECT_CONTRACT',
    objectId: 10,
    status: 'PENDING',
    availableActions: [],
    version: 1,
    ...overrides,
  }
}

describe('工作台页面辅助函数', () => {
  it('稳定格式化金额、数量、日期时间和进度', () => {
    expect(formatWorkbenchMoney('1286400.00')).toBe('¥1,286,400.00')
    expect(formatWorkbenchMoney(null)).toBe('—')
    expect(formatWorkbenchMoney('not-a-number')).toBe('—')
    expect(formatWorkbenchNumber('386.000', '件')).toBe('386 件')
    expect(formatWorkbenchNumber(undefined)).toBe('—')
    expect(formatWorkbenchDateTime('2026-07-23T10:28:00+08:00')).toBe('2026-07-23 10:28')
    expect(formatWorkbenchDateTime(null)).toBe('—')
    expect(clampProgress(130)).toBe(100)
    expect(clampProgress(-1)).toBe(0)
    expect(clampProgress(undefined)).toBeNull()
  })

  it('将任务和异常枚举转换为中文且未知值不暴露英文编码', () => {
    expect(taskStatusText('RUNNING')).toBe('执行中')
    expect(taskStatusText('VALIDATION_FAILED')).toBe('校验失败')
    expect(taskStatusText('FUTURE_STATUS')).toBe('未知任务状态')
    expect(taskDisplayName(documentTask())).toBe('物料导入')
    const futureTask = {
      ...documentTask(),
      taskType: 'FUTURE_TASK',
    } as unknown as DocumentTaskRecord
    expect(taskDisplayName(futureTask)).toBe('未知任务')
    expect(exceptionTypeText('INVENTORY_SHORTAGE')).toBe('库存不足')
    expect(exceptionTypeText('FUTURE_EXCEPTION')).toBe('未知异常类型')
    expect(exceptionSeverityText('CRITICAL')).toBe('严重')
    expect(exceptionSeverityText('FUTURE_SEVERITY')).toBe('未知严重程度')
  })

  it('汇总不同状态任务总数并按最近业务时间倒序截取', () => {
    const olderTask = documentTask({
      id: 1,
      createdAt: '2026-07-23T08:00:00+08:00',
    })
    const newerTask = documentTask({
      id: 2,
      createdAt: '2026-07-23T07:00:00+08:00',
      completedAt: '2026-07-23T10:00:00+08:00',
    })

    expect(mergeTaskPages([
      taskPage([olderTask], 4),
      taskPage([newerTask], 2),
    ], 1)).toEqual({
      total: 6,
      items: [newerTask],
    })
  })

  it('为缺省业务对象生成中文标题并构造保留工作台来源的任务路由', () => {
    expect(approvalTitle(approvalTask({ objectName: '主合同', objectNo: 'SC-001' }))).toBe('主合同')
    expect(approvalTitle(approvalTask({ objectNo: 'SC-001' }))).toBe('SC-001')
    expect(approvalTitle(approvalTask())).toBe('审批事项')
    expect(documentTaskRoute(91, 'FAILED'))
      .toBe('/platform/document-tasks?taskId=91&status=FAILED&returnTo=%2F')
  })
})
