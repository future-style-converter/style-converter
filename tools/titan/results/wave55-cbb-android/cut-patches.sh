#!/bin/bash
# wave-55 L2 (cbb-android) — cut the three delivered patches from the lane worktree (TREES: line of _note.md), in the
# order PLAN §0 / task rule (2) fixes: the SEAM patch first (seam file + its patch-borne test), then the seam file is
# RESTORED byte-exact (git show HEAD:<file>, sha256 checked against the registered base 2445d9ea…) and its patch-borne test
# removed, then the unit patches (owned paths only; new files as creation hunks via `git add -N`).
# Each patch = its header (headers/<name>.hdr, `#` lines git apply ignores) + `git diff HEAD -- <paths>`.
# Leaves the worktree WITHOUT the seam hunk; re-apply seam-1.patch to return to the full state.
set -eu
HERE="$(cd "$(dirname "$0")" && pwd)"
WT="$(sed -n 's/^TREES: //p' "$HERE/_note.md" | head -1)"
cd "$WT"
R=runtimes/compose/src; M=$R/main/java/com/styleconverter/runtime; T=$R/test/java/com/styleconverter/runtime
CR=$M/core/renderer/ComponentRenderer.kt; SW=$T/core/renderer/ChildContainingBlockSeamWiringTest.kt
U1=($M/core/variables/ContainingBlockBands.kt $M/core/variables/DynamicValueResolver.kt $T/core/variables/DynamicValueResolverTest.kt)
U2=($M/sizing/PercentContentBoxBand.kt $M/sizing/SizingApplier.kt $T/sizing/PercentContentBoxBandTest.kt)
git add -N "$SW" "${U1[0]}" "${U2[0]}" "${U2[2]}"
{ cat "$HERE/headers/seam-1.hdr"; git diff HEAD -- "$CR" "$SW"; } > "$HERE/seam-1.patch"
git show "HEAD:$CR" > "$CR"
[ "$(shasum -a 256 "$CR" | cut -d' ' -f1)" = 2445d9ea2674576fc0946e3f630f48b88d48f0f9d8c98df3249ba67d7a026aa5 ] || { echo "seam file NOT restored"; exit 2; }
git rm --cached -q "$SW" && rm -f "$SW"
{ cat "$HERE/headers/unit-U1.hdr"; git diff HEAD -- "${U1[@]}"; } > "$HERE/unit-U1.patch"
{ cat "$HERE/headers/unit-U2.hdr"; git diff HEAD -- "${U2[@]}"; } > "$HERE/unit-U2.patch"
for p in seam-1 unit-U1 unit-U2; do echo "$p.patch: $(git apply --numstat "$HERE/$p.patch" | awk '{a+=$1; d+=$2; n++} END {print n" files +"a" -"d}') sha256 $(shasum -a 256 "$HERE/$p.patch" | cut -d' ' -f1)"; done
echo "seam file restored: $CR sha256 2445d9ea2674576fc0946e3f630f48b88d48f0f9d8c98df3249ba67d7a026aa5"
