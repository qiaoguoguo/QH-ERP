import { describe, expect, it } from 'vitest'
import {
  approvalStatusLabel,
  deliveryPlanStatusLabel,
  effectiveDemandExcludedReasonLabel,
  effectiveDemandStatusLabel,
  orderChangeStatusLabel,
  quoteStatusLabel,
} from './salesFulfillmentPageHelpers'

describe('销售履约页面 helper', () => {
  it('报价、审批、交付计划、订单变更和有效需求状态均有中文兜底', () => {
    expect(quoteStatusLabel('APPROVED')).toBe('已批准')
    expect(quoteStatusLabel('REVIEW_REQUIRED')).toBe('未知状态')
    expect(approvalStatusLabel('REJECTED')).toBe('已驳回')
    expect(approvalStatusLabel('REVIEW_REQUIRED')).toBe('未知状态')
    expect(deliveryPlanStatusLabel('PARTIALLY_SHIPPED')).toBe('部分出库')
    expect(deliveryPlanStatusLabel('REVIEW_REQUIRED')).toBe('未知状态')
    expect(orderChangeStatusLabel('APPLIED')).toBe('已应用')
    expect(orderChangeStatusLabel('REVIEW_REQUIRED')).toBe('未知状态')
    expect(effectiveDemandStatusLabel('OPEN')).toBe('待处理')
    expect(effectiveDemandStatusLabel('LEGACY_UNKNOWN')).toBe('未知状态')
  })

  it('有效需求排除原因优先使用后端中文动态原因，未知原因不裸露原始编码', () => {
    expect(effectiveDemandExcludedReasonLabel('需求已关闭', 'CLOSED_ORDER')).toBe('需求已关闭')
    expect(effectiveDemandExcludedReasonLabel('CLOSED_ORDER', 'CLOSED_ORDER')).toBe('未知原因')
    expect(effectiveDemandExcludedReasonLabel(null, null)).toBe('未返回')
  })
})
