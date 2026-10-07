# wave53-lists-bakes (L1) — lane note

Builder lane L1 of wave 53, on the SHARED tree (PLAN §10.3; no worktree, no commit). Contract:
`tools/titan/results/wave53-plan/PLAN.md` "### L1 · lists-bakes"; briefs `nested-list-extractor.md` (Unit 1) and
`rtl-marker-bake.md` (Unit 2). Base: campaign/applier-campaign `e330e255` (= dev cdb8a845 + the committed wave-53
plan / gate record / tools; `tools/titan/extract-fixture.mjs` blob `26619a56` identical to cdb8a845's).

Both units are built, every pin is green, and every pin's mutation was EXECUTED (red → byte-exact restore → green,
full sha256 in `mutations.log`). The Chromium-side half of hunk M is verified only in pure pins: its real behaviour
under CDP, the gate-flag wire differential and the converter hop are ORCHESTRATOR WINDOW requests (below).

## Progress log (resumable)
- [x] Unit 1 (A seam-1, B counter-bake, C Swift fold, D Compose fold) + pins N1-N5, all mutations executed
- [x] Unit 2 (M `bidi-marker-bake.mjs` + call sites, P padding) + pins V1-V5, V3b, VF, `u2-narrow.patch` + V3c
- [x] census, in-process differential, PNG look, predictions, must-not-move, window requests

## 1. What changed and why

### Unit 1 — nested list (ONE commit: A + B + C + D; the brief: land all four together)
Looked at the PNGs first (ref | web | ios | android, wave53-open, rows y20-170): the ref nests Eleven/Nine/Eight at
x97 and reads 3/2/11/9/8/1; all three captures are flat at x57 reading 12/11/10/9/8, `1. One` is missing, iOS row 2
is `Two` with no marker — exactly the brief §2. `lists-bakes.geometry.py wave53-open wave53-open` prints
`GEOMETRY WRONG (rows 5/6)` on all three capture rows and `GEOMETRY OK` on the ref.

- **(A) `seam-1.patch`** (seam `tools/titan/extract-fixture.mjs`, never edited — patch only, PLAN §3 row 1):
  `IMPLIED_CLOSE_SCOPE` + exported `findImpliedClose(html, from, tagName, triggers)` beside `AUTO_CLOSE_TRIGGERS`,
  called from BOTH trigger sites (`walkChildren` l.1821-1833, `scanOwnText` l.3560-3570), so the two scans keep
  agreeing (wave-50 B4). HTML §13.2.6.4.7: an `<li>` opener nested inside a list opened after our `<li>` closes the
  inner item. Identity by construction when no boundary opens before the first trigger. KNOWN GAP named in the
  helper's banner (blockquote/table/…; dt/dd keep first-match; census 0 carriers). The draft
  `nested-list-extractor.patch-extractor.py` matched the tree exactly; the patch is that draft plus comments.
  Generator: `make-seam-1.py`. `git apply --check` clean on HEAD e330e255.
- **(B) `tools/titan/counter-bake.mjs`** `newInstance` (`lastNegBy`), `setCounter(…, who)`, `bumpCounter(…, who)`,
  `applyPairs`/`pseudoSet` thread `who` (walk `nodeIdx`, or `"<idx>::<pseudo>"`):
  `inst.implied = inst.negSum + value + (inst.lastNegBy === who ? 0 : inst.lastNeg)` — css-lists-3 §4.4.2 step 4
  after a step-3.3 stop; a self-stepping setter's own −increment already sits in `negSum` (the bake steps before it
  sets). The "KNOWN OUTLIER li-value-reversed-019" banner paragraph is rewritten: the spec explains it (1+3+1 = 5),
  and so it explains reversed-nested's Eleven (11) and li-value-reversed-008b (its own `<meta name=assert>`).
- **(C) Swift `StyleEngine/content/PseudoTextFold.swift`** `resolve` → new private `foldIntoRuns`: a `meta.runs`
  carrier whose `runs[0]` is TEXT gets the ::before inline run prefixed to BOTH `runs[0].text` and `text`
  (CSS 2.1 §12.1 / css-pseudo-4 §4.1 / CSS 2.1 §9.2.1.1). Named refusals kept: child-first run list
  (`pseudotext-runs-child-first`), box-shaped ::before (`pseudotext-runs-box`), styled ::before
  (`pseudotext-styled-nonuniform-before`), every ::after under runs (`pseudotext-runs`). 291 lines (< 300; was 228).
- **(D) Compose `content/PseudoTextFold.kt`** `resolve` runs branch → new private `foldBeforeIntoRuns`: the same
  fold, plus `before` removed from the copy's `pseudos` (map kept non-null), so
  `PseudoBucketExtractor.extractBeforeAfterConfig` builds no `Row { before, content }` — no ComponentRenderer hunk.
  The ::after-under-runs refusal is unchanged. 269 lines.

### Unit 2 — RTL markers (ONE commit: M + P; M must never land without P)
PNG look (rows y180-310): ref `foo .1` with the marker RIGHT (x133-145); web markers LEFT (x46-58); iOS no marker;
Android no marker and text +48 px (x151). Geometry probe on wave53-open: WRONG on all three captures, OK on ref,
and `rows 0-207 identical to wave53-open` on all three (the crop rule works).

- **(M) new `tools/titan/bidi-marker-bake.mjs` (200 lines)** — `collectMarkerFacts(page, elements, {fixture, html,
  stem})` (browser side; `{}` and no CDP session for a list-free walk; NEVER throws — a fault yields error facts that
  `planMarker` declines with a stamp), `parseSnapshotMarkers` (CDP `DOMSnapshot.captureSnapshot` → `::marker` nodes:
  host box, marker box, LayoutText string), `matchMarkers` (walk ⇄ snapshot by tag + used rect, unique only),
  `modelMarkerTexts` (string fallback: `bakeCounterStyles` on a CLONE, the ". " suffix's trimmed U+0020 restored),
  in-page `inPageMarkerProbe` (computed li/`::marker` facts + a hidden probe span's per-code-point glyph boxes,
  removed again; run after the walk measured everything), `planMarker(f, origin, first)` (pure): CDP box if its width
  matches the probe advance within `MARKER_PROBE_EPS` 0.5 px, else the analytic inline-start content edge (rtl:
  content right; ltr: content left − advance), stamped `marker-probe-mismatch` (box mismatched) or
  `marker-box-modelled` (no box) — MARKER-scoped, never `{ bail }`; glyph tops from the item's first own run (same font
  required, else decline); `groupCharRuns` then a per-grapheme split of no-strong-letter runs; runs sorted visually;
  `runProperties` + `font-variant-numeric` when not `normal`; `boxProps: { 'list-style-type': 'none' }`. Declines
  (image marker, no string, no first run, marker font ≠ first-line font, inside marker without a measured box, probe
  fault) leave the item exactly as before, stamped `marker-not-baked`. A modelled string adds `marker-text-modelled`.
- **`tools/titan/bidi-bake.mjs` call sites**: import; `planBidiBake` marker pre-pass (non-root, non-hidden boxes
  only) → box props gain `boxProps`, box entry gains `lossy` (only when non-empty), marker runs pushed AFTER the item's
  text runs (text-run child ids unchanged); `applyBidiBakePlan` stamps a box's `lossy`; `bidiBakeFixture` sets
  `walk.markers = await collectMarkerFacts(…)` after `mappingMismatch`, before `planBidiBake`. Header SCOPE BOUNDARY
  gains a ::MARKER bullet. `bidiBakeFixture`'s signature and its call site in extract-fixture.mjs are unchanged.
- **(P) `tools/titan/bidi-bake.mjs`**: walker records `padding` (4 resolved sides), `backgroundClip`,
  `backgroundOrigin`, `overflow` per element; `rootProperties(rect, position, el = null)` adds `padding: '0'` when the
  new exported `paddingIsSpent(el)` holds (non-zero on some side AND no content-box clip/origin AND overflow visible;
  CSS 2.1 §10.1 item 4); `applyBidiBakePlan` deletes every `padding-*` longhand/logical key first and lets
  Object.assign overwrite an authored `padding` shorthand IN PLACE. **Deviation from the brief, measured**: the brief
  said "delete every padding* key"; the padding census shows all 6 non-zero roots carry ONLY the shorthand, so keeping
  its key position is the smaller wire change (counter-suffix root keeps `padding` at index 1 → IR PaddingTop..Left
  stay at index 4-7, only their values change). V3 pins both the in-place shorthand and the longhand deletion.
  `paddingIsSpent` (6 code lines + doc) lives in bidi-bake.mjs because the plan names hunk P's logic there
  ("rootProperties/applyBidiBakePlan emit padding: 0 … only on a root whose …"); the new-file budget went to hunk M.
- **`StyleEngine/lists/ListMarkerOutsideHang.swift:33-41`** — comment only (PLAN §9 D4): "inline-END" → the
  inline-START side of an RTL item, and "no meta.markerText reaches them" → the wire did carry it, the items are
  out of flow; wave 53 bakes them upstream so none reaches them now.
- **Prepared, NOT applied: `u2-narrow.patch`** (P-narrow fallback, PLAN §2 L1 (P), §6 rule 7): one condition in
  `planBidiBake` — `rootProperties(e.rect, e.position, hostsMarker ? e : null)` — plus pin V3c replacing V3's
  bidi-lines-002 case. Base = the final U2 files (sha256 in its header). Generator `make-u2-narrow.py`.
  Verified twice (`u2-narrow.verify.log`): applied → 61/61 green incl. V3c; V3c mutation red; `git apply -R` →
  both files byte-exact to the U2 state.

## 2. Census (method + numbers) — the lane's own, cross-checked against expectations.json

`l1-census.py` (pure JSON; output `l1-census.out.txt`), `u1-differential.sh` (in-process, pure node), the plan's
`rtl-marker-bake.padding-census.py` (re-run: same 17 / 14 roots in 7 docs / 6 roots in 4 docs) and
`bake-root-tags.out.txt`:
1. **Wire identity** wave52-ship vs wave53-open: 1435/1435 per-test IR documents, 0 byte-different.
2. **U1 extraction reach** (in-process, no gate flags, 1435 tests of wave52-ship's tests.list, base = HEAD's
   extract-fixture.mjs + counter-bake.mjs, fix = tree with seam-1 + lane counter-bake): **changed 1** —
   `css/css-lists/counter-reset-reversed-nested.html`; 0 errors (`u1-differential.out.json`; re-run identically on
   the final combined tree, `seam-1.verify.log`). Matches the brief §6 and `revertUnits.U1.wire`.
3. **U1 runtime reach** (C/D): components with `meta.runs` AND `pseudos.before`: 2 corpus-wide — the target's `Two`
   (folds) and `display-contents-dynamic-before-after-001__1__3` (identity: no `_text`; pinned as the N4/N5 control).
4. **U2 hunk M reach**: abspos `li` components 4, all in counter-suffix, all carrying `meta.markerText`. The
   browser half opens a CDP session on 4 bidi-baked docs (the 3 arabic-indic + counter-suffix — the only bidi-baked
   walks holding a list item); the arabic-indic items are bake ROOTS, which `planBidiBake` never hands to
   `planMarker` (V2), so their plan — and wire — cannot change.
5. **U2 hunk P reach**: 79 bake roots in 17 docs; 14 carry a padding key; NON-ZERO 6 roots in 4 docs
   (counter-suffix ×2, anchor-center-safe-rtl ×2, bidi-lines-001 ×1, bidi-lines-002 ×1); none trips a guard. Extra
   check (hunk P reads the COMPUTED padding, the census reads fixture keys): no bake root's tag carries a UA padding —
   the 17 sources hold no ul/menu/dir/td/th/fieldset/…; their only `<ol>`s are counter-suffix's (author `0 3em`,
   roots) and arabic-indic's (NOT roots — their `li` are). So computed-non-zero = key-non-zero = 6 roots / 4 docs.
6. **Carrier set** = expectations.json `lanes.L1-lists-bakes.captureCarriers` exactly: web/ios
   {counter-reset-reversed-nested, counter-suffix}; android + {bidi-lines-001, bidi-lines-002,
   anchor-center-safe-rtl}. Wire carriers = those 5 documents. Passing today (wave53-open, `cells.mjs`):
   counter-reset-reversed-nested web P 0.9506 / ios P 0.9508 / android P 0.9509; counter-suffix web P 0.9818 /
   ios P 0.9802 / android P 0.9547; bidi-lines-002 android P 0.9534; bidi-lines-001 android f 0.8934;
   anchor-center-safe-rtl unscored → 7 P, 1 f among the 8 scored carrier cells.

## 3. Pins and EXECUTED mutations (full sha256 in `mutations.log`; seam runs in `seam-1.verify.log`)

| pin | file | mutation | red | restored |
|---|---|---|---|---|
| N1 | `tools/titan/extract-fixture-implied-close.test.mjs` | findImpliedClose ignores depth (first match) | N1 + helper + N2 red | byte-exact, 4/4 |
| N2 | same | revert the scanOwnText site only | N2 red (only) | byte-exact, 4/4 |
| N3 | `tools/titan/counter-bake.test.mjs` | A: drop the step-4 term | nested + 008b red | byte-exact, 27/27 |
| N3 | same | B: always add the step-4 term | `<li value>` ANCHORS + li-value-reversed-013 red | byte-exact |
| N4 | `PseudoTextFoldTests.swift` (Catalyst) | restore the blanket runs refusal | 5 failures (N4, child-first, ::after log pins) | byte-exact, 24/24 |
| N5 | `PseudoTextFoldTest.kt` (JVM) | keep `before` in the copy | N5 red (double paint) | byte-exact, 53/53 |
| V1 | `tools/titan/bidi-bake.test.mjs` | m1 drop `none` | V1 + V4 red | byte-exact, 61/61 |
| V1 | same | m2 RTL analytic at li.left − w | V1 (analytic pass) + V5 red | byte-exact |
| V1 | same | m3 no per-grapheme split | V1 red | byte-exact |
| V2 | same | emit marker runs for roots | V2 red | byte-exact |
| V3 | same | remove the `padding: '0'` line | V3 ×2 red | byte-exact |
| V3 | same | remove the content-box guard | V3 red | byte-exact |
| V3 | same | remove the longhand delete loop | V3 red | byte-exact |
| V3b | same | drop the NON-zero guard | V3b red | byte-exact |
| V4 | same (skip-guarded on tools/wpt) | m1 (omit `none`) → stamped 10 | V4 red | byte-exact |
| V5 | same | delete the probe self-check | V5 red | byte-exact |
| V5 | same | turn the mismatch into a whole-test `{ bail }` | V5 red | byte-exact |
| VF | same | collectMarkerFacts rethrows a page fault | VF red | byte-exact |
| V3c | same, with `u2-narrow.patch` applied | drop the marker-host condition | V3c red | byte-exact; patch reverted byte-exact |

Mutation N1/N2 ran on the seam file with `seam-1.patch` applied under the per-file lock
`tools/titan/runs/wave53-lock/extract-fixture.mjs` (mkdir mutex, acquired/released twice). Seam sha256: before
apply `831aa02d…ee721`, patched `845a3d26…fe5c9`, restored `831aa02d…ee721` (byte-exact, `git status` clean) both
times. `mutate.sh` refuses (exit 2, logs nothing) a mutation whose old string does not apply — added after one
void batch (zsh did not word-split the test command; those entries were deleted, not counted, and re-run).

Focused suites, final state:
- With seam-1 applied (lock): `node --test` implied-close + extract-fixture + counter-bake +
  generated-content-bake + bidi-bake + counter-style-bake → **638/638**.
- Catalyst `-only-testing:StyleConverterRuntimeTests/PseudoTextFoldTests` → **24/24** (private `-derivedDataPath`
  in the session scratchpad, to stay off the shared DerivedData).
- JVM `:runtime:testDebugUnitTest --tests '*PseudoTextFold*' --tests '*PseudoBucketExtractor*' --tests '*ListOrdinal*'`
  → **53/53** (18 + 21 + 14).
- **Note for every sweep before U1 lands: `extract-fixture-implied-close.test.mjs` is RED (4/4) without
  seam-1.patch, by design — it is seam-1's pin and lands in the same commit.**

## 4. Predictions (per target cell, with confidence) — the plan's table holds; nothing in the build moves it

| cell (from wave53-open) | prediction | confidence | why |
|---|---|---|---|
| counter-reset-reversed-nested web P 0.9506 | P ≥ 0.995, GEOMETRY OK | HIGH | post-fix DOM = the ref's own (real ol/li, the ref's numbers as ::before text) |
| counter-reset-reversed-nested ios P 0.9508 | P ≥ 0.99, GEOMETRY OK | MED-HIGH | C puts "2. " on both channels; nested-ol indent/no-gap already native (brief §5) |
| counter-reset-reversed-nested android P 0.9509 | P ≥ 0.99 | MED | D removes the Row wrapper; run-plan engagement on device unverified |
| counter-suffix web P 0.9818 | P ≥ 0.995, GEOMETRY OK, rows 0-207 identical | HIGH | Blink's left markers gone (`none`), Chromium's own glyph boxes painted right |
| counter-suffix ios P 0.9802 (DEGENERATE) | P ≈ 0.986 (replay 0.9888) | HIGH stays P / MED magnitude | out-of-family LTR residuals cap it below 0.99 |
| counter-suffix android P 0.9547 (DEGENERATE) | P ≈ 0.985 (replay 0.9900), GEOMETRY OK | MED-HIGH | P moves RTL text x151 → 103; M adds `.1` at x133-145 |
| bidi-lines-002 android P 0.9534 | P 0.975-0.98 | MED | P removes the +4 px content-box shift |
| bidi-lines-001 android f 0.8934 | ≈ 0.95-0.96, f→P possible | MED-LOW | same mechanism |

Expected extract-log line for counter-suffix: `[bidi-bake: baked — 2 roots, 10 runs] [counter-bake: baked — 6
markers, 2 declined]` (today: 4 runs; 10 markers, 2 declined). Pre-registered stamp outcomes: no `marker-*` stamp
expected if CDP serves the string and box. `marker-probe-mismatch` / `marker-box-modelled` / `marker-text-modelled`
do NOT change these predictions (on counter-suffix the analytic edge = the li's right edge x112 = the brief's measured
box; the modelled string "1. "/"א. " = Chromium's). A **`marker-not-baked`** stamp on any RTL item DOES: that item
keeps today's marker (web left, natives none), counter-suffix web prints GEOMETRY WRONG and U2 trips revert rule 4 —
fix the probe source, never ship it as a pass.

**Must not move** (`watchlist.txt` L1 must-not-move block, 92 cells, expectations.json `mustNotMove`): every other
`css-lists/counter-reset-reversed-*` ×3; `counter-list-item`, `-2`, `-3`, `-slot-order` ×3;
`css-display/display-contents-dynamic-before-after-001` ×3; web + ios of bidi-lines-001/-002; all three platforms of
the 13 other bidi-baked documents (arabic-indic 101-103, bidi-tab-001, boundary-shaping-009,
hyphenate-character-005, bidi-plaintext-br-001, dir-selector-change-003/-004, dir-style-02a/-03a,
attachment-local-positioning-3/-4). Plus: counter-suffix rows y0-207 on every platform (crop compare, the geometry
probe's second verdict); the per-test IR of `selectors/dir-style-02a`, `dir-selector-change-003`, `-004` byte-identical
(V3b); web + ios captures of anchor-center-safe-rtl.

## 5. Hand-offs

- **Seam**: `seam-1.patch` (PLAN §3 row 1) → `tools/titan/extract-fixture.mjs`; `git apply --check` clean on
  e330e255; pins N1/N2 green with it applied; seam file restored byte-exact. The only seam this lane needs.
- **Hunks for other lanes**: none.
- **Commit units** (the orchestrator commits; §4 step 3):
  - **U1**: `seam-1.patch` applied to `tools/titan/extract-fixture.mjs`; `tools/titan/extract-fixture-implied-close.test.mjs`;
    `tools/titan/counter-bake.mjs`, `tools/titan/counter-bake.test.mjs`;
    `runtimes/swiftui/Sources/StyleConverterRuntime/StyleEngine/content/PseudoTextFold.swift`,
    `runtimes/swiftui/Tests/StyleConverterRuntimeTests/PseudoTextFoldTests.swift`;
    `runtimes/compose/src/main/java/com/styleconverter/runtime/content/PseudoTextFold.kt`,
    `runtimes/compose/src/test/java/com/styleconverter/runtime/content/PseudoTextFoldTest.kt`.
  - **U2**: `tools/titan/bidi-marker-bake.mjs` (new), `tools/titan/bidi-bake.mjs`, `tools/titan/bidi-bake.test.mjs`,
    `runtimes/swiftui/Sources/StyleConverterRuntime/StyleEngine/lists/ListMarkerOutsideHang.swift` (comment only).
  - The two units share no file and no test dependency (V4's static counter-suffix extraction is unchanged by seam-1).
  - Lane dir `tools/titan/results/wave53-lists-bakes/` with either; `u2-narrow.patch` is applied only on its trigger.
- **Docs (orchestrator)**: BACKLOG 2(c⁴) re-true once U2 lands — "RTL list markers inside a bidi-bake root are baked
  upstream (tools/titan/bidi-marker-bake.mjs, wave 53 L1): Chromium's marker string/box/glyphs → positioned runs +
  `list-style-type: none`; the runtimes' positioned overlay still has no marker branch (no longer reached by the
  corpus)". 2(f) closes with U1 (nested-list extractor + §4.4.2 step 4 + both native folds).

## ORCHESTRATOR WINDOW REQUESTS

All start Chromium or Gradle: run in a device-idle window, on the integrated tree, in this order.

1. **CDP ::marker probe** (rtl brief §8 (a); with U2 in the tree):
   `node tools/titan/results/wave53-lists-bakes/marker-probe.mjs`
   Look for: last line `MARKER PROBE: ALL PASS` (exit 0) — outcome `baked — 2 roots, 10 runs`; each of the 4 RTL
   items `list-style-type none`, NO `marker-*` stamp, marker runs `['.','1']`, `['.','2']`, `['א.']`, `['ב.']`, frame
   x ⊂ [131,148], tops ≈ the text run's; DOMSnapshot strings `'1. ','2. ','א. ','ב. '`; row-1 box left 112 ± 0.5. It
   also prints the raw snapshot marker nodes and the AX ListMarker names. On a FAIL: a `marker-text-modelled` /
   `marker-box-modelled` stamp means the CDP source shape differs from `parseSnapshotMarkers`' reading (send the
   printed `snapshot marker` lines back to the lane); `marker-not-baked` means U2 must not land as is.
2. **Gate-flag wire differential** (PLAN §4 step 3; the in-process differential did not exercise --post-load /
   --bidi-bake / --vt-bake):
   `bash tools/titan/results/wave53-lists-bakes/wire-differential.sh <pre-tree> <post-tree> <out-dir> wave53-open`
   - U1: pre = `git worktree add --detach /tmp/w53-l1-pre cdb8a845` (symlink `node_modules`, `tools/wpt` from the
     main checkout), post = the U1-commit tree → expect exactly `CHANGED ./css-lists/counter-reset-reversed-nested.json`.
   - U2: pre = the U1-commit tree, post = the U2-commit tree → expect exactly counter-suffix, bidi__bidi-lines-001,
     bidi__bidi-lines-002, anchor-center-safe-rtl (+ any `__ref.json` twins is a finding); the three zero-padding
     selectors fixtures identical. Under P-narrow: counter-suffix only.
3. **Converter hop** (the gate's own path, section-runner.sh:375 / :416 / :435, for one test):
   `POST_LOAD_EXTRACT=1 BIDI_BAKE=1 VT_BAKE=1 node tools/titan/extract-fixture.mjs css/css-counter-styles/counter-suffix.html`
   (log line as §4 predicts), then `echo css/css-counter-styles/counter-suffix.html > /tmp/l1-cs.list &&
   node tools/titan/build-combined-fixture.mjs --out fixtures/wpt/_l1-cs.json --tests /tmp/l1-cs.list &&
   ./gradlew --no-daemon :converter:run --args="convert --from css --to ir -i fixtures/wpt/_l1-cs.json -o out/l1-cs"`.
   Look for: the 4 RTL li with `ListStyleType NONE` and no `meta.markerText`; 6 new run components whose types are
   all existing ones (incl. `FontVariantNumeric`); both RTL roots' PaddingTop..Left `{px:0}` at their old index 4-7;
   no converter warning for the new keys. (The per-test fixture has the same `{_wpt, components}` envelope, so a
   direct `-i fixtures/wpt/css-counter-styles/counter-suffix.json` also converts, with unprefixed names.)
4. **Replay** with the measured box (rtl brief §8 (d)): `python3 tools/titan/results/wave53-plan/rtl-marker-bake.replay.py`
   then `node tools/titan/results/wave53-plan/rtl-marker-bake.replay-score.mjs` — expect ≈ the brief's 1.0000 / 0.9888
   / 0.9900 if (1) reports the box at x112.
5. After the probe run: `python3 tools/titan/results/wave53-plan/lists-bakes.geometry.py wave53-probe wave53-open`
   → `GEOMETRY OK` on all six target rows and `rows 0-207 identical to wave53-open` on the three counter-suffix rows.

## 6. What I could NOT verify (honest list)

- Anything Chromium does: the DOMSnapshot `::marker` node shape (box entry + LayoutText text) `parseSnapshotMarkers`
  assumes, `getComputedStyle(li, '::marker')`, the probe span's glyph boxes, the CDP session inside the bake's
  browser. The V-pins use the verbatim wave52-ship geometry but MODEL glyph advances (' ' 4.48, '.' 4.42, tabular
  digits 9.9, 'א' 10.2, 'ב' 10.0).
- The gate-flag (post-load / bidi / vt) wire differential for both units; the converter hop of the new keys.
- Every device picture: whether iOS/Compose paint `Two` via the run plan or the leading label (both carry "2. "),
  the native rendering of a run with `font-variant-numeric`, and the predicted scores (replay- and reasoning-based).
- No full suites were run (lane rule); the ios-harness XCTest was not run (no simulator).

TREES: /Users/dranak/Documents/Projects/Style-Converter/.claude/worktrees/trusting-bohr-bd6fbf (Gradle ran in its
apps/android-harness; xcodebuild used a private -derivedDataPath in the session scratchpad, no other tree)

STATUS: COMPLETE

## Orchestrator window results (2026-10-07, device idle, shared tree with U1 + U2 uncommitted)

1. **CDP `::marker` probe** — `marker-probe.out.txt`: `MARKER PROBE: ALL PASS` (exit 0). Outcome `baked — 2 roots, 10 runs`;
   each of the four RTL `<li>` (`counter-suffix__0__4__0/1`, `__0__5__0/1`) has `list-style-type: none`, no `marker-*` stamp
   ("measured, not modelled"), marker runs `[".","1"]`, `[".","2"]`, `["א."]`, `["ב."]`; row-1 marker box left 112 = 112 ± 0.5;
   DOMSnapshot marker nodes read `1. `, `2. `, `א. `, `ב. ` (plus the LTR `一、`, `二、`, `일, `); AX ListMarker names as predicted.
2. **Gate-flag wire differential** — NOT run as written (three full extractions over 1435 tests with the bakes, on a swapping
   host, would cost hours); the closing gate's wire control (`tools/titan/results/wave53-gate/control-check.mjs`, every per-test
   IR byte-compared against `wave53-open`, allowed set = the plan's wireCarriers) is the authoritative check of the same
   property, over the real gate extraction. The reduced in-process differential the lane ran (1/1435) stands as the pre-commit
   evidence. Recorded as an orchestrator deviation.
3. **Converter hop** (gate flags, one test) — `orchestrator-windows.out.txt`: extract log `[bidi-bake: baked — 2 roots, 10 runs]
   [counter-bake: baked — 6 markers, 2 declined]`; converter: no warning; IR 23 → 29 components (6 new run components), 12 `<li>`
   of which 4 `ListStyleType NONE` and 0 of those carry `meta.markerText`, `FontVariantNumeric` present.
4. **Replay with the measured box** — `replay-score.measured-box.txt`: android P-only 0.9815, M+P **0.9900** (the brief's 0.9900);
   bidi-lines-002 android 0.9534 → 0.9818, bidi-lines-001 android 0.8934 → 0.9629 (the brief's numbers).
5. **Wire renumbering shadow (found by the hh-probe control, 2026-10-07)** — the counter-bake's +6 components on `counter-suffix`
   renumber the component ids of the 15 css-counter-styles documents after it in `tests.list` (every `cssom/cssom-*-setter`
   and `-setter-invalid`): `<name>-677…680` → `-683…686`, content identical, captures byte-identical on all three platforms.
   The extractor's component counter runs per section, not per test — so U1-B's wire radius is "counter-suffix + an id shadow
   over the rest of its section", wider than the lane's census (1 document) by 15 id-only documents. The gate's wire control
   (`wave53-gate/control-check.mjs`) now reports this class as "renumbered" with the explaining carrier (PLAN §10 item 6,
   `expectations.json` → `wireRenumbering`); the lane's `wireCarriers` entry stays `counter-suffix` alone, which is the
   content-changed set. Nothing in the lane's code changes for this.

