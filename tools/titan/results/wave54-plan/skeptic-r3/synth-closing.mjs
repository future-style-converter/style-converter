// skeptic-r3: synthetic CLOSING records from the real --movers 0 identity record (wave53-final -> wave54-open; nothing
// moved). Every gating row still gating in <exp> is set to its predicted value; optionally one must-not-move cell is
// moved +0.003. Each picture is written twice: as a --movers 0 record (every two-sided cell listed) and as the
// --movers 0.005 record score-gate.mjs would write for the same picture (movers filtered at |Δ| >= 0.005).
import { readFileSync, writeFileSync } from 'node:fs';
const [expPath, idPath, outDir, tag] = process.argv.slice(2);
const EXP = JSON.parse(readFileSync(expPath, 'utf8'));
const key = (c) => `${c.test.replace(/^css\//, '')} ${c.platform}`;
const gating = Object.values(EXP.lanes).flatMap((l) => l.predictions.filter((p) => p.gating));
const predicted = (p) => Number(p.to.match(/(\d\.\d+|\b1\b)/)[1]);
const MNM = 'css-cascade/all-prop-001.html ios';   // a must-not-move line of L7 (any would do)
function build(withMnm) {
  const s = JSON.parse(readFileSync(idPath, 'utf8'));
  const rows = s.movers; s.movers = []; s.gained = [];
  for (const r of rows) {
    const p = gating.find((g) => g.cell === key(r));
    if (p) { const v = predicted(p); r.cur = v; r.curPass = true; r.delta = +(v - r.prev).toFixed(4); }
    if (withMnm && key(r) === MNM) { r.cur = +(r.prev - 0.003).toFixed(4); r.delta = -0.003; }
    (r.prevPass === false && r.curPass === true ? s.gained : s.movers).push(r);
  }
  return s;
}
for (const withMnm of [false, true]) {
  const s0 = build(withMnm);
  const name = `${tag}-${withMnm ? 'mnm3' : 'allmet'}`;
  writeFileSync(`${outDir}/${name}.m0.json`, JSON.stringify(s0));
  const s5 = { ...s0, movers: s0.movers.filter((r) => Math.abs(r.cur - r.prev) >= 0.005) };
  writeFileSync(`${outDir}/${name}.m0005.json`, JSON.stringify(s5));
  console.log(name, 'movers m0', s0.movers.length, 'm0005', s5.movers.length, 'gained', s0.gained.length);
}
