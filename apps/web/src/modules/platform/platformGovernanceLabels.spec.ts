import { describe, expect, it } from 'vitest'
import {
  batchOperationItemStatusLabel,
  batchOperationStatusLabel,
  dataRepairCheckStageLabel,
  dataRepairCheckStatusLabel,
  dataRepairEventActionLabel,
  dataRepairRiskLabel,
  dataRepairStatusLabel,
  dataRepairStatusTagType,
  deliveryAssetActionLabel,
  deliveryAssetStatusLabel,
  demoDataStatusLabel,
  governanceErrorLabel,
  historyImportStatusLabel,
  historyImportStatusTagType,
} from './platformGovernanceLabels'

describe('平台治理状态文案', () => {
  it('数据修复状态、风险、检查和事件动作不裸露英文编码', () => {
    expect(dataRepairStatusLabel('PENDING_APPROVAL')).toBe('待审批')
    expect(dataRepairStatusLabel('UNKNOWN_STATUS')).toBe('未知状态')
    expect(dataRepairStatusTagType('CANCELLED')).toBe('info')
    expect(dataRepairRiskLabel('UNKNOWN_RISK')).toBe('未知风险')
    expect(dataRepairCheckStageLabel('PRECHECK')).toBe('预检查')
    expect(dataRepairCheckStageLabel('UNKNOWN_STAGE')).toBe('未知阶段')
    expect(dataRepairCheckStatusLabel('PASSED')).toBe('通过')
    expect(dataRepairCheckStatusLabel('UNKNOWN_STATUS')).toBe('未知状态')
    expect(dataRepairEventActionLabel('SUBMIT')).toBe('提交')
    expect(dataRepairEventActionLabel('UNKNOWN_ACTION')).toBe('未知动作')
  })

  it('历史导入、批量操作和交付资料状态统一中文兜底', () => {
    expect(historyImportStatusLabel('READY_TO_COMMIT')).toBe('待确认')
    expect(historyImportStatusLabel('UNKNOWN_STATUS')).toBe('未知状态')
    expect(historyImportStatusTagType('CANCELLED')).toBe('info')
    expect(historyImportStatusTagType('EXPIRED')).toBe('warning')
    expect(batchOperationStatusLabel('PRECHECKED')).toBe('预检通过')
    expect(batchOperationStatusLabel('UNKNOWN_STATUS')).toBe('未知状态')
    expect(batchOperationItemStatusLabel('READY')).toBe('可执行')
    expect(batchOperationItemStatusLabel('UNKNOWN_STATUS')).toBe('未知状态')
    expect(deliveryAssetStatusLabel('ACTIVE')).toBe('启用')
    expect(deliveryAssetStatusLabel('UNKNOWN_STATUS')).toBe('未知状态')
    expect(deliveryAssetActionLabel('STATUS_CHANGE')).toBe('状态变更')
    expect(deliveryAssetActionLabel('UNKNOWN_ACTION')).toBe('未知动作')
    expect(demoDataStatusLabel('NOT_VERIFIED')).toBe('未验证')
    expect(demoDataStatusLabel('UNKNOWN_STATUS')).toBe('未知状态')
    expect(governanceErrorLabel('UNKNOWN_CODE')).toBe('未知错误')
  })
})
