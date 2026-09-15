// Wave-50 lane B11 — ink bounding boxes of the committed baselines that the
// `android-harness-placeholder-floor` ledger lines (6 entries in
// tools/visual/cross-platform-expectations.json, expiry 2026-09-30) are
// about. Re-runnable evidence for the "measured before/after" the ledger's
// own reason text and docs/STATUS.md both demand — the same arithmetic
// regressed twice (waves 1 and 4), so the numbers have to be derivable, not
// remembered.
//
//   node tools/titan/results/wave50-B11/baseline-ink-boxes.mjs
//
// Method: read tools/visual/baseline/{platform}__{NNN}_{Name}.png, take the
// top-left pixel as the capture background (the harness paints a constant
// #1a1a2e card ground), and report the bounding box of every pixel more than
// 6/255 away from it on any channel. That box is the component's painted
// border box; its HEIGHT is what the floor bug shortens.
import fs from 'node:fs';
import path from 'node:path';
import { PNG } from 'pngjs';

// The three ledgered components plus the bordered controls that must NOT
// move (their padding alone already exceeds the 30px floor, so the minimum
// never binds and the band term cannot show).
const NAMES = [
  '091_Button_Outline', '094_Input_Field', '105_Edge_DeepNesting',
  '016_Border_Solid', '017_Border_Dashed', '097_Glass_Effect', '090_Button_Primary',
];
const PLATFORMS = ['web', 'iOS', 'Android'];
const BASE = 'tools/visual/baseline';

// Bounding box of everything that is not the corner background colour.
function inkBox(file) {
  const png = PNG.sync.read(fs.readFileSync(file));
  const bg = [png.data[0], png.data[1], png.data[2], png.data[3]];
  let x0 = Infinity, y0 = Infinity, x1 = -1, y1 = -1;
  for (let y = 0; y < png.height; y++) {
    for (let x = 0; x < png.width; x++) {
      const i = (png.width * y + x) << 2;
      // 6/255 tolerance absorbs PNG-level rounding without hiding a 1px edge.
      const differs = Math.abs(png.data[i] - bg[0]) > 6 || Math.abs(png.data[i + 1] - bg[1]) > 6
        || Math.abs(png.data[i + 2] - bg[2]) > 6 || Math.abs(png.data[i + 3] - bg[3]) > 6;
      if (!differs) continue;
      if (x < x0) x0 = x; if (x > x1) x1 = x;
      if (y < y0) y0 = y; if (y > y1) y1 = y;
    }
  }
  return { canvas: `${png.width}x${png.height}`, ink: `${x1 - x0 + 1}x${y1 - y0 + 1}`, at: `${x0},${y0}` };
}

const out = {};
for (const name of NAMES) {
  out[name] = {};
  for (const p of PLATFORMS) {
    const f = path.join(BASE, `${p}__${name}.png`);
    out[name][p] = fs.existsSync(f) ? inkBox(f) : 'missing';
  }
}
console.log(JSON.stringify(out, null, 1));
