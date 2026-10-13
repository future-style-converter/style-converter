// Synthetic score records for the readers' refusal / blind-path checks, all derived from the opening gate's
// --movers 0 record (wave54-final → wave55-open, 4096 cells, every one a 0-Δ mover).
import { readFileSync, writeFileSync } from 'node:fs';
const G = '/Users/dranak/Documents/Projects/Style-Converter/.claude/worktrees/trusting-bohr-bd6fbf/tools/titan/results/wave55-gate';
const src = JSON.parse(readFileSync(`${G}/score-open.movers0.json`, 'utf8'));
const clone = () => JSON.parse(JSON.stringify(src));
const w = (name, o) => writeFileSync(name, JSON.stringify(o));
const relabel = (o, prev, cur) => { o.prev = prev; o.cur = cur; return o; };
const pull = (o, test, platform) => { const i = o.movers.findIndex((c) => c.test === test && c.platform === platform); if (i < 0) throw new Error('no ' + test + ' ' + platform); return o.movers.splice(i, 1)[0]; };
// R0 the identity record relabelled base-first for the stage-1 read (Δ = 0 everywhere)
w('pre-identity.json', relabel(clone(), 'wave55-open', 'wave55-pre'));
// R1 ORDER: the opening record as is (prev wave54-final)
w('order-wrong.json', clone());
// R2 NOT A --movers 0: drop 500 movers (a thresholded record)
{ const o = relabel(clone(), 'wave55-open', 'wave55-pre'); o.movers = o.movers.slice(500); w('thresholded.json', o); }
// R3 a gating CARRIER (L5 counter-suffix ios, a prediction row) unmeasured on the probe
{ const o = relabel(clone(), 'wave55-open', 'wave55-pre'); const c = pull(o, 'css/css-counter-styles/counter-suffix.html', 'ios');
  delete c.delta; c.cur = null; c.curPass = null; o.unmeasuredNow.push(c); o.totals.cur.ios.measured--; if (c.prevPass) o.totals.cur.ios.passing--; w('carrier-unmeasured.json', o); }
// R3b the same carrier LOST (control: rule 1 must fire)
{ const o = relabel(clone(), 'wave55-open', 'wave55-pre'); const c = pull(o, 'css/css-counter-styles/counter-suffix.html', 'ios');
  delete c.delta; c.cur = 0.5; c.curPass = false; (c.prevPass ? o.lost : o.movers).push(c); w('carrier-lost.json', o); }
// R4 a section missing from the probe (css-text-decor): its cells unmeasured-now, the section in missingSections
{ const o = relabel(clone(), 'wave55-open', 'wave55-pre'); const keep = []; for (const c of o.movers) { if (c.sec === 'css-text-decor') { delete c.delta; c.cur = null; c.curPass = null; o.unmeasuredNow.push(c); } else keep.push(c); }
  o.movers = keep; o.missingSections.push('css-text-decor'); delete o.sections['css-text-decor']; w('section-missing.json', o); }
console.log('ok');
