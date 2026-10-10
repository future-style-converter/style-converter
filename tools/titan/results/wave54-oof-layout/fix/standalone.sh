#!/usr/bin/env bash
# standalone.sh — L4 fix pass (skeptic defect 2): executed stand-alone checks of the OOF unit split that
# hunk-for-orchestrator-3.patch gives land-units.sh. Simulates the ONLY lane-dir effect of reverting one OOF unit — its
# census base file disappears — by moving that file aside, runs the OTHER unit's focused suite, and restores the file
# byte-exact (sha256 before = after, logged). Rows:
#   RED   census-swift.base.txt aside → Catalyst OutOfFlowContainingBlockTests (the installed land-units.sh puts this file
#         in OOF-android, so an OOF-android revert does exactly this): the skeptic's repro, expected 1 failure.
#   GREEN census-compose.base.txt aside (an OOF-android revert under hunk-3) → Catalyst OutOfFlowContainingBlockTests +
#         FixedHoistTests, expected 0 failures, N > 0.
#   GREEN census-swift.base.txt aside (an OOF-ios revert under hunk-3) → Compose *OutOfFlowContainingBlock* +
#         *CanvasRootHoist*, expected 0 failures, the corpus pin RUN (not skipped).
# Usage: standalone.sh red|green-ios|green-android
set -uo pipefail
ROOT="$(cd "$(dirname "$0")/../../../../.." && pwd)"; cd "$ROOT"
L=tools/titan/results/wave54-oof-layout; F=$L/fix; LOG=$F/runs/standalone.log
aside() { # <file> <label> <cmd…>: move <file> aside, run, restore, log hashes
  local f="$1" label="$2"; shift 2; local before after
  before=$(shasum -a 256 "$f" | awk '{print $1}'); mv "$f" "$f.aside"
  "$@"; local rc=$?
  mv "$f.aside" "$f"; after=$(shasum -a 256 "$f" | awk '{print $1}')
  echo "$(date -u +%FT%TZ) $label file=$f before=$before after=$after restored=$([ "$before" = "$after" ] && echo OK || echo MISMATCH) rc=$rc" | tee -a "$LOG"
}
case "${1:-}" in
  red)           aside $L/census-swift.base.txt   RED-oofios-without-swift-base      bash $F/sw.sh RED-oofios-without-swift-base OutOfFlowContainingBlockTests ;;
  green-ios)     aside $L/census-compose.base.txt GREEN-oofios-without-compose-base  bash $F/sw.sh GREEN-oofios-without-compose-base OutOfFlowContainingBlockTests FixedHoistTests ;;
  green-android) aside $L/census-swift.base.txt   GREEN-oofandroid-without-swift-base bash $F/kt.sh GREEN-oofandroid-without-swift-base '*OutOfFlowContainingBlock*' '*CanvasRootHoist*' ;;
  *) echo "usage: $0 red|green-ios|green-android" >&2; exit 2 ;;
esac
