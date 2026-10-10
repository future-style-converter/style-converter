#!/bin/bash
# L4 focused Catalyst runner with the build-workflow rule-3 retry: on an
# xcodebuild "database is locked" / "build already in progress" it waits 3
# minutes and retries (up to 5 times). Args: the -only-testing class names.
# Exit code = xcodebuild's; output is printed whole (callers grep it).
ROOT=/Users/dranak/Documents/Projects/Style-Converter/.claude/worktrees/trusting-bohr-bd6fbf
cd "$ROOT" || exit 2
args=()
for c in "$@"; do args+=("-only-testing:StyleConverterRuntimeTests/$c"); done
for i in 1 2 3 4 5 6; do
  out=$(xcodebuild test -scheme StyleConverterRuntime -destination 'platform=macOS,variant=Mac Catalyst,arch=arm64' "${args[@]}" 2>&1); rc=$?
  if echo "$out" | grep -q 'database is locked\|build already in progress'; then
    echo "attempt $i: locked; waiting 180s" >&2; perl -e 'select(undef,undef,undef,180)'; continue
  fi
  echo "$out"; exit $rc
done
echo "gave up after 6 locked attempts"; exit 3
