import { Router, Request, Response } from 'express';
import { authMiddleware } from '../auth/middleware.js';
import { licenseService } from './service.js';
import { logger } from '../utils/logger.js';

const router = Router();

// Widevine license endpoint
// Client sends CDM challenge in body, expects license blob response
router.post('/widevine', authMiddleware, async (req: Request, res: Response) => {
  try {
    const contentId = req.headers['x-content-id'] as string;
    const isOffline = req.headers['x-offline'] === 'true';

    if (!contentId) {
      return res.status(400).json({ error: 'Missing x-content-id header' });
    }

    const challengeBytes = req.body as Buffer;
    const license = await licenseService.issueLicense(
      req.user!.userId,
      contentId,
      challengeBytes,
      isOffline
    );

    res.set('Content-Type', 'application/octet-stream');
    res.send(license);
  } catch (error) {
    logger.error(error);
    if (error instanceof Error && error.message.includes('not entitled')) {
      res.status(403).json({ error: error.message });
    } else {
      res.status(500).json({ error: 'License request failed' });
    }
  }
});

// PlayReady license endpoint (stub for future)
router.post('/playready', authMiddleware, async (req: Request, res: Response) => {
  res.status(501).json({ error: 'PlayReady licensing not yet implemented' });
});

// ClearKey license endpoint (W3C EME spec)
// Receives JSON {"kids":["<base64url-kid>"],"type":"temporary"} and returns
// {"keys":[{"kty":"oct","kid":"<base64url-kid>","k":"<base64url-key>"}],"type":"temporary"}
router.post('/clearkey', authMiddleware, async (req: Request, res: Response) => {
  try {
    const contentId = req.headers['x-content-id'] as string;
    if (!contentId) {
      return res.status(400).json({ error: 'Missing x-content-id header' });
    }
    const license = await licenseService.issueClearKeyLicense(
      req.user!.userId,
      contentId,
      req.body,
    );
    res.json(license);
  } catch (error) {
    logger.error(error);
    if (error instanceof Error && error.message.includes('not entitled')) {
      res.status(403).json({ error: error.message });
    } else {
      res.status(500).json({ error: 'License request failed' });
    }
  }
});

export default router;
