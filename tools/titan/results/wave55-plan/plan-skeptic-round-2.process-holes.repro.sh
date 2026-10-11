#!/usr/bin/env bash
# plan-skeptic-round-2.process-holes.repro.sh <scratch-dir> — the executed checks behind
# plan-skeptic-round-2.process-holes.md (wave 55, plan-skeptic "process holes", round 2).
#
# Why a script: every finding in the report is a claim about what the plan's own tools and records DO; this file re-runs
# each one so a fix lane can turn it green and an S1 reader can re-check it. Read-only on the shared tree:
#   plan-build.py runs with --out <scratch> only; final55-analysis.gen.py runs --check --exp <scratch json> only;
#   land-units.sh runs --check only (tree_mut exits 70 in that mode) against a THROWAWAY via --root;
#   every other git call is a read (rev-parse, status --no-optional-locks, diff, ls-files, worktree list) or
#   `git diff --no-index` between scratch files; the only state-changing git commands are
#   `git worktree add --detach <scratch>/wt-* HEAD` and `git worktree remove --force` of the same dirs.
# Interpreter: the plan's pinned /opt/homebrew/bin/python3.12 -B (C14). Output paths are redacted (<scratch>, ~).
set -uo pipefail
S="$(mkdir -p "${1:?usage: plan-skeptic-round-2.process-holes.repro.sh <scratch-dir>}" && cd "$1" && pwd -P)"
HERE="$(cd "$(dirname "$0")" && pwd -P)"; ROOT="$(git -C "$HERE" rev-parse --show-toplevel)"
PY="/opt/homebrew/bin/python3.12 -B"; P=tools/titan/results/wave55-plan; G=tools/titan/results/wave55-gate
KT=runtimes/compose/src/main/java/com/styleconverter/runtime; SW=runtimes/swiftui/Sources/StyleConverterRuntime
SWT=runtimes/swiftui/Tests/StyleConverterRuntimeTests; CRS=$SW/Renderer/ComponentRenderer.swift
cd "$ROOT" || exit 2
hdr() { printf '\n## %s\n' "$*"; }
red() { sed -e "s#$S#<scratch>#g" -e "s#$HOME#~#g" -e 's#\(\.\./\)*private/tmp/[^ ]*#<scratch>/…#g'; }
snap() { { git -C "$ROOT" rev-parse HEAD; git -C "$ROOT" --no-optional-locks status --short --untracked-files=all | grep -v "^?? $P/\|^?? $G/\|^ M $P/"; } ; }
mkwt() { local wt="$S/$1"; rm -rf "$wt"; git -C "$ROOT" worktree add -q --detach "$wt" HEAD >/dev/null 2>&1 || { echo "worktree add failed: $1"; exit 2; }; echo "$wt"; }
rmwt() { git -C "$ROOT" worktree remove --force "$1" && echo "  (throwaway <scratch>/$(basename "$1") removed)"; }
BEFORE="$(snap)"

hdr "H inputs (sha256)"
for f in $P/PLAN.md $P/expectations.json $P/landing-manifest.json $P/plan-build.py $P/land-units.sh $G/ab-arms55.sh $G/final55-analysis.sh; do
  printf '  %-48s %s\n' "$f" "$(shasum -a 256 "$f" | cut -c1-16)…"; done
echo "  HEAD $(git rev-parse --short HEAD)"

hdr "E1 plan-prose.py --check on the shared tree"
$PY $P/plan-prose.py --check 2>&1 | tail -2; echo "exit=${PIPESTATUS[0]}"

hdr "E2 the generator reproduces its outputs (plan-build.py --out <scratch>)"
mkdir -p "$S/repro"; $PY $P/plan-build.py --out "$S/repro" > "$S/repro/out.txt" 2>&1; echo "exit=$?"
for f in expectations.json watchlist.txt; do cmp -s "$S/repro/$f" "$P/$f" && echo "  $f byte-identical" || echo "  $f DIFFERS"; done
cmp -s "$S/repro/out.txt" "$P/plan-build.out.txt" && echo "  plan-build.out.txt byte-identical" || echo "  plan-build.out.txt DIFFERS"

hdr "E3 the 15 audit conflicts C1-C15: each resolution encoded in expectations.json / landing-manifest.json"
$PY - "$P/expectations.json" "$P/landing-manifest.json" "$P/PLAN.md" <<'PYX'
import json, re, sys
e = json.load(open(sys.argv[1])); m = json.load(open(sys.argv[2])); plan = open(sys.argv[3]).read(); L = e['lanes']
def ok(c, cond, msg): print(f"  {c:<4} {'OK ' if cond else 'BAD'} {msg}")
s1 = e['stage1Probe']['sections']; ok('C1', len(s1) == 9 and 'css-writing-modes' in s1, f"stage-1 union {len(s1)} sections incl. css-writing-modes; complement {len(e['mustNotMoveComplement']['cells'])} cells")
L6 = L['L6-android-paint-order']; carr = {s for n, l in L.items() if n != 'L6-android-paint-order' for s in l['captureCarriers']['android']}
ok('C2', len(L6['sharedStems']) == 12 and not (set(L6['mayMove']['android']) & carr), f"sharedStems {len(L6['sharedStems'])}; mayMove ∩ other lanes' android carriers = {len(set(L6['mayMove']['android']) & carr)}")
l7m = ' '.join(L['L7-instruments']['mustNotMove']); l7p = [g['cmd'].split('/')[-1].split()[0] for g in L['L7-instruments']['geometryProbes']]
ok('C3', 'counter-suffix.html ios' not in l7m and 'counter-suffix.html android' not in l7m and not any('rtl-marker' in x for x in l7p), f"L7 must-not-move holds no counter-suffix native; L7 probes {l7p}")
l5p = [g['cmd'].split('/')[-1].split()[0] for g in L['L5-counter-suffix-rows']['geometryProbes']]
ok('C4', any('rtl-marker-bake' in x for x in l5p) and any('lists-bakes' in x for x in l5p), f"L5 probes {l5p}")
ds = 'wpt__css-counter-styles__counter-style-at-rule__disclosure-styles'
u1 = L['L5-counter-suffix-rows']['revertUnits']['U1-ios']['captures']['ios']
ok('C5', ds in u1 and ds in L6['mayMove']['android'] and not any('disclosure-styles.html android' in x for x in L['L5-counter-suffix-rows']['mustNotMove']), "disclosure-styles: ios ∈ L5 U1-ios carriers, android ∈ L6 mayMove, android ∉ L5 must-not-move")
drp = [k for g in L['L3-positioned-parent-slot']['geometryProbes'] for k in g.get('dropped', {})]; ok('C6', drp == ['[C] anchor-center-safe-rtl android'], f"L3 dropped {drp}")
mu = {(u['lane'], u['unit']): u for u in m['units']}; ro = L['L5-counter-suffix-rows']['revertUnits']
ok('C7', 'D' in ro['U2o-ios']['revertOrder'] and 'D' in ro['U2o-android']['revertOrder'] and all(any(c['before'] == f'L5:{u}' and c['after'] == 'L5:D' for c in m['orderConstraints']) for u in ('U2o-ios', 'U2o-android')), f"D in both U2o chains {ro['U2o-ios']['revertOrder']} / {ro['U2o-android']['revertOrder']}; manifest constraints U2o-ios < D and U2o-android < D")
ok('C8', 'breadcrumb' in L['L2-cbb-android']['revertUnits']['U1']['commit'], "L2 U1 commit carries the breadcrumb-assertion row")
ok('C9', not any('ContentBoxRelativeInflationTest' in json.dumps(u) for u in L['L2-cbb-android']['revertUnits'].values()), "no L2 unit edits ContentBoxRelativeInflationTest (orchestrator docs pass)")
cm = {n: json.dumps(l['revertUnits'], ensure_ascii=False) for n, l in L.items()}
ok('C10', ':159' in cm['L4-ua-heading-margin'] and ':167' in cm['L5-counter-suffix-rows'] and ':182' in cm['L3-positioned-parent-slot'] and ':159' in json.dumps(m['deliveryContract']), "pinned anchors :159 (L4) / :167 (L5) / :182 (L3) in the commit strings and deliveryContract.cutOn")
gat = [p for l in L.values() for p in l['predictions'] if p['gating']]; ff = [p for l in L.values() for p in l['predictions'] if p['from'].startswith('f') and p['to'].startswith('f')]
fb = any('fallbackIfReverted' in p for p in L['L3-positioned-parent-slot']['predictions'])
ok('C11', all(p['floor'] == 0.95 for p in gat) and all('floorNA' in p for p in ff) and fb, f"gating {len(gat)} all floor 0.95; f→f {len(ff)} all floorNA; dir-style-02a fallbackIfReverted {fb}")
ok('C12', bool(L['L4-ua-heading-margin'].get('dryRuns')) and bool(ro['U1-ios'].get('dryRun')), "L4 dryRuns + L5 U1-ios dryRun present")
ok('C13', not re.search(r'StyleApplier[^ ]*:\d', json.dumps(L['L2-cbb-android'])), "L2 JSON cites StyleApplier by symbol, never by line")
cmds = [g['cmd'] for l in L.values() for g in l['geometryProbes']]; ok('C14', all(c.startswith(e['interpreter'] + ' ') for c in cmds), f"{len(cmds)} probe cmds on {e['interpreter']}")
miss = [k for l in L.values() for g in l['geometryProbes'] for k in list(g.get('gating', {})) + list(g.get('report', [])) if k not in g.get('keyCells', {})]
ok('C15', not miss, f"expect keys without keyCells: {len(miss)}")
ids = [d['id'] for d in e['planDecisions']]; sec9 = plan[plan.index('## 9.'):plan.index('## 10.')]
ok('§9', all(re.search(rf'^\| {i} \|', sec9, re.M) for i in ids if i.startswith('C')), f"§9 table carries a row for each of {sum(1 for i in ids if i.startswith('C'))} C-ids of planDecisions")
PYX

hdr "E4 round-1 M4 / S8 re-check: an L6 U2 revert withdraws may-move and its arm"
mkdir -p "$S/rev-l6u2"
echo '[{"lane":"L6-android-paint-order","unit":"U2","run":"wave55-pre","commit":"aaaaaaa","revertCommit":"bbbbbbb","rule":"4: [X] FAIL (fake)"}]' > "$S/rev-l6u2/reverted.json"
$PY $P/plan-build.py --out "$S/rev-l6u2" --reverted "$S/rev-l6u2/reverted.json" > "$S/rev-l6u2/out.txt" 2>&1; echo "plan-build exit=$?"
$PY - "$S/rev-l6u2/expectations.json" <<'PYX'
import json, sys
b = json.load(open(sys.argv[1]))
print('  unionMayMoveCaptures.android', len(b['unionMayMoveCaptures']['android']), '· abArms', [a['arm'] for a in b['abArms']], '· withdrawn', [a['arm'] for a in b.get('abArmsWithdrawn', [])])
PYX

hdr "E5 GO-SMALL (probe-decisions/holds.json goSmall.held) is not wired to the arm or to the landing"
mkdir -p "$S/gosmall"; $PY $P/plan-build.py --out "$S/gosmall" --go-small > "$S/gosmall/out.txt" 2>&1; echo "plan-build --go-small exit=$?"
$PY - "$S/gosmall/expectations.json" <<'PYX'
import json, sys
b = json.load(open(sys.argv[1])); L4 = b['lanes']['L4-ua-heading-margin']; a = next(x for x in b['abArms'] if x['arm'] == 'drop-L4-U2')
print('  heldUnits', list(L4.get('heldUnits', {})), '· revertUnits', list(L4['revertUnits']))
print('  drop-L4-U2 units', a['units'], '· drops', a['drops'], '(U2-android is held: it never lands, so the arms file cannot resolve it)')
PYX
echo "  land-units.sh reads holds.json: $(grep -c 'holds.json' $P/land-units.sh) line(s) (the landing needs --hold L4:U2-android by hand; its plan check errs if absent)"
echo '[{"lane":"L4-ua-heading-margin","unit":"U2-android","run":"wave55-pre","commit":"aaaaaaa","revertCommit":"bbbbbbb","rule":"4: [F] FAIL (fake)"}]' > "$S/gosmall/reverted.json"
$PY $P/plan-build.py --out "$S/gosmall2" --go-small --reverted "$S/gosmall/reverted.json" > "$S/gosmall/out2.txt" 2>&1; echo "  GO-SMALL taken AFTER U2-android landed (a stage-1 hold + its revert row): plan-build exit=$?"
tail -1 "$S/gosmall/out2.txt" | cut -c1-160 | red

hdr "E6 the L5 U1 LRM re-cut (a pre-registered stage-1 swap, §7 step 7) has no generator or landing path"
mkdir -p "$S/cont"
echo '[{"lane":"L1-hyphen-ios-linebox","unit":"NB-ios-b","run":"wave55-pre","why":"anchor offset 1 (fake)"}]' > "$S/cont/l1.json"
$PY $P/plan-build.py --out "$S/cont/l1" --contingency "$S/cont/l1.json" > "$S/cont/l1.out" 2>&1; echo "  control: an L1 NB-ios-b contingency row → plan-build exit=$?"
$PY -c "import json,sys; b=json.load(open(sys.argv[1])); print('    NB-ios revertOrder', b['lanes']['L1-hyphen-ios-linebox']['revertUnits']['NB-ios']['revertOrder'])" "$S/cont/l1/expectations.json"
echo '[{"lane":"L5-counter-suffix-rows","unit":"U1-ios-LRM","run":"wave55-pre","why":"[H] ios H-low (fake)"}]' > "$S/cont/l5.json"
$PY $P/plan-build.py --out "$S/cont/l5" --contingency "$S/cont/l5.json" > "$S/cont/l5.out" 2>&1; echo "  an L5 U1 LRM row (the only way §7 step 7 says a swap is recorded) → plan-build exit=$?"
tail -1 "$S/cont/l5.out" | cut -c1-160 | red
echo "  L5 lane keys with 'contingency': $($PY -c "import json; print([k for k in json.load(open('$P/expectations.json'))['lanes']['L5-counter-suffix-rows'] if 'ontingen' in k])")"
echo "  land-units.sh ORDER rows for an L5 re-cut: $(grep -cE '^[0-9]+\|L5\|.*(LRM|U1-ios-b|contingency)' $P/land-units.sh); --contingency lands: $(grep -o 'L1 NB-ios-b only' $P/land-units.sh | head -1)"

hdr "E7 a stage-1 regeneration drifts final55-analysis.sh, which §7 step 6 does not regenerate"
echo '[{"lane":"L2-cbb-android","unit":"U2","run":"wave55-pre","commit":"aaaaaaa","revertCommit":"bbbbbbb","rule":"4 (fake)"},{"lane":"L2-cbb-android","unit":"U1","run":"wave55-pre","commit":"ccccccc","revertCommit":"ddddddd","rule":"4 (fake)"}]' > "$S/l2pair.json"
$PY $P/plan-build.py --out "$S/l2pair" --reverted "$S/l2pair.json" > /dev/null 2>&1; echo "  plan-build (L2 pair reverted) exit=$?"
$PY $G/final55-analysis.gen.py --check --exp "$S/l2pair/expectations.json" 2>&1 | tail -1 | cut -c1-160 | red; echo "  final55-analysis.gen.py --check on the regenerated JSON: exit=${PIPESTATUS[0]}"
echo "  §7 step 6 names final55-analysis.gen.py: $(awk '/^6\. \*\*Revert, then regenerate/,/^7\. /' $P/PLAN.md | grep -c 'final55')"
echo "  final55-analysis.sh checks the expectations sha at run time: $(grep -c 'shasum\|sha256sum' $G/final55-analysis.sh)"

hdr "E8 beforeEveryDeviceRun (and the hash block, and the arm runner) do not see an UNTRACKED source file"
WT="$(mkwt wt-untracked)"
printf '// a stray NEW lane file on the shared tree (fake)\nobject StrayLaneFile\n' > "$WT/$KT/StrayLaneFile.kt"
printf '// a stray NEW lane file on the shared tree (fake)\nenum StrayLaneFile {}\n' > "$WT/$SW/StrayLaneFile.swift"
( cd "$WT" && git diff --quiet HEAD -- runtimes apps converter tools/titan ':(exclude)tools/titan/results' ':(exclude)*.md' && git diff --cached --quiet ); echo "  beforeEveryDeviceRun clause 1 on a tree with 2 untracked runtime sources: exit=$? (0 = the device run proceeds and builds them)"
( cd "$WT" && git diff --quiet -- runtimes apps converter ':(exclude)*.md' ); echo "  installed-build-hash.sh '+ uncommitted' test: exit=$? (0 = the block says nothing)"
echo "  ab-arms55.sh tracked_dirty (--untracked-files=no): '$(cd "$WT" && git --no-optional-locks status --porcelain --untracked-files=no)' (empty = not refused)"
echo "  the fix (status --untracked-files=all over the code paths) lists: $(cd "$WT" && git --no-optional-locks status --porcelain --untracked-files=all -- runtimes apps converter tools/titan ':(exclude)tools/titan/results' | tr '\n' ' ')"
echo "  the same fix on the shared tree today lists: '$(git --no-optional-locks status --porcelain --untracked-files=all -- runtimes apps converter tools/titan ':(exclude)tools/titan/results' | tr '\n' ' ')' (empty: it would not refuse a clean tree)"
printf '// mutation\n' >> "$WT/$KT/StyleApplier.kt"
( cd "$WT" && git diff --quiet HEAD -- runtimes apps converter tools/titan ':(exclude)tools/titan/results' ':(exclude)*.md' && git diff --cached --quiet ); echo "  control — a TRACKED edit: clause 1 exit=$? (1 = refused, as designed)"
rmwt "$WT"

hdr "E9 the one-shot hash wait returns at once on a launch whose driver.log already says 'attempt 1 starting'"
grep -n 'LOG=\|>> "\$LOG"\|mkdir -p "\$DRV_DIR"' tools/titan/gate-driver.sh | head -3
mkdir -p "$S/drv"; printf '[gate-driver 10:41:20] CSS2: attempt 1 starting\n[gate-driver 11:52:00] css-view-transitions: attempt 1 rc=137\n' > "$S/drv/driver.log"
t0=$(date +%s); until grep -q 'attempt 1 starting' "$S/drv/driver.log" 2>/dev/null; do sleep 15; done; echo "  a --resume launch of the same run id: the plan's until-grep returned after $(( $(date +%s) - t0 )) s, before that launch's provision"
echo "  PLAN names a hash read for a --resume launch: $(grep -c 'resume.*hash\|hash.*--resume' $P/PLAN.md)"

hdr "E10 beforeEveryDeviceRun's daemon roots: the 'wave55-' grep and the TREES: placeholder"
echo "  registered worktrees matching wave55-: $(git worktree list --porcelain | sed -n 's/^worktree //p' | grep -cE 'wave55-')"
echo "  registered Workflow worktrees (wf_*): $(git worktree list --porcelain | sed -n 's/^worktree //p' | grep -cE '/wf_')  e.g. $(git worktree list | grep -m1 '/wf_' | sed "s#$HOME#~#" | tr -s ' ')"
echo "  PLAN rules that name a lane worktree path with 'wave55-': $(grep -cE 'worktree[^|]*wave55-<|wave55-<lane>-wt|<scratch>/wave55-' $P/PLAN.md)"
echo "  beforeEveryDeviceRun carries a placeholder, not a command: $($PY -c "import json; print('<every tree on a lane note' in json.load(open('$P/expectations.json'))['beforeEveryDeviceRun'])")"

hdr "E11 an S1 fix patch delivered as §8 step 8 says (fix-S1-<U>.patch in the lane dir) is refused by land-units.sh's M3 guard"
WT="$(mkwt wt-fixs1)"; L1D="$WT/$P/../wave55-hyphen-ios-linebox"; mkdir -p "$L1D"; L1D="$(cd "$L1D" && pwd -P)"
MODEL="$S/model"; rm -rf "$MODEL"; mkdir -p "$MODEL/$(dirname "$CRS")"; cp "$WT/$CRS" "$MODEL/$CRS"
mkpatch() { # <out> <rel> <anchor|NEW> <text>  (git diff --no-index between scratch files; the selftest's helper)
  local d="$S/mk.tmp"; rm -rf "$d"; mkdir -p "$d/a/$(dirname "$2")" "$d/b/$(dirname "$2")"
  if [[ "$3" == NEW ]]; then printf '%s\n' "$4" > "$d/b/$2"; (cd "$d" && git diff --no-index --no-prefix --full-index /dev/null "b/$2") >> "$1"
  else cp "$MODEL/$2" "$d/a/$2"; $PY - "$d/a/$2" "$d/b/$2" "$3" "$4" <<'PYX'
import sys
a, b, anc, txt = sys.argv[1:5]; L = open(a).read().split('\n'); i = next(k for k, l in enumerate(L) if anc in l); L[i+1:i+1] = txt.split('\n'); open(b, 'w').write('\n'.join(L))
PYX
    (cd "$d" && git diff --no-index --no-prefix --full-index "a/$2" "b/$2") >> "$1"; cp "$d/b/$2" "$MODEL/$2"; fi; rm -rf "$d"; }
note() { { printf '# synthetic lane note (round-2 repro)\n\nTREES: <scratch>/wave55-hyphen-ios-linebox (synthetic)\n\n## NB-ios\n\nSYNTHETIC.\n\n'
  for p in "$1"/*.patch; do printf 'patch: %s sha256 %s\n' "${p##*/}" "$(shasum -a 256 "$p" | cut -c1-64)"; done; printf '\nSTATUS: COMPLETE\n'; } > "$1/_note.md"; }
mkpatch "$L1D/unit-NB-ios.patch" "$SW/StyleEngine/typography/line/NormalLineBox.swift" NEW "// SYNTHETIC (round-2 repro, L1 NB-ios)
enum NormalLineBox {}"
mkpatch "$L1D/seam-1.patch" "$CRS" "|| wptUnbreakableRun || broken.preBroken) && !uprightPlans" "        // SYNTHETIC seam 1 (round-2 repro)"
note "$L1D"; find "$L1D" -exec touch -t 202610110000 {} +
bash $P/land-units.sh --check --only L1 --root "$WT" > "$S/e11a.txt" 2>&1; echo "  control: COMPLETE note, unit + seam patch → --check exit=$?"
mkpatch "$L1D/fix-S1-NB-ios.patch" "$SW/StyleEngine/typography/line/NormalLineBoxFix.swift" NEW "// SYNTHETIC -S1 fix (round-2 repro)"
note "$L1D"; find "$L1D" -exec touch -t 202610110000 {} +
bash $P/land-units.sh --check --only L1 --root "$WT" > "$S/e11b.txt" 2>&1; echo "  + fix-S1-NB-ios.patch with its patch: line (PLAN §8 step 8) → --check exit=$?"
grep -E 'fix-S1|SUMMARY' "$S/e11b.txt" | red | cut -c1-200 | sed 's/^/    /'
echo "  the --contingency path walks the same lane-dir loop (RECDIRS has no CONT guard): $(awk '/^# ── records/,/^# ── optional units/' $P/land-units.sh | grep -c 'CONT')  CONT mention(s) in the records block (the force-add line only)"
rmwt "$WT"

hdr "E12 the destructive-path self-tests are recorded against older script bytes"
echo "  land-units.sh now $(shasum -a 256 $P/land-units.sh | cut -c1-16)… · land-units.selftest.out.txt (A-D, the real landing path) cites $(grep -o 'sha256 [0-9a-f]*' $P/land-units.selftest.out.txt | head -1) · r1-EF cites $(grep -o 'sha256 [0-9a-f]*' $P/land-units.selftest.r1-EF.out.txt | head -1)"
echo "  ab-arms55.sh now $(shasum -a 256 $G/ab-arms55.sh | cut -c1-16)… · ab-arms55.selftest.out.txt (--run, restore, records) cites $(grep -o 'runner sha256 [0-9a-f]*' $G/ab-arms55.selftest.out.txt | head -1) · against-proof (dry-run only) cites $(grep -o 'runner sha256 [0-9a-f]*' $G/ab-arms55.against-proof.out.txt | head -1)"
echo "  PLAN §8 steps 2-15 name a re-run of land-units.selftest.sh A-D / ab-arms55.selftest.sh: $(awk '/^\*\*Order of operations/,/^## 9\./' $P/PLAN.md | grep -cE 'land-units\.selftest|ab-arms55\.selftest')"

hdr "E13 the iOS .app embeds the same test-all IR as the apk (the apk-asset finding applies to iOS too)"
grep -n 'cp "\$OUTPUT_DIR/tmpOutput.json"' test-all.sh | sed 's/^/  test-all.sh:/'
echo "  is the sync step guarded by SKIP_IOS? $(awk 'NR>=452 && NR<=462' test-all.sh | grep -c 'SKIP_IOS')"
grep -n 'StyleConverterTest/Resources' apps/ios-harness/project.yml | head -1 | sed 's/^/  project.yml:/'
shasum apps/ios-harness/StyleConverterTest/Resources/tmpOutput.json apps/android-harness/app/src/main/assets/tmpOutput.json | sed 's/^/  /'
echo "  PLAN §0 hash rule states the asset caveat for: android $(grep -c 'apk sha1 is comparable across launches only when the last fixture-net asset' $P/PLAN.md) · ios $(grep -ciE 'ios[^.]*Resources/tmpOutput|\.app[^.]*embeds' $P/PLAN.md)"

hdr "E14 §6 'Two caveats' contradict the generated strings and the reader's code"
grep -n "name \`wave52-gate/make-corpus.mjs\` as the snapshot's reader\|does not count it in its exit status" $P/PLAN.md | cut -c1-150
$PY -c "import json; e=json.load(open('$P/expectations.json')); print('  corpusSnapshotSource names make-snapshot55:', 'make-snapshot55' in e['corpusSnapshotSource'], '· names make-corpus:', 'make-corpus' in e['corpusSnapshotSource'])"
grep -n "note: 'absent before' }); if (!allowed.has(stem)) out.leaks.push" $G/control-check.mjs | cut -c1-140 | sed 's/^/  control-check.mjs:/'

hdr "E15 stale arm names left by the D8 rename"
$PY -c "import json; e=json.load(open('$P/expectations.json')); print('  L4 stage1Decision says \"arm drop-U2\":', 'arm drop-U2 ' in e['lanes']['L4-ua-heading-margin']['stage1Decision'])"
echo "  land-units.sh ORDER row 03 note: $(grep -o 'L4 drop-U2, L6 drop-U2' $P/land-units.sh | head -1)"

hdr "E16 disk: lane / skeptic / S1 worktrees each get their own DerivedData, and nothing removes them"
df -g "$ROOT" | tail -1 | awk '{print "  free " $4 " GiB of " $2}'
for d in ~/Library/Developer/Xcode/DerivedData/StyleConverterRuntime-*; do printf '  %s %s → WorkspacePath %s\n' "$(du -sh "$d" | cut -f1)" "$(basename "$d" | cut -c1-30)…" "$(/usr/libexec/PlistBuddy -c 'Print :WorkspacePath' "$d/info.plist" 2>/dev/null | sed "s#$HOME#~#")"; done
echo "  PLAN lines on worktree removal / disk / DerivedData: $(grep -ciE 'worktree remove|DerivedData|df -|disk' $P/PLAN.md) (the one hit is the planning-stage throwaway at :47)"
echo "  registered worktrees older than this wave still on the host: $(git worktree list | grep -c '/wf_c3a87fe8')"

hdr "E17 the provisioning symlink resolves @style-converter/web to the SHARED tree"
ls -l node_modules/@style-converter/ | tail -1 | sed "s#$HOME#~#" | sed 's/^/  /'

hdr "E18 committed-to-be records that carry scratchpad paths (§8 step 18 greps for them at ship)"
PAT='/private/tmp/clau''de-|scratch''pad/'; grep -rlE "$PAT" $P $G | grep -v 'plan-skeptic-round-2\.process-holes' | sed 's/^/  /'

hdr "E19 the seam landing order on paper: hunk windows (anchor ± 3 context) at HEAD, cross-lane and intra-lane"
$PY - <<'PYX'
# (file, lane·unit, changed lines, inserted-after) from PLAN §3 / seam-registry.md; context = 3 lines either side
H = {'kt': [('L4 s1 U1-android', 1157, 1165), ('L4 s3 U2-android', 1161, 1161), ('L2 s1 U1', 1950, 1951), ('L3 s1 U1', 3633, 3661),
            ('L5 s4 U2o-android', 4482, 4482), ('L5 s5 U2i-android', 4665, 4665)],
     'swift': [('L4 s2 U1-ios', 340, 356), ('L4 s4 U2-ios', 352, 356), ('L5 s1 U1-ios @3973', 3973, 3973), ('L5 s1 U1-ios @4561', 4561, 4561),
               ('L5 s1 U1-ios @4602', 4602, 4602), ('L5 s2 U2o-ios', 4618, 4618), ('L5 s1 U1-ios @4626', 4626, 4626), ('L5 s3 U2i-ios', 4646, 4646),
               ('L5 s1 U1-ios @4650', 4650, 4650), ('L1 s1 NB-ios cut', 5055, 5069), ('L1 s1 NB-ios insert', 5151, 5154),
               ('L1 s1b NB-ios-b', 5335, 5335), ('L5 s6 U3-ios', 5537, 5537)],
     'SeamReachabilityTest': [('L4 s1+s3 (stacked)', 159, 159), ('L5 s4', 167, 167), ('L3 s1+U2 (stacked)', 182, 182)]}
for f, hs in H.items():
    hs = sorted(hs, key=lambda h: h[1])
    for a, b in zip(hs, hs[1:]):
        lane_a, lane_b = a[0].split()[0], b[0].split()[0]
        win_a, win_b = (a[1] - 3, a[2] + 3), (b[1] - 3, b[2] + 3)
        gap = b[1] - a[2]
        mod_in_other = (win_a[0] <= b[1] <= win_a[1]) or (win_b[0] <= a[2] <= win_b[1])
        kind = 'intra' if lane_a == lane_b else 'CROSS'
        verdict = 'STACKED (same lane; cut in order)' if (kind == 'intra' and a[2] >= b[1]) else ('a changed line inside the other\'s context window' if mod_in_other else ('shared context only' if win_a[1] >= win_b[0] else 'disjoint'))
        if True:
            print(f"  {f:<20} {kind:<5} {a[0]:<24} :{a[1]}-{a[2]}  vs  {b[0]:<24} :{b[1]}-{b[2]}  gap {gap:>4}  {verdict}")
PYX

hdr "E20 rule-6 revert states vs the §0 revertStatesCompiled list"
$PY - "$P/expectations.json" <<'PYX'
import json, sys
e = json.load(open(sys.argv[1]))
for n, l in e['lanes'].items():
    if n.startswith('L7'): continue
    states = sorted({' + '.join(u['revertOrder']) for u in l['revertUnits'].values()})
    print(f"  {n:<28} chains: {' | '.join(states)}")
PYX
grep -o 'L4 minus U2-<p>; L5 minus D, U2i, U2o; L3 minus U2; L6 minus U2; the L2 pair; L1 minus NB-ios-b' $P/PLAN.md | head -1 | sed 's/^/  §0 lists: /'

hdr "E21 regenerable counts still typed outside the gen blocks (lesson 11)"
grep -nE 'asserts all [0-9]+ of its own|probeRun.s [0-9]+ sections|over `probeRun`.s [0-9]+ sections' $P/PLAN.md | cut -c1-140
$PY -c "import json; m=json.load(open('$P/landing-manifest.json')); e=json.load(open('$P/expectations.json')); print('  landing-manifest orderConstraints', len(m['orderConstraints']), '· probeRun sections', len(e['probeRun']['sections']))"

hdr "E22 ab-arms55.sh's revert check, non-chain branch: each commit's reverse is checked ALONE on the current tree"
sed -n '/^revert_check() {/,/^}/p' $G/ab-arms55.sh | sed 's/^/  | /'
# Emulation with plain patch files (git apply outside any repository, as the runner's `git diff c^ c | git apply -R --check`):
# U = a unit, W = its -S1 fix on the same line, landed later (not U's child: the S1 commits land after every unit, §8 step 8).
E="$S/e22"; rm -rf "$E"; mkdir -p "$E"; rel=runtimes/x/Unit.kt
cutp() { local d="$E/mk"; rm -rf "$d"; mkdir -p "$d/a/$(dirname "$rel")" "$d/b/$(dirname "$rel")"
  if [[ "$2" == NEW ]]; then printf "$3" > "$d/b/$rel"; (cd "$d" && git diff --no-index --no-prefix --full-index /dev/null "b/$rel") > "$E/$1.patch"
  else printf "$2" > "$d/a/$rel"; printf "$3" > "$d/b/$rel"; (cd "$d" && git diff --no-index --no-prefix --full-index "a/$rel" "b/$rel") > "$E/$1.patch"; fi; rm -rf "$d"; }
tree() { rm -rf "$E/t"; mkdir -p "$E/t/$(dirname "$rel")"; printf "$1" > "$E/t/$rel"; }
chk() { (cd "$E/t" && git apply -R --check "$E/$1.patch" >/dev/null 2>&1) && echo ok || echo REFUSED; }
cutp U 'a\nfoo = 1\nb\n' 'a\nfoo = 2\nb\n'; cutp W 'a\nfoo = 2\nb\n' 'a\nfoo = 3\nb\n'; tree 'a\nfoo = 3\nb\n'
echo "  (a) a modified line: arms file 'drop-X … <W> <U>' (child first), current tree = U + W"
echo "      runner, one by one on the current tree:  W $(chk W) · U $(chk U)   → the arm is REFUSED (exit 2)"
(cd "$E/t" && git apply -R "$E/W.patch" && git apply -R --check "$E/U.patch") >/dev/null 2>&1 && echo "      cumulative (what git revert W U does): W ok · U ok" || echo "      cumulative: FAIL"
cutp N NEW 'enum NormalLineBox {\n  static let a = 1\n}\n'; cutp B 'enum NormalLineBox {\n  static let a = 1\n}\n' 'enum NormalLineBox {\n  static let a = 1\n  static func verticalPads() {}\n}\n'
tree 'enum NormalLineBox {\n  static let a = 1\n  static func verticalPads() {}\n}\n'
echo "  (b) a created file (NB-ios) later extended (NB-ios-b, or any -S1 on a NEW file): drop-NB-ios 'NB-ios-b NB-ios'"
echo "      runner, one by one on the current tree:  NB-ios-b $(chk B) · NB-ios $(chk N)   → REFUSED"
(cd "$E/t" && git apply -R "$E/B.patch" && git apply -R --check "$E/N.patch") >/dev/null 2>&1 && echo "      cumulative: NB-ios-b ok · NB-ios ok" || echo "      cumulative: FAIL"
echo "  the plan's drops put -S1 commits / a landed contingency first in every arm: $($PY -c "import json; print([a['arm']+': '+' → '.join(a['drops']) for a in json.load(open('$P/expectations.json'))['abArms']])")"

hdr "E23 every native unit in stage1Probe.decides has a carrier in a stage-1 section"
$PY - "$P/expectations.json" <<'PYX'
import json, sys
e = json.load(open(sys.argv[1])); s1 = set(e['stage1Probe']['sections']); bad = []
for n, l in e['lanes'].items():
    for u, spec in l['revertUnits'].items():
        nat = [s for p in ('ios', 'android') for s in spec['captures'][p]]
        if nat and not any(s.split('__')[1] in s1 for s in nat): bad.append(f'{n} {u}')
        if nat and f'{n} {u}' not in e['stage1Probe']['decides']: bad.append(f'{n} {u} (not in decides)')
print('  native units without a stage-1 carrier or not decided at stage 1:', bad or 'none')
PYX

hdr "E24 how an -S1 revert and a regenerated re-read are recorded (rule 6 / §7 step 6)"
mkdir -p "$S/s1row"; echo '[{"lane":"L6-android-paint-order","unit":"U2-S1","run":"wave55-pre","commit":"aaaaaaa","revertCommit":"bbbbbbb","rule":"6: -S1 before its unit (fake)"},{"lane":"L6-android-paint-order","unit":"U2","run":"wave55-pre","commit":"ccccccc","revertCommit":"ddddddd","rule":"4 (fake)"}]' > "$S/s1row/rev.json"
$PY $P/plan-build.py --out "$S/s1row/o" --reverted "$S/s1row/rev.json" > "$S/s1row/out.txt" 2>&1; echo "  a reverted.json row for the -S1 commit rule 6 reverts first → plan-build exit=$? ($(tail -1 "$S/s1row/out.txt"))"
echo "  PLAN says how an -S1 revert is recorded in reverted.json: $(grep -cE 'S1[^|]*reverted\.json|reverted\.json[^|]*-S1' $P/PLAN.md)"
echo "  PROBE_READOUT renders a regenerated re-read (the '*.regen.*' names of §7 step 6): $(grep -c 'regen\.' $P/plan-build.py) · in expectations.json: $(grep -c 'regen\.' $P/expectations.json)"

hdr "E25 the arm's dry run as §7 step 5 writes it (no --against) skips the --against tree check that --run applies"
WT="$(mkwt wt-armdry)"; cp "$G/ab-arms55.sh" "$WT/$G/ab-arms55.sh"; echo "drop-L4-U2 css-text-decor ios,android $(git -C "$WT" rev-parse --short HEAD~1)" > "$S/arms.txt"
printf '== wave55-pre (SYNTHETIC round-2 block) — 2026-10-11T00:00:00Z — HEAD 7cce3b22\nandroid  MATCH\nios      MATCH\n\n' >> "$WT/$G/build-hashes.txt"
o1="$(AB_ROOT="$WT" bash "$WT/$G/ab-arms55.sh" "$S/arms.txt" --dry-run 2>&1)"; o2="$(AB_ROOT="$WT" bash "$WT/$G/ab-arms55.sh" "$S/arms.txt" --dry-run --against wave55-pre 2>&1)"
echo "  --dry-run (PLAN §7 step 5):            $(grep -m1 'against' <<<"$o1" | sed 's/^ *//' | cut -c1-120)"
echo "  --dry-run --against wave55-pre:         $(grep -m1 'differs from it in code' <<<"$o2" | sed 's/^ *- *//' | cut -c1-120)"
rmwt "$WT"

hdr "Z the shared tree is unchanged (HEAD + status outside the two plan dirs); no throwaway left"
[[ "$(snap)" == "$BEFORE" ]] && echo "  unchanged" || { echo "  CHANGED"; diff <(echo "$BEFORE") <(snap); }
echo "  scratch worktrees still registered: $(git worktree list --porcelain | grep -c "^worktree $S/")"
