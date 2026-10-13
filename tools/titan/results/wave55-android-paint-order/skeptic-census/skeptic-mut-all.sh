#!/usr/bin/env bash
# skeptic-mut-all.sh <worktree> <log> — every L6 pin mutation (S1–S9 = the lane's M1–M9, re-cut by the skeptic) plus
# the skeptic's own pin-coverage probes (X*), each through skeptic-mut.sh against all four L6 test classes.
set -uo pipefail
WT="$1"; LOG="$2"; H="$(cd "$(dirname "$0")" && pwd)"; M="$H/skeptic-mutations"
B=runtimes/compose/src/main/java/com/styleconverter/runtime
T=('*BehindContentBorder*' '*BorderRadiusSelfPaint*' '*BorderSidesBehindContent*' '*EffectsClipFilterOrder*')
mut() { "$H/skeptic-mut.sh" "$WT" "$LOG" "$1" "$2" "$M/$1.edit" -- "${T[@]}"; }
mut S1 $B/borders/BehindContentBorder.kt
mut S2 $B/borders/radius/BorderRadiusApplier.kt
mut S3 $B/borders/BehindContentBorder.kt
mut S4 $B/borders/BehindContentBorder.kt
mut S5 $B/borders/BehindContentBorder.kt
mut S6 $B/StyleApplier.kt
mut S7 $B/effects/EffectsFacade.kt
mut S8 $B/effects/filter/FilterExtractor.kt
mut S9 $B/borders/sides/BorderSideApplier.kt
mut X1 $B/borders/BehindContentBorder.kt
mut X2 $B/borders/BehindContentBorder.kt
mut X3 $B/borders/BehindContentBorder.kt
mut X4 $B/borders/BehindContentBorder.kt
mut X6 $B/StyleApplier.kt
mut X7 $B/borders/BehindContentBorder.kt
mut X8 $B/borders/BehindContentBorder.kt
echo DONE >> "$LOG"
