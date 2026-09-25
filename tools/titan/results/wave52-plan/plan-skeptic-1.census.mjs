// plan-skeptic-1.census.mjs — READ-ONLY re-derivation for the wave-52 plan skeptic.
// Reads every wave51-fix section manifest, rebuilds the per-cell table, checks every
// "wave51-fix <sec>/<test> <plat> <P|f> <ssim>" citation in PLAN.md, prints headline totals.
// Writes nothing.
import fs from 'node:fs';
import path from 'node:path';
const root = '/Users/dranak/Documents/Projects/Style-Converter/.claude/worktrees/trusting-bohr-bd6fbf/';
const secDir = root + 'tools/titan/runs/wave51-fix/sections/';
const cells = {}; const totals = { web: [0, 0], ios: [0, 0], android: [0, 0] }; const excluded = { web: 0, ios: 0, android: 0 };
for (const sec of fs.readdirSync(secDir)) {
  const mp = path.join(secDir, sec, 'manifest.json'); if (!fs.existsSync(mp)) continue;
  const m = JSON.parse(fs.readFileSync(mp, 'utf8'));
  const results = (m.wpt && m.wpt.results) || {};
  for (const [test, r] of Object.entries(results)) {
    const diffs = (r.browserRef && r.browserRef.diffs) || {};
    for (const plat of ['web', 'ios', 'android']) {
      const d = diffs[plat + '-ref']; if (!d) continue;
      const key = `${test.replace(/^css\//, '').replace(/\.html$/, '')} ${plat}`;
      cells[key] = { ssim: d.ssim, wptPass: d.wptPass, cF: d.colorFailed, nI: d.novelInkFailed, ex: d.scoreExcluded, sp: d.semanticPresence };
      if (d.scoreExcluded) { excluded[plat]++; continue; }
      if (d.wptPass === true) totals[plat][0]++;
      if (d.wptPass === true || d.wptPass === false) totals[plat][1]++;
    }
  }
}
console.log('cells', Object.keys(cells).length, 'totals', JSON.stringify(totals), 'excluded', JSON.stringify(excluded));
const verdictOf = (c) => (c.ex ? 'X' : (c.wptPass === true ? 'P' : 'f'));
const plan = fs.readFileSync(root + 'tools/titan/results/wave52-plan/PLAN.md', 'utf8');
const re = /wave51-fix ([\w./+-]+) (web|ios|android) (P|f) ([\d.]+)/g;
let m, n = 0, bad = 0;
while ((m = re.exec(plan))) {
  n++; const [, t, plat, v, s] = m;
  const key = `${t} ${plat}`; const c = cells[key];
  if (!c) { console.log('NOTFOUND', key, v, s); bad++; continue; }
  const sOk = Math.abs(c.ssim - parseFloat(s)) < 0.0006;
  if (verdictOf(c) !== v || !sOk) { console.log('MISMATCH', key, 'plan', v, s, 'manifest', verdictOf(c), c.ssim.toFixed(4), 'cF', c.cF, 'nI', c.nI, 'ex', c.ex); bad++; }
}
console.log('explicit citations', n, 'bad', bad);
const re2 = /wave51-fix ([\w./+-]+) (web|ios|android) (P|f) ([\d.]+) · (web|ios|android) (P|f) ([\d.]+)/g;
while ((m = re2.exec(plan))) {
  const [, t, , , , p2, v2, s2] = m; const key = `${t} ${p2}`; const c = cells[key];
  if (!c) { console.log('NOTFOUND2', key); continue; }
  if (verdictOf(c) !== v2 || Math.abs(c.ssim - parseFloat(s2)) > 0.0006) console.log('MISMATCH2', key, 'plan', v2, s2, 'manifest', verdictOf(c), c.ssim.toFixed(4), 'cF', c.cF, 'ex', c.ex);
}
const spot = process.argv.slice(2);
if (spot.length) { console.log('\n-- spot cells:'); for (const k of spot) { const c = cells[k]; if (!c) { console.log('  NOTFOUND', k); continue; } console.log('  ', k, verdictOf(c) + (c.ex ? '(' + c.ex + ')' : ''), c.ssim.toFixed(4), c.cF ? 'cF' : '', c.nI ? 'nI' : ''); } }
if (process.env.PREFIX) { console.log('\n-- cells matching', process.env.PREFIX); for (const [k, c] of Object.entries(cells)) if (k.includes(process.env.PREFIX)) console.log('  ', k, verdictOf(c) + (c.ex ? '(' + c.ex + ')' : ''), c.ssim.toFixed(4), c.cF ? 'cF' : '', c.nI ? 'nI' : ''); }
if (process.env.WATCH) {
  // Replay score-gate.mjs watchCells matching: pat is a substring of `${sectionDir}/${manifestKey}` (manifestKey = css/<sec>/<test>.html)
  const secDirs = fs.readdirSync(secDir);
  const full = []; // [sectionDir/manifestKey, platform]
  for (const sec of secDirs) { const mp = path.join(secDir, sec, 'manifest.json'); if (!fs.existsSync(mp)) continue; const m = JSON.parse(fs.readFileSync(mp, 'utf8')); for (const [test, r] of Object.entries((m.wpt && m.wpt.results) || {})) { const diffs = (r.browserRef && r.browserRef.diffs) || {}; for (const plat of ['web', 'ios', 'android']) { const d = diffs[plat + '-ref']; if (!d || typeof d.ssim !== 'number' || d.scoreExcluded) continue; full.push([`${sec}/${test}`, plat]); } } }
  const lines = fs.readFileSync(process.env.WATCH, 'utf8').split('\n');
  console.log('\n-- watchlist replay:', process.env.WATCH, 'scored cells', full.length);
  let dead = 0, tot = 0, matchedCells = new Set();
  for (const raw of lines) { const line = raw.trim(); if (!line || line.startsWith('#')) continue; tot++; const [pat, platform] = line.split(/\s+/); let n = 0; for (const [k, plat] of full) { if (platform && plat !== platform) continue; if (!k.includes(pat)) continue; n++; matchedCells.add(k + ' ' + plat); } if (n === 0) { dead++; console.log('  DEAD  ', line); } else if (process.env.VERBOSE) console.log('  ', n, line); }
  console.log('  lines', tot, 'dead', dead, 'distinct cells matched', matchedCells.size);
}
if (process.env.ABSENCE) {
  const src = fs.readFileSync(root + 'tools/titan/inject-wpt-block.mjs', 'utf8');
  const fm = src.match(/WPT_PRESENCE_REF_MIN_PCT\s*=\s*([\d.]+)/); const floor = fm ? parseFloat(fm[1]) : NaN;
  console.log('\n-- absence-only re-derivation, floor', floor);
  let k2 = 0; for (const [k, c] of Object.entries(cells)) { if (c.ex || c.wptPass !== true) continue; const b = c.sp && c.sp.bCoveragePct; if (typeof b === 'number' && b < floor) { k2++; console.log('  ', k, c.ssim.toFixed(4), 'bCov', b); } }
  console.log('  count', k2);
}
