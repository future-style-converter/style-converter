#!/usr/bin/env node
// tools/titan/results/wave52-web-tail-colour-vt/bake-probe.mjs — wave-52 lane L1
// FOCUSED BAKE PROBE for F-B: drive the real view-transition bake (headless
// Chromium, the same openFrozenPage / settle / walk / solve / plan path the
// section runner uses) on EVERY solve-class bail of the evidence run and
// report what the bail path delivered — WITHOUT writing any fixture pair
// (writeFixturePair is never called; the extracted fixture lives and dies in
// this process).
//
//   node tools/titan/results/wave52-web-tail-colour-vt/bake-probe.mjs [rel …]
//
// FIX PASS (2026-10-05, skeptic must-fix 1). The first cut drove six named
// tests and the census PREDICTED the other twelve from an authorship
// predicate (`backdrop && solve-class bail`), which over-claimed nine stamps.
// The stamp fires on the MEASURED ring, so the only honest population is the
// one this drive measures: the target list is now derived, not typed — every
// test whose wave51-fix extract.log note is a bail that isSolveClassBail
// (the module's own classifier) accepts. Extra `rel` arguments are appended
// (a named negative control, say); with no arguments the list is exactly the
// solve-class population. Not a device run: no emulator, simulator,
// test-all.sh, feed-*.mjs or capture-browser-ref is involved.
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { extractFixture } from '../../extract-fixture.mjs';
import { viewTransitionBakeFixture, closeViewTransitionBakeBrowser, isSolveClassBail }
  from '../../view-transition-bake.mjs';

const HERE = path.dirname(fileURLToPath(import.meta.url));
const REPO = path.resolve(HERE, '..', '..', '..', '..');
// The evidence run of record: its log is where the bail classes come from.
const SEC = path.join(REPO, 'tools', 'titan', 'runs', 'wave51-fix', 'sections', 'css-view-transitions');

// ── The population: every solve-class bail the section run logged ───────────
// One `[vt-bake: <status> — <reason>]` note per extracted test; the reason is
// classified by the SAME function the drive gates the ring sample on.
const tests = fs.readFileSync(path.join(SEC, 'tests.list'), 'utf8').split('\n')
  .map((l) => l.trim()).filter(Boolean).map((t) => (t.startsWith('css/') ? t : `css/${t}`));
const log = fs.readFileSync(path.join(SEC, 'extract.log'), 'utf8').split('\n');
const logged = new Map();                          // rel → the logged bail reason
for (const rel of tests) {
  const line = log.find((l) => l.includes(rel) && l.includes('[vt-bake:'));
  const note = line ? /\[vt-bake: (\w+)(?: — ([^\]]*))?\]/.exec(line) : null;
  if (note?.[1] === 'bailed' && isSolveClassBail(note[2])) logged.set(rel, note[2]);
}
// Named extras ride after the population, de-duplicated, in argument order.
const TESTS = [...logged.keys(), ...process.argv.slice(2).filter((r) => !logged.has(r))];

// ── The drive ────────────────────────────────────────────────────────────────
const out = [];
try {
  for (const rel of TESTS) {
    // The static pass, exactly as the CLI's main() runs it before the bake.
    const result = await extractFixture(rel);
    // A deep copy of the components, so a changed component is named by id.
    const before = JSON.parse(JSON.stringify(result.fixture.components ?? {}));
    const beforeAll = JSON.stringify(result.fixture);
    const t0 = Date.now();
    const outcome = await viewTransitionBakeFixture(result.fixture, rel);
    const comps = result.fixture.components ?? {};
    // Every component whose bytes moved (added or changed): on a stamp this
    // must be exactly the body-root, and nothing else.
    const changed = Object.keys(comps).filter((k) => JSON.stringify(comps[k]) !== JSON.stringify(before[k]))
      .map((id) => ({ id, properties: comps[id]?.properties ?? null, _role: comps[id]?._role ?? null }));
    // Every body-root (by ROLE, the canvases' lookup key) after the drive.
    const bodyRoots = Object.entries(comps).filter(([, c]) => c && c._role === 'body-root')
      .map(([id, c]) => ({ id, properties: c.properties, _lossy: c._lossy ?? null, _lossyReasons: c._lossyReasons ?? null }));
    // How many components carry display:none (a stamp MINT is the one allowed).
    const displayNone = Object.values(comps).filter((c) => c?.properties?.display === 'none').length;
    // The reason suffix the drive writes (view-transition-bake.mjs bail
    // branch): `(frame-ring X stamped)`, `(frame-ring X not stamped)`, or none
    // (never sampled: a non-solve class or a dirty drive).
    const reason = String(outcome.reason ?? '');
    const ringNote = /\(frame-ring (.+?) (stamped|not stamped)\)$/.exec(reason);
    const rec = {
      rel, ms: Date.now() - t0, loggedReason: logged.get(rel) ?? null, outcome,
      ringSampled: !!ringNote, ringMeasured: ringNote ? (ringNote[1] === 'null' ? null : ringNote[1]) : null,
      stamped: ringNote?.[2] === 'stamped', changed, bodyRoots, displayNoneCount: displayNone,
      vtSubtree: Object.keys(comps).some((k) => k.endsWith('__vt')), mutated: JSON.stringify(result.fixture) !== beforeAll,
    };
    out.push(rec);
    console.log(`${rel.replace('css/css-view-transitions/', '')}\n  ${outcome.status} — ${reason || `${outcome.groups} groups`}` +
      `\n  sampled ${rec.ringSampled} · ring ${rec.ringMeasured} · stamped ${rec.stamped} · changed ${JSON.stringify(changed.map((c) => c.id))}` +
      ` · display:none ${displayNone} · __vt ${rec.vtSubtree} · mutated ${rec.mutated} · ${rec.ms} ms`);
  }
} finally {
  // Never leak the shared browser, whatever happened above.
  await closeViewTransitionBakeBrowser();
}

// ── Summary: the MEASURED F-B blast radius ───────────────────────────────────
const stamped = out.filter((r) => r.stamped);
const summary = {
  generatedAt: new Date().toISOString(), run: 'wave51-fix', driven: out.length,
  solveClassBails: logged.size, sampled: out.filter((r) => r.ringSampled).length,
  stamped: stamped.map((r) => ({ rel: r.rel, ring: r.ringMeasured })),
  notStamped: out.filter((r) => !r.stamped).map((r) => ({ rel: r.rel, sampled: r.ringSampled, ring: r.ringMeasured })),
  // A stamp must touch the body-root alone; any other moved id is a defect.
  stampTouchedOther: stamped.filter((r) => r.changed.some((c) => c._role !== 'body-root')).map((r) => r.rel),
  // A non-stamp must leave the fixture byte-identical.
  nonStampMutated: out.filter((r) => !r.stamped && r.mutated).map((r) => r.rel),
};
fs.writeFileSync(path.join(HERE, 'bake-probe.json'), JSON.stringify({ summary, drives: out }, null, 1) + '\n');
console.log(`\nsolve-class bails ${summary.solveClassBails} · driven ${summary.driven} · ring sampled ${summary.sampled}` +
  ` · STAMPED ${stamped.length}: ${stamped.map((r) => r.rel.replace('css/css-view-transitions/', '')).join(', ')}` +
  `\nstamp touched a non-body-root: ${summary.stampTouchedOther.length} · non-stamp mutated: ${summary.nonStampMutated.length}`);
