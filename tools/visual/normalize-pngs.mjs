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
// This script's whole job is to DROP ancillary chunks. That is correct for
// timestamps and software strings, and catastrophic for a colour profile:
// stripping `iCCP`/`cICP` turns a Display-P3 capture into bytes that every
// downstream metric reads as sRGB, with no error anywhere. Assert first, so
// the profile is refused rather than quietly discarded.
import { assertSrgbOrUntagged } from './png-color-space.mjs';

const dirs = process.argv.slice(2);
if (dirs.length === 0) {
  console.error('usage: normalize-pngs.mjs <dir> [dir…]');
  process.exit(2);
}

let rewritten = 0, skipped = 0, failed = 0;
// Colour-space refusals are counted separately from decode failures: a
// decode failure is a broken file, this is a correct file we must not
// silently degrade. They get their own exit code so the two never blur.
const colorSpaceViolations = [];

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
      // Refuse to normalize a profile away. Recorded rather than thrown so
      // one bad file lists every other bad file in the same run instead of
      // making the operator re-run once per offender.
      try {
        assertSrgbOrUntagged(src, path);
      } catch (e) {
        colorSpaceViolations.push(e.message ?? String(e));
        continue;
      }
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

// Colour-space refusal outranks a decode failure: exit 3 (matching
// compare-screenshots.mjs) so CI can distinguish "a file is broken" from
// "a capture is in the wrong colour space and we refused to hide it".
if (colorSpaceViolations.length > 0) {
  console.error(`✗ ${colorSpaceViolations.length} capture(s) refused — not untagged-sRGB:`);
  for (const m of colorSpaceViolations) console.error(`  · ${m}`);
  process.exit(3);
}
if (failed > 0) process.exit(1);
