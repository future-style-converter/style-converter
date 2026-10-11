#!/usr/bin/env bash
# plan-skeptic-round-1.process-holes.repro.sh <scratch-dir> — the executed checks behind
# plan-skeptic-round-1.process-holes.md (wave 55, plan-skeptic "process holes", round 1).
#
# Why a script: every defect in the report is a claim about what the plan's own tools DO; this file re-runs each one so a
# fix lane can turn it green and an S1 reader can re-check it. Read-only on the shared tree: plan-build.py runs with
# --out <scratch>, plan-prose.py runs on a scratch COPY of PLAN.md, land-units.sh runs --check / --print-order only
# (its --check is read-only by construction: tree_mut exits 70), and the only git commands that change state are
# `git worktree add --detach <scratch>/wt-status HEAD` and `git worktree remove` of it (the standing throwaway rule).
# Interpreter: the plan's pinned /opt/homebrew/bin/python3.12 -B (C14).
set -uo pipefail
S="$(mkdir -p "${1:?usage: plan-skeptic-round-1.process-holes.repro.sh <scratch-dir>}" && cd "$1" && pwd -P)"
HERE="$(cd "$(dirname "$0")" && pwd -P)"; ROOT="$(git -C "$HERE" rev-parse --show-toplevel)"
PY="/opt/homebrew/bin/python3.12 -B"; P=tools/titan/results/wave55-plan; G=tools/titan/results/wave55-gate
cd "$ROOT" || exit 2
hdr() { printf '\n## %s\n' "$*"; }
snap() { { git -C "$ROOT" rev-parse HEAD; git -C "$ROOT" --no-optional-locks status --short --untracked-files=all | grep -v "^?? $P/\|^?? $G/"; } ; }
BEFORE="$(snap)"

hdr "E1 plan-prose.py --check on the shared tree"
$PY $P/plan-prose.py --check; echo "exit=$?"

hdr "E2 the generator reproduces its outputs (plan-build.py --out <scratch>)"
mkdir -p "$S/repro"; $PY $P/plan-build.py --out "$S/repro" > "$S/repro/out.txt" 2>&1; echo "exit=$?"
for f in expectations.json watchlist.txt; do cmp -s "$S/repro/$f" "$P/$f" && echo "$f byte-identical" || echo "$f DIFFERS"; done
cmp -s "$S/repro/out.txt" "$P/plan-build.out.txt" && echo "plan-build.out.txt byte-identical" || echo "plan-build.out.txt DIFFERS"

hdr "E4 an L6 U2 revert does not withdraw the may-move class (M4) and keeps its arm (S8)"
mkdir -p "$S/rev-l6u2"
echo '[{"lane":"L6-android-paint-order","unit":"U2","run":"wave55-pre","commit":"aaaaaaa","revertCommit":"bbbbbbb","rule":"4: [X] FAIL (fake)"}]' > "$S/rev-l6u2/reverted.json"
$PY $P/plan-build.py --out "$S/rev-l6u2" --reverted "$S/rev-l6u2/reverted.json" > "$S/rev-l6u2/out.txt" 2>&1; echo "plan-build exit=$?"
$PY - "$S/repro/expectations.json" "$S/rev-l6u2/expectations.json" <<'PYX'
import json, sys
a, b = (json.load(open(p)) for p in sys.argv[1:3])
print('unionMayMoveCaptures.android', len(a['unionMayMoveCaptures']['android']), '->', len(b['unionMayMoveCaptures']['android']), '(after L6 U2 reverted; want 0)')
print('lanes.L6.mayMove.android    ', len(b['lanes']['L6-android-paint-order']['mayMove']['android']))
print('carriersWithdrawn.android   ', len(b['probeDecisions']['reverted'][0]['carriersWithdrawn']['captures']['android']))
print('abArms after the revert     ', [(x['arm'], x['lane'].split('-')[0], x['when']) for x in b['abArms']], '(the L6 drop-U2 arm is still listed)')
PYX
grep -n "const mayMoveSrc" $G/control-check.mjs

hdr "E5 SeamReachabilityTest.kt: five module lines from three lanes (M1)"
F=runtimes/compose/src/test/java/com/styleconverter/runtime/core/renderer/SeamReachabilityTest.kt
W="$S/srt"; rm -rf "$W"; mkdir -p "$W"
# cut <name> <anchor-or-END> <line> [base-file]: a unified diff that inserts <line> after <anchor> (END = after the last
# list entry), cut on <base-file> (default: HEAD's bytes) — what a lane in its own worktree off HEAD would deliver.
cut_patch() {
  local name="$1" anchor="$2" add="$3" base="${4:-}"
  mkdir -p "$W/a" "$W/b"; if [[ -n "$base" ]]; then cp "$base" "$W/a/f"; else git show "HEAD:$F" > "$W/a/f"; fi
  $PY - "$W/a/f" "$W/b/f" "$anchor" "$add" <<'PYX'
import sys
a, b, anc, add = sys.argv[1:5]; L = open(a).read().split('\n')
i = max(k for k, l in enumerate(L[:200]) if l.strip().endswith('.kt",')) if anc == 'END' else next(k for k, l in enumerate(L) if l.strip() == anc)
L[i + 1:i + 1] = ['            ' + add]; open(b, 'w').write('\n'.join(L))
PYX
  diff -u "$W/a/f" "$W/b/f" | sed "1s#.*#--- a/$F#; 2s#.*#+++ b/$F#" > "$W/$name.patch"; cp "$W/b/f" "$W/after_$name"; cp "$W/a/f" "$W/before_$name"; }
land() { # <label> <patch…>: apply in order on HEAD's bytes, then revert latest-first, then the FIRST alone on the full stack
  local t="$W/t"; rm -rf "$t"; mkdir -p "$t/$(dirname "$F")"; git show "HEAD:$F" > "$t/$F"
  printf '  %-34s land:' "$1"; shift; local p r=""; for p in "$@"; do (cd "$t" && git apply "$W/$p.patch" 2>/dev/null) && printf ' %s ok' "$p" || printf ' %s FAIL' "$p"; r="$p $r"; done; echo
  printf '  %-34s revert latest-first:' ""; for p in $r; do (cd "$t" && git apply -R "$W/$p.patch" 2>/dev/null) && printf ' %s ok' "$p" || printf ' %s FAIL' "$p"; done; echo; }
# (a) every lane appends at the list end, each cut on HEAD (parallel lanes, own worktrees)
cut_patch L4s1 END '"spacing/UaPositionedHeadingMargin.kt",'
cut_patch L3s1 END '"layout/position/PositionedParentSlotGate.kt",'
cut_patch L5s4 END '"lists/ListMarkerSuffixGap.kt",'
land "(a) END, each cut on HEAD" L4s1 L3s1 L5s4
# (b) the plan's own rule: each re-cut on the tree it lands on (L4 s1 → L4 s3 → L3 s1 → L3 U2 → L5 s4, all at END)
git show "HEAD:$F" > "$W/chain"
for spec in 'c_L4s1|"spacing/UaPositionedHeadingMargin.kt",' 'c_L4s3|"typography/UAHeadingFoldGate.kt",' 'c_L3s1|"layout/position/PositionedParentSlotGate.kt",' \
            'c_L3U2|"layout/position/PositionedParentRtlOrigin.kt",' 'c_L5s4|"lists/ListMarkerSuffixGap.kt",'; do
  cut_patch "${spec%%|*}" END "${spec#*|}" "$W/chain"; cp "$W/after_${spec%%|*}" "$W/chain"; done
mkdir -p "$W/full/$(dirname "$F")"; cp "$W/chain" "$W/full/$F"
for p in c_L4s3 c_L4s1 c_L3U2; do printf '  (b) re-cut chain: git apply -R --check %-7s on the full stack: ' "$p"; (cd "$W/full" && git apply -R --check "$W/$p.patch" >/dev/null 2>&1) && echo ok || echo FAIL; done
printf '  (b) re-cut chain: 3-way revert of L4 seam 3 (git revert'"'"'s merge) on the full stack: '
git merge-file -p "$W/chain" "$W/after_c_L4s3" "$W/before_c_L4s3" > /dev/null 2>&1 && echo clean || echo CONFLICT
# (c) the fix: pinned anchors ≥ 3 lines apart, each cut on HEAD
cut_patch A_L4 '"content/PseudoGeneratedBox.kt",' '"spacing/UaPositionedHeadingMargin.kt",'
cut_patch A_L5 '"images/ImageCandidateChain.kt",' '"lists/ListMarkerSuffixGap.kt",'
cut_patch A_L3 '"transforms/TransformInheritance.kt",' '"layout/position/PositionedParentSlotGate.kt",'
land "(c) pinned anchors, §4 order" A_L4 A_L3 A_L5
land "(c) pinned anchors, reversed order" A_L5 A_L3 A_L4

hdr "E6 land-units.sh --check on a synthetic limit-killed lane (M3) — one throwaway worktree"
WT="$S/wt-status"; [[ -e "$WT" ]] && { echo "exists: $WT"; exit 2; }
git worktree add -q --detach "$WT" HEAD && echo "throwaway $(git -C "$WT" rev-parse --short HEAD)"
LD="$WT/tools/titan/results/wave55-android-paint-order"; mkdir -p "$LD"; K=runtimes/compose/src/main/java/com/styleconverter/runtime/borders
for u in U1 U2 U3; do printf 'diff --git a/%s b/%s\nnew file mode 100644\n--- /dev/null\n+++ b/%s\n@@ -0,0 +1 @@\n+// SYNTHETIC (process-holes r1) — never shipped\n' \
  "$K/SyntheticPH$u.kt" "$K/SyntheticPH$u.kt" "$K/SyntheticPH$u.kt" > "$LD/unit-$u.patch"; done
lu() { find "$LD" -exec touch -t 202610110000 {} +; bash $P/land-units.sh --check --only L6 --root "$WT" > "$S/lu.$1.txt" 2>&1; printf '  %-58s exit=%s  %s\n' "$2" "$?" "$(grep -E '^SUMMARY' "$S/lu.$1.txt" | cut -c1-60)"; grep -E 'ends with' "$S/lu.$1.txt" | sed 's/^/      /'; }
printf '# L6 (SYNTHETIC: a limit-killed attempt)\n\n## U1\n\nhalf-written…\n' > "$LD/_note.md"; lu nostatus "note with NO STATUS line, no TREES: line"
printf '# L6 (SYNTHETIC)\n\n## U1\n\nhalf-written…\n\nSTATUS: PARTIAL — U2 mutation not restored\n' > "$LD/_note.md"; lu partial "note ending STATUS: PARTIAL"
printf '# L6 (SYNTHETIC)\n\n## U1\npaths: %s/SyntheticPHU1.kt, …/SyntheticPHU1Test.kt\npatch: unit-U1.patch sha256 0000000000000000\n\nTREES: /nonexistent\nSTATUS: COMPLETE\n' "$K" > "$LD/_note.md"; lu notevspatch "note names 2 U1 paths + a sha; the patch carries 1, another sha"
git worktree remove --force "$WT" && echo "  (throwaway removed)"

hdr "E7 a D13 re-probe read with the stage-1 strings, run id substituted (S2)"
$PY - $P/expectations.json <<'PYX'
import json, re, sys
ro = json.load(open(sys.argv[1]))['stage1Probe']['readOut']
for k, v in ro.items():
    outs = re.findall(r'(?:--json|>)\s+(\S+)', v.replace('wave55-pre', 'wave55-pre-r1'))
    print(f'  {k:9} writes {outs or "stdout only — no record path"}')
PYX

hdr "E8 a stage-2 regeneration given only the new decision file drops the stage-1 decision (S3)"
mkdir -p "$S/rev-l2"
echo '[{"lane":"L2-cbb-android","unit":"U2","run":"wave55-probe","commit":"a","revertCommit":"b","rule":"4: fake"},{"lane":"L2-cbb-android","unit":"U1","run":"wave55-probe","commit":"c","revertCommit":"d","rule":"4: fake (pair)"}]' > "$S/rev-l2/reverted.json"
$PY $P/plan-build.py --out "$S/rev-l2" --reverted "$S/rev-l2/reverted.json" > "$S/rev-l2/out.txt" 2>&1; echo "plan-build exit=$?"
$PY - "$S/rev-l6u2/expectations.json" "$S/rev-l2/expectations.json" <<'PYX'
import json, sys
a, b = (json.load(open(p)) for p in sys.argv[1:3])
print('  stage-1 JSON probeDecisions:', [(r['lane'][:2], r['unit']) for r in a['probeDecisions']['reverted']])
print('  stage-2 JSON probeDecisions:', [(r['lane'][:2], r['unit']) for r in b['probeDecisions']['reverted']], '— L6 U2 gone; L6 [X] gating keys back:',
      sum(1 for pr in b['lanes']['L6-android-paint-order']['geometryProbes'] for k in pr.get('gating', {}) if '[X]' in k))
print('  the generator records its inputs:', [k for k in b if 'arg' in k.lower() or 'input' in k.lower()] or 'no')
g = [q for l in b['lanes'].values() for q in l['predictions'] if q.get('gating') and str(q.get('from', '')).startswith('f')]
print(f"E9 calibration literal after the L2 pair revert: gating f→P rows {len(g)}; adjudicateCalibrations[0].expect = {b['adjudicateCalibrations'][0]['expect']!r}")
PYX

hdr "E10 prose numbers outside the gen blocks survive a regeneration (S7)"
cp $P/PLAN.md "$S/PLAN.copy.md"
$PY $P/plan-prose.py --fill "$S/PLAN.copy.md" --exp "$S/rev-l6u2/expectations.json" --report "$S/rev-l6u2/out.txt" > /dev/null
$PY $P/plan-prose.py --check "$S/PLAN.copy.md" --exp "$S/rev-l6u2/expectations.json" --report "$S/rev-l6u2/out.txt" | tail -1; echo "check exit=${PIPESTATUS[0]}"
grep -n '^\*\*[0-9]* in full' "$S/PLAN.copy.md"; grep -n '^  - L6: [0-9]* —\|^- 28 revert units' "$S/PLAN.copy.md" | cut -c1-70

hdr "E11 installed-build hash reads named in PLAN.md (M2)"
grep -n 'installed-build-hash.sh' $P/PLAN.md | cut -c1-120
echo "  wave-54 record labels:"; grep -n '^== wave54-p' tools/titan/results/wave54-gate/build-hashes.txt | cut -c1-90

hdr "Z the shared tree is unchanged (HEAD + status outside the two plan dirs)"
[[ "$(snap)" == "$BEFORE" ]] && echo "  unchanged" || echo "  CHANGED"
