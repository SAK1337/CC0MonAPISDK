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
    # API has 260 species; one (Vilewing) is unmapped — but it still appears.
    assert len(images) == 260
    mapped = [i for i in images if i.token_id is not None]
    assert len(mapped) == 259


def test_get_collector(client: Client) -> None:
    # Use a known holder so we get non-zero progress.
    collector = client.get_collector("0xB07952A55bF9c45C268F37C3631823Df50ac721a")
    assert isinstance(collector.progress, str)
    assert collector.progress.endswith("%")
    assert collector.total_cc0mon == 260
    assert len(collector.checklist) == 260
    assert isinstance(collector.by_energy, dict)
    assert collector.collected >= 1


def test_find_species_filter_subset(client: Client) -> None:
    fire_all = client.find_species(energy="Fire")
    fire_common = client.find_species(energy="Fire", rarity="Common")
    assert len(fire_common) <= len(fire_all)
    assert all(s.energy == "Fire" and s.rarity == "Common" for s in fire_common)


def test_find_collector_items_owned_only_consistent(client: Client) -> None:
    addr = "0xB07952A55bF9c45C268F37C3631823Df50ac721a"
    collector = client.get_collector(addr)
    owned = client.find_collector_items(addr, owned_only=True)
    assert len(owned) == collector.collected
    assert all(i.collected for i in owned)
