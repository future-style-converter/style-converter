#!/usr/bin/env node
// tools/titan/results/wave54-label-chrome/verdict-dump.mjs — wave 54 lane L7 (label-chrome), R3 / R0 identity evidence.
// Dumps the raw `checkTriplet(pngs, <name>)` verdict object (JSON, one line per stem) for every triplet under
// tools/visual/baseline/ (130 stems at db6e8aa0) and under the wave-54 plan's 77fe41e8 copies (6 stems), using the
// checker exported by the module given as argv[2] (repo-root-relative). Run once against HEAD's test file BEFORE the
// U1 edit and once against the U1 tree; `cmp` of the two outputs proves the moved checker is verdict-identical.
//   node --test-name-pattern='^__none__$' tools/titan/results/wave54-label-chrome/verdict-dump.mjs <module> > out
// (the name-pattern flag keeps an imported test file's node:test cases from running in this process).
import { readdirSync, readFileSync } from 'node:fs';   // PNG listing + bytes
import path from 'node:path';                           // joins
import { fileURLToPath, pathToFileURL } from 'node:url'; // dynamic import of a repo-relative module
import { PNG } from 'pngjs';                            // the decoder the tripwire uses

const HERE = path.dirname(fileURLToPath(import.meta.url));                         // this lane's results dir
const ROOT = path.resolve(HERE, '../../../..');                                    // repo root
const { checkTriplet } = await import(pathToFileURL(path.join(ROOT, process.argv[2])).href); // the checker under test
const DIRS = { baseline: path.join(ROOT, 'tools/visual/baseline'), seeded: path.join(ROOT, 'tools/titan/results/wave54-plan/label-chrome-all-reset.seeded-77fe41e8') }; // both PNG sources
for (const [tag, dir] of Object.entries(DIRS)) {                                    // baseline first, then the seeded copies
  const stems = [...new Set(readdirSync(dir).map((f) => /^(?:Android|iOS|web)__(\d{3}_.+)\.png$/.exec(f)?.[1]).filter(Boolean))].sort(); // stem list
  for (const s of stems) {                                                          // one verdict per stem
    const pngs = ['Android', 'iOS', 'web'].map((p) => PNG.sync.read(readFileSync(path.join(dir, `${p}__${s}.png`)))); // fixed platform order
    console.log(`${tag} ${s} ${JSON.stringify(checkTriplet(pngs, s.slice(4)))}`);  // the raw verdict object
  }
}
