package com.styleconverter.runtime.core.renderer

// RootPseudoSpec — the PURE half of the wave-28 lane-PG root-scope
// generated-box path: raw `pseudos` declarations → a tiny paintable box
// spec, plus the containment gate that decides whether the body's own
// `direction` may place that box. No Compose types here, so every rule is
// JVM-unit-pinnable (RootPseudoSpecTest) without an emulator.
//
// ── WHY THIS EXISTS ────────────────────────────────────────────────────
// The WPT extractor merges every `html` / `body` / `:root` / `*` rule into
// ONE synthetic component tagged `meta.role: "body-root"`, and since
// wave-27 A-RC2 it routes root-scope PSEUDO-ELEMENT rules
// (`html::before { content:""; width:100px; height:100px;
// background:orange; display:block }`) into that component's `pseudos`
// bucket instead of vandalising its flat declaration bag. Compose decoded
// that bucket and rendered NOTHING from it (IRModels' documented TODO), so
// the whole css-contain/contain-body-dir + contain-body-w-m family painted
// no orange square at all — MEASURED in
// tools/titan/runs/wave27-final/sections/css-contain: the Chromium ref has
// 10 000 orange px at image [16,16]-[115,115], the Android and iOS captures
// have ZERO orange px, while web painted the box (misplaced, fixed in the
// same lane by RootPseudoPlacement.ts).
//
// ── WHY A NARROW BRIDGE AND NOT THE FULL ENGINE ────────────────────────
// The bucket's `properties` is a RAW CSS declarations map (spec 01: the
// extractor's payload, forwarded verbatim), not a typed IR property list —
// the runtime has no CSS parser to turn `"100px"` into a typed Length. Web
// escapes that by handing the map to the DOM as inline styles; the natives
// cannot. So this bridges the SMALL declaration set the root-scope family
// actually uses and LOGS everything else, per the repo's no-silent-
// fallthrough rule. Twinned 1:1 by SwiftUI's RootPseudoSpec.swift.

import com.styleconverter.runtime.core.ir.IRLog
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/** Log tag for every unsupported-declaration report from this bridge. */
private const val TAG = "RootPseudoSpec"

/**
 * A root-scope generated box, reduced to what a native runtime can paint.
 *
 * @property widthPx used inline size in CSS px, or null for `auto`.
 * @property heightPx used block size in CSS px, or null for `auto`.
 * @property backgroundCss the raw `background`/`background-color` literal,
 *   left as a STRING so the caller resolves it through the platform's own
 *   color parser (Compose: ValueExtractors.parseCssColorLiteral) rather
 *   than this pure module growing a second color table.
 * @property text the literal `content` string, already unquoted; empty for
 *   `content: ""` (the whole contain family) and for unresolvable
 *   `counter()` / `attr()` / `url()` values, which are logged.
 * @property isBlock whether `display` generates a block-level box — the
 *   only display this bridge can place in the block flow.
 */
data class RootPseudoSpec(
    val widthPx: Float? = null,
    val heightPx: Float? = null,
    val backgroundCss: String? = null,
    val text: String = "",
    val isBlock: Boolean = false,
) {
    /**
     * Is there anything to draw? A box with neither a background nor a
     * content string paints nothing at any size, so the renderer skips it
     * entirely rather than emitting an invisible full-width shim.
     */
    val paints: Boolean get() = backgroundCss != null || text.isNotEmpty()
}

/**
 * Bridge one `pseudos.<role>` bucket to a [RootPseudoSpec], or null when
 * the bucket carries no declarations at all.
 *
 * @param bucket the wire object — `{ "properties": { … } }`, the shape the
 *   extractor emits and the decoder forwards verbatim.
 * @param role "before" / "after", for the log lines only.
 */
fun rootPseudoSpec(bucket: JsonObject?, role: String): RootPseudoSpec? {
    // The declarations live one level down; anything else is not a bucket.
    val decls = bucket?.get("properties") as? JsonObject ?: return null
    if (decls.isEmpty()) return null
    // Accumulators — every field defaults to "not declared".
    var widthPx: Float? = null
    var heightPx: Float? = null
    var background: String? = null
    var text = ""
    var isBlock = false
    for ((prop, raw) in decls) {
        // Only string/number primitives are declaration values; a nested
        // object means the payload is not a declarations map at all.
        val value = (raw as? JsonPrimitive)?.contentOrNull ?: run {
            IRLog.warn(TAG, "::$role declaration '$prop' is not a primitive — dropped")
            null
        } ?: continue
        when (prop.trim().lowercase()) {
            // css-sizing-3 §5: the used inline/block size. Absolute px only —
            // %, em, calc() and var() are runtime-dependent by the IR's own
            // rule (CLAUDE.md "null means runtime-dependent").
            "width" -> widthPx = cssPxOrWarn(value, prop, role)
            "height" -> heightPx = cssPxOrWarn(value, prop, role)
            // css-backgrounds-3 §2.11: the only background component this
            // box path paints is the color layer.
            "background", "background-color" -> background = value.trim()
            // css-display-3 §2: only a block-level box can be placed in the
            // root's block flow by the box builder.
            "display" -> isBlock = isBlockDisplay(value)
            // css-content-3 §2: the literal string is the only content type
            // resolvable without a counter/attr context.
            "content" -> text = contentLiteral(value, role)
            // No silent fallthrough: everything else is a real declaration
            // this bridge cannot honour, so it is named in the log.
            else -> IRLog.warn(TAG, "::$role declaration '$prop: $value' unsupported by the root-scope box bridge")
        }
    }
    return RootPseudoSpec(widthPx, heightPx, background, text, isBlock)
}

/**
 * `<length>` in absolute px, or null (with a log) for every other unit.
 * A bare `0` is legal CSS (css-values-4 §6) and resolves to zero.
 */
private fun cssPxOrWarn(value: String, prop: String, role: String): Float? {
    val t = value.trim().lowercase()
    if (t == "0") return 0f
    val px = t.removeSuffix("px").toFloatOrNull().takeIf { t.endsWith("px") }
    if (px == null) IRLog.warn(TAG, "::$role '$prop: $value' is not an absolute px length — treated as auto")
    return px
}

/**
 * Does this `display` value generate a BLOCK-LEVEL box? Token scan rather
 * than equality so css-display-3 §2's two-value syntax (`block flow`)
 * passes too; `list-item` is block-level by §2.1.
 */
internal fun isBlockDisplay(value: String): Boolean {
    val tokens = value.trim().lowercase().split(Regex("\\s+"))
    return tokens.contains("block") || tokens.contains("list-item") || tokens.contains("flow-root")
}

/**
 * The literal `content` string with its quotes removed. `none` / `normal`
 * generate no box (css-content-3 §2.1) and every function form
 * (counter() / attr() / url() / image-set()) needs context this bridge
 * does not have — all resolve to the empty string, the functions loudly.
 */
internal fun contentLiteral(value: String, role: String): String {
    val t = value.trim()
    if (t.equals("none", true) || t.equals("normal", true)) return ""
    // A quoted string: strip ONE matching pair of quotes.
    if (t.length >= 2 && ((t.startsWith("\"") && t.endsWith("\"")) || (t.startsWith("'") && t.endsWith("'")))) {
        return t.substring(1, t.length - 1)
    }
    IRLog.warn(TAG, "::$role 'content: $t' is not a literal string — rendered as empty")
    return ""
}

/**
 * wave-28 lane PG — does this body-root's containment take the body OFF
 * the writing-mode/direction propagation path?
 *
 * css-writing-modes-4 §8.1 propagates the BODY's `direction` to the
 * viewport (the ONLY channel by which a body declaration can reach a box
 * generated on the root), and css-contain-1 §3.1 removes a contained body
 * from that channel. So on a contained body-root the generated box keeps
 * the root's own inline direction — the initial `ltr`, i.e. physically
 * LEFT, which is exactly what the family's shared reference
 * (contain-body-w-m-001-ref.html, "Test passes if the orange square is in
 * the upper-left corner") asserts.
 *
 * The token test is deliberately IDENTICAL to the background-propagation
 * gate the three canvases already share ([containmentBlocksCanvasPropagation],
 * web `bodyRootHasContainment`, SwiftUI `WPTCanvas.containmentBlocksPropagation`):
 * any non-`NONE` keyword counts, because neither spec grades propagation
 * by containment KIND and the corpus proves it — contain-body-dir-001..004
 * declare layout / paint / size / style (VERIFIED against the corpus
 * sources, wave-28 skeptic — an earlier draft of this comment said
 * "content / strict" for 003/004, which the test files do not declare)
 * and all four match the same reference. Delegates so the two questions
 * can never drift apart.
 *
 * THE MERGED html+body CAVEAT is the same one [containmentBlocksCanvasPropagation]
 * documents: one bag for html and body means `html { contain }` and
 * `body { contain }` are indistinguishable here, and so are `html::before`
 * and `body::before`. Harmless for this corpus (contain-html-dir-001..004
 * put the containment on html and match the SAME reference), and the honest
 * fix for the rest is a wire that keeps html and body apart.
 */
fun containmentBlocksDirectionPropagation(containTokens: List<String>?): Boolean =
    containmentBlocksCanvasPropagation(containTokens)
