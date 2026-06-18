---
name: project-context
description: F1 Stats app phase progress, architecture decisions, and implementation patterns
metadata:
  type: project
---

Phase 1 (Visual Foundation) and Phase 2 (Race Weekend Command Center) are complete.

**Why:** Following the UX modernization plan in F1_STATS_UX_MODERNIZATION_CONTEXT.md. No Compose migration — stay on XML/Fragments/MVVM throughout.

**How to apply:** When implementing Phase 3+, reuse the design tokens in dimens.xml/colors.xml/styles.xml established in Phase 1. Do not modify ViewModel, Repository, or API layers unless strictly required.

## Phase 2 implementation details

### Race Hero
- `fragment_home.xml` hero card: added `tv_next_session` (next upcoming session label, goes live-green when session is active) and `ll_weekend_timeline` (programmatic FrameLayout with dot nodes + connector line)
- Timeline built in `HomeFragment.buildWeekendTimeline()` — dots colored green (done), white (active), outlined gray (upcoming)

### Weekend Timeline
- Status logic in `getSessionStatus()`: done = session ended >3h ago, active = started <3h ago, upcoming = future
- Session abbreviations: Practice 1→FP1, Practice 2→FP2, Practice 3→FP3, Sprint Shootout→SQ, Sprint→SPR, Qualifying→QUALI, Race→RACE

### Championship Battle
- `card_leader` restructured from horizontal (single leader) to vertical (P1 + gap display + P2)
- `layout_champ_gap` and `layout_p2` are GONE by default; shown when standings has ≥2 entries
- `tv_champ_gap` shows the gap integer, `tv_champ_insight` shows deterministic insight text
- Removed `tv_leader_gap` (old gap display), replaced with centered `tv_champ_gap`
- P2 headshot loaded alongside P1 and last winner via `driverHeadshotMap`

### Off-season / missing data
- `layout_champ_gap` and `layout_p2` stay GONE if gap ≤ 0 or standings < 2 entries
- `ll_weekend_timeline` stays GONE if no sessions
- `tv_next_session` shows "WEEKEND COMPLETE" if all sessions are finished
