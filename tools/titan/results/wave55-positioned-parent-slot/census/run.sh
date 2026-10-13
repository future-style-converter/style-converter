#!/bin/bash
# census/run.sh <lane-worktree> <run-dir> <out.tsv>
# Runs the lane census L3CensusRunTest.kt (the runtime's own predicates over the verbatim per-test IR of <run-dir>)
# inside the lane worktree: copy in → run → copy the report out → delete the copy (the worktree's test tree is left
# exactly as it was). Requires the lane's units applied in that worktree (it calls PositionedParentSlotGate).
set -uo pipefail
WT="$1"; RUN="$2"; OUT="$3"
HERE="$(cd "$(dirname "$0")" && pwd)"
DST="$WT/runtimes/compose/src/test/java/com/styleconverter/runtime/layout/position/L3CensusRunTest.kt"
[[ -e "$DST" ]] && { echo "refusing: $DST exists" >&2; exit 2; }
cp "$HERE/L3CensusRunTest.kt" "$DST"
export JAVA_HOME=$(/usr/libexec/java_home -v 21)
export L3_CENSUS_RUN="$RUN" L3_CENSUS_OUT="$OUT.tmp"
(cd "$WT/apps/android-harness" && ./gradlew :runtime:testDebugUnitTest --rerun --tests '*L3CensusRunTest' > "$OUT.gradle.log" 2>&1); R=$?
rm -f "$DST"
# The test JVM's working dir is the module dir; a relative L3_CENSUS_OUT is resolved there — this one is absolute.
mv "$OUT.tmp" "$OUT" 2>/dev/null || { echo "no report (gradle exit $R; see $OUT.gradle.log)" >&2; exit 3; }
echo "census: gradle exit $R, $(wc -l < "$OUT") lines → $OUT"
