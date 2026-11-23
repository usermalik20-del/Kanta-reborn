import { cp, mkdir, readdir, rm } from 'node:fs/promises';
import { extname, join } from 'node:path';

// The legacy site serves files from its project root. Copy only known public
// source trees/types; otherwise zero-config Vercel emits server code and tests.
const output = '.kanta-static';
const assetTypes = new Set(['.html', '.css', '.js', '.svg', '.png', '.txt', '.xml']);
const manifests = new Set(['downloads.json', 'updates.json']);
await rm(output, { recursive: true, force: true });
await mkdir(output);

async function copyAssets(dir) {
  for (const entry of await readdir(dir, { withFileTypes: true })) {
    const path = join(dir, entry.name);
    if (entry.isDirectory() && (dir !== '.' || ['blog', 'icons'].includes(entry.name))) {
      await mkdir(join(output, path));
      await copyAssets(path);
    } else if (entry.isFile() && (assetTypes.has(extname(path)) || (dir === '.' && manifests.has(path)))) {
      await cp(path, join(output, path));
    }
  }
}

await copyAssets('.');
