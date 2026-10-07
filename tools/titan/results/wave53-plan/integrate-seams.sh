#!/usr/bin/env bash
# integrate-seams.sh — wave 53: apply the lanes' seam patches in the landing
# order of PLAN.md §3/§4. One patch at a time: `git apply --check`, then
# `git apply`; the first failure stops the run with the patch named, so nothing
# lands half-applied out of order. Run from the repo root on a tree whose four
# seam files are clean (`git status --short -- <seams>` empty).
#
#   integrate-seams.sh            apply everything
#   integrate-seams.sh --dry-run  apply in order inside a throwaway worktree only (no tree change)
#
# The order, by seam file (PLAN §3 — L2's hunks go in before L4's on both natives):
#   extract-fixture.mjs      L1 seam-1 (findImpliedClose + IMPLIED_CLOSE_SCOPE; both trigger sites)
#   ComponentRenderer.swift  L2 seam-1 (SoftHyphenPolicy.admitsPreBreak guard) → L4 seam-2 (FloatAvoidLayout branch)
#   ComponentRenderer.kt     L2 seam-2 (runs-fold breadcrumb)                   → L4 seam-1 (FloatAvoidLayout branch)
#   ComponentRenderer.tsx    — (no lane needs it)
# A lane that delivered no patch for a registered slot is skipped with a note
# (the registry is the plan's expectation, the lane dir is the fact).
set -uo pipefail
ROOT="$(cd "$(dirname "$0")/../../../.." && pwd)"; cd "$ROOT"
R=tools/titan/results
DRY=0; [[ "${1:-}" == "--dry-run" ]] && DRY=1
ORDER=(
  # ── extractor (L1) ──
  "$R/wave53-lists-bakes/seam-1.patch"
  # ── iOS renderer: L2 before L4 ──
  "$R/wave53-soft-hyphen/seam-1.patch"
  "$R/wave53-float-avoid/seam-2.patch"
  # ── Compose renderer: L2 before L4 ──
  "$R/wave53-soft-hyphen/seam-2.patch"
  "$R/wave53-float-avoid/seam-1.patch"
)
if (( DRY )); then
  TMP="$(mktemp -d)"
  git worktree add -q --detach "$TMP/wt" HEAD || { echo "worktree add failed"; exit 2; }
  # Carry the uncommitted tree state too (the lanes' non-seam edits), so the dry run sees what the real run will.
  git diff HEAD | (cd "$TMP/wt" && git apply --whitespace=nowarn 2>/dev/null || true)
  cd "$TMP/wt"
fi
n=0
for p in "${ORDER[@]}"; do
  src="$ROOT/$p"
  if [[ ! -f "$src" ]]; then echo "skip     ${p#$R/wave53-}  (no such patch — the lane delivered none)"; continue; fi
  if git apply --check --whitespace=nowarn "$src" 2>"${TMPDIR:-/tmp}/wave53-apply.err"; then
    git apply --whitespace=nowarn "$src" && { n=$((n+1)); printf 'ok   %2d  %s\n' "$n" "${p#$R/wave53-}"; }
  else
    printf 'FAIL     %s\n' "${p#$R/wave53-}"; sed 's/^/         /' "${TMPDIR:-/tmp}/wave53-apply.err" | head -6
    (( DRY )) && { cd "$ROOT"; git worktree remove --force "$TMP/wt"; }
    exit 1
  fi
done
echo "applied $n patches"
(( DRY )) && { cd "$ROOT"; git worktree remove --force "$TMP/wt"; }
exit 0
