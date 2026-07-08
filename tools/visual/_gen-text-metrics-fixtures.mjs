#!/usr/bin/env node
// One-shot generator for tools/visual/fixtures/text-metrics/*.png
// Used to seed the per-metric unit-test fixtures. Outputs are committed
// to the repo (Section 8 q7 — don't generate at test time so sharp
// font rendering variance doesn't break CI). Re-run only when the
// metric semantics change.

import { PNG } from 'pngjs';
import { writeFileSync } from 'node:fs';
import { resolve } from 'node:path';

const OUT = resolve(process.argv[2] ?? './fixtures/text-metrics');

function makePng(w, h, fillRgb = [255, 255, 255, 255]) {
  const png = new PNG({ width: w, height: h });
  for (let i = 0; i < png.data.length; i += 4) {
    png.data[i]     = fillRgb[0];
    png.data[i + 1] = fillRgb[1];
    png.data[i + 2] = fillRgb[2];
    png.data[i + 3] = fillRgb[3];
  }
  return png;
}

function setPixel(png, x, y, rgba) {
  if (x < 0 || y < 0 || x >= png.width || y >= png.height) return;
  const i = (y * png.width + x) * 4;
  png.data[i]     = rgba[0];
  png.data[i + 1] = rgba[1];
  png.data[i + 2] = rgba[2];
  png.data[i + 3] = rgba[3];
}

// ── B8 — synthetic baseline fixtures ─────────────────────────────────────
// 240×80 (1×) → at 4× = 960×320. We draw a bottom-aligned glyph block
// of 8 px @4× tall × 16 px @4× wide so the column-scan helper picks up
// real "glyph mass" (≥ B8_MIN_DARK_RUN). Two variants: bottom row 200
// vs row 204, so the helper should report `200/4=50.0` vs `204/4=51.0`,
// a 1.0 px @1× delta.
function makeB8(bottomRow) {
  const W = 240 * 4, H = 80 * 4;
  const png = makePng(W, H);
  // Glyph block: 8 rows tall, 16 cols wide, centred-ish horizontally.
  for (let y = bottomRow - 7; y <= bottomRow; y++) {
    for (let x = 100; x < 116; x++) {
      setPixel(png, x, y, [0, 0, 0, 255]);
    }
  }
  return png;
}

// ── B9 — synthetic AA fixtures ───────────────────────────────────────────
// 128×128 (1×) → at 4× = 512×512. We draw a 45° line by setting each
// (x, y) where y == x. For greyscale: each pixel is pure black, with one
// half-grey pixel each side (luminance AA). For subpixel: edge pixels
// get coloured with R≠B (chroma AA — ClearType-style).
function makeB9_45deg(strategy) {
  const W = 128 * 4, H = 128 * 4;
  const png = makePng(W, H);
  for (let i = 0; i < W && i < H; i++) {
    setPixel(png, i, i, [0, 0, 0, 255]);
    if (strategy === 'greyscale') {
      // luminance AA → grey
      setPixel(png, i + 1, i, [128, 128, 128, 255]);
      setPixel(png, i, i + 1, [128, 128, 128, 255]);
    } else if (strategy === 'subpixel') {
      // chroma AA → red on one side, blue on the other (deliberately
      // exaggerated so R-B has obvious high-frequency energy)
      setPixel(png, i + 1, i, [255, 0, 0, 255]);
      setPixel(png, i, i + 1, [0, 0, 255, 255]);
    }
  }
  return png;
}

// ── B10 — synthetic glyph-spacing fixtures ───────────────────────────────
// 320×60 (1×) → at 4× = 1280×240. Mono variant: 5 evenly-spaced 8-px-wide
// black blocks. Kerned variant: same blocks but with the gap before/after
// the third block widened (simulating "AV" pair anti-kerning failure).
// 24-px font scaled 4× → 96-px-tall mass; we use 80 rows centered.
function makeB10(spacingMode) {
  const W = 320 * 4, H = 60 * 4;
  const png = makePng(W, H);
  const blockW = 8 * 4;       // 32 px @4× = 8 px @1×
  const baseGap = 16 * 4;     // 64 px @4× = 16 px @1× nominal
  const blocks = 5;
  // X positions for each block's left edge.
  const xs = [];
  let x = 80;
  for (let i = 0; i < blocks; i++) {
    xs.push(x);
    let gap = baseGap;
    if (spacingMode === 'kerned' && (i === 1 || i === 2)) {
      // Widen two of the gaps so the resulting stddev is large
      gap = baseGap * 2;
    }
    x += blockW + gap;
  }
  for (const bx of xs) {
    for (let y = 80; y < 160; y++) {
      for (let dx = 0; dx < blockW; dx++) {
        setPixel(png, bx + dx, y, [0, 0, 0, 255]);
      }
    }
  }
  return png;
}

writeFileSync(`${OUT}/b8_synthetic_baseline_200.png`, PNG.sync.write(makeB8(200)));
writeFileSync(`${OUT}/b8_synthetic_baseline_204.png`, PNG.sync.write(makeB8(204)));
writeFileSync(`${OUT}/b9_greyscale_45deg.png`,        PNG.sync.write(makeB9_45deg('greyscale')));
writeFileSync(`${OUT}/b9_subpixel_45deg.png`,         PNG.sync.write(makeB9_45deg('subpixel')));
writeFileSync(`${OUT}/b9_none_45deg.png`,             PNG.sync.write(makeB9_45deg('none')));
writeFileSync(`${OUT}/b10_mono_synthetic.png`,        PNG.sync.write(makeB10('mono')));
writeFileSync(`${OUT}/b10_kerned_synthetic.png`,      PNG.sync.write(makeB10('kerned')));

console.log(`✓ wrote 7 fixture PNGs to ${OUT}`);
