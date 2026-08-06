import XCTest
@testable import StyleConverterRuntime

/// Wave-32 lane R — the `meta.runs` render plan (Renderer/InlineRunPlan.swift).
///
/// WHY THIS EXISTS. `text` is ONE string and the child list has ONE order, so
/// the wire could say `run + children` or `children + run` but never
/// `run / child / run`. Through wave-31 the extractor marked the difference
/// loudly (`inline-run-reordered`, 1,203 components / 472 fixtures) and
/// shipped the concatenation. The canonical victim is
/// CSS2/abspos/static-inside-inline-001 — `<span><div id=abspos></div> X
/// </span>` — where the whole assertion is box-vs-text order: with the div
/// first the preceding inline fragment is empty (zero-height line box,
/// §9.4.2) and the abspos' static top is 0; with 'X' first it is 100.
///
/// The plan is a PURE fold of the wire list against the composed children,
/// which is why it is unit-testable without a simulator. It also carries the
/// one guard this platform's fold needs: the caller walks children in index
/// order, so a plan whose child references are OUT of that order cannot be
/// expressed as "runs before child i" and must REFUSE rather than paint the
/// wrong sequence.
final class InlineRunPlanTests: XCTestCase {

    private func comp(_ id: String, _ name: String) -> IRComponent {
        IRComponent(id: id, name: name, properties: [], selectors: nil, media: nil,
                    children: nil, slot: nil, text: nil, pseudos: nil, meta: nil)
    }

    private lazy var kids: [IRComponent] = [comp("a-id", "a-name"), comp("b-id", "b-name")]

    func testAbsentAndEmptyInputsFallBack() {
        // nil (not an empty plan) is what keeps every pre-wave-32 view tree
        // identical.
        XCTAssertNil(InlineRunPlan.resolve(nil, children: kids))
        XCTAssertNil(InlineRunPlan.resolve([], children: kids))
        XCTAssertNil(InlineRunPlan.resolve([IRRun(text: "x")], children: []))
    }

    func testChildResolvesByAuthoringKeyThenId() throws {
        // The reference is the child's `name` on the converter-emitted wire —
        // the converter mints ids at the flatten boundary, so a
        // producer-written id would name nothing after the hop.
        let byName = try XCTUnwrap(
            InlineRunPlan.resolve([IRRun(text: "lead"), IRRun(child: "b-name")], children: kids))
        XCTAssertEqual(byName.before[1] ?? [], ["lead"])
        // …and in the extractor-direct pipeline key == name == id, so the id
        // spelling must resolve identically rather than dangle.
        let byId = try XCTUnwrap(
            InlineRunPlan.resolve([IRRun(text: "lead"), IRRun(child: "a-id")], children: kids))
        XCTAssertEqual(byId.before[0] ?? [], ["lead"])
    }

    func testGlueCaseFoldsToBeforeAndTrailing() throws {
        // `the quick <u>brown</u> fox` with a surviving <u>: the shape the
        // single `text` string cannot express. The fold puts 'the quick '
        // before the child and ' fox' after the last one.
        let plan = try XCTUnwrap(InlineRunPlan.resolve(
            [IRRun(text: "the quick "), IRRun(child: "a-name"), IRRun(text: " fox")],
            children: kids))
        XCTAssertEqual(plan.before[0] ?? [], ["the quick "])
        XCTAssertEqual(plan.trailing, [" fox"])
    }

    func testChildBeforeTextIsTheCSS2Shape() throws {
        // `<span><div id=abspos></div> X </span>` — nothing before the child,
        // the text trailing it. That ordering is the quantity the WPT test
        // asserts, and the one the flat wire used to invert.
        let plan = try XCTUnwrap(InlineRunPlan.resolve(
            [IRRun(child: "a-name"), IRRun(text: " X")], children: kids))
        XCTAssertNil(plan.before[0])
        XCTAssertEqual(plan.trailing, [" X"])
    }

    func testWhitespaceOnlyRunSurvivesEmptyOneDropped() throws {
        // Rule 6 — a single space between two boxes IS the inter-run word
        // space and has real advance width; a zero-length run has neither
        // glyphs nor a box.
        let plan = try XCTUnwrap(InlineRunPlan.resolve(
            [IRRun(child: "a-name"), IRRun(text: " "), IRRun(text: ""), IRRun(child: "b-name")],
            children: kids))
        XCTAssertEqual(plan.before[1] ?? [], [" "])
        XCTAssertTrue(plan.trailing.isEmpty)
    }

    func testDanglingKeyIsSkippedNotThrown() throws {
        // Rule 5, mirroring the dangling-slot.parent rule: skip, never throw
        // — a droppable hint may not make a page unrenderable.
        let plan = try XCTUnwrap(InlineRunPlan.resolve(
            [IRRun(text: "a"), IRRun(child: "nobody"), IRRun(child: "b-name")],
            children: kids))
        // 'a' was pending when the dangling entry was skipped, so it still
        // lands before the NEXT real child.
        XCTAssertEqual(plan.before[1] ?? [], ["a"])
    }

    func testFullyDanglingListFallsBack() {
        // The honest failure mode: the wire asked for an order we could not
        // reconstruct, so paint the pre-wave-32 approximation, not nothing.
        XCTAssertNil(InlineRunPlan.resolve([IRRun(child: "nobody")], children: kids))
        // A list with no child reference at all says nothing the plain `text`
        // channel does not already say.
        XCTAssertNil(InlineRunPlan.resolve([IRRun(text: "just text")], children: kids))
    }

    func testOutOfOrderChildReferencesRefuse() {
        // THE PROOF the fold needs. The renderer walks children in index
        // order, so `[child b, text, child a]` cannot be expressed as "runs
        // before child i" — refusing puts the container back on the exact
        // pre-wave-32 path instead of painting the wrong sequence. (The
        // extractor emits document order, so this guard is about
        // FlexboxApplier.sorted permuting children by CSS `order`.)
        XCTAssertNil(InlineRunPlan.resolve(
            [IRRun(child: "b-name"), IRRun(text: "x"), IRRun(child: "a-name")],
            children: kids))
    }
}
