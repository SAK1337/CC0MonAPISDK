# cc0mon SDK — Python

A typed, synchronous Python SDK for the cc0mon.com NFT API.

- **Python:** 3.10+
- **HTTP:** `httpx >= 0.27` (the only runtime dependency)
- **Models:** frozen `@dataclass` with a `raw: dict` escape hatch on every type
- **Retries:** on by default (429 + 5xx, exponential backoff, `Retry-After` honored)

## Install

```powershell
cd python
python -m pip install -e .
```

The `-e` flag installs in editable mode so SDK edits are picked up immediately.

## Hello world

```python
from cc0mon_sdk import Client

with Client() as c:
    token = c.get_token(1)
    print(token.name, token.owner)
    contract = c.get_contract()
    print(contract.address, contract.network)
```

## Example scripts

10 standalone CLI scripts live under `examples/`. Each writes to `./cc0mon-api-<action>.log` in your current working directory.

| Script | Args | What it prints |
|--------|------|----------------|
| `cc0mon-api-get-token.py` | `--id <n>` | Token summary as JSON |
| `cc0mon-api-get-metadata.py` | `--id <n>` | ERC-721 metadata |
| `cc0mon-api-get-traits.py` | `--id <n>` | Decoded traits |
| `cc0mon-api-get-image-svg.py` | `--id <n> [--out path]` | Resolved output path |
| `cc0mon-api-get-image-png.py` | `--id <n> [--out path]` | Resolved output path |
| `cc0mon-api-get-owner.py` | `--id <n>` | `{ "owner": "0x…" }` |
| `cc0mon-api-get-contract.py` | (none) | Contract metadata |
| `cc0mon-api-get-registry.py` | `[--limit n]` | Species list |
| `cc0mon-api-get-registry-images.py` | `[--limit n]` | Species image URLs |
| `cc0mon-api-get-collector.py` | `--address 0x…` | Wallet checklist |

```powershell
python examples\cc0mon-api-get-token.py --id 1
python examples\cc0mon-api-get-image-png.py --id 42 --out .\my-mon.png
python examples\cc0mon-api-get-collector.py --address 0x0000000000000000000000000000000000000000
```

## Logging

Every script writes append-only to `./cc0mon-api-<action>.log` in the current working directory (not the script's directory). Default level is `INFO`; set `CC0MON_LOG_LEVEL=DEBUG` for request/response detail.

```powershell
$env:CC0MON_LOG_LEVEL = "DEBUG"
python examples\cc0mon-api-get-token.py --id 1
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

Three scripts accept filter flags; the SDK also exposes `Client.find_species()` and `Client.find_collector_items()`. Filters are AND-combined, validated against the canonical 16 energies × 4 rarities, and applied client-side.

```powershell
python examples\cc0mon-api-get-registry.py --energy Fire --rarity Common
python examples\cc0mon-api-get-registry-images.py --name-contains drill --has-image
python examples\cc0mon-api-get-collector.py --address 0xB07952A55bF9c45C268F37C3631823Df50ac721a --owned-only --energy Fire
```

```python
from cc0mon_sdk import Client, ENERGIES

with Client() as c:
    fire_common = c.find_species(energy="Fire", rarity="Common")
    print([s.name for s in fire_common])
    print("valid energies:", sorted(ENERGIES))
```

Invalid energy/rarity raises `ValidationError` (script exit `5`) with a message listing valid values.

## Customizing the client

```python
from cc0mon_sdk import Client

with Client(timeout=10, retries=0, user_agent="my-app/1.0") as c:
    c.get_token(1)
```

## Running integration tests

```powershell
python -m pip install -e ".[test]"
$env:CC0MON_RUN_INTEGRATION = "1"
pytest tests\test_integration.py -v
```

Tests hit the live API; the env-var gate prevents accidental runs in unrelated CI. All 10 tests fit well under the 60 req/min rate limit.

## Error handling

```python
from cc0mon_sdk import Client, RateLimitError, ApiError, NetworkError, ValidationError

try:
    with Client() as c:
        token = c.get_token(99999)
except ValidationError as e:
    print(f"bad input: {e}")
except RateLimitError as e:
    print(f"rate limited; retry after {e.retry_after_seconds}s")
except ApiError as e:
    print(f"API said {e.status_code}: {e.body}")
except NetworkError as e:
    print(f"transport problem: {e}")
```
