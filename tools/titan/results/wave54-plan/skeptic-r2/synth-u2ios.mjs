// skeptic-r2: closing score after a U2-ios revert (hook-u2ios): every kept gating row at its prediction, hyphenate-
// character-001/-003 ios at the U3-only replay B on their own pixels (hyphenate-character.replay.out.txt: 0.9304 f,
// 0.9566 P), limit-chars-001 ios back at wave54-open (withdrawn -> must-not-move).
import { readFileSync, writeFileSync } from 'node:fs';
const [expPath, idPath, out] = process.argv.slice(2);
const EXP = JSON.parse(readFileSync(expPath, 'utf8'));
const key = (c) => `${c.test.replace(/^css\//, '')} ${c.platform}`;
const gone = new Set(((EXP.probeDecisions || {}).reverted || []).flatMap((d) => d.withdrawnPredictions));
const gating = Object.values(EXP.lanes).flatMap((l) => l.predictions.filter((p) => p.gating && !gone.has(p.cell)));
const B = { 'css-text/hyphens/hyphenate-character-001.html ios': [0.9304, false], 'css-text/hyphens/hyphenate-character-003.html ios': [0.9566, true] };
const s = JSON.parse(readFileSync(idPath, 'utf8')); const rows = s.movers; s.movers = []; s.gained = [];
for (const r of rows) {
  const k = key(r), p = gating.find((g) => g.cell === k);
  if (B[k]) { [r.cur, r.curPass] = B[k]; }
  else if (p) { r.cur = Number(p.to.match(/(\d\.\d+|\b1\b)/)[1]); r.curPass = true; }
  (r.prevPass === false && r.curPass === true ? s.gained : s.movers).push(r);
}
writeFileSync(out, JSON.stringify(s));
