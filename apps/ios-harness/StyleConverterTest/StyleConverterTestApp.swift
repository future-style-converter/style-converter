//
//  StyleConverterTestApp.swift
//  StyleConverterTest
//
//  App entry point. Wraps the gallery in a 390×844 phone-frame view to
//  match the web and Android testing viewports pixel-for-pixel.
//

import SwiftUI
import CoreText
// The runtime style engine (StyleEngine + Models + Renderer) lives in the
// local SwiftPM package at the repo root — see Package.swift and
// runtimes/swiftui/Sources/StyleConverterRuntime. The harness keeps only
// the capture/gallery chrome.
import StyleConverterRuntime

/// Register every bundled Inter font with Core Text so SwiftUI's
/// `.font(.custom("Inter", size:))` resolves at runtime. We do this in
/// code (not via Info.plist's UIAppFonts) because the project's
/// Info.plist gets reverted by an auto-format pass between builds, and
/// runtime registration is otherwise equivalent.
private func registerBundledFonts() {
    for name in ["Inter-Regular", "Inter-Medium", "Inter-Bold", "Inter-Black"] {
        guard let url = Bundle.main.url(forResource: name, withExtension: "otf") else { continue }
        var error: Unmanaged<CFError>?
        CTFontManagerRegisterFontsForURL(url as CFURL, .process, &error)
        // Silently swallow already-registered errors (kCTFontManagerErrorAlreadyRegistered = 105).
    }
}

@main
struct StyleConverterTestApp: App {
    init() {
        // Load Inter before any view renders so the placeholder font path
        // (.custom("Inter", size:)) doesn't fall back to system on first paint.
        registerBundledFonts()
        // NOTE: the launch-time DEBUG self-tests that used to run here
        // (CoreTypesSelfTest … IRModelsSelfTest, 12 modules) were converted
        // into real XCTest cases when the engine moved into the SwiftPM
        // package — see runtimes/swiftui/Tests/StyleConverterRuntimeTests/.
        // Run them with:  xcodebuild test -scheme StyleConverterRuntime …
    }

    var body: some Scene {
        WindowGroup {
            RootView()
                .preferredColorScheme(.dark)
        }
    }
}

/// Outer dark background + inner 390×844 frame matching
/// `apps/android-harness/.../MainActivity.kt` and `apps/web-harness`.
struct RootView: View {
    var body: some View {
        ZStack {
            // Outer: matches web's body background (#111)
            Color(red: 0x11 / 255.0, green: 0x11 / 255.0, blue: 0x11 / 255.0)
                .ignoresSafeArea()

            // Inner: 390×844 phone frame matching web's #root
            ContentView()
                .frame(width: 390, height: 844)
                // #39: publish the phone-frame geometry to the runtime's
                // styleViewport channel so gallery renders resolve vw/vh
                // and root percentages against the same numbers the
                // capture canvas publishes (the runtime no longer
                // hardcodes 390×844 itself).
                .environment(\.styleViewport, CaptureCanvas.viewport)
                .background(
                    Color(red: 0x1A / 255.0, green: 0x1A / 255.0, blue: 0x2E / 255.0)
                )
                .clipShape(RoundedRectangle(cornerRadius: 12))
                .overlay(
                    RoundedRectangle(cornerRadius: 12)
                        .stroke(Color.white.opacity(0.15), lineWidth: 1)
                )
        }
    }
}
