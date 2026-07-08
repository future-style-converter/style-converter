# Phase 12 audit — category: background

Scope: BackgroundColor, BackgroundImage, BackgroundPosition(+X/Y/Block/Inline),
BackgroundSize, BackgroundRepeat, BackgroundOrigin, BackgroundClip,
BackgroundAttachment, BackgroundBlendMode.

Fixture added: `examples/properties/backgrounds/audit-phase12.json`
(14 edge-case components).

Pipeline status at audit time: `./test-all.sh` could not complete a clean
three-platform run for this fixture. iOS build failed with an Xcode
`XCBuildData build.db: disk I/O error` (stale derived-data). A concurrent
phase-12 audit on the *transforms* category raced us on the shared paths
`out/tmpOutput.json`, `testing/Android/app/src/main/assets/tmpOutput.json`,
`testing/web/public/ir-components.json`: by the time the Android/web captures
fired, the IR on disk was the **typography** audit's run. Android and web
captured 32/57 screenshots that have *nothing to do with* this fixture; they
were discarded. SSIM numbers therefore cannot be reported in this pass — a
re-run with exclusive access to the worktree is required. I proceeded with
(a) an isolated CSS→IR run into `/tmp/bg-audit-out` to inspect the IR the
parser actually produces for each variant, and (b) a code audit of the three
runtime style-engine folders.

## Parser findings (from isolated IR dump)

Each row = one audit variant. "IR" = what landed in `tmpOutput.json`.

| Variant | IR result | Status |
|---|---|---|
| `Audit_Multi4Layers` (4 comma-separated bg-image + position + size) | BackgroundImage has 4 layers ✓, BackgroundSize has 4 tuples ✓, **BackgroundPositionX/Y collapse to the first layer only** (IR shows scalar `0.0`, not 4 entries) | BUG |
| `Audit_LinearAngleMultiStops` (135deg, 6 stops) | 6 stops with correct `position` percentages, angle `135deg` ✓ | OK |
| `Audit_RadialEllipseClosestSide` | `BackgroundImage.type = "radial-gradient"`, but **`ellipse` and `closest-side` get consumed as color-stops** (`{"color":{"original":"ellipse"}}`). Shape/size/position fields on `RadialGradient` are always `null` (parser returns `RadialGradient(null,null,null,…)` — see `BackgroundImagePropertyParser.parseRadialGradient`, line 135). | BUG |
| `Audit_ConicFrom30degAt25` | `from 30deg` captured as `angle:30`, but the `at 25% 25%` prefix is discarded (no position field populated — see line 163, `ConicGradient(fromAngle, null, …)`). | BUG |
| `Audit_GradientOklabInterpolation` (`linear-gradient(in oklab, red, blue)`) | `in` and `oklab` are parsed as (invalid) color stops; no color-space hint on IR. | BUG |
| `Audit_UrlBroken404` | URL retained unchanged. Runtime behaviour not observed (see pipeline note). | PARSE-OK |
| `Audit_AttachmentFixedScrollable` | `BackgroundAttachment: ["fixed"]` ✓ | OK |
| `Audit_ClipTextGradient` | **Two `BackgroundClip` properties emitted** (one for `background-clip`, one for `-webkit-background-clip`) — both `["TEXT"]`. Duplicate. | BUG |
| `Audit_OriginContentBoxBigPad` | `BackgroundOrigin: ["content-box"]` ✓, size 100%×100% kept. | OK |
| `Audit_SizeMixedPercent` | `BackgroundSize: [{w:50,h:25}]` (percent-tagging preserved elsewhere in IR) ✓ | OK |
| `Audit_RepeatSpaceRound` | `BackgroundRepeat: [{x:"space",y:"round"}]` ✓ | OK |
| `Audit_BlendModeMultiply` | `BackgroundBlendMode: ["MULTIPLY"]` parsed, but no platform has a `BackgroundBlendMode{Config,Extractor,Applier}` triplet (see below). | PARSE-OK / RUNTIME-MISSING |
| `Audit_PositionBlockInline` | `[CSS Parser] Removed invalid properties: background-position-block, background-position-inline`. IR classes exist (`BackgroundPositionBlockProperty.kt`, `…InlineProperty.kt`) but the parser drops them — the longhand registry is not wired. | BUG |
| `Audit_DataUrlBase64` | `url: "data:image/png;base64,ivborw0kggoaaa…"` — **base64 payload lowercased** (`BackgroundImagePropertyParser.parse` line 32 does `trimmed.lowercase()` and then parses the lowered string). PNG will not decode. | BUG |

## Cross-platform style-engine parity

IR has **11** property classes in `irmodels/properties/background/`.
Runtime coverage:

| Property | iOS triplet | Web triplet | Android triplet |
|---|---|---|---|
| BackgroundColor | (via generic ColorConfig) | (via generic) | (via `style/color/ColorConfig`) |
| BackgroundImage | ✓ | ✓ | ✗ — only `BackgroundImageRenderer` monolith |
| BackgroundPosition | ✓ | ✓ | ✗ — config lives in `style/color/ColorConfig.kt` |
| BackgroundPositionX | ✗ | ✗ | ✗ |
| BackgroundPositionY | ✗ | ✗ | ✗ |
| BackgroundPositionBlock | ✗ | ✗ | ✗ |
| BackgroundPositionInline | ✗ | ✗ | ✗ |
| BackgroundSize | ✓ | ✓ | ✗ |
| BackgroundRepeat | ✓ | ✓ | ✗ |
| BackgroundOrigin | ✓ | ✓ | ✗ — only `BackgroundBoxApplier` monolith |
| BackgroundClip | ✓ | ✓ | ✗ — same monolith |
| BackgroundAttachment | ✓ | ✓ | ✗ |
| BackgroundBlendMode | ✗ | ✗ | ✗ |

Notable Android violations of the canonical triplet contract
(`testing/Android/…/style/background/`):

- No `{Property}{Config,Extractor,Applier}.kt` files exist. Instead:
  `BackgroundBoxApplier.kt` (375 lines — exceeds the ≤200 / soft-cap ~300
  rule), `BackgroundImageRenderer.kt` (292 lines), `RepeatingGradientHelper.kt`,
  `BackgroundBoxConfig.kt`, `BackgroundBoxExtractor.kt`.
- Background configs (`BackgroundImageConfig`, `BackgroundPositionConfig`,
  `BackgroundRepeatConfig`, `BackgroundSizeConfig`) are defined inside
  `com.styleconverter.test.style.color.ColorConfig.kt` — they do not live in
  the `style/background/` package at all. iOS/web reference equivalents from
  the mirror path; Android is the outlier.

## Recommended fixes (ordered by leverage)

1. **Stop lowercasing the whole value in `BackgroundImagePropertyParser`.**
   Lowercase only for the keyword / function-name dispatch; preserve the
   original casing inside `url(...)` so data URIs and case-sensitive remote
   URLs survive. (One-line fix.)
2. **Implement radial-gradient shape/size/position parsing** — the first
   comma-segment can be `<shape> [<size> | <length>{2}] [at <position>]`.
   Today the code drops to `parseColorStop` on segment 0 and the `ellipse`
   keyword ends up as an invalid stop. Same shape for conic `at <position>`.
3. **Register `background-position-block` / `background-position-inline`** in
   the longhand parser registry (the IR models already exist).
4. **Expand multi-layer BackgroundPosition** into N `BackgroundPositionX` /
   `BackgroundPositionY` entries (mirroring BackgroundSize / BackgroundImage
   cardinality). Currently only the first layer is kept.
5. **De-duplicate `-webkit-background-clip`** — map both aliases to a single
   `BackgroundClip` IR property rather than emitting two.
6. **Introduce gradient `<color-interpolation-method>` support** (`in oklab`,
   `in srgb`, `in hsl longer hue`) — today it corrupts the stop list.
7. **Android parity refactor**: split `BackgroundBoxApplier` and
   `BackgroundImageRenderer` into per-property triplets under
   `style/background/`, move the `Background*Config` types out of
   `style/color/ColorConfig.kt`. Add missing `BackgroundAttachment`,
   `BackgroundOrigin`, `BackgroundClip`, per-axis position and blend-mode
   appliers.
8. **Add `BackgroundBlendMode{Config,Extractor,Applier}`** on all three
   platforms (IR already has it, parser already emits it).

## Pipeline-quality fixes (separate concern, but block this audit)

- The three audit categories running in parallel on the same worktree share
  `out/tmpOutput.json`, `testing/web/public/ir-components.json`, and the
  Android asset. `test-all.sh` needs per-fixture output directories or a
  worktree-level lockfile to be safe for concurrent use — `flock` isn't
  available on macOS without brew and the instructions assumed it was.
- iOS build should be retried from a clean `testing/iOS/build` directory when
  the XCBuildData DB reports `disk I/O error` — current script exits hard.

## Done-definition status for the category

| Criterion | Status |
|---|---|
| Fixture exercises every value variant | Partial (this audit fixture focuses on edge cases; per-property fixtures already existed). |
| Triplet on all three platforms | **FAIL** — Android has zero triplets under `style/background/`; iOS/web missing 4–5 properties. |
| `test-all.sh` clean on this fixture | **NOT RUN** — blocked by iOS build failure and concurrent-audit race. |
| SSIM ≥ 0.95 all variants | **UNKNOWN**. |
| Baseline committed | **NO** — no `UPDATE_BASELINE=1` run performed (would have been invalid given race). |
| Coverage matrix row in `testing/README.md` | Not flipped. |

Read-only audit complete.
