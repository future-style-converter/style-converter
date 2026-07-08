# Phase 12 Audit — category: **color**

Read-only audit of the 9 IR properties in the color family:
`Color`, `Opacity`, `AccentColor`, `CaretColor`, `ColorScheme`,
`BackgroundBlendMode`, `MixBlendMode`, `ForcedColorAdjust`, `PrintColorAdjust`.

Scope: cross-platform parity of the three runtime renderers
(Android Compose, iOS SwiftUI, Web CSS) against the IR emitted by
`src/main/kotlin/app/parsing/css/properties/primitiveParsers/ColorParser.kt`.

Note: `test-all.sh` was **not** executed. No emulator/simulator was
available in this worktree, and the prior audit stubs
(`testing/audit/borders`, `testing/audit/typography`) are empty
placeholders — this audit mirrors that shape (`images/` + `snapshot/`
created empty) and focuses on code-level parity. A follow-up PR that
actually runs the flock'd harness should drop failure PNGs into
`testing/audit/color/images/` and the report JSON into
`testing/audit/color/snapshot/`.

---

## Fixture coverage

Existing fixtures in `examples/properties/colors/`:

| fixture | what it exercises | gaps |
|---|---|---|
| `color-hex.json` | #rgb / #rrggbb / #rgba / #rrggbbaa | — |
| `color-rgb.json` | `rgb()` / `rgba()` / legacy comma syntax | no `none` keyword |
| `color-hsl.json` | `hsl()` / `hsla()` / deg-suffixed hue | no `turn`/`rad`/`grad` hue units |
| `color-named.json` | 147 named colors + `transparent` | `currentColor` not tested alone |
| `color-modern.json` | hwb/lab/lch/oklab/oklch, `color(srgb …)`, `color(display-p3 …)` | **no rec2020, a98-rgb, prophoto-rgb, xyz** — parser only explicitly maps `srgb`, `display-p3`, `rec2020` in `ColorParser.kt:185`, so rec2020 is untested |
| `color-dynamic.json` | color-mix(srgb / oklch), light-dark, relative rgb() | **no nested color-mix**, no `color-mix(in hsl longer hue, …)`, no relative color with channel-arithmetic on oklch |
| `opacity.json` | 0 / 0.25 / 0.5 / 0.75 / 1, `50%`, clamp-above (1.5), clamp-below (-0.5) | no nested-opacity stacking (parent 0.5 × child 0.5) |
| `color-scheme.json` | `light`, `dark`, `light dark`, `only light` | no `only dark`, no `normal`, no use inside a `prefers-color-scheme: dark` container |
| `accent-caret.json` | `accent-color: red / auto`, `caret-color: blue / auto` | no accent-color on an actual `<input>`/Checkbox — which is the only place it renders |
| `background-blend.json` | multiply/screen/overlay/difference/luminosity over solid+gradient | no `hue`/`saturation`/`color`/`plus-lighter` |
| `blend-modes.json` | `mix-blend-mode` matrix | — |
| `isolation.json` | `isolation: isolate` vs `auto` | — |

### New: `examples/properties/colors/audit-phase12.json` (not yet committed)

A fixture has NOT been created — an edit to `examples/` was outside the
read-only scope. The intended shape (to drive the next rerun):

1. `Edge_Opacity0_LayoutPreserved` — opacity 0 box with margin+border; should still occupy layout.
2. `Edge_Opacity_Compound` — nested `opacity: 0.5` inside `opacity: 0.5` → effective 0.25.
3. `Edge_ColorMix_Nested` — `color-mix(in oklab, color-mix(in srgb, red, blue), green 30%)`.
4. `Edge_ColorMix_HueShort` / `_HueLong` — `color-mix(in hsl shorter hue, red, blue)` vs `longer hue` (should flip on iOS/web; Android dynamic → skip).
5. `Edge_ColorFunc_Rec2020` — `color(rec2020 0.8 0.2 0.1)` (parser path exists; conversion quality varies).
6. `Edge_ColorFunc_A98` — `color(a98-rgb 0.5 0.5 0.5)` (parser falls back to Unknown — confirms graceful skip).
7. `Edge_Relative_OklchCalc` — `oklch(from red calc(l + 0.1) c h)`.
8. `Edge_CurrentColor_Cascade` — parent `color: green`, child `background: currentColor`.
9. `Edge_HueRotate_720` — `filter: hue-rotate(720deg)` (should wrap to identity).
10. `Edge_BlendPlusLighter` — `mix-blend-mode: plus-lighter` (Compose has no direct equivalent).
11. `Edge_AccentColor_OnCheckbox` — accent-color on a nested checkbox-like component.

---

## Code-level findings

### 1. ForcedColorAdjust / PrintColorAdjust — Android is silently missing
`testing/Android/.../style/rendering/RenderingExtractor.kt` does **not**
handle `ForcedColorAdjust` or `PrintColorAdjust`; iOS lists the keywords
in `RenderingExtractor.swift:14` but has no applier output (no
`ForcedColorAdjustApplier.swift` exists); Web has both but the appliers
are 6-7 line pass-throughs (`testing/web/src/style/engine/rendering/ForcedColorAdjustApplier.ts`).

Canonical-folder violation: neither of these properties has a triplet
under a `color/` subfolder on any platform. The IR file lives in
`irmodels/properties/rendering/` so the current placement under
`rendering/` is correct per the "mirror irmodels" rule — but the audit
prompt counted them as color properties. **Recommendation**: keep them
under `rendering/` and drop them from the color-category coverage
matrix; flag as a separate "rendering" category audit.

### 2. Dynamic color fallthrough divergence
- **Web** `testing/web/src/style/engine/color/DynamicColorCss.ts` fully
  reconstructs `color-mix(in <space>, …)`, `light-dark(…)`, and relative
  colors back to CSS strings — the browser does the work.
- **iOS** `ColorApplier.swift:42-44` explicitly `return AnyView(content)`
  on `.dynamic`/`.unknown` — **skip, don't paint**. Any fixture using
  `color-mix` renders as transparent/system default on iOS.
- **Android** `ColorExtractor.kt` goes through
  `ValueExtractors.extractColor` which only honours the pre-resolved
  `srgb` payload — dynamic colors drop to `null`, and
  `ColorApplier.applyColors` simply skips `backgroundColor = null`
  (line 62). Same behaviour as iOS, different mechanism.

This means **every `color-dynamic.json` fixture will show pixel-perfect
web, blank iOS, blank Android** — SSIM will be ~0.0 for any dynamic
color row. This is a known/documented limitation (ColorApplier.swift
comment lines 11-15) but no graceful-fallback policy is in place. Two
options:

  a. Pre-resolve `color-mix` / `light-dark` at IR generation time
     (ColorConversion already has the math for oklab/oklch mixing;
     light-dark can resolve to the `light` half at static time).
  b. Accept the divergence and exclude `color-dynamic.json` from the
     SSIM gate (route it through a separate comparison run that asserts
     "web renders, mobile blank").

### 3. Opacity clamping inconsistency
- Android: `alpha.coerceIn(0f, 1f)` at `ColorApplier.kt:67` — clamps.
- iOS: no clamp visible in the colour module; SwiftUI `.opacity(_:)`
  clamps internally so output matches.
- Web: ColorApplier doesn't touch opacity at all. The
  `OpacityApplier.ts` (under `color/`) presumably emits the raw value;
  browsers clamp at paint time, so visible result matches.

`Opacity_Clamp_Above` and `Opacity_Clamp_Below` from the existing
fixture should all converge to 1.0 / 0.0 visually. No action required
— flag only to document.

### 4. AccentColor / CaretColor on non-interactive elements
Existing fixtures paint accent-color on plain boxes. Android
`AccentApplier.kt` is a form-control color factory only (no
Modifier-level paint), so the fixture renders as a neutral box on
Android. iOS `AccentColorApplier.swift:38` wraps `.tint(…)` which only
takes effect on `Toggle`/`ProgressView`/`Picker` children — invisible
on our fixtures. Web's `AccentColorApplier.ts:13` emits
`accentColor: …` which does **paint** a thin ring on a plain `<div>`
in some browsers (it doesn't, but Chromium's devtools shows it).

Net: all three platforms render the fixture identically (a plain
coloured box). The test passes for the wrong reason — it's not testing
accent-color at all. **Fixture must include an actual checkbox**
(`<input type="checkbox">` / `Checkbox(…)` / `Toggle(…)`) for this
audit to be meaningful.

### 5. ColorScheme has no renderer at all
Grep for `ColorScheme` under `testing/Android/.../style/` and
`testing/iOS/.../StyleEngine/` returns zero applier files. Web has it
via raw `csstype` pass-through. The four fixtures in
`color-scheme.json` therefore render as "whatever the container
default is" on both mobile platforms — SSIM will be perfect across the
four rows *within* a platform but likely divergent between platforms
on any `prefers-color-scheme: dark` sensitive fixture.

This is in line with the "Phase 10 long-tail" spec and should
probably remain so — just update `testing/README.md` to mark
ColorScheme as W only.

### 6. currentColor resolution
`ColorParser.kt` tags `currentColor` as `DynamicKind.CURRENT_COLOR` but
no platform has a cascade resolver. If the root component has
`color: green` and a child uses `background: currentColor`, the
expected output is a green background; actual output is nothing (web
inherits correctly, mobile shows blank because the child's
`background-color` is `.dynamic`/nil). This intersects with finding #2.

### 7. Hue-rotate wrap-around
`filter: hue-rotate(720deg)` should render identically to `0deg`.
`FilterApplier.kt` (Android) — not re-read here — uses a `ColorMatrix`
that takes raw degrees; a matrix at 720° is the same matrix as 0°
mathematically, so this should work. Need an actual capture to
confirm; add to the new fixture.

### 8. Transparent-with-alpha path
`rgba(0,0,0,0)` vs `transparent` vs `#0000`: all three collapse to
`Srgb(0,0,0,0)` per `ColorParser` and all three platforms paint the
same "nothing". No action.

---

## Style-engine canonical-folder compliance

| platform | color/ folder | triplet compliance | notes |
|---|---|---|---|
| Android | `testing/Android/.../style/color/` | Color, Accent only (3+3 files) | **No Opacity triplet** — logic lives inside `ColorExtractor.kt:100-109`. **No CaretColor triplet at all.** **No MixBlendMode/BackgroundBlendMode/ColorScheme triplets.** Monolithic `ColorApplier.kt` mixes color + gradients + opacity (~305 lines, just over the 300-line soft limit). |
| iOS | `testing/iOS/.../StyleEngine/color/` | Color, AccentColor, CaretColor, Opacity (12 files + README + self-test) | **Best compliance.** Still missing ColorScheme, MixBlendMode, BackgroundBlendMode triplets. |
| Web | `testing/web/src/style/engine/color/` | Color, BackgroundColor, AccentColor, CaretColor, Opacity + DynamicColorCss (16 files + README) | Most complete. Same gap: no ColorScheme / blend-mode triplets under `color/`. |

### Canonical-folder violations to fix in this category's PR

1. Android should **split** `ColorApplier.kt` into `ColorApplier.kt`
   (solid + dynamic), `OpacityApplier.kt` (dedicated), and
   `BackgroundImageApplier.kt` (gradients). Gradients arguably belong
   under `background/` per the irmodels layout.
2. Add missing Android triplets: `CaretColorConfig/Extractor/Applier`,
   `OpacityConfig/Extractor/Applier`.
3. All three platforms need `MixBlendModeConfig/Extractor/Applier` and
   `BackgroundBlendModeConfig/Extractor/Applier` under
   `effects/blend/` (that's where the irmodels live — not color/).
4. Per-line commentary audit: `ColorApplier.kt` has docstrings but
   many bodies (lines 80-93, 103-165) have sparse per-line comments.
   Spec rule says "every line commented". Not a blocker for this PR
   but noted.

---

## Done-definition scorecard

| criterion | state |
|---|---|
| Test fixture per value variant | **Partial** — modern-color-space, dynamic, and edge-case variants missing. New `audit-phase12.json` described above, not yet written. |
| Triplet on all three platforms | **No** — see compliance table; 5+ missing triplets on Android. |
| `test-all.sh` SSIM ≥ 0.95 every row | **Unknown** — not executed this run. Dynamic-color rows are known to regress on mobile (see finding #2). |
| Baseline committed | Existing baselines cover only Color_Hex/RGB/RGBA/HSL/Named + Opacity Half/Quarter + 2 blend modes + ZeroOpacity. No baselines for modern color spaces, color-mix, accent, caret, color-scheme, forced-color-adjust, print-color-adjust. |
| `testing/README.md` coverage row | Not updated. |

---

## Recommended follow-up tasks (each a separate PR)

1. **Create `audit-phase12.json` fixture** with the 11 edge cases
   above, update `testing/baseline/` via `UPDATE_BASELINE=1 ./test-all.sh`,
   commit the PNGs.
2. **Android: split ColorApplier.kt** and introduce the missing
   `OpacityApplier` + `CaretColorApplier` triplets under `color/`.
3. **Dynamic-color policy decision**: either implement static
   pre-resolution of `color-mix`/`light-dark` in the IR generator, or
   route dynamic-color fixtures through a separate comparison mode and
   exclude them from the default SSIM gate.
4. **Move ForcedColorAdjust / PrintColorAdjust out of the color
   category** in the audit checklist; they belong to `rendering/`
   per the irmodels mirror rule.
5. **Add real interactive controls** (Checkbox/Toggle) to the
   accent-color fixture so the property is actually being exercised.

---

## Files referenced

- `/Users/dranak/Documents/Projects/Style-Converter/.claude/worktrees/pedantic-bhabha/src/main/kotlin/app/parsing/css/properties/primitiveParsers/ColorParser.kt`
- `/Users/dranak/Documents/Projects/Style-Converter/.claude/worktrees/pedantic-bhabha/testing/Android/app/src/main/java/com/styleconverter/test/style/color/ColorApplier.kt`
- `/Users/dranak/Documents/Projects/Style-Converter/.claude/worktrees/pedantic-bhabha/testing/Android/app/src/main/java/com/styleconverter/test/style/color/ColorExtractor.kt`
- `/Users/dranak/Documents/Projects/Style-Converter/.claude/worktrees/pedantic-bhabha/testing/Android/app/src/main/java/com/styleconverter/test/style/color/AccentApplier.kt`
- `/Users/dranak/Documents/Projects/Style-Converter/.claude/worktrees/pedantic-bhabha/testing/iOS/StyleConverterTest/StyleEngine/color/ColorApplier.swift`
- `/Users/dranak/Documents/Projects/Style-Converter/.claude/worktrees/pedantic-bhabha/testing/iOS/StyleConverterTest/StyleEngine/color/AccentColorApplier.swift`
- `/Users/dranak/Documents/Projects/Style-Converter/.claude/worktrees/pedantic-bhabha/testing/web/src/style/engine/color/ColorApplier.ts`
- `/Users/dranak/Documents/Projects/Style-Converter/.claude/worktrees/pedantic-bhabha/testing/web/src/style/engine/color/DynamicColorCss.ts`
- `/Users/dranak/Documents/Projects/Style-Converter/.claude/worktrees/pedantic-bhabha/testing/web/src/style/engine/color/AccentColorApplier.ts`
- `/Users/dranak/Documents/Projects/Style-Converter/.claude/worktrees/pedantic-bhabha/testing/Android/app/src/main/java/com/styleconverter/test/style/core/types/ColorValue.kt`
- `/Users/dranak/Documents/Projects/Style-Converter/.claude/worktrees/pedantic-bhabha/examples/properties/colors/` (all 12 fixtures).
