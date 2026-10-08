#!/usr/bin/env bash
# Rebuild F (HEAD + the fix pass's apps/runtimes changes) and the A-only state on it in the scratch clone; cut the patch.
set -euo pipefail
SP=/private/tmp/claude-501/-Users-dranak-Documents-Projects-Style-Converter--claude-worktrees-trusting-bohr-bd6fbf/0c47cad8-074d-4821-bf4c-b5997f23f535/scratchpad/fix
R=/Users/dranak/Documents/Projects/Style-Converter/.claude/worktrees/trusting-bohr-bd6fbf
export GIT_AUTHOR_NAME=fix GIT_AUTHOR_EMAIL=fix@x GIT_COMMITTER_NAME=fix GIT_COMMITTER_EMAIL=fix@x
cd $SP/rclone; git checkout -q --detach -f 9516cf2d; git clean -qfd
for f in $(git -C $R status --short --untracked-files=all -- apps runtimes | awk '{print $2}'); do mkdir -p "$(dirname $f)"; cp "$R/$f" "$f"; done
git add -A apps runtimes; git commit -qm "F: fix-pass apps/runtimes changes"; F=$(git rev-parse HEAD)
for f in apps/android-harness/app/src/main/java/com/styleconverter/test/screenshot/ScreenshotCaptureScreen.kt apps/ios-harness/StyleConverterTest/Screenshot/CaptureCanvas.swift apps/web-harness/src/ui/ComposedCaptureGallery.tsx apps/android-harness/app/src/test/java/com/styleconverter/test/screenshot/ComposedCanvasTableBodyTest.kt runtimes/swiftui/Tests/StyleConverterRuntimeTests/WPTCaptureModeTests.swift; do git show e97fe655:$f > $f; done
git rm -q runtimes/compose/src/main/java/com/styleconverter/runtime/background/RootBackgroundPropagation.kt runtimes/compose/src/main/java/com/styleconverter/runtime/background/RootBackgroundUniformity.kt runtimes/swiftui/Sources/StyleConverterRuntime/StyleEngine/background/RootBackgroundPropagation.swift runtimes/swiftui/Sources/StyleConverterRuntime/StyleEngine/background/RootBackgroundUniformity.swift runtimes/web/src/engine/background/RootBackgroundPropagation.ts runtimes/web/src/engine/background/RootBackgroundUniformity.ts apps/android-harness/app/src/test/java/com/styleconverter/test/screenshot/ComposedCanvasRootBackgroundTest.kt apps/web-harness/tests/ui/ComposedCanvasRootBackgroundImage.test.tsx runtimes/web/tests/background/RootBackgroundPropagation.test.ts
git add -A; git commit -qm "A-only revert on F"
git diff $F HEAD > $SP/a-only-revert.F.patch
echo "F=$F AONLY=$(git rev-parse HEAD)"; git diff --stat e97fe655 HEAD -- apps runtimes | tail -1
