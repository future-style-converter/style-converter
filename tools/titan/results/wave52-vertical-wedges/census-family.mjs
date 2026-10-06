#!/usr/bin/env node
// Wave 52 · lane L8 (vertical-wedges) FIX PASS — the M-A census by RESOLVED
// FAMILY, the population the skeptic's must-fix turned on. census.mjs split
// ch carriers by "has a FontFamily anywhere up the chain"; the runtimes do
// not gate on that, they gate on what the family RESOLVES to:
//   Compose  ChUnitMetrics.paintsInter — the Inter loader serves ONLY null
//            (no declaration) and InterFontFamily (a stack naming `Inter`);
//            FontFamily.Default (system-ui / fantasy / unavailable names)
//            keeps Typeface.DEFAULT, the face its label paints. The pre-fix
//            lane gate also sent Default to Inter — counted here as the
//            DEFECT population (skeptic: 0 sizing carriers).
//   iOS      ChUnitMetrics.zeroAdvancePx — design `default` (no monospace /
//            serif / rounded flag over ANY list entry) with no document face
//            measures the registered Inter, which is what the label paints
//            for design default (ComponentRenderer `.custom("Inter")`).
// Twins re-implemented from source (CssFontFamilyResolver.resolve(Entry),
// FontFamilyExtractor.classifyGeneric) — no runtime code is imported.
// Output: census-family.json beside this file + a summary on stdout.

import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

// Repo root = four levels above tools/titan/results/<lane>/.
const here = path.dirname(fileURLToPath(import.meta.url));
const root = path.resolve(here, '../../../..');
const runDir = path.join(root, 'tools/titan/runs/wave51-fix/sections');

// Compose measures ch only for these slots (StyleApplier.buildSpacingContext
// usesChUnit list; TableBoxTree.columnWidthPx is a Width).
const COMPOSE_SLOTS = new Set(['Width', 'Height', 'InlineSize', 'BlockSize', 'MinWidth', 'MaxWidth', 'MinHeight',
  'MaxHeight', 'MinInlineSize', 'MaxInlineSize', 'MinBlockSize', 'MaxBlockSize']);
// iOS measures ch for StyleBuilder's usesCh list: physical size + padding + margin.
const IOS_SLOTS = new Set(['Width', 'Height', 'MinWidth', 'MaxWidth', 'MinHeight', 'MaxHeight',
  'PaddingTop', 'PaddingRight', 'PaddingBottom', 'PaddingLeft', 'MarginTop', 'MarginRight', 'MarginBottom', 'MarginLeft']);
// Android's loader accepts these containers only (DocumentFontRegistry gate).
const ANDROID_LOADABLE = new Set(['ttf', 'otf', 'ttc']);

/** First property of a type on a component, or undefined. */
const prop = (c, type) => (c.properties || []).find((p) => p.type === type);
/** The nearest FontFamily list up the slot.parent chain (it inherits on both natives). */
function familyList(c, byId) {
  let cur = c;
  // Bounded walk: the wire is a tree, but never trust a cycle.
  for (let hops = 0; cur && hops < 64; hops++) {
    const p = prop(cur, 'FontFamily');
    if (p) return Array.isArray(p.data) ? p.data.filter((s) => typeof s === 'string') : [String(p.data)];
    cur = cur.slot?.parent ? byId.get(cur.slot.parent) : null;
  }
  return null;
}
const norm = (s) => s.trim().replace(/^["']+|["']+$/g, '').toLowerCase();

/** Twin of CssFontFamilyResolver.resolveEntry → a family tag, or null (walk on). */
function composeEntry(raw, docFamilies) {
  const n = norm(raw);
  if (!n) return null;
  // Document database first (only faces Android can actually load register).
  if (docFamilies.has(n)) return 'doc';
  if (n === 'inter') return 'inter';
  const exact = { serif: 'serif', 'sans-serif': 'sans-serif', monospace: 'monospace', cursive: 'cursive',
    'system-ui': 'default', 'ui-rounded': 'default', 'ui-sans-serif': 'sans-serif', 'ui-serif': 'serif',
    'ui-monospace': 'monospace', fantasy: 'default', math: 'default', emoji: 'default', fangsong: 'default' };
  if (exact[n]) return exact[n];
  // Legacy substring heuristics, same order as the Kotlin `when`.
  if (n.includes('mono')) return 'monospace';
  if (n.includes('serif') && !n.includes('sans')) return 'serif';
  if (n.includes('sans')) return 'sans-serif';
  if (n.includes('cursive')) return 'cursive';
  return null;
}
/** Twin of CssFontFamilyResolver.resolve: 'null' | 'inter' | 'doc' | generic | 'default'. */
function composeResolve(list, docFamilies) {
  if (list === null) return 'null';
  for (const name of list) { const r = composeEntry(name, docFamilies); if (r) return r; }
  // css-fonts-4 §5.2 terminal: a declared-but-unavailable list → FontFamily.Default.
  return list.length === 0 ? 'null' : 'default';
}
/** Twin of the iOS design pick + document-face walk: 'doc' | design name. */
function iosResolve(list, docFamilies) {
  const names = (list || []).map(norm);
  if (names.some((n) => docFamilies.has(n))) return 'doc';
  // FontMod.design(for:) precedence: rounded > monospaced > serif > default.
  if (names.includes('ui-rounded')) return 'rounded';
  if (names.some((n) => n === 'monospace' || n === 'ui-monospace')) return 'monospaced';
  if (names.some((n) => n === 'serif' || n === 'ui-serif')) return 'serif';
  return 'default';
}

const out = {
  lane: 'L8', run: 'wave51-fix', docs: 0, chCarriers: 0,
  // Compose: per resolved family, the ch carriers in a Compose-measured slot.
  composeByFamily: {},
  composeMovers: { carriers: 0, docs: [] },          // null + inter (the fixed gate)
  composeDefectPopulation: { carriers: 0, docs: [] }, // 'default' (the pre-fix gate's extra)
  iosByDesign: {},
  iosMovers: { carriers: 0, docs: [] },              // design default, no document face
  iosMoversDeclaredFamily: { carriers: 0, docs: [] }, // …of which a family IS declared
  corpusDefaultFamilyDocs: [],                       // any FontFamily payload → Default
};
const bump = (o, k) => { o[k] = (o[k] || 0) + 1; };
const push = (bucket, tag) => { bucket.carriers++; if (!bucket.docs.includes(tag)) bucket.docs.push(tag); };

for (const section of fs.readdirSync(runDir)) {
  const irDir = path.join(runDir, section, 'per-test-ir');
  if (!fs.existsSync(irDir)) continue;
  for (const file of fs.readdirSync(irDir).filter((f) => f.endsWith('.json'))) {
    out.docs++;
    const doc = JSON.parse(fs.readFileSync(path.join(irDir, file), 'utf8'));
    const tag = `${section}/${file.replace(/^wpt__[^_]+__/, '').replace(/\.json$/, '').split('__').join('/')}`;
    const faces = doc.fontFaces || [];
    // iOS registers every face CoreText can load (woff included); Android only ttf/otf/ttc.
    const iosDoc = new Set(faces.map((f) => norm(f.family)));
    const androidDoc = new Set(faces.filter((f) => ANDROID_LOADABLE.has(String(f.src).split('.').pop().toLowerCase()))
      .map((f) => norm(f.family)));
    const comps = doc.components || [];
    const byId = new Map(comps.map((c) => [c.id, c]));
    // Any declared family resolving to FontFamily.Default (the product case the fix restores).
    if (comps.some((c) => prop(c, 'FontFamily') && composeResolve(familyList(c, byId), androidDoc) === 'default')) {
      out.corpusDefaultFamilyDocs.push(tag);
    }
    for (const c of comps) {
      const chTypes = (c.properties || []).filter((q) => JSON.stringify(q.data ?? null).includes('"u":"CH"')).map((q) => q.type);
      if (!chTypes.length) continue;
      out.chCarriers++;
      const list = familyList(c, byId);
      if (chTypes.some((t) => COMPOSE_SLOTS.has(t))) {
        const fam = composeResolve(list, androidDoc);
        bump(out.composeByFamily, fam);
        if (fam === 'null' || fam === 'inter') push(out.composeMovers, tag);
        if (fam === 'default') push(out.composeDefectPopulation, tag);
      }
      if (chTypes.some((t) => IOS_SLOTS.has(t))) {
        const design = iosResolve(list, iosDoc);
        bump(out.iosByDesign, design);
        if (design === 'default') {
          push(out.iosMovers, tag);
          if (list !== null) push(out.iosMoversDeclaredFamily, `${tag} ${JSON.stringify(list)}`);
        }
      }
    }
  }
}

fs.writeFileSync(path.join(here, 'census-family.json'), JSON.stringify(out, null, 2) + '\n');
console.log(`docs ${out.docs}; ch carriers ${out.chCarriers}`);
console.log(`Compose (sizing slots) by resolved family: ${JSON.stringify(out.composeByFamily)}`);
console.log(`Compose M-A movers (null+inter): ${out.composeMovers.carriers} carriers in ${out.composeMovers.docs.length} docs; pre-fix defect population (Default): ${out.composeDefectPopulation.carriers} carriers in ${out.composeDefectPopulation.docs.length} docs`);
console.log(`iOS (size+padding+margin) by design: ${JSON.stringify(out.iosByDesign)}`);
console.log(`iOS M-A movers (design default, no doc face): ${out.iosMovers.carriers} carriers in ${out.iosMovers.docs.length} docs; with a declared family: ${out.iosMoversDeclaredFamily.docs.join(' | ') || 'none'}`);
console.log(`corpus docs with a family resolving to FontFamily.Default: ${out.corpusDefaultFamilyDocs.length}`);
