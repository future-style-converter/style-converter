#!/usr/bin/env bash
# tools/titan/results/wave55-android-paint-order/census.sh <worktree> <run-id> <out.tsv>
# Runs lane L6's JVM chain census (L6ChainCensusTest.kt.txt) inside a THROWAWAY worktree: copies it into the Compose
# test tree with its two placeholders filled, runs only that class, copies the TSV out, and deletes the copied test
# again (the worktree's tracked files are never touched). census.py diffs a base-tree TSV against a patched-tree TSV.
set -euo pipefail
WT="$1"; RUN="$2"; OUT="$3"
HERE="$(cd "$(dirname "$0")" && pwd)"
SHARED="$(cd "$HERE/../../../.." && pwd)"
DST="$WT/runtimes/compose/src/test/java/com/styleconverter/runtime/L6ChainCensusTest.kt"
TMP="$WT/runtimes/compose/build/l6-census.tsv"
sed -e "s#@@RUN_SECTIONS@@#$SHARED/tools/titan/runs/$RUN/sections#" -e "s#@@OUT_TSV@@#$TMP#" "$HERE/L6ChainCensusTest.kt.txt" > "$DST"
trap 'rm -f "$DST"' EXIT
rm -f "$TMP"
export JAVA_HOME="${JAVA_HOME:-$(/usr/libexec/java_home -v 21)}"
(cd "$WT/apps/android-harness" && ./gradlew :runtime:testDebugUnitTest --tests '*L6ChainCensus*' -q)
cp "$TMP" "$OUT"
echo "census rows: $(($(wc -l < "$OUT") - 1)) -> $OUT"
