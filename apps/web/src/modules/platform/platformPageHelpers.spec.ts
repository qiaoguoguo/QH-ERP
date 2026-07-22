import { describe, expect, it } from 'vitest'
import {
  approvalActionLabel,
  approvalStatusLabel,
  approvalStatusTagType,
  documentTaskStageLabel,
  documentTaskStatusLabel,
  documentTaskStatusTagType,
  documentTaskTypeLabel,
} from './platformPageHelpers'

describe('平台页面通用文案', () => {
  it('审批状态和动作未知值不裸露原始编码', () => {
    expect(approvalStatusLabel('APPROVED')).toBe('已通过')
    expect(approvalStatusLabel('UNKNOWN_STATUS')).toBe('未知状态')
    expect(approvalStatusTagType('CANCELLED')).toBe('info')
    expect(approvalActionLabel('SUBMIT')).toBe('提交')
    expect(approvalActionLabel('UNKNOWN_ACTION')).toBe('未知动作')
  })

  it('文档任务类型、阶段和状态未知值提供中文兜底', () => {
    expect(documentTaskTypeLabel('MATERIAL_IMPORT')).toBe('物料导入')
    expect(documentTaskTypeLabel('UNKNOWN_TASK')).toBe('未知任务')
    expect(documentTaskStageLabel('VALIDATE')).toBe('校验')
    expect(documentTaskStageLabel('UNKNOWN_STAGE')).toBe('未知阶段')
    expect(documentTaskStatusLabel('READY_TO_COMMIT')).toBe('待确认')
    expect(documentTaskStatusLabel('UNKNOWN_STATUS')).toBe('未知状态')
    expect(documentTaskStatusTagType('CANCELLED')).toBe('info')
    expect(documentTaskStatusTagType('EXPIRED')).toBe('warning')
  })
})
