#!/usr/bin/env node
//
// gen-fidelity.mjs — deterministic FIDELITY FIXTURE ENGINE.
//
// Generates fixtures/fidelity/: multi-property ("combo") components and
// multi-component ("tree") fixtures that today's one-property-per-component
// suite under fixtures/properties/ does not exercise. Cross-property and
// layout-inheritance bugs hide exactly there — the July campaign found 16
// bugs with single-property fixtures alone.
//
// Outputs (all byte-reproducible for a given SEED + input fixture corpus):
//   fixtures/fidelity/<category>.combos.json   3–6 properties per component:
//                                              intra-category clusters +
//                                              cross-category standard recipes
//   fixtures/fidelity/trees/*.json             parent→child(→grandchild) trees
//                                              (children map-in envelope per
//                                              schema/spec/03-children.md)
//   fixtures/fidelity/manifest.json            file inventory for wave runs
//   fixtures/fidelity/PROVENANCE.md            generation record (tool + seed)
//
// Provenance lives in manifest.json / PROVENANCE.md — NOT inside the fixture
// envelopes: the CSS-side input tolerates only {properties, selectors, media,
// children, _text, _role} per component (CssParsing.parseComponent) and the
// IR wire schema rejects unknown envelope keys (schema/spec/01-envelope.md),
// so a "_generated" marker key is off the table by design.
//
// Usage:
//   node tools/visual/gen-fidelity.mjs          # (re)writes fixtures/fidelity/
//
// Determinism contract (pinned by tools/visual/gen-fidelity.test.mjs):
//   • seeded PRNG only (mulberry32, SEED constant below) — no Date, no Math.random
//   • every directory listing is sorted; every map iterated in sorted key order
//   • rerunning the generator produces byte-identical files

import { readdirSync, readFileSync, statSync, writeFileSync, mkdirSync } from 'node:fs';
import { join, resolve, dirname, relative, basename } from 'node:path';
import { fileURLToPath } from 'node:url';

const __dirname = dirname(fileURLToPath(import.meta.url));
export const REPO = resolve(__dirname, '..', '..');

// ── Seed ─────────────────────────────────────────────────────────────────
// Fixed generation seed. Changing it changes every PRNG-derived byte in
// fixtures/fidelity/, which invalidates committed baselines — treat a seed
// bump like a fixture rewrite, not a refactor.
export const SEED = 20260708;

// ── PRNG ─────────────────────────────────────────────────────────────────
/** mulberry32 — small, fast, deterministic 32-bit PRNG. */
export function mulberry32(seed) {
  let a = seed >>> 0;
  return function () {
    a |= 0; a = (a + 0x6d2b79f5) | 0;
    let t = Math.imul(a ^ (a >>> 15), 1 | a);
    t = (t + Math.imul(t ^ (t >>> 7), 61 | t)) ^ t;
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
  };
}

/** FNV-1a string hash — used to derive a per-file PRNG stream from SEED so
 *  each output file's bytes are independent of generation order. */
function fnv1a(str) {
  let h = 0x811c9dc5;
  for (let i = 0; i < str.length; i++) {
    h ^= str.charCodeAt(i);
    h = Math.imul(h, 0x01000193);
  }
  return h >>> 0;
}

/** Per-file PRNG: stream seeded by SEED ⊕ hash(fileKey). */
function rngFor(fileKey) {
  return mulberry32((SEED ^ fnv1a(fileKey)) >>> 0);
}

/** Deterministic pick from a (sorted) array. */
function pick(rng, arr) {
  return arr[Math.floor(rng() * arr.length) % arr.length];
}

// ── 1. HARVEST — catalogue of property → concrete values exercised today ──

const FIXTURE_PROPS_ROOT = join(REPO, 'fixtures', 'properties');
const IR_MODELS_ROOT = join(REPO, 'converter/src/main/kotlin/app/irmodels/properties');

/** PascalCase → kebab-case, handling runs of capitals (ZIndex → z-index). */
function kebab(name) {
  return name
    .replace(/([a-z0-9])([A-Z])/g, '$1-$2')
    .replace(/([A-Z])([A-Z][a-z])/g, '$1-$2')
    .toLowerCase();
}

/**
 * css property name → canonical category, derived from the converter's IR
 * catalogue (irmodels/properties/<category>/…/<Name>Property.kt) — the same
 * naming contract coverage-audit.mjs relies on. Vendor-prefixed properties
 * (WebkitLineClamp → -webkit-line-clamp) get their leading dash restored.
 */
export function buildCategoryMap() {
  const map = new Map();
  const walk = (dir, cat) => {
    for (const entry of readdirSync(dir).sort()) {
      const full = join(dir, entry);
      if (statSync(full).isDirectory()) { walk(full, cat); continue; }
      if (!entry.endsWith('Property.kt')) continue;
      if (entry.includes('Serializer') || entry.includes('ValueTypes') || entry === 'IRProperty.kt') continue;
      const css = kebab(entry.replace(/Property\.kt$/, ''));
      const name = css.startsWith('webkit-') ? `-${css}` : css;
      if (!map.has(name)) map.set(name, cat);
    }
  };
  for (const cat of readdirSync(IR_MODELS_ROOT).sort()) {
    const full = join(IR_MODELS_ROOT, cat);
    if (statSync(full).isDirectory()) walk(full, cat);
  }
  return map;
}

// Shorthands are expanded by the converter (parsing/css/properties/shorthands/)
// and have no irmodels file; category attribution for manifest reporting only.
export const SHORTHAND_CATEGORY = new Map(Object.entries({
  background: 'background', border: 'borders', 'border-radius': 'borders',
  flex: 'layout', 'flex-flow': 'layout', gap: 'spacing', grid: 'layout',
  'grid-column': 'layout', 'grid-row': 'layout', inset: 'layout',
  margin: 'spacing', outline: 'borders', overflow: 'scrolling',
  padding: 'spacing', 'scroll-margin': 'scrolling', 'scroll-padding': 'scrolling',
}));

/**
 * Walk every fixtures/properties/<category>/*.json and collect, per CSS
 * property, the sorted set of concrete value strings exercised today
 * (component properties at every nesting depth; selectors/media are skipped —
 * combos target base declarations).
 */
export function harvest() {
  const values = new Map(); // css name → Set<string>
  const collect = (component) => {
    for (const [prop, value] of Object.entries(component.properties ?? {})) {
      if (!values.has(prop)) values.set(prop, new Set());
      values.get(prop).add(String(value));
    }
    for (const child of Object.values(component.children ?? {})) collect(child);
  };
  for (const cat of readdirSync(FIXTURE_PROPS_ROOT).sort()) {
    const dir = join(FIXTURE_PROPS_ROOT, cat);
    if (!statSync(dir).isDirectory()) continue;
    for (const file of readdirSync(dir).sort()) {
      if (!file.endsWith('.json')) continue;
      const doc = JSON.parse(readFileSync(join(dir, file), 'utf8'));
      for (const comp of Object.values(doc.components ?? {})) collect(comp);
    }
  }
  // Freeze into sorted arrays for deterministic PRNG picks.
  const out = new Map();
  for (const key of [...values.keys()].sort()) out.set(key, [...values.get(key)].sort());
  return out;
}

// ── 2. Value filters for combo payloads ──────────────────────────────────
// A combo component must stay visible and context-free; drop values that
// would blank the capture, escape the gallery flow, or need unavailable
// runtime context. Single-property fixtures still cover all of these.

const GLOBAL_KEYWORDS = new Set(['inherit', 'initial', 'unset', 'revert', 'revert-layer']);

function usableInCombo(prop, value) {
  const v = value.trim();
  if (v.length === 0 || v.length > 200) return false;
  if (GLOBAL_KEYWORDS.has(v)) return false;                     // noise in multi-prop context
  if (/\b(var|url|attr|counter|counters|env)\(/.test(v)) return false; // needs external context
  if (prop === 'display' && (v === 'none' || v === 'contents')) return false; // removes the box
  if (prop === 'visibility' && v !== 'visible') return false;   // blanks the capture
  if (prop === 'content-visibility' && v === 'hidden') return false;
  if (prop === 'opacity' && /^0(\.0+)?%?$/.test(v)) return false;
  if (prop === 'position' && ['absolute', 'fixed', 'sticky'].includes(v)) return false; // escapes flow
  if (/\bscale\(\s*0(\.0+)?\s*\)/.test(v)) return false;        // collapses to zero size
  return true;
}

// ── 3. Shared style pools (curated, spec-safe, deterministically picked) ──

const PALETTE = ['#e74c3c', '#3498db', '#2ecc71', '#f39c12', '#9b59b6', '#1abc9c', '#e67e22', '#16a085'];
const BG_FG_PAIRS = [
  ['#ecf0f1', '#111111'], ['#2c3e50', '#ffffff'],
  ['#fdf2e9', '#7e3b09'], ['#1f2937', '#f9fafb'],
];
const WIDTHS = ['140px', '180px', '200px', '240px'];
const HEIGHTS = ['48px', '64px', '80px', '96px'];
const POOLS = {
  padding: ['8px', '12px', '16px 24px', '20px'],
  margin: ['4px', '8px', '12px 6px', '16px'],
  border: ['1px solid #2c3e50', '2px dashed #e74c3c', '3px solid #16a085', '4px double #8e44ad'],
  'border-radius': ['6px', '12px', '24px 8px', '50%'],
  'box-shadow': ['0 4px 8px rgba(0,0,0,0.25)', '2px 2px 6px rgba(0,0,0,0.35)', '0 0 0 3px #3498db', 'inset 0 2px 6px rgba(0,0,0,0.4)'],
  opacity: ['0.85', '0.7', '0.55'],
  transform: ['rotate(6deg)', 'scale(1.1)', 'translate(6px, 4px)', 'skewX(8deg)'],
  'font-family': ['monospace', 'serif', 'sans-serif'],
  'font-size': ['14px', '18px', '22px'],
  'line-height': ['1.4', '1.8', '2'],
  'text-align': ['left', 'center', 'right'],
};

// ── 4. COMBOS — multiple properties per component, per category ──────────

const MAX_COMPONENTS_PER_FILE = 25; // capture-time cap per combos file
const CLUSTER_BUDGET = MAX_COMPONENTS_PER_FILE - 3; // 3 slots reserved for standards

/** PascalCase a category id for component names (e.g. borders → Borders). */
function pascal(cat) {
  return cat.replace(/(^|-)([a-z])/g, (_, __, c) => c.toUpperCase());
}

/** PascalCase a css property for name suffixes (border-top-width → BorderTopWidth). */
function pascalProp(prop) {
  return prop.replace(/^-/, '').replace(/(^|-)([a-z0-9])/g, (_, __, c) => c.toUpperCase());
}

/** Add width/height/background support props so the component captures
 *  visibly — only where the payload didn't already claim the key. */
function addSupport(props, rng) {
  if (!('width' in props) && !('inline-size' in props)) props['width'] = pick(rng, WIDTHS);
  if (!('height' in props) && !('block-size' in props) && !('aspect-ratio' in props)) props['height'] = pick(rng, HEIGHTS);
  if (!('background-color' in props) && !('background' in props)) props['background-color'] = pick(rng, PALETTE);
  return props;
}

/**
 * Build one <category>.combos.json document. Returns null when the category
 * has fewer than 3 combo-usable harvested properties (nothing to combine).
 */
function buildCombosForCategory(cat, catalogue, rng) {
  // (a) intra-category clusters: kebab-sorted property list means related
  // longhands (border-top-*, font-*, …) sit adjacent, so fixed-size chunking
  // naturally yields "all border props together / font cluster together".
  const props = [...catalogue.keys()]; // already sorted
  if (props.length < 3) return null;
  const chunk = Math.min(6, Math.max(3, Math.ceil(props.length / CLUSTER_BUDGET)));
  const clusters = [];
  for (let i = 0; i < props.length; i += chunk) clusters.push(props.slice(i, i + chunk));
  // A trailing chunk below the 3-property floor merges into its predecessor.
  if (clusters.length > 1 && clusters[clusters.length - 1].length < 3) {
    clusters[clusters.length - 2].push(...clusters.pop());
  }
  // Respect the file cap even in pathological cases (chunk is derived from
  // the budget, so this trim should never fire — belt and braces).
  clusters.length = Math.min(clusters.length, CLUSTER_BUDGET);

  const components = {};
  const used = new Set();
  for (let i = 0; i < clusters.length; i++) {
    const payload = {};
    for (const p of clusters[i]) {
      payload[p] = pick(rng, catalogue.get(p)); // one harvested variant per property
      used.add(p);
    }
    const name = `${pascal(cat)}_C${String(i + 1).padStart(2, '0')}_${pascalProp(clusters[i][0])}`;
    components[name] = { properties: addSupport(payload, rng) };
  }

  // (b) cross-category standards — fixed recipes seasoned with up to two of
  // this category's own properties (when the recipe doesn't claim the key),
  // so every standards trio still exercises category-specific interplay.
  const [bg, fg] = pick(rng, BG_FG_PAIRS);
  const season = (recipe) => {
    let added = 0;
    for (const p of props) {
      if (added === 2) break;
      if (p in recipe) continue;
      recipe[p] = pick(rng, catalogue.get(p));
      added++;
    }
    return recipe;
  };
  components[`${pascal(cat)}_BoxModel`] = {
    properties: season({
      width: pick(rng, WIDTHS), height: pick(rng, HEIGHTS),
      padding: pick(rng, POOLS.padding), margin: pick(rng, POOLS.margin),
      border: pick(rng, POOLS.border), 'background-color': pick(rng, PALETTE),
    }),
  };
  components[`${pascal(cat)}_TextBlock`] = {
    properties: season({
      color: fg, 'font-family': pick(rng, POOLS['font-family']),
      'font-size': pick(rng, POOLS['font-size']), 'line-height': pick(rng, POOLS['line-height']),
      'text-align': pick(rng, POOLS['text-align']), padding: pick(rng, POOLS.padding),
      width: pick(rng, WIDTHS), 'background-color': bg,
    }),
  };
  components[`${pascal(cat)}_Decorated`] = {
    properties: season({
      'background-color': pick(rng, PALETTE), 'border-radius': pick(rng, POOLS['border-radius']),
      'box-shadow': pick(rng, POOLS['box-shadow']), opacity: pick(rng, POOLS.opacity),
      transform: pick(rng, POOLS.transform), padding: pick(rng, POOLS.padding),
    }),
  };

  return { doc: { components }, properties: used };
}

// ── 5. TREES — multiple connected components (children map-in envelope) ──
// Hand-designed templates (structure is the test surface); PRNG only colors
// the leaves so the layout/inheritance semantics stay reviewable.

/** Leaf helper: fixed box with a palette background + extra declarations. */
function leaf(rng, w, h, extra = {}) {
  const properties = {};
  if (w !== null) properties['width'] = w;
  if (h !== null) properties['height'] = h;
  properties['background-color'] = pick(rng, PALETTE);
  Object.assign(properties, extra);
  return { properties };
}

/** Container helper: display/flow props + dark backdrop for child contrast. */
function box(props, children) {
  const node = { properties: props };
  if (children) node.children = children;
  return node;
}

function buildTreeFlexRow(rng) {
  const parent = (extra = {}) => ({
    width: '320px', display: 'flex', 'flex-direction': 'row',
    gap: '8px', padding: '8px', 'background-color': '#1f2937', ...extra,
  });
  return {
    // align-self overrides the parent's align-items per child (stretch child
    // omits height so the stretch is observable).
    FR_AlignSelf: box(parent({ height: '120px', 'align-items': 'center' }), {
      a: leaf(rng, '50px', '30px', { 'align-self': 'flex-start' }),
      b: leaf(rng, '50px', '30px', { 'align-self': 'center' }),
      c: leaf(rng, '50px', '30px', { 'align-self': 'flex-end' }),
      d: leaf(rng, '50px', null, { 'align-self': 'stretch' }),
    }),
    // order rearranges source order: visual order must be b, c, a.
    FR_Order: box(parent({ height: '80px' }), {
      a: leaf(rng, '60px', '40px', { order: '3' }),
      b: leaf(rng, '60px', '40px', { order: '1' }),
      c: leaf(rng, '60px', '40px', { order: '2' }),
    }),
    // flex-grow distributes the free main-axis space 0:1:2.
    FR_GrowBasis: box(parent({ height: '80px' }), {
      a: leaf(rng, null, '40px', { 'flex-grow': '0', 'flex-basis': '40px' }),
      b: leaf(rng, null, '40px', { 'flex-grow': '1', 'flex-basis': '40px' }),
      c: leaf(rng, null, '40px', { 'flex-grow': '2', 'flex-basis': '40px' }),
    }),
    // over-constrained row: flex-shrink 0/1/3 must shrink unevenly.
    FR_ShrinkBasis: box(parent({ width: '240px', height: '80px' }), {
      a: leaf(rng, null, '40px', { 'flex-shrink': '0', 'flex-basis': '100px' }),
      b: leaf(rng, null, '40px', { 'flex-shrink': '1', 'flex-basis': '100px' }),
      c: leaf(rng, null, '40px', { 'flex-shrink': '3', 'flex-basis': '100px' }),
    }),
  };
}

function buildTreeFlexColumn(rng) {
  const parent = (extra = {}) => ({
    width: '200px', display: 'flex', 'flex-direction': 'column',
    gap: '6px', padding: '6px', 'background-color': '#1f2937', ...extra,
  });
  return {
    // cross-axis (horizontal) align-self in a column container.
    FC_AlignSelf: box(parent({ height: '200px', 'align-items': 'center' }), {
      a: leaf(rng, '80px', '24px', { 'align-self': 'flex-start' }),
      b: leaf(rng, '80px', '24px', { 'align-self': 'center' }),
      c: leaf(rng, '80px', '24px', { 'align-self': 'flex-end' }),
      d: leaf(rng, null, '24px', { 'align-self': 'stretch' }),
    }),
    // visual order must be c, a, b (top → bottom).
    FC_Order: box(parent({ height: '140px' }), {
      a: leaf(rng, '80px', '30px', { order: '2' }),
      b: leaf(rng, '80px', '30px', { order: '3' }),
      c: leaf(rng, '80px', '30px', { order: '1' }),
    }),
    // vertical flex-grow distribution 0:1:2 inside a fixed-height column.
    FC_GrowBasis: box(parent({ height: '220px' }), {
      a: leaf(rng, '100px', null, { 'flex-grow': '0', 'flex-basis': '30px' }),
      b: leaf(rng, '100px', null, { 'flex-grow': '1', 'flex-basis': '30px' }),
      c: leaf(rng, '100px', null, { 'flex-grow': '2', 'flex-basis': '30px' }),
    }),
    // space-between pushes first/last children to the container edges.
    FC_SpaceBetween: box(parent({ height: '200px', 'justify-content': 'space-between' }), {
      a: leaf(rng, '80px', '30px'),
      b: leaf(rng, '80px', '30px'),
      c: leaf(rng, '80px', '30px'),
    }),
  };
}

function buildTreeGrid2Col(rng) {
  const parent = (extra = {}) => ({
    width: '320px', display: 'grid', 'grid-template-columns': '1fr 1fr',
    gap: '8px', padding: '8px', 'background-color': '#1f2937', ...extra,
  });
  return {
    // explicit placement longhands: a spans both columns; d is pinned to
    // column 2 of an explicit later row.
    G2_Placement: box(parent(), {
      a: leaf(rng, null, '30px', { 'grid-column-start': '1', 'grid-column-end': '3' }),
      b: leaf(rng, null, '40px'),
      c: leaf(rng, null, '40px'),
      d: leaf(rng, null, '30px', { 'grid-column-start': '2', 'grid-column-end': '3', 'grid-row-start': '3', 'grid-row-end': '4' }),
    }),
    // per-item inline-axis alignment inside 1fr tracks (stretch omits width).
    G2_JustifySelf: box(parent(), {
      a: leaf(rng, '60px', '30px', { 'justify-self': 'start' }),
      b: leaf(rng, '60px', '30px', { 'justify-self': 'center' }),
      c: leaf(rng, '60px', '30px', { 'justify-self': 'end' }),
      d: leaf(rng, null, '30px', { 'justify-self': 'stretch' }),
    }),
    // per-item block-axis alignment inside fixed 60px rows (stretch omits height).
    G2_AlignSelf: box(parent({ 'grid-template-rows': '60px 60px' }), {
      a: leaf(rng, '60px', '24px', { 'align-self': 'start' }),
      b: leaf(rng, '60px', '24px', { 'align-self': 'center' }),
      c: leaf(rng, '60px', '24px', { 'align-self': 'end' }),
      d: leaf(rng, '60px', null, { 'align-self': 'stretch' }),
    }),
    // asymmetric tracks (80px / 1fr) + a full-width spanning child.
    G2_MixedTracks: box(parent({ 'grid-template-columns': '80px 1fr' }), {
      a: leaf(rng, null, '30px'),
      b: leaf(rng, null, '30px'),
      c: leaf(rng, null, '30px', { 'grid-column-start': '1', 'grid-column-end': '3' }),
      d: leaf(rng, null, '30px', { 'grid-column-start': '2', 'grid-column-end': '3' }),
    }),
  };
}

function buildTreeBlockFlow(rng) {
  return {
    // margin-left/right auto centers a fixed-width child in a block parent.
    B_MarginAuto: box(
      { width: '300px', padding: '10px', 'background-color': '#ecf0f1' },
      { centered: leaf(rng, '120px', '40px', { 'margin-left': 'auto', 'margin-right': 'auto' }) },
    ),
    // vertical stacking with percentage widths resolved against the parent.
    B_Stack: box(
      { width: '280px', padding: '10px', 'background-color': '#ecf0f1' },
      {
        full: leaf(rng, '100%', '24px', { 'margin-bottom': '8px' }),
        threequarter: leaf(rng, '75%', '24px', { 'margin-bottom': '8px' }),
        half: leaf(rng, '50%', '24px'),
      },
    ),
    // padding accumulates through two nesting levels (3-level chain).
    B_NestedPadding: box(
      { width: '260px', padding: '16px', 'background-color': '#2c3e50' },
      {
        inner: box(
          { padding: '12px', 'background-color': '#7f8c8d' },
          { core: leaf(rng, null, '30px') },
        ),
      },
    ),
    // absolutely-positioned child anchored to a relative parent, plus a
    // static sibling that must ignore the abspos box entirely.
    B_RelativeAnchor: box(
      { width: '280px', height: '100px', position: 'relative', 'background-color': '#ecf0f1' },
      {
        floating: leaf(rng, '60px', '30px', { position: 'absolute', top: '10px', left: '20px' }),
        inflow: leaf(rng, '100px', '30px'),
      },
    ),
  };
}

function buildTreeInheritanceColor(rng) {
  const [bg, fg] = pick(rng, BG_FG_PAIRS);
  return {
    // child "plain" must render the parent's color; "redeclared" overrides it.
    // Background is pinned light: both text colors here are hardcoded darks,
    // so a PRNG-dark backdrop would sink the contrast the fixture relies on.
    IH_Color: box(
      { width: '260px', padding: '10px', color: '#c0392b', 'font-size': '16px', 'background-color': '#fdf2e9' },
      {
        plain: { properties: {}, _text: 'inherited color' },
        redeclared: { properties: { color: '#1a5276' }, _text: 'redeclared color' },
      },
    ),
    // font-size inherits into "plain"; "redeclared" resets to 12px.
    IH_FontSize: box(
      { width: '260px', padding: '10px', color: fg, 'font-size': '22px', 'background-color': bg },
      {
        plain: { properties: {}, _text: 'inherited size' },
        redeclared: { properties: { 'font-size': '12px' }, _text: 'redeclared size' },
      },
    ),
    // line-height 2 inherits into wrapped text; sibling resets to 1.1.
    IH_LineHeight: box(
      { width: '220px', padding: '10px', color: fg, 'font-size': '14px', 'line-height': '2', 'background-color': bg },
      {
        plain: { properties: {}, _text: 'wrapped text inherits double line height from the parent' },
        redeclared: { properties: { 'line-height': '1.1' }, _text: 'wrapped text redeclares a tight line height locally' },
      },
    ),
    // color must survive TWO hops: parent → undecorated mid → text leaf.
    // Same light-background pin as IH_Color: the leaf text is hardcoded green.
    IH_TwoLevel: box(
      { width: '260px', padding: '10px', color: '#1e8449', 'font-size': '15px', 'background-color': '#eafaf1' },
      {
        mid: box(
          { padding: '8px', 'background-color': '#d5f5e3' },
          { deep: { properties: {}, _text: 'inherited through two levels' } },
        ),
      },
    ),
  };
}

function buildTreeInheritanceTypography(rng) {
  const [bg, fg] = pick(rng, BG_FG_PAIRS);
  const parent = (extra) => ({
    width: '240px', padding: '10px', color: fg, 'font-size': '15px',
    'background-color': bg, ...extra,
  });
  return {
    // font-family inherits into "plain"; sibling flips to serif.
    IT_Family: box(parent({ 'font-family': 'monospace' }), {
      plain: { properties: {}, _text: 'inherited monospace' },
      redeclared: { properties: { 'font-family': 'serif' }, _text: 'redeclared serif' },
    }),
    // font-weight 700 inherits; sibling redeclares 300.
    IT_Weight: box(parent({ 'font-weight': '700' }), {
      plain: { properties: {}, _text: 'inherited bold' },
      redeclared: { properties: { 'font-weight': '300' }, _text: 'redeclared light' },
    }),
    // letter-spacing + text-transform both inherit; sibling resets spacing.
    IT_SpacingTransform: box(parent({ 'letter-spacing': '2px', 'text-transform': 'uppercase' }), {
      plain: { properties: {}, _text: 'inherited spacing' },
      redeclared: { properties: { 'letter-spacing': 'normal' }, _text: 'reset spacing' },
    }),
    // text-align inherits into block children; sibling re-aligns left.
    IT_Align: box(parent({ 'text-align': 'right' }), {
      plain: { properties: {}, _text: 'inherited right align' },
      redeclared: { properties: { 'text-align': 'left' }, _text: 'redeclared left align' },
    }),
  };
}

function buildTreeNested3Level(rng) {
  const row = (extra, children) => box({
    display: 'flex', 'flex-direction': 'row', gap: '6px', padding: '6px',
    'background-color': '#34495e', ...extra,
  }, children);
  return {
    // column → two rows → four leaves: 3 levels of flex nesting; the second
    // row uses order:-1 so it must render ABOVE the first.
    N3_ColumnOfRows: box(
      { width: '280px', display: 'flex', 'flex-direction': 'column', gap: '8px', padding: '8px', 'background-color': '#1f2937' },
      {
        row1: row({}, { a: leaf(rng, '50px', '30px'), b: leaf(rng, '50px', '30px') }),
        row2: row({ order: '-1' }, { c: leaf(rng, '50px', '30px'), d: leaf(rng, '50px', '30px') }),
      },
    ),
    // grid cells that are themselves flex rows (grid → flex → leaf).
    N3_GridOfFlex: box(
      { width: '320px', display: 'grid', 'grid-template-columns': '1fr 1fr', gap: '8px', padding: '8px', 'background-color': '#1f2937' },
      {
        cellA: row({}, { a: leaf(rng, null, '30px', { 'flex-grow': '1' }), b: leaf(rng, null, '30px', { 'flex-grow': '1' }) }),
        cellB: row({}, { c: leaf(rng, '40px', '30px'), d: leaf(rng, null, '30px', { 'flex-grow': '1' }) }),
      },
    ),
    // block wrapper → grid → leaves (padding must offset the whole grid).
    N3_BlockGrid: box(
      { width: '280px', padding: '14px', 'background-color': '#ecf0f1' },
      {
        grid: box(
          { display: 'grid', 'grid-template-columns': '1fr 1fr', gap: '6px', 'background-color': '#bdc3c7', padding: '6px' },
          { a: leaf(rng, null, '36px'), b: leaf(rng, null, '36px') },
        ),
      },
    ),
  };
}

function buildTreeMixedDirection(rng) {
  const flex = (dir, extra, children) => box({
    display: 'flex', 'flex-direction': dir, gap: '6px', padding: '6px',
    'background-color': '#34495e', ...extra,
  }, children);
  return {
    // row of two columns — cross-direction nesting both ways in one tree.
    MD_RowOfColumns: flex('row', { width: '300px', 'background-color': '#1f2937', 'align-items': 'flex-start' }, {
      colA: flex('column', {}, { a: leaf(rng, '50px', '24px'), b: leaf(rng, '50px', '24px') }),
      colB: flex('column', {}, { c: leaf(rng, '50px', '24px'), d: leaf(rng, '50px', '36px') }),
    }),
    // column whose second entry is a row (leaf + nested row mix).
    MD_ColumnWithRow: flex('column', { width: '220px', 'background-color': '#1f2937' }, {
      solo: leaf(rng, '80px', '28px'),
      innerRow: flex('row', {}, { a: leaf(rng, '50px', '28px'), b: leaf(rng, '50px', '28px') }),
    }),
    // asymmetric gaps: outer row gap 12 vs inner column gap 2 must differ.
    MD_GapContrast: flex('row', { width: '300px', gap: '12px', 'background-color': '#1f2937', 'align-items': 'flex-start' }, {
      tight: flex('column', { gap: '2px' }, { a: leaf(rng, '60px', '20px'), b: leaf(rng, '60px', '20px') }),
      solo: leaf(rng, '60px', '42px'),
    }),
  };
}

const TREE_BUILDERS = [
  ['trees/flex-row.json', buildTreeFlexRow],
  ['trees/flex-column.json', buildTreeFlexColumn],
  ['trees/grid-2col.json', buildTreeGrid2Col],
  ['trees/block-flow.json', buildTreeBlockFlow],
  ['trees/inheritance-color.json', buildTreeInheritanceColor],
  ['trees/inheritance-typography.json', buildTreeInheritanceTypography],
  ['trees/nested-3level.json', buildTreeNested3Level],
  ['trees/mixed-direction.json', buildTreeMixedDirection],
];

// ── 6. Assembly ──────────────────────────────────────────────────────────

/** Count every node in a components map, recursing through children maps. */
function countNodes(components) {
  let n = 0;
  for (const comp of Object.values(components)) {
    n += 1;
    if (comp.children) n += countNodes(comp.children);
  }
  return n;
}

/** Collect every distinct declared property name in a components map. */
function collectProps(components, acc = new Set()) {
  for (const comp of Object.values(components)) {
    for (const p of Object.keys(comp.properties ?? {})) acc.add(p);
    if (comp.children) collectProps(comp.children, acc);
  }
  return acc;
}

/** Serialize a fixture document with the repo's 2-space + trailing-newline style. */
function toJson(doc) {
  return JSON.stringify(doc, null, 2) + '\n';
}

/**
 * Generate every fidelity artifact in memory.
 * Returns an array of { relPath, content } sorted by relPath — pure function
 * of the fixture corpus, the IR catalogue tree, and SEED.
 */
export function generate() {
  const categoryMap = buildCategoryMap();
  const harvested = harvest();

  // Per-category catalogue of combo-usable longhands: only properties whose
  // canonical category is known (shorthands stay recipe-only) and that keep
  // at least one usable value after the visibility/context filters.
  const perCategory = new Map(); // cat → Map(prop → [values])
  for (const [prop, values] of harvested) {
    const cat = categoryMap.get(prop);
    if (!cat) continue;
    const usable = values.filter((v) => usableInCombo(prop, v));
    if (usable.length === 0) continue;
    if (!perCategory.has(cat)) perCategory.set(cat, new Map());
    perCategory.get(cat).set(prop, usable);
  }

  const files = []; // { relPath, content }
  const manifestFiles = [];

  // Combos — one file per category with ≥3 usable properties.
  for (const cat of [...perCategory.keys()].sort()) {
    const relPath = `fixtures/fidelity/${cat}.combos.json`;
    const built = buildCombosForCategory(cat, perCategory.get(cat), rngFor(relPath));
    if (!built) continue;
    const content = toJson(built.doc);
    files.push({ relPath, content });
    manifestFiles.push({
      path: relPath,
      kind: 'combos',
      category: cat,
      components: Object.keys(built.doc.components).length,
      nodes: countNodes(built.doc.components),
      bytes: Buffer.byteLength(content),
      properties: [...collectProps(built.doc.components)].sort(),
    });
  }

  // Trees — fixed template set, PRNG-colored.
  for (const [name, builder] of TREE_BUILDERS) {
    const relPath = `fixtures/fidelity/${name}`;
    const components = builder(rngFor(relPath));
    const content = toJson({ components });
    const props = [...collectProps(components)].sort();
    // Category attribution for wave-run filtering: longhand map first,
    // shorthand table second; anything else is reported as-is under "other".
    const cats = new Set();
    for (const p of props) cats.add(categoryMap.get(p) ?? SHORTHAND_CATEGORY.get(p) ?? 'other');
    files.push({ relPath, content });
    manifestFiles.push({
      path: relPath,
      kind: 'tree',
      categories: [...cats].sort(),
      components: Object.keys(components).length,
      nodes: countNodes(components),
      bytes: Buffer.byteLength(content),
      properties: props,
    });
  }

  // Manifest — the wave-run iteration surface. Sorted stably: combos by
  // category, then trees in template order (already appended that way).
  const manifest = {
    generator: 'tools/visual/gen-fidelity.mjs',
    seed: SEED,
    files: manifestFiles,
    totals: {
      files: manifestFiles.length,
      components: manifestFiles.reduce((s, f) => s + f.components, 0),
      nodes: manifestFiles.reduce((s, f) => s + f.nodes, 0),
    },
  };
  files.push({ relPath: 'fixtures/fidelity/manifest.json', content: toJson(manifest) });

  // PROVENANCE.md — the human-readable generation record (the marker the
  // strict envelope forbids in-file lives here instead).
  const prov = [];
  prov.push('# fixtures/fidelity/ — PROVENANCE');
  prov.push('');
  prov.push('**Generated. Do not hand-edit.** Every file in this directory (including');
  prov.push('`manifest.json` and this document) is emitted by a deterministic generator:');
  prov.push('');
  prov.push('```bash');
  prov.push('node tools/visual/gen-fidelity.mjs');
  prov.push('```');
  prov.push('');
  prov.push(`- **Tool**: \`tools/visual/gen-fidelity.mjs\``);
  prov.push(`- **Seed**: \`${SEED}\` (mulberry32; the constant lives in the generator)`);
  prov.push('- **Inputs**: the harvested value variants of `fixtures/properties/<category>/*.json`');
  prov.push('  and the IR property catalogue under `converter/src/main/kotlin/app/irmodels/properties/`.');
  prov.push('- **Determinism pin**: `node --test tools/visual/gen-fidelity.test.mjs` fails if a');
  prov.push('  regeneration is not byte-identical to the committed files.');
  prov.push('');
  prov.push('Why no in-file marker: the CSS-side component envelope only tolerates');
  prov.push('`properties/selectors/media/children/_text/_role` (CssParsing.parseComponent) and');
  prov.push('the IR wire schema rejects unknown envelope keys (schema/spec/01-envelope.md), so a');
  prov.push('`"_generated"` key inside fixtures would be a contract violation. Provenance lives');
  prov.push('here and in `manifest.json` instead.');
  prov.push('');
  prov.push('## Inventory');
  prov.push('');
  prov.push('| file | kind | components | nodes |');
  prov.push('|---|---|---:|---:|');
  for (const f of manifestFiles) {
    prov.push(`| \`${f.path.replace('fixtures/fidelity/', '')}\` | ${f.kind} | ${f.components} | ${f.nodes} |`);
  }
  prov.push(`| **total** | | **${manifest.totals.components}** | **${manifest.totals.nodes}** |`);
  prov.push('');
  files.push({ relPath: 'fixtures/fidelity/PROVENANCE.md', content: prov.join('\n') + '\n' });

  files.sort((a, b) => (a.relPath < b.relPath ? -1 : a.relPath > b.relPath ? 1 : 0));
  return files;
}

// ── 7. CLI ───────────────────────────────────────────────────────────────

function main() {
  const files = generate();
  for (const { relPath, content } of files) {
    const abs = join(REPO, relPath);
    mkdirSync(dirname(abs), { recursive: true });
    writeFileSync(abs, content);
    console.log(`wrote ${relPath} (${Buffer.byteLength(content)} bytes)`);
  }
  console.log(`\n${files.length} files written to fixtures/fidelity/ (seed ${SEED})`);
}

// Import-safe: the test imports generate() without triggering writes.
import { pathToFileURL } from 'node:url';
if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  main();
}
