import { Request, Response, NextFunction } from 'express';
import { getTokenFromHeader, verifyToken, JwtPayload } from '../utils/jwt.js';

declare global {
  namespace Express {
    interface Request {
      user?: JwtPayload;
    }
  }
}

export function authMiddleware(req: Request, res: Response, next: NextFunction) {
  try {
    const token = getTokenFromHeader(req.headers.authorization);
    if (!token) {
      return res.status(401).json({ error: 'Missing authorization token' });
    }

    req.user = verifyToken(token);
    next();
  } catch (error) {
    res.status(401).json({ error: 'Invalid token' });
  }
}
