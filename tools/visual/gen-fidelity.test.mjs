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
//   4. converter round-trip — 3 representative files convert cleanly and the
//      emitted IR validates against schema/ir-v1.schema.json. Gradle-slow and
//      JDK-21-dependent, so it only runs when GEN_FIDELITY_CONVERT=1 is set
//      (the CI test-tooling job has no JDK — see .github/workflows/ci.yml).

import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync, existsSync, mkdirSync, rmSync } from 'node:fs';
import { join } from 'node:path';
import { execFileSync } from 'node:child_process';

import { generate, SEED, REPO } from './gen-fidelity.mjs';
// Reuse the conformance machinery (ajv 2020-12 + the IR v1 schema) rather
// than re-compiling a validator here; run.mjs is import-safe (main() is
// guarded behind an argv check).
import { makeValidator, validateDocument } from '../../schema/conformance/run.mjs';

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

// Three representative files: the smallest fixture, the largest combos file
// (typography-scale property pressure), and the deepest tree (children
// map-in → array-out flattening at 3 levels).
function representativeFiles() {
  const byBytes = [...manifest.files].sort((a, b) => a.bytes - b.bytes || (a.path < b.path ? -1 : 1));
  const smallest = byBytes[0];
  const largestCombos = [...manifest.files]
    .filter((f) => f.kind === 'combos')
    .sort((a, b) => b.bytes - a.bytes || (a.path < b.path ? -1 : 1))[0];
  const deepestTree = manifest.files.find((f) => f.path.endsWith('trees/nested-3level.json'));
  // De-dupe while preserving the selection intent (paths are unique keys).
  return [...new Map([smallest, largestCombos, deepestTree].map((f) => [f.path, f])).values()];
}

const CONVERT_ENABLED = process.env.GEN_FIDELITY_CONVERT === '1';

test(
  'converter round-trip: 3 representative files convert cleanly and validate against IR v1',
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
    const validate = makeValidator();

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
      // Envelope arithmetic: top-level component count survives conversion
      // (children flatten map→array inside components, not into the root).
      const input = JSON.parse(byPath.get(entry.path));
      assert.equal(
        ir.components.length,
        Object.keys(input.components).length,
        `${entry.path}: top-level component count changed during conversion`,
      );
    }
  },
);
