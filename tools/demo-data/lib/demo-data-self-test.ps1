param(
    [string] $Root = (Resolve-Path (Join-Path $PSScriptRoot "../../..")).Path
)

$ErrorActionPreference = "Stop"

. (Join-Path $PSScriptRoot "demo-data-common.ps1")

$cases = New-Object System.Collections.Generic.List[string]

function Assert-True {
    param(
        [bool] $Condition,
        [string] $Message
    )
    if (-not $Condition) {
        throw $Message
    }
}

$safe = Test-DemoResourceName -Value "qherp_demo_build_20260715" -Prefix "qherp_demo_build_"
Assert-True -Condition $safe -Message "临时数据库名称应被识别为安全资源。"
$unsafe = Test-DemoResourceName -Value "qherp" -Prefix "qherp_demo_build_"
Assert-True -Condition (-not $unsafe) -Message "正式数据库名称不得通过临时资源校验。"
$privateBucket = Test-DemoResourceName -Value "qherp-private" -Prefix "qherp-demo-build-"
Assert-True -Condition (-not $privateBucket) -Message "正式 MinIO bucket 不得通过临时资源校验。"

$body = ConvertTo-DemoJson -Value ([ordered]@{ materialCode = "RAW-CABLE-001"; quantity = "12.000000" })
$parsedBody = $body | ConvertFrom-Json
Assert-True -Condition ($parsedBody.materialCode -eq "RAW-CABLE-001") -Message "JSON 序列化应保留业务字段。"
Assert-True -Condition ($parsedBody.quantity -eq "12.000000") -Message "十进制字符串不得被转换成浮点数。"

$manifestPath = Join-Path $Root "apps/api/target/demo-data/self-test-manifest.json"
$manifest = New-DemoManifest -RunId "DEMO-ELEC-SELFTEST" -OutputPath $manifestPath -GitCommit "test"
$manifest.AddObject("unit", "PCS", 1)
$manifest.AddFile([ordered]@{ category = "attachment"; objectType = "UNIT"; objectId = 1; downloadableId = 99; downloadPath = "/api/admin/attachments/99/download"; fileName = "unit.txt" })
$manifest.Save()
Assert-True -Condition (Test-Path -LiteralPath $manifestPath) -Message "manifest 应写入 ignored target 目录。"
$manifestJson = Get-Content -LiteralPath $manifestPath -Raw | ConvertFrom-Json
Assert-True -Condition ($manifestJson.runId -eq "DEMO-ELEC-SELFTEST") -Message "manifest 应保留运行 ID。"
Assert-True -Condition ($manifestJson.objects.unit.PCS -eq 1) -Message "manifest 应记录对象自然键与 ID。"
Assert-True -Condition ($manifestJson.files[0].downloadPath -eq "/api/admin/attachments/99/download" -and $manifestJson.files[0].downloadableId -eq 99) `
    -Message "manifest files 必须记录附件、导入源、导出或打印的可下载对象标识，不能只保留含糊字符串。"

$generatorPath = Join-Path $Root "tools/demo-data/generate-demo-data.ps1"
$generator = Get-Content -LiteralPath $generatorPath -Raw
$rebuildPath = Join-Path $Root "tools/demo-data/rebuild-acceptance.ps1"
$rebuild = Get-Content -LiteralPath $rebuildPath -Raw
$commonPath = Join-Path $Root "tools/demo-data/lib/demo-data-common.ps1"
$common = Get-Content -LiteralPath $commonPath -Raw
Assert-True -Condition ($generator -match 'Get-ItemByField -Path "/api/admin/system/business-periods" -FieldName "periodCode" -FieldValue \$Code -Query @\{ periodCode = \$Code \}') `
    -Message "业务期间幂等查询必须使用真实 periodCode 参数，不能依赖 keyword。"
Assert-True -Condition ($generator -match 'contactPhone = "021-60000000"') `
    -Message "客户/供应商演示数据必须使用真实 DTO 字段 contactPhone。"
Assert-True -Condition ($generator -notmatch '(?m)^\s+phone = "021-60000000"') `
    -Message "客户/供应商演示数据不得发送未消费的 phone 字段。"
$warehouseStart = $generator.IndexOf("function Ensure-Warehouse")
$warehouseEnd = $generator.IndexOf("function Ensure-Category", $warehouseStart)
Assert-True -Condition ($warehouseStart -ge 0 -and $warehouseEnd -gt $warehouseStart) `
    -Message "自测无法定位 Ensure-Warehouse 函数边界。"
$warehouseFunction = $generator.Substring($warehouseStart, $warehouseEnd - $warehouseStart)
Assert-True -Condition ($warehouseFunction -notmatch 'sortOrder\s*=') `
    -Message "仓库 DTO 不消费 sortOrder，演示生成器不得发送或依赖该字段。"
Assert-True -Condition ($generator -notmatch 'Get-Date') `
    -Message "演示业务日期必须由 2026-07-15 固定锚点派生，不得使用运行时 Get-Date。"
Assert-True -Condition ($generator -match '\$script:DemoPurchaseReceiptIds\.Add\(\[long\]\$existing\.id\)') `
    -Message "质检处理必须限定本次演示采购入库来源，避免误处理非演示 PENDING。"
Assert-True -Condition ($generator -match 'BATCH-CU-Q' -and $generator -match 'BATCH-CU-R' -and $generator -match 'BATCH-CU-F' -and $generator -notmatch 'BATCH-CU-01') `
    -Message "铜排质检拆分必须依赖多个来源分配，不能重复使用同一 sourceAllocationId。"
Assert-True -Condition ($generator -match 'Submit-And-ActSalesContractApproval') `
    -Message "合同生效必须通过 022 固定审批提交和审批任务处理，不能直接调用业务 activate。"
Assert-True -Condition ($generator -notmatch '/api/admin/sales-project-contracts/[^`"]+/activate') `
    -Message "演示生成器不得直接调用合同 activate 绕过固定审批。"
$mainApprovalIndex = $generator.IndexOf('$mainContract = Submit-And-ActSalesContractApproval -Contract $mainContract -Action "approve" -Key "MAIN"')
$projectActivationIndex = $generator.IndexOf('/api/admin/sales-projects/$($projectA.id)/activate')
$supplementCreateIndex = $generator.IndexOf('$supplementContract = Ensure-SalesContract')
Assert-True -Condition ($mainApprovalIndex -ge 0 -and $projectActivationIndex -gt $mainApprovalIndex -and $supplementCreateIndex -gt $projectActivationIndex) `
    -Message "真实服务顺序必须是主合同审批生效、项目激活、再创建补充合同。"
Assert-True -Condition ($generator -match '/api/admin/users/\$\(\$existing\.id\)/password') `
    -Message "同库重跑时既有演示用户必须通过管理员 API 重置为本轮注入密码，保证审批用户可登录。"
Assert-True -Condition ($generator -notmatch 'CONTRACT-(REJECT|WITHDRAW|CANCEL).*?-ContractType "MAIN"') `
    -Message "驳回/撤回/取消审批样例不得在同一项目下重复创建多个非取消 MAIN 合同。"
Assert-True -Condition ($generator -match 'function Ensure-PeriodAuditSamples') `
    -Message "生成器必须通过真实业务期间解锁/锁定动作补足期间审计样例。"
$periodAuditStart = $generator.IndexOf("function Ensure-PeriodAuditSamples")
$periodAuditEnd = $generator.IndexOf("function Ensure-Bom", $periodAuditStart)
Assert-True -Condition ($periodAuditStart -ge 0 -and $periodAuditEnd -gt $periodAuditStart) `
    -Message "自测无法定位 Ensure-PeriodAuditSamples 函数边界。"
$periodAuditFunction = $generator.Substring($periodAuditStart, $periodAuditEnd - $periodAuditStart)
Assert-True -Condition ($periodAuditFunction -match 'Get-ItemByField -Path "/api/admin/system/business-periods" -FieldName "periodCode"') `
    -Message "期间审计刷新只能使用业务期间列表 periodCode 契约，不能调用不存在的详情端点。"
Assert-True -Condition ($periodAuditFunction -notmatch 'Get-ItemById -Path "/api/admin/system/business-periods"') `
    -Message "BusinessPeriodAdminController 没有 GET /api/admin/system/business-periods/{id}，生成器不得调用该详情端点。"
Assert-True -Condition ($generator -match 'function Ensure-DeniedAuditSample') `
    -Message "生成器必须通过低权限账号真实访问受保护接口生成权限拒绝审计样例。"
Assert-True -Condition ($generator -match 'function Ensure-AdditionalDemoAttachments') `
    -Message "生成器必须用真实附件 API 补足 8 个文件对象、业务附件和 MinIO 对象。"
Assert-True -Condition ($generator -match 'function Ensure-BomEcoDraft') `
    -Message "生成器必须通过真实 ECO API 补足至少 3 个工程变更样例。"
Assert-True -Condition ($generator -match 'SALES_CONTRACT_EFFECTIVE_MIN_1' -or $generator -notmatch 'SALES_CONTRACT_ACTIVE_MIN_1') `
    -Message "合同验收口径必须保持 EFFECTIVE，不得回退到 ACTIVE 合同规则。"
Assert-True -Condition ($generator -match 'function Ensure-OwnershipConversionPosted') `
    -Message "项目库存和项目成本层必须通过真实所有权转换单据与审批过账生成。"
$ownershipStart = $generator.IndexOf("function Ensure-OwnershipConversionPosted")
$ownershipEnd = $generator.IndexOf("function Ensure-WarehouseTransferPosted", $ownershipStart)
Assert-True -Condition ($ownershipStart -ge 0 -and $ownershipEnd -gt $ownershipStart) `
    -Message "自测无法定位 Ensure-OwnershipConversionPosted 函数边界。"
$ownershipFunction = $generator.Substring($ownershipStart, $ownershipEnd - $ownershipStart)
Assert-True -Condition ($ownershipFunction -match 'Submit-And-ApproveInventoryDocument' -and $generator -match '\$Path/\$\(\$latest\.id\)/submit-approval') `
    -Message "所有权转换必须通过通用审批提交端点 submit-approval，不能直接写项目库存。"
Assert-True -Condition ($generator -match 'function Ensure-WarehouseTransferPosted') `
    -Message "仓库调拨必须通过真实受控调拨单据过账生成。"
Assert-True -Condition ($generator -notmatch 'insert\\s+into\\s+inv_project_cost_layer' -and $generator -notmatch 'insert\\s+into\\s+inv_stock_balance') `
    -Message "演示生成器不得用 SQL 伪造项目成本层或库存余额。"
Assert-True -Condition ($generator -notmatch '\$projectCuLayer\.costLayerId') `
    -Message "成本层候选 DTO 主键字段是 id，sourceCostLayerId 不得读取不存在的 costLayerId。"
Assert-True -Condition ($generator -notmatch 'status = "OPEN"') `
    -Message "成本层接口返回状态为 ACTIVE，生成器不得用 OPEN 查询项目成本层。"
Assert-True -Condition ($generator -match 'PROJECT-CU-A' -and $generator -match 'sourceOwnershipType = "PUBLIC"' -and $generator -match 'targetOwnershipType = "PROJECT"') `
    -Message "所有权转换样例必须包含 PUBLIC_TO_PROJECT。"
Assert-True -Condition ($generator -match 'PROJECT-CABLE-REASSIGN' -and $generator -match 'sourceOwnershipType = "PROJECT"' -and $generator -match 'targetOwnershipType = "PROJECT"') `
    -Message "所有权转换样例必须包含 PROJECT_TO_PROJECT。"
Assert-True -Condition ($generator -match 'PROJECT-CU-RETURN-PUBLIC' -and $generator -match 'targetOwnershipType = "PUBLIC"') `
    -Message "所有权转换样例必须包含 PROJECT_TO_PUBLIC。"
Assert-True -Condition ($generator -match 'function Ensure-SalesOrderConfirmed' -and $generator -match '/api/admin/sales/orders/\$\(\$existing\.id\)/confirm') `
    -Message "销售订单必须通过真实销售 API 创建并确认，形成库存预留。"
Assert-True -Condition ($generator -match 'function Ensure-SalesShipmentPosted' -and $generator -match '/api/admin/sales/shipments/\$\(\$existing\.id\)/post') `
    -Message "销售出库必须通过真实发货单过账生成。"
Assert-True -Condition ($generator -match 'Ensure-SalesOrderConfirmed -Key "SEMI-B-' -and $generator -notmatch 'Ensure-SalesOrderConfirmed -Key "(SCREW|RAIL|CABLE-RESERVED)"') `
    -Message "销售服务只允许成品/半成品，演示销售链不得用原料或辅料绕过真实可售物料约束。"
Assert-True -Condition ($generator -match 'Ensure-SalesOrderConfirmed -Key "SEMI-B-A" -Customer \$customers\[0\] -Project \$projectA -Contract \$mainContract') `
    -Message "带项目/合同的销售订单客户必须与项目客户一致。"
Assert-True -Condition ($generator -match 'function Ensure-WorkOrderReleased' -and $generator -match '/api/admin/production/work-orders/\$\(\$existing\.id\)/release') `
    -Message "生产工单必须通过真实释放动作形成 BOM 用料快照和预留。"
Assert-True -Condition ($generator -match 'function Ensure-ProductionExecutionPosted' -and $generator -match '/material-issues/\$\(\$issue\.id\)/post' -and $generator -match '/reports/\$\(\$report\.id\)/post' -and $generator -match '/completion-receipts/\$\(\$receipt\.id\)/post') `
    -Message "生产领料、报工和完工必须通过真实子单过账，不能 SQL 造成本记录。"
Assert-True -Condition ($generator -match 'Process-PendingQualityInspections -SourceType "PRODUCTION_COMPLETION"') `
    -Message "半成品完工入库后必须通过真实质检接口转为合格库存，再进入销售发货链。"
$productionStepIndex = $generator.IndexOf('Write-Step "创建生产工单、领料、报工和完工入库。"')
$salesStepIndex = $generator.IndexOf('Write-Step "创建销售订单、预留和销售发货。"')
Assert-True -Condition ($productionStepIndex -ge 0 -and $salesStepIndex -gt $productionStepIndex) `
    -Message "可销售半成品库存必须先由生产完工和质检生成，销售链不能早于生产链执行。"
Assert-True -Condition ($generator -match 'function Ensure-FinanceSettlementPosted' -and $generator -match '/api/admin/finance/receivables/\$\([^)]*\.id\)/receipts' -and $generator -match '/api/admin/finance/payables/\$\([^)]*\.id\)/payments') `
    -Message "应收应付和收付款必须通过真实财务 API 生成。"
Assert-True -Condition ($generator -notmatch 'foreach \(\$[rp] in @\(\$(receivables|payables)\)\)') `
    -Message "财务 helper 不得用 @() 包装泛型列表遍历，避免 pwsh binder 类型错误。"
Assert-True -Condition ($generator -match 'function Ensure-ReversalDocumentsPosted' -and $generator -match '/api/admin/sales/returns' -and $generator -match '/api/admin/procurement/returns' -and $generator -match '/api/admin/production/material-returns' -and $generator -match '/api/admin/production/material-supplements') `
    -Message "销售退货、采购退货、生产退料和补料必须通过真实冲销 API 生成。"
Assert-True -Condition ($generator -match 'function Get-SalesReturnBySourceShipment' -and $generator -match 'source\.sourceId -eq \$Shipment\.id') `
    -Message "销售退货列表摘要不返回 remark，同库复跑必须按源发货单定位既有退货单。"
Assert-True -Condition ($generator -match '/api/admin/sales/return-sources' -and $generator -match 'sourceAllocationId = \$returnAllocation\.sourceAllocationId') `
    -Message "批次销售退货必须先读取 return-sources 候选并提交 sourceAllocationId，不能直接复用普通批次字段。"
Assert-True -Condition ($generator -match 'sourceShipmentLineId = \$sourceLine\.id\s+quantity = "0\.500000"') `
    -Message "收款样例之后的销售退货只能做部分退货，避免退货金额超过当前可冲金额。"
Assert-True -Condition ($generator -match 'elseif \(\$salesReturn\.status -eq "DRAFT"\)\s*\{\s*\$salesReturn = Invoke-DemoApi -Session \$Session -Method Put -Path "/api/admin/sales/returns/\$\(\$salesReturn\.id\)"') `
    -Message "同库复跑遇到旧销售退货 DRAFT 时，必须用真实 PUT 契约更新为当前行数量和追踪来源后再过账。"
Assert-True -Condition ($generator -match '/api/admin/production/material-return-sources' -and $generator -match 'sourceAllocationId = \$materialReturnAllocation\.sourceAllocationId') `
    -Message "批次生产退料必须先读取 material-return-sources 候选并提交 sourceAllocationId。"
Assert-True -Condition ($generator -match 'Ensure-ReversalDocumentsPosted -SalesShipment \$shipmentSemiA -PurchaseReceipt \$receiptMain `\s+-ProductionIssue \$productionExecution\.issue -WorkOrder \$workOrderReleased') `
    -Message "生产补料样例必须挂到 RELEASED/IN_PROGRESS 工单，不能使用已完成工单作为来源。"
Assert-True -Condition ($generator -match 'function Ensure-ProductionIssuePosted') `
    -Message "生产补料样例必须先通过同一工单真实领料过账形成成本来源。"
$supplementSourceIssueIndex = $generator.IndexOf('$supplementSourceIssue = Ensure-ProductionIssuePosted -Key "SUPPLEMENT-SOURCE"')
$reversalDocsIndex = $generator.IndexOf('$reversalDocs = Ensure-ReversalDocumentsPosted')
Assert-True -Condition ($supplementSourceIssueIndex -ge 0 -and $reversalDocsIndex -gt $supplementSourceIssueIndex) `
    -Message "同一工单领料成本来源必须在生产补料创建和过账之前生成。"
Assert-True -Condition ($generator -match 'DEMO-RESERVATION-SAMPLE' -and $generator -match 'MAT-AUX-SCREW' -and $generator -match '\$woScrewLine\.requiredQuantity') `
    -Message "生产样例必须包含非追踪螺钉用料并保留已释放工单，形成额外 BOM 快照和真实锁定余额。"
Assert-True -Condition ($generator -match 'adjustmentReceipt' -and $generator -match 'sourceType = "RECEIPT"\s+sourceId = \$AdjustmentReceipt\.id\s+targetId = \$ReceivableForAdjustment\.id' -and $generator -notmatch 'sourceType = "SALES_RETURN"\s+sourceId = \$salesReturn\.id\s+targetId = \$ReceivableForAdjustment\.id') `
    -Message "往来调整必须使用独立应收上的真实收款源，不能用销售退货单二次冲减同一往来。"
Assert-True -Condition ($generator -match '验收演示未盘草稿盘点' -and $generator -match 'STK-DRAFT-START' -and $generator -notmatch 'countedQuantity = \$null') `
    -Message "未盘样例必须启动盘点形成真实快照行，并保持 countedQuantity 为空而不是提交 null。"
Assert-True -Condition ($generator -match 'function Ensure-StocktakeDocuments' -and $generator -match 'function Get-StocktakeLinesPage' -and $generator -match '/api/admin/inventory/stocktakes/\$\(\$Stocktake\.id\)/lines\?page=\$Page&pageSize=\$PageSize') `
    -Message "盘点必须消费分页行接口，不能依赖详情无界 lines。"
Assert-True -Condition ($generator -match 'function Get-AllStocktakeLines' -and $generator -match 'while \(\$page -le \$data\.totalPages\)' -and $generator -notmatch '\$firstPage = Get-StocktakeLinesPage[\s\S]*\$secondPage = Get-StocktakeLinesPage') `
    -Message "盘点行读取必须按 totalPages 循环或精确 scope，不得固定只读第 1、2 页。"
Assert-True -Condition ($generator -match 'varianceUnitCost' -and $generator -match 'varianceReason' -and $generator -match 'INVENTORY_STOCKTAKE') `
    -Message "项目正差异盘盈必须提交单位成本、行级原因和有效附件证据。"
Assert-True -Condition ($generator -match 'function Ensure-ValuationAdjustmentPosted' -and $generator -match 'Submit-And-ApproveInventoryDocument -Document \$existing -Path "/api/admin/inventory/valuation-adjustments"') `
    -Message "估值调整必须通过真实单据和固定审批过账。"
Assert-True -Condition ($generator -match '\$latest\.status -eq "DRAFT" -or \$latest\.status -eq "RECONCILED"') `
    -Message "库存审批 helper 必须支持盘点 RECONCILED 状态提交审批，避免范围锁永久停留。"
Assert-True -Condition ($generator -match 'function Ensure-DocumentTaskSamples' -and $generator -match '/api/admin/imports/materials' -and $generator -match '/api/admin/imports/bom-drafts' -and $generator -match '/api/admin/exports/materials' -and $generator -match '/api/admin/print-tasks' -and $generator -match '/api/admin/document-tasks/\$\([^)]*\.id\)/cancel') `
    -Message "文档任务必须覆盖导入、导出、打印和取消任务真实 API。"
Assert-True -Condition ($generator -match 'MISSING-CATEGORY' -and $generator -match 'MISSING-UNIT') `
    -Message "无效物料导入必须使用当前服务端会拒绝的真实引用错误，不能依赖空编码自动占号路径。"
Assert-True -Condition ($generator -match '\$itemHeader = @\("lineNo", "childMaterialCode", "businessUnit", "businessQuantity", "lossRate", "warehouse", "remark"\)' -and $generator -notmatch 'items\.lineNo') `
    -Message "BOM 草稿导入 items 工作表必须使用后端真实冻结表头，不能带 items. 前缀。"
Assert-True -Condition ($common -match '\[hashtable\] \$Headers = @\{\}' -and $generator -match 'Idempotency-Key" = "\$RunId-DOC-EXPORT-MATERIAL' -and $generator -match 'Idempotency-Key" = "\$RunId-DOC-EXPORT-BOM' -and $generator -match 'Idempotency-Key" = "\$RunId-DOC-PRINT') `
    -Message "文档任务 JSON 端点必须通过真实 Idempotency-Key 头创建任务，不能被服务端幂等校验拒绝。"
Assert-True -Condition ($generator -match 'Get-FileSha256' -and $generator -match 'Invoke-DemoMultipart -Path "/api/admin/imports/materials"[\s\S]*"Idempotency-Key" = "\$RunId-DOC-IMPORT-MATERIAL-VALID"' -and $generator -notmatch 'Find-DemoDocumentTask -TaskType "MATERIAL_IMPORT"') `
    -Message "文档导入任务必须依赖真实幂等键和文件 sha256/文件名等价校验，不得按 taskType+status 宽泛复用旧任务。"
Assert-True -Condition ($generator -match 'StableXlsxEntryTime' -and $generator -match '\$entry\.LastWriteTime = \$StableXlsxEntryTime' -and $generator -match '\$sheetNames = @\(\$Sheets\.Keys \| Sort-Object\)') `
    -Message "XLSX 导入源必须固定 ZIP entry 时间和工作表顺序，确保同文件同幂等键复跑时 SHA 稳定。"
Assert-True -Condition ($generator -match 'QHERP_TASK_WORKER_ENABLED=false' -or $generator -match 'DocumentWorkerMode') `
    -Message "生成器或入口必须显式区分 worker 关闭窗口和 worker 处理窗口。"
Assert-True -Condition ($generator -match 'Ensure-PendingApprovalTaskSample' -and $generator -match 'PENDING-CONTRACT' -and $generator -match 'pendingApproval') `
    -Message "生成器必须保留一个真实 PENDING 审批任务样例，同时不影响通过/驳回/撤回/取消链。"
Assert-True -Condition ($generator -match 'AcceptanceAuthorizationPath' -and $generator -match 'Assert-DemoAcceptanceAuthorization' -and $generator -notmatch 'AcceptanceConfirmPhrase') `
    -Message "生成器不得绕过备份入口直接 Acceptance；正式模式必须校验由重建入口创建的短时目标绑定授权材料。"
Assert-True -Condition ($rebuild -match '\[ValidateSet\("Temporary", "Acceptance"\)\]' -and $rebuild -match 'REBUILD qherp/qherp-private ON 18080') `
    -Message "安全重建入口必须显式区分 Temporary/Acceptance，并要求正式重建精确确认词。"
Assert-True -Condition ($rebuild -match 'Database -ne "qherp"' -and $rebuild -match 'MinioBucket -ne "qherp-private"' -and $rebuild -match 'Port -ne 18080' -and $rebuild -match 'Assert-LoopbackApiBaseUrl') `
    -Message "Acceptance 模式只能允许 qherp、qherp-private、loopback 18080。"
Assert-True -Condition ($rebuild -match 'qherp_demo_build_' -and $rebuild -match 'qherp-demo-build-' -and $rebuild -match 'Temporary 模式禁止连接 18080') `
    -Message "Temporary 模式只能允许临时库、临时 bucket，并必须禁止 18080。"
Assert-True -Condition ($rebuild -match 'Backup-PostgresDatabase' -and $rebuild -match 'Backup-MinioBucket' -and $rebuild.IndexOf('Backup-PostgresDatabase') -lt $rebuild.IndexOf('Reset-PostgresDatabase') -and $rebuild.IndexOf('Backup-MinioBucket') -lt $rebuild.IndexOf('Reset-MinioBucket')) `
    -Message "安全重建入口必须在任何重建动作前先备份 PostgreSQL 和 MinIO。"
Assert-True -Condition ($rebuild -match 'QHERP_INITIAL_ADMIN_PASSWORD' -and $rebuild -match 'QHERP_DEMO_USER_PASSWORD' -and $rebuild -notmatch 'Qherp@2026!') `
    -Message "安全重建入口不得保留明文默认密码，凭据必须由环境变量注入。"
Assert-True -Condition ($rebuild -match 'Assert-PortAvailable' -and $rebuild -match 'Start-ManagedAcceptanceApi' -and $rebuild -match 'ExpectedGitCommit' -and $rebuild -match 'RepositoryRoot' -and $rebuild -match 'QHERP_DATASOURCE_URL' -and $rebuild -match 'qherp-private') `
    -Message "Acceptance 入口必须拒绝占用中的 18080，并从指定仓库/提交启动自己管理的隐藏 API 进程。"
Assert-True -Condition ($rebuild -match 'WorkerDisabled' -and $rebuild -match 'WorkerEnabled' -and $rebuild -match 'workerDisabledApi' -and $rebuild -match 'workerEnabledApi' -and $rebuild -match 'Stop-ManagedApi' -and $rebuild -match 'disabledManifestPath' -and $rebuild -match 'enabledManifestPath') `
    -Message "重建入口必须编排 worker-disabled 到 worker-enabled 两阶段受管 API，不能只用 WorkerEnabled 跳过取消任务窗口。"
Assert-True -Condition ($rebuild -match 'Get-NetTCPConnection[\s\S]*OwningProcess' -and $rebuild -match 'launcherPid') `
    -Message "受管 API 必须记录并停止实际监听端口的 Java PID，不能只记录 Maven wrapper 启动器 PID。"
Assert-True -Condition ($rebuild -match 'Wait-ManagedApiLoginReady' -and $rebuild.IndexOf('Wait-ManagedApiLoginReady') -gt $rebuild.IndexOf('Wait-ApiHealth')) `
    -Message "受管 API 健康 UP 后必须等待初始管理员可登录，再运行生成器，避免账号权限初始化竞态。"
Assert-True -Condition ($rebuild -match 'function Stop-StartedManagedApi' -and $rebuild -match 'catch\s*\{[\s\S]*Stop-StartedManagedApi') `
    -Message "受管 API 启动过程中健康或登录就绪失败时必须停止已启动进程，不能等调用方拿到对象后才清理。"
Assert-True -Condition ($rebuild -match 'New-AcceptanceAuthorization' -and $rebuild.IndexOf('New-AcceptanceAuthorization') -gt $rebuild.IndexOf('Backup-MinioBucket') -and $rebuild -match 'AcceptanceAuthorizationPath') `
    -Message "Acceptance 授权材料必须由重建入口在备份成功后创建并传给生成器。"
Assert-True -Condition ($rebuild -match 'Assert-PostgresOwner' -and $rebuild -match 'Quote-PostgresIdentifier' -and $rebuild -notmatch 'create database \$quoted owner \$PostgresUser') `
    -Message "Reset-PostgresDatabase 必须固定/白名单校验 owner 并安全引用，不能直接拼接 PostgresUser。"
Assert-True -Condition ($rebuild -match 'manifestPath' -and $rebuild -match 'validationPath' -and $rebuild -match 'generatedAndValidated = \$validationSucceeded') `
    -Message "重建 summary 必须在实际生成和验证成功后写 generatedAndValidated=true，并记录最终 manifest/validation 路径。"

Write-Host "demo-data-self-test 通过"
