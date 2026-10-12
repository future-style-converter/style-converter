#!/usr/bin/env bash
# Wave 55 lane L4 — run the behavioural census: copy census-jvm.kt.txt into the lane worktree's
# Compose test tree (placeholders filled), run ONLY that class, delete it again, then cross-check
# the read-out against expectations.json (census-check.py). Read-only on the shared tree except
# for the two outputs it writes into this directory.
#   run-census.sh <lane-worktree> [run]
set -euo pipefail
WT=$1; RUN=${2:-wave54-final}
HERE=$(cd "$(dirname "$0")" && pwd)
SHARED=$(cd "$HERE/../../../.." && pwd)
DEST=$WT/runtimes/compose/src/test/java/com/styleconverter/runtime/spacing/L4CensusTest.kt
OUT=$HERE/census-jvm.$RUN.out.txt
sed -e "s|@@SHARED_ROOT@@|$SHARED|" -e "s|@@RUN@@|$RUN|" -e "s|@@OUT@@|$OUT|" "$HERE/census-jvm.kt.txt" > "$DEST"
trap 'rm -f "$DEST"' EXIT
( cd "$WT/apps/android-harness" && JAVA_HOME=$(/usr/libexec/java_home -v 21) nice ./gradlew -q \
    :runtime:testDebugUnitTest --tests 'com.styleconverter.runtime.spacing.L4CensusTest' )
echo "census written: $OUT ($(wc -l < "$OUT") lines)"
/opt/homebrew/bin/python3.12 -B "$HERE/census-check.py" "$OUT" | tee "$HERE/census-check.$RUN.out.txt"
