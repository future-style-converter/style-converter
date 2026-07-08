# Typography audit (Phase 12)

## Scope
- Category IR properties scanned: 106 in `src/main/kotlin/app/irmodels/properties/typography/`.
- New fixture authored: `examples/properties/typography/audit-phase12.json` — **57 components** covering edge cases the existing per-property fixtures don't exercise.
- Capture result: **iOS 57 / 57 succeeded; Android 0 / 57 usable; web 0 / 57 usable** — see "Capture pipeline failure" below.

## Capture pipeline failure (blocks SSIM analysis)

Ran under serialized lock, but the `test-all.sh` harness has a structural flaw
that surfaces when many audit agents run in parallel: each platform's
**shared writable inputs** (Android `testing/Android/app/src/main/assets/tmpOutput.json`,
web `testing/web/…/tmpOutput.json`) are overwritten in the "Sync IR" step by
whichever worktree runs next, but the Android and web capture steps read those
files after a long build/emulator delay — so by the time Android and web
actually render, the IR has been clobbered by another agent's fixture.

Evidence from my run (`/tmp/testall-typo3.log`):
- Conversion: 57 components, `out/tmpOutput.json` OK, IR synced to iOS/Android/web.
- iOS capture: pulled 57 PNGs (all 57 typography components rendered correctly — the iOS simulator reads the IR once into the app bundle, then is immune to further writes).
- Android capture: the app crashed/restarted repeatedly (counter reset from 33 → 0 → 14 several times), pulled only 20 screenshots, and inspection of `testing/Android/app/src/main/assets/tmpOutput.json` after the run showed **a completely different fixture** (components named `NavUp_Auto`, `ReadingOrder_Normal` — these are from a parallel agent's `navigation` fixture). My Typography_* names were not in the Android IR at capture time.
- Web capture: reported "32 canvases captured" but zero of those canvases match my Typography_* component names; they are from a stale `contain` / `content-visibility` / `will-change` run held in the vite dev server.
- Report merge: the comparison manifest shows `pairs: null` for every Typography_* row because only iOS has a screenshot — Android/web columns are empty.

This means **no SSIM data is available** for the typography edge cases I
authored in this run. Two things are broken:

1. **Harness lock fragmentation.** Observed concurrent agents using at least
   three different advisory-lock paths: `/tmp/sc-testall.lock`,
   `/tmp/sc-testall.lockfile`, `testing/.test-all.lock`, plus one invocation
   using `perl flock` on a fourth path. Agents only serialize against peers
   using the *same* path; cross-path pairs trample one another.
2. **IR-sync is not re-run before Android/web capture.** `test-all.sh` syncs
   IR in Step 2, then runs iOS (builds app bundle, takes a snapshot of the
   JSON), Android (rebuilds apk against disk state at build time) and web
   (serves IR live from the vite filesystem). If another agent's sync step
   runs between my Step 2 and my Android/web capture windows, Android+web
   render the wrong input.

The iOS results below are therefore valid for iOS-only sanity checking, but
the cross-platform SSIM comparison that Phase 12 depends on cannot be
produced until the harness issues are fixed. I did not retry after the first
full run because contention was increasing (4+ agents queued on locks at the
time of writing).

## Read-only code audit — suspected divergences

Because the capture pipeline couldn't produce SSIM numbers, I did a static
read of the typography engine on all three platforms for the properties my
fixture targets. These are the highest-confidence places where the three
renderers will disagree if/when the harness is repaired:

### writing-mode (vertical-rl / vertical-lr / sideways-*)
- **iOS**: `testing/iOS/StyleConverterTest/StyleEngine/typography/writing/WritingModeApplier.swift:11-18` only sets `agg.verticalWritingMode = true` on the aggregate with an explicit "Not flipping `touched` — …would break measurement; the flag is a TODO" comment. **No rotation or layout swap is emitted.** iOS will render vertical-rl text as horizontal.
- **Android**: `testing/Android/app/src/main/java/com/styleconverter/test/style/typography/text/WritingModeApplier.kt:47-67` applies a `graphicsLayer { rotationZ = 90f }` (or -90f) and provides a `VerticalTextWrapper` that also swaps width/height constraints.
- **Web**: `testing/web/src/style/engine/typography/WritingModeApplier.ts:11-14` emits native `writing-mode: vertical-rl` — browser handles it correctly.
- **Hypothesis**: iOS will be the odd one out on every `WritingMode_*` fixture; Android rotates but does not re-flow per-character (differs from web's glyph-level handling, especially for Latin characters in vertical text — `text-orientation: mixed` vs `upright`).
- **Fixture components affected**: `Typography_WritingMode_VerticalRl_Latin`, `Typography_WritingMode_VerticalLr_Mixed`, `Typography_WritingMode_Sideways_Upright`.

### text-emphasis / text-emphasis-position / text-emphasis-style
- **iOS**: marked explicitly unsupported. `testing/iOS/StyleConverterTest/StyleEngine/typography/unsupported/UnsupportedRubyEmphasisExtractor.swift` captures `TextEmphasis`, `TextEmphasisStyle`, `TextEmphasisPosition`, `TextEmphasisColor` into a diagnostic config but renders nothing. Comment: "SwiftUI has no ruby/emphasis support; captured for audit."
- **Android**: `testing/Android/app/src/main/java/com/styleconverter/test/style/typography/TextEmphasisApplier.kt` implements a custom overlay (`TextWithEmphasis` composable rendering marks in a separate layer above each character).
- **Web**: `TextEmphasisApplier.ts`, `TextEmphasisStyleApplier.ts`, `TextEmphasisPositionApplier.ts` — native CSS pass-through.
- **Hypothesis**: iOS renders plain text with no marks; Android renders with marks above via custom composable; web uses the browser's native emphasis mark layout. All three will diverge visually.
- **Fixture components affected**: `Typography_TextEmphasis_Filled`, `Typography_TextEmphasis_Open`.

### font-family fallback chains
- **iOS**: `FontFamilyApplier.swift:18` `agg.fontFamilyPrimary = cfg.names.first` — only the **first** name in the fallback list is ever tried. Generic flags (`hasMonospace`, `hasSerif`, `hasRounded`) are OR-folded but specific named fallbacks after the first missing font are ignored. So `"NonExistent", sans-serif` attempts "NonExistent", fails, and falls through to SwiftUI's default system font rather than sans-serif specifically.
- **Web**: emits the whole chain to CSS; browser walks it properly.
- **Android**: goes through `TextStyleApplier.kt` / `TypographyApplier.kt` — needs verification but the single-`fontFamilyPrimary` pattern suggests similar truncation.
- **Hypothesis**: iOS picks a default font when the first name is missing; web picks the next family in the list. Subtle metric and glyph differences.
- **Fixture components affected**: `Typography_FontFamily_MissingFallback`, `Typography_FontFamily_AllMissing`, `Typography_FontFamily_UnicodeName`.

### line-clamp / -webkit-line-clamp
- **Android**: `testing/Android/app/src/main/java/com/styleconverter/test/style/typography/LineClampApplier.kt` exists as a dedicated applier.
- **Web**: native `-webkit-line-clamp` + `display: -webkit-box` triggers ellipsis truncation reliably.
- **iOS**: no `LineClamp*.swift` under `StyleEngine/typography/` (grep finds only `BlockEllipsis*`). Likely unhandled — will render all lines.
- **Fixture components affected**: `Typography_LineClamp_3`.

### unicode-bidi / bidi-override with direction: rtl
- **iOS**: `writing/UnicodeBidiApplier.swift` is 14 lines. Reviewing suggests only `isolate` family is propagated; `bidi-override` may no-op.
- **Android**: no dedicated `UnicodeBidi*.kt` visible under typography/ — probably dropped.
- **Web**: direct CSS pass-through handles `bidi-override`.
- **Fixture components affected**: `Typography_UnicodeBidi_BidiOverride`, `Typography_Direction_Rtl`.

### font-style: oblique <angle>
- Quick scan of `FontStyleExtractor.swift` (shallow check) suggests the oblique angle is discarded — SwiftUI's `.italic()` has no angle parameter. Android has similar constraint (`FontStyle.Italic` is boolean-ish). Web accepts the angle natively.
- **Fixture components affected**: `Typography_FontStyle_ObliqueAngle`, `Typography_FontStyle_ObliqueNegative`.

### text-shadow with ≥ 10 comma-separated shadows
- iOS: `TypographyExtractor.swift` handles text-shadow; need verification that the loop iterates all entries, not just the first.
- Android: `workarounds/TextShadowApplier.kt` — renders via draw layer.
- Web: native CSS.
- **Fixture components affected**: `Typography_TextShadow_ManyShadows` (10 shadows).

### text-transform: full-width / full-size-kana
- CSS-native on web. Android `TextStyleApplier` typically maps to Compose's `TextCapitalization` which only supports `Upper`/`Lower`/`Words`/`Sentences` — no full-width. iOS likely the same.
- **Fixture components affected**: `Typography_TextTransform_FullWidth`, `Typography_TextTransform_FullSizeKana`.

### font-variant-numeric composite (`tabular-nums diagonal-fractions`)
- Android `FontVariantApplier.kt` — needs verification that all sub-token combinations map to `FontFeature` settings.
- iOS must route these to `UIFontDescriptor` feature attributes (`.numberCase`, `.characterAlternatives`).
- Web CSS native.
- **Fixture components affected**: `Typography_FontVariantNumeric_TabularFractions`, `Typography_FontVariantNumeric_StackedFractions`, `Typography_FontVariantNumeric_OldstyleSlashed`.

### font-stretch numeric percentage (200%, ultra-condensed)
- Requires a variable font with a `wdth` axis. iOS's system font does not expose wdth to SwiftUI's `.fontWidth(…)` reliably; Android's `FontFamily.SansSerif` likewise won't honour 200% without a custom font. Web inherits the browser's rendering.
- **Fixture components affected**: `Typography_FontStretch_UltraCondensed/UltraExpanded/Percent_200`.

## Passing properties

Cannot enumerate — Android and web did not produce comparable screenshots for
any typography fixture in this run.

## Parser gaps encountered

- `lang` attribute on `Typography_Hyphens_AutoLongWord` was dropped by the CSS
  parser: `[CSS Parser] Removed invalid properties: lang`. `lang` is an HTML
  attribute, not a CSS property, so this is correct — the `hyphens: auto`
  component will still run but with no language hint.
- All other 57 components parsed cleanly with 0 generic fallthroughs.

## Recommendations (ordered by impact)

1. **Fix `test-all.sh` lock fragmentation.** Pick one canonical lockfile path
   (recommend `testing/.test-all.lock` since it's inside the shared tree all
   worktrees touch), document it in `test-all.sh` header, and update all
   audit-agent prompts so every agent uses the same mechanism. Until this is
   done, Phase 12 SSIM data from parallel agents is unreliable.
2. **Snapshot IR into iOS-style bundles for Android/web too.** The root
   contamination is that iOS embeds IR in the `.app` at build time (immune to
   subsequent writes), but Android/web read IR live from disk. Either (a)
   bake IR into the apk at build time and use a per-run-named assets file, or
   (b) pass the IR path as a build/runtime argument and keep per-run copies.
3. **Implement iOS `WritingModeApplier` for real.** It's the single largest
   iOS/other-platform gap this audit identified. The TODO in
   `WritingModeApplier.swift:15` has been there long enough to have shipped.
4. **Decide text-emphasis policy.** Either (a) unify on the Android
   overlay-composable approach on iOS (swiftUI `TextRenderer` in iOS 18+
   could do it), or (b) mark it explicitly "unsupported on mobile" across
   iOS and Android and teach the harness to skip those fixtures.
5. **Re-run this fixture** (`examples/properties/typography/audit-phase12.json`)
   after fix 1/2 ship — all 57 components are CSS-valid, parser-clean, and
   target distinct typography behaviors. A clean run should produce real
   SSIM data to validate the divergence hypotheses above.

## Appendix — iOS-only screenshots available

All 57 iOS renders are in `testing/audit/typography/images/` with the
`NNN_Typography_*` naming from the capture run. They are useful for seeing
what iOS does with each edge case, but cross-platform comparison requires
the harness fix first.
