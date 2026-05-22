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
}

export const catalogService = new CatalogService();
