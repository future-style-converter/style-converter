package com.styleconverter.runtime.layout.flexbox

// Phase 7 step 2 — flexbox sub-extractor for the style-engine LayoutConfig.
//
// This extractor is a PARTIAL LayoutConfig producer: it consumes the IR
// property pair list and returns a LayoutConfig populated only in the
// flexbox fields (display, flex*, justify/align*, order). All grid/position/
// advanced/root fields are left null so the later phase-step extractors can
// merge into the same LayoutConfig without overwriting each other.
//
// Precedent:
//   - BorderSideExtractor — single-file multi-property extractor (27 props)
//   - FlexExtractor (sibling file) — the LEGACY extractor whose keyword
//     mappings we deliberately mirror one-for-one so ComponentRenderer
//     behaviour stays byte-identical when it's wired to the engine path.
//
// Registration: LayoutExtractor already claims every flexbox property name
// against PropertyRegistry in its init {} block. FlexboxExtractor must NOT
// re-register — doing so would trip the "duplicate registration" check.

import com.styleconverter.runtime.core.types.ValueExtractors
import com.styleconverter.runtime.layout.AlignmentKeyword
import com.styleconverter.runtime.layout.DisplayKind
import com.styleconverter.runtime.layout.FlexBasisValue
import com.styleconverter.runtime.layout.FlexDirection as EngineFlexDirection
import com.styleconverter.runtime.layout.FlexWrap as EngineFlexWrap
import com.styleconverter.runtime.layout.LayoutConfig
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.intOrNull

/**
 * Extract flexbox-only fields from an IR property stream into a [LayoutConfig].
 *
 * The 12 properties handled here match the Phase 7b scope list:
 *   Display, FlexDirection, FlexWrap,
 *   FlexGrow, FlexShrink, FlexBasis,
 *   JustifyContent, AlignItems, AlignContent,
 *   AlignSelf, Order,
 *   BoxOrient (legacy -webkit-box-orient, treated as FlexDirection).
 *
 * Every field on the returned [LayoutConfig] that is NOT flexbox-related is
 * null. Later sub-extractors (grid, position, advanced, root) are expected
 * to fill the remaining slots via `.copy(...)`. The merge strategy lives in
 * [com.styleconverter.runtime.layout.LayoutExtractor.extractLayoutConfig].
 */
object FlexboxExtractor {

    /**
     * Build a flexbox-only [LayoutConfig] from the IR property pair list.
     *
     * @param properties (propertyType, data) pairs, usually from
     *                   IRComponent.properties.map { it.type to it.data }.
     * @return A LayoutConfig whose flexbox fields are populated from the
     *         input and whose grid/position/advanced/root fields are all null.
     */
    fun extract(properties: List<Pair<String, JsonElement?>>): LayoutConfig {
        // Accumulator starts as the identity scaffold and gets `.copy()`'d on
        // each hit. Using a var + copy keeps the type checker happy without a
        // builder helper class.
        var cfg = LayoutConfig.Empty

        properties.forEach { (type, data) ->
            // Keywords come through as uppercase primitives (e.g. "FLEX_START").
            // We lowercase + swap `_`→`-` so the match arms mirror CSS syntax
            // exactly — this keeps grep'ability against the CSS spec high.
            //
            // Guarded: extractKeyword calls .jsonPrimitive on the inner
            // "value" field, which throws IAE for object-shaped numeric data
            // (e.g. FlexGrow's {"value":{"type":"Number","value":1.0}}).
            // Swallow those so downstream branches that use `data` directly
            // (FlexGrow/FlexShrink/Order/FlexBasis) can still run.
            val kw = try {
                ValueExtractors.extractKeyword(data)
            } catch (e: IllegalArgumentException) {
                null
            }?.lowercase()?.replace('_', '-')

            cfg = when (type) {
                // --- display ------------------------------------------------
                // Mirrors DisplayProperty.kt value set. Unknown keywords fall
                // through to `Block` (CSS default for a non-flex element).
                "Display" -> cfg.copy(display = parseDisplay(kw))

                // --- flex axis ----------------------------------------------
                "FlexDirection" -> cfg.copy(flexDirection = parseFlexDirection(kw))
                "FlexWrap" -> cfg.copy(flexWrap = parseFlexWrap(kw))

                // Legacy -webkit-box-orient: vertical ≈ flex-direction: column.
                // Only applied when the explicit FlexDirection slot is still
                // empty, so an authored `flex-direction` always wins.
                "BoxOrient" -> if (cfg.flexDirection == null) {
                    cfg.copy(flexDirection = parseBoxOrient(kw))
                } else cfg

                // --- content + item alignment -------------------------------
                "JustifyContent" -> cfg.copy(justifyContent = parseAlignment(kw))
                "AlignItems" -> cfg.copy(alignItems = parseAlignment(kw))
                "AlignContent" -> cfg.copy(alignContent = parseAlignment(kw))
                "AlignSelf" -> cfg.copy(alignSelf = parseAlignment(kw))

                // --- numeric item properties --------------------------------
                // The IR wraps flex-grow/shrink as
                //   { "value": { "type": "...Number", "value": 1.5 } }
                // which ValueExtractors.extractFloat (one-level-deep) misses.
                // [deepFloat] / [deepInt] below walk the `value` chain. Null
                // results fall through cleanly so an unparseable value stays
                // null, NOT 0 (preserves the semantic of "unset").
                "FlexGrow" -> deepFloat(data)
                    ?.let { cfg.copy(flexGrow = it) } ?: cfg
                "FlexShrink" -> deepFloat(data)
                    ?.let { cfg.copy(flexShrink = it) } ?: cfg
                "Order" -> deepInt(data)
                    ?.let { cfg.copy(order = it) } ?: cfg

                // --- flex-basis ---------------------------------------------
                // FlexBasis has a compound grammar: keyword (auto/content) or
                // <length-percentage>. Step 1 LayoutConfig only exposes a
                // `Default` placeholder, so we map any non-null input to it;
                // the richer variants arrive when FlexBasisValue is expanded
                // in a later step. TODO(phase7): wire length/percentage cases
                // once FlexBasisValue has Length / Percentage / Auto variants.
                "FlexBasis" -> cfg.copy(flexBasis = FlexBasisValue.Default)

                // Anything else: pass through unchanged — the other
                // sub-extractors will claim it.
                else -> cfg
            }
        }

        return cfg
    }

    // --- keyword parsers -----------------------------------------------------
    //
    // Kept as private functions (not inline `when`s) so the test suite can
    // cover each mapping surface with a minimal fixture, and so adding a new
    // keyword is a one-line diff.

    /**
     * Walk a JSON value looking for the first primitive numeric payload,
     * descending through `{ "value": ... }` wrappers up to [maxDepth] levels.
     *
     * The IR commonly wraps scalars as
     *   {"value": {"type": "...Number", "value": 1.5}}
     * and ValueExtractors.extractFloat only unwraps the outermost level.
     * Rather than patch the shared extractor (risk of breaking unrelated
     * callers), we do the deep walk locally.
     */
    private fun deepFloat(json: JsonElement?, maxDepth: Int = 3): Float? {
        if (json == null) return null
        var node: JsonElement = json
        repeat(maxDepth) {
            when (node) {
                is JsonPrimitive -> return (node as JsonPrimitive).floatOrNull
                is JsonObject -> {
                    val obj = node as JsonObject
                    // Prefer "value" then "numeric" — matches ValueExtractors' order.
                    node = obj["value"] ?: obj["numeric"] ?: return null
                }
                else -> return null
            }
        }
        return (node as? JsonPrimitive)?.floatOrNull
    }

    /** Int analogue of [deepFloat]. */
    private fun deepInt(json: JsonElement?, maxDepth: Int = 3): Int? {
        if (json == null) return null
        var node: JsonElement = json
        repeat(maxDepth) {
            when (node) {
                is JsonPrimitive -> return (node as JsonPrimitive).intOrNull
                is JsonObject -> {
                    val obj = node as JsonObject
                    node = obj["value"] ?: obj["numeric"] ?: return null
                }
                else -> return null
            }
        }
        return (node as? JsonPrimitive)?.intOrNull
    }

    /** Map CSS `display` keyword → [DisplayKind]. Null/unknown → Block. */
    private fun parseDisplay(kw: String?): DisplayKind = when (kw) {
        "flex" -> DisplayKind.Flex
        "inline-flex" -> DisplayKind.InlineFlex
        "grid" -> DisplayKind.Grid
        "inline-grid" -> DisplayKind.InlineGrid
        "inline" -> DisplayKind.Inline
        "inline-block" -> DisplayKind.InlineBlock
        "none" -> DisplayKind.None
        "contents" -> DisplayKind.Contents
        "flow-root" -> DisplayKind.FlowRoot
        // Default: block. Mirrors legacy FlexExtractor.parseDisplayType.
        else -> DisplayKind.Block
    }

    /** Map CSS `flex-direction` keyword → [EngineFlexDirection]. */
    private fun parseFlexDirection(kw: String?): EngineFlexDirection = when (kw) {
        "row-reverse" -> EngineFlexDirection.RowReverse
        "column" -> EngineFlexDirection.Column
        "column-reverse" -> EngineFlexDirection.ColumnReverse
        else -> EngineFlexDirection.Row
    }

    /** Map CSS `flex-wrap` keyword → [EngineFlexWrap]. */
    private fun parseFlexWrap(kw: String?): EngineFlexWrap = when (kw) {
        "wrap" -> EngineFlexWrap.Wrap
        "wrap-reverse" -> EngineFlexWrap.WrapReverse
        else -> EngineFlexWrap.NoWrap
    }

    /**
     * Map legacy `-webkit-box-orient` → [EngineFlexDirection].
     *
     * Per spec: `horizontal` / `inline-axis` = row; `vertical` / `block-axis`
     * = column. Used only as a fallback when FlexDirection is absent.
     */
    private fun parseBoxOrient(kw: String?): EngineFlexDirection = when (kw) {
        "vertical", "block-axis" -> EngineFlexDirection.Column
        else -> EngineFlexDirection.Row
    }

    /**
     * Map a CSS `<content-alignment>` / `<self-alignment>` keyword → the
     * unified [AlignmentKeyword] enum. We deliberately accept the union of
     * both grammars here and rely on the Applier to reject combinations that
     * don't make sense (e.g. `stretch` for justify-content).
     */
    private fun parseAlignment(kw: String?): AlignmentKeyword = when (kw) {
        // Physical + logical edges collapse to Start/End because Compose
        // Arrangement maps them to the same values once LayoutDirection is
        // resolved by CompositionLocal.
        "start" -> AlignmentKeyword.Start
        "end" -> AlignmentKeyword.End
        "flex-start" -> AlignmentKeyword.FlexStart
        "flex-end" -> AlignmentKeyword.FlexEnd
        "center" -> AlignmentKeyword.Center
        "stretch" -> AlignmentKeyword.Stretch
        "space-between" -> AlignmentKeyword.SpaceBetween
        "space-around" -> AlignmentKeyword.SpaceAround
        "space-evenly" -> AlignmentKeyword.SpaceEvenly
        "baseline" -> AlignmentKeyword.Baseline
        "normal" -> AlignmentKeyword.Normal
        "auto" -> AlignmentKeyword.Auto
        // CSS Anchor Positioning Level 1 §6: `anchor-center` resolves to
        // `center` whenever no default anchor is in scope. SDUI has no
        // anchor-positioning runtime so this fold is always correct.
        // Mirrors the iOS mapAlignment + Web AlignSelfApplier behaviour.
        "anchor-center" -> AlignmentKeyword.Center
        // Null / unknown keyword: pick `Normal` since CSS `initial` value for
        // most align-* properties is `normal`. Callers treat Normal as
        // "use platform default" — matching Compose's defaults (Start/Top).
        else -> AlignmentKeyword.Normal
    }
}
