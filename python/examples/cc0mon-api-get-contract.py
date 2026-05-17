"""cc0mon-api-get-contract — fetch smart-contract metadata.

Usage::

    python cc0mon-api-get-contract.py
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
    RateLimitError,
    ServerApiError,
    ValidationError,
    configure_logger,
)

ACTION = "get-contract"


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description="Fetch cc0mon smart-contract metadata.")
    parser.parse_args(argv)  # no arguments; for --help symmetry

    logger = configure_logger(ACTION)
    logger.info("script start")

    try:
        with Client() as client:
            contract = client.get_contract()
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

    print(json.dumps(dataclasses.asdict(contract), indent=2, default=str))
    logger.info("script exit code=0 address=%s", contract.address)
    return 0


if __name__ == "__main__":
    sys.exit(main())
