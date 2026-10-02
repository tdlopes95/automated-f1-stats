"""
Tests for the track map generator geometry (scripts/build_track_maps.py)
and the /track-map/{circuit_id} endpoint.
"""

from app import track_maps
from scripts.build_track_maps import (
    BOX_SIZE,
    corners_match_outline,
    simplify_to_target,
    transform,
)


def square(per_side: int = 50) -> list[tuple[float, float]]:
    """A 100x100 square traced anticlockwise from (0, 0), per_side samples per side."""
    pts = []
    for i in range(per_side):
        pts.append((100 * i / per_side, 0.0))
    for i in range(per_side):
        pts.append((100.0, 100 * i / per_side))
    for i in range(per_side):
        pts.append((100 - 100 * i / per_side, 100.0))
    for i in range(per_side):
        pts.append((0.0, 100 - 100 * i / per_side))
    return pts


# ── Transform ────────────────────────────────────────────────────────────────

def test_transform_fits_box_and_flips_y():
    raw = square()
    points, corners = transform(raw, [{"number": 1, "x": 100.0, "y": 100.0, "angle": 90.0}],
                                rotation=0)
    xs = [p[0] for p in points]
    ys = [p[1] for p in points]
    assert (min(xs), max(xs), min(ys), max(ys)) == (0, BOX_SIZE, 0, BOX_SIZE)
    # (0, 0) is bottom-left in track space, so it lands at the bottom (max screen Y)
    assert points[0] == [0, BOX_SIZE]
    # the top-right track corner lands at screen top-right
    assert (corners[0]["x"], corners[0]["y"]) == (BOX_SIZE, 0)
    # "up" (90°) in track space is -90° in screen space
    assert corners[0]["angle"] == -90


def test_transform_centres_a_non_square_shape():
    points, _ = transform([(0, 0), (200, 0), (200, 100), (0, 100)], [], rotation=0)
    ys = [p[1] for p in points]
    assert min(ys) == 250 and max(ys) == 750           # 2:1 shape, centred vertically


def test_transform_rotation_is_anticlockwise_in_track_space():
    points, _ = transform([(0, 0), (100, 0), (100, 10)], [], rotation=90)
    # +x rotated 90° anticlockwise is track "up", i.e. screen up after the flip
    (x0, y0), (x1, y1) = points[0], points[1]
    assert abs(x1 - x0) < 1e-6 and y1 < y0


def test_simplification_preserves_start_and_sector_samples():
    raw = square(per_side=200)                          # 800 points
    breaks = [215, 437]                                 # mid-side samples, not vertices
    points, _ = transform(raw, [], rotation=0)
    simplified, new_breaks = simplify_to_target(points, breaks, target=300)

    assert len(simplified) <= 300
    assert simplified[0] == points[0]
    assert simplified[new_breaks[0]] == points[215]
    assert simplified[new_breaks[1]] == points[437]
    assert new_breaks[0] < new_breaks[1]


def test_simplification_leaves_short_outlines_alone():
    points, _ = transform(square(per_side=10), [], rotation=0)
    assert simplify_to_target(points, [12, 25], target=300) == (points, [12, 25])


# ── Corner sanity check ──────────────────────────────────────────────────────

def test_corner_check_accepts_corners_on_the_outline():
    corners = [{"x": 0.5, "y": 0.0}, {"x": 100.0, "y": 49.0}, {"x": 51.0, "y": 100.5}]
    assert corners_match_outline(corners, square())


def test_corner_check_rejects_corners_from_another_layout():
    # diagonal ~141; median distance must stay under ~4.2. These sit ~20 inside the square.
    corners = [{"x": 20.0, "y": 20.0}, {"x": 80.0, "y": 50.0}, {"x": 50.0, "y": 80.0},
               {"x": 0.0, "y": 0.0}]
    assert not corners_match_outline(corners, square())


def test_corner_check_rejects_empty_corners():
    assert not corners_match_outline([], square())


# ── Endpoint ─────────────────────────────────────────────────────────────────

def test_track_map_returns_generated_file(client):
    resp = client.get("/track-map/monza")
    assert resp.status_code == 200
    body = resp.json()
    assert body["circuit_id"] == "monza"
    assert body["attribution"] == track_maps.ATTRIBUTION
    assert 2 <= len(body["points"]) <= 350
    assert all(0 <= x <= BOX_SIZE and 0 <= y <= BOX_SIZE for x, y in body["points"])
    i1, i2 = body["sector_breaks"]
    assert 0 < i1 < i2 < len(body["points"])
    assert body["source"]["session"] in ("Qualifying", "Race")


def test_track_map_unknown_circuit_is_404(client):
    resp = client.get("/track-map/not_a_circuit")
    assert resp.status_code == 404
