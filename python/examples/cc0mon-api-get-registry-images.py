"""cc0mon-api-get-registry-images — list and filter species image URLs.

Usage::

    python cc0mon-api-get-registry-images.py
    python cc0mon-api-get-registry-images.py --limit 10
    python cc0mon-api-get-registry-images.py --name-contains stryx
    python cc0mon-api-get-registry-images.py --has-image
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

ACTION = "get-registry-images"


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description="List cc0mon species image URLs, optionally filtered.")
    parser.add_argument("--limit", type=int, default=None, help="Show only the first N entries (default: all)")
    parser.add_argument("--name-contains", dest="name_contains", default=None, help="Case-insensitive substring match on species name")
    parser.add_argument("--has-image", dest="has_image", action="store_true", help="Skip species without a mapped token (tokenId=null)")
    args = parser.parse_args(argv)

    logger = configure_logger(ACTION)
    logger.info("script start limit=%s name_contains=%s has_image=%s", args.limit, args.name_contains, args.has_image)

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

    filtered = images
    if args.has_image:
        filtered = [i for i in filtered if i.token_id is not None]
    if args.name_contains:
        needle = args.name_contains.lower()
        filtered = [i for i in filtered if needle in (i.name or "").lower()]

    shown = filtered[: args.limit] if args.limit else filtered
    print(json.dumps([dataclasses.asdict(i) for i in shown], indent=2, default=str))
    logger.info("script exit code=0 total=%d matched=%d shown=%d", len(images), len(filtered), len(shown))
    return 0


if __name__ == "__main__":
    sys.exit(main())
