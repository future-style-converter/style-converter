//
//  ScreenshotCaptureView.swift
//  StyleConverterTest
//
//  Drives the first-launch capture phase: clears old screenshots, then walks
//  through every component in the document, renders it via ImageRenderer,
//  saves the PNG, and calls `onComplete` when done.
//
//  Mirrors Android's ScreenshotCaptureScreen.kt but without needing manual
//  permissions — iOS app sandbox writes are unconditional.
//

import SwiftUI
// IRDocument/IRComponent/IRValue come from the runtime package — the
// context-creation predicate below inspects raw IR property payloads.
import StyleConverterRuntime

struct ScreenshotCaptureView: View {
    let document: IRDocument
    let onComplete: () -> Void

    @State private var captured = 0
    @State private var total = 0
    @State private var currentName = ""
    @State private var finished = false

    private var flat: [IRComponent] { flatten(document.components) }

    var body: some View {
        VStack(spacing: 16) {
            Text("Capturing component screenshots")
                .font(.system(size: 16, weight: .semibold))
                .foregroundColor(.white)

            if finished {
                VStack(spacing: 8) {
                    Text("✓ Captured \(captured) / \(total)")
                        .foregroundColor(.green)
                    Text("Pull from simulator:")
                        .font(.system(size: 12))
                        .foregroundColor(.gray)
                    Text("xcrun simctl get_app_container booted com.styleconverter.test data")
                        .font(.system(size: 11, design: .monospaced))
                        .foregroundColor(.white.opacity(0.7))
                        .multilineTextAlignment(.center)
                        .padding(8)
                        .background(Color.white.opacity(0.05))
                        .cornerRadius(4)
                    Text("test-all.sh and test-ios.sh do this automatically.")
                        .font(.system(size: 11))
                        .foregroundColor(.white.opacity(0.5))
                }
                Button("Continue to gallery", action: onComplete)
                    .buttonStyle(.borderedProminent)
            } else {
                ProgressView(value: Double(captured), total: Double(max(total, 1)))
                    .progressViewStyle(.linear)
                    .frame(maxWidth: 300)
                Text("\(captured) / \(total)")
                    .foregroundColor(.white.opacity(0.8))
                Text(currentName)
                    .font(.system(size: 12, design: .monospaced))
                    .foregroundColor(.gray)
                    .lineLimit(1)
            }
        }
        .padding(24)
        .onAppear(perform: startCapture)
    }

    private func startCapture() {
        ScreenshotManager.reset()
        // Wave 7 — announce the dynamic-capture hooks for this run so a
        // host script (and a human reading the simulator log) can verify
        // a forced/width/dark run actually ran forced (the log-side
        // sibling of the canvas's force-state accessibility marker).
        // Wave 8: announce the motion clock too — the log-side half of
        // the seized-run verification marker (DYNAMIC_CAPTURE.md §4: a
        // seized run must be distinguishable from a live one at a glance).
        print("[Capture] forceState=\(CaptureOverrides.forceState ?? "none") "
              + "width=\(Int(CaptureOverrides.captureWidth)) "
              + "scheme=\(CaptureOverrides.darkMode ? "dark" : "light") "
              + "animationTime=\(CaptureOverrides.animationTimeRaw ?? "live")")
        // Wave 8 — the HOST-verifiable marker: the run config rides the
        // pulled screenshot directory as capture-config.json, and
        // test-all.sh HARD-FAILS a CAPTURE_ANIMATION_TIME run whose
        // pulled config doesn't carry the expected t (the iOS analogue
        // of Android's logcat gate / web's data-animation-time check —
        // a seized run can never silently degrade to a live capture).
        let config: [String: Any] = [
            "forceState": CaptureOverrides.forceState ?? "none",
            "width": Int(CaptureOverrides.captureWidth),
            "scheme": CaptureOverrides.darkMode ? "dark" : "light",
            "animationTime": CaptureOverrides.animationTimeRaw ?? "live",
        ]
        if let data = try? JSONSerialization.data(withJSONObject: config, options: [.sortedKeys]) {
            try? data.write(to: ScreenshotManager.directory
                .appendingPathComponent("capture-config.json"))
        }
        total = flat.count
        captured = 0
        captureNext(index: 0)
    }

    /// Capture components one at a time via async dispatch. Each iteration
    /// yields to the main run loop so the progress UI updates.
    private func captureNext(index: Int) {
        guard index < flat.count else {
            finished = true
            // Auto-advance after a brief pause — matches Android behavior.
            DispatchQueue.main.asyncAfter(deadline: .now() + 1.5) {
                onComplete()
            }
            return
        }

        let component = flat[index]
        currentName = component.name

        // Render the chromeless CaptureCanvas — 390 px wide, natural height,
        // solid #1A1A2E background (this is the bundled/baseline flow —
        // wptCaptureMode is never set here, so the canvas keeps the dark
        // stage; the TITAN inbox flow below flips it white per corpus-v4),
        // 16 px padding. Matches the Android and
        // web canvases exactly so captures are directly pixel-diffable.
        // Wave 8: ImageRenderer builds an ISOLATED render tree — the
        // ContentView-level environment never reaches it — so the
        // document keyframes block (spec 07 §1.2) is re-published
        // directly on the captured content here.
        let canvas = CaptureCanvas(component: component)
            .environment(\.styleKeyframes, document.keyframes)

        // Lane BF-I deliberately does NOT two-pass here. This is the
        // BUNDLED property-fixture path behind the committed 327-pair
        // dark-stage baselines, including
        // fixtures/properties/effects/backdrop-filter.json — whose boxes
        // are standalone crops with nothing behind them but the stage, so a
        // backdrop pass would cost time and could only move pixels the
        // baselines pin. Single pass, unchanged call, byte-identical
        // captures; the WPT paths below (captureAllComponents /
        // captureComposedDocument), where a backdrop element actually has
        // neighbours, are where the two-pass runs.
        if let image = ScreenshotManager.render(canvas) {
            ScreenshotManager.save(image: image, index: index, name: component.name)
        }
        captured = index + 1

        DispatchQueue.main.asyncAfter(deadline: .now() + 0.02) {
            captureNext(index: index + 1)
        }
    }
}

// MARK: - Flatten

/// Predicate: does `parent` create a paint context that its children's
/// appearance depends on?
///
/// Mirrors `parentCreatesContext` in
/// apps/web-harness/src/ui/CaptureGallery.tsx — identical IR property-type set,
/// identical decision rules. Kept in lock-step across platforms so the
/// flattened capture lists line up by index for the cross-platform diff
/// inject-wpt-block.mjs runs.
///
/// Properties that flag context-creation (cross-referenced to the IR types
/// the Kotlin converter emits, src/main/kotlin/app/irmodels/properties/):
///   - ClipPath / Mask / MaskImage / Filter / BackdropFilter  (presence)
///   - Overflow / OverflowX / OverflowY = 'clip' | 'hidden'
///   - MixBlendMode != 'normal'
///   - Transform (non-empty list), Rotate / Scale / Translate (presence)
///   - Opacity value < 1
///
/// Rationale (swarm-002 RC1 — tools/titan/investigations/swarm-002/
/// css-overflow__clip-002.json + filter-effects__backdrop-filter-clip-rect-zoom.json):
/// EXTFIX-A nests children inside their parent's IR `children`; the renderer
/// recurses correctly so the parent's clip / blend / transform / opacity
/// context applies to the child INSIDE the parent's CaptureCanvas. But the
/// legacy `flatten` walk also emits a SECOND standalone capture per child,
/// which renders the child WITHOUT the parent's paint context — that leaked
/// standalone PNG never matches the browser-ref and trips false structural-
/// divergence. Suppress it on every platform.
func parentCreatesContext(_ parent: IRComponent) -> Bool {
    // No properties → no paint context. Cheap guard.
    if parent.properties.isEmpty { return false }
    // Single linear pass so the cost stays O(n) on hot capture paths.
    for p in parent.properties {
        switch p.type {
        // Pure-presence cases: any active value means a paint context.
        case "ClipPath", "Mask", "MaskImage", "Filter", "BackdropFilter",
             "Rotate", "Scale", "Translate":
            return true
        // Overflow keywords — only `clip` and `hidden` clip content. The
        // IR `data` payload is either a bare string or `{value: "..."}`
        // depending on the parser; tryStringValue handles both shapes.
        // The Kotlin enum serializer uppercases the value ("CLIP",
        // "HIDDEN") while the spec-grade parser lowercases it — lowercase
        // before comparing so the predicate fires on both shapes.
        case "Overflow", "OverflowX", "OverflowY":
            if let v = tryStringValue(p.data)?.lowercased(), v == "clip" || v == "hidden" { return true }
        // Blend-mode — only non-'normal' values create a blend context.
        // Same UPPER/lower variance as Overflow above.
        case "MixBlendMode":
            if let v = tryStringValue(p.data)?.lowercased(), v != "normal" { return true }
        // Transform — IR carries an array of transform-function entries;
        // any non-empty list is non-identity (the parser drops the
        // 'none' keyword before serialising).
        case "Transform":
            if case .array(let a) = p.data, !a.isEmpty { return true }
        // Opacity — IR data shape varies by extractor flavor (raw number,
        // `{value:Number}`, or the Kotlin-convert `{alpha:Number, original:...}`).
        // Values < 1 create a stacking context with backdrop dependence.
        case "Opacity":
            if let v = tryOpacityValue(p.data), v < 1.0 { return true }
        default:
            break
        }
    }
    return false
}

/// Helper: extract a String from an IRValue that may be a bare `.string` or
/// an `.object` wrapping `{value: "..."}` — matches the dual IR carrier shape
/// the longhand parsers emit.
private func tryStringValue(_ v: IRValue) -> String? {
    if let s = v.stringValue { return s }
    if let inner = v["value"], let s = inner.stringValue { return s }
    return nil
}

// Retro P2b (A6#10): `tryNumericValue` — a bare/`{value:}` Double reader —
// was deleted here as zero-reference. `tryStringValue` above and
// `tryOpacityValue` below are the two carriers this file actually reads;
// the numeric one had no call site (the runtime's own
// StyleEngine/core/types/NumberValue.swift owns that job).

/// Helper: extract an opacity scalar from an IRValue. Handles all three
/// carrier shapes the IR has used historically:
///   - bare number  (0.5)
///   - `{value: 0.5}`  (parser-uniform shape)
///   - `{alpha: 0.5, original: {type:'number', value:0.5}}`  (Kotlin convert)
private func tryOpacityValue(_ v: IRValue) -> Double? {
    if let d = v.doubleValue { return d }
    if let alpha = v["alpha"]?.doubleValue { return alpha }
    if let inner = v["value"]?.doubleValue { return inner }
    if let orig = v["original"]?["value"]?.doubleValue { return orig }
    return nil
}

/// Render + save EVERY component of `document` synchronously through the
/// exact same building blocks the first-launch auto-capture uses:
/// `flatten` for the capture list, `CaptureCanvas` (+ the document
/// keyframes environment) for the render surface, `ScreenshotManager.render`
/// for the ImageRenderer pass, and `ScreenshotManager.save` for the
/// `%03d_<name>.png` filename rule and the shared screenshot directory.
///
/// TITAN Phase 3: the inbox poll loop (InboxCaptureView) drives this once
/// per host-pushed fixture. Sharing this code path with the auto-capture
/// flow is the whole point — WPT captures MUST be byte-comparable with
/// normal captures and their per-component filenames MUST match what the
/// compare pipeline globs (`*_<safeKey>.png`, see
/// tools/titan/inject-wpt-block.mjs diffPlatformVsRef).
///
/// The auto-capture flow (ScreenshotCaptureView) keeps its own async,
/// per-component driver purely so its on-screen progress bar animates;
/// the render/save primitives it calls are identical to these. This
/// helper is synchronous because the inbox loop has no progress UI to
/// pump and wants the fixture done before it consumes the inbox file.
@MainActor
func captureAllComponents(_ document: IRDocument) {
    // reset() clears the screenshot dir so fixture N+1 never inherits
    // fixture N's PNGs — the on-device half of the feeder's idempotence
    // contract (the host also clears before each push).
    ScreenshotManager.reset()
    // Same flattened capture list the auto-capture flow renders. In IR v2
    // children is always nil (flat doc), so this is document.components in
    // declared order — matching the order the feeder derives host-side.
    let flat = flatten(document.components)
    for (index, component) in flat.enumerated() {
        // Identical to ScreenshotCaptureView.captureNext: the chromeless
        // CaptureCanvas with the document keyframes re-published on the
        // isolated ImageRenderer tree (Wave 8 comment there).
        //
        // WPT parity: THIS helper is the TITAN inbox/WPT capture path only
        // (InboxCaptureView is its sole caller), so publish WPT capture mode
        // so the runtime drops the synthesized component-name placeholder for
        // nameless-empty leaves — the browser-ref never paints it. Gated on
        // CaptureOverrides.titanInbox (the inbox=WPT launch signal) so it is
        // provably OFF for the normal bundled auto-capture flow in
        // captureNext, which does NOT set this and keeps every committed
        // baseline byte-identical.
        let canvas = CaptureCanvas(component: component)
            .environment(\.styleKeyframes, document.keyframes)
            .environment(\.wptCaptureMode, CaptureOverrides.titanInbox)
        // Lane BF-I — a component that declares `backdrop-filter` is
        // captured in two passes so the filter runs over what is painted
        // BEHIND it (ScreenshotManager.renderBackdropTwoPass). The
        // predicate is per-component here because this loop's capture
        // surface is one component; everything else takes the identical
        // single-pass `render` call this line used before the lane.
        if let image = ScreenshotManager.renderBackdropTwoPass(
            canvas, twoPass: BackdropCapture.needsTwoPass([component])) {
            ScreenshotManager.save(image: image, index: index, name: component.name)
        }
    }
}

/// Render the WHOLE fed document COMPOSED onto ONE browser-ref-framed
/// canvas and save it as a SINGLE `<safe(testKey)>.png`.
///
/// TITAN WPT Round 3: this is the native twin of the web harness's
/// WPT_COMPOSED path (apps/web-harness/src/ui/ComposedCaptureGallery.tsx).
/// InboxCaptureView drives it once per host-pushed per-test doc when the
/// inbox run is ALSO composed (CaptureOverrides.titanComposed). Unlike
/// captureAllComponents — which captures every flattened component to its
/// own `%03d_<name>.png` — this composes all of the doc's roots on one
/// ComposedCaptureCanvas (slot layout + document flow, 390-wide/WHITE
/// corpus-v4 canvas/
/// 16px-pad framing that mirrors the browser-ref) and ImageRenderer's it
/// ONCE. inject-wpt-block.mjs's diffComposedVsRef then diffs the composite
/// DIRECTLY against the ref, no vertical stitch.
///
/// `pngName` is the composed filename the feeder polls for and the compare
/// pipeline globs — `<safe(testKey)>.png`, derived by the caller from the
/// inbox fixture filename (ScreenshotManager.composedPngName). Kept as a
/// parameter (not recomputed here) so the name derivation has ONE home and
/// stays unit-testable device-free.
///
/// wptCaptureMode is published TRUE (this is the WPT capture path only, its
/// sole caller is the composed inbox loop) so the renderer drops the
/// synthesized component-name placeholder for nameless-empty leaves — the
/// browser-ref never paints it. Real element text still renders. The
/// bundled auto-capture flow (captureNext) never reaches here, so every
/// committed 327-pair baseline stays byte-identical.
@MainActor
func captureComposedDocument(_ document: IRDocument, pngName: String) {
    // Clear the screenshot dir so this doc's single PNG never sits beside a
    // prior doc's captures — the on-device half of the feeder's idempotence
    // contract (the host also clears before each push).
    ScreenshotManager.reset()
    // The whole doc, composed on one ref-matching surface. Keyframes are
    // re-published on the isolated ImageRenderer tree exactly like the
    // per-component path; wptCaptureMode drops debug-name placeholders.
    let canvas = ComposedCaptureCanvas(document: document)
        .environment(\.styleKeyframes, document.keyframes)
        .environment(\.wptCaptureMode, true)
    // ONE composite PNG named for the test key. Normally ONE ImageRenderer
    // pass; two (lane BF-I) when the doc declares `backdrop-filter`
    // anywhere, so those elements can filter what is painted behind them —
    // the composed canvas is exactly the surface where a backdrop element
    // HAS neighbours to sample, unlike the per-component crops.
    if let image = ScreenshotManager.renderBackdropTwoPass(
        canvas, twoPass: BackdropCapture.needsTwoPass(document.components)) {
        ScreenshotManager.saveComposed(image: image, filename: pngName)
    }
}

/// Flatten the IR tree depth-first pre-order, suppressing children of any
/// parent that creates a paint context (see parentCreatesContext above).
/// Matches the web `flatten()` in CaptureGallery.tsx and Android's
/// `flattenComponents()` exactly so capture indices align across platforms.
/// Stays file-private (ComponentGallery.swift declares its own private
/// `flatten`); the TITAN inbox loop reuses this rule THROUGH the internal
/// captureAllComponents above rather than calling flatten directly.
/// Does this component's OWN render depend on what is painted behind it?
///
/// `parentCreatesContext` asks the question in the parent direction and
/// suppresses children when the parent's paint context owns their
/// composition. That misses the mirror case: a child can be backdrop-
/// dependent by itself, under a perfectly ordinary parent.
///
/// `mix-blend-mode` and `backdrop-filter` are exactly that — both are DEFINED
/// as functions of the backdrop, so a standalone capture composites against
/// the bare canvas and answers no question. The comparator then reports
/// cross-platform divergence on a render that never occurs in the real
/// composition (observed on fixtures/composition-test.json: `005_layer.png`
/// and `007_layer.png` produced 4 divergent pairs of pure noise).
///
/// MUST stay identical to `dependsOnBackdrop` in web CaptureGallery.tsx and
/// Android ScreenshotCaptureScreen.kt — capture indices are positional, so a
/// rule firing on one platform only would silently misalign every subsequent
/// component in the comparison.
func dependsOnBackdrop(_ component: IRComponent) -> Bool {
    // Same cheap guard and single-pass shape as parentCreatesContext above.
    if component.properties.isEmpty { return false }
    for p in component.properties {
        switch p.type {
        // backdrop-filter filters the backdrop by definition — with nothing
        // behind it the filter is the identity.
        case "BackdropFilter":
            return true
        // Same UPPER/lower variance parentCreatesContext documents; reuse the
        // same tryStringValue helper so both predicates read the IR alike.
        case "MixBlendMode":
            if let v = tryStringValue(p.data)?.lowercased(), v != "normal" { return true }
        default:
            break
        }
    }
    return false
}

private func flatten(_ components: [IRComponent]) -> [IRComponent] {
    var out: [IRComponent] = []
    func walk(_ c: IRComponent) {
        out.append(c)
        guard let kids = c.children, !kids.isEmpty else { return }
        // Skip standalone child captures when the parent's paint context
        // governs how the child is visually composed; the child is already
        // captured visually-correctly inside the parent's canvas.
        if parentCreatesContext(c) { return }
        // Mirror rule: skip a child that is itself backdrop-dependent.
        kids.forEach { if !dependsOnBackdrop($0) { walk($0) } }
    }
    components.forEach(walk)
    return out
}
