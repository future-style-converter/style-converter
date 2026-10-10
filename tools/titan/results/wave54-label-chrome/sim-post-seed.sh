#!/usr/bin/env bash
# tools/titan/results/wave54-label-chrome/sim-post-seed.sh — wave 54 lane L7 (label-chrome): run the REAL tripwire
# (tools/visual/label-chrome-tripwire.test.mjs + label-chrome-check.mjs + label-chrome-exempt.json, as they are in the
# working tree) against the tree U2-seed WILL produce, without touching tools/visual/baseline/ (the 18 PNG paths are
# U2-seed's, landed only after the device seeding on the closing tree, PLAN §8 step 10).
# It builds a throwaway mirror in a mktemp dir: tools/visual/{the three files, block-font.json}, baseline/ = every
# committed PNG + the 18 PNGs of 77fe41e8 (tools/titan/results/wave54-plan/label-chrome-all-reset.seeded-77fe41e8/),
# fixtures/ = the tracked fixtures (git ls-files), node_modules → the repo's (pngjs). Then `node --test` there.
#   bash tools/titan/results/wave54-label-chrome/sim-post-seed.sh [MUTATION_SED_EXPR_FOR_CHECK_MJS]
# The optional argument is a sed expression applied to the MIRROR's label-chrome-check.mjs only (mutation runs: the
# working tree is never edited). Prints the node:test summary and every exempt / RED / HINT line; exit = node's exit.
set -uo pipefail                                                     # fail on unset vars / pipe errors (not -e: we report)
ROOT="$(cd "$(dirname "$0")/../../../.." && pwd)"                    # repo root from this script's location
SIM="$(mktemp -d "${TMPDIR:-/tmp}/l7-sim-post-seed.XXXXXX")"         # throwaway mirror, removed on exit
trap 'rm -rf "$SIM"' EXIT                                             # never leave the mirror behind
mkdir -p "$SIM/tools/visual/baseline"                                # mirror layout: HERE = tools/visual, ROOT = $SIM
cp "$ROOT"/tools/visual/{label-chrome-tripwire.test.mjs,label-chrome-check.mjs,label-chrome-exempt.json,block-font.json} "$SIM/tools/visual/" # the U1 files + atlas
cp "$ROOT"/tools/visual/baseline/*.png "$SIM/tools/visual/baseline/" # every committed baseline
cp "$ROOT"/tools/titan/results/wave54-plan/label-chrome-all-reset.seeded-77fe41e8/*.png "$SIM/tools/visual/baseline/" # + the 18 seed PNGs
(cd "$ROOT" && git ls-files -z fixtures | xargs -0 -I{} sh -c 'mkdir -p "$1/$(dirname "$2")" && cp "$2" "$1/$2"' _ "$SIM" {}) # tracked fixtures (hint + verification)
ln -s "$ROOT/node_modules" "$SIM/node_modules"                        # pngjs resolves from the mirror root
if [[ -n "${1:-}" ]]; then sed -i '' -e "$1" "$SIM/tools/visual/label-chrome-check.mjs"; echo "MUTATION (mirror only): $1"; fi # optional mutation
echo "mirror PNGs: $(ls "$SIM/tools/visual/baseline" | wc -l | tr -d ' ')"   # expect 390 + 18 = 408
node --test "$SIM/tools/visual/label-chrome-tripwire.test.mjs" > "$SIM/run.txt" 2>&1; rc=$?   # the real suite, mirrored
grep -E '^# (tests|pass|fail) ' "$SIM/run.txt"                       # the summary
grep -E 'exempt-entry|_ATC_|_reset |_span |RED|HINT|^not ok' "$SIM/run.txt" | sed -e "s|$SIM|<mirror>|g" | grep -v '^# Subtest' # the lines that matter
echo "exit=$rc"                                                      # node's exit code
exit $rc                                                             # propagate
