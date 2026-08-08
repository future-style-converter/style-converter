//
//  ZoomConfig.swift
//  StyleEngine/rendering — the typed value of the CSS `zoom` property.
//
//  Spec: css-viewport-1 §"The zoom property"
//  (https://drafts.csswg.org/css-viewport/#zoom-property). `zoom` is no
//  longer the legacy IE/WebKit extension — it is standardised and
//  multiplies the element's USED values (every length, spacing band,
//  border/stroke width, font size) AND the layout slot the element
//  occupies in its parent. That last part is what separates it from
//  `transform: scale()`, which warps paint only and leaves the slot alone.
//
//  ## Wire shapes this models
//  One case per sealed variant of `ZoomValue` in the reader
//  (converter/src/main/kotlin/app/parsing/css/properties/longhands/
//  rendering/ZoomPropertyParser.kt):
//
//    {"type":"number","value":1.5}     → factor 1.5, isNormal false
//    {"type":"percentage","value":150} → factor 1.5, isNormal false
//    {"type":"normal"}                 → factor 1.0, isNormal true
//    {"type":"reset"}                  → factor 1.0, isNormal true
//
//  `reset` folds to identity ON PURPOSE and convergently: it is the
//  legacy WebKit keyword, absent from css-viewport-1, so a browser drops
//  the declaration as invalid and paints the element unzoomed. Web's
//  ZoomApplier.ts passes the token straight through for exactly that
//  reason; folding it to 1.0 here reproduces the browser's observable
//  result instead of inventing a scale the author never wrote.
//
//  ## Why the factor alone is enough for the applier
//  ZoomApplier implements bounded css-viewport semantics by
//  MEASURE-THEN-SCALE (see that file), so it needs nothing but the
//  scalar — no per-length plumbing. This file is the byte-parallel twin
//  of runtimes/compose/.../rendering/ZoomConfig.kt.
//

import Foundation

/// Typed `zoom` value. `nil` on ComponentStyle means "no Zoom in the IR".
struct ZoomConfig: Equatable {
    /// The used scale factor. 1.0 = unzoomed; always > 0 when `hasZoom`.
    var factor: CGFloat = 1.0
    /// True for `normal` / `reset` / absent — i.e. "no scale to apply".
    var isNormal: Bool = true

    /// True when the applier has real work to do. Guards on BOTH fields so
    /// an explicit `zoom: 1` still short-circuits to identity rather than
    /// inserting a Layout node and a geometry effect for a no-op scale.
    var hasZoom: Bool { !isNormal && factor != 1.0 }
}
