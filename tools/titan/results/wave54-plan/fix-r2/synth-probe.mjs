#!/usr/bin/env node
// tools/titan/results/wave54-plan/fix-r2/synth-probe.mjs — fix r2 (R2-M2): synthetic PROBE records for probe-readout.mjs,
// built like fix-r1/synth-movers0.mjs with the scorer's own loader and diff (score-gate.mjs loadRun + diffRuns,
// moverThreshold 0 — what `score-gate.mjs <base> <probe> --movers 0 --json` writes). Base = wave53-final (= wave54-open,
// cells identical). Every variant starts from "allmet" (every gating row at its predicted value) and drops the four
// non-probed sections (css-color, css-grid, css-sizing, css-view-transitions), as the real 26-section probe will.
//   allmet       : nothing else                                                   -> expect exit 0
//   fall         : backdrop-filter-plus-filter web (an up-or-stay "stays" row, P 1) -0.003 -> rule 2 names L3 U3
//   undirected   : contain-content-004 android (undirected) -0.05                -> READ only, exit 0
//   lost         : block-ellipsis-002 android P 0.9884 -> f 0.94                   -> rule 1 (+ rule 2) names L3 U3
//   leak         : one L4 must-not-move cell +0.003 and one non-carrier, non-listed cell +0.0001 -> 2 leak signals
//   missing      : allmet with css-gaps absent too                                -> exit 3 (033 rows not decided)
// Writes fix-r2/synth-probe-<variant>.json (deleted after the read-outs are recorded; re-run this script to regenerate).
import { readFileSync, writeFileSync } from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { loadRun, diffRuns, resolveRunDir } from '../../../score-gate.mjs';

const HERE = path.dirname(fileURLToPath(import.meta.url));
const EXP = JSON.parse(readFileSync(path.join(HERE, '..', 'expectations.json'), 'utf8'));
const prev = loadRun(resolveRunDir('wave53-final'));
const where = new Map();
for (const [sec, s] of Object.entries(prev.sections)) for (const k of s.cells.keys()) { const [t, p] = k.split('|'); where.set(`${t.replace(/^css\//, '')} ${p}`, [sec, k]); }
const gating = Object.values(EXP.lanes).flatMap((l) => l.predictions.filter((p) => p.gating));
const NOT_PROBED = Object.keys(EXP.probeRun.notProbed);
const clone = (drop) => ({ runDir: 'synthetic', sections: Object.fromEntries(Object.entries(prev.sections).filter(([sec]) => !drop.includes(sec))
  .map(([sec, s]) => [sec, { ...s, cells: new Map([...s.cells].map(([k, v]) => [k, { ...v }])) }])) });
const get = (cell) => { const [sec, k] = where.get(cell); return prev.sections[sec].cells.get(k); };
const set = (run, cell, ssim, pass = ssim >= 0.95) => { const [sec, k] = where.get(cell); run.sections[sec].cells.set(k, { ssim, pass }); };
function variant(name, tweak, drop = []) {
  const cur = clone([...NOT_PROBED, ...drop]);
  for (const p of gating) { if (drop.includes(p.cell.split('/')[0])) continue; const m = p.to.match(/(?:≈|P\s*)(1(?:\.0+)?|0\.\d+)/); set(cur, p.cell, Number(m[1])); }
  tweak(cur);
  const d = diffRuns(prev, cur, { moverThreshold: 0 });
  writeFileSync(path.join(HERE, `synth-probe-${name}.json`), JSON.stringify({ prev: 'wave53-final', cur: `synthetic-probe-${name}`, ...d }));
  console.log(`${name}: gained ${d.gained.length} lost ${d.lost.length} movers ${d.movers.length} unmeasured-now ${d.unmeasuredNow.length} missing sections ${d.missingSections.join(',')}`);
}
const PF = 'filter-effects/backdrop-filter-plus-filter.html web', CC4 = 'css-contain/contain-content-004.html android', BE2 = 'css-overflow/line-clamp/block-ellipsis-002.html android';
// a must-not-move cell of a probed section, and a non-carrier cell no list names (both in probed sections)
const carried = new Set(Object.values(EXP.lanes).flatMap((l) => Object.values(l.revertUnits).flatMap((u) => Object.entries(u.captures).flatMap(([p, st]) => st.map((s) => `${s.split('__').slice(1).join('/')}.html ${p}`)))));
const listed = new Set(Object.values(EXP.lanes).flatMap((l) => [...l.mustNotMove, ...l.predictions.map((p) => p.cell)]));
const MNM = EXP.lanes['L4-oof-layout'].mustNotMove.find((c) => !NOT_PROBED.includes(c.split('/')[0]));
const FREE = [...where.keys()].find((c) => !carried.has(c) && !listed.has(c) && EXP.probeRun.sections.includes(c.split('/')[0]) && get(c).ssim < 0.999);
console.log(`fall ${PF} ${get(PF).ssim}; undirected ${CC4} ${get(CC4).ssim}; lost ${BE2} ${get(BE2).ssim}; leak mnm ${MNM} ${get(MNM).ssim}; leak free ${FREE} ${get(FREE).ssim}`);
variant('allmet', () => {});
variant('fall', (cur) => set(cur, PF, +(get(PF).ssim - 0.003).toFixed(4)));
variant('undirected', (cur) => set(cur, CC4, +(get(CC4).ssim - 0.05).toFixed(4)));
variant('lost', (cur) => set(cur, BE2, 0.94));
variant('leak', (cur) => { set(cur, MNM, +(get(MNM).ssim + (get(MNM).ssim <= 0.997 ? 0.003 : -0.003)).toFixed(4)); set(cur, FREE, +(get(FREE).ssim + 0.0001).toFixed(4)); });
variant('missing', () => {}, ['css-gaps']);
