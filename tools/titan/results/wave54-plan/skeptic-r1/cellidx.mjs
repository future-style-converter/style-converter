// Plan skeptic r1 — an INDEPENDENT cell index (does not import score-gate.mjs): reads
// tools/titan/runs/<run>/sections/*/manifest.json, applies the scorer idiom verbatim
// (scored iff typeof x.ssim==='number' && !x.scoreExcluded; pass iff x.wptPass===true),
// keys "<sec>/<path>.html <platform>" (the snapshot/watch form).
import fs from 'node:fs'; import path from 'node:path';
const ROOT = path.resolve(path.dirname(new URL(import.meta.url).pathname), '../../../../..');
const KEYS = { 'web-ref': 'web', 'ios-ref': 'ios', 'android-ref': 'android' };
export function index(run) {
  const sd = path.join(ROOT, 'tools/titan/runs', run, 'sections');
  const cells = {}; const totals = { web: [0, 0], ios: [0, 0], android: [0, 0] };
  for (const sec of fs.readdirSync(sd).sort()) {
    const mp = path.join(sd, sec, 'manifest.json'); if (!fs.existsSync(mp)) continue;
    const m = JSON.parse(fs.readFileSync(mp, 'utf8'));
    for (const [test, r] of Object.entries((m.wpt && m.wpt.results) || {})) {
      const d = (r.browserRef && r.browserRef.diffs) || {};
      for (const [k, p] of Object.entries(KEYS)) { const x = d[k];
        if (!(x && typeof x.ssim === 'number' && !x.scoreExcluded)) continue;
        const pass = x.wptPass === true; cells[`${test.replace(/^css\//, '')} ${p}`] = `${pass ? 'P' : 'f'} ${x.ssim}`;
        totals[p][1]++; if (pass) totals[p][0]++; }
    }
  }
  return { run, totals, cells };
}
if (process.argv[1] && process.argv[1].endsWith('cellidx.mjs')) {
  for (const run of process.argv.slice(2)) { const ix = index(run);
    fs.writeFileSync(path.join(path.dirname(new URL(import.meta.url).pathname), `idx-${run}.json`), JSON.stringify(ix));
    console.log(run, Object.keys(ix.cells).length, JSON.stringify(ix.totals)); }
}
