package com.styleconverter.runtime.core.variables

// DynamicValueResolver — the wave-6 wiring that gives the previously-dead
// CssVariableResolver + CalcExpressionEvaluator real call sites.
//
// Design: ONE list-level pre-resolution pass at the top of
// ComponentRenderer.RenderComponent, instead of per-callsite composable
// extraction. The whole style pipeline (StyleApplier → facades → appliers)
// is pure non-composable code, so routing var()/calc()/em/rem through it
// per-property would have meant touching every extractor. Rewriting the
// IRProperty list ONCE — var() substituted per css-variables-1 §2.3,
// calc()/min()/max()/clamp() evaluated with a real EvalContext, relative
// units resolved against the live font-size / containing-block channels —
// lets every downstream applier keep its frozen px-shape contract.
//
// Resolution order (schema/spec/02-values.md "Custom properties, var()
// substitution, and dynamic expressions"):
//   1. element scope → slot-parent chain (CssVariableScope merge, child
//      shadows parent — provided by the renderer's LocalCssVariables)
//   2. fallback, nested per css-variables-1 §2.3 (CssVariableResolver)
//   3. guaranteed-invalid ⇒ UNSET: the declaration is dropped from the
//      list, which renders as the property's absent/default behaviour
//      (transparent background, auto size) — the same pixels web paints.
//
// Pure JVM (no composable calls) so the pinning suite runs it directly.

import androidx.compose.runtime.compositionLocalOf
import com.styleconverter.runtime.core.expressions.CalcExpressionEvaluator
import com.styleconverter.runtime.core.ir.IRProperty
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/**
 * The containing-block channel: content-box size (in the runtime's px==dp
 * space) of the nearest ancestor box, used as the base for % inside calc()
 * and for bare %-unit spacing. Fields are null when the axis is not
 * statically known (auto-sized ancestor) — resolution then leaves the
 * declaration untouched rather than guessing (the legacy fallback paths
 * keep their behaviour).
 */
data class ContainingBlock(
    val widthPx: Float? = null,
    val heightPx: Float? = null
)

/**
 * CompositionLocal carrying [ContainingBlock] down the slot-composed tree.
 * Provided at the harness capture root (CaptureCanvas content box, 358dp)
 * and re-provided by RenderComponent for children whenever the component's
 * own resolved size is definite. Default: unknown on both axes.
 */
val LocalContainingBlock = compositionLocalOf { ContainingBlock() }

object DynamicValueResolver {

    /** CSS default font size — the em/rem base when no channel value exists. */
    const val DEFAULT_FONT_SIZE_PX = 16f

    /**
     * The real evaluation context replacing CalcExpressionEvaluator's
     * harness constants:
     *  - [containingBlockWidthPx]/[containingBlockHeightPx]: % base from
     *    the campaign's width channel ([LocalContainingBlock]).
     *  - [viewportWidthPx]/[viewportHeightPx]: LocalConfiguration screen dp
     *    (the runtime's px==dp space) for vw/vh/vmin/vmax.
     *  - [parentFontSizePx]: the inheritance channel's font-size — the em
     *    base for the element's OWN font-size declaration (css-values-4
     *    §5.1.1: em on font-size is relative to the INHERITED size).
     *  - [rootFontSizePx]: rem base. RESIDUAL CONSTANT — the harness never
     *    styles the root element, so 16px (the browser default the web
     *    reference inherits) is the honest value until a root-font channel
     *    exists.
     */
    data class Context(
        val containingBlockWidthPx: Float? = null,
        val containingBlockHeightPx: Float? = null,
        val viewportWidthPx: Float = 390f,
        val viewportHeightPx: Float = 844f,
        val parentFontSizePx: Float = DEFAULT_FONT_SIZE_PX,
        val rootFontSizePx: Float = DEFAULT_FONT_SIZE_PX,
        /**
         * True when this element participates in BLOCK layout (no flex/grid
         * ancestor container owns it — the renderer feeds
         * !LocalSelfAlignmentHandled here). Gates the guaranteed-invalid
         * width emulation below: a block-level box whose `width` declaration
         * is invalid at computed-value time computes to `auto`, and block
         * auto-width FILLS the containing block (CSS 2.1 §10.3.3) — which is
         * what the web reference paints. Compose's wrap-content default
         * would hug the label instead, so the resolver rewrites the dropped
         * declaration to the wire's 100% shape in block context only; flex
         * items keep the true drop (auto = content-based, css-flexbox-1 §9.2).
         */
        val blockLevelWidthAuto: Boolean = false
    )

    /**
     * Resolution output: the rewritten property list plus the element's own
     * resolved font-size (the em base the renderer provides to children via
     * the inheritance channel).
     */
    data class Resolution(
        val properties: List<IRProperty>,
        val fontSizePx: Float
    )

    /**
     * Properties whose % (inside calc or bare) resolves against the BLOCK
     * axis of the containing block (CSS 2.1 §10.5). Everything else —
     * including padding/margin on all four sides (§8.3/§8.4) — uses the
     * inline (width) base.
     */
    private val HEIGHT_AXIS_TYPES = setOf(
        "Height", "MinHeight", "MaxHeight",
        "BlockSize", "MinBlockSize", "MaxBlockSize",
        "Top", "Bottom"
    )

    /** Per-property outcome of a resolution attempt. */
    private sealed interface Outcome {
        /** Resolved to an absolute pixel count. */
        data class Px(val px: Float) : Outcome
        /** css-variables-1 §3 guaranteed-invalid ⇒ drop the declaration. */
        data object Invalid : Outcome
        /** Not resolvable in this context (e.g. % with no base) ⇒ keep as-is. */
        data object Keep : Outcome
    }

    /**
     * Resolve every dynamic value in [properties] against the merged
     * variable [variables] map (element ∪ slot-parent chain, child wins)
     * and the live [ctx]. Returns the SAME list instance when nothing in
     * it is dynamic — the static fixture corpus takes this path, keeping
     * the committed-baseline render byte-identical.
     */
    fun resolve(
        properties: List<IRProperty>,
        variables: Map<String, String>,
        ctx: Context
    ): Resolution {
        val scope = CssVariableScope(variables)
        // Own font-size FIRST: it is the em base for every other property
        // (css-values-4 §5.1.1), while its own em/% resolve against the
        // PARENT size (the inheritance channel).
        val fontSizePx = resolveOwnFontSize(properties, scope, ctx)
        // Fast path — identical list instance out when nothing is dynamic.
        if (properties.none { needsResolution(it) }) {
            return Resolution(properties, fontSizePx)
        }
        val out = ArrayList<IRProperty>(properties.size)
        for (p in properties) {
            val resolved = resolveProperty(p, scope, ctx, fontSizePx)
            if (resolved != null) out.add(resolved)
            // null ⇒ guaranteed-invalid ⇒ unset: declaration dropped, the
            // renderer's absent-property defaults take over (spec 02).
        }
        return Resolution(out, fontSizePx)
    }

    /**
     * Read the resolved font-size px out of an (already-resolved) property
     * list — the renderer uses this on the inheritance channel's list to
     * recover the PARENT's font size (the parent always publishes resolved
     * px shapes, so a plain px read suffices).
     */
    fun fontSizePxOf(properties: List<IRProperty>): Float? {
        val data = properties.firstOrNull { it.type == "FontSize" }?.data
        return ((data as? JsonObject)?.get("px") as? JsonPrimitive)
            ?.doubleOrNull?.toFloat()
    }

    /**
     * Compute the containing block this component establishes for its
     * children: the CONTENT box (CSS 2.1 §10.1 — declared border-box size
     * minus padding and border bands, mirroring the applier chain's
     * border-box semantics). An axis is null (unknown) when the component
     * has no definite resolved size on it — % sizes resolve against the
     * parent's base when that is known; auto/content-sized axes stay
     * unknown rather than guessed (the web harness wraps unsized
     * components in width:fit-content, which is not statically knowable).
     */
    fun childContainingBlock(
        properties: List<IRProperty>,
        parent: ContainingBlock
    ): ContainingBlock {
        // Top-level px of the first matching property (post-resolution all
        // definite lengths carry it, both typed and bare shapes).
        fun pxOf(vararg types: String): Float? {
            for (t in types) {
                val obj = properties.firstOrNull { it.type == t }?.data as? JsonObject ?: continue
                (obj["px"] as? JsonPrimitive)?.doubleOrNull?.let { return it.toFloat() }
            }
            return null
        }
        // Definite size on one axis: resolved px, or % of a KNOWN parent base
        // (the same base fillMaxWidth(fraction) will see at layout time).
        fun sizeOf(base: Float?, vararg types: String): Float? {
            pxOf(*types)?.let { return it }
            for (t in types) {
                val obj = properties.firstOrNull { it.type == t }?.data as? JsonObject ?: continue
                if ((obj["type"] as? JsonPrimitive)?.contentOrNull == "percentage") {
                    val v = (obj["value"] as? JsonPrimitive)?.doubleOrNull ?: continue
                    return base?.let { (v * it / 100.0).toFloat() }
                }
            }
            return null
        }
        val w = sizeOf(parent.widthPx, "Width", "InlineSize")
        val h = sizeOf(parent.heightPx, "Height", "BlockSize")
        // Border-band widths: raw px reads. A border with style:none would
        // compute to 0 in CSS; fixtures never declare width without style,
        // so the raw read is an accepted approximation (documented).
        val contentW = w?.let {
            it - (pxOf("PaddingLeft", "PaddingInlineStart") ?: 0f) -
                (pxOf("PaddingRight", "PaddingInlineEnd") ?: 0f) -
                (pxOf("BorderLeftWidth") ?: 0f) - (pxOf("BorderRightWidth") ?: 0f)
        }
        val contentH = h?.let {
            it - (pxOf("PaddingTop", "PaddingBlockStart") ?: 0f) -
                (pxOf("PaddingBottom", "PaddingBlockEnd") ?: 0f) -
                (pxOf("BorderTopWidth") ?: 0f) - (pxOf("BorderBottomWidth") ?: 0f)
        }
        return ContainingBlock(widthPx = contentW, heightPx = contentH)
    }

    // ─────────────────────────── internals ───────────────────────────

    /**
     * Quick dynamic-payload scan gating the fast path. True for every
     * carrier shape the converter emits for var()/calc()/relative values:
     * `{"expr":…}` (bare + typed), string `original` containing var(),
     * an `original` OBJECT with no resolved top-level px (relative {v,u}
     * or nested expression), and Generic `rawValue` carrying var().
     */
    internal fun needsResolution(p: IRProperty): Boolean {
        val obj = p.data as? JsonObject ?: return false
        if (obj.containsKey("srgb")) return false // already-resolved color
        if ((obj["expr"] as? JsonPrimitive)?.isString == true) return true
        if (obj["propertyName"] != null &&
            (obj["rawValue"] as? JsonPrimitive)?.contentOrNull?.contains("var(") == true
        ) return true
        return when (val original = obj["original"]) {
            is JsonPrimitive -> original.isString && original.content.contains("var(")
            is JsonObject -> (obj["px"] as? JsonPrimitive)?.doubleOrNull == null
            else -> false
        }
    }

    /**
     * Resolve one property. Returns null when the value is
     * guaranteed-invalid (unset ⇒ drop), the input property unchanged when
     * static or unresolvable-in-context, or a rewritten property carrying
     * the resolved wire shape.
     */
    private fun resolveProperty(
        p: IRProperty,
        scope: CssVariableScope,
        ctx: Context,
        ownFontSizePx: Float
    ): IRProperty? {
        val obj = p.data as? JsonObject ?: return p
        if (obj.containsKey("srgb")) return p

        // Em base: the element's own font-size — except for the FontSize
        // property itself, whose em/% are relative to the INHERITED size.
        val emBase = if (p.type == "FontSize") ctx.parentFontSizePx else ownFontSizePx
        val percentBase = percentBaseFor(p.type, ctx)

        // Generic fall-through carriers: the parser rejected var() so the
        // declaration landed as {propertyName, rawValue, _unmapped}. After
        // substitution we RE-TYPE it onto the real property so the normal
        // appliers pick it up (e.g. border-radius: var(--pad)).
        val propertyName = (obj["propertyName"] as? JsonPrimitive)?.contentOrNull
        val rawValue = (obj["rawValue"] as? JsonPrimitive)?.takeIf { it.isString }?.content
        if (propertyName != null && rawValue != null) {
            if (!rawValue.contains("var(")) return p
            val substituted = CssVariableResolver.resolve(rawValue, scope) ?: return null
            return remapGeneric(propertyName, obj, substituted, emBase, ctx)
        }

        // Expression carriers: bare {"expr":E} (spacing longhands) and
        // typed {"type":"expression","expr":E} (sizing longhands).
        val topExpr = (obj["expr"] as? JsonPrimitive)?.takeIf { it.isString }?.content
        if (topExpr != null) {
            val substituted = substitute(topExpr, scope)
                ?: return invalidWidthFallback(p.type, ctx) // guaranteed-invalid
            val typed = (obj["type"] as? JsonPrimitive)?.contentOrNull == "expression"
            return rewriteLengthCarrier(p.type, substituted, typed, emBase, percentBase, ctx) ?: p
        }

        when (val original = obj["original"]) {
            is JsonPrimitive -> {
                // String originals: color longhands ({"original":"var(--x)"})
                // and preserved-verbatim carriers (border-width var()).
                if (!original.isString || !original.content.contains("var(")) return p
                val substituted = CssVariableResolver.resolve(original.content, scope)
                    ?: return null // guaranteed-invalid ⇒ unset
                // Color first — the dominant carrier for string originals.
                CssVariableResolver.parseColorValue(substituted)?.let { c ->
                    return IRProperty(p.type, buildJsonObject {
                        put("srgb", buildJsonObject {
                            put("r", c.red.toDouble())
                            put("g", c.green.toDouble())
                            put("b", c.blue.toDouble())
                            put("a", c.alpha.toDouble())
                        })
                        put("original", substituted)
                    })
                }
                // Length-ish (e.g. BorderTopWidth carries {"original":var,
                // "px":3-medium-default}): override px, keep the other keys.
                // percentBase=null: no string-original carrier resolves %
                // against the containing block (border-width % is invalid CSS).
                plainToPx(substituted, emBase, percentBase = null, ctx)?.let { px ->
                    return IRProperty(p.type, JsonObject(obj.toMutableMap().apply {
                        put("original", JsonPrimitive(substituted))
                        put("px", JsonPrimitive(px.toDouble()))
                    }))
                }
                // Neither color nor length — keep the property but surface
                // the substituted token stream to downstream keyword readers.
                return IRProperty(p.type, JsonObject(obj.toMutableMap().apply {
                    put("original", JsonPrimitive(substituted))
                }))
            }
            is JsonObject -> {
                // px already resolved by the converter → authoritative.
                if ((obj["px"] as? JsonPrimitive)?.doubleOrNull != null) return p
                // Bare {v,u} PERCENT is only containing-block-relative for
                // the spacing family (CSS 2.1 §8.3/§8.4: padding-% and
                // margin-% resolve against the cb WIDTH) and for FontSize
                // (% of the inherited size). Everything else — notably
                // border-radius % (css-backgrounds-3 §4.2: % of the BOX's
                // own axes, resolved at draw time by the radius applier) —
                // must stay untouched: baseline pin Edge_PercentRadius
                // regressed to a 179px corner when the cb leaked in here.
                val objPercentBase = when {
                    p.type == "FontSize" -> percentBase
                    p.type.startsWith("Padding") || p.type.startsWith("Margin") -> percentBase
                    else -> null
                }
                return when (val o = resolveOriginalObject(original, scope, emBase, objPercentBase, ctx)) {
                    is Outcome.Px -> IRProperty(p.type, JsonObject(obj.toMutableMap().apply {
                        // Add the resolved px NEXT TO the preserved original
                        // — extractLength/extractDp treat top-level px as
                        // canonical for both typed and bare shapes.
                        put("px", JsonPrimitive(o.px.toDouble()))
                    }))
                    Outcome.Invalid -> invalidWidthFallback(p.type, ctx)
                    Outcome.Keep -> p
                }
            }
            else -> return p
        }
    }

    /**
     * The element's own font size in px: the FontSize declaration resolved
     * against the PARENT base (em/% per css-values-4 §5.1.1), falling back
     * to the inherited size itself. Post-merge property lists already
     * contain the parent's RESOLVED px FontSize when the element declared
     * none, so the px branch covers plain inheritance.
     */
    private fun resolveOwnFontSize(
        properties: List<IRProperty>,
        scope: CssVariableScope,
        ctx: Context
    ): Float {
        val obj = properties.firstOrNull { it.type == "FontSize" }?.data as? JsonObject
            ?: return ctx.parentFontSizePx
        (obj["px"] as? JsonPrimitive)?.doubleOrNull?.let { return it.toFloat() }
        val original = obj["original"] as? JsonObject ?: return ctx.parentFontSizePx
        // font-size % resolves against the inherited size, same as em.
        return when (val o = resolveOriginalObject(
            original, scope, ctx.parentFontSizePx, ctx.parentFontSizePx, ctx
        )) {
            is Outcome.Px -> o.px
            else -> ctx.parentFontSizePx
        }
    }

    /**
     * Resolve a nested `original` OBJECT payload:
     *   {v,u}                                   → relative unit conversion
     *   {"type":"expression","expr":E}          → substitute + evaluate
     *   {"type":"length","original":{v,u}}      → FontSize's nested shape
     */
    private fun resolveOriginalObject(
        original: JsonObject,
        scope: CssVariableScope,
        emBase: Float,
        percentBase: Float?,
        ctx: Context
    ): Outcome {
        // Relative {v,u} pair.
        val v = (original["v"] as? JsonPrimitive)?.doubleOrNull
        val u = (original["u"] as? JsonPrimitive)?.contentOrNull
        if (v != null && u != null) {
            val px = convertRelativeToPx(v, u, emBase, percentBase, ctx)
            return if (px != null) Outcome.Px(px) else Outcome.Keep
        }
        // Nested expression (FontSize: {"original":{"type":"expression","expr":…}}).
        val expr = (original["expr"] as? JsonPrimitive)?.takeIf { it.isString }?.content
        if (expr != null) {
            val substituted = substitute(expr, scope) ?: return Outcome.Invalid
            val px = plainToPx(substituted, emBase, percentBase, ctx)
            return if (px != null) Outcome.Px(px) else Outcome.Keep
        }
        // Nested length wrapper (FontSize em: original.type=length + inner {v,u}).
        if ((original["type"] as? JsonPrimitive)?.contentOrNull == "length") {
            (original["original"] as? JsonObject)?.let { inner ->
                return resolveOriginalObject(inner, scope, emBase, percentBase, ctx)
            }
        }
        return Outcome.Keep
    }

    /**
     * Substitute var() references in [value] (identity when none). Null =
     * guaranteed-invalid (missing variable without fallback, or a cycle).
     */
    private fun substitute(value: String, scope: CssVariableScope): String? =
        if (value.contains("var(")) CssVariableResolver.resolve(value, scope) else value

    /**
     * Rewrite a substituted expression carrier back into its property's
     * frozen wire shape. Returns null when the value cannot be resolved in
     * this context (caller keeps the original property — e.g. calc() with
     * % against an unknown containing block).
     */
    private fun rewriteLengthCarrier(
        type: String,
        substituted: String,
        typed: Boolean,
        emBase: Float,
        percentBase: Float?,
        ctx: Context
    ): IRProperty? {
        val s = substituted.trim()
        // calc()/min()/max()/clamp() → CalcExpressionEvaluator with the
        // real context (this is the wave-6 wiring the evaluator was built for).
        if (CalcExpressionEvaluator.hasExpression(s)) {
            val px = CalcExpressionEvaluator
                .evaluate(s, evalContext(emBase, percentBase, ctx))
                ?.toFloat() ?: return null
            return IRProperty(type, lengthJson(typed, px))
        }
        LENGTH_RE.find(s)?.let { m ->
            val num = m.groupValues[1].toDouble()
            val unit = m.groupValues[2].takeIf { it.isNotEmpty() }
            if (unit == "%") {
                // Typed sizing carriers keep the wire's percentage shape —
                // SizingApplier's fillMaxWidth(fraction) resolves it exactly
                // at layout time. Bare spacing carriers need a static base.
                if (typed) return IRProperty(type, buildJsonObject {
                    put("type", "percentage")
                    put("value", num)
                })
                val base = percentBase ?: return null
                return IRProperty(type, lengthJson(typed = false, px = (num * base / 100.0).toFloat()))
            }
            val px = convertRelativeToPx(num, unit ?: "px", emBase, percentBase, ctx)
                ?: return null
            return IRProperty(type, lengthJson(typed, px))
        }
        // Keyword token stream ("auto", "min-content", …) — hand the bare
        // primitive to extractLength, which owns intrinsic keywords.
        return IRProperty(type, JsonPrimitive(s))
    }

    /**
     * A substituted plain value → absolute px, or null when it is not a
     * resolvable length here (keywords, colors, %-without-base).
     */
    private fun plainToPx(
        substituted: String,
        emBase: Float,
        percentBase: Float?,
        ctx: Context
    ): Float? {
        val s = substituted.trim()
        if (CalcExpressionEvaluator.hasExpression(s)) {
            return CalcExpressionEvaluator
                .evaluate(s, evalContext(emBase, percentBase, ctx))
                ?.toFloat()
        }
        val m = LENGTH_RE.find(s) ?: return null
        val num = m.groupValues[1].toDouble()
        val unit = m.groupValues[2].takeIf { it.isNotEmpty() } ?: "px"
        return if (unit == "%") percentBase?.let { (num * it / 100.0).toFloat() }
        else convertRelativeToPx(num, unit, emBase, percentBase, ctx)
    }

    /**
     * Re-type a substituted Generic declaration onto its real property.
     * kebab-case propertyName → the registry's PascalCase type; the value
     * re-parses as a color (srgb shape) or absolute length (bare px shape
     * — the same wire shape those longhands emit when static, e.g.
     * BorderTopLeftRadius {"px":8}). Unparseable values keep the Generic
     * envelope with the substituted rawValue so nothing is silently lost.
     */
    private fun remapGeneric(
        propertyName: String,
        obj: JsonObject,
        substituted: String,
        emBase: Float,
        ctx: Context
    ): IRProperty {
        val pascal = propertyName.split('-').filter { it.isNotEmpty() }
            .joinToString("") { part -> part.replaceFirstChar { it.uppercaseChar() } }
        CssVariableResolver.parseColorValue(substituted)?.let { c ->
            return IRProperty(pascal, buildJsonObject {
                put("srgb", buildJsonObject {
                    put("r", c.red.toDouble())
                    put("g", c.green.toDouble())
                    put("b", c.blue.toDouble())
                    put("a", c.alpha.toDouble())
                })
                put("original", substituted)
            })
        }
        // percentBase deliberately null: Generic fall-throughs are mostly
        // radius-family longhands whose % is box-relative (css-backgrounds-3
        // §4.2), never containing-block-relative — only ABSOLUTE results remap.
        plainToPx(substituted, emBase, percentBase = null, ctx)?.let { px ->
            return IRProperty(pascal, buildJsonObject { put("px", px.toDouble()) })
        }
        return IRProperty("Generic", JsonObject(obj.toMutableMap().apply {
            put("rawValue", JsonPrimitive(substituted))
        }))
    }

    /**
     * Guaranteed-invalid handling for a dropped declaration: normally null
     * (unset ⇒ property absent ⇒ platform default). EXCEPT the block-level
     * `width`/`inline-size` case — see [Context.blockLevelWidthAuto]: the
     * unset width computes to `auto`, and CSS 2.1 §10.3.3 makes block
     * auto-width fill the containing block (pixel-verified: web paints the
     * tokenMath standalone capture as a full-width bar while a plain drop
     * hugged the label on Android — 0.58 → the 100% rewrite mirrors web).
     * Flex/grid contexts keep the true drop (auto is content-based there).
     */
    private fun invalidWidthFallback(type: String, ctx: Context): IRProperty? {
        if (!ctx.blockLevelWidthAuto) return null
        if (type != "Width" && type != "InlineSize") return null
        return IRProperty(type, buildJsonObject {
            put("type", "percentage")
            put("value", 100.0)
        })
    }

    /** The % base for a property type — see HEIGHT_AXIS_TYPES + FontSize note. */
    private fun percentBaseFor(type: String, ctx: Context): Float? = when {
        // font-size: 120% is relative to the inherited font size.
        type == "FontSize" -> ctx.parentFontSizePx
        type in HEIGHT_AXIS_TYPES -> ctx.containingBlockHeightPx
        else -> ctx.containingBlockWidthPx
    }

    /**
     * Build the evaluator's EvalContext from the live channels. The axis
     * base rides containerWidth because the evaluator's %-conversion reads
     * width-first — we pass exactly one base, pre-picked per property axis.
     * Variables are NOT forwarded: substitution already ran with proper
     * §2.3 semantics (the evaluator's internal var handling defaults
     * missing → "0", which would mask guaranteed-invalid).
     */
    private fun evalContext(
        emBase: Float,
        percentBase: Float?,
        ctx: Context
    ): CalcExpressionEvaluator.EvalContext = CalcExpressionEvaluator.EvalContext(
        containerWidth = percentBase,
        containerHeight = null,
        viewportWidth = ctx.viewportWidthPx,
        viewportHeight = ctx.viewportHeightPx,
        baseFontSize = emBase,
        rootFontSize = ctx.rootFontSizePx,
        variables = emptyMap()
    )

    /** Emit the length wire shape matching the carrier flavor. */
    private fun lengthJson(typed: Boolean, px: Float): JsonObject = buildJsonObject {
        if (typed) put("type", "length")
        put("px", px.toDouble())
    }

    /**
     * Convert a value+unit to px with the live context. Null for units that
     * need bases we don't have (ch/ex/…, or % without a containing block).
     */
    private fun convertRelativeToPx(
        v: Double,
        unit: String,
        emBase: Float,
        percentBase: Float?,
        ctx: Context
    ): Float? = when (unit.uppercase()) {
        "PX", "DP" -> v.toFloat()
        "PT" -> (v * 4.0 / 3.0).toFloat() // css-values-4 §6.2: 1pt = 4/3 px
        "EM" -> (v * emBase).toFloat()
        "REM" -> (v * ctx.rootFontSizePx).toFloat()
        "%", "PERCENT" -> percentBase?.let { (v * it / 100.0).toFloat() }
        "VW", "SVW", "LVW", "DVW" -> (v * ctx.viewportWidthPx / 100.0).toFloat()
        "VH", "SVH", "LVH", "DVH" -> (v * ctx.viewportHeightPx / 100.0).toFloat()
        "VMIN", "SVMIN", "LVMIN", "DVMIN" ->
            (v * minOf(ctx.viewportWidthPx, ctx.viewportHeightPx) / 100.0).toFloat()
        "VMAX", "SVMAX", "LVMAX", "DVMAX" ->
            (v * maxOf(ctx.viewportWidthPx, ctx.viewportHeightPx) / 100.0).toFloat()
        else -> null // ch/ex/lh/cq* — no honest base available yet
    }

    /** Plain `<number><unit>` token (units this resolver can convert). */
    private val LENGTH_RE = Regex(
        """^(-?\d+(?:\.\d+)?)(px|pt|em|rem|%|vw|vh|vmin|vmax|dp)?$""",
        RegexOption.IGNORE_CASE
    )
}
