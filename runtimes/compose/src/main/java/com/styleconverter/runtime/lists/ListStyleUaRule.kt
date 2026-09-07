package com.styleconverter.runtime.lists

import com.styleconverter.runtime.core.ir.IRProperty
import com.styleconverter.runtime.core.types.ValueExtractors
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Wave 25 (lane LF follow-up) — the UA `list-style-type` rule as a
 * CASCADE step, applied where the inheritance merge happens.
 *
 * ## The defect this repairs
 * Wave 24 resolved a `<li>`'s marker by folding, in order: the container's
 * UA default → the container's INHERITANCE-MERGED declarations → the
 * item's own. Slot 2 is merged, so a `list-style-type` declared on an
 * ANCESTOR (`body`, an outer `div`) arrived there and beat the container's
 * UA default. That is the wrong cascade.
 *
 * ## The correct semantics (verified against the specs, not guessed)
 * css-cascade-4 §4.3 — inheritance applies ONLY when the cascade produced
 * no value for the element: "if the cascade results in a value, use it;
 * otherwise, if the property is inherited … use the computed value of the
 * parent". An inherited value therefore LOSES to any declaration on the
 * element itself, whatever its origin — and the UA stylesheet's
 * `ul, menu, dir { list-style-type: disc }` / `ol { list-style-type:
 * decimal }` (HTML §15.3.7, verbatim in Chromium's html.css) IS such a
 * declaration, matching the container ELEMENT. So for a `<ul>` with no
 * author `list-style-type`, the cascade yields `disc` and inheritance is
 * never consulted; the `<li>` — for which the UA sheet declares no
 * list-style-type — then inherits `disc` from it. An ancestor's
 * `list-style-type: square` never reaches the marker.
 *
 * An author declaration ON the container still wins (author beats UA,
 * css-cascade-4 §6.1) — which is why the fix cannot be a plain fold
 * reorder: the live `content-property/marker-text-matches-armenian`
 * fixture puts `list-style-type: armenian` on the `<ol>` itself and must
 * keep painting Armenian rather than the UA `decimal`.
 *
 * ## Why here and not in the marker resolver
 * The resolver receives ONE already-merged parent list, in which an
 * ancestor-inherited entry and the container's own are indistinguishable
 * (see ComponentRenderer.mergeInherited: `inherited-not-declared + own`,
 * at most one entry per type). The merge site is the only place that
 * still holds both lists — so the UA rule is applied there, and every
 * downstream consumer (the marker resolver, StyleApplier's `lists`
 * config, the channel republished to descendants) then reads a
 * cascade-correct value. Twin of iOS `ListStyleUaRule.swift`.
 *
 * ## KNOWN GAP (documented, not a silent fallthrough)
 * Chromium's html.css additionally declares `ul ul, ol ul { list-style-
 * type: circle }` and `ol ol ul, ol ul ul, ul ol ul, ul ul ul {
 * list-style-type: square }`. Those depend on NESTING DEPTH, which
 * neither this helper nor [ListStyleExtractor.uaMarkerDefault] is given —
 * a nested `<ul>` therefore still resolves to `disc` where a browser
 * shows `circle`. No fixture in the pinned corpus nests lists
 * (tools/titan/runs/wave24-final/sections/css-lists/per-test-ir/ — every
 * `ul`/`ol` there is a capture root), so this gap is unexercised today.
 */
object ListStyleUaRule {

    /**
     * Property types whose presence on the element counts as "the author
     * declared list-style-type here". `ListStyle` is the unexpanded
     * shorthand (see [ListStyleShorthand]) — it carries a type component,
     * so it blocks the UA substitution exactly like the longhand.
     */
    private val TYPE_DECLARING = setOf("ListStyleType", "ListStyle")

    /**
     * The PHYSICAL padding longhand the UA `padding-inline-start`
     * declaration maps to under [rtl] — css-logical-1 §2.1: the
     * inline-start side is the left in a `direction: ltr` box and the
     * RIGHT in an rtl one. Internal so the injection below and its JVM
     * pin name the same mapping, and so the iOS twin can be diffed
     * against one function rather than two literals.
     */
    internal fun startPaddingType(rtl: Boolean): String =
        if (rtl) "PaddingRight" else "PaddingLeft"

    /**
     * Property types whose presence on the element counts as "the author
     * declared the container's inline-start padding here", blocking the
     * UA `padding-inline-start: 40px` substitution below.
     *
     * Direction-parameterised for the same css-logical-1 §2.1 reason as
     * [startPaddingType]: only the physical longhand the CURRENT direction
     * maps to can block the UA declaration. An rtl `<ul>` that declares
     * `padding-left` has said nothing about its inline START, so the UA
     * 40px still applies — on the right — exactly as it does in a browser,
     * where both declarations survive the cascade. The wire carries
     * whichever spelling the author wrote (the live
     * `css3-counter-styles-007` `<ol>` carries `PaddingLeft` for a
     * `padding-left: 8em`, under the default ltr), and the LOGICAL
     * `PaddingInlineStart` blocks in both directions because it names the
     * same side the UA rule does.
     *
     * `Padding` is DEFENSIVE ONLY — honestly, not load-bearing: the
     * converter's `PaddingExpander` always expands the `padding` shorthand
     * into the four longhands and the 558-property catalogue contains no
     * `PaddingProperty`, so no `Padding` entry can reach this on today's
     * wire. It is kept (and pinned by a test) so that a foreign producer
     * emitting the unexpanded shorthand — a shape
     * schema/spec/05-versioning.md tolerates rather than rejects — cannot
     * silently get its container double-indented.
     */
    private fun inlineStartPaddingDeclaring(rtl: Boolean): Set<String> =
        setOf(startPaddingType(rtl), "PaddingInlineStart", "Padding")

    /**
     * Is the element's RESOLVED `direction` rtl?
     *
     * Read from the INHERITANCE-MERGED list because that is where the
     * resolved value lives: `direction` is "Inherited: yes"
     * (css-writing-modes-4 §2.1) and sits in
     * `ComponentRenderer.INHERITED_PROPERTY_TYPES`, so the merged list
     * carries the container's OWN declaration when it has one (merge =
     * inherited-not-declared + own) and the ancestor's otherwise — the
     * same value the browser resolves for the box. Last entry wins, the
     * fold convention every extractor in this package uses. Anything that
     * is not the `rtl` keyword — absent, `ltr`, an unresolved `var()` —
     * is ltr, the CSS initial value.
     */
    internal fun isRtl(merged: List<IRProperty>): Boolean =
        merged.lastOrNull { it.type == "Direction" }
            ?.let { ValueExtractors.extractKeyword(it.data)?.uppercase() == "RTL" }
            ?: false

    /**
     * The UA stylesheet's list indentation, in CSS pixels. HTML §15.3.7
     * (verbatim in Chromium's html.css):
     * `ul, menu, dir, ol { padding-inline-start: 40px }`.
     */
    const val UA_PADDING_INLINE_START_PX: Double = 40.0

    /**
     * Replace an ancestor-inherited `ListStyleType` on a LIST CONTAINER
     * with that container's UA-declared value, and inject the container's
     * UA `padding-inline-start` when the author declared none.
     *
     * @param sourceTag the element's `meta.sourceTag`; only `ul`/`ol`/
     *   `menu`/`dir` carry the UA `list-style-type` + `padding-inline-start`
     *   declarations.
     * @param own the element's OWN declarations (pre-merge, after the
     *   bucket/media/scheme fold) — a type here means the author declared
     *   it and the UA rule loses.
     * @param merged the inheritance-merged list to correct. Also the
     *   source of the element's RESOLVED `direction`, which decides which
     *   physical side the injected inline-start padding lands on (see
     *   [isRtl]).
     * @return [merged] itself (same instance — the frozen-baseline
     *   byte-stability rule) whenever neither rule fires.
     */
    fun apply(
        sourceTag: String?,
        own: List<IRProperty>,
        merged: List<IRProperty>
    ): List<IRProperty> {
        // Only a list container has a UA list-style-type declaration.
        val uaKeyword = when (ListStyleExtractor.uaMarkerDefault(sourceTag)) {
            ListStyleType.DISC -> "disc"
            ListStyleType.DECIMAL -> "decimal"
            // uaMarkerDefault returns ONLY those two, or null for a
            // non-container. Anything else means its table grew without
            // this mapping: leave the list untouched rather than invent a
            // keyword — the marker resolver still applies the enum default
            // it derives from parentTag, so nothing is silently dropped.
            else -> return merged
        }
        // Wave 30 (lane 3, fix B5) — the SECOND UA declaration on the same
        // four elements, applied at the same cascade step and for the same
        // reason: `ul, menu, dir, ol { padding-inline-start: 40px }`
        // (HTML §15.3.7). Nothing in the runtime supplied it, so every list
        // container laid its items out at the canvas origin while the
        // browser indents them 40px. MEASURED on the live wave29-final
        // css-lists section — ink columns of
        // wpt__css-lists__change-list-style-type-001, ten `<ul>`s with no
        // author padding: web starts every row at x=56, both natives at
        // x=17, i.e. exactly the missing 40px (canvas origin 16). That one
        // offset is on all ten rows of a test scoring 0.75 Android↔web.
        // Author beats UA (css-cascade-4 §6.1), so any own inline-start
        // padding declaration blocks it — `css3-counter-styles-007`'s
        // `<ol>` declares `padding-left: 8em` and must keep it.
        //
        // Wave 30 FIX ROUND (lane N, fix N1) — the declaration is
        // padding-inline-START, and the first cut injected the PHYSICAL
        // `PaddingLeft` unconditionally. Under `direction: rtl` the
        // inline-start side is the RIGHT one (css-logical-1 §2.1), so that
        // indented an rtl list on the wrong side: 40px of padding on the
        // wrong edge against 40px missing on the right, an 80px relative
        // error. Both the injected side and the set that blocks it now
        // follow the container's resolved direction ([isRtl]). Not
        // hypothetical: fixtures/wpt/css-lists/list-marker-symbol-bidi.json
        // is five `direction: rtl` `<ul>`s.
        val rtl = isRtl(merged)
        val declaring = inlineStartPaddingDeclaring(rtl)
        val paddingEntry =
            if (own.none { it.type in declaring } &&
                merged.none { it.type in declaring })
            // Emitted in the wire's own plain-px length shape ({"px": n}),
            // which is what ValueExtractors.extractDp reads first — the UA
            // value is an absolute length, so it needs none of the
            // `original` wrapper's relative-unit machinery.
                IRProperty(startPaddingType(rtl), buildJsonObject {
                    put("px", UA_PADDING_INLINE_START_PX)
                })
            else null
        // An own declaration (author origin) beats the UA rule — nothing
        // to do for the TYPE half. This is the marker-text-matches-* shape.
        if (own.any { it.type in TYPE_DECLARING }) {
            return if (paddingEntry != null) merged + paddingEntry else merged
        }
        // No inherited entry to displace ⇒ the resolver's UA base already
        // stands. Identity return keeps every existing capture bit-stable.
        if (merged.none { it.type in TYPE_DECLARING }) {
            return if (paddingEntry != null) merged + paddingEntry else merged
        }
        val uaEntry = IRProperty("ListStyleType", JsonPrimitive(uaKeyword))
        // An inherited `ListStyle` SHORTHAND carries three components at
        // once, and only ONE of them (the type) is displaced by the UA
        // rule: `ul`/`ol` have no UA list-style-position/-image
        // declaration, so those two keep inheriting (probe: Chromium gives
        // `list-style-position: inside` on the `<li>` of
        // `<div style="list-style-position:inside"><ul><li>`). Replacing
        // the whole entry would silently drop them — so instead KEEP the
        // shorthand and append the UA type after it; the resolver's fold
        // is last-wins, so the UA type overrides the shorthand's type
        // component while its position/image survive.
        if (merged.any { it.type == "ListStyle" }) {
            return merged.filter { it.type != "ListStyleType" } + uaEntry +
                listOfNotNull(paddingEntry)
        }
        // Longhand-only path — substitute IN PLACE so the list order
        // (which several extractors read as last-wins) is otherwise
        // untouched: the first type-declaring entry becomes the UA value,
        // any further one is dropped as now-shadowed.
        var substituted = false
        return merged.mapNotNull { property ->
            when {
                property.type !in TYPE_DECLARING -> property
                !substituted -> { substituted = true; uaEntry }
                else -> null
            }
        } + listOfNotNull(paddingEntry)
    }
}
