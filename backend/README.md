# DRM E2E Backend

Node.js + Express + Prisma + SQLite. Implements auth, catalog, Widevine license proxy, and offline license management.

## Setup

### 1. Install dependencies

```bash
cd backend
pnpm install
```

### 2. Initialize database

```bash
# Create migrations
pnpm prisma:migrate

# Generate Prisma client
pnpm prisma:generate

# Seed with test user + content
pnpm prisma:seed
```

Creates `prisma/dev.db` (SQLite). Test user: `demo@example.com` / `password123`.

### 3. Configure environment

Copy `.env.example` → `.env`:

```bash
cp .env.example .env
```

Fill in these (others have sensible defaults):

```bash
JWT_SECRET=your-secret-key-min-32-chars-change-in-prod
DATABASE_URL=file:./prisma/dev.db
# R2 credentials (optional for now, will fill later)
CF_R2_ACCESS_KEY_ID=
CF_R2_SECRET_ACCESS_KEY=
CF_R2_ENDPOINT=https://drm-poc.r2.dev
```

### 4. Run

```bash
pnpm dev
# Server starts on http://localhost:3000
```

## API endpoints

### Auth

```bash
# Signup
curl -X POST http://localhost:3000/auth/signup \
  -H 'Content-Type: application/json' \
  -d '{"email":"user@example.com","password":"securepass123"}'

# Login
curl -X POST http://localhost:3000/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"email":"demo@example.com","password":"password123"}'
# → returns {token, userId}
```

### Catalog

All require `Authorization: Bearer <token>` header.

```bash
# List content
curl http://localhost:3000/catalog \
  -H 'Authorization: Bearer <token>'

# Get single content
curl http://localhost:3000/catalog/drm-bbb \
  -H 'Authorization: Bearer <token>'

# [DEV] Grant entitlement
curl -X POST http://localhost:3000/catalog/drm-bbb/entitle \
  -H 'Authorization: Bearer <token>'
```

### License

Send raw CDM challenge as body, expect license blob response.

```bash
# Widevine online license
curl -X POST http://localhost:3000/license/widevine \
  -H 'Authorization: Bearer <token>' \
  -H 'X-Content-Id: drm-bbb' \
  -H 'Content-Type: application/octet-stream' \
  --data-binary @challenge.bin > license.bin
```

### Offline

```bash
# Renew offline license
curl -X POST http://localhost:3000/offline/license/renew \
  -H 'Authorization: Bearer <token>' \
  -H 'Content-Type: application/json' \
  -d '{"contentId":"drm-bbb"}'

# Release offline license
curl -X POST http://localhost:3000/offline/license/release \
  -H 'Authorization: Bearer <token>' \
  -H 'Content-Type: application/json' \
  -d '{"contentId":"drm-bbb"}'
```

## Project structure

```
backend/
├── src/
│   ├── index.ts              # Express app + server
│   ├── auth/
│   │   ├── service.ts        # Signup/login logic
│   │   ├── routes.ts         # Auth endpoints
│   │   └── middleware.ts     # JWT verification
│   ├── catalog/
│   │   ├── service.ts        # Content listing
│   │   └── routes.ts         # Catalog endpoints
│   ├── license/
│   │   ├── service.ts        # Widevine license proxy
│   │   └── routes.ts         # License endpoints
│   ├── offline/
│   │   └── routes.ts         # Offline license endpoints
│   ├── db/
│   │   └── client.ts         # Prisma client
│   └── utils/
│       ├── config.ts         # Env config validation
│       ├── logger.ts         # Pino logger
│       └── jwt.ts            # JWT sign/verify
├── prisma/
│   ├── schema.prisma         # Data model
│   ├── seed.ts               # Database seed
│   └── dev.db                # SQLite (git-ignored)
├── package.json
├── tsconfig.json
└── README.md
```

## When R2 credentials arrive

1. Paste CF credentials into `.env`:
   ```bash
   CF_R2_ACCESS_KEY_ID=...
   CF_R2_SECRET_ACCESS_KEY=...
   CF_R2_ENDPOINT=https://drm-poc.r2.dev
   ```

2. Backend will automatically sign manifest URLs & fetch metadata from packaged assets.

## Next

Phase 3: Android app. Backend is ready to serve catalog + license requests.

## Troubleshooting

**"Database is locked"**
```bash
rm prisma/dev.db*
pnpm prisma:migrate
```

**"Cannot find module"**
```bash
pnpm prisma:generate
```

**License request returns error**
- Check backend logs for "License server error"
- Verify Widevine UAT URL is reachable
- Confirm CDM challenge format is correct (should be binary octet-stream)
