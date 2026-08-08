package com.styleconverter.runtime.rendering

import com.styleconverter.runtime.PropertyTracker
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.floatOrNull

/**
 * IR `Zoom` → [ZoomConfig].
 *
 * Mirrors the reader's value flavors one branch per sealed variant of
 * `ZoomValue` in `converter/src/main/kotlin/app/parsing/css/properties/
 * longhands/rendering/ZoomPropertyParser.kt`, and is the byte-parallel
 * twin of `runtimes/swiftui/.../StyleEngine/rendering/ZoomExtractor.swift`
 * and `runtimes/web/src/engine/rendering/ZoomExtractor.ts`.
 *
 * The wire shape the converter actually emits is a TAGGED OBJECT — e.g.
 * `{"type":"number","value":1.5}` for `zoom: 1.5` (grep `"type": "Zoom"`
 * in `out/tmpOutput.json`). Before this file grew the object branch it
 * only understood `JsonPrimitive`, so every real document fell through to
 * `ZoomConfig()` and `zoom` was silently dropped on Android. The bare
 * primitive branch is kept for hand-written fixtures and for a future
 * wire that flattens tag-only variants.
 */
object ZoomExtractor {

    fun extractZoomConfig(properties: List<Pair<String, JsonElement?>>): ZoomConfig {
        // Last write wins, matching the cascade every other extractor uses
        // (a later declaration of the same property overrides the earlier).
        var result = ZoomConfig()
        for ((type, data) in properties) {
            if (type == "Zoom") result = extractZoom(data)
        }
        return result
    }

    private fun extractZoom(data: JsonElement?): ZoomConfig {
        if (data == null) return ZoomConfig()

        return when (data) {
            // Tagged-object wire — the shape the converter emits today.
            is JsonObject -> fromTaggedObject(data)
            // Bare primitive: "normal"/"reset" keyword, or a raw number.
            is JsonPrimitive -> fromPrimitive(data)
            // Arrays et al. are not a shape `zoom` can take. Reported via
            // the PropertyTracker (the runtime's loud channel per CLAUDE.md
            // "no silent fallthroughs"), never swallowed.
            else -> {
                PropertyTracker.markUnhandled("Zoom")
                ZoomConfig()
            }
        }
    }

    /** `{"type":"number"|"percentage"|"normal"|"reset", "value": …}`. */
    private fun fromTaggedObject(o: JsonObject): ZoomConfig {
        val tag = (o["type"] as? JsonPrimitive)?.contentOrNull?.lowercase()
        // Both numeric variants carry the magnitude under `value`; the
        // reader wraps them in IRNumber / IRPercentage, which serialize as
        // a bare JSON number (see IRNumber/IRPercentage in ValueTypes.kt).
        val raw = (o["value"] as? JsonPrimitive)?.floatOrNull
        return when (tag) {
            // `<number>`: 1 is unzoomed, 2 doubles the used values.
            "number" -> raw?.let { factor(it) } ?: ZoomConfig()
            // `<percentage>`: the SUFFIX is load-bearing — `zoom: 150%`
            // and `zoom: 150` differ by two orders of magnitude, which is
            // exactly the corruption web's ZoomExtractor.ts calls out.
            "percentage" -> raw?.let { factor(it / 100f) } ?: ZoomConfig()
            // css-viewport-1: `normal` computes to the same used scale as 1.
            "normal" -> ZoomConfig(zoom = 1.0f, isNormal = true)
            // Legacy WebKit `reset` — identity here, see ZoomConfig's doc
            // for why that is the CONVERGENT reading, not a fallthrough.
            "reset" -> ZoomConfig(zoom = 1.0f, isNormal = true)
            // A variant the reader grew without this extractor — loud,
            // per the PropertyRegistry introspection contract.
            else -> {
                PropertyTracker.markUnhandled("Zoom")
                ZoomConfig()
            }
        }
    }

    /** Bare `"normal"` / `"reset"` / `1.5` payloads. */
    private fun fromPrimitive(p: JsonPrimitive): ZoomConfig {
        val content = p.contentOrNull?.lowercase()
        if (content == "normal" || content == "reset") {
            return ZoomConfig(zoom = 1.0f, isNormal = true)
        }
        return p.floatOrNull?.let { factor(it) } ?: ZoomConfig()
    }

    /**
     * Builds a non-normal config, rejecting factors the layout pass cannot
     * honour. css-viewport-1 clamps `zoom` to a positive value; a zero or
     * negative factor would divide the incoming constraints by <= 0 (NaN /
     * negative extents) and a non-finite one would poison the measure pass,
     * so both degrade to identity with a report instead of crashing.
     */
    private fun factor(f: Float): ZoomConfig {
        if (!f.isFinite() || f <= 0f) {
            PropertyTracker.markUnhandled("Zoom")
            return ZoomConfig()
        }
        return ZoomConfig(zoom = f, isNormal = false)
    }

    fun isZoomProperty(type: String): Boolean = type == "Zoom"
}
