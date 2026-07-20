package com.styleconverter.runtime.layout.position

// Wave 18 (RC2) — abspos inset-stretch sizing, css-position-3 §3.5:
// an absolutely positioned box whose axis has BOTH opposing insets
// non-auto and NO explicit size on that axis is sized to the
// inset-modified containing block (cb − inset1 − inset2). Both natives
// measured the abspos child UNBOUNDED (Compose absposOverflowMeasure's
// Constraints(); iOS mirrors it), under which an empty inset-stretched
// div collapsed to 0×0 — css-sizing abspos-003/004's green square was
// entirely absent. After the stretch, a declared aspect-ratio resolves
// with the INLINE axis owning the ratio (css-sizing-4 §5 interaction
// with §3.5: the ref renders abspos-003's all-0-inset 1:1 box as
// 100×100 — inline stretch 100, block DERIVED from the ratio — never
// 100×500 from the block stretch).
//
// The core resolver is PURE MATH with a signature mirrored verbatim by
// the iOS twin (StyleEngine/layout/position/AbsposInsetStretch.swift)
// so cross-native probes can diff the two rule tables directly.

// JSON wire access for the Compose-side injection wrapper only — the
// resolver itself never touches the wire.
import com.styleconverter.runtime.core.ir.IRProperty
import com.styleconverter.runtime.core.types.LengthValue
import com.styleconverter.runtime.core.types.extractLength
import com.styleconverter.runtime.core.variables.ContainingBlock
import com.styleconverter.runtime.sizing.extractAspectRatio
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

object AbsposInsetStretch {

    /**
     * The resolved injection for one box: px sizes to force onto the
     * still-auto axes (null = leave the axis alone). Never overrides an
     * author-declared size.
     */
    data class Resolved(val widthPx: Double?, val heightPx: Double?)

    /** The no-op result — identity guard for every non-stretch box. */
    private val NONE = Resolved(null, null)

    /**
     * The pin table (mirrored on iOS byte-for-byte):
     *  - S1 (stretch, css-position-3 §3.5.3): an axis is stretchable iff
     *    it has NO author size, BOTH opposing insets are known px, and
     *    the containing-block axis is known → candidate =
     *    max(0, cb − start − end). Percent/auto/unknown insets and
     *    unknown cb axes conservatively disable the stretch (nothing
     *    honest to resolve against).
     *  - S2 (no ratio): each stretchable axis is injected as-is.
     *  - S3 (ratio present, css-sizing-4 §5): the inline (width) axis
     *    wins — if width is determined (explicit px or stretch) and
     *    height is NOT explicit, height := width / ratio (this is what
     *    overrides abspos-003's 500px block stretch to 100). Otherwise,
     *    if height is determined and width is fully auto, width :=
     *    height × ratio (abspos-004: block stretch 50, ratio 2 → 100).
     *    An explicit author size on an axis is never overridden.
     *  - S4 (identity guard): if NEITHER axis stretched, return NONE —
     *    explicit-size + ratio boxes (abspos-001/002) keep the existing
     *    SizingApplier aspect-ratio path untouched, and every committed
     *    baseline render is byte-identical.
     *
     * All parameters are px in the runtime's px==dp space; `ratio` is
     * CSS width/height (> 0), null when absent or auto-only.
     */
    fun resolve(
        cbW: Double?, cbH: Double?,
        left: Double?, right: Double?, top: Double?, bottom: Double?,
        explicitW: Double?, explicitH: Double?,
        hasExplicitW: Boolean, hasExplicitH: Boolean,
        ratio: Double?,
    ): Resolved {
        // S1 — per-axis stretch candidates. coerceAtLeast(0): a box whose
        // insets exceed the containing block clamps to zero, never
        // negative (css-position-3 §3.5.3's over-constrained floor).
        val stretchW = if (!hasExplicitW && left != null && right != null && cbW != null)
            (cbW - left - right).coerceAtLeast(0.0) else null
        val stretchH = if (!hasExplicitH && top != null && bottom != null && cbH != null)
            (cbH - top - bottom).coerceAtLeast(0.0) else null
        // S4 — identity guard: no stretch anywhere → nothing to inject.
        if (stretchW == null && stretchH == null) return NONE
        // S2 — start from the plain stretch result.
        var outW = stretchW
        var outH = stretchH
        // S3 — aspect-ratio resolution with inline-axis priority.
        if (ratio != null && ratio > 0.0) {
            // The determined value per axis: author px first, else stretch.
            val wKnown = explicitW ?: stretchW
            val hKnown = explicitH ?: stretchH
            if (wKnown != null && !hasExplicitH) {
                // Inline determined → block derives from the ratio, even
                // over a block stretch (the abspos-003 100×100 pin).
                outH = wKnown / ratio
            } else if (hKnown != null && !hasExplicitW && wKnown == null) {
                // Block determined, inline fully auto → inline derives
                // (the abspos-004 50×2 → 100 pin).
                outW = hKnown * ratio
            }
        }
        return Resolved(outW, outH)
    }

    /**
     * Compose-side wire wrapper: rewrite an OUT-OF-FLOW component's
     * property list, injecting the resolved stretch sizes as the frozen
     * typed-length wire ({"type":"length","px":N} — the exact shape
     * DynamicValueResolver.lengthJson writes and SizingExtractor reads
     * as LengthValue.Exact), so the size survives the wave-8 unbounded
     * measure via constraint-independent Modifier.width/height. Runs
     * AFTER resolveOutOfFlowPercentSizes so percent sizes are already
     * px. Identity (same list instance) when nothing resolves — the
     * frozen-baseline byte-stability rule (and the WPT-capture gate at
     * the call site keeps every dark-stage path out entirely).
     *
     * Box-sizing note: the injected px is the border-box FRAME extent
     * the inset arithmetic produces; under the WPT content-box default
     * a bordered/padded stretch box would need the band subtraction —
     * the gated tests declare no bands, so the conversion is deferred
     * (documented, not silent: SizingApplier logs its box-sizing path).
     */
    /**
     * Strict px read of one inset side for the STRETCH math only (pin
     * S1's honesty rule, wave-18 skeptic fix): the typed {"px":N} wire is
     * the only shape whose meaning is unambiguously absolute px. The LIVE
     * converter emits a PERCENT inset as a BARE number (`left: 10%` →
     * `"data": 10.0` — pinned against the running converter, 2026-07),
     * which the permissive shared decoder (ValueExtractors.extractDp)
     * reads as px for the offset applier (a pre-existing, documented
     * approximation). SIZING against that mis-read would bake the wrong
     * basis into the box frame, so any present-but-non-{px} shape
     * resolves to null here — the axis conservatively never stretches
     * (exactly what S1 promises for percent insets). A keyword `auto`
     * (bare or {"keyword":"auto"}) counts as ABSENT and falls through to
     * the logical longhand, mirroring PositionConfig's resolved*
     * physical-over-logical precedence (last declaration wins per type,
     * like the extractor's fold).
     */
    private fun strictSidePx(
        properties: List<IRProperty>,
        physical: String,
        logical: String,
    ): Double? {
        // One side: null = absent/auto; (true to null) = present but not
        // honest px (percent bare number, calc, unknown) → disables axis.
        fun read(type: String): Pair<Boolean, Double?>? {
            val data = properties.lastOrNull { it.type == type }?.data ?: return null
            // Keyword auto — bare string or wrapped — IS the initial
            // value (css-position-3 §3.5: the side does not anchor).
            val kw = (data as? JsonPrimitive)?.contentOrNull
                ?: ((data as? JsonObject)?.get("keyword") as? JsonPrimitive)?.contentOrNull
            if (kw?.equals("auto", ignoreCase = true) == true) return null
            // Only the frozen typed-length px shape is trusted for math.
            return true to (data as? JsonObject)?.get("px")?.jsonPrimitive?.doubleOrNull
        }
        val side = read(physical) ?: read(logical) ?: return null
        return side.second
    }

    fun inject(properties: List<IRProperty>, cb: ContainingBlock): List<IRProperty> {
        // Author sizes: any recognized Width/Height (or logical alias)
        // that isn't `auto` counts as explicit; exact px carries a value
        // for the ratio step, non-px explicit (min-content/…) blocks
        // both stretch and derivation on that axis (conservative).
        fun sizeOf(vararg types: String): Pair<Boolean, Double?> {
            for (t in types) {
                val data = properties.firstOrNull { it.type == t }?.data ?: continue
                val v = extractLength(data)
                // Explicit `auto` behaves exactly like an absent size
                // (css-sizing-3 §5: auto IS the initial value); Unknown
                // wire shapes are treated as absent (extractor contract).
                if (v is LengthValue.Auto || v is LengthValue.Unknown) continue
                return true to (v as? LengthValue.Exact)?.px
            }
            return false to null
        }
        val (hasW, wPx) = sizeOf("Width", "InlineSize")
        val (hasH, hPx) = sizeOf("Height", "BlockSize")
        // Ratio via the shared aspect-ratio primitive (0.0 = auto-only).
        val ratio = properties.firstOrNull { it.type == "AspectRatio" }
            ?.let { extractAspectRatio(it.data) }
            ?.ratio?.takeIf { it > 0.0 }
        val resolved = resolve(
            cbW = cb.widthPx?.toDouble(), cbH = cb.heightPx?.toDouble(),
            // Strict {"px":N}-only side reads (see strictSidePx): the
            // offset applier keeps its permissive decoder, but the
            // STRETCH math refuses shapes it cannot honestly resolve.
            left = strictSidePx(properties, "Left", "InsetInlineStart"),
            right = strictSidePx(properties, "Right", "InsetInlineEnd"),
            top = strictSidePx(properties, "Top", "InsetBlockStart"),
            bottom = strictSidePx(properties, "Bottom", "InsetBlockEnd"),
            explicitW = wPx, explicitH = hPx,
            hasExplicitW = hasW, hasExplicitH = hasH,
            ratio = ratio,
        )
        // Identity out when nothing resolved (S4) — keeps remember{} keys
        // and every downstream fast path stable.
        if (resolved.widthPx == null && resolved.heightPx == null) return properties
        // Append the injected sizes AFTER the existing list: extraction is
        // last-writer-wins, and neither axis we inject carries an author
        // value (resolve never overrides explicit sizes), so appending is
        // purely additive.
        return properties + buildList {
            resolved.widthPx?.let {
                add(IRProperty("Width", buildJsonObject {
                    put("type", "length"); put("px", it)
                }))
            }
            resolved.heightPx?.let {
                add(IRProperty("Height", buildJsonObject {
                    put("type", "length"); put("px", it)
                }))
            }
        }
    }
}
