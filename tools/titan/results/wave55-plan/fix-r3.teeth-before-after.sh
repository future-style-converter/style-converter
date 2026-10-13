#!/usr/bin/env bash
# tools/titan/results/wave55-plan/fix-r3.teeth-before-after.sh — round 3 (plan-skeptic-round-3.verify.md M2): the standing
# rejection set (probes-teeth.py: round 1 + probes-teeth-r2.py + the round-3 ADDED-INK cases of probes-teeth-r3.py) run
#   BEFORE — on the ROUND-2 probe bytes (plan-skeptic-round-3.fakes/round2-probes/: the seven lane probes as fix-r2 left
#            them, sha256 recorded in fix-r2.done.md), in a throwaway worktree — the round-3 cases must be VIOLATED (the
#            added-ink fakes they pin were accepted; every round-1 / round-2 case still as pre-registered), and
#   AFTER  — on the current bytes in the same throwaway — 0 violations.
# The same proof fix-r2 made for round 2 (fix-r2.teeth-before-after.out.txt): each instrument change can fail.
# Usage: bash fix-r3.teeth-before-after.sh <scratch-dir>   (writes fix-r3.teeth-before-after.out.txt beside this file)
set -uo pipefail
HERE="$(cd "$(dirname "$0")" && pwd -P)"; SH="$(cd "$HERE/../../../.." && pwd -P)"; P=tools/titan/results/wave55-plan
S="${1:?scratch dir}"; mkdir -p "$S" && S="$(cd "$S" && pwd -P)"
case "$S/" in "$SH"/*) echo "refusing: the scratch dir is inside this tree"; exit 2 ;; esac
exec > >(sed -u -E "s#$S#<scratch>#g" | tee "$HERE/fix-r3.teeth-before-after.out.txt") 2>&1
WT="$S/wave55-fix-r3-teeth-wt"; git -C "$SH" worktree remove --force "$WT" 2>/dev/null; rm -rf "$WT"
git -C "$SH" worktree add -q --detach "$WT" HEAD || exit 2
ln -s "$SH/tools/titan/runs" "$WT/tools/titan/runs"; ln -s "$SH/tools/wpt" "$WT/tools/wpt"
rsync -a "$HERE/" "$WT/$P/"
echo "# fix-r3.teeth-before-after — $(date -u +%Y-%m-%dT%H:%M:%SZ); HEAD $(git -C "$SH" rev-parse --short HEAD); throwaway $(basename "$WT"); expectations.json $(shasum -a 256 "$HERE/expectations.json" | cut -c1-16)…"
echo "# probes-teeth.py $(shasum -a 256 "$HERE/probes-teeth.py" | cut -c1-16)… · -r2 $(shasum -a 256 "$HERE/probes-teeth-r2.py" | cut -c1-16)… · -r3 $(shasum -a 256 "$HERE/probes-teeth-r3.py" | cut -c1-16)… · added_ink.py $(shasum -a 256 "$HERE/plan-skeptic-round-3.fakes/added_ink.py" | cut -c1-16)…"
run() {  # <label>
  echo; echo "## $1"
  for f in "$WT/$P"/{positioned-parent-slot,compose-wpt-content-box-cb,cbb-android,counter-suffix-rows,ua-heading-margin,android-paint-order,hyphen-ios-linebox}.geometry.py; do
    echo "  $(basename "$f") $(shasum -a 256 "$f" | cut -c1-16)…"; done
  ( cd "$WT" && nice -n 19 /opt/homebrew/bin/python3.12 -B "$P/probes-teeth.py" --scratch "$S/teeth-$2" ) > "$S/teeth-$2.out" 2>&1; local rc=$?
  local v; v=$(grep -c '^  VIOLATION' "$S/teeth-$2.out"); local v3; v3=$(grep '^  VIOLATION' "$S/teeth-$2.out" | grep -c ' r3-')
  echo "  probes-teeth.py exit $rc — $(grep -E '^[0-9]+ cases' "$S/teeth-$2.out")"
  echo "  violations: $v (round-3 cases: $v3; rounds 1-2: $((v - v3)))"
  grep '^  VIOLATION' "$S/teeth-$2.out" | sed -E 's/ — .*//' | cut -c1-170 | sed 's/^/  /'
}
cp "$HERE/plan-skeptic-round-3.fakes/round2-probes/"*.geometry.py "$WT/$P/"
run "BEFORE: the round-2 probe bytes (every round-3 added-ink case should be a VIOLATION: the fake accepted)" before
cp "$HERE"/{positioned-parent-slot,compose-wpt-content-box-cb,cbb-android,counter-suffix-rows,ua-heading-margin,android-paint-order,hyphen-ios-linebox}.geometry.py "$WT/$P/"
run "AFTER: the current probe bytes (0 violations)" after
rm -f "$WT/tools/titan/runs" "$WT/tools/wpt"; git -C "$SH" worktree remove --force "$WT" && echo && echo "throwaway removed ($(git -C "$SH" worktree list | grep -c "$WT") left)"
