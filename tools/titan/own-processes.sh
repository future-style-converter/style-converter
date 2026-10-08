#!/usr/bin/env bash
# own-processes.sh — the gate stops ONLY what this project started (wave 52).
#
# WHY THIS EXISTS. gate-driver.sh's stop_our_processes said "Only OUR
# processes" and then ran `pkill -f 'qemu-system'` and `pkill -f 'Chrome for
# Testing'` — host-wide: every Android emulator on the machine and every
# puppeteer / Playwright browser of every other project died whenever a gate
# started, reprovisioned or ended. provision-devices.sh already had the right
# rule (retro R8b, A9#6: launch pids are recorded in emulator-pids and a
# foreign emulator is never killed silently); the driver, written a wave
# later, did not follow it. Same principle as tools/visual/web-port-guard.sh:
# a capture script has no destructive side effect on the machine it runs on.
# What is NOT ours stays up, is named on stderr, and shows up as load in the
# quiet-host check — which refuses the gate instead of clearing the host.
#
# Wave 53 (harness-hygiene T2/T3) retired the two BACKLOG-named host-wide
# stops of the gate path the same way: `./gradlew --stop` (every daemon of the
# user's Gradle version) → kill_own_gradle_daemons, and `pkill -9 -x adb`
# (every process named adb) → kill_wedged_adb_server. One remains, not taken:
# gate-driver.sh start_adb_detached runs `adb kill-server` at every provision
# (harness-hygiene T5, for BACKLOG "Known-broken").
#
# Sourced (not executed) by gate-driver.sh, which defines $PROJECT_ROOT first,
# and by section-runner.sh / provision-devices.sh, which call only
# kill_wedged_adb_server (it needs no $PROJECT_ROOT). TITAN_POOL_ROOT overrides
# the device-pool dir, GRADLE_USER_HOME / ANDROID_ADB_SERVER_PORT the daemon
# logs and the adb port (the test uses throwaway ones), and TITAN_GRADLE_PIDS
# narrows the daemon scan to the test's own fakes. Pinned by
# tools/titan/own-processes.test.mjs with real processes.

# own_emulator_pids → the live emulator pids provision-devices.sh launched.
own_emulator_pids() {
  local file="${TITAN_POOL_ROOT:-/tmp/titan-device-pool}/emulator-pids" p c
  # No record, nothing is ours.
  [[ -f "$file" ]] || return 0
  while read -r p; do
    # The file is appended to by hand-rolled shell; ignore anything not a pid.
    [[ "$p" =~ ^[0-9]+$ ]] || continue
    # The launcher either execs into qemu (same pid) or forks it (child pid).
    for c in "$p" $(pgrep -P "$p" 2>/dev/null); do
      # A recorded pid can be REUSED by an unrelated process after the emulator
      # died (the file outlives reboots of the pool) — so the command line must
      # still be an emulator before the pid counts as ours.
      ps -o command= -p "$c" 2>/dev/null | grep -Eq 'qemu-system|/emulator( |$)' && echo "$c"
    done
  done < "$file" | sort -u
}

# kill_own_emulators → SIGTERM (what pkill sent) to our emulators only; every
# other qemu on the host is named and left running.
kill_own_emulators() {
  local ours q
  # Space-padded so a pid matches whole, never as a prefix of another.
  ours=" $(own_emulator_pids | tr '\n' ' ')"
  for q in $ours; do kill "$q" 2>/dev/null || true; done
  for q in $(pgrep -f 'qemu-system' 2>/dev/null); do
    # No silent fallthrough: say which emulator was spared and why.
    [[ "$ours" == *" $q "* ]] || echo "[own-processes] emulator pid $q was not launched by provision-devices.sh — not touching it" >&2
  done
  return 0
}

# _op_cwd_in_checkout PID → 0 iff PID's working directory is inside this checkout.
_op_cwd_in_checkout() {
  local cwd
  # lsof -Fn prints the cwd as an `n<path>` field (same probe as web-port-guard).
  cwd="$(lsof -a -p "$1" -d cwd -Fn 2>/dev/null | sed -n 's/^n//p' | head -1)"
  [[ -n "$cwd" && "$cwd" == "$PROJECT_ROOT"* ]]
}

# kill_own_test_browsers → SIGTERM the Chrome-for-Testing processes that were
# started from this checkout (puppeteer children inherit the capture script's
# cwd: the web harness, or a per-section copy of it under tools/titan/runs/).
# Another project's test browser has another cwd and is left alone.
kill_own_test_browsers() {
  local q
  for q in $(pgrep -f 'Chrome for Testing' 2>/dev/null); do
    if _op_cwd_in_checkout "$q"; then kill "$q" 2>/dev/null || true
    # Helpers of a foreign browser match the pattern too; one line per browser
    # would need a process tree, so report only the ones that are not helpers.
    elif ! ps -o command= -p "$q" 2>/dev/null | grep -q -- '--type='; then
      echo "[own-processes] test browser pid $q was not started from this checkout — not touching it" >&2
    fi
  done
  return 0
}

# ── Gradle daemons (wave 53, harness-hygiene T2) ────────────────────────────
# `./gradlew --stop` stops EVERY daemon of that Gradle version the user runs,
# whatever checkout it served — both wrappers pin one version, so the driver's
# two --stop calls took down every other session's Gradle build on the host.
# The obvious repair, "a daemon whose cwd is this checkout", cannot work: every
# daemon runs in ~/.gradle/daemon/<ver> (measured: the live 9.6.1 daemon's cwd).
# The usable owner record is the daemon's own log, which names the directory of
# every build it served: `Received command: Build{id=…, currentDir=<dir>}`.

# _op_dir_in_root DIR ROOT → 0 iff DIR is ROOT or below it, and not inside a
# checkout of its own nested under ROOT (a git worktree under .claude/worktrees/
# of the main checkout is ANOTHER checkout, though its path sits below ROOT).
_op_dir_in_root() {
  local d="$1" r="${2%/}"
  # Boundary-checked: <root>-x is a sibling, not a child, so the `/` is required.
  [[ "$d" == "$r" || "$d" == "$r/"* ]] || return 1
  # Walk DIR up while it is still strictly below ROOT; a `.git` file or dir on
  # the way is a nested checkout (the condition bounds the walk by itself).
  while [[ "$d" == "$r/"* ]]; do
    [[ -e "$d/.git" ]] && return 1
    d="${d%/*}"
  done
  return 0
}

# _op_gradle_daemon_is_ours LOG ROOT… → 0 iff LOG names ≥ 1 served build and
# EVERY one of them is inside one of the ROOTs (a daemon shared with a foreign
# checkout is not ours — Gradle reuses a compatible idle daemon across them).
_op_gradle_daemon_is_ours() {
  local log="$1" dir r hit seen=0; shift
  # No log, no proof of ownership: the daemon is left.
  [[ -f "$log" ]] || return 1
  # Every `currentDir=` occurrence, up to the `}` that closes Build{…}.
  while IFS= read -r dir; do
    seen=1; hit=0
    # The roots are evaluated TOGETHER: one line may sit in any given root.
    for r in "$@"; do _op_dir_in_root "$dir" "$r" && { hit=1; break; }; done
    (( hit )) || return 1
  done < <(grep -o 'currentDir=[^}]*' "$log" 2>/dev/null | sed 's/^currentDir=//' | sort -u)
  (( seen ))
}

# kill_own_gradle_daemons [ROOT…] → SIGTERM the Gradle daemons that served only
# builds inside the ROOTs (default: this checkout); every other daemon is named
# on stderr and left running. One summary line names what was stopped and left.
kill_own_gradle_daemons() {
  local roots=() r p cmd ver log home served stopped="" left=""
  # Physical paths: the JVM's currentDir is getcwd(), never a symlinked spelling.
  for r in "${@:-$PROJECT_ROOT}"; do roots+=("$(cd "$r" 2>/dev/null && pwd -P || echo "${r%/}")"); done
  home="${GRADLE_USER_HOME:-$HOME/.gradle}"
  for p in $(pgrep -f 'org.gradle.launcher.daemon.bootstrap.GradleDaemon ' 2>/dev/null); do
    # TITAN_GRADLE_PIDS (hermetic tests) can only NARROW the scan, never add a pid.
    [[ -z "${TITAN_GRADLE_PIDS:-}" || " $TITAN_GRADLE_PIDS " == *" $p "* ]] || continue
    cmd="$(ps -o command= -p "$p" 2>/dev/null)" || continue
    # The daemon's argv ends `…GradleDaemon <version>`; its log lives under that version.
    ver="$(sed -n 's/.*GradleDaemon \([0-9][0-9A-Za-z.+-]*\).*/\1/p' <<<"$cmd")"
    log="$home/daemon/$ver/daemon-$p.out.log"
    if [[ -n "$ver" ]] && _op_gradle_daemon_is_ours "$log" "${roots[@]}"; then
      # Ours: SIGTERM, which the daemon's shutdown hook handles like --stop.
      kill "$p" 2>/dev/null || true; stopped+=" $p"
    else
      # Not ours (or unprovable): say which checkouts it served and leave it.
      left+=" $p"
      served="$(grep -o 'currentDir=[^}]*' "$log" 2>/dev/null | sed 's/^currentDir=//' | sort -u | paste -sd' ' - || true)"
      echo "[own-processes] gradle daemon pid $p (${ver:-no version in argv}) served [${served:-no log at $log}] — not only this checkout's, not touching it" >&2
    fi
  done
  echo "[own-processes] kill_own_gradle_daemons roots=[${roots[*]}]: stopped [${stopped# }] left [${left# }]" >&2
  return 0
}

# ── adb server (wave 53, harness-hygiene T3) ────────────────────────────────
# The device-listing timeout branches used `kill-server; pkill -9 -x adb`: the
# pkill ran even when kill-server worked and killed every process NAMED adb —
# every session's adb clients and shells. A wedged server is one process: the
# listener on the server port, whose argv is `adb … fork-server server …`.

# kill_wedged_adb_server → SIGKILL only the adb server listening on the server
# port (ANDROID_ADB_SERVER_PORT, default 5037); any other listener there is named
# and left. Called in the device-listing timeout branch right after a bounded
# `kill-server`: a server that honoured it no longer listens, so only a wedged
# one is hit.
kill_wedged_adb_server() {
  local port="${ANDROID_ADB_SERVER_PORT:-5037}" p cmd
  # -sTCP:LISTEN: the server only — a client socket to that port is not the server.
  for p in $(lsof -nP -tiTCP:"$port" -sTCP:LISTEN 2>/dev/null); do
    cmd="$(ps -o command= -p "$p" 2>/dev/null)" || continue
    if [[ "$cmd" == *"fork-server server"* ]]; then
      # The wedged server ignored `kill-server`, so a catchable signal would not do.
      kill -9 "$p" 2>/dev/null || true
      echo "[own-processes] killed the wedged adb server pid $p on tcp:$port" >&2
    else
      # No silent fallthrough: a non-adb listener on the adb port is named and left.
      echo "[own-processes] tcp:$port is held by pid $p ($(cut -c1-90 <<<"$cmd")) — not an adb server, not touching it" >&2
    fi
  done
  return 0
}
