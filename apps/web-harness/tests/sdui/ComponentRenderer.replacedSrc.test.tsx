// ComponentRenderer.replacedSrc.test.tsx — wave-36 lane M1: the
// REPLACED-ELEMENT SOURCE lane in the harness skin.
//
// THE CONTRACT UNDER TEST:
//   - `meta.attrs.src` (a corpus-relative path the extractor delivered)
//     becomes the element's `src`, routed through the harness's
//     /wpt-image/ prefix so vite's middleware can serve it from the WPT
//     mirror. `data:` and absolute URLs pass through verbatim.
//   - WPT mode ONLY: `embed`/`object`/`video` carrying such a source map to
//     a real <img>. Chromium paints an image document for `<embed
//     src=x.png>` / `<object data=x.png>` and the poster frame for
//     `<video poster>`; an <img> with the same source and the same
//     object-fit produces the same pixels, without giving third-party
//     corpus content a nested browsing context inside the capture page
//     (those two tags are on the production renderer's DENYLISTED_TAGS).
//   - Absent wire src ⇒ the wave-9 placeholder, byte-for-byte. The
//     327-pair baseline fixtures carry no `meta.attrs.src`, so their DOM
//     must not move.
//
// WPT_MODE is a read-once module constant, so the WPT describe stubs
// `window.location.search` and re-imports — the pattern the widgets and
// wptInk suites established.

import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { renderToStaticMarkup } from 'react-dom/server';
import type { ComposedNode } from '../../src/sdui/Composer';
import type { IRComponent } from '@style-converter/web/core/ir/IRModels';

const node = (component: IRComponent, children: ComposedNode[] = []): ComposedNode =>
  ({ component, children });

// The exact shape the css-images object-fit family puts on the wire: the
// element's own object-fit/object-position plus the delivered corpus path.
const withSrc = (sourceTag: string, src?: string): IRComponent => ({
  id: 'rep-001',
  name: 'Replaced_Comp',
  properties: [
    { type: 'ObjectFit', data: 'CONTAIN' },
    { type: 'Width', data: { type: 'length', px: 48 } },
    { type: 'Height', data: { type: 'length', px: 32 } },
  ],
  meta: src === undefined ? { sourceTag } : { sourceTag, attrs: { src } },
});

const CORPUS_SRC = 'css/css-images/support/colors-16x8.png';

describe('wave-36 M1 — replaced-element source (WPT mode)', () => {
  let ComponentRenderer: typeof import('../../src/sdui/ComponentRenderer').ComponentRenderer;

  beforeEach(async () => {
    vi.stubGlobal('window', { location: { search: '?wpt=1' } });
    vi.resetModules();
    ComponentRenderer = (await import('../../src/sdui/ComponentRenderer')).ComponentRenderer;
  }, 30000);

  afterEach(() => {
    vi.unstubAllGlobals();
    vi.restoreAllMocks();
  });

  const render = (n: ComposedNode) => renderToStaticMarkup(<ComponentRenderer node={n} />);

  it('an <img> paints the delivered corpus path through /wpt-image/', () => {
    const out = render(node(withSrc('img', CORPUS_SRC)));
    // `toContain`, not `startsWith`: React 19 emits a `<link rel=preload
    // as=image>` float ahead of any <img> with a real (non-data:) src, and
    // hoists it to <head> on the client. Its presence is the renderer
    // telling us the source is a genuine fetch — exactly what we want.
    expect(out).toContain('<img');
    expect(out).toContain(`src="/wpt-image/${CORPUS_SRC}"`);
    // The placeholder must be gone — a capture that still shows the grey
    // disc is scaling the wrong image, which is the whole bug this closes.
    expect(out).not.toContain('data:image/svg+xml,');
    // The replaced-element CSS still reaches the element.
    expect(out).toContain('object-fit:contain');
  });

  it.each(['embed', 'object', 'video'])(
    'a <%s> carrying a source maps to a real <img> with that source',
    (tag) => {
      const out = render(node(withSrc(tag, CORPUS_SRC)));
      expect(out).toContain('<img');
      expect(out).toContain(`src="/wpt-image/${CORPUS_SRC}"`);
      expect(out).toContain('object-fit:contain');
      // Never the real embedding element — those are denylisted upstream.
      expect(out).not.toContain(`<${tag}`);
    },
  );

  it.each(['embed', 'object', 'video'])(
    'a <%s> with NO wire source keeps the <div> demotion (gate is the src)',
    (tag) => {
      const out = render(node(withSrc(tag)));
      expect(out.startsWith('<div')).toBe(true);
    },
  );

  it('data: and absolute sources pass through verbatim, never re-prefixed', () => {
    const data = 'data:image/png,%89%50%4e%47';
    expect(render(node(withSrc('img', data)))).toContain(`src="${data}"`);
    const remote = 'https://example.test/x.png';
    expect(render(node(withSrc('img', remote)))).toContain(`src="${remote}"`);
    expect(render(node(withSrc('img', remote)))).not.toContain('/wpt-image/https');
  });

  it('path segments are percent-encoded but separators are not', () => {
    // The corpus has spaces and parentheses in some support paths; the vite
    // route decodes with decodeURIComponent, so both halves must agree.
    const out = render(node(withSrc('img', 'css/x/a b(1).png')));
    expect(out).toContain('src="/wpt-image/css/x/a%20b(1).png"');
  });

  it('a source-less <img> still gets the deterministic placeholder', () => {
    // The fallback is what keeps a capture stable when the extractor
    // declined to deliver (remote URL, non-image format, missing file).
    expect(render(node(withSrc('img')))).toContain('src="data:image/svg+xml,');
  });
});

describe('wave-36 M1 — the legacy (non-WPT) flow is untouched', () => {
  let ComponentRenderer: typeof import('../../src/sdui/ComponentRenderer').ComponentRenderer;

  beforeEach(async () => {
    vi.stubGlobal('window', { location: { search: '' } });
    vi.resetModules();
    ComponentRenderer = (await import('../../src/sdui/ComponentRenderer')).ComponentRenderer;
  }, 30000);

  afterEach(() => {
    vi.unstubAllGlobals();
    vi.restoreAllMocks();
  });

  const render = (n: ComposedNode) => renderToStaticMarkup(<ComponentRenderer node={n} />);

  it.each(['embed', 'object', 'video'])(
    'a <%s> stays demoted to <div> even WITH a source (327-pair DOM frozen)',
    (tag) => {
      expect(render(node(withSrc(tag, CORPUS_SRC))).startsWith('<div')).toBe(true);
    },
  );

  it('an <img> with no wire src still renders the placeholder', () => {
    // Every committed 327-pair fixture is this shape — the baseline captures
    // depend on these exact bytes.
    expect(render(node(withSrc('img')))).toContain('src="data:image/svg+xml,');
  });
});
