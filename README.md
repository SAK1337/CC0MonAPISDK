# cc0mon API SDK

A reference SDK and example-script collection for the public [cc0mon.com](https://cc0mon.com) NFT API — covering **Python**, **Java**, and **PowerShell**.

> **What is cc0mon?** A fully on-chain creature collection: 9,999 NFTs across 260 species on Ethereum mainnet, all CC0 (public domain). Contract: `0xeeb036dbbd3039429c430657ed9836568da79d5f`.

## What's in here

| Directory | Contents |
|-----------|----------|
| `spec.md` | Formal spec for the SDK (read this first) |
| `questionsandrecommendations.md` | Deferred design questions with stated defaults |
| `python/` | Python SDK (`cc0mon_sdk`) + 10 example scripts |
| `java/` | Java SDK (Maven) + 10 JBang example scripts |
| `powershell/` | 10 standalone PowerShell scripts + `CC0MonHelpers.psm1` module |

## The API at a glance

- **Base URL:** `https://api.cc0mon.com`
- **Auth:** none
- **Rate limit:** 60 requests/minute per IP
- **Endpoints:** 10 GETs (8 JSON, 2 image)

The 10 endpoints map to 10 action slugs — every example script is named `cc0mon-api-<action>.<ext>` and writes a structured log to `./cc0mon-api-<action>.log` in your current working directory.

| Action | Endpoint |
|--------|----------|
| `get-token` | `GET /cc0mon/{id}` |
| `get-metadata` | `GET /cc0mon/{id}/metadata` |
| `get-traits` | `GET /cc0mon/{id}/traits` |
| `get-image-svg` | `GET /cc0mon/{id}/image.svg` |
| `get-image-png` | `GET /cc0mon/{id}/image.png` |
| `get-owner` | `GET /cc0mon/{id}/owner` |
| `get-contract` | `GET /contract` |
| `get-registry` | `GET /registry` |
| `get-registry-images` | `GET /registry/images` |
| `get-collector` | `GET /collector/{address}` |

## Quick start

### Python (3.10+)

```powershell
cd python
python -m pip install -e .
python examples\cc0mon-api-get-token.py --id 1
Get-Content .\cc0mon-api-get-token.log -Tail 3
```

See [`python/README.md`](python/README.md) for details.

### Java (JDK 17 + [JBang](https://www.jbang.dev/download/))

```powershell
cd java
mvn install -DskipTests
jbang examples\cc0mon-api-get-token.java --id 1
Get-Content .\cc0mon-api-get-token.log -Tail 3
```

See [`java/README.md`](java/README.md) for details.

### PowerShell (7.x)

```powershell
Import-Module .\powershell\CC0MonHelpers.psm1
.\powershell\scripts\cc0mon-api-get-token.ps1 -Id 1
Get-Content .\cc0mon-api-get-token.log -Tail 3
```

See [`powershell/README.md`](powershell/README.md) for details.

## Logging behavior

Every example script (in any language) writes to `./cc0mon-api-<action>.log` resolved against the **current working directory**, not the script's location. Default level is `INFO`; set the env var `CC0MON_LOG_LEVEL=DEBUG` for full request/response detail.

Sample log line:

```
2026-05-17T14:23:08.412Z | INFO | get-token | GET https://api.cc0mon.com/cc0mon/1 | status=200 | latency_ms=187 | bytes=842 | ok
```

## Search & filtering

Three scripts accept filter flags. Filters are AND-combined and applied client-side after a single GET.

```powershell
# Find Fire-type Common species
python python\examples\cc0mon-api-get-registry.py --energy Fire --rarity Common

# All entries in the species image map that have an actual token
jbang java\examples\cc0mon-api-get-registry-images.java --has-image

# A wallet's owned Fire-type cc0mon
.\powershell\scripts\cc0mon-api-get-collector.ps1 -Address 0xB07952A55bF9c45C268F37C3631823Df50ac721a -OwnedOnly -Energy Fire
```

Valid `--energy` values: Bug, Celestial, Dragon, Earth, Electric, Fire, Fossil, Ghost, Grass, Ice, Metal, Mythic, Ocean, Rock, Toxic, Underworld
Valid `--rarity` values: Common, Uncommon, Rare, Legendary

Unknown values exit `5` with a friendly listing. SDK users can also call `Client.find_species()` / `Client.find_collector_items()` (Python) or `findSpecies()` / `findCollectorItems()` (Java) directly.

## Rate-limit handling

The SDK retries on `429` and `5xx` by default — 3 attempts with exponential backoff (base 1s, cap 30s, full jitter), honoring `Retry-After`. Pass `retries=0` to disable.

## License

MIT — see [`LICENSE`](LICENSE). The cc0mon collection itself is CC0.
