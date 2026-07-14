import { describe, expect, it, vi } from 'vitest'
import { AccountPermissionApiError } from './accountPermissionApi'
import {
  createInventoryApi,
  type InventoryDocumentPayload,
  type InventoryBatchSummaryRecord,
  type InventoryTrackingAllocationPayload,
  type InventoryBalanceRecord,
  type InventoryMovementRecord,
  type InventoryMovementType,
  type InventorySerialSummaryRecord,
  type InventoryReservationStatus,
  type InventoryReservationType,
  type InventoryTrackingMethod,
  type InventoryControlledDocumentActionPayload,
  type InventoryCostLayerRecord,
  type InventoryOwnershipConversionPayload,
  type InventoryStocktakeLinePayload,
  type InventoryValuationAdjustmentPayload,
  type InventoryWarehouseTransferPayload,
} from './inventoryApi'

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
    }),
  }
}

function apiFailure(status = 409) {
  return {
    ok: false,
    status,
    json: async () => ({
      success: false,
      code: 'INVENTORY_STOCK_NOT_ENOUGH',
      message: '库存不足，调减后库存不能小于 0',
      data: null,
      traceId: 'trace-inventory',
    }),
  }
}

describe('库存 API', () => {
  const inventoryBalanceTypeContract = {
    hasNetRequirementShortageQuantity: true as AssertTrue<
      'netRequirementShortageQuantity' extends keyof InventoryBalanceRecord ? true : false
    >,
    doesNotExposeLegacyNetRequirementQuantity: true as AssertTrue<
      'netRequirementQuantity' extends keyof InventoryBalanceRecord ? false : true
    >,
    movementHasTargetDocumentNo: true as AssertTrue<
      'targetDocumentNo' extends keyof InventoryMovementRecord ? true : false
    >,
    documentLineAcceptsTrackingAllocations: true as AssertTrue<
      InventoryDocumentPayload['lines'][number] extends {
        trackingAllocations?: InventoryTrackingAllocationPayload[]
      } ? true : false
    >,
    batchCandidateHasConsumableFields: true as AssertTrue<
      InventoryBatchSummaryRecord extends {
        warehouseId?: unknown
        warehouseName?: unknown
        qualityStatus?: unknown
        qualityStatusName?: unknown
        stockStatus?: unknown
        stockStatusName?: unknown
        selectable?: unknown
        disabledReasonCode?: unknown
        disabledReason?: unknown
      } ? true : false
    >,
    batchCandidateQualityStatusAllowsNull: true as AssertTrue<
      null extends InventoryBatchSummaryRecord['qualityStatus'] ? true : false
    >,
    serialCandidateHasConsumableFields: true as AssertTrue<
      InventorySerialSummaryRecord extends {
        availableQuantity?: unknown
        selectable?: unknown
        disabledReasonCode?: unknown
        disabledReason?: unknown
      } ? true : false
    >,
    balanceHasValuationFields: true as AssertTrue<
      InventoryBalanceRecord extends {
        ownershipType?: unknown
        projectId?: unknown
        costVisible?: unknown
        valuationState?: unknown
        inventoryAmount?: unknown
        averageUnitCost?: unknown
        costLayerCount?: unknown
      } ? true : false
    >,
    movementHasValuationFields: true as AssertTrue<
      InventoryMovementRecord extends {
        ownershipType?: unknown
        projectId?: unknown
        valuationMethod?: unknown
        unitCost?: unknown
        movementAmount?: unknown
        valueFlowId?: unknown
        costLayerId?: unknown
      } ? true : false
    >,
    costLayerUsesDecimalStrings: true as AssertTrue<
      InventoryCostLayerRecord extends {
        originalQuantity: string
        originalAmount?: string | null
        remainingQuantity: string
        remainingAmount?: string | null
      } ? true : false
    >,
    controlledActionsUseVersionAndIdempotency: true as AssertTrue<
      InventoryControlledDocumentActionPayload extends {
        version: number
        idempotencyKey: string
      } ? true : false
    >,
  }

  it('库存余额类型使用非负净需求缺口字段', () => {
    expect(inventoryBalanceTypeContract).toMatchObject({
      hasNetRequirementShortageQuantity: true,
      doesNotExposeLegacyNetRequirementQuantity: true,
      movementHasTargetDocumentNo: true,
      documentLineAcceptsTrackingAllocations: true,
      batchCandidateHasConsumableFields: true,
      batchCandidateQualityStatusAllowsNull: true,
      serialCandidateHasConsumableFields: true,
      balanceHasValuationFields: true,
      movementHasValuationFields: true,
      costLayerUsesDecimalStrings: true,
      controlledActionsUseVersionAndIdempotency: true,
    })
  })

  it('按查询条件分页获取库存余额', async () => {
    const fetcher = vi.fn().mockResolvedValueOnce(apiResponse({ items: [], total: 0, page: 2, pageSize: 50 }))
    const api = createInventoryApi({ fetcher })

    await api.balances.list({
      keyword: '原材料',
      warehouseId: 1,
      materialId: 2,
      materialType: 'RAW_MATERIAL',
      trackingMethod: 'BATCH',
      batchNo: 'B-20260711-001',
      serialNo: 'S-IGNORED',
      onlyPositive: true,
      page: 2,
      pageSize: 50,
    })

    expect(fetcher).toHaveBeenCalledWith(
      '/api/admin/inventory/balances?keyword=%E5%8E%9F%E6%9D%90%E6%96%99&warehouseId=1&materialId=2&materialType=RAW_MATERIAL&trackingMethod=BATCH&batchNo=B-20260711-001&serialNo=S-IGNORED&onlyPositive=true&page=2&pageSize=50',
      {
        credentials: 'include',
        headers: { Accept: 'application/json' },
        method: 'GET',
      },
    )
  })

  it('库存余额和流水查询支持所有权、项目、估值状态和成本层参数', async () => {
    const fetcher = vi
      .fn()
      .mockResolvedValueOnce(apiResponse({ items: [], total: 0, page: 1, pageSize: 20 }))
      .mockResolvedValueOnce(apiResponse({ items: [], total: 0, page: 1, pageSize: 20 }))
    const api = createInventoryApi({ fetcher })

    await api.balances.list({
      ownershipType: 'PROJECT',
      projectId: 501,
      valuationState: 'LEGACY_UNVALUED',
      includeZero: true,
      page: 1,
      pageSize: 20,
    })
    await api.movements.list({
      ownershipType: 'PROJECT',
      projectId: 501,
      valuationMethod: 'PROJECT_ACTUAL_LAYER',
      costLayerId: 9001,
      page: 1,
      pageSize: 20,
    })

    expect(fetcher).toHaveBeenNthCalledWith(
      1,
      '/api/admin/inventory/balances?ownershipType=PROJECT&projectId=501&valuationState=LEGACY_UNVALUED&includeZero=true&page=1&pageSize=20',
      expect.objectContaining({ method: 'GET' }),
    )
    expect(fetcher).toHaveBeenNthCalledWith(
      2,
      '/api/admin/inventory/movements?ownershipType=PROJECT&projectId=501&valuationMethod=PROJECT_ACTUAL_LAYER&costLayerId=9001&page=1&pageSize=20',
      expect.objectContaining({ method: 'GET' }),
    )
  })

  it('库存余额查询支持质量状态和零数量质量状态行参数', async () => {
    const fetcher = vi.fn().mockResolvedValueOnce(apiResponse({ items: [], total: 0, page: 1, pageSize: 20 }))
    const api = createInventoryApi({ fetcher })

    await api.balances.list({
      keyword: '钢板',
      qualityStatus: 'QUALIFIED',
      includeZeroQualityStatuses: true,
      page: 1,
      pageSize: 20,
    })

    expect(fetcher).toHaveBeenCalledWith(
      '/api/admin/inventory/balances?keyword=%E9%92%A2%E6%9D%BF&qualityStatus=QUALIFIED&includeZeroQualityStatuses=true&page=1&pageSize=20',
      {
        credentials: 'include',
        headers: { Accept: 'application/json' },
        method: 'GET',
      },
    )
  })

  it('按查询条件分页获取库存占用预留并按标识获取详情', async () => {
    const fetcher = vi
      .fn()
      .mockResolvedValueOnce(apiResponse({ items: [], total: 0, page: 1, pageSize: 20 }))
      .mockResolvedValueOnce(apiResponse({ id: 9, reservationNo: 'IR-20260711-001' }))
    const api = createInventoryApi({ fetcher })
    const reservationType: InventoryReservationType = 'RESERVATION'
    const status: InventoryReservationStatus = 'ACTIVE'

    await api.reservations.list({
      keyword: 'SO-20260711',
      warehouseId: 1,
      materialId: 2,
      reservationType,
      status,
      sourceType: 'SALES_ORDER',
      sourceId: 9,
      sourceLineId: 90,
      businessDateFrom: '2026-07-01',
      businessDateTo: '2026-07-11',
      page: 1,
      pageSize: 20,
    })
    await api.reservations.get(9)

    expect(fetcher).toHaveBeenNthCalledWith(
      1,
      '/api/admin/inventory/reservations?keyword=SO-20260711&warehouseId=1&materialId=2&reservationType=RESERVATION&status=ACTIVE&sourceType=SALES_ORDER&sourceId=9&sourceLineId=90&businessDateFrom=2026-07-01&businessDateTo=2026-07-11&page=1&pageSize=20',
      {
        credentials: 'include',
        headers: { Accept: 'application/json' },
        method: 'GET',
      },
    )
    expect(fetcher).toHaveBeenNthCalledWith(2, '/api/admin/inventory/reservations/9', {
      credentials: 'include',
      headers: { Accept: 'application/json' },
      method: 'GET',
    })
  })

  it('按查询条件分页获取库存变动和库存单据', async () => {
    const fetcher = vi
      .fn()
      .mockResolvedValueOnce(apiResponse({ items: [], total: 0, page: 1, pageSize: 20 }))
      .mockResolvedValueOnce(apiResponse({ items: [], total: 0, page: 3, pageSize: 10 }))
    const api = createInventoryApi({ fetcher })

    await api.movements.list({
      keyword: 'MOV',
      warehouseId: 1,
      materialId: 2,
      movementType: 'ADJUSTMENT_DECREASE',
      direction: 'OUT',
      trackingMethod: 'SERIAL',
      batchId: 31,
      batchNo: 'B-20260711-001',
      serialId: 41,
      serialNo: 'SN-20260711-001',
      dateFrom: '2026-07-01',
      dateTo: '2026-07-03',
      page: 1,
      pageSize: 20,
    })
    await api.documents.list({
      keyword: 'INV',
      documentType: 'ADJUSTMENT',
      status: 'DRAFT',
      dateFrom: '2026-07-01',
      dateTo: '2026-07-03',
      page: 3,
      pageSize: 10,
    })

    expect(fetcher).toHaveBeenNthCalledWith(
      1,
      '/api/admin/inventory/movements?keyword=MOV&warehouseId=1&materialId=2&movementType=ADJUSTMENT_DECREASE&direction=OUT&trackingMethod=SERIAL&batchId=31&batchNo=B-20260711-001&serialId=41&serialNo=SN-20260711-001&dateFrom=2026-07-01&dateTo=2026-07-03&page=1&pageSize=20',
      {
        credentials: 'include',
        headers: { Accept: 'application/json' },
        method: 'GET',
      },
    )
    expect(fetcher).toHaveBeenNthCalledWith(
      2,
      '/api/admin/inventory/documents?keyword=INV&documentType=ADJUSTMENT&status=DRAFT&dateFrom=2026-07-01&dateTo=2026-07-03&page=3&pageSize=10',
      {
        credentials: 'include',
        headers: { Accept: 'application/json' },
        method: 'GET',
      },
    )
  })

  it('库存变动查询支持销售出库流水类型', async () => {
    const fetcher = vi.fn().mockResolvedValueOnce(apiResponse({ items: [], total: 0, page: 1, pageSize: 20 }))
    const api = createInventoryApi({ fetcher })
    const salesShipmentType: InventoryMovementType = 'SALES_SHIPMENT'

    await api.movements.list({
      keyword: '销售出库',
      movementType: salesShipmentType,
      direction: 'OUT',
      page: 1,
      pageSize: 20,
    })

    expect(fetcher).toHaveBeenCalledWith(
      '/api/admin/inventory/movements?keyword=%E9%94%80%E5%94%AE%E5%87%BA%E5%BA%93&movementType=SALES_SHIPMENT&direction=OUT&page=1&pageSize=20',
      {
        credentials: 'include',
        headers: { Accept: 'application/json' },
        method: 'GET',
      },
    )
  })

  it('库存变动查询支持质量状态和来源追溯筛选', async () => {
    const fetcher = vi.fn().mockResolvedValueOnce(apiResponse({ items: [], total: 0, page: 1, pageSize: 20 }))
    const api = createInventoryApi({ fetcher })

    await api.movements.list({
      qualityStatus: 'PENDING_INSPECTION',
      sourceType: 'QUALITY_INSPECTION',
      sourceId: 9,
      sourceLineId: 90,
      page: 1,
      pageSize: 20,
    })

    expect(fetcher).toHaveBeenCalledWith(
      '/api/admin/inventory/movements?qualityStatus=PENDING_INSPECTION&sourceType=QUALITY_INSPECTION&sourceId=9&sourceLineId=90&page=1&pageSize=20',
      {
        credentials: 'include',
        headers: { Accept: 'application/json' },
        method: 'GET',
      },
    )
  })

  it('成本层列表和详情按冻结路径读取且不使用前端金额计算', async () => {
    const fetcher = vi
      .fn()
      .mockResolvedValueOnce(apiResponse({ items: [], total: 0, page: 1, pageSize: 20 }))
      .mockResolvedValueOnce(apiResponse({ id: 9001, layerNo: 'CL-001' }))
    const api = createInventoryApi({ fetcher })

    await api.costLayers.list({
      ownershipType: 'PROJECT',
      projectId: 501,
      materialId: 2,
      batchNo: 'B-001',
      status: 'OPEN',
      page: 1,
      pageSize: 20,
    })
    await api.costLayers.get(9001)

    expect(fetcher).toHaveBeenNthCalledWith(
      1,
      '/api/admin/inventory/cost-layers?ownershipType=PROJECT&projectId=501&materialId=2&batchNo=B-001&status=OPEN&page=1&pageSize=20',
      expect.objectContaining({ method: 'GET' }),
    )
    expect(fetcher).toHaveBeenNthCalledWith(
      2,
      '/api/admin/inventory/cost-layers/9001',
      expect.objectContaining({ method: 'GET' }),
    )
  })

  it('四类受控库存单据按路径发送十进制字符串、版本和幂等键', async () => {
    const fetcher = vi.fn()
    for (let index = 0; index < 18; index += 1) {
      fetcher.mockResolvedValueOnce(apiResponse({
        token: `csrf-${index}`,
        headerName: 'X-CSRF-TOKEN',
        parameterName: '_csrf',
      }))
      fetcher.mockResolvedValueOnce(apiResponse({ id: index + 1, version: index + 10 }))
    }
    const api = createInventoryApi({ fetcher })
    const actionPayload: InventoryControlledDocumentActionPayload = { version: 3, idempotencyKey: 'action-key' }
    const transferPayload: InventoryWarehouseTransferPayload = {
      idempotencyKey: 'transfer-key',
      businessDate: '2026-07-14',
      reason: '仓间补货',
      lines: [{
        lineNo: 10,
        sourceWarehouseId: 1,
        targetWarehouseId: 2,
        materialId: 3,
        unitId: 4,
        quantity: '12.345678',
        ownershipType: 'PROJECT',
        projectId: 501,
        sourceCostLayerId: 9001,
      }],
    }
    const conversionPayload: InventoryOwnershipConversionPayload = {
      idempotencyKey: 'conversion-key',
      businessDate: '2026-07-14',
      reason: '项目领用',
      lines: [{
        lineNo: 10,
        sourceOwnershipType: 'PROJECT',
        targetOwnershipType: 'PROJECT',
        sourceProjectId: 501,
        targetProjectId: 501,
        sourceWarehouseId: 1,
        targetWarehouseId: 2,
        materialId: 3,
        unitId: 4,
        quantity: '5.000000',
        sourceCostLayerId: 9001,
        sourceUnitCost: '11.000000',
      }],
    }
    const stocktakeLine: InventoryStocktakeLinePayload = {
      id: 7001,
      version: 4,
      countedQuantity: '0.000000',
    }
    const valuationPayload: InventoryValuationAdjustmentPayload = {
      adjustmentType: 'LEGACY_OPENING',
      idempotencyKey: 'valuation-key',
      businessDate: '2026-07-14',
      reason: '历史期初估值',
      lines: [{
        lineNo: 10,
        materialId: 3,
        ownershipType: 'PROJECT',
        projectId: 501,
        quantity: '100.000000',
        unitCost: '10.000000',
        adjustmentAmount: '1000.00',
        costLayerId: 9001,
      }],
    }

    await api.warehouseTransfers.create(transferPayload)
    await api.warehouseTransfers.update(1, transferPayload)
    await api.warehouseTransfers.post(1, actionPayload)
    await api.warehouseTransfers.cancel(1, actionPayload)
    await api.ownershipConversions.create(conversionPayload)
    await api.ownershipConversions.update(2, conversionPayload)
    await api.ownershipConversions.submitApproval(2, actionPayload)
    await api.ownershipConversions.withdraw(2, actionPayload)
    await api.ownershipConversions.cancel(2, actionPayload)
    await api.stocktakes.create({
      idempotencyKey: 'stocktake-key',
      businessDate: '2026-07-14',
      scopeType: 'WAREHOUSE',
      warehouseId: 1,
      reason: '月度盘点',
    })
    await api.stocktakes.start(3, actionPayload)
    await api.stocktakes.updateLines(3, { version: 9, lines: [stocktakeLine] })
    await api.stocktakes.reconcile(3, actionPayload)
    await api.stocktakes.completeZeroVariance(3, actionPayload)
    await api.stocktakes.cancel(3, actionPayload)
    await api.valuationAdjustments.create(valuationPayload)
    await api.valuationAdjustments.update(4, valuationPayload)
    await api.valuationAdjustments.submitApproval(4, actionPayload)

    const bodyForPath = (path: string) => {
      const call = fetcher.mock.calls.find(([url]) => url === path)
      expect(call).toBeDefined()
      return JSON.parse(call?.[1].body as string)
    }
    expect(bodyForPath('/api/admin/inventory/warehouse-transfers').lines[0].quantity).toBe('12.345678')
    expect(bodyForPath('/api/admin/inventory/warehouse-transfers').lines[0].unitId).toBe(4)
    expect(bodyForPath('/api/admin/inventory/warehouse-transfers').lines[0].sourceCostLayerId).toBe(9001)
    expect(bodyForPath('/api/admin/inventory/ownership-conversions').lines[0].sourceWarehouseId).toBe(1)
    expect(bodyForPath('/api/admin/inventory/ownership-conversions').lines[0].targetWarehouseId).toBe(2)
    expect(bodyForPath('/api/admin/inventory/ownership-conversions').lines[0].sourceUnitCost).toBe('11.000000')
    expect(bodyForPath('/api/admin/inventory/warehouse-transfers/1/post')).toEqual(actionPayload)
    expect(bodyForPath('/api/admin/inventory/stocktakes/3/lines')).toEqual({
      version: 9,
      lines: [{ id: 7001, version: 4, countedQuantity: '0.000000' }],
    })
    expect(bodyForPath('/api/admin/inventory/valuation-adjustments').lines[0].adjustmentAmount).toBe('1000.00')
    expect(fetcher.mock.calls.map((call) => call[0])).toEqual(expect.arrayContaining([
      '/api/admin/inventory/warehouse-transfers',
      '/api/admin/inventory/warehouse-transfers/1',
      '/api/admin/inventory/warehouse-transfers/1/post',
      '/api/admin/inventory/warehouse-transfers/1/cancel',
      '/api/admin/inventory/ownership-conversions/2/submit-approval',
      '/api/admin/inventory/ownership-conversions/2/withdraw',
      '/api/admin/inventory/stocktakes/3/start',
      '/api/admin/inventory/stocktakes/3/lines',
      '/api/admin/inventory/stocktakes/3/complete-zero-variance',
      '/api/admin/inventory/valuation-adjustments/4/submit-approval',
    ]))
  })

  it('批次、序列号和追溯接口按契约路径读取', async () => {
    const fetcher = vi
      .fn()
      .mockResolvedValueOnce(apiResponse({ items: [], total: 0, page: 1, pageSize: 20 }))
      .mockResolvedValueOnce(apiResponse({ id: 31, batchNo: 'B-20260711-001' }))
      .mockResolvedValueOnce(apiResponse({ items: [], total: 0, page: 1, pageSize: 20 }))
      .mockResolvedValueOnce(apiResponse({ id: 41, serialNo: 'SN-20260711-001' }))
      .mockResolvedValueOnce(apiResponse({ subject: { trackingMethod: 'BATCH', batchId: 31, batchNo: 'B-20260711-001' } }))
      .mockResolvedValueOnce(apiResponse({ subject: { trackingMethod: 'SERIAL', serialId: 41, serialNo: 'SN-20260711-001' } }))
    const api = createInventoryApi({ fetcher })
    const trackingMethod: InventoryTrackingMethod = 'BATCH'

    await api.batches.list({
      keyword: 'B-20260711',
      materialId: 2,
      warehouseId: 1,
      qualityStatus: 'QUALIFIED',
      batchNo: 'B-20260711-001',
      sourceType: 'PURCHASE_RECEIPT',
      sourceId: 9,
      onlyAvailable: true,
      page: 1,
      pageSize: 20,
    })
    await api.batches.get(31)
    await api.serials.list({
      keyword: 'SN-20260711',
      materialId: 2,
      warehouseId: 1,
      qualityStatus: 'QUALIFIED',
      batchId: 31,
      serialNo: 'SN-20260711-001',
      sourceType: 'PURCHASE_RECEIPT',
      sourceId: 9,
      onlyAvailable: true,
      page: 1,
      pageSize: 20,
    })
    await api.serials.get(41)
    await api.traces.getBatchTrace(31)
    await api.traces.getSerialTrace(41)

    expect(trackingMethod).toBe('BATCH')
    expect(fetcher).toHaveBeenNthCalledWith(
      1,
      '/api/admin/inventory/batches?keyword=B-20260711&materialId=2&warehouseId=1&qualityStatus=QUALIFIED&batchNo=B-20260711-001&sourceType=PURCHASE_RECEIPT&sourceId=9&onlyAvailable=true&page=1&pageSize=20',
      expect.objectContaining({ method: 'GET' }),
    )
    expect(fetcher).toHaveBeenNthCalledWith(
      2,
      '/api/admin/inventory/batches/31',
      expect.objectContaining({ method: 'GET' }),
    )
    expect(fetcher).toHaveBeenNthCalledWith(
      3,
      '/api/admin/inventory/serials?keyword=SN-20260711&materialId=2&warehouseId=1&qualityStatus=QUALIFIED&serialNo=SN-20260711-001&batchId=31&sourceType=PURCHASE_RECEIPT&sourceId=9&onlyAvailable=true&page=1&pageSize=20',
      expect.objectContaining({ method: 'GET' }),
    )
    expect(fetcher).toHaveBeenNthCalledWith(
      4,
      '/api/admin/inventory/serials/41',
      expect.objectContaining({ method: 'GET' }),
    )
    expect(fetcher).toHaveBeenNthCalledWith(
      5,
      '/api/admin/inventory/traces/batches/31',
      expect.objectContaining({ method: 'GET' }),
    )
    expect(fetcher).toHaveBeenNthCalledWith(
      6,
      '/api/admin/inventory/traces/serials/41',
      expect.objectContaining({ method: 'GET' }),
    )
  })

  it('按单据标识获取库存单据详情', async () => {
    const fetcher = vi.fn().mockResolvedValueOnce(apiResponse({ id: 9, documentNo: 'INV-20260703-001' }))
    const api = createInventoryApi({ fetcher })

    await api.documents.get(9)

    expect(fetcher).toHaveBeenCalledWith('/api/admin/inventory/documents/9', {
      credentials: 'include',
      headers: { Accept: 'application/json' },
      method: 'GET',
    })
  })

  it('创建、更新和过账库存单据前先获取 CSRF，过账不发送空 JSON body', async () => {
    const fetcher = vi
      .fn()
      .mockResolvedValueOnce(
        apiResponse({ token: 'csrf-create', headerName: 'X-CSRF-TOKEN', parameterName: '_csrf' }),
      )
      .mockResolvedValueOnce(apiResponse({ id: 1, documentNo: 'INV-1' }))
      .mockResolvedValueOnce(
        apiResponse({ token: 'csrf-update', headerName: 'X-CSRF-TOKEN', parameterName: '_csrf' }),
      )
      .mockResolvedValueOnce(apiResponse({ id: 1, documentNo: 'INV-1' }))
      .mockResolvedValueOnce(apiResponse({ token: 'csrf-post', headerName: 'X-CSRF-TOKEN', parameterName: '_csrf' }))
      .mockResolvedValueOnce(apiResponse({ id: 1, status: 'POSTED' }))
    const api = createInventoryApi({ fetcher })
    const payload: InventoryDocumentPayload = {
      documentType: 'OPENING',
      businessDate: '2026-07-03',
      reason: '期初库存',
      remark: '上线期初',
      lines: [{
        lineNo: 1,
        warehouseId: 1,
        materialId: 2,
        unitId: 3,
        quantity: '999999999999.999999',
        trackingAllocations: [{ batchNo: 'B-OPENING-001', quantity: '999999999999.999999' }],
      }],
    }

    await api.documents.create(payload)
    await api.documents.update(1, payload)
    await api.documents.post(1)

    expect(JSON.parse((fetcher.mock.calls[1][1].body as string))).toMatchObject({
      lines: [{
        quantity: '999999999999.999999',
        trackingAllocations: [{ batchNo: 'B-OPENING-001', quantity: '999999999999.999999' }],
      }],
    })

    expect(fetcher).toHaveBeenNthCalledWith(1, '/api/auth/csrf', {
      credentials: 'include',
      headers: { Accept: 'application/json' },
      method: 'GET',
    })
    expect(fetcher).toHaveBeenNthCalledWith(2, '/api/admin/inventory/documents', {
      body: JSON.stringify(payload),
      credentials: 'include',
      headers: {
        Accept: 'application/json',
        'Content-Type': 'application/json',
        'X-CSRF-TOKEN': 'csrf-create',
      },
      method: 'POST',
    })
    expect(fetcher).toHaveBeenNthCalledWith(3, '/api/auth/csrf', {
      credentials: 'include',
      headers: { Accept: 'application/json' },
      method: 'GET',
    })
    expect(fetcher).toHaveBeenNthCalledWith(4, '/api/admin/inventory/documents/1', {
      body: JSON.stringify(payload),
      credentials: 'include',
      headers: {
        Accept: 'application/json',
        'Content-Type': 'application/json',
        'X-CSRF-TOKEN': 'csrf-update',
      },
      method: 'PUT',
    })
    expect(fetcher).toHaveBeenNthCalledWith(5, '/api/auth/csrf', {
      credentials: 'include',
      headers: { Accept: 'application/json' },
      method: 'GET',
    })
    expect(fetcher).toHaveBeenNthCalledWith(6, '/api/admin/inventory/documents/1/post', {
      credentials: 'include',
      headers: {
        Accept: 'application/json',
        'X-CSRF-TOKEN': 'csrf-post',
      },
      method: 'PUT',
    })
  })

  it('后端返回非成功 envelope 时抛出统一 API 错误', async () => {
    const fetcher = vi.fn().mockResolvedValueOnce(apiFailure())
    const api = createInventoryApi({ fetcher })
    const request = api.balances.list({ page: 1, pageSize: 20 })

    await expect(request).rejects.toMatchObject({
      code: 'INVENTORY_STOCK_NOT_ENOUGH',
      status: 409,
      traceId: 'trace-inventory',
    })
    await expect(request).rejects.toBeInstanceOf(AccountPermissionApiError)
  })
})
