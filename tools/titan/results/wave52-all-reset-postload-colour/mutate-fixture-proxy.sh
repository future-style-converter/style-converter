#!/usr/bin/env bash
# mutate-fixture-proxy.sh — lane L11 fix pass 2 (skeptic RV-M1): executed-mutation
# proof for the textless fixtures/combinations/all-then-color.json, IN PLACE on
# the fixture (the exact `--input` path the closing gate passes), each mutation
# judged by pair-gate-proxy.mjs (the REAL compare-screenshots.mjs, gate-only)
# with its REQUIRED exit code, then a byte-exact restore with sha256 compared.
# Appends to mutations.log. Nothing outside the fixture and this dir is written
# (captures + reports go under PROXY_SCRATCH).
#   P-M1  the HEAD b35e203a fixture (span `_text` restored)      → exit 4 (face split reddens the pair gate)
#   P-M2  span `display` moved BEFORE `all` (§6.4 order flipped) → exit 6 (child inline + empty → blue fill)
#   P-M3  span declarations emptied (pre-wave-52 order-blind)    → exit 6 (child paints nothing → blue fill)
#   P-M4  PropsThenAll child `all: revert` → `all: initial`, judged with --model native
#         (the natives' S2 placeholder floor vs the web's inline collapse) → exit 4 (the
#         standalone `reset` frame splits 390x32 web vs 390x62 natives — why the row uses revert)
set -u                                                     # unset vars are bugs; a failed mutation must not abort the restore
HERE=$(cd "$(dirname "$0")" && pwd)                        # this results dir
ROOT=$(cd "$HERE/../../../.." && pwd)                      # repo root
F=$ROOT/fixtures/combinations/all-then-color.json          # the fixture under mutation
LOG=$HERE/mutations.log                                    # the lane's mutation record
: "${PROXY_SCRATCH:?set PROXY_SCRATCH to a scratch dir}"   # captures never land in the tree
SAVE=$PROXY_SCRATCH/all-then-color.saved.json              # byte-exact backup
mkdir -p "$PROXY_SCRATCH" && cp "$F" "$SAVE"               # back up before the first write
SHA0=$(shasum -a 256 "$F" | cut -d' ' -f1)                 # the bytes that must come back
restore() { cp "$SAVE" "$F"; }                             # one restore path for every exit
trap restore EXIT                                          # even a crashed mutation restores
mutate() {   # $1 id, $2 required exit, $3 node one-liner that rewrites $F (d = parsed fixture), $4 proxy model (default css)
  node -e "const fs=require('fs');const d=JSON.parse(fs.readFileSync(process.argv[1],'utf8'));$3;fs.writeFileSync(process.argv[1],JSON.stringify(d,null,2)+'\n')" "$F"   # rewrite in place
  local out; out=$(node "$HERE/pair-gate-proxy.mjs" --tag "$1" --model "${4:-css}" --expect-exit "$2" 2>&1)   # judge it
  local rc=$?                                              # 0 iff the comparator exited with the REQUIRED code
  { echo "== fixture $1 (required exit $2): $( [ $rc -eq 0 ] && echo CAUGHT || echo NOT-CAUGHT )"; echo "$out" | grep -E '^\[|  \| ' ; } >> "$LOG"   # record verdict + the gate's own lines
  restore                                                  # back to the saved bytes before the next one
}
echo "# FIX PASS 2 (skeptic RV-M1) $(date -u +%FT%TZ) — textless-fixture mutations via pair-gate-proxy.mjs; sha256(before)=$SHA0" >> "$LOG"
# P-M1: the committed HEAD bytes (with the 'Hamburg 123' text) — written verbatim, not via JSON.
git -C "$ROOT" show b35e203a:fixtures/combinations/all-then-color.json > "$F"   # git show reads only; the tree file is the target
{ out=$(node "$HERE/pair-gate-proxy.mjs" --tag P-M1-head-text --expect-exit 4 2>&1); rc=$?
  echo "== fixture P-M1-head-text (required exit 4): $( [ $rc -eq 0 ] && echo CAUGHT || echo NOT-CAUGHT )"; echo "$out" | grep -E '^\[|  \| '; } >> "$LOG"   # record
restore                                                    # back before P-M2
mutate P-M2-display-before-all 6 "const s=d.components.ATC_InitialUnderRedParent.children.span;const {display,...rest}=s.properties;s.properties={display,...rest}"   # display first, all second
mutate P-M3-order-blind-drop 6 "d.components.ATC_InitialUnderRedParent.children.span.properties={}"   # the old natives' drop-everything
mutate P-M4-reset-initial-native 4 "d.components.ATC_PropsThenAll_InGreenParent.children.reset.properties.all='initial'" native   # S2 frame split
SHA1=$(shasum -a 256 "$F" | cut -d' ' -f1)                 # after the last restore
echo "# restored sha256(after)=$SHA1 $( [ "$SHA0" = "$SHA1" ] && echo IDENTICAL || echo MISMATCH )" >> "$LOG"   # the byte-exact proof
