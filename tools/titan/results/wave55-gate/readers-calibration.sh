#!/usr/bin/env bash
# tools/titan/results/wave55-gate/readers-calibration.sh — calibrates the wave-55 copies of control-check.mjs and adjudicate.mjs
# (the two readers whose rules the wave-55 expectations extend: the may-move / ring-fence capture classes and the R5 complement)
# before they are trusted, and proves each new branch is load-bearing with a mutated copy run in the THROWAWAY worktree.
#   K. control-check.mjs: the identity pair HOLDS (union and --lane L7); the wave-54 pair FAILS with leaks; a synthetic post run
#      (two sections copied from wave54-final under the scratch dir, three PNGs nudged by one pixel) reads a may-move android
#      stem as "may-move", basic-blur android as "RING-FENCED" and a complement stem as a LEAK; without the leak it HOLDS; a copy
#      with the may-move class removed FAILS it. Round 1 (instrument-teeth S1): K7-K12 — a mistyped or absent --sections, a
#      section present in one run only, a capture or a per-test IR document that DISAPPEARS (LEAK / WIRE LEAK), and a copy
#      without the pre-side read that HOLDS again on the vanished capture (the new branch is load-bearing).
#   J. adjudicate.mjs: the identity record (relabelled wave55-open → wave55-final) misses the 9 gating flips (R4); the all-met
#      synthetic holds R1-R5; one complement cell +0.003 FAILS R5 as "complement"; a copy without the complement read does not
#      see it; a thresholded record is refused (exit 2).
#   F. (round 1, process-holes S11) final55-analysis.sh — GENERATED from the expectations strings — is RED on the identity
#      record (J1's relabelling; nothing landed) and HOLDS on the all-met synthetic; its --check catches a drifted script.
#   Round 2: K13 a CARRIER capture that vanished refuses the read (exit 2; instrument-teeth S3) and K14 a copy without that
#      branch HOLDS again; K15 a flag without its value exits 2 (S5); J6 the generator-truth S2 synthetic (inset-005 ios /
#      android f→P, both [F] rows PASS) retires ios and keeps android DEGENERATE (stayDegenerateEvenIfPass), J6b a copy without
#      the honesty-list read retires both; J7 adjudicate --exp without a value exits 2; F4 final55-analysis.sh refuses to run
#      against an expectations.json whose sha256 is not the one it was generated from (process-holes S8).
# Writes only under <scratch> and <worktree>, plus readers-calibration.txt beside this file. Read-only over runs.
# Usage: bash tools/titan/results/wave55-gate/readers-calibration.sh <worktree> <scratch-dir>
set -uo pipefail
SH="$(cd "$(dirname "$0")/../../../.." && pwd -P)"
WT="$(cd "$1" && pwd -P)"; S="$(mkdir -p "$2" && cd "$2" && pwd -P)"
[ "$WT" != "$SH" ] || { echo "refusing: <worktree> is the shared tree"; exit 2; }
PY="/opt/homebrew/bin/python3.12 -B"; G=tools/titan/results/wave55-gate; P=tools/titan/results/wave55-plan
exec > >(sed -u -E "s#$S#<scratch>#g; s#$WT#<worktree>#g; s#(/private)?/tmp/claude-[^ ]*#<scratch path>#g" | tee "$SH/$G/readers-calibration.txt") 2>&1   # scratch / worktree paths redacted (PLAN §8 step 18 greps for scratchpad paths)
echo "# readers-calibration.txt — $(date -u +%Y-%m-%dT%H:%M:%SZ); shared tree HEAD $(git -C "$SH" rev-parse --short HEAD); expectations sha256 $(shasum -a 256 "$SH/$P/expectations.json" | cut -c1-16)…"
FAILS=0
want() {  # <label> <wanted exit> <wanted text> <cmd…>
  local label="$1" wrc="$2" wtxt="$3"; shift 3; local o; o="$("$@" 2>&1)"; local rc=$?
  if [ "$rc" = "$wrc" ] && grep -qF -- "$wtxt" <<<"$o"; then echo "  as required  $label — exit $rc: $(grep -F -- "$wtxt" <<<"$o" | head -1 | cut -c1-170)"
  else echo "  !! NOT as required  $label — exit $rc (wanted $wrc and \"$wtxt\")"; FAILS=$((FAILS+1)); fi; }

echo; echo "## K. control-check.mjs ($G, run from the shared tree: read-only)"
CC="node $G/control-check.mjs"
want "K1 identity wave54-final → wave54-final (= wave54-final → wave55-open, which the opening gate measured byte-identical)" 0 "CONTROL HOLDS" bash -c "cd '$SH' && nice -n 19 $CC wave54-final wave54-final"
want "K2 the wave-54 pair wave54-open → wave54-final: wave 54's landed changes are not wave-55 carriers" 1 "CONTROL FAILED" bash -c "cd '$SH' && nice -n 19 $CC wave54-open wave54-final"
want "K3 --lane L7-instruments (empty carrier set) on the identity pair" 0 "CONTROL HOLDS" bash -c "cd '$SH' && nice -n 19 $CC wave54-final wave54-final --lane L7-instruments"
want "K3 --lane L7-instruments on the wave-54 pair" 1 "CONTROL FAILED" bash -c "cd '$SH' && nice -n 19 $CC wave54-open wave54-final --lane L7-instruments"
# the synthetic post run: CSS2 + filter-effects of wave54-final; one pixel nudged on a may-move, the ring-fenced and a complement android capture
SYN="$S/cc-syn"; rm -rf "$SYN"; mkdir -p "$SYN/sections"
for sec in CSS2 filter-effects; do cp -R "$SH/tools/titan/runs/wave54-final/sections/$sec" "$SYN/sections/"; done
$PY - "$SH/$P/expectations.json" "$SYN" > "$S/cc-syn.pick.txt" <<'EOF'
import json, os, sys
from PIL import Image
e, syn = json.load(open(sys.argv[1])), sys.argv[2]
may = [s for s in e['unionMayMoveCaptures']['android'] if s.startswith('wpt__CSS2__')]
car = set(e['unionCaptureCarriers']['android'])
d = os.path.join(syn, 'sections', 'CSS2', 'android-screenshots')
plain = sorted(f[:-4] for f in os.listdir(d) if f.endswith('.png') and f[:-4] not in may and f[:-4] not in car)
picks = {'may-move': ('CSS2', may[0]), 'ring-fenced': ('filter-effects', 'wpt__filter-effects__backdrop-filter-basic-blur'), 'complement': ('CSS2', plain[0])}
for what, (sec, st) in picks.items():
    f = os.path.join(syn, 'sections', sec, 'android-screenshots', st + '.png')
    im = Image.open(f).convert('RGBA'); r, g, b, a = im.getpixel((0, 0)); im.putpixel((0, 0), (r ^ 8, g, b, a)); im.save(f)
    print(what, st)
EOF
echo "  synthetic post run $SYN: $(tr '\n' ';' < "$S/cc-syn.pick.txt")"
want "K4 synthetic: the complement nudge is a LEAK" 1 "CONTROL FAILED: 1 capture(s)" bash -c "cd '$SH' && nice -n 19 $CC wave54-final '$SYN' --sections CSS2,filter-effects"
o=$(cd "$SH" && nice -n 19 $CC wave54-final "$SYN" --sections CSS2,filter-effects 2>&1); echo "     its lines: $(grep -E '^      (may-move|RING-FENCED|LEAK)' <<<"$o" | sed 's/^ *//' | cut -c1-80 | tr '\n' '|')"
cp "$SH/tools/titan/runs/wave54-final/sections/CSS2/android-screenshots/$(awk '/^complement/{print $2}' "$S/cc-syn.pick.txt").png" "$SYN/sections/CSS2/android-screenshots/"
want "K5 synthetic without the complement nudge: may-move + ring-fenced only" 0 "CONTROL HOLDS" bash -c "cd '$SH' && nice -n 19 $CC wave54-final '$SYN' --sections CSS2,filter-effects"
[ -e "$WT/node_modules" ] || ln -s "$SH/node_modules" "$WT/node_modules"     # sharp, for the copies run from the worktree
mkdir -p "$WT/$G"; cp "$SH/$P/expectations.json" "$WT/$P/expectations.json"
$PY - "$SH/$G/control-check.mjs" "$WT/$G/control-check.mjs" <<'EOF2'
import sys
s = open(sys.argv[1]).read(); old = "      else if (mayMove(platform).has(stem)) {"
assert s.count(old) == 1; open(sys.argv[2], 'w').write(s.replace(old, "      else if (false /* MUTATION: no may-move class */) {"))
EOF2
want "K6 MUTATION (may-move class removed, worktree copy) turns K5 red" 1 "CONTROL FAILED: 1 capture(s)" bash -c "cd '$WT' && nice -n 19 node $G/control-check.mjs wave54-final '$SYN' --sections CSS2,filter-effects"
# Round 1 (S1): the scope refusals and the vanished-file leaks. Fake post runs are symlink trees of wave54-final's css-text.
mkpost() {  # <name>: a one-section post run (css-text) whose sub-dirs are symlinks to wave54-final's; prints its path
  local d="$S/cc-$1"; rm -rf "$d"; mkdir -p "$d/sections/css-text"
  for sub in "$SH/tools/titan/runs/wave54-final/sections/css-text"/*; do ln -s "$sub" "$d/sections/css-text/"; done; echo "$d"; }
unlink_one() {  # <post> <sub-dir> <file>: replace the sub-dir symlink by a dir of symlinks to every file but <file>
  local d="$1/sections/css-text/$2"; rm "$d"; mkdir "$d"
  for f in "$SH/tools/titan/runs/wave54-final/sections/css-text/$2"/*; do [[ "$(basename "$f")" == "$3" ]] || ln -s "$f" "$d/"; done; }
GONE=$($PY -c "import json;e=json.load(open('$SH/$P/expectations.json'));import os
bad=set(e['unionCaptureCarriers']['android'])|set(e['unionMayMoveCaptures']['android'])
print(sorted(f[:-4] for f in os.listdir('$SH/tools/titan/runs/wave54-final/sections/css-text/android-screenshots') if f.endswith('.png') and f[:-4] not in bad)[0])")
echo "  K7-K12 fake post runs under $S/cc-*; the vanished capture / document: $GONE (a complement stem: no carrier, no may-move)"
ID1=$(mkpost id1); CG=$(mkpost capgone); unlink_one "$CG" android-screenshots "$GONE.png"; WG=$(mkpost wiregone); unlink_one "$WG" per-test-ir "$GONE.json"
want "K7 a mistyped --sections name (css-txt) is refused, not a vacuous hold" 2 "REFUSED" bash -c "cd '$SH' && nice -n 19 $CC wave54-final '$ID1' --sections css-txt"
want "K8 a listed section absent from the post run is refused" 2 "REFUSED" bash -c "cd '$SH' && nice -n 19 $CC wave54-final '$ID1' --sections CSS2"
want "K9 a capture that DISAPPEARS is a counted LEAK (absent after)" 1 "CONTROL FAILED: 1 capture(s)" bash -c "cd '$SH' && nice -n 19 $CC wave54-final '$CG' --sections css-text"
want "K10 a per-test IR document that DISAPPEARS is a WIRE LEAK" 1 "CONTROL FAILED: 0 capture(s) and 1 wire document(s)" bash -c "cd '$SH' && nice -n 19 $CC wave54-final '$WG' --sections css-text"
want "K11 a one-section post run read WITHOUT --sections is refused (29 sections absent from it)" 2 "REFUSED" bash -c "cd '$SH' && nice -n 19 $CC wave54-final '$ID1'"
$PY - "$SH/$G/control-check.mjs" "$WT/$G/control-check.mjs" <<'EOF3'
import sys
s = open(sys.argv[1]).read(); old = "      if (!existsSync(fb)) { r.changed.push({ stem, note: 'absent after' }); if (!allowed.has(stem)) out.leaks.push({ sec, platform, stem, note: 'absent after' }); else out.missingCarriers.push({ sec, platform, stem, note: 'absent after' }); continue; }"
assert s.count(old) == 1; open(sys.argv[2], 'w').write(s.replace(old, "      if (!existsSync(fb)) continue;   // MUTATION: the pre side not read"))
EOF3
want "K12 MUTATION (the pre side not read, worktree copy): K9's vanished capture HOLDS again" 0 "CONTROL HOLDS" bash -c "cd '$WT' && nice -n 19 node $G/control-check.mjs wave54-final '$CG' --sections css-text"
# Round 2 (S3): a CARRIER capture that vanished — hyphenate-character-001 ios (an L1 carrier) removed from a css-text post run
CARG=$(mkpost cargone); unlink_one "$CARG" ios-screenshots "wpt__css-text__hyphens__hyphenate-character-001.png"
want "K13 a CARRIER capture that DISAPPEARS refuses the read (a carrier may change, never vanish)" 2 "carrier capture(s) present on one side only" bash -c "cd '$SH' && nice -n 19 $CC wave54-final '$CARG' --sections css-text"
$PY - "$SH/$G/control-check.mjs" "$WT/$G/control-check.mjs" <<'EOF4'
import sys
s = open(sys.argv[1]).read(); old = "else out.missingCarriers.push({ sec, platform, stem, note: 'absent after' }); continue; }"
assert s.count(old) == 1; open(sys.argv[2], 'w').write(s.replace(old, "else r.carriers.push(stem); continue; }   // MUTATION: the round-1 carrier branch"))
EOF4
want "K14 MUTATION (the round-1 branch: a vanished carrier counted 'changed (carrier)', worktree copy): K13 HOLDS again" 0 "CONTROL HOLDS" bash -c "cd '$WT' && nice -n 19 node $G/control-check.mjs wave54-final '$CARG' --sections css-text"
want "K15 a flag without its value (--sections) is a usage error" 2 "given without a value" bash -c "cd '$SH' && node $G/control-check.mjs wave54-final wave54-final --sections"

echo; echo "## J. adjudicate.mjs"
AD="node $G/adjudicate.mjs"
( cd "$SH" && nice -n 19 node tools/titan/score-gate.mjs wave54-final wave54-final --movers 0 --json "$S/adj-id.json" >/dev/null 2>&1 )
( cd "$SH" && nice -n 19 node tools/titan/score-gate.mjs wave54-open wave54-final --movers 0.005 --json "$S/adj-thr.json" >/dev/null 2>&1 )
$PY - "$S/adj-id.json" "$SH/$P/expectations.json" "$S" <<'EOF'
import json, sys
src, exp, S = sys.argv[1:]
e = json.load(open(exp))
gate = {p['cell']: p for l in e['lanes'].values() for p in l['predictions'] if p['gating']}
def build(name, extra=None):
    d = json.load(open(src)); d['prev'], d['cur'] = 'wave55-open', 'wave55-final'
    keep = []
    for c in d['movers']:
        k = f"{c['test'].replace('css/', '', 1)} {c['platform']}"
        if name != 'id' and k in gate and not c['curPass'] and gate[k]['to'].startswith('P'):
            c = dict(c, cur=0.97, curPass=True, delta=round(0.97 - c['prev'], 4)); d['gained'].append(c); continue
        if extra and k == extra:
            c = dict(c, cur=round(c['prev'] + 0.003, 4), delta=0.003)
        keep.append(c)
    d['movers'] = keep
    json.dump(d, open(f'{S}/adj-{name}.json', 'w'))
build('allmet'); build('mnm3', extra=e['mustNotMoveComplement']['cells'][0])
print('  synthetic records: allmet (the 9 gating f→P rows at P 0.97), mnm3 (allmet + complement', e['mustNotMoveComplement']['cells'][0], '+0.003)')
EOF
d=$($PY -c "import json;d=json.load(open('$S/adj-id.json'));d['prev'],d['cur']='wave55-open','wave55-final';json.dump(d,open('$S/adj-relab.json','w'))")
want "J1 the identity record (nothing landed): the 9 gating flip rows are missed" 1 "gating predictions 44, missed 9" bash -c "cd '$SH' && $AD '$S/adj-relab.json'"
want "J2 all-met synthetic: R1-R5 hold" 0 "ADJUDICATION: R1–R5 hold" bash -c "cd '$SH' && $AD '$S/adj-allmet.json'"
want "J3 all-met + one complement cell +0.003: R5 FAIL, labelled complement" 1 "must-not-move MOVED (complement)" bash -c "cd '$SH' && $AD '$S/adj-mnm3.json'"
want "J4 a record whose prev is not the base is refused" 2 "ORDER" bash -c "cd '$SH' && $AD '$S/adj-thr.json'"
$PY -c "import json;d=json.load(open('$S/adj-thr.json'));d['prev'],d['cur']='wave55-open','wave55-final';json.dump(d,open('$S/adj-thr-relab.json','w'))"
want "J4b a thresholded (--movers 0.005) record is refused" 2 "NOT A --movers 0 RECORD" bash -c "cd '$SH' && $AD '$S/adj-thr-relab.json'"
$PY - "$SH/$G/adjudicate.mjs" "$WT/$G/adjudicate.mjs" <<'EOF2'
import sys
s = open(sys.argv[1]).read(); old = "  ...((EXP.mustNotMoveComplement && EXP.mustNotMoveComplement.cells) || []).map((c) => ['complement', c]), ...heldCells];"
assert s.count(old) == 1; open(sys.argv[2], 'w').write(s.replace(old, "  ...heldCells];   // MUTATION: no complement read"))
EOF2
grep -q 'MUTATION: no complement read' "$WT/$G/adjudicate.mjs" || { echo "  !! the J5 mutation did not apply"; FAILS=$((FAILS+1)); }
want "J5 MUTATION (complement not read, worktree copy): J3's move is no longer seen" 0 "R5  must-not-move cells" bash -c "cd '$WT' && $AD '$S/adj-mnm3.json'"
# Round 2 (generator-truth S2): inset-005 ios / android f → P 0.97 with both [F] rows PASS (plan-skeptic-round-2.generator-truth.d/scripts/r6-synth.py)
$PY - "$SH/tools/titan/results/wave55-plan/cells-wave54-final.json" "$S" <<'EOF6'
import json, sys
snap, out = json.load(open(sys.argv[1])), sys.argv[2]
tot = {s: {p: {'passing': 0, 'measured': 0} for p in ('web', 'ios', 'android')} for s in ('prev', 'cur')}; gained, movers = [], []
for k, v in snap['cells'].items():
    t, p = k.rsplit(' ', 1); s_ = float(v.split()[1]); P = v.startswith('P')
    tot['prev'][p]['measured'] += 1; tot['cur'][p]['measured'] += 1; tot['prev'][p]['passing'] += P
    if t == 'css-text-decor/text-decoration-inset-005.html' and p in ('ios', 'android'):
        gained.append({'test': 'css/' + t, 'platform': p, 'prev': s_, 'cur': 0.97, 'prevPass': P, 'curPass': True, 'delta': round(0.97 - s_, 4)}); tot['cur'][p]['passing'] += 1
    else:
        movers.append({'test': 'css/' + t, 'platform': p, 'prev': s_, 'cur': s_, 'prevPass': P, 'curPass': P, 'delta': 0}); tot['cur'][p]['passing'] += P
json.dump({'prev': 'wave55-open', 'cur': 'wave55-final', 'totals': tot, 'gained': gained, 'lost': [], 'movers': movers, 'newlyMeasured': [], 'unmeasuredNow': [],
           'missingSections': [], 'columnShorts': []}, open(f'{out}/adj-r6.json', 'w'))
rows = [{'script': 'ua-heading-margin.geometry.py', 'key': f'[F] text-decoration-inset-005 {p}', 'verdict': 'PASS', 'line': f'[F] text-decoration-inset-005 {p} bands … → GEOMETRY OK'} for p in ('android', 'ios')]
json.dump({'run': 'wave55-final', 'base': 'wave55-open', 'rows': rows}, open(f'{out}/adj-r6.geo.json', 'w'))
EOF6
o=$(cd "$SH" && $AD "$S/adj-r6.json" --geometry "$S/adj-r6.geo.json" 2>&1)
if grep -q "text-decoration-inset-005.html android .*stays degenerate (stayDegenerateEvenIfPass)" <<<"$o" && grep -q "^RETIRED from degenerateByConstruction (degenerateRetirement holds — gains): 1$" <<<"$o" && grep -q "text-decoration-inset-005.html ios .*units U1-ios, U2-ios on the tree" <<<"$o"
then echo "  as required  J6 the R6 synthetic: inset-005 ios RETIRED (a gain), inset-005 android stays DEGENERATE (stayDegenerateEvenIfPass) — $(grep -c 'stays degenerate' <<<"$o") line"
else echo "  !! NOT as required  J6 — $(grep -E 'RETIRED|stays degenerate|DEGENERATE-BY' <<<"$o" | tr '\n' '|' | cut -c1-200)"; FAILS=$((FAILS+1)); fi
$PY - "$SH/$G/adjudicate.mjs" "$WT/$G/adjudicate.mjs" <<'EOF7'
import sys
s = open(sys.argv[1]).read(); old = "  if (stayDegenerate.has(`${test} ${c.platform}`)) return"
assert s.count(old) == 1; open(sys.argv[2], 'w').write(s.replace(old, "  if (false) return   // MUTATION: the honesty list not read —"))
EOF7
want "J6b MUTATION (stayDegenerateEvenIfPass not read, worktree copy): both inset-005 gains RETIRED (the generator-truth S2 defect)" 1 "RETIRED from degenerateByConstruction (degenerateRetirement holds — gains): 2" bash -c "cd '$WT' && $AD '$S/adj-r6.json' --geometry '$S/adj-r6.geo.json'"
want "J7 adjudicate --exp without a value is a usage error" 2 "given without a value" bash -c "cd '$SH' && $AD '$S/adj-r6.json' --exp"
rm -f "$WT/$G/control-check.mjs" "$WT/$G/adjudicate.mjs"

echo; echo "## F. final55-analysis.sh (generated; the closing read-out) — red on the identity record, holds on the all-met one"
want "F0 the installed script is in sync with expectations.json" 0 "in sync" bash -c "cd '$SH' && $PY $G/final55-analysis.gen.py --check"
( cd "$SH" && nice -n 19 $PY $P/geometry-gate.py wave54-final --base wave54-final --self-test --json "$S/f55-geo-id.json" >/dev/null 2>&1 )   # --self-test: the identity pair (round 2 S2 refuses --base = the run otherwise)
$PY - "$S/f55-geo-id.json" "$S/f55-geo-relab.json" "$S/f55-geo-pass.json" <<'EOF5'
import json, sys
g = json.load(open(sys.argv[1])); g['run'], g['base'] = 'wave55-final', 'wave55-open'; json.dump(g, open(sys.argv[2], 'w'))
for r in g['rows']:                                       # the all-met geometry: every gating / tally key PASS
    if r['class'] in ('gating', 'report'):
        r['verdict'] = 'PASS'
g['gatingFail'] = 0; g['gatingUnmeasured'] = 0; g['selfCheckFail'] = 0; json.dump(g, open(sys.argv[3], 'w'))
EOF5
want "F1 the identity record (J1's relabelling) and the pre-fix geometry: the read-out FAILS" 1 "FINAL READ-OUT FAILS" \
  bash -c "cd '$SH' && CUR=wave54-final PRE=wave54-final SCORE='$S/adj-relab.json' GEO='$S/f55-geo-relab.json' OUT='$S/f55-id' nice -n 19 bash $G/final55-analysis.sh"
want "F2 the all-met record and an all-PASS geometry over an identity control: the read-out HOLDS" 0 "FINAL READ-OUT HOLDS" \
  bash -c "cd '$SH' && CUR=wave54-final PRE=wave54-final SCORE='$S/adj-allmet.json' GEO='$S/f55-geo-pass.json' OUT='$S/f55-ok' nice -n 19 bash $G/final55-analysis.sh"
cp "$SH/$G/final55-analysis.sh" "$S/f55-drift.sh"; sed -i '' 's/--movers 0 --json/--movers 0.005 --json/' "$S/f55-drift.sh"
mkdir -p "$WT/$G"; cp "$SH/$G/final55-analysis.gen.py" "$WT/$G/"; cp "$S/f55-drift.sh" "$WT/$G/final55-analysis.sh"; cp "$SH/$P/expectations.json" "$WT/$P/expectations.json"
want "F3 a hand-edited (drifted) script is caught by --check (worktree copy)" 1 "DRIFT" bash -c "cd '$WT' && $PY $G/final55-analysis.gen.py --check"
# Round 2 (process-holes S8): the generated script refuses an expectations.json it was not generated from (a regeneration not followed by gen.py)
cp "$SH/$G/final55-analysis.sh" "$WT/$G/final55-analysis.sh"; $PY -c "import json;p='$WT/$P/expectations.json';e=json.load(open(p));e['wave']=55.5;json.dump(e,open(p,'w'),indent=1,ensure_ascii=False)"
want "F4 final55-analysis.sh on an expectations.json with another sha256 refuses to run (STALE, exit 2)" 2 "STALE" bash -c "cd '$WT' && CUR=wave54-final PRE=wave54-final SCORE='$S/adj-allmet.json' GEO='$S/f55-geo-pass.json' OUT='$S/f55-stale' bash $G/final55-analysis.sh"
rm -f "$WT/$G/final55-analysis.gen.py" "$WT/$G/final55-analysis.sh"
echo; echo "readers-calibration: $([ $FAILS -eq 0 ] && echo 'every calibration behaved as required' || echo "$FAILS calibration(s) did NOT behave as required")"
exit $FAILS
