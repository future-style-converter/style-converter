<!-- PRs target `dev` — the only live branch. `main` is frozen at d61601f4
     (dev→main promotion was declined by the owner; see docs/BACKLOG.md).
     Vocabulary (reader / writer / runtime / harness / fixture / golden /
     baseline) is pinned in docs/NAMING.md — use it in the title too.
     In particular: say "runtime", not "engine". -->

## What

<!-- One or two sentences: what does this PR change? -->

## Why

<!-- What problem does it solve? Link the issue if there is one. -->

## Run before you push

From [CONTRIBUTING.md](https://github.com/future-style-converter/style-converter/blob/dev/CONTRIBUTING.md) — check what you actually ran:

- [ ] `bash tools/visual/smoke.sh --quick` — harness health (web-only, ~2 min)
- [ ] `bash tools/visual/doc-staleness-check.sh` — doc claims still match reality
- [ ] The suite(s) for what I touched (table in CONTRIBUTING.md):
  `converter/` → `./gradlew :converter:test` · web → `npm test` ·
  compose → `:runtime:testDebugUnitTest` · swiftui → `xcodebuild test` ·
  `tools/` → `node --test tools/visual/*.test.mjs tools/titan/*.test.mjs`
- [ ] **If `schema/` or converter output touched:** `node schema/conformance/run.mjs --emit`
  passes. (Byte-shape changes to the wire format are a v2-freeze event —
  see `schema/spec/05-versioning.md`. Stop and open an issue first.)

## Honesty check

- [ ] No SSIM thresholds lowered, and no baselines under
      `tools/visual/baseline/` modified — or, if they were, the What/Why
      above says exactly which ones and justifies each change.

## New-property checklist

<!-- Only for PRs that add or change a CSS property. Delete this section
     otherwise. Full contract: CLAUDE.md "Per-property contract". -->

- [ ] Fixture at `fixtures/properties/<category>/<property>.json` covers
      every value variant the parser recognises (one component per variant)
- [ ] `Config` + `Extractor` + `Applier` triplet at the same canonical path
      in all three runtimes (`runtimes/web/src/engine/`,
      `runtimes/compose/…/runtime/`, `runtimes/swiftui/…/StyleEngine/`)
- [ ] Files ≤200 lines, every line commented (the *why*), no silent
      fallthroughs
- [ ] Extractor registered in each platform's `PropertyRegistry`
- [ ] Unit tests added in each runtime's test tree
- [ ] `./test-all.sh fixtures/properties/<category>/<property>.json` —
      zero decode errors, SSIM ≥ 0.95 on every variant for every platform pair
- [ ] Baselines refreshed (`UPDATE_BASELINE=1 ./test-all.sh …`) and the
      `tools/visual/baseline/` PNGs committed with the code
- [ ] `node tools/visual/coverage-audit.mjs` shows the property on all
      three platforms
