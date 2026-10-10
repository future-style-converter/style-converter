#!/usr/bin/env bash
# standalone.sh — L4 RE-VERIFIER (wave 54): simulate the ONE cross-native effect of reverting an OOF unit — the lane-dir
# file that unit's commit deletes — and run the OTHER native's focused suite. Usage: standalone.sh <variant>
#   installed-oofandroid-revert : installed land-units.sh puts "$L4" in OOF-android → its revert deletes census-swift.base.txt
#                                 → Catalyst OutOfFlowContainingBlockTests (expected RED)
#   patched-oofandroid-revert   : hunk-3 → OOF-android deletes census-compose.base.txt only → Catalyst OOF + FixedHoist (GREEN)
#   patched-oofios-revert       : hunk-3 → OOF-ios deletes census-swift.base.txt → Compose OOF + CanvasRootHoist (GREEN)
#   control-compose-own-base    : negative control — Compose's OWN base aside → Compose OOF (expected RED: the pin reads it live)
# The file is moved aside and restored by a trap; sha256 before/after is logged (reverify/runs/standalone.log).
set -uo pipefail
D="$(cd "$(dirname "$0")/.." && pwd)"; RV=$D/reverify; LOG=$RV/runs/standalone.log; mkdir -p "$RV/aside" "$RV/runs"
case "$1" in
  installed-oofandroid-revert) f=census-swift.base.txt ;;
  patched-oofandroid-revert)   f=census-compose.base.txt ;;
  patched-oofios-revert)       f=census-swift.base.txt ;;
  control-compose-own-base)    f=census-compose.base.txt ;;
  *) echo "unknown variant $1" >&2; exit 2 ;;
esac
before=$(shasum -a 256 "$D/$f" | cut -d' ' -f1)
restore() { mv "$RV/aside/$f" "$D/$f"; after=$(shasum -a 256 "$D/$f" | cut -d' ' -f1)
  echo "$(date -u +%FT%TZ) $1 restored $f before=$before after=$after $([ "$before" = "$after" ] && echo OK || echo MISMATCH)" >> "$LOG"; }
mv "$D/$f" "$RV/aside/$f"; trap 'restore "$1"' EXIT
echo "$(date -u +%FT%TZ) $1 moved aside $f sha256=$before" >> "$LOG"
case "$1" in
  installed-oofandroid-revert) bash "$RV/sw.sh" RV-$1 OutOfFlowContainingBlockTests ;;
  patched-oofandroid-revert)   bash "$RV/sw.sh" RV-$1 OutOfFlowContainingBlockTests FixedHoistTests ;;
  patched-oofios-revert)       bash "$RV/kt.sh" RV-$1 '*OutOfFlowContainingBlock*' '*CanvasRootHoist*' ;;
  control-compose-own-base)    bash "$RV/kt.sh" RV-$1 '*OutOfFlowContainingBlock*' ;;
esac
echo "suite rc=$?"
