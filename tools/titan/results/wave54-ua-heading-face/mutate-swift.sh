#!/usr/bin/env bash
# Wave 54 lane L5 — one executed mutation of the SwiftUI mechanism (twin of mutate-compose.sh):
#   mutate-swift.sh <name> <file> <old-literal-file> <new-literal-file>
# swaps ONE exact occurrence, runs the lane's Catalyst classes (red expected), restores the
# file byte-exact (sha256 logged), re-runs (green expected); prints "Executed N tests" + failures.
set -u
ROOT=/Users/dranak/Documents/Projects/Style-Converter/.claude/worktrees/trusting-bohr-bd6fbf
OUT=$ROOT/tools/titan/results/wave54-ua-heading-face/mutations
name=$1; file=$2; oldf=$3; newf=$4
mkdir -p "$OUT"; bak="$OUT/$name.bak"; cp "$ROOT/$file" "$bak"
before=$(shasum -a 256 "$ROOT/$file" | cut -d' ' -f1)
python3 - "$ROOT/$file" "$oldf" "$newf" <<'PY'
import sys
p, o, n = sys.argv[1:4]
s = open(p, encoding='utf-8').read(); old = open(o, encoding='utf-8').read(); new = open(n, encoding='utf-8').read()
assert s.count(old) == 1, f'old literal occurs {s.count(old)} times'
open(p, 'w', encoding='utf-8').write(s.replace(old, new))
PY
echo "[$name] file=$file sha256 before=$before mutated=$(shasum -a 256 "$ROOT/$file" | cut -d' ' -f1)"
run() {
  (cd "$ROOT" && xcodebuild test -scheme StyleConverterRuntime -destination 'platform=macOS,variant=Mac Catalyst,arch=arm64' \
     -only-testing:StyleConverterRuntimeTests/UAElementFontRuleTests -only-testing:StyleConverterRuntimeTests/UAHeadingFoldGateTests > "$OUT/$name.$1.log" 2>&1)
  echo "[$name] $1 run rc=$?"
  grep -E 'Executed [0-9]+ test.*\(' "$OUT/$name.$1.log" | tail -1
  grep -E "error: -\[|: error: " "$OUT/$name.$1.log" | sed -E 's/^.*\/(UA[A-Za-z]+Tests\.swift:[0-9]+): error: -\[[^ ]+ ([^]]+)\] : (.{0,150}).*/    RED: \1 \2 :: \3/' | sort -u | head -12
}
run red
cp "$bak" "$ROOT/$file"; rm -f "$bak"
after=$(shasum -a 256 "$ROOT/$file" | cut -d' ' -f1)
echo "[$name] restored sha256 after=$after $( [ "$before" = "$after" ] && echo BYTE-EXACT || echo MISMATCH )"
run green
