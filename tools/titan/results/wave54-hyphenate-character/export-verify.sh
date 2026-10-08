#!/usr/bin/env bash
# tools/titan/results/wave54-hyphenate-character/export-verify.sh — wave 54 lane L3: verify U2-android + seam-1 in an
# ISOLATED export of the Gradle project (apps/android-harness + runtimes/compose), so no other lane's concurrent Gradle
# can race the build dir (the shared tree's runtimes/compose/build was observed mid-rewrite at 21:01: 1546 classes, no
# PreBreakPipeline.class). Nothing in the shared tree is touched; the seam file is patched in the EXPORT only.
#   alone  = HEAD (git archive) + L3's owned Compose files from the shared tree + seam-1   → "L3's unit, by itself"
#   union  = the shared tree's current apps/android-harness + runtimes/compose (every lane's unseamed edits) + seam-1
# Then: :runtime:testDebugUnitTest --rerun on the given filters, the JUnit XML of THIS run, and a bytecode check that
# the compiled renderer calls HyphenateCharacterApplier.preBreakString (the seam really compiled in).
# Usage: export-verify.sh alone|union <export-dir> <--tests filter>…
set -uo pipefail
R="$(cd "$(dirname "$0")/../../../.." && pwd)"; H="$R/tools/titan/results/wave54-hyphenate-character"
mode="$1"; E="$2"; shift 2
rm -rf "$E"; mkdir -p "$E"
if [ "$mode" = alone ]; then
  # HEAD's bytes for both Gradle dirs, then L3's own Compose files over them.
  git -C "$R" archive HEAD apps/android-harness runtimes/compose | tar -x -C "$E"
  for p in $(grep '^runtimes/compose/' "$H/owned-paths.txt"); do mkdir -p "$E/$(dirname "$p")"; cp "$R/$p" "$E/$p"; done
else
  # The shared tree as it stands (build outputs excluded).
  for d in apps/android-harness runtimes/compose; do mkdir -p "$E/$d"; rsync -a --exclude build --exclude .gradle --exclude .kotlin "$R/$d/" "$E/$d/"; done
fi
# :runtime:testDebugUnitTest declares the repo's schema/ and fixtures/ as inputs (the conformance tests read them):
# HEAD's tracked bytes (no lane edits either: `git status` is clean under both).
git -C "$R" archive HEAD schema fixtures | tar -x -C "$E"
# The gitignored SDK pointer (memory: android-harness-local-properties).
cp "$R/apps/android-harness/local.properties" "$E/apps/android-harness/local.properties"
# The seam patch, applied in the export only (it must apply clean there too).
(cd "$E" && git init -q . && git apply --check "$H/seam-1.patch" && git apply "$H/seam-1.patch") || { echo "seam-1 does not apply in the $mode export"; exit 6; }
echo "export $mode: seam ComponentRenderer.kt sha256 $(shasum -a 256 "$E/runtimes/compose/src/main/java/com/styleconverter/runtime/core/renderer/ComponentRenderer.kt" | cut -c1-16)"
export JAVA_HOME="$(/usr/libexec/java_home -v 21)"
args=(); for f in "$@"; do args+=(--tests "$f"); done
out="$(cd "$E/apps/android-harness" && ./gradlew :runtime:testDebugUnitTest --rerun "${args[@]}" --console=plain 2>&1)"; rc=$?
echo "$out" | grep -E '^> Task :runtime:(compileDebugKotlin|compileDebugUnitTestKotlin|testDebugUnitTest)|^e: |FAILED$|tests completed|BUILD (SUCCESSFUL|FAILED)'
for f in "$E"/runtimes/compose/build/test-results/testDebugUnitTest/*.xml; do [ -f "$f" ] && sed -n 's/.*<testsuite name="[^"]*\.\([^".]*\)" tests="\([0-9]*\)" skipped="\([0-9]*\)" failures="\([0-9]*\)" errors="\([0-9]*\)".*/  junit \1: tests=\2 skipped=\3 failures=\4 errors=\5/p' "$f"; done
K="$E/runtimes/compose/build/intermediates/built_in_kotlinc/debug/compileDebugKotlin/classes/com/styleconverter/runtime/core/renderer"
n=$(grep -l 'HyphenateCharacterApplier' "$K"/*.class 2>/dev/null | wc -l | tr -d ' ')
echo "bytecode: $n compiled renderer class(es) reference HyphenateCharacterApplier ($(grep -l 'preBreakString' "$K"/*.class 2>/dev/null | xargs -n1 basename 2>/dev/null | tr '\n' ' '))"
[ "$rc" -eq 0 ] && [ "$n" -gt 0 ] || exit 1
