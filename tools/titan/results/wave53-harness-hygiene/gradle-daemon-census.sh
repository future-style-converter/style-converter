#!/usr/bin/env bash
# wave 53 · L5 harness-hygiene — READ-ONLY census of what kill_own_gradle_daemons
# would decide on this host. It never signals a process: it calls only the
# predicate (_op_gradle_daemon_is_ours) over every daemon log, and over the log
# of every LIVE daemon pgrep finds. Usage: gradle-daemon-census.sh [ROOT…]
# (default: the checkout this script sits in).
set -uo pipefail
HERE="$(cd "$(dirname "$0")" && pwd)"
PROJECT_ROOT="$(cd "$HERE/../../../.." && pwd)"
source "$PROJECT_ROOT/tools/titan/own-processes.sh"
ROOTS=("${@:-$PROJECT_ROOT}")
HOME_G="${GRADLE_USER_HOME:-$HOME/.gradle}"
echo "roots: ${ROOTS[*]}"
# Every daemon log of every version: how many the predicate calls ours.
for vdir in "$HOME_G"/daemon/*/; do
  v="$(basename "$vdir")"; n=0; ours=0; multi=0
  for l in "$vdir"daemon-*.out.log; do
    [[ -f "$l" ]] || continue; n=$((n+1))
    _op_gradle_daemon_is_ours "$l" "${ROOTS[@]}" && ours=$((ours+1))
    # Distinct served dirs that are NOT inside the roots (a foreign or shared daemon).
    k="$(grep -o 'currentDir=[^}]*' "$l" | sed 's/^currentDir=//' | sort -u | while read -r d; do h=0; for r in "${ROOTS[@]}"; do _op_dir_in_root "$d" "$r" && h=1; done; (( h )) || echo x; done | wc -l | tr -d ' ')"
    in="$(grep -o 'currentDir=[^}]*' "$l" | sed 's/^currentDir=//' | sort -u | while read -r d; do for r in "${ROOTS[@]}"; do _op_dir_in_root "$d" "$r" && { echo y; break; }; done; done | wc -l | tr -d ' ')"
    (( k > 0 && in > 0 )) && multi=$((multi+1))
  done
  echo "version $v: logs=$n ours=$ours not-ours=$((n-ours)) (shared with a root AND a foreign tree: $multi)"
done
# Live daemons: the decision the helper would take right now.
for p in $(pgrep -f 'org.gradle.launcher.daemon.bootstrap.GradleDaemon ' 2>/dev/null); do
  ver="$(ps -o command= -p "$p" | sed -n 's/.*GradleDaemon \([0-9][0-9A-Za-z.+-]*\).*/\1/p')"
  l="$HOME_G/daemon/$ver/daemon-$p.out.log"
  if _op_gradle_daemon_is_ours "$l" "${ROOTS[@]}"; then d=WOULD-STOP; else d=WOULD-LEAVE; fi
  echo "live pid $p ($ver): $d — served: $(grep -o 'currentDir=[^}]*' "$l" 2>/dev/null | sed 's/^currentDir=//' | sort -u | paste -sd' ' - || echo 'no log')"
done
