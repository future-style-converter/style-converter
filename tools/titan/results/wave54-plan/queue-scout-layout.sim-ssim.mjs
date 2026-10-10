#!/usr/bin/env node
// tools/titan/results/wave54-plan/queue-scout-layout.sim-ssim.mjs
// READ-ONLY SSIM simulation for the queue-scout-layout predictions, with the
// scorer's own metric (ssim.js fast mssim, as inject-wpt-block.mjs runs it).
// Each case re-scores a <run> native capture against its frozen ref AFTER a
// pixel edit that stands in for the fix:
//   sub:<sec>/<test>   — substitute the SAME platform's capture of a sibling test
//                        whose picture is the post-fix picture (same paragraph,
//                        same green square, already correct on that platform);
//   edits              — fill rectangles (inclusive canvas px) with a colour, or
//                        copy a rectangle from the ref ('ref') — the latter is an
//                        UPPER BOUND (ref text pixels replace native text).
// Usage: node queue-scout-layout.sim-ssim.mjs [run=wave53-final]
import { createRequire } from 'node:module';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
const here = path.dirname(fileURLToPath(import.meta.url));
const ROOT = path.resolve(here, '../../../..') + '/';
const require = createRequire(ROOT + 'tools/titan/inject-wpt-block.mjs');
const { ssim } = require('ssim.js');
const { PNG } = require('pngjs');
const RUN = process.argv[2] ?? 'wave53-final';
const REF = ROOT + 'tools/wpt/refs/9b5435e55e0b54a6cd09c1c563861eb3c999cef1/white-black-ink-font-lh-imgpad-htmlpins-rootbg-uamargin/';
const cap = (sec, test, plat) => `${ROOT}tools/titan/runs/${RUN}/sections/${sec}/${plat === 'ios' ? 'ios-screenshots' : plat === 'android' ? 'android-screenshots' : 'screenshots'}/wpt__${sec}__${test.replace(/\//g, '__')}.png`;
const refOf = (sec, test) => `${REF}${sec}/${test.replace(/\//g, '__')}.png`;
const load = (p) => PNG.sync.read(fs.readFileSync(p));
const score = (a, b) => +ssim({ data: new Uint8ClampedArray(a.data), width: a.width, height: a.height }, { data: new Uint8ClampedArray(b.data), width: b.width, height: b.height }, { ssim: 'fast' }).mssim.toFixed(4);
const fill = (img, [x0, y0, x1, y1], rgb) => { for (let y = y0; y <= y1; y++) for (let x = x0; x <= x1; x++) { const i = (y * img.width + x) * 4; img.data[i] = rgb[0]; img.data[i + 1] = rgb[1]; img.data[i + 2] = rgb[2]; img.data[i + 3] = 255; } };
const copy = (dst, src, [x0, y0, x1, y1]) => { for (let y = y0; y <= y1; y++) for (let x = x0; x <= x1; x++) { const i = (y * dst.width + x) * 4; for (let k = 0; k < 4; k++) dst.data[i + k] = src.data[i + k]; } };
const W = [255, 255, 255], G = [0, 128, 0], R = [255, 0, 0];
// [section, test, platforms, {sub} | {edits:[[rect, colour|'ref']...]}, label]
const cases = [
  ['css-position', 'position-relative-003', ['android'], { sub: 'css-position/position-relative-002' }, 'M1 static position (android sibling = position-relative-002 android, already right)'],
  ['css-position', 'position-relative-003', ['ios'], { sub: 'css-position/position-relative-006' }, 'M1 (ios sibling = position-relative-006 ios, same paragraph + square)'],
  ['css-position', 'position-relative-004', ['android'], { sub: 'css-position/position-relative-002' }, 'M2 contain:paint'],
  ['css-position', 'position-relative-004', ['ios'], { sub: 'css-position/position-relative-006' }, 'M2 contain:paint'],
  ['css-position', 'change-insets-inside-strict-containment-nested', ['android'], { sub: 'css-position/position-relative-002' }, 'M2 contain:strict'],
  ['css-position', 'change-insets-inside-strict-containment-nested', ['ios'], { sub: 'css-position/position-relative-006' }, 'M2 contain:strict'],
  ['CSS2', 'abspos/static-fixed-inside-abspos', ['android'], { sub: 'css-position/position-relative-002' }, 'M1 fixed under abspos'],
  ['CSS2', 'abspos/static-fixed-inside-abspos', ['ios'], { sub: 'css-position/position-relative-006' }, 'M1 fixed under abspos'],
  ['css-contain', 'contain-content-003', ['android'], { sub: 'css-position/position-relative-002' }, 'M2 contain:content abspos'],
  ['css-contain', 'contain-content-011', ['android'], { edits: [[[274, 16, 373, 65], 'ref'], [[274, 534, 373, 583], W], [[16, 88, 115, 187], G]] }, 'M2 contain:content abspos (ref text under the hoisted box: upper bound)'],
  ['filter-effects', 'backdrop-filter-containing-block', ['ios', 'android'], { edits: [[[0, 0, 389, 299], 'ref']] }, 'M2 backdrop-filter (whole box band from the ref: UPPER BOUND)'],
  ['filter-effects', 'backdrop-filter-containing-block', ['ios', 'android'], { edits: [[[0, 0, 389, 87], 'keep'], [[16, 16, 373, 299], W], [[16, 88, 215, 287], G], [[226, 88, 373, 287], R]] }, 'M2 backdrop-filter (boxes repainted, paragraph LOST under the stray green: LOWER BOUND)'],
  ['css-flexbox', 'abspos/abspos-autopos-htb-ltr', ['android'], { edits: [[[16, 88, 115, 187], G]] }, 'cbborder (android % basis = padding box)'],
  ['css-gaps', 'flex/flex-gap-decorations-033', ['ios', 'android'], { refInk: true }, 'zero-gap rules (every strict-red / strict-blue ref pixel copied onto the capture)'],
];
for (const [sec, test, plats, how, label] of cases) {
  const ref = load(refOf(sec, test));
  for (const plat of plats) {
    const now = load(cap(sec, test, plat));
    const before = score(now, ref);
    let after;
    if (how.refInk) {
      const img = load(cap(sec, test, plat));
      for (let i = 0; i < ref.data.length; i += 4) { const [r, g, b] = [ref.data[i], ref.data[i + 1], ref.data[i + 2]]; if ((r > 200 && g < 80 && b < 80) || (b > 200 && r < 80 && g < 80)) { img.data[i] = r; img.data[i + 1] = g; img.data[i + 2] = b; } }
      after = score(img, ref);
    } else if (how.sub) {
      const [ssec, stest] = how.sub.split(/\/(.+)/);
      after = score(load(cap(ssec, stest, plat)), ref);
    } else {
      const img = load(cap(sec, test, plat));
      for (const [rect, col] of how.edits) { if (col === 'keep') continue; if (col === 'ref') copy(img, ref, rect); else fill(img, rect, col); }
      after = score(img, ref);
    }
    console.log(`${sec}/${test} ${plat.padEnd(7)} now ${before} → simulated ${after}   [${label}]`);
  }
}
