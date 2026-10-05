#!/usr/bin/env bash
# mutate-postload.sh — executed-mutation proof for the F2 pins (section 11 of
# post-load-extract.test.mjs): literal replacements in post-load-extract.mjs,
# each followed by the focused node --test run (wave52 L11 pins only), then a
# byte-exact restore (sha256 compared). Appends to mutations.log.
set -u
ROOT=$(cd "$(dirname "$0")/../../../.." && pwd)
F=$ROOT/tools/titan/post-load-extract.mjs
LOG=$ROOT/tools/titan/results/wave52-all-reset-postload-colour/mutations.log
SAVE=$(mktemp); cp "$F" "$SAVE"
SHA0=$(shasum -a 256 "$F" | cut -d' ' -f1)
run() {   # $1 id, $2 old literal, $3 new literal
  python3 - "$F" "$2" "$3" <<'PY'
import sys; p,o,n=sys.argv[1:4]; s=open(p).read(); assert s.count(o)==1, ('anchor', o); open(p,'w').write(s.replace(o,n))
PY
  (cd "$ROOT" && node --test --test-name-pattern='wave52 L11' tools/titan/post-load-extract.test.mjs > /tmp/l11-plmut.out 2>&1)
  local rc=$?
  { echo "== postload $1 (node --test exit $rc)"; grep -E '^not ok' /tmp/l11-plmut.out; grep -E '^# (pass|fail)' /tmp/l11-plmut.out; } >> "$LOG"
  cp "$SAVE" "$F"
}
echo "# post-load F2 mutations $(date -u +%FT%TZ) sha256(before)=$SHA0" >> "$LOG"
run P1 '  if (!rule?.introduceWhenParentDiffers) return false;' '  return false;'
run P2 '  return value !== parent;' '  return true;'
run P3 '  if (UA_COLOURED_TAGS.has(tag)) return false;' '  void tag;'
run P4 '  if (opts.onlyMissing) return false;' '  void opts.onlyMissing;'
SHA1=$(shasum -a 256 "$F" | cut -d' ' -f1)
echo "# restored sha256(after)=$SHA1 $( [ "$SHA0" = "$SHA1" ] && echo IDENTICAL || echo MISMATCH )" >> "$LOG"
rm -f "$SAVE"
