// Tests for the parentCreatesContext predicate in
// apps/web-harness/src/ui/CaptureGallery.tsx — the swarm-002 RC1 fix that
// suppresses standalone child captures when the parent's CSS creates a
// paint context (clip-path, overflow:clip|hidden, opacity<1, mix-blend-
// mode, non-identity transform, filter, backdrop-filter, mask).
//
// Backstop guarantees:
//   - Legacy 327-pair fixtures (no nested children, no context-creating
//     properties) still flatten exactly as before.
//   - Context-creating parents from the WPT corpus (clip-002 family, the
//     backdrop-filter family) no longer leak un-contextualised child PNGs
//     into the inject-wpt-block diff path.
//
// We deliberately exercise the predicate via the flatten() walk (the
// public observable: how many captures get emitted) since the predicate
// itself is only meaningful in the context of that walk.

import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest';
import { renderToStaticMarkup } from 'react-dom/server';
import { parentCreatesContext } from '../../src/ui/CaptureGallery';
import type { IRComponent, IRProperty, IRDocument } from '@style-converter/web/core/ir/IRModels';

// Helper: shape an IRComponent with sensible defaults so each test stays
// focused on the properties that drive parentCreatesContext.
function makeComp(overrides: Partial<IRComponent> = {}): IRComponent {
  return {
    id: 'test-id',
    name: 'Test_Comp',
    properties: [],
    ...overrides,
  };
}

function prop(type: string, data: unknown): IRProperty {
  return { type, data };
}

// ── Context-creating properties (predicate must fire) ──────────────────────

describe('parentCreatesContext — true positives', () => {
  it('returns true when overflow:clip is set (clip-002 family)', () => {
    // The WPT css-overflow/clip-002 fixture sets `overflow:clip` on the
    // outer box; the inner abspos must NOT be captured standalone.
    const parent = makeComp({
      properties: [
        prop('Width', { type: 'length', px: 50 }),
        prop('OverflowX', 'clip'),
        prop('OverflowY', 'clip'),
      ],
    });
    expect(parentCreatesContext(parent)).toBe(true);
  });

  it('returns true for upper-cased overflow:CLIP (Kotlin enum serializer output)', () => {
    // The Kotlin enum serializer writes enums as upper-cased ("CLIP" /
    // "HIDDEN"), while the spec-grade longhand parser writes the literal
    // CSS value lowercased. The predicate must accept both shapes so the
    // production IR coming through the Gradle convert path (which is what
    // section-runner.sh feeds the web stack) still fires the suppression.
    // Regression: the original implementation only matched lowercase and
    // missed every css-overflow WPT capture as a result.
    const parent = makeComp({
      properties: [
        prop('OverflowX', 'CLIP'),
        prop('OverflowY', 'HIDDEN'),
      ],
    });
    expect(parentCreatesContext(parent)).toBe(true);
  });

  it('returns true when opacity is below 1', () => {
    // Sub-1 opacity creates a stacking context whose composition vs the
    // backdrop is exactly what makes the child render differ from a
    // standalone capture.
    const parent = makeComp({
      properties: [prop('Opacity', { value: 0.5 })],
    });
    expect(parentCreatesContext(parent)).toBe(true);
  });

  it('returns true for Kotlin-convert opacity shape {alpha, original}', () => {
    // The production Gradle-convert path emits Opacity as
    //   { alpha: 0.5, original: { type:'number', value:0.5 } }
    // rather than the parser's flatter `{value: 0.5}`. The predicate
    // must accept both — verified live against the clip-002 fixture's
    // {alpha:0.5, original:...} shape in the f-harness-verify run.
    const parent = makeComp({
      properties: [prop('Opacity', { alpha: 0.5, original: { type: 'number', value: 0.5 } })],
    });
    expect(parentCreatesContext(parent)).toBe(true);
  });

  it('returns true when clip-path is present', () => {
    // Any clip-path value (other than 'none', which the parser drops)
    // creates a clipping context that bounds child paint.
    const parent = makeComp({
      properties: [prop('ClipPath', { shape: 'circle', r: 25 })],
    });
    expect(parentCreatesContext(parent)).toBe(true);
  });

  it('returns true when a non-identity Transform is set', () => {
    // A non-empty transform-function list is non-identity; the parser
    // strips the `none` keyword before serialising.
    const parent = makeComp({
      properties: [prop('Transform', [{ fn: 'rotate', deg: 45 }])],
    });
    expect(parentCreatesContext(parent)).toBe(true);
  });

  it('returns true when mix-blend-mode is set to a non-normal value', () => {
    // Multiply / screen / etc. require the child to sample the backdrop
    // — standalone child capture is wrong.
    const parent = makeComp({
      properties: [prop('MixBlendMode', 'multiply')],
    });
    expect(parentCreatesContext(parent)).toBe(true);
  });

  it('returns true when backdrop-filter is present (filter-effects family)', () => {
    // The backdrop-filter WPT family relies on the parent's backdrop
    // sampling — captured standalone, the child paints over an empty
    // canvas and the diff comes out wrong.
    const parent = makeComp({
      properties: [prop('BackdropFilter', [{ fn: 'invert', v: 100 }])],
    });
    expect(parentCreatesContext(parent)).toBe(true);
  });
});

// ── Non-context properties (predicate must NOT fire) ───────────────────────

describe('parentCreatesContext — true negatives', () => {
  it('returns false for a plain box (legacy 327-pair shape)', () => {
    // Width / height / background / color — no paint-context effect on
    // children. This is the dominant case in the legacy visual-test
    // fixture; flipping this true would break the 327-pair baseline.
    const parent = makeComp({
      properties: [
        prop('Width', { type: 'length', px: 200 }),
        prop('Height', { type: 'length', px: 100 }),
        prop('BackgroundColor', { r: 1, g: 0, b: 0, a: 1 }),
        prop('Color', { r: 1, g: 1, b: 1, a: 1 }),
      ],
    });
    expect(parentCreatesContext(parent)).toBe(false);
  });

  it('returns false when overflow is visible / scroll / auto', () => {
    // Only `clip` and `hidden` clip painted content. `visible` (the
    // default), `scroll`, and `auto` do not — they MUST NOT trigger
    // child suppression or we'd lose standalone captures unnecessarily.
    for (const v of ['visible', 'scroll', 'auto']) {
      const parent = makeComp({
        properties: [prop('OverflowX', v), prop('OverflowY', v)],
      });
      expect(parentCreatesContext(parent)).toBe(false);
    }
  });

  it('returns false when opacity is 1 or higher', () => {
    // Opacity 1 is the default and does NOT create a stacking context.
    const parent = makeComp({
      properties: [prop('Opacity', { value: 1 })],
    });
    expect(parentCreatesContext(parent)).toBe(false);
  });

  it('returns false when mix-blend-mode is normal', () => {
    // Default blend mode — no compositing context.
    const parent = makeComp({
      properties: [prop('MixBlendMode', 'normal')],
    });
    expect(parentCreatesContext(parent)).toBe(false);
  });

  it('returns false when Transform is an empty list', () => {
    // The parser drops `transform: none` before serialising, but defensive
    // coverage in case a downstream stage emits an empty array.
    const parent = makeComp({
      properties: [prop('Transform', [])],
    });
    expect(parentCreatesContext(parent)).toBe(false);
  });

  it('returns false when the component has no properties', () => {
    // Edge case — a placeholder / structural component with no styles.
    expect(parentCreatesContext(makeComp({ properties: [] }))).toBe(false);
  });
});

// ── F-G-HARNESS swarm-003 Bug 2: WPT-mode viewport-sized canvas ────────────
//
// In WPT_MODE (URL `?wpt=1`) the gallery must:
//   - emit ONE canvas per TOP-LEVEL component (no flatten() walk)
//   - render the full DOM subtree inside each canvas (preserves abs-position,
//     anchor-position, 3D context, clip context, etc.)
//   - size each canvas at 390×600 minimum (matches the browser-ref viewport,
//     prevents the position:absolute root → 32px-sliver collapse documented
//     in tools/titan/investigations/swarm-003/css-masking__clip-path-
//     borderBox-1a.json LAYER 3)
//   - keep box-sizing:border-box on the WRAPPER (the index.html `body.wpt-
//     mode [data-component-id]` selector handles content-box on the IR subtree)
//
// Legacy mode (no `?wpt=1`) keeps the flatten() + natural-height behaviour.
//
// We re-import CaptureGallery dynamically per test so we can stub
// window.location.search BEFORE the module's IIFE reads it.
//
// Helper: build a minimal v2 IRDocument. The list is FLAT (IR v2 wire) —
// composition is expressed by child-side `slot` refs, which CaptureGallery
// composes internally via composeTree before flattening/rendering.
function makeDoc(components: IRComponent[]): IRDocument {
  return { irVersion: 2, minReaderVersion: 2, components } as IRDocument;
}

// Helper: a component slotted under the given parent id (Mode A entry).
function slotted(parentId: string, overrides: Partial<IRComponent>): IRComponent {
  return makeComp({ ...overrides, slot: { parent: parentId, name: 'content' } });
}

// Helper: render CaptureGallery to static markup. We use renderToStaticMarkup
// so we don't need a JSDOM environment — matches the ComponentRenderer test
// pattern at apps/web-harness/tests/sdui/ComponentRenderer.test.tsx.
async function renderGalleryWithSearch(
  search: string,
  doc: IRDocument
): Promise<string> {
  // Reset the module registry so the WPT_MODE IIFE re-runs against our
  // freshly-stubbed window.location. Without resetModules, the second
  // import returns a cached module whose WPT_MODE was already frozen.
  vi.resetModules();
  // Stub window with a fake location.search before the dynamic import.
  // `window` in vitest's default 'node' environment is undefined, so we
  // mount a minimal stand-in that satisfies `typeof window !== 'undefined'`
  // + `window.location.search`.
  vi.stubGlobal('window', { location: { search } });
  // Dynamic import so the module-level WPT_MODE IIFE evaluates AFTER the
  // window stub is in place.
  const mod = await import('../../src/ui/CaptureGallery');
  return renderToStaticMarkup(<mod.CaptureGallery document={doc} />);
}

describe('CaptureGallery — WPT_MODE viewport canvas (swarm-003 Bug 2)', () => {
  afterEach(() => {
    // Restore globals so a leftover window stub doesn't poison the next
    // describe block (parentCreatesContext tests above pass with or without
    // a window, but we want a clean slate every test).
    vi.unstubAllGlobals();
    vi.resetModules();
  });

  it('legacy mode emits ONE canvas per FLATTENED node (327-pair compat)', async () => {
    // Two top-level parents, each with one child. Legacy flatten walk emits
    // 4 captures (2 parents + 2 children). This pins the back-compat
    // contract for the 327-pair visual-test pipeline.
    const parent1 = makeComp({ id: 'p1', name: 'Parent_1' });
    const child1 = slotted('p1', { id: 'c1', name: 'Child_1' });
    const parent2 = makeComp({ id: 'p2', name: 'Parent_2' });
    const child2 = slotted('p2', { id: 'c2', name: 'Child_2' });
    const html = await renderGalleryWithSearch('', makeDoc([parent1, child1, parent2, child2]));
    // Each capture canvas carries `data-capture-canvas`. Count them.
    const canvasCount = (html.match(/data-capture-canvas/g) || []).length;
    expect(canvasCount).toBe(4);
    // Sentinel reports the canvas count so puppeteer can verify.
    expect(html).toContain('data-capture-ready="4"');
  });

  it('WPT mode emits ONE canvas per TOP-LEVEL component (no flatten)', async () => {
    // Same shape as the legacy test — but in WPT mode we emit ONLY the
    // 2 top-level canvases; children render INSIDE their parent's canvas.
    // This is the swarm-003 Bug 2 core invariant: descendants must not
    // appear as separate captures (they leak un-contextualised paint into
    // the comparator and break anchor-positioning / abs-positioning /
    // 3D-context-tree fixtures).
    const parent1 = makeComp({ id: 'p1', name: 'Parent_1' });
    const child1 = slotted('p1', { id: 'c1', name: 'Child_1' });
    const parent2 = makeComp({ id: 'p2', name: 'Parent_2' });
    const child2 = slotted('p2', { id: 'c2', name: 'Child_2' });
    const html = await renderGalleryWithSearch('?wpt=1', makeDoc([parent1, child1, parent2, child2]));
    const canvasCount = (html.match(/data-capture-canvas/g) || []).length;
    expect(canvasCount).toBe(2);
    expect(html).toContain('data-capture-ready="2"');
    // Children still appear in the DOM — they're nested inside their parent
    // canvas, not emitted as separate captures.
    expect(html).toContain('data-component-id="c1"');
    expect(html).toContain('data-component-id="c2"');
    // Each child's data-component-id must appear AFTER its parent's, proving
    // DOM nesting (essential for anchor-positioning / abs-position context).
    expect(html.indexOf('data-component-id="p1"'))
      .toBeLessThan(html.indexOf('data-component-id="c1"'));
    expect(html.indexOf('data-component-id="p2"'))
      .toBeLessThan(html.indexOf('data-component-id="c2"'));
  });

  it('WPT mode canvas uses 390×600 viewport (min-height floor)', async () => {
    // The css-masking/clip-path-borderBox-1a regression: a position:absolute
    // root with no in-flow content produced a 32-pixel-tall capture (16+16
    // padding on the legacy canvas) that cropped out the abs-positioned
    // paint at y=66+. The WPT canvas's min-height:600px floor prevents the
    // collapse — the screenshot crop reserves enough vertical space for
    // any abs-positioned descendant of the IR root to land inside the
    // captured PNG.
    const comp = makeComp({ id: 'abs', name: 'Abs_Root' });
    const html = await renderGalleryWithSearch('?wpt=1', makeDoc([comp]));
    // React inlines style strings, so the min-height shows up verbatim.
    expect(html).toContain('min-height:600px');
    expect(html).toContain('width:390px');
    // padding:0 (no 16px chrome — the body coordinate system maps 1:1 to
    // the browser-ref's coordinate system).
    expect(html).toContain('padding:0');
  });

  it('WPT mode preserves the data-capture-name + data-capture-id contract', async () => {
    // inject-wpt-block.mjs (tools/titan/inject-wpt-block.mjs L389) matches
    // captures by `_<safe(component.name)>.png`. The data-capture-name
    // attribute drives capture-screenshots.mjs's per-canvas filename, so it
    // MUST be present on the WPT-mode canvas just like the legacy canvas.
    const comp1 = makeComp({ id: 'wpt__css-foo__bar__0', name: 'wpt__css-foo__bar__0' });
    const comp2 = makeComp({ id: 'wpt__css-foo__bar__1', name: 'wpt__css-foo__bar__1' });
    const html = await renderGalleryWithSearch('?wpt=1', makeDoc([comp1, comp2]));
    expect(html).toContain('data-capture-name="wpt__css-foo__bar__0"');
    expect(html).toContain('data-capture-name="wpt__css-foo__bar__1"');
    expect(html).toContain('data-capture-id="wpt__css-foo__bar__0"');
    expect(html).toContain('data-capture-id="wpt__css-foo__bar__1"');
  });

  it('WPT mode still emits the data-capture-ready sentinel', async () => {
    // Capture-screenshots.mjs waits on `[data-capture-ready]` (L123) and
    // cross-checks the count against `[data-capture-canvas]` (L128). The
    // sentinel must be present in WPT mode too or puppeteer hangs forever.
    const html = await renderGalleryWithSearch(
      '?wpt=1',
      makeDoc([makeComp({ id: 'a', name: 'A' }), makeComp({ id: 'b', name: 'B' })])
    );
    expect(html).toContain('data-capture-ready="2"');
  });

  it('WPT mode preserves descendants under context-creating parents (no double-render)', async () => {
    // The legacy flatten() walk + parentCreatesContext suppression dropped
    // descendants of clip/blend/etc. parents to avoid leaking standalone
    // un-contextualised child captures (swarm-002 RC1). In WPT mode the
    // suppression is implicit: we never emit standalone child captures at
    // all, so the descendant is rendered EXACTLY ONCE (inside the parent's
    // canvas where the clip/blend/transform context applies).
    const parent = makeComp({
      id: 'clip-parent',
      name: 'Clip_Parent',
      // OverflowX:clip triggers parentCreatesContext in the legacy path.
      properties: [prop('OverflowX', 'clip')],
    });
    const child = slotted('clip-parent', {
      id: 'clipped-child',
      name: 'Clipped_Child',
      properties: [prop('Width', { type: 'length', px: 100 })],
    });
    const html = await renderGalleryWithSearch('?wpt=1', makeDoc([parent, child]));
    // Exactly one capture canvas (the parent).
    const canvasCount = (html.match(/data-capture-canvas/g) || []).length;
    expect(canvasCount).toBe(1);
    // The child STILL renders — inside the parent's canvas, where the
    // overflow:clip context applies (this is the fix for swarm-003
    // css-overflow / css-masking / css-anchor-position families).
    expect(html).toContain('data-component-id="clipped-child"');
    // Child appears AFTER parent in the DOM (nested).
    expect(html.indexOf('data-component-id="clip-parent"'))
      .toBeLessThan(html.indexOf('data-component-id="clipped-child"'));
  });
});

// ── dependsOnBackdrop: the mirror rule ─────────────────────────────────────
//
// `parentCreatesContext` asks "does this node's paint context own how its
// CHILDREN compose?" and suppresses the children when it does. That misses
// the mirror case: a child can be backdrop-dependent all by itself, under a
// perfectly ordinary parent.
//
// Found on fixtures/composition-test.json — `Blend_Multiply_OverGradient` has
// a plain `position:relative` parent, so the parent predicate does not fire,
// and its mix-blend-mode child was emitted standalone as `005_layer.png`.
// Composited against the bare capture canvas instead of the gradient it was
// authored over, it produced 4 divergent cross-platform pairs of pure noise.
//
// MUST stay identical to `dependsOnBackdrop` in iOS ScreenshotCaptureView.swift
// and Android ScreenshotCaptureScreen.kt — capture indices are positional, so
// a rule firing on one platform only silently misaligns every component after
// it in the comparison.

describe('dependsOnBackdrop — predicate', () => {
  it('fires on backdrop-filter (the filter has nothing to filter alone)', async () => {
    const { dependsOnBackdrop } = await import('../../src/ui/CaptureGallery');
    expect(dependsOnBackdrop(makeComp({ properties: [prop('BackdropFilter', [{ blur: 8 }])] }))).toBe(true);
  });

  it('fires on a non-normal mix-blend-mode', async () => {
    const { dependsOnBackdrop } = await import('../../src/ui/CaptureGallery');
    for (const v of ['multiply', 'screen', 'difference', 'overlay']) {
      expect(dependsOnBackdrop(makeComp({ properties: [prop('MixBlendMode', v)] })), v).toBe(true);
    }
  });

  it('accepts the Kotlin uppercase enum shape and the {value} wrapper', async () => {
    // Same UPPER/lower + bare-string/{value} variance parentCreatesContext
    // documents; a predicate that handled only one shape would fire on some
    // platforms and not others, which is the misalignment this must avoid.
    const { dependsOnBackdrop } = await import('../../src/ui/CaptureGallery');
    expect(dependsOnBackdrop(makeComp({ properties: [prop('MixBlendMode', 'MULTIPLY')] }))).toBe(true);
    expect(dependsOnBackdrop(makeComp({ properties: [prop('MixBlendMode', { value: 'SCREEN' })] }))).toBe(true);
  });

  it('does NOT fire on mix-blend-mode:normal', async () => {
    // The control component in composition-test.json. If this fired, the
    // control would be suppressed and the case/control pair — the whole
    // mechanism for telling "implemented" from "silently dropped" — is lost.
    const { dependsOnBackdrop } = await import('../../src/ui/CaptureGallery');
    expect(dependsOnBackdrop(makeComp({ properties: [prop('MixBlendMode', 'normal')] }))).toBe(false);
    expect(dependsOnBackdrop(makeComp({ properties: [prop('MixBlendMode', 'NORMAL')] }))).toBe(false);
  });

  it('does NOT fire on ordinary paint properties', async () => {
    const { dependsOnBackdrop } = await import('../../src/ui/CaptureGallery');
    expect(dependsOnBackdrop(makeComp({
      properties: [prop('BackgroundColor', '#fff'), prop('Width', 100), prop('Position', 'absolute')],
    }))).toBe(false);
    expect(dependsOnBackdrop(makeComp({ properties: [] }))).toBe(false);
  });

  it('does NOT fire on Filter (that is a parent-context property, not backdrop-dependent)', async () => {
    // `filter` transforms the element's OWN paint; it renders identically with
    // or without a backdrop, so suppressing it standalone would lose coverage.
    const { dependsOnBackdrop } = await import('../../src/ui/CaptureGallery');
    expect(dependsOnBackdrop(makeComp({ properties: [prop('Filter', [{ blur: 4 }])] }))).toBe(false);
  });
});

describe('dependsOnBackdrop — observable effect on the capture list', () => {
  it('suppresses a blend child under an ordinary parent, keeps the parent', async () => {
    const doc = makeDoc([
      makeComp({ id: 'p', name: 'Parent', properties: [prop('Position', 'relative')] }),
      slotted('p', { id: 'c', name: 'Layer', properties: [prop('MixBlendMode', 'multiply')] }),
    ]);
    const html = await renderGalleryWithSearch('?mode=capture', doc);
    // Parent still captured; the un-contextualised child is not.
    expect((html.match(/data-capture-canvas/g) || []).length).toBe(1);
  });

  it('keeps a NORMAL-blend child, so case and control stay comparable', async () => {
    const doc = makeDoc([
      makeComp({ id: 'p', name: 'Parent', properties: [prop('Position', 'relative')] }),
      slotted('p', { id: 'c', name: 'Layer', properties: [prop('MixBlendMode', 'normal')] }),
    ]);
    const html = await renderGalleryWithSearch('?mode=capture', doc);
    expect((html.match(/data-capture-canvas/g) || []).length).toBe(2);
  });

  it('leaves a zero-children document untouched — the 327-pair backstop', async () => {
    // fixtures/visual-test.json has 0 components with children, so this rule
    // provably cannot alter the legacy flow or its 363 committed baselines.
    const doc = makeDoc([
      makeComp({ id: 'a', name: 'A', properties: [prop('MixBlendMode', 'multiply')] }),
      makeComp({ id: 'b', name: 'B', properties: [prop('BackdropFilter', [{ blur: 10 }])] }),
      makeComp({ id: 'c', name: 'C', properties: [] }),
    ]);
    const html = await renderGalleryWithSearch('?mode=capture', doc);
    // All three are ROOTS. The rule only suppresses CHILDREN, never a root —
    // otherwise visual-test's BlendMode_* and Glass_Effect would vanish.
    expect((html.match(/data-capture-canvas/g) || []).length).toBe(3);
  });
});
