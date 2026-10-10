#!/usr/bin/env bash
# tools/titan/results/wave54-rtl-marker-bake/w2-converter-hop.sh — window [W2] of wave-54 L1 (PLAN §2 L1): the gate's own
# extract → combine → convert → split path (section-runner.sh :375 / :416 / :435 / :620) for ONE section's tests (or a
# subset), on the tree as it stands, into a private out dir; then w2-check.mjs compares every per-test IR document with
# <base>'s. Starts Chromium (post-load + bidi bake) and Gradle: ORCHESTRATOR WINDOW ONLY (device-idle host).
# Side effect, as in the gate: extract-fixture.mjs rewrites fixtures/wpt/<section>/<stem>.json for the tests it runs.
# Usage: w2-converter-hop.sh <section> [test.html ...]   (no tests = the section's whole tests.list from <base>)
#        BASE=wave54-open (default) OUT=tools/titan/runs/wave54-l1-w2-<section> (default)
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../../../.." && pwd)"; cd "$ROOT"
SECTION=$1; shift
BASE=${BASE:-wave54-open}
OUT=${OUT:-tools/titan/runs/wave54-l1-w2-$SECTION}
rm -rf "$OUT"; mkdir -p "$OUT"
if (( $# )); then printf '%s\n' "$@" > "$OUT/tests.list"; else cp "tools/titan/runs/$BASE/sections/$SECTION/tests.list" "$OUT/tests.list"; fi
export JAVA_HOME=${JAVA_HOME:-$(/usr/libexec/java_home -v 21)}
# 1. extraction with the gate flags (section-runner.sh:375)
POST_LOAD_EXTRACT=1 BIDI_BAKE=1 VT_BAKE=1 node tools/titan/extract-fixture.mjs $(cat "$OUT/tests.list") > "$OUT/extract.log" 2>&1
# 2. the combined fixture (section-runner.sh:416) — relative path, as the gate passes it to Gradle
node tools/titan/build-combined-fixture.mjs --out "$OUT/combined.json" --tests "$OUT/tests.list" > "$OUT/combine.log" 2>&1
# 3. convert (section-runner.sh:435)
./gradlew --no-daemon :converter:run --args="convert --from css --to ir -i $OUT/combined.json -o $ROOT/$OUT/out" --quiet > "$OUT/gradle-convert.log" 2>&1
# 4. split into per-test documents (section-runner.sh:620)
node tools/titan/split-combined-ir.mjs --in "$OUT/out/tmpOutput.json" --out "$OUT/per-test-ir" > "$OUT/split.log" 2>&1
grep -h 'bidi-bake\|counter-bake' "$OUT/extract.log" || true
grep -i 'warn' "$OUT/gradle-convert.log" || echo "converter: no warning lines"
node tools/titan/results/wave54-rtl-marker-bake/w2-check.mjs "$OUT" "$SECTION" "$BASE"
