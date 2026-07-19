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

/// TITAN-WHITE lane — the WPT capture-canvas contract, corpus-v4 revision.
///
/// WHY WHITE: WPT reftests are authored against the spec-default WHITE
/// page. Many paint WHITE ink (borders/backgrounds — e.g. the six
/// abspos-autopos tests' `border: solid white` frames, ~5,200px of ink)
/// that is SUPPOSED to vanish into the canvas; their references paint none
/// of it. The Chromium browser-ref render (tools/titan/
/// capture-browser-ref.mjs CANVAS_BG) is white from the same boundary, so
/// on the old dark #1A1A2E stage that ink was visible ONLY in our captures
/// — a systematic reftest penalty independent of renderer correctness.
/// All three platforms flip simultaneously (web wptCanvasStyle /
/// composedCanvasStyle, Compose WPT_CANVAS_BACKGROUND, this enum), and
/// corpus-v4 is the FIRST white-canvas snapshot: its numbers are NOT
/// comparable to the v1..v3 dark-canvas corpora.
///
/// The property-fixture (327-pair) capture path keeps its own dark stage —
/// the harness owns that color and only routes through here when
/// `wptCaptureMode` is true, so every committed baseline stays
/// byte-identical.
public enum WPTCanvas {
    /// The corpus-v4 WPT canvas white. `Color(white: 1)` == #FFFFFF.
    public static let background = Color(white: 1)

    /// Pure canvas-background decision for the iOS capture canvases — the
    /// 1:1 twin of Compose's `captureCanvasBackground` (unit-pinned in
    /// WPTCaptureModeTests so the mode split can never silently drift).
    /// WPT capture mode → the white canvas; anything else returns
    /// `defaultBackground` VERBATIM (the harness's #1A1A2E stage), keeping
    /// the 327-pair baselines byte-identical.
    public static func captureBackground(wptCaptureMode: Bool,
                                         defaultBackground: Color) -> Color {
        wptCaptureMode ? background : defaultBackground
    }

    /// The WPT default TEXT INK — spec BLACK, the corpus-v4.1 ink
    /// sub-boundary within the v4 white-canvas era. Real WPT pages paint
    /// default prose in the UA `color: CanvasText` black; through
    /// corpus-v4.0 every surface kept the harness's near-white family
    /// (web body `color:#eee`, this runtime's
    /// `InheritedText.defaultTextColor` 0.93-white, the ref injection's
    /// old `color:#fff`), so on the white canvas default-ink text vanished
    /// on BOTH sides of the diff and prose reftests passed VACUOUSLY.
    /// From v4.1 all four surfaces flip together: the ref injection
    /// (capture-browser-ref.mjs `:where(body) { color:#000 }`), web
    /// (index.html wpt-mode rule + PlaceholderContent's WPT_MODE ink),
    /// Compose's `WPT_DEFAULT_TEXT_INK`, and this constant.
    /// `Color(white: 0)` == opaque #000000.
    ///
    /// The FONT half of the same v4.1 sub-boundary needs NO iOS hook:
    /// the ref injection now also pins the harness Inter stack (visible
    /// black prose exposed the ref's default-serif vs harness-Inter wrap
    /// -point divergence — capture-browser-ref.mjs REF_FONT_STACK), but
    /// this runtime's default text face is ALREADY the registered
    /// "Inter" unconditionally (ComponentRenderer's `.custom("Inter")` /
    /// FontFaceMatcher bottom-outs, WPT and non-WPT modes alike), so the
    /// native side already matches the newly pinned ref face and only
    /// ref + web carry explicit font pins.
    public static let textInk = Color(white: 0)

    /// Pure default-text-ink decision — the ink twin of
    /// `captureBackground` (unit-pinned in WPTCaptureModeTests, mirrored
    /// by Compose's `defaultTextInk` in WptCanvasBackgroundTest.kt). WPT
    /// capture mode bottoms text ink out at the corpus-v4.1 spec BLACK;
    /// every other path returns `defaultInk` VERBATIM so the dark-stage
    /// property-fixture pipeline keeps its historical near-white defaults
    /// (`InheritedText.defaultTextColor` and the placeholder contrast
    /// pick) byte-identically — the 327 committed baselines depend on
    /// that side never moving.
    public static func captureTextInk(wptCaptureMode: Bool,
                                      defaultInk: Color) -> Color {
        wptCaptureMode ? textInk : defaultInk
    }
}
