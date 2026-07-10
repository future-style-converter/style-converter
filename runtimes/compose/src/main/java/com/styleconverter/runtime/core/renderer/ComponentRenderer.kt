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
import androidx.compose.ui.graphics.Color
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
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonPrimitive
import com.styleconverter.runtime.lists.ListStyleConfig
import com.styleconverter.runtime.lists.ListStyleExtractor
import com.styleconverter.runtime.lists.ListStyleType
import com.styleconverter.runtime.lists.ListStyleApplier as StyleListApplier
import androidx.compose.ui.draw.drawBehind
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
import com.styleconverter.runtime.core.media.MediaQueryApplier

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
     * CSS-inherited text properties (css-cascade-4 / per-property "Inherited:
     * yes" table). These — and ONLY these — flow from parent to child when
     * the child doesn't declare them itself. Layout/box properties (Width,
     * Padding, Background*, Border*) are deliberately absent: they never
     * inherit in CSS. TextDecorationLine is also absent — decoration
     * PROPAGATES to inline descendants rather than inheriting, and our
     * leading-text sibling already handles the visible case.
     *
     * `Color` is deliberately absent too, even though CSS inherits it:
     * the WEB harness placeholder span (`PlaceholderContent` in
     * ComponentRenderer.tsx) always sets an explicit `color` — the
     * bg-luminance contrast pick — which beats browser inheritance on the
     * reference render. Verified by pixel-sampling the IT_Family web
     * capture: child text paints rgba(237,237,237,0.7), NOT the parent's
     * #111. Android's PlaceholderContent implements the same pick, so
     * inheriting Color here would push the platforms APART, not together.
     */
    internal val INHERITED_TEXT_PROPERTY_TYPES: Set<String> = setOf(
        "FontFamily", "FontSize", "FontWeight", "FontStyle", "FontStretch",
        "LetterSpacing", "LineHeight", "WordSpacing",
        "TextAlign", "TextTransform", "TextIndent",
        "WhiteSpace", "TabSize", "Direction"
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
     * Merge the inherited channel under the component's own declarations.
     * Pure function (JVM-testable): own properties always win; inherited
     * entries only fill types the component didn't declare.
     */
    internal fun mergeInherited(
        own: List<IRProperty>,
        inherited: List<IRProperty>
    ): List<IRProperty> {
        if (inherited.isEmpty()) return own
        val declaredTypes = own.mapTo(HashSet()) { it.type }
        return inherited.filter { it.type !in declaredTypes } + own
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
        // Apply media queries to get effective properties based on screen size
        val mediaProperties = if (component.media.isNotEmpty()) {
            MediaQueryApplier.applyMediaQueries(
                baseProperties = component.properties,
                mediaQueries = component.media
            )
        } else {
            component.properties
        }

        // Fold the parent's inheritable text declarations in UNDER the
        // component's own (CSS inheritance — see LocalInheritedProperties).
        val rawProperties = mergeInherited(mediaProperties, LocalInheritedProperties.current)

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
        val effectiveProperties = if (allReset != null) emptyList() else rawProperties

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
        val baseModifier = try {
            StyleApplier.applyProperties(effectiveProperties)
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
        // itemModifier (parent-injected stretch) goes OUTERMOST so the grid
        // cell's fillMaxHeight established the constraint the style chain
        // then works within.
        val sizedModifier = itemModifier.then(baseModifier).then(
            Modifier.defaultMinSize(
                minWidth = if (hasExplicitWidth) Dp.Unspecified else 50.dp,
                minHeight = if (hasExplicitHeight) Dp.Unspecified else 30.dp
            )
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

        // Apply animations to modifier if present
        val modifier = if (animationConfig?.hasAnimations == true) {
            animatedModifier(sizedModifier, animationConfig, transitionConfig ?: com.styleconverter.runtime.animations.TransitionConfig())
        } else {
            sizedModifier
        }

        val displayConfig = try {
            extractDisplayConfig(effectiveProperties)
        } catch (e: Exception) {
            DisplayConfig(DisplayType.BLOCK, FlexDirection.ROW, JustifyContent.FLEX_START, AlignItems.STRETCH, AlignContent.STRETCH, FlexWrap.NOWRAP, 0.dp, 0.dp)
        }

        val textColor = try {
            TextStyleApplier.extractTextColor(effectiveProperties)
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

        // Wrap content with direction if not default LTR
        val content: @Composable () -> Unit = {
            // Wrap in BorderImageBox if border image is configured
            if (borderImageConfig?.hasBorderImage == true) {
                BorderImageApplier.BorderImageBox(
                    config = borderImageConfig,
                    modifier = modifier
                ) {
                    RenderComponentContent(component, Modifier, displayConfig, textColor, engineDecision, flexDecision)
                }
            } else {
                RenderComponentContent(component, modifier, displayConfig, textColor, engineDecision, flexDecision)
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
        val inheritableForChildren = effectiveProperties.filter {
            it.type in INHERITED_TEXT_PROPERTY_TYPES
        }
        val inheritanceWrappedContent: @Composable () -> Unit = {
            CompositionLocalProvider(LocalInheritedProperties provides inheritableForChildren) {
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
        val selfAlignedContent: @Composable () -> Unit =
            if (!LocalSelfAlignmentHandled.current &&
                (blockJustifySelf == JustifySelf.CENTER ||
                    blockJustifySelf == JustifySelf.END ||
                    blockJustifySelf == JustifySelf.FLEX_END ||
                    blockJustifySelf == JustifySelf.RIGHT)
            ) {
                {
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        // Physical right / end / flex-end all collapse to End
                        // in the LTR-normalized engine (css-align-3 §5.2).
                        contentAlignment = if (blockJustifySelf == JustifySelf.CENTER)
                            Alignment.TopCenter else Alignment.TopEnd
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
            when (flexDecision.kind) {
                com.styleconverter.runtime.layout.flexbox.FlexContainerKind.Row -> {
                    Row(
                        modifier = modifier,
                        // Gap + justify-content COMPOSE, they don't compete:
                        // a distributing justify (space-between/around/evenly)
                        // owns the free space — the old `gap > 0 → spacedBy`
                        // override packed FC_SpaceBetween's children at the
                        // top because the fixture also declared `gap: 6px`
                        // (Android-web 0.920). mainAxisArrangement folds the
                        // gap into spacedBy(gap, <align>) only for the
                        // non-distributing keywords, mirroring the legacy
                        // toRowArrangement path.
                        horizontalArrangement = com.styleconverter.runtime.layout.flexbox
                            .FlexboxApplier.mainAxisHorizontal(flexDecision.justify, columnGap),
                        verticalAlignment = flexDecision.verticalAlignment
                    ) {
                        RenderRowContent(component, textColor)
                    }
                    return
                }
                com.styleconverter.runtime.layout.flexbox.FlexContainerKind.Column -> {
                    Column(
                        modifier = modifier,
                        // Same gap/justify composition as the Row branch.
                        verticalArrangement = com.styleconverter.runtime.layout.flexbox
                            .FlexboxApplier.mainAxisVertical(flexDecision.justify, rowGap),
                        horizontalAlignment = flexDecision.horizontalAlignment
                    ) {
                        RenderColumnContent(component, textColor)
                    }
                    return
                }
                com.styleconverter.runtime.layout.flexbox.FlexContainerKind.FlowRow -> {
                    FlowRow(
                        modifier = modifier,
                        horizontalArrangement = flexDecision.horizontalArrangement,
                        verticalArrangement = Arrangement.spacedBy(rowGap)
                    ) {
                        RenderContent(component, textColor, displayConfig)
                    }
                    return
                }
                com.styleconverter.runtime.layout.flexbox.FlexContainerKind.FlowColumn -> {
                    FlowColumn(
                        modifier = modifier,
                        verticalArrangement = flexDecision.verticalArrangement,
                        horizontalArrangement = Arrangement.spacedBy(columnGap)
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
                    Row(
                        modifier = rowModifier,
                        horizontalArrangement = displayConfig.toRowArrangement(),
                        verticalAlignment = displayConfig.alignItems.toRowAlignment()
                    ) {
                        RenderRowContent(component, textColor)
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
                    Column(
                        modifier = columnModifier,
                        verticalArrangement = displayConfig.toColumnArrangement(),
                        horizontalAlignment = displayConfig.alignItems.toColumnAlignment()
                    ) {
                        RenderColumnContent(component, textColor)
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
                MultiColumnApplier.MultiColumnLayout(
                    config = columnConfig,
                    modifier = modifier
                ) {
                    RenderContent(component, textColor, displayConfig)
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
                    Column(modifier = modifier) {
                        RenderContent(component, textColor, displayConfig)
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
     */
    @Composable
    private fun RenderTableContent(component: IRComponent, textColor: Color?) {
        if (!component.children.isNullOrEmpty()) {
            component.children.forEach { rowComponent ->
                // Each child is a table row
                TableApplier.TableRow {
                    if (!rowComponent.children.isNullOrEmpty()) {
                        rowComponent.children.forEach { cellComponent ->
                            // Each grandchild is a table cell
                            TableCell {
                                RenderComponent(cellComponent)
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
        if (!component.children.isNullOrEmpty()) {
            // Bug 1: leading _text node — wrapped in a Box so it sits as a
            // sibling of the children. PlaceholderContent uses the parent's
            // properties for text colour/font/decoration so the inline run
            // inherits the parent's styling, matching the web <span>
            // fallback.
            val parentText = component._text
            if (!parentText.isNullOrEmpty()) {
                PlaceholderContent(
                    name = parentText,
                    textColor = textColor,
                    properties = component.properties,
                    rawText = parentText
                )
            }

            // Bug 2: precompute list-marker config from the parent _tag
            // so we don't re-resolve per child. listConfig is non-null
            // only when the parent is <ol>/<ul>. We feed it to
            // RenderListItemMarker below.
            val parentTag = component._tag?.lowercase()
            val isListParent = parentTag == "ol" || parentTag == "ul"
            val listType = when (parentTag) {
                "ol" -> ListStyleType.DECIMAL
                "ul" -> ListStyleType.DISC
                else -> ListStyleType.NONE
            }
            val listConfig = if (isListParent) ListStyleConfig(listStyleType = listType) else null

            // Check if this is a positioned container (position: relative)
            val isPositionedContainer = extractPositionType(component.properties) == PositionType.RELATIVE

            if (isPositionedContainer) {
                // Render with Box to support absolute positioning
                Box(modifier = Modifier.fillMaxSize()) {
                    component.children.forEachIndexed { index, child ->
                        val childPosition = extractPositionType(child.properties)
                        if (childPosition == PositionType.ABSOLUTE || childPosition == PositionType.FIXED) {
                            // Render absolutely positioned child with offset
                            RenderAbsoluteChild(child)
                        } else if (listConfig != null && child._tag?.lowercase() == "li") {
                            RenderListItemMarker(child, index, listConfig, textColor)
                        } else {
                            RenderComponent(child)
                        }
                    }
                }
            } else {
                component.children.forEachIndexed { index, child ->
                    if (listConfig != null && child._tag?.lowercase() == "li") {
                        RenderListItemMarker(child, index, listConfig, textColor)
                    } else {
                        RenderComponent(child)
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
                rawText = component._text
            )
        }
    }

    /**
     * Render a <li> child with a leading marker derived from the parent
     * <ol>/<ul>'s listConfig. Compose has no ::marker pseudo, so we
     * synthesise a Row(Text(marker) + RenderComponent(child)) which
     * mirrors the simple-numeric / bullet behaviour the browser would
     * produce. Marker counter uses 1-based index from the parent's
     * children list — fine for the basic css-counter-styles tests where
     * the IR matches the source <li> ordering verbatim.
     */
    @Composable
    private fun RenderListItemMarker(
        child: IRComponent,
        index: Int,
        listConfig: ListStyleConfig,
        textColor: Color?
    ) {
        val marker = StyleListApplier.getMarker(index, listConfig)
        Row(verticalAlignment = Alignment.Top) {
            // Marker text: prefer the explicit textColor from the parent
            // when known so the bullet/number matches the surrounding
            // text. The trailing space mimics the browser's default
            // marker suffix when list-style-position is outside.
            Text(
                text = "$marker ",
                color = textColor ?: Color.Unspecified,
                modifier = Modifier.padding(end = 4.dp)
            )
            RenderComponent(child)
        }
    }

    /**
     * Render an absolutely positioned child.
     */
    @Composable
    private fun RenderAbsoluteChild(child: IRComponent) {
        val positionOffset = extractPositionOffsets(child.properties)
        val zIndexValue = extractZIndex(child.properties)

        // Build offset modifier based on top/right/bottom/left
        var offsetModifier: Modifier = Modifier

        // Apply z-index
        if (zIndexValue != 0f) {
            offsetModifier = offsetModifier.zIndex(zIndexValue)
        }

        // Apply position offsets
        if (positionOffset.top != null || positionOffset.left != null) {
            offsetModifier = offsetModifier.offset(
                x = positionOffset.left ?: 0.dp,
                y = positionOffset.top ?: 0.dp
            )
        }

        // Wrap in Box with offset
        Box(modifier = offsetModifier) {
            RenderComponent(child)
        }
    }

    /**
     * Position type enum.
     */
    enum class PositionType {
        STATIC, RELATIVE, ABSOLUTE, FIXED, STICKY
    }

    /**
     * Position offsets data class.
     */
    data class PositionOffsets(
        val top: Dp? = null,
        val right: Dp? = null,
        val bottom: Dp? = null,
        val left: Dp? = null
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
     * Extract position offsets (top, right, bottom, left) from properties.
     */
    private fun extractPositionOffsets(properties: List<IRProperty>): PositionOffsets {
        var top: Dp? = null
        var right: Dp? = null
        var bottom: Dp? = null
        var left: Dp? = null

        properties.forEach { prop ->
            when (prop.type) {
                "Top", "InsetBlockStart" -> top = ValueExtractors.extractDp(prop.data)
                "Right", "InsetInlineEnd" -> right = ValueExtractors.extractDp(prop.data)
                "Bottom", "InsetBlockEnd" -> bottom = ValueExtractors.extractDp(prop.data)
                "Left", "InsetInlineStart" -> left = ValueExtractors.extractDp(prop.data)
            }
        }

        return PositionOffsets(top, right, bottom, left)
    }

    /**
     * Extract z-index from properties.
     */
    private fun extractZIndex(properties: List<IRProperty>): Float {
        properties.forEach { prop ->
            if (prop.type == "ZIndex") {
                return ValueExtractors.extractFloat(prop.data) ?: 0f
            }
        }
        return 0f
    }

    /**
     * Render content in Row scope with AlignSelf, FlexGrow, and Order support.
     */
    @Composable
    fun RowScope.RenderRowContent(component: IRComponent, textColor: Color?) {
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
            // Sort children by order property
            val sortedChildren = sortByOrder(component.children)
            // Run the real §9.7 algorithm when the line is statically
            // resolvable (definite container main size + every child's flex
            // base known in px). Returns null otherwise → the legacy
            // weight fallback below stays in charge (wave-1 behaviour).
            val resolvedSizes = resolveFlexMainSizes(component, sortedChildren, rowAxis = true)
            // Flex items ignore justify-self (css-align-3 §6) and this Row
            // already owns align-self — turn the block-level self-alignment
            // wrapper off for the whole subtree root at each child.
            CompositionLocalProvider(LocalSelfAlignmentHandled provides true) {
            sortedChildren.forEachIndexed { index, child ->
                val alignSelf = extractAlignSelf(child.properties)
                val flexGrow = extractFlexGrow(child.properties)
                // `align-self: stretch` only stretches an item whose cross
                // size is AUTO (css-flexbox-1 §8.3); with a definite cross
                // size it behaves as flex-start. Row cross axis = vertical.
                val childHeightDefinite = hasDefiniteSize(child.properties, widthAxis = false)
                val stretches = alignSelf == AlignSelf.STRETCH && !childHeightDefinite

                // Build modifier with align and weight
                var childModifier: Modifier = Modifier
                childModifier = when (alignSelf) {
                    AlignSelf.FLEX_START -> childModifier.align(Alignment.Top)
                    AlignSelf.FLEX_END -> childModifier.align(Alignment.Bottom)
                    AlignSelf.CENTER -> childModifier.align(Alignment.CenterVertically)
                    // Definite-height stretch = flex-start (see above). The
                    // auto-height case fills below instead of aligning.
                    AlignSelf.STRETCH -> if (stretches) childModifier.fillMaxHeight()
                        else childModifier.align(Alignment.Top)
                    else -> childModifier
                }

                // Apply flex-grow as weight ONLY when the §9.7 resolver
                // could not run (definite main size still required).
                if (resolvedSizes == null && flexGrow > 0f && mainSizeDefinite) {
                    childModifier = childModifier.weight(flexGrow)
                }

                // Resolved main size pins the child's width OUTERMOST so
                // basis/grow/shrink win over the placeholder's 50dp floor
                // (FR_GrowBasis b/c grew 40→93/145 on web; FR_ShrinkBasis
                // b shrank 100→58 — pixel-verified against web captures).
                var itemModifier: Modifier = Modifier
                resolvedSizes?.get(index)?.let { itemModifier = itemModifier.width(it.toFloat().dp) }
                if (stretches) itemModifier = itemModifier.fillMaxHeight()

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
     */
    @Composable
    fun ColumnScope.RenderColumnContent(component: IRComponent, textColor: Color?) {
        if (!component.children.isNullOrEmpty()) {
            // Same css-flexbox-1 §9.7 rule as RenderRowContent, but the main
            // axis of a column flex container is BLOCK (height): free space
            // for flex-grow only exists when the container's height is
            // definite. Compose's weight would otherwise stretch the Column
            // to the full canvas height.
            val mainSizeDefinite = hasDefiniteSize(component.properties, widthAxis = false)
            // Sort children by order property
            val sortedChildren = sortByOrder(component.children)
            // §9.7 static resolver — column main axis is vertical (height).
            val resolvedSizes = resolveFlexMainSizes(component, sortedChildren, rowAxis = false)
            // Same suppression rationale as RenderRowContent.
            CompositionLocalProvider(LocalSelfAlignmentHandled provides true) {
            sortedChildren.forEachIndexed { index, child ->
                val alignSelf = extractAlignSelf(child.properties)
                val flexGrow = extractFlexGrow(child.properties)

                // Build modifier with align and weight
                var childModifier: Modifier = Modifier
                childModifier = when (alignSelf) {
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

                // Legacy weight fallback — only when §9.7 couldn't run.
                if (resolvedSizes == null && flexGrow > 0f && mainSizeDefinite) {
                    childModifier = childModifier.weight(flexGrow)
                }

                // Resolved main size pins the child height (FC_GrowBasis
                // b/c grew 30→66/101 on web — pixel-verified).
                var itemModifier: Modifier = Modifier
                resolvedSizes?.get(index)?.let { itemModifier = itemModifier.height(it.toFloat().dp) }

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
     * Statically run css-flexbox-1 §9.7 for a non-wrapping flex line.
     *
     * Returns the used main size (px) per child in [sortedChildren] order,
     * or null when the line isn't statically resolvable:
     *   - container main size isn't a definite px value, or
     *   - no child declares any flex property (nothing to resolve — keeps
     *     the legacy path byte-identical for plain rows/columns), or
     *   - some child's flex base is content-sized (flex-basis auto without
     *     a definite main-size property).
     *
     * The per-item minimum mirrors the WEB harness wrapper
     * (`ComponentRenderer.tsx`): min = main-size ?? min-size ?? placeholder
     * floor (50px inline / 30px block) — that floor is what web's flex
     * algorithm clamps against (FR_GrowBasis `a`: basis 40 → rendered 50).
     */
    internal fun resolveFlexMainSizes(
        component: IRComponent,
        sortedChildren: List<IRComponent>,
        rowAxis: Boolean
    ): List<Double>? {
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
            com.styleconverter.runtime.layout.flexbox.FlexSizeResolver.Item(
                // Used flex basis: flex-basis, else the main-size property,
                // else content (null → line unresolvable).
                basisPx = flexBasisPx(cp) ?: mainSize,
                grow = extractFlexGrow(cp).toDouble(),
                shrink = extractFlexShrink(cp).toDouble(),
                // Web wrapper: minWidth = width || min-width || 50px (30px
                // floor on the block axis).
                minPx = mainSize ?: minSize ?: (if (rowAxis) 50.0 else 30.0)
            )
        }
        return com.styleconverter.runtime.layout.flexbox.FlexSizeResolver
            .resolve(contentMain, gap, items)
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

    /**
     * FlexBasis IR shape: `{"value":{"px":40.0},"normalizedPixels":40.0}`.
     * Percentage / auto / content bases return null (not statically
     * resolvable — %-of-parent needs layout-time context we don't model).
     */
    private fun flexBasisPx(properties: List<IRProperty>): Double? {
        val data = properties.firstOrNull { it.type == "FlexBasis" }?.data ?: return null
        val obj = data as? JsonObject ?: return null
        obj["normalizedPixels"]?.jsonPrimitive?.doubleOrNull?.let { return it }
        return (obj["value"] as? JsonObject)?.get("px")?.jsonPrimitive?.doubleOrNull
    }

    /**
     * Extract flex-shrink (CSS initial 1). Same nested-Number IR shape as
     * flex-grow — ValueExtractors.extractFloat unwraps it.
     */
    private fun extractFlexShrink(properties: List<IRProperty>): Float {
        properties.forEach { prop ->
            if (prop.type == "FlexShrink") {
                return ValueExtractors.extractFloat(prop.data) ?: 1f
            }
        }
        return 1f
    }

    /**
     * Extract flex-grow value from properties.
     */
    private fun extractFlexGrow(properties: List<IRProperty>): Float {
        properties.forEach { prop ->
            if (prop.type == "FlexGrow") {
                return ValueExtractors.extractFloat(prop.data) ?: 0f
            }
        }
        return 0f
    }

    /**
     * Extract align-self value from properties.
     *
     * Internal (not private) so the unit test can pin the LIVE render-path
     * mapping directly — see ComponentRendererAlignSelfTest.
     */
    internal fun extractAlignSelf(properties: List<IRProperty>): AlignSelf {
        properties.forEach { prop ->
            if (prop.type == "AlignSelf") {
                val keyword = ValueExtractors.extractKeyword(prop.data)?.uppercase()
                return when (keyword) {
                    "FLEX_START", "FLEX-START", "START" -> AlignSelf.FLEX_START
                    "FLEX_END", "FLEX-END", "END" -> AlignSelf.FLEX_END
                    "CENTER" -> AlignSelf.CENTER
                    // CSS Anchor Positioning Level 1 §6: `anchor-center`
                    // collapses to `center` whenever no default anchor is in
                    // scope. The SDUI runtime has no anchor-positioning model,
                    // so this fold is unconditionally correct. Mirrors
                    // FlexboxExtractor.parseAlignment and
                    // FlexExtractor.parseAlignSelf (plus iOS/Web appliers) —
                    // previously this live path fell through to AUTO while
                    // the engine-path copies were already fixed.
                    "ANCHOR_CENTER", "ANCHOR-CENTER" -> AlignSelf.CENTER
                    "STRETCH" -> AlignSelf.STRETCH
                    "BASELINE" -> AlignSelf.BASELINE
                    else -> AlignSelf.AUTO
                }
            }
        }
        return AlignSelf.AUTO
    }

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
     */
    fun extractOrder(properties: List<IRProperty>): Int {
        properties.forEach { prop ->
            if (prop.type == "Order") {
                return ValueExtractors.extractInt(prop.data) ?: 0
            }
        }
        return 0
    }

    /**
     * Extract justify-self value from properties.
     */
    fun extractJustifySelf(properties: List<IRProperty>): JustifySelf {
        properties.forEach { prop ->
            if (prop.type == "JustifySelf") {
                val keyword = ValueExtractors.extractKeyword(prop.data)?.uppercase()
                    ?: ValueExtractors.extractKeywordFromObject(prop.data)?.uppercase()
                return when (keyword) {
                    "AUTO" -> JustifySelf.AUTO
                    "NORMAL" -> JustifySelf.NORMAL
                    "START", "SELF_START", "SELF-START" -> JustifySelf.START
                    "END", "SELF_END", "SELF-END" -> JustifySelf.END
                    "CENTER" -> JustifySelf.CENTER
                    "STRETCH" -> JustifySelf.STRETCH
                    "FLEX_START", "FLEX-START" -> JustifySelf.FLEX_START
                    "FLEX_END", "FLEX-END" -> JustifySelf.FLEX_END
                    "LEFT" -> JustifySelf.LEFT
                    "RIGHT" -> JustifySelf.RIGHT
                    "BASELINE" -> JustifySelf.BASELINE
                    else -> JustifySelf.AUTO
                }
            }
        }
        return JustifySelf.AUTO
    }

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
     * @param rawText Optional verbatim text override. When non-null and
     *   non-empty, replaces the underscore-stripped `name` as the visible
     *   string AND skips the text-transform pass (the source text is
     *   already the intended visible content). Powers the IR `_text`
     *   channel (swarm-001 css-color__color-001 leaf-text fix, swarm-002
     *   mixed-content fix). Absent rawText → existing placeholder
     *   behaviour, preserving the 327-pair baseline.
     */
    @Composable
    internal fun PlaceholderContent(
        name: String,
        textColor: Color?,
        properties: List<IRProperty> = emptyList(),
        itemIndex: Int = 0,
        rawText: String? = null
    ) {
        // When rawText is supplied (the IR's `_text` channel), it wins
        // over the synthesised "Component Name" placeholder AND bypasses
        // text-transform (the extractor already captured the desired
        // visible text). For legacy fixtures with no _text we fall
        // through to the underscore-stripped name path.
        val hasRawText = !rawText.isNullOrEmpty()
        var displayText = if (hasRawText) {
            rawText!!
        } else {
            val textTransform = TextStyleApplier.extractTextTransform(properties)
            TextStyleApplier.applyTextTransform(
                name.replace("_", " "),
                textTransform
            )
        }

        // Extract tab-size and process tabs in text
        val tabConfig = TextStyleApplier.extractTabSize(properties)
        displayText = TextStyleApplier.applyTabSize(displayText, tabConfig)

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

        // Extract additional text style properties
        val textStyle = TextStyleApplier.extractTextStyle(properties)

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
        val defaultPlaceholderColor = run {
            val bgColor = properties.find { it.type == "BackgroundColor" }?.data?.let {
                com.styleconverter.runtime.core.types.ValueExtractors.extractColor(it)
            }
            if (bgColor != null) {
                val brightness = 0.299f * bgColor.red + 0.587f * bgColor.green + 0.114f * bgColor.blue
                if (brightness > 0.6f) Color(0xB3333333) else Color(0xB3EEEEEE)
            } else {
                Color(0xB3EEEEEE) // default light text for dark card background
            }
        }
        val effectiveColor = textColor ?: if (textStyle.color != Color.Unspecified) textStyle.color else defaultPlaceholderColor
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
        val effectiveLineHeight = if (textStyle.lineHeight != TextUnit.Unspecified)
            textStyle.lineHeight
        else
            (effectiveFontSize.value * 1.2f).sp

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
        val placeholderPlatformStyle = textStyle.platformStyle
        val placeholderLineHeightStyle = textStyle.lineHeightStyle
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
                lineHeightStyle = placeholderLineHeightStyle
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
                lineHeightStyle = placeholderLineHeightStyle
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

        // CSS overflow is VISIBLE by default: a nowrap line that exceeds
        // its box paints past the border box (web C20/C21 draw the full
        // single line across the canvas). Compose Text defaults to Clip,
        // which chopped the line at the box edge — only force Visible for
        // the nowrap case (no declared text-overflow), so line-clamp and
        // ellipsis fixtures keep their current clipping behaviour.
        val effectiveOverflow = if (!wrapConfig.softWrap &&
            properties.none { it.type == "TextOverflow" }
        ) TextOverflow.Visible else textOverflow

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

        // Block-level width fill — the web placeholder is a `display:block`
        // <span>, which spans the parent's content box whenever the parent
        // has a definite width. Compose Text hugs its glyphs by default, so
        // `text-align: center/right` had nothing to align within (the
        // TextAlign_Center fixture rendered left-flushed on Android while
        // web centered it). Fill only when the IR declares a definite
        // width — for `fit-content`-like unsized parents fillMaxWidth would
        // wrongly stretch the wrap-content box to the canvas width.
        val textModifier = if (placeholderFillsParentWidth(properties)) {
            Modifier.fillMaxWidth().padding(4.dp)
        } else {
            Modifier.padding(4.dp)
        }
        Text(
            text = annotatedText,
            style = styledTextStyle,
            maxLines = effectiveMaxLines,
            overflow = effectiveOverflow,
            softWrap = wrapConfig.softWrap,
            onTextLayout = { layoutResult.value = it },
            modifier = textModifier.then(emphasisModifier)
        )
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
                        "TABLE", "TABLE_ROW", "TABLE-ROW", "TABLE_CELL", "TABLE-CELL" -> DisplayType.TABLE
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
