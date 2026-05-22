import { Router, Request, Response } from 'express';
import { z } from 'zod';
import { authMiddleware } from '../auth/middleware.js';
import { licenseService } from '../license/service.js';
import { prisma } from '../db/client.js';
import { logger } from '../utils/logger.js';

const router = Router();

const releaseSchema = z.object({
  contentId: z.string(),
  keysetId: z.string(),
});

// Renew an offline license before it expires
router.post('/license/renew', authMiddleware, async (req: Request, res: Response) => {
  try {
    const { contentId } = z.object({ contentId: z.string() }).parse(req.body);

    const license = await licenseService.renewOfflineLicense(req.user!.userId, contentId);
    res.set('Content-Type', 'application/octet-stream');
    res.send(license);
  } catch (error) {
    logger.error(error);
    res.status(500).json({ error: 'License renewal failed' });
  }
});

// Mark offline license as released
router.post('/license/release', authMiddleware, async (req: Request, res: Response) => {
  try {
    const { contentId } = releaseSchema.parse(req.body);

    await prisma.offlineLicense.update({
      where: { userId_contentId: { userId: req.user!.userId, contentId } },
      data: { revoked: true },
    });

    res.json({ ok: true });
  } catch (error) {
    logger.error(error);
    res.status(500).json({ error: 'License release failed' });
  }
});

export default router;
