"""Logging configuration for the cc0mon SDK and its example scripts.

Each example script calls :func:`configure_logger` once at startup with
its action slug. The returned logger writes to ``./cc0mon-api-<action>.log``
in the current working directory.
"""
from __future__ import annotations

import logging
import os
import time
from pathlib import Path

LOG_FORMAT = (
    "%(asctime)s.%(msecs)03dZ | %(levelname)s | %(action)s | %(message)s"
)
DATE_FORMAT = "%Y-%m-%dT%H:%M:%S"


def _make_utc_formatter() -> logging.Formatter:
    # Per-instance converter so we don't flip every Formatter in the process.
    fmt = logging.Formatter(LOG_FORMAT, datefmt=DATE_FORMAT)
    fmt.converter = time.gmtime
    return fmt


class _ActionFilter(logging.Filter):
    """Inject the action slug into every record so the format string works."""

    def __init__(self, action: str) -> None:
        super().__init__()
        self._action = action

    def filter(self, record: logging.LogRecord) -> bool:
        if not hasattr(record, "action"):
            record.action = self._action
        return True


def configure_logger(action: str) -> logging.Logger:
    """Return a logger that appends to ``./cc0mon-api-<action>.log``.

    Honors the ``CC0MON_LOG_LEVEL`` environment variable. Calling this
    function multiple times for the same action is safe; handlers are
    not duplicated.
    """
    level_name = os.environ.get("CC0MON_LOG_LEVEL", "INFO").upper()
    level = getattr(logging, level_name, logging.INFO)

    log_path = Path.cwd() / f"cc0mon-api-{action}.log"

    logger = logging.getLogger(f"cc0mon_sdk.{action}")
    logger.setLevel(level)
    logger.propagate = False

    already = any(
        isinstance(h, logging.FileHandler) and getattr(h, "_cc0mon_path", None) == str(log_path)
        for h in logger.handlers
    )
    if not already:
        handler = logging.FileHandler(log_path, mode="a", encoding="utf-8")
        handler._cc0mon_path = str(log_path)  # type: ignore[attr-defined]
        handler.setLevel(level)
        handler.setFormatter(_make_utc_formatter())
        handler.addFilter(_ActionFilter(action))
        logger.addHandler(handler)

    # Also propagate SDK-internal logs to this file at DEBUG when enabled.
    sdk_logger = logging.getLogger("cc0mon_sdk")
    sdk_logger.setLevel(level)
    if not any(
        isinstance(h, logging.FileHandler) and getattr(h, "_cc0mon_path", None) == str(log_path)
        for h in sdk_logger.handlers
    ):
        sdk_handler = logging.FileHandler(log_path, mode="a", encoding="utf-8")
        sdk_handler._cc0mon_path = str(log_path)  # type: ignore[attr-defined]
        sdk_handler.setLevel(level)
        sdk_handler.setFormatter(_make_utc_formatter())
        sdk_handler.addFilter(_ActionFilter(action))
        sdk_logger.addHandler(sdk_handler)
        sdk_logger.propagate = False

    return logger
