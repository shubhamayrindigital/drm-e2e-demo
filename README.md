# DRM E2E Demo

End-to-end demo of **DRM-protected** and **non-DRM (clear)** video playback on Android, with offline downloads, a Node.js backend, and a free-tier cloud deployment.

This README is written for someone who has **never worked with DRM before**. By the end you should understand:

- What DRM is and why it exists
- The terminology you'll keep bumping into (KID, CEK, PSSH, EME, CDM, manifest, license server, etc.)
- How a video actually plays back — from "tap on a title" to "frames on screen"
- How offline playback works (it's not just "download the file")
- What audio-only playback looks like in this same world
- How this specific project is wired up, and how to run / deploy / extend it

---

## Table of contents

1. [What DRM is (and isn't)](#1-what-drm-is-and-isnt)
2. [Terminology cheat sheet](#2-terminology-cheat-sheet)
3. [How streaming video actually works](#3-how-streaming-video-actually-works)
4. [DRM playback flow — diagram + walkthrough](#4-drm-playback-flow--diagram--walkthrough)
5. [Non-DRM (clear) playback](#5-non-drm-clear-playback)
6. [Audio-only playback](#6-audio-only-playback)
7. [Offline playback (and offline licenses)](#7-offline-playback-and-offline-licenses)
8. [What this project actually does](#8-what-this-project-actually-does)
9. [Architecture diagram](#9-architecture-diagram)
10. [Services used](#10-services-used)
11. [Repo layout](#11-repo-layout)
12. [Local setup](#12-local-setup)
13. [Deployment](#13-deployment)
14. [Known limitations / POC caveats](#14-known-limitations--poc-caveats)
15. [Glossary index](#15-glossary-index)

---

## 1. What DRM is (and isn't)

**DRM = Digital Rights Management.** It's the umbrella term for a set of technologies that let you encrypt a piece of media (a movie, a song, a PDF) and only let *authorized* devices decrypt it, *under conditions you control* — e.g. "user X can watch this for 48 hours then it expires" or "this device can only watch in SD, never HD" or "screenshots are blocked".

### Why DRM exists

If you just put an `.mp4` on a CDN, anyone with the URL can download it forever. For free trailers that's fine. For a paid Netflix movie it isn't. DRM lets you ship the encrypted bytes publicly while keeping the *decryption key* gated behind your business rules (logged in? subscription active? geo allowed? etc.).

### What DRM is NOT

- **Not unbreakable.** Determined attackers will eventually pull keys out of any consumer device. DRM raises the cost of piracy; it doesn't eliminate it.
- **Not encryption alone.** AES-encrypting a file is one piece. DRM also covers key delivery, license expiry, hardware-backed key storage, output protection (HDCP), and revocation.
- **Not a single standard.** There are multiple competing DRM systems (Widevine, PlayReady, FairPlay, ClearKey). Each device supports a different subset. You usually need 2–3 to cover all platforms.

### The three "real" DRMs

| DRM | Owner | Where it runs |
|---|---|---|
| **Widevine** | Google | Android, Chrome, Chromecast, Android TV, Firefox |
| **PlayReady** | Microsoft | Windows, Xbox, Edge, many smart TVs |
| **FairPlay** | Apple | iOS, macOS Safari, tvOS |

A 4th system called **ClearKey** exists — it's part of the W3C EME spec and is *not* really DRM. It's intended for testing: the "license" is literally the AES key handed to the player in plain JSON. We use ClearKey in this demo because Widevine requires Google approval for a real license server and adds a lot of friction for a POC.

---

## 2. Terminology cheat sheet

You will see these acronyms everywhere. Skim now, refer back later.

| Term | What it means |
|---|---|
| **Mezzanine** | The original high-quality master file (e.g. a 4K ProRes). You package *from* the mezzanine. |
| **Packaging** | Turning the mezzanine into a streaming-ready format (DASH or HLS), optionally encrypting it. |
| **DASH** | "Dynamic Adaptive Streaming over HTTP". A standard format: an XML **manifest** (`.mpd`) plus many small segments. |
| **HLS** | Apple's equivalent of DASH. A `.m3u8` text manifest plus segments. |
| **Manifest / MPD** | The text file the player downloads first. Describes available bitrates, segments, codecs, DRM info. |
| **Segment** | A short chunk (2–10s) of video or audio. The player downloads segments in sequence and stitches them. |
| **Bitrate ladder** | Multiple encodings of the same content at different qualities. The player picks one based on bandwidth. **Adaptive Bitrate (ABR)** = automatic switching. |
| **CENC** | "Common Encryption". A standard for AES-encrypting MP4 segments such that Widevine, PlayReady, and ClearKey can all decrypt the same bytes (with their own license formats). |
| **CEK** | "Content Encryption Key". The AES-128 key used to encrypt the actual video bytes. The *secret* you protect. |
| **KID** | "Key ID". A *public* identifier for which CEK was used. Sent in the manifest. The license server uses the KID to look up the CEK. |
| **PSSH** | "Protection System Specific Header". A blob embedded in the manifest/MP4 that tells the player "this content uses Widevine/PlayReady/ClearKey, here's the KID". |
| **License** | A small message from your license server that contains the CEK (often wrapped in another layer of encryption tied to the device). |
| **License server** | The HTTP service that issues licenses. Your business logic lives here: "is this user entitled?", "what's the playback window?", "should this license persist for offline?" |
| **CDM** | "Content Decryption Module". A piece of software/hardware inside the OS/browser that holds keys, decrypts video, and enforces output rules. **You never touch keys directly — the CDM does.** Widevine CDM ships with Android/Chrome; FairPlay CDM ships with Apple devices. |
| **EME** | "Encrypted Media Extensions". The W3C browser API for talking to a CDM. Standard across DRMs. |
| **MediaDrm** | Android's framework class that talks to the CDM (Widevine or ClearKey). Equivalent of EME on web. |
| **ExoPlayer / Media3** | Google's open-source player library for Android. Knows DASH, HLS, DRM, downloads, caching. |
| **Shaka Packager** | Google's open-source CLI for packaging mezzanine → DASH/HLS with optional encryption. |
| **HDCP** | A handshake between device and display that prevents capturing protected video over HDMI. DRM enforces it. |
| **Offline license** | A license that survives device reboots and works without network, usually with a server-imposed TTL. |
| **Keyset ID** | Opaque handle the CDM gives you after a successful license fetch. You store it, and later "restore" the license without contacting the server. (Real Widevine supports this; emulator ClearKey doesn't — see [POC caveats](#14-known-limitations--poc-caveats).) |

---

## 3. How streaming video actually works

Before DRM, understand plain streaming. There are no surprises after this.

```
┌──────────┐  1. GET manifest.mpd     ┌──────────┐
│          │ ───────────────────────► │          │
│          │                          │          │
│          │  2. manifest (XML)       │          │
│          │ ◄─────────────────────── │          │
│ Player   │                          │   CDN    │
│ (Phone)  │  3. GET video_001.m4s    │ / Origin │
│          │ ───────────────────────► │          │
│          │  4. binary segment       │          │
│          │ ◄─────────────────────── │          │
│          │  ...repeat for each      │          │
└──────────┘     segment...           └──────────┘
```

1. The player asks for the **manifest** (one small XML/text file).
2. The manifest lists all available video tracks (e.g. 360p, 720p, 1080p), audio tracks, subtitle tracks, and for each one a recipe for building **segment URLs**: e.g. `video_$Number$.m4s` for `$Number$ = 1..N`.
3. The player picks a bitrate based on current bandwidth and starts downloading segments in order.
4. As it goes it keeps measuring bandwidth and switches up/down. This is **Adaptive Bitrate streaming**.

There's no "video file" being downloaded — there's a *stream of small files* that the player assembles in memory.

---

## 4. DRM playback flow — diagram + walkthrough

Add encryption to the above. Now every segment is AES-encrypted with a CEK. The player needs that CEK to decrypt, but the CEK is not in the manifest — only the KID (a pointer to the CEK) is.

```
┌──────────────┐                                              ┌──────────────┐
│              │                                              │              │
│   Android    │                                              │   Backend    │
│   App        │                                              │ (license     │
│              │                                              │  server)     │
└──────┬───────┘                                              └──────┬───────┘
       │                                                             │
       │  1. login → JWT                                             │
       │ ──────────────────────────────────────────────────────────► │
       │ ◄────────────────────────────────────────────────────────── │
       │                                                             │
       │  2. GET /catalog → list of content                          │
       │ ──────────────────────────────────────────────────────────► │
       │                                                             │
       │  3. GET manifest.mpd                                        │
       │ ───────────────────────────────────────────►   ┌───────────┐│
       │ ◄────────────── (XML w/ PSSH, KID) ─────────── │ R2 / CDN  ││
       │                                                └───────────┘│
       │  4. download encrypted segments                             │
       │ ───────────────────────────────────────────►   ┌───────────┐│
       │ ◄──────────────  (encrypted bytes) ─────────── │ R2 / CDN  ││
       │                                                └───────────┘│
       │                                                             │
       │   ┌─────────────────────────────────────────────┐           │
       │   │ Player sees PSSH → "I need a license!"      │           │
       │   │ Asks CDM: "build me a license challenge"    │           │
       │   │ CDM returns challenge bytes (or, in         │           │
       │   │ ClearKey: just the KIDs we need)            │           │
       │   └─────────────────────────────────────────────┘           │
       │                                                             │
       │  5. POST /license/clearkey  + challenge + Bearer JWT        │
       │ ──────────────────────────────────────────────────────────► │
       │                                                             │
       │       ┌───────────────────────────────────────────┐         │
       │       │ Backend checks JWT, entitlement for       │         │
       │       │ contentId, then looks up KID→CEK in DB    │         │
       │       │ and responds with JSON containing CEK     │         │
       │       └───────────────────────────────────────────┘         │
       │                                                             │
       │ ◄──────────────── 6. license JSON {keys:[{kid,k}]} ──────── │
       │                                                             │
       │   ┌─────────────────────────────────────────────┐           │
       │   │ Player hands license to CDM.                │           │
       │   │ CDM stores keys (in secure memory).         │           │
       │   │ CDM decrypts each segment as it arrives,    │           │
       │   │ feeds decoded frames to the display.        │           │
       │   │ Keys NEVER appear in your app code.         │           │
       │   └─────────────────────────────────────────────┘           │
       │                                                             │
```

### Step-by-step explanation

1. **Auth.** User logs in. Backend issues a JWT. All future calls carry `Authorization: Bearer <jwt>`.
2. **Catalog.** App fetches the list of titles. Each item has `drm: true/false`, `entitled: true/false`.
3. **Manifest fetch.** App tells ExoPlayer to play a content ID. Player requests the manifest URL. The manifest contains the bitrate ladder + a **PSSH** block declaring "this is encrypted with ClearKey, here's the KID".
4. **Segment fetch.** Player starts downloading segments. They're AES-encrypted; without keys they're garbage.
5. **License request.** The PSSH triggers the **MediaDrm / CDM** subsystem. It builds a *challenge* and the player sends it to your license endpoint. Our endpoint is `POST /license/clearkey` with `X-Content-Id` header (so backend knows which KID/CEK to look up).
6. **License response.** Backend validates the JWT, checks the user has an `Entitlement` row for this content, looks up the KID/CEK in the database, and returns the W3C-EME-format JSON:
   ```json
   { "keys": [ { "kty": "oct", "kid": "<base64url KID>", "k": "<base64url CEK>" } ], "type": "temporary" }
   ```
7. **Decryption.** Player passes the license to the CDM. From this point segments are decrypted on the fly. Decoded frames are rendered. You never see the CEK in JS/Kotlin — it lives in CDM-managed memory.

> 💡 With real Widevine the license response is a binary blob that's itself encrypted to the device's per-instance key. With ClearKey there's no such wrapping — it's just the AES key in JSON. That's why ClearKey is not "real" DRM but is great for demos.

---

## 5. Non-DRM (clear) playback

Same as DRM playback, but **steps 5–6 don't exist**. The manifest has no PSSH. Segments are not encrypted. The player downloads, decodes, displays. No license server is contacted.

This is what 99% of YouTube videos use. DRM is the exception, not the rule. Many enterprises only need DRM for premium content and serve everything else in the clear.

This repo has a `clear-test` content item to demonstrate this path. Same player, same backend manifest proxy, just no `drmConfig` in the play-manifest response.

---

## 6. Audio-only playback

DASH and HLS support **audio-only adaptation sets**. The packager just emits an audio track and no video track. Same player, same flow, same DRM mechanics if you want it. ExoPlayer / Media3 will play it like a video with no picture (UI shows just controls).

For an audio app you typically:
- Strip the video adaptation set during packaging.
- Use a smaller bitrate ladder (e.g. 64/128/256 kbps AAC or Opus).
- Replace the player surface with a waveform/album-art UI.
- Use [`MediaSession`](https://developer.android.com/media/media3/session) so lockscreen + Bluetooth controls work.

DRM mechanics are identical: you encrypt the audio CMAF segments under a CEK, ship a PSSH-bearing manifest, and your license endpoint dispenses the CEK after entitlement checks. There's nothing audio-specific about DRM.

---

## 7. Offline playback (and offline licenses)

Offline = "user is on a plane / in a tunnel, app must still play the video".

Two parts must work offline:

### Part A: the encrypted bytes

The player downloads all the segments + manifest while online and stores them in a local cache. ExoPlayer's `DownloadManager` handles this. The cache is just a directory under the app's private storage; segments stay AES-encrypted on disk.

### Part B: the license

You can't call the license server when offline. So while online, you must fetch a **persistent / offline license** and store it locally.

**The official way (real Widevine):**

1. Download segments via `DownloadManager`.
2. Call `OfflineLicenseHelper.downloadLicense(...)`. This contacts the license server with a special "I want an offline license" flag.
3. CDM returns a `keysetId` — an opaque byte array.
4. App stores `keysetId` in DataStore.
5. On playback: app reads `keysetId`, calls `MediaDrm.restoreKeys(keysetId)`, CDM loads the cached license, decryption proceeds without network.
6. Server-side, store an `OfflineLicense` row with a TTL so you can revoke / renew.

**What this demo actually does (because emulator ClearKey CDM doesn't implement `restoreKeys`):**

1. Download segments via `DownloadManager`.
2. When the download completes, the app POSTs to `/license/clearkey` *once* and stores the **raw response bytes** in DataStore (`OfflineLicenseStore`).
3. On playback: a custom `MediaDrmCallback` (`CachedClearKeyDrmCallback`) intercepts the license request and returns those cached bytes instead of hitting the network.

This is functionally identical to `restoreKeys` for ClearKey — the JSON-encoded keys are deterministic, so replaying the response is safe. For real Widevine you'd use `restoreKeys`.

### Offline-aware catalog

The catalog screen also reacts to network state and download state:

- **Online**: full catalog from `/catalog`. Cached locally.
- **Offline**: cached catalog filtered down to items the user has fully downloaded. Removing a download while offline immediately drops the item from the list.

This is implemented with `kotlinx.coroutines.flow.combine(NetworkMonitor.isOnline, DownloadRepository.downloads)`.

---

## 8. What this project actually does

A complete vertical slice of the above, end-to-end:

- **Packaging** — `packager/` has shell scripts that take a sample MP4 (Big Buck Bunny), encode it to DASH with Shaka Packager, optionally encrypt it under a known KID/CEK pair, and produce a manifest + segments. Output is uploaded once to Cloudflare R2.
- **Backend** — Express + Prisma + Postgres. Endpoints for signup/login, catalog, manifest proxy (rewrites segment URLs to go through us so we can auth them), ClearKey license issuance, entitlement, and offline-license bookkeeping.
- **Android app** — Jetpack Compose + Hilt + Media3 ExoPlayer. Login screen, catalog screen, player screen, download management. Cache-first playback (plays local copy if downloaded; falls back to online).
- **CI** — GitHub Actions workflow that builds a signed release APK on every push and uploads it as an artifact.

### Implemented today

- ClearKey DRM for protected content (`drm-test`)
- Clear/unencrypted playback (`clear-test`)
- Adaptive bitrate via DASH
- Online playback with auth + entitlement
- Full offline downloads of both DRM and clear content
- Offline license caching for DRM
- Live catalog refresh on network state change
- Offline catalog filtered to downloaded items
- Login/signup with JWT
- Same keystore signing for debug+release (POC convenience)

### Documented but not implemented

- Real Widevine license proxy (UAT or commercial)
- PlayReady / FairPlay
- HLS output (only DASH)
- Subtitle / multi-audio tracks
- HDCP / output-protection enforcement
- iOS / Web frontends

---

## 9. Architecture diagram

```
┌──────────────────────────────────────────────────────────────────────────────┐
│                              ANDROID APP                                     │
│                                                                              │
│   ┌────────────┐    ┌────────────┐    ┌──────────────────────────────────┐   │
│   │ Compose UI │ ── │ ViewModels │ ── │  Repositories (Catalog,          │   │
│   │            │    │            │    │  Download, Auth, OfflineLicense) │   │
│   └────────────┘    └────────────┘    └────────┬──────────────┬──────────┘   │
│                                                │              │              │
│                            ┌───────────────────▼──┐  ┌────────▼───────────┐  │
│                            │  Media3 ExoPlayer    │  │  Retrofit + OkHttp │  │
│                            │  + MediaDrm (CDM)    │  │  (Auth interceptor)│  │
│                            │  + DownloadManager   │  └────────┬───────────┘  │
│                            │  + SimpleCache       │           │              │
│                            └──────────┬───────────┘           │              │
└───────────────────────────────────────┼───────────────────────┼──────────────┘
                                        │                       │
                  ┌─────────────────────┘                       │
                  ▼                                             ▼
       ┌──────────────────────┐                ┌───────────────────────────────┐
       │   Cloudflare R2      │                │  Backend (Render free tier)   │
       │   (static manifest   │                │  ┌───────────────────────┐    │
       │    + AES-encrypted   │ ◄──────────────│  │ Express               │    │
       │    CMAF segments)    │  proxied seg-  │  │  /auth /catalog       │    │
       └──────────────────────┘  ment fetches  │  │  /license /offline    │    │
                                 (R2 not       │  └────────┬──────────────┘    │
                                 directly      │           │                   │
                                 reachable;    │  ┌────────▼──────────────┐    │
                                 backend       │  │ Prisma                │    │
                                 streams)      │  └────────┬──────────────┘    │
                                               └───────────┼───────────────────┘
                                                           │
                                               ┌───────────▼───────────────────┐
                                               │  Neon Postgres (free tier)    │
                                               │  Users, Content, Entitlements │
                                               └───────────────────────────────┘
```

The backend proxies all R2 access. Reasons: simpler CORS, allows per-request auth on segment fetches in production, and avoids exposing R2 credentials to clients.

---

## 10. Services used

| Service | Tier | Purpose | Why this one |
|---|---|---|---|
| **Cloudflare R2** | Free (10 GB, $0 egress) | Object storage for packaged manifests + encrypted CMAF segments | S3-compatible API, zero egress fees, generous free tier |
| **Neon** | Free (0.5 GB) | Managed Postgres | Branches per env, serverless (sleeps idle), Prisma support |
| **Render** | Free (web service) | Backend hosting | Express runs as-is, free HTTPS, blueprints, no card |
| **GitHub** | Free (public repo) | Source + CI artifact storage | Standard |
| **GitHub Actions** | Free (2000 min/mo) | Build APK on every push | Native to GitHub |
| **Shaka Packager** | OSS | One-time content packaging | Google-maintained, supports CENC + ClearKey |
| **Media3 ExoPlayer** | OSS | Android playback | Google-maintained, DASH/HLS/DRM/offline first-class |
| **Prisma** | OSS | ORM | Type-safe, easy migrations |

---

## 11. Repo layout

```
drm-e2e-demo/
├── android/                  # Jetpack Compose app (Hilt + Media3 + Retrofit)
│   ├── app/
│   │   ├── src/main/java/com/ayrindigital/drme2edemo/
│   │   │   ├── data/         # repos, api, downloads, network, storage
│   │   │   ├── di/           # Hilt modules
│   │   │   ├── downloads/    # DemoDownloadService (foreground)
│   │   │   ├── player/       # PlayerManager + DRM callback
│   │   │   ├── ui/           # Compose screens + ViewModels
│   │   │   ├── MainActivity.kt
│   │   │   └── MyApplication.kt
│   │   ├── release.keystore  # ⚠ POC keystore committed intentionally
│   │   └── build.gradle.kts
│   └── gradle.properties     # has BACKEND_BASE_URL
├── backend/                  # Express + Prisma backend
│   ├── src/
│   │   ├── auth/             # signup, login, JWT middleware
│   │   ├── catalog/          # list, detail, manifest+segment proxy
│   │   ├── license/          # /license/clearkey
│   │   ├── offline/          # offline license issuance + TTL
│   │   ├── storage/          # R2 client
│   │   ├── db/               # Prisma client
│   │   └── index.ts
│   ├── prisma/
│   │   ├── schema.prisma     # Postgres schema
│   │   ├── migrations/
│   │   └── seed.ts           # creates demo user + 2 content items
│   └── package.json
├── packager/                 # one-time content packaging scripts
│   ├── download-sample.sh    # fetches Big Buck Bunny mezzanine
│   ├── package-clear.sh      # DASH, no encryption
│   ├── package.sh            # DASH + CENC (ClearKey-compatible)
│   └── keys.json             # KID/CEK for the sample (gitignored)
├── .github/workflows/
│   └── android-apk.yml       # build signed APK on every push
├── render.yaml               # Render blueprint
├── .env.example
└── README.md                 # ← this file
```

---

## 12. Local setup

### Prerequisites

- Node.js 20.x and pnpm or npm
- JDK 21, Android Studio (with Android SDK 36)
- macOS or Linux (the packager scripts use bash; Shaka Packager binary needed only if you want to repackage content)

### Backend

```bash
cd backend
cp ../.env.example .env
# Edit .env: set DATABASE_URL + DIRECT_URL to your Neon URLs,
#            JWT_SECRET (any 32+ char string),
#            CF_* (your R2 credentials)
npm install
npx prisma migrate deploy
npx tsx prisma/seed.ts            # creates demo@example.com / password123 + 2 contents
npm run dev                       # starts on :3000
curl http://localhost:3000/health # → {"ok":true}
```

### Android (emulator)

```bash
cd android
# gradle.properties already has BACKEND_BASE_URL=https://drm-e2e-backend.onrender.com/
# For local backend testing, override at build time:
./gradlew :app:assembleDebug -PBACKEND_BASE_URL=http://10.0.2.2:3000/
# Or just install:
./gradlew :app:installDebug
```

Login with `demo@example.com` / `password123`. You'll see two items: a DRM-protected one and a clear one. Tap to play, or tap Download to fetch for offline.

### Re-packaging content (only if you change the source video)

```bash
cd packager
./download-sample.sh                # ~350 MB
./package-clear.sh bbb_1080p.mp4    # → out/clear/
./package.sh bbb_1080p.mp4          # → out/drm/ (encrypted with hardcoded KID/CEK)
# Then upload out/clear/ and out/drm/ to your R2 bucket under matching keys.
```

The KID/CEK in `package.sh` must match what's in the database (`Content.kid` / `Content.cek`). Update both if you regenerate keys.

---

## 13. Deployment

### Backend (Render + Neon)

The repo includes `render.yaml`. On Render:

1. **New +** → **Blueprint** → connect this repo → **Apply**.
2. Open the created service → **Environment** tab → set all `sync: false` vars:
   - `DATABASE_URL` (Neon pooled)
   - `DIRECT_URL` (Neon direct)
   - `JWT_SECRET`
   - `CF_BUCKET_NAME`, `CF_R2_ENDPOINT`, `CF_R2_ACCESS_KEY_ID`, `CF_R2_SECRET_ACCESS_KEY`
3. Save → Render redeploys. `start` runs `prisma migrate deploy` then `node dist/index.js`.
4. Health check at `/health`. Free tier sleeps after 15min of no traffic (~30s cold start on next request).

### Android (sharing the APK)

Every push to GitHub triggers `.github/workflows/android-apk.yml`. It produces a signed release APK as a build artifact (`app-release-<sha>.apk`) downloadable from the Actions tab for 30 days.

The keystore (`android/app/release.keystore`) is **committed on purpose** — this is a POC. Do not reuse it for any real app. Password is `android` for both store and key.

---

## 14. Known limitations / POC caveats

- **ClearKey, not Widevine.** Real DRM needs a license server with privacy keys. Switching to Widevine is documented but not wired.
- **Emulator CDM oddities.** Android emulator's ClearKey implementation lacks `MediaDrm.restoreKeys`, so we cache the license response bytes instead. On a real device with Widevine you'd use `restoreKeys` + `keysetId`.
- **No content protection enforcement.** No HDCP checks, screenshot blocking, or output-protection level inspection. That's the CDM's job in real deployments.
- **R2 credentials in env, not signed URLs.** Production would issue short-lived signed URLs for segment fetches; we proxy through the backend instead.
- **`isMinifyEnabled = false`.** R8 / ProGuard is off for build simplicity.
- **No HLS.** Apple platforms need HLS + FairPlay; this demo is DASH-only.
- **Render free tier cold starts.** ~30s on first request after idle. Fine for demo, not for prod.
- **No license expiry enforcement.** `OfflineLicense.expiresAt` is stored but the client doesn't currently check or renew.

---

## 15. Glossary index

Quick links back to the cheat sheet:

- [Bitrate ladder / ABR](#2-terminology-cheat-sheet)
- [CDM](#2-terminology-cheat-sheet)
- [CEK](#2-terminology-cheat-sheet)
- [CENC](#2-terminology-cheat-sheet)
- [DASH / HLS](#2-terminology-cheat-sheet)
- [EME / MediaDrm](#2-terminology-cheat-sheet)
- [KID](#2-terminology-cheat-sheet)
- [License / License server](#2-terminology-cheat-sheet)
- [Manifest / MPD](#2-terminology-cheat-sheet)
- [PSSH](#2-terminology-cheat-sheet)
- [Segment](#2-terminology-cheat-sheet)

---

**Maintainer**: Shubham Singh ([shubham@ayrindigital.com](mailto:shubham@ayrindigital.com))
**License**: not declared — this is a POC; do not reuse without permission.
