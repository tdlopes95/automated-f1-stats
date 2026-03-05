# 🏎️ automated-f1-stats

A personal F1 companion app — live race timing, results, qualifying, sprint, standings, and schedule, all in one place.

> **Unofficial project. Not affiliated with Formula 1 or FOM.**

---

## Project Structure

```
automated-f1-stats/
├── backend/        # Python FastAPI server (data engine)
├── android/        # Android app — Java + Material Design 3
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
- **Koyeb** (or any cloud provider) hosts the backend — configure the URL inside the app without rebuilding

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

### 🌐 Deployment

The backend can be deployed to any cloud provider that supports Docker. Recommended free options:

| Option | Cost | Region | Notes |
|---|---|---|---|
| **Koyeb** | Free tier | Frankfurt | Always-on, no sleep, Docker deploy |
| **Railway** | $5 credit/month | Frankfurt | Easy GitHub deploy, sleeps on inactivity |
| **Render** | Free tier | Frankfurt | Similar to Railway, ~30s cold start |

> Once deployed, update the backend URL in the Android app via **Settings → gear icon** — no rebuild needed.

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

# Create a virtual environment (optional but recommended)
python -m venv venv
venv\Scripts\activate        # Windows
source venv/bin/activate     # macOS / Linux

# Install dependencies
pip install -r requirements.txt

# Run the server
uvicorn app.main:app --reload --port 8000
```

Backend is live at **http://localhost:8000**
Interactive API docs at **http://localhost:8000/docs**

### 3. Deploy the backend (optional)

The `backend/` folder contains a `Dockerfile` ready for deployment.

For **Koyeb**:
1. Push repo to GitHub
2. Create a new service → GitHub source → Dockerfile builder
3. Set Dockerfile location to `backend/Dockerfile`, work directory to `backend`
4. Select Frankfurt region, free instance, port 8000
5. Deploy

### 4. Set up the Android app
```
1. Open the /android folder in Android Studio
2. Let Gradle sync and download dependencies
3. Build and run on your device
4. Go to Settings (gear icon, top right)
5. Enter your backend URL (local IP or cloud URL)
```

---

## Android App — Features

| Screen | Features |
|---|---|
| **Home** | Next race countdown, championship leader with points gap, last race winner with team and race name |
| **Live** | Live session timing for all session types (Practice, Qualifying, Sprint, Race) |
| **Results** | Full season round list, race results, qualifying (Q1/Q2/Q3), sprint |
| **Standings** | Driver and constructor championship tables with DNF counts and podiums |
| **Schedule** | Full season calendar with all session times |
| **Settings** | Configure backend URL without rebuilding the app |

### UX Details
- Animated shimmer skeleton on first load
- Instant load from cache on reopen
- Pull-to-refresh with snackbar confirmation on all screens
- Error state with retry button when backend is unreachable
- Season selector on Results and Standings (back to 1950)
- Championship leader shows points gap to P2
- Off-season: shows last season's champion with correct label
- App icon and splash screen (F1 red background)

---

## Backend API — Quick Reference

| Endpoint | Rate Limit | Description |
|---|---|---|
| `GET /schedule` | 30/min | Full race calendar |
| `GET /schedule/next` | 30/min | Next race weekend |
| `GET /live` | 60/min | Live session snapshot |
| `GET /results/latest` | 30/min | Latest race results with race name |
| `GET /results/{year}/{round}` | 60/min | Results for a specific round |
| `GET /standings/drivers` | 30/min | Driver championship standings |
| `GET /standings/constructors` | 30/min | Constructor standings |
| `GET /sessions/{key}/pit-stops` | 30/min | Pit stops |
| `GET /sessions/{key}/race-control` | — | Flags, safety car messages |
| `GET /session-key/{year}/{round}` | 30/min | OpenF1 session key lookup |

Full docs auto-generated at `/docs` when the server is running.

### Caching Strategy
- **5 minutes** — live/current data (standings, next race, latest results)
- **7 days** — historical data (past seasons, completed rounds)
- **SQLite** — current season standings and results persisted across restarts

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
- [x] Rate limiting (slowapi — 30–60 req/min per IP)
- [x] Android app — home screen with countdown, leader, last winner
- [x] Android app — live timing screen (all session types)
- [x] Android app — results screen (race, qualifying, sprint)
- [x] Android app — standings screen (drivers + constructors)
- [x] Android app — schedule screen
- [x] Android app — shimmer loading skeleton
- [x] Android app — instant cache load on reopen
- [x] Android app — pull-to-refresh with snackbar on all screens
- [x] Android app — error state with retry button
- [x] Android app — settings screen (configure backend URL without rebuild)
- [x] Android app — season selector (back to 1950)
- [x] Android app — app icon + splash screen
- [x] Off-season fallback (shows last season champion with correct label)
- [x] Backend deployed to cloud (Koyeb — Frankfurt, always-on, free)
- [ ] Push notifications for session start / results available

---

## Disclaimer

This is an unofficial personal project. All F1 data is sourced from community APIs (OpenF1, Jolpica).
Not affiliated with Formula 1, FOM, or any F1 team.
F1, FORMULA ONE, and related marks are trademarks of Formula One Licensing BV.