#!/usr/bin/env bash
# reverify-r1/census.sh <worktree> <run-id> <out.tsv> — the L6 re-verifier's census run (ReverifyL6CensusTest.kt.txt,
# the skeptic's SkepticL6CensusTest plus a `clip` column = StyleApplier's config.overflow.shouldClip). Copies the test
# into <worktree>'s Compose test tree with its placeholders filled, runs only that class on whatever state the worktree
# holds, copies the TSV out and deletes the copied test (no tracked file is touched).
set -euo pipefail
WT="$1"; RUN="$2"; OUT="$3"
HERE="$(cd "$(dirname "$0")" && pwd)"
SHARED="$(cd "$HERE/../../../../.." && pwd)"
DST="$WT/runtimes/compose/src/test/java/com/styleconverter/runtime/ReverifyL6CensusTest.kt"
TMP="$WT/runtimes/compose/build/reverify-l6-census.tsv"
sed -e "s#@@RUN_SECTIONS@@#$SHARED/tools/titan/runs/$RUN/sections#" -e "s#@@OUT_TSV@@#$TMP#" "$HERE/ReverifyL6CensusTest.kt.txt" > "$DST"
trap 'rm -f "$DST"' EXIT
rm -f "$TMP"
export JAVA_HOME="${JAVA_HOME:-$(/usr/libexec/java_home -v 21)}"
(cd "$WT/apps/android-harness" && ./gradlew :runtime:testDebugUnitTest --tests '*ReverifyL6Census*' -q)
cp "$TMP" "$OUT"
echo "rows: $(($(wc -l < "$OUT") - 1)) -> $OUT"
