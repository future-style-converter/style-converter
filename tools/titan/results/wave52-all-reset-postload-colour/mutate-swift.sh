#!/usr/bin/env bash
# mutate-swift.sh — executed-mutation proof for the iOS AllResetTests pins
# (lane L11): literal replacements in GlobalExtractor.swift, each followed by
# the focused Catalyst run, then a byte-exact restore (sha256 compared).
# Usage: mutate-swift.sh <derivedDataPath>. Appends to mutations.log.
set -u
ROOT=$(cd "$(dirname "$0")/../../../.." && pwd)
F=$ROOT/runtimes/swiftui/Sources/StyleConverterRuntime/StyleEngine/global/GlobalExtractor.swift
LOG=$ROOT/tools/titan/results/wave52-all-reset-postload-colour/mutations.log
DD=$1
SAVE=$(mktemp); cp "$F" "$SAVE"
SHA0=$(shasum -a 256 "$F" | cut -d' ' -f1)
run() {   # $1 id, $2 old literal, $3 new literal
  python3 - "$F" "$2" "$3" <<'PY'
import sys; p,o,n=sys.argv[1:4]; s=open(p).read(); assert s.count(o)==1, ('anchor', o); open(p,'w').write(s.replace(o,n))
PY
  (cd "$ROOT" && xcodebuild test -scheme StyleConverterRuntime -destination 'platform=macOS,variant=Mac Catalyst,arch=arm64' \
     -derivedDataPath "$DD" -only-testing:StyleConverterRuntimeTests/AllResetTests > /tmp/l11-swmut.out 2>&1)
  local rc=$?
  { echo "== swift $1 (xcodebuild exit $rc)";
    grep -E "error: -\[StyleConverterRuntimeTests.AllResetTests" /tmp/l11-swmut.out | sed -E 's/.*AllResetTests (test[A-Za-z0-9]+)\].*/  FAILED \1/' | sort -u;
    grep -E 'Executed [0-9]+ tests' /tmp/l11-swmut.out | tail -1; } >> "$LOG"
  cp "$SAVE" "$F"
}
echo "# swift GlobalExtractor mutations $(date -u +%FT%TZ) sha256(before)=$SHA0" >> "$LOG"
run M1 'return (Array(before) + Array(after), keptInherited)' 'return ([], [])'
run M2 'let before = own[..<i].filter { allResetExemptTypes.contains($0.type) }' 'let before = own[..<i].filter { _ in true }'
run M3 'let before = own[..<i].filter { allResetExemptTypes.contains($0.type) }' 'let before = own[..<i].filter { _ in false }'
run M4 'let keptInherited = keepsInherited.contains(keyword)' 'let keptInherited = true'
run M5 'guard let i = own.lastIndex(where:' 'guard let i = own.firstIndex(where:'
SHA1=$(shasum -a 256 "$F" | cut -d' ' -f1)
echo "# restored sha256(after)=$SHA1 $( [ "$SHA0" = "$SHA1" ] && echo IDENTICAL || echo MISMATCH )" >> "$LOG"
rm -f "$SAVE"
