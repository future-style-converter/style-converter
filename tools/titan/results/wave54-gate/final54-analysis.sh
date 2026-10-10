# RECORD of what ran at the wave-54 closing gate (copied from the session scratchpad at ship time; ROOT below is the wave-54 worktree path —
# edit it to reproduce on another checkout). The gate note (_note.md "Closing gate") cites this file by name.
#!/usr/bin/env bash
# Wave 54 closing-gate read-out — every command is the pre-registered string from
# tools/titan/results/wave54-plan/expectations.json (scoreOfRecord, scoreReadout, geometryGate.closingCmd,
# adjudicate, probeRun.readOut.floors shape, abRead) — PLAN §8 step 11. Order: scores → geometry (R6 = its exit)
# → adjudicate (reads the --movers 0 record + the geometry JSON) → probe-readout → control → sheets → census → predictions.
set -uo pipefail
ROOT=/Users/dranak/Documents/Projects/Style-Converter/.claude/worktrees/trusting-bohr-bd6fbf; cd "$ROOT"
G=tools/titan/results/wave54-gate; P=tools/titan/results/wave54-plan; OUT=$G/final; mkdir -p "$OUT"
PRE=wave54-open; CUR=wave54-final

echo "## driver"
for s in tools/titan/runs/$CUR/gate-driver/*.status; do printf '%s: %s\n' "$(basename "$s" .status)" "$(cat "$s")"; done > "$OUT/sections.status.txt"
echo "sections OK: $(grep -c ': OK' "$OUT/sections.status.txt") / $(wc -l < "$OUT/sections.status.txt" | tr -d ' ')"

echo; echo "## scoreOfRecord (--movers 0, watchlist) → $G/score-final.json"
node tools/titan/score-gate.mjs $PRE $CUR --watch $P/watchlist.txt --movers 0 --json $G/score-final.json > $G/score-final.movers0.txt 2>&1; echo "rc=$?"
grep -E 'per-cell' $G/score-final.movers0.txt | cut -c1-160

echo; echo "## scoreReadout (--movers 0.005, watchlist) → $G/score-final.movers0005.json (the corpus snapshot's source; adjudicates nothing)"
node tools/titan/score-gate.mjs $PRE $CUR --watch $P/watchlist.txt --movers 0.005 --json $G/score-final.movers0005.json > $G/score-final.txt 2>&1; echo "rc=$?"
grep -E 'per-cell|^  (GAINED|LOST)' -A3 $G/score-final.txt | head -14 | cut -c1-160

echo; echo "## geometry gate (closingCmd; R6 = exit: 0 pass, 1 gating FAIL/self-check, 2 STALE, 3 UNMEASURED gating key)"
python3 $P/geometry-gate.py $CUR --base $PRE --json $G/geometry-final.json > $G/geometry-final.out.txt 2>&1; echo "rc=$?"
grep -E 'FAIL|UNMEASURED|^geometry-gate|by class|CONTROL' $G/geometry-final.out.txt | head -24 | cut -c1-200

echo; echo "## adjudicate R1–R9 (reads score-final.json + geometry-final.json; exit 0 = R1–R5 hold)"
node $G/adjudicate.mjs $G/score-final.json --geometry $G/geometry-final.json > $G/adjudicate-final.txt 2>&1; echo "rc=$?"
grep -E '^(ok|FAIL) R|ADJUDICATION|MOVED|WITHDRAWN|DEGENERATE|RETIRED|ORDER|NOT A|GEOMETRY PAIR' $G/adjudicate-final.txt | head -40 | cut -c1-180

echo; echo "## probe-readout (rules 1–3 over the --movers 0 record of the full gate)"
node $P/probe-readout.mjs $G/score-final.json > "$OUT/probe-readout.txt" 2>&1; echo "rc=$?"
grep -E '^summary|^PROBE|FIRES|LEAK|rule|REVERT|fired' "$OUT/probe-readout.txt" | head -16 | cut -c1-200

echo; echo "## control (union carriers minus the withdrawn; CBB-android's carriers are now leaks if changed)"
node $G/control-check.mjs $PRE $CUR --json $G/control-final-union.json > $G/control-final-union.txt 2>&1; echo "rc=$?"
grep -E 'withdrawn|LEAK|^total|^wire|CONTROL' $G/control-final-union.txt | cut -c1-170

echo; echo "## review sheets (gained / lost / movers from the 0.005 record) + red-square census"
for list in gained lost movers; do node tools/titan/results/wave52-gate/review-sheets.mjs $G/score-final.movers0005.json $list $PRE $CUR "$OUT/sheets-$list" 2>&1 | tail -1 | cut -c1-140; done
node tools/titan/red-square-census.mjs $CUR > "$OUT/red-square-census.txt" 2>&1; tail -1 "$OUT/red-square-census.txt" | cut -c1-220

echo; echo "## every prediction row (open → final)"
node tools/titan/results/wave52-gate/cells.mjs "$(cat $G/stage2/prediction-terms.txt)" $PRE $CUR > "$OUT/prediction-cells.txt" 2>&1
wc -l < "$OUT/prediction-cells.txt" | xargs echo "rows:"; grep -E ' f .*→ P |P .*→ f ' "$OUT/prediction-cells.txt" | cut -c1-150

echo; echo "outputs: $OUT/ and $G/{score-final.json,score-final.movers0.txt,score-final.movers0005.json,score-final.txt,geometry-final.*,adjudicate-final.txt,control-final-union.*}"
