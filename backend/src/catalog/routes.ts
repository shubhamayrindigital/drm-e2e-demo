import { Router, Request, Response } from 'express';
import jwt from 'jsonwebtoken';
import { authMiddleware } from '../auth/middleware.js';
import { catalogService } from './service.js';
import { logger } from '../utils/logger.js';
import { config } from '../utils/config.js';

const router = Router();

// List all content (for logged-in user)
router.get('/', authMiddleware, async (req: Request, res: Response) => {
  try {
    const items = await catalogService.listContent(req.user!.userId);
    res.json(items);
  } catch (error) {
    logger.error(error);
    res.status(500).json({ error: 'Failed to list content' });
  }
});

// Get single content metadata
router.get('/:id', authMiddleware, async (req: Request, res: Response) => {
  try {
    const content = await catalogService.getContent(req.params.id);
    if (!content) {
      return res.status(404).json({ error: 'Not found' });
    }
    res.json(content);
  } catch (error) {
    logger.error(error);
    res.status(500).json({ error: 'Failed to get content' });
  }
});

// [DEV ONLY] Grant entitlement to user
// In production, this would be tied to a payment system / subscription
router.post('/:id/entitle', authMiddleware, async (req: Request, res: Response) => {
  try {
    await catalogService.grantEntitlement(req.user!.userId, req.params.id);
    res.json({ ok: true });
  } catch (error) {
    logger.error(error);
    res.status(500).json({ error: 'Failed to grant entitlement' });
  }
});

// Get manifest + playback token for entitled content
router.get('/:id/play', authMiddleware, async (req: Request, res: Response) => {
  try {
    const manifest = await catalogService.getPlayManifest(req.user!.userId, req.params.id);
    const playbackToken = jwt.sign(
      { userId: req.user!.userId, contentId: req.params.id },
      config.JWT_SECRET,
      { expiresIn: '5m' },
    );
    res.json({ ...manifest, playbackToken });
  } catch (error) {
    logger.error(error);
    res.status(403).json({ error: 'Not entitled to this content' });
  }
});

export default router;
