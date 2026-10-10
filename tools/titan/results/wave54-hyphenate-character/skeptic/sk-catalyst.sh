#!/usr/bin/env bash
# Skeptic: Catalyst focused XCTest with a private derived-data path; prints the "Executed N tests" lines and fails on N == 0.
# Usage: sk-catalyst.sh <dd-path> <Class>…
R="$(cd "$(dirname "$0")/../../../../.." && pwd)"; DD="$1"; shift; a=(); for c in "$@"; do a+=(-only-testing:StyleConverterRuntimeTests/$c); done
for try in 1 2 3 4 5; do
  out=$(cd "$R" && xcodebuild test -scheme StyleConverterRuntime -destination 'platform=macOS,variant=Mac Catalyst,arch=arm64' -derivedDataPath "$DD" "${a[@]}" 2>&1); rc=$?
  if echo "$out" | grep -qE 'database is locked|build already in progress'; then echo "locked; retry in 180s ($try)"; sleep 180; continue; fi; break
done
echo "$out" | grep -E "error:|Test Case .* failed|Executed [0-9]+ tests|TEST (SUCCEEDED|FAILED)|BUILD FAILED" | grep -v '^\s*$' | sort -u | head -30
n=$(echo "$out" | grep -oE 'Executed [0-9]+ tests' | tail -1 | grep -oE '[0-9]+'); [ -z "$n" -o "$n" = 0 ] && { echo "ZERO TESTS EXECUTED"; exit 4; }
exit $rc
