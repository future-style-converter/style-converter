// gen-fidelity.test.mjs — pins the FIDELITY FIXTURE ENGINE's contract.
//
//   node --test tools/visual/gen-fidelity.test.mjs
//
// What is pinned:
//   1. determinism — generate() is byte-stable across calls, and the files
//      committed under fixtures/fidelity/ are byte-identical to a fresh
//      regeneration (i.e. nobody hand-edited generated output).
//   2. envelope shape — every generated fixture parses as JSON and matches
//      the CSS-side authoring envelope the converter reads
//      (CssParsing.parseComponent: properties / children map-in / _text).
//   3. structural budgets — combos files stay ≤25 components with ≥3
//      declarations each; the 8 tree files carry 10–20 nodes and include
//      3-level nesting.
//   4. pairwise coverage — the 6 pairwise shards cover ALL 66 unordered pairs
//      of the 12 visually-strongest categories, 4 components per pair, each
//      component carrying ≥2 properties from each side of its pair.
//   5. placement coverage — the 6 placement files carry child-side placement
//      claims (IR v2 slot/placement contract): grid-area names, line
//      numbers/spans, order permutations, self-alignment overrides, z-index
//      stacking, mixed claimed+unclaimed auto-flow, and a dangling area claim.
//   6. tokens coverage — the 3 token files (wave 6) carry custom-property
//      definitions, var() references across the slot chain (with shadowing),
//      missing-var/fallback/nested-fallback cases, and calc()/relative-unit
//      mixes; the manifest features vocabulary is pinned.
//   7b. motion coverage — the 2 motion files (wave 8) carry the keyframes
//      authoring block (opacity/translate/scale/color/multi-property/3-stop
//      sets, alternate direction, fill-mode both, an unsorted-offset pin)
//      and state-triggered transitions; vocabulary + one-clock 1s durations
//      + pairwise-distinct geometry are pinned.
//   7. dynamic coverage — the 3 dynamic files (wave 7) carry selector state
//      buckets (all 5 runtime-v1 conditions + a layering pin), media width
//      buckets spanning the 390/250 px truth table (match / no-match / flip
//      rows + a layering pin), and dark-mode buckets + light-dark() values;
//      every bucket overrides ≥1 base declaration with a DIFFERENT value
//      (the pixel-contrast guarantee); the features vocabulary is pinned.
//   8. converter round-trip — 14 representative files (3 wave-4 originals +
//      a pairwise shard + 2 placement trees + the 3 wave-6 token files + the
//      3 wave-7 dynamic files + the 2 wave-8 motion files) convert cleanly, the emitted IR validates
//      against schema/ir-v2.schema.json, the output is flat + slot-composed
//      (no nested children survive), authored --* declarations surface 1:1
//      as component `variables` maps, every whole-value var()/calc()
//      declaration survives byte-for-byte (spec 02 preservation contract),
//      and authored selector/media buckets survive to the wire in order
//      (conditions colon-stripped, queries verbatim — spec 01 buckets), and
//      authored keyframes blocks survive TYPED (offsets resolved 0..1 and
//      sorted, declarations as {type,data} envelopes — spec 07 §1.2).
//      Gradle-slow and JDK-21-dependent, so it only runs when
//      GEN_FIDELITY_CONVERT=1 is set (the CI test-tooling job has no JDK —
//      see .github/workflows/ci.yml).

import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync, existsSync, mkdirSync, rmSync } from 'node:fs';
import { join } from 'node:path';
import { execFileSync } from 'node:child_process';

import {
  generate, SEED, REPO,
  buildCategoryMap, PAIRWISE_CATEGORIES, PAIRWISE_PER_PAIR, pairwisePairs,
} from './gen-fidelity.mjs';
// Reuse the conformance machinery (ajv 2020-12 + the IR v2 schema — the
// converter emits v2 by default since the freeze) rather than
// re-compiling a validator here; run.mjs is import-safe (main() is
// guarded behind an argv check).
import { makeValidatorV2, validateDocument } from '../../schema/conformance/run.mjs';

// Generate once; individual tests re-generate where the determinism pin
// itself is the subject.
const files = generate();
const byPath = new Map(files.map((f) => [f.relPath, f.content]));
const fixtureFiles = files.filter(
  (f) => f.relPath.endsWith('.json') && !f.relPath.endsWith('manifest.json'),
);
const manifest = JSON.parse(byPath.get('fixtures/fidelity/manifest.json'));

// ── 1. Determinism ───────────────────────────────────────────────────────

test('generate() is deterministic: two calls produce identical bytes', () => {
  const again = generate();
  assert.equal(again.length, files.length, 'file count changed between calls');
  for (let i = 0; i < files.length; i++) {
    assert.equal(again[i].relPath, files[i].relPath);
    assert.equal(again[i].content, files[i].content, `bytes differ for ${files[i].relPath}`);
  }
});

test('committed fixtures/fidelity/ files are byte-identical to a regeneration', () => {
  for (const { relPath, content } of files) {
    const abs = join(REPO, relPath);
    assert.ok(existsSync(abs), `${relPath} missing on disk — run: node tools/visual/gen-fidelity.mjs`);
    assert.equal(
      readFileSync(abs, 'utf8'),
      content,
      `${relPath} is stale or hand-edited — regenerate with: node tools/visual/gen-fidelity.mjs`,
    );
  }
});

test('seed is pinned in the manifest', () => {
  assert.equal(manifest.seed, SEED);
  assert.equal(manifest.generator, 'tools/visual/gen-fidelity.mjs');
});

// ── 2. Envelope shape ────────────────────────────────────────────────────

// The authoring envelope the converter's parseComponent accepts. We only
// generate a subset (no _role), so the check is strict on exactly what the
// generator is allowed to emit — selectors/media joined in wave 7 (the
// dynamic suite) with their CSS-side bucket shapes validated below.
const ALLOWED_NODE_KEYS = new Set(['properties', 'children', '_text', 'selectors', 'media']);
const PROP_NAME_RE = /^-?[a-z][a-z0-9-]*$/; // css longhand/shorthand names (optional vendor dash)
// Custom-property declarations (css-variables-1 §2): `--` + at least one
// character. The tokens suite declares these alongside normal properties;
// the converter lifts them into the IR v2 `variables` envelope key.
const CUSTOM_PROP_RE = /^--[a-z][a-z0-9-]*$/;
const NODE_NAME_RE = /^[A-Za-z][A-Za-z0-9_]*$/;

function assertComponentNode(node, path) {
  assert.equal(typeof node, 'object', `${path}: node must be an object`);
  for (const key of Object.keys(node)) {
    assert.ok(ALLOWED_NODE_KEYS.has(key), `${path}: unexpected envelope key "${key}"`);
  }
  // properties is always present (may be {} — e.g. inheritance "plain" children).
  assert.equal(typeof node.properties, 'object', `${path}: missing properties map`);
  for (const [prop, value] of Object.entries(node.properties)) {
    assert.ok(
      PROP_NAME_RE.test(prop) || CUSTOM_PROP_RE.test(prop),
      `${path}: bad property name "${prop}"`,
    );
    assert.equal(typeof value, 'string', `${path}.${prop}: value must be a string`);
    assert.ok(value.length > 0, `${path}.${prop}: empty value`);
  }
  if ('_text' in node) {
    assert.equal(typeof node._text, 'string', `${path}: _text must be a string`);
    assert.ok(node._text.length > 0, `${path}: generator never emits empty _text`);
  }
  // Bucket property maps share the base-declaration rules (non-empty
  // string values, css property names).
  const assertBucketProps = (props, where) => {
    assert.equal(typeof props, 'object', `${where}: missing bucket properties map`);
    const entries = Object.entries(props);
    assert.ok(entries.length > 0, `${where}: empty bucket`);
    for (const [prop, value] of entries) {
      assert.ok(PROP_NAME_RE.test(prop), `${where}: bad bucket property name "${prop}"`);
      assert.equal(typeof value, 'string', `${where}.${prop}: value must be a string`);
      assert.ok(value.length > 0, `${where}.${prop}: empty value`);
    }
  };
  if ('selectors' in node) {
    // CSS-side selector bucket shape (CssParsing.parseComponent): array of
    // {selector, properties}; selector KEEPS the leading colon and must be
    // a runtime-v1 condition (spec 06 §2) — the generator never authors
    // conditions the runtimes define as inert.
    assert.ok(Array.isArray(node.selectors) && node.selectors.length > 0, `${path}: selectors must be a non-empty array`);
    for (const [i, bucket] of node.selectors.entries()) {
      assert.deepEqual(Object.keys(bucket).sort(), ['properties', 'selector'], `${path}.selectors[${i}]: bucket keys drifted`);
      assert.match(bucket.selector, /^:(hover|active|focus|disabled|checked)$/, `${path}.selectors[${i}]: non-runtime-v1 condition "${bucket.selector}"`);
      assertBucketProps(bucket.properties, `${path}.selectors[${i}]`);
    }
  }
  if ('media' in node) {
    // CSS-side media bucket shape: array of {query, properties}; query is
    // the raw parenthesised string, restricted to the runtime-v1 grammar
    // (min-width/max-width in px, prefers-color-scheme — spec 06 §4).
    assert.ok(Array.isArray(node.media) && node.media.length > 0, `${path}: media must be a non-empty array`);
    for (const [i, bucket] of node.media.entries()) {
      assert.deepEqual(Object.keys(bucket).sort(), ['properties', 'query'], `${path}.media[${i}]: bucket keys drifted`);
      assert.match(
        bucket.query,
        /^\((min-width: \d+px|max-width: \d+px|prefers-color-scheme: (light|dark))\)$/,
        `${path}.media[${i}]: query outside the runtime-v1 grammar "${bucket.query}"`,
      );
      assertBucketProps(bucket.properties, `${path}.media[${i}]`);
    }
  }
  if ('children' in node) {
    // children map-in rule (schema/spec/03-children.md): a non-empty
    // name→component object, recursively full components.
    assert.equal(typeof node.children, 'object', `${path}: children must be a map`);
    assert.ok(!Array.isArray(node.children), `${path}: children must be map-in, not array`);
    const entries = Object.entries(node.children);
    assert.ok(entries.length > 0, `${path}: children present ⇒ non-empty`);
    for (const [name, child] of entries) {
      assert.match(name, NODE_NAME_RE, `${path}: bad child key "${name}"`);
      assertComponentNode(child, `${path}.children.${name}`);
    }
  }
}

// Keyframe-set names are CSS custom-idents; the generator uses lowercase
// dashed idents. Offsets are the css-animations-1 §4.2 selector forms the
// converter resolves (from/to/percent) — parseKeyframeOffset in CssParsing.
const KEYFRAME_NAME_RE = /^[a-z][a-z0-9-]*$/;
const KEYFRAME_OFFSET_RE = /^(from|to|\d{1,3}(\.\d+)?%)$/;

/** Validate one authored document-level keyframes block (motion suite —
 *  spec 07 §1.1): name → non-empty array of {offset, declarations} stops
 *  whose declarations follow the same rules as base property maps. */
function assertKeyframesBlock(keyframes, path) {
  const sets = Object.entries(keyframes);
  assert.ok(sets.length > 0, `${path}: keyframes present ⇒ non-empty`);
  for (const [name, stops] of sets) {
    assert.match(name, KEYFRAME_NAME_RE, `${path}: bad keyframes name "${name}"`);
    assert.ok(Array.isArray(stops) && stops.length > 0, `${path}.${name}: stops must be a non-empty array`);
    for (const [i, stop] of stops.entries()) {
      assert.deepEqual(Object.keys(stop).sort(), ['declarations', 'offset'], `${path}.${name}[${i}]: stop keys drifted`);
      assert.match(stop.offset, KEYFRAME_OFFSET_RE, `${path}.${name}[${i}]: bad offset "${stop.offset}"`);
      const decls = Object.entries(stop.declarations);
      assert.ok(decls.length > 0, `${path}.${name}[${i}]: empty declarations`);
      for (const [prop, value] of decls) {
        assert.ok(PROP_NAME_RE.test(prop), `${path}.${name}[${i}]: bad property name "${prop}"`);
        assert.equal(typeof value, 'string', `${path}.${name}[${i}].${prop}: value must be a string`);
        assert.ok(value.length > 0, `${path}.${name}[${i}].${prop}: empty value`);
      }
    }
  }
}

test('every generated fixture parses as JSON and matches the CSS envelope shape', () => {
  assert.ok(fixtureFiles.length > 0, 'no fixtures generated');
  for (const { relPath, content } of fixtureFiles) {
    const doc = JSON.parse(content); // throws → test fails with the bad file named
    // The authoring envelope is {components} everywhere except the motion
    // keyframes fixture, which adds the document-level keyframes block
    // (spec 07 §1.1 — mirrors CSS @keyframes being document-scoped).
    if ('keyframes' in doc) {
      assert.deepEqual(Object.keys(doc).sort(), ['components', 'keyframes'], `${relPath}: envelope must be exactly {keyframes, components}`);
      assertKeyframesBlock(doc.keyframes, relPath);
    } else {
      assert.deepEqual(Object.keys(doc), ['components'], `${relPath}: envelope must be exactly {components}`);
    }
    const comps = Object.entries(doc.components);
    assert.ok(comps.length > 0, `${relPath}: empty components map`);
    for (const [name, comp] of comps) {
      assert.match(name, NODE_NAME_RE, `${relPath}: bad component name "${name}"`);
      assertComponentNode(comp, `${relPath}#${name}`);
    }
  }
});

// ── 3. Structural budgets ────────────────────────────────────────────────

test('combos: per-file cap of 25 components, each carrying at least 3 declarations', () => {
  const combos = manifest.files.filter((f) => f.kind === 'combos');
  assert.ok(combos.length >= 20, `expected combos for most categories, got ${combos.length}`);
  for (const entry of combos) {
    const doc = JSON.parse(byPath.get(entry.path));
    const comps = Object.values(doc.components);
    assert.ok(comps.length <= 25, `${entry.path}: ${comps.length} components exceeds the 25 cap`);
    assert.equal(comps.length, entry.components, `${entry.path}: manifest component count drifted`);
    for (const comp of comps) {
      assert.ok(
        Object.keys(comp.properties).length >= 3,
        `${entry.path}: combo component with fewer than 3 declarations`,
      );
    }
  }
});

test('trees: 9 files, 10–20 nodes each, map-in children, 3-level nesting present', () => {
  const trees = manifest.files.filter((f) => f.kind === 'tree');
  assert.equal(trees.length, 9, 'tree template set must stay at 9 files');
  let sawThreeLevels = false;
  const depthOf = (node) =>
    node.children ? 1 + Math.max(...Object.values(node.children).map(depthOf)) : 1;
  for (const entry of trees) {
    const doc = JSON.parse(byPath.get(entry.path));
    const roots = Object.values(doc.components);
    // every tree root actually has children (that is the point of the suite)
    for (const root of roots) {
      assert.ok(root.children, `${entry.path}: tree root without children`);
    }
    assert.ok(
      entry.nodes >= 10 && entry.nodes <= 20,
      `${entry.path}: ${entry.nodes} nodes outside the 10–20 budget`,
    );
    if (roots.some((r) => depthOf(r) >= 3)) sawThreeLevels = true;
  }
  assert.ok(sawThreeLevels, 'at least one tree must nest 3 levels deep');
});

// ── 3b. Pairwise cross-category coverage ─────────────────────────────────

test('pairwise: 6 shards cover all 66 category pairs, 4 components per pair, ≥2 props per side', () => {
  const shards = manifest.files.filter((f) => f.kind === 'pairwise');
  assert.equal(shards.length, 6, 'pairwise shard count must stay at 6');
  const catMap = buildCategoryMap(); // property → canonical category (converter catalogue)
  const seen = new Map(); // 'a+b' → component count across all shards
  for (const entry of shards) {
    const doc = JSON.parse(byPath.get(entry.path));
    const comps = Object.entries(doc.components);
    assert.ok(comps.length <= 44, `${entry.path}: ${comps.length} components exceeds the 44 shard cap`);
    assert.equal(comps.length, entry.components, `${entry.path}: manifest component count drifted`);
    const pairOrder = []; // unique pairs in component order — must match the descriptor
    for (const [name, comp] of comps) {
      // The name encodes the pair: PW_<PascalA>_<PascalB>_<NN>. All 12
      // pairwise categories are single lowercase words, so Pascal → lower
      // round-trips exactly.
      const m = name.match(/^PW_([A-Za-z]+)_([A-Za-z]+)_(\d{2})$/);
      assert.ok(m, `${entry.path}: bad pairwise component name "${name}"`);
      const a = m[1].toLowerCase();
      const b = m[2].toLowerCase();
      assert.ok(PAIRWISE_CATEGORIES.includes(a), `${name}: unknown category "${a}"`);
      assert.ok(PAIRWISE_CATEGORIES.includes(b), `${name}: unknown category "${b}"`);
      assert.ok(a < b, `${name}: pair must be lexicographically ordered (unordered-pair canon)`);
      const key = `${a}+${b}`;
      seen.set(key, (seen.get(key) ?? 0) + 1);
      if (pairOrder[pairOrder.length - 1] !== key) pairOrder.push(key);
      // Interaction-hunting substance: ≥2 properties from EACH side of the
      // pair (support props may add to a side — width/height are sizing,
      // background-color is background — so only the floor is asserted).
      let fromA = 0;
      let fromB = 0;
      for (const p of Object.keys(comp.properties)) {
        const cat = catMap.get(p);
        if (cat === a) fromA++;
        if (cat === b) fromB++;
      }
      assert.ok(fromA >= 2, `${name}: only ${fromA} properties from "${a}"`);
      assert.ok(fromB >= 2, `${name}: only ${fromB} properties from "${b}"`);
    }
    // The manifest coverage descriptor must be exactly the pairs generated,
    // in shard order.
    assert.deepEqual(pairOrder, entry.pairs, `${entry.path}: manifest pairs descriptor drifted`);
  }
  // Union across shards = all 66 unordered pairs, each with exactly 4 components.
  const expected = pairwisePairs().map(([a, b]) => `${a}+${b}`).sort();
  assert.deepEqual([...seen.keys()].sort(), expected, 'pairwise union must cover all 66 pairs');
  for (const [pair, n] of seen) {
    assert.equal(n, PAIRWISE_PER_PAIR, `pair ${pair}: expected ${PAIRWISE_PER_PAIR} components, got ${n}`);
  }
});

// ── 3c. Placement claims coverage (IR v2 slot/placement stress) ──────────

// The item-scoped claim properties (schema/spec/03-children.md frozen list —
// the subset the placement suite exercises).
const CLAIM_PROPS = [
  'grid-area', 'grid-column-start', 'grid-column-end', 'grid-row-start', 'grid-row-end',
  'order', 'align-self', 'justify-self', 'z-index',
];

test('placement: 6 files, 2–3 claim-carrying containers each, all hard cases present', () => {
  const placement = manifest.files.filter((f) => f.kind === 'placement');
  assert.equal(placement.length, 6, 'placement template set must stay at 6 files');
  // Structural scan flags — every hard case the suite exists for must be
  // observed in the actual fixture bytes, not just claimed by the manifest.
  let sawAreaNames = false;      // child claims an area the container defines
  let sawDecoupledName = false;  // child key ≠ claimed area name (anti name-matching)
  let sawDangling = false;       // child claims an area the container never defines
  let sawLines = false;          // numeric line claims
  let sawSpan = false;           // span claims
  let sawPermutation = false;    // ≥3 distinct order values on one row
  let sawSelfOverride = false;   // align-self/justify-self against a container policy
  let sawZStack = false;         // ≥2 overlapping abspos siblings with z-index
  let sawMixed = false;          // claimed + unclaimed children in ONE grid
  for (const entry of placement) {
    const doc = JSON.parse(byPath.get(entry.path));
    const roots = Object.entries(doc.components);
    assert.ok(roots.length >= 2 && roots.length <= 3, `${entry.path}: ${roots.length} containers outside 2–3`);
    assert.ok(entry.nodes >= 8 && entry.nodes <= 24, `${entry.path}: ${entry.nodes} nodes outside the 8–24 budget`);
    assert.ok(Array.isArray(entry.claims) && entry.claims.length > 0, `${entry.path}: manifest claims descriptor missing`);
    for (const [rootName, root] of roots) {
      assert.ok(root.children, `${entry.path}#${rootName}: placement container without children`);
      const cprops = root.properties;
      const kids = Object.entries(root.children);
      // Area names the container actually defines (identifiers inside the
      // quoted template rows; '.' cells are anonymous and excluded).
      const areaNames = new Set(
        (cprops['grid-template-areas'] ?? '').match(/[a-z][a-z0-9-]*/gi) ?? [],
      );
      let claimed = 0;
      let unclaimed = 0;
      for (const [kidName, kid] of kids) {
        const kp = kid.properties;
        CLAIM_PROPS.some((p) => p in kp) ? claimed++ : unclaimed++;
        const area = kp['grid-area'];
        // A pure-name grid-area claim (no slashes/spaces ⇒ not line syntax).
        if (area && !/[/ ]/.test(area)) {
          if (areaNames.has(area)) {
            sawAreaNames = true;
            if (kidName !== area) sawDecoupledName = true;
          } else if (cprops['grid-template-areas']) {
            sawDangling = true; // defined template, undefined name → CSS auto-place fallback
          }
        }
        if (/^-?\d+$/.test(kp['grid-column-start'] ?? '') || /^-?\d+$/.test(kp['grid-row-start'] ?? '')) sawLines = true;
        if (/span/.test(kp['grid-column-start'] ?? '') || /span/.test(area ?? '')) sawSpan = true;
        if (('align-self' in kp && 'align-items' in cprops) || ('justify-self' in kp && 'justify-items' in cprops)) sawSelfOverride = true;
      }
      if (cprops.display === 'grid' && claimed > 0 && unclaimed > 0) sawMixed = true;
      const orders = kids.map(([, k]) => k.properties.order).filter((o) => o !== undefined);
      if (orders.length >= 3 && new Set(orders).size >= 3) sawPermutation = true;
      const zKids = kids.filter(([, k]) => k.properties.position === 'absolute' && 'z-index' in k.properties);
      if (zKids.length >= 2) sawZStack = true;
    }
  }
  assert.ok(sawAreaNames, 'no grid-area name claim found');
  assert.ok(sawDecoupledName, 'no child with key ≠ claimed area name (name-matching trap missing)');
  assert.ok(sawDangling, 'no dangling area claim (undefined area name) found');
  assert.ok(sawLines, 'no numeric grid line claim found');
  assert.ok(sawSpan, 'no span claim found');
  assert.ok(sawPermutation, 'no order permutation found');
  assert.ok(sawSelfOverride, 'no self-alignment override against a container policy found');
  assert.ok(sawZStack, 'no z-index stacking between abspos siblings found');
  assert.ok(sawMixed, 'no grid mixing claimed and unclaimed children found');
  // Claim-descriptor vocabulary union — the coverage promise wave runs read.
  const claimUnion = new Set(placement.flatMap((f) => f.claims));
  for (const c of [
    'grid-area-names', 'dangling-claim', 'grid-lines', 'grid-spans',
    'order-permutation', 'align-self-override', 'justify-self-override',
    'z-index-stacking', 'auto-flow-interleave', 'mixed-claims',
  ]) {
    assert.ok(claimUnion.has(c), `placement manifest claims missing "${c}"`);
  }
});

// ── 3d. Tokens coverage (custom properties / var() / calc — wave 6) ──────

test('tokens: 3 files carrying var definitions, references, fallbacks, and calc mixes', () => {
  const tokens = manifest.files.filter((f) => f.kind === 'tokens');
  assert.equal(tokens.length, 3, 'tokens template set must stay at 3 files');
  // Structural scan flags — every dynamic-value scenario the suite exists
  // for must be observed in the actual fixture bytes.
  let sawVarDef = false;         // a --name declaration (variables source)
  let sawVarRef = false;         // a var(--x) reference
  let sawChainRef = false;       // a reference on a child whose ANCESTOR defines it
  let sawShadowing = false;      // a child redefining a token its ancestor defines
  let sawMissing = false;        // var() with neither definition nor fallback
  let sawFallback = false;       // var(--x, literal)
  let sawNestedFallback = false; // var(--x, var(--y, literal))
  let sawCalcPctPx = false;      // calc mixing % and px
  let sawCalcEm = false;         // calc using em (inherited font-size)
  let sawNestedCalc = false;     // calc(calc(…))
  let sawVarInCalc = false;      // calc(var(--x) …)
  // Recursive scan carrying the set of token names defined by ancestors.
  const scan = (node, inherited) => {
    const props = node.properties ?? {};
    const defined = new Set(inherited);
    for (const [p, v] of Object.entries(props)) {
      if (p.startsWith('--')) {
        sawVarDef = true;
        if (inherited.has(p)) sawShadowing = true;
        defined.add(p);
        continue;
      }
      for (const m of v.matchAll(/var\((--[a-z0-9-]+)\s*(,)?/gi)) {
        sawVarRef = true;
        const [, name, hasFallback] = m;
        if (inherited.has(name)) sawChainRef = true;
        if (hasFallback) sawFallback = true;
        if (!hasFallback && !inherited.has(name) && !(name in props)) sawMissing = true;
      }
      if (/var\(--[a-z0-9-]+,\s*var\(/i.test(v)) sawNestedFallback = true;
      if (/calc\([^)]*%[^)]*px/i.test(v)) sawCalcPctPx = true;
      if (/calc\([^)]*\dem\b/i.test(v)) sawCalcEm = true;
      if (/calc\(\s*calc\(/i.test(v)) sawNestedCalc = true;
      if (/calc\(\s*var\(/i.test(v)) sawVarInCalc = true;
    }
    for (const child of Object.values(node.children ?? {})) scan(child, defined);
  };
  for (const entry of tokens) {
    const doc = JSON.parse(byPath.get(entry.path));
    const roots = Object.values(doc.components);
    assert.ok(roots.length >= 2 && roots.length <= 3, `${entry.path}: ${roots.length} containers outside 2–3`);
    assert.ok(entry.nodes >= 8 && entry.nodes <= 16, `${entry.path}: ${entry.nodes} nodes outside the 8–16 budget`);
    assert.ok(Array.isArray(entry.features) && entry.features.length > 0, `${entry.path}: manifest features descriptor missing`);
    // Token names never leak into category attribution (they carry no
    // irmodels category — they ride the variables envelope key).
    assert.ok(!entry.categories.includes('other'), `${entry.path}: token declarations mis-attributed to a category`);
    for (const root of roots) scan(root, new Set());
  }
  assert.ok(sawVarDef, 'no custom-property definition (--name) found');
  assert.ok(sawVarRef, 'no var() reference found');
  assert.ok(sawChainRef, 'no reference resolved through an ancestor definition (slot chain)');
  assert.ok(sawShadowing, 'no child shadowing an ancestor token');
  assert.ok(sawMissing, 'no missing-var (guaranteed-invalid) case found');
  assert.ok(sawFallback, 'no var() fallback found');
  assert.ok(sawNestedFallback, 'no nested fallback chain found');
  assert.ok(sawCalcPctPx, 'no calc(% … px) mix found');
  assert.ok(sawCalcEm, 'no calc with em found');
  assert.ok(sawNestedCalc, 'no nested calc found');
  assert.ok(sawVarInCalc, 'no calc consuming var() found');
  // Feature-descriptor vocabulary union — the coverage promise wave runs read.
  const featureUnion = new Set(tokens.flatMap((f) => f.features));
  for (const feat of [
    'var-definitions', 'var-references', 'slot-chain-resolution', 'shadowing',
    'missing-var', 'fallback', 'nested-fallback', 'definition-beats-fallback',
    'calc-percent-px', 'calc-em-px', 'nested-calc', 'em-inheritance-chain', 'var-in-calc',
  ]) {
    assert.ok(featureUnion.has(feat), `tokens manifest features missing "${feat}"`);
  }
});

// ── 3e. Dynamic coverage (selector states + media queries — wave 7) ─────

test('dynamic: 3 files carrying state buckets, width buckets, and dark-mode buckets with guaranteed contrast', () => {
  const dynamic = manifest.files.filter((f) => f.kind === 'dynamic');
  assert.equal(dynamic.length, 3, 'dynamic template set must stay at 3 files');
  // Structural scan flags — every dynamic-styling scenario the suite exists
  // for must be observed in the actual fixture bytes (spec 06 §2/§3/§4).
  const states = new Set();       // runtime-v1 conditions seen (:x → x)
  let sawSelectorLayering = false; // ≥2 selector buckets claiming one property type
  let sawMediaLayering = false;    // ≥2 media buckets claiming one property type
  let sawMinMatch390 = false;      // (min-width ≤ 390px) — active at the default width
  let sawMinNoMatch390 = false;    // (min-width > 390px) — must stay inert
  let sawMaxMatch390 = false;      // (max-width ≥ 390px) — active at the default width
  let sawFlip250 = false;          // a bucket whose truth flips between 390 and 250
  let sawSchemeDark = false;       // (prefers-color-scheme: dark) bucket
  let sawLightDark = false;        // light-dark() color value in base properties
  let sawLayoutBucket = false;     // a bucket touching geometry (width/height/padding)
  for (const entry of dynamic) {
    const doc = JSON.parse(byPath.get(entry.path));
    const comps = Object.values(doc.components);
    assert.ok(comps.length >= 4 && comps.length <= 8, `${entry.path}: ${comps.length} components outside 4–8`);
    assert.ok(Array.isArray(entry.features) && entry.features.length > 0, `${entry.path}: manifest features descriptor missing`);
    for (const comp of comps) {
      const base = comp.properties;
      if (Object.values(base).some((v) => v.startsWith('light-dark('))) sawLightDark = true;
      const buckets = [...(comp.selectors ?? []), ...(comp.media ?? [])];
      // The pixel-contrast guarantee: EVERY bucket overrides at least one
      // base declaration with a DIFFERENT value, so bucket application vs
      // non-application always changes pixels (charter requirement — a
      // bucket of only-new properties could render invisibly).
      for (const bucket of buckets) {
        assert.ok(
          Object.entries(bucket.properties).some(([p, v]) => p in base && base[p] !== v),
          `${entry.path}: bucket ${bucket.selector ?? bucket.query} overrides no base declaration`,
        );
        if (Object.keys(bucket.properties).some((p) => ['width', 'height', 'padding'].includes(p))) sawLayoutBucket = true;
      }
      // Layering pins: two buckets of the SAME kind claiming the same
      // property — array order must decide (spec 06 §3).
      const claimsOverlap = (list) => {
        const seen = new Set();
        for (const b of list ?? []) {
          for (const p of Object.keys(b.properties)) {
            if (seen.has(p)) return true;
            seen.add(p);
          }
        }
        return false;
      };
      if (claimsOverlap(comp.selectors)) sawSelectorLayering = true;
      if (claimsOverlap(comp.media)) sawMediaLayering = true;
      for (const b of comp.selectors ?? []) states.add(b.selector.slice(1));
      for (const b of comp.media ?? []) {
        const min = b.query.match(/^\(min-width: (\d+)px\)$/);
        const max = b.query.match(/^\(max-width: (\d+)px\)$/);
        if (min && Number(min[1]) <= 390) sawMinMatch390 = true;
        if (min && Number(min[1]) > 390) sawMinNoMatch390 = true;
        if (max && Number(max[1]) >= 390) sawMaxMatch390 = true;
        // Flip rows: min-width in (250, 390] or max-width in [250, 390)
        // answers differently at the two capture widths (DYNAMIC_CAPTURE §2).
        if (min && Number(min[1]) > 250 && Number(min[1]) <= 390) sawFlip250 = true;
        if (max && Number(max[1]) >= 250 && Number(max[1]) < 390) sawFlip250 = true;
        if (b.query === '(prefers-color-scheme: dark)') sawSchemeDark = true;
      }
    }
  }
  // All five runtime-v1 conditions must be exercised (spec 06 §2).
  assert.deepEqual([...states].sort(), ['active', 'checked', 'disabled', 'focus', 'hover'], 'runtime-v1 condition set drifted');
  assert.ok(sawSelectorLayering, 'no multi-selector-bucket layering pin found');
  assert.ok(sawMediaLayering, 'no multi-media-bucket layering pin found');
  assert.ok(sawMinMatch390, 'no min-width bucket matching at 390px found');
  assert.ok(sawMinNoMatch390, 'no min-width bucket that must stay inert at 390px found');
  assert.ok(sawMaxMatch390, 'no max-width bucket matching at 390px found');
  assert.ok(sawFlip250, 'no bucket flipping between the 390px and 250px capture widths found');
  assert.ok(sawSchemeDark, 'no (prefers-color-scheme: dark) bucket found');
  assert.ok(sawLightDark, 'no light-dark() color value found');
  assert.ok(sawLayoutBucket, 'no bucket touching layout geometry found');
  // Feature-descriptor vocabulary union — the coverage promise wave runs read.
  const featureUnion = new Set(dynamic.flatMap((f) => f.features));
  for (const feat of [
    'selector-hover', 'selector-active', 'selector-focus', 'selector-disabled',
    'selector-checked', 'selector-layering', 'media-min-width', 'media-max-width',
    'media-match-390', 'media-nomatch-390', 'media-flip-250', 'media-layering',
    'media-layout-flip', 'prefers-color-scheme-dark', 'light-dark-color',
    'scheme-bucket-plus-light-dark',
  ]) {
    assert.ok(featureUnion.has(feat), `dynamic manifest features missing "${feat}"`);
  }
});

// ── 3f. Motion coverage (keyframes + transitions — wave 8) ───────────────

test('motion: 2 files — keyframes cover the animatable scenarios, transitions tie states to motion', () => {
  const motion = manifest.files.filter((f) => f.kind === 'motion');
  assert.equal(motion.length, 2, 'motion template set must stay at 2 files');

  // keyframes-basic.json — the document-level keyframes block + consumers.
  const kfEntry = motion.find((f) => f.path.endsWith('motion/keyframes-basic.json'));
  assert.ok(kfEntry, 'motion/keyframes-basic.json missing from the manifest');
  const kfDoc = JSON.parse(byPath.get(kfEntry.path));
  const sets = kfDoc.keyframes;
  assert.ok(sets && Object.keys(sets).length >= 5, 'keyframes-basic must define ≥5 named sets');
  // Manifest descriptor lists exactly the defined sets (sorted).
  assert.deepEqual(kfEntry.keyframeSets, Object.keys(sets).sort(), 'manifest keyframeSets drifted');
  // Scenario scan — every animatable-tier channel the suite exists for
  // (spec 07 §2) must be observed in actual fixture bytes.
  const allDecls = Object.values(sets).flat().map((s) => s.declarations);
  assert.ok(allDecls.some((d) => 'opacity' in d), 'no opacity keyframe found');
  assert.ok(allDecls.some((d) => /translate/.test(d.transform ?? '')), 'no translate keyframe found');
  assert.ok(allDecls.some((d) => /scale/.test(d.transform ?? '')), 'no scale keyframe found');
  assert.ok(allDecls.some((d) => 'background-color' in d), 'no color-shift keyframe found');
  assert.ok(allDecls.some((d) => Object.keys(d).length >= 3), 'no multi-property keyframe stop found');
  assert.ok(Object.values(sets).some((stops) => stops.length >= 3), 'no 3-stop set found');
  // Unsorted-offset pin: at least one set authors a percent offset out of
  // ascending order, so the converter's sorted emission is observable.
  const pct = (o) => (o === 'from' ? 0 : o === 'to' ? 100 : parseFloat(o));
  assert.ok(
    Object.values(sets).some((stops) => stops.some((s, i) => i > 0 && pct(s.offset) < pct(stops[i - 1].offset))),
    'no set with unsorted authored offsets found',
  );
  // Consumers: every animation-name references a set THIS document defines
  // (the dangling-reference case is pinned by the conformance golden, not
  // the visual suite — a dangling fixture would just capture statically),
  // carries a duration, and all durations share the 1s one-clock contract.
  const comps = Object.values(kfDoc.components);
  let sawAlternate = false;
  let sawFillBothDelay = false;
  const geometries = new Set();
  for (const comp of comps) {
    const p = comp.properties;
    assert.ok(p['animation-name'] in sets, `animation-name "${p['animation-name']}" dangles`);
    assert.ok(p['animation-duration'], 'animated component without a duration');
    assert.equal(p['animation-duration'], '1s', 'motion one-clock contract: all durations 1s');
    if (p['animation-direction'] === 'alternate') sawAlternate = true;
    if (p['animation-fill-mode'] === 'both' && p['animation-delay']) sawFillBothDelay = true;
    geometries.add(`${p.width ?? 'auto'}×${p.height ?? 'auto'}`);
  }
  assert.equal(geometries.size, comps.length, 'animated components must have pairwise-distinct geometry');
  assert.ok(sawAlternate, 'no alternate-direction component found');
  assert.ok(sawFillBothDelay, 'no fill-mode:both + delay component found');

  // transitions.json — state-triggered motion (spec 07 §4).
  const trEntry = motion.find((f) => f.path.endsWith('motion/transitions.json'));
  assert.ok(trEntry, 'motion/transitions.json missing from the manifest');
  const trDoc = JSON.parse(byPath.get(trEntry.path));
  assert.ok(!('keyframes' in trDoc), 'transitions.json must not define keyframes');
  assert.deepEqual(trEntry.keyframeSets, [], 'transitions manifest keyframeSets must be empty');
  let sawDelay = false;
  let sawAll = false;
  for (const [name, comp] of Object.entries(trDoc.components)) {
    const p = comp.properties;
    assert.ok(p['transition-property'], `${name}: no transition-property`);
    assert.ok(p['transition-duration'], `${name}: no transition-duration`);
    assert.ok(Array.isArray(comp.selectors) && comp.selectors.length > 0, `${name}: no selector bucket to trigger the transition`);
    // The bucket must CHANGE a transitioned property — otherwise forcing
    // the state starts no transition and the time capture is vacuous.
    const transitioned = p['transition-property'];
    for (const bucket of comp.selectors) {
      assert.ok(
        Object.entries(bucket.properties).some(([bp, bv]) =>
          (transitioned === 'all' || bp === transitioned) && bp in p && p[bp] !== bv),
        `${name}: bucket ${bucket.selector} does not change the transitioned property`,
      );
    }
    if (p['transition-delay']) sawDelay = true;
    if (transitioned === 'all') sawAll = true;
  }
  assert.ok(sawDelay, 'no delayed transition found');
  assert.ok(sawAll, 'no transition-property: all found');

  // Feature-descriptor vocabulary union — the coverage promise wave runs read.
  const featureUnion = new Set(motion.flatMap((f) => f.features));
  for (const feat of [
    'keyframes-opacity', 'keyframes-translate', 'keyframes-scale', 'keyframes-color',
    'keyframes-multi-property', 'keyframes-three-stop', 'direction-alternate',
    'fill-mode-both', 'unsorted-offsets', 'transition-background', 'transition-width',
    'transition-all', 'transition-delay', 'state-triggered-motion',
  ]) {
    assert.ok(featureUnion.has(feat), `motion manifest features missing "${feat}"`);
  }
});

test('manifest lists every generated fixture exactly once with correct byte sizes', () => {
  const listed = manifest.files.map((f) => f.path).sort();
  const actual = fixtureFiles.map((f) => f.relPath).sort();
  assert.deepEqual(listed, actual, 'manifest file list drifted from generated output');
  for (const entry of manifest.files) {
    assert.equal(
      entry.bytes,
      Buffer.byteLength(byPath.get(entry.path)),
      `${entry.path}: manifest byte size drifted`,
    );
  }
  assert.equal(manifest.totals.files, manifest.files.length);
  assert.equal(
    manifest.totals.components,
    manifest.files.reduce((s, f) => s + f.components, 0),
  );
  assert.equal(
    manifest.totals.nodes,
    manifest.files.reduce((s, f) => s + f.nodes, 0),
  );
});

// ── 4. Converter round-trip (opt-in: needs JDK 21 + warm Gradle) ─────────

// Nine representative files: the wave-4 trio (smallest fixture, largest
// combos file, deepest tree) plus the wave-5 additions — one pairwise shard
// (cross-category payload pressure) and two placement trees: grid-areas.json
// (named areas + the dangling claim) and mixed-claims.json (claimed+unclaimed
// auto-flow interleave — the claim/resolution algorithm's hardest case) —
// plus the wave-6 tokens trio (ALL of it: the variables envelope key and the
// var()/calc() preservation contract are exactly what these files exist to
// pin end-to-end through the real converter).
function representativeFiles() {
  const byBytes = [...manifest.files].sort((a, b) => a.bytes - b.bytes || (a.path < b.path ? -1 : 1));
  const smallest = byBytes[0];
  const largestCombos = [...manifest.files]
    .filter((f) => f.kind === 'combos')
    .sort((a, b) => b.bytes - a.bytes || (a.path < b.path ? -1 : 1))[0];
  const deepestTree = manifest.files.find((f) => f.path.endsWith('trees/nested-3level.json'));
  const pairwiseShard = manifest.files.find((f) => f.path.endsWith('pairwise/pairs-01.json'));
  const placementAreas = manifest.files.find((f) => f.path.endsWith('placement/grid-areas.json'));
  const placementMixed = manifest.files.find((f) => f.path.endsWith('placement/mixed-claims.json'));
  // Wave 6: the whole tokens suite rides along — the variables envelope
  // key + verbatim var()/calc() preservation are pinned per file below.
  const tokenFiles = manifest.files.filter((f) => f.kind === 'tokens');
  // Wave 7: the whole dynamic suite too — selector/media bucket survival
  // (conditions colon-stripped, queries verbatim, order preserved) is
  // exactly what these files exist to pin end-to-end.
  const dynamicFiles = manifest.files.filter((f) => f.kind === 'dynamic');
  // Wave 8: the whole motion suite — the document-level keyframes block
  // surviving to the wire TYPED (offsets resolved + sorted, declarations
  // as {type,data} envelopes) is exactly what keyframes-basic.json pins;
  // transitions.json rides along for its bucket + transition-* survival.
  const motionFiles = manifest.files.filter((f) => f.kind === 'motion');
  // De-dupe while preserving the selection intent (paths are unique keys).
  return [...new Map(
    [smallest, largestCombos, deepestTree, pairwiseShard, placementAreas, placementMixed, ...tokenFiles, ...dynamicFiles, ...motionFiles]
      .map((f) => [f.path, f]),
  ).values()];
}

/** Collect every authored custom-property map ({--name: raw}) in tree order,
 *  plus every TOP-LEVEL var()/calc() declaration VALUE — the verbatim-
 *  preservation and variables-envelope expectations for the converter
 *  round-trip below. Top-level only: a whole-value `var(…)`/`calc(…)` is
 *  unresolvable and MUST survive byte-for-byte (spec 02 null+original),
 *  whereas expressions nested inside an otherwise-parseable function (e.g.
 *  the relative color `rgb(from red calc(r - 50) g b)`) legitimately decompose
 *  into structured IR. */
function collectTokenExpectations(components) {
  const variableMaps = []; // one entry per component that declares --* props
  const dynamicValues = new Set(); // whole-value var()/calc() declarations
  const walk = (node) => {
    const vars = {};
    for (const [p, v] of Object.entries(node.properties ?? {})) {
      if (p.startsWith('--')) vars[p] = v;
      else if (/^(var|calc)\(/.test(v.trim())) dynamicValues.add(v);
    }
    if (Object.keys(vars).length > 0) variableMaps.push(vars);
    for (const child of Object.values(node.children ?? {})) walk(child);
  };
  for (const comp of Object.values(components)) walk(comp);
  return { variableMaps, dynamicValues };
}

/** Collect the authored selector/media bucket expectations per component
 *  name, in authoring order: conditions WITHOUT the leading colon (the
 *  converter strips it — Selectors.kt) and raw query strings (forwarded
 *  verbatim — Media.kt). The dynamic suite authors buckets only on ROOT
 *  components, so a flat name→expectation map suffices. */
function collectBucketExpectations(components) {
  const byName = new Map();
  for (const [name, comp] of Object.entries(components)) {
    if (!comp.selectors && !comp.media) continue;
    byName.set(name, {
      conditions: (comp.selectors ?? []).map((b) => b.selector.replace(/^:/, '')),
      queries: (comp.media ?? []).map((b) => b.query),
      // Per-bucket authored declaration counts — bucket properties parse
      // through the same PropertiesParser as base declarations, so each
      // emitted bucket must carry AT LEAST the authored count (shorthands
      // like `border` expand to more longhands, never fewer).
      selectorMinProps: (comp.selectors ?? []).map((b) => Object.keys(b.properties).length),
      mediaMinProps: (comp.media ?? []).map((b) => Object.keys(b.properties).length),
    });
  }
  return byName;
}

/** Count every node in an authored components map (children maps recurse). */
function countInputNodes(components) {
  let n = 0;
  for (const comp of Object.values(components)) {
    n += 1;
    if (comp.children) n += countInputNodes(comp.children);
  }
  return n;
}

const CONVERT_ENABLED = process.env.GEN_FIDELITY_CONVERT === '1';

test(
  'converter round-trip: 14 representative files convert cleanly, validate against IR v2, and emit flat+slot+variables+bucket+keyframes output',
  { skip: CONVERT_ENABLED ? false : 'set GEN_FIDELITY_CONVERT=1 (requires JDK 21) to run' },
  () => {
    // Pin Java 21 on macOS dev machines, mirroring schema/conformance/run.mjs.
    const env = { ...process.env };
    if (process.platform === 'darwin') {
      try {
        env.JAVA_HOME = execFileSync('/usr/libexec/java_home', ['-v', '21'], { encoding: 'utf8' }).trim();
      } catch {
        // fall through to whatever JAVA_HOME is set
      }
    }
    const gradlew = join(REPO, process.platform === 'win32' ? 'gradlew.bat' : 'gradlew');
    const validate = makeValidatorV2();

    for (const entry of representativeFiles()) {
      // out/ is gitignored; one scratch dir per input so artifacts don't clobber.
      const outDir = join(REPO, 'out', 'fidelity-roundtrip', entry.path.split('/').pop().replace(/\.json$/, ''));
      rmSync(outDir, { recursive: true, force: true });
      mkdirSync(outDir, { recursive: true });
      // Non-zero exit (parse failure, usage error) throws and fails the test.
      execFileSync(
        gradlew,
        [':converter:run', '-q', `--args=convert --from css --to ir -i ${join(REPO, entry.path)} -o ${outDir}`],
        { cwd: REPO, env, stdio: 'pipe' },
      );
      const irPath = join(outDir, 'tmpOutput.json');
      assert.ok(existsSync(irPath), `${entry.path}: converter produced no tmpOutput.json`);
      const ir = JSON.parse(readFileSync(irPath, 'utf8'));
      // Wire-contract validation via the shared conformance validator.
      const { ok, errors } = validateDocument(validate, ir);
      assert.ok(
        ok,
        `${entry.path}: IR fails schema validation:\n` +
          errors.map((e) => `  ${e.path}: ${e.message}`).join('\n'),
      );
      // Envelope arithmetic (IR v2 flat list): every entry WITHOUT a slot
      // ref is a root, and the root count must survive conversion —
      // descendants now live as flat slot-carrying entries, not nested
      // children (schema/spec/03-children.md).
      const input = JSON.parse(byPath.get(entry.path));
      assert.equal(
        ir.components.filter((c) => !c.slot).length,
        Object.keys(input.components).length,
        `${entry.path}: root component count changed during conversion`,
      );
      // Flat + slot assertions (the v2 wire promise the placement suite
      // exists to exercise): explicit version stamp, ZERO nested children
      // anywhere, every non-root slot ref resolvable, and the flat entry
      // count equal to the authored node count (pre-order flattener is
      // lossless — no node dropped, none duplicated).
      assert.equal(ir.irVersion, 2, `${entry.path}: converter must emit irVersion 2`);
      const ids = new Set(ir.components.map((c) => c.id));
      assert.equal(ids.size, ir.components.length, `${entry.path}: duplicate component ids in flat list`);
      for (const c of ir.components) {
        assert.ok(!('children' in c), `${entry.path}#${c.id}: nested children survived flattening`);
        if (c.slot) {
          assert.ok(ids.has(c.slot.parent), `${entry.path}#${c.id}: dangling slot.parent "${c.slot.parent}"`);
        }
      }
      assert.equal(
        ir.components.length,
        countInputNodes(input.components),
        `${entry.path}: flat component count != authored node count`,
      );
      // Wave-6 dynamic-value pins (tokens suite; vacuous for files that
      // author no --*/var()/calc() declarations):
      const { variableMaps, dynamicValues } = collectTokenExpectations(input.components);
      // (a) variables envelope key: every authored --* declaration block
      // must surface as an IDENTICAL component-level variables map (names
      // case-preserved, raw values verbatim, nothing added or dropped) —
      // pre-order flattening preserves authoring order, so the sequence of
      // maps must match 1:1.
      const emittedMaps = ir.components.filter((c) => c.variables).map((c) => c.variables);
      assert.deepEqual(
        emittedMaps,
        variableMaps,
        `${entry.path}: component variables maps drifted from the authored --* declarations`,
      );
      if (entry.kind === 'tokens') {
        assert.ok(variableMaps.length > 0, `${entry.path}: tokens fixture authored no --* declarations`);
        assert.ok(dynamicValues.size > 0, `${entry.path}: tokens fixture authored no var()/calc() values`);
      }
      // (b) preservation contract (spec 02): every var()/calc() declaration
      // value must appear byte-for-byte somewhere in the emitted IR text —
      // the exact-string assertion that catches lowercase folds, re-
      // tokenization, or truncation anywhere in the parser surface.
      const irText = readFileSync(irPath, 'utf8');
      for (const value of dynamicValues) {
        assert.ok(
          irText.includes(value),
          `${entry.path}: dynamic value '${value}' was not preserved verbatim in the emitted IR`,
        );
      }
      // Wave-7 dynamic-styling pins (dynamic suite; vacuous for files that
      // author no selectors/media buckets): every authored bucket must
      // survive to the wire — conditions colon-stripped, queries VERBATIM,
      // array order preserved (spec 06 §3 layering depends on it), each
      // bucket's property list parsed (≥ authored count — shorthands only
      // ever expand) and never empty.
      const bucketExpectations = collectBucketExpectations(input.components);
      for (const [name, expect] of bucketExpectations) {
        const emitted = ir.components.find((c) => c.name === name);
        assert.ok(emitted, `${entry.path}#${name}: bucket-carrying component missing from the wire`);
        assert.deepEqual(
          (emitted.selectors ?? []).map((s) => s.condition),
          expect.conditions,
          `${entry.path}#${name}: selector conditions drifted (order or colon-stripping)`,
        );
        assert.deepEqual(
          (emitted.media ?? []).map((m) => m.query),
          expect.queries,
          `${entry.path}#${name}: media queries not preserved verbatim in order`,
        );
        (emitted.selectors ?? []).forEach((s, i) => {
          assert.ok(
            s.properties.length >= expect.selectorMinProps[i],
            `${entry.path}#${name}: selector bucket ${i} lost declarations (${s.properties.length} < ${expect.selectorMinProps[i]})`,
          );
        });
        (emitted.media ?? []).forEach((m, i) => {
          assert.ok(
            m.properties.length >= expect.mediaMinProps[i],
            `${entry.path}#${name}: media bucket ${i} lost declarations (${m.properties.length} < ${expect.mediaMinProps[i]})`,
          );
        });
      }
      // Wave-8 motion pins (motion suite; vacuous for files that author no
      // keyframes block): the authored sets must survive to the wire TYPED
      // — same names, offsets RESOLVED to 0..1 fractions and SORTED
      // ascending, every stop's declarations parsed into {type,data}
      // property envelopes (≥ authored count — shorthands only expand).
      if (input.keyframes) {
        assert.ok(ir.keyframes, `${entry.path}: authored keyframes block missing from the wire`);
        assert.deepEqual(
          Object.keys(ir.keyframes).sort(),
          Object.keys(input.keyframes).sort(),
          `${entry.path}: keyframe set names drifted`,
        );
        const toFraction = (o) => (o === 'from' ? 0 : o === 'to' ? 1 : parseFloat(o) / 100);
        for (const [name, authoredStops] of Object.entries(input.keyframes)) {
          const emitted = ir.keyframes[name];
          assert.equal(emitted.length, authoredStops.length, `${entry.path}#${name}: stop count drifted`);
          // Offsets: resolved fractions, sorted ascending — exactly the
          // authored offsets' sorted image (spec 07 §1.2).
          const emittedOffsets = emitted.map((s) => s.offset);
          assert.deepEqual(
            emittedOffsets,
            authoredStops.map((s) => toFraction(s.offset)).sort((a, b) => a - b),
            `${entry.path}#${name}: offsets not resolved+sorted`,
          );
          for (const stop of emitted) {
            assert.ok(Array.isArray(stop.properties) && stop.properties.length > 0, `${entry.path}#${name}: empty typed stop`);
            for (const prop of stop.properties) {
              assert.equal(typeof prop.type, 'string', `${entry.path}#${name}: stop property without a type`);
              assert.ok('data' in prop, `${entry.path}#${name}: stop property without data`);
              // TYPED, not a Generic passthrough — every motion declaration
              // uses tier properties with dedicated parsers.
              assert.notEqual(prop.type, 'Generic', `${entry.path}#${name}: keyframe declaration degraded to Generic`);
            }
          }
        }
      } else {
        assert.ok(!ir.keyframes, `${entry.path}: wire grew a keyframes key the author never wrote`);
      }
      if (entry.kind === 'dynamic') {
        assert.ok(bucketExpectations.size > 0, `${entry.path}: dynamic fixture authored no buckets`);
        // light-dark() color values are dynamic colors (srgb null,
        // original preserved — spec 02): the raw function text must
        // survive somewhere in the emitted IR for dark-mode fixtures.
        for (const comp of Object.values(input.components)) {
          for (const v of Object.values(comp.properties ?? {})) {
            if (v.startsWith('light-dark(')) {
              assert.ok(irText.includes('light-dark'), `${entry.path}: light-dark() value vanished from the wire`);
            }
          }
        }
      }
    }
  },
);
