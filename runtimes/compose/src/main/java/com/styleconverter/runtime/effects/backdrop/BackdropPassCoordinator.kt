package com.styleconverter.runtime.effects.backdrop

// Compose snapshot state: the pass phase is read INSIDE draw lambdas, so a
// flip must invalidate the affected draw nodes (a plain field would not).
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
// The canvas origin is a layout coordinate (window space, px).
import androidx.compose.ui.geometry.Offset
// The pass-A snapshot travels as a Compose ImageBitmap (what
// GraphicsLayer.toImageBitmap()/Bitmap.asImageBitmap() hand us).
import androidx.compose.ui.graphics.ImageBitmap

/**
 * Which of the two backdrop passes the current frame is painting.
 *
 * filter-effects-2 §2 defines `backdrop-filter` against the *Backdrop Root
 * Image*: everything painted BELOW the element, filtered, then drawn under
 * the element's own paint. Compose has no API that hands a draw node the
 * pixels behind it, so the composed WPT canvas — a CONTROLLED tree we own
 * end to end — renders twice instead:
 *
 *  - [SAMPLE]    pass A. Every registered backdrop element paints NOTHING
 *                (its box, background, borders and children are suppressed),
 *                so the recorded canvas IS the backdrop root image.
 *  - [COMPOSITE] pass B. The pass-A bitmap is published; each backdrop
 *                element samples the patch under its own border box, runs
 *                the filter chain over it, draws it, and then paints
 *                normally on top.
 *  - [DISABLED]  no two-pass host is driving this frame. Every backdrop
 *                element keeps the historical no-op (see FilterApplier), so
 *                the per-component capture path and the committed 327-pair
 *                dark-stage baseline are byte-identical to before this lane.
 */
enum class BackdropPass { DISABLED, SAMPLE, COMPOSITE }

/**
 * Coordinates one document's two-pass backdrop render.
 *
 * ## Why a coordinator object and not a CompositionLocal
 * The scoped brief describes pass A as "the same tree with a CompositionLocal
 * suppressing backdrop elements' paint". A CompositionLocal cannot be read
 * where the decision is actually made: `StyleApplier.applyConfig` (and the
 * whole applier chain under it) is a PLAIN function, not `@Composable`, so
 * `FilterApplier` has no composition to read from. This object is the same
 * mechanism with the read moved to draw time — which is strictly better here:
 *
 *  - [enabled] is a PLAIN field, read once while the modifier chain is built.
 *    It must never be snapshot state: a recomposition would rebuild every
 *    modifier chain and throw away the per-element position slots that the
 *    composite pass depends on.
 *  - [pass] and [backdrop] ARE snapshot state, read only inside draw lambdas.
 *    Flipping them invalidates draw — NOT composition, NOT layout — so the
 *    second pass repaints the identical layout with the identical modifier
 *    instances. That invariant is what makes the two passes comparable.
 *
 * ## Backdrop-root boundary (declared, per the lane's hard rules)
 * ONE bitmap serves every backdrop element on the canvas, and pass A hides
 * ALL of them. That is exact when the backdrop elements do not overlap each
 * other and nothing paints above them; it is an approximation when they do
 * (a stacked pair should see each other's paint bottom-up). Nested/scoped
 * backdrop roots (`backdrop-filter-backdrop-root-*.html`: an ancestor with
 * filter/opacity/mask/clip-path creating its own root) are NOT modelled —
 * those tests stay out of scope and are documented as such rather than
 * silently mis-rendered.
 */
class BackdropPassCoordinator {

    /**
     * True while a two-pass host (the composed WPT canvas) is driving this
     * document AND the document actually declares `backdrop-filter`.
     * Deliberately NOT snapshot state — see the class KDoc. Read during
     * modifier construction; written by [beginDocument]/[reset] between
     * fixtures, never mid-frame.
     */
    var enabled: Boolean = false
        private set

    /**
     * The phase the next draw must paint. Snapshot state: the canvas record
     * modifier and every registered element read it in their draw lambdas, so
     * a write here schedules exactly one repaint of the same layout.
     */
    var pass: BackdropPass by mutableStateOf(BackdropPass.DISABLED)
        private set

    /**
     * Pass-A snapshot (the backdrop root image), in canvas pixel space with
     * its origin at the canvas's top-left. Null during pass A and whenever
     * the coordinator is idle — a null backdrop makes every element fall back
     * to "paint normally", i.e. the historical no-op.
     */
    var backdrop: ImageBitmap? by mutableStateOf(null)
        private set

    /**
     * The composed canvas's own top-left in WINDOW coordinates (px). Elements
     * report their position in window space, so this is the offset that maps
     * them into [backdrop]'s pixel space. Snapshot state because the canvas
     * publishes it from layout while draws read it.
     */
    var canvasOriginInWindow: Offset by mutableStateOf(Offset.Zero)
        private set

    /** Layout publishes the canvas origin here; see [canvasOriginInWindow]. */
    fun publishCanvasOrigin(origin: Offset) {
        // Cheap guard: writing an unchanged value would still invalidate every
        // reader, and layout re-reports the same origin on every frame.
        if (canvasOriginInWindow != origin) canvasOriginInWindow = origin
    }

    /**
     * Arm the coordinator for one document. [declaresBackdropFilter] comes
     * from the pure IR scan ([documentDeclaresBackdropFilter]) so documents
     * without the property never pay for the second pass and never diverge
     * from their committed captures.
     */
    fun beginDocument(declaresBackdropFilter: Boolean) {
        enabled = declaresBackdropFilter
        backdrop = null
        pass = if (declaresBackdropFilter) BackdropPass.SAMPLE else BackdropPass.DISABLED
    }

    /**
     * Publish pass A's snapshot and move to the composite pass. Called by the
     * capture host once it has read the recorded layer; the state write flips
     * every registered element from "suppress" to "sample + paint".
     */
    fun beginCompositePass(backdropImage: ImageBitmap) {
        backdrop = backdropImage
        pass = BackdropPass.COMPOSITE
    }

    /**
     * Drop back to the idle/no-op state. Called after the fixture's capture is
     * saved, and on any pass-A failure — an element with no backdrop bitmap
     * paints exactly as it did before this lane instead of half-filtering.
     */
    fun reset() {
        enabled = false
        backdrop = null
        pass = BackdropPass.DISABLED
        canvasOriginInWindow = Offset.Zero
    }

    companion object {
        /**
         * The single coordinator the render tree and the capture host share.
         * The harness is one activity rendering one document at a time, so a
         * shared instance is the whole lifetime story; tests construct their
         * own instances instead of touching this one.
         */
        val current = BackdropPassCoordinator()
    }
}
