<#
.SYNOPSIS
    Shared helpers for the cc0mon PowerShell example scripts.

.DESCRIPTION
    Exports two functions:
      - Write-Cc0Log:      structured append-only logging to ./cc0mon-api-<action>.log
      - Invoke-Cc0Request: HTTP GET with retry/backoff for 429 and 5xx, honoring Retry-After

    Honors the env var $env:CC0MON_LOG_LEVEL (DEBUG/INFO/WARNING/ERROR; default INFO).
#>

$script:LevelOrder = @{ DEBUG = 0; INFO = 1; WARNING = 2; ERROR = 3 }

function Get-Cc0LogLevel {
    $raw = [Environment]::GetEnvironmentVariable('CC0MON_LOG_LEVEL')
    if (-not $raw) { return 'INFO' }
    $upper = $raw.ToUpperInvariant()
    if ($script:LevelOrder.ContainsKey($upper)) { return $upper } else { return 'INFO' }
}

function Write-Cc0Log {
    [CmdletBinding()]
    param(
        [Parameter(Mandatory)] [string] $Action,
        [Parameter(Mandatory)] [ValidateSet('DEBUG', 'INFO', 'WARNING', 'ERROR')] [string] $Level,
        [Parameter(Mandatory)] [string] $Message
    )
    $configured = Get-Cc0LogLevel
    if ($script:LevelOrder[$Level] -lt $script:LevelOrder[$configured]) { return }

    $logPath = Join-Path (Get-Location).Path "cc0mon-api-$Action.log"
    $ts = [DateTime]::UtcNow.ToString('yyyy-MM-ddTHH:mm:ss.fffZ')
    $line = "$ts | $Level | $Action | $Message"
    Add-Content -Path $logPath -Value $line -Encoding UTF8
}

function Invoke-Cc0Request {
    <#
    .SYNOPSIS
        Send an HTTP GET to the cc0mon API with retry/backoff and structured logging.

    .OUTPUTS
        For -ReturnRaw, returns the raw byte array.
        Otherwise, returns the parsed JSON object (PSCustomObject).
    #>
    [CmdletBinding()]
    param(
        [Parameter(Mandatory)] [string] $Action,
        [Parameter(Mandatory)] [string] $Path,
        [string] $BaseUrl = 'https://api.cc0mon.com',
        [int] $Retries = 3,
        [int] $TimeoutSec = 30,
        [string] $Accept = 'application/json',
        [switch] $ReturnRaw
    )

    $url = "$BaseUrl$Path"
    $attempt = 0
    $retryStatuses = @(429, 500, 502, 503, 504)

    while ($true) {
        $stopwatch = [System.Diagnostics.Stopwatch]::StartNew()
        try {
            $headers = @{ 'Accept' = $Accept; 'User-Agent' = "cc0mon-sdk-powershell/0.1.0" }
            $response = Invoke-WebRequest -Uri $url -Method GET -Headers $headers `
                -TimeoutSec $TimeoutSec -SkipHttpErrorCheck -ErrorAction Stop
        } catch {
            $stopwatch.Stop()
            Write-Cc0Log -Action $Action -Level ERROR `
                -Message "GET $url | status=- | latency_ms=$($stopwatch.ElapsedMilliseconds) | bytes=0 | network: $($_.Exception.Message)"
            if ($attempt -ge $Retries) {
                throw [System.IO.IOException]::new("network error after $($Retries + 1) attempts: $($_.Exception.Message)", $_.Exception)
            }
            Invoke-Cc0SleepForRetry -Attempt $attempt -RetryAfter $null -Action $Action
            $attempt++
            continue
        }
        $stopwatch.Stop()

        $status = [int]$response.StatusCode
        $bytes = if ($response.RawContentLength) { $response.RawContentLength } elseif ($response.Content) { $response.Content.Length } else { 0 }
        $message = if ($status -lt 400) { 'ok' } else { 'http error' }
        Write-Cc0Log -Action $Action -Level INFO `
            -Message "GET $url | status=$status | latency_ms=$($stopwatch.ElapsedMilliseconds) | bytes=$bytes | $message"

        if ($status -lt 400) {
            if ($ReturnRaw) { return $response.Content }
            return ($response.Content | ConvertFrom-Json -Depth 32)
        }

        if ($retryStatuses -contains $status -and $attempt -lt $Retries) {
            $retryAfter = $null
            if ($response.Headers['Retry-After']) {
                [double]$parsed = 0
                if ([double]::TryParse($response.Headers['Retry-After'], [ref]$parsed)) { $retryAfter = $parsed }
            }
            Invoke-Cc0SleepForRetry -Attempt $attempt -RetryAfter $retryAfter -Action $Action
            $attempt++
            continue
        }

        $body = if ($response.Content -is [byte[]]) { [System.Text.Encoding]::UTF8.GetString($response.Content) } else { [string]$response.Content }
        $err = [PSCustomObject]@{
            StatusCode = $status
            Body = $body
            Category = if ($status -ge 500) { 'server' } elseif ($status -eq 429) { 'rate-limit' } else { 'client' }
        }
        throw [System.Management.Automation.RuntimeException]::new("HTTP $status :: $body :: $($err.Category)")
    }
}

function Invoke-Cc0SleepForRetry {
    param([int] $Attempt, [Nullable[double]] $RetryAfter, [string] $Action)
    if ($null -ne $RetryAfter) {
        $sleepFor = [double]$RetryAfter
    } else {
        $cap = 30.0
        $base = 1.0
        $delay = [Math]::Min($cap, $base * [Math]::Pow(2, $Attempt))
        $sleepFor = Get-Random -Minimum 0.0 -Maximum $delay
    }
    Write-Cc0Log -Action $Action -Level INFO -Message ("retry attempt={0} sleeping={1:F2}s" -f ($Attempt + 1), $sleepFor)
    Start-Sleep -Seconds $sleepFor
}

function Test-Cc0Address {
    param([Parameter(Mandatory)] [string] $Address)
    return ($Address -match '^0x[0-9a-fA-F]{40}$')
}

function ConvertTo-Cc0ExitCode {
    <#
    .SYNOPSIS
        Translate a thrown exception message into a spec exit code.
    .OUTPUTS
        0=ok, 1=unexpected, 2=network, 3=4xx, 4=5xx, 5=validation
    #>
    param([Parameter(Mandatory)] $ErrorRecord)
    $msg = $ErrorRecord.Exception.Message
    if ($msg -match 'HTTP (\d{3})') {
        $code = [int]$Matches[1]
        if ($code -ge 500) { return 4 }
        if ($code -ge 400) { return 3 }
    }
    if ($ErrorRecord.Exception -is [System.IO.IOException]) { return 2 }
    if ($msg -match 'validation') { return 5 }
    return 1
}

Export-ModuleMember -Function Write-Cc0Log, Invoke-Cc0Request, Test-Cc0Address, ConvertTo-Cc0ExitCode, Get-Cc0LogLevel
