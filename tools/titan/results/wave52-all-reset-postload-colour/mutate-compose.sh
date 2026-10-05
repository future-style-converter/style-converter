#!/usr/bin/env bash
# mutate-compose.sh — executed-mutation proof for the Compose AllReset pins
# (lane L11). Each mutation is a literal string replacement in AllReset.kt;
# the file is restored byte-exact from a saved copy and its sha256 compared
# before/after. Results append to mutations.log in this directory.
set -u
ROOT=$(cd "$(dirname "$0")/../../../.." && pwd)               # repo root
F=$ROOT/runtimes/compose/src/main/java/com/styleconverter/runtime/global/AllReset.kt
LOG=$ROOT/tools/titan/results/wave52-all-reset-postload-colour/mutations.log
export JAVA_HOME=$(/usr/libexec/java_home -v 21)
SAVE=$(mktemp)                                                  # pristine copy
cp "$F" "$SAVE"
SHA0=$(shasum -a 256 "$F" | cut -d' ' -f1)
run() {   # $1 = id, $2 = old literal, $3 = new literal
  python3 - "$F" "$2" "$3" <<'PY'
import sys; p,o,n=sys.argv[1:4]; s=open(p).read(); assert s.count(o)==1, ('mutation anchor', o); open(p,'w').write(s.replace(o,n))
PY
  (cd "$ROOT/apps/android-harness" && ./gradlew :runtime:testDebugUnitTest --tests 'com.styleconverter.runtime.global.AllResetTest' --console=plain > /tmp/l11-mut.out 2>&1)
  local rc=$?
  { echo "== compose $1 (gradle exit $rc)"; grep -E '> .* FAILED|tests completed' /tmp/l11-mut.out; } >> "$LOG"
  cp "$SAVE" "$F"                                               # restore byte-exact
}
echo "# compose AllReset mutations $(date -u +%FT%TZ) sha256(before)=$SHA0" >> "$LOG"
run M1 'return Result(before + after, keptInherited)' 'return Result(emptyList(), emptyList())'
run M2 'val before = own.subList(0, i).filter { it.type in EXEMPT_TYPES }' 'val before = own.subList(0, i)'
run M3 'val before = own.subList(0, i).filter { it.type in EXEMPT_TYPES }' 'val before = own.subList(0, i).filter { false }'
run M4 'if (keyword in KEEPS_INHERITED) inherited' 'if (true) inherited'
run M5 'val i = own.indexOfLast { keywordOf(it) != null }' 'val i = own.indexOfFirst { keywordOf(it) != null }'
SHA1=$(shasum -a 256 "$F" | cut -d' ' -f1)
echo "# restored sha256(after)=$SHA1 $( [ "$SHA0" = "$SHA1" ] && echo IDENTICAL || echo MISMATCH )" >> "$LOG"
rm -f "$SAVE"
