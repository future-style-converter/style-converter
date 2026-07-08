# Style-Converter

CSS → typed IR, rendered natively by three runtime style engines (Web /
Android / iOS). The engines are the product; static Compose/SwiftUI code
writers are roadmap (the old `logic/` generators were removed — the
converter emits IR only via `--to ir`).

## Quick Start

```bash
# Convert CSS to IR
./gradlew :converter:run --args="convert --from css --to ir -i examples/visual-test.json -o out"

# Full 3-platform test (convert → render on web/Android/iOS → SSIM compare)
./test-all.sh examples/visual-test.json
```

## Architecture

```
JSON Input → CSS Parser → IR Model → runtime style engines
                                      ├─ Web     (testing/web/,    DOM/CSS)
                                      ├─ Android (testing/Android/, Compose)
                                      └─ iOS     (testing/iOS/,    SwiftUI)
```

### Project Structure

```
converter/src/main/kotlin/app/
├── irmodels/                    # IR data models (567 property files)
│   ├── IRDocument.kt            # IRDocument, IRComponent
│   ├── IRProperty.kt            # Base interface
│   ├── ValueTypes.kt            # IRLength, IRColor, IRAngle, IRTime
│   └── properties/              # One file per CSS property
└── parsing/css/
    ├── CssParsing.kt
    └── properties/
        ├── longhands/           # Property parsers + registry
        ├── shorthands/          # Shorthand expanders
        └── primitiveParsers/    # Color, Length, Angle parsers

testing/Android/                 # Android runtime style engine
├── app/src/main/
│   ├── assets/tmpOutput.json    # IR loaded at runtime (regenerated, gitignored)
│   └── java/.../
│       ├── style/               # canonical engine tree (per-property
│       │                        #   Config/Extractor/Applier triplets;
│       │                        #   the legacy sdui/ folder was folded in here)
│       ├── screenshot/          # Auto screenshot capture
│       │   ├── ScreenshotManager.kt
│       │   └── ScreenshotCaptureScreen.kt
│       └── ui/
│           └── ComponentListScreen.kt

testing/iOS/                     # iOS runtime style engine (StyleEngine/)
testing/web/                     # Web runtime style engine (src/style/engine/)
testing/screenshots/             # Pulled screenshots from device
```

## Testing Workflow

### Option 1: Full Automated Test Script

```bash
./test-all.sh [input.json]        # all three platforms
./test-ios.sh [input.json]        # iOS only (thin wrapper: SKIP_ANDROID=1 SKIP_WEB=1)
```

**What `test-all.sh` does:**
1. Runs Style Converter → `out/tmpOutput.json` (`--to ir`)
2. Copies the IR into each platform bundle (Android assets, iOS Resources, web public/)
3. Builds + launches each platform (Android emulator, iOS simulator, vite + puppeteer)
4. Captures per-component screenshots on each platform
5. Runs the 3-way SSIM comparison → `testing/report/index.html`

**Skips:** `SKIP_ANDROID=1`, `SKIP_IOS=1`, `SKIP_WEB=1` env vars.

### Option 2: Auto Screenshot Capture (Per-Component)

The Android app automatically captures individual screenshots of each component on launch.

**How it works:**
1. On app startup, clears any existing screenshots
2. Loads components from `tmpOutput.json`
3. Renders each component one by one
4. Captures a screenshot of each component using Compose's GraphicsLayer
5. Saves to device storage with indexed filenames
6. Shows completion summary with adb pull command

**Screenshot location (Android 11+):**
```
/sdcard/Android/data/com.styleconverter.test/files/test_screenshots/
```

**Pull screenshots:**
```bash
adb pull /sdcard/Android/data/com.styleconverter.test/files/test_screenshots/ ./screenshots/
```

**Screenshot naming:** `{index}_{ComponentName}.png` (e.g., `000_SVG_Fill_Basic.png`)

**Each screenshot includes:**
- Component name and ID
- Property count badge
- Rendered component preview
- List of applied properties

### Manual Testing

```bash
# 1. Convert CSS to IR
./gradlew :converter:run --args="convert --from css --to ir -i examples/your-test.json -o out"

# 2. Copy to Android assets
cp out/tmpOutput.json testing/Android/app/src/main/assets/

# 3. Build and install
cd testing/Android
JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew installDebug

# 4. Launch app (screenshots auto-capture on startup)
adb shell am start -n com.styleconverter.test/.MainActivity

# 5. Wait for capture to complete, then pull screenshots
adb pull /sdcard/Android/data/com.styleconverter.test/files/test_screenshots/ ./screenshots/
```

## IR Value Normalization

All CSS values normalize to universal formats:

| Type | CSS | Normalized | Platform Use |
|------|-----|------------|--------------|
| Colors | oklch, hsl, hex, rgb | sRGB (0-1 floats) | `Color(r,g,b,a)` |
| Lengths | px, pt, em, rem, % | pixels (absolute only) | `Dp` |
| Angles | deg, rad, turn, grad | degrees | `rotate()` |
| Time | s, ms | milliseconds | animation duration |
| Font Weight | normal, bold, 100-900 | numeric 100-900 | `FontWeight` |
| Opacity | 0-1, 0%-100% | 0-1 float | `alpha()` |

**`null` means runtime-dependent:** `var()`, `calc()`, `em`, `%`, `vw` cannot be pre-computed.

---

## Style-engine architecture (canonical)

All three runtime renderers (Android, iOS, Web) share **one** folder structure
for their style engines. That structure is locked to the folders under
`converter/src/main/kotlin/app/irmodels/properties/` and
`converter/src/main/kotlin/app/parsing/css/properties/longhands/`. These three trees —
irmodels, parser, style-engine — are kept byte-for-byte parallel: the same
category paths, the same property files. If a property lives at
`irmodels/properties/borders/sides/BorderTopWidth.kt`, its runtime
implementations live at the mirror paths:

```
testing/Android/app/src/main/java/com/styleconverter/test/style/
  └── borders/sides/BorderTopWidth{Config,Extractor,Applier}.kt
testing/iOS/StyleConverterTest/StyleEngine/
  └── borders/sides/BorderTopWidth{Config,Extractor,Applier}.swift
testing/web/src/style/engine/
  └── borders/sides/BorderTopWidth{Config,Extractor,Applier}.ts
```

The categories (mirrored 1:1 from irmodels/properties/):

```
animations/ · appearance/ · background/ · borders/ (sides/, radius/, image/)
color/ · columns/ · container/ · content/ · counters/ · effects/ (clip/, mask/,
shadow/, filter/, shapes/, blend/) · experimental/ · global/ · images/
interactions/ · layout/ (advanced/, flexbox/, grid/, position/) · lists/
math/ · navigation/ · paging/ · performance/ · print/ · regions/ · rendering/
rhythm/ · scrolling/ · shapes/ · sizing/ · spacing/ · speech/ · svg/ · table/
transforms/ · typography/
```

If a category isn't yet implemented on a platform the folder still exists,
empty, with a `README.md` stub describing what goes there. This keeps coverage
auditable by `ls`.

## Per-property contract

Each property ships as a **triplet per platform**, in the canonical subfolder:

| file | purpose |
|---|---|
| `{Property}Config.{ext}`    | Typed value struct (what was extracted, ready for rendering) |
| `{Property}Extractor.{ext}` | `IRProperty → Config`. Handles every CSS value flavor the parser recognizes (see `converter/src/main/kotlin/app/parsing/css/properties/longhands/{category}/{Property}PropertyParser.kt` for the full list) |
| `{Property}Applier.{ext}`   | `Config → platform output` (Compose Modifier on Android, SwiftUI modifier on iOS, CSS declaration on Web) |

### Hard rules for every file

- **Short** — target ≤200 lines. If a file grows past ~300, split it (e.g. one
  applier per value family).
- **Every line commented.** Comments explain the *why*, not just the *what*.
  For extractors, reference the exact CSS spec section or parser file you're
  mirroring. For appliers, cite the platform API you're calling and why.
- **No silent fallthroughs.** If a value variant isn't supported on this
  platform yet, log it via the PropertyTracker or emit a TODO + keep the
  cross-platform comparison honest.
- **Registered, not dispatched inline.** Each `Extractor` registers itself
  with the platform's `PropertyRegistry`; the main `StyleApplier` reads from
  the registry instead of a giant `switch`. This makes coverage introspectable
  at runtime (see `PropertyRegistry.allRegistered()`).

## Done definition for a property

A property is "done" when **all five** are true:

1. **Test fixture** in `examples/properties/{category}/{property}.json` exercises
   every value variant listed in the parser's value flavors (see the CSS
   parser's `{Property}PropertyParser.kt`). One component per variant.
2. **Triplet exists on all three platforms** in the matching subfolder, with
   the commenting + size rules above.
3. **`./test-all.sh examples/properties/{category}/{property}.json`** runs cleanly:
   - zero `decode error` rows
   - every pair's SSIM ≥ 0.95 for every variant
   - no "size mismatch" warnings
4. **Baseline committed** — `UPDATE_BASELINE=1 ./test-all.sh …` runs; the
   resulting `testing/baseline/{platform}__{NNN}_{Variant}.png` files are
   staged and the baseline PRs are in-scope for the category PR.
5. **Documentation updated** — the category's coverage matrix row in
   `testing/README.md` is flipped to ✓.

## Implementation phases

The rollout plan is **complete** (Phases 0–11, 12 commits). Full
execution history + per-phase status lives in `testing/ROLLOUT.md`;
per-category coverage matrix is at `testing/COVERAGE.md` and regenerated
by `node testing/coverage-audit.mjs`.

Current coverage vs the 550-property IR catalogue (33 categories):

| platform | claimed | coverage |
|---|---:|---:|
| Android | 550 / 550 | 100% |
| iOS | 550 / 550 | 100% |
| Web | 550 / 550 | 100% |

Baseline: 327 cross-platform comparisons on the visual-test fixture,
0 regressions.

Phase summary (see ROLLOUT.md for commit hashes):

- **Phase 0** Canonical folder scaffold on all three platforms; Android
  refactored to mirror irmodels.
- **Phase 1** Primitive extractors (lengths, colors, angles, times,
  numbers, keywords).
- **Phase 2** spacing (26 props).
- **Phase 3** sizing (7).
- **Phase 4** colors + background (37).
- **Phase 5** borders (47).
- **Phase 6** typography (110).
- **Phase 7 / 7b** layout (61) — scaffold then flexbox/grid/position
  implementation.
- **Phase 8** effects + transforms (38).
- **Phase 9** animations + transitions + timelines (29) — config
  extraction only; runtime animation execution is follow-up work per
  platform.
- **Phase 10** long tail (~150 across 22 categories).
- **Phase 11** baseline harness + `testing/COVERAGE.md` + audit script.

---

## Implementation status

See `testing/COVERAGE.md` for the live per-category / per-platform
coverage matrix (regenerate with `node testing/coverage-audit.mjs`).

The legacy "Android SDUI Implementation Status" section that used to live
here listed ~60 working properties and a handful of TODOs, pinned to the
pre-rollout architecture. That status is now stale: all three platforms
claim the full 550-property IR surface (minus three L4 gaps — see
COVERAGE.md), the legacy `sdui/` folder has been folded into the
canonical `style/` tree, and the per-property contract documented above
is enforced across all categories.

For audit purposes the truncated legacy status block follows, untouched
from pre-Phase-0 for historical reference:

<details>
<summary>Pre-Phase-0 Android status (historical)</summary>

**Layout & Sizing** (12 properties)
- `Width`, `Height`, `MinWidth`, `MaxWidth`, `MinHeight`, `MaxHeight`
- `BlockSize`, `InlineSize`, `MinBlockSize`, `MaxBlockSize`, `MinInlineSize`, `MaxInlineSize`
- `AspectRatio`

**Flexbox Layout** (8 properties)
- `Display` (flex, grid, block, none)
- `FlexDirection`, `FlexWrap`, `FlexGrow`
- `JustifyContent`, `AlignItems`, `AlignContent`
- `Gap`, `RowGap`

**Spacing** (16 properties)
- `PaddingTop`, `PaddingRight`, `PaddingBottom`, `PaddingLeft`
- `PaddingBlockStart`, `PaddingBlockEnd`, `PaddingInlineStart`, `PaddingInlineEnd`
- `MarginTop`, `MarginRight`, `MarginBottom`, `MarginLeft`
- `MarginBlockStart`, `MarginBlockEnd`, `MarginInlineStart`, `MarginInlineEnd`

**Colors & Background** (4 properties)
- `BackgroundColor`
- `BackgroundImage` (linear/radial/conic gradients)
- `Opacity`

**Borders** (24 properties)
- `BorderTopWidth`, `BorderRightWidth`, `BorderBottomWidth`, `BorderLeftWidth`
- `BorderTopColor`, `BorderRightColor`, `BorderBottomColor`, `BorderLeftColor`
- `BorderTopStyle`, `BorderRightStyle`, `BorderBottomStyle`, `BorderLeftStyle`
- `BorderTopLeftRadius`, `BorderTopRightRadius`, `BorderBottomRightRadius`, `BorderBottomLeftRadius`
- `BorderStartStartRadius`, `BorderStartEndRadius`, `BorderEndStartRadius`, `BorderEndEndRadius`
- `BoxShadow` (multiple, inset, spread, blur)

**Effects** (6 properties)
- `Filter` (blur, brightness, contrast, grayscale, sepia, hue-rotate, invert, saturate)
- `BackdropFilter`
- `ClipPath` (inset, circle, ellipse, polygon)
- `MixBlendMode`, `Isolation`

**Transforms** (8 properties)
- `Transform` (translate, rotate, scale, skew)
- `TransformOrigin`
- `Rotate`, `Scale`, `Translate`, `Zoom`
- `ZIndex`

**Typography** (11 properties)
- `Color` (text)
- `FontSize`, `FontWeight`, `FontStyle`, `FontFamily`
- `LineHeight`, `LetterSpacing`
- `TextAlign`, `VerticalAlign`
- `TextDecoration` (line, style, color)

**Visibility** (4 properties)
- `Visibility`, `Overflow`, `OverflowX`, `OverflowY`

---

### Config Extraction Only (Data Available, Needs Custom Rendering)

These properties have `extract*Config()` functions that parse the data, but require additional implementation:

**Requires Custom Canvas/Shader:**
- Border Images: `BorderImageSource`, `BorderImageSlice`, `BorderImageWidth`, `BorderImageOutset`, `BorderImageRepeat`
- Masks: `MaskImage`, `MaskSize`, `MaskRepeat`, `MaskPosition`, `MaskMode`, `MaskComposite`, `MaskOrigin`, `MaskClip`, `MaskType`
- SVG: `Fill`, `Stroke`, `StrokeWidth`, `StrokeDasharray`, etc.

**Requires Compose Animation APIs:**
- Animations: `AnimationName`, `AnimationDuration`, `AnimationTimingFunction`, `AnimationDelay`, `AnimationIterationCount`, `AnimationDirection`, `AnimationFillMode`, `AnimationPlayState`
- Transitions: `TransitionProperty`, `TransitionDuration`, `TransitionTimingFunction`, `TransitionDelay`
- Scroll Timelines: `ScrollTimeline`, `ViewTimeline`, `AnimationTimeline`

**Handled at Container Level (not Modifier):**
- Flex items: `FlexBasis`, `FlexShrink`, `AlignSelf`, `Order`
- Grid: `GridTemplateColumns`, `GridTemplateRows`, `GridArea`, `GridColumnStart/End`, `GridRowStart/End`

**Text Styling (via TextStyleApplier):**
- `TextOverflow`, `LineClamp`, `MaxLines`, `WhiteSpace`, `WordBreak`
- `TextShadow`, `TextJustify`, `TextAlignLast`, `Hyphens`

---

### Config Extraction Implemented (Batch 12)

These properties now have full config extraction via dedicated functions:

**Multi-Column Layout** - `extractMultiColumnConfig()`
- `ColumnCount`, `ColumnWidth`, `ColumnGap`
- `ColumnRuleWidth`, `ColumnRuleStyle`, `ColumnRuleColor`
- `ColumnSpan`, `ColumnFill`

**Outline** - `extractOutlineConfig()` in OutlineApplier.kt
- `OutlineWidth`, `OutlineColor`, `OutlineStyle`, `OutlineOffset`

**Font Variants** - `extractFontVariantConfig()`
- `FontVariantNumeric`, `FontVariantCaps`, `FontVariantLigatures`
- `FontFeatureSettings`, `FontKerning`, `FontOpticalSizing`

**Text Decoration** - `extractTextDecorationConfig()`
- `TextDecorationThickness`, `TextUnderlineOffset`, `TextUnderlinePosition`

**Counters** - `extractCounterConfig()`, `extractQuotesConfig()`
- `CounterReset`, `CounterIncrement`, `CounterSet`, `Quotes`

**Form Styling** - `extractFormStylingConfig()` / `extractAccentConfig()`
- `AccentColor`, `CaretColor`, `ColorScheme`

---

### Not Yet Implemented

**Motion Paths** (5 properties) - CSS motion path animations
- `OffsetPath`, `OffsetDistance`, `OffsetRotate`, `OffsetAnchor`, `OffsetPosition`

**CSS Regions** (11 properties) - Paged media / print layout (N/A on mobile)
- `FlowInto`, `FlowFrom`, `RegionFragment`, `Continue`
- `BookmarkLevel`, `BookmarkLabel`, `BookmarkState`
- `StringSet`, `Running`, `Leader`, `FootnoteDisplay`, `FootnotePolicy`

---

### Not Applicable to Mobile

```
Cursor                           # No mouse cursor on mobile
Resize                           # No drag handles
PageBreak*                       # No printing
```

</details>

---

## Key files reference (current)

Android (canonical tree):
- `testing/Android/app/src/main/java/com/styleconverter/test/style/PropertyRegistry.kt` — coverage registry
- `testing/Android/app/src/main/java/com/styleconverter/test/style/<category>/` — per-category Config / Extractor / Applier triplets
- `testing/Android/app/src/main/java/com/styleconverter/test/style/core/renderer/ComponentRenderer.kt` — engine + legacy dispatch

iOS (canonical tree):
- `testing/iOS/StyleConverterTest/StyleEngine/PropertyRegistry.swift` — registry
- `testing/iOS/StyleConverterTest/StyleEngine/<category>/` — per-category triplets
- `testing/iOS/StyleConverterTest/Renderer/StyleBuilder.swift` + `Renderer/ComponentRenderer.swift`

Web (canonical tree):
- `testing/web/src/style/engine/PropertyRegistry.ts` — registry
- `testing/web/src/style/engine/<category>/` — per-category triplets + per-category `_dispatch.ts`
- `testing/web/src/style/core/renderer/StyleBuilder.ts` — top-level dispatcher

Shared tooling:
- `testing/compare-screenshots.mjs` — 3-way SSIM + pixelmatch + HTML report
- `testing/coverage-audit.mjs` — IR vs PropertyRegistry coverage matrix
- `testing/baseline/` — committed per-platform PNGs for `BASELINE=1 ./test-all.sh`
- `examples/properties/<category>/` — per-category fixture suites

## Adding a new property

When adding a new CSS property to the system:

1. **IR model**: add `irmodels/properties/<category>/<Name>Property.kt`
   (serialized naming: `<Name>Property` — the `Property` suffix is part
   of the filename convention `coverage-audit.mjs` relies on).
2. **Parser**: add `parsing/css/properties/longhands/<category>/<Name>PropertyParser.kt`.
3. **Parser registry**: wire into `PropertyParserRegistry.kt`.
4. **Fixture**: add a component under `examples/properties/<category>/<property>.json`
   exercising every value variant the parser recognises.
5. **Platform engines** — for each of Android / iOS / Web:
   a. Author `Config` + `Extractor` + `Applier` under `style(/Engine)/<category>/`
      matching the "Per-property contract" section above.
   b. Claim the IR type name in `PropertyRegistry` (either directly or via
      a grouped `Set` union).
   c. Add a unit test under the matching `test`/`tests` tree.
6. **Run `./test-all.sh examples/properties/<category>/<property>.json`**;
   iterate until all platform pairs hit SSIM ≥ 0.95 on every variant.
7. **Update baseline** with `UPDATE_BASELINE=1 ./test-all.sh …` and commit
   the captures alongside the engine code.
8. **Verify coverage** with `node testing/coverage-audit.mjs` — the new
   property must show up under the right category on every platform.

## Tech Stack

- Kotlin 2.1.0, Java 21+, Gradle 8.14
- kotlinx.serialization-json 1.6.3
- Android: Compose BOM 2024.12, Material3, API 24+
