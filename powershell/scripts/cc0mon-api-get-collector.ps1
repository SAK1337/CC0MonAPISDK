<#
.SYNOPSIS
    Fetch a wallet's cc0mon collection checklist.

.PARAMETER Address
    Ethereum 0x-prefixed address (42 chars). ENS not supported in v0.1.

.EXAMPLE
    .\cc0mon-api-get-collector.ps1 -Address 0xabcdef...0123
#>
[CmdletBinding()]
param(
    [Parameter(Mandatory)] [string] $Address
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

Import-Module (Join-Path $PSScriptRoot '..\CC0MonHelpers.psm1') -Force

$action = 'get-collector'
Write-Cc0Log -Action $action -Level INFO -Message "script start address=$Address"

if (-not (Test-Cc0Address -Address $Address)) {
    $msg = "validation: address must match 0x[0-9a-fA-F]{40}, got $Address"
    Write-Cc0Log -Action $action -Level ERROR -Message "exit=5 $msg"
    Write-Error $msg
    exit 5
}

try {
    $collector = Invoke-Cc0Request -Action $action -Path "/collector/$($Address.ToLowerInvariant())"
    $collector | ConvertTo-Json -Depth 32
    $progressCount = if ($collector.progress) { @($collector.progress.PSObject.Properties).Count } else { 0 }
    $itemsCount = if ($collector.items) { @($collector.items).Count } elseif ($collector.checklist) { @($collector.checklist).Count } elseif ($collector.registry) { @($collector.registry).Count } else { 0 }
    Write-Cc0Log -Action $action -Level INFO -Message "script exit code=0 progress_keys=$progressCount items=$itemsCount"
    exit 0
} catch {
    $code = ConvertTo-Cc0ExitCode -ErrorRecord $_
    Write-Cc0Log -Action $action -Level ERROR -Message "exit=$code msg=$($_.Exception.Message)"
    Write-Error $_.Exception.Message
    exit $code
}
