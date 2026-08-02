/**
 * ComposedCaptureGallery — the WPT COMPOSED capture mode (`?wptComposed=1`).
 *
 * WHY THIS EXISTS
 * ---------------
 * TITAN diffs each runtime's render of a WPT test against a Chromium render
 * of the WPT *reference* page — one COMPOSED image at 390px wide, WHITE
 * background (the corpus-v4 canvas), 16px padding, natural height
 * (tools/titan/capture-browser-ref.mjs).
 *
 * The legacy WPT web path (CaptureGallery + `?wpt=1`) captures each IR
 * component of a test on its OWN canvas (one PNG per root component), and
 * tools/titan/inject-wpt-block.mjs STITCHES a test's per-component PNGs
 * vertically before diffing. That vertical stitch does NOT reproduce the
 * reference page's real layout — bar heights, inter-element gaps, vertical
 * positions, and the ref's 16px body padding are all lost — so a MULTI-
 * component test scores low even when every component renders correctly
 * (e.g. css-color/background-color-hsl-001: 10 correct green bars, but the
 * stitched SSIM sits at ~0.91).
 *
 * This gallery fixes the geometry: it groups the loaded combined fixture's
 * components into per-test IR documents and renders EACH test's components
 * COMPOSED on ONE canvas — exactly the way a browser lays the reference
 * page out — then the capture script writes ONE PNG per test
 * (`<safe(testKey)>.png`) which inject-wpt-block diffs DIRECTLY against the
 * ref (no stitch). The canvas framing mirrors capture-browser-ref.mjs so
 * the two images are pixel-comparable.
 *
 * WHY WE MIRROR DocumentRenderer INSTEAD OF INSTANTIATING N OF THEM
 * ----------------------------------------------------------------
 * The composition itself is exactly what @style-converter/web's
 * DocumentRenderer does: `composeTree(doc)` → the flat root forest in
 * sibling order (spec 03) → one RootErrorBoundary per root. We reuse the
 * package's `composeTree` (the same function DocumentRenderer calls) and
 * `RootErrorBoundary`, and render each root with the harness's calibrated
 * `ComponentRenderer` (which carries HARNESS_OPTIONS — the WPT placeholder
 * suppression, sizing calibration, and sourceTag allowlist that the
 * per-component WPT path also uses, so the ONLY thing that changes between
 * the two paths is the composition geometry, never the per-node render).
 *
 * We do NOT instantiate the package DocumentRenderer component here because
 * it calls useDocumentRules, and that hook owns a SINGLE managed <style>
 * element per Document (MANAGED_STYLE_ID) — its own doc comment states
 * "render at most one IR document per page/Document at a time". This
 * gallery renders ~100 per-test documents on ONE page simultaneously (the
 * capture takes a single full-page screenshot), so N DocumentRenderers
 * would clobber each other's rules and tear them down on unmount. Instead
 * we rely on App.tsx's global `useDynamicRules(document)`, which mounts the
 * whole combined document's rule superset once — covering every per-test
 * doc's selector/media/@keyframes rules from a single mount.
 */

import React from 'react';
import type { IRComponent, IRDocument } from '@style-converter/web/core/ir/IRModels';
import { RootErrorBoundary } from '@style-converter/web/renderer/RootErrorBoundary';
// buildStyles is the SAME engine entry point NodeRenderer uses to resolve a
// component's inline CSS — reused here (GAP 2) to derive the document body's
// background color byte-identically to how it would render, so the composed
// canvas honors a page-level background exactly as the runtime paints it.
import { buildStyles } from '@style-converter/web/core/renderer/StyleBuilder';
import { composeTree } from '../sdui/Composer';
import { ComponentRenderer } from '../sdui/ComponentRenderer';

/**
 * The pipeline's default composed-canvas background — the WHITE the ref
 * canvas (capture-browser-ref.mjs's CANVAS_BG) paints on `:where(html,body)`
 * since the corpus-v4 white-canvas boundary (WPT reftests are authored
 * against the spec-default white page; white ink must vanish into it on
 * both sides of the diff — see wptCanvasStyle in CaptureGallery.tsx for
 * the full rationale). Used as the fallback when a test's document
 * declares no body background; an author `body { background }` still wins
 * (resolveCanvasBackground below), exactly as it beats the ref's
 * zero-specificity `:where()` injection.
 */
const CANVAS_BG_DEFAULT = '#FFFFFF';

/**
 * GAP 2 — BODY/ROOT BACKGROUND PROPAGATION. Resolve the background color the
 * composed canvas should paint for one per-test document.
 *
 * WHY: capture-browser-ref.mjs frames every reference page with a
 * ZERO-SPECIFICITY `:where(html, body) { background: #1A1A2E }`. Per CSS
 * Selectors L4, `:where()` contributes 0 specificity, so a reference that sets
 * its OWN `body { background: … }` (specificity 0,0,1) WINS and paints the
 * whole page that color (its `min-height:100vh` body fills the viewport).
 * css-color/a98rgb-003 is the canonical case — its ref is a full-page GREY.
 *
 * Our composed canvas hardcoded #1A1A2E, so it ignored that page background
 * and diffed a dark canvas against a grey ref (SSIM ≈ 0.38). The reader
 * captures a `body { background }` as a component tagged `meta.role:
 * 'body-root'` carrying a `BackgroundColor` property. We find that component
 * and run its properties through the same `buildStyles` engine the renderer
 * uses, then read the resolved `backgroundColor` — mirroring the ref's
 * "author body background wins over the zero-specificity default" exactly.
 * When there is no body-root, or it declares no background, we fall back to
 * the #1A1A2E default so every other test is byte-identical to before.
 */
function resolveCanvasBackground(doc: IRDocument): string {
  // The IR marks the document body with meta.role: 'body-root' (see the
  // per-test IR docs). First such component wins — a document has one body.
  const bodyRoot = doc.components.find((c) => c.meta?.role === 'body-root');
  if (!bodyRoot) return CANVAS_BG_DEFAULT;
  // Resolve via the engine so the color string matches what the runtime would
  // paint (rgba(...) / color-mix(...) / etc.), never a re-implemented parser.
  const bg = buildStyles(bodyRoot.properties).backgroundColor;
  // Only override when the body-root actually declared a background; a bare
  // body-root role marker (no BackgroundColor) keeps the pipeline default.
  return typeof bg === 'string' && bg.length > 0 ? bg : CANVAS_BG_DEFAULT;
}

/**
 * The composed canvas's image-space FRAME — capture-browser-ref.mjs's
 * CANVAS_PAD_PX. Since wave-25 CAL-RC1 the ref renders UNPADDED at 358 wide
 * and this 16px frame is memcpy'd around the finished PNG (padPngBuffer), so
 * it is unconditional: no author `body { padding }` can cancel an image-space
 * translation, it can only add an inset INSIDE the frame.
 *
 * The natives read the same number from their runtimes (Compose
 * `WPT_CANVAS_FRAME_DP`, SwiftUI `WPTCanvas.canvasFramePx`).
 */
export const CANVAS_FRAME_PX = 16;

/**
 * The ref's RENDER VIEWPORT — `capture-browser-ref.mjs`'s REF_RENDER_WIDTH
 * (390 − 2×16 = 358) and REF_RENDER_MIN_HEIGHT (600 − 2×16 = 568). This is
 * the INITIAL CONTAINING BLOCK: what `position: absolute`/`fixed` boxes with
 * no positioned ancestor anchor against (css-position-3 §3.1/§3.2), and what
 * `100vw`/`100vh` resolve to in the ref. The inner canvas div below IS this
 * box, which is how the web canvas reproduces the ref's geometry.
 */
export const ICB_WIDTH_PX = 390 - 2 * CANVAS_FRAME_PX;
export const ICB_MIN_HEIGHT_PX = 600 - 2 * CANVAS_FRAME_PX;

/**
 * The pipeline's default composed-canvas pad — the FRAME alone, i.e. what a
 * document whose body-root declares no padding gets. Kept at 16 so every such
 * capture is byte-identical to wave 24.
 */
const CANVAS_PAD_DEFAULT = CANVAS_FRAME_PX;

/** Per-side canvas pad in CSS px. */
export interface CanvasPadding {
  top: number; right: number; bottom: number; left: number;
}

/**
 * wave-24 B-RC5 — BODY/ROOT PADDING PROPAGATION. The exact twin of
 * resolveCanvasBackground above, for the other half of the ref's
 * zero-specificity body frame.
 *
 * WHY (wave 24): capture-browser-ref.mjs used to inject
 * `:where(body) { padding: 16px }`. `:where()` contributes ZERO specificity
 * (CSS Selectors L4 §17), so a ref that declared its OWN `body { padding: 0 }`
 * (specificity 0,0,1) WON and painted with no body pad at all. This canvas
 * hardcoded 16px, so every such test's whole render was offset (+16, +16)
 * against its ref — MEASURED as the ENTIRE divergence of
 * css-masking/clip-path-circle-007, whose test and ref both open with
 * `body, div { padding: 0; margin: 0 }`.
 *
 * WHY IT CHANGED (wave 25 round 3): at CAL-RC1 the ref stopped injecting a
 * body padding at all — it renders at 358 wide with `padding: 0` and the
 * 16px frame is applied to the finished PNG in IMAGE space. So the two halves
 * wave 24 conflated have SPLIT: the FRAME ([CANVAS_FRAME_PX]) is
 * unconditional, and the AUTHOR's body padding is an ADDITIONAL inset inside
 * it, defaulting to ZERO. Each side therefore resolves to `frame + declared`:
 * nothing declared → 16 (unchanged); `padding: 0` → 16 (wave 24 gave 0 — the
 * stale calibration this round repairs); `padding: 40px` → 56.
 *
 * The canvas SPENDS the two halves differently — the frame on the outer div,
 * the author part on the inner ICB div (see ComposedTestCanvas) — but this
 * resolver reports the SUM, because that sum is the cross-platform contract
 * the two native twins (`resolveComposedCanvasPadding`, `resolvedPadding`)
 * also return, and keeping the three functions comparable is what stops them
 * drifting.
 *
 * The resolution is PER SIDE, because the cascade is per-longhand: a ref
 * declaring only `padding-left: 0` leaves the other three at the bare frame.
 * Values are resolved through the same `buildStyles` engine the renderer
 * uses (never a re-implemented parser), and only CONCRETE px are honored —
 * a runtime-dependent length (`1em`, `%`, `calc()`, which the converter
 * emits as unresolved) has no absolute answer here, so that side keeps the
 * 16px default rather than silently guessing.
 *
 * No body-root, or a body-root declaring no padding ⇒ all four sides stay at
 * the bare frame (16), so those captures are byte-identical to before.
 */
export function resolveCanvasPadding(doc: IRDocument): CanvasPadding {
  const pad: CanvasPadding = {
    top: CANVAS_PAD_DEFAULT, right: CANVAS_PAD_DEFAULT,
    bottom: CANVAS_PAD_DEFAULT, left: CANVAS_PAD_DEFAULT,
  };
  // Same lookup rule as resolveCanvasBackground — a document has one body.
  const bodyRoot = doc.components.find((c) => c.meta?.role === 'body-root');
  if (!bodyRoot) return pad;
  // One engine pass; the applier emits paddingTop/… as CSS length strings.
  const styles = buildStyles(bodyRoot.properties) as Record<string, unknown>;
  const sides: Array<[keyof CanvasPadding, string]> = [
    ['top', 'paddingTop'], ['right', 'paddingRight'],
    ['bottom', 'paddingBottom'], ['left', 'paddingLeft'],
  ];
  // Wave 24 (skeptic B1) — the three composed canvases must honor the SAME
  // IR leaf shapes. The natives read the raw leaf ({px:N} or the wrapped
  // {original:{px:N}} pxFallback); buildStyles here re-emits the AUTHOR's
  // unit for wrapped leaves (correct for the engine, wrong for the canvas
  // frame, which wants the resolved px). So: engine string first, then the
  // raw-leaf fallback identical to the native resolvers.
  const irSides: Record<keyof CanvasPadding, string> = {
    top: 'PaddingTop', right: 'PaddingRight',
    bottom: 'PaddingBottom', left: 'PaddingLeft',
  };
  const leafPx = (name: string): number | null => {
    const prop = (bodyRoot.properties as Array<{ type: string; data?: unknown }>)
      .find((pr) => pr.type === name);
    const d = prop?.data as { px?: unknown; original?: { px?: unknown } } | undefined;
    if (typeof d?.px === 'number') return d.px;
    if (typeof d?.original?.px === 'number') return d.original.px;
    return null;                                     // unresolved (em/%/calc)
  };
  for (const [side, key] of sides) {
    const raw = styles[key];
    let px: number | null = null;
    if (typeof raw === 'string') {
      // Concrete px only (see doc): anything else falls through to the leaf.
      const m = /^(-?\d+(?:\.\d+)?)px$/.exec(raw.trim());
      if (m) px = parseFloat(m[1]);
    }
    if (px === null) px = leafPx(irSides[side]);     // the native-parity path
    if (px === null) continue;                       // side not resolvable
    // CSS 2.1 §8.4 forbids negative padding; clamp the AUTHOR term so a
    // malformed IR can never pull content OUTSIDE the canvas frame — then
    // stack it ON the frame, which no author rule can cancel any more.
    pad[side] = CANVAS_FRAME_PX + Math.max(0, px);
  }
  return pad;
}

interface ComposedCaptureGalleryProps {
  /** The decoded COMBINED IR document (every WPT test's components, flat). */
  document: IRDocument;
  /**
   * B-RC3 isolated-capture filter (`?only=<testKey>` via App.tsx): when
   * set, mount ONLY the test whose key equals this value — a fresh page
   * holding a single composed canvas, where headless Chromium paints the
   * spanner+abspos family correctly (the tall multi-canvas page mis-paints
   * it ~2312px off despite correct geometry). Undefined ⇒ the full gallery,
   * byte-identical to the pre-B-RC3 render.
   */
  only?: string;
}

/**
 * The test key of a ROOT component's name — ported VERBATIM from
 * tools/titan/split-combined-ir.mjs's `rootTestKey` (kept in lock-step by
 * this citation; the harness can't cleanly import a tools/ .mjs under
 * vite+TS). build-combined-fixture.mjs prefixes only ROOTS as
 * `wpt__<section>__<stem>__<idx>`, so the test key is the root name MINUS
 * the trailing `__<idx>` segment. wave-21 collision fix: this used to take
 * the FIRST THREE `__`-delimited segments, which was only correct while
 * stems never contained `__` — the subdir-encoded stems introduced by the
 * fixture-collision fix (safe-name.mjs fixtureStem, e.g.
 * `flexbox__monolithic-overflow-001.tentative`) contain `__` by design, and
 * slice(0, 3) would truncate them to the bare subdir name, merging every
 * test in that subdir into one bogus composed canvas. Stripping the one
 * trailing child-index segment is exact for BOTH shapes and byte-identical
 * to the old rule for every `__`-free stem. Non-`wpt__` roots (defensive)
 * become their own group under their raw name.
 */
function rootTestKey(rootName: string | undefined): string {
  const parts = String(rootName ?? '').split('__');
  if (parts[0] === 'wpt' && parts.length >= 4) return parts.slice(0, -1).join('__');
  return String(rootName ?? '');
}

/**
 * Resolve the test key for ANY component — ported VERBATIM from
 * tools/titan/split-combined-ir.mjs's `testKeyOf`. Flattened CHILD
 * components keep their raw extracted names (only the root carries the
 * `wpt__` prefix), so a child is grouped with its test ONLY by walking its
 * `slot.parent` chain up to the in-document root, then keying on the root's
 * name. A chain that leaves the document or cycles stops at the last
 * in-document component (treated as the root), so no component is dropped.
 */
function testKeyOf(component: IRComponent, byId: Map<string, IRComponent>): string {
  let cur: IRComponent | undefined = component;
  const seen = new Set<string>();
  // Climb while the current node has an in-document slot parent.
  while (cur?.slot?.parent && byId.has(cur.slot.parent) && !seen.has(cur.id)) {
    seen.add(cur.id);
    cur = byId.get(cur.slot.parent);
  }
  return rootTestKey(cur?.name ?? cur?.id ?? '');
}

/**
 * Group a combined IR document into per-test documents — the browser-side
 * twin of splitCombinedIr in tools/titan/split-combined-ir.mjs. First-seen
 * group order is preserved so the on-page canvas order is deterministic and
 * matches the source document order. Each emitted doc carries the v2
 * version pair, that test's components, and the document-level keyframes
 * block when the source had one (it is document-scoped, so every split doc
 * that might reference an animation keeps access to the full set).
 */
function groupByTest(combined: IRDocument): { key: string; doc: IRDocument }[] {
  const byId = new Map<string, IRComponent>();
  for (const c of combined.components) if (c.id) byId.set(c.id, c);
  const groups = new Map<string, IRComponent[]>();
  for (const c of combined.components) {
    const key = testKeyOf(c, byId);
    if (!groups.has(key)) groups.set(key, []);
    groups.get(key)!.push(c);
  }
  const out: { key: string; doc: IRDocument }[] = [];
  for (const [key, components] of groups) {
    const doc: IRDocument = { irVersion: 2, minReaderVersion: 2, components };
    if (combined.keyframes) doc.keyframes = combined.keyframes;
    out.push({ key, doc });
  }
  return out;
}

export function ComposedCaptureGallery({ document, only }: ComposedCaptureGalleryProps) {
  // Grouping is pure per document — memoise on document identity (and on
  // the isolated-capture filter, which narrows the grouped list). The
  // filter compares FULL test keys (exact equality, not prefix), so
  // `wpt__a__b` can never accidentally match `wpt__a__b-2`. A key that
  // matches nothing yields ZERO canvases and a `data-capture-ready="0"`
  // sentinel — the isolated driver asserts exactly 1 and fails loudly,
  // per the no-silent-fallthrough contract.
  const tests = React.useMemo(() => {
    const all = groupByTest(document);
    return only != null ? all.filter((t) => t.key === only) : all;
  }, [document, only]);
  return (
    <div style={containerStyle}>
      {tests.map(({ key, doc }, index) => (
        <ComposedTestCanvas key={key || index} testKey={key} doc={doc} index={index} />
      ))}
      {/* Sentinel so Puppeteer can tell when the full list has rendered —
          same contract capture-screenshots.mjs waits on. Value = the number
          of composed canvases (one per test), which the capture script
          asserts against the live `[data-capture-canvas]` count. */}
      <div data-capture-ready={tests.length} style={{ height: 0, overflow: 'hidden' }} />
    </div>
  );
}

interface ComposedTestCanvasProps {
  /** WPT test key (`wpt__<section>__<stem>`) — becomes the PNG filename. */
  testKey: string;
  /** The per-test IR document (that test's components only). */
  doc: IRDocument;
  index: number;
}

/**
 * One composed capture surface = one WPT test's whole reference-page render.
 * Mirrors DocumentRenderer.ts's body: compose the per-test doc into its
 * root forest, then render each root (in flat sibling order) inside its own
 * RootErrorBoundary via the calibrated harness ComponentRenderer. One
 * malformed component fails alone as a visible data-render-error box instead
 * of unmounting the canvas and starving the data-capture-ready sentinel.
 */
function ComposedTestCanvas({ testKey, doc, index }: ComposedTestCanvasProps) {
  // Composition is pure per document — memoise on identity (same as
  // DocumentRenderer's `useMemo(() => composeTree(doc), [doc])`).
  const roots = React.useMemo(() => composeTree(doc), [doc]);
  // GAP 2: the canvas background honors the document's body-root background
  // (falling back to #1A1A2E) so a full-page-colored ref (e.g. a98rgb-003's
  // grey page) is matched instead of diffed against a dark canvas. Pure per
  // document — memoise on identity alongside the composition above.
  const canvasBackground = React.useMemo(() => resolveCanvasBackground(doc), [doc]);
  // wave-24 B-RC5: the canvas pad honors the document's body-root padding
  // (falling back to 16px per side) so a ref that zeroes its body pad is
  // matched instead of diffed at a (+16,+16) offset — clip-path-circle-007's
  // whole divergence. Pure per document, memoised like the background.
  const canvasPadding = React.useMemo(() => resolveCanvasPadding(doc), [doc]);
  return (
    <div
      data-capture-canvas
      data-capture-index={index}
      // The capture id/name IS the test key: capture-screenshots.mjs reads
      // data-capture-name and (in composed mode) writes `<safe(name)>.png`,
      // which is exactly what inject-wpt-block.mjs's composed path globs for.
      data-capture-id={testKey}
      data-capture-name={testKey}
      // Spread the shared frame first, then supply `background` and the four
      // `padding-*` sides. wave-25 round 3: the OUTER div carries the CANVAS
      // FRAME only — the ref's image-space pad, which is the same 16px on
      // every document — while the author's own body padding rides the inner
      // ICB div below. composedCanvasStyle deliberately carries NO `padding`
      // key so the shorthand and these longhands can never both be serialised
      // into one style object (React warns on that mix, and the winner would
      // depend on key order).
      style={{
        ...composedCanvasStyle,
        background: canvasBackground,
        paddingTop: `${CANVAS_FRAME_PX}px`,
        paddingRight: `${CANVAS_FRAME_PX}px`,
        paddingBottom: `${CANVAS_FRAME_PX}px`,
        paddingLeft: `${CANVAS_FRAME_PX}px`,
      }}
    >
      {/* wave-25 round 3 — THE INITIAL CONTAINING BLOCK.
          MEASURED problem: `position: relative` + `transform` used to sit on
          the OUTER div, so an abspos/fixed child anchored at that div's
          PADDING box — the canvas corner. A probe of the live canvas CSS put
          `left:100px` at canvas x=100 and `right:0;bottom:0` at (380,590),
          i.e. flush with the IMAGE edge, while in-flow content sat at
          (16,16). That matched the pre-CAL-RC1 refs (a CSS body pad moves
          in-flow content only) and is 16px off the new ones.
          FIX: the positioning/containing-block role moves to this inner div,
          which IS the ref's render viewport — 358 x 568 minimum, offset
          (16,16) by the outer frame. The same probe then measures
          `left:100px` at (116,16), fixed at (16,16) and `right:0;bottom:0`
          at (364,574) — flush with the CONTENT edge, frame intact: exactly
          what the image-space-framed ref raster contains.
          The AUTHOR body padding rides here too (inside the frame, like the
          ref's own `body { padding }` inside its 358-wide viewport), derived
          by removing the frame term the resolver added — see
          resolveCanvasPadding for why that resolver reports the sum. */}
      <div
        data-capture-icb
        style={{
          ...composedIcbStyle,
          paddingTop: `${canvasPadding.top - CANVAS_FRAME_PX}px`,
          paddingRight: `${canvasPadding.right - CANVAS_FRAME_PX}px`,
          paddingBottom: `${canvasPadding.bottom - CANVAS_FRAME_PX}px`,
          paddingLeft: `${canvasPadding.left - CANVAS_FRAME_PX}px`,
        }}
      >
        {roots.map((root, i) => (
          <RootErrorBoundary key={root.component.id || i} componentId={root.component.id}>
            <ComponentRenderer node={root} />
          </RootErrorBoundary>
        ))}
      </div>
    </div>
  );
}

/**
 * Flat vertical list of composed canvases, WHITE (the corpus-v4 canvas —
 * this gallery is WPT-composed-only) so any sub-pixel bleed between
 * per-test crops is invisible against the white canvases. Same shape as
 * CaptureGallery's container so the single-full-page-screenshot +
 * per-canvas crop pipeline in capture-screenshots.mjs works unchanged.
 */
const containerStyle: React.CSSProperties = {
  display: 'flex',
  flexDirection: 'column',
  alignItems: 'flex-start',
  background: '#FFFFFF',
  margin: 0,
  padding: 0,
};

/**
 * Composed capture canvas — framed to match capture-browser-ref.mjs EXACTLY
 * so a composed PNG is directly diffable against the Chromium browser-ref:
 *   - width 390px            (CANVAS_WIDTH in capture-browser-ref.mjs)
 *   - background WHITE       (CANVAS_BG — the corpus-v4 white canvas the ref
 *                             sets on `:where(html,body)`; white ink vanishes
 *                             into it on BOTH sides, restoring the reftest
 *                             camouflage the dark stage broke)
 *   - padding 16px per side  (CANVAS_FRAME_PX — the ref's image-space frame;
 *                             box-sizing:border-box so the content box is the
 *                             358px viewport the ref is RENDERED at. wave-25
 *                             round 3: unconditional — an author
 *                             `body { padding }` no longer replaces it, it
 *                             insets FURTHER on the inner ICB div. See
 *                             resolveCanvasPadding + composedIcbStyle.)
 *   - min-height 600px       (the ref's `min-height:100vh` floors documentHeight
 *                             at 600 — capture-browser-ref.mjs's docHeight max;
 *                             the canvas grows past 600 when content overflows)
 *   - color #fff             (the ref keeps body color:#fff — default ink
 *                             stays the near-white harness family on every
 *                             surface, deliberately camouflaged on the white
 *                             canvas; see capture-browser-ref.mjs's injection
 *                             note for why a black-ink flip is deferred)
 * `overflow:hidden` keeps each canvas's paint confined to its own box so one
 * test can't bleed into the next crop — the same isolation CaptureGallery's
 * canvasStyle uses. wave-25 round 3: the `transform:translateZ(0)` +
 * `position:relative` half of that isolation MOVED to composedIcbStyle
 * below, because those two declarations are what make a box the containing
 * block for abspos AND fixed descendants — and that containing block must be
 * the ref's 358-wide render viewport, not the 390-wide framed image. Fixed
 * descendants are still confined (the transform still exists, one level in),
 * so the anti-bleed guarantee is unchanged.
 */
const composedCanvasStyle: React.CSSProperties = {
  width: '390px',
  minHeight: '600px',
  boxSizing: 'border-box',
  // The four `padding-*` longhands are written by ComposedTestCanvas (all
  // four = CANVAS_FRAME_PX). Keeping a shorthand `padding` here as well
  // would mix shorthand+longhand in one React style object.
  background: CANVAS_BG_DEFAULT,
  // corpus-v4.1 BLACK ink (wave-12 catch): this canvas was the ONE surface
  // the v4.1 ink flip missed — inheriting prose (NodeRenderer's bare text
  // spans) computed white-on-white and vanished from every composed web
  // capture (css-flexbox web fell to 4/12 on invisible-prose tests while
  // the prose-free pair scored 1.0000). The ref injects color:#000
  // (capture-browser-ref.mjs REF header, v4.1 sub-boundary); this canvas
  // must match. Author-declared colors still win (inline styles beat the
  // canvas inheritance).
  color: '#000',
  overflow: 'hidden',
};

/**
 * The INITIAL CONTAINING BLOCK box inside the framed canvas — wave-25
 * round 3. This div, not the canvas, is the web analogue of the ref's render
 * VIEWPORT:
 *   - 358 x 568 minimum   (ICB_WIDTH_PX / ICB_MIN_HEIGHT_PX = the ref's
 *                          REF_RENDER_WIDTH / REF_RENDER_MIN_HEIGHT; it grows
 *                          past the floor with content, like the ref's
 *                          documentHeight capture)
 *   - position:relative   (makes it the containing block for abspos
 *     + transform          descendants, and the transform additionally
 *                          captures `position: fixed` ones — CSS Transforms
 *                          §3: a transformed element is the containing block
 *                          for fixed descendants. Both moved here from the
 *                          outer canvas so out-of-flow boxes anchor at the
 *                          CONTENT corner (16,16), which is where the
 *                          image-space-framed ref raster puts them.)
 *   - transparent         (the outer canvas paints the background, so the
 *                          frame band shows the page/body colour exactly as
 *                          the ref's sampled image pad does)
 * The author body padding (if any) is written on top per side.
 */
const composedIcbStyle: React.CSSProperties = {
  width: '100%',
  minHeight: `${ICB_MIN_HEIGHT_PX}px`,
  boxSizing: 'border-box',
  position: 'relative',
  transform: 'translateZ(0)',
};

export default ComposedCaptureGallery;
