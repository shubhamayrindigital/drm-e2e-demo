import bcrypt from 'bcrypt';
import { prisma } from '../db/client.js';
import { signToken, JwtPayload } from '../utils/jwt.js';
import { logger } from '../utils/logger.js';

export class AuthService {
  async signup(email: string, password: string): Promise<{ token: string; userId: string }> {
    // Check if user exists
    const existing = await prisma.user.findUnique({ where: { email } });
    if (existing) {
      throw new Error('User already exists');
    }

    // Hash password
    const passwordHash = await bcrypt.hash(password, 12);

    // Create user
    const user = await prisma.user.create({
      data: { email, passwordHash },
    });

    logger.info({ userId: user.id, email }, 'User signed up');

    // Issue token
    const token = signToken({ userId: user.id, email: user.email });

    return { token, userId: user.id };
  }

  async login(email: string, password: string): Promise<{ token: string; userId: string }> {
    const user = await prisma.user.findUnique({ where: { email } });
    if (!user) {
      throw new Error('Invalid credentials');
    }

    const isValid = await bcrypt.compare(password, user.passwordHash);
    if (!isValid) {
      throw new Error('Invalid credentials');
    }

    logger.info({ userId: user.id, email }, 'User logged in');

    const token = signToken({ userId: user.id, email: user.email });

    return { token, userId: user.id };
  }
}

export const authService = new AuthService();
