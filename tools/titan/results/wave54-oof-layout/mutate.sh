#!/bin/bash
# L4 mutation runner: apply ONE literal replacement to an owned file, run a
# focused suite (expected RED), restore the file byte-exact from a copy and
# verify sha256 before == after. Usage:
#   mutate.sh <label> <file> <old-literal-file> <new-literal-file> <suite-cmd...>
set -u
label=$1; file=$2; oldf=$3; newf=$4; shift 4
ROOT=/Users/dranak/Documents/Projects/Style-Converter/.claude/worktrees/trusting-bohr-bd6fbf
OUT=$ROOT/tools/titan/results/wave54-oof-layout/mutations
mkdir -p "$OUT"
before=$(shasum -a 256 "$file" | cut -d' ' -f1)
cp "$file" "$OUT/.$label.restore"
python3 - "$file" "$oldf" "$newf" <<'PY'
import sys
p, o, n = sys.argv[1], open(sys.argv[2]).read(), open(sys.argv[3]).read()
s = open(p).read()
assert s.count(o) == 1, f"anchor count {s.count(o)}"
open(p, 'w').write(s.replace(o, n))
PY
[ $? -eq 0 ] || { echo "MUTATION NOT APPLIED"; exit 3; }
mutated=$(shasum -a 256 "$file" | cut -d' ' -f1)
( "$@" ) > "$OUT/$label.run.txt" 2>&1; rc=$?
cp "$OUT/.$label.restore" "$file"; rm -f "$OUT/.$label.restore"
after=$(shasum -a 256 "$file" | cut -d' ' -f1)
{
  echo "label=$label file=${file#$ROOT/}"
  echo "sha256 before=$before mutated=$mutated after=$after restored=$([ "$before" = "$after" ] && echo OK || echo MISMATCH)"
  echo "suite rc=$rc ($([ $rc -ne 0 ] && echo RED || echo GREEN))"
} | tee "$OUT/$label.summary.txt"
