#!/usr/bin/env bash
# control-check.mjs refusal / blind paths: pre = wave54-final, post = a fake run under ccruns/ (symlinks to wave54-final).
C=/Users/dranak/Documents/Projects/Style-Converter/.claude/worktrees/trusting-bohr-bd6fbf/tools/titan/results/wave55-gate/control-check.mjs
D=$(cd "$(dirname "$0")" && pwd -P)/ccruns
cd /Users/dranak/Documents/Projects/Style-Converter/.claude/worktrees/trusting-bohr-bd6fbf
while IFS= read -r r; do
  r="${r//@/$D}"
  nice -n 19 node $C $r > /tmp/cc.$$.txt 2>&1; rc=$?
  echo "control-check ${r//$D/<ccruns>} -> exit $rc :: $(grep -E 'usage|no lane|^css-text|^filter-effects|LEAK|RING|may-move|WIRE|^CONTROL|^total|^wire' /tmp/cc.$$.txt | grep -vE 'compared [0-9]+  identical +[0-9]+  re-encoded  0  changed  0' | cut -c1-150 | head -6 | tr '\n' ' ')"
done <<'L'

wave54-final no-such-run
wave54-final @/identity --lane L9
wave54-final @/identity --sections css-text
wave54-final @/identity --sections css-txt
wave54-final @/no-css-text --sections css-text
wave54-final @/no-ios-dir --sections css-text
wave54-final @/capture-gone --sections css-text
wave54-final @/wire-doc-gone --sections css-text
wave54-final @/leak --sections css-text
wave54-final @/carrier --sections css-text
wave54-final @/ring --sections filter-effects
L
rm -f /tmp/cc.$$.txt
