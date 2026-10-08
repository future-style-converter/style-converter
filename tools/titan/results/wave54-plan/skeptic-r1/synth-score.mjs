// Plan skeptic r1, check (5c): does the copied adjudicate.mjs (only the expectations path changed, as PLAN §6 says) accept
// a closing gate in which EVERY gating prediction lands exactly on its predicted value? Builds a synthetic score JSON the
// way score-gate.mjs diffRuns would with the plan's --movers 0.005: a same-verdict cell enters `movers` only when
// |Δ| >= 0.005 (score-gate.mjs:125); a f->P cell enters `gained`. Predicted value = the first "≈0.xxxx"/"P 0.xxxx" in `to`.
import fs from 'node:fs';
const E = JSON.parse(fs.readFileSync(new URL('../expectations.json', import.meta.url)));
const idx = JSON.parse(fs.readFileSync(new URL('./idx-wave53-final.json', import.meta.url))).cells;
const score = { prev: 'wave54-open(=wave53-final)', cur: 'synthetic-all-predictions-met', totals: { prev: {}, cur: {} }, gained: [], lost: [], movers: [], newlyMeasured: [], unmeasuredNow: [], missingSections: [], columnShorts: [] };
for (const p of ['web', 'ios', 'android']) { score.totals.prev[p] = { passing: 0, measured: 0 }; score.totals.cur[p] = { passing: 0, measured: 0 }; }
for (const L of Object.values(E.lanes)) for (const r of L.predictions) {
  if (!r.gating) continue; const [test, platform] = r.cell.split(' '); const [v, s] = idx[r.cell].split(' ');
  const m = r.to.match(/(?:≈|P\s*)(1(?:\.0+)?|0\.\d+)/); const cur = m ? Number(m[1]) : null; if (cur == null) { console.log('no numeric to:', r.cell, r.to); continue; }
  const row = { test: 'css/' + test, platform, prev: Number(s), cur, prevPass: v === 'P', curPass: cur >= 0.95 };
  if (v === 'f' && cur >= 0.95) score.gained.push(row); else if (Math.abs(cur - Number(s)) >= 0.005) score.movers.push(row);
  else console.log(`below the 0.005 mover threshold: ${r.cell} ${idx[r.cell]} -> predicted ${cur} (Δ ${(cur - Number(s)).toFixed(4)}), floor ${r.floor}`);
}
fs.writeFileSync(new URL('./synth-score.json', import.meta.url), JSON.stringify(score));
