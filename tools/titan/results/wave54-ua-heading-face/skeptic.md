# wave 54 · L5 ua-heading-face — SKEPTIC report

Skeptic of builder lane L5 (PLAN `tools/titan/results/wave54-plan/PLAN.md` "### L5 · ua-heading-face", brief
`wave54-plan/ua-heading-face.md`, lane note `_note.md`). Shared tree, HEAD db6e8aa0. Every claim below comes from an
executed repro. Scripts and outputs are under `tools/titan/results/wave54-ua-heading-face/skeptic/`. Nothing was
committed. No seam file was left modified (sha256 below). No device, Chromium, simulator, emulator, gate or full suite
was run.

## Verdict: MIXED — the mechanism and its pins hold; the folded-host predictions do not (must-fix before landing)

- **Holds:**
  - the Compose twin, both gates, the verbatim pins and every mutation, replayed;
  - the census (carriers android 10 · ios 3 · web 0 · wire 0, re-derived by my own script and a JVM fold probe);
  - both seam patches (clean on HEAD and in §4 order, pins green with each applied);
  - both revert units (each compiles and passes alone on an export of HEAD);
  - the fixture net (0 `_tag` in all 9 gate fixtures and in fixtures/{properties,components,fidelity,combinations});
  - every quoted score (cells.mjs, wave53-final == wave54-open);
  - the geometry self-test (exit 0, HOLDS).
- **Fails:**
  - the six MED / MED-LOW folded-host rows (`text-decoration-inset-005/-006/-014`, ios + android). They rest on
    anchors (inset-001…004) whose `<h1>` carries an extractor-baked `MarginTop 21.44`. The targets' `<h1>` carries
    only `Position ABSOLUTE`, and no native path gives an abspos `<h1>` its UA `.67em` margin.
  - A calibrated replay with the campaign scorer predicts all six cells **FALL** (Δ −0.011 … −0.017) once the face
    lands. All six are `direction: up-or-stay`, so PLAN revert rule 2 reverts **U1-android** at the probe, and takes
    the HIGH `block-in-inline-015-print` android flip with it. It also reverts **U2-ios**, whose only carriers are
    these three documents.
  - The 005/006/014 geometry rule cannot see the residual: the ref lifted 21 px prints `GEOMETRY OK`.

## Repros (executed), with outputs

1. **Verbatim wire.** `skeptic/verify-wire.py` (own regex over both test sources) → `verify-wire.out.txt`: all 7
   Kotlin and all 7 Swift constants are byte-identical to the per-test IR in wave53-final AND wave54-open (`RESULT
   ALL-IDENTICAL`).
2. **JVM baseline, unseamed tree:** UAElementFontRuleTest 11/0, UAHeadingFoldGateTest 9/0, InlineRunFoldTest 45/0
   (fresh XML timestamps, `jvm-baseline.log`).
3. **Kotlin mutations.** `skeptic/mutate.py` is my own driver: it clears the result XML before each run, so a compile
   failure cannot read as red or green. Output: `kt-mutations.out.txt`. Every row went red → restore → BYTE-EXACT
   (aaceeebf… / e0e3186d…) → green 11/0 · 9/0.

   | # | mutation | red pin(s) |
   |---|---|---|
   | M1 (lane) | drop the own-size guard | inset-001 SAME list (mutated sha256 b6e163be = the lane's) |
   | M2 (lane) | base 16 | inset-014 `expected 26.0 was 32.0` (268d2139 = the lane's) |
   | M3 (lane) | old hasChildren gate | gate 005/006/014 + rule 005/006, 014 (5) (b6c24f56 = the lane's) |
   | M4 (lane) | weight 700.0 | 5, incl. `TextStyleApplier paints` Bold → null (005085f9 = the lane's) |
   | M5 (lane) | UA step writes Color | soundness: 005 Folded → Bailed (dc8d6a6a = the lane's) |
   | K6 | sup/sub half gated by standsDown | `…never the sup half` |
   | K7 | relative-host mirror off | `a relative heading host stands down…` |
   | K8 | drop the own-weight guard | inset-001 SAME list |
   | K9 | append instead of substitute | `inherited size substituted in place` (expected 30, was 20) |
   | K10 | h3 1.17 → 1.2 | text-decoration-color h3 (expected 18.72, was 19.2) |
   | K11 | no inherited em rung | `inherited size…` (expected 30, was 24) |
   | K12 | weight half ignores the gate | inset-011 SAME list + the synthetic standsDown pin |

4. **Catalyst baseline:** `Executed 25 tests, with 0 failures` (18 UAElementFontRuleTests + 7 UAHeadingFoldGateTests,
   per-class from the log).
   - Mutations (`swift-mutations.out.txt`). Each went red → restore → BYTE-EXACT (2ca760a6… / 97146660…) → `Executed
     25 tests, with 0 failures`.

   | # | mutation | red |
   |---|---|---|
   | M3s (lane) | old hasChildren gate | 5 failures: testInset005/006/014 (7e189b26 = the lane's) |
   | M5s (lane) | UA step writes Color | 3 failures: testFoldVerdictIsInvariantUnderTheUAFace (62b01672 = the lane's) |
   | S6 | fold on an empty container list | 5 failures: testInset005/006/014 (the inherited ink admits the fold) |
   | S7 | gate never stands down | testInset011BailingHostStandsDown (the control pin) |

5. **seam-1 under the lock** (`seam-1.verify.out.txt`).
   - Lock acquired, sha256 da2df0b4 → patched 0cef8ca1 (= the lane's).
   - The compiled `ComponentRenderer.class` was rebuilt inside the window (20:45:21). It holds 2 `UAHeadingFoldGate`
     refs and 2 `typography/UAElementFontRule` refs.
   - Pins 11/0 · 9/0 · 45/0. Restored da2df0b4 BYTE-EXACT-HEAD; lock released.
6. **seam-2 under the lock** (`seam-2.verify.out.txt`).
   - Lock acquired 20:58:48, sha256 905d1669 → patched 0ba22748 (= the lane's).
   - The Catalyst `Debug-maccatalyst/…/ComponentRenderer.o` was rebuilt at 20:59:22, inside the window, with 1
     `UAHeadingFoldGate` ref. (xcodebuild prints no per-file compile line, so the object file is the evidence.)
   - `Executed 25 tests, with 0 failures`. Restored 905d1669 BYTE-EXACT-HEAD; lock released 21:00:26.
7. **Landing order.** A scratch repo built from HEAD's two seam files applies, in §4 order: L2 seam-1 → L5 seam-1 →
   L4 seam-1 → L3 seam-1 (.kt), then L5 seam-2 → L3 seam-2 (.swift). All six applied. `git apply --check` of both
   L5 patches on the tree is clean.
8. **Revert units stand alone** (`export-units.sh`: `git archive HEAD` export trees in the session scratchpad; no
   worktree, nothing in the shared tree touched).
   - EX-A = HEAD + the six U1-android files + seam-1 → 11/0 · 9/0 · 45/0, compiled renderer gate-refs 2
     (`export-A.out.txt`).
   - EX-B = HEAD + the four U2-ios files + seam-2 → `Executed 25 tests, with 0 failures` (`export-B.out.txt`).
   - So neither unit depends on another lane's in-flight edit, and rule 2b holds: the unseamed shared tree compiles
     too (repros 2 and 4).
9. **Census, my own** (`census.py`; structural, from the rule's contract and slot.parent trees) →
   `census.wave53-final.out.txt` (= wave54-open's).
   - 1435 documents, 43 tagged components in 24 documents, 31 firing before the gate (30 after inset-011 stands down).
   - Exactly four heading hosts have children: inset-005/-006/-011/-014. So the iOS flag can flip only there, and it
     flips on 3 of them.
   - JVM fold probe on Compose (`fold-probe.out.txt`; a temporary class appended to the L5-owned test file, restored
     BYTE-EXACT 0d99cd9a):
     - both subelements-002 outer hosts `Folded("Einstein said that e = mc2.")` / `Folded("Is an + bn = cn …")`, so
       all four sups are consumed (must-not-move is safe);
     - the subelements-003 `p` host `Bailed(member-prop:TextDecorationColor-divergence)`, so its sup renders and
       moves;
     - inset-005/-006/-014 `Folded`, inset-011 `Bailed`;
     - the text-decoration-color sub/sup are leaves of a runs-less span, so they render.
   - **Carriers android 10 · ios 3 · web 0, wire 0.** This equals `expectations.json` captureCarriers, wireCarriers
     and both revertUnits exactly. No under-report.
   - All 13 carrier cells are f today, so revert rule 1 cannot fire on them.
10. **Scores.** `cells.mjs` reproduces every number the lane and brief quote, and wave54-open is identical on all of
    them.
11. **Geometry gate.** `geometry-gate.py wave53-final --base wave53-open --lanes L5 --self-test` → exit 0,
    `self-test: HOLDS`: gating 1 FAIL, control 8 PASS, report 6 FAIL (`geometry-gate.self-test.out.txt`).
12. **PNGs looked at** (`skeptic/png/*.sbs.png`, ref | web | ios | android).
    - block-in-inline android: four 16 px regular lines.
    - inset-005 natives: one folded 16 px line (sup raised, sub lowered) where the ref wraps "fox".
    - inset-014: half-size two-line wrap.
    - subelements-002 android: folded, sups inline. subelements-003 android: stacked.
    - inset-001 anchor: one line, which fits at the 2em face.
    - All as the lane described.
13. **The margin evidence** (`margin-evidence.py`; first heading band top, y ≥ 100).

    | test | `<h1>` wire | ref | ios | android |
    |---|---|---|---|---|
    | inset-001 | baked `MarginTop 21.44` | 115 | 117 | 116 |
    | inset-005 | `Position ABSOLUTE` only | 134 | 109 | 107 |
    | inset-006 | `Position ABSOLUTE` only | 134 | 109 | 107 |
    | inset-014 | `Position ABSOLUTE` only | 129 | 111 | 111 |

    - The 25–27 px gap is ≈ the missing `.67em` × 32 = 21.44 plus the 16→32 px glyph offset. The 18 px gap on 014 is
      ≈ `.67` × 26 = 17.4 plus ~1.
    - Code agrees on Compose: an abspos child renders through `RenderAbsoluteChild` → `absposOverflowMeasure`
      (`Constraints()`, unbounded) with no `LocalCollapsedMargin` provided. UA heading margins reach a box only
      through the block-flow collapse plan or the harness root stack.
14. **Score replay with the campaign scorer** (`score.mjs` / `synth-sensitivity.mjs`, `diffWebVsRef`).
    - Calibration: today's captures re-score exactly to 0.9 / 0.8995 / 0.8993 / 0.8987 / 0.923 / 0.9238.
    - Method: the frozen ref's own 2em-bold heading rows are pasted into TODAY's native capture at a lift L (L = 0 is
      "UA margin present").

    | cell | today | L 0 | L 12 | L 18 | L 21 | L 24 |
    |---|---|---|---|---|---|---|
    | inset-005 android | 0.9 | 0.9959 | 0.9064 | 0.8937 | 0.8865 (Δ−0.0135) | 0.8803 |
    | inset-005 ios | 0.8995 | 0.9976 | 0.9081 | 0.8954 | 0.8882 (Δ−0.0113) | 0.882 |
    | inset-006 android | 0.8993 | 0.9959 | 0.9062 | 0.8932 | 0.8861 (Δ−0.0132) | 0.8797 |
    | inset-006 ios | 0.8987 | 0.9977 | 0.908 | 0.895 | 0.8879 (Δ−0.0108) | 0.8815 |
    | inset-014 android | 0.923 | 0.9968 | 0.9221 | 0.909 (Δ−0.014) | 0.9062 | 0.9043 |
    | inset-014 ios | 0.9238 | 0.998 | 0.9234 | 0.9103 (Δ−0.0135) | 0.9074 | 0.9056 |

    - The no-wrap variant (Compose's unbounded measure) is no better: inset-005 android 0.8834 (Δ−0.0166),
      `synth-after.out.txt`.
    - It is a replay ESTIMATE of the sign: Chromium glyphs pasted into a native frame. Native glyph differences can
      only lower the rows with a lift, not raise them.
15. **The probe's blind spot** (`probe-teeth.py`, which runs the probe's own `bands` / `verdict_inset`): the inset-005,
    -006 and -014 refs lifted 10 / 21 / 24 px all print `GEOMETRY OK`.
16. **`all` keywords** (`allreset-probe.out.txt`; temporary class in the owned test file, restored BYTE-EXACT
    e17c28ff). With a 20 px parent, `AllReset.apply` → `UAElementFontRule.apply("h1", …)` gives:
    - `all: initial` → 32 px bold (CSS: 16 px, normal weight);
    - `all: unset` and `all: inherit` → 40 px bold (CSS: 20 px, inherited weight);
    - only `all: revert` (40 px bold) is right.
17. **The seam is not pinned.** Every L5 pin is green on the UNSEAMED tree (repro 2) and green with the seam
    (repro 5), so no pin can tell whether, or how, the seam is wired.

## Defects (ranked)

1. **MUST-FIX — the folded-host face is predicted to make its six target cells worse, and its revert takes the HIGH
   flip with it.**
   - Evidence: repros 13 and 14, plus the wire (inset-001's `<h1>` has `MarginTop 21.44`, inset-005's has only
     `Position`).
   - The lane named only the wrap risk ("stay f"). Even with a perfect wrap (L 21), every row falls by more than the
     0.002 of revert rule 2.
   - Fix, before land-units:
     - take the PLAN's own GO-SMALL fallback for this wave. U1-android ships with the heading half for LEAF headings
       only (the gate keeps the old has-children stand-down). U2-ios and the folded-host gate are held back;
     - OR split U1-android into U1a (rule + seam, leaf-only gate) and U1b (UAHeadingFoldGate). U1b and U2-ios are
       re-registered with the replay's predicted fall (or withdrawn), so a fall can never revert U1a's HIGH
       block-in-inline flip;
     - queue "UA `.67em`-family margin for a positioned heading on both natives" (spacing/, not L5's ownership) as
       the prerequisite of the folded-host face.
2. **SHOULD-FIX — the 005/006/014 geometry rule has no band-top check** (repro 15). A face-right, wrap-right picture
   sitting 21 px high prints `GEOMETRY OK`, so a DEGENERATE gain would be labelled geometry-correct. Add the ±3 px
   band-top test that fix-r1 M2 gave `verdict_015` (`wave54-plan/ua-heading-face.geometry.py`, the orchestrator's
   file).
3. **SHOULD-FIX — honesty: the seam itself is unverified** (repro 17).
   - The pins replay seam-1/seam-2 through `UAHeadingFaceReplay`, a copy of the call.
   - A seam that ran the step after DynamicValueResolver, passed `standsDown = false`, or passed the wrong list would
     keep every pin green. Only "the class references the symbol" checks it.
   - The note's "What I could NOT verify" should say so. A source-text tripwire riding inside the seam patch would
     close it.
4. **SHOULD-FIX — ownership.** `runtimes/compose/src/test/…/typography/UAHeadingFaceReplay.kt` and
   `UAHeadingFaceWire.kt` are outside PLAN §2 L5 `own:`. R2-N8 named "these two files only, not the directory". They
   collide with no other lane and are listed in U1-android. The orchestrator should ratify them (or the lane should
   fold them into the owned test files) before land-units.
5. **NIT — census magnitude.** The lane's census gives the subelements-003 sup as "→ 13.33".
   - The replay never resolves the ancestor `font-size: 2em`. The renderer publishes the resolved 32 px, so on device
     the sup goes 32 → 26.67 (repro 9: the probe prints the unresolved em on 265/266).
   - The replay's premises assert "no var()" but not "no unresolved em/% FontSize on the path". Carrier membership is
     unaffected.
6. **NIT (latent; shared with the wave-40 Swift twin) — `all: initial|unset|inherit` on a heading gets the UA face**
   (repro 16). 0 of the 43 tagged corpus components carries `All`. Name it as a KNOWN GAP in both banners, or pass
   the governing `all` keyword into the rule.
7. **NIT (latent) — Blink's monospace 13/16 scaling** of an em-sized heading under an inherited px size (stated in
   `UaBlockMarginFontBasis.kt`) is not modelled by either twin's em base. The inherited-px rung wins. 0 corpus
   carriers; not in the Kotlin KNOWN GAP list.

Checked and clean:
- No silent fallthrough: a stand-down is the fold's logged refusal.
- New logic files: UAElementFontRule.kt 198, UAHeadingFoldGate.kt 105, UAHeadingFoldGate.swift 85 lines.
  UAElementFontRule.swift is 293 lines (< 300).
- The uncommented lines are continuations of a commented statement.
- No probe / scratchpad / `/tmp` leftovers in owned files or the note.
- No lock dir left behind; the seam files are clean at HEAD's sha256.
- No edits outside ownership apart from defect 4.

## What I could NOT check

- Any device picture: the face on Android, the abspos wrap on either native, the post-change line position, and the
  real Δ on the six folded-host cells. Repro 14 is a replay estimate, labelled as such; it is not a capture.
- That iOS supplies no UA margin to a nested abspos `<h1>` AFTER U2-ios. Today's iOS band (y109, the same as
  Android's) says it supplies none at the 16 px face. I did not read UABlockMargin.swift's reach.
- inset-014's `16ch` re-resolution at 26 px: the measured `ChUnitMetrics` path exists, but I did not execute it.
- The orchestrator's single-writer full sweeps.

TREES: /Users/dranak/Documents/Projects/Style-Converter/.claude/worktrees/trusting-bohr-bd6fbf (focused Gradle in
`apps/android-harness`, focused Catalyst at the root; both seams applied and restored only under the lock). There
were also two throwaway `git archive HEAD` export trees, `ex-A` (Gradle) and `ex-B` (xcodebuild,
`-derivedDataPath ex-B-dd`), in this session's scratchpad. They are rebuilt by `skeptic/export-units.sh <dir> A|B`;
every output they produced is under `skeptic/`.
