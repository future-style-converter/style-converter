# Contributing

Short version: PRs target `dev`, every property change ships as a
cross-platform triplet with a fixture and a baseline, and you run the
smoke check plus the suite you touched before pushing.

Terminology (reader / writer / runtime / harness / fixture / golden / …)
is pinned in [docs/NAMING.md](docs/NAMING.md) — use it in code, docs, and
PR titles. In particular: say **runtime**, not "engine".

## Dev setup

- **JDK 21** — `converter/build.gradle.kts` pins `jvmToolchain(21)` and CI
  uses temurin 21; the Gradle wrapper itself is 9.6.1 and runs on newer JDKs.
  macOS: `export JAVA_HOME=$(/usr/libexec/java_home -v 21)`.
- **Node 24** — matches CI and the committed `package-lock.json`.
  Then `npm ci` at the repo root (npm workspaces cover
  `runtimes/web`, `apps/web-harness`, and `tools`).
  Unit-test-only setup can skip the browser download:
  `PUPPETEER_SKIP_DOWNLOAD=1 npm ci`.
- **iOS (optional, macOS only)** — Xcode 26 (Swift 6.3 toolchain) or newer,
  because `Package.swift` declares `// swift-tools-version:6.3`, and
  [xcodegen](https://github.com/yonaskolb/XcodeGen) (`brew install xcodegen`;
  `test-all.sh` installs it automatically if missing).
- **Android (optional)** — Android SDK + an emulator (or device) for
  captures; the JUnit suite alone needs only the SDK.

Sanity check the setup:

```bash
./gradlew :converter:test
npm -w runtimes/web run test
node schema/conformance/run.mjs --emit
```

## Branch model

- **`dev`** — integration branch. All PRs target `dev`.
- **`main`** — frozen at `d61601f4` (99 first-parent commits behind `dev`,
  0 tags). There is no promotion pipeline: `docs/BACKLOG.md` records
  "No dev→main promotion — explicitly declined by the owner; do not
  re-propose" as a standing constraint. `main` moves only on an owner
  decision; BACKLOG is the authority on that.
- CI (`.github/workflows/ci.yml`) runs on every push/PR to both.

## PR checklist for property work

The per-property contract (full text in [CLAUDE.md](CLAUDE.md)):

- [ ] Fixture at `fixtures/properties/<category>/<property>.json` covers
      every value variant the parser recognises (one component per variant).
- [ ] `Config` + `Extractor` + `Applier` triplet at the **same canonical
      path** in all three runtimes (`runtimes/web/src/engine/`,
      `runtimes/compose/…/runtime/`, `runtimes/swiftui/…/StyleEngine/`).
- [ ] Files ≤200 lines, every line commented (the *why*), no silent
      fallthroughs, extractor registered in the platform's `PropertyRegistry`.
- [ ] Unit tests added in each runtime's test tree.
- [ ] `./test-all.sh fixtures/properties/<category>/<property>.json` —
      zero decode errors, SSIM ≥ 0.95 on every variant for every platform pair.
- [ ] Baselines refreshed (`UPDATE_BASELINE=1 ./test-all.sh …`) and the
      `tools/visual/baseline/` PNGs committed with the code.
- [ ] `node tools/visual/coverage-audit.mjs` shows the property on all
      three platforms.

If you change what the converter **emits**, stop: the wire format is a
contract (`schema/`), and byte-shape changes are a v2-freeze event —
see `schema/spec/05-versioning.md`.

## Run before you push

```bash
bash tools/visual/smoke.sh --quick        # harness health (web-only, ~2 min)
bash tools/visual/doc-staleness-check.sh  # doc claims still match reality
```

…plus the suite(s) you touched:

| you touched | run |
|---|---|
| `converter/` | `./gradlew :converter:test` |
| `runtimes/web/`, `apps/web-harness/` | `npm test` (root; all workspaces) |
| `runtimes/compose/`, `apps/android-harness/` | `(cd apps/android-harness && ./gradlew :runtime:testDebugUnitTest)` |
| `runtimes/swiftui/`, `apps/ios-harness/` | `xcodebuild test -scheme StyleConverterRuntime -destination 'platform=macOS,variant=Mac Catalyst,arch=arm64'` |
| `tools/` | `node --test tools/visual/*.test.mjs tools/titan/*.test.mjs` |
| `schema/`, converter output | `node schema/conformance/run.mjs --emit` |
