# wave 54 · L5 ua-heading-face — lane note

Builder lane L5 of wave 54 (PLAN `tools/titan/results/wave54-plan/PLAN.md` "### L5 · ua-heading-face", brief
`wave54-plan/ua-heading-face.md`), then its FIX PASS (skeptic `skeptic.md`). Shared tree; HEAD at the fix pass
029139fd (= db6e8aa0 + the orchestrator's plan fix passes; every L5-relevant file is byte-identical to db6e8aa0).
Nothing committed; no seam file left modified (sha256 below); no device, Chromium, simulator, emulator, gate or full
suite was run.

**The lane now ships GO-SMALL** (PLAN §2 L5's own fallback, taken at the fix pass — "## Fix pass" at the end):
**U1-android only, leaf headings + sub/sup on Compose.** The fold-aware heading gate (`UAHeadingFoldGate.{kt,swift}`,
the folded-host half of U1-android, and all of U2-ios) is **HELD** under `held/`, because the folded inset-005/-006/-014
`<h1>`s are abspos, no native gives an abspos heading its UA `.67em` margin, and the calibrated replay scores all six
native cells LOWER with the face alone. The builder pass's full note is kept verbatim as `held/_note.builder-pass.md.txt`.

## What changed and why

**The defect (wave53-final, looked at — `skeptic/png/block-in-inline-015-print.sbs.png`):** Android paints the four
author-unsized `<h1>`s of `css-break/block-in-inline-015-print` at the 16 px regular body face (bands 9–12 px, right
edges x42–53) where the ref paints the UA `2em bold` face (18–25 px, x72–96); iOS passes the same IR at P 0.9894 because
it has `UAElementFontRule.swift` (wave 40). Compose had no tag-keyed font step: `ComponentRenderer.kt` :1141 ran only
`ListStyleUaRule.apply`.

**U1-android (Compose), as shipped:**
- NEW `runtimes/compose/src/main/java/com/styleconverter/runtime/typography/UAElementFontRule.kt` (199 lines) —
  `UAElementFontRule.apply(sourceTag, own, merged, standsDown)`: h1…h6 `2/1.5/1.17/1/.83/.67` + bold, sub/sup `1/1.2`;
  em base `DynamicValueResolver.fontSizePxOf(merged) ?: MonospaceUAFontSize.resolveSp(merged) ?: 16`; the author's own
  FontSize/Font/FontWeight wins (css-cascade-4 §6.1); in-place substitution; SAME list instance when nothing fires;
  FontWeight emitted as the INTEGER `{"weight":700}` (ValueExtractors reads `intOrNull`).
  - `UAElementFontRule.headingStandsDown(component)` (fix pass) = `!component.children.isNullOrEmpty()` — the iOS
    call site's `hasElementChildren`, verbatim, so both natives face the same headings (twin parity).
- seam-1 (`seam-1.patch`, regenerated): hoists the ListStyleUaRule result into `listUaProperties` and wraps it in
  `UAElementFontRule.apply(component._tag, allReset.own, listUaProperties, standsDown =
  UAElementFontRule.headingStandsDown(component))`, BEFORE DynamicValueResolver (:1211); it also CREATES the
  patch-borne source pin `runtimes/compose/src/test/java/com/styleconverter/runtime/typography/UAElementFontRuleSeamWiringTest.kt`.
- `runtimes/swiftui/Sources/StyleConverterRuntime/StyleEngine/typography/UAElementFontRule.swift`: DOCS ONLY (verified:
  `git diff -U0` has no non-comment line) — TWIN STATUS re-trued to PORTED (same gate); `headingAppliesTo` records the
  wave-54 re-measurement and why the narrowing is held; KNOWN GAPS gains the `all: initial|unset|inherit` line.
- `UAElementFontRuleTest.kt` (243 lines; the replay `UAHeadingFaceReplay` and the verbatim wire `UAHeadingFaceWire`
  are folded in as `internal object`s, the wire spliced by `gen-wire-constants.py --splice-kt`).

**Held (not in the tree; restorable from `held/`, SHA256SUMS.txt):** `UAHeadingFoldGate.kt` + `UAHeadingFoldGateTest.kt`
+ `UAHeadingFaceReplay.kt` + `UAHeadingFaceWire.kt` (Compose), `UAHeadingFoldGate.swift` + `UAHeadingFoldGateTests.swift`
(Swift), the fold-gate versions of `UAElementFontRule.kt`, `UAElementFontRuleTest.kt`, the Swift docs/test diffs, and
`seam-1.foldgate.patch` / `seam-2.foldgate.patch`. Their builder-pass verification (fold verdicts on verbatim wire,
soundness pins, mutations M3/M3s/M5s, Catalyst 25/0) stays valid as a record for the wave that lands the margin first.

## Revert unit U1-android (one commit)

Paths:
- `runtimes/compose/src/main/java/com/styleconverter/runtime/typography/UAElementFontRule.kt` (new)
- `runtimes/compose/src/test/java/com/styleconverter/runtime/typography/UAElementFontRuleTest.kt` (new)
- `runtimes/compose/src/test/java/com/styleconverter/runtime/typography/UAElementFontRuleSeamWiringTest.kt` (new,
  CREATED by `seam-1.patch` — not in the tree before landing)
- `runtimes/compose/src/main/java/com/styleconverter/runtime/core/renderer/ComponentRenderer.kt` ← `seam-1.patch`
- `runtimes/swiftui/Sources/StyleConverterRuntime/StyleEngine/typography/UAElementFontRule.swift` (docs only; its
  TWIN STATUS is true iff this unit is on the tree, so it reverts with it)
- the lane dir `tools/titan/results/wave54-ua-heading-face/` (as land-units.sh already does)

No longer in this unit (removed from the tree at the fix pass): `UAHeadingFoldGate.kt`, `UAHeadingFoldGateTest.kt`,
`UAHeadingFaceReplay.kt`, `UAHeadingFaceWire.kt` — `land-units.sh`'s current L5 block names them and would stop at
`git add` (hand-off `hunk-for-plan-3.patch`).

## Revert unit U2-ios — HELD (no commit this wave)

No path. `UAElementFontRuleTests.swift` is byte-identical to HEAD again; `seam-2.patch` is withdrawn (kept as
`held/seam-2.foldgate.patch`; `integrate-seams.sh` skips a registered slot with no patch, with a note). The orchestrator
removes the unit from `plan-build.py` / `land-units.sh` / PLAN (hand-offs below).

## Seam patches (hand-off; PLAN §3)

| patch | seam file (sha256 at base, = db6e8aa0 = 7cce3b22) | anchor | unit |
|---|---|---|---|
| `seam-1.patch` (sha256 c3bc75a0…, regenerated by `seam1-build.py`) | `ComponentRenderer.kt` da2df0b4cca689ad30943213a5e10253881073b7287c45eabf42cb743e1d04db | :1141 `val rawProperties = …ListStyleUaRule.apply(` | U1-android |
| ~~`seam-2.patch`~~ HELD (`held/seam-2.foldgate.patch`) | `ComponentRenderer.swift` 905d1669… | :343-356 | (U2-ios, held) |

- `git apply --check` clean on the tree. Landing-order dry run (scratch git repo from HEAD's `.kt`, the current patches
  of the other lanes): L2 seam-1 → **L5 seam-1** → L4 seam-1 → L3 seam-1 all applied, the L5 call present once
  (`fix/landing-order-dry-run.out.txt`). No other lane's patch touches the tripwire's anchors (`rawProperties`,
  `unresolvedProperties`, the resolver call).
- Verified under the per-file lock (`fix/fixrun.py --seam`: mkdir lock → apply → focused suite → seam restored from HEAD
  + the patch-borne test file deleted → rmdir; `fix/runs/F1-seam1-verify.out.txt`): patched sha256 c6a83ecd…;
  UAElementFontRuleSeamWiringTest 2/0, UAElementFontRuleTest 10/0, InlineRunFoldTest 45/0; the compiled
  `ComponentRenderer.class` holds `headingStandsDown` ×1, `typography/UAElementFontRule` ×2, `UAHeadingFoldGate` ×0;
  restored da2df0b4… **BYTE-EXACT-HEAD**, patch-borne test removed, lock released.
- Rule 2b: no owned file imports a seam-only symbol; the unseamed shared tree compiles and passes (F0: 10/0, 45/0).
- Unit stands alone: `fix/export-unit.sh` (git archive HEAD export tree in the session scratchpad) = HEAD +
  `UAElementFontRule.kt` + `UAElementFontRuleTest.kt` + seam-1 → 2/0 · 10/0 · 45/0, compiled renderer
  `headingStandsDown` ×1 (`fix/runs/export-U1.out.txt`).

## Pins (verbatim wire) and executed mutations

Verbatim payloads: `tools/titan/runs/wave53-final/sections/*/per-test-ir/`, byte-identical in `wave54-open`
(`fix/verify-wire.fix.out.txt`: the skeptic's own regex verifier, pointed at the new file → 7/7 constants IDENTICAL in
both runs; `gen-wire-constants.py --check-kt` → IDENTICAL-TO-GENERATOR). The replay asserts its premises per node (no
`All`, no buckets, no `var()`).

**JVM** (`fix/fixrun.py`: result XML cleared before every run; `--tests '*UAElementFontRule*' --tests
'*UAHeadingFoldGate*' --tests '*InlineRunFoldTest'`): unseamed UAElementFontRuleTest **10/0**, InlineRunFoldTest 45/0;
seamed + UAElementFontRuleSeamWiringTest **2/0**.
- UAElementFontRuleTest: the four verbatim block-in-inline leaf h1s → 32 / 700, one entry each, `headingStandsDown`
  false; TextStyleApplier paints 32.sp Bold; inset-001 (own 32/700) → SAME instance; **inset-005/-006/-014 hosts and
  the inset-011 control → `headingStandsDown` true and the SAME instance** (GO-SMALL: byte-identical on Android);
  inset-014's 2em on the monospace base → 26 (the rule with the gate forced open); text-decoration-color sup → 13.333,
  no weight, h3 → 18.72 / 700; the step writes ONLY FontSize / FontWeight (every other entry kept, in order);
  untagged → SAME instance; inherited 20 px → h2 30 in place; `standsDown` withholds the heading half, never sup.
- UAElementFontRuleSeamWiringTest (patch-borne): the ListStyleUaRule binding and the UA font step exist once each, in
  that order, with exactly `component._tag, allReset.own, listUaProperties, standsDown =
  …UAElementFontRule.headingStandsDown(component)`; the rule is called once in the renderer; `rawProperties` flows to
  `unresolvedProperties`, which the resolver reads after it.

**Catalyst** `-only-testing:StyleConverterRuntimeTests/UAElementFontRuleTests` → **Executed 16 tests, with 0 failures**
(`fix/runs/catalyst-UAElementFontRuleTests.final.log`, built from the final `UAElementFontRule.swift` sha256 27cd64a9…;
HEAD's 16 tests — the Swift test file is at HEAD bytes, sha256 58b3f1fa…).

**Executed mutations — fix pass** (`fix/fixrun.py`; one exact literal from `fix/literals/` or `mutations/literals/`;
red → restore → green; outputs `fix/runs/<name>.out.txt`, logs `.red.log` / `.green.log`):

| # | mechanism mutated | file sha256 before → mutated → after | red | green |
|---|---|---|---|---|
| G1 | `headingStandsDown` → false (the folded-host face back on) | UAElementFontRule.kt c78f3557… → fbe1cf3f… → c78f3557… BYTE-EXACT | `GO-SMALL - every heading host…` (inset-005 first) | 10/0 |
| G2 | `headingStandsDown` → true (every heading stands down) | c78f3557… → 5be9d073… → c78f3557… BYTE-EXACT | 3: block-in-inline leaf h1s, TextStyleApplier paints, text-decoration-color h3 | 10/0 |
| M1 | drop the own-size guard | c78f3557… → 7efb8f43… → c78f3557… BYTE-EXACT | `inset-001 … SAME list` | 10/0 |
| M2 | em base 16 instead of MonospaceUAFontSize | c78f3557… → 0579136a… → c78f3557… BYTE-EXACT | `inset-014 …` expected 26.0 was 32.0 | 10/0 |
| M4 | FontWeight emitted as 700.0 | c78f3557… → 2d08ca96… → c78f3557… BYTE-EXACT | 4 incl. `TextStyleApplier paints` (Bold → null) | 10/0 |
| M5 | the UA step also writes Color | c78f3557… → 06d05854… → c78f3557… BYTE-EXACT | `the step writes only FontSize and FontWeight` | 10/0 |
| MS1 | renderer at HEAD bytes, tripwire present (seam absent) | ComponentRenderer.kt (seamed) c6a83ecd… → **da2df0b4…** (= HEAD) → c6a83ecd… BYTE-EXACT | both seam-wiring tests | 2/0 · 10/0 |
| MS2 | seam passes `standsDown = false` | c6a83ecd… → 17eb8a03… → c6a83ecd… BYTE-EXACT | both seam-wiring tests | 2/0 · 10/0 |
| MS3 | seam feeds the merged list (ListStyleUaRule skipped) | c6a83ecd… → dd330b6e… → c6a83ecd… BYTE-EXACT | both seam-wiring tests | 2/0 · 10/0 |
| MS4 | resolver fed the pre-UA list | c6a83ecd… → c0bf5107… → c6a83ecd… BYTE-EXACT | `DynamicValueResolver resolves the UA step's output` | 2/0 · 10/0 |

MS1–MS4 ran with seam-1 applied inside the lock; after each, the seam file was restored to HEAD (da2df0b4… BYTE-EXACT-HEAD)
and the patch-borne test deleted before the lock was released. The rule pins are green under every MS mutation —
which is exactly skeptic finding 3: only the source tripwire sees the call site. The builder pass's mutations M3 / M3s /
M5s mutate held files and are void for the shipped unit (record: `mutations/`).

## Geometry probe

`python3 tools/titan/results/wave54-plan/geometry-gate.py wave53-final --base wave53-open --lanes L5 --self-test` →
**exit 0, `self-test: HOLDS`** (`fix/geometry-gate.self-test.committed.wave53-final.out.txt`): 15 keys — gating 1
(block-in-inline android) FAIL `band height 9 vs ref 18 (y23)`; control 8 PASS; report 6 FAIL (inset-005/-006/-014
natives). The same with `--exp fix/plan-build-dry/go-small/expectations.json` (the GO-SMALL restatement) → identical
verdicts, HOLDS (`fix/geometry-gate.self-test.go-small.wave53-final.out.txt`). Under GO-SMALL the six inset report rows
are no longer targets: they stay WRONG and their captures are must-not-move. Skeptic finding 2's band-top check is a
hunk for the probe's owner (`hunk-for-plan-1.patch`, below).

## Census (blast radius) — own method, GO-SMALL

`fix/census-go-small.kt.txt` — a temporary JUnit class appended to the owned `UAElementFontRuleTest.kt` for ONE run and
removed (sha256 4c9dd125… before = after, BYTE-EXACT, `fix/runs/census-go-small.restore.txt`). It walks every per-test IR
document through the production decoder + composer, replays seam-1 at every node (merge → ListStyleUaRule →
UAElementFontRule with `headingStandsDown`), and records per tagged component whether the step fires (`post !== pre`)
and whether the component is RENDERED (not consumed by an engaged fold of an ancestor). Outputs
`fix/census-go-small.wave53-final.out.txt` and `.wave54-open.out.txt` — identical apart from the run id: **1435
documents, 43 tagged components, 27 firing, 19 rendered-firing, iOS heading-flag flips 0**.
- **Android carriers (fires AND rendered): 7 documents** — block-in-inline-015-print (4 leaf h1 → 32/700),
  counter-list-item (h2 → 24/700), counter-reset-increment-overflow-underflow (6 h3 → 18.72/700), text-decoration-color
  (h3 18.72/700; sub, sup → 13.33), text-decoration-subelements-003 (stacked sup), backdrop-filter-border-radius-change /
  -corner-shape-change (own 100 px h1s → bold half only).
- **No longer carriers:** inset-005/-006/-014 — the h1 host stands down (SAME instance) and its sup/sub fire but are
  consumed by the host's engaged fold (as subelements-002's sups, already must-not-move): byte-identical.
- **iOS carriers: 0** (no Swift behaviour change). **Web: 0. Wire: 0.**
- Fixture net: no `_tag` in the 9 gate fixtures or under `fixtures/` (builder + skeptic census) → same instance →
  byte-identical.
- **Passing carrier cells today: 0** — all 7 android carrier cells are f in wave53-final == wave54-open
  (`fix/carrier-cells.go-small.out.txt`), so revert rule 1 cannot fire on them.
- Cross-check: the GO-SMALL restatement of `plan-build.py` (`hunk-for-plan-2.patch`, dry run) gives L5 carriers
  `[web 0, ios 0, android 7]` — the same 7 stems.
- Census magnitude caveat (skeptic NIT 5, not changed): the replay does not resolve an ancestor's `em` FontSize, so
  subelements-003's sup reads "→ 13.33" where the device gets 32 → 26.67. Membership is unaffected; that row is
  undirected.

## Predictions (against wave54-open = wave53-final), GO-SMALL

| cell | from → predicted | confidence | gate | unit |
|---|---|---|---|---|
| css-break/block-in-inline-015-print android | f 0.9489 → **P ≈0.989** (iOS gives P 0.9894 with the identical rule on the same IR) | HIGH | floor 0.97 + geometry gating | U1-android |
| text-decoration-color android | f 0.6164 → ≈0.67 (iOS with the rule: f 0.674), stays f | LOW, directed | — | U1-android |
| counter-list-item, counter-reset-increment-overflow-underflow, subelements-003, backdrop-filter-border-radius-change, -corner-shape-change android | move, stay f | LOW, undirected | — | U1-android |

**Withdrawn (GO-SMALL):** the six inset-005/-006/-014 ios / android rows (MED / MED-LOW). With the face alone they were
predicted to FALL (`skeptic/synth-sensitivity.out.txt`, re-run byte-identical in `fix/synth-sensitivity.rerun.out.txt`:
at lift 21 inset-005 android 0.8865, ios 0.8882, inset-006 android 0.8861, ios 0.8879; at lift 18 inset-014 android
0.909, ios 0.9103 — a replay estimate, not a capture); they are now must-not-move.

## Must not move (52 cells; the GO-SMALL dry run's `mustNotMove`)

The builder's 46 — block-in-inline-015-print ios; inset-011 ios / android / web; inset-001/002/003/004/007/008/009/012/
013/015/016/024 ios + android; subelements-002 ios / android / web; the iOS and web cells of counter-list-item,
counter-reset-increment-overflow-underflow, text-decoration-color, subelements-003 and the two backdrop-filter
documents; inset-005/-006/-014 web — **plus the six inset-005/-006/-014 ios + android cells** (stand-down → same
instance on Android; no Swift change on iOS). `block-in-inline-015-print` web stays L6's carrier.

## Hand-offs

- **Seam:** `seam-1.patch` (U1-android; regenerated — the builder's version is `held/seam-1.foldgate.patch`).
  `seam-2.patch`: withdrawn (held).
- **`hunk-for-plan-1.patch`** → `tools/titan/results/wave54-plan/ua-heading-face.geometry.py` (skeptic finding 2): the
  ±3 px band-top check in `verdict_inset`, checked last. Verified without touching wave54-plan/
  (`fix/probe-band-top.py` → `fix/probe-band-top.out.txt`): on wave53-final the patched probe's output is
  **BYTE-IDENTICAL** to today's (so every verdict and the self-test are unchanged); the skeptic's lifted-ref fakes now
  print `GEOMETRY WRONG (band top y113 vs ref y134 …)` at lift 10/21/24 on all three tests and OK at lift 0.
- **`hunk-for-plan-2.patch`** → `wave54-plan/plan-build.py` (the GO-SMALL restatement: L5 carriers android 7 / ios 0,
  the six inset rows withdrawn and moved to must-not-move, U1-android's commit text, U2-ios → `heldUnits`). Dry run
  (`fix/plan-build-dry/`, HERE pinned to wave54-plan, `--out`): the UNPATCHED copy reproduces the committed
  `expectations.json` byte-for-byte (method check); the patched copy passes every assertion: union carriers web 44 ·
  iOS 24 · Android 48, revert units 18, MED/MED-LOW flips iOS 6 → 3 and Android 4 → 1 (ceiling web 1241 · iOS 1131 ·
  Android 1122), L5 carriers `[0, 0, 7]`, predictions 7, mustNotMove 52. Then regenerate + re-run `watchlist-check.mjs`.
- **`hunk-for-plan-3.patch`** → `wave54-plan/land-units.sh` (L5 block): one commit, the paths of the U1-android section
  above; the U2-ios block removed. `bash -n` clean on the patched copy.
- **PLAN.md text to restate (orchestrator; no patch — PLAN is under active edit):** §1 L5 row (targets: block-in-inline
  android + the movers; units U1-android; "19 revert units" → 18; expected-movement ceiling iOS 1134 → 1131, Android
  1125 → 1122); §2 L5 Change / own (no UAHeadingFoldGate files delivered) / Seams (seam-1 only) / Unit pins /
  Predictions (7 rows) / Must not move (52) / Carrier set (android 7, ios 0); §3 row "L5 · U1-android" →
  `standsDown = UAElementFontRule.headingStandsDown(component)` + the patch-borne seam-wiring test, row "L5 · U2-ios" →
  HELD, totals 9 → 8 patches and `.swift` 2 → 1 hunks; §4 step 5 → "L5 U1-android (+ seam-1 kt)"; §6 L5 probe row (the
  U2-ios requirement and the inset report rows go; the six inset native cells are must-not-move) and R4's union set
  (iOS 27 → 24, Android 51 → 48).
- **Queue (BACKLOG, docs pass):** NEW item "UA `.67em`-family margin for a positioned (abspos) heading on both natives"
  (spacing/, not L5's) as the prerequisite of the folded-host face; then restore `held/` and re-run the replay
  (`skeptic/synth-sensitivity.mjs`) before re-registering inset-005/-006/-014. BACKLOG 0(g): the Compose twin lands for
  leaf headings; the 4(b′) sentence (brief §4 E: the decoration-colour refusal no longer limits 005/006/014; the
  residual is the face AND the abspos margin).
- **Sweep restamp (§4 step 5):** Compose `:runtime` +12 tests (UAElementFontRuleTest 10, UAElementFontRuleSeamWiringTest
  2, the latter only once seam-1 lands); SwiftUI +0. No new `*Applier` → coverage-audit unchanged.

## ORCHESTRATOR WINDOW REQUESTS

None (PLAN §2 L5 "Window requests: none"). Device evidence arrives with stage 2 (`wave54-probe`) and the closing gate:
`python3 tools/titan/results/wave54-plan/ua-heading-face.geometry.py <run>` must print block-in-inline android
`→ GEOMETRY OK` (gating U1-android, floor 0.97), keep the inset-011 rows OK, and the six inset-005/-006/-014 native
captures must be decoded-pixel identical to wave54-open (control-check, must-not-move).

## What I could NOT verify

- Any device picture: the 2em face on Android, every SSIM prediction above, and the byte-identity of the six inset
  native captures (argued from the census — same list instance on Android, no Swift change — not captured). Nothing
  here is labelled picture-correct.
- **The seam's runtime behaviour.** `RenderComponent` is a @Composable this module's JVM JUnit cannot execute. The rule
  pins go through `UAHeadingFaceReplay`, a COPY of the seam's cascade step (green with or without the seam: F0 vs F1).
  The call site is pinned only at SOURCE level by `UAElementFontRuleSeamWiringTest` (its four facts; MS1–MS4 prove it
  can fail). That a correct-looking call produces the right Compose paragraph is first executed on device.
- The skeptic's synthetic replay (the reason for GO-SMALL) is an estimate — Chromium glyphs pasted into native frames —
  and I re-ran it, not a capture. That iOS gives an abspos `<h1>` no UA margin rests on today's iOS band (y109 ≈
  android y107 at the 16 px face) and the wire (inset-001's baked `MarginTop 21.44`, absent on 005/006/014); I did not
  trace `UABlockMargin.swift`'s reach past `UABlockChildMargin.swift`'s in-flow collapse plan.
- Skeptic NIT 7 (Blink's 13/16 monospace scaling of an em heading under an inherited px size) is not modelled and not
  named in the Kotlin banner (199 lines; 0 corpus carriers).
- The orchestrator-owned hunks are verified on copies / dry runs only; they are not applied.
- Full suites (single-writer sweep is the orchestrator's).

## Fix pass

Defects from `skeptic.md` (each re-run first: `fix/margin-evidence.rerun.out.txt` and `fix/synth-sensitivity.rerun.out.txt`
are byte-identical to the skeptic's outputs; the wire shows `MarginTop 21.44` on inset-001's `<h1>` and only `Position
ABSOLUTE` on 005/006, `Position` + `FontFamily monospace` + `Width 16ch` on 014; LOOKED at
`fix/inset-005.ref-android-ios-lift21.sbs.png` — ref | android | ios | the face lifted 21 px: the lifted face sits
against the explanatory line where the ref leaves the `.67em` gap).

| # | skeptic defect | action |
|---|---|---|
| 1 | MUST-FIX — the folded-host face is predicted to make its six cells worse; their rule-2 revert would take the HIGH block-in-inline flip with it | **Fixed by taking the PLAN's GO-SMALL fallback (option a).** `UAElementFontRule.headingStandsDown` (= iOS's wave-40 gate) replaces `UAHeadingFoldGate` at the seam; the gate files, U2-ios and seam-2 are moved to `held/` (byte-identical copies, SHA256SUMS.txt); the six inset rows are withdrawn and become must-not-move; pins G1/G2 prove the stand-down can fail both ways. Option (b) (split U1a/U1b) was rejected: U1b would need a second, unregistered seam hunk, and its only predictions are falls. The margin prerequisite is queued (hand-offs). Orchestrator restatement: `hunk-for-plan-2.patch` (dry-run verified), `hunk-for-plan-3.patch`, the PLAN.md list. |
| 2 | should-fix — `verdict_inset` has no band-top check | **Hunk delivered** (`hunk-for-plan-1.patch`; the probe is the orchestrator's file): wave53-final output BYTE-IDENTICAL, the lifted fakes now WRONG, lift 0 OK (`fix/probe-band-top.out.txt`). Under GO-SMALL it guards the future folded-host face. |
| 3 | should-fix — the seam call site is unpinned | **Fixed:** the patch-borne `UAElementFontRuleSeamWiringTest` (in `seam-1.patch`, the L2 / L4 / wave-53 precedent) pins the call's exact arguments, its single occurrence, its order after ListStyleUaRule and before the resolver; MS1–MS4 executed red → green under the lock. Named under "What I could NOT verify" (behaviour is still first executed on device). |
| 4 | should-fix — `UAHeadingFaceReplay.kt` / `UAHeadingFaceWire.kt` outside `own:` | **Fixed:** folded into the owned `UAElementFontRuleTest.kt` (the wire spliced by `gen-wire-constants.py --splice-kt`, `--check-kt` IDENTICAL; the skeptic's verifier ALL-IDENTICAL in both runs); both files are gone from the tree. The test file is 243 lines (over the 200 target, under the ~300 split line: 7 verbatim documents). |
| 5 | NIT — census magnitude (subelements-003 sup) | Not changed; stated in the census section (membership unaffected, row undirected). |
| 6 | NIT — `all: initial|unset|inherit` gets the UA face | Named as a KNOWN GAP in both banners (Kotlin `UAElementFontRule.kt`, Swift `UAElementFontRule.swift`). 0 corpus carriers. |
| 7 | NIT — Blink's 13/16 monospace em scaling | Not changed (0 carriers); listed under "What I could NOT verify". |

Re-run after the fix: JVM focused suite green unseamed (F0) and seamed (F1); every affected mutation re-executed
(G1, G2, M1, M2, M4, M5, MS1–MS4 — table above); Catalyst UAElementFontRuleTests Executed 16, 0 failures; the unit
alone on a HEAD export tree green; geometry self-test HOLDS on the committed and the GO-SMALL expectations. Grep of the
owned sources for `probe` / `Probe` / `/tmp` / `scratchpad`: none.

Scripts kept: `seam1-build.py` (regenerates seam-1.patch), `seam1-files/`, `gen-wire-constants.py` (`--splice-kt` /
`--check-kt` added), `fix/fixrun.py` (focused run / seam / mutation driver), `fix/export-unit.sh`,
`fix/probe-band-top.py`, `fix/verify-wire.fix.py`, `fix/census-go-small.kt.txt`. The builder-pass scripts
(`mutate-compose.sh`, `mutate-swift.sh`, `seam-verify.sh`, `census-jvm.kt.txt`) reference held files and are a record.

TREES: /Users/dranak/Documents/Projects/Style-Converter/.claude/worktrees/trusting-bohr-bd6fbf (Gradle in its
`apps/android-harness`, Catalyst at its root; seam-1 applied and restored only under the lock); one throwaway
`git archive HEAD` export tree `ex-U1` in the session scratchpad (rebuilt by `fix/export-unit.sh <dir>`; every output
it produced is under `fix/runs/`).

STATUS: COMPLETE

## Re-verification

Re-verifier of the L5 fix pass (2026-10-08, HEAD 029139fd; every L5-relevant source byte-identical to db6e8aa0). Each
skeptic finding re-run with the skeptic's own check where it still applies, plus independent checks. Everything executed is
under `reverify/`. Nothing was committed. No seam file was written: seam-1 was applied and mutated only inside a
`git archive HEAD` export tree in the session scratchpad (`reverify/export-tree.sh <dir>`), so no lock was needed. No device,
Chromium, simulator, emulator, gate or full suite was run.

**Verdict: CLAIM-HOLDS.** The one must-fix is fixed by GO-SMALL. Should-fix 2, 3 and 4 are fixed (2 as a verified hunk for
the orchestrator). The two NITs left unchanged are named honestly.

| # | skeptic finding | re-run | result |
|---|---|---|---|
| 1 | MUST-FIX: the folded-host face lands on inset-005/-006/-014 (6 native cells predicted to FALL; their rule-2 revert takes the HIGH flip) | (a) The skeptic's fold probe, re-written for the shipped gate (`reverify/ReverifyGoSmallProbe.kt.txt`), runs in the export tree = HEAD + U1-android + seam-1, over all 1435 documents. It models the renderer's own fold gate at :3362-3375 (RELATIVE host, WPT float/atom segments, InlineRunPlan, `InlineRunFold.fold` with `mergedComponent`'s effective list, :1841). The carriers are derived separately (`reverify/derive-carriers.py`). (b) The skeptic's `margin-evidence.py` and `synth-sensitivity.mjs` re-run. (c) My own mutations of the stand-down and of the seam call. (d) Swift diff, seam-2, units and plan-build dry run. | **FIXED.** (a) The inset-005/-006/-014 `<h1>` hosts (and the inset-011 control) give `standsDown=True same=True`, so the face no longer reaches them. Their sup/sub fire but are consumed by the host's engaged fold (`…0-069=Folded`; the wave53-final android PNG shows the folded line). Android carriers: **7**, identical in wave53-final and wave54-open and equal to the lane's census: block-in-inline-015-print, counter-list-item, counter-reset-increment-overflow-underflow, text-decoration-color, subelements-003, backdrop-filter-border-radius-change / -corner-shape-change. 43 tagged, 27 firing, 19 rendered-firing, All 0 · var 0. (b) Both re-runs are byte-identical to the skeptic's outputs, so the GO-SMALL motive stands (lift 21: 0.8865 / 0.8882 / 0.8861 / 0.8879; inset-014 lift 18: 0.909 / 0.9103). (c) **RV-G1:** `headingStandsDown` → `… && component.runs.isNullOrEmpty()` (the face back on runs hosts), UAElementFontRule.kt c78f3557… → c0599b9a… → c78f3557… BYTE-EXACT. Red `GO-SMALL - every heading host…` (inset-005 first); green 10/0. **RV-MS:** the seam narrows its gate the same way, seamed renderer c6a83ecd… → 9d26bec0… → c6a83ecd… BYTE-EXACT. Both seam-wiring tests red; green 2/0 · 10/0 · 45/0. (d) The `UAElementFontRule.swift` diff has no non-comment line, `seam-2.patch` is absent, UAElementFontRuleTests.swift is at HEAD bytes, and no `UAHeadingFoldGate` symbol remains under `runtimes/`. The independent `plan-build.py` dry run (copies, HERE pinned, `--out` in the scratchpad) gives: unpatched == committed `expectations.json` and `watchlist.txt` byte-for-byte; patched with `hunk-for-plan-2` == the lane's go-small JSON. In it, L5 carriers are `[0, 0, 7]` and equal derivation (a); units `U1-android` (U2-ios in `heldUnits`); 7 predictions, none on inset-005/-006/-014; mustNotMove 52, including all six inset native cells (`reverify/plan-build-dry.check.out.txt`). |
| 2 | should-fix: `verdict_inset` has no band-top check | `hunk-for-plan-1.patch` applied to a scratch copy (== the lane's `fix/ua-heading-face.geometry.patched.py.txt`); `reverify/band-top-check.py` with my own lifted-ref fakes | **FIXED (hunk, orchestrator's file).** On wave53-final the patched probe's output is BYTE-IDENTICAL to the real probe's (20 lines, exit 0). Fakes lifted 4/10/17/21 px print WRONG `band top …` on all three tests and 0/2/3 px print OK. The unpatched probe prints OK on every one. |
| 3 | should-fix: the seam call is unpinned | the patch-borne `UAElementFontRuleSeamWiringTest` in the export tree; RV-MS above; `reverify/landing-order-dry-run.out.txt` | **FIXED.** 2/0 seamed. RV-MS goes red. The compiled export renderer holds `headingStandsDown` ×1, `typography/UAElementFontRule` ×2 and `UAHeadingFoldGate` ×0. With all four `.kt` seam hunks in §4 order (L2 → L5 → L4 → L3), the wiring test's four facts still hold (×1 each, in order). L5 seam-2 is skipped (absent); L3 seam-2 applies. |
| 4 | should-fix: Replay / Wire files outside `own:` | tree listing + own byte check of the folded wire | **FIXED.** Both files are gone. The 7 constants in the owned `UAElementFontRuleTest.kt` are byte-identical to wave53-final and wave54-open (`reverify/verify-wire.own.out.txt`; the lane verifier also gives ALL-IDENTICAL). |
| 5–7 | NITs | — | 5 and 7 are unchanged and named (census section / "could NOT verify"). 6 is named as a KNOWN GAP in both banners. |

**Standing checks.**
- Rule 2b: the UNSEAMED shared tree compiles and passes UAElementFontRuleTest 10/0 and InlineRunFoldTest 45/0
  (`reverify/R0-shared-unseamed.out.txt`, fresh XML).
- Catalyst `-only-testing:StyleConverterRuntimeTests/UAElementFontRuleTests` on the shared tree (private derived data):
  **Executed 16 tests, with 0 failures**. Inputs: UAElementFontRule.swift 27cd64a9…, the tests 58b3f1fa… (= HEAD).
- `git apply --check seam-1.patch` is clean. The seam file sha256 is da2df0b4… at 7cce3b22, db6e8aa0 and 029139fd. The
  three hunks for the plan apply clean, and their base sha256s match.
- `geometry-gate.py wave53-final --base wave53-open --lanes L5 --self-test` exits 0, `HOLDS` (gating 1 FAIL, control 8
  PASS, report 6 FAIL). It is byte-identical to the lane's output, and the same holds with the go-small expectations.
- The four seam files: `git status --short` is empty. `tools/titan/runs/wave54-lock/` is empty. The patch-borne test is
  absent from the shared tree. `held/SHA256SUMS.txt` all OK. The owned sources contain no probe, Probe, `/tmp`, scratchpad,
  census or temporary leftovers (`reverify/hygiene.out.txt`).
- PNGs looked at: block-in-inline-015-print sbs (android 16 px regular vs 2em bold), and `fix/inset-005.ref-android-ios-lift21.sbs.png`
  (the natives fold one 16 px line; the lifted face sits on the explanation line).

**Correction (my own):** my first probe run passed the host's OWN list to `InlineRunFold.fold`, so the inset h1 hosts read
`Bailed`. The renderer passes `mergedComponent`'s effective list (:1841). With that list they read `Folded`, as the skeptic
and builder said. The outputs were regenerated. The carrier set (7) is the same under both models.

**For the orchestrator (not lane defects).**
- Until `hunk-for-plan-2` is applied and `plan-build.py` is re-run (+ watchlist-check), the committed `expectations.json`
  still pre-registers L5 as android 10 · ios 3 with U2-ios and the six inset rows.
- Until `hunk-for-plan-3` lands, `land-units.sh` names deleted files (`UAHeadingFoldGate.kt`, …), so it stops at
  `git add`.
- The PLAN §1/§2/§3/§4/§6 restatement is listed under "Hand-offs".
- Stale comments remain after hunk-3: land-units.sh :9 and :14, and integrate-seams.sh :18 (L5 seam-2 / U2-ios).
- Residual risk under GO-SMALL: U1-android still carries one directed row, text-decoration-color android (LOW,
  up-or-stay). A fall ≥ 0.002 there would revert U1-android under rule 2. The iOS anchor (same rule, same IR, 0.674 vs
  0.6164) supports "up".

**Not verified here:** any device picture or score. The seam's runtime behaviour on device. That iOS gives an abspos
`<h1>` no UA margin (inferred from the PNG and the wire, as before).

TREES: /Users/dranak/Documents/Projects/Style-Converter/.claude/worktrees/trusting-bohr-bd6fbf (focused Gradle in
`apps/android-harness`, unseamed; Catalyst at the root with a private `-derivedDataPath` in the session scratchpad); one
throwaway `git archive HEAD` export tree `ex-RV` in the session scratchpad (rebuilt by `reverify/export-tree.sh <dir>`;
every output is under `reverify/`).

STATUS: COMPLETE
