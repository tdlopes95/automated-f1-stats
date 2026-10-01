"""
F1 Backend - News Headlines Client
Headlines (title, link, source, time) from public F1 RSS feeds. No images,
summaries or article text are kept: the app links out to the publisher.
"""

import asyncio
import html
import logging
import re
from datetime import UTC, datetime

import feedparser
import httpx

from .errors import UpstreamError

logger = logging.getLogger(__name__)
USER_AGENT = "F1StatsApp/1.0 (personal, non-commercial)"
MAX_ITEMS = 50

# All four checked on 2026-10-01 and returned valid RSS 2.0.
# formula1.com's feed carries no publish dates, so its items sort after dated ones.
FEEDS = [
    {"source": "Formula 1", "url": "https://www.formula1.com/en/latest/all.xml"},
    {"source": "Autosport", "url": "https://www.autosport.com/rss/f1/news/"},
    {"source": "Motorsport.com", "url": "https://www.motorsport.com/rss/f1/news/"},
    {"source": "The Race", "url": "https://www.the-race.com/category/formula-1/feed/"},
]

_TAG_RE = re.compile(r"<[^>]+>")
_NON_WORD_RE = re.compile(r"[\W_]+")


def clean_title(title: str) -> str:
    return " ".join(html.unescape(_TAG_RE.sub("", title)).split())


def normalise_title(title: str) -> str:
    """Case- and punctuation-insensitive key for spotting the same story twice."""
    return _NON_WORD_RE.sub(" ", title.lower()).strip()


def _published(entry) -> str | None:
    parsed = entry.get("published_parsed") or entry.get("updated_parsed")
    if not parsed:
        return None
    return datetime(*parsed[:6], tzinfo=UTC).isoformat()


def parse_feed(source: str, content: bytes) -> list[dict]:
    """Feed bytes -> headline items. Raises ValueError if the content isn't a feed."""
    feed = feedparser.parse(content)
    if not feed.entries and (feed.bozo or not feed.version):
        raise ValueError(f"not a valid feed: {feed.get('bozo_exception', 'no entries')}")

    items = []
    for entry in feed.entries:
        title = clean_title(entry.get("title") or "")
        link = (entry.get("link") or "").strip()
        if not title or not link.startswith(("http://", "https://")):
            continue
        items.append({
            "title": title,
            "link": link,
            "source": source,
            "published_utc": _published(entry),
        })
    return items


def merge_items(items: list[dict], limit: int = MAX_ITEMS) -> list[dict]:
    """Newest first (undated last), deduped by link and by normalised title, cut to limit."""
    ordered = sorted(
        items,
        key=lambda i: (i["published_utc"] is not None, i["published_utc"] or ""),
        reverse=True,
    )
    seen_links, seen_titles, result = set(), set(), []
    for item in ordered:
        title_key = normalise_title(item["title"])
        if item["link"] in seen_links or title_key in seen_titles:
            continue
        seen_links.add(item["link"])
        seen_titles.add(title_key)
        result.append(item)
        if len(result) >= limit:
            break
    return result


class NewsClient:
    def __init__(self):
        self._client = httpx.AsyncClient(
            timeout=10.0,
            follow_redirects=True,
            headers={"User-Agent": USER_AGENT},
        )

    async def _fetch(self, feed: dict) -> list[dict]:
        try:
            response = await self._client.get(feed["url"])
        except httpx.RequestError as e:
            raise UpstreamError(feed["source"], feed["url"]) from e
        if response.status_code != 200:
            raise UpstreamError(feed["source"], feed["url"], response.status_code)
        try:
            return parse_feed(feed["source"], response.content)
        except ValueError as e:
            raise UpstreamError(feed["source"], feed["url"], response.status_code) from e

    async def get_headlines(self) -> tuple[list[dict], list[str]]:
        """
        (items, failed_sources) across all feeds, fetched concurrently.
        Raises UpstreamError("news") only if every feed fails.
        """
        results = await asyncio.gather(
            *(self._fetch(feed) for feed in FEEDS), return_exceptions=True
        )
        items, failed = [], []
        for feed, result in zip(FEEDS, results, strict=True):
            if isinstance(result, Exception):
                logger.warning(f"News feed {feed['source']} failed: {result}")
                failed.append(feed["source"])
            else:
                items.extend(result)

        if len(failed) == len(FEEDS):
            raise UpstreamError("news", "/news")
        return merge_items(items), failed

    async def close(self):
        await self._client.aclose()
