// RootClipPathResolver.ts — the DOCUMENT-ELEMENT half of `clip-path`.
//
// ── WHY THIS EXISTS (measured, wave 49 lane A4) ────────────────────────
// css-masking-1 §5: a non-`none` `clip-path` clips the element AND its
// descendants. When that element is the DOCUMENT element the clip therefore
// covers the whole page — including the background css-backgrounds-3
// §2.11.2 propagates from the root to the canvas, which the compositing
// spec composites through the root element's own group (fxtf compositing
// §rootgroup / §pagebackdrop, both linked by the WPT test below).
//
// The TITAN extractor merges every `html` / `body` / `:root` / `*` rule into
// ONE synthetic component tagged `meta.role: 'body-root'`, and the flat IR
// v2 wire (schema/spec/03-children.md) makes that component a SIBLING of the
// document's top-level boxes rather than their parent. So a `clip-path` on
// the merged root lands on a component whose own box is empty — nothing is
// inside it to clip — and the page renders unclipped.
//
// MEASURED on the wave-48 gate, WPT
// `css-masking/clip-path/clip-path-document-element` and its
// `-will-change` twin (`html { background: red; clip-path: polygon(…) }`
// over a `div { width:500px; height:500px; background:green }`):
//   Chromium ref  green 7500 px, an "L" at image [66,66]-[165,165]; else WHITE
//   web/iOS/Android  green 187 000 px (79.91 %) + red 47 000 px (20.09 %)
// — the root clip dropped entirely on all three platforms, 0.5367 against
// the ref on every one of the six cells.
//
// ── WHAT THIS RESOLVES, AND WHERE IT IS SPENT ──────────────────────────
// This module answers only "what CSS `clip-path` value does the document
// element declare?". The composed capture canvas spends it on the box that
// IS the document element's rendering surface — the ICB div, i.e. the ref's
// render viewport — and moves the propagated canvas background onto that
// same box so the background is clipped with everything else, which is
// exactly the sentence the test's `meta name=assert` states ("Clip-path on
// the document element applies to the root background").
//
// Deliberately NOT gated on containment, unlike resolveCanvasBackground /
// resolveCanvasWritingMode / resolveCanvasDirection: css-contain-2 §2 and
// css-contain-1 §2 remove a contained root from the PROPAGATION path for
// background / writing-mode / direction. A clip-path is not propagated to
// anything — it is the root's own clip over its own subtree — so no
// containment clause touches it. (A contained root also stops propagating
// its background, so the background this value clips is then already the
// canvas default; the two rules compose without a gate.)

import type { IRDocument } from '../../../core/ir/IRModels';
import { buildStyles } from '../../../core/renderer/StyleBuilder';

/**
 * The `meta.role` marker the extractor stamps on the merged
 * html/body/:root component (schema/spec/04-metadata-fields.md).
 */
const BODY_ROOT_ROLE = 'body-root';

/**
 * The document element's used `clip-path`, as the CSS string a browser
 * would receive, or `undefined` when the document declares none.
 *
 * Resolved through the SAME `buildStyles` entry point `NodeRenderer` uses,
 * so the canvas can never disagree with what the component itself would
 * have painted — the identical "one engine hop, never a re-implemented
 * parser" rule the canvas's background / writing-mode / direction resolvers
 * already follow.
 *
 * Returns `undefined` for: no body-root, a body-root with no `ClipPath`
 * leaf, and any value the engine refuses to serialise (the extractor emits
 * no `clipPath` key at all then — no silent fallthrough, and no chance of
 * writing an invalid declaration into a live style object). `'none'` is
 * likewise refused: it is the initial value, so writing it would be a no-op
 * carrying a byte-diff risk.
 */
export function resolveRootClipPath(doc: IRDocument): string | undefined {
  // One body per document, one synthetic bag for it — the same lookup rule
  // every other root-scope resolver uses.
  const bodyRoot = doc.components.find((c) => c.meta?.role === BODY_ROOT_ROLE);
  if (!bodyRoot) return undefined;
  // One engine hop (ClipPathExtractor → ClipPathApplier), never a second
  // decoder for the same wire.
  const value = buildStyles(bodyRoot.properties).clipPath;
  if (typeof value !== 'string') return undefined;              // not declared
  const trimmed = value.trim();
  // Empty or the initial keyword ⇒ nothing to clip with.
  if (trimmed.length === 0 || trimmed.toLowerCase() === 'none') return undefined;
  return trimmed;
}
