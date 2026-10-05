#!/usr/bin/env node
// tools/titan/results/wave52-instrument-and-calibration/absence-only-census.mjs
//
// wave-52 L12-A census — re-derives, with the SHIPPED predicates (imported from
// tools/titan/inject-wpt-block.mjs, never re-implemented here), the four
// populations the plan pre-registered on wave51-fix:
//   19 absence-only cells (web 7 / ios 6 / android 6), 13 uniform refs,
//   73 uniform captures, 36 blank-capture-vs-inked-ref cells (33 scored + 3 excl).
// It also replays the scorer totals (score-gate.mjs's own isScored/isPass) before
// and after the gate, and writes a compact DIGEST of every low-ink cell so the
// committed unit pins can replay the gate without the gitignored run dir.
//
// Usage: node …/absence-only-census.mjs [RUN=wave51-fix]   (read-only on the run)
// Writes: absence-only-census.<run>.json and absence-only-digest.<run>.json beside
// this script. Exit 0 always (the numbers are the output); 2 if the run is absent.
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { PNG } from 'pngjs';

// Paths: this script lives two levels under tools/titan/.
const HERE = path.dirname(fileURLToPath(import.meta.url));
const TITAN = path.resolve(HERE, '..', '..');
const REPO = path.resolve(TITAN, '..', '..');
// The shipped predicates and the scorer idiom — imported so the census cannot drift.
const inject = await import(path.join(TITAN, 'inject-wpt-block.mjs'));
const { isScored, isPass, resolveRunDir, PLATFORM_KEYS } = await import(path.join(TITAN, 'score-gate.mjs'));
const { safe, fixtureStem } = await import(path.join(TITAN, 'safe-name.mjs'));

// Which run to census (the plan's evidence run by default).
const runId = process.env.RUN || 'wave51-fix';
const runDir = resolveRunDir(runId);
if (!runDir) { console.error(`absence-only-census: run ${runId} not found`); process.exit(2); }
// The composed capture dir of each platform key (score-gate's CAPTURE_DIRS shape).
const CAP_DIR = { 'web-ref': 'screenshots', 'ios-ref': 'ios-screenshots', 'android-ref': 'android-screenshots' };

// Decode a PNG once; a missing or unreadable file is reported, never guessed.
function decode(p) {
  try { return PNG.sync.read(fs.readFileSync(p)); } catch { return null; }
}
// Short verdict label used in every cell line (the plan's citation shape).
function verdict(d) { return d.scoreExcluded ? `EXCL(${d.scoreExcluded})` : (d.wptPass === true ? 'P' : 'f'); }

// Accumulators: totals before/after per platform, populations, digest rows.
const tot = { before: {}, after: {} };
for (const p of Object.values(PLATFORM_KEYS)) { tot.before[p] = { passing: 0, measured: 0 }; tot.after[p] = { passing: 0, measured: 0 }; }
const absenceOnly = [], uniformCaptures = [], blank = [], uniformRefs = new Map(), digest = [];
let tests = 0, diffsSeen = 0, composedDiffs = 0, missingCaptures = 0;

for (const sec of fs.readdirSync(path.join(runDir, 'sections')).sort()) {
  // Each section's manifest is the scorer's input; sections without one are skipped.
  const mp = path.join(runDir, 'sections', sec, 'manifest.json');
  if (!fs.existsSync(mp)) continue;
  const results = JSON.parse(fs.readFileSync(mp, 'utf8')).wpt?.results ?? {};
  for (const [testRel, r] of Object.entries(results)) {
    tests++;
    const diffs = r?.browserRef?.diffs;
    if (!diffs) continue;
    // The ref PNG the scorer diffed against (manifest-recorded, repo-relative).
    const refPath = r.browserRef.path ? path.join(REPO, r.browserRef.path) : null;
    if (refPath && !uniformRefs.has(refPath)) {
      const img = refPath && decode(refPath);
      uniformRefs.set(refPath, img ? inject.uniformColour(img) : 'unreadable');
    }
    // Deep copy: the gate mutates in place and the run must stay untouched.
    const copy = JSON.parse(JSON.stringify(diffs));
    const stamped = r.scoreEligible === false ? [] : inject.applyAbsenceOnlyGate(copy);
    for (const [key, platform] of Object.entries(PLATFORM_KEYS)) {
      const d = diffs[key];
      if (!d) continue;
      diffsSeen++;
      if (d.composed === true) composedDiffs++;
      // Scorer totals on the run as recorded, then on the gated copy.
      if (isScored(d)) { tot.before[platform].measured++; if (isPass(d)) tot.before[platform].passing++; }
      const c = copy[key];
      if (isScored(c)) { tot.after[platform].measured++; if (isPass(c)) tot.after[platform].passing++; }
      const cell = `${sec}/${testRel.replace(/^css\/[^/]+\//, '')} ${platform}`;
      const ssim = typeof d.ssim === 'number' ? d.ssim.toFixed(4) : 'null';
      if (stamped.includes(key)) absenceOnly.push(`${runId} ${cell} ${verdict(d)} ${ssim}`);
      // Decision B: decode the composed capture and read its uniformity.
      const capPath = path.join(runDir, 'sections', sec, CAP_DIR[key], `${safe(`wpt__${sec}__${fixtureStem(testRel)}`)}.png`);
      const cap = decode(capPath);
      if (!cap) missingCaptures++;
      const u = cap ? inject.uniformColour(cap) : null;
      const bCov = d.semanticPresence?.bCoveragePct;
      if (u) uniformCaptures.push(`${cell} rgba(${u.join(',')})`);
      const refU = refPath ? uniformRefs.get(refPath) : null;
      if (inject.computeBlankCaptureVsInkedRef(u, d.semanticPresence, Array.isArray(refU) ? refU : null)) blank.push({ cell: `${runId} ${cell} ${verdict(d)} ${ssim}`, rgba: u, bCov, scored: isScored(d) });
      // Digest: every cell whose ref ink is under 1 % (50× the floor) — the only
      // cells a mutated predicate could plausibly stamp — plus its scoring fields.
      if (typeof bCov !== 'number' || bCov < 1) digest.push({ sec, test: testRel, key, ssim: d.ssim ?? null, wptPass: d.wptPass ?? null, bCov: bCov ?? null, scoreExcluded: d.scoreExcluded ?? null, scoreEligible: r.scoreEligible !== false });
    }
  }
}

// Compare with the plan's census by cell name (falsifier: any difference).
const planPath = path.join(TITAN, 'results', 'wave52-plan', 'absence-only-denominator.census.json');
const plan = fs.existsSync(planPath) ? JSON.parse(fs.readFileSync(planPath, 'utf8')) : null;
const norm = (s) => s.replace(/\.html /, ' ');
const planAbs = new Set((plan?.populations?.absenceOnly?.rows ?? []).map(x => norm(x.cell)));
const planBlank = new Set((plan?.populations?.blankCaptureVsInkedRef?.rows ?? []).map(x => norm(x.cell)));
const mine = new Set(absenceOnly.map(norm)), mineBlank = new Set(blank.map(b => norm(b.cell)));
const onlyPlan = [...planAbs].filter(x => !mine.has(x)), onlyMine = [...mine].filter(x => !planAbs.has(x));
const onlyPlanB = [...planBlank].filter(x => !mineBlank.has(x)), onlyMineB = [...mineBlank].filter(x => !planBlank.has(x));
const perPlat = (list) => Object.fromEntries(Object.values(PLATFORM_KEYS).map(p => [p, list.filter(c => c.split(' ')[2] === p).length]));
const pct = (t) => ({ ...t, pct: t.measured ? +(100 * t.passing / t.measured).toFixed(2) : null });

const out = {
  _lane: 'wave52 L12 instrument-and-calibration / Part A census',
  _run: runId,
  _method: 'manifest diffs → inject-wpt-block.mjs applyAbsenceOnlyGate on a deep copy (skipping scoreEligible:false tests, the isNa caller contract); composed captures + manifest-recorded refs decoded with pngjs → uniformColour / computeBlankCaptureVsInkedRef (the shipped functions); totals via score-gate.mjs isScored/isPass',
  tests, diffsSeen, composedDiffs, missingCaptures,
  uniformRefs: [...uniformRefs.entries()].filter(([, u]) => Array.isArray(u)).map(([p, u]) => ({ ref: path.relative(REPO, p), rgba: u })),
  absenceOnly: { cells: absenceOnly.length, perPlatform: perPlat(absenceOnly), tests: new Set(absenceOnly.map(c => c.split(' ')[1])).size, rows: absenceOnly },
  uniformCaptures: { cells: uniformCaptures.length, rows: uniformCaptures },
  blankCaptureVsInkedRef: { cells: blank.length, scored: blank.filter(b => b.scored).length, excluded: blank.filter(b => !b.scored).length, tests: new Set(blank.map(b => b.cell.split(' ')[1])).size, rows: blank },
  totals: { before: Object.fromEntries(Object.entries(tot.before).map(([k, v]) => [k, pct(v)])), after: Object.fromEntries(Object.entries(tot.after).map(([k, v]) => [k, pct(v)])) },
  vsPlanCensus: plan ? { absenceOnlyOnlyInPlan: onlyPlan, absenceOnlyOnlyHere: onlyMine, blankOnlyInPlan: onlyPlanB, blankOnlyHere: onlyMineB } : 'plan census absent',
};
fs.writeFileSync(path.join(HERE, `absence-only-census.${runId}.json`), JSON.stringify(out, null, 1) + '\n');
// The digest the unit pins replay (committed: the run dirs are gitignored).
fs.writeFileSync(path.join(HERE, `absence-only-digest.${runId}.json`), JSON.stringify({ _run: runId, _filter: 'cells with semanticPresence.bCoveragePct < 1 or absent', totalsBefore: tot.before, cells: digest }) + '\n');

// Human summary — the line a skeptic compares against the plan.
console.log(`absence-only-census run=${runId} tests=${tests} diffs=${diffsSeen} composed=${composedDiffs} missingCaptures=${missingCaptures}`);
console.log(`  uniform refs ${out.uniformRefs.length} · absence-only ${absenceOnly.length} ${JSON.stringify(out.absenceOnly.perPlatform)} over ${out.absenceOnly.tests} tests · uniform captures ${uniformCaptures.length} · blank-vs-inked ${blank.length} (${out.blankCaptureVsInkedRef.scored} scored + ${out.blankCaptureVsInkedRef.excluded} excl, ${out.blankCaptureVsInkedRef.tests} tests)`);
for (const p of Object.values(PLATFORM_KEYS)) console.log(`  ${p.padEnd(8)} ${tot.before[p].passing}/${tot.before[p].measured} → ${tot.after[p].passing}/${tot.after[p].measured} (${out.totals.after[p].pct} %)`);
console.log(`  vs plan census: absence-only onlyPlan=${onlyPlan.length} onlyHere=${onlyMine.length} · blank onlyPlan=${onlyPlanB.length} onlyHere=${onlyMineB.length}`);
console.log(`  digest cells ${digest.length}`);
