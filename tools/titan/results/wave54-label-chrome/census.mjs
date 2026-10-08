#!/usr/bin/env node
// tools/titan/results/wave54-label-chrome/census.mjs — wave 54 lane L7 (label-chrome): the lane's OWN blast-radius
// census, re-derived without the brief's script (tools/titan/results/wave54-plan/label-chrome-all-reset.census.mjs).
//   (A) code reach: every tracked file outside tools/titan/results/ and docs/ that names one of the U1 files — the
//       capture / extraction / converter / runtime paths must name none (→ capture carriers ∅, wire carriers ∅);
//   (B) the corpus documents of the run of record (tools/titan/runs/wave53-final/sections/*/per-test-ir/*.json) whose
//       IR carries an `All` property (the `all` shorthand), with their scored cells read from
//       tools/titan/results/wave54-plan/cells-wave53-final.json → the must-not-move set (expected 11 docs, 33 cells, all P);
//   (C) the label-exempt shapes among the committed baseline stems' owners is not re-derived here (brief §5 (B)):
//       instead the committed suite's own pending / verified lines are the record (replay-committed.out.txt).
// Run: node tools/titan/results/wave54-label-chrome/census.mjs   → prints (A), (B) and the cross-check vs expectations.json
import { readFileSync, readdirSync, existsSync } from 'node:fs';   // IR + cells + expectations
import { execSync } from 'node:child_process';                       // git grep for (A)
import path from 'node:path';                                        // joins
import { fileURLToPath } from 'node:url';                            // ESM has no __dirname

const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../../../..');          // repo root
const RUN = path.join(ROOT, 'tools/titan/runs/wave53-final/sections');                            // run of record
const PLAN = path.join(ROOT, 'tools/titan/results/wave54-plan');                                  // plan dir

// (A) code reach
const reach = execSync("git grep -l -E 'label-chrome-(check|exempt|tripwire)' -- ':!tools/titan/results' ':!docs' || true", { cwd: ROOT, encoding: 'utf8' }).trim().split('\n').filter(Boolean); // tracked readers
console.log(`(A) tracked files naming a U1 file (outside results/docs): ${reach.length} → ${reach.join(', ')}`);
const capturePath = reach.filter((f) => !/^tools\/visual\/label-chrome-|\.test\.(mjs|tsx?)$/.test(f)); // anything that is not the tripwire family or a test comment
console.log(`(A) of which on a capture / extraction / runtime path: ${capturePath.length}${capturePath.length ? ' → ' + capturePath.join(', ') : ''}`);

// (B) `All` documents of the run of record
const hasAll = (node) => { if (Array.isArray(node)) return node.some(hasAll); if (node && typeof node === 'object') { if (node.type === 'All') return true; return Object.values(node).some(hasAll); } return false; }; // any IR property of type All
const docs = [];                                                                                   // section/test of every All document
let scanned = 0;                                                                                   // documents read
for (const sec of readdirSync(RUN).sort()) {                                                       // 30 sections
  const dir = path.join(RUN, sec, 'per-test-ir');                                                  // that section's per-test IR
  if (!existsSync(dir)) continue;                                                                  // a section without IR is not a document source
  for (const f of readdirSync(dir).filter((n) => n.endsWith('.json')).sort()) {                    // one IR per test
    scanned += 1;                                                                                  // count it
    const doc = JSON.parse(readFileSync(path.join(dir, f), 'utf8'));                               // the verbatim wire
    if (hasAll(doc)) docs.push(`${sec}/${f.replace(/\.json$/, '')}`);                              // carries `all`
  }
}
console.log(`(B) per-test IR documents scanned: ${scanned}; carrying an All property: ${docs.length}`);
const cells = JSON.parse(readFileSync(path.join(PLAN, 'cells-wave53-final.json'), 'utf8'));        // the scored snapshot
const cellMap = cells.cells ?? cells;                                                              // tolerate either layout
const keys = Object.keys(cellMap);                                                                 // "<section>/<test> <platform>" keys
const byStem = new Map();                                                                          // per-test-IR file stem → its scored cells
for (const k of keys) { const [doc] = k.split(' '); const stem = 'wpt__' + doc.replace(/\.[a-z]+$/, '').replace(/\//g, '__'); byStem.set(stem, [...(byStem.get(stem) ?? []), k]); } // "<sec>/<test>.html p" → wpt__<sec>__<test>
const mine = docs.flatMap((d) => byStem.get(d.split('/')[1]) ?? [`${d} UNSCORED`]);                // every scored cell of every All document
const verdicts = mine.map((k) => `${k} ${cellMap[k] ?? '(no cell)'}`);                            // with their verdict / ssim
for (const v of verdicts) console.log(`    ${v}`);                                                 // print every cell
const exp = JSON.parse(readFileSync(path.join(PLAN, 'expectations.json'), 'utf8')).lanes['L7-label-chrome']; // the plan's L7 entry
const expSet = new Set(exp.mustNotMove);                                                           // plan's 33
const mineSet = new Set(mine);                                                                     // census's set
const onlyMine = [...mineSet].filter((k) => !expSet.has(k)), onlyExp = [...expSet].filter((k) => !mineSet.has(k)); // the two differences
console.log(`(B) my must-not-move cells: ${mine.length}; expectations.json mustNotMove: ${expSet.size}; only mine ${onlyMine.length} ${onlyMine.join(', ')}; only plan ${onlyExp.length} ${onlyExp.join(', ')}`);
const carriers = Object.values(exp.captureCarriers).flat().length + exp.wireCarriers.length;       // plan's carrier count
console.log(`(X) expectations.json L7 capture+wire carriers: ${carriers} (census (A): ${capturePath.length ? 'NON-EMPTY' : 'empty'}); revertUnits: ${Object.keys(exp.revertUnits).join(', ')}`);
