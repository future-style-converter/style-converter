# wave50-S1 — combined-tree audit (node/git only; no gradle, no xcodebuild)

Skeptic lane S1 of wave 50. Every claim below was **executed**; the commands
are in this file and the scripts sit next to it. All pointers are repo paths.

## Files

| file | what it is |
|---|---|
| `hunk-attribution.mjs` / `.txt` | Splits `git diff HEAD` into hunks and reports the wave-50 lane tags found in each hunk's ADDED lines. Re-run: `git diff HEAD > /tmp/w.diff && node tools/titan/results/wave50-S1/hunk-attribution.mjs /tmp/w.diff`. |
| `patch-presence.mjs` | For one `.patch`, checks each hunk's POST-image appears verbatim in the tree (offset-independent). Re-run: `node tools/titan/results/wave50-S1/patch-presence.mjs tools/titan/results/wave50-B11/placeholder-floor-band.patch`. |
| `rootscope-census.mjs` | INDEPENDENT census (imports no lane code) of html-scope vs body-scope same-property conflicts over the 1435 wave49-final scored tests. Re-run from the repo root: `node tools/titan/results/wave50-S1/rootscope-census.mjs`. |
| `extractor-head-differential.mjs` / `extractor-blast-radius.txt` | HEAD-vs-tree `extractFixture()` differential over all 1435 scored tests. Re-run: `git show HEAD:tools/titan/extract-fixture.mjs > tools/titan/_skeptic50-head-extract-fixture.mjs && node tools/titan/results/wave50-S1/extractor-head-differential.mjs; rm tools/titan/_skeptic50-head-extract-fixture.mjs`. |
| `provision-loop-budget-repro.sh` | Timing repro of the iteration-count-vs-wall-clock loop bug still present at `tools/titan/provision-devices.sh:235` and `:325`. |
| `doc-staleness.txt` | Output of `bash tools/visual/doc-staleness-check.sh` on this tree (exit 1). |

## What holds

- **Every `git status` entry maps to a lane or to the orchestrator.** All 12
  builder lanes' production files carry an in-file `wave-50 lane Bn` tag; the
  untagged hunks are exactly the applied seam-patch hunks (verified
  byte-for-byte against the lane's own `.patch`) plus the orchestrator's
  `tools/titan/provision-devices.sh`. No build/config file is touched
  (`build.gradle*`, `settings.gradle*`, `gradle.properties`, `package.json`,
  `Package.swift`, `.gitignore`: all untouched). No `node_modules` or
  `local.properties` tracked or untracked. `git stash list` is EMPTY.
  No `.orig` / `.rej` / `.bak` anywhere.
- **All eleven applied seam patches are present byte-for-byte.** The single
  `ABSENT` hunk reported by `patch-presence.mjs` on
  `tools/titan/results/wave50-B7/root-scope-conflict.patch` is the
  orchestrator's own citation repair (a css-compositing-1 section number that does not exist in the ED →
  the `background-blend-mode` property's real section); `diff` of the patch's post-image
  against `tools/titan/extract-fixture.mjs:6270-6299` shows that one comment
  line as the ONLY delta.
- **B7's root-scope conflict census is exactly right.** My independent census
  (`rootscope-census.mjs`, written from scratch) finds THREE conflicting
  documents over the 1435 scored tests — the same three B7 names
  (`CSS2/css21-errata/s-11-1-1b-005`, `css-contain/contain-html-overflow-002`,
  `css-writing-modes/inline-box-border-vlr-001`).
- **B7's "byte-identical IR when no conflict" claim is proven.** The
  HEAD-vs-tree `propsForBodyRoot` differential over all 1435 tests changes
  exactly 2 documents (`s-11-1-1b-005` background black→white,
  `contain-html-overflow-002` height 400px→200px) and leaves 1433 identical.
- **The whole extractor-side blast radius is 17 / 1435 documents, and all 17
  are declared in some lane's artifact.** Full `extractFixture()` differential,
  0 throw-differences. List in `extractor-blast-radius.txt`.
- **B7's UA-`<hr>` colour arithmetic is right.** Measured independently on the
  frozen ref `tools/wpt/refs/9b5435e55e0b54a6cd09c1c563861eb3c999cef1/white-black-ink-font-lh-imgpad-htmlpins/css-images/gradient__gradient-hue-direction.png`:
  y=188 row is rgb(154,154,154) ×357, y=189 is rgb(238,238,238) ×357, the two
  mitre pixels are rgb(196,196,196); Blink `Dark(238)` = 238·(0.93333−0.33)/0.93333
  = 153.85 → 154. The `#eeeeee` constant is a generic UA rule, not a
  test-specific carve-out.
- **B12's inject seam really does ship DISARMED, and the switch can fire.**
  `degenerateVetoActive(true)` is `false` with a clean env and `true` under
  `TITAN_DEGENERATE_VETO=1`, then `false` again when the var is cleared.
  Its always-on cost is negligible: `computeDegenerate` averages 13.8 ms/cell
  on real 390×600 pairs (vs `computeNovelInk` 5.2 ms) → ≈57 s over 4111 cells.
- **Ring-fence respected.** Every occurrence of `backdrop-filter-basic-blur`
  in the tree is inside a COMMENT explaining why a generic mechanism does or
  does not reach it; no code predicate anywhere keys on a test name, and the
  new Compose/Swift production files contain no `"section/test"` string
  literals at all.
- `node tools/visual/spec-cite-validate.mjs` → **0 unknown sections**.
- `node schema/conformance/run.mjs` → all 39 goldens + `out/tmpOutput.json` valid.
- `node --test tools/visual/*.test.mjs tools/titan/*.test.mjs` → **2030 tests,
  2026 pass, 0 fail, 4 skipped**.
- Comment density on the 15 new production files runs 39 %–81 %; no new file
  crosses the 300-line split rule.
- Every wave50-B* JSON parses; no PNG over 2 MB; no token-like strings.

## What does NOT hold — see the report for severities

1. `runtimes/compose/src/main/java/com/styleconverter/runtime/visibility/VisibilityBoxRules.kt`
   (176 lines, new) is **dead**: zero references in the whole repo outside
   itself and its own unit test, while the iOS twin IS dispatched from
   `VisibilityApplier.swift:72` / `VisibilityExtractor.swift:70`.
2. `tools/visual/doc-staleness-check.sh` **exits 1** — the CI job of the same
   name would fail. Live vs documented: tooling 2030 vs 1979, converter 526 vs
   514, compose 3233 vs 3143, swiftui 2006 vs 1979, web-harness 282 vs 276;
   and Android dedicated-`*Applier` files 45 → **44** (B8 deleted
   `MultipleShadowApplier.kt`), stale in FOUR docs but flagged in only one.
3. `tools/titan/results/wave50-B3/_note.md:166` introduces the wave's only
   NEW `spec-cite-validate --include-data` failure (a css-tables-3 section number that does not exist in the ED,
   quoted inside a fenced block); the other 8 are pre-existing.
4. `tools/titan/results/wave50-B1/` and `tools/titan/results/wave50-B6/` carry
   NO `_note.md` and no `README.md`.
5. `tools/titan/results/wave50-B8/_note.md` is titled "queue 6(b) + 6(c) +
   6(d)" but documents only 6(c) and the deferred patch — the applied iOS
   RENDER change (`StyleBuilder.swift`, overflow clip moved inside the
   transform, 5 tests / 7 components) appears nowhere in it.
6. `tools/titan/provision-devices.sh` fixes ONE of three iteration-count
   budget loops; `:235` and `:325` keep the bug the orchestrator just removed
   at `:206`.
7. Two other skeptics' probe dirs were still in the tree at report time
   (`runtimes/compose/src/test/java/com/styleconverter/runtime/_skeptic50/`,
   `runtimes/swiftui/Tests/StyleConverterRuntimeTests/_skeptic50/`) — not
   touched by this lane; they must be gone before ship (the Compose one adds
   1 to the `@Test` count the doc-staleness check derives).

## Not tested, and why

- `:converter:test`, compose/`:app` gradle and Catalyst `xcodebuild` belong to
  S5 / S3 / S4 by the wave's build-system ownership rule. B6's converter
  change (`TextDecorationExpander.kt`) and every Compose/Swift unit test added
  this wave are therefore UNVERIFIED by this lane.
- `npm -w runtimes/web run test` / `npm -w apps/web-harness run test` are S5's;
  their counts here (1337 / 282) come from the one `doc-staleness-check.sh`
  run this lane was told to make, which invokes both internally.
- No device or browser was available, so no cell verdict was re-scored.

## Exact doc rows the doc-staleness gate needs (line numbers at audit time)

Do NOT copy the numbers below — the tree was still moving (concurrent skeptic
probe files change the `@Test` grep). **Re-derive** with
`bash tools/visual/doc-staleness-check.sh` AFTER every `_skeptic50` dir is
deleted, and write what it prints.

Test-suite tables (converter / compose / swiftui / web-harness / tooling rows):
`README.md:210,212,214,215,216` · `CLAUDE.md:290,292,294,295,296` ·
`docs/STATUS.md:2291,2293,2295,2296,2297` (+ the inline roll-up at
`docs/STATUS.md:2283`) · `runtimes/compose/README.md:15` ·
`runtimes/swiftui/README.md:16` · `apps/android-harness/README.md:29`.

Dedicated-`*Applier` file counts, Android 45 → 44 after
`runtimes/compose/.../effects/shadow/MultipleShadowApplier.kt` was deleted:
`README.md:56` · `CLAUDE.md:354` · `docs/STATUS.md:48` · and regenerate
`tools/visual/COVERAGE.md` with `node tools/visual/coverage-audit.mjs --md`
(only COVERAGE.md is checked by the gate; the other three drift silently).

Same deletion leaves two prose rows naming the deleted file as the LIVE
over-blur mechanism: `docs/STATUS.md:1883` and `docs/BACKLOG.md:766`.
`tools/titan/results/wave50-B8/_note.md` says to strike the STATUS row and
gives the measured σ table as the evidence.
