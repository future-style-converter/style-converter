package com.styleconverter.runtime.global

import com.styleconverter.runtime.PropertyTracker
import com.styleconverter.runtime.core.ir.IRProperty
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * Wave 52 (lane L11, brief `colour-not-reaching-text` F1) — the ORDER-AWARE
 * `all` reset. Twin of iOS `GlobalExtractor.applyingAllReset(own:inherited:)`
 * and web `applyAllReset` (`engine/global/_dispatch.ts`): one rule, three
 * byte-parallel implementations.
 *
 * ## The defect this repairs
 * Until wave 52 ComponentRenderer dropped EVERY declaration — own and
 * inherited — whenever an `All` entry appeared anywhere in the merged list
 * (`unresolvedProperties = emptyList()`). css-cascade-4 §6.4 (order of
 * appearance) says otherwise: `all: initial; color: green` in one block
 * computes `color: green`, because the later longhand wins over the earlier
 * shorthand. The extractor writes the bag in cascade order and the converter
 * preserves it, so the IR list order IS that order. Measured on
 * `css-cascade/all-prop-initial-color` (wave51-fix): the span carries
 * `[All INITIAL, Color green]`, every runtime painted it BLACK, the ref GREEN.
 *
 * ## The rule (css-cascade-4 §3.1, §6.4, §7.3)
 * 1. Find the LAST `All` entry carrying a keyword at index i — a later `all`
 *    overrides an earlier one like any other declaration (§6.4).
 * 2. own[0, i): DROPPED, except `Direction` / `UnicodeBidi` — §3.1: `all`
 *    "resets all CSS properties except direction and unicode-bidi".
 * 3. own(i, end]: KEPT verbatim — they follow the shorthand in source order.
 * 4. The inherited channel: `INITIAL` drops it (§7.3.1 — every property takes
 *    its initial value, inherited ones included) except an inherited
 *    `Direction` (exempt, so it still inherits); `INHERIT` keeps it (§7.3.2);
 *    `UNSET` keeps it (§7.3.4 — unset = inherit for inherited properties, and
 *    the channel carries ONLY inherited types); `REVERT` / `REVERT_LAYER` keep
 *    it (§7.3.3 / §7.3.5 — the roll-back lands on the UA origin, where
 *    inherited properties inherit).
 * 5. Every `All` entry leaves the list — it has no applier (GlobalRegistration
 *    is a registry claim only), and it must not reach descendants.
 *
 * ## KNOWN GAP (logged, not silent)
 * `all: inherit` also makes NON-inherited properties (display, background,
 * border, …) take the PARENT's value (css-cascade-4 §7.3.2). The inherited
 * channel only carries inherited types (ComponentRenderer
 * .INHERITED_PROPERTY_TYPES), so here those properties fall to their initial
 * value instead — `css-cascade/all-prop-002` (`display: inherit`) is the
 * corpus carrier. Reported once per keyword via [PropertyTracker].
 *
 * Pure (no Compose, no android.util.Log) so the JVM suite pins it directly:
 * `src/test/…/global/AllResetTest.kt`.
 */
object AllReset {

    /** css-cascade-4 §3.1: the two longhands the `all` shorthand never resets. */
    internal val EXEMPT_TYPES: Set<String> = setOf("Direction", "UnicodeBidi")

    /** Keywords under which the inherited channel survives (§7.3.2–§7.3.5). */
    private val KEEPS_INHERITED: Set<String> = setOf("INHERIT", "UNSET", "REVERT", "REVERT_LAYER")

    /** Every keyword the converter can emit (AllPropertyParser.kt — five values). */
    private val KNOWN_KEYWORDS: Set<String> = KEEPS_INHERITED + "INITIAL"

    /**
     * The reset's two outputs, kept apart because ComponentRenderer merges
     * them AFTER the reset (own wins over inherited in `mergeInherited`).
     */
    data class Result(
        /** The element's own declarations that survive the reset. */
        val own: List<IRProperty>,
        /** The inherited channel that survives the reset. */
        val inherited: List<IRProperty>,
    )

    /**
     * The normalised keyword of an `All` entry, or null when [p] is not an
     * `All` entry or carries no primitive payload. Mirrors the historical
     * Compose test (`(p.data as? JsonPrimitive)?.contentOrNull?.uppercase()`)
     * so exactly the same entries count as "an `all` declaration"; `-` → `_`
     * folds a hand-written `revert-layer` onto the wire enum spelling.
     */
    internal fun keywordOf(p: IRProperty): String? =
        if (p.type != "All") null                                     // not the shorthand
        else (p.data as? JsonPrimitive)?.contentOrNull                // wire: "INITIAL" etc.
            ?.uppercase()?.replace('-', '_')                          // one spelling

    /**
     * Apply the order-aware reset to [own] (the element's own declarations,
     * after bucket folding + light-dark resolution) and [inherited] (the
     * parent's published channel). Returns the SAME list instances when [own]
     * carries no keyword `All` — the whole All-free corpus keeps its
     * identity, so `remember` keys and the frozen captures are untouched.
     */
    fun apply(own: List<IRProperty>, inherited: List<IRProperty>): Result {
        // Step 1 — the governing `all` is the LAST keyword one (§6.4).
        val i = own.indexOfLast { keywordOf(it) != null }
        // No `all` on this element: identity on both lists (fast path).
        if (i < 0) return Result(own, inherited)
        // Non-null by the indexOfLast predicate above.
        val keyword = keywordOf(own[i])!!
        // A keyword the converter cannot emit (hand-authored IR): surface it
        // and fall back to INITIAL — the historic drop-everything behaviour.
        if (keyword !in KNOWN_KEYWORDS) PropertyTracker.markUnhandled("All:$keyword")
        // The documented `all: inherit` gap (see the KDoc) — reported, once
        // per keyword in the tracker's set, never silently approximated.
        if (keyword == "INHERIT") PropertyTracker.markUnhandled("All:INHERIT(non-inherited→initial)")
        // Step 2 — declarations BEFORE the shorthand: only the §3.1 exemptions
        // survive (all-prop-001's `.test` keeps `direction: rtl` and
        // `unicode-bidi: bidi-override`).
        val before = own.subList(0, i).filter { it.type in EXEMPT_TYPES }
        // Step 3 — declarations AFTER the shorthand win over it; step 5 strips
        // any further (payload-less) `All` entry from that tail too.
        val after = own.subList(i + 1, own.size).filter { it.type != "All" }
        // Step 4 — the inherited channel, per keyword (§7.3).
        val keptInherited =
            if (keyword in KEEPS_INHERITED) inherited                  // inherit / unset / revert*
            else inherited.filter { it.type in EXEMPT_TYPES }          // initial: only `direction` flows
        // Source order is preserved inside each half; the exempt survivors
        // precede the tail exactly as they did in the source.
        return Result(before + after, keptInherited)
    }
}
