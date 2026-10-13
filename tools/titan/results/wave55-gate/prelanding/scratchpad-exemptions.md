# Wave 55 pre-landing: scratchpad-path exemptions (records scrub)

Sources: `citations.md` item 4 ("scratchpad paths in lane-dir records") and `censuses-and-deliveries.md` §4
("Records hygiene"). The rule is PLAN §8 step 18: "`grep` for a scratchpad path returns nothing (lane notes' `TREES:` lines
excepted: round-1 N6)".

The scrub record is `scratchpad-scrub.txt`, written by `scratchpad-scrub.py`. Its header holds the script's sha256. It covers
43 files, 1476 lines and 4075 substitutions. Nothing else needed an exemption: every other hit in scope is a `TREES:` line,
which is exempt by design.

`<scratch>` below stands for the session scratchpad root. This file does not spell the root out.

## For PLAN §10 (paste as the next free item number; it is 2 at HEAD 4e789976)

2. **Scratchpad-path exemption: the two REMOVED lines of `unit-U3.patch` (2026-10-12, pre-landing records scrub).**
   - **Run.** None. This is a records decision. No device, carrier, prediction, floor or unit changes, so there is no
     `plan-build.py` row and no regeneration.
   - **Rule.** §8 step 18 / §0 "No scratchpad pointers".
   - **The lines.** `tools/titan/results/wave55-instruments/unit-U3.patch` (sha256
     `5887ec66badca2aa904564729bb7854467347200c7dbd769ead1d69e4fe195c7`) has two REMOVED lines. Both are in its first hunk,
     which patches the tracked record `tools/titan/results/wave55-gate/ab-arms55.selftest.out.txt`
     (`index 10aeeaf9..f059aa20`, HEAD lines 1 and 25). The sha256 above is the one pinned on the
     `patch: unit-U3.patch sha256` line in `wave55-instruments/_note.md`.
     - Patch line 6 is the base record's header: `… scratch <scratch>/r3/tk/abst`.
     - Patch line 15 is T3's `(records in …)`. The self-test's `cut -c1-110` shortened the stage dir
       (`$TMPDIR` = `<scratch>/…/tmp/`) to the first 15 characters of the root. So only line 6 matches a grep for the
       session-id path. Line 15 is listed for completeness.
   - **Why they stay.** A `-` line is `git apply` context: it must equal HEAD's bytes of the tracked record. Changing it has
     three effects:
     - The patch's sha256 changes, so `land-units.sh --check` refuses the note's `patch:` line.
     - `git apply` no longer applies the patch.
     - The patch has to be re-cut under the §8 step 3 resume rule. That changes no landed byte.

     This is rule (a) of the scrub: a `*.patch` is never edited.
   - **What lands.** U3 replaces both lines. On the `+` side:
     - the header names the re-run's macOS `$TMPDIR` mktemp dir (`/private/var/folders/…/T/tmp.…`);
     - T3 reads `(records in /private/var/fo)`.

     Neither is a session scratchpad path. Once U3 lands, the tracked record no longer carries the pointer. The only copy
     left is the patch file itself, a lane-dir record that the records commit force-adds.
   - **Caveat.** Until U3 lands, and on any tree that drops U3 (a revert under §7, `--hold L7:U3`, or an A/B arm whose
     `drops` include U3), HEAD's `wave55-gate/ab-arms55.selftest.out.txt` lines 1 and 25 carry the same two strings. That
     file belongs to the gate dir, which is outside this scrub. A step-18 grep run on such a tree lists it, and lists it
     correctly: it is the base bytes that U3 removes.
   - **Step-18 reading.** `grep -rn '<session-id>/scratchpad'` over the wave-55 lane dirs in this scrub's scope should
     return only:
     - lane notes' and skeptics' `TREES:` lines (11 in L1–L4 and L7: `_note.md`, `skeptic.md` and
       `wave55-positioned-parent-slot/skeptic/mutation-log.txt` line 1);
     - `wave55-instruments/unit-U3.patch:6`, which is this item.
   - **Not decided here.** [W-L7a] pins the sha256 of `unit-U2.patch` and `unit-U5.patch`, not `unit-U3.patch`, so it is
     unaffected.

## Still carrying the path outside this scrub's scope (owned elsewhere; not touched)

These counts are hits that are not `TREES:` lines, read on 2026-10-12 against the working tree.

- **L6 `wave55-android-paint-order/` and L5 `wave55-counter-suffix-rows/`.** Other agents own these and they were not
  edited. A read-only grep at about 09:26 CEST found only `TREES:` lines: L6 `_note.md` lines 1–4, and L5 `_note.md` lines 1–2
  plus `skeptic.md` line 1. Both dirs can still change until their owners finish, so re-grep them before the records commit.
- **`wave55-gate/` (outside `wl7a/`).**
  - `ab-arms55.selftest.out.txt`: one full path plus the 15-character remnant. It is tracked, and U3 removes both (see the
    caveat above).
  - `toolkit-proofs.txt`: 2 hits.
- **`wave55-plan/`.**
  - `land-units.selftest.out.txt`: 10.
  - `fix-r3.guard-proof.out.txt`: 2.
  - `plan-skeptic-round-1.instrument-teeth.d/`: `ab-dry.out.txt` 2, `lu-check.run1.land-units.out.txt` 2, and
    `gate-out/combo-L1…L6.txt` 1 each.
- **Earlier waves' committed records** (wave 52–54) carry the same session root, for example
  `wave54-ua-heading-face/skeptic/export-B.xcodebuild.log` with 1824 hits. Step 18 governs this wave's records. These are
  history, listed only so that a tree-wide grep is not misread.
