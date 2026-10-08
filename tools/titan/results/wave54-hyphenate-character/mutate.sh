#!/usr/bin/env bash
# tools/titan/results/wave54-hyphenate-character/mutate.sh — executed-mutation runner (wave 54, lane L3).
# Usage: mutate.sh <label> <file> <python-literal-old> <python-literal-new> -- <test command…>
# Records sha256 of <file> before, applies ONE exact-substring replacement (must match exactly once), runs the test
# command (expects RED = non-zero exit), restores the file byte-exact from the saved copy, verifies the sha256, and
# re-runs the test command (expects GREEN). Appends a block to mutations.log beside this script.
set -uo pipefail
HERE="$(cd "$(dirname "$0")" && pwd)"; LOG="$HERE/mutations.log"
label="$1"; file="$2"; old="$3"; new="$4"; shift 4; [ "$1" = "--" ] && shift
before=$(shasum -a 256 "$file" | cut -d' ' -f1); cp "$file" "$HERE/.mutate-backup"
python3 - "$file" "$old" "$new" <<'PY' || { echo "mutation did not apply"; exit 9; }
import sys; p, old, new = sys.argv[1], sys.argv[2], sys.argv[3]
s = open(p, encoding='utf-8').read(); n = s.count(old)
if n != 1: sys.exit(f"expected exactly 1 match, found {n}")
open(p, 'w', encoding='utf-8').write(s.replace(old, new))
PY
mutated=$(shasum -a 256 "$file" | cut -d' ' -f1)
out_red=$("$@" 2>&1); rc_red=$?
cp "$HERE/.mutate-backup" "$file"; rm -f "$HERE/.mutate-backup"
after=$(shasum -a 256 "$file" | cut -d' ' -f1)
out_green=$("$@" 2>&1); rc_green=$?
{ echo "=== $label  ($(date '+%Y-%m-%d %H:%M:%S'))"; echo "file: $file"
  echo "sha256 before:  $before"; echo "sha256 mutated: $mutated"; echo "sha256 restored: $after  $( [ "$before" = "$after" ] && echo BYTE-EXACT || echo MISMATCH)"
  echo "mutated run exit=$rc_red  ($( [ $rc_red -ne 0 ] && echo RED || echo 'GREEN — MUTATION SURVIVED'))"
  echo "$out_red" | grep -E 'FAILED|failed|Failure|AssertionError|expected|tests completed|Executed|✖|not ok|# (pass|fail)|junit |NO FRESH' | head -12 | sed 's/^/  red> /'
  echo "restored run exit=$rc_green  ($( [ $rc_green -eq 0 ] && echo GREEN || echo 'STILL RED'))"
  echo "$out_green" | grep -E 'BUILD|Executed|# (pass|fail)|tests completed|junit |NO FRESH' | head -6 | sed 's/^/  green> /'
} >> "$LOG"
tail -n 25 "$LOG"
[ "$before" = "$after" ] || exit 8
