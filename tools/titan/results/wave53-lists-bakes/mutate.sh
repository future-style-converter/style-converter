#!/usr/bin/env bash
# tools/titan/results/wave53-lists-bakes/mutate.sh — one executed pin mutation, red → restore → green, logged.
# Usage: mutate.sh <label> <file> <old-string-file> <new-string-file> <test command...>
# The old/new strings are read from files so multi-line mutations stay exact. The file is restored byte-exact
# from a backup copy, and the full sha256 before / mutated / restored is appended to mutations.log beside this
# script with the test summary of the mutated and the restored run. Exit 1 if the restore is not byte-exact.
set -uo pipefail
here="$(cd "$(dirname "$0")" && pwd)"
label=$1; file=$2; oldf=$3; newf=$4; shift 4
bak="$(mktemp)"; cp "$file" "$bak"
h0=$(shasum -a 256 "$file" | cut -d' ' -f1)
python3 - "$file" "$oldf" "$newf" <<'PY'
import sys
p, o, n = sys.argv[1], open(sys.argv[2]).read(), open(sys.argv[3]).read()
s = open(p, encoding='utf-8').read()
assert s.count(o) == 1, f'old string found {s.count(o)} times'
open(p, 'w', encoding='utf-8').write(s.replace(o, n))
PY
# A mutation that did not apply is no evidence at all: restore, log nothing, fail loudly.
if [ $? -ne 0 ]; then cp "$bak" "$file"; rm -f "$bak"; echo "MUTATION NOT APPLIED: $label" >&2; exit 2; fi
hm=$(shasum -a 256 "$file" | cut -d' ' -f1)
red=$("$@" 2>&1 | tail -400)
cp "$bak" "$file"; rm -f "$bak"
h1=$(shasum -a 256 "$file" | cut -d' ' -f1)
green=$("$@" 2>&1 | tail -400)
sum() { printf '%s\n' "$1" | grep -E '^# (pass|fail)|Executed [0-9]+ tests?|tests completed|FAILED|BUILD (SUCCESSFUL|FAILED)|\*\* TEST (SUCCEEDED|FAILED)' | tr '\n' ' '; }
fails() { printf '%s\n' "$1" | grep -E '^not ok|error: -\[|FAILED$|> .*FAILED| failed' | head -8; }
{
  echo "### $label"
  echo "- file: ${file#$PWD/}"
  echo "- sha256 before   $h0"
  echo "- sha256 mutated  $hm"
  echo "- sha256 restored $h1 $([ "$h0" = "$h1" ] && echo '(byte-exact)' || echo '(NOT RESTORED)')"
  echo "- mutated run:  $(sum "$red")"
  fails "$red" | sed 's/^/    red: /'
  echo "- restored run: $(sum "$green")"
  echo
} | tee -a "$here/mutations.log"
[ "$h0" = "$h1" ]
