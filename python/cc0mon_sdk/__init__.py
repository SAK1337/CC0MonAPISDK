"""cc0mon SDK — Python client for the cc0mon.com NFT API.

Quick start::

    from cc0mon_sdk import Client

    with Client() as c:
        token = c.get_token(1)
        print(token.name, token.owner)
"""
from .client import Client, __version__
from .errors import (
    ApiError,
    CC0MonError,
    ClientApiError,
    NetworkError,
    RateLimitError,
    ServerApiError,
    ValidationError,
)
from .logging_setup import configure_logger
from .models import (
    ENERGIES,
    RARITIES,
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

__all__ = [
    "Client",
    "__version__",
    "configure_logger",
    "ApiError",
    "CC0MonError",
    "ClientApiError",
    "NetworkError",
    "RateLimitError",
    "ServerApiError",
    "ValidationError",
    "ENERGIES",
    "RARITIES",
    "Collector",
    "CollectorItem",
    "Contract",
    "Metadata",
    "OwnerInfo",
    "Species",
    "SpeciesImage",
    "Token",
    "Traits",
    "validate_energy",
    "validate_rarity",
]
