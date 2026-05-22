import 'dotenv/config';
import express from 'express';
import { config } from './utils/config.js';
import { logger } from './utils/logger.js';
import { prisma } from './db/client.js';
import authRoutes from './auth/routes.js';
import catalogRoutes from './catalog/routes.js';
import licenseRoutes from './license/routes.js';
import offlineRoutes from './offline/routes.js';

const app = express();

// Middleware
app.use(express.json());
app.use(express.raw({ type: 'application/octet-stream', limit: '50mb' }));

// Logging
app.use((req, res, next) => {
  logger.info({ method: req.method, path: req.path }, 'Incoming request');
  next();
});

// CORS (allow localhost + emulator)
app.use((req, res, next) => {
  const origin = req.headers.origin || '';
  const allowedOrigins = [
    'http://localhost:3000',
    'http://localhost:5173',
    'http://localhost:8081',
    /^http:\/\/10\.0\.2\.2(:\d+)?$/, // Android emulator
    /^http:\/\/192\.168\.\d+\.\d+(:\d+)?$/, // Local network
  ];

  const isAllowed = allowedOrigins.some((o) =>
    o instanceof RegExp ? o.test(origin) : o === origin
  );

  if (isAllowed) {
    res.setHeader('Access-Control-Allow-Origin', origin);
    res.setHeader('Access-Control-Allow-Credentials', 'true');
  }

  res.setHeader('Access-Control-Allow-Methods', 'GET,POST,PUT,DELETE,OPTIONS');
  res.setHeader('Access-Control-Allow-Headers', 'Content-Type,Authorization,X-Content-Id,X-Offline');

  if (req.method === 'OPTIONS') {
    return res.sendStatus(200);
  }

  next();
});

// Routes
app.use('/auth', authRoutes);
app.use('/catalog', catalogRoutes);
app.use('/license', licenseRoutes);
app.use('/offline', offlineRoutes);

// Health check
app.get('/health', (req, res) => {
  res.json({ ok: true });
});

// Error handler
app.use((err: any, req: express.Request, res: express.Response, next: express.NextFunction) => {
  logger.error(err);
  res.status(500).json({ error: 'Internal server error' });
});

// Start server
const port = config.PORT;
app.listen(port, () => {
  logger.info({ port }, 'Server started');
});

// Graceful shutdown
process.on('SIGTERM', async () => {
  logger.info('SIGTERM received, shutting down');
  await prisma.$disconnect();
  process.exit(0);
});
