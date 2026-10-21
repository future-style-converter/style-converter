#!/usr/bin/env bash
# tools/titan/results/wave55-plan/fix-r4.teeth-before-after.sh — round 4 (plan-skeptic-round-4.verify.md M1-M4; fix-r4.done.md):
# the standing rejection set (probes-teeth.py: round 1 + probes-teeth-r2.py + -r3.py + the round-4 STRICTLY-ADDITIVE cases of
# probes-teeth-r4.py) run
#   BEFORE — on the ROUND-3 probe bytes (plan-skeptic-round-4.fakes/round3-probes/: the seven lane probes at HEAD 611c9f93, the
#            sha256s fix-r3.done.md recorded), in a throwaway worktree — the round-4 cases must be VIOLATED (the fakes they pin
#            were accepted; every round-1 / 2 / 3 case still as pre-registered, the r2 clip-10 case PASS: D33's blind spot), and
#   AFTER  — on the current bytes in the same throwaway — 0 violations (the clip-10 case now pre-registered FAIL and FAILing).
# The same proof fix-r3 / fix-r2 made for their rounds: each instrument change can fail.
# Usage: bash fix-r4.teeth-before-after.sh <scratch-dir>   (writes fix-r4.teeth-before-after.out.txt beside this file)
set -uo pipefail
HERE="$(cd "$(dirname "$0")" && pwd -P)"; SH="$(cd "$HERE/../../../.." && pwd -P)"; P=tools/titan/results/wave55-plan
S="${1:?scratch dir}"; mkdir -p "$S" && S="$(cd "$S" && pwd -P)"
case "$S/" in "$SH"/*) echo "refusing: the scratch dir is inside this tree"; exit 2 ;; esac
OUT="${OUT:-$HERE/fix-r4.teeth-before-after.out.txt}"
exec > >(sed -u -E "s#$S#<scratch>#g" | tee "$OUT") 2>&1
WT="$S/wave55-plan-r4-teeth"; git -C "$SH" worktree remove --force "$WT" 2>/dev/null; rm -rf "$WT"
git -C "$SH" worktree add -q --detach "$WT" HEAD || exit 2
ln -s "$SH/tools/titan/runs" "$WT/tools/titan/runs"; ln -s "$SH/tools/wpt" "$WT/tools/wpt"
rsync -a "$HERE/" "$WT/$P/"
echo "# fix-r4.teeth-before-after — $(date -u +%Y-%m-%dT%H:%M:%SZ); HEAD $(git -C "$SH" rev-parse --short HEAD); throwaway $(basename "$WT"); expectations.json $(shasum -a 256 "$HERE/expectations.json" | cut -c1-16)…"
echo "# probes-teeth.py $(shasum -a 256 "$HERE/probes-teeth.py" | cut -c1-16)… · -r2 $(shasum -a 256 "$HERE/probes-teeth-r2.py" | cut -c1-16)… · -r3 $(shasum -a 256 "$HERE/probes-teeth-r3.py" | cut -c1-16)… · -r4 $(shasum -a 256 "$HERE/probes-teeth-r4.py" | cut -c1-16)… · added_ink4.py $(shasum -a 256 "$HERE/plan-skeptic-round-4.fakes/added_ink4.py" | cut -c1-16)…"
run() {  # <label> <tag>
  echo; echo "## $1"
  for f in "$WT/$P"/{positioned-parent-slot,compose-wpt-content-box-cb,cbb-android,counter-suffix-rows,ua-heading-margin,android-paint-order,hyphen-ios-linebox}.geometry.py; do
    echo "  $(basename "$f") $(shasum -a 256 "$f" | cut -c1-16)…"; done
  ( cd "$WT" && nice -n 19 /opt/homebrew/bin/python3.12 -B "$P/probes-teeth.py" --scratch "$S/teeth-$2" ) > "$S/teeth-$2.out" 2>&1; local rc=$?
  local v v4 vc; v=$(grep -c '^  VIOLATION' "$S/teeth-$2.out"); v4=$(grep '^  VIOLATION' "$S/teeth-$2.out" | grep -c ' r4-'); vc=$(grep '^  VIOLATION' "$S/teeth-$2.out" | grep -c 'clipped-10px')
  echo "  probes-teeth.py exit $rc — $(grep -E '^[0-9]+ cases' "$S/teeth-$2.out")"
  echo "  violations: $v (round-4 cases: $v4; the r2 clip-10 case: $vc; other rounds 1-3: $((v - v4 - vc)))"
  grep '^  VIOLATION' "$S/teeth-$2.out" | sed -E 's/ — .*//' | cut -c1-170 | sed 's/^/  /'
}
cp "$HERE/plan-skeptic-round-4.fakes/round3-probes/"*.geometry.py "$WT/$P/"
run "BEFORE: the round-3 probe bytes (every round-4 strictly-additive case should be a VIOLATION: the fake accepted)" before
cp "$HERE"/{positioned-parent-slot,compose-wpt-content-box-cb,cbb-android,counter-suffix-rows,ua-heading-margin,android-paint-order,hyphen-ios-linebox}.geometry.py "$WT/$P/"
run "AFTER: the current probe bytes (0 violations)" after
rm -f "$WT/tools/titan/runs" "$WT/tools/wpt"; git -C "$SH" worktree remove --force "$WT" && echo && echo "throwaway removed ($(git -C "$SH" worktree list | grep -c "$WT") left)"
