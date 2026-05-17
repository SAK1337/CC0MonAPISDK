"""cc0mon-api-get-registry-images — list pre-mapped species image URLs.

Usage::

    python cc0mon-api-get-registry-images.py
    python cc0mon-api-get-registry-images.py --limit 10
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

ACTION = "get-registry-images"


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description="List cc0mon species image URLs.")
    parser.add_argument("--limit", type=int, default=None, help="Show only the first N entries (default: all)")
    args = parser.parse_args(argv)

    logger = configure_logger(ACTION)
    logger.info("script start limit=%s", args.limit)

    try:
        with Client() as client:
            images = client.get_registry_images()
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

    shown = images[: args.limit] if args.limit else images
    print(json.dumps([dataclasses.asdict(i) for i in shown], indent=2, default=str))
    logger.info("script exit code=0 total=%d shown=%d", len(images), len(shown))
    return 0


if __name__ == "__main__":
    sys.exit(main())
