# 🏎️ automated-f1-stats

A personal F1 companion app — live race timing, results, qualifying, sprint, and standings, all in one place.

> **Unofficial project. Not affiliated with Formula 1 or FOM.**

---

## Project Structure

```
automated-f1-stats/
├── backend/        # Python FastAPI server (data engine)
├── android/        # Android app — Java + Material Design (coming soon)
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
- **Android app** calls your backend over your local network (or deployed server)

---

## Software You Will Need

This section tells you everything you need to install before each part of the project works.

### 🐍 Backend (Python)

| Software | Version | Why | Download |
|---|---|---|---|
| **Python** | 3.11+ | Runs the backend | [python.org](https://python.org) |
| **pip** | Comes with Python | Installs packages | — |
| **Git** | Any | Version control | [git-scm.com](https://git-scm.com) |

> **Optional but recommended:**
> A code editor — [VS Code](https://code.visualstudio.com) is free and works great for both Python and the project overall.
> Install the **Python extension** inside VS Code for syntax highlighting and debugging.

Once Python is installed, everything else is handled by `pip install -r requirements.txt`.

---

### 📱 Android App (Java)

| Software | Version | Why | Download |
|---|---|---|---|
| **Android Studio** | Hedgehog (2023.1.1) or newer | Full Android IDE, includes everything below | [developer.android.com/studio](https://developer.android.com/studio) |
| **JDK (Java)** | 17+ | Bundled inside Android Studio — no separate install needed | — |
| **Android SDK** | API 26+ (Android 8.0) | Bundled inside Android Studio | — |
| **Gradle** | Bundled | Build tool | — |

> ⚠️ **Android Studio is a large download (~1GB installer, ~3–4GB once set up).** Plan accordingly.
> When you first open it, it will download the Android SDK automatically — this takes a few minutes on first run.
> You do **not** need to install Java/JDK separately — Android Studio bundles it.

> **Optional:** A physical Android phone with **USB Debugging enabled** is much better for testing than the emulator. The emulator works but is slower.

---

### 🌐 Deployment (Optional — run backend beyond your local machine)

If you want the app to reach your backend when you're not on the same Wi-Fi:

| Option | Cost | Difficulty | Notes |
|---|---|---|---|
| **Local only (Wi-Fi)** | Free | ⭐ Easy | Phone and backend on same network |
| **Raspberry Pi** | ~€40 one-time | ⭐⭐ Medium | Always-on home server |
| **Railway.app** | Free tier | ⭐⭐ Easy | Push to GitHub, auto-deploys |
| **Render.com** | Free tier | ⭐⭐ Easy | Similar to Railway |
| **Fly.io** | Free tier | ⭐⭐⭐ Medium | More control, more setup |

> For a personal project, **local Wi-Fi** is the easiest to start with. You run the backend on your PC/laptop and your phone calls it over the home network.

---

## Quick Start

### 1. Clone the repo
```bash
# Requires: Git
git clone https://github.com/YOUR_USERNAME/automated-f1-stats.git
cd automated-f1-stats
```

### 2. Set up the backend
```bash
# Requires: Python 3.11+

cd backend

# Create a virtual environment (keeps dependencies isolated)
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

### 3. Set up the Android app *(coming soon)*
```
Requires: Android Studio
Instructions will be added when the Android module is built.
```

---

## Backend API — Quick Reference

| Endpoint | Description |
|---|---|
| `GET /schedule` | Full race calendar |
| `GET /schedule/next` | Next race weekend |
| `GET /live` | Live race snapshot |
| `GET /results/latest` | Latest race results |
| `GET /results/{year}/{round}` | Results for a specific round |
| `GET /standings/drivers` | Driver championship standings |
| `GET /standings/constructors` | Constructor standings |
| `GET /sessions/{key}/laps` | Lap times |
| `GET /sessions/{key}/stints` | Tyre strategy |
| `GET /sessions/{key}/pit-stops` | Pit stops |
| `GET /sessions/{key}/race-control` | Flags, safety car messages |

Full docs auto-generated at `/docs` when the server is running.

---

## Data Sources

| Source | What it provides | Cost |
|---|---|---|
| [OpenF1](https://openf1.org) | Live timing, positions, gaps, tyres, pit stops, weather | Free (historical) / €9.90/mo (live) |
| [Jolpica](https://github.com/jolpica/jolpica-f1) | Race calendar, final results, standings | Free |

---

## Roadmap

- [x] Python backend — FastAPI + scheduler + database
- [ ] Android app — results screen
- [ ] Android app — live timing screen
- [ ] Android app — standings screen
- [ ] Android app — schedule / countdown screen
- [ ] Notifications for session start / results available
- [ ] Optional: deploy backend to cloud

---

## Disclaimer

This is an unofficial personal project. All F1 data is sourced from community APIs (OpenF1, Jolpica).
Not affiliated with Formula 1, FOM, or any F1 team.
F1, FORMULA ONE, and related marks are trademarks of Formula One Licensing BV.