#!/usr/bin/env node
//
// Unit tests for the wave-36 lane M1 REPLACED-ELEMENT SOURCE lane in
// tools/titan/extract-fixture.mjs.
//
// Its own file rather than more lines in extract-fixture.test.mjs: that
// suite is edited by several lanes per wave and this one is self-contained
// (four exports plus one end-to-end pin). Picked up by the same
// `node --test tools/titan/*.test.mjs` glob.
//
// WHAT THE LANE IS FOR. `object-fit` / `object-position` decide how a
// replaced element's content is scaled into its box; until this wave nothing
// on the wire carried the content, so every css-images object-* capture
// scaled a fixed 100×100 placeholder — 154 of 196 scored cells failing on
// the wave-35 web map with the right box, the right keyword and the wrong
// pixels.

import { test } from 'node:test';
import assert from 'node:assert/strict';
import os from 'node:os';
import path from 'node:path';
import { promises as fsp } from 'node:fs';

import {
  REPLACED_SRC_TAGS,
  REPLACED_IMAGE_FORMATS,
  REPLACED_SRC_LOSSY_REASON,
  replacedSrcFor,
  resolveReplacedSrc,
  inlineFixtureAssets,
  WIDGET_ATTR_TAGS,
  LIST_ATTR_TAGS,
  buildComponents,
  parseCss,
} from './extract-fixture.mjs';

// ── the pure half: which attribute names the source ─────────────────────────

test('M1: the tag→attribute table is exactly img/embed/object/video', () => {
  assert.deepEqual([...REPLACED_SRC_TAGS.entries()], [
    ['img', 'src'],
    ['embed', 'src'],
    ['object', 'data'],   // HTML §4.8.7
    ['video', 'poster'],  // HTML §4.8.8 — the poster frame a still capture paints
  ]);
});

test('M1: the three attr lanes are DISJOINT (the buildNode merge can never collide)', () => {
  for (const tag of REPLACED_SRC_TAGS.keys()) {
    assert.equal(WIDGET_ATTR_TAGS.has(tag), false, `${tag} is also a widget tag`);
    assert.equal(LIST_ATTR_TAGS.has(tag), false, `${tag} is also a list tag`);
  }
});

test('M1: replacedSrcFor reads the RIGHT attribute per tag, verbatim', () => {
  assert.deepEqual(replacedSrcFor('img', { src: 'support/a.png' }), { src: 'support/a.png' });
  assert.deepEqual(replacedSrcFor('embed', { src: 'support/a.png' }), { src: 'support/a.png' });
  // `data`, not `src`, on <object> — reading `src` there would silently
  // deliver nothing for a third of the object-fit family.
  assert.deepEqual(replacedSrcFor('object', { data: 'support/a.png' }), { src: 'support/a.png' });
  assert.equal(replacedSrcFor('object', { src: 'support/a.png' }), null);
  assert.deepEqual(replacedSrcFor('video', { poster: 'support/a.png' }), { src: 'support/a.png' });
  assert.equal(replacedSrcFor('video', { src: 'movie.mp4' }), null);
});

test('M1: replacedSrcFor declines non-replaced tags, absent and empty sources', () => {
  assert.equal(replacedSrcFor('div', { src: 'a.png' }), null);
  assert.equal(replacedSrcFor('input', { src: 'a.png' }), null); // widget lane's tag
  assert.equal(replacedSrcFor(null, { src: 'a.png' }), null);
  assert.equal(replacedSrcFor('img', {}), null);
  assert.equal(replacedSrcFor('img', null), null);
  // `src=""` names the document itself (HTML §4.8.4.1), never an image.
  assert.equal(replacedSrcFor('img', { src: '   ' }), null);
});

// ── the resolving half: corpus path, or an honest decline ───────────────────

test('M1: resolveReplacedSrc returns the corpus-relative path for a real file', async () => {
  // Resolved against the WPT mirror the extractor reads (WPT_DIR); this
  // suite uses the corpus's own smallest support image so the pin exercises
  // the real join, not a temp-dir stand-in.
  const rel = await resolveReplacedSrc(
    'support/colors-16x8.png',
    path.join(process.env.WPT_DIR ?? 'tools/wpt', 'css', 'css-images'),
  );
  // Skip-guard: hermetic checkouts have no corpus mirror (it is gitignored).
  if (rel === null) return;
  assert.equal(rel, 'css/css-images/support/colors-16x8.png');
});

test('M1: a data: source passes through verbatim — nothing to deliver', async () => {
  const v = 'data:image/png,%89%50%4e%47';
  assert.equal(await resolveReplacedSrc(v, '/anywhere'), v);
});

test('M1: remote / template / escaping / non-image / missing all DECLINE', async () => {
  const dir = await fsp.mkdtemp(path.join(os.tmpdir(), 'm1-src-'));
  await fsp.writeFile(path.join(dir, 'real.png'), Buffer.from([0x89, 0x50]));
  for (const payload of [
    'https://example.test/x.png',            // Rule 37's remote domain
    '//cdn.example.test/x.png',              // protocol-relative
    'http://{{hosts[][]}}/support/x.png',    // WPT sub-template (Rule 36)
    '#frag',                                 // same-document reference
    '',                                      // empty
  ]) {
    assert.equal(await resolveReplacedSrc(payload, dir), null, payload);
  }
  // A temp dir is outside the corpus, so even the file that EXISTS declines
  // on the containment check — which is the point: the harness route only
  // serves the corpus, so a path it cannot serve must never reach the wire.
  assert.equal(await resolveReplacedSrc('real.png', dir), null);
  await fsp.rm(dir, { recursive: true, force: true });
});

test('M1: the image-format table is closed and includes SVG', () => {
  // SVG is IN, unlike the CSS url() inliner's raster-only table: that
  // exclusion is about percent-encoding a text payload into a fixture, which
  // this path-carrying lane never does. 315×2 css-images tests are SVG.
  assert.equal(REPLACED_IMAGE_FORMATS.has('svg'), true);
  assert.equal(REPLACED_IMAGE_FORMATS.has('png'), true);
  // Not a general file table: `<object data="x.pdf">` / `<embed src="y.html">`
  // are not images and must decline rather than 404 at capture time.
  assert.equal(REPLACED_IMAGE_FORMATS.has('pdf'), false);
  assert.equal(REPLACED_IMAGE_FORMATS.has('html'), false);
  assert.equal(REPLACED_IMAGE_FORMATS.has('woff'), false);
});

// ── the fixture walk: rewrite in place, or drop + mark ──────────────────────

test('M1: an undeliverable source DROPS the key and marks its OWN lossy reason', async () => {
  const fixture = {
    _wpt: { lossy: false, lossyReasons: [] },
    components: { a: { _tag: 'img', _attrs: { src: 'support/gone.png' }, properties: {} } },
  };
  const r = await inlineFixtureAssets(fixture, '/nonexistent-base');
  // The key is gone — a fabricated path is worse than an honest absence,
  // because the harness then falls back to its documented placeholder.
  assert.equal('_attrs' in fixture.components.a, false);
  assert.equal(fixture.components.a._lossy, true);
  assert.deepEqual(fixture.components.a._lossyReasons, [REPLACED_SRC_LOSSY_REASON]);
  assert.equal(r.srcUnresolved, 1);
  assert.equal(r.srcDelivered, 0);
  // THE LOAD-BEARING ASSERTION: NOT 'requires-bundled-asset'. That tag is
  // score-EXCLUDING (inject-wpt-block's SCORE_EXCLUDED_TAGS corroborates
  // wpt-not-applicable Rule 20 against it), and Rule 20's regex already
  // matches every `<img src="support/…">` in the corpus — so reusing it here
  // would move tests OUT of the scoring denominator the moment one image
  // failed to resolve, a scored-set change dressed as an asset note.
  assert.equal(fixture._wpt.lossyReasons.includes('requires-bundled-asset'), false);
  assert.equal(fixture._wpt.lossyReasons.includes(REPLACED_SRC_LOSSY_REASON), true);
  assert.equal(fixture._wpt.lossy, true);
});

test('M1: a component whose other attrs survive keeps `_attrs` minus the src', async () => {
  const fixture = {
    _wpt: { lossy: false, lossyReasons: [] },
    components: { a: { _tag: 'img', _attrs: { src: 'gone.png', alt: 'kitten' }, properties: {} } },
  };
  await inlineFixtureAssets(fixture, '/nonexistent-base');
  assert.deepEqual(fixture.components.a._attrs, { alt: 'kitten' });
});

test('M1: the walk reaches nested children, not just top-level components', async () => {
  const fixture = {
    _wpt: { lossy: false, lossyReasons: [] },
    components: {
      a: {
        properties: {},
        children: { 'a__0': { _tag: 'img', _attrs: { src: 'gone.png' }, properties: {} } },
      },
    },
  };
  const r = await inlineFixtureAssets(fixture, '/nonexistent-base');
  assert.equal(r.srcUnresolved, 1);
  assert.equal('_attrs' in fixture.components.a.children['a__0'], false);
});

test('M1: a fixture with no replaced elements is byte-identical (no new keys)', async () => {
  const fixture = {
    _wpt: { lossy: false, lossyReasons: [] },
    components: { a: { properties: { color: 'red' } } },
  };
  const before = JSON.stringify(fixture);
  const r = await inlineFixtureAssets(fixture, '/nonexistent-base');
  assert.equal(JSON.stringify(fixture), before);
  assert.equal(r.srcDelivered, 0);
  assert.equal(r.srcUnresolved, 0);
});

// ── end to end through buildNode ────────────────────────────────────────────

test('M1: buildComponents emits `_attrs.src` beside `_tag` for all four tags', () => {
  const html = '<body>'
    + '<img src="support/a.png">'
    + '<embed src="support/b.png">'
    + '<object data="support/c.png"></object>'
    + '<video poster="support/d.png"></video>'
    + '</body>';
  const { components } = buildComponents(html, parseCss('img,embed,object,video{width:48px}'), 'm1');
  const got = Object.values(components).map((c) => [c._tag, c._attrs?.src]);
  assert.deepEqual(got, [
    ['img', 'support/a.png'],
    ['embed', 'support/b.png'],
    ['object', 'support/c.png'],
    ['video', 'support/d.png'],
  ]);
});

test('M1: the two attr lanes MERGE rather than displace each other', () => {
  // No tag is in both sets today, so this pins the merge SHAPE: a widget's
  // attrs are untouched by the new lane, and vice versa.
  const html = '<body><input type="checkbox" checked><img src="support/a.png"></body>';
  const { components } = buildComponents(html, parseCss('input,img{width:10px}'), 'm2');
  const [input, img] = Object.values(components);
  assert.deepEqual(input._attrs, { type: 'checkbox', checked: true });
  assert.deepEqual(img._attrs, { src: 'support/a.png' });
});
