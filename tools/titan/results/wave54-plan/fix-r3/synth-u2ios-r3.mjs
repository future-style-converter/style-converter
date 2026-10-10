#!/usr/bin/env node
// fix r3 (plan-skeptic R3-S2): the U2-ios-reverted closing record of skeptic-r2/synth-u2ios.mjs, relabelled base-first
// (prev "wave54-open", cur "wave54-final") so adjudicate.mjs's order guard reads it with its default --base. Built from the
// real --movers 0 identity record fix-r1/score-identity-movers0.json: every kept gating row of <hook expectations> at its
// prediction; hyphenate-character-001 / -003 ios at the U3-only replay B values on their own pixels
// (hyphenate-character.replay.out.txt: 0.9304 f, 0.9566 P — the DEGENERATE -003 ios pass); limit-chars-001 ios (withdrawn)
// back at wave54-open. Usage: node synth-u2ios-r3.mjs <hook expectations.json> <identity.json> <out.json>
import { readFileSync, writeFileSync } from 'node:fs';
const [expPath, idPath, out] = process.argv.slice(2);
const EXP = JSON.parse(readFileSync(expPath, 'utf8'));
const key = (c) => `${c.test.replace(/^css\//, '')} ${c.platform}`;
const gone = new Set(((EXP.probeDecisions || {}).reverted || []).flatMap((d) => d.withdrawnPredictions));
const gating = Object.values(EXP.lanes).flatMap((l) => l.predictions.filter((p) => p.gating && !gone.has(p.cell)));
const B = { 'css-text/hyphens/hyphenate-character-001.html ios': [0.9304, false], 'css-text/hyphens/hyphenate-character-003.html ios': [0.9566, true] };
const s = JSON.parse(readFileSync(idPath, 'utf8'));
if (s.prev !== 'wave53-final') throw new Error(`expected the wave53-final -> wave54-open identity record, got ${s.prev}`);
s.prev = 'wave54-open'; s.cur = 'wave54-final';
const rows = s.movers; s.movers = []; s.gained = [];
for (const r of rows) {
  const k = key(r), p = gating.find((g) => g.cell === k);
  if (B[k]) { [r.cur, r.curPass] = B[k]; r.delta = +(r.cur - r.prev).toFixed(4); }
  else if (p) { r.cur = Number(p.to.match(/(\d\.\d+|\b1\b)/)[1]); r.curPass = true; r.delta = +(r.cur - r.prev).toFixed(4); }
  (r.prevPass === false && r.curPass === true ? s.gained : s.movers).push(r);
}
writeFileSync(out, JSON.stringify(s));
console.log(`${out}: ${s.prev} -> ${s.cur}  gained ${s.gained.length} (${s.gained.map(key).filter((k) => k.includes('hyphenate')).join(', ')})  gating rows set ${gating.length}`);
