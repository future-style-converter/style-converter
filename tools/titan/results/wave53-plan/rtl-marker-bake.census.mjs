#!/usr/bin/env node
// tools/titan/results/wave53-plan/rtl-marker-bake.census.mjs
// Wave-53 plan, family rtl-marker-bake (BACKLOG 2(c⁴)). Read-only census over
// the wave52-ship per-test IR (the wire every runtime rendered) — no images.
// Usage: node rtl-marker-bake.census.mjs [run=wave52-ship] > rtl-marker-bake.census.json
import { readdirSync, readFileSync, existsSync } from 'node:fs';
import path from 'node:path';
const ROOT = path.resolve(path.dirname(new URL(import.meta.url).pathname), '../../../..');
const run = process.argv[2] ?? 'wave52-ship';
const secDir = path.join(ROOT, 'tools/titan/runs', run, 'sections');
const prop = (c, t) => (c.properties || []).filter((p) => p.type === t).at(-1)?.data;
const up = (v) => String(v ?? '').toUpperCase().replace(/-/g, '_');
const out = {
  run, docs: 0,
  markerTextAndOutOfFlow: [],          // (a) meta.markerText + Position ABSOLUTE|FIXED
  liOutOfFlow: [],                     // (b) any list item (sourceTag li | Display LIST_ITEM) + ABSOLUTE|FIXED
  bidiBakeRootDocs: [],                // (c) docs whose wire carries the rootProperties signature
  bidiBakeRootDocsWithListItems: [],   // (d) …and a list item inside a bake root
  rtlListsUnbaked: [],                 // (e) list containers / items under a Direction RTL ancestor-or-self (bake did not run / bailed)
  markerTextDocs: 0, markerTextComponents: 0,
};
for (const sec of readdirSync(secDir).sort()) {
  const irDir = path.join(secDir, sec, 'per-test-ir');
  if (!existsSync(irDir)) continue;
  for (const f of readdirSync(irDir).filter((x) => x.endsWith('.json')).sort()) {
    const doc = JSON.parse(readFileSync(path.join(irDir, f), 'utf8'));
    out.docs++;
    const stem = f.replace(/\.json$/, '');
    const comps = Array.isArray(doc.components) ? doc.components : Object.values(doc.components || {});
    const byId = new Map(comps.map((c) => [c.id, c]));
    const parentOf = (c) => byId.get(c.slot?.parent);
    const ancestors = (c) => { const a = []; for (let p = parentOf(c); p; p = parentOf(p)) a.push(p); return a; };
    const isLi = (c) => c.meta?.sourceTag === 'li' || up(prop(c, 'Display')) === 'LIST_ITEM';
    const oof = (c) => ['ABSOLUTE', 'FIXED'].includes(up(prop(c, 'Position')));
    // bidi-bake.mjs rootProperties(): direction ltr + unicode-bidi normal + text-align left + border-box, together.
    const isBakeRoot = (c) => up(prop(c, 'Direction')) === 'LTR' && up(prop(c, 'UnicodeBidi')) === 'NORMAL'
      && up(prop(c, 'TextAlign')) === 'LEFT' && up(prop(c, 'BoxSizing')) === 'BORDER_BOX';
    let mt = 0;
    const roots = comps.filter(isBakeRoot).map((c) => c.id);
    for (const c of comps) {
      if (typeof c.meta?.markerText === 'string') mt++;
      const row = { stem, id: c.id, sourceTag: c.meta?.sourceTag ?? null, markerText: c.meta?.markerText ?? null,
        position: prop(c, 'Position') ?? null, parentListStyleType: prop(parentOf(c) ?? {}, 'ListStyleType') ?? null,
        ownListStyleType: prop(c, 'ListStyleType') ?? null, listStylePosition: prop(c, 'ListStylePosition') ?? prop(parentOf(c) ?? {}, 'ListStylePosition') ?? null };
      if (typeof c.meta?.markerText === 'string' && oof(c)) out.markerTextAndOutOfFlow.push(row);
      if (isLi(c) && oof(c)) out.liOutOfFlow.push(row);
      if (isLi(c)) {
        const chain = [c, ...ancestors(c)];
        if (chain.some((a) => up(prop(a, 'Direction')) === 'RTL')) out.rtlListsUnbaked.push(row);
      }
    }
    if (mt) { out.markerTextDocs++; out.markerTextComponents += mt; }
    if (roots.length) {
      out.bidiBakeRootDocs.push({ section: sec, stem, roots: roots.length });
      const lis = comps.filter((c) => isLi(c) && ancestors(c).concat([c]).some((a) => roots.includes(a.id)));
      if (lis.length) out.bidiBakeRootDocsWithListItems.push({ section: sec, stem, listItems: lis.map((c) => ({
        id: c.id, markerText: c.meta?.markerText ?? null, position: prop(c, 'Position') ?? null,
        parentListStyleType: prop(parentOf(c) ?? {}, 'ListStyleType') ?? null })) });
    }
  }
}
console.log(JSON.stringify(out, null, 1));
