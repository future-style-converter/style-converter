// skeptic-r3: R2 ("unmeasured-now = 0, newly measured = 0") reads scoreExcluded, and inject-wpt-block.mjs stamps
// scoreExcluded = 'absence-only' on a PASSING diff whose REF ink is under WPT_PRESENCE_REF_MIN_PCT — a stamp that
// depends on the verdict, i.e. on the runtime. A carrier that flips f→P on a blank ref would leave the denominator
// (unmeasured-now), and a carrier excluded today as absence-only that turns into a fail would enter it (newly measured).
// This lists every carrier cell (union of every lane's revertUnits.captures) with its ref coverage and stamp.
import { readFileSync } from 'node:fs';
import { WPT_PRESENCE_REF_MIN_PCT } from '../../../inject-wpt-block.mjs';
const E = JSON.parse(readFileSync(new URL('../expectations.json', import.meta.url)));
const RUN = process.argv[2] || 'wave54-open';
const K = { web: 'web-ref', ios: 'ios-ref', android: 'android-ref' };
const man = {};
const load = (sec) => (man[sec] ??= JSON.parse(readFileSync(`tools/titan/runs/${RUN}/sections/${sec}/manifest.json`, 'utf8')));
let n = 0, risky = 0, excludedAbs = 0, minCov = Infinity;
const rows = [];
for (const [ln, l] of Object.entries(E.lanes)) for (const [u, U] of Object.entries(l.revertUnits)) for (const [p, stems] of Object.entries(U.captures)) for (const st of stems) {
  const parts = st.split('__'); const sec = parts[1]; const test = `css/${parts.slice(1).join('/')}.html`;
  const r = load(sec).wpt.results[test]; const d = r?.browserRef?.diffs?.[K[p]];
  n++;
  const cov = d?.semanticPresence?.bCoveragePct;
  if (typeof cov === 'number') minCov = Math.min(minCov, cov);
  const under = typeof cov === 'number' && cov < WPT_PRESENCE_REF_MIN_PCT;
  if (d?.scoreExcluded === 'absence-only') excludedAbs++;
  if (under || d?.scoreExcluded === 'absence-only') { risky++; rows.push(`${ln} ${u} ${test} ${p}: ref cov ${cov} stamp ${d?.scoreExcluded} wptPass ${d?.wptPass}`); }
}
console.log(`presence floor ${WPT_PRESENCE_REF_MIN_PCT}% · carrier captures ${n} · min ref coverage ${minCov} · under the floor ${risky} · absence-only today ${excludedAbs}`);
rows.forEach((x) => console.log('  ' + x));
