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
}
