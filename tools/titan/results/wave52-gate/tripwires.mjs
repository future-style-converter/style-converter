#!/usr/bin/env node
// tools/titan/results/wave52-gate/tripwires.mjs
//
// The lanes' closing-gate tripwires (wave52-plan/ORCHESTRATOR-TODO.md §5/§6),
// each printed next to the value its lane pre-registered. A tripwire is a
// mechanism check, not a score: it says whether the thing a lane built is
// visibly doing its job in the gate's own artifacts.
//
//   T-L2   frame-ink census: overrun-right + overrun-left 208 + 84 → 0 / 0
//   T-L1   css-view-transitions extract.log stamps the frame ring on exactly the 4 fractional-box tests
//   T-L12  inject totals: absence-only=19 blank-captures=45
//   T-L4   b″: four abspos-auto-sizing-fit-content-percentage Android captures byte-identical to the opening gate
//   T-L6   T2: Android orange rows — floats-clear-multicol-003 at 161–163, …-balancing-003 at 171–175
//   REPORT filter-effects/backdrop-filter-basic-blur (ring-fenced: reported plainly, never a target)
//
// Usage: node tripwires.mjs <final-run> <opening-run> <refs-rev-dir-name> [calibration-run]
import { spawnSync } from 'node:child_process';
import { createRequire } from 'node:module';
import { existsSync, readdirSync, readFileSync } from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { loadRun, resolveRunDir } from '../../score-gate.mjs';

const HERE = path.dirname(fileURLToPath(import.meta.url));
const REPO = path.resolve(HERE, '..', '..', '..', '..');
const sharp = createRequire(path.join(REPO, 'package.json'))('sharp');
const [finalId, openId, rev, calibId] = process.argv.slice(2);
if (!rev) { console.error('usage: tripwires.mjs <final-run> <opening-run> <refs-rev-dir-name>'); process.exit(2); }
const run = (id) => resolveRunDir(id) ?? (console.error(`no run dir: ${id}`), process.exit(2));
const FINAL = run(finalId), OPEN = run(openId);
let tripped = 0;
const verdict = (id, ok, text) => { console.log(`${ok ? 'ok  ' : 'TRIP'} ${id}  ${text}`); if (!ok) tripped++; };

// T-L2 — the census script exits non-zero when any side overrun is left.
const census = spawnSync(process.execPath, [path.join(HERE, 'frame-ink-census.mjs'), finalId, rev, '--json', path.join(HERE, 'frame-ink-census.final.json')], { encoding: 'utf8' });
const n = (cls) => Number((census.stdout.match(new RegExp(`${cls}\\s+(\\d+)`)) || [])[1] ?? NaN);
verdict('T-L2 ', census.status === 0, `frame-ink overrun-right ${n('overrun-right')} · overrun-left ${n('overrun-left')} (wave 51: 208 · 84; expected 0 · 0) — clean ${n('clean-frame')}, top ${n('overrun-top')}, mixed ${n('mixed')}`);
if (census.status !== 0) console.log(census.stdout.split('\n').filter((l) => /^\s+overrun-(right|left)\s+\w+\s+L/.test(l)).join('\n'));

// T-L1 — the frame ring is stamped where, and only where, F-B measured a uniform ring.
const vt = path.join(FINAL, 'sections/css-view-transitions/extract.log');
const ring = existsSync(vt) ? readFileSync(vt, 'utf8').split('\n').filter((l) => l.includes('frame-ring rgb(255, 182, 193) stamped')) : [];
const ringOnFractional = ring.filter((l) => /fractional-box-with-(shadow|overflow-children)-(new|old)/.test(l)).length;
verdict('T-L1 ', ring.length === 4 && ringOnFractional === 4, `frame-ring stamped on ${ring.length} test(s), ${ringOnFractional} of them fractional-box (expected 4 / 4)`);

// T-L12 — the inject stage prints its two stamp counts once per section; the gate total is their sum.
const drv = path.join(FINAL, 'gate-driver');
let absence = 0, blank = 0, sectionsSeen = 0;
for (const sec of readdirSync(path.join(FINAL, 'sections'))) {
  // The LAST attempt's log is the one whose manifest the scorer reads.
  const logs = readdirSync(drv).filter((f) => f.startsWith(`${sec}.attempt`)).sort();
  if (!logs.length) continue;
  const m = [...readFileSync(path.join(drv, logs.at(-1)), 'utf8').matchAll(/absence-only=(\d+) blank-captures=(\d+)/g)].at(-1);
  if (m) { absence += Number(m[1]); blank += Number(m[2]); sectionsSeen++; }
}
// 45 was counted on the calibration run's (pre-lane) captures. A render lane that un-blanks a capture
// lowers it — that is a fix, so the rule is: never MORE than 45, never a NEW blank cell, and every
// cell that left the list is named (wave52-final: L4's two composited-under-rotateY-180deg cells).
const blankCells = (dir) => {
  const out = new Set();
  for (const sec of readdirSync(path.join(dir, 'sections'))) {
    const m = path.join(dir, 'sections', sec, 'manifest.json');
    if (!existsSync(m)) continue;
    for (const [t, r] of Object.entries(JSON.parse(readFileSync(m, 'utf8')).wpt?.results || {}))
      for (const [k, d] of Object.entries(r.browserRef?.diffs || {})) if (d && d.blankCaptureVsInkedRef === true) out.add(`${t} ${k}`);
  }
  return out;
};
const calibDir = resolveRunDir(calibId ?? '');
const [blankWas, blankIs] = calibDir ? [blankCells(calibDir), blankCells(FINAL)] : [new Set(), new Set()];
const unblanked = [...blankWas].filter((k) => !blankIs.has(k)), newlyBlank = [...blankIs].filter((k) => !blankWas.has(k));
verdict('T-L12', absence === 19 && blank <= 45 && newlyBlank.length === 0 && blank === 45 - unblanked.length,
  `absence-only=${absence} blank-captures=${blank} over ${sectionsSeen} section logs (expected 19 / 45 on the calibration captures; no longer blank ${unblanked.length}, newly blank ${newlyBlank.length})`);
unblanked.forEach((k) => console.log(`     no longer blank: ${k}`));
newlyBlank.forEach((k) => console.log(`     NEWLY BLANK: ${k}`));

// T-L4 — b″ claims ZERO movement on these four Android captures: byte-identical or it moved something.
const same = [1, 2, 3, 4].map((i) => {
  const rel = `sections/css-sizing/android-screenshots/wpt__css-sizing__abspos-auto-sizing-fit-content-percentage-00${i}.png`;
  const a = path.join(OPEN, rel), b = path.join(FINAL, rel);
  return existsSync(a) && existsSync(b) && readFileSync(a).equals(readFileSync(b));
});
verdict('T-L4 ', same.every(Boolean), `abspos-auto-sizing-fit-content-percentage-001…004 android byte-identical to ${openId}: ${same.map((s) => (s ? 'same' : 'DIFFERENT')).join(' ')}`);

// T-L6 — T2 paints the orange bottom border inside the clamped band: the rows it lands on are the measurement.
const orangeRows = async (file) => {
  if (!existsSync(file)) return 'missing';
  const { data, info } = await sharp(file).ensureAlpha().raw().toBuffer({ resolveWithObject: true });
  const rows = [];
  for (let y = 0; y < info.height; y++) for (let x = 0; x < info.width; x++) {
    const i = (y * info.width + x) * 4;
    // CSS `orange` = rgb(255,165,0), with the same 8/255 tolerance the census uses.
    if (Math.abs(data[i] - 255) <= 8 && Math.abs(data[i + 1] - 165) <= 8 && data[i + 2] <= 8) { rows.push(y); break; }
  }
  // Collapse to ranges: "161–163".
  const ranges = []; for (const y of rows) { const last = ranges.at(-1); if (last && y === last[1] + 1) last[1] = y; else ranges.push([y, y]); }
  return ranges.map(([a, b]) => (a === b ? `${a}` : `${a}–${b}`)).join(', ') || 'none';
};
for (const [stem, want] of [['floats-clear__floats-clear-multicol-003', '161–163'], ['floats-clear__floats-clear-multicol-balancing-003', '171–175']]) {
  const rel = `sections/CSS2/android-screenshots/wpt__CSS2__${stem}.png`;
  const [now, before] = [await orangeRows(path.join(FINAL, rel)), await orangeRows(path.join(OPEN, rel))];
  verdict('T-L6 ', now.split(', ').includes(want), `${stem} android orange rows ${now} (expected to include ${want}; ${openId}: ${before})`);
}

// REPORT — the ring-fenced test: its cells in both runs, stated and not judged.
const cellsOf = (dir) => loadRun(dir).sections['filter-effects']?.cells ?? new Map();
const [was, is] = [cellsOf(OPEN), cellsOf(FINAL)];
for (const p of ['web', 'ios', 'android']) {
  const k = `css/filter-effects/backdrop-filter-basic-blur.html|${p}`;
  const f = (c) => (c ? `${c.pass ? 'P' : 'f'} ${c.ssim}` : 'unscored');
  console.log(`     REPORT backdrop-filter-basic-blur ${p}: ${f(was.get(k))} → ${f(is.get(k))} (ring-fenced; generic mechanisms only)`);
}
console.log(tripped ? `\n${tripped} tripwire(s) tripped` : '\nall tripwires quiet');
process.exit(tripped ? 1 : 0);
