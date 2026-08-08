// tools/titan/build-combined-fixture.test.mjs
//
// Unit pins for the section pipeline's combined-fixture builder, added by
// wave-35 lane B2 alongside the `@font-face` DOCUMENT hop.
//
// The face merge is the one part of this file with a SPEC rule behind it
// (css-fonts-4 §4.1's (family, weight, style) slot and §4.2's
// case-insensitive family match), and the one part whose failure mode is
// silent: a shadowed face renders plausible text from the WRONG file. So the
// slot rule, the dedupe and the delivery stamp are pinned here rather than
// left to the integration run to notice.
//
// The end-to-end test writes throwaway per-test fixtures under
// `fixtures/wpt/_w35b2test/` (the directory the builder reads from) and
// removes them afterwards. The section name is deliberately unique so a
// parallel agent's section can never collide with it.

import { test } from 'node:test';
import assert from 'node:assert/strict';
import { promises as fs } from 'node:fs';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { execFile } from 'node:child_process';
import { promisify } from 'node:util';

import {
  componentKey, familyKey, faceSlotKey, faceIdentityKey, usableFace,
} from './build-combined-fixture.mjs';

const execFileAsync = promisify(execFile);
const __dirname = dirname(fileURLToPath(import.meta.url));
const REPO_ROOT = resolve(__dirname, '..', '..');
const SECTION = '_w35b2test';
const FIXTURE_DIR = join(REPO_ROOT, 'fixtures', 'wpt', SECTION);

// ── pure helpers ────────────────────────────────────────────────────────────

test('componentKey encodes section + stem + child index', () => {
  assert.equal(componentKey('css/css-text/boundary-shaping/boundary-shaping-001.html', 0),
    'wpt__css-text__boundary-shaping__boundary-shaping-001__0');
});

test('familyKey is ASCII case-insensitive and quote-stripping (§4.2)', () => {
  // `Test`, `test` and ` test ` name ONE family; treating them as three would
  // emit three database entries of which only the last could ever render.
  assert.equal(familyKey('Test'), 'test');
  assert.equal(familyKey('  test  '), 'test');
  assert.equal(familyKey('TEST'), familyKey('test'));
});

test('faceSlotKey is the §4.1 (family, weight, style) triple', () => {
  const a = { family: 'test', src: 'a.woff', weight: '700', style: 'italic' };
  const b = { family: 'TEST', src: 'b.woff', weight: '700', style: 'italic' };
  // Same slot, different file — this is precisely the collision the merge
  // must refuse to resolve silently.
  assert.equal(faceSlotKey(a), faceSlotKey(b));
  // A different weight is a DIFFERENT slot: both faces coexist in one
  // document, exactly as a browser would hold them.
  assert.notEqual(faceSlotKey(a), faceSlotKey({ ...a, weight: '400' }));
  assert.notEqual(faceSlotKey(a), faceSlotKey({ ...a, style: undefined }));
});

test('faceIdentityKey separates "same face twice" from "same slot, other file"', () => {
  const a = { family: 'test', src: 'a.woff', weight: null, style: null };
  assert.equal(faceIdentityKey(a), faceIdentityKey({ ...a, family: 'TEST' }));
  assert.notEqual(faceIdentityKey(a), faceIdentityKey({ ...a, src: 'b.woff' }));
});

test('usableFace demands both REQUIRED descriptors (spec 01 §5)', () => {
  assert.equal(usableFace({ family: 'test', src: 'a.woff' }), true);
  assert.equal(usableFace({ family: '', src: 'a.woff' }), false);
  assert.equal(usableFace({ family: 'test', src: '   ' }), false);
  assert.equal(usableFace({ family: 'test' }), false);
  assert.equal(usableFace(null), false);
});

// ── end-to-end over the real CLI ────────────────────────────────────────────

/** Write one per-test fixture where the builder expects it. */
async function writeFixture(stem, body) {
  await fs.mkdir(FIXTURE_DIR, { recursive: true });
  await fs.writeFile(join(FIXTURE_DIR, `${stem}.json`), JSON.stringify(body), 'utf8');
}

async function runBuilder(t, stems) {
  const dir = await fs.mkdtemp(join(REPO_ROOT, 'tools', 'titan', '.w35b2-'));
  t.after(() => fs.rm(dir, { recursive: true, force: true }));
  const testsFile = join(dir, 'tests.list');
  const outFile = join(dir, 'combined.json');
  await fs.writeFile(testsFile, stems.map((s) => `css/${SECTION}/${s}.html`).join('\n'), 'utf8');
  const { stderr } = await execFileAsync(process.execPath,
    [join(__dirname, 'build-combined-fixture.mjs'), '--tests', testsFile, '--out', outFile]);
  return { out: JSON.parse(await fs.readFile(outFile, 'utf8')), stderr };
}

test('the same face declared by many tests collapses to ONE document entry', async (t) => {
  t.after(() => fs.rm(FIXTURE_DIR, { recursive: true, force: true }));
  const face = { family: 'test', src: 'css/x/res/Lin.woff' };
  await writeFixture('one', { fontFaces: [face], components: { a: { properties: {} } } });
  await writeFixture('two', { fontFaces: [face], components: { b: { properties: {} } } });

  const { out } = await runBuilder(t, ['one', 'two']);
  assert.deepEqual(out.fontFaces, [face]);
  assert.equal(out._wpt.stats.fontFaces, 1);
  // BOTH tests are delivered: they asked for the same file and got it.
  for (const meta of Object.values(out._wpt.keyMap)) {
    assert.equal(meta.fontFacesDelivered, true);
  }
  assert.equal(out._wpt.fontFaceConflicts, undefined);
});

test('a shadowed face keeps the FIRST file and un-stamps the loser', async (t) => {
  t.after(() => fs.rm(FIXTURE_DIR, { recursive: true, force: true }));
  await writeFixture('first', {
    fontFaces: [{ family: 'test', src: 'css/x/A.woff' }],
    components: { a: { properties: {} } },
  });
  await writeFixture('second', {
    fontFaces: [{ family: 'Test', src: 'css/x/B.woff' }],   // same slot, other file
    components: { b: { properties: {} } },
  });

  const { out, stderr } = await runBuilder(t, ['first', 'second']);
  // Document order wins — a browser reading the concatenated sheets in list
  // order would hold the first declaration until the second replaced it, and
  // we refuse the replacement rather than let one test's file shape another's.
  assert.deepEqual(out.fontFaces, [{ family: 'test', src: 'css/x/A.woff' }]);
  assert.equal(out._wpt.fontFaceConflicts.length, 1);
  assert.equal(out._wpt.fontFaceConflicts[0].kept, 'css/x/A.woff');
  assert.equal(out._wpt.fontFaceConflicts[0].shadowed, 'css/x/B.woff');
  // The stamp is per-test: the winner is delivered, the loser is NOT — which
  // is what keeps inject-wpt-block's Rule 15 gate from scoring a test whose
  // text would shape from a sibling's file.
  const byTest = Object.fromEntries(
    Object.values(out._wpt.keyMap).map((m) => [m.stem, m.fontFacesDelivered]));
  assert.equal(byTest.first, true);
  assert.equal(byTest.second, false);
  // …and it is LOUD: a conflict that only lived in the JSON would be a silent
  // wrong-typeface risk.
  assert.match(stderr, /WARNING @font-face family "Test" already owned by/);
});

test('a face-free section emits no fontFaces key at all', async (t) => {
  t.after(() => fs.rm(FIXTURE_DIR, { recursive: true, force: true }));
  await writeFixture('plain', { components: { a: { properties: {} } } });

  const { out } = await runBuilder(t, ['plain']);
  // Omit-when-empty: every pre-wave-35 section's combined fixture stays
  // byte-identical, so the converter and all four decoders see no change.
  assert.equal('fontFaces' in out, false);
  assert.equal(out._wpt.stats.fontFaces, 0);
  // A test that declared nothing is NOT "delivered" — the stamp means "the
  // faces this test needs are present", and a test with no faces has no
  // requires-font-face tag for the gate to re-admit anyway.
  assert.equal(Object.values(out._wpt.keyMap)[0].fontFacesDelivered, false);
});

test('a missing fixture stamps fontFacesDelivered false, never inferred', async (t) => {
  t.after(() => fs.rm(FIXTURE_DIR, { recursive: true, force: true }));
  await fs.mkdir(FIXTURE_DIR, { recursive: true });   // exists, but no fixture in it
  const { out } = await runBuilder(t, ['absent']);
  const meta = Object.values(out._wpt.keyMap)[0];
  assert.equal(meta.bucket, 'missing');
  assert.equal(meta.fontFacesDelivered, false);
});

test('malformed face entries are dropped and cost the test its stamp', async (t) => {
  t.after(() => fs.rm(FIXTURE_DIR, { recursive: true, force: true }));
  await writeFixture('bad', {
    // `src` missing ⇒ names no file ⇒ nothing to deliver. Dropping at the
    // first reader (rather than passing it to the converter, which drops it
    // too) is what keeps the stamp truthful.
    fontFaces: [{ family: 'test' }],
    components: { a: { properties: {} } },
  });
  const { out } = await runBuilder(t, ['bad']);
  assert.equal('fontFaces' in out, false);
  assert.equal(Object.values(out._wpt.keyMap)[0].fontFacesDelivered, false);
});
