// swift-tools-version:6.3
//
// Package.swift — SwiftPM manifest for the iOS/SwiftUI runtime style engine.
//
// Lives at the REPO ROOT (not runtimes/swiftui/) because SwiftPM requires
// the manifest at the package root for clean consumption — consumers add
// the repo URL and get the "StyleConverterRuntime" product directly. The
// actual sources live under runtimes/swiftui/ to mirror the sibling
// runtimes (runtimes/compose, runtimes/web); the target 'path:' fields
// below point there.
//
// The harness app (apps/ios-harness) depends on this package via a local
// xcodegen 'packages:' entry (path: ../..) — see apps/ios-harness/project.yml.

import PackageDescription

let package = Package(
    // Product/target name matches the sibling runtime naming
    // (StyleConverterRuntime ↔ runtimes/compose's runtime package).
    name: "StyleConverterRuntime",
    // iOS 16 floor: the renderer uses Grid/LazyVGrid layout paths and
    // ImageRenderer-adjacent SwiftUI API that landed in iOS 16 (matches
    // the harness's project.yml deploymentTarget: "16.0").
    platforms: [.iOS(.v16)],
    products: [
        // Single library product: the full runtime style engine —
        // StyleEngine (per-property Config/Extractor/Applier triplets),
        // Models (IR decode), Renderer (IRComponent → SwiftUI View).
        .library(name: "StyleConverterRuntime", targets: ["StyleConverterRuntime"]),
    ],
    targets: [
        .target(
            name: "StyleConverterRuntime",
            path: "runtimes/swiftui/Sources/StyleConverterRuntime",
            // Retro (finding A2#8): the 43 per-category README.md stubs this
            // block used to exclude are DELETED. They claimed every folder was
            // "Empty — properties migrate in one-at-a-time … per the
            // testing/ROLLOUT.md phase plan" while holding 10–34 implementation
            // files each, and cited a path (testing/ROLLOUT.md) that does not
            // exist. Per-category coverage is auditable from
            // `node tools/visual/coverage-audit.mjs` (generated:
            // tools/visual/COVERAGE.md), which reports the registered facade
            // signal AND the stricter real dedicated-applier floor — strictly
            // more than an `ls` of stub files could. With the files gone the
            // exclude list is not just dead but harmful: SwiftPM emits an
            // "Invalid Exclude … File not found" warning per stale entry.
            // Wave 34 (lane F1) — the five bundled per-script fallback faces
            // (Noto Sans Arabic / Armenian / Bengali / Hebrew / Khmer
            // Regular, ~537 KB, OFL 1.1). They ship INSIDE the runtime, not
            // in the harness app, because the runtime is what registers them
            // with Core Text (StyleEngine/typography/font/
            // ScriptFallbackFonts.swift `registered`) — a consumer gets
            // per-script fallback with no integration step. `.process`
            // flattens Resources/Fonts/*.ttf to the bundle root, which is
            // what makes the `Bundle.module.url(forResource:withExtension:)`
            // lookup there a plain basename lookup.
            resources: [
                .process("Resources"),
            ],
            swiftSettings: [
                // The engine was authored under Swift 5 language mode
                // (project.yml SWIFT_VERSION 5.9). Pin the mode so the
                // 6.3 tools version above doesn't opt us into Swift 6
                // strict concurrency before the code is audited for it.
                .swiftLanguageMode(.v5),
            ]
        ),
        .testTarget(
            name: "StyleConverterRuntimeTests",
            dependencies: ["StyleConverterRuntime"],
            path: "runtimes/swiftui/Tests/StyleConverterRuntimeTests",
            swiftSettings: [
                // Same language mode as the target under test.
                .swiftLanguageMode(.v5),
            ]
        ),
    ]
)
