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
}

// Small convenience so the test reads cleanly on all SDKs.
private extension URL {
    init(fileURLToPath path: String) { self.init(fileURLWithPath: path) }
}
