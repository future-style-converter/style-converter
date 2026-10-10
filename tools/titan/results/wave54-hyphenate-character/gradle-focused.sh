#!/usr/bin/env bash
# tools/titan/results/wave54-hyphenate-character/gradle-focused.sh — wave 54 lane L3: run a focused Compose :runtime
# JVM suite and print what actually ran (the compile task's state and, from the JUnit XML written by THIS run, every
# class's tests/failures), so a mutation verdict can never come from a build that executed no test (R2-N5) or from a
# concurrent lane's Gradle racing the shared :runtime build dir (the bundleLib*ToJarDebug failure seen at 20:56).
# Usage: gradle-focused.sh <--tests filter>…   (exit = Gradle's exit; 3 when the run wrote no fresh XML)
set -uo pipefail
R="$(cd "$(dirname "$0")/../../../.." && pwd)"; X="$R/runtimes/compose/build/test-results/testDebugUnitTest"
export JAVA_HOME="$(/usr/libexec/java_home -v 21)"
stamp="$(mktemp)"; args=(); for f in "$@"; do args+=(--tests "$f"); done
out="$(cd "$R/apps/android-harness" && ./gradlew :runtime:testDebugUnitTest --rerun "${args[@]}" --console=plain 2>&1)"; rc=$?
echo "$out" | grep -E '^> Task :runtime:(compileDebugKotlin|compileDebugUnitTestKotlin|testDebugUnitTest)|FAILED$|tests completed|BUILD (SUCCESSFUL|FAILED)'
fresh=$(find "$X" -name '*.xml' -newer "$stamp" 2>/dev/null); rm -f "$stamp"
[ -z "$fresh" ] && { echo "NO FRESH JUnit XML — no test executed in this run"; exit 3; }
for f in $fresh; do sed -n 's/.*<testsuite name="[^"]*\.\([^".]*\)" tests="\([0-9]*\)" skipped="\([0-9]*\)" failures="\([0-9]*\)" errors="\([0-9]*\)".*/  junit \1: tests=\2 skipped=\3 failures=\4 errors=\5/p' "$f"; done
exit $rc
