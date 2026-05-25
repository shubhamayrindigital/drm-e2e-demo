import { S3Client, GetObjectCommand, PutObjectCommand } from '@aws-sdk/client-s3';
import { getSignedUrl } from '@aws-sdk/s3-request-presigner';
import { config } from '../utils/config.js';

class R2Service {
  private client: S3Client | null = null;

  private getClient(): S3Client {
    if (!this.client) {
      if (!config.CF_R2_ACCESS_KEY_ID || !config.CF_R2_SECRET_ACCESS_KEY) {
        throw new Error('R2 credentials not configured');
      }

      this.client = new S3Client({
        region: 'auto',
        endpoint: config.CF_R2_ENDPOINT || `https://${config.CF_R2_ACCESS_KEY_ID}.r2.cloudflarestorage.com`,
        credentials: {
          accessKeyId: config.CF_R2_ACCESS_KEY_ID,
          secretAccessKey: config.CF_R2_SECRET_ACCESS_KEY,
        },
      });
    }
    return this.client;
  }

  async getSignedManifestUrl(objectKey: string, expiresIn: number = 3600): Promise<string> {
    const client = this.getClient();
    const command = new GetObjectCommand({
      Bucket: config.CF_BUCKET_NAME || 'hayhouse-videos-1779739826',
      Key: objectKey,
    });

    return getSignedUrl(client, command, { expiresIn });
  }

  async getSignedMediaUrl(objectKey: string, expiresIn: number = 3600): Promise<string> {
    return this.getSignedManifestUrl(objectKey, expiresIn);
  }

  async getObject(objectKey: string) {
    const client = this.getClient();
    const command = new GetObjectCommand({
      Bucket: config.CF_BUCKET_NAME || 'hayhouse-videos-1779739826',
      Key: objectKey,
    });
    return client.send(command);
  }

  async uploadManifest(objectKey: string, content: string): Promise<void> {
    const client = this.getClient();
    const command = new PutObjectCommand({
      Bucket: config.CF_BUCKET_NAME || 'hayhouse-videos-1779739826',
      Key: objectKey,
      Body: content,
      ContentType: 'application/dash+xml',
    });

    await client.send(command);
  }
}

export const r2Service = new R2Service();
