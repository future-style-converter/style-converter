package com.styleconverter.runtime.typography

// Retrospective lane R3 (finding A5#4) — the `<'block-ellipsis'>` component
// of the two-value `line-clamp` grammar on Compose.
//
// css-overflow-4 §5.1: `line-clamp: none | [ <integer [1,∞]> || <'block-
// ellipsis'> ] -webkit-legacy?` — the second component sets `block-ellipsis`
// (§4.2), whose `no-ellipsis` value means the clamp still discards lines
// past the Nth but NO marker may be drawn and no content displaced to make
// room for one; WPT block-ellipsis-023 asserts exactly that, and -024 asserts
// the same for the EMPTY string (`line-clamp: 4 ""`), which the converter
// keeps verbatim because computed values retain their string
// (LineClampProperty.BlockEllipsis.Text).
//
// The wire (converter LineClampProperty, wave 42): the marker rides on the
// `lines` variant as an ADDITIVE nested object —
//   {"type":"lines","count":4,"ellipsis":{"type":"no-ellipsis"}}      -023
//   {"type":"lines","count":4,"ellipsis":{"type":"string","value":""}} -024
//   {"type":"lines","count":4,"ellipsis":{"type":"auto"}}   / absent    → marker
// (absent for `4` and `4 ellipsis`, the marker longhand's initial value).
//
// Until this file, Compose's three LineClamp readers (LineClampCap.linesCount,
// TextStyleApplier.extractLineClampValue, TypographyExtractor.extractLineClamp)
// read only {type,count} — the marker component was a SwiftUI-only behaviour
// (LineClampExtractor.markerSuppressed, wave 46) and web read it too
// (LineClampExtractor.suppressesMarker). One shared reader here, so the
// three Compose consumers can never disagree about the marker, exactly as
// Swift funnels its cap AND its leaf through one LineClampExtractor.
//
// Compose twin semantics: SwiftUI's `.lineLimit` cannot truncate without
// painting "…", so Swift re-routes a marker-less clamp through its height
// cap (LineClampCap.leafLineLimit → nil). Compose's Text(maxLines = N,
// overflow = Clip) discards without a glyph natively, so the twin is a
// DECISION, not a re-route: the placeholder's overflow must be Clip, never
// Ellipsis, when the marker is suppressed (ComponentRenderer.placeholder-
// Overflow; LineClampApplier.getTextOverflow / TypographyApplier.hasEllipsis
// for the config-driven paths). Pixel exposure at wave49-final: -023/-024
// already render without a marker on Android (no TextOverflow declared →
// the renderer's default Clip; per-line ink runs 3 and 4 are identical,
// n=493 each), so this makes the existing outcome honest and pins it — the
// only cells whose output could differ are ones pairing a suppressed marker
// with an explicit `text-overflow: ellipsis`, of which the corpus has none.

import com.styleconverter.runtime.core.ir.IRProperty
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

object LineClampWire {

    /** The IR type the converter emits for `line-clamp`. */
    const val TYPE: String = "LineClamp"

    /**
     * True ONLY for the two values whose effect is "clamp without a marker":
     *   • `{"type":"no-ellipsis"}` — the keyword (css-overflow-4 §4.2);
     *   • `{"type":"string","value":""}` — the empty string (WPT -024).
     * Absent (initial `ellipsis`), `auto` and a non-empty string all draw a
     * marker → false. `none` / `auto` clamps and the legacy bare-integer
     * shape carry no marker component → false. A malformed payload also
     * reads false, so a wire this reader does not understand never removes
     * a marker the author did not forbid — byte-parallel to Swift's
     * LineClampExtractor.markerSuppressed(_:).
     */
    fun markerSuppressed(data: JsonElement?): Boolean {
        // Legacy scalar shapes (bare int / "none") have no marker slot.
        val outer = data as? JsonObject ?: return false
        // TextStyleApplier.extractLineClampValue accepts a `{"clamp":{…}}`
        // envelope; honour the same nesting so the two readers see ONE
        // declaration (the live wire is the flat sealed object).
        val obj = (outer["clamp"] as? JsonObject) ?: outer
        // Only the fixed-count variant carries the component.
        if ((obj["type"] as? JsonPrimitive)?.contentOrNull != "lines") return false
        // Absent component → the initial value `ellipsis` → marker drawn.
        val ellipsis = obj["ellipsis"] as? JsonObject ?: return false
        return when ((ellipsis["type"] as? JsonPrimitive)?.contentOrNull) {
            // The explicit "never draw one" keyword.
            "no-ellipsis" -> true
            // An author string: only the EMPTY string renders nothing. A
            // missing "value" reads as empty — the Swift twin's `?? ""`.
            "string" -> (ellipsis["value"] as? JsonPrimitive)?.contentOrNull.isNullOrEmpty()
            // `auto`, unknown discriminators → a marker is drawn.
            else -> false
        }
    }

    /**
     * The element's marker decision over its declaration list: the LAST
     * `LineClamp` declaration wins (cascade order — Swift folds the list in
     * order and each declaration overwrites the flag, which is the same
     * thing). No declaration → no clamp → no marker to suppress.
     */
    fun markerSuppressed(properties: List<IRProperty>): Boolean =
        markerSuppressed(properties.lastOrNull { it.type == TYPE }?.data)

    /** The same decision over the (type, data) pair lists the facades use. */
    fun markerSuppressedInPairs(properties: List<Pair<String, JsonElement?>>): Boolean =
        markerSuppressed(properties.lastOrNull { it.first == TYPE }?.second)
}
