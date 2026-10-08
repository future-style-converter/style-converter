#!/usr/bin/env bash
# Skeptic: converter focused test + fresh-XML proof of execution.
R="$(cd "$(dirname "$0")/../../../../.." && pwd)"; export JAVA_HOME="$(/usr/libexec/java_home -v 21)"
st=$(mktemp); out=$(cd "$R" && ./gradlew :converter:test --tests '*HyphenateCharacter*' --rerun --console=plain 2>&1); rc=$?
echo "$out" | grep -E 'FAILED|BUILD|tests completed'
f=$(find "$R/converter/build/test-results/test" -name '*HyphenateCharacter*.xml' -newer "$st"); rm -f "$st"
[ -z "$f" ] && { echo "NO FRESH XML"; exit 3; }
grep -o 'tests="[0-9]*" skipped="[0-9]*" failures="[0-9]*"' $f | sed 's/^/junit /'; exit $rc
