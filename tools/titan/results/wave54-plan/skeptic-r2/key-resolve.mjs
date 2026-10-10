// skeptic-r2: every expectations.json cell (predictions + must-not-move) must resolve to a key of a --movers 0 score
// JSON under adjudicate.mjs's own key function; an unresolved must-not-move cell is SILENTLY skipped by R5.
import { readFileSync } from 'node:fs';
const [expPath, scorePath] = process.argv.slice(2);
const EXP = JSON.parse(readFileSync(expPath, 'utf8')), score = JSON.parse(readFileSync(scorePath, 'utf8'));
const key = (c) => `${c.test.replace(/^css\//, '')} ${c.platform}`;
const byKey = new Map();
for (const list of ['gained', 'lost', 'movers', 'newlyMeasured', 'unmeasuredNow']) for (const c of score[list]) byKey.set(key(c), c);
let mnm = 0, mnmMiss = [], pr = 0, prMiss = [], dupKeys = 0;
const all = ['gained', 'lost', 'movers', 'newlyMeasured', 'unmeasuredNow'].reduce((n, l) => n + score[l].length, 0);
for (const [ln, l] of Object.entries(EXP.lanes)) {
  for (const c of l.mustNotMove || []) { mnm++; if (!byKey.has(c)) mnmMiss.push(`${ln} ${c}`); }
  for (const p of l.predictions || []) { if (p.kind === 'non-corpus') continue; pr++; if (!byKey.has(p.cell)) prMiss.push(`${ln} ${p.cell}`); }
}
console.log(`score rows ${all}, distinct adjudicate keys ${byKey.size} (collisions ${all - byKey.size})`);
console.log(`must-not-move ${mnm}, unresolved ${mnmMiss.length}`); mnmMiss.forEach((x) => console.log('  ' + x));
console.log(`prediction cells ${pr}, unresolved ${prMiss.length}`); prMiss.forEach((x) => console.log('  ' + x));
