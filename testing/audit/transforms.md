# Transforms Category – Phase 12 Audit

Scope: 10 IR properties under `irmodels/properties/transforms/` — `Transform`,
`Rotate`, `Scale`, `Translate`, `TransformOrigin`, `TransformBox`,
`TransformStyle`, `Perspective`, `PerspectiveOrigin`, `BackfaceVisibility`.
Audited: iOS / Android / Web style-engine triplets plus existing fixtures
under `examples/properties/transforms/`.

Fixture added: `examples/properties/transforms/audit-phase12.json` — 18
edge-case components covering 5-function chains, `matrix(skew+translate)`,
`matrix3d` identity+translate, inline `perspective()` inside a transform
list, origin via keyword (`right bottom`) vs `100% 100%` vs absolute px,
`rotate` longhand vs `transform: rotate()` parity, 3D `rotate` with axis +
vector, non-uniform scale, percent-translate (needs element box),
`transform-box: fill-box`, `backface-visibility: hidden` during
rotateY(180°), order-sensitive rotate→scale→skewX composition, and
`perspective: 0` / `none`.

Prior-audit carry-over (expected divergence, not defects): iOS perspective
is a scale approximation; Z-translation is dropped in SwiftUI `offset()`;
percent-translate needs GeometryReader. These are already documented in
`TransformsApplier.swift` and do not count against SSIM targets.

Run status: `./test-all.sh examples/properties/transforms/audit-phase12.json`
could not complete on this host. iOS `xcodebuild` fails on a corrupted
SwiftExplicitPrecompiledModules cache (`_DarwinFoundation1-*.pcm not found`,
plus `build.db` disk I/O error) — unrelated to transforms. Android/web runs
also race with a parallel Phase-12 agent touching `testing/performance`;
my `mkdir`-based lockfile doesn't serialise against that agent, so
`out/tmpOutput.json`, `testing/Android/app/src/main/assets/tmpOutput.json`
and `testing/web/public/ir-components.json` get clobbered mid-pipeline.
Per-property captures for this fixture were NOT produced. The parser
itself is clean: `./gradlew run --args="convert … audit-phase12.json"`
reports `0 generic` for all 18 components (18/18 components, 4–5 parsed
properties each). Audit below is therefore parser+code-review based;
findings do not depend on fresh screenshots.

Snapshot dir (`testing/audit/transforms/snapshot/`) contains the fixture
JSON plus the last committed transform baselines from `testing/baseline/`
for reference when someone re-runs the pipeline.

## Findings

### T1 — Android extractor ignores the actual IR rotate-angle key

`testing/Android/app/src/main/java/com/styleconverter/test/style/transforms/TransformExtractor.kt`
reads `obj["angle"]` for every rotate* / skew* function (lines 349, 354,
359, 364, 371, and skew helpers at 418/424/431). The real IR shape, as
emitted by the Kotlin parser and observed on `audit-phase12.json`, is:

```json
{ "fn": "rotate",  "a": { "deg": 15.0 } }
{ "fn": "rotateY", "a": { "deg": 45.0 } }
{ "fn": "skewX",   "x": { "deg": 5.0 } }
```

So every `rotate()` / `rotateY()` / `rotateZ()` in a `transform:` function
list currently extracts as angle = 0 on Android — the shape never matches
`obj["angle"]`. Same for `rotate3d` (reads `obj["angle"]` at line 371).
iOS+Web already use `"a"` / `"x"` / `"y"` correctly, so this is an
Android-only gap. Fix: rename the lookups to `obj["a"]` for rotates
(`fn.a`) and keep `obj["x"]` / `obj["y"]` for skews (which are correct),
then parse the nested `{deg:N}` via `ValueExtractors.extractDegrees`
(already does recognise `{deg:…}`).

**Impact:** The `transform: rotate(45deg)` component in
`transform-functions.json` currently renders un-rotated on Android. Only
the `rotate:` **longhand** path happens to pass because it goes through
`ValueExtractors.extractDegrees(data)` on the raw property data (which
is `{deg:N}`), not through the function-list extractor.

### T2 — Android matrix / matrix3d extractor looks for `obj["values"]`, IR emits named keys

Same file, lines 448 and 458. `extractMatrixFunction` expects
`obj["values"] as JsonArray` of length ≥ 6. The IR actually looks like:

```json
{ "fn": "matrix", "a": 1.0, "b": 0.2, "c": -0.2, "d": 1.0, "e": 30.0, "f": 30.0 }
{ "fn": "matrix3d", "a1": 1.0, "b1": 0.0, …, "a4": 20.0, "b4": 10.0, "c4": 0.0, "d4": 1.0 }
```

`obj["values"]` is `null`, so both extractors return `null` and the
matrix function is silently dropped from the list. `Audit_Matrix_SkewTrans`
and `Audit_Matrix3dIdentityT` will both render as an un-transformed box on
Android. Fix: mirror the field names used by Web (`a/b/c/d/e/f` for 2D
and `a1…d4` for 3D) and assemble the 6-element / 16-element list
explicitly.

### T3 — Android inline `perspective()` reads `obj["d"]` / `obj["distance"]`, IR emits `"l"`

Same file, `extractPerspectiveFunction` at line 439. IR shape:
`{ "fn": "perspective", "l": { "px": 500.0 } }`. Neither `d` nor
`distance` is present → the code falls through to the hardcoded
`1000.dp` default. Inline `perspective(500px) rotateY(45deg)` therefore
renders with the *default* 1000px perspective on Android regardless of
the CSS value. Fix: add `obj["l"]` as the first lookup key (keep the
legacy `d`/`distance` as fallbacks for robustness).

### T4 — Android `TransformOrigin` percentage extractor reads wrong field name

`TransformExtractor.extractOriginComponent`, line 236:
`"percentage" -> value?.jsonPrimitive?.floatOrNull?.let { it / 100f }`.
It reads a top-level `value` key, but IR emits:
`{ "type": "percentage", "percentage": 100.0 }` (confirmed from
`audit-phase12.json` converted output). `value` is absent, so the
extractor returns `null` and the origin silently collapses to the 0.5f
default (center). `Audit_Origin_100pct` therefore renders identically
to `transform-origin: center`. Keyword form
(`{type:"keyword", value:"RIGHT"}`) *does* match `value` and still works,
but the percentage path is dead. Fix: read `obj["percentage"]` (matching
the length/angle pattern of nested primitive keys). A length-axis path
is also missing entirely — the pixel-origin edge case
(`transform-origin: 10px 10px`) falls through to the `else` branch and
returns `null`, so `Audit_Origin_PxAbsolute` also silently centers.

### T5 — `transform-box` is registered but not wired

`TransformExtractor.init` registers `"TransformBox"` with PropertyRegistry
(line 73) but the `when` in `extractTransformConfig` has no branch for
it, and `TransformApplier` never consults it. So `transform-box: fill-box`
on Android is a no-op. Same on iOS (`TransformsApplier` has no branch
for `c.box`; `applyBox` writes into `agg.box` but the field is never
read — audit line 40 of `TransformsExtractor.swift`, and the applier
file has no `.box` reference). Web emits `transformBox` through
`TransformBoxApplier`, which matches native CSS so there it works
correctly. Expected divergence for mobile (no SVG geometry box), but
it should be flagged in a comment + tracker entry instead of silent drop.

### T6 — iOS backface test only counts Y-axis *or* X-axis, not both

`TransformsApplier.isBackFacing`, lines 127–136: the inner loop has two
`if case .rotate(…)` patterns that both add to `total`, but they share
the same accumulator. For a rotateX(90°)+rotateY(90°) combo the test
adds 180° and reports "back facing" even though the front face is still
partly visible. More importantly, the `y != 0` / `x != 0` guards use
the *vector-component* axis values, not the accumulated rotation
direction, so `rotate3d(1,1,0, 45deg)` double-counts the angle. Low
priority — CSS `backface-visibility` is famously under-specified for
non-axis-aligned rotations — but worth a TODO.

### T7 — iOS perspective approximation clamps to 0.9×–1.0× regardless of declared distance

`TransformsApplier.body`, lines 69–71. `k = max(0.9, min(1.0, d/1000.0))`.
For `perspective: 500px` this yields 0.9; for `perspective: 100px` it
also yields 0.9; for `perspective: 5000px` it yields 1.0. The clamp
bucket is so narrow that every meaningful CSS value collapses to "same
small shrink" — the rotateY angle does all the actual work. This is
the documented "perspective is a scale approximation" divergence but
the bucket should probably widen (say 0.6×–1.0× over 200–2000px) so
distinct `perspective:` values produce visibly distinct output. Not a
spec conformance bug, a test-signal bug: the audit fixture's two
perspective rows (`500px` vs `1000px`) will both render identically on
iOS, giving the comparison harness nothing to discriminate.

### T8 — `perspective: 0` edge case is spec-invalid; behaviour undefined across platforms

CSS `perspective: 0` is technically invalid (per CSS Transforms 2 §6.2 —
"negative values and zero are invalid"). The Kotlin parser accepts it
(it's a length, and length 0 is valid at the token level). Then:
- **Android** `ValueExtractors.extractDp` returns `0.dp`; the applier
  divides `translateZ / perspective` → NaN/∞ guarded by `coerceIn`, so
  it silently becomes max-scale (10×). Bad.
- **iOS** `k = max(0.9, min(1.0, 0/1000))` = 0.9, harmless.
- **Web** emits `perspective: 0px` and the browser drops the declaration.

The spec says reject invalid; we accept and produce divergent output.
Fix: sizing.md precedent — reject non-positive values at parse time and
log via PropertyTracker. The fixture's `Audit_Perspective_Zero` exists
precisely to catch this.

### T9 — `rotate: 1 0 0 45deg` vector form — Android heuristic picks wrong axis

`extractRotate3dFunction` lines 377–381 picks the axis with the
largest component, tie-breaking toward Z. For `rotate3d(1, 1, 0, 45deg)`
(`Audit_Rotate_XAxis` adjacent pattern), x==y==1 so `z >= x && z >= y`
is `0 >= 1` → false, then `y >= x` is `1 >= 1` → true → rotateY. That
discards the X component entirely. Expected divergence for a 2D render
pipeline, but the heuristic should at least pick the first non-zero
axis consistently, and the limitation belongs in a `PROPERTY_TRACKING`
entry. iOS uses `rotation3DEffect` which handles arbitrary axis vectors
correctly.

### T10 — Standalone `rotate`/`translate`/`scale` longhand cascade order

CSS spec says the three longhands compose in the order
**translate → rotate → scale**, *after* the `transform:` function list.
iOS applies them in that order (`TransformsApplier.body` steps 1–2,
lines 38–47). Android accumulates them into the same totals as the
function list (lines 169–183 in `TransformApplier.kt`) with no ordering
guarantee: translate, rotate, and scale are merged into one graphicsLayer
call whose evaluation order is scale → rotate → translate (Compose
convention), which is the opposite of the CSS spec for the longhand
stack. `Audit_RotateLonghand45` vs `Audit_TransformRotate45` should be
visually identical (neither has translate/scale), so this finding
doesn't bite on that specific pair, but any fixture combining
`translate: …` + `rotate: …` + `scale: …` longhands (e.g.
`Audit_Translate_PctPair` paired with a nonzero rotate in a future
fixture) will diverge. Fix: apply the three longhands in
translate → rotate → scale order **after** the function list, via
three separate `graphicsLayer` calls rather than one accumulation.

### T11 — Parser coverage is complete for this fixture

`./gradlew run … audit-phase12.json` reports `Parsed N properties
(0 generic)` for every component, and the resulting `tmpOutput.json`
contains 18 components matching the fixture 1:1. No decode errors;
no fall-through to GenericProperty. The parser-side of Phase 12 is
clean for transforms.

## Done-definition status

1. Fixture exists → ✓
   (`examples/properties/transforms/audit-phase12.json`)
2. Triplet on all three platforms → ✓ for Transform / Rotate / Scale /
   Translate / TransformOrigin / Perspective / PerspectiveOrigin /
   BackfaceVisibility; ✗ for `TransformBox` (Android missing runtime
   branch, iOS reads-but-discards); ✗ for `TransformStyle` on Android
   (registered but applier has no behaviour — same flatness as default).
3. `./test-all.sh` clean → ✗ (iOS xcodebuild broken; parallel-agent race
   clobbered Android/web captures — see "Run status" above). Not a
   transforms defect.
4. Baseline committed → **pre-existing** baselines for
   `Transform_Rotate/Scale/Translate/Skew/Combined/Origin/Perspective_Rotate/
   Edge_MultiTransform` are present in `testing/baseline/` (24 PNGs,
   8 variants × 3 platforms). No new audit-phase12 baseline yet — defer
   until T1–T4 are fixed, otherwise Android baseline would bake in the
   broken "rotate as 0°" output.
5. Coverage matrix row in `testing/README.md` → not yet flipped to ✓.

## Suggested next moves (ordered)

1. Fix T1 (Android rotate angle key) and T2 (matrix keys) — both are
   one-line field-name renames and unblock most of the fixture.
2. Fix T4 (origin percentage) and T3 (inline perspective distance key)
   — same pattern, trivial.
3. Add a length-axis branch in `extractOriginComponent` to handle
   `transform-origin: 10px 10px`.
4. Decide the policy on T8 (`perspective: 0`) — reject at parser or
   clamp at applier; either is fine but document.
5. After T1–T4 land, re-run `test-all.sh audit-phase12.json` in a
   serialised window (see the shared-agent race described in "Run
   status"), commit baselines, flip the coverage matrix row.
