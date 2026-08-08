package com.styleconverter.runtime.core.renderer

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.graphics.Color
// Wave 30 (lane 3, fix B6): the marker SHAPE needs the glyph's resolved
// ink at composition time (a draw scope cannot read a CompositionLocal),
// and `Color.takeOrElse` is a top-level extension — importable only by name.
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.styleconverter.runtime.core.ir.IRComponent
import com.styleconverter.runtime.layout.grid.GridRenderer
import com.styleconverter.runtime.core.ir.IRProperty
import com.styleconverter.runtime.core.types.ValueExtractors
import com.styleconverter.runtime.StyleApplier
import com.styleconverter.runtime.scrolling.OverflowExtractor
// Wave 25 CAL-RC4 — the unclamped §9.7 main-size pins. Imported (not
// fully-qualified like the rest of the flexbox package) because Kotlin has
// no call syntax for a top-level extension function outside its package.
import com.styleconverter.runtime.layout.flexbox.flexMainWidthPin
import com.styleconverter.runtime.layout.flexbox.flexMainHeightPin
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
// Wave 28 (lane MC): the marker-row geometry decisions — twin of iOS's
// StyleEngine/lists/ListMarkerRow.swift. `markerBaselineClaim` is a
// top-level RowScope extension, so it must be imported by name rather
// than qualified at the call site.
import com.styleconverter.runtime.lists.ListMarkerLineBox
import com.styleconverter.runtime.lists.ListMarkerRow
import com.styleconverter.runtime.lists.markerBaselineClaim
import com.styleconverter.runtime.lists.ListMarkerTextStyle
import com.styleconverter.runtime.lists.ListStyleExtractor
import com.styleconverter.runtime.lists.ListStyleApplier as StyleListApplier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
// Draw-time fractional translation for the glyph placement compensation
// (center-align snap + sub-natural line-height) — graphicsLayer does not
// affect layout, so box geometry and the committed baselines stay put.
import androidx.compose.ui.graphics.graphicsLayer
import com.styleconverter.runtime.typography.TextStyleApplier
import com.styleconverter.runtime.typography.TypographyExtractor
import com.styleconverter.runtime.typography.FontVariantApplier
import com.styleconverter.runtime.typography.FontVariantCaps
import com.styleconverter.runtime.typography.TextEmphasisPosition
import com.styleconverter.runtime.typography.TextEmphasisStyle
import com.styleconverter.runtime.animations.AnimationExtractor
import com.styleconverter.runtime.animations.animatedModifier
import com.styleconverter.runtime.container.ContainerQueryApplier
import com.styleconverter.runtime.container.ContainerQueryExtractor
import com.styleconverter.runtime.columns.MultiColumnApplier
import com.styleconverter.runtime.columns.MultiColumnExtractor
// Wave-24 gap-decorations draw hook (css-gap-decorations-1) — see the two
// blocks marked "GAP-DECORATIONS" below; both are inert without the props.
import com.styleconverter.runtime.columns.gapDecorations
import com.styleconverter.runtime.columns.gapItemProbe
import com.styleconverter.runtime.columns.rememberGapDecorationSink
import com.styleconverter.runtime.table.TableApplier
import com.styleconverter.runtime.table.TableApplier.TableCell
import com.styleconverter.runtime.table.TableExtractor
import com.styleconverter.runtime.borders.image.BorderImageApplier
import com.styleconverter.runtime.borders.image.BorderImageExtractor
import com.styleconverter.runtime.interactions.forms.FormStylingApplier
import com.styleconverter.runtime.interactions.forms.FormStylingExtractor
import com.styleconverter.runtime.content.ContentApplier
import com.styleconverter.runtime.content.ContentExtractor
import com.styleconverter.runtime.content.CounterStateProvider

/**
 * SDUI Component Renderer.
 *
 * Renders IRComponents as Compose UI at runtime.
 *
 * ## Rendering Strategy
 * 1. Extract display type to determine container (Box, Row, Column)
 * 2. Extract flex properties for layout configuration
 * 3. Apply all modifier properties via PropertyApplier
 * 4. Apply text styles via TextStyleApplier
 * 5. Render placeholder content or children
 */
object ComponentRenderer {

    /**
     * Wave 33 (lane C) — the draw order of a `z-index: auto` POSITIONED
     * descendant, CSS 2.1 Appendix E step 8.
     *
     * Fractional on purpose: it must sit strictly ABOVE everything Compose
     * leaves at the implicit 0 (the in-flow, non-positioned siblings of
     * Appendix E step 4, and the negative-z backdrop layer) and strictly
     * BELOW any author-declared positive `z-index` (step 9), which
     * PositionApplier applies as the integer the author wrote. Any value
     * in (0, 1) satisfies both; 0.5 is the midpoint.
     *
     * See [absposPaintOrder] for where it applies and why.
     */
    private const val AUTO_Z_POSITIONED_DESCENDANT = 0.5f

    /**
     * One-shot gate for the multi-line sub-natural line-height log — the
     * honest-limitation notice (line boxes stay uncompressed; only the
     * glyph-run PLACEMENT is compensated) must appear once per process,
     * not once per frame: the emitting site is a graphicsLayer block that
     * re-runs on every layout-state change. See the glyph placement
     * compensation in the placeholder text path.
     */
    private val subNaturalMultiLineLogged =
        java.util.concurrent.atomic.AtomicBoolean(false)

    /**
     * One-shot gate for the decoration-style solid fallback log (wave
     * 21, lane TEXTDECOR): `text-decoration-style: double | wavy` have
     * no op emitters yet and paint solid — the repo's no-silent-
     * fallthrough rule requires the loss to be surfaced, once per
     * process (the emitting site recomposes per frame).
     */
    private val decorationStyleFallbackLogged =
        java.util.concurrent.atomic.AtomicBoolean(false)

    /**
     * CSS-inherited properties (css-cascade-4 / per-property "Inherited:
     * yes" tables) that our IR carries. These — and ONLY these — flow from
     * parent to child when the child doesn't declare them itself.
     * Layout/box properties (Width, Padding, Background*, per-side
     * Border*) are deliberately absent: they never inherit in CSS.
     * TextDecorationLine is also absent — decoration PROPAGATES to inline
     * descendants rather than inheriting, and our leading-text sibling
     * already handles the visible case.
     *
     * Wave 9 (#37): the original 14-property TEXT channel grows to the
     * full inherited set the IR carries, and `Color` — deliberately
     * excluded since wave 1 — now INHERITS. currentColor consumers
     * (BorderSideExtractor / OutlineExtractor / text-emphasis, which read
     * "Color" from the MERGED list) therefore resolve against the
     * ancestor chain exactly like the browser. Leaf PLACEHOLDER glyphs
     * still ignore an inherited-only Color: the web reference placeholder
     * span (`PlaceholderContent` in ComponentRenderer.tsx) always sets an
     * explicit own-`color`-or-contrast-pick that beats DOM inheritance
     * (pixel-sampled on IT_Family: child text paints rgba(237,237,237,.7),
     * NOT the parent's #111). See LocalColorIsInheritedOnly for the gate.
     */
    internal val INHERITED_PROPERTY_TYPES: Set<String> = setOf(
        // The wave-1 text channel (css-fonts-4 / css-text-4 / css-writing-modes).
        "FontFamily", "FontSize", "FontWeight", "FontStyle", "FontStretch",
        "LetterSpacing", "LineHeight", "WordSpacing",
        "TextAlign", "TextTransform", "TextIndent",
        "WhiteSpace", "TabSize", "Direction",
        // css-writing-modes-4 §3.1: writing-mode INHERITS ("Inherited: yes").
        // Wave 12 honesty fix: without it the wave-10 multicol
        // vertical-writing bail could NEVER fire — the css-break
        // background-image-001/002 fixtures declare `writing-mode:
        // vertical-rl` on the PARENT .container while the multicol children
        // declare only column properties, so MultiColumnExtractor's
        // WritingMode read (which runs on THIS merged list via
        // mergedComponent) always saw the horizontal default and fragmented
        // along the wrong axis with no breadcrumb. Threading it here makes
        // the documented bail + logFragmentationFallbackOnce actually fire,
        // and gives descendant placeholder glyphs the inherited vertical
        // flow exactly like the browser's cascade.
        "WritingMode",
        // css-color-4 §7: `color` inherits; the currentColor chain hangs
        // off the inherited value (placeholder gate documented above).
        "Color",
        // CSS 2.1 §11.2: visibility inherits (a hidden parent hides
        // children unless a child redeclares `visible`).
        "Visibility",
        // css-ui-4 §8.1: cursor inherits. No visual analogue in a static
        // native capture (no-op applier) but carried so the wire value
        // survives the cascade honestly.
        "Cursor",
        // css-lists-3 §4: list-style-* inherit from the list container to
        // every item ("ListStyle" covers a wire doc carrying the
        // unexpanded shorthand — ListStyleExtractor accepts it too).
        "ListStyleType", "ListStylePosition", "ListStyleImage", "ListStyle",
        // css-content-3 §2: quotes inherit (open/close pairs for q-elements).
        "Quotes",
        // css-text-decor-3 §4: text-shadow inherits — leaf placeholder
        // glyphs DO show it on web (the span never resets text-shadow).
        "TextShadow",
        // css-text-4 §5: line-breaking controls all inherit. WordWrap is
        // the legacy alias the parser may emit for `word-wrap`.
        "OverflowWrap", "WordWrap", "WordBreak", "Hyphens",
        // css-text-decor-3 §3: all three text-emphasis longhands inherit
        // (emphasis-color defaults to currentColor — the inherited Color
        // above keeps that chain honest).
        "TextEmphasisStyle", "TextEmphasisColor", "TextEmphasisPosition",
        // css-ruby-1 §4: ruby annotation layout properties inherit.
        "RubyAlign", "RubyPosition", "RubyMerge", "RubyOverhang",
        // CSS 2.1 §17 table model: caption side, border model, spacing
        // and empty-cell painting inherit (table-scoped).
        "CaptionSide", "BorderCollapse", "BorderSpacing", "EmptyCells",
        // CSS 2.1 §13.3.3 fragmentation: print-only, no-op appliers on
        // the mobile runtimes, but the values flow for honest coverage.
        "Orphans", "Widows",
        // css-ui-4 §7.1: accent-color is Inherited: yes — a parent's
        // declaration must reach the form-control descendants (wave-20
        // lane W2: accent-color-parent-currentcolor puts it on the DIV
        // and asserts the checkbox inside turns red). The widget painter
        // reads it off the merged list (UAWidgetsResolve.accentFor).
        "AccentColor"
    )

    /**
     * The inherited-property channel. Compose has no CSS cascade, so we
     * thread the parent's inheritable text declarations down through the
     * composition. RenderComponent merges these UNDER the child's own
     * declarations (own always wins — cascade specificity is irrelevant
     * inside one element) and re-provides the merged set for grandchildren,
     * which reproduces the transitive inheritance chain.
     *
     * Why: the inheritance-typography tree fixtures set font-size /
     * font-family / font-weight / letter-spacing / text-align on the PARENT
     * and expect the child `_text` nodes to render with them, exactly as
     * the browser does. Without this channel every child fell back to the
     * placeholder defaults (Inter 16sp, left) — IT_* parents sat at
     * SSIM 0.784 against web.
     */
    internal val LocalInheritedProperties =
        androidx.compose.runtime.compositionLocalOf<List<IRProperty>> { emptyList() }

    /**
     * True while an ancestor container (flex row/column, grid cell) already
     * consumed this component's self-alignment properties. css-align-3 §6:
     * `justify-self` applies to BLOCK-LEVEL boxes and grid items — flex
     * items ignore it, and our GridRenderer implements it as cell
     * contentAlignment. The block-level branch below (RenderComponent's
     * fillMaxWidth alignment wrapper) must therefore stay OFF inside those
     * containers, or grid cells would double-align and flex items would
     * wrongly honor a property CSS says to ignore.
     */
    internal val LocalSelfAlignmentHandled =
        androidx.compose.runtime.compositionLocalOf { false }

    /**
     * True when the nearest RenderComponent's merged list carries a Color
     * that arrived ONLY through the inheritance channel (no own
     * declaration). PlaceholderContent reads it to keep LEAF placeholder
     * glyphs on the web-parity contrast pick: the web placeholder span
     * always sets an explicit color (own-declared or bg-luminance pick),
     * so DOM inheritance never reaches leaf placeholder glyphs on the
     * reference render. Leading `_text` siblings and list markers DO show
     * the inherited color (web renders those through a plain inheriting
     * <span>) — their call sites re-extract from the merged list.
     */
    internal val LocalColorIsInheritedOnly =
        androidx.compose.runtime.compositionLocalOf { false }

    /**
     * CSS2 §8.3.1 collapse plan for the CURRENT block container's children,
     * provided by the block branch in RenderComponentContent and consumed by
     * RenderContent's default child loop (index-aligned with the children
     * list). Null everywhere else — flex/grid/table/inline paths and the
     * absolute-overlay branch never collapse (the plan builder gates them
     * out structurally). See BlockMarginCollapse for the mechanism.
     */
    internal val LocalBlockCollapsePlan =
        androidx.compose.runtime.compositionLocalOf<com.styleconverter.runtime.spacing.BlockCollapsePlan?> { null }

    /**
     * Plan-or-fallback result for [blockCollapsePlanFor]: [plan] null means
     * "keep the existing stacking behavior", with [fallbackReason] non-null
     * exactly when an IN-SCOPE-looking container was skipped for a value
     * flavor we don't emulate (house rule: no silent fallthroughs — the
     * composable call site logs it once). Reason stays null for the
     * structurally out-of-scope cases (no children / no margins / list or
     * relative parents) where silence is correct.
     */
    internal data class CollapsePlanResult(
        val plan: com.styleconverter.runtime.spacing.BlockCollapsePlan?,
        val fallbackReason: String?,
    )

    /**
     * Wave 26 (lane RES residual 3a) — the HOIST BAND one composed ROOT will
     * emit, exposed to the harness so its root-stack fold can own that
     * spacing instead of letting it stack on top (see
     * [com.styleconverter.runtime.spacing.BlockMarginCollapse.LocalHoistBandSuppressedFor]
     * for the full double-count argument).
     *
     * PUBLIC because the Android harness is a separate Gradle module and
     * cannot see [blockCollapsePlanFor]'s `internal` visibility; a thin
     * accessor keeps the plan builder itself unexported. Returns (0, 0)
     * whenever no plan exists — a childless root, a bailed container, or a
     * closed edge gate — which is exactly "this root emits no band", so the
     * caller needs no null handling.
     *
     * ## Why this reproduces the renderer's PRE-CONDITIONS, not just its plan
     * The harness SUPPRESSES the renderer's band unconditionally for every
     * composed root, so this accessor must return EXACTLY what the renderer
     * would have painted — under-reporting deletes real spacing just as
     * over-reporting adds phantom spacing. [blockCollapsePlanFor] alone is
     * not that number: the renderer only reaches it through two gates the
     * builder itself does not carry —
     *   1. `ContentsUnboxing.resolve`, which splices `display: contents`
     *      children out before the plan sees the child list, and
     *   2. `displayConfig.type == DisplayType.BLOCK` — §8.3.1 collapsing is
     *      block-flow only, so a `display: flex | grid | inline-block |
     *      table` root, and a `column-count`/`column-width` root (which
     *      extractDisplayConfig maps to MULTI_COLUMN), emit NO band at all.
     * Without gate 2 a `display:flex` root of `<p>` children reported
     * (16, 16) here while the renderer painted nothing, and the harness fold
     * added 16px above and below it. The SwiftUI twin has never had this
     * hole — its `MarginCollapsePlanner.containerPlan` carries the
     * block-only guard INSIDE the planner (GATE 1), so both consumers there
     * read one gated number; this is the Compose alignment.
     *
     * KNOWN residual (recorded, not silent): the renderer derives its
     * `displayConfig` from the BUCKET-RESOLVED property list, while a
     * harness-side static fold can only see the base list. A selector/media
     * bucket that re-declares `Display` on a composed ROOT would therefore
     * still diverge — `Display` is not in [gateAffectingParentBucketType]'s
     * bail set. No corpus fixture declares a bucket `Display` on a root;
     * closing it needs the active-bucket set threaded to the harness fold.
     *
     * @param component the composed ROOT component.
     * @param uaBlockMargins the caller's WPT-capture flag, threaded straight
     *   into the plan builder so the band the harness folds is the SAME
     *   number the renderer would have painted (one decision, two consumers).
     */
    fun composedRootHoistBand(
        component: IRComponent,
        uaBlockMargins: Boolean,
    ): com.styleconverter.runtime.spacing.CollapsedMargin {
        val none = com.styleconverter.runtime.spacing.CollapsedMargin(0f, 0f)
        // Gate 1 — the renderer plans over the UNBOXED component (RenderComponent
        // resolves `display: contents` before anything reads the child list), so
        // planning over the raw one would see a different first/last child.
        // Identity for the whole contents-free corpus.
        val resolved = ContentsUnboxing.resolve(component)
        // Gate 2 — block flow only, the renderer's own pre-condition at the
        // block branch (`displayConfig.type == DisplayType.BLOCK`). Read through
        // the SAME extractor so the two can never disagree about a keyword.
        val display = try {
            extractDisplayConfig(resolved.properties).type
        } catch (e: Exception) {
            // extractDisplayConfig's own catch-all default (see the renderer).
            DisplayType.BLOCK
        }
        if (display != DisplayType.BLOCK) return none
        // Same builder the block branch runs — never a re-derivation.
        val plan = blockCollapsePlanFor(resolved, uaBlockMargins).plan
            ?: return none
        // The band is the plan's parent-edge hoist, top and bottom.
        return com.styleconverter.runtime.spacing.CollapsedMargin(
            topPx = plan.hoistTopPx,
            bottomPx = plan.hoistBottomPx,
        )
    }

    /**
     * Build the §8.3.1 collapse plan for a block container, or decide to
     * fall back. Pure over the IR (JVM-pinnable). Implements the UNIFIED
     * collapse gate contract's container-level bails B1-B10 (shared
     * byte-for-byte with the iOS runtime — divergence between the two
     * implementations is the bug class this contract exists to kill).
     * Scope gates, in order:
     *  - children must exist, and the container must not also carry leading
     *    `_text` (the text renders as an extra first sibling the plan's
     *    index-aligned margins cannot describe);
     *  - `position: relative` parents render children as an absolute-overlay
     *    Box (RenderContent's positioned branch), not a block Column;
     *  - <ol>/<ul> parents route children through the list-marker Row path;
     *  - B6: parent selector/media buckets declaring padding/border/height/
     *    overflow/position longhands bail — an interactive/breakpoint
     *    restyle could flip a hoist gate the static plan already baked;
     *  - B1/B2: display:none children (their margins must not fold into
     *    visible siblings) and inline-level children (§8.3.1 collapses
     *    BLOCK-level margins only) bail;
     *  - B3/B4: every child must be in-flow (§8.3.1 collapses margins "of
     *    boxes in the normal flow"): absolute/fixed or floated children
     *    bail the whole container. For abspos this is DELIBERATE
     *    conservatism: a browser would skip the box and collapse the rest,
     *    but filtering it would break the index-map alignment with the
     *    render loops — BOTH natives bail instead (contract B4);
     *  - B5: children whose selector/media buckets declare a margin
     *    longhand bail (the bucket could restyle the very margin the plan
     *    overrides);
     *  - B9: a self-collapsing candidate child (no _text, no children, no
     *    explicit height/min-height, BOTH vertical margins declared with
     *    either positive) bails — §8.3.1 collapses its own top+bottom
     *    margins with BOTH neighbours into one n-ary max the pairwise
     *    fold cannot reproduce;
     *  - B10: a FIRST or LAST child that is itself an eligible unpadded/
     *    unbordered block container with children bails — the grandchild's
     *    edge margin would collapse THROUGH two levels into one n-ary max;
     *    the single-level emulation is pinned conservative instead;
     *  - B7/B8: every child's block-axis margins must be plain non-negative
     *    px (auto/negative/relative are outside the emulation — see
     *    BlockMarginCollapse.blockMarginsOrNull), as must the parent's own;
     *  - B11 (wave 25, lane UAM — only when [uaBlockMargins] is on): ANY
     *    child that is itself a hoisting block container bails, not just
     *    the first/last of B10. With UA defaults injected, nearly every
     *    prose child carries a block margin, so an INTERIOR nested
     *    container's hoisted band would compound with the sibling gap the
     *    parent's own fold already emits (the browser resolves both into
     *    ONE n-ary max — §8.3.1 adjoining chains are transitive). Bailing
     *    is the same pinned conservatism B10 already applies at the edges;
     *    with UA injection OFF the check is skipped entirely, so every
     *    declared-margin plan (and the 327 corpus) is byte-identical.
     *  - at least one collapsible margin must be non-zero, so margin-less
     *    corpora build no plan and render byte-identically to the frozen
     *    baseline.
     *
     * @param uaBlockMargins wave 25 (lane UAM / BD-RC3): fold each child's
     *   UA DEFAULT block margins (p / h1-h6 / ul / ol / blockquote / pre /
     *   figure — see spacing/UaBlockChildMargins.kt) into the plan on every
     *   edge the IR leaves undeclared, so a `<p>` nested inside another
     *   block gets the 1em the browser-ref's UA sheet gives it. The
     *   renderer passes its ambient `LocalWptCaptureMode`; the default
     *   FALSE is the dark-stage identity branch that keeps the 327
     *   committed baselines (and every pre-wave-25 pin) byte-identical.
     */
    internal fun blockCollapsePlanFor(
        component: IRComponent,
        uaBlockMargins: Boolean = false,
    ): CollapsePlanResult {
        // Structural silence cases first (correct to skip, nothing to log).
        val children = component.children
        if (children.isNullOrEmpty()) return CollapsePlanResult(null, null)
        if (!component._text.isNullOrEmpty()) return CollapsePlanResult(null, null)
        if (extractPositionType(component.properties) == PositionType.RELATIVE) {
            return CollapsePlanResult(null, null)
        }
        val tag = component._tag?.lowercase()
        // List containers bail: their children render through
        // RenderListItemMarker, which wraps each item in a marker Row and
        // has nowhere to hand a collapsed block margin. Wave 24 (lane LF)
        // widened marker synthesis from {ol,ul} to the full UA list-container
        // set {ol,ul,menu,dir} (HTML §15.3.9), so this gate MUST read the
        // same predicate or a <menu> would build a plan whose per-child
        // margins the marker branch silently drops. Routing both through
        // uaMarkerDefault makes the two sets un-driftable.
        if (ListStyleExtractor.uaMarkerDefault(tag) != null) return CollapsePlanResult(null, null)
        // B6: the plan is computed ONCE from base properties, but selector
        // (hover/focus) and media buckets re-style live — a bucket that can
        // touch the parent's padding/border/height/overflow/position could
        // flip a hoist gate after the fact. Bail so the legacy per-frame
        // stacking (which tracks the buckets) stays authoritative.
        if ((component.selectors.asSequence().flatMap { it.properties.asSequence() } +
                component.media.asSequence().flatMap { it.properties.asSequence() })
                .any { gateAffectingParentBucketType(it.type) }
        ) {
            return CollapsePlanResult(null, "parent bucket declares gate-affecting longhand")
        }
        // Per-child bails B1-B5 + B9 (container-level: ONE ineligible child
        // kills the whole plan — partial collapse would render geometry
        // neither engine produces).
        for (child in children) {
            // Raw display keyword (underscore- and hyphen-form tolerant,
            // matching extractDisplayConfig's keyword table).
            val display = child.properties.firstOrNull { it.type == "Display" }
                ?.data?.let { ValueExtractors.extractKeyword(it)?.uppercase()?.replace('-', '_') }
            // B1: a display:none child generates NO box (css-display-3 §2.4)
            // — its margins must not participate at all. The legacy path
            // renders it size-0 in place; folding its margins into visible
            // siblings would be WORSE than legacy, so bail.
            if (display == "NONE") {
                return CollapsePlanResult(null, "display:none child")
            }
            // B2: inline-level boxes don't produce block-level margins —
            // §8.3.1 collapses "adjoining margins of block-level boxes"
            // only; an inline/inline-block/inline-flex/inline-grid sibling
            // also changes the whole line-layout the plan can't model.
            if (display in INLINE_LEVEL_DISPLAY_KEYWORDS) {
                return CollapsePlanResult(null, "inline-level child")
            }
            // B4: out-of-flow boxes don't participate in block flow (§8.3.1
            // is normal-flow only). Browsers would skip the box and still
            // collapse the remaining siblings, but filtering it here would
            // desync the plan's index map from the render loops — BOTH
            // natives bail instead (contract-pinned conservatism).
            val pos = extractPositionType(child.properties)
            if (pos == PositionType.ABSOLUTE || pos == PositionType.FIXED) {
                return CollapsePlanResult(null, "out-of-flow child")
            }
            // B3: a float's margins never collapse (§8.3.1: "margins of
            // floating boxes never collapse") — Float keyword != NONE takes
            // the box out of the collapsing flow.
            val floatKeyword = child.properties.firstOrNull { it.type == "Float" }
                ?.data?.let { ValueExtractors.extractKeyword(it)?.uppercase() }
            if (floatKeyword != null && floatKeyword != "NONE") {
                return CollapsePlanResult(null, "floated child")
            }
            // B5: a selector/media bucket that can re-declare any margin
            // longhand invalidates the plan's DECLARED-margin math the
            // moment the bucket applies — bail. MarginTrim is screened out:
            // it is a trimming switch (css-box-4 §4), not a margin longhand.
            if ((child.selectors.asSequence().flatMap { it.properties.asSequence() } +
                    child.media.asSequence().flatMap { it.properties.asSequence() })
                    .any { it.type.startsWith("Margin") && it.type != "MarginTrim" }
            ) {
                return CollapsePlanResult(null, "bucket-declared child margin")
            }
            // B9: a self-collapsing candidate — no content (_text/children),
            // no explicit block-size floor, yet BOTH vertical margins
            // declared (either positive). §8.3.1 collapses such a box's own
            // top and bottom margins with each other AND both neighbours
            // into one n-ary max; the pairwise fold below cannot reproduce
            // that, so bail (pin S12).
            if (isSelfCollapsingCandidate(child)) {
                return CollapsePlanResult(null, "self-collapsing child")
            }
        }
        // B10: nested-hoist chain — a FIRST/LAST child that is itself an
        // eligible (unpadded/unbordered, block-flow) container with children
        // would let a grandchild edge margin collapse THROUGH two levels
        // (§8.3.1 adjoining chains are transitive); the single-level
        // emulation composes nested max()s instead of one n-ary max, so
        // bail — pinned conservative on BOTH natives.
        if (isNestedHoistChainChild(children.first(), edgeIsTop = true) ||
            isNestedHoistChainChild(children.last(), edgeIsTop = false)
        ) {
            return CollapsePlanResult(null, "nested-hoist chain")
        }
        // B11 (lane UAM): with UA defaults injected, an INTERIOR nested
        // hoisting container is as dangerous as an edge one — its own
        // hoisted band would stack on top of the gap this fold emits,
        // where the browser resolves the whole adjoining chain into one
        // max(). Skipped entirely when UA injection is off, so no
        // pre-wave-25 plan changes shape.
        if (uaBlockMargins && children.any {
                isNestedHoistChainChild(it, edgeIsTop = true) ||
                    isNestedHoistChainChild(it, edgeIsTop = false)
            }
        ) {
            return CollapsePlanResult(null, "nested-hoist chain (ua)")
        }
        // Per-child block-axis margins — all must be plain positive px,
        // then MERGED with the child's UA defaults (lane UAM): author
        // declarations win per edge, undeclared edges take the UA value.
        // With [uaBlockMargins] false the merge is the identity.
        val childMargins = children.map { child ->
            val declared = com.styleconverter.runtime.spacing.BlockMarginCollapse
                .blockMarginsOrNull(
                    com.styleconverter.runtime.spacing.SpacingExtractor
                        .extractMarginConfig(child.properties.map { it.type to it.data })
                ) ?: return CollapsePlanResult(null, "auto/negative/relative child margin")
            com.styleconverter.runtime.spacing.uaChildBlockEdges(
                sourceTag = child._tag,
                propertyTypes = child.properties.map { it.type },
                declared = declared,
                enabled = uaBlockMargins,
            )
        }
        // No collapsible margin anywhere → no plan (baseline-identity path).
        if (childMargins.all { it.topPx == 0f && it.bottomPx == 0f }) {
            return CollapsePlanResult(null, null)
        }
        // Parent's own block-axis margins feed the hoist max() composition;
        // out-of-scope parent flavors bail (the hoist math would be wrong).
        val parentPairs = component.properties.map { it.type to it.data }
        val parentDeclared = com.styleconverter.runtime.spacing.BlockMarginCollapse
            .blockMarginsOrNull(
                com.styleconverter.runtime.spacing.SpacingExtractor
                    .extractMarginConfig(parentPairs)
            ) ?: return CollapsePlanResult(null, "auto/negative/relative parent margin")
        // Lane UAM: the parent's OWN margin must carry its UA default too,
        // or the hoist band double-counts. A `<blockquote>` parent's 16px
        // top is painted one level up (its own parent's plan, or the
        // composed root stack), so the band it adds for a `<p>` first
        // child must be max(16, 16) − 16 = 0, not the full 16.
        val parentOwn = com.styleconverter.runtime.spacing.uaChildBlockEdges(
            sourceTag = component._tag,
            propertyTypes = component.properties.map { it.type },
            declared = parentDeclared,
            enabled = uaBlockMargins,
        )
        // Edge gates (§8.3.1 adjoining conditions: padding/border/BFC/height).
        val gates = com.styleconverter.runtime.spacing.BlockMarginCollapse
            .hoistGates(parentPairs)
        // Pure collapse math — max rule between siblings + edge hoisting.
        return CollapsePlanResult(
            com.styleconverter.runtime.spacing.BlockMarginCollapse
                .computePlan(childMargins, parentOwn, gates),
            null
        )
    }

    // Inline-level display keywords for bail B2 (css-display-3 §2.1's
    // inline-level outer display values), normalized to underscore form.
    private val INLINE_LEVEL_DISPLAY_KEYWORDS =
        setOf("INLINE", "INLINE_BLOCK", "INLINE_FLEX", "INLINE_GRID")

    /**
     * Bail B6 predicate: a parent bucket (selector/media) property type
     * that could flip a hoist gate when the bucket applies. Families match
     * the gate inputs in BlockMarginCollapse.hoistGates: padding (G1),
     * border (G2 — the whole Border* namespace, conservatively including
     * radius/image since a bucket restyle needs no precision), overflow
     * (G3 — OverflowWrap/OverflowAnchor screened out: text wrapping and
     * scroll anchoring never establish a BFC), position (G4), and the
     * block-size family (G5 — Max* included here even though it doesn't
     * pin the STATIC gate, because a bucket could swap it for a Height).
     */
    private fun gateAffectingParentBucketType(type: String): Boolean =
        type.startsWith("Padding") ||
            type.startsWith("Border") ||
            (type.startsWith("Overflow") &&
                type != "OverflowWrap" && type != "OverflowAnchor") ||
            type == "Position" ||
            type in setOf(
                "Height", "BlockSize", "MinHeight", "MinBlockSize",
                "MaxHeight", "MaxBlockSize", "AspectRatio",
            )

    // Block-size floors that stop a box from self-collapsing (bail B9):
    // CSS2 §8.3.1 requires "min-height of zero, and zero or auto computed
    // height" for a box's own top/bottom margins to be adjoining — any of
    // these declared means the box has (or floors) a height.
    private val SELF_COLLAPSE_HEIGHT_TYPES =
        setOf("Height", "BlockSize", "MinHeight", "MinBlockSize")

    // Declared vertical-margin type sets for B9's "declares BOTH vertical
    // margins" test (logical block aliases map to top/bottom in the
    // horizontal-tb engine, css-logical-1 §4.2).
    private val TOP_MARGIN_TYPES = setOf("MarginTop", "MarginBlockStart")
    private val BOTTOM_MARGIN_TYPES = setOf("MarginBottom", "MarginBlockEnd")

    /**
     * Bail B9 predicate: a child whose own top and bottom margins would be
     * adjoining (§8.3.1 self-collapsing box) — no `_text`, no children, no
     * explicit height/min-height, and BOTH vertical margins declared with
     * either one positive. The pairwise fold applies prev.bottom vs own.top
     * per gap; a self-collapsing box needs max(prevBottom, top, bottom,
     * nextTop) across ONE combined gap instead — unreproducible, so bail.
     */
    private fun isSelfCollapsingCandidate(child: IRComponent): Boolean {
        // Any content or a block-size floor → the box has extent, its own
        // margins never touch each other, the pairwise fold is exact.
        if (!child._text.isNullOrEmpty()) return false
        if (!child.children.isNullOrEmpty()) return false
        if (child.properties.any { it.type in SELF_COLLAPSE_HEIGHT_TYPES }) return false
        // BOTH vertical margins must be DECLARED (an undeclared side is the
        // initial 0 — a zero-width adjoining margin the fold handles fine).
        val top = child.properties.firstOrNull { it.type in TOP_MARGIN_TYPES }
            ?: return false
        val bottom = child.properties.firstOrNull { it.type in BOTTOM_MARGIN_TYPES }
            ?: return false
        // Either margin positive triggers the bail: two declared ZEROS
        // self-collapse harmlessly (the combined gap is still the plain
        // pairwise max). Unresolvable flavors fall to B7/B8 later anyway;
        // read via the spacing extractor to share one wire decoding.
        val margins = com.styleconverter.runtime.spacing.BlockMarginCollapse
            .blockMarginsOrNull(
                com.styleconverter.runtime.spacing.SpacingExtractor
                    .extractMarginConfig(listOf(top.type to top.data, bottom.type to bottom.data))
            ) ?: return true // non-static declared pair — conservative bail
        return margins.topPx > 0f || margins.bottomPx > 0f
    }

    /**
     * Bail B10 predicate for one edge child: is it an eligible block
     * container (block-flow display, children present, no text sibling)
     * whose OWN hoist gate on the touching edge is open (unpadded/
     * unbordered per hoistGates)? If so, its grandchild edge margin would
     * chain-collapse through it — beyond the single-level emulation.
     */
    private fun isNestedHoistChainChild(child: IRComponent, edgeIsTop: Boolean): Boolean {
        // Leaf children (or text-first containers) cannot hoist anything —
        // their own plan builder structurally skips them.
        if (child.children.isNullOrEmpty()) return false
        if (!child._text.isNullOrEmpty()) return false
        // Only block-flow containers hoist: an explicit non-block display
        // (flex/grid/table/…) establishes an independent formatting context
        // whose margins never collapse through (css-display-3 §2.1).
        val display = child.properties.firstOrNull { it.type == "Display" }
            ?.data?.let { ValueExtractors.extractKeyword(it)?.uppercase()?.replace('-', '_') }
        if (display != null && display != "BLOCK") return false
        // The chain only forms through the edge that touches THIS parent:
        // the child's top gate for a first child, bottom gate for a last.
        val gates = com.styleconverter.runtime.spacing.BlockMarginCollapse
            .hoistGates(child.properties.map { it.type to it.data })
        return if (edgeIsTop) gates.top else gates.bottom
    }

    // One log line per distinct fallback reason for the whole process —
    // keeps the no-silent-fallthrough contract without flooding logcat on
    // every recomposition. runCatching guards android.util.Log for JVM
    // callers (unit tests exercise the pure plan builder directly).
    private val collapseFallbacksLogged =
        java.util.Collections.synchronizedSet(mutableSetOf<String>())
    private fun logCollapseFallbackOnce(reason: String, componentId: String) {
        if (collapseFallbacksLogged.add(reason)) {
            runCatching {
                android.util.Log.i(
                    "BlockMarginCollapse",
                    "margin-collapse fallback ($reason) — first seen on $componentId; " +
                        "block-axis margins keep the legacy stacking behavior"
                )
            }
        }
    }

    /**
     * True when a Color-typed property's wire value is the `currentColor`
     * keyword. ColorParser emits it srgb-less — the wire is exactly
     * {"original":"currentColor"} (IRColor.ColorRepresentation.CurrentColor
     * serializes as that bare primitive; srgb stays null because the value
     * is context-dependent). A present srgb always means the converter
     * already resolved the color, so it is never re-resolved here. Pure +
     * internal for the JVM pinning suite.
     */
    internal fun isCurrentColorValue(data: kotlinx.serialization.json.JsonElement?): Boolean {
        val obj = data as? JsonObject ?: return false
        if (obj["srgb"] != null) return false
        return (obj["original"] as? JsonPrimitive)?.contentOrNull
            ?.equals("currentColor", ignoreCase = true) == true
    }

    /**
     * The runtime's DEFAULT TEXT COLOR — what glyphs resolve to when the
     * cascade genuinely runs out of `color` declarations. This is the
     * HARNESS STAGE CONTRACT made explicit: the web-harness capture page
     * (apps/web-harness/index.html) declares `body { color: #eee }` on the
     * `#1a1a2e` stage, so on the reference render any text whose `color`
     * chain bottoms out inherits an OPAQUE #eee — rgb(238,238,238), alpha
     * 1. Opaque #eee — NOT the 70%-alpha bg-contrast pick
     * (0xB3EEEEEE), which exists only for the SYNTHESIZED placeholder
     * label (the web placeholder span pins rgba(238,238,238,0.7)
     * explicitly; real inheriting text never blends). iOS's counterpart
     * constant is Color(white: 0.93) == 237/255, within 1/255 of this.
     */
    internal val DEFAULT_TEXT_COLOR = Color(0xFFEEEEEE)

    /**
     * Bottom-out for an own `color: currentColor` declaration
     * (css-color-4 §7.2: currentColor on `color` itself == inherit).
     * With an ancestor Color on the inheritance channel the inherited
     * value wins; with NO ancestor Color the browser's inherit chain ends
     * at the harness body's `color: #eee` — so the honest fallback is
     * [DEFAULT_TEXT_COLOR], the same opaque stage color, NOT the
     * 70%-alpha contrast pick (wave-5 device evidence: web painted
     * 238,238,238 opaque while the contrast-pick path composited to ~171
     * gray over the dark fixture bg — pair 0.856).
     *
     * corpus-v4.1 ink sub-boundary: in WPT capture the inherit chain ends
     * at the ref injection's `:where(body) { color:#000 }` (the UA
     * CanvasText black a real WPT page bottoms out at — see
     * [WPT_DEFAULT_TEXT_INK]), so the WPT-mode bottom-out is BLACK; the
     * dark-stage path keeps the #eee contract above verbatim. The flag is
     * threaded explicitly (no default) so every call site states which
     * side of the mode split it is on. Pure + internal for the JVM
     * pinning suite.
     */
    internal fun resolveCurrentColorBottomOut(
        inheritedColor: Color?,
        wptCaptureMode: Boolean,
    ): Color =
        inheritedColor ?: defaultTextInk(wptCaptureMode, DEFAULT_TEXT_COLOR)

    /**
     * Merge the inherited channel under the component's own declarations.
     * Pure function (JVM-testable): own properties always win; inherited
     * entries only fill types the component didn't declare.
     *
     * Wave-20 (lane W2) refinement — css-cascade-4 §7.3: `unset` on an
     * INHERITED property "acts as inherit", and an explicit `inherit`
     * says so outright. An own `Color: unset|inherit` therefore must NOT
     * shadow the inherited entry (before this fix the keyword blocked
     * the fold, extractColor yielded nothing, and the element fell to
     * the default ink — accent-color-parent-currentcolor's checkbox has
     * `color: unset` and must resolve red through the parent). Scoped to
     * `Color` only: it is the one keyword shape the corpus exercises,
     * and wider global-keyword emulation stays out of this merge.
     */
    internal fun mergeInherited(
        own: List<IRProperty>,
        inherited: List<IRProperty>
    ): List<IRProperty> {
        if (inherited.isEmpty()) return own
        // Drop own inherit-taking Color keywords so the ancestor value
        // flows in (they carry no paintable data of their own — the wire
        // shape is {"original":"unset"|"inherit"} with no srgb payload).
        val effectiveOwn = own.filterNot { p ->
            p.type == "Color" &&
                ((p.data as? JsonObject)?.let { d ->
                    d["srgb"] == null &&
                        ((d["original"] as? JsonPrimitive)?.contentOrNull
                            ?.lowercase() in setOf("unset", "inherit"))
                } == true)
        }
        val declaredTypes = effectiveOwn.mapTo(HashSet()) { it.type }
        return inherited.filter { it.type !in declaredTypes } + effectiveOwn
    }

    /**
     * Render a single IR component.
     *
     * @param itemModifier Optional OUTERMOST modifier injected by a layout
     *   parent (currently: grid cells applying css-align-3 `align-self:
     *   stretch` as fillMaxHeight). Prepended so the style-driven size
     *   modifiers still win for children with definite sizes — callers only
     *   pass a stretch when the corresponding axis is auto.
     */
    @OptIn(ExperimentalLayoutApi::class)
    @Composable
    fun RenderComponent(component: IRComponent, itemModifier: Modifier = Modifier) {
        // ── Wave-17 out-of-flow interception (css-position-3 §2.1) ──────
        // Under a CanvasRootHoist.Host (composed WPT capture only), a box
        // the shared hoist decision marks out-of-flow — FIXED always,
        // ABSOLUTE with no positioned ancestor — composes NOTHING here:
        // an out-of-flow box reserves no space in its parent (pin S5; the
        // diagnosed wave-17 bug rendered it in its flow slot and ADDED the
        // inset on top — flow position + offset — where css-position-3
        // treats insets as absolute anchors). The box itself renders from
        // the host's canvas-root overlay instead (the walk in
        // CanvasRootHoist.collectCanvasHoisted mirrors this decision
        // one-for-one, so nothing is dropped or doubled; LocalBypass marks
        // the overlay's own render so it passes through). Hostless paths —
        // the dark-stage per-component canvas and the whole 327-pair
        // baseline — have LocalActive false and skip this block entirely
        // (frozen-baseline byte-stability). Not a silent fallthrough: the
        // component IS rendered, just from the overlay.
        // Locals read once — the interception, the wave-18 static-position
        // branch below and the ancestry threading all consume the same
        // values, so reading them into vals keeps the three in one frame.
        val hoistHostActive = com.styleconverter.runtime.layout.position.CanvasRootHoist
            .LocalActive.current
        val hoistHasPositionedAncestor = com.styleconverter.runtime.layout.position.CanvasRootHoist
            .LocalHasPositionedAncestor.current
        // Wave 35 (lane B1) — the SECOND ancestry channel (css-transforms-1
        // §3 / css-transforms-2 §6): an ancestor with a used transform is
        // the containing block for fixed AND absolute descendants, so it
        // vetoes the hoist for both. Read next to the positioned flag so the
        // interception below sees one consistent ancestry frame.
        val hoistHasTransformedAncestor = com.styleconverter.runtime.layout.position.CanvasRootHoist
            .LocalHasTransformedAncestor.current
        if (com.styleconverter.runtime.layout.position.CanvasRootHoist.interceptsInFlow(
                component,
                hostActive = hoistHostActive,
                hasPositionedAncestor = hoistHasPositionedAncestor,
                bypass = com.styleconverter.runtime.layout.position.CanvasRootHoist
                    .LocalBypass.current,
                hasTransformedAncestor = hoistHasTransformedAncestor,
            )
        ) {
            return
        }
        // ── Wave-18 RC1: the static-position branch the hoist carved out ──
        // An ABSOLUTE box with NO positioned ancestor and NO inset is NOT
        // hoisted (css-position-3 §3.1 gives it its STATIC position — the
        // canvas-origin hoist painted css-sizing abspos-001/002's square at
        // (0,0) over the paragraph). It renders HERE, in its flow slot, but
        // as an out-of-flow box: the shared zeroFlowAnchor measures it
        // unbounded and reports 0×0 so no sibling moves for it (pin S5) and
        // its ink paints at the slot origin — exactly the static position.
        // Host-gated: hostless paths (dark stage, every committed baseline)
        // keep the byte-identical legacy modifier chain.
        @Suppress("NAME_SHADOWING")
        val itemModifier =
            if (hoistHostActive &&
                com.styleconverter.runtime.layout.position.CanvasRootHoist
                    .rendersInFlowAsStaticPosition(component.properties, hoistHasPositionedAncestor)
            ) {
                // Outermost slot, exactly like the overlay's canvasAnchor.
                itemModifier.then(
                    com.styleconverter.runtime.layout.position.CanvasRootHoist.zeroFlowAnchor()
                )
            } else {
                itemModifier
            }
                // ══ GAP-DECORATIONS ITEM PROBE (wave 24) ══ 1 line, and
                // the identity Modifier for every component whose parent
                // is not a gap-decorated flex container: the registry
                // lookup misses and gapItemProbe returns its receiver.
                .gapItemProbe(component.id)
        // ── Wave-18 RC6: display:contents unboxing (css-display-3 §2.5) ──
        // Resolve the component ONCE per instance: an unboxable `contents`
        // component strips to an undecorated pass-through, and every
        // unboxable `contents` CHILD is spliced out of the children list so
        // all downstream layout paths (block, positioned overlay, flex,
        // grid item collection) see the grandchildren as direct children.
        // Identity for the whole contents-free corpus (same instance out),
        // so remember{} keys and frozen baselines are untouched.
        @Suppress("NAME_SHADOWING")
        val component = androidx.compose.runtime.remember(component) {
            ContentsUnboxing.resolve(component)
        }
        // ── Wave-7 dynamic-styling resolution (schema/spec/06-dynamic-styling.md)
        // Fold ACTIVE media buckets (§4) then ACTIVE selector buckets (§2)
        // over the base list — array order, whole-value replace per property
        // type, last writer wins (§3: state must beat a width-bucket recolor).
        val hasSelectorBuckets = component.selectors.isNotEmpty()
        val hasMediaBuckets = component.media.isNotEmpty()

        // Forced-state hook (§6): the harness capture screen provides one
        // forced condition per run; production hosts leave the set empty.
        val forcedStates = com.styleconverter.runtime.core.states.DynamicStyleResolver
            .LocalForcedStates.current

        // Efficiency gate: ONLY components whose selector buckets contain a
        // real-input runtime-v1 condition (hover/active/focus) pay for an
        // InteractionSource + input modifiers. disabled/checked have no
        // self-service input source (host/harness supplies them, §2), and
        // bucket-free components skip this branch entirely.
        val needsInteraction = hasSelectorBuckets &&
            com.styleconverter.runtime.core.states.DynamicStyleResolver
                .needsInteractionSource(component.selectors)
        val interactionState: com.styleconverter.runtime.core.states.StateHandler.InteractionState?
        val interactionModifier: Modifier
        if (needsInteraction) {
            // The collect*AsState reads inside make THIS composable restyle
            // on every state flip — same composable identity, no subtree
            // recreation (§5 re-evaluation contract).
            val (source, state) = com.styleconverter.runtime.core.states.StateHandler
                .rememberInteractionState()
            interactionState = state
            // Press/hover/focus feeders (see StateHandler.stateInteractions
            // for the per-condition mapping and the touch-hover no-op).
            interactionModifier = with(com.styleconverter.runtime.core.states.StateHandler) {
                Modifier.stateInteractions(source)
            }
        } else {
            // No real-input buckets: resolution still runs (media buckets /
            // forced disabled/checked) but no input plumbing is composed.
            interactionState = null
            interactionModifier = Modifier
        }

        // Render-surface width (§4): media min/max-width compare against the
        // surface the document renders INTO — the harness capture canvas
        // provides it; absent a host-provided surface the window IS the
        // surface (LocalConfiguration width, the runtime's px==dp space).
        val surfaceWidthPx = com.styleconverter.runtime.core.media.MediaBucketEvaluator
            .LocalRenderSurfaceWidthPx.current
            ?: androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp.toFloat()
        // Platform dark-mode signal — shared by prefers-color-scheme buckets
        // AND light-dark() color values below (§4: one surface, one scheme
        // answer). Maps to configuration uiMode's night mask.
        val isDarkScheme = androidx.compose.foundation.isSystemInDarkTheme()

        val mediaProperties = if (!hasSelectorBuckets && !hasMediaBuckets) {
            // Identity-preserving fast path: the static corpus (no buckets)
            // must render byte-identically to the frozen baseline.
            component.properties
        } else {
            androidx.compose.runtime.remember(
                component, interactionState, forcedStates, surfaceWidthPx, isDarkScheme
            ) {
                // Which media buckets hold for this surface (strict
                // runtime-v1 grammar; unsupported ⇒ conservatively inactive).
                val activeMedia = com.styleconverter.runtime.core.media.MediaBucketEvaluator
                    .activeBuckets(component.media, surfaceWidthPx, isDarkScheme)
                // Which selector buckets hold for this state/forced set.
                val activeSelectors = com.styleconverter.runtime.core.states.DynamicStyleResolver
                    .activeSelectorBuckets(component.selectors, interactionState, forcedStates)
                // §3 layering fold: base → media (array order) → selectors
                // (array order), whole-value replace per type.
                com.styleconverter.runtime.core.states.DynamicStyleResolver
                    .resolve(component.properties, activeMedia, activeSelectors)
            }
        }

        // light-dark() color values (css-color-5) resolve per-property
        // against the SAME scheme signal (§4 note) — after bucket folding so
        // bucket-supplied light-dark values resolve too. Identity-preserving
        // when no light-dark value is present (the common case).
        val schemeResolvedProperties = androidx.compose.runtime.remember(mediaProperties, isDarkScheme) {
            com.styleconverter.runtime.core.colors.LightDarkResolver
                .resolve(mediaProperties, isDarkScheme)
        }

        // Fold the parent's inheritable text declarations in UNDER the
        // component's own (CSS inheritance — see LocalInheritedProperties).
        // Uses the DYNAMIC-RESOLVED list so bucket overrides and resolved
        // light-dark colors participate in inheritance like any other value.
        val inheritedProperties = LocalInheritedProperties.current
        // Wave 25 (lane LF follow-up) — the UA `list-style-type` rule runs
        // HERE, the last point that still holds the element's OWN list and
        // the inherited channel separately. On a list container with no own
        // declaration, `ul { list-style-type: disc }` / `ol { … decimal }`
        // (HTML §15.3.9) is a declaration ON the element, so it beats any
        // ancestor value — css-cascade-4 §4.3 consults inheritance only
        // when the cascade produced nothing. Identity (same list instance)
        // for every non-container and every own-declaring container, so no
        // committed capture moves. See ListStyleUaRule for the full
        // argument and the nested-list KNOWN GAP.
        val rawProperties = com.styleconverter.runtime.lists.ListStyleUaRule.apply(
            component._tag,
            schemeResolvedProperties,
            mergeInherited(schemeResolvedProperties, inheritedProperties)
        )

        // CSS `all: initial|inherit|unset|revert|revert-layer` resets every
        // other property to its respective global value. We can't synthesize
        // a per-property reset on Compose at runtime, but for the common
        // case where `all` is the SOLE override on an isolated element, the
        // simplest faithful behaviour is to drop every other declaration so
        // the element collapses to its untouched defaults — which on web is
        // exactly what these audit fixtures expect (no width / height / bg).
        // We only apply this when no parent-cascade context is wired in
        // (true today on Android), so `inherit` falls back to "no styling"
        // — same observable result as the audit's `All_Inherit` web render.
        val allReset = rawProperties.firstOrNull { it.type == "All" }?.let { p ->
            (p.data as? JsonPrimitive)?.contentOrNull?.uppercase()
        }
        val unresolvedProperties = if (allReset != null) emptyList() else rawProperties

        // ── Wave-6 dynamic-value resolution ────────────────────────────
        // Merge this component's custom-property definitions (the decoded
        // v2 `variables` map) ONTO the ambient scope — element scope wins
        // over the slot-parent chain (css-variables-1 §2, spec 02
        // resolution order). The merged scope is re-provided to children
        // below so shadowing composes transitively down the slot tree.
        val parentVarScope = com.styleconverter.runtime.core.variables.LocalCssVariables.current
        val varScope = androidx.compose.runtime.remember(parentVarScope, component.variables) {
            val own = component.variables
            if (own.isNullOrEmpty()) parentVarScope
            else parentVarScope.merge(
                com.styleconverter.runtime.core.variables.CssVariableScope(own)
            )
        }
        // Live evaluation context replacing the resolvers' old constants:
        //  - containing block (% base) from the width channel — provided by
        //    the harness capture root (CaptureCanvas content box) and
        //    re-derived per level from each parent's resolved content box;
        //  - viewport from the host-published [LocalComposedViewport] when a
        //    composed WPT capture provides one (wave 26, lane RES residual 2
        //    — the ref's 358 × 568-or-content RENDER viewport, see
        //    WptComposedGeometry.kt), else LocalConfiguration screen dp (the
        //    runtime's px==dp space, matching every other IR px→dp
        //    conversion) — which is what EVERY non-composed path still gets,
        //    so the 327 dark-stage baselines are untouched;
        //  - parent font size from the inheritance channel (the parent
        //    always publishes RESOLVED px FontSize, see fontSizePxOf);
        //  - root font size: 16px residual constant — fixtures never style
        //    the root element, so the browser-default the web reference
        //    inherits is the honest value (documented in the resolver).
        val containingBlock = com.styleconverter.runtime.core.variables.LocalContainingBlock.current
        val configuration = androidx.compose.ui.platform.LocalConfiguration.current
        // null on every non-composed path → the two elvis fallbacks below are
        // the historical LocalConfiguration basis, verbatim.
        val composedViewport = LocalComposedViewport.current
        val dynCtx = com.styleconverter.runtime.core.variables.DynamicValueResolver.Context(
            containingBlockWidthPx = containingBlock.widthPx,
            containingBlockHeightPx = containingBlock.heightPx,
            viewportWidthPx = composedViewport?.widthPx
                ?: configuration.screenWidthDp.toFloat(),
            viewportHeightPx = composedViewport?.heightPx
                ?: configuration.screenHeightDp.toFloat(),
            parentFontSizePx = com.styleconverter.runtime.core.variables.DynamicValueResolver
                .fontSizePxOf(inheritedProperties)
                ?: com.styleconverter.runtime.core.variables.DynamicValueResolver.DEFAULT_FONT_SIZE_PX,
            rootFontSizePx = com.styleconverter.runtime.core.variables.DynamicValueResolver
                .DEFAULT_FONT_SIZE_PX,
            // Block-context gate for the guaranteed-invalid width emulation
            // (CSS 2.1 §10.3.3 auto-fill — see invalidWidthFallback). The
            // self-alignment channel is true exactly when a flex/grid
            // ancestor owns this component, where auto width is
            // content-based instead.
            blockLevelWidthAuto = !LocalSelfAlignmentHandled.current
        )
        // The pre-resolution pass: var() substituted per §2.3 (guaranteed-
        // invalid ⇒ declaration dropped ⇒ unset), calc()/min()/max()/clamp()
        // evaluated, em/rem/vw/% rewritten to px against the live context.
        // Static lists return the SAME instance — the fixture corpus without
        // dynamic values renders byte-identically to the frozen baseline.
        val dynResolution = androidx.compose.runtime.remember(unresolvedProperties, varScope, dynCtx) {
            com.styleconverter.runtime.core.variables.DynamicValueResolver
                .resolve(unresolvedProperties, varScope.variables, dynCtx)
        }
        // ── Wave-8 motion (schema/spec/07-animations.md) ────────────────
        // 1. Transitions (§4): when the wave-7 bucket fold above changed a
        //    covered property's effective value, animate old → new per
        //    transition-*. Identity-preserving without transition-* props.
        val transitionedProperties = com.styleconverter.runtime.animations.TransitionDriver
            .apply(dynResolution.properties)
        // 2. Keyframe animations (§1–§3): overlay the interpolated wire-
        //    keyframe values for the current clock (live frame loop, or the
        //    CAPTURE_ANIMATION_TIME forced instant, §5) BEFORE extraction —
        //    an animated frame renders through the SAME extractor/applier
        //    path as the SSIM-verified static corpus, so parity is
        //    inherited rather than re-implemented. Identity-preserving for
        //    keyframe-free documents (the frozen-baseline guarantee).
        val animatedProperties = com.styleconverter.runtime.animations.KeyframeAnimationDriver
            .animate(transitionedProperties)
        // ── Wave-9 abspos percent-size pre-resolution ───────────────────
        // An out-of-flow child renders through absposOverflowMeasure's
        // UNBOUNDED measure (Constraints() = 0..∞, wave 8), under which
        // SizingApplier's percent mapping — fillMaxWidth/fillMaxHeight
        // (fraction) — is a DOCUMENTED no-op (fraction × ∞ is undefined,
        // Compose skips the constraint): percentage-of-parent sizing is
        // structurally dead for abspos children unless resolved to px
        // BEFORE the modifier chain is built. css-position-3 §5.1 resolves
        // an abspos percentage against the CONTAINING BLOCK, which is what
        // the LocalContainingBlock channel (read above) carries — so
        // rewrite percent Width/Height wires to exact px here and keep the
        // unbounded measure for the px-specified overflow it was built for.
        // Identity (same list instance) for in-flow components and for
        // percent-free lists — the frozen-baseline byte-stability rule.
        // Wave-18 RC2 rides the same out-of-flow branch: after percents are
        // px, inject the css-position-3 §3.5 INSET-STRETCH sizes (an axis
        // with both opposing insets and no author size sizes to cb − insets,
        // then aspect-ratio resolves inline-first — the abspos-003/004 fix;
        // the unbounded wave-8 measure otherwise collapses such boxes to
        // 0×0). WPT-capture gated: every dark-stage/baseline path keeps the
        // pre-wave-18 list byte-identical (AbsposInsetStretch.inject is
        // additionally identity whenever nothing stretches).
        val wptCaptureModeForStretch = LocalWptCaptureMode.current
        val effectiveProperties =
            if (isOutOfFlowChild(animatedProperties)) {
                // Wave-33 lane C — the ABSPOS view of the containing block.
                // css-position-3 §3.1 makes an out-of-flow box's containing
                // block the positioned ancestor's PADDING box, and CSS 2.2
                // §10.6.4 resolves its percentage height against that box's
                // USED height; §10.5's "compute to auto" degradation
                // explicitly exempts absolutely positioned boxes. So when
                // the declared-size channel had nothing (auto-height
                // ancestor) but the §10.6.3 static evaluation did, the
                // out-of-flow lane — and ONLY the out-of-flow lane — sees
                // the used height in `heightPx`. Identity (same instance)
                // whenever the ancestor's height was already definite or
                // the rule refused (AbsposCbUsedHeight H1–H7), which is
                // every component in the corpus except
                // absolute-tables-007's green table.
                val absposCb =
                    if (containingBlock.heightPx == null && containingBlock.absHeightPx != null) {
                        containingBlock.copy(heightPx = containingBlock.absHeightPx)
                    } else containingBlock
                val pctResolved = resolveOutOfFlowPercentSizes(animatedProperties, absposCb)
                if (wptCaptureModeForStretch) {
                    val stretched = com.styleconverter.runtime.layout.position.AbsposInsetStretch
                        // `_tag` (meta.sourceTag) supplies the UA display
                        // for S5's table classification: the live
                        // absolute-tables-008…011 IRs carry `<table>` with
                        // NO Display property, so the declared-keyword
                        // channel alone never sees them.
                        .inject(pctResolved, absposCb, component._tag)
                    // Wave-31 lane T rides the same out-of-flow branch, one
                    // step LATER: with the used size now known, resolve any
                    // `auto` margins per CSS 2.1 §10.3.7 (inline) / §10.6.4
                    // (block) and fold the solved START margin into the
                    // start inset — the absolute-tables-016 fix, where the
                    // block axis was never centred at all (the §10.3.3
                    // `autoMarginAlignment` path below covers the inline
                    // axis only, and only symmetrically). Identity for
                    // every box whose split is 0/0 or whose axis has an
                    // auto inset/size, i.e. everything else in the corpus.
                    com.styleconverter.runtime.layout.position.AbsposAutoMargin
                        .inject(stretched, absposCb)
                } else pctResolved
            } else animatedProperties

        // Extract property pairs for extractors
        val propertyPairs = effectiveProperties.map { it.type to it.data }

        // Phase 7 step 1: style-engine layout hook (no-op short-circuit).
        // extractLayoutConfig() returns LayoutConfig.Empty in step 1, and
        // containerDecision() returns ContainerDecision.default which the
        // renderer treats as "defer to legacy path." Populated in later steps.
        val layoutConfig = com.styleconverter.runtime.layout.LayoutFacade.extractLayoutConfig(propertyPairs)
        val engineDecision = com.styleconverter.runtime.layout.LayoutFacade.containerDecision(layoutConfig)
        // Step 1: engineDecision is always .default; legacy path still runs unchanged.
        // Follow-up steps (flexbox/grid/position) will populate this.

        // Extract data outside composable scope with error handling.
        // The previous catch silently swallowed exceptions and returned a
        // bare Modifier, which dropped width / height / bg on any element
        // whose IR contained a property that an extractor couldn't parse —
        // collapsing the box to defaultMinSize. Logging the throwable
        // lets logcat reveal which extractor blew up so we can either fix
        // the extractor or normalise the IR shape.
        // CSS2 §8.3.1 margin-collapse override: when THIS component is a
        // child in a parent's collapsing block flow, the parent's plan
        // provides the post-collapse applied block-axis margins here (see
        // BlockMarginCollapse + the block branch in RenderComponentContent).
        // Null on every other path — the style chain is byte-identical then.
        val collapsedMargin = com.styleconverter.runtime.spacing.BlockMarginCollapse
            .LocalCollapsedMargin.current
        // TITAN WPT lane — thread the ambient capture mode into the static
        // style chain (it can't read CompositionLocals itself). In WPT
        // capture the sizing lane defaults an UNDECLARED box-sizing to
        // css-sizing-3 §3's content-box initial value (the UA default the
        // WPT refs assume — twin of the web harness's body.wpt-mode
        // override); false on every dark-stage/baseline path, keeping the
        // 327-pair corpus byte-identical (SizingApplier.effectiveBoxSizing
        // pins the split).
        val wptCaptureModeForSizing = LocalWptCaptureMode.current
        val baseModifier = try {
            StyleApplier.applyProperties(effectiveProperties, collapsedMargin, wptCaptureModeForSizing)
        } catch (e: Exception) {
            android.util.Log.w("StyleApplier", "applyProperties threw for ${component.id}: ${e.message}", e)
            Modifier
        }

        // Extract animation config and apply animated modifier
        val animationConfig = try {
            AnimationExtractor.extractAnimationConfig(propertyPairs)
        } catch (e: Exception) {
            null
        }

        val transitionConfig = try {
            AnimationExtractor.extractTransitionConfig(propertyPairs)
        } catch (e: Exception) {
            null
        }

        // Apply default min dimensions matching web's ComponentRenderer defaults:
        // web: minWidth = styles.width || styles.minWidth || '50px'
        // web: minHeight = styles.height || styles.minHeight || '30px'
        val hasExplicitWidth = effectiveProperties.any { it.type in listOf("Width", "MinWidth", "InlineSize", "MinInlineSize") }
        val hasExplicitHeight = effectiveProperties.any { it.type in listOf("Height", "MinHeight", "BlockSize", "MinBlockSize") }
        // Web-parity clamp for ANIMATION-SUPPLIED sizes: web's placeholder
        // floor (min-width:50/min-height:30, injected when the BASE styles
        // lack width/height) keeps clamping an @keyframes-animated size —
        // CSS min-width beats width from any origin. On Android the
        // inside-chain floor below is skipped on axes the animated list
        // declares, so a keyframe track that supplies the ONLY width (e.g.
        // motion-steps' 40px at 0% on a width-less component) would render
        // 40 where web renders max(40, 50). Pre-chaining defaultMinSize
        // OUTSIDE the style chain reproduces the clamp: Modifier.width
        // coerces into incoming min constraints (unlike requiredWidth), so
        // max(animated, floor) falls out of constraint propagation.
        val animatedSizeFloor: Modifier =
            if (effectiveProperties !== transitionedProperties) {
                val baseHadWidth = transitionedProperties.any {
                    it.type in listOf("Width", "MinWidth", "InlineSize", "MinInlineSize")
                }
                val baseHadHeight = transitionedProperties.any {
                    it.type in listOf("Height", "MinHeight", "BlockSize", "MinBlockSize")
                }
                // Only the axes the ANIMATION introduced need the clamp —
                // base-declared axes already skipped web's floor entirely.
                val animSuppliedWidth = hasExplicitWidth && !baseHadWidth
                val animSuppliedHeight = hasExplicitHeight && !baseHadHeight
                if (animSuppliedWidth || animSuppliedHeight) {
                    StyleApplier.placeholderFloorMinSize(
                        effectiveProperties,
                        applyWidthFloor = animSuppliedWidth,
                        applyHeightFloor = animSuppliedHeight
                    )
                } else Modifier
            } else Modifier // identity fast path: no animation overlay ran
        // itemModifier (parent-injected stretch) goes OUTERMOST so the grid
        // cell's fillMaxHeight established the constraint the style chain
        // then works within. The interaction feeders (press/hover/focus →
        // selector-bucket restyle) sit just inside it, ahead of the style
        // chain, so the hit/hover/focus target is the component's full
        // laid-out box; Modifier (the no-selector case) chains as a no-op.
        // animatedSizeFloor sits OUTSIDE baseModifier so its min constraint
        // coerces the animated width/height upward (see the comment above);
        // Modifier (the common no-animation case) chains as a no-op.
        // Round-4b (composed WPT block-flow, FIX 2 completion). The native twin
        // of the web harness's WPT_MODE block-flow carve-out
        // (apps/web-harness/src/sdui/ComponentRenderer.tsx: under `?wpt=1` it
        // drops `fit-content` + `minWidth:50px` + `minHeight:30px` so block
        // elements get browser block-flow — width stretches to the containing
        // block, height hugs content). Those 50×30 floors are ESSENTIAL for the
        // 327-pair baseline (Compose hugs its content intrinsically) but break
        // stacked WPT tests: a <p> bar rendered 50dp-wide × 30dp-tall instead of
        // full-width × one-line-box makes the composed page's bars drift off the
        // ref's positions (the "half-a-pitch drift" the web fix calls out) and
        // horizontally miss the ref's full-width bars. Gated on
        // [LocalWptComposedMode] so the 327 baseline AND the per-component inbox
        // path (both leave it false) are byte-identical.
        val composedWpt = LocalWptComposedMode.current
        // Block-level (NOT a flex/grid item — LocalSelfAlignmentHandled false)
        // with no IR-declared width → stretch to the containing block, exactly
        // like a browser block box. Explicit-width components (a98rgb's squares,
        // the border-radius control) keep their declared width (hasExplicitWidth).
        // EXCLUSIONS: an aspect-ratio box derives a DEFINITE width from its
        // height (width = height × ratio), and an absolutely/fixed-positioned
        // box is sized by its own dimensions, not block flow — neither may be
        // stretched. Without these guards abspos-002 (height:100 +
        // aspect-ratio:1/1 + position:absolute — a 100px SQUARE) got filled to
        // the full canvas width, and aspect-ratio then derived a full-canvas
        // height (Android 0.762 vs a 100px green square).
        val hasAspectRatio = effectiveProperties.any { it.type == "AspectRatio" }
        val isOutOfFlow = extractPositionType(effectiveProperties)
            .let { it == PositionType.ABSOLUTE || it == PositionType.FIXED }
        val blockFlowWidth: Modifier =
            if (composedWpt && !hasExplicitWidth && !hasAspectRatio && !isOutOfFlow &&
                !LocalSelfAlignmentHandled.current)
                Modifier.fillMaxWidth()
            else Modifier
        // The 50×30 placeholder floor — skipped entirely in composed WPT capture
        // so the box hugs its content height (one line box), matching the ref's
        // <p>. Every other path keeps the floor.
        val placeholderFloor: Modifier =
            if (composedWpt) Modifier
            else StyleApplier.placeholderFloorMinSize(
                effectiveProperties,
                applyWidthFloor = !hasExplicitWidth,
                applyHeightFloor = !hasExplicitHeight
            )
        val sizedModifier = itemModifier.then(interactionModifier).then(animatedSizeFloor)
            // blockFlowWidth sits just OUTSIDE baseModifier so the component's
            // BackgroundColor (applied inside baseModifier) paints across the
            // full stretched width; a no-op (Modifier) on every non-composed
            // path and for explicit-width components.
            .then(blockFlowWidth).then(baseModifier).then(
            // BORDER-BOX floor: web's 50/30px minimum constrains the whole
            // card (box-sizing: border-box), so the content-box minimum
            // Compose enforces here (inside the padding-last chain) must be
            // 50/30 MINUS the padding + border bands. The previous raw
            // defaultMinSize(50.dp, 30.dp) floored the CONTENT box instead,
            // adding +2px to every default-font placeholder under
            // `padding: 8px+` (all 33 Decorated combo rows: Android canvas
            // 78/86/94/102 vs web 76/84/92/100). See
            // StyleApplier.placeholderFloorMinSize for the exact math.
            placeholderFloor
        ).then(
            // Border-band content inset — INSIDE the 30dp placeholder floor
            // above, so the band participates in the minimum instead of
            // stacking on top of it. Web's floor (`minHeight: 30px`) is a
            // border-box minimum: for a min-bound box the border is absorbed
            // by the 30px, not added to it. Chaining the band before the
            // floor grew every min-bound bordered placeholder by the band
            // (the wave-1 +2px regression: 094_Input_Field 198x52→198x54
            // content box, 097_Glass_Effect 140x70→140x72). See
            // StyleApplier.borderContentInset for the per-side math.
            StyleApplier.borderContentInset(effectiveProperties)
        )

        // Apply the LEGACY animated modifier only when the wave-8 driver
        // did NOT own the animation: (a) none of the names resolve against
        // the document's wire keyframes (the driver already overlaid those
        // values into effectiveProperties above — wrapping again would
        // double-apply), and (b) no CAPTURE_ANIMATION_TIME is forced (a
        // seized run must never leave a live legacy clock smearing the
        // frame — spec 07 §5 "every animation, paused").
        val docKeyframes = com.styleconverter.runtime.animations.KeyframeAnimationDriver
            .LocalDocumentKeyframes.current
        val forcedAnimationTime = com.styleconverter.runtime.animations.KeyframeAnimationDriver
            .LocalForcedAnimationTime.current
        val modifier = if (animationConfig?.hasAnimations == true &&
            forcedAnimationTime == null &&
            animationConfig.names.none { docKeyframes.containsKey(it) || docKeyframes.containsKey(it.lowercase()) }
        ) {
            animatedModifier(sizedModifier, animationConfig, transitionConfig ?: com.styleconverter.runtime.animations.TransitionConfig())
        } else {
            sizedModifier
        }

        val displayConfig = try {
            extractDisplayConfig(effectiveProperties)
        } catch (e: Exception) {
            DisplayConfig(DisplayType.BLOCK, FlexDirection.ROW, JustifyContent.FLEX_START, AlignItems.STRETCH, AlignContent.STRETCH, FlexWrap.NOWRAP, 0.dp, 0.dp)
        }

        // Wave 9 (#37): `Color` rides the inheritance channel now, so the
        // merged list may carry an ancestor's color. The web placeholder
        // span pins its own color (explicit or contrast pick) and NEVER
        // paints a DOM-inherited color on leaf glyphs, so the placeholder
        // textColor stays own-declared-only. currentColor consumers
        // (border / outline / text-emphasis extractors) keep reading the
        // MERGED list and therefore resolve against the inherited color —
        // the exact split the browser implements.
        val colorIsInheritedOnly = inheritedProperties.any { it.type == "Color" } &&
            schemeResolvedProperties.none { it.type == "Color" }
        // css-color-4 §7.2: `color: currentColor` on the COLOR property
        // itself is treated as `color: inherit` — it resolves to the
        // INHERITED color, never circularly to the element's own value.
        // The wire ships it srgb-less ({"original":"currentColor"}), so
        // extractTextColor returned null and the placeholder fell back to
        // the bg-contrast pick while web painted the ancestor's color.
        // Resolve through the wave-9 inheritance channel: the inherited
        // Color entry IS the ancestor's computed color. No inherited Color
        // → the browser's inherit chain ends at the harness BODY's
        // `color: #eee` (opaque), so bottom out into DEFAULT_TEXT_COLOR —
        // the earlier null-into-contrast-pick fallback composited the
        // 70%-alpha placeholder pick (~171 gray over a dark bg) while web
        // painted 238,238,238 opaque (wave-5 device evidence, pair 0.856;
        // see resolveCurrentColorBottomOut).
        val ownColorIsCurrentColor = schemeResolvedProperties.any {
            it.type == "Color" && isCurrentColorValue(it.data)
        }
        // Read the ambient WPT flag OUTSIDE the try below — CompositionLocal
        // reads are composable calls, and the Compose compiler forbids
        // composable invocations inside try/catch. The flag feeds the
        // corpus-v4.1 ink split in resolveCurrentColorBottomOut (WPT mode →
        // spec BLACK; dark-stage path → the historical #eee contract).
        val wptCaptureModeForInk = LocalWptCaptureMode.current
        val textColor = if (colorIsInheritedOnly) null else try {
            if (ownColorIsCurrentColor) {
                resolveCurrentColorBottomOut(
                    inheritedProperties.firstOrNull { it.type == "Color" }
                        ?.let { ValueExtractors.extractColor(it.data) },
                    wptCaptureModeForInk,
                )
            } else {
                TextStyleApplier.extractTextColor(effectiveProperties)
            }
        } catch (e: Exception) {
            null
        }

        // Extract text direction for layout
        val direction = TextStyleApplier.extractDirection(effectiveProperties)
        val layoutDirection = when (direction) {
            TextStyleApplier.DirectionMode.RTL -> LayoutDirection.Rtl
            TextStyleApplier.DirectionMode.LTR -> LayoutDirection.Ltr
        }

        // Check if this is a container query container
        val containerConfig = try {
            ContainerQueryExtractor.extractContainerQueryConfig(propertyPairs)
        } catch (e: Exception) {
            null
        }

        // Check for border image
        val borderImageConfig = try {
            BorderImageExtractor.extractBorderImageConfig(propertyPairs)
        } catch (e: Exception) {
            null
        }

        // Extract form styling configuration
        val formStylingConfig = try {
            FormStylingExtractor.extractFormConfig(propertyPairs)
        } catch (e: Exception) {
            null
        }

        // Extract before/after pseudo-element configuration from selectors
        val beforeAfterConfig = try {
            ContentApplier.extractBeforeAfterConfig(component.selectors)
        } catch (e: Exception) {
            null
        }

        // Extract counter configuration for nested content
        val counterConfig = try {
            ContentExtractor.extractCounterConfig(propertyPairs)
        } catch (e: Exception) {
            null
        }

        // Phase 7b: unpack any FlexDecision the style engine produced. When
        // present, RenderComponentContent takes a dedicated flex branch that
        // uses engine-computed Arrangement / Alignment instead of the
        // legacy DisplayConfig path. When absent (null), the legacy path
        // still runs unchanged — preserves zero-behavioural-change for
        // every component that doesn't set display:flex.
        val flexDecision = engineDecision.arrangement
            as? com.styleconverter.runtime.layout.flexbox.FlexDecision

        // Content rendering must see the INHERITED-MERGED property list, not
        // the raw IR one: PlaceholderContent derives its TextStyle from
        // `component.properties`, so a child with EMPTY properties under a
        // `font-size: 22px` parent rendered at the 16sp default (IH_FontSize
        // 0.865, IH_LineHeight 0.702 — web inherits both per css-cascade-4
        // §7.3, our merge channel computed them but only fed the modifier
        // chain). Children are untouched — each re-merges from the
        // composition local when recursed.
        val mergedComponent = component.copy(properties = effectiveProperties)

        // Wrap content with direction if not default LTR
        val content: @Composable () -> Unit = {
            // Wrap in BorderImageBox if border image is configured
            if (borderImageConfig?.hasBorderImage == true) {
                BorderImageApplier.BorderImageBox(
                    config = borderImageConfig,
                    modifier = modifier
                ) {
                    RenderComponentContent(mergedComponent, Modifier, displayConfig, textColor, engineDecision, flexDecision)
                }
            } else {
                RenderComponentContent(mergedComponent, modifier, displayConfig, textColor, engineDecision, flexDecision)
            }
        }

        // Apply container query wrapper if needed
        val containerWrappedContent: @Composable () -> Unit = if (containerConfig?.hasContainerQuery == true) {
            {
                ContainerQueryApplier.QueryContainer(
                    config = containerConfig,
                    modifier = Modifier
                ) {
                    content()
                }
            }
        } else {
            content
        }

        // Apply form styling wrapper if needed
        val formWrappedContent: @Composable () -> Unit = if (formStylingConfig?.hasFormStyling == true) {
            {
                FormStylingApplier.FormStylingProvider(config = formStylingConfig) {
                    containerWrappedContent()
                }
            }
        } else {
            containerWrappedContent
        }

        // Apply counter state wrapper if counters are defined
        val counterWrappedContent: @Composable () -> Unit = if (counterConfig?.hasCounters == true) {
            {
                CounterStateProvider(config = counterConfig) {
                    formWrappedContent()
                }
            }
        } else {
            formWrappedContent
        }

        // Apply before/after pseudo-element wrapper if defined
        val wrappedContent: @Composable () -> Unit = if (beforeAfterConfig?.hasBeforeAfter == true) {
            {
                ContentApplier.ContentWithPseudoElements(config = beforeAfterConfig) {
                    counterWrappedContent()
                }
            }
        } else {
            counterWrappedContent
        }

        // Re-provide the merged inheritable set for descendants. Because
        // effectiveProperties already contains what THIS component inherited,
        // filtering it reproduces the transitive cascade (grandchildren see
        // grandparent values unless a closer ancestor overrode them).
        // NOTE: filtered from the RESOLVED list, so children inherit the
        // parent's COMPUTED FontSize px (css-cascade-4 §7.3: inherited
        // values are computed values) — a parent `font-size: 1.5em` hands
        // 24px down, never the raw em that would compound per level.
        val inheritableForChildren = effectiveProperties.filter {
            it.type in INHERITED_PROPERTY_TYPES
        }.map { p ->
            // css-color-4 §7.2: the COMPUTED value of `color: currentColor`
            // is the inherited color — children must inherit the resolved
            // ancestor color, not the unresolvable keyword (an unresolved
            // currentColor entry would null out every descendant's color
            // extraction). Substitute the inherited entry when available;
            // with no ancestor Color the keyword rides along unchanged and
            // resolves to the same defaults at each level.
            if (p.type == "Color" && isCurrentColorValue(p.data)) {
                inheritedProperties.firstOrNull { it.type == "Color" } ?: p
            } else p
        }
        // The containing block THIS component establishes for its children:
        // its resolved content box (width channel, CSS 2.1 §10.1). Unknown
        // axes stay null — resolution then leaves child % values untouched.
        val childContainingBlock = androidx.compose.runtime.remember(
            effectiveProperties, containingBlock, component.children, wptCaptureModeForStretch,
        ) {
            val declared = com.styleconverter.runtime.core.variables.DynamicValueResolver
                .childContainingBlock(effectiveProperties, containingBlock)
            // Wave-33 lane C — the auto-height ABSPOS fallback. When the
            // declared channel above found no block size (this box's height
            // is `auto`), evaluate CSS 2.2 §10.6.3 statically over the
            // in-flow children and publish the result on the abspos-only
            // side channel; the out-of-flow branch at the top of
            // RenderComponent is its sole reader, so in-flow `%` heights
            // keep degrading per §10.5 exactly as before. WPT-capture
            // gated so all 363 committed dark-stage baselines are
            // byte-identical by construction, matching the
            // AbsposInsetStretch / AbsposAutoMargin precedent.
            if (!wptCaptureModeForStretch || declared.heightPx != null) declared
            else declared.copy(
                absHeightPx = com.styleconverter.runtime.layout.position.AbsposCbUsedHeight
                    .contentHeightPx(
                        ancestor = effectiveProperties,
                        // `_tag` (meta.sourceTag) is the only sighting of a
                        // bare `<table>`: the converter never serializes UA
                        // defaults (same channel AbsposInsetStretch.S5 uses).
                        ancestorTag = component._tag,
                        // Own text / inline runs make the content height a
                        // line-box question no static rule can answer (H4).
                        ancestorHasOwnContent =
                            !component._text.isNullOrEmpty() || !component.runs.isNullOrEmpty(),
                        children = component.children.orEmpty().map { it.properties },
                    )?.toFloat(),
            )
        }
        // Wave-17 positioned-ancestor channel (CSS 2.1 §10.1): descendants
        // of a positioned box (position != static) have a positioned
        // containing-block ancestor, so an ABSOLUTE descendant must NOT
        // hoist to the canvas root (pin S4 — the wave-8/9 machinery anchors
        // it at this ancestor's padding box instead). Computed from the RAW
        // base declarations, deliberately mirroring the pure walk in
        // CanvasRootHoist.collectCanvasHoisted — the two decisions must
        // agree byte-for-byte or a box is dropped from flow with no overlay
        // slot (bucket-flipped Position values are out of scope on BOTH
        // sides, same conservatism as collapse-plan bail B6).
        val childHasPositionedAncestor =
            com.styleconverter.runtime.layout.position.CanvasRootHoist
                .LocalHasPositionedAncestor.current ||
                com.styleconverter.runtime.layout.position.CanvasRootHoist
                    .establishesContainingBlock(component.properties)
        // Wave-35 (lane B1) — the TRANSFORM half of the same channel
        // (css-transforms-1 §3 / css-transforms-2 §6). Kept separate from the
        // positioned flag because the two claim different descendant classes:
        // only a transformed ancestor pulls a FIXED box out of the viewport.
        // Same OR-accumulating rule and the same raw-declaration basis, so it
        // mirrors CanvasRootHoist.collectCanvasHoisted's second flag exactly.
        val childHasTransformedAncestor =
            com.styleconverter.runtime.layout.position.CanvasRootHoist
                .LocalHasTransformedAncestor.current ||
                com.styleconverter.runtime.layout.position.CanvasRootHoist
                    .establishesTransformContainingBlock(component.properties)
        val inheritanceWrappedContent: @Composable () -> Unit = {
            CompositionLocalProvider(
                LocalInheritedProperties provides inheritableForChildren,
                // Wave 9: whether THIS component's Color is inherited-only —
                // read by its own PlaceholderContent (leaf-glyph gate); each
                // child RenderComponent re-provides its own value before the
                // child's placeholder composes.
                LocalColorIsInheritedOnly provides colorIsInheritedOnly,
                // Custom-property scope for descendants — element definitions
                // shadow the slot-parent chain (spec 02 resolution order).
                com.styleconverter.runtime.core.variables.LocalCssVariables provides varScope,
                // % base channel for descendants' calc()/bare-% resolution.
                com.styleconverter.runtime.core.variables.LocalContainingBlock provides childContainingBlock,
                // §8.3.1 collapse channels are strictly one-level: THIS
                // component consumed its own override above, and any plan it
                // publishes for its children is provided deeper (inside its
                // block branch). Reset both so grandchildren never see a
                // stale ancestor override/plan.
                com.styleconverter.runtime.spacing.BlockMarginCollapse
                    .LocalCollapsedMargin provides null,
                LocalBlockCollapsePlan provides null,
                // Wave-17: publish the positioned-ancestor flag for the
                // whole subtree (see childHasPositionedAncestor above) so
                // the canvas-hoist interception at each descendant's
                // RenderComponent reads the same ancestry the pure walk
                // computed. Provided unconditionally: outside a host the
                // value is never read past the LocalActive gate, and
                // providing it costs no layout node.
                com.styleconverter.runtime.layout.position.CanvasRootHoist
                    .LocalHasPositionedAncestor provides childHasPositionedAncestor,
                // Wave-35 (lane B1): the transform-CB twin of the line above.
                // Provided unconditionally for the same reason — outside a
                // host it is never read past the LocalActive gate.
                com.styleconverter.runtime.layout.position.CanvasRootHoist
                    .LocalHasTransformedAncestor provides childHasTransformedAncestor
            ) {
                wrappedContent()
            }
        }

        // css-align-3 §6.2: `justify-self` on a BLOCK-LEVEL box aligns the
        // box itself within its containing block's inline axis. Web renders
        // Layout_C12_JustifySelf (justify-self: center, width 200) centered
        // in the 358px canvas content box (x=95 on the capture) while
        // Android left-flushed it (SSIM 0.675). Only active when no ancestor
        // flex/grid container already owns self-alignment (see
        // LocalSelfAlignmentHandled) — flex items IGNORE justify-self and
        // grid cells implement it as cell contentAlignment.
        val blockJustifySelf = extractJustifySelf(effectiveProperties)
        // Resolve the block-level inline-axis alignment from either channel:
        //   - justify-self center/end (css-align-3 §6.2, wave-2 fix), or
        //   - auto horizontal margins (CSS 2.1 §10.3.3: definite-width block
        //     box + `margin-left/right: auto` centers in its containing
        //     block; left-only auto pushes the box right). Web centers
        //     B_MarginAuto's child (tree) AND the standalone child capture
        //     (auto margins against the capture canvas); Compose left-pinned
        //     both (0.805 parent / 0.616 child crop). Handling it HERE — not
        //     in the block-Column child loop — makes one implementation
        //     cover both render paths.
        val blockSelfAlignment: Alignment? = when {
            LocalSelfAlignmentHandled.current -> null
            blockJustifySelf == JustifySelf.CENTER -> Alignment.TopCenter
            // Physical right / end / flex-end all collapse to End in the
            // LTR-normalized engine (css-align-3 §5.2).
            blockJustifySelf == JustifySelf.END ||
                blockJustifySelf == JustifySelf.FLEX_END ||
                blockJustifySelf == JustifySelf.RIGHT -> Alignment.TopEnd
            else -> autoMarginAlignment(effectiveProperties)
                // CSS 2.1 §9.5: `float: right` (and its logical alias
                // `inline-end` in our LTR-normalized engine) shifts the box
                // to the RIGHT edge of its containing block. Compose has no
                // float layout; for the harness's dominant case — a floated
                // box whose siblings' wrap behaviour isn't observable — the
                // end-alignment wrapper reproduces the box position web
                // paints (PW_Borders_Layout_03 green box flush right,
                // A-w 0.406/55% px; PW_Layout_Typography_04 A-w 0.356 —
                // iOS shared the same at-left gap, i-A 0.95). float:left/
                // inline-start is the block default → no wrapper needed.
                ?: floatEndAlignment(effectiveProperties)
        }
        val selfAlignedContent: @Composable () -> Unit =
            if (blockSelfAlignment != null) {
                {
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = blockSelfAlignment
                    ) {
                        inheritanceWrappedContent()
                    }
                }
            } else {
                inheritanceWrappedContent
            }

        if (direction == TextStyleApplier.DirectionMode.RTL) {
            CompositionLocalProvider(LocalLayoutDirection provides layoutDirection) {
                selfAlignedContent()
            }
        } else {
            selfAlignedContent()
        }
    }

    /**
     * Render the actual component content (separated for direction wrapping).
     */
    @OptIn(ExperimentalLayoutApi::class)
    @Composable
    private fun RenderComponentContent(
        component: IRComponent,
        modifier: Modifier,
        displayConfig: DisplayConfig,
        textColor: Color?,
        engineDecision: com.styleconverter.runtime.layout.ContainerDecision =
            com.styleconverter.runtime.layout.ContainerDecision.default,
        flexDecision: com.styleconverter.runtime.layout.flexbox.FlexDecision? = null
    ) {
        // ══ GAP-DECORATIONS DRAW HOOK (wave 24, css-gap-decorations-1) ══
        // 6 lines. rememberGapDecorationSink returns null — and
        // gapDecorations then returns the receiver untouched — for every
        // component that is not a flex container declaring a painting
        // *-rule-*; see columns/GapDecorationHook.kt for the dark-stage
        // byte-stability argument. Must stay the FIRST statement so the
        // composable call site is unconditional.
        val gapSink = rememberGapDecorationSink(
            component, flexDecision != null,
            flexDecision?.kind == com.styleconverter.runtime.layout.flexbox.FlexContainerKind.Row ||
                flexDecision?.kind == com.styleconverter.runtime.layout.flexbox.FlexContainerKind.FlowRow
        )
        @Suppress("NAME_SHADOWING")
        val modifier = modifier.gapDecorations(gapSink)
        // ══ end GAP-DECORATIONS DRAW HOOK ═══════════════════════════════
        // Phase 7b engine-driven flex branch. Only activates when the
        // style-engine produced a FlexDecision; falls through to the legacy
        // displayConfig switch otherwise.
        if (engineDecision.kind == com.styleconverter.runtime.layout.ContainerKind.None) {
            // display: none — suppress rendering entirely.
            return
        }
        // Empty-container demotion — mirrors the web harness rule in
        // apps/web-harness/src/sdui/ComponentRenderer.tsx ("Render children
        // or placeholder" block): a grid/flex container with ZERO children
        // renders as `display: block`, because grid tracks / flex
        // arrangement make no visual sense for a lone placeholder label.
        // Without this, Compose kept the Row/Column/Grid path alive for
        // empty containers, so `justify-content` / `align-items` / grid
        // track sizing repositioned the placeholder while web pinned it
        // top-left — Flex_JustifyCenter / Flex_AlignCenter / Grid_Simple /
        // Grid_FixedTracks all diverged structurally (SSIM 0.80-0.90 on the
        // visual-test fixture). Box's default contentAlignment is TopStart,
        // matching CSS block flow (align-items is ignored in block layout).
        if (demotesEmptyContainer(displayConfig.type, component.children.isNullOrEmpty()) ||
            (component.children.isNullOrEmpty() && flexDecision != null &&
                engineDecision.kind == com.styleconverter.runtime.layout.ContainerKind.Flex)
        ) {
            Box(modifier = modifier) {
                RenderContent(component, textColor, displayConfig)
            }
            return
        }
        if (flexDecision != null &&
            engineDecision.kind == com.styleconverter.runtime.layout.ContainerKind.Flex
        ) {
            // Re-use legacy gap extraction — gap isn't owned by the flex
            // sub-config yet (TODO phase7/step5 folds spacing into LayoutConfig).
            val rowGap = displayConfig.rowGap
            val columnGap = displayConfig.columnGap
            // Wave 25 CAL-RC2: fold both gaps into ALL FOUR arrangements
            // once, here. The wrapping branches below used to read
            // flexDecision.horizontalArrangement / .verticalArrangement —
            // the justify-content-ONLY mapping — so `column-gap` vanished
            // from every wrapping flex row and `row-gap` from every
            // wrapping flex column. Reading axes.* instead means no branch
            // can reach a gap-free main-axis arrangement any more (see
            // layout/flexbox/FlexAxes.kt for the byte-stability argument:
            // with 0dp gaps every field is the same object as before).
            val axes = com.styleconverter.runtime.layout.flexbox
                .FlexAxes.of(flexDecision, rowGap, columnGap)
            when (flexDecision.kind) {
                com.styleconverter.runtime.layout.flexbox.FlexContainerKind.Row -> {
                    // Gap + justify-content COMPOSE, they don't compete:
                    // a distributing justify (space-between/around/evenly)
                    // owns the free space — the old `gap > 0 → spacedBy`
                    // override packed FC_SpaceBetween's children at the
                    // top because the fixture also declared `gap: 6px`
                    // (Android-web 0.920). mainAxisArrangement folds the
                    // gap into spacedBy(gap, <align>) only for the
                    // non-distributing keywords, mirroring the legacy
                    // toRowArrangement path. Hoisted so the intrinsic flex
                    // path inside RenderRowContent re-uses the same values.
                    val rowArrangement = axes.mainHorizontal
                    Row(
                        modifier = modifier,
                        horizontalArrangement = rowArrangement,
                        verticalAlignment = flexDecision.verticalAlignment
                    ) {
                        RenderRowContent(
                            component, textColor,
                            mainArrangement = rowArrangement,
                            crossAlignment = flexDecision.verticalAlignment
                        )
                    }
                    return
                }
                com.styleconverter.runtime.layout.flexbox.FlexContainerKind.Column -> {
                    // Same gap/justify composition as the Row branch —
                    // hoisted for the intrinsic path too.
                    val columnArrangement = axes.mainVertical
                    Column(
                        modifier = modifier,
                        verticalArrangement = columnArrangement,
                        horizontalAlignment = flexDecision.horizontalAlignment
                    ) {
                        RenderColumnContent(
                            component, textColor,
                            mainArrangement = columnArrangement,
                            crossAlignment = flexDecision.horizontalAlignment
                        )
                    }
                    return
                }
                com.styleconverter.runtime.layout.flexbox.FlexContainerKind.FlowRow -> {
                    // Wave 25 CAL-RC5: a wrapping row whose items must
                    // stretch to their LINE's cross size (css-flexbox-1
                    // §8.4 + §9.4 step 8) cannot be expressed with
                    // FlowRow — its lines never grow into a definite
                    // container height, which is why css-gaps
                    // flex-gap-decorations-001/002 (width-only children,
                    // auto height) captured EMPTY on Android. The plan is
                    // null whenever no item actually stretches or the
                    // container needs a RenderContent feature the wrap
                    // layout doesn't emit, so every other wrapping row
                    // keeps the frozen FlowRow path.
                    val stretchPlan = wrapRowStretchPlan(component, displayConfig)
                    if (stretchPlan != null) {
                        com.styleconverter.runtime.layout.flexbox.FlexWrapRow(
                            modifier = modifier,
                            mainArrangement = axes.mainHorizontal,
                            mainGap = axes.columnGap,
                            crossGap = axes.rowGap,
                            cross = stretchPlan,
                            containerCross = flexDecision.verticalAlignment,
                            // Skeptic wave-25 fix: §9.4 step 8 (grow the
                            // lines into a definite container cross size)
                            // is an `align-content: normal | stretch`
                            // rule. extractDisplayConfig already folds
                            // `normal`/absent into STRETCH, so this reads
                            // false only for an EXPLICIT non-stretch
                            // keyword (flexbox-baseline-multi-line-horiz-
                            // 003/004 declare `align-content: center` with
                            // a 100px height and were being stretched).
                            alignContentStretches =
                                displayConfig.alignContent == AlignContent.STRETCH
                        ) {
                            // One measurable per flex item, index-aligned
                            // with the plan — same 1:1 wrapper the
                            // intrinsic row path uses, and the same
                            // `order` sort the non-wrapping flex branches
                            // apply (css-flexbox-1 §5.4).
                            sortByOrder(component.children!!).forEach { child ->
                                Box(propagateMinConstraints = true) {
                                    RenderComponent(child, Modifier)
                                }
                            }
                        }
                        return
                    }
                    FlowRow(
                        modifier = modifier,
                        // CAL-RC2: was flexDecision.horizontalArrangement —
                        // justify-only, so column-gap was dropped.
                        horizontalArrangement = axes.mainHorizontal,
                        verticalArrangement = axes.crossVertical
                    ) {
                        RenderContent(component, textColor, displayConfig)
                    }
                    return
                }
                com.styleconverter.runtime.layout.flexbox.FlexContainerKind.FlowColumn -> {
                    // NOT a silent fallthrough: the column flavour keeps
                    // FlowColumn deliberately. Its cross axis is INLINE,
                    // and the web reference this platform is compared
                    // against gives every unsized child `width:
                    // fit-content` (apps/web-harness ComponentRenderer.tsx)
                    // — a definite cross size, so §8.3 stretch degrades to
                    // flex-start there too. That is the same rationale
                    // columnCrossPlacements already carries for the
                    // non-wrapping column path; implementing wrap-stretch
                    // here would make Android the ONLY platform that
                    // stretches. TODO(wave26): revisit together with the
                    // harness's fit-content calibration.
                    FlowColumn(
                        modifier = modifier,
                        // CAL-RC2: was flexDecision.verticalArrangement —
                        // justify-only, so row-gap was dropped.
                        verticalArrangement = axes.mainVertical,
                        horizontalArrangement = axes.crossHorizontal
                    ) {
                        RenderContent(component, textColor, displayConfig)
                    }
                    return
                }
                // Box / None already handled above (None) or fall through to
                // the legacy switch below (Box).
                else -> Unit
            }
        }
        when (displayConfig.type) {
            DisplayType.NONE -> {
                // Don't render anything
            }
            DisplayType.FLEX_ROW -> {
                // Check for overflow scroll
                val overflowConfig = OverflowExtractor.extractOverflowConfig(
                    component.properties.map { it.type to it.data }
                )

                if (displayConfig.flexWrap == FlexWrap.WRAP || displayConfig.flexWrap == FlexWrap.WRAP_REVERSE) {
                    // Use FlowRow for wrapping flex layouts
                    FlowRow(
                        modifier = modifier,
                        horizontalArrangement = displayConfig.toRowArrangement(),
                        verticalArrangement = Arrangement.spacedBy(displayConfig.rowGap)
                    ) {
                        RenderContent(component, textColor, displayConfig)
                    }
                } else {
                    // Apply horizontal scroll if overflow-x is scroll
                    val rowModifier = if (overflowConfig.isScrollableX) {
                        modifier.horizontalScroll(rememberScrollState())
                    } else {
                        modifier
                    }
                    // Hoisted so the intrinsic flex path shares the exact
                    // arrangement/alignment the Row itself uses.
                    val legacyRowArrangement = displayConfig.toRowArrangement()
                    val legacyRowAlignment = displayConfig.alignItems.toRowAlignment()
                    Row(
                        modifier = rowModifier,
                        horizontalArrangement = legacyRowArrangement,
                        verticalAlignment = legacyRowAlignment
                    ) {
                        RenderRowContent(
                            component, textColor,
                            mainArrangement = legacyRowArrangement,
                            crossAlignment = legacyRowAlignment
                        )
                    }
                }
            }
            DisplayType.FLEX_COLUMN -> {
                // Check for overflow scroll
                val overflowConfig = OverflowExtractor.extractOverflowConfig(
                    component.properties.map { it.type to it.data }
                )

                if (displayConfig.flexWrap == FlexWrap.WRAP || displayConfig.flexWrap == FlexWrap.WRAP_REVERSE) {
                    // Use FlowColumn for wrapping flex layouts
                    FlowColumn(
                        modifier = modifier,
                        verticalArrangement = displayConfig.toColumnArrangement(),
                        horizontalArrangement = Arrangement.spacedBy(displayConfig.columnGap)
                    ) {
                        RenderContent(component, textColor, displayConfig)
                    }
                } else {
                    // Apply vertical scroll if overflow-y is scroll
                    val columnModifier = if (overflowConfig.isScrollableY) {
                        modifier.verticalScroll(rememberScrollState())
                    } else {
                        modifier
                    }
                    // Hoisted for the intrinsic flex path, as in the Row twin.
                    val legacyColumnArrangement = displayConfig.toColumnArrangement()
                    val legacyColumnAlignment = displayConfig.alignItems.toColumnAlignment()
                    Column(
                        modifier = columnModifier,
                        verticalArrangement = legacyColumnArrangement,
                        horizontalAlignment = legacyColumnAlignment
                    ) {
                        RenderColumnContent(
                            component, textColor,
                            mainArrangement = legacyColumnArrangement,
                            crossAlignment = legacyColumnAlignment
                        )
                    }
                }
            }
            DisplayType.GRID -> {
                // Grid handled separately by GridApplier
                GridRenderer.RenderGrid(
                    component = component,
                    modifier = modifier,
                    displayConfig = displayConfig,
                    textColor = textColor
                )
            }
            DisplayType.TABLE -> {
                // Table layout
                val tableConfig = TableExtractor.extractTableConfig(
                    component.properties.map { it.type to it.data }
                )
                TableApplier.Table(
                    config = tableConfig,
                    modifier = modifier
                ) {
                    RenderTableContent(component, textColor)
                }
            }
            DisplayType.MULTI_COLUMN -> {
                // Multi-column layout
                val columnConfig = MultiColumnExtractor.extractMultiColumnConfig(
                    component.properties.map { it.type to it.data }
                )
                // ── Wave-35 lane B3: css-multicol-1 §6.2 DESCENDANT spanner
                // promotion (columns/MulticolDescendantSpanner.kt). A
                // `column-span: all` element one level down was classified as
                // an ordinary column item, and its ink-free wrapper carries
                // the browser's POST-hoist used height of 0 — which Compose's
                // Modifier.height(0.dp) propagates as a hard 0 max-height onto
                // the spanner, collapsing it (ancestor-toggle-spanner-001
                // captured pure RED where every other platform is GREEN).
                // The rewrite lifts the descendant's own ColumnSpan onto the
                // wrapper and drops the clamping sizing declarations; the
                // wrapper STAYS in the tree, so the positioned-ancestor
                // channel CanvasRootHoist mirrors is untouched. IDENTITY for
                // every other container, so the frozen corpus is byte-stable.
                // Resolved ONCE per instance (same remember{} discipline as
                // ContentsUnboxing at RenderComponent's entry) so both the
                // role classification and the content pass see one tree, and
                // capture-gated like the wave-21 hook it feeds: the dark-stage
                // 327-pair corpus never sets LocalWptCaptureMode, so its
                // multicol containers take the identity branch provably.
                val multicolCapture = LocalWptCaptureMode.current
                val multicolComponent = androidx.compose.runtime.remember(component, multicolCapture) {
                    if (multicolCapture)
                        com.styleconverter.runtime.columns.MulticolDescendantSpanner.resolve(component)
                    else component
                }
                MultiColumnApplier.MultiColumnLayout(
                    config = columnConfig,
                    modifier = modifier,
                    // Wave-21 lane MULTICOL wiring hook (the only renderer
                    // change): per-child spanner/abspos/flow roles plus the
                    // declared-block-size flag, in RenderContent's measurable
                    // order (leading _text first). Null for child-less
                    // containers; the applier gates the plan behind
                    // LocalWptCaptureMode itself.
                    childSpecs = multicolComponent.children?.let {
                        com.styleconverter.runtime.columns.MulticolSpannerFlow
                            .specsFor(it, !multicolComponent._text.isNullOrEmpty())
                    }
                ) {
                    RenderContent(multicolComponent, textColor, displayConfig)
                }
            }
            DisplayType.INLINE -> {
                Row(
                    modifier = modifier,
                    horizontalArrangement = displayConfig.toRowArrangement()
                ) {
                    RenderContent(component, textColor, displayConfig)
                }
            }
            else -> {
                // CSS block flow stacks children VERTICALLY. Compose Box
                // overlays its children at contentAlignment instead, which
                // superimposed every sibling of a block parent on top of
                // each other — the inheritance-typography IT_* cards drew
                // both `_text` children in the SAME 30px band (garbled
                // double-struck glyphs, card 50px tall vs web's 80px,
                // SSIM 0.784). Use a Column (no spacing — block boxes butt
                // together margin-collapse aside) when real children exist;
                // keep the Box for leaf/placeholder rendering where
                // contentAlignment still matters.
                if (!component.children.isNullOrEmpty()) {
                    // CSS2 §8.3.1 margin collapse for the block child loop:
                    // build the container's collapse plan (max() rule between
                    // siblings, edge margins hoisted through a padding-0 /
                    // border-0 parent). Gated to true BLOCK containers — the
                    // else-branch can also catch residual display values, and
                    // §8.3.1 only applies to block flow.
                    // Lane UAM (BD-RC3): in WPT capture the plan ALSO folds
                    // each child's UA default block margins (the browser-ref
                    // renders with its UA sheet intact — a `<p>` child keeps
                    // its 1em). LocalWptCaptureMode is false on every
                    // property-fixture path, so the 327 dark-stage baselines
                    // take the identity branch and stay byte-identical.
                    val collapse =
                        if (displayConfig.type == DisplayType.BLOCK)
                            blockCollapsePlanFor(component, LocalWptCaptureMode.current)
                        else CollapsePlanResult(null, null)
                    // No-silent-fallthrough: an in-scope-looking container we
                    // skipped for an unemulated value flavor logs once.
                    collapse.fallbackReason?.let { logCollapseFallbackOnce(it, component.id) }
                    val plan = collapse.plan
                    if (plan != null) {
                        // Wave 26 (lane RES residual 3a): a composed ROOT's
                        // band is owned by the harness's root-stack gap fold
                        // (it folds `composedRootHoistBand` into the same
                        // §8.3.1 max() as the root's own margin), so emitting
                        // it here too would ADD where the browser takes ONE
                        // max. The channel names the exact root's id, and the
                        // extractor's ids are hierarchical, so a nested
                        // container never matches and keeps its band
                        // byte-identically without any reset plumbing.
                        val suppressBand = com.styleconverter.runtime.spacing
                            .BlockMarginCollapse.suppressesHoistBand(
                                com.styleconverter.runtime.spacing.BlockMarginCollapse
                                    .LocalHoistBandSuppressedFor.current,
                                component.id,
                            )
                        // Hoisted edge margins become TRANSPARENT spacing
                        // OUTSIDE the whole style chain (`modifier` carries
                        // the parent's own margin → size → bg): chaining the
                        // padding first means the background modifier inside
                        // paints only below/above it — the browser's
                        // collapse-through geometry where the escaped margin
                        // sits outside the parent's border box.
                        Column(
                            modifier = Modifier
                                .padding(
                                    top = if (suppressBand) 0.dp else plan.hoistTopPx.dp,
                                    bottom = if (suppressBand) 0.dp else plan.hoistBottomPx.dp,
                                )
                                .then(modifier)
                        ) {
                            // Publish the per-child applied margins for
                            // RenderContent's index-aligned child loop. No
                            // suppression reset is needed here — the channel
                            // is identity-keyed (see suppressesHoistBand).
                            CompositionLocalProvider(LocalBlockCollapsePlan provides plan) {
                                RenderContent(component, textColor, displayConfig)
                            }
                        }
                    } else {
                        // No plan (margin-less children, or fallback): the
                        // pre-collapse path, byte-identical to the frozen
                        // baseline.
                        Column(modifier = modifier) {
                            RenderContent(component, textColor, displayConfig)
                        }
                    }
                } else {
                    Box(
                        modifier = modifier,
                        contentAlignment = displayConfig.alignItems.toBoxAlignment()
                    ) {
                        RenderContent(component, textColor, displayConfig)
                    }
                }
            }
        }
    }

    /**
     * Render table content - rows and cells.
     *
     * ## Wave 32 (lane P) — two corrections, both of which were DROPPING boxes
     *
     * **(1) Row groups are spliced (css-tables-3 §2.1).** HTML parsing
     * inserts an implied `<tbody>`, so the extracted wire is
     * table → TABLE_ROW_GROUP → TABLE_ROW → TABLE_CELL. The pre-wave-32
     * loop read the tbody as a row and the tr as a cell, putting every real
     * `<td>` one level below the code that looks for it.
     * [TableBoxTree.rowsOf] returns `children` UNCHANGED when no group is
     * present, so every table that already hands rows directly (the shape
     * most extracted fixtures carry, e.g. css-tables/absolute-tables-016)
     * is byte-identical to the frozen baseline.
     *
     * **(2) The positioned-ancestor mirror is restored.** This function
     * renders GRANDCHILDREN — `RenderComponent(cellComponent)` — so the
     * intermediate row/cell component's own `RenderComponent` never runs,
     * and neither does the `LocalHasPositionedAncestor` provider it would
     * have installed. `CanvasRootHoist` requires the composition side and
     * the pure walk (`collectCanvasHoisted`, which reads the REAL IR tree)
     * to answer that question identically — its kdoc: "the two MUST mirror
     * each other or a component gets dropped from flow but never overlaid."
     * On css-tables/abspos-container-change-dynamic-001 they disagreed
     * about the `position: relative` `<td>`: the composition side saw no
     * positioned ancestor and hoisted the lime abspos away, while the walk
     * saw one and gave it no overlay slot. Result: Android's capture held
     * 160 non-white pixels (the "A"/"B" glyphs) against the reference's
     * 10 070. Publishing the flag for each synthesized level puts both
     * sides back on the same answer.
     */
    @Composable
    private fun RenderTableContent(component: IRComponent, textColor: Color?) {
        // The table's own row list, with any row group spliced away.
        val rows = com.styleconverter.runtime.table.TableBoxTree.rowsOf(component.children)
        // Does the TABLE box itself establish the containing block? Read
        // through the hoist's own predicate so this can never drift from
        // the pure walk. OR-ed with whatever the enclosing context already
        // published, exactly as RenderComponent's childHasPositionedAncestor
        // does one level up.
        val tableEstablishes = com.styleconverter.runtime.layout.position.CanvasRootHoist
            .LocalHasPositionedAncestor.current ||
            com.styleconverter.runtime.table.TableBoxTree.establishesContainingBlock(component)
        if (rows.isNotEmpty()) {
            rows.forEach { rowComponent ->
                // Each child is a table row. A row group that was spliced
                // away cannot itself be positioned in this corpus, but the
                // ROW can be — thread it the same way.
                val rowEstablishes = tableEstablishes ||
                    com.styleconverter.runtime.table.TableBoxTree
                        .establishesContainingBlock(rowComponent)
                TableApplier.TableRow {
                    if (!rowComponent.children.isNullOrEmpty()) {
                        rowComponent.children.forEach { cellComponent ->
                            // Each grandchild is a table cell. The cell's
                            // OWN RenderComponent runs below and publishes
                            // its own flag for the cell's subtree; what this
                            // provider supplies is the ancestry the skipped
                            // table/row levels owe it.
                            TableCell {
                                CompositionLocalProvider(
                                    com.styleconverter.runtime.layout.position.CanvasRootHoist
                                        .LocalHasPositionedAncestor provides rowEstablishes
                                ) {
                                    RenderComponent(cellComponent)
                                }
                            }
                        }
                    } else {
                        // Single cell with row content
                        TableCell {
                            PlaceholderContent(rowComponent.name, textColor, rowComponent.properties)
                        }
                    }
                }
            }
        } else {
            // No children - show placeholder
            TableApplier.TableRow {
                TableCell {
                    PlaceholderContent(component.name, textColor, component.properties)
                }
            }
        }
    }

    /**
     * Render content - children or placeholder.
     * Handles position:absolute/fixed children with proper stacking.
     *
     * Bug 1 (mixed content) — see
     * testing/titan/investigations/swarm-002/css-text-decor__text-decoration-decorating-box-thickness-001.json
     * When the parent carries both `_text` AND children, render the text
     * as a leading sibling so the parent's text-decoration / color has a
     * glyph stream to attach to (matches the web hasChildren+hasText
     * branch). Without this fix, mixed-content nodes like
     * <div>abc <span>x</span> def</div> rendered only the child glyph.
     *
     * Bug 2 (list markers) — see
     * testing/titan/investigations/swarm-002/css-counter-styles__css3-counter-styles-101.json
     * When the parent `_tag` is `ol`/`ul` and a child is `li`, prepend a
     * simple-numeric/disc marker via ListStyleApplier.buildMarkedText so
     * Compose-rendered lists show "1. foo / 2. bar / 3. baz" instead of
     * just the body glyphs (Android has no native ::marker pseudo).
     */
    @Composable
    private fun RenderContent(component: IRComponent, textColor: Color?, displayConfig: DisplayConfig? = null) {
        // ── wave-30 lane-3 OWN-DISPLAY marker (fix B1, see
        // lists/ListItemMarkerGate.kt). css-lists-3 §3.1 attaches the
        // ::marker to the BOX, not the tag: a `display: list-item` element
        // that is not an `<li>` under a list container generates one too,
        // and both natives painted nothing for it. Emitted FIRST — ahead of
        // the root ::before below — because CSS orders ::marker before
        // ::before (the same ordering web's NodeRenderer spells out at its
        // `markerNode` positional argument). Null for every component that
        // is not a self-marking `inside` list item, i.e. for the entire
        // 327-pair dark stage and every tagged `<li>` (those keep the
        // parent-loop RenderListItemMarker path).
        RenderOwnListMarker(component, textColor)
        // ── wave-28 lane-PG ROOT-SCOPE generated box (see RootPseudoBox.kt).
        // This function is the ONE content emitter every layout branch calls,
        // so emitting here puts the box FIRST in the container's flow —
        // exactly where web's NodeRenderer puts its ::before span and where
        // SwiftUI's contentOrPlaceholder puts its twin. Null for every
        // component that is not a body-root carrying a paintable `pseudos`
        // bucket, i.e. for the entire 327-pair dark stage and for every
        // ordinary element's ::before (those still ride ContentApplier).
        rootPseudoSpecFor(component, "before")?.let { spec ->
            RootPseudoBox(
                spec = spec,
                // css-contain-1 §3.1: a contained body does not propagate its
                // direction to the viewport, so the root-owned box keeps the
                // root's own `ltr` and sits physically left.
                pinInlineStart = containmentBlocksDirectionPropagation(containKeywordsOf(component)),
            )
        }
        // (A root-scope `::after` cannot ride this LEADING emitter; the gap
        // is reported once by rootPseudoSpecFor, not silently dropped.)
        // ── Wave-20 lane-W2 UA-widget mount hook (the ONLY renderer entry
        // for widget content). WPT capture only: a form-control component
        // (meta.sourceTag + meta.attrs, css-ui-4 §7 appearance ≠ none)
        // paints its Chromium-lookalike replica INSTEAD of text/children
        // content — painting lives in widgets/UAWidgets*. Dark stage:
        // LocalWptCaptureMode is false on every 327-pair path → no-op.
        if (LocalWptCaptureMode.current) {
            com.styleconverter.runtime.widgets.UAWidgetsResolve.resolve(component)
                ?.let { spec -> com.styleconverter.runtime.widgets.UAWidgets.Render(spec); return }
        }
        if (!component.children.isNullOrEmpty()) {
            // Bug 1: leading _text node — wrapped in a Box so it sits as a
            // sibling of the children. PlaceholderContent uses the parent's
            // properties for text colour/font/decoration so the inline run
            // inherits the parent's styling, matching the web <span>
            // fallback.
            val parentText = component._text
            // Wave 9: leading `_text` renders through a PLAIN inheriting
            // <span> on web, so an inherited-only Color DOES reach these
            // glyphs — unlike the leaf placeholder. `textColor` arrives
            // null when Color is inherited-only (RenderComponent's gate),
            // so re-extract from the merged list (component.properties
            // here IS the inheritance-merged list — see mergedComponent).
            val inheritedAwareTextColor = textColor
                ?: runCatching { TextStyleApplier.extractTextColor(component.properties) }.getOrNull()
            // Wave 27 (lane NMARK, B-RC8) — the marker's font, resolved
            // from the SAME inheritance-merged list one line above
            // resolves its colour. Computed here rather than inside
            // RenderListItemMarker so both marker call sites below share
            // one resolution (and one failure mode: a malformed
            // typography payload falls back to the empty TextStyle, which
            // is exactly the pre-wave-27 Material default — no marker
            // ever disappears because of this line).
            //
            // SKEPTIC FIX (wave 27): `inheritedFontSizeSp` is threaded, as
            // it is at EVERY other extractTextStyle call site (line ~3940,
            // ContentApplier). Without it a RELATIVE font-size on the
            // container (`em` / `%` / `larger` — the wire leaves those
            // unresolved by design, CLAUDE.md's "null means
            // runtime-dependent") resolved against the extractor's
            // hard-coded 16sp browser default while the container's OWN
            // text resolved the same declaration against the real
            // inherited base, so marker and item disagreed — exactly the
            // divergence this lane set out to remove.
            val inheritedMarkerFontSizeSp =
                com.styleconverter.runtime.core.variables.DynamicValueResolver
                    .fontSizePxOf(LocalInheritedProperties.current)
            val inheritedMarkerTextStyle = runCatching {
                TextStyleApplier.extractTextStyle(
                    component.properties, inheritedMarkerFontSizeSp)
            }.getOrDefault(TextStyle()).let { base ->
                // Wave 29 (lane MP) — the marker's LINE BOX, pinned to the
                // same one [PlaceholderContent] gives the item's own text.
                // Extraction leaves `lineHeight` Unspecified whenever no
                // `line-height` was declared, and the marker `Text` was the
                // only run in the runtime that then fell through to the
                // resolved FACE's natural metrics instead of the three-state
                // resolution every other run makes. A Row is as tall as its
                // tallest child, so a marker box taller than the item's
                // pinned box became the ROW's height: measured +2.75px on
                // EVERY row of css3-counter-styles-007 (34px pitch vs web's
                // 31–32 and iOS's 31), accumulating to a 920px capture
                // against web's 885. See ListMarkerLineBox for the
                // on-device isolation. Resolved HERE, beside the
                // colour and the font, so both marker call sites below share
                // one resolution and the CompositionLocals are read once.
                base.copy(lineHeight = ListMarkerLineBox.resolve(
                    declaredLineHeight = base.lineHeight,
                    fontSize = base.fontSize,
                    declaredNormal = com.styleconverter.runtime.typography
                        .LineHeightNormal.isDeclaredNormal(component.properties),
                    wptCapture = LocalWptCaptureMode.current,
                    composedWpt = LocalWptComposedMode.current))
            }
            // ── Wave 32 (lane R): the inline anonymous-run box ──────────────
            //
            // `meta.runs` is the component's inline content in DOCUMENT order
            // (spec 03 §4.1) — the shape the single `_text` string cannot
            // express, and the one the CSS2 static-position family is decided
            // by. When a plan resolves it is AUTHORITATIVE: the leading-text
            // box below is suppressed (its string is the concatenation the
            // runs were split FROM, so painting both would double the glyphs)
            // and each referenced child renders AT ITS RUN SLOT.
            //
            // SCOPE, stated rather than silent. The plan is consumed by the
            // PLAIN block loop only. A positioned container (lane P's Box
            // branch) and the two composed-capture packing layouts (float
            // rows, inline atoms) index their subviews against
            // `component.children` and would have to learn a synthetic
            // measurable to host a run — that is a layout-contract change,
            // not an ordering one. For those three, [inlineRunPlan] resolves
            // to null and the container keeps the pre-wave-32 behaviour
            // (leading text, then children) EXACTLY, so nothing is lost or
            // painted twice; what is lost is the ordering fix, and the
            // fixture still carries `_runs` for the platforms that read it.
            val inlineRunPlan = run {
                if (component.runs.isNullOrEmpty()) return@run null
                // Lane P's branch: subviews are placed by the overlay/z-order
                // walk, not this loop.
                if (extractPositionType(component.properties) == PositionType.RELATIVE) return@run null
                // The two packing layouts, recomputed here from the SAME pure
                // functions the block branch calls below (both are cheap and
                // side-effect-free), so the suppression decision above and the
                // consumption decision further down can never disagree.
                if (LocalWptCaptureMode.current) {
                    if (blockFloatSegments(component.children, component.properties) != null) return@run null
                    if (blockInlineAtomSegments(component.children) != null) return@run null
                }
                InlineRunPlan.resolve(component.runs, component.children)
            }
            if (!parentText.isNullOrEmpty() && inlineRunPlan == null) {
                PlaceholderContent(
                    name = parentText,
                    textColor = inheritedAwareTextColor,
                    properties = component.properties,
                    rawText = parentText,
                    // Wave 22 (lane DECOR): this run renders the COMPONENT's
                    // own text, so it owns the component's `meta.decorations`
                    // list too — the mixed-content sibling of the leaf site
                    // below. Null for every non-collapsed component.
                    decorations = com.styleconverter.runtime.typography.DecorationWire
                        .toDecorationLines(component.decorations)
                )
            }

            // Bug 2 / wave-24 lane LF (B-RC3 parts 1+2): the marker family
            // is no longer derived from the parent's source tag ALONE. The
            // tag only supplies the UA default (HTML §15.3.9); the item's
            // own `list-style-*` declarations — which is where the live
            // wire actually puts them (css-lists change-list-style-type-001
            // carries ListStyleType square/none/upper-roman/decimal on each
            // <li>, ListStylePosition INSIDE on the <ul>) — override it in
            // ListStyleExtractor.resolveMarkerConfig, per child.
            val parentTag = component._tag?.lowercase()
            val isListParent = ListStyleExtractor.uaMarkerDefault(parentTag) != null
            // The parent's list-style declarations, hoisted once (the loop
            // below runs per child). `component.properties` here is the
            // INHERITANCE-MERGED list (mergedComponent — the four
            // list-style types sit in INHERITED_PROPERTY_TYPES), so a
            // declaration made on an ancestor rather than on the <ul>
            // itself still reaches the marker. Empty for non-list parents.
            val parentListPairs: List<Pair<String, kotlinx.serialization.json.JsonElement?>> =
                if (isListParent) {
                    component.properties
                        .filter { ListStyleExtractor.isListStyleProperty(it.type) }
                        .map { it.type to it.data }
                } else emptyList()

            // Check if this is a positioned container (position: relative)
            // Wave 35 (lane B1) — OR-ed in: a box with a used transform is a
            // containing block for its out-of-flow descendants exactly like a
            // `position: relative` one (css-transforms-1 §3 / css-transforms-2
            // §6), so its abspos children must take the SAME Box + anchored
            // mount instead of stacking as ordinary Column siblings.
            //
            // Measured need — backface-visibility-hidden-001. Its `.card` is a
            // STATIC `transform-style: preserve-3d` box holding two abspos
            // 100×100 faces at top/left 50px. Chromium resolves both against
            // `.card` (probe: _diag35/laneB1/probe-icb-shape.mjs reports
            // cb=card for both faces), i.e. they OVERLAP. Without this clause
            // the wave-35 hoist veto drops them into `.card`'s block Column,
            // where the first face reserves 100px and the second lands 100px
            // lower — strictly worse than the hoist it replaces.
            //
            // Blast radius, enumerated before the change
            // (_diag35/laneB1/scan-tfparent.mjs over all 29 frozen sections):
            // FIVE components in the whole corpus are transform-CB containers
            // with out-of-flow children, and FOUR of them are already
            // `position: relative` — so this OR flips exactly ONE container,
            // backface-visibility-hidden-001's `.card`. Nothing in the
            // dark-stage fixtures carries the shape at all (scan-fixtures.mjs).
            //
            // The `hasOutOfFlowChild` conjunct is what keeps that number at
            // one: this whole branch swaps the block Column for a
            // `Box(fillMaxSize())`, so a transform-CB container with only
            // IN-FLOW children (every `transform:` box in the corpus with
            // kids) must keep its block layout untouched. The out-of-flow
            // child is both the reason the branch exists and its gate.
            val transformCbHostsOutOfFlowChild =
                com.styleconverter.runtime.layout.position.CanvasRootHoist
                    .establishesTransformContainingBlock(component.properties) &&
                    component.children.any { isOutOfFlowChild(it.properties) }
            val isPositionedContainer =
                extractPositionType(component.properties) == PositionType.RELATIVE ||
                    transformCbHostsOutOfFlowChild

            if (isPositionedContainer) {
                // CSS 2.1 §9.9.1 / Appendix E: a child with NEGATIVE z-index
                // paints BEHIND its stacking context's background. Compose
                // draws the parent's background modifier before any child,
                // so a Modifier.zIndex(-1) child still painted ABOVE it
                // (PL_ZNegative: web hides the teal box behind the parent's
                // opaque bg, Android showed it — A-w 0.931, iOS shared).
                // Emulation: when a positioned child claims z < 0, re-paint
                // the parent's background as a z=0 sibling layer. Ties at
                // z=0 resolve by placement order, so in-flow siblings
                // (placed after the layer) stay on top while negative-z
                // children sink below it — exactly the browser's paint
                // order for an opaque background.
                val negativeZBackdrop: Color? = run {
                    val hasNegativeZChild = component.children.any { child ->
                        extractPositionType(child.properties) != PositionType.STATIC &&
                            (com.styleconverter.runtime.core.placement.ItemPlacementExtractor
                                .zIndex(child.properties) ?: 0) < 0
                    }
                    if (!hasNegativeZChild) null
                    else component.properties.firstOrNull { it.type == "BackgroundColor" }
                        ?.data?.let { ValueExtractors.extractColor(it) }
                        // Only fully-opaque backgrounds can be re-painted
                        // losslessly (a translucent layer would double-blend).
                        ?.takeIf { it.alpha == 1f }
                }
                // Render with Box to support absolute positioning
                Box(modifier = Modifier.fillMaxSize()) {
                    if (negativeZBackdrop != null) {
                        // The backdrop layer: same paint as the parent bg,
                        // placed FIRST so every z>=0 sibling still wins ties.
                        Box(
                            modifier = Modifier.fillMaxSize()
                                .zIndex(0f)
                                .background(negativeZBackdrop)
                        )
                    }
                    component.children.forEachIndexed { index, child ->
                        val childPosition = extractPositionType(child.properties)
                        if (childPosition == PositionType.ABSOLUTE || childPosition == PositionType.FIXED) {
                            // Render absolutely positioned child with offset
                            RenderAbsoluteChild(child)
                        } else if (isListParent && child._tag?.lowercase() == "li") {
                            RenderListItemMarker(
                                child, index, parentTag, parentListPairs,
                                inheritedAwareTextColor, inheritedMarkerTextStyle)
                        } else {
                            RenderComponent(child)
                        }
                    }
                }
            } else {
                // §8.3.1 collapse plan published by the block branch above —
                // index-aligned with this exact children list. Null for every
                // non-collapsing container (and always null for list/relative
                // parents, which the plan builder gates out, so the two other
                // branches here never need it).
                val collapsePlan = LocalBlockCollapsePlan.current
                // The per-child block renderer, factored so the wave-19
                // float-run segmentation below can re-use it for SINGLE
                // segments byte-identically (index-aligned with the
                // original children list — collapse plan + list markers
                // keep their indices).
                // Wave 30 (lane 3, fix B3) — the row-pitch residual carrier
                // for this stacking container. `current ?: remember{…}` is
                // the OUTERMOST-owns rule spelled out in ComposedRowPitch:
                // a nested container inherits its ancestor's accumulator, so
                // a row's prefix is every line box stacked above it in the
                // capture, not just within its immediate parent. That is the
                // shape css3-counter-styles-007 needs — its 24 rows are 24
                // separate `<ol>`s under one wrapper, each an only child.
                // Null outside composed WPT capture, where no provider is
                // installed and the isolated per-row rounding stands.
                val rowPitchAccumulator =
                    if (LocalWptComposedMode.current)
                        com.styleconverter.runtime.lists.LocalRowPitchAccumulator.current
                            ?: androidx.compose.runtime.remember {
                                com.styleconverter.runtime.lists.RowPitchAccumulator()
                            }
                    else null
                val renderBlockChild: @Composable (Int, IRComponent) -> Unit = { index, child ->
                    // Provided only when non-null so the dark-stage 327 pairs
                    // and the per-component inbox path keep their exact
                    // composition shape, not merely their pixels.
                    ProvidingRowPitch(rowPitchAccumulator) {
                    if (isListParent && child._tag?.lowercase() == "li") {
                        RenderListItemMarker(
                            child, index, parentTag, parentListPairs,
                            inheritedAwareTextColor, inheritedMarkerTextStyle)
                    } else {
                        // Auto-margin centering for block children is handled
                        // inside RenderComponent's self-alignment wrapper (one
                        // implementation covers tree children AND root-level
                        // standalone captures).
                        val collapsed = collapsePlan?.perChild?.getOrNull(index)
                        if (collapsed != null) {
                            // Hand the child its post-collapse applied
                            // block-axis margins; the child's RenderComponent
                            // reads the local and passes it into its style
                            // chain (and resets it for ITS children).
                            CompositionLocalProvider(
                                com.styleconverter.runtime.spacing.BlockMarginCollapse
                                    .LocalCollapsedMargin provides collapsed
                            ) {
                                RenderComponent(child)
                            }
                        } else {
                            RenderComponent(child)
                        }
                    }
                    }
                }
                // Wave-19 lane FLOAT — CSS 2.1 §9.5 float row packing,
                // composed-WPT capture ONLY (pin P8): consecutive
                // left-floating block siblings pack side-by-side instead
                // of stacking in the Column (descendant-static-position-
                // 001 green+grey pair, justify-self-001's 3/2/5/4 rows).
                // The dark-stage 327 corpus never sets the capture local,
                // so its block child loop stays byte-identical.
                val floatSegments =
                    if (LocalWptCaptureMode.current)
                        blockFloatSegments(component.children, component.properties)
                    else null
                // Wave-20 W3 — inline-level atom flow (css-ui widget rows):
                // only consulted when no float plan exists, same capture
                // gate; run-free containers fall through to the frozen loop.
                val inlineSegments =
                    if (LocalWptCaptureMode.current && floatSegments == null)
                        blockInlineAtomSegments(component.children, component.properties)
                    else null
                if (floatSegments != null) {
                    // Segment walk, sibling order preserved: runs render
                    // through the FloatRowLayout adapter (pure packing,
                    // unbounded measure — pins P3-P7), singles keep the
                    // exact per-child path above. Note floats never
                    // margin-collapse (§8.3.1 / collapse-plan bail B3),
                    // so collapsePlan is provably null whenever a run
                    // exists — run children can skip the collapse local.
                    floatSegments.forEach { seg ->
                        if (seg.isRun) {
                            com.styleconverter.runtime.layout.FloatRowLayout(
                                strutted = seg.strutted,
                                // P12/P13 — right runs take the mirrored
                                // packing + right-edge anchoring.
                                rightPacked = seg.rightRun
                            ) {
                                // Run members render their full style
                                // chains; the adapter owns geometry.
                                // Wave-20 W3: placement is the PACKER's —
                                // suppress the per-child block self-
                                // alignment wrapper (floatEndAlignment's
                                // TopEnd fallback, ~2280 below) for RIGHT
                                // run members so the box is never aligned
                                // twice. Left runs keep the wave-19
                                // composition byte-identically (their
                                // fallback is null anyway).
                                if (seg.rightRun) {
                                    CompositionLocalProvider(
                                        LocalSelfAlignmentHandled provides true
                                    ) {
                                        seg.indices.forEach { i ->
                                            RenderComponent(component.children[i])
                                        }
                                    }
                                } else {
                                    seg.indices.forEach { i ->
                                        RenderComponent(component.children[i])
                                    }
                                }
                            }
                        } else {
                            // Single segment — one child, original index.
                            val i = seg.indices.first()
                            renderBlockChild(i, component.children[i])
                        }
                    }
                } else if (inlineSegments != null) {
                    // Wave-20 W3 — inline atom runs (appearance-auto-001's
                    // widget rows): consecutive inline-level atoms pack
                    // horizontally with width-exhaustion wrapping and
                    // baseline-ish alignment (pins P15-P18); singles keep
                    // the exact per-child path above, original indices.
                    inlineSegments.forEach { seg ->
                        if (seg.isRun) {
                            // Resolve each member's atom spec ONCE from the
                            // shared UA geometry table (W2 paints inside
                            // the same boxes — UAWidgetIntrinsics is the
                            // coordination point).
                            val specs = seg.indices.map { i ->
                                // Wave-33 C2 — a DECLARED inline-block
                                // carries its geometry on the wire, so its
                                // spec comes from the same predicate that
                                // admitted it to the run; everything else
                                // resolves through the UA table as before.
                                inlineBlockAtomSpec(component.children[i], component.properties)
                                    ?: com.styleconverter.runtime.layout.UAWidgetIntrinsics
                                        .spec(atomKindOf(component.children[i]))
                            }
                            com.styleconverter.runtime.layout.InlineFlowLayout(
                                atoms = specs
                            ) {
                                // Atoms are OPAQUE: their content renders
                                // through the normal chain (W2's widget
                                // painting mounts inside RenderComponent);
                                // this lane owns only their placement.
                                //
                                // Wave 26 (lane RES residual 3b): each member
                                // still gets its §8.3.1 APPLIED margins from
                                // the container's plan, exactly like
                                // renderBlockChild above. The float-run branch
                                // may skip this because a float can never
                                // margin-collapse (bail B3 ⇒ the plan is
                                // provably null whenever a float run exists);
                                // an inline ATOM has no such guarantee — bail
                                // B2 only fires on a DECLARED non-inline
                                // Display, while InlineAtomFlow.isAtom accepts
                                // an undeclared-Display <input>/<select>/
                                // <button>/<a>. So a `<div><p style=
                                // "margin:10px"><button><button></div>` builds
                                // a real plan AND an atom run: dropping the
                                // override rendered each button's own
                                // uncollapsed margins instead of the plan's
                                // max()-folded ones. Providing null is a no-op
                                // (RenderComponent's read is null-tolerant),
                                // so run-with-no-plan is byte-identical.
                                seg.indices.forEach { i ->
                                    CompositionLocalProvider(
                                        com.styleconverter.runtime.spacing
                                            .BlockMarginCollapse.LocalCollapsedMargin
                                            provides collapsePlan?.perChild?.getOrNull(i)
                                    ) {
                                        RenderComponent(component.children[i])
                                    }
                                }
                            }
                        } else {
                            // Single segment — one child, original index
                            // (collapse plan + list markers preserved).
                            val i = seg.indices.first()
                            renderBlockChild(i, component.children[i])
                        }
                    }
                } else if (inlineRunPlan != null) {
                    // Wave 32 (lane R) — the ordered inline content. Text runs
                    // and child boxes emit in WIRE order through the SAME
                    // per-child renderer the frozen loop uses (original
                    // indices, so the collapse plan and list markers keep
                    // theirs). A run is an anonymous inline box on the wire;
                    // Compose's Column has no line box, so it lands as a
                    // stacked text box — the same approximation the leading
                    // `_text` box always was, now at the right POSITION. See
                    // InlineRunPlan's "HONEST SCOPE" note.
                    inlineRunPlan.entries.forEach { entry ->
                        when (entry) {
                            is InlineRunPlan.Entry.Text -> PlaceholderContent(
                                name = entry.text,
                                textColor = inheritedAwareTextColor,
                                properties = component.properties,
                                rawText = entry.text,
                                // A run belongs to the COMPONENT's own text, so
                                // it carries the component's decoration list —
                                // exactly like the leading-text site above.
                                // (The extractor never co-emits the two: the
                                // wave-22 collapse drops `_runs` with the
                                // children it flattens, so this is null here.)
                                decorations = com.styleconverter.runtime.typography.DecorationWire
                                    .toDecorationLines(component.decorations)
                            )
                            is InlineRunPlan.Entry.Child ->
                                renderBlockChild(entry.index, component.children[entry.index])
                        }
                    }
                    // Rule 4 — children the list did not name still render,
                    // after the runs, in sibling order. Empty for every
                    // fixture the extractor emits (it references every kept
                    // child), so this loop is a no-op in practice and exists
                    // so a partial list can never make a box disappear.
                    inlineRunPlan.unreferenced.forEach { i ->
                        renderBlockChild(i, component.children[i])
                    }
                } else {
                    // No packable run (or dark stage) — the frozen loop.
                    component.children.forEachIndexed { index, child ->
                        renderBlockChild(index, child)
                    }
                }
            }
        } else {
            // Leaf branch — when _text is present render it verbatim
            // instead of the underscore-stripped component name. Matches
            // web PlaceholderContent.text and iOS rawText overrides.
            PlaceholderContent(
                name = component.name,
                textColor = textColor,
                properties = component.properties,
                rawText = component._text,
                // Wave 22 (lane DECOR): the LEAF site — where a collapsed
                // inline run lands (the extractor flattens the chain to one
                // childless text component). `meta.decorations` reaches the
                // per-line painter from here.
                decorations = com.styleconverter.runtime.typography.DecorationWire
                    .toDecorationLines(component.decorations)
            )
        }
    }

    /**
     * Install a [com.styleconverter.runtime.lists.RowPitchAccumulator] for
     * [content], or emit [content] verbatim when there is none.
     *
     * Wave 30 (lane 3, fix B3). The null arm is not an optimisation: it
     * keeps the COMPOSITION SHAPE of every non-composed-WPT path
     * byte-identical (no extra provider node per block child), which is
     * the same discipline the collapse-plan provider a few lines above
     * follows.
     */
    @Composable
    private fun ProvidingRowPitch(
        accumulator: com.styleconverter.runtime.lists.RowPitchAccumulator?,
        content: @Composable () -> Unit
    ) {
        if (accumulator == null) content()
        else CompositionLocalProvider(
            com.styleconverter.runtime.lists.LocalRowPitchAccumulator provides accumulator,
            content = content
        )
    }

    /**
     * The `::marker` a box generates from its OWN `display: list-item`
     * (css-lists-3 §3.1) — wave 30, lane 3 (fix B1). Twin of iOS's
     * `ComponentRenderer.ownListMarker`.
     *
     * The gate, the four predicates behind it and the MEASURED reason
     * `outside` is excluded all live in
     * [com.styleconverter.runtime.lists.ListItemMarkerGate]; this function
     * is only the paint.
     *
     * ## Why a LEADING LINE BOX and not a row
     * css-lists-3 §3.2 makes an `inside` marker the item's FIRST INLINE
     * BOX. The item's own in-flow content on all three runtimes is
     * BLOCK-level ([PlaceholderContent] for text, a Column of children
     * otherwise), so the marker can never share a line with it and owns a
     * line box of its own at the top of the item's content — which is
     * exactly what the web runtime produces (`1.` on one line, `text` on
     * the next, ink rows 46–65 / 76–82 of the live
     * change-list-style-position-003 web capture). It is NOT an overlay:
     * an overlay reports zero size and would leave the item's block
     * content 20px too high, which is the current defect. It is NOT a Row
     * either: a Row would put the marker BESIDE the item's content, which
     * neither the browser nor web does for block content.
     *
     * The `Box` is height-pinned to the resolved line box rather than
     * wrapping the glyph, so an item whose marker resolves through a
     * fallback face with a taller natural line cannot stretch its own
     * content down — the same rule [ListMarkerLineBox] imposes on the
     * marker rows.
     */
    @Composable
    private fun RenderOwnListMarker(component: IRComponent, textColor: Color?) {
        val pairs = component.properties.map { it.type to it.data }
        if (!com.styleconverter.runtime.lists.ListItemMarkerGate
                .rendersOwnLeadingMarker(component._tag, pairs)) return
        val config = com.styleconverter.runtime.lists.ListItemMarkerGate
            .ownMarkerConfig(pairs)
        val marker = com.styleconverter.runtime.lists.ListItemMarkerGate
            .ownMarkerText(pairs)
        // The marker inherits from its originating element (css-lists-3
        // §3.2) — which HERE is the component itself, not a container, so
        // this is the one marker call site with the exact font the browser
        // would use (the honest-scope gap ListMarkerTextStyle documents for
        // the row path does not apply).
        val inheritedFontSizeSp = com.styleconverter.runtime.core.variables
            .DynamicValueResolver.fontSizePxOf(LocalInheritedProperties.current)
        val baseStyle = runCatching {
            TextStyleApplier.extractTextStyle(component.properties, inheritedFontSizeSp)
        }.getOrDefault(TextStyle())
        val lineBox = ListMarkerLineBox.resolve(
            declaredLineHeight = baseStyle.lineHeight,
            fontSize = baseStyle.fontSize,
            declaredNormal = com.styleconverter.runtime.typography
                .LineHeightNormal.isDeclaredNormal(component.properties),
            wptCapture = LocalWptCaptureMode.current,
            composedWpt = LocalWptComposedMode.current
        )
        val markerStyle = ListMarkerTextStyle.forItem(
            baseStyle.copy(lineHeight = lineBox),
            textColor ?: runCatching {
                TextStyleApplier.extractTextColor(component.properties)
            }.getOrNull()
        )
        val density = androidx.compose.ui.platform.LocalDensity.current
        val markerFontSizePx = with(density) {
            (if (markerStyle.fontSize != TextUnit.Unspecified) markerStyle.fontSize
            else ListMarkerLineBox.DEFAULT_FONT_SIZE_SP.sp).toPx()
        }
        // Unspecified line box ⇒ no height pin, i.e. the declared-`normal`
        // state where the face's own metrics ARE the CSS answer — the same
        // three-state resolution every other run in the runtime makes.
        val lineBoxModifier =
            if (lineBox != TextUnit.Unspecified)
                Modifier.height(with(density) { lineBox.toDp() })
            else Modifier
        Box(modifier = lineBoxModifier, contentAlignment = Alignment.CenterStart) {
            Text(
                // Wave 34 (lane F1) — the ::marker run is exactly where the
                // non-Latin counter systems paint (css-counter-styles-3 §6
                // `armenian` / `arabic-indic` / `bengali` / `cambodian`), so
                // the per-script fallback spans have to reach THIS Text and
                // not only the item's own content. Identity outside WPT
                // capture and for any Latin/ASCII marker ("1.", "•").
                text = com.styleconverter.runtime.typography.font
                    .ScriptFallbackFonts.annotate(marker, LocalWptCaptureMode.current),
                style = markerStyle,
                // Shrink-to-fit ::marker box (css-lists-3 §3.2) — the same
                // fix B2 puts on the two row call sites.
                softWrap = false,
                modifier = com.styleconverter.runtime.lists.ListMarkerSymbol.paint(
                    type = config.listStyleType,
                    markerText = marker,
                    fontSizePx = markerFontSizePx,
                    color = markerStyle.color.takeOrElse {
                        androidx.compose.material3.LocalContentColor.current
                    }
                )
            )
        }
    }

    /**
     * Render a `<li>` child with its own synthesised marker. Compose has
     * no `::marker` pseudo, so we emit a Row(Text(marker) +
     * RenderComponent(child)). Marker counter uses the 1-based index from
     * the parent's children list — fine for the basic css-counter-styles
     * tests where the IR matches the source `<li>` ordering verbatim.
     *
     * Wave 24 (lane LF, B-RC3 parts 1+2): the marker family is resolved
     * PER ITEM through [ListStyleExtractor.resolveMarkerConfig] — UA
     * default from [parentTag], then the parent's (inheritance-merged)
     * declarations, then the item's OWN. It routes through
     * [StyleListApplier.getMarker], so the whole counter-style table
     * (decimal-leading-zero, upper-roman, armenian, hebrew, the kana
     * sets, …) is now reachable from the renderer instead of only
     * `•` / `n.`.
     *
     * Wave 28 (lane MC): `list-style-position` now CHANGES the placement
     * instead of only being resolved and carried. An `inside` marker on an
     * item with no in-flow text is painted INSIDE the item's box, through
     * a zero-size overlay that cannot move or resize it — css-lists-3 §3.2
     * makes it the item's first inline box. Everything else keeps the Row.
     *
     * STILL DEFERRED — the rest of B-RC3 part 3. `outside` is still
     * painted as a leading inline box rather than hung in the item's
     * margin area (marker box outside the principal box, aligned on the
     * first line's baseline), an `inside` marker on an item that DOES have
     * text still displaces that item's box, and the gap is still the fixed
     * [ListMarkerRow.gapDp] rather than the UA's per-counter-style marker
     * padding. Those need a custom Layout and the item's resolved box
     * metrics at this call site.
     */
    @Composable
    private fun RenderListItemMarker(
        child: IRComponent,
        index: Int,
        parentTag: String?,
        parentListPairs: List<Pair<String, kotlinx.serialization.json.JsonElement?>>,
        textColor: Color?,
        // Wave 27 (lane NMARK, B-RC8) — the CONTAINER's inheritance-merged
        // text style, resolved by the caller at the same place it resolves
        // `textColor` so the two can never come from different property
        // lists. css-lists-3 §3.2: the marker inherits from its originating
        // element, so this is the marker's font. Narrowed to the
        // character-level fields by ListMarkerTextStyle.forItem below.
        inheritedTextStyle: TextStyle
    ) {
        // Resolve the item's OWN marker config. Null only if the caller's
        // isListParent gate and uaMarkerDefault ever disagreed — render
        // the child bare rather than guess a marker.
        val listConfig = ListStyleExtractor.resolveMarkerConfig(
            parentTag,
            parentListPairs,
            child.properties.map { it.type to it.data }
        )
        // Wave 27 (lane CBAKE): a BAKED marker wins outright. `meta.markerText`
        // is the extractor's full css-counter-styles-3 §6 resolution — the
        // counter style AND the `<ol start>` ordinal, neither of which
        // [ListStyleExtractor.resolveMarkerConfig] can see — so re-deriving
        // it here could only be worse. Absent ⇒ the local table, unchanged.
        val marker = child.markerText
            ?: listConfig?.let { StyleListApplier.getMarker(index, it) } ?: ""
        if (marker.isEmpty()) {
            // `list-style-type: none` (and the no-config guard). The
            // browser generates NO marker box at all — css-lists-3 §3.1:
            // "none: the item has no marker" — so the content must start
            // at the item's own content edge. The pre-wave-24 code still
            // emitted Text(" ") + 4dp padding here, shifting every
            // `none` item right by a space-plus-4dp (live fixture:
            // change-list-style-type-001's "square to none" rows).
            RenderComponent(child)
            return
        }
        // Wave 28 (lane MC) — the marker's typography, hoisted so BOTH
        // placements below paint with one resolution rather than two.
        val markerStyle = ListMarkerTextStyle.forItem(inheritedTextStyle, textColor)
        // Wave 29 (lane MP) — the marker's own TextLayoutResult, the line
        // count [ListMarkerLineBox.snap] needs. A marker token never wraps
        // in the corpus, but the count is READ rather than assumed: an
        // assumed 1 would silently mis-size the one document that ever
        // wraps one, and the item's snap reads it too.
        val markerLayout = androidx.compose.runtime.remember {
            androidx.compose.runtime.mutableStateOf<androidx.compose.ui.text.TextLayoutResult?>(null)
        }
        // The snap itself, built once per marker: the resolved CSS line box
        // in px (Unspecified ⇒ 0f ⇒ no modifier — the declared-`normal`
        // state, where the face's natural box IS the CSS answer), and the
        // composed-WPT gate that keeps every other capture path
        // byte-identical. Density is read here because a Modifier factory
        // is not a composable scope.
        val markerSnapDensity = androidx.compose.ui.platform.LocalDensity.current
        // Wave 30 (lane 3, fix B3) — the residual carrier the snap rounds
        // its ACCUMULATED position against. Null outside composed WPT (no
        // provider is installed there), which is exactly the isolated
        // per-row rounding every other capture path keeps.
        val markerRowPitch = com.styleconverter.runtime.lists
            .LocalRowPitchAccumulator.current
        val markerLineBoxSnapModifier = if (LocalWptComposedMode.current) {
            ListMarkerLineBox.snap(
                refLineBoxPx = if (markerStyle.lineHeight != TextUnit.Unspecified)
                    with(markerSnapDensity) { markerStyle.lineHeight.toPx() } else 0f,
                lineCount = { markerLayout.value?.lineCount ?: 0 },
                // Keyed by the ITEM's IR id: stable across measure passes
                // (the idempotence the accumulator's ordering contract
                // depends on) and unique per row within a capture.
                prefixExactPx = { exact ->
                    markerRowPitch?.prefixFor(child.id, exact) ?: 0f
                }
            )
        } else Modifier
        // Wave 30 (lane 3, fix B6) — the disc/circle/square SHAPE that
        // replaces the symbol glyph's ink. `Modifier` (a no-op) for every
        // numeric/alphabetic style and for every baked string that does
        // not match our own symbol table, so nothing but the three UA
        // symbols changes. Resolved once, shared by both placements below.
        val markerSymbolPaint = com.styleconverter.runtime.lists.ListMarkerSymbol.paint(
            type = listConfig?.listStyleType
                ?: com.styleconverter.runtime.lists.ListStyleType.DISC,
            markerText = marker,
            // The marker's own resolved size, bottoming out at the same
            // browser default `PlaceholderContent` uses when nothing up
            // the chain declared one (ListMarkerLineBox.DEFAULT_FONT_SIZE_SP).
            fontSizePx = with(markerSnapDensity) {
                (if (markerStyle.fontSize != TextUnit.Unspecified) markerStyle.fontSize
                else ListMarkerLineBox.DEFAULT_FONT_SIZE_SP.sp).toPx()
            },
            // A draw scope cannot read a CompositionLocal, so the ink the
            // glyph would have taken is resolved here — same three-step
            // fallback `Text` itself applies (explicit style colour, then
            // the ambient content colour).
            color = markerStyle.color.takeOrElse {
                androidx.compose.material3.LocalContentColor.current
            }
        )
        // Does the item's principal box carry a first text baseline at
        // all? Drives BOTH wave-28 decisions (which placement, and — in
        // the row — whether a baseline claim is meaningful).
        val exposesBaseline = ListMarkerRow.itemExposesTextBaseline(child)
        if (ListMarkerRow.rendersInsideOverlay(
                listConfig?.listStylePosition, exposesBaseline)) {
            // Wave 28 (lane MC), defect 1 — `list-style-position: inside`
            // on an item with no in-flow content. css-lists-3 §3.2 makes
            // the marker the item's FIRST INLINE BOX: it lives INSIDE the
            // principal box, so it must not move that box. The Row below
            // did move it, and the live counter-styles items anchor an
            // absolutely positioned reference glyph to it
            // (css-position-3 §2.1), so the whole reference column
            // inherited the displacement — see ListMarkerRow's header
            // for the measured columns.
            //
            // The Box sizes itself from the item ALONE: the marker's
            // modifier reports a zero size (ListMarkerRow.insideMarkerOverlay),
            // which also retires the vertical half of the same defect —
            // a marker taller than a definite-height item can no longer
            // stretch it (css-sizing-3 §5.1).
            //
            // Marker drawn LAST = on top. In the browser an inside marker
            // paints above the item's own background (it is the item's
            // content) but BELOW its positioned descendants; we cannot
            // split those two layers here, and painting it on top keeps
            // an item with an opaque background from swallowing its
            // marker entirely. The remaining deviation needs the marker
            // and the positioned glyph to physically overlap, which the
            // css-lists geometry never does.
            Box {
                RenderComponent(child)
                // NO line-box snap here, deliberately (wave 29, lane MP).
                // This marker already reports a ZERO size, so the snap's
                // HEIGHT half would be inert — but its ½px re-centring
                // would still move the drawn glyph ~1px up against the
                // wave-28 overlay origin these tests were tuned on, and
                // this branch has no row whose pitch it could fix. The
                // scope of the lane is the ROW's extent.
                //
                // SKEPTIC CORRECTION (wave 29): omitting the snap here is
                // VERIFIED — an A/B build with `markerLineBoxSnapModifier`
                // forced off produced BYTE-IDENTICAL captures for all three
                // `list-style-position: inside` documents
                // (css3-counter-styles-101/102/103), so the snap really is
                // inert on this branch. What is NOT byte-identical is the
                // branch's ink, because `markerStyle` itself is a wave-29
                // output: the line-box pin above plus the three mechanics
                // fields and the family bottom-out in ListMarkerTextStyle
                // all reach this `Text` too. MEASURED against the wave-28
                // captures: 101 moved 262 px, 102 2112 px, 103 60 px (SSIM
                // +0.0002 / +0.0011 / +0.0002 — a marginal improvement, not
                // a wash by luck). Stated so a later lane re-tuning the
                // overlay origin knows this branch already moved once.
                Text(
                    // Wave 34 (lane F1) — per-script fallback spans, same
                    // rule as the other two ::marker call sites. This is the
                    // `list-style-position: inside` branch, i.e. the one the
                    // arabic-indic 101/102/103 documents actually take.
                    text = com.styleconverter.runtime.typography.font
                        .ScriptFallbackFonts.annotate(marker, LocalWptCaptureMode.current),
                    style = markerStyle,
                    // Wave 30 (lane 3, fix B2) — css-lists-3 §3.2 makes the
                    // ::marker box shrink-to-fit inline-level content, sized
                    // by its glyphs and never by the space its item leaves
                    // over. `softWrap = false` is the closest Compose gets:
                    // it pins the run to ONE line, so a narrow constraint
                    // can no longer wrap the glyphs.
                    //
                    // HONEST SCOPE (corrected in the wave-30 fix round): it
                    // is NOT "the Compose spelling of `.fixedSize`", the
                    // claim this comment used to make. `Text` still measures
                    // against the incoming maxWidth, and its DEFAULT
                    // `TextOverflow.Clip` truncates whatever does not fit —
                    // where SwiftUI's `.fixedSize(horizontal:true,
                    // vertical:true)` takes the ideal width and OVERFLOWS
                    // its slot, painting every glyph. The two therefore
                    // still disagree for a marker wider than its constraint;
                    // that residual is inert on THIS branch by construction,
                    // because ListMarkerRow.insideMarkerOverlay measures
                    // with unbounded `Constraints()` and reports a zero
                    // size, so no constraint ever reaches the glyphs. Set
                    // here anyway so the two call sites agree about what a
                    // marker box is.
                    softWrap = false,
                    modifier = ListMarkerRow.insideMarkerOverlay()
                        .then(markerSymbolPaint)
                )
            }
            return
        }
        Row(verticalAlignment = Alignment.Top) {
            // Wave 28 (lane MC) — whether a baseline claim means anything
            // for THIS item. See repair 2 below.
            val aligns = ListMarkerRow.alignsByBaseline(exposesBaseline)
            // Marker text.
            //
            // Wave 28 (lane MC) — the trailing space is GONE. It was
            // described as "the browser's default marker suffix", but the
            // suffix is already inside the baked `meta.markerText` (and
            // inside StyleListApplier.getMarker's "n." for the unbaked
            // table); the space was a SECOND gap stacked on the 4dp
            // padding, worth ~7dp at the 25px these items inherit, and
            // the iOS twin never had it. That was a pure Android-vs-iOS
            // divergence on every marker row. The gap is now the shared
            // ListMarkerRow.gapDp alone. Safe for the committed captures:
            // a marker row needs `sourceTag` on both container and item,
            // and no file outside fixtures/wpt/ carries a tag at all, so
            // nothing under tools/visual/baseline/ builds one.
            //
            // Wave 27 (lane NMARK, B-RC8) — two repairs, both visible on
            // the wave27-gate arabic-indic capture:
            //
            //  1. `style`. This Text carried a colour and NOTHING else,
            //     so it painted at Compose's Material default (~14sp)
            //     while the item inherited `font-size: 25px`. The marker
            //     inherits from its originating element (css-lists-3
            //     §3.2), so it now takes the LIST CONTAINER's resolved
            //     style, narrowed to the character-level fields by
            //     ListMarkerTextStyle.forItem. Container, not `<li>` —
            //     that gap, and the fact that iOS shares it exactly, is
            //     documented under "Honest scope" in
            //     ListMarkerTextStyle. The explicit `textColor`
            //     still wins inside that call, so a document that only
            //     ever declared colour renders exactly as before.
            //
            //  2. `Modifier.alignByBaseline()`. `Alignment.Top` stacked
            //     the two boxes by their TOP edges, which put the marker
            //     and the item's text on different baselines the moment
            //     their line boxes differed in height (which, at 14sp vs
            //     25px, was always). The marker is the item's first
            //     inline box and shares the line's baseline.
            //
            //     WAVE 28 CORRECTION — the claim that followed here, that
            //     Compose "resolves an ABSENT FirstBaseline back to the
            //     cross-axis origin, i.e. exactly the Alignment.Top this
            //     replaces, so those rows are unchanged", is wrong about
            //     the row's EXTENT. RowColumnImpl reads an unspecified
            //     alignment line as offset 0, then sizes the row as
            //     max(baselineOffset) + max(height − baselineOffset) — so
            //     the marker's whole ascent is added ABOVE a baseline-less
            //     item instead of overlapping it. Measured on the live
            //     wave27-final capture: 43px pitch for a `height: 31.25px`
            //     item, and a first row 8px late, on Android only (iOS had
            //     already chosen per item). The claim is now MADE TRUE by
            //     construction — `aligns` is false for exactly the
            //     baseline-less items, and both children then fall back to
            //     the Row's `Alignment.Top`. The iOS twin spells the same
            //     two-case rule out in
            //     StyleEngine/lists/ListMarkerRow.swift, because SwiftUI
            //     falls back to the item's BOTTOM edge instead.
            //
            //     BOTH children take the modifier: `alignByBaseline` is a
            //     RowScope parent-data claim, and a row with only ONE
            //     baseline-aligned child has nothing to align it against,
            //     so marking the marker alone would be an inert no-op.
            //     `itemModifier` is documented (see RenderComponent's
            //     @param) as the OUTERMOST modifier, which is what puts
            //     the claim on the Row's direct child where parent data
            //     is read.
            //     The separate `color = textColor ?: Color.Unspecified`
            //     argument is GONE, not lost: forItem folds `textColor`
            //     in with the identical precedence (explicit wins, null
            //     falls through to the inherited value, and an
            //     Unspecified result still bottoms out at
            //     LocalContentColor inside Text). Keeping both would have
            //     left two places deciding one colour.
            Text(
                // Wave 34 (lane F1) — per-script fallback spans, same rule as
                // the other two ::marker call sites. This is the OUTSIDE
                // marker row (`list-style-position: outside`), the branch the
                // armenian / bengali / cambodian documents take.
                text = com.styleconverter.runtime.typography.font
                    .ScriptFallbackFonts.annotate(marker, LocalWptCaptureMode.current),
                style = markerStyle,
                // Wave 30 (lane 3, fix B2) — the one-line ::marker run
                // (css-lists-3 §3.2 wants the box shrink-to-fit). THIS is
                // the call site where it bites: the marker is a Row sibling
                // measured against whatever inline space the item's declared
                // width leaves, and the same over-constraint that made
                // SwiftUI compress its marker to zero width (see
                // ListMarkerRow's header, point 1) makes Compose wrap the
                // glyphs onto a second line — which the line-box snap below
                // then faithfully doubles the marker's height for.
                //
                // HONEST SCOPE (corrected in the wave-30 fix round): this is
                // NOT "the Compose spelling of `.fixedSize(horizontal:true,
                // vertical:true)`", as this comment used to claim. It stops
                // the WRAP, not the constraint: `Text` still measures
                // against the incoming maxWidth and its default
                // `TextOverflow.Clip` truncates the excess, where iOS's
                // `.fixedSize` keeps the ideal width and lets the marker
                // overflow its slot with every glyph painted. So for a
                // marker whose single-line width exceeds the space the row
                // leaves it, Compose now paints a CLIPPED marker where iOS
                // paints a full one — a smaller and non-compounding
                // divergence than the wrapped-and-doubled line box it
                // replaces (that one fed the snap and moved the whole row),
                // but a divergence, and it is stated rather than implied.
                softWrap = false,
                // The line-count channel the snap reads (see markerLineBox
                // + markerLineBoxSnap): Compose reports it after layout,
                // and writing the state re-runs the layout block, not the
                // composition.
                onTextLayout = { markerLayout.value = it },
                modifier = markerBaselineClaim(aligns)
                    // Wave 30 (lane 3, fix B6) — the painted disc/circle/
                    // square.
                    //
                    // CHAIN ORDER, corrected in the wave-30 fix round: this
                    // draw modifier is written BEFORE the snap, which makes
                    // it the OUTER of the two — so the size its DrawScope
                    // reads is the size the snap REPORTS, i.e. the snapped
                    // height, not the glyph's natural one. (The previous
                    // comment claimed the opposite.) It is self-neutralising
                    // rather than a bug: ListMarkerSymbol.topPx CENTRES the
                    // shape in whatever height reaches it, and the snap's
                    // trim is symmetric — it re-places the glyph band at
                    // (target − natural) / 2 — so the shape and the ink it
                    // replaces stay centred on the same line box either way.
                    .then(markerSymbolPaint)
                    // Wave 29 (lane MP) — the composed-WPT line-box snap the
                    // ITEM's text run has always had. Without it the marker
                    // box keeps its FACE's natural line (measured h=34 for a
                    // 31.25px CSS box, because the Armenian marker resolves
                    // through a system fallback face) and, this Row being
                    // baseline-aligned, the ROW inherits that height: the
                    // 34px pitch against web's 31–32 and iOS's 31. Composed
                    // WPT only — every other capture path is untouched.
                    .then(markerLineBoxSnapModifier)
                    .padding(end = ListMarkerRow.gapDp)
            )
            RenderComponent(child, markerBaselineClaim(aligns))
        }
    }

    /**
     * Render an absolutely positioned child.
     *
     * The top/left offset and z-index are NOT applied here: the child's own
     * style chain already applies them (LayoutFacade → PositionApplier
     * `applyPosition`, ABSOLUTE branch → `Modifier.offset`). Applying them
     * again in this wrapper DOUBLED every inset — B_RelativeAnchor's
     * `top:10px; left:20px` child landed at ~(40,20) instead of (20,10),
     * drifting below-right of its in-flow sibling (Android-web 0.833 on the
     * floating-child crop). CSS 2.1 §10.6.4: the offset applies ONCE,
     * from the containing block's padding box — which is exactly what the
     * parent's overlay Box (RenderContent's positioned-container branch)
     * plus the child's own offset modifier already produce.
     *
     * Wave 8: the child is measured through [absposOverflowMeasure] so its
     * SPECIFIED size wins over the container's incoming constraints —
     * css-position-3 §2.1: an out-of-flow box is sized by its own
     * properties against its containing block and may overflow it; without
     * the unbounded measure a 69px child of a 50px positioned container
     * silently clamped to 50px (flex-abspos-staticpos-align-self-safe-001/
     * 002 natives 0.86-0.92 while web overflowed correctly).
     *
     * Wave 28 (lane NE): the mount is chosen per child. A box whose only
     * inset on an axis is `right`/`bottom` anchors from the containing
     * block's END edge (css-position-3 §3.5.3) — the wave-22 rule table the
     * canvas-hoist slot already applies to ROOT-level boxes, which this
     * NESTED slot never had: every child was placed at the slot origin, so
     * the child's own `−right`/`−bottom` offset (PositionConfig.offsetX/Y)
     * moved it OUTSIDE the parent's top-left corner instead of inward from
     * its bottom-right (position-right-bottom.json's RB_Px painted at
     * (−20,−20), not (220,60)). [PositionedAncestorAnchor] is that mount and
     * subsumes the unbounded measure; every other child — start-anchored,
     * over-constrained or inset-free — keeps [absposOverflowMeasure]
     * byte-identically, which is the frozen-baseline guarantee.
     */
    @Composable
    private fun RenderAbsoluteChild(child: IRComponent) {
        // Read the insets through the SAME extractor the child's own style
        // chain will use, so the mount and the offset can never disagree
        // about which sides are declared (the rule CanvasRootHoist
        // .canvasAnchor follows for the root-level slot).
        // anchorGate, NOT the bare extractor: this call site reads the RAW
        // wire while the child's own style chain reads the
        // DynamicValueResolver-resolved one, and the two disagree about a
        // `calc()`/`var()`/`%` START inset (see anchorGate's kdoc — a raw
        // `left: calc(10px + 5px)` reads as absent here and as +15dp there,
        // which would compose anchor and offset from opposite edges).
        val positionConfig = com.styleconverter.runtime.layout.position.PositionedAncestorAnchor
            .anchorGate(child.properties.map { it.type to it.data })
        val mount =
            if (com.styleconverter.runtime.layout.position.PositionedAncestorAnchor
                    .anchors(positionConfig)
            ) {
                // End-anchored mount (A1/A5) — fills + anchors the axes that
                // declare only right/bottom, wave-8 measure for the rest.
                com.styleconverter.runtime.layout.position.PositionedAncestorAnchor
                    .endAnchoredMeasure(positionConfig)
            } else {
                // The wave-8 mount, verbatim (A2/A3/A4 and every in-flow-
                // anchored box).
                absposOverflowMeasure()
            }
        RenderComponent(child, absposPaintOrder(child).then(mount))
    }

    /**
     * Wave 33 (lane C) — CSS 2.1 Appendix E paint order for a `z-index:
     * auto` positioned child of a `position: relative` container.
     *
     * ## The measured defect
     * The positioned-container branch of [RenderContent] emits every child
     * into ONE `Box` in DOCUMENT order, and a Compose `Box` draws later
     * children on top. Appendix E does not: step 4 paints the in-flow,
     * non-positioned block boxes, and step 8 paints the positioned
     * descendants with `z-index: auto | 0`. So an abspos child that comes
     * FIRST in the source must still paint LAST.
     *
     * css-tables/absolute-tables-007 is the pixel proof. Its relative
     * wrapper holds `[abspos green table, in-flow red 100px block]` in that
     * order; once the wave-33 cb-height channel gave the green table its
     * 100×100 used size, Compose painted it and then painted the red block
     * straight over it — a probe with the two children SWAPPED
     * (_diag33/laneC/probe, red first) rendered the identical IR fully
     * GREEN, isolating draw order from sizing. The Chromium ref is green.
     *
     * ## Why a fractional z and not a reorder
     * `Modifier.zIndex` reorders DRAWING without touching measurement,
     * placement or composition identity, so nothing else in the tree can
     * observe it. The value 0.5 places the box exactly where Appendix E
     * does: above the in-flow siblings and the negative-z backdrop layer
     * (both implicit 0 — step 4), below any DECLARED positive `z-index`
     * (step 9), above any declared negative one (step 3). A child that
     * declares its own `z-index` is left alone entirely: `PositionApplier`
     * already puts that value on the child's own chain, and an outer
     * wrapper z would shadow it (the outer node's z is what orders it in
     * the container).
     *
     * ## Blast radius, enumerated before the change
     * Only a container whose abspos child PRECEDES an in-flow sibling can
     * observe this — everything else already drew in Appendix-E order by
     * accident of document order. Across all 27 frozen wave32-final
     * sections that is THREE tests (css-tables/absolute-tables-007,
     * css-multicol/abspos-multicol-in-second-outer-clipped,
     * css-text/bidi/bidi-lines-002), and across the committed dark-stage
     * fixtures exactly ONE (fixtures/fidelity/trees/block-flow.json, whose
     * abspos red 60×30 at (20,10) is currently overpainted by the in-flow
     * orange 100×30 at (0,0) — the same defect, and the same divergence
     * from web). WPT-capture gated so that fixture's committed baseline
     * does NOT move in this wave; re-baselining it is a deferred item, not
     * a claim that the dark-stage path is right.
     *
     * ## Wave 35 (lane B1) — the gate is GONE, and why that was safe
     * The wave-33 capture gate was a schedule decision, not a correctness
     * one: Appendix E is Appendix E on the product path too, and iOS never
     * had the gate at all (`ComponentRenderer.outOfFlowChildren` mounts the
     * positioned half as an unconditional `.overlay`, so SwiftUI has always
     * painted step 8 above step 4 for every consumer). Android was the
     * odd platform out — an SDUI host embedding this runtime got a DIFFERENT
     * paint order from the same IR depending on a capture flag it cannot
     * see.
     *
     * The gate's stated cost of removal — "block-flow.json's committed
     * baseline moves" — was re-measured before pulling it, and it does not
     * exist. _diag35/laneB1/scan-appendixE.mjs enumerates every fixture
     * component in the repo carrying the observable shape (a `position:
     * relative` container whose z-index-less out-of-flow child precedes an
     * in-flow sibling) and cross-references the result against the 363
     * committed `tools/visual/baseline` PNG names: 87 components match, all
     * 87 report `baseline=none`, and the single non-WPT match is exactly
     * `fixtures/fidelity/trees/block-flow.json`'s `B_RelativeAnchor` —
     * which was never baselined. So there is no committed dark-stage
     * capture that can observe this change; the un-gate is byte-neutral for
     * the frozen set by enumeration, not by hope.
     */
    @Composable
    private fun absposPaintOrder(child: IRComponent): Modifier =
        // Un-gated (wave 35): Appendix E step 8 is the paint order for every
        // consumer of this renderer, product path included — see the kdoc's
        // enumeration for why no committed baseline can observe it.
        autoZForPositionedChild(child.properties)
            ?.let { Modifier.zIndex(it) } ?: Modifier

    /**
     * The pure half of [absposPaintOrder] — the draw order to FORCE on a
     * positioned child, or null to leave its chain untouched. Pure over the
     * IR so the whole decision is JVM-pinnable without Robolectric.
     *
     * - A DECLARED `z-index` → null. PositionApplier already puts that
     *   value on the child's own chain, and an outer wrapper z would
     *   shadow it (the outer node's z is what orders it in the container).
     * - Otherwise → [AUTO_Z_POSITIONED_DESCENDANT], Appendix E step 8.
     *
     * Wave 35 (lane B1): the `wptCaptureMode` parameter is GONE, not
     * defaulted. Appendix E does not have a capture mode, and leaving a
     * `wptCaptureMode = true` default in place would have kept a dead knob
     * that a future caller could silently flip back to the divergent
     * order — see [absposPaintOrder]'s kdoc for the enumeration proving the
     * committed dark-stage baselines cannot observe the un-gate.
     */
    internal fun autoZForPositionedChild(
        properties: List<IRProperty>,
    ): Float? {
        if (com.styleconverter.runtime.core.placement.ItemPlacementExtractor
                .zIndex(properties) != null
        ) return null
        return AUTO_Z_POSITIONED_DESCENDANT
    }

    /**
     * Out-of-flow test for a child's IR list (css-position-3 §2.1:
     * `absolute` and `fixed` take the box out of flow; `relative`/`sticky`
     * keep it in flow). Pure over the IR — JVM-pinned by
     * AbsposOverflowMeasureTest.
     */
    internal fun isOutOfFlowChild(properties: List<IRProperty>): Boolean =
        extractPositionType(properties)
            .let { it == PositionType.ABSOLUTE || it == PositionType.FIXED }

    /**
     * The size an out-of-flow child REPORTS to its parent on one axis:
     * the unbounded-measured size, coerced back into the incoming
     * constraints. The parent's own geometry must not change (an abspos
     * box never sizes its ancestors — css-position-3 §3), so the report
     * fits the constraint envelope while the INK is placed unclipped and
     * overflows. Pure math — JVM-pinned by AbsposOverflowMeasureTest.
     */
    internal fun absposReportedAxis(measuredPx: Int, minPx: Int, maxPx: Int): Int =
        measuredPx.coerceIn(minPx, maxPx)

    /**
     * Measurement wrapper for absolutely/fixed-positioned children
     * (wave 8): measure the child's whole style chain UNBOUNDED
     * (Constraints() == 0..∞ on both axes) so the child's own width/height
     * modifiers resolve to their SPECIFIED values instead of clamping to
     * the container — css-position-3 §2.1 sizes an out-of-flow box purely
     * from its own properties + containing block, and overflow is the
     * correct rendering (the WPT refs paint the 69px box spilling out of
     * the 50px container). The wrapper then reports a constraint-fitting
     * size (see [absposReportedAxis]) and anchors the oversized placeable
     * at the reported box's origin — the box's static position — so the
     * excess overflows toward the inline/block end exactly like the LTR
     * horizontal-tb refs. Compose does not clip children by default, so
     * the overflowing ink draws (matching CSS overflow:visible).
     *
     * Lane FLEX-SAFE: [crossSpec] carries the child's resolved abspos
     * static-position CROSS alignment (align-self center / safe center /
     * unsafe center — css-flexbox-1 §4.1 sole-item hypothetical +
     * css-align-3 §4.4 overflow keywords). The parent-side Box.align only
     * positions the REPORTED (constraint-clamped) box, which fills the
     * cross axis whenever the child overflows — so the OVERFLOWING ink's
     * alignment must be applied here, on the placeable itself:
     * crossOffset(child, reported) is 0 when the child fits (reported ==
     * measured; the parent Box.align then owns placement) and the
     * safe-aware centered/end offset when it overflows (reported ==
     * container cross size). Null spec ⇒ byte-identical wave-8 behaviour
     * (the RenderAbsoluteChild overlay path always passes null).
     *
     * internal (was private) since the wave-11 grid abspos partition:
     * GridRenderer's out-of-flow overlay (css-grid-1 §9.2) reuses the same
     * unbounded measure + cross-offset machinery for its overlay children.
     */
    internal fun absposOverflowMeasure(
        crossSpec: com.styleconverter.runtime.layout.flexbox.AbsposStaticAlignment.Spec? = null,
        crossIsVertical: Boolean = true
    ): Modifier = Modifier.layout { measurable, constraints ->
        // Unbounded measure: min 0 / max ∞ both axes — the child's own
        // size chain (width/height/padding/border modifiers) decides.
        val placeable = measurable.measure(androidx.compose.ui.unit.Constraints())
        // Report a size the parent flow can live with (fits the incoming
        // envelope); the flow geometry is byte-identical to the clamped
        // behaviour, only the drawn ink now overflows.
        val reportedW = absposReportedAxis(placeable.width, constraints.minWidth, constraints.maxWidth)
        val reportedH = absposReportedAxis(placeable.height, constraints.minHeight, constraints.maxHeight)
        layout(reportedW, reportedH) {
            // Cross-axis static-position shift for the overflow case —
            // the shared safe-fallback math (identically pinned on iOS).
            // roundToInt: Compose places on whole px; ties round half-up
            // on both platforms' captures at the SSIM scale used.
            val crossPx = crossSpec?.let { spec ->
                com.styleconverter.runtime.layout.flexbox.AbsposStaticAlignment.crossOffset(
                    // Child extent on the cross axis vs the reported box.
                    childPx = (if (crossIsVertical) placeable.height else placeable.width).toDouble(),
                    containerPx = (if (crossIsVertical) reportedH else reportedW).toDouble(),
                    spec = spec
                ).let { kotlin.math.round(it).toInt() }
            } ?: 0
            // Anchor at the reported box's origin (the static position
            // the parent placed us at), shifted by the cross offset;
            // place() beyond the reported size is legal and draws
            // unclipped — negative offsets spill toward the start edge.
            placeable.place(
                // Column flex: cross axis is horizontal (x shift).
                x = if (crossIsVertical) 0 else crossPx,
                // Row flex: cross axis is vertical (y shift).
                y = if (crossIsVertical) crossPx else 0
            )
        }
    }

    /**
     * Wave 19 (lane FLEX) — the FULL static-position measure for an
     * abspos child of a flex container, replacing [absposOverflowMeasure]
     * on the flex loops in WPT capture mode (the grid overlay and the
     * dark-stage flex path keep the wave-8/18 modifier byte-identically).
     *
     * Same unbounded-measure + constraint-fitting report contract as
     * absposOverflowMeasure (css-position-3 §2.1 — see its docs), but the
     * placement is PHYSICAL per axis via the shared
     * [AbsposStaticPosition.axisOffset] twins:
     *   • [containerWPx]/[containerHPx] carry the container's DEFINITE
     *     content extents from the IR (AbsposStaticPosition
     *     .definiteExtentPx) so the FITS case places internally too —
     *     the wave-18 split (parent align modifier for fits, internal
     *     shift for overflow) cannot express reversed axes (a vertical-rl
     *     cross axis is the Row loop's MAIN axis, where no per-child
     *     align modifier exists — the safe-003 C3 bottom-right anchor);
     *   • a null extent falls back to the REPORTED box (== the wave-18
     *     overflow-only correction: fits ⇒ reported == measured ⇒ offset
     *     0, and the caller's align-modifier fallback owns placement);
     *   • a null spec contributes 0 (axis owned by an inset, the
     *     arrangement, or nothing).
     * roundToInt matches absposOverflowMeasure's whole-px placement.
     */
    internal fun absposStaticMeasure(
        xSpec: com.styleconverter.runtime.layout.flexbox.AbsposStaticPosition.AxisSpec?,
        ySpec: com.styleconverter.runtime.layout.flexbox.AbsposStaticPosition.AxisSpec?,
        containerWPx: Double?,
        containerHPx: Double?
    ): Modifier = Modifier.layout { measurable, constraints ->
        // Unbounded measure: the child's own size chain decides (wave 8).
        val placeable = measurable.measure(androidx.compose.ui.unit.Constraints())
        // Constraint-fitting report — flow geometry stays byte-identical
        // to the wave-8 modifier; only placement below differs.
        val reportedW = absposReportedAxis(placeable.width, constraints.minWidth, constraints.maxWidth)
        val reportedH = absposReportedAxis(placeable.height, constraints.minHeight, constraints.maxHeight)
        layout(reportedW, reportedH) {
            // Per-axis physical offset: IR extent when definite, else the
            // reported box (overflow-only correction — see the doc).
            val xOff = xSpec?.let { spec ->
                kotlin.math.round(
                    com.styleconverter.runtime.layout.flexbox.AbsposStaticPosition.axisOffset(
                        childPx = placeable.width.toDouble(),
                        containerPx = containerWPx ?: reportedW.toDouble(),
                        spec = spec
                    )
                ).toInt()
            } ?: 0
            val yOff = ySpec?.let { spec ->
                kotlin.math.round(
                    com.styleconverter.runtime.layout.flexbox.AbsposStaticPosition.axisOffset(
                        childPx = placeable.height.toDouble(),
                        containerPx = containerHPx ?: reportedH.toDouble(),
                        spec = spec
                    )
                ).toInt()
            } ?: 0
            // Negative/overflowing placement draws unclipped (CSS
            // overflow:visible) — same contract as the wave-8 modifier.
            placeable.place(xOff, yOff)
        }
    }

    /**
     * Wave-9 companion to [absposOverflowMeasure]: rewrite percentage
     * Width/Height wires ({"type":"percentage","value":N} — the frozen
     * typed-sizing shape) to exact px ({"type":"length","px":…}) against
     * the containing-block channel, for OUT-OF-FLOW components only.
     *
     * Why: fillMaxWidth/fillMaxHeight(fraction) — SizingApplier's percent
     * mapping — resolve against the incoming MAX constraint, which the
     * wave-8 unbounded measure sets to Infinity; Compose documents the
     * fill modifiers as no-ops there, so a `position:absolute; width:50%`
     * child would collapse to content size (no ink) on the next device
     * run. css-position-3 §5.1: abspos percentages resolve against the
     * containing block — [cb] carries the nearest ancestor's CONTENT box
     * (the padding-box the spec names minus nothing further; content box
     * is the channel's existing, documented approximation — same base the
     * in-flow % path uses, kept consistent rather than re-derived).
     *
     * Axes with an UNKNOWN base (null cb axis: auto-sized ancestor) keep
     * their percentage wire — nothing honest to resolve against; the
     * fill-modifier no-op then matches the pre-wave-8 unknown-parent
     * behaviour instead of inventing a base. Identity (same list
     * instance) when nothing rewrites, preserving frozen-baseline
     * byte-stability. Pure over the IR — JVM-pinned by
     * AbsposOverflowMeasureTest.
     */
    internal fun resolveOutOfFlowPercentSizes(
        properties: List<IRProperty>,
        cb: com.styleconverter.runtime.core.variables.ContainingBlock
    ): List<IRProperty> {
        // Track whether any property actually rewrote — identity out when
        // not, so remember{} keys and downstream fast paths stay stable.
        var changed = false
        val out = properties.map { p ->
            // Axis base: physical + logical size types map to the cb axis
            // they resolve against (css-logical-1 §4.1, LTR horizontal-tb:
            // inline = width, block = height — the engine's normalization).
            val base = when (p.type) {
                "Width", "InlineSize" -> cb.widthPx
                "Height", "BlockSize" -> cb.heightPx
                else -> null
            } ?: return@map p // not a size axis, or base unknown → keep
            // Only the typed percentage wire rewrites; px/keyword/calc
            // shapes flow through untouched (px overflow is exactly what
            // the unbounded measure exists for).
            val obj = p.data as? JsonObject ?: return@map p
            if ((obj["type"] as? JsonPrimitive)?.contentOrNull != "percentage") return@map p
            val pct = (obj["value"] as? JsonPrimitive)?.doubleOrNull ?: return@map p
            changed = true
            // Emit the frozen typed-length wire ({"type":"length","px":N} —
            // the same shape DynamicValueResolver.lengthJson(typed=true)
            // writes), which SizingExtractor reads as LengthValue.Exact →
            // Modifier.width/height(px.dp): constraint-independent, so the
            // specified size survives the unbounded measure.
            IRProperty(p.type, buildJsonObject {
                put("type", "length")
                put("px", pct * base / 100.0)
            })
        }
        // Identity contract: hand back the ORIGINAL instance when no
        // percentage resolved (byte-stable for the whole static corpus).
        return if (changed) out else properties
    }

    /**
     * Position type enum.
     */
    enum class PositionType {
        STATIC, RELATIVE, ABSOLUTE, FIXED, STICKY
    }

    /**
     * CSS 2.1 §10.3.3 auto-margin resolution for a block-level child, as a
     * Compose 2D alignment (pure + internal for the JVM pinning suite).
     *
     * Returns non-null only when the auto margin actually moves the box:
     *  - definite width + margin-left:auto + margin-right:auto → Center
     *  - definite width + margin-left:auto only → End (left absorbs space)
     *  - margin-right:auto only → null (box stays at the left edge — the
     *    wrapper would be a no-op, so we skip it)
     *  - width:auto → null (the box fills the containing block; §10.3.3
     *    resolves auto margins to 0, nothing to center)
     */
    internal fun autoMarginAlignment(properties: List<IRProperty>): Alignment? {
        // A margin is auto when the IR carries the bare "auto" keyword —
        // MarginPropertyParser emits it as a plain string primitive.
        fun isAuto(type: String) = properties.any { p ->
            p.type == type &&
                (p.data as? JsonPrimitive)?.contentOrNull?.equals("auto", ignoreCase = true) == true
        }
        // Logical inline margins behave identically in the LTR-normalized
        // engine (css-logical-1 §4.2 maps inline-start→left for LTR).
        val leftAuto = isAuto("MarginLeft") || isAuto("MarginInlineStart")
        val rightAuto = isAuto("MarginRight") || isAuto("MarginInlineEnd")
        if (!leftAuto) return null
        // §10.3.3 precondition: auto margins only absorb space when the
        // box's width is definite; with width:auto the width absorbs it.
        val hasDefiniteWidth = properties.any { it.type == "Width" || it.type == "InlineSize" }
        if (!hasDefiniteWidth) return null
        return if (rightAuto) Alignment.TopCenter else Alignment.TopEnd
    }

    /**
     * CSS 2.1 §9.5 float → block-level end alignment, or null when the box
     * doesn't float rightward. Pure + internal for the JVM pinning suite.
     * Reads the Float longhand through FloatExtractor (the single owner of
     * float/clear keyword parsing) so `right` and the logical `inline-end`
     * (LTR → right, css-logical-1 §2.1) share one mapping.
     */
    internal fun floatEndAlignment(properties: List<IRProperty>): Alignment? {
        val cfg = com.styleconverter.runtime.layout.FloatExtractor
            .extractFloatConfig(properties.map { it.type to it.data })
        return when (cfg.float) {
            com.styleconverter.runtime.layout.FloatValue.RIGHT,
            com.styleconverter.runtime.layout.FloatValue.INLINE_END -> Alignment.TopEnd
            else -> null
        }
    }

    /**
     * Wave-19 lane FLOAT — the block child loop's float-run plan, or
     * null when the sibling list contains no packable run (the common
     * case: the caller then keeps the frozen per-child loop verbatim).
     * Pure over the IR + internal for the JVM pinning suite. Facts come
     * from FloatExtractor (the single owner of float/clear keyword
     * parsing — same reader floatEndAlignment uses) and feed the shared
     * FloatRowPacking.segment twin (pins P1/P2).
     */
    internal fun blockFloatSegments(
        children: List<IRComponent>,
        containerProperties: List<IRProperty> = emptyList()
    ): List<com.styleconverter.runtime.layout.FloatRowPacking.Segment>? {
        // Wave-20 W3: the container's own Direction keyword decides how
        // the LOGICAL float/clear members map to physical sides
        // (css-logical-1 §2.1: rtl swaps inline-start/end). Read from
        // the container's declared properties only — the engine has no
        // inherited-direction channel, and the corpus (descendant-
        // static-position-002/004) declares physical `right` anyway.
        val rtl = containerProperties.any {
            it.type == "Direction" &&
                ValueExtractors.extractKeyword(it.data)?.uppercase() == "RTL"
        }
        // Per-sibling facts: float side + the `<br clear>` break shape.
        val facts = children.map { child ->
            com.styleconverter.runtime.layout.FloatRowPacking.facts(
                config = com.styleconverter.runtime.layout.FloatExtractor
                    .extractFloatConfig(child.properties.map { it.type to it.data }),
                // The clear-break marker must be childless (pin P2) —
                // a clear on a content box is real layout, out of scope.
                hasChildren = !child.children.isNullOrEmpty(),
                // css-logical mapping per the container's direction.
                rtl = rtl,
            )
        }
        // Segment once; only return a plan when a run actually exists so
        // run-free containers never leave the frozen loop.
        val segments = com.styleconverter.runtime.layout.FloatRowPacking.segment(facts)
        return if (segments.any { it.isRun }) segments else null
    }

    /**
     * Wave-20 W3 — the block child loop's inline-atom plan, or null when
     * the sibling list contains no packable atom run (pins P15/P19).
     * Pure over the IR + internal for the JVM pinning suite. An atom is
     * an inline-level UA widget (meta._tag widget set) or a text-only
     * anchor — the InlineAtomFlow twins own the predicate; this wrapper
     * only derives the per-child facts from the decoded IR.
     */
    internal fun blockInlineAtomSegments(
        children: List<IRComponent>,
        // Wave-33 C2 — the CONTAINER's own declarations, read only for
        // InlineBlockAtom's B7 line-box gate. Defaulted so every existing
        // wave-20 caller and JVM pin keeps its exact signature (and its
        // exact widget-lane behaviour: B7 can only ever REFUSE the new
        // inline-block family, never touch a UA widget atom).
        containerProperties: List<IRProperty> = emptyList(),
    ): List<com.styleconverter.runtime.layout.InlineAtomFlow.Segment>? {
        // Per-sibling atom facts from the decoded wire: originating tag
        // (meta.sourceTag → _tag), a declared Display override, element
        // children and text presence (the text-only-anchor guard, P15).
        val atomFlags = children.map { child ->
            com.styleconverter.runtime.layout.InlineAtomFlow.isAtom(
                tag = child._tag,
                // css-display-3 §2: a declared non-inline display makes
                // the box block-level — it leaves the inline flow.
                displayKeyword = child.properties.firstOrNull { it.type == "Display" }
                    ?.let { ValueExtractors.extractKeyword(it.data)?.uppercase() },
                hasElementChildren = !child.children.isNullOrEmpty(),
                hasText = !child._text.isNullOrEmpty(),
            // Wave-33 lane C (C2) — the SECOND atom family: an
            // author-declared `display: inline-block` box with a definite
            // size. Same §9.4.2 packing, different geometry SOURCE (the
            // wire, not the UA table), so it enters the identical run.
            // See InlineBlockAtom's B1–B6 gate table and its enumerated
            // 8-test blast radius.
            ) || inlineBlockAtomSpec(child, containerProperties) != null
        }
        // Segment once; only a plan with an actual run (≥2 consecutive
        // atoms) leaves the frozen block loop — mirror of the float gate.
        val segments = com.styleconverter.runtime.layout.InlineAtomFlow.segment(atomFlags)
        return if (segments.any { it.isRun }) segments else null
    }

    /**
     * Wave-33 lane C (C2) — the declared-inline-block AtomSpec for one
     * child, or null when it is not that family. Split out so the two
     * consumers (the segmenter above and the run's spec resolution in
     * the block child loop) read the SAME predicate and can never
     * disagree about which children are in the run.
     */
    internal fun inlineBlockAtomSpec(
        child: IRComponent,
        containerProperties: List<IRProperty>,
    ): com.styleconverter.runtime.layout.UAWidgetIntrinsics.AtomSpec? =
        com.styleconverter.runtime.layout.InlineBlockAtom.spec(
            properties = child.properties,
            // B6 — own text / inline runs are the statically visible
            // line-box source; either moves the baseline off the bottom
            // margin edge (§10.8.1) and the box leaves this lane.
            hasOwnText = !child._text.isNullOrEmpty(),
            hasOwnRuns = !child.runs.isNullOrEmpty(),
            // B7 — the strut pins are solved for the harness's default
            // line box; a declared container line-height invalidates them
            // (absolute-tables-013's `line-height: 0` <td>).
            containerDeclaresLineHeight = containerProperties.any { it.type == "LineHeight" },
        )

    /**
     * Wave-20 W3 — a run member's widget kind for the shared UA geometry
     * table (UAWidgetIntrinsics). The `type`/`multiple` attributes ride
     * the wire as `meta.attrs` (the wave-20 widget-identity contract)
     * and are decoded into [IRComponent.attrs] by lane W2's capsule —
     * an absent capsule (pre-wave-20 wires, non-widget tags) folds to
     * the TEXT_FIELD/menulist defaults exactly like the HTML parser's
     * missing-attribute states.
     */
    internal fun atomKindOf(child: IRComponent): com.styleconverter.runtime.layout.UAWidgetIntrinsics.Kind =
        com.styleconverter.runtime.layout.UAWidgetIntrinsics.kind(
            tag = child._tag,
            // The wire's `type` attribute (present-in-source only).
            typeAttr = child.attrs?.type,
            // The wire's boolean `multiple` presence (listbox height).
            multiple = child.attrs?.multiple == true,
        )

    /**
     * Extract position type from properties.
     */
    private fun extractPositionType(properties: List<IRProperty>): PositionType {
        properties.forEach { prop ->
            if (prop.type == "Position") {
                val keyword = ValueExtractors.extractKeyword(prop.data)?.uppercase()
                return when (keyword) {
                    "RELATIVE" -> PositionType.RELATIVE
                    "ABSOLUTE" -> PositionType.ABSOLUTE
                    "FIXED" -> PositionType.FIXED
                    "STICKY" -> PositionType.STICKY
                    else -> PositionType.STATIC
                }
            }
        }
        return PositionType.STATIC
    }

    /**
     * Render content in Row scope with AlignSelf, FlexGrow, and Order support.
     *
     * [mainArrangement] / [crossAlignment] are the SAME values the caller
     * hands to the wrapping Row — the wave-9 intrinsic flex path re-uses
     * them so justify-content / align-items behave identically whether the
     * line resolves statically, intrinsically, or not at all.
     */
    @Composable
    fun RowScope.RenderRowContent(
        component: IRComponent,
        textColor: Color?,
        mainArrangement: Arrangement.Horizontal = Arrangement.Start,
        crossAlignment: Alignment.Vertical = Alignment.Top
    ) {
        if (!component.children.isNullOrEmpty()) {
            // css-flexbox-1 §9.7: flex-grow distributes the container's FREE
            // space — which only exists when the container's main size is
            // DEFINITE. Our reference render (web harness) gives every
            // container `width: fit-content` unless the IR declares a width,
            // so an unsized flex container hugs its items and grow is a
            // visual no-op. Compose's `Modifier.weight` does the OPPOSITE:
            // it forces the Row to expand to the incoming max constraint
            // (the full canvas), which blew nested flex rows out to 358px
            // with the items spread across them (nested-3level 008_cellA
            // 0.68 / 011_cellB 0.77 vs web's compact cards). Only translate
            // flex-grow to weight when the container declares a definite
            // main-axis size.
            val mainSizeDefinite = placeholderFillsParentWidth(component.properties)
            // Wave 19 (lane FLEX): the abspos static-position resolver is
            // WPT-capture-gated — the dark-stage 327 corpus keeps the
            // wave-18 machinery byte-identically (see the loop below).
            val wptMode = LocalWptCaptureMode.current
            // Sort children by order property
            val sortedChildren = sortByOrder(component.children)
            // Extract the line inputs once; run the static §9.7 pass when
            // every base is definite. A null resolvedSizes with a non-null
            // spec means some base is CONTENT-SIZED → the intrinsic-measure
            // layout below fills it at measure time (wave-9, #40).
            val lineSpec = flexLineSpec(component, sortedChildren, rowAxis = true)
            val resolvedSizes = lineSpec?.let {
                com.styleconverter.runtime.layout.flexbox.FlexSizeResolver
                    .resolve(it.contentMainPx, it.gapPx, it.items)
            }
            // Flex items ignore justify-self (css-align-3 §6) and this Row
            // already owns align-self — turn the block-level self-alignment
            // wrapper off for the whole subtree root at each child.
            CompositionLocalProvider(LocalSelfAlignmentHandled provides true) {
            if (lineSpec != null && resolvedSizes == null &&
                lineSpec.items.any { it.basisPx == null }
            ) {
                // Wave-9 intrinsic pass: content-sized bases are measured
                // (max-content, §9.2.3.E) inside this Layout, then the same
                // §9.7 loop pins every item — no more weight fallback here.
                com.styleconverter.runtime.layout.flexbox.FlexIntrinsicRow(
                    contentMainPx = lineSpec.contentMainPx,
                    gapPx = lineSpec.gapPx,
                    items = lineSpec.items,
                    cross = rowCrossPlacements(sortedChildren),
                    arrangement = mainArrangement,
                    containerCross = crossAlignment
                ) {
                    sortedChildren.forEach { child ->
                        // One Box per child keeps the measurable list 1:1
                        // with the item list regardless of what the child
                        // renders to. propagateMinConstraints forwards the
                        // EXACT width FlexIntrinsicRow measures with into
                        // the component root — a plain Box would relax min
                        // to 0 and the unsized child would hug its text
                        // inside the resolved slot (visible as gaps between
                        // grown items on the first flex-auto capture).
                        Box(propagateMinConstraints = true) {
                            RenderComponent(child, Modifier)
                        }
                    }
                }
                return@CompositionLocalProvider
            }
            sortedChildren.forEachIndexed { index, child ->
                // v2 placement contract: one union read per arriving child;
                // this flex container consumes ONLY the flex block + the
                // shared alignment claim (css-align-3 §6.4) — grid claims
                // on the same child are inert here, like `grid-area` on a
                // flex child in a browser (design §2.2 resolution rule).
                val childPlacement = com.styleconverter.runtime.core.placement
                    .ItemPlacementExtractor.extract(child.properties)
                val alignSelf = childPlacement.alignSelf
                val flexGrow = childPlacement.flex.grow
                // css-flexbox-1 §4.1: an absolutely-positioned child of a
                // flex container is NOT a flex item — it takes no flex
                // sizing (grow/shrink/resolved main size) and only borrows
                // the container's align/justify values to derive its STATIC
                // position (as if it were the sole flex item). The align
                // branch below still runs for it (that IS the static
                // position); the sizing branches are gated off it.
                val childIsOutOfFlow = isOutOfFlowChild(child.properties)
                // `align-self: stretch` only stretches an item whose cross
                // size is AUTO (css-flexbox-1 §8.3); with a definite cross
                // size it behaves as flex-start. Row cross axis = vertical.
                // §4.1: stretch never stretches an out-of-flow child either
                // (static position treats it as flex-start).
                val childHeightDefinite = hasDefiniteSize(child.properties, widthAxis = false)
                val stretches = alignSelf == AlignSelf.STRETCH &&
                    !childHeightDefinite && !childIsOutOfFlow
                // Wave 19 (lane FLEX): the FULL static-position resolve —
                // physical (x,y) claims from the RAW flex-direction /
                // writing-mode / direction wire (never the extractDisplayConfig
                // fold, which erases *_REVERSE) + align-self (child, both
                // channels) + justify-content-as-sole-item (container).
                // WPT-capture-gated: the dark-stage 327 corpus keeps the
                // wave-18 machinery below byte-identically.
                val absposStatic = if (childIsOutOfFlow && wptMode)
                    com.styleconverter.runtime.layout.flexbox.AbsposStaticPosition
                        .resolveStatic(component.properties, child.properties) else null
                // Definite container CONTENT extents (px) — the internal
                // placement basis; null (auto/percent) keeps the wave-18
                // fits-fallback (align modifier + overflow-only shift).
                val absposCbW = if (absposStatic != null)
                    com.styleconverter.runtime.layout.flexbox.AbsposStaticPosition
                        .definiteExtentPx(component.properties, vertical = false) else null
                val absposCbH = if (absposStatic != null)
                    com.styleconverter.runtime.layout.flexbox.AbsposStaticPosition
                        .definiteExtentPx(component.properties, vertical = true) else null
                // The Row arrangement (justify-content) already places the
                // reported box on X for TYPED keywords — internal x must
                // stand down then or the offset double-counts (Generic-only
                // justify never reaches the arrangement, so it stays).
                val absposX = absposStatic?.x?.takeUnless { absposStatic.justifyTyped }
                val absposY = absposStatic?.y
                // Internal ownership per axis: claim + definite extent.
                val internalY = absposY != null && absposCbH != null
                // Lane FLEX-SAFE (wave 18, kept as the non-WPT path): the
                // CROSS-only resolve — align-self incl. safe/unsafe via the
                // Generic wire; a vertical inset stands it down (§3.5).
                val absposCross = if (childIsOutOfFlow && absposStatic == null &&
                    !com.styleconverter.runtime.layout.flexbox.AbsposStaticAlignment
                        .hasCrossInset(child.properties, vertical = true)
                ) com.styleconverter.runtime.layout.flexbox.AbsposStaticAlignment
                    .resolveCross(child.properties) else null

                // Build modifier with align and weight
                var childModifier: Modifier = Modifier
                // Wave 19: the loop's ALIGN-modifier decision for the
                // resolver path (y is the Row loop's alignable axis).
                val staticAlign: Alignment.Vertical? = when {
                    absposStatic == null -> null // non-WPT path decides below
                    // Internal placement owns y — pin the reported box to
                    // the TOP so the internal offset measures from the
                    // container's physical start edge regardless of the
                    // Row's align-items-derived verticalAlignment.
                    internalY -> Alignment.Top
                    // Claim without a definite extent — the wave-18 fits
                    // fallback, but by the PHYSICAL outcome: a reversed
                    // cross axis flips start/end (css-flexbox-1 §5), and
                    // the overflow half still corrects inside the measure.
                    absposY != null -> when (absposY.base) {
                        com.styleconverter.runtime.layout.flexbox.AbsposStaticAlignment.Base.START ->
                            if (absposY.reversed) Alignment.Bottom else Alignment.Top
                        com.styleconverter.runtime.layout.flexbox.AbsposStaticAlignment.Base.CENTER ->
                            Alignment.CenterVertically
                        com.styleconverter.runtime.layout.flexbox.AbsposStaticAlignment.Base.END ->
                            if (absposY.reversed) Alignment.Top else Alignment.Bottom
                    }
                    // css-position-3 §3.5: an explicit NON-auto inset
                    // REPLACES the static position on the axis — the
                    // child's own PositionApplier owns the offset, so
                    // alignment must pin to the padding-box origin
                    // (RC-A5b's Android half: dynamic-align-self-001
                    // painted the green inset-anchored child 100px low
                    // via align(Bottom)). Auto-tolerant reader (skeptic
                    // fix): `top: auto` keeps the static position, so an
                    // auto-only inset falls through to the legacy branch.
                    com.styleconverter.runtime.layout.flexbox.AbsposStaticPosition
                        .hasNonAutoInset(child.properties, vertical = true) -> Alignment.Top
                    // No claim at all → the legacy branch below (container
                    // alignment inheritance / stretch handling) stands.
                    else -> null
                }
                childModifier = if (staticAlign != null) {
                    childModifier.align(staticAlign)
                } else if (absposCross != null) when (absposCross.base) {
                    // Static-position base alignment for the REPORTED
                    // (constraint-fitting) box — the overflow half of the
                    // same alignment happens inside absposOverflowMeasure.
                    // Base values mirror the typed arms below 1:1, so a
                    // typed CENTER routes identically through either path.
                    com.styleconverter.runtime.layout.flexbox.AbsposStaticAlignment.Base.START ->
                        childModifier.align(Alignment.Top)
                    com.styleconverter.runtime.layout.flexbox.AbsposStaticAlignment.Base.CENTER ->
                        childModifier.align(Alignment.CenterVertically)
                    com.styleconverter.runtime.layout.flexbox.AbsposStaticAlignment.Base.END ->
                        childModifier.align(Alignment.Bottom)
                } else when (alignSelf) {
                    AlignSelf.FLEX_START -> childModifier.align(Alignment.Top)
                    AlignSelf.FLEX_END -> childModifier.align(Alignment.Bottom)
                    AlignSelf.CENTER -> childModifier.align(Alignment.CenterVertically)
                    // Definite-height stretch = flex-start (see above). The
                    // auto-height case fills below instead of aligning.
                    AlignSelf.STRETCH -> if (stretches) childModifier.fillMaxHeight()
                        else childModifier.align(Alignment.Top)
                    else -> childModifier
                }

                // LAST-RESORT legacy weight: only reachable when the line is
                // genuinely unmeasurable ahead of layout — the container's
                // main size is definite-but-not-px (percentage / relative
                // units the parser couldn't resolve), so neither the static
                // §9.7 pass nor the intrinsic pass has a free-space budget.
                // Logged so unexpected fallbacks surface in capture runs.
                // Out-of-flow children never take weight (§4.1: flex-grow
                // is a flex-ITEM property and they aren't items).
                if (resolvedSizes == null && flexGrow > 0f && mainSizeDefinite && !childIsOutOfFlow) {
                    android.util.Log.i(
                        "FlexSizeResolver",
                        "legacy weight fallback for ${component.id}/${child.id}: " +
                            "container main size not px-resolvable (row axis)"
                    )
                    childModifier = childModifier.weight(flexGrow)
                }

                // Resolved main size pins the child's width OUTERMOST so
                // basis/grow/shrink win over the placeholder's 50dp floor
                // (FR_GrowBasis b/c grew 40→93/145 on web; FR_ShrinkBasis
                // b shrank 100→58 — pixel-verified against web captures).
                var itemModifier: Modifier = Modifier
                if (childIsOutOfFlow) {
                    // Wave 8: an abspos/fixed child is sized by its OWN
                    // properties (css-position-3 §2.1), never by flex
                    // resolution or the container's constraint envelope —
                    // measure it unbounded so its specified size wins and
                    // overflows the container like the WPT refs
                    // (flex-abspos-staticpos-align-self-safe-001/002: a
                    // 69px box spilling out of a 50px flex container that
                    // Android was clamping to fit, natives 0.86-0.92).
                    // Wave 19: in WPT capture the FULL physical resolver
                    // places both axes (reverse directions + writing
                    // modes); the dark-stage path keeps the wave-18
                    // cross-only modifier byte-identically.
                    itemModifier = if (absposStatic != null)
                        absposStaticMeasure(absposX, absposY, absposCbW, absposCbH)
                    else
                        absposOverflowMeasure(absposCross, crossIsVertical = true)
                } else {
                    // Wave 25 CAL-RC4: the pin must be UNCLAMPED. Row hands
                    // each child only the main-axis space its predecessors
                    // left, and Modifier.width() constrains its request into
                    // that — so an overflowing line (6×50px `flex-shrink: 0`
                    // items in a 200px container, css-gaps
                    // flex-gap-decorations-008) squeezed its tail items to
                    // slivers instead of overflowing. §9.7's output IS the
                    // used main size; flexMainWidthPin forces it and lets the
                    // surplus overflow. Identical to Modifier.width whenever
                    // the line fits (the forced constraints are then already
                    // inside the incoming ones).
                    resolvedSizes?.get(index)?.let {
                        itemModifier = itemModifier.flexMainWidthPin(it.toFloat().dp)
                    }
                    if (stretches) itemModifier = itemModifier.fillMaxHeight()
                }

                Box(modifier = childModifier) {
                    RenderComponent(child, itemModifier)
                }
            }
            }
        } else {
            PlaceholderContent(component.name, textColor, component.properties)
        }
    }

    /**
     * Render content in Column scope with AlignSelf, FlexGrow, and Order support.
     *
     * [mainArrangement] / [crossAlignment] mirror RenderRowContent: the
     * caller's Column arrangement values, re-used by the intrinsic path.
     */
    @Composable
    fun ColumnScope.RenderColumnContent(
        component: IRComponent,
        textColor: Color?,
        mainArrangement: Arrangement.Vertical = Arrangement.Top,
        crossAlignment: Alignment.Horizontal = Alignment.Start
    ) {
        if (!component.children.isNullOrEmpty()) {
            // Same css-flexbox-1 §9.7 rule as RenderRowContent, but the main
            // axis of a column flex container is BLOCK (height): free space
            // for flex-grow only exists when the container's height is
            // definite. Compose's weight would otherwise stretch the Column
            // to the full canvas height.
            val mainSizeDefinite = hasDefiniteSize(component.properties, widthAxis = false)
            // Wave 19 (lane FLEX): same WPT gate as the row loop — the
            // abspos static resolver never runs on the dark-stage corpus.
            val wptMode = LocalWptCaptureMode.current
            // Sort children by order property
            val sortedChildren = sortByOrder(component.children)
            // §9.7 line inputs + static resolve — column main axis is
            // vertical (height). Same three-way outcome as the row path.
            val lineSpec = flexLineSpec(component, sortedChildren, rowAxis = false)
            val resolvedSizes = lineSpec?.let {
                com.styleconverter.runtime.layout.flexbox.FlexSizeResolver
                    .resolve(it.contentMainPx, it.gapPx, it.items)
            }
            // Same suppression rationale as RenderRowContent.
            CompositionLocalProvider(LocalSelfAlignmentHandled provides true) {
            if (lineSpec != null && resolvedSizes == null &&
                lineSpec.items.any { it.basisPx == null }
            ) {
                // Wave-9 intrinsic pass, block-axis flavour: content-sized
                // bases come from maxIntrinsicHeight at the container width.
                com.styleconverter.runtime.layout.flexbox.FlexIntrinsicColumn(
                    contentMainPx = lineSpec.contentMainPx,
                    gapPx = lineSpec.gapPx,
                    items = lineSpec.items,
                    cross = columnCrossPlacements(sortedChildren),
                    arrangement = mainArrangement,
                    containerCross = crossAlignment
                ) {
                    sortedChildren.forEach { child ->
                        // 1:1 measurable-per-item wrapper with the same
                        // min-constraint propagation as the row path (the
                        // resolved HEIGHT must reach the component root).
                        Box(propagateMinConstraints = true) {
                            RenderComponent(child, Modifier)
                        }
                    }
                }
                return@CompositionLocalProvider
            }
            sortedChildren.forEachIndexed { index, child ->
                // Same v2 single-union read as RenderRowContent — column
                // flex consumes only its own claim kinds.
                val childPlacement = com.styleconverter.runtime.core.placement
                    .ItemPlacementExtractor.extract(child.properties)
                val alignSelf = childPlacement.alignSelf
                val flexGrow = childPlacement.flex.grow
                // css-flexbox-1 §4.1 out-of-flow gate — same rationale as
                // the row loop: abspos/fixed children are not flex items;
                // alignment still derives their static position but every
                // sizing branch is gated off below.
                val childIsOutOfFlow = isOutOfFlowChild(child.properties)
                // Wave 19 (lane FLEX) — column twin of the row loop's full
                // resolve: physical (x,y) claims from the RAW wire, WPT
                // gated (dark-stage keeps the wave-18 machinery below).
                val absposStatic = if (childIsOutOfFlow && wptMode)
                    com.styleconverter.runtime.layout.flexbox.AbsposStaticPosition
                        .resolveStatic(component.properties, child.properties) else null
                // Definite container CONTENT extents (same read as the row
                // loop) — null keeps the wave-18 fits-fallback per axis.
                val absposCbW = if (absposStatic != null)
                    com.styleconverter.runtime.layout.flexbox.AbsposStaticPosition
                        .definiteExtentPx(component.properties, vertical = false) else null
                val absposCbH = if (absposStatic != null)
                    com.styleconverter.runtime.layout.flexbox.AbsposStaticPosition
                        .definiteExtentPx(component.properties, vertical = true) else null
                // The Column arrangement (justify-content) places the
                // reported box on Y for TYPED keywords — internal y stands
                // down then (arrangement double-count gate, row-loop twin).
                val absposY = absposStatic?.y?.takeUnless { absposStatic.justifyTyped }
                val absposX = absposStatic?.x
                // Internal ownership of the loop's alignable axis (x).
                val internalX = absposX != null && absposCbW != null
                // Lane FLEX-SAFE (wave 18, kept as the non-WPT path) —
                // the column cross axis is HORIZONTAL, so the inset gate
                // checks left/right (css-position-3 §3.5) and the base
                // maps to horizontal alignments below.
                val absposCross = if (childIsOutOfFlow && absposStatic == null &&
                    !com.styleconverter.runtime.layout.flexbox.AbsposStaticAlignment
                        .hasCrossInset(child.properties, vertical = false)
                ) com.styleconverter.runtime.layout.flexbox.AbsposStaticAlignment
                    .resolveCross(child.properties) else null

                // Build modifier with align and weight
                var childModifier: Modifier = Modifier
                // Wave 19: align-modifier decision for the resolver path
                // (x is the Column loop's alignable axis) — row-loop twin.
                val staticAlign: Alignment.Horizontal? = when {
                    absposStatic == null -> null // non-WPT path decides below
                    // Internal placement owns x — pin the reported box to
                    // the START edge so the internal offset measures from
                    // the container's physical left edge regardless of the
                    // Column's align-items-derived horizontalAlignment.
                    internalX -> Alignment.Start
                    // Claim without a definite extent — wave-18 fits
                    // fallback by the PHYSICAL outcome (reversed flips
                    // start/end); the overflow half corrects inside.
                    absposX != null -> when (absposX.base) {
                        com.styleconverter.runtime.layout.flexbox.AbsposStaticAlignment.Base.START ->
                            if (absposX.reversed) Alignment.End else Alignment.Start
                        com.styleconverter.runtime.layout.flexbox.AbsposStaticAlignment.Base.CENTER ->
                            Alignment.CenterHorizontally
                        com.styleconverter.runtime.layout.flexbox.AbsposStaticAlignment.Base.END ->
                            if (absposX.reversed) Alignment.Start else Alignment.End
                    }
                    // css-position-3 §3.5: an explicit NON-auto inset
                    // replaces the static position — pin to the
                    // padding-box origin so the child's own
                    // PositionApplier owns the offset (RC-A5b's Android
                    // half, column flavour). Auto-tolerant reader
                    // (skeptic fix) — see the row-loop twin.
                    com.styleconverter.runtime.layout.flexbox.AbsposStaticPosition
                        .hasNonAutoInset(child.properties, vertical = false) -> Alignment.Start
                    // No claim → the legacy branch below stands.
                    else -> null
                }
                childModifier = if (staticAlign != null) {
                    childModifier.align(staticAlign)
                } else if (absposCross != null) when (absposCross.base) {
                    // Static-position base alignment of the reported box
                    // (overflow half lives in absposOverflowMeasure) —
                    // horizontal mirror of the row loop's mapping.
                    com.styleconverter.runtime.layout.flexbox.AbsposStaticAlignment.Base.START ->
                        childModifier.align(Alignment.Start)
                    com.styleconverter.runtime.layout.flexbox.AbsposStaticAlignment.Base.CENTER ->
                        childModifier.align(Alignment.CenterHorizontally)
                    com.styleconverter.runtime.layout.flexbox.AbsposStaticAlignment.Base.END ->
                        childModifier.align(Alignment.End)
                } else when (alignSelf) {
                    AlignSelf.FLEX_START -> childModifier.align(Alignment.Start)
                    AlignSelf.FLEX_END -> childModifier.align(Alignment.End)
                    AlignSelf.CENTER -> childModifier.align(Alignment.CenterHorizontally)
                    // Column cross axis is INLINE (width). The web harness
                    // wraps every unsized child in `width: fit-content`,
                    // which makes the cross size non-auto — so per
                    // css-flexbox-1 §8.3 stretch never actually stretches
                    // there and behaves as flex-start (pixel-verified:
                    // FC_AlignSelf `d` sits at content-left x=22 on web,
                    // not full-width). Mirror that: align Start, no fill.
                    // Compose's Column default would otherwise CENTER the
                    // child whenever align-items:center is set (0.963 row).
                    AlignSelf.STRETCH -> childModifier.align(Alignment.Start)
                    else -> childModifier
                }

                // LAST-RESORT legacy weight — same gate + logging as the row
                // path: only percentage / unresolvable container heights land
                // here now that content-sized bases go through the intrinsic
                // pass above. Out-of-flow children never take weight (§4.1).
                if (resolvedSizes == null && flexGrow > 0f && mainSizeDefinite && !childIsOutOfFlow) {
                    android.util.Log.i(
                        "FlexSizeResolver",
                        "legacy weight fallback for ${component.id}/${child.id}: " +
                            "container main size not px-resolvable (column axis)"
                    )
                    childModifier = childModifier.weight(flexGrow)
                }

                // Resolved main size pins the child height (FC_GrowBasis
                // b/c grew 30→66/101 on web — pixel-verified).
                var itemModifier: Modifier = Modifier
                if (childIsOutOfFlow) {
                    // Wave 8: unbounded measure for the out-of-flow child —
                    // specified size wins and overflows the container
                    // (css-position-3 §2.1; see the row-loop twin for the
                    // fixture evidence). Wave 19: WPT capture routes through
                    // the full physical resolver; dark-stage keeps the
                    // wave-18 cross-only modifier byte-identically.
                    itemModifier = if (absposStatic != null)
                        absposStaticMeasure(absposX, absposY, absposCbW, absposCbH)
                    else
                        absposOverflowMeasure(absposCross, crossIsVertical = false)
                } else {
                    // Wave 25 CAL-RC4, column twin: same unclamped pin, block
                    // axis. A Column squeezes its tail children exactly the
                    // way a Row squeezes its tail items once the resolved
                    // sizes exceed the container's height.
                    resolvedSizes?.get(index)?.let {
                        itemModifier = itemModifier.flexMainHeightPin(it.toFloat().dp)
                    }
                }

                Box(modifier = childModifier) {
                    RenderComponent(child, itemModifier)
                }
            }
            }
        } else {
            PlaceholderContent(component.name, textColor, component.properties)
        }
    }

    /**
     * The extracted inputs of one non-wrapping flex line, in IR px (== dp)
     * units. Produced by [flexLineSpec]; consumed by BOTH resolution paths:
     * the static §9.7 pass (every base definite → [resolveFlexMainSizes])
     * and the wave-9 intrinsic-measure pass (some base content-sized →
     * FlexIntrinsicRow/Column fill the bases at measure time, #40).
     */
    internal data class FlexLineSpec(
        val contentMainPx: Double,
        val gapPx: Double,
        val items: List<com.styleconverter.runtime.layout.flexbox.FlexSizeResolver.Item>
    )

    /**
     * Statically run css-flexbox-1 §9.7 for a non-wrapping flex line.
     *
     * Returns the used main size (px) per child in [sortedChildren] order,
     * or null when the line isn't statically resolvable:
     *   - container main size isn't a definite px value, or
     *   - no child declares any flex property (nothing to resolve — keeps
     *     the legacy path byte-identical for plain rows/columns), or
     *   - some child's flex base is content-sized (flex-basis auto without
     *     a definite main-size property) — the renderer then hands the
     *     SAME spec to the intrinsic-measure layout instead of bailing to
     *     the legacy weight fallback (wave-9, #40).
     */
    internal fun resolveFlexMainSizes(
        component: IRComponent,
        sortedChildren: List<IRComponent>,
        rowAxis: Boolean
    ): List<Double>? = flexLineSpec(component, sortedChildren, rowAxis)?.let { spec ->
        com.styleconverter.runtime.layout.flexbox.FlexSizeResolver
            .resolve(spec.contentMainPx, spec.gapPx, spec.items)
    }

    /**
     * Extract the flex-line inputs (container content main size, gap, one
     * resolver Item per child) from the IR, or null when the line doesn't
     * participate in flex resolution at all:
     *   - no child declares any flex property (plain row/column), or
     *   - the container's main size isn't a definite px value (percentage /
     *     relative-unit sizes are only knowable at layout time — those keep
     *     the legacy weight fallback, logged at the call site).
     *
     * The per-item minimum mirrors the WEB harness wrapper
     * (`ComponentRenderer.tsx`): min = main-size ?? min-size ?? placeholder
     * floor (50px inline / 30px block) — that floor is what web's flex
     * algorithm clamps against (FR_GrowBasis `a`: basis 40 → rendered 50).
     * The maximum is the declared max-size property (max-width/max-height,
     * css-flexbox-1 §9.7.4.d max violations), +∞ when absent.
     */
    internal fun flexLineSpec(
        component: IRComponent,
        sortedChildren: List<IRComponent>,
        rowAxis: Boolean
    ): FlexLineSpec? {
        // Only engage when some child actually declares a flex property —
        // otherwise this is a plain Row/Column and legacy behaviour stands.
        val anyFlex = sortedChildren.any { c ->
            c.properties.any { it.type == "FlexBasis" || it.type == "FlexGrow" || it.type == "FlexShrink" }
        }
        if (!anyFlex) return null

        val props = component.properties
        // Declared main size (border-box — web sets box-sizing: border-box
        // globally, and the Compose chain is width→…→padding-last which is
        // border-box too).
        val mainPx = (if (rowAxis) pxOf(props, "Width", "InlineSize")
            else pxOf(props, "Height", "BlockSize")) ?: return null
        // Content box = declared size minus padding + border bands on the
        // main axis (both consume interior space under border-box).
        val padStart = (if (rowAxis) pxOf(props, "PaddingLeft", "PaddingInlineStart")
            else pxOf(props, "PaddingTop", "PaddingBlockStart")) ?: 0.0
        val padEnd = (if (rowAxis) pxOf(props, "PaddingRight", "PaddingInlineEnd")
            else pxOf(props, "PaddingBottom", "PaddingBlockEnd")) ?: 0.0
        val borderStart = (if (rowAxis) pxOf(props, "BorderLeftWidth")
            else pxOf(props, "BorderTopWidth")) ?: 0.0
        val borderEnd = (if (rowAxis) pxOf(props, "BorderRightWidth")
            else pxOf(props, "BorderBottomWidth")) ?: 0.0
        val contentMain = mainPx - padStart - padEnd - borderStart - borderEnd

        // Main-axis gap: column-gap separates row items, row-gap column items.
        val gap = (if (rowAxis) pxOf(props, "ColumnGap", "Gap")
            else pxOf(props, "RowGap", "Gap")) ?: 0.0

        val items = sortedChildren.map { child ->
            val cp = child.properties
            val mainSize = if (rowAxis) pxOf(cp, "Width", "InlineSize")
                else pxOf(cp, "Height", "BlockSize")
            val minSize = if (rowAxis) pxOf(cp, "MinWidth", "MinInlineSize")
                else pxOf(cp, "MinHeight", "MinBlockSize")
            // Declared main-axis maximum — feeds the §9.7.4.d max-violation
            // clamp (an item never grows past its max-width/max-height).
            val maxSize = if (rowAxis) pxOf(cp, "MaxWidth", "MaxInlineSize")
                else pxOf(cp, "MaxHeight", "MaxBlockSize")
            // v2 placement contract: the child's flex claims come from
            // the single ITEM union (grow 0 / shrink 1 / basis auto are
            // the CSS-initial defaults the union carries for absent
            // fields — design §2.2 resolution rule).
            val flexClaims = com.styleconverter.runtime.core.placement
                .ItemPlacementExtractor.extract(cp).flex
            com.styleconverter.runtime.layout.flexbox.FlexSizeResolver.Item(
                // Used flex basis: flex-basis, else the main-size property,
                // else content (null → filled by the intrinsic pass).
                basisPx = flexClaims.basisPx ?: mainSize,
                grow = flexClaims.grow.toDouble(),
                shrink = flexClaims.shrink.toDouble(),
                // Web wrapper: minWidth = width || min-width || 50px (30px
                // floor on the block axis).
                minPx = mainSize ?: minSize ?: (if (rowAxis) 50.0 else 30.0),
                maxPx = maxSize ?: Double.POSITIVE_INFINITY
            )
        }
        return FlexLineSpec(contentMain, gap, items)
    }

    /**
     * Wave 25 CAL-RC5 — the wrapping ROW's cross-placement plan, or null to
     * keep the frozen FlowRow path.
     *
     * Returns a list index-aligned with `sortByOrder(component.children)`
     * (the order FlexWrapRow's content lambda emits) ONLY when at least one
     * item genuinely stretches, so a wrapping container that needs nothing
     * from the custom layout never enters it.
     *
     * STRETCH ELIGIBILITY is the CSS rule, not an approximation:
     * css-flexbox-1 §8.3 — `align-self: auto` resolves to the container's
     * `align-items`, whose initial value `normal` behaves as `stretch` in a
     * flex container (which is why extractDisplayConfig defaults alignItems
     * to STRETCH), and stretch only applies to an item whose CROSS size
     * (height, for a row) is `auto`.
     *
     * The four null gates are the RenderContent features FlexWrapRow's
     * content lambda does not reproduce. Each is a real behaviour, not a
     * hedge — falling into the wrap layout would silently drop it:
     *   1. a leading `_text` run (rendered as an inline sibling),
     *   2. list markers (`<li>` children of a list parent),
     *   3. non-static children (abspos/fixed get RenderAbsoluteChild; a
     *      negative-z relative child gets the backdrop layer),
     *   4. no children at all (the childless case is demoted to Box far
     *      above this call, so this is belt-and-braces).
     */
    private fun wrapRowStretchPlan(
        component: IRComponent,
        displayConfig: DisplayConfig
    ): List<com.styleconverter.runtime.layout.flexbox.FlexCrossPlacement>? {
        val children = component.children
        if (children.isNullOrEmpty()) return null
        if (!component._text.isNullOrEmpty()) return null
        if (ListStyleExtractor.uaMarkerDefault(component._tag?.lowercase()) != null) return null
        if (children.any { extractPositionType(it.properties) != PositionType.STATIC }) return null
        // `normal` and `stretch` both arrive here as STRETCH — see the
        // extractDisplayConfig AlignItems mapping's `else` arm.
        val containerStretches = displayConfig.alignItems == AlignItems.STRETCH
        val plan = sortByOrder(children).map { child ->
            val alignSelf = com.styleconverter.runtime.core.placement
                .ItemPlacementExtractor.extract(child.properties).alignSelf
            // A declared height makes the item's cross size definite, and
            // §8.3 then degrades stretch to flex-start.
            val crossAuto = !hasDefiniteSize(child.properties, widthAxis = false)
            when (alignSelf) {
                AlignSelf.FLEX_START -> com.styleconverter.runtime.layout.flexbox.FlexCrossPlacement.START
                AlignSelf.FLEX_END -> com.styleconverter.runtime.layout.flexbox.FlexCrossPlacement.END
                AlignSelf.CENTER -> com.styleconverter.runtime.layout.flexbox.FlexCrossPlacement.CENTER
                // Baseline has no Compose cross-axis equivalent — the same
                // flex-start approximation FlexboxApplier documents.
                AlignSelf.BASELINE -> com.styleconverter.runtime.layout.flexbox.FlexCrossPlacement.START
                AlignSelf.STRETCH ->
                    if (crossAuto) com.styleconverter.runtime.layout.flexbox.FlexCrossPlacement.STRETCH
                    else com.styleconverter.runtime.layout.flexbox.FlexCrossPlacement.START
                // AUTO → inherit the container's align-items.
                AlignSelf.AUTO ->
                    if (containerStretches && crossAuto)
                        com.styleconverter.runtime.layout.flexbox.FlexCrossPlacement.STRETCH
                    else com.styleconverter.runtime.layout.flexbox.FlexCrossPlacement.DEFAULT
            }
        }
        return plan.takeIf {
            it.contains(com.styleconverter.runtime.layout.flexbox.FlexCrossPlacement.STRETCH)
        }
    }

    /**
     * Per-child cross placements for the intrinsic ROW layout — the same
     * `align-self` mapping the static path applies via RowScope.align:
     * flex-start→top, flex-end→bottom, center→center, stretch→fill ONLY
     * when the child's cross (height) is auto (css-flexbox-1 §8.3), and
     * auto/absent→DEFAULT (container align-items decides).
     */
    private fun rowCrossPlacements(
        sortedChildren: List<IRComponent>
    ): List<com.styleconverter.runtime.layout.flexbox.FlexCrossPlacement> =
        sortedChildren.map { child ->
            // Single ITEM-union read, same as the static loop.
            val alignSelf = com.styleconverter.runtime.core.placement
                .ItemPlacementExtractor.extract(child.properties).alignSelf
            when (alignSelf) {
                AlignSelf.FLEX_START -> com.styleconverter.runtime.layout.flexbox.FlexCrossPlacement.START
                AlignSelf.FLEX_END -> com.styleconverter.runtime.layout.flexbox.FlexCrossPlacement.END
                AlignSelf.CENTER -> com.styleconverter.runtime.layout.flexbox.FlexCrossPlacement.CENTER
                // Definite-height stretch degrades to flex-start (§8.3).
                AlignSelf.STRETCH ->
                    if (!hasDefiniteSize(child.properties, widthAxis = false))
                        com.styleconverter.runtime.layout.flexbox.FlexCrossPlacement.STRETCH
                    else com.styleconverter.runtime.layout.flexbox.FlexCrossPlacement.START
                else -> com.styleconverter.runtime.layout.flexbox.FlexCrossPlacement.DEFAULT
            }
        }

    /**
     * Column twin of [rowCrossPlacements]. STRETCH maps to START outright:
     * the web reference wraps unsized children in `width: fit-content`
     * (a non-auto cross size), so §8.3 stretch never actually stretches
     * there — mirrored from the static column loop's STRETCH→Start.
     */
    private fun columnCrossPlacements(
        sortedChildren: List<IRComponent>
    ): List<com.styleconverter.runtime.layout.flexbox.FlexCrossPlacement> =
        sortedChildren.map { child ->
            val alignSelf = com.styleconverter.runtime.core.placement
                .ItemPlacementExtractor.extract(child.properties).alignSelf
            when (alignSelf) {
                AlignSelf.FLEX_START, AlignSelf.STRETCH ->
                    com.styleconverter.runtime.layout.flexbox.FlexCrossPlacement.START
                AlignSelf.FLEX_END -> com.styleconverter.runtime.layout.flexbox.FlexCrossPlacement.END
                AlignSelf.CENTER -> com.styleconverter.runtime.layout.flexbox.FlexCrossPlacement.CENTER
                else -> com.styleconverter.runtime.layout.flexbox.FlexCrossPlacement.DEFAULT
            }
        }

    /**
     * First px-resolvable value among [types], reading both length IR
     * shapes: `{"type":"length","px":N}` (Width/Height/Gap) and the bare
     * `{"px":N}` SizeValue shape (padding, logical sizes, borders).
     */
    private fun pxOf(properties: List<IRProperty>, vararg types: String): Double? {
        for (t in types) {
            val data = properties.firstOrNull { it.type == t }?.data ?: continue
            val obj = data as? JsonObject ?: continue
            val px = obj["px"]?.jsonPrimitive?.doubleOrNull
            if (px != null) return px
        }
        return null
    }

    // NOTE (v2 placement contract): the private flexBasisPx /
    // extractFlexShrink / extractFlexGrow copies were DELETED — every
    // flex claim now flows through the single ITEM union
    // (core/placement/ItemPlacementExtractor), read once per child in
    // RenderRowContent / RenderColumnContent / resolveFlexMainSizes.

    /**
     * Extract align-self value from properties.
     *
     * Internal (not private) so the unit test can pin the LIVE render-path
     * mapping directly — see ComponentRendererAlignSelfTest. The parsing
     * itself moved to core/placement/ItemPlacementExtractor (the v2
     * single-owner rule for ITEM-scoped properties — this delegation is
     * what guarantees a future keyword fix can no longer land in a dead
     * copy while the live path lags, the original ANCHOR_CENTER failure
     * mode).
     */
    internal fun extractAlignSelf(properties: List<IRProperty>): AlignSelf =
        com.styleconverter.runtime.core.placement.ItemPlacementExtractor.alignSelf(properties)

    enum class AlignSelf {
        AUTO, FLEX_START, FLEX_END, CENTER, STRETCH, BASELINE
    }

    /**
     * Justify-self values for grid item alignment along the inline (row) axis.
     */
    enum class JustifySelf {
        AUTO, NORMAL, START, END, CENTER, STRETCH, FLEX_START, FLEX_END, SELF_START, SELF_END, LEFT, RIGHT, BASELINE
    }

    /**
     * Extract order value from properties.
     * CSS order: integer (default 0), lower values appear first.
     * Delegates to the single ITEM-scope owner (core/placement).
     */
    fun extractOrder(properties: List<IRProperty>): Int =
        com.styleconverter.runtime.core.placement.ItemPlacementExtractor.order(properties)

    /**
     * Extract justify-self value from properties.
     * Delegates to the single ITEM-scope owner (core/placement).
     */
    fun extractJustifySelf(properties: List<IRProperty>): JustifySelf =
        com.styleconverter.runtime.core.placement.ItemPlacementExtractor.justifySelf(properties)

    /**
     * Sort children by their order property.
     * Items with lower order values appear first.
     * Items with same order maintain their source order (stable sort).
     */
    fun sortByOrder(children: List<IRComponent>): List<IRComponent> {
        return children.mapIndexed { index, child -> IndexedChild(index, child) }
            .sortedWith(compareBy(
                { extractOrder(it.child.properties) },
                { it.originalIndex }
            ))
            .map { it.child }
    }

    private data class IndexedChild(val originalIndex: Int, val child: IRComponent)

    /**
     * True when an empty container should be demoted to block layout.
     *
     * Mirrors the web harness (`ComponentRenderer.tsx`): components with no
     * children force `display: block` when their declared display is
     * grid / flex / inline-grid / inline-flex, because track/arrangement
     * layout is meaningless around a lone placeholder label. Pure function
     * (no Compose deps) so the JVM unit suite can pin the rule.
     */
    internal fun demotesEmptyContainer(type: DisplayType, childrenEmpty: Boolean): Boolean {
        if (!childrenEmpty) return false
        return type == DisplayType.FLEX_ROW ||
            type == DisplayType.FLEX_COLUMN ||
            type == DisplayType.GRID
    }

    /**
     * True when the placeholder text node should fill the parent's content
     * width (the Compose analogue of the web placeholder's
     * `display: block` span).
     *
     * CSS: a block-level text container spans 100% of the containing
     * block, which is what makes `text-align: center/right` visible. The
     * web harness wrapper defaults to `width: fit-content` (box hugs the
     * text — alignment is a no-op) UNLESS the IR declares a definite
     * width, in which case the block span stretches to it. We replicate
     * exactly that: fill only when a definite Width/InlineSize is present
     * (`length` with a resolved px, or `percentage`) — never for `auto` /
     * unresolvable (null px) values, where filling would blow the
     * wrap-content box out to the canvas width.
     */
    internal fun placeholderFillsParentWidth(properties: List<IRProperty>): Boolean =
        hasDefiniteSize(properties, widthAxis = true)

    /**
     * True when the IR declares a DEFINITE size on the given axis — an
     * absolute length the parser resolved to px, or a percentage (which
     * resolves against the parent at layout time). `auto`, unresolvable
     * relative units (em/vw/calc → px:null), and absent declarations are
     * all indefinite. Drives both the placeholder block-span rule and the
     * flex-grow gating (free space only exists on a definite main axis).
     */
    internal fun hasDefiniteSize(properties: List<IRProperty>, widthAxis: Boolean): Boolean {
        val types = if (widthAxis) setOf("Width", "InlineSize") else setOf("Height", "BlockSize")
        return properties.any { prop ->
            (prop.type in types) && run {
                val obj = prop.data as? JsonObject ?: return@run false
                when (obj["type"]?.jsonPrimitive?.contentOrNull) {
                    // Absolute length — definite only when the parser
                    // resolved it to px (em/vw/calc serialize px:null).
                    "length" -> obj["px"]?.jsonPrimitive?.doubleOrNull != null
                    // Percentages resolve against the parent at layout
                    // time (SizingApplier → fillMaxWidth(fraction)), so
                    // the box is always definite.
                    "percentage" -> obj["value"]?.jsonPrimitive?.doubleOrNull != null
                    // Logical sizes (InlineSize/BlockSize) ship the bare
                    // SizeValue shape `{"px":N}` with NO type tag — the
                    // Sizing_BoxModel fixture's `inline-size: 250px` was
                    // invisible to this check and the box fell back to the
                    // earlier `width: 240px`.
                    null -> obj["px"]?.jsonPrimitive?.doubleOrNull != null
                    else -> false
                }
            }
        }
    }

    /**
     * Placeholder content showing component name with text styling support.
     *
     * @param name The component name to display
     * @param textColor Optional text color override
     * @param properties IR properties for styling
     * @param itemIndex Index for numbered list markers (default 0)
     * @param rawText Optional verbatim source-text override. When non-null
     *   and non-empty, replaces the underscore-stripped `name` as the
     *   visible string. Powers the IR `_text` channel (swarm-001
     *   css-color__color-001 leaf-text fix, swarm-002 mixed-content fix).
     *   text-transform APPLIES to it — see [placeholderDisplayText].
     *   Absent rawText → existing placeholder behaviour, preserving the
     *   327-pair baseline.
     */
    @Composable
    internal fun PlaceholderContent(
        name: String,
        textColor: Color?,
        properties: List<IRProperty> = emptyList(),
        itemIndex: Int = 0,
        rawText: String? = null,
        // Wave 22 (lane DECOR, B-RC4b) — the merged `meta.decorations`
        // wire for a COLLAPSED inline run: the ordered, ancestor-first
        // list of every decorating box's line and its OWN color
        // (css-text-decor-3 §2.1 propagation + §2.2 per-box color). Wire
        // shape [{line, color?}] with `color` the AUTHORED CSS token
        // (absent = currentColor), turned into this list by
        // DecorationWire.toDecorationLines — see its banner for why the
        // token is resolved in the runtime and not in the converter.
        //
        // THREE STATES, all meaningful:
        //   null  → not a collapsed run (every legacy document): the pass
        //           synthesizes this component's own three flags exactly
        //           as before, so the dark-stage 327 baselines cannot move.
        //   list  → AUTHORITATIVE: it is the complete line set, the
        //           component's own flags are ignored, and the built-ins
        //           are suppressed (see `paintedTextStyle` below).
        //   empty → ALSO authoritative, and it says "no lines": paint
        //           nothing AND still suppress the built-ins, or the
        //           merged flat bag repaints what the wire just retired.
        // The full seam is live: extractor `_decorations` → converter
        // `meta.decorations` → IRDocumentDecoder → IRComponent.decorations
        // → DecorationWire → here.
        decorations: List<com.styleconverter.runtime.typography.DecorationColorOps.DecorationLine>? = null
    ) {
        // When rawText is supplied (the IR's `_text` channel), it wins
        // over the synthesised "Component Name" placeholder. For legacy
        // fixtures with no _text we fall through to the
        // underscore-stripped name path. Both paths run text-transform —
        // see placeholderDisplayText for why (the old rawText bypass was
        // based on a false claim that the extractor pre-transformed it).
        val hasRawText = !rawText.isNullOrEmpty()
        // WPT-capture-mode gate (mirrors the web harness's WPT_MODE label
        // suppression): in WPT capture the browser-reference shows no
        // component-name text, so when we'd fall back to the SYNTHESIZED name
        // (no real `_text`) we render NOTHING. Real leading text (hasRawText)
        // is never suppressed. Default mode → LocalWptCaptureMode is false →
        // this is a no-op, keeping the 327-pair baseline byte-identical.
        if (shouldSuppressSynthesizedName(LocalWptCaptureMode.current, rawText)) return
        var displayText = placeholderDisplayText(name, rawText, properties)

        // Extract tab-size and process tabs in text
        val tabConfig = TextStyleApplier.extractTabSize(properties)
        displayText = TextStyleApplier.applyTabSize(displayText, tabConfig)

        // Wave 37 (lane W7, rule A) — `hyphens: none` (css-text-3 §6.1):
        // U+00AD SOFT HYPHEN must not be a break opportunity. The twin of
        // the iOS PlaceholderLabel strip, and DELIBERATELY KEPT even though
        // it is measured-INERT on Compose today. The measurement, so the
        // next wave does not redo it:
        //
        //   Compose's default is `TextStyle.hyphens = Hyphens.None`, which
        //   sets Minikin's hyphenationFrequency to NONE — and Minikin then
        //   ignores U+00AD outright. So Android ALREADY renders `hyphens:
        //   none` correctly for soft hyphens, and the break it does take in
        //   css-text/hyphens-none-011 is the ordinary emergency CHARACTER
        //   break, not a soft-hyphen break. PROVEN by feeding a per-test IR
        //   with the soft hyphens deleted upstream: the emulator capture
        //   came back byte-identical (sha1 943f2b18…, both runs), i.e. the
        //   glyphs Android breaks between do not depend on U+00AD at all.
        //   iOS is the platform that needed the repair: TextKit honours soft
        //   hyphens unconditionally (device-measured 0.8181 → 0.9017 on that
        //   same test once they are stripped).
        //
        // It stays because it is a GUARD with a named trigger, not
        // decoration: the moment a future wave turns `TextStyle.hyphens =
        // Hyphens.Auto` on — the obvious closing move for
        // `requires-hyphenation-dictionary` once the IR carries a language —
        // Minikin starts honouring U+00AD, and without this strip `hyphens:
        // none` would silently start breaking at soft hyphens on Android.
        // Identity for `manual`/`auto` and for any run without a soft
        // hyphen, so it can cost nothing in the meantime.
        //
        // The keyword is read from THIS component's own list first and the
        // INHERITED channel second: `hyphens` is an inherited property
        // (css-text-3 §6.1; it is in this renderer's `inheritableTypes`
        // table), and the run that paints the glyphs is not always the box
        // that declared it.
        val hyphensMode = TextStyleApplier.extractHyphens(
            if (properties.any { it.type == "Hyphens" }) properties
            else LocalInheritedProperties.current
        ).name
        displayText = com.styleconverter.runtime.typography.wrapping.SoftHyphenPolicy
            .displayString(displayText, hyphensMode)
        // No-silent-fallthrough twin of the iOS HyphensApplier breadcrumb:
        // `auto` is the one keyword neither native can honour in full
        // (Minikin CAN hyphenate, but only against a language the IR does
        // not carry — there is no lang channel on the wire, so the
        // dictionary cannot be selected). The degradation is auto →
        // manual's explicit opportunities, which is exactly right for
        // untagged content (css-text-3 §6.1 makes the resource
        // language-dependent) and a wall for tagged content — named in
        // tools/titan/wpt-not-applicable.mjs as
        // `requires-hyphenation-dictionary`. logUnhandled dedupes by type.
        if (com.styleconverter.runtime.typography.wrapping.SoftHyphenPolicy
                .wantsDictionaryHyphenation(hyphensMode)) {
            com.styleconverter.runtime.PropertyTracker.logUnhandled(
                "Hyphens",
                "hyphens: auto — no hyphenation dictionary can be selected " +
                    "(the IR carries no language); falls back to the explicit " +
                    "opportunities `manual` allows")
        }

        // ── Block-font label branch (cross-platform glyph-wall fix) ──────
        // The SYNTHESIZED component-name label no longer renders through a
        // font stack: it rasterizes via the shared 5x7 block atlas
        // (BlockFont.gen.kt, checksum-pinned cb3c6e411c7b2859) as integer-
        // coordinate 1x1-px rects, so all three platforms paint byte-
        // identical label pixels (fonts were the residual noise capping
        // ~50 text-bearing fixtures at SSIM 0.90-0.949). ONLY the
        // synthesized name takes this path — real leading text (hasRawText,
        // the IR `_text` channel) keeps the full CSS-styled Text pipeline
        // below, exactly like the web/iOS runtimes. This branch sits AFTER
        // the shouldSuppressSynthesizedName gate above, so WPT capture mode
        // still renders NOTHING for synthesized names (gate unchanged).
        // Note: the writing-mode rotation further down no longer applies to
        // the synthesized label — the shared spec pins it as a single
        // horizontal line at (8,6) on every platform.
        if (!hasRawText) {
            // The input is EXACTLY the string this function passed to Text
            // before (underscore-stripped name + text-transform + tab-size
            // applied above); BlockLabelPlaceholder uppercases and maps
            // atlas-unknown characters to '-' itself (the shared transform).
            BlockLabelPlaceholder(displayText)
            return
        }

        // Note: list-style markers are not prepended to placeholder text
        // to match web renderer behavior (web shows plain component name)

        // Extract line-clamp and text-overflow.
        //
        // Default = unlimited: CSS has NO implicit line clamp, and the web
        // placeholder (`PlaceholderContent` in ComponentRenderer.tsx) is a
        // plain block <span> that wraps freely. The previous `?: 2` default
        // silently dropped the 3rd+ line on narrow boxes (Edge_NegativeOffset:
        // web wrapped "Edge Negativ eOffset" onto 3 lines, Compose clipped it
        // to 2 — Android-web SSIM 0.91). Only an explicit line-clamp /
        // -webkit-line-clamp / max-lines IR property may limit lines.
        val maxLines = TextStyleApplier.extractMaxLines(properties) ?: Int.MAX_VALUE
        val textOverflow = TextStyleApplier.extractTextOverflow(properties)

        // Extract text wrap configuration (word-break, overflow-wrap, white-space)
        val wrapConfig = TextStyleApplier.extractTextWrapConfig(properties)

        // Determine max lines based on white-space mode
        // pre and nowrap should not wrap, so use Int.MAX_VALUE for no line limit
        val effectiveMaxLines = when (wrapConfig.softWrap) {
            false -> Int.MAX_VALUE  // No wrapping for nowrap/pre
            true -> maxLines
        }

        // Wave 21 (lane TEXTDECOR, B-RC7) — unbreakable runs must NOT
        // emergency-wrap in composed WPT capture. CSS gives a run with no
        // soft-wrap opportunity (UAX #14: no spaces, no break-after
        // punctuation, no ideographs — DecorationOps.hasSoftWrapOpportunity)
        // ONE overflowing line under `overflow-wrap: normal`; Compose's
        // softWrap=true instead breaks mid-run at the constraint edge (an
        // emergency break CSS reserves for break-word/anywhere). The
        // Chromium refs for text-decoration-dotted-001/002 keep
        // 'fooשלוםbaz' / 'foobarbaz' @92px on ONE line overflowing the
        // 390px canvas — the wrap, not the dots, dominated those scores.
        // Gated on LocalWptComposedMode so the per-component inbox and the
        // 327-pair baseline captures stay byte-identical (flag false
        // there); web twin: ComponentRenderer.tsx suppresses its span's
        // hardcoded `word-break: break-word` under the same composed gate,
        // iOS twin: fixedSize(horizontal:) in its PlaceholderLabel chain.
        val effectiveSoftWrap = wrapConfig.softWrap &&
            !(LocalWptComposedMode.current &&
                !com.styleconverter.runtime.typography.DecorationOps.hasSoftWrapOpportunity(displayText))

        // Extract additional text style properties. The inherited font-size
        // (parent's RESOLVED px FontSize off the inheritance channel —
        // parents always publish resolved px, see DynamicValueResolver.
        // fontSizePxOf) is threaded as the base for the relative
        // font-size values (em / % / smaller / larger, css-values-4
        // §5.1.1 + CSS 2.1 §15.7 resolve against the INHERITED size),
        // matching iOS's inherited-size resolution. Null (no styled
        // ancestor) falls back to the extractor's 16sp browser default —
        // identical outcomes on the committed fixtures, which never style
        // ancestors.
        val inheritedFontSizeSp = com.styleconverter.runtime.core.variables.DynamicValueResolver
            .fontSizePxOf(LocalInheritedProperties.current)
        val textStyle = TextStyleApplier.extractTextStyle(properties, inheritedFontSizeSp)

        // Build final text style with all extracted properties
        // For list-style-position: outside, we'd need padding on the left,
        // but for simplicity we include the marker inline (like "inside")
        // Placeholder default font size = 16.sp to match the web browser's
        // inherited body default (16px). The web placeholder
        // (`PlaceholderContent` in `ComponentRenderer.tsx`) sets no
        // explicit font-size and inherits CapturePage's body size, which
        // is the browser default 16px. Compose previously defaulted to
        // 11.sp here, leaving placeholder boxes ~30% smaller than their
        // web counterparts on every placeholder-only fixture without an
        // explicit `font-size` (Card_Complete, Input_Field, Outline_Solid,
        // Shadow_Simple, Neumorphic_Light, Tag_Chip, …). Box dimensions
        // follow text size under `wrapContentSize`, so the 11→16 bump
        // propagates to overall component geometry — which is what SSIM
        // is comparing across iOS / Android / web.
        val effectiveFontSize = if (textStyle.fontSize != TextUnit.Unspecified) textStyle.fontSize else 16.sp
        // Smart contrast: use dark text on light backgrounds, light text on dark backgrounds
        // Web uses color:inherit (#eee) with opacity:0.7 → rgba(238,238,238,0.7) for dark bg
        //
        // corpus-v4.1 ink sub-boundary — the pick is wrapped in
        // defaultTextInk: in WPT capture (LocalWptCaptureMode) the no-color
        // bottom-out is the spec BLACK (WPT_DEFAULT_TEXT_INK), because a
        // real WPT page's default prose is the UA CanvasText black and the
        // browser-ref now injects `:where(body) { color:#000 }` — the old
        // near-white 0xB3EEEEEE fallback vanished into the v4 white canvas
        // exactly like the ref's old white ink, so default-ink text tests
        // matched VACUOUSLY. Web flips the same way (index.html wpt-mode
        // rule + PlaceholderContent's WPT_MODE '#000000'), iOS via
        // WPTCanvas.textInk. The dark-stage contrast pick inside `run` is
        // untouched — LocalWptCaptureMode is false on every 327-pair path,
        // so those baselines stay byte-identical.
        val defaultPlaceholderColor = defaultTextInk(LocalWptCaptureMode.current, run {
            val bgColor = properties.find { it.type == "BackgroundColor" }?.data?.let {
                com.styleconverter.runtime.core.types.ValueExtractors.extractColor(it)
            }
            if (bgColor != null) {
                val brightness = 0.299f * bgColor.red + 0.587f * bgColor.green + 0.114f * bgColor.blue
                if (brightness > 0.6f) Color(0xB3333333) else Color(0xB3EEEEEE)
            } else {
                Color(0xB3EEEEEE) // default light text for dark card background
            }
        })
        // Wave 9: when the nearest component's Color is inherited-only the
        // merged `properties` list still carries it (currentColor consumers
        // need it there), but the WEB leaf placeholder never paints an
        // inherited color (its span pins own-or-contrast) — so the
        // textStyle.color fallback must not resurrect what the caller's
        // gate already suppressed.
        val effectiveColor = textColor
            ?: if (!LocalColorIsInheritedOnly.current && textStyle.color != Color.Unspecified) textStyle.color else defaultPlaceholderColor
        // CSS / iOS / web default for `text-align` is `start` (== left in
        // LTR). Android previously defaulted to `Center` for placeholder
        // text, which made every placeholder fixture (Card_Complete,
        // Input_Field, Tag_Chip, …) render with text horizontally
        // centered while iOS/web rendered it top-left. The visible offset
        // dragged Card_Complete iOS-Android SSIM to ~0.57. `TextAlign.Start`
        // matches iOS leading + web `text-align: start` byte-for-byte.
        val effectiveTextAlign = if (textStyle.textAlign != TextAlign.Unspecified) textStyle.textAlign else TextAlign.Start

        // Default placeholder line-height = 1.2 × fontSize when no explicit
        // line-height is set, to match the line box on iOS / web.
        //   • SwiftUI Text default line-height for size=16 ≈ 19pt (≈1.19×).
        //   • Browser default `line-height: normal` for size=16px ≈ ~19px (≈1.2×).
        //   • Compose Material default for unspecified lineHeight resolves
        //     through Paragraph + the platform font's metrics, which on
        //     emulator-default Roboto comes out closer to 1.4-1.5×.
        // The 0.2-0.3 ratio gap added a visible 4-5px to every Compose
        // placeholder line box vs iOS/web. On Outline_Solid (081) /
        // Neumorphic_Light (098) etc. that translates to a taller box →
        // iOS-Android and Android-web SSIM dragged ~0.78-0.80. Forcing 1.2×
        // pulls Compose into the same band as the other two engines on the
        // single-line-text fixtures that dominate the placeholder corpus.
        // When the IR DOES carry an explicit `line-height` we keep using
        // that value verbatim — only the missing-value branch is bounded.
        //
        // Round-4b (composed WPT line-box, FIX 2; recalibrated at the
        // corpus-v4.1 LINE-HEIGHT sub-boundary): in composed WPT capture the
        // default box pins to the browser-ref's PINNED line box (20px @16px,
        // ratio 1.25 — the ref injection's explicit REF_LINE_HEIGHT, no
        // longer any font's `normal` metrics) instead of the native 1.2× — see
        // [composedDefaultLineHeightPx] / [LocalWptComposedMode]. Every other
        // path (per-component inbox, the 327-pair baseline) keeps 1.2× because
        // LocalWptComposedMode is false there, so those captures are byte-
        // identical. An IR-declared line-height still wins (the `if` above).
        //
        // Wave 22 (lane FONT) — the THIRD state, WPT-GATED. An IR that DECLARES
        // `line-height: normal` (css-fonts-4 §4.3: exactly what `font: 92px
        // Arial` resets to, now emitted by FontExpander.kt) must fall through to
        // the FONT's natural metrics, not to the calibration: Chromium renders
        // that div at Arial's own 1.1499em box (≈105.8px at 92px) because a
        // directly-matching declaration beats the ref injection's inherited
        // `:where(body){line-height:1.25}` (115px), and the calibration exists
        // only to stand in for that inherited rule. Outside WPT capture the
        // keyword keeps the extractors' historical 1.2× stand-in so the
        // committed 327-pair baselines never move — see
        // LineHeightNormal.lineBoxSource for the full table and the stated risk.
        // ABSENT line-height takes the calibration bit-for-bit in BOTH modes —
        // that is what the entire rest of the corpus rides on.
        // The three-state pick is delegated to the shared native decision so
        // this `when` and SwiftUI's ComponentRenderer.effectiveLineHeight can
        // never diverge (byte-parallel twins, identical pin tables).
        val lineBoxSource = com.styleconverter.runtime.typography.LineHeightNormal.lineBoxSource(
            hasDeclaredValue = textStyle.lineHeight != TextUnit.Unspecified,
            declaredNormal = com.styleconverter.runtime.typography.LineHeightNormal
                .isDeclaredNormal(properties),
            // LocalWptCaptureMode (NOT LocalWptComposedMode) is the exact twin
            // of SwiftUI's `@Environment(\.wptCaptureMode)` that gates the same
            // row there — both are false on the 327-pair dark stage, which is
            // the property that protects the committed baselines.
            wptCapture = LocalWptCaptureMode.current
        )
        val effectiveLineHeight = when (lineBoxSource) {
            // An explicit numeric/length line-height wins outright (author > us).
            com.styleconverter.runtime.typography.LineHeightNormal.LineBoxSource.DECLARED ->
                textStyle.lineHeight
            // Declared `normal` → stay Unspecified: Compose's Paragraph then
            // uses the resolved face's ascent+descent, the CSS `normal` model.
            com.styleconverter.runtime.typography.LineHeightNormal.LineBoxSource.NATURAL ->
                TextUnit.Unspecified
            // Nothing declared → the unchanged WPT / native calibration.
            com.styleconverter.runtime.typography.LineHeightNormal.LineBoxSource.CALIBRATED ->
                composedDefaultLineHeightPx(
                    LocalWptComposedMode.current, effectiveFontSize.value
                ).sp
        }

        // CSS `background-clip: text` plus a `background-image` clips the
        // bg paint to the glyph shape — web typically pairs it with
        // `color: transparent` so only the gradient-filled text shows.
        // Compose's TextStyle accepts a Brush: when both flags are
        // present we build a linear-gradient brush from the IR and feed
        // it as the text fill, replacing `color`. The bg-image painter
        // upstream still draws the rectangle behind, but ColorApplier's
        // suppress check (added together with this) skips it for the
        // clip:text case so the rendered output matches web's clipped
        // text only. Falls back to the regular `color` path when either
        // background-clip or a usable bg-image is absent.
        val clipTextBrush: androidx.compose.ui.graphics.Brush? = run {
            // BackgroundClip IR shape is `["TEXT"]` (array of keyword
            // strings — one per CSS layer). extractKeyword doesn't unwrap
            // arrays so we read the first primitive directly.
            val clipArr = properties.firstOrNull { it.type == "BackgroundClip" }
                ?.data as? kotlinx.serialization.json.JsonArray
            val clip = (clipArr?.firstOrNull() as? kotlinx.serialization.json.JsonPrimitive)
                ?.contentOrNull?.lowercase()
            if (clip != "text") return@run null
            val bgArr = properties.firstOrNull { it.type == "BackgroundImage" }
                ?.data as? kotlinx.serialization.json.JsonArray
            val firstGrad = bgArr?.firstOrNull()
                as? kotlinx.serialization.json.JsonObject ?: return@run null
            // Only the simplest linear/radial cases — pull colours via
            // ValueExtractors so the same logic that drives the regular
            // gradient brush stays the source of truth for stops.
            val stops = (firstGrad["stops"] as? kotlinx.serialization.json.JsonArray)
                ?.mapNotNull { st ->
                    val so = st as? kotlinx.serialization.json.JsonObject ?: return@mapNotNull null
                    val c = so["color"]?.let {
                        com.styleconverter.runtime.core.types.ValueExtractors.extractColor(it)
                    } ?: return@mapNotNull null
                    c
                } ?: return@run null
            if (stops.size < 2) return@run null
            // Direction-agnostic — Compose Brush.linearGradient defaults
            // to top-left → bottom-right which matches the most common
            // CSS `background-image: linear-gradient(to right, ...)` use
            // closely enough for the audit fixtures.
            androidx.compose.ui.graphics.Brush.linearGradient(stops)
        }

        // Reuse the platformStyle + lineHeightStyle TextStyleApplier
        // configured (PlatformTextStyle includeFontPadding=false +
        // LineHeightStyle Center/None) so the placeholder honors the
        // CSS-aligned line-box height. Without this, the per-frame
        // TextStyle(...) constructions below would lose those flags
        // (Compose's positional-arg ctor doesn't inherit Defaults) and
        // single-line text would collapse to font-natural metrics.
        // textMotion rides along for the same reason: TextStyleApplier
        // sets TextMotion.Animated (linear unhinted advances + subpixel
        // positioning, matching Chrome/CoreText — see the why-comment in
        // TextStyleApplier.extractTextStyle) and dropping it here would
        // silently revert the placeholder path to hinted quantized
        // advances, reintroducing the cumulative first-line glyph drift.
        val placeholderPlatformStyle = textStyle.platformStyle
        val placeholderLineHeightStyle = textStyle.lineHeightStyle
        val placeholderTextMotion = textStyle.textMotion
        val finalTextStyle = if (clipTextBrush != null) {
            TextStyle(
                brush = clipTextBrush,
                fontSize = effectiveFontSize,
                textAlign = effectiveTextAlign,
                letterSpacing = textStyle.letterSpacing,
                lineHeight = effectiveLineHeight,
                fontWeight = textStyle.fontWeight,
                fontStyle = textStyle.fontStyle,
                // Default to bundled Inter when CSS didn't declare a
                // font-family — keeps placeholder text matching iOS+web.
                fontFamily = textStyle.fontFamily
                    ?: com.styleconverter.runtime.typography.InterFontFamily,
                textDecoration = textStyle.textDecoration,
                shadow = textStyle.shadow,
                baselineShift = textStyle.baselineShift,
                textIndent = textStyle.textIndent,
                textGeometricTransform = textStyle.textGeometricTransform,
                platformStyle = placeholderPlatformStyle,
                lineHeightStyle = placeholderLineHeightStyle,
                // Preserve linear/subpixel glyph advances (see above).
                textMotion = placeholderTextMotion
            )
        } else {
            TextStyle(
                fontSize = effectiveFontSize,
                color = effectiveColor,
                textAlign = effectiveTextAlign,
                letterSpacing = textStyle.letterSpacing,
                lineHeight = effectiveLineHeight,
                fontWeight = textStyle.fontWeight,
                fontStyle = textStyle.fontStyle,
                // Default to bundled Inter when CSS didn't declare a
                // font-family — keeps placeholder text matching iOS+web.
                fontFamily = textStyle.fontFamily
                    ?: com.styleconverter.runtime.typography.InterFontFamily,
                textDecoration = textStyle.textDecoration,
                shadow = textStyle.shadow,
                baselineShift = textStyle.baselineShift,
                textIndent = textStyle.textIndent,
                textGeometricTransform = textStyle.textGeometricTransform,
                platformStyle = placeholderPlatformStyle,
                lineHeightStyle = placeholderLineHeightStyle,
                // Preserve linear/subpixel glyph advances (see above).
                textMotion = placeholderTextMotion
            )
        }

        // OpenType features from font-variant-* (ordn, smcp, liga off, …).
        // FontVariantApplier already built the CSS-syntax feature string;
        // it was just never wired into the placeholder's TextStyle, so
        // `font-variant-numeric: ordinal` had no Android effect while web
        // raised the "07" ordinals (Typography_C07 0.844).
        val propertyPairs = properties.map { it.type to it.data }
        val fontVariantConfig = try {
            TypographyExtractor.extractFontVariantConfig(propertyPairs)
        } catch (e: Exception) {
            null
        }
        val featureSettings = fontVariantConfig
            ?.let { FontVariantApplier.buildFontFeatureSettings(it) }
            ?.takeIf { it.isNotEmpty() }
        val styledTextStyle = if (featureSettings != null)
            finalTextStyle.copy(fontFeatureSettings = featureSettings)
        else finalTextStyle

        // font-variant-caps: small-caps. The bundled static Inter honours
        // "smcp" only partially across weights, and the browser SYNTHESIZES
        // small caps whenever the face lacks the feature — so we synthesize
        // deterministically the way Chrome does: lowercase letters become
        // uppercase at a reduced size (Typography_C06: web shows
        // TYPOGRAPHY C06 in mixed cap sizes, Android showed normal case).
        val smallCaps = fontVariantConfig?.caps == FontVariantCaps.SMALL_CAPS ||
            fontVariantConfig?.caps == FontVariantCaps.ALL_SMALL_CAPS
        val annotatedText = if (smallCaps)
            synthesizeSmallCaps(displayText, effectiveFontSize.value)
        else androidx.compose.ui.text.AnnotatedString(displayText)

        // css-text-3 §5.1 word-spacing — real implementation. Compose's
        // TextStyle has no word-spacing, and the old letter-spacing
        // fallback inserted the gap between EVERY glyph pair (12px between
        // single digits pushed '0123 4567' half off its box). Instead, a
        // SpanStyle letter-spacing on ONLY the space characters adds the
        // extra advance after each space — exactly the CSS model, negatives
        // included. The span REPLACES the base tracking on those chars, so
        // resolveWordSpacingSpanSp folds the paragraph letter-spacing back
        // in (both trackings apply at a separator per spec). em/rem wire
        // values resolve inside extractWordSpacingSp (em × element
        // font-size, rem × 16px root — the letter-spacing escape hatch,
        // mirrored). Skipped entirely for absent/normal/zero values so the
        // frozen baseline renders byte-identically.
        val wordSpacingSp = TextStyleApplier.extractWordSpacingSp(properties, effectiveFontSize.value)
        val spacedText = if (wordSpacingSp != null && wordSpacingSp != 0f) {
            TextStyleApplier.applyWordSpacingSpans(
                annotatedText,
                TextStyleApplier.resolveWordSpacingSpanSp(
                    wordSpacingSp, styledTextStyle.letterSpacing, effectiveFontSize.value
                )
            )
        } else annotatedText

        // Wave 34 (lane F1) — PER-SCRIPT FONT FALLBACK. css-fonts-4 §5.2
        // matches a font stack per CHARACTER; Compose's FontFamily selects
        // ONE face for the whole Text and hands the rest to an opaque
        // platform cascade (Typeface.CustomFallbackBuilder, the API that
        // would pin it, is API 29 against this module's minSdk 24). So a
        // document painting Armenian / Arabic-Indic / Bengali / Khmer /
        // Hebrew text resolved the emulator's own Noto subset while the
        // browser-ref resolved CoreText's — different advances, different
        // wrap points, an SSIM bounded by typography (the Rule-43 boundary
        // in tools/titan/wpt-not-applicable.mjs). ScriptFallbackFonts splits
        // the run by script and installs a BUNDLED face per run, which is
        // ordinary text layout and needs no blocked API. Identity outside
        // WPT capture AND for any string with no target-script codepoint —
        // it returns `spacedText` itself, so the 327 baselines cannot move.
        val scriptedText = com.styleconverter.runtime.typography.font
            .ScriptFallbackFonts.applyTo(spacedText, LocalWptCaptureMode.current)

        // CSS overflow is VISIBLE by default: text that exceeds its box
        // paints past the border box (CSS 2.1 §11.1.1 — overflow applies to
        // the box, and the initial value clips nothing). Compose Text
        // defaults to Clip, which chopped glyphs at the box edge in BOTH
        // axes: horizontally for nowrap lines (web C20/C21 draw the full
        // single line across the canvas) and VERTICALLY for wrapping text
        // in a height-bound box (Transforms_BoxModel: height 48 − padding
        // 20×2 − border 1×2 leaves a 6px content box, web paints the label
        // over the padding band, Compose clipped it to nothing). Force
        // Visible whenever the fixture declares no clipping intent — i.e.
        // no explicit text-overflow AND no line-clamp limit; declared
        // ellipsis / line-clamp fixtures keep their clipping behaviour.
        val effectiveOverflow = placeholderOverflow(properties, effectiveMaxLines, textOverflow)

        // text-emphasis marks (css-text-decor-3 §3). Compose has no native
        // emphasis-mark support, so we paint one mark per typographic unit
        // from the TextLayoutResult glyph boxes: filled/open circles above
        // (over) or below (under) each non-space glyph. Web paints these
        // rows of dots on Typography_C16 (under left, #e74c3c) and C17
        // (over, text color); Android previously rendered nothing.
        val emphasisConfig = try {
            TypographyExtractor.extractTextEmphasisConfig(propertyPairs)
        } catch (e: Exception) {
            null
        }
        val layoutResult = androidx.compose.runtime.remember {
            androidx.compose.runtime.mutableStateOf<androidx.compose.ui.text.TextLayoutResult?>(null)
        }
        val emphasisModifier = if (emphasisConfig?.hasEmphasis == true) {
            val markColor = emphasisConfig.color ?: effectiveColor
            val over = emphasisConfig.position == TextEmphasisPosition.OVER_RIGHT ||
                emphasisConfig.position == TextEmphasisPosition.OVER_LEFT
            val open = emphasisConfig.style == TextEmphasisStyle.OPEN_DOT ||
                emphasisConfig.style == TextEmphasisStyle.OPEN_CIRCLE ||
                emphasisConfig.style == TextEmphasisStyle.OPEN_DOUBLE_CIRCLE ||
                emphasisConfig.style == TextEmphasisStyle.OPEN_TRIANGLE ||
                emphasisConfig.style == TextEmphasisStyle.OPEN_SESAME
            // Mark size ≈ half the font size (spec: marks render at 50%
            // font-size); a circle glyph's ink is ~⅔ of its em box, so the
            // painted diameter lands near 0.33 × font-size — matches the
            // ~5-6px dots in the web capture at 16px.
            val radiusPx = effectiveFontSize.value * 0.165f
            Modifier.drawBehind {
                val layout = layoutResult.value ?: return@drawBehind
                val text = layout.layoutInput.text.text
                for (i in text.indices) {
                    // Word separators get no mark (css-text-decor-3 §3.4).
                    if (text[i].isWhitespace()) continue
                    // Guard: offsets past the laid-out end (clipped lines).
                    if (i >= layout.getLineEnd(layout.lineCount - 1, true)) break
                    val box = layout.getBoundingBox(i)
                    val line = layout.getLineForOffset(i)
                    val cx = (box.left + box.right) / 2f
                    val cy = if (over) layout.getLineTop(line) - radiusPx - 1f
                    else layout.getLineBottom(line) + radiusPx + 1f
                    if (open) {
                        drawCircle(
                            color = markColor,
                            radius = radiusPx,
                            center = androidx.compose.ui.geometry.Offset(cx, cy),
                            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1f)
                        )
                    } else {
                        drawCircle(
                            color = markColor,
                            radius = radiusPx,
                            center = androidx.compose.ui.geometry.Offset(cx, cy)
                        )
                    }
                }
            }
        } else Modifier

        // text-decoration-line OWNERSHIP (css-text-decor-3 §2.1). The
        // owned pass draws ALL flagged lines — underline, overline AND
        // line-through — with Chromium-matched geometry instead of
        // delegating any of them to Compose's 1px built-ins. Device
        // evidence (wave-5 gate): web draws pixel-snapped 2px-thick lines
        // at 22px Inter while the built-ins drew 1px at different offsets
        // (Underline 0.809, UnderOver 0.740, Triple 0.737 vs the
        // fixture's 0.8695 no-decoration floor). Geometry from the
        // onTextLayout-captured TextLayoutResult (the same layoutResult
        // state the emphasis pass reads): one band per flagged kind per
        // VISUAL line, anchored on getLineBaseline(i) and spanning
        // getLineLeft..getLineRight (each line box gets its own
        // decoration per spec). Color: text-decoration-color when
        // declared, else currentColor == the text color (§2.2 initial).
        // drawWithContent paints AFTER the glyphs — Blink's order for the
        // through/over lines; the underline difference (skip-ink) is
        // documented on decorationSegments.
        //
        // Wave 21 (lane TEXTDECOR, B-RC8): the extractor's STYLE and
        // THICKNESS are no longer dropped on the floor.
        //   • thickness (css-text-decor-4 §2.4): an explicit
        //     `text-decoration-thickness` overrides the font-derived
        //     auto default (Blink underline gap = max(1, ceil(T/2)),
        //     ref-pinned to the dotted-001 band tops 207/363/519); the
        //     AUTO path keeps TextStyleApplier.decorationSegments' rows
        //     byte-for-byte (its 22px oracle + the 327-pair baseline pin
        //     those rows). Wave 22 folded BOTH rules into
        //     DecorationColorOps.bandTop — the two former emitters
        //     (decorationSegments / DecorationOps.explicitBands) survive
        //     as the pinned reference implementations that
        //     DecorationWirePinTest compares this pass against.
        //   • style (css-text-decor-3 §2.3): every band is expanded via
        //     DecorationOps.styleOps — DOTTED paints Chromium round-cap
        //     circle runs (≤3px: square dashes), DASHED paints
        //     Blink-fitted rect runs, SOLID emits the band unchanged
        //     (legacy captures byte-identical). DOUBLE/WAVY still paint
        //     solid — surfaced ONCE via logcat below, never silent.
        val ownedDecorations = TextStyleApplier.extractDecorationLineFlags(properties)
        // Wave 22 (lane DECOR, B-RC4b) — the ORDERED per-line request
        // list this pass paints, each entry carrying ITS OWN color
        // (css-text-decor-3 §2.2: every decorating box paints its line in
        // its own color, and §2.1 propagates all of them onto the one
        // collapsed inline run). `decorations` is the merged
        // `meta.decorations` wire; null (every document today, and every
        // run the extractor did not collapse) makes resolve() synthesize
        // this component's own three flags with null colors — literally
        // the pre-wave-22 band set, order and color, so the dark-stage
        // 327 baselines cannot move.
        val decorationRequests = com.styleconverter.runtime.typography.DecorationColorOps.resolve(
            wire = decorations,
            underline = ownedDecorations.underline,
            overline = ownedDecorations.overline,
            lineThrough = ownedDecorations.lineThrough
        )
        val decorationModifier = if (decorationRequests.isNotEmpty()) {
            // Full decoration config: color + style + explicit thickness.
            val decorationConfig = try {
                TextStyleApplier.extractTextDecorationConfig(properties)
            } catch (e: Exception) {
                null
            }
            val ownedDecorationColor = decorationConfig?.color ?: effectiveColor
            // Map the applier's style enum onto the pure twin's own enum
            // (same member set — DecorationOps stays dependency-free so
            // the JVM pin suite compiles it standalone).
            val decorationLineStyle = when (decorationConfig?.style) {
                TextStyleApplier.TextDecorationStyleType.DOUBLE ->
                    com.styleconverter.runtime.typography.DecorationOps.LineStyle.DOUBLE
                TextStyleApplier.TextDecorationStyleType.DOTTED ->
                    com.styleconverter.runtime.typography.DecorationOps.LineStyle.DOTTED
                TextStyleApplier.TextDecorationStyleType.DASHED ->
                    com.styleconverter.runtime.typography.DecorationOps.LineStyle.DASHED
                TextStyleApplier.TextDecorationStyleType.WAVY ->
                    com.styleconverter.runtime.typography.DecorationOps.LineStyle.WAVY
                // SOLID and "no config extracted" both paint solid.
                else -> com.styleconverter.runtime.typography.DecorationOps.LineStyle.SOLID
            }
            // Honest limitation, logged once per process (no silent
            // fallthrough): double/wavy render as solid until they get
            // their own op emitters.
            if (com.styleconverter.runtime.typography.DecorationOps.isLossyFallback(decorationLineStyle) &&
                decorationStyleFallbackLogged.compareAndSet(false, true)
            ) {
                android.util.Log.i(
                    "ComponentRenderer",
                    "text-decoration-style ${decorationLineStyle.name.lowercase()} " +
                        "not implemented: painting solid (DecorationOps TODO)"
                )
            }
            val explicitThicknessPx = decorationConfig?.thickness
            Modifier.drawWithContent {
                drawContent()
                val layout = layoutResult.value ?: return@drawWithContent
                // Per-visual-line COLORED bands, one per requested line
                // per visual line, line-major then request order — which
                // for the legacy (no-wire) request list is exactly the
                // old underline → overline → line-through sequence.
                // DecorationColorOps.bandTop carries BOTH thickness
                // rules: auto reproduces decorationSegments' wave-5
                // capture rows byte-for-byte, explicit swaps in Blink's
                // ref-pinned underline gap (the wave-21 explicitBands
                // rule) — the two former branches, now one call.
                val bands = com.styleconverter.runtime.typography.DecorationColorOps.bands(
                    lineCount = layout.lineCount,
                    // px==dp==sp space (density 1 harness) — same
                    // convention as the emphasis radius above.
                    fontSizePx = effectiveFontSize.value,
                    // `text-decoration-thickness: auto` (css-text-decor-4
                    // §2.4 initial) — the face-derived rule the wave-5
                    // captures pinned; passed IN so DecorationColorOps
                    // stays dependency-free of TextStyleApplier.
                    autoThicknessPx = TextStyleApplier.decorationThicknessPx(effectiveFontSize.value),
                    explicitThicknessPx = explicitThicknessPx,
                    lines = decorationRequests,
                    lineBaseline = { layout.getLineBaseline(it) },
                    lineLeft = { layout.getLineLeft(it) },
                    lineRight = { layout.getLineRight(it) }
                )
                // Expand every band into its style's ops, each op TAGGED
                // with its own line's color, and paint. A dotted
                // underline in color A and a solid overline in color B
                // compose for free — style expansion is per band.
                com.styleconverter.runtime.typography.DecorationColorOps
                    .ops(bands, decorationLineStyle).forEach { colored ->
                        // §2.2: a null wire color means `currentColor` →
                        // the same substitute the pass used before
                        // (declared text-decoration-color, else the text
                        // color). IR colors are normalized sRGB 0..1
                        // (schema/spec/02-values.md), which is exactly
                        // Compose's Color(r,g,b,a) space — no conversion.
                        val paint = colored.color?.let {
                            androidx.compose.ui.graphics.Color(it.r, it.g, it.b, it.a)
                        } ?: ownedDecorationColor
                        when (val op = colored.op) {
                            is com.styleconverter.runtime.typography.DecorationOps.Op.Band ->
                                drawRect(
                                    color = paint,
                                    topLeft = androidx.compose.ui.geometry.Offset(op.left, op.top),
                                    size = androidx.compose.ui.geometry.Size(op.width, op.height)
                                )
                            is com.styleconverter.runtime.typography.DecorationOps.Op.Dot ->
                                drawCircle(
                                    color = paint,
                                    radius = op.radius,
                                    center = androidx.compose.ui.geometry.Offset(op.centerX, op.centerY)
                                )
                        }
                    }
            }
        } else Modifier
        // Suppress the platform built-ins ONLY where the owned pass draws
        // (this label path has layout access via onTextLayout): leaving
        // TextDecoration.Underline/LineThrough in the style would paint
        // Compose's 1px lines UNDER the owned 2px rects at different
        // offsets — a visible double-draw. Paths without layout access
        // (list markers, non-label Text sites) keep extractTextDecoration's
        // TextDecoration untouched, so their existing rendering survives.
        // Wave 22 (B-RC4b): gated on the RESOLVED request list so a
        // merged run that inherited an ancestor's underline suppresses
        // the built-in too. Identical to `ownedDecorations.any` whenever
        // no merged wire arrived (resolve() synthesizes exactly those
        // flags), so no committed capture changes.
        //
        // …PLUS the authoritative-when-present clause: a PRESENT wire
        // suppresses the built-ins even when the resolved list is EMPTY.
        // Without the `decorations != null` disjunct, a wire whose entries
        // were all unknown keywords would resolve to zero bands here while
        // `styledTextStyle` still carried TextDecoration.Underline from
        // the component's merged flat bag — the wire would say "no lines"
        // and Compose would paint one anyway.
        val paintedTextStyle = if (decorations != null || decorationRequests.isNotEmpty())
            styledTextStyle.copy(textDecoration = androidx.compose.ui.text.style.TextDecoration.None)
        else styledTextStyle

        // Block-level width fill — the web placeholder is a `display:block`
        // <span>, which spans the parent's content box whenever the parent
        // has a definite width. Compose Text hugs its glyphs by default, so
        // `text-align: center/right` had nothing to align within (the
        // TextAlign_Center fixture rendered left-flushed on Android while
        // web centered it). Fill only when the IR declares a definite
        // width — for `fit-content`-like unsized parents fillMaxWidth would
        // wrongly stretch the wrap-content box to the canvas width.
        // word-break: break-word — the web placeholder <span> declares it
        // (apps/web-harness ComponentRenderer.tsx), which makes the span's
        // MIN-CONTENT width a single grapheme: `width: min-content` boxes
        // collapse to a one-character column on web (PW_Sizing_Spacing_02's
        // 49px sliver) while Compose Text reports its longest WORD as the
        // min intrinsic (Android rendered a 100px box, A-w 0.868). The shim
        // caps the Text's reported min-intrinsic width at ~one em so
        // IntrinsicSize.Min measurement matches the harness's break-word
        // floor; normal measurement (bounded constraints) is untouched, and
        // Compose Text already breaks words mid-grapheme when the incoming
        // constraint is narrower than the word — the same fallback
        // break-word uses.
        val breakWordShim = BreakWordMinIntrinsicModifier(effectiveFontSize.value)
        // Round-4b (composed WPT line-box, FIX 2): drop the 4dp placeholder pad
        // to 0 in composed WPT capture so a text bar is exactly one line box
        // tall (matching the ref's tight <p>); every other path keeps 4dp
        // (LocalWptComposedMode is false → byte-identical). See
        // [composedPlaceholderTextPaddingDp].
        val placeholderPad = composedPlaceholderTextPaddingDp(LocalWptComposedMode.current).dp
        val textModifier = if (placeholderFillsParentWidth(properties)) {
            Modifier.fillMaxWidth().padding(placeholderPad).then(breakWordShim)
        } else {
            Modifier.padding(placeholderPad).then(breakWordShim)
        }

        // Round-4b (composed WPT line-box, FIX 2) — enforce the CSS line box.
        // The browser lays a `<p>` line out at EXACTLY its `line-height` (18px
        // @16px for the ref's default font); Compose instead FLOORS a single
        // line at the font's natural glyph box, so Inter renders ~19px even with
        // lineHeight pinned to 18 (line-height below the glyph box is ignored,
        // and Trim can't shrink below it). That +1px/line makes a stacked 10-bar
        // test drift ~1px/bar off the ref and collapses SSIM (proven: perfect
        // 18px alignment scores ~0.90 vs ~0.59 with the drift). This snap
        // re-imposes the CSS model: measure the text, then report a height of
        // `lineCount × effectiveLineHeight` (from the onTextLayout result), the
        // glyph box centered inside — trimming the ~1px/line font excess without
        // clipping the ink (glyphs sit well inside the box). Composed WPT capture
        // ONLY (LocalWptComposedMode) so the 327 baseline + per-component inbox
        // path are byte-identical.
        val snapDensity = androidx.compose.ui.platform.LocalDensity.current
        // Wave 22 (lane FONT) — `effectiveLineHeight` can now legitimately be
        // TextUnit.Unspecified (the declared-`normal` state above), and
        // `TextUnit.toPx()` THROWS on a non-Sp unit, so the conversion is
        // guarded. 0f is the correct sentinel: it disables the composed
        // line-box snap below (`refLineBoxPx > 0f`) and nulls
        // `declaredLineHeightPx`, which is exactly right — with `normal` there
        // is no CSS-computed box to snap the natural glyph box into, the
        // natural box IS the answer. Every previously-reachable state still
        // takes the identical `toPx()` path, so no committed capture moves.
        val refLineBoxPx =
            if (effectiveLineHeight != TextUnit.Unspecified)
                with(snapDensity) { effectiveLineHeight.toPx() }
            else 0f
        val composedLineBoxSnap: Modifier =
            if (LocalWptComposedMode.current && refLineBoxPx > 0f) {
                Modifier.layout { measurable, constraints ->
                    val placeable = measurable.measure(constraints)
                    // lineCount comes from the previous frame's onTextLayout
                    // (below); until it settles (0) we pass the natural height
                    // through unchanged — the capture waits for layout to settle.
                    val lines = layoutResult.value?.lineCount ?: 0
                    if (lines <= 0) {
                        layout(placeable.width, placeable.height) { placeable.place(0, 0) }
                    } else {
                        val target = Math.round(lines * refLineBoxPx).coerceAtLeast(0)
                        layout(placeable.width, target) {
                            // Center the (slightly taller) glyph box in the
                            // tightened CSS line box — symmetric ½px trim.
                            placeable.place(0, (target - placeable.height) / 2)
                        }
                    }
                }
            } else Modifier

        // ── Glyph placement compensation (wave-7 text placement) ──
        // Two draw-time fractional translations of the glyph run, both pure
        // math in TextStyleApplier (JVM-pinned) and both applied via
        // Modifier.graphicsLayer so LAYOUT is untouched — box geometry,
        // intrinsics and the committed baselines' component sizes are
        // byte-identical; only the drawn ink moves sub-pixel.
        //
        // FIX 2 — center-align even-truncation: StaticLayout's ALIGN_CENTER
        // snaps the centered run to an integer start AT DRAW TIME (AOSP
        // Layout#getLineStartPos even-truncates the raw advance:
        // (W − ((int)advance & ~1)) >> 1) while the browser centers
        // fractionally — see TextStyleApplier.centerAlignFractionalDeltaX
        // for the draw-path model and the wave-7 meta-lesson (the first
        // version read the getLineLeft REPORT path, a different, floor'd
        // formula whose self-consistent rounding made the delta compute 0
        // while the pixels drew 0.65px right of the ideal center).
        //
        // FIX 3 — sub-natural line-height: when the DECLARED line-height is
        // below the font's natural content height, CSS negative half-leading
        // paints the glyph band (L − natural)/2 higher; StaticLayout clamps
        // it — see TextStyleApplier.subNaturalLineHeightDeltaY. This is the
        // shared cross-native contract with the iOS lane: both natives land
        // the SAME compensation so the currently-agreeing native pair
        // (0.993) moves to web together instead of splitting. Gates:
        //   • only when the IR DECLARES a line-height
        //     (textStyle.lineHeight != Unspecified) — the synthetic 1.2×
        //     placeholder default above is a harness-matching constant, not
        //     a CSS-resolved value, and compensating its ~0.2px sub-natural
        //     sliver would subtly shift EVERY committed placeholder baseline;
        //   • never in composed WPT mode — composedLineBoxSnap already
        //     centers the natural glyph box inside the L-sized line box
        //     there (the same (L−natural)/2 shift, done via placement), so
        //     translating too would double-compensate;
        //   • the natural height is read off the live TextLayoutResult's
        //     first line box (the Paint/FontMetrics the layout itself used —
        //     with includeFontPadding=false a clamped line IS the natural
        //     ascent+descent box). If Compose ever honors the smaller
        //     line-height, first-line height == L and the helper returns
        //     null — a safe no-op, not a wrong shift.
        val declaredLineHeightPx =
            if (textStyle.lineHeight != TextUnit.Unspecified) refLineBoxPx else null
        val wptComposedActive = LocalWptComposedMode.current
        val glyphPlacementCompensation = Modifier.graphicsLayer {
            // Reset both axes on every pass: the layer block re-runs when the
            // layoutResult state it reads changes (no recomposition), and a
            // stale translation must not survive a layout that no longer
            // needs compensating.
            var dx = 0f
            var dy = 0f
            val layout = layoutResult.value
            if (layout != null) {
                // FIX 2 — only when the effective alignment actually centers.
                // Wave-8 rework: wave 7 fed this from getLineLeft/getLineRight
                // — the REPORT accessors, whose ALIGN_CENTER floor/ceil
                // rounding is self-consistent, so the delta computed 0 while
                // the DRAW path (getLineStartPos even-truncation) actually
                // placed the run 1px right of getLineLeft's answer (fixture:
                // report 16, draw 17, ideal 16.35). The helper now models
                // the draw path from the UNROUNDED line advance instead.
                if (effectiveTextAlign == TextAlign.Center) {
                    TextStyleApplier.centerAlignFractionalDeltaX(
                        // TextLayoutResult.size is the text's own layout box
                        // (px == dp, density-1 harness) — the box
                        // StaticLayout centered within.
                        layoutWidthPx = layout.size.width.toFloat(),
                        lineCount = layout.lineCount,
                        // UNROUNDED advance: multiParagraph.getLineWidth
                        // delegates (AndroidParagraph → TextLayout) to
                        // android.text.Layout#getLineWidth — the raw float
                        // TextLine extent, no floor/ceil. getLineRight −
                        // getLineLeft would re-import the rounded report
                        // the wave-7 version was fooled by. (Honest nit:
                        // getLineWidth includes trailing whitespace where
                        // the draw path's getLineMax excludes it — identical
                        // for the corpus's trimmed single-line labels.)
                        lineAdvance = { layout.multiParagraph.getLineWidth(it) }
                    )?.let { dx = it }
                }
                // FIX 3 — declared sub-natural line-height (gates above).
                if (declaredLineHeightPx != null && !wptComposedActive && layout.lineCount > 0) {
                    // First visual line's box height = the natural glyph box
                    // whenever StaticLayout clamped the sub-natural value.
                    val naturalPx = layout.getLineBottom(0) - layout.getLineTop(0)
                    TextStyleApplier.subNaturalLineHeightDeltaY(
                        resolvedLineHeightPx = declaredLineHeightPx,
                        naturalLineBoxPx = naturalPx
                    )?.let { delta ->
                        dy = delta
                        // Honest limitation, logged ONCE per process: on
                        // multi-line sub-natural text only the PLACEMENT is
                        // compensated — the line-to-line advance stays at
                        // the natural height (StaticLayout can't compress a
                        // line below the glyph box), so line 2+ still sits
                        // lower than web's compressed stack.
                        if (layout.lineCount > 1 &&
                            subNaturalMultiLineLogged.compareAndSet(false, true)
                        ) {
                            android.util.Log.i(
                                "ComponentRenderer",
                                "sub-natural line-height: multi-line boxes remain " +
                                    "uncompressed (advance stays natural); only glyph-run " +
                                    "placement is compensated by (L-natural)/2"
                            )
                        }
                    }
                }
            }
            translationX = dx
            translationY = dy
        }

        // css-writing-modes-4 §3: vertical / sideways writing modes rotate
        // the TEXT FLOW inside the box (the box itself keeps its geometry —
        // see WritingModeApplier.applyWritingMode). Chrome lays the line
        // out against the box's HEIGHT and stacks lines along the width:
        // sideways-lr reads bottom-to-top with lines left→right (a -90°
        // glyph-run rotation), vertical-rl / sideways-rl read top-to-bottom
        // with lines right→left (+90°). We reproduce that with a wrapper
        // Layout that measures the Text against SWAPPED constraints and
        // rotates the laid-out run about its center — only the glyphs
        // rotate, not background/borders (PW_Layout_Typography_03 web
        // reference; Android previously drew horizontal text, A-w 0.568).
        val writingModeConfig = try {
            com.styleconverter.runtime.typography.text.TextExtractor
                .extractWritingModeConfig(propertyPairs)
        } catch (e: Exception) {
            null
        }
        if (writingModeConfig != null && writingModeConfig.isVertical) {
            val rotation = when (writingModeConfig.writingMode) {
                // lines stack left→right, glyphs read bottom-to-top.
                com.styleconverter.runtime.typography.text.WritingModeValue.SIDEWAYS_LR -> -90f
                // vertical-rl / sideways-rl / vertical-lr approximate as
                // +90° (top-to-bottom glyph run; the rl/lr difference is
                // the line-stacking side which the single-line placeholder
                // corpus can't distinguish).
                else -> 90f
            }
            // The sideways glyph run itself — unchanged; only its wrapper
            // moved (to VerticalTextFlowLayout.VerticalRotatedTextRun, which
            // is the same swap/coerce/centre-rotate measure policy verbatim).
            val verticalGlyphRun: @Composable () -> Unit = {
                Text(
                    // scriptedText = spacedText (= annotatedText +
                    // word-spacing spans, identity when word-spacing is
                    // absent/zero) + the wave-34 per-script fallback
                    // spans (identity outside WPT capture and for any
                    // Latin-only string).
                    text = scriptedText,
                    // paintedTextStyle == styledTextStyle unless the
                    // owned decoration pass is active (built-ins
                    // stripped there — see paintedTextStyle above).
                    style = paintedTextStyle,
                    maxLines = effectiveMaxLines,
                    overflow = effectiveOverflow,
                    softWrap = effectiveSoftWrap,  // wrapConfig.softWrap minus the B-RC7 composed-WPT unbreakable-run gate
                    onTextLayout = { layoutResult.value = it },
                    // decorationModifier no-ops without owned lines;
                    // inside the rotated branch it draws in the
                    // PRE-rotation frame, so the lines ride the
                    // rotated glyph run.
                    modifier = Modifier.padding(4.dp).then(emphasisModifier).then(decorationModifier)
                )
            }
            // Wave 35 (lane B5) — css-writing-modes-4 §5.1 `text-orientation`.
            // A run whose glyphs are Vertical_Orientation U (CJK, kana,
            // FULLWIDTH forms) stands UPRIGHT inside the vertical line; the
            // rotated wrapper above cannot draw that, because it spins the
            // glyphs and the line stacking together. VerticalTextFlow decides
            // (pure, twinned with the Swift module of the same name) and
            // VerticalUprightTextFlow typesets it — declining back to the
            // rotated run when the wrap budget is unbounded. Every ASCII run,
            // every `sideways-*` mode and every `text-orientation: sideways`
            // answers ROTATED here, i.e. the frozen tree below, byte for byte.
            val uprightStack = verticalUprightStack(writingModeConfig, displayText)
            if (uprightStack != null) {
                VerticalUprightTextFlow(
                    text = displayText,
                    stack = uprightStack,
                    rotatedRun = { VerticalRotatedTextRun(rotation, run = verticalGlyphRun) },
                    // One upright glyph, styled exactly like the run it came
                    // from. No 4dp padding here (each glyph IS a line box —
                    // padding per glyph would insert 8dp of leading between
                    // every character) and no maxLines/softWrap plumbing (a
                    // single code point cannot wrap).
                    uprightGlyph = { glyph -> Text(text = glyph, style = paintedTextStyle) },
                )
            } else {
                VerticalRotatedTextRun(rotation, run = verticalGlyphRun)
            }
            return
        }

        Text(
            // scriptedText = spacedText (= annotatedText + word-spacing
            // spans, identity when word-spacing is absent/zero — baseline
            // byte-identical) + the wave-34 per-script fallback spans
            // (identity outside WPT capture and for any Latin-only string).
            text = scriptedText,
            // paintedTextStyle == styledTextStyle unless the owned
            // decoration pass is active (built-ins stripped there).
            style = paintedTextStyle,
            maxLines = effectiveMaxLines,
            overflow = effectiveOverflow,
            softWrap = effectiveSoftWrap,  // wrapConfig.softWrap minus the B-RC7 composed-WPT unbreakable-run gate
            onTextLayout = { layoutResult.value = it },
            // composedLineBoxSnap (no-op outside composed WPT) tightens the box
            // to the CSS line box before emphasis paints over it; the owned
            // decoration pass draws last so its rects sit over the final
            // glyph layout. glyphPlacementCompensation sits BEFORE the
            // emphasis/decoration draw modifiers so its graphicsLayer wraps
            // them — marks and decoration rects translate together with the
            // glyph run, exactly as the whole inline band shifts on web.
            // (The rotated writing-mode branch above deliberately skips the
            // compensation: its glyph run is rotated ±90° and the corpus's
            // vertical fixtures are neither centered nor sub-natural.)
            modifier = textModifier.then(composedLineBoxSnap).then(glyphPlacementCompensation).then(emphasisModifier).then(decorationModifier)
        )
    }

    /**
     * The visible string for a placeholder / real-text node, BEFORE
     * tab-size processing (pure + internal for the JVM pinning suite).
     *
     * css-text-3 §2.1: text-transform is a RENDER-time transformation of
     * the element's text — the converter never applies it, and the old
     * rawText branch here skipped the transform behind a FALSE comment
     * claiming the extractor pre-transformed `_text`. Result:
     * `text-transform: uppercase` on a real `_text` node rendered
     * verbatim lowercase on Android while web uppercased it. Both paths
     * now run the same transform machinery; `full-width` remains a
     * deliberate no-op inside extractTextTransform (documented there:
     * Chromium, the reference, leaves the Latin corpus unchanged).
     */
    internal fun placeholderDisplayText(
        name: String,
        rawText: String?,
        properties: List<IRProperty>
    ): String {
        val transform = TextStyleApplier.extractTextTransform(properties)
        val source = if (!rawText.isNullOrEmpty()) {
            // Real `_text` renders verbatim (no underscore stripping —
            // underscores in source text are content, not separators).
            rawText
        } else {
            // Synthesized placeholder label: component name with the
            // fixture-naming underscores turned back into spaces.
            name.replace("_", " ")
        }
        return TextStyleApplier.applyTextTransform(source, transform)
    }

    /**
     * The placeholder Text's overflow policy (pure + internal for the JVM
     * pinning suite). Compose Text defaults to Clip, but CSS's initial
     * `overflow: visible` (CSS 2.1 §11.1.1) means overflowing glyphs PAINT
     * outside the box on the web reference — both past the right edge for
     * nowrap lines and past the bottom edge for wrapping text inside a
     * height-bound box (Transforms_BoxModel: 6px content box, web paints
     * the label over the padding band, Clip erased it entirely). Visible
     * unless the fixture declares clipping intent: an explicit
     * text-overflow (ellipsis fixtures) or a line-clamp limit.
     */
    internal fun placeholderOverflow(
        properties: List<IRProperty>,
        effectiveMaxLines: Int,
        declared: TextOverflow
    ): TextOverflow {
        val declaresClipIntent = properties.any { it.type == "TextOverflow" } ||
            effectiveMaxLines != Int.MAX_VALUE
        return if (declaresClipIntent) declared else TextOverflow.Visible
    }

    /**
     * Synthesize small-caps the way browsers do when the face lacks a real
     * `smcp` table: lowercase letters are UPPERCASED and rendered at a
     * reduced size (0.8× — Chrome's synthesis ratio for Latin text), while
     * true capitals keep the full size. Pure function so the JVM suite can
     * pin the transformation.
     */
    internal fun synthesizeSmallCaps(
        text: String,
        fontSizeSp: Float
    ): androidx.compose.ui.text.AnnotatedString {
        return androidx.compose.ui.text.buildAnnotatedString {
            var i = 0
            while (i < text.length) {
                val lower = text[i].isLowerCase()
                var j = i
                while (j < text.length && text[j].isLowerCase() == lower) j++
                val run = text.substring(i, j)
                if (lower) {
                    // Lowercase run → small capital: uppercase at 80% size.
                    pushStyle(
                        androidx.compose.ui.text.SpanStyle(
                            fontSize = (fontSizeSp * 0.8f).sp
                        )
                    )
                    append(run.uppercase())
                    pop()
                } else {
                    append(run)
                }
                i = j
            }
        }
    }

    /**
     * Extract display configuration from properties.
     */
    private fun extractDisplayConfig(properties: List<IRProperty>): DisplayConfig {
        var displayType = DisplayType.BLOCK
        var flexDirection = FlexDirection.ROW
        var justifyContent = JustifyContent.FLEX_START
        var alignItems = AlignItems.STRETCH
        var flexWrap = FlexWrap.NOWRAP
        var rowGap = 0.dp
        var columnGap = 0.dp
        var alignContent = AlignContent.STRETCH

        properties.forEach { prop ->
            when (prop.type) {
                "Display" -> {
                    val keyword = ValueExtractors.extractKeyword(prop.data)?.uppercase()
                    displayType = when (keyword) {
                        "FLEX" -> DisplayType.FLEX_ROW // Default flex is row
                        "INLINE_FLEX", "INLINE-FLEX" -> DisplayType.FLEX_ROW
                        "GRID" -> DisplayType.GRID
                        // Wave 32 (lane P) — css-tables-3 §2.1: a table-CELL
                        // (and a table-CAPTION) "establishes a block
                        // container box for its contents"; it does NOT
                        // re-enter table layout. Sending them to
                        // DisplayType.TABLE made a cell re-run the row/cell
                        // synthesizer over its OWN content, turning every
                        // child-less child into a PlaceholderContent text run
                        // — no background, no box. MEASURED on
                        // CSS2/abspos/static-inside-table-cell, whose
                        // `display:table-cell` div holds a green abspos and a
                        // red decoy: Android painted NEITHER (capture carries
                        // only the header glyphs) while iOS painted the green
                        // 100×100 at the reference's [16,88..115,187].
                        // BLOCK routes them through RenderContent, which is
                        // where the positioned-container branch (CSS 2.2
                        // §10.1 padding-box anchor), the mixed-content text
                        // run, and every ordinary paint applier live.
                        // The cell's table CHROME is unaffected: the
                        // synthesizer still wraps each cell in
                        // TableApplier.TableCell before calling
                        // RenderComponent on it.
                        "TABLE", "TABLE_ROW", "TABLE-ROW" -> DisplayType.TABLE
                        "TABLE_CELL", "TABLE-CELL",
                        "TABLE_CAPTION", "TABLE-CAPTION" -> DisplayType.BLOCK
                        "INLINE", "INLINE_BLOCK", "INLINE-BLOCK" -> DisplayType.INLINE
                        "NONE" -> DisplayType.NONE
                        else -> DisplayType.BLOCK
                    }
                }
                // Check for multi-column layout (column-count or column-width)
                "ColumnCount", "ColumnWidth" -> {
                    if (displayType == DisplayType.BLOCK) {
                        displayType = DisplayType.MULTI_COLUMN
                    }
                }
                "FlexDirection" -> {
                    val keyword = ValueExtractors.extractKeyword(prop.data)?.uppercase()
                    // AXIS fold only: *_REVERSE picks the same Row/Column
                    // composable as its forward twin (the main-axis
                    // ORIENTATION is what the container choice needs). The
                    // reversal itself is NOT modelled for in-flow item
                    // order (documented gap), but the wave-19 abspos
                    // static-position resolver (AbsposStaticPosition) reads
                    // the RAW wire keyword directly in the flex loops, so
                    // out-of-flow children DO honor *_REVERSE + writing
                    // modes — this fold no longer erases reversal for them.
                    flexDirection = when (keyword) {
                        "COLUMN", "COLUMN_REVERSE", "COLUMN-REVERSE" -> FlexDirection.COLUMN
                        else -> FlexDirection.ROW
                    }
                    // Update display type based on flex direction
                    if (displayType == DisplayType.FLEX_ROW && flexDirection == FlexDirection.COLUMN) {
                        displayType = DisplayType.FLEX_COLUMN
                    }
                }
                "JustifyContent" -> {
                    val keyword = ValueExtractors.extractKeyword(prop.data)?.uppercase()
                    justifyContent = when (keyword) {
                        "CENTER" -> JustifyContent.CENTER
                        // css-align-3 §5.2: physical `right` ≡ `end` in the
                        // LTR-normalized engine (matches FlexboxExtractor).
                        "FLEX_END", "FLEX-END", "END", "RIGHT" -> JustifyContent.FLEX_END
                        "SPACE_BETWEEN", "SPACE-BETWEEN" -> JustifyContent.SPACE_BETWEEN
                        "SPACE_AROUND", "SPACE-AROUND" -> JustifyContent.SPACE_AROUND
                        "SPACE_EVENLY", "SPACE-EVENLY" -> JustifyContent.SPACE_EVENLY
                        else -> JustifyContent.FLEX_START
                    }
                }
                "AlignItems" -> {
                    val keyword = ValueExtractors.extractKeyword(prop.data)?.uppercase()
                    alignItems = when (keyword) {
                        "CENTER" -> AlignItems.CENTER
                        "FLEX_START", "FLEX-START", "START" -> AlignItems.FLEX_START
                        "FLEX_END", "FLEX-END", "END" -> AlignItems.FLEX_END
                        "BASELINE" -> AlignItems.BASELINE
                        else -> AlignItems.STRETCH
                    }
                }
                "AlignContent" -> {
                    val keyword = ValueExtractors.extractKeyword(prop.data)?.uppercase()
                    alignContent = when (keyword) {
                        "CENTER" -> AlignContent.CENTER
                        "FLEX_START", "FLEX-START", "START" -> AlignContent.FLEX_START
                        "FLEX_END", "FLEX-END", "END" -> AlignContent.FLEX_END
                        "SPACE_BETWEEN", "SPACE-BETWEEN" -> AlignContent.SPACE_BETWEEN
                        "SPACE_AROUND", "SPACE-AROUND" -> AlignContent.SPACE_AROUND
                        "SPACE_EVENLY", "SPACE-EVENLY" -> AlignContent.SPACE_EVENLY
                        else -> AlignContent.STRETCH
                    }
                }
                "FlexWrap" -> {
                    val keyword = ValueExtractors.extractKeyword(prop.data)?.uppercase()
                    flexWrap = when (keyword) {
                        "WRAP" -> FlexWrap.WRAP
                        "WRAP_REVERSE", "WRAP-REVERSE" -> FlexWrap.WRAP_REVERSE
                        else -> FlexWrap.NOWRAP
                    }
                }
                "Gap" -> {
                    ValueExtractors.extractDp(prop.data)?.let {
                        rowGap = it
                        columnGap = it
                    }
                }
                "RowGap" -> {
                    ValueExtractors.extractDp(prop.data)?.let { rowGap = it }
                }
                "ColumnGap" -> {
                    ValueExtractors.extractDp(prop.data)?.let { columnGap = it }
                }
            }
        }

        // ── Wave-35 lane B3: order-independent multicol promotion ─────────
        // css-multicol-1 §2: `column-count`/`column-width` turn a BLOCK
        // container into a multi-column one — the two declarations are not
        // in competition, `display: block` is the multicol container's OWN
        // display. The loop above, however, is order-sensitive: the
        // ColumnCount arm only promotes `if (displayType == BLOCK)`, and a
        // LATER `Display: BLOCK` arm then writes BLOCK straight back over
        // the promotion. The extractor's wire order is the emitter's, not
        // the author's, so whether a container reached the multicol layout
        // at all depended on which key post-load-extract happened to write
        // first. Measured over the whole frozen wave34-final gate: exactly
        // TWO components carry ColumnCount BEFORE their Display and are
        // block-ish after it — css-multicol ancestor-toggle-spanner-001 and
        // -002, both of which render their `columns: 2` box as a plain
        // block today (-001 captures pure RED where every other platform is
        // GREEN). Re-asserting the promotion here makes the decision depend
        // on the DECLARATIONS rather than their order, and touches nothing
        // else in the corpus.
        if (displayType == DisplayType.BLOCK &&
            properties.any { it.type == "ColumnCount" || it.type == "ColumnWidth" }
        ) {
            displayType = DisplayType.MULTI_COLUMN
        }

        return DisplayConfig(
            type = displayType,
            flexDirection = flexDirection,
            justifyContent = justifyContent,
            alignItems = alignItems,
            alignContent = alignContent,
            flexWrap = flexWrap,
            rowGap = rowGap,
            columnGap = columnGap
        )
    }

    // ==================== CONFIG TYPES ====================

    data class DisplayConfig(
        val type: DisplayType,
        val flexDirection: FlexDirection,
        val justifyContent: JustifyContent,
        val alignItems: AlignItems,
        val alignContent: AlignContent = AlignContent.STRETCH,
        val flexWrap: FlexWrap,
        val rowGap: androidx.compose.ui.unit.Dp = 0.dp,
        val columnGap: androidx.compose.ui.unit.Dp = 0.dp
    ) {
        // Legacy support - get single gap value
        val gap: androidx.compose.ui.unit.Dp get() = maxOf(rowGap, columnGap)
    }

    enum class DisplayType {
        BLOCK, INLINE, FLEX_ROW, FLEX_COLUMN, GRID, TABLE, MULTI_COLUMN, NONE
    }

    enum class FlexDirection {
        ROW, COLUMN
    }

    enum class JustifyContent {
        FLEX_START, FLEX_END, CENTER, SPACE_BETWEEN, SPACE_AROUND, SPACE_EVENLY
    }

    enum class AlignItems {
        STRETCH, FLEX_START, FLEX_END, CENTER, BASELINE
    }

    enum class AlignContent {
        STRETCH, FLEX_START, FLEX_END, CENTER, SPACE_BETWEEN, SPACE_AROUND, SPACE_EVENLY
    }

    enum class FlexWrap {
        NOWRAP, WRAP, WRAP_REVERSE
    }

    // ==================== ARRANGEMENT/ALIGNMENT CONVERTERS ====================

    /**
     * Convert DisplayConfig to horizontal arrangement with gap support.
     */
    private fun DisplayConfig.toRowArrangement(): Arrangement.Horizontal {
        val spacing = columnGap
        return when (justifyContent) {
            JustifyContent.FLEX_START -> if (spacing > 0.dp) Arrangement.spacedBy(spacing, Alignment.Start) else Arrangement.Start
            JustifyContent.FLEX_END -> if (spacing > 0.dp) Arrangement.spacedBy(spacing, Alignment.End) else Arrangement.End
            JustifyContent.CENTER -> if (spacing > 0.dp) Arrangement.spacedBy(spacing, Alignment.CenterHorizontally) else Arrangement.Center
            JustifyContent.SPACE_BETWEEN -> Arrangement.SpaceBetween
            JustifyContent.SPACE_AROUND -> Arrangement.SpaceAround
            JustifyContent.SPACE_EVENLY -> Arrangement.SpaceEvenly
        }
    }

    /**
     * Convert DisplayConfig to vertical arrangement with gap support.
     */
    private fun DisplayConfig.toColumnArrangement(): Arrangement.Vertical {
        val spacing = rowGap
        return when (justifyContent) {
            JustifyContent.FLEX_START -> if (spacing > 0.dp) Arrangement.spacedBy(spacing, Alignment.Top) else Arrangement.Top
            JustifyContent.FLEX_END -> if (spacing > 0.dp) Arrangement.spacedBy(spacing, Alignment.Bottom) else Arrangement.Bottom
            JustifyContent.CENTER -> if (spacing > 0.dp) Arrangement.spacedBy(spacing, Alignment.CenterVertically) else Arrangement.Center
            JustifyContent.SPACE_BETWEEN -> Arrangement.SpaceBetween
            JustifyContent.SPACE_AROUND -> Arrangement.SpaceAround
            JustifyContent.SPACE_EVENLY -> Arrangement.SpaceEvenly
        }
    }

    private fun AlignItems.toRowAlignment(): Alignment.Vertical = when (this) {
        AlignItems.FLEX_START -> Alignment.Top
        AlignItems.FLEX_END -> Alignment.Bottom
        AlignItems.CENTER -> Alignment.CenterVertically
        AlignItems.BASELINE -> Alignment.CenterVertically // Baseline not directly supported
        AlignItems.STRETCH -> Alignment.CenterVertically
    }

    private fun AlignItems.toColumnAlignment(): Alignment.Horizontal = when (this) {
        AlignItems.FLEX_START -> Alignment.Start
        AlignItems.FLEX_END -> Alignment.End
        AlignItems.CENTER -> Alignment.CenterHorizontally
        AlignItems.BASELINE -> Alignment.Start
        AlignItems.STRETCH -> Alignment.CenterHorizontally
    }

    private fun AlignItems.toBoxAlignment(): Alignment = when (this) {
        AlignItems.FLEX_START -> Alignment.TopStart
        AlignItems.FLEX_END -> Alignment.BottomEnd
        AlignItems.CENTER -> Alignment.Center
        AlignItems.BASELINE -> Alignment.TopStart
        // Default `align-items: stretch` for a non-flex Box has no real
        // analog in Compose (Box has no stretch alignment). CSS block-flow
        // default places contents top-LEFT (the implicit `text-align: start`
        // inside the box), so use TopStart. Was TopCenter, which centred
        // every placeholder horizontally — Card_Complete / Input_Field /
        // Tag_Chip rendered with text mid-card while iOS/web rendered
        // them at the top-left of the padding band.
        AlignItems.STRETCH -> Alignment.TopStart
    }
}

/**
 * Placeholder break-word shim (wave 5). The web harness's placeholder
 * <span> declares `word-break: break-word`, which makes the span's
 * MIN-CONTENT contribution a single grapheme instead of the widest word
 * (css-text-3 §5.2: break-word allows breaks anywhere for intrinsic-size
 * purposes' worst case). Compose Text reports its longest word as the min
 * intrinsic, so `width: min-content` boxes measured word-wide on Android
 * while the web reference collapsed to a one-character column
 * (PW_Sizing_Spacing_02: 100px vs 49px, A-w 0.868).
 *
 * The shim caps ONLY the reported minIntrinsicWidth at ~1em of the
 * placeholder's font size; measurement itself (and the other three
 * intrinsics) pass through untouched, so plain bounded layouts see no
 * difference. When the capped intrinsic then feeds a narrow measure
 * constraint, Compose Text already breaks words mid-grapheme — the same
 * fallback rendering break-word produces.
 */
private class BreakWordMinIntrinsicModifier(
    private val fontSizeSp: Float
) : androidx.compose.ui.layout.LayoutModifier {
    override fun androidx.compose.ui.layout.MeasureScope.measure(
        measurable: androidx.compose.ui.layout.Measurable,
        constraints: androidx.compose.ui.unit.Constraints
    ): androidx.compose.ui.layout.MeasureResult {
        // Pass-through measurement — the shim only affects intrinsics.
        val placeable = measurable.measure(constraints)
        return layout(placeable.width, placeable.height) {
            placeable.placeRelative(0, 0)
        }
    }

    override fun androidx.compose.ui.layout.IntrinsicMeasureScope.minIntrinsicWidth(
        measurable: androidx.compose.ui.layout.IntrinsicMeasurable,
        height: Int
    ): Int {
        // One em ≈ the widest single grapheme ("W" at Inter 16 ≈ 15-16px) —
        // the break-word min-content floor. Never RAISE the child's own
        // min intrinsic (short labels stay exact).
        val oneChar = with(this) { fontSizeSp.sp.roundToPx() }
        return minOf(measurable.minIntrinsicWidth(height), oneChar)
    }
}
