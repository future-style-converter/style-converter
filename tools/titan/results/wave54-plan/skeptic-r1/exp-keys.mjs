// Plan skeptic r1, check (5b): expectations.json carries every key adjudicate.mjs / control-check.mjs (wave53-gate) read,
// with the types they assume; union sets equal the per-lane unions; every prediction row is well-formed.
import fs from 'node:fs'; import path from 'node:path';
const HERE = path.dirname(new URL(import.meta.url).pathname);
const EXP = JSON.parse(fs.readFileSync(path.join(HERE, '..', 'expectations.json'), 'utf8'));
const out = []; const fail = (m) => out.push('FAIL ' + m); const ok = (m) => out.push('ok   ' + m);
const arr = (x) => Array.isArray(x);
for (const k of ['lost', 'newlyMeasured', 'unmeasuredNow']) (EXP.expected && arr(EXP.expected[k])) ? ok(`expected.${k} array len ${EXP.expected[k].length}`) : fail(`expected.${k}`);
arr(EXP.degenerateByConstruction) ? ok(`degenerateByConstruction ${EXP.degenerateByConstruction.length}: ${EXP.degenerateByConstruction.join(', ')}`) : fail('degenerateByConstruction');
(EXP.probeDecisions && arr(EXP.probeDecisions.reverted)) ? ok(`probeDecisions.reverted len ${EXP.probeDecisions.reverted.length}`) : fail('probeDecisions.reverted');
const P = ['web', 'ios', 'android'];
const U = EXP.unionCaptureCarriers; P.every((p) => arr(U && U[p])) ? ok(`unionCaptureCarriers ${P.map((p) => p + ' ' + U[p].length).join(' · ')}`) : fail('unionCaptureCarriers');
arr(EXP.unionWireCarriers) ? ok(`unionWireCarriers ${EXP.unionWireCarriers.length}`) : fail('unionWireCarriers');
let gating = 0, flo = 0; const units = [];
for (const [id, L] of Object.entries(EXP.lanes)) {
  for (const p of P) if (!arr(L.captureCarriers && L.captureCarriers[p])) fail(`${id}.captureCarriers.${p}`);
  if (!arr(L.wireCarriers)) fail(`${id}.wireCarriers`);
  if (!arr(L.mustNotMove) || !L.mustNotMove.length) fail(`${id}.mustNotMove empty/absent`);
  for (const r of L.predictions || []) {
    for (const k of ['cell', 'from', 'to', 'confidence']) if (typeof r[k] !== 'string') fail(`${id} pred ${r.cell} .${k}`);
    if (typeof r.gating !== 'boolean') fail(`${id} ${r.cell} gating not boolean`);
    if (r.gating) { gating++; if (typeof r.floor !== 'number') fail(`${id} ${r.cell} gating without numeric floor`); else flo++; }
    if (/^(HIGH|MED-HIGH)/.test(r.confidence) && !(r.gating && typeof r.floor === 'number')) fail(`${id} ${r.cell} ${r.confidence} without gating floor`);
  }
  units.push(...Object.keys(L.revertUnits || {}).map((u) => id + ':' + u));
}
// union = per-lane union
for (const p of P) { const u = new Set(Object.values(EXP.lanes).flatMap((L) => L.captureCarriers[p])); const s = new Set(U[p]);
  (u.size === s.size && [...u].every((x) => s.has(x))) ? ok(`union ${p} == lane union (${u.size})`) : fail(`union ${p} ${s.size} vs lanes ${u.size}`); }
const wu = new Set(Object.values(EXP.lanes).flatMap((L) => L.wireCarriers)); (wu.size === EXP.unionWireCarriers.length) ? ok(`unionWire == lane union (${wu.size})`) : fail('unionWire');
ok(`gating predictions ${gating} (with numeric floor ${flo}); revert units ${units.length}: ${units.join(' ')}`);
ok(`top-level keys: ${Object.keys(EXP).join(', ')}`);
console.log(out.join('\n')); process.exit(out.some((l) => l.startsWith('FAIL')) ? 1 : 0);
