#!/bin/bash
# Re-verifier (wave 54 L4; a copy of skeptic/sw.sh writing into reverify/runs): focused Catalyst XCTest run with rule-3 retry on a
# lock / build-in-progress / foreign compile error. Prints "Executed N tests"
# lines, per-class counts and failing test names. Usage: sw.sh <label> <Class>...
ROOT=/Users/dranak/Documents/Projects/Style-Converter/.claude/worktrees/trusting-bohr-bd6fbf
OUT=$ROOT/tools/titan/results/wave54-oof-layout/reverify/runs
label=$1; shift
cd "$ROOT" || exit 2
args=(); for c in "$@"; do args+=("-only-testing:StyleConverterRuntimeTests/$c"); done
for i in 1 2 3 4 5 6; do
  xcodebuild test -scheme StyleConverterRuntime -destination 'platform=macOS,variant=Mac Catalyst,arch=arm64' "${args[@]}" > "$OUT/$label.xc.txt" 2>&1; rc=$?
  if grep -q 'database is locked\|build already in progress' "$OUT/$label.xc.txt"; then
    echo "attempt $i: locked; waiting 180s" >&2; perl -e 'select(undef,undef,undef,180)'; continue
  fi
  if [ $rc -ne 0 ] && grep -q 'error:' "$OUT/$label.xc.txt" && ! grep -q 'Executed [0-9]* test' "$OUT/$label.xc.txt"; then
    echo "attempt $i: build error, waiting 180s:" >&2; grep 'error:' "$OUT/$label.xc.txt" | head -5 >&2
    perl -e 'select(undef,undef,undef,180)'; continue
  fi
  break
done
{
  grep -E "Executed [0-9]+ tests?" "$OUT/$label.xc.txt" | tail -3
  grep -E "^Test Suite '.*' (passed|failed)" "$OUT/$label.xc.txt" | sort -u
  grep -E "error: -\[|: error: .*XCT|failed \(" "$OUT/$label.xc.txt" | head -30
  grep -E '\*\* TEST (SUCCEEDED|FAILED) \*\*' "$OUT/$label.xc.txt"
  echo "xcodebuild rc=$rc"
} > "$OUT/$label.summary.txt"
cat "$OUT/$label.summary.txt"
exit $rc
