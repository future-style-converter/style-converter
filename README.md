# Style Converter

[![License](https://img.shields.io/badge/License-Apache_2.0-blue.svg)](LICENSE)

**CSS in → typed IR → the same styles rendered natively on Web, Android, and iOS.**

Style Converter parses CSS declarations (wrapped in a small JSON envelope) into a
typed **intermediate representation (IR)**, then renders that IR with three
**runtime style engines** — one per platform:

| engine | platform tech | where it lives today |
|---|---|---|
| Web | React + real DOM/CSS | `testing/web/` |
| Android | Jetpack Compose | `testing/Android/` |
| iOS | SwiftUI | `testing/iOS/` |

The three engines are the product. They currently live under `testing/`
for historical reasons (they grew out of the visual-verification harness);
a restructure to `runtimes/` is planned. A screenshot harness renders every
fixture on all three platforms and compares the results with SSIM, so
"the same CSS looks the same everywhere" is a measured claim, not a hope.

```
your-styles.json  (CSS properties per component)
        │
        │  ./gradlew run --args="convert --from css --to ir -i … -o out"
        ▼
   typed IR  (out/tmpOutput.json)
        │
        │  test-all.sh copies the IR into each runtime bundle
        ▼
┌──────────────┬──────────────────┬───────────────┐
│  Web engine  │  Android engine  │  iOS engine   │
│  (DOM/CSS)   │  (Compose)       │  (SwiftUI)    │
└──────┬───────┴────────┬─────────┴───────┬───────┘
       │  screenshots   │                 │
       ▼                ▼                 ▼
      3-way SSIM comparison report (testing/report/)
```

## Current status (honest)

- **Working today:** the CSS → IR converter and all three runtime engines.
  Every property in the 550-property IR catalogue has an
  extractor/config/applier triplet on all three platforms
  (see `testing/COVERAGE.md`).
- **Verified cross-platform:** **91 / 550 properties** pass the strict
  bar — SSIM ≥ 0.95 on *every* value variant on *every* platform pair
  (`testing/TIER1_VARIANT_DEPTH.md`). Of the rest, 419 are blocked on
  platform capability gaps or missing harness tiers (animations,
  interactions, print, …), 37 are exhausted (no meaningful visual test
  exists), and 3 have known real divergences.
- **Roadmap:** code *writers* that emit static Jetpack Compose / SwiftUI
  source from the IR. An earlier generator scaffold was removed; the
  converter currently emits IR only (`--to ir`).

Don't expect a drop-in "convert my stylesheet to production Compose code"
tool yet. Do expect a solid IR, three faithful runtime renderers, and an
unusually thorough cross-platform verification harness.

## Requirements

- **Java 21+** (converter; Gradle wrapper included)
- **Node 20+** (web engine + comparison tooling; `npm install` in `testing/web/`)
- macOS + **Xcode** — only if you want iOS captures
- **Android SDK / emulator** — only if you want Android captures

## Quick start

```bash
# 1. Convert CSS to IR
./gradlew run --args="convert --from css --to ir -i examples/visual-test.json -o out"
# → out/tmpOutput.json (the IR)

# 2. Render + compare on all three platforms
./test-all.sh examples/visual-test.json
# → testing/report/index.html (side-by-side screenshots + SSIM per component)

# Only one platform? Skip the others:
SKIP_ANDROID=1 SKIP_WEB=1 ./test-all.sh examples/visual-test.json   # iOS only
./test-ios.sh examples/visual-test.json                             # same thing

# 3. Fast health check of the harness itself (web-only, ~2 min)
bash testing/smoke.sh --quick
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

Fixtures for every property category live under `examples/properties/`.
Values are normalized in the IR: colors → sRGB floats, lengths → px,
angles → degrees, times → ms. Runtime-dependent values (`var()`, `calc()`,
`em`, `%`) stay unresolved (`null`) for the engines to handle.

## Repository layout

```
src/main/kotlin/app/
├── irmodels/            # typed IR: one file per CSS property (550-property catalogue)
└── parsing/css/         # CSS value parsers (longhands, shorthands, primitives)

testing/                 # ← the three runtime engines + verification harness
├── web/src/style/engine/                      # Web engine (per-property triplets)
├── Android/app/src/main/java/…/style/         # Android engine (per-property triplets)
├── iOS/StyleConverterTest/StyleEngine/        # iOS engine (per-property triplets)
├── compare-screenshots.mjs                    # 3-way SSIM + report
├── coverage-audit.mjs                         # IR ↔ engine coverage matrix
└── titan/                                     # WPT-corpus test harness (fetched, not committed)

examples/                # fixtures, one suite per property category
test-all.sh              # convert → render on 3 platforms → compare
```

Each property is implemented as a **Config / Extractor / Applier** triplet
at the same relative path in all three engines — see `CLAUDE.md` for the
full per-property contract.

## Running the tests

```bash
./gradlew test                                          # JVM parser/IR tests
node --test testing/*.test.mjs testing/titan/*.test.mjs # harness unit tests
bash testing/smoke.sh                                   # full smoke (adds native baselines)
```

## License

Apache License 2.0 — see [LICENSE](LICENSE) and [NOTICE](NOTICE).
The test harness references the Web Platform Tests corpus (BSD-3-Clause,
fetched at test time, never redistributed) — see
[THIRD_PARTY_LICENSES.md](THIRD_PARTY_LICENSES.md).
