package com.styleconverter.runtime.content

// PseudoTextBridge — wave-50 lane B3: the PURE half of the Compose ::after
// text fold, and the byte-parallel twin of iOS
// StyleEngine/content/PseudoTextBridge.swift (same name, same gate set, same
// refusal vocabulary; skeptic probes diff the two).
//
// The split mirrors the iOS pair exactly: [PseudoTextFold] owns the COMPONENT
// rewrite (which bucket may be claimed, and where the glyphs land), this file
// owns the BUCKET question — can an inline text run honestly express this
// `pseudos.after` payload at all? Keeping it separate also keeps both files
// inside the repo's 200-line target and lets the JVM pin table exercise the
// declaration gate without building a component.
//
// WHAT AN INLINE RUN CAN CARRY. The bucket's `properties` map is RAW CSS
// strings (schema/spec/01-envelope.md forwards the extractor payload
// verbatim) and this runtime has no CSS parser, so the gate is a whitelist,
// not a parse: declarations that are inert for a text run pass, the three
// styling declarations PseudoStyledDeclarations can type pass as typed
// properties, and EVERYTHING else refuses the whole bucket — named through
// the PropertyTracker, never silently flattened. A pseudo that asked for a
// 20x20 tomato square (css-cascade/scope-pseudo-element) must not be folded
// down to the letter "A".

import com.styleconverter.runtime.PropertyTracker
import com.styleconverter.runtime.core.ir.IRComponent
import com.styleconverter.runtime.core.ir.IRLog
import com.styleconverter.runtime.core.ir.IRProperty
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/** Log tag for this bridge's named refusals — shared with the fold's. */
private const val TAG = "PseudoTextFold"

object PseudoTextBridge {

    /**
     * Declarations an inline text run consumes or may ignore without lying.
     * `content` is the run itself; the counter mutations (css-lists-3 §4)
     * are INPUTS to the producer's bake and are already resolved inside the
     * baked `_text`, so re-running them here could only disagree. Byte-
     * parallel with the iOS twin's `inertDeclarations`.
     */
    private val INERT_DECLARATIONS = setOf(
        "content", "counter-increment", "counter-reset", "counter-set"
    )

    /**
     * Box-model declarations that are SPEC-DEAD when the same bucket also
     * declares `display: contents` — css-display-3 §2.5: a box-less element
     * has nothing for border/background/size to apply to. Outside that
     * shape each of these means a real box and refuses the text fold.
     * Byte-parallel with the iOS twin's `boxUnderContents`.
     */
    private val BOX_UNDER_CONTENTS = setOf(
        "border", "background", "background-color", "width", "height"
    )

    /** The styling trio [PseudoStyledDeclarations] can type. */
    private val STYLING_TRIO = setOf("color", "font-size", "font-family")

    /** One foldable run: the baked glyphs plus its typed own styling. */
    data class InlineRun(val text: String, val styling: List<IRProperty>)

    /**
     * The bucket's foldable run, or null when there is nothing an inline
     * text run can honestly express (a declaration outside the consumable
     * set, an unconvertible style value, or no baked glyphs).
     */
    fun inlineRun(bucket: JsonObject, host: IRComponent): InlineRun? {
        // The raw CSS declarations map — extractor-owned and forwarded
        // verbatim (schema/spec/01-envelope.md). Absent map = a bare text
        // bucket: nothing to gate on.
        val decls = bucket["properties"] as? JsonObject
        // Pre-scan for `display: contents`, which flips the meaning of the
        // box set below and so must be known before the walk. The token
        // test is exact because `contents` is exclusive in the display
        // grammar (css-display-3 §2 — it never pairs like `block flow`).
        val displayContents = decls?.entries
            ?.firstOrNull { it.key.trim().lowercase() == "display" }
            ?.let { (it.value as? JsonPrimitive)?.contentOrNull?.trim()?.lowercase() == "contents" }
            ?: false
        // Does the host declare its own font-size? A RELATIVE pseudo size
        // resolves against the ORIGINATING ELEMENT's size (css-values-4
        // §6.1.1), but a FontSize appended to the host's own list resolves
        // against what the HOST inherited — a different base. Refuse that
        // combination rather than paint the wrong size (zero carriers in
        // the wave49-final corpus: contain-content-011's host declares only
        // `width`, so its `3em` resolves against the 16px default, right).
        val hostDeclaresFontSize = host.properties.any { it.type == "FontSize" }
        // Accumulators for the walk.
        var refused = false
        val styling = mutableListOf<IRProperty>()
        // Sorted so a multi-key refusal logs in a deterministic order.
        if (decls != null) for (prop in decls.keys.sorted()) {
            // css-syntax-3 §5: property names are ASCII case-insensitive.
            val key = prop.trim().lowercase()
            // Consumed by the run itself / already inside the bake.
            if (key in INERT_DECLARATIONS) continue
            // `display: contents` IS this fold's own semantics (the element
            // generates no box, only its content) — consumed. Any OTHER
            // display value still refuses below: a block/none pseudo is not
            // an inline text run.
            if (key == "display" && displayContents) continue
            // Box declarations are spec-dead under display:contents.
            if (key in BOX_UNDER_CONTENTS && displayContents) continue
            // The declaration's raw value; a non-primitive is not a
            // declarations map at all and refuses like any unknown shape.
            val raw = (decls[prop] as? JsonPrimitive)?.contentOrNull
            // The styling trio converts to the typed wire shapes the one
            // existing text pipeline already resolves.
            if (raw != null && key in STYLING_TRIO) {
                when (val conv = PseudoStyledDeclarations.convert(key, raw)) {
                    // Typed — collected for the caller's uniformity gate.
                    is PseudoStyledDeclarations.Conversion.Typed -> {
                        // The em/%/rem base guard above: only a relative
                        // FontSize is at risk, and only under a host that
                        // declares its own size.
                        if (key == "font-size" && hostDeclaresFontSize &&
                            !raw.trim().lowercase().endsWith("px")
                        ) {
                            PropertyTracker.markUnhandled("PseudoText::after/font-size")
                            IRLog.warn(TAG, "::after 'font-size: $raw' needs the host's own size as its em base — bucket not folded")
                            refused = true
                        } else {
                            styling += conv.property
                        }
                        continue
                    }
                    // A CSS-wide keyword collapsing to the run's existing
                    // default — consumed, correctly, as a no-op.
                    PseudoStyledDeclarations.Conversion.Inert -> continue
                    // Unparseable flavour (var()/calc()/keyword sizes) —
                    // falls through to the named refusal below, because
                    // folding the text with the WRONG ink/size would
                    // half-render the bucket.
                    PseudoStyledDeclarations.Conversion.Unsupported -> {}
                    // convert() returns null only for names outside the
                    // trio, which this branch has already excluded.
                    null -> {}
                }
            }
            // No silent fallthrough: name the refused declaration, then
            // keep scanning so EVERY offending declaration gets a line.
            PropertyTracker.markUnhandled("PseudoText::after/$key")
            IRLog.warn(TAG, "::after declaration '$prop' is beyond the inline-text fold — bucket not folded")
            refused = true
        }
        // Any refusal leaves the bucket on its existing wrapper path —
        // exactly the behaviour this platform had before the seam, now
        // named instead of silent.
        if (refused) return null
        // The baked literal. `_text` is what the WPT extractor emits today;
        // `text` is the v2 spelling — web tolerates both in exactly this
        // order (PseudoNodeRenderer's `p._text ?? p.text`), so mirror it.
        val text = (bucket["_text"] as? JsonPrimitive)?.takeIf { it.isString }?.content
            ?: (bucket["text"] as? JsonPrimitive)?.takeIf { it.isString }?.content
        // No bake at all: a `content` the producer could not resolve
        // (e.g. `attr()` on a subtree that never laid out). Web renders the
        // empty string for these too — name the gap, fold nothing.
        if (text == null) {
            if (decls?.keys?.any { it.trim().lowercase() == "content" } == true) {
                PropertyTracker.markUnhandled("PseudoText::after/unbaked")
                IRLog.warn(TAG, "::after content has no baked _text — rendered as empty")
            }
            return null
        }
        // `content: ""` bakes an EMPTY string: a legal pseudo that paints no
        // glyphs (css-content-3 §2), so there is nothing to fold.
        return if (text.isEmpty()) null else InlineRun(text, styling)
    }
}
