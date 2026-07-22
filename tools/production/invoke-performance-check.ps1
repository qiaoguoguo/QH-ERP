[CmdletBinding()]
param(
    [string] $BaseUrl = "http://127.0.0.1:45173",
    [string] $PeriodCode = "2026-07",
    [string] $OutputPath
)

$ErrorActionPreference = "Stop"
Set-StrictMode -Version Latest
. (Join-Path $PSScriptRoot "lib\production-common.ps1")

if (-not ("QherpHttpLoad" -as [type])) {
    Add-Type -TypeDefinition @'
using System;
using System.Collections.Generic;
using System.Diagnostics;
using System.Net.Http;
using System.Threading;
using System.Threading.Tasks;

public sealed class QherpHttpSample {
    public int StatusCode { get; set; }
    public double DurationMs { get; set; }
}

public static class QherpHttpLoad {
    public static async Task<QherpHttpSample[]> Run(HttpClient client, string url, int count, int concurrency) {
        var samples = new QherpHttpSample[count];
        var next = -1;
        var workers = new List<Task>();
        for (var worker = 0; worker < concurrency; worker++) {
            workers.Add(Task.Run(async () => {
                while (true) {
                    var index = Interlocked.Increment(ref next);
                    if (index >= count) return;
                    var watch = Stopwatch.StartNew();
                    var status = 0;
                    try {
                        using var response = await client.GetAsync(url).ConfigureAwait(false);
                        status = (int)response.StatusCode;
                        await response.Content.ReadAsByteArrayAsync().ConfigureAwait(false);
                    }
                    catch {
                        status = 0;
                    }
                    watch.Stop();
                    samples[index] = new QherpHttpSample { StatusCode = status, DurationMs = watch.Elapsed.TotalMilliseconds };
                }
            }));
        }
        await Task.WhenAll(workers).ConfigureAwait(false);
        return samples;
    }
}
'@
}

function New-PerformanceClient {
    $handler = [Net.Http.HttpClientHandler]::new()
    $handler.UseCookies = $true
    $handler.CookieContainer = [Net.CookieContainer]::new()
    $client = [Net.Http.HttpClient]::new($handler, $true)
    $client.Timeout = [TimeSpan]::FromSeconds(30)
    return $client
}

function Get-CsrfContext {
    param([Parameter(Mandatory)][Net.Http.HttpClient] $Client)

    $response = $Client.GetAsync("$BaseUrl/api/auth/csrf").GetAwaiter().GetResult()
    try {
        if (-not $response.IsSuccessStatusCode) {
            throw "CSRF 获取失败：$([int]$response.StatusCode)"
        }
        $content = $response.Content.ReadAsStringAsync().GetAwaiter().GetResult() | ConvertFrom-Json
        return [pscustomobject]@{
            HeaderName = [string]$content.data.headerName
            Token = [string]$content.data.token
        }
    }
    finally {
        $response.Dispose()
    }
}

function Start-LoginRequest {
    param(
        [Parameter(Mandatory)][Net.Http.HttpClient] $Client,
        [Parameter(Mandatory)] $Csrf,
        [Parameter(Mandatory)][string] $Password
    )

    $request = [Net.Http.HttpRequestMessage]::new([Net.Http.HttpMethod]::Post, "$BaseUrl/api/auth/login")
    [void]$request.Headers.TryAddWithoutValidation([string]$Csrf.HeaderName, [string]$Csrf.Token)
    $json = @{ username = "admin"; password = $Password } | ConvertTo-Json -Compress
    $request.Content = [Net.Http.StringContent]::new($json, [Text.Encoding]::UTF8, "application/json")
    return [pscustomobject]@{
        Request = $request
        Watch = [Diagnostics.Stopwatch]::StartNew()
        Task = $Client.SendAsync($request)
    }
}

function Complete-LoginRequest {
    param([Parameter(Mandatory)] $Pending)

    $status = 0
    try {
        $response = $Pending.Task.GetAwaiter().GetResult()
        try {
            $status = [int]$response.StatusCode
            [void]$response.Content.ReadAsByteArrayAsync().GetAwaiter().GetResult()
        }
        finally {
            $response.Dispose()
        }
    }
    catch {
        $status = 0
    }
    finally {
        $Pending.Watch.Stop()
        $Pending.Request.Dispose()
    }
    return [pscustomobject]@{ StatusCode = $status; DurationMs = $Pending.Watch.Elapsed.TotalMilliseconds }
}

function Invoke-LoginAttempt {
    param([Parameter(Mandatory)][string] $Password)

    $client = New-PerformanceClient
    $watch = [Diagnostics.Stopwatch]::StartNew()
    try {
        $csrf = Get-CsrfContext -Client $client
        $pending = Start-LoginRequest -Client $client -Csrf $csrf -Password $Password
        $sample = Complete-LoginRequest -Pending $pending
        $watch.Stop()
        $sample.DurationMs = $watch.Elapsed.TotalMilliseconds
        return [pscustomobject]@{ Client = $client; Sample = $sample }
    }
    catch {
        $watch.Stop()
        $client.Dispose()
        return [pscustomobject]@{
            Client = $null
            Sample = [pscustomobject]@{ StatusCode = 0; DurationMs = $watch.Elapsed.TotalMilliseconds }
        }
    }
}

$secrets = Import-QherpProductionSecrets
$adminPassword = ConvertFrom-QherpSecureString -Value $secrets.InitialAdminPassword
$readClient = $null
$clientsToDispose = [Collections.Generic.List[Net.Http.HttpClient]]::new()
try {
    $healthClient = New-PerformanceClient
    $clientsToDispose.Add($healthClient)
    for ($index = 0; $index -lt 5; $index++) {
        [void]$healthClient.GetAsync("$BaseUrl/api/health").GetAwaiter().GetResult()
    }
    $healthSamples = [QherpHttpLoad]::Run($healthClient, "$BaseUrl/api/health", 1000, 20).GetAwaiter().GetResult()
    $healthSummary = New-QherpHttpSummary -Name "健康接口" -Samples $healthSamples -ExpectedStatusCodes @(200)

    $validLoginSamples = [Collections.Generic.List[object]]::new()
    for ($index = 0; $index -lt 20; $index++) {
        $attempt = Invoke-LoginAttempt -Password $adminPassword
        $validLoginSamples.Add($attempt.Sample)
        if ($index -eq 0 -and $attempt.Sample.StatusCode -eq 200) {
            $readClient = $attempt.Client
            $clientsToDispose.Add($readClient)
        }
        elseif ($null -ne $attempt.Client) {
            $attempt.Client.Dispose()
        }
    }
    $loginSummary = New-QherpHttpSummary -Name "有效登录" -Samples @($validLoginSamples) -ExpectedStatusCodes @(200)
    if ($null -eq $readClient) {
        throw "无法建立认证只读性能会话。"
    }

    Start-Sleep -Seconds 3
    $invalidContexts = [Collections.Generic.List[object]]::new()
    for ($index = 0; $index -lt 60; $index++) {
        $client = New-PerformanceClient
        $clientsToDispose.Add($client)
        $invalidContexts.Add([pscustomobject]@{ Client = $client; Csrf = Get-CsrfContext -Client $client })
    }
    $invalidPending = [Collections.Generic.List[object]]::new()
    foreach ($context in $invalidContexts) {
        $invalidPending.Add((Start-LoginRequest -Client $context.Client -Csrf $context.Csrf `
            -Password "invalid-$([guid]::NewGuid().ToString('N'))"))
    }
    $invalidSamples = [Collections.Generic.List[object]]::new()
    foreach ($pending in $invalidPending) {
        $invalidSamples.Add((Complete-LoginRequest -Pending $pending))
    }
    $invalidSummary = New-QherpHttpSummary -Name "无效登录突发" -Samples @($invalidSamples) `
        -ExpectedStatusCodes @(401, 429)
    $invalidMeStatuses = [Collections.Generic.List[int]]::new()
    foreach ($context in $invalidContexts) {
        $response = $context.Client.GetAsync("$BaseUrl/api/auth/me").GetAwaiter().GetResult()
        try {
            $invalidMeStatuses.Add([int]$response.StatusCode)
        }
        finally {
            $response.Dispose()
        }
    }
    $rateLimitedCount = @($invalidSamples | Where-Object { $_.StatusCode -eq 429 }).Count
    $invalidAuthenticatedCount = @($invalidMeStatuses | Where-Object { $_ -ne 401 }).Count

    Start-Sleep -Seconds 3
    $postBurstAttempt = Invoke-LoginAttempt -Password $adminPassword
    $postBurstValidLoginStatus = [int]$postBurstAttempt.Sample.StatusCode
    if ($null -ne $postBurstAttempt.Client) {
        $postBurstAttempt.Client.Dispose()
    }

    for ($index = 0; $index -lt 5; $index++) {
        [void]$readClient.GetAsync("$BaseUrl/api/auth/me").GetAwaiter().GetResult()
    }
    $readSamples = [QherpHttpLoad]::Run($readClient, "$BaseUrl/api/auth/me", 1000, 20).GetAwaiter().GetResult()
    $readSummary = New-QherpHttpSummary -Name "认证只读接口" -Samples $readSamples -ExpectedStatusCodes @(200)

    $reportUrl = "$BaseUrl/api/admin/reports/operating-finance-overview?periodCode=$([uri]::EscapeDataString($PeriodCode))&analysisMode=LIVE"
    for ($index = 0; $index -lt 3; $index++) {
        [void]$readClient.GetAsync($reportUrl).GetAwaiter().GetResult()
    }
    $reportSamples = [QherpHttpLoad]::Run($readClient, $reportUrl, 300, 10).GetAwaiter().GetResult()
    $reportSummary = New-QherpHttpSummary -Name "固定经营报表" -Samples $reportSamples -ExpectedStatusCodes @(200)

    $healthPassed = Test-QherpPerformanceThreshold -Summary $healthSummary `
        -MaxP95Ms 250 -MaxUnexpectedRate 0 -RequireZeroServerErrors
    $loginPassed = Test-QherpPerformanceThreshold -Summary $loginSummary `
        -MaxP95Ms 1500 -MaxUnexpectedRate 0 -RequireZeroServerErrors
    $readPassed = Test-QherpPerformanceThreshold -Summary $readSummary `
        -MaxP95Ms 1000 -MaxUnexpectedRate 0.001 -RequireZeroServerErrors
    $reportPassed = Test-QherpPerformanceThreshold -Summary $reportSummary `
        -MaxP95Ms 3000 -MaxUnexpectedRate 0 -RequireZeroServerErrors
    $invalidPassed = $invalidSummary.UnexpectedCount -eq 0 -and $invalidSummary.ServerErrorCount -eq 0 -and
        $rateLimitedCount -gt 0 -and $invalidAuthenticatedCount -eq 0 -and $postBurstValidLoginStatus -eq 200

    $report = [ordered]@{
        SchemaVersion = 1
        CheckedAtUtc = [DateTimeOffset]::UtcNow.ToString("o")
        SourceCommit = Get-QherpSourceCommit
        BaseUrl = $BaseUrl
        Scope = "本机约 30MB 候选数据与合成并发；不外推为企业生产容量承诺"
        Health = $healthSummary
        ValidLogin = $loginSummary
        InvalidLoginBurst = [ordered]@{
            Summary = $invalidSummary
            RateLimitedCount = $rateLimitedCount
            AuthenticatedAfterInvalidCount = $invalidAuthenticatedCount
            PostBurstValidLoginStatus = $postBurstValidLoginStatus
            Passed = $invalidPassed
        }
        AuthenticatedRead = $readSummary
        FixedReport = $reportSummary
        Thresholds = [ordered]@{
            HealthPassed = $healthPassed
            ValidLoginPassed = $loginPassed
            InvalidLoginPassed = $invalidPassed
            AuthenticatedReadPassed = $readPassed
            FixedReportPassed = $reportPassed
        }
        Passed = $healthPassed -and $loginPassed -and $invalidPassed -and $readPassed -and $reportPassed
    }
    if ([string]::IsNullOrWhiteSpace($OutputPath)) {
        $OutputPath = Join-Path $env:USERPROFILE ".codex\backups\qherp\035-performance-$((Get-Date).ToUniversalTime().ToString('yyyyMMdd-HHmmss')).json"
    }
    $OutputPath = [IO.Path]::GetFullPath($OutputPath)
    New-Item -ItemType Directory -Path (Split-Path -Parent $OutputPath) -Force | Out-Null
    $report | ConvertTo-Json -Depth 12 | Set-Content -LiteralPath $OutputPath -Encoding utf8

    if (-not $report.Passed) {
        throw "性能验证未满足冻结阈值；报告：$OutputPath"
    }
    Write-Host "性能验证通过；报告：$OutputPath" -ForegroundColor Green
    Write-Host "P95(ms)：健康 $($healthSummary.P95Ms)，登录 $($loginSummary.P95Ms)，认证读 $($readSummary.P95Ms)，固定报表 $($reportSummary.P95Ms)；限流 $rateLimitedCount/60。"
}
finally {
    $adminPassword = $null
    foreach ($client in $clientsToDispose) {
        $client.Dispose()
    }
}
