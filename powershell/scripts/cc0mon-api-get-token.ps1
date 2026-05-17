<#
.SYNOPSIS
    Fetch the summary for a single cc0mon token.

.PARAMETER Id
    Token id, 1..10000.

.EXAMPLE
    .\cc0mon-api-get-token.ps1 -Id 1
#>
[CmdletBinding()]
param(
    [Parameter(Mandatory)] [ValidateRange(1, 10000)] [int] $Id
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

Import-Module (Join-Path $PSScriptRoot '..\CC0MonHelpers.psm1') -Force

$action = 'get-token'
Write-Cc0Log -Action $action -Level INFO -Message "script start id=$Id"

try {
    $token = Invoke-Cc0Request -Action $action -Path "/cc0mon/$Id"
    $token | ConvertTo-Json -Depth 32
    Write-Cc0Log -Action $action -Level INFO -Message "script exit code=0"
    exit 0
} catch {
    $code = ConvertTo-Cc0ExitCode -ErrorRecord $_
    Write-Cc0Log -Action $action -Level ERROR -Message "exit=$code msg=$($_.Exception.Message)"
    Write-Error $_.Exception.Message
    exit $code
}
