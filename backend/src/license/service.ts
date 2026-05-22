import { prisma } from '../db/client.js';
import { config } from '../utils/config.js';
import { logger } from '../utils/logger.js';
import fetch from 'node-fetch';

export class LicenseService {
  /**
   * Issue a Widevine license by forwarding challenge to Google UAT server
   * In production, this would sign the request with the operator's key
   */
  async issueLicense(
    userId: string,
    contentId: string,
    challengeBytes: Buffer,
    isOffline: boolean
  ): Promise<Buffer> {
    // Verify user is entitled to this content
    const entitlement = await prisma.entitlement.findUnique({
      where: { userId_contentId: { userId, contentId } },
    });

    if (!entitlement) {
      throw new Error('User not entitled to this content');
    }

    // Get content metadata (KID, CEK)
    const content = await prisma.content.findUnique({ where: { id: contentId } });
    if (!content) {
      throw new Error('Content not found');
    }

    logger.info(
      { userId, contentId, isOffline, challengeSize: challengeBytes.length },
      'Issuing license'
    );

    // TODO: Build policy with license duration, HDCP requirement, etc.
    // For now, forward challenge directly to Google UAT
    // The UAT server recognizes the test KIDs and issues test licenses

    try {
      const response = await fetch(config.WIDEVINE_LICENSE_SERVER_URL, {
        method: 'POST',
        body: challengeBytes,
        headers: {
          'Content-Type': 'application/octet-stream',
        },
      });

      if (!response.ok) {
        logger.error(
          { status: response.status, body: await response.text() },
          'License server error'
        );
        throw new Error(`License server returned ${response.status}`);
      }

      const licenseBytes = Buffer.from(await response.arrayBuffer());

      // If offline, record the offline license in DB
      if (isOffline) {
        const expiresAt = new Date(
          Date.now() + config.OFFLINE_LICENSE_TTL_SECONDS * 1000
        );
        await prisma.offlineLicense.upsert({
          where: { userId_contentId: { userId, contentId } },
          update: { expiresAt, revoked: false, issuedAt: new Date() },
          create: {
            userId,
            contentId,
            keysetId: 'pending', // client will report keyset_id later
            expiresAt,
          },
        });
        logger.info({ userId, contentId, expiresAt }, 'Offline license issued');
      }

      return licenseBytes;
    } catch (error) {
      logger.error(error, 'Failed to get license from upstream');
      throw error;
    }
  }

  async renewOfflineLicense(userId: string, contentId: string): Promise<Buffer> {
    const offlineLicense = await prisma.offlineLicense.findUnique({
      where: { userId_contentId: { userId, contentId } },
    });

    if (!offlineLicense || offlineLicense.revoked) {
      throw new Error('License not found or revoked');
    }

    // For now, just return a placeholder
    // In production, this would re-issue via the license server
    logger.info({ userId, contentId }, 'Offline license renewed');

    return Buffer.from('renewed-license-blob');
  }
}

export const licenseService = new LicenseService();
