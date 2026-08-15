//
// tools/titan/svg-preraster.test.mjs — wave-40 lane T5.
//
// Pins for the SVG PRE-RASTER hop's pure halves plus one END-TO-END raster
// against the real corpus vectors. The end-to-end case is the one that matters
// most: the whole hop rests on the claim that Chromium's `naturalWidth` IS the
// css-images-3 §5.1 concrete object size, and the six css-ui support vectors
// are precisely the six shapes that claim has to survive (both dimensions,
// width only, height only, ratio only, and each dimension paired with a
// ratio). It SKIPS itself when the corpus is not materialised, because
// tools/wpt/ is a gitignored mirror and CI does not fetch it.

import test from 'node:test';
import assert from 'node:assert/strict';
import { existsSync } from 'node:fs';
import { promises as fs } from 'node:fs';
import { tmpdir } from 'node:os';
import { join, resolve, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';
import { PNG } from 'pngjs';

import {
  PRERASTER_SUFFIX, isPrerasterCandidate, prerasterSrcFor, isPrerasterSrc,
  applyPrerasterRewrite, collectSvgSources, prerasterizeSvgSources,
} from './svg-preraster.mjs';

const REPO_ROOT = resolve(dirname(fileURLToPath(import.meta.url)), '..', '..');
const WPT_DIR = join(REPO_ROOT, 'tools', 'wpt');

// ── the candidate gate ──────────────────────────────────────────────────────

test('isPrerasterCandidate admits corpus-relative .svg and nothing else', () => {
  assert.equal(isPrerasterCandidate('css/css-ui/support/h100.svg'), true);
  assert.equal(isPrerasterCandidate('support/R1-1.SVG'), true, 'extension is case-insensitive');
  assert.equal(isPrerasterCandidate('  support/a.svg  '), true, 'the wire value is trimmed');
  // Rasters are already deliverable — standing in for one would be pointless
  // work and would shadow the authored file.
  assert.equal(isPrerasterCandidate('support/a.png'), false);
  assert.equal(isPrerasterCandidate('support/a.svgz'), false, 'gzipped SVG is a different container');
  // A data: payload already rides the wire; the natives decline it in their
  // own data: branch, which is a separately stamped gap. Inventing a file for
  // it would mean delivering something the wire never named.
  assert.equal(isPrerasterCandidate('data:image/svg+xml;base64,PHN2Zz48L3N2Zz4='), false);
  // Containment, mirroring resolveReplacedImageFile: an absolute or protocol
  // source names a file outside the corpus, which this hop must never write.
  assert.equal(isPrerasterCandidate('/abs/a.svg'), false);
  assert.equal(isPrerasterCandidate('https://example.com/a.svg'), false);
  for (const bad of [null, undefined, 42, '', '   ']) {
    assert.equal(isPrerasterCandidate(bad), false, `bad input ${JSON.stringify(bad)}`);
  }
});

// ── the naming contract ─────────────────────────────────────────────────────

test('the raster suffix is APPENDED, so the vector stem survives verbatim', () => {
  assert.equal(PRERASTER_SUFFIX, '.png');
  assert.equal(prerasterSrcFor('css/css-ui/support/r1-1.svg'), 'css/css-ui/support/r1-1.svg.png');
  // The load-bearing consequence: substitution (`r1-1.png`) could collide with
  // a real sibling the corpus already ships and silently deliver the wrong
  // picture. Appending cannot collide with any authored name, because no
  // authored file is called `<stem>.svg.png`.
  assert.notEqual(prerasterSrcFor('a/b.svg'), 'a/b.png');
});

test('isPrerasterSrc is the same predicate the two natives implement', () => {
  // Byte-parallel with Kotlin DocumentImageRegistry.isHostPreRaster and Swift
  // DocumentImageRegistry.isHostPreRaster — the three are pinned separately on
  // purpose: a drift means a stand-in paints with no line in any log saying it
  // was one.
  assert.equal(isPrerasterSrc('css/s/r1-1.svg.png'), true);
  assert.equal(isPrerasterSrc('css/s/R1-1.SVG.PNG'), true);
  assert.equal(isPrerasterSrc('  css/s/a.svg.png  '), true);
  assert.equal(isPrerasterSrc('css/s/a.png'), false, 'an authored raster is not a stand-in');
  assert.equal(isPrerasterSrc('css/s/a.svg'), false, 'the vector itself is not a stand-in');
  assert.equal(isPrerasterSrc('css/s/svg.png'), false, 'the dot before svg is required');
  assert.equal(isPrerasterSrc(null), false);
});

// ── the wire rewrite ────────────────────────────────────────────────────────

const imgComponent = (id, src) => ({
  id, name: id, properties: [], meta: { sourceTag: 'img', attrs: { src } },
});

test('applyPrerasterRewrite rewrites ONLY sources that were actually rasterised', () => {
  const doc = {
    irVersion: 2,
    components: [
      imgComponent('a', 'css/s/r1-1.svg'),
      imgComponent('b', 'css/s/r1-1.svg'),   // same vector, many boxes
      imgComponent('c', 'css/s/w100.svg'),   // NOT in the map — raster failed
      imgComponent('d', 'css/s/photo.png'),  // authored raster, untouched
      { id: 'e', name: 'e', properties: [], meta: { role: 'body-root' } },
    ],
  };
  const applied = applyPrerasterRewrite(doc, new Map([['css/s/r1-1.svg', 'css/s/r1-1.svg.png']]));
  assert.deepEqual(applied, [{ src: 'css/s/r1-1.svg', rasterSrc: 'css/s/r1-1.svg.png' }],
    'reported once per distinct vector, not once per painting box');
  assert.equal(doc.components[0].meta.attrs.src, 'css/s/r1-1.svg.png');
  assert.equal(doc.components[1].meta.attrs.src, 'css/s/r1-1.svg.png');
  // A vector whose raster failed KEEPS its .svg so the runtime's format
  // decline still fires. Rewriting it to a file that does not exist would swap
  // a precise "this platform has no SVG rasteriser" for a misleading "the
  // feeder hop did not deliver it".
  assert.equal(doc.components[2].meta.attrs.src, 'css/s/w100.svg');
  assert.equal(doc.components[3].meta.attrs.src, 'css/s/photo.png');
});

test('applyPrerasterRewrite reaches a v1 nested document too', () => {
  // The feeders decode whatever IR the section pipeline handed them; the same
  // robustness feed-lib.mjs's documentReplacedSrcs has, for the same reason —
  // a v1 fixture silently keeping its vectors would be an invisible failure.
  const doc = {
    components: [{
      id: 'p', name: 'p', properties: [],
      children: [imgComponent('kid', 'css/s/a.svg')],
    }],
  };
  const applied = applyPrerasterRewrite(doc, new Map([['css/s/a.svg', 'css/s/a.svg.png']]));
  assert.equal(applied.length, 1);
  assert.equal(doc.components[0].children[0].meta.attrs.src, 'css/s/a.svg.png');
});

test('applyPrerasterRewrite is a no-op on a document with nothing to rewrite', () => {
  const doc = { components: [imgComponent('a', 'css/s/photo.png')] };
  const before = JSON.stringify(doc);
  assert.deepEqual(applyPrerasterRewrite(doc, new Map()), []);
  assert.equal(JSON.stringify(doc), before, 'byte-identical — this is what keeps every non-SVG section unchanged');
});

test('collectSvgSources dedupes across the whole batch, in first-seen order', () => {
  // 19 css-ui tests share six support vectors; rasterising per fixture instead
  // of per batch would launch the browser 19 times for the same six files.
  const srcsOf = (d) => d.srcs;
  const got = collectSvgSources(
    [{ srcs: ['a.svg', 'x.png'] }, { srcs: ['b.svg', 'a.svg'] }, { srcs: ['data:image/svg+xml,x'] }],
    srcsOf,
  );
  assert.deepEqual(got, ['a.svg', 'b.svg']);
});

// ── the rasteriser's declines ───────────────────────────────────────────────

test('prerasterizeSvgSources declines everything, loudly, with no --wpt-dir', async () => {
  const lines = [];
  const map = await prerasterizeSvgSources(['a.svg'], { enabled: true, wptDir: null, log: (m) => lines.push(m) });
  assert.equal(map.size, 0);
  assert.match(lines.join('\n'), /SKIPPED/);
});

test('the hop is OFF by default — the MEASURED verdict, not a pending question', async (t) => {
  // The default is OFF on TWO measured A/Bs of the 19-test cluster. Wave-41
  // T2: arm B (raster delivered) cost 7 Android + 8 iOS passes — the eight
  // vacuous passes all flipped to fail when the §10.4-unclamped raster
  // painted its intrinsic size and overshot the constrained box. Wave-42 W6
  // landed §10.4 and the wave-43 V1 RE-RUN measured the size defect gone
  // but arm B still costing 8 Android + 7 iOS passes on PLACEMENT: the
  // delivered replaced roots block-stack one per line where the ref packs
  // them on line boxes, because the composed root row flow admits only
  // declared inline-blocks (InlineBlockAtom B1). The flip is now gated on
  // wiring InlineAtomFlow's wave-43 replaced-image atom family through the
  // root flow. An empty map makes applyPrerasterRewrite a no-op, so "off"
  // is byte-identical, not merely similar. Full per-arm numbers in the
  // switch banner in svg-preraster.mjs.
  const prev = process.env.TITAN_SVG_PRERASTER;
  delete process.env.TITAN_SVG_PRERASTER;
  t.after(() => { if (prev === undefined) delete process.env.TITAN_SVG_PRERASTER; else process.env.TITAN_SVG_PRERASTER = prev; });
  const lines = [];
  const map = await prerasterizeSvgSources(['css/css-ui/support/r1-1.svg'], {
    wptDir: WPT_DIR, log: (m) => lines.push(m), launch: () => { throw new Error('must not launch'); },
  });
  assert.equal(map.size, 0);
  assert.match(lines.join('\n'), /OFF \(default/);
  // …and an empty map really is a no-op on a document that references it.
  const doc = { components: [imgComponent('a', 'css/css-ui/support/r1-1.svg')] };
  const before = JSON.stringify(doc);
  assert.deepEqual(applyPrerasterRewrite(doc, map), []);
  assert.equal(JSON.stringify(doc), before);
});

test('TITAN_SVG_PRERASTER=1 engages the hop; any other value leaves it off', async (t) => {
  const prev = process.env.TITAN_SVG_PRERASTER;
  t.after(() => { if (prev === undefined) delete process.env.TITAN_SVG_PRERASTER; else process.env.TITAN_SVG_PRERASTER = prev; });
  // Hermetic corpus: the engaged arm must get PAST the existsSync gate to
  // reach launch, and the real tools/wpt/ mirror is gitignored (absent on
  // CI). A temp root with a stub vector pins the env semantics everywhere —
  // unlike the end-to-end raster below, nothing here needs a real browser or
  // real vector content, so this test must never skip.
  const tmpRoot = await fs.mkdtemp(join(tmpdir(), 'svg-preraster-env-'));
  t.after(() => fs.rm(tmpRoot, { recursive: true, force: true }));
  await fs.mkdir(join(tmpRoot, 'css', 'css-ui', 'support'), { recursive: true });
  await fs.writeFile(join(tmpRoot, 'css', 'css-ui', 'support', 'r1-1.svg'), '<svg xmlns="http://www.w3.org/2000/svg"/>');
  // Only the literal '1' engages it — 'true'/'yes'/'0' must NOT, so a typo in
  // a section-runner env line cannot silently turn a measured-off gate on.
  for (const [val, wantLaunch] of [['1', true], ['0', false], ['true', false], ['', false]]) {
    process.env.TITAN_SVG_PRERASTER = val;
    let launched = false;
    await prerasterizeSvgSources(['css/css-ui/support/r1-1.svg'], {
      wptDir: tmpRoot,
      log: () => {},
      launch: () => { launched = true; throw new Error('stop here — reaching launch is the assertion'); },
    });
    assert.equal(launched, wantLaunch, `TITAN_SVG_PRERASTER=${JSON.stringify(val)}`);
  }
});

test('prerasterizeSvgSources launches nothing for an empty batch', async () => {
  let launched = false;
  const map = await prerasterizeSvgSources([], {
    enabled: true, wptDir: WPT_DIR, log: () => {}, launch: () => { launched = true; },
  });
  assert.equal(map.size, 0);
  assert.equal(launched, false, '29 of the 30 depth-48 sections reference no vector at all');
});

test('prerasterizeSvgSources declines a vector that escapes the corpus root', async () => {
  const lines = [];
  let launched = false;
  const map = await prerasterizeSvgSources(['../../etc/evil.svg'], {
    enabled: true, wptDir: WPT_DIR, log: (m) => lines.push(m), launch: () => { launched = true; },
  });
  assert.equal(map.size, 0);
  assert.equal(launched, false, 'containment is decided before any browser exists');
  assert.match(lines.join('\n'), /outside the corpus root/);
});

test('prerasterizeSvgSources declines a vector that is not on disk', async () => {
  const lines = [];
  const map = await prerasterizeSvgSources(['css/css-ui/support/definitely-absent.svg'], {
    enabled: true, wptDir: WPT_DIR, log: (m) => lines.push(m), launch: () => { throw new Error('must not launch'); },
  });
  assert.equal(map.size, 0);
  assert.match(lines.join('\n'), /no such vector on disk/);
});

// ── the two feeders' wiring (source-scan pins) ──────────────────────────────

test('feed-android rewrites BEFORE the image push, and pushes the rewritten file', async () => {
  // Ordering is the contract: the image hop delivers whatever `meta.attrs.src`
  // says at the moment it runs, so a rewrite applied afterwards would push the
  // VECTOR and then ask the device to open a PNG that was never sent — the
  // exact "feeder hop did not deliver it" mis-diagnosis the decline text warns
  // about. And Android pushes the fixture FILE (iOS re-serialises its doc), so
  // the pushed path must be the rewritten one or the device reads the vector
  // back off disk regardless.
  const src = await fs.readFile(new URL('./feed-android.mjs', import.meta.url), 'utf8');
  const rewriteAt = src.indexOf('const pushFx = pushableFixture(');
  const imagesAt = src.indexOf('const images = pushReplacedImages(');
  assert.ok(rewriteAt > 0 && imagesAt > 0, 'both sites must exist');
  assert.ok(rewriteAt < imagesAt, 'the rewrite must precede the image push');
  assert.ok(src.includes("adbx(['push', pushFx, `${INBOX_DIR}"),
    'the inbox push must carry the rewritten file, not the original');
  // The tail-retry pass re-reads the fixture FROM DISK, so it must re-apply
  // the rewrite or a salvaged row is the one capture in the run scored against
  // a vector both natives decline.
  assert.ok(src.indexOf('const retryFx = pushableFixture(') > imagesAt,
    'the tail-retry pass must re-apply the rewrite');
  assert.ok(src.includes("adbx(['push', retryFx,"), 'and push its rewritten file');
});

test('feed-ios rewrites BEFORE the image copy loop', async () => {
  const src = await fs.readFile(new URL('./feed-ios.mjs', import.meta.url), 'utf8');
  const rewriteAt = src.indexOf('applyPrerasterRewrite(doc, rasterMap)');
  const copyAt = src.indexOf('for (const src of documentReplacedSrcs(doc))');
  assert.ok(rewriteAt > 0 && copyAt > 0, 'both sites must exist');
  assert.ok(rewriteAt < copyAt, 'the rewrite must precede the image copy');
  // iOS re-serialises its in-memory doc into the inbox, so no file swap is
  // needed there — but the write must still come after the rewrite.
  assert.ok(src.indexOf('JSON.stringify(doc)') > rewriteAt);
});

test('the pre-raster pre-pass runs ONCE per feeder run, before the fixture loop', async () => {
  // 19 css-ui tests share six vectors; rasterising inside the loop would
  // launch the browser once per fixture. Both feeders must call the batch
  // entry point, and must call it above their loop.
  for (const f of ['feed-android.mjs', 'feed-ios.mjs']) {
    const src = await fs.readFile(new URL(`./${f}`, import.meta.url), 'utf8');
    const prepassAt = src.indexOf('await prerasterizeFixtures(');
    const applyAt = src.indexOf('applyPrerasterRewrite(');
    assert.ok(prepassAt > 0, `${f}: must run the batch pre-pass`);
    assert.ok(prepassAt < applyAt, `${f}: the pre-pass must precede any rewrite`);
    // MEASURED REGRESSION (this run): both call sites shipped without
    // `srcsOf`, every unit test passed because they inject their own walker,
    // and the first real feeder run died with "srcsOf is not a function". The
    // pre-raster module deliberately does not import feed-lib — so the
    // injection IS the wiring, and the wiring needs a pin of its own.
    const call = src.slice(prepassAt, src.indexOf('});', prepassAt));
    assert.match(call, /srcsOf:\s*documentReplacedSrcs/,
      `${f}: the pre-pass must be handed feed-lib's own document walker`);
  }
});

test('the pre-pass runs ahead of every device mutation inside main()', async () => {
  // Scoped to main()'s body, because both feeders DEFINE their device helpers
  // above it — a whole-file scan would match a function declaration and pin
  // nothing. MEASURED motivation: the first device run of this lane spent ten
  // minutes on a wedged emulator before anyone could tell whether the rasters
  // had even been written. Host work first means a sick device cannot mask a
  // raster failure, and a raster failure cannot be mistaken for one.
  const cases = [
    ['feed-android.mjs', 'resetAndLaunch(adbx, opts)'],
    ['feed-ios.mjs', "run('xcrun', ['simctl', 'launch'"],
  ];
  for (const [f, firstDeviceMutation] of cases) {
    const whole = await fs.readFile(new URL(`./${f}`, import.meta.url), 'utf8');
    const src = whole.slice(whole.indexOf('async function main('));
    const prepassAt = src.indexOf('await prerasterizeFixtures(');
    const deviceAt = src.indexOf(firstDeviceMutation);
    assert.ok(prepassAt > 0 && deviceAt > 0, `${f}: both sites must exist in main()`);
    assert.ok(prepassAt < deviceAt, `${f}: the pre-pass must precede ${firstDeviceMutation}`);
  }
});

// ── THE SCALE CONTRACT, end to end against the real corpus ──────────────────

const CSS_UI_SUPPORT = join(WPT_DIR, 'css', 'css-ui', 'support');
const haveCorpus = existsSync(join(CSS_UI_SUPPORT, 'r1-1.svg'));

// The concrete object sizes Chromium computes for the six css-ui support
// vectors — css-images-3 §5.1's default sizing algorithm against the 300×150
// default object size. These are the numbers the natives read back OUT of the
// PNG as the CSS intrinsic size, which is why they are pinned as literals here
// rather than recomputed from the SVG attributes: if a browser upgrade moves
// one, every box-sizing capture moves with it and this test is where that
// shows up first.
const EXPECTED_NATURAL = {
  'w100_h100.svg': [100, 100],   // both intrinsic dimensions
  'w100.svg': [100, 150],        // intrinsic width only  → default object height
  'h100.svg': [300, 100],        // intrinsic height only → default object width
  'r1-1.svg': [150, 150],        // ratio only, no intrinsic size at all
  'w100_r1-1.svg': [100, 100],   // intrinsic width + ratio
  'h100_r1-1.svg': [100, 100],   // intrinsic height + ratio
};

test('the corpus vectors rasterise at their concrete object size', { skip: !haveCorpus ? 'tools/wpt not materialised (gitignored mirror)' : false }, async (t) => {
  // Rasterise into a THROWAWAY corpus root so the test never writes into
  // tools/wpt (a re-run of the pipeline must not inherit a test artefact) —
  // the vectors are copied in under their real relative paths so containment,
  // naming and resolution are exercised exactly as they are in a feeder run.
  const root = await fs.mkdtemp(join(tmpdir(), 'svg-preraster-test-'));
  t.after(() => fs.rm(root, { recursive: true, force: true }));
  const rel = (n) => `css/css-ui/support/${n}`;
  await fs.mkdir(join(root, 'css', 'css-ui', 'support'), { recursive: true });
  for (const name of Object.keys(EXPECTED_NATURAL)) {
    await fs.copyFile(join(CSS_UI_SUPPORT, name), join(root, rel(name)));
  }

  const lines = [];
  const map = await prerasterizeSvgSources(Object.keys(EXPECTED_NATURAL).map(rel),
    { enabled: true, wptDir: root, log: (m) => lines.push(m) });

  for (const [name, [w, h]] of Object.entries(EXPECTED_NATURAL)) {
    const rasterSrc = map.get(rel(name));
    assert.equal(rasterSrc, `${rel(name)}.png`, `${name} produced no raster — log:\n${lines.join('\n')}`);
    const png = PNG.sync.read(await fs.readFile(join(root, rasterSrc)));
    assert.deepEqual([png.width, png.height], [w, h], `${name} raster size`);
    // The stamp names the size, because the size is the lossy part of the hop.
    assert.match(lines.join('\n'), new RegExp(`${name}\\.png at ${w}x${h}`));
    // The vectors paint `background: green` over their whole viewport, so the
    // centre pixel proves the RASTER IS THE VECTOR'S PIXELS and not a blank
    // canvas of the right size — the failure mode a size-only assertion would
    // sail straight past.
    const i = ((png.height >> 1) * png.width + (png.width >> 1)) << 2;
    assert.equal(png.data[i + 0] < 40, true, `${name} centre R`);
    assert.equal(png.data[i + 1] > 100, true, `${name} centre G`);
    assert.equal(png.data[i + 2] < 40, true, `${name} centre B`);
    assert.equal(png.data[i + 3], 255, `${name} centre alpha (the ink is opaque)`);
  }

  // A second pass must hit the mtime cache and launch nothing: a section
  // re-run pays for the browser once, ever.
  const cachedLines = [];
  const again = await prerasterizeSvgSources(Object.keys(EXPECTED_NATURAL).map(rel), {
    enabled: true, wptDir: root, log: (m) => cachedLines.push(m),
    launch: () => { throw new Error('must not launch on a cache hit'); },
  });
  assert.equal(again.size, Object.keys(EXPECTED_NATURAL).length);
  assert.match(cachedLines.join('\n'), /CACHED/);
});

test('the raster keeps ALPHA, so it cannot paint a backdrop the stylesheet never asked for',
  { skip: !haveCorpus ? 'tools/wpt not materialised (gitignored mirror)' : false }, async (t) => {
    // The box-sizing family sets `background: white` on every <img>; the
    // element's own background paints UNDER the image, so an opaque backdrop
    // baked into the raster would be a rectangle nothing declared. A vector
    // that covers only part of its viewport is the case that proves it.
    const root = await fs.mkdtemp(join(tmpdir(), 'svg-preraster-alpha-'));
    t.after(() => fs.rm(root, { recursive: true, force: true }));
    await fs.mkdir(join(root, 's'), { recursive: true });
    await fs.writeFile(join(root, 's', 'half.svg'),
      '<svg xmlns="http://www.w3.org/2000/svg" width="20" height="20">'
      + '<rect x="0" y="0" width="20" height="10" fill="green"/></svg>');
    const map = await prerasterizeSvgSources(['s/half.svg'], { enabled: true, wptDir: root, log: () => {} });
    assert.equal(map.get('s/half.svg'), 's/half.svg.png');
    const png = PNG.sync.read(await fs.readFile(join(root, 's/half.svg.png')));
    assert.deepEqual([png.width, png.height], [20, 20]);
    const at = (x, y) => png.data[((y * png.width + x) << 2) + 3];
    assert.equal(at(10, 5), 255, 'the painted half is opaque');
    assert.equal(at(10, 15), 0, 'the unpainted half is fully TRANSPARENT');
  });
