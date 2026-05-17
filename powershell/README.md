# cc0mon API — PowerShell examples

10 PowerShell 7 scripts plus a small shared module (`CC0MonHelpers.psm1`) for the cc0mon.com NFT API.

- **PowerShell:** 7.x cross-platform (Windows / macOS / Linux)
- **Dependencies:** none (uses built-in `Invoke-WebRequest`)
- **Retries:** on by default in the shared module (429 + 5xx, exponential backoff, `Retry-After` honored)

> **Note on Windows PowerShell 5.1:** not supported in v0.1. PS 5.1 defaults to TLS 1.0 and lacks several modern operators used here.

## Install

There's nothing to install. Just import the helper module from your shell or from each script (the scripts already do this themselves):

```powershell
Import-Module .\CC0MonHelpers.psm1
```

## Hello world

```powershell
Import-Module .\powershell\CC0MonHelpers.psm1
$token = Invoke-Cc0Request -Action "demo" -Path "/cc0mon/1"
$token | ConvertTo-Json -Depth 5
```

## Example scripts

Each script under `scripts/` is parameterized via PowerShell's standard `param()` block — meaning every script supports `-?` help, tab-completion, and parameter validation out of the box.

```powershell
.\scripts\cc0mon-api-get-token.ps1 -Id 1
.\scripts\cc0mon-api-get-image-png.ps1 -Id 42 -Out .\my-mon.png
.\scripts\cc0mon-api-get-collector.ps1 -Address 0x0000000000000000000000000000000000000000
```

| Script | Args |
|--------|------|
| `cc0mon-api-get-token.ps1` | `-Id <n>` |
| `cc0mon-api-get-metadata.ps1` | `-Id <n>` |
| `cc0mon-api-get-traits.ps1` | `-Id <n>` |
| `cc0mon-api-get-image-svg.ps1` | `-Id <n> [-Out path]` |
| `cc0mon-api-get-image-png.ps1` | `-Id <n> [-Out path]` |
| `cc0mon-api-get-owner.ps1` | `-Id <n>` |
| `cc0mon-api-get-contract.ps1` | (none) |
| `cc0mon-api-get-registry.ps1` | `[-Limit n]` |
| `cc0mon-api-get-registry-images.ps1` | `[-Limit n]` |
| `cc0mon-api-get-collector.ps1` | `-Address 0x…` |

## What the helper module provides

| Function | Purpose |
|----------|---------|
| `Invoke-Cc0Request` | HTTP GET with retry/backoff for 429/5xx; honors `Retry-After`. Returns parsed JSON or raw bytes (`-ReturnRaw`, sourced from `RawContentStream` so binary data isn't corrupted). |
| `Write-Cc0Log` | Append-only log to `./cc0mon-api-<action>.log` with `ISO8601 \| LEVEL \| action \| message`. UTC timestamps. |
| `Test-Cc0Address` | Returns `$true` if input is a valid 0x-prefixed 42-char hex address. |
| `Assert-Cc0Address` | Throws a spec-formatted `validation:` error on a bad address — use in script entry points. |
| `Test-Cc0Energy` / `Test-Cc0Rarity` | Validate case-insensitively; return canonical case or throw. |
| `Get-Cc0Energies` / `Get-Cc0Rarities` | Return the canonical 16 / 4 string arrays. |
| `ConvertTo-Cc0ExitCode` | Maps a thrown error record to the spec's exit code (0/1/2/3/4/5). |

## Logging

Append-only logs to `./cc0mon-api-<action>.log` in the current working directory. Level defaults to `INFO`; set `$env:CC0MON_LOG_LEVEL = "DEBUG"` for request/response detail.

```powershell
$env:CC0MON_LOG_LEVEL = "DEBUG"
.\scripts\cc0mon-api-get-token.ps1 -Id 1
Get-Content .\cc0mon-api-get-token.log -Tail 10
```

## Exit codes

| Code | Meaning |
|------|---------|
| `0` | Success |
| `2` | Network failure |
| `3` | HTTP 4xx (after retries) |
| `4` | HTTP 5xx (after retries) |
| `5` | Argument/input validation |
| `1` | Unexpected error |

## Search & filtering

Three scripts accept filter parameters. Filters are AND-combined and validated against the 16 energies × 4 rarities exposed by `Get-Cc0Energies` / `Get-Cc0Rarities`.

```powershell
.\scripts\cc0mon-api-get-registry.ps1 -Energy Fire -Rarity Common
.\scripts\cc0mon-api-get-registry-images.ps1 -NameContains drill -HasImage
.\scripts\cc0mon-api-get-collector.ps1 -Address 0xB07952A55bF9c45C268F37C3631823Df50ac721a -OwnedOnly -Energy Fire
```

```powershell
Import-Module .\CC0MonHelpers.psm1
Get-Cc0Energies          # 16 canonical energy types
Get-Cc0Rarities          # 4 canonical rarities
Test-Cc0Energy -Value 'fire'   # returns 'Fire'; throws on unknown
```

Invalid `-Energy` / `-Rarity` exits `5` with a message listing valid values.

## Custom retry behavior

```powershell
Import-Module .\CC0MonHelpers.psm1
# Disable retries:
$token = Invoke-Cc0Request -Action "demo" -Path "/cc0mon/1" -Retries 0
# Shorter timeout:
$token = Invoke-Cc0Request -Action "demo" -Path "/cc0mon/1" -TimeoutSec 5
```
