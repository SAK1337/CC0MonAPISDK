<#
.SYNOPSIS
    List cc0mon species (full registry).

.PARAMETER Limit
    Show only the first N entries.

.EXAMPLE
    .\cc0mon-api-get-registry.ps1
    .\cc0mon-api-get-registry.ps1 -Limit 5
#>
[CmdletBinding()]
param(
    [int] $Limit = 0
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

Import-Module (Join-Path $PSScriptRoot '..\CC0MonHelpers.psm1') -Force

$action = 'get-registry'
Write-Cc0Log -Action $action -Level INFO -Message "script start limit=$Limit"

try {
    $species = Invoke-Cc0Request -Action $action -Path "/registry"
    $items = if ($species -is [System.Array]) { $species } elseif ($species.species) { $species.species } elseif ($species.items) { $species.items } else { @($species) }
    $shown = if ($Limit -gt 0) { $items | Select-Object -First $Limit } else { $items }
    $shown | ConvertTo-Json -Depth 32
    Write-Cc0Log -Action $action -Level INFO -Message "script exit code=0 total=$(@($items).Count) shown=$(@($shown).Count)"
    exit 0
} catch {
    $code = ConvertTo-Cc0ExitCode -ErrorRecord $_
    Write-Cc0Log -Action $action -Level ERROR -Message "exit=$code msg=$($_.Exception.Message)"
    Write-Error $_.Exception.Message
    exit $code
}
