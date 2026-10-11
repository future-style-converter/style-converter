#!/usr/bin/env bash
# tools/titan/results/wave55-plan/plan-prose.selfcheck.sh — proves plan-prose.py --check can fail (the standing rule "a new
# check must be proven able to fail before it is trusted"). Every mutation is applied to a SCRATCH COPY of PLAN.md /
# expectations.json / cells-wave54-final.json (never to the files beside this script), --check must exit 1 naming the
# violation, and the installed files must be byte-identical before and after (sha256 printed). M11 is the realistic case:
# a regeneration (plan-build.py --go-small --out <scratch>) that PLAN.md has not been re-filled for.
#
# Usage: SCRATCH=<an empty dir> bash tools/titan/results/wave55-plan/plan-prose.selfcheck.sh > tools/titan/results/wave55-plan/plan-prose.out.txt
#   (SCRATCH defaults to a fresh mktemp -d; nothing is written anywhere else)
set -uo pipefail
HERE="$(cd "$(dirname "$0")" && pwd)"
PY=/opt/homebrew/bin/python3.12
S="${SCRATCH:-$(mktemp -d)}"; mkdir -p "$S"
PP="$PY -B $HERE/plan-prose.py"
sha() { shasum -a 256 "$1" | cut -c1-16; }
echo "# plan-prose.selfcheck — $(date -u +%Y-%m-%dT%H:%M:%SZ); HEAD $(git -C "$HERE" rev-parse --short HEAD); scratch copies only"
echo "installed before: PLAN.md $(sha "$HERE/PLAN.md") · expectations.json $(sha "$HERE/expectations.json") · cells-wave54-final.json $(sha "$HERE/cells-wave54-final.json") · landing-manifest.json $(sha "$HERE/landing-manifest.json")"
echo
echo "## G0 the installed PLAN.md (must HOLD)"
$PP --check "$HERE/PLAN.md"; echo "exit $?"
fails=0
red() {  # <label> <expected substring> <cmd…>: the check must exit 1 and print the substring
  local label="$1" want="$2"; shift 2
  local out rc; out="$("$@" 2>&1)"; rc=$?
  if [[ $rc -eq 1 && "$out" == *"$want"* ]]; then echo "RED as required  $label  (exit 1: $(grep -m1 -F "$want" <<<"$out" | sed 's/^ *//' | cut -c1-170))"
  else echo "NOT RED  $label  (exit $rc; wanted 1 with: $want)"; fails=$((fails+1)); fi
}
mut() {  # <name> <python edit of the text in $S/<name>/PLAN.md>
  mkdir -p "$S/$1"; cp "$HERE/PLAN.md" "$S/$1/PLAN.md"
  $PY -B -c "import sys; p=sys.argv[1]; t=open(p).read(); $2; open(p,'w').write(t)" "$S/$1/PLAN.md"
}
echo; echo "## mutations of PLAN.md (copies)"
mut M2 "t=t.replace('\n| C7 |', '\n| C7x |', 1)"
red 'M2  a planDecisions id (C7) missing from §9' 'no hand-written row for planDecisions C7' $PP --check "$S/M2/PLAN.md"
mut M3 "t=t.replace('hyphenate-character-004 ios f 0.9477', 'hyphenate-character-004 ios f 0.9478', 1)"
red 'M3  a cited wave54-final cell with a wrong score' 'disagrees with cells-wave54-final.json' $PP --check "$S/M3/PLAN.md"
mut M3b "t=t.replace('selectors/dir-style-02a android f 0.9425', 'selectors/dir-style-02b android f 0.9425', 1)"
red 'M3b a cited cell that is not scored' 'not scored at wave54-final' $PP --check "$S/M3b/PLAN.md"
landmut() {  # <name> <python edit of the manifest dict m> — a mutated COPY of landing-manifest.json, filled into a PLAN copy,
             # so check 1 (block == rendering) holds and only the independent order / unit checks can fire
  mkdir -p "$S/$1"; cp "$HERE/PLAN.md" "$S/$1/PLAN.md"
  $PY -B -c "import json,sys; m=json.load(open(sys.argv[1])); U={(u['lane'],u['unit']):u for u in m['units']}; $2; json.dump(m,open(sys.argv[2],'w'),indent=1,ensure_ascii=False)" \
    "$HERE/landing-manifest.json" "$S/$1/landing-manifest.json"
  $PP --fill --landing "$S/$1/landing-manifest.json" "$S/$1/PLAN.md" > /dev/null
}
swap() { echo "a=U[('$1','$2')]; b=U[('$3','$4')]; a['seq'],b['seq']=b['seq'],a['seq']"; }
landmut M4 "$(swap L4 U1-android L3 U1)"
red 'M4  L3 U1 landed before L4 U1-android (C10)' 'C10' $PP --check --landing "$S/M4/landing-manifest.json" "$S/M4/PLAN.md"
landmut M5 "m['units']=[u for u in m['units'] if (u['lane'],u['unit'])!=('L5','D')]"
red 'M5  a revert unit (L5 D) missing from the landing order' 'L5-counter-suffix-rows D 0 times' $PP --check --landing "$S/M5/landing-manifest.json" "$S/M5/PLAN.md"
landmut M6 "$(swap L2 U1 L2 U2)"
red 'M6  L2 U2 landed before U1 (revertOrder)' 'revertOrder of U1' $PP --check --landing "$S/M6/landing-manifest.json" "$S/M6/PLAN.md"
landmut M8 "$(swap L1 NB-ios L5 U3-ios)"
red 'M8  L5 U3-ios before L1 NB-ios (seam 6 re-anchor)' 're-anchored by content' $PP --check --landing "$S/M8/landing-manifest.json" "$S/M8/PLAN.md"
landmut M9 "$(swap L5 U2o-android L5 D)"
red 'M9  L5 D before U2o-android (C7)' 'C7' $PP --check --landing "$S/M9/landing-manifest.json" "$S/M9/PLAN.md"
landmut M14 "U[('L6','U1')]['revertOrder']=['U1']"
red 'M14 the manifest and expectations.json disagree on a revertOrder (check 6)' 'revertOrder' $PP --check --landing "$S/M14/landing-manifest.json" "$S/M14/PLAN.md"
landmut M15 "m['units'].append(dict(U[('L6','U3')], unit='U9', seq=99))"
red 'M15 a manifest unit that expectations.json does not know (check 6)' 'neither a revert unit nor a contingency unit' $PP --check --landing "$S/M15/landing-manifest.json" "$S/M15/PLAN.md"
mut M7 "i=t.index('<!-- gen:L1-predictions'); j=t.index('P ≈0.978', i); t=t[:j]+'P ≈0.988'+t[j+len('P ≈0.978'):]"
red 'M7  a hand edit inside a generated block' 'block gen:L1-predictions differs' $PP --check "$S/M7/PLAN.md"
mut M12 "i=t.index('<!-- gen:union'); j=t.index('<!-- /gen:union -->'); t=t[:i]+t[j+len('<!-- /gen:union -->'):]"
red 'M12 a generated block deleted' 'block gen:union present 0 times' $PP --check "$S/M12/PLAN.md"
mut M13 "t=t.replace('### L5 · counter-suffix-rows', '### L5 · counter-suffix', 1)"
red 'M13 a lane heading renamed' 'no "### L5 · counter-suffix-rows" heading' $PP --check "$S/M13/PLAN.md"
mut M16 "t=t.replace('- **Paint order only (D23', '- L6: 7 — clip-rect [PO] and the six [X] rows (a hand-typed count, round-1 E10)\\n- **Paint order only (D23', 1)"
red 'M16 a hand-typed regenerable count outside the gen blocks (the E10 shape, round 1 S7)' '7. a regenerable count typed by hand' $PP --check "$S/M16/PLAN.md"
mut M17 "t=t.replace('**Order of operations.**', '- 28 revert units (+1 contingency), each one commit.\\n\\n**Order of operations.**', 1)"
red 'M17 a hand-typed unit count outside the gen blocks (round 1 S7)' '7. a regenerable count typed by hand' $PP --check "$S/M17/PLAN.md"
echo; echo "## mutations of the generated inputs (copies), against the installed PLAN.md"
mkdir -p "$S/M1"; $PY -B -c "
import json,sys; e=json.load(open(sys.argv[1])); p=e['lanes']['L2-cbb-android']['predictions'][0]; p['to']=p['to'].replace('0.999','0.998',1)
json.dump(e,open(sys.argv[2],'w'),indent=1,ensure_ascii=False)" "$HERE/expectations.json" "$S/M1/expectations.json"
red 'M1  a prediction regenerated (L2 clip-3 to), PLAN.md not re-filled' 'block gen:L2-predictions differs' $PP --check --exp "$S/M1/expectations.json" "$HERE/PLAN.md"
mkdir -p "$S/M10"; $PY -B -c "
import json,sys; c=json.load(open(sys.argv[1])); c['cells']['css-text/hyphens/hyphenate-character-001.html ios']='P 0.9612'
json.dump(c,open(sys.argv[2],'w'))" "$HERE/cells-wave54-final.json" "$S/M10/cells.json"
red 'M10 the run of record disagrees with a cited cell' 'hyphenate-character-001 ios P 0.9611' $PP --check --cells "$S/M10/cells.json" "$HERE/PLAN.md"
echo; echo "## M11 a real regeneration that PLAN.md has not followed: plan-build.py --go-small --out <scratch>"
mkdir -p "$S/M11"; $PY -B "$HERE/plan-build.py" --go-small --out "$S/M11" > "$S/M11/plan-build.out.txt" 2>&1; echo "plan-build --go-small exit $?"
red 'M11 the GO-SMALL JSON (L4 U2-android held) against the GO plan' 'not a revert unit of expectations.json' \
  $PP --check --exp "$S/M11/expectations.json" --report "$S/M11/plan-build.out.txt" "$HERE/PLAN.md"
echo; echo "## G1 restore: the installed files are untouched and still HOLD"
echo "installed after:  PLAN.md $(sha "$HERE/PLAN.md") · expectations.json $(sha "$HERE/expectations.json") · cells-wave54-final.json $(sha "$HERE/cells-wave54-final.json") · landing-manifest.json $(sha "$HERE/landing-manifest.json")"
$PP --check "$HERE/PLAN.md"; echo "exit $?"
echo; echo "plan-prose.selfcheck: $([[ $fails -eq 0 ]] && echo 'every mutation turned the check red' || echo "$fails mutation(s) NOT caught")"
exit $fails
