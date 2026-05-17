"""Regression test: every example script maps SDK exceptions to the spec exit codes.

Spec §8.2:
    2 = NetworkError, 3 = ClientApiError or RateLimitError (429 after retries),
    4 = ServerApiError, 5 = ValidationError, 1 = unexpected.

This guards the H1 fix from the v0.1 audit: it's the test that would have caught
"no example script catches RateLimitError" before it shipped.
"""
from __future__ import annotations

import importlib.util
import sys
from pathlib import Path

import pytest

from cc0mon_sdk import (
    ClientApiError,
    NetworkError,
    RateLimitError,
    ServerApiError,
    ValidationError,
)

EXAMPLES_DIR = Path(__file__).resolve().parent.parent / "examples"


def _load(script: str):
    """Load a script with hyphens in its filename as an importable module."""
    path = EXAMPLES_DIR / script
    spec = importlib.util.spec_from_file_location(path.stem.replace("-", "_"), path)
    module = importlib.util.module_from_spec(spec)
    sys.modules[module.__name__] = module
    spec.loader.exec_module(module)
    return module


# (script_filename, argv-to-pass-to-main)
SCRIPTS = [
    ("cc0mon-api-get-token.py", ["--id", "1"]),
    ("cc0mon-api-get-metadata.py", ["--id", "1"]),
    ("cc0mon-api-get-traits.py", ["--id", "1"]),
    ("cc0mon-api-get-image-svg.py", ["--id", "1", "--out", "/tmp/_unused.svg"]),
    ("cc0mon-api-get-image-png.py", ["--id", "1", "--out", "/tmp/_unused.png"]),
    ("cc0mon-api-get-owner.py", ["--id", "1"]),
    ("cc0mon-api-get-contract.py", []),
    ("cc0mon-api-get-registry.py", []),
    ("cc0mon-api-get-registry-images.py", []),
    ("cc0mon-api-get-collector.py", ["--address", "0x" + "0" * 40]),
]

EXCEPTIONS_AND_EXPECTED_CODES = [
    (lambda: ValidationError("bad input"), 5),
    (lambda: NetworkError("dns failed"), 2),
    (lambda: RateLimitError(429, "rate limited", 1.0), 3),
    (lambda: ClientApiError(404, "not found"), 3),
    (lambda: ServerApiError(503, "upstream"), 4),
]


@pytest.mark.parametrize("script,argv", SCRIPTS)
@pytest.mark.parametrize("exc_factory,expected", EXCEPTIONS_AND_EXPECTED_CODES)
def test_example_maps_exception_to_exit_code(monkeypatch, script, argv, exc_factory, expected):
    """Every example must translate every SDK exception to the documented exit code."""
    module = _load(script)

    def raise_it(self, *_a, **_kw):
        raise exc_factory()

    # Patch every endpoint method on the Client class so whichever the script calls trips the exception.
    from cc0mon_sdk import Client
    for name in (
        "get_token", "get_metadata", "get_traits",
        "get_image_svg", "get_image_png", "get_owner",
        "get_contract", "get_registry", "get_registry_images",
        "get_collector", "find_species", "find_collector_items",
    ):
        monkeypatch.setattr(Client, name, raise_it, raising=False)

    assert module.main(argv) == expected
