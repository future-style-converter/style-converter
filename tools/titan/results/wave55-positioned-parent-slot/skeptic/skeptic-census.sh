#!/bin/bash
# Wave 55 L3 SKEPTIC census driver (independent of the lane's census/):
#   skeptic-census.sh <worktree> <run-dir> <out-dir>
# 1. copies SkepticL3HostDump.kt into the worktree's :app test tree, runs it
#    (host flags raw + rewritten, Box-branch inputs, applies) -> <out>/host-dump.tsv,
#    deletes it again;
# 2. runs skeptic-census.py over the per-test IR + host-dump.tsv.
set -euo pipefail
WT=$1; RUN=$2; OUT=$3
HERE=$(cd "$(dirname "$0")" && pwd)
DST=$WT/apps/android-harness/app/src/test/java/com/styleconverter/test/screenshot/SkepticL3HostDump.kt
cp "$HERE/SkepticL3HostDump.kt" "$DST"
trap 'rm -f "$DST"' EXIT
export JAVA_HOME=${JAVA_HOME:-$(/usr/libexec/java_home -v 21)}
export SKL3_RUN=$RUN SKL3_OUT=$OUT/host-dump.tsv
(cd "$WT/apps/android-harness" && ./gradlew :app:testDebugUnitTest --rerun --tests '*SkepticL3HostDump*' -q)
/opt/homebrew/bin/python3.12 -B "$HERE/skeptic-census.py" "$RUN" "$OUT/host-dump.tsv"
