// swift-tools-version:6.1
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
            // The canonical category tree keeps a README.md stub in every
            // not-yet-populated folder (CLAUDE.md: "coverage auditable by
            // ls"). SwiftPM treats unknown files as unhandled — exclude
            // them explicitly so the build stays warning-free.
            exclude: [
                "StyleEngine/animations/README.md",
                "StyleEngine/appearance/README.md",
                "StyleEngine/background/README.md",
                "StyleEngine/borders/README.md",
                "StyleEngine/borders/image/README.md",
                "StyleEngine/borders/outline/README.md",
                "StyleEngine/borders/radius/README.md",
                "StyleEngine/borders/sides/README.md",
                "StyleEngine/color/README.md",
                "StyleEngine/columns/README.md",
                "StyleEngine/container/README.md",
                "StyleEngine/content/README.md",
                "StyleEngine/counters/README.md",
                "StyleEngine/effects/README.md",
                "StyleEngine/effects/blend/README.md",
                "StyleEngine/effects/clip/README.md",
                "StyleEngine/effects/filter/README.md",
                "StyleEngine/effects/mask/README.md",
                "StyleEngine/effects/shadow/README.md",
                "StyleEngine/experimental/README.md",
                "StyleEngine/global/README.md",
                "StyleEngine/images/README.md",
                "StyleEngine/interactions/README.md",
                "StyleEngine/layout/README.md",
                "StyleEngine/layout/advanced/README.md",
                "StyleEngine/layout/flexbox/README.md",
                "StyleEngine/layout/grid/README.md",
                "StyleEngine/layout/position/README.md",
                "StyleEngine/lists/README.md",
                "StyleEngine/math/README.md",
                "StyleEngine/navigation/README.md",
                "StyleEngine/paging/README.md",
                "StyleEngine/performance/README.md",
                "StyleEngine/print/README.md",
                "StyleEngine/regions/README.md",
                "StyleEngine/rendering/README.md",
                "StyleEngine/rhythm/README.md",
                "StyleEngine/scrolling/README.md",
                "StyleEngine/shapes/README.md",
                "StyleEngine/sizing/README.md",
                "StyleEngine/spacing/README.md",
                "StyleEngine/speech/README.md",
                "StyleEngine/svg/README.md",
                "StyleEngine/table/README.md",
                "StyleEngine/transforms/README.md",
                "StyleEngine/typography/README.md",
            ],
            swiftSettings: [
                // The engine was authored under Swift 5 language mode
                // (project.yml SWIFT_VERSION 5.9). Pin the mode so the
                // 6.1 tools version above doesn't opt us into Swift 6
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
