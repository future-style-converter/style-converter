#!/usr/bin/env node
// Wave 52 · lane L8 (vertical-wedges) — the corpus CENSUS this lane's blast
// radius is derived from. Walks every per-test IR document of the run of
// record (wave51-fix, 1435 docs across 30 sections) and counts the carriers
// of each mechanism the lane changes, re-deriving the brief's numbers
// (vertical-wedges.md §5) with an independent script rather than trusting
// them. Output: census.json beside this file (committed), plus a summary.
//
// Grep shapes (the brief's, plus the two the lane's ACTUAL gates widen):
//   M-A/M-B  "u":"CH" anywhere in a property payload; FontFamily presence.
//            + per ch-carrier: the RESOLVED writing-mode / text-orientation
//              (own, then the slot.parent chain) — the upright-vertical ch
//              carriers are the only components whose ch basis M-B moves.
//   M-C      a text leaf whose resolved mode is VERTICAL_* and whose
//            resolved text-orientation is UPRIGHT; plus MIXED/absent leaves
//            carrying a code point > U+2E7F (the Vertical_Orientation=U core).
//   M-E      meta.sourceTag col/colgroup carrying a Width property.
//   M-G      the brief counted `input[type=range]` with a 0px Width/Height;
//            the gate this lane ships fires for EVERY widget tag, so the
//            census also counts that wider shape (the honest blast radius).
// Pass letters come from each section's manifest (`browserRef.diffs[p-ref]
// .wptPass`): W/I/A = passing, w/i/a = failing, '-' = not scored.

import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

// Repo root = four levels above tools/titan/results/<lane>/.
const here = path.dirname(fileURLToPath(import.meta.url));
const root = path.resolve(here, '../../../..');
const runDir = path.join(root, 'tools/titan/runs/wave51-fix/sections');

// Widget tags the UA-widget mount resolves (UAWidgetsResolve.kindFor).
const WIDGET_TAGS = new Set(['input', 'button', 'select', 'textarea', 'meter', 'progress']);
const VERTICAL = new Set(['VERTICAL_RL', 'VERTICAL_LR', 'SIDEWAYS_RL', 'SIDEWAYS_LR']);
const UPRIGHT_VERTICAL = new Set(['VERTICAL_RL', 'VERTICAL_LR']);
// The ch slots each native measures (see chFacelessSizing below).
const SIZING = new Set(['Width', 'Height', 'InlineSize', 'BlockSize', 'MinWidth', 'MaxWidth', 'MinHeight',
  'MaxHeight', 'MinInlineSize', 'MaxInlineSize', 'MinBlockSize', 'MaxBlockSize']);
const SPACING = new Set(['PaddingTop', 'PaddingRight', 'PaddingBottom', 'PaddingLeft',
  'MarginTop', 'MarginRight', 'MarginBottom', 'MarginLeft']);

/** Keyword payloads ride as a bare string; anything else is not a keyword. */
const keyword = (data) => (typeof data === 'string' ? data.toUpperCase().replace(/-/g, '_') : null);
/** The first property of a type on a component, or undefined. */
const prop = (c, type) => (c.properties || []).find((p) => p.type === type);
/** Absolute px of a length payload, or null (relative / keyword / absent). */
const px = (data) => (data && typeof data === 'object' && typeof data.px === 'number' ? data.px : null);

/** Manifest key for a per-test-ir filename: wpt__a__b__c.json → css/a/b/c.html */
function manifestKey(file) {
  const stem = path.basename(file, '.json').replace(/^wpt__/, '');
  return 'css/' + stem.split('__').join('/') + '.html';
}

/** Per-platform pass letters from the section manifest. */
function passLetters(manifest, key) {
  const r = manifest?.wpt?.results?.[key];
  const diffs = r?.browserRef?.diffs || {};
  const letter = (p, up, low) => {
    const d = diffs[`${p}-ref`];
    if (!d || d.wptPass === undefined) return '-';
    return d.wptPass ? up : low;
  };
  return letter('web', 'W', 'w') + letter('ios', 'I', 'i') + letter('android', 'A', 'a');
}

/** Does the component or any ancestor carry a FontFamily (any payload)? */
function hasFamily(c, byId) {
  let cur = c;
  for (let hops = 0; cur && hops < 64; hops++) {
    if (prop(cur, 'FontFamily')) return true;
    cur = cur.slot?.parent ? byId.get(cur.slot.parent) : null;
  }
  return false;
}

/** Resolve an inherited keyword property up the slot.parent chain. */
function resolved(c, type, byId, fallback) {
  let cur = c;
  // Bounded walk: the wire is a tree, but never trust a cycle.
  for (let hops = 0; cur && hops < 64; hops++) {
    const k = keyword(prop(cur, type)?.data);
    if (k) return k;
    cur = cur.slot?.parent ? byId.get(cur.slot.parent) : null;
  }
  return fallback;
}

/** Does the text carry a code point above U+2E7F (the brief's CJK proxy)? */
const hasWideCodePoint = (text) => [...(text || '')].some((ch) => ch.codePointAt(0) > 0x2e7f);

// The runtime's EXACT upright ranges (VerticalTextFlow.isUprightOrientation,
// byte-parallel on both natives) — the faithful twin of the proxy above.
const UPRIGHT_RANGES = [[0x1100, 0x11ff], [0x2e80, 0x303e], [0x3041, 0x33ff], [0x3400, 0x4dbf],
  [0x4e00, 0x9fff], [0xa000, 0xa4cf], [0xa960, 0xa97f], [0xac00, 0xd7ff], [0xf900, 0xfaff],
  [0xfe10, 0xfe19], [0xfe30, 0xfe4f], [0xff01, 0xff60], [0xffe0, 0xffe6], [0x20000, 0x2fffd], [0x30000, 0x3fffd]];
const isUprightCp = (cp) => UPRIGHT_RANGES.some(([a, b]) => cp >= a && cp <= b);
/** VerticalTextFlow.classifyMixed: UPRIGHT only when every non-whitespace
 *  code point is upright-class and at least one exists. */
const mixedRunIsUpright = (text) => {
  const cps = [...(text || '')].map((ch) => ch.codePointAt(0)).filter((cp) => !/\s/u.test(String.fromCodePoint(cp)));
  return cps.length > 0 && cps.every(isUprightCp);
};
/** The runtime's text-orientation read: OWN declaration only — neither
 *  native's inherited set (Compose INHERITED_PROPERTY_TYPES, iOS
 *  InheritedText.inheritedTypes) carries TextOrientation. */
const ownTo = (c) => keyword(prop(c, 'TextOrientation')?.data) || 'MIXED';

const census = {
  family: 'vertical-wedges', lane: 'L8', run: 'wave51-fix', docs: 0, sections: 0,
  chDocs: { count: 0, noFontFamily: 0, noFontFamilyDocs: [] },
  chCarriers: { total: 0, uprightVertical: 0, uprightVerticalDocs: [], otherVertical: 0, horizontal: 0 },
  // Runtime-faithful M-B population: inherited WritingMode, OWN TextOrientation.
  chRuntimeUprightVertical: { carriers: 0, docs: [] },
  // M-A per carrier: no FontFamily on the carrier or any ancestor.
  chFacelessCarriers: { carriers: 0, docs: [] },
  // …split by WHICH slot carries the ch, because the two natives measure
  // different slots: Compose buildSpacingContext measures ch only for the
  // SIZING slots (usesChUnit(width, height, inline/block, min/max…)); iOS
  // StyleBuilder also for padding + margin (ChUnitMetrics.usesCh list).
  chFacelessSizing: { carriers: 0, docs: [] },
  chFacelessSpacingOnly: { carriers: 0, docs: [] },
  // Runtime-faithful M-C population: the leaves VerticalUprightGate admits.
  runtimeUprightLeaves: { count: 0, docs: [] },
  uprightVerticalTextLeaves: { count: 0, docs: [] },
  mixedCjkVerticalTextLeaves: { count: 0, docs: [] },
  asciiMixedVerticalTextLeaves: { count: 0, docs: [] },
  colWithWidth: { count: 0, docs: [] },
  // Runtime-faithful M-E population: TableBoxTree.columnChains harvests
  // UA-only boxes only (no declared Display) — the rowsOf drop rule.
  colWithWidthUaOnly: { count: 0, docs: [] },
  rangeZeroBox: { count: 0, docs: [] },
  anyWidgetZeroBox: { count: 0, docs: [] },
};

const sections = fs.readdirSync(runDir).filter((s) => fs.existsSync(path.join(runDir, s, 'per-test-ir')));
census.sections = sections.length;
for (const section of sections) {
  const manifest = JSON.parse(fs.readFileSync(path.join(runDir, section, 'manifest.json'), 'utf8'));
  const irDir = path.join(runDir, section, 'per-test-ir');
  for (const file of fs.readdirSync(irDir).filter((f) => f.endsWith('.json'))) {
    census.docs++;
    const doc = JSON.parse(fs.readFileSync(path.join(irDir, file), 'utf8'));
    const key = manifestKey(file);
    const tag = `${key.replace(/^css\//, '').replace(/\.html$/, '')} [${passLetters(manifest, key)}]`;
    const comps = doc.components || [];
    const byId = new Map(comps.map((c) => [c.id, c]));
    const raw = JSON.stringify(comps);
    // ── M-A / M-B: ch carriers ────────────────────────────────────────
    if (raw.includes('"u":"CH"')) {
      census.chDocs.count++;
      if (!comps.some((c) => prop(c, 'FontFamily'))) {
        census.chDocs.noFontFamily++;
        census.chDocs.noFontFamilyDocs.push(tag);
      }
      let uprightHere = false, runtimeUprightHere = false, facelessHere = false;
      let facelessSizingHere = false, facelessSpacingHere = false;
      for (const c of comps) {
        if (!JSON.stringify(c.properties || []).includes('"u":"CH"')) continue;
        census.chCarriers.total++;
        const wm = resolved(c, 'WritingMode', byId, 'HORIZONTAL_TB');
        const to = resolved(c, 'TextOrientation', byId, 'MIXED');
        if (UPRIGHT_VERTICAL.has(wm) && to === 'UPRIGHT') { census.chCarriers.uprightVertical++; uprightHere = true; }
        else if (VERTICAL.has(wm)) census.chCarriers.otherVertical++;
        else census.chCarriers.horizontal++;
        // What VerticalInlineAxis.chAdvanceIsVertical actually sees.
        if (UPRIGHT_VERTICAL.has(wm) && ownTo(c) === 'UPRIGHT') { census.chRuntimeUprightVertical.carriers++; runtimeUprightHere = true; }
        // M-A: FontFamily resolved up the chain (it inherits on both natives).
        if (!hasFamily(c, byId)) {
          census.chFacelessCarriers.carriers++; facelessHere = true;
          const chTypes = (c.properties || []).filter((q) => JSON.stringify(q.data ?? null).includes('"u":"CH"')).map((q) => q.type);
          if (chTypes.some((t) => SIZING.has(t))) { census.chFacelessSizing.carriers++; facelessSizingHere = true; }
          else if (chTypes.some((t) => SPACING.has(t))) { census.chFacelessSpacingOnly.carriers++; facelessSpacingHere = true; }
        }
      }
      if (uprightHere) census.chCarriers.uprightVerticalDocs.push(tag);
      if (runtimeUprightHere) census.chRuntimeUprightVertical.docs.push(tag);
      if (facelessHere) census.chFacelessCarriers.docs.push(tag);
      if (facelessSizingHere) census.chFacelessSizing.docs.push(tag);
      if (facelessSpacingHere) census.chFacelessSpacingOnly.docs.push(tag);
    }
    // ── M-C: vertical text leaves by orientation class ───────────────
    let upright = false, mixedCjk = false, asciiMixed = false;
    for (const c of comps) {
      if (!c.text || !c.text.trim()) continue;
      const wm = resolved(c, 'WritingMode', byId, 'HORIZONTAL_TB');
      if (!UPRIGHT_VERTICAL.has(wm)) continue;
      const to = resolved(c, 'TextOrientation', byId, 'MIXED');
      if (to === 'UPRIGHT') upright = true;
      else if (to === 'MIXED') { if (hasWideCodePoint(c.text)) mixedCjk = true; else asciiMixed = true; }
    }
    // Runtime-faithful: inherited WritingMode, OWN TextOrientation, the
    // exact classifyMixed rule — the population whose decline M-C replaces.
    if (comps.some((c) => c.text && c.text.trim() && UPRIGHT_VERTICAL.has(resolved(c, 'WritingMode', byId, 'HORIZONTAL_TB'))
        && (ownTo(c) === 'UPRIGHT' || (ownTo(c) === 'MIXED' && mixedRunIsUpright(c.text))))) {
      census.runtimeUprightLeaves.count++; census.runtimeUprightLeaves.docs.push(tag);
    }
    if (upright) { census.uprightVerticalTextLeaves.count++; census.uprightVerticalTextLeaves.docs.push(tag); }
    if (mixedCjk) { census.mixedCjkVerticalTextLeaves.count++; census.mixedCjkVerticalTextLeaves.docs.push(tag); }
    if (asciiMixed) { census.asciiMixedVerticalTextLeaves.count++; census.asciiMixedVerticalTextLeaves.docs.push(tag); }
    // ── M-E: column boxes carrying a Width ───────────────────────────
    if (comps.some((c) => ['col', 'colgroup'].includes((c.meta?.sourceTag || '').toLowerCase()) && prop(c, 'Width'))) {
      census.colWithWidth.count++; census.colWithWidth.docs.push(tag);
    }
    if (comps.some((c) => ['col', 'colgroup'].includes((c.meta?.sourceTag || '').toLowerCase()) && prop(c, 'Width') && !prop(c, 'Display'))) {
      census.colWithWidthUaOnly.count++; census.colWithWidthUaOnly.docs.push(tag);
    }
    // ── M-G: zero used box on a widget ───────────────────────────────
    const zero = (c) => px(prop(c, 'Width')?.data) === 0 || px(prop(c, 'Height')?.data) === 0;
    const widgets = comps.filter((c) => WIDGET_TAGS.has((c.meta?.sourceTag || '').toLowerCase()));
    if (widgets.some((c) => c.meta?.attrs?.type === 'range' && zero(c))) { census.rangeZeroBox.count++; census.rangeZeroBox.docs.push(tag); }
    if (widgets.some(zero)) { census.anyWidgetZeroBox.count++; census.anyWidgetZeroBox.docs.push(tag); }
  }
}

fs.writeFileSync(path.join(here, 'census.json'), JSON.stringify(census, null, 2) + '\n');
const { chDocs, chCarriers, uprightVerticalTextLeaves: u, mixedCjkVerticalTextLeaves: m, asciiMixedVerticalTextLeaves: a, colWithWidth, rangeZeroBox, anyWidgetZeroBox } = census;
console.log(`docs ${census.docs} sections ${census.sections}`);
console.log(`ch docs ${chDocs.count} (no FontFamily ${chDocs.noFontFamily}); ch carriers ${chCarriers.total}: upright-vertical ${chCarriers.uprightVertical} in ${chCarriers.uprightVerticalDocs.length} docs, other vertical ${chCarriers.otherVertical}, horizontal ${chCarriers.horizontal}`);
console.log(`M-C upright leaves ${u.count} docs; mixed-CJK ${m.count} docs; ascii-under-mixed (stay rotated) ${a.count} docs`);
console.log(`runtime-faithful: M-B upright ch carriers ${census.chRuntimeUprightVertical.carriers} in ${census.chRuntimeUprightVertical.docs.length} docs; M-A face-less ch carriers ${census.chFacelessCarriers.carriers} in ${census.chFacelessCarriers.docs.length} docs (sizing slot — both natives — ${census.chFacelessSizing.docs.length} docs; spacing-only — iOS only — ${census.chFacelessSpacingOnly.docs.length} docs); M-C gate-admitted upright leaves ${census.runtimeUprightLeaves.count} docs`);
console.log(`M-E col/colgroup with Width ${colWidth(colWithWidth)} (UA-only, what the runtime harvests: ${census.colWithWidthUaOnly.count} docs); M-G range zero-box ${rangeZeroBox.count}; ANY widget zero-box ${anyWidgetZeroBox.count}`);
function colWidth(x) { return `${x.count} docs`; }
