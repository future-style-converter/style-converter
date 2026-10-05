#!/usr/bin/env node
// tools/titan/results/wave52-instrument-and-calibration/calib-hashcheck.mjs
//
// wave-52 L12 — the Calibration-gate recipe's capture-hash check plus per-cell
// attribution for `score-gate.mjs wave52-open wave52-calib`:
//   1. sha1 every composed capture PNG of both runs (identical bytes ⇒ every
//      move is INSTRUMENT-ONLY — the BACKLOG rule "identical capture bytes →
//      instrument-only flip, different bytes → a render change");
//   2. replay score-gate's own loadRun/diffRuns (imported, not re-implemented);
//   3. attribute every cell whose ssim or verdict or scored-state moved to one
//      of: 'absence-only stamp' (cur scoreExcluded === 'absence-only'),
//      're-frozen ref' (test ∈ refreeze-detail.json), or UNATTRIBUTED — which
//      must be empty (it would mean inject is non-deterministic or a ref moved
//      that the re-freeze did not touch).
// Usage: node …/calib-hashcheck.mjs [PREV=wave52-open] [CUR=wave52-calib]
// Writes calib-attribution.json beside this script.
import fs from 'node:fs';
import path from 'node:path';
import crypto from 'node:crypto';
import { fileURLToPath } from 'node:url';

// Paths, the scorer (imported), and the re-frozen test set.
const HERE = path.dirname(fileURLToPath(import.meta.url));
const TITAN = path.resolve(HERE, '..', '..');
const sg = await import(path.join(TITAN, 'score-gate.mjs'));
const prevId = process.env.PREV || 'wave52-open', curId = process.env.CUR || 'wave52-calib';
const prevDir = sg.resolveRunDir(prevId), curDir = sg.resolveRunDir(curId);
const refrozen = new Set(JSON.parse(fs.readFileSync(path.join(HERE, 'refreeze-detail.json'), 'utf8')).rows.map((r) => r.test));
const DIRS = ['screenshots', 'ios-screenshots', 'android-screenshots'];
const sha1 = (p) => crypto.createHash('sha1').update(fs.readFileSync(p)).digest('hex');

// 1. Capture-hash check over every PNG of every platform column.
let same = 0; const differ = [], onlyOne = [];
for (const sec of fs.readdirSync(path.join(prevDir, 'sections')).sort()) {
  for (const d of DIRS) {
    const a = path.join(prevDir, 'sections', sec, d), b = path.join(curDir, 'sections', sec, d);
    const names = new Set([...(fs.existsSync(a) ? fs.readdirSync(a) : []), ...(fs.existsSync(b) ? fs.readdirSync(b) : [])].filter((f) => f.endsWith('.png')));
    for (const f of names) {
      const pa = path.join(a, f), pb = path.join(b, f);
      if (!fs.existsSync(pa) || !fs.existsSync(pb)) { onlyOne.push(`${sec}/${d}/${f}`); continue; }
      if (sha1(pa) === sha1(pb)) same++; else differ.push(`${sec}/${d}/${f}`);
    }
  }
}

// 2–3. Scorer diff + attribution over the RAW manifests (to see stamps too).
const prev = sg.loadRun(prevDir), cur = sg.loadRun(curDir);
const d = sg.diffRuns(prev, cur, { moverThreshold: 0.0001 });
const raw = (dir, sec) => JSON.parse(fs.readFileSync(path.join(dir, 'sections', sec, 'manifest.json'), 'utf8')).wpt?.results ?? {};
const moved = [], unattributed = [];
let unchangedCells = 0;
for (const sec of fs.readdirSync(path.join(prevDir, 'sections')).sort()) {
  const A = raw(prevDir, sec), B = raw(curDir, sec);
  for (const test of new Set([...Object.keys(A), ...Object.keys(B)])) {
    for (const [key, platform] of Object.entries(sg.PLATFORM_KEYS)) {
      const a = A[test]?.browserRef?.diffs?.[key], b = B[test]?.browserRef?.diffs?.[key];
      const sa = a ? `${a.ssim}|${a.wptPass}|${a.scoreExcluded ?? ''}` : 'absent';
      const sb = b ? `${b.ssim}|${b.wptPass}|${b.scoreExcluded ?? ''}` : 'absent';
      if (sa === sb) { unchangedCells++; continue; }
      const why = b?.scoreExcluded === 'absence-only' && a?.ssim === b?.ssim ? 'absence-only stamp'
        : refrozen.has(test) ? 're-frozen ref' : null;
      const row = { sec, test, platform, prev: sa, cur: sb, why };
      (why ? moved : unattributed).push(row);
    }
  }
}
const out = {
  _lane: 'wave52 L12 calibration — capture-hash check + attribution', prev: prevId, cur: curId,
  captures: { identical: same, differ: differ.length, onlyInOneRun: onlyOne.length, differList: differ, onlyOneList: onlyOne },
  scoreGate: { prev: d.totals.prev, cur: d.totals.cur, gained: d.gained, lost: d.lost, newlyMeasured: d.newlyMeasured, unmeasuredNow: d.unmeasuredNow, movers: d.movers },
  cells: { unchanged: unchangedCells, movedAttributed: moved.length, unattributed: unattributed.length },
  moved, unattributed,
};
fs.writeFileSync(path.join(HERE, 'calib-attribution.json'), JSON.stringify(out, null, 1) + '\n');
console.log(`captures: identical ${same} · differ ${differ.length} · in one run only ${onlyOne.length}`);
console.log(`score-gate ${prevId} → ${curId}: gained ${d.gained.length} lost ${d.lost.length} newly ${d.newlyMeasured.length} unmeasured-now ${d.unmeasuredNow.length} movers(|Δ|≥0.0001) ${d.movers.length}`);
console.log(`cells: unchanged ${unchangedCells} · moved+attributed ${moved.length} · UNATTRIBUTED ${unattributed.length}`);
for (const r of [...moved, ...unattributed]) console.log(`  ${(r.why ?? 'UNATTRIBUTED').padEnd(18)} ${r.sec}/${r.test.replace(/^css\/[^/]+\//, '')} ${r.platform}  ${r.prev} → ${r.cur}`);
