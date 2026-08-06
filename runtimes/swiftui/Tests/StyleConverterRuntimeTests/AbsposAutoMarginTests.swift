//
//  AbsposAutoMarginTests.swift
//  Wave 31 (lane T) — abspos AUTO-MARGIN resolution pins
//  (CSS 2.1 §10.3.7 inline / §10.6.4 block).
//
//  The resolver is pure math with an identical signature on Compose
//  (AbsposAutoMarginTest.kt pins the same table entry-for-entry so
//  cross-native probes can diff the rule tables); the style fold is
//  pinned against the LIVE wave-30 IR shapes
//  (tools/titan/runs/wave30-final/sections/css-tables/per-test-ir/
//  wpt__css-tables__absolute-tables-016.json).
//

import XCTest
@testable import StyleConverterRuntime

final class AbsposAutoMarginTests: XCTestCase {

    // MARK: - M1–M6: the pure §10.3.7 / §10.6.4 rule table

    /// M1 — §10.3.7 solves only for margins that ARE auto.
    func testM1NoAutoMarginResolvesNothing() {
        XCTAssertNil(AbsposAutoMargin.split(
            cb: 160, startInset: 0, endInset: 0, sizePx: 100,
            startAuto: false, endAuto: false))
    }

    /// M2 — the over-constrained branch needs left, width AND right
    /// non-auto. With `right: auto` the spec sets the auto margins to 0
    /// and solves for right instead, i.e. the pre-wave-31 behaviour. This
    /// is the filter-effects backdrop-filter-basic-blur shape.
    func testM2AutoEndInsetKeepsTheAxisUnresolved() {
        XCTAssertNil(AbsposAutoMargin.split(
            cb: 160, startInset: 100, endInset: nil, sizePx: 150,
            startAuto: true, endAuto: true))
    }

    /// M2 — an indefinite used size cannot produce a free space.
    func testM2IndefiniteSizeKeepsTheAxisUnresolved() {
        XCTAssertNil(AbsposAutoMargin.split(
            cb: 160, startInset: 0, endInset: 0, sizePx: nil,
            startAuto: true, endAuto: true))
    }

    /// M2 — nor can an unknown containing block.
    func testM2UnknownContainingBlockKeepsTheAxisUnresolved() {
        XCTAssertNil(AbsposAutoMargin.split(
            cb: nil, startInset: 0, endInset: 0, sizePx: 100,
            startAuto: true, endAuto: true))
    }

    /// M4 — the absolute-tables-016 pin: a 100px box with `inset: 0` in a
    /// 160px containing block → free 60 → 30 each. This is the exact 30px
    /// the wave30-final Android capture was missing on the block axis.
    func testM4BothAutoSplitTheFreeSpace() {
        let r = AbsposAutoMargin.split(
            cb: 160, startInset: 0, endInset: 0, sizePx: 100,
            startAuto: true, endAuto: true)
        XCTAssertEqual(r?.startPx, 30)
        XCTAssertEqual(r?.endPx, 30)
    }

    /// M4 — the whole reason this is arithmetic and not an alignment: a
    /// frame-centring answers 30 here regardless of the insets; §10.3.7
    /// answers (160 − 20 − 100 − 0)/2 = 20, used x = 20 + 20 = 40.
    func testM4NonZeroInsetsAreSubtractedBeforeTheSplit() {
        let r = AbsposAutoMargin.split(
            cb: 160, startInset: 20, endInset: 0, sizePx: 100,
            startAuto: true, endAuto: true)
        XCTAssertEqual(r?.startPx, 20)
        XCTAssertEqual(r?.endPx, 20)
    }

    /// M5 — §10.3.7: "unless this would make them negative, in which case
    /// when the direction of the containing block is ltr, set margin-left
    /// to 0 and solve for margin-right".
    func testM5NegativeFreeSpacePinsTheLTRStartMarginAtZero() {
        let r = AbsposAutoMargin.split(
            cb: 100, startInset: 0, endInset: 0, sizePx: 160,
            startAuto: true, endAuto: true)
        XCTAssertEqual(r?.startPx, 0)
        XCTAssertEqual(r?.endPx, -60)
    }

    /// M6 — a single auto margin absorbs all the free space.
    func testM6SingleAutoMarginAbsorbsEverything() {
        let start = AbsposAutoMargin.split(
            cb: 160, startInset: 0, endInset: 0, sizePx: 100,
            startAuto: true, endAuto: false)
        XCTAssertEqual(start?.startPx, 60)
        XCTAssertEqual(start?.endPx, 0)
        let end = AbsposAutoMargin.split(
            cb: 160, startInset: 0, endInset: 0, sizePx: 100,
            startAuto: false, endAuto: true)
        XCTAssertEqual(end?.startPx, 0)
        XCTAssertEqual(end?.endPx, 60)
    }

    // MARK: - M3/M6 with a DECLARED opposite margin (skeptic bugs 1 & 2)
    // Chromium numbers from _diag31/skeptic/chromium-automargin-probe.mjs
    // (a 160px containing block, `left:0;right:0;width:100px`), which also
    // asserts native == Chromium on every case listed here.

    /// BUG 1 — `margin-left:auto; margin-right:20px`: §10.3.7 solves
    /// margin-left = 160 − 0 − 100 − 0 − 20 = 40. Chromium probe case C:
    /// used x=40, computed margins `40px 20px`. The pre-fix table left the
    /// 20px out of `free` and answered 60 (and the fold then deleted the
    /// declared 20px, painting x=0).
    func testM3DeclaredOppositeMarginIsSubtractedFromTheFreeSpace() {
        let r = AbsposAutoMargin.split(
            cb: 160, startInset: 0, endInset: 0, sizePx: 100,
            startAuto: true, endAuto: false, endMarginPx: 20)
        XCTAssertEqual(r?.startPx, 40)
        // The non-auto side reports its DECLARED value, so the pair is
        // exactly Chromium's computed margin pair.
        XCTAssertEqual(r?.endPx, 20)
    }

    /// BUG 2 — `margin-left:10%; margin-right:auto`. §8.3: the percent
    /// resolves against the containing block's inline size → 16px; §10.3.7
    /// then solves margin-right = 160 − 0 − 100 − 0 − 16 = 44. Chromium
    /// probe case F: used x=16, computed margins `16px 44px`. The pre-fix
    /// table answered 0/60 and the fold zeroed the left margin → x=0.
    func testM3DeclaredPercentStartMarginIsSubtractedAndReported() {
        let r = AbsposAutoMargin.split(
            cb: 160, startInset: 0, endInset: 0, sizePx: 100,
            startAuto: false, endAuto: true, startMarginPx: 16)
        XCTAssertEqual(r?.startPx, 16)
        XCTAssertEqual(r?.endPx, 44)
    }

    /// `margin-left:auto; margin-right:120px` → 60 − 120 = −60. Chromium
    /// probe case N: used x=−60, computed margins `-60px 120px`. Only the
    /// BOTH-auto branch has the §10.3.7 negative special case (M5).
    func testM3SingleAutoMarginMaySolveNegative() {
        let r = AbsposAutoMargin.split(
            cb: 160, startInset: 0, endInset: 0, sizePx: 100,
            startAuto: true, endAuto: false, endMarginPx: 120)
        XCTAssertEqual(r?.startPx, -60)
        XCTAssertEqual(r?.endPx, 120)
    }

    /// Both sides auto means both are unknowns; whatever the caller passes
    /// for the declared values must not enter `free`, so absolute-tables-016
    /// still answers 30/30.
    func testM3DeclaredMarginOnABothAutoAxisIsIgnored() {
        let r = AbsposAutoMargin.split(
            cb: 160, startInset: 0, endInset: 0, sizePx: 100,
            startAuto: true, endAuto: true,
            startMarginPx: 999, endMarginPx: 999)
        XCTAssertEqual(r?.startPx, 30)
        XCTAssertEqual(r?.endPx, 30)
    }

    /// M2b — an `em` / unresolved calc() opposite an auto margin leaves the
    /// equation with two unknowns. Guessing zero would silently move the
    /// box, so the rule declines and the pre-wave-31 render stands.
    func testM2bUnreadableDeclaredMarginKeepsTheAxisUnresolved() {
        XCTAssertNil(AbsposAutoMargin.split(
            cb: 160, startInset: 0, endInset: 0, sizePx: 100,
            startAuto: true, endAuto: false, endMarginPx: nil))
        XCTAssertNil(AbsposAutoMargin.split(
            cb: 160, startInset: 0, endInset: 0, sizePx: 100,
            startAuto: false, endAuto: true, startMarginPx: nil))
    }

    /// CSS 2.1 §8.3, pinned with a RECTANGULAR 160×120 containing block so
    /// the two candidate bases give different answers. Chromium probe case
    /// K (`margin-top:10%; margin-bottom:auto`, height 100): used y=16,
    /// computed block margins `16px 4px` — the 10% is 16 (from the 160
    /// WIDTH), not 12 (from the 120 height).
    func testM3PercentMarginsUseTheCbInlineSizeOnTheBlockAxisToo() {
        let r = AbsposAutoMargin.split(
            cb: 120, startInset: 0, endInset: 0, sizePx: 100,
            startAuto: false, endAuto: true, startMarginPx: 16)
        XCTAssertEqual(r?.startPx, 16)
        XCTAssertEqual(r?.endPx, 4)
    }

    /// The css-align abspos__*-stretch-auto-margins shape: `inset: 50px`
    /// with the size STRETCHED to cb − 100 leaves no free space, so the
    /// margins are 0 and the style fold takes its M7 identity guard.
    func testZeroFreeSpaceStretchAxisResolvesBothMarginsToZero() {
        let r = AbsposAutoMargin.split(
            cb: 400, startInset: 50, endInset: 50, sizePx: 300,
            startAuto: true, endAuto: true)
        XCTAssertEqual(r?.startPx, 0)
        XCTAssertEqual(r?.endPx, 0)
    }

    // MARK: - Chromium probe parity (every case, both axes)
    // One row per AXIS of every case in
    // _diag31/skeptic/chromium-automargin-probe.mjs, carrying the numbers
    // Chromium actually painted. `used` composes `split` exactly the way
    // `resolveFor` folds it, so this table and the probe's own JS mirror of
    // the rule table can only agree if the native rule table is right.
    // Byte-parallel with the same two tests in AbsposAutoMarginTest.kt.

    /// One axis' inputs: cb extent, insets, used size, auto flags, declared
    /// margins in px (percents pre-resolved against the cb's INLINE size
    /// per §8.3 — noted per row where it matters).
    private struct Ax {
        var cb: Double?
        var start: Double?
        var end: Double?
        var size: Double?
        var sAuto = false
        var eAuto = false
        var sDecl: Double = 0
        var eDecl: Double = 0
    }

    /// (used start-edge offset, used start margin, used end margin) — the
    /// three quantities the probe reads out of Chromium. An unresolved axis
    /// (M1/M2/M2b) keeps its declared bands and an `auto` margin there is
    /// used as 0, which is the pre-wave-31 behaviour.
    private func used(_ a: Ax) -> (Double, Double, Double) {
        let declStart = a.sAuto ? 0 : a.sDecl
        let declEnd = a.eAuto ? 0 : a.eDecl
        guard let axis = AbsposAutoMargin.split(
            cb: a.cb, startInset: a.start, endInset: a.end, sizePx: a.size,
            startAuto: a.sAuto, endAuto: a.eAuto,
            startMarginPx: declStart, endMarginPx: declEnd)
        else { return ((a.start ?? 0) + declStart, declStart, declEnd) }
        // Only a SOLVED auto start margin moves the inset; a declared one
        // paints its own band and must not be counted twice.
        return ((a.start ?? 0) + (a.sAuto ? axis.startPx : declStart),
                axis.startPx, axis.endPx)
    }

    private func assertAxis(_ label: String, _ a: Ax,
                            _ offset: Double, _ sm: Double, _ em: Double) {
        let (o, s, e) = used(a)
        XCTAssertEqual(o, offset, "\(label) offset")
        XCTAssertEqual(s, sm, "\(label) start margin")
        XCTAssertEqual(e, em, "\(label) end margin")
    }

    func testChromiumProbeParityInlineAxis() {
        // A 160px cb, `left:0;right:0;width:100px` unless stated.
        func base(_ s: Bool, _ e: Bool, _ sd: Double, _ ed: Double) -> Ax {
            Ax(cb: 160, start: 0, end: 0, size: 100,
               sAuto: s, eAuto: e, sDecl: sd, eDecl: ed)
        }
        assertAxis("A margin:auto", base(true, true, 0, 0), 30, 30, 30)
        assertAxis("B ml:auto mr:0", base(true, false, 0, 0), 60, 60, 0)
        assertAxis("C ml:auto mr:20px", base(true, false, 0, 20), 40, 40, 20)
        assertAxis("D ml:0 mr:auto", base(false, true, 0, 0), 0, 0, 60)
        // E moves the start inset to 20 — the case a frame-based centring
        // gets wrong (it would answer 30, not 20+20=40).
        assertAxis("E left:20 margin:auto",
                   Ax(cb: 160, start: 20, end: 0, size: 100, sAuto: true, eAuto: true),
                   40, 20, 20)
        // F/G: `10%` of the 160px cb inline size = 16.
        assertAxis("F ml:10% mr:auto", base(false, true, 16, 0), 16, 16, 44)
        assertAxis("G ml:10% mr:10%", base(false, false, 16, 16), 16, 16, 16)
        assertAxis("H width:160 right:60 margin:auto",
                   Ax(cb: 160, start: 0, end: 60, size: 160, sAuto: true, eAuto: true),
                   0, 0, -60)
        assertAxis("I ml:0 mr:0", base(false, false, 0, 0), 0, 0, 0)
        // J has `right: auto` → M2, so the auto margins stay 0 and the box
        // sits at its declared left.
        assertAxis("J left:100 right:auto margin-inline:auto",
                   Ax(cb: 160, start: 100, end: nil, size: 150, sAuto: true, eAuto: true),
                   100, 0, 0)
        assertAxis("K rect cb, inline margins 0", base(false, false, 0, 0), 0, 0, 0)
        assertAxis("L rect cb, ml:10% mr:10%", base(false, false, 16, 16), 16, 16, 16)
        assertAxis("M rect cb, inline margins 0", base(false, false, 0, 0), 0, 0, 0)
        assertAxis("N ml:auto mr:120px", base(true, false, 0, 120), -60, -60, 120)
    }

    func testChromiumProbeParityBlockAxis() {
        // A 160px-tall cb, `top:0;bottom:0;height:100px` unless stated;
        // K/L/M use the RECTANGULAR 160×120 cb, where a `10%` margin is
        // still 16 (the 160 WIDTH, §8.3) and not 12.
        func sq(_ s: Bool, _ e: Bool, _ sd: Double, _ ed: Double) -> Ax {
            Ax(cb: 160, start: 0, end: 0, size: 100,
               sAuto: s, eAuto: e, sDecl: sd, eDecl: ed)
        }
        func rect(_ s: Bool, _ e: Bool, _ sd: Double, _ ed: Double) -> Ax {
            Ax(cb: 120, start: 0, end: 0, size: 100,
               sAuto: s, eAuto: e, sDecl: sd, eDecl: ed)
        }
        assertAxis("A margin:auto", sq(true, true, 0, 0), 30, 30, 30)
        assertAxis("B block margins 0", sq(false, false, 0, 0), 0, 0, 0)
        assertAxis("C block margins 0", sq(false, false, 0, 0), 0, 0, 0)
        assertAxis("D block margins 0", sq(false, false, 0, 0), 0, 0, 0)
        assertAxis("E top:20 margin:auto",
                   Ax(cb: 160, start: 20, end: 0, size: 100, sAuto: true, eAuto: true),
                   40, 20, 20)
        assertAxis("F block margins 0", sq(false, false, 0, 0), 0, 0, 0)
        assertAxis("G block margins 0", sq(false, false, 0, 0), 0, 0, 0)
        assertAxis("H margin:auto", sq(true, true, 0, 0), 30, 30, 30)
        assertAxis("I mt:auto mb:auto", sq(true, true, 0, 0), 30, 30, 30)
        assertAxis("J bottom:auto",
                   Ax(cb: 160, start: -50, end: nil, size: 100), -50, 0, 0)
        assertAxis("K mt:10% mb:auto", rect(false, true, 16, 0), 16, 16, 4)
        assertAxis("L mt:10% mb:10%", rect(false, false, 16, 16), 16, 16, 16)
        assertAxis("M mt:auto mb:10%", rect(true, false, 0, 16), 4, 4, 16)
        assertAxis("N block margins 0", sq(false, false, 0, 0), 0, 0, 0)
    }

    // MARK: - The style fold (live IR shapes)

    /// absolute-tables-016's abspos child, verbatim from the live IR.
    private func tables016() throws -> IRComponent {
        try JSONDecoder().decode(IRComponent.self, from: Data(#"""
        {"id":"c","name":"c","properties":[
          {"type":"Display","data":"TABLE"},
          {"type":"Position","data":"ABSOLUTE"},
          {"type":"Width","data":{"type":"length","px":100}},
          {"type":"Height","data":{"type":"length","px":100}},
          {"type":"Top","data":{"px":0}},
          {"type":"Right","data":{"px":0}},
          {"type":"Bottom","data":{"px":0}},
          {"type":"Left","data":{"px":0}},
          {"type":"MarginTop","data":"auto"},
          {"type":"MarginRight","data":"auto"},
          {"type":"MarginBottom","data":"auto"},
          {"type":"MarginLeft","data":"auto"}
        ]}
        """#.utf8))
    }

    /// The end-to-end pin: 160×160 containing block, 100×100 box, all
    /// insets 0, all margins auto → the used position is (30, 30) from the
    /// containing block's origin, where the Chromium ref paints the square.
    func testResolveForFoldsBothSolvedStartMarginsIntoTheInsets() throws {
        let comp = try tables016()
        let style = StyleBuilder.build(from: comp.properties)
        let fold = AbsposAutoMargin.resolveFor(
            margin: style.spacing.margin,
            size: style.size,
            inset: AbsposInsetStretch.strictInsets(from: comp.properties),
            cbW: 160, cbH: 160)
        XCTAssertEqual(fold.leftPx, 30)
        XCTAssertEqual(fold.topPx, 30)
        // All four sides were `auto`, so all four are cleared.
        XCTAssertTrue(fold.clearsLeftMargin)
        XCTAssertTrue(fold.clearsRightMargin)
        XCTAssertTrue(fold.clearsTopMargin)
        XCTAssertTrue(fold.clearsBottomMargin)
    }

    /// A 100×100 abspos box with `inset: 0` and the given margin wires
    /// appended — the shared shape of every probe case below.
    private func box(_ margins: String) throws -> IRComponent {
        let head = """
        {"id":"c","name":"c","properties":[
          {"type":"Position","data":"ABSOLUTE"},
          {"type":"Width","data":{"type":"length","px":100}},
          {"type":"Height","data":{"type":"length","px":100}},
          {"type":"Top","data":{"px":0}},
          {"type":"Right","data":{"px":0}},
          {"type":"Bottom","data":{"px":0}},
          {"type":"Left","data":{"px":0}},
        """
        return try JSONDecoder().decode(
            IRComponent.self, from: Data((head + margins + "]}").utf8))
    }

    /// BUG 1 — `margin-left:auto; margin-right:20px` in a 160px cb.
    /// Chromium (probe case C) paints the border box at x=40 with computed
    /// margins `40px 20px`. The fold folds the SOLVED 40 into `left`,
    /// clears the `auto` side, and leaves the declared 20px alone so it
    /// still paints its own `.padding` band — before this fix the box
    /// landed at x=0 with the 20px deleted.
    func testResolveForBug1KeepsTheDeclaredOppositeMargin() throws {
        let comp = try box("""
          {"type":"MarginLeft","data":"auto"},
          {"type":"MarginRight","data":{"px":20}},
          {"type":"MarginTop","data":{"px":0}},
          {"type":"MarginBottom","data":{"px":0}}
        """)
        let style = StyleBuilder.build(from: comp.properties)
        let fold = AbsposAutoMargin.resolveFor(
            margin: style.spacing.margin,
            size: style.size,
            inset: AbsposInsetStretch.strictInsets(from: comp.properties),
            cbW: 160, cbH: 160)
        XCTAssertEqual(fold.leftPx, 40)
        XCTAssertTrue(fold.clearsLeftMargin)
        XCTAssertFalse(fold.clearsRightMargin, "the declared 20px must survive")
        // The block axis has no auto margin at all — M1 leaves it alone.
        XCTAssertNil(fold.topPx)
        XCTAssertFalse(fold.clearsTopMargin)
        XCTAssertFalse(fold.clearsBottomMargin)
    }

    /// BUG 2 — `margin-left:10%; margin-right:auto` in a 160px cb.
    /// Chromium (probe case F) paints x=16 with computed margins
    /// `16px 44px`. The used position is `left(0)` plus the 16px band
    /// MarginApplier paints from the SAME §8.3 basis, so the inset must NOT
    /// move and the percent must survive — before this fix the left margin
    /// was rewritten to 0 and the box landed at x=0.
    func testResolveForBug2PreservesTheDeclaredPercentStartMargin() throws {
        // A bare number on a margin longhand IS a percent (see
        // extractLengthPercentDefault) — how the live converter emits `10%`.
        let comp = try box("""
          {"type":"MarginLeft","data":10},
          {"type":"MarginRight","data":"auto"},
          {"type":"MarginTop","data":{"px":0}},
          {"type":"MarginBottom","data":{"px":0}}
        """)
        let style = StyleBuilder.build(from: comp.properties)
        XCTAssertEqual(style.spacing.margin?.left,
                       .relative(value: 10, unit: .percent, pxFallback: nil))
        let fold = AbsposAutoMargin.resolveFor(
            margin: style.spacing.margin,
            size: style.size,
            inset: AbsposInsetStretch.strictInsets(from: comp.properties),
            cbW: 160, cbH: 160)
        XCTAssertNil(fold.leftPx, "the start inset must not move")
        XCTAssertFalse(fold.clearsLeftMargin, "the declared 10% must survive")
        // The auto END side still loses its keyword: MarginConfig's
        // horizontalAutoAlignment would otherwise push the box inside an
        // expanded frame on top of the arithmetic.
        XCTAssertTrue(fold.clearsRightMargin)
    }

    /// CSS 2.1 §8.3 with a RECTANGULAR 160×120 containing block, the shape
    /// that discriminates the two candidate percent bases. Chromium probe
    /// case M (`margin-top:auto; margin-bottom:10%`, height 100): used y=4,
    /// computed block margins `4px 16px`.
    ///   width basis  → bottom 16 → top = 120 − 100 − 16 = 4  ← Chromium
    ///   height basis → bottom 12 → top = 8                   ← wrong
    /// so `fold.topPx` alone pins the basis.
    func testResolveForResolvesBlockAxisPercentsAgainstTheInlineSize() throws {
        let comp = try box("""
          {"type":"MarginLeft","data":{"px":0}},
          {"type":"MarginRight","data":{"px":0}},
          {"type":"MarginTop","data":"auto"},
          {"type":"MarginBottom","data":10}
        """)
        let style = StyleBuilder.build(from: comp.properties)
        let fold = AbsposAutoMargin.resolveFor(
            margin: style.spacing.margin,
            size: style.size,
            inset: AbsposInsetStretch.strictInsets(from: comp.properties),
            cbW: 160, cbH: 120)
        XCTAssertEqual(fold.topPx, 4)
        XCTAssertTrue(fold.clearsTopMargin)
        XCTAssertFalse(fold.clearsBottomMargin, "the declared 10% must survive")
    }

    /// M2b end-to-end: `margin-right: 2em` opposite an auto margin. The
    /// equation has two unknowns, so the rule declines rather than guessing
    /// zero and silently moving the box.
    func testResolveForIsIdentityWhenADeclaredMarginIsUnreadable() throws {
        let comp = try box("""
          {"type":"MarginLeft","data":"auto"},
          {"type":"MarginRight","data":{"original":{"v":2,"u":"em"}}},
          {"type":"MarginTop","data":{"px":0}},
          {"type":"MarginBottom","data":{"px":0}}
        """)
        let style = StyleBuilder.build(from: comp.properties)
        let fold = AbsposAutoMargin.resolveFor(
            margin: style.spacing.margin,
            size: style.size,
            inset: AbsposInsetStretch.strictInsets(from: comp.properties),
            cbW: 160, cbH: 160)
        XCTAssertTrue(fold.isNone)
    }

    /// M7 — a 0/0 split moves nothing, so the fold is the identity and the
    /// css-align abspos stretch captures stay byte-identical.
    func testResolveForIsIdentityWhenTheSplitIsZero() throws {
        let comp = try JSONDecoder().decode(IRComponent.self, from: Data(#"""
        {"id":"c","name":"c","properties":[
          {"type":"Position","data":"ABSOLUTE"},
          {"type":"Top","data":{"px":50}},
          {"type":"Right","data":{"px":50}},
          {"type":"Bottom","data":{"px":50}},
          {"type":"Left","data":{"px":50}},
          {"type":"Width","data":{"type":"length","px":300}},
          {"type":"Height","data":{"type":"length","px":300}},
          {"type":"MarginTop","data":"auto"},
          {"type":"MarginBottom","data":"auto"}
        ]}
        """#.utf8))
        let style = StyleBuilder.build(from: comp.properties)
        let fold = AbsposAutoMargin.resolveFor(
            margin: style.spacing.margin,
            size: style.size,
            inset: AbsposInsetStretch.strictInsets(from: comp.properties),
            cbW: 400, cbH: 400)
        XCTAssertTrue(fold.isNone)
    }

    /// M2 on the live backdrop-filter-basic-blur shape (`margin: 0px auto`
    /// with no `right`). That test's render is owned by another lane; the
    /// pin exists so this fold provably cannot move it.
    func testResolveForIsIdentityForTheBackdropFilterShape() throws {
        let comp = try JSONDecoder().decode(IRComponent.self, from: Data(#"""
        {"id":"c","name":"c","properties":[
          {"type":"Position","data":"ABSOLUTE"},
          {"type":"Top","data":{"px":-50}},
          {"type":"Left","data":{"px":100}},
          {"type":"Width","data":{"type":"length","px":150}},
          {"type":"Height","data":{"type":"length","px":100}},
          {"type":"MarginLeft","data":"auto"},
          {"type":"MarginRight","data":"auto"},
          {"type":"MarginTop","data":{"px":0}},
          {"type":"MarginBottom","data":{"px":0}}
        ]}
        """#.utf8))
        let style = StyleBuilder.build(from: comp.properties)
        let fold = AbsposAutoMargin.resolveFor(
            margin: style.spacing.margin,
            size: style.size,
            inset: AbsposInsetStretch.strictInsets(from: comp.properties),
            cbW: 390, cbH: 600)
        XCTAssertTrue(fold.isNone)
    }

    /// An unknown containing block takes M2 on both axes.
    func testResolveForIsIdentityWithoutAContainingBlock() throws {
        let comp = try tables016()
        let style = StyleBuilder.build(from: comp.properties)
        let fold = AbsposAutoMargin.resolveFor(
            margin: style.spacing.margin,
            size: style.size,
            inset: AbsposInsetStretch.strictInsets(from: comp.properties),
            cbW: nil, cbH: nil)
        XCTAssertTrue(fold.isNone)
    }
}
