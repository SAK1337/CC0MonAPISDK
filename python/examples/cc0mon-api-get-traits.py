"""cc0mon-api-get-traits — fetch decoded traits for a single token.

Usage::

    python cc0mon-api-get-traits.py --id 1
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

ACTION = "get-traits"


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description="Fetch decoded traits for a cc0mon token.")
    parser.add_argument("--id", dest="token_id", type=int, required=True, help="Token id (1-10000)")
    args = parser.parse_args(argv)

    logger = configure_logger(ACTION)
    logger.info("script start id=%d", args.token_id)

    try:
        with Client() as client:
            traits = client.get_traits(args.token_id)
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

    print(json.dumps(dataclasses.asdict(traits), indent=2, default=str))
    logger.info("script exit code=0 attributes=%d", len(traits.attributes))
    return 0


if __name__ == "__main__":
    sys.exit(main())
