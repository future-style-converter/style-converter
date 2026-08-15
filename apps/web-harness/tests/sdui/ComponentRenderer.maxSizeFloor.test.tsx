// wave-35 lane B9 follow-up — DOM pins for the PLACEHOLDER SIZE FLOOR vs
// the max-* suppression in apps/web-harness/src/sdui/ComponentRenderer.tsx
// (`calibrateStyles`, the minWidth/minHeight ternaries).
//
// THE BUG THIS PINS SHUT
//
// The harness sizing calibration gives every capture box `width:
// fit-content` so a web <div> hugs its content like SwiftUI/Compose
// intrinsic sizing. For a CHILDLESS component that resolves to exactly
// 0px: PlaceholderContent draws its block-font label in a `height: 0`
// span with a `position: absolute` svg (the ZERO LAYOUT FOOTPRINT
// contract), so the box has no intrinsic inline contribution at all. The
// only thing that gave such a box a visible width was the hard 50px
// placeholder floor.
//
// That floor used to be dropped whenever the IR declared ANY max-*:
//
//     (styles.maxWidth || styles.maxInlineSize) ? '0' : …
//
// which conflated two floors. The rule was written for `width: 300;
// max-width: 50`, where the SIZE-DERIVED floor (`min-width: <declared
// width>`) beats the cap — used width = max(min, min(width, max)) =
// max(300, 50) = 300. With NO declared width there is no size-derived
// floor for the cap to fight, so the suppression only zeroed the
// placeholder floor and the box vanished.
//
// It went live when lane B9 widened the engine's extractLength to read
// the `{type:'percentage', percentage:N}` wire for Max*/Min*: the
// 327-capture net's `Sizing_MaxWidthPercent` (`max-width: 80%; height:
// 50px`, no width) flipped from a 50x50 purple square — the committed
// baseline on all three platforms — to nothing at all.
//
// NATIVE PARITY is the reason the answer is "keep the floor": Compose
// gates the identical 50x30 floor on `hasExplicitWidth = any { type in
// [Width, MinWidth, InlineSize, MinInlineSize] }` and SwiftUI's
// MinBoxFloor mirrors it — MaxWidth is deliberately absent from both, so
// both natives still render the square.
//
// Every case below is the LEGACY capture path (no `?wpt=1`), which is the
// 327-pair baseline contract; WPT mode drops the floors wholesale and is
// pinned by ComponentRenderer.swarm003.test.tsx.

import { describe, it, expect, vi, beforeEach } from 'vitest';
import { renderToStaticMarkup } from 'react-dom/server';
import type { ComposedNode } from '../../src/sdui/Composer';
import type { IRComponent, IRProperty } from '@style-converter/web/core/ir/IRModels';

/** One IR property in the wire's `{type, data}` shape. */
function prop(type: string, data: unknown): IRProperty {
  return { type, data };
}

/** A childless v2 component — the shape the placeholder floor exists for. */
function makeComp(id: string, properties: IRProperty[]): IRComponent {
  return { id, name: 'Floor_Probe', properties };
}

/** Wrap into the ComposedNode the renderer consumes (no slot children). */
function makeNode(component: IRComponent): ComposedNode {
  return { component, children: [] };
}

/** The absolute-length wire shape (spec 02: everything normalises to px). */
function px(n: number) {
  return { type: 'length', px: n };
}

/** The percentage wire shape lane B9 taught extractLength to read. */
function pct(n: number) {
  return { type: 'percentage', percentage: n };
}

describe('placeholder size floor vs declared max-* (legacy capture path)', () => {
  let ComponentRenderer: typeof import('../../src/sdui/ComponentRenderer').ComponentRenderer;

  beforeEach(async () => {
    // No window stub → WPT_MODE / WPT_COMPOSED_MODE false → legacy
    // 327-pair defaults, the path every committed baseline was captured on.
    vi.unstubAllGlobals();
    vi.resetModules();
    ComponentRenderer = (await import('../../src/sdui/ComponentRenderer')).ComponentRenderer;
  }, 60_000); // ← hook timeout, NOT the default 10s. See below.
  //
  // WHY THIS HOOK NEEDS SIXTY SECONDS (wave-42 lane F4, a measured flake).
  //
  // `vi.resetModules()` drops the module cache, so EVERY iteration of this
  // hook re-imports ComponentRenderer from scratch — and that import pulls the
  // whole @style-converter/web engine (1,681 `.ts` modules under
  // runtimes/web/src) back through vite's transform. Measured on an idle
  // machine, one cold pass costs ~1.8s wall (~1.4s of it transform). That is
  // comfortably inside vitest's 10s default hookTimeout ALONE — which is
  // exactly why this looked fine in isolation and failed in CI: `npm test` at
  // the repo root runs every workspace's suite with a worker per core, so this
  // transform competes with the web runtime's own 1200+ tests for the same
  // CPUs and the same vite cache, and the hook was measured straddling the 10s
  // line under that load. A straddled timeout is the worst kind of red: it
  // fails a hook whose only sin is scheduling, and it fails DIFFERENT files run
  // to run, which reads as a real regression to whoever sees it next.
  //
  // 60s is chosen as ~30x the measured idle cost — high enough that only a
  // genuine hang (a circular import, a never-resolving dynamic import) can
  // reach it, so the timeout keeps its diagnostic value instead of becoming a
  // load gauge. The honest alternative — dropping resetModules and importing
  // once at file scope — is NOT available here: the module-level WPT_MODE
  // constant is read at import time, so the fresh import IS the mechanism that
  // pins the legacy capture path this whole file is about.

  /** Pull the inline style string off the rendered component element. */
  function styleOf(component: IRComponent): string {
    const html = renderToStaticMarkup(<ComponentRenderer node={makeNode(component)} />);
    const m = html.match(new RegExp(`data-component-id="${component.id}"[^>]*style="([^"]*)"`));
    if (!m) throw new Error(`component ${component.id} did not render`);
    return m[1];
  }

  it('keeps the 50px floor under a percentage max-width with no declared width (the wave-35 regression)', () => {
    // Sizing_MaxWidthPercent, verbatim: the cap is a ceiling (80% of the
    // 358px canvas content box = 286.4px) and never binds, so the box must
    // still land on the shared 50px placeholder minimum the natives use.
    const style = styleOf(makeComp('maxpct-1', [
      prop('MaxWidth', pct(80)),
      prop('Height', px(50)),
    ]));
    expect(style).toMatch(/max-width:\s*80%/);   // the cap survives (lane B9)
    expect(style).toMatch(/min-width:\s*50px/);  // …and so does the floor
    expect(style).toMatch(/width:\s*fit-content/); // floor is what makes it visible
  });

  it('keeps the 50px floor under an ABSOLUTE max-width with no declared width', () => {
    // Same rule, unit-agnostic: the suppression was never about the cap's
    // unit, it was about there being a competing size-derived floor.
    const style = styleOf(makeComp('maxpx-1', [prop('MaxWidth', px(300))]));
    expect(style).toMatch(/max-width:\s*300px/);
    expect(style).toMatch(/min-width:\s*50px/);
  });

  it('still drops the SIZE-DERIVED floor when width AND max-width are both declared', () => {
    // The original bug this branch was written for: `width: 300;
    // max-width: 50` must resolve to 50, so the floor must NOT become
    // min-width:300px. Regression-guards the reason the branch exists.
    const style = styleOf(makeComp('wmax-1', [
      prop('Width', px(300)),
      prop('MaxWidth', px(50)),
    ]));
    expect(style).toMatch(/min-width:\s*0px|min-width:\s*0(?![a-z0-9.])/);
    expect(style).not.toMatch(/min-width:\s*300px/);
  });

  it('keeps the 30px floor under a max-height with no declared height (block-axis twin)', () => {
    // The block axis collapses for exactly the same reason (the label span
    // contributes no height either) and Compose's hasExplicitHeight gate
    // likewise omits MaxHeight — kept in lock-step with the inline axis.
    const style = styleOf(makeComp('maxh-1', [prop('MaxHeight', pct(80))]));
    expect(style).toMatch(/max-height:\s*80%/);
    expect(style).toMatch(/min-height:\s*30px/);
  });

  it('still drops the size-derived height floor when height AND max-height are declared', () => {
    const style = styleOf(makeComp('hmax-1', [
      prop('Height', px(300)),
      prop('MaxHeight', px(50)),
    ]));
    expect(style).not.toMatch(/min-height:\s*300px/);
  });

  it('leaves the uncapped axes byte-identical to the historical floor', () => {
    // No max-* anywhere: min-width mirrors the declared width, min-height
    // the declared height — the pre-existing 327-pair behaviour.
    const style = styleOf(makeComp('plain-1', [
      prop('Width', px(120)),
      prop('Height', px(80)),
    ]));
    expect(style).toMatch(/min-width:\s*120px/);
    expect(style).toMatch(/min-height:\s*80px/);
  });

  it('lets an IR-declared min-width win over both the cap and the floor', () => {
    // Sizing_MinMax's shape (min-width:100 + max-width:300): the explicit
    // min bottoms out in the FIRST disjunct, so this component never
    // reaches the capped branch at all — which is why it is untouched by
    // the fix and its committed capture stayed byte-identical.
    const style = styleOf(makeComp('minmax-1', [
      prop('MinWidth', px(100)),
      prop('MaxWidth', px(300)),
      prop('MinHeight', px(50)),
    ]));
    expect(style).toMatch(/min-width:\s*100px/);
    expect(style).toMatch(/min-height:\s*50px/);
  });
});
