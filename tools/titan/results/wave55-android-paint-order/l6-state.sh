#!/usr/bin/env bash
# state.sh <label> <unit patches to keep…> — bring the L6 worktree to a revert state on a clean 611c9f93 checkout of
# the lane's owned paths, then compile + run the focused suites. Prints one revertStatesCompiled line.
set -uo pipefail
WT="${L6_WT:?set L6_WT to the lane worktree (the TREES line of _note.md)}"
D="$(cd "$(dirname "$0")" && pwd)"
S=$(dirname "$0")
P=runtimes/compose/src
NEW="$P/main/java/com/styleconverter/runtime/borders/BehindContentBorder.kt $P/test/java/com/styleconverter/runtime/borders/BehindContentBorderTest.kt $P/test/java/com/styleconverter/runtime/borders/sides/BorderSidesBehindContentTest.kt $P/test/java/com/styleconverter/runtime/effects/EffectsClipFilterOrderTest.kt"
L="$1"; shift
cd "$WT"
# Drop the intent-to-add index entries (index only), restore tracked files, remove the lane's new files.
for f in $NEW; do git ls-files --error-unmatch "$f" >/dev/null 2>&1 && git rm --cached -q "$f"; done
git checkout -- .
for f in $NEW; do rm -f "$f"; done
[ -z "$(git status --porcelain --untracked-files=all -- runtimes apps converter Package.swift)" ] || { echo "NOT CLEAN"; git status --short; exit 2; }
kept=""
for u in "$@"; do git apply "$D/unit-$u.patch" || { echo "APPLY FAILED $u"; exit 3; }; kept="$kept $u"; done
export JAVA_HOME=$(/usr/libexec/java_home -v 21)
c=$(cd apps/android-harness && ./gradlew :runtime:compileDebugKotlin :runtime:compileDebugUnitTestKotlin -q 2>&1); crc=$?
echo "$c" | grep -E '^e: ' | head -5
t=$("$S/l6-rt.sh" '*BehindContentBorder*' '*BorderRadiusSelfPaint*' '*BorderSidesBehindContent*' '*EffectsClipFilterOrder*' '*BorderSide*' '*BordersFacade*' '*Border*' '*Backface*' '*StyleApplier*' '*Opacity*' '*Effects*' '*Filter*' '*Backdrop*' '*Color*' '*Outline*' '*Shadow*' 2>&1)
sum=$(echo "$t" | grep -E '^SUMMARY'); fails=$(echo "$t" | grep -E '^FAIL' | tr '\n' ';')
echo "revertStatesCompiled: $L (kept:${kept:- none}): compileDebugKotlin+compileDebugUnitTestKotlin rc=$crc; focused :runtime suites → $sum ${fails}"
