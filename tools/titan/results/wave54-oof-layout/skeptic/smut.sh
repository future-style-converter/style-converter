#!/bin/bash
# Skeptic (wave 54 L4) mutation replay: ONE literal replacement in an owned
# file (anchor count asserted 1), the focused suite run (kt.sh / sw.sh), the
# file restored byte-exact from a copy, sha256 before / mutated / after logged.
# Usage: smut.sh <label> <file> <old-literal-file> <new-literal-file> kt|sw <filters/classes...>
ROOT=/Users/dranak/Documents/Projects/Style-Converter/.claude/worktrees/trusting-bohr-bd6fbf
D=$ROOT/tools/titan/results/wave54-oof-layout/skeptic
label=$1; file=$2; oldf=$3; newf=$4; kind=$5; shift 5
before=$(shasum -a 256 "$file" | cut -d' ' -f1)
cp "$file" "$D/runs/.$label.restore"
python3 - "$file" "$oldf" "$newf" <<'PY' || { echo "MUTATION NOT APPLIED"; rm -f "$D/runs/.$label.restore"; exit 3; }
import sys
p, o, n = sys.argv[1], open(sys.argv[2]).read().rstrip('\n'), open(sys.argv[3]).read().rstrip('\n')
s = open(p).read()
assert s.count(o) == 1, f"anchor count {s.count(o)}"
open(p, 'w').write(s.replace(o, n))
PY
mutated=$(shasum -a 256 "$file" | cut -d' ' -f1)
"$D/$kind.sh" "$label" "$@" > "$D/runs/$label.suite.txt" 2>&1; rc=$?
cp "$D/runs/.$label.restore" "$file"; rm -f "$D/runs/.$label.restore"
after=$(shasum -a 256 "$file" | cut -d' ' -f1)
{
  echo "label=$label file=${file#$ROOT/}"
  echo "sha256 before=$before mutated=$mutated after=$after restored=$([ "$before" = "$after" ] && echo OK || echo MISMATCH)"
  echo "suite rc=$rc ($([ $rc -ne 0 ] && echo RED || echo GREEN))"
  grep -E 'FAILED|TOTAL|Executed [0-9]+ test|failed \(|error: -\[' "$D/runs/$label.suite.txt" | head -12
} | tee "$D/runs/$label.mut.txt"
