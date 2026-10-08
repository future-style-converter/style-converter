#!/usr/bin/env bash
# wave 53 · L5 harness-hygiene — the ios-harness XCTest verification (T1).
#
# ORCHESTRATOR WINDOW ONLY for the run modes: they boot nothing, but they need
# an already-BOOTED simulator, which the lanes may not touch (PLAN §10 item 4).
# `--build-only` is device-free (generic simulator destination, compile only):
# the lane ran it to prove every step below compiles and that the bundle holds
# what the pins read.
#
# Everything runs in an EXPORT: <dir>/x = `git archive $REV` of the runtime,
# the harness and the vendored IR, with L5's four ios-harness files overlaid
# from the working tree. The shared tree is never edited (CaptureCanvas.swift
# belongs to L3), so the M2 mutation needs no lock, and $REV defaults to the
# wave-53 base 762d3d30 (= cdb8a845's runtime and harness: "L5 on the cdb8a845
# text", PLAN §2 L5). The integrated L5 + L3 tree's 30/30 is a separate,
# in-place run (the §4 step 7 sweep), not this script.
#
# Usage: xctest-window.sh <scratch-dir> <booted-simulator-udid>   # green + M1..M3 + green
#        xctest-window.sh <scratch-dir> --build-only               # device-free compile check
set -euo pipefail
HERE="$(cd "$(dirname "$0")" && pwd)"
TREE="$(cd "$HERE/../../../.." && pwd)"
REV="${REV:-762d3d30}"
D="${1:?scratch dir}"; MODE="${2:?simulator udid or --build-only}"
X="$D/x"; H="$X/apps/ios-harness"; LOG="$D/xctest-window.log"
L5_FILES=(apps/ios-harness/project.yml
          apps/ios-harness/StyleConverterTestTests/ComposedCanvasIcbClipTests.swift
          apps/ios-harness/StyleConverterTestTests/ComposedCanvasPaddingTests.swift
          apps/ios-harness/StyleConverterTestTests/ComposedRootInlineFlowTests.swift)
CANVAS="$H/StyleConverterTest/Screenshot/CaptureCanvas.swift"
say() { echo "[xctest-window $(date +%H:%M:%S)] $*" | tee -a "$LOG"; }
sha() { shasum -a 256 "$1" | cut -c1-16; }

# 1. The export.
rm -rf "$X"; mkdir -p "$X"
git -C "$TREE" archive "$REV" Package.swift runtimes/swiftui apps/ios-harness tools/titan/fixtures/per-test-ir/wave49-final | tar -x -C "$X"
for f in "${L5_FILES[@]}"; do cp "$TREE/$f" "$X/$f"; done
say "export at $X: REV=$REV + L5 overlay; CaptureCanvas.swift sha256=$(sha "$CANVAS")"

# 2. Generate, then the pbxproj greps (brief §8.1, corrected: CaptureCanvas.swift
#    rides a Copy FILES phase — Xcode drops a .swift from Copy Bundle Resources).
gen() { (cd "$H" && rm -rf StyleConverterTest.xcodeproj && xcodegen generate >/dev/null); }
gen
P="$H/StyleConverterTest.xcodeproj/project.pbxproj"
for k in 'CaptureCanvas.swift in Sources' 'CaptureCanvas.swift in CopyFiles' \
         'wpt__CSS2__abspos__static-inside-inline-block.json in Resources' \
         'wpt__css-cascade__scope-pseudo-element.json in Resources'; do
  # Each build file is declared once and listed once in its phase: 2 mentions.
  say "pbxproj '$k': $(grep -c "/\* $k \*/" "$P") mentions (want 2)"
done

# xct <label> [only-testing…] → runs (or, --build-only, compiles) the scheme.
xct() {
  local label="$1"; shift
  local args=(-project "$H/StyleConverterTest.xcodeproj" -scheme StyleConverterTestTests -derivedDataPath "$D/dd")
  if [[ "$MODE" == --build-only ]]; then
    xcodebuild build-for-testing "${args[@]}" -destination 'generic/platform=iOS Simulator' >"$D/$label.log" 2>&1 \
      && say "$label: BUILD OK" || say "$label: BUILD FAILED (see $D/$label.log)"
    local b="$D/dd/Build/Products/Debug-iphonesimulator/StyleConverterTest.app/PlugIns/StyleConverterTestTests.xctest"
    say "$label: bundle resources = $(cd "$b" && ls | grep -E '\.(json|swift)$' | tr '\n' ' ')"
  else
    rm -rf "$D/$label.xcresult"
    xcodebuild test "${args[@]}" -destination "platform=iOS Simulator,id=$MODE" -resultBundlePath "$D/$label.xcresult" "$@" >"$D/$label.log" 2>&1 || true
    xcrun xcresulttool get test-results summary --path "$D/$label.xcresult" >"$D/$label.summary.json" 2>&1 || true
    say "$label: $(python3 -c "import json,sys;d=json.load(open(sys.argv[1]));print('passed',d.get('passedTests'),'failed',d.get('failedTests'),'|',' ; '.join(f\"{t.get('testName')}: {t.get('failureText','')[:140]}\" for t in d.get('testFailures',[])))" "$D/$label.summary.json" 2>/dev/null || echo 'no summary — see the log')"
  fi
}
# mutate <file> <python-replace-expr-file> <label> [only-testing…]: cp → sha → edit → run → cp back → sha.
mutate() {
  local f="$1" py="$2" label="$3"; shift 3
  local before; before="$(sha "$f")"; cp "$f" "$D/$label.orig"
  python3 - "$f" <<<"$py"
  say "$label: mutated $(basename "$f") $before → $(sha "$f")"
  [[ "$(basename "$f")" == project.yml ]] && gen
  xct "$label" "$@"
  cp "$D/$label.orig" "$f"; [[ "$(basename "$f")" == project.yml ]] && gen
  say "$label: restored $(basename "$f") sha256 $(sha "$f") ($( [[ "$(sha "$f")" == "$before" ]] && echo BYTE-EXACT || echo MISMATCH))"
}

# 3. GREEN — want 30 passed / 0 failed.
xct green
# 4. M1 — drop scope-pseudo-element's resource entry → that pin red "not a resource" (the bundle, not the disk, is read).
mutate "$H/project.yml" '
import sys; p=sys.argv[1]; s=open(p).read()
old="      - path: ../../tools/titan/fixtures/per-test-ir/wave49-final/css-cascade/wpt__css-cascade__scope-pseudo-element.json\n        buildPhase: resources\n"
assert s.count(old)==1; open(p,"w").write(s.replace(old,""))' M1 -only-testing:StyleConverterTestTests/ComposedRootInlineFlowTests
# 5. M2 — the ICB clip moved after .background(canvasBackground) → testClipPrecedesTheCanvasBackground red (IOS-M1, first executed in Swift).
mutate "$CANVAS" '
import sys; p=sys.argv[1]; s=open(p).read()
clip="        .clipShape(WPTCanvas.IcbClipBand(frame: Self.padding))\n"; bg="\n        .background(canvasBackground)\n"
assert s.count(clip)==1 and s.count(bg)==1; s=s.replace(clip,""); open(p,"w").write(s.replace(bg, bg+clip))' M2 -only-testing:StyleConverterTestTests/ComposedCanvasIcbClipTests
# 6. M3 — the wave-24 rule restored in the resolver → the four updated padding pins red.
mutate "$CANVAS" '
import sys; p=sys.argv[1]; s=open(p).read()
old="return Self.padding + max(0, CGFloat(px))"; assert s.count(old)==1
open(p,"w").write(s.replace(old,"return max(0, CGFloat(px))"))' M3 -only-testing:StyleConverterTestTests/ComposedCanvasPaddingTests
# 7. GREEN again after every restore.
xct green-after
say "done — $LOG"
