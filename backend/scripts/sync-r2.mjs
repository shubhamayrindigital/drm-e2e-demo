import { S3Client, ListObjectsV2Command, DeleteObjectsCommand, PutObjectCommand } from '@aws-sdk/client-s3';
import fs from 'node:fs';
import path from 'node:path';
import 'dotenv/config';

const BUCKET = process.env.CF_BUCKET_NAME;
const client = new S3Client({
  region: 'auto',
  endpoint: process.env.CF_R2_ENDPOINT,
  credentials: {
    accessKeyId: process.env.CF_R2_ACCESS_KEY_ID,
    secretAccessKey: process.env.CF_R2_SECRET_ACCESS_KEY,
  },
});

async function listAll() {
  const out = [];
  let token;
  do {
    const resp = await client.send(new ListObjectsV2Command({ Bucket: BUCKET, ContinuationToken: token }));
    (resp.Contents || []).forEach((o) => out.push(o.Key));
    token = resp.IsTruncated ? resp.NextContinuationToken : undefined;
  } while (token);
  return out;
}

async function deleteAll(keys) {
  if (!keys.length) return;
  for (let i = 0; i < keys.length; i += 1000) {
    const chunk = keys.slice(i, i + 1000);
    await client.send(new DeleteObjectsCommand({ Bucket: BUCKET, Delete: { Objects: chunk.map((Key) => ({ Key })) } }));
    console.log(`Deleted ${chunk.length} objects`);
  }
}

function walk(dir, out = []) {
  for (const name of fs.readdirSync(dir)) {
    const p = path.join(dir, name);
    if (fs.statSync(p).isDirectory()) walk(p, out);
    else out.push(p);
  }
  return out;
}

function mimeFor(filepath) {
  if (filepath.endsWith('.mpd')) return 'application/dash+xml';
  if (filepath.endsWith('.mp4')) return 'video/mp4';
  if (filepath.endsWith('.m4s')) return 'video/iso.segment';
  return 'application/octet-stream';
}

async function uploadDir(localDir, prefix) {
  const files = walk(localDir);
  for (const f of files) {
    const rel = path.relative(localDir, f).split(path.sep).join('/');
    const key = `${prefix}/${rel}`;
    const body = fs.readFileSync(f);
    await client.send(new PutObjectCommand({ Bucket: BUCKET, Key: key, Body: body, ContentType: mimeFor(f) }));
    console.log(`Uploaded ${key} (${body.length} bytes)`);
  }
}

const existing = await listAll();
const drmKeys = existing.filter((k) => k.startsWith('drm/'));
console.log(`Deleting ${drmKeys.length} drm/ objects`);
await deleteAll(drmKeys);

await uploadDir('/tmp/packager-out/drm', 'drm');

console.log('Done.');
const final = await listAll();
console.log(`\nFinal bucket contents (${final.length}):`);
final.forEach((k) => console.log('  ' + k));
