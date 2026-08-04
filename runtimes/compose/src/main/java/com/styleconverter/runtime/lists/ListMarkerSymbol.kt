package com.styleconverter.runtime.lists

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke

/**
 * The GEOMETRY of a `disc` / `circle` / `square` marker — wave 30, lane 3
 * (fix B6). BYTE-PARALLEL TWIN of
 * runtimes/swiftui/Sources/StyleConverterRuntime/StyleEngine/lists/
 * ListMarkerSymbol.swift.
 *
 * ## The defect (measured on the LIVE wave29-final css-lists section)
 * Both natives paint the three UA symbol markers as TEXT: `•`,
 * `○`, `■` through the marker `Text` (see
 * [ListStyleApplier.getMarker]). A browser paints a SHAPE instead —
 * Chromium's `ListMarker::RelativeSymbolMarkerRect` sizes it
 * `(ascent * 2 / 3 + 1) / 2`, which at the ref's 16px root font
 * (ascent 15) is 5.5px — and the glyph is 2.2–2.6× larger than that.
 * Ink extents of the `square` marker of
 * `wpt__css-lists__change-list-style-type-001` (`font-size: 16px`):
 *
 * | platform | marker ink   | ratio vs web |
 * |----------|--------------|--------------|
 * | web      | x 56–60, y 41–45 (**5×5**) | 1.00 |
 * | Android  | x 17–27, y 36–46 (**11×11**) | 2.20 |
 * | iOS      | x 17–29, y 36–48 (**13×13**) | 2.60 |
 *
 * The two natives do not even agree with EACH OTHER, because a glyph's
 * size is a property of whichever fallback face resolved it. A painted
 * shape is font-independent, so this closes the native-parity gap in the
 * same edit as the web gap.
 *
 * ## Why 0.35em
 * Chromium's `(ascent * 2 / 3 + 1) / 2` is 5.5px at the ref's 16px/ascent-15
 * root = 0.34375em; the runtimes have no ascent at this call site, so the
 * em-relative approximation [SIZE_EM] reproduces it to within 0.1px there
 * and scales the same way the glyph it replaces did. It is deliberately a
 * ratio, not the absolute 5.5px: the ref's line box is a unitless 1.25
 * that recomputes per element (see `ComponentRenderer.wptRefLineHeightRatio`
 * for the twin argument), and a marker on a 25px item must scale with it.
 *
 * ## What this does NOT change (deferred, measured)
 * The marker box's ADVANCE — the distance from the marker's origin to the
 * item's first glyph — is untouched: the shape is painted INSIDE the box
 * the marker `Text` already measures, so no item moves. That advance is
 * still wrong (web puts the first glyph of the `inside` square row at
 * x 79, i.e. 23px after the marker origin; Android puts it at 17px) and
 * is the UA's per-counter-style marker padding (css-lists-3 §3.2), the
 * still-deferred B-RC3 part 3 called out at both renderers' marker
 * branches. Shrinking the advance ALONG WITH the shape would have moved
 * every symbol row a further ~5px away from web, so it is explicitly not
 * done here.
 *
 * Pure and JVM-pinned (ListMarkerSymbolTest); the painting itself is the
 * `Modifier.drawWithContent` at the renderer's two marker call sites.
 */
object ListMarkerSymbol {

    /**
     * The three UA symbol markers css-counter-styles-3 §6.1 defines and
     * a browser PAINTS rather than sets in a font.
     */
    enum class Shape {
        /** `disc` — filled circle. */
        FILLED_CIRCLE,
        /** `circle` — hollow circle, stroked at [STROKE_EM]. */
        HOLLOW_CIRCLE,
        /** `square` — filled square. */
        FILLED_SQUARE
    }

    /** See "Why 0.35em" in the file header. */
    const val SIZE_EM: Float = 0.35f

    /**
     * Stroke width of the hollow `circle`, as a fraction of the font
     * size. Chromium strokes it at 1 device pixel at the 16px root; 1/16
     * of the em keeps that at the ref root and thickens proportionally
     * on a larger item, which is what the glyph it replaces did.
     */
    const val STROKE_EM: Float = 1f / 16f

    /**
     * Which shape (if any) this counter style paints.
     *
     * Everything else returns null and keeps its TEXT rendering — that
     * includes every numeric/alphabetic style AND every counter style
     * this runtime failed to resolve, which is the important case: the
     * live `marker-text-matches-disc` / `-circle` fixtures declare
     * `list-style-type: my-disc` / `my-circle` (custom `@counter-style`
     * names the IR wire cannot carry), so
     * [ListStyleExtractor.typeFromKeyword] returns null for them and the
     * container's `<ol>` UA `decimal` stands. They therefore paint `1.`
     * as text on all three runtimes (0.99 across every pair in
     * wave29-final) and this table must not reach them.
     */
    fun shapeFor(type: ListStyleType): Shape? = when (type) {
        ListStyleType.DISC -> Shape.FILLED_CIRCLE
        ListStyleType.CIRCLE -> Shape.HOLLOW_CIRCLE
        ListStyleType.SQUARE -> Shape.FILLED_SQUARE
        else -> null
    }

    /**
     * [shapeFor] narrowed by the string the marker box is ACTUALLY about
     * to paint — null unless that string is exactly the symbol glyph this
     * runtime's own table produces for [type].
     *
     * The guard exists for `meta.markerText`: wave 27 made a BAKED marker
     * win outright over the local table (it carries the converter's full
     * css-counter-styles-3 §6 resolution, including the `<ol start>`
     * ordinal), and a producer is free to bake ANY string onto an item
     * whose resolved `list-style-type` still reads `disc`. Replacing that
     * string's ink with a painted circle would silently delete content the
     * wire asked for, so the shape only fires when the two agree.
     */
    fun shapeFor(type: ListStyleType, markerText: String): Shape? {
        val shape = shapeFor(type) ?: return null
        val ownGlyph = ListStyleApplier.getMarker(0, ListStyleConfig(listStyleType = type))
        return if (markerText == ownGlyph) shape else null
    }

    /**
     * The painted shape's side length in px for a run at [fontSizePx].
     *
     * Floored at zero so an unresolved (0 or negative) font size paints
     * nothing rather than an inverted rect — the marker `Text` box is
     * still measured either way, so an item never moves because of it.
     */
    fun sizePx(fontSizePx: Float): Float =
        if (fontSizePx <= 0f) 0f else fontSizePx * SIZE_EM

    /** Stroke width in px for [Shape.HOLLOW_CIRCLE] at [fontSizePx]. */
    fun strokePx(fontSizePx: Float): Float =
        if (fontSizePx <= 0f) 0f else fontSizePx * STROKE_EM

    /**
     * Where the shape's top edge sits inside a marker box of
     * [boxHeightPx]: vertically CENTRED.
     *
     * Chromium aligns the symbol against the font's ascent, not the line
     * box centre, but the two coincide within a pixel on the corpus: the
     * ref's `square` occupies rows 41–45 of a line box spanning 33–53
     * (centre 43, shape centre 43). Centring is chosen over an
     * ascent-relative offset because this call site has the box height
     * but no font metrics.
     */
    fun topPx(boxHeightPx: Float, fontSizePx: Float): Float =
        ((boxHeightPx - sizePx(fontSizePx)) / 2f).coerceAtLeast(0f)

    /**
     * Replace a marker `Text`'s INK with the painted shape, keeping the
     * box it measured.
     *
     * `drawWithContent` without calling `drawContent()` is the whole
     * trick: layout has already run, so the glyph's advance, line box and
     * first baseline all still drive the row — only the pixels change.
     * That is deliberate, not incidental; see "What this does NOT change"
     * in the file header for the measurement behind it.
     *
     * @param type the item's RESOLVED counter style. A non-symbol style
     *   (or an unresolved custom `@counter-style` name, which resolves to
     *   the container's UA default) returns [Modifier] itself and paints
     *   the glyph exactly as before.
     * @param markerText the string the box would otherwise paint — see
     *   the baked-marker guard on [shapeFor].
     * @param fontSizePx the marker run's resolved font size; the shape is
     *   [SIZE_EM] of it.
     * @param color the ink the glyph would have used, resolved by the
     *   caller — a draw scope cannot read `LocalContentColor`.
     */
    fun paint(
        type: ListStyleType,
        markerText: String,
        fontSizePx: Float,
        color: Color
    ): Modifier {
        val shape = shapeFor(type, markerText) ?: return Modifier
        val side = sizePx(fontSizePx)
        if (side <= 0f) return Modifier
        return Modifier.drawWithContent {
            // NO drawContent() — the glyph is deliberately not painted.
            val top = topPx(size.height, fontSizePx)
            when (shape) {
                // css-counter-styles-3 §6.1 `disc`: a filled circle
                // inscribed in the side×side box.
                Shape.FILLED_CIRCLE -> drawCircle(
                    color = color,
                    radius = side / 2f,
                    center = Offset(side / 2f, top + side / 2f)
                )
                // `circle`: the same circle, stroked. The stroke is
                // centred on the path, so the drawn extent still spans
                // `side` — matching the filled disc's footprint, which is
                // what a browser does with the two styles.
                Shape.HOLLOW_CIRCLE -> drawCircle(
                    color = color,
                    radius = (side - strokePx(fontSizePx)) / 2f,
                    center = Offset(side / 2f, top + side / 2f),
                    style = Stroke(width = strokePx(fontSizePx))
                )
                // `square`: the box itself, filled.
                Shape.FILLED_SQUARE -> drawRect(
                    color = color,
                    topLeft = Offset(0f, top),
                    size = Size(side, side)
                )
            }
        }
    }
}
