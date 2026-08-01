// DecorationColorOps.kt — PER-LINE decoration colors (applier campaign
// wave 22, lane DECOR, B-RC4b).
//
// BYTE-PARALLEL TWIN of the iOS runtime's
// runtimes/swiftui/Sources/StyleConverterRuntime/StyleEngine/typography/
// decoration/DecorationColorOps.swift — same types, same keyword table,
// same `resolve` ordering, same `ops` colouring. Change one, change both.
// (The BAND GEOMETRY differs by construction and always has: Compose
// anchors on TextLayoutResult BASELINES — `bands` below — while iOS
// anchors on LINE-BOX TOPS — DecorationMetrics.top. That asymmetry
// predates this lane; the colour half is the part that must stay twinned.)
//
// WHY: css-text-decor-3 §2.2 — each decorating box paints ITS line in ITS
// OWN colour — and §2.1 propagates every ancestor's line onto the same
// inline run, so a nested chain paints SEVERAL lines in DIFFERENT colours
// over ONE run. Both native painters resolved exactly one colour (the
// innermost `text-decoration-color`, else the text colour) and stamped
// every band with it, losing every ancestor line AND its colour.
//
// WIRE CONTRACT — reconciled against lane EX2's LANDED extractor
// (tools/titan/extract-fixture.mjs, the "_decorations" banner ~L847 and
// its collapse tests): a collapsed inline run carries
//   _decorations → meta.decorations = [ {line, color?}, … ]
//   * ORDER outermost-first (the collapse root, then each wrapper from
//     outside in), so a descendant's line paints OVER its ancestors'
//     where both land on the same row. (PAINT ORDER — the corrected
//     citation: css-text-decor-3 §5.1 "Painting Order of Text
//     Decorations" pins the per-KIND order bottom-first as shadows →
//     underlines → overlines → TEXT → emphasis marks → line-through. It
//     says nothing about ancestor-vs-descendant within one kind; that is
//     the extractor's outermost-first emission order, which this module
//     preserves and the painters' band order honours. Earlier wave-22
//     comments cited "§2.5" — that section is `text-underline-position`.)
//   * `line` is exactly ONE keyword (an element declaring two contributes
//     two entries), lowercase CSS spelling; lineKindFrom also accepts the
//     IR's screaming spelling (UNDERLINE / LINE_THROUGH) defensively.
//   * `color` is the AUTHORED CSS token, all the way to the wire ("blue",
//     "#00f"). The converter does NOT normalize it to the IR sRGB leaf —
//     `meta` members are extractor-owned payloads forwarded verbatim (the
//     wave-20 `meta.attrs` precedent; rationale in
//     schema/spec/04-metadata-fields.md). DecorationWire.toDecorationLines
//     resolves the token through the runtime's own CSS token parser and
//     hands this module the finished [Rgba]. OMITTED (or unresolvable —
//     logged, never silent) = `currentColor` (§2.2 initial), which is why
//     colours stay nullable all the way to the op, for the PAINTER to
//     substitute.
//   * AUTHORITATIVE when present: the list is the COMPLETE set of lines
//     for the run, so [resolve] ignores the component's own flags there.
//     STYLE and THICKNESS are deliberately NOT per-entry — EX2 folds them
//     into the run's merged flat bag (root-wins), so every line of a
//     collapsed run shares one style and one thickness. A chain mixing
//     `dotted` and `solid` therefore paints all lines with the ROOT's
//     style: a known, contract-level limitation, not a silent drop.
//
// PIXEL ORACLE — the live wave-21 WEB capture (web is the platform that
// matches the browser ref here): tools/titan/runs/wave21-final/sections/
// css-text-decor/report/images/web/wpt__css-text-decor__text-decoration-
// color.png, component `text-decoration-color__7` (IR at …/per-test-ir/
// wpt__css-text-decor__text-decoration-color.json — span[underline blue]
// > span[overline gray] > span[line-through green], text innermost).
// Visual line 0, 16px face, 1px bands, advance 20:
//   row 219 (128,128,128) gray → OVERLINE · 230 (0,128,0) green →
//   LINE_THROUGH · 237 (0,0,255) blue → UNDERLINE
// Three rows, three colours, one run — the Android capture painted ONE
// green row (innermost only), iOS two. The 11 / 18 deltas below the
// overline are exactly what `bands` derives at fontSize 16 (arithmetic in
// DecorationColorOpsTest).
package com.styleconverter.runtime.typography

object DecorationColorOps {

    /** The three css-text-decor-3 §2.1 line keywords, as an OWN enum so
     *  this module stays dependency-free (no IR / Compose imports) and
     *  the JVM pin suite compiles it standalone — the DecorationOps rule. */
    enum class LineKind { UNDERLINE, OVERLINE, LINE_THROUGH }

    /** A decoration colour in normalized sRGB 0..1 — the IR colour leaf
     *  shape (schema/spec/02-values.md), NOT a Compose Color: the painter
     *  converts at draw time so this module has no graphics dependency. */
    data class Rgba(val r: Float, val g: Float, val b: Float, val a: Float = 1f)

    /** One requested decoration line: which kind, in which colour. A null
     *  [color] means `currentColor` (css-text-decor-3 §2.2 initial) — the
     *  painter substitutes the run's resolved text colour. */
    data class DecorationLine(val kind: LineKind, val color: Rgba?)

    /** One placed band, now carrying the colour of the line that owns it
     *  (the wave-21 LineBand plus the §2.2 colour). */
    data class ColoredBand(
        val left: Float,      // line's leftmost painted x
        val top: Float,       // snapped top row for THIS kind
        val width: Float,     // the visual line's inked extent
        val thickness: Float, // resolved decoration thickness
        val color: Rgba?      // null = currentColor (painter substitutes)
    )

    /** One paint op carrying ITS line's colour — the B-RC4b deliverable:
     *  the op list is no longer mono-colour. */
    data class ColoredOp(val op: DecorationOps.Op, val color: Rgba?)

    /** Wire token → [LineKind]. Accepts the CSS spelling (`line-through`)
     *  and the IR's screaming spelling (`LINE_THROUGH`, what
     *  TextDecorationLine actually ships — see the live per-test IR),
     *  case-insensitively. Null for `none` / `blink` / anything
     *  unrecognised, so the caller DROPS the entry rather than painting a
     *  wrong line — the no-silent-fallthrough rule. */
    fun lineKindFrom(token: String?): LineKind? =
        when (token?.lowercase()?.replace('_', '-')) {
            "underline" -> LineKind.UNDERLINE
            "overline" -> LineKind.OVERLINE
            "line-through" -> LineKind.LINE_THROUGH
            else -> null
        }

    /** One `meta.decorations` entry → a [DecorationLine], or null when the
     *  `line` token is not one of the three paintable keywords. */
    fun decorationLine(line: String?, color: Rgba?): DecorationLine? =
        lineKindFrom(line)?.let { DecorationLine(it, color) }

    /**
     * The ordered line list the painter walks.
     *
     * [wire] PRESENT (a collapsed chain) → it IS the list, order kept:
     * ancestor-first, so later entries paint over earlier ones where two
     * decorating boxes put a line on the same row (emission order IS paint
     * order in both painters; see the banner's §5.1 note).
     *
     * AUTHORITATIVE-WHEN-PRESENT INCLUDES THE EMPTY LIST. A present-but-
     * empty wire means "this run's complete line set is: nothing" — it
     * arises when DecorationWire's known-keyword filter drops every entry
     * — and it MUST paint nothing. Falling back to the component's flags
     * there would re-enable the legacy path from the merged flat bag and
     * paint lines the authoritative list just said were unpaintable, i.e.
     * the exact double-source bug the wire exists to remove. Callers must
     * therefore ALSO suppress the platform built-ins on `wire != null`,
     * not on "the resolved list is non-empty" (see ComponentRenderer's
     * `paintedTextStyle` gate and the iOS `overlayOwns` twin).
     *
     * [wire] null (every legacy document, and every run the extractor did
     * not collapse) → synthesize this box's own lines in the LEGACY order
     * underline → overline → line-through with null colours, so the
     * painter substitutes exactly what it substituted before: same bands,
     * same order, same colour. That is the dark-stage 327 byte-identity
     * guarantee.
     */
    fun resolve(
        wire: List<DecorationLine>?,
        underline: Boolean,
        overline: Boolean,
        lineThrough: Boolean
    ): List<DecorationLine> {
        // Collapsed run: the merged list is authoritative (it already
        // encodes every ancestor's line AND colour) — empty included.
        if (wire != null) return wire
        // Legacy single-box path — byte-identical to decorationSegments'
        // emit order (underline, overline, line-through).
        return buildList {
            if (underline) add(DecorationLine(LineKind.UNDERLINE, null))
            if (overline) add(DecorationLine(LineKind.OVERLINE, null))
            if (lineThrough) add(DecorationLine(LineKind.LINE_THROUGH, null))
        }
    }

    /** Snapped top row for ONE kind on ONE visual line, baseline-anchored
     *  (Compose text-layout space). The formulas ARE the wave-5
     *  capture-pinned ones from TextStyleApplier.decorationSegments with
     *  the kind lifted out of the fixed emit order, so an AUTO call
     *  ([explicit] = false, [thicknessPx] = decorationThicknessPx)
     *  reproduces those rows byte-for-byte; [explicit] = true swaps in
     *  Blink's ref-pinned underline gap (explicitUnderlineGapPx). */
    fun bandTop(
        kind: LineKind,
        baselinePx: Float,
        fontSizePx: Float,
        thicknessPx: Float,
        explicit: Boolean
    ): Float = when (kind) {
        // Gap below the baseline: auto keeps the measured gap == T rule,
        // explicit uses max(1, ceil(T/2)) (dotted-ref-pinned).
        LineKind.UNDERLINE -> baselinePx +
            (if (explicit) DecorationOps.explicitUnderlineGapPx(thicknessPx) else thicknessPx)
        // Bottom edge flush with the line-box top (baseline − ascent).
        LineKind.OVERLINE ->
            baselinePx - Math.round(fontSizePx * DecorationOps.ASCENT_EM) - thicknessPx
        // Strike CENTER at baseline − yStrikeoutPosition·fs; band straddles.
        LineKind.LINE_THROUGH -> Math.round(
            baselinePx - fontSizePx * DecorationOps.STRIKEOUT_EM - thicknessPx / 2f
        ).toFloat()
    }

    /** Per-visual-line coloured bands for the ordered [lines] list — the
     *  coloured twin of TextStyleApplier.decorationSegments (auto) and
     *  DecorationOps.explicitBands (explicit), both of which
     *  DecorationWirePinTest pins this equal to. LINE-MAJOR then request
     *  order, which for the legacy [resolve] output is exactly the legacy
     *  sequence. Empty visual lines paint nothing (the browser draws no
     *  decoration over zero inked extent). */
    fun bands(
        lineCount: Int,
        fontSizePx: Float,
        autoThicknessPx: Float,
        explicitThicknessPx: Float?,
        lines: List<DecorationLine>,
        lineBaseline: (Int) -> Float,
        lineLeft: (Int) -> Float,
        lineRight: (Int) -> Float
    ): List<ColoredBand> {
        // Nothing requested → nothing owned (total, no caller surprises).
        if (lines.isEmpty()) return emptyList()
        // css-text-decor-4 §2.4: a declared thickness wins over `auto`.
        val thickness = explicitThicknessPx ?: autoThicknessPx
        return (0 until lineCount).flatMap { i ->
            // This visual line's inked extent.
            val left = lineLeft(i)
            val right = lineRight(i)
            // Zero inked extent → no decoration on this line.
            if (right <= left) return@flatMap emptyList<ColoredBand>()
            // Integral baseline row — every measured web row is snapped.
            val baseline = Math.round(lineBaseline(i)).toFloat()
            // One band per requested line, in the requested order.
            lines.map { req ->
                ColoredBand(
                    left,
                    bandTop(req.kind, baseline, fontSizePx, thickness, explicitThicknessPx != null),
                    right - left,
                    thickness,
                    req.color
                )
            }
        }
    }

    /**
     * Expand every band through the wave-21 style emitter and TAG each op
     * with its band's colour — the op list the painter consumes. One
     * band's ops share one colour (a decorating box has one); DIFFERENT
     * bands may now differ, which is the whole point of B-RC4b. Style
     * composition is free: a dotted underline in colour A and a solid
     * overline in colour B are two bands through the same expander.
     */
    fun ops(bands: List<ColoredBand>, style: DecorationOps.LineStyle): List<ColoredOp> =
        bands.flatMap { b ->
            DecorationOps.styleOps(b.left, b.top, b.width, b.thickness, style)
                .map { ColoredOp(it, b.color) }
        }
}
