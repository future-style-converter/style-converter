#!/usr/bin/env bash
# Wave 55 lane L4 — the C12 / R8 dry-run in a `git archive` EXPORT TREE (never the shared tree, never
# the lane worktree): (1) HEAD's runtimes/ + the eight patches in landing order (§4 rows 13-16) —
# each `git apply --check`ed first — must equal the lane worktree byte-for-byte on every touched path;
# (2) every revertOrder chain reverse-applied on the fully landed series must give back the base
# byte-for-byte: U2-ios (seam 4 → unit) then U1-ios (seam 2 → unit), U2-android (seam 3 → unit) then
# U1-android (seam 1 → unit), in both cross-platform interleavings; (3) the WRONG intra-lane order
# (seam 1 reversed while seam 3 is on the tree; seam 2 while seam 4 is) is reported, so the note
# says whether revertOrder is load-bearing or only the review order.
#   export-check.sh <lane-worktree> <patch-dir> <scratch-dir>
set -uo pipefail
WT=$1; P=$2; X=$3
rm -rf "$X"; mkdir -p "$X/base"
git -C "$WT" archive 611c9f93 runtimes | tar -x -C "$X/base"
ORDER=(unit-U1-android seam-1 unit-U1-ios seam-2 unit-U2-android seam-3 unit-U2-ios seam-4)
FILES=$(cat "$P"/*.patch | grep '^diff --git' | awk '{print $4}' | sed 's#^b/##' | sort -u)
fresh() { rm -rf "$X/t"; cp -R "$X/base" "$X/t"; }
landall() { for p in "${ORDER[@]}"; do (cd "$X/t" && git apply --check "$P/$p.patch" && git apply "$P/$p.patch") || { echo "APPLY-FAIL $p"; return 1; }; done; }
rev() { for p in "$@"; do (cd "$X/t" && git apply -R --check "$P/$p.patch" && git apply -R "$P/$p.patch") || { echo "  REVERT-FAIL $p"; return 1; }; done; }
same_as() {  # $1 = reference root; every touched path identical (absent on both sides counts as identical)
  local bad=0
  for f in $FILES; do
    if [ -e "$1/$f" ] || [ -e "$X/t/$f" ]; then cmp -s "$1/$f" "$X/t/$f" || { echo "  DIFF $f"; bad=1; }; fi
  done; return $bad
}
echo "== (1) landing order on HEAD 611c9f93 → equals the lane worktree"
fresh; landall && echo "  applied: ${ORDER[*]}"
same_as "$WT" && echo "  IDENTICAL to the lane worktree on all $(echo "$FILES" | wc -l | tr -d ' ') touched paths"
echo "== (2) revert chains on the fully landed series → equal the base"
fresh; landall >/dev/null && rev seam-4 unit-U2-ios seam-2 unit-U1-ios seam-3 unit-U2-android seam-1 unit-U1-android \
  && same_as "$X/base" && echo "  ios first (U2-ios, U1-ios, U2-android, U1-android): BASE RESTORED byte-for-byte"
fresh; landall >/dev/null && rev seam-3 unit-U2-android seam-1 unit-U1-android seam-4 unit-U2-ios seam-2 unit-U1-ios \
  && same_as "$X/base" && echo "  android first (U2-android, U1-android, U2-ios, U1-ios): BASE RESTORED byte-for-byte"
fresh; landall >/dev/null && rev seam-3 unit-U2-android && echo "  U2-android alone reverts (chain [U2-android])"
fresh; landall >/dev/null && rev seam-4 unit-U2-ios && echo "  U2-ios alone reverts (chain [U2-ios])"
echo "== (3) the WRONG intra-lane order (expected to refuse: revertOrder is load-bearing)"
fresh; landall >/dev/null
(cd "$X/t" && git apply -R --check "$P/seam-1.patch" >/dev/null 2>&1) && echo "  seam-1 reverses with seam-3 on the tree (order is review-only)" \
  || echo "  seam-1 does NOT reverse while seam-3 is on the tree → revert seam 3 first (as revertOrder says)"
(cd "$X/t" && git apply -R --check "$P/seam-2.patch" >/dev/null 2>&1) && echo "  seam-2 reverses with seam-4 on the tree (order is review-only)" \
  || echo "  seam-2 does NOT reverse while seam-4 is on the tree → revert seam 4 first (as revertOrder says)"
(cd "$X/t" && git apply -R --check "$P/unit-U1-android.patch" >/dev/null 2>&1) && echo "  unit-U1-android reverses with U2-android on the tree" \
  || echo "  unit-U1-android does NOT reverse while U2-android is on the tree (UAElementFontRule.kt context) → U2 first"
echo "== (4) each U2 patch alone on bare HEAD (expected to refuse: U2 needs U1)"
for p in seam-3 seam-4; do fresh; (cd "$X/t" && git apply --check "$P/$p.patch" >/dev/null 2>&1) && echo "  $p applies on bare HEAD" || echo "  $p refuses on bare HEAD (needs its U1 seam)"; done
rm -rf "$X/t"
