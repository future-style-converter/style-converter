package app.parsing.css.properties.longhands.layout.flexbox

import app.irmodels.IRProperty
import app.irmodels.properties.layout.flexbox.AlignSelfProperty
import app.parsing.css.properties.InvalidDeclaration
import app.parsing.css.properties.longhands.PropertyParser

// `align-self` — css-align-3 §6.1 grammar:
//   auto | normal | stretch | <baseline-position> |
//   <overflow-position>? <self-position>
//   <baseline-position> = [ first | last ]? && baseline          (§4.2)
//   <self-position>     = center | start | end | self-start | self-end |
//                         flex-start | flex-end                   (§4.3)
// plus the css-anchor-position-1 §6 `anchor-center` keyword.
//
// Wave 52 (lane L7, static-position T3). Three value classes used to fall
// through to the Generic passthrough (`_unmapped: true`): `normal`, the
// two-token baseline positions, and the physical `left`/`right`. The web
// runtime drops static Generic values by design (a parser-gap detector), so
// `align-self: last baseline` never reached the browser and every
// css-grid/abspos/*-last-baseline-* mark painted at the grid-area START
// instead of the `safe self-end` fallback the ref shows (§4.2) — 12 web
// cells at 0.9954–0.9983. Typed now: `normal` → NORMAL, `baseline` and
// `first baseline` → BASELINE (the same value, §4.2), `last baseline` →
// LAST_BASELINE. `left`/`right` are NOT in the align-self grammar (they
// belong to justify-self only, §6.1), so the declaration is INVALID and is
// dropped (css-syntax-3 §2.2) — the box then computes `auto`, which is what
// the refs paint. `<overflow-position> <self-position>` (`safe center`)
// stays on the Generic wire on purpose: the typed enum cannot carry the
// overflow keyword, and both mobile runtimes already parse it there.
object AlignSelfPropertyParser : PropertyParser {

    // css-align-3 §6.1: the physical keywords only justify-self accepts.
    private val physicalOnly = setOf("left", "right")

    // css-align-3 §4.4 overflow keywords that may prefix a <self-position>.
    private val overflowWords = setOf("safe", "unsafe")

    override fun parse(value: String): IRProperty? {
        // CSS keywords are ASCII case-insensitive; normalise once.
        val trimmed = value.trim().lowercase()
        // Whitespace-separated component values (no functions in this grammar).
        val tokens = trimmed.split(Regex("\\s+")).filter { it.isNotEmpty() }
        // Two tokens: a <baseline-position> (either order, `&&` in §4.2) or an
        // overflow-prefixed position (kept Generic, see header).
        if (tokens.size == 2) {
            // Order-free comparison of the two tokens (`last baseline` ==
            // `baseline last` under the `&&` combinator).
            val set = tokens.toSet()
            return when {
                // §4.2: `last baseline` — fallback alignment `safe self-end`.
                set == setOf("last", "baseline") ->
                    AlignSelfProperty(AlignSelfProperty.AlignSelf.LAST_BASELINE)
                // §4.2: `first baseline` is the SAME value as `baseline`.
                set == setOf("first", "baseline") ->
                    AlignSelfProperty(AlignSelfProperty.AlignSelf.BASELINE)
                // `safe left` / `unsafe right`: left/right are never valid in
                // align-self, with or without an overflow keyword (§6.1).
                tokens[0] in overflowWords && tokens[1] in physicalOnly -> InvalidDeclaration
                // Anything else two-token (`safe center`, …) → Generic wire.
                else -> null
            }
        }
        // Single-keyword values; anything longer is unmodelled → Generic.
        if (tokens.size != 1) return null
        val alignSelf = when (tokens[0]) {
            "auto" -> AlignSelfProperty.AlignSelf.AUTO
            // §6.1: `normal` — behaves as stretch/start per layout mode; typed
            // so the web runtime emits it instead of dropping it.
            "normal" -> AlignSelfProperty.AlignSelf.NORMAL
            "flex-start" -> AlignSelfProperty.AlignSelf.FLEX_START
            "flex-end" -> AlignSelfProperty.AlignSelf.FLEX_END
            "center" -> AlignSelfProperty.AlignSelf.CENTER
            "baseline" -> AlignSelfProperty.AlignSelf.BASELINE
            "stretch" -> AlignSelfProperty.AlignSelf.STRETCH
            "start" -> AlignSelfProperty.AlignSelf.START
            "end" -> AlignSelfProperty.AlignSelf.END
            "self-start" -> AlignSelfProperty.AlignSelf.SELF_START
            "self-end" -> AlignSelfProperty.AlignSelf.SELF_END
            // CSS Anchor Positioning Level 1 §6 — keyword survives extraction
            // even though SC has no anchor-positioning runtime; per-platform
            // Appliers collapse it to `center` per the spec's "no default
            // anchor in scope" fallback. Mirrors the existing arm on
            // JustifySelfPropertyParser.kt:42.
            "anchor-center" -> AlignSelfProperty.AlignSelf.ANCHOR_CENTER
            // §6.1: physical keywords are justify-self-only → invalid here,
            // dropped (the PropertiesParser InvalidDeclaration channel).
            in physicalOnly -> return InvalidDeclaration
            // Unknown keyword: keep the author bytes on the Generic wire.
            else -> return null
        }
        // Typed single-keyword value.
        return AlignSelfProperty(alignSelf)
    }
}
