# 🏎️ automated-f1-stats

A personal F1 companion app — live race timing, results, qualifying, sprint, standings, and schedule, all in one place.

> **Unofficial project. Not affiliated with Formula 1 or FOM.**

---

## Project Structure

```
automated-f1-stats/
├── backend/        # Python FastAPI server (data engine)
├── android/        # Android app — Java + Material Design
├── .gitignore
└── README.md       ← you are here
```

---

## How It Works

```
OpenF1 API  ──┐
              ├──▶  Python Backend  ──▶  REST API  ──▶  Android App
Jolpica API ──┘     (APScheduler)         JSON
```

- **OpenF1** provides live timing during sessions (~1–3s delay)
- **Jolpica** provides the race calendar, final results, and standings
- **APScheduler** arms polling jobs automatically based on real session times
- **Android app** calls your backend over your local network or a deployed server
- **ngrok** (optional) tunnels the backend to a public URL for remote access — configurable inside the app without rebuilding

---

## Software You Will Need

### 🐍 Backend (Python)

| Software | Version | Why | Download |
|---|---|---|---|
| **Python** | 3.11+ | Runs the backend | [python.org](https://python.org) |
| **pip** | Comes with Python | Installs packages | — |
| **Git** | Any | Version control | [git-scm.com](https://git-scm.com) |

> **Optional but recommended:**
> A code editor — [VS Code](https://code.visualstudio.com) is free and works great.
> Install the **Python extension** for syntax highlighting and debugging.
> ⚠️ If you install the VS Code Java extension, make sure Android Studio's Gradle JDK is not accidentally pointing to the VS Code JRE. Set `org.gradle.java.home=C:/Program Files/Android/Android Studio/jbr` in `gradle.properties` if needed.

Once Python is installed, everything else is handled by `pip install -r requirements.txt`.

---

### 📱 Android App (Java)

| Software | Version | Why | Download |
|---|---|---|---|
| **Android Studio** | Hedgehog (2023.1.1) or newer | Full Android IDE | [developer.android.com/studio](https://developer.android.com/studio) |
| **JDK (Java)** | 17+ | Bundled inside Android Studio | — |
| **Android SDK** | API 26+ (Android 8.0) | Bundled inside Android Studio | — |
| **Gradle** | Bundled | Build tool | — |

> ⚠️ Android Studio is a large download (~1GB installer, ~3–4GB once set up).
> You do **not** need to install Java/JDK separately — Android Studio bundles it.
> A physical Android phone with **USB Debugging enabled** is recommended over the emulator.

---

### 🌐 Deployment (Optional)

If you want the app to reach your backend when away from home Wi-Fi:

| Option | Cost | Difficulty | Notes |
|---|---|---|---|
| **Local only (Wi-Fi)** | Free | ⭐ Easy | Phone and backend on same network |
| **ngrok** | Free tier | ⭐ Easy | Tunnel to public URL, configure in app settings |
| **Raspberry Pi** | ~€40 one-time | ⭐⭐ Medium | Always-on home server |
| **Railway.app** | Free tier | ⭐⭐ Easy | Push to GitHub, auto-deploys |
| **Render.com** | Free tier | ⭐⭐ Easy | Similar to Railway |
| **Fly.io** | Free tier | ⭐⭐⭐ Medium | More control, more setup |

---

## Quick Start

### 1. Clone the repo
```bash
git clone https://github.com/YOUR_USERNAME/automated-f1-stats.git
cd automated-f1-stats
```

### 2. Set up the backend
```bash
cd backend

# Create a virtual environment
python -m venv venv

# Activate it
source venv/bin/activate        # macOS / Linux
venv\Scripts\activate           # Windows

# Install dependencies
pip install -r requirements.txt

# Configure environment
cp .env.example .env
# Open .env and add your OPENF1_TOKEN if you have one (optional)

# Run the server
uvicorn app.main:app --reload --port 8000
```

Backend is live at **http://localhost:8000**
Interactive API docs at **http://localhost:8000/docs**

---

### 3. Set up the Android app

```
Requires: Android Studio
1. Open the /android folder in Android Studio
2. Let Gradle sync and download dependencies
3. Open the app and go to Settings (gear icon, top right)
4. Enter your backend URL (local IP or ngrok URL)
5. Run on your device or emulator
```

> The backend URL is saved in SharedPreferences — no rebuild needed when the URL changes (e.g. when ngrok restarts).

---

## Android App — Features

| Screen | Features |
|---|---|
| **Home** | Next race countdown, championship leader, last race winner with race name and team |
| **Live** | Live session timing, positions, gaps, tyre compounds |
| **Results** | Full season round list, race results, qualifying (Q1/Q2/Q3), sprint |
| **Standings** | Driver and constructor championship tables with DNF counts and podiums |
| **Schedule** | Full season calendar with session times |
| **Settings** | Configure backend URL without rebuilding the app |

### UX Details
- Animated shimmer skeleton on home screen while data loads
- Pull-to-refresh on all screens
- Error state with retry button when all endpoints fail
- Season selector on Results and Standings (back to 1950)
- Championship leader shows points gap to P2
- Off-season: shows last season's champion with correct label

---

## Backend API — Quick Reference

| Endpoint | Description |
|---|---|
| `GET /schedule` | Full race calendar |
| `GET /schedule/next` | Next race weekend |
| `GET /live` | Live race snapshot |
| `GET /results/latest` | Latest race results with race name |
| `GET /results/{year}/{round}` | Results for a specific round |
| `GET /standings/drivers` | Driver championship standings |
| `GET /standings/constructors` | Constructor standings |
| `GET /sessions/{key}/laps` | Lap times |
| `GET /sessions/{key}/stints` | Tyre strategy |
| `GET /sessions/{key}/pit-stops` | Pit stops |
| `GET /sessions/{key}/race-control` | Flags, safety car messages |

Full docs auto-generated at `/docs` when the server is running.

### Caching Strategy
- **5 minutes** — live/current data (standings, next race, latest results)
- **7 days** — historical data (past seasons, completed rounds)
- **SQLite** — standings and results persisted across restarts

---

## Data Sources

| Source | What it provides | Cost |
|---|---|---|
| [OpenF1](https://openf1.org) | Live timing, positions, gaps, tyres, pit stops, weather | Free (historical) / €9.90/mo (live) |
| [Jolpica](https://github.com/jolpica/jolpica-f1) | Race calendar, final results, standings | Free |

---

## Roadmap

- [x] Python backend — FastAPI + APScheduler + SQLite cache
- [x] Dual-tier caching (5-min live, 7-day historical)
- [x] Android app — home screen with countdown, leader, last winner
- [x] Android app — live timing screen
- [x] Android app — results screen (race, qualifying, sprint)
- [x] Android app — standings screen (drivers + constructors)
- [x] Android app — schedule screen
- [x] Android app — shimmer loading skeleton
- [x] Android app — pull-to-refresh on all screens
- [x] Android app — error state with retry button
- [x] Android app — settings screen (configure backend URL without rebuild)
- [x] Android app — season selector (back to 1950)
- [x] Off-season fallback (shows last season champion with correct label)
- [ ] Cache home screen data for instant load on reopen
- [ ] Handle ngrok 429 rate limit errors with specific message
- [ ] Snackbar toast on pull-to-refresh complete
- [ ] App icon + splash screen
- [ ] Deploy backend to cloud (Railway / Render)
- [ ] Push notifications for session start / results available

---

## Disclaimer

This is an unofficial personal project. All F1 data is sourced from community APIs (OpenF1, Jolpica).
Not affiliated with Formula 1, FOM, or any F1 team.
F1, FORMULA ONE, and related marks are trademarks of Formula One Licensing BV.