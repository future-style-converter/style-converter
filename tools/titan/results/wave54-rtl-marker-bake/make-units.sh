#!/usr/bin/env bash
# tools/titan/results/wave54-rtl-marker-bake/make-units.sh — proves this lane's two revert units replay to the lane's
# tree byte-for-byte, and that each unit's state is green ON ITS OWN, in a throwaway export (NOT a git worktree):
#   1. export HEAD's tools/titan + tools/visual + package.json (git archive) and link the repo's node_modules;
#   2. apply unit-P.patch (git apply --check first) → sha256 of bidi-bake.mjs / bidi-bake.test.mjs must equal state-P/*.txt
#      (the P-state bytes, kept as .txt so no tooling glob ever loads them as modules);
#      bidi-marker-bake.mjs must NOT exist; run the lane's three focused suites → must be green (no marker module);
#   3. apply unit-Mprime.patch → sha256 of the three owned files must equal the SHARED TREE's; run the suites again.
# Prints one line per check; exits 1 on any mismatch or red suite. Usage: make-units.sh [export-dir]
set -uo pipefail
ROOT="$(cd "$(dirname "$0")/../../../.." && pwd)"; L="$ROOT/tools/titan/results/wave54-rtl-marker-bake"
EXP="${1:-$(mktemp -d)}"; rm -rf "$EXP"; mkdir -p "$EXP"
fail=0; say() { echo "$1"; if [[ "$1" == FAIL* ]]; then fail=1; fi; return 0; }
h() { shasum -a 256 "$1" | cut -d' ' -f1; }
suites() { (cd "$EXP" && nice -n 19 node --test tools/titan/bidi-bake.test.mjs tools/titan/counter-style-bake.test.mjs tools/titan/counter-bake.test.mjs 2>&1 | grep -E '^# (pass|fail|skipped)' | tr '\n' ' '); }
(cd "$ROOT" && git archive HEAD tools/titan tools/visual package.json) | tar -x -C "$EXP"; ln -s "$ROOT/node_modules" "$EXP/node_modules"
echo "export: $EXP (HEAD $(git -C "$ROOT" rev-parse --short HEAD))"
(cd "$EXP" && git apply --check "$L/unit-P.patch" && git apply "$L/unit-P.patch") && say "ok   unit-P.patch applies on HEAD" || say "FAIL unit-P.patch does not apply"
for f in bidi-bake.mjs bidi-bake.test.mjs; do
  [[ "$(h "$EXP/tools/titan/$f")" == "$(h "$L/state-P/$f.txt")" ]] && say "ok   P state $f = state-P/$f.txt ($(h "$L/state-P/$f.txt" | cut -c1-12))" || say "FAIL P state $f differs from state-P/$f.txt"
done
[[ ! -e "$EXP/tools/titan/bidi-marker-bake.mjs" ]] && say "ok   P state has no bidi-marker-bake.mjs" || say "FAIL P state carries the marker module"
r=$(suites); [[ "$r" == *"# fail 0"* ]] && say "ok   P state suites: $r" || say "FAIL P state suites: $r"
(cd "$EXP" && git apply --check "$L/unit-Mprime.patch" && git apply "$L/unit-Mprime.patch") && say "ok   unit-Mprime.patch applies on the P state" || say "FAIL unit-Mprime.patch does not apply"
for f in bidi-bake.mjs bidi-bake.test.mjs bidi-marker-bake.mjs; do
  [[ "$(h "$EXP/tools/titan/$f")" == "$(h "$ROOT/tools/titan/$f")" ]] && say "ok   P+M′ state $f = shared tree ($(h "$ROOT/tools/titan/$f" | cut -c1-12))" || say "FAIL P+M′ state $f differs from the shared tree"
done
r=$(suites); [[ "$r" == *"# fail 0"* ]] && say "ok   P+M′ state suites: $r" || say "FAIL P+M′ state suites: $r"
exit $fail
