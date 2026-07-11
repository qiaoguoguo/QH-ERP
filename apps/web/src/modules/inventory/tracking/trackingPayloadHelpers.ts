import type {
  InventoryBatchSummaryRecord,
  InventorySerialSummaryRecord,
  InventoryTrackingAllocationPayload,
  InventoryTrackingMethod,
  ResourceId,
} from '../../../shared/api/inventoryApi'

export interface TrackingCandidateRecord {
  id: ResourceId
  trackingNo: string
  materialCode?: string | null
  materialName?: string | null
  warehouseName?: string | null
  qualityStatusName?: string | null
  stockStatusName?: string | null
  availableQuantity?: string | number | null
  disabled?: boolean
  disabledReason?: string | null
}

function parseQuantityToMicro(value: string | number | null | undefined): bigint | null {
  if (value === null || value === undefined) {
    return null
  }
  const trimmed = String(value).trim()
  if (!/^\d+(?:\.\d{1,6})?$/.test(trimmed)) {
    return null
  }
  const [integerPart, decimalPart = ''] = trimmed.split('.')
  return BigInt(integerPart) * 1_000_000n + BigInt(decimalPart.padEnd(6, '0'))
}

function formatMicroQuantity(value: bigint): string {
  const integerPart = value / 1_000_000n
  const decimalPart = value % 1_000_000n
  if (decimalPart === 0n) {
    return `${integerPart}`
  }
  return `${integerPart}.${decimalPart.toString().padStart(6, '0').replace(/0+$/, '')}`
}

function normalizedText(value: unknown): string {
  return String(value ?? '').trim()
}

function trackingIdentityKey(
  trackingMethod: InventoryTrackingMethod,
  allocation: Partial<InventoryTrackingAllocationPayload>,
) {
  if (allocation.sourceAllocationId) {
    return `source:${allocation.sourceAllocationId}`
  }
  if (trackingMethod === 'BATCH') {
    return allocation.batchId
      ? `batch-id:${allocation.batchId}`
      : normalizedText(allocation.batchNo)
        ? `batch-no:${normalizedText(allocation.batchNo)}`
        : ''
  }
  if (trackingMethod === 'SERIAL') {
    return allocation.serialId
      ? `serial-id:${allocation.serialId}`
      : normalizedText(allocation.serialNo)
        ? `serial-no:${normalizedText(allocation.serialNo)}`
        : ''
  }
  return ''
}

export function validateTrackingAllocations(
  trackingMethod: InventoryTrackingMethod,
  allocations: Partial<InventoryTrackingAllocationPayload>[] | undefined,
  expectedQuantity: string | number | null | undefined,
) {
  if (trackingMethod === 'NONE') {
    return []
  }
  const expected = parseQuantityToMicro(expectedQuantity)
  if (expected === null) {
    return ['业务数量不正确，无法校验追踪分配']
  }
  const rows = allocations ?? []
  if (rows.length === 0) {
    return ['追踪分配不能为空']
  }

  const messages: string[] = []
  let total = 0n
  const seen = new Set<string>()
  const duplicated = new Set<string>()
  rows.forEach((allocation, index) => {
    const identityKey = trackingIdentityKey(trackingMethod, allocation)
    if (!identityKey) {
      messages.push(`第 ${index + 1} 行${trackingMethod === 'SERIAL' ? '序列号' : '批次'}身份不能为空`)
    } else if (trackingMethod === 'SERIAL') {
      if (seen.has(identityKey)) {
        duplicated.add(identityKey.replace(/^[^:]+:/, ''))
      }
      seen.add(identityKey)
    }

    const quantity = parseQuantityToMicro(allocation.quantity)
    if (quantity === null || quantity <= 0n) {
      messages.push(`第 ${index + 1} 行数量必须为正数，最多 6 位小数`)
      return
    }
    if (trackingMethod === 'SERIAL' && quantity !== 1_000_000n) {
      messages.push(`第 ${index + 1} 行序列号数量必须为 1`)
    }
    total += quantity
  })

  if (duplicated.size > 0) {
    messages.push(`序列号重复：${Array.from(duplicated).join('、')}`)
  }
  if (total !== expected) {
    messages.push(`追踪分配数量合计 ${formatMicroQuantity(total)} 与业务数量 ${formatMicroQuantity(expected)} 不一致`)
  }
  return messages
}

export const validateOutboundTrackingAllocations = validateTrackingAllocations
export const validateInboundTrackingAllocations = validateTrackingAllocations

export function inferTrackingMethodFromAllocations(
  allocations?: InventoryTrackingAllocationPayload[] | null,
): InventoryTrackingMethod {
  const allocation = allocations?.find((item) => item.trackingMethod || item.batchId || item.batchNo || item.serialId || item.serialNo)
  if (!allocation) {
    return 'NONE'
  }
  if (allocation.trackingMethod) {
    return allocation.trackingMethod
  }
  if (allocation.batchId || allocation.batchNo) {
    return 'BATCH'
  }
  if (allocation.serialId || allocation.serialNo) {
    return 'SERIAL'
  }
  return 'NONE'
}

export function mapBatchCandidate(item: InventoryBatchSummaryRecord): TrackingCandidateRecord {
  return {
    id: item.id,
    trackingNo: item.batchNo,
    materialCode: item.materialCode,
    materialName: item.materialName,
    warehouseName: item.warehouseName,
    qualityStatusName: item.qualityStatusName,
    stockStatusName: item.stockStatusName,
    availableQuantity: item.availableQuantity,
    disabled: item.selectable === false,
    disabledReason: item.disabledReason ?? null,
  }
}

export function mapSerialCandidate(item: InventorySerialSummaryRecord): TrackingCandidateRecord {
  return {
    id: item.id,
    trackingNo: item.serialNo,
    materialCode: item.materialCode,
    materialName: item.materialName,
    warehouseName: item.warehouseName,
    qualityStatusName: item.qualityStatusName,
    stockStatusName: item.stockStatusName,
    availableQuantity: item.availableQuantity,
    disabled: item.selectable === false,
    disabledReason: item.disabledReason ?? null,
  }
}

export function outboundTrackingAllocationsPayload(
  trackingMethod: InventoryTrackingMethod,
  allocations: InventoryTrackingAllocationPayload[] | undefined,
  fallbackQuantity: string,
) {
  if (trackingMethod === 'NONE') {
    return undefined
  }
  const rows = (allocations ?? [])
    .map((allocation) => ({
      ...(allocation.sourceAllocationId ? { sourceAllocationId: allocation.sourceAllocationId } : {}),
      ...(allocation.batchId ? { batchId: allocation.batchId } : {}),
      ...(!allocation.batchId && normalizedText(allocation.batchNo) ? { batchNo: normalizedText(allocation.batchNo) } : {}),
      ...(allocation.serialId ? { serialId: allocation.serialId } : {}),
      ...(!allocation.serialId && normalizedText(allocation.serialNo) ? { serialNo: normalizedText(allocation.serialNo) } : {}),
      quantity: String(allocation.quantity ?? (trackingMethod === 'SERIAL' ? '1' : fallbackQuantity)).trim(),
    }))
    .filter((allocation) => (
      allocation.sourceAllocationId || allocation.batchId || allocation.serialId
    ) && allocation.quantity)
  return rows.length > 0 ? rows : undefined
}

export function inboundTrackingAllocationsPayload(
  trackingMethod: InventoryTrackingMethod,
  allocations: InventoryTrackingAllocationPayload[] | undefined,
) {
  if (trackingMethod === 'NONE') {
    return undefined
  }
  const rows = (allocations ?? [])
    .map((allocation) => ({
      ...(allocation.sourceAllocationId ? { sourceAllocationId: allocation.sourceAllocationId } : {}),
      ...(trackingMethod === 'BATCH' && String(allocation.batchNo ?? '').trim() ? { batchNo: String(allocation.batchNo).trim() } : {}),
      ...(trackingMethod === 'SERIAL' && String(allocation.serialNo ?? '').trim() ? { serialNo: String(allocation.serialNo).trim() } : {}),
      ...(allocation.batchId ? { batchId: allocation.batchId } : {}),
      ...(allocation.serialId ? { serialId: allocation.serialId } : {}),
      quantity: String(allocation.quantity ?? (trackingMethod === 'SERIAL' ? '1' : '')).trim(),
    }))
    .filter((allocation) => (
      allocation.sourceAllocationId || allocation.batchId || allocation.batchNo || allocation.serialId || allocation.serialNo
    ) && allocation.quantity)
  return rows.length > 0 ? rows : undefined
}
