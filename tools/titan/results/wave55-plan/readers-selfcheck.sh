#!/usr/bin/env bash
# tools/titan/results/wave55-plan/readers-selfcheck.sh — proves the three wave-55 plan instruments can fail, and that their
# dry-run paths reproduce the installed outputs, in a THROWAWAY worktree (never on the shared tree).
#   A. plan-build.py: the dry run (--out) in the worktree reproduces expectations.json / watchlist.txt byte-for-byte;
#      snapshot-cells.mjs reproduces cells-wave54-final.json; six mutations each stop the build with their assertion;
#      the probe-decision / contingency / GO-SMALL hooks regenerate a consistent JSON.
#   B. geometry-gate.py: red on the pre-fix run; --sections turns a partial run's unprobed gating keys from UNMEASURED
#      into NOT PROBED (a copy with the filter disabled brings them back: the filter is load-bearing); an off-interpreter
#      cmd and a STALE JSON are refused (exit 2); a broken control turns the self-test red.
#   C. probe-readout.mjs: ORDER / NOT --movers 0 refusals; red on the identity record (rule 3) and on the wave-54 pair
#      (leak signals); a synthetic record exercises the may-move band, the ring-fence, the complement and the
#      down-preregistered bar; two mutated copies (bar disabled, may-move class disabled) turn the synthetic case red.
#   E. (plan-skeptic round 1) the probe-decision mechanism: an L6 U2 revert withdraws the may-move class and the drop-L6-U2 arm
#      (process-holes M4 / S8); control-check on that JSON reads a one-pixel nudge on a may-move capture as a LEAK, and a
#      copy of the generator WITHOUT the withdrawal reads it as may-move (the withdrawal is load-bearing); a regeneration fed
#      only a newer decision file refuses to drop the installed one (S3, append-only); probes-teeth.py (§T's engine) turns red
#      on a JSON whose L2 probe is the pre-round-1 wave-54 rule (instrument-teeth M9: the standing fakes can fail).
#   Round 2 (instrument-teeth S2 / S5): B1 reads the pre-fix run against wave54-open (--base = the run is refused outside
#      --self-test); B7 --base = the run, B8 a flag without its value, B9 an empty --lanes entry (`L1,`), B10 a --base with no
#      sections/ are each exit 2, and B11 a copy without the base check proceeds (the refusal is load-bearing); C10-C12
#      probe-readout's value-less --lanes / --exp and an empty --sections entry exit 2.
# Everything it writes goes under $S (the scratchpad) or $WT (the worktree); the shared tree gets only readers-selfcheck.txt.
# Usage: bash tools/titan/results/wave55-plan/readers-selfcheck.sh <worktree> <scratch-dir>
set -uo pipefail
SH="$(cd "$(dirname "$0")/../../../.." && pwd -P)"            # the shared tree (read-only here)
WT="$(cd "$1" && pwd -P)"; S="$(mkdir -p "$2" && cd "$2" && pwd -P)"
[ "$WT" != "$SH" ] || { echo "refusing: <worktree> is the shared tree"; exit 2; }
PY="/opt/homebrew/bin/python3.12 -B"; P=tools/titan/results/wave55-plan
OUT="$SH/$P/readers-selfcheck.txt"
exec > >(sed -u -E "s#$S#<scratch>#g; s#$WT#<worktree>#g; s#(/private)?/tmp/claude-[^ ]*#<scratch path>#g" | tee "$OUT") 2>&1   # scratch / worktree paths redacted (PLAN §8 step 18 greps for scratchpad paths)
echo "# readers-selfcheck.txt — $(date -u +%Y-%m-%dT%H:%M:%SZ); shared tree HEAD $(git -C "$SH" rev-parse --short HEAD); worktree HEAD $(git -C "$WT" rev-parse --short HEAD) (detached, throwaway)"
echo "# worktree links: tools/titan/runs -> $(readlink "$WT/tools/titan/runs"), tools/wpt -> $(readlink "$WT/tools/wpt")"
cp "$SH/$P"/{plan-build.py,snapshot-cells.mjs,geometry-gate.py,probe-readout.mjs,probes-selfcheck.py,probes-teeth.py,probes-teeth-r2.py,probes-teeth-r3.py,probes-teeth-r4.py} "$WT/$P/"
mkdir -p "$WT/$P/plan-skeptic-round-3.fakes"; cp "$SH/$P"/plan-skeptic-round-3.fakes/{added_ink.py,sweep.py} "$WT/$P/plan-skeptic-round-3.fakes/"   # round 3 (M2): §T3
mkdir -p "$WT/$P/plan-skeptic-round-4.fakes"; cp "$SH/$P"/plan-skeptic-round-4.fakes/{added_ink4.py,fakes4.py,zones4.py} "$WT/$P/plan-skeptic-round-4.fakes/"   # round 4 (M1-M4): §T / §T4
cp "$SH/$P"/*.geometry.py "$WT/$P/"                                  # the round-1 probes (and the wave-55 L2 copy)
mkdir -p "$WT/$P/probe-decisions"; cp "$SH/$P"/probe-decisions/*.json "$WT/$P/probe-decisions/"
# every input the generator hashes into generatedWith, so the dry run reproduces the installed JSON byte-for-byte
$PY - "$SH/$P/expectations.json" "$SH" "$WT" <<'EOF0'
import json, os, shutil, sys
e, sh, wt = json.load(open(sys.argv[1])), sys.argv[2], sys.argv[3]
for rel in e['generatedWith']['inputs']:
    os.makedirs(os.path.dirname(os.path.join(wt, rel)), exist_ok=True); shutil.copyfile(os.path.join(sh, rel), os.path.join(wt, rel))
EOF0
expect_fail() {  # <label> <expected text> <cmd…>: the command must exit non-zero AND print the expected text
  local label="$1" want="$2"; shift 2; local o; o="$("$@" 2>&1)"; local rc=$?
  if [ $rc -ne 0 ] && grep -qF -- "$want" <<<"$o"; then echo "  RED as required  $label  (exit $rc: $(grep -F -- "$want" <<<"$o" | head -1 | cut -c1-150))"
  else echo "  !! NOT RED       $label  (exit $rc; wanted \"$want\")"; FAILS=$((FAILS+1)); fi; }
FAILS=0

echo; echo "## A. plan-build.py in the worktree"
( cd "$WT" && nice -n 19 node "$P/snapshot-cells.mjs" wave54-final >/dev/null ) && cmp -s "$WT/$P/cells-wave54-final.json" "$SH/$P/cells-wave54-final.json" \
  && echo "  snapshot-cells.mjs in the worktree reproduces cells-wave54-final.json byte-for-byte" || { echo "  !! snapshot differs"; FAILS=$((FAILS+1)); }
( cd "$WT" && nice -n 19 $PY "$P/plan-build.py" --out "$S/wtdry" > "$S/wtdry.out" 2>&1 ); echo "  plan-build.py --out (dry run) exit $?"
for f in expectations.json watchlist.txt; do cmp -s "$S/wtdry/$f" "$SH/$P/$f" && echo "  $f: byte-identical to the installed one" || { echo "  !! $f differs"; FAILS=$((FAILS+1)); }; done
cp "$S/wtdry/expectations.json" "$S/wtdry/watchlist.txt" "$WT/$P/"      # installed in the worktree: the readers below use its defaults
mut() {  # <label> <python find> <python replace> <expected assertion text>
  local label="$1"; ( cd "$WT" && FIND="$2" REPL="$3" $PY - <<'EOF'
import os; s = open('tools/titan/results/wave55-plan/plan-build.py').read(); f, r = os.environ['FIND'], os.environ['REPL']
assert s.count(f) == 1, f'mutation anchor not unique: {f[:60]}'; open('tools/titan/results/wave55-plan/plan-build.mut.py', 'w').write(s.replace(f, r))
EOF
  ); expect_fail "$label" "$4" bash -c "cd '$WT' && nice -n 19 $PY $P/plan-build.mut.py --out '$S/wtmut'"; rm -f "$WT/$P/plan-build.mut.py"; }
mkdir -p "$S/wtmut"
mut "M1 a prediction's from disagrees with the run of record" "pred(f'{HC[1]} ios', 'P 0.9611'" "pred(f'{HC[1]} ios', 'P 0.9612'" 'brief says "P 0.9612"'
mut "M2 a probe cmd off the pinned interpreter (C14)" "LB = f'{PY} {P55}/hyphen-ios-linebox.geometry.py <run> --base <base>'" "LB = f'python3 {P55}/hyphen-ios-linebox.geometry.py <run> --base <base>'" 'not on the pinned interpreter'
mut "M3 a carrier slips into a must-not-move list" "        if o is None:
            out.append(cell(*l.rsplit(' ', 1)))" "        if o is None or o == lane_name:
            out.append(cell(*l.rsplit(' ', 1)))" 'does not partition'    # caught by the partition check, before the per-lane consistency check
mut "M4 an expect key without its section (C15)" "'hyphen-ios-linebox 002 ios': f'{HC[2]} ios', **{k: f'{t} ios' for k, t in lb_stack.items()}}," "**{k: f'{t} ios' for k, t in lb_stack.items()}}," 'keyCells'
mut "M5 a gating floor set from a replay magnitude" "'picture-correctness (P→P): in-run pitch 19, group firsts on the ref rows', 'MED', PASS, True, ['NB-ios'], 'LINEBOX OK'),
    pred(f'{HC[3]} ios'" "'picture-correctness (P→P): in-run pitch 19, group firsts on the ref rows', 'MED', 0.97, True, ['NB-ios'], 'LINEBOX OK'),
    pred(f'{HC[3]} ios'" 'a gating floor is the pass threshold'
mut "M6 the ring-fenced test leaks into the complement" "complement = sorted(k for k in CELLS if carrier_of(k) is None and k.rsplit(' ', 1)[0] != RING and k not in named)" "complement = sorted(k for k in CELLS if carrier_of(k) is None and k not in named)" 'does not partition'
echo "  hooks:"
printf '%s' '[{"lane": "L2-cbb-android", "unit": "U2", "run": "wave55-pre", "commit": "x", "revertCommit": "y", "rule": "4: synthetic"}, {"lane": "L2-cbb-android", "unit": "U1", "run": "wave55-pre", "commit": "x", "revertCommit": "y", "rule": "6: the pair"}]' > "$S/rev-l2.json"
( cd "$WT" && nice -n 19 $PY "$P/plan-build.py" --out "$S/wtrev" --reverted "$S/rev-l2.json" > "$S/wtrev.out" 2>&1 ); echo "    --reverted (L2 U2 + U1, the pair) exit $?: $(grep -c '^probe decision' "$S/wtrev.out") decisions; $(grep 'probe decision L2-cbb-android U1' "$S/wtrev.out" | cut -c1-140)"
o=$(cd "$WT" && nice -n 19 $PY "$P/geometry-gate.py" wave54-final --base wave54-final --self-test --exp "$S/wtrev/expectations.json" --lanes L2 2>&1); rc=$?
echo "    … geometry-gate.py on that regenerated JSON, --lanes L2: exit $rc (0 = no gating key left), $(grep -c 'WITHDRAWN' <<<"$o") WITHDRAWN key line(s); $(grep '^geometry-gate' <<<"$o" | cut -c1-120)"
[ $rc -eq 0 ] && [ "$(grep -c 'WITHDRAWN' <<<"$o")" -ge 8 ] || { echo "    !! the withdrawn keys did not behave"; FAILS=$((FAILS+1)); }
printf '%s' '[{"lane": "L1-hyphen-ios-linebox", "unit": "NB-ios-b", "run": "wave55-pre", "why": "synthetic: anchor offset 1 only"}]' > "$S/cont.json"
( cd "$WT" && nice -n 19 $PY "$P/plan-build.py" --out "$S/wtcont" --contingency "$S/cont.json" > "$S/wtcont.out" 2>&1 ); echo "    --contingency NB-ios-b exit $?: $(grep 'revert units' "$S/wtcont.out"); NB-ios revertOrder $($PY -c "import json;print(json.load(open('$S/wtcont/expectations.json'))['lanes']['L1-hyphen-ios-linebox']['revertUnits']['NB-ios']['revertOrder'])")"
( cd "$WT" && nice -n 19 $PY "$P/plan-build.py" --out "$S/wtsmall" --go-small > "$S/wtsmall.out" 2>&1 ); echo "    --go-small exit $?: $(grep 'L4-ua' "$S/wtsmall.out" | cut -c1-150)"

echo; echo "## B. geometry-gate.py"
GG="$P/geometry-gate.py"
( cd "$WT" && nice -n 19 $PY $GG wave54-final --base wave54-open --json "$S/g1.json" > "$S/g1.txt" 2>&1 ); rc=$?
echo "  B1 the pre-fix run (base wave54-open), no --self-test: exit $rc (1 required) — $(grep '^geometry-gate' "$S/g1.txt")"; [ $rc -eq 1 ] || FAILS=$((FAILS+1))
SEC=CSS2,css-counter-styles,css-tables,css-text
( cd "$WT" && nice -n 19 $PY $GG wave54-pre --base wave54-open --json "$S/g2a.json" > "$S/g2a.txt" 2>&1 )
( cd "$WT" && nice -n 19 $PY $GG wave54-pre --base wave54-open --sections $SEC --json "$S/g2b.json" > "$S/g2b.txt" 2>&1 )
u_a=$($PY -c "import json;print(json.load(open('$S/g2a.json'))['gatingUnmeasured'])"); u_b=$($PY -c "import json;d=json.load(open('$S/g2b.json'));print(d['gatingUnmeasured'], d['notProbed'])")
echo "  B2 wave54-pre (4 sections) without --sections: gating UNMEASURED $u_a; with --sections $SEC: gating UNMEASURED / NOT PROBED = $u_b"
[ "${u_a:-0}" -gt 0 ] && [ "${u_b%% *}" = 0 ] && [ "${u_b##* }" -gt 0 ] && echo "     as required: the filter turns every unprobed gating key from UNMEASURED into NOT PROBED" || { echo "     !! B2 not as required"; FAILS=$((FAILS+1)); }
sed 's/^        in_scope = \[k for k in keys if SECTIONS is None or section_of_key(probe, k) in SECTIONS\]/        in_scope = keys   # MUTATION: the section filter disabled/' "$WT/$GG" > "$WT/$P/geometry-gate.mut.py"
( cd "$WT" && nice -n 19 $PY $P/geometry-gate.mut.py wave54-pre --base wave54-open --sections $SEC --json "$S/g2c.json" > "$S/g2c.txt" 2>&1 )
u_c=$($PY -c "import json;print(json.load(open('$S/g2c.json'))['gatingUnmeasured'])"); rm -f "$WT/$P/geometry-gate.mut.py"
echo "  B3 MUTATION (filter disabled) with --sections: gating UNMEASURED $u_c — $( [ "$u_c" = "$u_a" ] && [ "$u_c" != "0" ] && echo 'RED as required: the filter is load-bearing' || { echo '!! not red'; FAILS=$((FAILS+1)); })"
$PY - "$SH/$P/expectations.json" "$S/exp-offinterp.json" "$S/exp-stale.json" "$S/exp-badctl.json" <<'EOF'
import json, sys
src, off, stale, bad = sys.argv[1:]
e = json.load(open(src)); e['lanes']['L7-instruments']['geometryProbes'][0]['cmd'] = 'python3 tools/titan/results/wave55-plan/flex-zero-gap-rules.geometry.py <run>'; json.dump(e, open(off, 'w'))
e = json.load(open(src)); e['probeDecisions'] = {'reverted': [{'lane': 'L6-android-paint-order', 'unit': 'U1', 'run': 'wave55-pre', 'rule': 'synthetic, JSON NOT regenerated'}]}; json.dump(e, open(stale, 'w'))
e = json.load(open(src)); e['lanes']['L7-instruments']['geometryProbes'][0]['expect']['flex/flex-gap-decorations-033 ios'] = 'GEOMETRY WRONG (synthetic)'; json.dump(e, open(bad, 'w'))
EOF
expect_fail "B4 an off-interpreter cmd is refused (C14)" "probe cmds off the pinned interpreter" bash -c "cd '$WT' && $PY $GG wave54-final --base wave54-open --exp '$S/exp-offinterp.json' --lanes L7"
expect_fail "B5 a STALE expectations.json is refused" "STALE expectations.json" bash -c "cd '$WT' && $PY $GG wave54-final --base wave54-open --exp '$S/exp-stale.json' --lanes L6"
expect_fail "B6 a control that no longer holds turns the self-test red" "self-test: FAILS" bash -c "cd '$WT' && nice -n 19 $PY $GG wave54-final --base wave54-final --exp '$S/exp-badctl.json' --lanes L7 --self-test"
want2() {  # <label> <expected text> <cmd…>: the command must exit EXACTLY 2 and print the expected text (a usage refusal, never exit 1)
  local label="$1" want="$2"; shift 2; local o; o="$("$@" 2>&1)"; local rc=$?
  if [ $rc -eq 2 ] && grep -qF -- "$want" <<<"$o"; then echo "  as required     $label  (exit 2: $(grep -F -- "$want" <<<"$o" | head -1 | cut -c1-140))"
  else echo "  !! NOT as required  $label  (exit $rc; wanted 2 and \"$want\")"; FAILS=$((FAILS+1)); fi; }
want2 "B7 --base equal to the run, without --self-test, is refused (round 2, S2)" "is the run itself" bash -c "cd '$WT' && $PY $GG wave54-final --base wave54-final --lanes L1"
want2 "B8 a flag given without its value (--lanes) is a usage error, not an IndexError (S5)" "given without a value" bash -c "cd '$WT' && $PY $GG wave54-final --base wave54-open --lanes"
want2 "B9 an EMPTY --lanes entry ('L1,') is refused, never widened to every lane (S5)" "REFUSED — unknown scope" bash -c "cd '$WT' && $PY $GG wave54-final --base wave54-open --lanes L1,"
want2 "B10 a --base run with no sections/ is refused (S2)" "has no sections/ directory" bash -c "cd '$WT' && $PY $GG wave54-final --base wave55-nope --lanes L1"
sed 's/^if base == run and not self_test:$/if False:   # MUTATION: the base check removed/' "$WT/$GG" > "$WT/$P/geometry-gate.mut.py"
o=$(cd "$WT" && nice -n 19 $PY $P/geometry-gate.mut.py wave54-final --base wave54-final --lanes L1 2>&1); rc=$?; rm -f "$WT/$P/geometry-gate.mut.py"
if [ $rc -ne 2 ] && ! grep -q 'is the run itself' <<<"$o"; then echo "  as required     B11 MUTATION (the base check removed): --base = the run is read (exit $rc) — the refusal is load-bearing"
else echo "  !! NOT as required  B11 (exit $rc)"; FAILS=$((FAILS+1)); fi

echo; echo "## C. probe-readout.mjs"
PR="$P/probe-readout.mjs"; E="--exp $SH/$P/expectations.json"
( cd "$WT" && nice -n 19 node tools/titan/score-gate.mjs wave54-final wave54-final --movers 0 --json "$S/id.json" >/dev/null 2>&1 )
( cd "$WT" && nice -n 19 node tools/titan/score-gate.mjs wave54-open wave54-final --movers 0 --json "$S/w54.json" >/dev/null 2>&1 )
( cd "$WT" && nice -n 19 node tools/titan/score-gate.mjs wave54-open wave54-final --movers 0.005 --json "$S/w54t.json" >/dev/null 2>&1 )
( cd "$WT" && nice -n 19 node tools/titan/score-gate.mjs wave54-open wave54-pre --movers 0 --json "$S/pre.json" >/dev/null 2>&1 )
expect_fail "C1 a record whose prev is not the base" "ORDER" bash -c "cd '$WT' && node $PR '$S/id.json' $E"
expect_fail "C2 a thresholded (--movers 0.005) record" "NOT A --movers 0 RECORD" bash -c "cd '$WT' && node $PR '$S/w54t.json' --base wave54-open $E"
expect_fail "C3 the identity record: the 9 gating flip rows fire rule 3" "rule 3 fired 9" bash -c "cd '$WT' && node $PR '$S/id.json' --base wave54-final $E"
expect_fail "C4 the wave-54 pair: wave 54's landed changes are leak signals here" "leak signals 62 ·" bash -c "cd '$WT' && node $PR '$S/w54.json' --base wave54-open $E"
want2 "C10 probe-readout --lanes without a value is a usage error (round 2, S5; was a TypeError, exit 1)" "given without a value" bash -c "cd '$WT' && node $PR '$S/id.json' --base wave54-final --lanes"
want2 "C11 probe-readout --exp without a value is a usage error" "given without a value" bash -c "cd '$WT' && node $PR '$S/id.json' --exp"
want2 "C12 probe-readout an empty --sections entry ('css-text,') is refused" "an empty --sections entry" bash -c "cd '$WT' && node $PR '$S/id.json' --base wave54-final --sections css-text, $E"
o=$(cd "$WT" && node $PR "$S/pre.json" --base wave54-open --stage1 $E 2>&1); m1=$(grep -o 'MISSING listed sections ([0-9]*)' <<<"$o")
o=$(cd "$WT" && node $PR "$S/pre.json" --base wave54-open --stage1 --sections css-counter-styles,css-text $E 2>&1); m2=$(grep -c 'MISSING' <<<"$o")
if [ "$m1" = "MISSING listed sections (7)" ] && [ "$m2" = 0 ]; then echo "  as required     C5 a 4-section record read --stage1 lists the 7 absent stage-1 sections MISSING; with --sections css-counter-styles,css-text none ($(grep '^summary' <<<"$o" | cut -c1-90))"
else echo "  !! NOT as required  C5 ($m1 / $m2)"; FAILS=$((FAILS+1)); fi
$PY - "$S/id.json" "$SH/$P/expectations.json" "$S/syn-ok.json" "$S/syn-red.json" <<'EOF'
import json, sys
src, exp, ok_path, red_path = sys.argv[1:]
e = json.load(open(exp)); may = e['unionMayMoveCaptures']['android']
cell = lambda st: '/'.join(st.split('__')[1:]) + '.html'
def build(moves):
    d = json.load(open(src)); d['prev'] = d['cur'] = 'wave55-open'; d['prev'] = 'wave55-open'; d['cur'] = 'synthetic'
    for c in d['movers']:
        k = (c['test'].replace('css/', '', 1), c['platform'])
        if k in moves:
            c['cur'] = round(c['prev'] + moves[k], 4); c['delta'] = moves[k]
    return d
scored = {(c['test'].replace('css/', '', 1), c['platform']) for c in json.load(open(src))['movers']}
cand = [cell(st) for st in may if (cell(st), 'android') in scored and not cell(st).startswith(('css-color/', 'css-pseudo/', 'css-sizing/', 'css-ui/'))]
m1, m2 = cand[0], cand[1]
CSA = ('css-counter-styles/cjk-decimal/css3-counter-styles-005.html', 'android')
okm = {(m1, 'android'): 0.001, ('filter-effects/backdrop-filter-basic-blur.html', 'android'): -0.01, CSA: -0.003}   # −0.003: inside the D5 band (bar −0.0038), outside the plain −0.002 one
redm = {(m2, 'android'): 0.003, CSA: -0.0039, (e['mustNotMoveComplement']['cells'][0].rsplit(' ', 1)[0], e['mustNotMoveComplement']['cells'][0].rsplit(' ', 1)[1]): 0.0001}
json.dump(build(okm), open(ok_path, 'w')); json.dump(build(redm), open(red_path, 'w'))
print(f'  synthetic: may-move +0.001 on {m1} android, ring-fenced basic-blur android −0.01, -005 android −0.003 (ok: within its pre-registered band); may-move +0.003 on {m2}, -005 android −0.0039, complement {e["mustNotMoveComplement"]["cells"][0]} +0.0001 (red)')
EOF
check() {  # <label> <condition 0|1> <detail>
  if [ "$2" = 1 ]; then echo "  as required     $1 — $3"; else echo "  !! NOT as required  $1 — $3"; FAILS=$((FAILS+1)); fi; }
o=$(cd "$WT" && node $PR "$S/syn-ok.json" $E --lanes L5,L6 2>&1); rc=$?
mw=$(grep -c '^  may-move ' <<<"$o"); rg=$(grep -c 'RING-FENCED' <<<"$o"); r2=$(grep -c 'FIRES.*css3-counter-styles-005.html android' <<<"$o")
check "C6 synthetic OK case (--lanes L5,L6): exit 0, a may-move move within the band, the ring-fence printed, no rule 2 on -005 android at −0.003" \
  $([ $rc -eq 0 ] && [ $mw -ge 1 ] && [ $rg -ge 1 ] && [ $r2 -eq 0 ] && echo 1 || echo 0) "exit $rc, may-move within band $mw, ring-fenced $rg, -005 fires $r2"
o=$(cd "$WT" && node $PR "$S/syn-red.json" $E --lanes L5,L6 2>&1); rc=$?
mm=$(grep -c '^  MAY-MOVE' <<<"$o"); r2=$(grep -c 'FIRES.*css3-counter-styles-005.html android.*rule 2' <<<"$o"); lk=$(grep -c 'must-not-move of complement' <<<"$o")
check "C7 synthetic RED case: exit 1, may-move beyond the band named, rule 2 at −0.0039, a complement move of +0.0001 is a leak" \
  $([ $rc -eq 1 ] && [ $mm -ge 1 ] && [ $r2 -ge 1 ] && [ $lk -ge 1 ] && echo 1 || echo 0) "exit $rc, MAY-MOVE $mm, rule 2 $r2, complement LEAK $lk"
sed "s/const bar = p.direction === 'down-preregistered' ? +(p.predictedDelta - 0.002).toFixed(4) : -0.002;/const bar = -0.002;   \/\/ MUTATION: the pre-registered fall ignored/" "$WT/$PR" > "$WT/$P/probe-readout.mut1.mjs"
o=$(cd "$WT" && node $P/probe-readout.mut1.mjs "$S/syn-ok.json" $E --lanes L5,L6 2>&1); rc=$?; r2=$(grep -c 'FIRES.*css3-counter-styles-005.html android.*rule 2' <<<"$o")
check "C8 MUTATION (D5 bar ignored) turns the OK case red" $([ $rc -eq 1 ] && [ $r2 -ge 1 ] && echo 1 || echo 0) "exit $rc, rule 2 on -005 android $r2"
sed "s/Object.entries(EXP.unionMayMoveCaptures || {})/Object.entries({})/" "$WT/$PR" > "$WT/$P/probe-readout.mut2.mjs"   # MUTATION: no may-move class
o=$(cd "$WT" && node $P/probe-readout.mut2.mjs "$S/syn-ok.json" $E --lanes L5,L6 2>&1); rc=$?; lk=$(grep -c '^  LEAK' <<<"$o")
check "C9 MUTATION (may-move class removed) turns the OK case red" $([ $rc -eq 1 ] && [ $lk -ge 1 ] && echo 1 || echo 0) "exit $rc, LEAK lines $lk"
rm -f "$WT/$P/probe-readout.mut1.mjs" "$WT/$P/probe-readout.mut2.mjs"
echo; echo "## D. probes-selfcheck.py can fail: a control that can never fail promoted to GATING"
$PY - "$SH/$P/expectations.json" "$S/exp-cantfail.json" <<'EOF'
import json, sys
e = json.load(open(sys.argv[1])); pr = e['lanes']['L7-instruments']['geometryProbes'][0]
pr['gating']['flex/flex-gap-decorations-033 ios'] = 'U1'; json.dump(e, open(sys.argv[2], 'w'))
EOF
expect_fail "D1 a gating key with no failing picture (today OK, no fake) is reported CANNOT FAIL" "CANNOT FAIL: flex-zero-gap-rules.geometry.py flex/flex-gap-decorations-033 ios" \
  bash -c "cd '$SH' && nice -n 19 $PY $P/probes-selfcheck.py --scratch '$S/pscf' --wt '$WT' --exp '$S/exp-cantfail.json' --out '$S/pscf.txt' --teeth-lanes L7 && cat '$S/pscf.txt' || { cat '$S/pscf.txt'; exit 1; }"
# (round 3: --teeth-lanes L7 narrows §T / §T3 to the mutated key's lane — the CANNOT FAIL read is per gating key and does not
#  depend on the other lanes' fakes, and the full §T3 added-ink sweep alone takes ~25 min; probes-selfcheck.txt is the full run)
echo; echo "## E. round 1: the probe-decision mechanism (M4 / S8 / S3) and the standing fakes (M9) can fail"
printf '%s' '[{"lane": "L6-android-paint-order", "unit": "U2", "run": "wave55-pre", "commit": "x", "revertCommit": "y", "rule": "4: synthetic [X] FAIL"}]' > "$S/rev-l6.json"
( cd "$WT" && nice -n 19 $PY "$P/plan-build.py" --out "$S/wtl6" --reverted "$S/rev-l6.json" > "$S/wtl6.out" 2>&1 ); rc=$?
read -r umm lmw arms awd <<<"$($PY -c "import json;e=json.load(open('$S/wtl6/expectations.json'));l=e['lanes']['L6-android-paint-order']
print(len(e['unionMayMoveCaptures']['android']), len(l.get('mayMoveWithdrawn',{}).get('android',[])), ','.join(a['arm'] for a in e['abArms']), ','.join(a['arm'] for a in e.get('abArmsWithdrawn',[])))")"
check "E1 regenerated with L6 U2 reverted: unionMayMoveCaptures.android empty, the class recorded as withdrawn, drop-L6-U2 withdrawn" \
  $([ $rc -eq 0 ] && [ "$umm" = 0 ] && [ "${lmw:-0}" -gt 200 ] && [ "$awd" = drop-L6-U2 ] && echo 1 || echo 0) "exit $rc, union may-move $umm, withdrawn $lmw, arms $arms, abArmsWithdrawn $awd"
# a synthetic post run: one may-move android capture of CSS2 nudged by one pixel (control-check reads the WT's installed JSON)
SYN6="$S/cc6"; rm -rf "$SYN6"; mkdir -p "$SYN6/sections"; cp -R "$SH/tools/titan/runs/wave54-final/sections/CSS2" "$SYN6/sections/"
MM=$($PY - "$SH/$P/expectations.json" "$SYN6" <<'EOF6'
import json, os, sys
from PIL import Image
e, syn = json.load(open(sys.argv[1])), sys.argv[2]
st = sorted(s for s in e['unionMayMoveCaptures']['android'] if s.startswith('wpt__CSS2__'))[0]
f = os.path.join(syn, 'sections', 'CSS2', 'android-screenshots', st + '.png'); im = Image.open(f).convert('RGBA')
r, g, b, a = im.getpixel((0, 0)); im.putpixel((0, 0), (r ^ 8, g, b, a)); im.save(f); print(st)
EOF6
)
[ -e "$WT/node_modules" ] || ln -s "$SH/node_modules" "$WT/node_modules"
mkdir -p "$WT/tools/titan/results/wave55-gate"; cp "$SH/tools/titan/results/wave55-gate/control-check.mjs" "$WT/tools/titan/results/wave55-gate/"
cp "$S/wtl6/expectations.json" "$WT/$P/expectations.json"
o=$(cd "$WT" && nice -n 19 node tools/titan/results/wave55-gate/control-check.mjs wave54-final "$SYN6" --sections CSS2 2>&1); rc=$?
check "E2 with the withdrawal installed, the nudged may-move capture ($MM) is a LEAK" $([ $rc -eq 1 ] && grep -q "LEAK  $MM" <<<"$o" && echo 1 || echo 0) "exit $rc"
( cd "$WT" && FIND="        if r['lane'] == 'L6-android-paint-order' and r['unit'] in ('U2', 'U1') and lane.get('mayMove', {}).get('android'):" \
  REPL="        if False:   # MUTATION: no may-move withdrawal" $PY - <<'EOF7'
import os; s = open('tools/titan/results/wave55-plan/plan-build.py').read(); f, r = os.environ['FIND'], os.environ['REPL']
assert s.count(f) == 1; open('tools/titan/results/wave55-plan/plan-build.mut.py', 'w').write(s.replace(f, r))
EOF7
)
( cd "$WT" && nice -n 19 $PY "$P/plan-build.mut.py" --out "$S/wtl6m" --reverted "$S/rev-l6.json" > "$S/wtl6m.out" 2>&1 ); rm -f "$WT/$P/plan-build.mut.py"
cp "$S/wtl6m/expectations.json" "$WT/$P/expectations.json"
o=$(cd "$WT" && nice -n 19 node tools/titan/results/wave55-gate/control-check.mjs wave54-final "$SYN6" --sections CSS2 2>&1); rc=$?
check "E3 MUTATION (the withdrawal disabled in the generator): the same nudge reads may-move and HOLDS" $([ $rc -eq 0 ] && grep -q "may-move  $MM" <<<"$o" && echo 1 || echo 0) "exit $rc"
cp "$S/wtl6/expectations.json" "$WT/$P/expectations.json"        # the L6 decision is now the INSTALLED record in the worktree
printf '%s' '[{"lane": "L2-cbb-android", "unit": "U2", "run": "wave55-probe", "commit": "a", "revertCommit": "b", "rule": "4: synthetic"}]' > "$S/rev-l2only.json"
expect_fail "E4 a regeneration fed only a newer decision file refuses to drop the installed L6 U2 row (append-only, S3)" "decision row dropped: L6-android-paint-order U2 at wave55-pre" \
  bash -c "cd '$WT' && nice -n 19 $PY $P/plan-build.py --out '$S/wtl2only' --reverted '$S/rev-l2only.json'"
cp "$S/wtdry/expectations.json" "$WT/$P/expectations.json"       # the worktree's installed JSON back to the planning one
$PY - "$SH/$P/expectations.json" "$S/exp-teeth-w54.json" <<'EOF8'
import json, sys
e = json.load(open(sys.argv[1])); pr = e['lanes']['L2-cbb-android']['geometryProbes'][0]
pr['cmd'] = pr['cmd'].replace('wave55-plan/compose-wpt-content-box-cb', 'wave54-plan/compose-wpt-content-box-cb'); json.dump(e, open(sys.argv[2], 'w'))
EOF8
o=$(cd "$SH" && nice -n 19 $PY $P/probes-teeth.py --scratch "$S/teeth-w54" --exp "$S/exp-teeth-w54.json" --lanes L2 2>&1); rc=$?
check "E5 probes-teeth.py on a JSON whose L2 probe is the pre-round-1 wave-54 rule: red (hollow / corners2 / stripe30 accepted)" \
  $([ $rc -eq 1 ] && grep -q 'VIOLATION L2 U1-hollow' <<<"$o" && echo 1 || echo 0) "exit $rc, $(grep -c '^  VIOLATION' <<<"$o") violation(s)"

echo; echo "readers-selfcheck: $([ $FAILS -eq 0 ] && echo 'every check behaved as required' || echo "$FAILS check(s) did NOT behave as required")"
exit $FAILS
