#!/usr/bin/env node
//
// normalize-pngs.mjs — rewrite PNGs to a deterministic byte stream.
//
// Why: iOS's `UIImage.pngData()` embeds non-deterministic metadata
// (timestamps, software strings, color-profile variants). Two runs with
// pixel-identical output produce different MD5 hashes, which makes
// committed baselines noisy and byte-level regression checks impossible.
//
// What we do: decode each PNG with pngjs, re-encode it with only the
// RGBA pixel data (no ancillary chunks), at a fixed compression level.
// Result: pixel-identical input → byte-identical output.
//
// Usage:
//     node normalize-pngs.mjs path/to/dir [path/to/dir2 ...]
//
// Invoked from test-all.sh immediately after the iOS pull.
//

import { readdirSync, readFileSync, writeFileSync, statSync } from 'node:fs';
import { join } from 'node:path';
import { PNG } from 'pngjs';

const dirs = process.argv.slice(2);
if (dirs.length === 0) {
  console.error('usage: normalize-pngs.mjs <dir> [dir…]');
  process.exit(2);
}

let rewritten = 0, skipped = 0, failed = 0;

for (const dir of dirs) {
  if (!statSync(dir, { throwIfNoEntry: false })?.isDirectory()) {
    console.warn(`  ⚠ ${dir}: not a directory, skipping`);
    continue;
  }
  for (const f of readdirSync(dir)) {
    if (!f.endsWith('.png')) continue;
    const path = join(dir, f);
    try {
      const src = readFileSync(path);
      const decoded = PNG.sync.read(src);
      // Re-encode with fixed compression level. pngjs doesn't copy source
      // ancillary chunks, so the output has ONLY IHDR / IDAT / IEND.
      const normalized = PNG.sync.write(decoded, {
        deflateLevel: 9,
        deflateStrategy: 3, // Z_RLE — deterministic, good for sparse UI
        filterType: 4,      // adaptive — pngjs picks the same filter per scanline
      });
      // Byte-identical input is a no-op; only write when something changed.
      if (normalized.length === src.length && normalized.equals(src)) {
        skipped += 1;
      } else {
        writeFileSync(path, normalized);
        rewritten += 1;
      }
    } catch (e) {
      failed += 1;
      console.warn(`  ⚠ ${path}: ${e.message ?? e}`);
    }
  }
}

console.log(`✓ normalized PNGs: ${rewritten} rewritten, ${skipped} unchanged, ${failed} failed`);
if (failed > 0) process.exit(1);
