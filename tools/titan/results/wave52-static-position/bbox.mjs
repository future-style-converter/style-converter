#!/usr/bin/env node
// wave-52 lane L7 — colour-class bounding boxes of the frozen ref and the
// three wave51-fix captures for ONE test (the brief's measurement method:
// "colour-class bounding boxes; frame 390 wide, body origin (16,16)").
// Read-only. Usage: node bbox.mjs <section> <css/…/test.html>
import fs from 'node:fs';
import path from 'node:path';
import { createRequire } from 'node:module';
import { fileURLToPath } from 'node:url';

const HERE = path.dirname(fileURLToPath(import.meta.url));
const ROOT = path.resolve(HERE, '..', '..', '..', '..');
const req = createRequire(path.join(ROOT, 'package.json'));
const { PNG } = req('pngjs');
const [section, test] = process.argv.slice(2);
// The run of record's manifest gives the ref path and the recorded cells.
const m = JSON.parse(fs.readFileSync(path.join(ROOT, 'tools/titan/runs/wave51-fix/sections', section, 'manifest.json'), 'utf8'));
const res = m.wpt.results[test];
const stem = test.replace(/^css\//, '').replace(/\.html$/, '').replace(/\//g, '__');
const DIRS = { web: 'screenshots', ios: 'ios-screenshots', android: 'android-screenshots' };
// Same colour classes as nnm-ink.mjs / the L2 replay (+ teal, yellow marks).
export const CLASSES = {
  green: ([r, g, b]) => g > 100 && r < 60 && b < 60,
  red: ([r, g, b]) => r > 200 && g < 80 && b < 80,
  teal: ([r, g, b]) => r < 40 && g > 100 && g < 160 && b > 100 && b < 160,
  yellow: ([r, g, b]) => r > 200 && g > 200 && b < 80,
  black: ([r, g, b]) => r < 60 && g < 60 && b < 60,
};
// One bounding box + pixel count per colour class.
function bbox(p) {
  const o = {};
  for (const [k, f] of Object.entries(CLASSES)) {
    let x0 = 1e9; let y0 = 1e9; let x1 = -1; let y1 = -1; let n = 0;
    for (let y = 0; y < p.height; y++) for (let x = 0; x < p.width; x++) {
      const i = (y * p.width + x) * 4;
      if (f([p.data[i], p.data[i + 1], p.data[i + 2]])) { n++; x0 = Math.min(x0, x); y0 = Math.min(y0, y); x1 = Math.max(x1, x); y1 = Math.max(y1, y); }
    }
    if (n) o[k] = `(${x0},${y0})-(${x1},${y1}) n=${n}`;
  }
  return o;
}
const ref = PNG.sync.read(fs.readFileSync(path.join(ROOT, res.browserRef.path)));
console.log('ref', ref.width, ref.height, JSON.stringify(bbox(ref)));
for (const pf of ['web', 'ios', 'android']) {
  const f = path.join(ROOT, 'tools/titan/runs/wave51-fix/sections', section, DIRS[pf], `wpt__${stem}.png`);
  if (!fs.existsSync(f)) continue;
  const p = PNG.sync.read(fs.readFileSync(f));
  const d = res.browserRef.diffs[`${pf}-ref`];
  console.log(pf, p.width, p.height, d?.ssim, d?.wptPass ? 'P' : 'f', d?.colorFailed ? 'cF' : '', JSON.stringify(bbox(p)));
}
