import { Router, Request, Response } from 'express';
import { z } from 'zod';
import { authService } from './service.js';
import { logger } from '../utils/logger.js';

const router = Router();

const signupSchema = z.object({
  email: z.string().email(),
  password: z.string().min(8),
});

const loginSchema = z.object({
  email: z.string().email(),
  password: z.string(),
});

router.post('/signup', async (req: Request, res: Response) => {
  try {
    const { email, password } = signupSchema.parse(req.body);
    const { token, userId } = await authService.signup(email, password);
    res.json({ token, userId });
  } catch (error) {
    logger.error(error);
    if (error instanceof z.ZodError) {
      res.status(400).json({ error: error.errors });
    } else if (error instanceof Error) {
      res.status(400).json({ error: error.message });
    } else {
      res.status(500).json({ error: 'Unknown error' });
    }
  }
});

router.post('/login', async (req: Request, res: Response) => {
  try {
    const { email, password } = loginSchema.parse(req.body);
    const { token, userId } = await authService.login(email, password);
    res.json({ token, userId });
  } catch (error) {
    logger.error(error);
    if (error instanceof z.ZodError) {
      res.status(400).json({ error: error.errors });
    } else if (error instanceof Error) {
      res.status(401).json({ error: error.message });
    } else {
      res.status(500).json({ error: 'Unknown error' });
    }
  }
});

export default router;
