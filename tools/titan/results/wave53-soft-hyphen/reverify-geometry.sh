#!/bin/bash
# tools/titan/results/wave53-soft-hyphen/reverify-geometry.sh — wave 53 L2 RE-VERIFIER, skeptic D1 (must-fix).
# Re-runs the skeptic's own D1 checks, then applies the fix pass's two orchestrator hunks to COPIES of the plan files
# (never the committed ones) and re-proves them: restated probe on wave52-ship / wave53-open, three executed probe
# mutations (red -> restore sha256 -> green), and plan-build.py regeneration of expectations.json / watchlist.txt.
# Usage: reverify-geometry.sh <empty temp dir>   (reads the shared tree; writes only under the temp dir; prints to stdout)
set -u
W=${1:?usage: reverify-geometry.sh <temp dir>}; T=$(cd "$(dirname "$0")/../../../.." && pwd); D=tools/titan/results/wave53-plan
H=$T/tools/titan/results/wave53-soft-hyphen
echo "# tree $T HEAD $(git -C $T rev-parse --short HEAD)"
echo "## 0. committed plan files (expect HEAD bytes = hunks NOT yet applied)"
for f in soft-hyphen.geometry.py plan-build.py expectations.json; do a=$(shasum -a 256 $T/$D/$f|cut -c1-64); b=$(git -C $T show HEAD:$D/$f|shasum -a 256|cut -c1-64); echo "$([ $a = $b ] && echo EQ-HEAD || echo DIFFERS) $a $f"; done
(cd $T && git apply --check $H/hunk-for-orchestrator-1.patch && echo "hunk-1 git apply --check OK"; git apply --check $H/hunk-for-orchestrator-2.patch && echo "hunk-2 git apply --check OK")
echo "## 1. skeptic's own D1 checks, committed probe"
(cd $T && python3 $D/soft-hyphen.geometry.py wave52-ship; echo "exit=$?")
(cd $T && python3 $H/skeptic-geometry-anchor.py wave52-ship > $W/anchor.txt; echo "anchor exit=$?"; cmp -s $W/anchor.txt $H/skeptic-evidence/geometry-anchor.wave52-ship.txt && echo "skeptic-geometry-anchor.py output byte-identical to skeptic-evidence/geometry-anchor.wave52-ship.txt"; grep -E '^(sum<300|max<200) hyphens/hyphens-span-002 android' $W/anchor.txt)
echo "## 2. mirror: committed plan files + hunk-1, refs/runs symlinked (geometry_common resolves ROOT from __file__)"
rm -rf $W/root; mkdir -p $W/root/$D; ln -s $T/tools/wpt $W/root/tools/wpt; ln -s $T/tools/titan/runs $W/root/tools/titan/runs
for f in soft-hyphen.geometry.py geometry_common.py; do git -C $T show HEAD:$D/$f > $W/root/$D/$f; done
P=$W/root/$D/soft-hyphen.geometry.py; (cd $W/root && git apply $H/hunk-for-orchestrator-1.patch) && echo "hunk-1 applied; sha256 $(shasum -a 256 $P|cut -c1-64)"
for run in wave52-ship wave53-open; do echo "### restated probe, $run"; (cd $W/root && python3 $D/soft-hyphen.geometry.py $run > $W/r.$run.txt; e=$?; cat $W/r.$run.txt; echo "exit=$e"; (cat $W/r.$run.txt; echo "exit=$e") | cmp -s - $H/geometry-restated.$run.txt && echo "byte-identical to geometry-restated.$run.txt"); done
echo "## 3. executed probe mutations (mirror copy only)"
cp $P $W/probe.bak; B=$(shasum -a 256 $P|cut -c1-64)
mut() { python3 - "$P" "$@" <<'PY'
import sys; p=sys.argv[1]; s=open(p).read(); pairs=sys.argv[2:]
for i in range(0,len(pairs),2):
    assert s.count(pairs[i])==1, pairs[i]; s=s.replace(pairs[i],pairs[i+1])
open(p,'w').write(s)
PY
}
INK_NEW='    return max(p[0], p[1], p[2]) < 200'; INK_OLD='    return p[0] + p[1] + p[2] < 300'
FLAG=$'        if test == ANCHOR and why is not None:\n            anchor_failed = True'
echo "### RV-G1 ink() back to r+g+b < 300 — expect span-002 android WRONG, SELF-CHECK FAILED, exit 1"
mut "$INK_NEW" "$INK_OLD"; echo "mutated $(shasum -a 256 $P|cut -c1-64)"; (cd $W/root && python3 $D/soft-hyphen.geometry.py wave52-ship > $W/g1.txt; echo "exit=$?"); grep -E 'span-002 android|SELF-CHECK' $W/g1.txt
cp $W/probe.bak $P; echo "restored $(shasum -a 256 $P|cut -c1-64) (before $B)"
echo "### RV-G2 RV-G1 + the anchor flag deleted — expect exit 0 (the self-check, not luck, makes RV-G1 red)"
mut "$INK_NEW" "$INK_OLD" "$FLAG" "        pass"; echo "mutated $(shasum -a 256 $P|cut -c1-64)"; (cd $W/root && python3 $D/soft-hyphen.geometry.py wave52-ship > $W/g2.txt; echo "exit=$?"); grep -E 'span-002 android|SELF-CHECK' $W/g2.txt
cp $W/probe.bak $P; echo "restored $(shasum -a 256 $P|cut -c1-64) (before $B)"
echo "### RV-G3 unmutated probe on a run with no captures — expect anchor MISSING, SELF-CHECK FAILED, exit 1"
(cd $W/root && python3 $D/soft-hyphen.geometry.py no-such-run > $W/g3.txt; echo "exit=$?"); grep -c MISSING $W/g3.txt | sed 's/^/MISSING rows: /'; grep SELF-CHECK $W/g3.txt
for run in wave52-ship wave53-open; do (cd $W/root && python3 $D/soft-hyphen.geometry.py $run >/dev/null; echo "green after restore, $run exit=$?"); done
echo "## 4. hunk-2: plan-build.py regeneration in a git-archive copy of $D"
rm -rf $W/pb; mkdir -p $W/pb; git -C $T archive HEAD $D | tar -x -C $W/pb
(cd $W/pb && python3 $D/plan-build.py >/dev/null 2>&1; echo "unpatched regen exit=$?"; for f in expectations.json watchlist.txt; do git -C $T show HEAD:$D/$f | cmp -s - $D/$f && echo "unpatched regen: $f byte-identical to HEAD"; done
 git -C $T show HEAD:$D/expectations.json > $D/expectations.json
 git apply $H/hunk-for-orchestrator-1.patch && git apply $H/hunk-for-orchestrator-2.patch && echo "both hunks applied"; cp $D/expectations.json $W/exp.patched.json
 python3 $D/plan-build.py >/dev/null 2>&1; echo "patched regen exit=$?"; cmp -s $W/exp.patched.json $D/expectations.json && echo "patched plan-build.py regenerates the patched expectations.json byte-identically"
 git -C $T show HEAD:$D/watchlist.txt | cmp -s - $D/watchlist.txt && echo "patched regen: watchlist.txt byte-identical to HEAD"
 git -C $T show HEAD:$D/expectations.json | diff - $D/expectations.json | grep -c '^[<>]' | sed 's/^/expectations.json changed lines: /')
