package com.styleconverter.runtime.core.colors

// LightDarkResolver — resolves `light-dark()` COLOR VALUES (css-color-5 §7.2)
// against the platform dark-mode signal, per the spec 06 §4 note: light-dark
// rides INSIDE property `data` as a dynamic color (`srgb: null`, structured
// original — schema/spec/02-values.md) and MUST resolve against the SAME
// scheme answer as `prefers-color-scheme` buckets (one surface, one scheme).
//
// Wire shape (pinned by schema/conformance/fixtures/v2/dynamic-styling.json):
//   { "original": { "type": "light-dark",
//                   "lightColor": "#ecf0f1", "darkColor": "#111827" } }
//
// Before this resolver, every color extractor's srgb-only path returned null
// for that shape — light-dark values silently DROPPED on Android (web paints
// them natively via the reconstructed CSS function). Resolution happens once
// at style-resolution time (spec 06 §1: before extraction): the chosen arm
// is parsed and the node rewritten to the standard static shape
// { srgb: {r,g,b[,a]}, original: "<arm string>" }, which every existing
// extractor already consumes — no per-extractor changes, no drop path.

import android.util.Log
import com.styleconverter.runtime.PropertyTracker
import com.styleconverter.runtime.core.ir.IRProperty
import com.styleconverter.runtime.core.variables.CssVariableResolver
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

object LightDarkResolver {

    // Logcat tag for the one-time unresolvable-arm warnings below.
    private const val TAG = "LightDarkResolver"

    // Arms already reported unresolvable — logged once per arm string.
    private val loggedUnresolvable = java.util.Collections.synchronizedSet(mutableSetOf<String>())

    /**
     * Rewrite every light-dark color node in `properties` to its
     * scheme-chosen arm. Pure function (JVM-testable): the caller supplies
     * the dark-mode signal (ComponentRenderer: isSystemInDarkTheme(), the
     * same signal MediaBucketEvaluator uses for prefers-color-scheme).
     *
     * Identity guarantee: a list without light-dark values is returned
     * UNCHANGED (same instance) — the static corpus pays one read-only walk
     * per component identity (memoized by the caller's remember()).
     */
    fun resolve(properties: List<IRProperty>, isDarkScheme: Boolean): List<IRProperty> {
        // Cheap presence probe first: most components carry no light-dark.
        if (properties.none { containsLightDark(it.data) }) return properties
        // Rebuild only the affected property envelopes; untouched entries
        // keep their original instances.
        return properties.map { p ->
            if (containsLightDark(p.data)) IRProperty(p.type, rewrite(p.data, isDarkScheme)) else p
        }
    }

    /** Deep probe: does this JSON subtree contain a light-dark color node? */
    internal fun containsLightDark(el: JsonElement): Boolean = when (el) {
        // A color node is {original: {type: "light-dark", …}} — but probing
        // just for the tagged original object also catches nested carriers
        // (border shorthand data, gradient stops, …).
        is JsonObject -> isLightDarkOriginal(el) || el.values.any { containsLightDark(it) }
        is JsonArray -> el.any { containsLightDark(it) }
        else -> false
    }

    /** True when `el` is the structured light-dark `original` payload itself. */
    private fun isLightDarkOriginal(el: JsonObject): Boolean =
        (el["type"] as? JsonPrimitive)?.contentOrNull == "light-dark"

    /**
     * Recursive rewrite: any object carrying `original: {type: "light-dark"}`
     * (the dynamic-color envelope — no srgb key by construction, spec 02) is
     * replaced by the resolved static shape; everything else recurses.
     */
    private fun rewrite(el: JsonElement, isDark: Boolean): JsonElement = when (el) {
        is JsonObject -> {
            val original = el["original"] as? JsonObject
            if (original != null && isLightDarkOriginal(original)) {
                // Found the dynamic-color envelope — resolve or preserve.
                resolveNode(el, original, isDark)
            } else {
                // Not a light-dark envelope: rebuild with rewritten values.
                JsonObject(el.mapValues { (_, v) -> rewrite(v, isDark) })
            }
        }
        is JsonArray -> JsonArray(el.map { rewrite(it, isDark) })
        // Primitives / null carry no color structure.
        else -> el
    }

    /**
     * Resolve one light-dark envelope to the standard static color shape.
     * css-color-5 §7.2: `light-dark(<light>, <dark>)` computes to the first
     * arm under a light used color scheme and the second under dark.
     */
    private fun resolveNode(envelope: JsonObject, original: JsonObject, isDark: Boolean): JsonElement {
        // Pick the arm by the platform scheme signal (spec 06 §4).
        val armKey = if (isDark) "darkColor" else "lightColor"
        val arm = (original[armKey] as? JsonPrimitive)?.contentOrNull
        // Parse via the shared CSS color grammar (hex / rgb / rgba / hsl /
        // hsla / 147 named) — the same parser the var() pre-resolution pass
        // uses, so one grammar serves both dynamic-value channels.
        val color = arm?.let { CssVariableResolver.parseColorValue(it) }
        if (arm == null || color == null) {
            // Unresolvable arm (nested var()/color-mix, exotic syntax):
            // conservatively PRESERVE the envelope untouched — downstream
            // extractors treat it as before (no worse than today), and the
            // miss is logged once (no-silent-fallthrough).
            PropertyTracker.markUnhandled("LightDark[${arm ?: "missing-$armKey"}]")
            if (loggedUnresolvable.add(arm ?: armKey)) {
                try {
                    Log.w(TAG, "light-dark arm not resolvable, value left dynamic: \"${arm ?: armKey}\"")
                } catch (_: RuntimeException) {
                    // android.util.Log is unmocked in plain-JVM unit tests.
                }
            }
            return envelope
        }
        // Emit the static wire shape every color extractor consumes:
        // srgb floats (0-1) + the chosen arm string as `original`. The "a"
        // key is included only when alpha < 1, matching the converter's
        // omit-opaque-alpha convention (spec 02).
        val srgb = buildMap<String, JsonElement> {
            put("r", JsonPrimitive(color.red.toDouble()))
            put("g", JsonPrimitive(color.green.toDouble()))
            put("b", JsonPrimitive(color.blue.toDouble()))
            if (color.alpha < 1f) put("a", JsonPrimitive(color.alpha.toDouble()))
        }
        return JsonObject(
            mapOf(
                "srgb" to JsonObject(srgb),
                "original" to JsonPrimitive(arm)
            )
        )
    }
}
