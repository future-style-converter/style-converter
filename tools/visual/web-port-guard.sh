#!/usr/bin/env bash
# web-port-guard.sh — never kill a process this checkout did not start (wave 52).
#
# WHY THIS EXISTS. test-all.sh and tools/titan/section-runner.sh used to free
# their vite port with `lsof -ti:"$PORT" | xargs kill -9` — before starting
# vite and again on cleanup. That kills WHATEVER listens there. The default
# web port is 3000, the most common dev-server port there is: on 2026-10-05 a
# developer's unrelated API server was listening on :3000 while a wave was
# being verified, and the only reason it survived is that no default-port
# test-all ran that day (the smoke's probes merely got that server's 404).
# A capture script must not have a destructive side effect on the machine it
# runs on, so the rule is now: only a vite dev server whose working directory
# is inside THIS checkout may be killed; anything else holding the port is
# reported and left alone, and the caller moves to another port.
#
# Sourced (not executed) by test-all.sh and section-runner.sh; both define
# $PROJECT_ROOT before sourcing. Pinned by tools/visual/web-port-guard.test.mjs
# with real throwaway listeners (a foreign one must survive, ours must die).

# wpg_is_our_vite PID → 0 iff PID is a vite dev server started from this checkout.
wpg_is_our_vite() {
  local cmd cwd
  # The command line as ps prints it; a vanished PID is simply "not ours".
  cmd="$(ps -o command= -p "$1" 2>/dev/null)" || return 1
  # Both callers launch `npx vite --port N`, so the listener's argv names vite.
  [[ "$cmd" == *vite* ]] || return 1
  # lsof -Fn prints the cwd as an `n<path>` field; both callers `cd` into the
  # web harness (or a per-section copy of it under tools/titan/runs/) first.
  cwd="$(lsof -a -p "$1" -d cwd -Fn 2>/dev/null | sed -n 's/^n//p' | head -1)"
  # Inside THIS checkout only — a vite from a sibling worktree is foreign too.
  [[ -n "$cwd" && "$cwd" == "$PROJECT_ROOT"* ]]
}

# wpg_kill_our_vite_on_port PORT → kills our own stale vite listeners on PORT;
# returns 1 iff a FOREIGN process is listening there (which is never touched).
wpg_kill_our_vite_on_port() {
  local pid foreign=0
  # -sTCP:LISTEN: listeners only — a client socket to that port is nobody's server.
  for pid in $(lsof -nP -tiTCP:"$1" -sTCP:LISTEN 2>/dev/null); do
    if wpg_is_our_vite "$pid"; then
      # Ours: the same hard kill the callers always used for a stale vite.
      kill -9 "$pid" 2>/dev/null || true
    else
      # Not ours: say who it is (no silent fallthrough) and leave it running.
      foreign=1
      echo "[web-port-guard] port $1 is held by pid $pid ($(ps -o command= -p "$pid" 2>/dev/null | cut -c1-90)) — not this checkout's vite, not touching it" >&2
    fi
  done
  return $foreign
}

# wpg_free_port FIRST LAST → prints the first port in [FIRST, LAST] with no
# listener; returns 1 when the whole range is taken.
wpg_free_port() {
  local p
  for p in $(seq "$1" "$2"); do
    # Same listener probe as above, so "free" means "nobody is listening".
    if ! lsof -nP -tiTCP:"$p" -sTCP:LISTEN >/dev/null 2>&1; then echo "$p"; return 0; fi
  done
  return 1
}
