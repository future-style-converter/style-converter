#!/bin/bash
# Skeptic L6: build an export tree <name> from HEAD (git archive) + a variant.
set -euo pipefail
W=/Users/dranak/Documents/Projects/Style-Converter/.claude/worktrees/trusting-bohr-bd6fbf
L=$W/tools/titan/results/wave54-web-tail
SK=$(cd "$(dirname "$0")" && pwd)
name=$1; variant=$2
T=$SK/$name
rm -rf "$T"; mkdir -p "$T"
git -C $W archive HEAD apps/web-harness runtimes/web package.json tsconfig.json 2>/dev/null | tar -x -C "$T" || git -C $W archive HEAD apps/web-harness runtimes/web package.json | tar -x -C "$T"
mkdir -p "$T/node_modules/@style-converter"
for e in $W/node_modules/* $W/node_modules/.bin $W/node_modules/.package-lock.json; do
  b=$(basename "$e"); [ "$b" = "@style-converter" ] && continue; ln -s "$e" "$T/node_modules/$b"; done
ln -s "$T/runtimes/web" "$T/node_modules/@style-converter/web"
for d in tools fixtures schema; do ln -s $W/$d "$T/$d"; done
if [ $variant = rs ] || [ $variant = landed ] || [ $variant = lift ]; then
    cp $W/apps/web-harness/src/ui/ComposedRootSeparator.ts $T/apps/web-harness/src/ui/
    cp $W/apps/web-harness/tests/ui/ComposedRootSeparator.test.ts $T/apps/web-harness/tests/ui/
    (cd $T && git apply $L/seam-1.patch)
    if [ $variant = lift ]; then git -C $W show HEAD:apps/web-harness/src/ui/ComposedCaptureGallery.tsx > $T/apps/web-harness/src/ui/ComposedCaptureGallery.tsx; rm $T/apps/web-harness/tests/ui/ComposedRootSeparatorWire.test.tsx; fi
fi
if [ $variant = w1 ] || [ $variant = landed ]; then
  for f in runtimes/web/src/renderer/InertOutOfFlowWordJoin.ts runtimes/web/tests/renderer/InertOutOfFlowWordJoin.test.tsx runtimes/web/src/renderer/InlineRuns.ts runtimes/web/src/renderer/NodeRenderer.ts; do cp $W/$f $T/$f; done
fi
echo "built $name ($variant)"
