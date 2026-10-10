// Plan skeptic r1, check (1b): the "<tests> ios / android | P 0.9594 / 0.9589" and "web / ios / android f a / f b / f c"
// citation forms — platforms and values in parallel order. Each test named in the segment before the platform group
// is checked for every platform/value pair, against idx-wave53-final.json (independent manifest index).
import fs from 'node:fs'; import path from 'node:path';
const HERE = path.dirname(new URL(import.meta.url).pathname); const PLAN = path.resolve(HERE, '..');
const cells = JSON.parse(fs.readFileSync(path.join(HERE, 'idx-wave53-final.json'))).cells;
const tests = [...new Set(Object.keys(cells).map((k) => k.split(' ')[0]))];
const resolve = (tok) => tests.filter((t) => { const b = t.replace(/\.html$/, ''); return b === tok || b.endsWith('/' + tok) || b.endsWith('-' + tok) && tok.length > 8; });
const files = fs.readdirSync(PLAN).filter((f) => f.endsWith('.md') && !f.startsWith('plan-skeptic')).map((f) => path.join(PLAN, f));
const P = { web: 'web', ios: 'ios', iOS: 'ios', android: 'android', Android: 'android' };
let ok = 0; const bad = [], skipped = [];
for (const f of files) fs.readFileSync(f, 'utf8').split('\n').forEach((line, i) => {
  if (/wave53-(probe|open)|wave52|wave54-(pre|probe|final)/.test(line)) return;
  const re = /\b(web|ios|iOS|android|Android)((?:\s*\/\s*(?:web|ios|iOS|android|Android))+)\b\s*\|?\s*((?:P|f) (?:0\.\d+|1)(?:\s*\/\s*(?:(?:P|f) )?(?:0\.\d+|1))+)/g;
  let m, last = 0;
  while ((m = re.exec(line))) {
    const plats = [m[1], ...m[2].split('/').map((s) => s.trim()).filter(Boolean)].map((p) => P[p]);
    const vals = []; let curV = null;
    for (const part of m[3].split('/')) { const mm = part.trim().match(/^(?:(P|f) )?(0\.\d+|1)$/); if (!mm) continue; if (mm[1]) curV = mm[1]; vals.push([curV, Number(mm[2])]); }
    const seg = line.slice(last, m.index); last = m.index + m[0].length;
    let stem = null; const named = [];
    for (const t of seg.match(/`?[A-Za-z0-9][A-Za-z0-9_./…-]*-[A-Za-z0-9_.…-]+`?|`-[A-Za-z0-9-]+`/g) || []) {
      let tok = t.replace(/`/g, ''); if (tok.startsWith('-') && stem) tok = stem + tok;
      for (const sub of tok.includes('/-') ? [tok.split('/-')[0], ...tok.split('/-').slice(1).map((x) => tok.split('/-')[0].replace(/-[^-]+$/, '') + '-' + x)] : [tok]) {
        const h = resolve(sub); if (h.length === 1) { named.push(h[0]); const s = sub.match(/^(.*)-[^-]+$/); if (s) stem = s[1]; }
      }
    }
    if (!named.length || plats.length !== vals.length) { skipped.push(`${path.basename(f)}:${i + 1} ${m[0]} (tests ${named.length}, plats ${plats.length}, vals ${vals.length})`); continue; }
    for (const t of named) plats.forEach((p, j) => { const c = cells[`${t} ${p}`]; const [v, s] = vals[j];
      if (c && c === `${v} ${s}`) ok++; else bad.push(`${path.basename(f)}:${i + 1} ${t} ${p} cited ${v} ${s} → wave53-final ${c || 'UNSCORED'}`); });
  }
});
console.log(`slash-form citations OK: ${ok}`); console.log(`MISMATCH (${bad.length})`); bad.forEach((b) => console.log('  ' + b));
console.log(`SKIPPED (${skipped.length})`); skipped.forEach((b) => console.log('  ' + b));
