#!/usr/bin/env bash
# skeptic/mirror.sh — wave 54 L7 SKEPTIC (independent of the lane's sim-post-seed.sh): build a throwaway tree from
# `git archive HEAD -- tools/visual fixtures` (so the unit is measured on HEAD, not on the shared working tree that
# carries six other lanes' edits), optionally overlay the three U1 files from the working tree and/or the 18 seeded
# PNGs, optionally apply ONE python-literal replacement to one mirrored U1 file, then run the real tripwire there.
#   bash mirror.sh <head|u1|head-seed|u1-seed> [file-in-tools/visual] [old] [new]
# Prints: the PNG count, node:test's tests/pass/fail, every exempt-entry / ATC / RED / HINT / not ok line; exit = node's.
set -uo pipefail                                                     # report, do not abort
ROOT="$(cd "$(dirname "$0")/../../../../.." && pwd)"                 # repo root
MODE="$1"; MF="${2:-}"; OLD="${3:-}"; NEW="${4:-}"                   # mode + optional mutation
M="$(mktemp -d "${TMPDIR:-/tmp}/l7-skeptic-mirror.XXXXXX")"          # throwaway mirror
trap 'rm -rf "$M"' EXIT                                               # never left behind
(cd "$ROOT" && git archive HEAD -- tools/visual fixtures) | tar -x -C "$M"   # HEAD's tools/visual + tracked fixtures
ln -s "$ROOT/node_modules" "$M/node_modules"                          # pngjs
case "$MODE" in u1|u1-seed) cp "$ROOT"/tools/visual/{label-chrome-tripwire.test.mjs,label-chrome-check.mjs,label-chrome-exempt.json} "$M/tools/visual/";; esac # U1 overlay
case "$MODE" in *-seed) cp "$ROOT"/tools/titan/results/wave54-plan/label-chrome-all-reset.seeded-77fe41e8/*.png "$M/tools/visual/baseline/";; esac # U2-seed overlay
if [[ -n "$MF" ]]; then                                               # one exact, single-occurrence replacement
  python3 - "$M/tools/visual/$MF" "$OLD" "$NEW" <<'PY' || exit 99
import sys; p, old, new = sys.argv[1:4]; t = open(p, encoding='utf8').read(); n = t.count(old)
if n != 1: print(f'MUTATION ANCHOR occurs {n}x in {p}'); sys.exit(1)
open(p, 'w', encoding='utf8').write(t.replace(old, new)); print(f'MUTATION applied to mirror {p.split("/tools/visual/")[1]}')
PY
fi
echo "mode=$MODE PNGs=$(ls "$M/tools/visual/baseline" | grep -c '\.png$')"
node --test "$M/tools/visual/label-chrome-tripwire.test.mjs" > "$M/run.txt" 2>&1; rc=$?
grep -E '^# (tests|pass|fail) ' "$M/run.txt"
grep -E 'exempt-entry|_ATC_|_reset |_span |RED|HINT|^not ok' "$M/run.txt" | grep -v '^# Subtest' | sed -e "s|$M|<mirror>|g" | head -40
echo "exit=$rc"; exit $rc
