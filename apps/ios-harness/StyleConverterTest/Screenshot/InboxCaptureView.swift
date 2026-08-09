//
//  InboxCaptureView.swift
//  StyleConverterTest
//
//  TITAN Phase 3 — the app-side poll loop for the WPT feeder.
//
//  When the app launches in inbox mode (CaptureOverrides.titanInbox, set by
//  SIMCTL_CHILD_TITAN_INBOX=1 or the -titanInbox 1 launch arg) ContentView
//  routes here INSTEAD of the bundled-IR auto-capture flow. The host
//  (tools/titan/feed-ios.mjs) pushes per-fixture IR JSON into
//  Documents/inbox; this view polls that directory, decodes each fixture
//  with the SAME JSONDecoder + IRDocument the bundled flow uses, renders +
//  captures every component through the IDENTICAL path (captureAllComponents
//  in ScreenshotCaptureView.swift), then consumes the inbox file and keeps
//  polling. The app boots ONCE and services many fixtures, so per-fixture
//  cost is just the SwiftUI render + ImageRenderer pass (~0.5 s), not a
//  30 s rebuild+install+launch cycle.
//

import SwiftUI
// IRDocument decode comes from the runtime package — same type the bundled
// ContentView.loadDocument decodes, so the inbox path and the bundled path
// share one decoder contract.
import StyleConverterRuntime

struct InboxCaptureView: View {
    // Lightweight on-screen status so a human watching the simulator (and
    // a host tailing the log) can confirm the loop is alive. None of this
    // reaches the captured PNGs — ImageRenderer builds an isolated tree
    // from CaptureCanvas alone.
    @State private var processed = 0
    @State private var lastFixture = "—"

    var body: some View {
        VStack(spacing: 12) {
            Text("TITAN inbox mode")
                .font(.system(size: 16, weight: .semibold))
                .foregroundColor(.white)
            Text("Fixtures processed: \(processed)")
                .foregroundColor(.green)
            Text("Last: \(lastFixture)")
                .font(.system(size: 11, design: .monospaced))
                .foregroundColor(.gray)
                .lineLimit(1)
            Text("Polling \(ScreenshotManager.inboxDirectory.path)")
                .font(.system(size: 9))
                .foregroundColor(.white.opacity(0.4))
                .multilineTextAlignment(.center)
        }
        .padding(24)
        .onAppear(perform: startPolling)
    }

    private func startPolling() {
        // Announce activation ONCE. The host greps this in `simctl launch`
        // / log output to be sure the app really entered inbox mode and did
        // not silently fall back to the auto-capture flow (the honesty rule
        // the dynamic-capture markers follow elsewhere in this harness).
        // Touch inboxDirectory here so the directory exists before the host
        // pushes its first fixture (createDirectory is lazy on first access).
        print("[TITAN] inbox mode active; polling \(ScreenshotManager.inboxDirectory.path)")
        poll()
    }

    /// One poll tick: DRAIN every queued fixture oldest-first (the host
    /// keeps one fixture in flight at a time, but draining is robust if it
    /// ever pushes a burst), then re-arm on the main run loop. 250 ms keeps
    /// the app responsive and the idle CPU cost near zero between pushes.
    private func poll() {
        while let url = ScreenshotManager.nextFixtureURL() {
            processOne(url)
        }
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.25, execute: poll)
    }

    /// Decode one inbox fixture and capture it. `consumeFixture` runs in a
    /// `defer` so even a poison (undecodable) fixture is removed rather than
    /// wedging the loop on the same bad file forever — a re-push gets a
    /// fresh attempt, matching the FIFO/at-least-once contract in
    /// ScreenshotManager.nextFixtureURL.
    @MainActor
    private func processOne(_ url: URL) {
        defer { ScreenshotManager.consumeFixture(url) }
        guard let data = try? Data(contentsOf: url) else {
            FileHandle.standardError.write(Data(
                "[TITAN] could not read inbox fixture \(url.lastPathComponent)\n".utf8))
            return
        }
        // SAME decoder as ContentView.loadDocument — the inbox render must
        // go through the identical IR decode so WPT captures are comparable
        // with the bundled captures.
        guard let document = try? JSONDecoder().decode(IRDocument.self, from: data) else {
            FileHandle.standardError.write(Data(
                "[TITAN] IR decode failed for \(url.lastPathComponent)\n".utf8))
            return
        }
        // wave-35 lane B2 — register this document's `@font-face` files BEFORE
        // any capture. Ordering is load-bearing: CoreText must know the face
        // before Text measures, so a registration after the render would
        // arrive too late to shape the capture and the screenshot would
        // silently record the fallback. Called UNCONDITIONALLY (a nil list
        // clears): this view renders many documents in one process and a
        // leftover face from the previous one would shadow this one's
        // same-named family.
        let faceCount = DocumentFontRegistry.shared.register(
            document.fontFaces, baseDirectory: ScreenshotManager.fontsDirectory)
        if let faces = document.fontFaces, !faces.isEmpty {
            let rep = DocumentFontRegistry.shared.lastReport
            print("[TITAN] @font-face: declared=\(rep.declared) registered=\(faceCount)" +
                  (rep.declined.isEmpty ? "" : " DECLINED=\(rep.declined.joined(separator: ","))"))
        }
        // wave-39 lane A2 — point the replaced-element image registry at this
        // run's sandbox and drop the previous document's decode cache. Called
        // UNCONDITIONALLY for DocumentFontRegistry's reason, though the failure
        // it guards against is subtler: rasters are cached by the wire `src`,
        // so a document reusing a path the PREVIOUS document delivered would
        // paint the old picture even though its own delivery failed. Cheap (a
        // dictionary clear) and it makes the "no images this run" state
        // explicit rather than inherited.
        DocumentImageRegistry.shared.configure(baseDirectory: ScreenshotManager.imagesDirectory)
        // TITAN WPT Round 3: composed sub-flag (titanComposed) switches this
        // per-fixture render between the two capture footings. Both consume
        // the IDENTICAL decoded IRDocument; only the capture GEOMETRY differs.
        if CaptureOverrides.titanComposed {
            // Composed: the WHOLE doc on ONE browser-ref-framed canvas →
            // ONE `<safe(testKey)>.png` (testKey derived from THIS inbox
            // file's name, which the feeder pushed as `<testKey>.json`).
            // diffComposedVsRef diffs it directly against the ref, no stitch.
            let pngName = ScreenshotManager.composedPngName(
                forFixtureFilename: url.lastPathComponent)
            captureComposedDocument(document, pngName: pngName)
            processed += 1
            lastFixture = url.lastPathComponent
            print("[TITAN] composed capture \(pngName) from \(document.components.count) root(s) of \(url.lastPathComponent)")
        } else {
            // Per-component (legacy inbox path, unchanged): every flattened
            // component to its own `%03d_<name>.png`, stitched host-side.
            captureAllComponents(document)
            processed += 1
            lastFixture = url.lastPathComponent
            // Progress line the host can tail to confirm the fixture finished.
            print("[TITAN] captured \(document.components.count) component(s) from \(url.lastPathComponent)")
        }
    }
}
