#!/usr/bin/env node
// Wave 52 (lane L3 · failure-ink) — the PNG replay owed by §0 rule (iii):
// for each target cell, the frozen ref, the wave51-fix captures on all three
// platforms, and the PREDICTED post-fix native picture (= the web capture,
// which already matches the ref on every target — web P 1.0000 / 1.0000 /
// P on T1), laid over the ref with the residual described in pixels.
// Red = the test's own failure ink ((255,0,0)-class); the census below counts
// it per image with its bounding box, and diffs each capture against the ref
// (per-pixel channel delta > 24 on any channel = mismatch).
import { readFileSync, writeFileSync } from 'node:fs';
import { join, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';
import { createRequire } from 'node:module';
const require = createRequire(import.meta.url);
const { PNG } = require(join(dirname(fileURLToPath(import.meta.url)), '../../../../node_modules/pngjs'));

const here = dirname(fileURLToPath(import.meta.url));
const repo = join(here, '../../../..');
const run = join(repo, 'tools/titan/runs/wave51-fix/sections');
const refs = join(repo, 'tools/wpt/refs/9b5435e55e0b54a6cd09c1c563861eb3c999cef1/white-black-ink-font-lh-imgpad-htmlpins');
const targets = [
  { id: 'T2', sec: 'css-display', test: 'display-contents-float-001', fix: 'F2 (Float clause deleted)' },
  { id: 'T1', sec: 'css-writing-modes', test: 'flexbox_align-items-stretch-writing-modes', fix: 'F1 (baked signature)' },
  { id: 'T3', sec: 'css-multicol', test: 'abspos-containing-block-outside-spanner', fix: 'F3 (spanner chain → ICB hoist)' },
];
const load = p => PNG.sync.read(readFileSync(p));
const isRed = (d, i) => d[i] > 200 && d[i + 1] < 60 && d[i + 2] < 60;
const isGreen = (d, i) => d[i] < 40 && d[i + 1] > 90 && d[i + 1] < 170 && d[i + 2] < 40;
// Ink census: red / green pixel counts with the red bounding box.
function ink(img) {
  const d = img.data; let red = 0, green = 0, bb = null;
  for (let y = 0; y < img.height; y++) for (let x = 0; x < img.width; x++) {
    const i = (y * img.width + x) * 4;
    if (isRed(d, i)) { red++; bb = bb ? [Math.min(bb[0], x), Math.min(bb[1], y), Math.max(bb[2], x), Math.max(bb[3], y)] : [x, y, x, y]; }
    if (isGreen(d, i)) green++;
  }
  return { w: img.width, h: img.height, red, green, redBBox: bb };
}
// Overlay diff: mismatching pixels + their bounding box (same-size images only).
function diff(a, b) {
  if (a.width !== b.width || a.height !== b.height) return { sizeMismatch: `${a.width}x${a.height} vs ${b.width}x${b.height}` };
  let n = 0, bb = null;
  for (let y = 0; y < a.height; y++) for (let x = 0; x < a.width; x++) {
    const i = (y * a.width + x) * 4;
    if (Math.abs(a.data[i] - b.data[i]) > 24 || Math.abs(a.data[i + 1] - b.data[i + 1]) > 24 || Math.abs(a.data[i + 2] - b.data[i + 2]) > 24) {
      n++; bb = bb ? [Math.min(bb[0], x), Math.min(bb[1], y), Math.max(bb[2], x), Math.max(bb[3], y)] : [x, y, x, y];
    }
  }
  return { mismatched: n, pct: +(100 * n / (a.width * a.height)).toFixed(3), bbox: bb };
}
const out = [];
for (const t of targets) {
  const name = `wpt__${t.sec}__${t.test}.png`;
  const ref = load(join(refs, t.sec, `${t.test}.png`));
  const caps = { web: load(join(run, t.sec, 'screenshots', name)), ios: load(join(run, t.sec, 'ios-screenshots', name)), android: load(join(run, t.sec, 'android-screenshots', name)) };
  const row = { ...t, ref: ink(ref), captures: {}, predictedResidual_webOverRef: diff(caps.web, ref) };
  for (const [plat, img] of Object.entries(caps)) row.captures[plat] = { ...ink(img), vsRef: diff(img, ref) };
  out.push(row);
  console.log(`${t.id} ${t.sec}/${t.test} — ${t.fix}`);
  console.log(`   ref: red ${row.ref.red} green ${row.ref.green} (${row.ref.w}x${row.ref.h})`);
  for (const [plat, c] of Object.entries(row.captures)) console.log(`   ${plat.padEnd(7)} red ${String(c.red).padStart(6)} bbox ${JSON.stringify(c.redBBox)} green ${String(c.green).padStart(6)} | vs ref: ${JSON.stringify(c.vsRef)}`);
  console.log(`   predicted post-fix native picture = web capture; residual over ref: ${JSON.stringify(row.predictedResidual_webOverRef)}`);
}
writeFileSync(join(here, 'png-replay.json'), JSON.stringify(out, null, 2) + '\n');
