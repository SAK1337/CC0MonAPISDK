"""Exception hierarchy for the cc0mon SDK.

All SDK exceptions inherit from :class:`CC0MonError`, so callers can
catch the family with one ``except`` clause.
"""
from __future__ import annotations


class CC0MonError(Exception):
    """Base class for every error raised by the cc0mon SDK."""


class ValidationError(CC0MonError):
    """Raised before any HTTP request when arguments fail local validation."""


class NetworkError(CC0MonError):
    """DNS, TCP, TLS, timeout, or any other transport-level failure."""


class ApiError(CC0MonError):
    """An HTTP response was received but indicated failure (status >= 400)."""

    def __init__(self, status_code: int, body: str, message: str | None = None) -> None:
        self.status_code = status_code
        self.body = body
        super().__init__(message or f"HTTP {status_code}: {body[:200]}")


class RateLimitError(ApiError):
    """HTTP 429. ``retry_after_seconds`` is parsed from the response header when present."""

    def __init__(self, status_code: int, body: str, retry_after_seconds: float | None) -> None:
        self.retry_after_seconds = retry_after_seconds
        super().__init__(status_code, body, message=f"Rate limited (HTTP {status_code})")


class ClientApiError(ApiError):
    """HTTP 4xx other than 429."""


class ServerApiError(ApiError):
    """HTTP 5xx."""
