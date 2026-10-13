#!/usr/bin/env bash
# The U6-swift focused classes (S1-S6) under Mac Catalyst, with the lane worktree's private DerivedData (<checkout>/.dd).
nice -n 5 xcodebuild test -scheme StyleConverterRuntime -destination 'platform=macOS,variant=Mac Catalyst,arch=arm64' \
  -derivedDataPath "$PWD/.dd" -only-testing:StyleConverterRuntimeTests/HyphenateCharacterCascadeTests \
  -only-testing:StyleConverterRuntimeTests/OutOfFlowContainingBlockBranchesTests 2>&1 | grep -E "Test Case .*(passed|failed)|error: |\*\* TEST|Executed [0-9]+ tests" | sed -e 's/ (.*seconds)\.$//'
exit ${PIPESTATUS[0]}
