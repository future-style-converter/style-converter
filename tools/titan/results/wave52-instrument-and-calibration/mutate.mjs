#!/usr/bin/env node
// tools/titan/results/wave52-instrument-and-calibration/mutate.mjs
//
// Executed-mutation proof for the L12 pins ("A new check must be proven able to
// fail"). For each mutation: sha-256 the target, replace ONE exact substring
// (refuses if it is not found exactly once), run the named node --test file
// filtered to the pins that must catch it, require a NON-zero exit, restore the
// original bytes, and require the sha-256 to match again. Every outcome lands in
// mutations.json beside this script. Usage: node …/mutate.mjs [only-id …]
import fs from 'node:fs';
import path from 'node:path';
import crypto from 'node:crypto';
import { spawnSync } from 'node:child_process';
import { fileURLToPath } from 'node:url';

// Repo root and the two files this lane owns that the pins exercise.
const HERE = path.dirname(fileURLToPath(import.meta.url));
const REPO = path.resolve(HERE, '..', '..', '..', '..');
const INJ = 'tools/titan/inject-wpt-block.mjs';
const SG = 'tools/titan/score-gate.mjs';
const CBR = 'tools/titan/capture-browser-ref.mjs';

// id, file, exact `from` substring, `to` replacement, test file, name pattern.
const MUTATIONS = [
  ['M1', INJ, "  if (diff.wptPass !== true) return false;\n", '', 'tools/titan/inject-wpt-block.test.mjs', 'L12-A pin (1|7)'],
  ['M2', INJ, '  return ref < WPT_PRESENCE_REF_MIN_PCT;\n}\n\n/** wave-52 L12-A per-cell gate', '  return ref <= WPT_PRESENCE_REF_MIN_PCT;\n}\n\n/** wave-52 L12-A per-cell gate', 'tools/titan/inject-wpt-block.test.mjs', 'L12-A pin 1'],
  ['M3', INJ, '    if (d.scoreExcluded) continue;\n    // The pre-registered predicate', '    // The pre-registered predicate', 'tools/titan/inject-wpt-block.test.mjs', 'L12-A pin 2'],
  ['M4', INJ, '  const captureUniform = uniformColour(a);\n', '  const captureUniform = uniformColour(await padToCanvas(a, Math.max(a.width, b.width), Math.max(a.height, b.height)));\n', 'tools/titan/inject-wpt-block.test.mjs', 'L12-A pin 6 \\(wiring\\)'],
  ['M5', INJ, '      && (!web.scoreExcluded || web.scoreExcluded === ABSENCE_ONLY_STAMP);', '      && !web.scoreExcluded;', 'tools/titan/inject-wpt-block.test.mjs', 'L12-A pin 9'],
  ['M6', INJ, '  return !same;\n', '  return true;\n', 'tools/titan/inject-wpt-block.test.mjs', 'L12-A pin 6b'],
  ['M7a', INJ, 'const absenceOnlyExcluded = isNa ? [] : applyAbsenceOnlyGate({', 'const absenceOnlyExcluded = applyAbsenceOnlyGate({', 'tools/titan/inject-wpt-block.test.mjs', 'L12-A pin 3'],
  ['M7', SG, 'export function isScored(x) { return !!x && typeof x.ssim === \'number\' && !x.scoreExcluded; }', 'export function isScored(x) { return !!x && typeof x.ssim === \'number\' && x.scoreExcluded !== true; }', 'tools/titan/score-gate.test.mjs', 'L12-A'],
];
// Part B mutations are appended by the Part B section of this lane (same runner).
const extra = path.join(HERE, 'mutations-partB.json');
if (fs.existsSync(extra)) for (const m of JSON.parse(fs.readFileSync(extra, 'utf8'))) MUTATIONS.push(m);

const sha = (p) => crypto.createHash('sha256').update(fs.readFileSync(path.join(REPO, p))).digest('hex');
const only = new Set(process.argv.slice(2));
const ledger = [];
for (const [id, file, from, to, testFile, pattern] of MUTATIONS) {
  if (only.size && !only.has(id)) continue;
  const abs = path.join(REPO, file);
  const before = sha(file);
  const orig = fs.readFileSync(abs);
  const text = orig.toString('utf8');
  // Exactly one occurrence, or the mutation is ambiguous and is refused.
  const n = text.split(from).length - 1;
  if (n !== 1) { ledger.push({ id, file, error: `substring found ${n}× (need 1)` }); continue; }
  let res;
  try {
    fs.writeFileSync(abs, text.replace(from, to));
    res = spawnSync(process.execPath, ['--test', `--test-name-pattern=${pattern}`, testFile], { cwd: REPO, encoding: 'utf8', timeout: 300000, maxBuffer: 256 * 1024 * 1024 });  // an assertion can print the 1.1 MB font-bearing sheet
  } finally {
    // Restore the ORIGINAL bytes whatever happened above.
    fs.writeFileSync(abs, orig);
  }
  const after = sha(file);
  const failing = [...(res.stdout ?? '').matchAll(/^not ok \d+ - (.+)$/gm)].map(m => m[1]);
  // Caught = the runner reported a failed test (summary line or a `not ok` row).
  const failed = /# fail [1-9]/.test(res.stdout ?? '') || failing.length > 0;
  ledger.push({ id, file, pattern, exit: res.status, caught: failed && res.status !== 0, failing, shaBefore: before, shaAfter: after, restored: before === after });
  console.log(`${id} ${file} → exit ${res.status} caught=${failed} restored=${before === after} ${failing.join(' | ')}`);
}
const outPath = path.join(HERE, only.size ? `mutations.${[...only].join('-')}.json` : 'mutations.json');
fs.writeFileSync(outPath, JSON.stringify(ledger, null, 1) + '\n');
// Non-zero if any mutation went uncaught or any restore drifted.
process.exit(ledger.every(l => l.caught && l.restored) ? 0 : 1);
