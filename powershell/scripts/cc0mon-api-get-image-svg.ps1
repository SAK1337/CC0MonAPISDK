<#
.SYNOPSIS
    Download the SVG artwork for a cc0mon token.

.PARAMETER Id
    Token id, 1..10000.

.PARAMETER Out
    Output path. Default: ./cc0mon-<id>.svg in the current directory.

.EXAMPLE
    .\cc0mon-api-get-image-svg.ps1 -Id 1
    .\cc0mon-api-get-image-svg.ps1 -Id 1 -Out .\my-mon.svg
#>
[CmdletBinding()]
param(
    [Parameter(Mandatory)] [ValidateRange(1, 10000)] [int] $Id,
    [string] $Out
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

Import-Module (Join-Path $PSScriptRoot '..\CC0MonHelpers.psm1') -Force

$action = 'get-image-svg'
if (-not $Out) { $Out = Join-Path (Get-Location).Path "cc0mon-$Id.svg" }
Write-Cc0Log -Action $action -Level INFO -Message "script start id=$Id out=$Out"

try {
    $bytes = Invoke-Cc0Request -Action $action -Path "/cc0mon/$Id/image.svg" -Accept 'image/svg+xml' -ReturnRaw
    [System.IO.File]::WriteAllBytes($Out, $bytes)
    (Resolve-Path $Out).Path
    Write-Cc0Log -Action $action -Level INFO -Message "script exit code=0 bytes=$($bytes.Length) path=$Out"
    exit 0
} catch {
    $code = ConvertTo-Cc0ExitCode -ErrorRecord $_
    Write-Cc0Log -Action $action -Level ERROR -Message "exit=$code msg=$($_.Exception.Message)"
    [Console]::Error.WriteLine($_.Exception.Message)
    exit $code
}
