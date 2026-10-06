//
//  ComposedRootStackTests.swift
//  StyleConverterRuntimeTests
//
//  Pins the RC-A4 (wave 19) declared-margin root-stack plan
//  (UABlockMargin.rootStackMargin + staticDeclaredEdges) that the composed
//  WPT canvas uses to COLLAPSE adjacent stacked roots' declared block
//  margins per CSS 2.1 §8.3.1 instead of stacking them (the safe-001
//  96px-vs-76px root-pitch drift). The R1–R7 expected values are IDENTICAL
//  to the Kotlin twin's pins (apps/android-harness UaBlockMarginsTest) so
//  the two implementations cannot drift apart silently.
//

import XCTest
import CoreGraphics
@testable import StyleConverterRuntime

final class ComposedRootStackTests: XCTestCase {

    // MARK: - IR helper (same wire-decoding pattern as UABlockMarginTests)

    /// Decode a childless component's `properties` from wire JSON —
    /// IRProperty is Decodable-only, so tests stay on live byte shapes.
    private func props(_ propsJSON: String) throws -> [IRProperty] {
        let json = "{\"id\":\"t\",\"name\":\"t\",\"properties\":[\(propsJSON)]}"
        return try JSONDecoder().decode(IRComponent.self, from: Data(json.utf8)).properties
    }

    // MARK: - R1–R5: the per-root plan (shared pin table)

    func testR1PureUaRootKeepsRound4Contribution() {
        // R1: an undeclared <p> contributes its UA 16/16 and never strips.
        let plan = UABlockMargin.rootStackMargin(
            tag: "p", declaresTop: false, declaresBottom: false,
            staticDeclaredEdges: (top: 0, bottom: 0))
        XCTAssertEqual(plan, .init(top: 16, bottom: 16, stripDeclared: false))
    }

    func testR2Safe001RootDeclared20BothSidesFoldsAndStrips() {
        // R2: the safe-001 flex containers (margin: 20px, tag-less divs) —
        // declared px feed the fold and the root's block margins strip.
        let plan = UABlockMargin.rootStackMargin(
            tag: nil, declaresTop: true, declaresBottom: true,
            staticDeclaredEdges: (top: 20, bottom: 20))
        XCTAssertEqual(plan, .init(top: 20, bottom: 20, stripDeclared: true))
    }

    func testR3DeclaredTopOnlyMixesDeclaredAndUa() {
        // R3: declared top (20px) wins over UA; undeclared bottom keeps UA 16.
        let plan = UABlockMargin.rootStackMargin(
            tag: "p", declaresTop: true, declaresBottom: false,
            staticDeclaredEdges: (top: 20, bottom: 0))
        XCTAssertEqual(plan, .init(top: 20, bottom: 16, stripDeclared: true))
    }

    func testR4NonStaticDeclaredMarginsBailToRound4() {
        // R4: auto/negative/relative/calc (classifier nil) — both declared
        // sides render via MarginApplier (UA zeroed), no strip.
        let plan = UABlockMargin.rootStackMargin(
            tag: "p", declaresTop: true, declaresBottom: true,
            staticDeclaredEdges: nil)
        XCTAssertEqual(plan, .init(top: 0, bottom: 0, stripDeclared: false))
    }

    func testR5PartialBailKeepsUaOnUndeclaredSide() {
        // R5: only bottom declared and unresolvable — UA top survives.
        let plan = UABlockMargin.rootStackMargin(
            tag: "p", declaresTop: false, declaresBottom: true,
            staticDeclaredEdges: nil)
        XCTAssertEqual(plan, .init(top: 16, bottom: 0, stripDeclared: false))
    }

    // MARK: - R6/R7: the fold over the plans (stackedSpacing reuse)

    func testR6Safe001StackFourRootsCollapseTo20pxGaps() {
        // R6: four stripped 20/20 roots → every interior gap max(20,20)=20,
        // NOT 40 — the ref's 76px root pitch (56px box + 20px gap).
        let plans = (0..<4).map { _ in UABlockMargin.rootStackMargin(
            tag: nil, declaresTop: true, declaresBottom: true,
            staticDeclaredEdges: (top: 20, bottom: 20)) }
        let spacing = UABlockMargin.stackedSpacing(
            plans.map { (top: $0.top, bottom: $0.bottom) })
        XCTAssertEqual(spacing.leading, [20, 20, 20, 20])
        XCTAssertEqual(spacing.trailing, 20)
    }

    func testR7DeclaredAboveUaParagraphCollapsesToMax() {
        // R7: a div with declared margin-bottom 20 above an undeclared <p>
        // (UA 16 top) → interior gap max(20, 16) = 20.
        let above = UABlockMargin.rootStackMargin(
            tag: "div", declaresTop: false, declaresBottom: true,
            staticDeclaredEdges: (top: 0, bottom: 20))
        let below = UABlockMargin.rootStackMargin(
            tag: "p", declaresTop: false, declaresBottom: false,
            staticDeclaredEdges: (top: 0, bottom: 0))
        let spacing = UABlockMargin.stackedSpacing([
            (top: above.top, bottom: above.bottom),
            (top: below.top, bottom: below.bottom),
        ])
        XCTAssertEqual(spacing.leading, [0, 20])
        XCTAssertEqual(spacing.trailing, 16)
    }

    // MARK: - staticDeclaredEdges rides the runtime's §8.3.1 classifier

    func testStaticEdgesReadTheLiveSafe001WireShape() throws {
        // The exact per-side wire the safe-001 IR carries: {"px": 20}.
        let p = try props("""
        {"type":"MarginTop","data":{"px":20}},
        {"type":"MarginBottom","data":{"px":20}},
        {"type":"MarginLeft","data":{"px":20}},
        {"type":"MarginRight","data":{"px":20}}
        """)
        let edges = UABlockMargin.staticDeclaredEdges(p)
        XCTAssertEqual(edges?.top, 20)
        XCTAssertEqual(edges?.bottom, 20)
    }

    func testStaticEdgesBailOnAutoVerticalMargin() throws {
        // margin-top: auto is centering, not px — the classifier must bail
        // (nil) so the canvas keeps the Round 4 behavior for this root.
        let p = try props("""
        {"type":"MarginTop","data":"auto"},
        {"type":"MarginBottom","data":{"px":20}}
        """)
        XCTAssertNil(UABlockMargin.staticDeclaredEdges(p))
    }

    func testStaticEdgesTreatMarginlessRootAsZeroZero() throws {
        // No margin longhand at all → the CSS initial 0/0 (eligible), which
        // rootStackMargin then overrides per-side with the UA defaults.
        let p = try props("{\"type\":\"BackgroundColor\",\"data\":\"#fff\"}")
        let edges = UABlockMargin.staticDeclaredEdges(p)
        XCTAssertEqual(edges?.top, 0)
        XCTAssertEqual(edges?.bottom, 0)
    }

    // MARK: - Wave-19 follow-up: §8.3.1 collapse THROUGH transparent roots
    // Shared T1–T6 pin table, byte-parallel with the Kotlin twin
    // (UaBlockMarginsTest). A margin-transparent root has zero flow
    // footprint (on iOS: the RC1 static-position root behind
    // StaticPositionAnchor): the adjoining-margin set stays OPEN across it,
    // so its two margined neighbors share ONE max() gap instead of one each.

    /// Shorthand: an opaque stripped root plan.
    private func opaque(_ top: CGFloat, _ bottom: CGFloat) -> UABlockMargin.RootStackMargin {
        .init(top: top, bottom: bottom, stripDeclared: true)
    }
    /// Shorthand: a margin-transparent (zero-flow) root plan.
    private func transparent(_ top: CGFloat = 0, _ bottom: CGFloat = 0,
                             strip: Bool = false) -> UABlockMargin.RootStackMargin {
        .init(top: top, bottom: bottom, stripDeclared: strip, marginTransparent: true)
    }

    func testT1TransparentRootBetweenMarginedRootsCollapsesToOneGap() {
        // T1 — the composed shape: a transparent abspos static-position root
        // between two stripped margin:20px containers. The set {20,0,0,20}
        // resolves to ONE 20px gap, emitted ABOVE the transparent slot
        // (§8.3.1 collapse-through position — the slot sits where the
        // hypothetical in-flow box would); zero above the next container.
        // Inter-container space 20 → the ref's 76px root pitch (56 + 20).
        let s = UABlockMargin.stackedSpacing(plans: [opaque(20, 20), transparent(), opaque(20, 20)])
        XCTAssertEqual(s.leading, [20, 20, 0])
        XCTAssertEqual(s.trailing, 20)
    }

    func testT2ChainOfTwoTransparentRootsStillOneCollapsedGap() {
        // T2 — TWO transparent roots between the margined pair: the set stays
        // open across both — still exactly one 20px inter-container gap.
        let s = UABlockMargin.stackedSpacing(
            plans: [opaque(20, 20), transparent(), transparent(), opaque(20, 20)])
        XCTAssertEqual(s.leading, [20, 20, 0, 0])
        XCTAssertEqual(s.trailing, 20)
    }

    func testT3TransparentRootWithOwnMarginsParticipatesInTheMax() {
        // T3 — a transparent root with its OWN declared (stripped) margins:
        // the whole adjoining set {20, 30, 10, 20} collapses to max = 30
        // (§8.3.1's empty-box model), emitted once; its slot anchors at the
        // prefix max through its own top margin (30) — the hypothetical
        // static position — and the following root adds nothing.
        let s = UABlockMargin.stackedSpacing(
            plans: [opaque(0, 20), transparent(30, 10, strip: true), opaque(20, 0)])
        XCTAssertEqual(s.leading, [0, 30, 0])
        XCTAssertEqual(s.trailing, 0)
    }

    func testT4AllOpaquePlansMatchTheLegacyTupleFoldByteForByte() {
        // T4 — regression: with no transparent entry the plan fold IS the
        // Round 4 tuple fold (which now delegates), pinned on the R6
        // safe-001 shape: four stripped 20/20 containers → every gap 20.
        let plans = (0..<4).map { _ in opaque(20, 20) }
        let viaPlans = UABlockMargin.stackedSpacing(plans: plans)
        let viaTuples = UABlockMargin.stackedSpacing(
            plans.map { (top: $0.top, bottom: $0.bottom) })
        XCTAssertEqual(viaPlans.leading, viaTuples.leading)
        XCTAssertEqual(viaPlans.trailing, viaTuples.trailing)
        XCTAssertEqual(viaPlans.leading, [20, 20, 20, 20])
        XCTAssertEqual(viaPlans.trailing, 20)
    }

    func testT5LeadingAndTrailingTransparentRootsKeepPaddingBoundedEdges() {
        // T5 — transparent FIRST: nothing above its slot (the canvas padding
        // bounds the set) and the container's 20px top margin still emits in
        // full. Transparent LAST: the container's 20px bottom margin sits
        // above its slot; the trailing pad is 0 (total below stays 20).
        let lead = UABlockMargin.stackedSpacing(plans: [transparent(), opaque(20, 20)])
        XCTAssertEqual(lead.leading, [0, 20])
        XCTAssertEqual(lead.trailing, 20)
        let trail = UABlockMargin.stackedSpacing(plans: [opaque(20, 20), transparent()])
        XCTAssertEqual(trail.leading, [20, 20])
        XCTAssertEqual(trail.trailing, 0)
    }

    func testT6ZeroMarginTransparentRootBetweenSmallMargins() {
        // T6 — the dynamic-align-self-001 flow shape (8px UA-ish margins
        // around a zero-margin transparent slot): one 8px collapsed gap,
        // mirroring the Kotlin twin's hoisted-root pin (on iOS the hoisted
        // flavor never reaches the flow list, so the transparent entry here
        // stands for the static-position family).
        let s = UABlockMargin.stackedSpacing(plans: [opaque(8, 8), transparent(), opaque(8, 8)])
        XCTAssertEqual(s.leading, [8, 8, 0])
        XCTAssertEqual(s.trailing, 8)
    }

    // MARK: - B1-B6: wave 26 (lane RES residual 3a) — the ROOT-STACK
    // DOUBLE-COUNT fold. Byte-parallel with the Kotlin twin
    // (apps/android-harness UaBlockMarginsTest, same B-names and numbers)
    // and with the band producer's own pins (composedRootHoistBand below).

    func testB1ZeroBandLeavesThePlanUntouched() {
        // The already-correct shape (blockquote root + <p> child: band 0 on
        // both edges) stays byte-identical — every pre-wave-26 capture whose
        // roots emit no band is unmoved by this residual.
        let plan = opaque(16, 16)
        XCTAssertEqual(UABlockMargin.withHoistBand(plan, band: (top: 0, bottom: 0)), plan)
    }

    func testB2BandJoinsTheStackEdgeAsTheCollapsedThroughValue() {
        // blockquote root (own 16) whose first/last child is an <h5> (27):
        // the renderer's band is max(16,27) − 16 = 11, so the folded edge is
        // 16 + 11 = 27 — the collapsed-through margin the browser takes into
        // the adjoining chain.
        let folded = UABlockMargin.withHoistBand(opaque(16, 16), band: (top: 11, bottom: 11))
        XCTAssertEqual(folded.top, 27)
        XCTAssertEqual(folded.bottom, 27)
        // Flags ride through unchanged.
        XCTAssertTrue(folded.stripDeclared)
        XCTAssertFalse(folded.marginTransparent)
    }

    func testB3TheDoubleCountThatUsedToRenderIsGone() {
        // THE DEFECT, end to end. Roots: [prev with a 40px bottom margin,
        // blockquote (own 16) whose first child is an <h5> (27)].
        //   BEFORE: pad = max(40,16) = 40, PLUS the renderer's 11px band
        //           painted outside the root -> 51px of spacing.
        //   BROWSER: one adjoining set {40, 16, 27} -> max = 40.
        //   NOW: the band is folded in (root edge 27) and suppressed on the
        //        render, so the fold alone emits max(40, 27) = 40.
        let prev = opaque(0, 40)
        let root = UABlockMargin.withHoistBand(opaque(16, 16), band: (top: 11, bottom: 11))
        XCTAssertEqual(UABlockMargin.stackedSpacing(plans: [prev, root]).leading[1], 40)
    }

    func testB4TwoAdjacentBandCarryingRootsCollapseToOneGap() {
        // The case that PROVES subtraction cannot express this: root A (own
        // 0) whose last child has a 16px bottom, root B (own 0) whose first
        // child has a 16px top. Both bands are 16, folded edges 16/16, so
        // the §8.3.1 fold emits ONE 16px gap. Subtracting the two bands from
        // that gap would give -16 -> floored 0 -> 0+16+16 = 32 rendered.
        let a = UABlockMargin.withHoistBand(opaque(0, 0), band: (top: 16, bottom: 16))
        let b = UABlockMargin.withHoistBand(opaque(0, 0), band: (top: 16, bottom: 16))
        let s = UABlockMargin.stackedSpacing(plans: [a, b])
        XCTAssertEqual(s.leading, [16, 16])
        XCTAssertEqual(s.trailing, 16)
    }

    func testB5TransparentRootsNeverTakeABand() {
        // A zero-flow root (the RC1 static-position anchor on iOS) displaces
        // no flow sibling, so whatever its subtree emits must not enter the
        // gap math — returned verbatim, band ignored.
        let t = transparent(4, 4)
        XCTAssertEqual(UABlockMargin.withHoistBand(t, band: (top: 16, bottom: 16)), t)
    }

    func testB6AsymmetricBandsFoldPerEdge() {
        // Edge gates resolve independently (§8.3.1 is per edge): a root with
        // a padded TOP and an open bottom folds only the bottom band.
        let folded = UABlockMargin.withHoistBand(opaque(0, 0), band: (top: 0, bottom: 16))
        XCTAssertEqual(folded.top, 0)
        XCTAssertEqual(folded.bottom, 16)
    }

    // MARK: - The band PRODUCER + the suppression channel (twins of the
    // Compose RootHoistBandTest pins).

    /// A 20px prose bar with a source tag and no declared margins — the UA
    /// table supplies its block edges when uaBlockMargins is on.
    private func bar(_ id: String, _ tag: String) throws -> IRComponent {
        let json = """
        {"id":"\(id)","name":"\(id)","meta":{"sourceTag":"\(tag)"},
         "properties":[{"type":"Height","data":{"type":"length","px":20.0}}]}
        """
        return try JSONDecoder().decode(IRComponent.self, from: Data(json.utf8))
    }

    /// An UNPADDED block root (explicit padding-0 longhands are the live wire
    /// shape) with the given tag and one child — both edge gates open.
    private func root(_ id: String, _ tag: String, childTag: String,
                      padTop: Double = 0.0) throws -> IRComponent {
        let json = """
        {"id":"\(id)","name":"\(id)","meta":{"sourceTag":"\(tag)"},
         "properties":[
           {"type":"Display","data":"block"},
           {"type":"Width","data":{"type":"length","px":300.0}},
           {"type":"PaddingTop","data":{"px":\(padTop)}},
           {"type":"PaddingRight","data":{"px":0.0}},
           {"type":"PaddingBottom","data":{"px":0.0}},
           {"type":"PaddingLeft","data":{"px":0.0}}],
         "children":[
           {"id":"\(id)__0","name":"c","meta":{"sourceTag":"\(childTag)"},
            "properties":[{"type":"Height","data":{"type":"length","px":20.0}}]}]}
        """
        return try JSONDecoder().decode(IRComponent.self, from: Data(json.utf8))
    }

    func testB1BandBlockquoteWithProseChildIsZero() throws {
        // max(16, 16) − 16 = 0 on both edges: the plan already composes
        // against the parent's OWN UA margin, so the root stack alone owns
        // the 16.
        let b = UABlockMargin.composedRootHoistBand(
            try root("r1", "blockquote", childTag: "p"), uaBlockMargins: true)
        XCTAssertEqual(b.top, 0)
        XCTAssertEqual(b.bottom, 0)
    }

    func testB2BandBiggerChildMarginEscapesAsTheExcessOnly() throws {
        // <h5> child (UA 27) under a blockquote root (UA 16): band 11.
        let b = UABlockMargin.composedRootHoistBand(
            try root("r2", "blockquote", childTag: "h5"), uaBlockMargins: true)
        XCTAssertEqual(b.top, 11)
        XCTAssertEqual(b.bottom, 11)
    }

    func testB4BandDarkStageBranchSeesNoUaMarginsAtAll() throws {
        // uaBlockMargins = false is the property-fixture pipeline: no UA
        // table, no declared margins => no plan => no band. The 327
        // committed baselines ride this branch.
        let b = UABlockMargin.composedRootHoistBand(
            try root("r4", "blockquote", childTag: "h5"), uaBlockMargins: false)
        XCTAssertEqual(b.top, 0)
        XCTAssertEqual(b.bottom, 0)
    }

    func testB6BandChildlessRootHasNoBand() throws {
        // The (0,0) contract for "no plan": a leaf root emits nothing, so
        // the harness fold needs no nil handling.
        let b = UABlockMargin.composedRootHoistBand(try bar("r6", "p"),
                                                    uaBlockMargins: true)
        XCTAssertEqual(b.top, 0)
        XCTAssertEqual(b.bottom, 0)
    }

    func testSuppressionChannelMatchesExactlyOneIdAndNeverLeaks() {
        // nil channel (every non-composed path) suppresses nothing.
        XCTAssertFalse(MarginCollapse.suppressesHoistBand(suppressedForId: nil,
                                                          componentId: "r1"))
        // The flagged root matches …
        XCTAssertTrue(MarginCollapse.suppressesHoistBand(suppressedForId: "r1",
                                                         componentId: "r1"))
        // … and nothing else does, including its own descendants: the
        // extractor's ids are hierarchical (`<root>__<i>`), so a child id
        // always EXTENDS its ancestor's and can never equal it.
        XCTAssertFalse(MarginCollapse.suppressesHoistBand(suppressedForId: "r1",
                                                          componentId: "r1__0"))
        XCTAssertFalse(MarginCollapse.suppressesHoistBand(suppressedForId: "r1",
                                                          componentId: "r2"))
    }

    // MARK: - B7/B8 — the accessor must report EXACTLY what the renderer
    // paints. The harness suppresses the renderer's band UNCONDITIONALLY, so
    // an accessor that under-reports deletes real spacing just as one that
    // over-reports adds phantom spacing. Byte-parallel with the Kotlin twin.

    func testB7NonBlockRootEmitsNoBandBecauseTheRendererPaintsNone() throws {
        // §8.3.1 collapsing is block-flow only. `MarginCollapsePlanner`
        // carries that guard INSIDE the planner (GATE 1/2/4), so both this
        // accessor and the renderer already read one gated number — pinned
        // so it can never migrate to the call site the way Compose's had.
        for display in ["flex", "grid", "inline-block"] {
            let b = UABlockMargin.composedRootHoistBand(
                try displayRoot(display), uaBlockMargins: true)
            XCTAssertEqual(b.top, 0, "display: \(display) top")
            XCTAssertEqual(b.bottom, 0, "display: \(display) bottom")
        }
    }

    func testB8DisplayContentsFirstChildIsUnboxedBeforePlanning() throws {
        // `ComponentRenderer.init` runs ContentsUnboxing.resolve before
        // anything reads the child list, so the renderer plans against the
        // GRANDCHILD. Planning against the raw tree saw the wrapper `<div>`
        // (UA 0) and reported a 0 band while the renderer planned 27 (the
        // `<h5>`'s UA margin) — and the unconditional suppression then
        // deleted those 27px outright.
        let b = UABlockMargin.composedRootHoistBand(try contentsRoot(),
                                                    uaBlockMargins: true)
        XCTAssertEqual(b.top, 27)
        XCTAssertEqual(b.bottom, 16)
    }

    /// A two-`<p>`-child root carrying an explicit `display` keyword.
    private func displayRoot(_ display: String) throws -> IRComponent {
        let json = """
        {"id":"r7","name":"r7","meta":{"sourceTag":"div"},
         "properties":[
           {"type":"Display","data":"\(display)"},
           {"type":"PaddingTop","data":{"px":0.0}},
           {"type":"PaddingBottom","data":{"px":0.0}}],
         "children":[
           {"id":"r7__0","name":"c","meta":{"sourceTag":"p"},
            "properties":[{"type":"Height","data":{"type":"length","px":20.0}}]},
           {"id":"r7__1","name":"c","meta":{"sourceTag":"p"},
            "properties":[{"type":"Height","data":{"type":"length","px":20.0}}]}]}
        """
        return try JSONDecoder().decode(IRComponent.self, from: Data(json.utf8))
    }

    /// `<div><div style="display:contents"><h5></div><p></div>`.
    private func contentsRoot() throws -> IRComponent {
        let json = """
        {"id":"r8","name":"r8","meta":{"sourceTag":"div"},
         "properties":[
           {"type":"Display","data":"block"},
           {"type":"PaddingTop","data":{"px":0.0}},
           {"type":"PaddingBottom","data":{"px":0.0}}],
         "children":[
           {"id":"r8__0","name":"w","meta":{"sourceTag":"div"},
            "properties":[{"type":"Display","data":"contents"}],
            "children":[{"id":"r8__0__0","name":"c","meta":{"sourceTag":"h5"},
              "properties":[{"type":"Height","data":{"type":"length","px":20.0}}]}]},
           {"id":"r8__1","name":"c","meta":{"sourceTag":"p"},
            "properties":[{"type":"Height","data":{"type":"length","px":20.0}}]}]}
        """
        return try JSONDecoder().decode(IRComponent.self, from: Data(json.utf8))
    }

    // MARK: - wave-52 lane L2 — the composed-stack PLAN on the VERBATIM per-test IR
    //
    // U1–U6 pin `UABlockMargin.composedRootStackPlan` / `isSelfCollapsingRoot`
    // on documents copied byte-for-byte out of tools/titan/runs/wave51-fix/
    // sections/<section>/per-test-ir/, decoded through the runtime's public
    // IRDocument witness (the harness's path). Byte-parallel with the Kotlin
    // twin (apps/android-harness UaBlockMarginsTest U1–U6).
    //
    // EXECUTED MUTATIONS (applied to ComposedRootStack.swift, this class run
    // alone under Catalyst, source restored byte-exact — sha256 checked;
    // record in tools/titan/results/wave52-composed-canvas/_note.md):
    //   M1 shape 1 restored to the wave-19 join (RC1 root: rootStackMargin
    //      + marginTransparent) → testU1 red (50, strip).
    //   M2 `isSelfCollapsingRoot` forced false → testU2 red (32 px).
    //   M3 the generated-content guard deleted  → testU7 red.
    //   M4 the `Display` BLOCK guard deleted    → testU8 red.
    //   M5 canvasBodyMargin's §8.3 guard deleted → testU10 red.
    //   M6 withCanvasOwnedBodyMargin returns `roots` unchanged → testU11 red.
    //   M7 withUaBlockMarginOnHoistedRoot returns `root` first  → testU12 red (T6).

    /// Decode a v2 document's components exactly as the harness does.
    private func rootsOf(_ componentsJSON: String) throws -> [IRComponent] {
        let json = "{\"irVersion\":2,\"minReaderVersion\":2,\"components\":[\(componentsJSON)]}"
        return try JSONDecoder().decode(IRDocument.self, from: Data(json.utf8)).components
    }

    /// css-masking/clip-path/clip-path-ellipse-006 — roots 0 and 1, verbatim.
    private let ellipse006 = """
        {"id":"wpt__css-masking__clip-path__clip-path-ellipse-006__0-059","name":"wpt__css-masking__clip-path__clip-path-ellipse-006__0","properties":[],"text":"The test passes if there is a full green ellipse.","meta":{"sourceTag":"p","role":"ws-after"}},
        {"id":"wpt__css-masking__clip-path__clip-path-ellipse-006__1-060","name":"wpt__css-masking__clip-path__clip-path-ellipse-006__1","properties":[{"type":"Width","data":{"type":"length","px":150}},{"type":"Height","data":{"type":"length","px":100}},{"type":"Position","data":"ABSOLUTE"},{"type":"MarginTop","data":{"px":50}},{"type":"MarginRight","data":{"px":50}},{"type":"MarginBottom","data":{"px":50}},{"type":"MarginLeft","data":{"px":50}},{"type":"BackgroundColor","data":{"srgb":{"r":0,"g":0.5019607843137255,"b":0},"original":"green"}},{"type":"ClipPath","data":{"type":"ellipse"}}]}
        """

    /// css-text-decor/text-decoration-propagation-shadow — all three roots, verbatim (post-load wire).
    private let propagationShadow = """
        {"id":"wpt__css-text-decor__text-decoration-propagation-shadow__0-230","name":"wpt__css-text-decor__text-decoration-propagation-shadow__0","properties":[{"type":"Width","data":{"type":"length","px":358}},{"type":"Height","data":{"type":"length","px":0}},{"type":"Position","data":"STATIC"},{"type":"BoxSizing","data":"CONTENT_BOX"},{"type":"MarginTop","data":{"px":16}},{"type":"MarginRight","data":{"px":0}},{"type":"MarginBottom","data":{"px":16}},{"type":"MarginLeft","data":{"px":0}},{"type":"PaddingTop","data":{"px":0}},{"type":"PaddingRight","data":{"px":0}},{"type":"PaddingBottom","data":{"px":0}},{"type":"PaddingLeft","data":{"px":0}},{"type":"BorderTopWidth","data":{"px":0}},{"type":"BorderRightWidth","data":{"px":0}},{"type":"BorderBottomWidth","data":{"px":0}},{"type":"BorderLeftWidth","data":{"px":0}},{"type":"BorderTopStyle","data":"NONE"},{"type":"BorderRightStyle","data":"NONE"},{"type":"BorderBottomStyle","data":"NONE"},{"type":"BorderLeftStyle","data":"NONE"},{"type":"BorderTopColor","data":{"srgb":{"r":0,"g":0,"b":0},"original":{"r":0,"g":0,"b":0}}},{"type":"BorderRightColor","data":{"srgb":{"r":0,"g":0,"b":0},"original":{"r":0,"g":0,"b":0}}},{"type":"BorderBottomColor","data":{"srgb":{"r":0,"g":0,"b":0},"original":{"r":0,"g":0,"b":0}}},{"type":"BorderLeftColor","data":{"srgb":{"r":0,"g":0,"b":0},"original":{"r":0,"g":0,"b":0}}},{"type":"BackgroundColor","data":{"srgb":{"r":0,"g":0,"b":0,"a":0},"original":{"r":0,"g":0,"b":0,"a":0}}},{"type":"Display","data":"BLOCK"},{"type":"OverflowX","data":"VISIBLE"},{"type":"OverflowY","data":"VISIBLE"}],"meta":{"sourceTag":"p"}},
        {"id":"wpt__css-text-decor__text-decoration-propagation-shadow__1-231","name":"wpt__css-text-decor__text-decoration-propagation-shadow__1","properties":[{"type":"TextDecorationLine","data":["UNDERLINE"]},{"type":"Position","data":"STATIC"},{"type":"Width","data":{"type":"length","px":358}},{"type":"Height","data":{"type":"length","px":20}},{"type":"BoxSizing","data":"CONTENT_BOX"},{"type":"MarginTop","data":{"px":0}},{"type":"MarginRight","data":{"px":0}},{"type":"MarginBottom","data":{"px":0}},{"type":"MarginLeft","data":{"px":0}},{"type":"PaddingTop","data":{"px":0}},{"type":"PaddingRight","data":{"px":0}},{"type":"PaddingBottom","data":{"px":0}},{"type":"PaddingLeft","data":{"px":0}},{"type":"BorderTopWidth","data":{"px":0}},{"type":"BorderRightWidth","data":{"px":0}},{"type":"BorderBottomWidth","data":{"px":0}},{"type":"BorderLeftWidth","data":{"px":0}},{"type":"BorderTopStyle","data":"NONE"},{"type":"BorderRightStyle","data":"NONE"},{"type":"BorderBottomStyle","data":"NONE"},{"type":"BorderLeftStyle","data":"NONE"},{"type":"BorderTopColor","data":{"srgb":{"r":0,"g":0,"b":0},"original":{"r":0,"g":0,"b":0}}},{"type":"BorderRightColor","data":{"srgb":{"r":0,"g":0,"b":0},"original":{"r":0,"g":0,"b":0}}},{"type":"BorderBottomColor","data":{"srgb":{"r":0,"g":0,"b":0},"original":{"r":0,"g":0,"b":0}}},{"type":"BorderLeftColor","data":{"srgb":{"r":0,"g":0,"b":0},"original":{"r":0,"g":0,"b":0}}},{"type":"BackgroundColor","data":{"srgb":{"r":0,"g":0,"b":0,"a":0},"original":{"r":0,"g":0,"b":0,"a":0}}},{"type":"Display","data":"BLOCK"},{"type":"OverflowX","data":"VISIBLE"},{"type":"OverflowY","data":"VISIBLE"}],"text":"This text should be underlined.","meta":{"role":"ws-after"}},
        {"id":"wpt__css-text-decor__text-decoration-propagation-shadow__2-232","name":"wpt__css-text-decor__text-decoration-propagation-shadow__2","properties":[{"type":"Width","data":{"type":"length","px":358}},{"type":"Height","data":{"type":"length","px":0}},{"type":"Position","data":"STATIC"},{"type":"BoxSizing","data":"CONTENT_BOX"},{"type":"MarginTop","data":{"px":16}},{"type":"MarginRight","data":{"px":0}},{"type":"MarginBottom","data":{"px":16}},{"type":"MarginLeft","data":{"px":0}},{"type":"PaddingTop","data":{"px":0}},{"type":"PaddingRight","data":{"px":0}},{"type":"PaddingBottom","data":{"px":0}},{"type":"PaddingLeft","data":{"px":0}},{"type":"BorderTopWidth","data":{"px":0}},{"type":"BorderRightWidth","data":{"px":0}},{"type":"BorderBottomWidth","data":{"px":0}},{"type":"BorderLeftWidth","data":{"px":0}},{"type":"BorderTopStyle","data":"NONE"},{"type":"BorderRightStyle","data":"NONE"},{"type":"BorderBottomStyle","data":"NONE"},{"type":"BorderLeftStyle","data":"NONE"},{"type":"BorderTopColor","data":{"srgb":{"r":0,"g":0,"b":0},"original":{"r":0,"g":0,"b":0}}},{"type":"BorderRightColor","data":{"srgb":{"r":0,"g":0,"b":0},"original":{"r":0,"g":0,"b":0}}},{"type":"BorderBottomColor","data":{"srgb":{"r":0,"g":0,"b":0},"original":{"r":0,"g":0,"b":0}}},{"type":"BorderLeftColor","data":{"srgb":{"r":0,"g":0,"b":0},"original":{"r":0,"g":0,"b":0}}},{"type":"BackgroundColor","data":{"srgb":{"r":0,"g":0,"b":0,"a":0},"original":{"r":0,"g":0,"b":0,"a":0}}},{"type":"Display","data":"BLOCK"},{"type":"OverflowX","data":"VISIBLE"},{"type":"OverflowY","data":"VISIBLE"}],"meta":{"sourceTag":"p"}}
        """

    /// css-flexbox/align-items-007 — the RC1 green (root 1), verbatim.
    private let alignItems007Root1 = """
        {"id":"wpt__css-flexbox__align-items-007__1-362","name":"wpt__css-flexbox__align-items-007__1","properties":[{"type":"Position","data":"ABSOLUTE"},{"type":"Width","data":{"type":"length","px":100}},{"type":"Height","data":{"type":"length","px":100}},{"type":"BackgroundColor","data":{"srgb":{"r":0,"g":0.5019607843137255,"b":0},"original":"green"}}],"meta":{"role":"ws-after"}}
        """

    /// css-images/gradient/gradient-hue-direction — root 4, the baked `<hr>`, verbatim.
    private let hueDirectionHr = """
        {"id":"wpt__css-images__gradient__gradient-hue-direction__4-070","name":"wpt__css-images__gradient__gradient-hue-direction__4","properties":[{"type":"Display","data":"BLOCK"},{"type":"Height","data":{"type":"length","px":0}},{"type":"BoxSizing","data":"CONTENT_BOX"},{"type":"BorderTopWidth","data":{"px":1}},{"type":"BorderRightWidth","data":{"px":1}},{"type":"BorderBottomWidth","data":{"px":1}},{"type":"BorderLeftWidth","data":{"px":1}},{"type":"BorderTopStyle","data":"INSET"},{"type":"BorderRightStyle","data":"INSET"},{"type":"BorderBottomStyle","data":"INSET"},{"type":"BorderLeftStyle","data":"INSET"},{"type":"BorderTopColor","data":{"srgb":{"r":0.9333333333333333,"g":0.9333333333333333,"b":0.9333333333333333},"original":"#eeeeee"}},{"type":"BorderRightColor","data":{"srgb":{"r":0.9333333333333333,"g":0.9333333333333333,"b":0.9333333333333333},"original":"#eeeeee"}},{"type":"BorderBottomColor","data":{"srgb":{"r":0.9333333333333333,"g":0.9333333333333333,"b":0.9333333333333333},"original":"#eeeeee"}},{"type":"BorderLeftColor","data":{"srgb":{"r":0.9333333333333333,"g":0.9333333333333333,"b":0.9333333333333333},"original":"#eeeeee"}},{"type":"MarginTop","data":{"px":8}},{"type":"MarginBottom","data":{"px":8}},{"type":"OverflowX","data":"HIDDEN"},{"type":"OverflowY","data":"HIDDEN"}],"meta":{"sourceTag":"hr","role":"ws-after","lang":"en"}}
        """

    /// selectors/has-style-sharing-002 — root 0 (em padding + em margins), verbatim.
    private let hasStyleSharing002Root0 = """
        {"id":"wpt__selectors__has-style-sharing-002__0-162","name":"wpt__selectors__has-style-sharing-002__0","properties":[{"type":"BackgroundColor","data":{"srgb":{"r":0,"g":0,"b":1},"original":"blue"}},{"type":"PaddingTop","data":{"original":{"v":1,"u":"EM"}}},{"type":"PaddingRight","data":{"original":{"v":1,"u":"EM"}}},{"type":"PaddingBottom","data":{"original":{"v":1,"u":"EM"}}},{"type":"PaddingLeft","data":{"original":{"v":1,"u":"EM"}}},{"type":"MarginTop","data":{"original":{"v":1,"u":"EM"}}},{"type":"MarginRight","data":{"original":{"v":1,"u":"EM"}}},{"type":"MarginBottom","data":{"original":{"v":1,"u":"EM"}}},{"type":"MarginLeft","data":{"original":{"v":1,"u":"EM"}}}],"meta":{"role":"ws-after"}}
        """

    /// CSS2/css21-errata/s-11-1-1b-006 — root 0, the `display: table` body-root, verbatim.
    private let s006BodyRoot = """
        {"id":"wpt__css2__css21-errata__s-11-1-1b-006__0-141","name":"wpt__CSS2__css21-errata__s-11-1-1b-006__0","properties":[{"type":"OverflowX","data":"HIDDEN"},{"type":"OverflowY","data":"HIDDEN"},{"type":"Display","data":"TABLE"},{"type":"BorderSpacing","data":{"type":"single","px":0}},{"type":"MarginTop","data":{"px":40}},{"type":"MarginRight","data":{"px":8}},{"type":"MarginBottom","data":{"px":8}},{"type":"MarginLeft","data":{"px":8}}],"meta":{"role":"body-root"}}
        """

    func testU1Ellipse006Rc1RootContributesZeroAndKeepsItsDeclaredMargins() throws {
        // U1 (T1). Chrome: <p> bottom margin 16 resolves in full ABOVE the
        // abspos slot, then the box's own margin-top 50 offsets its ink →
        // ellipse top at line box + 66 (ref y 118). Wave 19–51 joined the 50
        // into the set: max(16, 50) = 50, stripped → y 102, 16 px high.
        let roots = try rootsOf(ellipse006)
        // FixedHoist.split keeps the no-inset abspos root in the FLOW list.
        let split = FixedHoist.split(roots: roots)
        XCTAssertEqual(split.flow.count, 2)
        XCTAssertTrue(split.hoisted.isEmpty)
        let plans = split.flow.map { UABlockMargin.composedRootStackPlan($0) }
        // The <p>: UA 16/16, opaque, never stripped (R1).
        XCTAssertEqual(plans[0], .init(top: 16, bottom: 16, stripDeclared: false))
        // The RC1 root: (0,0), transparent, NOT stripped — its 50 renders on the box.
        XCTAssertEqual(plans[1], .init(top: 0, bottom: 0, stripDeclared: false, marginTransparent: true))
        // Gap above the slot is the <p>'s full 16; slot + own 50 = 66 below the <p>.
        let spacing = UABlockMargin.stackedSpacing(plans: plans)
        XCTAssertEqual(spacing.leading, [16, 16])
        XCTAssertEqual(spacing.trailing, 0)
        XCTAssertEqual(spacing.leading[1] + 50, 66)
    }

    func testU2PropagationShadowEmptyRootsCollapseThrough() throws {
        // U2 (T2-roots). EMPTY <p> (h0, 16/16), the text block (0/0), EMPTY
        // <p> (h0, 16/16). §8.3.1 collapses the empty box's margins through
        // it → ONE 16-px gap above the text (ref y 35 = 16 frame + 16 + 3
        // line-box). The opaque fold emitted 16 + 16 → y 51 (ios f 0.9484).
        let plans = try rootsOf(propagationShadow).map { UABlockMargin.composedRootStackPlan($0) }
        XCTAssertEqual(plans[0], .init(top: 16, bottom: 16, stripDeclared: true, marginTransparent: true))
        XCTAssertEqual(plans[1], .init(top: 0, bottom: 0, stripDeclared: true, marginTransparent: false))
        XCTAssertEqual(plans[2], .init(top: 16, bottom: 16, stripDeclared: true, marginTransparent: true))
        // [above p0, above text, above p2] + trailing: text sits 16 below the
        // padded top; the trailing 16 is emitted above p2's slot.
        let spacing = UABlockMargin.stackedSpacing(plans: plans)
        XCTAssertEqual(spacing.leading, [16, 0, 16])
        XCTAssertEqual(spacing.trailing, 0)
        // Mutation M2 (predicate forced false) gives [16, 16, 16] + 16 — 32 px.
    }

    func testU3AlignItems007Rc1RootIsTheStaticPositionSlot() throws {
        // U3 (T3 consumer). Root 1 (abspos, no inset) is the RC1 slot — with
        // no declared z and no content, the one the VStack lifts with
        // `.zIndex(1)` (testU13 pins the lift); its plan is shape 1.
        let root = try rootsOf(alignItems007Root1)[0]
        XCTAssertTrue(FixedHoist.rendersInFlowAsStaticPosition(root))
        XCTAssertEqual(UABlockMargin.composedRootStackPlan(root),
                       .init(top: 0, bottom: 0, stripDeclared: false, marginTransparent: true))
    }

    func testU4EmptyPredicateAcceptsThePostLoadHeightZeroParagraph() throws {
        // U4. No text/children, Display BLOCK, Height 0, block edges 0, static.
        let roots = try rootsOf(propagationShadow)
        XCTAssertTrue(UABlockMargin.isSelfCollapsingRoot(roots[0]))
        // The text block has text → not empty.
        XCTAssertFalse(UABlockMargin.isSelfCollapsingRoot(roots[1]))
    }

    func testU5EmptyPredicateRefusesBorderedTableAndPaddedRoots() throws {
        // U5 — the census's at-risk rows stay OPAQUE:
        //  gradient-hue-direction's <hr>: Height 0 but a 1-px inset border.
        XCTAssertFalse(UABlockMargin.isSelfCollapsingRoot(try rootsOf(hueDirectionHr)[0]))
        //  has-style-sharing-002 root 0: `padding: 1em` (non-zero, unresolvable).
        XCTAssertFalse(UABlockMargin.isSelfCollapsingRoot(try rootsOf(hasStyleSharing002Root0)[0]))
        //  s-11-1-1b-006's body-root: `display: table` AND the body-root role.
        XCTAssertFalse(UABlockMargin.isSelfCollapsingRoot(try rootsOf(s006BodyRoot)[0]))
        //  ellipse-006's abspos root: out of flow → its own rule, not this one.
        XCTAssertFalse(UABlockMargin.isSelfCollapsingRoot(try rootsOf(ellipse006)[1]))
    }

    func testU6PlanBuilderMatchesTheLegacyClosureOnTheR1Paragraph() throws {
        // U6 — byte-compat: an ordinary prose <p> root is R1 exactly as
        // before — UA 16/16, opaque, no strip, band 0 — so every prose-only
        // capture is unmoved.
        let p = try rootsOf(ellipse006)[0]
        XCTAssertEqual(UABlockMargin.composedRootStackPlan(p), .init(top: 16, bottom: 16, stripDeclared: false))
    }
    // MARK: - wave-52 lane L2 — predicate guards found by the corpus census

    /// css-counter-styles/counter-name-case-sensitive — root 1, verbatim:
    /// a div with no text whose `::before { content: "1-5" }` is its ink.
    private let counterNameRoot1 = """
        {"id":"wpt__css-counter-styles__counter-name-case-sensitive__1-514","name":"wpt__css-counter-styles__counter-name-case-sensitive__1","properties":[{"type":"CounterIncrement","data":[{"name":"foo"}]}],"pseudos":{"before":{"properties":{"content":"\\"1\\" \\"-\\" \\"5\\""},"_text":"1-5","_lossy":true,"_lossyReasons":["generated-content-baked"]}},"meta":{"role":"ws-after","lang":"en-US"}}
        """

    /// selectors/invalidation/any-link-attribute-removal — root 0, verbatim:
    /// an empty `display: inline` `<a>` with every block edge zero.
    private let anyLinkRoot0 = """
        {"id":"wpt__selectors__invalidation__any-link-attribute-removal__0-220","name":"wpt__selectors__invalidation__any-link-attribute-removal__0","properties":[{"type":"TextDecorationLine","data":["UNDERLINE"]},{"type":"Position","data":"STATIC"},{"type":"BoxSizing","data":"CONTENT_BOX"},{"type":"MarginTop","data":{"px":0}},{"type":"MarginRight","data":{"px":0}},{"type":"MarginBottom","data":{"px":0}},{"type":"MarginLeft","data":{"px":0}},{"type":"PaddingTop","data":{"px":0}},{"type":"PaddingRight","data":{"px":0}},{"type":"PaddingBottom","data":{"px":0}},{"type":"PaddingLeft","data":{"px":0}},{"type":"BorderTopWidth","data":{"px":0}},{"type":"BorderRightWidth","data":{"px":0}},{"type":"BorderBottomWidth","data":{"px":0}},{"type":"BorderLeftWidth","data":{"px":0}},{"type":"BorderTopStyle","data":"NONE"},{"type":"BorderRightStyle","data":"NONE"},{"type":"BorderBottomStyle","data":"NONE"},{"type":"BorderLeftStyle","data":"NONE"},{"type":"BorderTopColor","data":{"srgb":{"r":0,"g":0,"b":0},"original":{"r":0,"g":0,"b":0}}},{"type":"BorderRightColor","data":{"srgb":{"r":0,"g":0,"b":0},"original":{"r":0,"g":0,"b":0}}},{"type":"BorderBottomColor","data":{"srgb":{"r":0,"g":0,"b":0},"original":{"r":0,"g":0,"b":0}}},{"type":"BorderLeftColor","data":{"srgb":{"r":0,"g":0,"b":0},"original":{"r":0,"g":0,"b":0}}},{"type":"BackgroundColor","data":{"srgb":{"r":0,"g":0,"b":0,"a":0},"original":{"r":0,"g":0,"b":0,"a":0}}},{"type":"Display","data":"INLINE"},{"type":"OverflowX","data":"VISIBLE"},{"type":"OverflowY","data":"VISIBLE"}],"meta":{"sourceTag":"a"}}
        """

    /// css-gaps/flex/flex-gap-decorations-027 — root 0 (body-root), verbatim.
    private let gaps027Body = """
        {"id":"wpt__css-gaps__flex__flex-gap-decorations-027__0-193","name":"wpt__css-gaps__flex__flex-gap-decorations-027__0","properties":[{"type":"MarginTop","data":{"px":0}},{"type":"MarginRight","data":{"px":0}},{"type":"MarginBottom","data":{"px":0}},{"type":"MarginLeft","data":{"px":200}}],"meta":{"role":"body-root"}}
        """

    /// CSS2/css21-errata/s-11-1-1b-005 — root 0 (`display: table-cell` body), verbatim.
    private let s005Body = """
        {"id":"wpt__css2__css21-errata__s-11-1-1b-005__0-139","name":"wpt__CSS2__css21-errata__s-11-1-1b-005__0","properties":[{"type":"OverflowX","data":"HIDDEN"},{"type":"OverflowY","data":"HIDDEN"},{"type":"Display","data":"TABLE_CELL"},{"type":"BorderSpacing","data":{"type":"single","px":0}},{"type":"BackgroundColor","data":{"srgb":{"r":1,"g":1,"b":1},"original":"white"}},{"type":"MarginTop","data":{"px":-15}},{"type":"MarginRight","data":{"px":8}},{"type":"MarginBottom","data":{"px":8}},{"type":"MarginLeft","data":{"px":8}},{"type":"Width","data":{"type":"length","px":20}},{"type":"Height","data":{"type":"length","px":20}}],"meta":{"role":"body-root"}}
        """

    /// css-tables/collapsed-border-vertical-rtl-overflow — root 0 (body-root), verbatim.
    private let collapsedBorderBody = """
        {"id":"wpt__css-tables__collapsed-border-vertical-rtl-overflow__0-207","name":"wpt__css-tables__collapsed-border-vertical-rtl-overflow__0","properties":[{"type":"MarginTop","data":{"px":60}},{"type":"MarginRight","data":{"px":60}},{"type":"MarginBottom","data":{"px":60}},{"type":"MarginLeft","data":{"px":60}}],"meta":{"role":"body-root"}}
        """

    func testU7EmptyPredicateRefusesGeneratedContent() throws {
        // U7 — a text-less div whose ::before paints "1-5" has line boxes
        // (§8.3.1 "no line boxes" fails): it must stay OPAQUE. 35 corpus
        // roots carry a pseudo and match every other clause (M3 red).
        XCTAssertFalse(UABlockMargin.isSelfCollapsingRoot(try rootsOf(counterNameRoot1)[0]))
    }

    func testU8EmptyPredicateRefusesANonBlockDisplay() throws {
        // U8 — an empty `display: inline` <a>: not a §8.3.1 block box, so the
        // Display guard alone keeps it opaque (M4 red).
        XCTAssertFalse(UABlockMargin.isSelfCollapsingRoot(try rootsOf(anyLinkRoot0)[0]))
    }

    func testU9CanvasBodyMarginReadsTheConcreteSides() throws {
        // U9 (M1) — 027's `body { margin: 0 0 0 200px }` → the flow inset 216.
        XCTAssertEqual(UABlockMargin.canvasBodyMargin(try rootsOf(gaps027Body)),
                       .init(top: 0, right: 0, bottom: 0, left: 200))
        // s-11-1-1b-006's `display: table` body keeps all four (§8.3 names table).
        XCTAssertEqual(UABlockMargin.canvasBodyMargin(try rootsOf(s006BodyRoot)),
                       .init(top: 40, right: 8, bottom: 8, left: 8))
    }

    func testU10TableCellBodyHasNoUsedMargin() throws {
        // U10 (M1) — CSS 2.1 §8.3: margins do not apply to table-internal
        // boxes; s-11-1-1b-005's table-cell body resolves to zero (M5 red),
        // and zero owns nothing — the same roots back.
        let roots = try rootsOf(s005Body)
        XCTAssertEqual(UABlockMargin.canvasBodyMargin(roots), .zero)
        XCTAssertEqual(UABlockMargin.withCanvasOwnedBodyMargin(roots, .zero).map(\.properties.count),
                       roots.map(\.properties.count))
    }

    func testU11OneOwnerStripsTheBodyRootFoldGaps() throws {
        // U11 (M1) — collapsed-border's `margin: 60px` body: through wave 51
        // the fold emitted its 60/60 as gaps; once the canvas pads the flow
        // stack by 60, the body-root composes without them (M6 red).
        let roots = try rootsOf(collapsedBorderBody)
        let m = UABlockMargin.canvasBodyMargin(roots)
        XCTAssertEqual(m, .init(top: 60, right: 60, bottom: 60, left: 60))
        XCTAssertEqual(UABlockMargin.composedRootStackPlan(roots[0]),
                       .init(top: 60, bottom: 60, stripDeclared: true))
        let owned = UABlockMargin.withCanvasOwnedBodyMargin(roots, m)
        XCTAssertFalse(owned[0].properties.contains { $0.type.hasPrefix("Margin") })
        XCTAssertEqual(UABlockMargin.composedRootStackPlan(owned[0]),
                       .init(top: 0, bottom: 0, stripDeclared: false))
    }
    /// CSS2/css21-errata/s-11-1-1b-006 — root 3, verbatim: the inset abspos `<p>`.
    private let s006InsetP = """
        {"id":"wpt__css2__css21-errata__s-11-1-1b-006__3-144","name":"wpt__CSS2__css21-errata__s-11-1-1b-006__3","properties":[{"type":"Position","data":"ABSOLUTE"},{"type":"Top","data":{"px":0}},{"type":"Left","data":{"px":8}}],"text":"Test passes if there is a black square below.","meta":{"sourceTag":"p"}}
        """

    func testU12T6InsetAbsposPGetsItsUaBlockMargin() throws {
        // U12 (T6). CSS 2.1 §9.3.2: `top: 0` offsets the MARGIN edge — the
        // <p>'s border box sits 16 below the ICB edge. The overlay never sees
        // the fold's UA margins, so the root gets UA 16/16 declared (M7 red).
        let p = try rootsOf(s006InsetP)[0]
        let out = UABlockMargin.withUaBlockMarginOnHoistedRoot(p)
        XCTAssertEqual(out.properties.map(\.type), ["Position", "Top", "Left", "MarginTop", "MarginBottom"])
        // Stop here (a recorded failure, not an index trap) when the two
        // longhands are missing — e.g. under mutation M7.
        guard out.properties.count == 5 else { return }
        XCTAssertEqual(out.properties[3].data["px"]?.doubleValue, 16)
        XCTAssertEqual(out.properties[4].data["px"]?.doubleValue, 16)
        // The RC1 root (no inset) and the in-flow <p> are not this rule's.
        let ellipse = try rootsOf(ellipse006)
        XCTAssertEqual(UABlockMargin.withUaBlockMarginOnHoistedRoot(ellipse[1]).properties.count, ellipse[1].properties.count)
        XCTAssertEqual(UABlockMargin.withUaBlockMarginOnHoistedRoot(ellipse[0]).properties.count, ellipse[0].properties.count)
    }

    // MARK: - wave-52 lane L2 — T3 skeptic fix: WHICH flow roots the VStack lifts
    //
    // `UABlockMargin.composedRootsPaintingAboveFlow` on four more verbatim
    // wave51-fix docs (byte-parallel with UaBlockMarginsTest u10–u12). The
    // first cut lifted every RC1 root.
    //
    // EXECUTED MUTATIONS (ComposedRootStack.swift, this class run alone under
    // Catalyst, restored byte-exact — sha256 checked; mutations.log carries
    // the mutated text and the failing assertion):
    //   M8  rule 2 dropped (`.paint.zIndex == nil` → `true`)      → testU13 red.
    //   M9  rule 4 dropped (`paintsAsLayer(flow[j])` → `false`)   → testU14 red.
    //   M10 rule 3 dropped (`(root.children ?? []).isEmpty` → `true`) → testU15 red.

    /// css-flexbox/align-items-007 — all four components, verbatim (the positive control).
    private let alignItems007 = """
        {"id":"wpt__css-flexbox__align-items-007__0-361","name":"wpt__css-flexbox__align-items-007__0","properties":[],"text":"Test passes if there is a filled green square and no red.","meta":{"sourceTag":"p","role":"ws-after"}},
        {"id":"wpt__css-flexbox__align-items-007__1-362","name":"wpt__css-flexbox__align-items-007__1","properties":[{"type":"Position","data":"ABSOLUTE"},{"type":"Width","data":{"type":"length","px":100}},{"type":"Height","data":{"type":"length","px":100}},{"type":"BackgroundColor","data":{"srgb":{"r":0,"g":0.5019607843137255,"b":0},"original":"green"}}],"meta":{"role":"ws-after"}},
        {"id":"wpt__css-flexbox__align-items-007__2-363","name":"wpt__css-flexbox__align-items-007__2","properties":[{"type":"Display","data":"FLEX"},{"type":"FlexDirection","data":"COLUMN"},{"type":"Width","data":{"type":"length","px":100}},{"type":"Height","data":{"type":"length","px":100}},{"type":"LineHeight","data":{"original":{"type":"length","px":20}}},{"type":"AlignItems","data":"CENTER"}]},
        {"id":"align-items-007__2__0-364","name":"align-items-007__2__0","properties":[{"type":"Width","data":{"type":"length","px":100}},{"type":"Height","data":{"type":"length","px":100}}],"slot":{"parent":"wpt__css-flexbox__align-items-007__2-363"},"meta":{"sourceTag":"img","attrs":{"src":"css/support/red-rect.svg"}}}
        """

    /// css-tables/height-distribution/extra-height-given-to-all-row-groups-001 — all 7 components, verbatim.
    private let extraHeight001 = """
        {"id":"wpt__css-tables__height-distribution__extra-height-given-to-all-row-groups-001__0-254","name":"wpt__css-tables__height-distribution__extra-height-given-to-all-row-groups-001__0","properties":[],"text":"Test passes if there is a filled green square and no red.","meta":{"sourceTag":"p","role":"ws-after"}},
        {"id":"wpt__css-tables__height-distribution__extra-height-given-to-all-row-groups-001__1-255","name":"wpt__css-tables__height-distribution__extra-height-given-to-all-row-groups-001__1","properties":[{"type":"Height","data":{"type":"length","px":100}},{"type":"Width","data":{"type":"length","px":100}},{"type":"BackgroundColor","data":{"srgb":{"r":1,"g":0,"b":0},"original":"red"}},{"type":"Position","data":"ABSOLUTE"},{"type":"ZIndex","data":{"value":-1,"original":{"type":"integer","value":-1}}}],"meta":{"role":"ws-after"}},
        {"id":"wpt__css-tables__height-distribution__extra-height-given-to-all-row-groups-001__2-256","name":"wpt__css-tables__height-distribution__extra-height-given-to-all-row-groups-001__2","properties":[{"type":"BackgroundColor","data":{"srgb":{"r":0,"g":0.5019607843137255,"b":0},"original":"green"}},{"type":"BorderCollapse","data":"COLLAPSE"},{"type":"Height","data":{"type":"length","px":100}}],"meta":{"sourceTag":"table"}},
        {"id":"height-distribution__extra-height-given-to-all-row-groups-001__2__0-257","name":"height-distribution__extra-height-given-to-all-row-groups-001__2__0","properties":[],"slot":{"parent":"wpt__css-tables__height-distribution__extra-height-given-to-all-row-groups-001__2-256"},"meta":{"sourceTag":"thead"}},
        {"id":"height-distribution__extra-height-given-to-all-row-groups-001__2__0__0-258","name":"height-distribution__extra-height-given-to-all-row-groups-001__2__0__0","properties":[],"slot":{"parent":"height-distribution__extra-height-given-to-all-row-groups-001__2__0-257"},"meta":{"sourceTag":"tr"}},
        {"id":"height-distribution__extra-height-given-to-all-row-groups-001__2__0__0__0-259","name":"height-distribution__extra-height-given-to-all-row-groups-001__2__0__0__0","properties":[{"type":"PaddingTop","data":{"px":0}},{"type":"PaddingRight","data":{"px":0}},{"type":"PaddingBottom","data":{"px":0}},{"type":"PaddingLeft","data":{"px":0}}],"slot":{"parent":"height-distribution__extra-height-given-to-all-row-groups-001__2__0__0-258"},"meta":{"sourceTag":"td"}},
        {"id":"height-distribution__extra-height-given-to-all-row-groups-001__2__0__0__0__0-260","name":"height-distribution__extra-height-given-to-all-row-groups-001__2__0__0__0__0","properties":[{"type":"Display","data":"INLINE_BLOCK"},{"type":"Width","data":{"type":"length","px":100}}],"slot":{"parent":"height-distribution__extra-height-given-to-all-row-groups-001__2__0__0__0-259"}}
        """

    /// CSS2/abspos/static-inside-inline-001 — all 5 components, verbatim (root 2 nests an abspos green).
    private let staticInsideInline001 = """
        {"id":"wpt__css2__abspos__static-inside-inline-001__0-019","name":"wpt__CSS2__abspos__static-inside-inline-001__0","properties":[],"text":"Test passes if there is a filled green square and no red.","meta":{"sourceTag":"p","role":"ws-after"}},
        {"id":"wpt__css2__abspos__static-inside-inline-001__1-020","name":"wpt__CSS2__abspos__static-inside-inline-001__1","properties":[{"type":"Position","data":"ABSOLUTE"},{"type":"Width","data":{"type":"length","px":100}},{"type":"Height","data":{"type":"length","px":100}},{"type":"BackgroundColor","data":{"srgb":{"r":1,"g":0,"b":0},"original":"red"}}],"meta":{"role":"ws-after"}},
        {"id":"wpt__css2__abspos__static-inside-inline-001__2-021","name":"wpt__CSS2__abspos__static-inside-inline-001__2","properties":[{"type":"OverflowX","data":"HIDDEN"},{"type":"OverflowY","data":"HIDDEN"},{"type":"Width","data":{"type":"length","px":100}},{"type":"Height","data":{"type":"length","px":100}}]},
        {"id":"abspos__static-inside-inline-001__2__0-022","name":"abspos__static-inside-inline-001__2__0","properties":[{"type":"LineHeight","data":{"original":{"type":"length","px":100}}},{"type":"Color","data":{"srgb":{"r":0,"g":0,"b":0,"a":0},"original":"transparent"}}],"slot":{"parent":"wpt__css2__abspos__static-inside-inline-001__2-021"},"text":"X","meta":{"sourceTag":"span","runs":[{"child":"abspos__static-inside-inline-001__2__0__0"},{"text":" X"}]}},
        {"id":"abspos__static-inside-inline-001__2__0__0-023","name":"abspos__static-inside-inline-001__2__0__0","properties":[{"type":"Position","data":"ABSOLUTE"},{"type":"BackgroundColor","data":{"srgb":{"r":0,"g":0.5019607843137255,"b":0},"original":"green"}},{"type":"Width","data":{"type":"length","px":100}},{"type":"Height","data":{"type":"length","px":100}}],"slot":{"parent":"abspos__static-inside-inline-001__2__0-022"}}
        """

    /// css-cascade/unset-val-002 — all 4 components, verbatim (root 1 holds a `display: unset` span).
    private let unsetVal002 = """
        {"id":"wpt__css-cascade__unset-val-002__0-150","name":"wpt__css-cascade__unset-val-002__0","properties":[],"text":"Test passes if there is a filled green square and no red.","meta":{"sourceTag":"p","role":"ws-after"}},
        {"id":"wpt__css-cascade__unset-val-002__1-151","name":"wpt__css-cascade__unset-val-002__1","properties":[{"type":"Position","data":"ABSOLUTE"}],"meta":{"role":"ws-after"}},
        {"id":"unset-val-002__1__0-152","name":"unset-val-002__1__0","properties":[{"type":"Width","data":{"type":"length","px":100}},{"type":"Height","data":{"type":"length","px":100}},{"type":"BackgroundColor","data":{"srgb":{"r":1,"g":0,"b":0},"original":"red"}},{"type":"Generic","data":{"propertyName":"display","rawValue":"unset","_unmapped":true}}],"slot":{"parent":"wpt__css-cascade__unset-val-002__1-151"},"meta":{"sourceTag":"span"}},
        {"id":"wpt__css-cascade__unset-val-002__2-153","name":"wpt__css-cascade__unset-val-002__2","properties":[{"type":"Width","data":{"type":"length","px":100}},{"type":"Height","data":{"type":"length","px":100}},{"type":"BackgroundColor","data":{"srgb":{"r":0,"g":0.5019607843137255,"b":0},"original":"green"}}]}
        """

    /// css-masking/clip-path/clip-path-path-with-zoom — both roots, verbatim (the green is a clip-path layer).
    private let clipPathWithZoom = """
        {"id":"wpt__css-masking__clip-path__clip-path-path-with-zoom__0-099","name":"wpt__css-masking__clip-path__clip-path-path-with-zoom__0","properties":[{"type":"Position","data":"ABSOLUTE"},{"type":"Width","data":{"type":"length","px":100}},{"type":"Height","data":{"type":"length","px":100}},{"type":"BackgroundColor","data":{"srgb":{"r":1,"g":0,"b":0},"original":"red"}}],"meta":{"role":"ws-after"}},
        {"id":"wpt__css-masking__clip-path__clip-path-path-with-zoom__1-100","name":"wpt__css-masking__clip-path__clip-path-path-with-zoom__1","properties":[{"type":"Width","data":{"type":"length","px":100}},{"type":"Height","data":{"type":"length","px":100}},{"type":"BackgroundColor","data":{"srgb":{"r":0,"g":0.5019607843137255,"b":0},"original":"green"}},{"type":"ClipPath","data":{"type":"path","d":"M0,0 L100,0  L0,100  L0,0","rule":"nonzero"}},{"type":"Zoom","data":{"type":"number","value":2}}]}
        """

    /// The lift verdicts over a verbatim doc's FLOW half — the list CaptureCanvas walks.
    private func liftsOf(_ json: String) throws -> [Bool] {
        UABlockMargin.composedRootsPaintingAboveFlow(FixedHoist.split(roots: try rootsOf(json)).flow)
    }

    func testU13ExtraHeight001NegativeZRc1RootIsNotLifted() throws {
        // U13 (rule 2). Root 1: red `position: absolute; z-index: -1`, the RC1
        // slot (its plan is still shape 1), then the green table. Appendix E
        // step 3 paints it BELOW in-flow blocks; the first cut lifted it.
        let flow = FixedHoist.split(roots: try rootsOf(extraHeight001)).flow
        XCTAssertEqual(flow.map { FixedHoist.rendersInFlowAsStaticPosition($0) }, [false, true, false])
        XCTAssertEqual(try liftsOf(extraHeight001), [false, false, false])
        // The positive control: align-items-007's undeclared-z green IS lifted.
        XCTAssertEqual(try liftsOf(alignItems007), [false, true, false])
    }

    func testU14LaterStep8ContentKeepsTheRc1RootBelow() throws {
        // U14 (rule 4). static-inside-inline-001: root 2 nests an abspos green
        // that follows the red RC1 box in TREE order at step 8 — it must win.
        XCTAssertEqual(try liftsOf(staticInsideInline001), [false, false, false])
        // clip-path-path-with-zoom: the later green is a clip-path layer.
        XCTAssertEqual(try liftsOf(clipPathWithZoom), [false, false])
    }

    func testU15ContentBearingRc1RootIsNotLifted() throws {
        // U15 (rule 3). unset-val-002's root 1 paints only through its child span.
        XCTAssertEqual(try liftsOf(unsetVal002), [false, false, false])
    }
}
