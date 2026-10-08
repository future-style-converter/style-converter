#!/usr/bin/env bash
# tools/titan/results/wave53-canvas-root/skeptic-web-census.sh [RUN=wave52-ship]
# Writes HEAD's ComposedCaptureGallery.tsx beside skeptic-web-census.test.tsx
# (relative imports re-pointed at the tree), runs the census under the
# web-harness vite config (its @style-converter aliases), prints the output,
# removes the copy. Read-only on the shared tree.
set -euo pipefail
HERE="$(cd "$(dirname "$0")" && pwd)"; ROOT="$(cd "$HERE/../../../.." && pwd)"
export CENSUS_RUN="${1:-wave52-ship}"
git -C "$ROOT" show "HEAD:apps/web-harness/src/ui/ComposedCaptureGallery.tsx" \
  | sed -e "s#'\.\./sdui/#'$ROOT/apps/web-harness/src/sdui/#g" -e "s#'\./#'$ROOT/apps/web-harness/src/ui/#g" > "$HERE/.sk-base-gallery.tsx"
trap 'rm -f "$HERE/.sk-base-gallery.tsx"' EXIT
(cd "$ROOT/apps/web-harness" && npx vitest run --dir "$HERE" skeptic-web-census 2>&1 | tail -6)
cat "$HERE/skeptic-web-census.$CENSUS_RUN.txt"
