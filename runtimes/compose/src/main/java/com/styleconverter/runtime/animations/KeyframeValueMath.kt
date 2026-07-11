package com.styleconverter.runtime.animations

// KeyframeValueMath — the tier VALUE arithmetic shared by keyframe
// animation and transitions (schema/spec/07-animations.md §2 + §4: one
// interpolation tier, two triggers). Split out of KeyframeInterpolator
// (which owns tracks/segments/step logging) per the ≤300-line file rule.
//
// Everything operates on VERBATIM wire byte shapes and emits the same
// shapes back ({alpha}, {srgb:{r,g,b[,a]}}, {type:'length',px},
// {type:'functions',list:[…]}) so interpolated frames flow through the
// exact extractor/applier path the static corpus uses.

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull

object KeyframeValueMath {

    /** Interpolate one tier property between two wire payloads at fraction
     *  [t] (already eased by the caller). null = shapes don't interpolate
     *  → the caller applies its discrete step rule (§2/§4). */
    internal fun interpolateTyped(
        type: String,
        from: JsonElement,
        to: JsonElement,
        t: Double
    ): JsonElement? = when (type) {
        // Opacity — number, linear; clamped to [0,1] at paint by the
        // applier (§2 table row 1). Emit the {alpha} carrier the runtime's
        // ColorExtractor.extractOpacity already reads.
        "Opacity" -> {
            val a = opacityOf(from)
            val b = opacityOf(to)
            if (a == null || b == null) null
            else JsonObject(mapOf("alpha" to JsonPrimitive(lerp(a, b, t))))
        }
        // Colors — component-wise in sRGB on the wire's 0–1 floats (§2:
        // all three platforms interpolate the same bytes). Alpha defaults
        // to 1 so an {r,g,b} ↔ {r,g,b,a} pair still interpolates.
        "BackgroundColor", "Color" -> {
            val a = srgbOf(from)
            val b = srgbOf(to)
            if (a == null || b == null) null
            else {
                val mixed = DoubleArray(4) { i -> lerp(a[i], b[i], t) }
                val srgb = buildMap<String, JsonElement> {
                    put("r", JsonPrimitive(mixed[0]))
                    put("g", JsonPrimitive(mixed[1]))
                    put("b", JsonPrimitive(mixed[2]))
                    // Only carry alpha when it deviates — matches the wire's
                    // omit-opaque-alpha posture so extractors see one shape.
                    if (mixed[3] < 1.0) put("a", JsonPrimitive(mixed[3]))
                }
                JsonObject(mapOf("srgb" to JsonObject(srgb)))
            }
        }
        // Lengths — px space (the wire's normalized unit). A null px is a
        // runtime-dependent length (%, em, calc): §2 says interpolate only
        // if resolvable first — this runtime steps it (caller logs).
        "Width", "Height" -> {
            val a = pxOf(from)
            val b = pxOf(to)
            if (a == null || b == null) null
            else JsonObject(mapOf(
                "type" to JsonPrimitive("length"),
                "px" to JsonPrimitive(lerp(a, b, t))
            ))
        }
        // Transform lists + the individual translate/scale/rotate
        // properties — css-transforms-1 §interpolation: MATCHING lists
        // interpolate per-function (px lengths, deg angles — exactly the
        // numeric leaves of the wire shape). Non-matching → null → step.
        "Transform", "Translate", "Scale", "Rotate" -> lerpJson(from, to, t)
        else -> null
    }

    // ── payload readers (wire shapes per spec 02) ───────────────────────

    /** Opacity carrier: bare number | {alpha} | {value} (historic shapes —
     *  same set ScreenshotCaptureScreen.tryOpacityValue tolerates). */
    internal fun opacityOf(el: JsonElement): Double? {
        (el as? JsonPrimitive)?.doubleOrNull?.let { return it }
        val obj = el as? JsonObject ?: return null
        (obj["alpha"] as? JsonPrimitive)?.doubleOrNull?.let { return it }
        (obj["value"] as? JsonPrimitive)?.doubleOrNull?.let { return it }
        return null
    }

    /** Color carrier: {srgb:{r,g,b[,a]}} → [r,g,b,a]; anything without a
     *  pre-resolved srgb (light-dark objects, color-mix originals) is not
     *  interpolable here. */
    internal fun srgbOf(el: JsonElement): DoubleArray? {
        val srgb = (el as? JsonObject)?.get("srgb") as? JsonObject ?: return null
        val r = (srgb["r"] as? JsonPrimitive)?.doubleOrNull ?: return null
        val g = (srgb["g"] as? JsonPrimitive)?.doubleOrNull ?: return null
        val b = (srgb["b"] as? JsonPrimitive)?.doubleOrNull ?: return null
        val a = (srgb["a"] as? JsonPrimitive)?.doubleOrNull ?: 1.0
        return doubleArrayOf(r, g, b, a)
    }

    /** Length carrier: {type:'length', px} — null px = runtime-dependent. */
    internal fun pxOf(el: JsonElement): Double? {
        val obj = el as? JsonObject ?: return (el as? JsonPrimitive)?.doubleOrNull
        return (obj["px"] as? JsonPrimitive)?.doubleOrNull
    }

    /**
     * Structural JSON lerp for transform-shaped payloads: identical
     * structure → numeric leaves interpolate, strings/booleans must match
     * exactly (that's the css-transforms-1 "matching function lists" test
     * falling out of the byte shape: equal `fn` names, equal arities).
     * Provenance keys ("original") are DROPPED — they're metadata, and a
     * lerped frame has no meaningful authored source text.
     */
    internal fun lerpJson(a: JsonElement, b: JsonElement, t: Double): JsonElement? {
        return when {
            a is JsonPrimitive && b is JsonPrimitive -> {
                val na = a.doubleOrNull
                val nb = b.doubleOrNull
                when {
                    // Two numbers → linear interpolation (§2 rule 1).
                    na != null && nb != null -> JsonPrimitive(lerp(na, nb, t))
                    // Equal non-numeric primitives (fn names, keywords) hold.
                    a.contentOrNull == b.contentOrNull -> a
                    else -> null
                }
            }
            a is JsonObject && b is JsonObject -> {
                // Drop provenance before the structural comparison so a
                // stop with `original` and one without still match.
                val ka = a.keys - "original"
                val kb = b.keys - "original"
                if (ka != kb) return null
                val out = LinkedHashMap<String, JsonElement>(ka.size)
                for (k in ka) {
                    val v = lerpJson(a.getValue(k), b.getValue(k), t) ?: return null
                    out[k] = v
                }
                JsonObject(out)
            }
            a is JsonArray && b is JsonArray -> {
                // Non-matching lengths = non-matching function lists → step.
                if (a.size != b.size) return null
                val out = ArrayList<JsonElement>(a.size)
                for (i in a.indices) {
                    val v = lerpJson(a[i], b[i], t) ?: return null
                    out.add(v)
                }
                JsonArray(out)
            }
            else -> null
        }
    }

    /** Plain linear interpolation — §2 "numbers linear". */
    internal fun lerp(a: Double, b: Double, t: Double): Double = a + (b - a) * t
}
