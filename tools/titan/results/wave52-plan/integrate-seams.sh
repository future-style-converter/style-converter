#!/usr/bin/env bash
# integrate-seams.sh — wave 52: apply every lane's seam patch and cross-lane hunk
# in the landing order of PLAN.md §4 (as corrected in §9) and each lane note's
# stated base. One patch at a time: `git apply --check`, then `git apply`; the
# first failure stops the run with the patch named, so nothing lands half-applied
# out of order. Run from the repo root on a tree whose four seam files are clean.
#
#   integrate-seams.sh            apply everything
#   integrate-seams.sh --dry-run  apply in order inside a scratch index only (no tree change)
#
# The order, by seam file:
#   extract-fixture.mjs      L5 s1..s5 → L1 hunk-for-L5-1 → L11 s3 → L5 s6 (F-E, with L6 s3/s4)
#   ComponentRenderer.kt     L4 s1 → L3 s1 → L7 s1 (cut on L3) → L10 s2 → L11 s1 → L6 s3,s1 → L8 s1,s3 → L9 s1
#   ComponentRenderer.swift  L3 s2 → L7 s2 (cut on L3) → L10 s1 → L11 s2 → L6 s4,s2 → L8 s2,s4 → L9 s2
#   ComponentRenderer.tsx    L11 s4
#   non-seam hunks           L4 (SeamReachabilityTest) · L3 (VerticalMulticolPlan + 3) · L8→L4 (StyleApplier + test)
#                            · L11→L3 (ContentsUnboxingTests, after L11 s2) · L6→L12 (inject-wpt-block)
#                            · L12 ×2 (the two rev-literal test files, same commit as the CANVAS_REV bump)
set -uo pipefail
ROOT="$(cd "$(dirname "$0")/../../../.." && pwd)"; cd "$ROOT"
R=tools/titan/results
DRY=0; [[ "${1:-}" == "--dry-run" ]] && DRY=1
ORDER=(
  # ── extractor ──
  "$R/wave52-extractor-cascade/seam-1-F3-oracle-filter.patch"
  "$R/wave52-extractor-cascade/seam-2-F1-importance.patch"
  "$R/wave52-extractor-cascade/seam-3-FD-specificity.patch"
  "$R/wave52-extractor-cascade/seam-4-F2-splitter.patch"
  "$R/wave52-extractor-cascade/seam-5-FC-colormix.patch"
  "$R/wave52-web-tail-colour-vt/hunk-for-L5-1.patch"
  "$R/wave52-all-reset-postload-colour/seam-3.patch"
  # ── Compose renderer ──
  "$R/wave52-small-fixes/seam-1.patch"
  "$R/wave52-small-fixes/hunk-for-orchestrator-1.patch"
  "$R/wave52-failure-ink/seam-1.patch"
  "$R/wave52-static-position/seam-1.patch"
  "$R/wave52-flex-nowrap-gaps/seam-2.patch"
  "$R/wave52-all-reset-postload-colour/seam-1.patch"
  "$R/wave52-counters-and-lists/seam-3.patch"
  "$R/wave52-counters-and-lists/seam-1.patch"
  "$R/wave52-vertical-wedges/seam-1.patch"
  "$R/wave52-vertical-wedges/seam-3.patch"
  "$R/wave52-inline-run-wall/seam-1.patch"
  # ── iOS renderer ──
  "$R/wave52-failure-ink/seam-2.patch"
  "$R/wave52-failure-ink/hunk-for-orchestrator-1.patch"
  "$R/wave52-static-position/seam-2.patch"
  "$R/wave52-flex-nowrap-gaps/seam-1.patch"
  "$R/wave52-all-reset-postload-colour/seam-2.patch"
  "$R/wave52-all-reset-postload-colour/hunk-for-L3-1.patch"
  "$R/wave52-counters-and-lists/seam-4.patch"
  "$R/wave52-counters-and-lists/seam-2.patch"
  "$R/wave52-vertical-wedges/seam-2.patch"
  "$R/wave52-vertical-wedges/seam-4.patch"
  "$R/wave52-inline-run-wall/seam-2.patch"
  # ── web renderer ──
  "$R/wave52-all-reset-postload-colour/seam-4.patch"
  # ── extractor, step 5 (F-E lands with L6's seam-3/seam-4, which are above) ──
  "$R/wave52-extractor-cascade/seam-6-FE-li.patch"
  # ── remaining cross-lane hunks ──
  "$R/wave52-vertical-wedges/hunk-for-L4-1.patch"
  "$R/wave52-counters-and-lists/hunk-for-L12-1.patch"
  "$R/wave52-instrument-and-calibration/hunk-for-orchestrator-1.patch"
  "$R/wave52-instrument-and-calibration/hunk-for-orchestrator-2.patch"
)
if (( DRY )); then
  # Dry run on a throwaway copy of the files the patches touch (no tree change).
  TMP="$(mktemp -d)"; git ls-files -z | grep -zE '^(runtimes|apps|tools/titan|tools/visual|converter)/' | xargs -0 -I{} true
  git worktree add -q --detach "$TMP/wt" HEAD || { echo "worktree add failed"; exit 2; }
  # carry the uncommitted tree state too (the lanes' fix-pass edits), so the dry run sees what the real run will
  git diff HEAD | (cd "$TMP/wt" && git apply --whitespace=nowarn 2>/dev/null || true)
  cd "$TMP/wt"
fi
n=0
for p in "${ORDER[@]}"; do
  src="$ROOT/$p"
  [[ -f "$src" ]] || { echo "MISSING $p"; exit 3; }
  if git apply --check --whitespace=nowarn "$src" 2>/tmp/claude-501-apply.err; then
    git apply --whitespace=nowarn "$src" && { n=$((n+1)); printf 'ok   %2d  %s\n' "$n" "${p#$R/wave52-}"; }
  else
    printf 'FAIL     %s\n' "${p#$R/wave52-}"; sed 's/^/         /' /tmp/claude-501-apply.err | head -6
    (( DRY )) && { cd "$ROOT"; git worktree remove --force "$TMP/wt"; }
    exit 1
  fi
done
echo "applied $n/${#ORDER[@]} patches"
(( DRY )) && { cd "$ROOT"; git worktree remove --force "$TMP/wt"; }
exit 0
