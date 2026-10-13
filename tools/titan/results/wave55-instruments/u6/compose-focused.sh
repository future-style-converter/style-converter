#!/usr/bin/env bash
# The U6-compose focused classes (C1-C7), run from apps/android-harness of the cwd checkout.
export JAVA_HOME=$(/usr/libexec/java_home -v 21)
cd apps/android-harness && nice -n 5 ./gradlew :runtime:testDebugUnitTest --console=plain \
  --tests 'com.styleconverter.runtime.typography.wrapping.HyphenateCharacterExtractorTest' \
  --tests 'com.styleconverter.runtime.typography.wrapping.PreBreakPipelineClampHyphenTest' \
  --tests 'com.styleconverter.runtime.typography.wrapping.SoftHyphenCutsTest' \
  --tests 'com.styleconverter.runtime.layout.position.OutOfFlowContainingBlockBranchesTest' 2>&1 | grep -vE '^w: '
exit ${PIPESTATUS[0]}
