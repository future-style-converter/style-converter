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
//   6. converter round-trip — 6 representative files (3 wave-4 originals + a
//      pairwise shard + 2 placement trees) convert cleanly, the emitted IR
//      validates against schema/ir-v2.schema.json, and the output is flat +
//      slot-composed (no nested children survive). Gradle-slow and
//      JDK-21-dependent, so it only runs when GEN_FIDELITY_CONVERT=1 is set
//      (the CI test-tooling job has no JDK — see .github/workflows/ci.yml).

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
// generate a subset (no selectors/media/_role), so the check is strict on
// exactly what the generator is allowed to emit.
const ALLOWED_NODE_KEYS = new Set(['properties', 'children', '_text']);
const PROP_NAME_RE = /^-?[a-z][a-z0-9-]*$/; // css longhand/shorthand names (optional vendor dash)
const NODE_NAME_RE = /^[A-Za-z][A-Za-z0-9_]*$/;

function assertComponentNode(node, path) {
  assert.equal(typeof node, 'object', `${path}: node must be an object`);
  for (const key of Object.keys(node)) {
    assert.ok(ALLOWED_NODE_KEYS.has(key), `${path}: unexpected envelope key "${key}"`);
  }
  // properties is always present (may be {} — e.g. inheritance "plain" children).
  assert.equal(typeof node.properties, 'object', `${path}: missing properties map`);
  for (const [prop, value] of Object.entries(node.properties)) {
    assert.match(prop, PROP_NAME_RE, `${path}: bad property name "${prop}"`);
    assert.equal(typeof value, 'string', `${path}.${prop}: value must be a string`);
    assert.ok(value.length > 0, `${path}.${prop}: empty value`);
  }
  if ('_text' in node) {
    assert.equal(typeof node._text, 'string', `${path}: _text must be a string`);
    assert.ok(node._text.length > 0, `${path}: generator never emits empty _text`);
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

test('every generated fixture parses as JSON and matches the CSS envelope shape', () => {
  assert.ok(fixtureFiles.length > 0, 'no fixtures generated');
  for (const { relPath, content } of fixtureFiles) {
    const doc = JSON.parse(content); // throws → test fails with the bad file named
    assert.deepEqual(Object.keys(doc), ['components'], `${relPath}: envelope must be exactly {components}`);
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

test('trees: 8 files, 10–20 nodes each, map-in children, 3-level nesting present', () => {
  const trees = manifest.files.filter((f) => f.kind === 'tree');
  assert.equal(trees.length, 8, 'tree template set must stay at 8 files');
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

// Six representative files: the wave-4 trio (smallest fixture, largest combos
// file, deepest tree) plus the wave-5 additions — one pairwise shard
// (cross-category payload pressure) and two placement trees: grid-areas.json
// (named areas + the dangling claim) and mixed-claims.json (claimed+unclaimed
// auto-flow interleave — the claim/resolution algorithm's hardest case).
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
  // De-dupe while preserving the selection intent (paths are unique keys).
  return [...new Map(
    [smallest, largestCombos, deepestTree, pairwiseShard, placementAreas, placementMixed]
      .map((f) => [f.path, f]),
  ).values()];
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
  'converter round-trip: 6 representative files convert cleanly, validate against IR v2, and emit flat+slot output',
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
    }
  },
);
