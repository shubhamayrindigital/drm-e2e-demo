import { z } from 'zod';

const envSchema = z.object({
  PORT: z.coerce.number().default(3000),
  NODE_ENV: z.enum(['development', 'production']).default('development'),
  JWT_SECRET: z.string().min(32),
  JWT_EXPIRY_HOURS: z.coerce.number().default(24),
  DATABASE_URL: z.string(),
  CF_BUCKET_NAME: z.string().optional(),
  CF_R2_ENDPOINT: z.string().url().optional(),
  CF_R2_ACCESS_KEY_ID: z.string().optional(),
  CF_R2_SECRET_ACCESS_KEY: z.string().optional(),
  WIDEVINE_LICENSE_SERVER_URL: z.string().url().default('https://proxy.uat.widevine.com/proxy'),
  OFFLINE_LICENSE_TTL_SECONDS: z.coerce.number().default(604800), // 7 days
});

export const config = envSchema.parse(process.env);
