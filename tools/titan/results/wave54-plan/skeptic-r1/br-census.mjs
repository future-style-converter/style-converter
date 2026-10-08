// Plan skeptic r1, check (2b): L3 U3 / U3b blast radius, re-derived from the wave53-final wire (per-test IR) with this
// script's own simulation of extract-fixture.mjs buildNode's BR-LINE-CONTEXT (:11457-11466 br rule, :11510-11534
// sibling update, :11706 seed = !!ownText) with and without U3's re-arm (non-whitespace {text} run between child i-1
// and child i → hasInline = true). Only hosts carrying meta.runs are reachable by U3 (it reads node.runs).
// U3b: a LINE-START br (height 20 after U3) whose host carries a LineHeight on the wire is re-heighted to that line box
// (normal → 1.2 × FontSize px); counted when the resolved box != 20, and separately ANY LineHeight-declaring host.
import fs from 'node:fs'; import path from 'node:path';
const ROOT = path.resolve(path.dirname(new URL(import.meta.url).pathname), '../../../../..');
const SD = path.join(ROOT, 'tools/titan/runs', process.argv[2] || 'wave53-final', 'sections');
const INLINE = new Set(['span', 'a', 'b', 'i', 'em', 'strong', 'code', 'small', 'sub', 'sup', 'u', 's', 'q', 'abbr', 'cite', 'time', 'label', 'mark', 'bdi', 'bdo', 'samp', 'kbd', 'var', 'img', 'input', 'select', 'button', 'textarea', 'output', 'meter', 'progress', 'ruby', 'rt', 'rb']);
const prop = (c, t) => (c.properties || []).filter((p) => p.type === t).at(-1)?.data;
const up = (v) => String(v ?? '').toUpperCase();
const hpx = (c) => { const h = prop(c, 'Height'); return h && typeof h === 'object' ? (h.px ?? h.original?.px) : undefined; };
const u3 = { docs: new Map() }, u3b = { docs: new Map() }, u3bAny = { docs: new Map() }, odd = [];
for (const sec of fs.readdirSync(SD).sort()) {
  const d = path.join(SD, sec, 'per-test-ir'); if (!fs.existsSync(d)) continue;
  for (const f of fs.readdirSync(d).filter((x) => x.endsWith('.json')).sort()) {
    const comps = JSON.parse(fs.readFileSync(path.join(d, f), 'utf8')).components;
    const byName = new Map(comps.map((c) => [c.name, c]));
    for (const host of comps) {
      const runs = host.meta?.runs; if (!Array.isArray(runs)) continue;
      let stU3 = runs.some((r) => r.text !== undefined && r.text.length > 0); // seed !!ownText (approx: any text run)
      let textSince = false, idx = 0;
      for (const r of runs) {
        if (r.text !== undefined) { if (/\S/.test(r.text)) textSince = true; continue; }
        const ch = byName.get(r.child); idx++;
        if (!ch) { odd.push(`${f} missing child ${r.child}`); continue; }
        if (idx > 1 && textSince) stU3 = true;   // U3 re-arm (child-scope twin of :11733)
        textSince = false;
        const isBr = ch.meta?.role === 'line-break' || ch.meta?.sourceTag === 'br';
        if (isBr) {
          const today = hpx(ch); const clear = prop(ch, 'Clear') !== undefined;
          const after = (stU3 || clear) ? 0 : 20;
          if (today === 20 && after === 0) { (u3.docs.get(`${sec}/${f}`) ?? u3.docs.set(`${sec}/${f}`, []).get(`${sec}/${f}`)).push(ch.name); }
          if (after === 20) {
            const lh = prop(host, 'LineHeight');
            if (lh !== undefined) {
              const fs_ = prop(host, 'FontSize'); const fpx = fs_?.px ?? 16;
              const box = lh.px ?? (lh.multiplier != null ? +(lh.multiplier * fpx).toFixed(3) : undefined);
              (u3bAny.docs.get(`${sec}/${f}`) ?? u3bAny.docs.set(`${sec}/${f}`, []).get(`${sec}/${f}`)).push(`${ch.name}:${JSON.stringify(lh).slice(0, 60)}→${box}`);
              if (box !== 20) (u3b.docs.get(`${sec}/${f}`) ?? u3b.docs.set(`${sec}/${f}`, []).get(`${sec}/${f}`)).push(`${ch.name}→${box}`);
            }
          }
          stU3 = false; continue;
        }
        const pos = up(prop(ch, 'Position')), flt = up(prop(ch, 'Float')), disp = up(typeof prop(ch, 'Display') === 'string' ? prop(ch, 'Display') : prop(ch, 'Display')?.value ?? '');
        if (pos === 'ABSOLUTE' || pos === 'FIXED' || ['LEFT', 'RIGHT', 'INLINE_START', 'INLINE_END'].includes(flt) || disp === 'NONE') continue;
        stU3 = disp ? (disp.startsWith('INLINE') || disp === 'CONTENTS') : INLINE.has(ch.meta?.sourceTag);
      }
    }
  }
}
const sum = (m) => [...m.values()].reduce((a, b) => a + b.length, 0);
console.log(`U3: ${sum(u3.docs)} brs 20 -> 0 in ${u3.docs.size} documents`); for (const [k, v] of u3.docs) console.log(`  ${k.padEnd(78)} ${v.length}`);
console.log(`U3b (line-start br, host LineHeight resolving != 20): ${sum(u3b.docs)} brs in ${u3b.docs.size} documents`); for (const [k, v] of u3b.docs) console.log(`  ${k.padEnd(78)} ${v.join(' ')}`);
console.log(`U3b reach (line-start br in ANY host carrying LineHeight): ${sum(u3bAny.docs)} brs in ${u3bAny.docs.size} documents`); for (const [k, v] of u3bAny.docs) console.log(`  ${k.padEnd(78)} ${v.length} e.g. ${v[0]}`);
if (odd.length) console.log('odd:', odd.slice(0, 5));
