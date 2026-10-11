#!/usr/bin/env bash
# tools/titan/results/wave55-gate/ab-arms55.against-proof.sh <throwaway worktree> — proves ab-arms55.sh's --against tree check
# (plan-skeptic round 1, process-holes S5) can fail, with the runner's DRY RUN only (it writes nothing: git reads use
# --no-optional-locks, the revert check is `git apply -R --check`). Runs in a THROWAWAY worktree (`git worktree add --detach`),
# whose build-hashes.txt it edits (a tracked file of the throwaway — the "tracked changes" and "detached HEAD" refusals are
# therefore listed too; this proof reads only the --against lines). The shared tree is never touched.
#   P1 --against wave55-open: its last block names a tree with no code difference to HEAD → the "against:" OK note, no refusal;
#   P2 a block naming an older tree (7cce3b22, wave 53) → REFUSED "differs from it in code";
#   P3 a block saying "+ uncommitted" → REFUSED "ran with uncommitted code";
#   P4 a run directory that exists with no block (wave54-pre) → REFUSED "no '== wave54-pre …' block";
#   P5 MUTATION: a copy of the runner without the check → P2's tree passes silently (the check is load-bearing).
# Plan-skeptic round 2 (instrument-teeth S4): the runner reads the run's CORPUS-LAUNCH block(s), never the last block, and refuses
# a MISMATCH / MISSING line for a platform an arm reads. The synthetic blocks are labelled `== <run> corpus launch …` (the label
# expectations.json hashRead writes), and:
#   AB2 a corpus-launch block naming HEAD whose ios line reads MISMATCH (built digest MISSING) → REFUSED "MISMATCH";
#   AB3 a corpus-launch block on wave 53's tree FOLLOWED by a net-launch block naming HEAD → REFUSED (the first corpus block is
#       read: "differs from it in code"), where the round-1 runner read the net block (`tail -1`) and accepted;
#   AB4 two corpus-launch blocks naming different trees → REFUSED;
#   P1 now reads wave55-open's CORPUS block (tree 0d0d3a3d, MATCH), not its net block (a2a37a8e, ios MISMATCH: the race).
set -uo pipefail
WT="$(cd "${1:?usage: ab-arms55.against-proof.sh <throwaway worktree>}" && pwd -P)"
SH="$(cd "$(dirname "$0")/../../../.." && pwd -P)"
[[ "$WT" != "$SH" ]] || { echo "refusing: the throwaway is the shared tree"; exit 2; }
G=tools/titan/results/wave55-gate; FAILS=0
cp "$SH/$G/ab-arms55.sh" "$WT/$G/ab-arms55.sh"
ARMS="$(mktemp -d)/arms.txt"; echo "drop-X css-text ios $(git -C "$WT" rev-parse --short HEAD~1)" > "$ARMS"
echo "# ab-arms55.against-proof — $(date -u +%Y-%m-%dT%H:%M:%SZ); runner sha256 $(shasum -a 256 "$SH/$G/ab-arms55.sh" | cut -c1-16)…; throwaway at $(git -C "$WT" rev-parse --short HEAD) (detached)"
probe() {  # <label> <against> <want text> <want present 1|0>
  local o; o="$(AB_ROOT="$WT" bash "$WT/$G/ab-arms55.sh" "$ARMS" --dry-run --against "$2" 2>&1)"
  if [[ "$4" == 1 ]] && grep -qF -- "$3" <<<"$o"; then echo "  as required  $1 — $(grep -F -- "$3" <<<"$o" | head -1 | sed 's/^ *//' | cut -c1-190)"
  elif [[ "$4" == 0 ]] && ! grep -qF -- "$3" <<<"$o"; then echo "  as required  $1 — no line with \"$3\"; against: $(grep -E 'against' <<<"$o" | head -1 | sed 's/^ *//' | cut -c1-150)"
  else echo "  !! NOT as required  $1 (wanted \"$3\" $( [[ $4 == 1 ]] && echo present || echo absent))"; FAILS=$((FAILS+1)); fi; }
probe "P1 --against wave55-open (its block's tree has no code difference to HEAD)" wave55-open "no code difference to HEAD" 1
probe "P1b …and no --against refusal" wave55-open "--against wave55-open" 0
printf '== wave55-pre corpus launch (SYNTHETIC proof block) — 2026-10-11T00:00:00Z — HEAD 7cce3b22\nandroid  MATCH\nios      MATCH\n\n' >> "$WT/$G/build-hashes.txt"
probe "P2 a block naming wave 53's tree (7cce3b22): refused" wave55-pre "ran tree 7cce3b22, but HEAD differs from it in code" 1
printf '== wave55-probe corpus launch (SYNTHETIC proof block) — 2026-10-11T00:00:00Z — HEAD %s + uncommitted code changes (tracked or untracked)\n\n' "$(git -C "$WT" rev-parse --short HEAD)" >> "$WT/$G/build-hashes.txt"
probe "P3 a block saying + uncommitted: refused" wave55-probe "ran with uncommitted code" 1
probe "P4 a run directory with no block (wave54-pre): refused" wave54-pre "no '== wave54-pre corpus launch …' block" 1
H="$(git -C "$WT" rev-parse --short HEAD)"
printf '== wave55-ab2 corpus launch (SYNTHETIC proof block AB2) — 2026-10-11T00:00:00Z — HEAD %s\nandroid  MATCH\nios      udid=X  built .app digest=MISSING  installed .app digest=38ea8fdc\nios      MISMATCH\n\n' "$H" >> "$WT/$G/build-hashes.txt"
probe "AB2 a corpus-launch block naming HEAD with 'ios MISMATCH' (built digest MISSING): refused" wave55-ab2 "the installed build was not that tree's build" 1
printf '== wave55-ab3 corpus launch (SYNTHETIC proof block AB3) — 2026-10-11T00:00:00Z — HEAD 7cce3b22\nandroid  MATCH\nios      MATCH\n\n== wave55-ab3 net launch (SYNTHETIC: the race) — 2026-10-11T01:00:00Z — HEAD %s\nandroid  MATCH\nios      MATCH\n\n' "$H" >> "$WT/$G/build-hashes.txt"
probe "AB3 a corpus-launch block on wave 53's tree, then a net-launch block naming HEAD: the corpus block is read — refused" wave55-ab3 "ran tree 7cce3b22, but HEAD differs from it in code" 1
printf '== wave55-ab4 corpus launch (SYNTHETIC AB4, launch 1) — 2026-10-11T00:00:00Z — HEAD %s\nios      MATCH\n\n== wave55-ab4 corpus launch (SYNTHETIC AB4, launch 2) — 2026-10-11T01:00:00Z — HEAD 7cce3b22\nios      MATCH\n\n' "$H" >> "$WT/$G/build-hashes.txt"
probe "AB4 two corpus-launch blocks naming different trees: refused" wave55-ab4 "its corpus-launch blocks name different trees" 1
python3 -I - "$WT/$G/ab-arms55.sh" <<'EOF'
import sys
p = sys.argv[1]; s = open(p).read(); a = 'if [[ -n "$AGAINST_HEAD" ]]; then'
assert s.count(a) == 1; open(p, 'w').write(s.replace(a, 'if false; then   # MUTATION: the --against tree check removed', 1))
EOF
probe "P5 MUTATION (the check removed): wave 53's tree passes silently" wave55-pre "differs from it in code" 0
git -C "$WT" --no-optional-locks show "HEAD:$G/build-hashes.txt" > "$WT/$G/build-hashes.txt"; rm -f "$WT/$G/ab-arms55.sh"   # restored by a read + a write (no checkout)
echo "ab-arms55.against-proof: $([ $FAILS -eq 0 ] && echo 'every probe behaved as required' || echo "$FAILS probe(s) did NOT behave as required")"
exit $FAILS
