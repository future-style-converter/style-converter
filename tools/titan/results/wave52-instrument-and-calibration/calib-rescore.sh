#!/usr/bin/env bash
# tools/titan/results/wave52-instrument-and-calibration/calib-rescore.sh
#
# wave-52 L12 Calibration-gate recipe, device-free: re-run ONLY Step 7 of
# section-runner.sh (inject-wpt-block.mjs) over a CLONE of the opening gate
# (tools/titan/runs/wave52-calib, never wave52-open — inject rewrites --manifest
# in place) with the instrument under test: the L12-A stamps and the re-frozen
# '…-rootbg-uamargin' refs. Captures are the clone's own bytes, so every cell
# move is instrument-only by construction (verified by the capture-hash check
# in calib-hashcheck.mjs). The --refs-root literal is section-runner.sh's own
# stale `…/white-black-ink-font-lh`, so this also proves the scripts reach the
# new tree through normalizeRefsRoot with no edit.
# Usage: bash …/calib-rescore.sh [section …]   (default: all 30; 3 in parallel)
set -euo pipefail
REPO="$(cd "$(dirname "$0")/../../../.." && pwd)"
RUN="$REPO/tools/titan/runs/wave52-calib"
WPT_REF=9b5435e55e0b54a6cd09c1c563861eb3c999cef1
LOGDIR="$RUN/calib-logs"; mkdir -p "$LOGDIR"
# Refuse to run against anything but the calibration clone.
[[ "$(basename "$RUN")" == wave52-calib ]] || { echo "refusing: $RUN" >&2; exit 2; }
SECTIONS=("$@"); [[ ${#SECTIONS[@]} -gt 0 ]] || SECTIONS=($(ls "$RUN/sections" | sort))
one() {
  local sec="$1" W="$RUN/sections/$1"
  # The exact Step-7 argument list (section-runner.sh), all-platform scope.
  env TITAN_REQUIRE_ALL_COLUMNS=1 node "$REPO/tools/titan/inject-wpt-block.mjs" \
    --manifest "$W/manifest.json" --tests "$W/tests.list" --wpt-ref "$WPT_REF" \
    --run-id wave52-calib \
    --refs-root "$REPO/tools/wpt/refs/$WPT_REF/white-black-ink-font-lh" \
    --capture-log "$W/capture.log" --combined "$RUN/combined/_section-$sec.json" \
    --web-dir "$W/screenshots" --ios-dir "$W/ios-screenshots" --android-dir "$W/android-screenshots" \
    >"$LOGDIR/$sec.log" 2>&1
  echo "$sec rc=$?" >>"$LOGDIR/_status"
}
: >"$LOGDIR/_status"
# Three sections at a time — the host runs eleven other lanes.
for sec in "${SECTIONS[@]}"; do
  one "$sec" || echo "$sec rc=$?" >>"$LOGDIR/_status" &
  while [[ $(jobs -r | wc -l) -ge 3 ]]; do sleep 2; done
done
wait
cat "$LOGDIR/_status"
