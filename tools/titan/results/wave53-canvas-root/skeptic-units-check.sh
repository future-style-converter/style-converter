#!/usr/bin/env bash
# tools/titan/results/wave53-canvas-root/skeptic-units-check.sh — the L3 SKEPTIC's
# independent check of the lane's four revert-unit patches (not make-units.py):
#  1. in a throwaway repo seeded with HEAD's bytes of every path the patches
#     name, `git apply` A → B-web → B-ios → B-android in the §4 order; every
#     path must then be byte-identical (sha256) to the shared tree;
#  2. each unit applies ALONE where it must: A on HEAD; each B on the A state;
#  3. each B patch reverse-applies alone on the FINAL tree (`--check` only);
#  4. unit A names no item-B symbol.
# Read-only on the shared tree.
set -uo pipefail
HERE="$(cd "$(dirname "$0")" && pwd)"; ROOT="$(cd "$HERE/../../../.." && pwd)"
TMP="$(mktemp -d)"; trap 'rm -rf "$TMP"' EXIT
paths=$(cat "$HERE"/unit-*.patch | grep '^+++ b/' | sed 's#^+++ b/##' | sort -u)
seed() { rm -rf "$1"; mkdir -p "$1"; (cd "$1" && git init -q); for p in $paths; do
  if git -C "$ROOT" cat-file -e "HEAD:$p" 2>/dev/null; then mkdir -p "$1/$(dirname "$p")"; git -C "$ROOT" show "HEAD:$p" > "$1/$p"; fi; done; }
fail=0
seed "$TMP/all"
for u in A B-web B-ios B-android; do
  (cd "$TMP/all" && git apply "$HERE/unit-$u.patch") && echo "apply-in-order $u: ok" || { echo "apply-in-order $u: FAILED"; fail=1; }
done
for p in $paths; do
  a=$(shasum -a 256 "$TMP/all/$p" 2>/dev/null | cut -c1-16); b=$(shasum -a 256 "$ROOT/$p" | cut -c1-16)
  [ "$a" = "$b" ] && echo "  == $b $p" || { echo "  != replay $a tree $b $p"; fail=1; }
done
seed "$TMP/a"; (cd "$TMP/a" && git apply --check "$HERE/unit-A.patch") && echo "A alone on HEAD: ok" || { echo "A alone on HEAD: FAILED"; fail=1; }
(cd "$TMP/a" && git apply "$HERE/unit-A.patch")
for u in B-web B-ios B-android; do
  (cd "$TMP/a" && git apply --check "$HERE/unit-$u.patch") && echo "$u alone on the A state: ok" || { echo "$u alone on A: FAILED"; fail=1; }
  (cd "$ROOT" && git apply -R --check "$HERE/unit-$u.patch") && echo "$u reverse-applies alone on the final tree: ok" || { echo "$u reverse on tree: FAILED"; fail=1; }
done
grep -c "TableBodyForest\|CanvasTableBody\|canvasTableBody" "$HERE/unit-A.patch" | sed 's/^/item-B symbols in unit A: /'
echo "verdict: $([ $fail = 0 ] && echo PASS || echo FAIL)"
