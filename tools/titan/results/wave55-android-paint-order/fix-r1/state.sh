#!/usr/bin/env bash
# fix-r1/state.sh <patch-dir> <units…> — bring the fix worktree ($L6_WT) to a clean 611c9f93 checkout of the lane's
# paths and `git apply` <patch-dir>/unit-<U>.patch (or .patch.txt) for each named unit, in the order given. No build.
set -euo pipefail
WT="${L6_WT:?set L6_WT to the fix worktree (the first TREES line of _note.md)}"; PD="$1"; shift
M=runtimes/compose/src/main/java/com/styleconverter/runtime; T=runtimes/compose/src/test/java/com/styleconverter/runtime
NEW="$M/borders/BehindContentBorder.kt $T/borders/BehindContentBorderTest.kt $T/borders/sides/BorderSidesBehindContentTest.kt $T/effects/EffectsClipFilterOrderTest.kt"
cd "$WT"
for f in $NEW; do git ls-files --error-unmatch "$f" >/dev/null 2>&1 && git rm --cached -q "$f"; done
git checkout -- runtimes
git clean -fdq -- $NEW
[ -z "$(git status --porcelain --untracked-files=all -- runtimes apps converter Package.swift)" ] || { echo "NOT CLEAN"; git status --short; exit 2; }
for u in "$@"; do p="$PD/unit-$u.patch"; [ -f "$p" ] || p="$p.txt"; git apply "$p"; done
echo "state: kept ${*:-none} from $PD"
