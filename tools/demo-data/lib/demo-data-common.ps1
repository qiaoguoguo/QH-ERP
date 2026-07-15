$ErrorActionPreference = "Stop"

function Assert-DemoPwshRuntime {
    $minimumVersion = [version]"7.0.0"
    $actualVersion = $PSVersionTable.PSVersion
    if ($actualVersion -lt $minimumVersion -or $PSVersionTable.PSEdition -ne "Core") {
        throw "演示数据脚本仅支持 pwsh >= 7。当前运行时：PSEdition=$($PSVersionTable.PSEdition), PSVersion=$actualVersion。请使用 pwsh 7.5.8 或更高版本运行。"
    }
}

Assert-DemoPwshRuntime

function Test-DemoResourceName {
    param(
        [Parameter(Mandatory = $true)][string] $Value,
        [Parameter(Mandatory = $true)][string] $Prefix
    )
    if ([string]::IsNullOrWhiteSpace($Value)) {
        return $false
    }
    if (-not $Value.StartsWith($Prefix, [StringComparison]::Ordinal)) {
        return $false
    }
    return $Value -match '^[a-zA-Z0-9][a-zA-Z0-9_-]*[a-zA-Z0-9]$'
}

function ConvertTo-DemoJson {
    param(
        [Parameter(Mandatory = $true)] $Value,
        [int] $Depth = 20
    )
    $json = ConvertTo-Json -InputObject $Value -Depth $Depth -Compress
    return [string] $json
}

function New-DemoDirectory {
    param([Parameter(Mandatory = $true)][string] $Path)
    if (-not (Test-Path -LiteralPath $Path)) {
        New-Item -ItemType Directory -Path $Path | Out-Null
    }
    return (Resolve-Path -LiteralPath $Path).Path
}

function Invoke-DemoProcess {
    param(
        [Parameter(Mandatory = $true)][string] $FilePath,
        [string[]] $ArgumentList = @(),
        [string] $InputText,
        [string] $WorkingDirectory = (Get-Location).Path,
        [switch] $AllowFailure
    )
    $startInfo = [System.Diagnostics.ProcessStartInfo]::new()
    $startInfo.FileName = $FilePath
    foreach ($argument in $ArgumentList) {
        [void] $startInfo.ArgumentList.Add($argument)
    }
    $startInfo.WorkingDirectory = $WorkingDirectory
    $startInfo.RedirectStandardOutput = $true
    $startInfo.RedirectStandardError = $true
    $startInfo.RedirectStandardInput = $null -ne $InputText
    $startInfo.UseShellExecute = $false
    $process = [System.Diagnostics.Process]::Start($startInfo)
    if ($null -ne $InputText) {
        $process.StandardInput.Write($InputText)
        $process.StandardInput.Close()
    }
    $stdout = $process.StandardOutput.ReadToEnd()
    $stderr = $process.StandardError.ReadToEnd()
    $process.WaitForExit()
    if ($process.ExitCode -ne 0 -and -not $AllowFailure) {
        throw "命令失败：$FilePath $($ArgumentList -join ' ')`n退出码：$($process.ExitCode)`n$stderr`n$stdout"
    }
    return [pscustomobject]@{
        exitCode = $process.ExitCode
        stdout = $stdout
        stderr = $stderr
    }
}

function New-DemoManifest {
    param(
        [Parameter(Mandatory = $true)][string] $RunId,
        [Parameter(Mandatory = $true)][string] $OutputPath,
        [string] $GitCommit
    )
    $directory = Split-Path -Parent $OutputPath
    New-DemoDirectory -Path $directory | Out-Null
    $state = [ordered]@{
        runId = $RunId
        gitCommit = $GitCommit
        startedAt = (Get-Date).ToString("o")
        completedAt = $null
        objects = [ordered]@{}
        tasks = [ordered]@{}
        files = @()
        notes = @()
    }
    $manifestObject = [pscustomobject]@{
        State = $state
        OutputPath = $OutputPath
    }
    $manifestObject | Add-Member -MemberType ScriptMethod -Name AddObject -Value {
        param([string] $Type, [string] $Key, $Id)
        if (-not $this.State.objects.Contains($Type)) {
            $this.State.objects[$Type] = [ordered]@{}
        }
        $this.State.objects[$Type][$Key] = $Id
    }
    $manifestObject | Add-Member -MemberType ScriptMethod -Name AddTask -Value {
        param([string] $Key, $Id)
        $this.State.tasks[$Key] = $Id
    }
    $manifestObject | Add-Member -MemberType ScriptMethod -Name AddFile -Value {
        param($FileRecord)
        if ($FileRecord -is [string]) {
            $this.State.files += [ordered]@{ identifier = $FileRecord }
            return
        }
        $this.State.files += $FileRecord
    }
    $manifestObject | Add-Member -MemberType ScriptMethod -Name AddNote -Value {
        param([string] $Message)
        $this.State.notes += $Message
    }
    $manifestObject | Add-Member -MemberType ScriptMethod -Name Save -Value {
        $this.State.completedAt = (Get-Date).ToString("o")
        $this.State | ConvertTo-Json -Depth 20 | Set-Content -LiteralPath $this.OutputPath -Encoding UTF8
    }
    return $manifestObject
}

function New-DemoApiSession {
    param(
        [Parameter(Mandatory = $true)][string] $BaseUrl,
        [Parameter(Mandatory = $true)][string] $Username,
        [Parameter(Mandatory = $true)][string] $Password
    )
    $session = [Microsoft.PowerShell.Commands.WebRequestSession]::new()
    $csrfResponse = Invoke-RestMethod -Method Get -Uri "$($BaseUrl.TrimEnd('/'))/api/auth/csrf" -WebSession $session
    $csrf = $csrfResponse.data
    $loginBody = ConvertTo-DemoJson -Value ([ordered]@{ username = $Username; password = $Password })
    Invoke-RestMethod -Method Post -Uri "$($BaseUrl.TrimEnd('/'))/api/auth/login" -WebSession $session `
        -ContentType "application/json" -Headers @{ $csrf.headerName = $csrf.token } -Body $loginBody | Out-Null
    return [pscustomobject]@{
        baseUrl = $BaseUrl.TrimEnd("/")
        webSession = $session
        csrfHeaderName = $csrf.headerName
        csrfToken = $csrf.token
        username = $Username
    }
}

function Invoke-DemoApi {
    param(
        [Parameter(Mandatory = $true)] $Session,
        [Parameter(Mandatory = $true)][string] $Method,
        [Parameter(Mandatory = $true)][string] $Path,
        $Body = $null,
        [string] $ContentType = "application/json",
        [hashtable] $Headers = @{},
        [switch] $AllowFailure
    )
    $uri = "$($Session.baseUrl)$Path"
    $headers = @{} + $Headers
    $headers[$Session.csrfHeaderName] = $Session.csrfToken
    $parameters = @{
        Method = $Method
        Uri = $uri
        WebSession = $Session.webSession
        Headers = $headers
    }
    if ($null -ne $Body) {
        $parameters.ContentType = $ContentType
        $parameters.Body = if ($Body -is [string]) { $Body } else { ConvertTo-DemoJson -Value $Body }
    }
    try {
        $response = Invoke-RestMethod @parameters
        if ($response.success -ne $true) {
            throw "API 返回失败：$Method $Path code=$($response.code) message=$($response.message)"
        }
        return $response.data
    }
    catch {
        if ($AllowFailure) {
            return [pscustomobject]@{
                failed = $true
                message = $_.Exception.Message
            }
        }
        throw
    }
}

function New-DemoQueryString {
    param([hashtable] $Parameters)
    if ($null -eq $Parameters -or $Parameters.Count -eq 0) {
        return ""
    }
    $pairs = foreach ($key in ($Parameters.Keys | Sort-Object)) {
        $value = $Parameters[$key]
        if ($null -eq $value -or $value -eq "") {
            continue
        }
        if ($value -is [System.Collections.IEnumerable] -and -not ($value -is [string])) {
            foreach ($item in $value) {
                if ($null -ne $item -and $item -ne "") {
                    "{0}={1}" -f [uri]::EscapeDataString([string]$key), [uri]::EscapeDataString([string]$item)
                }
            }
        }
        else {
            "{0}={1}" -f [uri]::EscapeDataString([string]$key), [uri]::EscapeDataString([string]$value)
        }
    }
    $joined = @($pairs) -join "&"
    if ([string]::IsNullOrWhiteSpace($joined)) {
        return ""
    }
    return "?$joined"
}

function Invoke-DemoApiPage {
    param(
        [Parameter(Mandatory = $true)] $Session,
        [Parameter(Mandatory = $true)][string] $Path,
        [hashtable] $Parameters = @{}
    )
    $items = New-Object System.Collections.Generic.List[object]
    $page = 1
    $pageSize = 100
    while ($true) {
        $query = @{} + $Parameters
        $query["page"] = $page
        $query["pageSize"] = $pageSize
        $data = Invoke-DemoApi -Session $Session -Method Get -Path ($Path + (New-DemoQueryString -Parameters $query))
        foreach ($item in @($data.items)) {
            $items.Add($item)
        }
        if ($data.totalPages -le $page -or $data.items.Count -eq 0) {
            break
        }
        $page++
    }
    return $items.ToArray()
}

function Wait-DemoCondition {
    param(
        [Parameter(Mandatory = $true)][scriptblock] $Condition,
        [int] $TimeoutSeconds = 60,
        [int] $DelayMilliseconds = 1000,
        [string] $TimeoutMessage = "等待条件超时。"
    )
    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    do {
        $result = & $Condition
        if ($result) {
            return $result
        }
        Start-Sleep -Milliseconds $DelayMilliseconds
    } while ((Get-Date) -lt $deadline)
    throw $TimeoutMessage
}

function Get-DemoGitCommit {
    param([string] $Root)
    $result = Invoke-DemoProcess -FilePath "git" -ArgumentList @("rev-parse", "HEAD") -WorkingDirectory $Root
    return $result.stdout.Trim()
}

function Get-FileSha256 {
    param([Parameter(Mandatory = $true)][string] $Path)
    return (Get-FileHash -LiteralPath $Path -Algorithm SHA256).Hash.ToLowerInvariant()
}

function New-AcceptanceAuthorization {
    param(
        [Parameter(Mandatory = $true)][string] $Path,
        [Parameter(Mandatory = $true)][string] $Database,
        [Parameter(Mandatory = $true)][string] $MinioBucket,
        [Parameter(Mandatory = $true)][string] $ApiBaseUrl,
        [Parameter(Mandatory = $true)][string] $RunId,
        [Parameter(Mandatory = $true)][string] $RepositoryRoot,
        [Parameter(Mandatory = $true)][string] $ExpectedGitCommit
    )
    $token = [guid]::NewGuid().ToString("N")
    $authorization = [ordered]@{
        database = $Database
        minioBucket = $MinioBucket
        apiBaseUrl = $ApiBaseUrl
        runId = $RunId
        repositoryRoot = $RepositoryRoot
        expectedGitCommit = $ExpectedGitCommit
        token = $token
        issuedAt = (Get-Date).ToUniversalTime().ToString("o")
        expiresAt = (Get-Date).ToUniversalTime().AddMinutes(20).ToString("o")
    }
    New-DemoDirectory -Path (Split-Path -Parent $Path) | Out-Null
    $authorization | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath $Path -Encoding UTF8
    return $token
}

function Assert-DemoAcceptanceAuthorization {
    param(
        [Parameter(Mandatory = $true)][string] $Path,
        [Parameter(Mandatory = $true)][string] $Token,
        [Parameter(Mandatory = $true)][string] $Database,
        [Parameter(Mandatory = $true)][string] $MinioBucket,
        [Parameter(Mandatory = $true)][string] $ApiBaseUrl,
        [Parameter(Mandatory = $true)][string] $RunId
    )
    if (-not (Test-Path -LiteralPath $Path)) {
        throw "Acceptance 模式缺少重建入口创建的一次性授权材料。"
    }
    $authorization = Get-Content -LiteralPath $Path -Raw | ConvertFrom-Json
    try {
        if ($authorization.token -ne $Token) {
            throw "授权令牌不匹配。"
        }
        if ($authorization.database -ne $Database -or $authorization.minioBucket -ne $MinioBucket `
                -or $authorization.apiBaseUrl -ne $ApiBaseUrl -or $authorization.runId -ne $RunId) {
            throw "授权材料与当前目标不一致。"
        }
        if ([datetime]::Parse($authorization.expiresAt).ToUniversalTime() -lt (Get-Date).ToUniversalTime()) {
            throw "授权材料已过期。"
        }
    }
    finally {
        Remove-Item -LiteralPath $Path -Force -ErrorAction SilentlyContinue
    }
}
