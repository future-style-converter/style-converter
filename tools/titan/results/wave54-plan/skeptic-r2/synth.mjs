// skeptic-r2: synthetic closing scores built from the REAL --movers 0 identity score (wave53-final -> wave54-open),
// with gating rows set to (a) their predicted value, (b) exactly their floor, (c) floor - 0.0001 on one row.
import { readFileSync, writeFileSync } from 'node:fs';
const [expPath, idPath, outDir] = process.argv.slice(2);
const EXP = JSON.parse(readFileSync(expPath, 'utf8'));
const key = (c) => `${c.test.replace(/^css\//, '')} ${c.platform}`;
const gating = Object.values(EXP.lanes).flatMap((l) => l.predictions.filter((p) => p.gating));
function build(valueOf, name) {
  const s = JSON.parse(readFileSync(idPath, 'utf8'));
  const rows = s.movers; s.movers = []; s.gained = [];
  for (const r of rows) {
    const p = gating.find((g) => g.cell === key(r));
    if (p) { const v = valueOf(p); r.cur = v; r.curPass = true; r.delta = +(v - r.prev).toFixed(4); }
    (r.prevPass === false && r.curPass === true ? s.gained : s.movers).push(r);
  }
  writeFileSync(`${outDir}/${name}.json`, JSON.stringify(s));
}
const predicted = (p) => { const m = p.to.match(/(\d\.\d+|\b1\b)/); return Number(m[1]); };
build(predicted, 'synth-predicted');
build((p) => p.floor, 'synth-at-floor');
let first = true;
build((p) => { if (first && p.cell.includes('counter-suffix.html ios')) { first = false; return p.floor - 0.0001; } return p.floor; }, 'synth-one-below');
for (const p of gating) console.log(p.cell.padEnd(72), 'from', p.from.split(' ').slice(0, 2).join(' '), 'predicted', predicted(p), 'floor', p.floor, predicted(p) < p.floor ? 'PREDICTED<FLOOR' : '', Number(p.from.split(' ')[1]) >= p.floor ? 'FLOOR<=FROM (vacuous)' : '');
