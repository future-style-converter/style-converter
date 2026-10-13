#!/usr/bin/env bash
# tools/titan/results/wave55-plan/fix-r3.guard-proof.sh — round 3 (plan-skeptic-round-3.verify.md M1): expectations.json
# `beforeEveryDeviceRun` is an EXECUTABLE bash string. Proves, on the installed expectations.json against the round-2 bytes:
#   1 `bash -n` (noexec: syntax only) — the round-2 string exits 2 (its trailing prose), the round-3 string 0;
#   2 the round-3 string run verbatim (`bash -c`) from the shared tree's root AND from `/` exits 0 (every path absolute,
#     PROJECT_ROOT exported); its kill_own_gradle_daemons summary line is printed (stderr);
#   3 it can still refuse (red): the same string with PROJECT_ROOT re-pointed at a throwaway worktree holding an UNTRACKED
#     runtime source exits 2, and with the disk floor raised to 100000 GiB exits 2 — neither reaches the daemon clause;
#   4 the generator's own assertion is red: plan-build.py with the round-2 trailing note appended to the string refuses
#     (AssertionError) in the throwaway, writing only under the scratch dir.
# Usage: bash fix-r3.guard-proof.sh <scratch-dir> <round-2 expectations.json copy>   (writes fix-r3.guard-proof.out.txt here)
set -uo pipefail
HERE="$(cd "$(dirname "$0")" && pwd -P)"; SH="$(cd "$HERE/../../../.." && pwd -P)"
S="${1:?scratch dir}"; mkdir -p "$S" && S="$(cd "$S" && pwd -P)"; OLDEXP="${2:?round-2 expectations.json}"
case "$S/" in "$SH"/*) echo "refusing: the scratch dir is inside this tree"; exit 2 ;; esac
exec > >(tee "$HERE/fix-r3.guard-proof.out.txt") 2>&1
NEW="$(jq -r .beforeEveryDeviceRun "$HERE/expectations.json")"; OLD="$(jq -r .beforeEveryDeviceRun "$OLDEXP")"
echo "# fix-r3.guard-proof.out.txt — $(date -u +%Y-%m-%dT%H:%M:%SZ); expectations.json sha256 $(shasum -a 256 "$HERE/expectations.json" | cut -c1-16)… (round 2: $(shasum -a 256 "$OLDEXP" | cut -c1-16)…); plan-build.py $(shasum -a 256 "$HERE/plan-build.py" | cut -c1-16)…; HEAD $(git -C "$SH" rev-parse --short HEAD)"
echo; echo "## the round-3 string (expectations.json beforeEveryDeviceRun)"; printf '%s\n' "$NEW"
echo; echo "## its note (beforeEveryDeviceRunNote)"; jq -r .beforeEveryDeviceRunNote "$HERE/expectations.json"
echo; echo "## 1. bash -n (noexec) on each string"
bash -n <(printf '%s' "$OLD"); echo "  round-2 string: bash -n exit $?   (want 2: the trailing prose note)"
bash -n <(printf '%s' "$NEW"); echo "  round-3 string: bash -n exit $?   (want 0)"
echo; echo "## 2. the round-3 string run verbatim"
echo "  Gradle daemons on the host before: [$(pgrep -f 'org.gradle.launcher.daemon.bootstrap.GradleDaemon ' | tr '\n' ' ')]"
( cd "$SH" && bash -c "$NEW" ); echo "  from the shared tree's root: exit $?   (want 0)"
( cd / && bash -c "$NEW" ); echo "  from /: exit $?   (want 0)"
echo "  Gradle daemons on the host after: [$(pgrep -f 'org.gradle.launcher.daemon.bootstrap.GradleDaemon ' | tr '\n' ' ')]"
echo; echo "## 3. red: the same string can refuse"
WT="$S/wave55-fix-r3-guard-wt"; git -C "$SH" worktree remove --force "$WT" 2>/dev/null; rm -rf "$WT"
git -C "$SH" worktree add -q --detach "$WT" HEAD && echo "  throwaway $(basename "$WT") at $(git -C "$WT" rev-parse --short HEAD)"
printf 'export const R3_UNTRACKED = 1;\n' >"$WT/runtimes/web/src/engine/core/R3UntrackedProbe.ts"
( cd "$WT" && bash -c "${NEW//$SH/$WT}" ); echo "  PROJECT_ROOT = the throwaway + one untracked runtime source: exit $?   (want 2)"
rm -f "$WT/runtimes/web/src/engine/core/R3UntrackedProbe.ts"
( cd "$WT" && bash -c "${NEW//$SH/$WT}" ) 2>/dev/null; echo "  the same throwaway, source removed: exit $?   (want 0: the refusal was the untracked file)"
( cd "$SH" && bash -c "${NEW/>= 20/>= 100000}" ); echo "  the shared tree, the disk floor at 100000 GiB: exit $?   (want 2)"
echo; echo "## 4. red: the generator refuses a string carrying prose (its bash -n assertion), in the throwaway"
ln -s "$SH/tools/titan/runs" "$WT/tools/titan/runs"      # the generator reads the wave54-final run (after step 3: an untracked symlink
                                                        # would itself trip clause 1)
rsync -a --delete "$HERE/" "$WT/tools/titan/results/wave55-plan/"
python3 - "$WT/tools/titan/results/wave55-plan/plan-build.py" <<'PY'
import sys
p = sys.argv[1]; s = open(p).read()
anchor = "grep -E \\'/wave55-\\')'),\n"
assert s.count(anchor) == 1, 'mutation anchor not found'
s = s.replace(anchor, "grep -E \\'/wave55-\\')   (never ./gradlew --stop; the /wave55- filter keeps other sessions\\' worktrees out, so no foreign process is stopped)'),\n")
open(p, 'w').write(s); print('  mutant: the round-2 trailing note re-appended to the string')
PY
mkdir -p "$S/mut-out"
( cd "$WT" && /opt/homebrew/bin/python3.12 -B tools/titan/results/wave55-plan/plan-build.py --out "$S/mut-out" >/dev/null 2>"$S/mut-out/err.txt" ); rc=$?
echo "  mutant plan-build.py: exit $rc   (want 1) — $(grep -m1 "^AssertionError" "$S/mut-out/err.txt" | cut -c1-200) …"
( cd "$WT" && git checkout -q -- . 2>/dev/null; rsync -a --delete "$HERE/" "$WT/tools/titan/results/wave55-plan/"; /opt/homebrew/bin/python3.12 -B tools/titan/results/wave55-plan/plan-build.py --out "$S/mut-out" >/dev/null ); rc=$?
cmp -s "$S/mut-out/expectations.json" "$HERE/expectations.json"; same=$?
echo "  unmutated plan-build.py in the throwaway: exit $rc (want 0), its expectations.json byte-identical to the installed one: $([[ $same == 0 ]] && echo yes || echo NO)"
git -C "$SH" worktree remove --force "$WT" && echo "  throwaway removed ($(git -C "$SH" worktree list | grep -c "$WT") left)"
