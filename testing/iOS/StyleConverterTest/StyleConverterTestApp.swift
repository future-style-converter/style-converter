//
//  StyleConverterTestApp.swift
//  StyleConverterTest
//
//  App entry point. Wraps the gallery in a 390×844 phone-frame view to
//  match the web and Android testing viewports pixel-for-pixel.
//

import SwiftUI
import CoreText

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
    // Phase 1 primitive extractor self-test. DEBUG-only — zero release cost.
    // See testing/iOS/StyleConverterTest/StyleEngine/core/types/CoreTypesSelfTest.swift
    init() {
        // Load Inter before any view renders so the placeholder font path
        // (.custom("Inter", size:)) doesn't fall back to system on first paint.
        registerBundledFonts()
        #if DEBUG
        CoreTypesSelfTest.run()
        // Phase 2 — spacing extractors + resolver sanity check.
        SpacingSelfTest.run()
        // Phase 3 — sizing extractor + applier resolver sanity check.
        SizingSelfTest.run()
        // Phase 4 — colour + background + blend + isolation extractors.
        ColorBackgroundSelfTest.run()
        // Phase 5 — border family (sides, radius, outline, image, shadow,
        // misc keywords). See testing/iOS/.../StyleEngine/borders/
        // BordersSelfTest.swift.
        BordersSelfTest.run()
        // Phase 6 — typography family (font + font-variant + line + spacing
        // + decoration + wrapping + writing + other + grouped unsupported).
        // See testing/iOS/.../StyleEngine/typography/TypographySelfTest.swift.
        TypographySelfTest.run()
        // Phase 7 — layout family scaffold (step 1). Registers 60 layout
        // property names; extractors/appliers are no-op today and land in
        // steps 2-5. See testing/iOS/.../StyleEngine/layout/LayoutSelfTest.swift.
        LayoutSelfTest.run()
        // Phase 8 — transforms family + effects clip/filter/mask +
        // visibility/overflow. Runs AFTER LayoutSelfTest so the layout
        // registry drift check is the first failure the developer sees.
        // Both tests print-only on failure (no assertionFailure / fatal
        // Error) — ff901e3 hotfix convention.
        TransformsSelfTest.run()
        EffectsSelfTest.run()
        // Phase 9 — animations + transitions + view-timeline +
        // view-transition + scroll-timeline (25 properties). Runs AFTER
        // EffectsSelfTest so earlier phase drift surfaces first. Print-
        // only on failure (ff901e3 hotfix convention).
        AnimationsSelfTest.run()
        // Phase 10 — long-tail sweep (~22 categories, ~150 property
        // names registered via grouped Config/Extractor/Applier
        // triplets). Runs last so earlier-phase drift surfaces first.
        // Print-only on failure (ff901e3 convention).
        Phase10SelfTest.run()
        // Bug 1 + Bug 2 — IR `_text` / `_tag` decode contract. Pinned
        // separately because these are renderer-only metadata fields
        // (no associated CSS property) and the existing phase tests
        // don't cover them. See IRModelsSelfTest.swift header for the
        // motivating investigations.
        IRModelsSelfTest.run()
        #endif
    }

    var body: some Scene {
        WindowGroup {
            RootView()
                .preferredColorScheme(.dark)
        }
    }
}

/// Outer dark background + inner 390×844 frame matching
/// `testing/Android/.../MainActivity.kt` and `testing/web`.
struct RootView: View {
    var body: some View {
        ZStack {
            // Outer: matches web's body background (#111)
            Color(red: 0x11 / 255.0, green: 0x11 / 255.0, blue: 0x11 / 255.0)
                .ignoresSafeArea()

            // Inner: 390×844 phone frame matching web's #root
            ContentView()
                .frame(width: 390, height: 844)
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
