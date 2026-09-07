#!/usr/bin/env node
//
// Pins for tools/titan/section-runner.sh — the per-section WPT orchestrator.
//
// section-runner.sh is bash, so these are source-scan pins in the style of
// run-titan.test.mjs / inject-wpt-block.test.mjs: they assert the
// load-bearing lines a refactor is most likely to drop. The subject is the
// "honest composed 3-platform capture" re-plumb (Big Rock #1): before it,
// section-runner captured web-only and hardcoded EMPTY_DIR for the iOS /
// Android inject dirs, so every per-section manifest showed native n/a. The
// re-plumb (a) runs the CANONICAL in-place web driver in WPT_COMPOSED mode,
// (b) added Step 5b native feeders behind a global device lock, and (c)
// pointed inject at the per-section native capture dirs.
//
// The companion fix lives in the three puppeteer capture drivers: under
// headless:'new' on this toolchain the GPU raster path deadlocks
// Page.captureScreenshot, so every driver must launch with --disable-gpu or
// the web capture silently yields "0 screenshots". Pinned below too.

import { test } from 'node:test';
import assert from 'node:assert/strict';
import { promises as fs } from 'node:fs';

const src = await fs.readFile(new URL('./section-runner.sh', import.meta.url), 'utf8');

test('web capture runs the CANONICAL in-place driver in WPT_COMPOSED mode', () => {
  // Must invoke apps/web-harness/capture-screenshots.mjs from $PROJECT_ROOT —
  // NOT the rsync'd copy via `cd "$WEB_ROOT" && node capture-screenshots.mjs`.
  // The copy sits two dirs deeper, where the driver's relative import of
  // ../../tools/titan/safe-name.mjs resolves to a nonexistent path and the
  // whole capture crashes at import time (the "0 web screenshots" bug).
  assert.match(
    src,
    /cd "\$PROJECT_ROOT" && WPT_MODE=1 WPT_COMPOSED=1 node apps\/web-harness\/capture-screenshots\.mjs/,
    'web capture must run the canonical in-place driver with WPT_COMPOSED=1',
  );
  assert.doesNotMatch(
    src,
    /cd "\$WEB_ROOT" && [^\n]*node capture-screenshots\.mjs/,
    'must NOT run the rsync-copied driver (breaks the safe-name.mjs relative import)',
  );
});

test('Step 1 extraction engages post-load augmentation itself (RC-A5a)', () => {
  // extract-fixture.mjs only augments wall-tagged tests (requires-script-
  // mutation / requires-script-driven-scroll) when POST_LOAD_EXTRACT=1 is in
  // its environment. section-runner used to rely on the CALLER's ambient
  // shell for that, so a fresh section run silently re-extracted those tests
  // static-only — clobbering any earlier post-load stamp and leaving e.g.
  // css-flexbox/abspos/dynamic-align-self-001 rendering its pre-mutation
  // state (postLoadExtracted:false). The env must be set on the invocation.
  assert.match(
    src,
    /POST_LOAD_EXTRACT=1 (?:[A-Z_]+=\S+ )*node "\$TITAN_DIR\/extract-fixture\.mjs"/,
    'Step 1 must run extract-fixture.mjs with POST_LOAD_EXTRACT=1 inline',
  );
});

test('Step 1 extraction engages the bidi bake itself (wave-30 fix-T3)', () => {
  // Same contract, same failure mode as POST_LOAD_EXTRACT above: the bidi
  // bake is opt-in (`--bidi-bake` / BIDI_BAKE=1, see extract-fixture main()),
  // and left to the caller's ambient shell it engaged only by accident. A
  // fresh `section-runner.sh selectors` without it silently reproduced
  // dir-selector-change-003/004 in LOGICAL order where wave29-final had the
  // measured visual order, with no marker to explain the drift.
  assert.match(
    src,
    /(?:^|\s)BIDI_BAKE=1 (?:[A-Z_]+=\S+ )*node "\$TITAN_DIR\/extract-fixture\.mjs"/m,
    'Step 1 must run extract-fixture.mjs with BIDI_BAKE=1 inline',
  );
  // Both pins ride the SAME invocation — a second, separate extract call
  // would re-extract and clobber, so pin them to one line.
  const line = src.split('\n').find((l) => l.includes('node "$TITAN_DIR/extract-fixture.mjs"'));
  assert.ok(line, 'Step 1 extract invocation not found');
  assert.ok(line.includes('POST_LOAD_EXTRACT=1') && line.includes('BIDI_BAKE=1'),
    `both env pins must be on the one extract invocation, got: ${line}`);
});

test('Step 5b feeds BOTH natives in composed mode through per-device pool slots', () => {
  assert.match(src, /feed-android\.mjs" --fixtures "\$PERTEST_DIR" --composed/, 'android composed feeder dropped');
  assert.match(src, /feed-ios\.mjs" --fixtures "\$PERTEST_DIR" --composed/, 'ios composed feeder dropped');
  // Devices are a POOL: each parallel section-runner acquires one free booted
  // device per platform via a per-device mkdir-atomic slot lock (stale-healed).
  // With one emulator + one simulator this degrades to the old serialization;
  // with a provisioned fleet N sections feed natives concurrently.
  assert.match(src, /POOL_ROOT="\/tmp\/titan-device-pool"/, 'device pool root dropped');
  assert.match(src, /_pool_acquire/, 'pool acquire helper dropped');
  assert.match(src, /_pool_release/, 'pool release helper dropped');
  // Feeders must target their acquired slot, not whatever device is default.
  assert.match(src, /--udid "\$ANDROID_DEV"/, 'android feeder must target the acquired slot');
  assert.match(src, /--udid "\$IOS_DEV"/, 'ios feeder must target the acquired slot');
});

test('Step 5b runs the two native feeders CONCURRENTLY (independent hardware)', () => {
  // Android and iOS are separate devices — feeding them serially under one
  // lock wasted half the native wall-clock. Both feeders launch as background
  // jobs and are awaited; a wedge/timeout on one platform must not starve the
  // other (each has its own wait + warn).
  assert.match(src, /FEED_ANDROID_PID=\$!/, 'android feeder no longer backgrounded');
  assert.match(src, /FEED_IOS_PID=\$!/, 'ios feeder no longer backgrounded');
  const waits = src.match(/wait "\$FEED_(ANDROID|IOS)_PID"/g) ?? [];
  assert.equal(waits.length, 2, `both feeder pids must be awaited, found ${waits.length}`);
});

test('provisioned devices skip in-feeder builds (concurrent-build collision guard)', () => {
  // Two simultaneous gradle installs / xcodebuilds in one checkout can
  // collide. provision-devices.sh pre-installs the app on every pool device
  // and writes marker files; Step 5b must consult them and pass the
  // build-skipping flags so concurrent feeds never trigger parallel builds.
  assert.match(src, /provisioned-android/, 'android provision marker not consulted');
  assert.match(src, /provisioned-ios/, 'ios provision marker not consulted');
  assert.match(src, /--skip-install/, 'android prebuilt flag dropped');
  assert.match(src, /--no-build/, 'ios prebuilt flag dropped');
});

test('inject points at the per-section native dirs, never EMPTY_DIR', () => {
  // Both the main inject (Step 7) and the recovery inject (Step 7.5) must
  // pass the real per-section capture dirs so native columns are scored.
  const iosHits = src.match(/--ios-dir "\$IOS_SHOTS_DIR"/g) ?? [];
  const androidHits = src.match(/--android-dir "\$ANDROID_SHOTS_DIR"/g) ?? [];
  assert.ok(iosHits.length >= 2, `expected --ios-dir on main+recovery inject, found ${iosHits.length}`);
  assert.ok(androidHits.length >= 2, `expected --android-dir on main+recovery inject, found ${androidHits.length}`);
  assert.doesNotMatch(src, /--ios-dir "\$EMPTY_DIR"/, 'native inject must not be hardcoded to EMPTY_DIR');
  assert.doesNotMatch(src, /--android-dir "\$EMPTY_DIR"/, 'native inject must not be hardcoded to EMPTY_DIR');
});

test('per-section native capture dirs are defined', () => {
  assert.match(src, /IOS_SHOTS_DIR="\$WORK_DIR\/ios-screenshots"/, 'IOS_SHOTS_DIR def dropped');
  assert.match(src, /ANDROID_SHOTS_DIR="\$WORK_DIR\/android-screenshots"/, 'ANDROID_SHOTS_DIR def dropped');
});

test('provision-devices.sh writes the exact markers Step 5b consumes', async () => {
  // Producer/consumer contract: section-runner greps
  // $POOL_ROOT/provisioned-{android,ios} to decide --skip-install/--no-build.
  // If either side renames the marker, concurrent feeds silently fall back to
  // in-feeder builds — reintroducing the parallel-build collision.
  const prov = await fs.readFile(new URL('./provision-devices.sh', import.meta.url), 'utf8');
  assert.match(prov, /POOL_ROOT="\/tmp\/titan-device-pool"/, 'provision pool root must match Step 5b');
  assert.match(prov, /provisioned-android/, 'android marker dropped from provision');
  assert.match(prov, /provisioned-ios/, 'ios marker dropped from provision');
  // Extra emulator instances must be -read-only clones of ONE AVD (identical
  // rendering config = pixel parity across the pool) and headless (silent).
  assert.match(prov, /-read-only/, 'extra emulators must be -read-only instances of the same AVD');
  assert.match(prov, /-no-window/, 'extra emulators must be headless');
  // Two hangs/failures that each cost a full provisioning run once:
  // (1) `simctl bootstatus -b` blocks FOREVER on some freshly-created sims
  //     (hung 7 hours) — only bounded polling is allowed;
  // (2) emulator launch errors went to /dev/null, so a refused instance
  //     ("run all emulators with -read-only") was invisible — launches must
  //     log to a file.
  // (line-anchored + comment-excluded: the script legitimately DOCUMENTS the
  //  ban in a comment; only a real, non-comment invocation may trip this)
  assert.doesNotMatch(prov, /^(?!\s*#)[^\n]*bootstatus[^\n]*-b\b/m, 'unbounded `simctl bootstatus -b` is banned (7h hang)');
  assert.match(prov, /emulator-launch-\$i\.log/, 'emulator launches must log to a file, not /dev/null');
  // (3) a zombie sim wedged `simctl terminate` for 38 minutes: EVERY
  //     per-device simctl/adb mutation must run under the _bounded watchdog
  //     (macOS has no `timeout`), so no single sick device can hang the run.
  assert.match(prov, /_bounded\(\)/, 'the _bounded watchdog helper was dropped');
  assert.match(prov, /_bounded \d+ xcrun simctl install/, 'simctl install must be watchdogged');
  assert.match(prov, /_bounded \d+ xcrun simctl terminate/, 'simctl terminate must be watchdogged');
  assert.match(prov, /_bounded \d+ "\$ADB" -s "\$s" install/, 'adb install must be watchdogged');
});

for (const driver of [
  '../../apps/web-harness/capture-screenshots.mjs',
  '../../apps/web-harness/capture-screenshots-hires.mjs',
  './capture-browser-ref.mjs',
]) {
  test(`${driver} launches puppeteer with --disable-gpu (GPU-raster deadlock fix)`, async () => {
    const s = await fs.readFile(new URL(driver, import.meta.url), 'utf8');
    assert.match(s, /'--disable-gpu'/, `${driver} must force CPU raster or Page.captureScreenshot deadlocks`);
  });
}

// ── retro R8b (A9#1): NATIVE_SHORT — a short/absent native column exits 1 ──
//
// Before this, every native delivery failure under --all-platforms (no free
// slot, a failed split, a feeder exiting non-zero, N missing captures) was a
// `warn` into a per-section log; the runner exited 0 and the missing cells
// silently shrank that platform's denominator — a missing capture is neither
// pass nor fail. inject's assertPlatformColumns only ever saw a WHOLE column
// at zero, and only warned because the runner never set
// TITAN_REQUIRE_ALL_COLUMNS. wave49-final happened to land 0 missing cells;
// the guard that made that true was not in the repository.

test('every native delivery failure sets NATIVE_SHORT (slot, split, per-test dir, feeder rc, count parity, inject exit 3)', () => {
  assert.match(src, /^NATIVE_SHORT=0$/m, 'the flag must be initialised (set -u)');
  // Each failure path pairs its warn with the flag.
  assert.match(src, /no free Android device[^\n]*; NATIVE_SHORT=1; \}/, 'unacquired Android slot');
  assert.match(src, /no free iOS simulator[^\n]*; NATIVE_SHORT=1; \}/, 'unacquired iOS slot');
  assert.match(src, /split-combined-ir failed[^\n]*; NATIVE_SHORT=1; \}/, 'failed split');
  assert.match(src, /per-test IR dir \$PERTEST_DIR missing[^\n]*; NATIVE_SHORT=1/, 'missing per-test dir');
  assert.match(src, /wait "\$FEED_ANDROID_PID" \|\| \{ warn[^\n]*; NATIVE_SHORT=1; \}/, 'feed-android non-zero');
  assert.match(src, /wait "\$FEED_IOS_PID"\s+\|\| \{ warn[^\n]*; NATIVE_SHORT=1; \}/, 'feed-ios non-zero');
  // Capture-count parity: PNGs per native dir vs per-test docs fed.
  assert.match(src, /N_PERTEST=\$\(find "\$PERTEST_DIR" -maxdepth 1 -name '\*\.json' -type f \| wc -l/, 'per-test doc count');
  assert.match(src, /"\$N_IOS" != "\$N_PERTEST" \]\]; then\n\s*warn "iOS column SHORT[^\n]*; NATIVE_SHORT=1/, 'iOS parity');
  assert.match(src, /"\$N_ANDROID" != "\$N_PERTEST" \]\]; then\n\s*warn "Android column SHORT[^\n]*; NATIVE_SHORT=1/, 'Android parity');
  // inject's exit 3 (a whole column at zero under TITAN_REQUIRE_ALL_COLUMNS=1)
  // maps to the flag — the manifest is already written — not to set -e death.
  assert.match(src, /3\) warn "inject: platform column ABSENT[^\n]*; NATIVE_SHORT=1 ;;/, 'inject exit 3 → flag');
});

test('the NATIVE_SHORT gate exits 1 AFTER the manifest is written and verified, BEFORE the success exit', () => {
  const gate = src.indexOf('if [[ "${NATIVE_SHORT:-0}" == 1 ]]; then');
  const wptOk = src.lastIndexOf('WPT_OK=$(node -e "');   // Step 7.5's (recovery) verification
  const done = src.indexOf('log "done."');
  assert.ok(gate > 0, 'gate missing');
  assert.ok(wptOk > 0 && wptOk < gate, 'gate must come after Step 7.5 (manifest written + verified)');
  assert.ok(gate < done, 'gate must precede the success exit');
  const body = src.slice(gate, done);
  assert.match(body, /err "native column short\/absent under --all-platforms/, 'the failure must be named');
  assert.match(body, /\n\s*exit 1\n/, 'the gate exits 1');
  // And the script no longer ends with success regardless of the flag.
  assert.match(src.slice(done), /^log "done\."\nexit 0\n?$/, 'exit 0 only past the gate');
});

test('both inject invocations declare the platform scope via env $INJECT_ENV and survive exit 3', () => {
  assert.match(src, /if \[\[ "\$PLATFORM_SCOPE" == "all" \]\]; then\n\s*INJECT_ENV="TITAN_REQUIRE_ALL_COLUMNS=1"\nelse\n\s*INJECT_ENV="SKIP_IOS=1 SKIP_ANDROID=1"\nfi/,
    'all-platforms → a zero column is fatal in inject; web-only → the empty native dirs are declared');
  const hits = src.match(/env \$INJECT_ENV node "\$TITAN_DIR\/inject-wpt-block\.mjs"/g) ?? [];
  assert.equal(hits.length, 2, `main + recovery inject must both carry the env, found ${hits.length}`);
  const bare = src.match(/^\s*node "\$TITAN_DIR\/inject-wpt-block\.mjs"/gm) ?? [];
  assert.equal(bare.length, 0, 'no bare inject invocation may remain');
  // Each is `|| INJECT_RC=$?` + the rc check, so exit 3 cannot kill the runner under set -e.
  const checked = src.match(/--android-dir "\$ANDROID_SHOTS_DIR" \|\| INJECT_RC=\$\?\n\s*_inject_rc_check "\$INJECT_RC"/g) ?? [];
  assert.equal(checked.length, 2, `both invocations must route through _inject_rc_check, found ${checked.length}`);
  // The env is scoped to inject: compare-screenshots (Step 6) also reads SKIP_*.
  assert.doesNotMatch(src, /^export (SKIP_IOS|SKIP_ANDROID|TITAN_REQUIRE_ALL_COLUMNS)=/m, 'never export the scope globally');
});

test('the exit-code header documents the NATIVE_SHORT exit 1 and the disk exits', () => {
  assert.match(src, /#\s+1 — at least one stage failed[\s\S]{0,400}NATIVE_SHORT/, 'exit 1 must mention NATIVE_SHORT');
  assert.match(src, /#\s+2 — infra error[\s\S]{0,300}TITAN_MIN_FREE_GB[\s\S]{0,200}DISK_ABORT/, 'exit 2 must mention the disk guards');
});

// ── retro R8b (A10#12): the df preflight + low-disk watchdog are CODE ───────
//
// BACKLOG obligation #6 said "mid-run disk is now a gate abort (<8G)"; no
// tracked script implemented it or the ≥10G preflight — the gate script was
// re-derived from prose each wave, so either could vanish silently.

test('disk preflight refuses below TITAN_MIN_FREE_GB (default 10) with exit 2, before the run dir exists', () => {
  assert.match(src, /TITAN_MIN_FREE_GB="\$\{TITAN_MIN_FREE_GB:-10\}"/, 'default floor 10G (BACKLOG "≥10G or abort")');
  assert.match(src, /TITAN_ABORT_FREE_GB="\$\{TITAN_ABORT_FREE_GB:-8\}"/, 'default watchdog floor 8G (BACKLOG "<8G")');
  // POSIX df (macOS + Linux); column 4 = Available in 1K blocks → whole GiB.
  assert.match(src, /_free_gb\(\) \{ df -Pk "\$PROJECT_ROOT" 2>\/dev\/null \| awk 'NR==2 \{ printf "%d", \$4 \/ 1048576 \}'; \}/, 'portable df helper');
  const pre = src.indexOf('elif (( FREE_GB < TITAN_MIN_FREE_GB )); then');
  const mk = src.indexOf('mkdir -p "$WORK_DIR"');
  assert.ok(pre > 0 && mk > 0 && pre < mk, 'preflight must run before the work dir is created');
  assert.match(src.slice(pre, pre + 700), /err "disk preflight: \$\{FREE_GB\}G free < \$\{TITAN_MIN_FREE_GB\}G[^\n]*\n\s*exit 2/, 'refusal is exit 2');
});

test('low-disk watchdog: poller → DISK_ABORT marker → USR1 → kill feeders → exit 2; every later EXIT trap stops it', () => {
  assert.match(src, /DISK_ABORT_MARKER="\$WORK_DIR\/DISK_ABORT"/, 'marker path');
  assert.match(src, /trap '_on_disk_abort' USR1/, 'USR1 handler installed');
  const handler = src.slice(src.indexOf('_on_disk_abort() {'), src.indexOf("trap '_on_disk_abort' USR1"));
  assert.match(handler, /kill -TERM \$\{FEED_ANDROID_PID:-\} \$\{FEED_IOS_PID:-\}/, 'feeders must not keep writing to a full disk');
  assert.match(handler, /\n\s*exit 2\n/, 'a disk abort is an infra error');
  const poller = src.slice(src.indexOf('while kill -0 "$$" 2>/dev/null; do'), src.indexOf('DISK_WD_PID=$!'));
  assert.match(poller, /\(\( free < TITAN_ABORT_FREE_GB \)\)/, 'the watchdog floor');
  assert.match(poller, /> "\$DISK_ABORT_MARKER"/, 'the marker is written before the signal');
  assert.match(poller, /kill -USR1 "\$\$"/, 'the parent is signalled');
  // Every EXIT trap installed once the watchdog exists must stop it first.
  const traps = [...src.matchAll(/^\s*trap '([^\n]*)' EXIT/gm)].map((m) => m[1]);
  const first = traps.findIndex((t) => t.startsWith('_stop_disk_watchdog'));
  assert.ok(first > 0, 'the watchdog-aware base trap must follow the pre-WORK_DIR base trap');
  const after = traps.slice(first);
  assert.ok(after.length >= 5, `expected ≥5 watchdog-aware EXIT traps, found ${after.length}`);
  for (const t of after) assert.ok(t.startsWith('_stop_disk_watchdog;'), `EXIT trap must stop the watchdog: ${t}`);
});

// ── retro R8b (A9#6): provision-devices.sh idempotence repairs ──────────────

test('provision-devices.sh reuses Shutdown titan-pool sims before creating, names new ones uniquely, and never pkills foreign emulators silently', async () => {
  const prov = await fs.readFile(new URL('./provision-devices.sh', import.meta.url), 'utf8');
  // iOS: boot existing Shutdown pool sims BEFORE any create (the 81-sim /
  // 38 GB CoreSimulator exhaustion came from creating on every run).
  const reuse = prov.indexOf('for u in $(_shutdown_pool_udids || true); do');
  const create = prov.indexOf('xcrun simctl create "$NAME"');
  assert.ok(reuse > 0, 'reuse loop missing');
  assert.ok(create > 0 && reuse < create, 'reuse must precede create');
  assert.match(prov, /_shutdown_pool_udids\(\) \{\n\s*xcrun simctl list devices 2>\/dev\/null \| grep -E 'titan-pool-\[0-9\]\+' \| grep '\(Shutdown\)'/, 'pool lookup by name + state');
  assert.match(prov, /grep -v unavailable/, 'unavailable sims cannot boot — skipped');
  assert.match(prov, /_bounded 60 xcrun simctl boot "\$u"/, 'the reuse boot is watchdogged');
  assert.match(prov, /NAME="titan-pool-\$i-\$\(date -u \+%Y%m%dT%H%M%SZ\)"/, 'new sims get a unique name');
  assert.doesNotMatch(prov, /simctl create "titan-pool-\$i"/, 'the duplicate-minting name is gone');
  // Android: the host-wide pkill only on the opt-in path; own pids recorded.
  assert.match(prov, /--restart-fleet\) RESTART_FLEET=1; shift ;;/, '--restart-fleet flag parsed');
  const pkills = prov.split('\n').filter((l) => /pkill -f 'qemu-system'/.test(l) && !/^\s*#/.test(l));
  assert.equal(pkills.length, 1, 'exactly one live pkill, on the opt-in path');
  const pkAt = prov.indexOf(pkills[0]);
  const guard = prov.lastIndexOf('if (( RESTART_FLEET )); then', pkAt);
  const elif = prov.indexOf('elif [[ -z "$FOREIGN" ]]; then', guard);
  assert.ok(guard > 0 && guard < pkAt && pkAt < elif, 'pkill must sit inside the RESTART_FLEET branch');
  assert.match(prov, /echo \$! >> "\$EMU_PIDS_FILE"/, 'launched emulator pids must be recorded');
  assert.match(prov, /WANT_ANDROID=\$CUR/, 'a foreign writable emulator shrinks the pool instead of being killed');
});
