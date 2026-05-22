# DRM E2E Demo — Android App

Kotlin + Jetpack Compose + Media3 ExoPlayer. Demonstrates Widevine DRM and non-DRM playback.

## Architecture

```
com.ayrindigital.drme2edemo/
├── data/
│   ├── api/          # Retrofit service + models
│   ├── auth/         # TokenStore, AuthRepository
│   └── catalog/      # CatalogRepository
├── drm/              # Widevine DRM helpers (stub)
├── player/           # ExoPlayer setup
├── downloads/        # DownloadManager service
├── ui/
│   ├── auth/         # Login screen
│   ├── catalog/      # Content listing
│   └── player/       # Video player
├── di/               # Hilt DI modules
└── MainActivity.kt   # NavHost
```

## Setup

1. **Backend running** on `http://localhost:3000` (see `backend/README.md`).
2. **Open in Android Studio** (Iguana+).
3. **Build & run** on emulator (API ≥ 31) or physical device.

## Current state

✅ Auth (signup/login)
✅ Catalog listing (entitled/not-entitled)
✅ Navigation (Login → Catalog → Player)
🚧 Player (stub, needs ExoPlayer wiring + manifest URL fetch)
🚧 Offline downloads (skeleton)
🚧 DRM license proxy (stub)

## Next

1. **Fetch signed manifest URLs** from backend (`/play/:id/manifest` endpoint — TBD).
2. **Wire ExoPlayer** for clear (HLS/DASH) playback.
3. **Wire Widevine** DRM playback (license proxy integration).
4. **Download manager** (offline DRM + clear with TTL).
5. **Test on emulator** (L3 Widevine, may limit HD).
6. **Test on physical device** (L1 for HD content).

## Building

```bash
cd android
./gradlew build
./gradlew installDebug  # on connected device/emulator
```

## Troubleshooting

**"Class X not found"** after adding Hilt
```bash
./gradlew clean build
```

**Network timeout on login**
- Ensure backend is running on `:3000`.
- Check firewall / emulator networking (Android emulator accesses host via `10.0.2.2:3000`).
- Modify `baseUrl` in `NetworkModule.kt` if needed.

**Compose previews fail**
- Just a preview limitation, doesn't affect builds.

---

## Phase 3 status

Scaffolded. Auth flow + catalog UI complete. Player screen needs ExoPlayer + manifest-URL fetching from backend. No external CF creds needed yet.
