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
//   fixtures/fidelity/pairwise/pairs-NN.json   category-PAIR components: all 66
//                                              unordered pairs of the 12
//                                              visually-strongest categories,
//                                              4 components per pair, 2–3
//                                              harvested declarations per side
//   fixtures/fidelity/trees/*.json             parent→child(→grandchild) trees
//                                              (children map-in envelope per
//                                              schema/spec/03-children.md)
//   fixtures/fidelity/placement/*.json         child-side placement claims for
//                                              the IR v2 slot/placement
//                                              contract: grid areas/lines/spans,
//                                              order, self-alignment, z-index,
//                                              auto-flow interleave, dangling
//                                              area claims
//   fixtures/fidelity/tokens/*.json            dynamic-value suite: custom-
//                                              property definitions (variables
//                                              envelope key), var() refs +
//                                              fallback chains across the slot
//                                              chain, calc()/relative-unit
//                                              arithmetic — visible-on-failure
//                                              by construction
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

/** Deterministic sample WITHOUT replacement: up to k distinct entries of arr,
 *  in PRNG draw order. Never throws on short pools — callers that need a
 *  guaranteed minimum assert the pool size up front (see generate()). */
function sampleDistinct(rng, arr, k) {
  const pool = [...arr];                       // copy — never mutate the catalogue
  const out = [];
  const n = Math.min(k, pool.length);          // short pools yield what they have
  for (let i = 0; i < n; i++) {
    const idx = Math.floor(rng() * pool.length) % pool.length;
    out.push(pool.splice(idx, 1)[0]);          // remove so re-draws are impossible
  }
  return out;
}

/** Palette cycler: returns a nullary function yielding PALETTE colors from a
 *  PRNG-seeded offset, cycling WITHOUT repetition for up to 8 draws. Placement
 *  fixtures need distinct sibling colors — a z-index or order bug is invisible
 *  if two overlapping/reordered siblings happen to share a color. */
function paletteCycler(rng) {
  let i = Math.floor(rng() * PALETTE.length) % PALETTE.length;
  return () => PALETTE[i++ % PALETTE.length];
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

// ── 5. PAIRWISE — cross-CATEGORY combos (category-pair components) ──────
// Single-category combos cannot see interaction bugs BETWEEN categories
// (transform×border-radius, background-clip×box-shadow, typography×sizing…).
// Each pairwise component carries 2–3 harvested properties from category A
// plus 2–3 from category B; all 66 unordered pairs of the 12 visually
// strongest categories are covered, 4 components per pair, sharded into 6
// capture-friendly files of 44 components (11 pairs × 4).

// The 12 categories with the strongest pixel footprint (kept sorted so the
// pair enumeration below is deterministic and lexicographic).
export const PAIRWISE_CATEGORIES = [
  'background', 'borders', 'color', 'effects', 'images', 'layout',
  'lists', 'shapes', 'sizing', 'spacing', 'transforms', 'typography',
];
export const PAIRWISE_PER_PAIR = 4;       // components generated per category pair
const PAIRWISE_PAIRS_PER_SHARD = 11;      // 66 pairs / 11 = 6 shards × 44 components

/** All C(12,2)=66 unordered category pairs, lexicographic by construction. */
export function pairwisePairs() {
  const pairs = [];
  for (let i = 0; i < PAIRWISE_CATEGORIES.length; i++) {
    for (let j = i + 1; j < PAIRWISE_CATEGORIES.length; j++) {
      pairs.push([PAIRWISE_CATEGORIES[i], PAIRWISE_CATEGORIES[j]]);
    }
  }
  return pairs;
}

/**
 * Build one pairwise shard document covering the given category pairs.
 * Per component: sample 2–3 distinct properties from each side's usable
 * catalogue (one harvested value each), then add the standard support props
 * (width/height/background) where the payload didn't claim the key. Property
 * sets never collide across the two sides — buildCategoryMap assigns every
 * longhand exactly one canonical category.
 */
function buildPairwiseShard(pairs, perCategory, rng) {
  const components = {};
  for (const [a, b] of pairs) {
    for (let i = 0; i < PAIRWISE_PER_PAIR; i++) {
      const payload = {};
      for (const cat of [a, b]) {
        const catalogue = perCategory.get(cat);       // presence asserted in generate()
        const k = 2 + Math.floor(rng() * 2);          // 2 or 3 properties this side
        for (const p of sampleDistinct(rng, [...catalogue.keys()], k)) {
          payload[p] = pick(rng, catalogue.get(p));   // one harvested variant per property
        }
      }
      // Name encodes the pair (PW_<A>_<B>_<NN>) — the regeneration test parses
      // it back to verify each component really carries both categories.
      const name = `PW_${pascal(a)}_${pascal(b)}_${String(i + 1).padStart(2, '0')}`;
      components[name] = { properties: addSupport(payload, rng) };
    }
  }
  return { components };
}

// ── 6. TREES — multiple connected components (children map-in envelope) ──
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

// ── 7. PLACEMENT — child-side placement claims (IR v2 slot/placement) ───
// The v2 contract stress suite: trees whose CHILDREN carry explicit placement
// claims (item-scoped properties: grid-area, grid line numbers/spans, order,
// align-self/justify-self, z-index + absolute insets). Fixtures stay CSS-side
// nested — children maps per schema/spec/03-children.md — the converter
// flattens them into the v2 flat list with slot refs; the claims are just
// properties on the child, which is exactly how the runtimes must route them
// (child-carried parent-data, never parent introspection). Hand-designed
// structure, PRNG only picks colors (via paletteCycler so overlapping /
// reordered siblings never share a color — a stacking or ordering bug must
// change pixels).

/** Per-builder cell factory: fixed box + cycling palette color + claims. */
function cellFactory(rng) {
  const nextColor = paletteCycler(rng);       // distinct colors for ≤8 siblings
  return (w, h, extra = {}) => {
    const properties = {};
    if (w !== null) properties['width'] = w;    // null width ⇒ stretch/track-sized
    if (h !== null) properties['height'] = h;   // null height ⇒ stretch/row-sized
    properties['background-color'] = nextColor();
    Object.assign(properties, extra);           // the placement claims themselves
    return { properties };
  };
}

/** Grid container helper: dark backdrop + gap/padding, caller sets tracks. */
function gridBox(extra, children) {
  return box({
    width: '300px', display: 'grid', gap: '6px', padding: '6px',
    'background-color': '#1f2937', ...extra,
  }, children);
}

/** Flex container helper: dark backdrop + gap/padding, caller sets axis. */
function flexBox(extra, children) {
  return box({
    width: '320px', display: 'flex', gap: '6px', padding: '6px',
    'background-color': '#1f2937', ...extra,
  }, children);
}

function buildPlacementGridAreas(rng) {
  const cell = cellFactory(rng);
  return {
    // The design-doc card (ir-v2 §2.1): media spans both rows via its area;
    // children stretch to their named cells (no explicit width/height).
    PL_AreasCard: gridBox({
      'grid-template-columns': '80px 1fr',
      'grid-template-rows': '40px 60px',
      'grid-template-areas': '"media title" "media body"',
    }, {
      media: cell(null, null, { 'grid-area': 'media' }),
      title: cell(null, null, { 'grid-area': 'title' }),
      body: cell(null, null, { 'grid-area': 'body' }),
    }),
    // Child keys deliberately ≠ area names: catches any renderer that still
    // matches children to areas BY NAME (the old iOS templateAreasGrid hack)
    // instead of reading the child's grid-area claim.
    PL_AreasDecoupled: gridBox({
      'grid-template-columns': '1fr 1fr 1fr',
      'grid-template-rows': '40px 40px',
      'grid-template-areas': '"head head head" "side main main"',
    }, {
      box1: cell(null, null, { 'grid-area': 'main' }),
      box2: cell(null, null, { 'grid-area': 'head' }),
      box3: cell(null, null, { 'grid-area': 'side' }),
    }),
    // Dangling claim: "ghost" names an area the container never defines →
    // per CSS Grid §8.4 the claim resolves to auto placement (implicit row).
    // The claim/resolution algorithm must fall back, not crash or blank.
    PL_AreasDangling: gridBox({
      'grid-template-columns': '1fr 1fr',
      'grid-template-rows': '40px 40px',
      'grid-template-areas': '"a a" "b c"',
    }, {
      a: cell(null, null, { 'grid-area': 'a' }),
      b: cell(null, null, { 'grid-area': 'b' }),
      c: cell(null, null, { 'grid-area': 'c' }),
      ghost: cell(null, '30px', { 'grid-area': 'ghost' }),
    }),
  };
}

function buildPlacementGridLines(rng) {
  const cell = cellFactory(rng);
  return {
    // Numeric line claims: a/b tile row 1, c is pinned to the middle of row 2.
    PL_LineNumbers: gridBox({ 'grid-template-columns': '1fr 1fr 1fr 1fr', 'grid-auto-rows': '40px' }, {
      a: cell(null, null, { 'grid-column-start': '1', 'grid-column-end': '3' }),
      b: cell(null, null, { 'grid-column-start': '3', 'grid-column-end': '5' }),
      c: cell(null, null, { 'grid-column-start': '2', 'grid-column-end': '4', 'grid-row-start': '2', 'grid-row-end': '3' }),
    }),
    // Span + negative-line claims: span 2 from auto position, -1 = last line.
    PL_LineSpans: gridBox({ 'grid-template-columns': '1fr 1fr 1fr', 'grid-auto-rows': '36px' }, {
      a: cell(null, null, { 'grid-column-start': 'span 2' }),
      b: cell(null, null, {}),
      c: cell(null, null, { 'grid-column-start': '1', 'grid-column-end': '-1' }),
      d: cell(null, null, { 'grid-column-start': '2', 'grid-row-start': '3', 'grid-row-end': '5' }),
    }),
    // grid-area line syntax (2- and 4-value slash forms + double span) — the
    // shorthand route to the same longhand claims, per GA_ByLines/GA_Span.
    PL_AreaLineSyntax: gridBox({ 'grid-template-columns': '1fr 1fr 1fr', 'grid-template-rows': '40px 40px 40px' }, {
      a: cell(null, null, { 'grid-area': '1 / 1 / 2 / 3' }),
      b: cell(null, null, { 'grid-area': '2 / 2 / 4 / 4' }),
      c: cell(null, null, { 'grid-area': 'span 2 / span 1' }),
    }),
  };
}

function buildPlacementFlexOrder(rng) {
  const cell = cellFactory(rng);
  return {
    // Full 5-way permutation: source a,b,c,d,e must render as b,d,e,a,c.
    PL_OrderPermutation: flexBox({ height: '52px', 'flex-direction': 'row' }, {
      a: cell('50px', '40px', { order: '4' }),
      b: cell('50px', '40px', { order: '1' }),
      c: cell('50px', '40px', { order: '5' }),
      d: cell('50px', '40px', { order: '2' }),
      e: cell('50px', '40px', { order: '3' }),
    }),
    // Ties + negative: b(-1), d(0), then a and c tie at 1 → source order
    // breaks the tie (flat-array order rule, ir-v2 §1.1). Visual: b,d,a,c.
    PL_OrderTies: flexBox({ height: '52px', 'flex-direction': 'row' }, {
      a: cell('60px', '40px', { order: '1' }),
      b: cell('60px', '40px', { order: '-1' }),
      c: cell('60px', '40px', { order: '1' }),
      d: cell('60px', '40px', { order: '0' }),
    }),
    // Same re-sort on the column axis: source a,b,c renders b,c,a top→bottom.
    PL_OrderColumn: flexBox({ width: '200px', 'flex-direction': 'column' }, {
      a: cell('80px', '30px', { order: '3' }),
      b: cell('80px', '30px', { order: '1' }),
      c: cell('80px', '30px', { order: '2' }),
    }),
  };
}

function buildPlacementSelfAlignment(rng) {
  const cell = cellFactory(rng);
  return {
    // Container policy flex-end; three children override, d takes the policy.
    // Stretch child omits height so the stretch is observable.
    PL_FlexAlignOverride: flexBox({ height: '120px', 'flex-direction': 'row', 'align-items': 'flex-end' }, {
      a: cell('50px', '30px', { 'align-self': 'flex-start' }),
      b: cell('50px', '30px', { 'align-self': 'center' }),
      c: cell('50px', null, { 'align-self': 'stretch' }),
      d: cell('50px', '30px', {}),
    }),
    // Grid inline-axis: container centers items; children re-justify per cell
    // (stretch omits width); d inherits the container's center policy.
    PL_GridJustifyOverride: gridBox({ 'grid-template-columns': '1fr 1fr', 'grid-auto-rows': '50px', 'justify-items': 'center' }, {
      a: cell('60px', '36px', { 'justify-self': 'start' }),
      b: cell('60px', '36px', { 'justify-self': 'end' }),
      c: cell(null, '36px', { 'justify-self': 'stretch' }),
      d: cell('60px', '36px', {}),
    }),
    // Grid block-axis: container policy start; children re-align inside fixed
    // 60px rows (stretch omits height); d inherits start.
    PL_GridAlignOverride: gridBox({ 'grid-template-columns': '1fr 1fr', 'grid-template-rows': '60px 60px', 'align-items': 'start' }, {
      a: cell('60px', '24px', { 'align-self': 'end' }),
      b: cell('60px', '24px', { 'align-self': 'center' }),
      c: cell('60px', null, { 'align-self': 'stretch' }),
      d: cell('60px', '24px', {}),
    }),
  };
}

function buildPlacementZStack(rng) {
  const cell = cellFactory(rng);
  // Relative stage: absolutely-positioned children overlap diagonally so
  // every pairwise stacking decision changes visible pixels.
  const stage = (children) => box(
    { width: '300px', height: '120px', position: 'relative', 'background-color': '#1f2937' },
    children,
  );
  return {
    // z 3/1/2 vs source a,b,c: paint bottom→top must be b, c, a — source
    // order alone would paint a, b, c and fail the comparison.
    PL_ZOverlap: stage({
      a: cell('90px', '70px', { position: 'absolute', top: '10px', left: '20px', 'z-index': '3' }),
      b: cell('90px', '70px', { position: 'absolute', top: '25px', left: '70px', 'z-index': '1' }),
      c: cell('90px', '70px', { position: 'absolute', top: '40px', left: '120px', 'z-index': '2' }),
    }),
    // Equal z ties resolve by source order (a under b); c at z 0 sits under both.
    PL_ZTies: stage({
      a: cell('90px', '70px', { position: 'absolute', top: '10px', left: '30px', 'z-index': '1' }),
      b: cell('90px', '70px', { position: 'absolute', top: '30px', left: '90px', 'z-index': '1' }),
      c: cell('90px', '70px', { position: 'absolute', top: '20px', left: '150px', 'z-index': '0' }),
    }),
    // Negative z paints below the auto sibling but above the stage backdrop
    // (ZI_Negative precedent in fixtures/properties/layout/z-index.json).
    PL_ZNegative: stage({
      a: cell('90px', '70px', { position: 'absolute', top: '15px', left: '40px', 'z-index': '-1' }),
      b: cell('90px', '70px', { position: 'absolute', top: '35px', left: '90px' }),
    }),
  };
}

function buildPlacementMixedClaims(rng) {
  const cell = cellFactory(rng);
  return {
    // Claimed + unclaimed interleave — the auto-placement algorithm's hardest
    // case (CSS Grid §8.5): definite items land first, the auto cursor must
    // flow the unclaimed items AROUND them (u1→r1c1, u2→r2c1, u3→r2c2,
    // u4→r2c3; c2 owns r3c1-c2).
    PL_InterleaveLines: gridBox({ 'grid-template-columns': '1fr 1fr 1fr', 'grid-auto-rows': '40px' }, {
      u1: cell(null, null, {}),
      c1: cell(null, null, { 'grid-column-start': '2', 'grid-column-end': '4', 'grid-row-start': '1' }),
      u2: cell(null, null, {}),
      u3: cell(null, null, {}),
      c2: cell(null, null, { 'grid-column-start': '1', 'grid-column-end': '3', 'grid-row-start': '3' }),
      u4: cell(null, null, {}),
    }),
    // Area claims + auto children: the '.' cells of row 2 are unnamed but
    // explicit — u1/u2 must auto-place into them, not into implicit rows.
    PL_InterleaveAreas: gridBox({
      'grid-template-columns': '1fr 1fr 1fr',
      'grid-template-rows': '40px 40px',
      'grid-template-areas': '"top top top" ". mid ."',
    }, {
      top: cell(null, null, { 'grid-area': 'top' }),
      mid: cell(null, null, { 'grid-area': 'mid' }),
      u1: cell(null, null, {}),
      u2: cell(null, null, {}),
    }),
    // dense backfill: wide (span 2) + tall's row-2 claim punch holes that
    // row-dense auto flow must back-fill with u1/u2/u3.
    PL_DenseBackfill: gridBox({ 'grid-template-columns': '1fr 1fr 1fr', 'grid-auto-rows': '36px', 'grid-auto-flow': 'row dense' }, {
      wide: cell(null, null, { 'grid-column-start': 'span 2' }),
      u1: cell(null, null, {}),
      tall: cell(null, null, { 'grid-column-start': '2', 'grid-column-end': '4', 'grid-row-start': '2' }),
      u2: cell(null, null, {}),
      u3: cell(null, null, {}),
    }),
  };
}

// ── 7b. TOKENS — custom properties, var() resolution, calc/relative units ─
// The wave-6 dynamic-value suite: fixtures whose CORRECT rendering depends on
// the runtime resolving CSS custom properties (component `variables` on the
// IR v2 wire — schema/spec/01-envelope.md), var() fallbacks (css-variables-1
// §2.3), and calc()/relative-unit arithmetic (spec 02 null+original escapes).
// Every component is built so SUCCESS vs FAILURE of resolution changes
// visible pixels: colored tiles whose color/size/padding come from tokens —
// an unresolved var() computes to `unset` (guaranteed-invalid, spec 02
// resolution order), collapsing the tile to transparent/auto against a
// contrasting backdrop. Hand-designed structure (the resolution chain IS the
// test surface); PRNG only picks backdrop/support colors.

function buildTokenTheme(rng) {
  const nextColor = paletteCycler(rng); // support colors for reference tiles
  return {
    // Single-hop resolution: the flex parent DEFINES palette + spacing
    // tokens; each child consumes them through a different property class
    // (background/size, padding, border-radius+color). If resolution fails
    // every tile goes transparent/auto — nothing but the dark backdrop.
    TK_Palette: box({
      width: '300px', display: 'flex', 'flex-direction': 'row', gap: '8px',
      padding: '10px', 'background-color': '#111827',
      '--tile-a': '#e74c3c', '--tile-b': '#3498db', '--tile-c': '#2ecc71',
      '--size': '64px', '--pad': '8px',
    }, {
      // color token drives the paint, size token drives the geometry.
      a: { properties: { width: 'var(--size)', height: 'var(--size)', 'background-color': 'var(--tile-a)' } },
      // padding token shrinks the inner box (content-box observable via
      // the child's own child? no — via total size: padding grows the box).
      b: { properties: { width: 'var(--size)', height: 'var(--size)', 'background-color': 'var(--tile-b)', padding: 'var(--pad)' } },
      // radius token rounds the corners; border color token paints the edge.
      c: { properties: { width: 'var(--size)', height: 'var(--size)', 'background-color': 'var(--tile-c)', 'border-radius': 'var(--pad)' } },
    }),
    // Two-hop chain + SHADOWING: root defines --accent/--gap; mid consumes
    // --gap and SHADOWS --accent with its own definition; the leaf must see
    // the MID definition (nearest slot-parent wins — spec 02 rule 2), the
    // sibling leaf under root sees the root's. Wrong-scope resolution swaps
    // the two colors — a visible diff, not a subtle one.
    TK_TwoLevelShadow: box({
      width: '280px', padding: '10px', 'background-color': '#ecf0f1',
      '--accent': '#c0392b', '--gap': '6px',
    }, {
      rootLeaf: { properties: { width: '80px', height: '30px', 'background-color': 'var(--accent)' } },
      mid: box({ padding: 'var(--gap)', 'background-color': '#bdc3c7', '--accent': '#1a5276' }, {
        midLeaf: { properties: { width: '80px', height: '30px', 'background-color': 'var(--accent)' } },
      }),
    }),
    // Typography tokens across the text channel: color + font-size both
    // token-driven, with a fixed reference tile so a total-failure capture
    // still anchors the layout.
    TK_TextTokens: box({
      width: '260px', padding: '10px', 'background-color': '#fdf2e9',
      '--ink': '#7e3b09', '--type': '18px',
    }, {
      styled: { properties: { color: 'var(--ink)', 'font-size': 'var(--type)' }, _text: 'token ink and size' },
      reference: { properties: { width: '60px', height: '14px', 'background-color': nextColor() } },
    }),
  };
}

function buildTokenFallbacks(rng) {
  const nextColor = paletteCycler(rng);
  return {
    // Missing token vs fallback: `missing` paints nothing (guaranteed-
    // invalid → unset → transparent) while `saved` lands on its fallback —
    // side-by-side tiles make the difference unmissable.
    TK_MissingVsFallback: box({
      width: '280px', display: 'flex', 'flex-direction': 'row', gap: '8px',
      padding: '10px', 'background-color': '#1f2937',
    }, {
      missing: { properties: { width: '70px', height: '48px', 'background-color': 'var(--nope)' } },
      saved: { properties: { width: '70px', height: '48px', 'background-color': 'var(--nope, #e67e22)' } },
      reference: { properties: { width: '70px', height: '48px', 'background-color': nextColor() } },
    }),
    // Nested fallback chains (css-variables-1 §2.3): --mid IS defined, so
    // the chain must stop there (NOT fall through to the literal); the
    // sibling's fully-missing chain must reach the deepest literal.
    TK_NestedFallback: box({
      width: '280px', display: 'flex', 'flex-direction': 'row', gap: '8px',
      padding: '10px', 'background-color': '#111827', '--mid': '#9b59b6',
    }, {
      stopsAtMid: { properties: { width: '80px', height: '44px', 'background-color': 'var(--top, var(--mid, #ffffff))' } },
      fallsThrough: { properties: { width: '80px', height: '44px', 'background-color': 'var(--a, var(--b, #16a085))' } },
    }),
    // Definition beats fallback: --accent exists on the parent, so the
    // child's fallback literal (#000) must LOSE; padding fallback also
    // resolves from the defined token, not the 2px literal.
    TK_DefinitionWins: box({
      width: '240px', padding: '10px', 'background-color': '#ecf0f1',
      '--accent': '#f39c12', '--pad': '12px',
    }, {
      tile: { properties: { width: '90px', height: '40px', 'background-color': 'var(--accent, #000000)', padding: 'var(--pad, 2px)' } },
    }),
  };
}

function buildCalcUnits(rng) {
  const nextColor = paletteCycler(rng);
  return {
    // % of a DEFINITE parent minus absolute px: 300−2·10(padding) = 280
    // content box → child must be 280−40 = 240px wide. A calc failure
    // (auto width) collapses to content width — visibly different.
    CU_PercentMinusPx: box({
      width: '300px', padding: '10px', 'background-color': '#1f2937',
    }, {
      bar: { properties: { width: 'calc(100% - 40px)', height: '32px', 'background-color': nextColor() } },
    }),
    // em against the INHERITED font-size chain: root pins 20px; `direct`
    // resolves 1em=20px; `chained` sits under an undecorated mid so its
    // 2em must still see 20px through two hops (24px vs 44px tall boxes).
    CU_EmInheritance: box({
      width: '260px', padding: '10px', 'font-size': '20px', 'background-color': '#ecf0f1',
    }, {
      direct: { properties: { width: '80px', height: 'calc(1em + 4px)', 'background-color': nextColor() } },
      mid: box({ padding: '4px', 'background-color': '#bdc3c7' }, {
        chained: { properties: { width: '80px', height: 'calc(2em + 4px)', 'background-color': nextColor() } },
      }),
    }),
    // Nested calc + calc-consuming-var: the arithmetic tree must evaluate
    // inside-out ((100%−20px)/2 of a 240px content box = 110px), and the
    // token-driven multiply needs BOTH var resolution and calc math.
    CU_NestedAndVar: box({
      width: '260px', padding: '10px', 'background-color': '#111827', '--u': '12px',
    }, {
      nested: { properties: { width: 'calc(calc(100% - 20px) / 2)', height: '28px', 'background-color': nextColor() } },
      tokenMath: { properties: { width: 'calc(var(--u) * 10)', height: '28px', 'background-color': nextColor() } },
      emMix: { properties: { width: '80px', 'padding-top': 'calc(1em + 2px)', 'background-color': nextColor() } },
    }),
  };
}

// Token template table: path → builder → feature descriptors surfaced in the
// manifest (the coverage vocabulary the regeneration test pins).
const TOKEN_BUILDERS = [
  ['tokens/token-theme.json', buildTokenTheme,
    ['var-definitions', 'var-references', 'slot-chain-resolution', 'shadowing']],
  ['tokens/token-fallbacks.json', buildTokenFallbacks,
    ['missing-var', 'fallback', 'nested-fallback', 'definition-beats-fallback']],
  ['tokens/calc-units.json', buildCalcUnits,
    ['calc-percent-px', 'calc-em-px', 'nested-calc', 'em-inheritance-chain', 'var-in-calc']],
];

// Placement template table: path → builder → claim descriptors surfaced in
// the manifest (the coverage vocabulary the regeneration test pins).
const PLACEMENT_BUILDERS = [
  ['placement/grid-areas.json', buildPlacementGridAreas, ['grid-area-names', 'dangling-claim']],
  ['placement/grid-lines.json', buildPlacementGridLines, ['grid-lines', 'grid-spans', 'negative-lines']],
  ['placement/flex-order.json', buildPlacementFlexOrder, ['order-permutation', 'order-ties']],
  ['placement/self-alignment.json', buildPlacementSelfAlignment, ['align-self-override', 'justify-self-override']],
  ['placement/z-stack.json', buildPlacementZStack, ['z-index-stacking', 'z-index-ties']],
  ['placement/mixed-claims.json', buildPlacementMixedClaims, ['auto-flow-interleave', 'mixed-claims', 'dense-backfill']],
];

// ── 8. Assembly ──────────────────────────────────────────────────────────

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

  // Pairwise — cross-category shards. The 12 target categories must all be
  // present with ≥2 usable properties; a silent thin-out would quietly erode
  // the 66-pair coverage promise, so fail the generation loudly instead.
  for (const cat of PAIRWISE_CATEGORIES) {
    if ((perCategory.get(cat)?.size ?? 0) < 2) {
      throw new Error(`pairwise: category "${cat}" has <2 combo-usable harvested properties`);
    }
  }
  const allPairs = pairwisePairs();
  for (let s = 0; s * PAIRWISE_PAIRS_PER_SHARD < allPairs.length; s++) {
    const shardPairs = allPairs.slice(s * PAIRWISE_PAIRS_PER_SHARD, (s + 1) * PAIRWISE_PAIRS_PER_SHARD);
    const relPath = `fixtures/fidelity/pairwise/pairs-${String(s + 1).padStart(2, '0')}.json`;
    const doc = buildPairwiseShard(shardPairs, perCategory, rngFor(relPath));
    const content = toJson(doc);
    files.push({ relPath, content });
    manifestFiles.push({
      path: relPath,
      kind: 'pairwise',
      // Coverage descriptors: the exact category pairs this shard covers
      // (wave runs and the regeneration test reconstruct the 66-pair promise
      // from the union of these lists).
      categories: [...new Set(shardPairs.flat())].sort(),
      pairs: shardPairs.map(([a, b]) => `${a}+${b}`),
      components: Object.keys(doc.components).length,
      nodes: countNodes(doc.components),
      bytes: Buffer.byteLength(content),
      properties: [...collectProps(doc.components)].sort(),
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

  // Placement — the IR v2 slot/placement contract stress suite: fixed
  // templates (structure IS the test surface), PRNG-colored cells.
  for (const [name, builder, claims] of PLACEMENT_BUILDERS) {
    const relPath = `fixtures/fidelity/${name}`;
    const components = builder(rngFor(relPath));
    const content = toJson({ components });
    const props = [...collectProps(components)].sort();
    // Category attribution mirrors the trees block: longhand map first,
    // shorthand table second, "other" as the honest fallback.
    const cats = new Set();
    for (const p of props) cats.add(categoryMap.get(p) ?? SHORTHAND_CATEGORY.get(p) ?? 'other');
    files.push({ relPath, content });
    manifestFiles.push({
      path: relPath,
      kind: 'placement',
      categories: [...cats].sort(),
      // Coverage descriptors: which claim/resolution scenarios this file
      // exercises (vocabulary pinned by gen-fidelity.test.mjs).
      claims,
      components: Object.keys(components).length,
      nodes: countNodes(components),
      bytes: Buffer.byteLength(content),
      properties: props,
    });
  }

  // Tokens — the wave-6 dynamic-value suite: custom-property definitions,
  // var() references/fallbacks, calc()/relative-unit arithmetic. Fixed
  // templates (the resolution chain IS the test surface), PRNG-colored
  // support tiles only.
  for (const [name, builder, features] of TOKEN_BUILDERS) {
    const relPath = `fixtures/fidelity/${name}`;
    const components = builder(rngFor(relPath));
    const content = toJson({ components });
    // Category attribution intentionally skips `--*` declarations: custom
    // properties belong to no irmodels category (they ride the component
    // `variables` envelope key, not a property triplet).
    const props = [...collectProps(components)].sort();
    const cats = new Set();
    for (const p of props) {
      if (p.startsWith('--')) continue; // token names carry no category
      cats.add(categoryMap.get(p) ?? SHORTHAND_CATEGORY.get(p) ?? 'other');
    }
    files.push({ relPath, content });
    manifestFiles.push({
      path: relPath,
      kind: 'tokens',
      categories: [...cats].sort(),
      // Coverage descriptors: which dynamic-value scenarios this file
      // exercises (vocabulary pinned by gen-fidelity.test.mjs).
      features,
      components: Object.keys(components).length,
      nodes: countNodes(components),
      bytes: Buffer.byteLength(content),
      properties: props,
    });
  }

  // Manifest — the wave-run iteration surface. Sorted stably: combos by
  // category, then pairwise shards, then trees, then placement templates,
  // then token templates (already appended in that order).
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
  prov.push('## Suites');
  prov.push('');
  prov.push('- `<category>.combos.json` — intra-category clusters + cross-category standard');
  prov.push('  recipes, 3–6 declarations per component.');
  prov.push('- `pairwise/pairs-NN.json` — category-PAIR components: all 66 unordered pairs of');
  prov.push('  the 12 visually-strongest categories (background, borders, color, effects,');
  prov.push('  images, layout, lists, shapes, sizing, spacing, transforms, typography),');
  prov.push('  4 components per pair, 2–3 harvested declarations from each side — hunts');
  prov.push('  cross-category interaction bugs single-category combos cannot reach.');
  prov.push('- `trees/*.json` — hand-designed parent→child templates (flex/grid/block flow +');
  prov.push('  inheritance), PRNG-colored leaves.');
  prov.push('- `placement/*.json` — IR v2 slot/placement contract stress: children carrying');
  prov.push('  explicit placement claims (grid-area names, line numbers + spans, order');
  prov.push('  permutations, align-self/justify-self overrides, z-index stacking among');
  prov.push('  absolutely-positioned siblings, mixed claimed+unclaimed auto-flow interleave,');
  prov.push('  and a dangling area claim). Authored CSS-side nested per');
  prov.push('  schema/spec/03-children.md; the converter flattens to the v2 flat+slot form.');
  prov.push('- `tokens/*.json` — dynamic-value suite (wave 6): custom-property definitions');
  prov.push('  (`--name` declarations → the IR v2 component `variables` key), var()');
  prov.push('  references consumed across the slot-parent chain (with shadowing), missing-');
  prov.push('  var vs fallback vs nested-fallback chains, and calc()/relative-unit');
  prov.push('  arithmetic (%−px against definite parents, em against inherited font-size');
  prov.push('  chains, nested calc, calc-consuming-var). Components are built so');
  prov.push('  resolution SUCCESS vs FAILURE changes visible pixels (spec 02');
  prov.push('  custom-properties section).');
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

// ── 9. CLI ───────────────────────────────────────────────────────────────

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
