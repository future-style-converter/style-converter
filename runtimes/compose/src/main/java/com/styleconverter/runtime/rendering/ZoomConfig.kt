package com.styleconverter.runtime.rendering

/**
 * Typed value of the CSS `zoom` property (css-viewport-1 §"The zoom
 * property", https://drafts.csswg.org/css-viewport/#zoom-property).
 *
 * `zoom` is no longer the legacy IE/WebKit extension — it is standardised
 * and multiplies the element's USED values: every length, spacing band,
 * border/stroke width and font size, plus the layout slot the element
 * occupies in its parent. That last part is what separates it from
 * `transform: scale()`, which only warps paint and leaves the slot alone.
 *
 * ## Wire shapes this models
 * One field per sealed variant of `ZoomValue` in the reader
 * (`converter/src/main/kotlin/app/parsing/css/properties/longhands/
 * rendering/ZoomPropertyParser.kt`):
 *
 * | IR payload                            | factor | isNormal |
 * |---------------------------------------|--------|----------|
 * | `{"type":"number","value":1.5}`       | 1.5    | false    |
 * | `{"type":"percentage","value":150}`   | 1.5    | false    |
 * | `{"type":"normal"}`                   | 1.0    | true     |
 * | `{"type":"reset"}`                    | 1.0    | true     |
 *
 * `reset` folds to identity ON PURPOSE and convergently: it is the legacy
 * WebKit keyword that is NOT in css-viewport-1, so a browser drops the
 * declaration as invalid and paints the element unzoomed. Web's
 * `ZoomApplier.ts` passes the token through for exactly that reason;
 * folding it to 1.0 here reproduces the browser's observable result
 * rather than inventing a scale the author never wrote.
 *
 * ## Why the factor is enough for the applier
 * `ZoomApplier` implements bounded css-viewport semantics by
 * MEASURE-THEN-SCALE (see that file): the subtree is measured against
 * `constraints / zoom` and the node reports `size * zoom`, so both the
 * used lengths inside AND the slot outside end up multiplied. That means
 * the applier needs nothing but the scalar — no per-length plumbing.
 * SwiftUI's `ZoomConfig.swift` is the byte-parallel twin of this file.
 */
data class ZoomConfig(
    /** The used scale factor. 1.0 = unzoomed. Always > 0 when [hasZoom]. */
    val zoom: Float = 1.0f,
    /** True for `normal` / `reset` / absent — i.e. "no scale to apply". */
    val isNormal: Boolean = true
) {
    /**
     * True when the applier has real work to do. Guards on BOTH fields so
     * an explicit `zoom: 1` (isNormal=false, zoom=1f) still short-circuits
     * to identity — scaling by 1 would otherwise insert a pointless layout
     * node and a graphics layer on every such element.
     */
    val hasZoom: Boolean
        get() = !isNormal && zoom != 1.0f
}
