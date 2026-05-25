import { Router, Request, Response } from 'express';
import jwt from 'jsonwebtoken';
import { authMiddleware } from '../auth/middleware.js';
import { catalogService } from './service.js';
import { r2Service } from '../storage/r2Service.js';
import { logger } from '../utils/logger.js';
import { config } from '../utils/config.js';

const router = Router();

// List all content (for logged-in user)
router.get('/', authMiddleware, async (req: Request, res: Response) => {
  try {
    const items = await catalogService.listContent(req.user!.userId);
    res.json({ items });
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

// Derive R2 prefix from content's manifestPath (e.g. "drm/manifest.mpd" -> "drm")
async function getContentPrefix(id: string): Promise<string | null> {
  const content = await catalogService.getContent(id);
  if (!content) return null;
  return content.manifestPath.replace(/\/manifest\.mpd$/, '');
}

async function streamR2Object(res: Response, key: string, contentType: string) {
  const obj = await r2Service.getObject(key);
  res.type(contentType);
  if (obj.ContentLength) res.setHeader('Content-Length', String(obj.ContentLength));
  const body = obj.Body as NodeJS.ReadableStream;
  body.pipe(res);
}

// Serve manifest from R2 (relative URLs in manifest resolve to segment routes below)
router.get('/:id/manifest.mpd', async (req: Request, res: Response) => {
  try {
    const prefix = await getContentPrefix(req.params.id);
    if (!prefix) return res.status(404).json({ error: 'Not found' });
    await streamR2Object(res, `${prefix}/manifest.mpd`, 'application/dash+xml');
  } catch (error) {
    logger.error(error);
    res.status(500).json({ error: 'Failed to get manifest' });
  }
});

// Proxy DASH segments and init segments from R2
router.get('/:id/:kind(video|audio)/:file', async (req: Request, res: Response) => {
  try {
    const { id, kind, file } = req.params;
    const prefix = await getContentPrefix(id);
    if (!prefix) return res.status(404).json({ error: 'Not found' });
    const contentType = file.endsWith('.mp4') ? 'video/mp4' : 'video/iso.segment';
    await streamR2Object(res, `${prefix}/${kind}/${file}`, contentType);
  } catch (error) {
    logger.error(error);
    res.status(500).json({ error: 'Failed to get segment' });
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

    const base = publicBaseUrl(req);
    const manifestUrl = `${base}/catalog/${req.params.id}/manifest.mpd`;
    const licenseUrl = `${base}/license/clearkey`;

    res.json({ ...manifest, manifestUrl, licenseUrl, playbackToken });
  } catch (error) {
    logger.error(error);
    res.status(403).json({ error: 'Not entitled to this content' });
  }
});

function publicBaseUrl(req: Request): string {
  if (process.env.PUBLIC_BASE_URL) {
    return process.env.PUBLIC_BASE_URL.replace(/\/$/, '');
  }
  const proto = (req.headers['x-forwarded-proto'] as string)?.split(',')[0] || req.protocol;
  const host = (req.headers['x-forwarded-host'] as string) || req.get('host') || 'localhost';
  return `${proto}://${host}`;
}

// [DEV ONLY] Update manifest in R2
router.post('/dev/update-manifest', async (req: Request, res: Response) => {
  try {
    const { manifestPath, content } = req.body;
    if (!manifestPath || !content) {
      return res.status(400).json({ error: 'manifestPath and content required' });
    }
    await r2Service.uploadManifest(manifestPath, content);
    res.json({ ok: true, message: `Updated ${manifestPath}` });
  } catch (error) {
    logger.error(error);
    res.status(500).json({ error: 'Failed to update manifest' });
  }
});

export default router;
