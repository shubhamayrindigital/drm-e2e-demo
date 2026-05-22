import { PrismaClient } from '@prisma/client';
import bcrypt from 'bcrypt';

const prisma = new PrismaClient();

async function main() {
  console.log('Seeding database...');

  // Create test user
  const user = await prisma.user.upsert({
    where: { email: 'demo@example.com' },
    update: {},
    create: {
      email: 'demo@example.com',
      passwordHash: await bcrypt.hash('password123', 12),
    },
  });
  console.log('✓ Test user:', user.email);

  // Create DRM content (Big Buck Bunny encrypted)
  const drmContent = await prisma.content.upsert({
    where: { id: 'drm-bbb' },
    update: {},
    create: {
      id: 'drm-bbb',
      title: 'Big Buck Bunny (DRM)',
      description: 'Widevine-encrypted DASH stream',
      drm: true,
      manifestPath: 'vod/drm-bbb/manifest.mpd',
      kid: 'abba271e8bcf552bbd2e86a434a9a5d9',
      cek: '69eaa802a6763af979e0d6ed5e2c4ed7',
      pssh: 'CAESEAbba271e8bcf552bbd2e86a434a9a5d9GjAIDBIGV2lkZXZpbmUaKEFFU0ExMjhhZWU5YWY5Njk2ODkzNGE1YzQ0MzI4ZGQ2',
    },
  });
  console.log('✓ DRM content:', drmContent.id);

  // Create clear content (Big Buck Bunny non-DRM)
  const clearContent = await prisma.content.upsert({
    where: { id: 'clear-bbb' },
    update: {},
    create: {
      id: 'clear-bbb',
      title: 'Big Buck Bunny (Clear)',
      description: 'Non-DRM DASH + HLS stream',
      drm: false,
      manifestPath: 'vod/clear-bbb/manifest.mpd',
    },
  });
  console.log('✓ Clear content:', clearContent.id);

  // Grant entitlements
  await prisma.entitlement.upsert({
    where: { userId_contentId: { userId: user.id, contentId: drmContent.id } },
    update: {},
    create: { userId: user.id, contentId: drmContent.id },
  });
  console.log('✓ Entitled user to DRM content');

  await prisma.entitlement.upsert({
    where: { userId_contentId: { userId: user.id, contentId: clearContent.id } },
    update: {},
    create: { userId: user.id, contentId: clearContent.id },
  });
  console.log('✓ Entitled user to clear content');

  console.log('\nSeeding complete!');
  console.log('Test user: demo@example.com / password123');
}

main()
  .catch((e) => {
    console.error(e);
    process.exit(1);
  })
  .finally(async () => {
    await prisma.$disconnect();
  });
