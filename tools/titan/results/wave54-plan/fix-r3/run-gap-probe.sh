#!/bin/bash
# fix r3 (R3-S3): run one flex-zero-gap-rules.geometry.py over the two real runs and every fake on disk (r2, fix-r2,
# skeptic-r3, fix-r3), printing each line with the measurement dict elided and each exit code.
# Usage: bash fix-r3/run-gap-probe.sh <probe.py>   (from tools/titan/results/wave54-plan/)
R=../results/wave54-plan
for run in wave53-final wave54-open $R/skeptic-r2/fake-gap $R/fix-r2/fake-gap-blue $R/fix-r2/fake-gap-line3 \
           $R/skeptic-r3/fake-gap-vshift5 $R/skeptic-r3/fake-gap-line2-short4 $R/skeptic-r3/fake-gap-short6 \
           $R/skeptic-r3/fake-gap-line1-short4 $R/fix-r3/fake-gap-vshift3-up $R/fix-r3/fake-gap-line3-short3; do
  echo "## $run"
  out=$(nice -n 19 python3 "$1" "$run"); rc=$?
  echo "$out" | sed -E 's/\{.*\} →/{…} →/'
  echo "exit=$rc"
done
