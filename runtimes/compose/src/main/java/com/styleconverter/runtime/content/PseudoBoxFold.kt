package com.styleconverter.runtime.content

// PseudoBoxFold — wave-49 lane A2: the COMPONENT half of the ordinary-element
// generated-BOX path (the pure half is PseudoGeneratedBox).
//
// CSS 2.1 §12.1 / css-pseudo-4 §4.1 ("Generated Content Pseudo-elements:
// ::before and ::after" — a TREE-ABIDING pseudo-element, §4): a `::before`
// box is generated INSIDE the originating element as its FIRST child,
// `::after` as its LAST. Web spells exactly that — NodeRenderer.tsx emits
// the pseudo <span> as a positional child of the element (marker, before,
// text, children, after) — which is why web paints
// css-pseudo/before-as-flex-container's green box OVER the
// div's red background while both natives paint red.
//
// So the fold makes the generated box a REAL CHILD COMPONENT. Nothing else
// in the renderer has to learn about pseudo boxes: the host's own layout
// branch (block flow here, flex/grid elsewhere) places it as the first/last
// in-flow child exactly like any authored child, and StyleApplier paints its
// typed declarations through the one existing pipeline. That is also why
// this is a component REWRITE and not a wrapper: a wrapper composed around
// the host (ContentApplier.ContentWithPseudoElements) sits OUTSIDE the
// host's border box and can never cover the host's own background, which is
// precisely the divergence measured on Android.
//
// IDENTITY EVERYWHERE ELSE: every component without a `pseudos` bucket —
// which is the entire committed 327-fixture baseline corpus — gets the SAME
// instance back, so remember{} keys and frozen captures are untouched.

import com.styleconverter.runtime.PropertyTracker
import com.styleconverter.runtime.core.ir.IRComponent
import com.styleconverter.runtime.core.ir.IRLog

/** Log tag for this fold's named refusals. */
private const val FOLD_TAG = "PseudoBoxFold"

object PseudoBoxFold {

    /**
     * The component with its generated `::before` / `::after` BOXES spliced
     * into the child list, or the SAME instance when nothing is claimed.
     *
     * Called once per component at the renderer's single entry, right after
     * `ContentsUnboxing.resolve` — so the fold sees the final composed child
     * list (a `display: contents` child is already spliced out) and every
     * downstream path (block flow, flex item collection, grid placement,
     * the positioned overlay walk) sees the generated box as an ordinary
     * child with no further plumbing.
     */
    fun resolve(component: IRComponent): IRComponent {
        // The overwhelmingly common path: no bucket at all.
        val pseudos = component.pseudos ?: return component
        // `meta.runs` is AUTHORITATIVE over the sibling walk (spec 03 §4.1):
        // the renderer paints the listed entries in run order, so a child
        // the run list cannot name would land at an unspecified slot.
        // Refuse and name it rather than guess — same conservatism as the
        // iOS text fold's runs guard.
        if (component.runs != null) {
            if (pseudos["before"] != null || pseudos["after"] != null) {
                IRLog.warn(FOLD_TAG,
                    "component carries meta.runs — generated pseudo box not folded (runs own the content slot)")
                PropertyTracker.markUnhandled("PseudoBox::runs")
            }
            return component
        }
        // The SAME claim function PseudoBucketExtractor consults, so a
        // bucket taken here can never also render through the inline path.
        val before = PseudoGeneratedBox.claim(
            pseudos["before"] as? kotlinx.serialization.json.JsonObject, "before", component.role)
        val after = PseudoGeneratedBox.claim(
            pseudos["after"] as? kotlinx.serialization.json.JsonObject, "after", component.role)
        // Nothing claimed → identity, so the inline path stays in charge.
        if (before == null && after == null) return component
        // CSS 2.1 §12.1 order: ::before, the element's own children, ::after.
        val children = buildList {
            before?.let { add(boxComponent(component, "before", it)) }
            addAll(component.children.orEmpty())
            after?.let { add(boxComponent(component, "after", it)) }
        }
        return component.copy(children = children)
    }

    /**
     * One generated box as a synthetic child component.
     *
     * `slot` is deliberately null: it is COMPOSER input (spec 03 — only a
     * composer reads it, and composition has already happened by the time
     * this fold runs), so setting it would claim a wire identity this box
     * never had. `pseudos` is null too — css-pseudo-4 §1 notes that
     * "pseudo-elements cannot be chained together unless explicitly
     * allowed", and `::before::before` is not among the allowed chains, so
     * a generated box carries no `::before`/`::after` bucket of its own.
     * That also makes the fold structurally non-recursive.
     */
    private fun boxComponent(
        host: IRComponent,
        role: String,
        claim: PseudoGeneratedBox.Claim,
    ): IRComponent = IRComponent(
        // Stable, collision-free identity: the CSS selector spelling of the
        // box. Compose keys child composition by it, so it must not move
        // between recompositions of the same host.
        id = "${host.id}::$role",
        name = "${host.name}::$role",
        properties = claim.properties,
        // The literal/baked content string — null rather than "" so the
        // renderer's has-text tests read the same as an authored empty leaf
        // (`content: ""` boxes paint their background only).
        _text = claim.text.takeIf { it.isNotEmpty() },
    )
}
