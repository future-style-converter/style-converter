#!/usr/bin/env bash
# tools/titan/results/wave55-plan/instruments.wl3-classifier-replay.sh — wave-55 L7 (brief instruments.md §2.2):
# replays the wave-54 [W-L3] CLASSIFIER (the node heredoc of
# tools/titan/results/wave54-hyphenate-character/wl3-wire-differential.sh:46-64, extracted VERBATIM by line range, never
# edited) on (a) the parked wave-54 per-test-IR trees tools/titan/runs/wave54-wl3-diff/ (gitignored; the record's
# 1397 / 23 / 15 / 0) and (b) a synthetic mis-invocation: an out dir whose pre/<sec>/ has logs but no per-test-ir, as a
# relative out dir produced (every artefact landed inside the trees). It shows the exit code the record could not have
# (a script that ends with that node prints and exits 0 whatever it counts). Read-only on the repo; the synthetic dir
# lives under $TMPDIR and is removed. Also prints, per section and side, the extract.log `extracted` / `FAIL` counts.
set -uo pipefail
HERE="$(cd "$(dirname "$0")" && pwd)"; ROOT="$(cd "$HERE/../../../.." && pwd)"
SRC="$ROOT/tools/titan/results/wave54-hyphenate-character/wl3-wire-differential.sh"
JS="$(mktemp -t wl3cls).cjs"; sed -n '47,63p' "$SRC" > "$JS"           # the heredoc body only (between <<'JS' and JS)
echo "# classifier = $SRC:47-63 (sha256 of the script $(shasum -a 256 "$SRC" | cut -c1-16)…)"
echo "## (a) parked wave-54 [W-L3] trees: tools/titan/runs/wave54-wl3-diff"
node "$JS" "$ROOT/tools/titan/runs/wave54-wl3-diff" > "$JS.out"; rc=$?; tail -1 "$JS.out"; echo "classifier exit=$rc"
for side in pre post; do
  ext=0; fl=0; for d in "$ROOT/tools/titan/runs/wave54-wl3-diff/$side"/*/; do
    e=$(grep -c '^extracted ' "$d/extract.log" 2>/dev/null); f=$(grep -c '^FAIL ' "$d/extract.log" 2>/dev/null); ext=$((ext+e)); fl=$((fl+f)); done
  grep -h '^FAIL ' "$ROOT/tools/titan/runs/wave54-wl3-diff/$side"/*/extract.log | cut -c1-160 | sed "s/^/  $side FAIL line: /"
  echo "  $side: extract.log 'extracted' lines $ext · 'FAIL' lines $fl over $(ls -d "$ROOT/tools/titan/runs/wave54-wl3-diff/$side"/*/ | wc -l | tr -d ' ') sections"
done
echo "## (b) synthetic mis-invocation (relative out dir: logs present, per-test-ir absent on BOTH sides, 30 sections)"
T="$(mktemp -d -t wl3mis)"
for s in $(ls "$ROOT/tools/titan/runs/wave54-final/sections"); do mkdir -p "$T/pre/$s" "$T/post/$s"; : > "$T/pre/$s/extract.log"; done
node "$JS" "$T" > "$JS.out"; rc=$?; tail -1 "$JS.out"; echo "classifier exit=$rc   <- the record's mis-invoked first run: 30 one-sided, exit 0"
rm -rf "$T" "$JS" "$JS.out"
