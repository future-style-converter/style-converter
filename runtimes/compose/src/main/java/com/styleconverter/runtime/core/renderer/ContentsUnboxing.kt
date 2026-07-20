package com.styleconverter.runtime.core.renderer

// Wave 18 (RC6) — `display: contents` unboxing, css-display-3 §2.5: the
// element itself generates NO box; its contents (text run + children)
// render as if the element were replaced by them in its parent. Before
// this file neither native unboxed: the wrapper became a real block box,
// so (a) its border/background/margin painted (display-contents-button /
// -details drew spurious 10px red borders on iOS; -block-002 applied a
// giant margin on both natives) and (b) in a grid/flex parent the WRAPPER
// became the item, so the container's justify-items/align-items never
// reached the grandchildren (display-contents-alignment-002: Android
// left-flushed the square the ref centers).
//
// The unboxing is a PURE IR tree rewrite applied at the renderer's entry
// (both natives, parallel files — iOS twin: Renderer/ContentsUnboxing.swift)
// so EVERY child-collection path (block flow, positioned overlay, flex,
// grid item collection) sees the spliced children without per-branch code.

// IR model — the rewrite is pure over the IR (JVM-pinned by
// ContentsUnboxingTest, no Robolectric).
import com.styleconverter.runtime.core.ir.IRComponent
import com.styleconverter.runtime.core.ir.IRProperty
import com.styleconverter.runtime.core.types.ValueExtractors
import com.styleconverter.runtime.layout.position.PositionExtractor
import com.styleconverter.runtime.layout.position.PositionType

object ContentsUnboxing {

    /** The wire keyword the CSS reader emits for `display: contents`. */
    private const val CONTENTS_KEYWORD = "CONTENTS"

    /** True when the BASE declarations compute `display: contents`. */
    internal fun isDisplayContents(properties: List<IRProperty>): Boolean =
        properties.lastOrNull { it.type == "Display" }
            // Same keyword decoder the display extractors use — one wire
            // reader, so unboxing can never disagree with layout mapping.
            ?.let { ValueExtractors.extractKeyword(it.data)?.uppercase() == CONTENTS_KEYWORD }
            ?: false

    /**
     * The unboxing gate (pin C1/C3). A component unboxes only when ALL of:
     *  - its base Display computes to `contents`;
     *  - it is NOT positioned out-of-flow and NOT floated — css-display-3
     *    §2.7 blockifies the display of absolutely-positioned and floated
     *    elements, so `contents` never survives on them;
     *  - it carries NO ::before/::after payload — a contents element's
     *    pseudo-elements DO render (treated as its first/last children,
     *    css-display-3 §2.5), and our pseudo machinery hangs off the
     *    component box; such elements keep the legacy block-wrapper
     *    approximation (documented TODO, pinned: the four currently-green
     *    display-contents-before-after tests stay byte-identical);
     *  - it carries NO selector/media buckets — a bucket could flip
     *    `display` at runtime, and the static rewrite cannot follow
     *    (same conservatism as CanvasRootHoist's base-declaration rule).
     */
    internal fun isUnboxable(component: IRComponent): Boolean {
        if (!isDisplayContents(component.properties)) return false
        // §2.7 blockification: out-of-flow boxes never stay `contents`.
        // Deliberately WIDER than the spec here: ANY non-static position
        // keeps the box. Spec-wise a `position: relative` contents element
        // still unboxes (the offset has no box to move), but this engine's
        // positioned-ancestor threading (CanvasRootHoist's walk mirrors
        // CSS 2.1 §10.1 over the ORIGINAL tree) would then disagree with
        // the composition (which sees the stripped tree) about whether an
        // absolute descendant has a positioned ancestor — and a box with
        // no overlay slot is dropped outright. Keeping the wrapper is the
        // safe, honest approximation (documented, pinned).
        val position = PositionExtractor
            .extractPositionConfig(component.properties.map { it.type to it.data }).type
        if (position != PositionType.STATIC) return false
        // Float blockifies too (§2.7); any non-none float keeps the box.
        val floated = component.properties.lastOrNull { it.type == "Float" }
            ?.let { ValueExtractors.extractKeyword(it.data)?.uppercase() }
        if (floated != null && floated != "NONE") return false
        // Pseudo payloads render through the component box — keep it.
        if (component.pseudos != null) return false
        // Dynamic buckets could flip display — conservative keep.
        return component.selectors.isEmpty() && component.media.isEmpty()
    }

    /**
     * The inheritable subset of a contents element's declarations —
     * css-display-3 §2.5: `display: contents` removes the BOX, not the
     * ELEMENT, so inheritance still flows through it (its color/font
     * reach the spliced children). Everything non-inherited had only the
     * box to apply to and is dropped (border, background, size, margin,
     * padding, Display itself, `All`, …).
     */
    private fun inheritableSubset(properties: List<IRProperty>): List<IRProperty> =
        properties.filter { it.type in ComponentRenderer.INHERITED_PROPERTY_TYPES }

    /**
     * Splice one unboxable child into its replacement sequence (pin C1/C2):
     *  - a non-empty text run becomes a synthetic carrier component (the
     *    contents element's text is a real text node the browser keeps —
     *    display-contents-button's "PASS") holding ONLY the inheritable
     *    declarations, so it paints unboxed text with the element's
     *    inherited styling and nothing else;
     *  - then the element's own children, recursively unboxed (contents
     *    inside contents flattens — the -details chain), each with the
     *    element's inheritable declarations merged UNDER its own (the
     *    tree-inheritance hop the splice would otherwise skip) and the
     *    element's custom-property scope merged under its own (shadowing
     *    order preserved, css-variables-1 §2).
     */
    private fun splice(child: IRComponent): List<IRComponent> = buildList {
        val inherited = inheritableSubset(child.properties)
        // The text run carrier — id-suffixed so debug output stays traceable.
        if (!child._text.isNullOrEmpty()) {
            add(IRComponent(
                id = "${child.id}::contents-text",
                name = child.name,
                properties = inherited,
                _text = child._text,
            ))
        }
        // Grandchildren become the parent's direct children (THE grid/flex
        // item fix: css-display-3 §2.5 — they participate in the grand-
        // parent's formatting context as if the wrapper never existed).
        unboxChildren(child.children)?.forEach { grandchild ->
            add(IRComponent(
                id = grandchild.id,
                name = grandchild.name,
                // Grandchild declarations win; the wrapper's inheritable
                // set fills the gaps (mergeInherited's documented order).
                properties = ComponentRenderer.mergeInherited(grandchild.properties, inherited),
                selectors = grandchild.selectors,
                media = grandchild.media,
                children = grandchild.children,
                _text = grandchild._text,
                _tag = grandchild._tag,
                slot = grandchild.slot,
                pseudos = grandchild.pseudos,
                role = grandchild.role,
                // Wrapper scope under the grandchild's own (element wins).
                variables = mergeVariables(child.variables, grandchild.variables),
            ))
        }
    }

    /** Nearest-wins merge of two custom-property scopes (own shadows outer). */
    private fun mergeVariables(
        outer: Map<String, String>?,
        own: Map<String, String>?,
    ): Map<String, String>? = when {
        outer.isNullOrEmpty() -> own
        own.isNullOrEmpty() -> outer
        else -> outer + own
    }

    /**
     * Rewrite a children list, replacing every unboxable `contents` child
     * with its spliced sequence. IDENTITY (the same list instance) when no
     * child unboxes — the frozen-baseline byte-stability rule. An empty
     * result normalizes to null: the decode contract says children is
     * null, never [], when empty (mirrors iOS FixedHoist's rule), so a
     * container whose only child was an empty contents wrapper renders
     * exactly like a wire-decoded childless leaf.
     */
    internal fun unboxChildren(children: List<IRComponent>?): List<IRComponent>? {
        if (children.isNullOrEmpty()) return children
        // Fast path: no contents child anywhere at this level → identity.
        if (children.none { isUnboxable(it) }) return children
        val out = children.flatMap { child ->
            if (isUnboxable(child)) splice(child) else listOf(child)
        }
        return out.ifEmpty { null }
    }

    /**
     * The renderer entry (pin C4): resolve a component about to render.
     *  - An unboxable component ITSELF (a document ROOT — the parent-side
     *    splice never sees roots) strips to its inheritable declarations +
     *    text + (spliced) children: an undecorated block pass-through.
     *    The residual block box is a documented approximation — at the
     *    canvas root the extra block wrapper is visually inert (block in
     *    block flow, no decoration to paint), while a true splice has no
     *    parent list to splice into.
     *  - Any other component gets its CHILDREN spliced (all layout paths
     *    downstream — block, positioned, flex, grid — consume the result).
     * IDENTITY when nothing changes, so remember{} keys and the whole
     * contents-free corpus stay byte-stable.
     */
    internal fun resolve(component: IRComponent): IRComponent {
        val splicedChildren = unboxChildren(component.children)
        return if (isUnboxable(component)) {
            // Self-strip: no box-generating declarations survive.
            component.copy(
                properties = inheritableSubset(component.properties),
                children = splicedChildren,
            )
        } else if (splicedChildren !== component.children) {
            // Children-only rewrite.
            component.copy(children = splicedChildren)
        } else {
            // Identity — the common case.
            component
        }
    }
}
