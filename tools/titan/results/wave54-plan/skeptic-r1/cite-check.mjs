// Plan skeptic r1, check (1): every "<test> <platform> <P|f> <ssim>" citation in PLAN.md + briefs re-derived
// against the independent wave53-final index (idx-wave53-final.json, built by cellidx.mjs from the manifests).
// Strict rows: platform word immediately before the verdict ("android f 0.8934"). The test is the nearest
// preceding test token on the line (a `-NNN` shorthand inherits the stem of the previous full token).
// Values right after "→"/"≈" are predictions and are skipped. Lines naming another run (wave53-probe/-open,
// wave52-*, wave54-*) are reported separately as OTHER-RUN and checked against that run's index when it exists.
import fs from 'node:fs'; import path from 'node:path';
const HERE = path.dirname(new URL(import.meta.url).pathname); const PLAN = path.resolve(HERE, '..');
const idx = { 'wave53-final': JSON.parse(fs.readFileSync(path.join(HERE, 'idx-wave53-final.json'))).cells };
for (const r of ['wave53-open', 'wave53-probe']) { const f = path.join(HERE, `idx-${r}.json`); if (fs.existsSync(f)) idx[r] = JSON.parse(fs.readFileSync(f)).cells; }
const byTest = {}; for (const k of Object.keys(idx['wave53-final'])) { const [t, p] = k.split(' '); (byTest[t] ||= {})[p] = idx['wave53-final'][k]; }
const tests = Object.keys(byTest);
function resolve(tok) {
  tok = tok.replace(/^`|`$/g, '').replace(/\.html$/, '');
  const hits = tests.filter((t) => t.replace(/\.html$/, '').endsWith('/' + tok) || t.replace(/\.html$/, '') === tok);
  return hits;
}
const files = process.argv.slice(2).length ? process.argv.slice(2) : fs.readdirSync(PLAN).filter((f) => f.endsWith('.md') && !f.startsWith('plan-skeptic')).map((f) => path.join(PLAN, f));
const PLAT = { web: 'web', ios: 'ios', iOS: 'ios', android: 'android', Android: 'android' };
let ok = 0, bad = [], amb = [], other = [];
for (const f of files) {
  const lines = fs.readFileSync(f, 'utf8').split('\n');
  lines.forEach((line, i) => {
    const runM = line.match(/\b(wave5[0-4]-[a-z0-9-]+)\b/g) || [];
    const otherRuns = runM.filter((r) => r !== 'wave53-final' && !/plan|gate|lists-bakes|rtl|hyph|oof|table|ua-|web-|label|fix|L\d/.test(r));
    // tokenize: test tokens (path-ish with digits or known stems), platform words, verdict pairs, arrows
    const re = /(`?[A-Za-z0-9_][A-Za-z0-9_./-]*-[A-Za-z0-9_.-]+`?|`-[A-Za-z0-9-]+`|\s-\d{3}\b)|\b(web|ios|iOS|android|Android)\b|(→|≈)|\b(P|f) (0\.\d+|1(?:\.0+)?)\b/g;
    let m, test = null, stemPrefix = null, plat = null, lastArrow = false, lastPlatPos = -10, tokIdx = 0;
    while ((m = re.exec(line))) {
      tokIdx++;
      if (m[1]) { let tok = m[1].trim().replace(/`/g, '');
        if (tok.startsWith('-') && stemPrefix) { tok = stemPrefix + tok; }
        const hits = resolve(tok);
        if (hits.length === 1) { test = hits[0]; const s = tok.match(/^(.*?)-\d+[a-z]?$/); stemPrefix = s ? s[1] : stemPrefix; plat = null; }
        else if (hits.length > 1) { test = { amb: tok, hits }; }
        lastArrow = false; continue; }
      if (m[2]) { plat = PLAT[m[2]]; lastPlatPos = tokIdx; lastArrow = false; continue; }
      if (m[3]) { lastArrow = true; continue; }
      if (m[4]) { const v = m[4], s = m[5]; const wasArrow = lastArrow; lastArrow = false;
        if (wasArrow) continue;
        const where = `${path.basename(f)}:${i + 1}`;
        if (!test || !plat || lastPlatPos !== tokIdx - 1) { continue; }
        if (test.amb) { amb.push(`${where} ${test.amb} ${plat} ${v} ${s} (ambiguous: ${test.hits.slice(0, 3).join(', ')})`); continue; }
        const run = otherRuns.length ? otherRuns[otherRuns.length - 1] : 'wave53-final';
        const cell = (idx[run] || {})[`${test} ${plat}`];
        const cited = `${v} ${Number(s)}`;
        if (otherRuns.length) { other.push(`${where} [${otherRuns.join(',')}] ${test} ${plat} cited ${cited} | final ${idx['wave53-final'][`${test} ${plat}`] || '—'}${idx[run] ? ` | ${run} ${cell || '—'}` : ''}`); continue; }
        if (!cell) { bad.push(`${where} ${test} ${plat} cited ${cited} → UNSCORED in wave53-final`); continue; }
        const [cv, cs] = cell.split(' ');
        if (cv === v && Number(cs) === Number(s)) ok++; else bad.push(`${where} ${test} ${plat} cited ${cited} → wave53-final ${cell}`);
      }
    }
  });
}
console.log(`strict citations OK: ${ok}`); console.log(`MISMATCH (${bad.length}):`); bad.forEach((b) => console.log('  ' + b));
console.log(`AMBIGUOUS stem (${amb.length}):`); amb.forEach((b) => console.log('  ' + b));
console.log(`OTHER-RUN lines (${other.length}):`); other.forEach((b) => console.log('  ' + b));
