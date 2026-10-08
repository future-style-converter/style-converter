#!/usr/bin/env node
// wave-54 plan, family compose-table-body-cell — the wire census (read-only).
//
// Re-derives, over every per-test IR document of a run, the table shapes the
// COMPOSE table path would touch, mirroring the code read in the brief
// (compose-table-body-cell.md §3/§5):
//   * the TableBodyForest trigger (body-root's LAST Display ∈ {TABLE, INLINE_TABLE}),
//   * which boxes Compose routes to DisplayType.TABLE (declared TABLE / TABLE_ROW,
//     last Display wins — ComponentRenderer.kt DisplayExtractor `when (keyword)`;
//     or the wave-38 UA fold: `_tag == table`, no Display, consumableRowList),
//   * D1 the fabricated 1-dp cell stroke (TableApplier.TableCell): fabricatedCellBorder
//     is TRUE for a declared-Display table; SEPARATE model only (COLLAPSE always strokes),
//   * D2 the first-fill row: a row of ≥ 2 cells whose non-last cell takes the composed-WPT
//     block fill (no Width/MinWidth/InlineSize/MinInlineSize, not abspos/fixed, no
//     AspectRatio, not an atomic-inline display) — it eats the rest of the row.
// Cells are joined with the run's scored cells through score-gate.mjs's own loader.
//
// Usage: node compose-table-body-cell.census.mjs [run-id]   (default wave53-final)
// Writes compose-table-body-cell.census.json next to this file; prints a summary.
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { loadRun, resolveRunDir } from '../../score-gate.mjs';

const here = path.dirname(fileURLToPath(import.meta.url));
const run = process.argv[2] || 'wave53-final';
const runDir = resolveRunDir(run);
const scored = loadRun(runDir);

// key "css/<sec>/<a>/<b>.<ext>|<platform>" → {ssim, pass}; index by extension-less test path.
const cellsByTest = new Map();
for (const s of Object.values(scored.sections)) {
  for (const [k, c] of s.cells) {
    const [test, platform] = k.split('|');
    const stem = test.replace(/^css\//, '').replace(/\.[a-z]+$/i, '');
    if (!cellsByTest.has(stem)) cellsByTest.set(stem, {});
    cellsByTest.get(stem)[platform] = `${c.pass ? 'P' : 'f'} ${c.ssim}`;
  }
}

const TABLE_FAMILY = new Set(['TABLE', 'INLINE_TABLE', 'TABLE_ROW', 'TABLE_ROW_GROUP', 'TABLE_HEADER_GROUP',
  'TABLE_FOOTER_GROUP', 'TABLE_CELL', 'TABLE_CAPTION', 'TABLE_COLUMN', 'TABLE_COLUMN_GROUP']);
const UA_TABLE_TAGS = new Set(['table', 'tbody', 'thead', 'tfoot', 'tr', 'td', 'th', 'caption', 'col', 'colgroup']);
const SIZE_W = ['Width', 'MinWidth', 'InlineSize', 'MinInlineSize'];
const ATOMIC_INLINE = new Set(['INLINE_BLOCK', 'INLINE-BLOCK', 'INLINE_FLEX', 'INLINE-FLEX', 'INLINE_GRID', 'INLINE-GRID', 'INLINE_TABLE']);

const kw = (d) => (typeof d === 'string' ? d : (d && typeof d === 'object' && typeof d.value === 'string' ? d.value : null));
const lastDisplay = (c) => { const p = c.properties.filter((x) => x.type === 'Display').pop(); return p ? kw(p.data)?.toUpperCase() ?? null : null; };
const firstDisplay = (c) => { const p = c.properties.find((x) => x.type === 'Display'); return p ? kw(p.data)?.toUpperCase().replace(/-/g, '_') ?? null : null; };
const tagOf = (c) => (c.meta && c.meta.sourceTag ? String(c.meta.sourceTag).toLowerCase() : null);
// TableBoxTree.roleOf(component, useUaTagDefaults = true): declared FIRST Display, else UA tag.
function roleOf(c) {
  const d = firstDisplay(c);
  if (c.properties.some((x) => x.type === 'Display')) {
    if (d === 'TABLE' || d === 'INLINE_TABLE') return 'TABLE';
    if (['TABLE_ROW_GROUP', 'TABLE_HEADER_GROUP', 'TABLE_FOOTER_GROUP'].includes(d)) return 'ROW_GROUP';
    if (d === 'TABLE_ROW') return 'ROW';
    if (d === 'TABLE_CELL') return 'CELL';
    if (d === 'TABLE_CAPTION') return 'CAPTION';
    return 'NONE';
  }
  return { table: 'TABLE', tbody: 'ROW_GROUP', thead: 'ROW_GROUP', tfoot: 'ROW_GROUP', tr: 'ROW', td: 'CELL', th: 'CELL', caption: 'CAPTION' }[tagOf(c)] ?? 'NONE';
}
const dropsColumn = (c) => !c.properties.some((x) => x.type === 'Display') && ['col', 'colgroup'].includes(tagOf(c));
// TableBoxTree.rowsOf(children, useUaTagDefaults = true).
function rowsOf(kids) {
  const out = [];
  for (const k of kids) { if (dropsColumn(k)) continue; if (roleOf(k) === 'ROW_GROUP') out.push(...k.children); else out.push(k); }
  return out;
}
// Compose DisplayType for a box (ComponentRenderer.kt DisplayExtractor + the wave-38 UA fold).
function composeRoutesToTable(c) {
  const d = lastDisplay(c);
  if (d === 'TABLE' || d === 'TABLE_ROW' || d === 'TABLE-ROW') return 'declared';
  if (d === null && tagOf(c) === 'table') {
    const rows = rowsOf(c.children);
    if (rows.length && rows.every((r) => roleOf(r) === 'ROW')) return 'ua-fold';
  }
  return null;
}
// The composed-WPT block-fill guard of RenderComponent (horizontal-tb branch).
function takesFill(c) {
  if (c.properties.some((x) => SIZE_W.includes(x.type) || x.type === 'AspectRatio')) return false;
  const pos = kw(c.properties.filter((x) => x.type === 'Position').pop()?.data)?.toUpperCase();
  if (pos === 'ABSOLUTE' || pos === 'FIXED') return false;
  if (ATOMIC_INLINE.has(lastDisplay(c) ?? '')) return false;
  return true;
}

const docs = [];
let nDocs = 0;
for (const sec of fs.readdirSync(path.join(runDir, 'sections')).sort()) {
  const dir = path.join(runDir, 'sections', sec, 'per-test-ir');
  if (!fs.existsSync(dir)) continue;
  for (const f of fs.readdirSync(dir).filter((x) => x.endsWith('.json')).sort()) {
    nDocs++;
    const ir = JSON.parse(fs.readFileSync(path.join(dir, f), 'utf8'));
    const comps = ir.components.map((c) => ({ ...c, properties: c.properties || [], children: [] }));
    const byId = new Map(comps.map((c) => [c.id, c]));
    const roots = [];
    for (const c of comps) { const p = c.slot && byId.get(c.slot.parent); if (p) p.children.push(c); else roots.push(c); }
    const stem = f.replace(/^wpt__/, '').replace(/\.json$/, '').split('__').join('/');
    const tableFamily = comps.filter((c) => TABLE_FAMILY.has(lastDisplay(c) ?? '') || UA_TABLE_TAGS.has(tagOf(c) ?? ''));
    if (!tableFamily.length) continue;
    const body = roots.find((c) => c.meta && c.meta.role === 'body-root');
    const forestTrigger = !!body && ['TABLE', 'INLINE_TABLE'].includes(lastDisplay(body) ?? '');
    // A row RenderTableContent consumes (it renders the row's CELLS, never the row) is not a table of its own:
    // walk from the roots, and below a TABLE-routed box skip straight to its rows' cells.
    const rendered = [];
    const walk = (c) => {
      if (composeRoutesToTable(c)) { rendered.push(c); for (const r of rowsOf(c.children)) for (const cell of r.children) walk(cell); return; }
      for (const k of c.children) walk(k);
    };
    roots.forEach(walk);
    const tables = [];
    for (const c of rendered) {
      const route = composeRoutesToTable(c);
      const collapse = kw(c.properties.filter((x) => x.type === 'BorderCollapse').pop()?.data)?.toUpperCase() === 'COLLAPSE';
      const rows = rowsOf(c.children);
      const cellCount = rows.reduce((n, r) => n + Math.max(1, r.children.length), 0);
      const firstFillRows = rows.filter((r) => r.children.length >= 2 && r.children.slice(0, -1).some(takesFill)).length;
      tables.push({ id: c.id, route, display: lastDisplay(c), tag: tagOf(c), model: collapse ? 'COLLAPSE' : 'SEPARATE',
        rows: rows.length, cells: cellCount, fabricatedStroke: route === 'declared' && !collapse && rows.length > 0, firstFillRows });
    }
    docs.push({ test: stem, section: sec, forestTrigger,
      tableFamilyBoxes: tableFamily.length,
      displays: [...new Set(tableFamily.map((c) => lastDisplay(c) ?? `ua:${tagOf(c)}`))].sort(),
      composeTables: tables, cells: cellsByTest.get(stem) || {} });
  }
}

const reach = docs.filter((d) => d.composeTables.length);
const d1 = reach.filter((d) => d.composeTables.some((t) => t.fabricatedStroke));
const d2 = reach.filter((d) => d.composeTables.some((t) => t.firstFillRows > 0));
const summary = {
  run, docs: nDocs, tableFamilyDocs: docs.length, forestTriggerDocs: docs.filter((d) => d.forestTrigger).map((d) => d.test),
  composeTableDocs: reach.length,
  routes: { declared: reach.filter((d) => d.composeTables.some((t) => t.route === 'declared')).length,
    uaFold: reach.filter((d) => d.composeTables.some((t) => t.route === 'ua-fold')).length },
  d1FabricatedStrokeDocs: d1.map((d) => d.test), d2FirstFillDocs: d2.map((d) => d.test),
};
fs.writeFileSync(path.join(here, 'compose-table-body-cell.census.json'), JSON.stringify({ summary, docs }, null, 1) + '\n');
console.log(`run ${run}: ${nDocs} docs; ${docs.length} carry a table-family display or UA table tag; ` +
  `${reach.length} route ≥1 box to Compose DisplayType.TABLE (declared ${summary.routes.declared}, ua-fold ${summary.routes.uaFold})`);
console.log(`forest trigger (body-root TABLE/INLINE_TABLE): ${summary.forestTriggerDocs.join(', ') || 'none'}`);
const fmt = (d) => `${d.test.padEnd(74)} android ${(d.cells.android || '—').padEnd(9)} ios ${(d.cells.ios || '—').padEnd(9)} web ${d.cells.web || '—'}`;
console.log(`\nD1 fabricated-stroke docs (declared-Display table, SEPARATE model, ≥1 row): ${d1.length}`);
for (const d of d1) console.log('  ' + fmt(d));
console.log(`\nD2 first-fill docs (a row of ≥2 cells whose non-last cell takes the block fill): ${d2.length}`);
for (const d of d2) console.log('  ' + fmt(d));
console.log(`\nAll Compose-table docs:`);
for (const d of reach) console.log('  ' + fmt(d) + `  [${d.composeTables.map((t) => `${t.route}:${t.display ?? t.tag}/${t.model} r${t.rows} c${t.cells}${t.fabricatedStroke ? ' D1' : ''}${t.firstFillRows ? ` D2×${t.firstFillRows}` : ''}`).join('; ')}]`);
