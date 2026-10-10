// skeptic-r2 OWN census of L3 U3 from the WIRE (not the extractor source): a <br> component (meta.sourceTag br) whose
// Height is 20 today and whose parent's meta.runs carries a non-whitespace {text} entry after the previous {child}
// entry and before this br's entry — the br the re-arm turns into a line END (height 0). Also lists, for U3b, the
// 20-px brs that stay line-start, with the line-height declared on the host vs on an ancestor.
import fs from 'node:fs'; import path from 'node:path';
const SEC = '../../runs/wave53-final/sections';
const EXP = JSON.parse(fs.readFileSync('expectations.json', 'utf8'));
const want = new Set(EXP.lanes['L3-hyphenate-character'].wireCarriers);
const hits = new Map(); let docsWithRuns = 0, brs20 = 0;
const u3bHost = new Map(), u3bAnc = new Map();
for (const sec of fs.readdirSync(SEC).sort()) {
  const dir = path.join(SEC, sec, 'per-test-ir'); if (!fs.existsSync(dir)) continue;
  for (const f of fs.readdirSync(dir).sort()) {
    const doc = JSON.parse(fs.readFileSync(path.join(dir, f), 'utf8')); const stem = f.replace(/\.json$/, '');
    const byName = new Map(doc.components.map((c) => [c.name, c])), byId = new Map(doc.components.map((c) => [c.id, c]));
    const h20 = (c) => (c.properties || []).some((p) => p.type === 'Height' && p.data && p.data.px === 20);
    const isBr = (c) => c && c.meta && c.meta.sourceTag === 'br';
    const lh = (c) => (c.properties || []).some((p) => p.type === 'LineHeight' || p.type === 'Font');
    let any = false;
    for (const host of doc.components) {
      const runs = host.meta && host.meta.runs; if (!runs) continue; any = true;
      let textSincePrev = false, afterText = false;
      for (const e of runs) {
        if (e.text !== undefined) { if (e.text.trim()) { textSincePrev = true; } continue; }
        const c = byName.get(e.child);
        if (isBr(c) && h20(c)) {
          brs20++;
          if (textSincePrev) { if (!hits.has(stem)) hits.set(stem, []); hits.get(stem).push(c.name); }
          else {
            // U3b reading: line-start br stays 20 today and after U3 — does its HOST declare line-height on itself?
            let anc = host.slot && byId.get(host.slot.parent), ancLh = false;
            while (anc) { if (lh(anc)) ancLh = true; anc = anc.slot && byId.get(anc.slot.parent); }
            if (lh(host)) u3bHost.set(stem, (u3bHost.get(stem) || 0) + 1); else if (ancLh) u3bAnc.set(stem, (u3bAnc.get(stem) || 0) + 1);
          }
        }
        textSincePrev = false;
      }
    }
    if (any) docsWithRuns++;
  }
}
let n = 0; for (const v of hits.values()) n += v.length;
console.log(`documents with meta.runs: ${docsWithRuns}; 20-px brs inside a runs host: ${brs20}`);
console.log(`U3 (re-arm) brs: ${n} in ${hits.size} documents`);
for (const [s, v] of hits) console.log(`   ${s.padEnd(66)} ${String(v.length).padStart(2)} ${want.has(s) ? 'wire carrier' : 'NOT A WIRE CARRIER'}`);
const missing = [...want].filter((s) => !hits.has(s)); console.log(`L3 wire carriers not reached by U3: ${missing.join(', ') || 'none'}`);
let a = 0, b = 0; for (const v of u3bHost.values()) a += v; for (const v of u3bAnc.values()) b += v;
console.log(`U3b line-start 20-px brs (after U3): host declares line-height ${a} in ${u3bHost.size} docs: ${[...u3bHost.keys()].join(', ')}`);
console.log(`   ancestor-only line-height (the inherited reading U3b must NOT take): ${b} in ${u3bAnc.size} docs: ${[...u3bAnc.keys()].join(', ')}`);
