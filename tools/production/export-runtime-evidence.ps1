[CmdletBinding()]
param([string] $OutputDirectory)

$ErrorActionPreference = "Stop"
Set-StrictMode -Version Latest
. (Join-Path $PSScriptRoot "lib\production-common.ps1")

$commit = Get-QherpSourceCommit
$stamp = (Get-Date).ToUniversalTime().ToString("yyyyMMdd-HHmmss")
if ([string]::IsNullOrWhiteSpace($OutputDirectory)) {
    $OutputDirectory = Join-Path $env:USERPROFILE ".codex\backups\qherp\035-runtime-$stamp"
}
$OutputDirectory = [IO.Path]::GetFullPath($OutputDirectory)
if (Test-Path -LiteralPath $OutputDirectory) {
    throw "运行证据目录已存在：$OutputDirectory"
}
New-Item -ItemType Directory -Path $OutputDirectory -Force | Out-Null

$secrets = Import-QherpProductionSecrets
$databaseUsername = Get-QherpContainerEnvironmentValue -ContainerName "qherp035-postgres-1" -VariableName "POSTGRES_USER"
$containers = [Collections.Generic.List[object]]::new()
$imagesById = @{}
foreach ($containerName in @("qherp035-postgres-1", "qherp035-minio-1", "qherp035-secret-store-1", "qherp035-api-1", "qherp035-web-1")) {
    $inspection = @(& docker inspect $containerName | ConvertFrom-Json)[0]
    $healthProperty = $inspection.State.PSObject.Properties["Health"]
    $health = if ($null -ne $healthProperty -and $null -ne $healthProperty.Value) {
        [string]$healthProperty.Value.Status
    }
    else {
        [string]$inspection.State.Status
    }
    $ports = @($inspection.NetworkSettings.Ports.PSObject.Properties | ForEach-Object {
        $bindings = @($_.Value | ForEach-Object { "$($_.HostIp):$($_.HostPort)" })
        [pscustomobject]@{ ContainerPort = $_.Name; HostBindings = $bindings }
    })
    $containers.Add([pscustomobject]@{
        Name = $containerName
        Image = [string]$inspection.Config.Image
        ImageId = [string]$inspection.Image
        Status = [string]$inspection.State.Status
        Health = $health
        RestartPolicy = [string]$inspection.HostConfig.RestartPolicy.Name
        MemoryBytes = [long]$inspection.HostConfig.Memory
        CpuQuota = [long]$inspection.HostConfig.CpuQuota
        CpuPeriod = [long]$inspection.HostConfig.CpuPeriod
        ReadonlyRootfs = [bool]$inspection.HostConfig.ReadonlyRootfs
        LogDriver = [string]$inspection.HostConfig.LogConfig.Type
        LogMaxSize = [string]$inspection.HostConfig.LogConfig.Config."max-size"
        LogMaxFiles = [string]$inspection.HostConfig.LogConfig.Config."max-file"
        Ports = $ports
    })
    $imagesById[[string]$inspection.Image] = [string]$inspection.Config.Image
}

$images = [Collections.Generic.List[object]]::new()
foreach ($item in $imagesById.GetEnumerator()) {
    $inspection = @(& docker image inspect $item.Key | ConvertFrom-Json)[0]
    $sourceRevision = $null
    $labelsProperty = $inspection.Config.PSObject.Properties["Labels"]
    if ($null -ne $labelsProperty -and $null -ne $labelsProperty.Value) {
        $revisionProperty = $labelsProperty.Value.PSObject.Properties["org.opencontainers.image.revision"]
        if ($null -ne $revisionProperty) {
            $sourceRevision = [string]$revisionProperty.Value
        }
    }
    $images.Add([pscustomobject]@{
        Reference = $item.Value
        Id = [string]$inspection.Id
        RepoDigests = @($inspection.RepoDigests)
        Created = [string]$inspection.Created
        SizeBytes = [long]$inspection.Size
        SourceRevision = $sourceRevision
    })
}

$databaseName = [string]$secrets.DatabaseName
$bucket = [string]$secrets.S3Bucket
$databaseSummary = @(Invoke-QherpPostgresScalar -ContainerName "qherp035-postgres-1" `
    -DatabaseUsername $databaseUsername -DatabaseName $databaseName -Sql @"
select current_setting('server_version');
select version from flyway_schema_history where success order by installed_rank desc limit 1;
select count(*) from flyway_schema_history where not success;
select count(*) from sys_permission;
select count(*) from sys_role;
select count(*) from sys_user;
select count(*) from platform_file_object where status = 'AVAILABLE';
"@)

$permissionCsv = Join-Path $OutputDirectory "permission-matrix.csv"
$rolePermissionCsv = Join-Path $OutputDirectory "role-permission-matrix.csv"
$userRoleCsv = Join-Path $OutputDirectory "user-role-matrix.csv"
$permissionSql = "\copy (select id,code,name,type,parent_id,route_path,api_method,api_path,sort_order from sys_permission order by id) to stdout with csv header"
$rolePermissionSql = "\copy (select r.code as role_code,r.name as role_name,p.code as permission_code,p.type as permission_type from sys_role r join sys_role_permission rp on rp.role_id=r.id join sys_permission p on p.id=rp.permission_id order by r.code,p.code) to stdout with csv header"
$userRoleSql = "\copy (select u.username,u.status,r.code as role_code,r.name as role_name from sys_user u join sys_user_role ur on ur.user_id=u.id join sys_role r on r.id=ur.role_id order by u.username,r.code) to stdout with csv header"
@(& docker exec "qherp035-postgres-1" psql -X -U $databaseUsername -d $databaseName -c $permissionSql) `
    | Set-Content -LiteralPath $permissionCsv -Encoding utf8
if ($LASTEXITCODE -ne 0) { throw "导出权限矩阵失败。" }
@(& docker exec "qherp035-postgres-1" psql -X -U $databaseUsername -d $databaseName -c $rolePermissionSql) `
    | Set-Content -LiteralPath $rolePermissionCsv -Encoding utf8
if ($LASTEXITCODE -ne 0) { throw "导出角色权限矩阵失败。" }
@(& docker exec "qherp035-postgres-1" psql -X -U $databaseUsername -d $databaseName -c $userRoleSql) `
    | Set-Content -LiteralPath $userRoleCsv -Encoding utf8
if ($LASTEXITCODE -ne 0) { throw "导出用户角色矩阵失败。" }

$secretPath = Get-QherpDefaultSecretPath
$secretAcl = Get-Acl -LiteralPath $secretPath
$configurationFiles = @(
    "compose.production.yaml",
    "apps/api/Dockerfile",
    "apps/api/docker-entrypoint.sh",
    "apps/api/src/main/resources/application-production.properties",
    "apps/web/Dockerfile",
    "apps/web/nginx.production.conf"
) | ForEach-Object {
    $path = Join-Path (Get-QherpRepoRoot) $_
    [pscustomobject]@{ Path = $_; Sha256 = Get-QherpFileSha256 -Path $path }
}

$report = [ordered]@{
    SchemaVersion = 1
    CapturedAtUtc = [DateTimeOffset]::UtcNow.ToString("o")
    SourceCommit = $commit
    ComposeProject = "qherp035"
    Containers = @($containers)
    Images = @($images)
    ConfigurationFiles = $configurationFiles
    Database = [ordered]@{
        Name = $databaseName
        PostgresVersion = $databaseSummary[0]
        FlywayVersion = $databaseSummary[1]
        FailedMigrationCount = [int]$databaseSummary[2]
        PermissionCount = [int]$databaseSummary[3]
        RoleCount = [int]$databaseSummary[4]
        UserCount = [int]$databaseSummary[5]
        AvailableFileObjectCount = [int]$databaseSummary[6]
    }
    ObjectStorage = [ordered]@{ Bucket = $bucket; ExpectedObjectCount = [int]$databaseSummary[6] }
    SecretCarrier = [ordered]@{
        SchemaVersion = [int]$secrets.SchemaVersion
        ProjectName = [string]$secrets.ProjectName
        Path = $secretPath
        Owner = [string]$secretAcl.Owner
        AccessRuleCount = @($secretAcl.Access).Count
        InheritanceProtected = [bool]$secretAcl.AreAccessRulesProtected
    }
    EvidenceFiles = @(
        [pscustomobject]@{ Path = "permission-matrix.csv"; Sha256 = Get-QherpFileSha256 $permissionCsv },
        [pscustomobject]@{ Path = "role-permission-matrix.csv"; Sha256 = Get-QherpFileSha256 $rolePermissionCsv },
        [pscustomobject]@{ Path = "user-role-matrix.csv"; Sha256 = Get-QherpFileSha256 $userRoleCsv }
    )
}
$reportPath = Join-Path $OutputDirectory "runtime-evidence.json"
$report | ConvertTo-Json -Depth 12 | Set-Content -LiteralPath $reportPath -Encoding utf8
(Get-QherpFileSha256 -Path $reportPath) | Set-Content -LiteralPath (Join-Path $OutputDirectory "runtime-evidence.sha256") -Encoding ascii

if ($report.Database.FlywayVersion -ne "36" -or $report.Database.FailedMigrationCount -ne 0 -or
    $report.Database.PermissionCount -ne 507 -or @($containers | Where-Object { $_.Health -notin @("healthy", "running") }).Count -ne 0 -or
    -not $report.SecretCarrier.InheritanceProtected -or $report.SecretCarrier.AccessRuleCount -ne 1) {
    throw "运行证据未满足 V36、507 权限、容器健康或 DPAPI ACL 门禁。"
}
Write-Host "运行与权限证据已导出：$OutputDirectory" -ForegroundColor Green
