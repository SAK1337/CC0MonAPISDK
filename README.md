# cc0mon API SDK

A reference, multi-language SDK and example-script collection for the public **[cc0mon.com](https://cc0mon.com)** NFT API — covering **Python**, **Java**, and **PowerShell**.

> **cc0mon** is a fully on-chain creature collection: 9,999 NFTs across 260 species on Ethereum mainnet, all CC0 (public domain). Contract: `0xeeb036dbbd3039429c430657ed9836568da79d5f`.

The repo ships three independent SDKs plus 30 single-purpose example scripts (10 per language) that demonstrate every endpoint with logging, retries, and structured error handling. A developer who has never seen the API should be able to clone the repo and run a working API call in under 60 seconds.

---

## Table of contents

- [Quick start](#quick-start)
- [Repository layout](#repository-layout)
- [Choosing an SDK](#choosing-an-sdk)
- [The cc0mon API](#the-cc0mon-api)
- [SDK design](#sdk-design)
- [Python SDK](#python-sdk)
- [Java SDK](#java-sdk)
- [PowerShell SDK](#powershell-sdk)
- [Example scripts (all 30)](#example-scripts-all-30)
- [Logging](#logging)
- [Retry and rate-limit handling](#retry-and-rate-limit-handling)
- [Error model and exit codes](#error-model-and-exit-codes)
- [Search and filtering](#search-and-filtering)
- [Data models reference](#data-models-reference)
- [Environment variables](#environment-variables)
- [Testing](#testing)
- [Building from source](#building-from-source)
- [Troubleshooting](#troubleshooting)
- [Known limitations](#known-limitations-v01)
- [Project documents](#project-documents)
- [License and credits](#license-and-credits)

---

## Quick start

Each SDK is independently installable. Pick the language you want; everything else in this README explains it in detail.

### Python (3.10+)

```powershell
cd python
python -m pip install -e .
python examples\cc0mon-api-get-token.py --id 1
Get-Content .\cc0mon-api-get-token.log -Tail 3
```

### Java (JDK 17 + [JBang](https://www.jbang.dev/download/))

```powershell
cd java
mvn install -DskipTests
jbang examples\cc0mon-api-get-token.java --id 1
Get-Content .\cc0mon-api-get-token.log -Tail 3
```

### PowerShell (7.x)

```powershell
Import-Module .\powershell\CC0MonHelpers.psm1
.\powershell\scripts\cc0mon-api-get-token.ps1 -Id 1
Get-Content .\cc0mon-api-get-token.log -Tail 3
```

All three commands hit the same live API and write a structured log line to the same `cc0mon-api-get-token.log` file in your current working directory.

---

## Repository layout

```
CC0MonAPISDK/
├── README.md                       ← you are here
├── spec.md                         ← formal v0.1 specification
├── questionsandrecommendations.md  ← deferred design questions with defaults
├── codeauditopus47.md              ← code audit + remediation report
├── LICENSE                         ← MIT
│
├── python/
│   ├── cc0mon_sdk/                 ← library package
│   │   ├── __init__.py             ← public re-exports
│   │   ├── client.py               ← Client class, retry/backoff, version
│   │   ├── errors.py               ← exception hierarchy
│   │   ├── logging_setup.py        ← configure_logger helper
│   │   └── models.py               ← dataclass models + canonical sets
│   ├── examples/                   ← 10 CLI scripts (cc0mon-api-<action>.py)
│   ├── tests/
│   │   ├── test_filters.py         ← unit tests (12)
│   │   ├── test_integration.py     ← live-API smoke tests (gated)
│   │   └── test_example_exit_codes.py  ← regression: 50 parametrized assertions
│   ├── pyproject.toml
│   └── README.md
│
├── java/
│   ├── src/main/java/com/cc0mon/sdk/
│   │   ├── Client.java             ← Client, retry/backoff, version
│   │   ├── Errors.java             ← exception hierarchy
│   │   ├── LoggingSetup.java       ← configure(action) helper
│   │   ├── Models.java             ← record types + canonical sets
│   │   └── Validators.java         ← energy/rarity validation
│   ├── src/test/java/com/cc0mon/sdk/
│   │   └── ClientIT.java           ← integration tests (gated)
│   ├── examples/                   ← 10 JBang scripts (cc0mon-api-<action>.java)
│   ├── pom.xml
│   └── README.md
│
└── powershell/
    ├── CC0MonHelpers.psm1          ← shared helpers module
    ├── scripts/                    ← 10 standalone scripts (cc0mon-api-<action>.ps1)
    └── README.md
```

The three SDKs are independent — install or use just one without touching the others.

---

## Choosing an SDK

| Use case | Best fit | Why |
|---------|---------|-----|
| Notebook, pipeline, web service in Python | **Python** | Typed dataclasses, frozen models, `with`-statement client, full pytest coverage |
| JVM service, JBang one-off, Maven dep | **Java** | Records, zero external HTTP dep (uses JDK `java.net.http`), Jackson-backed JSON tolerance |
| Ops automation, ad-hoc queries on any OS | **PowerShell** | Zero install (Windows), PS-native parameter binding, structured logging, JSON pipe-friendly |

Behavior is identical across all three: same retry algorithm, same exit codes, same log format, same client-side filter semantics. Switching languages doesn't mean re-learning the SDK.

---

## The cc0mon API

- **Base URL:** `https://api.cc0mon.com`
- **Auth:** none (public)
- **Rate limit:** 60 requests/minute per IP, sliding window
- **Versioning:** unversioned (no `/v1/` prefix; single live version)
- **OpenAPI spec:** **not** published — the SDK models are hand-coded against observed responses

### The 10 endpoints

| # | Method | Path | Returns | Action slug |
|---|--------|------|---------|-------------|
| 1 | GET | `/cc0mon/{id}` | Token summary (name, traits, image URLs, owner) | `get-token` |
| 2 | GET | `/cc0mon/{id}/metadata` | ERC-721 metadata (name, description, image, attributes) | `get-metadata` |
| 3 | GET | `/cc0mon/{id}/traits` | Decoded trait array | `get-traits` |
| 4 | GET | `/cc0mon/{id}/image.svg` | `image/svg+xml` binary | `get-image-svg` |
| 5 | GET | `/cc0mon/{id}/image.png` | `image/png` binary | `get-image-png` |
| 6 | GET | `/cc0mon/{id}/owner` | `{"owner": "0x…"}` | `get-owner` |
| 7 | GET | `/contract` | Contract metadata | `get-contract` |
| 8 | GET | `/registry` | Array of 260 species (number, name, energy, rarity) | `get-registry` |
| 9 | GET | `/registry/images` | Species → image map (260 entries, 259 mapped) | `get-registry-images` |
| 10 | GET | `/collector/{address}` | Wallet collection checklist | `get-collector` |

`{id}` is an integer in `1..10000`; `{address}` is a 0x-prefixed 42-character Ethereum address.

---

## SDK design

Three design principles shape every implementation:

### 1. Typed models with a `raw` escape hatch

Every response model — `Token`, `Metadata`, `Traits`, `Contract`, `Species`, `SpeciesImage`, `Collector`, `CollectorItem`, `OwnerInfo` — carries a `raw` field containing the original parsed JSON (`dict` in Python, `Map<String, Object>` in Java). When the API adds a field the SDK does not yet model, callers can still read it via `raw`. The SDK survives additive API changes without a release.

### 2. Multi-key tolerance

Models accept both `snake_case` and `camelCase` for the same logical field. For example, `Token.fromJson` reads `tokenId`, `id`, and `number` in that order before falling back to a default. This means if the API silently renames a key or restructures a payload, the typed view still works for known fields and the new shape is available via `raw`.

### 3. Identical behavior across languages

The retry algorithm (3 attempts, exponential backoff with full jitter, cap 30s, honors `Retry-After`), the exit codes (0/1/2/3/4/5), the log format (`ISO8601 | LEVEL | action | message`), and the filter semantics (AND-combined, case-insensitive energy/rarity validation) are the same in all three SDKs. A user who learns one SDK knows the others.

---

## Python SDK

### Install

```powershell
cd python
python -m pip install -e .
```

`-e` installs in editable mode so SDK edits are picked up immediately. The only runtime dependency is `httpx >= 0.27`.

### Hello world

```python
from cc0mon_sdk import Client

with Client() as c:
    token = c.get_token(1)
    print(token.name, "owned by", token.owner)
```

### All client methods

```python
from cc0mon_sdk import Client

with Client() as c:
    # 10 endpoint methods
    token       = c.get_token(1)               # → Token
    metadata    = c.get_metadata(1)            # → Metadata
    traits      = c.get_traits(1)              # → Traits
    svg_bytes   = c.get_image_svg(1)           # → bytes (image/svg+xml)
    png_bytes   = c.get_image_png(1)           # → bytes (image/png)
    owner_info  = c.get_owner(1)               # → OwnerInfo
    contract    = c.get_contract()             # → Contract
    species     = c.get_registry()             # → list[Species]
    images      = c.get_registry_images()      # → list[SpeciesImage]
    collector   = c.get_collector("0x...")     # → Collector

    # 2 convenience filters (client-side, after a single GET)
    fire_common = c.find_species(energy="Fire", rarity="Common")
    owned_fire  = c.find_collector_items("0x...", owned_only=True, energy="Fire")
```

### Customizing the client

```python
from cc0mon_sdk import Client

# Defaults: base_url=https://api.cc0mon.com, timeout=30s, retries=3
with Client(timeout=10, retries=0, user_agent="my-app/1.0") as c:
    c.get_token(1)
```

| Parameter | Default | Meaning |
|-----------|--------|---------|
| `base_url` | `https://api.cc0mon.com` | Override for testing or proxies |
| `timeout` | `30.0` | Per-request timeout in seconds |
| `retries` | `3` | Retry attempts on 429/5xx/transient network errors. `0` disables |
| `user_agent` | `cc0mon-sdk-python/<version>` | Custom UA string |

### Error handling

```python
from cc0mon_sdk import (
    Client, ValidationError, NetworkError,
    RateLimitError, ClientApiError, ServerApiError, ApiError, CC0MonError,
)

try:
    with Client() as c:
        token = c.get_token(99999)
except ValidationError as e:
    print(f"bad input: {e}")              # exit 5 in scripts
except RateLimitError as e:
    print(f"rate limited; retry after {e.retry_after_seconds}s")  # exit 3
except ClientApiError as e:
    print(f"HTTP {e.status_code}: {e.body}")  # exit 3
except ServerApiError as e:
    print(f"server HTTP {e.status_code}")     # exit 4
except NetworkError as e:
    print(f"transport: {e}")                  # exit 2
```

The exception hierarchy is flat: `RateLimitError` is a **sibling** of `ClientApiError` (both extend `ApiError`), not a subclass. If you only catch `ClientApiError`, a 429 will fall through — that's why every example script has explicit `RateLimitError` handling.

### Constants and helpers

```python
from cc0mon_sdk import (
    ENERGIES, RARITIES,
    validate_energy, validate_rarity,
    configure_logger,
)

print(sorted(ENERGIES))                  # the 16 canonical types
validate_energy("fire")                   # → "Fire"  (canonical case)
validate_rarity("LEGENDARY")              # → "Legendary"
configure_logger("my-script")             # logger writing to ./cc0mon-api-my-script.log
```

---

## Java SDK

### Install

```powershell
cd java
mvn install -DskipTests
```

This compiles `com.cc0mon:sdk:0.1.0` and installs it to your local `~/.m2/repository`. JBang then resolves it via the `//DEPS` directive in each example. Prerequisites: JDK 17+, Maven 3.9+, optionally [JBang](https://www.jbang.dev/download/) for the example scripts.

### Hello world

```java
import com.cc0mon.sdk.Client;
import com.cc0mon.sdk.Models.Token;

class Demo {
    public static void main(String[] args) {
        try (Client c = new Client()) {
            Token token = c.getToken(1);
            System.out.println(token.name() + " owned by " + token.owner());
        }
    }
}
```

### All client methods

```java
import com.cc0mon.sdk.Client;
import com.cc0mon.sdk.Models.*;
import java.util.List;

try (Client c = new Client()) {
    Token token             = c.getToken(1);
    Metadata md             = c.getMetadata(1);
    Traits traits           = c.getTraits(1);
    byte[] svg              = c.getImageSvg(1);
    byte[] png              = c.getImagePng(1);
    OwnerInfo owner         = c.getOwner(1);
    Contract contract       = c.getContract();
    List<Species> species   = c.getRegistry();
    List<SpeciesImage> imgs = c.getRegistryImages();
    Collector collector     = c.getCollector("0x...");

    List<Species> fireCommon = c.findSpecies("Fire", "Common", null);
    List<CollectorItem> owned = c.findCollectorItems("0x...", true, "Fire", null);
}
```

### Customizing the client

```java
import java.time.Duration;
import com.cc0mon.sdk.Client;

try (Client c = new Client(
        "https://api.cc0mon.com",
        Duration.ofSeconds(10),
        0,                  // retries
        "my-app/1.0"        // userAgent (or null for default)
)) {
    c.getToken(1);
}
```

Constructor parameters mirror the Python client: `baseUrl`, `timeout` (as `Duration`), `retries`, `userAgent`. The `timeout` value is applied to both connection establishment and per-request reads.

### Error handling

```java
import com.cc0mon.sdk.Errors;
import com.cc0mon.sdk.Client;

try (Client c = new Client()) {
    var token = c.getToken(99999);
} catch (Errors.ValidationException e) {
    System.err.println("bad input: " + e.getMessage());
} catch (Errors.RateLimitException e) {
    System.err.println("rate limited; retry after " + e.getRetryAfterSeconds() + "s");
} catch (Errors.ClientApiException e) {
    System.err.printf("client HTTP %d: %s%n", e.getStatusCode(), e.getBody());
} catch (Errors.ServerApiException e) {
    System.err.printf("server HTTP %d%n", e.getStatusCode());
} catch (Errors.NetworkException e) {
    System.err.println("transport: " + e.getMessage());
}
```

All SDK exceptions extend `Errors.CC0MonException` (which extends `RuntimeException`), so a single `catch (Errors.CC0MonException e)` will catch the family if you don't care to distinguish.

### Why JBang for examples?

Standard `javac` requires the public class name to match the filename. `cc0mon-api-get-token` is not a valid Java identifier, so the example scripts use **package-private** classes (`class GetToken`) inside hyphenated filenames. [JBang](https://www.jbang.dev/) sidesteps the naming rule, declares its dependencies via `//DEPS` comments, and auto-runs `main`. If you'd rather skip JBang, the SDK is a regular Maven artifact — drop it into any normal project.

---

## PowerShell SDK

### Install

There's nothing to install. Import the helper module — each script does this itself, but you can also do it from your shell:

```powershell
Import-Module .\powershell\CC0MonHelpers.psm1
```

PowerShell 7.x is required (cross-platform: Windows / macOS / Linux). Windows PowerShell 5.1 is **not** supported in v0.1 — it defaults to TLS 1.0 and lacks several modern operators used here.

### Hello world

```powershell
Import-Module .\powershell\CC0MonHelpers.psm1
$token = Invoke-Cc0Request -Action "demo" -Path "/cc0mon/1"
$token | ConvertTo-Json -Depth 5
```

### Helper module functions

| Function | Purpose |
|----------|---------|
| `Invoke-Cc0Request` | HTTP GET with retry/backoff for 429/5xx; honors `Retry-After`. Returns parsed JSON or raw bytes (`-ReturnRaw`, via `RawContentStream` so binary data isn't corrupted). |
| `Write-Cc0Log` | Append-only log to `./cc0mon-api-<action>.log`, UTC timestamps, level-filtered. |
| `Test-Cc0Address` | Returns `$true` if the input is a valid 0x-prefixed 42-char hex address. |
| `Assert-Cc0Address` | Throws a spec-formatted `validation:` error on a bad address — use in script entry points. |
| `Test-Cc0Energy` / `Test-Cc0Rarity` | Validate case-insensitively; return canonical case or throw. |
| `Get-Cc0Energies` / `Get-Cc0Rarities` | Return the canonical 16 / 4 string arrays. |
| `ConvertTo-Cc0ExitCode` | Maps a thrown `ErrorRecord` to the spec exit code (0/1/2/3/4/5). |
| `Get-Cc0LogLevel` | Reads `$env:CC0MON_LOG_LEVEL` (default `INFO`). |

### Custom retry behavior

```powershell
Import-Module .\powershell\CC0MonHelpers.psm1

# Disable retries
$token = Invoke-Cc0Request -Action "demo" -Path "/cc0mon/1" -Retries 0

# Shorter timeout
$token = Invoke-Cc0Request -Action "demo" -Path "/cc0mon/1" -TimeoutSec 5

# Download binary
$bytes = Invoke-Cc0Request -Action "demo" -Path "/cc0mon/1/image.png" `
            -Accept 'image/png' -ReturnRaw
[System.IO.File]::WriteAllBytes("./mon.png", $bytes)
```

---

## Example scripts (all 30)

Every example script is named `cc0mon-api-<action>.<ext>` and writes a structured append-only log to `./cc0mon-api-<action>.log` resolved against the **current working directory** (not the script's directory).

### Per-action matrix

| Action | Python | Java | PowerShell | Required args | Optional flags |
|--------|--------|------|-----------|---------------|----------------|
| `get-token` | `python examples\cc0mon-api-get-token.py` | `jbang examples\cc0mon-api-get-token.java` | `.\scripts\cc0mon-api-get-token.ps1` | `--id` / `-Id` | — |
| `get-metadata` | …`-metadata.py` | …`-metadata.java` | …`-metadata.ps1` | `--id` / `-Id` | — |
| `get-traits` | …`-traits.py` | …`-traits.java` | …`-traits.ps1` | `--id` / `-Id` | — |
| `get-image-svg` | …`-image-svg.py` | …`-image-svg.java` | …`-image-svg.ps1` | `--id` / `-Id` | `--out` / `-Out` |
| `get-image-png` | …`-image-png.py` | …`-image-png.java` | …`-image-png.ps1` | `--id` / `-Id` | `--out` / `-Out` |
| `get-owner` | …`-owner.py` | …`-owner.java` | …`-owner.ps1` | `--id` / `-Id` | — |
| `get-contract` | …`-contract.py` | …`-contract.java` | …`-contract.ps1` | — | — |
| `get-registry` | …`-registry.py` | …`-registry.java` | …`-registry.ps1` | — | `--limit`/`-Limit`, `--energy`/`-Energy`, `--rarity`/`-Rarity`, `--name-contains`/`-NameContains` |
| `get-registry-images` | …`-registry-images.py` | …`-registry-images.java` | …`-registry-images.ps1` | — | `--limit`/`-Limit`, `--name-contains`/`-NameContains`, `--has-image`/`-HasImage` |
| `get-collector` | …`-collector.py` | …`-collector.java` | …`-collector.ps1` | `--address` / `-Address` | `--owned-only`/`-OwnedOnly`, `--energy`/`-Energy`, `--rarity`/`-Rarity` |

### Common script behavior

Every example script:

1. Parses its arguments using the stdlib (Python `argparse`, Java `String[] args` parsing, PowerShell `param()` with `[ValidateRange]`).
2. Configures logging to `./cc0mon-api-<action>.log` (append, UTF-8, UTC timestamps).
3. Instantiates the SDK client.
4. Invokes the relevant endpoint.
5. On success: pretty-prints JSON to stdout for JSON endpoints, or the resolved output path for image endpoints. Exits `0`.
6. On error: logs the error with context to the log file, writes a one-line human-readable error to stderr, exits with the spec exit code.

### Image scripts

```powershell
python python\examples\cc0mon-api-get-image-png.py --id 42 --out .\my-mon.png
jbang java\examples\cc0mon-api-get-image-svg.java --id 42 --out .\my-mon.svg
.\powershell\scripts\cc0mon-api-get-image-png.ps1 -Id 42 -Out .\my-mon.png
```

- Default output path: `./cc0mon-<id>.svg` or `./cc0mon-<id>.png` in CWD.
- Existing files are **silently overwritten** (matches `curl -o` and `wget` defaults).
- The resolved absolute output path is printed to stdout on success.

### Collector script

```powershell
# Full summary (progress, byEnergy, full 260-item checklist)
python python\examples\cc0mon-api-get-collector.py --address 0xB07952A55bF9c45C268F37C3631823Df50ac721a

# Filtered: only collected Fire-type items (prints just the items array)
python python\examples\cc0mon-api-get-collector.py --address 0xB079... --owned-only --energy Fire
```

- Address must be a 42-char 0x-prefixed hex string. ENS names are out of scope for v0.1.
- The SDK normalizes the address to lowercase before sending.
- Without filters: prints the full collector summary.
- With any filter: prints only the filtered checklist items array. Empty results print `[]` and exit `0` (not an error).

---

## Logging

| Aspect | Behavior |
|--------|----------|
| **File path** | `<CWD>/cc0mon-api-<action>.log` |
| **Resolution** | Python `pathlib.Path.cwd()`, Java `System.getProperty("user.dir")`, PowerShell `(Get-Location).Path` |
| **Mode** | Append (`a`) — history preserved across runs |
| **Encoding** | UTF-8 |
| **Timestamps** | UTC ISO8601 with `Z` suffix (`2026-05-17T14:23:08.412Z`) |
| **Default level** | `INFO` |
| **Override** | `CC0MON_LOG_LEVEL` env var ∈ {`DEBUG`, `INFO`, `WARNING`, `ERROR`} |

### Line format

```
<ISO8601-ts> | <LEVEL> | <action> | <METHOD> <URL> | status=<code> | latency_ms=<n> | bytes=<n> | <message>
```

Example:

```
2026-05-17T14:23:08.412Z | INFO | get-token | GET https://api.cc0mon.com/cc0mon/1 | status=200 | latency_ms=187 | bytes=842 | ok
```

### What gets logged

- **Scripts:** start (with parsed args), exit (with code and key counts), errors with full context.
- **SDK:** every request (method, URL, status, latency, bytes) at `INFO`; retry attempts at `INFO`; rate-limit waits at `WARNING`; terminal failures at `ERROR`.
- **DEBUG:** adds request and response detail.

### Library-mode logging

When the SDK is used as a library (no `configure_logger` call), Python honors `CC0MON_LOG_LEVEL` at import time, so SDK log events still flow to whatever handlers your application has configured.

### Concurrent writes

v0.1 does not attempt cross-process locking. Two processes running the same action in parallel may interleave lines but won't split a line mid-write. Within one JVM, Java reuses a single `FileHandler` per log path so repeated `configure(action)` calls don't stack duplicate handlers.

---

## Retry and rate-limit handling

| Item | Value |
|------|-------|
| Default retries | 3 |
| Triggers | HTTP 429, HTTP 500/502/503/504, transient network errors (DNS/TCP/TLS/timeout) |
| Backoff | Exponential, base 1.0s, multiplier 2.0, cap 30s, **full jitter** (random in `[0, computed_delay]`) |
| `Retry-After` | **Honored** when present (replaces computed backoff for that attempt) |
| Disable | Pass `retries=0` to the client constructor |
| Logging | Each retry attempt logged at `INFO` with attempt number and sleep duration |

### Algorithm (pseudocode — identical across all three SDKs)

```
attempt = 0
loop:
    response = http_get(url)
    if response.status < 400: return response
    if attempt >= retries: raise mapped_exception(response)
    if response.status not in (429, 500, 502, 503, 504): raise
    delay = retry_after_header or min(cap, base * (2 ** attempt))
    sleep(uniform(0, delay))    # full jitter
    attempt += 1
```

Network errors follow the same retry schedule. After all retries are exhausted, the SDK raises a typed exception (`RateLimitError` for 429, `ClientApiError` for other 4xx, `ServerApiError` for 5xx, `NetworkError` for transport failures).

---

## Error model and exit codes

### Exception hierarchy

**Python** (`cc0mon_sdk.errors`):

```
CC0MonError                  (base)
├── ValidationError          (bad arguments before any request)
├── NetworkError             (DNS, TCP, TLS, timeout, connection reset)
└── ApiError                 (HTTP response received; .status_code, .body)
    ├── RateLimitError       (HTTP 429; .retry_after_seconds)
    ├── ClientApiError       (HTTP 4xx, not 429)
    └── ServerApiError       (HTTP 5xx)
```

**Java** (`com.cc0mon.sdk.Errors`):

```
CC0MonException                  (extends RuntimeException)
├── ValidationException
├── NetworkException
└── ApiException                 (.getStatusCode(), .getBody())
    ├── RateLimitException       (.getRetryAfterSeconds())
    ├── ClientApiException
    └── ServerApiException
```

PowerShell uses a single `RuntimeException` carrying a message of the form `"HTTP <status> :: <body> :: <category>"`; `ConvertTo-Cc0ExitCode` does the mapping.

> **Important:** `RateLimitError` / `RateLimitException` is a **sibling** of `ClientApiError`, not a subclass. Catching only `ClientApiError` will let 429s fall through. The example scripts all handle both explicitly.

### Exit codes (uniform across all 30 example scripts)

| Code | Meaning |
|------|---------|
| `0` | Success |
| `1` | Unexpected internal error (catch-all) |
| `2` | Network failure — `NetworkError` |
| `3` | HTTP 4xx — `ClientApiError` (includes 429 after retries exhausted) |
| `4` | HTTP 5xx — `ServerApiError` (after retries exhausted) |
| `5` | Argument or input validation failure — `ValidationError` |

---

## Search and filtering

The SDK exposes attribute-based filtering on the registry and collector endpoints. The cc0mon API itself has no search endpoint; filters are applied **client-side** after a single GET, keeping the rate-limit footprint at one request per filter call.

### Canonical attribute sets

| Set | Count | Values |
|-----|------:|--------|
| **Energies** | 16 | Bug, Celestial, Dragon, Earth, Electric, Fire, Fossil, Ghost, Grass, Ice, Metal, Mythic, Ocean, Rock, Toxic, Underworld |
| **Rarities** | 4 | Common, Uncommon, Rare, Legendary |

Validation is case-insensitive (`"fire"` → `"Fire"`); unknown values raise `ValidationError`/`ValidationException` with a friendly listing. Scripts map this to exit code `5`. Validation runs **before** any HTTP call.

### Script flag matrix

| Script | Flags | Notes |
|--------|-------|-------|
| `cc0mon-api-get-registry.*` | `--energy <type>`, `--rarity <tier>`, `--name-contains <substr>`, `--limit <n>` | AND-combined; `--limit` applied after filtering |
| `cc0mon-api-get-registry-images.*` | `--name-contains <substr>`, `--has-image`, `--limit <n>` | `--has-image` excludes entries with `tokenId=null` (the unmapped Vilewing species) |
| `cc0mon-api-get-collector.*` | `--owned-only`, `--energy <type>`, `--rarity <tier>` | Without any filter: full summary. With any filter: only the filtered checklist items array |

Empty result is **not** an error — scripts print `[]` and exit `0`.

### SDK convenience methods

**Python:**
```python
c.find_species(energy=None, rarity=None, name_contains=None)            # → list[Species]
c.find_collector_items(address, owned_only=False, energy=None, rarity=None)  # → list[CollectorItem]
```

**Java:**
```java
List<Species> findSpecies(String energy, String rarity, String nameContains)
List<CollectorItem> findCollectorItems(String address, boolean ownedOnly, String energy, String rarity)
```

Both make a single underlying HTTP call and filter the result in memory.

### Token-level search (out of scope)

Iterating `GET /cc0mon/{id}/traits` across all 9,999 tokens at the 60 req/min rate limit would take ~167 minutes. This is not implemented and not recommended. The `/collector/{address}` endpoint and the registry filters cover the realistic use cases.

---

## Data models reference

The same logical fields appear in all three SDKs with language-appropriate naming (`snake_case` Python, `camelCase` Java records, `PascalCase` PowerShell-style on `PSCustomObject`). Every model carries a `raw` field with the original parsed JSON.

### `Token` — `/cc0mon/{id}`

| Field | Type | Notes |
|-------|------|-------|
| `id` / `id` | int | Token number 1..10000 |
| `name` | str | Display name |
| `image_svg_url` / `imageSvgUrl` | str? | SVG artwork URL |
| `image_png_url` / `imagePngUrl` | str? | PNG artwork URL |
| `owner` | str? | Current holder 0x-address |
| `raw` | dict / Map | Original parsed JSON |

### `Metadata` — `/cc0mon/{id}/metadata`

ERC-721 standard: `name`, `description`, `image`, `attributes` (list of dicts), plus `raw`.

### `Traits` — `/cc0mon/{id}/traits`

| Field | Type |
|-------|------|
| `token_id` / `tokenId` | int |
| `name` | str |
| `attributes` | list of dicts (each dict typically `{trait_type, value}`) |
| `raw` | dict / Map |

### `OwnerInfo` — `/cc0mon/{id}/owner`

`owner` (str) + `raw`.

### `Contract` — `/contract`

`name`, `symbol`, `address`, `network` (or `chain`), `total_supply` / `totalSupply` (nullable), `raw`.

### `Species` — `/registry`

| Field | Type |
|-------|------|
| `number` | int (1..260) |
| `name` | str |
| `energy` | str? (one of the 16) |
| `rarity` | str? (one of the 4) |
| `raw` | dict / Map |

### `SpeciesImage` — `/registry/images`

| Field | Type | Notes |
|-------|------|-------|
| `name` | str | Species name |
| `token_id` / `tokenId` | int? | `null` for the one unmapped species (Vilewing) — the `--has-image` filter excludes these |
| `svg_url` / `svgUrl` | str? | |
| `png_url` / `pngUrl` | str? | |
| `raw` | dict / Map | |

### `CollectorItem` — element of `Collector.checklist`

| Field | Type |
|-------|------|
| `number` | int |
| `name` | str |
| `energy` | str? |
| `rarity` | str? |
| `collected` | bool |
| `token_ids` / `tokenIds` | list[int] |
| `raw` | dict / Map |

### `Collector` — `/collector/{address}`

| Field | Type | Notes |
|-------|------|-------|
| `address` | str | Lowercased 0x address |
| `total_cc0mon` / `totalCC0mon` | int | Currently 260 |
| `collected` | int | Count of distinct species owned |
| `missing` | int | `total_cc0mon - collected` |
| `progress` | str | Formatted like `"20.0%"` |
| `total_tokens_held` / `totalTokensHeld` | int | May exceed `collected` if the wallet holds duplicates |
| `by_energy` / `byEnergy` | dict / Map | Per-energy breakdown |
| `checklist` | list[CollectorItem] | All 260 species, in registry order |
| `raw` | dict / Map | |

---

## Environment variables

| Variable | Default | Effect |
|---------|---------|--------|
| `CC0MON_LOG_LEVEL` | `INFO` | Log filtering level: `DEBUG`, `INFO`, `WARNING`, `ERROR`. Python also reads this at SDK import time when used as a library |
| `CC0MON_RUN_INTEGRATION` | unset | Set to `1` to run the live-API integration tests (Python `pytest`, Java `mvn verify`) |

---

## Testing

| Language | Framework | Scope | Files | How to run |
|----------|-----------|-------|-------|-----------|
| Python | `pytest` | 62 tests: 12 filter unit tests + 50 parametrized exit-code regression tests + 12 live-API smoke tests (gated) | `python/tests/test_filters.py`, `test_example_exit_codes.py`, `test_integration.py` | `pytest python/tests/ -q` (unit); add `CC0MON_RUN_INTEGRATION=1` for live |
| Java | JUnit 5 via `maven-failsafe-plugin` | 12 live-API smoke tests (gated) | `java/src/test/java/com/cc0mon/sdk/ClientIT.java` | `mvn verify` with `CC0MON_RUN_INTEGRATION=1` |
| PowerShell | optional Pester | Manual run-script-per-endpoint | — | — |

### Why so many Python tests?

The 50 parametrized exit-code tests are a regression guard added after the v0.1 audit: they instantiate each example script's `main()` function with a monkey-patched client that raises every SDK exception type, then assert the script exits with the spec-mandated code. This is the test that would have caught the original "no example script catches `RateLimitError`" bug.

### Running the live integration tests

```powershell
# Python
cd python
python -m pip install -e ".[test]"
$env:CC0MON_RUN_INTEGRATION = "1"
pytest tests\test_integration.py -v

# Java
cd java
$env:CC0MON_RUN_INTEGRATION = "1"
mvn verify
```

All live tests fit well under the 60 req/min rate budget (≤30 calls per full run).

---

## Building from source

### Python distribution

The Python package builds to a wheel and sdist via standard `setuptools`:

```powershell
cd python
python -m pip install build
python -m build
ls dist\
```

### Java JAR

```powershell
cd java
mvn package -DskipTests
ls target\
# → sdk-0.1.0.jar  (with Implementation-Version in MANIFEST.MF)
```

The JAR's manifest carries `Implementation-Version`, so the runtime version reported by the SDK (`Client.VERSION`) matches the artifact version rather than a hardcoded string. When the JAR is run from the IDE classpath (no manifest), `VERSION` falls back to `"0.0.0-local"`.

### PowerShell

No build step — the module is the source. The module version is held in a single `$script:ModuleVersion` variable inside `CC0MonHelpers.psm1`.

---

## Troubleshooting

| Symptom | Cause / Fix |
|---------|-------------|
| `ModuleNotFoundError: No module named 'cc0mon_sdk'` | Run `python -m pip install -e .` from the `python/` directory |
| `[ERROR] Could not find or load main class` from JBang | Either JBang isn't installed, or `mvn install` hasn't been run yet (JBang resolves the SDK from your local Maven cache) |
| `Import-Module` complains about syntax in `CC0MonHelpers.psm1` | You're on Windows PowerShell 5.1, not PowerShell 7. Run `pwsh` instead of `powershell` |
| `validation: address must match 0x[0-9a-fA-F]{40}` | The collector script accepts only 0x-prefixed 42-character hex addresses. ENS (`name.eth`) is out of scope for v0.1 |
| Script exits `3` repeatedly on a single token id | Likely a 404 (the API responds with HTTP 4xx for an out-of-range id). Token ids are `1..10000` |
| Script exits `4` and retries don't help | The API itself is returning 5xx after all retries. Check `cc0mon.com` status |
| Log file has incorrect-looking timestamps | All SDKs emit UTC (`Z` suffix) regardless of host timezone. If you see local-time-looking values, you may be on an older revision before the audit fix |
| PNG/SVG file opens corrupted | Only relevant on revisions before the audit fix. The current code reads bytes from `RawContentStream` (PowerShell) or `BodyHandlers.ofByteArray()` (Java) / `httpx.Response.content` (Python), all of which preserve binary content |
| Java `getJson` throws `NetworkException` with "invalid JSON" | The API returned a non-JSON body where JSON was expected. Capture the request URL from the log line and reproduce with `curl` for inspection |

---

## Known limitations (v0.1)

Explicitly **not** implemented in v0.1, flagged for v0.2+:

- **Async clients** (Python `httpx.AsyncClient`; Java `CompletableFuture`)
- **PyPI / Maven Central publication** — install via `pip install -e ./python` and `mvn install`
- **ETag / `If-None-Match` caching** — would cut bandwidth on the static `/registry` endpoint
- **ENS name resolution** for the collector endpoint
- **Multi-process log locking / rotation** — log files grow unbounded
- **A unified CLI tool** — one binary replacing the 10 per-action scripts
- **Multi-value OR filters** (e.g. `--energy Fire,Water`) — only single-value AND today
- **Sorting filter output** — shell tools (`jq`, `Sort-Object`) handle this cleanly
- **Streaming `/registry`** — currently returns all 260 items in one shot
- **WebSocket / streaming endpoints** — none exist in the API today
- **Generated types** from a future OpenAPI spec (the API has none today)
- **Token-level search** across all 9,999 tokens — would take ~167 minutes at the rate limit
- **Windows PowerShell 5.1** support — PS 7.x only

See `questionsandrecommendations.md` for the full list of deferred decisions, each with a stated default and a recommendation.

---

## Project documents

| File | What it contains |
|------|------------------|
| `spec.md` | Formal v0.1 specification — frozen, authoritative |
| `questionsandrecommendations.md` | 25 deferred design questions with stated defaults, rationales, and recommendations |
| `codeauditopus47.md` | Code audit identifying 1 high / 4 medium / 11 low issues and their remediations (all applied) |
| `python/README.md` | Python-specific deep dive |
| `java/README.md` | Java-specific deep dive |
| `powershell/README.md` | PowerShell-specific deep dive |

---

## License and credits

**MIT License** — see [`LICENSE`](LICENSE). You may use, modify, and redistribute this SDK freely, including in commercial products.

**The cc0mon collection itself** is dedicated to the public domain under **CC0**. All artwork, traits, and metadata are free to use without attribution.

The SDK is a community reference implementation, not an official cc0mon project. Contributions and issue reports are welcome.
