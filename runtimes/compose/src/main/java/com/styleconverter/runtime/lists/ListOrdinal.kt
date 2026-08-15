package com.styleconverter.runtime.lists

// Wave 43, lane V4 — the HTML ordinal algorithm for list-item markers.
//
// ## The defect this closes
// Both natives numbered every `<ol>` from the raw children-loop index, so
// `<ol start="30">` painted "1. 2. 3." where Chromium paints "30. 31. 32."
// and `<li value="30">` reset nothing at all. MEASURED on the wave42-final
// gate (tools/titan/runs/wave42-final/sections/css-lists/manifest.json):
// wpt/css-lists/counter-list-item.html scored android-ref 0.744 / ios-ref
// 0.761 while web PASSES the same IR (0.976) by forwarding the very same
// `meta.attrs` to a real DOM `<ol start>` (web/src/renderer/WidgetAttrs.ts)
// and letting the browser count. The wire already carries everything the
// natives need — `{"start":"30"}` on the ol, `{"value":"30"}` on the li
// (IRAttrs.start / IRAttrs.value, both verbatim strings by contract) — so
// this file is pure consumption, no wire change.
//
// ## The algorithm (HTML §4.4.5 <ol> / §4.4.8 <li> — "ordinal value",
// https://html.spec.whatwg.org/multipage/grouping-content.html#ordinal-value)
//  - the starting value is `<ol start>` when it parses as an integer;
//  - walking items in tree order, `<li value>` RESETS the running counter;
//  - each item takes the counter, which then steps +1 (or −1 when the list
//    is reversed).
//
// ## Reversed lists — SPEC-FAITHFUL, wiring deferred
// `ordinals()` accepts `reversed` and implements the countdown per
// css-lists-3 §4.4.2 "Instantiating Counters". The chain: `<ol reversed>`
// is a `counter-reset: reversed(list-item)` presentational hint (HTML
// §15.3.7 Lists), a reversed `list-item` counter steps −1 on every list
// item (css-lists-3 §4.6 "The Implicit list-item Counter"), and `<li
// value>` is a `counter-set: list-item <n>` hint (HTML §15.3.7). §4.4.2
// then says a counter instantiated with NO initial value — only reversed
// counters may be — has that value computed at layout time: walk the
// elements that touch the counter in tree order accumulating
// `incrementNegated` (= +1 here, the increment × −1); when one uses
// `counter-set`, ADD ITS VALUE AND STOP; finally add the last non-zero
// incrementNegated.
//
// Worked on counter-list-item's third reversed list (two plain items,
// then value=30): num = 1 + 1 + 30 = 32, plus the trailing +1 ⇒ initial
// 33; each item decrements BEFORE its ::marker reads the counter, so the
// first marker paints 32 and the run is 32,31,30,29,35,34 — which is what
// the Chromium ref shows because that is what the algorithm computes, not
// the other way round. `ordinals()` returns post-decrement marker values
// directly, so its anchor is §4.4.2's num MINUS ONE by construction.
// With no `<li value>` anywhere the same walk degenerates to
// num = itemCount + 1 ⇒ the last item lands on 1; that is the only case
// where the naive "initial = item count" shortcut agrees. The shortcut is
// NOT the CSS-UA rule (it ignores counter-set entirely) — do not
// "simplify" this back to it. The caller passes `false` today because the
// producer never forwards the attribute: tools/titan/extract-fixture.mjs
// pins LIST_ATTR_KEYS = ['start','value'] (no 'reversed'), and the strict
// meta.attrs decoders on both natives would reject the key anyway
// (IRDocumentDecoder.ATTR_KEYS / Swift IRAttrs.from). Closing that is a
// producer + decoder change outside this lane; the countdown is unit-tested
// here so the wiring is one boolean when the wire arrives.
//
// TWIN of the iOS StyleEngine/lists/ListOrdinal.swift — same functions,
// same anchor rule, same tests. Change one, change both.

import com.styleconverter.runtime.core.ir.IRComponent

object ListOrdinal {

    /**
     * Parse one attribute string per HTML §2.3.4.1 "rules for parsing
     * integers": skip ASCII whitespace, take an optional sign, then read
     * leading digits and IGNORE any trailing junk ("30abc" → 30 — the
     * browser behaviour, not an error). Null when no digit is found.
     */
    fun parseHtmlInteger(raw: String?): Int? {
        // Absent attribute → no integer (the caller falls back per spec).
        if (raw == null) return null
        // Position cursor over the string, mirroring the spec's steps.
        var i = 0
        // §2.3.4.1 step 4: skip ASCII whitespace (TAB LF FF CR SPACE).
        while (i < raw.length && raw[i] in " \t\n\r") i++
        // §2.3.4.1 steps 5-7: one optional sign character.
        var sign = 1
        if (i < raw.length && (raw[i] == '-' || raw[i] == '+')) {
            // '-' flips the sign; '+' is consumed and ignored.
            if (raw[i] == '-') sign = -1
            i++
        }
        // §2.3.4.1 step 8: at least one ASCII digit must follow the sign.
        val digitsStart = i
        // Accumulate in Long so a wire value near Int.MAX cannot overflow
        // mid-parse; anything beyond Int range clamps below (the corpus
        // never exercises it, and a clamp is strictly closer to the
        // browser's big-integer handling than a silent wraparound).
        var value = 0L
        // §2.3.4.1 step 9: collect the digit run; everything AFTER it is
        // deliberately ignored ("collect a sequence … return value").
        while (i < raw.length && raw[i] in '0'..'9') {
            value = value * 10 + (raw[i] - '0')
            // Early cap: once past Int range the remaining digits cannot
            // change the clamped answer, so stop growing the accumulator.
            if (value > Int.MAX_VALUE) { value = Int.MAX_VALUE + 1L; break }
            i++
        }
        // No digits ⇒ the attribute is not a valid integer ⇒ null.
        if (i == digitsStart) return null
        // Clamp into Int range (see the accumulator comment above).
        return (sign * value).coerceIn(Int.MIN_VALUE.toLong(), Int.MAX_VALUE.toLong()).toInt()
    }

    /**
     * The HTML ordinal of every child slot, index-aligned with [children].
     *
     * Only `<li>` slots (the renderer's own marker gate:
     * `_tag == "li"`) consume ordinals; any other slot — whitespace runs,
     * stray non-item children — carries the running counter untouched, so
     * an interleaved text node can never shift the numbering the way the
     * old raw loop index did.
     *
     * @param startAttr the container's verbatim `meta.attrs.start` string
     *   (`<ol start>`), or null.
     * @param reversed whether the list counts down (`<ol reversed>` —
     *   currently always false at the call site; see the file header).
     * @param children the SAME list the render loop iterates, so indices
     *   align by construction.
     */
    fun ordinals(
        startAttr: String?,
        reversed: Boolean,
        children: List<IRComponent>
    ): IntArray {
        // The item gate, hoisted once per child — must stay byte-identical
        // to the renderer's `child._tag?.lowercase() == "li"` marker gate
        // or the plan and the paint would disagree about who numbers.
        val isItem = BooleanArray(children.size) { i ->
            children[i]._tag?.lowercase() == "li"
        }
        // Counting direction — HTML's ordinal step 7: reversed decrements.
        val step = if (reversed) -1 else 1
        // The starting value: `<ol start>` wins when it parses (§4.4.5)…
        var counter = parseHtmlInteger(startAttr)
            ?: if (!reversed) 1 else run {
                // …otherwise css-lists-3 §4.4.2 instantiates the reversed
                // counter by walking siblings until the first `counter-set`
                // (`<li value>`) and adding it — so the run flows INTO that
                // item: anchor = value + the +1-per-item walked before it.
                // (§4.4.2 returns one MORE than this; the marker is read
                // after the item's own −1 step, and `anchor` is already
                // that post-step value. See the file header's worked run.)
                var itemsBefore = 0
                // Default anchor: with no `counter-set` anywhere the §4.4.2
                // walk visits every item, so num = itemCount + 1 and the
                // first marker is itemCount ⇒ the LAST item lands on 1.
                var anchor = isItem.count { it }
                // Scan for the first item carrying a valid value attribute.
                for (i in children.indices) {
                    // Non-items neither anchor nor count (they own no slot
                    // in the numbering, same as the main walk below).
                    if (!isItem[i]) continue
                    // A parseable value at item-position `itemsBefore`
                    // pins the initial value; stop at the FIRST one.
                    val v = parseHtmlInteger(children[i].attrs?.value)
                    if (v != null) { anchor = v + itemsBefore; break }
                    // Otherwise this item sits before the anchor: count it.
                    itemsBefore++
                }
                anchor
            }
        // The per-slot ordinal plan the render loop indexes into.
        val out = IntArray(children.size)
        // HTML's ordinal loop (steps 3-8), one pass in tree order.
        for (i in children.indices) {
            // Non-item slots carry the running value but consume nothing —
            // the marker branch never reads them (its `<li>` gate).
            if (!isItem[i]) { out[i] = counter; continue }
            // §4.4.8: a valid `value` attribute RESETS the counter here.
            parseHtmlInteger(children[i].attrs?.value)?.let { counter = it }
            // Step 6: this item's ordinal is the (possibly reset) counter.
            out[i] = counter
            // Step 7: advance for the next item (down when reversed).
            counter += step
        }
        return out
    }

    /**
     * Bridge one render-loop child index to the 0-based index
     * [ListStyleApplier.getMarker] expects (its numeric systems render
     * `index + 1`, its alphabetic/cyclic systems index from 0 — so the
     * bridge is `ordinal − 1` for every system at once). Null / short
     * plans fall back to the raw index — the exact pre-wave-43 behaviour,
     * so a non-list caller can never change output.
     */
    fun markerIndex(ordinals: IntArray?, index: Int): Int =
        (ordinals?.getOrNull(index) ?: (index + 1)) - 1
}
