/**
 * LabelChrome — the harness debug label drawn as CAPTURE CHROME, outside
 * the component's paint chain (wave 51 PR (A); normative home:
 * docs/DYNAMIC_CAPTURE.md section "Harness label chrome").
 *
 * WHY A SIBLING, NOT A DESCENDANT. Until wave 51 the label was emitted by
 * the renderer skin INSIDE the styled element (`[data-component-id]`), so
 * every CSS property on that element composited it too: `mix-blend-mode`
 * multiplied it, `opacity` faded it, `filter` recoloured it, `clip-path` /
 * `overflow:hidden` clipped it, `transform` / `zoom` rotated and scaled it,
 * and its truncation ran against the COMPONENT width — which web often
 * could not know (a padded width-less box → Infinity → the 14
 * WEB-LABEL-SPILL rows). None of that is what the label is for: it is dev
 * chrome that names the capture, and the shared spec promises byte-
 * identical rects at (8,6) on all three platforms. Drawing it here, as the
 * LAST child of the capture canvas, puts it after the whole paint chain:
 * no runtime CSS box is an ancestor of this <svg>, so nothing the
 * component declares can move, fade, clip or recolour it.
 *
 * GEOMETRY CONTRACT (shared verbatim with iOS `HarnessLabelChrome` and the
 * Compose `harnessLabelRects` drawWithContent — all three MUST agree):
 *   - origin: literal (8, 6) in the CAPTURE FRAME — the PNG's own pixel
 *     grid. The canvas is `position: relative` and the puppeteer crop is
 *     the canvas border box (capture-screenshots.mjs manifest =
 *     getBoundingClientRect per canvas, deviceScaleFactor 1), so CSS
 *     `left: 8px; top: 6px` against the canvas padding box IS PNG (8,6).
 *     Glyph rows 6..12 sit inside the canvas's 16 px top pad band, above
 *     the border box of any in-flow root with non-negative margin/top.
 *   - text: the PLAIN component name with `_` → space, then the shared
 *     normalize (uppercase, atlas-unknown → '-') inside layoutBlockLabel.
 *     No text-transform / tab-size / any runtime text pipeline — byte
 *     parity across platforms is the contract, not fidelity to CSS.
 *   - truncation: against the FRAME width — largest n with
 *     `8 + n*6 <= frameWidth - 8` → 62 glyphs at 390, 39 at 250, 0 below
 *     22 (BlockFontLabel.layoutBlockLabel). Never the component width.
 *   - colour: BLOCK_LABEL_FILL — rgba(237,237,237) at the alpha that makes
 *     Chromium's CPU raster composite (174,174,180) over the #1A1A2E
 *     ground, the byte the natives paint at alpha 179/255. That is alpha
 *     byte 180 on web, NOT 179 (Chromium lands 179 one LSB darker —
 *     BlockFontLabel.ts has the measurement); the contract is the
 *     composited byte, and LabelChrome.raster.test.tsx reads it back.
 *   - stacking: `z-index: 2147483647` (the CSS int32 max) inside the
 *     canvas's own stacking context (`transform: translateZ(0)` on the
 *     canvas — CSS Transforms 1 §3: any transform creates one), so no
 *     component `z-index` can paint over the chrome; `pointer-events:
 *     none` so the Tier-5 interaction driver's hover/click still lands on
 *     the component, never on the label sitting over its pad band.
 *
 * WHO MOUNTS IT (and when): CaptureGallery.CaptureCanvas and
 * FixtureCanvas, each gated by its OWN predicate — exactly one label per
 * capture iff the COMPOSED root has zero composed children, no non-empty
 * `text`, and the run is not WPT/composed. This component takes the
 * decision as given: it only knows how to draw.
 */

import React from 'react';
// The shared cross-platform block-font layout: origin constants, the
// pinned fill, and the pure normalize + truncate + rasterize math.
import {
  BLOCK_LABEL_FILL,
  BLOCK_LABEL_ORIGIN_X,
  BLOCK_LABEL_ORIGIN_Y,
  layoutBlockLabel,
} from '@style-converter/web/renderer/BlockFontLabel';

/**
 * The CSS `z-index` ceiling — the largest value CSS 2.1 §9.9.1 / the
 * integer grammar guarantees every engine accepts (2^31 − 1). Chromium
 * clamps larger literals to this anyway; spelling the maximum makes the
 * intent ("above every component z-index") explicit and byte-stable.
 */
const LABEL_CHROME_Z_INDEX = 2147483647;

/** Props: the two inputs the shared contract needs, and nothing else. */
export interface LabelChromeProps {
  /** The component's wire `name` — `_` → space happens here, once. */
  name: string;
  /**
   * The capture-frame width in px (CaptureGallery: CANVAS_WIDTH_PX, i.e.
   * 390 or the `?width=` override; FixtureCanvas: its literal 390). The
   * ONLY width the truncation may see — passing a component width or
   * Infinity re-opens the spill mechanism this file exists to close.
   */
  frameWidth: number;
}

/**
 * Draw one label as harness chrome. Returns null (no DOM at all) when the
 * frame is too narrow for a single glyph or the name is empty — and says
 * so on the console, because a capture that silently lost its tag is the
 * "silent fallthrough" the house rules forbid.
 */
export function LabelChrome({ name, frameWidth }: LabelChromeProps): React.ReactElement | null {
  // Shared-spec input: the plain name, underscores → spaces. Uppercasing
  // and the unknown-glyph → '-' mapping live inside layoutBlockLabel.
  const label = name.replace(/_/g, ' ');
  // Pure layout, truncated against the FRAME width (never the component).
  // Memoised on the two inputs so a capture re-render (force-state,
  // animation seize) does not re-rasterise the atlas walk.
  const layout = React.useMemo(() => layoutBlockLabel(label, frameWidth), [label, frameWidth]);
  // Nothing fits (frameWidth < 22) or the name normalised to nothing: no
  // svg. Log it — a label-less capture must be a loud decision, mirroring
  // the natives' Log.w on `truncatedCount == 0` (design C5).
  if (layout.rects.length === 0) {
    // console.warn is the harness's tracker: capture logs keep the line.
    console.warn(`[LabelChrome] no glyph fits: name="${name}" frameWidth=${frameWidth} — capture carries no label`);
    return null;
  }
  return (
    <svg
      // Marker for tests/tooling: THIS svg is the chrome, nothing else is.
      data-label-chrome=""
      // Explicit px attributes sized to the TIGHT truncated block — the svg
      // must never stretch, or the 1 px bit grid resamples (SVG 2 §8.2:
      // width/height on the outermost svg set its viewport exactly).
      width={layout.width}
      height={layout.height}
      // Keep the label string reachable for a11y/tooling (the rects carry
      // no text content the DOM could expose) — WAI-ARIA `img` + name.
      role="img"
      aria-label={label}
      // Integer-grid rendering, no antialiasing — the byte-parity point
      // (SVG 1.1 §11.7 `shape-rendering: crispEdges`).
      shapeRendering="crispEdges"
      // The pinned fill, set once here and inherited by every <rect>.
      fill={BLOCK_LABEL_FILL}
      style={{
        // display:block kills the inline baseline gap an svg would get as
        // an inline replaced element (CSS 2.1 §10.8 line-height strut).
        display: 'block',
        // Out of flow: the canvas is `position: relative`, so left/top
        // resolve against ITS padding box (CSS 2.1 §10.3.7 / §10.6.4) —
        // PNG (8,6) — and the chrome adds ZERO flow height to the canvas
        // (natural-height canvases must not grow by a label).
        position: 'absolute',
        left: BLOCK_LABEL_ORIGIN_X,
        top: BLOCK_LABEL_ORIGIN_Y,
        // Paint last, above every component stacking level — see header.
        zIndex: LABEL_CHROME_Z_INDEX,
        // Never intercept the Tier-5 driver's hover/focus/click events.
        pointerEvents: 'none',
      }}
    >
      {layout.rects.map((r, i) => (
        // One filled 1x1 px rect per set atlas bit — block-local integer
        // coords; the index key is stable because the layout is pure.
        <rect key={i} x={r.x} y={r.y} width={1} height={1} />
      ))}
    </svg>
  );
}

export default LabelChrome;
