[CmdletBinding()]
param(
    [string] $ApiBaseUrl = $(if ($env:QHERP_API_BASE_URL) { $env:QHERP_API_BASE_URL } else { "http://127.0.0.1:18081" }),
    [string] $Database = $(if ($env:QHERP_POSTGRES_DB) { $env:QHERP_POSTGRES_DB } else { "qherp_demo_build_20260715" }),
    [string] $MinioBucket = $(if ($env:QHERP_S3_BUCKET) { $env:QHERP_S3_BUCKET } else { "qherp-demo-build-20260715" }),
    [ValidateSet("Temporary", "Acceptance")][string] $Mode = "Temporary",
    [string] $AcceptanceAuthorizationPath,
    [string] $AcceptanceAuthorizationToken,
    [string] $AdminUsername = "admin",
    [string] $AdminPassword = $env:QHERP_INITIAL_ADMIN_PASSWORD,
    [string] $DemoUserPassword = $env:QHERP_DEMO_USER_PASSWORD,
    [string] $RunId = "DEMO-ELEC-20260715-RUN",
    [ValidateSet("WorkerDisabled", "WorkerEnabled")][string] $DocumentWorkerMode = $(if ($env:QHERP_TASK_WORKER_ENABLED -eq "true") { "WorkerEnabled" } else { "WorkerDisabled" }),
    [string] $OutputManifestPath = (Join-Path (Get-Location).Path "apps/api/target/demo-data/generate-demo-data-manifest.json")
)

$ErrorActionPreference = "Stop"
. (Join-Path $PSScriptRoot "lib/demo-data-common.ps1")

$parsedApiBaseUrl = [uri]$ApiBaseUrl
if ($Mode -eq "Temporary") {
    if (-not (Test-DemoResourceName -Value $Database -Prefix "qherp_demo_build_")) {
        throw "Temporary 模式拒绝生成演示数据：数据库名必须以 qherp_demo_build_ 开头，当前为 $Database。"
    }
    if (-not (Test-DemoResourceName -Value $MinioBucket -Prefix "qherp-demo-build-")) {
        throw "Temporary 模式拒绝生成演示数据：MinIO bucket 必须以 qherp-demo-build- 开头，当前为 $MinioBucket。"
    }
    if ($parsedApiBaseUrl.Port -eq 18080) {
        throw "Temporary 模式拒绝连接 18080 正式后端，当前 ApiBaseUrl=$ApiBaseUrl。"
    }
}
else {
    if ($Database -ne "qherp" -or $MinioBucket -ne "qherp-private") {
        throw "Acceptance 模式只允许目标 Database=qherp 且 Bucket=qherp-private，当前 Database=$Database Bucket=$MinioBucket。"
    }
    if ($parsedApiBaseUrl.Port -ne 18080) {
        throw "Acceptance 模式必须连接正式验收后端 18080，当前 ApiBaseUrl=$ApiBaseUrl。"
    }
    $acceptanceHost = $parsedApiBaseUrl.Host.ToLowerInvariant()
    if ($acceptanceHost -notin @("127.0.0.1", "localhost", "::1", "[::1]")) {
        throw "Acceptance 模式必须限定 loopback 地址，当前 ApiBaseUrl=$ApiBaseUrl。"
    }
    if ([string]::IsNullOrWhiteSpace($AcceptanceAuthorizationPath) -or [string]::IsNullOrWhiteSpace($AcceptanceAuthorizationToken)) {
        throw "Acceptance 模式必须由重建入口传入一次性授权材料，生成器不允许独立直连正式目标。"
    }
    Assert-DemoAcceptanceAuthorization -Path $AcceptanceAuthorizationPath -Token $AcceptanceAuthorizationToken `
        -Database $Database -MinioBucket $MinioBucket -ApiBaseUrl $ApiBaseUrl -RunId $RunId
}
if ([string]::IsNullOrWhiteSpace($AdminPassword)) {
    throw "缺少管理员密码。请通过 -AdminPassword 或 QHERP_INITIAL_ADMIN_PASSWORD 注入，脚本不提供默认明文密码。"
}
if ([string]::IsNullOrWhiteSpace($DemoUserPassword)) {
    throw "缺少演示用户初始密码。请通过 -DemoUserPassword 或 QHERP_DEMO_USER_PASSWORD 注入，脚本不提供默认明文密码。"
}

$Root = (Resolve-Path (Join-Path $PSScriptRoot "../..")).Path
$Manifest = New-DemoManifest -RunId $RunId -OutputPath $OutputManifestPath -GitCommit (Get-DemoGitCommit -Root $Root)
$Session = New-DemoApiSession -BaseUrl $ApiBaseUrl -Username $AdminUsername -Password $AdminPassword
$DemoBaseDate = [datetime]"2026-07-15"
$Today = $DemoBaseDate.ToString("yyyy-MM-dd")
$Yesterday = $DemoBaseDate.AddDays(-1).ToString("yyyy-MM-dd")
$CurrentBomFrom = "2026-07-01"
$CurrentBomTo = "2026-07-31"
$FutureBomFrom = "2026-08-01"
$HistoricalBomFrom = "2026-06-01"
$HistoricalBomTo = "2026-06-30"
$MasterDataEffectiveFrom = "2026-06-01"
$LockedPeriodStart = "2026-06-01"
$LockedPeriodEnd = "2026-06-30"
$OpenPeriodStart = "2026-07-01"
$OpenPeriodEnd = "2026-07-31"
$DemoPrefix = "DEMO-ELEC-20260715"
$OpenPeriodCode = "DE260715-OPEN"
$LockedPeriodCode = "DE260715-LOCK"
$Manifest.AddNote("业务期间 period_code 受 V13 varchar(20) 限制，使用 DE260715-* 作为 DEMO-ELEC-20260715 的字段长度等价短前缀。")
$script:DemoPurchaseReceiptIds = New-Object 'System.Collections.Generic.HashSet[long]'

function Write-Step {
    param([string] $Message)
    Write-Host "[demo-data-generator] $Message"
}

function Get-PropertyValue {
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

function Get-ItemByField {
    param(
        [string] $Path,
        [string] $FieldName,
        [string] $FieldValue,
        [hashtable] $Query = @{}
    )
    $queryParameters = @{} + $Query
    if (-not $queryParameters.ContainsKey("keyword")) {
        $queryParameters["keyword"] = $FieldValue
    }
    $items = Invoke-DemoApiPage -Session $Session -Path $Path -Parameters $queryParameters
    foreach ($item in @($items)) {
        if ([string](Get-PropertyValue -Object $item -Name $FieldName) -eq $FieldValue) {
            return $item
        }
    }
    return $null
}

function Get-ItemById {
    param([string] $Path, [long] $Id)
    return Invoke-DemoApi -Session $Session -Method Get -Path "$Path/$Id"
}

function Ensure-PostedByCode {
    param(
        [string] $Path,
        [string] $Code,
        [hashtable] $Body,
        [string] $ManifestType
    )
    $existing = Get-ItemByField -Path $Path -FieldName "code" -FieldValue $Code
    if ($null -ne $existing) {
        $Manifest.AddObject($ManifestType, $Code, $existing.id)
        return $existing
    }
    $created = Invoke-DemoApi -Session $Session -Method Post -Path $Path -Body $Body
    $Manifest.AddObject($ManifestType, $Code, $created.id)
    return $created
}

function Flatten-Permissions {
    param([object[]] $Nodes)
    $result = New-Object System.Collections.Generic.List[object]
    foreach ($node in @($Nodes)) {
        $result.Add($node)
        $children = Get-PropertyValue -Object $node -Name "children"
        if ($children -is [System.Collections.IEnumerable] -and -not ($children -is [string])) {
            foreach ($child in Flatten-Permissions -Nodes @($children)) {
                $result.Add($child)
            }
        }
    }
    return $result.ToArray()
}

function Get-PermissionMap {
    $tree = Invoke-DemoApi -Session $Session -Method Get -Path "/api/admin/permissions/tree"
    $map = @{}
    foreach ($permission in Flatten-Permissions -Nodes @($tree)) {
        $map[[string]$permission.code] = [long]$permission.id
    }
    return $map
}

function Ensure-Role {
    param(
        [string] $Code,
        [string] $Name,
        [string[]] $PermissionCodes,
        [hashtable] $PermissionMap
    )
    $role = Get-ItemByField -Path "/api/admin/roles" -FieldName "code" -FieldValue $Code
    if ($null -eq $role) {
        $role = Invoke-DemoApi -Session $Session -Method Post -Path "/api/admin/roles" -Body ([ordered]@{
            code = $Code
            name = $Name
            description = "验收演示角色"
            status = "ENABLED"
            sortOrder = 900
        })
    }
    $permissionIds = New-Object System.Collections.Generic.List[long]
    foreach ($permissionCode in $PermissionCodes) {
        if ($PermissionMap.ContainsKey($permissionCode)) {
            $permissionIds.Add([long]$PermissionMap[$permissionCode])
        }
    }
    Invoke-DemoApi -Session $Session -Method Put -Path "/api/admin/roles/$($role.id)/permissions" -Body ([ordered]@{
        permissionIds = $permissionIds.ToArray()
    }) | Out-Null
    $Manifest.AddObject("role", $Code, $role.id)
    return $role
}

function Ensure-User {
    param(
        [string] $Username,
        [string] $DisplayName,
        [long[]] $RoleIds,
        [string] $Status = "ENABLED"
    )
    $existing = Get-ItemByField -Path "/api/admin/users" -FieldName "username" -FieldValue $Username
    if ($null -ne $existing) {
        Invoke-DemoApi -Session $Session -Method Put -Path "/api/admin/users/$($existing.id)/password" -Body ([ordered]@{
            newPassword = $DemoUserPassword
        }) | Out-Null
        if ($Status -eq "ENABLED" -and $existing.status -ne "ENABLED") {
            $existing = Invoke-DemoApi -Session $Session -Method Put -Path "/api/admin/users/$($existing.id)/enable"
        }
        elseif ($Status -ne "ENABLED" -and $existing.status -eq "ENABLED") {
            $existing = Invoke-DemoApi -Session $Session -Method Put -Path "/api/admin/users/$($existing.id)/disable"
        }
        $Manifest.AddObject("user", $Username, $existing.id)
        return $existing
    }
    $created = Invoke-DemoApi -Session $Session -Method Post -Path "/api/admin/users" -Body ([ordered]@{
        username = $Username
        displayName = $DisplayName
        phone = "13800000000"
        email = "$Username@example.invalid"
        initialPassword = $DemoUserPassword
        status = $Status
        roleIds = $RoleIds
    })
    $Manifest.AddObject("user", $Username, $created.id)
    return $created
}

function Ensure-Unit {
    param([string] $Code, [string] $Name, [int] $Scale = 6)
    return Ensure-PostedByCode -Path "/api/admin/master/units" -Code $Code -ManifestType "unit" -Body ([ordered]@{
        code = $Code
        name = $Name
        precisionScale = $Scale
        sortOrder = 10
        status = "ENABLED"
        remark = "验收演示单位"
    })
}

function Ensure-Warehouse {
    param([string] $Code, [string] $Name, [string] $Type = "NORMAL")
    return Ensure-PostedByCode -Path "/api/admin/master/warehouses" -Code $Code -ManifestType "warehouse" -Body ([ordered]@{
        code = $Code
        name = $Name
        warehouseType = $Type
        managerName = "仓储主管"
        address = "桥合电气园区"
        status = "ENABLED"
        remark = "验收演示仓库"
    })
}

function Ensure-Category {
    param([string] $Code, [string] $Name)
    return Ensure-PostedByCode -Path "/api/admin/master/material-categories" -Code $Code -ManifestType "materialCategory" -Body ([ordered]@{
        code = $Code
        name = $Name
        sortOrder = 10
        status = "ENABLED"
        remark = "验收演示分类"
    })
}

function Ensure-Partner {
    param([string] $Kind, [string] $Code, [string] $Name)
    $path = if ($Kind -eq "customer") { "/api/admin/master/customers" } else { "/api/admin/master/suppliers" }
    return Ensure-PostedByCode -Path $path -Code $Code -ManifestType $Kind -Body ([ordered]@{
        code = $Code
        name = $Name
        contactName = "业务联系人"
        contactPhone = "021-60000000"
        status = "ENABLED"
        remark = "验收演示往来单位"
    })
}

function Ensure-SettlementTax {
    param([string] $Kind, $Partner, [int] $Index)
    $path = if ($Kind -eq "customer") {
        "/api/admin/master/customers/$($Partner.id)/settlement-tax"
    }
    else {
        "/api/admin/master/suppliers/$($Partner.id)/settlement-tax"
    }
    $current = Invoke-DemoApi -Session $Session -Method Get -Path $path
    if ($current.hasData -eq $true) {
        return $current
    }
    return Invoke-DemoApi -Session $Session -Method Put -Path $path -Body ([ordered]@{
        invoiceTitle = $Partner.name
        taxNo = "91310000DEMO$Index"
        registeredAddress = "上海市桥合电气演示地址 $Index 号"
        registeredPhone = "021-6000000$Index"
        bankName = "桥合银行上海分行"
        bankAccount = "622200000000000$Index"
        defaultTaxRate = "0.130000"
        invoiceType = "SPECIAL_VAT"
        settlementMethod = "MONTHLY"
        paymentTermDays = 30
        paymentTerms = "月结 30 天"
        remark = "验收演示结算税务资料"
        version = $current.version
    })
}

function Ensure-Material {
    param(
        [string] $Code,
        [string] $Name,
        [string] $MaterialType,
        [string] $SourceType,
        [string] $TrackingMethod,
        [long] $CategoryId,
        [long] $UnitId,
        [bool] $Valued = $true,
        [bool] $ProjectCost = $false
    )
    $existing = Get-ItemByField -Path "/api/admin/master/materials" -FieldName "code" -FieldValue $Code
    if ($null -ne $existing) {
        $Manifest.AddObject("material", $Code, $existing.id)
        return $existing
    }
    $created = Invoke-DemoApi -Session $Session -Method Post -Path "/api/admin/master/materials" -Body ([ordered]@{
        code = $Code
        name = $Name
        specification = "验收规格"
        materialType = $MaterialType
        sourceType = $SourceType
        trackingMethod = $TrackingMethod
        categoryId = $CategoryId
        unitId = $UnitId
        status = "ENABLED"
        remark = "桥合电气验收演示物料"
        costCategory = switch ($MaterialType) {
            "FINISHED_GOOD" { "FINISHED_GOOD" }
            "SEMI_FINISHED" { "SEMI_FINISHED" }
            "AUXILIARY" { "AUXILIARY_MATERIAL" }
            default { "DIRECT_MATERIAL" }
        }
        inventoryValuationCategory = if ($Valued) { "VALUATED_MATERIAL" } else { "NON_VALUATED_CONSUMABLE" }
        inventoryValueEnabled = $Valued
        projectCostEnabled = $ProjectCost
        costRemark = "验收演示成本属性"
    })
    $Manifest.AddObject("material", $Code, $created.id)
    return $created
}

function Ensure-UnitConversion {
    param($Material, $BusinessUnit, [string] $Rate)
    $existing = Invoke-DemoApiPage -Session $Session -Path "/api/admin/master/unit-conversions" -Parameters @{
        keyword = $Material.code
    } | Where-Object { $_.materialId -eq $Material.id -and $_.businessUnitId -eq $BusinessUnit.id } | Select-Object -First 1
    if ($null -ne $existing) {
        return $existing
    }
    return Invoke-DemoApi -Session $Session -Method Post -Path "/api/admin/master/unit-conversions" -Body ([ordered]@{
        materialId = $Material.id
        businessUnitId = $BusinessUnit.id
        conversionRate = $Rate
        quantityScale = 6
        roundingMode = "HALF_UP"
        effectiveFrom = $MasterDataEffectiveFrom
        effectiveTo = $null
        remark = "验收演示单位换算"
    })
}

function Ensure-CodingRule {
    param([string] $RuleCode, [string] $Name, [string] $ObjectType, [string] $Prefix, [string] $Status = "ENABLED")
    $existing = Get-ItemByField -Path "/api/admin/coding-rules" -FieldName "ruleCode" -FieldValue $RuleCode
    if ($null -ne $existing) {
        $Manifest.AddObject("codingRule", $RuleCode, $existing.id)
        return $existing
    }
    $created = Invoke-DemoApi -Session $Session -Method Post -Path "/api/admin/coding-rules" -Body ([ordered]@{
        ruleCode = $RuleCode
        name = $Name
        objectType = $ObjectType
        prefix = $Prefix
        datePattern = "YYYYMMDD"
        serialLength = 4
        resetCycle = "DAY"
        nextSerialNo = 1
        status = $Status
        remark = "验收演示编码规则"
    })
    $Manifest.AddObject("codingRule", $RuleCode, $created.id)
    return $created
}

function Ensure-Period {
    param([string] $Code, [string] $Name, [string] $Start, [string] $End, [bool] $Locked = $false)
    $existing = Get-ItemByField -Path "/api/admin/system/business-periods" -FieldName "periodCode" -FieldValue $Code -Query @{ periodCode = $Code }
    if ($null -eq $existing) {
        $existing = Invoke-DemoApi -Session $Session -Method Post -Path "/api/admin/system/business-periods" -Body ([ordered]@{
            periodCode = $Code
            periodName = $Name
            startDate = $Start
            endDate = $End
        })
    }
    if ($Locked -and $existing.status -ne "LOCKED") {
        $existing = Invoke-DemoApi -Session $Session -Method Post -Path "/api/admin/system/business-periods/$($existing.id)/lock" -Body ([ordered]@{
            reason = "验收演示锁定期间"
        })
    }
    $Manifest.AddObject("period", $Code, $existing.id)
    return $existing
}

function Ensure-PeriodAuditSamples {
    param($LockedPeriod)
    $latest = Get-ItemByField -Path "/api/admin/system/business-periods" -FieldName "periodCode" `
        -FieldValue $LockedPeriod.periodCode -Query @{ periodCode = $LockedPeriod.periodCode }
    if ($latest.status -eq "LOCKED") {
        $latest = Invoke-DemoApi -Session $Session -Method Post -Path "/api/admin/system/business-periods/$($latest.id)/unlock" -Body ([ordered]@{
            reason = "验收演示期间审计解锁样例"
        })
    }
    if ($latest.status -eq "OPEN") {
        $latest = Invoke-DemoApi -Session $Session -Method Post -Path "/api/admin/system/business-periods/$($latest.id)/lock" -Body ([ordered]@{
            reason = "验收演示期间审计重新锁定样例"
        })
    }
    return $latest
}

function Ensure-Bom {
    param(
        [string] $Code,
        $Parent,
        [string] $VersionCode,
        [string] $Name,
        [string] $Status,
        [string] $EffectiveFrom,
        [string] $EffectiveTo,
        [object[]] $Items
    )
    $existing = Get-ItemByField -Path "/api/admin/boms" -FieldName "bomCode" -FieldValue $Code
    if ($null -eq $existing) {
        $existing = Invoke-DemoApi -Session $Session -Method Post -Path "/api/admin/boms" -Body ([ordered]@{
            bomCode = $Code
            parentMaterialId = $Parent.id
            versionCode = $VersionCode
            name = $Name
            baseQuantity = "1.000000"
            baseUnitId = $Parent.unitId
            status = "DRAFT"
            effectiveFrom = $EffectiveFrom
            effectiveTo = $EffectiveTo
            remark = "桥合电气验收演示 BOM"
            items = $Items
        })
    }
    if ($Status -eq "ENABLED" -and $existing.status -ne "ENABLED") {
        $detail = Get-ItemById -Path "/api/admin/boms" -Id $existing.id
        $existing = Invoke-DemoApi -Session $Session -Method Put -Path "/api/admin/boms/$($existing.id)/enable" -Body ([ordered]@{
            version = $detail.version
        })
    }
    $Manifest.AddObject("bom", $Code, $existing.id)
    return $existing
}

function Ensure-Substitute {
    param($Main, $Substitute, [string] $ScopeType = "GLOBAL", [Nullable[long]] $ScopeId = $null)
    $existing = Invoke-DemoApiPage -Session $Session -Path "/api/admin/material-substitutes" -Parameters @{
        keyword = $Main.code
    } | Where-Object { $_.mainMaterialId -eq $Main.id -and $_.substituteMaterialId -eq $Substitute.id } | Select-Object -First 1
    if ($null -ne $existing) {
        return $existing
    }
    return Invoke-DemoApi -Session $Session -Method Post -Path "/api/admin/material-substitutes" -Body ([ordered]@{
        mainMaterialId = $Main.id
        substituteMaterialId = $Substitute.id
        scopeType = $ScopeType
        scopeId = $ScopeId
        priority = 1
        substituteRate = "1.000000"
        status = "ENABLED"
        effectiveFrom = $MasterDataEffectiveFrom
        effectiveTo = $null
        remark = "验收演示替代料"
    })
}

function Invoke-DemoMultipart {
    param(
        [string] $Path,
        [hashtable] $Fields,
        [string] $FilePath,
        [string] $FileFieldName = "file",
        [string] $ContentType = "text/plain",
        [hashtable] $Headers = @{}
    )
    $headers = @{} + $Headers
    $headers[$Session.csrfHeaderName] = $Session.csrfToken
    if ($ContentType) {
        $headers["X-QHERP-Test-Content-Type"] = $ContentType
    }
    $form = @{} + $Fields
    $form[$FileFieldName] = Get-Item -LiteralPath $FilePath
    $response = Invoke-RestMethod -Method Post -Uri "$($Session.baseUrl)$Path" -WebSession $Session.webSession -Headers $headers -Form $form
    if ($response.success -ne $true) {
        throw "API 返回失败：POST $Path code=$($response.code) message=$($response.message)"
    }
    return $response.data
}

function New-DemoXlsx {
    param(
        [string] $Path,
        [hashtable] $Sheets
    )
    Add-Type -AssemblyName System.IO.Compression
    Add-Type -AssemblyName System.IO.Compression.FileSystem
    if (Test-Path -LiteralPath $Path) {
        Remove-Item -LiteralPath $Path -Force
    }
    $directory = Split-Path -Parent $Path
    New-DemoDirectory -Path $directory | Out-Null
    $fileStream = [System.IO.File]::Open($Path, [System.IO.FileMode]::CreateNew)
    try {
        $zip = [System.IO.Compression.ZipArchive]::new($fileStream, [System.IO.Compression.ZipArchiveMode]::Create)
        try {
            Add-XlsxEntry -Zip $zip -Name "[Content_Types].xml" -Content '<?xml version="1.0" encoding="UTF-8"?><Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="xml" ContentType="application/xml"/><Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/></Types>'
            Add-XlsxEntry -Zip $zip -Name "_rels/.rels" -Content '<?xml version="1.0" encoding="UTF-8"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/></Relationships>'
            $sheetNames = @($Sheets.Keys | Sort-Object)
            $workbookSheets = New-Object System.Text.StringBuilder
            $rels = New-Object System.Text.StringBuilder
            [void]$workbookSheets.Append('<sheets>')
            [void]$rels.Append('<?xml version="1.0" encoding="UTF-8"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">')
            for ($i = 0; $i -lt $sheetNames.Count; $i++) {
                $sheetId = $i + 1
                $name = [System.Security.SecurityElement]::Escape($sheetNames[$i])
                [void]$workbookSheets.Append("<sheet name=`"$name`" sheetId=`"$sheetId`" r:id=`"rId$sheetId`"/>")
                [void]$rels.Append("<Relationship Id=`"rId$sheetId`" Type=`"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet`" Target=`"worksheets/sheet$sheetId.xml`"/>")
                Add-XlsxEntry -Zip $zip -Name "xl/worksheets/sheet$sheetId.xml" -Content (New-XlsxSheetXml -Rows $Sheets[$sheetNames[$i]])
            }
            [void]$workbookSheets.Append('</sheets>')
            [void]$rels.Append('</Relationships>')
            Add-XlsxEntry -Zip $zip -Name "xl/workbook.xml" -Content ('<?xml version="1.0" encoding="UTF-8"?><workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">' + $workbookSheets.ToString() + '</workbook>')
            Add-XlsxEntry -Zip $zip -Name "xl/_rels/workbook.xml.rels" -Content $rels.ToString()
        }
        finally {
            $zip.Dispose()
        }
    }
    finally {
        $fileStream.Dispose()
    }
}

function Add-XlsxEntry {
    param($Zip, [string] $Name, [string] $Content)
    $StableXlsxEntryTime = [System.DateTimeOffset]::new(2026, 7, 15, 0, 0, 0, [System.TimeSpan]::Zero)
    $entry = $Zip.CreateEntry($Name)
    $entry.LastWriteTime = $StableXlsxEntryTime
    $writer = [System.IO.StreamWriter]::new($entry.Open(), [System.Text.UTF8Encoding]::new($false))
    try {
        $writer.Write($Content)
    }
    finally {
        $writer.Dispose()
    }
}

function New-XlsxSheetXml {
    param([object[]] $Rows)
    $builder = [System.Text.StringBuilder]::new()
    [void]$builder.Append('<?xml version="1.0" encoding="UTF-8"?><worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><sheetData>')
    for ($r = 0; $r -lt $Rows.Count; $r++) {
        [void]$builder.Append("<row r=`"$($r + 1)`">")
        $row = @($Rows[$r])
        for ($c = 0; $c -lt $row.Count; $c++) {
            $cellRef = (ConvertTo-XlsxColumnName -Index $c) + ($r + 1)
            $value = [System.Security.SecurityElement]::Escape([string]$row[$c])
            [void]$builder.Append("<c r=`"$cellRef`" t=`"inlineStr`"><is><t>$value</t></is></c>")
        }
        [void]$builder.Append("</row>")
    }
    [void]$builder.Append('</sheetData></worksheet>')
    return $builder.ToString()
}

function ConvertTo-XlsxColumnName {
    param([int] $Index)
    $name = ""
    $value = $Index + 1
    while ($value -gt 0) {
        $mod = ($value - 1) % 26
        $name = [char](65 + $mod) + $name
        $value = [math]::Floor(($value - $mod) / 26)
    }
    return $name
}

function Upload-Attachment {
    param([string] $ObjectType, [long] $ObjectId, [string] $AssetName, [string] $Description)
    $assetPath = Join-Path $PSScriptRoot "assets/$AssetName"
    $existing = Invoke-DemoApiPage -Session $Session -Path "/api/admin/attachments" -Parameters @{
        objectType = $ObjectType
        objectId = $ObjectId
    } | Where-Object { $_.fileName -eq $AssetName -or $_.originalFilename -eq $AssetName } | Select-Object -First 1
    if ($null -ne $existing) {
        $Manifest.AddFile([ordered]@{
            category = "attachment"
            objectType = $ObjectType
            objectId = $ObjectId
            downloadableId = $existing.id
            downloadPath = "/api/admin/attachments/$($existing.id)/download"
            fileName = $AssetName
            sha256 = $existing.sha256
        })
        return $existing
    }
    $contentType = if ($AssetName.EndsWith(".csv")) { "text/csv" } else { "text/plain" }
    $uploaded = Invoke-DemoMultipart -Path "/api/admin/attachments" -Fields @{
        objectType = $ObjectType
        objectId = "$ObjectId"
        description = $Description
        idempotencyKey = "$RunId-$ObjectType-$ObjectId-$AssetName"
    } -FilePath $assetPath -ContentType $contentType
    $Manifest.AddFile([ordered]@{
        category = "attachment"
        objectType = $ObjectType
        objectId = $ObjectId
        downloadableId = $uploaded.id
        downloadPath = "/api/admin/attachments/$($uploaded.id)/download"
        fileName = $AssetName
        sha256 = $uploaded.sha256
    })
    return $uploaded
}

function Ensure-AdditionalDemoAttachments {
    param(
        $MainContract,
        $SupplementContract,
        $RejectContract,
        $WithdrawContract,
        $CancelContract,
        $Eco
    )
    Upload-Attachment -ObjectType "SALES_PROJECT_CONTRACT" -ObjectId $SupplementContract.id `
        -AssetName "bridge-electrical-production-note.txt" -Description "补充合同生产交付说明" | Out-Null
    Upload-Attachment -ObjectType "SALES_PROJECT_CONTRACT" -ObjectId $WithdrawContract.id `
        -AssetName "bridge-electrical-quality-note.txt" -Description "撤回合同质量补充材料" | Out-Null
    Upload-Attachment -ObjectType "SALES_PROJECT_CONTRACT" -ObjectId $CancelContract.id `
        -AssetName "bridge-electrical-import-source.csv" -Description "取消合同数据校验附件" | Out-Null
    Upload-Attachment -ObjectType "BOM_ENGINEERING_CHANGE" -ObjectId $Eco.id `
        -AssetName "bridge-electrical-eco-note.txt" -Description "ECO 审批依据" | Out-Null
    Upload-Attachment -ObjectType "BOM_ENGINEERING_CHANGE" -ObjectId $Eco.id `
        -AssetName "bridge-electrical-stocktake-evidence.txt" -Description "ECO 后续盘点依据样例" | Out-Null
    Upload-Attachment -ObjectType "BOM_ENGINEERING_CHANGE" -ObjectId $Eco.id `
        -AssetName "bridge-electrical-valuation-note.txt" -Description "ECO 成本影响说明" | Out-Null
    $Manifest.AddObject("attachmentObject", "MAIN-CONTRACT", $MainContract.id)
}

function Ensure-BomEcoDraft {
    param(
        [string] $EcoNo,
        $SourceBom,
        $TargetBom,
        [string] $EffectiveFrom,
        [string] $Summary
    )
    $existing = Get-ItemByField -Path "/api/admin/bom-engineering-changes" -FieldName "ecoNo" -FieldValue $EcoNo
    if ($null -ne $existing) {
        $Manifest.AddObject("eco", $EcoNo, $existing.id)
        return $existing
    }
    $created = Invoke-DemoApi -Session $Session -Method Post -Path "/api/admin/bom-engineering-changes" -Body ([ordered]@{
        ecoNo = $EcoNo
        sourceBomId = $SourceBom.id
        targetBomId = $TargetBom.id
        parentMaterialId = $SourceBom.parentMaterialId
        effectiveFrom = $EffectiveFrom
        effectiveTo = $null
        changeReason = "验收演示工程变更排程"
        impactScope = "后续订单"
        changeSummary = $Summary
        status = "DRAFT"
        remark = "验收演示 DRAFT ECO"
    })
    $Manifest.AddObject("eco", $EcoNo, $created.id)
    return $created
}

function Get-FirstByRemark {
    param([string] $Path, [string] $Remark, [hashtable] $Query = @{})
    return Invoke-DemoApiPage -Session $Session -Path $Path -Parameters $Query |
        Where-Object { $_.remark -eq $Remark } |
        Select-Object -First 1
}

function Ensure-PurchaseOrder {
    param([string] $Key, $Supplier, [object[]] $Lines, [bool] $PublicDirect = $true)
    $remark = "验收演示采购订单 $Key"
    $existing = Get-FirstByRemark -Path "/api/admin/procurement/orders" -Remark $remark
    if ($null -eq $existing) {
        $body = [ordered]@{
            supplierId = $Supplier.id
            orderDate = "2026-07-02"
            expectedArrivalDate = "2026-07-06"
            remark = $remark
            lines = $Lines
        }
        if ($PublicDirect) {
            $body.publicDirectReason = "验收演示公共直采：生产备料与质量追溯样例需要"
        }
        $existing = Invoke-DemoApi -Session $Session -Method Post -Path "/api/admin/procurement/orders" -Body $body
    }
    if ($existing.status -eq "DRAFT") {
        if ($PublicDirect) {
            $approval = Invoke-DemoApi -Session $Session -Method Post -Path "/api/admin/procurement/orders/$($existing.id)/submit-exception" -Body ([ordered]@{
                version = $existing.version
                reason = "验收演示公共直采例外审批"
                idempotencyKey = "$RunId-PO-$Key-EXCEPTION-SUBMIT"
            })
            $approved = Invoke-ApprovalTaskAction -Approval $approval -Action "approve" `
                -Comment "同意验收演示公共直采确认" -Key "$RunId-PO-$Key-EXCEPTION-APPROVE"
            $Manifest.AddObject("approval", "PO-$Key", $approved.id)
            $existing = Get-ItemById -Path "/api/admin/procurement/orders" -Id $existing.id
            if ($existing.status -ne "CONFIRMED") {
                throw "公共直采采购订单 $Key 例外审批通过后未自动确认，当前状态：$($existing.status)。"
            }
        }
        else {
            $existing = Invoke-DemoApi -Session $Session -Method Put -Path "/api/admin/procurement/orders/$($existing.id)/confirm" -Body ([ordered]@{
                version = $existing.version
                reason = "验收演示确认采购订单"
                idempotencyKey = "$RunId-PO-$Key-CONFIRM"
            })
        }
    }
    $Manifest.AddObject("purchaseOrder", $Key, $existing.id)
    return Get-ItemById -Path "/api/admin/procurement/orders" -Id $existing.id
}

function Ensure-PurchaseReceipt {
    param([string] $Key, $Order, $Warehouse, [object[]] $Lines)
    $remark = "验收演示采购入库 $Key"
    $existing = Get-FirstByRemark -Path "/api/admin/procurement/receipts" -Remark $remark -Query @{ orderId = $Order.id }
    if ($null -eq $existing) {
        $existing = Invoke-DemoApi -Session $Session -Method Post -Path "/api/admin/procurement/orders/$($Order.id)/receipts" -Body ([ordered]@{
            warehouseId = $Warehouse.id
            businessDate = "2026-07-04"
            remark = $remark
            lines = $Lines
        })
    }
    if ($existing.status -eq "DRAFT") {
        $existing = Invoke-DemoApi -Session $Session -Method Put -Path "/api/admin/procurement/receipts/$($existing.id)/post" -Body ([ordered]@{
            version = $existing.version
            reason = "验收演示采购入库过账"
            idempotencyKey = "$RunId-PR-$Key-POST"
        })
    }
    $Manifest.AddObject("purchaseReceipt", $Key, $existing.id)
    $script:DemoPurchaseReceiptIds.Add([long]$existing.id) | Out-Null
    return Get-ItemById -Path "/api/admin/procurement/receipts" -Id $existing.id
}

function Process-PendingQualityInspections {
    param(
        [string] $SourceType = "PURCHASE_RECEIPT",
        [long[]] $SourceIds = @(),
        [string] $BusinessDate = "2026-07-05"
    )
    if ($SourceIds.Count -eq 0 -and $SourceType -eq "PURCHASE_RECEIPT") {
        $SourceIds = @($script:DemoPurchaseReceiptIds)
    }
    $pending = Invoke-DemoApiPage -Session $Session -Path "/api/admin/quality/inspections" -Parameters @{
        sourceType = $SourceType
        status = "PENDING"
        page = 1
        pageSize = 100
    }
    foreach ($summary in @($pending)) {
        $detail = Invoke-DemoApi -Session $Session -Method Get -Path "/api/admin/quality/inspections/$($summary.id)"
        if ($SourceIds.Count -gt 0 -and -not $SourceIds.Contains([long]$detail.sourceId)) {
            continue
        }
        $quantity = [decimal]$detail.inspectionQuantity
        $allocations = @($detail.trackingAllocations)
        $payloadAllocations = New-Object System.Collections.Generic.List[object]
        $qualified = $quantity
        $rejected = [decimal]"0"
        $frozen = [decimal]"0"
        if ($detail.materialCode -like "*RAW-CU") {
            $qualified = [decimal]"90.000000"
            $rejected = [decimal]"5.000000"
            $frozen = [decimal]"5.000000"
            if ($allocations.Count -lt 3) {
                throw "铜排质检需要三个独立来源分配，避免同一 sourceAllocationId 被拆分为多种质量状态。Inspection=$($detail.id)"
            }
            foreach ($allocation in $allocations) {
                $targetStatus = if ($allocation.batchNo -like "*BATCH-CU-Q*") {
                    "QUALIFIED"
                }
                elseif ($allocation.batchNo -like "*BATCH-CU-R*") {
                    "REJECTED"
                }
                elseif ($allocation.batchNo -like "*BATCH-CU-F*") {
                    "FROZEN"
                }
                else {
                    throw "铜排质检来源批次不符合 DEMO-ELEC 拆分约定：$($allocation.batchNo)"
                }
                $payloadAllocations.Add([ordered]@{
                    batchId = $allocation.batchId
                    serialId = $allocation.serialId
                    quantity = $allocation.quantity
                    qualityStatus = $targetStatus
                    sourceAllocationId = $allocation.sourceAllocationId
                })
            }
        }
        elseif ($detail.materialCode -like "*RAW-BREAKER" -or $detail.materialCode -like "*RAW-METER" -or $detail.materialCode -like "*RAW-PLC") {
            $qualifiedCount = [math]::Max(0, $allocations.Count - 1)
            $qualified = [decimal]$qualifiedCount
            $rejected = [decimal]($allocations.Count - $qualifiedCount)
            foreach ($allocation in $allocations) {
                $status = if ($payloadAllocations.Count -lt $qualifiedCount) { "QUALIFIED" } else { "REJECTED" }
                $payloadAllocations.Add([ordered]@{
                    batchId = $allocation.batchId
                    serialId = $allocation.serialId
                    quantity = "1.000000"
                    qualityStatus = $status
                    sourceAllocationId = $allocation.sourceAllocationId
                })
            }
        }
        elseif ($allocations.Count -gt 0) {
            foreach ($allocation in $allocations) {
                $payloadAllocations.Add([ordered]@{
                    batchId = $allocation.batchId
                    serialId = $allocation.serialId
                    quantity = $allocation.quantity
                    qualityStatus = "QUALIFIED"
                    sourceAllocationId = $allocation.sourceAllocationId
                })
            }
        }
        $body = [ordered]@{
            businessDate = $BusinessDate
            qualifiedQuantity = $qualified.ToString("0.000000", [Globalization.CultureInfo]::InvariantCulture)
            rejectedQuantity = $rejected.ToString("0.000000", [Globalization.CultureInfo]::InvariantCulture)
            frozenQuantity = $frozen.ToString("0.000000", [Globalization.CultureInfo]::InvariantCulture)
            reason = "验收演示质检完成"
            remark = "按真实质检接口拆分质量状态"
        }
        if ($payloadAllocations.Count -gt 0) {
            $body.trackingAllocations = $payloadAllocations.ToArray()
        }
        Invoke-DemoApi -Session $Session -Method Post -Path "/api/admin/quality/inspections/$($detail.id)/process" -Body $body | Out-Null
    }
}

function New-DemoUserSession {
    param([string] $Username)
    return New-DemoApiSession -BaseUrl $ApiBaseUrl -Username $Username -Password $DemoUserPassword
}

function Ensure-DeniedAuditSample {
    $limitedSession = New-DemoUserSession -Username "$DemoPrefix-no-cost"
    $response = Invoke-DemoApi -Session $limitedSession -Method Get `
        -Path "/api/admin/print-templates?sceneCode=SALES_PROJECT_CONTRACT_ACTIVATION" -AllowFailure
    if ($response.failed -ne $true) {
        throw "低权限用户访问打印模板应被拒绝，用于生成权限拒绝审计样例。"
    }
    $Manifest.AddObject("auditSample", "DENIED-PRINT-TEMPLATE", "AUTH_FORBIDDEN")
}

function Get-InventoryBatch {
    param([string] $BatchNo)
    $batch = Invoke-DemoApiPage -Session $Session -Path "/api/admin/inventory/batches" -Parameters @{
        batchNo = $BatchNo
        onlyAvailable = "true"
        page = 1
        pageSize = 100
    } | Where-Object { $_.batchNo -eq $BatchNo } | Select-Object -First 1
    if ($null -eq $batch) {
        throw "未找到可用批次：$BatchNo。"
    }
    return $batch
}

function Submit-And-ApproveInventoryDocument {
    param(
        [Parameter(Mandatory = $true)] $Document,
        [Parameter(Mandatory = $true)][string] $Path,
        [Parameter(Mandatory = $true)][string] $Key,
        [Parameter(Mandatory = $true)][string] $Reason
    )
    $latest = Get-ItemById -Path $Path -Id $Document.id
    if ($latest.status -eq "POSTED") {
        return $latest
    }
    if ($latest.status -eq "DRAFT" -or $latest.status -eq "RECONCILED") {
        $latest = Invoke-DemoApi -Session $Session -Method Put -Path "$Path/$($latest.id)/submit-approval" -Body ([ordered]@{
            version = $latest.version
            reason = $Reason
            idempotencyKey = "$RunId-$Key-SUBMIT"
        })
    }
    if ($latest.status -eq "SUBMITTED") {
        $approved = Invoke-ApprovalTaskAction -Approval $latest.approvalSummary -Action "approve" `
            -Comment "审批通过，执行库存价值过账" -Key "$RunId-$Key-APPROVE"
        $Manifest.AddObject("approval", $Key, $approved.id)
    }
    return Get-ItemById -Path $Path -Id $latest.id
}

function Ensure-OwnershipConversionPosted {
    param(
        [string] $Key,
        [string] $Reason,
        [object[]] $Lines
    )
    $existing = Invoke-DemoApiPage -Session $Session -Path "/api/admin/inventory/ownership-conversions" -Parameters @{
        keyword = $Reason
        page = 1
        pageSize = 100
    } | Where-Object { $_.reason -eq $Reason } | Select-Object -First 1
    if ($null -eq $existing) {
        $existing = Invoke-DemoApi -Session $Session -Method Post -Path "/api/admin/inventory/ownership-conversions" -Body ([ordered]@{
            businessDate = "2026-07-09"
            reason = $Reason
            idempotencyKey = "$RunId-OWN-$Key-CREATE"
            lines = $Lines
        })
    }
    $posted = Submit-And-ApproveInventoryDocument -Document $existing -Path "/api/admin/inventory/ownership-conversions" `
        -Key "OWN-$Key" -Reason $Reason
    $Manifest.AddObject("ownershipConversion", $Key, $posted.id)
    return $posted
}

function Ensure-WarehouseTransferPosted {
    param(
        [string] $Key,
        [string] $Reason,
        [object[]] $Lines
    )
    $existing = Invoke-DemoApiPage -Session $Session -Path "/api/admin/inventory/warehouse-transfers" -Parameters @{
        keyword = $Reason
        page = 1
        pageSize = 100
    } | Where-Object { $_.reason -eq $Reason } | Select-Object -First 1
    if ($null -eq $existing) {
        $existing = Invoke-DemoApi -Session $Session -Method Post -Path "/api/admin/inventory/warehouse-transfers" -Body ([ordered]@{
            businessDate = "2026-07-10"
            reason = $Reason
            idempotencyKey = "$RunId-TRF-$Key-CREATE"
            lines = $Lines
        })
    }
    $latest = Get-ItemById -Path "/api/admin/inventory/warehouse-transfers" -Id $existing.id
    if ($latest.status -eq "DRAFT") {
        $latest = Invoke-DemoApi -Session $Session -Method Put -Path "/api/admin/inventory/warehouse-transfers/$($latest.id)/post" -Body ([ordered]@{
            version = $latest.version
            reason = $Reason
            idempotencyKey = "$RunId-TRF-$Key-POST"
        })
    }
    $Manifest.AddObject("warehouseTransfer", $Key, $latest.id)
    return Get-ItemById -Path "/api/admin/inventory/warehouse-transfers" -Id $latest.id
}

function Find-ApprovalTask {
    param(
        [Parameter(Mandatory = $true)] $ApprovalSession,
        [Parameter(Mandatory = $true)] $Approval,
        [string] $SceneCode
    )
    $tasks = Invoke-DemoApiPage -Session $approvalSession -Path "/api/admin/approval-tasks" -Parameters @{
        scope = "TODO"
        page = 1
        pageSize = 100
    }
    $task = @($tasks | Where-Object {
            ($_.id -eq $Approval.id -or $_.objectId -eq $Approval.objectId -or $_.businessObjectId -eq $Approval.objectId) `
                -and ([string]::IsNullOrWhiteSpace($SceneCode) -or $_.sceneCode -eq $SceneCode)
        } | Select-Object -First 1)
    if ($task.Count -eq 0 -or $null -eq $task[0]) {
        throw "未找到审批 $($Approval.id) 的候选待办，无法执行真实审批动作。"
    }
    return $task[0]
}

function Invoke-ApprovalTaskAction {
    param(
        [Parameter(Mandatory = $true)] $Approval,
        [Parameter(Mandatory = $true)][ValidateSet("approve", "reject")] [string] $Action,
        [Parameter(Mandatory = $true)][string] $Comment,
        [Parameter(Mandatory = $true)][string] $Key
    )
    $approvalSession = New-DemoUserSession -Username "$DemoPrefix-approval"
    $task = Find-ApprovalTask -ApprovalSession $approvalSession -Approval $Approval -SceneCode $Approval.sceneCode
    return Invoke-DemoApi -Session $approvalSession -Method Post -Path "/api/admin/approval-tasks/$($task.taskId)/$Action" -Body ([ordered]@{
        version = $task.version
        comment = $Comment
        idempotencyKey = $Key
    })
}

function Invoke-ApprovalInstanceAction {
    param(
        [Parameter(Mandatory = $true)] $Approval,
        [Parameter(Mandatory = $true)][ValidateSet("withdraw", "cancel")] [string] $Action,
        [Parameter(Mandatory = $true)][string] $Comment,
        [Parameter(Mandatory = $true)][string] $Key
    )
    return Invoke-DemoApi -Session $Session -Method Post -Path "/api/admin/approvals/$($Approval.id)/$Action" -Body ([ordered]@{
        version = $Approval.version
        comment = $Comment
        idempotencyKey = $Key
    })
}

function Submit-And-ActSalesContractApproval {
    param(
        [Parameter(Mandatory = $true)] $Contract,
        [Parameter(Mandatory = $true)][ValidateSet("approve", "reject", "withdraw", "cancel")] [string] $Action,
        [Parameter(Mandatory = $true)][string] $Key
    )
    $latest = Get-ItemById -Path "/api/admin/sales-project-contracts" -Id $Contract.id
    $desiredApprovalStatus = switch ($Action) {
        "approve" { "APPROVED" }
        "reject" { "REJECTED" }
        "withdraw" { "WITHDRAWN" }
        "cancel" { "CANCELLED" }
    }
    if ($Action -eq "approve" -and $latest.status -eq "EFFECTIVE") {
        return $latest
    }
    if ($null -ne $latest.approvalSummary -and $latest.approvalSummary.status -eq $desiredApprovalStatus) {
        return $latest
    }
    $submitted = Invoke-DemoApi -Session $Session -Method Post -Path "/api/admin/approvals/sales-project-contract-activation/$($latest.id)/submit" -Body ([ordered]@{
        version = $latest.version
        reason = "验收演示合同生效审批"
        idempotencyKey = "$RunId-CONTRACT-$Key-SUBMIT"
    })
    $result = switch ($Action) {
        "approve" {
            Invoke-ApprovalTaskAction -Approval $submitted -Action "approve" -Comment "同意合同生效" -Key "$RunId-CONTRACT-$Key-APPROVE"
        }
        "reject" {
            Invoke-ApprovalTaskAction -Approval $submitted -Action "reject" -Comment "合同资料需补充" -Key "$RunId-CONTRACT-$Key-REJECT"
        }
        "withdraw" {
            Invoke-ApprovalInstanceAction -Approval $submitted -Action "withdraw" -Comment "发起人补充附件后再提交" -Key "$RunId-CONTRACT-$Key-WITHDRAW"
        }
        "cancel" {
            Invoke-ApprovalInstanceAction -Approval $submitted -Action "cancel" -Comment "演示治理取消审批" -Key "$RunId-CONTRACT-$Key-CANCEL"
        }
    }
    $Manifest.AddObject("approval", "CONTRACT-$Key", $result.id)
    return Get-ItemById -Path "/api/admin/sales-project-contracts" -Id $latest.id
}

function Ensure-PendingApprovalTaskSample {
    param($Project, $MainContract)
    $pendingContract = Ensure-SalesContract -Key "$DemoPrefix-CONTRACT-PENDING" -Project $Project -ContractType "SUPPLEMENT" `
        -MainContractId $MainContract.id -Name "桥合低压柜技改待办补充合同" -Amount "8600.00" -ExternalNo "$DemoPrefix-EXT-PENDING"
    Upload-Attachment -ObjectType "SALES_PROJECT_CONTRACT" -ObjectId $pendingContract.id `
        -AssetName "bridge-electrical-contract-note.txt" -Description "待办合同审批依据" | Out-Null
    $latest = Get-ItemById -Path "/api/admin/sales-project-contracts" -Id $pendingContract.id
    if ($null -ne $latest.approvalSummary -and $latest.approvalSummary.status -eq "SUBMITTED") {
        $Manifest.AddObject("pendingApproval", "PENDING-CONTRACT", $latest.approvalSummary.id)
        return $latest
    }
    if ($latest.status -eq "DRAFT") {
        $submitted = Invoke-DemoApi -Session $Session -Method Post -Path "/api/admin/approvals/sales-project-contract-activation/$($latest.id)/submit" -Body ([ordered]@{
            version = $latest.version
            reason = "验收演示保留待办审批任务"
            idempotencyKey = "$RunId-CONTRACT-PENDING-SUBMIT"
        })
        $Manifest.AddObject("pendingApproval", "PENDING-CONTRACT", $submitted.id)
        return Get-ItemById -Path "/api/admin/sales-project-contracts" -Id $latest.id
    }
    return $latest
}

function Submit-And-ApproveBomEco {
    param($Eco)
    if ($Eco.status -eq "APPLIED") {
        return $Eco
    }
    $submit = Invoke-DemoApi -Session $Session -Method Post -Path "/api/admin/approvals/bom-eco-application/$($Eco.id)/submit" -Body ([ordered]@{
        version = $Eco.version
        reason = "验收演示通过固定审批应用 ECO"
        idempotencyKey = "$RunId-BOM-ECO-$($Eco.id)-SUBMIT"
    })
    $approved = Invoke-ApprovalTaskAction -Approval $submit -Action "approve" -Comment "审批通过，应用 BOM 未来版本" -Key "$RunId-BOM-ECO-$($Eco.id)-APPROVE"
    $Manifest.AddObject("approval", "BOM-ECO-$($Eco.id)", $approved.id)
    return Get-ItemById -Path "/api/admin/bom-engineering-changes" -Id $Eco.id
}

function Ensure-SalesOrderConfirmed {
    param(
        [string] $Key,
        $Customer,
        [object[]] $Lines,
        $Project = $null,
        $Contract = $null
    )
    $remark = "验收演示销售订单 $Key"
    $existing = Get-FirstByRemark -Path "/api/admin/sales/orders" -Remark $remark
    if ($null -eq $existing) {
        $body = [ordered]@{
            customerId = $Customer.id
            orderDate = "2026-07-10"
            expectedShipDate = "2026-07-14"
            remark = $remark
            lines = $Lines
        }
        if ($null -ne $Project) {
            $body.projectId = $Project.id
        }
        if ($null -ne $Contract) {
            $body.contractId = $Contract.id
        }
        $existing = Invoke-DemoApi -Session $Session -Method Post -Path "/api/admin/sales/orders" -Body $body
    }
    if ($existing.status -eq "DRAFT") {
        $existing = Invoke-DemoApi -Session $Session -Method Put -Path "/api/admin/sales/orders/$($existing.id)/confirm" -Body ([ordered]@{
            version = $existing.version
            reason = "验收演示确认销售订单"
            idempotencyKey = "$RunId-SALES-ORDER-$Key-CONFIRM"
        })
    }
    $detail = Get-ItemById -Path "/api/admin/sales/orders" -Id $existing.id
    $Manifest.AddObject("salesOrder", $Key, $detail.id)
    return $detail
}

function Ensure-SalesShipmentPosted {
    param([string] $Key, $Order, $Warehouse, [object[]] $Lines)
    $remark = "验收演示销售发货 $Key"
    $existing = Get-FirstByRemark -Path "/api/admin/sales/shipments" -Remark $remark -Query @{ orderId = $Order.id }
    if ($null -eq $existing) {
        $existing = Invoke-DemoApi -Session $Session -Method Post -Path "/api/admin/sales/orders/$($Order.id)/shipments" -Body ([ordered]@{
            warehouseId = $Warehouse.id
            businessDate = "2026-07-11"
            remark = $remark
            lines = $Lines
        })
    }
    if ($existing.status -eq "DRAFT") {
        $existing = Invoke-DemoApi -Session $Session -Method Put -Path "/api/admin/sales/shipments/$($existing.id)/post" -Body ([ordered]@{
            version = $existing.version
            reason = "验收演示销售出库过账"
            idempotencyKey = "$RunId-SALES-SHIPMENT-$Key-POST"
        })
    }
    $detail = Get-ItemById -Path "/api/admin/sales/shipments" -Id $existing.id
    $Manifest.AddObject("salesShipment", $Key, $detail.id)
    return $detail
}

function Ensure-WorkOrderReleased {
    param(
        [string] $Key,
        $Product,
        $Bom,
        [string] $PlannedQuantity,
        $IssueWarehouse,
        $ReceiptWarehouse,
        [string] $PlannedStartDate = "2026-07-12",
        [string] $PlannedFinishDate = "2026-07-14"
    )
    $remark = "验收演示生产工单 $Key"
    $existing = Get-FirstByRemark -Path "/api/admin/production/work-orders" -Remark $remark
    if ($null -eq $existing) {
        $existing = Invoke-DemoApi -Session $Session -Method Post -Path "/api/admin/production/work-orders" -Body ([ordered]@{
            productMaterialId = $Product.id
            bomId = $Bom.id
            plannedQuantity = $PlannedQuantity
            issueWarehouseId = $IssueWarehouse.id
            receiptWarehouseId = $ReceiptWarehouse.id
            plannedStartDate = $PlannedStartDate
            plannedFinishDate = $PlannedFinishDate
            remark = $remark
        })
    }
    if ($existing.status -eq "DRAFT") {
        $existing = Invoke-DemoApi -Session $Session -Method Put -Path "/api/admin/production/work-orders/$($existing.id)/release"
    }
    $detail = Get-ItemById -Path "/api/admin/production/work-orders" -Id $existing.id
    $Manifest.AddObject("workOrder", $Key, $detail.id)
    return $detail
}

function Ensure-WorkOrderDraft {
    param([string] $Key, $Product, $Bom, [string] $PlannedQuantity, $IssueWarehouse, $ReceiptWarehouse)
    $remark = "验收演示生产工单 $Key"
    $existing = Get-FirstByRemark -Path "/api/admin/production/work-orders" -Remark $remark
    if ($null -eq $existing) {
        $existing = Invoke-DemoApi -Session $Session -Method Post -Path "/api/admin/production/work-orders" -Body ([ordered]@{
            productMaterialId = $Product.id
            bomId = $Bom.id
            plannedQuantity = $PlannedQuantity
            issueWarehouseId = $IssueWarehouse.id
            receiptWarehouseId = $ReceiptWarehouse.id
            plannedStartDate = "2026-07-12"
            plannedFinishDate = "2026-07-14"
            remark = $remark
        })
    }
    $detail = Get-ItemById -Path "/api/admin/production/work-orders" -Id $existing.id
    $Manifest.AddObject("workOrder", $Key, $detail.id)
    return $detail
}

function Ensure-WorkOrderCancelled {
    param([string] $Key, $Product, $Bom, $IssueWarehouse, $ReceiptWarehouse)
    $detail = Ensure-WorkOrderDraft -Key $Key -Product $Product -Bom $Bom -PlannedQuantity "1.000000" `
        -IssueWarehouse $IssueWarehouse -ReceiptWarehouse $ReceiptWarehouse
    if ($detail.status -eq "DRAFT") {
        $detail = Invoke-DemoApi -Session $Session -Method Put -Path "/api/admin/production/work-orders/$($detail.id)/cancel"
    }
    return Get-ItemById -Path "/api/admin/production/work-orders" -Id $detail.id
}

function New-IssueLineForMaterial {
    param($WorkOrder, [string] $MaterialCode, [long] $WarehouseId, [string] $Quantity, [object[]] $TrackingAllocations = @())
    $material = @($WorkOrder.materials) | Where-Object { $_.materialCode -eq $MaterialCode } | Select-Object -First 1
    if ($null -eq $material) {
        throw "工单 $($WorkOrder.workOrderNo) 未找到用料 $MaterialCode。"
    }
    $line = [ordered]@{
        lineNo = [int]$material.lineNo
        workOrderMaterialId = $material.id
        warehouseId = $WarehouseId
        quantity = $Quantity
        remark = "验收演示生产领料"
    }
    if ($TrackingAllocations.Count -gt 0) {
        $line.trackingAllocations = $TrackingAllocations
    }
    return $line
}

function Ensure-ProductionExecutionPosted {
    param(
        [string] $Key,
        $WorkOrder,
        [object[]] $IssueLines,
        [string] $CompletionQuantity,
        [object[]] $CompletionTrackingAllocations = @()
    )
    $issueRemark = "验收演示生产领料 $Key"
    $issue = Get-FirstByRemark -Path "/api/admin/production/work-orders/$($WorkOrder.id)/material-issues" -Remark $issueRemark
    if ($null -eq $issue) {
        $issue = Invoke-DemoApi -Session $Session -Method Post -Path "/api/admin/production/work-orders/$($WorkOrder.id)/material-issues" -Body ([ordered]@{
            businessDate = "2026-07-13"
            reason = "验收演示按 BOM 领料"
            remark = $issueRemark
            lines = $IssueLines
        })
    }
    if ($issue.status -eq "DRAFT") {
        $issue = Invoke-DemoApi -Session $Session -Method Put -Path "/api/admin/production/work-orders/$($WorkOrder.id)/material-issues/$($issue.id)/post"
    }

    $reportRemark = "验收演示生产报工 $Key"
    $report = Get-FirstByRemark -Path "/api/admin/production/work-orders/$($WorkOrder.id)/reports" -Remark $reportRemark
    if ($null -eq $report) {
        $report = Invoke-DemoApi -Session $Session -Method Post -Path "/api/admin/production/work-orders/$($WorkOrder.id)/reports" -Body ([ordered]@{
            businessDate = "2026-07-13"
            qualifiedQuantity = $CompletionQuantity
            defectiveQuantity = "0.000000"
            reporterName = "桥合演示报工员"
            remark = $reportRemark
        })
    }
    if ($report.status -eq "DRAFT") {
        $report = Invoke-DemoApi -Session $Session -Method Put -Path "/api/admin/production/work-orders/$($WorkOrder.id)/reports/$($report.id)/post"
    }

    $receiptRemark = "验收演示完工入库 $Key"
    $receipt = Get-FirstByRemark -Path "/api/admin/production/work-orders/$($WorkOrder.id)/completion-receipts" -Remark $receiptRemark
    if ($null -eq $receipt) {
        $body = [ordered]@{
            businessDate = "2026-07-14"
            receiptWarehouseId = $WorkOrder.receiptWarehouseId
            quantity = $CompletionQuantity
            provisionalUnitCost = "2200.000000"
            remark = $receiptRemark
        }
        if ($CompletionTrackingAllocations.Count -gt 0) {
            $body.trackingAllocations = $CompletionTrackingAllocations
        }
        $receipt = Invoke-DemoApi -Session $Session -Method Post -Path "/api/admin/production/work-orders/$($WorkOrder.id)/completion-receipts" -Body $body
    }
    if ($receipt.status -eq "DRAFT") {
        $receipt = Invoke-DemoApi -Session $Session -Method Put -Path "/api/admin/production/work-orders/$($WorkOrder.id)/completion-receipts/$($receipt.id)/post"
    }

    $latestWorkOrder = Get-ItemById -Path "/api/admin/production/work-orders" -Id $WorkOrder.id
    if ($latestWorkOrder.status -ne "COMPLETED" -and "$($latestWorkOrder.receivedQuantity)" -eq "$($latestWorkOrder.plannedQuantity)") {
        $latestWorkOrder = Invoke-DemoApi -Session $Session -Method Put -Path "/api/admin/production/work-orders/$($latestWorkOrder.id)/complete"
    }
    $Manifest.AddObject("materialIssue", $Key, $issue.id)
    $Manifest.AddObject("workReport", $Key, $report.id)
    $Manifest.AddObject("completionReceipt", $Key, $receipt.id)
    return [pscustomobject]@{
        workOrder = Get-ItemById -Path "/api/admin/production/work-orders" -Id $WorkOrder.id
        issue = Get-ItemById -Path "/api/admin/production/work-orders/$($WorkOrder.id)/material-issues" -Id $issue.id
        report = Get-ItemById -Path "/api/admin/production/work-orders/$($WorkOrder.id)/reports" -Id $report.id
        receipt = Get-ItemById -Path "/api/admin/production/work-orders/$($WorkOrder.id)/completion-receipts" -Id $receipt.id
    }
}

function Ensure-ProductionIssuePosted {
    param(
        [string] $Key,
        $WorkOrder,
        [object[]] $IssueLines,
        [string] $BusinessDate = "2026-07-13"
    )
    $issueRemark = "验收演示生产领料 $Key"
    $issue = Get-FirstByRemark -Path "/api/admin/production/work-orders/$($WorkOrder.id)/material-issues" -Remark $issueRemark
    $body = [ordered]@{
        businessDate = $BusinessDate
        reason = "验收演示补料来源领料"
        remark = $issueRemark
        lines = $IssueLines
    }
    if ($null -eq $issue) {
        $issue = Invoke-DemoApi -Session $Session -Method Post -Path "/api/admin/production/work-orders/$($WorkOrder.id)/material-issues" -Body $body
    }
    elseif ($issue.status -eq "DRAFT") {
        $issue = Invoke-DemoApi -Session $Session -Method Put -Path "/api/admin/production/work-orders/$($WorkOrder.id)/material-issues/$($issue.id)" -Body $body
    }
    if ($issue.status -eq "DRAFT") {
        $issue = Invoke-DemoApi -Session $Session -Method Put -Path "/api/admin/production/work-orders/$($WorkOrder.id)/material-issues/$($issue.id)/post"
    }
    $detail = Get-ItemById -Path "/api/admin/production/work-orders/$($WorkOrder.id)/material-issues" -Id $issue.id
    $Manifest.AddObject("materialIssue", $Key, $detail.id)
    return $detail
}

function Ensure-FinanceSettlementPosted {
    param([object[]] $ReceivableShipments, [object[]] $PayableReceipts)
    $receivables = New-Object System.Collections.Generic.List[object]
    foreach ($shipment in @($ReceivableShipments)) {
        $receivable = Invoke-DemoApiPage -Session $Session -Path "/api/admin/finance/receivables" -Parameters @{
            sourceNo = $shipment.shipmentNo
            page = 1
            pageSize = 20
        } | Select-Object -First 1
        if ($null -eq $receivable) {
            $receivable = Invoke-DemoApi -Session $Session -Method Post -Path "/api/admin/finance/receivables" -Body ([ordered]@{
                sourceType = "SALES_SHIPMENT"
                sourceId = $shipment.id
                dueDate = "2026-07-20"
                remark = "验收演示应收 $($shipment.shipmentNo)"
            })
        }
        if ($receivable.status -eq "DRAFT") {
            $receivable = Invoke-DemoApi -Session $Session -Method Put -Path "/api/admin/finance/receivables/$($receivable.id)/confirm"
        }
        $receivables.Add((Get-ItemById -Path "/api/admin/finance/receivables" -Id $receivable.id))
    }
    if ($receivables.Count -gt 0) {
        $target = $receivables[0]
        $receipt = Get-FirstByRemark -Path "/api/admin/finance/receipts" -Remark "验收演示收款 $($target.receivableNo)" -Query @{ receivableId = $target.id }
        if ($null -eq $receipt) {
            $receipt = Invoke-DemoApi -Session $Session -Method Post -Path "/api/admin/finance/receivables/$($target.id)/receipts" -Body ([ordered]@{
                receiptDate = "2026-07-18"
                amount = "100.00"
                method = "BANK_TRANSFER"
                remark = "验收演示收款 $($target.receivableNo)"
            })
        }
        if ($receipt.status -eq "DRAFT") {
            $receipt = Invoke-DemoApi -Session $Session -Method Put -Path "/api/admin/finance/receipts/$($receipt.id)/post"
        }
        $Manifest.AddObject("receipt", "MAIN", $receipt.id)
    }
    $adjustmentReceivable = $null
    $adjustmentReceipt = $null
    if ($receivables.Count -gt 1) {
        $adjustmentReceivable = $receivables[1]
        $adjustmentReceipt = Get-FirstByRemark -Path "/api/admin/finance/receipts" -Remark "验收演示调整收款 $($adjustmentReceivable.receivableNo)" -Query @{ receivableId = $adjustmentReceivable.id }
        if ($null -eq $adjustmentReceipt) {
            $adjustmentReceipt = Invoke-DemoApi -Session $Session -Method Post -Path "/api/admin/finance/receivables/$($adjustmentReceivable.id)/receipts" -Body ([ordered]@{
                receiptDate = "2026-07-18"
                amount = "80.00"
                method = "BANK_TRANSFER"
                remark = "验收演示调整收款 $($adjustmentReceivable.receivableNo)"
            })
        }
        if ($adjustmentReceipt.status -eq "DRAFT") {
            $adjustmentReceipt = Invoke-DemoApi -Session $Session -Method Put -Path "/api/admin/finance/receipts/$($adjustmentReceipt.id)/post"
        }
        $Manifest.AddObject("receipt", "ADJUSTMENT", $adjustmentReceipt.id)
    }

    $payables = New-Object System.Collections.Generic.List[object]
    foreach ($receiptDoc in @($PayableReceipts)) {
        $payable = Invoke-DemoApiPage -Session $Session -Path "/api/admin/finance/payables" -Parameters @{
            sourceNo = $receiptDoc.receiptNo
            page = 1
            pageSize = 20
        } | Select-Object -First 1
        if ($null -eq $payable) {
            $payable = Invoke-DemoApi -Session $Session -Method Post -Path "/api/admin/finance/payables" -Body ([ordered]@{
                sourceType = "PURCHASE_RECEIPT"
                sourceId = $receiptDoc.id
                dueDate = "2026-07-21"
                remark = "验收演示应付 $($receiptDoc.receiptNo)"
            })
        }
        if ($payable.status -eq "DRAFT") {
            $payable = Invoke-DemoApi -Session $Session -Method Put -Path "/api/admin/finance/payables/$($payable.id)/confirm"
        }
        $payables.Add((Get-ItemById -Path "/api/admin/finance/payables" -Id $payable.id))
    }
    if ($payables.Count -gt 0) {
        $target = $payables[0]
        $payment = Get-FirstByRemark -Path "/api/admin/finance/payments" -Remark "验收演示付款 $($target.payableNo)" -Query @{ payableId = $target.id }
        if ($null -eq $payment) {
            $payment = Invoke-DemoApi -Session $Session -Method Post -Path "/api/admin/finance/payables/$($target.id)/payments" -Body ([ordered]@{
                paymentDate = "2026-07-18"
                amount = "100.00"
                method = "BANK_TRANSFER"
                remark = "验收演示付款 $($target.payableNo)"
            })
        }
        if ($payment.status -eq "DRAFT") {
            $payment = Invoke-DemoApi -Session $Session -Method Put -Path "/api/admin/finance/payments/$($payment.id)/post"
        }
        $Manifest.AddObject("payment", "MAIN", $payment.id)
    }
    foreach ($r in $receivables.ToArray()) { $Manifest.AddObject("receivable", $r.receivableNo, $r.id) }
    foreach ($p in $payables.ToArray()) { $Manifest.AddObject("payable", $p.payableNo, $p.id) }
    return [pscustomobject]@{
        receivables = $receivables.ToArray()
        payables = $payables.ToArray()
        adjustmentReceivable = $adjustmentReceivable
        adjustmentReceipt = $adjustmentReceipt
    }
}

function Get-SalesReturnBySourceShipment {
    param($Shipment)
    return Invoke-DemoApiPage -Session $Session -Path "/api/admin/sales/returns" -Parameters @{
        keyword = $Shipment.shipmentNo
        page = 1
        pageSize = 20
    } |
        Where-Object { $null -ne $_.source -and $_.source.sourceId -eq $Shipment.id } |
        Select-Object -First 1
}

function Ensure-ReversalDocumentsPosted {
    param($SalesShipment, $PurchaseReceipt, $ProductionIssue, $WorkOrder, $ReceivableForAdjustment, $AdjustmentReceipt)
    $salesReturnRemark = "验收演示销售退货"
    $salesReturn = Get-SalesReturnBySourceShipment -Shipment $SalesShipment
    $sourceLine = @($SalesShipment.lines) | Select-Object -First 1
    $returnSource = Invoke-DemoApiPage -Session $Session -Path "/api/admin/sales/return-sources" -Parameters @{
        keyword = $SalesShipment.shipmentNo
        page = 1
        pageSize = 20
    } | Where-Object { $_.shipmentId -eq $SalesShipment.id } | Select-Object -First 1
    if ($null -eq $returnSource) {
        throw "未找到销售发货 $($SalesShipment.shipmentNo) 的退货来源候选。"
    }
    $returnSourceLine = @($returnSource.lines) |
        Where-Object { $_.shipmentLineId -eq $sourceLine.id } |
        Select-Object -First 1
    $returnAllocation = @($returnSourceLine.trackingAllocations) | Select-Object -First 1
    if ($null -eq $returnSourceLine -or $null -eq $returnAllocation -or $null -eq $returnAllocation.sourceAllocationId) {
        throw "销售退货缺少可继承追踪来源。Shipment=$($SalesShipment.shipmentNo)"
    }
    $salesReturnLines = @([ordered]@{
        sourceShipmentLineId = $sourceLine.id
        quantity = "0.500000"
        reason = "客户退回演示"
        trackingAllocations = @([ordered]@{
            sourceAllocationId = $returnAllocation.sourceAllocationId
            batchId = $returnAllocation.batchId
            serialId = $returnAllocation.serialId
            quantity = "0.500000"
        })
    })
    if ($null -eq $salesReturn) {
        $salesReturn = Invoke-DemoApi -Session $Session -Method Post -Path "/api/admin/sales/returns" -Body ([ordered]@{
            sourceShipmentId = $SalesShipment.id
            businessDate = "2026-07-19"
            clientRequestId = "$RunId-SALES-RETURN"
            remark = $salesReturnRemark
            lines = $salesReturnLines
        })
    }
    elseif ($salesReturn.status -eq "DRAFT") {
        $salesReturn = Invoke-DemoApi -Session $Session -Method Put -Path "/api/admin/sales/returns/$($salesReturn.id)" -Body ([ordered]@{
            sourceShipmentId = $SalesShipment.id
            businessDate = "2026-07-19"
            clientRequestId = "$RunId-SALES-RETURN"
            remark = $salesReturnRemark
            lines = $salesReturnLines
        })
    }
    if ($salesReturn.status -eq "DRAFT") {
        $salesReturn = Invoke-DemoApi -Session $Session -Method Put -Path "/api/admin/sales/returns/$($salesReturn.id)/post" -Body ([ordered]@{
            version = $salesReturn.version
            reason = "验收演示销售退货过账"
            idempotencyKey = "$RunId-SALES-RETURN-POST"
        })
    }

    $purchaseReturnRemark = "验收演示采购退货"
    $purchaseReturn = Get-FirstByRemark -Path "/api/admin/procurement/returns" -Remark $purchaseReturnRemark
    if ($null -eq $purchaseReturn) {
        $sourceLine = @($PurchaseReceipt.lines) | Where-Object { $_.materialCode -like "*RAW-CABLE" } | Select-Object -First 1
        $allocation = @($sourceLine.trackingAllocations) | Select-Object -First 1
        $purchaseReturn = Invoke-DemoApi -Session $Session -Method Post -Path "/api/admin/procurement/returns" -Body ([ordered]@{
            sourceReceiptId = $PurchaseReceipt.id
            businessDate = "2026-07-19"
            clientRequestId = "$RunId-PURCHASE-RETURN"
            remark = $purchaseReturnRemark
            lines = @([ordered]@{
                sourceReceiptLineId = $sourceLine.id
                quantity = "2.000000"
                reason = "供应商退货演示"
                qualityStatus = "QUALIFIED"
                trackingAllocations = @([ordered]@{ batchId = $allocation.batchId; serialId = $allocation.serialId; quantity = "2.000000" })
            })
        })
    }
    if ($purchaseReturn.status -eq "DRAFT") {
        $purchaseReturn = Invoke-DemoApi -Session $Session -Method Put -Path "/api/admin/procurement/returns/$($purchaseReturn.id)/post" -Body ([ordered]@{
            version = $purchaseReturn.version
            reason = "验收演示采购退货过账"
            idempotencyKey = "$RunId-PURCHASE-RETURN-POST"
        })
    }

    $materialReturnRemark = "验收演示生产退料"
    $materialReturn = Get-FirstByRemark -Path "/api/admin/production/material-returns" -Remark $materialReturnRemark
    if ($null -eq $materialReturn) {
        $issueLine = @($ProductionIssue.lines) | Select-Object -First 1
        $materialReturnSource = Invoke-DemoApiPage -Session $Session -Path "/api/admin/production/material-return-sources" -Parameters @{
            keyword = $ProductionIssue.issueNo
            page = 1
            pageSize = 20
        } | Where-Object { $_.issueId -eq $ProductionIssue.id } | Select-Object -First 1
        if ($null -eq $materialReturnSource) {
            throw "未找到生产领料 $($ProductionIssue.issueNo) 的退料来源候选。"
        }
        $materialReturnSourceLine = @($materialReturnSource.lines) |
            Where-Object { $_.issueLineId -eq $issueLine.id } |
            Select-Object -First 1
        $materialReturnAllocation = @($materialReturnSourceLine.trackingAllocations) | Select-Object -First 1
        if ($null -eq $materialReturnSourceLine -or $null -eq $materialReturnAllocation -or $null -eq $materialReturnAllocation.sourceAllocationId) {
            throw "生产退料缺少可继承追踪来源。Issue=$($ProductionIssue.issueNo)"
        }
        $materialReturn = Invoke-DemoApi -Session $Session -Method Post -Path "/api/admin/production/material-returns" -Body ([ordered]@{
            sourceIssueId = $ProductionIssue.id
            businessDate = "2026-07-19"
            clientRequestId = "$RunId-MATERIAL-RETURN"
            remark = $materialReturnRemark
            lines = @([ordered]@{
                sourceIssueLineId = $issueLine.id
                quantity = "1.000000"
                reason = "生产退料演示"
                trackingAllocations = @([ordered]@{
                    sourceAllocationId = $materialReturnAllocation.sourceAllocationId
                    batchId = $materialReturnAllocation.batchId
                    serialId = $materialReturnAllocation.serialId
                    quantity = "1.000000"
                })
            })
        })
    }
    if ($materialReturn.status -eq "DRAFT") {
        $materialReturn = Invoke-DemoApi -Session $Session -Method Put -Path "/api/admin/production/material-returns/$($materialReturn.id)/post"
    }

    $supplementRemark = "验收演示生产补料"
    $supplement = Get-FirstByRemark -Path "/api/admin/production/material-supplements" -Remark $supplementRemark
    if ($null -eq $supplement) {
        $material = @($WorkOrder.materials) | Where-Object { $_.materialCode -like "*RAW-CABLE" } | Select-Object -First 1
        $batch = Get-InventoryBatch -BatchNo "$DemoPrefix-BATCH-CABLE-01"
        $supplement = Invoke-DemoApi -Session $Session -Method Post -Path "/api/admin/production/material-supplements" -Body ([ordered]@{
            workOrderId = $WorkOrder.id
            warehouseId = $WorkOrder.issueWarehouseId
            businessDate = "2026-07-19"
            clientRequestId = "$RunId-MATERIAL-SUPPLEMENT"
            remark = $supplementRemark
            lines = @([ordered]@{
                workOrderMaterialId = $material.id
                quantity = "1.000000"
                reason = "生产补料演示"
                trackingAllocations = @([ordered]@{ batchId = $batch.id; quantity = "1.000000" })
            })
        })
    }
    if ($supplement.status -eq "DRAFT") {
        $supplement = Invoke-DemoApi -Session $Session -Method Put -Path "/api/admin/production/material-supplements/$($supplement.id)/post"
    }

    if ($null -ne $ReceivableForAdjustment) {
        if ($null -eq $AdjustmentReceipt) {
            throw "往来调整缺少独立收款来源。"
        }
        $adjustmentRemark = "验收演示往来调整"
        $adjustment = Get-FirstByRemark -Path "/api/admin/finance/settlement-adjustments" -Remark $adjustmentRemark
        $adjustmentBody = [ordered]@{
            settlementSide = "RECEIVABLE"
            adjustmentType = "REFUND"
            sourceType = "RECEIPT"
            sourceId = $AdjustmentReceipt.id
            targetId = $ReceivableForAdjustment.id
            businessDate = "2026-07-20"
            amount = "10.00"
            clientRequestId = "$RunId-SETTLEMENT-ADJ"
            remark = $adjustmentRemark
        }
        if ($null -eq $adjustment) {
            $adjustment = Invoke-DemoApi -Session $Session -Method Post -Path "/api/admin/finance/settlement-adjustments" -Body $adjustmentBody
        }
        elseif ($adjustment.status -eq "DRAFT") {
            $adjustment = Invoke-DemoApi -Session $Session -Method Put -Path "/api/admin/finance/settlement-adjustments/$($adjustment.id)" -Body $adjustmentBody
        }
        if ($adjustment.status -eq "DRAFT") {
            $adjustment = Invoke-DemoApi -Session $Session -Method Put -Path "/api/admin/finance/settlement-adjustments/$($adjustment.id)/post"
        }
        $Manifest.AddObject("settlementAdjustment", "MAIN", $adjustment.id)
    }

    $Manifest.AddObject("salesReturn", "MAIN", $salesReturn.id)
    $Manifest.AddObject("purchaseReturn", "MAIN", $purchaseReturn.id)
    $Manifest.AddObject("materialReturn", "MAIN", $materialReturn.id)
    $Manifest.AddObject("materialSupplement", "MAIN", $supplement.id)
    return [pscustomobject]@{
        salesReturn = $salesReturn
        purchaseReturn = $purchaseReturn
        materialReturn = $materialReturn
        materialSupplement = $supplement
    }
}

function Ensure-ValuationAdjustmentPosted {
    param($Material, $ProjectLayer = $null)
    $reason = "验收演示暂估调整"
    $existing = Invoke-DemoApiPage -Session $Session -Path "/api/admin/inventory/valuation-adjustments" -Parameters @{
        keyword = $reason
        page = 1
        pageSize = 20
    } | Where-Object { $_.reason -eq $reason } | Select-Object -First 1
    if ($null -eq $existing) {
        $lines = @([ordered]@{
            lineNo = 1
            ownershipType = "PUBLIC"
            materialId = $Material.id
            adjustmentAmount = "25.00"
        })
        if ($null -ne $ProjectLayer) {
            $lines += [ordered]@{
                lineNo = 2
                ownershipType = "PROJECT"
                projectId = $ProjectLayer.projectId
                materialId = $ProjectLayer.materialId
                costLayerId = $ProjectLayer.id
                adjustmentAmount = "15.00"
            }
        }
        $existing = Invoke-DemoApi -Session $Session -Method Post -Path "/api/admin/inventory/valuation-adjustments" -Body ([ordered]@{
            adjustmentType = "PROVISIONAL_REVALUATION"
            businessDate = "2026-07-21"
            reason = $reason
            idempotencyKey = "$RunId-VALUATION-ADJ"
            lines = $lines
        })
    }
    $posted = Submit-And-ApproveInventoryDocument -Document $existing -Path "/api/admin/inventory/valuation-adjustments" `
        -Key "VALUATION-ADJ" -Reason $reason
    $Manifest.AddObject("valuationAdjustment", "MAIN", $posted.id)
    return $posted
}

function Get-StocktakeLinesPage {
    param($Stocktake, [int] $Page = 1, [int] $PageSize = 20)
    return Invoke-DemoApi -Session $Session -Method Get -Path "/api/admin/inventory/stocktakes/$($Stocktake.id)/lines?page=$Page&pageSize=$PageSize"
}

function Get-AllStocktakeLines {
    param($Stocktake, [int] $PageSize = 20)
    $lines = New-Object System.Collections.Generic.List[object]
    $page = 1
    $data = $null
    do {
        $data = Get-StocktakeLinesPage -Stocktake $Stocktake -Page $page -PageSize $PageSize
        foreach ($line in @($data.items)) {
            $lines.Add($line)
        }
        $page++
    } while ($page -le $data.totalPages)
    return $lines.ToArray()
}

function Update-StocktakeLineBatch {
    param($Stocktake, [object[]] $Lines)
    if ($Lines.Count -eq 0) {
        return Get-ItemById -Path "/api/admin/inventory/stocktakes" -Id $Stocktake.id
    }
    return Invoke-DemoApi -Session $Session -Method Put -Path "/api/admin/inventory/stocktakes/$($Stocktake.id)/lines" -Body ([ordered]@{
        version = $Stocktake.version
        lines = $Lines
    })
}

function Ensure-StocktakeDocuments {
    param($ProjectLayer)
    $zeroReason = "验收演示零差异盘点"
    $zero = Invoke-DemoApiPage -Session $Session -Path "/api/admin/inventory/stocktakes" -Parameters @{ keyword = $zeroReason; page = 1; pageSize = 20 } |
        Where-Object { $_.reason -eq $zeroReason } | Select-Object -First 1
    if ($null -eq $zero) {
        $zero = Invoke-DemoApi -Session $Session -Method Post -Path "/api/admin/inventory/stocktakes" -Body ([ordered]@{
            businessDate = "2026-07-22"
            scopeType = "WAREHOUSE"
            warehouseId = $ProjectLayer.warehouseId
            reason = $zeroReason
            idempotencyKey = "$RunId-STK-ZERO"
        })
    }
    if ($zero.status -eq "DRAFT") {
        $zero = Invoke-DemoApi -Session $Session -Method Put -Path "/api/admin/inventory/stocktakes/$($zero.id)/start" -Body ([ordered]@{
            version = $zero.version
            reason = "启动零差异盘点"
            idempotencyKey = "$RunId-STK-ZERO-START"
        })
    }
    if ($zero.status -eq "COUNTING") {
        $allZeroLines = Get-AllStocktakeLines -Stocktake $zero -PageSize 30
        $updates = @($allZeroLines | ForEach-Object {
            [ordered]@{ id = $_.id; version = $_.version; countedQuantity = $_.bookQuantity }
        })
        $zero = Update-StocktakeLineBatch -Stocktake $zero -Lines $updates
        $zero = Invoke-DemoApi -Session $Session -Method Put -Path "/api/admin/inventory/stocktakes/$($zero.id)/reconcile" -Body ([ordered]@{
            version = $zero.version
            reason = "零差异复核"
            idempotencyKey = "$RunId-STK-ZERO-RECONCILE"
        })
    }
    if ($zero.status -eq "RECONCILED") {
        $zero = Invoke-DemoApi -Session $Session -Method Put -Path "/api/admin/inventory/stocktakes/$($zero.id)/complete-zero-variance" -Body ([ordered]@{
            version = $zero.version
            reason = "零差异完成"
            idempotencyKey = "$RunId-STK-ZERO-COMPLETE"
        })
    }

    $varianceReason = "验收演示差异盘点"
    $variance = Invoke-DemoApiPage -Session $Session -Path "/api/admin/inventory/stocktakes" -Parameters @{ keyword = $varianceReason; page = 1; pageSize = 20 } |
        Where-Object { $_.reason -eq $varianceReason } | Select-Object -First 1
    if ($null -eq $variance) {
        $variance = Invoke-DemoApi -Session $Session -Method Post -Path "/api/admin/inventory/stocktakes" -Body ([ordered]@{
            businessDate = "2026-07-23"
            scopeType = "WAREHOUSE"
            warehouseId = $ProjectLayer.warehouseId
            reason = $varianceReason
            idempotencyKey = "$RunId-STK-VAR"
        })
    }
    Upload-Attachment -ObjectType "INVENTORY_STOCKTAKE" -ObjectId $variance.id `
        -AssetName "bridge-electrical-stocktake-evidence.txt" -Description "项目盘盈证据附件" | Out-Null
    if ($variance.status -eq "DRAFT") {
        $variance = Invoke-DemoApi -Session $Session -Method Put -Path "/api/admin/inventory/stocktakes/$($variance.id)/start" -Body ([ordered]@{
            version = $variance.version
            reason = "启动差异盘点"
            idempotencyKey = "$RunId-STK-VAR-START"
        })
    }
    if ($variance.status -eq "COUNTING") {
        $allLines = Get-AllStocktakeLines -Stocktake $variance -PageSize 20
        $updates = New-Object System.Collections.Generic.List[object]
        $positiveDone = $false
        $negativeDone = $false
        $zeroDone = $false
        foreach ($line in @($allLines)) {
            if ($line.ownershipType -eq "PROJECT" -and -not $positiveDone) {
                $updates.Add([ordered]@{
                    id = $line.id
                    version = $line.version
                    countedQuantity = ([decimal]$line.bookQuantity + [decimal]"1.000000").ToString("0.000000", [Globalization.CultureInfo]::InvariantCulture)
                    varianceUnitCost = "88.000000"
                    varianceReason = "项目现场发现备用铜排"
                })
                $positiveDone = $true
                continue
            }
            if (-not $negativeDone -and [decimal]$line.bookQuantity -gt [decimal]"1.000000") {
                $updates.Add([ordered]@{
                    id = $line.id
                    version = $line.version
                    countedQuantity = ([decimal]$line.bookQuantity - [decimal]"1.000000").ToString("0.000000", [Globalization.CultureInfo]::InvariantCulture)
                    varianceReason = "盘亏演示"
                })
                $negativeDone = $true
                continue
            }
            if (-not $zeroDone) {
                $updates.Add([ordered]@{ id = $line.id; version = $line.version; countedQuantity = "0.000000" })
                $zeroDone = $true
                continue
            }
            if ($updates.Count -lt 25) {
                $updates.Add([ordered]@{ id = $line.id; version = $line.version; countedQuantity = $line.bookQuantity })
            }
        }
        if (-not $positiveDone -or -not $negativeDone -or $updates.Count -lt 2) {
            throw "差异盘点缺少可用项目盘盈或盘亏行。"
        }
        $variance = Update-StocktakeLineBatch -Stocktake $variance -Lines $updates.ToArray()
        $variance = Invoke-DemoApi -Session $Session -Method Put -Path "/api/admin/inventory/stocktakes/$($variance.id)/reconcile" -Body ([ordered]@{
            version = $variance.version
            reason = "差异复核"
            idempotencyKey = "$RunId-STK-VAR-RECONCILE"
        })
    }
    if ($variance.status -eq "RECONCILED") {
        $variance = Submit-And-ApproveInventoryDocument -Document $variance -Path "/api/admin/inventory/stocktakes" `
            -Key "STK-VAR" -Reason "审批盘点差异"
    }

    $draftReason = "验收演示未盘草稿盘点"
    $draft = Invoke-DemoApiPage -Session $Session -Path "/api/admin/inventory/stocktakes" -Parameters @{ keyword = $draftReason; page = 1; pageSize = 20 } |
        Where-Object { $_.reason -eq $draftReason } | Select-Object -First 1
    if ($null -eq $draft) {
        $draft = Invoke-DemoApi -Session $Session -Method Post -Path "/api/admin/inventory/stocktakes" -Body ([ordered]@{
            businessDate = "2026-07-24"
            scopeType = "WAREHOUSE"
            warehouseId = $ProjectLayer.warehouseId
            reason = $draftReason
            idempotencyKey = "$RunId-STK-DRAFT"
        })
    }
    if ($draft.status -eq "DRAFT") {
        $draft = Invoke-DemoApi -Session $Session -Method Put -Path "/api/admin/inventory/stocktakes/$($draft.id)/start" -Body ([ordered]@{
            version = $draft.version
            reason = "启动未盘快照样例"
            idempotencyKey = "$RunId-STK-DRAFT-START"
        })
    }
    $Manifest.AddObject("stocktake", "ZERO", $zero.id)
    $Manifest.AddObject("stocktake", "VARIANCE", $variance.id)
    $Manifest.AddObject("stocktake", "DRAFT", $draft.id)
    return [pscustomobject]@{ zero = $zero; variance = $variance; draft = $draft }
}

function Wait-DocumentTaskStatus {
    param($Task, [string[]] $Statuses, [int] $Attempts = 20)
    $current = $Task
    for ($i = 0; $i -lt $Attempts; $i++) {
        $current = Invoke-DemoApi -Session $Session -Method Get -Path "/api/admin/document-tasks/$($Task.id)"
        if ($Statuses -contains $current.status) {
            return $current
        }
        Start-Sleep -Milliseconds 2500
    }
    throw "文档任务 $($Task.id) 未在预期时间进入状态：$($Statuses -join ',')，当前 $($current.status)。"
}

function Find-DemoDocumentTask {
    param(
        [string] $TaskType,
        [string[]] $Statuses,
        [string] $ObjectType = $null,
        [Nullable[long]] $ObjectId = $null
    )
    $tasks = Invoke-DemoApiPage -Session $Session -Path "/api/admin/document-tasks" -Parameters @{}
    foreach ($task in @($tasks)) {
        if ($task.taskType -ne $TaskType) {
            continue
        }
        if ($Statuses -notcontains $task.status) {
            continue
        }
        if ($ObjectType -and $task.objectType -ne $ObjectType) {
            continue
        }
        if ($ObjectId.HasValue -and [long]$task.objectId -ne $ObjectId.Value) {
            continue
        }
        return $task
    }
    return $null
}

function New-MaterialImportFile {
    param([string] $Path, [bool] $Valid)
    $rows = New-Object System.Collections.Generic.List[object]
    $rows.Add(@("code", "name", "specification", "materialType", "sourceType", "trackingMethod", "categoryCode", "unitCode", "status", "costCategory", "inventoryValuationCategory", "inventoryValueEnabled", "projectCostEnabled", "costRemark", "remark"))
    if ($Valid) {
        $rows.Add(@("$DemoPrefix-MAT-IMP-01", "导入演示端子排", "TB-16", "RAW_MATERIAL", "PURCHASED", "NONE", "$DemoPrefix-CAT-ELEC", "$DemoPrefix-EA", "ENABLED", "STANDARD", "MOVING_AVERAGE", "true", "false", "导入成本属性", "有效导入停在待确认"))
    }
    else {
        $rows.Add(@("$DemoPrefix-MAT-IMP-ERR", "", "ERR", "RAW_MATERIAL", "PURCHASED", "NONE", "MISSING-CATEGORY", "MISSING-UNIT", "ENABLED", "STANDARD", "MOVING_AVERAGE", "true", "false", "无效导入", "故意校验失败"))
    }
    New-DemoXlsx -Path $Path -Sheets @{ materials = $rows.ToArray() }
    return $Path
}

function New-BomDraftImportFile {
    param([string] $Path, $ParentMaterial, $ChildMaterial)
    $header = @("mode", "bomId", "version", "bomCode", "parentMaterialCode", "versionCode", "name", "baseQuantity", "baseUnit", "effectiveFrom", "effectiveTo", "remark")
    $itemHeader = @("lineNo", "childMaterialCode", "businessUnit", "businessQuantity", "lossRate", "warehouse", "remark")
    $rows1 = @(
        $header,
        @("CREATE", "", "", "$DemoPrefix-BOM-IMPORT-DRAFT", $ParentMaterial.code, "IMP1", "导入演示 BOM 草稿", "1.000000", "$DemoPrefix-SET", "2026-08-01", "", "BOM 草稿导入演示")
    )
    $rows2 = @(
        $itemHeader,
        @("1", $ChildMaterial.code, "$DemoPrefix-EA", "1.000000", "0.000000", "", "导入子项")
    )
    New-DemoXlsx -Path $Path -Sheets @{ bom = $rows1; items = $rows2 }
    return $Path
}

function Ensure-DocumentTaskSamples {
    param($ContractApproval, $BomEcoApproval, $BomDraft, $BomImportParent, $BomImportChild)
    $taskDir = New-DemoDirectory -Path (Join-Path $Root "apps/api/target/demo-data/document-tasks")
    if ($DocumentWorkerMode -eq "WorkerDisabled") {
        $cancelTask = Invoke-DemoApi -Session $Session -Method Post -Path "/api/admin/exports/materials" -Body ([ordered]@{
            keyword = $DemoPrefix
            status = "ENABLED"
            trackingMethod = "NONE"
        }) -Headers @{ "Idempotency-Key" = "$RunId-DOC-CANCEL-EXPORT" } -AllowFailure
        if ($cancelTask.failed -ne $true) {
            $latest = Invoke-DemoApi -Session $Session -Method Get -Path "/api/admin/document-tasks/$($cancelTask.id)"
            if ($latest.status -eq "QUEUED" -or $latest.status -eq "RUNNING") {
                $cancelTask = Invoke-DemoApi -Session $Session -Method Post -Path "/api/admin/document-tasks/$($latest.id)/cancel" -Body ([ordered]@{
                    version = $latest.version
                    reason = "验收演示保留取消任务状态"
                })
            }
            $Manifest.AddTask("CANCELLED-EXPORT", $cancelTask.id)
        }
        $Manifest.AddNote("DocumentWorkerMode=WorkerDisabled：已创建可取消文档任务窗口；重启 worker enabled 后继续生成导入、导出和打印任务。")
        return
    }

    $validImportFile = New-MaterialImportFile -Path (Join-Path $taskDir "materials-valid.xlsx") -Valid $true
    $invalidImportFile = New-MaterialImportFile -Path (Join-Path $taskDir "materials-invalid.xlsx") -Valid $false
    $bomImportFile = New-BomDraftImportFile -Path (Join-Path $taskDir "bom-draft.xlsx") -ParentMaterial $BomImportParent -ChildMaterial $BomImportChild
    $validImportSha = Get-FileSha256 -Path $validImportFile
    $invalidImportSha = Get-FileSha256 -Path $invalidImportFile
    $bomImportSha = Get-FileSha256 -Path $bomImportFile

    $validImport = Invoke-DemoMultipart -Path "/api/admin/imports/materials" -FileFieldName "file" -FilePath $validImportFile -Fields @{} -Headers @{ "Idempotency-Key" = "$RunId-DOC-IMPORT-MATERIAL-VALID" }
    $validImport = Wait-DocumentTaskStatus -Task $validImport -Statuses @("READY_TO_COMMIT", "SUCCEEDED") -Attempts 20
    $Manifest.AddTask("MATERIAL-IMPORT-READY", $validImport.id)
    $Manifest.AddFile([ordered]@{
        category = "importSource"
        taskType = "MATERIAL_IMPORT"
        taskId = $validImport.id
        fileName = "materials-valid.xlsx"
        sha256 = $validImportSha
        idempotencyKey = "$RunId-DOC-IMPORT-MATERIAL-VALID"
    })

    $invalidImport = Invoke-DemoMultipart -Path "/api/admin/imports/materials" -FileFieldName "file" -FilePath $invalidImportFile -Fields @{} -Headers @{ "Idempotency-Key" = "$RunId-DOC-IMPORT-MATERIAL-INVALID" }
    $invalidImport = Wait-DocumentTaskStatus -Task $invalidImport -Statuses @("VALIDATION_FAILED") -Attempts 20
    $Manifest.AddTask("MATERIAL-IMPORT-FAILED", $invalidImport.id)
    $Manifest.AddFile([ordered]@{
        category = "importSource"
        taskType = "MATERIAL_IMPORT"
        taskId = $invalidImport.id
        fileName = "materials-invalid.xlsx"
        sha256 = $invalidImportSha
        idempotencyKey = "$RunId-DOC-IMPORT-MATERIAL-INVALID"
    })

    $bomImport = Invoke-DemoMultipart -Path "/api/admin/imports/bom-drafts" -FileFieldName "file" -FilePath $bomImportFile -Fields @{} -Headers @{ "Idempotency-Key" = "$RunId-DOC-IMPORT-BOM" }
    $bomImport = Wait-DocumentTaskStatus -Task $bomImport -Statuses @("READY_TO_COMMIT", "SUCCEEDED") -Attempts 20
    if ($bomImport.status -eq "READY_TO_COMMIT") {
        $bomImport = Invoke-DemoApi -Session $Session -Method Post -Path "/api/admin/imports/$($bomImport.id)/confirm" -Body ([ordered]@{
            version = $bomImport.version
        }) -Headers @{ "Idempotency-Key" = "$RunId-DOC-IMPORT-BOM-CONFIRM" }
        $bomImport = Wait-DocumentTaskStatus -Task $bomImport -Statuses @("SUCCEEDED") -Attempts 20
    }
    $Manifest.AddTask("BOM-IMPORT-SUCCEEDED", $bomImport.id)
    $Manifest.AddFile([ordered]@{
        category = "importSource"
        taskType = "BOM_DRAFT_IMPORT"
        taskId = $bomImport.id
        fileName = "bom-draft.xlsx"
        sha256 = $bomImportSha
        idempotencyKey = "$RunId-DOC-IMPORT-BOM"
    })

    $materialExport = Invoke-DemoApi -Session $Session -Method Post -Path "/api/admin/exports/materials" -Body ([ordered]@{
        keyword = $DemoPrefix
        status = "ENABLED"
        trackingMethod = "NONE"
    }) -Headers @{ "Idempotency-Key" = "$RunId-DOC-EXPORT-MATERIALS" }
    $materialExport = Wait-DocumentTaskStatus -Task $materialExport -Statuses @("SUCCEEDED") -Attempts 12
    $Manifest.AddTask("MATERIAL-EXPORT", $materialExport.id)
    $Manifest.AddFile([ordered]@{
        category = "documentTaskResult"
        taskType = "MATERIAL_EXPORT"
        taskId = $materialExport.id
        downloadableId = $materialExport.id
        downloadPath = "/api/admin/document-tasks/$($materialExport.id)/download"
        idempotencyKey = "$RunId-DOC-EXPORT-MATERIALS"
    })

    $bomExport = Invoke-DemoApi -Session $Session -Method Post -Path "/api/admin/exports/bom-drafts/$($BomDraft.id)" `
        -Headers @{ "Idempotency-Key" = "$RunId-DOC-EXPORT-BOM-DRAFT" }
    $bomExport = Wait-DocumentTaskStatus -Task $bomExport -Statuses @("SUCCEEDED") -Attempts 12
    $Manifest.AddTask("BOM-EXPORT", $bomExport.id)
    $Manifest.AddFile([ordered]@{
        category = "documentTaskResult"
        taskType = "BOM_DRAFT_EXPORT"
        taskId = $bomExport.id
        downloadableId = $bomExport.id
        downloadPath = "/api/admin/document-tasks/$($bomExport.id)/download"
        idempotencyKey = "$RunId-DOC-EXPORT-BOM-DRAFT"
    })

    foreach ($approval in @($ContractApproval, $BomEcoApproval)) {
        if ($null -eq $approval) { continue }
        $templateCode = switch ($approval.sceneCode) {
            "SALES_PROJECT_CONTRACT_ACTIVATION" { "CONTRACT_ACTIVATION_APPROVAL_V1" }
            "BOM_ECO_APPLICATION" { "BOM_ECO_APPLICATION_APPROVAL_V1" }
            default { throw "不支持的打印审批场景：$($approval.sceneCode)" }
        }
        $task = Invoke-DemoApi -Session $Session -Method Post -Path "/api/admin/print-tasks" -Body ([ordered]@{
            approvalInstanceId = $approval.id
            templateCode = $templateCode
        }) -Headers @{ "Idempotency-Key" = "$RunId-DOC-PRINT-$($approval.sceneCode)" }
        $task = Wait-DocumentTaskStatus -Task $task -Statuses @("SUCCEEDED") -Attempts 20
        $Manifest.AddTask("PRINT-$($approval.sceneCode)", $task.id)
        $Manifest.AddFile([ordered]@{
            category = "documentTaskResult"
            taskType = "APPROVAL_PRINT"
            sceneCode = $approval.sceneCode
            taskId = $task.id
            downloadableId = $task.id
            downloadPath = "/api/admin/document-tasks/$($task.id)/download"
            idempotencyKey = "$RunId-DOC-PRINT-$($approval.sceneCode)"
        })
    }
    $Manifest.AddNote("DocumentWorkerMode=WorkerEnabled：已通过真实 worker 处理导入、导出和打印任务。")
}

function Ensure-SalesProject {
    param(
        [string] $Key,
        $Customer,
        [long] $OwnerUserId,
        [string] $Name,
        [string] $Revenue,
        [string] $Cost
    )
    $existing = Get-ItemByField -Path "/api/admin/sales-projects" -FieldName "name" -FieldValue $Name
    if ($null -eq $existing) {
        $existing = Invoke-DemoApi -Session $Session -Method Post -Path "/api/admin/sales-projects" -Body ([ordered]@{
            name = $Name
            customerId = $Customer.id
            ownerUserId = $OwnerUserId
            plannedStartDate = "2026-07-01"
            plannedFinishDate = "2026-10-31"
            targetRevenue = $Revenue
            targetCost = $Cost
            remark = "验收演示销售项目 $Key"
        })
    }
    $Manifest.AddObject("salesProject", $Key, $existing.id)
    return Get-ItemById -Path "/api/admin/sales-projects" -Id $existing.id
}

function Ensure-SalesContract {
    param(
        [string] $Key,
        $Project,
        [string] $ContractType,
        [Nullable[long]] $MainContractId,
        [string] $Name,
        [string] $Amount,
        [string] $ExternalNo
    )
    $existing = Invoke-DemoApiPage -Session $Session -Path "/api/admin/sales-projects/$($Project.id)/contracts" -Parameters @{
        keyword = $ExternalNo
        page = 1
        pageSize = 100
    } | Where-Object { $_.externalContractNo -eq $ExternalNo -or $_.name -eq $Name } | Select-Object -First 1
    if ($null -eq $existing) {
        $body = [ordered]@{
            contractType = $ContractType
            externalContractNo = $ExternalNo
            name = $Name
            signedDate = "2026-07-03"
            effectiveStartDate = "2026-07-05"
            effectiveEndDate = "2026-12-31"
            amount = $Amount
            remark = "验收演示销售合同 $Key"
        }
        if ($null -ne $MainContractId) {
            $body.mainContractId = $MainContractId
        }
        $existing = Invoke-DemoApi -Session $Session -Method Post -Path "/api/admin/sales-projects/$($Project.id)/contracts" -Body $body
    }
    $Manifest.AddObject("salesContract", $Key, $existing.id)
    return Get-ItemById -Path "/api/admin/sales-project-contracts" -Id $existing.id
}

Write-Step "创建权限角色与演示账号。"
$permissionMap = Get-PermissionMap
$allPermissionCodes = @($permissionMap.Keys)
$adminLikeRole = Ensure-Role -Code "$DemoPrefix-ROLE-ADMIN" -Name "桥合演示管理员" -PermissionCodes $allPermissionCodes -PermissionMap $permissionMap
$warehouseRole = Ensure-Role -Code "$DemoPrefix-ROLE-WAREHOUSE" -Name "桥合仓储角色" -PermissionCodes ($allPermissionCodes | Where-Object { $_ -like "inventory:*" -or $_ -like "master:*" -or $_ -like "platform:*" -or $_ -like "system:business-period:*" }) -PermissionMap $permissionMap
$productionRole = Ensure-Role -Code "$DemoPrefix-ROLE-PRODUCTION" -Name "桥合生产角色" -PermissionCodes ($allPermissionCodes | Where-Object { $_ -like "production:*" -or $_ -like "material:*" -or $_ -like "inventory:*" -or $_ -like "platform:*" }) -PermissionMap $permissionMap
$financeRole = Ensure-Role -Code "$DemoPrefix-ROLE-FINANCE" -Name "桥合财务角色" -PermissionCodes ($allPermissionCodes | Where-Object { $_ -like "finance:*" -or $_ -like "sales:*" -or $_ -like "procurement:*" -or $_ -like "platform:*" }) -PermissionMap $permissionMap
$approvalRole = Ensure-Role -Code "$DemoPrefix-ROLE-APPROVAL" -Name "桥合审批角色" -PermissionCodes ($allPermissionCodes | Where-Object { $_ -like "platform:*" -or $_ -like "sales:contract:*" -or $_ -like "material:bom-eco:*" -or $_ -like "inventory:*" -or $_ -eq "procurement:order:view" -or $_ -eq "procurement:order:exception-approve" }) -PermissionMap $permissionMap
$readonlyRole = Ensure-Role -Code "$DemoPrefix-ROLE-READONLY" -Name "桥合只读角色" -PermissionCodes ($allPermissionCodes | Where-Object { $_ -like "*:view" -or $_ -like "*:export" }) -PermissionMap $permissionMap

$demoAdmin = Ensure-User -Username "$DemoPrefix-admin" -DisplayName "桥合演示管理员" -RoleIds @($adminLikeRole.id)
$demoWarehouse = Ensure-User -Username "$DemoPrefix-warehouse" -DisplayName "桥合仓储主管" -RoleIds @($warehouseRole.id)
$demoProduction = Ensure-User -Username "$DemoPrefix-production" -DisplayName "桥合生产主管" -RoleIds @($productionRole.id)
$demoFinance = Ensure-User -Username "$DemoPrefix-finance" -DisplayName "桥合财务主管" -RoleIds @($financeRole.id)
$demoApproval = Ensure-User -Username "$DemoPrefix-approval" -DisplayName "桥合审批经理" -RoleIds @($approvalRole.id)
$demoReadonly = Ensure-User -Username "$DemoPrefix-readonly" -DisplayName "桥合只读用户" -RoleIds @($readonlyRole.id)
$demoNoCost = Ensure-User -Username "$DemoPrefix-no-cost" -DisplayName "桥合无成本权限用户" -RoleIds @($readonlyRole.id)
$demoPlanner = Ensure-User -Username "$DemoPrefix-planner" -DisplayName "桥合计划员" -RoleIds @($productionRole.id)
$demoBuyer = Ensure-User -Username "$DemoPrefix-buyer" -DisplayName "桥合采购员" -RoleIds @($warehouseRole.id)
$demoDisabled = Ensure-User -Username "$DemoPrefix-disabled" -DisplayName "桥合停用账号" -RoleIds @($readonlyRole.id) -Status "DISABLED"

Write-Step "创建基础资料。"
$unitEach = Ensure-Unit -Code "$DemoPrefix-EA" -Name "件"
$unitSet = Ensure-Unit -Code "$DemoPrefix-SET" -Name "套"
$unitMeter = Ensure-Unit -Code "$DemoPrefix-M" -Name "米"
$unitKg = Ensure-Unit -Code "$DemoPrefix-KG" -Name "千克"
$unitBox = Ensure-Unit -Code "$DemoPrefix-BOX" -Name "箱"
$whRaw = Ensure-Warehouse -Code "$DemoPrefix-WH-RAW" -Name "一号原料仓"
$whProject = Ensure-Warehouse -Code "$DemoPrefix-WH-PROJ" -Name "项目专用仓"
$whFinished = Ensure-Warehouse -Code "$DemoPrefix-WH-FG" -Name "一号成品仓"
$whQuality = Ensure-Warehouse -Code "$DemoPrefix-WH-QC" -Name "质检暂存仓"
$category = Ensure-Category -Code "$DemoPrefix-CAT-ELEC" -Name "电气成套物料"

$customers = @()
$customerNames = @(
    "华东智能配电工程有限公司",
    "苏州轨道交通机电集成有限公司",
    "南京新能源装备制造有限公司",
    "宁波海工电气工程有限公司",
    "杭州数字厂房自动化有限公司"
)
for ($i = 1; $i -le 5; $i++) {
    $customers += Ensure-Partner -Kind "customer" -Code ("$DemoPrefix-CUST-{0:D2}" -f $i) -Name $customerNames[$i - 1]
}
$suppliers = @()
$supplierNames = @(
    "上海铜联导体材料有限公司",
    "无锡安电断路器有限公司",
    "常州线缆科技有限公司",
    "浙江柜体钣金制造有限公司",
    "昆山自动化模块有限公司"
)
for ($i = 1; $i -le 5; $i++) {
    $suppliers += Ensure-Partner -Kind "supplier" -Code ("$DemoPrefix-SUP-{0:D2}" -f $i) -Name $supplierNames[$i - 1]
}
for ($i = 0; $i -lt 3; $i++) {
    Ensure-SettlementTax -Kind "customer" -Partner $customers[$i] -Index ($i + 1) | Out-Null
    Ensure-SettlementTax -Kind "supplier" -Partner $suppliers[$i] -Index ($i + 1) | Out-Null
}

$materials = @{}
$materialSeed = @(
    @("$DemoPrefix-MAT-FG-A", "GGD 低压配电柜", "FINISHED_GOOD", "SELF_MADE", "NONE", $unitSet.id, $true, $true),
    @("$DemoPrefix-MAT-FG-B", "XL-21 动力柜", "FINISHED_GOOD", "SELF_MADE", "NONE", $unitSet.id, $true, $true),
    @("$DemoPrefix-MAT-SEMI-A", "铜排组件", "SEMI_FINISHED", "SELF_MADE", "BATCH", $unitEach.id, $true, $true),
    @("$DemoPrefix-MAT-SEMI-B", "控制线束", "SEMI_FINISHED", "SELF_MADE", "BATCH", $unitEach.id, $true, $true),
    @("$DemoPrefix-MAT-RAW-CU", "T2 铜排", "RAW_MATERIAL", "PURCHASED", "BATCH", $unitKg.id, $true, $true),
    @("$DemoPrefix-MAT-RAW-CABLE", "阻燃电缆", "RAW_MATERIAL", "PURCHASED", "BATCH", $unitMeter.id, $true, $true),
    @("$DemoPrefix-MAT-RAW-BREAKER", "框架断路器", "RAW_MATERIAL", "PURCHASED", "SERIAL", $unitEach.id, $true, $true),
    @("$DemoPrefix-MAT-RAW-METER", "智能电表", "RAW_MATERIAL", "PURCHASED", "SERIAL", $unitEach.id, $true, $false),
    @("$DemoPrefix-MAT-AUX-LABEL", "铭牌标签", "AUXILIARY", "PURCHASED", "NONE", $unitEach.id, $false, $false),
    @("$DemoPrefix-MAT-AUX-SCREW", "标准螺钉", "AUXILIARY", "PURCHASED", "NONE", $unitBox.id, $true, $false),
    @("$DemoPrefix-MAT-AUX-PAINT", "绝缘漆", "AUXILIARY", "PURCHASED", "NONE", $unitKg.id, $true, $false),
    @("$DemoPrefix-MAT-RAW-RAIL", "安装导轨", "RAW_MATERIAL", "PURCHASED", "BATCH", $unitMeter.id, $true, $false),
    @("$DemoPrefix-MAT-RAW-TERM", "接线端子", "RAW_MATERIAL", "PURCHASED", "NONE", $unitEach.id, $true, $false),
    @("$DemoPrefix-MAT-RAW-FAN", "散热风扇", "RAW_MATERIAL", "PURCHASED", "SERIAL", $unitEach.id, $true, $false),
    @("$DemoPrefix-MAT-RAW-PLC", "PLC 模块", "RAW_MATERIAL", "PURCHASED", "SERIAL", $unitEach.id, $true, $true),
    @("$DemoPrefix-MAT-RAW-SW", "转换开关", "RAW_MATERIAL", "PURCHASED", "NONE", $unitEach.id, $true, $false),
    @("$DemoPrefix-MAT-RAW-CT", "电流互感器", "RAW_MATERIAL", "PURCHASED", "BATCH", $unitEach.id, $true, $false),
    @("$DemoPrefix-MAT-RAW-LAMP", "指示灯", "RAW_MATERIAL", "PURCHASED", "NONE", $unitEach.id, $true, $false),
    @("$DemoPrefix-MAT-RAW-FUSE", "熔断器", "RAW_MATERIAL", "PURCHASED", "NONE", $unitEach.id, $true, $false),
    @("$DemoPrefix-MAT-RAW-PACK", "包装木箱", "AUXILIARY", "PURCHASED", "NONE", $unitEach.id, $false, $false)
)
foreach ($seed in $materialSeed) {
    $material = Ensure-Material -Code $seed[0] -Name $seed[1] -MaterialType $seed[2] -SourceType $seed[3] `
        -TrackingMethod $seed[4] -CategoryId $category.id -UnitId $seed[5] -Valued $seed[6] -ProjectCost $seed[7]
    $materials[$seed[0]] = $material
}
Ensure-UnitConversion -Material $materials["$DemoPrefix-MAT-RAW-CABLE"] -BusinessUnit $unitBox -Rate "100.000000" | Out-Null
Ensure-UnitConversion -Material $materials["$DemoPrefix-MAT-RAW-CU"] -BusinessUnit $unitBox -Rate "25.000000" | Out-Null
Ensure-UnitConversion -Material $materials["$DemoPrefix-MAT-AUX-SCREW"] -BusinessUnit $unitEach -Rate "0.010000" | Out-Null

Write-Step "创建编码规则和业务期间。"
$codingRules = @(
    [pscustomobject]@{ RuleCode = "$DemoPrefix-RULE-MAT"; Name = "演示物料编码"; ObjectType = "MATERIAL"; Prefix = "MAT"; Status = "ENABLED" },
    [pscustomobject]@{ RuleCode = "$DemoPrefix-RULE-CUST"; Name = "演示客户编码"; ObjectType = "CUSTOMER"; Prefix = "CUST"; Status = "ENABLED" },
    [pscustomobject]@{ RuleCode = "$DemoPrefix-RULE-SUP"; Name = "演示供应商编码"; ObjectType = "SUPPLIER"; Prefix = "SUP"; Status = "ENABLED" },
    [pscustomobject]@{ RuleCode = "$DemoPrefix-RULE-BOM"; Name = "演示 BOM 编码"; ObjectType = "BOM"; Prefix = "BOM"; Status = "ENABLED" },
    [pscustomobject]@{ RuleCode = "$DemoPrefix-RULE-ECO"; Name = "演示 ECO 编码"; ObjectType = "BOM_ECO"; Prefix = "ECO"; Status = "ENABLED" },
    [pscustomobject]@{ RuleCode = "$DemoPrefix-RULE-MAT-DIS"; Name = "停用物料编码"; ObjectType = "MATERIAL"; Prefix = "DMAT"; Status = "DISABLED" },
    [pscustomobject]@{ RuleCode = "$DemoPrefix-RULE-BOM-DIS"; Name = "停用 BOM 编码"; ObjectType = "BOM"; Prefix = "DBOM"; Status = "DISABLED" },
    [pscustomobject]@{ RuleCode = "$DemoPrefix-RULE-ECO-DIS"; Name = "停用 ECO 编码"; ObjectType = "BOM_ECO"; Prefix = "DECO"; Status = "DISABLED" }
)
foreach ($rule in $codingRules) {
    Ensure-CodingRule -RuleCode $rule.RuleCode -Name $rule.Name -ObjectType $rule.ObjectType -Prefix $rule.Prefix -Status $rule.Status | Out-Null
}
$openPeriod = Ensure-Period -Code $OpenPeriodCode -Name "$DemoPrefix 2026 年 7 月开放期间" -Start $OpenPeriodStart -End $OpenPeriodEnd
$lockedPeriod = Ensure-Period -Code $LockedPeriodCode -Name "$DemoPrefix 2026 年 6 月锁定期间" -Start $LockedPeriodStart -End $LockedPeriodEnd -Locked $true
$lockedPeriod = Ensure-PeriodAuditSamples -LockedPeriod $lockedPeriod

Write-Step "创建 BOM、替代料和 ECO。"
$fgA = $materials["$DemoPrefix-MAT-FG-A"]
$fgB = $materials["$DemoPrefix-MAT-FG-B"]
$semiA = $materials["$DemoPrefix-MAT-SEMI-A"]
$semiB = $materials["$DemoPrefix-MAT-SEMI-B"]
$cu = $materials["$DemoPrefix-MAT-RAW-CU"]
$cable = $materials["$DemoPrefix-MAT-RAW-CABLE"]
$breaker = $materials["$DemoPrefix-MAT-RAW-BREAKER"]
$label = $materials["$DemoPrefix-MAT-AUX-LABEL"]
$screw = $materials["$DemoPrefix-MAT-AUX-SCREW"]
$bomItem1 = @(
    [ordered]@{ lineNo = 1; childMaterialId = $semiA.id; businessUnitId = $semiA.unitId; businessQuantity = "2.000000"; lossRate = "0.010000"; remark = "铜排组件" },
    [ordered]@{ lineNo = 2; childMaterialId = $breaker.id; businessUnitId = $breaker.unitId; businessQuantity = "1.000000"; lossRate = "0.000000"; remark = "主断路器" },
    [ordered]@{ lineNo = 3; childMaterialId = $label.id; businessUnitId = $label.unitId; businessQuantity = "4.000000"; lossRate = "0.000000"; remark = "铭牌" }
)
$bomItem2 = @(
    [ordered]@{ lineNo = 1; childMaterialId = $cu.id; businessUnitId = $cu.unitId; businessQuantity = "18.000000"; lossRate = "0.020000"; remark = "铜排原料" },
    [ordered]@{ lineNo = 2; childMaterialId = $cable.id; businessUnitId = $cable.unitId; businessQuantity = "35.000000"; lossRate = "0.030000"; remark = "控制电缆" },
    [ordered]@{ lineNo = 3; childMaterialId = $screw.id; businessUnitId = $unitEach.id; businessQuantity = "20.000000"; lossRate = "0.000000"; remark = "端子固定螺钉" }
)
$bomCurrent = Ensure-Bom -Code "$DemoPrefix-BOM-FG-A-V1" -Parent $fgA -VersionCode "V1" -Name "GGD 当前 BOM" -Status "ENABLED" -EffectiveFrom $CurrentBomFrom -EffectiveTo $null -Items $bomItem1
$bomFuture = Ensure-Bom -Code "$DemoPrefix-BOM-FG-A-V2" -Parent $fgA -VersionCode "V2" -Name "GGD 未来 BOM" -Status "DRAFT" -EffectiveFrom $FutureBomFrom -EffectiveTo $null -Items $bomItem1
$bomHistory = Ensure-Bom -Code "$DemoPrefix-BOM-FG-B-HIS" -Parent $fgB -VersionCode "H1" -Name "XL 历史 BOM" -Status "ENABLED" -EffectiveFrom $HistoricalBomFrom -EffectiveTo $HistoricalBomTo -Items $bomItem1
$bomDraft = Ensure-Bom -Code "$DemoPrefix-BOM-SEMI-A-DRAFT" -Parent $semiA -VersionCode "D1" -Name "铜排组件草稿 BOM" -Status "DRAFT" -EffectiveFrom $Today -EffectiveTo $null -Items $bomItem2
$bomSemi = Ensure-Bom -Code "$DemoPrefix-BOM-SEMI-B-V1" -Parent $semiB -VersionCode "V1" -Name "线束当前 BOM" -Status "ENABLED" -EffectiveFrom $CurrentBomFrom -EffectiveTo $null -Items $bomItem2
$bomExtra = Ensure-Bom -Code "$DemoPrefix-BOM-FG-B-DRAFT" -Parent $fgB -VersionCode "D1" -Name "XL 草稿 BOM" -Status "DRAFT" -EffectiveFrom $Today -EffectiveTo $null -Items $bomItem1
$bomSemiDraft = Ensure-Bom -Code "$DemoPrefix-BOM-SEMI-B-DRAFT" -Parent $semiB -VersionCode "D2" -Name "线束未来草稿 BOM" -Status "DRAFT" -EffectiveFrom $FutureBomFrom -EffectiveTo $null -Items $bomItem2
Ensure-Substitute -Main $breaker -Substitute $materials["$DemoPrefix-MAT-RAW-PLC"] -ScopeType "BOM" -ScopeId $bomCurrent.id | Out-Null

$existingEco = Get-ItemByField -Path "/api/admin/bom-engineering-changes" -FieldName "ecoNo" -FieldValue "$DemoPrefix-ECO-001"
if ($null -eq $existingEco) {
    $eco = Invoke-DemoApi -Session $Session -Method Post -Path "/api/admin/bom-engineering-changes" -Body ([ordered]@{
        ecoNo = "$DemoPrefix-ECO-001"
        sourceBomId = $bomCurrent.id
        targetBomId = $bomFuture.id
        parentMaterialId = $fgA.id
        effectiveFrom = $FutureBomFrom
        effectiveTo = $null
        changeReason = "客户要求升级主断路器"
        impactScope = "后续订单"
        changeSummary = "当前 BOM 与未来 BOM 治理演示"
        status = "DRAFT"
        remark = "验收演示 ECO"
    })
    $Manifest.AddObject("eco", "$DemoPrefix-ECO-001", $eco.id)
}
else {
    $eco = $existingEco
}
$eco = Submit-And-ApproveBomEco -Eco $eco
$Manifest.AddObject("eco", "$DemoPrefix-ECO-001", $eco.id)
Ensure-BomEcoDraft -EcoNo "$DemoPrefix-ECO-002" -SourceBom $bomHistory -TargetBom $bomExtra `
    -EffectiveFrom $Today -Summary "XL 历史版本向当前草稿版本切换评估" | Out-Null
Ensure-BomEcoDraft -EcoNo "$DemoPrefix-ECO-003" -SourceBom $bomSemi -TargetBom $bomSemiDraft `
    -EffectiveFrom $FutureBomFrom -Summary "控制线束未来版本工程变更评估" | Out-Null

Write-Step "创建采购入库、批次序列和质检结果。"
$poMain = Ensure-PurchaseOrder -Key "$DemoPrefix-PO-MAIN" -Supplier $suppliers[0] -Lines @(
    [ordered]@{ lineNo = 1; materialId = $cu.id; unitId = $cu.unitId; quantity = "100.000000"; unitPrice = "80.000000"; expectedArrivalDate = "2026-07-06"; remark = "T2 铜排批次采购" },
    [ordered]@{ lineNo = 2; materialId = $cable.id; unitId = $cable.unitId; quantity = "500.000000"; unitPrice = "4.500000"; expectedArrivalDate = "2026-07-06"; remark = "阻燃电缆批次采购" },
    [ordered]@{ lineNo = 3; materialId = $breaker.id; unitId = $breaker.unitId; quantity = "3.000000"; unitPrice = "1200.000000"; expectedArrivalDate = "2026-07-06"; remark = "框架断路器序列采购" }
)
$poAux = Ensure-PurchaseOrder -Key "$DemoPrefix-PO-AUX" -Supplier $suppliers[1] -Lines @(
    [ordered]@{ lineNo = 1; materialId = $materials["$DemoPrefix-MAT-AUX-SCREW"].id; unitId = $unitBox.id; quantity = "10.000000"; unitPrice = "35.000000"; expectedArrivalDate = "2026-07-07"; remark = "标准螺钉采购" },
    [ordered]@{ lineNo = 2; materialId = $materials["$DemoPrefix-MAT-RAW-RAIL"].id; unitId = $unitMeter.id; quantity = "200.000000"; unitPrice = "8.000000"; expectedArrivalDate = "2026-07-07"; remark = "安装导轨采购" }
)
$poSerial = Ensure-PurchaseOrder -Key "$DemoPrefix-PO-SERIAL" -Supplier $suppliers[4] -Lines @(
    [ordered]@{ lineNo = 1; materialId = $materials["$DemoPrefix-MAT-RAW-METER"].id; unitId = $unitEach.id; quantity = "4.000000"; unitPrice = "260.000000"; expectedArrivalDate = "2026-07-07"; remark = "智能电表序列采购" },
    [ordered]@{ lineNo = 2; materialId = $materials["$DemoPrefix-MAT-RAW-PLC"].id; unitId = $unitEach.id; quantity = "2.000000"; unitPrice = "1800.000000"; expectedArrivalDate = "2026-07-07"; remark = "PLC 模块序列采购" }
)

$receiptMain = Ensure-PurchaseReceipt -Key "$DemoPrefix-PR-MAIN" -Order $poMain -Warehouse $whRaw -Lines @(
    [ordered]@{
        lineNo = 1
        orderLineId = $poMain.lines[0].id
        materialId = $cu.id
        unitId = $cu.unitId
        quantity = "100.000000"
        remark = "铜排批次入库"
        trackingAllocations = @(
            [ordered]@{ batchNo = "$DemoPrefix-BATCH-CU-Q"; quantity = "90.000000" },
            [ordered]@{ batchNo = "$DemoPrefix-BATCH-CU-R"; quantity = "5.000000" },
            [ordered]@{ batchNo = "$DemoPrefix-BATCH-CU-F"; quantity = "5.000000" }
        )
    },
    [ordered]@{
        lineNo = 2
        orderLineId = $poMain.lines[1].id
        materialId = $cable.id
        unitId = $cable.unitId
        quantity = "500.000000"
        remark = "电缆批次入库"
        trackingAllocations = @([ordered]@{ batchNo = "$DemoPrefix-BATCH-CABLE-01"; quantity = "500.000000" })
    },
    [ordered]@{
        lineNo = 3
        orderLineId = $poMain.lines[2].id
        materialId = $breaker.id
        unitId = $breaker.unitId
        quantity = "3.000000"
        remark = "断路器序列入库"
        trackingAllocations = @(
            [ordered]@{ serialNo = "$DemoPrefix-SN-BREAKER-001"; quantity = "1.000000" },
            [ordered]@{ serialNo = "$DemoPrefix-SN-BREAKER-002"; quantity = "1.000000" },
            [ordered]@{ serialNo = "$DemoPrefix-SN-BREAKER-003"; quantity = "1.000000" }
        )
    }
)
$receiptAux = Ensure-PurchaseReceipt -Key "$DemoPrefix-PR-AUX" -Order $poAux -Warehouse $whRaw -Lines @(
    [ordered]@{ lineNo = 1; orderLineId = $poAux.lines[0].id; materialId = $materials["$DemoPrefix-MAT-AUX-SCREW"].id; unitId = $unitBox.id; quantity = "10.000000"; remark = "螺钉入库" },
    [ordered]@{ lineNo = 2; orderLineId = $poAux.lines[1].id; materialId = $materials["$DemoPrefix-MAT-RAW-RAIL"].id; unitId = $unitMeter.id; quantity = "200.000000"; remark = "导轨批次入库"; trackingAllocations = @([ordered]@{ batchNo = "$DemoPrefix-BATCH-RAIL-01"; quantity = "200.000000" }) }
)
$receiptSerial = Ensure-PurchaseReceipt -Key "$DemoPrefix-PR-SERIAL" -Order $poSerial -Warehouse $whRaw -Lines @(
    [ordered]@{
        lineNo = 1
        orderLineId = $poSerial.lines[0].id
        materialId = $materials["$DemoPrefix-MAT-RAW-METER"].id
        unitId = $unitEach.id
        quantity = "4.000000"
        remark = "电表序列入库"
        trackingAllocations = @(
            [ordered]@{ serialNo = "$DemoPrefix-SN-METER-001"; quantity = "1.000000" },
            [ordered]@{ serialNo = "$DemoPrefix-SN-METER-002"; quantity = "1.000000" },
            [ordered]@{ serialNo = "$DemoPrefix-SN-METER-003"; quantity = "1.000000" },
            [ordered]@{ serialNo = "$DemoPrefix-SN-METER-004"; quantity = "1.000000" }
        )
    },
    [ordered]@{
        lineNo = 2
        orderLineId = $poSerial.lines[1].id
        materialId = $materials["$DemoPrefix-MAT-RAW-PLC"].id
        unitId = $unitEach.id
        quantity = "2.000000"
        remark = "PLC 序列入库"
        trackingAllocations = @(
            [ordered]@{ serialNo = "$DemoPrefix-SN-PLC-001"; quantity = "1.000000" },
            [ordered]@{ serialNo = "$DemoPrefix-SN-PLC-002"; quantity = "1.000000" }
        )
    }
)
Process-PendingQualityInspections

Write-Step "创建销售项目、合同附件和合同固定审批。"
$projectA = Ensure-SalesProject -Key "$DemoPrefix-SALES-PROJ-A" -Customer $customers[0] -OwnerUserId $demoAdmin.id `
    -Name "桥合低压柜技改项目" -Revenue "280000.00" -Cost "190000.00"
$mainContract = Ensure-SalesContract -Key "$DemoPrefix-CONTRACT-MAIN" -Project $projectA -ContractType "MAIN" `
    -MainContractId $null -Name "桥合低压柜技改主合同" -Amount "180000.00" -ExternalNo "$DemoPrefix-EXT-MAIN"
Upload-Attachment -ObjectType "SALES_PROJECT_CONTRACT" -ObjectId $mainContract.id `
    -AssetName "bridge-electrical-contract-note.txt" -Description "合同生效审批依据" | Out-Null
$mainContract = Submit-And-ActSalesContractApproval -Contract $mainContract -Action "approve" -Key "MAIN"
$projectA = Get-ItemById -Path "/api/admin/sales-projects" -Id $projectA.id
if ($projectA.status -ne "ACTIVE") {
    $projectA = Invoke-DemoApi -Session $Session -Method Put -Path "/api/admin/sales-projects/$($projectA.id)/activate" -Body ([ordered]@{
        version = $projectA.version
        reason = "主合同生效后启动验收演示项目"
    })
}
$supplementContract = Ensure-SalesContract -Key "$DemoPrefix-CONTRACT-SUPP" -Project $projectA -ContractType "SUPPLEMENT" `
    -MainContractId $mainContract.id -Name "桥合低压柜技改补充合同" -Amount "25000.00" -ExternalNo "$DemoPrefix-EXT-SUPP"
$supplementContract = Submit-And-ActSalesContractApproval -Contract $supplementContract -Action "approve" -Key "SUPP"

$projectB = Ensure-SalesProject -Key "$DemoPrefix-SALES-PROJ-B" -Customer $customers[1] -OwnerUserId $demoPlanner.id `
    -Name "桥合轨交配电箱项目" -Revenue "160000.00" -Cost "118000.00"
$rejectContract = Ensure-SalesContract -Key "$DemoPrefix-CONTRACT-REJECT" -Project $projectA -ContractType "SUPPLEMENT" `
    -MainContractId $mainContract.id -Name "桥合低压柜技改驳回补充合同" -Amount "9000.00" -ExternalNo "$DemoPrefix-EXT-REJECT"
Upload-Attachment -ObjectType "SALES_PROJECT_CONTRACT" -ObjectId $rejectContract.id `
    -AssetName "bridge-electrical-shipment-note.txt" -Description "合同驳回审批依据" | Out-Null
$rejectContract = Submit-And-ActSalesContractApproval -Contract $rejectContract -Action "reject" -Key "REJECT"

$withdrawContract = Ensure-SalesContract -Key "$DemoPrefix-CONTRACT-WITHDRAW" -Project $projectA -ContractType "SUPPLEMENT" `
    -MainContractId $mainContract.id -Name "桥合低压柜技改撤回补充合同" -Amount "9200.00" -ExternalNo "$DemoPrefix-EXT-WITHDRAW"
$withdrawContract = Submit-And-ActSalesContractApproval -Contract $withdrawContract -Action "withdraw" -Key "WITHDRAW"

$cancelContract = Ensure-SalesContract -Key "$DemoPrefix-CONTRACT-CANCEL" -Project $projectA -ContractType "SUPPLEMENT" `
    -MainContractId $mainContract.id -Name "桥合低压柜技改取消补充合同" -Amount "9400.00" -ExternalNo "$DemoPrefix-EXT-CANCEL"
$cancelContract = Submit-And-ActSalesContractApproval -Contract $cancelContract -Action "cancel" -Key "CANCEL"
$pendingApprovalContract = Ensure-PendingApprovalTaskSample -Project $projectA -MainContract $mainContract
Ensure-AdditionalDemoAttachments -MainContract $mainContract -SupplementContract $supplementContract `
    -RejectContract $rejectContract -WithdrawContract $withdrawContract -CancelContract $cancelContract -Eco $eco
Ensure-DeniedAuditSample

Write-Step "创建项目库存权属转换和仓库调拨。"
$batchCuQualified = Get-InventoryBatch -BatchNo "$DemoPrefix-BATCH-CU-Q"
$batchCable = Get-InventoryBatch -BatchNo "$DemoPrefix-BATCH-CABLE-01"
$plcSerial = Invoke-DemoApiPage -Session $Session -Path "/api/admin/inventory/serials" -Parameters @{
    serialNo = "$DemoPrefix-SN-PLC-001"
    onlyAvailable = "true"
    page = 1
    pageSize = 20
} | Where-Object { $_.serialNo -eq "$DemoPrefix-SN-PLC-001" } | Select-Object -First 1
if ($null -eq $plcSerial) {
    throw "未找到可用 PLC 序列号 DEMO-ELEC-20260715-SN-PLC-001。"
}

$ownCuA = Ensure-OwnershipConversionPosted -Key "PROJECT-CU-A" -Reason "验收演示项目 A 铜排实际成本层一" -Lines @(
    [ordered]@{
        lineNo = 1
        sourceOwnershipType = "PUBLIC"
        sourceProjectId = $null
        targetOwnershipType = "PROJECT"
        targetProjectId = $projectA.id
        sourceWarehouseId = $whRaw.id
        targetWarehouseId = $whProject.id
        materialId = $cu.id
        unitId = $cu.unitId
        qualityStatus = "QUALIFIED"
        batchId = $batchCuQualified.id
        serialId = $null
        quantity = "20.000000"
    }
)
$ownCuB = Ensure-OwnershipConversionPosted -Key "PROJECT-CU-B" -Reason "验收演示项目 A 铜排实际成本层二" -Lines @(
    [ordered]@{
        lineNo = 1
        sourceOwnershipType = "PUBLIC"
        sourceProjectId = $null
        targetOwnershipType = "PROJECT"
        targetProjectId = $projectA.id
        sourceWarehouseId = $whRaw.id
        targetWarehouseId = $whProject.id
        materialId = $cu.id
        unitId = $cu.unitId
        qualityStatus = "QUALIFIED"
        batchId = $batchCuQualified.id
        serialId = $null
        quantity = "15.000000"
    }
)
$ownCable = Ensure-OwnershipConversionPosted -Key "PROJECT-CABLE" -Reason "验收演示项目 B 电缆实际成本层" -Lines @(
    [ordered]@{
        lineNo = 1
        sourceOwnershipType = "PUBLIC"
        sourceProjectId = $null
        targetOwnershipType = "PROJECT"
        targetProjectId = $projectB.id
        sourceWarehouseId = $whRaw.id
        targetWarehouseId = $whProject.id
        materialId = $cable.id
        unitId = $cable.unitId
        qualityStatus = "QUALIFIED"
        batchId = $batchCable.id
        serialId = $null
        quantity = "60.000000"
    }
)
$projectCuLayer = Invoke-DemoApiPage -Session $Session -Path "/api/admin/inventory/cost-layers" -Parameters @{
    ownershipType = "PROJECT"
    projectId = $projectA.id
    materialId = $cu.id
    warehouseId = $whProject.id
    status = "ACTIVE"
    page = 1
    pageSize = 20
} | Select-Object -First 1
if ($null -eq $projectCuLayer) {
    throw "项目铜排成本层未通过所有权转换真实生成。"
}
$projectCableLayer = Invoke-DemoApiPage -Session $Session -Path "/api/admin/inventory/cost-layers" -Parameters @{
    ownershipType = "PROJECT"
    projectId = $projectB.id
    materialId = $cable.id
    warehouseId = $whProject.id
    status = "ACTIVE"
    page = 1
    pageSize = 20
} | Select-Object -First 1
if ($null -eq $projectCableLayer) {
    throw "项目电缆成本层未通过所有权转换真实生成。"
}
$ownProjectToProject = Ensure-OwnershipConversionPosted -Key "PROJECT-CABLE-REASSIGN" -Reason "验收演示项目 B 电缆转项目 A" -Lines @(
    [ordered]@{
        lineNo = 1
        sourceOwnershipType = "PROJECT"
        sourceProjectId = $projectB.id
        targetOwnershipType = "PROJECT"
        targetProjectId = $projectA.id
        sourceWarehouseId = $whProject.id
        targetWarehouseId = $whProject.id
        materialId = $cable.id
        unitId = $cable.unitId
        qualityStatus = "QUALIFIED"
        batchId = $batchCable.id
        serialId = $null
        quantity = "10.000000"
        sourceCostLayerId = $projectCableLayer.id
    }
)
$ownProjectToPublic = Ensure-OwnershipConversionPosted -Key "PROJECT-CU-RETURN-PUBLIC" -Reason "验收演示项目 A 铜排退回公共库存" -Lines @(
    [ordered]@{
        lineNo = 1
        sourceOwnershipType = "PROJECT"
        sourceProjectId = $projectA.id
        targetOwnershipType = "PUBLIC"
        targetProjectId = $null
        sourceWarehouseId = $whProject.id
        targetWarehouseId = $whRaw.id
        materialId = $cu.id
        unitId = $cu.unitId
        qualityStatus = "QUALIFIED"
        batchId = $batchCuQualified.id
        serialId = $null
        quantity = "3.000000"
        sourceCostLayerId = $projectCuLayer.id
    }
)
Ensure-WarehouseTransferPosted -Key "PROJECT-CU" -Reason "验收演示项目铜排从项目仓调拨至成品仓" -Lines @(
    [ordered]@{
        lineNo = 1
        sourceWarehouseId = $whProject.id
        targetWarehouseId = $whFinished.id
        ownershipType = "PROJECT"
        projectId = $projectA.id
        materialId = $cu.id
        unitId = $cu.unitId
        qualityStatus = "QUALIFIED"
        batchId = $batchCuQualified.id
        serialId = $null
        quantity = "5.000000"
        sourceCostLayerId = $projectCuLayer.id
    }
) | Out-Null

Write-Step "创建生产工单、领料、报工和完工入库。"
$workOrderComplete = Ensure-WorkOrderReleased -Key "COMPLETE-SEMI-B" -Product $semiB -Bom $bomSemi `
    -PlannedQuantity "3.000000" -IssueWarehouse $whRaw -ReceiptWarehouse $whFinished
$workOrderReleased = Ensure-WorkOrderReleased -Key "RELEASED-SAMPLE" -Product $semiB -Bom $bomSemi `
    -PlannedQuantity "0.100000" -IssueWarehouse $whRaw -ReceiptWarehouse $whFinished
$workOrderReservation = Ensure-WorkOrderReleased -Key "DEMO-RESERVATION-SAMPLE" -Product $semiB -Bom $bomSemi `
    -PlannedQuantity "0.001000" -IssueWarehouse $whRaw -ReceiptWarehouse $whFinished
$workOrderDraftA = Ensure-WorkOrderDraft -Key "DRAFT-SAMPLE-A" -Product $semiB -Bom $bomSemi `
    -PlannedQuantity "0.100000" -IssueWarehouse $whRaw -ReceiptWarehouse $whFinished
$workOrderDraftB = Ensure-WorkOrderDraft -Key "DRAFT-SAMPLE-B" -Product $semiB -Bom $bomSemi `
    -PlannedQuantity "0.100000" -IssueWarehouse $whRaw -ReceiptWarehouse $whFinished
$workOrderCancelled = Ensure-WorkOrderCancelled -Key "CANCELLED-SAMPLE" -Product $semiB -Bom $bomSemi `
    -IssueWarehouse $whRaw -ReceiptWarehouse $whFinished
$woCuLine = @($workOrderComplete.materials) | Where-Object { $_.materialCode -eq $cu.code } | Select-Object -First 1
$woCableLine = @($workOrderComplete.materials) | Where-Object { $_.materialCode -eq $cable.code } | Select-Object -First 1
$woScrewLine = @($workOrderComplete.materials) | Where-Object { $_.materialCode -eq $screw.code } | Select-Object -First 1
$productionExecution = Ensure-ProductionExecutionPosted -Key "COMPLETE-SEMI-B" -WorkOrder $workOrderComplete -IssueLines @(
    (New-IssueLineForMaterial -WorkOrder $workOrderComplete -MaterialCode $cu.code -WarehouseId $whRaw.id -Quantity $woCuLine.requiredQuantity `
        -TrackingAllocations @([ordered]@{ batchId = $batchCuQualified.id; quantity = $woCuLine.requiredQuantity })),
    (New-IssueLineForMaterial -WorkOrder $workOrderComplete -MaterialCode $cable.code -WarehouseId $whRaw.id -Quantity $woCableLine.requiredQuantity `
        -TrackingAllocations @([ordered]@{ batchId = $batchCable.id; quantity = $woCableLine.requiredQuantity })),
    (New-IssueLineForMaterial -WorkOrder $workOrderComplete -MaterialCode $screw.code -WarehouseId $whRaw.id -Quantity $woScrewLine.requiredQuantity)
) -CompletionQuantity "3.000000" -CompletionTrackingAllocations @([ordered]@{
    batchNo = "$DemoPrefix-BATCH-SEMI-B-001"
    quantity = "3.000000"
})
Process-PendingQualityInspections -SourceType "PRODUCTION_COMPLETION" -SourceIds @([long]$productionExecution.receipt.id) -BusinessDate "2026-07-14"
$supplementSourceIssue = Ensure-ProductionIssuePosted -Key "SUPPLEMENT-SOURCE" -WorkOrder $workOrderReleased -IssueLines @(
    (New-IssueLineForMaterial -WorkOrder $workOrderReleased -MaterialCode $cable.code -WarehouseId $whRaw.id -Quantity "1.000000" `
        -TrackingAllocations @([ordered]@{ batchId = $batchCable.id; quantity = "1.000000" }))
) -BusinessDate "2026-07-13"

Write-Step "创建销售订单、预留和销售发货。"
$semiBBatch = Get-InventoryBatch -BatchNo "$DemoPrefix-BATCH-SEMI-B-001"
$salesOrderSemiA = Ensure-SalesOrderConfirmed -Key "SEMI-B-A" -Customer $customers[0] -Project $projectA -Contract $mainContract -Lines @(
    [ordered]@{
        lineNo = 1
        materialId = $semiB.id
        unitId = $semiB.unitId
        quantity = "1.000000"
        unitPrice = "3200.00"
        reservationWarehouseId = $whFinished.id
        expectedShipDate = "2026-07-14"
        remark = "控制线束销售演示 A"
    }
)
$salesOrderSemiB = Ensure-SalesOrderConfirmed -Key "SEMI-B-B" -Customer $customers[3] -Lines @(
    [ordered]@{
        lineNo = 1
        materialId = $semiB.id
        unitId = $semiB.unitId
        quantity = "1.000000"
        unitPrice = "3300.00"
        reservationWarehouseId = $whFinished.id
        expectedShipDate = "2026-07-14"
        remark = "控制线束销售演示 B"
    }
)
$salesOrderSemiReserved = Ensure-SalesOrderConfirmed -Key "SEMI-B-RESERVED" -Customer $customers[4] -Lines @(
    [ordered]@{
        lineNo = 1
        materialId = $semiB.id
        unitId = $semiB.unitId
        quantity = "1.000000"
        unitPrice = "3350.00"
        reservationWarehouseId = $whFinished.id
        expectedShipDate = "2026-07-14"
        remark = "保留未发货预留演示"
    }
)
$shipmentSemiA = Ensure-SalesShipmentPosted -Key "SEMI-B-A" -Order $salesOrderSemiA -Warehouse $whFinished -Lines @(
    [ordered]@{
        lineNo = 1
        orderLineId = $salesOrderSemiA.lines[0].id
        materialId = $semiB.id
        unitId = $semiB.unitId
        quantity = "1.000000"
        remark = "控制线束销售出库 A"
        trackingAllocations = @([ordered]@{ batchId = $semiBBatch.id; quantity = "1.000000" })
    }
)
$shipmentSemiB = Ensure-SalesShipmentPosted -Key "SEMI-B-B" -Order $salesOrderSemiB -Warehouse $whFinished -Lines @(
    [ordered]@{
        lineNo = 1
        orderLineId = $salesOrderSemiB.lines[0].id
        materialId = $semiB.id
        unitId = $semiB.unitId
        quantity = "1.000000"
        remark = "控制线束销售出库 B"
        trackingAllocations = @([ordered]@{ batchId = $semiBBatch.id; quantity = "1.000000" })
    }
)

Write-Step "创建应收应付、收付款和冲销单据。"
$financeSettlement = Ensure-FinanceSettlementPosted -ReceivableShipments @($shipmentSemiA, $shipmentSemiB) `
    -PayableReceipts @($receiptMain, $receiptAux)
$reversalDocs = Ensure-ReversalDocumentsPosted -SalesShipment $shipmentSemiA -PurchaseReceipt $receiptMain `
    -ProductionIssue $productionExecution.issue -WorkOrder $workOrderReleased `
    -ReceivableForAdjustment $financeSettlement.adjustmentReceivable -AdjustmentReceipt $financeSettlement.adjustmentReceipt

Write-Step "创建估值调整和盘点单据。"
$valuationAdjustment = Ensure-ValuationAdjustmentPosted -Material $materials["$DemoPrefix-MAT-RAW-RAIL"] -ProjectLayer $projectCuLayer
$stocktakes = Ensure-StocktakeDocuments -ProjectLayer $projectCuLayer

Write-Step "创建文档任务样例。"
Ensure-DocumentTaskSamples -ContractApproval $mainContract.approvalSummary -BomEcoApproval $eco.approvalSummary `
    -BomDraft $bomDraft -BomImportParent $fgB -BomImportChild $label

$Manifest.AddNote("核心业务数据生成坚持 API-only；未使用业务 INSERT/UPDATE SQL。")
$Manifest.Save()
Write-Step "基础生成完成。Manifest=$OutputManifestPath"
