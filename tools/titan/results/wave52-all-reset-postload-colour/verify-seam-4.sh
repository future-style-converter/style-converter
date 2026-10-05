#!/usr/bin/env bash
# verify-seam-4.sh — PLAN §0 seam verification for seam-4.patch (web-harness
# ComponentRenderer.tsx, skeptic-M1 fix pass), under the per-file lock:
#   1. lock (mkdir; poll 30 s up to 20 min), sha256 the seam (must equal HEAD);
#   2. git apply seam-4.patch (source hunk + the NEW pin file);
#   3. focused vitest: the new pin + the calibrateStyles neighbours
#      (RendererParity golden, maxSizeFloor, swarm003, ComponentRenderer);
#   4. the real-harness oracle probe (fixture-oracle-probe.mjs --mode harness);
#   5. MUTATION: seam source back to HEAD, pin file kept → the new pin must FAIL;
#   6. remove the pin file, restore the seam from HEAD, re-sha (IDENTICAL), unlock.
# Appends its record to seam-verification.log; full outputs in seam-4-*.out.
set -u
ROOT=$(cd "$(dirname "$0")/../../../.." && pwd)
HERE=$ROOT/tools/titan/results/wave52-all-reset-postload-colour
SEAM=apps/web-harness/src/sdui/ComponentRenderer.tsx
PIN=apps/web-harness/tests/sdui/ComponentRenderer.allHarnessDefaults.test.tsx
LOCK=$ROOT/tools/titan/runs/wave52-lock/$(basename "$SEAM")
LOG=$HERE/seam-verification.log
GOT=0                                                     # 1 once mkdir succeeds
for i in $(seq 1 40); do if mkdir "$LOCK" 2>/dev/null; then GOT=1; break; fi; sleep 30; done
[ $GOT = 1 ] || { echo "seam verification blocked by lock $(basename "$SEAM")" | tee -a "$LOG"; exit 3; }
cd "$ROOT"
SHA0=$(shasum -a 256 "$SEAM" | cut -d' ' -f1)
# Seam files are never left edited: refuse to start from a non-HEAD state.
[ "$SHA0" = "$(git show HEAD:"$SEAM" | shasum -a 256 | cut -d' ' -f1)" ] || { rmdir "$LOCK"; echo "seam not at HEAD — abort" | tee -a "$LOG"; exit 5; }
[ -e "$PIN" ] && { rmdir "$LOCK"; echo "pin file already present — abort" | tee -a "$LOG"; exit 6; }
git apply "$HERE/seam-4.patch" || { rmdir "$LOCK"; echo "apply FAILED seam-4.patch" | tee -a "$LOG"; exit 4; }
# 3. focused vitest (filtered to five files — never the whole suite).
(cd apps/web-harness && npx vitest run tests/sdui/ComponentRenderer.allHarnessDefaults.test.tsx \
  tests/sdui/RendererParity.test.tsx tests/sdui/ComponentRenderer.maxSizeFloor.test.tsx \
  tests/sdui/ComponentRenderer.swarm003.test.tsx tests/sdui/ComponentRenderer.test.tsx) > "$HERE/seam-4-vitest.out" 2>&1
RC_T=$?
# 4. the real harness with the patch applied (vite serves the patched source).
node "$HERE/fixture-oracle-probe.mjs" --mode harness > "$HERE/seam-4-probe.out" 2>&1
RC_P=$?
cp "$HERE/fixture-oracle-probe.json" "$HERE/fixture-oracle-probe.harness-patched.json"
# 5. mutation: HEAD source, pin kept → the pin must go red.
git show HEAD:"$SEAM" > "$SEAM"
(cd apps/web-harness && npx vitest run tests/sdui/ComponentRenderer.allHarnessDefaults.test.tsx) > "$HERE/seam-4-mutation.out" 2>&1
RC_M=$?
# 6. restore: drop the pin file, seam already at HEAD; verify and unlock.
rm -f "$PIN"
git show HEAD:"$SEAM" > "$SEAM"
SHA1=$(shasum -a 256 "$SEAM" | cut -d' ' -f1)
rmdir "$LOCK"
strip() { sed 's/\x1b\[[0-9;]*m//g' "$1" | grep -E 'Test Files|Tests +[0-9]|FAIL|×|PROBE' | head -12; }
{ echo "== $(date -u +%FT%TZ) seam-4.patch on $SEAM: vitest exit $RC_T, harness probe exit $RC_P, mutation (HEAD source + pin) vitest exit $RC_M (must be non-zero); sha256 before=$SHA0 after=$SHA1 $( [ "$SHA0" = "$SHA1" ] && echo IDENTICAL || echo MISMATCH ); pin file removed: $( [ -e "$PIN" ] && echo NO || echo yes )"
  echo "-- patched vitest"; strip "$HERE/seam-4-vitest.out"
  echo "-- patched harness probe"; strip "$HERE/seam-4-probe.out"
  echo "-- mutation vitest"; strip "$HERE/seam-4-mutation.out"; } >> "$LOG"
[ $RC_T = 0 ] && [ $RC_P = 0 ] && [ $RC_M != 0 ] && [ "$SHA0" = "$SHA1" ]
