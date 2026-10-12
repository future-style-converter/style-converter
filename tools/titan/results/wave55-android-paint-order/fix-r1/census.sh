#!/usr/bin/env bash
# fix-r1/census.sh <worktree> <run-id> <out.tsv> — runs L6FixCensusTest.kt.txt in <worktree> (whatever state it holds):
# copies it into the Compose test tree with its placeholders filled, runs only that class, copies the TSV out, and
# deletes the copied test again (no tracked file is touched).
set -euo pipefail
WT="$1"; RUN="$2"; OUT="$3"
HERE="$(cd "$(dirname "$0")" && pwd)"
SHARED="$(cd "$HERE/../../../../.." && pwd)"
DST="$WT/runtimes/compose/src/test/java/com/styleconverter/runtime/L6FixCensusTest.kt"
TMP="$WT/runtimes/compose/build/l6-fix-census.tsv"
sed -e "s#@@RUN_SECTIONS@@#$SHARED/tools/titan/runs/$RUN/sections#" -e "s#@@OUT_TSV@@#$TMP#" "$HERE/L6FixCensusTest.kt.txt" > "$DST"
trap 'rm -f "$DST"' EXIT
rm -f "$TMP"
export JAVA_HOME="${JAVA_HOME:-$(/usr/libexec/java_home -v 21)}"
(cd "$WT/apps/android-harness" && ./gradlew :runtime:testDebugUnitTest --tests '*L6FixCensus*' -q)
cp "$TMP" "$OUT"
echo "census rows: $(($(wc -l < "$OUT") - 1)) -> $OUT"
