#!/usr/bin/env node
// tools/titan/results/wave52-instrument-and-calibration/body-margin-census.mjs
//
// wave-52 L12-B Fix B census — re-derives, with the SHIPPED predicate
// (capture-browser-ref.mjs bodyDeclaresMargin / pageDeclaresBodyMargin /
// uaBodyMarginFor, imported, never re-implemented), how many of the corpus
// tests the UA-body variant fires on, and compares the result by name with the
// plan's independent census (wave52-plan/body-margin-census.json: 150 tests
// declare, 149 refs declare, 19 mismatch pairs, 10 in the test-declares /
// ref-relies-on-UA direction — the only direction Fix B changes).
//
// Two predicate arms are reported: INLINE (the plan census's own scope: inline
// <style> + <body style>) and SHIPPED (inline + linked local stylesheets).
// Usage: node …/body-margin-census.mjs [RUN=wave51-fix]  (read-only)
// Writes body-margin-census.<run>.json beside this script.
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

// Paths and the shipped module.
const HERE = path.dirname(fileURLToPath(import.meta.url));
const TITAN = path.resolve(HERE, '..', '..');
const REPO = path.resolve(TITAN, '..', '..');
const WPT = path.join(REPO, 'tools', 'wpt');
const cbr = await import(path.join(TITAN, 'capture-browser-ref.mjs'));

// The scored test list = every section's tests.list of the run.
const runId = process.env.RUN || 'wave51-fix';
const secRoot = path.join(TITAN, 'runs', runId, 'sections');
if (!fs.existsSync(secRoot)) { console.error(`body-margin-census: ${secRoot} missing`); process.exit(2); }
const tests = [];
for (const sec of fs.readdirSync(secRoot).sort()) {
  const tl = path.join(secRoot, sec, 'tests.list');
  if (fs.existsSync(tl)) for (const l of fs.readFileSync(tl, 'utf8').split('\n')) if (l.trim()) tests.push(l.trim());
}

// Per test: both arms on both sides; refs resolved by the pipeline's resolver.
const rows = [];
let unresolved = 0;
for (const t of tests) {
  let refAbs;
  try { refAbs = await cbr.resolveRefPath(t); } catch { unresolved++; continue; }
  const testAbs = path.join(WPT, t);
  const inline = (p) => cbr.bodyDeclaresMargin(fs.readFileSync(p, 'utf8'));
  const row = {
    test: t, ref: path.relative(WPT, refAbs),
    testInline: inline(testAbs), refInline: inline(refAbs),
    testShipped: cbr.pageDeclaresBodyMargin(testAbs), refShipped: cbr.pageDeclaresBodyMargin(refAbs),
  };
  row.fires = cbr.uaBodyMarginFor(testAbs, refAbs);
  rows.push(row);
}

// The plan census, for the by-name comparison (its 10 affected tests).
const plan = JSON.parse(fs.readFileSync(path.join(TITAN, 'results', 'wave52-plan', 'body-margin-census.json'), 'utf8'));
const planFires = new Set(plan.mismatchPairs.filter((p) => p.direction === 'test-declares-ref-relies-on-UA').map((p) => p.test));
const planReverse = new Set(plan.mismatchPairs.filter((p) => p.direction !== 'test-declares-ref-relies-on-UA').map((p) => p.test));
const count = (f) => rows.filter(f).length;
const firesShipped = rows.filter((r) => r.fires).map((r) => r.test);
const firesInline = rows.filter((r) => r.testInline && !r.refInline).map((r) => r.test);
const reverseInline = rows.filter((r) => !r.testInline && r.refInline).map((r) => r.test);

const out = {
  _lane: 'wave52 L12 instrument-and-calibration / Part B Fix B census',
  _run: runId,
  tests: tests.length, unresolvedRefs: unresolved,
  inline: { testDeclares: count((r) => r.testInline), refDeclares: count((r) => r.refInline),
    mismatchPairs: count((r) => r.testInline !== r.refInline), fires: firesInline.length, reverse: reverseInline.length },
  shipped: { testDeclares: count((r) => r.testShipped), refDeclares: count((r) => r.refShipped),
    mismatchPairs: count((r) => r.testShipped !== r.refShipped), fires: firesShipped.length },
  firesShipped,
  vsPlan: {
    firesOnlyHere: firesShipped.filter((t) => !planFires.has(t)),
    firesOnlyInPlan: [...planFires].filter((t) => !firesShipped.includes(t)),
    reverseOnlyHere: reverseInline.filter((t) => !planReverse.has(t)),
    reverseOnlyInPlan: [...planReverse].filter((t) => !reverseInline.includes(t)),
  },
  // Distinct REF files the variant re-renders (two tests can share one ref).
  refsFiring: [...new Set(rows.filter((r) => r.fires).map((r) => r.ref))],
};
fs.writeFileSync(path.join(HERE, `body-margin-census.${runId}.json`), JSON.stringify(out, null, 1) + '\n');
console.log(`body-margin-census run=${runId} tests=${tests.length} unresolved=${unresolved}`);
console.log(`  inline arm : test ${out.inline.testDeclares} · ref ${out.inline.refDeclares} · mismatch ${out.inline.mismatchPairs} (fires ${out.inline.fires} / reverse ${out.inline.reverse})`);
console.log(`  shipped arm: test ${out.shipped.testDeclares} · ref ${out.shipped.refDeclares} · mismatch ${out.shipped.mismatchPairs} · FIRES ${out.shipped.fires}`);
console.log(`  vs plan: firesOnlyHere=${out.vsPlan.firesOnlyHere.length} firesOnlyInPlan=${out.vsPlan.firesOnlyInPlan.length} reverseOnlyHere=${out.vsPlan.reverseOnlyHere.length} reverseOnlyInPlan=${out.vsPlan.reverseOnlyInPlan.length}`);
for (const t of firesShipped) console.log(`    fires ${t}`);
