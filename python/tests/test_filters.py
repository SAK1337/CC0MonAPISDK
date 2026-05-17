"""Unit tests for the SDK filter helpers (no API calls).

These tests use the SDK's `find_*` methods via the Client class. We monkey-patch
the underlying endpoint methods to return fixture data so the filters can be
exercised without hitting the network.
"""
from __future__ import annotations

import pytest

from cc0mon_sdk import (
    Client,
    CollectorItem,
    Species,
    ValidationError,
    validate_energy,
    validate_rarity,
)


@pytest.fixture
def fixture_species() -> list[Species]:
    return [
        Species(number=1, name="Drillipede", energy="Earth", rarity="Common", raw={}),
        Species(number=2, name="Pyrogob", energy="Fire", rarity="Common", raw={}),
        Species(number=3, name="Solaroar", energy="Fire", rarity="Rare", raw={}),
        Species(number=4, name="Shadowshock", energy="Electric", rarity="Legendary", raw={}),
    ]


@pytest.fixture
def fixture_collector_items() -> list[CollectorItem]:
    return [
        CollectorItem(number=1, name="Drillipede", energy="Earth", rarity="Common", collected=False, token_ids=[], raw={}),
        CollectorItem(number=2, name="Pyrogob", energy="Fire", rarity="Common", collected=True, token_ids=[101], raw={}),
        CollectorItem(number=3, name="Solaroar", energy="Fire", rarity="Rare", collected=True, token_ids=[102], raw={}),
        CollectorItem(number=4, name="Shadowshock", energy="Electric", rarity="Legendary", collected=False, token_ids=[], raw={}),
    ]


def test_validate_energy_canonical_case():
    assert validate_energy("Fire") == "Fire"
    assert validate_energy("fire") == "Fire"
    assert validate_energy("FIRE") == "Fire"


def test_validate_energy_rejects_unknown():
    with pytest.raises(ValidationError) as excinfo:
        validate_energy("florp")
    assert "valid:" in str(excinfo.value)
    assert "Fire" in str(excinfo.value)


def test_validate_rarity_canonical_case():
    assert validate_rarity("Common") == "Common"
    assert validate_rarity("LEGENDARY") == "Legendary"


def test_validate_rarity_rejects_unknown():
    with pytest.raises(ValidationError):
        validate_rarity("Mythical")


def test_find_species_by_energy(monkeypatch, fixture_species):
    with Client() as c:
        monkeypatch.setattr(c, "get_registry", lambda: fixture_species)
        fire = c.find_species(energy="Fire")
        assert {s.name for s in fire} == {"Pyrogob", "Solaroar"}


def test_find_species_by_energy_and_rarity(monkeypatch, fixture_species):
    with Client() as c:
        monkeypatch.setattr(c, "get_registry", lambda: fixture_species)
        fire_common = c.find_species(energy="fire", rarity="common")
        assert [s.name for s in fire_common] == ["Pyrogob"]


def test_find_species_name_contains(monkeypatch, fixture_species):
    with Client() as c:
        monkeypatch.setattr(c, "get_registry", lambda: fixture_species)
        drill = c.find_species(name_contains="DRILL")
        assert [s.name for s in drill] == ["Drillipede"]


def test_find_species_empty_result_not_error(monkeypatch, fixture_species):
    with Client() as c:
        monkeypatch.setattr(c, "get_registry", lambda: fixture_species)
        assert c.find_species(energy="Fire", rarity="Legendary") == []


def test_find_species_invalid_energy_raises(monkeypatch, fixture_species):
    with Client() as c:
        monkeypatch.setattr(c, "get_registry", lambda: fixture_species)
        with pytest.raises(ValidationError):
            c.find_species(energy="florp")


def test_find_collector_items_owned_only(monkeypatch, fixture_collector_items):
    fake_collector = type("FakeCollector", (), {"checklist": fixture_collector_items})()
    with Client() as c:
        monkeypatch.setattr(c, "get_collector", lambda addr: fake_collector)
        owned = c.find_collector_items("0x" + "0" * 40, owned_only=True)
        assert [i.name for i in owned] == ["Pyrogob", "Solaroar"]


def test_find_collector_items_owned_and_energy(monkeypatch, fixture_collector_items):
    fake_collector = type("FakeCollector", (), {"checklist": fixture_collector_items})()
    with Client() as c:
        monkeypatch.setattr(c, "get_collector", lambda addr: fake_collector)
        result = c.find_collector_items("0x" + "0" * 40, owned_only=True, energy="Fire")
        assert {i.name for i in result} == {"Pyrogob", "Solaroar"}


def test_find_collector_items_filter_then_subset(monkeypatch, fixture_collector_items):
    fake_collector = type("FakeCollector", (), {"checklist": fixture_collector_items})()
    with Client() as c:
        monkeypatch.setattr(c, "get_collector", lambda addr: fake_collector)
        all_fire = c.find_collector_items("0x" + "0" * 40, energy="Fire")
        fire_owned = c.find_collector_items("0x" + "0" * 40, owned_only=True, energy="Fire")
        # owned subset of all
        assert all(i.collected for i in fire_owned)
        assert len(fire_owned) <= len(all_fire)
