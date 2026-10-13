#!/usr/bin/env bash
# tools/titan/results/wave55-plan/flex-zero-gap-rules.fakes-run.sh — wave-55 L7 (brief instruments.md §7): reads the
# real runs and EVERY recorded flex-gap fake (wave-54 skeptic r2 / fix r2 / skeptic r3 / fix r3 / verify-r3) plus the
# wave-55 row-rule fakes through BOTH the wave-54 probe (record, unchanged) and the wave-55 copy, side by side.
# Read-only: PIL reads of PNGs, no device, no build. Output: flex-zero-gap-rules.geometry.out.txt beside this file.
set -uo pipefail
HERE="$(cd "$(dirname "$0")" && pwd)"
OLD="$HERE/../wave54-plan/flex-zero-gap-rules.geometry.py"
NEW="$HERE/flex-zero-gap-rules.geometry.py"
# Run ids resolve under tools/titan/runs/, so fakes are named relative to it (geometry_common.paths).
RUNS=(wave53-final wave54-open wave54-final
  ../results/wave54-plan/skeptic-r2/fake-gap
  ../results/wave54-plan/fix-r2/fake-gap-blue ../results/wave54-plan/fix-r2/fake-gap-line3
  ../results/wave54-plan/skeptic-r3/fake-gap-vshift5 ../results/wave54-plan/skeptic-r3/fake-gap-line2-short4
  ../results/wave54-plan/skeptic-r3/fake-gap-short6 ../results/wave54-plan/skeptic-r3/fake-gap-line1-short4
  ../results/wave54-plan/fix-r3/fake-gap-vshift3-up ../results/wave54-plan/fix-r3/fake-gap-line3-short3
  ../results/wave54-plan/verify-r3/gap/fake-ref-as-run
  ../results/wave54-plan/verify-r3/gap/fake-red-l1r-down2 ../results/wave54-plan/verify-r3/gap/fake-red-l3r-up2
  ../results/wave54-plan/verify-r3/gap/fake-blue-r1-short5 ../results/wave54-plan/verify-r3/gap/fake-blue-r2-lshort4
  ../results/wave55-plan/instruments-flex-gap-fakes/fake-blue-r1-lshort2
  ../results/wave55-plan/instruments-flex-gap-fakes/fake-blue-r1-split1
  ../results/wave55-plan/instruments-flex-gap-fakes/fake-blue-r1-moved5
  ../results/wave55-plan/instruments-flex-gap-fakes/fake-blue-r2-hshift2
  ../results/wave55-plan/instruments-flex-gap-fakes/fake-blue-none
  ../results/wave55-plan/instruments-flex-gap-fakes/fake-blue-r1-short1)
{
  echo "# flex-zero-gap-rules: wave-54 probe (OLD, record) vs wave-55 copy (NEW), $(date -u +%Y-%m-%dT%H:%M:%SZ), HEAD $(git -C "$HERE" rev-parse --short HEAD)"
  echo "# sha256 OLD $(shasum -a 256 "$OLD" | cut -c1-16)…  NEW $(shasum -a 256 "$NEW" | cut -c1-16)…"
  for r in "${RUNS[@]}"; do
    echo "## $r"
    for which in OLD NEW; do
      f=$([ $which = OLD ] && echo "$OLD" || echo "$NEW")
      out="$(nice -n 19 python3 "$f" "$r")"; rc=$?
      # Print only the verdict (the measurement dict is long); keep label and reason.
      printf '%s\n' "$out" | sed -E 's/^(flex\/flex-gap-decorations-033 [a-z]+ +)\{.*\} → /\1/' | sed "s/^/  $which  /"
      echo "  $which  exit=$rc"
    done
  done
} > "$HERE/flex-zero-gap-rules.geometry.out.txt" 2>&1
echo "wrote $HERE/flex-zero-gap-rules.geometry.out.txt"
