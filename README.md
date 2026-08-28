# Style Converter

[![ci](https://github.com/future-style-converter/style-converter/actions/workflows/ci.yml/badge.svg)](https://github.com/future-style-converter/style-converter/actions/workflows/ci.yml)
[![License](https://img.shields.io/badge/License-Apache_2.0-blue.svg)](LICENSE)

**CSS in → typed IR → the same styles rendered natively on Web, Android, and iOS.**

Style Converter parses CSS declarations (wrapped in a small JSON envelope) into a
typed **intermediate representation (IR)**, then renders that IR with three
**runtime style engines** — one per platform. The runtimes are the product:

| runtime | package | platform tech | where it lives |
|---|---|---|---|
| Web | npm `@style-converter/web` | React + real DOM/CSS | `runtimes/web/` + `apps/web-harness/` (vite harness) |
| Android | AGP library `com.styleconverter.runtime` | Jetpack Compose | `runtimes/compose/` + `apps/android-harness/` (test app) |
| iOS | SwiftPM `StyleConverterRuntime` | SwiftUI | `runtimes/swiftui/` + `apps/ios-harness/` (test app) |

A screenshot harness renders every fixture on all three platforms and
compares the results with SSIM, so "the same CSS looks the same everywhere"
is a measured claim, not a hope.

```
your-styles.json  (CSS properties per component)
        │
        │  ./gradlew :converter:run --args="convert … -i … -o out"
        ▼
   typed IR  (out/tmpOutput.json)
        │
        │  test-all.sh copies the IR into each runtime bundle
        ▼
┌──────────────────┬─────────────────────┬──────────────────────┐
│  runtimes/web    │  runtimes/compose   │  runtimes/swiftui    │
│  (DOM/CSS)       │  (Compose)          │  (SwiftUI)           │
└──────┬───────────┴─────────┬───────────┴──────────┬───────────┘
       │      screenshots    │                      │
       ▼                     ▼                      ▼
      3-way SSIM comparison report (tools/visual/report/)
```

## Current status (honest)

Three coverage numbers, all true — and they mean different things:

| claim | number | source of truth |
|---|---|---|
| Registration coverage — a triplet exists + claims the IR type (a string-presence facade; does **not** imply native rendering) | **558 / 558 per platform** (Android 558 / 558 · iOS 558 / 558 · Web 558 / 558) | `node tools/visual/coverage-audit.mjs` (`registered:`) → `tools/visual/COVERAGE.md` |
| Real-applier floor — a dedicated `<Name>Applier` file exists (under-counts grouped appliers) | **Android 19 / 558 · iOS 74 / 558 · Web 516 / 558** | `coverage-audit.mjs` (`real:` line) |
| Verified rendering coverage — SSIM ≥ 0.95, every variant, every platform pair | **91/550 (~17%)** | [docs/STATUS.md](docs/STATUS.md) |

`registered` is only a string-presence facade — the triplet exists and
claims the type, but that alone does not render the property natively. The
`real` floor counts a dedicated `<Name>Applier.<ext>` file per property; it
under-counts grouped appliers (one file — e.g. Compose `LayoutApplier.kt`,
iOS `FlexboxApplier.swift`, web `ScrollMarginApplier.ts` — renders many
properties but its basename matches at most one IR name, so the raw
dedicated-applier file counts Android 60 · iOS 119 · Web 530 sit above the
per-property floor).

Of the unverified remainder: 419 are blocked on platform capability gaps
or missing harness tiers (animations, interactions, print, …), 37 are
exhausted (no meaningful visual test exists), and 3 have known real
divergences. [docs/STATUS.md](docs/STATUS.md) carries the full breakdown
and how the tracker got honest.

**Roadmap:** code *writers* that emit static Jetpack Compose / SwiftUI
source from the IR. An earlier generator scaffold was removed; the
converter currently emits IR only (`--to ir`).

Don't expect a drop-in "convert my stylesheet to production Compose code"
tool yet. Do expect a solid IR, three faithful runtime renderers, and an
unusually thorough cross-platform verification harness.

## Requirements

- **JDK 21** (converter; Gradle wrapper included)
- **Node 24** (web runtime + tooling; `npm ci` at the repo root — npm workspaces)
- macOS + **Xcode** + **xcodegen** — only if you want iOS captures
- **Android SDK / emulator** — only if you want Android captures

See [CONTRIBUTING.md](CONTRIBUTING.md) for full dev setup.

## Quick start

```bash
# JDK 21 required — on macOS:
export JAVA_HOME=$(/usr/libexec/java_home -v 21)

# 1. Convert CSS to IR (emits the flat-list IR v2 wire by default)
./gradlew :converter:run --args="convert --from css --to ir -i fixtures/visual-test.json -o out"
# → out/tmpOutput.json (the IR)
# --emit-ir v1|v2 selects the wire; v2 is the default. Append --emit-ir v1 for the
# DEPRECATED legacy nested-children wire (kept for one deprecation window):
#   ./gradlew :converter:run --args="convert --from css --to ir -i fixtures/visual-test.json -o out --emit-ir v1"

# 2. Render + compare on all three platforms
./test-all.sh fixtures/visual-test.json
# → tools/visual/report/index.html (side-by-side screenshots + SSIM per component)

# Only one platform? Skip the others:
SKIP_ANDROID=1 SKIP_WEB=1 ./test-all.sh fixtures/visual-test.json   # iOS only
./test-ios.sh fixtures/visual-test.json                             # same thing

# 3. Fast health check of the harness itself (web-only, ~2 min)
bash tools/visual/smoke.sh --quick
```

## Input format

A JSON envelope mapping component names to CSS properties:

```json
{
  "components": {
    "PrimaryButton": {
      "properties": {
        "background-color": "#6200EE",
        "padding": "10px 16px",
        "border-radius": "8px",
        "color": "#FFFFFF"
      }
    }
  }
}
```

Fixtures for every property category live under `fixtures/properties/`
(33 categories). Values are normalized in the IR: colors → sRGB floats,
lengths → px, angles → degrees, times → ms. Runtime-dependent values
(`var()`, `calc()`, `em`, `%`) stay unresolved (`null`) for the runtimes
to handle.

## Repository layout

```
converter/src/main/kotlin/app/
├── irmodels/            # typed IR: one file per CSS property (558-property catalogue)
└── parsing/css/         # CSS value parsers (longhands, shorthands, primitives)

runtimes/                # ← the three runtime style engines (the product)
├── web/src/engine/                            # Web runtime (per-property triplets)
├── compose/src/main/java/…/runtime/           # Android runtime (per-property triplets)
└── swiftui/Sources/StyleConverterRuntime/     # iOS runtime (per-property triplets)

apps/                    # per-platform capture harnesses (web / android / ios)
tools/
├── visual/              # 3-way SSIM compare + coverage audit + smoke.sh
├── titan/               # WPT-corpus test harness
└── wpt/                 # WPT corpus mirror (gitignored; tools/titan/fetch-wpt.sh)

fixtures/                # test fixtures: properties/<category>/ + components/ + probes
schema/                  # the IR wire-format contract (JSON Schema + spec + golden fixtures)
docs/NAMING.md           # vocabulary glossary (reader/writer/runtime/harness/…)
docs/STATUS.md           # one-page honest status (coverage numbers + tier record)
test-all.sh              # convert → render on 3 platforms → compare
```

Each property is implemented as a **Config / Extractor / Applier** triplet
at the same relative path in all three runtimes — see [CLAUDE.md](CLAUDE.md)
for the full per-property contract.

## The wire format is a contract — `schema/`

The IR JSON that `--to ir` emits and all three runtimes consume is
machine-checked, not folklore:

- `schema/ir-v2.schema.json` — the **current** wire's JSON Schema (draft
  2020-12): **strict** on the v2 envelope (`irVersion`/`minReaderVersion`,
  flat component list, `slot`/`meta` structures), **permissive** at
  property-data leaves (full leaf strictness is deferred to a future
  revision — the 558-property surface is still moving). This is what the
  converter emits by default. `schema/ir-v1.schema.json` is the
  **deprecated** legacy contract for the pre-v2 nested-children wire,
  still emitted byte-for-byte by `--emit-ir v1` for one deprecation window.
- `schema/spec/01…05-*.md` — normative prose lifted from the serializer
  code: envelope, value shapes (including the known defects), the children
  map-in/array-out rule (v1) and the flat slot/placement rule (v2), the
  `_underscore`→`meta` field renames, and the versioning policy
  (`schema/spec/05-versioning.md`: v2 is the frozen current wire, v1 is
  deprecated; unknown property types are tolerated, unknown envelope keys
  are an error).
- `schema/conformance/fixtures/` — 39 hand-authored golden IR documents
  (12 v1 + 27 v2 under `fixtures/v2/`), one wire-shape family each,
  decoded by conformance tests on **all four codebases** (converter, web,
  compose, swiftui).

```bash
node schema/conformance/run.mjs          # validate goldens (+ out/tmpOutput.json if present)
node schema/conformance/run.mjs --emit   # convert fixtures/visual-test.json first, then validate
node --test schema/conformance/run.test.mjs   # the runner's own tests
```

CI runs the schema check on every push; if you change what the converter
emits, the goldens (and all four platform suites) will tell you — that is
the point.

## Running the tests

| suite | command | tests |
|---|---|---:|
| converter (Kotlin) | `./gradlew :converter:test` | 368 |
| web runtime (vitest) | `npm -w runtimes/web run test` | 1308 |
| compose runtime (JUnit) | `(cd apps/android-harness && ./gradlew :runtime:testDebugUnitTest)` | 2761 |
| swiftui runtime (XCTest) | `xcodebuild test -scheme StyleConverterRuntime -destination 'platform=macOS,variant=Mac Catalyst,arch=arm64'` | 1811 |
| tooling (node --test) | `node --test tools/visual/*.test.mjs tools/titan/*.test.mjs` | 1738 |
| IR conformance | `node schema/conformance/run.mjs --emit` | 39 goldens (12 v1 + 27 v2) × 4 codebases |

CI (`.github/workflows/ci.yml`) runs the converter, web-runtime,
test-tooling, schema-conformance, doc-staleness, and two native
IR-conformance jobs (compose on the JVM, swiftui under Mac Catalyst) on
every push/PR to `main`/`dev` (doc-staleness derives every headline number
in these docs from live source-of-truth and fails on drift; the native
gates catch a wire change the Kotlin/Swift decoders would reject). The full
visual pipeline (`./test-all.sh`, plus `BASELINE=1` for regression gating)
runs locally — it needs an Android emulator and an iOS simulator.

## Going deeper

- [CLAUDE.md](CLAUDE.md) — architecture, the per-property contract,
  done-definition, how to add a property
- [CONTRIBUTING.md](CONTRIBUTING.md) — dev setup, branch model, PR checklist
- [docs/NAMING.md](docs/NAMING.md) — the vocabulary glossary
- [schema/spec/](schema/spec/) — the normative IR wire-format spec
- [docs/STATUS.md](docs/STATUS.md) — the one-page status + testing-tier record

## License

Apache License 2.0 — see [LICENSE](LICENSE) and [NOTICE](NOTICE).
The test harness references the Web Platform Tests corpus (BSD-3-Clause,
fetched at test time, never redistributed) — see
[THIRD_PARTY_LICENSES.md](THIRD_PARTY_LICENSES.md).
