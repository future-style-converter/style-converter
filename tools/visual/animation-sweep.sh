#!/usr/bin/env bash
# animation-sweep.sh — run a fixture at several animation times and check
# both that the runtimes AGREE and that they actually MOVE.
#
# ## The gap this closes
#
# Every piece of the deterministic animation-capture contract already
# existed and worked: `CAPTURE_ANIMATION_TIME` seizes the clock on all
# three platforms (schema/spec/07-animations.md §5), each platform verifies
# its own seize loudly (test-all.sh errors if iOS or Android silently ran
# live; capture-screenshots.mjs throws if the web page exposes no
# `__seizeAnimations` hook or the canvases carry no marker), and
# fixtures/fidelity/motion/* is purpose-built for it — every duration is 1s
# so one clock value is mid-run for the whole surface.
#
# What did not exist was anything that USED it. No automated run ever set
# the variable, so an animation divergence could not be caught by the
# pipeline, and a runtime that ignored animation entirely would never be
# noticed.
#
# ## Why "do they agree?" is not enough
#
# A static capture is the easiest thing in the world for three runtimes to
# agree on. If a runtime rendered the t=0 frame no matter what time was
# requested, every cross-platform pair would be perfect and every gate
# would pass — the correlated-failure mode, where all three are wrong the
# same way, in its purest form.
#
# So this script asks a second question the 3-way comparison structurally
# cannot: for each platform and component, did the pixels CHANGE across the
# sweep? A component that is byte-identical at every sampled time either
# has no animation, or has one nobody is running.
#
# ## Why a sweep and not two points
#
# Measured while building this. `MK_FillBoth` (`animation-delay: 0.5s`,
# `duration: 1s`, `fill-mode: both`, opacity 0→1) is byte-identical at
# t=0 and t=0.5 on ALL THREE platforms — and that is CORRECT: at t=0 it is
# backwards-filling at opacity 0, and at t=0.5 the animation is starting,
# also opacity 0. At t=1.0 it moves 22.22% on all three. A two-point check
# would have called a spec-correct render a dead animation. The motion
# question is only meaningful across a sweep wide enough to catch every
# component's active window.
#
# ## Usage
#
#   bash tools/visual/animation-sweep.sh [fixture.json] [t1,t2,...]
#
# Defaults: fixtures/fidelity/motion/keyframes-basic.json, "0,0.25,0.5,0.75,1"
#
# Each time point is a full test-all.sh (convert + build + boot + capture on
# three platforms, ~2-3 min), because test-all.sh has no capture-only mode.
# A 5-point sweep is therefore ~12 min. The cross-platform gate runs per
# time point, so a divergence at ANY sampled time fails the sweep.
#
# Exit 0 = every time point's gate passed AND every component moved
# somewhere. Exit 1 = a component never moved. Exit 4/5 = a gate failed
# (see cross-platform-gate.mjs).

set -euo pipefail

FIXTURE="${1:-fixtures/fidelity/motion/keyframes-basic.json}"
TIMES="${2:-0,0.25,0.5,0.75,1}"
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
WORK="${ANIMATION_SWEEP_WORK:-$(mktemp -d)}"
# mkdir -p, not just mktemp: the env override is a caller-supplied
# path that need not exist yet, and without this the first touch
# died under `set -e` with a bare "No such file or directory".
mkdir -p "$WORK"

cd "$ROOT"

echo "=== animation sweep ==="
echo "  fixture : $FIXTURE"
echo "  times   : $TIMES"
[[ -n "${CAPTURE_FORCE_STATE:-}" ]] && echo "  state   : ${CAPTURE_FORCE_STATE} (forced)"
echo "  work    : $WORK"
echo

IFS=',' read -r -a TIME_LIST <<< "$TIMES"
GATE_FAILURES=0

for t in "${TIME_LIST[@]}"; do
    echo "--- t=${t}s ---"
    # Stamped before the run so the snapshot below can tell captures this
    # run wrote from a previous run's leftovers — same reasoning, and the
    # same BSD-find-safe reference-file form, as noise-floor.sh.
    STAMP="$WORK/stamp-$t"; touch "$STAMP"
    touch -A -000002 "$STAMP" 2>/dev/null || touch -d '2 seconds ago' "$STAMP" 2>/dev/null || true

    # SIMCTL_CHILD_ prefix is REQUIRED for iOS: simctl only forwards
    # environment to the launched app for variables carrying that prefix,
    # and test-all.sh errors out if the marker file disagrees. Setting only
    # CAPTURE_ANIMATION_TIME would seize Android and web while iOS ran
    # live — a skew that would look exactly like an iOS animation bug.
    # CAPTURE_FORCE_STATE rides along when set. NOTE the iOS transport is
    # SIMCTL_CHILD_FORCE_STATE, not SIMCTL_CHILD_CAPTURE_FORCE_STATE: the
    # iOS harness reads the env `FORCE_STATE` (CaptureOverrides.swift),
    # which does not carry the CAPTURE_ prefix that animationTime's knob
    # does. Writing the symmetric-looking name is exactly the mistake that
    # left iOS running base-state while Android and web honoured the
    # request -- I made it here first, and test-all.sh now hard-fails on
    # it rather than letting the run proceed.
    # Transitions need a forced state: a transition only starts when the
    # property changes, so without a forced state `transitions.json`
    # renders its base frame at every time and the motion check below
    # correctly reports that nothing moved (verified — that is this
    # script's own negative control).
    RC=0
    CAPTURE_ANIMATION_TIME="$t" SIMCTL_CHILD_CAPTURE_ANIMATION_TIME="$t" \
    CAPTURE_FORCE_STATE="${CAPTURE_FORCE_STATE:-}" \
    SIMCTL_CHILD_FORCE_STATE="${CAPTURE_FORCE_STATE:-}" \
        ./test-all.sh "$FIXTURE" >"$WORK/run-$t.log" 2>&1 || RC=$?

    # Confirm the seize actually happened rather than trusting the exit
    # code: a run that silently captured live would otherwise be folded
    # into the sweep as if it were a real sample.
    for marker in "verified: iOS capture ran with animationTime=$t" \
                  "verified: Android capture ran with animationTime=$t" \
                  "animation clock seized at t=${t}s"; do
        grep -q "$marker" "$WORK/run-$t.log" || {
            echo "  ✗ no evidence of the seize: \"$marker\" absent from the log"
            echo "    see $WORK/run-$t.log"
            exit 2
        }
    done
    echo "  seize verified on all three platforms"

    if [[ "$RC" -ne 0 ]]; then
        echo "  ✗ gate failed at t=$t (exit $RC) — see $WORK/run-$t.log"
        grep -E "unexpected|stale expectation" "$WORK/run-$t.log" | head -5 | sed 's/^/    /' || true
        GATE_FAILURES=$((GATE_FAILURES + 1))
    else
        echo "  ✓ cross-platform gate clean"
    fi

    mkdir -p "$WORK/t-$t"
    for p in ios android web; do
        [[ -d "apps/$p-harness/screenshots" ]] || continue
        NEW=$(find "apps/$p-harness/screenshots" -name '*.png' -newer "$STAMP" 2>/dev/null | wc -l | tr -d ' ')
        [[ "$NEW" -gt 0 ]] || { echo "  $p: no captures written — excluded"; continue; }
        cp -R "apps/$p-harness/screenshots" "$WORK/t-$t/$p"
    done
done

# ── The motion check ─────────────────────────────────────────────────────
# The question the 3-way comparison cannot ask. For each platform and
# component: is it byte-identical at EVERY sampled time?
echo
echo "=== motion check — did anything actually animate? ==="
node -e '
const fs = require("fs"), path = require("path");
const work = process.argv[1], times = process.argv[2].split(",");
let dead = [], checked = 0;
for (const p of ["ios", "android", "web"]) {
  const dirs = times.map(t => path.join(work, `t-${t}`, p)).filter(d => fs.existsSync(d));
  if (dirs.length < 2) continue;
  for (const f of fs.readdirSync(dirs[0]).filter(f => f.endsWith(".png"))) {
    const bufs = dirs.map(d => { try { return fs.readFileSync(path.join(d, f)); } catch { return null; } })
                     .filter(Boolean);
    if (bufs.length < 2) continue;
    checked++;
    // Byte equality is the right test: the noise floor is zero (see
    // noise-floor.sh), so identical bytes across two times means the
    // renderer produced the same frame, not that a metric rounded away.
    if (bufs.every(b => b.equals(bufs[0]))) dead.push(`${p}/${f}`);
  }
}
console.log(`  ${checked} platform-component series checked across ${times.length} time point(s)`);
if (!dead.length) { console.log("  ✓ every component changed somewhere in the sweep"); process.exit(0); }
console.log(`  ✗ ${dead.length} never changed at any sampled time:`);
for (const d of dead) console.log(`      ${d}`);
console.log("  Either the animation is unimplemented on that platform, or the sweep");
console.log("  never sampled its active window (check delay + duration).");
process.exit(1);
' "$WORK" "$TIMES" || MOTION_FAILED=1

echo
if [[ "$GATE_FAILURES" -gt 0 ]]; then
    echo "✗ $GATE_FAILURES of ${#TIME_LIST[@]} time point(s) failed the cross-platform gate"
    exit 4
fi
if [[ -n "${MOTION_FAILED:-}" ]]; then
    echo "✗ some components never moved — see above"
    exit 1
fi
echo "✓ sweep clean: ${#TIME_LIST[@]} time point(s), gates passed, everything moved"
