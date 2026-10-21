#!/usr/bin/env bash
# tools/titan/results/wave55-instruments/u2/calibrate.sh — L7 U2's calibration of wire-differential.mjs on REAL data
# (instruments.md §4 U2 "Calibration on real data"; PLAN §2 L7 U2). Read-only on the repo; Chromium-free, gradle-free.
#   (1) the parked wave-54 [W-L3] trees tools/titan/runs/wave54-wl3-diff/{pre,post} (gitignored) against the wave-54
#       pre-registered set → `identical 1397 · content-changed 23 · renumbered 15 · one-sided 0`, exit 0;
#   (2) the same with the counter-suffix line deleted from a copy of the expect file → exit 5, counter-suffix UNEXPECTED;
#   (3) the wave-54 mis-invocation shape (every section dir present, no per-test-ir on either side — what a relative out
#       dir produced) → exit 4, with the wave-54 expect file (its 38 entries read UNREAD, not MISSING) and with an empty one.
# Usage: bash calibrate.sh <checkout holding tools/titan/wire-differential.mjs> [<runs root>]   (runs root default:
#        <checkout>/tools/titan/runs). The scratch dir is mktemp'd and removed.
set -uo pipefail
ROOT="$(cd -P "${1:?checkout}" && pwd -P)"; RUNS="${2:-$ROOT/tools/titan/runs}"
EXP="$ROOT/tools/titan/results/wave55-plan/instruments.wl3-expect.wave54.txt"; S="$(mktemp -d)"
cls() { node "$ROOT/tools/titan/wire-differential.mjs" classify --pre "$1" --post "$2" --tests-root "$RUNS/wave54-open/sections" --expect "$3" --no-status; }
echo "# wire-differential.mjs sha256 $(shasum -a 256 "$ROOT/tools/titan/wire-differential.mjs" | cut -d' ' -f1)"
echo "## (1) parked wave-54 trees, the wave-54 expect file"
cls "$RUNS/wave54-wl3-diff/pre" "$RUNS/wave54-wl3-diff/post" "$EXP" > "$S/1.txt"; echo "exit=$?"; tail -2 "$S/1.txt"
echo "## (2) the same, counter-suffix deleted from the expect file"
grep -v counter-suffix "$EXP" > "$S/e2.txt"; cls "$RUNS/wave54-wl3-diff/pre" "$RUNS/wave54-wl3-diff/post" "$S/e2.txt" > "$S/2.txt"; echo "exit=$?"
grep -E '^(UNEXPECTED|MISSING)' "$S/2.txt"; tail -2 "$S/2.txt"
echo "## (3) the mis-invocation shape (30 section dirs, no per-test-ir)"
for s in $(ls "$RUNS/wave54-final/sections"); do mkdir -p "$S/mis/pre/$s" "$S/mis/post/$s"; : > "$S/mis/pre/$s/extract.log"; done
cls "$S/mis/pre" "$S/mis/post" "$EXP" > "$S/3.txt"; echo "exit=$? (wave-54 expect file; UNREAD lines: $(grep -c '^UNREAD' "$S/3.txt"))"; tail -2 "$S/3.txt"
: > "$S/empty.txt"; cls "$S/mis/pre" "$S/mis/post" "$S/empty.txt" > "$S/3b.txt"; echo "exit=$? (empty expect file)"; tail -2 "$S/3b.txt"
rm -rf "$S"
