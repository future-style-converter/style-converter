#!/usr/bin/env bash
# Wave 55 lane L4 — cut ONE patch from the lane worktree against a base state, without touching the
# worktree's own index: an alternate GIT_INDEX_FILE is set to <base tree> + the base patches
# (`git apply --cached`), new files are intent-to-add there, and `git diff -- <paths>` (worktree vs
# that index) is the delta of exactly <paths> on top of the base. A seam patch gets the PLAN §3
# header (base tree, the seam file's sha256 at base, the content anchor) as leading `#` lines,
# which `git apply` skips.
#   cut-patch.sh <worktree> <base-tree> <out.patch> "<base patches…>" "<header text or ->" <paths…>
set -euo pipefail
WT=$1; BASE=$2; OUT=$3; BASEPATCHES=$4; HEADER=$5; shift 5
IDX=$(mktemp "${TMPDIR:-/tmp}/l4idx.XXXXXX")
# The base tree in the alternate index (the worktree's own index is never read or written).
GIT_INDEX_FILE=$IDX git -C "$WT" read-tree "$BASE"
# Earlier units / seams this patch is cut on top of, applied to that index only.
for p in $BASEPATCHES; do GIT_INDEX_FILE=$IDX git -C "$WT" apply --cached "$p"; done
# New files become intent-to-add in the alternate index, so the diff carries creation hunks.
for f in "$@"; do
  if ! GIT_INDEX_FILE=$IDX git -C "$WT" ls-files --error-unmatch -- "$f" >/dev/null 2>&1; then
    GIT_INDEX_FILE=$IDX git -C "$WT" add -N -- "$f"
  fi
done
# The header (seam patches only), then the diff of exactly these paths.
{ if [ "$HEADER" != "-" ]; then printf '%s\n' "$HEADER"; fi
  GIT_INDEX_FILE=$IDX git -C "$WT" diff --no-color --no-ext-diff -- "$@"; } > "$OUT"
rm -f "$IDX"
# Refuse an empty cut (a path list that names nothing changed).
grep -q '^diff --git' "$OUT" || { echo "cut-patch: EMPTY $OUT" >&2; exit 3; }
echo "cut $OUT ($(grep -c '^diff --git' "$OUT") file(s), sha256 $(shasum -a 256 "$OUT" | cut -c1-12)…)"
