#!/bin/bash
# L4 census-base provenance check: swap ONE owned file back to its HEAD bytes,
# run the corpus census pin (expected RED: nothing moves any more), compare the
# census the pin wrote against the committed base file (expected byte-equal:
# HEAD's code reproduces the base), then restore the lane bytes and verify
# sha256 before == after. Usage: census-base-check.sh <label> <owned-file> <now-file> <base-file> <suite-cmd...>
set -u
label=$1; file=$2; now=$3; base=$4; shift 4
ROOT=/Users/dranak/Documents/Projects/Style-Converter/.claude/worktrees/trusting-bohr-bd6fbf
OUT=$ROOT/tools/titan/results/wave54-oof-layout/mutations
before=$(shasum -a 256 "$ROOT/$file" | cut -d' ' -f1)
cp "$ROOT/$file" "$OUT/.$label.restore"
git -C "$ROOT" show "HEAD:$file" > "$ROOT/$file"
swapped=$(shasum -a 256 "$ROOT/$file" | cut -d' ' -f1)
rm -f "$now"
( "$@" ) > "$OUT/$label.run.txt" 2>&1; rc=$?
cp "$OUT/.$label.restore" "$ROOT/$file"; rm -f "$OUT/.$label.restore"
after=$(shasum -a 256 "$ROOT/$file" | cut -d' ' -f1)
{
  echo "label=$label file=$file (swapped to HEAD bytes)"
  echo "sha256 before=$before head=$swapped after=$after restored=$([ "$before" = "$after" ] && echo OK || echo MISMATCH)"
  echo "suite rc=$rc ($([ $rc -ne 0 ] && echo RED || echo GREEN))"
  if [ -f "$now" ]; then cmp -s "$now" "$base" && echo "census written under HEAD code == ${base#$ROOT/} (byte-equal, $(wc -l < "$base") lines)" || echo "census under HEAD code DIFFERS from base: $(diff "$now" "$base" | head -5)"; else echo "census file not written: $now"; fi
} | tee "$OUT/$label.summary.txt"
