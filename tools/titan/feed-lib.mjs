#!/usr/bin/env node
//
// tools/titan/feed-lib.mjs
//
// Pure (device-free) helpers shared by the native TITAN feeders
// (feed-android.mjs today; a feed-ios.mjs twin can reuse the same math).
// Kept in their own module so feed-<platform>.test.mjs can unit-test the
// load-bearing logic — expected-PNG-name derivation, arg parsing, PNG
// validation — without touching an emulator/simulator.
//
// The expected-name derivation MUST match the on-device capture path exactly,
// otherwise the feeder waits for the wrong filenames and every fixture times
// out. It mirrors, line-for-line in intent:
//   - SlotComposer.compose   (runtimes/compose/.../core/renderer/SlotComposer.kt)
//   - flattenComponents      (apps/android-harness/.../ScreenshotCaptureScreen.kt)
//   - parentCreatesContext   (same file)
//   - ScreenshotManager.saveScreenshot filename rule (%03d_<safeName>.png)
// so the PNGs the feeder pulls are exactly the ones inject-wpt-block.mjs later
// globs for (`<idx>_<safeKey>.png`).

import { PNG } from 'pngjs';

// ── Arg parsing ─────────────────────────────────────────────────────────────

/**
 * Parse feeder CLI args into a plain options object. Pure: takes an argv array
 * (process.argv.slice(2) in main), returns { fixtures, out, timeoutPerFixture,
 * udid, skipInstall }. Unknown flags are ignored (forward-compatible).
 *
 *   --fixtures <dir | comma-list of IR .json>   (required)
 *   --out <hostScreenshotsDir>                   (required)
 *   --timeout-per-fixture <seconds>             (default 30)
 *   --udid <adb serial>                         (optional; else sole device)
 *   --skip-install                              (reuse already-installed app)
 */
export function parseArgs(argv) {
  const get = (name) => {
    const i = argv.indexOf(name);
    return i >= 0 && i + 1 < argv.length ? argv[i + 1] : null;
  };
  const has = (name) => argv.includes(name);
  const timeoutRaw = get('--timeout-per-fixture');
  const timeout = timeoutRaw != null ? Number(timeoutRaw) : 30;
  return {
    fixtures: get('--fixtures'),
    out: get('--out'),
    // Guard against a non-numeric / non-positive timeout silently disabling
    // the per-fixture watchdog (which would let one bad fixture hang forever).
    timeoutPerFixture: Number.isFinite(timeout) && timeout > 0 ? timeout : 30,
    udid: get('--udid'),
    skipInstall: has('--skip-install'),
    // TITAN Round-3 composed capture: launch the app with titanComposed=true
    // and expect ONE `<safe(testKey)>.png` per fixture instead of the
    // per-component `%03d_<safeName>.png` set. Default off ⇒ legacy behaviour.
    composed: has('--composed'),
  };
}

// ── Filename derivation (mirror of the Android capture path) ─────────────────

/** Sanitise a component name for a filename — identical char class to the
 *  Kotlin ScreenshotManager (`Regex("[^a-zA-Z0-9_-]")`). */
export function safeName(name) {
  return String(name).replace(/[^a-zA-Z0-9_-]/g, '_');
}

/** Extract a String from an IR `data` leaf that may be a bare string or a
 *  `{value:"..."}` object — mirrors tryStringValue in the Kotlin capture path. */
function tryStringValue(el) {
  if (typeof el === 'string') return el;
  if (el && typeof el === 'object' && !Array.isArray(el) && typeof el.value === 'string') {
    return el.value;
  }
  return null;
}

/** Extract an opacity scalar from an IR `data` leaf across every carrier shape
 *  the IR has used (bare number, {value}, {alpha, original:{value}}) — mirrors
 *  tryOpacityValue in the Kotlin capture path. */
function tryOpacityValue(el) {
  if (typeof el === 'number') return el;
  if (el && typeof el === 'object' && !Array.isArray(el)) {
    if (typeof el.alpha === 'number') return el.alpha;
    if (typeof el.value === 'number') return el.value;
    if (el.original && typeof el.original.value === 'number') return el.original.value;
  }
  return null;
}

/**
 * Does this component create a paint context its children's appearance depends
 * on? A byte-for-byte mirror of parentCreatesContext in ScreenshotCaptureScreen
 * .kt — when true, the capture path SUPPRESSES standalone child captures, so
 * the feeder must expect FEWER PNGs (only the parent's composited render).
 */
export function parentCreatesContext(parent) {
  const props = parent?.properties ?? [];
  if (props.length === 0) return false;
  for (const p of props) {
    switch (p.type) {
      // Pure-presence cases: any active value means a paint context.
      case 'ClipPath': case 'Mask': case 'MaskImage': case 'Filter':
      case 'BackdropFilter': case 'Rotate': case 'Scale': case 'Translate':
        return true;
      // Overflow keywords — only clip/hidden clip content. IR data is an
      // uppercased enum string ("HIDDEN") or {value}; normalise to lowercase.
      case 'Overflow': case 'OverflowX': case 'OverflowY': {
        const v = tryStringValue(p.data)?.toLowerCase();
        if (v === 'clip' || v === 'hidden') return true;
        break;
      }
      // Blend-mode — only non-'normal' values create a blend context.
      case 'MixBlendMode': {
        const v = tryStringValue(p.data)?.toLowerCase();
        if (v != null && v !== 'normal') return true;
        break;
      }
      // Transform — non-empty array is non-identity (parser drops 'none').
      case 'Transform': {
        if (Array.isArray(p.data) && p.data.length > 0) return true;
        break;
      }
      // Opacity < 1 creates a stacking context with backdrop dependence.
      case 'Opacity': {
        const v = tryOpacityValue(p.data);
        if (v != null && v < 1.0) return true;
        break;
      }
      default: break; // no paint-context effect on descendants
    }
  }
  return false;
}

/**
 * Rebuild render tree roots from a decoded IR document's flat component list.
 * Mirror of SlotComposer.compose: components with no `slot` are roots; a
 * `slot.parent` ref nests the child under that parent, preserving flat-array
 * order within each parent bucket; a dangling parent ref becomes a root.
 */
export function composeRoots(doc) {
  const components = doc?.components ?? [];
  // Fast path: no slot refs → v1 nested (children already in place) or v2
  // Mode B (all roots). Return untouched.
  if (!components.some((c) => c && c.slot != null)) return components;

  const ids = new Set(components.map((c) => c.id));
  const childrenOf = new Map(); // parentId -> children[] in flat order
  const roots = [];
  for (const c of components) {
    const parentId = c.slot?.parent ?? null;
    if (parentId == null || !ids.has(parentId)) {
      roots.push(c); // root, or dangling parent → promote to root
    } else {
      if (!childrenOf.has(parentId)) childrenOf.set(parentId, []);
      childrenOf.get(parentId).push(c);
    }
  }
  // Recursive assembly with a visited guard (converter never emits cycles;
  // the guard just prevents infinite recursion on a corrupted document).
  const visited = new Set();
  const build = (c) => {
    if (visited.has(c.id)) return { ...c };
    visited.add(c.id);
    const kids = childrenOf.get(c.id);
    return kids ? { ...c, children: kids.map(build) } : { ...c };
  };
  return roots.map(build);
}

/**
 * Depth-first pre-order flatten with paint-context suppression — mirror of
 * flattenComponents in ScreenshotCaptureScreen.kt. Parent first, then each
 * child recursively UNLESS the parent creates a paint context (then the child
 * is only captured inside the parent's canvas, not standalone).
 */
export function flattenComponents(roots) {
  const out = [];
  const walk = (c) => {
    out.push(c);
    const kids = c.children;
    if (!kids || kids.length === 0) return;
    if (parentCreatesContext(c)) return;
    kids.forEach(walk);
  };
  (roots ?? []).forEach(walk);
  return out;
}

/**
 * The ordered list of per-component PNG filenames the app will write for this
 * IR document — the feeder's poll target AND its host-side output names (kept
 * identical so they drop straight into inject-wpt-block's `<idx>_<safeKey>.png`
 * glob). Index is the position in the flattened list, zero-padded to 3 digits
 * to match Kotlin's `String.format("%03d_%s.png", ...)`.
 */
export function expectedPngNames(doc) {
  const flat = flattenComponents(composeRoots(doc));
  return flat.map((c, i) => `${String(i).padStart(3, '0')}_${safeName(c.name)}.png`);
}

/**
 * The single COMPOSED-capture PNG name for a fixture (TITAN Round 3): the WPT
 * test key sanitised + ".png". The app derives the SAME name from the inbox
 * filename (TitanInbox.composedTestKey + composedPngName) so the feeder's poll
 * target, the on-device write, and inject-wpt-block's `diffComposedVsRef` glob
 * all agree.
 *
 * Takes the host-side fixture BASENAME (e.g.
 * `wpt__css-color__background-color-hsl-001.json`, optionally with the feeder's
 * `<NNNN>-` index prefix): strip the prefix + `.json`, then apply inject's
 * exact `safe()` class (`[^A-Za-z0-9._-] → _`, dot KEPT). For a WPT key this is
 * the identity + ".png".
 */
export function composedPngName(fixtureBasename) {
  const stem = String(fixtureBasename)
    .replace(/^\d+-/, '')      // drop the feeder's FIFO index prefix if present
    .replace(/\.json$/i, '');  // drop the .json extension
  return stem.replace(/[^A-Za-z0-9._-]/g, '_') + '.png';
}

// ── PNG validation ───────────────────────────────────────────────────────────

/**
 * True when `buf` is a fully-parseable PNG. Guards against the known
 * adb-pull truncation flake (a half-written PNG has the signature but throws
 * on full decode). Uses pngjs sync decode — the same lib the compare pipeline
 * uses, so "parses here" == "parses downstream".
 */
export function pngIsValid(buf) {
  if (!buf || buf.length < 8) return false;
  // PNG 8-byte signature — cheap reject before the full decode.
  const sig = [0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a];
  for (let i = 0; i < 8; i++) if (buf[i] !== sig[i]) return false;
  try {
    const png = PNG.sync.read(buf);
    return png.width > 0 && png.height > 0;
  } catch {
    return false; // truncated / corrupt → caller retries the pull
  }
}
