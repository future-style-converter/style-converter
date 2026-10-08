// skeptic-r2 citation check, general form: "<test>[/-NNN…] <plat>[ / <plat>…] <P|f> <a>[ / [P|f] <b>…]" in PLAN.md and
// every brief, expanded to cells and compared with an index read straight from the run's manifests (scorer idiom).
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
const tests = [...new Set(Object.keys(idx).map((k) => k.slice(0, k.lastIndexOf(' '))))];
const resolve = (tok) => { tok = tok.replace(/^css\//, '').replace(/\.html$/, '').replace(/^\//, ''); if (!tok || !/\d|[a-z]-[a-z]/.test(tok)) return null;
  let h = tests.filter((t) => t === tok + '.html' || t.endsWith('/' + tok + '.html')); if (h.length === 1) return h[0];
  h = tests.filter((t) => t.endsWith('-' + tok + '.html')); return h.length === 1 ? h[0] : null; };
const RE = /`?((?:[A-Za-z0-9_.]+(?:-[A-Za-z0-9_.]+)*\/)*[A-Za-z0-9_.]+(?:-[A-Za-z0-9_.]+)*)((?:\/-\d{3}[a-z]?)*)`?((?:\s*(?:\/|,|and)\s*`?-\d{3}[a-z]?`?)*)\s+((?:web|ios|android)(?:\s*\/\s*(?:web|ios|android))*)\s*(?:\|\s*)?([Pf])\s+([01](?:\.\d+)?)((?:\s*\/\s*(?:[Pf]\s+)?[01](?:\.\d+)?)*)/g;
const files = ['PLAN.md', ...fs.readdirSync('.').filter((f) => f.endsWith('.md') && !f.startsWith('plan-skeptic') && f !== 'PLAN.md')];
let ok = 0, multi = 0; const bad = [], skipped = [];
for (const f of files) fs.readFileSync(f, 'utf8').split('\n').forEach((line, i) => {
  for (const m of line.matchAll(RE)) {
    const base = resolve(m[1]); if (!base) { continue; }
    const sufs = [...(m[2] + ' ' + m[3]).matchAll(/-(\d{3}[a-z]?)/g)].map((x) => x[1]);
    const T = [base, ...sufs.map((s) => base.replace(/\d{3}[a-z]?(\.[a-z]+)?\.html$/, `${s}$1.html`))];
    const Pl = m[4].split('/').map((s) => s.trim());
    let verdict = m[5]; const V = [`${verdict} ${m[6]}`];
    for (const x of m[7].matchAll(/\/\s*([Pf]\s+)?([01](?:\.\d+)?)/g)) { if (x[1]) verdict = x[1].trim(); V.push(`${verdict} ${x[2]}`); }
    let pairs = null;
    if (V.length === 1) pairs = T.flatMap((t) => Pl.map((p) => [t, p, V[0]]));
    else if (T.length === 1 && V.length === Pl.length) pairs = Pl.map((p, j) => [T[0], p, V[j]]);
    else if (Pl.length === 1 && V.length === T.length) pairs = T.map((t, j) => [t, Pl[0], V[j]]);
    else if (V.length === T.length * Pl.length) pairs = T.flatMap((t, a) => Pl.map((p, b) => [t, p, V[a * Pl.length + b]]));
    if (!pairs) { skipped.push(`${f}:${i + 1} ${m[0].slice(0, 90)}`); continue; }
    if (pairs.length > 1) multi++;
    for (const [t, p, v] of pairs) { const got = idx[`${t} ${p}`]; if (got === v) ok++; else bad.push(`${f}:${i + 1}  "${m[0].trim().slice(0, 100)}" -> ${t} ${p}: cited ${v}, ${RUN} ${got ?? 'UNSCORED'}`); }
  }
});
console.log(`cells checked OK ${ok} (multi-cell citations ${multi}); mismatches ${bad.length}; unparsed ${skipped.length}`);
bad.forEach((b) => console.log('  MISMATCH ' + b)); skipped.forEach((s) => console.log('  unparsed ' + s));
