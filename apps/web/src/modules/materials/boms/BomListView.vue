<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import {
  bomApi,
  type BomStateSnapshot,
  type BomCopyPayload,
  type BomDetailRecord,
  type BomEngineeringChangeApplyResult,
  type BomEngineeringChangePayload,
  type BomEngineeringChangeRecord,
  type BomEngineeringChangeStatus,
  type BomHistoryRelationRecord,
  type BomPayload,
  type BomStatus,
  type BomSummaryRecord,
  type CandidateItem,
  type MaterialSubstitutePayload,
  type MaterialSubstituteRecord,
  type MaterialSubstituteScopeType,
  type ResourceId,
} from '../../../shared/api/bomApi'
import { createIdempotencyKey, documentPlatformApi, type BomDraftImportMode, type DocumentTaskRecord } from '../../../shared/api/documentPlatformApi'
import { downloadFile } from '../../../shared/file/download'
import { masterDataApi } from '../../../shared/api/masterDataApi'
import { useAuthStore } from '../../../stores/authStore'
import ApprovalStatusPanel from '../../platform/components/ApprovalStatusPanel.vue'
import AttachmentPanel from '../../platform/components/AttachmentPanel.vue'
import PrintAction from '../../platform/components/PrintAction.vue'
import MasterDataTableView from '../../master/shared/MasterDataTableView.vue'
import { errorMessage, pageItems } from '../../system/shared/pageHelpers'
import BomLineEditor from './BomLineEditor.vue'
import BomStatusTag from './BomStatusTag.vue'
import {
  bomEffectiveState,
  bomEffectiveStateLabel,
  bomEffectiveStateTagType,
  type BomLineDraft,
  lossRateDecimalString,
  newBomLine,
  positiveDecimalString,
  todayText,
} from './bomPageHelpers'
import { confirmAction } from '../../../shared/ui/confirmDialog'

const authStore = useAuthStore()
type BomTab = 'versions' | 'eco' | 'substitutes'

const activeTab = ref<BomTab>('versions')
const filters = reactive<{ keyword: string; status?: BomStatus; parentMaterialId: ResourceId | ''; effectiveDate: string; includeHistory: boolean }>({
  keyword: '',
  status: undefined,
  parentMaterialId: '',
  effectiveDate: '',
  includeHistory: false,
})
const pagination = reactive({ page: 1, pageSize: 10, total: 0 })
const records = ref<BomSummaryRecord[]>([])
const bomMaterialCandidates = ref<CandidateItem[]>([])
const bomUnitCandidates = ref<CandidateItem[]>([])
const ecoSourceBomCandidates = ref<CandidateItem[]>([])
const ecoTargetBomCandidates = ref<CandidateItem[]>([])
const substituteMaterialCandidates = ref<CandidateItem[]>([])
const substituteBomCandidates = ref<CandidateItem[]>([])
const loading = ref(true)
const referenceLoading = ref(true)
const error = ref('')
const referenceError = ref('')
const actionError = ref('')
const actionLoading = ref(false)

const formVisible = ref(false)
const formSubmitting = ref(false)
const formError = ref('')
const codeGenerating = ref(false)
const lineErrors = ref<Record<number, string>>({})
const editingRecord = ref<BomDetailRecord | null>(null)
const form = reactive({
  bomCode: '',
  parentMaterialId: '' as ResourceId | '',
  versionCode: '',
  name: '',
  baseQuantity: '1',
  baseUnitId: '' as ResourceId | '',
  effectiveFrom: '',
  effectiveTo: '',
  remark: '',
})
const lines = ref<BomLineDraft[]>([newBomLine()])

const detailVisible = ref(false)
const detailRecord = ref<BomDetailRecord | null>(null)

const copyVisible = ref(false)
const copySubmitting = ref(false)
const copyError = ref('')
const copyTarget = ref<BomSummaryRecord | null>(null)
const copyForm = reactive({ bomCode: '', versionCode: '', name: '' })

const ecoFilters = reactive<{ keyword: string; status?: BomEngineeringChangeStatus }>({ keyword: '', status: undefined })
const ecoPagination = reactive({ page: 1, pageSize: 10, total: 0 })
const ecoRecords = ref<BomEngineeringChangeRecord[]>([])
const ecoLoading = ref(false)
const ecoError = ref('')
const ecoActionError = ref('')
const ecoFormVisible = ref(false)
const ecoFormSubmitting = ref(false)
const ecoFormError = ref('')
const ecoEditingRecord = ref<BomEngineeringChangeRecord | null>(null)
const ecoSourceParentMaterialId = ref<ResourceId | undefined>(undefined)
const ecoDetailVisible = ref(false)
const ecoDetailRecord = ref<BomEngineeringChangeRecord | BomEngineeringChangeApplyResult | null>(null)
const ecoForm = reactive({
  ecoNo: '',
  sourceBomId: '' as ResourceId | '',
  targetBomId: '' as ResourceId | '',
  effectiveFrom: '',
  effectiveTo: '',
  changeReason: '',
  impactScope: '',
  changeSummary: '',
  remark: '',
})
const ecoCancelVisible = ref(false)
const ecoCancelSubmitting = ref(false)
const ecoCancelError = ref('')
const ecoCancelTarget = ref<BomEngineeringChangeRecord | null>(null)
const ecoCancelReason = ref('')
const ecoApprovalVisible = ref(false)
const ecoApprovalSubmitting = ref(false)
const ecoApprovalError = ref('')
const ecoApprovalTarget = ref<BomEngineeringChangeRecord | null>(null)
const ecoApprovalReason = ref('')
const latestDocumentTask = ref<DocumentTaskRecord | null>(null)
const bomDraftImportVisible = ref(false)
const bomDraftImportMode = ref<BomDraftImportMode>('CREATE')
const bomDraftImportTargetId = ref<ResourceId | ''>('')
const bomDraftImportFile = ref<File | null>(null)
const bomDraftImportSubmitting = ref(false)
const bomDraftImportError = ref('')

const substituteFilters = reactive<{
  keyword: string
  mainMaterialId: ResourceId | ''
  substituteMaterialId: ResourceId | ''
  status?: 'ENABLED' | 'DISABLED'
  scopeType?: MaterialSubstituteScopeType
  scopeId: ResourceId | ''
  effectiveDate: string
}>({
  keyword: '',
  mainMaterialId: '',
  substituteMaterialId: '',
  status: undefined,
  scopeType: undefined,
  scopeId: '',
  effectiveDate: '',
})
const substitutePagination = reactive({ page: 1, pageSize: 10, total: 0 })
const substituteRecords = ref<MaterialSubstituteRecord[]>([])
const substituteLoading = ref(false)
const substituteError = ref('')
const substituteActionError = ref('')
const substituteFormVisible = ref(false)
const substituteFormSubmitting = ref(false)
const substituteFormError = ref('')
const substituteEditingRecord = ref<MaterialSubstituteRecord | null>(null)
const substituteDetailVisible = ref(false)
const substituteDetailRecord = ref<MaterialSubstituteRecord | null>(null)
const substituteForm = reactive({
  mainMaterialId: '' as ResourceId | '',
  substituteMaterialId: '' as ResourceId | '',
  scopeType: 'GLOBAL' as MaterialSubstituteScopeType,
  scopeId: '' as ResourceId | '',
  priority: '',
  substituteRate: '1',
  effectiveFrom: '',
  effectiveTo: '',
  status: 'ENABLED' as 'ENABLED' | 'DISABLED',
  remark: '',
})

const canCreate = computed(() => authStore.hasPermission('material:bom:create'))
const canUpdate = computed(() => authStore.hasPermission('material:bom:update'))
const canCopy = computed(() => authStore.hasPermission('material:bom:copy'))
const canEnable = computed(() => authStore.hasPermission('material:bom:enable'))
const canDisable = computed(() => authStore.hasPermission('material:bom:disable'))
const canGenerateCode = computed(() => authStore.hasPermission('master:coding-rule:generate'))
const canHistoryImport = computed(() => (
  authStore.hasPermission('platform:history-import:view')
  || authStore.hasPermission('platform:history-import:create')
))
const canEcoCreate = computed(() => authStore.hasPermission('material:bom-eco:create'))
const canEcoUpdate = computed(() => authStore.hasPermission('material:bom-eco:update'))
const canEcoApply = computed(() => authStore.hasPermission('material:bom-eco:apply'))
const canEcoCancel = computed(() => authStore.hasPermission('material:bom-eco:cancel'))
const canBomImport = computed(() => authStore.hasPermission('material:bom:import'))
const canBomExport = computed(() => authStore.hasPermission('material:bom:export'))
const canSubstituteCreate = computed(() => authStore.hasPermission('material:substitute:create'))
const canSubstituteUpdate = computed(() => authStore.hasPermission('material:substitute:update'))
const canSubstituteEnable = computed(() => authStore.hasPermission('material:substitute:enable'))
const canSubstituteDisable = computed(() => authStore.hasPermission('material:substitute:disable'))
const effectiveReferenceDate = computed(() => filters.effectiveDate || todayText())

function normalizeOptionalId(value: ResourceId | ''): ResourceId | undefined {
  if (value === '' || value === null || value === undefined) {
    return undefined
  }
  const text = String(value).trim()
  if (!text) {
    return undefined
  }
  const numericValue = Number(text)
  return Number.isFinite(numericValue) ? numericValue : text
}

function normalizeRequiredId(value: ResourceId | ''): ResourceId | null {
  return normalizeOptionalId(value) ?? null
}

function mergeCandidateItems(page: { items?: CandidateItem[]; selectedItems?: CandidateItem[] }): CandidateItem[] {
  const merged = new Map<string, CandidateItem>()
  ;[...(page.selectedItems ?? []), ...(page.items ?? [])].forEach((item) => {
    merged.set(String(item.id), item)
  })
  return Array.from(merged.values())
}

function selectedIds(value: ResourceId | ''): ResourceId[] {
  const id = normalizeOptionalId(value)
  return id === undefined ? [] : [id]
}

function collectBomMaterialSelectedIds() {
  return [
    ...selectedIds(form.parentMaterialId),
    ...lines.value.flatMap((line) => selectedIds(line.childMaterialId)),
  ]
}

function collectBomUnitSelectedIds() {
  return [
    ...selectedIds(form.baseUnitId),
    ...lines.value.flatMap((line) => selectedIds(line.businessUnitId)),
  ]
}

function candidateLabel(item: CandidateItem): string {
  return item.summary ? `${item.code} ${item.name} / ${item.summary}` : `${item.code} ${item.name}`
}

async function loadBomMaterialCandidates(keyword = '', retainedIds: ResourceId[] = []) {
  try {
    const page = await bomApi.materialCandidates({
      keyword,
      status: 'ENABLED',
      page: 1,
      pageSize: 20,
      selectedIds: retainedIds,
    })
    bomMaterialCandidates.value = mergeCandidateItems(page)
  } catch (caught) {
    bomMaterialCandidates.value = []
    referenceError.value = errorMessage(caught)
  }
}

async function loadBomUnitCandidates(keyword = '', retainedIds: ResourceId[] = []) {
  try {
    const page = await bomApi.unitCandidates({
      keyword,
      status: 'ENABLED',
      page: 1,
      pageSize: 20,
      selectedIds: retainedIds,
    })
    bomUnitCandidates.value = mergeCandidateItems(page)
  } catch (caught) {
    bomUnitCandidates.value = []
    referenceError.value = errorMessage(caught)
  }
}

async function loadEcoSourceBomCandidates(keyword = '', retainedIds: ResourceId[] = [], parentMaterialId?: ResourceId) {
  try {
    const page = await bomApi.engineeringChanges.sourceBomCandidates({
      keyword,
      parentMaterialId,
      page: 1,
      pageSize: 20,
      selectedIds: retainedIds,
    })
    ecoSourceBomCandidates.value = mergeCandidateItems(page)
  } catch (caught) {
    ecoSourceBomCandidates.value = []
    ecoFormError.value = errorMessage(caught)
  }
}

async function searchEcoSourceBoms(keyword = '') {
  await loadEcoSourceBomCandidates(keyword, selectedIds(ecoForm.sourceBomId), ecoSourceParentMaterialId.value)
}

function refreshEcoSourceBoms(visible: boolean) {
  if (visible) {
    void searchEcoSourceBoms('')
  }
}

async function loadEcoTargetBomCandidates(
  keyword = '',
  retainedIds: ResourceId[] = [],
  sourceBomId = normalizeOptionalId(ecoForm.sourceBomId),
) {
  try {
    const page = await bomApi.engineeringChanges.targetBomCandidates({
      keyword,
      sourceBomId,
      page: 1,
      pageSize: 20,
      selectedIds: retainedIds,
    })
    ecoTargetBomCandidates.value = mergeCandidateItems(page)
  } catch (caught) {
    ecoTargetBomCandidates.value = []
    ecoFormError.value = errorMessage(caught)
  }
}

async function loadSubstituteMaterialCandidates(keyword = '', retainedIds: ResourceId[] = []) {
  try {
    const page = await bomApi.substitutes.materialCandidates({
      keyword,
      status: 'ENABLED',
      page: 1,
      pageSize: 20,
      selectedIds: retainedIds,
    })
    substituteMaterialCandidates.value = mergeCandidateItems(page)
  } catch (caught) {
    substituteMaterialCandidates.value = []
    substituteError.value = errorMessage(caught)
  }
}

async function loadSubstituteBomCandidates(
  keyword = '',
  retainedIds: ResourceId[] = [],
  parentMaterialId = normalizeOptionalId(substituteForm.mainMaterialId) ?? normalizeOptionalId(substituteFilters.mainMaterialId),
) {
  try {
    const page = await bomApi.substitutes.bomCandidates({
      keyword,
      parentMaterialId,
      page: 1,
      pageSize: 20,
      selectedIds: retainedIds,
    })
    substituteBomCandidates.value = mergeCandidateItems(page)
  } catch (caught) {
    substituteBomCandidates.value = []
    substituteFormError.value = errorMessage(caught)
  }
}

async function loadReferences() {
  referenceLoading.value = true
  referenceError.value = ''
  try {
    await Promise.all([
      loadBomMaterialCandidates('', []),
      loadBomUnitCandidates('', []),
      loadSubstituteMaterialCandidates('', []),
    ])
  } finally {
    referenceLoading.value = false
  }
}

function searchBomMaterials(keyword: string) {
  void loadBomMaterialCandidates(keyword, collectBomMaterialSelectedIds())
}

function searchBomUnits(keyword: string) {
  void loadBomUnitCandidates(keyword, collectBomUnitSelectedIds())
}

function searchFilterBomParents(keyword: string) {
  void loadBomMaterialCandidates(keyword, selectedIds(filters.parentMaterialId))
}

async function loadRecords() {
  loading.value = true
  error.value = ''
  try {
    const page = await bomApi.list({
      keyword: filters.keyword,
      status: filters.status,
      parentMaterialId: normalizeOptionalId(filters.parentMaterialId),
      ...(filters.effectiveDate ? { effectiveDate: filters.effectiveDate } : {}),
      includeHistory: filters.includeHistory,
      page: pagination.page,
      pageSize: pagination.pageSize,
    })
    records.value = pageItems(page)
    pagination.total = Number(page.total)
  } catch (caught) {
    records.value = []
    pagination.total = 0
    error.value = errorMessage(caught)
  } finally {
    loading.value = false
  }
}

async function loadEcoRecords() {
  ecoLoading.value = true
  ecoError.value = ''
  try {
    const page = await bomApi.engineeringChanges.list({
      keyword: ecoFilters.keyword,
      status: ecoFilters.status,
      page: ecoPagination.page,
      pageSize: ecoPagination.pageSize,
    })
    ecoRecords.value = pageItems(page)
    ecoPagination.total = Number(page.total)
  } catch (caught) {
    ecoRecords.value = []
    ecoPagination.total = 0
    ecoError.value = errorMessage(caught)
  } finally {
    ecoLoading.value = false
  }
}

async function loadSubstituteRecords() {
  substituteLoading.value = true
  substituteError.value = ''
  try {
    const page = await bomApi.substitutes.list({
      keyword: substituteFilters.keyword,
      mainMaterialId: normalizeOptionalId(substituteFilters.mainMaterialId),
      substituteMaterialId: normalizeOptionalId(substituteFilters.substituteMaterialId),
      status: substituteFilters.status,
      scopeType: substituteFilters.scopeType,
      scopeId: normalizeOptionalId(substituteFilters.scopeId),
      effectiveDate: substituteFilters.effectiveDate || undefined,
      page: substitutePagination.page,
      pageSize: substitutePagination.pageSize,
    })
    substituteRecords.value = pageItems(page)
    substitutePagination.total = Number(page.total)
  } catch (caught) {
    substituteRecords.value = []
    substitutePagination.total = 0
    substituteError.value = errorMessage(caught)
  } finally {
    substituteLoading.value = false
  }
}

function switchTab(tab: BomTab) {
  activeTab.value = tab
  if (tab === 'eco' && ecoRecords.value.length === 0) {
    void loadEcoRecords()
  }
  if (tab === 'substitutes' && substituteRecords.value.length === 0) {
    void loadSubstituteRecords()
  }
}

function handleTabChange(name: string | number) {
  if (name === 'versions' || name === 'eco' || name === 'substitutes') {
    switchTab(name)
  }
}

function search() {
  pagination.page = 1
  void loadRecords()
}

function resetSearch() {
  filters.keyword = ''
  filters.status = undefined
  filters.parentMaterialId = ''
  filters.effectiveDate = ''
  filters.includeHistory = false
  pagination.page = 1
  void loadRecords()
}

function changePage(page: number) {
  pagination.page = page
  void loadRecords()
}

function changePageSize(pageSize: number) {
  pagination.pageSize = pageSize
  pagination.page = 1
  void loadRecords()
}

function changeEcoPage(page: number) {
  ecoPagination.page = page
  void loadEcoRecords()
}

function changeEcoPageSize(pageSize: number) {
  ecoPagination.pageSize = pageSize
  ecoPagination.page = 1
  void loadEcoRecords()
}

function changeSubstitutePage(page: number) {
  substitutePagination.page = page
  void loadSubstituteRecords()
}

function changeSubstitutePageSize(pageSize: number) {
  substitutePagination.pageSize = pageSize
  substitutePagination.page = 1
  void loadSubstituteRecords()
}

function resetForm(record?: BomDetailRecord) {
  Object.assign(form, {
    bomCode: record?.bomCode ?? '',
    parentMaterialId: record?.parentMaterialId ?? '',
    versionCode: record?.versionCode ?? '',
    name: record?.name ?? '',
    baseQuantity: record?.baseQuantity ?? '1',
    baseUnitId: record?.baseUnitId ?? '',
    effectiveFrom: record?.effectiveFrom ?? '',
    effectiveTo: record?.effectiveTo ?? '',
    remark: record?.remark ?? '',
  })
  lines.value = record?.items.map((item) => ({
    lineNo: item.lineNo,
    childMaterialId: item.childMaterialId,
    businessUnitId: item.businessUnitId,
    businessQuantity: item.businessQuantity,
    lossRate: item.lossRate ?? '0',
    remark: item.remark ?? '',
  })) ?? [newBomLine()]
  formError.value = ''
  lineErrors.value = {}
}

async function openCreate() {
  editingRecord.value = null
  resetForm()
  await Promise.all([
    loadBomMaterialCandidates('', collectBomMaterialSelectedIds()),
    loadBomUnitCandidates('', collectBomUnitSelectedIds()),
  ])
  formVisible.value = true
}

async function openEdit(record: BomSummaryRecord) {
  actionError.value = ''
  try {
    const detail = await bomApi.get(record.id)
    editingRecord.value = detail
    resetForm(detail)
    await Promise.all([
      loadBomMaterialCandidates('', collectBomMaterialSelectedIds()),
      loadBomUnitCandidates('', collectBomUnitSelectedIds()),
    ])
    formVisible.value = true
  } catch (caught) {
    actionError.value = errorMessage(caught)
  }
}

async function openDetail(record: BomSummaryRecord) {
  actionError.value = ''
  try {
    detailRecord.value = await bomApi.get(record.id)
    detailVisible.value = true
  } catch (caught) {
    actionError.value = errorMessage(caught)
  }
}

function validateBomForm(): BomPayload | null {
  const parentMaterialId = normalizeRequiredId(form.parentMaterialId)
  if (!form.bomCode.trim() || !form.versionCode.trim() || !form.name.trim() || parentMaterialId === null) {
    formError.value = '请完整填写 BOM 编码、版本、名称和父项物料'
    return null
  }
  const baseQuantity = positiveDecimalString(form.baseQuantity)
  if (baseQuantity === null) {
    formError.value = '基准数量必须大于 0'
    return null
  }
  if (form.effectiveFrom && form.effectiveTo && form.effectiveFrom > form.effectiveTo) {
    formError.value = '生效日期不能晚于失效日期'
    return null
  }
  if (lines.value.length === 0) {
    formError.value = 'BOM 明细不能为空'
    return null
  }
  const nextLineErrors: Record<number, string> = {}
  const childIds = new Set<string>()
  const items = []
  for (const line of lines.value) {
    const childMaterialId = normalizeRequiredId(line.childMaterialId)
    const businessUnitId = normalizeRequiredId(line.businessUnitId)
    if (childMaterialId === null) {
      nextLineErrors[line.lineNo] = `第 ${line.lineNo} 行请选择子项物料`
      continue
    }
    if (businessUnitId === null) {
      nextLineErrors[line.lineNo] = `第 ${line.lineNo} 行请选择业务单位`
      continue
    }
    const businessQuantity = positiveDecimalString(line.businessQuantity)
    if (businessQuantity === null) {
      nextLineErrors[line.lineNo] = `第 ${line.lineNo} 行用量必须大于 0`
      continue
    }
    const lossRate = lossRateDecimalString(line.lossRate)
    if (lossRate === null) {
      nextLineErrors[line.lineNo] = `第 ${line.lineNo} 行损耗率需大于等于 0 且小于 1`
      continue
    }
    const childKey = String(childMaterialId)
    if (childIds.has(childKey)) {
      formError.value = 'BOM 明细子项不能重复'
      lineErrors.value = {}
      return null
    }
    childIds.add(childKey)
    items.push({
      lineNo: line.lineNo,
      childMaterialId,
      businessUnitId,
      businessQuantity,
      lossRate,
      remark: line.remark.trim() || null,
    })
  }
  lineErrors.value = nextLineErrors
  if (Object.keys(nextLineErrors).length > 0) {
    formError.value = ''
    return null
  }
  const baseUnitId = normalizeOptionalId(form.baseUnitId)
  const payload: BomPayload = {
    bomCode: form.bomCode.trim(),
    parentMaterialId,
    versionCode: form.versionCode.trim(),
    name: form.name.trim(),
    baseQuantity,
    ...(baseUnitId !== undefined ? { baseUnitId } : {}),
    effectiveFrom: form.effectiveFrom || null,
    effectiveTo: form.effectiveTo || null,
    remark: form.remark.trim() || null,
    items,
  }
  if (editingRecord.value) {
    payload.version = editingRecord.value.version
  }
  formError.value = ''
  return payload
}

async function saveRecord() {
  if (formSubmitting.value) {
    return
  }
  const payload = validateBomForm()
  if (!payload) {
    return
  }
  formSubmitting.value = true
  try {
    if (editingRecord.value) {
      await bomApi.update(editingRecord.value.id, payload)
    } else {
      await bomApi.create(payload)
    }
    formVisible.value = false
    await loadRecords()
  } catch (caught) {
    formError.value = errorMessage(caught)
  } finally {
    formSubmitting.value = false
  }
}

async function generateBomCode() {
  if (codeGenerating.value) {
    return
  }
  formError.value = ''
  codeGenerating.value = true
  try {
    const result = await masterDataApi.codingRules.generate({ objectType: 'BOM' })
    form.bomCode = result.generatedCode
  } catch (caught) {
    formError.value = errorMessage(caught)
  } finally {
    codeGenerating.value = false
  }
}

function openCopy(record: BomSummaryRecord) {
  copyTarget.value = record
  copyForm.bomCode = ''
  copyForm.versionCode = ''
  copyForm.name = record.name
  copyError.value = ''
  copyVisible.value = true
}

async function submitCopy() {
  if (!copyTarget.value || copySubmitting.value) {
    return
  }
  if (!copyForm.bomCode.trim() || !copyForm.versionCode.trim()) {
    copyError.value = '请填写新 BOM 编码和版本'
    return
  }
  const payload: BomCopyPayload = {
    bomCode: copyForm.bomCode.trim(),
    versionCode: copyForm.versionCode.trim(),
    name: copyForm.name.trim() || undefined,
  }
  copySubmitting.value = true
  try {
    await bomApi.copy(copyTarget.value.id, payload)
    copyVisible.value = false
    await loadRecords()
  } catch (caught) {
    copyError.value = errorMessage(caught)
  } finally {
    copySubmitting.value = false
  }
}

async function publishRecord(record: BomSummaryRecord) {
  if (!(await confirmAction(`确认发布 BOM“${record.bomCode}”？`))) {
    return
  }
  actionError.value = ''
  actionLoading.value = true
  try {
    await bomApi.enable(record.id, { version: record.version })
    await loadRecords()
  } catch (caught) {
    actionError.value = errorMessage(caught)
  } finally {
    actionLoading.value = false
  }
}

async function disableRecord(record: BomSummaryRecord) {
  if (!(await confirmAction(`确认停用 BOM“${record.bomCode}”？`))) {
    return
  }
  actionError.value = ''
  actionLoading.value = true
  try {
    await bomApi.disable(record.id, { version: record.version })
    await loadRecords()
  } catch (caught) {
    actionError.value = errorMessage(caught)
  } finally {
    actionLoading.value = false
  }
}

async function downloadBomDraftTemplate() {
  actionError.value = ''
  actionLoading.value = true
  try {
    downloadFile(await documentPlatformApi.importTemplates.downloadBomDrafts())
  } catch (caught) {
    actionError.value = errorMessage(caught)
  } finally {
    actionLoading.value = false
  }
}

function openBomDraftImport() {
  bomDraftImportVisible.value = true
  bomDraftImportMode.value = 'CREATE'
  bomDraftImportTargetId.value = ''
  bomDraftImportFile.value = null
  bomDraftImportError.value = ''
}

function selectBomDraftImportFile(event: Event) {
  const input = event.target as HTMLInputElement
  bomDraftImportFile.value = input.files?.[0] ?? null
}

function selectedBomDraftImportTarget(): BomSummaryRecord | null {
  if (bomDraftImportTargetId.value === '') {
    return null
  }
  return records.value.find((record) => String(record.id) === String(bomDraftImportTargetId.value)) ?? null
}

async function submitBomDraftImport() {
  if (!bomDraftImportFile.value || bomDraftImportSubmitting.value) {
    bomDraftImportError.value = '请选择 .xlsx BOM 草稿文件'
    return
  }
  const target = selectedBomDraftImportTarget()
  if (bomDraftImportMode.value === 'UPDATE_DRAFT' && !target) {
    bomDraftImportError.value = '请选择要更新的草稿 BOM'
    return
  }
  bomDraftImportSubmitting.value = true
  bomDraftImportError.value = ''
  try {
    latestDocumentTask.value = await documentPlatformApi.imports.uploadBomDraft({
      mode: bomDraftImportMode.value,
      ...(target ? { bomId: target.id, version: target.version } : {}),
      file: bomDraftImportFile.value,
      idempotencyKey: createIdempotencyKey('bom-draft-import'),
    })
    bomDraftImportVisible.value = false
  } catch (caught) {
    bomDraftImportError.value = errorMessage(caught)
  } finally {
    bomDraftImportSubmitting.value = false
  }
}

async function exportBomDraft(record: BomSummaryRecord) {
  actionError.value = ''
  actionLoading.value = true
  try {
    latestDocumentTask.value = await documentPlatformApi.exports.createBomDraft(record.id, {
      idempotencyKey: createIdempotencyKey('bom-draft-export'),
    })
  } catch (caught) {
    actionError.value = errorMessage(caught)
  } finally {
    actionLoading.value = false
  }
}

function resetEcoForm(record?: BomEngineeringChangeRecord) {
  Object.assign(ecoForm, {
    ecoNo: record?.ecoNo ?? '',
    sourceBomId: record?.sourceBomId ?? '',
    targetBomId: record?.targetBomId ?? '',
    effectiveFrom: record?.effectiveFrom ?? '',
    effectiveTo: record?.effectiveTo ?? '',
    changeReason: record?.changeReason ?? '',
    impactScope: record?.impactScope ?? '',
    changeSummary: record?.changeSummary ?? '',
    remark: record?.remark ?? '',
  })
  ecoFormError.value = ''
}

async function openCreateEco(source?: Pick<BomSummaryRecord, 'id' | 'parentMaterialId'>) {
  ecoEditingRecord.value = null
  ecoSourceParentMaterialId.value = source?.parentMaterialId
  resetEcoForm()
  if (source) {
    ecoForm.sourceBomId = source.id
  }
  await Promise.all([
    searchEcoSourceBoms(''),
    loadEcoTargetBomCandidates('', selectedIds(ecoForm.targetBomId), normalizeOptionalId(ecoForm.sourceBomId)),
  ])
  ecoFormVisible.value = true
}

async function openEditEco(record: BomEngineeringChangeRecord) {
  ecoFormError.value = ''
  try {
    const detail = await bomApi.engineeringChanges.get(record.id)
    ecoEditingRecord.value = detail
    ecoSourceParentMaterialId.value = detail.parentMaterialId
    resetEcoForm(detail)
    await Promise.all([
      searchEcoSourceBoms(''),
      loadEcoTargetBomCandidates('', selectedIds(detail.targetBomId), detail.sourceBomId),
    ])
    ecoFormVisible.value = true
  } catch (caught) {
    ecoActionError.value = errorMessage(caught)
  }
}

async function changeEcoSource(value: ResourceId | '') {
  ecoForm.sourceBomId = value
  if (!ecoEditingRecord.value) {
    ecoForm.targetBomId = ''
  }
  await loadEcoTargetBomCandidates('', selectedIds(ecoForm.targetBomId), normalizeOptionalId(value))
}

function validateEcoForm(): BomEngineeringChangePayload | null {
  const sourceBomId = normalizeRequiredId(ecoForm.sourceBomId)
  const targetBomId = normalizeRequiredId(ecoForm.targetBomId)
  if (!ecoEditingRecord.value && !ecoForm.ecoNo.trim()) {
    ecoFormError.value = '请先生成工程变更编号'
    return null
  }
  if (sourceBomId === null || targetBomId === null || !ecoForm.effectiveFrom || !ecoForm.changeReason.trim() || !ecoForm.impactScope.trim() || !ecoForm.changeSummary.trim()) {
    ecoFormError.value = '请完整填写来源 BOM、目标 BOM、生效日期、原因、影响范围和变更摘要'
    return null
  }
  if (ecoForm.effectiveTo && ecoForm.effectiveFrom > ecoForm.effectiveTo) {
    ecoFormError.value = '生效日期不能晚于失效日期'
    return null
  }
  const payload: BomEngineeringChangePayload = {
    ...(!ecoEditingRecord.value && ecoForm.ecoNo.trim() ? { ecoNo: ecoForm.ecoNo.trim() } : {}),
    sourceBomId,
    targetBomId,
    effectiveFrom: ecoForm.effectiveFrom,
    effectiveTo: ecoForm.effectiveTo || null,
    changeReason: ecoForm.changeReason.trim(),
    impactScope: ecoForm.impactScope.trim(),
    changeSummary: ecoForm.changeSummary.trim(),
    remark: ecoForm.remark.trim() || null,
  }
  if (ecoEditingRecord.value) {
    payload.version = ecoEditingRecord.value.version
  }
  return payload
}

async function generateEcoCode() {
  if (codeGenerating.value) {
    return
  }
  ecoFormError.value = ''
  codeGenerating.value = true
  try {
    const result = await masterDataApi.codingRules.generate({ objectType: 'BOM_ECO' })
    ecoForm.ecoNo = result.generatedCode
  } catch (caught) {
    ecoFormError.value = errorMessage(caught)
  } finally {
    codeGenerating.value = false
  }
}

async function saveEco() {
  if (ecoFormSubmitting.value) {
    return
  }
  const payload = validateEcoForm()
  if (!payload) {
    return
  }
  ecoFormSubmitting.value = true
  try {
    let savedRecord: BomEngineeringChangeRecord
    if (ecoEditingRecord.value) {
      savedRecord = await bomApi.engineeringChanges.update(ecoEditingRecord.value.id, payload)
    } else {
      savedRecord = await bomApi.engineeringChanges.create(payload)
      if (payload.ecoNo && savedRecord.ecoNo !== payload.ecoNo) {
        ecoFormError.value = '保存返回的工程变更编号与已生成编号不一致'
        return
      }
    }
    ecoFormVisible.value = false
    await loadEcoRecords()
  } catch (caught) {
    ecoFormError.value = errorMessage(caught)
  } finally {
    ecoFormSubmitting.value = false
  }
}

async function applyEco(record: BomEngineeringChangeRecord) {
  ecoApprovalTarget.value = record
  ecoApprovalReason.value = ''
  ecoApprovalError.value = ''
  ecoApprovalVisible.value = true
}

async function submitEcoApproval() {
  if (!ecoApprovalTarget.value || ecoApprovalSubmitting.value) {
    return
  }
  if (!ecoApprovalReason.value.trim()) {
    ecoApprovalError.value = '审批提交原因必填'
    return
  }
  ecoApprovalSubmitting.value = true
  ecoApprovalError.value = ''
  try {
    const approval = await documentPlatformApi.approvals.submitBomEcoApplication(ecoApprovalTarget.value.id, {
      version: ecoApprovalTarget.value.version,
      reason: ecoApprovalReason.value.trim(),
      idempotencyKey: createIdempotencyKey('bom-eco-approval'),
    })
    ecoDetailRecord.value = {
      ...ecoApprovalTarget.value,
      approvalSummary: {
        id: approval.id,
        sceneCode: approval.sceneCode,
        status: approval.status,
        submittedAt: approval.submittedAt,
        version: approval.version,
      },
    }
    ecoDetailVisible.value = true
    ecoApprovalVisible.value = false
    await loadEcoRecords()
  } catch (caught) {
    ecoApprovalError.value = errorMessage(caught)
  } finally {
    ecoApprovalSubmitting.value = false
  }
}

async function cancelEco(record: BomEngineeringChangeRecord) {
  ecoCancelTarget.value = record
  ecoCancelReason.value = ''
  ecoCancelError.value = ''
  ecoCancelVisible.value = true
}

async function submitCancelEco() {
  if (!ecoCancelTarget.value || ecoCancelSubmitting.value) {
    return
  }
  if (!ecoCancelReason.value.trim()) {
    ecoCancelError.value = '取消原因必填'
    return
  }
  ecoCancelSubmitting.value = true
  ecoActionError.value = ''
  try {
    await bomApi.engineeringChanges.cancel(ecoCancelTarget.value.id, {
      version: ecoCancelTarget.value.version,
      reason: ecoCancelReason.value.trim(),
    })
    ecoCancelVisible.value = false
    await loadEcoRecords()
  } catch (caught) {
    ecoCancelError.value = errorMessage(caught)
  } finally {
    ecoCancelSubmitting.value = false
  }
}

function openEcoDetail(record: BomEngineeringChangeRecord) {
  ecoDetailRecord.value = record
  ecoDetailVisible.value = true
}

function scopeTypeLabel(value?: MaterialSubstituteScopeType | null) {
  if (value === 'BOM') return 'BOM 范围'
  if (value === 'PARENT_MATERIAL') return '父项物料范围'
  return '全局'
}

function searchSubstituteMaterials(keyword: string) {
  const retainedIds = [
    ...selectedIds(substituteForm.mainMaterialId),
    ...selectedIds(substituteForm.substituteMaterialId),
    ...selectedIds(substituteFilters.mainMaterialId),
    ...selectedIds(substituteFilters.substituteMaterialId),
  ]
  void loadSubstituteMaterialCandidates(keyword, retainedIds)
}

function searchSubstituteBoms(keyword: string) {
  const retainedIds = [
    ...selectedIds(substituteForm.scopeId),
    ...selectedIds(substituteFilters.scopeId),
  ]
  void loadSubstituteBomCandidates(keyword, retainedIds)
}

function resetSubstituteForm(record?: MaterialSubstituteRecord) {
  Object.assign(substituteForm, {
    mainMaterialId: record?.mainMaterialId ?? '',
    substituteMaterialId: record?.substituteMaterialId ?? '',
    scopeType: record?.scopeType ?? 'GLOBAL',
    scopeId: record?.scopeId ?? '',
    priority: record ? String(record.priority) : '',
    substituteRate: record?.substituteRate ?? '1',
    effectiveFrom: record?.effectiveFrom ?? '',
    effectiveTo: record?.effectiveTo ?? '',
    status: record?.status ?? 'ENABLED',
    remark: record?.remark ?? '',
  })
  substituteFormError.value = ''
}

async function openCreateSubstitute() {
  substituteEditingRecord.value = null
  resetSubstituteForm()
  await Promise.all([
    loadSubstituteMaterialCandidates('', []),
    loadSubstituteBomCandidates('', []),
  ])
  substituteFormVisible.value = true
}

async function openEditSubstitute(record: MaterialSubstituteRecord) {
  substituteFormError.value = ''
  try {
    const detail = await bomApi.substitutes.get(record.id)
    substituteEditingRecord.value = detail
    resetSubstituteForm(detail)
    await Promise.all([
      loadSubstituteMaterialCandidates('', [detail.mainMaterialId, detail.substituteMaterialId]),
      loadSubstituteBomCandidates('', selectedIds(detail.scopeId ?? ''), detail.mainMaterialId),
    ])
    substituteFormVisible.value = true
  } catch (caught) {
    substituteActionError.value = errorMessage(caught)
  }
}

async function changeSubstituteMain(value: ResourceId | '') {
  substituteForm.mainMaterialId = value
  await loadSubstituteBomCandidates('', selectedIds(substituteForm.scopeId), normalizeOptionalId(value))
}

function validateSubstituteForm(): MaterialSubstitutePayload | null {
  const mainMaterialId = normalizeRequiredId(substituteForm.mainMaterialId)
  const substituteMaterialId = normalizeRequiredId(substituteForm.substituteMaterialId)
  if (mainMaterialId === null || substituteMaterialId === null) {
    substituteFormError.value = '请选择主物料和替代物料'
    return null
  }
  if (String(mainMaterialId) === String(substituteMaterialId)) {
    substituteFormError.value = '主物料和替代物料不能相同'
    return null
  }
  const priority = Number(substituteForm.priority)
  if (!Number.isInteger(priority) || priority <= 0) {
    substituteFormError.value = '优先级必须为正整数'
    return null
  }
  const substituteRate = positiveDecimalString(substituteForm.substituteRate)
  if (!substituteRate) {
    substituteFormError.value = '替代比例必须大于 0'
    return null
  }
  if (substituteForm.effectiveFrom && substituteForm.effectiveTo && substituteForm.effectiveFrom > substituteForm.effectiveTo) {
    substituteFormError.value = '开始日期不能晚于结束日期'
    return null
  }
  const payload: MaterialSubstitutePayload = {
    mainMaterialId,
    substituteMaterialId,
    scopeType: substituteForm.scopeType,
    scopeId: normalizeOptionalId(substituteForm.scopeId) ?? null,
    priority,
    substituteRate,
    effectiveFrom: substituteForm.effectiveFrom || null,
    effectiveTo: substituteForm.effectiveTo || null,
    status: substituteForm.status,
    remark: substituteForm.remark.trim() || null,
  }
  if (substituteEditingRecord.value) {
    payload.version = substituteEditingRecord.value.version
  }
  return payload
}

async function saveSubstitute() {
  if (substituteFormSubmitting.value) {
    return
  }
  const payload = validateSubstituteForm()
  if (!payload) {
    return
  }
  substituteFormSubmitting.value = true
  try {
    if (substituteEditingRecord.value) {
      await bomApi.substitutes.update(substituteEditingRecord.value.id, payload)
    } else {
      await bomApi.substitutes.create(payload)
    }
    substituteFormVisible.value = false
    await loadSubstituteRecords()
  } catch (caught) {
    substituteFormError.value = errorMessage(caught)
  } finally {
    substituteFormSubmitting.value = false
  }
}

async function disableSubstitute(record: MaterialSubstituteRecord) {
  if (!(await confirmAction(`确认停用替代料“${record.mainMaterialCode} -> ${record.substituteMaterialCode}”？`))) {
    return
  }
  substituteActionError.value = ''
  try {
    await bomApi.substitutes.disable(record.id, { version: record.version })
    await loadSubstituteRecords()
  } catch (caught) {
    substituteActionError.value = errorMessage(caught)
  }
}

async function enableSubstitute(record: MaterialSubstituteRecord) {
  if (!(await confirmAction(`确认启用替代料“${record.mainMaterialCode} -> ${record.substituteMaterialCode}”？`))) {
    return
  }
  substituteActionError.value = ''
  try {
    await bomApi.substitutes.enable(record.id, { version: record.version })
    await loadSubstituteRecords()
  } catch (caught) {
    substituteActionError.value = errorMessage(caught)
  }
}

function openSubstituteDetail(record: MaterialSubstituteRecord) {
  substituteDetailRecord.value = record
  substituteDetailVisible.value = true
}

function formatDate(value?: string | null) {
  return value || '-'
}

function formatDateTime(value?: string | null) {
  return value ? value.replace('T', ' ').slice(0, 16) : '-'
}

function effectiveState(record: Pick<BomSummaryRecord, 'status' | 'effectiveFrom' | 'effectiveTo'>) {
  return bomEffectiveState(record.status, record.effectiveFrom, record.effectiveTo, effectiveReferenceDate.value)
}

function effectiveStateLabel(record: Pick<BomSummaryRecord, 'status' | 'effectiveFrom' | 'effectiveTo'>) {
  return bomEffectiveStateLabel(effectiveState(record))
}

function effectiveStateTagType(record: Pick<BomSummaryRecord, 'status' | 'effectiveFrom' | 'effectiveTo'>) {
  return bomEffectiveStateTagType(effectiveState(record))
}

function formatBomSnapshot(snapshot?: BomStateSnapshot | null) {
  if (!snapshot) {
    return '-'
  }
  return `${snapshot.bomCode || '-'} / ${snapshot.versionCode || '-'} | ${snapshot.status || '-'} | ${formatDate(snapshot.effectiveFrom)} 至 ${formatDate(snapshot.effectiveTo)}`
}

function formatBomVersion(code?: string | null, version?: string | null) {
  return `${code || '-'} / ${version || '-'}`
}

function historyRelationLabel(relationType: BomHistoryRelationRecord['relationType']) {
  return relationType === 'SOURCE' ? '作为来源' : '作为目标'
}

function engineeringChangeStatusLabel(status: BomEngineeringChangeStatus) {
  if (status === 'APPLIED') {
    return '已应用'
  }
  if (status === 'CANCELLED') {
    return '已取消'
  }
  return '草稿'
}

onMounted(() => {
  void loadReferences()
  void loadRecords()
})
</script>

<template>
  <MasterDataTableView title="BOM 管理" description="维护 BOM 版本、工程变更和替代料治理关系。">
    <template #actions>
      <a
        v-if="activeTab === 'versions' && canHistoryImport"
        data-test="bom-history-import-entry"
        class="inline-action-link"
        href="/platform/history-imports?adapterCode=BOM_DRAFT_V1"
      >
        历史导入
      </a>
      <el-button v-if="activeTab === 'versions' && canBomImport" data-test="download-bom-draft-template" @click="downloadBomDraftTemplate">
        下载草稿模板
      </el-button>
      <el-button v-if="activeTab === 'versions' && canBomImport" data-test="open-bom-draft-import" @click="openBomDraftImport">
        导入 BOM 草稿
      </el-button>
      <el-button v-if="activeTab === 'versions' && canCreate" data-test="create-bom" type="primary" @click="openCreate">
        新增 BOM
      </el-button>
      <el-button v-if="activeTab === 'eco' && canEcoCreate" data-test="create-bom-eco" type="primary" @click="openCreateEco()">
        新增工程变更
      </el-button>
      <el-button
        v-if="activeTab === 'substitutes' && canSubstituteCreate"
        data-test="create-substitute"
        type="primary"
        @click="openCreateSubstitute"
      >
        新增替代料
      </el-button>
    </template>

    <template #filters>
      <el-form v-if="activeTab === 'versions'" class="query-form" inline>
        <el-form-item label="关键词">
          <el-input v-model="filters.keyword" name="bom-keyword" clearable placeholder="BOM 编码、版本或物料" />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="filters.status" data-test="filter-bom-status" clearable placeholder="全部状态">
            <el-option label="草稿" value="DRAFT" />
            <el-option label="已发布" value="ENABLED" />
            <el-option label="停用" value="DISABLED" />
          </el-select>
        </el-form-item>
        <el-form-item label="父项物料">
          <el-select
            v-model="filters.parentMaterialId"
            data-test="filter-bom-parent-material-id"
            filterable
            remote
            clearable
            :remote-method="searchFilterBomParents"
            placeholder="全部父项"
          >
            <el-option
              v-for="material in bomMaterialCandidates"
              :key="material.id"
              :label="candidateLabel(material)"
              :value="material.id"
              :disabled="Boolean(material.disabled)"
            >
              <span>{{ material.code }} {{ material.name }}</span>
              <span class="line-option-meta">{{ material.disabledReason || material.summary || material.status }}</span>
            </el-option>
          </el-select>
        </el-form-item>
        <el-form-item label="有效日期">
          <el-date-picker
            v-model="filters.effectiveDate"
            name="bom-effective-date"
            value-on-clear=""
            type="date"
            format="YYYY-MM-DD"
            value-format="YYYY-MM-DD"
            placeholder="选择日期"
          />
        </el-form-item>
        <el-form-item label="历史版本">
          <el-select v-model="filters.includeHistory" data-test="filter-bom-include-history" placeholder="不含停用">
            <el-option label="不含停用" :value="false" />
            <el-option label="包含历史" :value="true" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button data-test="search-bom" type="primary" @click="search">查询</el-button>
          <el-button data-test="reset-bom" @click="resetSearch">重置</el-button>
        </el-form-item>
      </el-form>
      <el-form v-else-if="activeTab === 'eco'" class="query-form" inline>
        <el-form-item label="关键词">
          <el-input v-model="ecoFilters.keyword" clearable placeholder="变更编号或摘要" />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="ecoFilters.status" clearable placeholder="全部状态">
            <el-option label="草稿" value="DRAFT" />
            <el-option label="已应用" value="APPLIED" />
            <el-option label="已取消" value="CANCELLED" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="loadEcoRecords">查询</el-button>
        </el-form-item>
      </el-form>
      <el-form v-else class="query-form" inline>
        <el-form-item label="关键词">
          <el-input v-model="substituteFilters.keyword" clearable placeholder="物料或 BOM" />
        </el-form-item>
        <el-form-item label="主物料">
          <el-select
            v-model="substituteFilters.mainMaterialId"
            data-test="filter-substitute-main-material-id"
            filterable
            remote
            clearable
            :remote-method="searchSubstituteMaterials"
            placeholder="全部主物料"
          >
            <el-option
              v-for="material in substituteMaterialCandidates"
              :key="material.id"
              :label="candidateLabel(material)"
              :value="material.id"
              :disabled="Boolean(material.disabled)"
            >
              <span>{{ material.code }} {{ material.name }}</span>
              <span class="line-option-meta">{{ material.disabledReason || material.summary || material.status }}</span>
            </el-option>
          </el-select>
        </el-form-item>
        <el-form-item label="替代物料">
          <el-select
            v-model="substituteFilters.substituteMaterialId"
            data-test="filter-substitute-material-id"
            filterable
            remote
            clearable
            :remote-method="searchSubstituteMaterials"
            placeholder="全部替代物料"
          >
            <el-option
              v-for="material in substituteMaterialCandidates"
              :key="material.id"
              :label="candidateLabel(material)"
              :value="material.id"
              :disabled="Boolean(material.disabled)"
            >
              <span>{{ material.code }} {{ material.name }}</span>
              <span class="line-option-meta">{{ material.disabledReason || material.summary || material.status }}</span>
            </el-option>
          </el-select>
        </el-form-item>
        <el-form-item label="范围">
          <el-select v-model="substituteFilters.scopeType" data-test="filter-substitute-scope-type" clearable placeholder="全部范围">
            <el-option label="全局" value="GLOBAL" />
            <el-option label="父项物料范围" value="PARENT_MATERIAL" />
            <el-option label="BOM 范围" value="BOM" />
          </el-select>
        </el-form-item>
        <el-form-item label="范围对象">
          <el-select
            v-model="substituteFilters.scopeId"
            data-test="filter-substitute-scope-id"
            filterable
            remote
            clearable
            :remote-method="searchSubstituteBoms"
            placeholder="全部对象"
          >
            <el-option
              v-for="record in substituteBomCandidates"
              :key="record.id"
              :label="candidateLabel(record)"
              :value="record.id"
              :disabled="Boolean(record.disabled)"
            >
              <span>{{ record.code }} {{ record.name }}</span>
              <span class="line-option-meta">{{ record.disabledReason || record.summary || record.status }}</span>
            </el-option>
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="substituteFilters.status" clearable placeholder="全部状态">
            <el-option label="启用" value="ENABLED" />
            <el-option label="停用" value="DISABLED" />
          </el-select>
        </el-form-item>
        <el-form-item label="有效日期">
          <el-date-picker
            v-model="substituteFilters.effectiveDate"
            name="substitute-effective-date"
            value-on-clear=""
            type="date"
            format="YYYY-MM-DD"
            value-format="YYYY-MM-DD"
            placeholder="选择日期"
          />
        </el-form-item>
        <el-form-item>
          <el-button data-test="search-substitute" type="primary" @click="loadSubstituteRecords">查询</el-button>
        </el-form-item>
      </el-form>
    </template>

    <template #alerts>
      <el-alert v-if="error" class="state-alert" type="error" :title="error" :closable="false" />
      <el-alert v-if="referenceError" class="state-alert" type="error" :title="referenceError" :closable="false" />
      <el-alert v-if="actionError" class="state-alert" type="error" :title="actionError" :closable="false" />
      <el-alert v-if="ecoError" class="state-alert" type="error" :title="ecoError" :closable="false" />
      <el-alert v-if="ecoActionError" class="state-alert" type="error" :title="ecoActionError" :closable="false" />
      <el-alert v-if="substituteError" class="state-alert" type="error" :title="substituteError" :closable="false" />
      <el-alert v-if="substituteActionError" class="state-alert" type="error" :title="substituteActionError" :closable="false" />
      <el-alert
        v-if="latestDocumentTask"
        class="state-alert"
        type="success"
        :title="`已创建文档任务 ${latestDocumentTask.taskNo}`"
        :closable="false"
      />
      <el-alert v-if="loading || referenceLoading" class="state-alert" type="info" title="BOM 数据加载中" :closable="false" />
    </template>

    <el-tabs :model-value="activeTab" class="bom-tabs" @tab-change="handleTabChange">
      <el-tab-pane label="BOM 版本" name="versions">
        <template #label><button data-test="bom-tab-versions" class="tab-button" type="button" @click="switchTab('versions')">BOM 版本</button></template>
      </el-tab-pane>
      <el-tab-pane label="工程变更" name="eco">
        <template #label><button data-test="bom-tab-eco" class="tab-button" type="button" @click="switchTab('eco')">工程变更</button></template>
      </el-tab-pane>
      <el-tab-pane label="替代料" name="substitutes">
        <template #label><button data-test="bom-tab-substitutes" class="tab-button" type="button" @click="switchTab('substitutes')">替代料</button></template>
      </el-tab-pane>
    </el-tabs>

    <template v-if="activeTab === 'versions'">
      <div class="table-scroll">
        <el-table :data="records" :empty-text="loading ? '加载中' : '暂无 BOM 数据'" stripe>
          <el-table-column prop="bomCode" label="BOM 编码" width="170" show-overflow-tooltip />
          <el-table-column label="状态" width="90"><template #default="{ row }"><BomStatusTag :status="row.status" /></template></el-table-column>
          <el-table-column label="时效状态" width="110">
            <template #default="{ row }">
              <el-tag :type="effectiveStateTagType(row)" size="small">{{ effectiveStateLabel(row) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="有效期" width="160">
            <template #default="{ row }">{{ formatDate(row.effectiveFrom) }} 至 {{ formatDate(row.effectiveTo) }}</template>
          </el-table-column>
          <el-table-column prop="versionCode" label="版本" width="90" show-overflow-tooltip />
          <el-table-column label="父项物料" width="190" show-overflow-tooltip>
            <template #default="{ row }">{{ row.parentMaterialCode }} {{ row.parentMaterialName }}</template>
          </el-table-column>
          <el-table-column prop="name" label="名称" width="180" show-overflow-tooltip />
          <el-table-column prop="baseQuantity" label="基准数量" width="100" />
          <el-table-column prop="baseUnitName" label="单位" width="120" show-overflow-tooltip />
          <el-table-column prop="itemCount" label="明细数" width="80" />
          <el-table-column label="更新时间" width="150"><template #default="{ row }">{{ formatDateTime(row.updatedAt) }}</template></el-table-column>
          <el-table-column label="操作" fixed="right" min-width="340">
            <template #default="{ row }">
              <el-button size="small" text data-test="view-bom" @click="openDetail(row)">详情</el-button>
              <el-button v-if="canUpdate && row.status === 'DRAFT'" size="small" text data-test="edit-bom" @click="openEdit(row)">编辑</el-button>
              <el-button v-if="canCopy" size="small" text data-test="copy-bom" @click="openCopy(row)">复制新版本</el-button>
              <el-button v-if="canEnable && row.status === 'DRAFT'" size="small" text type="success" data-test="publish-bom" :disabled="actionLoading" @click="publishRecord(row)">发布</el-button>
              <el-button v-if="canDisable && row.status !== 'DISABLED'" size="small" text type="danger" data-test="disable-bom" :disabled="actionLoading" @click="disableRecord(row)">停用</el-button>
              <el-button v-if="row.status === 'DRAFT' && canBomExport" size="small" text data-test="export-bom-draft" :disabled="actionLoading" @click="exportBomDraft(row)">导出草稿</el-button>
              <el-button v-if="row.status === 'ENABLED' && canEcoCreate" size="small" text data-test="create-eco-from-bom" @click="openCreateEco(row)">创建工程变更</el-button>
            </template>
          </el-table-column>
        </el-table>
      </div>
      <el-pagination class="table-pagination" layout="total, sizes, prev, pager, next" :page-sizes="[10, 20, 50, 100]" :total="pagination.total" :page-size="pagination.pageSize" :current-page="pagination.page" @current-change="changePage" @size-change="changePageSize" />
    </template>

    <template v-else-if="activeTab === 'eco'">
      <div class="table-scroll">
        <el-table :data="ecoRecords" :empty-text="ecoLoading ? '加载中' : '暂无工程变更记录'" stripe>
          <el-table-column prop="ecoNo" label="变更编号" min-width="150" show-overflow-tooltip />
          <el-table-column label="来源 BOM" min-width="170"><template #default="{ row }">{{ row.sourceBomCode }} {{ row.sourceVersionCode }}</template></el-table-column>
          <el-table-column label="目标 BOM" min-width="170"><template #default="{ row }">{{ row.targetBomCode }} {{ row.targetVersionCode }}</template></el-table-column>
          <el-table-column label="父项物料" min-width="170"><template #default="{ row }">{{ row.parentMaterialCode }} {{ row.parentMaterialName }}</template></el-table-column>
          <el-table-column prop="effectiveFrom" label="生效日期" min-width="110" />
          <el-table-column prop="changeSummary" label="变更摘要" min-width="220" show-overflow-tooltip />
          <el-table-column label="状态" min-width="90"><template #default="{ row }">{{ row.status === 'APPLIED' ? '已应用' : row.status === 'CANCELLED' ? '已取消' : '草稿' }}</template></el-table-column>
          <el-table-column prop="appliedBy" label="应用人" min-width="100" />
          <el-table-column label="应用时间" min-width="150"><template #default="{ row }">{{ formatDateTime(row.appliedAt) }}</template></el-table-column>
          <el-table-column label="操作" fixed="right" min-width="220">
            <template #default="{ row }">
              <el-button size="small" text @click="openEcoDetail(row)">详情</el-button>
              <el-button v-if="canEcoUpdate && row.status === 'DRAFT'" size="small" text data-test="edit-bom-eco" @click="openEditEco(row)">编辑</el-button>
              <el-button v-if="canEcoApply && row.status === 'DRAFT' && row.approvalSummary?.status !== 'SUBMITTED'" size="small" text type="success" data-test="apply-bom-eco" @click="applyEco(row)">提交应用审批</el-button>
              <el-button v-if="canEcoCancel && row.status === 'DRAFT'" size="small" text type="danger" data-test="cancel-bom-eco" @click="cancelEco(row)">取消</el-button>
            </template>
          </el-table-column>
        </el-table>
      </div>
      <el-pagination class="table-pagination" layout="total, sizes, prev, pager, next" :page-sizes="[10, 20, 50, 100]" :total="ecoPagination.total" :page-size="ecoPagination.pageSize" :current-page="ecoPagination.page" @current-change="changeEcoPage" @size-change="changeEcoPageSize" />
    </template>

    <template v-else>
      <el-alert class="state-alert" type="info" title="替代料仅供人工识别和后续阶段引用，不自动改 BOM、采购、领料或缺料建议。" :closable="false" />
      <div class="table-scroll">
        <el-table :data="substituteRecords" :empty-text="substituteLoading ? '加载中' : '暂无替代料记录'" stripe>
          <el-table-column label="主物料" min-width="190"><template #default="{ row }">{{ row.mainMaterialCode }} {{ row.mainMaterialName }}</template></el-table-column>
          <el-table-column label="替代物料" min-width="190"><template #default="{ row }">{{ row.substituteMaterialCode }} {{ row.substituteMaterialName }}</template></el-table-column>
          <el-table-column label="适用范围" min-width="140"><template #default="{ row }">{{ scopeTypeLabel(row.scopeType) }}</template></el-table-column>
          <el-table-column prop="priority" label="优先级" min-width="90" />
          <el-table-column prop="substituteRate" label="替代比例" min-width="110" />
          <el-table-column label="有效期" min-width="180"><template #default="{ row }">{{ formatDate(row.effectiveFrom) }} 至 {{ formatDate(row.effectiveTo) }}</template></el-table-column>
          <el-table-column label="状态" min-width="90">
            <template #default="{ row }">
              <el-tag :type="row.status === 'ENABLED' ? 'success' : 'info'" size="small">
                {{ row.status === 'ENABLED' ? '启用' : '停用' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" fixed="right" min-width="210">
            <template #default="{ row }">
              <el-button size="small" text @click="openSubstituteDetail(row)">详情</el-button>
              <el-button v-if="canSubstituteUpdate" size="small" text data-test="edit-substitute" @click="openEditSubstitute(row)">编辑</el-button>
              <el-button v-if="canSubstituteDisable && row.status === 'ENABLED'" size="small" text type="danger" data-test="disable-substitute" @click="disableSubstitute(row)">停用</el-button>
              <el-button v-if="canSubstituteEnable && row.status === 'DISABLED'" size="small" text type="success" data-test="enable-substitute" @click="enableSubstitute(row)">启用</el-button>
            </template>
          </el-table-column>
        </el-table>
      </div>
      <el-pagination class="table-pagination" layout="total, sizes, prev, pager, next" :page-sizes="[10, 20, 50, 100]" :total="substitutePagination.total" :page-size="substitutePagination.pageSize" :current-page="substitutePagination.page" @current-change="changeSubstitutePage" @size-change="changeSubstitutePageSize" />
    </template>

    <el-dialog v-model="formVisible" :title="editingRecord ? '编辑 BOM' : '新增 BOM'" width="min(1120px, calc(100vw - 48px))">
      <el-alert v-if="formError" class="form-alert" type="error" :title="formError" :closable="false" />
      <el-form label-position="top">
        <div class="bom-form-grid">
          <el-form-item label="BOM 编码">
            <div class="field-with-action">
              <el-input v-model="form.bomCode" name="bom-code" :disabled="Boolean(editingRecord)" />
              <el-button v-if="!editingRecord && canGenerateCode" data-test="generate-bom-code" :loading="codeGenerating" :disabled="codeGenerating" @click="generateBomCode">生成编码</el-button>
            </div>
          </el-form-item>
          <el-form-item label="版本"><el-input v-model="form.versionCode" name="bom-version-code" /></el-form-item>
          <el-form-item label="BOM 名称"><el-input v-model="form.name" name="bom-name" /></el-form-item>
          <el-form-item label="父项物料">
            <el-select
              v-model="form.parentMaterialId"
              data-test="bom-parent-material-id"
              filterable
              remote
              :remote-method="searchBomMaterials"
              placeholder="请选择父项物料"
              style="width: 100%"
            >
              <el-option
                v-for="material in bomMaterialCandidates"
                :key="material.id"
                :label="candidateLabel(material)"
                :value="material.id"
                :disabled="Boolean(material.disabled)"
              >
                <span>{{ material.code }} {{ material.name }}</span><span class="line-option-meta">{{ material.disabledReason || material.summary || material.status }}</span>
              </el-option>
            </el-select>
          </el-form-item>
          <el-form-item label="基准数量"><el-input v-model="form.baseQuantity" name="bom-base-quantity" /></el-form-item>
          <el-form-item label="基准单位">
            <el-select
              v-model="form.baseUnitId"
              data-test="bom-base-unit-id"
              filterable
              remote
              clearable
              :remote-method="searchBomUnits"
              placeholder="默认父项单位"
              style="width: 100%"
            >
              <el-option
                v-for="unit in bomUnitCandidates"
                :key="unit.id"
                :label="candidateLabel(unit)"
                :value="unit.id"
                :disabled="Boolean(unit.disabled)"
              >
                <span>{{ unit.code }} {{ unit.name }}</span><span class="line-option-meta">{{ unit.disabledReason || unit.summary || unit.status }}</span>
              </el-option>
            </el-select>
          </el-form-item>
          <el-form-item label="生效日期"><el-date-picker value-on-clear="" type="date" format="YYYY-MM-DD" value-format="YYYY-MM-DD" v-model="form.effectiveFrom" name="bom-effective-from" placeholder="选择日期" /></el-form-item>
          <el-form-item label="失效日期"><el-date-picker value-on-clear="" type="date" format="YYYY-MM-DD" value-format="YYYY-MM-DD" v-model="form.effectiveTo" name="bom-effective-to" placeholder="选择日期" /></el-form-item>
        </div>
        <el-form-item label="备注"><el-input v-model="form.remark" name="bom-remark" type="textarea" :rows="2" /></el-form-item>
        <el-form-item label="BOM 明细">
          <BomLineEditor
            v-model:lines="lines"
            :materials="bomMaterialCandidates"
            :units="bomUnitCandidates"
            :load-material-candidates="loadBomMaterialCandidates"
            :load-unit-candidates="loadBomUnitCandidates"
            :errors="lineErrors"
            :read-only="false"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="formVisible = false">取消</el-button>
        <el-button data-test="submit-bom" type="primary" :loading="formSubmitting" :disabled="formSubmitting" @click="saveRecord">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="copyVisible" title="复制 BOM 版本" width="520px">
      <el-alert v-if="copyError" class="form-alert" type="error" :title="copyError" :closable="false" />
      <el-form label-position="top">
        <el-form-item label="新 BOM 编码"><el-input v-model="copyForm.bomCode" name="copy-bom-code" /></el-form-item>
        <el-form-item label="新版本"><el-input v-model="copyForm.versionCode" name="copy-bom-version-code" /></el-form-item>
        <el-form-item label="新名称"><el-input v-model="copyForm.name" name="copy-bom-name" /></el-form-item>
      </el-form>
      <template #footer><el-button @click="copyVisible = false">取消</el-button><el-button data-test="submit-copy-bom" type="primary" :loading="copySubmitting" :disabled="copySubmitting" @click="submitCopy">保存</el-button></template>
    </el-dialog>

    <el-dialog v-model="bomDraftImportVisible" title="导入 BOM 草稿" width="min(620px, 94vw)">
      <el-alert v-if="bomDraftImportError" class="form-alert" type="error" :title="bomDraftImportError" :closable="false" />
      <el-alert
        class="form-alert"
        type="info"
        title="导入仅创建或更新草稿 BOM，不发布、不应用 ECO、不改写工单快照；行字段覆盖子项物料、业务单位、业务数量、损耗率和备注。仓库为保留列，当前阶段必须留空；非空将校验失败。"
        :closable="false"
      />
      <el-form label-position="top">
        <el-form-item label="导入模式">
          <el-select v-model="bomDraftImportMode" data-test="bom-draft-import-mode" style="width: 100%">
            <el-option label="创建草稿" value="CREATE" />
            <el-option label="更新草稿" value="UPDATE_DRAFT" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="bomDraftImportMode === 'UPDATE_DRAFT'" label="目标草稿">
          <el-select v-model="bomDraftImportTargetId" data-test="bom-draft-import-target" style="width: 100%">
            <el-option
              v-for="record in records.filter((item) => item.status === 'DRAFT')"
              :key="record.id"
              :label="`${record.bomCode} / ${record.versionCode}`"
              :value="record.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="XLSX 文件">
          <input data-test="bom-draft-import-file" type="file" accept=".xlsx" @change="selectBomDraftImportFile">
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="bomDraftImportVisible = false">取消</el-button>
        <el-button
          data-test="submit-bom-draft-import"
          type="primary"
          :loading="bomDraftImportSubmitting"
          :disabled="bomDraftImportSubmitting"
          @click="submitBomDraftImport"
        >
          上传并校验
        </el-button>
      </template>
    </el-dialog>

    <el-drawer v-model="detailVisible" title="BOM 详情" size="min(680px, 92vw)">
      <el-alert v-if="detailRecord?.status === 'ENABLED'" class="form-alert" type="warning" title="已发布 BOM 不允许普通编辑，请复制新版本或创建工程变更。" :closable="false" />
      <dl v-if="detailRecord" class="bom-detail-list">
        <dt>BOM 编码</dt><dd>{{ detailRecord.bomCode }}</dd>
        <dt>父项物料</dt><dd>{{ detailRecord.parentMaterialCode }} {{ detailRecord.parentMaterialName }}</dd>
        <dt>版本</dt><dd>{{ detailRecord.versionCode }}</dd>
        <dt>发布状态</dt><dd><BomStatusTag :status="detailRecord.status" /></dd>
        <dt>时效状态</dt>
        <dd>
          <el-tag :type="effectiveStateTagType(detailRecord)" size="small">{{ effectiveStateLabel(detailRecord) }}</el-tag>
        </dd>
        <dt>基准数量</dt><dd>{{ detailRecord.baseQuantity }} {{ detailRecord.baseUnitName }}</dd>
        <dt>有效期</dt><dd>{{ formatDate(detailRecord.effectiveFrom) }} 至 {{ formatDate(detailRecord.effectiveTo) }}</dd>
        <dt>历史关系</dt>
        <dd>
          <div v-if="detailRecord.historyRelations.length" class="history-relation-list">
            <article v-for="relation in detailRecord.historyRelations" :key="`${relation.ecoId}-${relation.relationType}`" class="history-relation-card">
              <div class="history-relation-header">
                <strong>{{ relation.ecoNo }}</strong>
                <el-tag size="small">{{ historyRelationLabel(relation.relationType) }}</el-tag>
                <el-tag size="small" type="success">{{ engineeringChangeStatusLabel(relation.status) }}</el-tag>
              </div>
              <dl class="history-relation-detail">
                <dt>来源版本</dt><dd>{{ formatBomVersion(relation.sourceBomCode, relation.sourceVersionCode) }}</dd>
                <dt>目标版本</dt><dd>{{ formatBomVersion(relation.targetBomCode, relation.targetVersionCode) }}</dd>
                <dt>生效信息</dt><dd>{{ formatDate(relation.effectiveFrom) }} 至 {{ formatDate(relation.effectiveTo) }}</dd>
                <dt>应用信息</dt><dd>{{ relation.appliedBy || '-' }} / {{ formatDateTime(relation.appliedAt) }}</dd>
              </dl>
            </article>
          </div>
          <span v-else>暂无工程变更关系</span>
        </dd>
        <dt>创建时间</dt><dd>{{ formatDateTime(detailRecord.createdAt) }}</dd>
        <dt>更新时间</dt><dd>{{ formatDateTime(detailRecord.updatedAt) }}</dd>
        <dt>工程变更</dt>
        <dd>
          <el-button
            v-if="detailRecord.status === 'ENABLED' && canEcoCreate"
            data-test="detail-create-eco"
            size="small"
            @click="openCreateEco(detailRecord)"
          >
            创建工程变更
          </el-button>
          <span v-else>当前状态不可创建工程变更</span>
        </dd>
        <dt>备注</dt><dd>{{ detailRecord.remark || '未填写' }}</dd>
      </dl>
      <div class="table-scroll">
        <el-table v-if="detailRecord" :data="detailRecord.items" stripe>
          <el-table-column prop="lineNo" label="行号" width="80" />
          <el-table-column label="子项物料" min-width="180"><template #default="{ row }">{{ row.childMaterialCode }} {{ row.childMaterialName }}</template></el-table-column>
          <el-table-column prop="businessQuantity" label="业务数量" width="110" />
          <el-table-column prop="businessUnitName" label="业务单位" width="100" />
          <el-table-column prop="baseQuantity" label="基本数量" width="110" />
          <el-table-column prop="baseUnitName" label="基本单位" width="100" />
          <el-table-column prop="lossRate" label="损耗率" width="90" />
          <el-table-column prop="quantityBasis" label="数量口径" width="140" />
        </el-table>
      </div>
    </el-drawer>

    <el-dialog v-model="ecoFormVisible" :title="ecoEditingRecord ? '编辑工程变更' : '新增工程变更'" width="min(760px, 96vw)">
      <el-alert v-if="ecoFormError" class="form-alert" type="error" :title="ecoFormError" :closable="false" />
      <el-form label-position="top">
        <div class="bom-form-grid">
          <el-form-item label="变更编号">
            <div class="field-with-action">
              <el-input v-model="ecoForm.ecoNo" name="eco-no" :disabled="Boolean(ecoEditingRecord)" />
              <el-button
                v-if="!ecoEditingRecord && canGenerateCode"
                data-test="generate-eco-code"
                :loading="codeGenerating"
                :disabled="codeGenerating"
                @click="generateEcoCode"
              >
                生成编码
              </el-button>
            </div>
          </el-form-item>
          <el-form-item label="来源 BOM">
            <el-select
              :model-value="ecoForm.sourceBomId"
              data-test="eco-source-bom-id"
              filterable
              remote
              :remote-method="searchEcoSourceBoms"
              style="width: 100%"
              :disabled="Boolean(ecoEditingRecord)"
              @visible-change="refreshEcoSourceBoms"
              @update:model-value="changeEcoSource"
            >
              <el-option
                v-for="record in ecoSourceBomCandidates"
                :key="record.id"
                :label="candidateLabel(record)"
                :value="record.id"
                :disabled="Boolean(record.disabled)"
              >
                <span>{{ record.code }} {{ record.name }}</span>
                <span class="line-option-meta">{{ record.disabledReason || record.summary || record.status }}</span>
              </el-option>
            </el-select>
          </el-form-item>
          <el-form-item label="目标 BOM">
            <el-select
              v-model="ecoForm.targetBomId"
              data-test="eco-target-bom-id"
              filterable
              remote
              :remote-method="(keyword: string) => loadEcoTargetBomCandidates(keyword, selectedIds(ecoForm.targetBomId))"
              style="width: 100%"
            >
              <el-option
                v-for="record in ecoTargetBomCandidates"
                :key="record.id"
                :label="candidateLabel(record)"
                :value="record.id"
                :disabled="Boolean(record.disabled)"
              >
                <span>{{ record.code }} {{ record.name }}</span>
                <span class="line-option-meta">{{ record.disabledReason || record.summary || record.status }}</span>
              </el-option>
            </el-select>
          </el-form-item>
          <el-form-item label="生效日期"><el-date-picker v-model="ecoForm.effectiveFrom" name="eco-effective-from" value-on-clear="" type="date" format="YYYY-MM-DD" value-format="YYYY-MM-DD" /></el-form-item>
          <el-form-item label="失效日期"><el-date-picker v-model="ecoForm.effectiveTo" name="eco-effective-to" value-on-clear="" type="date" format="YYYY-MM-DD" value-format="YYYY-MM-DD" /></el-form-item>
        </div>
        <el-form-item label="变更原因"><el-input v-model="ecoForm.changeReason" name="eco-change-reason" /></el-form-item>
        <el-form-item label="影响范围"><el-input v-model="ecoForm.impactScope" name="eco-impact-scope" /></el-form-item>
        <el-form-item label="变更摘要"><el-input v-model="ecoForm.changeSummary" name="eco-change-summary" type="textarea" :rows="3" maxlength="500" show-word-limit /></el-form-item>
        <el-form-item label="备注"><el-input v-model="ecoForm.remark" type="textarea" :rows="2" /></el-form-item>
      </el-form>
      <template #footer><el-button @click="ecoFormVisible = false">取消</el-button><el-button data-test="submit-bom-eco" type="primary" :loading="ecoFormSubmitting" :disabled="ecoFormSubmitting" @click="saveEco">保存</el-button></template>
    </el-dialog>

    <el-drawer v-model="ecoDetailVisible" title="工程变更详情" size="min(620px, 92vw)">
      <dl v-if="ecoDetailRecord" class="bom-detail-list">
        <dt>变更编号</dt><dd>{{ ecoDetailRecord.ecoNo }}</dd>
        <dt>来源 BOM</dt><dd>{{ ecoDetailRecord.sourceBomCode }} {{ ecoDetailRecord.sourceVersionCode }}</dd>
        <dt>目标 BOM</dt><dd>{{ ecoDetailRecord.targetBomCode }} {{ ecoDetailRecord.targetVersionCode }}</dd>
        <dt>变更摘要</dt><dd>{{ ecoDetailRecord.changeSummary }}</dd>
        <dt>状态</dt><dd>{{ ecoDetailRecord.status === 'APPLIED' ? '已应用' : ecoDetailRecord.status === 'CANCELLED' ? '已取消' : '草稿' }}</dd>
      </dl>
      <section v-if="ecoDetailRecord && 'sourceBomBefore' in ecoDetailRecord" class="eco-apply-result">
        <h3>应用结果</h3>
        <dl class="bom-detail-list">
          <dt>来源变更前</dt><dd>{{ formatBomSnapshot(ecoDetailRecord.sourceBomBefore) }}</dd>
          <dt>来源变更后</dt><dd>{{ formatBomSnapshot(ecoDetailRecord.sourceBomAfter) }}</dd>
          <dt>目标变更前</dt><dd>{{ formatBomSnapshot(ecoDetailRecord.targetBomBefore) }}</dd>
          <dt>目标变更后</dt><dd>{{ formatBomSnapshot(ecoDetailRecord.targetBomAfter) }}</dd>
          <dt>应用人</dt><dd>{{ ecoDetailRecord.appliedBy || '-' }}</dd>
        <dt>应用时间</dt><dd>{{ formatDateTime(ecoDetailRecord.appliedAt) }}</dd>
      </dl>
      </section>
      <template v-if="ecoDetailRecord">
        <ApprovalStatusPanel
          :approval-instance-id="ecoDetailRecord.approvalSummary?.id"
          :approval-status="ecoDetailRecord.approvalSummary?.status"
          :submitted-at="ecoDetailRecord.approvalSummary?.submittedAt"
        />
        <AttachmentPanel
          title="ECO 附件"
          object-type="BOM_ENGINEERING_CHANGE"
          :object-id="ecoDetailRecord.id"
          :readonly="ecoDetailRecord.approvalSummary?.status === 'SUBMITTED'"
        />
        <PrintAction
          title="BOM ECO 应用审批单"
          scene-code="BOM_ECO_APPLICATION"
          :approval-instance-id="ecoDetailRecord.approvalSummary?.id"
        />
      </template>
    </el-drawer>

    <el-dialog v-model="ecoApprovalVisible" title="提交应用审批" width="min(520px, 94vw)">
      <el-alert v-if="ecoApprovalError" class="form-alert" type="error" :title="ecoApprovalError" :closable="false" />
      <p v-if="ecoApprovalTarget">确认提交工程变更“{{ ecoApprovalTarget.ecoNo }}”应用审批？</p>
      <el-input
        v-model="ecoApprovalReason"
        data-test="eco-approval-submit-reason"
        type="textarea"
        :rows="3"
        maxlength="200"
        show-word-limit
        placeholder="请填写审批提交原因（必填）"
      />
      <template #footer>
        <el-button @click="ecoApprovalVisible = false">取消</el-button>
        <el-button
          data-test="confirm-bom-eco-approval"
          type="primary"
          :loading="ecoApprovalSubmitting"
          :disabled="ecoApprovalSubmitting"
          @click="submitEcoApproval"
        >
          提交审批
        </el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="ecoCancelVisible" title="取消工程变更" width="min(520px, 94vw)">
      <el-alert v-if="ecoCancelError" class="form-alert" type="error" :title="ecoCancelError" :closable="false" />
      <el-form label-position="top">
        <el-form-item label="取消原因">
          <el-input v-model="ecoCancelReason" name="eco-cancel-reason" type="textarea" :rows="3" maxlength="200" show-word-limit />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="ecoCancelVisible = false">关闭</el-button>
        <el-button
          data-test="submit-cancel-bom-eco"
          type="primary"
          :loading="ecoCancelSubmitting"
          :disabled="ecoCancelSubmitting"
          @click="submitCancelEco"
        >
          确认取消
        </el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="substituteFormVisible" :title="substituteEditingRecord ? '编辑替代料' : '新增替代料'" width="min(760px, 96vw)">
      <el-alert v-if="substituteFormError" class="form-alert" type="error" :title="substituteFormError" :closable="false" />
      <el-form label-position="top">
        <div class="bom-form-grid">
          <el-form-item label="主物料">
            <el-select
              :model-value="substituteForm.mainMaterialId"
              data-test="substitute-main-material-id"
              filterable
              remote
              :remote-method="searchSubstituteMaterials"
              style="width: 100%"
              @update:model-value="changeSubstituteMain"
            >
              <el-option
                v-for="material in substituteMaterialCandidates"
                :key="material.id"
                :label="candidateLabel(material)"
                :value="material.id"
                :disabled="Boolean(material.disabled)"
              >
                <span>{{ material.code }} {{ material.name }}</span>
                <span class="line-option-meta">{{ material.disabledReason || material.summary || material.status }}</span>
              </el-option>
            </el-select>
          </el-form-item>
          <el-form-item label="替代物料">
            <el-select
              v-model="substituteForm.substituteMaterialId"
              data-test="substitute-material-id"
              filterable
              remote
              :remote-method="searchSubstituteMaterials"
              style="width: 100%"
            >
              <el-option
                v-for="material in substituteMaterialCandidates"
                :key="material.id"
                :label="candidateLabel(material)"
                :value="material.id"
                :disabled="Boolean(material.disabled)"
              >
                <span>{{ material.code }} {{ material.name }}</span>
                <span class="line-option-meta">{{ material.disabledReason || material.summary || material.status }}</span>
              </el-option>
            </el-select>
          </el-form-item>
          <el-form-item label="适用范围"><el-select v-model="substituteForm.scopeType" data-test="substitute-scope-type" style="width: 100%"><el-option label="全局" value="GLOBAL" /><el-option label="父项物料范围" value="PARENT_MATERIAL" /><el-option label="BOM 范围" value="BOM" /></el-select></el-form-item>
          <el-form-item label="范围对象">
            <el-select
              v-model="substituteForm.scopeId"
              data-test="substitute-scope-id"
              filterable
              remote
              clearable
              :remote-method="searchSubstituteBoms"
              style="width: 100%"
            >
              <el-option
                v-for="record in substituteBomCandidates"
                :key="record.id"
                :label="candidateLabel(record)"
                :value="record.id"
                :disabled="Boolean(record.disabled)"
              >
                <span>{{ record.code }} {{ record.name }}</span>
                <span class="line-option-meta">{{ record.disabledReason || record.summary || record.status }}</span>
              </el-option>
            </el-select>
          </el-form-item>
          <el-form-item label="优先级"><el-input v-model="substituteForm.priority" /></el-form-item>
          <el-form-item label="替代比例"><el-input v-model="substituteForm.substituteRate" name="substitute-rate" /></el-form-item>
          <el-form-item label="生效日期"><el-date-picker v-model="substituteForm.effectiveFrom" value-on-clear="" type="date" format="YYYY-MM-DD" value-format="YYYY-MM-DD" /></el-form-item>
          <el-form-item label="失效日期"><el-date-picker v-model="substituteForm.effectiveTo" value-on-clear="" type="date" format="YYYY-MM-DD" value-format="YYYY-MM-DD" /></el-form-item>
        </div>
        <el-form-item label="备注"><el-input v-model="substituteForm.remark" type="textarea" :rows="2" /></el-form-item>
      </el-form>
      <template #footer><el-button @click="substituteFormVisible = false">取消</el-button><el-button data-test="submit-substitute" type="primary" :loading="substituteFormSubmitting" :disabled="substituteFormSubmitting" @click="saveSubstitute">保存</el-button></template>
    </el-dialog>

    <el-drawer v-model="substituteDetailVisible" title="替代料详情" size="min(520px, 92vw)">
      <dl v-if="substituteDetailRecord" class="bom-detail-list">
        <dt>主物料</dt><dd>{{ substituteDetailRecord.mainMaterialCode }} {{ substituteDetailRecord.mainMaterialName }}</dd>
        <dt>替代物料</dt><dd>{{ substituteDetailRecord.substituteMaterialCode }} {{ substituteDetailRecord.substituteMaterialName }}</dd>
        <dt>范围</dt><dd>{{ scopeTypeLabel(substituteDetailRecord.scopeType) }}</dd>
        <dt>优先级</dt><dd>{{ substituteDetailRecord.priority }}</dd>
        <dt>替代比例</dt><dd>{{ substituteDetailRecord.substituteRate }}</dd>
        <dt>状态</dt><dd>{{ substituteDetailRecord.status === 'ENABLED' ? '启用' : '停用' }}</dd>
        <dt>版本</dt><dd>{{ substituteDetailRecord.version }}</dd>
      </dl>
    </el-drawer>
  </MasterDataTableView>
</template>

<style scoped>
.bom-tabs {
  margin-bottom: 12px;
}

.tab-button {
  background: transparent;
  border: 0;
  color: inherit;
  cursor: pointer;
  font: inherit;
  padding: 0;
}

.bom-form-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 0 14px;
}

.field-with-action {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 8px;
}

.bom-detail-list {
  display: grid;
  grid-template-columns: 112px minmax(0, 1fr);
  gap: 10px 14px;
  margin: 0 0 16px;
}

.bom-detail-list dt {
  color: var(--qherp-muted);
}

.bom-detail-list dd {
  min-width: 0;
  margin: 0;
  word-break: break-word;
}

.history-relation-list {
  display: grid;
  gap: 10px;
}

.history-relation-card {
  border: 1px solid var(--qherp-border);
  border-radius: 6px;
  padding: 10px;
}

.history-relation-header {
  align-items: center;
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 8px;
}

.history-relation-detail {
  display: grid;
  grid-template-columns: 72px minmax(0, 1fr);
  gap: 6px 10px;
  margin: 0;
}

.history-relation-detail dt {
  color: var(--qherp-muted);
}

.history-relation-detail dd {
  margin: 0;
  min-width: 0;
  word-break: break-word;
}

.line-option-meta {
  color: var(--qherp-muted);
  float: right;
  font-size: 12px;
  margin-left: 12px;
}

.eco-apply-result h3 {
  font-size: 16px;
  margin: 16px 0 10px;
}

@media (max-width: 760px) {
  .bom-form-grid,
  .field-with-action {
    grid-template-columns: 1fr;
  }
}
</style>
