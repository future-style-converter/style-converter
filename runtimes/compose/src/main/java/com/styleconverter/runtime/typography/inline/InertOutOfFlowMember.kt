// typography/inline — wave 53 (lane L2, F2): the PAINT-INERT OUT-OF-FLOW
// member of an inline-run fold. Measured victims (wave52-ship, Android):
// css-text/hyphens/hyphens-out-of-flow-001 P 0.9685 and -002 P 0.982, both
// DEGENERATE — boxes 3-6 read `h`/`ighway`, `high`/`way` or `highwa`/`y`
// where the ref paints `high‐`/`way` in every box.
package com.styleconverter.runtime.typography.inline

// The typed IR node the predicate classifies (pure data, no Compose).
import com.styleconverter.runtime.core.ir.IRComponent
// SHOUTY keyword reads (the Position wire is a bare string, "ABSOLUTE").
import com.styleconverter.runtime.core.types.ValueExtractors
// The Color wire is `{"srgb":{r,g,b,a?},"original":…}` — read the alpha
// with SAFE casts only (a malformed wire refuses, it never throws).
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.floatOrNull

/**
 * InertOutOfFlowMember — may an `{child}` member of a `meta.runs` host be
 * DROPPED from the folded paragraph because it is out of flow AND paints
 * nothing?
 *
 * ## Why the fold needs this
 * [InlineRunFold] refuses every member property outside its rings, and an
 * abspos `<span>` carries `Position` — so the fold bailed
 * (`member-prop:Position`, InlineSpanRing.admit) and the host fell back to
 * the wave-32 STACKED rendering: one paragraph per text run, the member
 * mounted between them. That splits ONE word into two blocks at the span
 * (`h` / `igh­way`), which no line breaker can repair — the run never
 * reaches a single layout.
 *
 * ## Why dropping is faithful (and when it is not)
 * - CSS 2.1 §9.3.1 / §10.3.7: an absolutely (or fixed) positioned box is
 *   taken OUT of the normal flow; it contributes nothing to the line boxes
 *   of the inline formatting context its source position sits in.
 * - css-text-3 §5.1 (line-breaking details): out-of-flow elements introduce
 *   neither a forced break nor a soft wrap opportunity in the flow — the
 *   word on both sides is one word, which is exactly what the WPT asserts
 *   ("the presence of an out of flow element has no effect" on manual
 *   hyphenation).
 * - The member's own box is NOT modelled once dropped (its static position
 *   is unmodelled, it is not mounted). That is only honest when the box
 *   paints nothing, so the predicate demands zero paint by construction:
 *   transparent ink (css-color-4 §4.2: alpha 0 paints no glyph), no box
 *   ink (no background, border, outline or decoration may ride), and no
 *   structure of its own. Anything else returns false and the fold keeps
 *   its old bail verbatim.
 * - Border colours are REFUSED too (wave-53 L2 skeptic D4), though a bare
 *   `border-*-color` without a style paints nothing (css-backgrounds-3
 *   §3.2): the converter emits `border: inherit` as exactly four
 *   `Border*Color {"original":"inherit"}` longhands, which InlineAtomRing
 *   decodes as the HOST's whole painted border. Dropping such a member
 *   would erase real ink, and no carrier needs any border-colour
 *   tolerance (census: none of the 21 abspos members carries one without
 *   also carrying a refused property), so every border colour refuses.
 * - `Hyphens` on the member is tolerated and IGNORED: it governs only the
 *   member's own (unpainted) text, never the host paragraph's breaks, so
 *   it is deliberately not adopted (InlineRunFold's adoption walk is for
 *   IN-FLOW members only).
 *
 * The drop is counted ([InlineRunFold.Outcome.Folded.droppedOutOfFlowMembers])
 * and named in the seam's fold breadcrumb — a removal from the mounted tree
 * is never silent (repo rule; PLAN wave 53 §9 D2).
 *
 * Census (tools/titan/results/wave53-plan/spaceless-soft-hyphen.census.py,
 * wave52-ship): 21 abspos/fixed members in runs hosts; exactly 12 pass —
 * all in hyphens-out-of-flow-001/-002 — and the other 9 keep bailing.
 *
 * Pure Kotlin (no Compose, no Android) so the JVM suite pins it deviceless.
 */
object InertOutOfFlowMember {

    /** The out-of-flow `position` keywords (CSS 2.1 §9.3.1: absolute and
     *  fixed both take the box out of flow; relative/sticky stay in flow
     *  and are refused). */
    private val OUT_OF_FLOW_POSITIONS = setOf("ABSOLUTE", "FIXED")

    /**
     * True when [member] is out of flow AND paints nothing, so the fold may
     * drop it from the merged paragraph. The caller has already checked the
     * member's tag against InlineRunFold's no-UA-styling TEXT tag ring (one
     * source for that set) and that the member carries glyphs.
     *
     * Every property must be one of: `Position` ABSOLUTE|FIXED (REQUIRED),
     * `Color` with alpha 0 (REQUIRED — glyph-bearing text paints in the
     * inherited ink otherwise), `Hyphens`. A border colour refuses (banner).
     */
    fun admits(member: IRComponent): Boolean {
        // A member with its own structure or decoration wire is not a
        // single inert box — its nested nodes or lines could paint.
        if (!member.children.isNullOrEmpty() || !member.runs.isNullOrEmpty() ||
            !member.decorations.isNullOrEmpty()
        ) return false
        // Both REQUIRED facts, proven by a declaration each.
        var outOfFlow = false
        var transparentInk = false
        for (prop in member.properties) when (prop.type) {
            // Out of flow only for absolute/fixed; anything else refuses.
            "Position" ->
                if (ValueExtractors.extractKeyword(prop.data)?.uppercase() in OUT_OF_FLOW_POSITIONS) {
                    outOfFlow = true
                } else return false
            // Only fully transparent ink is paint-inert for real glyphs.
            "Color" -> if (alphaIsZero(prop.data)) transparentInk = true else return false
            // The member's own paragraph policy — tolerated, not adopted.
            "Hyphens" -> {}
            // Any other declaration (border colours included: the
            // `border: inherit` dialect paints, banner) could paint or
            // move ink: refuse.
            else -> return false
        }
        // Inert only when BOTH were declared on the member itself.
        return outOfFlow && transparentInk
    }

    /** The wire's sRGB alpha is exactly 0 (absent alpha = opaque, so an
     *  unreadable or alpha-less colour answers false — refuse, never guess). */
    private fun alphaIsZero(data: JsonElement): Boolean {
        // `{"srgb":{…,"a":0},"original":"transparent"}` — the converter's
        // normalized colour (schema/spec/02-values.md).
        val srgb = (data as? JsonObject)?.get("srgb") as? JsonObject ?: return false
        // A missing alpha channel means opaque (css-color-4 §4.1); a
        // non-primitive or non-numeric one is malformed — both refuse
        // (safe cast, skeptic D7: `.jsonPrimitive` would THROW on an object).
        val a = (srgb["a"] as? JsonPrimitive)?.floatOrNull ?: return false
        return a <= 0f
    }
}
