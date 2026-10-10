// skeptic-r2 OWN census of L6 RS: adjacent ROOT pairs (components with no slot.parent, wire order) where the earlier
// root carries meta.role "ws-after" and both are inline-level (declared Display starting "inline", else an inline-default
// sourceTag) — the WWS branch of renderChildSeparator applied between composed-canvas roots.
import fs from 'node:fs'; import path from 'node:path';
const SEC = '../../runs/wave53-final/sections';
const EXP = JSON.parse(fs.readFileSync('expectations.json', 'utf8'));
const car = new Set(EXP.lanes['L6-web-tail'].revertUnits.RS.captures.web);
const TAGS = new Set(['span', 'a', 'b', 'i', 'em', 'strong', 'code', 'small', 'sub', 'sup', 'u', 's', 'q', 'abbr', 'cite', 'time', 'label', 'mark', 'bdi', 'bdo', 'samp', 'kbd', 'var', 'img', 'input', 'select', 'button', 'textarea', 'output', 'meter', 'progress', 'ruby', 'rt', 'rb']);
const disp = (c) => { const p = (c.properties || []).find((q) => q.type === 'Display'); if (!p) return null; const k = typeof p.data === 'string' ? p.data : (p.data && (p.data.keyword ?? p.data.type)); return typeof k === 'string' ? k.toLowerCase().replace(/_/g, '-') : null; };
const inl = (c) => { const d = disp(c); if (d !== null) return d.startsWith('inline'); const t = c.meta && c.meta.sourceTag && c.meta.sourceTag.toLowerCase(); return !!t && TAGS.has(t); };
const found = new Map(); let pairs = 0;
for (const sec of fs.readdirSync(SEC).sort()) {
  const dir = path.join(SEC, sec, 'per-test-ir'); if (!fs.existsSync(dir)) continue;
  for (const f of fs.readdirSync(dir).sort()) {
    const doc = JSON.parse(fs.readFileSync(path.join(dir, f), 'utf8')); const stem = f.replace(/\.json$/, '');
    const roots = doc.components.filter((c) => !(c.slot && c.slot.parent));
    for (let i = 0; i + 1 < roots.length; i++) {
      const a = roots[i], b = roots[i + 1];
      if (a.meta && a.meta.role === 'ws-after' && inl(a) && inl(b)) { pairs++; found.set(stem, (found.get(stem) || 0) + 1); }
    }
  }
}
console.log(`root pairs ${pairs} in ${found.size} documents`);
for (const [s, n] of found) console.log(`   ${s.padEnd(76)} ${String(n).padStart(2)} ${car.has(s) ? 'RS carrier' : 'NOT AN RS CARRIER'}`);
console.log(`RS carriers not found: ${[...car].filter((s) => !found.has(s)).join(', ') || 'none'}`);
