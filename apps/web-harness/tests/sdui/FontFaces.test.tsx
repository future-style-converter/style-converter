// @vitest-environment jsdom
//
// FontFaces.test — JSDOM pins for the harness-side `@font-face` mount
// (schema/spec/01-envelope.md §5; wave-34 lane F2).
//
// Division of labour: the ENGINE owns per-property declarations and knows
// nothing about faces (a face is a document-level RESOURCE, not a style);
// the HARNESS owns the mount and the /wpt-font/ route that resolves the
// wire's corpus-relative path. This suite pins the harness half — the CSS
// text, the mount lifecycle, and the route-prefix agreement with
// vite.config.ts.
//
// What was broken before it: the extractor's CSS reader skips every
// at-rule, so a WPT test declaring `@font-face { font-family: test; src:
// url(resources/X.woff) }` reached the harness carrying the family NAME and
// no face — every capture fell through to bundled Inter while the
// browser-ref painted the author's file. css-text/boundary-shaping-001…010
// assert on LIGATURES only the declared face carries, so the assertion was
// unobservable on our side of the diff.

import { describe, it, expect, afterEach } from 'vitest';
import { readFileSync } from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { decodeIRDocument } from '@style-converter/web/core/ir/IRDecode';
import {
  buildFontFaceCss,
  mountFontFaces,
  formatHintFor,
  FONT_FACE_STYLE_ID,
  WPT_FONT_ROUTE,
} from '../../src/sdui/useFontFaces';

const HERE = path.dirname(fileURLToPath(import.meta.url));
const HARNESS_ROOT = path.resolve(HERE, '..', '..');

afterEach(() => {
  document.getElementById(FONT_FACE_STYLE_ID)?.remove();
});

describe('buildFontFaceCss — the emitted rule text', () => {
  it('emits one rule per face, src routed through /wpt-font/', () => {
    const css = buildFontFaceCss([
      { family: 'test', src: 'css/css-text/boundary-shaping/resources/Lin.woff' },
    ]);
    expect(css).toContain('@font-face {');
    expect(css).toContain('font-family: "test"');
    expect(css).toContain(
      'src: url("/wpt-font/css/css-text/boundary-shaping/resources/Lin.woff") format("woff")',
    );
    // font-display: block mirrors index.html's bundled-Inter rules and the
    // ref injection's embedded faces — a capture must never race a
    // fallback-face first paint, because the screenshot would then record
    // the WRONG typeface with no error anywhere.
    expect(css).toContain('font-display: block');
  });

  it('omits weight/style when the wire omitted them, emits them verbatim when present', () => {
    // Absent = the css-fonts-4 §4.4/§4.5 initial `normal`. Emitting the
    // literal instead would work by accident here but would diverge the
    // moment a consumer distinguished the two.
    const bare = buildFontFaceCss([{ family: 'A', src: 'a.woff' }]);
    expect(bare).not.toContain('font-weight');
    expect(bare).not.toContain('font-style');
    // A §4.4 weight RANGE and a §4.5 oblique ANGLE have no normalized form,
    // so they ride exactly as authored.
    const rich = buildFontFaceCss([
      { family: 'A', src: 'a.otf', weight: '400 700', style: 'oblique 20deg' },
    ]);
    expect(rich).toContain('font-weight: 400 700');
    expect(rich).toContain('font-style: oblique 20deg');
  });

  it('preserves DOCUMENT order (css-fonts-4 §4.1 last-wins)', () => {
    const css = buildFontFaceCss([
      { family: 'A', src: '1.woff' },
      { family: 'A', src: '2.woff' },
    ]);
    // Reordering would silently change which file renders for family A.
    expect(css.indexOf('1.woff')).toBeLessThan(css.indexOf('2.woff'));
  });

  it('derives format() from the extension and omits it when unknown', () => {
    expect(formatHintFor('a.woff2')).toBe('woff2');
    expect(formatHintFor('a.TTF')).toBe('truetype');
    expect(formatHintFor('a.otf')).toBe('opentype');
    expect(formatHintFor('a.ttc')).toBe('collection');
    // Unknown extension → no format() at all. A format() that DISAGREES
    // with the bytes makes Chromium skip the face silently, which is the
    // exact failure mode this channel exists to end; letting the browser
    // sniff is always legal.
    expect(formatHintFor('a.bin')).toBeNull();
    expect(buildFontFaceCss([{ family: 'A', src: 'a.bin' }])).not.toContain('format(');
  });

  it('drops traversal / absolute / schemed srcs instead of serving them', () => {
    // Defence in depth: the producer already guarantees containment, but the
    // WPT corpus is third-party text and the route resolves against the
    // corpus root. Dropping beats emitting a rule the route will refuse —
    // that would produce a silent fallback face instead of a missing one.
    expect(buildFontFaceCss([{ family: 'A', src: '../../etc/passwd.woff' }])).toBe('');
    expect(buildFontFaceCss([{ family: 'A', src: '/etc/passwd.woff' }])).toBe('');
    expect(buildFontFaceCss([{ family: 'A', src: 'https://x.example/f.woff' }])).toBe('');
    expect(buildFontFaceCss([{ family: 'A', src: 'data:font/ttf;base64,AA' }])).toBe('');
  });

  it('escapes quotes in family names and paths', () => {
    // An unescaped quote would terminate the CSS <string> early and turn
    // the remainder into arbitrary declarations.
    const css = buildFontFaceCss([{ family: 'a"b', src: 'q"uote.woff' }]);
    expect(css).toContain('font-family: "a\\"b"');
    expect(css).toContain('q\\"uote.woff');
  });

  it('returns empty for a face-free document (mounts nothing)', () => {
    expect(buildFontFaceCss(undefined)).toBe('');
    expect(buildFontFaceCss([])).toBe('');
  });
});

describe('mountFontFaces — the managed <style> lifecycle', () => {
  it('mounts once, updates in place, and REMOVES on empty', () => {
    mountFontFaces('@font-face { font-family: "A"; src: url("/wpt-font/a.woff"); }');
    const el = document.getElementById(FONT_FACE_STYLE_ID)!;
    expect(el).toBeTruthy();
    expect(el.tagName).toBe('STYLE');
    // Same text → same node, untouched. Re-writing identical text would
    // restart every fetch and, with font-display: block, re-blank the text
    // mid-capture.
    const before = el.textContent;
    mountFontFaces(before!);
    expect(document.getElementById(FONT_FACE_STYLE_ID)).toBe(el);
    // Changed text → same node, new content (no duplicate <style>).
    mountFontFaces('@font-face { font-family: "B"; src: url("/wpt-font/b.woff"); }');
    expect(document.querySelectorAll(`#${FONT_FACE_STYLE_ID}`)).toHaveLength(1);
    expect(document.getElementById(FONT_FACE_STYLE_ID)!.textContent).toContain('"B"');
    // Empty → removed, so a hot-reload swap to a face-free document cannot
    // leave a stale face registered.
    mountFontFaces('');
    expect(document.getElementById(FONT_FACE_STYLE_ID)).toBeNull();
  });
});

describe('end-to-end: raw v2 wire → decode → mounted @font-face', () => {
  it('the golden document mounts both of its faces', () => {
    // The SAME bytes the converter emits (schema/conformance/fixtures/v2/
    // font-faces.json) through the SAME decoder the harness uses.
    const golden = JSON.parse(readFileSync(
      path.resolve(HARNESS_ROOT, '..', '..', 'schema', 'conformance', 'fixtures', 'v2', 'font-faces.json'),
      'utf8',
    ));
    const doc = decodeIRDocument(golden);
    mountFontFaces(buildFontFaceCss(doc.fontFaces));
    const text = document.getElementById(FONT_FACE_STYLE_ID)!.textContent!;
    expect(text.match(/@font-face/g)).toHaveLength(2);
    expect(text).toContain('/wpt-font/css/css-text/boundary-shaping/resources/LinLibertine_Re-4.7.5.woff');
    expect(text).toContain('font-weight: 400 700');
  });
});

describe('route agreement with vite.config.ts', () => {
  it('the /wpt-font/ prefix is the SAME literal on both sides', () => {
    // The hook writes the URL and the middleware answers it. A rename that
    // lands on one side only produces a silent 404 → silent fallback face,
    // so the two literals are pinned against each other rather than trusted.
    const viteConfig = readFileSync(path.join(HARNESS_ROOT, 'vite.config.ts'), 'utf8');
    expect(viteConfig).toContain(`const WPT_FONT_ROUTE = '${WPT_FONT_ROUTE}'`);
    expect(WPT_FONT_ROUTE).toBe('/wpt-font/');
  });

  it('the route serves ONLY font extensions (not a general file reader)', () => {
    // The corpus is third-party content; a widened table would turn the
    // dev-server route into a repo-wide static endpoint.
    const viteConfig = readFileSync(path.join(HARNESS_ROOT, 'vite.config.ts'), 'utf8');
    const table = /const FONT_CONTENT_TYPES[^}]+}/.exec(viteConfig)![0];
    for (const ext of ['woff2', 'woff', 'ttf', 'otf', 'ttc', 'otc']) {
      expect(table).toContain(`${ext}:`);
    }
    for (const ext of ['png', 'html', 'js', 'json', 'css']) {
      expect(table).not.toContain(`${ext}:`);
    }
  });
});
