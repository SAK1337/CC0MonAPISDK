"""cc0mon-api-get-collector — fetch a wallet's collection checklist.

Usage::

    python cc0mon-api-get-collector.py --address 0xabc...def

Only 0x-prefixed hex addresses are accepted (no ENS).
"""
from __future__ import annotations

import argparse
import dataclasses
import json
import sys

from cc0mon_sdk import (
    Client,
    ClientApiError,
    NetworkError,
    ServerApiError,
    ValidationError,
    configure_logger,
)

ACTION = "get-collector"


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description="Fetch a wallet's cc0mon collection checklist.")
    parser.add_argument("--address", required=True, help="Ethereum 0x-address (42 chars)")
    args = parser.parse_args(argv)

    logger = configure_logger(ACTION)
    logger.info("script start address=%s", args.address)

    try:
        with Client() as client:
            collector = client.get_collector(args.address)
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

    print(json.dumps(dataclasses.asdict(collector), indent=2, default=str))
    logger.info(
        "script exit code=0 progress_keys=%d items=%d",
        len(collector.progress),
        len(collector.items),
    )
    return 0


if __name__ == "__main__":
    sys.exit(main())
