#!/usr/bin/env bash
# Skeptic (wave 54 L5) — seam-2 under the per-file lock on the SHARED tree: mkdir lock → apply →
# Catalyst focused pins (Executed N > 0) → restore from HEAD (sha256) → rmdir lock.
set -u
W=/Users/dranak/Documents/Projects/Style-Converter/.claude/worktrees/trusting-bohr-bd6fbf; cd $W
D=tools/titan/results/wave54-ua-heading-face/skeptic
until grep -q BATCH-DONE $D/swift-mutations.out.txt; do sleep 10; done
F=runtimes/swiftui/Sources/StyleConverterRuntime/Renderer/ComponentRenderer.swift
L=tools/titan/runs/wave54-lock/ComponentRenderer.swift; mkdir -p tools/titan/runs/wave54-lock
until mkdir $L 2>/dev/null; do echo "lock busy $(date)"; sleep 60; done
trap 'git show HEAD:$F > $F; rmdir $L 2>/dev/null' EXIT
echo "lock acquired $(date)"; echo "pre sha256 $(shasum -a 256 $F | cut -d' ' -f1)"
git apply $D/../seam-2.patch && echo "patched sha256 $(shasum -a 256 $F | cut -d' ' -f1)"
xcodebuild test -scheme StyleConverterRuntime -destination 'platform=macOS,variant=Mac Catalyst,arch=arm64' \
  -only-testing:StyleConverterRuntimeTests/UAElementFontRuleTests -only-testing:StyleConverterRuntimeTests/UAHeadingFoldGateTests > $D/seam-2.catalyst.log 2>&1
echo "xcodebuild rc=$? $(grep -E 'Executed [0-9]+ tests?' $D/seam-2.catalyst.log | tail -1)"
echo "renderer compiled in window: $(grep -c 'ComponentRenderer.swift' $D/seam-2.catalyst.log) log lines naming ComponentRenderer.swift"
git show HEAD:$F > $F
A=$(shasum -a 256 $F | cut -d' ' -f1); echo "restored sha256 $A $([ $A = 905d1669d3856364c57fa0c821449eabfb28380b781eab557279e0280c996e1e ] && echo BYTE-EXACT-HEAD || echo MISMATCH)"
rmdir $L && echo "lock released $(date)"; trap - EXIT
