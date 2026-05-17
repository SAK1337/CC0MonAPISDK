<#
.SYNOPSIS
    Fetch cc0mon smart-contract metadata.

.EXAMPLE
    .\cc0mon-api-get-contract.ps1
#>
[CmdletBinding()]
param()

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

Import-Module (Join-Path $PSScriptRoot '..\CC0MonHelpers.psm1') -Force

$action = 'get-contract'
Write-Cc0Log -Action $action -Level INFO -Message "script start"

try {
    $contract = Invoke-Cc0Request -Action $action -Path "/contract"
    $contract | ConvertTo-Json -Depth 32
    $address = if ($contract.address) { $contract.address } else { '<unknown>' }
    Write-Cc0Log -Action $action -Level INFO -Message "script exit code=0 address=$address"
    exit 0
} catch {
    $code = ConvertTo-Cc0ExitCode -ErrorRecord $_
    Write-Cc0Log -Action $action -Level ERROR -Message "exit=$code msg=$($_.Exception.Message)"
    [Console]::Error.WriteLine($_.Exception.Message)
    exit $code
}
