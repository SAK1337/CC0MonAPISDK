<#
.SYNOPSIS
    Fetch decoded traits for a cc0mon token.

.PARAMETER Id
    Token id, 1..10000.

.EXAMPLE
    .\cc0mon-api-get-traits.ps1 -Id 1
#>
[CmdletBinding()]
param(
    [Parameter(Mandatory)] [ValidateRange(1, 10000)] [int] $Id
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

Import-Module (Join-Path $PSScriptRoot '..\CC0MonHelpers.psm1') -Force

$action = 'get-traits'
Write-Cc0Log -Action $action -Level INFO -Message "script start id=$Id"

try {
    $traits = Invoke-Cc0Request -Action $action -Path "/cc0mon/$Id/traits"
    $traits | ConvertTo-Json -Depth 32
    $attrCount = if ($traits.attributes) { @($traits.attributes).Count } else { 0 }
    Write-Cc0Log -Action $action -Level INFO -Message "script exit code=0 attributes=$attrCount"
    exit 0
} catch {
    $code = ConvertTo-Cc0ExitCode -ErrorRecord $_
    Write-Cc0Log -Action $action -Level ERROR -Message "exit=$code msg=$($_.Exception.Message)"
    [Console]::Error.WriteLine($_.Exception.Message)
    exit $code
}
