# Wave 54 · S1 fix — plan / docs / records lane

## S1 fix pass

**Scope.** This lane fixes S1's plan / docs / records items (`../_note.md`):
- should-fix 1, 7, 8 and 9;
- nits N1, N3, N5 and N9;
- the S2 file list. The `git add -f` is the orchestrator's job.

**Rules held.**
- Tree `campaign/applier-campaign` at `4f3853d7`.
- No commit, checkout, stash or reset. The one index change is the `git rm` of item 5, which the task names.
- No device, emulator, simulator or Chromium.
- Every mutation ran in a mini export in the session scratchpad, never in the shared tree.
- Other fix lanes edited the shared tree at the same time: L1, L2 and L6 (`git status`). I touched none of their files.
  The exception is the L1 `_note.md`: I changed its two count strings with exact one-occurrence replaces.

Every output below is in this directory.

### Verdict: CLAIM-HOLDS on my items. All five are fixed; there are no open defects.

### 1. S1 should-fix 1: the [M] geometry rule can now see a missing '.' run

**The number** (`marker-left.measure.py` → `.out.txt`). The ref's marker-ink LEFT edge on all four RTL rows:
- x133 at the antialiased edge (sum < 600);
- x134 at the `dark` core (sum < 300), which is what the probes scan.

The pictures that must stay OK:
- wave53-probe web: x134 ×4;
- wave53-probe iOS: x134 / 134 / 135 / 134.

So the rule is **x133 ±2**. That interval accepts 134 and 135 and rejects the nodot fake's x138.

**The rule.** Each RTL row's marker ink must start at x133 ±2. Added to:
- `wave54-plan/rtl-marker-bake.geometry.py`: `MARKER_LEFT`, checked after the width check;
- `wave53-plan/lists-bakes.geometry.py`: in `check_suffix`, after the no-ink check.

The why is cited from the css-counter-styles-3 `suffix` descriptor: the marker is the counter plus ". ", which reads
".1" in RTL order, so its ink begins at the '.'.

**Executed checks:**

| check | output | result |
|---|---|---|
| L1 skeptic's fakes, replayed with S1's method (`skeptic/geom-fakes.py` into a scratch root) | `geom-fakes.post-fix.out.txt`; diff vs S1's `geom-fakes.replay.out.txt`: `geom-fakes.pre-vs-post.diff.txt` | **exactly the 3 formerly-OK nodot lines turn WRONG**: rtl web, lists web, lists iOS (`marker left x138 vs ref x133±2`). ctl web / iOS stay OK; nodigit unchanged; every ref line OK |
| HEAD vs fixed probes on the six recorded invocations (rtl: wave53-open / -probe / -final; lists: wave52-ship / wave53-probe / -final) | `probes-pre-post.py` → `.out.txt` | `PRE == POST over the six invocations: True (72 lines)`; 0 MISSING. The ref is OK; wave53-probe web / iOS are OK |
| recorded `rtl-marker-bake.geometry.out.txt` | same | its 3 blocks are byte-identical (inline self-test line aside) |
| the recorded inline replay self-test (`rtl-marker-bake.replay.py` PNGs, fixed `m_check`) | `replay-selftest.py` → `.out.txt` | byte-equal to the recorded line: android-P WRONG, android-Mp-dev OK, android-Mp-ref OK |
| `geometry-gate.py wave53-final --base wave53-open --self-test` | `geometry-gate.selftest.post-fix.out.txt` | **`self-test: HOLDS`**: gating 37 all FAIL, control 112 all PASS, report 36 all FAIL. Every line equals S1's record (S1's `time` line aside) |
| `geometry-gate.py wave53-probe --base wave53-open --lanes L1,L2` | `geometry-gate.wave53-probe.L1L2.post-fix.out.txt` | the L1 rows are unchanged: [P] PASS, [M] web / iOS PASS, [M] android FAIL. Revert rule 4 names `Mprime, TB-android`; exit 1 (the reverted units, as recorded) |

**Mutation proof** (`mutate-probes.py` → `mutations.probes.out.txt`):
- The claim: the nodot fake is WRONG on rtl:web, lists:web and lists:iOS.
- Baseline: the claim holds (GREEN).

| mutation | mutated sha | claim | restore | re-run |
|---|---|---|---|---|
| rtl check → `if False and …` | `484bd89d` | RED (rtl:web OK) | sha256 `01da3224…` = `01da3224…`, BYTE-EXACT | GREEN |
| lists check → `if False and …` | `32db967f` | RED (lists:web and lists:iOS OK) | sha256 `fd8004a4…` = `fd8004a4…`, BYTE-EXACT | GREEN |

**`plan-build.py`.** Both L1 probes gain a documentation-only `teeth` string, which geometry-gate.py does not read.

### 2. S1 should-fix 7 / nit N3: PLAN.md
- **§10 item 6** restates every hand-off of `25cbd1c2`:
  - L4 CBB-android +3 semi-replaced-stretch android captures (CBB 14; L4 has 29 predictions and android carriers 24);
  - `contain-content-011` android in `stayDegenerateEvenIfPass`;
  - L5 GO-SMALL: U1-android only and U2-ios HELD; the six inset rows become must-not-move; L5 carriers [0, 0, 7];
  - `hyphenate-character-002` ×3;
  - union carriers web 44 · iOS 24 · Android 51, wire 23;
  - 18 revert units, 776 watch lines, 655 must-not-move cells;
  - ceiling web 1241 · iOS 1131 · Android 1122;
  - §3's stale L5 rows and totals: 8 patches, and `.swift` has 1 row;
  - the DEGENERATE list: 4 → 8 → 10.

  Each number was read from `plan-build.out.txt` and its `25cbd1c2` diff. That includes the web LOW tally going 0 → 1:
  it is the "P" in `hyphenate-character-002` web's new `kind`, and LOW rows are never counted.
- **§10 item 7** records this pass.
- **§3's U3b row** now names its three anchors:
  - `brHostLineBoxPx` at `:10142`;
  - the br rule at `:11457-11459`;
  - the `childLineCtx` seed at `:11706`.

  These are the anchors in the `seam-3b.patch` header.

### 3. S1 should-fix 8 (L6 skeptic D4): the honesty labels
**Added to `stayDegenerateEvenIfPass`, each with its residue named in the row's `kind`:**
- `css-position/position-absolute-semi-replaced-stretch-other.html web`: the label paints "abel" where the ref paints
  "label";
- `css-cascade/scope-pseudo-element.html web`: the B/Foo wrap defect remains.

**A new pin in `plan-build.py`** keeps the two sides in agreement:
- every list entry names a prediction row;
- every row whose `kind` says "DEGENERATE even if P" is listed.

It runs after `probe_decisions()`, so a RESTATE `kind` is read as restated. A withdrawn row stays in `predictions`.

**The probe-decision hook still works** (`hooks-check.py` → `.out.txt`). The HEAD and the fixed `plan-build.py` were run
on all 7 recorded `--reverted` hook inputs (fix-r2 a / u1 / u2ios / u3b / w1; fix-r3 abc / u2ios). The exit codes and
stdout are identical on every one of them: all `SAME`, rc 0.

**Mutation proof** (`mutate-planbuild.py` → `mutations.plan-build.out.txt`, run in an export):

| mutation | result | restore | re-run |
|---|---|---|---|
| MUT-a: drop the stretch-other entry (the D4 state) | rc 1, `…omits it: ['…semi-replaced-stretch-other.html web']` | sha256 `abac114a…` BYTE-EXACT | rc 0, outputs = the tree's |
| MUT-b: the entry names `ios` | rc 1, `…names a cell with no prediction row` | BYTE-EXACT | GREEN |

**Regeneration** (`plan-build.regen.out.txt`):
- `python3 plan-build.py` exits 0 and every assertion holds;
- stdout equals the committed `plan-build.out.txt` byte for byte;
- `watchlist.txt` equals HEAD byte for byte;
- `expectations.json` changes only in the 2 list entries, 2 `kind` strings and 2 `teeth` strings.

**Re-checked after regeneration:**
- `watchlist-check.out.txt`: `unmatched 0` on wave53-final and on wave54-open;
- `pred-cells-check.post-fix.out.txt`: 115 / 115;
- the gate self-test still HOLDS.

### 4. S1 should-fix 9 (L7 skeptic S5): `docs/DYNAMIC_CAPTURE.md` §5
**Applied** (`dyncap-apply.out.txt`):
- the base sha256 `4258766a…` equals the patch header's;
- `git apply --check -v` rc 0, then `git apply -v` rc 0.

The hunk carries **no count or seed lines**, so none were held back for later.

**One deviation, on purpose.** L7 skeptic N1 executed the check and found that the hunk's phrase "counted for the verdict
line" is false: the stem line never prints the spread. So the doc now says the spread "is returned in the verdict object
(`underPaintSpread`), never asserted", which is what `label-chrome-check.mjs:98` does.

**The L7 note §7** records that the hunk is applied.

**doc-staleness** (`doc-staleness.out.txt`) exits 1. The failing lines are all suite counts from the other fix lanes'
in-flight tests: tooling 2334, web 1381, compose 3492. None comes from a docs line of this pass. The orchestrator
restamps them after its sweep.

### 5. Nits
- **N9.** The L7 `_note.md` §3 names all 18 U2-seed PNGs in full: `iOS__001_ATC_PropsThenAll_InGreenParent.png`,
  `iOS__003_ATC_InitialUnderRedParent.png` and `iOS__005_ATC_DirectionSurvives.png`. A script check found 18 named,
  equal to the 18 in `label-chrome-all-reset.seeded-77fe41e8/`, with 0 elisions left.
- **N5.** Two counts in the L1 `_note.md` are corrected:
  - "29/29 OK" → **30/30 OK**: the four `mutations-*.result.json` hold 7 + 7 + 15 + 1 rows, and every `verdict` is OK;
  - "+101 / −4" → **+105 / −4** (`git diff --numstat 1cde1f48 d64d4c6e`), net +101, which gives 1172 → 1273.
- **N1.** The 518 485-byte seam-file copy is removed:
  - path: `wave54-table-body-cell/seam1-files/…/core/renderer/ComponentRenderer.kt`, removed with `git rm` (staged);
  - first proved regenerable: `seam1-hunk.py` on the base renderer, at both `db6e8aa0` and `1cde1f48`, gives sha
    `eaf9111e…`, byte-identical to the copy (`seam1-regen.out.txt`);
  - the patch-borne `TableCellHugSeamWiringTest.kt` stays, as the L2 skeptic asked;
  - a one-line `seam1-files/README.txt` says how to regenerate the copy.

### S2: evidence the orchestrator must `git add -f`
`git status --ignored --untracked-files=all --short tools/titan/results/wave54-*` lists 110 ignored files:
- **104 `*.log` files** (`.gitignore:21`), listed in `ignored-evidence.logs.txt`:
  - L3 5;
  - L4 2;
  - L1 1;
  - L5 89 (S1 counted directory entries: 58);
  - L6 7.
- **6 dry-run `out/` files** (`.gitignore:24 out/`; L4's plan-hunk dry runs), listed in `ignored-evidence.out-dirs.txt`.

Re-run the command just before the records commit, because the other fix lanes may add more logs.

### What I could not verify
- No device picture. The new rule was proven only on frozen captures and on synthetic fakes. Stage 1 is its first real
  reading.
- The L1 note's own "wave 53 was +95 / −4 → 1259" does not add up: 1172 + 91 = 1263. I left it, because it is outside
  N5 and I had no wave-53 base to re-derive it from.

STATUS: COMPLETE
