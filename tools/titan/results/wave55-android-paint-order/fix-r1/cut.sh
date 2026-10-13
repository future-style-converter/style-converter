#!/usr/bin/env bash
# fix-r1/cut.sh <outdir> — cut lane L6's three unit patches from the fix worktree ($L6_WT): `git add -N` the new files
# (intent-to-add, so each is a creation hunk), `git diff HEAD -- <that unit's paths>` per unit, then drop the
# intent-to-add entries again (index only; the working files are untouched). Paths per unit = the note's unit sections.
set -euo pipefail
WT="${L6_WT:?set L6_WT to the fix worktree (the first TREES line of _note.md)}"; OUT="$1"; mkdir -p "$OUT"
M=runtimes/compose/src/main/java/com/styleconverter/runtime; T=runtimes/compose/src/test/java/com/styleconverter/runtime
U1="$M/borders/BehindContentBorder.kt $M/borders/radius/BorderRadiusApplier.kt $T/borders/BehindContentBorderTest.kt $T/borders/BorderRadiusSelfPaintTest.kt"
U2="$M/borders/sides/BorderSideApplier.kt $M/borders/BordersFacade.kt $M/StyleApplier.kt $T/borders/sides/BorderSidesBehindContentTest.kt"
U3="$T/effects/EffectsClipFilterOrderTest.kt"
NEW="$M/borders/BehindContentBorder.kt $T/borders/BehindContentBorderTest.kt $T/borders/sides/BorderSidesBehindContentTest.kt $T/effects/EffectsClipFilterOrderTest.kt"
cd "$WT"
git add -N $NEW
git diff HEAD -- $U1 > "$OUT/unit-U1.patch"
git diff HEAD -- $U2 > "$OUT/unit-U2.patch"
git diff HEAD -- $U3 > "$OUT/unit-U3.patch"
git reset -q -- $NEW
# Every changed path of the worktree's runtimes/ tree must belong to exactly one unit.
ALL="$(git status --porcelain --untracked-files=all -- runtimes | awk '{print $2}' | sort)"
WANT="$(printf '%s\n' $U1 $U2 $U3 | sort)"
[ "$ALL" == "$WANT" ] || { echo "PATH SET MISMATCH"; diff <(echo "$ALL") <(echo "$WANT"); exit 2; }
shasum -a 256 "$OUT"/unit-U*.patch
