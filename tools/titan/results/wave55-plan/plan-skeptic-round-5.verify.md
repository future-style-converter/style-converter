# Wave 55 — plan-skeptic round 5: VERIFY (independent of fix-r4)

Tree `…/trusting-bohr-bd6fbf`, branch `campaign/applier-campaign`, HEAD `611c9f93` (unchanged). `fix-r4.done.md` was read
only for what it claims. Seven builder lanes were running in their own worktrees throughout; this pass edited no file they
read.

**Inputs (sha256, first 16).** All of these match `fix-r4.done.md`:

| file | sha256 |
|---|---|
| `expectations.json` / `watchlist.txt` / `PLAN.md` | `980542d01c175500` / `4b1a9cd5bb421e3b` / `35639d61a07fdfa8` |
| `geometry-gate.py` / `probe-readout.mjs` / `plan-build.py` / `plan-prose.py` | `39b9a0d7b1acd7df` / `d627607ddcbb4d8c` / `67833a1dac86bbc5` / `2d4d802275cbbeed` |
| probes: hyphen-ios-linebox / compose-wpt-content-box-cb / cbb-android / positioned-parent-slot | `0dc54c73` / `c52fe610` / `76abeb24` / `134d16f2` |
| probes: ua-heading-margin / counter-suffix-rows / android-paint-order | `50344dc6` / `bd2f001a` / `360e6e28` |
| `probes-teeth.py` / `-r2` / `-r3` / `-r4` / `probes-selfcheck.py` | `79af7c0e` / `590a1858` / `668d0c54` / `686e21fe` / `7aeb3f1b` |

**How it ran.**
- Read-only on the shared tree. Every record that a script would `tee` into the tree was redirected to the scratchpad.
- The verdict scripts and the selfcheck's L6 `--fakes` step ran in the throwaway `<scratchpad>/wave55-plan-r4-verify5`.
  It was made with `git worktree add --detach … HEAD`, given symlinks to `tools/titan/runs` and `tools/wpt`, and an rsync of
  the current `wave55-plan/` and `wave55-gate/` bytes. Before use, `diff -rq` against the shared dirs was empty.
- `fix-r4.teeth-before-after.sh` made and removed its own `<scratchpad>/r5/teeth-ba/wave55-plan-r4-teeth`.
- Pinned interpreter `/opt/homebrew/bin/python3.12 -B` everywhere, every long run at `nice -n 19`, at most two of this pass's
  processes at a time.
- No gradle, xcodebuild, vite, emulator, simulator, adb or test-all.

## 1. Claims re-executed

| # | claim | executed | result |
|---|---|---|---|
| 1 | `geometry-gate --self-test` HOLDS | `geometry-gate.py wave54-final --base wave54-final --self-test` | **HOLDS**, exit 0: 230 keys, gating 43 FAIL, control 160 PASS, report 27 FAIL, UNMEASURED 0, self-check failures 0 |
| 2 | `plan-prose --check` HOLDS | on a scratch copy of PLAN.md, then on the installed one | **HOLDS**, exit 0 both (36 gen blocks · 41 decision ids · 46 citations · 28 / 28 units). PLAN.md sha unchanged |
| 3 | watchlist unmatched 0 | `RUN=wave54-final WATCH=…/watchlist.txt node wave52-plan/watchlist-check.mjs` | **unmatched 0**, exit 0. Output byte-identical to `watchlist-check.wave54-final.out.txt` |
| 4 | `expectations.json` is what `plan-build.py` generates | `plan-build.py --out <scratch>` | `expectations.json` and `watchlist.txt` **cmp-identical**; stdout identical to `plan-build.out.txt` |
| 5 | `fix-r3.verdicts-preserved` reproduces | `fix-r3.verdicts.sh` in the throwaway (round-2 bytes vs current bytes, 45 argument sets) | exit 0, "every verdict preserved". Body (48 lines) **identical** to the committed `fix-r3.verdicts-preserved.out.txt` and to `fix-r4.verdicts-vs-round2.out.txt` once the tree path is normalised; only the timestamp header differs |
| 6 | `fix-r4.verdicts-preserved` reproduces | `fix-r4.verdicts.sh` in the throwaway (round-3 bytes vs current bytes) | exit 0, "every verdict preserved". Body **identical**; only the timestamp header differs |
| 7 | the round-3 / round-4 teeth records reproduce | `OUT=<scratch> fix-r4.teeth-before-after.sh <scratch>` | **body byte-identical** to `fix-r4.teeth-before-after.out.txt` (171 lines; line 1 is the timestamp). BEFORE (round-3 bytes): 265 cases, 145 violations, all round-4 cases, rounds 1-3 0, clip-10 0. AFTER (current bytes): 265 cases, **0 violations**, 48 / 48. The BEFORE leg is the round-3 bytes, so it also re-proves fix-r3's own AFTER claim (rounds 1-3: 0 violations). Its throwaway was removed (0 left) |
| 8 | `probes-selfcheck.txt` reproduces | the shared `probes-selfcheck.py --scratch <scratch> --wt <throwaway> --out <scratch>/probes-selfcheck.r5.txt` (§0, §L1-§L7, §C4, §T, §T3, §T4; 36 min at nice 19) | exit 0. **Byte-identical** to the committed `probes-selfcheck.txt` (1231 lines, `682496d3…`). That covers §0 HOLDS, §T 265 cases / 0 violations / 48 of 48, §T3 987 reads / HOLES 0 / residuals 0, and §T4 IN-ZONE 0 / > 2-px holes 0 / benign 493. The throwaway's fresh L6 fakes are pixel-identical to the committed ones. The §T4 per-lane `fakes4` summaries are the same six lines as `fix-r4.verifier-rerun.out.txt` |
| 9 | `fix-r4.teeth-r4-equiv` reproduces | `fix-r4.teeth-r4-equiv.py <scratch>/selfcheck/sweep4`. These are row 8's §T4 fake runs, made by `plan-skeptic-round-4.fakes/fakes4.py`, the relative-path copy of the verifier's generator: the same picture code, with only the two path lines differing | exit 0. Body **identical** to `fix-r4.teeth-r4-equiv.out.txt` (its line 1 is the caller's header): 42 pinned cases, 286 native captures, 42 identical, 0 differ |
| 10 | every fixed picture prints OK | each group's FIXED fake run read by the real gate, inside the round-5 sweep (§2); plus §T's fixed cases (row 8) | **OK on every gating / tally key under test**. Two exceptions, both by design: `[X] flex-…-safe-002 android` (round-3 N5: its replay keeps 50 crossing px) and the four `[F]` keys under the U1-alone arm (WRONG by design, tested on the C arm) |
| 11 | nothing outside the two plan dirs changed | git state before / after (§5) | **holds** |

## 2. The round-5 strictly-additive sweep

### 2.1 Instrument

`plan-skeptic-round-5.verify.d/fakes5.py` (`1d84784e8fdb26d5`) was written without reusing `added_ink.py`,
`added_ink4.py` or the `fakes4.py` builders. It reuses only `probes-teeth.py`'s `make_run` / `gate` / `cap`, the run
plumbing every round used.

**Fixed pictures.** The standing set's `*fixed` cases (probes-teeth + r2), merged when they share pictures: 14 groups,
covering every gating key and every `pictureCorrectTally` key.

**Read zones.** For each key, the zone is the region its probe reads with a non-pixel-identity read. Regions that a
pixel-identity read covers (`[K]`, `I_OUT`, `ROW9_OUT`, the L3 `[C]` rows) cannot accept added ink, so they are not faked.

| lane | zone |
|---|---|
| L1 | x0-139 × y DIV0-599 |
| L2, L3 stack / dynamic-offset, L6 | the whole capture |
| L3 dir rows 6 / 7 / 9 | each row's 20-px band |
| L3 bidi | the four RTL bands |
| L4 | y96-399 (011: y112-399) |
| L5 `[H]` | x0-63 × y64-111 |
| L5 `[C]` | x0-63 × y112-159 |
| L5 `[T]` | x136-150 × y208-231 |
| L5 `[I]` | x200-389 × y95-151 |

**Ink classes.** The zone's SOLID colours: at least 15 px, not an anti-aliasing blend of two palette colours. The unit's
own changed colour comes first, up to 5 classes. Where a zone has no solid colour (the CJK marker), the farthest colour is
used and its glyph cores are the ink.

**Strictly additive.** Paint goes only onto pixels that are flat palette colour and not already that class's ink. A faint
copy is a 50 % alpha blend. Nothing is moved or erased. Each record also counts how much paint landed on the zone's
background versus over another colour (an overpaint).

**Variants, per (zone, class)** — 30 in all:
- specks touching a glyph edge: 1 px, 2×2 and 3×3, at the middle or the last free spot;
- 2×2 / 3×3 specks in the widest gap of the band, and in the open zone;
- thin 1-px lines: a strike-through, an underline, an overline, a vertical line in the widest gap, and a 30-px line in the
  open zone;
- faint 50 % copies:
  - the class ink moved +1 x, +1 y, (+2,+1), (+4,0), (0,+5) and (−3,+3);
  - every non-background pixel moved (+3,+2);
  - a 3×3 faint speck in a gap and in the open;
  - a faint strike-through and a faint 30-px line in the open;
  - a faint duplicated glyph;
- a duplicated glyph at full ink: after the band's end, in its widest gap, and below itself.

**Gate.** Every fake run is a symlink tree of wave54-final under the scratchpad, read by the REAL `geometry-gate.py` on the
generated `expectations.json` with `--base wave54-final`, as rounds 1-4 did.

**Acceptance.** A fake is ACCEPTED when an owning gating key that PASSed the fixed picture still PASSes, or when every key of
the zone's tally entry still PASSes.
- More than 2 changed px: **MUST-FIX**.
- 2 px or fewer: the named 2-px floor (PLAN §0 / D39).

**Visual check.** The builders were checked by eye (`look5.png`). A first draft had an operator-precedence bug that made
every pixel "flat"; it was discarded, and the sweep was re-run end-to-end with the final bytes. All six records carry
`fakes5.py 1d84784e…`.

### 2.2 Result per lane

| lane | fake runs | faked zones · key reads | MUST-FIX acceptances (> 2 px) | floor (≤ 2 px) | verdict |
|---|---:|---:|---:|---:|---|
| L1 hyphen-ios-linebox | 29 | 85 · 141 | **6**: all 3 gating keys, plus the 001 / 003 tally entries | 3 | **FAILS** — M4 |
| L2 cbb-android | 194 | 454 · 548 | **280**: all 8 gating keys and the 6 tally entries | 13 | **FAILS** — M1 |
| L3 positioned-parent-slot | 309 | 309 · 309 | **104**: bidi, stack, dyn-001, dyn-002, and the vrl-rtl-001 tally entry | 12 | **FAILS** — M2. The dir rows 6 / 7 / 9 **hold** (0) |
| L4 ua-heading-margin | 87 | 603 · 717 | **45**: all 12 gating keys | 27 | **FAILS** — M3 |
| L5 counter-suffix-rows | 75 | 148 · 148 | **0** | 10 | **holds**: all six gating keys and both counter-suffix tally entries |
| L6 android-paint-order | 207 | 525 · 525 | **269**: `[PO]` and all 5 testable `[X]` keys, plus their 6 tally entries | 20 | **FAILS** — M5, M6 |
| **all** | **901** | **2124 · 2388** | **704** on 33 of 43 gating keys | **85** | |

The 10 gating keys that hold:
- the 3 L3 dir rows;
- the 6 L5 keys;
- `[X] …safe-002`, which cannot be tested (no passing fixed picture: round-3 N5).

### 2.3 Per key: MUST-FIX acceptances, and the subset painted on the zone background only

The background-only subset matters because every finding below stands without counting any overpaint.

| key | accepted | background only (faint / full ink) | largest (background only) |
|---|---:|---:|---|
| L1 `hyphen-ios-linebox 001 / 003 / 004 ios` | 2 each | 2 each (2 / 0) | +30 px: a faint 30-px line below the text. A faint 3×3 between word groups adds +9 |
| L2 `abspos-autopos-htb / vlr / vrl-ltr android` | 37 each | 37 (21 / 16) | +10000 px: a second, 50 % green box beside the box |
| L2 `…border-radius-clip android` | 46 | 29 (13 / 16) | +6776 px |
| L2 `…clip-2` / `…clip-4` | 27 / 38 | 20 / 23 | +1438 / +6782 px |
| L2 `…clip-3 android` (compose / cbb) | 58 / 46 | 39 / 34 | +14900 px |
| L3 `[S] bidi-tab-001 android rtl-rows` | 19 | 6 (6 / 0) | +278 px. A faint copy of the run box beside it adds +216 |
| L3 `[W] anchor-position-multicol-nested-001 android stack` | 40 | 40 (24 / 16) | +1493 px |
| L3 `[S] dynamic-offset-rtl-001` / `[W] …-rtl-002 android` | 15 / 15 | 7 / 7 (faint) | +9900 px: a faint second parent box |
| L3 `[S] dynamic-offset-vrl-rtl-001 android` (report class, tally entry) | 15 tally | 7 | +9900 px |
| L4 `[M]` 005 ios / 006 ios | 9 / 9 | 9 / 9 (6 faint, 3 full-ink iOS grey (127,127,127)) | +30 px |
| L4 `[M]` 005 / 006 / 014 android, 014 ios, 011 ios / android | 6 / 5 / 5 / 3 / 4 / 4 | all (faint) | +30 px |
| L4 `[F]` 005 / 006 / 014 ios, 014 android | 2 each | 2 each (faint) | +30 px |
| L6 `[PO] backdrop-filter-clip-rect android` | 56 | 45 (19 / 26) | +6294 px |
| L6 `[X]` safe-001 / align-self-001 / -rtl-001 / -vertWM-001 / justify-self-001 | 29 / 46 / 45 / 45 / 48 | 27 / 35 / 34 / 32 / 33 | +4405 … +2056 px |

**Full-ink own-colour acceptances** — paint in the very colour class the key reads, at full intensity:
- `[PO]`: blue specks / lines anywhere (blue is bounded below only); pink outside its counted box;
- L3 dyn: a 2×2 yellow over the magenta child (yellow + magenta inside the parent ±1 %, magenta ±5);
- L4 iOS: specks / lines of its own (127,127,127) grey outside the band rows.

Every other acceptance is a faint copy, or paint of a colour the key's masks never read.

## 3. Defects

### Must-fix (each: the read that misses the ink, and the measured fix basis)

**M1 · L2 (all 8 gating keys; 6 tally entries) — only strict green and the fail ink are read.**
- Both probes judge strict green (0,128,0)±8 (bbox, XOR ≤ 40, core ±1) and red / pink fail ink. Nothing else in the capture
  is read.
- So all of these pass on all eight keys:
  - a faint copy of the green box (127,191,127), including a whole second 50 % box beside the real one (+10000 px);
  - black, #66ccff blue, #cccccc grey or yellow ink anywhere — specks, lines, a duplicated glyph.
- `residual.py` measures what the units change outside strict green (fixed ∪ base) grown 1 px:
  - **0 px** on the six U1 fixed pictures (the wave54-probe device captures). A "rest of the capture = the base's" read at
    tolerance 0 accepts them.
  - **573 px** on the clip-3 U2 replay: U2 also changes #middle's grey / blue runs, so the allowed region there must include
    the bands `cbb-android.geometry.py` already reads.

**M2 · L3 bidi `rtl-rows`, `[W] stack`, `[S] dyn-001`, `[W] dyn-002` (4 gating keys) and the vrl-rtl-001 tally entry —
colour masks only.**
- Faint copies of the blue run, the green squares or the yellow parent pass, because no mask sees them. bidi: a 50 % run box
  beside the box, +216 px; dyn: a faint second parent, +9900 px.
- Full-ink paint the keys do not read also passes: black specks / lines on the stack picture (16, all on the background),
  and on bidi 3 yellow overpaints of the RTL bands' non-blue pixels (+4 … +12 px).
- dyn's "yellow + magenta inside the parent ±1 %" also lets a 2×2 yellow overpaint of the magenta child through.
- `residual.py`, outside the key masks of (fixed ∪ base) grown 1 px:
  - stack and dyn ×3: **0 px**;
  - bidi: **93 px** (24 at 2 px). The '0' glyphs and the run's AA ride the blue run, so the allowed region needs the band's
    run extent, not only the blue mask.
- The dir rows 6 / 7 / 9 are NOT affected: their `mass:` reads ink as min < 200, which sees faint ink.

**M3 · L4 (all 12 gating keys) — ink lighter than the dark threshold, outside the band rows, is never read.**
- Heading bands are segmented on `dark` (RGB sum < 300) over y96-399.
- The level XOR covers only the band rows ±2 (`face xor:`) or the base bands' rows −3 … +3 (`crop` / `ink:`, today's face and
  011).
- A 50 % copy (grey 128, sum 384) or iOS's own full-ink (127,127,127) placed elsewhere in the scanned zone makes no band and
  is never XORed:
  - a 3×3 below the heading, +9 px;
  - a 30-px line at y276-368, +30.
- Fix basis: run the level XOR over the whole scanned zone (outside the predicted bands every pixel is background at both
  levels), or segment the bands on the level mask (min < 200) instead of `dark`. The passing pictures are flat white there
  (the C / D / U1 arms, wave54-final).

**M4 · L1 (all 3 gating keys; the 001 / 003 tally entries) — the same blind spot.**
- Lines are segmented on `core` (max channel < 110).
- `glyph_identity` reads each line's crop rows top−3 … bottom+3 only.
- Faint ink in the blank rows between word groups (a 3×3, +9) or below the text (a 30-px line, +30) is never read.
- The wave-54 tally probe (tops ±4) accepts these too, so the tally entries pass.
- Fix basis as M3: the zone x0-139 × y DIV0-599 outside the line crops must be background at both levels.

**M5 · L6 `[X]` (all 5 testable keys; their 5 tally entries) — fill is colour-exact and dark is bounded below only.**
- `crossings` needs the same fill on both ends. `extra` / `lost` read only the teal / yellow fill. The band count is a lower
  bound.
- So these pass:
  - black, purple or parent-yellow paint on white (no crossing);
  - faint copies of children or parents (a faint second child +48, whole-picture ghosts +2056 … +4405).
- Fix basis (`residual.py`): outside (fill ∪ dark band) of fixed ∪ base, the six U2 replays differ from wave54-final by
  **0 px** at grow 0. A "rest = base" read at tolerance 0 closes it.

**M6 · L6 `[PO]` — blue is bounded below only, and pink is counted inside one box.**
- Blue ink anywhere passes: the navbar ≥ 0.95 × ref, its sides ≥ 312, the overlap rows allow ≤ 8 blue. A 3×3 blue speck
  below the navbar's end adds +9.
- Pink passes outside x116-315 × y168-217.
- Green, black and faint ink are not read: whole-picture ghosts add +6294.
- Fix basis (`residual.py`): outside red ∪ blue of fixed ∪ base, the U1 replay differs from wave54-final by **0 px**.

All six are the same class, and **none is the strictly-additive class round 4 closed**. The round-4 reads count ink of the
key's own colour / level inside the key's own rows. These fakes put paint where no read of the key looks: in a colour or
alpha its masks do not see, or in rows its XOR does not cover. Under D31 these fakes join the standing set
(`probes-teeth-r5.py`), each pre-registered FAIL on exactly the keys `adjudicate5.out.txt` lists.

### Should-fix / notes

- **S1 — restate the claims.** PLAN §0 / D39 and `fix-r4.done.md` say the added-ink class is closed, with the 2-px floor as
  the only residual. That holds for round 4's variant set and for L5 / the L3 dir rows here; it does not hold for M1–M6.
- **S2 — the floor reproduces as named.** 85 acceptances add ≤ 2 px:
  - 1-px specks on every lane;
  - ≤ 2-px faint slivers on L4 / L5;
  - a 1-px strike-through remnant on L5 `[C]` android.
- **N1 — overpaints are listed apart.** These are acceptances whose paint covers another colour (L2 58, L3 37, L6 63, counting
  "both"). Every finding stands on background-only paint alone (§2.3).
- **N2 — the `[K]` / `I_OUT` / `ROW9_OUT` / `[C]` controls were not faked.** They are pixel-identity reads: any added pixel
  fails them by construction. The residual measurements suggest the same construction for M1, M2, M5 and M6.
- **N3 — the earlier nits are not re-checked here.** These are verify-r4 N1 (self-test records cite the script under test, not
  themselves) and fix-r3's queued nits; this pass did not re-check them.

## 4. Evidence (`plan-skeptic-round-5.verify.d/`)

**Scripts:**
- `fakes5.py` (the generator; `--scratch <dir> --groups Lx --out … --json …`);
- `adjudicate5.py` (per-key adjudication from the JSON);
- `residual.py` (the fix-basis measurement);
- `look5.py` (renders `look5.png`).

**Records:**
- `fakes5.L1…L6.out.txt` — every fake run, with each owning key's verdict;
- `fakes5.L1…L6.json` — the per-zone records, slimmed: probe lines kept for the acceptances; `adjudicate5.py` reproduces
  `adjudicate5.out.txt` from them byte-for-byte;
- `adjudicate5.out.txt`, `residual.out.txt`;
- `look5.png` — 12 representative acceptances, fixed crop left and accepted fake right;
- `checks.out.txt` — §1's commands and outputs.

The fake runs live only in the scratchpad.

## 5. Tree

- **Before → after:**
  - HEAD `611c9f93` → `611c9f93`;
  - index sha256 `fba384c3…` → `fba384c3…`;
  - `git status --porcelain --untracked-files=all` outside `tools/titan/results/wave55-*`: 0 → 0 lines;
  - `git stash list` empty;
  - no `wave55-plan-r4-*` worktree left registered. Both throwaways were removed: `wave55-plan-r4-verify5` (its runs / wpt
    symlinks first) and `fix-r4.teeth-before-after.sh`'s own `wave55-plan-r4-teeth`.
- **This pass's writes to the shared tree:** only the new `plan-skeptic-round-5.verify.md`, `plan-skeptic-round-5.verify.done.md`
  and `plan-skeptic-round-5.verify.d/`.
  - Each was installed atomically: a temp beside it, then `mv`.
  - No existing file was edited. No probe, `geometry-gate.py`, `probe-readout.mjs`, `expectations.json`, `watchlist.txt` or
    `PLAN.md` byte was written.
  - No `__pycache__`, dot-file or temp was left behind.
- **Concurrent writes by another session.** The 821 files of the two plan dirs were hashed before the first run and again
  after the checks:
  - every file this pass read is byte-identical, except `PLAN.md` (`35639d61…` → `10eeae07…`) and
    `../wave55-gate/_note.md`;
  - `../wave55-gate/wl7a/` (21 files) is new.
  - All three were written at 01:40:45 local by another session: the "[W-L7a] held exactly" paragraph appended at PLAN.md
    line 2671, with its records.
  - This pass wrote none of them.
  - `plan-prose.py --check` was re-run on the new PLAN.md: **HOLDS**, exit 0 (`checks.out.txt` §2b).
  - `expectations.json`, `watchlist.txt`, every probe and every record re-run in §1 are unchanged.

STATUS: PARTIAL — fix-r4's own claims all re-execute. The self-test HOLDS; `plan-prose --check` HOLDS, on the PLAN.md another session changed mid-run too; the watchlist is unmatched 0; plan-build regenerates byte-identically. probes-selfcheck.txt, the round-3 / round-4 teeth record, teeth-r4-equiv and both verdicts-preserved records re-run byte-for-byte (timestamp headers aside), and every fixed picture prints OK. But this round's independent strictly-additive sweep (901 fake runs, 2388 key reads) finds 704 acceptances adding > 2 px inside a key's own read zone, on 33 of 43 gating keys and 17 tally entries. Mechanisms: M1 L2 ×8; M2 L3 bidi / stack / dyn ×2 plus the vrl-rtl tally entry; M3 L4 ×12; M4 L1 ×3; M5 L6 [X] ×5; M6 L6 [PO]. The ink is faint (50 %), in a colour the key never reads, or in rows its XOR does not cover — each must-fix. Only L5 (6 keys) and the L3 dir rows (3) hold; safe-002 is untestable (N5). The 2-px floor (85) reproduces as named.
