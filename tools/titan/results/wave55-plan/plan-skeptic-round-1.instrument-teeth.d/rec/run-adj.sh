#!/usr/bin/env bash
# adjudicate.mjs refusal paths and holds on synthetic records.
A=/Users/dranak/Documents/Projects/Style-Converter/.claude/worktrees/trusting-bohr-bd6fbf/tools/titan/results/wave55-gate/adjudicate.mjs
while IFS= read -r r; do
  node $A $r > o.txt 2>&1; rc=$?
  echo "adjudicate ${r:-<no args>} -> exit $rc :: $(grep -E 'ORDER|NOT A|usage|GEOMETRY PAIR|^ADJUDICATION|^FAIL' o.txt | cut -c1-170 | head -4 | tr '\n' ' ')"
done <<'L'

order-wrong.json
thresholded-final.json
allmet-final-curmismatch.json
allmet-final.json
allmet-final.json --geometry ../out/L1-fixed.json
allmet-final.json --geometry geo-final-pair.json
allmet-pre.json
allmet-pre.json --base wave55-open
L
rm -f o.txt
