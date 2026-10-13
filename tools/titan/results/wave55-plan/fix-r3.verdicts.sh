#!/usr/bin/env bash
# tools/titan/results/wave55-plan/fix-r3.verdicts.sh — round 3: the re-ruled probes keep EVERY verdict of the briefs' own
# pictures (today, the replays / arms / tags / fakes, the controls-can-fail pairs): for each probe and each argument set, the
# round-2 bytes (plan-skeptic-round-3.fakes/round2-probes/, copied beside this file as a dot-file for the run and removed) and
# the current bytes print the same key + OK / WRONG per line. The new reasons may only reject the new (added-ink) fakes.
# Usage: bash fix-r3.verdicts.sh   (writes fix-r3.verdicts-preserved.out.txt beside this file; no other file is left behind)
set -uo pipefail
HERE="$(cd "$(dirname "$0")" && pwd -P)"; SH="$(cd "$HERE/../../../.." && pwd -P)"; PY="/opt/homebrew/bin/python3.12 -B"
OUT="$HERE/fix-r3.verdicts-preserved.out.txt"; DIFFS=0
v() { sed -E 's/^(.{0,90}[^ ]) .*(OK|WRONG).*$/\1 \2/' | awk '{v=$NF; $NF=""; print $1" "$2" "$3" "$4" "v}'; }
cmp1() {  # <probe> <args…>
  local p="$1"; shift; local old="$HERE/.r3old.$p"
  cp "$HERE/plan-skeptic-round-3.fakes/round2-probes/$p" "$old"
  local a b; a="$(cd "$SH" && $PY "$old" "$@" 2>&1 | v)"; b="$(cd "$SH" && $PY "$HERE/$p" "$@" 2>&1 | v)"; rm -f "$old"
  if [[ "$a" == "$b" ]]; then echo "  identical  $p $* ($(wc -l <<<"$b" | tr -d ' ') lines)"
  else echo "  DIFFER     $p $*"; diff <(echo "$a") <(echo "$b") | head -6 | sed 's/^/    /'; DIFFS=$((DIFFS + 1)); fi
}
{
echo "# fix-r3.verdicts-preserved.out.txt — $(date -u +%Y-%m-%dT%H:%M:%SZ); HEAD $(git -C "$SH" rev-parse --short HEAD); round-2 bytes vs current bytes, verdict per line"
for a in "wave54-final wave54-final" "wave54-final wave54-final --replay U1U2" "wave54-final wave54-final --replay U1" "wave54-final wave54-final --replay U2" "wave54-final wave54-final --replay PLAIN" "wave54-open wave54-final"; do cmp1 positioned-parent-slot.geometry.py $a; done
for a in wave54-final wave54-probe wave54-open; do cmp1 compose-wpt-content-box-cb.geometry.py $a; done
R=$HERE/cbb-android-replay
for a in wave54-final wave54-probe; do cmp1 cbb-android.geometry.py $a --png replay-U1U2=$R/android-clip-3.png F3-centered=$R/fake-centered.png F4-unclipped=$R/fake-unclipped.png F6-double=$R/fake-double.png; done
for a in "wave54-final" "--replay Rm" "--replay Rw" "--replay R0" "--replay R1" "--replay Rd" "--replay X" "--replay U" "--fakes" "wave54-open --base wave54-final"; do cmp1 hyphen-ios-linebox.geometry.py $a; done
for a in "wave54-final" "wave54-open --base wave54-final"; do cmp1 ua-heading-margin.geometry.py $a; done
for arm in T U1 R X2 B C D C4; do cmp1 ua-heading-margin.geometry.py wave54-final --arm $arm; done
for t in wave54-final replay:ok-all replay:H-broken replay:H-nodot replay:H-flush replay:H-low replay:C-shift8 replay:C-nocomma replay:C-space-only replay:I-wholeline replay:K-leak; do cmp1 counter-suffix-rows.geometry.py $t wave54-final; done
for a in "wave54-final --base wave54-final" "--replays" "wave54-open --base wave54-final"; do cmp1 android-paint-order.geometry.py $a; done
echo; echo "fix-r3.verdicts: $([ $DIFFS -eq 0 ] && echo 'every verdict preserved' || echo "$DIFFS argument set(s) DIFFER")"
} | tee "$OUT"
exit $DIFFS
