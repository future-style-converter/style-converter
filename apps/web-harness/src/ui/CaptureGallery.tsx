/**
 * CaptureGallery — chromeless render of every component in the IR,
 * specifically for headless screenshot capture.
 *
 * Visited via `?mode=capture`. Each component renders inside a
 * <CaptureCanvas> element with:
 *   - exactly 390 px width
 *   - natural height (no clamping)
 *   - solid #1A1A2E background (no alpha compositing)
 *   - 16 px padding on all sides
 *   - no gallery chrome (no card header / footer / border / label)
 *   - a data-capture-canvas marker with index + name for Puppeteer
 *
 * Matches the iOS `CaptureCanvas.swift` and Android `CaptureCanvas`
 * composable exactly — so captures from all three platforms are directly
 * pixel-diffable.
 */

import React from 'react';
import type { IRComponent, IRDocument } from '@style-converter/web/core/ir/IRModels';
import { ComponentRenderer } from '../sdui/ComponentRenderer';

interface CaptureGalleryProps {
  document: IRDocument;
}

/**
 * WPT-mode detector — reads the `?wpt=1` query parameter once per module load.
 *
 * Drives the swarm-003 Bug 2 fix: in WPT mode, the gallery renders ONE
 * viewport-sized canvas per top-level component (390×600 minimum, naturally
 * grown to fit content) with the full DOM tree rendered inside — rather
 * than the legacy `flatten()`-emit-one-canvas-per-descendant pattern.
 *
 * Why a separate canvas per top-level component (instead of one global
 * scene): TITAN's inject-wpt-block.mjs matches per-component captures by
 * the `_<safeKey>.png` suffix and stitches them vertically before diffing
 * against the browser-ref (see tools/titan/inject-wpt-block.mjs L389-401
 * `for (const key of matchingKeys) { ... }`). Emitting one canvas per
 * `matchingKey` preserves that match contract — the stitching step
 * short-circuits to a no-op for the single-canvas case and the diff lines
 * up byte-identically with the multi-component stitched composition.
 *
 * Why preserve descendants inside (no flatten): WPT structural tests
 * critically depend on the parent's containing block for layout
 * resolution. Anchor-positioning, position:absolute insets, percentage
 * sizing, 3D rendering contexts, position-area, ::before/::after pseudo-
 * elements — all collapse or relocate when the child is captured in
 * isolation outside the parent's coordinate space (see swarm-003
 * css-anchor-position / css-masking / css-transforms / css-overflow
 * investigations). The legacy flatten() walk emits separate captures for
 * descendants, contaminating the comparator with un-contextualised paint.
 *
 * Backward compat: legacy 327-pair flow (no `?wpt=1`) is unaffected — the
 * flatten() walk and natural-height canvases are preserved verbatim.
 * URL params don't change during a capture session so the read-once
 * pattern keeps the render loop allocation-free.
 */
const WPT_MODE: boolean = (() => {
  if (typeof window === 'undefined') return false;
  return new URLSearchParams(window.location.search).get('wpt') === '1';
})();

/**
 * WPT canvas viewport — matches the per-section capture viewport that
 * tools/titan/capture-browser-ref.mjs uses for browser-ref PNGs
 * (390×600, the iPhone-12-mini-portrait shape). Per-test browser refs
 * land at this exact size, so the SDUI canvas must match to give the
 * comparator a fair shape-to-shape diff. The canvas grows naturally
 * past 600px when content overflows (e.g. tall reftest scenes that
 * scroll), but never shrinks below — even an empty body must reserve
 * the full viewport so the diff doesn't collapse to a 32px sliver
 * (the css-masking/clip-path-borderBox-1a regression in swarm-003).
 */
const WPT_CANVAS_WIDTH_PX = 390;
const WPT_CANVAS_MIN_HEIGHT_PX = 600;

/**
 * Predicate: does this component create a paint context that its children's
 * appearance depends on?
 *
 * Returns TRUE when the parent has a property that fundamentally alters how
 * its descendants are painted — meaning a stand-alone capture of the child
 * (rendered without the parent wrapper) would NOT match what the child
 * actually looks like inside the composed scene.
 *
 * The context-creating CSS properties this predicate flags (cross-referenced
 * to the IR property type names emitted by the Kotlin converter; see
 * src/main/kotlin/app/irmodels/properties/):
 *
 *   - clip-path                → ClipPath           (effects/ClipPathProperty.kt)
 *   - mask / mask-image / etc. → Mask, MaskImage    (effects/mask/)
 *   - overflow:clip|hidden     → Overflow,OverflowX,OverflowY = 'clip'|'hidden'
 *   - mix-blend-mode (!normal) → MixBlendMode       (color/MixBlendModeProperty.kt)
 *   - non-identity transform   → Transform,Rotate,Scale,Translate (transforms/)
 *   - opacity != 1             → Opacity            (effects/OpacityProperty.kt)
 *   - filter (non-empty)       → Filter             (effects/filter/)
 *   - backdrop-filter          → BackdropFilter     (effects/filter/)
 *
 * Rationale (swarm-002 RC1 — tools/titan/investigations/swarm-002/
 * css-overflow__clip-002.json + filter-effects__backdrop-filter-clip-rect-zoom.json):
 * post-EXTFIX-A the IR fixture nests inner elements under their parent's
 * `children` map, and the renderer recurses correctly — the parent's clip /
 * blend / transform / opacity context applies to the child inside the parent's
 * <CaptureCanvas>. But the legacy flatten() walk ALSO emits a separate
 * <CaptureCanvas> per child (depth-first), so each child is captured a
 * second time standalone, WITHOUT its parent's paint context. The inject-wpt-
 * block diff then sees a leaked, un-clipped/un-blended child PNG that
 * doesn't match anything in the browser-ref → false structural-divergence.
 *
 * When this predicate fires, we suppress the standalone child captures: the
 * parent's capture already contains the visually-correct, in-context render
 * of its children. The legacy 327-pair visual-test pipeline is unaffected
 * because those fixtures don't nest components (children is always null/empty).
 */
export function parentCreatesContext(parent: IRComponent): boolean {
  // No properties → no context. Cheap guard before scanning.
  if (!parent.properties || parent.properties.length === 0) return false;
  // Walk the IR property list. We deliberately do this in one pass (rather
  // than property-by-property predicates) so the cost stays O(n) on hot capture
  // paths even for long fixtures.
  for (const p of parent.properties) {
    if (!p || typeof p.type !== 'string') continue;
    switch (p.type) {
      // ClipPath — anything other than 'none' (the IR convention is that
      // 'none' wouldn't survive into the property list at all; presence
      // already implies an active clip).
      case 'ClipPath':
      case 'Mask':
      case 'MaskImage':
      case 'Filter':
      case 'BackdropFilter':
        return true;
      // Overflow keywords: only 'clip' and 'hidden' clip painted content.
      // 'visible', 'scroll', 'auto' don't create a clipping context.
      // NOTE: The Kotlin enum serializer uppercases the value ("CLIP",
      // "HIDDEN") and the spec-grade parser lowercases it ("clip",
      // "hidden") — accept both shapes so the predicate works no matter
      // which writer produced the IR.
      case 'Overflow':
      case 'OverflowX':
      case 'OverflowY': {
        const raw = typeof p.data === 'string' ? p.data : (p.data as { value?: string } | null)?.value;
        const v = typeof raw === 'string' ? raw.toLowerCase() : null;
        if (v === 'clip' || v === 'hidden') return true;
        break;
      }
      // MixBlendMode — only non-'normal' values create a blend context.
      // Same UPPER/lower variance as overflow above; normalise before
      // comparing.
      case 'MixBlendMode': {
        const raw = typeof p.data === 'string' ? p.data : (p.data as { value?: string } | null)?.value;
        const v = typeof raw === 'string' ? raw.toLowerCase() : null;
        if (v && v !== 'normal') return true;
        break;
      }
      // Transform — IR carries an array of TransformFunction entries; any
      // non-empty list is a non-identity transform (the parser drops the
      // 'none' keyword before serialising).
      case 'Transform': {
        if (Array.isArray(p.data) && p.data.length > 0) return true;
        break;
      }
      // Rotate / Scale / Translate — individual transform-property
      // shorthands. Their mere presence in the IR means a non-default value
      // (defaults are dropped by the parser).
      case 'Rotate':
      case 'Scale':
      case 'Translate':
        return true;
      // Opacity — IR data shape varies by extractor flavor:
      //   - Kotlin convert path: { alpha: 0.5, original: {type:'number', value:0.5} }
      //   - bare-number shortcut: 0.5
      //   - parser-uniform shape: { value: 0.5 }
      // < 1 creates a stacking context that compositors render against the
      // backdrop, so the standalone-child capture would render at full
      // opacity and diverge.
      case 'Opacity': {
        const d = p.data as any;
        const v = typeof d === 'number'
          ? d
          : (d?.alpha ?? d?.value ?? d?.original?.value);
        if (typeof v === 'number' && v < 1) return true;
        break;
      }
      // All other properties — no paint-context effect on descendants.
      default:
        break;
    }
  }
  return false;
}

/**
 * Flatten nested component trees for per-component capture.
 *
 * Depth-first pre-order walk (parent first, then descendants). Children of a
 * parent that creates a paint context (see parentCreatesContext above) are
 * SUPPRESSED — they're already captured visually-correctly inside the parent's
 * canvas, and emitting them standalone produces false-positive structural-
 * divergence (swarm-002 RC1).
 *
 * Matches the iOS `flatten()` in ScreenshotCaptureView.swift and Android
 * `flattenComponents()` in ScreenshotCaptureScreen.kt — same suppression
 * rule applies on every platform so capture indices stay aligned across
 * iOS/Android/web for the inject-wpt-block diff matching.
 */
function flatten(components: IRComponent[]): IRComponent[] {
  const out: IRComponent[] = [];
  // Walker. When `suppressChildren` is true we still emit `c`, but we skip
  // its children — they are not separately captured because the visible
  // composition lives inside `c`'s own capture canvas.
  const walk = (c: IRComponent) => {
    out.push(c);
    if (!c.children || c.children.length === 0) return;
    // If this parent creates a paint context, its descendants are visually
    // dependent on the parent wrapper. Emitting them standalone leaks an
    // un-contextualised render into the comparator → drop them.
    if (parentCreatesContext(c)) return;
    c.children.forEach(walk);
  };
  components.forEach(walk);
  return out;
}

export function CaptureGallery({ document }: CaptureGalleryProps) {
  // WPT mode: skip the flatten() walk entirely and emit one viewport-sized
  // canvas per TOP-LEVEL component (preserving the inject-wpt-block.mjs
  // per-component matching contract). Descendants render inside their
  // parent's canvas via ComponentRenderer recursion — the parent's
  // containing block / clip context / 3D scene is preserved, which the
  // legacy per-descendant flatten() destroyed.
  //
  // Legacy 327-pair flow (WPT_MODE=false): unchanged — flatten + emit one
  // canvas per descendant, parentCreatesContext-suppressed where needed.
  const items = React.useMemo(
    () => (WPT_MODE ? document.components : flatten(document.components)),
    [document]
  );

  return (
    <div style={containerStyle}>
      {items.map((component, index) => (
        <CaptureCanvas key={component.id || index} component={component} index={index} />
      ))}
      {/* Sentinel so Puppeteer can tell when the full list has rendered. */}
      <div data-capture-ready={items.length} style={{ height: 0, overflow: 'hidden' }} />
    </div>
  );
}

interface CaptureCanvasProps {
  component: IRComponent;
  index: number;
}

/** Single chromeless capture surface. */
export function CaptureCanvas({ component, index }: CaptureCanvasProps) {
  // WPT mode: switch to a viewport-sized canvas (390×600 minimum) with NO
  // padding so the SDUI scene shares the browser-ref's coordinate system
  // exactly. The min-height floor prevents the
  // `position:absolute root → 0-height collapse → 32px sliver` regression
  // documented in tools/titan/investigations/swarm-003/
  // css-masking__clip-path-borderBox-1a.json (LAYER 3). The `box-sizing:
  // content-box` on the IR subtree is supplied separately by the
  // index.html `body.wpt-mode` selector (Bug 1 fix), which scopes the
  // override to `[data-component-id]` descendants only.
  const style = WPT_MODE ? wptCanvasStyle : canvasStyle;
  return (
    <div
      data-capture-canvas
      data-capture-index={index}
      data-capture-id={component.id}
      data-capture-name={component.name}
      style={style}
    >
      <ComponentRenderer component={component} />
    </div>
  );
}

/** Flat vertical list, no gaps, same background as the canvases themselves
 *  so any sub-pixel bleed around element screenshots is invisible. */
const containerStyle: React.CSSProperties = {
  display: 'flex',
  flexDirection: 'column',
  alignItems: 'flex-start',
  background: '#1A1A2E',
  margin: 0,
  padding: 0,
};

/** 390 × natural, #1A1A2E, 16 px padding — the shared capture contract.
 *  `overflow: hidden` confines each canvas's paint to its own box so a
 *  CSS `transform: rotate(...)` (or any other paint that escapes layout)
 *  on one component doesn't bleed into the screenshot of the next one
 *  in the gallery. iOS and Android already capture per-component into
 *  isolated PNGs; this matches that isolation on web.
 *
 *  Round 47 Tier 9 fix: add `transform: translateZ(0)` so this canvas
 *  becomes the containing block for any `position: fixed` descendant.
 *  Without this, real-world IR like CNN's `modal__overlay` (which uses
 *  `position: fixed; inset: 0; width: 100%; height: 100%`) covered the
 *  ENTIRE web viewport when rendered anywhere in the gallery, contaminating
 *  every other component's per-element screenshot with its semi-transparent
 *  overlay (subscribe-button captured iOS-web SSIM 0.54 due to this bleed).
 *  iOS+Android already isolate each component in its own view tree so they
 *  weren't affected. The transform creates a paint stacking context that
 *  traps `position: fixed` to the canvas — matching the iOS/Android
 *  isolation semantics for the gallery render path.
 *
 *  `translateZ(0)` is the canonical way to opt into a containing-block
 *  stacking context without affecting layout (no visible transform, but
 *  CSS spec says any `transform` value other than `none` makes the
 *  element a containing block for fixed-position descendants per
 *  https://www.w3.org/TR/css-transforms-1/#containing-block-for-all-descendants).
 */
const canvasStyle: React.CSSProperties = {
  width: '390px',
  boxSizing: 'border-box',
  padding: '16px',
  background: '#1A1A2E',
  overflow: 'hidden',
  transform: 'translateZ(0)',
  // Reset position-fixed-inside-this-canvas inset to the canvas itself.
  // Without this, fixed children would be relative to the canvas at top:0
  // — fine for our use case.
  position: 'relative',
};

/**
 * WPT capture canvas — viewport-sized (390×600 minimum, naturally grown
 * by content) with ZERO padding so the SDUI coordinate system exactly
 * matches the browser-ref viewport. Used only when WPT_MODE is true
 * (`?wpt=1` URL param). See the module-level WPT_MODE doc above for the
 * full rationale.
 *
 * - `minHeight: 600px` — guarantees the screenshot crop never collapses
 *   when the IR root is position:absolute with no in-flow descendants
 *   (the css-masking/clip-path-borderBox-1a regression). The canvas
 *   grows past 600px when content overflows (tall scenes / scrolling).
 * - `padding: 0` — the browser-ref captures from the body's top-left
 *   without any chrome offset, so SDUI matches.
 * - `boxSizing: border-box` is fine on the WRAPPER itself; the
 *   index.html `body.wpt-mode [data-component-id]` selector switches
 *   the IR subtree to content-box, restoring the WPT spec default.
 * - `transform: translateZ(0)` + `position: relative` retained for the
 *   same Round-47 Tier-9 fixed-position-isolation guarantee — but with
 *   600px+ height there's no abs-positioned-child cropping issue.
 * - `overflow: hidden` retained so a scene that overflows the canvas
 *   bottom doesn't bleed into the next captured component's screenshot.
 */
const wptCanvasStyle: React.CSSProperties = {
  width: `${WPT_CANVAS_WIDTH_PX}px`,
  minHeight: `${WPT_CANVAS_MIN_HEIGHT_PX}px`,
  boxSizing: 'border-box',
  padding: 0,
  background: '#1A1A2E',
  overflow: 'hidden',
  transform: 'translateZ(0)',
  position: 'relative',
};

export default CaptureGallery;
