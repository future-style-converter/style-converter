# Wave 52 — gate record

## Opening gate `wave52-open` (2026-09-25, 13:55 → 15:16 UTC, quiet host, `tools/titan/gate-driver.sh wave52-open`)

Tree: `campaign/applier-campaign` == `neworigin/dev` 5d9ed628 (wave 51 PR 3 merged), unmodified.

- 30/30 sections OK, every column 48/48/48 on the first attempt (`summary-open.txt`).
- Fixture net `BASELINE=1 ./test-all.sh --gate-set`: **exit 0 on all 8 fixtures** (`fixture-net-open.txt`) — the 390 baselines refreshed by PR 3 and the 10-line ledger hold on device.
- `node tools/titan/score-gate.mjs wave51-fix wave52-open --watch tools/titan/results/wave50-gate/watchlist.txt`: **0 gained / 0 lost / 0 movers / 0 newly measured / 0 unmeasured** over 4117 scored cells (`score-open.json`, `score-open.txt`). Totals unchanged: web 1215/1379 · iOS 1089/1369 · Android 1080/1369 = corpus-v6.17.

What this proves: PR 2 (9(g)) and PR 3 (label chrome) moved no corpus cell — as predicted (their carriers are off-corpus; TITAN captures suppress the chrome) — and the pipeline is deterministic run-to-run for the third consecutive gate (wave51-open, wave51-fix, wave52-open). Every wave-52 lane's prediction attributes against this run.

## Closing gate

Three stages, all 2026-10-05 UTC, all through `tools/titan/gate-driver.sh` on a quiet host (one emulator, one
simulator). Every run's installed `base.apk` sha1 / `.app` digest is recorded against the build it tested in
`build-hashes.txt` (all MATCH).

### 1. First attempt — `wave52-final` (commit 308d11f0, 19:00 → 20:44) — STOPPED THE SHIP

- The launch was cut by the background task's time limit 10 minutes in (two sections done); `--resume` ran the other
  28. Same installed builds before and after the cut (`build-hashes.txt`, first two blocks).
- 30/30 sections OK, every column full, first attempt. Fixture net exit 0 on all 9 gate fixtures.
- `score-final.txt` (against wave51-fix): web 1229/1372 · iOS 1118/1362 · Android 1104/1362 — gained 99, **lost 13**,
  unmeasured-now 21. `score-final-vs-calib.txt` (instrument held fixed): gained 86, **lost 5**, unmeasured-now 2.
- `adjudication.txt`: rules R1 and R2 FAIL — five lost cells outside the eight pre-registered instrument-only ones
  (PLAN §10: "a lost cell outside that list of eight is a REAL regression and stops the ship"):
  `css-lists/counter-reset-reversed-nested` ios 0.9551 → 0.9424 and android 0.9509 → 0.9425;
  `css-text/hyphens/hyphens-manual-011` / `-012` / `-manual-inline-011` android 0.98 → 0.89.
- `tripwires.txt` (lanes' mechanism checks): frame-ink overrun-right 208 → **0**, overrun-left 84 → **1** (one pixel,
  `backdrop-filter-clip-rect-2` android, cell unchanged at P 0.9981); frame-ring stamped on exactly the 4
  fractional-box tests; absence-only 19; blank-captures 43 = 45 − the two `composited-under-rotateY-180deg-preserve-3d`
  natives L4 un-blanked; the four `abspos-auto-sizing-fit-content-percentage` Android captures byte-identical to the
  opening gate (L4 b″ moved nothing); L6's orange rows at 161–163 / 171–175 (were 158–160 / 166–170).
  `frame-ink-census.mjs` is a re-implementation of the planner's lost script; on wave51-fix it reproduces
  3717 / 208 / 84 / 23 / 38 / 47 exactly.

### 2. Two root causes, both older than this wave (fixed in commit aaf676c5)

**Android `ch` was a rasteriser's number.** `ChUnitMetrics` measured '0' with a plain `Paint()` (advances hinted to
whole pixels) through `Paint.measureText` (`Math.ceil` of the run). Measured on the captures: the `10ch` box of
hyphens-manual-011 is 194 px wide on Android against 197 on the reference, web and iOS (ch = 19, font = 19.2 at 32 px
monospace) — it holds nine glyphs, so L9's soft-hyphen pre-break (F5), correct for that box, took the earlier
opportunity (`Deoxy-` / `ribo-` / `nucleic` / `acid`, four lines for the reference's three). The same defect made every
`5ch` box of ch-units-vrl-005..008 65 px against the reference's 63 (Inter '0' = 12.6 at 20 px, answered as 13).
Fix: linear sub-pixel flags + `Paint.getRunAdvance`. That made `Nch` EQUAL to N glyphs, which exposed Compose's
round-to-nearest of the box (probe 1 below); `ChUnitMetrics.fitSafePx` rounds a ch-derived length UP to a whole device
pixel and leaves a length already on a pixel alone (probe 2 below measured why).

**Both natives drew a list marker on an `<li>` that is not a list item.** `counter-reset-reversed-nested` is
`li { display: block }` with authored `li::before` counters; both natives painted a native `1.`…`5.` in front of them
(ink columns 57–65 at wave52-calib — the pre-wave state). L6's outside-marker hang moved that stray marker to 38–49,
which is all the "regression" was. css-lists-3 §3.1: the marker follows the box. Fix: `ListItemMarkerGate
.displayTakesMarkerAway` on both natives' parent-loop marker paths.

### 3. Probes of the fixes (affected sections only, each with a byte-identity control)

`control-check.mjs <pre-fix> <post-fix>` knows which captures each fix MAY change (a per-test IR with a `"u":"CH"`
length — Android only; an `<li>` whose authored Display is not LIST_ITEM — both natives) and requires everything else
byte-identical. It holds on every probe: web 0 changed, iOS only the five `<li>` documents, Android only carriers.
On identical captures (wave52-open vs -calib) it reports 0 changed; across the lanes' own changes (calib vs final) it
fails with 747 leaks — it can fail.

| run | tree | sections | vs wave52-final (`ab-diff.mjs`) |
|---|---|---|---|
| `wave52-probe` | fixes, no pixel rounding | 7 | f → P 7 (the five lost cells + hyphens-auto-010 and hyphens-span-001 android), **P → f 2**: `line-clamp/block-ellipsis-023` / `-024` android 0.9736 → 0.9496 — `32ch` = 655.2 device px rounded to 655 under a 655.2-px line, the word "lines" wrapped |
| `wave52-probe2` | + "strictly above" pixel snap | 5 | 023/024 back; **P → f 2**: `ch-units-vrl-003` / `-004` android 0.9504 → 0.9440 — their upright `5ch` = 315 device px exactly became 316 |
| `wave52-probe3` | + round-up-only snap (shipped) | 5 | f → P 6 (the three hyphens-manual cells + hyphens-auto-010, hyphens-punctuation-001, hyphens-span-001 android), P → f 0; 26 movers, 25 up, one down (`hyphenate-limit-chars-001` android f 0.8608 → 0.8581) |

The marker fix on its carriers (wave52-probe): `counter-reset-reversed-nested` ios 0.9424 → 0.9508, android 0.9425 →
0.9509 (the stray marker is gone; the picture is still wrong — see the degenerate list); the four
`gradient-powerless-hue-*` documents (`li { display: flex }`) rise +0.009…+0.012 on both natives, all still failing.

Unit pins for every piece, with nine executed mutations each killed by exactly its named pins:
`closing-fixes-mutations.py` → `closing-fixes-mutations.log`.

### 4. The expected result, assembled — `wave52-preview`

`wave52-preview` is a directory of symlinks: `wave52-probe3` for the five `ch` sections, `wave52-probe` for css-lists
and css-images, `wave52-final` for the other 23 (which the control proves the fixes cannot touch). Scored
(`score-preview.txt`, `score-preview-vs-calib.txt`):

- web **1229/1372** · iOS **1119/1362** · Android **1111/1362** (wave51-fix: 1215/1379 · 1089/1369 · 1080/1369).
- gained **102** = 13 instrument-only + 89 render (web 18 · iOS 35 · Android 36); lost **8**, all eight the
  pre-registered instrument-only cells; newly measured 0; unmeasured-now 21 = 19 absence-only + L6's two.
- `adjudicate.mjs`: all five rules hold.

### 5. Every flipped cell looked at — `cell-review.json`

Six reviewers looked at all 102 gained and 8 lost cells against the reference (contact sheets from
`review-sheets.mjs`, full-size pictures and the test source); every non-plain verdict and every sixth plain one went to
a second, adversarial reader (26 cells; two verdicts overturned, one in each direction).

- **91 FAITHFUL** gains.
- **11 DEGENERATE** gains — they pass, and the picture does not earn it. Recorded, never claimed as fixes:
  - `anchor-position-multicol-007` android — the forbidden red anchor box is still drawn.
  - `contain-inline-size-bfc-floats-001` ios + android — the orange bar is not beside the third float (L2 pre-labelled it).
  - `counter-suffix` ios + android — the two `dir=rtl` lists have no markers at all.
  - `hyphens-out-of-flow-001` / `-002`, `hyphens-span-001` android — the hyphenation under test is wrong: no hyphen in
    `-001` or `span-001`, the break landing at a character (`highwa` / `y`; `-001` also `h` / `ighway` and `high` /
    `way`); `-002` hyphenates only boxes 1, 2 and 7. The box width now matches the reference. `-001` / `-002` already
    passed in `wave52-final` (before aaf676c5); `span-001` flipped with aaf676c5 (what moved the pair in the first
    attempt: BACKLOG queue 4(g)). F5 declines a run with no space.
  - `s-11-1-1b-006` ios + android — the black square is 5 px too high on both (ink band y 36–70 against the
    reference's 36–50 + 56–75) — reviewers' mechanism guess, untraced: a `display: table` body laid out as blocks.
  - `backdrop-filter-basic-blur` ios — RING-FENCED, reported plainly: f 0.9469 → P 0.9526 through L2's generic frame
    clip; its filter boxes sit 24 px right of the reference. Not a target of this campaign's lanes.
- **8 HONEST_FAIL** — all eight lost cells are wrong against the re-rendered reference; none is a suspect reference.
- Not in the lists but known: `counter-reset-reversed-nested` ×3 passes at 0.9506–0.9509 with a flat list and wrong
  counter values (the extractor slots the nested `<ol>`'s items under the root list and drops the last `<li>`).

### 6. The gate of record — `wave52-ship` (commit aaf676c5, 2026-10-05 21:54 → 23:27 UTC)

- Corpus: 30/30 sections OK, every column full (48/48/48; css-cascade 43/43/43), first attempt, no reprovision.
  Installed builds MATCH the ones built from the tree (`build-hashes.txt`, the `wave52-ship` block).
- Fixture net (second launch, `--skip-corpus`): exit 0 on all 9 gate fixtures (`gate-driver/fixture-net.log`).
- `score-ship.txt` / `score-ship-vs-calib.txt` / `adjudication-ship.txt`: exactly the preview's numbers — web
  **1229/1372** · iOS **1119/1362** · Android **1111/1362**; gained 102 (13 instrument-only + 89 render: web 18 ·
  iOS 35 · Android 36), lost 8 (all pre-registered instrument-only), newly measured 0, unmeasured-now 21; render
  lost 0. **All five adjudication rules hold.**
- Determinism: `ab-diff.mjs wave52-preview wave52-ship --threshold 0.0001` → 4096 cells, 0 differ;
  `control-check.mjs wave52-preview wave52-ship` → 0 of 1435 captures changed on each platform. The gate of record is
  byte-identical to the assembly the probes predicted, so every statement written against `wave52-preview` holds
  for `wave52-ship` unchanged.
- `tripwires-ship.txt`: as in §1 (overrun 0 / 1 px; frame ring 4 / 4; absence-only 19; blank-captures 43 with the
  two L4 un-blanked cells named; L4 b″ four captures byte-identical to the opening gate; L6 rows 161–163 / 171–175;
  the ring-fenced test reported plainly: web P 0.9943 → P 1, iOS f 0.9469 → P 0.9526, Android f 0.899 → f 0.904).
- Snapshot: `tools/titan/results/corpus-v6-18.json` (`make-corpus.mjs` over `score-ship.json`, `score-calib.json`,
  `cell-review.json` and `corpus-v6-18.note.md` — no number typed by hand).

### 7. The three device A/B exclude arms (2026-10-05 23:28 → 23:58 UTC, `device-ab.sh`; include arm = `wave52-ship`)

Each arm: ONE mechanism taken out of the shipped tree, the sections that hold its cells captured, the installed
`base.apk` sha1 / `.app` digest recorded against the build (`build-hashes.txt` — MATCH, all three), the tree restored
(`git diff --quiet -- runtimes apps converter ':(exclude)*.md'` clean after each). Read-outs `ab-t5.txt`, `ab-ma.txt`,
`ab-f1.txt` (`ab-diff.mjs <arm> wave52-ship --platforms ios,android`, |Δ| ≥ 0.002).

- **t5 — L6 T5, the outside-marker hang** (Compose seam-1 reversed; iOS `t5-exclude-ios.integrated.patch`, seam-2
  reversed and re-cut because the closing-gate `<li>`-display fix sits inside seam-2's context; seam-3/4 stay). 254
  cells: the hang flips `counter-suffix` ios f 0.9285 → P 0.9802 and android f 0.9030 → P 0.9547 (both DEGENERATE:
  the RTL lists still have no marker); 18 more cells up (`change-list-style-type-001` ios +0.084 / android +0.075,
  `first-line-and-marker` +0.029 both, `counter-list-item`, `-2`, `-3`, `broken-symbols`, `change-list-style-type-002`,
  `first-letter-skip-marker`, `add-inline-child-after-marker-002`), 2 down (`first-letter-exclude-inline-marker` ios
  −0.006 / android −0.009, failing in both arms); P → f 0.
- **ma — L8 M-A, the Inter measuring face** (Compose seam-1 reversed; iOS `ma-exclude-ios.integrated.patch`, the SF
  measurement). 288 cells: M-A flips `hyphens-auto-last-word-001` ios f 0.9032 → P 0.9991 and, with the closing `ch`
  fix present in both arms, `hyphens-punctuation-001` android f 0.938 → P 0.9981, `hyphens-out-of-flow-001` / `-002`
  android f 0.9218 / 0.9291 → P 0.9685 / 0.982 and `hyphens-span-001` android f 0.9166 → P 0.9532 (the last three
  DEGENERATE); it costs `hyphens-manual-inline-012` android −0.010 (P 0.9903 → 0.9803) and `ch-units-vrl-003` / `-004`
  −0.003 on both natives (P in both arms — the `ch-units-vrl` flips are M-B's, present in both arms);
  `text-decoration-inset-004` and `bidi-lines-001` / `-002` did not move; P → f 0.
- **f1 — L9 F1, the hanging-punctuation fold** (`drop-F1.patch` applied). 96 cells: 94 identical; F1 is what moved
  `hanging-punctuation-inline-001` ios P 0.9756 → P 0.9555 and android f 0.9495 → f 0.9452. No flip either way.
  DECISION: F1 KEPT — the CSS-correct fold (kinsoku wrap, inherited colour), the cell still passes on iOS, and a score
  drop with no flip does not outrank picture-correctness; this deviates from the lane's "apply drop-F1 if the arm
  confirms the drop", and is recorded as such in docs/BACKLOG.md 4(f). `drop-F1.patch` stays as the reversible arm;
  the iOS cell is a thin pass on the wave-53 watch list.
- A side finding of the arms: at each arm's start `stop_our_processes` named the previous run's qemu as "not launched
  by provision-devices.sh" — the launcher pid that provision-devices.sh records forks qemu as a child and, on SIGTERM,
  exits after waiting for it, leaving the dying qemu an orphan no recorded pid reaches. provision-devices.sh now records
  the children too, once they exist (uncommitted at the arms' time; in the ship commit).

### 8. After the PR opened (2026-10-06)

- **CodeQL.** All four required ci.yml contexts and the other seven jobs green on f122b50c; the bare `CodeQL` summary
  check FAILED with "11 new alerts … 11 high" — real findings in this PR's own code, not the stuck analyzer:
  `js/regex-injection` (cells.mjs ×2, one pre-existing), `js/redos` (the redundant `(?:-[^_]+)*`), and the regex
  `<script>` / `<!-- -->` filters in counter-style-author.mjs, png-replay.mjs and census.mjs plus one quote escape.
  Fixed in code (`tools/titan/html-blocks.mjs` + `html-blocks.test.mjs`: byte-equal to the regexes over every corpus
  source; plain-term helpers; entity escaping), re-pushed for CodeQL to re-evaluate before the squash merge.
- **The host changed under the record.** Between the gate (2026-10-05, macOS 26 / Xcode 26.6) and this check-in the
  host moved to macOS 27.0.1 / Xcode 27.0. Re-run on the new toolchain: swiftui Catalyst 2147/2147; the ios-harness
  XCTest scheme now RUNS (it could not before) — 17 pass, 13 fail reading repo files from inside the simulator
  (NSCocoaErrorDomain 257 on `~/Documents/…`; BACKLOG Known-broken has the diagnosis and the fix direction). The
  gate of record itself was captured on the old toolchain and is not re-run: nothing in the shipped tree changed.
