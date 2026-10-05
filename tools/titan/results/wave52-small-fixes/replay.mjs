#!/usr/bin/env node
// wave52-small-fixes/replay.mjs — the PNG replay §0 rule (iii) owes: for each
// L4 target, the PREDICTED post-fix picture is the wave51-fix WEB capture
// (web already matches the frozen ref: P 1.0000 / P 0.9990), scored against
// the ref with the SAME diffWebVsRef the titan gate uses (honest-frame SSIM +
// pre-flip pixelmatch), beside today's native captures — so "what the natives
// should score after the fix" and "what they score now" are one metric.
// Also prints per-colour ink bboxes so the residual is described in pixels.
// READ-ONLY; writes replay.json beside itself.
//   node tools/titan/results/wave52-small-fixes/replay.mjs
import fs from 'node:fs';
import path from 'node:path';
import { createRequire } from 'node:module';
const HERE = path.dirname(new URL(import.meta.url).pathname);
const ROOT = path.resolve(HERE, '../../../..');
const { diffWebVsRef } = await import(path.join(ROOT, 'tools/titan/inject-wpt-block.mjs'));
const { PNG } = createRequire(path.join(ROOT, 'tools/visual/compare-screenshots-metrics.mjs'))('pngjs');
const RUN = path.join(ROOT, 'tools/titan/runs/wave51-fix/sections');
const REFS = path.join(ROOT, 'tools/wpt/refs/9b5435e55e0b54a6cd09c1c563861eb3c999cef1/white-black-ink-font-lh-imgpad-htmlpins');
// <section>/<test> cells: the three flip targets + the four b″ passing-wrong controls.
const CELLS = [
  ['css-transforms', 'composited-under-rotateY-180deg-preserve-3d', 'T5 (+2 ios/android predicted)'],
  ['css-transforms', 'css-transform-inherit-scale', 'T7 (+1 android predicted; ios already P)'],
  ['css-sizing', 'abspos-auto-sizing-fit-content-percentage-001', 'b″ control (must not move)'],
  ['css-sizing', 'abspos-auto-sizing-fit-content-percentage-002', 'b″ control (must not move)'],
  ['css-sizing', 'abspos-auto-sizing-fit-content-percentage-003', 'b″ control (must not move)'],
  ['css-sizing', 'abspos-auto-sizing-fit-content-percentage-004', 'b″ control (must not move)'],
];
const cap = (sec, test, p) => path.join(RUN, sec, p === 'web' ? 'screenshots' : `${p}-screenshots`, `wpt__${sec}__${test}.png`);
// Per-colour ink census (nnm-ink.mjs's classifier, so bboxes read the same).
function classify(r, g, b) {
  if (r > 247 && g > 247 && b > 247) return null;
  if (g > 100 && r < 60 && b < 60) return 'green';
  if (r > 200 && g < 80 && b < 80) return 'red';
  if (r > 200 && g > 200 && b < 80) return 'yellow';
  if (Math.abs(r - g) < 12 && Math.abs(g - b) < 12) return r < 128 ? 'dark' : 'grey';
  return 'other';
}
function ink(file) {
  if (!fs.existsSync(file)) return { missing: true };
  const { width, height, data } = PNG.sync.read(fs.readFileSync(file));
  const out = { size: `${width}x${height}` };
  for (let y = 0; y < height; y++) for (let x = 0; x < width; x++) {
    const i = (y * width + x) * 4; const c = classify(data[i], data[i + 1], data[i + 2]); if (!c) continue;
    const o = out[c] ||= { px: 0, x0: 1e9, y0: 1e9, x1: -1, y1: -1 };
    o.px++; o.x0 = Math.min(o.x0, x); o.y0 = Math.min(o.y0, y); o.x1 = Math.max(o.x1, x); o.y1 = Math.max(o.y1, y);
  }
  return out;
}
const fmtInk = o => Object.entries(o).map(([k, v]) => typeof v === 'object' ? `${k} ${v.px}px [${v.x0},${v.y0}→${v.x1},${v.y1}]` : `${k}=${v}`).join('  ');
const out = [];
for (const [sec, test, label] of CELLS) {
  const ref = path.join(REFS, sec, `${test}.png`);
  const row = { cell: `${sec}/${test}`, label, ref: fmtInk(ink(ref)), platforms: {} };
  for (const p of ['web', 'ios', 'android']) {
    const file = cap(sec, test, p);
    const d = await diffWebVsRef(file, ref);
    row.platforms[p] = { ssim: d.ssim ?? d.ssimScore ?? null, pixelMismatchedPct: +(d.pixelMismatchedPct ?? d.pixelPct ?? NaN).toFixed(3), ink: fmtInk(ink(file)) };
  }
  // Native-vs-native byte identity (the b″ controls today are one picture).
  const same = (a, b) => fs.existsSync(a) && fs.existsSync(b) && Buffer.compare(fs.readFileSync(a), fs.readFileSync(b)) === 0;
  row.androidEqualsIos = same(cap(sec, test, 'android'), cap(sec, test, 'ios'));
  out.push(row);
  console.log(`\n## ${row.cell} — ${label}`);
  console.log(`REF      ${row.ref}`);
  for (const p of ['web', 'ios', 'android']) { const r = row.platforms[p]; console.log(`${p.padEnd(8)} ssim=${r.ssim} mismatch=${r.pixelMismatchedPct}%  ${r.ink}`); }
}
fs.writeFileSync(path.join(HERE, 'replay.json'), JSON.stringify(out, null, 1));
console.log('\nPredicted post-fix native score = the web row (web capture ≡ predicted native picture); residual = the web row\'s mismatch, described by the ink bboxes above.');
