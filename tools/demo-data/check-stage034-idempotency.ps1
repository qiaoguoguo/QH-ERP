[CmdletBinding()]
param(
    [string] $ApiBaseUrl = $(if ($env:QHERP_API_BASE_URL) { $env:QHERP_API_BASE_URL } else { "http://127.0.0.1:38080" }),
    [string] $Database = $(if ($env:QHERP_POSTGRES_DB) { $env:QHERP_POSTGRES_DB } else { "qherp_034_delivery_governance" }),
    [string] $PostgresContainer = $(if ($env:QHERP_POSTGRES_CONTAINER) { $env:QHERP_POSTGRES_CONTAINER } else { "qherp-034-postgres-qa-20260721" }),
    [string] $PostgresUser = $(if ($env:QHERP_POSTGRES_USER) { $env:QHERP_POSTGRES_USER } else { "qherp" }),
    [string] $MinioBucket = $(if ($env:QHERP_S3_BUCKET) { $env:QHERP_S3_BUCKET } else { "qherp-034-delivery-governance" }),
    [string] $AdminUsername = "admin",
    [string] $AdminPassword = $env:QHERP_INITIAL_ADMIN_PASSWORD,
    [string] $QaUserPassword = $(if ($env:QHERP_DEMO_USER_PASSWORD) { $env:QHERP_DEMO_USER_PASSWORD } else { $env:QHERP_INITIAL_ADMIN_PASSWORD }),
    [string] $RunId = ("QA034-IDEMPOTENCY-" + (Get-Date -Format "yyyyMMddHHmmss")),
    [string] $OutputJsonPath = (Join-Path (Get-Location).Path "apps/api/target/demo-data/stage034-idempotency-check.json")
)

$ErrorActionPreference = "Stop"
. (Join-Path $PSScriptRoot "lib/demo-data-common.ps1")
. (Join-Path $PSScriptRoot "lib/stage034-isolation-strategy.ps1")

if ([string]::IsNullOrWhiteSpace($AdminPassword)) {
    throw "缺少 QHERP_INITIAL_ADMIN_PASSWORD 或 -AdminPassword，无法执行 034 幂等复现检查。"
}
if ([string]::IsNullOrWhiteSpace($QaUserPassword)) {
    throw "缺少 QHERP_DEMO_USER_PASSWORD 或 -QaUserPassword，无法创建审批复核用户。"
}

Assert-Stage034IsolationTarget -Database $Database -MinioBucket $MinioBucket -ApiBaseUrl $ApiBaseUrl

$script:Checks = New-Object System.Collections.Generic.List[object]
$script:Stage034BatchNegativeCheckCodes = @(
    "A10_SUPPLIER_BATCH_TOOL_PERMISSION_FORBIDDEN",
    "A10_SUPPLIER_BATCH_TOOL_STALE_VERSION_BLOCKED",
    "A10_SUPPLIER_BATCH_TOOL_UNCHANGED_STATUS_BLOCKED",
    "A10_SUPPLIER_BATCH_TOOL_REVALIDATION_ALL_OR_NOTHING",
    "A10_MATERIAL_BATCH_TOOL_PERMISSION_FORBIDDEN",
    "A10_MATERIAL_BATCH_TOOL_STALE_VERSION_BLOCKED",
    "A10_MATERIAL_BATCH_TOOL_UNCHANGED_STATUS_BLOCKED",
    "A10_MATERIAL_BATCH_TOOL_OPEN_REFERENCE_BLOCKED",
    "A10_MATERIAL_BATCH_TOOL_REVALIDATION_ALL_OR_NOTHING"
)

function Write-CheckInfo {
    param([string] $Message)
    Write-Host "[stage034-idempotency] $Message"
}

function Add-Check {
    param(
        [Parameter(Mandatory = $true)][string] $Code,
        [Parameter(Mandatory = $true)][bool] $Passed,
        [Parameter(Mandatory = $true)][string] $Actual,
        [Parameter(Mandatory = $true)][string] $Expected,
        [Parameter(Mandatory = $true)][string] $Message
    )
    $script:Checks.Add([pscustomobject]@{
        code = $Code
        passed = $Passed
        actual = $Actual
        expected = $Expected
        message = $Message
    })
}

function Get-QAProperty {
    param($Object, [string] $Name)
    if ($null -eq $Object) {
        return $null
    }
    $property = $Object.PSObject.Properties[$Name]
    if ($null -eq $property) {
        return $null
    }
    return $property.Value
}

function ConvertTo-QAErrorResult {
    param($ErrorRecord, [string] $Method, [string] $Path)
    $statusCode = $null
    $body = $ErrorRecord.ErrorDetails.Message
    if ($null -ne $ErrorRecord.Exception.Response) {
        try {
            $statusCode = [int] $ErrorRecord.Exception.Response.StatusCode
        }
        catch {
            $statusCode = $null
        }
        try {
            if ($null -ne $ErrorRecord.Exception.Response.Content) {
                $body = $ErrorRecord.Exception.Response.Content.ReadAsStringAsync().GetAwaiter().GetResult()
            }
        }
        catch {
            if ([string]::IsNullOrWhiteSpace($body)) {
                $body = $ErrorRecord.Exception.Message
            }
        }
    }
    if ([string]::IsNullOrWhiteSpace($body)) {
        $body = $ErrorRecord.Exception.Message
    }
    $json = $null
    try {
        $json = $body | ConvertFrom-Json
    }
    catch {
        $json = $null
    }
    return [pscustomobject]@{
        success = $false
        failed = $true
        statusCode = $statusCode
        code = if ($null -ne $json) { [string]$json.code } else { $null }
        message = if ($null -ne $json) { [string]$json.message } else { $ErrorRecord.Exception.Message }
        raw = $body
        method = $Method
        path = $Path
    }
}

function Invoke-QAApi {
    param(
        [Parameter(Mandatory = $true)] $Session,
        [Parameter(Mandatory = $true)][string] $Method,
        [Parameter(Mandatory = $true)][string] $Path,
        $Body = $null,
        [hashtable] $Headers = @{},
        [switch] $AllowFailure
    )
    $headers = @{} + $Headers
    $headers[$Session.csrfHeaderName] = $Session.csrfToken
    $parameters = @{
        Method = $Method
        Uri = "$($Session.baseUrl)$Path"
        WebSession = $Session.webSession
        Headers = $headers
        ErrorAction = "Stop"
    }
    if ($null -ne $Body) {
        $parameters.ContentType = "application/json"
        $parameters.Body = if ($Body -is [string]) { $Body } else { ConvertTo-DemoJson -Value $Body }
    }
    try {
        $response = Invoke-WebRequest @parameters
        $json = $response.Content | ConvertFrom-Json
        $result = [pscustomobject]@{
            success = [bool]$json.success
            failed = -not [bool]$json.success
            statusCode = [int]$response.StatusCode
            code = [string]$json.code
            message = [string]$json.message
            data = $json.data
            raw = $response.Content
            method = $Method
            path = $Path
        }
        if ($result.failed -and -not $AllowFailure.IsPresent) {
            throw "API 返回失败：$Method $Path code=$($result.code) message=$($result.message)"
        }
        return $result
    }
    catch {
        $result = ConvertTo-QAErrorResult -ErrorRecord $_ -Method $Method -Path $Path
        if ($AllowFailure.IsPresent) {
            return $result
        }
        throw "API 请求失败：$Method $Path status=$($result.statusCode) code=$($result.code) message=$($result.message)"
    }
}

function Invoke-QAData {
    param(
        [Parameter(Mandatory = $true)] $Session,
        [Parameter(Mandatory = $true)][string] $Method,
        [Parameter(Mandatory = $true)][string] $Path,
        $Body = $null,
        [hashtable] $Headers = @{}
    )
    return (Invoke-QAApi -Session $Session -Method $Method -Path $Path -Body $Body -Headers $Headers).data
}

function Get-QAPageItems {
    param(
        [Parameter(Mandatory = $true)] $Session,
        [Parameter(Mandatory = $true)][string] $Path,
        [hashtable] $Parameters = @{}
    )
    $items = New-Object System.Collections.Generic.List[object]
    $page = 1
    while ($true) {
        $query = @{} + $Parameters
        $query["page"] = $page
        $query["pageSize"] = 100
        $data = Invoke-QAData -Session $Session -Method Get -Path ($Path + (New-DemoQueryString -Parameters $query))
        foreach ($item in @($data.items)) {
            $items.Add($item)
        }
        if ($data.totalPages -le $page -or @($data.items).Count -eq 0) {
            break
        }
        $page++
    }
    return $items.ToArray()
}

function Find-QAItemByField {
    param(
        [Parameter(Mandatory = $true)] $Session,
        [Parameter(Mandatory = $true)][string] $Path,
        [Parameter(Mandatory = $true)][string] $FieldName,
        [Parameter(Mandatory = $true)][string] $FieldValue,
        [hashtable] $Parameters = @{}
    )
    $query = @{} + $Parameters
    if (-not $query.ContainsKey("keyword")) {
        $query["keyword"] = $FieldValue
    }
    foreach ($item in @(Get-QAPageItems -Session $Session -Path $Path -Parameters $query)) {
        if ([string](Get-QAProperty -Object $item -Name $FieldName) -eq $FieldValue) {
            return $item
        }
    }
    return $null
}

function Flatten-QAPermissions {
    param([object[]] $Nodes)
    $result = New-Object System.Collections.Generic.List[object]
    foreach ($node in @($Nodes)) {
        $result.Add($node)
        $children = Get-QAProperty -Object $node -Name "children"
        if ($children -is [System.Collections.IEnumerable] -and -not ($children -is [string])) {
            foreach ($child in Flatten-QAPermissions -Nodes @($children)) {
                $result.Add($child)
            }
        }
    }
    return $result.ToArray()
}

function Get-QAPermissionMap {
    param($Session)
    $tree = Invoke-QAData -Session $Session -Method Get -Path "/api/admin/permissions/tree"
    $map = @{}
    foreach ($permission in Flatten-QAPermissions -Nodes @($tree)) {
        $map[[string]$permission.code] = [long]$permission.id
    }
    return $map
}

function Ensure-QARole {
    param($Session, [string] $Code, [string] $Name, [string[]] $PermissionCodes, [hashtable] $PermissionMap)
    $role = Find-QAItemByField -Session $Session -Path "/api/admin/roles" -FieldName "code" -FieldValue $Code
    if ($null -eq $role) {
        $role = Invoke-QAData -Session $Session -Method Post -Path "/api/admin/roles" -Body ([ordered]@{
            code = $Code
            name = $Name
            description = "034 独立 QA 幂等复现角色"
            status = "ENABLED"
            sortOrder = 934
        })
    }
    $permissionIds = New-Object System.Collections.Generic.List[long]
    foreach ($permissionCode in $PermissionCodes) {
        if ($PermissionMap.ContainsKey($permissionCode)) {
            $permissionIds.Add([long]$PermissionMap[$permissionCode])
        }
    }
    Invoke-QAData -Session $Session -Method Put -Path "/api/admin/roles/$($role.id)/permissions" -Body ([ordered]@{
        permissionIds = $permissionIds.ToArray()
    }) | Out-Null
    return $role
}

function Ensure-QAUser {
    param($Session, [string] $Username, [string] $DisplayName, [long[]] $RoleIds)
    $user = Find-QAItemByField -Session $Session -Path "/api/admin/users" -FieldName "username" -FieldValue $Username
    if ($null -ne $user) {
        Invoke-QAData -Session $Session -Method Put -Path "/api/admin/users/$($user.id)/password" -Body ([ordered]@{
            newPassword = $QaUserPassword
        }) | Out-Null
        if ($user.status -ne "ENABLED") {
            $user = Invoke-QAData -Session $Session -Method Put -Path "/api/admin/users/$($user.id)/enable"
        }
        return $user
    }
    return Invoke-QAData -Session $Session -Method Post -Path "/api/admin/users" -Body ([ordered]@{
        username = $Username
        displayName = $DisplayName
        phone = "13800000000"
        email = "$Username@example.invalid"
        initialPassword = $QaUserPassword
        status = "ENABLED"
        roleIds = $RoleIds
    })
}

function Ensure-QAUnit {
    param($Session, [string] $Code)
    $existing = Find-QAItemByField -Session $Session -Path "/api/admin/master/units" -FieldName "code" -FieldValue $Code
    if ($null -ne $existing) {
        return Invoke-QAData -Session $Session -Method Get -Path "/api/admin/master/units/$($existing.id)"
    }
    return Invoke-QAData -Session $Session -Method Post -Path "/api/admin/master/units" -Body ([ordered]@{
        code = $Code
        name = "034 幂等单位"
        precisionScale = 6
        sortOrder = 934
        status = "ENABLED"
        remark = "034 独立 QA 幂等复现"
    })
}

function Ensure-QACategory {
    param($Session, [string] $Code)
    $existing = Find-QAItemByField -Session $Session -Path "/api/admin/master/material-categories" -FieldName "code" -FieldValue $Code
    if ($null -ne $existing) {
        return Invoke-QAData -Session $Session -Method Get -Path "/api/admin/master/material-categories/$($existing.id)"
    }
    return Invoke-QAData -Session $Session -Method Post -Path "/api/admin/master/material-categories" -Body ([ordered]@{
        code = $Code
        name = "034 幂等分类"
        sortOrder = 934
        status = "ENABLED"
        remark = "034 独立 QA 幂等复现"
    })
}

function Ensure-QACustomer {
    param($Session, [string] $Code, [string] $Name)
    $existing = Find-QAItemByField -Session $Session -Path "/api/admin/master/customers" -FieldName "code" -FieldValue $Code
    if ($null -ne $existing) {
        return Invoke-QAData -Session $Session -Method Get -Path "/api/admin/master/customers/$($existing.id)"
    }
    $created = Invoke-QAData -Session $Session -Method Post -Path "/api/admin/master/customers" -Body ([ordered]@{
        code = $Code
        name = $Name
        contactName = "034 幂等联系人"
        contactPhone = "13900000000"
        status = "ENABLED"
        remark = "034 独立 QA 幂等复现"
    })
    return Invoke-QAData -Session $Session -Method Get -Path "/api/admin/master/customers/$($created.id)"
}

function Ensure-QASupplier {
    param($Session, [string] $Code, [string] $Name)
    $existing = Find-QAItemByField -Session $Session -Path "/api/admin/master/suppliers" -FieldName "code" -FieldValue $Code
    if ($null -ne $existing) {
        return Invoke-QAData -Session $Session -Method Get -Path "/api/admin/master/suppliers/$($existing.id)"
    }
    $created = Invoke-QAData -Session $Session -Method Post -Path "/api/admin/master/suppliers" -Body ([ordered]@{
        code = $Code
        name = $Name
        contactName = "034 批量负例联系人"
        contactPhone = "13900000001"
        status = "ENABLED"
        remark = "034 独立 QA 批量工具负例"
    })
    return Invoke-QAData -Session $Session -Method Get -Path "/api/admin/master/suppliers/$($created.id)"
}

function Ensure-QAMaterial {
    param($Session, [string] $Code, [string] $Name, $Unit, $Category)
    $existing = Find-QAItemByField -Session $Session -Path "/api/admin/master/materials" -FieldName "code" -FieldValue $Code
    if ($null -ne $existing) {
        return Invoke-QAData -Session $Session -Method Get -Path "/api/admin/master/materials/$($existing.id)"
    }
    $created = Invoke-QAData -Session $Session -Method Post -Path "/api/admin/master/materials" -Body ([ordered]@{
        code = $Code
        name = $Name
        specification = "034 批量负例规格"
        materialType = "RAW_MATERIAL"
        sourceType = "PURCHASED"
        trackingMethod = "NONE"
        categoryId = $Category.id
        unitId = $Unit.id
        status = "ENABLED"
        remark = "034 独立 QA 批量工具负例"
        costCategory = "AUXILIARY_MATERIAL"
        inventoryValuationCategory = "VALUATED_MATERIAL"
        inventoryValueEnabled = $true
        projectCostEnabled = $false
        costRemark = "034 批量负例成本属性"
    })
    return Invoke-QAData -Session $Session -Method Get -Path "/api/admin/master/materials/$($created.id)"
}

function Assert-QAError {
    param($Result, [int] $ExpectedStatusCode, [string] $ExpectedCode, [string] $CheckCode, [string] $Message)
    $passed = $Result.failed -eq $true -and [int]$Result.statusCode -eq $ExpectedStatusCode -and $Result.code -eq $ExpectedCode
    Add-Check -Code $CheckCode -Passed:$passed `
        -Actual "status=$($Result.statusCode);code=$($Result.code);message=$($Result.message)" `
        -Expected "status=$ExpectedStatusCode;code=$ExpectedCode" -Message $Message
}

function Invoke-QAPostgresScalar {
    param([Parameter(Mandatory = $true)][string] $Sql)
    $output = docker exec -i $PostgresContainer psql -q -U $PostgresUser -d $Database -t -A -v ON_ERROR_STOP=1 -c $Sql 2>&1
    if ($LASTEXITCODE -ne 0) {
        throw "034 只读 SQL 查询失败：container=$PostgresContainer database=$Database sql=$Sql output=$output"
    }
    return (($output | ForEach-Object { $_.ToString().Trim() }) | Where-Object { $_ } | Select-Object -Last 1)
}

function Get-QAObjectVersion {
    param([Parameter(Mandatory = $true)][ValidateSet("mst_customer", "mst_supplier", "mst_material")] [string] $TableName,
        [Parameter(Mandatory = $true)][long] $Id)
    $value = Invoke-QAPostgresScalar -Sql "select version from $TableName where id = $Id;"
    if ([string]::IsNullOrWhiteSpace($value)) {
        throw "未找到 $TableName/$Id 的 version。"
    }
    return [long]$value
}

function Get-QAPendingApprovalTask {
    param($ApproverSession, [long] $ObjectId)
    $tasks = Get-QAPageItems -Session $ApproverSession -Path "/api/admin/approval-tasks" -Parameters @{
        scope = "TODO"
    }
    foreach ($task in @($tasks)) {
        if ([long]$task.objectId -eq $ObjectId -and [string]$task.objectType -eq "DATA_REPAIR_REQUEST") {
            return $task
        }
    }
    throw "未通过公开审批任务列表找到 DATA_REPAIR_REQUEST/$ObjectId 的待审批任务。"
}

function Run-DataRepairCreateReplayCheck {
    param($AdminSession, $ApproverSession)
    Write-CheckInfo "执行数据修复创建幂等精确重放检查。"
    $customer = Ensure-QACustomer -Session $AdminSession -Code "$RunId-REPAIR-CUS" -Name "034 幂等原客户"
    $customerVersion = Get-QAObjectVersion -TableName "mst_customer" -Id ([long]$customer.id)
    $payload = [ordered]@{
        adapterCode = "CUSTOMER_PROFILE_CORRECTION_V1"
        targetObjectType = "CUSTOMER"
        targetObjectId = $customer.id
        targetVersion = $customerVersion
        reason = "$RunId 数据修复创建幂等复现"
        riskSummary = "确认创建幂等优先于对象版本重校验"
        changes = @([ordered]@{ fieldName = "name"; afterValue = "034 幂等修复后客户" })
    }
    $createKey = "$RunId-DATA-REPAIR-CREATE"
    $draft = Invoke-QAData -Session $AdminSession -Method Post -Path "/api/admin/platform/data-repairs" `
        -Body $payload -Headers @{ "Idempotency-Key" = $createKey }
    $submitted = Invoke-QAData -Session $AdminSession -Method Post -Path "/api/admin/platform/data-repairs/$($draft.id)/submit" `
        -Body ([ordered]@{ version = $draft.version; reason = "提交幂等复现"; idempotencyKey = "$RunId-DATA-REPAIR-SUBMIT" })
    $approvalTask = Get-QAPendingApprovalTask -ApproverSession $ApproverSession -ObjectId ([long]$submitted.id)
    Invoke-QAData -Session $ApproverSession -Method Post -Path "/api/admin/approval-tasks/$($approvalTask.taskId)/approve" `
        -Body ([ordered]@{ version = $approvalTask.version; comment = "同意幂等复现"; idempotencyKey = "$RunId-DATA-REPAIR-APPROVE" }) | Out-Null
    $ready = Invoke-QAData -Session $AdminSession -Method Get -Path "/api/admin/platform/data-repairs/$($draft.id)"
    $executed = Invoke-QAData -Session $AdminSession -Method Post -Path "/api/admin/platform/data-repairs/$($draft.id)/execute" `
        -Body ([ordered]@{ version = $ready.version; idempotencyKey = "$RunId-DATA-REPAIR-EXECUTE" })
    $replay = Invoke-QAApi -Session $AdminSession -Method Post -Path "/api/admin/platform/data-repairs" `
        -Body $payload -Headers @{ "Idempotency-Key" = $createKey } -AllowFailure
    $replayPassed = $replay.success -eq $true -and [long]$replay.data.id -eq [long]$draft.id `
        -and $replay.data.status -eq $executed.status
    Add-Check -Code "A01_DATA_REPAIR_CREATE_EXACT_REPLAY_AFTER_EXECUTE" -Passed:$replayPassed `
        -Actual $(if ($replay.success) { "status=$($replay.statusCode);id=$($replay.data.id);repairStatus=$($replay.data.status)" } else { "status=$($replay.statusCode);code=$($replay.code);message=$($replay.message)" }) `
        -Expected "HTTP 200;data.id=$($draft.id);status=$($executed.status)" `
        -Message "同一数据修复创建请求在执行改变对象版本后，原 Idempotency-Key 和原请求指纹重放必须返回既有修复结果。"

    $changedPayload = [ordered]@{
        adapterCode = "CUSTOMER_PROFILE_CORRECTION_V1"
        targetObjectType = "CUSTOMER"
        targetObjectId = $customer.id
        targetVersion = $customerVersion
        reason = "$RunId 数据修复创建幂等复现"
        riskSummary = "同键不同请求必须冲突"
        changes = @([ordered]@{ fieldName = "name"; afterValue = "034 同键不同请求" })
    }
    $conflict = Invoke-QAApi -Session $AdminSession -Method Post -Path "/api/admin/platform/data-repairs" `
        -Body $changedPayload -Headers @{ "Idempotency-Key" = $createKey } -AllowFailure
    Assert-QAError -Result $conflict -ExpectedStatusCode 409 -ExpectedCode "DOCUMENT_TASK_IDEMPOTENCY_CONFLICT" `
        -CheckCode "A01_DATA_REPAIR_CREATE_SAME_KEY_DIFFERENT_REQUEST_CONFLICT" `
        -Message "同一数据修复创建 Idempotency-Key 携带不同请求指纹时必须返回幂等冲突，而不是被对象版本预校验吞掉。"
}

function Run-BatchToolPreviewReplayCheck {
    param($AdminSession)
    Write-CheckInfo "执行批量工具预检幂等精确重放检查。"
    $first = Ensure-QACustomer -Session $AdminSession -Code "$RunId-BATCH-CUS-A" -Name "034 批量客户 A"
    $second = Ensure-QACustomer -Session $AdminSession -Code "$RunId-BATCH-CUS-B" -Name "034 批量客户 B"
    $firstVersion = Get-QAObjectVersion -TableName "mst_customer" -Id ([long]$first.id)
    $secondVersion = Get-QAObjectVersion -TableName "mst_customer" -Id ([long]$second.id)
    $previewKey = "$RunId-BATCH-CUSTOMER-PREVIEW"
    $originalPayload = [ordered]@{
        actionCode = "STATUS_CHANGE"
        targetStatus = "DISABLED"
        reason = "$RunId 批量工具幂等复现"
        targets = @(
            [ordered]@{ targetObjectId = $first.id; version = $firstVersion },
            [ordered]@{ targetObjectId = $second.id; version = $secondVersion }
        )
        idempotencyKey = $previewKey
    }
    $preview = Invoke-QAData -Session $AdminSession -Method Post `
        -Path "/api/admin/platform/batch-tools/CUSTOMER_STATUS_CHANGE_V1/preview" -Body $originalPayload
    $executed = Invoke-QAData -Session $AdminSession -Method Post -Path "/api/admin/platform/batch-operations/$($preview.id)/execute" `
        -Body ([ordered]@{ version = $preview.version; idempotencyKey = "$RunId-BATCH-CUSTOMER-EXECUTE" })
    $replay = Invoke-QAApi -Session $AdminSession -Method Post `
        -Path "/api/admin/platform/batch-tools/CUSTOMER_STATUS_CHANGE_V1/preview" -Body $originalPayload -AllowFailure
    $replayPassed = $replay.success -eq $true -and [long]$replay.data.id -eq [long]$preview.id `
        -and $replay.data.status -eq $executed.status
    Add-Check -Code "A01_BATCH_TOOL_PREVIEW_EXACT_REPLAY_AFTER_EXECUTE" -Passed:$replayPassed `
        -Actual $(if ($replay.success) { "status=$($replay.statusCode);id=$($replay.data.id);operationStatus=$($replay.data.status)" } else { "status=$($replay.statusCode);code=$($replay.code);message=$($replay.message)" }) `
        -Expected "HTTP 200;data.id=$($preview.id);status=$($executed.status)" `
        -Message "批量工具首次预检并执行成功后，原目标版本、原 Idempotency-Key、原请求指纹重放必须返回既有操作。"

    $latestFirstVersion = Get-QAObjectVersion -TableName "mst_customer" -Id ([long]$first.id)
    $latestSecondVersion = Get-QAObjectVersion -TableName "mst_customer" -Id ([long]$second.id)
    $changedPayload = [ordered]@{
        actionCode = "STATUS_CHANGE"
        targetStatus = "DISABLED"
        reason = "$RunId 批量工具幂等复现"
        targets = @(
            [ordered]@{ targetObjectId = $first.id; version = $latestFirstVersion },
            [ordered]@{ targetObjectId = $second.id; version = $latestSecondVersion }
        )
        idempotencyKey = $previewKey
    }
    $conflict = Invoke-QAApi -Session $AdminSession -Method Post `
        -Path "/api/admin/platform/batch-tools/CUSTOMER_STATUS_CHANGE_V1/preview" -Body $changedPayload -AllowFailure
    Assert-QAError -Result $conflict -ExpectedStatusCode 409 -ExpectedCode "DOCUMENT_TASK_IDEMPOTENCY_CONFLICT" `
        -CheckCode "A01_BATCH_TOOL_PREVIEW_NEW_VERSION_OLD_KEY_CONFLICT" `
        -Message "批量工具使用新目标版本和旧 Idempotency-Key 属于不同请求，必须返回幂等冲突。"
}

function New-QAStatusBatchPayload {
    param([string] $TargetStatus, [object[]] $Targets, [string] $Key)
    return [ordered]@{
        actionCode = "STATUS_CHANGE"
        targetStatus = $TargetStatus
        reason = "$RunId 批量状态工具负例 $Key"
        targets = @($Targets)
        idempotencyKey = "$RunId-$Key"
    }
}

function New-QABatchTarget {
    param($Object, [long] $Version = -1)
    $targetVersion = if ($Version -ge 0) { $Version } else { [long]$Object.version }
    return [ordered]@{
        targetObjectId = [long]$Object.id
        version = $targetVersion
    }
}

function Assert-QAPrecheckBlocked {
    param($Operation, [string] $CheckCode, [string] $ExpectedCode, [string] $Message)
    $items = @($Operation.items)
    $blockedItems = @($items | Where-Object { $_.status -eq "BLOCKED" })
    $matchedErrors = @($Operation.errors | Where-Object { $_.errorCode -eq $ExpectedCode })
    $passed = $Operation.status -eq "PRECHECK_FAILED" -and $blockedItems.Count -gt 0 -and $matchedErrors.Count -gt 0
    Add-Check -Code $CheckCode -Passed:$passed `
        -Actual "status=$($Operation.status);blocked=$($blockedItems.Count);errors=$($matchedErrors.Count)" `
        -Expected "PRECHECK_FAILED with $ExpectedCode" -Message $Message
}

function Get-QAObjectStatus {
    param($Session, [string] $Path, [long] $Id)
    $latest = Invoke-QAData -Session $Session -Method Get -Path "$Path/$Id"
    return [string]$latest.status
}

function Disable-QAObject {
    param($Session, [string] $Kind, [string] $Path, $Object)
    if ($Kind -eq "MATERIAL") {
        $latest = Invoke-QAData -Session $Session -Method Get -Path "$Path/$($Object.id)"
        Invoke-QAData -Session $Session -Method Put -Path "$Path/$($Object.id)/disable" -Body ([ordered]@{
            version = [long]$latest.version
        }) | Out-Null
        return
    }
    Invoke-QAData -Session $Session -Method Put -Path "$Path/$($Object.id)/disable" | Out-Null
}

function Find-QAOpenReferencedMaterial {
    param($Session)
    $id = Invoke-QAPostgresScalar -Sql @"
select m.id
from mst_material m
where m.status = 'ENABLED'
  and exists (
      select 1
      from mfg_bom_item i
      join mfg_bom b on b.id = i.bom_id
      where i.child_material_id = m.id
        and b.status in ('DRAFT', 'ENABLED')
  )
order by m.id
limit 1;
"@
    if ([string]::IsNullOrWhiteSpace($id)) {
        return $null
    }
    return Invoke-QAData -Session $Session -Method Get -Path "/api/admin/master/materials/$id"
}

function Run-StatusBatchNegativeChecks {
    param(
        $AdminSession,
        $LimitedSession,
        [ValidateSet("SUPPLIER", "MATERIAL")] [string] $Kind,
        [string] $ToolCode,
        [string] $Path,
        $Primary,
        $Secondary,
        $OpenReferenced = $null
    )
    Write-CheckInfo "执行 $Kind 状态批量工具权限、陈旧版本、不可执行状态和整批不提交负例。"
    $previewPath = "/api/admin/platform/batch-tools/$ToolCode/preview"
    $tableName = if ($Kind -eq "SUPPLIER") { "mst_supplier" } else { "mst_material" }
    $forbiddenPayload = New-QAStatusBatchPayload -TargetStatus "DISABLED" `
        -Targets @((New-QABatchTarget -Object $Primary)) -Key "$Kind-BATCH-PERMISSION"
    $forbidden = Invoke-QAApi -Session $LimitedSession -Method Post -Path $previewPath -Body $forbiddenPayload -AllowFailure
    Assert-QAError -Result $forbidden -ExpectedStatusCode 403 -ExpectedCode "AUTH_FORBIDDEN" `
        -CheckCode "A10_${Kind}_BATCH_TOOL_PERMISSION_FORBIDDEN" `
        -Message "$Kind 状态批量工具必须同时检查平台预检权限和领域更新权限，缺少领域权限时不得返回目标信息。"

    $currentVersion = Get-QAObjectVersion -TableName $tableName -Id ([long]$Primary.id)
    $staleVersion = if ($currentVersion -gt 0) { $currentVersion - 1 } else { $currentVersion + 1 }
    $stalePayload = New-QAStatusBatchPayload -TargetStatus "DISABLED" `
        -Targets @((New-QABatchTarget -Object $Primary -Version $staleVersion)) `
        -Key "$Kind-BATCH-STALE-VERSION"
    $stalePreview = Invoke-QAData -Session $AdminSession -Method Post -Path $previewPath -Body $stalePayload
    Assert-QAPrecheckBlocked -Operation $stalePreview -CheckCode "A10_${Kind}_BATCH_TOOL_STALE_VERSION_BLOCKED" `
        -ExpectedCode "BATCH_OPERATION_OBJECT_CHANGED" `
        -Message "$Kind 状态批量工具遇到陈旧版本必须进入预检失败，不得允许执行。"

    $unchangedPayload = New-QAStatusBatchPayload -TargetStatus ([string]$Primary.status) `
        -Targets @((New-QABatchTarget -Object $Primary -Version $currentVersion)) -Key "$Kind-BATCH-UNCHANGED"
    $unchangedPreview = Invoke-QAData -Session $AdminSession -Method Post -Path $previewPath -Body $unchangedPayload
    Assert-QAPrecheckBlocked -Operation $unchangedPreview -CheckCode "A10_${Kind}_BATCH_TOOL_UNCHANGED_STATUS_BLOCKED" `
        -ExpectedCode "BATCH_OPERATION_PRECHECK_FAILED" `
        -Message "$Kind 状态批量工具目标已是同状态时必须判定不可执行。"
    $unchangedExecute = Invoke-QAApi -Session $AdminSession -Method Post `
        -Path "/api/admin/platform/batch-operations/$($unchangedPreview.id)/execute" `
        -Body ([ordered]@{ version = [long]$unchangedPreview.version; idempotencyKey = "$RunId-$Kind-BATCH-UNCHANGED-EXECUTE" }) `
        -AllowFailure
    Assert-QAError -Result $unchangedExecute -ExpectedStatusCode 409 -ExpectedCode "BATCH_OPERATION_STATUS_INVALID" `
        -CheckCode "A10_${Kind}_BATCH_TOOL_UNCHANGED_STATUS_EXECUTE_REJECTED" `
        -Message "$Kind 预检失败操作不得被执行。"

    if ($Kind -eq "MATERIAL" -and $null -eq $OpenReferenced) {
        Add-Check -Code "A10_MATERIAL_BATCH_TOOL_OPEN_REFERENCE_BLOCKED" -Passed:$false `
            -Actual "openReferencedMaterial=none" -Expected "one ENABLED material referenced by DRAFT/ENABLED BOM" `
            -Message "物料状态批量工具开放引用负例需要 FullFacts 中存在被开放 BOM 引用的启用物料。"
    }
    if ($Kind -eq "MATERIAL" -and $null -ne $OpenReferenced) {
        $openReferenceVersion = Get-QAObjectVersion -TableName "mst_material" -Id ([long]$OpenReferenced.id)
        $openReferencePayload = New-QAStatusBatchPayload -TargetStatus "DISABLED" `
            -Targets @((New-QABatchTarget -Object $OpenReferenced -Version $openReferenceVersion)) `
            -Key "$Kind-BATCH-OPEN-REFERENCE"
        $openReferencePreview = Invoke-QAData -Session $AdminSession -Method Post -Path $previewPath -Body $openReferencePayload
        Assert-QAPrecheckBlocked -Operation $openReferencePreview -CheckCode "A10_MATERIAL_BATCH_TOOL_OPEN_REFERENCE_BLOCKED" `
            -ExpectedCode "BATCH_OPERATION_PRECHECK_FAILED" `
            -Message "物料状态批量工具必须阻止停用被开放 BOM 等业务引用的物料。"
    }

    $first = Invoke-QAData -Session $AdminSession -Method Get -Path "$Path/$($Primary.id)"
    $second = Invoke-QAData -Session $AdminSession -Method Get -Path "$Path/$($Secondary.id)"
    if ($first.status -ne "ENABLED" -or $second.status -ne "ENABLED") {
        Add-Check -Code "A10_${Kind}_BATCH_TOOL_REVALIDATION_ALL_OR_NOTHING" -Passed:$false `
            -Actual "first=$($first.status);second=$($second.status)" -Expected "both ENABLED before preview" `
            -Message "$Kind 整批不提交负例需要两个启用目标。"
        return
    }
    $revalidationPayload = New-QAStatusBatchPayload -TargetStatus "DISABLED" `
        -Targets @((New-QABatchTarget -Object $first), (New-QABatchTarget -Object $second)) `
        -Key "$Kind-BATCH-REVALIDATION"
    $operation = Invoke-QAData -Session $AdminSession -Method Post -Path $previewPath -Body $revalidationPayload
    Disable-QAObject -Session $AdminSession -Kind $Kind -Path $Path -Object $first
    $execute = Invoke-QAApi -Session $AdminSession -Method Post -Path "/api/admin/platform/batch-operations/$($operation.id)/execute" `
        -Body ([ordered]@{ version = [long]$operation.version; idempotencyKey = "$RunId-$Kind-BATCH-REVALIDATION-EXECUTE" }) `
        -AllowFailure
    $secondStatus = Get-QAObjectStatus -Session $AdminSession -Path $Path -Id ([long]$second.id)
    $passed = $execute.failed -eq $true -and $execute.code -eq "BATCH_OPERATION_OBJECT_CHANGED" -and $secondStatus -eq "ENABLED"
    Add-Check -Code "A10_${Kind}_BATCH_TOOL_REVALIDATION_ALL_OR_NOTHING" -Passed:$passed `
        -Actual "executeStatus=$($execute.statusCode);executeCode=$($execute.code);secondStatus=$secondStatus" `
        -Expected "BATCH_OPERATION_OBJECT_CHANGED and untouched second target" `
        -Message "$Kind 状态批量工具执行前重新校验发现任一目标变化时，必须整批不提交。"
}

Write-CheckInfo "连接 034 隔离 API：$ApiBaseUrl，Database=$Database，Bucket=$MinioBucket。"
$adminSession = New-DemoApiSession -BaseUrl $ApiBaseUrl -Username $AdminUsername -Password $AdminPassword
$permissionMap = Get-QAPermissionMap -Session $adminSession
$approverRole = Ensure-QARole -Session $adminSession -Code "$RunId-ROLE-APPROVER" -Name "034 幂等审批复核" `
    -PermissionCodes @(
        "platform:approval:view",
        "platform:todo:view",
        "platform:data-repair:view",
        "platform:data-repair:approve",
        "platform:data-repair:verify"
    ) -PermissionMap $permissionMap
Ensure-QAUser -Session $adminSession -Username "$RunId-approver" -DisplayName "034 幂等审批复核" `
    -RoleIds @([long]$approverRole.id) | Out-Null
$limitedBatchRole = Ensure-QARole -Session $adminSession -Code "$RunId-ROLE-BATCH-LIMITED" -Name "034 批量工具无领域权限" `
    -PermissionCodes @(
        "platform:batch-tool:view",
        "platform:batch-tool:preview"
    ) -PermissionMap $permissionMap
Ensure-QAUser -Session $adminSession -Username "$RunId-batch-limited" -DisplayName "034 批量工具无领域权限" `
    -RoleIds @([long]$limitedBatchRole.id) | Out-Null
$approverSession = New-DemoApiSession -BaseUrl $ApiBaseUrl -Username "$RunId-approver" -Password $QaUserPassword
$limitedBatchSession = New-DemoApiSession -BaseUrl $ApiBaseUrl -Username "$RunId-batch-limited" -Password $QaUserPassword
$qaUnit = Ensure-QAUnit -Session $adminSession -Code "$RunId-EA"
$qaCategory = Ensure-QACategory -Session $adminSession -Code "$RunId-CAT"

Run-DataRepairCreateReplayCheck -AdminSession $adminSession -ApproverSession $approverSession
Run-BatchToolPreviewReplayCheck -AdminSession $adminSession
$supplierA = Ensure-QASupplier -Session $adminSession -Code "$RunId-BATCH-SUP-A" -Name "034 批量供应商 A"
$supplierB = Ensure-QASupplier -Session $adminSession -Code "$RunId-BATCH-SUP-B" -Name "034 批量供应商 B"
Run-StatusBatchNegativeChecks -AdminSession $adminSession -LimitedSession $limitedBatchSession `
    -Kind "SUPPLIER" -ToolCode "SUPPLIER_STATUS_CHANGE_V1" -Path "/api/admin/master/suppliers" `
    -Primary $supplierA -Secondary $supplierB
$materialA = Ensure-QAMaterial -Session $adminSession -Code "$RunId-BATCH-MAT-A" -Name "034 批量物料 A" `
    -Unit $qaUnit -Category $qaCategory
$materialB = Ensure-QAMaterial -Session $adminSession -Code "$RunId-BATCH-MAT-B" -Name "034 批量物料 B" `
    -Unit $qaUnit -Category $qaCategory
$openReferencedMaterial = Find-QAOpenReferencedMaterial -Session $adminSession
Run-StatusBatchNegativeChecks -AdminSession $adminSession -LimitedSession $limitedBatchSession `
    -Kind "MATERIAL" -ToolCode "MATERIAL_STATUS_CHANGE_V1" -Path "/api/admin/master/materials" `
    -Primary $materialA -Secondary $materialB -OpenReferenced $openReferencedMaterial

$failedChecks = @($script:Checks | Where-Object { $_.passed -ne $true })
$summary = [ordered]@{
    status = if ($failedChecks.Count -eq 0) { "PASS" } else { "FAIL" }
    runId = $RunId
    apiBaseUrl = $ApiBaseUrl
    database = $Database
    minioBucket = $MinioBucket
    totalChecks = $script:Checks.Count
    failedChecks = $failedChecks.Count
    checks = $script:Checks
    completedAt = (Get-Date).ToString("o")
}
$directory = Split-Path -Parent $OutputJsonPath
New-DemoDirectory -Path $directory | Out-Null
$summary | ConvertTo-Json -Depth 12 | Set-Content -LiteralPath $OutputJsonPath -Encoding UTF8
Write-Host ($summary | ConvertTo-Json -Depth 12)
if ($failedChecks.Count -gt 0) {
    exit 1
}
