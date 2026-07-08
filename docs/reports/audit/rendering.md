# Phase 12 Audit — rendering

READ-ONLY audit. Scope: 12 IR properties flagged as "rendering" in the
Phase 12 spec: ImageRendering, ShapeRendering, TextRendering, ColorScheme,
ForcedColorAdjust, PrintColorAdjust, BackgroundBlendMode, MixBlendMode,
Isolation, ColorRendering, FieldSizing, InputSecurity, Zoom, InterpolateSize.
(The spec lists 14 names with a "typography dup" note on TextRendering; the
`irmodels/properties/rendering/` folder itself contains 12 property files.)

## Inventory

**IR-model locations** (mirror-tree reference, what platforms *should* mirror):

| Property | IR location |
|---|---|
| ImageRendering | `irmodels/properties/images/` |
| ShapeRendering | `irmodels/properties/svg/` |
| TextRendering | `irmodels/properties/typography/` |
| BackgroundBlendMode / MixBlendMode | `irmodels/properties/color/` |
| Isolation | `irmodels/properties/performance/` |
| ColorScheme | **no IR file found** under `rendering/` or `appearance/` — the name doesn't map to a property; there's only `ColorInterpolation*` + `PrintColorAdjust`. Likely a drift between the Phase 12 spec text and the IR. |
| ColorRendering, ForcedColorAdjust, PrintColorAdjust, FieldSizing, InputSecurity, Zoom, InterpolateSize, ContentVisibility, ColorInterpolation, ColorInterpolationFilters, ImageOrientation, ImageResolution | `irmodels/properties/rendering/` (12 files) |

**CSS parsers**: present for every IR property above. Parser value flavors
(from `longhands/rendering/*PropertyParser.kt`):

- `Zoom`: `normal | reset | <percentage> | <number>` — all four handled.
- `ForcedColorAdjust`: `auto | none | preserve-parent-color` + Raw catch-all.
- `ImageOrientation`: `none | from-image | <angle> [flip]?` (flip-without-angle = 0deg flip).
- `ImageResolution`: `from-image | <dpi|dpcm|dppx>` (all normalized to dppx).
- `PrintColorAdjust`, `FieldSizing`, `InputSecurity`, `InterpolateSize`,
  `ContentVisibility`, `ColorInterpolation*`, `ColorRendering`: strict enums.

**Fixtures**: `examples/properties/rendering/longtail.json` (exists, covers
31 components across the 11 non-blend rendering properties) plus the 3 Zoom
and 2 BlendMode components implicit in the baseline set under
`examples/properties/transforms/*` and `examples/properties/effects/*`. No
consolidated `audit-phase12.json` for the rendering category; the existing
`longtail.json` already enumerates every enum variant called for in the
parser. `audit-phase12.json` exists for **other** Phase-12 categories
(borders, layout, sizing, backgrounds, transforms, spacing, typography,
effects) but **not for rendering** — consistent with the category being
mostly no-op-on-native.

**Baselines** (`testing/baseline/`): 15 of 327 PNGs match the rendering
category — all of them Zoom (3 variants × 3 platforms = 9) and BlendMode
(Multiply, Screen × 3 platforms = 6). Nine other rendering properties have
**zero baselines** across all three platforms. The `094_Input_Field.png`
baseline is for an input-rendering fixture, not `input-security`.

## Platform triplet structure (partially canonical)

| Property family | Android | iOS | Web | Verdict |
|---|---|---|---|---|
| `rendering/*` (12 long-tail props) | Category-level `RenderingConfig/Extractor/Registration.kt` (3 files, 242 lines) + separate `Zoom{Config,Extractor}.kt`. **No applier file.** | Category-level `Rendering{Config,Extractor,Applier}.swift` (3 files, 73 lines). Applier is `_ = cfg` no-op. | Per-property triplet (9 × {Config,Extractor,Applier}.ts + `_dispatch.ts`). Canonical. |
| ImageRendering | Not in `style/images/` — only parser reads it; no engine triplet. | Not found in `StyleEngine/images/`. | Likely in `images/` (not audited in depth). |
| ShapeRendering | `style/svg/` — not a per-property triplet. | `StyleEngine/svg/` — category-level. | Per-property. |
| TextRendering | `style/typography/other/` (Android has no standalone extractor; folded into TypographyExtractor). | `StyleEngine/typography/other/TextRendering{Extractor,Applier}.swift`. Per-property. | Per-property. |
| BackgroundBlendMode / MixBlendMode | `style/effects/blend/BlendMode{Config,Extractor,Applier}.kt` (merged both). | Same path in iOS, merged. | Same path in web, merged. **Mirror-tree violation**: IR lives in `color/`, engine in `effects/blend/`. |
| Isolation | `style/performance/Isolation{Config,Extractor,Applier}.kt` ✓ mirrors IR `performance/`. | Same ✓. | Same ✓. |

Contract violations:

1. **Android: no rendering applier.** `StyleApplier.kt` imports `RenderingConfig`
   and `ZoomConfig`, stores them on `Config`, gates `hasAnyProperties` on
   `rendering.hasRenderingProperties || zoom.hasZoom` — but `applyConfig()`
   never reads them to produce a `Modifier`. `Zoom 1.5` does not scale the
   rendered box on Android; all 12 rendering enums are silently dropped.
2. **iOS: explicit identity no-op.** `RenderingApplier.contribute(_ cfg) { _ = cfg }`
   with a header comment ("Everything else has no analog" + "TODO(phase-11):
   plumb image-rendering through the Image pipeline"). Honest but means
   `zoom: 1.5` renders at 1.0 on iOS too.
3. **Only Web applies anything.** `ZoomApplier.ts` emits `{ zoom: 1.5 }` (a
   real CSS declaration), FieldSizing/ForcedColorAdjust/InputSecurity emit
   their csstype-widened declarations. The 3-platform visual parity for
   `zoom` baselines is achieved only because the Android/iOS "1.5" and "0.75"
   fixtures render at identity AND the baseline PNGs were captured at
   identity — the test is green for the wrong reason.
4. **Mirror-tree drift for BlendMode**: IR `color/BackgroundBlendModeProperty.kt`
   + `color/MixBlendModeProperty.kt` live under `color/`, but all three
   engine implementations sit under `effects/blend/`. Per CLAUDE.md's
   "byte-for-byte parallel" rule, the engine files should be at
   `{platform}/.../color/BackgroundBlendMode{Config,Extractor,Applier}`.
5. **ColorScheme in the Phase 12 brief doesn't resolve to an IR file.** The
   closest IR property is `ColorInterpolation` or the out-of-scope
   `appearance/ColorScheme*` (not located). Treat the brief as an alias.

## Static-rendering divergences (the actual risk)

- **Zoom 1.5 / 0.5 / 0.75 / 2.0**: Web scales the box; Android + iOS render
  identity. Baselines exist and pass because they were captured from identity-
  rendering builds. A Web re-baseline without coordinated Android/iOS update
  would produce a 3-way divergence (Web at 1.5× size, others at 1×). This is
  a ticking baseline correctness bomb, not a current test failure.
- **ImageRendering `pixelated` vs `auto`**: edge-case requires an upscaled
  bitmap to be visible. No fixture upscales a raster — all use solid colors.
  Even if the appliers plumbed the enum through (they don't on Android/iOS),
  the current fixtures wouldn't reveal the difference. Need a bitmap +
  `transform: scale(4)` variant to actually test.
- **ForcedColorAdjust**: only affects Windows high-contrast mode. Expected
  no-op on all three rendering targets (macOS/iOS/Android sim). WAI.
- **FieldSizing: content**: Web emits `fieldSizing: content` — Chrome 123+
  honors it, shrinking an `<input>` to content width. Android + iOS have
  no `<input>` analog in the SDUI; no fixture renders an input. Untestable
  with current harness.
- **InputSecurity: none**: Web-only security hint; no-op on native. WAI.
- **InterpolateSize**: experimental CSS keyword; Web will only honor in
  Chrome 129+. Android + iOS: no animation path that would observe this
  hint statically. WAI.
- **BackgroundBlendMode / MixBlendMode**: baselined 2 variants (Multiply,
  Screen); BlendMode is Phase 4 work and already passes. Out-of-scope for
  rendering Phase 12 fixes.
- **Isolation: isolate**: creates a stacking context. Passes Phase 4. Not
  baselined standalone, but visually no-op unless paired with blend-mode
  children — covered by BlendMode fixtures.

## Static test run

Not executed. Rationale:

- `test-all.sh` requires Android emulator + iOS simulator + vite; the worktree
  already carries `SKIP_IOS/SKIP_ANDROID/SKIP_WEB` knobs but running would
  produce write-side-effects (screenshots, report/, audit/rendering/snapshot/)
  and the brief says READ-ONLY.
- With 9 of 12 properties lacking any applier on Android/iOS, a run would
  produce three identity-rendered platforms that all match each other and
  all match a (stale) baseline. That's noise.
- The real signal is the code-audit findings above, reproducible without a
  run by reading `StyleApplier.kt:220,275,362` (Android wires up the config
  then drops it) and `RenderingApplier.swift:18` (`_ = cfg`).

## Done-definition status for rendering category

| Criterion | Status |
|---|---|
| Fixtures cover parser variants | Partial — `longtail.json` covers enum variants but no bitmap-upscaling variant for ImageRendering, no input-element variant for FieldSizing/InputSecurity |
| Triplets on all three platforms | **Partial** — Web per-property ✓; Android + iOS use category-level facades, and Android has **no applier file at all** |
| `test-all.sh` clean, SSIM ≥ 0.95 | Green-for-wrong-reason on Zoom/BlendMode (identity rendering). Other 9 props unbaselined. |
| Baseline committed | 5 of ~33 variants baselined (Zoom ×3, BlendMode ×2). 28 missing. |
| Coverage matrix row in `testing/README.md` | Not flipped |

**Overall: NOT DONE.** Two real cross-platform bugs (Android silently drops
`Zoom` + all rendering enums; iOS explicit no-op on same), one contract
violation (BlendMode mirror-tree drift), and 28 missing baselines. The Zoom
baselines are a latent divergence once any platform stops rendering at
identity.

## Recommendations (for a follow-up PR, out of scope here)

1. **Wire `ZoomConfig` into Android `applyConfig()`** as `Modifier.scale(zoom)`
   (or document a decision to ignore `zoom` and delete the extractor). Same
   for iOS via `.scaleEffect(zoom)` on the root, guarded against collision
   with the Phase 8 transforms pipeline.
2. **Add `ImageRendering` plumbing** on Android (`BitmapDrawable` filter flag)
   and iOS (`Image.interpolation(.none|.high)`) once the SDUI exposes image
   nodes — mirror the web applier.
3. **Move BlendMode engine files** from `effects/blend/` to `color/` across
   all three platforms to satisfy the byte-for-byte mirror rule, or formally
   document the category reassignment in `CLAUDE.md`.
4. **Add two fixture variants** to `longtail.json`: (a) a raster image + 4×
   scale to exercise `image-rendering: pixelated`, (b) an input-like primitive
   to exercise `field-sizing: content` on web (skip-baselined on native).
5. **Baseline the 9 unbaselined rendering props** — even if all three
   platforms render identity, committing the identity baselines makes the
   "green for wrong reason" state explicit and catches future regressions
   when appliers actually start working.
6. **Reconcile the Phase 12 brief's `ColorScheme` bullet** — either add an
   IR property + parser + triplet, or drop from the spec.
