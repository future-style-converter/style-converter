package com.styleconverter.test.style.core.types

// Phase 1 primitive: a sealed, exhaustive representation of every CSS length
// shape produced by the Style-Converter IR. Extractors never throw and never
// return null — they always return a LengthValue (Unknown on parse failure).
//
// Reference fixtures (under examples/primitives/):
//   lengths-absolute.json       — px/pt/cm/mm/in/pc/Q
//   lengths-font-relative.json  — em/rem/ex/ch/cap/ic/lh/rlh
//   lengths-viewport.json       — vw/vh/vmin/vmax/vi/vb + svw..dvmax
//   lengths-container.json      — cqw/cqh/cqi/cqb/cqmin/cqmax
//   lengths-intrinsic.json      — "auto", "min-content", "max-content"
//   lengths-special.json        — percentages, fr tracks

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonPrimitive

/** Every length-ish IR shape normalizes to one of these variants. */
sealed interface LengthValue {
    /** An absolute pixel value we can hand straight to the renderer. */
    data class Exact(val px: Double) : LengthValue

    /** Relative units (em, %, vw, cqw, …) that need runtime context. */
    data class Relative(
        val value: Double,
        val unit: LengthUnit,
        val pxFallback: Double?, // null when no absolute fallback is known
    ) : LengthValue

    /** The bare string `"auto"` — CSS intrinsic sizing keyword. */
    data object Auto : LengthValue

    /**
     * Intrinsic keywords that are not "auto" (min-content, max-content,
     * fit-content). `fit-content(<len>)` carries an optional bounding length
     * that caps the intrinsic size; bare `fit-content` without an argument is
     * emitted as Generic by the parser today and never reaches this extractor.
     */
    data class Intrinsic(val kind: IntrinsicKind, val bound: LengthValue? = null) : LengthValue

    /** CSS Grid track fraction (`1fr`) — only legal inside grid templates. */
    data class Fraction(val fr: Double) : LengthValue

    /** Unresolved `calc(...)` expressions we preserve verbatim for later. */
    data class Calc(val expression: String) : LengthValue

    /**
     * Explicit CSS `none` keyword on min/max sizing properties. Distinct from
     * Unknown (missing/unparseable) and Auto (intrinsic). Emitted by the IR as
     * `{"type":"none"}` on MaxWidth/MaxHeight when the author writes `none`.
     */
    data object None : LengthValue

    /** Parse failure — preferred over null so callers can `when`-exhaust. */
    data object Unknown : LengthValue

    /** Named intrinsic sizes. */
    enum class IntrinsicKind { MIN_CONTENT, MAX_CONTENT, FIT_CONTENT }
}

/**
 * Every length unit the IR can emit. Mirrors `app.irmodels.IRLength.LengthUnit`
 * on the codegen side. Keep in sync when new units are added upstream.
 */
enum class LengthUnit {
    // Absolute (all normalize to px in IR, but we still carry the tag).
    PX, PT, CM, MM, IN, PC, Q, DP, SP,
    // Font-relative.
    EM, REM, EX, CH, CAP, IC, LH, RLH,
    // Classic viewport.
    VW, VH, VMIN, VMAX, VI, VB,
    // Small viewport.
    SVW, SVH, SVMIN, SVMAX, SVI, SVB,
    // Large viewport.
    LVW, LVH, LVMIN, LVMAX, LVI, LVB,
    // Dynamic viewport.
    DVW, DVH, DVMIN, DVMAX, DVI, DVB,
    // Container query.
    CQW, CQH, CQI, CQB, CQMIN, CQMAX,
    // Special.
    PERCENT, FR,
    // Unknown/unsupported unit string from IR — preserved so we don't lose data.
    UNKNOWN,
}

/** Parse a [LengthUnit] from the IR's uppercase unit string. */
internal fun parseLengthUnit(name: String?): LengthUnit {
    if (name == null) return LengthUnit.UNKNOWN
    // IR emits unit names in uppercase already (see IRLengthSerializer), but
    // we uppercase defensively so we accept hand-written fixtures too.
    return try {
        LengthUnit.valueOf(name.uppercase())
    } catch (_: IllegalArgumentException) {
        LengthUnit.UNKNOWN
    }
}

/**
 * Normalize any IR length JSON shape into a [LengthValue]. Never throws.
 *
 * Supported shapes (see fixture notes above):
 *   "auto" | "min-content" | "max-content"      → Auto / Intrinsic
 *   { "fr": N }                                  → Fraction (grid only)
 *   { "type": "length", "px": N }                → Exact
 *   { "type": "length", "px": N, "original": … } → Exact (absolute inputs)
 *   { "type": "length", "original": { v, u } }   → Relative (no px fallback)
 *   { "type": "percentage", "value": N }         → Relative(PERCENT)
 *   { "px": N }                                  → Exact  (raw shape, e.g. PaddingTop)
 *   { "original": { v, u } }                     → Relative (raw, pxFallback=null)
 *   { "calc": "…" }                              → Calc
 */
fun extractLength(json: JsonElement?): LengthValue {
    if (json == null) return LengthValue.Unknown
    // Bare string primitives carry intrinsic keywords ("auto" etc.).
    if (json is JsonPrimitive) {
        // Bare numeric primitives show up on padding/margin longhands as the
        // percentage shape (spec: "padding: 10%" → data: 10.0). See
        // examples/properties/spacing/padding-units.json → Padding_Percent_10.
        // We treat bare numbers as PERCENT so shorthand-expanded % survives
        // the IR round-trip. Other wrapper shapes handle non-% percentages.
        if (!json.isString) {
            val num = json.doubleOrNull ?: return LengthValue.Unknown
            return LengthValue.Relative(num, LengthUnit.PERCENT, pxFallback = null)
        }
        return extractIntrinsicKeyword(json.content) ?: LengthValue.Unknown
    }
    if (json !is JsonObject) return LengthValue.Unknown

    // Grid-only fraction shape: { "fr": 1.0 }.
    json["fr"]?.jsonPrimitive?.doubleOrNull?.let { return LengthValue.Fraction(it) }

    // Min/Max sizing explicit `none`: {"type":"none"}. Seen in
    // examples/properties/sizing/width-constraints.json → MaxWidth_None.
    if ((json["type"] as? JsonPrimitive)?.content == "none") return LengthValue.None

    // Bounded fit-content: {"fit-content": <inner-length>}. The inner element
    // recurses through extractLength so px/em/% shapes all work as bounds.
    // Seen in width-intrinsic.json → Width_FitContent_Bounded_200px.
    json["fit-content"]?.let { inner ->
        val bound = extractLength(inner)
        return LengthValue.Intrinsic(LengthValue.IntrinsicKind.FIT_CONTENT, bound)
    }

    // Unresolved calc() — the codegen might emit { "calc": "…" } in future.
    (json["calc"] as? JsonPrimitive)?.content?.let { return LengthValue.Calc(it) }

    // New-style calc shape carried by padding-units.json → Padding_Calc_Mixed:
    // data is { "expr": "calc(10px + 5px)" }. We preserve the expression
    // verbatim so later CalcExpressionEvaluator runs can resolve it.
    (json["expr"] as? JsonPrimitive)?.content?.let { return LengthValue.Calc(it) }

    // Percentages on sizing properties use their own wrapper shape.
    if ((json["type"] as? JsonPrimitive)?.content == "percentage") {
        val v = json["value"]?.jsonPrimitive?.doubleOrNull ?: return LengthValue.Unknown
        return LengthValue.Relative(v, LengthUnit.PERCENT, pxFallback = null)
    }

    // Both the "type: length" wrapper and the raw shape share the rest of the
    // decoding logic — we just read px and original from whichever object we
    // have in hand. This handles the two-tier shape quirk noted in the spec.
    val px = json["px"]?.jsonPrimitive?.doubleOrNull
    val original = json["original"] as? JsonObject

    // Absolute units: pixel value is present and authoritative.
    if (px != null && original == null) return LengthValue.Exact(px)
    if (px != null && original != null) {
        // For absolute units IR still emits the original (e.g. PT). The px
        // value is canonical, so we drop to Exact. Callers that want the
        // source unit can still inspect the raw JSON separately.
        return LengthValue.Exact(px)
    }

    // Relative units: no px fallback; we carry the value+unit verbatim.
    if (original != null) {
        val v = original["v"]?.jsonPrimitive?.doubleOrNull ?: return LengthValue.Unknown
        val unit = parseLengthUnit((original["u"] as? JsonPrimitive)?.content)
        return LengthValue.Relative(v, unit, pxFallback = null)
    }

    return LengthValue.Unknown
}

/** Map a string primitive to an intrinsic length keyword, or null. */
private fun extractIntrinsicKeyword(s: String): LengthValue? = when (s) {
    "auto" -> LengthValue.Auto
    "min-content" -> LengthValue.Intrinsic(LengthValue.IntrinsicKind.MIN_CONTENT)
    "max-content" -> LengthValue.Intrinsic(LengthValue.IntrinsicKind.MAX_CONTENT)
    "fit-content" -> LengthValue.Intrinsic(LengthValue.IntrinsicKind.FIT_CONTENT)
    else -> null
}
