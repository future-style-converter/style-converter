package com.styleconverter.runtime.effects

// Compose Color — the resolved ink handed to the effects extractors.
import androidx.compose.ui.graphics.Color
// RC-B1 ink mode-split: WPT capture bottoms `currentcolor` out at spec black,
// the dark stage keeps the historical #eee harness body colour.
import com.styleconverter.runtime.core.renderer.defaultTextInk
// The static IRColor decoder — returns null for the `currentColor` keyword
// (it has no element context), which is exactly the case this object fills.
import com.styleconverter.runtime.core.types.ValueExtractors
import kotlinx.serialization.json.JsonElement

/**
 * The element's resolved `color`, for effect values whose <color> defaults to
 * it (retro R6, audit finding A7#1):
 *
 *  - `filter: drop-shadow(…)` — filter-effects-1: an omitted colour is "taken
 *    from the color property" (the drop-shadow() function definition), and an
 *    explicit `currentColor` resolves to the same thing (css-color-4 §6.4).
 *  - `box-shadow` — css-backgrounds-3 §6.1: "If the color is absent, the used
 *    color is taken from the color property."
 *
 * Both natives substituted a hard-coded colour here (Compose OPAQUE BLACK,
 * Swift black at 30 % alpha) whenever the wire carried
 * `c: {"original": "currentColor"}` — the corpus carrier css-color/
 * currentcolor-003 painted a solid red block on Android and grey copies on
 * iOS where the ref paints copies in the element's own colour. The fix is
 * the chain BorderSideExtractor already uses for `border-color`'s
 * currentcolor initial (css-backgrounds-3 §3.1), byte-for-byte:
 *
 *  1. the element's own `Color` property, if declared (or inherited — the
 *     `properties` list is the MERGED own-over-inherited channel);
 *  2. the mode-split default ink: WPT capture → spec black (a real page's
 *     inherit chain ends at the UA `color: CanvasText`), dark stage → #eee
 *     (the web harness body colour the 327 committed baselines assume).
 */
internal object EffectsCurrentColorInk {

    /** The dark-stage bottom-out — the web harness body `color: #eee`. */
    val DARK_STAGE_INK: Color = Color(0xFFEEEEEE)

    /**
     * Resolve the ink for [properties]. Mirrors BorderSideExtractor's
     * `currentColor` resolution so border, box-shadow and drop-shadow can
     * never disagree about what `currentcolor` means on an element.
     *
     * @param wptCaptureMode the StyleApplier-threaded WPT flag (the same one
     *   BordersFacade.extractConfig receives); default false keeps every
     *   dark-stage caller on the historical #eee.
     */
    fun resolve(properties: List<Pair<String, JsonElement?>>, wptCaptureMode: Boolean): Color =
        // 1. the element's own computed colour when the wire carries one…
        properties.firstOrNull { it.first == "Color" }?.second?.let { ValueExtractors.extractColor(it) }
            // 2. …else the mode-split default (WptCaptureMode.kt).
            ?: defaultTextInk(wptCaptureMode, DARK_STAGE_INK)
}
