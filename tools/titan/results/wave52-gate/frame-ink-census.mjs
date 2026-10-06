#!/usr/bin/env node
// tools/titan/results/wave52-gate/frame-ink-census.mjs
//
// The wave-52 L2 "Fix A" tripwire, as a script (the planner's frame-census.mjs
// lived in a session scratchpad and was purged; this is its recorded method,
// tools/titan/results/wave52-plan/frame-ink-census.json → "method", verbatim):
//
//   For every SCORED browserRef cell with a same-width capture, count capture
//   pixels in the 16-px image frame (columns x<16 = left, x>=W-16 = right,
//   rows y<16 = top) that differ from the frozen ref by >8/255 on any channel.
//   The ref frame is image-space padding, so capture ink there is ink the ref
//   cannot have. Classes: overrun-right (right only), overrun-left (left
//   only), overrun-top (top only), frame-colour(all-sides) (all three), mixed.
//
// Wave 51 measured overrun-right 208 + overrun-left 84 = 292 scored cells;
// L2 clips the composed canvas at the capture frame, so the closing gate must
// print 0 / 0. Run on wave51-fix with its own ref tree this must reproduce
// 208 / 84 — that is the check that the re-implementation is the same census.
//
// Usage: node frame-ink-census.mjs <run-id> <refs-rev-dir-name> [--json out.json]
import { createRequire } from 'node:module';
import { existsSync, writeFileSync } from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { loadRun, resolveRunDir } from '../../score-gate.mjs';

const REPO = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..', '..', '..', '..');
const sharp = createRequire(path.join(REPO, 'package.json'))('sharp');
sharp.concurrency(1);   // one thread: this may run beside a capture, and must not look like load
const [runId, rev, flag, jsonOut] = process.argv.slice(2);
if (!rev) { console.error('usage: frame-ink-census.mjs <run-id> <refs-rev-dir-name> [--json out.json]'); process.exit(2); }
const REFS = path.join(REPO, 'tools/wpt/refs/9b5435e55e0b54a6cd09c1c563861eb3c999cef1', rev);
const runDir = resolveRunDir(runId);
if (!runDir || !existsSync(REFS)) { console.error(`missing run dir (${runId}) or ref tree (${REFS})`); process.exit(2); }
const DIR = { web: 'screenshots', ios: 'ios-screenshots', android: 'android-screenshots' };
const PAD = 16, TOL = 8;

const raw = (file) => sharp(file).ensureAlpha().raw().toBuffer({ resolveWithObject: true });
const counts = {}; const rows = []; let compared = 0, skipped = 0;
for (const [sec, section] of Object.entries(loadRun(runDir).sections)) {
  // loadRun keeps only scored cells (numeric ssim, no scoreExcluded) — the census's denominator.
  for (const key of section.cells.keys()) {
    const [test, platform] = key.split('|');
    const stem = test.split('/').slice(2).join('__').replace(/\.html?$/, '');
    const capFile = path.join(runDir, 'sections', sec, DIR[platform], `wpt__${sec}__${stem}.png`);
    const refFile = path.join(REFS, sec, `${stem}.png`);
    if (!existsSync(capFile) || !existsSync(refFile)) { skipped++; continue; }
    const [cap, ref] = await Promise.all([raw(capFile), raw(refFile)]);
    // Same-width pairs only; a height mismatch is compared over the shared rows.
    if (cap.info.width !== ref.info.width) { skipped++; continue; }
    const W = cap.info.width, H = Math.min(cap.info.height, ref.info.height);
    let left = 0, right = 0, top = 0;
    const differs = (x, y) => { const i = (y * W + x) * 4; for (let c = 0; c < 4; c++) if (Math.abs(cap.data[i + c] - ref.data[i + c]) > TOL) return true; return false; };
    for (let y = 0; y < H; y++) {
      for (let x = 0; x < PAD; x++) if (differs(x, y)) left++;
      for (let x = W - PAD; x < W; x++) if (differs(x, y)) right++;
    }
    for (let y = 0; y < Math.min(PAD, H); y++) for (let x = 0; x < W; x++) if (differs(x, y)) top++;
    compared++;
    const cls = !left && !right && !top ? 'clean-frame'
      : left && right && top ? 'frame-colour(all-sides)'
      : right && !left && !top ? 'overrun-right'
      : left && !right && !top ? 'overrun-left'
      : top && !left && !right ? 'overrun-top' : 'mixed';
    counts[cls] = (counts[cls] || 0) + 1;
    if (cls !== 'clean-frame') rows.push({ sec, test, platform, class: cls, left, right, top, ...section.cells.get(key) });
  }
}
console.log(`frame-ink census — run ${runId} vs refs ${rev}`);
console.log(`  scored cells compared ${compared} (skipped: no file / width mismatch ${skipped})`);
for (const c of ['clean-frame', 'overrun-right', 'overrun-left', 'overrun-top', 'mixed', 'frame-colour(all-sides)']) console.log(`  ${c.padEnd(26)} ${counts[c] || 0}`);
for (const c of ['overrun-right', 'overrun-left']) for (const r of rows.filter((x) => x.class === c).slice(0, 40)) console.log(`    ${c}  ${r.platform.padEnd(8)} L${r.left} R${r.right} T${r.top}  ${r.pass ? 'P' : 'f'} ${r.ssim}  ${r.test}`);
if (flag === '--json' && jsonOut) writeFileSync(jsonOut, JSON.stringify({ run: runId, refs: rev, pad: PAD, tolerance: TOL, compared, skipped, counts, cells: rows }, null, 1));
// The tripwire itself: any side overrun left is a non-zero exit.
process.exit((counts['overrun-right'] || 0) + (counts['overrun-left'] || 0) ? 1 : 0);
