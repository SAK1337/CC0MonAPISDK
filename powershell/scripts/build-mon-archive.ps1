<#
.SYNOPSIS
    Build a per-species cc0mon archive: one JSON + one SVG + one PNG per species.

.DESCRIPTION
    Iterates the full 260-species registry. For each species that has a minted
    representative tokenId (259 of 260), fetches metadata + traits + SVG + PNG
    and writes all three on-disk artifacts with a shared basename:

        <OutDir>\<NNN>-mon-<Name>.json     (always, even for the unminted species)
        <OutDir>\<NNN>-mon-<Name>.svg      (only if tokenId is not null)
        <OutDir>\<NNN>-mon-<Name>.png      (only if tokenId is not null)

    Re-running is idempotent: a species with all expected files present is skipped.

.PARAMETER OutDir
    Output directory. Default: ./mon under the current working directory.

.PARAMETER Force
    Re-fetch and overwrite every species, ignoring existing files.

.EXAMPLE
    .\build-mon-archive.ps1
    .\build-mon-archive.ps1 -OutDir D:\cc0mon-archive
    .\build-mon-archive.ps1 -Force
#>
[CmdletBinding()]
param(
    [string] $OutDir,
    [switch] $Force
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

Import-Module (Join-Path $PSScriptRoot '..\CC0MonHelpers.psm1') -Force

$action = 'build-mon-archive'

if (-not $OutDir) { $OutDir = Join-Path (Get-Location).Path 'mon' }
if (-not (Test-Path $OutDir)) { New-Item -ItemType Directory -Path $OutDir | Out-Null }

$errorsLogPath = Join-Path (Get-Location).Path "$action-errors.log"

Write-Cc0Log -Action $action -Level INFO -Message "script start out_dir=$OutDir force=$($Force.IsPresent)"
Write-Host "[archive] output directory: $OutDir"
Write-Host "[archive] error log:        $errorsLogPath"

function Get-Prop {
    param($Obj, [string] $Name)
    if ($null -eq $Obj) { return $null }
    if ($Obj.PSObject.Properties.Name -contains $Name) { return $Obj.$Name }
    return $null
}

function Get-ListFromResponse {
    param($Resp)
    if ($Resp -is [System.Array]) { return $Resp }
    foreach ($k in 'cc0mon','species','images','items','data') {
        if ($Resp.PSObject.Properties.Name -contains $k) { return $Resp.$k }
    }
    return @($Resp)
}

function Get-SafeName {
    param([string] $Name)
    return ($Name -replace '[<>:"/\\|?*]', '_').Trim()
}

try {
    Write-Host "[archive] fetching /registry ..."
    $registryResp = Invoke-Cc0Request -Action $action -Path '/registry'
    $species = @(Get-ListFromResponse -Resp $registryResp)

    Write-Host "[archive] fetching /registry/images ..."
    $imagesResp = Invoke-Cc0Request -Action $action -Path '/registry/images'

    # /registry/images shape: { total, mapped, images: { "1": {...}, "2": {...}, ..., "260": {...} } }
    # The keys are species numbers as strings. We use them directly as the join key,
    # which sidesteps the duplicate-name problem (Vilewing #86/#229, Gazebleed #215/#228).
    $imagesMap = $null
    if ($imagesResp.PSObject.Properties.Name -contains 'images') {
        $imagesMap = $imagesResp.images
    } else {
        throw "/registry/images response missing 'images' property (got keys: $($imagesResp.PSObject.Properties.Name -join ', '))"
    }
    $imageEntryCount = ($imagesMap.PSObject.Properties | Measure-Object).Count

    if ($species.Count -ne $imageEntryCount) {
        throw "registry/images count mismatch: registry=$($species.Count) images=$imageEntryCount"
    }

    Write-Host "[archive] registry=$($species.Count) image-entries=$imageEntryCount (joining by species number)"
    Write-Cc0Log -Action $action -Level INFO -Message "loaded registry=$($species.Count) image_entries=$imageEntryCount"

    $sorted = @($species | Sort-Object @{ Expression = { [int](Get-Prop $_ 'number') } })

    $stats = [ordered]@{ total = $sorted.Count; skipped = 0; written = 0; failed = 0 }
    $i = 0

    foreach ($sp in $sorted) {
        $i++

        $number = [int](Get-Prop $sp 'number')
        $name   = [string](Get-Prop $sp 'name')
        $energy = Get-Prop $sp 'energy'
        $rarity = Get-Prop $sp 'rarity'

        $imageEntry = Get-Prop $imagesMap ([string]$number)
        if ($null -eq $imageEntry) {
            $stats.failed++
            $msg = "no /registry/images entry for species #$number"
            Write-Host ("[{0,3}/{1}] FAIL   #{2:D3} {3} :: {4}" -f $i, $sorted.Count, $number, $name, $msg) -ForegroundColor Red
            $ts = [DateTime]::UtcNow.ToString('yyyy-MM-ddTHH:mm:ss.fffZ')
            Add-Content -Path $errorsLogPath -Encoding UTF8 -Value "$ts | #$number $name | $msg"
            continue
        }
        $imgName = Get-Prop $imageEntry 'name'
        if ($imgName -and ($imgName -ne $name)) {
            Write-Cc0Log -Action $action -Level WARNING -Message "name disagree at #$number : registry='$name' images='$imgName'"
        }

        $safeName = Get-SafeName -Name $name
        $basename = '{0:D3}-mon-{1}' -f $number, $safeName
        $jsonPath = Join-Path $OutDir "$basename.json"
        $svgPath  = Join-Path $OutDir "$basename.svg"
        $pngPath  = Join-Path $OutDir "$basename.png"

        $tokenId = Get-Prop $imageEntry 'tokenId'
        $svgUrl  = Get-Prop $imageEntry 'svg'
        $pngUrl  = Get-Prop $imageEntry 'png'
        $hasToken = ($null -ne $tokenId)

        if (-not $Force) {
            $trioPresent = if ($hasToken) {
                (Test-Path $jsonPath) -and (Test-Path $svgPath) -and (Test-Path $pngPath)
            } else {
                Test-Path $jsonPath
            }
            if ($trioPresent) {
                $stats.skipped++
                Write-Host ("[{0,3}/{1}] skip   #{2:D3} {3}" -f $i, $sorted.Count, $number, $name)
                continue
            }
        }

        try {
            $metadata = $null
            $traits   = $null

            if ($hasToken) {
                $metadata = Invoke-Cc0Request -Action $action -Path "/cc0mon/$tokenId/metadata"
                $traits   = Invoke-Cc0Request -Action $action -Path "/cc0mon/$tokenId/traits"

                $svgBytes = Invoke-Cc0Request -Action $action -Path "/cc0mon/$tokenId/image.svg" -Accept 'image/svg+xml' -ReturnRaw
                [System.IO.File]::WriteAllBytes($svgPath, $svgBytes)

                $pngBytes = Invoke-Cc0Request -Action $action -Path "/cc0mon/$tokenId/image.png" -Accept 'image/png' -ReturnRaw
                [System.IO.File]::WriteAllBytes($pngPath, $pngBytes)
            }

            $record = [ordered]@{
                number   = $number
                name     = $name
                energy   = $energy
                rarity   = $rarity
                tokenId  = $tokenId
                svgUrl   = $svgUrl
                pngUrl   = $pngUrl
                metadata = $metadata
                traits   = $traits
                _source  = [ordered]@{
                    fetchedAt = [DateTime]::UtcNow.ToString('yyyy-MM-ddTHH:mm:ss.fffZ')
                    sdkScript = 'build-mon-archive.ps1'
                }
            }

            $json = ($record | ConvertTo-Json -Depth 32)
            Set-Content -Path $jsonPath -Value $json -Encoding UTF8

            $stats.written++
            $tokenLabel = if ($hasToken) { "token=$tokenId" } else { 'token=null' }
            Write-Host ("[{0,3}/{1}] write  #{2:D3} {3} ({4})" -f $i, $sorted.Count, $number, $name, $tokenLabel)
        }
        catch {
            $stats.failed++
            $msg = $_.Exception.Message
            Write-Host ("[{0,3}/{1}] FAIL   #{2:D3} {3} :: {4}" -f $i, $sorted.Count, $number, $name, $msg) -ForegroundColor Red

            $ts = [DateTime]::UtcNow.ToString('yyyy-MM-ddTHH:mm:ss.fffZ')
            Add-Content -Path $errorsLogPath -Encoding UTF8 -Value "$ts | #$number $name | $msg"
            Write-Cc0Log -Action $action -Level ERROR -Message "species #$number $name failed: $msg"

            foreach ($p in @($jsonPath, $svgPath, $pngPath)) {
                if (Test-Path $p) { Remove-Item $p -Force -ErrorAction SilentlyContinue }
            }
        }
    }

    Write-Host ''
    Write-Host '[archive] summary:'
    Write-Host ("  total   : {0}" -f $stats.total)
    Write-Host ("  skipped : {0}" -f $stats.skipped)
    Write-Host ("  written : {0}" -f $stats.written)
    Write-Host ("  failed  : {0}" -f $stats.failed)

    Write-Cc0Log -Action $action -Level INFO -Message ("script exit code=0 total={0} skipped={1} written={2} failed={3}" -f $stats.total, $stats.skipped, $stats.written, $stats.failed)
    exit 0
}
catch {
    $code = ConvertTo-Cc0ExitCode -ErrorRecord $_
    Write-Cc0Log -Action $action -Level ERROR -Message "exit=$code msg=$($_.Exception.Message)"
    [Console]::Error.WriteLine($_.Exception.Message)
    exit $code
}
