#!/usr/bin/env bash
# For each landed unit commit that touches a seam: apply its lane's patch (ALL files in the patch) to the commit's parent bytes
# in a scratch dir and compare every resulting file with the commit's bytes. Prints EQUAL / DIFF per file.
T=$1; S=$2; R=$T/tools/titan/results
check() { # sha patch
  local sha=$1 patch=$2 d=$S/replay-$1; rm -rf $d; mkdir -p $d
  local files=$(grep '^+++ b/' $patch | sed 's#^+++ b/##')
  for f in $files; do mkdir -p $d/$(dirname $f); git -C $T show $sha^:$f > $d/$f 2>/dev/null || rm -f $d/$f; done
  (cd $d && git init -q . && git apply --whitespace=nowarn $patch) || { echo "APPLY FAIL $sha $patch"; return; }
  for f in $files; do if cmp -s $d/$f <(git -C $T show $sha:$f); then echo "EQUAL  $sha  $f  <= ${patch#$R/}"; else echo "DIFF   $sha  $f  <= ${patch#$R/}"; fi; done
  # files the commit changes that the patch does not carry
  for f in $(git -C $T diff --name-only $sha^ $sha); do echo "$files" | grep -qx "$f" || echo "        $sha  also commits $f"; done
}
check e86d3dd2 $R/wave54-table-body-cell/seam-1.patch
check 110f4b4e $R/wave54-web-tail/seam-1.patch
check a5708b7e $R/wave54-ua-heading-face/seam-1.patch
check 5b333c8a $R/wave54-oof-layout/seam-1.patch
check b5b721fd $R/wave54-hyphenate-character/seam-1.patch
check 20b65b57 $R/wave54-hyphenate-character/seam-2.patch
check 1d8fe364 $R/wave54-hyphenate-character/seam-3.patch
check d64d4c6e $R/wave54-hyphenate-character/seam-3b.patch
