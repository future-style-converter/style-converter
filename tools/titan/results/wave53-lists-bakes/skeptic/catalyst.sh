#!/usr/bin/env bash
# tools/titan/results/wave53-lists-bakes/skeptic/catalyst.sh — the L1 skeptic's focused Catalyst run of
# PseudoTextFoldTests (PLAN §2 L1 "May run"), on a PRIVATE derived-data dir (argument 1) so it stays off the shared one.
cd "$(dirname "$0")/../../../../.." && nice -n 10 xcodebuild test -scheme StyleConverterRuntime \
  -destination 'platform=macOS,variant=Mac Catalyst,arch=arm64' \
  -only-testing:StyleConverterRuntimeTests/PseudoTextFoldTests -derivedDataPath "$1" 2>&1 \
  | grep -E "Test Case .*(passed|failed)|error: |Executed [0-9]+ test|\*\* TEST (SUCCEEDED|FAILED)|BUILD FAILED" | grep -v 'passed' | tail -40
exit ${PIPESTATUS[0]}
