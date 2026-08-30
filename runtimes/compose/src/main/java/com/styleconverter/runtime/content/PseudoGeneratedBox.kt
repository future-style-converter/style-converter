package com.styleconverter.runtime.content

// PseudoGeneratedBox — wave-49 lane A2: the ordinary-element `::before` /
// `::after` bucket that generates a BLOCK-LEVEL BOX, not an inline text run.
//
// ── THE MEASURED DEFECT ────────────────────────────────────────────────
// WPT css-pseudo/before-as-flex-container declares
//   div            { width:200px; height:100px; background:red }
//   div::before    { content:"A B"; display:flex; justify-content:space-between;
//                    width:200px; height:100px; background:green }
// The Chromium ref (tools/wpt/refs/9b5435e…/css-pseudo/
// before-as-flex-container.png) is a 200×100 GREEN rectangle: the generated
// box is the div's first child and covers its red background exactly.
// Both natives painted the FAIL colour instead — Android
// (report/images/Android/wpt__css-pseudo__before-as-flex-container.png)
// prints the bare glyphs "A B" to the LEFT of an all-red 200×100 box
// because PseudoBucketExtractor types only content/display/colour and drops
// width/height/background, and ContentApplier's wrapper places the run
// OUTSIDE the originating element's box; iOS
// (report/images/iOS/…png) paints the red box alone because
// PseudoTextBridge refuses any bucket declaring a box. Gate rows:
// ios-ref 0.9990 FAIL, android-ref 0.9626 FAIL (colorFailed = true, green
// histogram-KL 0.515), web-ref 0.9985 PASS.
//
// ── WHAT THIS FILE IS ──────────────────────────────────────────────────
// The PURE half: one raw `pseudos.<role>` declarations map → the TYPED
// IRProperty list + text of the box it generates, or null when this path
// does not own the bucket. No Compose types, so every rule is JVM-pinnable
// (PseudoGeneratedBoxTest) without an emulator. [PseudoBoxFold] turns a
// claim into a real child component; the SAME claim function is what
// PseudoBucketExtractor consults so the inline-text path can never
// double-render a bucket this path took (one decision, two consumers —
// the idiom RootPseudoSpec/CanvasRootHoist already use).
//
// ── WHY A NARROW BRIDGE ────────────────────────────────────────────────
// `properties` is a RAW CSS declarations map (spec 01-envelope.md forwards
// the extractor payload verbatim) and the runtime has no CSS parser, so
// only the declaration set below converts; ANY other declaration refuses
// the whole claim and is named (repo rule: no silent fallthroughs, never a
// half-rendered box). `position` is deliberately outside the set: an
// out-of-flow / anchored generated box needs containing-block resolution
// this bridge does not model, so the four css-anchor-position buckets keep
// their current render exactly. Twinned 1:1 by SwiftUI's PseudoBoxBridge.swift.

import com.styleconverter.runtime.PropertyTracker
import com.styleconverter.runtime.core.ir.IRLog
import com.styleconverter.runtime.core.ir.IRProperty
import com.styleconverter.runtime.core.types.ValueExtractors
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put

/** Log tag for this bridge's refusal reports. */
private const val TAG = "PseudoGeneratedBox"

object PseudoGeneratedBox {

    /**
     * A generated box this bridge can build: the typed declarations plus
     * the baked literal text the box contains.
     *
     * @property properties typed IR entries, in declaration-walk order —
     *   fed to a synthetic child component so the ONE existing style
     *   pipeline (StyleApplier + the renderer's own layout branches)
     *   paints them; never a second styling channel that could drift.
     * @property text the box's resolved content string (may be empty for
     *   `content: ""`, which still paints a background — css-content-3 §2).
     */
    data class Claim(val properties: List<IRProperty>, val text: String)

    /**
     * css-display-3 §2.1 BLOCK-LEVEL outer display types the corpus can
     * carry on a generated box. Only these take this path: an inline-level
     * pseudo (`inline`, `inline-block`, …) participates in the host's
     * INLINE flow, which is what ContentApplier's existing Row/Column
     * wrapper already approximates — re-routing it here would move ink for
     * buckets nothing is wrong with (css-cascade/scope-pseudo-element's
     * `display: inline-block` pair, android-ref 0.9746 PASS today).
     */
    private val BLOCK_LEVEL_DISPLAY =
        setOf("block", "flow-root", "list-item", "flex", "grid", "table")

    /**
     * css-align-3 §5.1 `justify-content` single keywords, raw → the wire's
     * SCREAMING_SNAKE spelling (the shape every corpus `JustifyContent`
     * datum uses — censused across wave48-final per-test-ir: FLEX_END,
     * CENTER, SPACE_BETWEEN, END, SPACE_AROUND, SPACE_EVENLY, FLEX_START,
     * STRETCH). Two-token `<overflow-position> <content-position>` values
     * are NOT here — they refuse, named, rather than losing the safety
     * qualifier silently.
     */
    private val JUSTIFY_CONTENT = setOf(
        "flex-start", "flex-end", "center", "space-between", "space-around",
        "space-evenly", "start", "end", "left", "right", "normal", "stretch",
    )

    /**
     * The generated box for one bucket, or null when this path does not
     * own it — in which case the caller keeps its existing behaviour
     * byte-identically (the inline text fold on both platforms).
     *
     * @param bucket the wire object `{ "properties": {…}, "_text": "…" }`,
     *   forwarded verbatim by the decoder (spec 01-envelope.md).
     * @param role "before" / "after" — log lines and nothing else.
     * @param hostRole the ORIGINATING component's `meta.role`. A
     *   "body-root" bucket is the ROOT-SCOPE generated box owned by
     *   RootPseudoBox (wave-28 lane PG); claiming it here would
     *   double-render the very box that path already paints.
     */
    fun claim(bucket: JsonObject?, role: String, hostRole: String?): Claim? {
        // Root-scope buckets belong to RootPseudoBox — see @param hostRole.
        if (hostRole == "body-root") return null
        // No declarations map = a bare `_text` bucket: an inline run, not a
        // box. Nothing to claim, and nothing to log (this is the norm).
        val decls = bucket?.get("properties") as? JsonObject ?: return null
        // GATE 1 — a box path needs a BLOCK-LEVEL `display`. Read before
        // the walk so an inline/undeclared bucket costs no log line at all.
        val displayKeyword = blockLevelDisplayKeyword(decls) ?: return null
        // Typed accumulator, seeded with the display the gate resolved.
        val properties = mutableListOf(
            IRProperty(type = "Display", data = JsonPrimitive(displayKeyword))
        )
        // Does the box have a background layer? Recorded because a box with
        // neither background nor glyphs paints nothing at all (see GATE 3).
        var paintsBackground = false
        // GATE 2 — walk EVERY declaration. One unconvertible declaration
        // refuses the whole claim: a half-typed box would paint a green
        // rectangle where the author asked for a bordered/positioned one.
        for ((prop, raw) in decls) {
            // Declaration values are string primitives; anything else means
            // the payload is not a declarations map at all.
            val value = (raw as? JsonPrimitive)?.contentOrNull
                ?: return refuse(role, prop, "value is not a primitive")
            when (prop.trim().lowercase()) {
                // Consumed by the text resolution below / by GATE 1.
                "content", "display" -> {}
                // css-sizing-3 §3.1.1 used inline/block size. Absolute px only —
                // %, em, calc() and var() are runtime-dependent by the IR's
                // own rule (CLAUDE.md "null means runtime-dependent").
                "width" -> properties += lengthProperty("Width", value)
                    ?: return refuse(role, prop, "not an absolute px length")
                "height" -> properties += lengthProperty("Height", value)
                    ?: return refuse(role, prop, "not an absolute px length")
                // css-backgrounds-3 §2.11: the only background component a
                // box this small can paint is the colour layer, so the
                // shorthand is accepted exactly when it IS a bare colour.
                "background", "background-color" -> {
                    properties += colorProperty(value)
                        ?: return refuse(role, prop, "not a parseable colour literal")
                    paintsBackground = true
                }
                // css-align-3 §5.1 — the box's own content distribution.
                "justify-content" -> properties += keywordProperty("JustifyContent", value)
                    ?: return refuse(role, prop, "not a single justify-content keyword")
                // css-lists-3 §4 counter mutations are INPUTS to the
                // producer's bake: the `_text` read below already contains
                // their effect, so honouring them again could only disagree.
                "counter-increment", "counter-reset", "counter-set" -> {}
                // No silent fallthrough: a declaration outside the set is a
                // real box property this bridge cannot type (border,
                // position, anchor-name, …) — refuse and name it.
                else -> return refuse(role, prop, "beyond the generated-box bridge")
            }
        }
        // The producer's baked resolution wins, exactly like meta.markerText
        // (the `generated-content-baked` lossy marker names the bake);
        // otherwise the literal string sequence, which needs no context.
        val text = (bucket["_text"] as? JsonPrimitive)?.takeIf { it.isString }?.content
            ?: (decls["content"] as? JsonPrimitive)?.contentOrNull
                ?.let { PseudoBucketExtractor.literalContentText(it, role) }
            ?: ""
        // GATE 3 — a box with no background and no glyphs paints nothing at
        // any size, so emitting it would only add an invisible layout box
        // (mirrors RootPseudoSpec.paints).
        if (!paintsBackground && text.isEmpty()) return null
        return Claim(properties, text)
    }

    /** Named refusal (repo rule: no silent fallthroughs), then null. */
    private fun refuse(role: String, prop: String, why: String): Claim? {
        PropertyTracker.markUnhandled("PseudoBox::$role/$prop")
        IRLog.warn(
            TAG, "::$role '$prop' $why — generated box not built, bucket falls back to the inline path")
        return null
    }

    /**
     * The wire `Display` keyword when this bucket's `display` generates a
     * BLOCK-LEVEL box, else null. Token scan rather than equality so
     * css-display-3 §2's two-value syntax (`block flow`) resolves on its
     * outer keyword; `none` and `contents` generate no box of their own and
     * are owned by the existing extractors, so they never reach here.
     */
    private fun blockLevelDisplayKeyword(decls: JsonObject): String? {
        val raw = (decls["display"] as? JsonPrimitive)?.contentOrNull ?: return null
        val tokens = raw.trim().lowercase().split(Regex("\\s+"))
        // `none` / `contents` are exclusive in the display grammar and mean
        // "no box of my own" — never this path.
        if (tokens.contains("none") || tokens.contains("contents")) return null
        val block = tokens.firstOrNull { it in BLOCK_LEVEL_DISPLAY } ?: return null
        // Wire spelling: SCREAMING_SNAKE (censused: "FLEX", "FLOW_ROOT", …).
        return block.uppercase().replace('-', '_')
    }

    /**
     * `<length>` in absolute px → the `{"type":"length","px":N}` datum every
     * corpus Width/Height carries; null for every other unit (see the
     * runtime-dependent rule cited at the call site).
     */
    private fun lengthProperty(type: String, value: String): IRProperty? {
        val t = value.trim().lowercase()
        // A bare `0` is a legal length (css-values-4 §6) and means zero px.
        val px = if (t == "0") 0.0
        else if (t.endsWith("px")) t.removeSuffix("px").toDoubleOrNull() ?: return null
        else return null
        return IRProperty(type = type, data = buildJsonObject {
            put("type", "length"); put("px", px)
        })
    }

    /**
     * A colour literal → the typed `{"srgb":{r,g,b,a}}` block
     * [ValueExtractors.extractColor] reads, through the SHARED literal
     * parser the runtime already uses for colour-mix endpoints (one colour
     * vocabulary, never a second table).
     */
    private fun colorProperty(value: String): IRProperty? {
        val c = ValueExtractors.parseCssColorLiteral(value) ?: return null
        return IRProperty(type = "BackgroundColor", data = buildJsonObject {
            put("srgb", buildJsonObject {
                put("r", c.red.toDouble()); put("g", c.green.toDouble())
                put("b", c.blue.toDouble()); put("a", c.alpha.toDouble())
            })
            // The author string, mirrored the way converter output does.
            put("original", value.trim())
        })
    }

    /** A single css-align-3 keyword → its SCREAMING_SNAKE wire spelling. */
    private fun keywordProperty(type: String, value: String): IRProperty? {
        val t = value.trim().lowercase()
        if (t !in JUSTIFY_CONTENT) return null
        return IRProperty(type = type, data = JsonPrimitive(t.uppercase().replace('-', '_')))
    }
}
