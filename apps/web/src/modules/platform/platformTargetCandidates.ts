import type { PageResult } from '../../shared/api/accountPermissionApi'
import { masterDataApi, type MasterDataStatus, type ResourceId } from '../../shared/api/masterDataApi'
import { pageItems } from '../system/shared/pageHelpers'

export interface PlatformTargetCandidate {
  id: ResourceId
  code: string
  name: string
  status?: string | null
  version?: number | null
}

export interface PlatformTargetCandidateQuery {
  keyword?: string
  page: number
  pageSize: number
  status?: MasterDataStatus
}

export async function listPlatformTargetCandidates(
  targetObjectType: string,
  query: PlatformTargetCandidateQuery,
): Promise<PageResult<PlatformTargetCandidate>> {
  const normalizedType = targetObjectType.trim().toUpperCase()
  if (normalizedType === 'CUSTOMER') {
    return mapCandidatePage(await masterDataApi.customers.list(query))
  }
  if (normalizedType === 'SUPPLIER') {
    return mapCandidatePage(await masterDataApi.suppliers.list(query))
  }
  if (normalizedType === 'MATERIAL') {
    return mapCandidatePage(await masterDataApi.materials.list(query))
  }
  throw new Error(`暂不支持 ${targetObjectType} 候选查询`)
}

export function hasStableCandidateVersion(candidate: PlatformTargetCandidate): boolean {
  return Number.isInteger(candidate.version) && Number(candidate.version) >= 0
}

export function stableCandidateVersion(candidate: PlatformTargetCandidate): number | null {
  return hasStableCandidateVersion(candidate) ? Number(candidate.version) : null
}

export function candidateMissingVersionMessage(candidate: PlatformTargetCandidate): string {
  return `候选对象 ${candidate.code || candidate.id} 缺少稳定版本`
}

function mapCandidatePage<T extends { id: ResourceId; code: string; name: string; status?: string | null; version?: number | null }>(
  page: PageResult<T>,
): PageResult<PlatformTargetCandidate> {
  return {
    ...page,
    items: pageItems(page).map((item) => ({
      id: item.id,
      code: item.code,
      name: item.name,
      status: item.status ?? null,
      version: item.version ?? null,
    })),
  }
}
