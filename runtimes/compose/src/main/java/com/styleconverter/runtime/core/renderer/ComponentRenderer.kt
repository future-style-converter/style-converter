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
        val rawProperties = mergeInherited(schemeResolvedProperties, inheritedProperties)

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
        //  - viewport from LocalConfiguration (screen dp — the runtime's
        //    px==dp space, matching every other IR px→dp conversion);
        //  - parent font size from the inheritance channel (the parent
        //    always publishes RESOLVED px FontSize, see fontSizePxOf);
        //  - root font size: 16px residual constant — fixtures never style
        //    the root element, so the browser-default the web reference
        //    inherits is the honest value (documented in the resolver).
        val containingBlock = com.styleconverter.runtime.core.variables.LocalContainingBlock.current
        val configuration = androidx.compose.ui.platform.LocalConfiguration.current
        val dynCtx = com.styleconverter.runtime.core.variables.DynamicValueResolver.Context(
            containingBlockWidthPx = containingBlock.widthPx,
            containingBlockHeightPx = containingBlock.heightPx,
            viewportWidthPx = configuration.screenWidthDp.toFloat(),
            viewportHeightPx = configuration.screenHeightDp.toFloat(),
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
        val effectiveProperties = com.styleconverter.runtime.animations.KeyframeAnimationDriver
            .animate(transitionedProperties)

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
        val sizedModifier = itemModifier.then(interactionModifier).then(animatedSizeFloor).then(baseModifier).then(
            // BORDER-BOX floor: web's 50/30px minimum constrains the whole
            // card (box-sizing: border-box), so the content-box minimum
            // Compose enforces here (inside the padding-last chain) must be
            // 50/30 MINUS the padding + border bands. The previous raw
            // defaultMinSize(50.dp, 30.dp) floored the CONTENT box instead,
            // adding +2px to every default-font placeholder under
            // `padding: 8px+` (all 33 Decorated combo rows: Android canvas
            // 78/86/94/102 vs web 76/84/92/100). See
            // StyleApplier.placeholderFloorMinSize for the exact math.
            StyleApplier.placeholderFloorMinSize(
                effectiveProperties,
                applyWidthFloor = !hasExplicitWidth,
                applyHeightFloor = !hasExplicitHeight
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
            it.type in INHERITED_TEXT_PROPERTY_TYPES
        }
        // The containing block THIS component establishes for its children:
        // its resolved content box (width channel, CSS 2.1 §10.1). Unknown
        // axes stay null — resolution then leaves child % values untouched.
        val childContainingBlock = androidx.compose.runtime.remember(effectiveProperties, containingBlock) {
            com.styleconverter.runtime.core.variables.DynamicValueResolver
                .childContainingBlock(effectiveProperties, containingBlock)
        }
        val inheritanceWrappedContent: @Composable () -> Unit = {
            CompositionLocalProvider(
                LocalInheritedProperties provides inheritableForChildren,
                // Custom-property scope for descendants — element definitions
                // shadow the slot-parent chain (spec 02 resolution order).
                com.styleconverter.runtime.core.variables.LocalCssVariables provides varScope,
                // % base channel for descendants' calc()/bare-% resolution.
                com.styleconverter.runtime.core.variables.LocalContainingBlock provides childContainingBlock
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
                        // Auto-margin centering for block children is handled
                        // inside RenderComponent's self-alignment wrapper (one
                        // implementation covers tree children AND root-level
                        // standalone captures).
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
     */
    @Composable
    private fun RenderAbsoluteChild(child: IRComponent) {
        RenderComponent(child)
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
                // v2 placement contract: one union read per arriving child;
                // this flex container consumes ONLY the flex block + the
                // shared alignment claim (css-align-3 §6.4) — grid claims
                // on the same child are inert here, like `grid-area` on a
                // flex child in a browser (design §2.2 resolution rule).
                val childPlacement = com.styleconverter.runtime.core.placement
                    .ItemPlacementExtractor.extract(child.properties)
                val alignSelf = childPlacement.alignSelf
                val flexGrow = childPlacement.flex.grow
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
                // Same v2 single-union read as RenderRowContent — column
                // flex consumes only its own claim kinds.
                val childPlacement = com.styleconverter.runtime.core.placement
                    .ItemPlacementExtractor.extract(child.properties)
                val alignSelf = childPlacement.alignSelf
                val flexGrow = childPlacement.flex.grow

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
            // v2 placement contract: the child's flex claims come from
            // the single ITEM union (grow 0 / shrink 1 / basis auto are
            // the CSS-initial defaults the union carries for absent
            // fields — design §2.2 resolution rule).
            val flexClaims = com.styleconverter.runtime.core.placement
                .ItemPlacementExtractor.extract(cp).flex
            com.styleconverter.runtime.layout.flexbox.FlexSizeResolver.Item(
                // Used flex basis: flex-basis, else the main-size property,
                // else content (null → line unresolvable).
                basisPx = flexClaims.basisPx ?: mainSize,
                grow = flexClaims.grow.toDouble(),
                shrink = flexClaims.shrink.toDouble(),
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
        val textModifier = if (placeholderFillsParentWidth(properties)) {
            Modifier.fillMaxWidth().padding(4.dp).then(breakWordShim)
        } else {
            Modifier.padding(4.dp).then(breakWordShim)
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
            androidx.compose.ui.layout.Layout(
                content = {
                    Text(
                        text = annotatedText,
                        style = styledTextStyle,
                        maxLines = effectiveMaxLines,
                        overflow = effectiveOverflow,
                        softWrap = wrapConfig.softWrap,
                        onTextLayout = { layoutResult.value = it },
                        modifier = Modifier.padding(4.dp).then(emphasisModifier)
                    )
                }
            ) { measurables, constraints ->
                // Swap the axes: the text's inline axis runs along the
                // box's block axis, so its wrap width is the incoming
                // HEIGHT budget (unbounded → let it be a single line).
                val swapped = androidx.compose.ui.unit.Constraints(
                    minWidth = 0,
                    maxWidth = if (constraints.hasBoundedHeight) constraints.maxHeight else androidx.compose.ui.unit.Constraints.Infinity,
                    minHeight = 0,
                    maxHeight = if (constraints.hasBoundedWidth) constraints.maxWidth else androidx.compose.ui.unit.Constraints.Infinity
                )
                val placeable = measurables.first().measure(swapped)
                // Report the rotated footprint (width↔height swapped).
                val w = placeable.height.coerceIn(constraints.minWidth, constraints.maxWidth)
                val h = placeable.width.coerceIn(constraints.minHeight, constraints.maxHeight)
                layout(w, h) {
                    // Center-rotate: place the child so its center lands at
                    // the wrapper's center, then spin it about that center.
                    val x = (w - placeable.width) / 2
                    val y = (h - placeable.height) / 2
                    placeable.placeWithLayer(x, y) {
                        rotationZ = rotation
                    }
                }
            }
            return
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
