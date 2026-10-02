"""
F1 Backend - Circuit maps
Static outlines in app/data/tracks/{circuit_id}.json, generated from OpenF1
position data and MultiViewer corner data by scripts/build_track_maps.py.
"""

import functools
import json
from pathlib import Path

DATA_DIR = Path(__file__).parent / "data" / "tracks"
ATTRIBUTION = "Position data: OpenF1. Corner data: MultiViewer."


@functools.cache
def load_track_maps() -> dict[str, dict]:
    """Every generated map keyed by circuit_id. Read once; the files only change on deploy."""
    maps = {}
    for path in sorted(DATA_DIR.glob("*.json")):
        with path.open(encoding="utf-8") as f:
            track = json.load(f)
        maps[track["circuit_id"]] = track
    return maps


def get_track_map(circuit_id: str) -> dict | None:
    track = load_track_maps().get(circuit_id)
    return {**track, "attribution": ATTRIBUTION} if track else None
