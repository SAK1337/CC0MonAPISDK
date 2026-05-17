# cc0mon API SDK — Specification

**Status:** v0.1 — initial draft, frozen for v0.1 implementation.
**Audience:** Python developers, Java developers, PowerShell users / ops.
**Scope:** A multi-language reference SDK and example-script collection for the public **cc0mon.com** NFT API.

---

## 1. Overview & goals

[CC0mon](https://cc0mon.com) is a fully on-chain creature universe with 9,999 NFTs across 260 species on Ethereum mainnet (contract `0xeeb036dbbd3039429c430657ed9836568da79d5f`). All artwork, traits, and metadata are CC0 (public domain).

This repository delivers:

- A **Python SDK** (`cc0mon_sdk`) — typed, idiomatic, sync.
- A **Java SDK** (`com.cc0mon:sdk`) — typed records, JDK 17+, zero external HTTP dependencies.
- **PowerShell** examples — cross-platform PowerShell 7.x, with a small shared helpers module.
- For each of the API's 10 endpoints: **one example script per language** (30 scripts total) demonstrating real-world usage with logging, error handling, and retries.

Primary success criteria:

1. A developer unfamiliar with the API can clone the repo, run any example script, and see a working API call within 60 seconds.
2. Every example script writes a structured log to `./cc0mon-api-<action>.log` in the user's current working directory.
3. The SDK libraries handle the API's 60 req/min rate limit transparently.
4. The SDK survives additive API changes via a `raw` escape hatch on every typed model.

Non-goals (v0.1): async clients, PyPI/Maven Central publication, ETag caching, CLI binaries, GraphQL surfaces.

---

## 2. Target audiences

| Audience | Primary deliverable | Why they care |
|----------|--------------------|--------------|
| Python developer | `cc0mon_sdk` package + 10 example `.py` scripts | Integrating cc0mon data into a Python service, notebook, or pipeline |
| Java developer | `com.cc0mon:sdk` artifact + 10 JBang `.java` scripts | Integrating cc0mon data into a JVM service or one-off tooling |
| PowerShell user | 10 standalone `.ps1` scripts + `CC0MonHelpers.psm1` | Ops automation, ad-hoc queries on Windows / Linux / macOS PS 7 |

---

## 3. API surface

- **Base URL:** `https://api.cc0mon.com`
- **Auth:** none (public)
- **Rate limit:** 60 requests per minute per IP, sliding window
- **Versioning:** unversioned (single version, no `/v1/` prefix)
- **OpenAPI spec:** **not available** — models are hand-coded

### 3.1 Endpoints

| # | Method | Path | Returns | Action slug |
|---|--------|------|---------|-------------|
| 1 | GET | `/cc0mon/{id}` | JSON: token summary (name, traits, image URLs, owner) | `get-token` |
| 2 | GET | `/cc0mon/{id}/metadata` | JSON: ERC-721 metadata (name, description, image, attributes) | `get-metadata` |
| 3 | GET | `/cc0mon/{id}/traits` | JSON: decoded trait array | `get-traits` |
| 4 | GET | `/cc0mon/{id}/image.svg` | `image/svg+xml` binary | `get-image-svg` |
| 5 | GET | `/cc0mon/{id}/image.png` | `image/png` binary | `get-image-png` |
| 6 | GET | `/cc0mon/{id}/owner` | JSON: `{"owner": "0x…"}` | `get-owner` |
| 7 | GET | `/contract` | JSON: contract details (name, symbol, address, chain) | `get-contract` |
| 8 | GET | `/registry` | JSON: array of 260 species (number, name, energy, rarity) | `get-registry` |
| 9 | GET | `/registry/images` | JSON: species → image map | `get-registry-images` |
| 10 | GET | `/collector/{address}` | JSON: wallet collection checklist | `get-collector` |

`{id}` is an integer in `1..10000`. `{address}` is a 0x-prefixed Ethereum address (42 chars).

---

## 4. Functional requirements

### 4.1 Per-language SDK

Each SDK MUST expose a `Client` type with one method per endpoint:

| Method (Python / Java) | Endpoint | Signature shape |
|-----------------------|----------|----------------|
| `get_token(id) / getToken(int id)` | 1 | returns `Token` |
| `get_metadata(id) / getMetadata(int id)` | 2 | returns `Metadata` |
| `get_traits(id) / getTraits(int id)` | 3 | returns `Traits` |
| `get_image_svg(id) / getImageSvg(int id)` | 4 | returns `bytes` / `byte[]` |
| `get_image_png(id) / getImagePng(int id)` | 5 | returns `bytes` / `byte[]` |
| `get_owner(id) / getOwner(int id)` | 6 | returns `OwnerInfo` |
| `get_contract() / getContract()` | 7 | returns `Contract` |
| `get_registry() / getRegistry()` | 8 | returns `list[Species]` / `List<Species>` |
| `get_registry_images() / getRegistryImages()` | 9 | returns `list[SpeciesImage]` / `List<SpeciesImage>` |
| `get_collector(address) / getCollector(String address)` | 10 | returns `Collector` |

Client constructor MUST accept:

- `base_url` (default `"https://api.cc0mon.com"`)
- `timeout` (default 30s)
- `retries` (default 3; `0` disables)
- `user_agent` (default `"cc0mon-sdk-<lang>/<version>"`)

### 4.2 Example scripts

For each endpoint per language, one script:

```
cc0mon-api-<action>.<py|java|ps1>
```

Required behavior of every example script:

1. Parse command-line arguments using stdlib argument parsing (Python `argparse`, Java `String[] args`, PowerShell `param()`).
2. Configure logging to `./cc0mon-api-<action>.log` (resolved against the **current working directory**, not the script's own directory).
3. Instantiate the SDK client (Python/Java) or call the API directly (PowerShell, using shared helpers).
4. Invoke the relevant action with parsed arguments.
5. On success: pretty-print the result (JSON for typed responses; resolved path for image files) to stdout and exit `0`.
6. On error: log the error with stack/context to the log file, write a one-line human-readable error to stderr, exit with the appropriate code from §8.

### 4.3 Image scripts

- `cc0mon-api-get-image-svg.<ext>` and `cc0mon-api-get-image-png.<ext>` accept:
  - `--id <n>` (required, integer 1–10000)
  - `--out <path>` (optional; default `./cc0mon-<id>.svg` or `./cc0mon-<id>.png` in CWD)
- Output file is silently overwritten if it exists.

### 4.4 Collector script

- `cc0mon-api-get-collector.<ext>` accepts `--address <0x...>` (required).
- Address is validated: must be 42 chars, starts with `0x`, hex body. SDK normalizes to lowercase before sending.
- ENS names are **out of scope** for v0.1.

---

## 5. Non-functional requirements

| Concern | Requirement |
|---------|-------------|
| Python version | **3.10+** |
| Java version | **JDK 17 LTS** |
| PowerShell version | **PowerShell 7.x** (cross-platform); Windows PowerShell 5.1 not supported in v0.1 |
| OS | Windows 10/11, macOS 12+, Linux (any glibc-2.28+) |
| Python deps | `httpx >= 0.27` (the only runtime dep) |
| Java deps | `com.fasterxml.jackson.core:jackson-databind >= 2.17` (HTTP via JDK `java.net.http`) |
| PowerShell deps | None — uses `Invoke-WebRequest`/`Invoke-RestMethod` |
| Test deps | Python: `pytest`. Java: JUnit 5 (via `maven-failsafe-plugin`). PowerShell: none required (optional Pester). |

---

## 6. Naming conventions

- **Action slugs** (kebab-case): see §3.1 table. Frozen for v0.1.
- **Script file name:** `cc0mon-api-<action>.<ext>` where `<ext>` ∈ {`py`, `java`, `ps1`}.
- **Log file name:** `cc0mon-api-<action>.log` (always, regardless of language).
- **Package / artifact names:**
  - Python distribution: `cc0mon-sdk`; importable module: `cc0mon_sdk`.
  - Java: `groupId=com.cc0mon`, `artifactId=sdk`, package `com.cc0mon.sdk`.
  - PowerShell module: `CC0MonHelpers` (file `CC0MonHelpers.psm1`).

---

## 7. Logging specification

| Item | Value |
|------|-------|
| File path | `<CWD>/cc0mon-api-<action>.log` |
| Path resolution | Python `pathlib.Path.cwd()`; Java `System.getProperty("user.dir")`; PowerShell `(Get-Location).Path` |
| Mode | **Append** (`a`) — preserves history across runs |
| Encoding | UTF-8 |
| Line format | `<ISO8601-ts> \| <LEVEL> \| <action> \| <METHOD> <URL> \| status=<code> \| latency_ms=<n> \| bytes=<n> \| <message>` |
| Default level | `INFO` |
| Override | Environment variable `CC0MON_LOG_LEVEL` ∈ {`DEBUG`, `INFO`, `WARNING`, `ERROR`} |
| What SDK logs | DEBUG: request/response details, retry attempts. INFO: retry events (when level=INFO). WARNING: rate-limit waits. ERROR: terminal failures. |
| What scripts log | INFO: script start, parsed args (no secrets), exit. ERROR: argument/validation failures. |
| Stdout/stderr | Scripts also emit a concise human-readable line to stdout on success and stderr on failure. The full record stays in the log file. |

Example log line:

```
2026-05-17T14:23:08.412Z | INFO | get-token | GET https://api.cc0mon.com/cc0mon/1 | status=200 | latency_ms=187 | bytes=842 | ok
```

Concurrent writes to the same log file from multiple processes may interleave on a per-line basis. v0.1 does not attempt cross-process locking.

---

## 8. Error model

### 8.1 Exception / error type hierarchy

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

**Java** (`com.cc0mon.sdk`):

```
CC0MonException                  (RuntimeException)
├── ValidationException
├── NetworkException
└── ApiException                 (.statusCode, .body)
    ├── RateLimitException       (.retryAfterSeconds)
    ├── ClientApiException
    └── ServerApiException
```

### 8.2 Exit codes (all example scripts)

| Code | Meaning |
|------|---------|
| `0` | Success |
| `2` | Network failure (DNS, TCP, TLS, timeout) — `NetworkError` |
| `3` | HTTP 4xx — `ClientApiError` (includes 429 after retries exhausted) |
| `4` | HTTP 5xx — `ServerApiError` (after retries exhausted) |
| `5` | Argument or input validation failure — `ValidationError` |
| `1` | Unexpected internal error (catch-all) |

---

## 9. Retry / rate-limit handling

| Item | Value |
|------|-------|
| Default retries | 3 |
| Triggers | HTTP 429, HTTP 5xx, transient network errors |
| Backoff | Exponential, base 1.0s, multiplier 2.0, cap 30s, **full jitter** (random in `[0, computed_delay]`) |
| `Retry-After` header | **Honored** when present (replaces computed backoff for that attempt) |
| Disable | Pass `retries=0` to client constructor |
| Logging | Each retry attempt logged at INFO with attempt number and sleep duration |

Pseudocode:

```
attempt = 0
while True:
    response = http_get(url)
    if response.status < 400: return response
    if attempt >= retries: raise
    if response.status not in (429, 500, 502, 503, 504): raise
    delay = retry_after_header or min(cap, base * (2 ** attempt))
    sleep(uniform(0, delay))
    attempt += 1
```

---

## 10. Image-endpoint handling

- SDK methods (`get_image_svg`, `get_image_png`, `getImageSvg`, `getImagePng`) return raw bytes.
- Example scripts save the bytes to `--out <path>`; default is `./cc0mon-<id>.<ext>`.
- Files are silently overwritten.
- The script writes the resolved output path to stdout on success.

---

## 11. Models design

Every typed response model carries a `raw` field containing the original parsed JSON (`dict` in Python, `Map<String,Object>` in Java). This is the **escape hatch** for API additions not yet reflected in the typed structure.

### 11.1 Python

```python
@dataclass(frozen=True)
class Token:
    id: int
    name: str
    image_svg_url: str
    image_png_url: str
    owner: str
    raw: dict
    @classmethod
    def from_dict(cls, d: dict) -> "Token": ...
```

### 11.2 Java

```java
public record Token(
    int id,
    String name,
    String imageSvgUrl,
    String imagePngUrl,
    String owner,
    Map<String, Object> raw
) {
    public static Token fromJson(JsonNode node) { ... }
}
```

Field names in JSON responses are converted from `snake_case` / `camelCase` as observed during integration testing. Unknown fields are ignored at the typed level but remain available via `raw`.

---

## 12. Project layout

See the plan file or the repo tree. Summary:

```
/spec.md
/questionsandrecommendations.md
/README.md
/python/cc0mon_sdk/       (library)
/python/examples/         (10 scripts)
/python/tests/            (integration)
/python/pyproject.toml
/java/src/main/java/com/cc0mon/sdk/   (library)
/java/examples/                       (10 JBang scripts)
/java/src/test/java/com/cc0mon/sdk/   (integration)
/java/pom.xml
/powershell/CC0MonHelpers.psm1
/powershell/scripts/       (10 scripts)
```

---

## 13. Build & install

### 13.1 Python

```powershell
cd python
python -m pip install -e .
python examples\cc0mon-api-get-token.py --id 1
```

### 13.2 Java

Prerequisites: JDK 17, Maven 3.9+, [JBang](https://www.jbang.dev/download/).

```powershell
cd java
mvn install -DskipTests
jbang examples\cc0mon-api-get-token.java --id 1
```

### 13.3 PowerShell

```powershell
Import-Module .\powershell\CC0MonHelpers.psm1
.\powershell\scripts\cc0mon-api-get-token.ps1 -Id 1
```

---

## 14. Testing strategy

| Language | Framework | Scope | Gate |
|----------|-----------|-------|------|
| Python | `pytest` | One smoke test per endpoint (10 total) against the live API | `CC0MON_RUN_INTEGRATION=1` |
| Java | JUnit 5 via `maven-failsafe-plugin` | One `*IT.java` smoke test class with 10 methods | `CC0MON_RUN_INTEGRATION=1` |
| PowerShell | (optional Pester) | Manual run script per endpoint | n/a |

All 30 calls in a full run consume ≤30 of the 60-per-minute rate budget. Tests run serially.

---

## 15. Out of scope (v0.1)

- Async / non-blocking clients.
- Publication to PyPI or Maven Central.
- HTTP caching, ETag, `If-None-Match`.
- Authentication (none required).
- ENS name resolution.
- Multi-process log locking / rotation.
- A unified CLI tool.
- Webhooks / streaming endpoints (none exist).

---

## 16. Search & filtering

The SDK exposes attribute-based filtering over the registry and collector endpoints. The API itself has no search endpoint; filters are applied **client-side** after a single GET. This keeps the rate-limit footprint at one request per filter call.

### 16.1 Closed value sets

The cc0mon attribute space is fixed:

| Set | Values | Source |
|-----|--------|--------|
| Energy (16) | Bug, Celestial, Dragon, Earth, Electric, Fire, Fossil, Ghost, Grass, Ice, Metal, Mythic, Ocean, Rock, Toxic, Underworld | `/registry` |
| Rarity (4) | Common, Uncommon, Rare, Legendary | `/registry` |

These are exported as constants in each language: `cc0mon_sdk.ENERGIES`/`RARITIES` (Python), `com.cc0mon.sdk.Models.ENERGIES`/`RARITIES` (Java), `Get-Cc0Energies`/`Get-Cc0Rarities` (PowerShell).

### 16.2 Validation

Energy and rarity inputs are matched case-insensitively against the canonical sets and normalized to canonical casing (`"fire"` → `"Fire"`). Unknown values raise `ValidationError` / `ValidationException` whose message lists the valid options. Scripts map this to exit code `5`.

### 16.3 Script flag matrix

| Script | New flags | Notes |
|--------|-----------|-------|
| `cc0mon-api-get-registry.<ext>` | `--energy <type>`, `--rarity <tier>`, `--name-contains <substring>` | All AND-combined; `--limit` applied after filtering. |
| `cc0mon-api-get-registry-images.<ext>` | `--name-contains <substring>`, `--has-image` | `--has-image` excludes entries with `tokenId=null` (the unmapped Vilewing species). |
| `cc0mon-api-get-collector.<ext>` | `--owned-only`, `--energy <type>`, `--rarity <tier>` | Without any filter, the full collector summary (progress, byEnergy, checklist) is printed. With any filter, only the filtered checklist items array is printed. |

Empty result is **not** an error: scripts print `[]` and exit `0`.

### 16.4 SDK convenience methods

In addition to script flags, the Python and Java SDK libraries expose:

**Python** (`cc0mon_sdk.Client`):

```python
client.find_species(energy=None, rarity=None, name_contains=None) -> list[Species]
client.find_collector_items(address, owned_only=False, energy=None, rarity=None) -> list[CollectorItem]
```

**Java** (`com.cc0mon.sdk.Client`):

```java
List<Species> findSpecies(String energy, String rarity, String nameContains)
List<CollectorItem> findCollectorItems(String address, boolean ownedOnly, String energy, String rarity)
```

Both make a single underlying HTTP call (to `/registry` or `/collector/{address}`) and filter the result in memory.

### 16.5 Token-level search (out of scope)

Searching for tokens matching a trait across the full 9,999-token set would require iterating `GET /cc0mon/{id}/traits` — roughly 167 minutes at the 60 req/min rate limit. This is not implemented and is not recommended for typical use. The `/collector/{address}` endpoint and the registry filters cover the realistic use cases.

---

## 17. Glossary

| Term | Definition |
|------|------------|
| **cc0mon** | "CC0 monster" — the NFT collection at cc0mon.com |
| **CC0** | Creative Commons "No Rights Reserved" — full public domain dedication |
| **Action** | A user-visible operation, mapped to one endpoint; one of the 10 action slugs |
| **JBang** | A Java tool for running `.java` files as single-file scripts with declared dependencies |
| **Raw escape hatch** | The `raw` field on every typed model carrying the original parsed JSON |
