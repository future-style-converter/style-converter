//
//  InboxModeTests.swift
//  StyleConverterTestTests
//
//  TITAN Phase 3 — device-free unit tests for the two pure pieces of the
//  inbox-mode app path: the activation-flag parse (CaptureOverrides.
//  isInboxActivated) and the FIFO poll ordering (ScreenshotManager.
//  orderOldestFirst). The end-to-end capture path is proven live by
//  tools/titan/feed-ios.mjs against the booted simulator; these lock the
//  logic that can be checked in isolation so a refactor can't silently
//  break activation or reorder the queue.
//

import XCTest
@testable import StyleConverterTest

final class InboxModeTests: XCTestCase {

    // MARK: - Activation flag (SIMCTL_CHILD_TITAN_INBOX / -titanInbox)

    func testOnlyLiteralOneActivatesInbox() {
        // The transport carries the raw string "1" when active.
        XCTAssertTrue(CaptureOverrides.isInboxActivated("1"))
        // Everything else keeps the historical auto-capture path.
        XCTAssertFalse(CaptureOverrides.isInboxActivated(nil))
        XCTAssertFalse(CaptureOverrides.isInboxActivated(""))
        XCTAssertFalse(CaptureOverrides.isInboxActivated("0"))
        XCTAssertFalse(CaptureOverrides.isInboxActivated("true"))
        XCTAssertFalse(CaptureOverrides.isInboxActivated(" 1"))
    }

    // MARK: - FIFO poll ordering

    func testOrderOldestFirstReturnsFifoOrder() {
        let base = Date(timeIntervalSince1970: 1_000_000)
        let a = URL(fileURLToPath: "/inbox/a.json")
        let b = URL(fileURLToPath: "/inbox/b.json")
        let c = URL(fileURLToPath: "/inbox/c.json")
        // Deliberately unsorted input: b newest, a oldest, c middle.
        let entries = [
            (url: b, date: base.addingTimeInterval(30)),
            (url: a, date: base.addingTimeInterval(0)),
            (url: c, date: base.addingTimeInterval(15)),
        ]
        let ordered = ScreenshotManager.orderOldestFirst(entries)
        // Oldest (a@+0) → middle (c@+15) → newest (b@+30).
        XCTAssertEqual(ordered.map { $0.lastPathComponent }, ["a.json", "c.json", "b.json"])
    }

    func testOrderOldestFirstEmptyIsEmpty() {
        XCTAssertTrue(ScreenshotManager.orderOldestFirst([]).isEmpty)
    }

    // MARK: - TITAN WPT Round 3 composed sub-flag (SIMCTL_CHILD_TITAN_COMPOSED)

    func testOnlyLiteralOneActivatesComposed() {
        // Same "only the literal 1" rule as the inbox activation flag.
        XCTAssertTrue(CaptureOverrides.isComposedActivated("1"))
        XCTAssertFalse(CaptureOverrides.isComposedActivated(nil))
        XCTAssertFalse(CaptureOverrides.isComposedActivated(""))
        XCTAssertFalse(CaptureOverrides.isComposedActivated("0"))
        XCTAssertFalse(CaptureOverrides.isComposedActivated("true"))
        XCTAssertFalse(CaptureOverrides.isComposedActivated(" 1"))
    }

    // MARK: - Composed PNG-name derivation

    func testComposedPngNameStripsJsonAndAppendsPng() {
        // The common case: a per-test-ir fixture filename (already all safe
        // chars) → the exact `<testKey>.png` diffComposedVsRef globs for.
        XCTAssertEqual(
            ScreenshotManager.composedPngName(
                forFixtureFilename: "wpt__css-color__background-color-hsl-001.json"),
            "wpt__css-color__background-color-hsl-001.png")
    }

    func testComposedPngNameSanitisesUnsafeChars() {
        // Non-[A-Za-z0-9._-] chars → '_', mirroring safe() in
        // inject-wpt-block.mjs. The trailing .json is stripped BEFORE
        // sanitising so the extension dot never leaks into the key.
        XCTAssertEqual(
            ScreenshotManager.composedPngName(forFixtureFilename: "a b/c:d.json"),
            "a_b_c_d.png")
        // A name without the .json suffix is sanitised as-is + .png.
        XCTAssertEqual(
            ScreenshotManager.composedPngName(forFixtureFilename: "plainkey"),
            "plainkey.png")
    }

    func testSafeCaptureNameMatchesCompareSanitiser() {
        // Dots, dashes, underscores survive; everything else → '_'.
        XCTAssertEqual(ScreenshotManager.safeCaptureName("keep.dots-and_dashes"),
                       "keep.dots-and_dashes")
        XCTAssertEqual(ScreenshotManager.safeCaptureName("Foo Bar:baz/qux"),
                       "Foo_Bar_baz_qux")
    }
}

// Small convenience so the test reads cleanly on all SDKs.
private extension URL {
    init(fileURLToPath path: String) { self.init(fileURLWithPath: path) }
}
