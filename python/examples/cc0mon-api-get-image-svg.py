"""cc0mon-api-get-image-svg — download the SVG artwork for a token.

Usage::

    python cc0mon-api-get-image-svg.py --id 1
    python cc0mon-api-get-image-svg.py --id 1 --out .\\my-mon.svg

Output is silently overwritten if it exists.
"""
from __future__ import annotations

import argparse
import sys
from pathlib import Path

from cc0mon_sdk import (
    Client,
    ClientApiError,
    NetworkError,
    ServerApiError,
    ValidationError,
    configure_logger,
)

ACTION = "get-image-svg"


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description="Download a cc0mon SVG image.")
    parser.add_argument("--id", dest="token_id", type=int, required=True, help="Token id (1-10000)")
    parser.add_argument("--out", dest="out_path", type=Path, default=None, help="Output path (default ./cc0mon-<id>.svg)")
    args = parser.parse_args(argv)

    out_path = args.out_path or Path.cwd() / f"cc0mon-{args.token_id}.svg"

    logger = configure_logger(ACTION)
    logger.info("script start id=%d out=%s", args.token_id, out_path)

    try:
        with Client() as client:
            svg_bytes = client.get_image_svg(args.token_id)
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

    out_path.write_bytes(svg_bytes)
    print(out_path.resolve())
    logger.info("script exit code=0 bytes=%d path=%s", len(svg_bytes), out_path)
    return 0


if __name__ == "__main__":
    sys.exit(main())
