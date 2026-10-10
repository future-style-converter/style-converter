#!/usr/bin/env bash
# plan-hunks-dry.sh — L4 RE-VERIFIER (wave 54): independent dry run of hunk-for-orchestrator-1/-2 against plan-build.py.
# Read-only on tools/titan/results/wave54-plan/: each variant patches a COPY (git apply in a throwaway dir), runs it with
# __file__ = the real path (so it loads the real plan inputs) and plan-build's own `--out` option into reverify/dry/<v>/,
# then counts the generated expectations.json with its OWN reader (count-expectations.py), not plan-build's summary lines.
set -uo pipefail
ROOT="$(cd "$(dirname "$0")/../../../../.." && pwd)"; cd "$ROOT"
L=tools/titan/results/wave54-oof-layout; P=tools/titan/results/wave54-plan; D=$L/reverify/dry
rm -rf "$D"; mkdir -p "$D"
shasum -a 256 $P/plan-build.py $P/expectations.json $P/watchlist.txt > "$D/plan-dir.before.sha256"
run() { # <variant> <patch…>
  local v="$1"; shift; local t="$D/$v/tree"; mkdir -p "$t/$P" "$D/$v/out"
  cp "$P/plan-build.py" "$t/$P/plan-build.py"
  for h in "$@"; do (cd "$t" && git apply --whitespace=nowarn "$ROOT/$h") || { echo "$v: $h does NOT apply"; return; }; done
  python3 - "$t/$P/plan-build.py" "$ROOT/$P/plan-build.py" "$D/$v/out" > "$D/$v/plan-build.out.txt" 2>&1 <<'PY'
import sys
src, real, out = sys.argv[1:4]
code = compile(open(src).read(), real, 'exec')
sys.argv = [real, '--out', out]
exec(code, {'__file__': real, '__name__': '__main__'})
PY
  local rc=$?
  echo "== $v ($*) plan-build rc=$rc"
  python3 $L/reverify/count-expectations.py "$D/$v/out/expectations.json" "$D/$v/out/watchlist.txt"
  for run in wave53-final wave54-open; do
    printf '   watchlist-check %s: %s\n' $run "$(RUN=$run WATCH="$D/$v/out/watchlist.txt" node tools/titan/results/wave52-plan/watchlist-check.mjs 2>&1 | tail -1)"
  done
  cmp -s "$D/$v/out/expectations.json" "$P/expectations.json" && echo "   expectations.json == installed" || echo "   expectations.json differs from installed"
  cmp -s "$D/$v/out/watchlist.txt" "$P/watchlist.txt" && echo "   watchlist.txt == installed" || echo "   watchlist.txt differs from installed"
}
run base
run h1 $L/hunk-for-orchestrator-1.patch
run h2 $L/hunk-for-orchestrator-2.patch
run h1h2 $L/hunk-for-orchestrator-1.patch $L/hunk-for-orchestrator-2.patch
run h2h1 $L/hunk-for-orchestrator-2.patch $L/hunk-for-orchestrator-1.patch
cmp -s "$D/h1h2/out/expectations.json" "$D/h2h1/out/expectations.json" && echo "order-independent: h1h2 == h2h1 (expectations.json)" || echo "ORDER-DEPENDENT"
rm -rf "$D"/*/tree
shasum -a 256 $P/plan-build.py $P/expectations.json $P/watchlist.txt | diff - "$D/plan-dir.before.sha256" >/dev/null && echo "plan dir untouched (sha256 before = after)" || echo "PLAN DIR CHANGED"
