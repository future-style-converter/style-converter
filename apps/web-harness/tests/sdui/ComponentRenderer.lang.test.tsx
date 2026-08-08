// ComponentRenderer.lang.test.tsx — wave-37 lane W4: THE LANG WIRE in the
// harness skin.
//
// THE CONTRACT UNDER TEST:
//   - `meta.lang` (the element's COMPUTED content language — the extractor
//     already walked HTML §3.2.6.2's own-lang → nearest-ancestor →
//     `<html>`/`<body>` ladder, see schema/spec/04-metadata-fields.md)
//     becomes a real DOM `lang` attribute on the rendered element.
//   - VERBATIM. RFC 4647 matching is case-insensitive and subtag-truncating
//     and the BROWSER does the lookup, so lowercasing here would diverge
//     from what the reference page's own attribute said.
//   - WPT capture mode ONLY. The legacy 327-pair flow never sets `?wpt=1`,
//     so every committed baseline capture keeps its exact DOM — the same
//     gate every wire-widening calibration since wave-20 shipped behind.
//
// WHY IT MATTERS, measured. The harness already maps `q` to a REAL <q>
// (TAG_ALLOWLIST), so Chromium's UA rule `q::before { content: open-quote }`
// fires — and picks its CLDR pair from the element's LANGUAGE. Without the
// attribute every `<q>` in the corpus painted the root pair, so the
// css-content quotes-004…027 family (one test per language) captured
// English “ ‘ ’ ” against Amharic « ›, French « «, Japanese 「 『 refs; and
// `font: 32px serif` under `lang="ja"` resolves to a Japanese serif face for
// the LATIN text too, which is the whole `requires-bundled-font` tag on
// quotes-014/016/018/025/026/027.
//
// WPT_MODE is a read-once module constant, so each describe stubs
// `window.location.search` and re-imports — the pattern the widgets,
// wptInk and replacedSrc suites established.

import { describe, it, expect, vi, beforeAll, afterAll } from 'vitest';
import { renderToStaticMarkup } from 'react-dom/server';
import type { ComposedNode } from '../../src/sdui/Composer';
import type { IRComponent } from '@style-converter/web/core/ir/IRModels';

const node = (component: IRComponent, children: ComposedNode[] = []): ComposedNode =>
  ({ component, children });

// The exact shape the css-content quotes family puts on the wire: a real
// inline `<q>` with text, plus the resolved content language.
const withLang = (sourceTag: string, lang?: string): IRComponent => ({
  id: 'lang-001',
  name: 'Lang_Comp',
  properties: [],
  meta: lang === undefined ? { sourceTag } : { sourceTag, lang },
});

describe('wave-37 W4 — the lang attribute (WPT mode)', () => {
  let ComponentRenderer: typeof import('../../src/sdui/ComponentRenderer').ComponentRenderer;

  // beforeALL, not beforeEach: WPT_MODE is a read-once module constant, so
  // the stub only has to precede ONE import — and every test in this
  // describe wants the same mode. Paying the module transform once keeps the
  // suite off the hook-timeout cliff on a loaded machine (the wave-20
  // widgets suite re-imports per test and costs ~30 s a hook).
  beforeAll(async () => {
    vi.stubGlobal('window', { location: { search: '?wpt=1' } });
    vi.resetModules();
    ComponentRenderer = (await import('../../src/sdui/ComponentRenderer')).ComponentRenderer;
  }, 120000);

  afterAll(() => {
    vi.unstubAllGlobals();
    vi.restoreAllMocks();
  });

  const render = (n: ComposedNode) => renderToStaticMarkup(<ComponentRenderer node={n} />);

  it('the wire language lands on the rendered element', () => {
    // <q> is in TAG_ALLOWLIST, so this is a real <q> and the browser's own
    // UA quote rule will read the attribute we just emitted.
    const out = render(node(withLang('q', 'fr')));
    expect(out).toContain('<q');
    expect(out).toContain('lang="fr"');
  });

  it('the tag is irrelevant — a <div>-demoted component carries it too', () => {
    // Font fallback is not a `q` concern: `lang` on a paragraph is what makes
    // `font: 32px serif` resolve to a Japanese face for its LATIN text.
    const out = render(node(withLang('button', 'ja')));
    expect(out).toContain('lang="ja"');
  });

  it('case and extra subtags survive verbatim — the browser does the lookup', () => {
    // quotes-035 pins that `eN-Us` and `DE-LATN-DE` are legal source
    // spellings; canonicalising here would make our DOM say something the
    // reference page's own attribute did not.
    expect(render(node(withLang('p', 'eN-Us')))).toContain('lang="eN-Us"');
    expect(render(node(withLang('p', 'DE-LATN-DE')))).toContain('lang="DE-LATN-DE"');
  });

  it('no wire language ⇒ no attribute at all (absence means default locale)', () => {
    const out = render(node(withLang('p')));
    expect(out).not.toContain('lang=');
  });

  it('a widget keeps its inert/tabIndex calibration alongside the language', () => {
    // The hook now decides two things; neither may swallow the other.
    const out = render(node(withLang('button', 'fr')));
    expect(out).toContain('lang="fr"');
    expect(out).toContain('inert=""');
  });
});

describe('wave-37 W4 — the legacy 327-pair flow is untouched', () => {
  let ComponentRenderer: typeof import('../../src/sdui/ComponentRenderer').ComponentRenderer;

  beforeAll(async () => {
    // No `?wpt=1` — the committed-baseline capture flow.
    vi.stubGlobal('window', { location: { search: '' } });
    vi.resetModules();
    ComponentRenderer = (await import('../../src/sdui/ComponentRenderer')).ComponentRenderer;
  }, 120000);

  afterAll(() => {
    vi.unstubAllGlobals();
    vi.restoreAllMocks();
  });

  it('emits no lang attribute even when the wire carries one', () => {
    // Byte-stable DOM for every committed baseline PNG: the legacy fixtures
    // have no language today, but the gate is what guarantees they cannot
    // acquire one from a future extractor change either.
    const out = renderToStaticMarkup(
      <ComponentRenderer node={node(withLang('p', 'ja'))} />,
    );
    expect(out).not.toContain('lang=');
  });
});
