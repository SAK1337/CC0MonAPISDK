"""cc0mon-api-get-collector — fetch and optionally filter a wallet's collection.

Usage::

    python cc0mon-api-get-collector.py --address 0x...
    python cc0mon-api-get-collector.py --address 0x... --owned-only
    python cc0mon-api-get-collector.py --address 0x... --owned-only --energy Fire

Without any filter flag, the full collector summary (progress, byEnergy,
checklist) is printed. When any filter is supplied, only the filtered
checklist items array is printed.

Only 0x-prefixed hex addresses are accepted (no ENS).
"""
from __future__ import annotations

import argparse
import dataclasses
import json
import sys

from cc0mon_sdk import (
    ENERGIES,
    RARITIES,
    Client,
    ClientApiError,
    NetworkError,
    RateLimitError,
    ServerApiError,
    ValidationError,
    configure_logger,
)

ACTION = "get-collector"


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(
        description="Fetch a wallet's cc0mon collection checklist, optionally filtered.",
        epilog=(
            f"Valid --energy values: {', '.join(sorted(ENERGIES))}\n"
            f"Valid --rarity values: {', '.join(sorted(RARITIES))}"
        ),
        formatter_class=argparse.RawDescriptionHelpFormatter,
    )
    parser.add_argument("--address", required=True, help="Ethereum 0x-address (42 chars)")
    parser.add_argument("--owned-only", dest="owned_only", action="store_true", help="Show only collected items")
    parser.add_argument("--energy", default=None, help="Filter checklist items by energy")
    parser.add_argument("--rarity", default=None, help="Filter checklist items by rarity")
    args = parser.parse_args(argv)

    logger = configure_logger(ACTION)
    logger.info(
        "script start address=%s owned_only=%s energy=%s rarity=%s",
        args.address, args.owned_only, args.energy, args.rarity,
    )

    has_filter = args.owned_only or args.energy or args.rarity

    try:
        with Client() as client:
            if has_filter:
                items = client.find_collector_items(
                    args.address,
                    owned_only=args.owned_only,
                    energy=args.energy,
                    rarity=args.rarity,
                )
            else:
                collector = client.get_collector(args.address)
    except ValidationError as e:
        logger.error("validation: %s", e)
        print(f"validation error: {e}", file=sys.stderr)
        return 5
    except NetworkError as e:
        logger.error("network: %s", e)
        print(f"network error: {e}", file=sys.stderr)
        return 2
    except RateLimitError as e:
        logger.error("rate limited http %d: %s", e.status_code, e.body[:200])
        print(f"rate limited HTTP {e.status_code}: {e.body[:200]}", file=sys.stderr)
        return 3
    except ClientApiError as e:
        logger.error("http %d: %s", e.status_code, e.body[:200])
        print(f"client error HTTP {e.status_code}: {e.body[:200]}", file=sys.stderr)
        return 3
    except ServerApiError as e:
        logger.error("http %d: %s", e.status_code, e.body[:200])
        print(f"server error HTTP {e.status_code}: {e.body[:200]}", file=sys.stderr)
        return 4

    if has_filter:
        print(json.dumps([dataclasses.asdict(i) for i in items], indent=2, default=str))
        logger.info("script exit code=0 matched=%d", len(items))
    else:
        out = dataclasses.asdict(collector)
        print(json.dumps(out, indent=2, default=str))
        logger.info(
            "script exit code=0 collected=%d missing=%d total_tokens_held=%d",
            collector.collected, collector.missing, collector.total_tokens_held,
        )
    return 0


if __name__ == "__main__":
    sys.exit(main())
