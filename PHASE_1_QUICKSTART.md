# Phase 1 — Content packaging & R2 setup

## What you're doing
Package a sample video (clear + DRM encrypted) and upload to Cloudflare R2. This gives you the static assets that the backend will serve to players.

## Before you start
- `shaka-packager` installed (`brew install shaka-packager`).
- Cloudflare account (free, takes 2 min).
- ~1 hour for download + packaging.

## Step-by-step

### 1. Follow R2 setup

Go to `infra/r2-setup.md` and complete steps 1–5 (create bucket, enable public read, CORS, API token).

You'll end up with these credentials:
- `CF_R2_ACCESS_KEY_ID`
- `CF_R2_SECRET_ACCESS_KEY`
- `CF_R2_ENDPOINT` (e.g., `https://drm-poc.r2.dev`)

**Don't continue until you have these.**

### 2. Download sample video

```bash
cd packager/
./download-sample.sh
# → bbb_1080p.mp4 (~350 MB, takes ~5 min on typical internet)
```

Verify:
```bash
ls -lh bbb_1080p.mp4
# Should be ~350 MB
```

### 3. Package clear stream (non-DRM)

```bash
./package-clear.sh bbb_1080p.mp4
# Runs shaka-packager without encryption
# → creates out/clear/ with manifest.mpd, master.m3u8, video.mp4, audio.mp4
```

Takes ~2 min. When done, verify:
```bash
ls -la out/clear/
# manifest.mpd  master.m3u8  video.mp4  audio.mp4
```

### 4. Package DRM stream (Widevine)

```bash
./package.sh bbb_1080p.mp4
# Runs shaka-packager with CENC encryption (test KID/CEK)
# → creates out/drm/ with encrypted segments
```

Takes ~2 min. Verify:
```bash
ls -la out/drm/
# manifest.mpd  master.m3u8  video.mp4 (encrypted)  audio.mp4 (encrypted)
```

Segments are now AES-128-CTR encrypted. Only the CDM (inside the player) can decrypt them.

### 5. Create project `.env` file

```bash
cd .. # back to project root
cp .env.example .env
```

Edit `.env` and fill in your Cloudflare R2 credentials from step 1:
```bash
CF_R2_ACCESS_KEY_ID=abc123...
CF_R2_SECRET_ACCESS_KEY=xyz789...
CF_R2_ENDPOINT=https://drm-poc.r2.dev
```

**⚠️ Never commit `.env`** (it's in `.gitignore`).

### 6. Upload to R2

Install AWS CLI (if not present):
```bash
brew install awscli
```

Configure credentials:
```bash
aws configure
# When prompted:
# AWS Access Key ID: [paste CF_R2_ACCESS_KEY_ID]
# AWS Secret Access Key: [paste CF_R2_SECRET_ACCESS_KEY]
# Default region: auto
# Default output format: json
```

Upload clear stream:
```bash
cd packager/
aws s3 sync out/clear/ \
  s3://drm-poc/vod/clear-bbb/ \
  --endpoint-url https://drm-poc.r2.dev \
  --region auto
```

Upload DRM stream:
```bash
aws s3 sync out/drm/ \
  s3://drm-poc/vod/drm-bbb/ \
  --endpoint-url https://drm-poc.r2.dev \
  --region auto
```

Should see:
```
upload: out/clear/manifest.mpd to s3://drm-poc/vod/clear-bbb/manifest.mpd
upload: out/clear/master.m3u8 to s3://drm-poc/vod/clear-bbb/master.m3u8
...
```

### 7. Verify uploads

```bash
# Clear stream manifest
curl -I https://drm-poc.r2.dev/vod/clear-bbb/manifest.mpd
# Should return 200 OK

# DRM stream manifest
curl -I https://drm-poc.r2.dev/vod/drm-bbb/manifest.mpd
# Should return 200 OK
```

If 403/404, check:
- R2 bucket **Public access** is enabled.
- CORS policy allows `GET, HEAD, OPTIONS`.
- Upload completed without errors.

## ✓ Phase 1 done when

- [x] `packager/out/clear/` and `packager/out/drm/` exist locally.
- [x] Both directories uploaded to R2 (`vod/clear-bbb/` and `vod/drm-bbb/`).
- [x] Manifest URLs return 200 OK (verified with curl).
- [x] `.env` file has CF_R2_* values filled in.

## Next phase

Phase 2: Backend skeleton (Node + Express + SQLite + Prisma).

Your backend will:
1. Read content metadata (these assets' KID/CEK) from the packager.
2. Expose `/catalog` endpoint listing the two assets.
3. Expose `/play/:id/manifest` returning signed manifest URLs pointing to R2.
4. Expose `/license/widevine` proxy forwarding CDM challenges to Google UAT server.

---

## Troubleshooting

**"shaka-packager: command not found"**
```bash
brew install shaka-packager
# Verify: which shaka-packager
```

**Download hangs**
```bash
# Try explicit timeout
timeout 600 ./download-sample.sh
# If still fails, download manually:
curl -L -o bbb_1080p.mp4 https://download.blender.org/demo/movies/BBB/bbb_sunflower_1080p_h264.mov
```

**Upload fails with "InvalidAccessKeyId"**
- Check credentials pasted into `.env` exactly.
- Re-run `aws configure` and verify output of `aws sts get-caller-identity`.

**Manifests return 403**
- R2 bucket needs **public read** enabled. Check **Public access** toggle in bucket settings.

**Manifests return 404**
- Verify upload completed. Check R2 web UI for `vod/clear-bbb/manifest.mpd` and `vod/drm-bbb/manifest.mpd`.
- If missing, try upload again.

---

## FAQ

**Why not use S3 instead of R2?**
- R2 free tier: 10 GB, zero egress. S3 free tier: 5 GB, metered egress (costs money quickly).
- R2 is faster for this POC. Plan doc covers AWS migration for future.

**Why test KID/CEK instead of generating my own?**
- Google publishes these KIDs for the UAT license server. Saves a key-generation step.
- In production, you'd use your own KID/CEK + a real license server (Axinom, EZDRM, etc.).

**Can I use multiple video files?**
- Yes. Run `package.sh other-video.mp4` to create more DRM/clear pairs.
- Backend catalog can have many assets; each needs its own KID/CEK + R2 path.

**How big will the packaged output be?**
- ~same as input (shaka-packager does not re-encode). BBB 1080p → ~700 MB total (video + audio, both clear + DRM).
- Combined with mezzanine, you're at ~1 GB total. Within free R2 tier.
