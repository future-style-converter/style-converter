//
//  WPTCaptureMode.swift
//  StyleConverterRuntime — WPT reftest capture parity.
//
//  A host→runtime AMBIENT flag, cut from the exact same cloth as the
//  styleViewport (#39) and styleKeyframes (wave 8) channels — an
//  EnvironmentKey with a default that keeps every pre-existing consumer
//  byte-identical (StyleViewport.swift / AnimationEnvironment.swift).
//
//  Why it exists: for empty components the renderer paints a SYNTHESIZED
//  placeholder showing the component NAME (ComponentRenderer's leaf
//  branch → PlaceholderLabel). That name label is a harness debug aid,
//  NOT CSS content. In a WPT reftest the native capture is SSIM-compared
//  against the Chromium browser-ref, which paints no such placeholder —
//  so e.g. a bare background-color box got "background color rgb 001"
//  glyphs stamped on it, dragging the comparison down and making it
//  dishonest. When this flag is ON the renderer drops that synthesized
//  name placeholder (browser-ref parity). This is the 1:1 analogue of
//  the web harness's `?wpt=1` (WPT_MODE) empty-visible-text path in
//  apps/web-harness/src/sdui/ComponentRenderer.tsx.
//
//  What it does NOT touch: REAL element text. The IR `_text`/rawText
//  channel (component.text) and genuine text nodes always render — web
//  gives that text precedence over the WPT empty-string suppression, and
//  so do we (see ComponentRenderer.suppressesNamePlaceholder).
//
//  Default FALSE = the normal bundled auto-capture and every SDUI app:
//  the committed 327-pair baselines keep their name placeholders exactly.
//  ONLY the harness inbox/WPT capture path turns it on
//  (CaptureOverrides.titanInbox in ScreenshotCaptureView.captureAllComponents).
//

import SwiftUI

/// Environment key. Default false = product / baseline behavior UNCHANGED:
/// the synthesized component-name placeholder renders exactly as before.
private struct WPTCaptureModeKey: EnvironmentKey {
    static let defaultValue: Bool = false
}

public extension EnvironmentValues {
    /// WPT-reftest capture mode. When true, the renderer suppresses the
    /// SYNTHESIZED component-name placeholder for nameless-empty leaves so
    /// native captures match the browser-ref (which paints no placeholder);
    /// real element text is never suppressed. See the file header.
    // public: set by the harness inbox/WPT capture path via
    // `.environment(\.wptCaptureMode, …)`; unset everywhere else (default
    // false), so no other host needs any change.
    var wptCaptureMode: Bool {
        get { self[WPTCaptureModeKey.self] }
        set { self[WPTCaptureModeKey.self] = newValue }
    }
}

/// TITAN Round 4 (GAP 1, WIDTH half) — the block-flow fill-width channel.
///
/// iOS boxes hug their content (intrinsic SwiftUI sizing — no UA sheet), so
/// a composed WPT `<p>`/`<div>` renders only as wide as its text, while the
/// browser-ref paints it FULL-BLEED: a block-level box with `width: auto`
/// fills its containing block (CSS 2.1 §10.3.3). The web harness already
/// carves this out (`apps/web-harness/src/sdui/ComponentRenderer.tsx` WPT
/// branch: skip the fit-content default so block elements get width:auto),
/// which is exactly why the composed web bars are full-width and the Round-4
/// margins/line-box could take effect. This channel is the iOS analogue: the
/// composed canvas publishes the content-box width, and ComponentRenderer
/// folds it into an auto-width, in-flow box's SizeConfig so its background
/// paints the full width — the SAME fold path as flexStretchWidth.
///
/// Default nil = every other render (product, the 327-pair baseline, the
/// per-component WPT path) UNCHANGED — only the composed canvas sets it, and
/// the fold is additionally gated on wptCaptureMode. ComponentRenderer resets
/// it to nil for its children (block-fill is scoped to the stacked ROOTS —
/// flex/grid items and nested boxes keep their own sizing), so it never
/// leaks past a root.
private struct WPTBlockFlowFillWidthKey: EnvironmentKey {
    static let defaultValue: CGFloat? = nil
}

public extension EnvironmentValues {
    /// The content-box width a composed-WPT block-flow ROOT should stretch
    /// to (nil everywhere but the composed canvas). See the key doc above.
    var wptBlockFlowFillWidth: CGFloat? {
        get { self[WPTBlockFlowFillWidthKey.self] }
        set { self[WPTBlockFlowFillWidthKey.self] = newValue }
    }
}
