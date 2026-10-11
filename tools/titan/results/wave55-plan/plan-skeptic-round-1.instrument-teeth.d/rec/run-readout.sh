#!/usr/bin/env bash
# probe-readout.mjs refusal / fire paths on synthetic records (base-first relabels of the opening --movers 0 record).
P=/Users/dranak/Documents/Projects/Style-Converter/.claude/worktrees/trusting-bohr-bd6fbf/tools/titan/results/wave55-plan/probe-readout.mjs
while IFS= read -r r; do
  node $P $r > o.txt 2>&1; rc=$?
  echo "probe-readout ${r:-<no args>} -> exit $rc :: $(grep -E 'ORDER|NOT A|usage|^PROBE|MISSING listed|NOTE|summary|FIRES|LEAK |MAY-MOVE|may-move  |  [A-Z0-9-]+ [A-Za-z0-9-]+  \(revertOrder' o.txt | cut -c1-200 | head -6 | tr '\n' ' ')"
done <<'L'

order-wrong.json
thresholded.json
pre-identity.json --stage1
allmet-pre.json --stage1
allmet-pre-missing-masking.json --stage1
allmet-pre.json --stage1 --sections css-txt
allmet-pre.json --stage1 --sections css-text,css-txt
allmet-pre-mnm-leak.json --stage1
allmet-pre-maymove-in.json --stage1
allmet-pre-maymove-out.json --stage1
allmet-pre-carrier-lost.json --stage1
carrier-unmeasured.json --stage1 --lanes L5
allmet-final.json --stage1
allmet-pre.json --stage1 --lanes L9
L
rm -f o.txt
