package com.styleconverter.runtime.columns

// css-gap-decorations-1 — the DRAW HOOK that lets the Compose flex path
// paint gap decorations (wave 24, lane GAPS-A).
//
// Compose's Row/Column/FlowRow place their children themselves and expose
// no post-placement geometry to the parent, so the painter cannot ask the
// container where the items landed. The items report instead: each direct
// child of a decorated flex container carries an onGloballyPositioned
// probe, and the container's draw modifier reads the collected rectangles.
//
// WHY A REGISTRY AND NOT A CompositionLocal: providing a local would mean
// wrapping the flex branch's body in a CompositionLocalProvider — a brace
// change through the middle of a 4600-line renderer that five concurrent
// lanes are editing. Keying sinks by the CHILD's IR id (ids are unique per
// document) keeps the ComponentRenderer edit down to three lines.
//
// DARK-STAGE SAFETY (the frozen 327-pair baseline): the hook is inert
// unless a component is a flex container AND declares a gap-decoration
// rule that paints. No fixture outside fixtures/wpt/css-gaps pairs
// `display: flex` with any `*-rule-*` property — verified by scanning
// every non-WPT fixture in fixtures/ (0 hits). When inert,
// rememberGapDecorationSink returns null, both Modifier extensions return
// the receiver unchanged, and the registry stays empty: the modifier
// chain, the layout, and the draw list are byte-identical to today's.
// The multicol `column-rule` path is untouched — multicol containers are
// `display: block`, so they never reach this hook.

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.unit.Density
import com.styleconverter.runtime.core.ir.IRComponent

/**
 * Collection point for one decorated flex container.
 *
 * @param childIds the container's DIRECT children, in document order —
 *        the only ids whose probes are honoured, so a grandchild that
 *        happens to find this sink is ignored rather than mistaken for a
 *        flex item.
 * @param config the container's resolved gap-decoration config.
 * @param mainHorizontal true for a row-direction flex container.
 */
class GapItemSink(
    val childIds: List<String>,
    val config: GapDecorationConfig,
    val mainHorizontal: Boolean
) {
    // Window-space item rectangles, keyed by IR id. A snapshot map so the
    // container's draw phase re-runs when a child is (re)placed.
    internal val rects = mutableStateMapOf<String, GapRect>()

    // The container's own content-box origin in window space, captured by
    // the SAME modifier chain position as the draw call — subtracting it
    // converts item rects into the DrawScope's coordinate space.
    internal val origin = mutableStateOf(Offset.Zero)

    /** Item rectangles in document order, origin-relative. Empty until placed. */
    internal fun placedItems(): List<GapRect> {
        val o = origin.value
        return childIds.mapNotNull { rects[it] }.map {
            GapRect(it.left - o.x, it.top - o.y, it.right - o.x, it.bottom - o.y)
        }
    }
}

/**
 * Process-wide child-id → sink map. Entries exist only while a decorated
 * flex container is composed (a DisposableEffect adds and removes them),
 * so for every fixture that declares no gap decorations this map is empty
 * and every lookup is a miss.
 */
private val sinkByChildId = java.util.Collections.synchronizedMap(HashMap<String, GapItemSink>())

/**
 * Build (and publish) the sink for [component] if it is a flex container
 * that actually paints gap decorations; otherwise null.
 *
 * @param isFlex whether the renderer chose a flex container for this
 *        component — the gate that keeps multicol and block boxes out.
 * @param mainHorizontal true when the main axis is horizontal.
 */
@Composable
fun rememberGapDecorationSink(
    component: IRComponent,
    isFlex: Boolean,
    mainHorizontal: Boolean
): GapItemSink? {
    // Decode once per component identity; extraction is cheap but this
    // also keeps the sink (and its rect map) stable across recomposition.
    val sink = remember(component.id, isFlex, mainHorizontal) {
        val children = component.children.orEmpty()
        // Fewer than two items opens no gap, so nothing could be painted.
        if (!isFlex || children.size < 2) return@remember null
        val config = GapDecorationExtractor.extract(component.properties.map { it.type to it.data })
        if (!config.active) return@remember null
        GapItemSink(children.map { it.id }, config, mainHorizontal).also { made ->
            // Publish INSIDE remember, i.e. during this container's own
            // composition — the children compose after this point but
            // before any DisposableEffect fires, so registering in an
            // effect would make every probe miss on the first frame.
            made.childIds.forEach { sinkByChildId[it] = made }
        }
    }
    // Effect owns only the teardown (and re-publish after a swap).
    DisposableEffect(sink) {
        sink?.childIds?.forEach { sinkByChildId[it] = sink }
        onDispose { sink?.childIds?.forEach { sinkByChildId.remove(it) } }
    }
    return sink
}

/**
 * HOOK PART 1 — attach to every component's outermost modifier. Reports
 * the component's placed bounds when (and only when) its parent is a
 * decorated flex container. Returns the receiver untouched otherwise, so
 * `Modifier.then(Modifier)` folds away to nothing.
 */
fun Modifier.gapItemProbe(id: String): Modifier {
    val sink = sinkByChildId[id] ?: return this
    return this.onGloballyPositioned { coords ->
        // positionInWindow + size, NOT boundsInWindow: bounds are clipped
        // by ancestors, and WPT flex-gap-decorations-008 deliberately
        // overflows its container (six 50px items, nowrap, in a 200px
        // box) — its five rules are painted outside the container box.
        val p = coords.positionInWindow()
        sink.rects[id] = GapRect(p.x, p.y, p.x + coords.size.width, p.y + coords.size.height)
    }
}

/**
 * HOOK PART 2 — attach to the flex container's own modifier, at the END
 * of the chain so the DrawScope (and the captured origin) are in
 * CONTENT-BOX space: border and padding modifiers earlier in the chain
 * have already inset it, which is exactly the box css-gap-decorations-1
 * spans the between-line rules across.
 *
 * Decorations paint BEFORE the content: the WPT reference documents place
 * their decoration boxes at `z-index: -1` (fixtures/wpt/css-gaps/
 * flex__flex-gap-decorations-034__ref.json), i.e. behind the items and
 * above the container background.
 */
fun Modifier.gapDecorations(sink: GapItemSink?): Modifier {
    if (sink == null) return this
    return this
        .onGloballyPositioned { coords -> sink.origin.value = coords.positionInWindow() }
        .drawWithContent {
            val items = sink.placedItems()
            // All children must have reported before the geometry means
            // anything; a partial set would invent gaps that do not exist.
            if (items.size == sink.childIds.size) {
                val box = GapRect(0f, 0f, size.width, size.height)
                // The config's widths/insets are IR px == dp; the item
                // rects are real px. Scale the config once, here, so the
                // segment model stays in a single unit system.
                val scaled = scaleToPixels(sink.config, this)
                GapDecorationPainter.paint(
                    this,
                    GapDecorationSegments.build(scaled, items, box, sink.mainHorizontal),
                    scaled
                )
            }
            drawContent()
        }
}

/** dp-space rule widths/insets → real pixels for the current density. */
internal fun scaleToPixels(config: GapDecorationConfig, density: Density): GapDecorationConfig {
    val s = density.density
    fun spec(x: GapRuleSpec) = x.copy(widthPx = x.effectiveWidthPx * s, insetPx = x.insetPx * s)
    return config.copy(column = spec(config.column), row = spec(config.row))
}
