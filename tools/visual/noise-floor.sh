#!/usr/bin/env bash
# noise-floor.sh — measure this harness's A/A noise floor.
#
# ## The question
#
# Every threshold in this repo (SSIM >= 0.95, dpx <= 2%) was chosen by
# eyeballing, and both gates that use them documented the same caveat: the
# harness's own run-to-run variance had never been measured, so nobody knew
# whether a pair sitting at 0.9499 was genuinely failing or just flapping.
# That caveat was the stated reason the cross-platform gate reported stale
# expectations as warnings instead of failing on them.
#
# An A/A study answers it directly: run the SAME code against the SAME
# fixture twice and compare the two runs. Anything that differs is pure
# harness noise -- GPU nondeterminism, font-atlas state, timing, dithering
# -- because nothing else changed.
#
# ## Method
#
# Run test-all.sh N times, snapshotting every platform's captures after
# each run, then compare run 1 against each later run.
#
# The comparison deliberately reuses the SHIPPING metric code rather than
# reimplementing SSIM here. compare-screenshots.mjs takes its three capture
# directories from env, so pointing IOS_SCREENSHOTS_DIR at run A's Android
# captures and ANDROID_SCREENSHOTS_DIR at run B's Android captures turns
# the "iOS-Android" pair into an Android A/A comparison, scored by exactly
# the code the real gate uses. A noise number measured with a different
# implementation would not be comparable to the thresholds it is meant to
# justify.
#
# Bytes are checked first because if the captures are byte-identical the
# metrics are trivially at their floor (SSIM 1.0, dpx 0%, dE 0) and the
# expensive comparison run is pointless.
#
# ## Usage
#
#   bash tools/visual/noise-floor.sh [fixture.json] [runs]
#
# Defaults: fixtures/visual-test.json, 2 runs. Each run is a full
# convert + build + boot + capture on all three platforms (~165s for
# visual-test), so N=2 is ~6min and N=3 ~9min.
#
# Exits non-zero if the captures are NOT byte-identical, printing the
# per-platform metric spread. That is not necessarily a bug -- it is the
# number you need before touching a threshold.

set -euo pipefail

FIXTURE="${1:-fixtures/visual-test.json}"
RUNS="${2:-2}"
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
WORK="${NOISE_FLOOR_WORK:-$(mktemp -d)}"
# mkdir -p, not just mktemp: the env override is a caller-supplied
# path that need not exist yet, and without this the first touch
# died under `set -e` with a bare "No such file or directory".
mkdir -p "$WORK"

cd "$ROOT"

echo "=== A/A noise floor ==="
echo "  fixture : $FIXTURE"
echo "  runs    : $RUNS"
echo "  work    : $WORK"
echo

# ── Capture N independent runs ───────────────────────────────────────────
# Each iteration is a full test-all.sh: fresh convert, fresh iOS build (it
# rm -rf's the xcodeproj + build dir), fresh emulator boot (-no-snapshot),
# fresh install. That is the level of independence a human re-running the
# suite gets, which is the variance the gates actually face.
for i in $(seq 1 "$RUNS"); do
    echo "--- run $i/$RUNS ---"
    # Stamped BEFORE the run so the mtime guard below can tell captures
    # this run wrote from captures a previous run left behind.
    #
    # A reference FILE, not `find -newermt @<epoch>`: BSD find (which is
    # what /usr/bin/find is on the macOS hosts this harness runs on)
    # cannot parse the @epoch form and exits 1, which under `set -e`
    # aborted the study with no message. `-newer <file>` is POSIX and
    # works on both BSD and GNU find. Backdated ~2s so a capture written
    # in the same second the stamp was taken still counts as newer.
    STAMP="$WORK/stamp-$i"
    touch "$STAMP"
    touch -A -000002 "$STAMP" 2>/dev/null || touch -d '2 seconds ago' "$STAMP" 2>/dev/null || true
    ./test-all.sh "$FIXTURE" >"$WORK/run-$i.log" 2>&1 || {
        echo "  test-all.sh failed on run $i — see $WORK/run-$i.log" >&2
        tail -20 "$WORK/run-$i.log" >&2
        exit 2
    }
    mkdir -p "$WORK/run-$i"
    for p in ios android web; do
        [[ -d "apps/$p-harness/screenshots" ]] || continue
        # Only snapshot captures THIS run actually wrote.
        #
        # A skipped platform (SKIP_IOS=1) leaves the previous run's PNGs
        # sitting in its harness directory. Copying those would compare
        # stale bytes against themselves and report a perfect noise floor
        # for a platform that never ran -- a study that cannot fail is
        # worth nothing. The mtime guard also catches a platform whose
        # capture silently produced nothing this run.
        NEWEST=$(find "apps/$p-harness/screenshots" -name '*.png' -newer "$STAMP" 2>/dev/null | wc -l | tr -d ' ')
        if [[ "$NEWEST" -eq 0 ]]; then
            echo "  $p: no captures written this run — excluded"
            continue
        fi
        cp -R "apps/$p-harness/screenshots" "$WORK/run-$i/$p"
    done
    # Hash on CONTENT, not mtime: `cp` restamps mtime, so a timestamp
    # comparison here would silently compare the copy step, not the runs.
    ( cd "$WORK/run-$i" && find . -name '*.png' | sort | xargs shasum -a 256 ) > "$WORK/run-$i.sha256"
    echo "  captured $(wc -l < "$WORK/run-$i.sha256" | tr -d ' ') png(s)"
done
echo

# ── Byte identity ────────────────────────────────────────────────────────
# A study that compared NOTHING must not report a perfect noise floor.
# Without this, a run where every platform was skipped (or crashed) leaves
# two empty hash lists, `diff` succeeds, and the script prints
# "BYTE-IDENTICAL (0 captures)" and exits 0 — a check that cannot fail,
# which is the exact class of defect this harness keeps finding elsewhere.
CAPTURE_COUNT=$(wc -l < "$WORK/run-1.sha256" | tr -d ' ')
if [[ "$CAPTURE_COUNT" -eq 0 ]]; then
    echo "✗ run 1 captured NOTHING — there is no noise floor to report."
    echo "  Every platform was skipped or failed; see $WORK/run-1.log"
    exit 2
fi

DRIFT=0
for i in $(seq 2 "$RUNS"); do
    if diff -q "$WORK/run-1.sha256" "$WORK/run-$i.sha256" >/dev/null; then
        echo "run 1 vs run $i: BYTE-IDENTICAL ($(wc -l < "$WORK/run-1.sha256" | tr -d ' ') captures)"
    else
        N=$(diff "$WORK/run-1.sha256" "$WORK/run-$i.sha256" | grep -c '^<' || true)
        echo "run 1 vs run $i: $N capture(s) DIFFER"
        diff "$WORK/run-1.sha256" "$WORK/run-$i.sha256" | grep '^<' | sed 's|.*\./|    |'
        DRIFT=1
    fi
done

if [[ "$DRIFT" -eq 0 ]]; then
    echo
    echo "NOISE FLOOR = 0. Every capture is bit-for-bit reproducible across"
    echo "independent runs, so SSIM = 1.0000, dpx = 0.00%, dE = 0 by"
    echo "construction. No metric can flap run-to-run on this machine."
    exit 0
fi

# ── Quantify the drift with the shipping metric code ─────────────────────
# Only reached when bytes differ. Score each platform's run-1-vs-run-2
# captures through compare-screenshots.mjs by mapping the two runs onto
# two of its platform slots (see the header note).
echo
echo "=== quantifying drift (shipping metric code) ==="
EMPTY="$WORK/empty"; mkdir -p "$EMPTY"
for p in ios android web; do
    # Skipped/failed platforms were excluded above, on both sides.
    [[ -d "$WORK/run-1/$p" && -d "$WORK/run-2/$p" ]] || continue
    OUT="$WORK/aa-$p"; mkdir -p "$OUT"
    IOS_SCREENSHOTS_DIR="$WORK/run-1/$p" \
    ANDROID_SCREENSHOTS_DIR="$WORK/run-2/$p" \
    WEB_SCREENSHOTS_DIR="$EMPTY" \
    REPORT_DIR="$OUT" \
    MANIFEST_OUT="$OUT/manifest.json" \
    node tools/visual/compare-screenshots.mjs \
        --input "$FIXTURE" --no-cross-platform-gate --full-lab >"$OUT/log" 2>&1 || true
    node -e '
      const m = require(process.argv[1]);
      const rows = (m.components ?? m.rows ?? []);
      const v = rows.map(r => r.pairs?.["iOS-Android"]).filter(Boolean);
      if (!v.length) { console.log(`  '"$p"': no pairs scored`); process.exit(0); }
      const q = (a, p) => a.slice().sort((x, y) => x - y)[Math.min(a.length - 1, Math.floor(a.length * p))];
      const ssim = v.map(x => x.ssim).filter(x => x != null);
      const px   = v.map(x => x.pixelMismatchedPct).filter(x => x != null);
      const de   = v.map(x => x.labDeltaE?.p95).filter(x => x != null);
      console.log(`  '"$p"': n=${v.length}` +
        `  SSIM min ${Math.min(...ssim).toFixed(6)} p01 ${q(ssim, 0.01).toFixed(6)}` +
        `  dpx max ${Math.max(...px).toFixed(4)}% p99 ${q(px, 0.99).toFixed(4)}%` +
        `  dE95 max ${Math.max(...de).toFixed(3)}`);
    ' "$OUT/manifest.json" 2>/dev/null || echo "  $p: manifest unreadable ($OUT/log)"
done
echo
echo "Thresholds in force: SSIM >= 0.95, dpx <= 2%. Compare the spread"
echo "above against them before promoting any gate to failing."
exit 1
