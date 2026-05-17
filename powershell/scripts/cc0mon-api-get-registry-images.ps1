<#
.SYNOPSIS
    List cc0mon species image URLs, optionally filtered.

.PARAMETER Limit
    Show only the first N entries.

.PARAMETER NameContains
    Case-insensitive substring match on species name.

.PARAMETER HasImage
    Skip entries with tokenId=null (unmapped species).

.EXAMPLE
    .\cc0mon-api-get-registry-images.ps1
    .\cc0mon-api-get-registry-images.ps1 -NameContains stryx -HasImage
#>
[CmdletBinding()]
param(
    [int] $Limit = 0,
    [string] $NameContains,
    [switch] $HasImage
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

Import-Module (Join-Path $PSScriptRoot '..\CC0MonHelpers.psm1') -Force

$action = 'get-registry-images'
Write-Cc0Log -Action $action -Level INFO -Message "script start limit=$Limit name_contains=$NameContains has_image=$HasImage"

try {
    $resp = Invoke-Cc0Request -Action $action -Path "/registry/images"

    # Real API shape: { total, mapped, images: { "1": {...}, "2": {...}, ... } }
    $items = @()
    if ($resp -is [System.Array]) {
        $items = $resp
    } elseif ($resp.PSObject.Properties.Name -contains 'images') {
        $imagesNode = $resp.images
        if ($imagesNode -is [System.Array]) {
            $items = $imagesNode
        } else {
            # Object keyed by species number string — flatten to a list of values.
            $items = $imagesNode.PSObject.Properties | ForEach-Object { $_.Value }
        }
    } elseif ($resp.PSObject.Properties.Name -contains 'species') {
        $items = $resp.species
    } elseif ($resp.PSObject.Properties.Name -contains 'items') {
        $items = $resp.items
    }
    $items = @($items)

    $filtered = $items
    if ($HasImage) { $filtered = $filtered | Where-Object { $null -ne $_.tokenId } }
    if ($NameContains) {
        $needle = $NameContains.ToLowerInvariant()
        $filtered = $filtered | Where-Object { $_.name -and $_.name.ToLowerInvariant().Contains($needle) }
    }
    $filtered = @($filtered)
    $shown = if ($Limit -gt 0) { $filtered | Select-Object -First $Limit } else { $filtered }
    $shown = @($shown)
    $shown | ConvertTo-Json -Depth 32 -AsArray
    Write-Cc0Log -Action $action -Level INFO -Message "script exit code=0 total=$($items.Count) matched=$($filtered.Count) shown=$($shown.Count)"
    exit 0
} catch {
    $code = ConvertTo-Cc0ExitCode -ErrorRecord $_
    Write-Cc0Log -Action $action -Level ERROR -Message "exit=$code msg=$($_.Exception.Message)"
    [Console]::Error.WriteLine($_.Exception.Message)
    exit $code
}
