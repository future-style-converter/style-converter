// @vitest-environment jsdom
// @vitest-environment-options { "url": "http://localhost:3000/?mode=capture" }
//
// LabelChrome.test.tsx — the web pins for wave 51 PR (A): the harness debug
// label is CAPTURE CHROME drawn by the canvas as a SIBLING of the component
// at frame (8,6), never a descendant of the styled element, exactly one per
// childless textless root, and never emitted by the renderer skin
// (docs/DYNAMIC_CAPTURE.md section "Harness label chrome").
//
// The environment URL is `?mode=capture` with NO `wpt` and NO `width`, so
// the module-scope WPT_MODE / CANVAS_WIDTH_PX constants (CaptureGallery.tsx,
// ComponentRenderer.tsx — read ONCE from window.location at import) resolve
// to the 327-pair baseline boot: legacy flatten() flow, 390 px frame. The
// `?width=250` and `?wpt=1` boots live in their own files
// (LabelChrome.width250.test.tsx / LabelChrome.wpt.test.tsx) — one URL per
// file is the only way to vary a read-once constant.
//
// Markup is produced by renderToStaticMarkup and parsed by JSDOM so the
// pins are DOM-structural (closest / parentElement / querySelector), not
// string greps — the sibling-vs-descendant distinction IS the design.
//
// MUTATION RECORD (executed 2026-09-16 on this tree, source restored
// byte-exact afterwards — the OverflowClipTransformOrderTests.swift:34-39
// style):
//   1. `<LabelChrome>` mounted INSIDE the component element through the
//      skin's renderEmptyContent (the old label site, next to the slot
//      span) with the canvas mount removed → 7 of 11 red: "the chrome is
//      a SIBLING of the component" failed at `svg.closest('[data-
//      component-id]')` ("expected <div …> to be null" — the component
//      div), "the component element carries NO svg" failed at the
//      `[data-component-id] svg[role="img"]` query (an SVGSVGElement),
//      and the FixtureCanvas / composed-gallery / container pins went red
//      with it (the in-element label re-appeared on every leaf). Moving
//      the mount merely INSIDE <RootErrorBoundary> (still a sibling of
//      ComponentRenderer) is NOT observable here and stayed 11/11 green —
//      the boundary renders no DOM of its own (RootErrorBoundary.ts
//      "transparent passthrough"), so the DOM shape is identical.
//   2. `!WPT_MODE` dropped from CaptureCanvas.showLabel → this file stayed
//      11/11 green (no wpt here); LabelChrome.wpt.test.tsx went red.
//   3. `Infinity` passed as frameWidth → this file's "62 glyphs at 390"
//      pin red ("expected '599' to be '371'" — all 100 glyphs kept);
//      LabelChrome.width250.test.tsx red on the same run ('599' vs '233').
//   4. (fix lane, 2026-09-22) BLOCK_LABEL_FILL re-spelt `0.70196` and then
//      `0.7` → the fill STRING pin below red both times ("expected
//      'rgba(237, 237, 237, 0.70196)' to be 'rgba(237, 237, 237, 0.706)'"),
//      as is the runtimes/web BlockFontLabel.test.ts fill pin. This string
//      pin is deliberately NOT the colour gate: both spellings composite
//      to the same (173,173,179) in Chromium, so the same two mutations
//      were also run against LabelChrome.raster.test.tsx, where they turn
//      the composited byte red ((8,6) = [173,173,179] on the real gallery
//      markup) — that file's header carries the full record.
import { describe, it, expect, afterEach } from 'vitest';
import { renderToStaticMarkup } from 'react-dom/server';
import type { ReactElement } from 'react';
import { CaptureGallery } from '../../src/ui/CaptureGallery';
import { FixtureCanvas } from '../../src/ui/FixtureCanvas';
import { ComposedCaptureGallery } from '../../src/ui/ComposedCaptureGallery';
import type { IRComponent, IRDocument } from '@style-converter/web/core/ir/IRModels';

/** The chrome's marker selector — the ONLY svg the canvas may hold. */
const CHROME = 'svg[data-label-chrome]';

/** Minimal decoded v2 document (flat list; composition via `slot`). */
function doc(components: IRComponent[]): IRDocument {
  return { irVersion: 2, minReaderVersion: 2, components } as IRDocument;
}
/** A v2 component with the sibling suites' default identity. */
function comp(overrides: Partial<IRComponent> = {}): IRComponent {
  return { id: 'test-id', name: 'Test_Comp', properties: [], ...overrides };
}
/** Render into the JSDOM body so structural queries work. */
function mount(el: ReactElement): HTMLElement {
  document.body.innerHTML = renderToStaticMarkup(el);
  return document.body;
}
/** Every capture canvas in the mounted markup, in DOM order. */
function canvases(root: HTMLElement): HTMLElement[] {
  return Array.from(root.querySelectorAll<HTMLElement>('[data-capture-canvas]'));
}
/** Count distinct 6-px glyph cells the svg's rects occupy (block-local x). */
function glyphColumns(svg: Element): number {
  const cells = new Set<number>();
  svg.querySelectorAll('rect').forEach((r) => cells.add(Math.floor(Number(r.getAttribute('x')) / 6)));
  return cells.size;
}

// A clean body between tests — the pins count elements.
afterEach(() => { document.body.innerHTML = ''; });

describe('LabelChrome — one label per capture, as canvas chrome (?mode=capture)', () => {
  it('exactly one chrome svg per canvas, aria-labelled with the plain name (_ → space)', () => {
    const root = mount(<CaptureGallery document={doc([comp(), comp({ id: 'o', name: 'Other_One' })])} />);
    const cs = canvases(root);
    expect(cs).toHaveLength(2);
    // Per canvas: ONE svg carrying the marker, the a11y role and the name.
    expect(cs[0].querySelectorAll('svg[data-label-chrome][role="img"][aria-label="Test Comp"]')).toHaveLength(1);
    expect(cs[1].querySelectorAll('svg[data-label-chrome][role="img"][aria-label="Other One"]')).toHaveLength(1);
    // And no other chrome anywhere (one per capture, not one per node).
    expect(root.querySelectorAll('[data-label-chrome]')).toHaveLength(2);
  });

  it('the chrome is a SIBLING of the component, never a descendant, and paints LAST', () => {
    const root = mount(<CaptureGallery document={doc([comp()])} />);
    const canvas = canvases(root)[0];
    const svg = canvas.querySelector(CHROME)!;
    // Not under any styled element: no component CSS is an ancestor.
    expect(svg.closest('[data-component-id]')).toBeNull();
    // Directly inside the canvas — the containing block / stacking root.
    expect(svg.parentElement).toBe(canvas);
    // After the component (mounted after </RootErrorBoundary>): the last
    // child paints last in the canvas's stacking context.
    expect(canvas.lastElementChild).toBe(svg);
    expect(svg.previousElementSibling?.getAttribute('data-component-id')).toBe('test-id');
  });

  it('is pinned at frame (8,6): absolute, z-index set, pointer-inert, crisp', () => {
    const root = mount(<CaptureGallery document={doc([comp()])} />);
    const svg = canvases(root)[0].querySelector(CHROME)!;
    // React serialises the style object verbatim — assert the tokens.
    const style = svg.getAttribute('style') ?? '';
    expect(style).toContain('position:absolute');
    expect(style).toContain('left:8px');
    expect(style).toContain('top:6px');
    expect(style).toMatch(/z-index:\d+/);
    expect(style).toContain('pointer-events:none');
    expect(style).toContain('display:block');
    // Integer-grid rendering and the pinned fill SPELLING. Alpha 0.706 is
    // alpha BYTE 180 (0.706 × 255 = 180.03 under floor and round), the
    // value Chromium's CPU raster needs to composite the natives' byte
    // (174,174,180) over the ground — 179/255 (0.70196) rendered the SAME
    // (173,173,179) as the old 0.7 (wave-51 web skeptic). This string pin
    // only guards the spelling; the composited byte is pinned by
    // LabelChrome.raster.test.tsx through headless Chrome.
    expect(svg.getAttribute('shape-rendering')).toBe('crispEdges');
    expect(svg.getAttribute('fill')).toBe('rgba(237, 237, 237, 0.706)');
  });

  it('the component element carries NO svg and NO name text (the skin no longer labels)', () => {
    const root = mount(<CaptureGallery document={doc([comp()])} />);
    const canvas = canvases(root)[0];
    // The old site: an svg[role=img] under the styled element — gone.
    expect(canvas.querySelector('[data-component-id] svg[role="img"]')).toBeNull();
    const el = canvas.querySelector('[data-component-id]')!;
    // No name text node either (design C48: the branch returns null).
    expect(el.textContent).toBe('');
    // The 0-height label SLOT span survives with its byte-identical
    // geometry, so the component's auto-height does not move.
    const slot = el.querySelector('span')!;
    expect(slot).not.toBeNull();
    expect(slot.getAttribute('style')).toContain('height:0');
    expect(slot.getAttribute('style')).toContain('position:relative');
    expect(slot.getAttribute('style')).toContain('padding:0');
    expect(slot.childNodes).toHaveLength(0);
  });

  it('a root WITH text gets no chrome (the text is the content)', () => {
    const root = mount(<CaptureGallery document={doc([comp({ text: 'x' })])} />);
    const canvas = canvases(root)[0];
    expect(canvas.querySelectorAll('[data-label-chrome]')).toHaveLength(0);
    // The real text still renders inside the element.
    expect(canvas.querySelector('[data-component-id]')!.textContent).toBe('x');
  });

  it('a root WITH composed children gets no chrome; its flattened leaf keeps one', () => {
    const parent = comp({ id: 'p', name: 'Parent_1' });
    const child = comp({ id: 'c', name: 'Child_1', slot: { parent: 'p' } });
    const root = mount(<CaptureGallery document={doc([parent, child])} />);
    const cs = canvases(root);
    // Legacy flatten(): parent canvas + the child's own standalone canvas.
    expect(cs).toHaveLength(2);
    // The container is unlabelled (children.length > 0)…
    expect(cs[0].getAttribute('data-capture-id')).toBe('p');
    expect(cs[0].querySelectorAll('[data-label-chrome]')).toHaveLength(0);
    // …and the leaf's standalone capture carries its own band tag.
    expect(cs[1].getAttribute('data-capture-id')).toBe('c');
    expect(cs[1].querySelectorAll('svg[data-label-chrome][aria-label="Child 1"]')).toHaveLength(1);
  });

  it('truncates against the 390 frame: a 100-char name keeps 62 glyphs (width 371)', () => {
    const root = mount(<CaptureGallery document={doc([comp({ name: 'A'.repeat(100) })])} />);
    const svg = canvases(root)[0].querySelector(CHROME)!;
    // 8 + n*6 <= 390 - 8 → n = 62; tight width (n-1)*6 + 5 = 371, one cell tall.
    expect(svg.getAttribute('width')).toBe(String(61 * 6 + 5));
    expect(svg.getAttribute('height')).toBe('7');
    expect(glyphColumns(svg)).toBe(62);
    // The a11y name is the UNtruncated label (tooling identity).
    expect(svg.getAttribute('aria-label')).toBe('A'.repeat(100));
  });

  it('a legacy-demoted widget root is labelled at the CANVAS level (relocated widgets pin)', () => {
    // wave-20 W1 legacy flow: `<input>` demotes to <div>; the name tag that
    // ComponentRenderer.widgets.test.tsx used to find as an svg aria-label
    // INSIDE the div is now the canvas chrome.
    const widget = comp({ id: 'wg-id', name: 'Widget_Comp', meta: { sourceTag: 'input', attrs: { type: 'checkbox', checked: true } } });
    const root = mount(<CaptureGallery document={doc([widget])} />);
    const canvas = canvases(root)[0];
    expect(canvas.querySelector('input')).toBeNull();
    expect(canvas.querySelectorAll('svg[data-label-chrome][aria-label="Widget Comp"]')).toHaveLength(1);
    expect(canvas.querySelector('[data-component-id] svg')).toBeNull();
  });
});

describe('LabelChrome — FixtureCanvas (Tier-5) draws the same chrome', () => {
  it('one chrome as a sibling of the component inside the [data-testid] wrapper', () => {
    const root = mount(<FixtureCanvas document={doc([comp()])} fixtureName="Test_Comp" />);
    const wrapper = root.querySelector<HTMLElement>('[data-testid="Test_Comp"]')!;
    const svgs = wrapper.querySelectorAll('svg[data-label-chrome][role="img"][aria-label="Test Comp"]');
    expect(svgs).toHaveLength(1);
    expect(svgs[0].parentElement).toBe(wrapper);
    expect(svgs[0].closest('[data-component-id]')).toBeNull();
    expect(wrapper.lastElementChild).toBe(svgs[0]);
  });

  it('a text root or a container gets none', () => {
    const textRoot = mount(<FixtureCanvas document={doc([comp({ text: 'hello' })])} fixtureName="Test_Comp" />);
    expect(textRoot.querySelectorAll('[data-label-chrome]')).toHaveLength(0);
    const parent = comp({ id: 'p', name: 'Parent_1' });
    const child = comp({ id: 'c', name: 'Child_1', slot: { parent: 'p' } });
    const container = mount(<FixtureCanvas document={doc([parent, child])} fixtureName="Parent_1" />);
    expect(container.querySelectorAll('[data-label-chrome]')).toHaveLength(0);
  });
});

describe('LabelChrome — the composed WPT gallery never mounts it (guard)', () => {
  it('ComposedCaptureGallery emits zero [data-label-chrome]', () => {
    const composed = doc([
      { id: 'a0', name: 'wpt__css-color__test-a__0', properties: [] },
      { id: 'a1', name: 'wpt__css-color__test-a__1', properties: [] },
    ]);
    const root = mount(<ComposedCaptureGallery document={composed} />);
    expect(canvases(root).length).toBeGreaterThan(0);
    expect(root.querySelectorAll('[data-label-chrome]')).toHaveLength(0);
  });
});
