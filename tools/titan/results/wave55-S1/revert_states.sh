#!/usr/bin/env bash
# S1 revert-state driver (throwaway worktree ONLY): for each "<lane> <chain> <suite> <sha...>" line, git revert --no-commit
# the chain in revertOrder, run the suite, record, then restore with `git reset --hard 5bbc2a41` and verify the tree is
# 5bbc2a41-clean before the next state.
set -u
WT=<scratchpad>/wave55-S1-skeptic
OUT=<scratchpad>/s1/revert-states.txt
export JAVA_HOME=$(/usr/libexec/java_home -v 21)
cd "$WT"
while read -r lane chain suite shas; do
  [ -z "$lane" ] && continue; case "$lane" in \#*) continue;; esac
  t0=$SECONDS
  if ! git revert --no-commit $shas > /tmp/s1-rv.$$ 2>&1; then
    echo "$lane [$chain] REVERT-FAILED: $(tr '\n' ' ' < /tmp/s1-rv.$$ | cut -c1-300)" >> "$OUT"; git revert --abort >/dev/null 2>&1; git reset -q --hard 5bbc2a41; continue
  fi
  files=$(git diff --cached --name-only | wc -l | tr -d ' ')
  case "$suite" in
    compose) (cd apps/android-harness && ./gradlew :runtime:testDebugUnitTest --console=plain) > /tmp/s1-st.$$ 2>&1; rc=$?
             cnt="$(find runtimes/compose/build/test-results/testDebugUnitTest -name '*.xml' -print0 | xargs -0 grep -o '<testcase' | wc -l | tr -d ' ') testcases; $(grep -E 'BUILD (SUCCESSFUL|FAILED)' /tmp/s1-st.$$ | tail -1); $(grep -E 'tests completed' /tmp/s1-st.$$ | tail -1)";;
    tooling) node --test tools/visual/*.test.mjs tools/titan/*.test.mjs > /tmp/s1-st.$$ 2>&1; rc=$?; cnt="$(grep -E '^# (tests|pass|fail|skipped) ' /tmp/s1-st.$$ | tr '\n' ' ')";;
    web)     npm -w runtimes/web run test > /tmp/s1-st.$$ 2>&1; rc1=$?; npm -w apps/web-harness run test >> /tmp/s1-st.$$ 2>&1; rc=$((rc1|$?)); cnt="$(grep -E 'Tests +[0-9]+' /tmp/s1-st.$$ | tr -s ' ' | tr '\n' ' ')";;
    swift)   xcodebuild test -scheme StyleConverterRuntime -destination 'platform=macOS,variant=Mac Catalyst,arch=arm64' -derivedDataPath "$WT/.dd" > /tmp/s1-st.$$ 2>&1; rc=$?
             cnt="$(grep -E 'Executed [0-9]+ tests' /tmp/s1-st.$$ | tail -1 | tr -s '\t ' ' '); $(grep -E '\*\* (TEST|BUILD) [A-Z]+ \*\*' /tmp/s1-st.$$ | tail -1)";;
    both)    (cd apps/android-harness && ./gradlew :runtime:testDebugUnitTest --console=plain) > /tmp/s1-st.$$ 2>&1; rc1=$?
             c1="compose $(find runtimes/compose/build/test-results/testDebugUnitTest -name '*.xml' -print0 | xargs -0 grep -o '<testcase' | wc -l | tr -d ' ') $(grep -E 'BUILD (SUCCESSFUL|FAILED)' /tmp/s1-st.$$ | tail -1)"
             xcodebuild test -scheme StyleConverterRuntime -destination 'platform=macOS,variant=Mac Catalyst,arch=arm64' -derivedDataPath "$WT/.dd" > /tmp/s1-sw.$$ 2>&1; rc2=$?
             node --test tools/titan/*.test.mjs > /tmp/s1-tl.$$ 2>&1; rc3=$?
             rc=$((rc1|rc2|rc3)); cnt="$c1 · swift $(grep -E 'Executed [0-9]+ tests' /tmp/s1-sw.$$ | tail -1 | tr -s '\t ' ' ') $(grep -E '\*\* TEST [A-Z]+ \*\*' /tmp/s1-sw.$$ | tail -1) · titan-tooling $(grep -E '^# (pass|fail) ' /tmp/s1-tl.$$ | tr '\n' ' ')";;
  esac
  fails="$(grep -E 'FAILED$|error: |✖|not ok' /tmp/s1-st.$$ /tmp/s1-sw.$$ 2>/dev/null | grep -v 'BUILD FAILED' | head -5 | cut -c1-200 | tr '\n' ' ')"
  echo "$lane [$chain] reverted ${shas} → ${files} paths · suite $suite rc=$rc · $cnt · $((SECONDS-t0))s ${fails:+· FAILS: $fails}" >> "$OUT"
  git reset -q --hard 5bbc2a41
  [ -z "$(git status --porcelain --untracked-files=no)" ] || echo "  !! tree not clean after restore" >> "$OUT"
done < "$1"
rm -f /tmp/s1-rv.$$ /tmp/s1-st.$$ /tmp/s1-sw.$$ /tmp/s1-tl.$$
