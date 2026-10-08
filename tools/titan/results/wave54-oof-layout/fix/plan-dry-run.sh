#!/usr/bin/env bash
# plan-dry-run.sh — L4 fix pass: dry-run the lane's plan-build.py hunks (hunk-for-orchestrator-1 / -2) in every order,
# READ-ONLY on tools/titan/results/wave54-plan/ (the orchestrator's dir): each variant is a patched COPY of plan-build.py
# applied with `git apply --directory` into a throwaway tree under this lane's fix/dry/, then exec'd with __file__ = the
# real plan-build.py path (so it loads the real plan inputs) and `--out` into fix/dry/<variant>/ (plan-build's own dry-run
# option, fix r1 M3). Prints rc, the plan-build summary lines and watchlist-check per variant.
set -uo pipefail
ROOT="$(cd "$(dirname "$0")/../../../../.." && pwd)"; cd "$ROOT"
L=tools/titan/results/wave54-oof-layout; P=tools/titan/results/wave54-plan; D=$L/fix/dry
rm -rf "$D"; mkdir -p "$D"
shasum -a 256 $P/plan-build.py $P/expectations.json $P/watchlist.txt > "$D/plan-dir.before.sha256"
run() { # <variant> <patch…>
  local v="$1"; shift; local t="$D/$v/tree"; mkdir -p "$t/$P" "$D/$v/out"
  cp "$P/plan-build.py" "$t/$P/plan-build.py"
  for h in "$@"; do (cd "$t" && git apply --whitespace=nowarn "$ROOT/$h") || { echo "$v: $h does NOT apply" ; return; }; done
  python3 - "$t/$P/plan-build.py" "$ROOT/$P/plan-build.py" "$D/$v/out" > "$D/$v/plan-build.out.txt" 2>&1 <<'PY'
import sys, runpy
src, real, out = sys.argv[1:4]
code = compile(open(src).read(), real, 'exec')          # tracebacks name the real file
sys.argv = [real, '--out', out]
exec(code, {'__file__': real, '__name__': '__main__'})
PY
  local rc=$?
  RUN=wave53-final WATCH="$D/$v/out/watchlist.txt" node tools/titan/results/wave52-plan/watchlist-check.mjs 2>&1 | tail -1 > "$D/$v/watchlist-check.wave53-final.txt"
  RUN=wave54-open  WATCH="$D/$v/out/watchlist.txt" node tools/titan/results/wave52-plan/watchlist-check.mjs 2>&1 | tail -1 > "$D/$v/watchlist-check.wave54-open.txt"
  echo "== $v ($*) rc=$rc"; grep -E "union capture|watch lines|L4-oof-layout:|L6-web-tail:|predicted f->P|must-not-move exclusions|-> carrier of" "$D/$v/plan-build.out.txt"
  echo "   watchlist-check wave53-final: $(cat $D/$v/watchlist-check.wave53-final.txt) · wave54-open: $(cat $D/$v/watchlist-check.wave54-open.txt)"
  cmp -s "$D/$v/out/expectations.json" "$P/expectations.json" && echo "   expectations.json == installed" || echo "   expectations.json differs from installed"
  cmp -s "$D/$v/out/watchlist.txt" "$P/watchlist.txt" && echo "   watchlist.txt == installed" || echo "   watchlist.txt differs from installed"
}
run base
run h1 $L/hunk-for-orchestrator-1.patch
run h2 $L/hunk-for-orchestrator-2.patch
run h1h2 $L/hunk-for-orchestrator-1.patch $L/hunk-for-orchestrator-2.patch
run h2h1 $L/hunk-for-orchestrator-2.patch $L/hunk-for-orchestrator-1.patch
cmp -s "$D/h1h2/out/expectations.json" "$D/h2h1/out/expectations.json" && echo "order-independent: h1h2 == h2h1 (expectations.json)"
python3 $L/fix/plan-dry-run.json-diff.py > $L/fix/plan-dry-run.json-diff.txt && echo "json diff: $L/fix/plan-dry-run.json-diff.txt"
# Keep the evidence small: drop the throwaway trees (patched plan-build.py copies) and every output JSON but h2 / h1h2's.
rm -rf "$D"/*/tree; for v in base h1 h2h1; do rm -f "$D/$v/out/expectations.json" "$D/$v/out/watchlist.txt"; done
shasum -a 256 $P/plan-build.py $P/expectations.json $P/watchlist.txt | diff - "$D/plan-dir.before.sha256" >/dev/null && echo "plan dir untouched (sha256 before = after)"
