//
//  PercentInsetResolve.swift
//  StyleEngine/layout/position — wave 49 (lane A7).
//
//  PERCENTAGE INSETS (top/right/bottom/left + the logical spellings),
//  CSS 2.1 §9.4.3 read through css-position-3 §"Relative Positioning"
//  (drafts.csswg.org/css-position-3/#relpos-insets).
//
//  This file is the byte-for-byte twin of the Compose rule table in
//  runtimes/compose/.../layout/position/PercentInsetResolve.kt — same
//  decisions, same guard, so a cross-native probe can diff the two.
//
//  ## The measured defect
//  tools/titan/runs/wave48-final/sections/css-position/per-test-ir/
//  wpt__css-position__position-relative-006.json carries, verbatim:
//
//      { "type":"Top", "data":-10000 }, { "type":"Position","data":"RELATIVE" }
//
//  on a GREEN 100×100 div whose parent declares `Width:100px` and
//  `MinHeight:100px` and paints RED. `data: -10000` is the converter's
//  IRPercentage wire for `top: -10000%`: InsetValueSerializer emits a
//  BARE JSON NUMBER for InsetValue.PercentageValue, while a length is
//  always the object shape `{"px":N}` / `{"type":"length","px":N}`.
//  `PositionExtractor.extractPx` read that bare number as POINTS
//  (`if case .double(let d) = value { return CGFloat(d) }`), so the green
//  square translated 10 000 pt off the canvas and the red parent was left
//  bare — the frozen capture
//  …/report/images/iOS/wpt__css-position__position-relative-006.png is a
//  pure-red square where the Chromium ref paints it green.
//
//  ## What CSS says
//  A percentage inset resolves against the CORRESPONDING dimension of the
//  containing block: the inline (width) axis for left/right, the block
//  (height) axis for top/bottom. css-position-3's relpos-insets section
//  adds the clause the test asserts — when that dimension is INDEFINITE
//  the percentage resolves to ZERO. `min-height` is not `height`, so the
//  parent's block axis is indefinite and the used inset is 0.
//
//  ## The channel, and the guard on it
//  `EnvironmentValues.containingBlockWidth` / `.containingBlockHeight`
//  (Renderer/ContainingBlock.swift) publish the ancestor's content box,
//  nil meaning "not statically definite". That nil CONFLATES two things:
//    (a) the ancestor IS modelled and its block size is genuinely `auto`
//        — CSS-indefinite, the §relpos-insets zero case; and
//    (b) the ancestor declared no size at all (an inline `<span>`, a
//        `<tbody>`), so the channel never modelled the real containing
//        block, which per CSS 2.1 §10.1 is the nearest BLOCK CONTAINER
//        ancestor — a box the channel skipped over.
//  Only (a) is CSS-indefinite, so `resolve` treats a nil axis as zero only
//  when the OTHER axis is definite (the evidence that the ancestor was
//  modelled at all); with both axes nil it keeps the pre-wave-49 value.
//  That guard is CHANNEL-GAP conservatism, not spec text. Live witnesses:
//    • position-relative-006's child → cb (100, nil) → case (a) → 0.
//    • position-relative-002's child → parent is a sizeless `<span>` →
//      cb (nil, nil) → case (b) → untouched (that test passes today only
//      because its containing block happens to be exactly 100pt, so
//      `-100%` and `-100pt` coincide).
//
//  ## Blast radius, enumerated before the change
//  Every per-test-ir document of all 30 frozen wave-48 sections was
//  scanned for a bare-number inset: 10 properties across exactly 7 tests,
//  all css-position/position-relative-001…006 and -008. Only -006's child
//  sits under a modelled containing block with an indefinite block axis.
//

import CoreGraphics
import Foundation

/// Percentage magnitudes in CSS percent units (`-100%` is `-100`) for
/// whichever inset longhands the IR wrote as a bare number. One optional
/// slot per longhand so a mixed declaration (`top: 50%; left: 10px`)
/// leaves the px side on the legacy path.
struct InsetPercents: Equatable {
    var top: CGFloat? = nil
    var right: CGFloat? = nil
    var bottom: CGFloat? = nil
    var left: CGFloat? = nil
    var blockStart: CGFloat? = nil
    var blockEnd: CGFloat? = nil
    var inlineStart: CGFloat? = nil
    var inlineEnd: CGFloat? = nil

    /// True when at least one side was declared as a percentage.
    var any: Bool {
        top != nil || right != nil || bottom != nil || left != nil ||
        blockStart != nil || blockEnd != nil || inlineStart != nil || inlineEnd != nil
    }

    /// Physical-side mirror for RTL, the exact twin of
    /// `InsetRect.resolved(isRTL:)` in PositionExtractor.swift: the
    /// extractor folds logical sides onto physical ones assuming LTR, and
    /// the applier re-resolves once it can see the layout direction. The
    /// percentage channel has to be mirrored in lockstep or a `left: 50%`
    /// would be scaled onto the wrong physical slot under RTL.
    /// Block-axis sides are writing-mode invariant for horizontal-tb.
    func resolved(isRTL: Bool) -> InsetPercents {
        guard isRTL else { return self }
        var out = self
        out.left = self.right
        out.right = self.left
        return out
    }
}

enum PercentInsetResolve {

    /// The percentage an inset payload carries, or nil when it is not the
    /// percentage wire shape. "Bare number ⇔ percentage" is a property of
    /// the converter's InsetValueSerializer (see the file header), not a
    /// heuristic: `auto` is `.string`, and every length / `expr` /
    /// `anchor()` carrier is `.object`.
    static func percentOf(_ value: IRValue) -> CGFloat? {
        if case .double(let d) = value { return CGFloat(d) }
        if case .int(let i) = value { return CGFloat(i) }
        return nil
    }

    /// Resolve `percents` against the containing block and return the used
    /// inset rect, starting from `legacy` (the pre-wave-49 rect) so every
    /// non-percentage side is carried through verbatim.
    ///
    /// Returns `legacy` untouched when nothing is a percentage, or when
    /// both channel axes are nil (the case-(b) guard in the header).
    ///
    /// - TODO(wave-49 A7): the spec-true repair for case (b) is for a
    ///   non-block-container ancestor to REPUBLISH its own containing
    ///   block (CSS 2.1 §10.1) instead of writing nil; that lives in the
    ///   renderer's `.environment(\.containingBlock…)` sites, not here.
    static func resolve(legacy: InsetRect?,
                        percents: InsetPercents?,
                        cbWidth: CGFloat?,
                        cbHeight: CGFloat?) -> InsetRect? {
        guard let pct = percents, pct.any else { return legacy }
        guard cbWidth != nil || cbHeight != nil else { return legacy }
        var out = legacy ?? InsetRect()

        // CSS 2.1 §9.4.3 axis table: left/right (and the inline logical
        // spellings) scale the containing block's WIDTH, top/bottom (and
        // the block logical spellings) its HEIGHT.
        if let p = pct.top { out.top = side(p, cbHeight) }
        if let p = pct.bottom { out.bottom = side(p, cbHeight) }
        if let p = pct.left { out.left = side(p, cbWidth) }
        if let p = pct.right { out.right = side(p, cbWidth) }
        // Logical spellings, folded onto the same physical slots the
        // extractor already folds them onto (horizontal-tb is the only
        // writing mode this runtime models). A physical percentage
        // declared on the same side wins, matching the extractor's own
        // "physical wins" ordering, so these only fill an empty slot.
        if pct.top == nil, let p = pct.blockStart { out.top = side(p, cbHeight) }
        if pct.bottom == nil, let p = pct.blockEnd { out.bottom = side(p, cbHeight) }
        if pct.left == nil, let p = pct.inlineStart { out.left = side(p, cbWidth) }
        if pct.right == nil, let p = pct.inlineEnd { out.right = side(p, cbWidth) }
        return out
    }

    /// One side's used inset. A definite base scales the percentage
    /// (css-values-4 §5.5); a nil base on a modelled containing block is
    /// the CSS-indefinite case and resolves to 0 (css-position-3
    /// §relpos-insets — position-relative-006's own assert).
    private static func side(_ percent: CGFloat, _ base: CGFloat?) -> CGFloat {
        guard let base = base else { return 0 }
        return base * percent / 100
    }
}
