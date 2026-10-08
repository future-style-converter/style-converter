// skeptic-r2: a closing score for the hook-a decision (Mprime + TB-android reverted at stage 1): withdrawn cells stay at
// wave54-open, counter-suffix android at the P-only replay 0.9815, every other gating row at its predicted value.
import { readFileSync, writeFileSync } from 'node:fs';
const [expPath, idPath, out, moveHeld] = process.argv.slice(2);
const EXP = JSON.parse(readFileSync(expPath, 'utf8'));
const key = (c) => `${c.test.replace(/^css\//, '')} ${c.platform}`;
const gone = new Set(((EXP.probeDecisions || {}).reverted || []).flatMap((d) => d.withdrawnPredictions));
const gating = Object.values(EXP.lanes).flatMap((l) => l.predictions.filter((p) => p.gating && !gone.has(p.cell)));
const s = JSON.parse(readFileSync(idPath, 'utf8')); const rows = s.movers; s.movers = []; s.gained = [];
for (const r of rows) {
  const k = key(r), p = gating.find((g) => g.cell === k);
  if (k === 'css-counter-styles/counter-suffix.html android') { r.cur = 0.9815; r.curPass = true; }
  else if (p) { const m = p.to.match(/(\d\.\d+|\b1\b)/); r.cur = Number(m[1]); r.curPass = true; }
  if (moveHeld && k === 'css-counter-styles/counter-suffix.html web') { r.cur = 0.9850; }
  (r.prevPass === false && r.curPass === true ? s.gained : s.movers).push(r);
}
writeFileSync(out, JSON.stringify(s));
