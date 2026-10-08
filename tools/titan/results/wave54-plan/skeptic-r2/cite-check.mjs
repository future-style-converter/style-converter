// skeptic-r2 citation check: an independent manifest reader (scorer idiom re-typed: scored iff typeof ssim === 'number'
// && !scoreExcluded; P iff wptPass === true), then every "<test> <platform> <P|f> <score>" and every
// "<test> … <platform> <P|f> <a> / <P|f>? <b>" shaped citation in PLAN.md + the briefs, resolved by unique suffix match.
import fs from 'node:fs'; import path from 'node:path';
const RUN = process.argv[2] || 'wave53-final'; const SEC = `../../runs/${RUN}/sections`;
const idx = {}; const KEYS = { 'web-ref': 'web', 'ios-ref': 'ios', 'android-ref': 'android' };
for (const sec of fs.readdirSync(SEC)) {
  const m = path.join(SEC, sec, 'manifest.json'); if (!fs.existsSync(m)) continue;
  const res = (JSON.parse(fs.readFileSync(m, 'utf8')).wpt || {}).results || {};
  for (const [t, r] of Object.entries(res)) for (const [k, p] of Object.entries(KEYS)) {
    const x = ((r.browserRef || {}).diffs || {})[k];
    if (x && typeof x.ssim === 'number' && !x.scoreExcluded) idx[`${t.replace(/^css\//, '')} ${p}`] = `${x.wptPass === true ? 'P' : 'f'} ${x.ssim}`;
  }
}
const snap = JSON.parse(fs.readFileSync(`cells-${RUN}.json`, 'utf8')).cells;
const diffSnap = Object.keys({ ...idx, ...snap }).filter((k) => idx[k] !== snap[k]);
console.log(`${RUN}: own index ${Object.keys(idx).length} cells; differences vs cells-${RUN}.json: ${diffSnap.length}`);
const tests = [...new Set(Object.keys(idx).map((k) => k.slice(0, k.lastIndexOf(' '))))];
const resolve = (tok) => { tok = tok.replace(/^css\//, '').replace(/\.html$/, ''); const h = tests.filter((t) => t === tok + '.html' || t.endsWith('/' + tok + '.html')); return h.length === 1 ? h[0] : null; };
const files = ['PLAN.md', ...fs.readdirSync('.').filter((f) => f.endsWith('.md') && !f.startsWith('plan-skeptic') && f !== 'PLAN.md')];
let ok = 0; const bad = [], unres = new Set();
const RE = /`?([A-Za-z0-9_\-\/\.]*[A-Za-z][A-Za-z0-9_\-\/\.]*?)`?\s+(web|ios|android)\s+(P|f)\s+([01](?:\.\d+)?)\b/g;
for (const f of files) {
  fs.readFileSync(f, 'utf8').split('\n').forEach((line, i) => {
    for (const m of line.matchAll(RE)) {
      const t = resolve(m[1]); if (!t) { unres.add(m[1]); continue; }
      const got = idx[`${t} ${m[2]}`], want = `${m[3]} ${m[4]}`;
      if (got === want || (got && got.split(' ')[0] === m[3] && Number(got.split(' ')[1]).toFixed(m[4].split('.')[1]?.length || 0) === Number(m[4]).toFixed(m[4].split('.')[1]?.length || 0) && false)) ok++;
      else bad.push(`${f}:${i + 1}  cited "${m[1]} ${m[2]} ${want}"  ${RUN} has ${got ?? 'UNSCORED'}`);
    }
  });
}
console.log(`strict citations: ${ok} OK, ${bad.length} not equal`); bad.forEach((b) => console.log('  ' + b));
console.log(`unresolved tokens (not a unique test): ${[...unres].slice(0, 40).join(' | ')}`);
