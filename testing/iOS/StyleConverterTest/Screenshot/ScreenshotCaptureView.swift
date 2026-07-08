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
        // solid #1A1A2E background, 16 px padding. Matches the Android and
        // web canvases exactly so captures are directly pixel-diffable.
        let canvas = CaptureCanvas(component: component)

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
/// testing/web/src/ui/CaptureGallery.tsx — identical IR property-type set,
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
/// Rationale (swarm-002 RC1 — testing/titan/investigations/swarm-002/
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

/// Helper: extract a Double from an IRValue that may be a bare `.int`/
/// `.double` or an `.object` wrapping `{value: <Number>}`.
private func tryNumericValue(_ v: IRValue) -> Double? {
    if let d = v.doubleValue { return d }
    if let inner = v["value"], let d = inner.doubleValue { return d }
    return nil
}

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

/// Flatten the IR tree depth-first pre-order, suppressing children of any
/// parent that creates a paint context (see parentCreatesContext above).
/// Matches the web `flatten()` in CaptureGallery.tsx and Android's
/// `flattenComponents()` exactly so capture indices align across platforms.
private func flatten(_ components: [IRComponent]) -> [IRComponent] {
    var out: [IRComponent] = []
    func walk(_ c: IRComponent) {
        out.append(c)
        guard let kids = c.children, !kids.isEmpty else { return }
        // Skip standalone child captures when the parent's paint context
        // governs how the child is visually composed; the child is already
        // captured visually-correctly inside the parent's canvas.
        if parentCreatesContext(c) { return }
        kids.forEach(walk)
    }
    components.forEach(walk)
    return out
}
