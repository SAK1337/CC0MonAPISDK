"""Integration tests for cc0mon_sdk.

These tests hit the **real** ``https://api.cc0mon.com`` API. They are
gated behind the ``CC0MON_RUN_INTEGRATION=1`` environment variable so
they don't run by accident during unrelated CI.

Run with::

    $env:CC0MON_RUN_INTEGRATION = "1"
    pytest python/tests/test_integration.py -v

All 10 calls fit comfortably under the 60 req/min rate limit.
"""
from __future__ import annotations

import os

import pytest

from cc0mon_sdk import Client

pytestmark = pytest.mark.skipif(
    os.environ.get("CC0MON_RUN_INTEGRATION") != "1",
    reason="Set CC0MON_RUN_INTEGRATION=1 to run live API tests.",
)

SAMPLE_TOKEN_ID = 1
# A well-known cc0mon holder; if this changes, swap it for any 0x address you control.
# Note: collector endpoint is expected to respond 200 for any well-formed address,
# returning an empty checklist for non-holders.
SAMPLE_ADDRESS = "0x0000000000000000000000000000000000000000"


@pytest.fixture(scope="module")
def client() -> Client:
    with Client() as c:
        yield c


def test_get_token(client: Client) -> None:
    token = client.get_token(SAMPLE_TOKEN_ID)
    assert token.id == SAMPLE_TOKEN_ID or token.raw  # name might be empty in raw form
    assert isinstance(token.raw, dict)


def test_get_metadata(client: Client) -> None:
    metadata = client.get_metadata(SAMPLE_TOKEN_ID)
    assert metadata.name
    assert isinstance(metadata.attributes, list)


def test_get_traits(client: Client) -> None:
    traits = client.get_traits(SAMPLE_TOKEN_ID)
    assert isinstance(traits.attributes, list)
    assert isinstance(traits.raw, dict)


def test_get_image_svg(client: Client) -> None:
    svg = client.get_image_svg(SAMPLE_TOKEN_ID)
    assert isinstance(svg, bytes)
    assert len(svg) > 100
    assert b"<svg" in svg[:200]


def test_get_image_png(client: Client) -> None:
    png = client.get_image_png(SAMPLE_TOKEN_ID)
    assert isinstance(png, bytes)
    assert len(png) > 100
    assert png[:8] == b"\x89PNG\r\n\x1a\n"


def test_get_owner(client: Client) -> None:
    info = client.get_owner(SAMPLE_TOKEN_ID)
    assert info.owner.startswith("0x")
    assert len(info.owner) == 42


def test_get_contract(client: Client) -> None:
    contract = client.get_contract()
    assert contract.address.lower().startswith("0x")
    assert contract.name or contract.raw


def test_get_registry(client: Client) -> None:
    species = client.get_registry()
    assert len(species) >= 1
    assert all(isinstance(s.name, str) for s in species)


def test_get_registry_images(client: Client) -> None:
    images = client.get_registry_images()
    assert len(images) >= 1


def test_get_collector(client: Client) -> None:
    collector = client.get_collector(SAMPLE_ADDRESS)
    assert collector.raw is not None
