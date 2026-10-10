#!/usr/bin/env bash
# Catalyst focused run in the export with a private derived-data dir; prints xcodebuild's Executed line; rc = xcodebuild rc
cd /private/tmp/claude-501/-Users-dranak-Documents-Projects-Style-Converter--claude-worktrees-trusting-bohr-bd6fbf/0c47cad8-074d-4821-bf4c-b5997f23f535/scratchpad/s1/exp || exit 9
xcodebuild test -scheme StyleConverterRuntime -destination 'platform=macOS,variant=Mac Catalyst,arch=arm64' -derivedDataPath /private/tmp/claude-501/-Users-dranak-Documents-Projects-Style-Converter--claude-worktrees-trusting-bohr-bd6fbf/0c47cad8-074d-4821-bf4c-b5997f23f535/scratchpad/s1/dd \
  -only-testing:StyleConverterRuntimeTests/OutOfFlowContainingBlockTests -only-testing:StyleConverterRuntimeTests/FixedHoistTests \
  -only-testing:StyleConverterRuntimeTests/GreedyLineBreakerTests -only-testing:StyleConverterRuntimeTests/GapDecorationSegmentsTests > /private/tmp/claude-501/-Users-dranak-Documents-Projects-Style-Converter--claude-worktrees-trusting-bohr-bd6fbf/0c47cad8-074d-4821-bf4c-b5997f23f535/scratchpad/s1/xc-last.log 2>&1; rc=$?
grep -E 'Executed [0-9]+ tests|error:|failed \(' /private/tmp/claude-501/-Users-dranak-Documents-Projects-Style-Converter--claude-worktrees-trusting-bohr-bd6fbf/0c47cad8-074d-4821-bf4c-b5997f23f535/scratchpad/s1/xc-last.log | grep -v '^\s*$' | tail -8
exit $rc
