"""
F1 Backend - Shared error types
"""

from typing import Optional


class UpstreamError(Exception):
    """
    Raised when an upstream data source (Jolpica / OpenF1) fails:
    rate-limited after retries, non-404 HTTP error, network error or bad JSON.
    A 404 is NOT an UpstreamError — it means "no data" and yields an empty payload.
    """

    def __init__(self, source: str, path: str, status_code: Optional[int] = None):
        self.source = source
        self.path = path
        self.status_code = status_code
        status = f" (HTTP {status_code})" if status_code is not None else ""
        super().__init__(f"{source} request failed on {path}{status}")
