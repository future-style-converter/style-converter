//
//  PropertyTracker.swift
//  StyleEngine/core/dynamic — wave 7 (dynamic styling, issues #33/#34).
//
//  The iOS arm of the repo-wide no-silent-fallthrough rule
//  (CLAUDE.md "Hard rules"): when the runtime meets something it
//  deliberately does NOT implement — an unsupported selector condition,
//  a media query outside the runtime-v1 grammar (spec 06 §2/§4), a
//  light-dark() arm it cannot parse — the miss is LOGGED once and the
//  input stays inert. Never a crash, never an apply-by-guess, never a
//  silent drop.
//
//  "Once" is once per unique key per process: spec 06 asks for once per
//  condition per document; the harness renders one document per process
//  so process-lifetime dedupe implements that without threading a
//  document identity through the pure resolver.
//

import Foundation

/// Process-wide, thread-safe, once-per-key breadcrumb logger.
// enum: pure namespace — never instantiated (mirrors StyleBuilder et al.).
enum PropertyTracker {

    /// Keys already reported this process — the dedupe set.
    private static var seen = Set<String>()

    /// Guards `seen`: SwiftUI may resolve styles from multiple render
    /// passes; NSLock keeps the set mutation race-free at trivial cost.
    private static let lock = NSLock()

    /// Log `message` to stderr exactly once for a given `key`.
    /// Returns true when this call actually emitted (first sighting) —
    /// callers never need it for control flow, but tests pin the dedupe.
    @discardableResult
    static func logOnce(key: String, message: String) -> Bool {
        // Take the lock for the whole test-and-insert so two threads
        // can't both think they are the first sighting.
        lock.lock()
        defer { lock.unlock() }
        // Second sighting → dedupe: stay silent, report nothing.
        guard !seen.contains(key) else { return false }
        seen.insert(key)
        // stderr, not print: capture pipelines read stdout for progress
        // and the deprecation warnings already go to stderr (IRModels).
        FileHandle.standardError.write(Data(
            "[PropertyTracker] \(message)\n".utf8))
        return true
    }

    /// Test hook: clear the dedupe set so XCTest cases can pin the
    /// once-per-key behaviour independently of execution order.
    static func _resetForTests() {
        // Same lock discipline as logOnce — tests may run in parallel.
        lock.lock()
        defer { lock.unlock() }
        seen.removeAll()
    }
}
