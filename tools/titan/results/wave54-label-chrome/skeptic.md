# Wave 54 · L7 label-chrome — SKEPTIC report

Shared tree `/Users/dranak/Documents/Projects/Style-Converter/.claude/worktrees/trusting-bohr-bd6fbf`, branch
`campaign/applier-campaign`, HEAD `db6e8aa0`. Read: `_note.md`, PLAN §0/§1/§2 L7/§3/§4/§6/§8/§10, brief
`label-chrome-all-reset.md`, `expectations.json` `lanes["L7-label-chrome"]`, the U1 diff and the two new files.
Every finding below comes from an EXECUTED repro; the scripts and their outputs are kept under `skeptic/`
(written independently of the lane's `census.mjs` / `mutations.py` / `sim-post-seed.sh` / `verdict-dump.mjs`).
No device, Chromium, Gradle or xcodebuild run; no commit / checkout / stash; no seam file touched. The owned
files were mutated in place and restored byte-exact (`skeptic/sha256.pre.txt` == `skeptic/sha256.post.txt`:
check `fad37378…7b65`, test `9d8f2b22…23ed5`, manifest `5d5ea832…98753`, = the lane's `sha256.final.txt`).

## Verdict: MIXED — the lane's core claims hold; no must-fix; five should-fix, five nits

What HOLDS (executed):
- **Suite** `node --test tools/visual/label-chrome-tripwire.test.mjs` → 148 / 148, three `exempt-entry … pending`.
- **Units stand alone** (`skeptic/mirror.sh`, a `git archive HEAD -- tools/visual fixtures` mirror, so the other six
  lanes' working-tree edits are excluded; `skeptic/units.out.txt`):
  HEAD → 136 / 136; **U1 alone → 148 / 148** (entries pending); **U2-seed without U1 → 142 tests, 3 fail**
  (001 / 003 / 005 RED (i) 437/437, 369/369, 293/293 ×3 — revertOrder "U2-seed before U1" is right);
  **U1 + U2-seed → 154 / 154**, three entries `verified`, `001/003/005 [exempt band-identical, 0 glyph px] ok`,
  `000/002/004 [band-identical, 224/80/65 glyph px] ok`.
- **R0 / R3** (`skeptic/verdicts.mjs`: HEAD's `checkTriplet` extracted from `git show HEAD:…tripwire.test.mjs`, node:test
  stubbed, vs the working-tree checker): committed stems 130, **identical 130, differ 0**, green 130; the six seeded
  stems identical too, R0 counts 437 / 369 / 293 (the record's "437/437 for all three" correction is right).
- **Every lane mutation replays** (`skeptic/mutate.py` → `skeptic/mutate.out.txt`): MUT-1…MUT-9 each RED on its pin
  (SUITE / SEED mirror / R3), restored sha256 == pre, GREEN again. Counts match the note (MUT-2 SUITE 3 / SEED 6 fails;
  MUT-5 SEED 3; MUT-6 SUITE 1 / SEED 2; MUT-8 R3 differ 9).
- **`fixtureSeeded` ≡ `test-all.sh _gate_fixture_has_baselines`** (`skeptic/seeded-equiv.sh`, the shell function
  eval'd verbatim): 446 tracked fixtures × {390 committed, 408 with seed}: 14 / 14 and 15 / 15 seeded, **0
  disagreements** — so "`pending` ⇔ the net runs the fixture gate-only" is true.
- **Corpus census** (`skeptic/census-all.mjs`): 1435 per-test IR documents, **11** carry `All` (= the lane's list and
  `expectations.json` `mustNotMove`'s 11 tests ×3); per-test IR `wave53-final` vs `wave54-open` **1435 / 1435
  byte-identical**. `cells.mjs` (`skeptic/cells.out.txt`): all 33 must-not-move values in the note equal
  `wave53-final` (= `wave54-open`). Carrier sets ∅ / ∅ agree with `expectations.json`.
- **U2-seed name radius** (`skeptic/census-seed.mjs`): of 446 tracked fixture JSONs only all-then-color declares
  `ATC_*` / `reset` / `span`; of the 9 gate fixtures only all-then-color flips gate-only → BASELINE; 0 WPT
  components carry a seeded name.
- **Pictures** (opened: web 001, Android 000, iOS 005, web 005, iOS 003, iOS 002, web 004, Android 001): label in
  the band on 000 / 002 / 004 only; 001 / 003 / 005 band empty; green fills at (16,16); 005 "123" at the right edge,
  web serif vs native sans. Non-ground pixel counts 9824 / 9600 / 80 / 12000 / 12065 / 8000, identical ×3
  (= 160×60+224, 160×60, 80, 200×60, 200×60+65, 200×40).
- **Opening net reproduces the seed bytes**: `apps/{android,ios,web}-harness/screenshots/` (16:52:47–16:53:05)
  `cmp`-identical to the 77fe41e8 copies 18 / 18 (`skeptic/open-net-captures.out.txt`); `wave54-open`
  fixture-net.log :1224 / :1225 / :1247 read as quoted.
- **Geometry**: `geometry-gate.py wave53-final --base wave53-open --lanes L7 --self-test` → 0 keys, HOLDS, exit 0
  (vacuous, as the note says); the seed probe default → 18 OK, 18/18, exit 0; `--naive` → 9 WRONG; `baseline` →
  exit 1 today. **Teeth on a wrong PICTURE, not just a mode** (`skeptic/geo-teeth.mjs`): label painted onto a
  copy of web 001 and erased from Android 004 → two `GEOMETRY WRONG` lines, exit 1 — so [W-L7] step 3 can fail.
- **Hygiene**: no seam patch (none registered); `hunk-for-orchestrator-1.patch` `git apply --check` clean, header
  sha256 `4258766a…` == `docs/DYNAMIC_CAPTURE.md` at HEAD; no "probe" leftover, no scratchpad pointer, no
  "engine" in owned files or the note; `label-chrome-check.mjs` 199 lines, test 207; the only uncommented lines
  are function signatures under docstrings, braces and `test(` names (HEAD's own style); `git status` under
  `tools/visual`, `docs`, `fixtures`: only the three U1 paths. No L7 lock dir (the `ComponentRenderer.kt/.swift`
  locks seen at 19:30–19:31 belonged to other lanes and were released, seam files restored, by 19:37).
- Brief replay `label-chrome-all-reset.replay.mjs` re-run now: identical to its recorded `.replay.out.txt`.

## Defects (ranked)

### should-fix

**S1 · U2-seed revert-unit section does not name its exact paths.** `_note.md` §3 writes `iOS__001_…`,
`iOS__003_…`, `iOS__005_…` for three of the 18 PNGs. PLAN §0 ("each note has one section per revert unit,
naming exactly the paths of that unit's commit. `land-units.sh` is written from those sections"). The names are
recoverable from `label-chrome-all-reset.seeded-77fe41e8/`, but a literal transcription commits three wrong
paths. Fix: write `tools/visual/baseline/iOS__001_ATC_PropsThenAll_InGreenParent.png`,
`iOS__003_ATC_InitialUnderRedParent.png`, `iOS__005_ATC_DirectionSurvives.png` in full.

**S2 · U2-seed's radius inside the tooling suite is under-reported; §4 (C) "The seed can only be read by the
all-then-color child" is false.** Three tooling tests enumerate `tools/visual/baseline/` and change INPUTS when
the 18 PNGs land (`skeptic/seed-readers.inputs.txt`):
- `column-presence-e2e.test.mjs` `pickTrio(5)`: `000_GrayBasis_Red … 001_GrayBasis_Green` →
  `000_ATC_AllThenProps 000_GrayBasis_Red 000_Ratio_16_9 000_Sepia_Zero 000_Sizing_Fixed`;
- `cross-platform-gate-e2e.test.mjs` scaffold sources: `Android__000_GrayBasis_Red` / `iOS__000_Ratio_16_9` →
  `Android__000_ATC_AllThenProps` / `iOS__000_GrayBasis_Red` (its "diverge" case needs these two to differ);
- `png-color-space.test.mjs` asserts the colour chunk of every committed PNG (the 18 included);
plus `doc-staleness-check.sh` `check_baseline_orphans` (all six names are declared, so it stays green).
Executed (`skeptic/seed-readers.sh`, HEAD mirror): **40 / 40 green without and with the seed** (libuv's scandir
sorts, so CI's order is the same as here). No outcome changes, but the census is the instrument the closing
control reads, and these readers are exactly what R9 must be looked at for. Fix: add them to the note's census.

**S3 · The exempt path drops band byte-identity the moment any ONE platform paints in the band** (brief §9 R2
design; the note discloses only "partial or mis-positioned"). `skeptic/holes.mjs` on the REAL seeded PNGs of all
three exempt stems — GREEN: label on web only with origin +1 px (x); on Android only +1 px (y); a truncated label
(first 100 px) ×3; label ×3 at +1 px; iOS drawing a DIFFERENT label in the band. RED (correctly): full label ×3, full
label on one platform. Contrast: the label-due path reds a +1 px web shift on 000 (`(i) web: 145/224`). With P = ∅
there is no positional assertion left once the mode flips to glyph-mask. Realistic single-platform predicate drift
draws the full label at the spec origin and IS caught, and a mis-positioned drawer is caught suite-wide by 133
label-due stems — hence not must-fix. Cheap strengthening that keeps R2's protection (paint on all three): on an
exempt stem, red when the band state is MIXED (ground ×≥1 and painted ×≥1).

**S4 · Untested branch: "EVERY same-named node must justify the exemption"** (`verifyExemptEntry`, the
`for (const node of hits)` loop). Mutation X1 `hits` → `hits.slice(0, 1)` **survives** SUITE (148/148) and the
seeded mirror (154/154). A pin claimed by a comment is not a pin. Fix: one synthetic DOC where a name is a
container under one parent and a leaf under another → red.

**S5 · The DYNAMIC_CAPTURE §5 hunk documents U1 but rides U2-seed's commit.** [W-L7]'s failure branch ("do NOT
commit the PNGs: U1 stays") then leaves the normative doc describing a tripwire without the manifest / clause (iv)
while U1 is on the tree (and keeps the stale "(iii) agree within ±1" sentence). The hunk is seed-independent
(nothing in it is true only after seeding). Fix: say the hunk lands with U1's landing (orchestrator docs commit)
whatever the seeding decides; only the counts / STATUS:19 / gate-fixtures / fixture `_comment` lines wait for it.

### nits

**N1 · "counted for the verdict line" is inaccurate** (new test header (iii) and the docs hunk): the stem line
prints only `[mode, N glyph px]`; `underPaintSpread` is never printed (executed: `023_Shadow_Simple
[glyph-mask, 189 glyph px] ok`). Inherited from HEAD's comment, re-asserted by the "correction". Print it or
reword ("returned in the verdict object, never asserted").

**N2 · Clause (iv)'s "every pixel" tolerance (the R2 false-red guard) is unpinned**: X8 "every" → "any"
survives SUITE and SEED. Stricter direction, no false-green risk.

**N3 · The stem loop's HINT call site is unpinned**: X4 (call removed) survives; shown only by the lane's
mirror-only `sim-post-seed.hint-demo.out.txt`. Message-only; it can never turn red into green.

**N4 · `expectations.json` U2-seed `commit` still lists "tripwire header 130 -> 136 / 390 -> 408"**, a U1 file.
The lane decided against it (§1 item 3, sound: live counts are gone, the dated record stays), but no hand-off asks
the orchestrator to amend that text; following it would make U2-seed touch U1's file (units not disjoint).

**N5 · Census wording**: "tracked fixtures (446) other than all-then-color" — 446 is the total including it
(445 others; 445 of the 446 JSONs have `components`).

## Extra mutations (skeptic's own, `skeptic/mutate.py`)

| id | mutation | result |
|---|---|---|
| X1 | only the first same-named node verified | **SURVIVED** SUITE + SEED (S4) |
| X2 | `checkExempt` drops `base.problems` | RED (the (ii) LSB synthetic) |
| X3 | `_text: ""` counts as text | RED (manifest mutations) |
| X4 | stem-loop HINT call removed | **SURVIVED** (N3) |
| X5 | non-red (incl. unlisted) stems exempt | RED 130 fails — over-exemption is caught by (iv) on the labelled stems |
| X6 | 005's fixture → `fixtures/properties/global/longtail.json` | RED SUITE 1 / SEED 2 |
| X7 | the stale-entry rule disabled | RED |
| X8 | (iv) "every" → "any" | **SURVIVED** (N2) |

## Predictions re-read

T1–T3 (HIGH), T4 (HIGH), T5 (HIGH), m1–m5 (HIGH) — reproduced on the same bytes through the working-tree code.
Tripwire 148 on U1 alone / 154 after the seed — reproduced. T6 MED-HIGH and "fresh seed byte-identical MED" stay
device claims; the opening-net captures equal the 77fe41e8 bytes 18/18, and the only other lane edit near the
capture app (L2 `ScreenshotCaptureScreen.kt`) is in the composed / WPT canvas (`composedCanvasRoots`), not the
legacy `CaptureCanvas` all-then-color uses. No green here is DEGENERATE: the three exempt stems are band-ground ×3,
so (ii) byte-identity binds on their current pictures.

## What I could not check

- The seeding, the post-seed geometry read, the T6 net and the full tooling suite on the ship tree (device /
  orchestrator windows; S2's three readers were run focused, 40 tests).
- Node 24 (CI) — run under v22.21.0 only.
- Whether a future exempt stem's own band paint would make S3's suggested MIXED-state rule false-red (none of the
  three entries paints the band).
