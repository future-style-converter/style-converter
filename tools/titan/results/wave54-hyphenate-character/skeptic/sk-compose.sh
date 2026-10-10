#!/usr/bin/env bash
# Skeptic: Compose :runtime focused JVM tests with --rerun + fresh-JUnit-XML proof (exit 3 if no test executed).
# Usage: sk-compose.sh <filter>…
R="$(cd "$(dirname "$0")/../../../../.." && pwd)"; X="$R/runtimes/compose/build/test-results/testDebugUnitTest"
export JAVA_HOME="$(/usr/libexec/java_home -v 21)"; st=$(mktemp); a=(); for f in "$@"; do a+=(--tests "$f"); done
out=$(cd "$R/apps/android-harness" && ./gradlew :runtime:testDebugUnitTest --rerun "${a[@]}" --console=plain 2>&1); rc=$?
echo "$out" | grep -E 'FAILED|BUILD|tests completed|^e: ' | head -20
fr=$(find "$X" -name '*.xml' -newer "$st" 2>/dev/null); rm -f "$st"
[ -z "$fr" ] && { echo "NO FRESH XML"; exit 3; }
for f in $fr; do sed -n 's/.*<testsuite name="[^"]*\.\([^".]*\)" tests="\([0-9]*\)" skipped="\([0-9]*\)" failures="\([0-9]*\)" errors="\([0-9]*\)".*/junit \1 tests=\2 skipped=\3 failures=\4 errors=\5/p' "$f"; done
exit $rc
