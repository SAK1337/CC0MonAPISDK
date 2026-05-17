<#
.SYNOPSIS
    Fetch and optionally filter a wallet's cc0mon collection checklist.

.PARAMETER Address
    Ethereum 0x-prefixed address (42 chars). ENS not supported.

.PARAMETER OwnedOnly
    Switch: filter checklist to entries with collected=true.

.PARAMETER Energy
    Filter checklist by energy (case-insensitive, must be a valid value).

.PARAMETER Rarity
    Filter checklist by rarity (case-insensitive, must be a valid value).

.EXAMPLE
    .\cc0mon-api-get-collector.ps1 -Address 0xB07952A55bF9c45C268F37C3631823Df50ac721a
    .\cc0mon-api-get-collector.ps1 -Address 0x... -OwnedOnly -Energy Fire
#>
[CmdletBinding()]
param(
    [Parameter(Mandatory)] [string] $Address,
    [switch] $OwnedOnly,
    [string] $Energy,
    [string] $Rarity
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

Import-Module (Join-Path $PSScriptRoot '..\CC0MonHelpers.psm1') -Force

$action = 'get-collector'
Write-Cc0Log -Action $action -Level INFO -Message "script start address=$Address owned_only=$($OwnedOnly.IsPresent) energy=$Energy rarity=$Rarity"

$hasFilter = $OwnedOnly.IsPresent -or $Energy -or $Rarity

try {
    Assert-Cc0Address -Address $Address
    $canonEnergy = $null; $canonRarity = $null
    if ($Energy) { $canonEnergy = Test-Cc0Energy -Value $Energy }
    if ($Rarity) { $canonRarity = Test-Cc0Rarity -Value $Rarity }

    $collector = Invoke-Cc0Request -Action $action -Path "/collector/$($Address.ToLowerInvariant())"

    if (-not $hasFilter) {
        $collector | ConvertTo-Json -Depth 32
        $collected = if ($collector.PSObject.Properties.Name -contains 'collected') { $collector.collected } else { 0 }
        $missing = if ($collector.PSObject.Properties.Name -contains 'missing') { $collector.missing } else { 0 }
        $heldKey = if ($collector.PSObject.Properties.Name -contains 'totalTokensHeld') { 'totalTokensHeld' } else { 'total_tokens_held' }
        $held = if ($collector.PSObject.Properties.Name -contains $heldKey) { $collector.$heldKey } else { 0 }
        Write-Cc0Log -Action $action -Level INFO -Message "script exit code=0 collected=$collected missing=$missing total_tokens_held=$held"
        exit 0
    }

    $checklist = if ($collector.PSObject.Properties.Name -contains 'checklist') { $collector.checklist } `
                 elseif ($collector.PSObject.Properties.Name -contains 'items') { $collector.items } `
                 elseif ($collector.PSObject.Properties.Name -contains 'registry') { $collector.registry } `
                 else { @() }
    $checklist = @($checklist)

    $filtered = $checklist
    if ($OwnedOnly.IsPresent) { $filtered = $filtered | Where-Object { $_.collected -eq $true } }
    if ($canonEnergy)         { $filtered = $filtered | Where-Object { $_.energy -eq $canonEnergy } }
    if ($canonRarity)         { $filtered = $filtered | Where-Object { $_.rarity -eq $canonRarity } }
    $filtered = @($filtered)

    $filtered | ConvertTo-Json -Depth 32 -AsArray
    Write-Cc0Log -Action $action -Level INFO -Message "script exit code=0 matched=$($filtered.Count)"
    exit 0
} catch {
    $code = ConvertTo-Cc0ExitCode -ErrorRecord $_
    Write-Cc0Log -Action $action -Level ERROR -Message "exit=$code msg=$($_.Exception.Message)"
    [Console]::Error.WriteLine($_.Exception.Message)
    exit $code
}
