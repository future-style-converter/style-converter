package com.styleconverter.runtime.color

import com.styleconverter.runtime.core.ir.IRComponent
import com.styleconverter.runtime.core.ir.IRProperty
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * `background-color: inherit` (css-cascade-5 §7.3.2) — wave-49 lane A5.
 *
 * ## Why a TREE pass and not an extractor
 *
 * `background-color` is a NON-inherited property, so the only way a child
 * takes its parent's value is the explicit `inherit` keyword, and §7.3.2
 * defines that as "the COMPUTED value of the property on the parent". An
 * extractor sees one component's declaration list and has no parent, so the
 * substitution has to happen where the parent link exists — here, on the
 * composed tree, once, before styling.
 *
 * It must NOT be done by adding `BackgroundColor` to
 * `ComponentRenderer.INHERITED_PROPERTY_TYPES`: that channel means "inherits
 * by default", which would give every child its parent's background and
 * repaint most of the corpus.
 *
 * ## Why the substituted value stays UNRESOLVED
 *
 * The inherited thing is the parent's *computed* value, and css-color-4 §6.4
 * says `currentcolor` "computes to itself" — so a parent's
 * `background-color: currentcolor` arrives at the child still a keyword and
 * re-resolves against the CHILD's own `color`. Copying the parent's wire
 * value verbatim (rather than a resolved colour) is what preserves that, and
 * [CurrentColorBackground] then does the per-element §6.4 resolution. Same
 * for a `color-mix()` containing `currentcolor`.
 *
 * Measured in Chrome 151 on the exact three-div nesting of
 * `css-color/currentcolor-002` — outer `color:red; background:currentcolor`,
 * middle `background:inherit`, inner `color:green; background:inherit` —
 * computed backgrounds are rgb(255,0,0) / rgb(255,0,0) / rgb(0,128,0): the
 * inner box is GREEN, which is why the reference is a plain green square
 * while both natives painted nothing at all on the wave-48 gate (iOS ssim
 * 0.8727, Android 0.8721).
 *
 * Byte-parallel twin of `StyleEngine/color/BackgroundColorInheritance.swift`.
 */
object BackgroundColorInheritance {

    /** The IR type this pass rewrites. */
    private const val TYPE = "BackgroundColor"

    /**
     * Resolve `inherit` through a whole composed tree.
     *
     * Roots have no parent, so an `inherit` on a root takes the initial
     * value (`transparent`, css-backgrounds-3 §2.2) — expressed by dropping
     * the declaration, which is exactly what "no BackgroundColor property"
     * already means to every applier.
     */
    fun resolve(root: IRComponent): IRComponent = resolve(root, inherited = null)

    /** Convenience for a forest (the harness renders roots one at a time). */
    fun resolveAll(roots: List<IRComponent>): List<IRComponent> = roots.map { resolve(it) }

    /**
     * @param inherited the parent's COMPUTED `background-color` wire value —
     *   null when the parent declared none (its computed value is then the
     *   initial `transparent`, which needs no declaration).
     */
    private fun resolve(component: IRComponent, inherited: JsonElement?): IRComponent {
        // The cascade already ran in the converter, so the LAST declaration
        // of the type is the specified one (the same last-wins rule every
        // extractor in this runtime applies to its own property list).
        val declared = component.properties.lastOrNull { it.type == TYPE }
        val declaredIsInherit = declared != null && isInheritKeyword(declared.data)

        // This element's computed value: the parent's when `inherit`, its own
        // declaration otherwise, and null (initial) when it declares none.
        val computed: JsonElement? = when {
            declaredIsInherit -> inherited
            declared != null -> declared.data
            else -> null
        }

        // Rewrite only when the keyword is actually present — every other
        // component is returned by identity, so this pass cannot perturb a
        // document that does not use `inherit`.
        val self = if (!declaredIsInherit) component else {
            // Drop EVERY BackgroundColor entry and re-add the resolved one at
            // the end. Dropping all of them keeps last-wins correct: an
            // earlier real colour must not resurface when the winning
            // `inherit` resolves to "nothing" (the root case).
            val kept = component.properties.filterNot { it.type == TYPE }
            component.copy(
                properties = if (computed == null) kept
                else kept + IRProperty(TYPE, computed),
            )
        }

        // Recurse with THIS element's computed value as the children's
        // inheritance source. `children` is null (not empty) for leaves — the
        // absent-vs-empty discipline the composer keeps — so preserve that.
        val kids = self.children ?: return self
        // Identity short-circuit: rebuild the children array ONLY when some
        // descendant actually changed. This is what makes the pass free (and
        // provably inert) for the ~99.8% of corpus documents that never write
        // `background-color: inherit` — ComponentHost re-runs it per root, and
        // `remember` keys on the component, so a fresh copy every frame would
        // be pure waste. The Swift twin gets the same guarantee structurally:
        // IRComponent is a value type there, so an untouched subtree is
        // already indistinguishable from the original.
        var changed = false
        val mapped = ArrayList<IRComponent>(kids.size)
        for (k in kids) {
            val r = resolve(k, computed)
            if (r !== k) changed = true
            mapped.add(r)
        }
        return if (changed) self.copy(children = mapped) else self
    }

    /**
     * True when the wire value is the CSS-wide keyword `inherit`.
     *
     * The converter ships keywords it cannot pre-resolve in the same
     * srgb-less envelope it uses for `currentColor` — `{"original":"inherit"}`
     * (verbatim in `tools/titan/runs/wave48-final/sections/css-color/
     * per-test-ir/wpt__css-color__currentcolor-002.json` and both
     * `color-mix-currentcolor-00{1,2}.json`); a bare `"inherit"` string is
     * accepted for symmetry with the other keyword readers. Deliberately
     * narrow: `initial` / `unset` / `revert` all compute to the initial value
     * on a non-inherited property, which is already what no declaration
     * produces, so claiming them here would widen the surface for no effect.
     */
    private fun isInheritKeyword(data: JsonElement?): Boolean = when (data) {
        is JsonObject ->
            (data["original"] as? JsonPrimitive)?.contentOrNull
                ?.equals("inherit", ignoreCase = true) == true
        // CSS keywords are ASCII case-insensitive (css-values-4 §4.1).
        is JsonPrimitive -> data.contentOrNull?.equals("inherit", ignoreCase = true) == true
        else -> false
    }
}
