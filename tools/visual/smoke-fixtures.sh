#!/usr/bin/env bash
# smoke-fixtures.sh — build_fixtures, the Tier-5/11 fixture step of smoke.sh
# (wave 53; split out of smoke.sh at the wave-53 fix pass, PLAN §0 size rule —
# the function body is unchanged).
#
# Sourced (not executed) by smoke.sh after smoke-port.sh. It runs in smoke.sh's
# shell: $PROJECT_ROOT is the cwd, and log / err / add_result are smoke.sh's
# own helpers (resolved when build_fixtures is CALLED, after smoke.sh defines
# them). Pinned by tools/visual/smoke-port.test.mjs (source + call order).

# ── Tier-5/11 fixture JSONs ─────────────────────────────────────────────────
#
# interaction-states.mjs and a11y-audit.mjs open `/?fixture=<Name>` and the
# page fetches apps/web-harness/public/fixtures/<Name>.json — the pre-flight
# contract at the top of interaction-states.mjs ("`npm run build-fixtures` has
# produced … for every COMPONENTS entry"). That directory is gitignored
# converter output, so a fresh worktree has none; vite's SPA fallback then
# answers index.html for the missing JSON ("Unexpected token '<' … is not valid
# JSON" in the vite log) and every probe dies on `fixture-ready-timeout` — the
# wave-53 symptom (Tier 5: 0 captured · 90 failed; Tier 11: 15 errored) once
# the port guard had got vite up correctly on :3400. The step was simply never
# in this script. build-fixtures.mjs skips files newer than their source, so a
# built tree pays only the mtime checks.
#
# JDK 21 is selected the way test-all.sh does (macOS `java_home`; elsewhere the
# ambient JAVA_HOME stands) — the converter's toolchain is pinned to 21.
build_fixtures() {
    log "building the Tier-5/11 fixture JSONs (apps/web-harness/public/fixtures/; first run ~5 min)…"
    if [[ -x /usr/libexec/java_home ]] && /usr/libexec/java_home -v 21 &>/dev/null; then
        export JAVA_HOME
        JAVA_HOME="$(/usr/libexec/java_home -v 21)"
    fi
    if (cd apps/web-harness && npm run --silent build-fixtures > /tmp/smoke-build-fixtures.log 2>&1); then
        local summary
        # build-fixtures.mjs ends with "N built · M skipped · F failed · T total available".
        summary=$(grep -E "built · .* skipped · .* failed" /tmp/smoke-build-fixtures.log | tail -1)
        log "fixtures: ${summary:-built (no summary line; see /tmp/smoke-build-fixtures.log)}"
        add_result "fixtures: ${summary:-built}"
    else
        err "build-fixtures FAILED; tail of /tmp/smoke-build-fixtures.log:"
        tail -20 /tmp/smoke-build-fixtures.log >&2
        add_result "fixtures: FAILED"
        return 1
    fi
}
