#!/usr/bin/env bash
# fix-r2/census.sh <worktree> <run-id> <out.tsv> — fix-r2's census run (FixR2L6CensusTest.kt.txt, reverify-r1's
# census plus overflowX / overflowY / clipsX / clipsY / legacyBand columns for mutation XR3). Copies the test
# into <worktree>'s Compose test tree with its placeholders filled, runs only that class on whatever state the worktree
# holds, copies the TSV out and deletes the copied test (no tracked file is touched).
set -euo pipefail
WT="$1"; RUN="$2"; OUT="$3"
HERE="$(cd "$(dirname "$0")" && pwd)"
SHARED="$(cd "$HERE/../../../../.." && pwd)"
DST="$WT/runtimes/compose/src/test/java/com/styleconverter/runtime/FixR2L6CensusTest.kt"
TMP="$WT/runtimes/compose/build/fixr2-l6-census.tsv"
sed -e "s#@@RUN_SECTIONS@@#$SHARED/tools/titan/runs/$RUN/sections#" -e "s#@@OUT_TSV@@#$TMP#" "$HERE/FixR2L6CensusTest.kt.txt" > "$DST"
trap 'rm -f "$DST"' EXIT
rm -f "$TMP"
export JAVA_HOME="${JAVA_HOME:-$(/usr/libexec/java_home -v 21)}"
(cd "$WT/apps/android-harness" && ./gradlew :runtime:testDebugUnitTest --tests '*FixR2L6Census*' -q)
cp "$TMP" "$OUT"
echo "rows: $(($(wc -l < "$OUT") - 1)) -> $OUT"
