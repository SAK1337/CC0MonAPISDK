"""cc0mon-api-get-registry — list and filter species in the registry.

Usage::

    python cc0mon-api-get-registry.py
    python cc0mon-api-get-registry.py --limit 5
    python cc0mon-api-get-registry.py --energy Fire --rarity Common
    python cc0mon-api-get-registry.py --name-contains drill

Filters are AND-combined; --limit applies after filtering. Validation against
the canonical energy/rarity sets happens before any HTTP call.
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
    ServerApiError,
    ValidationError,
    configure_logger,
)

ACTION = "get-registry"


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(
        description="List cc0mon species (registry), optionally filtered.",
        epilog=(
            f"Valid --energy values: {', '.join(sorted(ENERGIES))}\n"
            f"Valid --rarity values: {', '.join(sorted(RARITIES))}"
        ),
        formatter_class=argparse.RawDescriptionHelpFormatter,
    )
    parser.add_argument("--limit", type=int, default=None, help="Show only the first N entries (default: all)")
    parser.add_argument("--energy", default=None, help="Filter by energy type (case-insensitive)")
    parser.add_argument("--rarity", default=None, help="Filter by rarity tier (case-insensitive)")
    parser.add_argument("--name-contains", dest="name_contains", default=None, help="Case-insensitive substring match on species name")
    args = parser.parse_args(argv)

    logger = configure_logger(ACTION)
    logger.info(
        "script start limit=%s energy=%s rarity=%s name_contains=%s",
        args.limit, args.energy, args.rarity, args.name_contains,
    )

    try:
        with Client() as client:
            species = client.find_species(
                energy=args.energy,
                rarity=args.rarity,
                name_contains=args.name_contains,
            )
    except ValidationError as e:
        logger.error("validation: %s", e)
        print(f"validation error: {e}", file=sys.stderr)
        return 5
    except NetworkError as e:
        logger.error("network: %s", e)
        print(f"network error: {e}", file=sys.stderr)
        return 2
    except ClientApiError as e:
        logger.error("http %d: %s", e.status_code, e.body[:200])
        print(f"client error HTTP {e.status_code}: {e.body[:200]}", file=sys.stderr)
        return 3
    except ServerApiError as e:
        logger.error("http %d: %s", e.status_code, e.body[:200])
        print(f"server error HTTP {e.status_code}: {e.body[:200]}", file=sys.stderr)
        return 4

    shown = species[: args.limit] if args.limit else species
    print(json.dumps([dataclasses.asdict(s) for s in shown], indent=2, default=str))
    logger.info("script exit code=0 matched=%d shown=%d", len(species), len(shown))
    return 0


if __name__ == "__main__":
    sys.exit(main())
