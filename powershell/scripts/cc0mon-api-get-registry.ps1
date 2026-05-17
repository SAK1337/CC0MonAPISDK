<#
.SYNOPSIS
    List cc0mon species (registry), optionally filtered.

.PARAMETER Limit
    Show only the first N entries.

.PARAMETER Energy
    Filter by energy type (case-insensitive). One of:
    Bug, Celestial, Dragon, Earth, Electric, Fire, Fossil, Ghost, Grass,
    Ice, Metal, Mythic, Ocean, Rock, Toxic, Underworld.

.PARAMETER Rarity
    Filter by rarity tier (case-insensitive). One of: Common, Uncommon, Rare, Legendary.

.PARAMETER NameContains
    Case-insensitive substring match on species name.

.EXAMPLE
    .\cc0mon-api-get-registry.ps1
    .\cc0mon-api-get-registry.ps1 -Energy Fire -Rarity Common
    .\cc0mon-api-get-registry.ps1 -NameContains drill
#>
[CmdletBinding()]
param(
    [int] $Limit = 0,
    [string] $Energy,
    [string] $Rarity,
    [string] $NameContains
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

Import-Module (Join-Path $PSScriptRoot '..\CC0MonHelpers.psm1') -Force

$action = 'get-registry'
Write-Cc0Log -Action $action -Level INFO -Message "script start limit=$Limit energy=$Energy rarity=$Rarity name_contains=$NameContains"

try {
    $canonEnergy = $null; $canonRarity = $null
    if ($Energy)  { $canonEnergy = Test-Cc0Energy -Value $Energy }
    if ($Rarity)  { $canonRarity = Test-Cc0Rarity -Value $Rarity }

    $resp = Invoke-Cc0Request -Action $action -Path "/registry"
    $items = if ($resp -is [System.Array]) { $resp } `
             elseif ($resp.PSObject.Properties.Name -contains 'cc0mon') { $resp.cc0mon } `
             elseif ($resp.PSObject.Properties.Name -contains 'species') { $resp.species } `
             elseif ($resp.PSObject.Properties.Name -contains 'items') { $resp.items } `
             else { @($resp) }

    $filtered = $items
    if ($canonEnergy) { $filtered = $filtered | Where-Object { $_.energy -eq $canonEnergy } }
    if ($canonRarity) { $filtered = $filtered | Where-Object { $_.rarity -eq $canonRarity } }
    if ($NameContains) {
        $needle = $NameContains.ToLowerInvariant()
        $filtered = $filtered | Where-Object { $_.name -and $_.name.ToLowerInvariant().Contains($needle) }
    }
    $filtered = @($filtered)
    $shown = if ($Limit -gt 0) { $filtered | Select-Object -First $Limit } else { $filtered }
    $shown = @($shown)
    $shown | ConvertTo-Json -Depth 32 -AsArray
    Write-Cc0Log -Action $action -Level INFO -Message "script exit code=0 matched=$($filtered.Count) shown=$($shown.Count)"
    exit 0
} catch {
    $code = ConvertTo-Cc0ExitCode -ErrorRecord $_
    Write-Cc0Log -Action $action -Level ERROR -Message "exit=$code msg=$($_.Exception.Message)"
    [Console]::Error.WriteLine($_.Exception.Message)
    exit $code
}
