package com.styleconverter.runtime.color

import androidx.compose.ui.graphics.Color
import com.styleconverter.runtime.PropertyTracker
import com.styleconverter.runtime.core.types.ValueExtractors
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * `background-color: currentcolor` resolution (css-color-4 §6.4).
 *
 * ## Why this file exists
 *
 * §6.4 defines `currentcolor` as "the value of the `color` property on the
 * SAME element" — the element's own computed `color`, not its parent's, and
 * not the runtime's default ink. The wire carries the keyword srgb-less
 * (`{"original":"currentColor"}` — see
 * `tools/titan/runs/wave48-final/sections/css-color/per-test-ir/
 * wpt__css-color__currentcolor-001.json`), and
 * [ValueExtractors.extractColor] correctly returns `null` for it because it
 * has no element context. ColorExtractor used to hand that `null` straight to
 * ColorConfig, so `background-color: currentcolor` painted NOTHING and the
 * ancestor's own background showed through: on `css-color/currentcolor-001`
 * the inner box (color: green) left the outer `background-color: red` visible,
 * i.e. the runtime painted the test's FAIL square. Measured on the wave-48
 * captures: iOS ssim 0.9988 with `colorFailed` and labDeltaE.mean 11.24,
 * Android ssim 0.8957 / mean 8.48.
 *
 * wave-49 residual on Compose (retrospective R3, finding A3#0): the colour is
 * right (`colorFailed` false, labDeltaE.mean 5.85) but the box is 96×96 —
 * 6em against 16px, the percentage font-size never reaches the em basis —
 * android-ref 0.8957 on currentcolor-001/002 while iOS paints the ref's
 * 192×192 and passes (0.9990). Measured on the wave49-final captures: green
 * n=9216 bbox [16,88]-[111,183] vs the frozen ref's n=36864 bbox
 * [16,88]-[207,279]. Not this file's mechanism: the outer div's FontSize
 * arrives as `{"original":{"type":"percentage","value":200}}` and the em
 * basis for Width/Height `6em` is resolved by
 * `core/variables/DynamicValueResolver.resolveOriginalObject`, which needs a
 * `{type:percentage}` branch (css-fonts-4 §2.5: a percentage font-size
 * resolves against the INHERITED size; css-values-4 §5.5) before the element's
 * own em resolves — the R3 seam patch, pinned on the verbatim
 * currentcolor-002 IR (outer/middle/inner all 192px).
 *
 * ## The resolution target
 *
 * The `Color` entry of the list handed to [resolve] is the element's COMPUTED
 * color: `ComponentRenderer` merges the inherited channel (which includes
 * `Color`, see INHERITED_PROPERTY_TYPES) UNDER the component's own
 * declarations before extraction, so "own wins, else the ancestor's" is
 * already true of that list. This is the identical lookup
 * `BorderSideExtractor` performs for `border-*-color: currentcolor`, so the
 * two currentcolor consumers can never disagree about the target.
 *
 * ## Inside `color-mix()` too — and why that needs the §7.3.2 pass
 *
 * §6.4 applies wherever the keyword appears, including as a `color-mix()`
 * endpoint (css-color-5 §3 mixes the USED colours), so this object also
 * routes such a mix through [StaticColorMix].
 *
 * That arm is only safe TOGETHER with [BackgroundColorInheritance]:
 * `css-color/color-mix-currentcolor-001` covers the mix-bearing parent with a
 * child whose `background-color: inherit` re-resolves the SAME mix against
 * its own green. Evaluating the parent's mix without the §7.3.2 pass would
 * paint the parent's brown where the reference is green — and the corpus
 * colour veto misses it at that area (web renders exactly that brown today
 * and scores ssim 1.0000 with mean ΔE 1.954, under the 2.3 JND threshold in
 * `tools/titan/inject-wpt-block.mjs`). If the §7.3.2 pass is ever unwired from
 * `ComponentHost`, delete the [StaticColorMix] arm in [resolve] with it.
 */
internal object CurrentColorBackground {

    /**
     * Resolve one `BackgroundColor` wire value against [properties] (the
     * element's MERGED declaration list — see the class doc).
     *
     * Static payloads are delegated to [ValueExtractors.extractColor]
     * untouched, so every already-working background colour keeps its exact
     * previous value; only the case that used to return `null` because of the
     * `currentcolor` keyword is newly resolved.
     */
    fun resolve(
        data: JsonElement?,
        properties: List<Pair<String, JsonElement?>>,
    ): Color? {
        // 1. The unchanged path: anything the static decoder can already read
        //    (pre-resolved srgb, and the srgb-space color-mix() it handles).
        ValueExtractors.extractColor(data)?.let { return it }

        // 2. Not statically resolvable. A `color-mix()` the static decoder
        //    refused — a space other than srgb, or an endpoint it cannot
        //    read, `currentColor` first among them — is evaluated here
        //    (css-color-5 §3, see StaticColorMix). §6.4 makes the keyword
        //    resolve against the element the mix is USED on, so the element's
        //    own computed colour is what goes in.
        StaticColorMix.payload(data)?.let { mix ->
            return StaticColorMix.resolve(mix, ownComputedColor(properties))
        }

        // 3. Not a mix either. Is it the currentcolor keyword?
        if (!isCurrentColorKeyword(data)) {
            // Some OTHER unresolvable dynamic value (var(), light-dark,
            // relative colour). Unchanged behaviour — but recorded rather
            // than dropped silently, per the runtime's no-silent-fallthrough
            // rule.
            if (data != null) PropertyTracker.markUnhandled("BackgroundColor[dynamic]")
            return null
        }

        // 4. §6.4: substitute the element's own computed `color`.
        val own = ownComputedColor(properties)
        if (own == null) {
            // No `Color` anywhere on the merged list — the chain bottoms out
            // in the UA/initial ink. That bottom-out is deliberately NOT done
            // here: ComponentRenderer.resolveCurrentColorBottomOut owns it
            // because it is capture-mode split (WPT stage → spec black, dark
            // stage → #eee) and this extractor has no access to that flag.
            // Recorded so the drop is visible in the coverage report.
            PropertyTracker.markUnhandled("BackgroundColor[currentcolor-no-color]")
            return null
        }
        return own
    }

    /**
     * True when the wire value is the `currentcolor` keyword.
     *
     * Two shapes reach us: the `{"original":"currentColor"}` envelope the
     * converter emits for a keyword it cannot pre-resolve, and a bare
     * `"currentColor"` primitive (the shape `fixtures/properties/color/
     * color-named.json` carries). CSS keywords are ASCII case-insensitive
     * (css-values-4 §4.1), hence the case-insensitive compare — the same test
     * `ComponentRenderer.isCurrentColorValue` applies to the `Color` property.
     */
    private fun isCurrentColorKeyword(data: JsonElement?): Boolean = when (data) {
        is JsonObject ->
            (data["original"] as? JsonPrimitive)?.contentOrNull
                ?.equals("currentColor", ignoreCase = true) == true
        is JsonPrimitive ->
            data.contentOrNull?.equals("currentColor", ignoreCase = true) == true
        else -> false
    }

    /**
     * The element's computed `color`, or null when the merged list carries
     * none (or carries one that is itself unresolvable — e.g. `color:
     * currentColor`, which ComponentRenderer already rewrites to the
     * inherited entry before extraction, so reaching here means the whole
     * chain bottomed out).
     */
    private fun ownComputedColor(properties: List<Pair<String, JsonElement?>>): Color? =
        properties.firstOrNull { it.first == "Color" }
            ?.second
            ?.let { ValueExtractors.extractColor(it) }
}
