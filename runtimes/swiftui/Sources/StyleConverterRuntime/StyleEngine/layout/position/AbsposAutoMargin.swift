//
//  AbsposAutoMargin.swift
//  StyleEngine/layout/position — wave 31 (lane T).
//
//  AUTO-MARGIN resolution for absolutely positioned boxes, CSS 2.1
//  §10.3.7 (inline axis) / §10.6.4 (block axis).
//
//  ## The measured defect
//  css-tables/absolute-tables-016 centres a 100×100 abspos table in a
//  160×160 `position:relative` container with `inset: 0; margin: auto`.
//  §10.3.7: with `left`, `width` and `right` all non-auto and BOTH inline
//  margins auto, the equation `left + ml + width + mr + right = cb` is
//  over-constrained, and the extra constraint is that the two auto
//  margins get EQUAL values — each absorbs half the free space,
//  (160 − 0 − 100 − 0)/2 = 30. §10.6.4 says the identical thing for the
//  block axis.
//
//  iOS already PAINTS that test correctly (frozen wave30-final capture:
//  green 100×100 at (16, 88), matching the Chromium ref) — but through a
//  DIFFERENT mechanism: MarginApplier's HAutoFrameModifier /
//  VAutoFrameModifier expand the box to `.infinity` on an auto axis and
//  centre inside the expanded frame. That mechanism can only ever produce
//  the SYMMETRIC answer, because it splits the whole containing block and
//  ignores the insets; §10.3.7 says `cb − start − size − end`, which
//  differs for every non-zero inset (left:20/right:0/w:100 in a 160 cb →
//  margins 20 each → used x = 40; the frame answer is 30). Compose had
//  the same shape of approximation on the inline axis (§10.3.3's
//  `autoMarginAlignment`) and NOTHING on the block axis, which is why
//  Android painted the square 30px too high (0.9486, FAIL).
//
//  This file is the arithmetic both natives now share: the margins are
//  SOLVED and the start margin is folded into the start inset, so the
//  existing anchored-offset machinery (PositionApplier.anchoredInsetOffset
//  here, PositionApplier.absoluteOffset on Compose) paints the used
//  position with no new mounting slot. The rewrite also zeroes the
//  resolved margins, which is what stops the frame-based mechanism from
//  double-counting the same rule.
//
//  ## The DECLARED opposite margin (skeptic bugs 1 & 2)
//  §10.3.7's equation has SIX terms, not four: `left + ml + width + mr +
//  right = cb`. The first cut of this file dropped `ml`/`mr` from the free
//  space and zeroed BOTH sides of a resolved axis, so a declared margin
//  opposite an `auto` one was both ignored in the arithmetic and deleted
//  from the style. Two Chromium-verified misses (probe:
//  _diag31/skeptic/chromium-automargin-probe.mjs, cases C/F and the
//  rectangular-cb K/L/M):
//    - `left:0;right:0;width:100px;margin-left:auto;margin-right:20px` in
//      a 160px cb — Chromium paints x=40 with margins `40px 20px`; this
//      file answered x=0 and dropped the 20px.
//    - `margin-left:10%;margin-right:auto`, same shape — Chromium resolves
//      the percent against the cb and paints x=16 with margins
//      `16px 44px`; this file answered x=0 and rewrote the left margin
//      to 0.
//  So: `split` SUBTRACTS the declared opposite margin (M3) and reports it
//  back as the used value on the non-auto side (M6), and `resolveFor`
//  clears ONLY the sides that were genuinely `auto`. The percent basis for
//  a declared margin is the containing block's INLINE size on BOTH axes
//  (CSS 2.1 §8.3) — probe case K pins it: in a 160×120 cb a block-axis
//  `margin-top:10%` is 16px, not 12px, and that is the same basis
//  `SpacingResolver.percentBasisPx` hands MarginApplier under WPT capture,
//  so a preserved percent margin paints exactly the band assumed here.
//
//  ## Blast radius
//  M2 (all three of start inset / size / end inset definite) plus the
//  zero-split identity guard (M7) mean the ONLY box in the whole fixture
//  corpus this rewrites is absolute-tables-016's pair — verified by
//  scanning every `fixtures/**/*.json` for an out-of-flow box with any
//  auto margin: the css-align `abspos__*-stretch-auto-margins*` boxes all
//  resolve to a 0/0 split (their stretched size exactly fills the
//  inset-modified containing block) and take M7; filter-effects
//  `backdrop-filter-basic-blur` has an `auto` END inset and takes M2. No
//  corpus box mixes a declared margin with an auto one, so the bug-1/2 fix
//  is provably capture-neutral — it only widens what the rule answers
//  CORRECTLY when such a box arrives.
//
//  The resolver is PURE MATH with a signature mirrored verbatim by the
//  Compose twin (layout/position/AbsposAutoMargin.kt) so cross-native
//  probes can diff the two rule tables directly; the same pin table lives
//  in AbsposAutoMarginTest.kt / AbsposAutoMarginTests.swift.
//

import CoreGraphics
import Foundation

enum AbsposAutoMargin {

    /// The resolved used margins for ONE axis, in px.
    struct Axis: Equatable {
        let startPx: Double
        let endPx: Double
    }

    /// CSS 2.1 §10.3.7 / §10.6.4 auto-margin resolution for one axis of
    /// an absolutely positioned box. Returns nil when the rule does not
    /// apply — the caller then leaves the style untouched.
    ///
    /// The pin table (mirrored on Compose byte-for-byte):
    ///  - M1: no auto margin on this axis → nil. §10.3.7 only solves for
    ///    margins that are `auto`; a declared margin is used as declared.
    ///  - M2: any of the containing-block extent, the start inset, the
    ///    end inset or the used size is indefinite → nil. §10.3.7's
    ///    over-constrained branch is reached only when all three of
    ///    start/size/end are non-auto; with one of them auto the spec
    ///    instead sets the auto margins to ZERO and solves for that
    ///    value, which is exactly the pre-wave-31 behaviour (the auto
    ///    margin contributes nothing to the used position), so returning
    ///    nil here is the identity, not a silent fallthrough.
    ///  - M2b: a DECLARED margin on the non-auto side that cannot be read
    ///    as px (an `em`, an unresolved `calc()`, a percent with no known
    ///    basis) → nil. §10.3.7 cannot be solved without every term, and
    ///    guessing zero would silently move the box; the identity leaves
    ///    the pre-wave-31 render, which is the honest answer.
    ///  - M3: free = cb − start − size − end − declaredStart −
    ///    declaredEnd, where a side that IS auto contributes 0 (it is the
    ///    unknown being solved for). This is the space the auto margins
    ///    have to share. Dropping the declared terms was skeptic bug 1.
    ///  - M4 (both auto, free ≥ 0): equal split — `free/2` each. The
    ///    absolute-tables-016 pin: (160 − 0 − 100 − 0)/2 = 30.
    ///  - M5 (both auto, free < 0): §10.3.7 — "unless this would make
    ///    them negative, in which case when the direction of the
    ///    containing block is ltr, set margin-left to 0 and solve for
    ///    margin-right". The engine is LTR-normalized (see InsetRect's
    ///    `resolved(isRTL:)` fold), so the START margin is 0 and the END
    ///    margin takes the whole (negative) remainder. The box stays
    ///    flush with its start inset.
    ///  - M6 (exactly one auto): that margin absorbs ALL the remaining
    ///    free space (§10.3.7: "if there is exactly one value specified
    ///    as auto … that value is solved for") and the returned `Axis`
    ///    reports the DECLARED value on the other side — so the pair this
    ///    returns is always the USED margin pair Chromium's computed style
    ///    shows (probe case C: 40/20; case F: 16/44). No clamping: probe
    ///    case N confirms Chromium solves a single auto margin NEGATIVE
    ///    when the declared opposite margin exceeds the free space
    ///    (−60/120).
    ///
    /// All parameters are px. `startMarginPx`/`endMarginPx` are the
    /// DECLARED margins with percents already resolved against the cb's
    /// inline size (§8.3), and are ignored on whichever side is `auto`;
    /// nil means "declared but unreadable" (M2b). They default to 0 — the
    /// CSS initial value — so a caller with no declared margins reads
    /// exactly like the pre-bug-fix signature.
    static func split(
        cb: Double?,
        startInset: Double?,
        endInset: Double?,
        sizePx: Double?,
        startAuto: Bool,
        endAuto: Bool,
        startMarginPx: Double? = 0,
        endMarginPx: Double? = 0
    ) -> Axis? {
        // M1 — nothing to solve for.
        if !startAuto && !endAuto { return nil }
        // M2 — the over-constrained branch needs every other term.
        guard let cb, let startInset, let endInset, let sizePx else { return nil }
        // M2b — an auto side is the unknown (contributes 0); a declared
        // one must be readable in px or the equation has two unknowns.
        let declaredStart: Double
        if startAuto { declaredStart = 0 } else {
            guard let m = startMarginPx else { return nil }
            declaredStart = m
        }
        let declaredEnd: Double
        if endAuto { declaredEnd = 0 } else {
            guard let m = endMarginPx else { return nil }
            declaredEnd = m
        }
        // M3 — the free space the auto margins share, net of the declared
        // ones (the full six-term §10.3.7 equation).
        let free = cb - startInset - sizePx - endInset - declaredStart - declaredEnd
        // M4/M5 — both auto: equal split, or the LTR over-constrained
        // rule when the equal split would go negative. Both declared terms
        // are 0 here, so `free` is the classic four-term value.
        if startAuto && endAuto {
            return free >= 0
                ? Axis(startPx: free / 2, endPx: free / 2)
                : Axis(startPx: 0, endPx: free)
        }
        // M6 — the single auto margin absorbs everything that is left; the
        // other side reports its declared value unchanged.
        return startAuto ? Axis(startPx: free, endPx: declaredEnd)
                         : Axis(startPx: declaredStart, endPx: free)
    }

    /// The per-axis outcome of `resolveFor`: the inset the caller must
    /// WRITE BACK (start inset + SOLVED start margin) on each axis whose
    /// margins this rule resolved. A nil axis means "leave it alone".
    ///
    /// The `clears*` flags are PER SIDE, not per axis: only a side that
    /// was genuinely `auto` gets zeroed. A declared margin keeps its
    /// value so it still paints its own band — skeptic bug 2's fix, and
    /// what makes the Chromium `40px 20px` / `16px 44px` pairs
    /// reproducible here.
    struct Fold: Equatable {
        /// New `left` (nil = unchanged — e.g. a DECLARED left margin,
        /// which already paints as its own band and must not be folded
        /// into the inset as well).
        let leftPx: CGFloat?
        /// New `top` (nil = unchanged).
        let topPx: CGFloat?
        /// True when `margin-left` was `auto` and is now solved.
        let clearsLeftMargin: Bool
        /// True when `margin-right` was `auto` and is now solved.
        let clearsRightMargin: Bool
        /// True when `margin-top` was `auto` and is now solved.
        let clearsTopMargin: Bool
        /// True when `margin-bottom` was `auto` and is now solved.
        let clearsBottomMargin: Bool
        /// Identity — nothing to write back.
        static let none = Fold(leftPx: nil, topPx: nil,
                               clearsLeftMargin: false, clearsRightMargin: false,
                               clearsTopMargin: false, clearsBottomMargin: false)
        var isNone: Bool { self == .none }
    }

    /// iOS-side style fold: apply [split] to a built ComponentStyle's
    /// margin + size + inset state.
    ///
    /// `inset` comes from [AbsposInsetStretch.strictInsets] — the same
    /// px-honest reader the stretch lane uses, and for the same reason:
    /// only the typed `{"px":N}` wire is unambiguously absolute px, so a
    /// percent inset (which the live converter emits as a BARE number)
    /// can never silently become a px basis for this arithmetic. An
    /// unreadable side arrives as nil and M2 turns it into the identity.
    ///
    /// `size` must be the POST-stretch size config (call this after the
    /// AbsposInsetStretch fold) — the spec order too: §10.3.7 resolves
    /// the used width first and the margins then absorb the free space.
    ///
    /// M7 (identity guard): an axis that moves nothing and solves nothing
    /// to a non-zero band needs no write-back; leaving the `auto` keywords
    /// in place keeps the css-align abspos stretch captures byte-identical.
    static func resolveFor(margin: MarginConfig?,
                           size: SizeConfig,
                           inset: InsetRect?,
                           cbW: Double?, cbH: Double?) -> Fold {
        // No margin config at all → no `auto` anywhere → M1 identity.
        guard let margin else { return .none }
        // Only an EXACT px size is a definite used size; `auto`,
        // intrinsic keywords and unresolved calc/% all take M2.
        func exactPx(_ v: LengthValue?) -> Double? {
            if case .exact(let px) = v { return px }
            return nil
        }
        func isAuto(_ v: LengthValue) -> Bool {
            if case .auto = v { return true }
            return false
        }
        // A DECLARED margin in px — the M3 term skeptic bug 1 was missing.
        // `cbW` is the §8.3 percent basis on BOTH axes (probe case K: in a
        // 160×120 cb a block-axis `10%` is 16px, from the width), which is
        // the same basis `SpacingResolver.percentBasisPx` gives
        // MarginApplier, so a preserved percent margin paints this band.
        // nil = present but unreadable → M2b identity.
        func declaredPx(_ v: LengthValue) -> Double? {
            switch v {
            case .exact(let px): return px
            case .relative(let value, .percent, _):
                guard let cbW else { return nil }
                return value * cbW / 100
            // Any other relative unit (em/vw/…), calc, intrinsic, auto or
            // unknown has no px basis here and is never approximated.
            default: return nil
            }
        }
        let lAuto = isAuto(margin.left), rAuto = isAuto(margin.right)
        let tAuto = isAuto(margin.top), bAuto = isAuto(margin.bottom)
        let x = split(cb: cbW,
                      startInset: (inset?.left).map(Double.init),
                      endInset: (inset?.right).map(Double.init),
                      sizePx: exactPx(size.width),
                      startAuto: lAuto,
                      endAuto: rAuto,
                      // Declared only — an auto side is the unknown.
                      startMarginPx: lAuto ? 0 : declaredPx(margin.left),
                      endMarginPx: rAuto ? 0 : declaredPx(margin.right))
        let y = split(cb: cbH,
                      startInset: (inset?.top).map(Double.init),
                      endInset: (inset?.bottom).map(Double.init),
                      sizePx: exactPx(size.height),
                      startAuto: tAuto,
                      endAuto: bAuto,
                      startMarginPx: tAuto ? 0 : declaredPx(margin.top),
                      endMarginPx: bAuto ? 0 : declaredPx(margin.bottom))
        // How far each START edge actually moves. A DECLARED start margin
        // already paints as its own outer `.padding` band, so folding it
        // into the inset too would double-count it — only a solved AUTO
        // margin moves the inset.
        let dx = (lAuto ? x?.startPx : 0) ?? 0
        let dy = (tAuto ? y?.startPx : 0) ?? 0
        // The solved value on an auto END side. Non-zero means the style
        // must still lose its `auto` keyword even though the inset does
        // not move (probe cases D/F).
        let endX = (rAuto ? x?.endPx : 0) ?? 0
        let endY = (bAuto ? y?.endPx : 0) ?? 0
        // M7 per axis — an axis is live only if it changes something.
        let liveX = (x != nil && !(dx == 0 && endX == 0))
        let liveY = (y != nil && !(dy == 0 && endY == 0))
        guard liveX || liveY else { return .none }
        return Fold(
            // Used position = cb start edge + start inset + SOLVED start
            // margin (CSS 2.1 §10.3.7's equation). Folding it into the
            // inset lets PositionApplier.anchoredInsetOffset paint it with
            // no new mounting slot; nil leaves the extracted inset alone.
            leftPx: (liveX && dx != 0) ? CGFloat(Double(inset?.left ?? 0) + dx) : nil,
            topPx: (liveY && dy != 0) ? CGFloat(Double(inset?.top ?? 0) + dy) : nil,
            clearsLeftMargin: liveX && lAuto,
            clearsRightMargin: liveX && rAuto,
            clearsTopMargin: liveY && tAuto,
            clearsBottomMargin: liveY && bAuto)
    }
}
