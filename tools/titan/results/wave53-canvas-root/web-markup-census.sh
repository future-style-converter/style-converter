#!/usr/bin/env bash
# tools/titan/results/wave53-canvas-root/web-markup-census.sh [BASE=HEAD] [RUN=wave52-ship]
# Writes the BASE copy of ComposedCaptureGallery.tsx (relative imports re-pointed
# at the tree's apps/web-harness/src) beside the census test, runs it under the
# web-harness vite config, prints web-markup-census.out.txt, removes the copy.
set -euo pipefail
HERE="$(cd "$(dirname "$0")" && pwd)"; ROOT="$(cd "$HERE/../../../.." && pwd)"
BASE="${1:-HEAD}"; export CENSUS_RUN="${2:-wave52-ship}"
git -C "$ROOT" show "$BASE:apps/web-harness/src/ui/ComposedCaptureGallery.tsx" \
  | sed -e "s#'\.\./sdui/#'$ROOT/apps/web-harness/src/sdui/#g" > "$HERE/.census-base-gallery.tsx"
trap 'rm -f "$HERE/.census-base-gallery.tsx"' EXIT
(cd "$ROOT/apps/web-harness" && npx vitest run --dir "$HERE" web-markup-census >/dev/null 2>&1) || {
  (cd "$ROOT/apps/web-harness" && npx vitest run --dir "$HERE" web-markup-census 2>&1 | tail -30); exit 1; }
cat "$HERE/web-markup-census.out.txt"
