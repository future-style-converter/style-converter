/**
 * ComposedCaptureGallery — the WPT COMPOSED capture mode (`?wptComposed=1`).
 *
 * WHY THIS EXISTS
 * ---------------
 * TITAN diffs each runtime's render of a WPT test against a Chromium render
 * of the WPT *reference* page — one COMPOSED image at 390px wide, #1A1A2E
 * background, 16px padding, natural height (tools/titan/capture-browser-ref.mjs).
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
 * The pipeline's default composed-canvas background — the #1A1A2E the ref
 * canvas (capture-browser-ref.mjs's CANVAS_BG) paints on `:where(html,body)`.
 * Used as the fallback when a test's document declares no body background.
 */
const CANVAS_BG_DEFAULT = '#1A1A2E';

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

interface ComposedCaptureGalleryProps {
  /** The decoded COMBINED IR document (every WPT test's components, flat). */
  document: IRDocument;
}

/**
 * The test key of a ROOT component's name — ported VERBATIM from
 * tools/titan/split-combined-ir.mjs's `rootTestKey` (kept in lock-step by
 * this citation; the harness can't cleanly import a tools/ .mjs under
 * vite+TS). build-combined-fixture.mjs prefixes only ROOTS as
 * `wpt__<section>__<stem>__<idx>`, so the test is the first three
 * `__`-delimited segments (`wpt`, `<section>`, `<stem>`); WPT section names
 * and stems use hyphens, never `__`. Non-`wpt__` roots (defensive) become
 * their own group under their raw name.
 */
function rootTestKey(rootName: string | undefined): string {
  const parts = String(rootName ?? '').split('__');
  if (parts[0] === 'wpt' && parts.length >= 4) return parts.slice(0, 3).join('__');
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

export function ComposedCaptureGallery({ document }: ComposedCaptureGalleryProps) {
  // Grouping is pure per document — memoise on document identity.
  const tests = React.useMemo(() => groupByTest(document), [document]);
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
  return (
    <div
      data-capture-canvas
      data-capture-index={index}
      // The capture id/name IS the test key: capture-screenshots.mjs reads
      // data-capture-name and (in composed mode) writes `<safe(name)>.png`,
      // which is exactly what inject-wpt-block.mjs's composed path globs for.
      data-capture-id={testKey}
      data-capture-name={testKey}
      // Spread the shared frame first, then override just `background` with the
      // per-document resolved color (identical to the default for every test
      // that declares no body background — so those captures are unchanged).
      style={{ ...composedCanvasStyle, background: canvasBackground }}
    >
      {roots.map((root, i) => (
        <RootErrorBoundary key={root.component.id || i} componentId={root.component.id}>
          <ComponentRenderer node={root} />
        </RootErrorBoundary>
      ))}
    </div>
  );
}

/**
 * Flat vertical list of composed canvases, #1A1A2E so any sub-pixel bleed
 * between per-test crops is invisible. Same shape as CaptureGallery's
 * container so the single-full-page-screenshot + per-canvas crop pipeline in
 * capture-screenshots.mjs works unchanged.
 */
const containerStyle: React.CSSProperties = {
  display: 'flex',
  flexDirection: 'column',
  alignItems: 'flex-start',
  background: '#1A1A2E',
  margin: 0,
  padding: 0,
};

/**
 * Composed capture canvas — framed to match capture-browser-ref.mjs EXACTLY
 * so a composed PNG is directly diffable against the Chromium browser-ref:
 *   - width 390px            (CANVAS_WIDTH in capture-browser-ref.mjs)
 *   - background #1A1A2E      (CANVAS_BG — the ref sets html+body to this)
 *   - padding 16px           (CANVAS_PAD_PX — the ref's `:where(body)` pad;
 *                             box-sizing:border-box so content is 358px wide,
 *                             mirroring the ref's body content box)
 *   - min-height 600px       (the ref's `min-height:100vh` floors documentHeight
 *                             at 600 — capture-browser-ref.mjs's docHeight max;
 *                             the canvas grows past 600 when content overflows)
 *   - color #fff             (the ref sets body color:#fff)
 * `overflow:hidden` + `transform:translateZ(0)` + `position:relative` keep
 * each canvas's paint (and any position:fixed descendant) confined to its
 * own box so one test can't bleed into the next crop — the same isolation
 * CaptureGallery's canvasStyle uses. Note the KEY difference from the
 * per-component WPT canvas (wptCanvasStyle, padding:0): the ref HAS 16px
 * body padding, so the composed canvas must too — the missing 16px offset
 * was part of the stitched path's geometry error.
 */
const composedCanvasStyle: React.CSSProperties = {
  width: '390px',
  minHeight: '600px',
  boxSizing: 'border-box',
  padding: '16px',
  background: '#1A1A2E',
  color: '#fff',
  overflow: 'hidden',
  transform: 'translateZ(0)',
  position: 'relative',
};

export default ComposedCaptureGallery;
