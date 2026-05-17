"""Synchronous HTTP client for the cc0mon API.

The client encapsulates request building, retry/backoff for ``429`` and
``5xx``, and conversion of JSON responses into typed model objects.
"""
from __future__ import annotations

import logging
import random
import re
import time
from typing import Any, Iterable

import httpx

from .errors import (
    ApiError,
    ClientApiError,
    NetworkError,
    RateLimitError,
    ServerApiError,
    ValidationError,
)
from .models import (
    Collector,
    CollectorItem,
    Contract,
    Metadata,
    OwnerInfo,
    Species,
    SpeciesImage,
    Token,
    Traits,
    validate_energy,
    validate_rarity,
)

DEFAULT_BASE_URL = "https://api.cc0mon.com"
DEFAULT_TIMEOUT = 30.0
DEFAULT_RETRIES = 3
DEFAULT_BACKOFF_BASE = 1.0
DEFAULT_BACKOFF_CAP = 30.0
RETRY_STATUSES: frozenset[int] = frozenset({429, 500, 502, 503, 504})
ETH_ADDRESS_RE = re.compile(r"^0x[0-9a-fA-F]{40}$")

_log = logging.getLogger("cc0mon_sdk")
__version__ = "0.1.0"


def _validate_token_id(token_id: int) -> int:
    """Return ``token_id`` if it is a valid cc0mon id, else raise."""
    if not isinstance(token_id, int) or isinstance(token_id, bool):
        raise ValidationError(f"token id must be int, got {type(token_id).__name__}")
    if token_id < 1 or token_id > 10000:
        raise ValidationError(f"token id must be in 1..10000, got {token_id}")
    return token_id


def _normalize_address(address: str) -> str:
    """Validate and lowercase an Ethereum 0x-address."""
    if not isinstance(address, str) or not ETH_ADDRESS_RE.match(address):
        raise ValidationError(f"address must match 0x[0-9a-fA-F]{{40}}, got {address!r}")
    return address.lower()


def _parse_retry_after(value: str | None) -> float | None:
    """Parse a ``Retry-After`` header value as seconds (best effort)."""
    if not value:
        return None
    try:
        return max(0.0, float(value))
    except ValueError:
        return None  # HTTP-date form is not honored in v0.1


class Client:
    """A synchronous client for the cc0mon HTTP API.

    Parameters
    ----------
    base_url:
        Override the API root. Defaults to ``https://api.cc0mon.com``.
    timeout:
        Per-request timeout in seconds. Defaults to 30.
    retries:
        Number of retry attempts on 429 / 5xx / transient network errors.
        Pass ``0`` to disable retries.
    user_agent:
        Custom ``User-Agent`` header. Defaults to ``cc0mon-sdk-python/<version>``.
    """

    def __init__(
        self,
        base_url: str = DEFAULT_BASE_URL,
        timeout: float = DEFAULT_TIMEOUT,
        retries: int = DEFAULT_RETRIES,
        user_agent: str | None = None,
    ) -> None:
        self.base_url = base_url.rstrip("/")
        self.timeout = timeout
        self.retries = max(0, int(retries))
        self.user_agent = user_agent or f"cc0mon-sdk-python/{__version__}"
        self._client = httpx.Client(
            timeout=timeout,
            headers={"User-Agent": self.user_agent, "Accept": "application/json"},
        )

    # Context-manager support so users can `with Client() as c: ...`
    def __enter__(self) -> "Client":
        return self

    def __exit__(self, *_exc: object) -> None:
        self.close()

    def close(self) -> None:
        self._client.close()

    # ---------- HTTP plumbing ----------

    def _request(self, path: str, accept: str = "application/json") -> httpx.Response:
        url = f"{self.base_url}{path}"
        attempt = 0
        while True:
            start = time.monotonic()
            try:
                response = self._client.get(url, headers={"Accept": accept})
            except httpx.TimeoutException as e:
                self._log_event("GET", url, status=None, latency=time.monotonic() - start, body_bytes=0, msg=f"timeout: {e}")
                if attempt >= self.retries:
                    raise NetworkError(f"timeout after {self.retries + 1} attempts: {e}") from e
                self._sleep_for_retry(attempt, None)
                attempt += 1
                continue
            except httpx.HTTPError as e:
                self._log_event("GET", url, status=None, latency=time.monotonic() - start, body_bytes=0, msg=f"network: {e}")
                if attempt >= self.retries:
                    raise NetworkError(f"network error after {self.retries + 1} attempts: {e}") from e
                self._sleep_for_retry(attempt, None)
                attempt += 1
                continue

            latency = time.monotonic() - start
            body_bytes = len(response.content)
            self._log_event("GET", url, response.status_code, latency, body_bytes, msg="ok" if response.is_success else "http error")

            if response.is_success:
                return response

            if response.status_code in RETRY_STATUSES and attempt < self.retries:
                self._sleep_for_retry(attempt, _parse_retry_after(response.headers.get("Retry-After")))
                attempt += 1
                continue

            self._raise_for_status(response)

    @staticmethod
    def _log_event(method: str, url: str, status: int | None, latency: float, body_bytes: int, msg: str) -> None:
        _log.info(
            "%s %s | status=%s | latency_ms=%d | bytes=%d | %s",
            method,
            url,
            "-" if status is None else status,
            int(latency * 1000),
            body_bytes,
            msg,
        )

    def _sleep_for_retry(self, attempt: int, retry_after: float | None) -> None:
        if retry_after is not None:
            sleep_for = retry_after
        else:
            backoff = min(DEFAULT_BACKOFF_CAP, DEFAULT_BACKOFF_BASE * (2 ** attempt))
            sleep_for = random.uniform(0.0, backoff)
        _log.info("retry attempt=%d sleeping=%.2fs", attempt + 1, sleep_for)
        time.sleep(sleep_for)

    @staticmethod
    def _raise_for_status(response: httpx.Response) -> None:
        status = response.status_code
        body = response.text
        if status == 429:
            raise RateLimitError(status, body, _parse_retry_after(response.headers.get("Retry-After")))
        if 400 <= status < 500:
            raise ClientApiError(status, body)
        if 500 <= status < 600:
            raise ServerApiError(status, body)
        raise ApiError(status, body)

    # ---------- JSON helpers ----------

    def _get_json(self, path: str) -> Any:
        response = self._request(path, accept="application/json")
        return response.json()

    def _get_bytes(self, path: str, accept: str) -> bytes:
        response = self._request(path, accept=accept)
        return response.content

    # ---------- Public methods (one per endpoint) ----------

    def get_token(self, token_id: int) -> Token:
        _validate_token_id(token_id)
        return Token.from_dict(self._get_json(f"/cc0mon/{token_id}"))

    def get_metadata(self, token_id: int) -> Metadata:
        _validate_token_id(token_id)
        return Metadata.from_dict(self._get_json(f"/cc0mon/{token_id}/metadata"))

    def get_traits(self, token_id: int) -> Traits:
        _validate_token_id(token_id)
        return Traits.from_dict(self._get_json(f"/cc0mon/{token_id}/traits"))

    def get_image_svg(self, token_id: int) -> bytes:
        _validate_token_id(token_id)
        return self._get_bytes(f"/cc0mon/{token_id}/image.svg", accept="image/svg+xml")

    def get_image_png(self, token_id: int) -> bytes:
        _validate_token_id(token_id)
        return self._get_bytes(f"/cc0mon/{token_id}/image.png", accept="image/png")

    def get_owner(self, token_id: int) -> OwnerInfo:
        _validate_token_id(token_id)
        return OwnerInfo.from_dict(self._get_json(f"/cc0mon/{token_id}/owner"))

    def get_contract(self) -> Contract:
        return Contract.from_dict(self._get_json("/contract"))

    def get_registry(self) -> list[Species]:
        data = self._get_json("/registry")
        if isinstance(data, list):
            items: Iterable[dict[str, Any]] = data
        else:
            items = data.get("cc0mon") or data.get("species") or data.get("items") or []
        return [Species.from_dict(item) for item in items]

    def get_registry_images(self) -> list[SpeciesImage]:
        data = self._get_json("/registry/images")
        if isinstance(data, list):
            items: Iterable[dict[str, Any]] = data
        else:
            images = data.get("images")
            if isinstance(images, dict):
                items = (v for v in images.values() if isinstance(v, dict))
            elif isinstance(images, list):
                items = images
            else:
                items = data.get("species", data.get("items", []))
        return [SpeciesImage.from_dict(item) for item in items]

    def get_collector(self, address: str) -> Collector:
        normalized = _normalize_address(address)
        return Collector.from_dict(self._get_json(f"/collector/{normalized}"))

    # ---------- Convenience search methods (client-side filters) ----------

    def find_species(
        self,
        energy: str | None = None,
        rarity: str | None = None,
        name_contains: str | None = None,
    ) -> list[Species]:
        """Return species from ``/registry`` filtered by energy, rarity, and/or name substring.

        All filters are AND-combined. Energy and rarity are validated against the
        canonical sets in :mod:`cc0mon_sdk.models`. Name matching is case-insensitive.
        """
        species = self.get_registry()
        if energy is not None:
            e = validate_energy(energy)
            species = [s for s in species if s.energy == e]
        if rarity is not None:
            r = validate_rarity(rarity)
            species = [s for s in species if s.rarity == r]
        if name_contains:
            needle = name_contains.lower()
            species = [s for s in species if needle in (s.name or "").lower()]
        return species

    def find_collector_items(
        self,
        address: str,
        owned_only: bool = False,
        energy: str | None = None,
        rarity: str | None = None,
    ) -> list[CollectorItem]:
        """Return checklist items for an address, filtered by ownership/energy/rarity."""
        collector = self.get_collector(address)
        items = collector.checklist
        if owned_only:
            items = [i for i in items if i.collected]
        if energy is not None:
            e = validate_energy(energy)
            items = [i for i in items if i.energy == e]
        if rarity is not None:
            r = validate_rarity(rarity)
            items = [i for i in items if i.rarity == r]
        return items
