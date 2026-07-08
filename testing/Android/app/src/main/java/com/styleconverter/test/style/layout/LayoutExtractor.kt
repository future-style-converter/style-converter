package com.styleconverter.test.style.layout

import com.styleconverter.test.style.PropertyRegistry
import kotlinx.serialization.json.JsonElement

/**
 * Phase 7 step 1 scaffold extractor for the CSS layout family.
 *
 * Responsibilities:
 *   1. Claim every layout IR property name against [PropertyRegistry] so the
 *      legacy [com.styleconverter.test.style.StyleApplier] dispatch switch
 *      defers to the style-engine path instead of double-applying.
 *   2. Expose a single [extractLayoutConfig] entrypoint that returns a fully
 *      resolved [LayoutConfig]. In step 1 every field resolves to null — the
 *      real extraction logic arrives in Phase 7 steps 2–5.
 *
 * Precedent: [com.styleconverter.test.style.typography.TypographyExtractor] —
 * same init-block registration + single `extract*Config` entrypoint shape.
 *
 * Owner strings match the IR sub-folder name for introspection-friendly
 * coverage audits via [PropertyRegistry.allRegistered]. The four sub-folders
 * are: layout/flexbox, layout/grid, layout/position, layout/advanced; plus
 * the four layout root properties (Clear / Float / Overlay / ReadingFlow)
 * registered under "layout".
 */
object LayoutExtractor {

    init {
        // ----- layout/flexbox -----
        // See src/main/kotlin/app/irmodels/properties/layout/flexbox/*.kt for
        // the source-of-truth property files. Each name below maps 1:1 to an
        // IRProperty.type emitted by the CSS parser.
        PropertyRegistry.migrated(
            "Display",
            "FlexDirection", "FlexWrap",
            "FlexGrow", "FlexShrink", "FlexBasis",
            "JustifyContent", "AlignItems", "AlignContent",
            "AlignSelf", "Order",
            // Legacy box-orient lives under flexbox in the IR tree because it
            // predates the flex spec but shares axis semantics.
            "BoxOrient",
            owner = "layout/flexbox"
        )

        // ----- layout/grid -----
        // Mirrors src/main/kotlin/app/irmodels/properties/layout/grid/*.kt.
        // GridTemplate (shorthand) + GridAutoTrack (CSS Grid 3 proposal) are
        // parse-only at this stage but the tripwire test requires them listed.
        PropertyRegistry.migrated(
            "GridTemplateColumns", "GridTemplateRows", "GridTemplateAreas",
            "GridTemplate",
            "GridAutoColumns", "GridAutoRows", "GridAutoFlow",
            "GridAutoTrack",
            "GridArea",
            "GridColumnStart", "GridColumnEnd",
            "GridRowStart", "GridRowEnd",
            "JustifyItems", "JustifySelf",
            "AlignTracks", "JustifyTracks",
            "MasonryAutoFlow",
            owner = "layout/grid"
        )

        // ----- layout/position -----
        // Mirrors src/main/kotlin/app/irmodels/properties/layout/position/.
        // Logical insets (InsetBlock*, InsetInline*) register alongside
        // physical top/right/bottom/left; step 4 will fold them into a
        // single InsetRect via LayoutDirection.
        PropertyRegistry.migrated(
            "Position",
            "Top", "Right", "Bottom", "Left",
            "InsetBlockStart", "InsetBlockEnd",
            "InsetInlineStart", "InsetInlineEnd",
            "ZIndex",
            owner = "layout/position"
        )

        // ----- layout/advanced -----
        // CSS Anchor Positioning (2024) + CSS Motion Path + Position Try.
        // All parse-only on Compose today — registering so the legacy switch
        // doesn't silently claim them while the style-engine catches up.
        PropertyRegistry.migrated(
            "AnchorName", "AnchorScope",
            "InsetArea",
            "OffsetPath", "OffsetDistance", "OffsetAnchor", "OffsetPosition",
            "OffsetRotate", "Offset",
            "PositionAnchor", "PositionArea",
            "PositionFallback", "PositionTry",
            "PositionTryFallbacks", "PositionTryOptions",
            "PositionTryOrder",
            "PositionVisibility",
            owner = "layout/advanced"
        )

        // ----- layout root -----
        // Four top-level IR files directly under layout/: Clear / Float /
        // Overlay / ReadingFlow. FloatExtractor already handles Clear+Float
        // at runtime but did not previously register with PropertyRegistry —
        // we claim them here so the tripwire test stays green.
        PropertyRegistry.migrated(
            "Clear", "Float",
            "Overlay",
            "ReadingFlow",
            owner = "layout"
        )
    }

    /**
     * Extract a fully-resolved [LayoutConfig] from the IR property stream.
     *
     * Step 1 stub: returns [LayoutConfig.Empty] unconditionally. Subsequent
     * Phase 7 steps replace the stub with real per-property extraction. The
     * method signature is locked in now so the facade and ComponentRenderer
     * hook don't churn between steps.
     *
     * @param properties (propertyType, data) pairs from IRComponent.properties.
     * @return LayoutConfig with all fields null in step 1.
     */
    fun extractLayoutConfig(properties: List<Pair<String, JsonElement?>>): LayoutConfig {
        // Phase 7b step 2: delegate to the flexbox sub-extractor. The flexbox
        // extractor returns a LayoutConfig with ONLY its owned fields
        // populated; all grid/position/advanced/root slots stay null. When
        // later steps ship their own sub-extractors (GridExtractor,
        // PositionExtractor) they'll merge into this same aggregate via a
        // copy-based fold — see the TODO below.
        val flexbox = com.styleconverter.test.style.layout.flexbox.FlexboxExtractor
            .extract(properties)

        // Phase 7b — fold grid + position sub-extractors into the flexbox
        // result. Each sub-extractor returns only its own fields populated;
        // we merge by copying non-null fields onto the aggregate. The
        // advanced/root extractors will join via the same pattern in a
        // later step.
        val g = com.styleconverter.test.style.layout.grid.GridLayoutExtractor.extract(properties)
        val p = com.styleconverter.test.style.layout.position.PositionLayoutExtractor.extract(properties)
        return flexbox.copy(
            gridTemplateColumns = g.templateColumns ?: flexbox.gridTemplateColumns,
            gridTemplateRows = g.templateRows ?: flexbox.gridTemplateRows,
            gridTemplateAreas = g.templateAreas ?: flexbox.gridTemplateAreas,
            gridAutoColumns = g.autoColumns ?: flexbox.gridAutoColumns,
            gridAutoRows = g.autoRows ?: flexbox.gridAutoRows,
            gridAutoFlow = g.autoFlow ?: flexbox.gridAutoFlow,
            gridArea = g.gridArea ?: flexbox.gridArea,
            gridColumn = g.gridColumn ?: flexbox.gridColumn,
            gridRow = g.gridRow ?: flexbox.gridRow,
            // justifyItems/justifySelf are shared with flexbox's JustifyContent
            // alignment set; if flexbox already claimed them prefer that so a
            // container that happens to set both gets the flex semantics. In
            // practice only one code path populates each per component.
            justifyItems = flexbox.justifyItems ?: g.justifyItems,
            justifySelf = flexbox.justifySelf ?: g.justifySelf,
            position = p.position ?: flexbox.position,
            inset = p.inset ?: flexbox.inset,
            zIndex = p.zIndex ?: flexbox.zIndex,
        )
    }
}
