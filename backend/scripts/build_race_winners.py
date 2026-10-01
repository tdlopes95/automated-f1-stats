"""
Build app/data/race_winners.json: every F1 race winner from 1950 up to the end
of last season, fetched from Jolpica.

The /history/on-this-day endpoint reads this file and fetches only the seasons
after the newest one in it, so the file never goes wrong when it ages. It costs
one extra Jolpica request per missing season, though, so regenerate it once a
season has finished (e.g. in January) and commit the result:

    cd backend && .venv/bin/python -m scripts.build_race_winners

Shared drives (1950s) produce one entry per winning driver.
"""

import json
import sys
import time
from datetime import UTC, datetime

import httpx

from app.history import DATA_PATH, race_to_winners

URL = "https://api.jolpi.ca/ergast/f1/results/1.json"
PAGE_LIMIT = 100        # Jolpica's maximum page size
PAGE_DELAY = 0.5        # seconds between pages (Jolpica allows 4 req/s burst)
MAX_RETRIES = 4


def fetch_page(client: httpx.Client, offset: int) -> dict:
    for attempt in range(MAX_RETRIES):
        response = client.get(URL, params={"limit": PAGE_LIMIT, "offset": offset})
        if response.status_code == 429:
            wait = 2 ** (attempt + 1)
            print(f"  429 at offset {offset}, retrying in {wait}s", file=sys.stderr)
            time.sleep(wait)
            continue
        response.raise_for_status()
        return response.json()["MRData"]
    raise RuntimeError(f"Jolpica kept rate-limiting at offset {offset}")


def main() -> None:
    current_year = datetime.now(UTC).year
    winners: list[dict] = []
    offset, total = 0, None

    with httpx.Client(timeout=30.0, headers={"User-Agent": "F1StatsApp/1.0 (build script)"}) as c:
        while total is None or offset < total:
            if offset:
                time.sleep(PAGE_DELAY)
            mr_data = fetch_page(c, offset)
            total = int(mr_data["total"])
            for race in mr_data["RaceTable"]["Races"]:
                winners.extend(race_to_winners(race))
            offset += PAGE_LIMIT
            print(f"  fetched {min(offset, total)}/{total}", file=sys.stderr)

    winners = [w for w in winners if w["season"] < current_year]
    winners.sort(key=lambda w: (w["season"], w["round"], w["driver_id"] or ""))

    DATA_PATH.parent.mkdir(parents=True, exist_ok=True)
    with DATA_PATH.open("w", encoding="utf-8") as f:
        json.dump(winners, f, ensure_ascii=False, indent=1)
        f.write("\n")

    seasons = {w["season"] for w in winners}
    print(f"Wrote {len(winners)} winners ({min(seasons)}-{max(seasons)}) to {DATA_PATH}")


if __name__ == "__main__":
    main()
