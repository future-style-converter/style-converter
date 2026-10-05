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
# Sourced (not executed) by gate-driver.sh, which defines $PROJECT_ROOT first.
# TITAN_POOL_ROOT overrides the device-pool dir (the test uses a throwaway
# one). Pinned by tools/titan/own-processes.test.mjs with real processes.

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
