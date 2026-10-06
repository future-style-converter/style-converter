#!/usr/bin/env bash
# tools/titan/results/wave52-gate/device-ab.sh <arm>
#
# The EXCLUDE arm of one wave-52 device A/B (ORCHESTRATOR-TODO.md §6). The
# INCLUDE arm is the gate of record `wave52-ship` (commit aaf676c5 — the
# integrated tree with the closing-gate fixes; its installed-build hashes are
# in build-hashes.txt). Each arm takes ONE mechanism out of that tree, runs
# the sections that hold its cells, records
# the installed base.apk sha1 / .app digest against the build (the standing
# constraint — a run without that hash is a score, not evidence), and puts the
# tree back. Nothing here is ever committed.
#
#   t5   L6 T5, outside-marker hang   reverse counters-and-lists seam-1 (Compose) + apply
#                                     t5-exclude-ios.integrated.patch (iOS: seam-2 reversed, recut — the
#                                     closing-gate li-display fix sits inside seam-2's context);
#                                     seam-3/4 stay (F-E coupling)
#   ma   L8 M-A, ch measuring face    reverse vertical-wedges seam-1 (Compose) + apply
#                                     ma-exclude-ios.integrated.patch (iOS)
#   f1   L9 F1, hanging-punctuation   apply inline-run-wall drop-F1.patch
#
# Read-out: node ab-diff.mjs wave52-ab-<arm> wave52-ship --platforms ios,android
# Exit: the gate driver's exit code (2 = host not quiet — defer, never force).
set -uo pipefail
ARM="${1:?usage: device-ab.sh t5|ma|f1}"
HERE="$(cd "$(dirname "$0")" && pwd)"
ROOT="$(cd "$HERE/../../../.." && pwd)"; cd "$ROOT"
RES=tools/titan/results
# Each entry: "<R|F> <patch>" — R = reverse-apply (take a landed seam out), F = forward-apply (an exclude patch).
case "$ARM" in
  t5) STEPS=("R $RES/wave52-counters-and-lists/seam-1.patch" "F $RES/wave52-counters-and-lists/t5-exclude-ios.integrated.patch"); SECTIONS=css-lists,css-counter-styles,css-pseudo ;;
  ma) STEPS=("R $RES/wave52-vertical-wedges/seam-1.patch" "F $RES/wave52-vertical-wedges/ma-exclude-ios.integrated.patch"); SECTIONS=css-text,css-text-decor,css-writing-modes ;;
  f1) STEPS=("F $RES/wave52-inline-run-wall/drop-F1.patch"); SECTIONS=css-text ;;
  *) echo "unknown arm: $ARM" >&2; exit 2 ;;
esac
RUN_ID="wave52-ab-$ARM"
# Start from the integrated tree exactly: an exclude arm on a dirty tree excludes nobody knows what.
# Documentation under those trees (README count restamps) does not reach a build and is not a change here.
if ! git diff --quiet -- runtimes apps converter ':(exclude)*.md'; then echo "runtimes/apps/converter have uncommitted code changes — refusing" >&2; exit 2; fi
# All patches must apply before any does (no half-applied arm).
for s in "${STEPS[@]}"; do
  read -r dir patch <<<"$s"
  if [[ "$dir" == R ]]; then git apply -R --check "$patch"; else git apply --check "$patch"; fi || { echo "patch does not apply: $s" >&2; exit 2; }
done
# Whatever happens next, the tree goes back: the patched files are restored from HEAD.
restore() { git diff --name-only -- runtimes apps converter ':(exclude)*.md' | while read -r f; do git show "HEAD:$f" > "$f"; done; git diff --quiet -- runtimes apps converter ':(exclude)*.md' && echo "[device-ab] tree restored" || echo "[device-ab] WARNING: tree NOT clean after restore" >&2; }
trap restore EXIT
for s in "${STEPS[@]}"; do
  read -r dir patch <<<"$s"
  if [[ "$dir" == R ]]; then git apply -R --whitespace=nowarn "$patch"; else git apply --whitespace=nowarn "$patch"; fi
done
echo "[device-ab] $ARM applied:"; git diff --stat -- runtimes apps converter | sed 's/^/   /'
export JAVA_HOME="${JAVA_HOME:-$(/usr/libexec/java_home -v 21)}"; unset WEB_PORT
DRV="tools/titan/runs/$RUN_ID/gate-driver/driver.log"
tools/titan/gate-driver.sh "$RUN_ID" --sections "$SECTIONS" --skip-fixture-net > "$HERE/device-ab-$ARM.out" 2>&1 &
GATE_PID=$!
# The hash must be taken while the devices are up: as soon as provisioning reports, before the first section ends.
for _ in $(seq 1 180); do
  grep -q 'provision rc=' "$DRV" 2>/dev/null && break
  kill -0 "$GATE_PID" 2>/dev/null || break
  sleep 5
done
if grep -q 'provision rc=0' "$DRV" 2>/dev/null; then bash "$HERE/installed-build-hash.sh" "$RUN_ID ($ARM exclude arm)" || echo "[device-ab] WARNING: installed build does not match the built one" >&2
else echo "[device-ab] provisioning did not report rc=0 — no hash recorded" >&2; fi
wait "$GATE_PID"; RC=$?
echo "[device-ab] $ARM gate-driver rc=$RC"; tail -6 "$HERE/device-ab-$ARM.out"
exit $RC
