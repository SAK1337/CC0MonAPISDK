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
    Collector,
    Contract,
    Metadata,
    OwnerInfo,
    Species,
    SpeciesImage,
    Token,
    Traits,
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
    "Collector",
    "Contract",
    "Metadata",
    "OwnerInfo",
    "Species",
    "SpeciesImage",
    "Token",
    "Traits",
]
