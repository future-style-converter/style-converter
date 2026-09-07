#!/usr/bin/env node
//
// tools/titan/feed-android.mjs
//
// HOST-SIDE feeder for the TITAN Android inbox capture loop (this lane's
// producer). It reuses an ALREADY-BOOTED emulator — it never boots/erases one —
// installs+launches the harness in titan-inbox mode, then per IR fixture:
//   push the IR JSON into the device inbox  →  poll the on-device screenshot
//   dir until the expected per-component PNGs appear  →  pull them to --out
//   (verifying each parses; retry once on the adb-pull truncation flake)  →
//   clear the on-device PNGs  →  next fixture.
// The expected filenames come from the fed IR document itself (feed-lib.mjs's
// expectedPngNames, a mirror of the on-device flatten+naming), and are pulled
// with the SAME `<idx>_<safeName>.png` names inject-wpt-block.mjs globs for, so
// captures drop straight into the existing compare pipeline.
//
// TITAN Round 3 — `--composed`: launch the app with titanComposed=true so it
// renders the WHOLE fed doc COMPOSED on one ref-matching canvas and writes ONE
// `<safe(testKey)>.png` per test (name derived from the fixture basename, the
// same key the app derives from the inbox filename). The feeder then waits for
// and pulls that single PNG instead of the per-component `%03d_<safe>.png` set;
// tools/titan/inject-wpt-block.mjs's diffComposedVsRef diffs it DIRECTLY vs the
// browser-ref (no stitch). Without the flag, the legacy per-component path is
// byte-for-byte unchanged.
//
// Usage:
//   node tools/titan/feed-android.mjs --fixtures <dir|a.json,b.json> --out <dir> \
//       [--timeout-per-fixture 30] [--udid emulator-5554] [--skip-install] [--composed]
//
// Contract: the device must already be running. If none is attached the feeder
// FAILS LOUDLY telling the caller to boot one (per the lane's BACKGROUND_MODE
// rule — this tool must not create/destroy devices).

import { execFileSync, execSync } from 'node:child_process';
import { existsSync, mkdirSync, mkdtempSync, readdirSync, readFileSync, rmSync, statSync,
         writeFileSync } from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { parseArgs, expectedPngNames, composedPngName, pngIsValid,
         documentFontSrcs, resolveFontFile,
         documentReplacedSrcs, resolveReplacedImageFile,
         // retro R8b (A9#3): the shared --wpt-dir preflight + the honest
         // decline text for an absent corpus root (see feed-lib's banner).
         assetHopPreflight, NO_WPT_DIR_DECLINE } from './feed-lib.mjs';
// wave-40 lane T5: the SVG PRE-RASTER pre-pass. Android's BitmapFactory ships
// no SVG decoder, so the vector is rasterised on the HOST and this document's
// copy of the wire is re-pointed at the PNG sibling BEFORE the image hop
// pushes anything. Shared — not cloned — with feed-ios.mjs: both natives must
// be scored against the same raster.
import { prerasterizeFixtures, applyPrerasterRewrite } from './svg-preraster.mjs';
// wave-42 lane W9: the WOFF→TTF TRANSCODE pre-pass. android.graphics.Typeface
// cannot parse a WOFF container (it loads as the DEFAULT typeface — the
// wave-35 device gate measured it), so DocumentFontRegistry declines every
// `.woff` by name and ALL 48 wave-41 corpus faces shaped in the fallback.
// WOFF1 is a per-table zlib wrapper around the sfnt, so the HOST repackages
// it losslessly (node core zlib, no new dependency) and this feeder's copy of
// the wire is re-pointed at the `.woff.ttf`/`.woff.otf` sibling. ANDROID-ONLY
// by design — web browsers and iOS CoreText both parse WOFF1 natively
// (measured in DocumentFontRegistry.kt's format-table comment), so feed-ios
// deliberately does NOT import this module.
import { transcodeWoffFixtures, applyWoffTranscodeRewrite } from './woff-to-ttf.mjs';
// wave-45 lane X6 — the Rule-43 NOTO BUNDLING PILOT (noto-pilot.mjs banner).
// The feeder's half is DELIVERY ONLY: with TITAN_NOTO_PILOT=1 the staged
// pilot faces are pushed once per run into a `_noto-pilot/` corner of the
// fonts sandbox, proving the per-run font hop can carry the wave-46 payload.
// The runtime deliberately does NOT consume them (nothing registers a face
// no document references): what Compose actually paints in this pilot is its
// wave-34 bundled Noto (5 scripts) + the emulator's system Noto CJK, which
// is exactly what the pilot ref adopts. Flag off ⇒ both imports are inert
// and the feeder is byte-identical (pinned by feed-android.test.mjs).
import { notoPilotEnabled, notoPilotFontFiles } from './noto-pilot.mjs';
// wave-46 lane Y7 — the MONOSPACE FONT-METRIC PIN pilot (mono-pin.mjs banner).
// Unlike the Noto pilot above, the runtime DOES consume this one: with
// TITAN_MONO_PIN=1 the two staged DejaVu Sans Mono files are pushed once per
// app launch into a `_mono-pin/` corner of the fonts sandbox, and every
// document that names the `monospace` generic reaches the inbox with two
// `fontFaces` entries NAMED `monospace` pointing at them — so Compose's
// document-font-first resolution (wave-35 B2) paints the pin face with no
// resolver change. Flag off ⇒ every call below is an identity and the feeder
// is byte-identical (pinned by mono-pin.test.mjs + feed-android.test.mjs).
import { monoPinEnabled, monoPinFontFiles, monoPinMissingWarnings, monoPinDocument,
         MONO_PIN_SANDBOX_DIR } from './mono-pin.mjs';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const REPO_ROOT = path.resolve(__dirname, '..', '..');

// On-device layout (mirrors ScreenshotManager: getExternalFilesDir(null)+…).
const PKG = 'com.styleconverter.test';
const ACTIVITY = `${PKG}/.MainActivity`;
const INBOX_DIR = `/sdcard/Android/data/${PKG}/files/inbox`;
const SHOT_DIR = `/sdcard/Android/data/${PKG}/files/test_screenshots`;
// wave-35 lane B2 — the @font-face sandbox. A SIBLING of the inbox, not a
// subdirectory of it: ScreenshotManager.nextFixtureFile() lists the inbox for
// `*.json`, so a font under it would be inert but confusing, and a future
// listing change would trip over it. The runtime reads File(FONTS_DIR, src)
// with the corpus-relative path preserved verbatim (see feed-lib.mjs's hop
// banner), which is why the pushes below mkdir the src's parent chain.
const FONTS_DIR = `/sdcard/Android/data/${PKG}/files/fonts`;
// wave-39 lane A2 — the replaced-element image sandbox. A SIBLING of both the
// inbox and the fonts dir, for the same two reasons: the inbox listing globs
// `*.json` (an image under it would be inert but confusing) and the two asset
// channels must stay separately auditable. The runtime reads
// File(IMAGES_DIR, src) with the corpus-relative path preserved verbatim (see
// feed-lib.mjs's hop banner), which is why the pushes below mkdir the src's
// parent chain exactly as the font pushes do.
const IMAGES_DIR = `/sdcard/Android/data/${PKG}/files/images`;

const log = (m) => process.stderr.write(`[feed-android] ${m}\n`);

// ── Device plumbing ──────────────────────────────────────────────────────────

/** Locate the adb binary the same way test-all.sh does (SDK env vars first,
 *  then the canonical macOS SDK path, then PATH). */
function findAdb() {
  const candidates = [
    process.env.ANDROID_HOME && `${process.env.ANDROID_HOME}/platform-tools/adb`,
    process.env.ANDROID_SDK_ROOT && `${process.env.ANDROID_SDK_ROOT}/platform-tools/adb`,
    `${process.env.HOME}/Library/Android/sdk/platform-tools/adb`,
  ].filter(Boolean);
  for (const c of candidates) if (existsSync(c)) return c;
  try { return execSync('command -v adb', { encoding: 'utf8' }).trim(); } catch { /* fall through */ }
  throw new Error('adb not found — set ANDROID_HOME or put adb on PATH');
}

/** Return connected device serials (lines ending in a TAB + "device"). */
function listDevices(adb) {
  // Bounded like every other adb call (retro 2026-09-06): a wedged adb server made this first
  // call hang before the feeder's first log line, so a whole section's native step went silent.
  // Retried with a server restart between attempts: right after an `adb kill-server` elsewhere
  // (a gate's reprovision, another tool) the first client call can sit in SYN_SENT past 20 s
  // (retro 2026-09-06, attempt 15: the feeder died here at startup and a whole section shipped
  // Android-less). A restart from THIS process, then a retry, recovers it in ~3 s.
  let out = '';
  for (let attempt = 1; ; attempt++) {
    try {
      out = execFileSync(adb, ['devices'], { encoding: 'utf8', timeout: 20_000, killSignal: 'SIGKILL', maxBuffer: 8 * 1024 * 1024, stdio: ['ignore', 'pipe', 'ignore'] });
      break;
    } catch (e) {
      if (attempt >= 3 || !(e && e.code === 'ETIMEDOUT')) throw e;
      log(`adb devices timed out (attempt ${attempt}) — restarting the adb server and retrying`);
      try { execFileSync(adb, ['kill-server'], { timeout: 10_000, killSignal: 'SIGKILL', stdio: 'ignore' }); } catch { /* best-effort */ }
      try { execFileSync(adb, ['start-server'], { timeout: 20_000, killSignal: 'SIGKILL', stdio: 'ignore' }); } catch { /* the retry's auto-start covers it */ }
    }
  }
  return out.split('\n').slice(1)
    .map((l) => l.trim()).filter(Boolean)
    .filter((l) => /\tdevice$/.test(l))
    .map((l) => l.split('\t')[0]);
}

/** Bind a factory that runs `adb -s <serial> …`, returning stdout as text. */
function makeAdb(adb, serial) {
  return (args, opts = {}) => {
    // Retro gate 2026-09-06: EVERY adb call is bounded. A single `adb logcat -d` hung for 15 min
    // (child alive, node waiting on exit) with the marker already in the buffer — an unbounded
    // execFileSync deadlocks when the child stalls or its output outgrows the default 1 MiB
    // maxBuffer (an emulator's main log buffer reads 5 MiB after a long boot). A timed-out call
    // throws, and every caller here already treats a throw as 'retry' or 'declined'.
    // A host under memory pressure (retro gate 2026-09-06: load 25, swap 14 GB) makes single adb
    // calls exceed 30 s; ETIMEDOUT is retried with a doubling budget (30 → 60 → 120 s) so a slow
    // host degrades to a slow feed instead of a FATAL exit mid-section (attempt 8 died at 25/48
    // on the per-fixture cleanup rm). Non-timeout failures still throw on the first attempt —
    // callers that treat a throw as 'declined'/'retry' keep their semantics.
    let budget = 30_000;
    for (let attempt = 1; ; attempt++) {
      try {
        return execFileSync(adb, ['-s', serial, ...args], { encoding: 'utf8', timeout: budget, killSignal: 'SIGKILL', maxBuffer: 64 * 1024 * 1024, ...opts });
      } catch (e) {
        if (e && e.code === 'ETIMEDOUT' && attempt < 3) { budget *= 2; continue; }
        throw e;
      }
    }
  };
}

// ── Per-fixture steps ─────────────────────────────────────────────────────────

/** Snapshot the on-device screenshot dir as a Set of filenames (empty on a
 *  missing dir — `ls` exits non-zero, which execFileSync throws on). */
function deviceShotNames(adbx) {
  try {
    return new Set(adbx(['shell', 'ls', SHOT_DIR]).split('\n').map((s) => s.trim()).filter(Boolean));
  } catch { return new Set(); }
}

/** Block until every expected PNG name exists on-device, or the timeout
 *  elapses. Returns { done, present }. Pure polling — no pulling here. */
async function waitForPngs(adbx, expected, timeoutSec) {
  const deadline = Date.now() + timeoutSec * 1000;
  const want = new Set(expected);
  for (;;) {
    const have = deviceShotNames(adbx);
    if ([...want].every((n) => have.has(n))) return { done: true, present: have };
    if (Date.now() >= deadline) return { done: false, present: have };
    await new Promise((r) => setTimeout(r, 150)); // poll cadence
  }
}

/** Force-stop the harness, wipe the inbox + shot dirs + logcat, relaunch in
 *  inbox/composed mode, and verify the run-config marker. Returns whether the
 *  marker was seen. Used at startup AND as recovery: a single fixture whose
 *  on-device capture WEDGES (e.g. a pathological transform layer the composed
 *  capture never returns from) leaves the inbox poll loop stuck forever, so
 *  every fixture queued behind it would cascade-timeout on a still-hung app.
 *  Restarting between a timeout and the next fixture gives each one a clean
 *  app — one bad fixture costs one timeout, not the whole tail of the batch. */
async function resetAndLaunch(adbx, opts) {
  adbx(['shell', 'am', 'force-stop', PKG]);
  // wave-35: FONTS_DIR joins the wipe. A face registered from a PREVIOUS
  // run's file would be the worst possible stale state — the capture would
  // shape correctly-looking glyphs from the wrong file with no error anywhere
  // — so the fonts sandbox gets the same idempotence guarantee the inbox has.
  // wave-39: IMAGES_DIR joins the wipe on the same argument the fonts dir
  // joined it on — a support image left over from a PREVIOUS run would let a
  // fixture whose own delivery FAILED paint a plausible raster from the wrong
  // file, with nothing in any log. Idempotence is the whole point.
  adbx(['shell', 'rm', '-rf', INBOX_DIR, SHOT_DIR, FONTS_DIR, IMAGES_DIR]);
  // SHOT_DIR is deliberately NOT shell-mkdir'd: it is the ONE dir the APP
  // must WRITE, and on newer emulator images (observed API 36.1, wave 40) a
  // shell-created dir under the app's external files fails the app's own
  // ScreenshotManager.canWrite() probe ("Permission check failed: Permission
  // denied") — the app then renders forever and never writes a PNG, which
  // the feeder sees as TIMEOUT (0/N) on EVERY fixture. ScreenshotManager
  // mkdirs its own dir on first capture (ScreenshotManager.kt:39/80/119),
  // with app ownership, which always passes its own probe. The INBOX is
  // likewise NOT shell-mkdir'd: on API-36.1 emulator images a shell-created
  // dir under the app's external files is invisible to the app's FUSE view
  // for LISTING too — the app "polls the inbox for next fixture" forever and
  // every fixture times out (the wave-40 gate lost ~30h to exactly this).
  // The app lazily mkdirs the inbox app-owned on first poll
  // (ScreenshotManager.inboxDir), and the launch-marker wait below
  // guarantees it exists before the first push. FONTS_DIR / IMAGES_DIR are
  // NOT shell-mkdir'd either — the wave-40 "residual hazard" note that used
  // to sit here was PROBED REAL in wave-41 (lane T2, private API-36.1
  // instance): a shell-created asset root is invisible to the app for
  // DIRECT-PATH OPENS too, not just listing — the feeder pushed the woff,
  // shell `ls` saw all 261 KB of it, and DocumentFontRegistry still declined
  // "no readable file at <that exact path>" (shell-owned uid 2000 dir vs app
  // uid 10224; captures byte-identical to a no-asset run). So the asset
  // roots ride the SAME app-first pattern as the inbox: ScreenshotManager's
  // poll loop (ensureAssetRoots, called from nextFixtureFile) creates both
  // roots app-owned on every poll, and the launch wait below blocks on all
  // THREE dirs before anything is pushed. Only the SUBPATHS below the roots
  // (pushFontFaces/pushReplacedImages' `mkdir -p` of each src's parent
  // chain) remain shell-created — probed VISIBLE under an app-owned root on
  // the same instance: the pushed woff got far enough to hit the runtime's
  // own woff-container decline (i.e. the file was READ through the
  // shell-made subchain), and the grid support PNG painted, moving pixels
  // (grid-abspos-staticpos-align-self-img-001: 0.9576 vacuous → 0.9615).
  try { adbx(['logcat', '-c']); } catch { /* logcat clear is best-effort */ }
  // Match the shared 390×844 @160dpi capture canvas. Composed mode layers
  // `--ez titanComposed true`: same inbox poll, whole doc onto one canvas.
  adbx(['shell', 'wm', 'size', '390x844']);
  adbx(['shell', 'wm', 'density', '160']);
  const launchArgs = ['shell', 'am', 'start', '-n', ACTIVITY, '--ez', 'titanInbox', 'true'];
  if (opts.composed) launchArgs.push('--ez', 'titanComposed', 'true');
  adbx(launchArgs);
  // Grep the run-config marker (same verification philosophy as test-all's
  // animationTime/forceState gate) — proves the app really entered the mode.
  // In composed mode we additionally require titanComposed=true so a stale
  // per-component launch can't masquerade as composed. logcat was just cleared,
  // so a match is this launch's marker, not a stale one from a prior launch.
  const markerRe = opts.composed ? /titanInbox=true titanComposed=true/ : /titanInbox=true/;
  let marked = false;
  for (let i = 0; i < 40 && !marked; i++) {
    // `-t 2000`: the marker is a fresh line (logcat was cleared above), so the last 2000 lines
    // always contain it; dumping the whole buffer is what made this poll hang (retro 2026-09-06).
    try { marked = markerRe.test(adbx(['logcat', '-d', '-t', '2000'])); } catch { /* retry */ }
    if (!marked) await new Promise((r) => setTimeout(r, 250));
  }
  // The first push must land in APP-created dirs (see the mkdir note above)
  // — wait for the app's lazy per-poll mkdirs to have run: the inbox
  // (ScreenshotManager.inboxDir) AND, since wave-41, the two asset roots
  // (ScreenshotManager.ensureAssetRoots, same poll call), because a font or
  // image pushed before the app created its root would auto-create that root
  // as SHELL — which on API-36.1 images the app cannot see even for
  // direct-path opens (the wave-41 T2 probe), silently stripping every
  // fontFaces/replaced-image test of its assets. One `ls -d` with all three
  // paths: it exits non-zero while ANY of them is missing, so the loop's
  // pass condition is exactly "the app's poll has fully provisioned". The
  // app logs its first "polling …/inbox" only after that lazy init; the ls
  // poll closes the race without trusting the ordering. 60s cap: the very
  // first cold launch (JIT + font init) has been observed to need >10s, and
  // an expired wait must FAIL LOUDLY — falling through would let the first
  // `adb push` create shell-owned dirs and silently kill the WHOLE
  // section's Android leg (the wave-40 gate lost its first two sections to
  // exactly this race, on the inbox alone).
  let dirsReady = false;
  for (let i = 0; i < 240; i++) {
    try { adbx(['shell', 'ls', '-d', INBOX_DIR, FONTS_DIR, IMAGES_DIR]); dirsReady = true; break; } catch { /* not yet */ }
    await new Promise((r) => setTimeout(r, 250));
  }
  if (!dirsReady) {
    throw new Error(`app never created ${INBOX_DIR} + asset roots within 60s — refusing to push (shell-created dirs are invisible to the app on API-36.1 images)`);
  }
  return marked;
}

/** wave-45 lane X6 — push the staged NOTO-PILOT faces into the fonts
 *  sandbox, once per run, under `_noto-pilot/` (a name no corpus-relative
 *  `fontFaces[].src` can collide with — corpus paths never start with an
 *  underscore dir we invent). No-op with the flag unset. Delivery proof for
 *  the wave-46 decision, deliberately NOT consumed by the runtime (see the
 *  import banner); a later in-run resetAndLaunch (timeout recovery) wipes
 *  the sandbox and does NOT re-push — irrelevant to the proof, and nothing
 *  downstream reads the files. Missing staged files are LOUD per face, so a
 *  mis-set TITAN_NOTO_PILOT_FONTS can never masquerade as a delivered run. */
function pushNotoPilotFonts(adbx) {
  if (!notoPilotEnabled()) return { pushed: 0, missing: 0 };
  const { present, missing } = notoPilotFontFiles(process.env, { existsSync });
  for (const face of missing) {
    log(`  noto-pilot: staged font MISSING for '${face.family}' (${face.file}) — not pushed`);
  }
  const remote = `${FONTS_DIR}/_noto-pilot`;
  let pushed = 0;
  if (present.length) adbx(['shell', 'mkdir', '-p', remote]);
  for (const face of present) {
    try { adbx(['push', face.abs, `${remote}/${face.file}`]); pushed++; }
    catch (e) { log(`  noto-pilot: PUSH FAILED ${face.file} — ${e.message}`); }
  }
  if (pushed) log(`noto-pilot: pushed ${pushed}/${present.length + missing.length} pilot faces (delivery proof only)`);
  return { pushed, missing: missing.length };
}

/** wave-46 lane Y7 — push the staged MONO-PIN faces into the fonts sandbox
 *  under `_mono-pin/` (MONO_PIN_SANDBOX_DIR — a leading-underscore dir no
 *  corpus-relative `fontFaces[].src` can collide with). No-op with the flag
 *  unset.
 *
 *  Called after EVERY resetAndLaunch, not once per run: the relaunch wipes
 *  FONTS_DIR (wave-35 idempotence), and unlike the Noto delivery proof above
 *  the runtime READS these files — a timeout-recovery relaunch that did not
 *  re-push would silently hand the rest of the batch back to Droid Sans Mono
 *  under a pilot-labelled run. `adb push` is idempotent, so the repeat costs
 *  two pushes. Missing staged files are LOUD per face (mono-pin.mjs wording)
 *  so a mis-set TITAN_MONO_PIN_FONTS can never masquerade as a pinned run. */
function pushMonoPinFonts(adbx) {
  if (!monoPinEnabled()) return { pushed: 0, missing: 0 };
  const { present, missing } = monoPinFontFiles(process.env, { existsSync });
  for (const line of monoPinMissingWarnings(process.env, { existsSync }, 'feed-android')) log(`  ${line}`);
  const remote = `${FONTS_DIR}/${MONO_PIN_SANDBOX_DIR}`;
  let pushed = 0;
  if (present.length) adbx(['shell', 'mkdir', '-p', remote]);
  for (const face of present) {
    try { adbx(['push', face.abs, `${remote}/${face.file}`]); pushed++; }
    catch (e) { log(`  mono-pin: PUSH FAILED ${face.file} — ${e.message}`); }
  }
  if (pushed) log(`mono-pin: pushed ${pushed}/${present.length + missing.length} DejaVu Sans Mono faces → ${remote}`);
  return { pushed, missing: missing.length };
}

/** wave-35 lane B2 — push this document's @font-face FILES into the device's
 *  fonts sandbox, preserving the corpus-relative path verbatim.
 *
 *  Called BEFORE the IR reaches the inbox, and that ordering is the contract:
 *  the app registers faces at decode time, so a font arriving after its
 *  document would register too late to shape the capture — and with
 *  `font-display: block`-equivalent semantics absent on the native side, the
 *  capture would silently record the fallback face.
 *
 *  Returns { pushed, declined } for the feeder's per-fixture log. A decline is
 *  never fatal: the runtimes degrade to their bundled face and stamp the miss,
 *  which is the wave-34 behaviour this hop improves on rather than replaces.
 *  Failing the fixture instead would hide every OTHER property it measures. */
function pushFontFaces(adbx, doc, opts) {
  const srcs = documentFontSrcs(doc);
  if (srcs.length === 0) return { pushed: 0, declined: 0 };
  // retro R8b (A9#3): an ABSENT corpus root is named as the cause, per src,
  // instead of falling into resolveFontFile's null (which reads as
  // "unresolvable/not a font" — the wrong diagnosis). Normally unreachable:
  // main()'s preflight refuses such a run before any device call.
  if (!opts.wptDir) {
    for (const src of srcs) log(`  font ${NO_WPT_DIR_DECLINE}: ${src}`);
    return { pushed: 0, declined: srcs.length };
  }
  let pushed = 0, declined = 0;
  const madeDirs = new Set();
  for (const src of srcs) {
    const abs = resolveFontFile(opts.wptDir, src, { resolve: path.resolve, existsSync, statSync });
    if (!abs) { declined++; log(`  font DECLINED (unresolvable/not a font): ${src}`); continue; }
    // `adb push` will not create intermediate directories, and the relative
    // chain is what the runtime resolves against — so mkdir the parent once
    // per distinct directory (WPT font paths cluster in one resources/ dir).
    const remote = `${FONTS_DIR}/${src}`;
    const parent = remote.slice(0, remote.lastIndexOf('/'));
    if (!madeDirs.has(parent)) { adbx(['shell', 'mkdir', '-p', parent]); madeDirs.add(parent); }
    try { adbx(['push', abs, remote]); pushed++; }
    catch (e) { declined++; log(`  font PUSH FAILED: ${src} — ${e.message}`); }
  }
  return { pushed, declined };
}

/** wave-39 lane A2 — push this document's REPLACED-ELEMENT image files into
 *  the device's images sandbox, preserving the corpus-relative path verbatim.
 *
 *  Called BEFORE the IR reaches the inbox, like its font twin — but for a
 *  weaker reason, and the difference is worth stating rather than copying the
 *  font comment: images are decoded at PAINT time, not at decode time, so a
 *  late arrival would not silently mis-shape the capture the way a late font
 *  does. It still goes first because the capture is asynchronous (the app may
 *  begin rendering the moment the inbox file lands) and a race that
 *  sometimes paints the image is far worse to debug than one that never does.
 *
 *  Returns { pushed, declined } for the feeder's per-fixture log. A decline is
 *  never fatal: the runtimes paint the empty box they painted before this
 *  channel existed and stamp the miss. Failing the fixture instead would hide
 *  every OTHER property the test measures. */
function pushReplacedImages(adbx, doc, opts) {
  const srcs = documentReplacedSrcs(doc);
  if (srcs.length === 0) return { pushed: 0, declined: 0 };
  // retro R8b (A9#3): same honest-cause branch as pushFontFaces — an absent
  // corpus root must never be logged as "unresolvable/not an image".
  if (!opts.wptDir) {
    for (const src of srcs) log(`  image ${NO_WPT_DIR_DECLINE}: ${src}`);
    return { pushed: 0, declined: srcs.length };
  }
  let pushed = 0, declined = 0;
  const madeDirs = new Set();
  for (const src of srcs) {
    const abs = resolveReplacedImageFile(opts.wptDir, src, { resolve: path.resolve, existsSync, statSync });
    if (!abs) { declined++; log(`  image DECLINED (unresolvable/not an image): ${src}`); continue; }
    // `adb push` will not create intermediate directories, and the relative
    // chain is what the runtime resolves against — so mkdir the parent once
    // per distinct directory (WPT support images cluster in one support/ dir).
    const remote = `${IMAGES_DIR}/${src}`;
    const parent = remote.slice(0, remote.lastIndexOf('/'));
    if (!madeDirs.has(parent)) { adbx(['shell', 'mkdir', '-p', parent]); madeDirs.add(parent); }
    try { adbx(['push', abs, remote]); pushed++; }
    catch (e) { declined++; log(`  image PUSH FAILED: ${src} — ${e.message}`); }
  }
  return { pushed, declined };
}

/** Pull one PNG to the host and verify it decodes; retry the pull ONCE on the
 *  known adb truncation flake. `remoteName` is the on-device filename (device
 *  sanitiser rule) and `localName` is the host output name (compare-pipeline
 *  `safe()` rule) — they differ only when a component name carries a dot, in
 *  which case the pull RENAMES the device file to the compare-glob name.
 *  Returns true iff a valid PNG landed in out. */
function pullVerified(adbx, remoteName, localName, outDir) {
  const remote = `${SHOT_DIR}/${remoteName}`;
  const local = path.join(outDir, localName);
  for (let attempt = 0; attempt < 2; attempt++) {
    try {
      adbx(['pull', remote, local]);
      if (pngIsValid(readFileSync(local))) return true;
      log(`  pulled ${remoteName} failed PNG parse (attempt ${attempt + 1}) — retrying`);
    } catch (e) {
      log(`  pull ${remoteName} errored (attempt ${attempt + 1}): ${e.message}`);
    }
  }
  // retro R8b (A9#4): after the FINAL failed attempt, DELETE whatever landed.
  // A truncated PNG left under the compare-glob name is worse than no file:
  // inject-wpt-block's loadPng throws on it → an `{error}`-shaped diff that
  // silently leaves the scored denominator (assertPlatformColumns skips error
  // cells) and compare-screenshots renders a "decode error" row. An ABSENT
  // file is a MISSING cell, which section-runner.sh Step 5b's capture-count
  // parity check turns into a loud exit 1. `force` swallows ENOENT (the pull
  // may have written nothing at all).
  try { rmSync(local, { force: true }); } catch { /* nothing landed to remove */ }
  return false;
}

// ── Fixture list expansion ────────────────────────────────────────────────────

/** Expand --fixtures (a directory OR a comma-separated list) into a sorted
 *  list of absolute IR .json paths. Sorting makes the feed order stable. */
function expandFixtures(arg) {
  if (existsSync(arg) && statSync(arg).isDirectory()) {
    return readdirSync(arg).filter((f) => f.endsWith('.json')).sort()
      .map((f) => path.resolve(arg, f));
  }
  return arg.split(',').map((s) => path.resolve(s.trim())).filter(Boolean);
}

// ── Main ──────────────────────────────────────────────────────────────────────

async function main() {
  const opts = parseArgs(process.argv.slice(2));
  if (!opts.fixtures || !opts.out) {
    log('usage: feed-android.mjs --fixtures <dir|a.json,b.json> --out <dir> [--timeout-per-fixture N] [--udid S] [--skip-install] [--composed]');
    process.exit(2);
  }
  const fixtures = expandFixtures(opts.fixtures);
  if (fixtures.length === 0) { log(`no fixtures found under ${opts.fixtures}`); process.exit(2); }
  mkdirSync(opts.out, { recursive: true });

  // retro R8b (A9#3) — the --wpt-dir PREFLIGHT, FIRST of all: before findAdb
  // (the first device-side call) and before the two host pre-passes below,
  // which both silently no-op without a corpus root. If any fixture declares
  // a @font-face or replaced-image src and no root was given, the hop cannot
  // happen and every capture would score an ARTIFACT — so the run refuses
  // here, loudly, with exit 2 (the usage-error code above), instead of
  // producing a manifest of bundled-face text. A fixture that fails to parse
  // is skipped here (null) and reported per-fixture by the loop below.
  const readDocOrNull = (fx) => { try { return JSON.parse(readFileSync(fx, 'utf8')); } catch { return null; } };
  const preflight = assetHopPreflight(fixtures.map(readDocOrNull), opts.wptDir);
  if (preflight.fatal) { log(preflight.message); process.exit(2); }

  const adb = findAdb();
  const devices = listDevices(adb);
  if (devices.length === 0) {
    log('FATAL: no Android device/emulator attached. Boot one first (this feeder never boots devices).');
    process.exit(3);
  }
  const serial = opts.udid || devices[0];
  if (!devices.includes(serial)) { log(`FATAL: requested --udid ${serial} not attached (${devices.join(', ')})`); process.exit(3); }
  const adbx = makeAdb(adb, serial);
  log(`device=${serial}  fixtures=${fixtures.length}  out=${opts.out}  timeout=${opts.timeoutPerFixture}s  composed=${opts.composed}`);

  // wave-40 lane T5 — SVG PRE-RASTER pre-pass. FIRST, ahead of every device
  // call: it is pure HOST work (headless Chromium → PNG siblings in the
  // corpus) with no device dependency at all, so running it here means a sick
  // emulator cannot mask a raster failure, and a raster failure cannot be
  // mistaken for one. ONE browser launch covers the whole batch (the css-ui
  // box-sizing cluster's 19 tests share six support vectors), and a batch with
  // no vectors — 28 of the depth-48 corpus's 30 sections (only css-ui and
  // css-flexbox carry SVG <img> sources: the box-sizing cluster and
  // align-items-007's ../support/red-rect.svg; wave49-final feed logs show
  // PRE-RASTER lines in exactly those two sections) — launches nothing at
  // all. The returned map is vector-src → raster-src for the rasters that
  // actually exist; anything missing from it keeps its `.svg` on the wire so
  // DocumentImageRegistry's format decline still fires and still stamps.
  const rasterMap = await prerasterizeFixtures(fixtures, {
    wptDir: opts.wptDir,
    log,
    // The document walker is INJECTED rather than imported by the pre-raster
    // module: feed-lib.mjs owns the `meta.attrs.src` walk (documentReplacedSrcs)
    // and the two must never disagree about which components carry a source —
    // a second walker would be a second chance to miss one.
    srcsOf: documentReplacedSrcs,
  });

  // wave-42 lane W9 — WOFF→TTF TRANSCODE pre-pass, the font channel's twin of
  // the pre-raster above and placed here for the same reason: pure HOST work
  // (zlib inflate → sfnt siblings in the corpus) with no device dependency,
  // so a sick emulator cannot mask a transcode failure or vice versa. ONE
  // pass covers the whole batch (all 48 wave-41 font tests share a single
  // LinLibertine woff). The returned map is woff-src → sfnt-src for the
  // siblings that actually exist; anything missing from it keeps its `.woff`
  // on the wire so DocumentFontRegistry's extension decline still fires and
  // still stamps. Like the pre-raster above, this defaults ON — but the two
  // reached that default by different routes: the transcode is LOSSLESS
  // (identical table bytes, rebuilt container only), so it had no fidelity
  // trade to A/B and defaulted ON from wave 42, where the lossy pre-raster
  // had to EARN its wave-44 flip with a measured A/B. TITAN_WOFF_TRANSCODE=0
  // is this hop's off-switch, TITAN_SVG_PRERASTER=0 the pre-raster's.
  const woffMap = await transcodeWoffFixtures(fixtures, {
    wptDir: opts.wptDir,
    log,
    // Injected for the same one-walker-one-truth reason as above: feed-lib's
    // documentFontSrcs is the ONLY reader of `fontFaces[].src`, and the
    // transcoder must see exactly the srcs the push below will resolve.
    srcsOf: documentFontSrcs,
    // The other half of the same env switch: `=0` turns the hop off (above),
    // `=force` rewrites every sibling regardless of the mtime cache. Needed
    // because the cache keys on the WOFF's mtime, so a fix to the transcoder
    // itself changes its output while every input file stays untouched — this
    // knob is how such a fix reaches an already-materialised corpus without
    // hand-deleting siblings. Any other value (unset included) keeps the
    // ordinary validated-cache path.
    force: process.env.TITAN_WOFF_TRANSCODE === 'force',
  });

  // Install (unless reusing the already-installed app) so the feeder is
  // self-contained. Incremental Gradle → a no-change reinstall is fast.
  if (!opts.skipInstall) {
    log('installing APK (:app:installDebug)…');
    const env = { ...process.env };
    if (!env.JAVA_HOME) {
      const jdk = `${process.env.HOME}/Library/Java/JavaVirtualMachines/temurin-21.jdk/Contents/Home`;
      if (existsSync(jdk)) env.JAVA_HOME = jdk;
    }
    // SCOPE THE INSTALL TO *OUR* DEVICE. `:app:installDebug` fans out to
    // EVERY attached device, so on the shared device farm one sick emulator
    // fails the whole task — and with it this feeder — even though the run
    // only ever touches `serial`. MEASURED (wave-38 lane N8): with 7
    // emulators attached the install threw `InstallException` on somebody
    // else's wedged instance and the css-text capture never started, while
    // the app installed fine on the requested device. ANDROID_SERIAL is the
    // knob AGP's device provider honours, and it is set on the CHILD env
    // only, so it cannot leak into the adb calls below (which already carry
    // `-s serial` through makeAdb).
    env.ANDROID_SERIAL = serial;
    // Route Gradle's stdout to OUR stderr (fd 2) so it can't pollute the
    // machine-readable JSON summary we print to stdout at the end.
    execFileSync('./gradlew', [':app:installDebug', '--quiet'],
      { cwd: path.join(REPO_ROOT, 'apps', 'android-harness'), env, stdio: ['ignore', 2, 2] });
  }

  // Reset device state + launch BEFORE the loop so the poll starts clean
  // (idempotence: a second run can't see the first run's stale files). Same
  // helper the timeout branch uses to recover from a wedged capture.
  log(`launching in titan-${opts.composed ? 'composed' : 'inbox'} mode; verifying marker…`);
  const marked = await resetAndLaunch(adbx, opts);
  log(marked ? `verified: app logged ${opts.composed ? 'titanComposed=true' : 'titanInbox=true'}`
             : `WARNING: never saw ${opts.composed ? 'titanComposed=true' : 'titanInbox=true'} marker (continuing)`);

  // wave-45 lane X6: pilot font delivery, AFTER resetAndLaunch's dirs-ready
  // wait (the fonts root must be app-created first — the wave-41 T2 probe)
  // and before any fixture. Identity no-op unless TITAN_NOTO_PILOT=1.
  pushNotoPilotFonts(adbx);
  // wave-46 lane Y7: the mono-pin faces ride the same post-launch slot; the
  // runtime reads them, so this push repeats after every later relaunch.
  pushMonoPinFonts(adbx);

  // Scratch dir for REWRITTEN IR. Android pushes the fixture FILE into the
  // inbox (iOS re-serialises its in-memory doc), so a rewritten document needs
  // a file of its own — under the SAME basename, because the app derives the
  // composed PNG's test key from the inbox filename. Created lazily: a run
  // with no rasters writes nothing and pushes the original bytes, which is
  // what keeps every non-SVG section byte-identical.
  //
  // Returns the path to PUSH: the original fixture when nothing was rewritten
  // (byte-identical bytes on the wire), else the rewritten scratch file.
  // `localName` only has to be unique WITHIN the scratch dir — the on-device
  // inbox name is composed separately by the caller, and it is that name the
  // app derives the test key from.
  let rewriteDir = null;
  const pushableFixture = (fx, doc, localName, label) => {
    const applied = applyPrerasterRewrite(doc, rasterMap);
    // wave-42 lane W9 — the WOFF→TTF stand-in rewrite rides the SAME seam and
    // the same in-memory-only discipline as the pre-raster one: this doc copy
    // (and the scratch file below) is the only place the `.woff.ttf`/`.otf`
    // src exists — the on-disk per-test IR, the web bundle and the iOS
    // feeder's copy all keep the real `.woff`. Only srcs whose sibling was
    // actually WRITTEN are in woffMap, so a failed transcode keeps its woff
    // and the runtime's extension decline stays visible (module banner).
    const woffApplied = applyWoffTranscodeRewrite(doc, woffMap);
    if (applied.length === 0 && woffApplied.length === 0) return fx;
    for (const { src, rasterSrc } of applied) {
      log(`  ${label}: svg PRE-RASTER stand-in ${src} → ${rasterSrc}`);
    }
    // The per-fixture LOUD STAMP for the font rewrite, mirroring the svg one:
    // the feeder log must say, next to each fixture, which faces will shape
    // from a host repackage rather than from the authored container.
    for (const { src, fontSrc } of woffApplied) {
      log(`  ${label}: woff TRANSCODE stand-in ${src} → ${fontSrc}`);
    }
    if (rewriteDir === null) rewriteDir = mkdtempSync(path.join(os.tmpdir(), 'titan-preraster-'));
    const out = path.join(rewriteDir, localName);
    writeFileSync(out, JSON.stringify(doc));
    return out;
  };

  // wave-46 lane Y7 — the MONO-PIN document rewrite, a THIRD in-memory-only
  // seam beside the pre-raster and WOFF ones, with one ordering difference
  // that is a correctness contract: it runs AFTER pushFontFaces has read the
  // document's own `fontFaces[].src`, because the pin's `_mono-pin/…` srcs
  // are sandbox-relative (pushed above by pushMonoPinFonts), not
  // corpus-relative, and the corpus hop would DECLINE them loudly for nothing.
  // Identity (the already-chosen pushFx path) unless the flag is on AND the
  // document names the generic; otherwise the pinned doc goes to the same
  // scratch dir under a `mono-` prefixed local name, and the per-fixture LOUD
  // STAMP says which fixtures shape from the pin.
  const monoPinnedFixture = (pushFx, doc, localName, label) => {
    const pinned = monoPinDocument(doc);
    if (pinned === doc) return pushFx;
    log(`  ${label}: mono-pin @font-face 'monospace' → ${pinned.fontFaces.map((f) => f.src).join(', ')}`);
    if (rewriteDir === null) rewriteDir = mkdtempSync(path.join(os.tmpdir(), 'titan-preraster-'));
    const out = path.join(rewriteDir, `mono-${localName}`);
    writeFileSync(out, JSON.stringify(pinned));
    return out;
  };

  // Feed each fixture and record a result row.
  const results = [];
  for (let i = 0; i < fixtures.length; i++) {
    const fx = fixtures[i];
    const base = path.basename(fx);
    let doc;
    try { doc = JSON.parse(readFileSync(fx, 'utf8')); }
    catch (e) { results.push({ fixture: base, ok: false, error: `bad IR json: ${e.message}` }); continue; }
    // Both modes yield a list of { deviceFile, hostFile } captures: deviceFile
    // is the on-device name the poll waits for; hostFile is the compare-glob
    // name the pull writes to --out (they differ only for a dotted name).
    //   • Composed: ONE PNG named for the WPT test key (derived from the
    //     fixture basename, identical to what the app derives from the inbox
    //     filename). The Android composed rule keeps the dot too, so device ==
    //     host here.
    //   • Per-component: one PNG per flattened component (expectedPngNames
    //     already returns the decoupled {deviceFile, hostFile} pairs).
    const expected = opts.composed
      ? (() => { const n = composedPngName(base); return [{ deviceFile: n, hostFile: n }]; })()
      : expectedPngNames(doc);
    const wantDevice = expected.map((e) => e.deviceFile);
    const t0 = Date.now();
    // wave-40 lane T5 / wave-42 lane W9 — apply BOTH native-side wire
    // rewrites (svg → pre-raster PNG sibling; woff → transcoded sfnt sibling)
    // to THIS DOCUMENT'S IN-MEMORY COPY ONLY, and take the file to push back.
    // MOVED ABOVE the two asset pushes in wave-42, and the order is now a
    // correctness contract: pushFontFaces below reads the REWRITTEN doc's
    // fontFaces, so the woff rewrite must land first or the push would carry
    // the `.woff` Android's Typeface cannot parse instead of the `.ttf`/`.otf`
    // sibling the registry can load. The on-disk per-test IR and the web
    // harness's bundle are untouched, which keeps the web score byte-identical.
    const pushFx = pushableFixture(fx, doc, `${String(i).padStart(4, '0')}-${base}`, base);
    // wave-35: faces FIRST (before the IR reaches the inbox) — see
    // pushFontFaces' ordering contract. Since wave-42 the doc's srcs may be
    // the `.woff.ttf`/`.woff.otf` stand-ins, which resolveFontFile admits
    // (ttf/otf are in FONT_EXTENSIONS) and the transcode pre-pass wrote.
    const fonts = pushFontFaces(adbx, doc, opts);
    if (fonts.pushed || fonts.declined) {
      log(`  ${base}: fonts pushed=${fonts.pushed} declined=${fonts.declined}`);
    }
    // wave-39: replaced-element images — also before the inbox push (see
    // pushReplacedImages for why the ordering is a race guard, not a
    // correctness contract like the font one).
    const images = pushReplacedImages(adbx, doc, opts);
    if (images.pushed || images.declined) {
      log(`  ${base}: images pushed=${images.pushed} declined=${images.declined}`);
    }
    // wave-46 lane Y7: the mono-pin rewrite LAST — after the corpus font hop
    // above has read the document's own srcs (see monoPinnedFixture).
    const inboxFx = monoPinnedFixture(pushFx, doc, `${String(i).padStart(4, '0')}-${base}`, base);
    // Push into the inbox under a unique, FIFO-ordered name (index prefix
    // guarantees uniqueness even if two fixtures share a basename).
    adbx(['push', inboxFx, `${INBOX_DIR}/${String(i).padStart(4, '0')}-${base}`]);
    const { done, present } = await waitForPngs(adbx, wantDevice, opts.timeoutPerFixture);
    if (!done) {
      results.push({ fixture: base, ok: false, error: 'timeout',
        want: wantDevice, got: [...present], elapsedSec: (Date.now() - t0) / 1000 });
      // A timeout means the on-device capture WEDGED — the app's inbox loop is
      // now stuck on this fixture and will never process the next one. Restart
      // the app so the wedge doesn't cascade-timeout the whole tail of the
      // batch (resetAndLaunch also wipes the inbox + partials). Skip the
      // restart if this was the last fixture — nothing left to protect.
      if (i < fixtures.length - 1) {
        log(`  ${base}: TIMEOUT (${present.size}/${expected.length} PNGs) — restarting app to clear the wedge`);
        // Retro 2026-09-06 (attempt 16 died HERE at 18/48): a starved device can make the restart's
        // adb calls exhaust their retry budget; treat that as 'not re-marked' and let the tail
        // retry / the column-presence guard report the short column honestly — never FATAL.
        let remarked = false;
        try { remarked = await resetAndLaunch(adbx, opts); } catch (e) { log(`  app restart failed (${e && e.code || e}) — fixture stays failed`); }
        if (!remarked) log('  WARNING: marker not seen after restart (continuing)');
        // wave-46 lane Y7: the relaunch wiped FONTS_DIR — re-deliver the pin.
        pushMonoPinFonts(adbx);
      } else {
        log(`  ${base}: TIMEOUT (${present.size}/${expected.length} PNGs) — last fixture, not restarting`);
      }
      continue;
    }
    await new Promise((r) => setTimeout(r, 150)); // settle before pulling
    const pulled = [];
    const bad = [];
    for (const e of expected) {
      (pullVerified(adbx, e.deviceFile, e.hostFile, opts.out) ? pulled : bad).push(e.hostFile);
    }
    const elapsedSec = (Date.now() - t0) / 1000;
    // Clear this fixture's on-device PNGs so the next fixture's poll is clean.
    // Best-effort: a failed cleanup only means the next fixture's poll sees stale PNGs (it
    // already tolerates that via expected-name matching); it must never abort the feed.
    try { adbx(['shell', 'rm', '-f', `${SHOT_DIR}/*`]); } catch { log(`  ${base}: on-device cleanup failed (adb) — continuing`); }
    const ok = bad.length === 0;
    results.push({ fixture: base, ok, pulled, ...(bad.length ? { badPngs: bad } : {}), elapsedSec });
    log(`  ${base}: ${ok ? 'OK' : 'PARTIAL'} ${pulled.length}/${expected.length} PNGs in ${elapsedSec.toFixed(2)}s`);
  }

  // Tail-retry pass: give every timed-out fixture ONE more chance now that
  // the app is warm. The dominant timeout cause on a pooled device is the
  // COLD first launch after boot (JIT + first render blow the budget on
  // fixture #1, then everything runs in ~1s) — without this pass, every
  // section that lands on a cold slot ships a hole at its first fixture. A
  // genuinely wedging fixture (e.g. css-transforms z-ordering-003) just
  // times out a second time and stays failed — retried:true marks the row
  // either way so the summary shows what was salvaged vs truly broken.
  const timedOutRows = results.filter((r) => r.error === 'timeout');
  if (timedOutRows.length > 0 && timedOutRows.length < fixtures.length) {
    log(`tail-retry: ${timedOutRows.length} timed-out fixture(s), one warm retry each…`);
    for (const row of timedOutRows) {
      const fx = fixtures.find((f) => path.basename(f) === row.fixture);
      if (!fx) continue;
      const retryDoc = JSON.parse(readFileSync(fx, 'utf8'));
      const expected = opts.composed
        ? (() => { const n = composedPngName(row.fixture); return [{ deviceFile: n, hostFile: n }]; })()
        : expectedPngNames(retryDoc);
      const t0 = Date.now();
      // RE-PUSH THE ASSETS. The timeout branch above called resetAndLaunch,
      // which WIPES both asset sandboxes — so without this the retry would
      // render the same document with its fonts and images gone, and a
      // "salvaged" row would carry a capture measurably worse than the one
      // that timed out. Pre-wave-39 this was a live (if narrow) defect on the
      // font channel: only fixtures that both timed out AND declared a
      // @font-face were affected, and their retry silently shaped in the
      // bundled face. Both channels are idempotent, so re-pushing when the
      // sandbox was NOT wiped (the last-fixture branch) costs one adb push.
      // wave-40 lane T5: re-apply the raster rewrite too. The retry re-parses
      // the fixture FROM DISK, so its `meta.attrs.src` is the untouched `.svg`
      // again — without this line a salvaged row would be the one capture in
      // the run scored against a vector both natives decline, i.e. exactly the
      // silent asymmetry the asset re-push above exists to prevent.
      // wave-42 lane W9: pushableFixture also re-applies the WOFF rewrite for
      // the same re-parsed-from-disk reason, and it MUST stay above the
      // pushFontFaces call below — the font push resolves the rewritten srcs.
      const retryFx = pushableFixture(
        fx, retryDoc, `9${String(fixtures.indexOf(fx)).padStart(3, '0')}-${row.fixture}`, row.fixture,
      );
      pushFontFaces(adbx, retryDoc, opts);
      pushReplacedImages(adbx, retryDoc, opts);
      // wave-46 lane Y7: re-deliver the pin faces (the last-fixture timeout
      // branch does not relaunch, but the push is idempotent) and re-apply
      // the pin rewrite to the re-parsed doc, after the font hop as always.
      pushMonoPinFonts(adbx);
      const retryInboxFx = monoPinnedFixture(
        retryFx, retryDoc, `9${String(fixtures.indexOf(fx)).padStart(3, '0')}-${row.fixture}`, row.fixture,
      );
      // 9xxx prefix keeps the inbox name unique vs the first attempt's 0xxx.
      adbx(['push', retryInboxFx, `${INBOX_DIR}/9${String(fixtures.indexOf(fx)).padStart(3, '0')}-${row.fixture}`]);
      const { done } = await waitForPngs(adbx, expected.map((e) => e.deviceFile), opts.timeoutPerFixture);
      row.retried = true;
      if (done) {
        await new Promise((r) => setTimeout(r, 150));
        const pulled = [];
        for (const e of expected) {
          if (pullVerified(adbx, e.deviceFile, e.hostFile, opts.out)) pulled.push(e.hostFile);
        }
        if (pulled.length === expected.length) {
          row.ok = true; delete row.error;
          row.pulled = pulled; row.elapsedSec = (Date.now() - t0) / 1000;
          log(`  ${row.fixture}: RETRY OK in ${row.elapsedSec.toFixed(2)}s`);
        }
      } else {
        log(`  ${row.fixture}: RETRY TIMEOUT — genuinely failing, restarting app`);
        // Retro 2026-09-06: a device so starved that `am force-stop` exhausts its 30/60/120 s retry
        // budget (attempt 16: host at 76 MB free RAM) must cost ONE fixture, not the feed — the
        // column-presence guard downstream still reports the short column honestly.
        try { await resetAndLaunch(adbx, opts); } catch (e) { log(`  app restart failed (${e && e.code || e}) — continuing without a relaunch`); }
        // wave-46 lane Y7: the relaunch wiped FONTS_DIR — re-deliver the pin
        // so the NEXT retry row (and the force-stop'd app's final state)
        // never run un-pinned under a pilot-labelled run.
        pushMonoPinFonts(adbx);
      }
      adbx(['shell', 'rm', '-f', `${SHOT_DIR}/*`]);
    }
  }

  adbx(['shell', 'am', 'force-stop', PKG]); // stop app; leave the device running

  // wave-40 lane T5: drop the rewritten-IR scratch dir. Best-effort — a leak
  // here is a few KB in $TMPDIR, never a wrong capture, so it must not be able
  // to fail a run that already produced its PNGs.
  if (rewriteDir !== null) { try { rmSync(rewriteDir, { recursive: true, force: true }); } catch { /* best effort */ } }

  // Machine-readable summary on stdout (human logs went to stderr).
  const okCount = results.filter((r) => r.ok).length;
  const timed = results.filter((r) => r.ok && typeof r.elapsedSec === 'number');
  const warm = timed.slice(1); // exclude the first (may include JIT warmup)
  const avg = (rs) => rs.length ? rs.reduce((a, r) => a + r.elapsedSec, 0) / rs.length : null;
  const summary = {
    device: serial, total: fixtures.length, ok: okCount,
    secPerFixtureAll: avg(timed), secPerFixtureWarm: avg(warm.length ? warm : timed),
    results,
  };
  process.stdout.write(JSON.stringify(summary, null, 2) + '\n');
  process.exit(okCount === fixtures.length ? 0 : 1);
}

// retro R8b: CLI guard — the exact shape feed-ios.mjs already uses — so
// feed-android.test.mjs can import pullVerified for a real-path unit test
// without main() parsing the test runner's argv and exiting 2. Every caller
// (section-runner.sh, run-titan.sh, the BACKLOG refeed recipe) invokes this
// file as `node …/feed-android.mjs`, so argv[1] resolves to this module.
if (process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  main().catch((e) => { log(`FATAL: ${e.stack || e.message}`); process.exit(1); });
}

// Exported for tools/titan/feed-android.test.mjs (A9#4 pin: a PNG that fails
// to parse twice must be ABSENT afterwards, never left corrupt under the
// compare-glob name). Takes an injected `adbx`, so the test needs no device.
export { pullVerified };
