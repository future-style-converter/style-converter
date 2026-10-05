#!/usr/bin/env bash
#
# probe-text-metrics.sh — B-EXT spec Section 7 step 11 driver.
#
# Captures the B8/B9/B10 probe fixtures at 4× resolution per platform,
# then runs compute-text-metrics.mjs to populate
# tools/visual/report/manifest.json's `perPlatformProbes` field.
#
# Pipeline:
#   1. Merge every fixture under fixtures/_metric_probes/ into a single
#      IR doc (the dev server / test app load ONE IR doc per session;
#      the probe fixtures together fit in one).
#   2. Convert merged → IR (out/probe-ir.json), sync into the web bundle.
#   3. Start vite, run capture-screenshots-hires.mjs (web) → tools/visual/probes/web__*.png.
#   4. iOS / Android branches are SCAFFOLDED ONLY (TODO[B-EXT round 91+]):
#      they require Xcode + Android emulator validation that this script
#      has no way to run autonomously. The web pipeline alone is enough
#      to populate `perPlatformProbes` because the spec's per-platform
#      result fields are independently nullable.
#   5. Run compute-text-metrics.mjs → tools/visual/text-metrics.json + manifest inline.
#
# Usage:
#   ./tools/visual/probe-text-metrics.sh
#
# Environment overrides:
#   SKIP_WEB=1     — skip web capture (use existing tools/visual/probes/web__*.png)
#   WEB_PORT=3000  — vite dev-server port. If another program holds the
#                    default port the probe moves to a free one (3300–3399);
#                    an explicitly set WEB_PORT that is taken is an error.
#
set -euo pipefail

PROJECT_ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
TOOLS_VISUAL_DIR="$PROJECT_ROOT/tools/visual"
WEB_DIR="$PROJECT_ROOT/apps/web-harness"
PROBES_DIR="$TOOLS_VISUAL_DIR/probes"
PROBE_FIXTURES_ROOT="$PROJECT_ROOT/fixtures/_metric_probes"
# Whether the caller picked the port: a default may be moved, a choice may not.
WEB_PORT_EXPLICIT=0; [[ -n "${WEB_PORT:-}" ]] && WEB_PORT_EXPLICIT=1
WEB_PORT="${WEB_PORT:-3000}"
# Port hygiene: only THIS checkout's stale vite may be killed (wave 52 —
# this script used to `kill -9` whatever listened on the port).
source "$TOOLS_VISUAL_DIR/web-port-guard.sh"

R='\033[0;31m'; G='\033[0;32m'; Y='\033[1;33m'; N='\033[0m'
log()  { printf "${G}[probe]${N} %s\n" "$*"; }
warn() { printf "${Y}[probe]${N} %s\n" "$*" >&2; }
err()  { printf "${R}[probe]${N} %s\n" "$*" >&2; }

mkdir -p "$PROBES_DIR"

# ── Step 1: merge probe fixtures ─────────────────────────────────────────
# The dev server's CaptureGallery loads one IR doc with N components.
# Each probe-fixture file is a separate JSON; merge their `components`
# objects so a single converter invocation produces one IR with all 14
# probe components.
log "merging probe fixtures from $PROBE_FIXTURES_ROOT"
MERGED_FIXTURE="$(mktemp -t probe-merged.XXXXXX.json)"
node --input-type=module -e "
import { readdirSync, statSync, readFileSync, writeFileSync } from 'node:fs';
import { join } from 'node:path';
const root = '$PROBE_FIXTURES_ROOT';
const out = { components: {} };
function walk(dir) {
  for (const e of readdirSync(dir)) {
    const p = join(dir, e);
    if (statSync(p).isDirectory()) walk(p);
    else if (e.endsWith('.json')) {
      const doc = JSON.parse(readFileSync(p, 'utf8'));
      Object.assign(out.components, doc.components ?? {});
    }
  }
}
walk(root);
writeFileSync('$MERGED_FIXTURE', JSON.stringify(out, null, 2));
console.log('  merged components:', Object.keys(out.components).length);
"

# ── Step 2: convert + sync ───────────────────────────────────────────────
log "converting merged fixture to IR"
cd "$PROJECT_ROOT"
# Use TESTALL_SKIP_LOCK so the test-all.sh advisory lock doesn't block us
# if a sibling test-all run is in flight. Probe runs are independent.
./gradlew --no-daemon :converter:run --args="convert --from css --to ir -i $MERGED_FIXTURE -o out_probe" --quiet 2>/tmp/probe-convert.log || {
  err "convert failed; tail of /tmp/probe-convert.log:"
  tail -10 /tmp/probe-convert.log >&2
  exit 1
}
# Sync into web bundle so vite can serve it. Uses ir-components.json
# (the existing canonical name CaptureGallery / FixtureCanvas read).
cp "$PROJECT_ROOT/out_probe/tmpOutput.json" "$WEB_DIR/public/ir-components.json"
log "  IR synced to $WEB_DIR/public/ir-components.json"

# ── Step 3: web capture ──────────────────────────────────────────────────
if [[ "${SKIP_WEB:-0}" == "1" ]]; then
  warn "SKIP_WEB=1 → reusing existing tools/visual/probes/web__*.png"
elif ! command -v node &>/dev/null; then
  warn "node not found → skipping web capture"
else
  log "starting vite for web capture"
  # Harness deps hoist to the repo-root node_modules (npm workspaces).
  if [[ ! -d "$PROJECT_ROOT/node_modules/puppeteer" ]]; then
    log "installing web deps…"
    ( cd "$PROJECT_ROOT" && npm install --silent )
  fi
  # Free the port of OUR OWN stale vite (a crashed run). A foreign listener is
  # never touched: move off a default port, refuse an explicitly chosen one.
  if ! wpg_kill_our_vite_on_port "$WEB_PORT"; then
    if [[ "$WEB_PORT_EXPLICIT" == "1" ]]; then
      err "WEB_PORT=$WEB_PORT is in use by another program (named above) — choose a free port"; exit 2
    fi
    _wpg_new_port="$(wpg_free_port 3300 3399)" || { err "no free port in 3300–3399 for vite"; exit 2; }
    warn "web port $WEB_PORT is in use by another program — using $_wpg_new_port for this run"
    WEB_PORT="$_wpg_new_port"
  fi
  set +m
  ( cd "$WEB_DIR" && exec npx vite --port "$WEB_PORT" >/tmp/probe-vite.log 2>&1 ) &
  VITE_PID=$!
  _cleanup_vite() {
    if [[ -n "${VITE_PID:-}" ]]; then
      kill -TERM "-$VITE_PID" 2>/dev/null || kill -TERM "$VITE_PID" 2>/dev/null || true
      wait "$VITE_PID" 2>/dev/null || true
    fi
    # Same rule on the way out: only a vite of this checkout.
    wpg_kill_our_vite_on_port "$WEB_PORT" >/dev/null 2>&1 || true
  }
  trap _cleanup_vite EXIT INT TERM HUP

  # Wait for vite up to 30 s; bail early if the process died.
  log "waiting for vite @ :$WEB_PORT"
  VITE_READY=0
  for i in {1..30}; do
    if curl -s "http://localhost:$WEB_PORT" >/dev/null 2>&1; then
      VITE_READY=1; break
    fi
    if ! kill -0 "$VITE_PID" 2>/dev/null; then break; fi
    sleep 1
  done

  if [[ $VITE_READY -eq 0 ]]; then
    err "vite failed to start on port $WEB_PORT"
    err "  vite log tail:"
    tail -20 /tmp/probe-vite.log 2>/dev/null | sed 's/^/    /' >&2 || true
    _cleanup_vite
    exit 1
  fi

  ( cd "$WEB_DIR" && node capture-screenshots-hires.mjs \
      --url "http://localhost:$WEB_PORT" \
      --probe-dir "$PROBE_FIXTURES_ROOT" \
      --out "$PROBES_DIR" )
  COUNT=$(ls -1 "$PROBES_DIR"/web__*.png 2>/dev/null | wc -l | tr -d ' ')
  log "captured $COUNT web probe screenshots"
  _cleanup_vite
  trap - EXIT INT TERM HUP
  set -m
fi

# ── Step 4: iOS / Android (scaffolded) ───────────────────────────────────
# TODO[B-EXT round 91+]: these branches need an Xcode+simulator (iOS) and
# Android emulator validation pass before they can ship. Current state:
#   • iOS code: ScreenshotManager.renderHires() exists at scale=4; the
#     wiring from CaptureGallery → renderHires for probe components is
#     not yet in place because it requires a build cycle on a fresh sim.
#   • Android code: ScreenshotManager.saveProbeScreenshot() exists with
#     a 4× bilinear upscale; same wiring gap.
# The web-only run is sufficient to populate perPlatformProbes (per spec
# Section 3 — `iOS: number | null` schema explicitly allows nulls).
warn "iOS/Android probe capture skipped (TODO[B-EXT round 91+]: needs Xcode + emulator)"

# ── Step 5: compute + inline ─────────────────────────────────────────────
log "running compute-text-metrics"
node "$TOOLS_VISUAL_DIR/compute-text-metrics.mjs" \
  --probes "$PROBES_DIR" \
  --manifest "$TOOLS_VISUAL_DIR/report/manifest.json" \
  --out "$TOOLS_VISUAL_DIR/text-metrics.json"
log "done — see $TOOLS_VISUAL_DIR/text-metrics.json"
