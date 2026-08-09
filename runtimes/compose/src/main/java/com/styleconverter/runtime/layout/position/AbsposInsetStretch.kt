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
     *  - S5 (wave 31, css-tables-3 §"Abspos tables"): a TABLE box's
     *    available space "can never exceed the available space of the
     *    containing block" (the assert text of css-tables/
     *    absolute-tables-008…011). The inset-modified containing block is
     *    NOT that bound: a NEGATIVE start inset makes cb − start − end
     *    LARGER than cb, and a table — unlike a block — does not stretch
     *    into it. Measured on absolute-tables-009 (cb 100×100, `left:
     *    -100; right: 0`): S1 hands 100 − (−100) − 0 = 200, and both
     *    natives painted a 200×100 green band where the Chromium ref
     *    paints 100×100 (frozen captures, wave30-final/sections/
     *    css-tables — Android 0.9398 FAIL, iOS 0.9503). Clamping the
     *    candidate to the containing-block extent reproduces the ref.
     *    Applies to TABLE boxes ONLY (`isTable`) so every non-table
     *    abspos stretch — the whole css-position / css-sizing baseline —
     *    stays byte-identical; a block box legitimately stretches past
     *    its containing block under a negative inset (css-position-3
     *    §3.5.3 has no such clamp).
     *  - S6 (wave 38, css-sizing-4 §4.1): an AUTHOR-DEFINITE block size
     *    ([explicitH]) plus a ratio determines the inline size, so the
     *    inline axis is NOT auto for §3.5.3 — width := explicitH × ratio
     *    wins over the inline stretch (abspos-006: cb 500 wide, all-0
     *    insets, `height: 100px; aspect-ratio: 1/1` → 100×100, not
     *    500×100). Ordered FIRST inside S3 because the case fell through
     *    every other branch; nothing S3 already handled changes.
     *
     * All parameters are px in the runtime's px==dp space; `ratio` is
     * CSS width/height (> 0), null when absent or auto-only. `isTable`
     * is the css-display-3 table-ish classification of THIS box (see
     * [isTableBox]) and defaults to false so every pre-wave-31 call site
     * keeps S1 verbatim.
     */
    fun resolve(
        cbW: Double?, cbH: Double?,
        left: Double?, right: Double?, top: Double?, bottom: Double?,
        explicitW: Double?, explicitH: Double?,
        hasExplicitW: Boolean, hasExplicitH: Boolean,
        ratio: Double?,
        isTable: Boolean = false,
    ): Resolved {
        // S1 — per-axis stretch candidates. coerceAtLeast(0): a box whose
        // insets exceed the containing block clamps to zero, never
        // negative (css-position-3 §3.5.3's over-constrained floor).
        // S5 folds in as a per-axis ceiling: for a table the candidate may
        // never exceed the containing block's own extent (css-tables-3).
        fun clampToCb(candidate: Double, cb: Double) =
            if (isTable) minOf(candidate, cb) else candidate
        val stretchW = if (!hasExplicitW && left != null && right != null && cbW != null)
            clampToCb((cbW - left - right).coerceAtLeast(0.0), cbW) else null
        val stretchH = if (!hasExplicitH && top != null && bottom != null && cbH != null)
            clampToCb((cbH - top - bottom).coerceAtLeast(0.0), cbH) else null
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
            if (explicitH != null && !hasExplicitW && stretchW != null) {
                // S6 (wave 38, lane N7) — an AUTHOR-DEFINITE block size
                // plus a ratio determines the inline size, and that wins
                // over the §3.5.3 inline stretch. css-sizing-4 §4.1 makes
                // the automatic inline size blockSize × ratio, so the
                // inline axis is no longer `auto` for css-position-3
                // §3.5.3's over-constrained equation — the END inset is
                // the side that gets ignored (LTR), not the ratio.
                // Measured on css-sizing/aspect-ratio/abspos-006 (cb
                // 500×100, `left/right/top/bottom: 0; height: 100px;
                // aspect-ratio: 1/1`): both natives painted the 500-wide
                // stretch — a full-canvas green BAR in the frozen
                // wave37-final captures (Android 0.8929, iOS 0.9053) —
                // where the Chromium ref paints the 100×100 square.
                // Strictly additive: this case previously fell through
                // every S3 branch (branch 2 needs `!hasExplicitH`, branch
                // 3 needs `wKnown == null`) and kept the raw stretch, and
                // it cannot fire for abspos-003/004/005's pins (no author
                // height, or an author width). A non-px explicit height
                // (`height: 100%` — abspos-009) leaves `explicitH` null
                // and stays on the old path.
                outW = explicitH * ratio
            } else if (wKnown != null && !hasExplicitH) {
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

    /**
     * Is this box a TABLE box for S5's purposes (css-display-3 §2:
     * `table` and `inline-table` both generate a table wrapper box, and
     * css-tables-3's abspos available-space rule is written against the
     * table box, not the caption/row/cell internals)?
     *
     * Two channels, in css-cascade order:
     *  1. A DECLARED `Display` always wins. Read from the raw wire
     *     because the runtime's DisplayConfig enum predates the table
     *     lane and folds several table-internal keywords together; the
     *     serialized keyword is the honest source (the converter emits
     *     SCREAMING_SNAKE — `"TABLE"` / `"INLINE_TABLE"`). Last
     *     declaration wins, matching every other reader in this file.
     *     A declared non-table display makes this NOT a table even on a
     *     `<table>` element (css-display-3 §2).
     *  2. With NO declared display, the originating tag's UA default
     *     decides — `<table>` is `display: table` per the HTML UA
     *     stylesheet, and the converter does NOT serialize UA defaults.
     *     This channel is load-bearing, not belt-and-braces: the live
     *     absolute-tables-008…011 IRs carry the table ONLY as
     *     `meta.sourceTag: "table"` with no Display property at all
     *     (absolute-tables-016 is the one that declares `display:
     *     table` in author CSS), so S5 would never fire on the very
     *     tests it was measured against. `tag` is [IRComponent._tag].
     *
     * Anything else is NOT a table — S5 then never fires and S1 is
     * byte-identical.
     *
     * Twin: AbsposInsetStretch.isTableBox(from:tag:) on iOS.
     */
    fun isTableBox(properties: List<IRProperty>, tag: String? = null): Boolean {
        val kw = (properties.lastOrNull { it.type == "Display" }?.data as? JsonPrimitive)
            ?.contentOrNull
        if (kw != null) {
            return kw.equals("TABLE", ignoreCase = true) ||
                kw.equals("INLINE_TABLE", ignoreCase = true)
        }
        // UA default. Only `<table>` maps to `display: table`; no HTML
        // element defaults to `inline-table`.
        return tag?.equals("table", ignoreCase = true) == true
    }

    fun inject(
        properties: List<IRProperty>,
        cb: ContainingBlock,
        tag: String? = null,
    ): List<IRProperty> {
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
            // S5 — the css-tables-3 available-space ceiling applies to
            // table boxes only; every other box keeps S1 verbatim.
            isTable = isTableBox(properties, tag),
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
