# Wave 55 — ship notes for the docs pass and the next wave

Written by the pre-landing regeneration (`regen.done.md`, 2026-10-12). Each item names its source. Nothing here changes a
carrier, prediction, floor or unit of wave 55. The ship's docs pass turns the BACKLOG items into `docs/BACKLOG.md` rows, and
the closing PR body cites the bounded instrument claim (§2) instead of "class closed".

## 1. BACKLOG items to add at ship

1. **Delete `UAElementFontRule.headingStandsDown` (Compose).** Source: PLAN §10 item 7;
   `../wave55-ua-heading-margin/_note.md` "Decisions" 1.
   - The function and its kdoc are KEPT in wave 55 so that `unit-U2-android.patch` compiles without seam 3 (rule 2b).
   - The kept kdoc already says "removal is queued (BACKLOG)", and no such BACKLOG row exists yet. Add one.
   - After seam 3 lands, nothing in `runtimes/` calls the function (the renderer and the replay both call
     `UAHeadingFoldGate.standsDown`).
   - Delete it once seam 3 has landed and survived its stage-1 / stage-2 probes: one function and its kdoc, plus any test
     row that still names it (grep `headingStandsDown` under `runtimes/` first).
2. **The round-7 instrument classes MF1–MF7: open must-fixes for the next wave.** Source: PLAN §0 "Round 7" and §10 item 9;
   `plan-skeptic-round-7.verify.md` §3; fix bases in `plan-skeptic-round-7.verify.d/basis7*.out.txt` and `propose7.out.txt`.
   Wave 55 ships with these classes NAMED, not closed. No round 8 ran before the landing. Each class below gives the accepted
   fakes' changed-px span, the keys it reaches, and the verifier's measured tolerance-0 fix basis.

   | class | what passes | px span | keys | fix basis (measured) |
   |---|---|---|---|---|
   | MF1 | faint in-zone ink, min channel ≥ 200, on a neutral / ramp zone. The residual allows any grey / ramp colour there, and every probe's ink level starts at min < 200 | 4–2558 | 24 gating (L1 ×3, L4 ×12, L5 ×6, L3 dir rows ×3) + tally 001 / 003 ios, counter-suffix ×2, 005 ×2 | on 71 legitimate pictures all 172 598 changed non-white in-zone px lie ≤ 1 px from probe-level ink (min < 200). Rule: a changed non-white in-zone px > 1 px from probe-level ink is non-class. Closes 1063 / 1134; leaves the MF4 shifts and 59 4-px specks inside the 1-px AA ring (name or close) |
   | MF2 | L3 bidi: the class blue outside the four RTL bands (the `blue3` mask makes its own zone) | 4–144 | `[S] bidi-tab-001 android rtl-rows` (gating + tally) | restrict the zone to `blue3` ∩ the four bands (∪ the windows): the fixed U1U2 replay changes 0 blue px outside them; PLAIN still reads 506; closes all 22 |
   | MF3 | palette paint grown 1–4 px from a recorded window (one connected component, flat palette colours and blends allowed) | 6–1008 | clip, clip-2, clip-4, bidi (gating + tally) | declare each window's capture classes (closes clip-4, most of clip / clip-2, bidi black / grey); for the rest, record the window as the fixed change's pixel MASK, not its bbox + 1 (0 by construction on the fixed pictures; device pictures re-record through the D40 path) |
   | MF4 | an L4 face-present heading moved ±2–3 px in x (`X0_TOL` ±3; `face xor:` searches dx ±3) | 3350–4888 | 10 (`[M]` 005 / 006 / 014 ×2, `[F]` 005 / 006 / 014 ios, 014 android) | web's left edges equal the ref's (Δx0 0); the C arm reads 0. Set `X0_TOL` and the `face xor:` dx span from the measured Δx0 at stage 1, as D33 does for `X1_TOL`, or name the ±3 as a D33 residual with its reason |
   | MF5 | L6 `[X]`: child fill painted over the parent band (3–9 % of the border) | 4–138 | safe-001, grid align-self-001 / -rtl-001 / -vertWM-001, justify-self-001 (gating + tally) | count ORPHAN reveals: on all six U2 replays every revealed fill px belongs to a fill component that holds base fill (0 orphans); the band fakes have 40–162. The 4-px speck joined to a child stays: name it with the floor, or bound the reveal to the child's own box |
   | MF6 | L6 `[PO]`: 4 / 6 / 8 overlap px reverted to the base's blue, as many red px added elsewhere | 8–16 | `[PO] backdrop-filter-clip-rect android` (gating + tally) | the fixed U1 replay reads overlap red 416 / blue 0 exactly. Require blue ≤ 0 (or the 2-px floor) with red = 416 |
   | MF7 | L3 stack: an "other" square relocated 24–40 px (`others` counts green outside the cells and never asks where) | 200 | `[W] anchor-position-multicol-nested-001 android stack` | no tolerance-0 reference exists: the fixed U1 replay's other squares differ from the base and from web / iOS. Decide where they belong, for example the replay's own squares as a positional read ±1 |
   | floor | ≤ 2 px class-consistent paint inside a zone (D39) | ≤ 2 | 32 acceptances in round 7 (clip-3 faint +1 x copies; #66ccff / white 1–2 px at the clip arcs) | a 0-px tolerance: a stage-1 device question |

3. **Open skeptic pin gaps, not taken in wave 55** (the orchestrator's call before the landing; each changes patch bytes).
   Source: `../wave55-gate/prelanding/censuses-and-deliveries.md` §4. If taken later, `land-units.sh --check`, the revert
   states and the [W-L7a] patch-sha rule re-run.
   - L1: a registered-face pin for `NormalLineBox.faceMetrics` (the L1 skeptic's should-fix 2; SX1 survives today's 9 tests).
   - L2: the `PercentFillPlusBandModifier.measure` pin.
   - L3: the `originModifier` mechanism pin.
   - L4: the seam-4 patch-borne pin (changes PLAN §3 .swift row 2).
   - L7: D2's five guards.

## 2. The instrument claim to cite (closing PR, `docs/STATUS.md`)

Do not write "class closed". Cite the claim exactly as PLAN §0 bounds it after round 7 (D31's form). Every gating and
`pictureCorrectTally` key rejects:
- every fake class in the standing set (`probes-teeth.py` … `probes-teeth-r5.py`; `probes-selfcheck.txt` §T, §T3, §T4, §T5);
- any paint outside its declared zone in a colour that is not the cell's declared class.

The claim does NOT cover MF1–MF7 or the 2-px floor (§1 item 2). A picture-correct count certifies the probes' reads, the
residual read and the recorded looks. It does not certify the absence of those classes.

## 3. Stale text for the docs pass (no wave-55 unit owns it)

- `runtimes/compose/…/color/ColorApplier.kt`: the `applyColors` kdoc says "border draws sit OUTSIDE the alpha layer … left as
  future work". This is no longer true on the legacy route after L6 U2 (`../wave55-android-paint-order/_note.md` deviation 6;
  the file was read-only for L6).
- `runtimes/compose/src/test/java/com/styleconverter/runtime/lists/ListMarkerOutsideHangTest.kt:89`: "RTL: the inline-END
  side", plus its test name. This is the same false claim L5's unit D fixed in the main file
  (`../wave55-counter-suffix-rows/_note.md` deviation 5).
- `ContentBoxRelativeInflationTest.kt`: the stale "future work" phrase (C9). Its pointer in `../wave55-cbb-android/_note.md`
  was cut on base numbering (citations audit item 6). Cite it by phrase: "a px band cannot be added to a fraction".
- `land-units.sh` ORDER row 30 (L5 `U1-ios-LRM`): its commit-body note says "[H] counter-suffix ios WRONG (or H-low-shaped)",
  which predates the regenerated precedence (an `[H]` ios GEOMETRY reason or an H-low look; PLAN §10 item 5). It was left
  unchanged so that the manifest's only difference stays the `cutOn` text. Re-word it with the next table edit.

## 4. Notes for the next wave's instruments

- L6 XR3 (`../wave55-android-paint-order/_note.md` fix-r2): on a BORDERED box with overflow-x `visible` and overflow-y
  `clip`, neither `shouldClip` nor `clipsX` gives the CSS picture. Such a box has 0 corpus carriers today. A future carrier
  needs a per-axis band split.
- `probes-selfcheck.py` §C4 builds its fake run id with `os.path.relpath(fake, ROOT/tools/titan/runs)`. It does not use the
  real path, as `probes-teeth.py` `make_run` does. In a throwaway worktree, whose `tools/titan/runs` is a symlink, the
  kernel resolves `..` from the symlink's target, so the §C4 keys print MISSING. On the shared tree (a real directory) they
  read. The regeneration ran its §RG record from the shared tree for that reason (`regen-verify.md`). One-line fix: `realpath`.
- The L5 score arms (`counter-suffix-rows-replay/arms/`) model only row 9's `1` for U3-ios. Scoring U3's five other tabular
  glyphs needs a re-graft of those arms. Until then the counter-suffix ios score prediction is MED.
