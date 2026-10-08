#!/usr/bin/env bash
# Skeptic (wave 54, L3) — own mutation runner. Usage: sk-mutate.sh <label> <file> <old> <new> -- <cmd…>
# sha256 before; ONE exact replacement (must match once); run cmd (expect RED); restore from a byte copy; sha256 after
# (must equal before); re-run cmd (expect GREEN). Appends to skeptic/sk-mutations.log. Exit 8 if not byte-exact.
set -uo pipefail
HERE="$(cd "$(dirname "$0")" && pwd)"; LOG="$HERE/sk-mutations.log"
label="$1"; file="$2"; old="$3"; new="$4"; shift 4; [ "$1" = "--" ] && shift
before=$(shasum -a 256 "$file" | cut -d' ' -f1); bak="$HERE/.sk-bak.$$"; cp "$file" "$bak"
python3 - "$file" "$old" "$new" <<'PY' || { echo "MUTATION DID NOT APPLY: $label"; rm -f "$bak"; exit 9; }
import sys; p, o, n = sys.argv[1:4]; s = open(p, encoding='utf-8').read(); c = s.count(o)
if c != 1: sys.exit(f"expected 1 match, found {c}")
open(p, 'w', encoding='utf-8').write(s.replace(o, n))
PY
mut=$(shasum -a 256 "$file" | cut -d' ' -f1)
red=$("$@" 2>&1); rr=$?
cp "$bak" "$file"; rm -f "$bak"; after=$(shasum -a 256 "$file" | cut -d' ' -f1)
green=$("$@" 2>&1); rg=$?
{ echo "=== $label ($(date '+%F %T'))"; echo "file $file"; echo "sha before $before / mutated $mut / restored $after $([ "$before" = "$after" ] && echo BYTE-EXACT || echo MISMATCH)"
  echo "mutated exit=$rr $([ $rr -ne 0 ] && echo RED || echo 'GREEN (MUTATION SURVIVED)')"
  echo "$red" | grep -E 'FAILED|Failure|failed|AssertionError|Executed|not ok|# (pass|fail)|junit |tests completed|NO FRESH|error:' | head -14 | sed 's/^/  red> /'
  echo "restored exit=$rg $([ $rg -eq 0 ] && echo GREEN || echo 'STILL RED')"
  echo "$green" | grep -E 'BUILD|Executed|# (pass|fail)|junit |tests completed|NO FRESH' | head -8 | sed 's/^/  green> /'; } >> "$LOG"
tail -n 22 "$LOG"; [ "$before" = "$after" ] || exit 8
