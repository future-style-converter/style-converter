#!/usr/bin/env bash
# refresh-run.sh — wave 51 PR 3: the baseline refresh for the label chrome, as
# design-record.md §5 prescribes, in two halves with the REVIEW between them.
#
#   refresh-run.sh measure   BASELINE=1 ./test-all.sh <fixture> for each of the four
#                            baselined fixtures; archives each fixture's fresh
#                            captures + report under tools/titan/runs/wave51-labels/
#                            <fixture-stem>/ and runs refresh-check.mjs on it.
#                            Records every exit code. Nothing is refreshed.
#   refresh-run.sh refresh   UPDATE_BASELINE=1 ./test-all.sh <fixture> ×4, then
#                            node tools/visual/baseline-stats.mjs, then
#                            BASELINE=1 ./test-all.sh --gate-set (expect exit 0 ×8),
#                            then node --test tools/visual/label-chrome-tripwire.test.mjs.
#
# Preconditions (the caller's job): one -read-only emulator booted and
# ANDROID_SERIAL exported; the seed simulator booted; browsers quiet; JDK 21.
set -uo pipefail
ROOT="$(cd "$(dirname "$0")/../../../.." && pwd)"
RUNS="$ROOT/tools/titan/runs/wave51-labels"
FIXTURES=(fixtures/visual-test.json fixtures/properties/effects/filter-sepia-amounts.json fixtures/properties/sizing/aspect-ratio.json fixtures/properties/effects/filter-grayscale-basis.json)
export JAVA_HOME="${JAVA_HOME:-$(/usr/libexec/java_home -v 21)}"
log() { printf '[refresh %s] %s\n' "$(date -u +%H:%M:%S)" "$*"; }
cd "$ROOT"
case "${1:-}" in
  measure)
    mkdir -p "$RUNS"; : > "$RUNS/measure-exits.txt"
    for fx in "${FIXTURES[@]}"; do
      stem="$(basename "$fx" .json)"; dest="$RUNS/$stem"; rm -rf "$dest"; mkdir -p "$dest"
      log "BASELINE=1 ./test-all.sh $fx"
      BASELINE=1 ./test-all.sh "$fx" > "$dest/test-all.log" 2>&1; rc=$?
      echo "$fx exit $rc" >> "$RUNS/measure-exits.txt"; log "$stem exit $rc"
      cp -R tools/visual/report/images "$dest/images" 2>/dev/null; cp tools/visual/report/manifest.json "$dest/" 2>/dev/null
      grep -E 'REGRESS|stale|STALE|divergen|exit [0-9]|✗' "$dest/test-all.log" | grep -v '^\s*$' | tail -20 | cut -c1-200 > "$dest/verdict-lines.txt"
      node tools/titan/results/wave51-A/refresh-check.mjs --images "$dest/images" --out "$dest/refresh-check.json" > "$dest/refresh-check.txt" 2>&1
      tail -1 "$dest/refresh-check.txt"
    done
    log "measure done — exits:"; cat "$RUNS/measure-exits.txt"
    ;;
  refresh)
    for fx in "${FIXTURES[@]}"; do
      log "UPDATE_BASELINE=1 ./test-all.sh $fx"
      UPDATE_BASELINE=1 ./test-all.sh "$fx" > "$RUNS/update-$(basename "$fx" .json).log" 2>&1; rc=$?; log "update $(basename "$fx" .json) exit $rc"
      (( rc != 0 )) && { log "UPDATE refused (exit $rc) — stop and read the log"; exit "$rc"; }
    done
    log "node tools/visual/baseline-stats.mjs"; node tools/visual/baseline-stats.mjs > "$RUNS/baseline-stats.log" 2>&1; log "baseline-stats exit $?"
    log "BASELINE=1 ./test-all.sh --gate-set"; BASELINE=1 ./test-all.sh --gate-set > "$RUNS/gate-set-after.log" 2>&1; rc=$?; log "gate-set exit $rc"
    grep -E '^\s+exit ' "$RUNS/gate-set-after.log"
    log "tripwire"; node --test tools/visual/label-chrome-tripwire.test.mjs > "$RUNS/tripwire-after.log" 2>&1; rc=$?; log "tripwire exit $rc"; grep -E '^# (tests|pass|fail)' "$RUNS/tripwire-after.log"
    ;;
  *) echo "usage: $0 measure | refresh"; exit 2 ;;
esac
