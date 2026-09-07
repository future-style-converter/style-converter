package com.styleconverter.runtime.effects.clip

// RootCanvasClip — the DOCUMENT-ELEMENT half of `clip-path` on Compose.
// Twin of the web runtime's RootClipPathResolver.ts and of SwiftUI's
// RootCanvasClip.swift; read that file's banner for the full measurement.
//
// ── THE DEFECT (measured, wave 49 lane A4) ─────────────────────────────
// css-masking-1 §5: a non-`none` `clip-path` clips the element AND its
// descendants. On the DOCUMENT element that covers the whole page, the
// background css-backgrounds-3 §2.11.1 propagates to the canvas included —
// the fxtf compositing §rootgroup / §pagebackdrop chain the WPT test links,
// whose `meta name=assert` states it outright: "Clip-path on the document
// element applies to the root background."
//
// The TITAN extractor merges every `html` / `body` / `:root` / `*` rule into
// ONE synthetic `meta.role: "body-root"` component, and the flat IR v2 wire
// makes it a SIBLING of the document's top-level boxes, not their parent. So
// the root's clip-path landed on an empty box with nothing inside it to clip.
// MEASURED on the wave-48 gate, WPT css-masking/clip-path/
// clip-path-document-element and its `-will-change` twin:
//   Chromium ref     green 7500 px, an "L" at image [66,66]-[165,165]; else WHITE
//   all 3 platforms  green 187 000 px (79.91 %) + red 47 000 px (20.09 %)
// — 0.5367 against the ref on all six cells.
//
// ── WHAT THIS FILE PROVIDES ────────────────────────────────────────────
// [rootCanvasClipConfig] answers "does the document element declare a clip?"
// (pure, JVM-pinned), and [rootCanvasClipShape] turns it into the Compose
// [Shape] the composed capture canvas clips with. The canvas spends it as
//     .background(WPT canvas default)   ← the page backdrop, UNCLIPPED
//     .clip(rootCanvasClipShape(…))     ← css-masking-1 §5 root clip
//     .background(propagated root bg)   ← now INSIDE the clip
// so the propagated background and the whole root forest are clipped by one
// region and everything outside it is the canvas default, exactly as the ref
// raster has it.
//
// ── WHY THE FRAME TRANSLATION ──────────────────────────────────────────
// The clip's lengths are measured from the ROOT ELEMENT's border box, which
// is the INITIAL CONTAINING BLOCK — the framed content corner, not the image
// corner (CanvasRootHoist.Host's `canvasFrame` doc has the wave-25 CAL-RC1
// history of that distinction). The composed canvas Box the clip rides is
// the OUTER, framed surface, so the shape is built against the ICB extent
// and then translated by the frame. Verified against the frozen ref by
// construction: polygon(50px 50px …) must land at image (66,66), and the
// ref's ink bounding box is exactly [66,66]-[165,165].

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import com.styleconverter.runtime.core.ir.IRComponent

/** The `meta.role` marker the extractor stamps on the merged root bag. */
private const val BODY_ROOT_ROLE = "body-root"

/**
 * The document element's `clip-path`, or null when it declares none.
 *
 * Read through the SAME [ClipPathExtractor] the live style chain uses, so
 * the canvas can never disagree with what a component would have clipped
 * with — the one-engine-hop rule `resolveComposedCanvasBackground` and its
 * writing-mode / direction siblings already follow.
 *
 * Deliberately NOT gated on containment: css-contain-2 §2 and
 * css-contain-1 §2 remove a contained root from the PROPAGATION path for
 * background / writing-mode / direction, but a clip-path is not propagated
 * to anything — it is the root's own clip over its own subtree.
 *
 * Null for: no body-root, no `ClipPath` leaf, and any value the extractor
 * refuses (then `hasClipPath` is false and no clip would have been painted
 * anywhere — predicate and applier share one decoder for exactly that
 * reason).
 */
fun rootCanvasClipConfig(roots: List<IRComponent>): ClipPathConfig? {
    // One body per document, one synthetic bag for it.
    val bodyRoot = roots.firstOrNull { it.role == BODY_ROOT_ROLE } ?: return null
    // GUARDED (wave 49 lane F1). This hop runs once per composed capture
    // canvas, inside the canvas's own modifier chain — i.e. at composition
    // time, with no error boundary above it in apps/android-harness. The
    // identical unguarded pattern one layer over (CanvasRootHoist's clip
    // ancestry predicate) is what killed 3 of the 1435 wave-48 corpus
    // documents when lane A4 introduced it. No corpus document carries a
    // malformed ROOT clip today — the only two body-roots with any ClipPath
    // are the polygon-clipped document-element pair — so this changes no
    // measured behaviour; it removes the failure mode.
    val config = clipDecodeOrElse<ClipPathConfig?>(
        where = "rootCanvasClipConfig",
        // A root clip this decoder cannot read paints no clip anywhere, so
        // "no page clip" is the truthful degradation: the canvas keeps its
        // wave-25 chain byte for byte, exactly as for the 1433 documents
        // that declare no root clip at all.
        fallback = null,
    ) {
        ClipPathExtractor.extractClipPathConfig(bodyRoot.properties.map { it.type to it.data })
    } ?: return null
    // No painted shape ⇒ no clip; the canvas keeps its wave-25 chain byte
    // for byte (this is what leaves 1433 of the 1435 corpus documents
    // untouched).
    return if (config.hasClipPath) config else null
}

/**
 * The composed capture canvas's root clip, or null when the document
 * declares none.
 *
 * @param roots the document's root forest, in wire order.
 * @param canvasFrame the inset between the OUTER capture surface this shape
 *   is applied to and the ICB the clip's lengths are measured from — the
 *   same `CaptureCanvasFrame` the hoist overlay anchors against, threaded in
 *   rather than re-derived so the two can never drift.
 */
fun rootCanvasClipShape(roots: List<IRComponent>, canvasFrame: Dp): Shape? =
    rootCanvasClipConfig(roots)?.let { config ->
        // Reuse the category's own shape factory — every value flavor
        // (polygon / inset / circle / ellipse / path / geometry-box) is
        // already handled there, and reusing it means the root clip and an
        // element clip can never render the same declaration differently.
        ClipPathApplier.createShape(config)?.let { inner ->
            IcbTranslatedShape(inner, canvasFrame)
        }
    }

/**
 * A [Shape] that resolves its delegate against the INITIAL CONTAINING BLOCK
 * inside a framed canvas and then translates the result out to the frame.
 *
 * Compose resolves a `Shape` against the node's own measured size, and the
 * node here is the framed 390×H capture surface — so a delegate resolved
 * directly would measure the clip's percentages against the image and place
 * its lengths at the image corner. Both are wrong by the frame: the root
 * element's border box is the 358×(H−32) content box at (frame, frame).
 *
 * The translation goes through a [Path] because that is the one
 * representation every [Outline] variant converts into
 * (`androidx.compose.ui.graphics.addOutline`) — `Outline.Rounded` has no
 * translate of its own, so special-casing the three variants would only
 * duplicate what `addOutline` already does.
 */
private class IcbTranslatedShape(
    private val delegate: Shape,
    private val frame: Dp,
) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density,
    ): Outline {
        // Dp→px inside the Density receiver, so the arithmetic lands in the
        // same device-px space as `size` at any screen density.
        val framePx = with(density) { frame.toPx() }
        // The ICB extent: the surface minus the frame on BOTH sides, floored
        // at zero (a canvas narrower than its own frame has no content box —
        // degenerate, but a negative Size would throw inside the delegate).
        val icb = Size(
            width = (size.width - 2f * framePx).coerceAtLeast(0f),
            height = (size.height - 2f * framePx).coerceAtLeast(0f),
        )
        // Resolve the real clip against that box…
        val outline = delegate.createOutline(icb, layoutDirection, density)
        // …then move it to where the ICB actually sits on the surface.
        val path = Path().apply { addOutline(outline) }
        path.translate(Offset(framePx, framePx))
        return Outline.Generic(path)
    }
}
