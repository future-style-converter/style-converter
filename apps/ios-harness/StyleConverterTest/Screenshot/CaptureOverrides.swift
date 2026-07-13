//
//  CaptureOverrides.swift
//  StyleConverterTest
//
//  The iOS transports for the dynamic-styling capture hooks
//  (docs/DYNAMIC_CAPTURE.md; runtime semantics in
//  schema/spec/06-dynamic-styling.md). Three per-run knobs, all read
//  ONCE at launch (a capture run is one process — the hooks cannot
//  change mid-run, mirroring the web gallery's read-once constants):
//
//    forceState   -forceState <state> launch arg, or FORCE_STATE env
//                 (host shell: SIMCTL_CHILD_FORCE_STATE=… simctl launch)
//    captureWidth -captureWidth <px> launch arg, or CAPTURE_WIDTH env
//                 (SIMCTL_CHILD_CAPTURE_WIDTH=…) — default 390 stays
//                 byte-identical to every committed baseline
//    darkMode     -captureDark 1 launch arg, or CAPTURE_DARK=1 env —
//                 the forced-dark run of DYNAMIC_CAPTURE.md §3; default
//                 LIGHT is the standard capture contract
//    animationTime -animationTime <s> launch arg, or
//                 CAPTURE_ANIMATION_TIME env (host shell:
//                 SIMCTL_CHILD_CAPTURE_ANIMATION_TIME=… simctl launch) —
//                 the spec 07 §5 deterministic motion clock: every
//                 animation renders its state at absolute second t,
//                 paused. 0 is MEANINGFUL (the initial frame); unset
//                 keeps the historical live path byte-identical
//
//  `xcrun simctl launch` forwards SIMCTL_CHILD_* environment variables
//  to the app process, so the host recipe needs zero test-all.sh
//  changes — the env flows through the shell, same as the web lane.
//

import SwiftUI

enum CaptureOverrides {

    /// The runtime-v1 forced-state vocabulary (spec 06 §2) — the node
    /// and web gates validate against the same five names.
    static let validStates: Set<String> =
        ["hover", "active", "focus", "disabled", "checked"]

    /// Read one string knob: launch argument first (UserDefaults maps
    /// "-key value" arguments automatically), then the env fallback.
    private static func knob(argument: String, env: String) -> String? {
        // Launch-argument transport (DYNAMIC_CAPTURE.md iOS row).
        if let v = UserDefaults.standard.string(forKey: argument), !v.isEmpty {
            return v
        }
        // Environment transport (SIMCTL_CHILD_* from the host shell).
        if let v = ProcessInfo.processInfo.environment[env], !v.isEmpty {
            return v
        }
        return nil
    }

    /// The forced interaction state for this capture run, validated
    /// against the runtime-v1 set. Invalid values are dropped LOUDLY
    /// (stderr) rather than silently degrading to a base-state run —
    /// the same honesty rule the web capture script applies.
    static let forceState: String? = {
        guard let raw = knob(argument: "forceState", env: "FORCE_STATE") else {
            return nil
        }
        // Normalize author case; validate the vocabulary.
        let state = raw.lowercased()
        guard validStates.contains(state) else {
            FileHandle.standardError.write(Data(
                "[CaptureOverrides] invalid forceState '\(raw)' — expected one of \(validStates.sorted()); running BASE state\n".utf8))
            return nil
        }
        return state
    }()

    /// The forced set handed to the runtime's style resolution
    /// (spec 06 §6) — empty for a normal base-state run.
    static var forcedStates: Set<String> {
        forceState.map { [$0] } ?? []
    }

    /// Capture-canvas width in px. Default 390 = the committed-baseline
    /// contract (DYNAMIC_CAPTURE.md §2 — MUST stay byte-identical when
    /// unset); non-positive / non-numeric overrides fall back to 390
    /// rather than emit a zero-width canvas that blanks every capture.
    static let captureWidth: CGFloat = {
        guard let raw = knob(argument: "captureWidth", env: "CAPTURE_WIDTH"),
              let px = Int(raw), px > 0 else { return 390 }
        return CGFloat(px)
    }()

    /// Forced-dark run flag (§3). Default false = the LIGHT default
    /// scheme every platform captures under — dark buckets and dark
    /// light-dark() arms must NOT apply in the standard run.
    static let darkMode: Bool = {
        knob(argument: "captureDark", env: "CAPTURE_DARK") == "1"
    }()

    /// The scheme the capture canvas pins on its render environment.
    /// Pinned EXPLICITLY (not inherited) so ImageRenderer captures are
    /// deterministic regardless of app/system appearance — the harness
    /// chrome itself runs `.preferredColorScheme(.dark)`.
    static var colorScheme: ColorScheme {
        darkMode ? .dark : .light
    }

    /// The spec 07 §5 pinned motion clock (docs/DYNAMIC_CAPTURE.md §4,
    /// iOS transport row) — VALIDATED RAW STRING form. Validation
    /// mirrors the web capture script's gate byte-for-byte: finite and
    /// ≥ 0, where 0 is meaningful (the initial frame). Invalid values
    /// are dropped LOUDLY — a seized run must never silently degrade to
    /// a live capture. Kept as the authored string so the host-side
    /// marker check (test-all.sh grep against capture-config.json)
    /// compares the exact bytes the host exported ("1" must not become
    /// "1.0" on the round-trip).
    static let animationTimeRaw: String? = {
        guard let raw = knob(argument: "animationTime",
                             env: "CAPTURE_ANIMATION_TIME") else { return nil }
        guard let t = Double(raw), t.isFinite, t >= 0 else {
            FileHandle.standardError.write(Data(
                "[CaptureOverrides] invalid animationTime '\(raw)' — expected a finite number of seconds >= 0; running LIVE clock\n".utf8))
            return nil
        }
        return raw
    }()

    /// Numeric form for the runtime's animationCaptureTime environment.
    static var animationTime: Double? {
        animationTimeRaw.flatMap(Double.init)
    }

    // MARK: - TITAN Phase 3 inbox-polling activation
    //
    // The activation flag for the WPT feeder loop. It rides the SAME
    // transport rails as the dynamic-capture hooks above (launch arg OR
    // SIMCTL_CHILD_* env) so the host recipe needs no bespoke plumbing —
    // `tools/titan/feed-ios.mjs` launches with
    // `SIMCTL_CHILD_TITAN_INBOX=1` and `xcrun simctl launch` forwards it
    // into the app process environment, identical to the
    // SIMCTL_CHILD_CAPTURE_ANIMATION_TIME pattern.
    //
    // When true the app SKIPS the normal bundled-IR-from-Resources
    // auto-capture (ContentView routes to InboxCaptureView) and instead
    // polls Documents/inbox for host-pushed IR documents, rendering each
    // through the IDENTICAL CaptureCanvas + ImageRenderer + Screenshot
    // save path the auto-capture flow uses (see captureAllComponents in
    // ScreenshotCaptureView.swift). Default false keeps every existing
    // launch (test-all.sh / test-ios.sh) on the historical path.
    static let titanInbox: Bool = {
        isInboxActivated(knob(argument: "titanInbox", env: "TITAN_INBOX"))
    }()

    /// Pure activation predicate, split out so it is unit-testable without a
    /// device (InboxModeTests): only the literal "1" activates inbox mode —
    /// nil/empty/any other value keeps the historical auto-capture path.
    static func isInboxActivated(_ raw: String?) -> Bool {
        raw == "1"
    }

    // MARK: - TITAN WPT Round 3 composed-capture sub-flag
    //
    // A SUB-flag layered on TOP of inbox mode (titanInbox above). Rides the
    // same transport rails (launch arg OR SIMCTL_CHILD_* env) — the feeder
    // launches with `SIMCTL_CHILD_TITAN_COMPOSED=1` and `xcrun simctl
    // launch` forwards it, identical to SIMCTL_CHILD_TITAN_INBOX.
    //
    // When true AND inbox mode is active, InboxCaptureView renders each
    // host-pushed per-test doc as ONE COMPOSED capture (the whole doc's
    // roots on one browser-ref-framed ComposedCaptureCanvas →
    // `<safe(testKey)>.png`) instead of the legacy per-component captures
    // (`%03d_<name>.png`). This is the honest per-PAGE comparison footing
    // the web harness switched to (WPT_COMPOSED); the diff runs directly
    // against the browser-ref with no vertical stitch.
    //
    // Default false keeps the existing inbox path (per-component captures)
    // AND every non-inbox launch (test-all.sh / test-ios.sh bundled capture)
    // exactly as before — the committed 327-pair baseline is untouched
    // because it never sets titanInbox, let alone this.
    static let titanComposed: Bool = {
        isComposedActivated(knob(argument: "titanComposed", env: "TITAN_COMPOSED"))
    }()

    /// Pure activation predicate for the composed sub-flag, split out so it
    /// is unit-testable device-free (InboxModeTests): only the literal "1"
    /// activates composed mode — nil/empty/any other value keeps the
    /// per-component inbox capture. Same rule as isInboxActivated.
    static func isComposedActivated(_ raw: String?) -> Bool {
        raw == "1"
    }
}
