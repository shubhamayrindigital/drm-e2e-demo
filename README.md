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
7. [From MP4 to streamable content — packaging pipeline](#7-from-mp4-to-streamable-content--packaging-pipeline)
8. [Offline playback (and offline licenses)](#8-offline-playback-and-offline-licenses)
9. [What this project actually does](#9-what-this-project-actually-does)
10. [Architecture diagram](#10-architecture-diagram)
11. [Services used](#11-services-used)
12. [Repo layout](#12-repo-layout)
13. [Local setup](#13-local-setup)
14. [Deployment](#14-deployment)
15. [Known limitations / POC caveats](#15-known-limitations--poc-caveats)
16. [Glossary index](#16-glossary-index)

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
| **Input video** | The original high-quality source file (e.g. a 4K ProRes). You package *from* the input video. |
| **Packaging** | Turning the input video into a streaming-ready format (DASH or HLS), optionally encrypting it. |
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
| **Shaka Packager** | Google's open-source CLI for packaging input video → DASH/HLS with optional encryption. |
| **HDCP** | A handshake between device and display that prevents capturing protected video over HDMI. DRM enforces it. |
| **Offline license** | A license that survives device reboots and works without network, usually with a server-imposed TTL. |
| **Keyset ID** | Opaque handle the CDM gives you after a successful license fetch. You store it, and later "restore" the license without contacting the server. (Real Widevine supports this; emulator ClearKey doesn't — see [POC caveats](#15-known-limitations--poc-caveats).) |

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

## 7. From MP4 to streamable content — packaging pipeline

Before any of the playback flow above can work, somebody has to turn the original `.mp4` into the format the player expects: a manifest plus many small (optionally encrypted) segments sitting on a CDN, with metadata seeded into the backend so the license endpoint knows which key to hand out. This section follows one MP4 end-to-end.

```
┌──────────────┐   shaka-packager   ┌──────────────────┐    upload      ┌──────────────┐
│ input video  │ ─────────────────► │ DASH manifest    │ ─────────────► │ Cloudflare R2│
│ (Big Buck    │                    │  + CMAF segments │                │ vod/drm-bbb/ │
│  Bunny .mp4) │                    │  (encrypted +    │                │ vod/clear-…/ │
└──────────────┘                    │   PSSH for DRM)  │                └──────┬───────┘
                                    └──────────────────┘                       │
                                                                               │
   ┌──────────────────────────────────┐                ┌────────────────┐      │
   │ Postgres: Content row            │ ◄── seed.ts ── │ KID / CEK pair │      │
   │   { id, manifestUrl, kid, cek }  │                │ (package.sh)   │      │
   └──────────────┬───────────────────┘                └────────────────┘      │
                  │                                                            │
                  │  /catalog → /play-manifest → manifest proxy ───────────────┘
                  ▼
            Android player
```

### Step 1 — Input video

The input is a single high-quality MP4 (Big Buck Bunny, ~350 MB). `packager/download-sample.sh` pulls it once. In production this would be the master file delivered by post-production.

### Step 2 — Pick a KID/CEK (DRM path only)

A **KID** (16-byte Key ID, public) and a **CEK** (16-byte AES key, secret) are chosen up front. `packager/package.sh` hardcodes Google's published Widevine UAT test pair:

```
KID = abba271e8bcf552bbd2e86a434a9a5d9
CEK = 69eaa802a6763af979e0d6ed5e2c4ed7
```

The KID ends up inside the manifest (public — identifies *which* key is needed). The CEK lives only in the backend `Content` row and is handed to the player exclusively via the license endpoint.

### Step 3 — Run Shaka Packager

`packager/package.sh` calls `shaka-packager` once. It:

1. Demuxes the MP4 into separate video and audio streams.
2. Splits each into ~4 s CMAF fragments (`--segment_duration 4`).
3. AES-CTR-encrypts each fragment with the CEK using the CENC `cenc` scheme (`--enable_raw_key_encryption --protection_scheme cenc`).
4. Embeds a **PSSH** block listing the KID + Widevine system ID (`--protection_systems Widevine --pssh ""`).
5. Emits `video.mp4` + `audio.mp4` (init + numbered fragments), plus a DASH `.mpd` manifest and an HLS `.m3u8` master (the latter is unused by the Android app but emitted for free).

`packager/package-clear.sh` runs the same tool *without* the encryption / protection-system / pssh flags, so segments are unencrypted and the manifest contains no PSSH. The Android player handles both with identical code; only the absence of `drmConfig` on the play-manifest response distinguishes them.

### Step 4 — Upload to R2

The packaged folder (`out/drm/` or `out/clear/`) is copied to Cloudflare R2 under a stable prefix (`vod/drm-bbb/`, `vod/clear-bbb/`). R2 is private — only the backend has credentials. The bucket is never reached directly by the app; segment + manifest fetches go through the backend proxy.

### Step 5 — Seed the backend

`backend/prisma/seed.ts` inserts one `Content` row per packaged title:

| Column | Value |
|---|---|
| `id` | `drm-test` / `clear-test` |
| `manifestUrl` | R2 object key for the `.mpd` (e.g. `vod/drm-bbb/manifest.mpd`) |
| `kid` | the KID hex (DRM only) |
| `cek` | the CEK hex (DRM only) |
| `drm` | `true` / `false` |

The KID/CEK on this row **must match** the values used at packaging time. If you re-run `package.sh` with different keys, update the seed and re-apply it — otherwise the license endpoint will hand out a CEK that doesn't decrypt the segments and playback silently dies.

### Step 6 — Serve through the backend

At playback time:

- `GET /catalog` returns the list of `Content` rows (without `cek`).
- `GET /play-manifest/:id` returns the public manifest URL plus, for DRM content, the license endpoint URL and any required headers.
- The manifest is fetched through a proxy route so segment URLs can be rewritten through the backend — that's what allows per-request auth on segment fetches and keeps R2 credentials out of the client.
- `POST /license/clearkey` (with `X-Content-Id` header) looks up the row, reads `cek`, and returns the W3C-EME-format JSON `{ keys: [{ kty, kid, k }] }` that the CDM expects.

That's the full path: a raw MP4 turns into a public manifest + private CEK split between R2 and Postgres, glued together at runtime by the manifest proxy and the license endpoint. From here on the [DRM playback flow](#4-drm-playback-flow--diagram--walkthrough) takes over.

---

## 8. Offline playback (and offline licenses)

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
2. When the download completes, the app POSTs to `/license/clearkey` *once* and stores the **raw response bytes** in DataStore (`OfflineLicenseStore`), tagged with a `storedAt` timestamp.
3. On playback: a custom `MediaDrmCallback` (`CachedClearKeyDrmCallback`) intercepts the license request and returns those cached bytes instead of hitting the network.

This is functionally identical to `restoreKeys` for ClearKey — the JSON-encoded keys are deterministic, so replaying the response is safe. For real Widevine you'd use `restoreKeys`.

### Client-side license TTL + auto-cleanup

`OfflineLicenseStore` enforces a short POC TTL (`LICENSE_TTL_MS = 60_000L`, i.e. 60 s) on the cached license. `expiriesFlow` emits `contentId → expiryAt` whenever stored licenses change. `DownloadRepository` observes it and schedules a per-content cleanup job that, on expiry, calls `downloadManager.removeDownload(id)` and clears the license entry — so an expired offline title automatically reverts to online-only and disappears from the offline catalog. The catalog UI shows a live countdown so this is visible by hand. Bump the constant for production-realistic windows.

### Offline-aware catalog

The catalog screen also reacts to network state and download state:

- **Online**: full catalog from `/catalog`. Cached locally.
- **Offline**: cached catalog filtered down to items the user has fully downloaded. Removing a download while offline immediately drops the item from the list.

`CatalogViewModel` runs two independent collectors: the `NetworkMonitor.isOnline` flow is the only signal that triggers a `/catalog` fetch (so flipping download state never thrashes the network), and the `DownloadRepository.downloads` flow re-filters the cached catalog while offline (so a license-expiry removal slides the title out immediately).

---

## 9. What this project actually does

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
- Client-side offline-license TTL with live per-row countdown in the catalog
- Automatic deletion of expired offline downloads (revert to online-only)
- Live catalog refresh on network state change
- Offline catalog filtered to downloaded items
- Login/signup with JWT
- Dark-only Material 3 theme with Scaffold + TopAppBar across all screens, edge-to-edge insets, FLAG_SECURE on the player, and an in-app glossary dialog
- Reusable `LoadingDialog` for network-bound operations
- Same keystore signing for debug+release (POC convenience)

### Documented but not implemented

- Real Widevine license proxy (UAT or commercial)
- PlayReady / FairPlay
- HLS output (only DASH)
- Subtitle / multi-audio tracks
- HDCP / output-protection enforcement
- iOS / Web frontends

---

## 10. Architecture diagram

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

## 11. Services used

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

## 12. Repo layout

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
│   ├── download-sample.sh    # fetches Big Buck Bunny input video
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

## 13. Local setup

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
# To point at a local backend, run `adb reverse tcp:3000 tcp:3000` then build with:
./gradlew :app:assembleDebug -PBACKEND_BASE_URL=http://localhost:3000/
# Or just install pointing at the deployed backend:
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

## 14. Deployment

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

## 15. Known limitations / POC caveats

- **ClearKey, not Widevine.** Real DRM needs a license server with privacy keys. Switching to Widevine is documented but not wired.
- **Emulator CDM oddities.** Android emulator's ClearKey implementation lacks `MediaDrm.restoreKeys`, so we cache the license response bytes instead. On a real device with Widevine you'd use `restoreKeys` + `keysetId`.
- **No content protection enforcement.** No HDCP checks, screenshot blocking, or output-protection level inspection. That's the CDM's job in real deployments.
- **R2 credentials in env, not signed URLs.** Production would issue short-lived signed URLs for segment fetches; we proxy through the backend instead.
- **`isMinifyEnabled = false`.** R8 / ProGuard is off for build simplicity.
- **No HLS.** Apple platforms need HLS + FairPlay; this demo is DASH-only.
- **Render free tier cold starts.** ~30s on first request after idle. Fine for demo, not for prod.
- **Partial license-expiry enforcement.** Client enforces a POC 60 s TTL on the cached license bytes and auto-deletes the offline download on expiry. The backend's `OfflineLicense.expiresAt` row is recorded but not yet cross-checked or renewed against the client TTL.

---

## 16. Glossary index

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
