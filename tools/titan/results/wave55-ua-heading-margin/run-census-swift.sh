#!/usr/bin/env bash
# Wave 55 lane L4 — the iOS twin of run-census.sh: copy census-swift.swift.txt into the lane
# worktree's SwiftUI test target (placeholders filled), run ONLY that class under Catalyst with the
# worktree's own DerivedData, delete it, then census-check.py the read-out (platform ios).
#   run-census-swift.sh <lane-worktree> [run]
set -euo pipefail
WT=$1; RUN=${2:-wave54-final}
HERE=$(cd "$(dirname "$0")" && pwd)
SHARED=$(cd "$HERE/../../../.." && pwd)
DEST=$WT/runtimes/swiftui/Tests/StyleConverterRuntimeTests/L4CensusTests.swift
OUT=$HERE/census-swift.$RUN.out.txt
sed -e "s|@@SHARED_ROOT@@|$SHARED|" -e "s|@@RUN@@|$RUN|g" -e "s|@@OUT@@|$OUT|" "$HERE/census-swift.swift.txt" > "$DEST"
trap 'rm -f "$DEST"' EXIT
( cd "$WT" && nice xcodebuild test -scheme StyleConverterRuntime \
    -destination 'platform=macOS,variant=Mac Catalyst,arch=arm64' -derivedDataPath "$WT/.dd" \
    -only-testing:StyleConverterRuntimeTests/L4CensusTests > "$WT/../l4-census-swift.log" 2>&1 ) \
  || { tail -30 "$WT/../l4-census-swift.log"; exit 1; }
grep -E "Executed|TEST SUCCEEDED|TEST FAILED" "$WT/../l4-census-swift.log" | tail -2
echo "census written: $OUT ($(wc -l < "$OUT") lines)"
/opt/homebrew/bin/python3.12 -B "$HERE/census-check.py" "$OUT" ios | tee "$HERE/census-check-swift.$RUN.out.txt"
