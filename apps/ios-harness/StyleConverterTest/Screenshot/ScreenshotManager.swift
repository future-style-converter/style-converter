//
//  ScreenshotManager.swift
//  StyleConverterTest
//
//  Saves per-component screenshots to the app's Documents directory using
//  SwiftUI's ImageRenderer (iOS 16+). The test-ios.sh script pulls them
//  out of the simulator with `xcrun simctl get_app_container`.
//
//  Mirrors apps/android-harness/.../screenshot/ScreenshotManager.kt.
//

import SwiftUI
import UIKit
// Lane BF-I: BackdropPlate + the `backdropPass` environment channel used by
// the two-pass backdrop render below come from the runtime package.
import StyleConverterRuntime

enum ScreenshotManager {

    /// Directory: <Documents>/test_screenshots/
    static var directory: URL {
        let docs = FileManager.default.urls(for: .documentDirectory, in: .userDomainMask)[0]
        let dir  = docs.appendingPathComponent("test_screenshots", isDirectory: true)
        try? FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
        return dir
    }

    /// Reset capture directory.
    static func reset() {
        let fm = FileManager.default
        try? fm.removeItem(at: directory)
        try? fm.createDirectory(at: directory, withIntermediateDirectories: true)
    }

    /// Save a UIImage as `{index}_{componentName}.png`.
    static func save(image: UIImage, index: Int, name: String) {
        let sanitized = name
            .replacingOccurrences(of: "/", with: "_")
            .replacingOccurrences(of: " ", with: "_")
        let filename = String(format: "%03d_%@.png", index, sanitized)
        let url = directory.appendingPathComponent(filename)
        if let data = image.pngData() {
            // `.atomic` writes to a temp file then renames, so a host
            // reader (the TITAN feeder polling this dir) can never observe
            // a half-written PNG and pull a truncated capture. The final
            // bytes are identical to a plain write, so committed baselines
            // are unaffected — this only closes the read-during-write race
            // the inbox feeder's tight poll loop would otherwise hit.
            try? data.write(to: url, options: .atomic)
        }
    }

    // MARK: - TITAN WPT Round 3 composed capture

    /// Compare-pipeline filename sanitiser — mirrors `safe()` in
    /// tools/titan/inject-wpt-block.mjs and `safeName()` in
    /// tools/titan/feed-ios.mjs: every char OUTSIDE `[A-Za-z0-9._-]`
    /// becomes `_`. The composed WPT capture writes `<safe(testKey)>.png`,
    /// which is exactly the name the orchestrator's diffComposedVsRef globs
    /// for, so this rule MUST match those two byte-for-byte. Pure + static
    /// so InboxModeTests pins it without a device.
    static func safeCaptureName(_ name: String) -> String {
        // The literal allow-set of the JS regex class `[A-Za-z0-9._-]`.
        let allowed = Set(
            "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789._-")
        return String(name.map { allowed.contains($0) ? $0 : "_" })
    }

    /// Derive the composed-capture PNG filename from an inbox fixture's
    /// filename. The feeder (tools/titan/feed-ios.mjs, composed mode) pushes
    /// each per-test doc named `<testKey>.json` where testKey =
    /// `wpt__<section>__<stem>`; the composed capture writes ONE PNG named
    /// `<safe(testKey)>.png`. Deriving the name from the inbox filename
    /// keeps the testKey out of the IR payload (the feeder already knows it
    /// from the per-test-ir filename) and gives the name ONE derivation
    /// home. Pure so InboxModeTests pins it device-free.
    static func composedPngName(forFixtureFilename filename: String) -> String {
        // Strip exactly one trailing ".json" (the inbox extension); any
        // other suffix is left intact then sanitised.
        var key = filename
        if key.hasSuffix(".json") { key.removeLast(".json".count) }
        return safeCaptureName(key) + ".png"
    }

    /// Save the single composed-document capture under its testKey-derived
    /// name. Unlike `save` (which prefixes `%03d_` per component), the
    /// composed WPT capture is ONE PNG per fed doc named exactly
    /// `<safe(testKey)>.png` — no index prefix — matching the web composed
    /// capture and what diffComposedVsRef globs. `.atomic` closes the same
    /// read-during-write race the feeder's tight poll loop would hit.
    static func saveComposed(image: UIImage, filename: String) {
        let url = directory.appendingPathComponent(filename)
        if let data = image.pngData() {
            try? data.write(to: url, options: .atomic)
        }
    }

    /// Render a SwiftUI view to UIImage at 1x scale to match the Android
    /// emulator's 160dpi baseline (1pt == 1px). That keeps per-component
    /// captures the same pixel dimensions across platforms.
    @MainActor
    static func render<V: View>(_ view: V) -> UIImage? {
        let renderer = ImageRenderer(content: view)
        renderer.scale = 1.0
        // NOT `renderer.uiImage` directly: the framework promotes a render
        // whose result leaves [0,1] to a wide-gamut context, and ImageIO
        // then tags the PNG with `iCCP`/`cICP` — chunks the host pipeline
        // discards rather than honours, so the pixels would be scored as
        // sRGB. `CaptureColorSpace.capture` returns `uiImage`'s own raster
        // untouched unless that promotion happened. See
        // Renderer/CaptureColorSpace.swift for the measurements behind it.
        return CaptureColorSpace.capture(renderer, scale: 1.0)
    }

    /// Lane BF-I — the TWO-PASS backdrop render.
    ///
    /// CSS `backdrop-filter` filters what is painted BEHIND an element, and
    /// SwiftUI has no API for that. The runtime implements it as two
    /// ImageRenderer passes over the same canvas:
    ///
    ///   PASS A  `backdropPass = .sampling` — every backdrop element
    ///           suppresses its own paint (layout untouched), so the
    ///           resulting raster IS their backdrop;
    ///   PASS B  `backdropPass = .compositing(plate)` — each backdrop
    ///           element crops that raster to its own border box, filters
    ///           the crop, and draws it underneath itself.
    ///
    /// `twoPass: false` calls `render` directly — the SAME single
    /// ImageRenderer pass, on the same view, that every capture used before
    /// this lane. Callers derive the flag from
    /// `BackdropCapture.needsTwoPass`, so a document that declares no
    /// `backdrop-filter` cannot take the new path at all.
    ///
    /// A failed pass A (ImageRenderer returning nil, or a UIImage with no
    /// CGImage) falls back to the single-pass render rather than returning
    /// nil: a capture with un-filtered backdrops is the pre-lane rendering,
    /// while a missing PNG would stall the feeder's poll loop.
    @MainActor
    static func renderBackdropTwoPass<V: View>(_ view: V, twoPass: Bool) -> UIImage? {
        // No backdrop element on this surface → the untouched path.
        guard twoPass else { return render(view) }
        // PASS A. Same scale as the real capture, because the plate is
        // sampled in its own pixels.
        guard let sampled = render(view.environment(\.backdropPass, .sampling)),
              let plate = sampled.cgImage else { return render(view) }
        // 1.0 — `render` pins ImageRenderer.scale to 1 (1pt == 1px across
        // platforms), so the plate maps points to pixels 1:1. Spelled from
        // the produced image rather than assumed, so a future scale change
        // in `render` cannot silently mis-crop every backdrop.
        let scale = sampled.scale
        // PASS B — the capture that is actually saved.
        return render(view.environment(\.backdropPass,
                                       .compositing(BackdropPlate(image: plate,
                                                                  scale: scale))))
    }

    /// Hi-res variant for the B-EXT typography probes
    /// (docs/reports/COMPARE_METRICS_B8-B10.md Section 1.1). Renders at
    /// scale=4.0 so a 0.25-pt baseline shift in CSS coords surfaces as
    /// a 1-px shift in the captured buffer (above AA noise).
    ///
    /// The probe pipeline uses this only for components whose ID begins
    /// with "B8_", "B9_", or "B10_" — the canonical naming convention
    /// for fixtures/_metric_probes/ fixtures. The 327-pair regression
    /// baseline is locked at 1× and must never call into here.
    ///
    /// TODO[B-EXT round 91+]: requires Xcode/simulator validation pass
    /// before the iOS branch of probe-text-metrics.sh can call it. The
    /// web pipeline already produces real probe data via
    /// capture-screenshots-hires.mjs.
    @MainActor
    static func renderHires<V: View>(_ view: V) -> UIImage? {
        let renderer = ImageRenderer(content: view)
        // 4.0 — Section 1.1 of the spec. NOT a config value: hardcoded
        // because the B8/B9/B10 metric helpers expect exactly 4× and the
        // unit conversion (4× px → 1× px = divide by 4) is baked in there.
        renderer.scale = 4.0
        // Same colour-space gate as `render` — see
        // Renderer/CaptureColorSpace.swift. The probe path is not wired
        // into the iOS pipeline yet, so this is not fixing an observed
        // failure; it keeps the two capture entry points from drifting into
        // different colour policies, which is exactly how the tag reached a
        // committed run unnoticed.
        return CaptureColorSpace.capture(renderer, scale: 4.0)
    }

    /// Returns true when a component ID belongs to the B-EXT typography
    /// probe set (B8/B9/B10). Used by the capture loop to decide between
    /// the standard 1× render and the hires 4× render.
    /// See docs/reports/COMPARE_METRICS_B8-B10.md Section 2 for the naming
    /// convention.
    static func isProbeComponent(_ name: String) -> Bool {
        // Underscore-suffix match keeps "B8_Serif_AVATAR_16" in the set
        // but excludes a hypothetical "B8X_..." that isn't ours.
        return name.hasPrefix("B8_") || name.hasPrefix("B9_") || name.hasPrefix("B10_")
    }

    // MARK: - TITAN Phase 1 inbox-polling mode
    //
    // docs/reports/TITAN_ARCHITECTURE.md §6.3 — for the WPT bucket-A pass we
    // can't afford a 30 s xcodebuild + simctl install + launch cycle
    // per fixture (10 000 fixtures × 30 s = 83 hours, untenable). The
    // app boots ONCE; the orchestrator pushes per-fixture IR JSON into
    // an "inbox" directory inside the simulator's Documents/, the app
    // detects it, renders + screenshots, deletes the inbox file, and
    // waits for the next one.
    //
    // Per-fixture cost drops to ~0.5 s (just the SwiftUI render + the
    // ImageRenderer pass), giving the iOS pass an estimated wall time
    // of ~6-8 hours for full bucket-A.
    //
    // TODO[TITAN Phase 1.5]: requires Xcode/simulator validation pass.
    // The Phase 1 implementer (TITAN-IMPL-1) shipped this as code-only
    // because the headless-Xcode validation environment couldn't be
    // booted reliably during the time-budgeted Phase 1 run. The code
    // paths below are exercised by the unit-test scaffolding in
    // tools/titan/extract-fixture.test.mjs (the IR-shape contract)
    // but NOT by an end-to-end iOS capture run yet.
    //
    // The host-side counterpart (tools/titan/feed-ios.mjs) is
    // deferred to Phase 1.5 — it pushes fixtures via `xcrun simctl
    // pasteboard` or via writing into the simulator's app container
    // directly with `simctl get_app_container <udid> com.styleconverter.test data`.

    /// Inbox directory. Host pushes IR JSON into here; app polls and
    /// renders one at a time. Living inside Documents/ means it
    /// survives app restarts and is reachable from the host via
    /// `xcrun simctl get_app_container <udid> ... data`.
    static var inboxDirectory: URL {
        let docs = FileManager.default.urls(for: .documentDirectory, in: .userDomainMask)[0]
        let dir  = docs.appendingPathComponent("inbox", isDirectory: true)
        try? FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
        return dir
    }

    /// TITAN @font-face sandbox (wave-35 lane B2).
    ///
    /// A SIBLING of `inboxDirectory`, not a subdirectory: `nextFixtureURL`
    /// lists the inbox for `.json`, so a font living under it would be inert
    /// but confusing. The host feeder (tools/titan/feed-ios.mjs) copies each
    /// font file the fed document declares to `<this dir>/<fontFaces[].src>`,
    /// preserving the corpus-relative path VERBATIM — which is the whole
    /// contract with `DocumentFontRegistry`: it resolves
    /// `fontsDirectory.appendingPathComponent(face.src)` with no name
    /// mangling, so no escaping rule can drift between host and device.
    ///
    /// Unlike `inboxDirectory` this does NOT create the directory. Absence is
    /// a meaningful state — it means this run's feeder pushed no faces — and
    /// the registry's decline path reports it with the family AND the path.
    static var fontsDirectory: URL {
        let docs = FileManager.default.urls(for: .documentDirectory, in: .userDomainMask)[0]
        return docs.appendingPathComponent("fonts", isDirectory: true)
    }

    /// TITAN replaced-element image sandbox (wave-39 lane A2).
    ///
    /// A THIRD sibling of `inboxDirectory` and `fontsDirectory`, on the same
    /// two arguments: `nextFixtureURL` lists the inbox for `.json` (an image
    /// under it would be inert but confusing), and the two ASSET channels stay
    /// separately auditable rather than sharing one directory with a widened
    /// file-type table. The host feeder (tools/titan/feed-ios.mjs) copies each
    /// image the fed document references to `<this dir>/<meta.attrs.src>`,
    /// preserving the corpus-relative path VERBATIM — the whole contract with
    /// `DocumentImageRegistry`, which resolves
    /// `imagesDirectory.appendingPathComponent(src)` with no name mangling.
    ///
    /// Not created here, for `fontsDirectory`'s reason: absence means this
    /// run's feeder delivered no images, and the registry's decline path
    /// reports it with the source AND the path it looked at.
    static var imagesDirectory: URL {
        let docs = FileManager.default.urls(for: .documentDirectory, in: .userDomainMask)[0]
        return docs.appendingPathComponent("images", isDirectory: true)
    }

    /// Polls the inbox for the oldest *.json fixture. Returns its URL or
    /// nil if the inbox is empty. The caller is responsible for deleting
    /// the file after it's been processed (so a crash mid-render doesn't
    /// drop the fixture; a re-poll picks it up again).
    static func nextFixtureURL() -> URL? {
        let fm = FileManager.default
        guard let entries = try? fm.contentsOfDirectory(
            at: inboxDirectory,
            includingPropertiesForKeys: [.contentModificationDateKey],
            options: [.skipsHiddenFiles]
        ) else { return nil }
        // Oldest-first ordering matches FIFO semantics the orchestrator
        // expects — a host that pushes fixtures in deterministic order
        // gets them back in the same order.
        let jsonFiles = entries.filter { $0.pathExtension == "json" }
        // Pair each *.json with its modification date, then defer the
        // ordering to the pure `orderOldestFirst` helper (unit-testable
        // without a filesystem — see InboxModeTests).
        let dated: [(url: URL, date: Date)] = jsonFiles.map { url in
            let d = (try? url.resourceValues(forKeys: [.contentModificationDateKey]).contentModificationDate) ?? Date.distantPast
            return (url, d)
        }
        return orderOldestFirst(dated).first
    }

    /// Pure oldest-first ordering of (url, mtime) pairs — the FIFO poll rule,
    /// factored out so InboxModeTests can prove ordering without touching the
    /// filesystem or a device.
    static func orderOldestFirst(_ entries: [(url: URL, date: Date)]) -> [URL] {
        entries.sorted { $0.date < $1.date }.map { $0.url }
    }

    /// Mark a processed inbox fixture as consumed by deleting the file.
    /// Idempotent — missing file is not an error (the host may have
    /// re-pushed before we got around to deleting our copy).
    static func consumeFixture(_ url: URL) {
        try? FileManager.default.removeItem(at: url)
    }
}
