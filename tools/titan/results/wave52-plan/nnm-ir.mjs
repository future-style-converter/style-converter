// Read-only IR summariser: one compact line per component of a wave51-fix per-test IR document.
// Usage: node nnm-ir.mjs <cell> [<cell>...]   (cell = census name, e.g. css-tables/fixup-dynamic-anonymous-inline-table-001)
import fs from 'node:fs';
const ROOT = '/Users/dranak/Documents/Projects/Style-Converter/.claude/worktrees/trusting-bohr-bd6fbf';
const SKIP = new Set(['BoxSizing', 'PaddingTop', 'PaddingRight', 'PaddingBottom', 'PaddingLeft', 'BorderTopStyle', 'BorderRightStyle', 'BorderBottomStyle', 'BorderLeftStyle',
  'BorderTopColor', 'BorderRightColor', 'BorderBottomColor', 'BorderLeftColor', 'OverflowX', 'OverflowY']);
const val = (d) => {
  if (d == null) return 'null';
  if (typeof d !== 'object') return String(d);
  if (d.px != null) return d.px + 'px';
  if (d.percent != null) return d.percent + '%';
  if (d.srgb) return d.original && typeof d.original === 'string' ? d.original : `rgb(${Math.round(d.srgb.r * 255)},${Math.round(d.srgb.g * 255)},${Math.round(d.srgb.b * 255)}${d.srgb.a != null ? ',' + d.srgb.a : ''})`;
  if (d.rawValue != null) return `${d.propertyName}:${d.rawValue}${d._unmapped ? '(UNMAPPED)' : ''}`;
  return JSON.stringify(d).slice(0, 80);
};
for (const cell of process.argv.slice(2)) {
  const sec = cell.split('/')[0];
  const p = `${ROOT}/tools/titan/runs/wave51-fix/sections/${sec}/per-test-ir/wpt__${cell.replace(/\//g, '__')}.json`;
  if (!fs.existsSync(p)) { console.log('MISSING', p); continue; }
  const ir = JSON.parse(fs.readFileSync(p, 'utf8'));
  console.log(`\n## ${cell}  (${ir.components.length} components)`);
  for (const c of ir.components) {
    const props = (c.properties || []).filter(x => !SKIP.has(x.type)).map(x => {
      if (x.type === 'BorderTopWidth' || x.type === 'BorderRightWidth' || x.type === 'BorderBottomWidth' || x.type === 'BorderLeftWidth') return x.data?.px ? `${x.type}=${x.data.px}` : null;
      if (x.type === 'BackgroundColor' && x.data?.srgb?.a === 0) return null;
      return `${x.type}=${val(x.data)}`;
    }).filter(Boolean);
    const slot = c.slot ? ` slot=${JSON.stringify(c.slot)}` : '';
    const meta = c.meta ? Object.entries(c.meta).map(([k, v]) => `${k}:${v}`).join(',') : '';
    const idx = c.name.split('__').pop();
    console.log(`  [${idx}]${slot} {${meta}} ${c.text != null ? JSON.stringify(c.text).slice(0, 50) + ' ' : ''}| ${props.join(' ')}`);
  }
}
