package com.styleconverter.runtime.effects.backdrop

import com.styleconverter.runtime.core.ir.IRComponent

/**
 * The IR property type the converter emits for CSS `backdrop-filter`
 * (irmodels/properties/effects/BackdropFilterProperty.kt; the shared
 * FilterExtractor reads the same key).
 */
private const val BACKDROP_FILTER = "BackdropFilter"

/**
 * Does this composed document declare `backdrop-filter` anywhere?
 *
 * This is the gate that decides whether a fixture pays for the second render
 * pass at all. It matters for more than performance: a document that answers
 * false leaves the coordinator DISABLED, every backdrop hook stays the
 * historical no-op, and the capture is byte-identical to the committed one.
 * The dark-stage 327-pair baseline and every non-backdrop WPT section are
 * protected by exactly this check.
 *
 * The walk covers the base property list, the conditional (selector) and media
 * property lists — a `:hover`/media-only `backdrop-filter` still needs the
 * two-pass host once its condition resolves — and every child, recursively.
 * Over-answering true is harmless (an extra pass whose sample set is empty
 * renders identically); under-answering would silently drop the feature.
 */
fun documentDeclaresBackdropFilter(roots: List<IRComponent>): Boolean =
    roots.any { componentDeclaresBackdropFilter(it) }

/** Recursive half of [documentDeclaresBackdropFilter]. */
private fun componentDeclaresBackdropFilter(component: IRComponent): Boolean {
    // Base declarations — the overwhelmingly common case, checked first.
    if (component.properties.any { it.type == BACKDROP_FILTER }) return true
    // State-conditional declarations (:hover, :focus, forced states…).
    if (component.selectors.any { sel -> sel.properties.any { it.type == BACKDROP_FILTER } }) return true
    // Media-conditional declarations (the bucket the evaluator may activate).
    if (component.media.any { m -> m.properties.any { it.type == BACKDROP_FILTER } }) return true
    // Descendants. `children` is nullable in the IR (leaf nodes omit it).
    return component.children?.any { componentDeclaresBackdropFilter(it) } == true
}
