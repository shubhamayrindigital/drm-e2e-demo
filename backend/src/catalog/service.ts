import { prisma } from '../db/client.js';

export class CatalogService {
  async listContent(userId: string) {
    // Get all content, mark which ones user is entitled to
    const contents = await prisma.content.findMany({
      include: {
        entitlements: {
          where: { userId },
          select: { id: true },
        },
      },
    });

    return contents.map((content) => ({
      id: content.id,
      title: content.title,
      description: content.description,
      drm: content.drm,
      entitled: content.entitlements.length > 0,
    }));
  }

  async getContent(id: string) {
    return prisma.content.findUnique({ where: { id } });
  }

  async grantEntitlement(userId: string, contentId: string) {
    return prisma.entitlement.upsert({
      where: { userId_contentId: { userId, contentId } },
      update: {},
      create: { userId, contentId },
    });
  }

  async getPlayManifest(userId: string, contentId: string) {
    const content = await prisma.content.findUnique({
      where: { id: contentId },
      include: {
        entitlements: {
          where: { userId },
          select: { id: true },
        },
      },
    });

    if (!content || content.entitlements.length === 0) {
      throw new Error('Not entitled to this content');
    }

    const result: any = {};

    if (content.drm) {
      result.drmConfig = {
        kid: content.kid,
        cek: content.cek,
        pssh: content.pssh,
      };
    }

    return result;
  }
}

export const catalogService = new CatalogService();
