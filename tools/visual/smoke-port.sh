#!/usr/bin/env bash
# smoke-port.sh — the web port smoke.sh's vite and its two probes share (wave 53).
#
# WHY THIS EXISTS (harness-hygiene T4). smoke.sh started vite with `npm run dev`
# — vite.config.ts `server.port: 3000` with no `strictPort`, so a held :3000
# made vite silently move to 3001 — then polled http://localhost:3000/ and
# accepted any 2xx, so a FOREIGN server answering 200 on :3000 passed the
# readiness poll by itself; Tier 5 (interaction-states.mjs) and Tier 11
# (a11y-audit.mjs) then read WEB_PORT unset → '3000' and measured that foreign
# server. smoke never sourced web-port-guard.sh. The rule is test-all.sh's
# (wave 52): a stale vite of THIS checkout on the port may be killed, anything
# else is named and left; a caller-chosen port held by another program is an
# error, the default port held by one moves the run to a free port.
#
# The fallback range is 3400–3499: disjoint from section-runner's 3100–3299
# and test-all's 3300–3399, so a smoke beside a gate or a test-all never races
# them for a port (recorded in web-port-guard.sh's header).
#
# Sourced (not executed) by smoke.sh after web-port-guard.sh; both need
# $PROJECT_ROOT. Pinned by tools/visual/smoke-port.test.mjs with real
# throwaway listeners.

# smoke_pick_web_port [DEFAULT] → prints the port vite must bind; returns 1
# (after naming the holder) when a caller-chosen WEB_PORT is held by another
# program or no fallback port is free. DEFAULT is 3000 (vite.config.ts); the
# pin passes a throwaway port so it never has to bind the real :3000.
smoke_pick_web_port() {
  local default="${1:-3000}" new
  # Caller-chosen port (WEB_PORT already set): honoured as given or refused.
  if [[ -n "${WEB_PORT:-}" ]]; then
    # Ours-or-free: a stale vite of this checkout is cleared, nothing else is.
    if wpg_kill_our_vite_on_port "$WEB_PORT"; then echo "$WEB_PORT"; return 0; fi
    # The guard named the foreign holder on stderr; a chosen port is never swapped.
    echo "[smoke-port] WEB_PORT=$WEB_PORT is in use by another program (named above) — choose a free port" >&2
    return 1
  fi
  # The default port, freed of our own stale vite; a foreign holder stays up.
  if wpg_kill_our_vite_on_port "$default"; then echo "$default"; return 0; fi
  # Same move test-all makes, in smoke's own range.
  new="$(wpg_free_port 3400 3499)" || { echo "[smoke-port] port $default is in use by another program and no port is free in 3400..3499" >&2; return 1; }
  echo "[smoke-port] port $default is in use by another program — using $new for this smoke" >&2
  echo "$new"
}
