# Phase 12 Audit — `effects` category

Fixture: [`examples/properties/effects/audit-phase12.json`](../../examples/properties/effects/audit-phase12.json) — 20 edge-case variants covering clip-path, filter, backdrop-filter, mask, visibility, overflow, overflow-clip-margin, overscroll-behavior, and legacy `clip`.

## Pipeline status

`test-all.sh` was **not fully run** in this audit session. Environment gaps:

| Requirement | Status |
|---|---|
| `flock` binary (for serialized runs) | absent on macOS — no BSD `flock(1)`; needs `util-linux` / fallback |
| Android `adb` | not installed in worktree PATH |
| iOS Simulator + `xcrun simctl` | available (`iPhone 17 Pro` listed, shutdown) |
| `node` + vite | available |
| Kotlin IR convert | verified — fixture parses with **1 generic** (expected: legacy `clip`) |

Snapshot + failure-image directories exist (`testing/audit/effects/snapshot/`, `testing/audit/effects/images/`) but are empty; re-run on CI with Android emulator booted and `flock` installed:

```bash
flock testing/audit/effects.lock \
  ./test-all.sh examples/properties/effects/audit-phase12.json
```

## Static engine coverage matrix

Inspected:
- iOS: `testing/iOS/StyleConverterTest/StyleEngine/effects/{clip,filter,mask,shadow,blend}` + `visibility/`
- Android: `testing/Android/app/src/main/java/com/styleconverter/test/style/effects/*` + `style/visibility/`, `style/scrolling/`
- Web: `testing/web/src/style/engine/effects/*` + `engine/visibility/`, `engine/scrolling/`

| Variant | iOS | Android | Web | Notes |
|---|---|---|---|---|
| `clip-path: polygon(evenodd, …)` | ✓ (`ClipExtractor.swift:189`) | ✗ **gap** | ✓ | Android `ClipPathExtractor` has no `evenodd`/`fillRule` branch. |
| `clip-path: inset(0 round 50%)` non-square | ✓ | ✓ | ✓ | Percent round OK; confirm pill render matches. |
| `clip-path: path('M…Z M…Z')` disconnected | ✓ | ✓ (via `SvgPathParser.kt`) | ✓ | Validate `Path.FillType.EvenOdd` on Android for overlap. |
| `clip-path: url(#missing)` | ⚠ | ⚠ | native CSS | All three must degrade to "no clip" — no crash. |
| Filter chain order `brightness→blur` vs `blur→brightness` | ✓ chain preserved | ✓ | ✓ | Renders **must differ**; if SSIM between the two variants ≥ 0.98 that's a bug (order collapsed). |
| `drop-shadow` before/after `blur` | ✓ | ✓ | ✓ | Same ordering invariant. |
| `backdrop-filter` on no-background element | ✓ | ✓ (`BackdropBlurApplier.kt`) | ✓ | Expect pass-through / no-op; Android's `GraphicsLayer` backdrop needs a composition source. |
| `mask-image: linear-gradient` soft edge | ✓ | ✓ | ✓ | |
| Multi-source mask + `mask-composite: exclude` | ✓ (`MaskComposite.exclude`) | ✓ (`MaskCompositeValue`) | ✓ | Verify layer count matches comma-list length. |
| `mask-mode: luminance` on colored gradient | ✓ | ✓ | ✓ | Red/lime/blue should produce greyscale-luminance mask, not per-channel alpha. |
| `mask-repeat: space round` two-axis | ✓ parsed | ✓ parsed | ✓ | Android/iOS likely collapse to single-axis — high-risk for SSIM < 0.95. |
| `visibility: collapse` table-row | ✗ no table layout | ✗ | ✓ | On iOS/Android collapses to `hidden` — spec non-conformance, document. |
| `visibility: collapse` flex-item | ≈hidden | ≈hidden | ✓ zero-size | Three-way divergence expected. |
| `visibility: collapse` block | =hidden | =hidden | =hidden | Three-way match expected. |
| `overflow-clip-margin: 20px` | ✗ **gap** (no `OverflowClipMargin*` under iOS `scrolling/`) | ✗ **gap** | ✓ (`web/.../scrolling/OverflowClipMargin*.ts`) | Add triplet on iOS + Android, or document as web-only. |
| `overscroll-behavior: contain` / `none` | ✗ **gap** (only `ScrollingApplier.swift` monolith) | ✗ **gap** (no `OverscrollBehavior*` triplet, rolled into `ScrollApplier.kt`) | ✓ (full triplet set) | Non-mirror to irmodels — violates the style-engine folder contract. |
| Legacy `clip: rect(auto, 100px, 200px, 0)` | ? | ? | ✓ native | CSS parser falls back to `GenericProperty` (`[CSS Parser] No parser for 'clip'`) — expected, deprecated shorthand. Consider dropping from audit or adding a `ClipLegacyRectPropertyParser`. |

## Actionable gaps (ranked)

1. **Android `clip-path` `evenodd`** — add `fillRule` field to `ClipPathConfig.kt`, extract `evenodd` keyword in `ClipPathExtractor.kt`, set `Path.fillType = PathFillType.EvenOdd` in applier.
2. **iOS + Android `overscroll-behavior` triplet split** — the style-engine contract mandates one-file-per-property mirroring `irmodels/properties/scrolling/`. Currently rolled into `ScrollApplier.kt`/`ScrollingApplier.swift`. Refactor into 9 triplets (`OverscrollBehavior{,X,Y,Block,Inline}{Config,Extractor,Applier}`).
3. **iOS + Android `overflow-clip-margin` triplet** — missing entirely; web has full triplet. Mirror to iOS/Android or open an exception note in `scrolling/README.md`.
4. **`mask-repeat: space round`** two-axis — verify all three platforms honour per-axis values; spot-check via the `MaskRepeat_SpaceRound_TwoAxis` variant when the pipeline runs.
5. **Legacy `clip`** — decide: parse (add longhand parser) or drop from effects fixture set. Currently emits `GenericProperty` which no platform renders.

## Done-definition checklist (per CLAUDE.md)

- [x] Test fixture committed
- [ ] Triplets exist on all three platforms — **failing** for `evenodd`, `overscroll-behavior`, `overflow-clip-margin`
- [ ] `test-all.sh` clean run — **not executed** (env)
- [ ] Baselines committed — pending clean run
- [ ] `testing/README.md` coverage row flipped — pending

## Next step

On a machine with `flock` + booted Android emulator + iOS simulator:

```bash
UPDATE_BASELINE=0 flock testing/audit/effects.lock \
  ./test-all.sh examples/properties/effects/audit-phase12.json \
  2>&1 | tee testing/audit/effects/snapshot/run.log
# then copy any SSIM<0.95 PNG pair into testing/audit/effects/images/
```
