package com.styleconverter.runtime.layout.position

// Wave 31 (lane T) — AUTO-MARGIN resolution for absolutely positioned
// boxes, CSS 2.1 §10.3.7 (inline axis) / §10.6.4 (block axis).
//
// ## The measured defect
// css-tables/absolute-tables-016 centres a 100×100 abspos table in a
// 160×160 `position:relative` container with `inset: 0; margin: auto`.
// §10.3.7: with `left`, `width` and `right` all non-auto and BOTH inline
// margins auto, the equation `left + ml + width + mr + right = cb` is
// over-constrained, and the extra constraint is that the two auto
// margins get EQUAL values — i.e. each absorbs half the free space
// (160 − 0 − 100 − 0)/2 = 30. §10.6.4 says the identical thing for the
// block axis. The container's own `left:-30; top:-30` relative shift
// then cancels the centring, so the ref paints the green square exactly
// at the container's static position.
//
// Compose got the INLINE half right by accident and the BLOCK half not
// at all: ComponentRenderer.autoMarginAlignment implements only CSS 2.1
// §10.3.3 (the IN-FLOW block-level rule) and returns a 2D `Alignment`
// whose vertical component is always `Top`, applied through a
// `Modifier.fillMaxWidth()` box. In the wave30-final frozen capture
// (tools/titan/runs/wave30-final/sections/css-tables/android-screenshots/
// wpt__css-tables__absolute-tables-016.png) the green square measures
// 100×100 at (16, 58) where the Chromium ref
// (tools/wpt/refs/<sha>/white-black-ink-font-lh-imgpad-htmlpins/
// css-tables/absolute-tables-016.png) puts it at (16, 88): the x is
// right (the fillMaxWidth centring), the y is exactly 30px — one
// block-axis auto margin — too high. Android scored 0.9486 (FAIL); iOS
// passed because MarginApplier's VAutoFrameModifier already expands a
// both-auto block axis and centres in it.
//
// ## Why this resolves the margins into the INSET rather than aligning
// An alignment-based centring can only ever produce the SYMMETRIC
// answer: it splits the whole containing block, ignoring the insets. The
// §10.3.7 answer is `cb − start − size − end`, which differs from the
// alignment answer for every non-zero inset (left:20/right:0/w:100 in a
// 160 cb → margins 20 each → used x = 40; alignment would say 30). This
// object resolves the margins ARITHMETICALLY and folds the start margin
// into the start inset, so the existing offset machinery
// (PositionApplier's absoluteOffset, css-position-3 §3.1) paints the
// used position with no new mounting slot — and it composes with the
// wave-25 hoist contract and the wave-28 nested end-inset machinery
// unchanged, because both of those read the SAME inset wire this rewrite
// hands them (and `anchorsFromEnd*` is false whenever M2 holds: the
// start inset is by definition non-auto).
//
// The rewrite also ZEROES the resolved auto margins, which is what makes
// the two halves not double-count: with `MarginLeft` no longer `auto`,
// ComponentRenderer.autoMarginAlignment's §10.3.3 centring stops firing
// (and on iOS the H/VAutoFrameModifier frames stop expanding), leaving
// exactly one implementation of the rule per axis.
//
// ## The DECLARED opposite margin (skeptic bugs 1 & 2)
// §10.3.7's equation has SIX terms, not four: `left + ml + width + mr +
// right = cb`. The first cut of this file dropped `ml`/`mr` from the free
// space and zeroed BOTH sides of a resolved axis, so a declared margin
// opposite an `auto` one was both ignored in the arithmetic and deleted
// from the wire. Two Chromium-verified misses (probe:
// _diag31/skeptic/chromium-automargin-probe.mjs, cases C/F and the
// rectangular-cb K/L/M):
//   - `left:0;right:0;width:100px;margin-left:auto;margin-right:20px` in a
//     160px cb — Chromium paints x=40 with margins `40px 20px`; this file
//     answered x=0 and dropped the 20px.
//   - `margin-left:10%;margin-right:auto`, same shape — Chromium resolves
//     the percent against the cb and paints x=16 with margins `16px 44px`;
//     this file answered x=0 and rewrote MarginLeft to 0.
// So: [split] SUBTRACTS the declared opposite margin (M3) and reports it
// back as the used value on the non-auto side (M6), and [inject] rewrites
// ONLY the sides that were genuinely `auto`. The percent basis for a
// declared margin is the containing block's INLINE size on BOTH axes
// (CSS 2.1 §8.3) — probe case K pins it: in a 160×120 cb a block-axis
// `margin-top:10%` is 16px, not 12px, and both natives' margin appliers
// already use that same basis (MarginApplier's P10 percent lane here,
// `SpacingResolver.percentBasisPx` on iOS), so a preserved percent margin
// paints exactly the band this arithmetic assumed.
//
// ## Blast radius
// M2 (all three of start inset / size / end inset definite) plus the
// zero-split identity guard (M7) mean the ONLY box in the whole fixture
// corpus this rewrites is absolute-tables-016's pair — verified by
// scanning every `fixtures/**/*.json` for an out-of-flow box with any
// auto margin: the css-align `abspos__*-stretch-auto-margins*` boxes all
// resolve to a 0/0 split (their stretched size exactly fills the
// inset-modified containing block) and take M7; filter-effects
// `backdrop-filter-basic-blur` has an `auto` END inset and takes M2. No
// corpus box mixes a declared margin with an auto one, so the bug-1/2 fix
// is provably capture-neutral — it only widens what the rule answers
// CORRECTLY when such a box arrives.
//
// Pure decisions + arithmetic (Double), so the whole rule table is
// pinnable on the JVM without Robolectric — the standing constraint of
// this suite. Twin: StyleEngine/layout/position/AbsposAutoMargin.swift.

// JSON wire access for the Compose-side injection wrapper only — the
// resolver itself never touches the wire.
import com.styleconverter.runtime.core.ir.IRProperty
import com.styleconverter.runtime.core.types.LengthUnit
import com.styleconverter.runtime.core.types.LengthValue
import com.styleconverter.runtime.core.types.extractLength
import com.styleconverter.runtime.core.variables.ContainingBlock
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

object AbsposAutoMargin {

    /** The resolved used margins for ONE axis, in px. */
    data class Axis(val startPx: Double, val endPx: Double)

    /**
     * CSS 2.1 §10.3.7 / §10.6.4 auto-margin resolution for one axis of an
     * absolutely positioned box. Returns null when the rule does not
     * apply — the caller then leaves the wire untouched.
     *
     * The pin table (mirrored on iOS byte-for-byte):
     *  - M1: no auto margin on this axis → null. §10.3.7 only solves for
     *    margins that are `auto`; a declared margin is used as declared.
     *  - M2: any of the containing-block extent, the start inset, the end
     *    inset or the used size is indefinite → null. §10.3.7's
     *    over-constrained branch is reached only when all three of
     *    start/size/end are non-auto; with one of them auto the spec
     *    instead sets the auto margins to ZERO and solves for that value,
     *    which is exactly the pre-wave-31 behaviour (the auto margin
     *    contributes nothing to the used position), so returning null
     *    here is the identity, not a silent fallthrough.
     *  - M2b: a DECLARED margin on the non-auto side that cannot be read
     *    as px (an `em`, an unresolved `calc()`, a percent with no known
     *    basis) → null. §10.3.7 cannot be solved without every term, and
     *    guessing zero would silently move the box; the identity leaves
     *    the pre-wave-31 render, which is the honest answer.
     *  - M3: free = cb − start − size − end − declaredStart − declaredEnd,
     *    where a side that IS auto contributes 0 (it is the unknown being
     *    solved for). This is the space the auto margins have to share.
     *    Dropping the declared terms was skeptic bug 1 (see the header).
     *  - M4 (both auto, free ≥ 0): equal split — `free/2` each. The
     *    absolute-tables-016 pin: (160 − 0 − 100 − 0)/2 = 30.
     *  - M5 (both auto, free < 0): §10.3.7 — "unless this would make them
     *    negative, in which case when the direction of the containing
     *    block is ltr, set margin-left to 0 and solve for margin-right".
     *    The engine is LTR-normalized (see PositionConfig's kdoc), so the
     *    START margin is 0 and the END margin takes the whole (negative)
     *    remainder. The box stays flush with its start inset.
     *  - M6 (exactly one auto): that margin absorbs ALL the remaining free
     *    space (§10.3.7: "if there is exactly one value specified as auto
     *    … that value is solved for") and the returned [Axis] reports the
     *    DECLARED value on the other side — so the pair this returns is
     *    always the USED margin pair Chromium's computed style shows
     *    (probe case C: 40/20; case F: 16/44). No clamping: probe case N
     *    confirms Chromium solves a single auto margin NEGATIVE when the
     *    declared opposite margin exceeds the free space (−60/120).
     *
     * All parameters are px in the runtime's px==dp space.
     * [startMarginPx]/[endMarginPx] are the DECLARED margins with percents
     * already resolved against the cb's inline size (§8.3), and are ignored
     * on whichever side is `auto`; null means "declared but unreadable"
     * (M2b). They default to 0 — the CSS initial value — so a caller with
     * no declared margins reads exactly like the pre-bug-fix signature.
     */
    fun split(
        cb: Double?,
        startInset: Double?,
        endInset: Double?,
        sizePx: Double?,
        startAuto: Boolean,
        endAuto: Boolean,
        startMarginPx: Double? = 0.0,
        endMarginPx: Double? = 0.0,
    ): Axis? {
        // M1 — nothing to solve for.
        if (!startAuto && !endAuto) return null
        // M2 — the over-constrained branch needs every other term.
        if (cb == null || startInset == null || endInset == null || sizePx == null) return null
        // M2b — an auto side is the unknown (contributes 0); a declared one
        // must be readable in px or the equation has two unknowns.
        val declaredStart = if (startAuto) 0.0 else (startMarginPx ?: return null)
        val declaredEnd = if (endAuto) 0.0 else (endMarginPx ?: return null)
        // M3 — the free space the auto margins share, net of the declared
        // ones (the full six-term §10.3.7 equation).
        val free = cb - startInset - sizePx - endInset - declaredStart - declaredEnd
        return when {
            // M4/M5 — both auto: equal split, or the LTR over-constrained
            // rule when the equal split would go negative. Both declared
            // terms are 0 here, so `free` is the classic four-term value.
            startAuto && endAuto ->
                if (free >= 0.0) Axis(free / 2.0, free / 2.0) else Axis(0.0, free)
            // M6 — the single auto margin absorbs everything that is left;
            // the other side reports its declared value unchanged.
            startAuto -> Axis(free, declaredEnd)
            else -> Axis(declaredStart, free)
        }
    }

    /** The physical + LTR-logical wire spellings, per axis and per side. */
    private val START_INSET_X = listOf("Left", "InsetInlineStart")
    private val END_INSET_X = listOf("Right", "InsetInlineEnd")
    private val START_INSET_Y = listOf("Top", "InsetBlockStart")
    private val END_INSET_Y = listOf("Bottom", "InsetBlockEnd")
    private val START_MARGIN_X = listOf("MarginLeft", "MarginInlineStart")
    private val END_MARGIN_X = listOf("MarginRight", "MarginInlineEnd")
    private val START_MARGIN_Y = listOf("MarginTop", "MarginBlockStart")
    private val END_MARGIN_Y = listOf("MarginBottom", "MarginBlockEnd")

    /**
     * Strict px read of one inset side — the SAME honesty rule
     * [AbsposInsetStretch.strictSidePx] enforces, and for the same
     * reason: only the typed `{"px":N}` wire is unambiguously absolute
     * px, so a percent inset (which the live converter emits as a BARE
     * number) can never silently become a px basis for this arithmetic.
     * A keyword `auto` counts as ABSENT and falls through to the logical
     * longhand; a present-but-unreadable shape yields null, which M2 then
     * turns into the identity.
     */
    private fun strictInsetPx(properties: List<IRProperty>, types: List<String>): Double? {
        for (t in types) {
            val data = properties.lastOrNull { it.type == t }?.data ?: continue
            val kw = (data as? JsonPrimitive)?.contentOrNull
                ?: ((data as? JsonObject)?.get("keyword") as? JsonPrimitive)?.contentOrNull
            // `auto` is the initial value — try the next spelling.
            if (kw?.equals("auto", ignoreCase = true) == true) continue
            return (data as? JsonObject)?.get("px")?.jsonPrimitive?.doubleOrNull
        }
        return null
    }

    /**
     * The USED size on one axis, in px, or null when indefinite.
     *
     * Reads the LAST recognized declaration so this runs correctly AFTER
     * [AbsposInsetStretch.inject] has appended its stretch sizes — the
     * spec order too: §10.3.7 resolves the used width first and the
     * margins then absorb whatever free space is left.
     */
    private fun usedSizePx(properties: List<IRProperty>, types: List<String>): Double? {
        val data = properties.lastOrNull { it.type in types }?.data ?: return null
        return (extractLength(data) as? LengthValue.Exact)?.px
    }

    /** Is any spelling of this side's margin the `auto` keyword? */
    private fun isAutoMargin(properties: List<IRProperty>, types: List<String>): Boolean =
        properties.any { p ->
            p.type in types &&
                (p.data as? JsonPrimitive)?.contentOrNull?.equals("auto", ignoreCase = true) == true
        }

    /**
     * The DECLARED margin on one side, in px — the M3 term skeptic bug 1
     * was missing. Only called for a side [isAutoMargin] already rejected,
     * so the last recognized spelling is unambiguously a length.
     *
     * Returns 0.0 when the side is absent (CSS initial value), and null
     * when it is present but not px-resolvable, which M2b turns into the
     * identity rather than a guess.
     *
     * [cbInlinePx] is the containing block's INLINE size — the percent
     * basis for margins on BOTH axes per CSS 2.1 §8.3, verified against
     * Chromium with a rectangular 160×120 cb (probe case K: a block-axis
     * `margin-top: 10%` is 16px, from the 160 width, not 12px). It is the
     * same basis MarginApplier's percent lane resolves against under WPT
     * capture, so a preserved percent margin paints the band assumed here.
     */
    private fun declaredMarginPx(
        properties: List<IRProperty>,
        types: List<String>,
        cbInlinePx: Double?,
    ): Double? {
        // Absent on every spelling → the initial value, a real 0.
        val data = properties.lastOrNull { it.type in types }?.data ?: return 0.0
        return when (val len = extractLength(data)) {
            // `{"px":N}` and the `{"type":"length","px":N}` wrapper.
            is LengthValue.Exact -> len.px
            // A percent — bare-number or `{"type":"percentage"}` wire.
            // Any other relative unit (em/vw/…) has no basis here and is
            // reported as unreadable rather than approximated.
            is LengthValue.Relative ->
                if (len.unit == LengthUnit.PERCENT && cbInlinePx != null) {
                    len.value * cbInlinePx / 100.0
                } else null
            // auto/calc/intrinsic/unknown — M2b.
            else -> null
        }
    }

    /**
     * Compose-side wire wrapper: rewrite an OUT-OF-FLOW component's
     * property list so the §10.3.7/§10.6.4 auto margins are RESOLVED — a
     * solved START margin is folded into the start inset, and every side
     * that WAS `auto` is replaced by an explicit zero. Sides with a
     * DECLARED margin keep their wire entry untouched: they already paint
     * their own band, so folding or deleting them would move the box
     * (skeptic bugs 1 & 2 — see the file header).
     *
     * Identity (the same list instance) whenever nothing moves and nothing
     * solves to a non-zero band, which is the frozen-baseline
     * byte-stability rule and covers every corpus box except
     * absolute-tables-016 (see the blast-radius note in the file header).
     *
     * Must run AFTER [AbsposInsetStretch.inject] so the used size it
     * reads is the post-stretch one.
     */
    fun inject(properties: List<IRProperty>, cb: ContainingBlock): List<IRProperty> {
        // One axis' worth of wire edits (null fields = leave that wire
        // alone). Only sides this rule actually SOLVED are listed here —
        // a declared margin keeps its own entry, which is skeptic bug 2's
        // fix and the reason the fold is per-side rather than per-axis.
        data class Plan(
            /** New start inset px, or null when the start margin was
             *  DECLARED and the inset therefore does not move. */
            val newInsetPx: Double?,
            /** Every spelling of the start inset, dropped when it moves. */
            val insetTypes: List<String>,
            val physicalInset: String,
            /** Every spelling of the sides that were genuinely `auto`. */
            val autoMarginTypes: List<String>,
            /** The physical names to re-emit as explicit zeros. */
            val physicalAutoMargins: List<String>,
        )

        // The containing block's INLINE size — the §8.3 percent basis for
        // declared margins on BOTH axes (probe case K).
        val cbInline = cb.widthPx?.toDouble()

        fun plan(
            startTypes: List<String>, endTypes: List<String>,
            sizeTypes: List<String>,
            startMarginTypes: List<String>, endMarginTypes: List<String>,
            cbExtent: Double?,
            physicalInset: String, physicalStartMargin: String, physicalEndMargin: String,
        ): Plan? {
            val startInset = strictInsetPx(properties, startTypes)
            val startAuto = isAutoMargin(properties, startMarginTypes)
            val endAuto = isAutoMargin(properties, endMarginTypes)
            val axis = split(
                cb = cbExtent,
                startInset = startInset,
                endInset = strictInsetPx(properties, endTypes),
                sizePx = usedSizePx(properties, sizeTypes),
                startAuto = startAuto,
                endAuto = endAuto,
                // Declared only — an auto side is the unknown, and reading
                // its (absent) length would just be noise.
                startMarginPx =
                    if (startAuto) 0.0 else declaredMarginPx(properties, startMarginTypes, cbInline),
                endMarginPx =
                    if (endAuto) 0.0 else declaredMarginPx(properties, endMarginTypes, cbInline),
            ) ?: return null
            // How far the START edge actually moves. A DECLARED start
            // margin already paints as its own outer band (MarginApplier's
            // absolutePadding), so folding it into the inset too would
            // double-count it — only a solved AUTO margin moves the inset.
            val insetDelta = if (startAuto) axis.startPx else 0.0
            // The solved value on an auto END side. Non-zero means the wire
            // still has to lose its `auto` keyword even though the inset
            // does not move (probe cases D/F).
            val solvedEnd = if (endAuto) axis.endPx else 0.0
            // M7 (identity guard) — nothing to move and nothing solved to a
            // non-zero band: no wire change. Leaving the `auto` keywords in
            // place keeps the css-align abspos stretch captures
            // byte-identical.
            if (insetDelta == 0.0 && solvedEnd == 0.0) return null
            return Plan(
                newInsetPx = if (insetDelta != 0.0) startInset!! + insetDelta else null,
                insetTypes = startTypes,
                physicalInset = physicalInset,
                autoMarginTypes =
                    (if (startAuto) startMarginTypes else emptyList()) +
                        (if (endAuto) endMarginTypes else emptyList()),
                physicalAutoMargins = listOfNotNull(
                    physicalStartMargin.takeIf { startAuto },
                    physicalEndMargin.takeIf { endAuto },
                ),
            )
        }

        // The inline axis' own extent IS the §8.3 percent basis, hence the
        // one variable for both roles here.
        val planX = plan(
            START_INSET_X, END_INSET_X, listOf("Width", "InlineSize"),
            START_MARGIN_X, END_MARGIN_X, cbInline,
            "Left", "MarginLeft", "MarginRight",
        )
        val planY = plan(
            START_INSET_Y, END_INSET_Y, listOf("Height", "BlockSize"),
            START_MARGIN_Y, END_MARGIN_Y, cb.heightPx?.toDouble(),
            "Top", "MarginTop", "MarginBottom",
        )
        // Identity — the overwhelming majority of boxes.
        if (planX == null && planY == null) return properties

        val plans = listOfNotNull(planX, planY)
        // Drop every wire this rewrite supersedes: the axis' start inset
        // when it moves (all spellings — the replacement is the PHYSICAL
        // one, which is what the LTR-normalized PositionConfig resolves
        // against) and the axis' AUTO margins only. Filtering rather than
        // appending is what makes the result independent of each consumer's
        // fold order — PositionExtractor is last-writer-wins but
        // ComponentRenderer.autoMarginAlignment uses `any`, so an appended
        // `MarginLeft: 0` alone would NOT stop its §10.3.3 centring.
        val superseded = plans.flatMap {
            (if (it.newInsetPx != null) it.insetTypes else emptyList()) + it.autoMarginTypes
        }.toSet()
        return properties.filterNot { it.type in superseded } + plans.flatMap { p ->
            listOfNotNull(
                // Used position = cb start edge + start inset + SOLVED
                // start margin (CSS 2.1 §10.3.7's equation). Folding it
                // into the inset lets PositionApplier's existing
                // absoluteOffset paint it with no new mounting slot.
                p.newInsetPx?.let { px ->
                    IRProperty(p.physicalInset, buildJsonObject { put("px", px) })
                },
            ) + p.physicalAutoMargins.map { side ->
                // A side that WAS `auto` is no longer: emit an explicit
                // ZERO so no downstream alignment path re-applies the same
                // rule (ComponentRenderer.autoMarginAlignment on Compose,
                // MarginApplier's H/VAutoFrameModifier on iOS).
                //
                // Deliberately ZERO rather than the solved value: both
                // natives render a margin as a real OUTER PADDING BAND
                // (Compose MarginApplier / SwiftUI `.padding`), so writing
                // the solved 30px would grow the box's reported footprint
                // by 30px on each side on top of the inset fold that
                // already carries the whole §10.3.7 displacement — the
                // used position of an out-of-flow box depends only on the
                // START margin, which is now inside `physicalInset`. The
                // solved values remain observable in [split]'s return.
                //
                // A DECLARED margin is NOT in this list: it keeps its own
                // wire entry and its own band, which is what makes the
                // Chromium `40px 20px` / `16px 44px` pairs reproducible.
                IRProperty(side, buildJsonObject { put("px", 0.0) })
            }
        }
    }
}
