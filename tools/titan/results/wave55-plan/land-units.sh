#!/usr/bin/env bash
# land-units.sh — wave 55: land the seven lanes' DELIVERED PATCHES as PLAN.md §4's revert units, one commit each, in the
# ORDER table below: the lane records first, then one commit per revert unit, each seam patch applied right before its
# unit's commit.
#
# Model: tools/titan/results/wave54-plan/land-units.sh. Wave 54's lanes edited the shared tree and that script committed
# their paths. Wave 55's lanes never edit the shared tree; each delivers, in its lane dir tools/titan/results/wave55-<lane>/:
#   unit-<U>.patch  the unit's owned files (no such file for a seam-only unit: L5 U2i-ios, U2i-android)
#   seam-<n>.patch  the unit's hunk in ONE seam file + its patch-borne test files (seam-registry.md; never a direct edit)
#   _note.md        one section per revert unit (every unit commit's body points at it). Plan-skeptic round 1 (process-holes
#                   M3): the note must end `STATUS: COMPLETE`, carry a `TREES:` line (written FIRST, before any build), and name
#                   every delivered patch on a line `patch: <name> sha256 <64 hex>` written after the final restore — --check
#                   errors on a missing / non-COMPLETE STATUS, a missing TREES: line, a lane-dir patch the note does not name,
#                   a sha256 that differs, a note line naming no file, and a *.patch the ORDER table does not know (a stale
#                   dead attempt). So a limit-killed lane never lands: it is resumed (PLAN §8 step 3) and re-cut first.
# Each patch is cut on the tree it lands on (seam-registry.md §2); one cut on 0d0d3a3d applies with offsets wherever no
# earlier patch touched its context. SeamReachabilityTest.kt's three writers (round 1, M1) are each CUT ON HEAD AT A PINNED
# ANCHOR: L4 after "content/PseudoGeneratedBox.kt", (:159), L5 after "images/ImageCandidateChain.kt", (:167), L3 after
# "transforms/TransformInheritance.kt", (:182) — so they land in any order and every earlier unit still reverts.
# The ORDER (PLAN.md §4; seam-registry.md §6; briefs-audit.md C7 / C10 / C12 / C13):
#   00     RECORDS: the selected lane dirs (+ the six wave-54 out/ files instruments.md:300 hands to "the records commit",
#          force-added) → 1 commit BEFORE any unit, so every unit commit holds code paths only and reverts alone
#   01-09  L7 instruments  U1 (optional) U2 U3 (optional) U4 U6-compose U6-swift U6-web U6-tooling U5  (instruments §4's
#          order; tooling first as in wave 54; U5 only after [W-L7a] holds, else --hold L7:U5). U1 and U3 were written at
#          planning (wave55-plan/flex-zero-gap-rules.*, wave55-gate/ab-arms55.*): with no patch their files must be tracked
#   10-12  L6 android-paint-order  U1 U2 U3                      (C13: L6 U2's StyleApplier lines land before L2)
#   13-16  L4 ua-heading-margin  U1-android U1-ios U2-android U2-ios   (kt seam 1 → 3, swift seam 2 → 4; C10 first)
#   17-18  L2 cbb-android  U1 U2     (group "cbb": U1 is committed only after U2 is checked on top of it; never apart)
#   19-20  L3 positioned-parent-slot  U1 U2                      (C10 second writer of SeamReachabilityTest.kt)
#   21-25  L5 counter-suffix-rows  U1-ios U2o-ios U2i-ios U2o-android U2i-android  (U2o before U2i; C10 third writer;
#          round 1 S1: a U2o revert reverts D first — its SUFFIX NOTE is true only with U2o)
#   26     L1 hyphen-ios-linebox  NB-ios                         (swift seam 1: after L5 seam 3, before L5 seam 6)
#   27-28  L5  U3-ios (swift seam 6, re-anchored after L1)  D (comment-only; C7: after U2o)
#   29     L1  NB-ios-b — CONTINGENCY: landed only by --contingency L1:NB-ios-b, after the stage-1 probe's `anchor:` trigger
#   30     L5  U1-ios-LRM — CONTINGENCY (plan-skeptic round 2, process-holes S2): landed only by --contingency L5:U1-ios-LRM,
#          after the stage-1 probe's [H] ios trigger (the LRM fallback re-cut of U1-ios, pre-registered like NB-ios-b)
# Round 2 (process-holes S3): a lane dir may also hold `fix-S1-<U>.patch` for each unit U of the lane (PLAN §8 step 8: the S1
# skeptic's fixes, landed by the orchestrator AFTER the units with `git apply --index` + a named-path commit, never by this
# script) — each still needs its `patch: <name> sha256 <64 hex>` line; any other unknown *.patch is refused.
# So ComponentRenderer.kt takes L4 s1 → L4 s3 → L2 s1 → L3 s1 → L5 s4 → L5 s5 and ComponentRenderer.swift takes L4 s2 →
# L4 s4 → L5 s1 → s2 → s3 → L1 s1 → L5 s6: seam-registry.md §6 verbatim. Every ordering rule is ASSERTED (CONSTRAINTS).
# landing-manifest.json beside this script is GENERATED from the two tables (--emit-manifest; --check fails on drift).
#
# MODES (exit 0 ok · 1 check found defects · 2 refused, nothing touched · 3 stopped mid-landing, tree back at the last
# landed commit · 70 internal guard: a mutating call reached in --check mode)
#   land-units.sh --check [opts]       READ-ONLY. Prints the order; verifies every expected patch and lane dir exists;
#                                      each patch with `git apply --check` on the CURRENT tree alone, then the whole series
#                                      cumulatively (one concatenated input, still `git apply --check`); the seam and
#                                      ownership rules; that every path a patch touches is clean at HEAD; the C12 revert
#                                      dry-runs (every unit's revertOrder reverse-applied on the fully landed series) in an
#                                      EXPORT TREE — copies of the touched files in a private mktemp dir outside the repo,
#                                      where `git apply` runs as a plain patch tool; the manifest is in sync.
#   land-units.sh [opts]               THE LANDING. Refuses (2) on a gate / probe / A/B arm running in this tree, a staged
#                                      index, a dirty touched path, or a lane file written < 120 s ago; runs every --check
#                                      read first and lands nothing unless it is clean; then the records commit and one
#                                      commit per unit (`git apply --index --check`, `git apply --index`, staged set ==
#                                      the paths its patches name, `git commit`). A failure mid-unit reverse-applies that
#                                      unit's patches (`git apply --index -R`): nothing is ever rewound with checkout / rm /
#                                      reset, and no lane edit can be wiped (the lanes' work lives in their patches).
#   land-units.sh --contingency <L:U> [--check]   ONE pre-registered contingency unit — L1:NB-ios-b (needs NB-ios landed) or
#                                      L5:U1-ios-LRM (needs U1-ios landed): records (if any) + 1 commit (round 2, S2).
#   land-units.sh --emit-manifest      (re)write landing-manifest.json from the tables; touches nothing else.
#   land-units.sh --print-order        print the two tables; touches nothing.
# opts: --hold L4:U2-android,L7:U5   hold units (a group is held whole; a held unit's dependents must be held too)
#       --only L1,L4                 land / check only these lanes (records: their dirs only) — the rehearsal knob
#       --from L3:U1                 resume after a stop: every earlier selected unit must already be in HEAD's history
#       --root DIR                   the work tree to act on (default: the repo holding this script) — a throwaway
#                                    worktree for rehearsals
#       --no-revert-dry-runs         skip the export-tree revert dry-runs
# Rehearsal-only fault knob: LAND_FAULT_AT=<L:U> makes the real path fail after applying that unit (before its commit),
# to exercise the reverse-apply restore. Run only on an idle host, after every pre-landing window, after re-reading
# every lane's "## Fix pass" section; tee stdout to $TMPDIR and copy it into wave55-gate/ AFTER the run (records after
# the tree is in its final state). Wave-54 lesson (BACKLOG; wave skill): "a --check mode that still runs the real
# script's git checkout / rm is not a check" — here every mutating git call goes through tree_mut(), which exits 70 in
# --check mode, and nothing in this script ever runs checkout, reset, stash, clean or rm on the tree.
set -uo pipefail
export LC_ALL=C

HERE="$(cd "$(dirname "$0")" && pwd -P)"
MANIFEST="$HERE/landing-manifest.json"
R=tools/titan/results
CO="Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
FRESH_SECS=120
SEAM_kt=runtimes/compose/src/main/java/com/styleconverter/runtime/core/renderer/ComponentRenderer.kt
SEAM_swift=runtimes/swiftui/Sources/StyleConverterRuntime/Renderer/ComponentRenderer.swift
SEAM_tsx=apps/web-harness/src/sdui/ComponentRenderer.tsx
SEAM_xf=tools/titan/extract-fixture.mjs
SEAMS_ALL="$SEAM_kt $SEAM_swift $SEAM_tsx $SEAM_xf"
# The only paths two lanes may both touch (seam-registry.md §1, §2, §5; briefs-audit.md §1 "0 paths owned by two lanes").
SHARED_OK="$SEAM_kt $SEAM_swift runtimes/compose/src/test/java/com/styleconverter/runtime/core/renderer/SeamReachabilityTest.kt"
# instruments.md:300: "The six gitignored L4 dry-run out/ files (wave54-S1/fix/ignored-evidence.out-dirs.txt; all six
# exist, untracked) are force-added in the records commit." Committed with L7's records (skipped when --only drops L7).
FORCE_ADD="tools/titan/results/wave54-oof-layout/fix/dry/h1h2/out/expectations.json
tools/titan/results/wave54-oof-layout/fix/dry/h1h2/out/watchlist.txt
tools/titan/results/wave54-oof-layout/fix/dry/h2/out/expectations.json
tools/titan/results/wave54-oof-layout/fix/dry/h2/out/watchlist.txt
tools/titan/results/wave54-oof-layout/reverify/dry/h1h2/out/expectations.json
tools/titan/results/wave54-oof-layout/reverify/dry/h1h2/out/watchlist.txt"
RECORDS_SUBJECT="wave55 lane records: the lanes' notes, skeptic reports, fix passes, unit and seam patches, censuses and mutation logs (L1 hyphen-ios-linebox, L2 cbb-android, L3 positioned-parent-slot, L4 ua-heading-margin, L5 counter-suffix-rows, L6 android-paint-order, L7 instruments)"

# ── THE ORDER TABLE ── seq | lane | lane slug (dir tools/titan/results/wave55-<slug>/) | unit | unit patch ('-' = seam-only)
#    | seam patches in apply order, each @ its seam file (kt / swift / tsx / xf) | needs (same-lane units that must land
#    first) | revertOrder (PLAN / plan-build.py lanes.*.revertUnits) | kind (unit · optional · contingency) | group | commit
#    subject (after "wave55 <lane> <unit>: ") | note (into the commit body)
table_text() { cat <<'EOF'
01|L7|instruments|U1|unit-U1.patch|-|-|U1|optional|-|the flex-gap probe's wave-55 copy (+ the blue row-rule horizontal extent), its fakes and outputs, re-executed — records only|OPTIONAL: no patch when the re-execution left the copy (tracked since a2a37a8e) byte-identical (instruments §4 U1); then its files must be tracked at HEAD (checked). C4 / X6: its flex-gap-decorations-033 keys are CONTROL.
02|L7|instruments|U2|unit-U2.patch|-|-|U2|unit|-|tools/titan/wire-differential.{sh,mjs} + pins — a gate-flag wire differential that can fail (identical / renumbered / content-changed / one-sided; refuses its own checkout)|Tooling; executed by no gate. [W-L7a] runs it before U5 may land.
03|L7|instruments|U3|unit-U3.patch|-|-|U3|optional|-|ab-arms55.sh + its self-test — the A/B arm runner writes its records after the tree is restored|OPTIONAL: the gate-toolkit stage wrote the runner into wave55-gate/ at planning (wave55-gate/toolkit.done.md); no patch when it is committed with the gate records, and then its files must be tracked at HEAD (checked). The runner of every A/B arm (L1 drop-NB-ios, L4 drop-L4-U2, L6 drop-L6-U2).
04|L7|instruments|U4|unit-U4.patch|-|-|U4|unit|-|spec 03 §4.1 renderer-latitude paragraph and the web citations (InertOutOfFlowWordJoin.ts, InlineRuns.ts; InlineRunsLatitudeCitation pins)|Comments, one console string and one spec paragraph: no pixel, no wire byte (schema/conformance byte-identical).
05|L7|instruments|U6-compose|unit-U6-compose.patch|-|-|U6-compose|unit|-|pins C1-C7 on the wave-54 Compose hyphenate-character and out-of-flow containing-block code — tests only|Tests only.
06|L7|instruments|U6-swift|unit-U6-swift.patch|-|-|U6-swift|unit|-|pins S1-S6 on the wave-54 SwiftUI hyphenate-character cascade and out-of-flow containing-block branches — tests only|Tests only.
07|L7|instruments|U6-web|unit-U6-web.patch|-|-|U6-web|unit|-|pins W1-W6 on InertOutOfFlowWordJoin's branches — tests only|Tests only.
08|L7|instruments|U6-tooling|unit-U6-tooling.patch|-|-|U6-tooling|unit|-|pins T1-T2 on label-chrome-check — tests only|Tests only.
09|L7|instruments|U5|unit-U5.patch|-|-|U5|unit|-|the marker paint decline lists maskImage / mixBlendMode (bidi-marker-paint.mjs MARKER_PAINT_INITIAL; bidi-bake.test.mjs S6-2) — predicted wire-identical|Lands only after [W-L7a] holds (C3: U5's claim is wire-only); otherwise land with --hold L7:U5.
10|L6|android-paint-order|U1|unit-U1.patch|-|-|U2,U1|unit|-|PO-selfpaint-android: a Compose self-painted border band paints behind its content (BehindContentBorder; CSS 2.1 Appendix E) — device-gated at stage 1|revertOrder [U2, U1]: U2's fast path calls U1's painter.
11|L6|android-paint-order|U2|unit-U2.patch|-|U1|U2|unit|-|PO-sides-android: the legacy Compose border sides move inner of the background and paint behind content (BorderSideApplier, BordersFacade, three StyleApplier call-site lines)|C13: lands before L2, whose StyleApplier chain claims cite symbols, not lines. C2: never reverted for another lane's carrier.
12|L6|android-paint-order|U3|unit-U3.patch|-|-|U3|unit|-|FO-pin: filter then clip on the verbatim clip-path-filter-order wire (EffectsClipFilterOrderTest) — test only|Test only.
13|L4|ua-heading-margin|U1-android|unit-U1-android.patch|seam-1.patch@kt|-|U2-android,U1-android|unit|-|the UA .67em-family block margin of an out-of-flow heading under WPT capture on Compose (UaPositionedHeadingMargin; HTML §15.3.6, css-position-3 §2.1)|Seam 1 (ComponentRenderer.kt :1157-1165) lands before seam 3; revertOrder [U2-android, U1-android] (C12 dry-run).
14|L4|ua-heading-margin|U1-ios|unit-U1-ios.patch|seam-2.patch@swift|-|U2-ios,U1-ios|unit|-|the SwiftUI twin of the out-of-flow heading's UA block margin (UAPositionedHeadingMargin; the mergedPreUA hoist in mergedProperties)|Seam 2 (ComponentRenderer.swift :340-356) lands before seam 4; revertOrder [U2-ios, U1-ios] (C12 dry-run).
15|L4|ua-heading-margin|U2-android|unit-U2-android.patch|seam-3.patch@kt|U1-android|U2-android|unit|-|the held folded-host heading face restored on Compose (UAHeadingFoldGate) — 005 / 006 android stayDegenerateEvenIfPass|D1: GO; --hold L4:U2-android is the GO-SMALL fallback. Seam 3 sits on seam 1's 3-line context.
16|L4|ua-heading-margin|U2-ios|unit-U2-ios.patch|seam-4.patch@swift|U1-ios|U2-ios|unit|-|the held folded-host heading face restored on SwiftUI (UAHeadingFoldGate.swift; seam 4 on top of seam 2)|Seam 4 reads seam 2's mergedPreUA: apply 2 then 4, revert 4 then 2.
17|L2|cbb-android|U1|unit-U1.patch|seam-1.patch@kt|-|U2,U1|unit|cbb|CBB-android re-landed (5b333c8a): under WPT capture a content-box box publishes its declared size to its children's containing block (ContainingBlockBands; CSS 2.1 §10.1-§10.2)|Never without U2 (cbb-android §4): U2 is checked on top before this commit and is the next commit; revert U2 then U1, together. C8: the dark-stage breadcrumb row rides in DynamicValueResolverTest.
18|L2|cbb-android|U2|unit-U2.patch|-|U1|U2,U1|unit|cbb|the in-flow percentage of a content-box box gets its containing block's content band at layout time (PercentContentBoxBand; CSS 2.1 §10.2)|Never without U1; revert U2 then U1, together.
19|L3|positioned-parent-slot|U1|unit-U1.patch|seam-1.patch@kt|-|U2,U1|unit|-|composed-capture engagement: a positioned parent's flow slot mounts under composed capture for LTR parents (PositionedParentSlotGate) — device-gated at stage 1|revertOrder [U2, U1]. PLAIN (no !parentRtl guard) never ships.
20|L3|positioned-parent-slot|U2|unit-U2.patch|-|U1|U2|unit|-|the RTL physical-left origin of a positioned parent's flow slot (PositionedParentRtlOrigin; the gate's RTL branch)|U2 is the RTL branch of U1's gate file: a U1 revert reverts U2 first.
21|L5|counter-suffix-rows|U1-ios|unit-U1-ios.patch|seam-1.patch@swift|-|U1-ios|unit|-|a list marker whose natural direction differs from its item's is bidi-isolated on SwiftUI (ListMarkerBidi; css-lists-3 §3.1.1, css-writing-modes-4 §2.2, UAX #9)|C12: its revert is dry-run with U2i-ios applied (ComponentRenderer.swift :4646 / :4650).
22|L5|counter-suffix-rows|U2o-ios|unit-U2o-ios.patch|seam-2.patch@swift|-|D,U2i-ios,U2o-ios|unit|-|a U+3001-suffixed outside marker hangs with no stand-in gap on SwiftUI (ListMarkerSuffixGap; css-counter-styles-3 §6)|U2o lands before U2i; a U2o revert reverts D, then U2i, first (round 1 S1: D's SUFFIX NOTE is true only with U2o).
23|L5|counter-suffix-rows|U2i-ios|-|seam-3.patch@swift|U2o-ios|U2i-ios|unit|-|the inside-marker HStack takes the same suffix gap on SwiftUI (seam only)|Seam only: consumes ListMarkerSuffixGap (U2o-ios).
24|L5|counter-suffix-rows|U2o-android|unit-U2o-android.patch|seam-4.patch@kt|-|D,U2i-android,U2o-android|unit|-|the Compose twin of the outside-marker suffix gap (ListMarkerSuffixGap.kt)|C10: its SeamReachabilityTest.kt line rides seam 4, cut on HEAD after "images/ImageCandidateChain.kt", (round 1 M1). A U2o revert reverts D, then U2i, first (S1).
25|L5|counter-suffix-rows|U2i-android|-|seam-5.patch@kt|U2o-android|U2i-android|unit|-|the Compose inside-marker Row takes the same suffix gap (seam only)|D5: css3-counter-styles-005 android -0.0018 is pre-registered (down-preregistered).
26|L1|hyphen-ios-linebox|NB-ios|unit-NB-ios.patch|seam-1.patch@swift|-|NB-ios|unit|-|a declared-normal WPT run on iOS lays out on Blink's rounded line box (NormalLineBox; CSS 2.1 §10.8.1) — device-gated at stage 1|Additive on the wave-54 U3b: revert NB-ios BEFORE U3b, never U3b alone.
27|L5|counter-suffix-rows|U3-ios|unit-U3-ios.patch|seam-6.patch@swift|-|U3-ios|unit|-|tabular digits on a SwiftUI label run (font-variant-numeric tabular-nums as Font.monospacedDigit(); css-fonts-4)|Seam 6 lands after L1's seam 1 (seam-registry §2), re-anchored by content.
28|L5|counter-suffix-rows|D|unit-D.patch|-|U2o-ios,U2o-android|D|unit|-|comment-only: the list-marker RTL and twin notes corrected, and the counter-style-table SUFFIX NOTE (C7)|C7: after U2o (a U+3001 suffix has no stand-in gap). No pixel, no wire byte.
29|L1|hyphen-ios-linebox|NB-ios-b|unit-NB-ios-b.patch|seam-1b.patch@swift|NB-ios|NB-ios-b|contingency|-|CONTINGENCY — a pinned declared-normal run pads 0 above and 2x half-leading below (NormalLineBox.verticalPads)|Lands only with --contingency L1:NB-ios-b, after the stage-1 probe prints `anchor: group 1 line 1 offset 1` and nothing else; NB-ios's revertOrder then becomes [NB-ios-b, NB-ios].
30|L5|counter-suffix-rows|U1-ios-LRM|unit-U1-ios-LRM.patch|-|U1-ios|U1-ios-LRM|contingency|-|CONTINGENCY — the LRM fallback re-cut: the painted marker is wrapped in LRM … LRM instead of LRI … PDI (no isolate run, so no fallback face grows the marker's line box)|Lands only with --contingency L5:U1-ios-LRM, after the stage-1 probe prints [H] counter-suffix ios WRONG (or H-low-shaped) while [C] / [T] / [K] ios are OK; it edits the file U1-ios creates, so U1-ios's revertOrder then becomes [U1-ios-LRM, U1-ios].
EOF
}
# (bash 3.2 mis-parses a quoted heredoc inside $( ), so each table is a function)
TABLE="$(table_text)"
# ── OPTIONAL UNITS: the files that must already be tracked at HEAD when the unit delivers no patch (a planning stage wrote
#    them): L:U | paths. A delivered patch instead must CREATE or MODIFY them (an untracked loose copy then blocks it).
optional_files_text() { cat <<'EOF'
L7:U1 | tools/titan/results/wave55-plan/flex-zero-gap-rules.geometry.py tools/titan/results/wave55-plan/flex-zero-gap-rules.fakes-run.sh tools/titan/results/wave55-plan/flex-zero-gap-rules.geometry.out.txt
L7:U3 | tools/titan/results/wave55-gate/ab-arms55.sh tools/titan/results/wave55-gate/ab-arms55.selftest.sh tools/titan/results/wave55-gate/ab-arms55.selftest.out.txt
EOF
}
OPTFILES="$(optional_files_text)"
optfiles_of() { echo "$OPTFILES" | awk -F'|' -v k="$1" '{gsub(/^[ \t]+|[ \t]+$/, "", $1)} $1==k {print $2}'; }

# ── ORDERING CONSTRAINTS (asserted on the table in every mode): A < B | why ──
constraints_text() { cat <<'EOF'
L6:U2 < L2:U1 | C13 (briefs-audit §8): L6 U2 adds StyleApplier.kt lines above L2's quoted chain, so L2 lands on the tree that holds them
L4:U2-android < L3:U1 | C10: SeamReachabilityTest.kt's writers land L4 → L3 → L5 (§4 order; each line patch-borne and cut on HEAD at its pinned anchor — round 1 M1 — so any order applies)
L3:U2 < L5:U2o-android | C10 (L5's line rides seam 4)
L4:U2-android < L2:U1 | seam-registry §6, ComponentRenderer.kt: L4 s1 → L4 s3 → L2 s1 → L3 s1 → L5 s4 → L5 s5
L2:U1 < L3:U1 | seam-registry §6, ComponentRenderer.kt
L3:U1 < L5:U2o-android | seam-registry §6, ComponentRenderer.kt
L5:U2o-android < L5:U2i-android | seam-registry §6, ComponentRenderer.kt; counter-suffix-rows §4 "land order U2o before U2i"
L4:U1-android < L4:U2-android | seam-registry §1: seam 1 → seam 3 (one shared 3-line context)
L4:U1-ios < L4:U2-ios | seam-registry §2: seam 2 → seam 4 (seam 4 reads seam 2's mergedPreUA)
L4:U2-ios < L5:U1-ios | seam-registry §6, ComponentRenderer.swift: L4 s2 → L4 s4 → L5 s1 → s2 → s3 → L1 s1 → L5 s6
L5:U1-ios < L5:U2o-ios | seam-registry §6, ComponentRenderer.swift
L5:U2o-ios < L5:U2i-ios | seam-registry §6, ComponentRenderer.swift; counter-suffix-rows §4
L5:U2i-ios < L1:NB-ios | seam-registry §6, ComponentRenderer.swift
L1:NB-ios < L5:U3-ios | seam-registry §2: "land L1 first and re-anchor seam 6 by content"
L5:U2o-ios < L5:D | C7: unit D after U2o
L5:U2o-android < L5:D | C7: unit D after U2o
L2:U1 < L2:U2 | cbb-android §4: land U1 → U2, never apart (group cbb)
L3:U1 < L3:U2 | positioned-parent-slot §4: landing and revert order U1 then U2
L6:U1 < L6:U2 | android-paint-order §4: U2's fast path calls U1's painter
L7:U1 < L7:U2 | instruments §4: U1 → U2 → U3 → U4 → U6-* → U5
L7:U2 < L7:U3 | instruments §4
L7:U3 < L7:U4 | instruments §4
L7:U4 < L7:U6-compose | instruments §4
L7:U6-tooling < L7:U5 | instruments §4: U5 last of L7 (after [W-L7a])
L7:U5 < L6:U1 | PLAN §4: tooling first, as wave 54
L1:NB-ios < L1:NB-ios-b | hyphen-ios-linebox §9 R1: the contingency sits on NB-ios
L5:U1-ios < L5:U1-ios-LRM | counter-suffix-rows §9 R1 (round 2, S2): the LRM re-cut sits on U1-ios (it edits ListMarkerBidi.swift, which U1-ios creates)
EOF
}
CONSTRAINTS="$(constraints_text)"

# ── helpers ──────────────────────────────────────────────────────────────────────────────────────────────────────────────
trim() { local s="$1"; s="${s#"${s%%[![:space:]]*}"}"; s="${s%"${s##*[![:space:]]}"}"; printf '%s' "$s"; }
ERRS=0; WARNS=0
err()  { ERRS=$((ERRS+1));  printf '  ERROR   %s\n' "$*"; }
warn() { WARNS=$((WARNS+1)); printf '  WARN    %s\n' "$*"; }
die()  { printf 'land-units: %s\n' "$1" >&2; exit "${2:-2}"; }

N=0
while IFS= read -r line; do
  t="$(trim "$line")"; [[ -z "$t" || "$t" == \#* ]] && continue
  IFS='|' read -r f1 f2 f3 f4 f5 f6 f7 f8 f9 f10 f11 f12 <<<"$line"
  SEQ[N]=$(trim "$f1"); LANE[N]=$(trim "$f2"); SLUG[N]=$(trim "$f3"); UNIT[N]=$(trim "$f4"); UPATCH[N]=$(trim "$f5")
  SEAMSPEC[N]=$(trim "$f6"); NEEDS[N]=$(trim "$f7"); REV[N]=$(trim "$f8"); KIND[N]=$(trim "$f9"); GROUP[N]=$(trim "$f10")
  SUBJ[N]="wave55 ${LANE[N]} ${UNIT[N]}: $(trim "$f11")"; NOTE[N]=$(trim "${f12:-}")
  N=$((N+1))
done <<<"$TABLE"
idx() { local k; for ((k=0; k<N; k++)); do [[ "${LANE[k]}:${UNIT[k]}" == "$1" ]] && { echo "$k"; return; }; done; echo -1; }
dir_of() { echo "$R/wave55-${SLUG[$1]}"; }
key_of() { echo "${LANE[$1]}:${UNIT[$1]}"; }
csv() { [[ "$1" == "-" ]] && return; echo "$1" | tr ',' '\n'; }
# The patches of unit i in APPLY order: the unit patch, then its seam patch(es) ("a seam patch applied right before its
# unit's commit"). Repo-relative paths (the script runs from the work-tree root).
patches_of() { local i=$1 d s; d="$(dir_of "$i")"; [[ "${UPATCH[i]}" != "-" ]] && echo "$d/${UPATCH[i]}"
  for s in $(csv "${SEAMSPEC[i]}"); do echo "$d/${s%@*}"; done; }
seamfile_of_tok() { case "$1" in kt) echo "$SEAM_kt";; swift) echo "$SEAM_swift";; tsx) echo "$SEAM_tsx";; xf) echo "$SEAM_xf";; *) echo "";; esac; }

# ── the table is checked in every mode (a table edit that breaks a rule fails before anything else) ───────────────────
table_check() {
  local i j k a b n prev=0 bad=0 why
  for ((i=0; i<N; i++)); do
    [[ "${SEQ[i]}" =~ ^[0-9]+$ ]] && (( 10#${SEQ[i]} > prev )) || { echo "  TABLE   seq ${SEQ[i]} is not increasing"; bad=1; }
    prev=$((10#${SEQ[i]}))
    [[ "${KIND[i]}" =~ ^(unit|optional|contingency)$ ]] || { echo "  TABLE   $(key_of $i): unknown kind ${KIND[i]}"; bad=1; }
    [[ "$(idx "$(key_of $i)")" == "$i" ]] || { echo "  TABLE   $(key_of $i) appears twice"; bad=1; }
    [[ "${UPATCH[i]}" == "-" || "${UPATCH[i]}" == "unit-${UNIT[i]}.patch" ]] || { echo "  TABLE   $(key_of $i): unit patch must be unit-${UNIT[i]}.patch"; bad=1; }
    [[ "${UPATCH[i]}" != "-" || "${SEAMSPEC[i]}" != "-" ]] || { echo "  TABLE   $(key_of $i) has no patch at all"; bad=1; }
    for s in $(csv "${SEAMSPEC[i]}"); do
      [[ "${s%@*}" =~ ^seam-[0-9]+b?\.patch$ && -n "$(seamfile_of_tok "${s#*@}")" ]] || { echo "  TABLE   $(key_of $i): bad seam spec $s"; bad=1; }
    done
    for n in $(csv "${NEEDS[i]}"); do j=$(idx "${LANE[i]}:$n")
      (( j >= 0 && j < i )) || { echo "  TABLE   $(key_of $i) needs ${LANE[i]}:$n, which does not precede it"; bad=1; }; done
    for n in $(csv "${REV[i]}"); do (( $(idx "${LANE[i]}:$n") >= 0 )) || { echo "  TABLE   $(key_of $i): revertOrder names unknown ${LANE[i]}:$n"; bad=1; }; done
    [[ ",${REV[i]}," == *",${UNIT[i]},"* ]] || { echo "  TABLE   $(key_of $i): its revertOrder does not contain itself"; bad=1; }
  done
  for ((i=0; i<N; i++)); do
    if [[ "${KIND[i]}" == optional ]]; then [[ -n "$(optfiles_of "$(key_of $i)")" ]] || { echo "  TABLE   optional $(key_of $i) has no OPTIONAL-files line"; bad=1; }
    elif [[ -n "$(optfiles_of "$(key_of $i)")" ]]; then echo "  TABLE   $(key_of $i) has an OPTIONAL-files line but is not optional"; bad=1; fi
  done
  # a seam patch name is unique within its lane dir
  for ((i=0; i<N; i++)); do for s in $(csv "${SEAMSPEC[i]}"); do for ((j=i+1; j<N; j++)); do
    [[ "${SLUG[j]}" == "${SLUG[i]}" && ",${SEAMSPEC[j]}," == *",${s%@*}@"* ]] && { echo "  TABLE   ${s%@*} claimed twice in ${SLUG[i]}"; bad=1; }
  done; done; done
  # a group's members are adjacent (nothing lands between them)
  for ((i=1; i<N; i++)); do [[ "${GROUP[i]}" != "-" ]] || continue
    for ((j=0; j<i-1; j++)); do [[ "${GROUP[j]}" == "${GROUP[i]}" ]] && { a=1; for ((k=j+1; k<i; k++)); do [[ "${GROUP[k]}" == "${GROUP[i]}" ]] || a=0; done
      (( a )) || { echo "  TABLE   group ${GROUP[i]} is not contiguous"; bad=1; }; }; done; done
  while IFS= read -r line; do
    t="$(trim "$line")"; [[ -z "$t" ]] && continue
    a="$(trim "${t%%<*}")"; b="${t#*<}"; why="$(trim "${b#*|}")"; b="$(trim "${b%%|*}")"
    i=$(idx "$a"); j=$(idx "$b")
    if (( i < 0 || j < 0 )); then echo "  ORDER   constraint names an unknown unit: $t"; bad=1
    elif (( i >= j )); then echo "  ORDER   VIOLATED: $a must land before $b — $why"; bad=1; fi
  done <<<"$CONSTRAINTS"
  return $bad
}

emit_manifest() { # stdout: the manifest JSON (indent 1), generated from the two tables
  LU_TABLE="$TABLE" LU_CONSTRAINTS="$CONSTRAINTS" LU_FORCE="$FORCE_ADD" LU_RECORDS="$RECORDS_SUBJECT" LU_SHARED="$SHARED_OK" \
  LU_KT="$SEAM_kt" LU_SWIFT="$SEAM_swift" LU_TSX="$SEAM_tsx" LU_XF="$SEAM_xf" LU_CO="$CO" LU_OPT="$OPTFILES" node - <<'JS'
const e = process.env, R = "tools/titan/results";
const rows = s => s.split("\n").map(l => l.trim()).filter(l => l && !l.startsWith("#"));
const list = s => (s === "-" ? [] : s.split(",").map(x => x.trim()));
const seamFile = {kt: e.LU_KT, swift: e.LU_SWIFT, tsx: e.LU_TSX, xf: e.LU_XF};
const units = rows(e.LU_TABLE).map(l => {
  const [seq, lane, slug, unit, up, seams, needs, rev, kind, group, subject, note] = l.split("|").map(x => x.trim());
  const dir = `${R}/wave55-${slug}`;
  const sm = list(seams).map(s => ({patch: `${dir}/${s.split("@")[0]}`, seamFile: seamFile[s.split("@")[1]]}));
  const up2 = up === "-" ? null : `${dir}/${up}`;
  return {seq: Number(seq), lane, laneSlug: slug, dir, unit, kind, unitPatch: up2, seams: sm,
          applyOrder: [...(up2 ? [up2] : []), ...sm.map(s => s.patch)], needs: list(needs), revertOrder: list(rev),
          group: group === "-" ? null : group, subject: `wave55 ${lane} ${unit}: ${subject}`, note: note || null};
});
const lanes = [...new Map(units.map(u => [u.lane, u.dir])).entries()].sort((a, b) => a[0].localeCompare(b[0]));
const constraints = rows(e.LU_CONSTRAINTS).map(l => { const [r, why] = l.split("|"); const [before, after] = r.split("<").map(x => x.trim()); return {before, after, why: why.trim()}; });
const opt = Object.fromEntries(rows(e.LU_OPT).map(l => { const [k, v] = l.split("|").map(x => x.trim()); return [k, v.split(/\s+/)]; }));
units.forEach(u => { if (u.kind === "optional") u.trackedAtHeadIfNoPatch = opt[`${u.lane}:${u.unit}`]; });
const groups = {}; units.filter(u => u.group).forEach(u => (groups[u.group] = groups[u.group] || []).push(`${u.lane}:${u.unit}`));
const out = {
  about: "GENERATED by land-units.sh --emit-manifest from the ORDER and CONSTRAINTS tables at the top of land-units.sh; never hand-edited (land-units.sh --check fails on drift)",
  script: `${R}/wave55-plan/land-units.sh`, wave: 55, branch: "campaign/applier-campaign",
  sources: ["tools/titan/results/wave55-plan/seam-registry.md (§1, §2, §5, §6)", "tools/titan/results/wave55-plan/briefs-audit.md (§8 C7, C10, C12, C13)",
            "tools/titan/results/wave55-plan/plan-build.py (lanes.*.revertUnits, L7 landingOrder)", "the seven briefs, §4 Units"],
  records: {seq: 0, subject: e.LU_RECORDS, laneDirs: Object.fromEntries(lanes), laneNote: "_note.md",
            forceAdd: e.LU_FORCE.split("\n"), forceAddWhy: "instruments.md:300: the six gitignored wave-54 L4 dry-run out/ files are force-added in the records commit (committed with L7 records)"},
  deliveryContract: {unitPatch: "unit-<U>.patch: the unit's owned files; absent only for a seam-only unit",
                     seamPatch: "seam-<n>.patch: one hunk set in exactly ONE seam file (the registered one) + its patch-borne test files",
                     cutOn: "the tree it lands on (records + every earlier unit); a patch cut on 0d0d3a3d is fine wherever no earlier patch touched its context. SeamReachabilityTest.kt (round 1, M1): cut on HEAD at the writer's PINNED anchor — L4 seams 1 → 3 after \"content/PseudoGeneratedBox.kt\", (:159), L5 seam 4 after \"images/ImageCandidateChain.kt\", (:167), L3 seam 1 → unit-U2 after \"transforms/TransformInheritance.kt\", (:182); never at the list end",
                     note: "_note.md (round 1, M3): `TREES: <worktree path>` written FIRST (before any build); one section per unit naming exactly its paths; one line `patch: <name> sha256 <64 hex>` per delivered patch, written after the final restore; `revertStatesCompiled:` — every state of the unit's revertOrder chains compiled and its focused tests run on the lane's worktree (round 1, S12); ends `STATUS: COMPLETE` (anything else is refused by --check)",
                     forbidden: ["a unit patch touching a seam file", "a seam patch touching another seam file", "a patch touching a lane records dir", "two lanes touching one path outside sharedPaths", "a unit patch and its own seam patch touching one path", "renames and copies"]},
  seamFiles: seamFile, sharedPaths: e.LU_SHARED.split(" "),
  units, orderConstraints: constraints, groups,
  counts: {revertUnits: units.filter(u => u.kind !== "contingency").length, optional: units.filter(u => u.kind === "optional").map(u => `${u.lane}:${u.unit}`),
           contingency: units.filter(u => u.kind === "contingency").map(u => `${u.lane}:${u.unit}`), records: 1},
  commitTrailer: e.LU_CO,
  exitCodes: {"0": "ok", "1": "check found defects (nothing touched)", "2": "refused (nothing touched)", "3": "stopped mid-landing; the failing unit reverse-applied, tree at the last landed commit", "70": "internal guard: a mutating call in --check mode"}
};
process.stdout.write(JSON.stringify(out, null, 1) + "\n");
JS
}

print_order() {
  local i
  printf '  %-4s %-4s %-12s %-26s %-30s %s\n' seq lane unit "unit patch" "seam patches" "needs / revertOrder / kind / group"
  printf '  %-4s %-4s %-12s %s\n' 00 "L*" records "the selected lane dirs → 1 commit (+ force-added wave-54 out/ files with L7)"
  for ((i=0; i<N; i++)); do
    printf '  %-4s %-4s %-12s %-26s %-30s needs[%s] revert[%s] %s%s\n' "${SEQ[i]}" "${LANE[i]}" "${UNIT[i]}" "${UPATCH[i]}" \
      "${SEAMSPEC[i]}" "${NEEDS[i]}" "${REV[i]}" "${KIND[i]}" "$([[ ${GROUP[i]} != - ]] && echo " group:${GROUP[i]}")"
  done
}

# ── arguments ───────────────────────────────────────────────────────────────────────────────────────────────────────────
CHECK=0; CONT=0; CONTU=""; EMIT=0; PRINT=0; HOLD=""; ONLY=""; FROM=""; ROOT=""; NOREV=0
while (( $# )); do
  case "$1" in
    --check) CHECK=1;; --emit-manifest) EMIT=1;; --print-order) PRINT=1;; --no-revert-dry-runs) NOREV=1;;
    --contingency) CONT=1; CONTU="${2:-}"; [[ "$CONTU" =~ ^L[1-7]:[A-Za-z0-9-]+$ ]] || die "--contingency needs ONE unit, L:U (L1:NB-ios-b or L5:U1-ios-LRM)"; shift;;
    --hold) HOLD="${2:?--hold needs L:U[,L:U]}"; shift;; --only) ONLY="${2:?--only needs L1[,L4]}"; shift;;
    --from) FROM="${2:?--from needs L:U}"; shift;; --root) ROOT="${2:?--root needs a directory}"; shift;;
    -h|--help) sed -n '2,/^set -uo/p' "$0" | sed '$d'; exit 0;;
    *) die "unknown argument: $1 (see --help)";;
  esac; shift
done

echo "land-units.sh — wave 55 ($( ((EMIT)) && echo emit-manifest || { ((PRINT)) && echo print-order; } || { ((CHECK)) && echo 'CHECK: read-only'; } || echo LANDING)$( ((CONT)) && echo ', contingency'))"
if ! TC="$(table_check)"; then echo "$TC"; die "the ORDER / CONSTRAINTS tables are inconsistent — fix the table, nothing was read or touched" 1; fi
if (( EMIT )); then emit_manifest > "$MANIFEST.tmp.$$" && mv "$MANIFEST.tmp.$$" "$MANIFEST" && echo "wrote $MANIFEST"; exit $?; fi
if (( PRINT )); then print_order; echo "constraints:"; echo "$CONSTRAINTS" | sed 's/^/  /'; exit 0; fi

# ── the work tree ───────────────────────────────────────────────────────────────────────────────────────────────────────
[[ -n "$ROOT" ]] || ROOT="$(git -C "$HERE" rev-parse --show-toplevel 2>/dev/null)" || die "no work tree holds $HERE"
ROOT="$(cd "$ROOT" 2>/dev/null && pwd -P)" || die "--root: no such directory"
[[ "$(git -C "$ROOT" rev-parse --show-toplevel 2>/dev/null)" == "$ROOT" ]] || die "$ROOT is not a work-tree root"
cd "$ROOT" || die "cannot cd to $ROOT"
TMPBASE="$(cd "${TMPDIR:-/tmp}" && pwd -P)"
[[ "$TMPBASE/" == "$ROOT/"* ]] && die "TMPDIR ($TMPBASE) lies inside the work tree — the export tree must live outside it"
TMPD="$(mktemp -d "$TMPBASE/land-units.XXXXXX")" || die "mktemp failed"
cleanup() { [[ -n "${TMPD:-}" && "$TMPD" == "$TMPBASE"/land-units.* && -d "$TMPD" ]] && rm -rf -- "$TMPD"; }   # the private export dir only
trap cleanup EXIT
# Every mutating git call goes through tree_mut; in --check mode it is a hard stop (exit 70) — the wave-54 lesson as code.
tree_mut() { if (( CHECK )); then echo "INTERNAL: mutating call in --check mode: $*" >&2; exit 70; fi; "$@"; }
# git as a plain patch tool inside the export dir: no repository is discoverable (ceiling = the private dir).
exp_git() { local here; here="$(pwd -P)"; [[ "$here/" == "$TMPD/"* ]] || { echo "INTERNAL: export git outside $TMPD" >&2; exit 70; }
  env -u GIT_DIR -u GIT_WORK_TREE -u GIT_INDEX_FILE -u GIT_OBJECT_DIRECTORY GIT_CEILING_DIRECTORIES="$TMPD" git "$@"; }
HEADSHA="$(git rev-parse --short HEAD)"; BRANCH="$(git symbolic-ref -q --short HEAD || echo detached)"
echo "root    $ROOT"
echo "HEAD    $HEADSHA ($BRANCH)   export dir (private, removed at exit): $TMPD"

# ── selection: --only / --hold / --from / --contingency / optional-absent ─────────────────────────────────────────────
landed() { # <L> <U>: the unit's commit is in HEAD's history and not reverted after it
  local subj="wave55 $1 $2: " log; log="$(git log --format=%s -n 2000)"
  awk -v p="$subj" 'index($0,p)==1{f=1} END{exit !f}' <<<"$log" || return 1
  ! awk -v p="Revert \"$subj" 'index($0,p)==1{f=1} END{exit !f}' <<<"$log"
}
for h in $(echo "$HOLD" | tr ',' ' '); do (( $(idx "$h") >= 0 )) || die "--hold: unknown unit $h"; done
if (( CONT )); then ci=$(idx "$CONTU"); (( ci >= 0 )) && [[ "${KIND[ci]}" == contingency ]] || die "--contingency $CONTU: not a contingency row of the ORDER table (have: $(for ((k=0; k<N; k++)); do [[ "${KIND[k]}" == contingency ]] && printf '%s ' "$(key_of $k)"; done))"; fi
for l in $(echo "$ONLY" | tr ',' ' '); do [[ "$l" =~ ^L[1-7]$ ]] || die "--only: unknown lane $l"; done
FROMI=-1; if [[ -n "$FROM" ]]; then FROMI=$(idx "$FROM"); (( FROMI >= 0 )) || die "--from: unknown unit $FROM"; fi
(( CONT && FROMI >= 0 )) && die "--contingency and --from do not combine"
NSEL=0
for ((i=0; i<N; i++)); do
  SEL[i]=1; WHY[i]=""
  if [[ -n "$ONLY" && ",$ONLY," != *",${LANE[i]},"* ]]; then SEL[i]=0; WHY[i]="not in --only"
  elif [[ ",$HOLD," == *",$(key_of $i),"* ]]; then SEL[i]=0; WHY[i]="HELD (--hold)"
  elif (( CONT )) && [[ "$(key_of $i)" != "$CONTU" ]]; then SEL[i]=0; WHY[i]="$( [[ "${KIND[i]}" == contingency ]] && echo "another contingency unit (--contingency $CONTU)" || echo "not a contingency unit")"
  elif (( ! CONT )) && [[ "${KIND[i]}" == contingency ]]; then SEL[i]=0; WHY[i]="contingency (lands only by --contingency)"
  elif (( FROMI >= 0 && i < FROMI )); then SEL[i]=0; WHY[i]="before --from (must be landed)"
  elif [[ "${KIND[i]}" == optional && ! -f "$(dir_of $i)/${UPATCH[i]}" ]]; then SEL[i]=0; WHY[i]="optional, no patch delivered (its files must be tracked at HEAD: checked below)"
  fi
  (( SEL[i] )) && NSEL=$((NSEL+1))
done
SELERR=0
for ((i=0; i<N; i++)); do
  if [[ "${WHY[i]}" == "before --from"* ]] && ! landed "${LANE[i]}" "${UNIT[i]}"; then echo "  REFUSE  --from $FROM: $(key_of $i) precedes it but is not in HEAD's history"; SELERR=1; fi
  (( SEL[i] )) || continue
  for n in $(csv "${NEEDS[i]}"); do j=$(idx "${LANE[i]}:$n")
    (( SEL[j] )) || landed "${LANE[i]}" "$n" || { echo "  REFUSE  $(key_of $i) needs ${LANE[i]}:$n, which is neither selected nor landed (${WHY[j]}) — hold $(key_of $i) too"; SELERR=1; }; done
  if [[ "${GROUP[i]}" != "-" ]]; then for ((j=0; j<N; j++)); do
    [[ "${GROUP[j]}" == "${GROUP[i]}" ]] && (( ! SEL[j] )) && [[ "${WHY[j]}" != "before --from"* ]] && { echo "  REFUSE  group ${GROUP[i]}: $(key_of $i) is selected but $(key_of $j) is not (${WHY[j]}) — a group lands or is held whole"; SELERR=1; }
  done; fi
done
(( SELERR )) && die "the selection breaks a dependency — nothing was read further or touched"
SELIDX=(); for ((i=0; i<N; i++)); do (( SEL[i] )) && SELIDX+=("$i"); done
(( NSEL > 0 )) || die "nothing selected"

echo
echo "ORDER ($NSEL unit commits + records; hold: ${HOLD:-none}; only: ${ONLY:-all lanes}${FROM:+; from: $FROM})"
printf '  00   records  %s\n' "$( ((FROMI >= 0)) && echo 'only if the lane dirs hold new files (--from)' || { ((CONT)) && echo 'only if the lane dir holds new files (--contingency)'; } || echo 'the selected lane dirs → 1 commit')"
for ((i=0; i<N; i++)); do
  if (( SEL[i] )); then printf '  %s   %-3s %-12s %s\n' "${SEQ[i]}" "${LANE[i]}" "${UNIT[i]}" "$(patches_of "$i" | sed "s#^$R/##" | tr '\n' ' ')"
  else printf '  %s   %-3s %-12s -- skipped: %s\n' "${SEQ[i]}" "${LANE[i]}" "${UNIT[i]}" "${WHY[i]}"; fi
done

# ── host and manifest ───────────────────────────────────────────────────────────────────────────────────────────────────
echo; echo "HOST, INDEX, MANIFEST"
BUSY=""
for pid in $(pgrep -f 'gate-driver\.sh|section-runner\.sh|test-all\.sh|provision-devices\.sh|ab-arms5[0-9]\.sh' 2>/dev/null); do
  cwd="$(lsof -a -d cwd -p "$pid" -Fn 2>/dev/null | sed -n 's/^n//p' | head -1)"; cmd="$(ps -o command= -p "$pid" 2>/dev/null)"
  # busy = its cwd is in this tree, or its command line names this tree (an unknown cwd counts as busy)
  if [[ -z "$cwd" || "$cwd/" == "$ROOT/"* || "$cmd " == *"$ROOT/"* || "$cmd " == *"$ROOT "* ]]; then BUSY="$BUSY pid $pid (${cmd:0:110}…; cwd ${cwd:-unknown})"
  else echo "  note    pid $pid (${cmd:0:60}…) runs in $cwd and does not name this tree: ignored"; fi
done
if [[ -n "$BUSY" ]]; then echo "  busy    a gate / probe / A/B arm runs in this tree:$BUSY"; else echo "  host    no gate / probe / A/B arm process in this tree"; fi
if git diff-index --cached --quiet HEAD --; then echo "  index   nothing staged"; else STAGED=1; echo "  index   HAS STAGED CHANGES: $(git diff-index --cached --name-only HEAD -- | head -5 | tr '\n' ' ')"; fi
emit_manifest > "$TMPD/manifest.json" || die "manifest generation failed (node)"
if [[ ! -f "$MANIFEST" ]]; then err "manifest MISSING: $MANIFEST — run land-units.sh --emit-manifest"
elif cmp -s "$MANIFEST" "$TMPD/manifest.json"; then echo "  manifest in sync with the tables (${MANIFEST#$HERE/}, sha256 $(shasum -a 256 "$MANIFEST" | cut -c1-12)…)"
else err "manifest DRIFT: ${MANIFEST} differs from the tables — regenerate with --emit-manifest (never hand-edit)"; fi

# ── agreement with the plan of record: expectations.json (GENERATED by plan-build.py) names the same units ────────────
PLANJSON="$HERE/expectations.json"
if [[ -f "$PLANJSON" ]]; then
  LU_TABLE="$TABLE" LU_HOLD="$HOLD" node - "$PLANJSON" > "$TMPD/plan.txt" 2>&1 <<'JS'
const e = require(process.argv[2]), held = (process.env.LU_HOLD || "").split(",").filter(Boolean);
const rows = process.env.LU_TABLE.split("\n").map(l => l.trim()).filter(l => l && !l.startsWith("#")).map(l => l.split("|").map(x => x.trim()));
const mine = rows.map(([, lane, slug, unit, , , , rev, kind]) => ({lane, slug, unit, rev: rev.split(","), kind}));
const cont = new Set(mine.filter(u => u.kind === "contingency").map(u => u.unit));
let bad = 0; const out = (k, s) => { if (k === "ERR") bad++; console.log(`${k}\t${s}`); };
for (const [name, l] of Object.entries(e.lanes || {})) {
  const L = name.split("-")[0], plan = {...(l.revertUnits || {}), ...(l.contingencyUnits || {})};
  // a landed contingency or a held unit (the plan's heldUnits, e.g. --go-small, or --hold) rewrites its partners' revertOrders
  const drop = new Set([...cont, ...Object.keys(l.heldUnits || {}), ...held.filter(h => h.startsWith(L + ":")).map(h => h.split(":")[1])]);
  const strip = a => a.filter(u => !drop.has(u)).join(",");
  for (const [u, spec] of Object.entries(plan)) {
    const m = mine.find(x => x.lane === L && x.unit === u);
    if (!m) { out("ERR", `the plan's ${L} ${u} has no row in the ORDER table`); continue; }
    if (`tools/titan/results/wave55-${m.slug}` !== l.dir) out("ERR", `${L}: the plan's lane dir ${l.dir} is not wave55-${m.slug}`);
    if (strip(spec.revertOrder || []) !== strip(m.rev)) out("ERR", `${L} ${u}: revertOrder plan [${spec.revertOrder}] vs table [${m.rev}]`);
  }
  for (const u of Object.keys(l.heldUnits || {})) if (!held.includes(`${L}:${u}`)) out("ERR", `the plan HOLDS ${L} ${u} (${String(l.heldUnits[u]).slice(0, 60)}…): land with --hold ${L}:${u}`);
  for (const m of mine.filter(x => x.lane === L)) if (!(m.unit in plan) && !((l.heldUnits || {})[m.unit])) out("ERR", `the table's ${L} ${m.unit} is not a unit of the plan`);
}
const n = Object.values(e.lanes || {}).reduce((a, l) => a + Object.keys(l.revertUnits || {}).length, 0);
out(bad ? "BAD" : "OK", `${n} plan revert units (+ ${Object.values(e.lanes || {}).reduce((a, l) => a + Object.keys(l.contingencyUnits || {}).length, 0)} contingency) agree with the table: lane dirs, unit names, revertOrders`);
JS
  while IFS=$'\t' read -r k msg; do case "$k" in ERR) err "plan: $msg";; OK) echo "  plan    $msg (${PLANJSON#$HERE/})";; BAD) ;; *) err "plan agreement could not be read: $k $msg";; esac; done < "$TMPD/plan.txt"
else warn "plan of record $PLANJSON absent — the table is not cross-checked against plan-build.py's revert units"; fi

# ── records ─────────────────────────────────────────────────────────────────────────────────────────────────────────────
echo; echo "RECORDS (committed first)"
RECDIRS=(); WITH_L7=0
for ((i=0; i<N; i++)); do (( SEL[i] )) || continue; d="$(dir_of $i)"; [[ " ${RECDIRS[*]-} " == *" $d "* ]] || RECDIRS+=("$d"); [[ "${LANE[i]}" == L7 ]] && WITH_L7=1; done
NOW=$(date +%s); FRESH=""
for d in "${RECDIRS[@]}"; do
  if [[ ! -d "$d" ]]; then err "lane dir MISSING: $d/"; continue; fi
  nf=$(find "$d" -type f | wc -l | tr -d ' ')
  [[ -f "$d/_note.md" ]] || err "lane note MISSING: $d/_note.md (every unit commit's body points at it)"
  st="$( [[ -f "$d/_note.md" ]] && grep -E '^STATUS:' "$d/_note.md" | tail -1)"
  # Round 1 (process-holes M3): a limit-killed or PARTIAL lane never lands — resume it (PLAN §8 step 3), then re-cut.
  [[ "$st" == "STATUS: COMPLETE"* ]] || err "$d/_note.md: ${st:-no STATUS line} — a lane lands only on STATUS: COMPLETE (resume a killed / PARTIAL lane first: PLAN §8 step 3)"
  [[ -f "$d/_note.md" ]] && ! grep -qE '^TREES:' "$d/_note.md" && err "$d/_note.md has no TREES: line (written FIRST: the worktree a resumed lane re-opens, and where beforeEveryDeviceRun stops Gradle)"
  # every delivered patch is named, with its sha256, on a `patch:` line written after the final restore — a stale
  # dead-attempt patch, or a patch re-cut after the note, is refused
  if [[ -f "$d/_note.md" ]]; then
    # round 2 (S3): fix-S1-<U>.patch for each unit U of the lane is known too (the S1 fixes, landed by the orchestrator after the units)
    known=" $(for ((k=0; k<N; k++)); do [[ "$(dir_of $k)" == "$d" ]] && { [[ "${UPATCH[k]}" != - ]] && printf '%s ' "${UPATCH[k]}"; printf 'fix-S1-%s.patch ' "${UNIT[k]}"; for sp in $(csv "${SEAMSPEC[k]}"); do printf '%s ' "${sp%@*}"; done; }; done)"
    while IFS= read -r pf; do [[ -n "$pf" ]] || continue; b="${pf##*/}"
      [[ "$known" == *" $b "* ]] || err "$pf is not a patch the ORDER table names for this lane (a stale dead-attempt patch? delete it)"
      want="$(sed -n "s/^patch: $b sha256 \([0-9a-f]\{64\}\)\$/\1/p" "$d/_note.md" | tail -1)"
      have="$(shasum -a 256 "$pf" | cut -c1-64)"
      if [[ -z "$want" ]]; then err "$d/_note.md names no 'patch: $b sha256 <64 hex>' line (write it after the final restore)"
      elif [[ "$want" != "$have" ]]; then err "$pf sha256 $have differs from the note's $want (re-cut after the note, or a stale copy)"; fi
    done < <(find "$d" -maxdepth 1 -type f -name '*.patch' | sort)
    while read -r _ b _ _; do [[ -f "$d/$b" ]] || err "$d/_note.md names $b, which is not in the lane dir"; done < <(grep -E '^patch: ' "$d/_note.md")
  fi
  ign="$(git ls-files --others --ignored --exclude-standard -- "$d" | head -20)"
  [[ -n "$ign" ]] && warn "$d holds gitignored files the records commit will NOT carry (lane evidence of record is .txt, or force-added): $(echo "$ign" | tr '\n' ' ')"
  while IFS= read -r f; do [[ -n "$f" ]] || continue; m=$(stat -f %m "$f" 2>/dev/null || stat -c %Y "$f"); (( NOW - m < FRESH_SECS )) && FRESH="$FRESH $f"; done < <(find "$d" -type f)
  echo "  ok      $d/  ($nf files)"
done
[[ -n "$FRESH" ]] && warn "written less than ${FRESH_SECS}s ago (an instrument may still be writing; the landing refuses):$FRESH"
if (( WITH_L7 && ! CONT )); then while IFS= read -r f; do
  if [[ -f "$f" ]]; then echo "  ok      force-add $f$(git ls-files --error-unmatch -- "$f" >/dev/null 2>&1 && echo ' (already tracked)')"; else err "force-add MISSING: $f (instruments.md:300)"; fi
done <<<"$FORCE_ADD"; fi

# ── optional units that delivered no patch: their files must already be tracked at HEAD (else they would never land) ──
HDR=0
for ((i=0; i<N; i++)); do [[ "${WHY[i]}" == "optional, no patch"* ]] || continue
  (( HDR++ )) || { echo; echo "OPTIONAL UNITS WITH NO PATCH (their files must already be tracked at HEAD)"; }
  for f in $(optfiles_of "$(key_of $i)"); do
    if ! git ls-files --error-unmatch -- "$f" >/dev/null 2>&1; then err "$(key_of $i) delivered no patch and $f is NOT tracked at HEAD — commit it with the plan / gate records before landing, or deliver $(dir_of $i)/${UPATCH[i]} (after removing the loose copy)"
    elif [[ "$(git hash-object -- "$f" 2>/dev/null)" != "$(git rev-parse -q --verify "HEAD:$f")" ]]; then warn "$(key_of $i) delivered no patch but $f differs from HEAD on disk (a re-execution not delivered?)"
    else echo "  ok      $(key_of $i) (optional, no patch): $f tracked at HEAD"; fi
  done
done

# ── patches: presence, the paths each one names, and the delivery rules ────────────────────────────────────────────────
echo; echo "PATCHES (each: present · parsed · its paths · the rules · alone on the current tree)"
TOUCH="$TMPD/touches.tsv"; : > "$TOUCH"      # idx \t patch \t path \t created(1/0) \t kind(unit/seam)
SERIES=(); SERIES_I=(); MISSING=0
for i in "${SELIDX[@]}"; do
  for p in $(patches_of "$i"); do
    if [[ ! -f "$p" ]]; then err "MISSING $p   ($(key_of $i))"; MISSING=$((MISSING+1)); continue; fi
    SERIES+=("$p"); SERIES_I+=("$i")
  done
done
for ((s=0; s<${#SERIES[@]}; s++)); do
  p="${SERIES[s]}"; i="${SERIES_I[s]}"; base="${p##*/}"; pk=unit; [[ "$base" == seam-* ]] && pk=seam
  if ! git apply --numstat "$p" > "$TMPD/numstat" 2>"$TMPD/apply.err" || ! git apply --summary "$p" > "$TMPD/summary" 2>>"$TMPD/apply.err"; then
    err "UNPARSEABLE $p: $(head -2 "$TMPD/apply.err" | tr '\n' ' ')"; continue; fi
  grep -qE '^(rename|copy) (from|to) ' "$p" && err "$p carries a rename / copy — deliver it as a delete + a create"
  [[ -s "$TMPD/numstat" ]] || err "$p is EMPTY (no file changes)"
  sed -n 's/^ create \(mode [0-7]* \)\{0,1\}//p' "$TMPD/summary" > "$TMPD/creates"
  while IFS=$'\t' read -r _a _d path; do
    [[ -n "$path" ]] || continue
    c=0; grep -qxF -- "$path" "$TMPD/creates" && c=1
    printf '%s\t%s\t%s\t%s\t%s\n' "$i" "$p" "$path" "$c" "$pk" >> "$TOUCH"
  done < "$TMPD/numstat"
done
for ((s=0; s<${#SERIES[@]}; s++)); do
  p="${SERIES[s]}"; i="${SERIES_I[s]}"; base="${p##*/}"
  printf '  %-62s %s\n' "${p#$R/}" "($(key_of $i))"
  awk -F'\t' -v p="$p" '$2==p {print "            " ($4=="1" ? "+ " : "~ ") $3}' "$TOUCH"
  # a seam patch: exactly its registered seam file among the seam files; a unit patch: no seam file at all
  if [[ "$base" == seam-* ]]; then
    tok=""; for sp in $(csv "${SEAMSPEC[i]}"); do [[ "${sp%@*}" == "$base" ]] && tok="${sp#*@}"; done; want="$(seamfile_of_tok "$tok")"
    awk -F'\t' -v p="$p" -v w="$want" '$2==p && $3==w {f=1} END{exit !f}' "$TOUCH" || err "$p does not touch its registered seam file $want"
    for sf in $SEAMS_ALL; do [[ "$sf" == "$want" ]] && continue
      awk -F'\t' -v p="$p" -v w="$sf" '$2==p && $3==w {f=1} END{exit !f}' "$TOUCH" && err "$p touches a second seam file $sf (one seam file per seam patch)"; done
  else
    for sf in $SEAMS_ALL; do awk -F'\t' -v p="$p" -v w="$sf" '$2==p && $3==w {f=1} END{exit !f}' "$TOUCH" && err "$p (a unit patch) edits the seam file $sf — seams travel only as seam-<n>.patch"; done
  fi
  # no patch writes into a lane records dir (those belong to the records commit)
  for sl in "${SLUG[@]}"; do awk -F'\t' -v p="$p" -v d="$R/wave55-$sl/" '$2==p && index($3,d)==1 {print $3; f=1} END{exit !f}' "$TOUCH" > "$TMPD/rec" && err "$p writes into the lane records dir: $(tr '\n' ' ' < "$TMPD/rec")"; done
  while IFS= read -r fa; do awk -F'\t' -v p="$p" -v w="$fa" '$2==p && $3==w {f=1} END{exit !f}' "$TOUCH" && err "$p touches $fa, which the records commit force-adds"; done <<<"$FORCE_ADD"
  # stacked: a path an earlier patch of the series also touches (then "alone" may fail by construction)
  stk="$(awk -F'\t' -v p="$p" 'FNR==NR {if ($2==p) mine[$3]=1; next} $2==p {exit} ($3 in mine) {print $3 " (" $1 ")"}' "$TOUCH" "$TOUCH" | sort -u)"
  stacked=""; while IFS= read -r x; do [[ -n "$x" ]] || continue; xi="${x##*(}"; xi="${xi%)}"; stacked="$stacked ${x%% (*} on $(key_of "$xi");"; done <<<"$stk"
  if git apply --check --whitespace=nowarn "$p" 2>"$TMPD/apply.err"; then echo "            alone: applies on the current tree${stacked:+ (stacked:$stacked)}"
  elif [[ -n "$stacked" ]]; then echo "            alone: does not apply on the current tree — STACKED (expected; the cumulative check decides):$stacked"
  else err "$p does NOT apply on the current tree and stacks on nothing: $(head -2 "$TMPD/apply.err" | tr '\n' ' ')"; fi
done
# cross-lane: one path, two lanes — only the registered shared seam / test files
awk -F'\t' 'FNR==NR {lane[$1]=$2; next} {k=$3; l=lane[$1]; if (!(k SUBSEP l in seen)) {seen[k SUBSEP l]=1; n[k]++; who[k]=who[k] " " l}} END {for (k in n) if (n[k]>1) print k "\t" who[k]}' \
  <(for ((i=0; i<N; i++)); do printf '%s\t%s\n' "$i" "${LANE[i]}"; done) "$TOUCH" | sort > "$TMPD/xlane"
while IFS=$'\t' read -r path who; do [[ -n "$path" ]] || continue
  [[ " $SHARED_OK " == *" $path "* ]] && echo "  shared  $path:$who (registered shared file)" || err "two lanes touch $path:$who — not a registered shared file (briefs-audit §1: 0 paths owned by two lanes)"
done < "$TMPD/xlane"
# within one unit: its unit patch and its seam patch never touch one path
awk -F'\t' '{k=$1 SUBSEP $3; if ((k in kind) && kind[k]!=$5) print $1 "\t" $3; kind[k]=$5}' "$TOUCH" | sort -u > "$TMPD/inunit"
while IFS=$'\t' read -r i path; do [[ -n "$path" ]] && err "$(key_of "$i"): its unit patch and its seam patch both touch $path — deliver that path in one of them"; done < "$TMPD/inunit"

# ── every touched path is clean, judged by the FIRST patch that touches it (read-only: hash-object without -w) ─────────
echo; echo "TOUCHED PATHS (clean at HEAD on the current tree; a created path absent)"
DIRTY=0; : > "$TMPD/export.list"
awk -F'\t' '!($3 in s) {s[$3]=1; print $3 "\t" $4}' "$TOUCH" > "$TMPD/first"
while IFS=$'\t' read -r path c; do
  if (( c )); then
    if [[ -e "$path" || -L "$path" ]]; then err "$path exists on disk, but its first patch CREATES it (a lane copied it in? the patch is the record — the landing refuses)"; DIRTY=1
    elif git cat-file -e "HEAD:$path" 2>/dev/null; then err "$path is tracked at HEAD, but its first patch CREATES it (cut on the wrong base)"; DIRTY=1; fi
  else
    hb="$(git rev-parse -q --verify "HEAD:$path" 2>/dev/null)"
    if [[ -z "$hb" ]]; then err "$path is not tracked at HEAD, but its first patch MODIFIES it"; DIRTY=1; continue; fi
    echo "$path" >> "$TMPD/export.list"
    wb="$(git hash-object -- "$path" 2>/dev/null)"; ib="$(git ls-files -s -- "$path" | awk '{print $2; exit}')"
    [[ "$wb" == "$hb" ]] || { err "$path is MODIFIED in the working tree (a lane edited the shared tree? the landing never rewinds it — refuses)"; DIRTY=1; }
    [[ "$ib" == "$hb" ]] || { err "$path is STAGED differently from HEAD"; DIRTY=1; }
  fi
done < "$TMPD/first"
if [[ ! -s "$TMPD/first" ]]; then echo "  n/a     no delivered patch names a path yet"
elif (( ! DIRTY )); then echo "  ok      $(wc -l < "$TMPD/first" | tr -d ' ') paths ($(awk -F'\t' '$2==1' "$TMPD/first" | wc -l | tr -d ' ') created, the rest clean at HEAD $HEADSHA)"; fi

# ── the whole series, cumulatively, on the current tree: ONE concatenated input to `git apply --check` ────────────────
echo; echo "CUMULATIVE (the series in landing order, \`git apply --check\` of one concatenated input on the current tree)"
CUM_OK=0
if (( MISSING )); then echo "  skipped: $MISSING patch(es) missing — the series is incomplete (check a delivered subset with --only / --hold)"
elif (( ${#SERIES[@]} == 0 )); then echo "  nothing to check"
else
  : > "$TMPD/series.patch"; CUM_OK=1
  for ((s=0; s<${#SERIES[@]}; s++)); do
    cat "${SERIES[s]}" >> "$TMPD/series.patch"; [[ -n "$(tail -c1 "${SERIES[s]}")" ]] && echo >> "$TMPD/series.patch"
    if ! git apply --check --whitespace=nowarn "$TMPD/series.patch" 2>"$TMPD/apply.err"; then
      err "the series STOPS at ${SERIES[s]#$R/} ($(key_of "${SERIES_I[s]}")): $(head -2 "$TMPD/apply.err" | tr '\n' ' ') — re-cut it on the tree holding every patch before it"; CUM_OK=0; break; fi
  done
  (( CUM_OK )) && echo "  ok      ${#SERIES[@]} patches apply in order on $HEADSHA (+ the records commit, which touches no patched path)"
fi

# ── C12 revert dry-runs in an EXPORT TREE (copies of the touched files in $TMPD; git apply as a plain patch tool) ─────
echo; echo "REVERT DRY-RUNS (every selected unit's revertOrder, reverse-applied on the fully landed series; export tree)"
if (( NOREV )); then echo "  skipped (--no-revert-dry-runs)"
elif (( ! CUM_OK )); then echo "  skipped: the forward series is not clean"
else
  EXB="$TMPD/export/landed"; mkdir -p "$EXB"
  [[ -s "$TMPD/export.list" ]] && tar -cf - -T "$TMPD/export.list" | (cd "$EXB" && tar -xf -)
  FWD=1; for ((s=0; s<${#SERIES[@]}; s++)); do
    (cd "$EXB" && exp_git apply --whitespace=nowarn "$ROOT/${SERIES[s]}") 2>"$TMPD/apply.err" || { err "export replay disagrees with the cumulative check at ${SERIES[s]}: $(head -1 "$TMPD/apply.err")"; FWD=0; break; }
  done
  if (( FWD )); then
    for i in "${SELIDX[@]}"; do
      chain=""; for u in $(csv "${REV[i]}"); do j=$(idx "${LANE[i]}:$u"); (( SEL[j] )) && chain="$chain $j"; done
      W="$TMPD/export/work"; rm -rf -- "$W"; cp -Rp "$EXB" "$W"; ok=1; at=""
      for j in $chain; do
        for p in $(patches_of "$j" | sed -n '1!G;h;$p'); do   # reverse: the seam(s) first, the unit patch last
          (cd "$W" && exp_git apply -R --whitespace=nowarn "$ROOT/$p") 2>"$TMPD/apply.err" || { ok=0; at="$(key_of "$j") ${p##*/}: $(head -1 "$TMPD/apply.err")"; break 2; }
        done
      done
      names=""; for j in $chain; do names="$names ${UNIT[j]}"; done
      c12=""; [[ "${LANE[i]}" == L4 || "$(key_of $i)" == L5:U1-ios ]] && c12="  (C12)"
      if (( ok )); then printf '  ok      %-15s revert [%s ] reverse-applies on the landed series%s\n' "$(key_of $i)" "$names" "$c12"
      else err "$(key_of $i) revert [$names ] FAILS on the landed series at $at$c12"; fi
    done
  fi
fi

# ── the contingency on top (informational in a normal --check) ─────────────────────────────────────────────────────────
if (( ! CONT )); then echo; echo "CONTINGENCY (not landed by this run)"; nc=0
  for ((i=0; i<N; i++)); do [[ "${KIND[i]}" == contingency ]] || continue
    [[ -z "$ONLY" || ",$ONLY," == *",${LANE[i]},"* ]] || continue; nc=$((nc+1))
    have=1; for p in $(patches_of "$i"); do [[ -f "$p" ]] || have=0; done
    if (( ! have )); then echo "  note    $(key_of $i) (contingency): not delivered — expected until the stage-1 probe prints its trigger"
    elif (( CUM_OK )); then cp "$TMPD/series.patch" "$TMPD/series.cont.patch"
      for p in $(patches_of "$i"); do cat "$p" >> "$TMPD/series.cont.patch"; done
      git apply --check --whitespace=nowarn "$TMPD/series.cont.patch" 2>/dev/null && echo "  ok      $(key_of $i) (contingency) applies on top of the series" \
        || warn "$(key_of $i) (contingency) does NOT apply on top of the landed series — re-cut before --contingency"
    fi
  done
  (( nc )) || echo "  none in the selected lanes"
fi

echo
if (( CHECK )); then
  echo "SUMMARY  $ERRS error(s), $WARNS warning(s) — $( ((ERRS)) && echo 'NOT landable as is' || echo 'landable') (--check touched nothing: no checkout, no rm, no apply on the tree)"
  (( ERRS )) && exit 1; exit 0
fi

# ════════════════════════════════════════════════ THE LANDING ════════════════════════════════════════════════════════
(( ERRS )) && die "the preflight found $ERRS error(s) — nothing was touched" 1
[[ -n "$BUSY" ]] && die "a gate / probe / A/B arm runs in this tree — refusing (nothing was touched)"
[[ -n "${STAGED:-}" ]] && die "the index has staged changes — refusing (nothing was touched)"
[[ -n "$FRESH" ]] && die "lane files were written less than ${FRESH_SECS}s ago — refusing (nothing was touched)"
echo "LANDING on $ROOT ($BRANCH $HEADSHA)"
n=0
staged_is() { # <expected-paths-file>: the staged set equals it exactly
  git diff-index --cached --name-only HEAD -- | sort > "$TMPD/staged"; sort -u "$1" > "$TMPD/expected"; cmp -s "$TMPD/staged" "$TMPD/expected"; }

# 00 records
git ls-files -s -- "${RECDIRS[@]}" > "$TMPD/rec.before"
tree_mut git add -- "${RECDIRS[@]}" || die "git add of the lane records failed (nothing committed; unstage with: git reset -q -- ${RECDIRS[*]})" 3
(( WITH_L7 && ! CONT )) && { tree_mut git add -f -- $(echo "$FORCE_ADD" | tr '\n' ' ') || die "force-add failed (unstage with: git reset -q -- <paths>)" 3; }
if git diff-index --cached --quiet HEAD --; then
  (( FROMI >= 0 || CONT )) && echo "skip 00  records: the lane dirs hold nothing new" || die "the records commit is EMPTY — the lane dirs are already committed (resume with --from <L:U>)" 3
else
  bad="$(git diff-index --cached --name-only HEAD -- | awk -v dirs="${RECDIRS[*]}" -v force="$(echo "$FORCE_ADD" | tr '\n' ' ')" 'BEGIN{nd=split(dirs,D," "); nf=split(force,F," ")} {ok=0; for(k=1;k<=nd;k++) if (index($0, D[k] "/")==1) ok=1; for(k=1;k<=nf;k++) if ($0==F[k]) ok=1; if(!ok) print}')"
  [[ -z "$bad" ]] || die "the records commit would carry non-record paths: $bad (unstage with: git reset -q -- <paths>)" 3
  tree_mut git commit -q -m "$RECORDS_SUBJECT" -m "Records, not code: every revert unit below holds only its code paths, so each reverts alone (PLAN.md §4). Lane dirs: ${RECDIRS[*]}$( ((WITH_L7 && ! CONT)) && echo "; plus the six wave-54 out/ files instruments.md:300 hands to this commit (force-added)")." -m "$CO" \
    || die "git commit of the records failed (staged, uncommitted: unstage with git reset -q -- ${RECDIRS[*]})" 3
  printf 'ok 00  %s  %s\n' "$(git rev-parse --short HEAD)" "$RECORDS_SUBJECT"
fi

# 01..: one commit per unit
restore_unit() { # reverse-apply what this unit applied (LIFO); the tree returns to the last landed commit
  local k p
  for ((k=${#APPLIED[@]}-1; k>=0; k--)); do p="${APPLIED[k]}"
    tree_mut git apply --index -R --whitespace=nowarn "$p" 2>/dev/null || { echo "RESTORE FAILED at $p — restore by hand: git apply --index -R $(printf '%s ' "${APPLIED[@]}" | awk '{for(i=NF;i>0;i--) printf "%s ", $i}')" >&2; exit 3; }
  done
  git diff-index --cached --quiet HEAD -- && echo "restored: the unit's patches reverse-applied; HEAD $(git rev-parse --short HEAD), nothing staged" >&2
}
for i in "${SELIDX[@]}"; do
  APPLIED=(); awk -F'\t' -v i="$i" '$1==i {print $3}' "$TOUCH" > "$TMPD/unit.paths"
  for p in $(patches_of "$i"); do
    git apply --index --check --whitespace=nowarn "$p" 2>"$TMPD/apply.err" || { echo "STOP at $(key_of $i): $p does not apply: $(head -2 "$TMPD/apply.err" | tr '\n' ' ')" >&2; restore_unit; exit 3; }
    tree_mut git apply --index --whitespace=nowarn "$p" || { echo "STOP at $(key_of $i): git apply --index $p failed" >&2; restore_unit; exit 3; }
    APPLIED+=("$p")
  done
  if [[ "${GROUP[i]}" != "-" ]]; then   # the rest of the group must apply on top of this unit before it is committed
    : > "$TMPD/group.patch"; for ((j=i+1; j<N; j++)); do [[ "${GROUP[j]}" == "${GROUP[i]}" ]] || continue; for p in $(patches_of "$j"); do cat "$p" >> "$TMPD/group.patch"; done; done
    if [[ -s "$TMPD/group.patch" ]] && ! git apply --check --whitespace=nowarn "$TMPD/group.patch" 2>/dev/null; then
      echo "STOP at $(key_of $i): the rest of group ${GROUP[i]} does not apply on top of it — never land it alone" >&2; restore_unit; exit 3; fi
  fi
  [[ "${LAND_FAULT_AT:-}" == "$(key_of $i)" ]] && { echo "STOP at $(key_of $i): LAND_FAULT_AT (rehearsal fault knob)" >&2; restore_unit; exit 3; }
  staged_is "$TMPD/unit.paths" || { echo "STOP at $(key_of $i): the staged set differs from the paths its patches name:" >&2; diff "$TMPD/expected" "$TMPD/staged" >&2; restore_unit; exit 3; }
  shas=""; for p in $(patches_of "$i"); do shas="$shas ${p##*/} sha256 $(shasum -a 256 "$p" | cut -c1-16)…;"; done
  body="Revert unit ${LANE[i]}-${UNIT[i]} ($(dir_of $i)/_note.md; tools/titan/results/wave55-plan/PLAN.md §4). revertOrder: [$(echo "${REV[i]}" | sed 's/,/, /g')].${NOTE[i]:+ ${NOTE[i]}}"
  tree_mut git commit -q -m "${SUBJ[i]}" -m "$body" -m "Landed by tools/titan/results/wave55-plan/land-units.sh (seq ${SEQ[i]}) from$shas" -m "$CO" \
    || { echo "STOP at $(key_of $i): git commit failed" >&2; restore_unit; exit 3; }
  n=$((n+1)); printf 'ok %s  %s  %s\n' "${SEQ[i]}" "$(git rev-parse --short HEAD)" "${SUBJ[i]}"
done
echo "landed $n unit commit(s) on $BRANCH: $HEADSHA..$(git rev-parse --short HEAD); working tree now:"
git --no-optional-locks status --short | head -20
