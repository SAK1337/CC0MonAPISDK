"""Typed response models for the cc0mon API.

Every model carries a ``raw`` dict containing the original parsed JSON
so callers can read fields the SDK does not yet know about.
"""
from __future__ import annotations

from dataclasses import dataclass, field
from typing import Any


def _get(d: dict, *keys: str, default: Any = None) -> Any:
    """Return ``d[k]`` for the first key present, else ``default``.

    Tolerates the API switching between snake_case / camelCase keys.
    """
    for k in keys:
        if k in d:
            return d[k]
    return default


@dataclass(frozen=True)
class Token:
    id: int
    name: str
    image_svg_url: str | None
    image_png_url: str | None
    owner: str | None
    raw: dict[str, Any] = field(default_factory=dict)

    @classmethod
    def from_dict(cls, d: dict[str, Any]) -> "Token":
        images = d.get("images") or {}
        return cls(
            id=int(_get(d, "tokenId", "id", "number", default=0)),
            name=str(_get(d, "name", default="")),
            image_svg_url=_get(d, "imageSvg", "image_svg_url") or _get(images, "svg"),
            image_png_url=_get(d, "imagePng", "image_png_url") or _get(images, "png"),
            owner=_get(d, "owner", "holder"),
            raw=d,
        )


@dataclass(frozen=True)
class Metadata:
    name: str
    description: str | None
    image: str | None
    attributes: list[dict[str, Any]]
    raw: dict[str, Any] = field(default_factory=dict)

    @classmethod
    def from_dict(cls, d: dict[str, Any]) -> "Metadata":
        return cls(
            name=str(_get(d, "name", default="")),
            description=_get(d, "description"),
            image=_get(d, "image"),
            attributes=list(_get(d, "attributes", default=[]) or []),
            raw=d,
        )


@dataclass(frozen=True)
class Traits:
    token_id: int
    name: str
    attributes: list[dict[str, Any]]
    raw: dict[str, Any] = field(default_factory=dict)

    @classmethod
    def from_dict(cls, d: dict[str, Any]) -> "Traits":
        return cls(
            token_id=int(_get(d, "tokenId", "id", default=0)),
            name=str(_get(d, "name", default="")),
            attributes=list(_get(d, "attributes", "traits", default=[]) or []),
            raw=d,
        )


@dataclass(frozen=True)
class OwnerInfo:
    owner: str
    raw: dict[str, Any] = field(default_factory=dict)

    @classmethod
    def from_dict(cls, d: dict[str, Any]) -> "OwnerInfo":
        return cls(owner=str(_get(d, "owner", "holder", default="")), raw=d)


@dataclass(frozen=True)
class Contract:
    name: str
    symbol: str
    address: str
    network: str | None
    total_supply: int | None
    raw: dict[str, Any] = field(default_factory=dict)

    @classmethod
    def from_dict(cls, d: dict[str, Any]) -> "Contract":
        return cls(
            name=str(_get(d, "name", default="")),
            symbol=str(_get(d, "symbol", default="")),
            address=str(_get(d, "address", default="")),
            network=_get(d, "network", "chain"),
            total_supply=_get(d, "totalSupply", "total_supply"),
            raw=d,
        )


@dataclass(frozen=True)
class Species:
    number: int
    name: str
    energy: str | None
    rarity: str | None
    raw: dict[str, Any] = field(default_factory=dict)

    @classmethod
    def from_dict(cls, d: dict[str, Any]) -> "Species":
        return cls(
            number=int(_get(d, "number", "id", default=0)),
            name=str(_get(d, "name", default="")),
            energy=_get(d, "energy"),
            rarity=_get(d, "rarity"),
            raw=d,
        )


@dataclass(frozen=True)
class SpeciesImage:
    name: str
    token_id: int | None
    svg_url: str | None
    png_url: str | None
    raw: dict[str, Any] = field(default_factory=dict)

    @classmethod
    def from_dict(cls, d: dict[str, Any]) -> "SpeciesImage":
        return cls(
            name=str(_get(d, "name", default="")),
            token_id=_get(d, "tokenId", "token_id"),
            svg_url=_get(d, "svg", "svgUrl", "svg_url"),
            png_url=_get(d, "png", "pngUrl", "png_url"),
            raw=d,
        )


@dataclass(frozen=True)
class Collector:
    address: str
    progress: dict[str, Any]
    items: list[dict[str, Any]]
    raw: dict[str, Any] = field(default_factory=dict)

    @classmethod
    def from_dict(cls, d: dict[str, Any]) -> "Collector":
        return cls(
            address=str(_get(d, "address", default="")),
            progress=dict(_get(d, "progress", default={}) or {}),
            items=list(_get(d, "items", "checklist", "registry", default=[]) or []),
            raw=d,
        )
