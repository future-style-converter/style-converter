// Wave 35 lane B3 — css-multicol-1 §6.2 DESCENDANT spanner promotion.
//
// §6.2 makes `column-span: all` apply to any in-flow DESCENDANT of the
// multicol container, not just a direct child: the spanner is pulled out of
// its ancestors' block flow and laid out as a full-width block of the
// MULTICOL container, and its ancestors fragment around it. The wave-21
// classifier (MulticolSpannerFlow.rolesFor) only ever looked at DIRECT
// children, so a spanner one level down stayed an ordinary column item.
//
// The measured Android defect (wave-34 gate, css-multicol
// ancestor-toggle-spanner-001): the wrapper carries the browser's USED
// height — 0px, precisely BECAUSE the browser hoisted the spanner out of
// it — and Compose's `Modifier.height(0.dp)` COERCES its subtree's height
// constraints to 0 (SizeNode enforces the incoming envelope, unlike CSS
// `overflow: visible`). The 100×100 green spanner therefore measured 0 tall
// and the red parent showed through unpainted: the Android capture is pure
// RED where ref/web/iOS are pure GREEN. iOS renders it correctly only
// because SwiftUI's frame() does not clamp its child that way, so this file
// is an ANDROID-side compensation and deliberately has NO iOS twin — the
// pure geometry twin (MulticolSpannerFlow / FragmentGeometry) is untouched
// and stays byte-parallel with runtimes/swiftui/.../MulticolSpannerFlow.swift.
//
// Shape: a PURE IR rewrite (JVM-pinned, no Compose imports) applied at the
// renderer's MULTI_COLUMN branch, exactly like ContentsUnboxing's
// `display: contents` splice. The wrapper is NOT removed from the tree —
// removing a `position: relative` box would desynchronise the composition's
// positioned-ancestor channel from CanvasRootHoist's pure walk over the RAW
// document (a box intercepted in flow with no overlay slot is dropped
// outright). Instead the wrapper INHERITS the spanner's own `ColumnSpan`
// property and sheds the sizing declarations that clamp it, so the existing
// §6.2 machinery does the rest.
package com.styleconverter.runtime.columns

// The IR types the rewrite operates on.
import com.styleconverter.runtime.core.ir.IRComponent
import com.styleconverter.runtime.core.ir.IRProperty
// The shared keyword / colour / length decoders — the same readers the
// extractors ride, so this gate can never disagree with what actually paints.
import com.styleconverter.runtime.core.types.ValueExtractors

object MulticolDescendantSpanner {

    /** Sizing declarations dropped from a promoted wrapper (see [promote]). */
    private val SIZING_TYPES = setOf(
        // Compose coerces each of these into the child's constraint envelope;
        // the browser's used values describe the POST-hoist wrapper (0 tall,
        // column-wide), which is exactly the geometry that must not clamp the
        // spanner once we render the spanner INSIDE the wrapper.
        "Width", "Height", "MinWidth", "MinHeight", "MaxWidth", "MaxHeight",
        "InlineSize", "BlockSize", "MinInlineSize", "MinBlockSize",
        "MaxInlineSize", "MaxBlockSize", "AspectRatio",
    )

    /**
     * Declarations that make an ancestor an INDEPENDENT fragmentation /
     * containing-block root, disqualifying a `column-span: all` descendant
     * below it (css-multicol-1 §6.2: the spanner's nearest multicol ancestor
     * must be THIS container). This is the exact axis the WPT pair
     * ancestor-toggle-spanner-001 / -002 toggles: -001 REMOVES the ancestor's
     * transform (spanner valid → full-width green square), -002 ADDS one
     * (spanner invalid → the box stays a column item and fragments). Both
     * captures carry the post-script state, so the presence of the property
     * IS the discriminator.
     */
    private val SPAN_BLOCKING_TYPES = setOf(
        // Transform/perspective make the element a containing block for
        // fixed/abspos descendants and an independent formatting context.
        "Transform", "Perspective", "Rotate", "Scale", "Translate",
        // filter / backdrop-filter / will-change / contain do the same.
        "Filter", "BackdropFilter", "WillChange", "Contain",
        // A nested multicol owns its OWN spanners — never this container's.
        "ColumnCount", "ColumnWidth", "Columns",
    )

    /**
     * Rewrite one multicol container's children so that every in-flow child
     * hosting a hoistable `column-span: all` descendant is itself classified
     * (and measured) as a spanner. IDENTITY — the same instance — when no
     * child qualifies, which is every fixture in the frozen corpus except the
     * descendant-spanner family, so remember{} keys and baselines are stable.
     */
    fun resolve(component: IRComponent): IRComponent {
        // Childless containers (and leaf multicol boxes) have nothing to scan.
        val children = component.children ?: return component
        // Fast path: no child qualifies → return the SAME instance.
        if (children.none { promotes(it) }) return component
        // Rewrite only the qualifying children; siblings pass through by
        // reference so their own remember{} identity is untouched.
        return component.copy(
            children = children.map { if (promotes(it)) promote(it) else it },
        )
    }

    /**
     * The promotion gate. Deliberately NARROW — every clause below is a
     * property the measured defect actually has, so a container that merely
     * resembles the shape keeps the frozen render.
     */
    internal fun promotes(child: IRComponent): Boolean {
        // Already a spanner → the wave-21 classifier handles it; no rewrite.
        if (spanKeyword(child) == "ALL") return false
        // In-flow only (css-position-3 §2.1: an out-of-flow box takes no part
        // in the column flow at all, so `column-span` under it is moot).
        val position = keyword(child, "Position") ?: "STATIC"
        if (position != "STATIC" && position != "RELATIVE") return false
        // A float is out of the block flow the spanner would interrupt.
        val float = keyword(child, "Float")
        if (float != null && float != "NONE") return false
        // §6.2 disqualifiers (see SPAN_BLOCKING_TYPES) — the -002 gate.
        if (child.properties.any { it.type in SPAN_BLOCKING_TYPES }) return false
        // A clipping wrapper would cut the spanner it is supposed to release;
        // only the `visible` initial (or no declaration) can host one.
        if (!overflowVisible(child)) return false
        // Dynamic buckets / pseudo payloads could reintroduce ink or a
        // blocking declaration the static rewrite cannot follow — same
        // conservatism as ContentsUnboxing.isUnboxable.
        if (child.pseudos != null || child.selectors.isNotEmpty() || child.media.isNotEmpty()) return false
        // INK-FREE: the wrapper's own box must paint nothing, because after
        // promotion it is measured at CONTAINER width instead of column
        // width — a background or border would visibly widen.
        if (!inkFree(child)) return false
        // The browser's used block-size is the honest post-hoist signal: a
        // wrapper whose spanner really was hoisted collapses to 0 (or was
        // never given a height). A wrapper with a real height kept its
        // content in flow, so promoting it would be a fabrication.
        val declaredHeight = child.properties.lastOrNull { it.type == "Height" }
        if (declaredHeight != null &&
            (ValueExtractors.extractDp(declaredHeight.data)?.value ?: -1f) != 0f
        ) return false
        // Exactly ONE in-flow child, and it must be the spanner. A wrapper
        // holding a spanner PLUS other column content would need real
        // fragmentation around the spanner (css-multicol-1 §6.3), which this
        // rewrite does not model — such wrappers keep the frozen render.
        val inFlow = (child.children ?: emptyList()).filter { inFlowChild(it) }
        return inFlow.size == 1 && spanKeyword(inFlow[0]) == "ALL"
    }

    /**
     * Build the promoted wrapper: the spanner descendant's OWN `ColumnSpan`
     * property is appended (reusing the wire instance, so no value shape is
     * re-synthesised here) and the clamping sizing declarations are dropped.
     * Everything else — position, z-index, margins, children — is preserved
     * byte-for-byte, so the wrapper still occupies the same place in the
     * positioned-ancestor chain that CanvasRootHoist's raw-tree walk sees.
     */
    private fun promote(child: IRComponent): IRComponent {
        // The descendant's ColumnSpan envelope, reused verbatim.
        val span = (child.children ?: emptyList())
            .firstOrNull { inFlowChild(it) }
            ?.properties?.lastOrNull { it.type == "ColumnSpan" }
            ?: return child
        // Shed the sizing declarations, then claim the span.
        val properties = child.properties.filterNot { it.type in SIZING_TYPES } + span
        return child.copy(properties = properties)
    }

    /** `column-span` keyword of a component ("ALL" / "NONE" / null). */
    private fun spanKeyword(component: IRComponent): String? = keyword(component, "ColumnSpan")

    /** Last-wins keyword read of one property type, uppercased. */
    private fun keyword(component: IRComponent, type: String): String? =
        component.properties.lastOrNull { it.type == type }
            ?.let { ValueExtractors.extractKeyword(it.data)?.uppercase() }

    /** In flow ⇔ not absolutely/fixed positioned (css-position-3 §2.1). */
    private fun inFlowChild(component: IRComponent): Boolean {
        val position = keyword(component, "Position") ?: "STATIC"
        return position != "ABSOLUTE" && position != "FIXED"
    }

    /** True when neither overflow axis clips (absent = the `visible` initial). */
    private fun overflowVisible(component: IRComponent): Boolean =
        listOf("Overflow", "OverflowX", "OverflowY").all { type ->
            keyword(component, type)?.let { it == "VISIBLE" } ?: true
        }

    /**
     * Does this box paint nothing of its own? Only a fully transparent
     * background, zero-width borders, no shadow/outline and no text run
     * qualify — anything else would move visibly when the box is re-measured
     * at container width.
     */
    private fun inkFree(component: IRComponent): Boolean {
        // A text run (collapsed or raw) is ink the wrapper owns.
        if (!component._text.isNullOrEmpty() || component.runs != null) return false
        if (component.markerText != null) return false
        for (p in component.properties) when {
            // Any background image/gradient paints.
            p.type == "BackgroundImage" -> return false
            // Shadows and outlines paint outside the box too.
            p.type == "BoxShadow" -> return false
            p.type == "OutlineStyle" ->
                if (ValueExtractors.extractKeyword(p.data)?.uppercase() != "NONE") return false
            // A non-transparent background colour paints.
            p.type == "BackgroundColor" ->
                if ((ValueExtractors.extractColor(p.data)?.alpha ?: 0f) > 0f) return false
            // A non-zero border width paints (its colour is irrelevant then).
            p.type.startsWith("Border") && p.type.endsWith("Width") ->
                if ((ValueExtractors.extractDp(p.data)?.value ?: 0f) > 0f) return false
        }
        return true
    }

    /** The property list a promoted wrapper carries — exposed for the pins. */
    internal fun promotedProperties(child: IRComponent): List<IRProperty> = promote(child).properties
}
