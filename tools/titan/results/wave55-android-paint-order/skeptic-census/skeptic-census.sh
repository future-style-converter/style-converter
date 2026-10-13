#!/usr/bin/env bash
# skeptic-census.sh <worktree> <run-id> <out.tsv> — the L6 skeptic's own JVM census (see SkepticL6CensusTest.kt.txt).
# Copies the test into the worktree's Compose test tree, runs only that class, copies the TSV out, deletes the test.
set -euo pipefail
WT="$1"; RUN="$2"; OUT="$3"
HERE="$(cd "$(dirname "$0")" && pwd)"
SHARED="$(cd "$HERE/../../../../.." && pwd)"
DST="$WT/runtimes/compose/src/test/java/com/styleconverter/runtime/SkepticL6CensusTest.kt"
TMP="$WT/runtimes/compose/build/skeptic-l6-census.tsv"
sed -e "s#@@RUN_SECTIONS@@#$SHARED/tools/titan/runs/$RUN/sections#" -e "s#@@OUT_TSV@@#$TMP#" "$HERE/SkepticL6CensusTest.kt.txt" > "$DST"
trap 'rm -f "$DST"' EXIT
rm -f "$TMP"
export JAVA_HOME="${JAVA_HOME:-$(/usr/libexec/java_home -v 21)}"
(cd "$WT/apps/android-harness" && ./gradlew :runtime:testDebugUnitTest --tests '*SkepticL6Census*' -q)
cp "$TMP" "$OUT"
echo "rows: $(($(wc -l < "$OUT") - 1)) -> $OUT"
