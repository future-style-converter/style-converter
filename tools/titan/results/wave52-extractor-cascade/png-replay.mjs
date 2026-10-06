#!/usr/bin/env node
// wave-52 lane L5 (extractor-cascade) — the PNG replay (PLAN.md §0 rule iii):
// the PREDICTED post-fix picture of each IR-changing target, simulated on the
// wave51-fix capture by the one paint change the new IR implies, then scored
// against the frozen ref with the gate's own scorer (`diffWebVsRef`) and the
// residual described in pixels.
//
// The simulations (each is the IR delta of the extract+convert differential,
// differential-static-stage-6-FE.json, rendered by hand):
//   import-conditional-00{1,2}  BackgroundColor red → green: every pure-red
//                               pixel of the square becomes rgb(0,128,0).
//   color-mix-percents-02       rows t6/t7 color-mix → the rgb() purple: the
//                               five painted rows' x-extent and 2em pitch are
//                               read off the capture and two more rows painted.
//   flex-gap-decorations-024    ColumnRule 9px dotted blue → 10px solid pink:
//                               the column gap between the two skyblue items
//                               is painted pink, under the green row rule.
//   revert-val-002              display revert → block: the 100×100 #outer box
//                               (the red pixels' bounding box) fills green.
// A replay predicts the WEB picture best (the natives already track web to
// ±0.002 on these cells); natives are replayed with the same rule.
//
// Usage (repo root): node png-replay.mjs <inject-wpt-block.mjs to score with> > png-replay.json
// Pass a HEAD copy of the scorer (the shared tree's may carry another lane's
// in-flight edit); the lane used a `git archive HEAD` copy.
import { readFileSync, writeFileSync, mkdtempSync } from 'node:fs';
import { join, resolve, dirname } from 'node:path';
import { tmpdir } from 'node:os';
import { fileURLToPath } from 'node:url';
import { createRequire } from 'node:module';

const HERE = dirname(fileURLToPath(import.meta.url));
const REPO = resolve(HERE, '..', '..', '..', '..');
const { PNG } = createRequire(join(REPO, 'package.json'))('pngjs');
const { diffWebVsRef } = await import(resolve(process.argv[2] ?? join(REPO, 'tools/titan/inject-wpt-block.mjs')));
const RUN = join(REPO, 'tools/titan/runs/wave51-fix/sections');
const TMP = mkdtempSync(join(tmpdir(), 'l5-replay-'));

const px = (img, x, y) => { const i = (y * img.width + x) * 4; return [img.data[i], img.data[i + 1], img.data[i + 2]]; };
const put = (img, x, y, [r, g, b]) => { const i = (y * img.width + x) * 4; img.data[i] = r; img.data[i + 1] = g; img.data[i + 2] = b; img.data[i + 3] = 255; };
const near = (a, b, tol = 24) => Math.abs(a[0] - b[0]) <= tol && Math.abs(a[1] - b[1]) <= tol && Math.abs(a[2] - b[2]) <= tol;
// Bounding box of the pixels matching `colour`.
function bbox(img, colour, tol) {
  let x0 = Infinity, y0 = Infinity, x1 = -1, y1 = -1;
  for (let y = 0; y < img.height; y++) for (let x = 0; x < img.width; x++) {
    if (near(px(img, x, y), colour, tol)) { x0 = Math.min(x0, x); y0 = Math.min(y0, y); x1 = Math.max(x1, x); y1 = Math.max(y1, y); }
  }
  return x1 < 0 ? null : { x0, y0, x1, y1 };
}
// Pixels differing from the ref by more than 32 on any channel, split into
// the caption band (y < 80, harness-wide font noise) and the test area.
function residual(pred, ref) {
  let caption = 0, test = 0;
  for (let y = 0; y < Math.min(pred.height, ref.height); y++) for (let x = 0; x < Math.min(pred.width, ref.width); x++) {
    if (!near(px(pred, x, y), px(ref, x, y), 32)) { if (y < 80) caption++; else test++; }
  }
  return { captionBandPx: caption, testAreaPx: test };
}

const RED = [255, 0, 0], GREEN = [0, 128, 0], PINK = [255, 192, 203], SKY = [135, 206, 235], PURPLE = [174, 91, 174];
const SIMS = {
  // Every red pixel of the square → green.
  recolourRedToGreen(img) { for (let y = 0; y < img.height; y++) for (let x = 0; x < img.width; x++) if (near(px(img, x, y), RED, 40)) put(img, x, y, GREEN); },
  // Two more purple rows at the measured pitch.
  twoMorePurpleRows(img) {
    const b = bbox(img, PURPLE, 12); const pitch = (b.y1 - b.y0 + 1) / 5;
    for (let y = b.y1 + 1; y <= Math.min(img.height - 1, Math.round(b.y1 + 2 * pitch)); y++) for (let x = b.x0; x <= b.x1; x++) put(img, x, y, PURPLE);
  },
  // The column gap between the skyblue items, pink under the green row rule.
  pinkColumnGap(img) {
    const b = bbox(img, SKY, 12); const yProbe = b.y0 + 2;
    let gx0 = -1, gx1 = -1;
    for (let x = b.x0; x <= b.x1; x++) if (!near(px(img, x, yProbe), SKY, 12)) { if (gx0 < 0) gx0 = x; gx1 = x; }
    for (let y = b.y0; y <= b.y1; y++) for (let x = gx0; x <= gx1; x++) if (!near(px(img, x, y), GREEN, 40)) put(img, x, y, PINK);
    return { gap: [gx0, gx1], grid: b };
  },
  // The #outer box fills green. A capture with no red (both natives box the
  // span block-level already) has nothing to repaint: predicted unchanged.
  fillOuterGreen(img) {
    const b = bbox(img, RED, 40);
    if (!b) return { unchanged: 'no red pixels — the span already paints the whole box' };
    for (let y = b.y0; y <= b.y1; y++) for (let x = b.x0; x <= b.x1; x++) put(img, x, y, GREEN);
    return b;
  },
};
const TARGETS = [
  ['css-cascade', 'import-conditional-001', 'recolourRedToGreen'],
  ['css-cascade', 'import-conditional-002', 'recolourRedToGreen'],
  ['css-color', 'color-mix-percents-02', 'twoMorePurpleRows'],
  ['css-gaps', 'flex/flex-gap-decorations-024', 'pinkColumnGap'],
  ['css-cascade', 'revert-val-002', 'fillOuterGreen'],
];
const DIRS = { web: 'screenshots', ios: 'ios-screenshots', android: 'android-screenshots' };
const out = [];
for (const [sec, test, sim] of TARGETS) {
  const key = `css/${sec}/${test}.html`;
  const r = JSON.parse(readFileSync(join(RUN, sec, 'manifest.json'), 'utf8')).wpt.results[key];
  const refPath = join(REPO, r.browserRef.path);
  const ref = PNG.sync.read(readFileSync(refPath));
  for (const [platform, dir] of Object.entries(DIRS)) {
    const capPath = join(RUN, sec, dir, `wpt__${sec}__${test.replaceAll('/', '__')}.png`);
    const cap = PNG.sync.read(readFileSync(capPath));
    const pred = PNG.sync.read(readFileSync(capPath));
    const geometry = SIMS[sim](pred) ?? null;
    const predPath = join(TMP, `${platform}-${test.replaceAll('/', '__')}.png`);
    writeFileSync(predPath, PNG.sync.write(pred));
    const before = await diffWebVsRef(capPath, refPath);
    const after = await diffWebVsRef(predPath, refPath);
    out.push({ cell: `${sec}/${test} ${platform}`, sim, geometry, ssimBefore: before.ssim, ssimPredicted: after.ssim,
      residualBefore: residual(cap, ref), residualPredicted: residual(pred, ref) });
  }
}
console.log(JSON.stringify({ scorer: process.argv[2] ?? 'tools/titan/inject-wpt-block.mjs', replays: out }, null, 1));
