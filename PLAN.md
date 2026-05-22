# DRM E2E POC — Implementation Plan

End-to-end Widevine DRM + non-DRM video playback POC on Android (Kotlin + Jetpack Compose) and Web (Shaka Player), with a Node.js backend. Zero-cost stack; production-shaped where free options exist; explicit notes where paid services are required for true production.

---

## 0. Locked scope (from grilling session)

| Decision | Value |
|---|---|
| DRM systems built | Widevine only (Android + Web/Chrome). PlayReady + FairPlay documented for production handoff. |
| License server | Hybrid: own Node license proxy that forwards challenges to Google's public Widevine UAT license server (`https://proxy.uat.widevine.com/proxy`). Entitlement, auth, and policy decisions live in our proxy. |
| Content + packaging | Pre-package public sample MP4 (Big Buck Bunny / Tears of Steel) → DASH + CENC via shaka-packager CLI, run locally once. Static output uploaded to Cloudflare R2. |
| Auth model | Full: signup + login (email/password, bcrypt), JWT access tokens, SQLite persistence, entitlement table, persistent offline-license records with server-side TTL. |
| Storage / CDN | Cloudflare R2 public bucket (free tier, 10 GB, zero egress). Plan documents Akamai → AWS S3+CloudFront migration. |
| Offline | Both DRM and non-DRM offline downloads. ExoPlayer DownloadManager. Persistent Widevine offline license w/ server-issued TTL + renewal stub. |
| Non-DRM demo | HLS + DASH clear streams (same shaka-packager pipeline minus encryption). |
| Frontends built | Android (Compose) + Web (Shaka Player). iOS / AVPlayer / FairPlay documented only. |

---

## 1. Repository layout

```
drm-e2e-demo/
├── android/                # existing Compose starter (target of code work)
├── backend/                # Node.js + Express + SQLite + Prisma
│   ├── src/
│   │   ├── auth/           # signup, login, JWT
│   │   ├── catalog/        # content list, entitlement
│   │   ├── license/        # Widevine license proxy
│   │   ├── offline/        # offline license issue + TTL tracking
│   │   ├── signing/        # signed URL helper for R2 segments
│   │   └── db/             # prisma schema + migrations
│   ├── prisma/
│   └── package.json
├── packager/               # shaka-packager scripts + sample manifests
│   ├── package.sh          # encode + encrypt + upload
│   ├── package-clear.sh    # non-DRM HLS+DASH
│   └── keys.json           # generated KIDs + CEKs (gitignored)
├── web/                    # Shaka Player single-page demo
│   ├── index.html
│   ├── main.js
│   └── package.json
├── infra/
│   ├── r2-setup.md         # Cloudflare R2 bucket + token setup
│   └── env.example
├── docs/
│   ├── architecture.md     # diagrams from this brief
│   ├── production-handoff.md   # what changes for prod (FairPlay, Akamai, KMS)
│   └── ios-fairplay.md     # documented-only iOS plan
└── PLAN.md                 # this file
```

---

## 2. Phase 0 — Tooling & accounts (free)

1. Install `shaka-packager` (Homebrew: `brew install shaka-packager`).
2. Install Node 20 LTS, pnpm.
3. Cloudflare account → create R2 bucket `drm-poc`. Generate API token (S3-compatible). Enable public read on a custom subdomain.
4. Download free sample mezzanine: Big Buck Bunny 1080p MP4 (Blender Foundation, CC-BY).
5. JDK 17, Android Studio Iguana+. Use existing `android/` starter as-is.
6. Optional: ngrok (free tier) to expose backend to physical Android device.

Estimated cost: $0.

---

## 3. Phase 1 — Content packaging (one-time, local)

### 3.1 Non-DRM clear stream

`packager/package-clear.sh`:

```bash
shaka-packager \
  in=bbb_1080p.mp4,stream=video,output=out/clear/video_1080.mp4,playlist_name=video_1080.m3u8 \
  in=bbb_1080p.mp4,stream=video,output=out/clear/video_720.mp4,playlist_name=video_720.m3u8 \
  in=bbb_1080p.mp4,stream=audio,output=out/clear/audio.mp4,playlist_name=audio.m3u8 \
  --hls_master_playlist_output out/clear/master.m3u8 \
  --mpd_output out/clear/manifest.mpd \
  --segment_duration 4
```

Outputs CMAF fragments + DASH `.mpd` + HLS `.m3u8`. Upload `out/clear/` to R2 path `vod/clear-bbb/`.

> Multi-bitrate ladder: real transcoding ladder (e.g., 1080p/720p/480p) requires a transcoder. For the POC we'll either (a) ship a single rendition and document the ladder, or (b) pre-encode three renditions with FFmpeg locally (free, slow). Pick (b) only if presentation needs ABR demo.

### 3.2 DRM (Widevine CENC) stream

Use Google's Widevine UAT test keys (publicly documented). These KIDs are accepted by Google's UAT license server out of the box:

```
content_id = "fkj3ljaSdfalkr3j"
KID        = "abba271e8bcf552bbd2e86a434a9a5d9"
CEK        = "69eaa802a6763af979e0d6ed5e2c4ed7"
```

`packager/package.sh`:

```bash
shaka-packager \
  in=bbb_1080p.mp4,stream=video,output=out/drm/video.mp4 \
  in=bbb_1080p.mp4,stream=audio,output=out/drm/audio.mp4 \
  --enable_raw_key_encryption \
  --keys label=CENC:key_id=abba271e8bcf552bbd2e86a434a9a5d9:key=69eaa802a6763af979e0d6ed5e2c4ed7 \
  --protection_scheme cenc \
  --protection_systems Widevine \
  --pssh "" \
  --mpd_output out/drm/manifest.mpd \
  --hls_master_playlist_output out/drm/master.m3u8 \
  --segment_duration 4
```

Persist KID/CEK + content metadata into backend DB so the license proxy can issue policies for this asset.

Upload `out/drm/` to R2 path `vod/drm-bbb/`.

### 3.3 R2 hosting

- Public-read bucket, CORS allows `GET, HEAD` from frontend origins (Android emulator host, localhost web).
- Custom domain or `r2.dev` subdomain for the URLs we hand to players.
- For production-shape realism, all manifest URLs returned by the backend will be HMAC-signed with a short expiry, even though R2 itself serves public. (Backend validates signature on a proxy route → 302 redirect to R2 URL.)

---

## 4. Phase 2 — Backend (Node + Express + SQLite + Prisma)

### 4.1 Stack

- `express`, `zod` (validation), `jsonwebtoken`, `bcrypt`, `prisma` + `sqlite`, `node-fetch`, `pino` (logs), `dotenv`.
- TypeScript.

### 4.2 Data model (Prisma)

```prisma
model User {
  id           String   @id @default(cuid())
  email        String   @unique
  passwordHash String
  createdAt    DateTime @default(now())
  entitlements Entitlement[]
  offlineLicenses OfflineLicense[]
}

model Content {
  id           String   @id            // "drm-bbb" or "clear-bbb"
  title        String
  drm          Boolean
  manifestPath String                  // R2 key
  kid          String?                 // hex, null if clear
  cek          String?                 // hex, null if clear (POC only; prod uses KMS)
  pssh         String?
}

model Entitlement {
  id        String  @id @default(cuid())
  userId    String
  contentId String
  expiresAt DateTime?
  user      User    @relation(fields: [userId], references: [id])
  content   Content @relation(fields: [contentId], references: [id])
  @@unique([userId, contentId])
}

model OfflineLicense {
  id         String   @id @default(cuid())
  userId     String
  contentId  String
  keysetId   String                    // opaque CDM keyset id reported by client
  issuedAt   DateTime @default(now())
  expiresAt  DateTime                  // server-side TTL mirror
  revoked    Boolean  @default(false)
  user       User     @relation(fields: [userId], references: [id])
}
```

### 4.3 Endpoints

| Method | Path | Purpose |
|---|---|---|
| POST | `/auth/signup` | email + password → user row, returns JWT |
| POST | `/auth/login` | returns JWT |
| GET  | `/catalog` | list content; for logged-in user marks entitled items |
| POST | `/catalog/:id/entitle` | dev convenience — grant entitlement (POC only) |
| GET  | `/play/:id/manifest` | returns `{ manifestUrl, drm: { licenseUrl, token } }`; manifestUrl is signed, expires in 5 min |
| POST | `/license/widevine` | **license proxy** — verifies short-lived playback token + entitlement, forwards CDM challenge body to Google UAT license server, returns license blob |
| POST | `/offline/license` | issues a persistent license (proxied as above with `persistent_license=true`, `rental_duration` set), records `OfflineLicense` row, returns license blob |
| POST | `/offline/renew` | re-issue persistent license if entitlement still valid and not revoked |
| POST | `/offline/release` | mark license released (client must also `releaseSecureStop`) |

### 4.4 License proxy core logic

```ts
// pseudo
app.post('/license/widevine', auth, async (req, res) => {
  const { contentId, playbackToken } = parseHeaders(req);
  verifyPlaybackToken(playbackToken, req.user.id, contentId);
  const ent = await db.entitlement.find(req.user.id, contentId);
  if (!ent || expired(ent)) return res.status(403).end();

  const policy = {
    policy_overrides: {
      can_play: true,
      can_persist: false,            // online flow
      can_renew: false,
      license_duration_seconds: 3600,
      rental_duration_seconds: 0,
      playback_duration_seconds: 3600,
      hdcp: 'HDCP_V1',               // require HDCP for HD
    },
    content_id: contentId,
    // raw body from CDM
    drm_info: req.body,              // the challenge bytes
  };

  // For Google UAT, the test server accepts the challenge directly when
  // the asset uses the published test KIDs. The "policy" is applied here
  // in the proxy layer (we *gate* before forwarding). In a real production
  // deployment, the proxy signs a request to the Widevine license server
  // with the operator's signing key.
  const upstream = await fetch('https://proxy.uat.widevine.com/proxy', {
    method: 'POST',
    body: req.body,
    headers: { 'Content-Type': 'application/octet-stream' },
  });
  const license = Buffer.from(await upstream.arrayBuffer());
  res.set('Content-Type', 'application/octet-stream').send(license);
});
```

Offline variant identical except entitlement check writes an `OfflineLicense` row, and the proxy intends a persistent license (client requests `OFFLINE` key type via MediaDrm; UAT honors it for the test KIDs).

### 4.5 Manifest signing

- `/play/:id/manifest` returns `manifestUrl = https://r2.example/vod/drm-bbb/manifest.mpd?exp=...&sig=HMAC(...)`.
- A thin Express route `/signed/*` validates `exp + sig` then 302s to R2. Demonstrates the signed-URL pattern without paying for Akamai token auth or CloudFront signed URLs.

### 4.6 Production-handoff notes (in `docs/production-handoff.md`)

- Replace Google UAT with vendor (Axinom/EZDRM/Pallycon) or self-hosted Widevine + signed service key.
- Move CEK out of SQLite → AWS KMS / HashiCorp Vault; backend only fetches at license-issue time.
- Add PlayReady proxy (`/license/playready`) — same flow, different upstream + challenge format.
- FairPlay flow (separate, see `docs/ios-fairplay.md`).
- Swap R2 → Akamai NetStorage + AMD + token-auth, or AWS S3 + CloudFront + signed cookies.

---

## 5. Phase 3 — Android app (Kotlin + Compose + Media3 ExoPlayer)

### 5.1 Dependencies to add to [app/build.gradle.kts](android/app/build.gradle.kts)

```kotlin
// version catalog additions in libs.versions.toml
media3 = "1.4.1"
retrofit = "2.11.0"
okhttp = "4.12.0"
datastore = "1.1.1"
hilt = "2.51.1"
room = "2.6.1"
navCompose = "2.8.0"
coil = "2.7.0"

// dependencies
implementation("androidx.media3:media3-exoplayer:1.4.1")
implementation("androidx.media3:media3-exoplayer-dash:1.4.1")
implementation("androidx.media3:media3-exoplayer-hls:1.4.1")
implementation("androidx.media3:media3-datasource-okhttp:1.4.1")
implementation("androidx.media3:media3-ui:1.4.1")
implementation("androidx.media3:media3-session:1.4.1")
implementation("androidx.media3:media3-database:1.4.1")

implementation("com.squareup.retrofit2:retrofit:2.11.0")
implementation("com.squareup.retrofit2:converter-kotlinx-serialization:2.11.0")
implementation("com.squareup.okhttp3:okhttp:4.12.0")
implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")
implementation("androidx.datastore:datastore-preferences:1.1.1")
implementation("androidx.room:room-runtime:2.6.1")
implementation("androidx.room:room-ktx:2.6.1")
kapt("androidx.room:room-compiler:2.6.1")
implementation("io.coil-kt:coil-compose:2.7.0")
implementation("androidx.navigation:navigation-compose:2.8.0")
implementation("com.google.dagger:hilt-android:2.51.1")
kapt("com.google.dagger:hilt-compiler:2.51.1")
implementation("androidx.hilt:hilt-navigation-compose:1.2.0")
```

### 5.2 Module layout

```
com.ayrindigital.drme2edemo/
├── data/
│   ├── api/                # Retrofit interfaces (Auth, Catalog, License, Offline)
│   ├── auth/               # TokenStore (DataStore), AuthRepository
│   ├── catalog/            # CatalogRepository
│   └── offline/            # OfflineDb (Room), DownloadsRepository
├── drm/
│   ├── LicenseHttpCallback.kt   # custom HttpMediaDrmCallback variant — injects JWT
│   ├── DrmSessionManagerFactory.kt
│   └── OfflineLicenseHelper.kt  # acquire/release/renew persistent licenses
├── player/
│   ├── PlayerHost.kt       # ExoPlayer factory, DataSource w/ OkHttp+auth interceptor
│   ├── PlayerScreen.kt     # Compose PlayerView wrapper
│   └── PlayerViewModel.kt
├── downloads/
│   ├── DemoDownloadService.kt
│   ├── DownloadManagerProvider.kt
│   └── DownloadTracker.kt
├── ui/
│   ├── auth/               # SignupScreen, LoginScreen
│   ├── catalog/            # CatalogScreen
│   ├── player/             # PlayerScreen
│   └── downloads/          # DownloadsScreen
├── di/                     # Hilt modules
└── MainActivity.kt
```

### 5.3 Auth + Catalog UI

- Three Compose screens behind `NavHost`: Login → Catalog → Player. Downloads tab as bottom nav.
- `TokenStore` (Preferences DataStore) holds JWT.
- OkHttp `Interceptor` injects `Authorization: Bearer <jwt>` on all backend calls.
- `AndroidManifest`: `<uses-permission android:name="android.permission.INTERNET" />`, foreground-service permission for `DemoDownloadService`.

### 5.4 Online DRM playback wiring

```kotlin
fun buildExoPlayer(content: PlayDescriptor, token: String): ExoPlayer {
  val httpFactory = OkHttpDataSource.Factory(okHttpClient)

  val drmCallback = HttpMediaDrmCallback(
    /* defaultLicenseUrl = */ content.licenseUrl, // e.g. https://api/license/widevine
    httpFactory
  ).apply {
    setKeyRequestProperty("Authorization", "Bearer $token")
    setKeyRequestProperty("X-Content-Id", content.contentId)
    setKeyRequestProperty("X-Playback-Token", content.playbackToken)
  }

  val drmSessionManager = DefaultDrmSessionManager.Builder()
    .setUuidAndExoMediaDrmProvider(C.WIDEVINE_UUID, FrameworkMediaDrm.DEFAULT_PROVIDER)
    .setMultiSession(false)
    .build(drmCallback)

  val mediaSource = DashMediaSource.Factory(httpFactory)
    .setDrmSessionManagerProvider { drmSessionManager }
    .createMediaSource(MediaItem.fromUri(content.manifestUrl))

  return ExoPlayer.Builder(context)
    .setMediaSourceFactory(DefaultMediaSourceFactory(httpFactory))
    .build().apply { setMediaSource(mediaSource); prepare() }
}
```

PlayerScreen uses `AndroidView` wrapping `PlayerView` from `media3-ui`.

`FLAG_SECURE` set on the Activity window while a DRM playback is active (prevents screenshots / external mirroring).

### 5.5 Non-DRM playback wiring

Same as above minus `DrmSessionManager`. Supports both DASH (`DashMediaSource`) and HLS (`HlsMediaSource`) — pick based on `content.streamType`.

### 5.6 Offline downloads (DRM + non-DRM)

ExoPlayer's `DownloadManager` + a `DownloadService`.

```kotlin
// DownloadHelper for DASH
val helper = DownloadHelper.forMediaItem(
  context,
  MediaItem.Builder()
    .setUri(content.manifestUrl)
    .setDrmConfiguration(if (content.drm) drmConfig(content, token) else null)
    .build(),
  DefaultTrackSelector(context),
  DefaultRenderersFactory(context)
)

helper.prepare(object : DownloadHelper.Callback {
  override fun onPrepared(h: DownloadHelper) {
    if (content.drm) {
      // Acquire persistent offline license up front
      val offlineHelper = OfflineLicenseHelper.newWidevineInstance(
        content.licenseUrl,
        /* forceDefaultLicenseUrl = */ true,
        httpFactory,
        mapOf(
          "Authorization" to "Bearer $token",
          "X-Content-Id" to content.contentId,
          "X-Offline" to "true",
        ),
        DrmSessionEventListener.EventDispatcher()
      )
      val keySetId = offlineHelper.downloadLicense(getFormatWithDrmInitData(h))
      offlineDb.put(content.contentId, keySetId, expiresAtFromServerResponse)
      offlineHelper.release()
    }
    val request = h.getDownloadRequest(content.contentId.toByteArray())
      .copyWithKeySetId(keySetIdOrNull)
    DownloadService.sendAddDownload(context, DemoDownloadService::class.java, request, false)
  }
})
```

Offline playback path: when constructing the `DashMediaSource` for an offline asset, attach the stored `keySetId` to the `DrmConfiguration`. ExoPlayer's MediaDrm uses the persistent license without contacting the network.

**TTL enforcement**:
- Server response on `/offline/license` includes `expiresAt` (ISO). Stored in Room.
- Before playback of an offline DRM asset, app checks `expiresAt`. If within renewal window (24h), call `/offline/renew`. If expired, call `OfflineLicenseHelper.renewLicense(keySetId)` and update DB; on failure, show "License expired — please renew online."
- Client-side check is UX; the CDM itself enforces hard expiry via the `playback_duration_seconds` / `rental_duration_seconds` baked into the license.

### 5.7 Lifecycle, errors

- `DrmSessionException` → distinguish:
  - `ERROR_KEYS_EXPIRED` → trigger renewal.
  - `ERROR_NO_INTERNET_DURING_PROVISIONING` → guide user.
  - 403 from license proxy → re-login or surface "Not entitled."
- Release ExoPlayer in `onStop` (not `onPause`) to allow PiP later.

### 5.8 Security level check (production-grade)

```kotlin
val mediaDrm = FrameworkMediaDrm.newInstance(C.WIDEVINE_UUID)
val level = mediaDrm.getPropertyString("securityLevel")  // "L1" / "L3"
mediaDrm.release()
// Surface in UI; gate HD content if L3 (mirrors prod policy)
```

---

## 6. Phase 4 — Web demo (Shaka Player)

A single `index.html` + `main.js`. Two pages (login + player) with vanilla JS; goal is to prove the same backend serves both clients.

```js
const player = new shaka.Player();
await player.attach(videoElement);

player.configure({
  drm: {
    servers: {
      'com.widevine.alpha': `${API}/license/widevine`,
    },
  },
});

player.getNetworkingEngine().registerRequestFilter((type, req) => {
  if (type === shaka.net.NetworkingEngine.RequestType.LICENSE) {
    req.headers['Authorization'] = `Bearer ${jwt}`;
    req.headers['X-Content-Id'] = contentId;
    req.headers['X-Playback-Token'] = playbackToken;
  }
});

await player.load(manifestUrl);
```

Web offline (PWA-style) is **not** implemented — EME persistent licenses on Chrome require complex storage and the UAT server's persistent-license support for browser CDMs is flaky. Documented as a production task.

---

## 7. Phase 5 — Demo flow / acceptance criteria

A successful run looks like:

1. `pnpm --filter backend dev` brings up API on `:3000`.
2. `packager/package.sh` already executed; assets live on R2.
3. Web demo signs up `demo@x.com`, sees catalog with two entries: **BBB (DRM)** and **BBB (Clear)**, both auto-entitled (POC convenience).
4. Plays DRM stream in Chrome → license request visible in DevTools hitting `/license/widevine`.
5. Same user signs into Android app on emulator (API ≥ 31), plays both streams. Frames render.
6. Android download flow:
   - DRM asset: download progresses, persistent license issued, airplane mode → still plays.
   - Clear asset: download progresses, airplane mode → still plays.
7. Force-expire the offline license in SQLite (`expiresAt = past`) → reopen download → renewal prompt → success after renew.
8. Logout, attempt playback → 401 from manifest endpoint → UI surfaces.

---

## 8. What is intentionally NOT included (and why)

| Item | Why deferred | Where covered |
|---|---|---|
| FairPlay / iOS | Requires paid Apple Developer cert + Apple-signed FPS deployment package | `docs/ios-fairplay.md` (flow doc) |
| PlayReady proxy | Browser scope already covered by Widevine on Chrome; Edge testing would need Windows | `docs/production-handoff.md` |
| Real multi-bitrate ladder | Free transcoding is slow & off-topic | Note in `packager/README.md` |
| Live (low-latency) streams | POC is VOD per requirements | — |
| Real KMS for CEK storage | Costs money | `docs/production-handoff.md` |
| Akamai token auth | Client account; can't test for free | `docs/production-handoff.md` |
| Concurrent stream limit, geofencing, analytics | Out of POC scope; pattern is "add checks in license proxy" | `docs/production-handoff.md` |

---

## 9. Production handoff doc (what the client gets)

`docs/production-handoff.md` covers, with concrete code-pointer references back to the POC:

1. Multi-DRM packaging: switch `--protection_systems` to `Widevine,PlayReady` + add CBCS profile; same CMAF output works for FairPlay HLS via `--hls_master_playlist_output` with CBCS protection scheme.
2. License-server vendor selection matrix (Axinom vs EZDRM vs Pallycon vs self-host) with cost/feature axes.
3. CEK → AWS KMS migration: backend pseudo-flow.
4. Origin/CDN migration: Akamai NetStorage + AMD + token-auth-2.0 today → optional AWS S3 + CloudFront + Signed Cookies (or Lambda@Edge for token auth) in future. Manifest/segment URL shape stays identical from the player's perspective; only the backend's signing module changes.
5. Output protection / HDCP policy table per content tier.
6. Concurrent stream enforcement: Redis-backed session table keyed on `user_id`, decremented on license `release` or 5-min idle TTL.
7. Geo-blocking: MaxMind GeoLite2 (free) in license proxy.
8. Analytics: log every license issue/deny + ABR events to a sink (Mux Data trial or self-host).
9. FairPlay (iOS) full flow doc.
10. Forensic watermarking (NexGuard / ContentArmor) — paid, vendor pick.

---

## 10. Build order (recommended)

1. **Packager** runs locally → R2 has assets. ✅ visible via direct URL.
2. **Backend skeleton**: signup/login/catalog. Manifest endpoint returns unsigned R2 URL.
3. **Android catalog + clear (non-DRM) playback**. Smoke test ExoPlayer path.
4. **Web clear playback**. Confirms shared catalog/auth.
5. **License proxy + Widevine online playback** (Android + Web).
6. **Signed-URL middleware**.
7. **Android offline (non-DRM first, then DRM with persistent license + TTL)**.
8. **Renewal + revoke flows**.
9. **Polish UI, error states, FLAG_SECURE, security-level gating**.
10. **Write `docs/`**.

Each step ends with a working app — no dead branches.

---

## 11. Risks / known gotchas

- Android emulator Widevine is **L3 only**. HD-only policies will block playback. POC uses SD-tier policy on emulator; physical device demoes L1.
- ExoPlayer offline-license API surface changed across Media3 versions; pin to `1.4.1` and follow Media3 sample `OfflineLicenseFetcher`.
- Google UAT license server is rate-limited and intended for testing; do not load-test against it. Production must use a real license server.
- `proxy.uat.widevine.com` test KIDs are widely published — POC must not be presented as a "secured" production system.
- R2 free tier has no Signed-URL primitive equivalent to S3 presigning; the backend `/signed/*` redirect pattern stands in.
- CORS on R2 must include `Range` request headers for video byte-range fetches.

---

## 12. Definition of done

- [ ] `packager/package.sh` reproducibly produces uploaded DRM + clear assets.
- [ ] Backend serves the 9 endpoints listed in §4.3 with Prisma migrations checked in.
- [ ] Android app: signup → catalog → online DRM playback → online clear playback → offline DRM download + airplane-mode playback → offline clear download + airplane-mode playback → TTL renewal demo.
- [ ] Web app: signup → catalog → online DRM playback in Chrome → online clear playback.
- [ ] `docs/architecture.md`, `docs/production-handoff.md`, `docs/ios-fairplay.md` written.
- [ ] README at repo root has 5-minute quickstart.
