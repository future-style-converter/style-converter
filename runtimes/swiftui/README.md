# StyleConverterRuntime — the iOS runtime

The iOS runtime style engine: a SwiftPM package (manifest at the repo-root
`Package.swift`, sources here) that turns Style Converter IR into SwiftUI
views, one **Config / Extractor / Applier** triplet per property under
`Sources/StyleConverterRuntime/StyleEngine/<category>/` (the canonical
33-category tree shared with `runtimes/web` and `runtimes/compose` — see
the repo-root `CLAUDE.md` for the per-property contract). `Models/` decodes
the IR, `Renderer/` (StyleBuilder + ComponentRenderer) builds the views.
iOS 16+, Swift 5 language mode.

```bash
# from the repo root — needs Xcode 15+
xcodebuild test -scheme StyleConverterRuntime \
  -destination 'platform=macOS,variant=Mac Catalyst,arch=arm64'   # XCTest suite (964 tests)
```

Rendered and screenshot-tested by [`apps/ios-harness/`](../../apps/ios-harness/)
via `./test-all.sh` (or `./test-ios.sh` for iOS alone).

Wave 8 (#35/#36): `@keyframes` animations and state-flip transitions
execute in **property space** — pure state-at-t math in
`StyleEngine/animations/` (`AnimationDriver` phase arithmetic,
`KeyframeInterpolator` per-segment easing + tier interpolation,
`AnimationResolver`/`TransitionResolver` glue) driven by
ComponentRenderer's TimelineView clock or the pinned
`CAPTURE_ANIMATION_TIME` hook (`schema/spec/07-animations.md` §5,
`docs/DYNAMIC_CAPTURE.md` §4). `background-image: url(...)` raster
layers (data URIs, local files) now paint with background-size /
-position / -repeat honored (`StyleEngine/background/BackgroundURLImage`
+ the pure `BackgroundImageGeometry`); remote http(s) URLs stay a
defined, logged no-op for capture determinism.
