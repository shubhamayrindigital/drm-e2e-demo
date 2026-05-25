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

  // Delete old hardcoded content
  await prisma.content.deleteMany({
    where: { id: { in: ['drm-bbb', 'clear-bbb'] } },
  });

  const drmPssh = 'AAAASnBzc2gAAAAA7e+LqXnWSs6jyCfc1R0h7QAAACoSEKu6Jx6Lz1UrvS6GpDSppdkSEKu6Jx6Lz1UrvS6GpDSppdlI49yVmwY=';

  // Create DRM content (Shaka-packaged DASH stream from R2)
  const drmContent = await prisma.content.upsert({
    where: { id: 'drm-test' },
    update: {
      manifestPath: 'drm/manifest.mpd',
      pssh: drmPssh,
    },
    create: {
      id: 'drm-test',
      title: 'Test Content (DRM)',
      description: 'Widevine-encrypted DASH stream',
      drm: true,
      manifestPath: 'drm/manifest.mpd',
      kid: 'abba271e8bcf552bbd2e86a434a9a5d9',
      cek: '69eaa802a6763af979e0d6ed5e2c4ed7',
      pssh: drmPssh,
    },
  });
  console.log('✓ DRM content:', drmContent.id);

  // Create clear content (Shaka-packaged DASH stream from R2)
  const clearContent = await prisma.content.upsert({
    where: { id: 'clear-test' },
    update: {
      manifestPath: 'clear/manifest.mpd',
    },
    create: {
      id: 'clear-test',
      title: 'Test Content (Clear)',
      description: 'Non-DRM DASH stream',
      drm: false,
      manifestPath: 'clear/manifest.mpd',
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
