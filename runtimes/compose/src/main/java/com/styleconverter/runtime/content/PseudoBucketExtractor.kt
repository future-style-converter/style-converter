package com.styleconverter.runtime.content

// PseudoBucketExtractor — wave-42 lane W1: the v2 `pseudos` wire → the
// ContentApplier config the renderer's existing ::before/::after wrapper
// consumes. This closes the wave-41 dead-wire find: the v2 IR carries an
// ordinary element's generated content in `component.pseudos` (spec
// 01-envelope.md: `{before?, after?, marker?}`, forwarded verbatim), the
// decoder retained it (IRModels.IRComponent.pseudos), and NOTHING read it —
// ComponentRenderer fed ContentApplier.extractBeforeAfterConfig(selectors),
// a channel no v2 document emits, so baked pseudo text never rendered
// (measured: css-counter-styles/counter-name-case-sensitive renders BLANK
// on Android vs the ref's six text lines — android-ref 0.9792 FAIL).
//
// SHAPE OF ONE BUCKET (verified against wave41-final per-test-ir):
//   { "properties": { "<css-prop>": "<raw css value>", … },   // RAW strings
//     "_text": "1-5",                                          // baked answer
//     "_lossy": true, "_lossyReasons": ["generated-content-baked"] }
// `properties` is a RAW CSS declarations map, NOT typed IR — the runtime
// has no CSS parser (the same wall RootPseudoSpec documents), so this
// bridge consumes the producer's BAKED `_text` resolution first and only
// falls back to context-free literal `content` strings. Styling
// declarations it cannot honour are named via the tracker, never dropped
// silently (repo rule: no silent fallthroughs).

import com.styleconverter.runtime.PropertyTracker
import com.styleconverter.runtime.core.ir.IRLog
import com.styleconverter.runtime.core.ir.IRProperty
import com.styleconverter.runtime.core.renderer.isBlockDisplay
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/** Log tag for this bridge's unsupported-shape reports. */
private const val TAG = "PseudoBucketExtractor"

object PseudoBucketExtractor {

    /**
     * Bridge a component's decoded `pseudos` map to the [ContentApplier]
     * config, or null when there is nothing this channel may render —
     * the seam in ComponentRenderer then falls back to the legacy
     * selectors channel (v1/fixture documents), unchanged.
     *
     * @param pseudos the decoded wire object ([IRComponent.pseudos]).
     * @param role the component's `meta.role`. A "body-root" bucket is the
     *   ROOT-SCOPE generated box and is owned by RootPseudoBox (wave-28
     *   lane PG) — consuming it here too would double-render the box, the
     *   exact hazard RootPseudoBox's banner pins. Skipped by design.
     */
    fun extractBeforeAfterConfig(
        pseudos: JsonObject?,
        role: String?
    ): ContentApplier.BeforeAfterConfig? {
        // No bucket → nothing to bridge (every pre-wave-27 document).
        if (pseudos == null) return null
        // Root-scope buckets belong to RootPseudoBox — see the KDoc above.
        if (role == "body-root") return null
        // `pseudos.marker` is deliberately NOT read here: the resolved
        // marker string rides `meta.markerText` into RenderListItemMarker
        // (wave-27 lane CBAKE), so the marker bucket is that channel's
        // source metadata, not a second render instruction.
        val before = pseudoElementConfig(pseudos["before"] as? JsonObject, "before")
        val after = pseudoElementConfig(pseudos["after"] as? JsonObject, "after")
        // Neither side produced renderable content → hand the seam back
        // to the legacy channel rather than an inert wrapper.
        if (before == null && after == null) return null
        return ContentApplier.BeforeAfterConfig(
            before = before,
            after = after,
            // Same AND-fold the legacy selectors extractor applies: one
            // block-level pseudo makes the whole wrapper stack vertically.
            isInline = (before?.isInline ?: true) && (after?.isInline ?: true)
        )
    }

    /**
     * One `pseudos.<role>` bucket → a [ContentApplier.PseudoElementConfig],
     * or null when the bucket resolves to no paintable text.
     */
    private fun pseudoElementConfig(
        bucket: JsonObject?,
        which: String
    ): ContentApplier.PseudoElementConfig? {
        // Absent side (e.g. `before` only) — nothing to build.
        if (bucket == null) return null
        // The raw declarations map (may be absent on a `_text`-only bucket).
        val decls = bucket["properties"] as? JsonObject
        // The producer's baked resolution — the css-content-3 §2 counter()/
        // string sequence ALREADY evaluated against the real document state
        // (the `generated-content-baked` lossy marker names the bake).
        // AUTHORITATIVE when present, exactly like meta.markerText.
        val baked = (bucket["_text"] as? JsonPrimitive)
            ?.takeIf { it.isString }?.content
        // Fallback: a context-free literal `content` value ("a" "b" …) —
        // resolvable without counters or attributes, so bridging it is
        // honest even when the producer did not bake.
        val text = baked ?: (decls?.get("content") as? JsonPrimitive)
            ?.contentOrNull?.let { literalContentText(it, which) }
        // css-display-3 §2: the pseudo box is inline unless declared
        // block-level. Baked buckets rarely carry `display`; default inline.
        var isInline = true
        // css-display-3 §2.4: `display: none` generates NO boxes at all —
        // the element AND its pseudo-elements. Tracked separately from the
        // inline/block fold because it suppresses the WHOLE bucket instead
        // of re-stacking it; without it the old `isInline = !isBlockDisplay`
        // branch read `none` as "not block" = inline and Android painted the
        // literal word FAIL of css-pseudo/before-dynamic-display-none (iOS
        // PseudoTextBridge already refuses any bucket declaring `display`).
        var suppressed = false
        // Wave-43 lane V7 — the bucket's OWN styling declarations, typed.
        // The render site (ContentApplier.PseudoElement) already feeds
        // config.properties through TextStyleApplier, so converting the
        // raw color/font-size/font-family strings to their typed wire
        // shapes lets the ONE existing text pipeline style the run
        // (declared values then win over PseudoTextMetrics' bottom-outs,
        // author > UA). See PseudoStyledDeclarations for the census.
        val styled = mutableListOf<IRProperty>()
        // Walk every declaration so nothing is silently dropped.
        if (decls != null) for ((prop, raw) in decls) {
            // Declaration values are string primitives; anything else is
            // not a declarations map — name it and move on.
            val value = (raw as? JsonPrimitive)?.contentOrNull ?: run {
                IRLog.warn(TAG, "::$which declaration '$prop' is not a primitive — dropped")
                null
            } ?: continue
            when (prop.trim().lowercase()) {
                // Consumed above (baked `_text` wins; literal fallback).
                "content" -> {}
                // css-display-3 §2.4 first, then §2: `none` kills the box
                // outright, otherwise a block-level pseudo stacks vertically.
                "display" ->
                    if (isNoneDisplay(value)) suppressed = true
                    else isInline = !isBlockDisplay(value)
                // Counter mutations are INPUTS to the producer's bake — a
                // baked `_text` already contains their effect. Without a
                // bake there is no counter machinery fed by this raw-string
                // channel, so the declaration is named, not silently lost.
                "counter-increment", "counter-reset", "counter-set" ->
                    if (baked == null) {
                        PropertyTracker.markUnhandled("Pseudo::$which/$prop")
                        IRLog.warn(TAG, "::$which '$prop: $value' has no baked _text — counter state not applied")
                    }
                // Wave-43 lane V7: the styling trio the census carries —
                // color / font-size / font-family — converts to the typed
                // wire shapes the pseudo Text pipeline already consumes.
                "color", "font-size", "font-family" ->
                    when (val c = PseudoStyledDeclarations.convert(prop.trim().lowercase(), value)) {
                        // Typed — the run renders with the declared value.
                        is PseudoStyledDeclarations.Conversion.Typed -> styled += c.property
                        // A CSS-wide keyword collapsing to the run's
                        // existing default — consumed, correctly, as no-op.
                        PseudoStyledDeclarations.Conversion.Inert -> {}
                        // Unconvertible flavor (var()/calc()/keyword sizes)
                        // — named exactly like before this lane.
                        PseudoStyledDeclarations.Conversion.Unsupported -> {
                            PropertyTracker.markUnhandled("Pseudo::$which/$prop")
                            IRLog.warn(TAG, "::$which '$prop: $value' unsupported by the pseudos-bucket bridge")
                        }
                        // convert() only returns null for names outside its
                        // trio — unreachable behind this branch's match.
                        null -> {}
                    }
                // Every other styling declaration (margins, borders, …) is a
                // raw CSS string this runtime cannot type — tracked so the
                // gap stays visible in the coverage report.
                else -> {
                    PropertyTracker.markUnhandled("Pseudo::$which/$prop")
                    IRLog.warn(TAG, "::$which '$prop: $value' unsupported by the pseudos-bucket bridge")
                }
            }
        }
        // A `display: none` bucket generates no box, so nothing here may
        // render — checked BEFORE the ink test because the suppression is
        // independent of whether the bucket baked text (the FAIL string of
        // before-dynamic-display-none bakes fine; it must still not paint).
        if (suppressed) return null
        // No paintable ink: `content: ""` (or none/normal, or an
        // unresolvable function) generates no glyphs, and no decoration
        // channel is bridged, so there is nothing to draw — mirror
        // RootPseudoSpec.paints and render nothing rather than an empty box.
        if (text.isNullOrEmpty()) return null
        return ContentApplier.PseudoElementConfig(
            // One resolved run — the wrapper's buildContentString passes
            // ContentValue.Text through verbatim (no counter context needed).
            content = listOf(ContentValue.Text(text)),
            // Wave-43 lane V7: the bucket's OWN color/font-size/font-family,
            // converted to typed entries above — the render site's
            // TextStyleApplier resolves them (em against the threaded
            // inherited base) and PseudoTextMetrics lets declared values
            // win over its WPT bottom-outs (author > UA). Declarations the
            // conversion could not type were named in the walk.
            properties = styled,
            isInline = isInline
        )
    }

    /**
     * Does this `display` value suppress the box entirely (css-display-3
     * §2.4: `none` generates no boxes, pseudo-elements included)? A token
     * scan rather than equality — raw declarations reach this bridge
     * unparsed, so `none !important` must match too — and `none` is
     * exclusive in the display grammar (it can never pair with a
     * box-generating keyword the way `block flow` does), which makes
     * containment safe where isBlockDisplay needs the same scan for pairs.
     */
    private fun isNoneDisplay(value: String): Boolean =
        value.trim().lowercase().split(Regex("\\s+")).contains("none")

    /**
     * A literal `content` value → its resolved text, or null when the
     * value needs no box (`none`/`normal`, css-content-3 §1.2) or needs
     * context this bridge does not have (counter()/attr()/url()/quotes —
     * the producer bakes those into `_text` when resolvable).
     *
     * Handles the css-syntax-3 §4.3.7 escape SIMPLIFIED to backslash-next-
     * char (`\"` → `"`); hex escapes are not decoded — acceptable because
     * this path only runs when the producer did not bake, and every baked
     * corpus payload carries `_text`.
     */
    internal fun literalContentText(value: String, which: String): String? {
        // Whitespace around the whole value is never content.
        val t = value.trim()
        // Keywords first: none/normal generate NO pseudo box at all.
        if (t.isEmpty() || t.equals("none", true) || t.equals("normal", true)) return null
        // Concatenate a sequence of quoted strings ("1" "-" "5" → 1-5).
        val sb = StringBuilder()
        var i = 0
        while (i < t.length) {
            val c = t[i]
            // Inter-string whitespace separates tokens, contributes nothing.
            if (c == ' ' || c == '\t' || c == '\n') { i++; continue }
            // Any non-quote token makes the value context-dependent
            // (counter(), attr(), open-quote, url(), …) — name it and
            // yield nothing rather than a wrong partial string.
            if (c != '"' && c != '\'') {
                PropertyTracker.markUnhandled("Pseudo::$which/content")
                IRLog.warn(TAG, "::$which 'content: $t' is not a literal string sequence — not rendered")
                return null
            }
            // Consume one quoted string, honoring the simplified escape.
            i++
            while (i < t.length && t[i] != c) {
                if (t[i] == '\\' && i + 1 < t.length) { sb.append(t[i + 1]); i += 2 }
                else { sb.append(t[i]); i++ }
            }
            // An unterminated string is a malformed wire — never guess.
            if (i >= t.length) {
                IRLog.warn(TAG, "::$which 'content: $t' has an unterminated string — not rendered")
                return null
            }
            // Skip the closing quote and continue with the next token.
            i++
        }
        return sb.toString()
    }
}
