// @vitest-environment jsdom
// @vitest-environment-options { "url": "http://localhost:3000/?mode=capture" }
//
// LabelChrome.test.tsx — the width-INDEPENDENT structural pins for wave 51
// PR (A): the harness debug label is CAPTURE CHROME drawn by the canvas as a
// SIBLING of the component, never a descendant of the styled element,
// exactly one per childless textless root, and never emitted by the
// renderer skin (docs/DYNAMIC_CAPTURE.md section "Harness label chrome").
// The svg's OWN attributes — the (8,6) style tokens, crispEdges, the fill
// spelling and the 390-frame truncation geometry — are pinned by
// LabelChrome.style.test.tsx (split out 2026-09-22 to keep both files under
// the 200-line target); the svg-less case by LabelChrome.dropped.test.tsx.
//
// The environment URL is `?mode=capture` with NO `wpt` and NO `width`, so
// the module-scope WPT_MODE / CANVAS_WIDTH_PX constants (CaptureGallery.tsx,
// ComponentRenderer.tsx — read ONCE from window.location at import) resolve
// to the 327-pair baseline boot: legacy flatten() flow, 390 px frame. The
// `?width=250`, `?wpt=1` and `?width=20` boots live in their own files
// (LabelChrome.width250 / .wpt / .dropped.test.tsx) — one URL per file is
// the only way to vary a read-once constant.
//
// Markup is produced by renderToStaticMarkup and parsed by JSDOM so the
// pins are DOM-structural (closest / parentElement / querySelector), not
// string greps — the sibling-vs-descendant distinction IS the design.
//
// MUTATION RECORD (executed on this tree, source restored byte-exact
// afterwards; counts as recorded before the 2026-09-22 split):
//   1. (2026-09-16) `<LabelChrome>` mounted INSIDE the component element
//      through the skin's renderEmptyContent (the old label site, next to
//      the slot span) with the canvas mount removed → 7 of 11 red: "the
//      chrome is a SIBLING of the component" failed at `svg.closest('[data-
//      component-id]')` ("expected <div …> to be null" — the component
//      div), "the component element carries NO svg" failed at the
//      `[data-component-id] svg[role="img"]` query (an SVGSVGElement),
//      and the FixtureCanvas / composed-gallery / container pins went red
//      with it (the in-element label re-appeared on every leaf). Moving
//      the mount merely INSIDE <RootErrorBoundary> (still a sibling of
//      ComponentRenderer) is NOT observable here and stayed 11/11 green —
//      the boundary renders no DOM of its own (RootErrorBoundary.ts
//      "transparent passthrough"), so the DOM shape is identical.
//   2. (2026-09-16) `!WPT_MODE` dropped from CaptureCanvas.showLabel → this
//      file stayed green (no wpt here); LabelChrome.wpt.test.tsx went red.
//   3. (polish pass, 2026-09-22, restored sha256-exact) CaptureCanvas
//      stamping `data-label-chrome-dropped=""` UNCONDITIONALLY → the "no
//      dropped marker on a labelled canvas" line below red (2 marked, 0
//      expected) and LabelChrome.dropped.test.tsx's "NOT due a label" pin
//      red (a text root marked); its FixtureCanvas pins stayed green (the
//      wrapper stamps from its own predicate).
//   The fill / origin / Infinity mutations moved with their pins to
//   LabelChrome.style.test.tsx, whose header carries them.
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
    // The svg-less marker (LabelChrome.dropped.test.tsx) is ABSENT on a
    // labelled canvas: "dropped" and "drawn" are mutually exclusive.
    expect(root.querySelectorAll('[data-capture-canvas][data-label-chrome-dropped]')).toHaveLength(0);
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
