// @vitest-environment jsdom
// @vitest-environment-options { "url": "http://localhost:3000/?mode=capture&width=20" }
//
// LabelChrome.dropped.test.tsx — the svg-less case of the harness label
// chrome (wave 51 PR (A); docs/DYNAMIC_CAPTURE.md "Harness label chrome":
// 0 glyphs below 22 px — logged once, never silent). The environment URL
// carries `?width=20`, so CaptureGallery's read-once CANVAS_WIDTH_PX is 20:
// no glyph fits (8 + 6·1 > 20 − 8), LabelChrome returns null, and the
// capture must say so two ways — a `data-label-chrome-dropped=""` marker on
// the CANVAS (greppable by tooling: a label-less capture is a decision, not
// an inference from a missing svg) and ONE console.warn per (name, frame
// width), the twin of Android's `remember(name, widthPx)` Log.w and iOS's
// PropertyTracker.logOnce — a re-render must not repeat the line.
//
// FixtureCanvas keeps its literal 390 frame regardless of `?width=`, so this
// file also pins (a) its independence from the URL and (b) the OTHER drop
// reason: a name that lays out no rect at any width (all underscores → all
// spaces → zero ink), which drops on both canvases.
//
// The once-guard is module-level (LabelChrome.tsx `warnedDrops`), so every
// test here uses names of its own; the `labelWarnings` helper counts only
// the chrome's lines, never React's.
//
// MUTATION RECORD (executed 2026-09-22 on this tree, sources restored
// sha256-exact afterwards; the polish lane's report carries the runs):
//   1. LabelChrome.tsx once-guard removed (warn on every render) → "warns
//      ONCE per (name, frameWidth)" red: 1 line on the re-render, 0 expected.
//   2. CaptureCanvas `data-label-chrome-dropped` stamp removed → both
//      CaptureGallery marker pins red (attribute null, '' expected); the
//      FixtureCanvas pins stayed green (independent stamp).
//   3. FixtureCanvas `data-label-chrome-dropped` stamp removed → the
//      all-underscore FixtureCanvas pin red; the CaptureGallery pins green.
//   4. CaptureCanvas stamp made UNCONDITIONAL → the "NOT due a label" pin
//      red (text root: expected true to be false) alongside the structural
//      file's "no marker on a labelled canvas" line (2 marked, 0 expected).
import { describe, it, expect, afterEach, vi } from 'vitest';
import { renderToStaticMarkup } from 'react-dom/server';
import type { ReactElement } from 'react';
import { CaptureGallery } from '../../src/ui/CaptureGallery';
import { FixtureCanvas } from '../../src/ui/FixtureCanvas';
import type { IRComponent, IRDocument } from '@style-converter/web/core/ir/IRModels';

/** Minimal decoded v2 document (flat list; composition via `slot`). */
function doc(components: IRComponent[]): IRDocument {
  return { irVersion: 2, minReaderVersion: 2, components } as IRDocument;
}
/** A v2 component; every test overrides id + name (the once-guard is keyed on name). */
function comp(overrides: Partial<IRComponent>): IRComponent {
  return { id: 'test-id', name: 'Test_Comp', properties: [], ...overrides };
}
/** Render into the JSDOM body so structural queries work. */
function mount(el: ReactElement): HTMLElement {
  document.body.innerHTML = renderToStaticMarkup(el);
  return document.body;
}
/** The capture canvas for one component id (flatten order is not assumed). */
function canvasOf(root: HTMLElement, id: string): HTMLElement {
  return root.querySelector<HTMLElement>(`[data-capture-canvas][data-capture-id="${id}"]`)!;
}
/** The chrome's console.warn lines emitted while `fn` runs (React's own warnings filtered out). */
function labelWarnings(fn: () => void): string[] {
  const spy = vi.spyOn(console, 'warn').mockImplementation(() => {});
  // Read the calls BEFORE restoring: vitest's mockRestore also resets them.
  try { fn(); return spy.mock.calls.map((c) => String(c[0])).filter((m) => m.startsWith('[LabelChrome]')); }
  finally { spy.mockRestore(); }
}

// A clean body between tests — the pins count elements.
afterEach(() => { document.body.innerHTML = ''; });

describe('LabelChrome — the svg-less case at ?width=20 (frame too narrow for one glyph)', () => {
  it('draws no svg and stamps data-label-chrome-dropped="" on every canvas that was DUE a label', () => {
    const root = mount(<CaptureGallery document={doc([comp({ id: 'n1', name: 'Narrow_One' }), comp({ id: 'n2', name: 'Narrow_Two' })])} />);
    // Two canvases, zero chrome anywhere: nothing fits at 20 px.
    expect(root.querySelectorAll('[data-capture-canvas]')).toHaveLength(2);
    expect(root.querySelectorAll('[data-label-chrome]')).toHaveLength(0);
    // Each was due a label (childless, textless) → each carries the marker.
    expect(canvasOf(root, 'n1').getAttribute('data-label-chrome-dropped')).toBe('');
    expect(canvasOf(root, 'n2').getAttribute('data-label-chrome-dropped')).toBe('');
  });

  it('a canvas NOT due a label (text root, container) carries no dropped marker; its leaf child does', () => {
    const parent = comp({ id: 'p', name: 'Parent_D' });
    const child = comp({ id: 'c', name: 'Child_D', slot: { parent: 'p' } });
    const root = mount(<CaptureGallery document={doc([comp({ id: 't', name: 'Text_D', text: 'x' }), parent, child])} />);
    // "dropped" means DUE-but-nothing-fits: a text root and a container are
    // unlabelled by the predicate, so they are not dropped either.
    expect(canvasOf(root, 't').hasAttribute('data-label-chrome-dropped')).toBe(false);
    expect(canvasOf(root, 'p').hasAttribute('data-label-chrome-dropped')).toBe(false);
    // The flattened leaf child WAS due one and lost it to the frame.
    expect(canvasOf(root, 'c').getAttribute('data-label-chrome-dropped')).toBe('');
  });

  it('warns ONCE per (name, frameWidth): a re-render is silent, a new name logs again', () => {
    const d = doc([comp({ id: 'w1', name: 'Warn_Once' })]);
    // First render of this (name, 20): exactly one line naming both inputs.
    const first = labelWarnings(() => mount(<CaptureGallery document={d} />));
    expect(first).toHaveLength(1);
    expect(first[0]).toContain('name="Warn_Once"');
    expect(first[0]).toContain('frameWidth=20');
    // The same (name, width) again — the re-render case (force-state,
    // animation seize, gallery hover): no second line.
    expect(labelWarnings(() => mount(<CaptureGallery document={d} />))).toHaveLength(0);
    // A different name is a new decision and logs again.
    expect(labelWarnings(() => mount(<CaptureGallery document={doc([comp({ id: 'w2', name: 'Warn_Twice' })])} />))).toHaveLength(1);
  });
});

describe('LabelChrome — FixtureCanvas (literal 390 frame) under the same URL', () => {
  it('ignores ?width=20: a normal name is labelled and carries no dropped marker', () => {
    const root = mount(<FixtureCanvas document={doc([comp({ id: 'f1', name: 'Fixture_Wide' })])} fixtureName="Fixture_Wide" />);
    const wrapper = root.querySelector<HTMLElement>('[data-testid="Fixture_Wide"]')!;
    // Its own 390 fits 12 glyphs: chrome present, marker absent.
    expect(wrapper.querySelectorAll('svg[data-label-chrome]')).toHaveLength(1);
    expect(wrapper.hasAttribute('data-label-chrome-dropped')).toBe(false);
  });

  it('an all-underscore name lays out zero ink even at 390: no svg, marker stamped, warned once', () => {
    const d = doc([comp({ id: 'f2', name: '___' })]);
    // `___` → three spaces → three atlas cells with no set bit → no rect.
    const warned = labelWarnings(() => mount(<FixtureCanvas document={d} fixtureName="___" />));
    const wrapper = document.body.querySelector<HTMLElement>('[data-testid="___"]')!;
    expect(wrapper.querySelectorAll('svg[data-label-chrome]')).toHaveLength(0);
    expect(wrapper.getAttribute('data-label-chrome-dropped')).toBe('');
    // Logged once, against the wrapper's own frame width, not the URL's.
    expect(warned).toHaveLength(1);
    expect(warned[0]).toContain('frameWidth=390');
  });
});
