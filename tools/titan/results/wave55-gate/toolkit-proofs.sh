#!/usr/bin/env bash
# tools/titan/results/wave55-gate/toolkit-proofs.sh — the wave-55 gate toolkit, each instrument shown able to FAIL before it is
# trusted (toolkit.done.md). Read-only over runs and over every source; writes only under <scratch> and toolkit-proofs.txt
# beside this file; the dry runs touch the throwaway worktree first. Light: one control-check pass over the wave-54 pair, the
# rest is JSON.
#   A. adjudicate.mjs — the repointing audit; synthetic --movers 0 records built from the wave-54 one
#      (wave54-gate/score-final.json): all gating rows met → exit 0; + one lost carrier cell → exit 1 (R1 only); prev ≠
#      wave55-open → exit 2 ORDER; the wave-54 0.005 record → exit 2 NOT A --movers 0 RECORD; a geometry JSON of another pair →
#      exit 2 GEOMETRY PAIR; a copy with R1 blinded → the lost cell passes (R1 is the rule that sees it).
#   B. control-check.mjs on wave54-open → wave54-final with the WAVE-55 carriers: every wave-54 change that is not a wave-55
#      carrier (or may-move / ring-fenced) must read LEAK; the count is the proof, and the class split must add up.
#   C. ab-arms55.sh — its self-test (git-init repos: T1 run, M-L18 / M-per-arm / M-dry RED, refusals); --dry-run in the throwaway
#      worktree (refuses: detached HEAD + a non-ancestor sha; prints the plan; writes nothing), then (round 3) on a scratch clone
#      of the branch at HEAD (DRY RUN OK) and on the shared tree (refused while it holds uncommitted tracked records).
#   D. make-snapshot55.mjs — on the wave-54 inputs it reproduces corpus-v6-20.json (totals equal, then byte-for-byte); the
#      comparison fails against corpus-v6-19.json; the four refusals; the label-filter mutation.
# Usage: bash tools/titan/results/wave55-gate/toolkit-proofs.sh <throwaway-worktree> <scratch-dir>
set -uo pipefail
SH="$(cd "$(dirname "$0")/../../../.." && pwd -P)"
WT="$(cd "$1" && pwd -P)"; S="$(mkdir -p "$2" && cd "$2" && pwd -P)"
[ "$WT" != "$SH" ] || { echo "refusing: <worktree> is the shared tree"; exit 2; }
G=tools/titan/results/wave55-gate; G54=tools/titan/results/wave54-gate; P=tools/titan/results/wave55-plan; R=tools/titan/results
exec > >(tee "$SH/$G/toolkit-proofs.txt") 2>&1
cd "$SH" || exit 2
h16() { shasum -a 256 "$1" | cut -c1-16; }
echo "# toolkit-proofs.txt — $(date -u +%Y-%m-%dT%H:%M:%SZ); shared tree HEAD $(git --no-optional-locks rev-parse --short HEAD); throwaway worktree $WT at $(git -C "$WT" rev-parse --short HEAD)"
echo "# sha256: expectations.json $(h16 $P/expectations.json)… adjudicate.mjs $(h16 $G/adjudicate.mjs)… control-check.mjs $(h16 $G/control-check.mjs)… ab-arms55.sh $(h16 $G/ab-arms55.sh)… ab-arms55.selftest.sh $(h16 $G/ab-arms55.selftest.sh)… make-snapshot55.mjs $(h16 $G/make-snapshot55.mjs)…"
FAILS=0
want() {  # <label> <wanted exit> <wanted text> <cmd…>
  local label="$1" wrc="$2" wtxt="$3"; shift 3; local o; o="$("$@" 2>&1)"; local rc=$?
  printf '%s\n' "$o" >"$S/last.out"
  if [ "$rc" = "$wrc" ] && grep -qF -- "$wtxt" <<<"$o"; then echo "  as required  $label — exit $rc: $(grep -F -- "$wtxt" <<<"$o" | head -1 | cut -c1-170)"
  else echo "  !! NOT as required  $label — exit $rc (wanted $wrc and \"$wtxt\")"; FAILS=$((FAILS+1)); fi; }
check() { local label="$1"; shift; if "$@"; then echo "  as required  $label"; else echo "  !! NOT as required  $label"; FAILS=$((FAILS+1)); fi; }

echo; echo "## A. adjudicate.mjs ($G)"
echo "  A0 repointing audit (the wave-54 file → this copy):"
check "A0a 'wave54' survives only in the provenance header ($(grep -n 'wave54' $G/adjudicate.mjs | cut -d: -f1 | tr '\n' ' '))" test "$(grep -c 'wave54' $G/adjudicate.mjs)-$(grep -n 'wave54' $G/adjudicate.mjs | cut -d: -f1)" = "1-7"
for s in "tools/titan/results/wave55-plan/expectations.json" "opt('--base', 'wave55-open')" "score-gate.mjs wave55-open wave55-final --movers 0" "tools/titan/results/wave55-gate/score-final.json" "geometry-gate.py wave55-final --base wave55-open" "tools/titan/results/wave55-gate/geometry-final.json" "EXP.probeDecisions && EXP.probeDecisions.reverted" "withdrawnPredictions" "mustNotMoveAfter" "mustNotMoveComplement"; do
  check "A0b the copy carries: $s" grep -qF -- "$s" $G/adjudicate.mjs
done
# the CODE (not comment) delta against the wave-54 reader: exactly the documented three (readerChanges)
code() { grep -vE '^[[:space:]]*//' "$1" | sed 's#//.*$##' ; }
diff <(code $G54/adjudicate.mjs) <(code $G/adjudicate.mjs) >"$S/adj-code.diff"
echo "     code delta vs $G54/adjudicate.mjs ($(grep -c '^[<>]' "$S/adj-code.diff") changed lines):"; sed 's/^/       /' "$S/adj-code.diff" | cut -c1-200
# plan-skeptic round 2 (fix-r2): + the value-less-flag refusal (USAGE + a 6-line opt, S5) and R6's stayDegenerateEvenIfPass read
# (2 lines, generator-truth S2) — 14 added code lines in all, each documented in expectations.json readerChanges
check "A0c the code delta is the expectations path, the --base default, the usage string, the R5 complement walk and (round 2) the opt() refusal + R6's honesty-list read — nothing else" test "$(grep -c '^>' "$S/adj-code.diff")" = 14

cat >"$S/build-syn.mjs" <<'EOF'
// Synthetic --movers 0 records for adjudicate.mjs, from the wave-54 one (wave54-gate/score-final.json, wave54-open → wave54-final):
// the wave-55 base is the wave-54 record's CUR side (wave55-open = wave54-final byte-for-byte, wave55-gate/_note.md).
import fs from 'node:fs';
const [src, src0005, expPath, out] = process.argv.slice(2);
const E = JSON.parse(fs.readFileSync(expPath, 'utf8'));
const key = (c) => `${c.test.replace(/^css\//, '')} ${c.platform}`;
const load = (f) => JSON.parse(fs.readFileSync(f, 'utf8'));
const predList = Object.values(E.lanes).flatMap((l) => l.predictions || []);
const gatingCells = new Set(predList.filter((p) => p.gating).map((p) => p.cell));
const save = (name, d) => fs.writeFileSync(`${out}/adj-${name}.json`, JSON.stringify(d));
function neutral() {
  const d = load(src); if (d.newlyMeasured.length || d.unmeasuredNow.length) throw new Error('one-sided cells in the source');
  d.movers = [...d.gained, ...d.lost, ...d.movers].map((c) => ({ ...c, prev: c.cur, prevPass: c.curPass, delta: 0 }));
  d.gained = []; d.lost = []; d.prev = 'wave55-open'; d.cur = 'wave55-final';
  d.totals = { prev: structuredClone(d.totals.cur), cur: structuredClone(d.totals.cur) };
  return d;
}
function setCell(d, k, cur, curPass) {   // re-file one cell (totals.cur.passing follows; adjudicate does not read sections)
  const c = [...d.gained, ...d.lost, ...d.movers].find((x) => key(x) === k); if (!c) throw new Error(`no cell ${k}`);
  for (const l of ['gained', 'lost', 'movers']) d[l] = d[l].filter((x) => x !== c);
  if (c.curPass !== curPass) d.totals.cur[c.platform].passing += curPass ? 1 : -1;
  (c.prevPass && !curPass ? d.lost : !c.prevPass && curPass ? d.gained : d.movers).push({ ...c, cur, curPass, delta: +(cur - c.prev).toFixed(4) });
}
const relab = load(src); relab.prev = 'wave55-open'; relab.cur = 'wave55-final'; save('relab', relab);
const all = neutral(); let met = 0;
for (const p of predList) if (p.gating && p.floor != null && / (web|ios|android)$/.test(p.cell)) {
  const c = [...all.gained, ...all.movers].find((x) => key(x) === p.cell); const want = /^P/.test(p.to);
  setCell(all, p.cell, want ? Math.max(c.prev, 0.97) : c.prev, want); met++;
}
save('allmet', all);
// one carrier cell (a wave-55 capture carrier — every carrier has a pre-registered row, so: one whose rows are all non-gating;
// P at the base) → f: lost. Carriers are in no must-not-move list, so R1 is the only rule that can see it.
const carrierCells = new Set(Object.entries(E.unionCaptureCarriers).flatMap(([pf, stems]) => stems.map((s) => `${s.replace(/^wpt__/, '').split('__').join('/')}.html ${pf}`)));
const pick = all.movers.filter((c) => carrierCells.has(key(c)) && !gatingCells.has(key(c)) && c.prevPass).map(key).sort()[0];
if (!pick) throw new Error('no non-gating carrier cell that passes at the base');
const lost = structuredClone(all); setCell(lost, pick, 0.9, false); save('lost', lost);
const order = structuredClone(all); order.prev = 'wave54-final'; save('order', order);
const thr = load(src0005); thr.prev = 'wave55-open'; thr.cur = 'wave55-final'; save('thr-relab', thr);
console.log(`gating rows met in allmet: ${met}; the lost carrier cell: ${pick} (${carrierCells.size} carrier cells in the union)`);
EOF
echo "  $(node "$S/build-syn.mjs" $G54/score-final.json $G54/score-final.movers0005.json $P/expectations.json "$S")"
AD="node $G/adjudicate.mjs"
want "A1 the wave-54 record relabelled (wave 54's moves under wave 55's rules) reads red" 1 "rule(s) broken — do not ship" $AD "$S/adj-relab.json"
echo "     its rule lines: $(grep -E '^(ok  |FAIL) R[0-9]' "$S/last.out" | cut -c1-60 | tr '\n' '|')"
want "A2 all-met synthetic (every gating row at its 'to', nothing else moved) holds" 0 "ADJUDICATION: R1–R5 hold" $AD "$S/adj-allmet.json"
want "A3 all-met + ONE lost carrier cell → exit 1" 1 "FAIL R1  lost 1; pre-registered 0; outside the list 1" $AD "$S/adj-lost.json"
echo "     its rule lines: $(grep -E '^(ok  |FAIL) R[0-9]' "$S/last.out" | cut -c1-60 | tr '\n' '|')"
check "A3b … and only R1 is broken" grep -q '^ADJUDICATION: 1 rule(s) broken' "$S/last.out"
want "A4 prev ≠ wave55-open (the all-met record with prev wave54-final) → ORDER" 2 "ORDER: this record is score-gate.mjs wave54-final → wave55-final" $AD "$S/adj-order.json"
want "A4b the raw wave-54 record (wave54-open → wave54-final) → ORDER" 2 "ORDER: this record is score-gate.mjs wave54-open → wave54-final" $AD $G54/score-final.json
want "A5 the wave-54 --movers 0.005 record (relabelled, so ORDER passes) → NOT A --movers 0 RECORD" 2 "NOT A --movers 0 RECORD: 81 cells listed against 4096" $AD "$S/adj-thr-relab.json"
want "A5b the raw wave-54 0.005 record → refused (ORDER fires first)" 2 "ORDER" $AD $G54/score-final.movers0005.json
want "A6 a geometry JSON of another pair (wave54-final --base wave54-open) → GEOMETRY PAIR" 2 "GEOMETRY PAIR" $AD "$S/adj-allmet.json" --geometry $G54/geometry-final.json
mkdir -p "$S/mut"
python3 - $G/adjudicate.mjs "$S/mut/adjudicate.noR1.mjs" <<'EOF'
import sys
s = open(sys.argv[1]).read(); a = "const realLost = score.lost.filter((c) => !lostAllowed.has(key(c)));"
assert s.count(a) == 1; open(sys.argv[2], 'w').write(s.replace(a, "const realLost = [];   // MUTATION: R1 blinded"))
EOF
want "A7 MUTATION (R1 blinded, scratch copy, --exp explicit): A3's lost cell is no longer seen" 0 "ADJUDICATION: R1–R5 hold" node "$S/mut/adjudicate.noR1.mjs" "$S/adj-lost.json" --exp $P/expectations.json

echo; echo "## B. control-check.mjs ($G) on wave54-open → wave54-final with the WAVE-55 carriers"
echo "  B0 repointing audit:"
check "B0a 'wave54' survives only in the provenance header ($(grep -n 'wave54' $G/control-check.mjs | cut -d: -f1 | tr '\n' ' '))" test "$(grep -n 'wave54' $G/control-check.mjs | cut -d: -f1 | tr '\n' ' ')" = "2 "
for s in "tools/titan/results/wave55-plan/expectations.json" "wireRenumbered++" "shift === runShift" "carriersWithdrawn" "unionMayMoveCaptures" "ringFenced.captures"; do
  check "B0b the copy carries: $s" grep -qF -- "$s" $G/control-check.mjs
done
want "B1 the wave-54 pair under the wave-55 carriers FAILS" 1 "CONTROL FAILED" nice -n 19 node $G/control-check.mjs wave54-open wave54-final --json "$S/cc-w54pair.json"
grep -E '^(total|wire:)' "$S/last.out" | sed 's/^/     /'
node - "$S/cc-w54pair.json" $G54/control-final-union.json $P/expectations.json >"$S/cc-account.txt" <<'EOF'
const fs = require('fs');
const [mine, w54, expPath] = process.argv.slice(2).map((f) => JSON.parse(fs.readFileSync(f, 'utf8')));
let ok = true, leaks = 0;
for (const p of ['web', 'ios', 'android']) {
  const t = mine.totals[p], l = mine.leaks.filter((x) => x.platform === p).length; leaks += l;
  const closes = l === t.changed - t.carriers - t.mayMove - t.ringFenced, same = t.changed === w54.totals[p].changed;
  ok = ok && closes && same;
  console.log(`${p.padEnd(8)} changed ${t.changed} (the wave-54 reader on this pair: ${w54.totals[p].changed}${same ? ', same' : ', DIFFERENT'}) = LEAK ${l} + wave-55 carrier ${t.carriers} + may-move ${t.mayMove} + ring-fenced ${t.ringFenced}${closes ? '' : '  ← DOES NOT ADD UP'}`);
}
const carriersHit = Object.entries(mine.sections).flatMap(([sec, row]) => Object.entries(row).flatMap(([p, r]) => r.carriers.map((s) => `${p} ${s}`)));
console.log(`wave-54 changes that ARE wave-55 capture carriers (not leaks): ${carriersHit.length}${carriersHit.length ? ' — ' + carriersHit.join(', ') : ''}`);
console.log(`may-move (L6 band, not leaks): ${mine.mayMove.length}${mine.mayMove.length ? ' — ' + mine.mayMove.map((x) => `${x.platform} ${x.stem}`).join(', ') : ''}`);
console.log(`ring-fenced: ${mine.ringFenced.length}`);
const wsame = mine.wire.changed === w54.wire.changed && mine.wire.renumbered === w54.wire.renumbered;
console.log(`wire: content-changed ${mine.wire.changed} → WIRE LEAK ${mine.wireLeaks.length} (unionWireCarriers is empty this wave); renumbered ${mine.wire.renumbered} (the class is kept: ids-only shifts explained by earlier content changes, not leaks); the wave-54 reader: changed ${w54.wire.changed}, renumbered ${w54.wire.renumbered}${wsame ? ', same' : ', DIFFERENT'}`);
ok = ok && wsame && mine.wireLeaks.length === mine.wire.changed;
console.log(`THE PROOF: ${leaks} capture LEAK(s) + ${mine.wireLeaks.length} wire LEAK(s) — every wave-54 change that is not a wave-55 carrier, may-move or ring-fenced capture reads LEAK; accounting ${ok ? 'CLOSES' : 'DOES NOT CLOSE'}`);
process.exit(ok ? 0 : 1);
EOF
acc=$?; sed 's/^/     /' "$S/cc-account.txt"
check "B2 the class split adds up per platform, the changed counts equal the wave-54 reader's on the same pair, and every content-changed wire document is a WIRE LEAK" test "$acc" = 0

echo; echo "## C. ab-arms55.sh"
bash $G/ab-arms55.selftest.sh "$S/abst" >/dev/null 2>&1; strc=$?
grep -E '^  (as required|!! NOT)' $G/ab-arms55.selftest.out.txt | sed 's/^  /     /' | cut -c1-175
check "C1 ab-arms55.selftest.sh: every check as required, M-L18 / M-per-arm / M-dry RED (record: ab-arms55.selftest.out.txt; exit $strc)" test "$strc" = 0
# Round 4 (fix-r4.done.md): the valid child-first chain is the branch's two NEWEST commits, read at run time — the hard-coded
# pair (640276a0 a2a37a8e, the two wave-55 record commits) stopped reverse-applying once the plan commit 611c9f93 edited their
# files on top of them (C2 / C3a went red on HEAD, not on any instrument); reversing HEAD then HEAD~1 always applies on HEAD.
GR="$(git --no-optional-locks rev-parse --short=8 HEAD) $(git --no-optional-locks rev-parse --short=8 HEAD~1)"
printf '# the toolkit-proofs arms: a valid child-first chain (the branch'"'"'s two newest commits) and a sha that is not on this branch\ndrop-gaterecs  css-text  web  %s\ndrop-old       css-text  ios  6e1c2d5e\n' "$GR" >"$S/arms.wt.txt"
wt_state() { { git -C "$SH" for-each-ref; git -C "$SH" stash list; git -C "$WT" rev-parse HEAD; git -C "$WT" status --porcelain --untracked-files=all; shasum -a 256 "$WT/$G/build-hashes.txt"; ls -la "$WT/$G" "$WT/tools/titan/runs" 2>&1 | grep -v ' \.\.$'; } | shasum -a 1 | cut -d' ' -f1; }
b="$(wt_state)"
want "C2 --dry-run in the throwaway worktree (detached HEAD) → REFUSED" 2 "detached HEAD: there is no branch to restore to" env AB_ROOT="$WT" bash $G/ab-arms55.sh "$S/arms.wt.txt" --dry-run
for t in "6e1c2d5e is not an ancestor of HEAD" "revert check: arm drop-gaterecs — the reverse of $GR applies" "driver --plan | run-id: wave55-ab-drop-gaterecs" "driver --plan | sections (1): css-text"; do
  check "C2 … and it reports: $t" grep -qF -- "$t" "$S/last.out"
done
check "C2 … and the worktree, its refs and this repo's refs / stash are byte-identical after it" test "$(wt_state)" = "$b"
printf 'drop-gaterecs  css-text  web  %s\n' "$GR" >"$S/arms.sh.txt"
sh_state() { { git --no-optional-locks for-each-ref; git --no-optional-locks stash list; git --no-optional-locks rev-parse HEAD; git --no-optional-locks status --porcelain --untracked-files=no; shasum -a 256 "$G/build-hashes.txt"; ls -la "$G/final" tools/titan/runs/wave55-ab-drop-gaterecs 2>&1; } | shasum -a 1 | cut -d' ' -f1; }
b="$(sh_state)"
# Plan-skeptic round 3 (fix-r3, S1): the shared tree carries the wave's UNCOMMITTED plan records (tracked edits under
# wave55-plan / wave55-gate) until PLAN §8's records commit, and the runner refuses a tracked-dirty tree by design — so the
# DRY RUN OK path is shown on a tracked-clean checkout of the same branch at HEAD: a scratch CLONE (`git clone --shared`, its
# own refs, under <scratch>; the shared repository is only read), and the shared tree's own dry run must refuse for exactly
# that reason while it is dirty (and print DRY RUN OK once it is clean).
CL="$S/c3-clone"; rm -rf "$CL"
git clone -q --shared --branch "$(git --no-optional-locks symbolic-ref --short HEAD)" "$SH" "$CL" 2>"$S/c3-clone.err"
cl_state() { { git -C "$CL" for-each-ref; git -C "$CL" rev-parse HEAD; git -C "$CL" status --porcelain --untracked-files=all; shasum -a 256 "$CL/$G/build-hashes.txt"; ls -la "$CL/$G" 2>&1 | grep -v ' \.\.$'; } | shasum -a 256; }
c="$(cl_state)"
want "C3a --dry-run on a scratch clone on $(git --no-optional-locks symbolic-ref --short HEAD) at $(git -C "$CL" rev-parse --short HEAD) (tracked-clean) → DRY RUN OK" 0 "DRY RUN OK — nothing written" env AB_ROOT="$CL" bash $G/ab-arms55.sh "$S/arms.sh.txt" --dry-run
check "C3a … the clone's refs, HEAD, status, build-hashes.txt and gate dir unchanged" test "$(cl_state)" = "$c"
if [ -n "$(git --no-optional-locks status --porcelain --untracked-files=no)" ]; then
  want "C3b --dry-run on the shared tree, tracked-dirty ($(git --no-optional-locks status --porcelain --untracked-files=no | wc -l | tr -d ' ') uncommitted tracked records) → REFUSED for that" 2 "tracked changes in the tree" bash $G/ab-arms55.sh "$S/arms.sh.txt" --dry-run
else
  want "C3b --dry-run on the shared tree (on campaign/applier-campaign, tracked-clean) → DRY RUN OK" 0 "DRY RUN OK — nothing written" bash $G/ab-arms55.sh "$S/arms.sh.txt" --dry-run
fi
check "C3b … refs, stash, HEAD, tracked status, build-hashes.txt and final/ unchanged" test "$(sh_state)" = "$b"
rm -rf "$CL"

echo; echo "## D. make-snapshot55.mjs"
MS="node $G/make-snapshot55.mjs"
node -e 'process.stdout.write(JSON.parse(require("fs").readFileSync(process.argv[1],"utf8"))._note + "\n")' $R/corpus-v6-20.json >"$S/note-v6-20.txt"
AGAINST54="$(node -e 'console.log(JSON.parse(require("fs").readFileSync(process.argv[1],"utf8")).perCellDiff.against)' $R/corpus-v6-20.json)"
DEV54="$(node -e 'console.log(JSON.parse(require("fs").readFileSync(process.argv[1],"utf8")).devSha)' $R/corpus-v6-20.json)"
W54=(--prev $R/corpus-v6-19.json --snapshot corpus-v6.20 --run wave54-final --base wave54-open --exp tools/titan/results/wave54-plan/expectations.json --date 2026-10-09)   # --gate per call
want "D1 the wave-54 inputs → a scratch snapshot" 0 "wrote" env DEV_SHA="$DEV54" $MS $G54/score-final.movers0005.json "$S/note-v6-20.txt" "$S/snap-w54.json" "${W54[@]}" --gate $G54 --against "$AGAINST54"
tot() { node -e 'console.log(JSON.stringify(JSON.parse(require("fs").readFileSync(process.argv[1],"utf8")).totals))' "$1"; }
echo "     totals: scratch $(tot "$S/snap-w54.json") · corpus-v6-20 $(tot $R/corpus-v6-20.json)"
check "D1a its totals equal corpus-v6-20.json's" test "$(tot "$S/snap-w54.json")" = "$(tot $R/corpus-v6-20.json)"
check "D1b … and the whole file is byte-identical to corpus-v6-20.json (sections, perCellDiff, installedBuilds, probeReverts, degenerateGains, reproduce)" cmp -s "$S/snap-w54.json" $R/corpus-v6-20.json
check "D2 the comparison can fail: the same scratch totals against corpus-v6-19.json differ ($(tot $R/corpus-v6-19.json))" test "$(tot "$S/snap-w54.json")" != "$(tot $R/corpus-v6-19.json)"
want "D3a the --movers 0 record (score-final.json) → NOT THE 0.005 RECORD" 2 "NOT THE 0.005 RECORD" $MS $G54/score-final.json "$S/note-v6-20.txt" "$S/snap-x.json" "${W54[@]}" --gate $G54
want "D3b the wave-54 record under the wave-55 defaults → ORDER" 2 "ORDER: tools/titan/results/wave54-gate/score-final.movers0005.json is score-gate.mjs wave54-open → wave54-final" $MS $G54/score-final.movers0005.json "$S/note-v6-20.txt" "$S/snap-x.json"
want "D3c wave55-gate has no final/cell-review.json yet → NO CELL REVIEW" 2 "NO CELL REVIEW" $MS $G54/score-final.movers0005.json "$S/note-v6-20.txt" "$S/snap-x.json" "${W54[@]}" --gate $G
mkdir -p "$S/gate-nohash/final"; cp $G54/final/cell-review.json "$S/gate-nohash/final/"; cp $G/build-hashes.txt "$S/gate-nohash/"
want "D3d a build-hashes.txt without a wave54-final block (wave55-gate's) → NO INSTALLED BUILD" 2 "NO INSTALLED BUILD" $MS $G54/score-final.movers0005.json "$S/note-v6-20.txt" "$S/snap-x.json" "${W54[@]}" --gate "$S/gate-nohash"
check "D3e no refusal wrote its output file" test ! -e "$S/snap-x.json"
# the label filter: an A/B arm block whose body names the run (ab-arms55.sh labels end "read against <run>)") is not a launch
mkdir -p "$S/gate-arm/final"; cp $G54/final/cell-review.json "$S/gate-arm/final/"; cp $G54/build-hashes.txt "$S/gate-arm/"
printf '== wave54-ab-drop-X (A/B arm of wave54: drop-X = ef20c977 minus 1234567; sections css-text; arm tree 89abcde; read against wave54-final) — 2026-10-09T12:00:00Z — HEAD 89abcde\nandroid  serial=emulator-5554  built app-debug.apk sha1=aaaa  installed base.apk sha1=aaaa\nandroid  MATCH\nios      udid=X  built .app digest=bbbb  installed .app digest=bbbb\nios      MATCH\n\n' >>"$S/gate-arm/build-hashes.txt"
builds() { node -e 'console.log(JSON.parse(require("fs").readFileSync(process.argv[1],"utf8")).installedBuilds.length)' "$1"; }
DEV_SHA="$DEV54" $MS $G54/score-final.movers0005.json "$S/note-v6-20.txt" "$S/snap-arm.json" "${W54[@]}" --gate "$S/gate-arm" >/dev/null 2>&1
python3 - $G/make-snapshot55.mjs "$S/mut/make-snapshot55.includes.mjs" <<'EOF'
import sys
s = open(sys.argv[1]).read(); a = ".filter((b) => b.startsWith(`${run} `))"
assert s.count(a) == 1; open(sys.argv[2], 'w').write(s.replace(a, ".filter((b) => b.includes(run))   /* MUTATION: wave 54's whole-block match */"))
EOF
DEV_SHA="$DEV54" node "$S/mut/make-snapshot55.includes.mjs" $G54/score-final.movers0005.json "$S/note-v6-20.txt" "$S/snap-arm-mut.json" "${W54[@]}" --gate "$S/gate-arm" >/dev/null 2>&1
check "D4 an A/B arm block that names the run in its label body is not published as a launch: $(builds "$S/snap-arm.json") builds; the MUTATION (wave 54's includes) publishes $(builds "$S/snap-arm-mut.json")" test "$(builds "$S/snap-arm.json")-$(builds "$S/snap-arm-mut.json")" = "2-3"

echo; echo "toolkit-proofs: $([ $FAILS -eq 0 ] && echo 'every instrument behaved as required (each shown able to fail)' || echo "$FAILS proof(s) did NOT behave as required")"
exit $FAILS
