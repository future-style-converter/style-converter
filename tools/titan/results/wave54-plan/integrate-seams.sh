#!/usr/bin/env bash
# integrate-seams.sh — wave 54: apply the lanes' seam patches in the landing order of PLAN.md §3/§4. One patch at a
# time: `git apply --check`, then `git apply`; the first failure stops the run with the patch named, so no patch lands
# OUT OF ORDER — but the patches before it STAY APPLIED in a real run (fix r2, plan-skeptic N6; --dry-run discards its
# worktree): restore the four seam files (`git checkout -- <the SEAMS below>`) before re-running. Run from the repo root
# on a tree whose four seam files are clean
# (`git status --short -- <seams>` empty). The wave-53 script (tools/titan/results/wave53-plan/integrate-seams.sh)
# with the wave-54 registry; land-units.sh (written at integration from the lane notes, as in wave 53) applies the
# same patches one unit at a time right before each unit's commit — this script is the "do they all go in together,
# in this order" check the S1 skeptic and the orchestrator run first.
#
#   integrate-seams.sh            apply everything
#   integrate-seams.sh --dry-run  apply in order inside a throwaway worktree only (no tree change)
#
# The order, by seam file (PLAN §3 / §4 — every patch is cut against 7cce3b22 and applies with offsets):
#   ComponentRenderer.kt     L2 seam-1 (:2653 TableApplier.Table chrome) → L5 seam-1 (:1141 UAElementFontRule wrap)
#                            → L4 seam-1 (:1934 childContainingBlock wptCaptureMode) → L3 seam-1 (:7072 hyphenChar)
#   ComponentRenderer.swift  L5 seam-2 (:343-356 hasElementChildren → UAHeadingFoldGate) → L3 seam-2 (:5011 / :5026)
#   ComponentRenderer.tsx    L6 seam-1 (WWS lift → exported wsAfterSeparator) → L6 seam-2 (renderText :991; branch H ONLY)
#   extract-fixture.mjs      L3 seam-3 (buildNode children-loop re-arm, U3) → L3 seam-3b (line-start br line box, U3b)
# A lane that delivered no patch for a registered slot is skipped with a note (the registry is the plan's
# expectation, the lane dir is the fact; L6 seam-2 exists only if step 0 chose branch H).
set -uo pipefail
ROOT="$(cd "$(dirname "$0")/../../../.." && pwd)"; cd "$ROOT"
R=tools/titan/results
DRY=0; [[ "${1:-}" == "--dry-run" ]] && DRY=1
ORDER=(
  # ── landing order (PLAN §4): L1 has no seam; L2 → L6 → L5 → L4 → L3 ──
  "$R/wave54-table-body-cell/seam-1.patch"
  "$R/wave54-web-tail/seam-1.patch"
  "$R/wave54-web-tail/seam-2.patch"
  "$R/wave54-ua-heading-face/seam-1.patch"
  "$R/wave54-ua-heading-face/seam-2.patch"
  "$R/wave54-oof-layout/seam-1.patch"
  "$R/wave54-hyphenate-character/seam-1.patch"
  "$R/wave54-hyphenate-character/seam-2.patch"
  "$R/wave54-hyphenate-character/seam-3.patch"
  "$R/wave54-hyphenate-character/seam-3b.patch"
)
SEAMS=(runtimes/compose/src/main/java/com/styleconverter/runtime/core/renderer/ComponentRenderer.kt
       runtimes/swiftui/Sources/StyleConverterRuntime/Renderer/ComponentRenderer.swift
       apps/web-harness/src/sdui/ComponentRenderer.tsx
       tools/titan/extract-fixture.mjs)
for seam in "${SEAMS[@]}"; do
  git diff --quiet -- "$seam" || { echo "seam file already modified: $seam — refusing" >&2; exit 2; }
done
if (( DRY )); then
  TMP="$(mktemp -d)"
  git worktree add -q --detach "$TMP/wt" HEAD || { echo "worktree add failed"; exit 2; }
  # Carry the uncommitted tree state too (the lanes' non-seam edits), so the dry run sees what the real run will.
  git diff HEAD | (cd "$TMP/wt" && git apply --whitespace=nowarn 2>/dev/null || true)
  cd "$TMP/wt"
fi
n=0
ERR="${TMPDIR:-/tmp}/wave54-apply.$$.err"
for p in "${ORDER[@]}"; do
  src="$ROOT/$p"
  if [[ ! -f "$src" ]]; then echo "skip     ${p#$R/wave54-}  (no such patch — the lane delivered none)"; continue; fi
  if git apply --check --whitespace=nowarn "$src" 2>"$ERR"; then
    git apply --whitespace=nowarn "$src" && { n=$((n+1)); printf 'ok   %2d  %s\n' "$n" "${p#$R/wave54-}"; }
  else
    printf 'FAIL     %s  (re-cut it against the tree that holds the patches above it)\n' "${p#$R/wave54-}"; sed 's/^/         /' "$ERR" | head -6
    rm -f "$ERR"
    (( DRY )) && { cd "$ROOT"; git worktree remove --force "$TMP/wt"; }
    exit 1
  fi
done
rm -f "$ERR"
echo "applied $n patches"
(( DRY )) && { cd "$ROOT"; git worktree remove --force "$TMP/wt"; }
exit 0
