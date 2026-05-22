# Video packaging for DRM + non-DRM playback

Scripts and config for one-time packaging of mezzanine video → encrypted (DASH CENC) + clear (DASH HLS).

## Prerequisites

```bash
# Install shaka-packager
brew install shaka-packager

# Or build from source: https://github.com/google/shaka-packager
# Linux: apt-get install shaka-packager
```

## Quickstart

1. **Download sample video**
   ```bash
   ./download-sample.sh
   # → bbb_1080p.mp4 (~350 MB)
   ```

2. **Package clear (non-DRM)**
   ```bash
   ./package-clear.sh bbb_1080p.mp4
   # → out/clear/manifest.mpd, master.m3u8, video.mp4, audio.mp4
   ```

3. **Package DRM (Widevine)**
   ```bash
   ./package.sh bbb_1080p.mp4
   # → out/drm/manifest.mpd, master.m3u8, video.mp4 (encrypted), audio.mp4 (encrypted)
   ```

4. **Upload to Cloudflare R2**
   See `../infra/r2-setup.md` for bucket setup, then:
   ```bash
   aws s3 sync out/clear/ s3://drm-poc/vod/clear-bbb/ --endpoint-url https://drm-poc.r2.dev --region auto
   aws s3 sync out/drm/   s3://drm-poc/vod/drm-bbb/   --endpoint-url https://drm-poc.r2.dev --region auto
   ```

5. **Seed backend DB** with content metadata:
   Backend script will load these assets, KID/CEK from `keys.json`, and populate the `Content` table.

## What each script does

### `download-sample.sh`
- Fetches Big Buck Bunny 1080p (H.264 / AAC) from Blender Foundation CDN.
- ~350 MB, ~10 min 24 sec duration.
- CC-BY 3.0 licensed (OK for POC).

### `package-clear.sh`
- Runs shaka-packager with **no encryption**.
- Produces CMAF fragments for both DASH (`.mpd`) + HLS (`.m3u8`).
- Output:
  - `manifest.mpd` — DASH manifest.
  - `master.m3u8` — HLS master playlist.
  - `video.mp4`, `audio.mp4` — CMAF fragments (unencrypted).

### `package.sh`
- Runs shaka-packager with **CENC encryption** (Widevine).
- Uses Google's published UAT test KID/CEK (in `keys.json`).
- Segments are AES-128-CTR encrypted; CDM must decrypt.
- Output:
  - `manifest.mpd` — DASH manifest with PSSH signaling.
  - `master.m3u8` — HLS master playlist (CENC-compatible).
  - `video.mp4`, `audio.mp4` — encrypted CMAF fragments.

## Multi-bitrate ladder (optional)

Current scripts package a single rendition (1080p). For production ABR (adaptive bitrate), pre-encode multiple resolutions:

```bash
# E.g., 1080p / 720p / 480p
ffmpeg -i bbb_1080p.mp4 -c:v libx264 -crf 23 -s 1280x720 -c:a aac bbb_720p.mp4
ffmpeg -i bbb_1080p.mp4 -c:v libx264 -crf 26 -s 854x480 -c:a aac bbb_480p.mp4

# Then modify scripts to package all three:
# shaka-packager in=bbb_1080p.mp4,... in=bbb_720p.mp4,... in=bbb_480p.mp4,...
```

Omitted from this POC (FFmpeg transcode is CPU-intensive; packaging time would be 30+ min). For production, use AWS MediaConvert or Mux.

## Keys & test credentials

`keys.json` contains the Google Widevine **UAT test KID/CEK**:

```json
{
  "kid": "abba271e8bcf552bbd2e86a434a9a5d9",
  "cek": "69eaa802a6763af979e0d6ed5e2c4ed7"
}
```

These are **published** by Google for testing purposes. The license server (`proxy.uat.widevine.com`) recognizes them and issues test licenses without a production signing key.

**⚠️ Security note**: Do not use these KIDs in any production system. For production:
- Generate your own KID/CEK via a KMS (AWS KMS / HashiCorp Vault).
- Use a real Widevine license server (Axinom, EZDRM, Pallycon, or self-hosted with Google-signed operator cert).

## Troubleshooting

**`shaka-packager: command not found`**
```bash
brew install shaka-packager
# or follow https://github.com/google/shaka-packager/releases
```

**Large output files**
- `video.mp4` + `audio.mp4` will be similar size to `bbb_1080p.mp4` (compression already done by input file).
- Encryption adds ~1-2% overhead.

**Upload to R2 fails**
- Verify credentials in `.env`: `CF_R2_ACCESS_KEY_ID`, `CF_R2_SECRET_ACCESS_KEY`, `CF_R2_ENDPOINT`.
- Verify bucket allows public read (R2 settings → **Public access** → **Allow public access**).
- Check CORS policy includes `GET, HEAD, OPTIONS` and `Content-Range` header.

**License issues in player**
- If app can't fetch license, verify backend is running + proxy URL is correct.
- Check `/license/widevine` endpoint in backend logs.
- Decode license response to see if it's a real license blob or an error.

## Output structure

```
packager/
├── out/
│   ├── clear/
│   │   ├── manifest.mpd         # DASH manifest
│   │   ├── master.m3u8          # HLS master
│   │   ├── video.mp4            # clear CMAF segments
│   │   └── audio.mp4
│   └── drm/
│       ├── manifest.mpd         # DASH manifest (w/ PSSH)
│       ├── master.m3u8          # HLS master
│       ├── video.mp4            # encrypted CMAF segments
│       └── audio.mp4
├── bbb_1080p.mp4                # source (re-usable for re-packaging)
├── keys.json                    # KID/CEK reference
├── package.sh
├── package-clear.sh
├── download-sample.sh
└── README.md                    # this file
```

## Next

1. Verify uploads to R2 are public-readable (curl manifest URL).
2. Backend catalog will reference these manifest URLs; see `backend/README.md`.
