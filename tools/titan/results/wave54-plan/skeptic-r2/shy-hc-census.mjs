// skeptic-r2 OWN census for L3: (a) every per-test IR document carrying U+00AD anywhere in its text (the documents
// whose Compose runs reach tookSoftHyphenBreak -> SoftHyphenCuts.took, i.e. U2-android's default-argument population);
// (b) every HyphenateCharacter / hyphenate-character declaration on the wire (typed or Generic), with its value.
import fs from 'node:fs'; import path from 'node:path';
const SEC = '../../runs/wave53-final/sections';
const EXP = JSON.parse(fs.readFileSync('expectations.json', 'utf8'));
const L3 = EXP.lanes['L3-hyphenate-character'];
const mnm = new Set(L3.mustNotMove.map((c) => c.rsplit ? c : c.slice(0, c.lastIndexOf(' '))));
const carriers = new Set([...L3.captureCarriers.android, ...L3.captureCarriers.ios]);
const shy = [], hc = [];
for (const sec of fs.readdirSync(SEC).sort()) {
  const dir = path.join(SEC, sec, 'per-test-ir'); if (!fs.existsSync(dir)) continue;
  for (const f of fs.readdirSync(dir).sort()) {
    const raw = fs.readFileSync(path.join(dir, f), 'utf8'); const stem = f.replace(/\.json$/, '');
    const test = stem.split('__').slice(1).join('/') + '.html';
    const doc = JSON.parse(raw);
    let n = 0; for (const c of doc.components) { const t = JSON.stringify(c); n += (t.match(/­|\\u00ad/gi) || []).length; }
    if (n) shy.push({ test, n, inPop: mnm.has(test), carrier: carriers.has(stem) });
    for (const c of doc.components) for (const p of c.properties || []) {
      const s = JSON.stringify(p);
      if (p.type === 'HyphenateCharacter' || /hyphenate-character/i.test(s)) hc.push(`${test} ${c.name} ${s.slice(0, 140)}`);
    }
  }
}
console.log(`(a) documents carrying U+00AD: ${shy.length}`);
for (const r of shy) console.log(`   ${r.test.padEnd(70)} shy ${String(r.n).padStart(3)}  ${r.carrier ? 'L3 CARRIER' : r.inPop ? 'L3 must-not-move' : 'NOT IN L3 POPULATION'}`);
console.log(`(b) hyphenate-character declarations on the wire: ${hc.length}`); hc.forEach((x) => console.log('   ' + x));
