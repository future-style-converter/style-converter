# Sizing Category – Phase 12 Audit

Scope: 13 IR properties under `irmodels/properties/sizing/` plus the physical
`Width/Height/Min*/Max*` that ride alongside them (parser lives under
`longhands/sizing/`). Audited: iOS, Android, Web style-engine triplets plus
existing fixtures under `examples/properties/sizing/`.

Fixture added: `examples/properties/sizing/audit-phase12.json` (15 edge-case
components — min>max, aspect-ratio vs max-height / min-height, fit-content
bound, max-content + long text, calc max-width, 100vh, 9999px overflow, zero
width flex item, aspect-ratio overridden by explicit height, pure logical
sizing, max-width:none, min-inline-size forces growth, aspect-ratio: 0/1).

Run status: `./test-all.sh` could not produce a clean three-platform compare
on this host during the audit session — iOS `xcodebuild` failed on a missing
VFS auxiliary directory and Android `installDebug` hit
`INSTALL_FAILED_DUPLICATE_PACKAGE` from a concurrent pending install.
Audit is therefore code-review + fixture design. Findings below do not
depend on the run.

## Findings

### F1 — parser: `max-width: calc(...)` falls through to GenericProperty

`src/main/kotlin/app/parsing/css/properties/longhands/sizing/MaxWidthPropertyParser.kt`
only delegates to `LengthParser.parse`, which does not recognise `calc(...)`.
Running the audit fixture logs:

```
[CSS Parser] No parser for 'max-width', using GenericProperty
```

`MaxHeight`, `MinWidth`, `MinHeight`, and probably the logical `Min/Max*Size`
parsers have the same gap (they all use `LengthParser.parse`). `Width` and
`Height` do handle calc via the longhand parser's `calc(` prefix branch —
asymmetry bug. Fix: route every sizing parser through the same calc-aware
path the Width parser uses, or have `LengthParser.parse` itself accept
`calc(...)` and emit `IRLength(expr=...)`.

### F2 — Android logical→physical merge is wrong order

`testing/Android/.../style/sizing/SizingApplier.kt:40-47` merges logical and
physical with `config.width ?: config.inlineSize`, i.e. physical wins.
CSS spec says the one declared **last** wins (cascade), and the iOS extractor
correctly implements last-write-wins by overwriting `cfg.width` when
`InlineSize` is encountered
(`testing/iOS/.../SizeExtractor.swift:57-68`). Android + Web therefore can
disagree with iOS when a component sets both `width:` and `inline-size:`.
The `BlockSize_100_InlineSize_150` case in the new fixture exercises this;
Web delegates to the browser which honours writing-mode, Android pins
physical, iOS pins whichever came last.

### F3 — Web ignores the `hasAny` short-circuit (minor)

`testing/web/src/style/engine/sizing/SizeApplier.ts` always walks every
field. Harmless — browsers ignore absent keys — but it means the web
applier produces an empty `{}` object for components with no sizing,
which `Object.assign`s a hot-path allocation per render. Consider a
`hasAny` guard to mirror Android/iOS.

### F4 — Intrinsic sizing is only approximated; fixtures over-claim success

All three platforms collapse `min-content` and `max-content` to the same
primitive (`wrapContentWidth` on Android, `.fixedSize` on iOS, native on
web). So `width-intrinsic.json` can pass visually on web while
Android/iOS render the two identically. The new
`Width_MaxContent_With_LongText` / `Width_MinContent_With_LongText` pair
will make this drift measurable once the full `test-all` runs.

### F5 — `height: %` is silently dropped on iOS

`SizeApplierResolve.exact` passes `allowPercent:false` for the height axis
(documented as "CSS auto fallback when parent has no definite height").
That matches the ScrollView-rooted container, but it means
`height:100vh` / `height:50%` diverge from Android (which applies via
`fillMaxHeight` clamped to `[0,1]` with a default 390-wide context, i.e.
renders at container height, not viewport) and web (viewport-anchored).
`Height_100vh` in the audit fixture is the repro.

### F6 — AspectRatio + min/max height interaction is unhandled

`SizingApplier.kt:50-52` appends `.aspectRatio(ratio)` *after*
`heightIn(min, max)`. Compose resolves aspect-ratio inside the clamp, so
`AspectRatio_16_9_Capped_By_MaxHeight` (320×180 requested, capped at 80)
renders 80 tall but width is still 320 — ratio silently violated.
SwiftUI's `.aspectRatio(_, contentMode:.fit)` collapses *both* axes into
the smaller frame, so iOS renders a 142×80 box. Web matches iOS per spec.
Android is the outlier and will fail SSIM. This is a real rendering bug
that the phase-12 fixture will expose on the first green test-all run.

### F7 — `aspect-ratio: 0/1` yields ratio=0 sentinel, treated as "auto"

`AspectRatioValue.kt:51` and `AspectRatioValue.ts:22` both trust
`normalizedRatio` even when it's 0; Android checks `ratio > 0.0` and
skips the modifier, Web emits `aspectRatio: '0'` (browser ignores).
Defensible as invalid-value recovery, but silent.

### F8 — `min-width > max-width` — no platform normalises

CSS spec: `max-width` wins, so effective width becomes max-width.
All three appliers attach the constraints as authored. Compose/SwiftUI
resolve correctly because their `widthIn` APIs clamp max over min at
layout; web browsers do likewise. Expected to pass.

## Files touched

- `examples/properties/sizing/audit-phase12.json` (new fixture, 15 components)
- `testing/audit/sizing.md` (this file)

## Recommended follow-ups (each a separate task)

1. Fix F1: wire calc-awareness into every sizing longhand parser.
2. Fix F2: swap Android/Web to last-write-wins on logical vs physical.
3. Fix F6: on Android, apply aspect-ratio *before* `heightIn` so the
   clamp governs both axes (or special-case the `max-height` path to
   recompute width from ratio).
4. Decide + document policy on `height: %` / viewport-height on iOS
   (currently silently dropped).
