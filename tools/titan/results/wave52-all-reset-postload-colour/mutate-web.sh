#!/usr/bin/env bash
# mutate-web.sh — executed-mutation proof for the web `all` pins (lane L11):
# literal replacements in StyleBuilder.ts / _dispatch.ts, each followed by the
# focused vitest run, then a byte-exact restore (sha256 compared).
set -u
ROOT=$(cd "$(dirname "$0")/../../../.." && pwd)
SB=$ROOT/runtimes/web/src/core/renderer/StyleBuilder.ts
DP=$ROOT/runtimes/web/src/engine/global/_dispatch.ts
LOG=$ROOT/tools/titan/results/wave52-all-reset-postload-colour/mutations.log
S1=$(mktemp); S2=$(mktemp); cp "$SB" "$S1"; cp "$DP" "$S2"
SHA0=$(cat "$SB" "$DP" | shasum -a 256 | cut -d' ' -f1)
run() {   # $1 id, then (file old new) triples
  local id=$1; shift
  while [ $# -ge 3 ]; do
    python3 - "$1" "$2" "$3" <<'PY'
import sys; p,o,n=sys.argv[1:4]; s=open(p).read(); assert s.count(o)==1, ('anchor', o); open(p,'w').write(s.replace(o,n))
PY
    shift 3
  done
  (cd "$ROOT/runtimes/web" && npx vitest run tests/global/globalPhase10.test.ts > /tmp/l11-webmut.out 2>&1)
  local rc=$?
  { echo "== web $id (vitest exit $rc)"; grep -E '^\s+(×|✗|FAIL)|Tests +[0-9]' /tmp/l11-webmut.out | sed 's/\x1b\[[0-9;]*m//g' | head -12; } >> "$LOG"
  cp "$S1" "$SB"; cp "$S2" "$DP"
}
echo "# web all-reset mutations $(date -u +%FT%TZ) sha256(StyleBuilder.ts+_dispatch.ts before)=$SHA0" >> "$LOG"
run W1 "$SB" 'const styles: CSSStyles = { ...globalStyles } as CSSStyles;' 'const styles: CSSStyles = {};' \
       "$SB" '// (Phase-10 global — `all` — is emitted FIRST, at the top: see wave 52.)' 'Object.assign(styles, globalStyles);'
run W2 "$DP" 'const before = properties.slice(0, i).filter((p) => ALL_RESET_EXEMPT_TYPES.has(p.type));' 'const before = properties.slice(0, i);'
run W3 "$DP" 'const before = properties.slice(0, i).filter((p) => ALL_RESET_EXEMPT_TYPES.has(p.type));' 'const before = properties.slice(0, i).filter(() => false);'
run W4 "$DP" 'if (p.type === ALL_PROPERTY_TYPE && keywordOrRaw(p.data) !== undefined) i = k;' 'if (i < 0 && p.type === ALL_PROPERTY_TYPE && keywordOrRaw(p.data) !== undefined) i = k;'
SHA1=$(cat "$SB" "$DP" | shasum -a 256 | cut -d' ' -f1)
echo "# restored sha256(after)=$SHA1 $( [ "$SHA0" = "$SHA1" ] && echo IDENTICAL || echo MISMATCH )" >> "$LOG"
rm -f "$S1" "$S2"
