#!/usr/bin/env node
// tools/titan/results/wave54-plan/fix-r3/synth-closing-r3.mjs — fix r3 (plan-skeptic round 3, R3-S1 / R3-S2):
// synthetic CLOSING records shaped exactly as the plan's score of record, built from the REAL `--movers 0` identity
// record fix-r1/score-identity-movers0.json (score-gate.mjs wave53-final wave54-open --movers 0: nothing moved; the
// opening gate proved wave54-open's 4096 cells equal wave53-final's, so its `prev` values ARE wave54-open's).
// Unlike skeptic-r3/synth-closing.mjs, the record is relabelled `prev: "wave54-open"`, `cur: <cur>` — the order the
// closing gate writes (score-gate.mjs wave54-open wave54-final) — so adjudicate.mjs's new order guard reads it with
// its default --base. Every row still gating in <exp> is set to its predicted value (the first number in `to`);
// `mnm3` additionally moves one must-not-move cell by −0.003 (inside the (0.002, 0.005) hole). Each picture is written
// as the --movers 0 record (every two-sided cell listed) and as the --movers 0.005 record score-gate.mjs would write
// for the same picture (same-verdict cells kept only at |Δ| >= 0.005).
// Usage: node synth-closing-r3.mjs <expectations.json> <identity.json> <outDir> <tag> <cur>
import { readFileSync, writeFileSync } from 'node:fs';
const [expPath, idPath, outDir, tag, curName] = process.argv.slice(2);
if (!curName) { console.error('usage: synth-closing-r3.mjs <exp> <identity.json> <outDir> <tag> <cur>'); process.exit(2); }
const EXP = JSON.parse(readFileSync(expPath, 'utf8'));
const key = (c) => `${c.test.replace(/^css\//, '')} ${c.platform}`;
const gating = Object.values(EXP.lanes).flatMap((l) => l.predictions.filter((p) => p.gating));
const predicted = (p) => Number(p.to.match(/(\d\.\d+|\b1\b)/)[1]);
const MNM = 'css-cascade/all-prop-001.html ios';   // an L7 must-not-move line (the skeptic's choice, kept for comparability)
function build(withMnm) {
  const s = JSON.parse(readFileSync(idPath, 'utf8'));
  if (s.prev !== 'wave53-final') throw new Error(`expected the wave53-final -> wave54-open identity record, got ${s.prev}`);
  s.prev = 'wave54-open'; s.cur = curName;
  const rows = s.movers; s.movers = []; s.gained = [];
  for (const r of rows) {
    const p = gating.find((g) => g.cell === key(r));
    if (p) { const v = predicted(p); r.cur = v; r.curPass = v >= 0.95; r.delta = +(v - r.prev).toFixed(4); }
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
  console.log(`${name}: ${s0.prev} -> ${s0.cur}  gained ${s0.gained.length}  movers m0 ${s0.movers.length}  m0005 ${s5.movers.length}  gating rows set ${gating.length}`);
}
