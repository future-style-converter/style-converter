#!/usr/bin/env node
// tools/titan/results/wave54-plan/queue-scout-layout.float-rows-score.mjs — scores the float-row composites written by
// queue-scout-layout.float-rows-sim.py against the frozen refs with the scorer's own SSIM (ssim.js fast mssim).
import { createRequire } from 'node:module';
import fs from 'node:fs'; import path from 'node:path'; import { fileURLToPath } from 'node:url';
const here = path.dirname(fileURLToPath(import.meta.url)); const ROOT = path.resolve(here, '../../../..') + '/';
const require = createRequire(ROOT + 'tools/titan/inject-wpt-block.mjs');
const { ssim } = require('ssim.js'); const { PNG } = require('pngjs');
const L = (p) => PNG.sync.read(fs.readFileSync(p));
const sc = (a, b) => +ssim({ data: new Uint8ClampedArray(a.data), width: a.width, height: a.height }, { data: new Uint8ClampedArray(b.data), width: b.width, height: b.height }, { ssim: 'fast' }).mssim.toFixed(4);
for (const t of ['abs-pos-border-offset-001', 'abs-pos-border-offset-002']) {
  const ref = L(`${ROOT}tools/wpt/refs/9b5435e55e0b54a6cd09c1c563861eb3c999cef1/white-black-ink-font-lh-imgpad-htmlpins-rootbg-uamargin/css-writing-modes/${t}.png`);
  for (const p of ['ios', 'android']) console.log(`${t} ${p} rows-only composite ssim ${sc(L(`${here}/queue-scout-layout.float-rows/${t}-${p}-rows.png`), ref)}`);
}
