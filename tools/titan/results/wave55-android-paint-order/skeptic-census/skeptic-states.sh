#!/usr/bin/env bash
# skeptic-states.sh <worktree> <patch-dir> <log> — the L6 skeptic's revert-state replay. For every state the lane's
# revertStatesCompiled lines claim, the worktree is brought back to 611c9f93 on the lane's paths (checkout of the
# tracked ones, removal of the four created files), the KEPT unit patches are applied in landing order, and the
# Compose main + unit-test sources are compiled and the lane's focused filter set is run. Ends on the full state.
set -uo pipefail
WT="$1"; P="$2"; LOG="$3"
NEW=(runtimes/compose/src/main/java/com/styleconverter/runtime/borders/BehindContentBorder.kt
     runtimes/compose/src/test/java/com/styleconverter/runtime/borders/BehindContentBorderTest.kt
     runtimes/compose/src/test/java/com/styleconverter/runtime/borders/sides/BorderSidesBehindContentTest.kt
     runtimes/compose/src/test/java/com/styleconverter/runtime/effects/EffectsClipFilterOrderTest.kt)
F=('*BehindContentBorder*' '*BorderRadiusSelfPaint*' '*BorderSidesBehindContent*' '*EffectsClipFilterOrder*' '*BorderSide*'
   '*BordersFacade*' '*Border*' '*Backface*' '*StyleApplier*' '*Opacity*' '*Effects*' '*Filter*' '*Backdrop*' '*Color*'
   '*Outline*' '*Shadow*')
state() {  # <label> <kept units...>
  local label="$1"; shift
  git -C "$WT" checkout -q -- runtimes/compose && git -C "$WT" clean -q -f -- "${NEW[@]}"
  local ap=ok; for u in "$@"; do git -C "$WT" apply "$P/unit-$u.patch" || ap="apply-FAILED($u)"; done
  local R="$WT/runtimes/compose/build/test-results/testDebugUnitTest"; rm -rf "$R"
  local args=(); for f in "${F[@]}"; do args+=(--tests "$f"); done
  local out; out=$(cd "$WT/apps/android-harness" && JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew :runtime:compileDebugKotlin :runtime:compileDebugUnitTestKotlin :runtime:testDebugUnitTest "${args[@]}" -q 2>&1); local rc=$?
  local sum; sum=$(/opt/homebrew/bin/python3.12 -B - "$R" <<'PY'
import sys, glob, os, xml.etree.ElementTree as ET
t = n = 0; bad = []
for x in sorted(glob.glob(os.path.join(sys.argv[1], 'TEST-*.xml'))):
    r = ET.parse(x).getroot(); n += 1; t += int(r.get('tests'))
    bad += [r.get('name').split('.')[-1] + '::' + c.get('name') for c in r.iter('testcase') if c.find('failure') is not None or c.find('error') is not None]
print(f'SUMMARY suites={n} tests={t} failed={len(bad)} ' + ' | '.join(bad[:6]))
PY
)
  echo "revertStatesCompiled(skeptic replay): $label (kept: ${*:-none}) apply=$ap gradle rc=$rc $(echo "$out" | grep -E '^e: ' | head -2 | tr '\n' ' ')$sum" >> "$LOG"
  tail -1 "$LOG"
}
state "U3 reverted" U1 U2
state "U2 reverted" U1 U3
state "U2 then U1 reverted" U3
state "U2 and U3 reverted" U1
state "base 611c9f93" 
state "full" U1 U2 U3
echo "final: $(git -C "$WT" status --short | tr '\n' ' ')" >> "$LOG"
echo STATES-DONE >> "$LOG"
