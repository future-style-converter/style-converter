#!/usr/bin/env node
// tools/titan/results/wave54-plan/ua-heading-face.census.mjs
//
// Census for the wave-54 "ua-heading-face" brief (queue-scout-text-web.md §A).
// READ-ONLY over a gate run: every per-test IR document of every section,
// joined to that run's scored cells through the scorer's own loader.
//
// Question answered: which documents carry an element the UA sheet sizes
// (h1–h6: font-size Nem + bold; sup/sub: font-size smaller) with NO author
// FontSize/Font on the element itself, and in what wire shape:
//   leaf      — no slot children (UAElementFontRule.swift already fires on iOS)
//   folded    — the heading is a `meta.runs` host (the inline fold paints it as
//               one paragraph; iOS's headingAppliesTo stands DOWN today)
//   stacked   — element children, no runs (a Column of blocks; iOS stands down)
// plus whether an ANCESTOR declares FontSize (the em base the rule reads).
//
// Usage: node ua-heading-face.census.mjs [run-id=wave53-final] [--json out.json]
import fs from 'node:fs';
import path from 'node:path';
import { loadRun, resolveRunDir } from '../../score-gate.mjs';

const args = process.argv.slice(2);
const runId = args.find((a) => !a.startsWith('--')) ?? 'wave53-final';
const jsonOut = args.includes('--json') ? args[args.indexOf('--json') + 1] : null;
const runDir = resolveRunDir(runId);
const run = loadRun(runDir);

const HEADING = new Set(['h1', 'h2', 'h3', 'h4', 'h5', 'h6']);
const SUPSUB = new Set(['sup', 'sub']);
const SIZE = new Set(['FontSize', 'Font']);
const WEIGHT = new Set(['FontWeight', 'Font']);

// per-test IR file name → manifest key: wpt__<sec>__a__b.json → css/<sec>/a/b.html
// (resolved by scanning the section manifest keys, never by guessing the stem)
function keyIndex(sec) {
  const m = JSON.parse(fs.readFileSync(path.join(runDir, 'sections', sec, 'manifest.json'), 'utf8'));
  const idx = new Map();
  for (const k of Object.keys(m.wpt?.results ?? {})) {
    const stem = 'wpt__' + k.replace(/^css\//, '').replace(/\.[a-z]+$/, '').split('/').join('__');
    idx.set(stem, k);
  }
  return idx;
}

const rows = [];
for (const sec of Object.keys(run.sections).sort()) {
  const irDir = path.join(runDir, 'sections', sec, 'per-test-ir');
  if (!fs.existsSync(irDir) || !run.sections[sec].hasManifest) continue;
  const idx = keyIndex(sec);
  for (const f of fs.readdirSync(irDir).filter((x) => x.endsWith('.json')).sort()) {
    const doc = JSON.parse(fs.readFileSync(path.join(irDir, f), 'utf8'));
    const comps = doc.components ?? [];
    const byId = new Map(comps.map((c) => [c.id, c]));
    const kids = new Map();
    for (const c of comps) if (c.slot?.parent) { if (!kids.has(c.slot.parent)) kids.set(c.slot.parent, []); kids.get(c.slot.parent).push(c); }
    const ancestorSizes = (c) => { let p = c.slot?.parent ? byId.get(c.slot.parent) : null; while (p) { if ((p.properties ?? []).some((q) => SIZE.has(q.type))) return true; p = p.slot?.parent ? byId.get(p.slot.parent) : null; } return false; };
    const hits = [];
    for (const c of comps) {
      const tag = (c.meta?.sourceTag ?? '').toLowerCase();
      if (!HEADING.has(tag) && !SUPSUB.has(tag)) continue;
      const own = new Set((c.properties ?? []).map((p) => p.type));
      const ownSize = [...own].some((t) => SIZE.has(t));
      const ownWeight = [...own].some((t) => WEIGHT.has(t));
      const hasKids = (kids.get(c.id) ?? []).length > 0;
      const shape = !hasKids ? 'leaf' : (c.meta?.runs ? 'folded' : 'stacked');
      // a sup/sub that is a RUN MEMBER of its parent (the parent's runs name it)
      const parent = c.slot?.parent ? byId.get(c.slot.parent) : null;
      const runMember = !!parent?.meta?.runs?.some((r) => r.child && c.id.startsWith(r.child));
      hits.push({ tag, shape, ownSize, ownWeight, ancestorSize: ancestorSizes(c), runMember, id: c.id });
    }
    if (!hits.length) continue;
    const stem = f.replace(/\.json$/, '');
    const key = idx.get(stem) ?? null;
    const cells = {};
    for (const p of ['web', 'ios', 'android']) { const x = key && run.sections[sec].cells.get(`${key}|${p}`); cells[p] = x ? `${x.pass ? 'P' : 'f'} ${x.ssim}` : '—'; }
    rows.push({ sec, stem, key, hits, cells });
  }
}

// Classes the brief cares about.
const unsized = (h) => !h.ownSize;
const headingRows = rows.filter((r) => r.hits.some((h) => HEADING.has(h.tag) && unsized(h)));
const byShape = {};
for (const r of headingRows) for (const h of r.hits.filter((h) => HEADING.has(h.tag) && unsized(h))) {
  byShape[h.shape] ??= new Set(); byShape[h.shape].add(r.key ?? r.stem);
}
console.log(`run ${runId}: ${rows.length} documents carry h1–h6/sup/sub; ${headingRows.length} carry an author-UNSIZED heading`);
for (const [s, set] of Object.entries(byShape)) console.log(`  unsized heading shape ${s}: ${set.size} documents`);
const pad = (s, n) => String(s).padEnd(n);
console.log('\nunsized-heading documents (shape list | ancestorSize | cells web / ios / android):');
for (const r of headingRows) {
  const hs = r.hits.filter((h) => HEADING.has(h.tag) && unsized(h));
  const desc = hs.map((h) => `${h.tag}:${h.shape}${h.ownWeight ? '+w' : ''}${h.ancestorSize ? '+anc' : ''}`).join(',');
  console.log(pad((r.key ?? r.stem).replace(/^css\//, ''), 74), pad(desc, 34), `${r.cells.web} / ${r.cells.ios} / ${r.cells.android}`);
}
const supRows = rows.filter((r) => r.hits.some((h) => SUPSUB.has(h.tag) && unsized(h)));
console.log(`\nunsized sup/sub documents: ${supRows.length}`);
for (const r of supRows) {
  const hs = r.hits.filter((h) => SUPSUB.has(h.tag) && unsized(h));
  const desc = hs.map((h) => `${h.tag}:${h.shape}${h.runMember ? '+member' : ''}`).join(',');
  console.log(pad((r.key ?? r.stem).replace(/^css\//, ''), 74), pad(desc.slice(0, 34), 34), `${r.cells.web} / ${r.cells.ios} / ${r.cells.android}`);
}
if (jsonOut) fs.writeFileSync(jsonOut, JSON.stringify({ run: runId, rows }, null, 1));
