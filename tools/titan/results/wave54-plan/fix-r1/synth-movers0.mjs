#!/usr/bin/env node
// tools/titan/results/wave54-plan/fix-r1/synth-movers0.mjs — fix r1 (plan-skeptic M1): calibrate adjudicate.mjs under the
// restated score of record (--movers 0). Builds synthetic CLOSING runs from the run of record with the scorer's OWN
// loader and diff (score-gate.mjs loadRun + diffRuns, moverThreshold 0 — exactly what `--movers 0 --json` writes), so
// the adjudicator reads the JSON shape it will read at the closing gate. Read-only over tools/titan/runs/wave53-final.
//   allmet : every GATING prediction lands exactly on its predicted value (the first "≈x" / "P x" in its `to`)
//   floor  : allmet, but counter-suffix ios lands exactly ON its floor 0.985 (Δ +0.0048, below the old 0.005 threshold)
//   mnm3   : allmet, plus one must-not-move cell moved by 0.003 (inside the (0.002, 0.005) hole the old JSON could not see)
//   mnm3-old : mnm3 diffed with the OLD moverThreshold 0.005 — the hole itself: R5 cannot see the move (and R4 misses 4)
// Writes synth-movers0-<variant>.json beside this file; the adjudicate.mjs read-outs are recorded by the caller.
import { readFileSync, writeFileSync } from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { loadRun, diffRuns, resolveRunDir } from '../../../score-gate.mjs';

const HERE = path.dirname(fileURLToPath(import.meta.url));
const EXP = JSON.parse(readFileSync(path.join(HERE, '..', 'expectations.json'), 'utf8'));
const prev = loadRun(resolveRunDir('wave53-final'));
// cell "<sec>/<path>.html <platform>" -> [section, loader key "css/<sec>/<path>.html|<platform>"] (the loader keeps the css/ prefix)
const where = new Map();
for (const [sec, s] of Object.entries(prev.sections)) for (const k of s.cells.keys()) { const [t, p] = k.split('|'); where.set(`${t.replace(/^css\//, '')} ${p}`, [sec, k]); }
const gating = Object.values(EXP.lanes).flatMap((l) => l.predictions.filter((p) => p.gating));
const clone = () => ({ runDir: 'synthetic', sections: Object.fromEntries(Object.entries(prev.sections).map(([sec, s]) => [sec, { ...s, cells: new Map([...s.cells].map(([k, v]) => [k, { ...v }])) }])) });
const set = (run, cell, ssim) => { const [sec, k] = where.get(cell); run.sections[sec].cells.set(k, { ssim, pass: ssim >= 0.95 }); };
function variant(name, tweak, moverThreshold = 0) {
  const cur = clone();
  for (const p of gating) {
    const m = p.to.match(/(?:≈|P\s*)(1(?:\.0+)?|0\.\d+)/);
    if (!m) throw new Error(`no numeric prediction in ${p.cell}: ${p.to}`);
    set(cur, p.cell, Number(m[1]));
  }
  tweak(cur);
  const d = diffRuns(prev, cur, { moverThreshold });
  writeFileSync(path.join(HERE, `synth-movers0-${name}.json`), JSON.stringify({ prev: 'wave53-final', cur: `synthetic-${name}`, ...d }));
  console.log(`${name}: moverThreshold ${moverThreshold} — gained ${d.gained.length} lost ${d.lost.length} movers ${d.movers.length}`);
}
variant('allmet', () => {});
variant('floor', (cur) => set(cur, 'css-counter-styles/counter-suffix.html ios', 0.985));
const mnmCell = EXP.lanes['L4-oof-layout'].mustNotMove[0];
variant('mnm3', (cur) => { const [sec, k] = where.get(mnmCell); const s = prev.sections[sec].cells.get(k).ssim; set(cur, mnmCell, +(s <= 0.997 ? s + 0.003 : s - 0.003).toFixed(4)); console.log(`  mnm3 moves ${mnmCell} from ${s} by 0.003`); });
variant('mnm3-old', (cur) => { const [sec, k] = where.get(mnmCell); const s = prev.sections[sec].cells.get(k).ssim; set(cur, mnmCell, +(s <= 0.997 ? s + 0.003 : s - 0.003).toFixed(4)); }, 0.005);
