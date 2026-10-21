// "All-met" synthetic records: every prediction row of the expectations moved to its predicted verdict at >= its floor,
// read base-first (wave55-open → <cur>). Variants each break ONE thing.
import { readFileSync, writeFileSync } from 'node:fs';
const R = '/Users/dranak/Documents/Projects/Style-Converter/.claude/worktrees/trusting-bohr-bd6fbf/tools/titan/results';
const src = JSON.parse(readFileSync(`${R}/wave55-gate/score-open.movers0.json`, 'utf8'));
const EXP = JSON.parse(readFileSync(`${R}/wave55-plan/expectations.json`, 'utf8'));
const key = (c) => `${c.test.replace(/^css\//, '')} ${c.platform}`;
function allMet(cur) {
  const o = JSON.parse(JSON.stringify(src)); o.prev = 'wave55-open'; o.cur = cur;
  const rows = new Map(); for (const l of Object.values(EXP.lanes)) for (const p of l.predictions || []) rows.set(p.cell, p);
  const keep = [];
  for (const c of o.movers) {
    const p = rows.get(key(c));
    if (!p) { keep.push(c); continue; }
    const wantP = /^P/.test(p.to);
    let v = c.prev;
    if (p.gating && p.floor != null) v = Math.max(v, p.floor + 0.01);
    else if (p.direction === 'down-preregistered') v = +(c.prev + p.predictedDelta).toFixed(4);
    const n = { ...c, cur: +v.toFixed(4), curPass: wantP ? true : c.prevPass };
    if (!n.curPass && wantP) n.curPass = true;
    n.delta = +(n.cur - n.prev).toFixed(4);
    if (!c.prevPass && n.curPass) { delete n.delta; o.gained.push(n); } else if (c.prevPass && !n.curPass) { delete n.delta; o.lost.push(n); } else keep.push(n);
  }
  o.movers = keep; return o;
}
const w = (n, o) => writeFileSync(n, JSON.stringify(o));
const find = (o, cell) => { for (const l of ['movers', 'gained', 'lost']) { const i = o[l].findIndex((c) => key(c) === cell); if (i >= 0) return [l, i]; } throw new Error('no ' + cell); };
w('allmet-pre.json', allMet('wave55-pre'));
w('allmet-final.json', allMet('wave55-final'));
{ const o = allMet('wave55-pre');
  for (const l of ['movers', 'gained', 'lost']) { const keep = []; for (const c of o[l]) { if (c.sec === 'css-masking') { delete c.delta; c.cur = null; c.curPass = null; o.unmeasuredNow.push(c); } else keep.push(c); } o[l] = keep; }
  o.missingSections.push('css-masking'); delete o.sections['css-masking']; w('allmet-pre-missing-masking.json', o); }
// a must-not-move complement cell in a stage-1 section moves by +0.0001 (same-host noise is 0: a leak signal)
{ const o = allMet('wave55-pre'); const cell = EXP.mustNotMoveComplement.cells.find((c) => c.startsWith('css-masking/'));
  const [l, i] = find(o, cell); o[l][i].cur = +(o[l][i].cur + 0.0001).toFixed(4); o[l][i].delta = 0.0001; w('allmet-pre-mnm-leak.json', o); console.log('mnm cell', cell); }
// an L6 may-move android stem in a stage-1 section: Δ +0.002 (inside the band) and +0.0021 (beyond)
{ const stems = EXP.unionMayMoveCaptures.android; const st = stems.find((s) => s.startsWith('wpt__css-masking__') || s.startsWith('wpt__css-flexbox__'));
  const cell = st.replace(/^wpt__/, '').split('__').join('/') + '.html android';
  for (const [tag, d] of [['in', 0.002], ['out', 0.0021]]) { const o = allMet('wave55-pre'); const [l, i] = find(o, cell); o[l][i].cur = +(o[l][i].cur + d).toFixed(4); o[l][i].delta = d; w(`allmet-pre-maymove-${tag}.json`, o); }
  console.log('may-move cell', cell); }
// a carrier (prediction row) lost on the probe
{ const o = allMet('wave55-pre'); const p = Object.values(EXP.lanes).flatMap((l) => l.predictions).find((p) => p.gating && /^P/.test(p.from) && p.cell.startsWith('css-text/'));
  const [l, i] = find(o, p.cell); const c = o[l].splice(i, 1)[0]; delete c.delta; c.cur = 0.9; c.curPass = false; o.lost.push(c); w('allmet-pre-carrier-lost.json', o); console.log('lost carrier', p.cell); }
console.log('ok');
