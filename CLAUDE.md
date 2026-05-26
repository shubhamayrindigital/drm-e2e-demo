# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Repo shape

Three independent components in one repo. README.md is the source of truth for DRM concepts and end-to-end flow — read it before changing playback or license code.

- `android/` — Jetpack Compose app (Hilt + Media3 ExoPlayer + Retrofit). Player, downloads, offline license cache.
- `backend/` — Express + Prisma + Postgres. Auth, catalog, manifest/segment proxy, ClearKey license endpoint, offline-license bookkeeping.
- `packager/` — one-shot bash scripts wrapping `shaka-packager`. Produces DASH + CMAF (clear or CENC-encrypted) for upload to R2.
- `render.yaml` — Render blueprint for backend deploy.
- `.github/workflows/android-apk.yml` — builds signed release APK on push to `android/**`.

## Commands

### Backend (`cd backend`)
- `npm run dev` — tsx watch on `src/index.ts`, listens on `:3000`.
- `npm run build` — `prisma generate && tsc` → `dist/`.
- `npm start` — production: `prisma migrate deploy && node dist/index.js`. Used by Render.
- `npx prisma migrate dev --name <n>` — create + apply migration locally.
- `npx prisma migrate deploy` — apply pending migrations (no schema changes).
- `npm run prisma:seed` — seed demo user (`demo@example.com` / `password123`) + `drm-test` and `clear-test` content rows.
- `npm run db:reset` — destructive, drops + reseeds local DB.
- `node scripts/sync-r2.mjs` — uploads `packager/out/**` to R2 under configured prefixes.
- Required env: `DATABASE_URL`, `DIRECT_URL`, `JWT_SECRET`, `CF_BUCKET_NAME`, `CF_R2_ENDPOINT`, `CF_R2_ACCESS_KEY_ID`, `CF_R2_SECRET_ACCESS_KEY`. See `.env.example`.

### Android (`cd android`)
- `./gradlew :app:assembleDebug` — debug build.
- `./gradlew :app:assembleRelease` — signed release APK (uses committed POC keystore).
- `./gradlew :app:installDebug` — install onto running emulator/device.
- `./gradlew :app:lint` / `./gradlew test` — lint and unit tests.
- Backend URL override at build time: `-PBACKEND_BASE_URL=http://localhost:3000/`. Default from `gradle.properties` points at Render.
- For local backend on emulator: `adb reverse tcp:3000 tcp:3000` first.

### Packager (`cd packager`, one-shot)
- `./download-sample.sh` — fetches Big Buck Bunny input video (~350 MB).
- `./package.sh <input.mp4>` — DASH + CENC encryption using hardcoded KID/CEK → `out/drm/`.
- `./package-clear.sh <input.mp4>` — DASH, no encryption → `out/clear/`.
- KID/CEK in `package.sh` MUST match `Content.kid` / `Content.cek` in DB or playback silently dies. Re-seed if regenerated.

## Architecture essentials

### Trust model & key handling
- **CEK never leaves the backend except via `/license/clearkey`.** Catalog responses strip `cek`. The license endpoint validates JWT + `Entitlement` row before returning `{ keys: [{ kty, kid, k }] }` in W3C EME JSON format. ClearKey is wire-format; real Widevine would wrap.
- **R2 is private.** All manifest + segment fetches go through the backend proxy (`backend/src/catalog/`). This is intentional — enables per-request auth and keeps R2 credentials out of the client. Don't bypass it.
- **POC release keystore is committed** at `android/app/release.keystore` (password `android`). Same signing for debug + release. Do not reuse elsewhere.

### Backend layout (`backend/src/`)
- `auth/` — signup, login, JWT issuance, `requireAuth` middleware.
- `catalog/` — `/catalog`, `/play-manifest/:id`, manifest proxy that rewrites segment URLs to flow through backend.
- `license/` — `POST /license/clearkey` (reads `X-Content-Id`, checks entitlement, returns CEK).
- `offline/` — offline license issuance + TTL bookkeeping (`OfflineLicense.expiresAt`).
- `storage/` — S3-compatible R2 client.
- `db/` — Prisma client singleton.

### Android layout (`android/app/src/main/java/com/ayrindigital/drme2edemo/`)
- `data/` — repositories (Auth, Catalog, Download, OfflineLicense), Retrofit API, OkHttp auth interceptor, DataStore, `NetworkMonitor`.
- `player/` — `PlayerManager` builds Media3 player with `CachedClearKeyDrmCallback` (intercepts license request, replays cached bytes from `OfflineLicenseStore` when offline).
- `downloads/` — `DemoDownloadService` foreground service driving Media3 `DownloadManager`. Cache-first playback (local copy used when present).
- `ui/` — Compose screens + ViewModels. Single-activity (`MainActivity`).
- `di/` — Hilt modules.

### Offline license flow (POC vs real)
Emulator's ClearKey CDM lacks `MediaDrm.restoreKeys`. So instead of storing a `keysetId`, this demo stores the **raw license response bytes** in `OfflineLicenseStore` after the first online fetch. `CachedClearKeyDrmCallback` returns those bytes for any later license request for that content. For real Widevine you'd switch to `OfflineLicenseHelper.downloadLicense` + `restoreKeys(keysetId)`.

`OfflineLicenseStore.LICENSE_TTL_MS = 60_000L` (POC value). On expiry, `DownloadRepository` calls `downloadManager.removeDownload(id)` and the catalog UI countdown drops the item from the offline list. Bump the constant for production-realistic windows.

`CatalogViewModel` separates concerns: `NetworkMonitor.isOnline` is the *only* trigger for `/catalog` fetches (so download-state churn doesn't thrash network); `DownloadRepository.downloads` re-filters the cached catalog while offline.

### Packaging → DB invariant
After `package.sh`, the KID/CEK used at packaging time **must** match `Content.kid` / `Content.cek`. If you regenerate keys, update `prisma/seed.ts` (or directly the DB row) and re-run seed. Mismatch produces silent decryption failure — segments download fine, playback never starts.

### CI scope
GitHub Actions only builds the APK on changes under `android/**` or the workflow file itself. Backend deploys are Render-driven (push to main → Render rebuilds). The build injects `BACKEND_BASE_URL=https://drm-e2e-backend.onrender.com/`.

## Conventions

- Backend is ESM (`"type": "module"`); imports need `.js` extensions in TS source for emitted JS.
- All client-facing R2 URLs go through the backend proxy — never hand the player a direct R2 URL.
- Commit style: no Claude attribution trailer.
- Never `git push` until user has tested and approved.
