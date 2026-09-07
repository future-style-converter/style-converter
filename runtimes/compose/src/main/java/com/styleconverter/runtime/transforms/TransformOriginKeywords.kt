package com.styleconverter.runtime.transforms

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

/**
 * Axis-aware resolution of `transform-origin` keyword pairs (retro R1,
 * finding A11#14 — the Compose half; the iOS lane fixes its twin to the
 * same rule).
 *
 * css-transforms-1 §4 gives transform-origin a <position>-style grammar
 * whose keywords are bound to their OWN axis: `left`/`right` are horizontal,
 * `top`/`bottom` vertical, `center` either — so `top right` and `right top`
 * are the same point. The converter stores the two tokens POSITIONALLY
 * (x = first token, y = second), and the old extractor mapped whatever
 * landed in x with LEFT/TOP → 0, RIGHT/BOTTOM → 1: `top right` became
 * x = 0 (TOP), y = 1 (RIGHT) — the BOTTOM-LEFT corner. Measured on the
 * transform-origin fixture (120x80 box, rotate(15deg), fill centroid in
 * canvas px with the 16px capture pad): Chromium (web harness) `top right`
 * = (68.4, 41.2), both natives (82.3, 71.8). The correct origin (120, 0)
 * predicts the box centre at (51.7, 23.1) + 16 = (67.7, 39.1) ✓; the
 * un-swapped origin (0, 80) predicts (68.3, 56.9) + 16 = (84.3, 72.9) —
 * the natives. `bottom center`: web (85.7, 57.7) vs predicted (70.4,
 * 41.4) + 16 ✓; natives (78.2, 43.2) vs the un-swapped (62.0, 24.5) + 16 ✓.
 * The same rule Transform3DExtractor.extractPerspectiveOrigin already
 * applies to perspective-origin (css-values-4 §8.3's <position>).
 */
object TransformOriginKeywords {

    /** Which axis a keyword is pinned to. */
    enum class Axis { HORIZONTAL, VERTICAL, EITHER }

    /**
     * One decoded component: its 0..1 fraction (null when not a keyword/percentage), its axis
     * affinity, and whether it was a KEYWORD — retro R5 (A11#14 twin agreement): a `center`
     * keyword and a `50%` both read EITHER/0.5, but only a keyword may sit beside a
     * reordered keyword (§4's `&&` production is keyword-only), so the flag is what
     * separates the valid `top center` from the invalid `top 50%`-style pairs.
     */
    data class Component(val fraction: Float?, val axis: Axis, val keyword: Boolean = true)

    /** Keyword text (any case) → (fraction, affinity); null for a non-keyword. */
    fun keyword(text: String?): Component? = when (text?.uppercase()) {
        "LEFT" -> Component(0f, Axis.HORIZONTAL)
        "RIGHT" -> Component(1f, Axis.HORIZONTAL)
        "TOP" -> Component(0f, Axis.VERTICAL)
        "BOTTOM" -> Component(1f, Axis.VERTICAL)
        "CENTER" -> Component(0.5f, Axis.EITHER)
        else -> null
    }

    /**
     * Decode one wire component. Keywords arrive as {"type":"keyword",
     * "value":"TOP"} (TransformOriginProperty) or as a bare string; anything
     * else (percentage / length) is axis-neutral and its fraction comes
     * from [numeric] — the caller's existing percentage/length reader.
     */
    fun component(el: JsonElement?, numeric: (JsonElement?) -> Float?): Component {
        val kw = when (el) {
            is JsonPrimitive -> keyword(el.contentOrNull)
            is JsonObject -> if (el["type"]?.jsonPrimitive?.contentOrNull?.lowercase() == "keyword")
                keyword(el["value"]?.jsonPrimitive?.contentOrNull) else null
            else -> null
        }
        // A non-keyword (percentage / length) is axis-neutral and flagged as such.
        return kw ?: Component(numeric(el), Axis.EITHER, keyword = false)
    }

    /** The implicit `center` §5 supplies for an omitted value. */
    private val CENTER = Component(0.5f, Axis.EITHER)

    /**
     * retro R5 (A11#14 twin agreement): true when the pair is OUTSIDE the §5 grammar — a
     * reordered keyword (vertical first / horizontal second) beside a <length-percentage>.
     * The two-value form is `[left|center|right|<lp>] [top|center|bottom|<lp>]` and only the
     * KEYWORD pair may reorder, so `top 10px` / `30% right` are invalid: Chromium drops the
     * declaration and the origin is the initial 50% 50%. Consulted by [resolve] and by
     * TransformExtractor.extractTransformOriginDp, so the dropped <length> does not ride as
     * a dp anchor either. The iOS resolver (TransformOriginResolver.swift) applies the same rule.
     */
    fun dropsDeclaration(x: JsonElement?, y: JsonElement?): Boolean {
        val cx = component(x) { null }
        val cy = component(y) { null }
        return (cx.axis == Axis.VERTICAL && !cy.keyword) || (cy.axis == Axis.HORIZONTAL && !cx.keyword)
    }

    /**
     * Resolve the (x, y) fractions with the §5 axis binding: if the first
     * token is a VERTICAL keyword or the second a HORIZONTAL one, the
     * tokens name the axes in the other order and are swapped. Missing
     * fractions default to the 50% initial value.
     */
    fun resolve(x: JsonElement?, y: JsonElement?, numeric: (JsonElement?) -> Float?): Pair<Float, Float> {
        val cx = component(x, numeric)
        val cy = component(y, numeric)
        val swapped = cx.axis == Axis.VERTICAL || cy.axis == Axis.HORIZONTAL
        // Natural order: first token is x, second is y.
        if (!swapped) return (cx.fraction ?: 0.5f) to (cy.fraction ?: 0.5f)
        // retro R5: a number beside the reordered keyword is not in the grammar — initial value.
        if (dropsDeclaration(x, y)) return 0.5f to 0.5f
        // The `&&` form: the horizontal keyword is x, the vertical keyword is y. A slot whose
        // keyword moved to the OTHER axis and whose partner is not that axis's keyword takes
        // §5's implicit `center` (retro R5): the converter encodes a lone `top` as x:TOP, y:TOP
        // (TransformOriginPropertyParser duplicates the vertical keyword), which is `center top`
        // — "If only one value is specified, the second value is assumed to be center" — not
        // the top-left corner the plain swap read.
        val h = if (cy.axis == Axis.HORIZONTAL) cy else if (cx.axis == Axis.VERTICAL) CENTER else cx
        val v = if (cx.axis == Axis.VERTICAL) cx else if (cy.axis == Axis.HORIZONTAL) CENTER else cy
        return (h.fraction ?: 0.5f) to (v.fraction ?: 0.5f)
    }
}
