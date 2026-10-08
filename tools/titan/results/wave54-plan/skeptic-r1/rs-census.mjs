// Plan skeptic r1, check (2h): L6 RS radius — adjacent ROOT pairs (no slot.parent, body-root excluded, wire order) that
// (a) the WWS branch of renderChildSeparator would separate (prev meta.role ws-after, both isInlineLevelSibling), and
// (b) the WIDGET branch (:1105-1107, unconditional under ?wpt=1: both sourceTags in WIDGET_TAGS, NO marker needed) would
// separate — in case the lift ":1105-1158" (PLAN §2 L6) carries the widget rule into the root separator.
import fs from 'node:fs'; import path from 'node:path';
const ROOT = path.resolve(path.dirname(new URL(import.meta.url).pathname), '../../../../..');
const SD = path.join(ROOT, 'tools/titan/runs', process.argv[2] || 'wave53-final', 'sections');
const INLINE = new Set(['span', 'a', 'b', 'i', 'em', 'strong', 'code', 'small', 'sub', 'sup', 'u', 's', 'q', 'abbr', 'cite', 'time', 'label', 'mark', 'bdi', 'bdo', 'samp', 'kbd', 'var', 'img', 'input', 'select', 'button', 'textarea', 'output', 'meter', 'progress', 'ruby', 'rt', 'rb']);
const WIDGET = new Set(process.env.WIDGET_TAGS.split(','));
const disp = (c) => { for (const p of c.properties || []) if (p.type === 'Display') { const k = typeof p.data === 'string' ? p.data : p.data?.keyword ?? p.data?.type; if (typeof k === 'string') return k.toLowerCase().replace(/_/g, '-'); } return null; };
const inl = (c) => { const d = disp(c); if (d !== null) return d.startsWith('inline'); const t = c.meta?.sourceTag?.toLowerCase(); return !!t && INLINE.has(t); };
const wws = new Map(), widgetOnly = new Map();
for (const sec of fs.readdirSync(SD).sort()) {
  const d = path.join(SD, sec, 'per-test-ir'); if (!fs.existsSync(d)) continue;
  for (const f of fs.readdirSync(d).filter((x) => x.endsWith('.json')).sort()) {
    const roots = JSON.parse(fs.readFileSync(path.join(d, f), 'utf8')).components.filter((c) => !c.slot?.parent && c.meta?.role !== 'body-root');
    const k = `${sec}/${f.replace(/\.json$/, '')}`;
    for (let i = 0; i + 1 < roots.length; i++) { const a = roots[i], b = roots[i + 1];
      const wt = a.meta?.sourceTag && b.meta?.sourceTag && WIDGET.has(a.meta.sourceTag.toLowerCase()) && WIDGET.has(b.meta.sourceTag.toLowerCase());
      const w = a.meta?.role === 'ws-after' && inl(a) && inl(b);
      if (w) wws.set(k, (wws.get(k) ?? 0) + 1); else if (wt) widgetOnly.set(k, (widgetOnly.get(k) ?? 0) + 1); }
  }
}
console.log(`WWS root pairs: ${[...wws.values()].reduce((a, b) => a + b, 0)} in ${wws.size} documents`); for (const [k, v] of wws) console.log(`  ${k} ${v}`);
console.log(`WIDGET-branch-only root pairs (no ws-after marker, both widget tags): ${[...widgetOnly.values()].reduce((a, b) => a + b, 0)} in ${widgetOnly.size} documents`); for (const [k, v] of widgetOnly) console.log(`  ${k} ${v}`);
