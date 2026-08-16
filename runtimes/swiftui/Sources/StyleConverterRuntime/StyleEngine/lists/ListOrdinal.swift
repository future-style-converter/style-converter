//
//  ListOrdinal.swift
//  StyleEngine/lists — wave 43, lane V4.
//
//  The HTML ordinal algorithm for list-item markers.
//
//  ## The defect this closes
//  Both natives numbered every `<ol>` from the raw children-loop index, so
//  `<ol start="30">` painted "1. 2. 3." where Chromium paints "30. 31. 32."
//  and `<li value="30">` reset nothing. MEASURED on the wave42-final gate:
//  wpt/css-lists/counter-list-item.html scored ios-ref 0.761 (android-ref
//  0.744) while web PASSES the same IR (0.976) by forwarding the very same
//  `meta.attrs` onto a real DOM `<ol start>` and letting the browser count.
//  The wire already carries everything — `{"start":"30"}` on the ol,
//  `{"value":"30"}` on the li (IRAttrs.start / IRAttrs.value, verbatim
//  strings by contract) — so this file is pure consumption, no wire change.
//
//  ## The algorithm (HTML §4.4.5 <ol> / §4.4.8 <li> — "ordinal value")
//   - the starting value is `<ol start>` when it parses as an integer;
//   - walking items in tree order, `<li value>` RESETS the running counter;
//   - each item takes the counter, then it steps +1 (−1 when reversed).
//
//  ## Reversed lists — SPEC-FAITHFUL, wiring deferred
//  `ordinals()` implements the countdown per css-lists-3 §4.4.2
//  "Instantiating Counters". The chain: `<ol reversed>` is a
//  `counter-reset: reversed(list-item)` presentational hint (HTML §15.3.7
//  Lists), a reversed `list-item` counter steps −1 on every list item
//  (css-lists-3 §4.6 "The Implicit list-item Counter"), and `<li value>`
//  is a `counter-set: list-item <n>` hint (HTML §15.3.7). §4.4.2 then says
//  a counter instantiated with NO initial value — only reversed counters
//  may be — has that value computed at layout time: walk the elements that
//  touch the counter in tree order accumulating `incrementNegated` (= +1
//  here, the increment × −1); when one uses `counter-set`, ADD ITS VALUE
//  AND STOP; finally add the last non-zero incrementNegated.
//
//  Worked on counter-list-item's third reversed list (two plain items,
//  then value=30): num = 1 + 1 + 30 = 32, plus the trailing +1 ⇒ initial
//  33; each item decrements BEFORE its ::marker reads the counter, so the
//  first marker paints 32 and the run is 32,31,30,29,35,34 — which is what
//  the Chromium ref shows because that is what the algorithm computes, not
//  the other way round. `ordinals()` returns post-decrement marker values
//  directly, so its anchor is §4.4.2's num MINUS ONE by construction. With
//  no `<li value>` anywhere the same walk degenerates to num = itemCount +
//  1 ⇒ the last item lands on 1; that is the only case where the naive
//  "initial = item count" shortcut agrees. The shortcut is NOT the CSS-UA
//  rule (it ignores counter-set entirely) — do not "simplify" this back to
//  it. Wave 44 (lane U5) delivered the wire this header used to defer: the
//  producer forwards `<ol reversed>` as a presence-`true` boolean
//  (extract-fixture.mjs LIST_BOOLEAN_ATTR_KEYS, HTML §4.4.5 boolean
//  attribute), both strict decoders admit it (Swift IRWireV2Reader attrKeys
//  / Kotlin IRDocumentDecoder.ATTR_KEYS), and the ComponentRenderer ordinal
//  plan passes `attrs?.reversed == true` here.
//
//  TWIN of Compose lists/ListOrdinal.kt — same functions, same anchor
//  rule, same tests. Change one, change both.
//

import Foundation

enum ListOrdinal {

    /// Parse one attribute string per HTML §2.3.4.1 "rules for parsing
    /// integers": skip ASCII whitespace, take an optional sign, then read
    /// leading digits and IGNORE any trailing junk ("30abc" → 30 — the
    /// browser behaviour, not an error). Nil when no digit is found.
    static func parseHtmlInteger(_ raw: String?) -> Int? {
        // Absent attribute → no integer (the caller falls back per spec).
        guard let raw = raw else { return nil }
        // Walk the scalar view, mirroring the spec's cursor steps.
        let scalars = Array(raw.unicodeScalars)
        var i = 0
        // §2.3.4.1 step 4: skip ASCII whitespace (TAB LF FF CR SPACE).
        while i < scalars.count, " \t\n\r".unicodeScalars.contains(scalars[i]) { i += 1 }
        // §2.3.4.1 steps 5-7: one optional sign character.
        var sign = 1
        if i < scalars.count, scalars[i] == "-" || scalars[i] == "+" {
            // '-' flips the sign; '+' is consumed and ignored.
            if scalars[i] == "-" { sign = -1 }
            i += 1
        }
        // §2.3.4.1 step 8: at least one ASCII digit must follow the sign.
        let digitsStart = i
        // Accumulate in Int64 so a wire value near Int32.max cannot
        // overflow mid-parse; out-of-range clamps below (closer to the
        // browser's big-integer handling than a trap or wraparound).
        var value: Int64 = 0
        // §2.3.4.1 step 9: collect the digit run; everything AFTER it is
        // deliberately ignored ("collect a sequence … return value").
        while i < scalars.count, scalars[i].value >= 48, scalars[i].value <= 57 {
            value = value * 10 + Int64(scalars[i].value - 48)
            // Early cap: past Int32 range further digits cannot change
            // the clamped answer, so stop growing the accumulator.
            if value > Int64(Int32.max) { value = Int64(Int32.max) + 1; break }
            i += 1
        }
        // No digits ⇒ the attribute is not a valid integer ⇒ nil.
        guard i > digitsStart else { return nil }
        // Clamp into Int32 range (see the accumulator comment above) —
        // the same window the Compose twin's Int clamp lands in.
        return Int(max(Int64(Int32.min), min(Int64(Int32.max), Int64(sign) * value)))
    }

    /// The HTML ordinal of every child slot, index-aligned with `children`.
    ///
    /// Only `<li>` slots (the renderer's own marker gate:
    /// `meta.sourceTag == "li"`) consume ordinals; any other slot carries
    /// the running counter untouched, so an interleaved non-item child can
    /// never shift the numbering the way the old raw loop index did.
    ///
    /// - Parameters:
    ///   - startAttr: the container's verbatim `meta.attrs.start` string.
    ///   - reversed: whether the list counts down (`<ol reversed>` —
    ///     always false at today's call site; see the file header).
    ///   - children: the SAME array the render loop iterates, so indices
    ///     align by construction.
    static func ordinals(startAttr: String?,
                         reversed: Bool,
                         children: [IRComponent]) -> [Int] {
        // The item gate, hoisted once per child — must stay identical to
        // the renderer's `meta.sourceTag == "li"` marker gate or the plan
        // and the paint would disagree about who numbers.
        let isItem = children.map { ($0.meta?.sourceTag ?? "").lowercased() == "li" }
        // Counting direction — HTML's ordinal step 7: reversed decrements.
        let step = reversed ? -1 : 1
        // The starting value: `<ol start>` wins when it parses (§4.4.5)…
        var counter: Int
        if let s = parseHtmlInteger(startAttr) {
            counter = s
        } else if !reversed {
            // …a forward list without one simply starts at 1.
            counter = 1
        } else {
            // …and css-lists-3 §4.4.2 instantiates the reversed counter by
            // walking siblings until the first `counter-set` (`<li value>`)
            // and adding it — so the run flows INTO that item: anchor =
            // value + the +1-per-item walked before it. (§4.4.2 returns one
            // MORE than this; the marker is read after the item's own −1
            // step, and `anchor` is already that post-step value. See the
            // file header's worked run.)
            var itemsBefore = 0
            // Default anchor: with no `counter-set` anywhere the §4.4.2
            // walk visits every item, so num = itemCount + 1 and the first
            // marker is itemCount ⇒ the LAST item lands on 1.
            var anchor = isItem.filter { $0 }.count
            // Scan for the first item carrying a valid value attribute.
            for (i, child) in children.enumerated() {
                // Non-items neither anchor nor count (no numbering slot).
                guard isItem[i] else { continue }
                // A parseable value at item-position `itemsBefore` pins
                // the initial value; stop at the FIRST one.
                if let v = parseHtmlInteger(child.meta?.attrs?.value) {
                    anchor = v + itemsBefore
                    break
                }
                // Otherwise this item sits before the anchor: count it.
                itemsBefore += 1
            }
            counter = anchor
        }
        // The per-slot ordinal plan the render loop indexes into.
        var out = [Int](repeating: 0, count: children.count)
        // HTML's ordinal loop (steps 3-8), one pass in tree order.
        for (i, child) in children.enumerated() {
            // Non-item slots carry the running value but consume nothing —
            // the marker branch never reads them (its `<li>` gate).
            guard isItem[i] else { out[i] = counter; continue }
            // §4.4.8: a valid `value` attribute RESETS the counter here.
            if let v = parseHtmlInteger(child.meta?.attrs?.value) { counter = v }
            // Step 6: this item's ordinal is the (possibly reset) counter.
            out[i] = counter
            // Step 7: advance for the next item (down when reversed).
            counter += step
        }
        return out
    }

    /// Bridge one render-loop child index to the 0-based index
    /// `ListMarkerText.marker(index:config:)` expects (its numeric systems
    /// render `index + 1`, its alphabetic/cyclic systems index from 0 — so
    /// the bridge is `ordinal − 1` for every system at once). Nil / short
    /// plans fall back to the raw index — the exact pre-wave-43 behaviour,
    /// so a non-list caller can never change output.
    static func markerIndex(_ ordinals: [Int]?, _ index: Int) -> Int {
        // Bounds-checked read: a plan shorter than the loop (impossible by
        // construction, but stated) degrades to the raw index, never traps.
        if let plan = ordinals, plan.indices.contains(index) { return plan[index] - 1 }
        // No plan ⇒ the pre-wave-43 identity (ordinal = position + 1).
        return index
    }
}
