# Cloudflare R2 Setup for DRM POC

Free storage for encrypted + clear video segments. 10 GB free, zero egress cost.

## 1. Create R2 bucket

1. **Sign up / Log in** to Cloudflare (https://dash.cloudflare.com).
2. Left sidebar → **R2** → **Create bucket**.
3. Bucket name: `drm-poc` (or similar).
4. **Ownership rules**: default.
5. **Create bucket** → wait ~10s.

## 2. Enable public read access

By default, R2 is private. For video playback, segments + manifests must be publicly readable (signed by the backend).

1. In bucket settings, scroll to **Public access**.
2. Click **Allow public access**.
3. Set Custom domain (optional but recommended):
   - Under **Public access**, choose **Add custom domain**.
   - Enter a subdomain you own (e.g., `r2.your-domain.com`), or use the R2 auto-assigned `*.r2.dev` domain.
   - For POC, the auto domain `{bucket}.r2.dev` is fine.

Domain will be: `https://drm-poc.r2.dev/` (adjust bucket name).

## 3. CORS policy

Video playback needs byte-range requests. Set CORS:

1. Bucket settings → **CORS**.
2. Add policy:
   ```json
   [
     {
       "AllowedOrigins": [
         "http://localhost:3000",
         "http://localhost:5173",
         "http://192.168.*.*",
         "http://10.0.0.*",
         "https://*.example.com"
       ],
       "AllowedMethods": ["GET", "HEAD", "OPTIONS"],
       "AllowedHeaders": ["*"],
       "ExposeHeaders": ["Content-Length", "Content-Range"],
       "MaxAgeSeconds": 3600
     }
   ]
   ```
   Adjust `AllowedOrigins` to match your frontend (localhost for dev, your domain for prod).

3. **Save**.

## 4. Create API token (S3-compatible credentials)

1. Top right → **Account menu** → **API Tokens**.
2. **Create Token** → **Create Custom Token**.
3. Permissions:
   - **Object List** for bucket → `drm-poc` → **Read, Write, Delete**.
   - **Object Content** for bucket → `drm-poc` → **Read, Write, Delete**.
4. **TTL**: 12 months (fine for POC).
5. **Create Token** → copy the secret. **Save it securely** (you'll only see it once).

## 5. Generate R2 API credentials

Still in API Tokens page:

1. Scroll to **R2 API Token** section (should have auto-generated on account creation).
2. If empty, click **Create R2 API Token**.
3. You'll get:
   - **Access Key ID** (e.g., `abc123def456`)
   - **Secret Access Key** (e.g., `xyz789abcdef+...`)

## 6. Environment config

Create `.env` (gitignored) at project root:

```bash
# Cloudflare R2
CF_ACCOUNT_ID="your-account-id"                  # from dash.cloudflare.com?tab=overview
CF_BUCKET_NAME="drm-poc"
CF_R2_ACCESS_KEY_ID="..."                        # from step 5
CF_R2_SECRET_ACCESS_KEY="..."                    # from step 5
CF_R2_ENDPOINT="https://drm-poc.r2.dev"          # or your custom domain

# Backend
BACKEND_URL="http://localhost:3000"
JWT_SECRET="my-secret-key-change-me-in-prod"
DATABASE_URL="file:./prisma/dev.db"

# Android emulator (if exposing backend via ngrok)
NGROK_TUNNEL="https://abc123.ngrok.io"           # optional
```

## 7. Upload samples

Once you have packaged clear + DRM segments (`./packager/out/clear/` and `./packager/out/drm/`):

**Option A: AWS CLI**

```bash
# Install aws CLI
brew install awscli

# Configure
aws configure
# Access Key ID: [paste from step 5]
# Secret Access Key: [paste from step 5]
# Default region: auto (R2 doesn't use regions)
# Default output: json

# Upload clear
aws s3 sync ./packager/out/clear/ \
  s3://drm-poc/vod/clear-bbb/ \
  --endpoint-url https://drm-poc.r2.dev \
  --region auto

# Upload DRM
aws s3 sync ./packager/out/drm/ \
  s3://drm-poc/vod/drm-bbb/ \
  --endpoint-url https://drm-poc.r2.dev \
  --region auto
```

**Option B: Node script (see `backend/scripts/upload-to-r2.js`)**

Node + `@aws-sdk/client-s3` can also upload. Backend will provide this script.

**Option C: Web UI**

Cloudflare R2 web dashboard → **Upload** button → select files. Slower for bulk; fine for testing.

## 8. Verify

```bash
# Check clear stream accessible
curl -I https://drm-poc.r2.dev/vod/clear-bbb/manifest.mpd

# Check DRM stream accessible
curl -I https://drm-poc.r2.dev/vod/drm-bbb/manifest.mpd
```

Both should return `200 OK`.

## 9. Backend integration

Backend will:
1. Read `CF_R2_ENDPOINT` + credentials from `.env`.
2. On `/play/:id/manifest`:
   - Return signed manifest URLs (HMAC-based, 5-min expiry).
   - Return license URL for DRM assets.
3. Optional: `/signed/*` endpoint that validates signature + 302s to R2 (demonstrates access control).

---

## Cost & quota notes

- **Storage**: $0.015 per GB/month. 10 GB free always. POC will use ~1-2 GB packaged assets.
- **Egress**: $0 (Cloudflare pays for egress; R2 is zero-cost egress).
- **API calls**: First 25M/month free, then cheap.
- **Custom domain**: if you use Cloudflare as DNS registrar, free; otherwise can use `*.r2.dev` subdomain.

For a small POC, you'll **never** leave free tier.

---

## When to share credentials with Claude Code

Once bucket + API token are ready:

1. Paste the output of:
   ```bash
   cat <<EOF
   CF_ACCOUNT_ID="..."
   CF_BUCKET_NAME="drm-poc"
   CF_R2_ACCESS_KEY_ID="..."
   CF_R2_SECRET_ACCESS_KEY="..."
   CF_R2_ENDPOINT="https://drm-poc.r2.dev"
   EOF
   ```
2. Claude will embed into backend `.env` template + write upload scripts.

**Keep secrets out of git**: add `.env` to `.gitignore`.
