# Style-Converter

CSS in → typed IR → the same styles rendered natively on three platforms.

The **converter** (`converter/`, Gradle `:converter`) is a Kotlin CSS
*reader*: it parses CSS declarations (in a small JSON envelope) into a
typed intermediate representation and emits it as JSON (`--to ir` — the
only output target today). Three **runtimes** consume that IR and render
it live:

| runtime | package | where |
|---|---|---|
| Web | npm `@style-converter/web` | `runtimes/web/` (React → DOM/CSS) |
| Android | AGP library `com.styleconverter.runtime` | `runtimes/compose/` (Jetpack Compose) |
| iOS | SwiftPM `StyleConverterRuntime` | `runtimes/swiftui/` (SwiftUI; manifest at repo-root `Package.swift`) |

**The runtimes are the product** — server-driven UI (ship styles as IR,
render natively) is the horizon. Static code *writers* (emit Compose /
SwiftUI / Tailwind source from IR) are roadmap, not present: the old
generators were removed, and `--to compose` exits non-zero by design.

Vocabulary (reader / writer / runtime / harness / fixture / golden / …)
is pinned in `docs/NAMING.md`. Say "runtime", not "engine".

## Quick Start

```bash
# JDK 21 required for anything Gradle — on macOS:
export JAVA_HOME=$(/usr/libexec/java_home -v 21)

# Convert CSS to IR
./gradlew :converter:run --args="convert --from css --to ir -i fixtures/visual-test.json -o out"
# → out/tmpOutput.json

# Full 3-platform visual test (convert → render on web/Android/iOS → SSIM compare)
./test-all.sh fixtures/visual-test.json
# → tools/visual/report/index.html

# Fast web-only health check of the harness itself (~2 min)
bash tools/visual/smoke.sh --quick
```

## Architecture

```
JSON input → CSS reader (:converter) → typed IR → runtime style engines
                                                   ├─ runtimes/web      (DOM/CSS)
                                                   ├─ runtimes/compose  (Compose)
                                                   └─ runtimes/swiftui  (SwiftUI)
```

### Repository layout

```
converter/                       # Gradle :converter — the Kotlin CSS reader
└── src/main/kotlin/app/
    ├── irmodels/                # typed IR: IRDocument, ValueTypes, one file per property
    │   └── properties/          # 550-property catalogue, 33 categories
    └── parsing/css/
        └── properties/
            ├── longhands/       # per-property parsers + PropertyParserRegistry
            ├── shorthands/      # shorthand expanders
            └── primitiveParsers/# color / length / angle / time primitives

runtimes/                        # ← THE THREE PRODUCTS
├── web/                         # npm @style-converter/web (src/engine/ triplets)
├── compose/                     # AGP library com.styleconverter.runtime
│                                #   (included as :runtime by apps/android-harness)
└── swiftui/                     # SwiftPM StyleConverterRuntime (Sources/…/StyleEngine/)

apps/                            # per-platform capture harnesses (not products)
├── web-harness/                 # vite + React + puppeteer capture
├── android-harness/             # Android app; auto-captures per-component PNGs
└── ios-harness/                 # xcodegen iOS app; ImageRenderer captures

fixtures/                        # test inputs: properties/<category>/ (33 canonical
│                                #   categories) + suite trees (fuzz/, perfect/,
│                                #   combos/, components/, keyframes/, …)
tools/
├── visual/                      # SSIM compare, coverage-audit, smoke.sh, baseline/
├── titan/                       # WPT-corpus harness
└── wpt/                         # WPT corpus mirror (gitignored; tools/titan/fetch-wpt.sh)

schema/                          # IR wire-format contract (see below)
docs/
├── NAMING.md                    # the vocabulary glossary
└── reports/                     # historical campaign docs + generated COVERAGE.md
test-all.sh                      # convert → render on 3 platforms → compare
test-ios.sh                      # thin wrapper: SKIP_ANDROID=1 SKIP_WEB=1 test-all.sh
```

## The wire contract — `schema/`

The IR JSON that `--to ir` emits and all three runtimes consume is
machine-checked, not folklore:

- `schema/ir-v1.schema.json` — JSON Schema (draft 2020-12): strict on the
  envelope, permissive at property-data leaves (leaf strictness arrives
  with the flat-IR v2 freeze).
- `schema/spec/01…05-*.md` — normative prose: envelope, value shapes,
  the children map-in/array-out rule, the `_underscore` metadata-field
  rule, and the versioning policy (v1 is implicit; unknown property
  types are tolerated, unknown envelope keys are an error).
- `schema/conformance/fixtures/` — 12 hand-authored golden IR documents,
  decoded by conformance tests on **all four codebases** (converter,
  web, compose, swiftui).

```bash
node schema/conformance/run.mjs          # validate goldens (+ out/tmpOutput.json if present)
node schema/conformance/run.mjs --emit   # convert fixtures/visual-test.json first, then validate
```

Changing an emitted byte shape is a v2-freeze event
(`schema/spec/05-versioning.md`), not a casual PR.

### IR value normalization

All CSS values normalize to universal formats (full spec:
`schema/spec/02-values.md`):

| Type | CSS | Normalized |
|------|-----|------------|
| Colors | oklch, hsl, hex, rgb | sRGB (0-1 floats) |
| Lengths | px, pt, em, rem, % | pixels (absolute only) |
| Angles | deg, rad, turn, grad | degrees |
| Time | s, ms | milliseconds |
| Font weight | normal, bold, 100-900 | numeric 100-900 |
| Opacity | 0-1, 0%-100% | 0-1 float |

**`null` means runtime-dependent:** `var()`, `calc()`, `em`, `%`, `vw`
cannot be pre-computed and stay unresolved for the runtimes.

## Per-property contract (canonical triplets)

All three runtimes share **one** folder structure, locked to the folders
under `converter/src/main/kotlin/app/irmodels/properties/` and
`…/parsing/css/properties/longhands/`. These trees — irmodels, parser,
runtime engines — are kept byte-for-byte parallel: same category paths,
same property files. If a property lives at
`irmodels/properties/borders/sides/BorderTopWidth.kt`, its runtime
implementations live at the mirror paths:

```
runtimes/compose/src/main/java/com/styleconverter/runtime/
  └── borders/sides/BorderTopWidth{Config,Extractor,Applier}.kt
runtimes/swiftui/Sources/StyleConverterRuntime/StyleEngine/
  └── borders/sides/BorderTopWidth{Config,Extractor,Applier}.swift
runtimes/web/src/engine/
  └── borders/sides/BorderTopWidth{Config,Extractor,Applier}.ts
```

The 33 categories (mirrored 1:1 from irmodels/properties/):

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
empty, with a `README.md` stub. This keeps coverage auditable by `ls`.

Each property ships as a **triplet per platform**, in the canonical subfolder:

| file | purpose |
|---|---|
| `{Property}Config.{ext}`    | Typed value struct (what was extracted, ready for rendering) |
| `{Property}Extractor.{ext}` | `IRProperty → Config`. Handles every CSS value flavor the parser recognizes (see `converter/src/main/kotlin/app/parsing/css/properties/longhands/{category}/{Property}PropertyParser.kt`) |
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

Key per-platform files:

- Compose: `runtimes/compose/src/main/java/com/styleconverter/runtime/PropertyRegistry.kt`,
  `…/core/renderer/ComponentRenderer.kt`
- SwiftUI: `runtimes/swiftui/Sources/StyleConverterRuntime/StyleEngine/PropertyRegistry.swift`,
  `…/Renderer/StyleBuilder.swift` + `…/Renderer/ComponentRenderer.swift`
- Web: `runtimes/web/src/engine/PropertyRegistry.ts`,
  `runtimes/web/src/core/renderer/StyleBuilder.ts`

## Done definition for a property

A property is "done" when **all five** are true:

1. **Test fixture** in `fixtures/properties/{category}/{property}.json` exercises
   every value variant listed in the parser's value flavors (see the CSS
   parser's `{Property}PropertyParser.kt`). One component per variant.
2. **Triplet exists on all three platforms** in the matching subfolder, with
   the commenting + size rules above.
3. **`./test-all.sh fixtures/properties/{category}/{property}.json`** runs cleanly:
   - zero `decode error` rows
   - every pair's SSIM ≥ 0.95 for every variant
   - no "size mismatch" warnings
4. **Baseline committed** — `UPDATE_BASELINE=1 ./test-all.sh …` runs; the
   resulting `tools/visual/baseline/{platform}__{NNN}_{Variant}.png` files
   are staged alongside the runtime code.
5. **Coverage regenerated** — `node tools/visual/coverage-audit.mjs` shows
   the property under the right category on every platform
   (`docs/reports/COVERAGE.md` is its generated output).

## Adding a new property

1. **IR model**: add `converter/src/main/kotlin/app/irmodels/properties/<category>/<Name>Property.kt`
   (serialized naming: `<Name>Property` — the `Property` suffix is part
   of the filename convention `coverage-audit.mjs` relies on).
2. **Parser**: add `converter/src/main/kotlin/app/parsing/css/properties/longhands/<category>/<Name>PropertyParser.kt`.
3. **Parser registry**: wire into `PropertyParserRegistry.kt` (same longhands tree).
4. **Fixture**: add `fixtures/properties/<category>/<property>.json`
   exercising every value variant the parser recognises.
5. **Runtimes** — for each of `runtimes/compose`, `runtimes/swiftui`, `runtimes/web`:
   a. Author `Config` + `Extractor` + `Applier` in the canonical
      `<category>/` subfolder, matching the per-property contract above.
   b. Claim the IR type name in that platform's `PropertyRegistry`
      (directly or via a grouped `Set` union).
   c. Add a unit test under the matching test tree
      (`runtimes/compose/src/test/`, `runtimes/swiftui/Tests/`, `runtimes/web/tests/`).
6. **Run `./test-all.sh fixtures/properties/<category>/<property>.json`**;
   iterate until all platform pairs hit SSIM ≥ 0.95 on every variant.
7. **Update baseline** with `UPDATE_BASELINE=1 ./test-all.sh …` and commit
   the captures alongside the runtime code.
8. **Verify coverage** with `node tools/visual/coverage-audit.mjs` — the new
   property must show up under the right category on every platform.

## Testing workflows

Unit / conformance suites (all copy-paste runnable from the repo root;
Gradle commands need JDK 21):

| suite | command | tests |
|---|---|---:|
| converter (Kotlin) | `./gradlew :converter:test` | 43 |
| web runtime (vitest) | `npm -w runtimes/web run test` | 786 |
| compose runtime (JUnit) | `(cd apps/android-harness && ./gradlew :runtime:testDebugUnitTest)` | 413 |
| swiftui runtime (XCTest) | `xcodebuild test -scheme StyleConverterRuntime -destination 'platform=macOS,variant=Mac Catalyst,arch=arm64'` | 60 |
| tooling (node --test) | `node --test tools/visual/*.test.mjs tools/titan/*.test.mjs` | 448 |
| IR conformance | `node schema/conformance/run.mjs --emit` | 12 goldens × 4 codebases |

(`npm test` at the root runs every workspace's vitest suite — the web
runtime plus the web-harness's own capture-pipeline tests.)

Visual pipeline:

```bash
./test-all.sh [fixture.json]         # all three platforms → tools/visual/report/index.html
./test-ios.sh [fixture.json]         # iOS only (wrapper: SKIP_ANDROID=1 SKIP_WEB=1)
SKIP_ANDROID=1 SKIP_IOS=1 ./test-all.sh …   # any subset via SKIP_* env vars

BASELINE=1 ./test-all.sh …           # compare captures vs tools/visual/baseline/, fail on regression
UPDATE_BASELINE=1 ./test-all.sh …    # refresh the committed baselines

bash tools/visual/smoke.sh --quick   # web-only smoke of the harness itself (~2 min)
bash tools/visual/smoke.sh           # full smoke (adds native baselines)
```

What `test-all.sh` does: converts the fixture → `out/tmpOutput.json`,
copies the IR into each harness bundle (Android assets, iOS Resources,
web public/), builds + launches each platform (emulator / simulator /
vite + puppeteer), captures per-component screenshots, then runs the
3-way SSIM comparison → `tools/visual/report/index.html`.

CI (`.github/workflows/ci.yml`) runs 4 jobs on push/PR to `main`/`dev`:
converter, web-runtime, test-tooling, schema-conformance. Device-level
visual jobs are local-only for now.

## Honest status

Two different numbers, both true — do not conflate them:

- **Registration coverage: 550 / 550 per platform** (Android 550 / 550,
  iOS 550 / 550, Web 550 / 550). Every property in the 550-property IR
  catalogue (33 categories) has a registered Config/Extractor/Applier
  triplet on all three platforms — `node tools/visual/coverage-audit.mjs`
  is the source of truth (`docs/reports/COVERAGE.md`). "Registered" means
  the triplet exists and claims the type; some appliers are intentional
  no-op + TODO where no mobile analogue exists (speech/, regions/, print/, …).
- **Verified rendering coverage: 91/550 (~17%)**. Only 91 properties pass
  the strict bar — SSIM ≥ 0.95 on every value variant on every platform
  pair against committed baselines. Of the rest: 419 blocked-platform
  (need capability tiers the harness doesn't have — animation state,
  scroll, real tables, …), 37 exhausted (no meaningful visual test
  exists), 3 failing (known real divergences). See
  `docs/reports/CAMPAIGN_SUMMARY.md` for how the tracker went from a
  dishonest "548/548 passing" to this classification, and
  `docs/reports/TIER1_VARIANT_DEPTH.md` for the per-property tracker.

## Roadmap

Static code **writers** that emit Compose / SwiftUI (and eventually
Tailwind) source from the IR; the **flat-IR v2** freeze with the
slot/placement children contract (`schema/spec/03-children.md` and
`05-versioning.md` describe the v1 rules it replaces); repairing the
known v1 wire defects at that freeze. Execution history — the 12-commit
rollout, the 70-round audit campaign, TITAN — lives in `docs/reports/`.

## Tech stack

Kotlin 2.1.0 · Java 21 · Gradle 8.14 · kotlinx.serialization-json 1.6.3 ·
Node 24 (npm workspaces) · TypeScript 5 / vitest · Compose BOM 2024.12
(minSdk 24) · Swift 5.9 language mode / iOS 16+ · xcodegen.
