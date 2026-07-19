import { describe, expect, it, vi } from 'vitest'
import { AccountPermissionApiError } from './accountPermissionApi'
import {
  createBusinessPeriodCloseApi,
  type BusinessPeriodCloseActionPayload,
  type BusinessPeriodCloseCheckItem,
  type BusinessPeriodCloseReportCode,
  type BusinessPeriodCloseRunDetail,
  type BusinessPeriodCloseRunRecord,
  type BusinessPeriodCloseSnapshotInventoryRecord,
  type BusinessPeriodCloseSnapshotProjectCostRecord,
  type BusinessPeriodCloseSnapshotWipRecord,
} from './businessPeriodCloseApi'

type AssertTrue<T extends true> = T

function apiResponse<T>(data: T, status = 200) {
  return {
    ok: status >= 200 && status < 300,
    status,
    json: async () => ({
      success: true,
      code: 'OK',
      message: '成功',
      data,
      traceId: 'trace-period-close',
    }),
  } as Response
}

function apiFailure(status = 409) {
  return {
    ok: false,
    status,
    json: async () => ({
      success: false,
      code: 'PERIOD_CLOSE_SOURCE_CHANGED',
      message: '来源已变化，请重新检查后再关闭。',
      data: null,
      traceId: 'trace-period-close-conflict',
    }),
  } as Response
}

describe('业务月结 API', () => {
  const typeContract = {
    actionPayloadCarriesVersionFingerprintAndIdempotency: true as AssertTrue<
      BusinessPeriodCloseActionPayload extends {
        version: number
        idempotencyKey: string
        sourceFingerprint?: string | null
      } ? true : false
    >,
    workbenchDecimalsStayStringsOrNull: true as AssertTrue<
      BusinessPeriodCloseRunRecord['snapshotValueAmount'] extends string | null
        ? BusinessPeriodCloseRunDetail['snapshotValueAmount'] extends string | null
          ? true
          : false
        : false
    >,
    snapshotDecimalsStayStringsOrNull: true as AssertTrue<
      BusinessPeriodCloseSnapshotInventoryRecord['endingValue'] extends string | null
        ? BusinessPeriodCloseSnapshotWipRecord['wipAmount'] extends string | null
          ? BusinessPeriodCloseSnapshotProjectCostRecord['grossMarginRate'] extends string | null
            ? true
            : false
          : false
        : false
    >,
    reportsAreFrozenEightCodes: true as AssertTrue<
      BusinessPeriodCloseReportCode extends
        | 'OVERVIEW'
        | 'SALES_SUMMARY'
        | 'PROCUREMENT_SUMMARY'
        | 'INVENTORY_STOCK_FLOW'
        | 'PRODUCTION_EXECUTION'
        | 'COST_COLLECTION'
        | 'SETTLEMENT_SUMMARY'
        | 'EXCEPTIONS'
        ? true
        : false
    >,
    checksCarryRestrictedSourceState: true as AssertTrue<
      BusinessPeriodCloseCheckItem extends {
        amountVisible: boolean
        sourceVisible: boolean
        restrictedReason?: string | null
      } ? true : false
    >,
  }

  it('声明 030 前端 API 契约：十进制字符串、权限脱敏、八类报表和原子动作载荷', () => {
    expect(typeContract).toMatchObject({
      actionPayloadCarriesVersionFingerprintAndIdempotency: true,
      checksCarryRestrictedSourceState: true,
      reportsAreFrozenEightCodes: true,
      snapshotDecimalsStayStringsOrNull: true,
      workbenchDecimalsStayStringsOrNull: true,
    })
  })

  it('分页查询月结工作台时用真实后端 envelope 映射 currentRunId 并提交冻结筛选参数', async () => {
    const page = {
      items: [{
        periodId: 7,
        periodCode: '2026-07',
        periodName: '2026年07月',
        startDate: '2026-07-01',
        endDate: '2026-07-31',
        periodStatus: 'OPEN',
        status: 'READY',
        currentRunId: 11,
        currentRevisionNo: 1,
        latestCheckRunId: 21,
        blockingCount: 0,
        warningCount: 2,
        currentSnapshotId: null,
        version: 5,
        sourceFingerprint: 'fp-030',
        allowedActions: ['CLOSE'],
        actionDisabledReasons: {},
      }],
      total: 1,
      page: 1,
      pageSize: 10,
    }
    const fetcher = vi.fn().mockResolvedValueOnce(apiResponse(page))
    const api = createBusinessPeriodCloseApi({ baseUrl: '/erp', fetcher })

    await expect(api.runs.list({
      periodCode: '2026',
      startDate: '2026-07-01',
      endDate: '',
      closeStatus: 'READY',
      checkResult: 'WARNING',
      hasBlocking: false,
      page: 1,
      pageSize: 10,
    })).resolves.toMatchObject({
      items: [{
        runId: 11,
        periodCode: '2026-07',
        closeStatus: 'READY',
        latestCheckId: 21,
        snapshotId: null,
      }],
      page: 1,
      pageSize: 10,
    })

    expect(fetcher).toHaveBeenCalledWith(
      '/erp/api/admin/period-closes?periodCode=2026&startDate=2026-07-01&status=READY&checkResult=WARNING&hasBlocking=false&page=1&pageSize=10',
      {
        credentials: 'include',
        headers: { Accept: 'application/json' },
        method: 'GET',
      },
    )
  })

  it('业务期间摘要、运行详情和检查项分页使用真实后端字段并保护受限来源字段', async () => {
    const fetcher = vi.fn()
      .mockResolvedValueOnce(apiResponse({
        periodId: 7,
        periodCode: '2026-07',
        status: 'READY',
        currentRunId: 11,
        currentRevisionNo: 1,
        latestCheckRunId: 21,
        blockingCount: 0,
        warningCount: 2,
        allowedActions: ['CHECK'],
        actionDisabledReasons: {},
        history: [{ id: 11, revisionNo: 1, status: 'READY' }],
      }))
      .mockResolvedValueOnce(apiResponse({
        id: 11,
        periodId: 7,
        periodCode: '2026-07',
        periodName: '2026年07月',
        startDate: '2026-07-01',
        endDate: '2026-07-31',
        periodStatus: 'OPEN',
        status: 'READY',
        statusName: '可月结',
        revisionNo: 1,
        latestCheckRunId: 21,
        blockingCount: 0,
        warningCount: 2,
        amountVisible: false,
        sourceVisible: false,
        restrictedReason: '无权查看成本金额',
        version: 5,
        sourceFingerprint: 'fp-030',
        allowedActions: ['CLOSE'],
        actionDisabledReasons: {},
        historyVersions: [{ id: 11, revisionNo: 1, status: 'READY' }],
        auditSummary: [{ action: 'CHECK', operatorUsername: 'admin', reason: null, createdAt: '2026-07-31T22:00:00+08:00' }],
      }))
      .mockResolvedValueOnce(apiResponse({
        items: [{
          id: 31,
          checkRunId: 21,
          domain: 'INVENTORY',
          severity: 'BLOCKING',
          checkCode: 'INVENTORY_UNVALUED_SOURCE',
          title: '存在未估值库存来源',
          description: '受限来源摘要',
          objectType: 'INVENTORY_SOURCE',
          objectNo: null,
          suggestion: '完成库存估值后重新检查',
          sourceRouteJson: '{"path":"/inventory/movements","query":{"sourceNo":"INV-001"}}',
          amountVisible: false,
          sourceVisible: false,
          restrictedReason: '来源权限受限，仅显示脱敏摘要',
        }],
        total: 1,
        page: 1,
        pageSize: 10,
      }))
    const api = createBusinessPeriodCloseApi({ fetcher })

    await expect(api.periods.getSummary(7)).resolves.toMatchObject({ periodCode: '2026-07', currentRunId: 11 })
    await expect(api.runs.get(11)).resolves.toMatchObject({
      runId: 11,
      closeStatus: 'READY',
      snapshotValueAmount: null,
      amountVisible: false,
      sourceVisible: false,
      restrictedReason: '无权查看成本金额',
      historyVersions: [{ runId: 11, closeStatus: 'READY' }],
      auditSummary: [{ action: 'CHECK', operatorUsername: 'admin' }],
    })
    await expect(api.checks.items(11, 21, { page: 1, pageSize: 10 })).resolves.toMatchObject({
      items: [{
        severity: 'BLOCKING',
        objectNo: null,
        businessImpact: '受限来源摘要',
        sourceRoute: { path: '/inventory/movements', query: { sourceNo: 'INV-001' } },
        amountVisible: false,
        sourceVisible: false,
      }],
    })

    expect(fetcher.mock.calls.map((call) => call[0])).toEqual([
      '/api/admin/period-closes/periods/7',
      '/api/admin/period-closes/11',
      '/api/admin/period-closes/11/checks/21/items?page=1&pageSize=10',
    ])
  })

  it('检查、关闭和重开动作携带 CSRF、版本、幂等键、来源指纹和高风险原因', async () => {
    const fetcher = vi.fn()
      .mockResolvedValueOnce(apiResponse({ token: 'csrf-check', headerName: 'X-CSRF-TOKEN', parameterName: '_csrf' }))
      .mockResolvedValueOnce(apiResponse({ runId: 11, closeStatus: 'BLOCKED', version: 1 }))
      .mockResolvedValueOnce(apiResponse({ token: 'csrf-close', headerName: 'X-CSRF-TOKEN', parameterName: '_csrf' }))
      .mockResolvedValueOnce(apiResponse({ runId: 11, closeStatus: 'CLOSED', version: 2 }))
      .mockResolvedValueOnce(apiResponse({ token: 'csrf-reopen', headerName: 'X-CSRF-TOKEN', parameterName: '_csrf' }))
      .mockResolvedValueOnce(apiResponse({ runId: 11, closeStatus: 'REOPENED', version: 3 }))
    const api = createBusinessPeriodCloseApi({ fetcher })

    await api.checks.create({ periodId: 7, idempotencyKey: 'check-key' })
    await api.runs.close(11, {
      version: 2,
      sourceFingerprint: 'fp-030',
      warningAcknowledged: true,
      reason: '警告项已业务确认，生成 2026-07 月结快照',
      idempotencyKey: 'close-key',
    })
    await api.runs.reopen(11, {
      version: 3,
      reason: '补录已审批跨期反向业务',
      idempotencyKey: 'reopen-key',
    })

    expect(fetcher).toHaveBeenNthCalledWith(2, '/api/admin/period-closes/checks', expect.objectContaining({
      body: JSON.stringify({ periodId: 7, idempotencyKey: 'check-key' }),
      headers: expect.objectContaining({ 'X-CSRF-TOKEN': 'csrf-check' }),
      method: 'POST',
    }))
    expect(fetcher).toHaveBeenNthCalledWith(4, '/api/admin/period-closes/11/close', expect.objectContaining({
      body: JSON.stringify({
        version: 2,
        sourceFingerprint: 'fp-030',
        warningAcknowledged: true,
        reason: '警告项已业务确认，生成 2026-07 月结快照',
        idempotencyKey: 'close-key',
      }),
      headers: expect.objectContaining({ 'X-CSRF-TOKEN': 'csrf-close' }),
      method: 'POST',
    }))
    expect(fetcher).toHaveBeenNthCalledWith(6, '/api/admin/period-closes/11/reopen', expect.objectContaining({
      body: JSON.stringify({ version: 3, reason: '补录已审批跨期反向业务', idempotencyKey: 'reopen-key' }),
      headers: expect.objectContaining({ 'X-CSRF-TOKEN': 'csrf-reopen' }),
      method: 'POST',
    }))
  })

  it('快照库存、在制、项目成本和八类报表基线映射真实 DTO 且不把 null 金额改成 0', async () => {
    const fetcher = vi.fn()
      .mockResolvedValueOnce(apiResponse({
        snapshotId: 81,
        runId: 11,
        periodCode: '2026-07',
        startDate: '2026-07-01',
        endDate: '2026-07-31',
        revisionNo: 1,
        generatedAt: '2026-07-31T23:30:00+08:00',
        sourceCheckRunId: 21,
        sourceFingerprint: 'fp-030',
        isHistoricalRevision: false,
        partitions: [
          { code: 'INVENTORY', name: '库存快照', itemCount: 3, amountVisible: true, sourceVisible: true },
          { code: 'REPORTS', name: '经营报表基线', itemCount: 8, amountVisible: true, sourceVisible: true },
        ],
      }))
      .mockResolvedValueOnce(apiResponse({
        items: [{
          id: 801,
          materialCode: 'MAT-001',
          materialName: '铜件',
          warehouseName: '一号仓',
          endingQuantity: '12.000000',
          endingAmount: null,
          unitCost: null,
          inQuantity: '3.000000',
          outQuantity: '1.000000',
          amountVisible: false,
          sourceVisible: false,
          restrictedReason: '无权查看库存金额',
        }],
        total: 1,
        page: 1,
        pageSize: 10,
      }))
      .mockResolvedValueOnce(apiResponse({ items: [{ id: 901, projectNo: 'SP-001', workOrderNo: 'WO-001', productMaterialCode: 'MAT-001', productMaterialName: '铜件', status: 'IN_PROGRESS', wipQuantity: '2.000000', wipCost: '80.000000', amountVisible: true, sourceVisible: true }], total: 1, page: 1, pageSize: 10 }))
      .mockResolvedValueOnce(apiResponse({ items: [{ id: 1001, projectNo: 'SP-001', projectCostTotal: '838.000000', shipmentRevenue: '10000.000000', shipmentGrossMargin: '9162.000000', amountVisible: true, sourceVisible: true }], total: 1, page: 1, pageSize: 10 }))
      .mockResolvedValueOnce(apiResponse({
        reportCode: 'OVERVIEW',
        schemaVersion: 1,
        createdAt: '2026-07-31T23:30:00+08:00',
        sourceCount: 6,
        fingerprint: 'report-fp',
        amountVisible: false,
        sourceVisible: false,
        restrictedReason: '报表来源权限受限',
        resultJson: '{"revenue":null}',
      }))
    const api = createBusinessPeriodCloseApi({ fetcher })

    await expect(api.snapshots.get(11)).resolves.toMatchObject({
      snapshotId: 81,
      periodCode: '2026-07',
      partitions: [
        expect.objectContaining({ code: 'INVENTORY', recordCount: 3 }),
        expect.objectContaining({ code: 'REPORTS', recordCount: 8 }),
      ],
    })
    await expect(api.snapshots.inventory(11, { page: 1, pageSize: 10 })).resolves.toMatchObject({
      items: [{ endingValue: null, unitCost: null, inboundQuantity: '3.000000', outboundQuantity: '1.000000', restrictedReason: '无权查看库存金额' }],
    })
    await expect(api.snapshots.wip(11, { page: 1, pageSize: 10 })).resolves.toMatchObject({
      items: [{ materialCode: 'MAT-001', stage: 'IN_PROGRESS', wipAmount: '80.000000' }],
    })
    await expect(api.snapshots.projectCosts(11, { page: 1, pageSize: 10 })).resolves.toMatchObject({
      items: [{ totalCost: '838.000000', revenueAmount: '10000.000000', grossMarginAmount: '9162.000000' }],
    })
    await expect(api.snapshots.report(11, 'OVERVIEW')).resolves.toMatchObject({
      reportCode: 'OVERVIEW',
      reportName: '经营概览',
      generatedAt: '2026-07-31T23:30:00+08:00',
      sourceFingerprint: 'report-fp',
      result: { revenue: null },
    })

    expect(fetcher.mock.calls.map((call) => call[0])).toEqual([
      '/api/admin/period-closes/11/snapshot',
      '/api/admin/period-closes/11/snapshot/inventory?page=1&pageSize=10',
      '/api/admin/period-closes/11/snapshot/wip?page=1&pageSize=10',
      '/api/admin/period-closes/11/snapshot/project-costs?page=1&pageSize=10',
      '/api/admin/period-closes/11/snapshot/reports/OVERVIEW',
    ])
  })

  it('409 来源变化和幂等冲突抛出稳定错误对象供页面显示中文下一步', async () => {
    const fetcher = vi.fn()
      .mockResolvedValueOnce(apiResponse({ token: 'csrf-close', headerName: 'X-CSRF-TOKEN', parameterName: '_csrf' }))
      .mockResolvedValueOnce(apiFailure(409))
    const api = createBusinessPeriodCloseApi({ fetcher })

    const request = api.runs.close(11, {
      version: 2,
      sourceFingerprint: 'fp-old',
      warningAcknowledged: true,
      reason: '确认关闭',
      idempotencyKey: 'close-key',
    })

    await expect(request).rejects.toMatchObject({
      code: 'PERIOD_CLOSE_SOURCE_CHANGED',
      status: 409,
      message: '来源已变化，请重新检查后再关闭。',
      traceId: 'trace-period-close-conflict',
    })
    await expect(request).rejects.toBeInstanceOf(AccountPermissionApiError)
  })
})
