package com.styleconverter.runtime.transforms

// Wave 52 (lane L4, native-near-misses T7) — `transform: inherit`.
//
// css-cascade-4 §7.3.2: the `inherit` keyword makes an element's computed
// value for a property EQUAL to its parent's computed value for that same
// property. `transform` is not an inherited property, so this only happens
// when an author writes the keyword — and then it compounds: the child paints
// its own copy of the parent's matrix INSIDE the parent's already-transformed
// coordinate space.
//
// THE DEFECT (wave51-fix, css-transforms/css-transform-inherit-scale android
// f 0.9965): a 50×50 `.parent` with `transform: scale(2)` holds a 50×50
// `.child` with `transform: inherit` over a 200×200 red FAIL square the green
// child must cover — 50 × 2 (own, inherited) × 2 (ancestor) = 200 px. The
// Android capture paints a 100×100 green square centred on the red: exactly
// one factor of two, the child's own (inherited) scale dropped. iOS resolves
// the same wire through an ambient channel (StyleEngine/transforms/
// TransformInheritance.swift, wave 49) and passes at 0.9974.
//
// WHY THE WIRE, NOT THE FUNCTION LIST. `TransformExtractor` is a pure
// function of ONE element's property list (the IR v2 contract) and is reached
// from `StyleApplier.extractConfig`, which cannot read a CompositionLocal;
// the only composable that sees both the parent and the child is
// `ComponentRenderer.RenderComponent`. So the channel carries the parent's
// COMPUTED `Transform` wire (the JSON the parent itself rendered — resolved,
// if the parent inherited too) and the renderer substitutes it into the
// child's property list BEFORE extraction, the same way it already
// substitutes the inherited `Color` for `currentColor` (css-color-4 §6.4).
// Every extractor downstream then sees a plain `{"type":"functions"}` value:
// the transform, the transformed-containing-block decision and the backface
// rule all agree on the list the element actually paints.
//
// THE RENDERER SEAM (tools/titan/results/wave52-small-fixes/seam-1.patch) is
// this object's ONLY live caller: until it is applied nothing calls [resolve]
// and the extractor's `inherit` branch keeps refusing the keyword with its
// PropertyTracker breadcrumb exactly as before wave 52. The frozen behaviour
// is the fallback, not the target.
//
// UNPROVIDED IS NOT `none` (wave-52 skeptic should-fix 2). The local's
// default is Kotlin `null` = "no RenderComponent provided the channel on this
// path" (a mis-merged seam, or a render route outside RenderComponent), and
// [resolve] then LEAVES the keyword in place so the extractor's breadcrumb
// fires — a mis-merge is loud, never a silently dropped transform. A parent
// whose computed transform is `none` publishes [NONE] (`JsonNull`) instead,
// and only that drops the entry.
//
// UNLIKE the iOS twin's documented approximation (an untransformed wrapper
// forwards the nearest TRANSFORMED ancestor's list), the renderer provides
// this local at EVERY RenderComponent, so a `transform: inherit` under a
// parent with no `transform` reads [NONE] = the parent's computed `none`
// (css-transforms-1 §3 initial value) and drops the property — the §7.3.2
// answer, not the nearest transformed ancestor's.

// CompositionLocal factory — the `LocalContainingBlock` /
// `LocalElementContainingBlock` form.
import androidx.compose.runtime.compositionLocalOf
// The renderer's property record: `type` + raw JSON `data`.
import com.styleconverter.runtime.core.ir.IRProperty
// The channel's payload: the parent's raw `Transform` wire.
import kotlinx.serialization.json.JsonElement
// The [NONE] sentinel: a JSON null is never a `Transform` wire shape.
import kotlinx.serialization.json.JsonNull
// The keyword wire is an object: `{"type":"keyword","keyword":…}`.
import kotlinx.serialization.json.JsonObject
// Null-safe string read of the `type` / `keyword` primitives.
import kotlinx.serialization.json.contentOrNull
// Primitive accessor for those two fields.
import kotlinx.serialization.json.jsonPrimitive

object TransformInheritance {

    /**
     * The parent's COMPUTED `Transform` wire, [NONE] when the parent's
     * computed transform is `none` (no `Transform` property), and Kotlin
     * `null` — the default — when no RenderComponent provided the channel on
     * this path. Read only through [resolve]; provided only by
     * ComponentRenderer (always via [published], which never returns null).
     */
    val LocalInheritedTransform = compositionLocalOf<JsonElement?> { null }

    /** "The parent's computed transform is `none`" — distinct from unprovided `null`. */
    val NONE: JsonElement = JsonNull

    /** The IR type name this channel is about. */
    const val TRANSFORM = "Transform"

    /**
     * True for the converter's CSS-wide `inherit` wire on `transform`:
     * `{"type":"keyword","keyword":"inherit"}` (TransformPropertyParser).
     * Case-insensitive on the keyword, like TransformExtractor's own read.
     */
    fun isInheritKeyword(data: JsonElement?): Boolean {
        // Only the keyword object shape carries CSS-wide keywords.
        val obj = data as? JsonObject ?: return false
        if (obj["type"]?.jsonPrimitive?.contentOrNull != "keyword") return false
        return obj["keyword"]?.jsonPrimitive?.contentOrNull?.lowercase() == "inherit"
    }

    /**
     * True when some `Transform` entry is the `inherit` keyword — the ONLY
     * lists [resolve] can change. The renderer reads [LocalInheritedTransform]
     * behind this gate, so an element without the keyword never subscribes
     * to its parent's (possibly animating) transform (skeptic nit 7).
     */
    fun carriesInherit(properties: List<IRProperty>): Boolean =
        properties.any { it.type == TRANSFORM && isInheritKeyword(it.data) }

    /**
     * The element's property list with `transform: inherit` resolved to the
     * parent's computed wire ([inherited]) — css-cascade-4 §7.3.2.
     *
     * Identity (the SAME list instance) whenever no `Transform` entry is the
     * `inherit` keyword, so every list without the keyword — the whole
     * committed baseline corpus and 1434 of the 1435 wave51-fix documents —
     * is byte-identical and allocation-free. When the keyword IS present:
     *   * [inherited] a wire → that entry's data becomes the parent's wire;
     *   * [inherited] [NONE] → the parent's computed value is `none`, and the
     *     entry is DROPPED (an absent `Transform` renders as `none`, which is
     *     also what TransformExtractor returns for `initial`);
     *   * [inherited] null (UNPROVIDED) → identity: the keyword reaches
     *     TransformExtractor, which refuses it with its breadcrumb.
     */
    fun resolve(properties: List<IRProperty>, inherited: JsonElement?): List<IRProperty> {
        // Fast path: nothing to resolve → identity, same instance.
        if (!carriesInherit(properties)) return properties
        // Unprovided channel: never guess — leave the keyword for the
        // extractor's PropertyTracker breadcrumb (no silent fallthrough).
        if (inherited == null) return properties
        // Rebuild only when the keyword is present (the sole corpus carrier).
        return properties.mapNotNull { p ->
            when {
                p.type != TRANSFORM || !isInheritKeyword(p.data) -> p
                // Parent computed `none`: drop the entry (see the contract above).
                inherited is JsonNull -> null
                else -> IRProperty(TRANSFORM, inherited)
            }
        }
    }

    /**
     * What THIS element publishes to its children: its own computed
     * `Transform` wire — the data of its last `Transform` entry (the cascade's
     * last-declaration-wins order the renderer's buckets already fold to), or
     * [NONE] when it declares none (computed `none`) — never the unprovided
     * `null`. Call it on the RESOLVED list, so a chain of `inherit` carries
     * the same value all the way down (§7.3.2 is per element, recursively).
     */
    fun published(resolvedProperties: List<IRProperty>): JsonElement =
        resolvedProperties.lastOrNull { it.type == TRANSFORM }?.data ?: NONE
}
