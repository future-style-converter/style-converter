#!/usr/bin/env bash
# tools/titan/results/wave55-gate/ab-arms55.sh — the wave-55 A/B arm runner (L7 U3, instruments.md §2.3 / §4 U3; wave-54 lesson
# L18 "An A/B arm runner that switches branches must write its records after the tree is restored").
#
# Why a new runner. wave54-gate/ab-arms54.sh ran installed-build-hash.sh ON the throwaway arm branch; that script `tee -a`s its
# block into build-hashes.txt (a TRACKED file), and the runner then restored the tree with `git checkout -f`, which discarded the
# three blocks (build-hashes.txt:45-56 is marked RECONSTRUCTED). Here every record is written only AFTER the tree is back on the
# branch and the restore is VERIFIED. Each arm's installed-build block is captured from the hash script's stdout into a stage dir
# OUTSIDE the tree; its own in-tree `tee -a` lands on the arm branch and is discarded by the restore, as it must be.
#
# One step beyond the brief (instruments.md §4 U3 step 8 appends per arm): with two or more arms a per-arm append would sit in the
# tracked build-hashes.txt while the NEXT arm runs, and that arm's `git checkout -f` would discard it again (L18 in another
# place; ab-arms55.selftest.sh mutation M-per-arm shows it). So the loop only runs arms and restores; ALL records (the hash
# blocks, the driver outputs, the ab-diff read-outs) are written once, after the LAST verified restore, then verified.
#
# Usage: ab-arms55.sh <arms-file> [--dry-run | --run] [--against <run>]
#   <arms-file>  one arm per line: `<arm> <sections,> <platforms,> <sha…>` (shas child first, as ab-arms54.sh:16-21);
#                `#` comments and blank lines are skipped. Arm names must be unique and must not reuse a run dir, a branch,
#                a read-out or a build-hashes label of an earlier arm (the toolkit-stage expectations.json named TWO arms
#                "drop-U2", L4's probe-time arm and L6's closing arm; since plan-skeptic round 1 (D28) they are drop-L4-U2 and
#                drop-L6-U2, run ids wave55-ab-drop-L4-U2 / wave55-ab-drop-L6-U2, and plan-build.py asserts unique names).
#                ONE --against per invocation: an arms file holds only arms whose expectations.json `against` is that run
#                (drop-L4-U2 → wave55-pre; drop-NB-ios, drop-L6-U2 → wave55-final).
#   --dry-run    THE DEFAULT. Every precondition below is checked and every refusal is listed (not just the first); the plan
#                is printed with the gate driver's own `--plan`; nothing is written anywhere (git reads use
#                --no-optional-locks; the revert check is `git apply -R --check`). Exit 0 = `--run` would start, 2 = refused.
#   --run        the arms (an orchestrator window: devices, the quiet host, after the run they are read against).
#   --against    the include run of the read-out (default wave55-final; L4's probe-time arm is read against wave55-pre).
#                Plan-skeptic round 1 (process-holes S5): the against run must have run THIS tree's code. Its installed-build
#                block in build-hashes.txt (`== <against> … — HEAD <sha>`) names that tree; the runner REFUSES when HEAD
#                differs from it in code (runtimes, apps, converter, tools/titan outside tools/titan/results, *.md excluded:
#                those commits would be read as the dropped unit's Δ), when the block says "+ uncommitted", or when the run
#                directory exists with no block (a run without its hash is a score, not evidence). A run not captured yet is
#                only noted (the arms file can be dry-run before it).
#                Plan-skeptic round 2 (instrument-teeth S4): the block read is the run's CORPUS-LAUNCH block(s) — `== <against>
#                corpus launch …`, the FIRST one names the tree — never the last block (the --skip-corpus net launch's read is
#                the race by design: wave55-open's names another HEAD and reads ios MISMATCH). Refused: no corpus-launch block,
#                two corpus-launch blocks naming different trees, "+ uncommitted" on any, or a MISMATCH / MISSING line on any
#                for a platform an arm reads (the installed build was not the tree's build).
#   Revert check (round 2, process-holes M1): a non-chain sha list is reverse-applied CUMULATIVELY, child first, in a private
#                mktemp export of the touched files outside the tree (land-units.sh's C12 pattern) — each commit's reverse
#                checked ALONE against the current tree refused every arm whose unit carries an -S1 fix or a landed
#                contingency (W rewrites U's line; NB-ios-b extends the file NB-ios creates). The arms file lists the
#                resolved shas in `git log` order (newest first), whatever order `drops` names them in.
#   Untracked code (round 2, process-holes S1): an UNTRACKED file under runtimes / apps / converter / tools/titan (results
#                and *.md excepted) or a modified root Package.swift refuses the run, as a tracked edit does: the arm would
#                build it.
# Environment:
#   AB_ROOT                the checkout to run in (default: the checkout holding this script).
#   TITAN_DEVICE_POOL_DIR  the pool whose provisioned-* markers are removed before each arm, forcing a rebuild + reinstall
#                          from the arm's tree (default /tmp/titan-device-pool — the ONLY pool gate-driver.sh,
#                          provision-devices.sh and installed-build-hash.sh read; another value is for the self-test).
#   AB_POLL_SEC / AB_WAIT_MAX_SEC  how often / how long to wait for `attempt 1 starting` (15 / 1800: provisioning alone is
#                          bounded at 25 min by gate-driver.sh).
# Per arm (--run): 1 record build-hashes.txt's sha256 and size; 2 `git checkout -B wave55-ab-<arm> <BASE>` + `git revert`;
#   3 the driver (`gate-driver.sh wave55-ab-<arm> --sections … --skip-fixture-net`) with its output in the stage dir;
#   4 after `attempt 1 starting`, installed-build-hash.sh with its stdout to the stage dir; 5 wait for the driver, keep its rc;
#   6 RESTORE `git checkout -f <BRANCH>` + `git branch -D`; 7 VERIFY: HEAD == BASE on BRANCH, tracked-clean, build-hashes.txt
#   sha256 == the pre-arm one (an UNTRACKED build-hashes.txt that kept the in-arm append is first truncated to its pre-arm size).
# After the last arm: 8 write the records (append the staged blocks to build-hashes.txt; final/ab-<arm>.driver.txt;
#   `ab-diff.mjs wave55-ab-<arm> <against> --threshold 0.002 --platforms <p>` → final/ab-<arm>.txt); 9 verify them.
# Exit: 0 every arm OK · 1 an arm's driver rc ≠ 0 or no hash read (the other arms still run) · 2 refused, nothing touched ·
#   3 a revert did not apply (that arm skipped, tree restored) · 6 a restore not verified (records left in the stage dir, path
#   printed; nothing written in the tree) · 7 records written but not verified · 130 interrupted (tree restored).
# Self-test: ab-arms55.selftest.sh (a throwaway `git init` repository; stubs for the driver, the hash script and ab-diff).
set -uo pipefail

WAVE=wave55
HERE="$(cd "$(dirname "$0")" && pwd -P)"
AB_ROOT="$(cd "${AB_ROOT:-$HERE/../../../..}" && pwd -P)"
POOL="${TITAN_DEVICE_POOL_DIR:-/tmp/titan-device-pool}"
POLL="${AB_POLL_SEC:-15}"; WAIT_MAX="${AB_WAIT_MAX_SEC:-1800}"
G_REL=tools/titan/results/$WAVE-gate; G="$AB_ROOT/$G_REL"; OUT="$G/final"
HASHES_REL="$G_REL/build-hashes.txt"; HASHES="$AB_ROOT/$HASHES_REL"
DRIVER="$AB_ROOT/tools/titan/gate-driver.sh"; HASHER="$G/installed-build-hash.sh"
ABDIFF_REL=tools/titan/results/wave52-gate/ab-diff.mjs
PLATS_OK=" web ios android "

MODE=dry; AGAINST="$WAVE-final"; ARMS_FILE=""
while (( $# )); do
  case "$1" in
    --dry-run) MODE=dry; shift ;;
    --run) MODE=run; shift ;;
    --against) AGAINST="${2:?--against needs a run id}"; shift 2 ;;
    -h|--help) sed -n '2,65p' "$0"; exit 0 ;;   # the whole header (round 3: it had drifted to 2-46)
    -*) echo "ab-arms55: unknown flag $1" >&2; exit 2 ;;
    *) [[ -z "$ARMS_FILE" ]] || { echo "ab-arms55: unexpected arg $1" >&2; exit 2; }; ARMS_FILE="$1"; shift ;;
  esac
done
[[ -n "$ARMS_FILE" && -f "$ARMS_FILE" ]] || { echo "usage: ab-arms55.sh <arms-file> [--dry-run | --run] [--against <run>]" >&2; exit 2; }
ARMS_FILE="$(cd "$(dirname "$ARMS_FILE")" && pwd -P)/$(basename "$ARMS_FILE")"
cd "$AB_ROOT" || exit 2
gitr() { git --no-optional-locks -C "$AB_ROOT" "$@"; }       # reads never refresh the index (the tree may be mid-gate)
sha256() { shasum -a 256 "$1" 2>/dev/null | cut -d' ' -f1; }
fsize() { wc -c < "$1" 2>/dev/null | tr -d ' '; }
tracked_dirty() { gitr status --porcelain --untracked-files=no; }
CODE_PATHS=(runtimes apps converter tools/titan Package.swift ':(exclude)tools/titan/results' ':(exclude)*.md')
code_dirty() { gitr status --porcelain --untracked-files=all -- "${CODE_PATHS[@]}"; }    # round 2 (S1): untracked code too

# ── Preconditions (both modes): every refusal is collected; --run refuses on any, before touching anything ──
REFUSALS=()
refuse() { REFUSALS+=("$*"); }
[[ "$(gitr rev-parse --show-toplevel 2>/dev/null)" == "$AB_ROOT" ]] || refuse "AB_ROOT $AB_ROOT is not the top of a git work tree"
BRANCH="$(gitr symbolic-ref --short -q HEAD)"; [[ -n "$BRANCH" ]] || refuse "detached HEAD: there is no branch to restore to"
BASE="$(gitr rev-parse -q --verify HEAD)"; BASE_S="$(gitr rev-parse --short HEAD 2>/dev/null)"
DIRTY="$(tracked_dirty)"; [[ -z "$DIRTY" ]] || refuse "tracked changes in the tree (git status --porcelain --untracked-files=no): $(tr '\n' ' ' <<<"$DIRTY" | cut -c1-200)"
CDIRTY="$(code_dirty)"; [[ -z "$CDIRTY" ]] || refuse "code differs from HEAD (tracked, staged or UNTRACKED; results and *.md excepted): $(tr '\n' ' ' <<<"$CDIRTY" | cut -c1-200) — a device run would build it"
[[ -x "$DRIVER" ]] || refuse "no executable $DRIVER"
[[ -f "$HASHER" ]] || refuse "no $HASHER"
[[ -f "$HASHES" ]] || refuse "no $HASHES (the record the arms append to)"
[[ -f "$AB_ROOT/$ABDIFF_REL" ]] || refuse "no $ABDIFF_REL (the read-out)"
[[ -f "$AB_ROOT/tools/titan/own-processes.sh" ]] || refuse "no tools/titan/own-processes.sh (beforeEveryDeviceRun)"
SECTIONS_OK=" $(sed -n 's/^DEFAULT_SECTIONS="\(.*\)"$/\1/p' "$DRIVER" 2>/dev/null) "
[[ "$SECTIONS_OK" != "  " ]] || refuse "could not read DEFAULT_SECTIONS from $DRIVER"

NAMES=(); SECS=(); PLATS=(); SHAS=(); SHAS_OK=()
n=0
while IFS= read -r line || [[ -n "$line" ]]; do
  line="${line%%#*}"; read -r arm secs plats shas <<<"$line"; [[ -n "${arm:-}" ]] || continue
  n=$((n + 1)); run="$WAVE-ab-$arm"
  [[ "$arm" =~ ^[A-Za-z0-9][A-Za-z0-9._-]*$ ]] || refuse "arm $n: bad name '$arm'"
  for prev in ${NAMES[@]+"${NAMES[@]}"}; do [[ "$prev" != "$arm" ]] || refuse "arm $n: duplicate arm name $arm (its run dir, branch, read-out and hash label would collide)"; done
  [[ -n "${shas:-}" ]] || { refuse "arm $arm: needs <sections,> <platforms,> <sha…>"; NAMES+=("$arm"); SECS+=(""); PLATS+=(""); SHAS+=(""); SHAS_OK+=(0); continue; }
  for s in ${secs//,/ }; do [[ "$SECTIONS_OK" == *" $s "* ]] || refuse "arm $arm: section $s is not one of gate-driver.sh's corpus sections"; done
  for p in ${plats//,/ }; do [[ "$PLATS_OK" == *" $p "* ]] || refuse "arm $arm: platform $p is not web / ios / android"; done
  [[ ! -e "$AB_ROOT/tools/titan/runs/$run" ]] || refuse "arm $arm: tools/titan/runs/$run already exists (never overwrite an arm's capture)"
  gitr show-ref -q --verify "refs/heads/$run" && refuse "arm $arm: branch $run already exists (checkout -B would reset it)"
  for f in "$OUT/ab-$arm.txt" "$OUT/ab-$arm.driver.txt"; do [[ ! -e "$f" ]] || refuse "arm $arm: ${f#$AB_ROOT/} already exists"; done
  [[ -f "$HASHES" ]] && grep -q "^== $run " "$HASHES" && refuse "arm $arm: build-hashes.txt already has a '== $run' block"
  prevsha=""; shok=1
  for s in $shas; do
    full="$(gitr rev-parse -q --verify "$s^{commit}")" || { refuse "arm $arm: $s is not a commit"; shok=0; continue; }
    gitr merge-base --is-ancestor "$full" HEAD 2>/dev/null || { refuse "arm $arm: $s is not an ancestor of HEAD (nothing to revert on this branch)"; shok=0; }
    gitr rev-parse -q --verify "$full^" >/dev/null || { refuse "arm $arm: $s is a root commit"; shok=0; }
    if [[ -n "$prevsha" ]]; then   # child first: each later sha must be an ancestor of the one before it
      gitr merge-base --is-ancestor "$full" "$prevsha" 2>/dev/null && [[ "$full" != "$prevsha" ]] || { refuse "arm $arm: $s is not older than the sha before it (list the shas child first)"; shok=0; }
    fi
    prevsha="$full"
  done
  NAMES+=("$arm"); SECS+=("$secs"); PLATS+=("$plats"); SHAS+=("$shas"); SHAS_OK+=("$shok")
done < "$ARMS_FILE"
(( n > 0 )) || refuse "no arm in $ARMS_FILE"

# The --against tree (round 1, S5; round 2, instrument-teeth S4): the read-out's Δ = against − arm is the dropped unit's ONLY if
# against's CORPUS launch ran this tree's code and installed it. Every `== <against> corpus launch` block is read (blocks end at
# a blank line); the FIRST names the tree; the net-launch block (the race) is never read.
AGAINST_BLOCKS="$(awk -v pfx="== $AGAINST corpus launch" 'index($0, pfx) == 1 { on = 1; n++; print "@@BLOCK " n } on && /^$/ { on = 0 } on { print }' "$HASHES" 2>/dev/null)"
AGAINST_LINE="$(grep -m1 "^== $AGAINST corpus launch" <<<"$AGAINST_BLOCKS")"
AGAINST_HEAD="$(sed -n 's/.* — HEAD \([0-9a-f]\{7,40\}\).*/\1/p' <<<"$AGAINST_LINE")"
ARM_PLATS=" $(tr ',' ' ' <<<"${PLATS[*]:-}") "
if [[ -n "$AGAINST_HEAD" ]]; then
  heads="$(grep "^== $AGAINST corpus launch" <<<"$AGAINST_BLOCKS" | sed -n 's/.* — HEAD \([0-9a-f]\{7,40\}\).*/\1/p' | sort -u | tr '\n' ' ')"
  bad_inst=""
  for p in web ios android; do
    [[ "$ARM_PLATS" == *" $p "* && "$p" != web ]] || continue
    grep -qE "^$p +MISMATCH" <<<"$AGAINST_BLOCKS" && bad_inst+=" $p MISMATCH;"
    grep -E "^$p " <<<"$AGAINST_BLOCKS" | grep -q "=MISSING" && bad_inst+=" $p MISSING;"
  done
  if [[ "$(wc -w <<<"$heads")" -gt 1 ]]; then refuse "--against $AGAINST: its corpus-launch blocks name different trees ($heads)"
  elif grep -q "+ uncommitted" <<<"$AGAINST_BLOCKS"; then refuse "--against $AGAINST ran with uncommitted code (a corpus-launch block says so): its tree is not a commit"
  elif [[ -n "$bad_inst" ]]; then refuse "--against $AGAINST: its corpus-launch block reads$bad_inst for a platform an arm reads — the installed build was not that tree's build"
  elif ! gitr rev-parse -q --verify "$AGAINST_HEAD^{commit}" >/dev/null; then refuse "--against $AGAINST: its corpus-launch block names HEAD $AGAINST_HEAD, which is not a commit here"
  elif ! gitr diff --quiet "$AGAINST_HEAD" HEAD -- "${CODE_PATHS[@]}"; then
    refuse "--against $AGAINST ran tree $AGAINST_HEAD, but HEAD differs from it in code ($(gitr diff --shortstat "$AGAINST_HEAD" HEAD -- "${CODE_PATHS[@]}" | tr -s ' ')): the arm's Δ would carry those commits — run the arm before them, or read it against a re-probe of this tree"
  else echo "  against: $AGAINST's corpus launch ran tree $AGAINST_HEAD and installed it (MATCH) — no code difference to HEAD (runtimes, apps, converter, tools/titan outside results, Package.swift; *.md excluded)"; fi
elif [[ -d "$AB_ROOT/tools/titan/runs/$AGAINST" ]]; then
  refuse "--against $AGAINST: tools/titan/runs/$AGAINST exists but build-hashes.txt has no '== $AGAINST corpus launch …' block naming its HEAD (a run without its hash is a score, not evidence; a net-launch block is the race, never read)"
else echo "  against: $AGAINST has not run yet (no run dir, no block) — its tree is checked once it has"; fi

echo "ab-arms55 ($MODE) — root $AB_ROOT · branch ${BRANCH:-DETACHED} · BASE ${BASE_S:-?} · arms $n · read against $AGAINST · pool $POOL"
# The revert check: the reverse of each arm's commits must apply to the CURRENT tree (`git apply --check` writes nothing).
# A first-parent chain (each sha the parent of the one before) is checked as one combined patch; otherwise (round 2, M1) the
# reverses apply CUMULATIVELY, child first, to a private copy of the touched files in a mktemp dir outside the tree — what
# `git revert <child> … <parent>` does — never one by one against the current tree (a child that rewrites its parent's lines,
# or extends the file its parent created, made the parent's lone reverse fail).
# It runs for every arm whose shas are valid, whatever else was refused, so a dry run reports every problem at once.
revert_check() {  # <shas…>
  local -a s=("$@"); local i chain=1
  for (( i = 1; i < ${#s[@]}; i++ )); do [[ "$(gitr rev-parse -q --verify "${s[i-1]}^")" == "$(gitr rev-parse -q --verify "${s[i]}")" ]] || chain=0; done
  if (( chain )); then gitr diff --binary "${s[${#s[@]}-1]}^" "${s[0]}" | gitr apply -R --check 2>&1
  else
    local X f c
    X="$(mktemp -d "${TMPDIR:-/tmp}/ab-arms55-rc.XXXXXX")" || return 1
    case "$X/" in "$AB_ROOT"/*) echo "the revert-check dir $X is inside the tree"; rm -rf "$X"; return 1 ;; esac
    for c in "${s[@]}"; do
      while IFS= read -r f; do          # every touched path, copied once from the CURRENT tree (absent = created later / deleted)
        [[ -z "$f" || -e "$X/$f" || ! -e "$AB_ROOT/$f" ]] || { mkdir -p "$X/$(dirname "$f")"; cp -p "$AB_ROOT/$f" "$X/$f"; }
      done < <(gitr diff --name-only "$c^" "$c")
    done
    for c in "${s[@]}"; do              # child first: each reverse applies to the copy the previous ones left
      gitr diff --binary "$c^" "$c" | (cd "$X" && GIT_CEILING_DIRECTORIES="$X" git apply -R 2>&1) || { rm -rf "$X"; return 1; }
    done
    rm -rf "$X"
  fi
}
for (( i = 0; i < ${#NAMES[@]}; i++ )); do
  (( ${SHAS_OK[i]} )) || continue
  if msg="$(revert_check ${SHAS[i]})"; then echo "  revert check: arm ${NAMES[i]} — the reverse of ${SHAS[i]} applies to the tree at ${BASE_S:-?} (git apply -R --check)"
  else refuse "arm ${NAMES[i]}: the revert of ${SHAS[i]} does not apply to ${BASE_S:-HEAD}: $(tr '\n' ' ' <<<"$msg" | cut -c1-200)"; fi
done

[[ "$POOL" == /tmp/titan-device-pool ]] || echo "  NOTE: TITAN_DEVICE_POOL_DIR=$POOL is honoured by this runner only; gate-driver.sh, provision-devices.sh and installed-build-hash.sh read /tmp/titan-device-pool"
for (( i = 0; i < ${#NAMES[@]}; i++ )); do
  arm="${NAMES[i]}"; run="$WAVE-ab-$arm"
  echo "  arm $arm: git checkout -B $run $BASE_S; git revert --no-edit ${SHAS[i]}; rm -f $POOL/provisioned-*;"
  echo "      tools/titan/gate-driver.sh $run --sections ${SECS[i]} --skip-fixture-net  → \$STAGE/$arm.driver.txt"
  echo "      installed-build-hash.sh '$run (A/B arm …)' after 'attempt 1 starting' → \$STAGE/$arm.hash.txt; restore + verify;"
  echo "      after the last arm: >> $HASHES_REL, $G_REL/final/ab-$arm.driver.txt, ab-diff.mjs $run $AGAINST --threshold 0.002 --platforms ${PLATS[i]} → $G_REL/final/ab-$arm.txt"
  if [[ -n "${SECS[i]}" && -x "$DRIVER" ]]; then
    "$DRIVER" "$run" --sections "${SECS[i]}" --skip-fixture-net --plan 2>&1 | sed 's/^/      driver --plan | /' || refuse "arm $arm: gate-driver.sh --plan failed"
  fi
done
if (( ${#REFUSALS[@]} )); then
  echo "REFUSED (${#REFUSALS[@]}):"; printf '  - %s\n' "${REFUSALS[@]}"; exit 2
fi
if [[ "$MODE" == dry ]]; then echo "DRY RUN OK — nothing written; --run would start the $n arm(s) above"; exit 0; fi

# ── --run ───────────────────────────────────────────────────────────────────
STAGE="$(mktemp -d "${TMPDIR:-/tmp}/ab-arms55.XXXXXX" 2>/dev/null)" || { echo "REFUSED: cannot create a stage dir"; exit 2; }
case "$STAGE/" in "$AB_ROOT"/*) echo "REFUSED: the stage dir $STAGE is inside the tree"; rmdir "$STAGE"; exit 2 ;; esac
echo "stage dir (records live here until every arm is restored): $STAGE"
export PROJECT_ROOT="$AB_ROOT"; source "$AB_ROOT/tools/titan/own-processes.sh"
RC=0; CUR_RUN=""; DPID=""
STATUS=(); DRC=(); PRE_SHA=(); PRE_SIZE=(); ARM_TREE=(); LABEL=()

on_signal() {   # an interrupt mid-arm: stop the driver, put the tree back, keep the stage
  echo "INTERRUPTED — stopping the driver and restoring the tree; records stay in $STAGE"
  [[ -n "$DPID" ]] && kill "$DPID" 2>/dev/null
  gitr rev-parse -q --verify REVERT_HEAD >/dev/null && git revert --abort 2>/dev/null
  git checkout -q -f "$BRANCH" && [[ -n "$CUR_RUN" ]] && git branch -q -D "$CUR_RUN" 2>/dev/null
  exit 130
}
trap on_signal INT TERM

run_arm() {   # steps 1-5 for arm index $1
  local i="$1" arm="${NAMES[$1]}" run="$WAVE-ab-${NAMES[$1]}" waited=0
  [[ "$(gitr rev-parse HEAD)" == "$BASE" && "$(gitr symbolic-ref --short -q HEAD)" == "$BRANCH" && -z "$(tracked_dirty)" && -z "$(code_dirty)" ]] \
    || { echo "arm $arm: the tree is not at $BASE_S, clean, on $BRANCH before the arm — stopping (records in $STAGE)"; exit 6; }
  PRE_SHA[i]="$(sha256 "$HASHES")"; PRE_SIZE[i]="$(fsize "$HASHES")"                                  # step 1
  echo "=== $arm: revert ${SHAS[i]}, sections ${SECS[i]}, read platforms ${PLATS[i]} ==="
  kill_own_gradle_daemons "$AB_ROOT" >"$STAGE/$arm.gradle-daemons.txt" 2>&1                           # beforeEveryDeviceRun (never ./gradlew --stop)
  CUR_RUN="$run"
  git checkout -q -B "$run" "$BASE" || { STATUS[i]=checkout-failed; return 3; }                        # step 2
  if ! git revert --no-edit ${SHAS[i]} >"$STAGE/$arm.revert.txt" 2>&1; then
    git revert --abort 2>/dev/null; STATUS[i]=revert-failed; echo "arm $arm: revert failed (see $STAGE/$arm.revert.txt)"; return 3
  fi
  ARM_TREE[i]="$(git rev-parse --short HEAD)"
  LABEL[i]="$run (A/B arm of $WAVE: $arm = $BASE_S minus ${SHAS[i]}; sections ${SECS[i]}; arm tree ${ARM_TREE[i]}; read against $AGAINST)"
  echo "arm tree: ${ARM_TREE[i]}  ($(git log --format=%s -1 | cut -c1-80))"
  rm -f "$POOL"/provisioned-*                                                                         # force a rebuild + reinstall
  "$DRIVER" "$run" --sections "${SECS[i]}" --skip-fixture-net >"$STAGE/$arm.driver.txt" 2>&1 & DPID=$!  # step 3
  until grep -q 'attempt 1 starting' "$STAGE/$arm.driver.txt" 2>/dev/null; do                          # step 4
    kill -0 "$DPID" 2>/dev/null || break; (( waited >= WAIT_MAX )) && break; sleep "$POLL"; waited=$((waited + POLL))
  done
  if grep -q 'attempt 1 starting' "$STAGE/$arm.driver.txt"; then
    bash "$HASHER" "${LABEL[i]}" >"$STAGE/$arm.hash.txt" 2>"$STAGE/$arm.hash.stderr.txt"; echo "installed-build read: rc $? ($(grep -E '^(android|ios) +(MATCH|MISMATCH)' "$STAGE/$arm.hash.txt" | tr -s ' ' | tr '\n' ' '))"
  else echo "driver never reached 'attempt 1 starting' for $arm (waited ${waited}s)"; tail -8 "$STAGE/$arm.driver.txt"; fi
  wait "$DPID"; DRC[i]=$?; DPID=""                                                                     # step 5
  echo "driver rc=${DRC[i]}"; grep -E 'OK|FAIL|short|abort|REFUSED' "$STAGE/$arm.driver.txt" | tail -4 | cut -c1-160
  STATUS[i]=ran; return 0
}

restore_tree() {   # steps 6-7 for arm index $1
  local i="$1" run="$WAVE-ab-${NAMES[$1]}" bad=""
  git checkout -q -f "$BRANCH" && git branch -q -D "$run" 2>/dev/null                                  # step 6
  CUR_RUN=""
  if ! gitr ls-files --error-unmatch -- "$HASHES_REL" >/dev/null 2>&1 && [[ -n "${PRE_SIZE[i]:-}" ]]; then
    perl -e 'truncate($ARGV[0], $ARGV[1]) or die "truncate: $!"' "$HASHES" "${PRE_SIZE[i]}"          # an untracked record kept the append
  fi
  [[ "$(gitr rev-parse HEAD)" == "$BASE" ]] || bad+=" HEAD $(gitr rev-parse --short HEAD) != $BASE_S;"   # step 7
  [[ "$(gitr symbolic-ref --short -q HEAD)" == "$BRANCH" ]] || bad+=" not on $BRANCH;"
  [[ -z "$(tracked_dirty)" ]] || bad+=" tracked changes: $(tracked_dirty | tr '\n' ' ');"
  [[ -z "${PRE_SHA[i]:-}" || "$(sha256 "$HASHES")" == "${PRE_SHA[i]}" ]] || bad+=" build-hashes.txt differs from its pre-arm sha256;"
  gitr show-ref -q --verify "refs/heads/$run" && bad+=" branch $run still exists;"
  if [[ -n "$bad" ]]; then echo "RESTORE NOT VERIFIED after ${NAMES[i]}:$bad — nothing written in the tree; the records are in $STAGE"; exit 6; fi
  echo "tree restored to $BASE_S ($BRANCH), verified"
}

write_records() {   # step 8, for every arm that ran — only ever called after the last verified restore
  local i arm run
  mkdir -p "$OUT"
  for (( i = 0; i < ${#NAMES[@]}; i++ )); do
    [[ "${STATUS[i]:-}" == ran ]] || continue
    arm="${NAMES[i]}"; run="$WAVE-ab-$arm"
    if grep -q "^== $run " "$STAGE/$arm.hash.txt" 2>/dev/null; then cat "$STAGE/$arm.hash.txt" >>"$HASHES"
    else printf '== %s — NOT READ: the driver never reached "attempt 1 starting" (driver rc %s); this arm is a score, not evidence\n\n' "${LABEL[i]}" "${DRC[i]}" >>"$HASHES"; RC=$(( RC > 1 ? RC : 1 )); fi
    cp "$STAGE/$arm.driver.txt" "$OUT/ab-$arm.driver.txt"
    node "$ABDIFF_REL" "$run" "$AGAINST" --threshold 0.002 --platforms "${PLATS[i]}" >"$OUT/ab-$arm.txt" 2>&1
    echo "read-out ($G_REL/final/ab-$arm.txt):"; grep -vE '^\s*$' "$OUT/ab-$arm.txt" | head -24 | cut -c1-170
  done
}

verify_records() {   # step 9
  local i arm run bad=""
  for (( i = 0; i < ${#NAMES[@]}; i++ )); do
    [[ "${STATUS[i]:-}" == ran ]] || continue
    arm="${NAMES[i]}"; run="$WAVE-ab-$arm"
    [[ "$(grep -c "^== $run " "$HASHES")" == 1 ]] || bad+=" '== $run' block x$(grep -c "^== $run " "$HASHES") in build-hashes.txt (want 1);"
    [[ -s "$OUT/ab-$arm.txt" ]] || bad+=" final/ab-$arm.txt empty;"
    [[ -s "$OUT/ab-$arm.driver.txt" ]] || bad+=" final/ab-$arm.driver.txt empty;"
  done
  if [[ -n "$bad" ]]; then echo "RECORDS NOT VERIFIED:$bad (stage $STAGE)"; exit 7; fi
  echo "records verified: one block per arm in $HASHES_REL, read-outs under $G_REL/final/"
}

for (( i = 0; i < ${#NAMES[@]}; i++ )); do
  run_arm "$i"; arm_rc=$?                                        # STEP-ARM (steps 1-5)
  restore_tree "$i"                                              # STEP-RESTORE (steps 6-7; exit 6 when not verified)
  if (( arm_rc == 3 )); then RC=3
  elif (( ${DRC[i]:-0} != 0 )) && (( RC == 0 )); then RC=1; fi
done
write_records                                                    # STEP-RECORDS (step 8) — after the LAST verified restore
verify_records                                                   # STEP-VERIFY (step 9)
if [[ "$(gitr rev-parse HEAD)" == "$BASE" && "$(gitr symbolic-ref --short -q HEAD)" == "$BRANCH" ]]; then
  echo "ALL ARMS DONE (exit $RC) — tree at $BASE_S on $BRANCH; stage kept at $STAGE"
else echo "TREE NOT AT $BASE_S ON $BRANCH at the end"; exit 6; fi
exit "$RC"
