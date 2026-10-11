#!/usr/bin/env bash
# tools/titan/results/wave55-gate/ab-arms55.selftest.sh — the self-test of ab-arms55.sh (L7 U3, instruments.md §4 U3).
# Device-free, Gradle-free, Chromium-free. Every repository it drives is a fresh `git init` under <scratch> — NOT a worktree of
# this repo, so no branch, ref or stash is shared — holding stubs for tools/titan/gate-driver.sh (prints `attempt 1 starting`,
# records which tree it ran on), tools/titan/own-processes.sh, wave55-gate/installed-build-hash.sh (appends AND tees a
# `== <label>` block beside itself, like the real one) and wave52-gate/ab-diff.mjs; a TRACKED build-hashes.txt; commits
# base → U → V → head and two arms, drop-U and drop-V (two, because the multi-arm case is where a per-arm append is lost).
# It asserts (T1): exit 0; HEAD restored on main; each arm's block exactly once and the in-arm tee'd copies gone; final/ab-<arm>
# .txt and .driver.txt written; each driver ran on its reverted tree; the stub pool's markers removed; and, at the end, the
# real /tmp/titan-device-pool and THIS repo's refs / worktrees / stash unchanged. Mutations (each must turn the checks RED):
#   M-L18     the records written on the arm branch, before the restore (the wave-54 order) → the blocks are missing;
#   M-per-arm the records written after EACH restore (the brief's literal step 8) → the second arm cannot keep the first block;
#   M-dry     --dry-run falling through to the run path → the dry-run identity check (refs + every file) goes red;
# plus the dry run's own identity (default mode and --dry-run: refs and files byte-identical), the refusals (exit 2, nothing
# touched), a failing driver (exit 1, records still written) and a driver that never starts (a NOT READ block, exit 1).
# Plan-skeptic round 2 added: T5b2 an UNTRACKED file under a code path refuses (process-holes S1); T5l an arm that INCLUDES
# the child commit W rewriting U's line ('drop-UW W U') dry-runs exit 0 while T5j (U alone) stays refused, and T5m a file
# created by N and extended by B ('drop-NB B N') dry-runs exit 0 (process-holes M1: the cumulative reverse check); the
# mutant M-onebyone (the round-1 per-commit check) turns T5l / T5m RED (refused).
# Usage: bash tools/titan/results/wave55-gate/ab-arms55.selftest.sh [<scratch-dir>]
# Writes ab-arms55.selftest.out.txt beside this file and nothing else in this tree.
set -uo pipefail
HERE="$(cd "$(dirname "$0")" && pwd -P)"; RUNNER="$HERE/ab-arms55.sh"; SH="$(cd "$HERE/../../../.." && pwd -P)"
if [[ -n "${1:-}" ]]; then mkdir -p "$1" && S="$(cd "$1" && pwd -P)"; else S="$(cd "$(mktemp -d)" && pwd -P)"; fi
case "$S/" in "$SH"/*) echo "refusing: the scratch dir is inside this tree"; exit 2 ;; esac
mkdir -p "$S/tmp"; export TMPDIR="$S/tmp/"
exec > >(tee "$HERE/ab-arms55.selftest.out.txt") 2>&1
echo "# ab-arms55.selftest.out.txt — $(date -u +%Y-%m-%dT%H:%M:%SZ); runner sha256 $(shasum -a 256 "$RUNNER" | cut -c1-16)…; scratch $S"
FAILS=0
ok() { echo "  as required  $*"; }
bad() { echo "  !! NOT as required  $*"; FAILS=$((FAILS + 1)); }
check() { local label="$1"; shift; if "$@"; then ok "$label"; else bad "$label"; fi; }
repo_state() { local r="$1"; { git -C "$r" for-each-ref --format='%(refname) %(objectname)'; git -C "$r" rev-parse HEAD; git -C "$r" symbolic-ref -q HEAD;
  ( cd "$r" && find . -path ./.git -prune -o -type f -print | LC_ALL=C sort | xargs shasum -a 1 ); } | shasum -a 1 | cut -d' ' -f1; }
this_repo() { { git -C "$SH" for-each-ref; git -C "$SH" worktree list --porcelain; git -C "$SH" stash list; } | shasum -a 1 | cut -d' ' -f1; }
real_pool() { ls -laT /tmp/titan-device-pool 2>&1 | grep -v ' \.\.$'; }   # every entry with its mtime, and the dir's own (an add / remove); not '..' (= /tmp, which the host touches)
THIS_BEFORE="$(this_repo)"; POOL_BEFORE="$(real_pool)"

make_repo() {   # <dir> → a fresh repo with the stubs; prints nothing; sets U V W HEADSHA
  local R="$1"; rm -rf "$R"; mkdir -p "$R"
  git init -q -b main "$R"; git -C "$R" config user.name ab-selftest; git -C "$R" config user.email ab-selftest@invalid; git -C "$R" config commit.gpgsign false
  mkdir -p "$R/tools/titan/results/wave55-gate" "$R/tools/titan/results/wave52-gate" "$R/src"
  printf 'tools/titan/runs/\n' >"$R/.gitignore"
  cat >"$R/tools/titan/gate-driver.sh" <<'EOF'
#!/usr/bin/env bash
# STUB gate driver: the arm protocol only (attempt-1 line while "devices are up", an OK, an rc).
DEFAULT_SECTIONS="CSS2 css-text css-text-decor"
RUN_ID="$1"; shift; PLAN=0; SECS=""
while (( $# )); do case "$1" in --sections) SECS="$2"; shift 2 ;; --plan) PLAN=1; shift ;; *) shift ;; esac; done
if (( PLAN )); then echo "run-id: $RUN_ID"; echo "sections: ${SECS//,/ }"; exit 0; fi
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"; D="$ROOT/tools/titan/runs/$RUN_ID"; mkdir -p "$D"
{ git -C "$ROOT" rev-parse --abbrev-ref HEAD; cat "$ROOT/src/a.txt" "$ROOT/src/b.txt"; } >"$D/tree.txt"   # which tree the arm ran on
echo "[stub] provision"; sleep 1
[[ "$RUN_ID" == *"${STUB_NO_START:-__none__}" ]] && { echo "[stub] provision wedged"; exit 1; }
echo "[stub] ${SECS%%,*}: attempt 1 starting"; sleep 2
[[ "$RUN_ID" == *"${STUB_FAIL_ARM:-__none__}" ]] && { echo "[stub] ${SECS%%,*}: FAILED(rc=1)"; exit 1; }
echo "[stub] ${SECS%%,*}: OK"; exit 0
EOF
  chmod +x "$R/tools/titan/gate-driver.sh"
  printf 'kill_own_gradle_daemons() { echo "stub: no gradle daemon of this checkout"; }\n' >"$R/tools/titan/own-processes.sh"
  cat >"$R/tools/titan/results/wave55-gate/installed-build-hash.sh" <<'EOF'
#!/usr/bin/env bash
# STUB: like the real one, it appends its block to build-hashes.txt beside itself AND prints it.
HERE="$(cd "$(dirname "$0")" && pwd)"; ROOT="$(cd "$HERE/../../../.." && pwd)"
{ echo "== $1 — $(date -u +%Y-%m-%dT%H:%M:%SZ) — HEAD $(git -C "$ROOT" rev-parse --short HEAD)"; echo "android  MATCH (stub)"; echo "ios      MATCH (stub)"; echo; } | tee -a "$HERE/build-hashes.txt"
EOF
  printf '== wave55-open (stub opening block)\nandroid  MATCH\nios      MATCH\n\n' >"$R/tools/titan/results/wave55-gate/build-hashes.txt"
  printf 'console.log(`A/B  exclude=${process.argv[2]}  include=${process.argv[3]}  (stub ab-diff) ${process.argv.slice(4).join(" ")}`);\nconsole.log("compared 0 cells: 0 differ (stub)");\n' >"$R/tools/titan/results/wave52-gate/ab-diff.mjs"
  printf 'a0\n' >"$R/src/a.txt"; printf 'b0\n' >"$R/src/b.txt"
  git -C "$R" add -A; git -C "$R" commit -q -m base
  printf 'a1\n' >"$R/src/a.txt"; git -C "$R" commit -q -am "U: a0 -> a1"; U="$(git -C "$R" rev-parse --short HEAD)"
  printf 'b1\n' >"$R/src/b.txt"; git -C "$R" commit -q -am "V: b0 -> b1"; V="$(git -C "$R" rev-parse --short HEAD)"
  printf 'c\n' >"$R/src/c.txt"; git -C "$R" add src/c.txt; git -C "$R" commit -q -m head; HEADSHA="$(git -C "$R" rev-parse HEAD)"
  printf '# arm  sections  platforms  shas (child first)\ndrop-U  css-text        web,ios   %s\ndrop-V  css-text-decor  android   %s   # second arm\n' "$U" "$V" >"$S/arms.$(basename "$R").txt"
}
blocks() { grep -c "^== wave55-ab-$2 " "$1/tools/titan/results/wave55-gate/build-hashes.txt"; }
runner() {   # <runner> <repo> <pool> <mode…>  → output in $S/<repo>.out, rc in $RRC
  local rn="$1" R="$2" P="$3"; shift 3
  AB_ROOT="$R" TITAN_DEVICE_POOL_DIR="$P" AB_POLL_SEC=1 AB_WAIT_MAX_SEC=20 bash "$rn" "$S/arms.$(basename "$R").txt" "$@" >"$S/$(basename "$R").out" 2>&1; RRC=$?
}
pool() { rm -rf "$1"; mkdir -p "$1"; echo emulator-stub >"$1/provisioned-android"; echo SIM-STUB >"$1/provisioned-ios"; }
# the T1 assertions, reused to show each mutant RED
t1_checks() {   # <repo> <pool> <rc> → prints the number of failed checks
  local R="$1" P="$2" rc="$3" f=0 G="$1/tools/titan/results/wave55-gate"
  [[ "$rc" == 0 ]] || f=$((f + 1))
  [[ "$(git -C "$R" rev-parse HEAD)" == "$HEADSHA" && "$(git -C "$R" symbolic-ref --short HEAD)" == main ]] || f=$((f + 1))
  [[ "$(git -C "$R" for-each-ref refs/heads --format='%(refname:short)')" == main ]] || f=$((f + 1))
  for a in drop-U drop-V; do
    [[ "$(blocks "$R" "$a")" == 1 ]] || f=$((f + 1))
    [[ -s "$G/final/ab-$a.txt" && -s "$G/final/ab-$a.driver.txt" ]] || f=$((f + 1))
  done
  [[ "$(grep -c '^== ' "$G/build-hashes.txt")" == 3 ]] || f=$((f + 1))             # the opening block + one per arm, nothing more
  [[ ! -e "$P/provisioned-android" && ! -e "$P/provisioned-ios" ]] || f=$((f + 1))
  echo "$f"
}

echo; echo "## T1. --run, two arms (drop-U, drop-V)"
make_repo "$S/r1"; pool "$S/pool1"; runner "$RUNNER" "$S/r1" "$S/pool1" --run
G1="$S/r1/tools/titan/results/wave55-gate"
check "T1 exit 0 (got $RRC)" test "$RRC" = 0
check "T1 HEAD restored: main at the head commit" test "$(git -C "$S/r1" rev-parse HEAD)-$(git -C "$S/r1" symbolic-ref --short HEAD)" = "$HEADSHA-main"
check "T1 the arm branches are gone (refs/heads = main)" test "$(git -C "$S/r1" for-each-ref refs/heads --format='%(refname:short)')" = main
for a in drop-U drop-V; do
  check "T1 '== wave55-ab-$a' block exactly once in build-hashes.txt ($(blocks "$S/r1" $a))" test "$(blocks "$S/r1" $a)" = 1
  check "T1 final/ab-$a.txt and final/ab-$a.driver.txt written" test -s "$G1/final/ab-$a.txt" -a -s "$G1/final/ab-$a.driver.txt"
done
check "T1 build-hashes.txt = the opening block + exactly one block per arm ($(grep -c '^== ' "$G1/build-hashes.txt") '== ' lines)" test "$(grep -c '^== ' "$G1/build-hashes.txt")" = 3
check "T1 drop-U's driver ran on the U-reverted tree (branch wave55-ab-drop-U, a0, b1)" test "$(tr '\n' ' ' <"$S/r1/tools/titan/runs/wave55-ab-drop-U/tree.txt")" = "wave55-ab-drop-U a0 b1 "
check "T1 drop-V's driver ran on the V-reverted tree (branch wave55-ab-drop-V, a1, b0)" test "$(tr '\n' ' ' <"$S/r1/tools/titan/runs/wave55-ab-drop-V/tree.txt")" = "wave55-ab-drop-V a1 b0 "
check "T1 each hash block was read on its arm tree (HEAD in the block != the head commit)" bash -c "! grep -q 'HEAD $(git -C "$S/r1" rev-parse --short HEAD)\$' '$G1/build-hashes.txt'"
check "T1 the stub pool's provisioned-* markers removed" test ! -e "$S/pool1/provisioned-android" -a ! -e "$S/pool1/provisioned-ios"
check "T1 the only tracked change is build-hashes.txt (the records await the orchestrator's commit)" test "$(git -C "$S/r1" status --porcelain --untracked-files=no)" = " M tools/titan/results/wave55-gate/build-hashes.txt"
check "T1 the records were written after the last restore (runner log order)" bash -c "awk '/tree restored/{r=NR} /read-out \\(/{if(!w)w=NR} END{exit !(w>r && r>0)}' '$S/r1.out'"
check "T1 the reusable T1 check set reads 0 failures on the real runner" test "$(t1_checks "$S/r1" "$S/pool1" "$RRC")" = 0

echo; echo "## T2. M-L18 — the wave-54 order (records on the arm branch, then git checkout -f)"
mkdir -p "$S/mut"
python3 - "$RUNNER" "$S/mut/ab-arms55.M-L18.sh" <<'EOF'
import sys
s = open(sys.argv[1]).read()
a = '  restore_tree "$i"                                              # STEP-RESTORE'
b = 'write_records                                                    # STEP-RECORDS'
assert s.count(a) == 1 and s.count(b) == 1
s = s.replace(a, '  write_records; restore_tree "$i"   # MUTATION M-L18: records on the arm branch, before the restore').replace(b, ':   # MUTATION M-L18: no records after the loop')
open(sys.argv[2], 'w').write(s)
EOF
make_repo "$S/r2"; pool "$S/pool2"; runner "$S/mut/ab-arms55.M-L18.sh" "$S/r2" "$S/pool2" --run
n="$(t1_checks "$S/r2" "$S/pool2" "$RRC")"
check "T2 M-L18 is RED: $n T1 check(s) fail (exit $RRC; drop-U blocks $(blocks "$S/r2" drop-U), drop-V blocks $(blocks "$S/r2" drop-V))" test "$n" -gt 0 -a "$(blocks "$S/r2" drop-U)" = 0
check "T2 … and the read-outs survived (untracked), exactly as in wave 54" test -s "$S/r2/tools/titan/results/wave55-gate/final/ab-drop-U.txt"

echo; echo "## T3. M-per-arm — the records after EACH restore (the brief's literal step 8), two arms"
python3 - "$RUNNER" "$S/mut/ab-arms55.M-per-arm.sh" <<'EOF'
import sys
s = open(sys.argv[1]).read()
a = '  restore_tree "$i"                                              # STEP-RESTORE'
b = 'write_records                                                    # STEP-RECORDS'
c = 'verify_records                                                   # STEP-VERIFY'
assert s.count(a) == 1 and s.count(b) == 1 and s.count(c) == 1
s = s.replace(a, '  restore_tree "$i"; write_records   # MUTATION M-per-arm').replace(b, ':   # MUTATION M-per-arm')
open(sys.argv[2], 'w').write(s)
EOF
make_repo "$S/r3"; pool "$S/pool3"; runner "$S/mut/ab-arms55.M-per-arm.sh" "$S/r3" "$S/pool3" --run
n="$(t1_checks "$S/r3" "$S/pool3" "$RRC")"
check "T3 M-per-arm is RED: $n T1 check(s) fail (exit $RRC; $(grep -m1 -E 'not at|RESTORE NOT VERIFIED' "$S/r3.out" | cut -c1-110))" test "$n" -gt 0 -a "$RRC" != 0

echo; echo "## T4. the dry run: default mode and --dry-run leave refs and files byte-identical; M-dry turns that red"
make_repo "$S/r4"; pool "$S/pool4"; before="$(repo_state "$S/r4")"
runner "$RUNNER" "$S/r4" "$S/pool4"; rc_default=$RRC
check "T4 no mode flag = the dry run: exit 0, 'DRY RUN OK', refs + files identical" test "$rc_default-$(grep -c 'DRY RUN OK' "$S/r4.out")-$(repo_state "$S/r4")" = "0-1-$before"
runner "$RUNNER" "$S/r4" "$S/pool4" --dry-run
check "T4 --dry-run: exit 0, refs + files identical, the pool markers untouched" test "$RRC-$(repo_state "$S/r4")-$(ls "$S/pool4" | tr '\n' ,)" = "0-$before-provisioned-android,provisioned-ios,"
check "T4 the dry run printed the driver's own --plan for both arms" test "$(grep -c 'driver --plan | run-id: wave55-ab-drop-' "$S/r4.out")" = 2
python3 - "$RUNNER" "$S/mut/ab-arms55.M-dry.sh" <<'EOF'
import sys
s = open(sys.argv[1]).read()
a = '    --dry-run) MODE=dry; shift ;;'
assert s.count(a) == 1 and s.count('MODE=dry; AGAINST=') == 1
s = s.replace(a, '    --dry-run) MODE=run; shift ;;   # MUTATION M-dry').replace('MODE=dry; AGAINST=', 'MODE=run; AGAINST=')
open(sys.argv[2], 'w').write(s)
EOF
runner "$S/mut/ab-arms55.M-dry.sh" "$S/r4" "$S/pool4" --dry-run
check "T4 M-dry is RED: the identity check sees the changed refs/files (exit $RRC)" test "$(repo_state "$S/r4")" != "$before"

echo; echo "## T5. refusals: exit 2 and nothing touched"
make_repo "$S/r5"; pool "$S/pool5"
refusal() {   # <label> <wanted text> [runner args…]
  local label="$1" want="$2"; shift 2; local st; st="$(repo_state "$S/r5")"
  runner "$RUNNER" "$S/r5" "$S/pool5" "$@"
  check "$label — exit $RRC, '$(grep -m1 -F -- "$want" "$S/r5.out" | sed 's/^ *- //' | cut -c1-90)'" test "$RRC" = 2 -a "$(grep -c -F -- "$want" "$S/r5.out")" -ge 1 -a "$(repo_state "$S/r5")" = "$st"
}
printf 'dirty\n' >>"$S/r5/src/c.txt"
refusal "T5a tracked change, --run" "tracked changes in the tree" --run
git -C "$S/r5" checkout -q -- src/c.txt
printf 'untracked\n' >"$S/r5/untracked.txt"
runner "$RUNNER" "$S/r5" "$S/pool5" --dry-run
check "T5b an UNTRACKED file outside the code paths does not block — exit $RRC" test "$RRC" = 0
rm -f "$S/r5/untracked.txt"
mkdir -p "$S/r5/runtimes"; printf 'stray\n' >"$S/r5/runtimes/StrayLaneFile.kt"
refusal "T5b2 an UNTRACKED file under a code path (runtimes/) refuses — a device run would build it" "code differs from HEAD" --run
rm -rf "$S/r5/runtimes"
git -C "$S/r5" checkout -q --detach
refusal "T5c detached HEAD, --run" "detached HEAD" --run
git -C "$S/r5" checkout -q main
cp "$S/arms.r5.txt" "$S/arms.r5.keep"
git -C "$S/r5" checkout -q -b side "$U"; printf 'side\n' >"$S/r5/src/side.txt"; git -C "$S/r5" add src/side.txt; git -C "$S/r5" commit -q -m side; SIDE="$(git -C "$S/r5" rev-parse --short HEAD)"; git -C "$S/r5" checkout -q main
printf 'drop-S css-text web %s\n' "$SIDE" >"$S/arms.r5.txt";            refusal "T5d a sha that is not an ancestor of HEAD" "is not an ancestor of HEAD" --run
printf 'drop-UV css-text web %s %s\n' "$U" "$V" >"$S/arms.r5.txt";      refusal "T5e shas parent first (must be child first)" "list the shas child first" --run
printf 'drop-U css-text web %s\ndrop-U css-text web %s\n' "$U" "$V" >"$S/arms.r5.txt"; refusal "T5f a duplicate arm name (expectations abArms has two drop-U2)" "duplicate arm name" --run
printf 'drop-U css-nope web %s\n' "$U" >"$S/arms.r5.txt";               refusal "T5g a section the driver does not know" "is not one of gate-driver.sh's corpus sections" --run
printf 'drop-U css-text mac %s\n' "$U" >"$S/arms.r5.txt";               refusal "T5h a platform that is not web / ios / android" "is not web / ios / android" --run
mkdir -p "$S/r5/tools/titan/runs/wave55-ab-drop-U"; cp "$S/arms.r5.keep" "$S/arms.r5.txt"
refusal "T5i the arm's run dir already exists (never overwrite a capture)" "already exists (never overwrite" --run
rm -rf "$S/r5/tools/titan/runs/wave55-ab-drop-U"
printf 'a2\n' >"$S/r5/src/a.txt"; git -C "$S/r5" commit -q -am "W: a1 -> a2"
printf 'drop-U css-text web %s\n' "$U" >"$S/arms.r5.txt";               refusal "T5j a revert that does not apply to HEAD (W rewrote U's line)" "does not apply to" --run
git -C "$S/r5" branch -q wave55-ab-drop-U; refusal "T5k a branch with the arm's run name already exists" "already exists (checkout -B would reset it)" --run
# round 2 (M1): the arm INCLUDES W (W rewrote U's line; W's parent is not U) — the cumulative reverse applies
W="$(git -C "$S/r5" rev-parse --short HEAD)"
printf 'drop-UW css-text web %s %s\n' "$W" "$U" >"$S/arms.r5.txt"; runner "$RUNNER" "$S/r5" "$S/pool5" --dry-run
check "T5l 'drop-UW W U' (child first; W rewrites U's line; not a chain) dry-runs exit 0 — $(grep -m1 'revert check' "$S/r5.out" | sed 's/^ *//' | cut -c1-90)" test "$RRC" = 0
printf 'n0\n' >"$S/r5/src/n.txt"; git -C "$S/r5" add src/n.txt; git -C "$S/r5" commit -q -m "N: creates n.txt"; N="$(git -C "$S/r5" rev-parse --short HEAD)"
printf 'y\n' >"$S/r5/src/y.txt"; git -C "$S/r5" add src/y.txt; git -C "$S/r5" commit -q -m "Y: unrelated"
printf 'n1\n' >>"$S/r5/src/n.txt"; git -C "$S/r5" commit -q -am "B: extends n.txt"; B="$(git -C "$S/r5" rev-parse --short HEAD)"
printf 'drop-NB css-text web %s %s\n' "$B" "$N" >"$S/arms.r5.txt"; runner "$RUNNER" "$S/r5" "$S/pool5" --dry-run
check "T5m 'drop-NB B N' (N creates n.txt, B extends it; not a chain) dry-runs exit 0" test "$RRC" = 0
python3 - "$RUNNER" "$S/mut/ab-arms55.M-onebyone.sh" <<'EOF'
import sys
s = open(sys.argv[1]).read(); a = '    local X f c\n'
assert s.count(a) == 1
s = s.replace(a, '    for c in "${s[@]}"; do gitr diff --binary "$c^" "$c" | gitr apply -R --check 2>&1 || return 1; done; return 0   # MUTATION M-onebyone (round 1)\n    local X f c\n', 1)
open(sys.argv[2], 'w').write(s)
EOF
runner "$S/mut/ab-arms55.M-onebyone.sh" "$S/r5" "$S/pool5" --dry-run
check "T5m M-onebyone is RED on 'drop-NB B N' (each reverse alone: N's deletion does not apply over B) — exit $RRC" test "$RRC" = 2
printf 'drop-UW css-text web %s %s\n' "$W" "$U" >"$S/arms.r5.txt"; runner "$S/mut/ab-arms55.M-onebyone.sh" "$S/r5" "$S/pool5" --dry-run
check "T5l M-onebyone is RED on 'drop-UW W U' — exit $RRC" test "$RRC" = 2

echo; echo "## T6. a failing driver (drop-V rc 1) and a driver that never reaches attempt 1 (drop-U)"
make_repo "$S/r6"; pool "$S/pool6"
STUB_FAIL_ARM=drop-V runner "$RUNNER" "$S/r6" "$S/pool6" --run
check "T6a driver rc 1 on drop-V → exit 1, both arms recorded once, tree restored" test "$RRC-$(blocks "$S/r6" drop-U)-$(blocks "$S/r6" drop-V)-$(git -C "$S/r6" rev-parse HEAD)" = "1-1-1-$HEADSHA"
make_repo "$S/r7"; pool "$S/pool7"
STUB_NO_START=drop-U runner "$RUNNER" "$S/r7" "$S/pool7" --run
check "T6b no 'attempt 1 starting' on drop-U → a NOT READ block (once), exit 1, drop-V read normally" test "$RRC-$(grep -c '^== wave55-ab-drop-U .*NOT READ' "$S/r7/tools/titan/results/wave55-gate/build-hashes.txt")-$(blocks "$S/r7" drop-V)" = "1-1-1"

echo; echo "## T7. outside the throwaway repos"
check "T7 this repo's refs, worktrees and stash unchanged" test "$(this_repo)" = "$THIS_BEFORE"
real_pool >"$S/real-pool.after.txt"; printf '%s\n' "$POOL_BEFORE" >"$S/real-pool.before.txt"
check "T7 the real /tmp/titan-device-pool unchanged (listing with mtimes, $(grep -c . "$S/real-pool.before.txt") lines)" test "$(real_pool)" = "$POOL_BEFORE"
diff "$S/real-pool.before.txt" "$S/real-pool.after.txt" | sed 's/^/     /'
echo; echo "ab-arms55.selftest: $([ $FAILS -eq 0 ] && echo 'every check as required (mutants M-L18, M-per-arm, M-dry RED)' || echo "$FAILS check(s) NOT as required")"
exit $FAILS
