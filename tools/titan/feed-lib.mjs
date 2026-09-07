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
// The ONE canonical compare-pipeline sanitiser (dot KEPT). Re-exported below
// so callers can import it from feed-lib too, and used for every HOST-side
// filename this module derives (the names inject-wpt-block.mjs globs for).
import { safe } from './safe-name.mjs';

// Re-export the shared sanitiser so `import { safe } from './feed-lib.mjs'`
// resolves to the exact same function as the standalone module — there is
// only one implementation in the whole pipeline.
export { safe };

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
    // wave-35 lane B2: WPT corpus root the per-test doc's `fontFaces[].src`
    // resolves against, so the feeder can copy the font FILE into the device
    // sandbox. null ⇒ no font hop (every pre-wave-35 caller), which degrades
    // to exactly the wave-34 behaviour: the natives decode the list and
    // render with their bundled face.
    wptDir: get('--wpt-dir'),
  };
}

// ── wave-35 lane B2: the @font-face file hop (shared by both feeders) ────────
//
// The IR carries a corpus-relative PATH, never a payload (extract-fixture.mjs
// explains why: a real webfont is 50–500 KB). The web harness serves that path
// off a static route over the host's corpus; a device cannot reach the host's
// disk at all, so the native half must COPY — and the copy rides the feeders'
// existing fixture channel, one directory over from the inbox:
//
//   Android:  /sdcard/Android/data/<pkg>/files/fonts/<src>   (adb push)
//   iOS:      <app data container>/Documents/fonts/<src>     (host fs copy)
//
// The RELATIVE PATH IS PRESERVED VERBATIM under that root, and that is the
// whole contract with the two runtimes: each `DocumentFontRegistry` resolves
// `File(fontsDir, face.src)` / `fontsDir.appending(face.src)` with no name
// mangling, so there is no shared escaping rule to drift between four
// codebases. Two faces from different corpus directories therefore cannot
// collide even when their basenames match.

/** The font file extensions the wire admits (spec 01 §5), mirroring
 *  extract-fixture.mjs's FONT_FORMATS and vite.config.ts's
 *  FONT_CONTENT_TYPES. A CLOSED table on purpose: this function decides what
 *  gets written into an app sandbox, so it must never become a general file
 *  copier for whatever a corpus path happens to point at. */
const FONT_EXTENSIONS = new Set(['woff2', 'woff', 'ttf', 'otf', 'ttc', 'otc']);

/** Every distinct, usable `fontFaces[].src` in a decoded IR document, in
 *  document order. Pure — no disk access; the caller resolves.
 *
 *  Deduped because css-fonts-4 §4.1 lets many faces (weights/styles) name one
 *  file, and pushing the same 261 KB woff once per face would multiply the
 *  slowest step of a native section run for no gain. */
export function documentFontSrcs(doc) {
  const faces = doc?.fontFaces;
  if (!Array.isArray(faces)) return [];
  const out = [];
  const seen = new Set();
  for (const f of faces) {
    const src = typeof f?.src === 'string' ? f.src.trim() : '';
    if (!src || seen.has(src)) continue;
    seen.add(src);
    out.push(src);
  }
  return out;
}

/** Resolve one `src` under the corpus root, or null when it is not a font
 *  file this hop will carry.
 *
 *  The corpus is third-party content and `src` came out of it, so the checks
 *  mirror vite.config.ts's route rather than trusting the producer:
 *    * containment is tested on the RESOLVED absolute path (a `..` chain or an
 *      absolute token must never let a fixture name a file outside the corpus
 *      and get it written into an app's sandbox);
 *    * the extension must be one the wire admits;
 *    * the file must exist and be a regular file.
 *  A null return is a DECLINE, never a clamp — the caller logs it and the
 *  runtimes degrade to their bundled face with the loud stamp. */
export function resolveFontFile(wptDir, src, { resolve, existsSync, statSync }) {
  if (!wptDir || typeof src !== 'string' || src === '') return null;
  if (src.startsWith('/') || /^[a-z][a-z0-9+.-]*:/i.test(src)) return null;
  const root = resolve(wptDir);
  const abs = resolve(root, src);
  if (abs !== root && !abs.startsWith(root + '/')) return null;
  const ext = /\.([A-Za-z0-9]+)$/.exec(abs)?.[1]?.toLowerCase();
  if (!ext || !FONT_EXTENSIONS.has(ext)) return null;
  try {
    if (!existsSync(abs) || !statSync(abs).isFile()) return null;
  } catch {
    return null;
  }
  return abs;
}

// ── wave-39 lane A2: the REPLACED-ELEMENT image hop (shared by both feeders) ─
//
// The second asset channel, and a deliberate CLONE of the font hop above
// rather than a generalisation of it. Same shape, same guarantees:
//
//   Android:  /sdcard/Android/data/<pkg>/files/images/<src>   (adb push)
//   iOS:      <app data container>/Documents/images/<src>     (host fs copy)
//
// WHY IT IS A SEPARATE DIRECTORY, not `fonts/` with a wider extension table:
// the two sandboxes have different lifetimes in the runtimes' heads (a font is
// registered with the text stack at decode time and must be there BEFORE
// measurement; an image is decoded lazily at paint time) and different
// decline semantics (a declined font degrades to the bundled face — still
// text; a declined image paints nothing at all). Keeping them apart means a
// future change to one channel cannot silently widen what the other writes
// into an app sandbox.
//
// WHY IT IS NEEDED AT ALL. wave-36 lane M1 put the replaced element's SOURCE
// on the wire (`meta.attrs.src`, a corpus-relative PATH — extract-fixture.mjs
// REPLACED_SRC_TAGS) and taught the WEB harness to resolve it through its
// /wpt-image/ route. The natives got the path and nothing to open: a device
// cannot reach the host's corpus at all, so both painted an EMPTY box where
// the browser ref paints the image. MEASURED (wave38-final, css-writing-modes
// img-intrinsic-size-contribution-001/002): web 0.9623 PASS against natives
// 0.8654 fail, with the ref's 200×100 blue raster simply absent from both
// native captures. Same shape on the whole depth-48 replaced cluster.
//
// The RELATIVE PATH IS PRESERVED VERBATIM under the images root — the same
// contract, and for the same reason, as the font hop: each runtime's
// DocumentImageRegistry resolves `File(imagesDir, src)` /
// `imagesDir.appending(src)` with no name mangling, so there is no shared
// escaping rule to drift between four codebases, and two support images from
// different corpus directories cannot collide on a shared basename.

/** Image file extensions this hop will carry, mirroring extract-fixture.mjs's
 *  REPLACED_IMAGE_FORMATS (the table that decided what got onto the wire) and
 *  vite.config.ts's IMAGE_CONTENT_TYPES (the web route that serves the same
 *  paths). A CLOSED table for the same reason FONT_EXTENSIONS is one: this
 *  function decides what gets written into an app sandbox and must never
 *  become a general file copier for whatever a corpus path points at.
 *
 *  `svg` IS admitted here even though NEITHER native can rasterise one. The
 *  gate on platform decode capability lives in the RUNTIMES (Compose's
 *  DocumentImageRegistry declines it loudly, exactly as its font twin declines
 *  a WOFF), not in the host channel — the feeder's job is to deliver what the
 *  wire named, and a host-side gate would hide the platform limit behind a
 *  "file was never sent" that reads like a plumbing bug. */
const REPLACED_IMAGE_EXTENSIONS = new Set([
  'png', 'gif', 'jpg', 'jpeg', 'webp', 'bmp', 'ico', 'svg', 'avif',
]);

/** The four `meta.sourceTag` values extract-fixture.mjs's REPLACED_SRC_TAGS
 *  will ever put a `meta.attrs.src` on. Read here as a CORROBORATION of the
 *  wire, not as a re-derivation: `attrs.src` on any other tag is a shape this
 *  producer does not emit, and pushing a file for it would mean the feeder had
 *  invented a delivery the renderers were never told to expect. */
const REPLACED_SRC_TAGS = new Set(['img', 'embed', 'object', 'video']);

/** Every distinct, deliverable `meta.attrs.src` in a decoded IR document, in
 *  document order. Pure — no disk access; the caller resolves.
 *
 *  Walks the v2 FLAT component list and, for robustness against a v1 document
 *  (nested `children`), recurses into children too: the feeders decode whatever
 *  IR the section pipeline handed them, and a v1 fixture silently delivering no
 *  images would be the same invisible failure the font hop's ordering contract
 *  guards against.
 *
 *  Deduped because a single test routinely paints ONE support image in many
 *  boxes (css-grid grid-abspos-staticpos-align-self-img-001 references
 *  colors-8x16.png from 41 components) and pushing it 41 times would multiply
 *  the slowest step of a native section run for no gain.
 *
 *  `data:` sources are SKIPPED: the payload is already on the wire, so there is
 *  no file to copy — the runtimes decode those bytes directly. Returning them
 *  here would hand the resolver a path it must then re-decline. */
export function documentReplacedSrcs(doc) {
  const out = [];
  const seen = new Set();
  const visit = (c) => {
    if (!c || typeof c !== 'object') return;
    const tag = typeof c.meta?.sourceTag === 'string' ? c.meta.sourceTag.toLowerCase() : null;
    const src = typeof c.meta?.attrs?.src === 'string' ? c.meta.attrs.src.trim() : '';
    if (src && tag && REPLACED_SRC_TAGS.has(tag) && !/^data:/i.test(src) && !seen.has(src)) {
      seen.add(src);
      out.push(src);
    }
    // v1 nested shape — harmless on a v2 doc (no `children` key).
    for (const kid of Array.isArray(c.children) ? c.children : []) visit(kid);
  };
  for (const c of Array.isArray(doc?.components) ? doc.components : []) visit(c);
  return out;
}

/** Resolve one replaced-element `src` under the corpus root, or null when it
 *  is not an image file this hop will carry.
 *
 *  Byte-for-byte the same check sequence as [resolveFontFile] — containment on
 *  the RESOLVED absolute path (a `..` chain or an absolute token must never let
 *  a fixture name a file outside the corpus and get it written into an app's
 *  sandbox), a closed extension table, and a regular-file existence test — with
 *  only the table swapped. Deliberately duplicated rather than factored into a
 *  shared `resolveCorpusFile(table)`: the two tables are the two channels'
 *  entire security surface, and a shared helper is one refactor away from a
 *  caller passing the wrong one.
 *
 *  A null return is a DECLINE, never a clamp — the caller logs it and the
 *  runtimes paint the empty box they painted before this channel existed. */
export function resolveReplacedImageFile(wptDir, src, { resolve, existsSync, statSync }) {
  if (!wptDir || typeof src !== 'string' || src === '') return null;
  if (src.startsWith('/') || /^[a-z][a-z0-9+.-]*:/i.test(src)) return null;
  const root = resolve(wptDir);
  const abs = resolve(root, src);
  if (abs !== root && !abs.startsWith(root + '/')) return null;
  const ext = /\.([A-Za-z0-9]+)$/.exec(abs)?.[1]?.toLowerCase();
  if (!ext || !REPLACED_IMAGE_EXTENSIONS.has(ext)) return null;
  try {
    if (!existsSync(abs) || !statSync(abs).isFile()) return null;
  } catch {
    return null;
  }
  return abs;
}

// ── retro R8b (A9#3): the --wpt-dir PREFLIGHT shared by both feeders ────────
//
// Both resolvers above return null for a MISSING corpus root, and that null is
// the same value as a genuinely unresolvable path. So a feeder invoked without
// `--wpt-dir` (run-titan.sh's --all-platforms path did exactly this) logged
// "DECLINED (unresolvable/not a font)" for every face, the pre-raster and WOFF
// pre-passes silently no-op'd (both `return` on `!wptDir`), the capture went
// ahead, and ARTIFACT scores — bundled-face text, empty image boxes — entered
// the manifest as if they measured the renderers. BACKLOG kept "refeeds must
// carry --wpt-dir" as an operator recipe; this helper is the guard that
// replaces the recipe: scan every fixture's asset srcs BEFORE any device is
// touched and refuse the whole run when the hop cannot happen.

/** Pure. Given the decoded IR docs of a batch (null entries tolerated — an
 *  unreadable fixture is reported per-fixture later) and the parsed `--wpt-dir`
 *  value, return the distinct font / image srcs the hops WOULD carry and
 *  whether the run must refuse: `fatal` is true iff at least one src exists and
 *  wptDir is null. The message is pre-rendered so both feeders log one text. */
export function assetHopPreflight(docs, wptDir) {
  const fontSrcs = new Set();   // distinct `fontFaces[].src` across the batch
  const imageSrcs = new Set();  // distinct replaced-element `meta.attrs.src`
  for (const doc of docs ?? []) {
    for (const s of documentFontSrcs(doc)) fontSrcs.add(s);      // the SAME walker pushFontFaces uses
    for (const s of documentReplacedSrcs(doc)) imageSrcs.add(s); // the SAME walker pushReplacedImages uses
  }
  // Srcs declared but nowhere to resolve them: the hop would skip silently.
  const fatal = fontSrcs.size + imageSrcs.size > 0 && !wptDir;
  return {
    fontSrcs: [...fontSrcs], imageSrcs: [...imageSrcs], fatal,
    // One sentence naming the count, the cause and the fix — the line an
    // operator sees at the top of the feed log instead of N cryptic declines.
    message: fatal
      ? `FATAL: fixtures declare ${fontSrcs.size} @font-face src(s) + ${imageSrcs.size} replaced-image src(s) ` +
        'but --wpt-dir was not given — the asset hop would silently skip and every capture would score ' +
        'ARTIFACTS (bundled-face text / empty image boxes). Pass --wpt-dir <corpus root> (tools/wpt).'
      : null,
  };
}

/** The decline text for one src when the corpus root is ABSENT. Kept apart
 *  from the resolvers' null so a per-fixture log names the real cause; the
 *  feeders branch on `!wptDir` BEFORE calling resolveFontFile /
 *  resolveReplacedImageFile (normally unreachable after the preflight above —
 *  it exists so a future caller that skips the preflight still cannot log
 *  "unresolvable" for a root that was simply never given). */
export const NO_WPT_DIR_DECLINE = 'DECLINED (no --wpt-dir: corpus root absent, nothing to resolve against)';

// ── Filename derivation (mirror of the Android capture path) ─────────────────

/** Sanitise a component name for the ON-DEVICE per-component filename —
 *  identical char class to the Kotlin ScreenshotManager.saveScreenshot
 *  (`Regex("[^a-zA-Z0-9_-]")`, apps/android-harness .../ScreenshotManager.kt),
 *  which DROPS the dot. This predicts the exact name the app WROTE, so the
 *  feeder polls for the right file. It is deliberately NOT the compare-
 *  pipeline `safe()` (which keeps the dot): for a component name containing a
 *  dot the device writes `_` where inject globs `.`, so the feeder must poll
 *  with the device rule here and then RE-NAME to the compare `safe()` host
 *  name on pull (see expectedPngNames' deviceFile vs hostFile — the same
 *  decouple feed-ios.mjs's deviceSafeName/safeName split uses). For the
 *  common WPT name (alnum/`-`/`_`, no dot) it equals `safe()` exactly. */
export function deviceSafeName(name) {
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
    // Mirror rule (ScreenshotCaptureScreen.kt flattenComponents): a child
    // that DEPENDS ON ITS BACKDROP is suppressed standalone on every
    // device — with nothing behind it, backdrop-filter is the identity and
    // a blend mode composites against noise. This walk omitted the rule,
    // so on any fixture with such children the feeder's manifest listed
    // PNGs the devices never write: the poll waited for phantoms AND, the
    // names being POSITIONAL, every entry after the first suppressed child
    // was misaligned against the device's numbering.
    kids.forEach((k) => { if (!dependsOnBackdrop(k)) walk(k); });
  };
  (roots ?? []).forEach(walk);
  return out;
}

/**
 * Byte-for-byte mirror of dependsOnBackdrop in ScreenshotCaptureScreen.kt
 * (and its twins in web CaptureGallery.tsx / iOS ScreenshotCaptureView) —
 * the other half of the suppression contract the walk above enforces.
 */
export function dependsOnBackdrop(component) {
  for (const p of component?.properties ?? []) {
    if (p.type === 'BackdropFilter') return true;
    if (p.type === 'MixBlendMode') {
      const v = tryStringValue(p.data)?.toLowerCase();
      if (v != null && v !== 'normal') return true;
    }
  }
  return false;
}

/**
 * The ordered per-component capture manifest the app will write for this IR
 * document: `{ index, name, deviceFile, hostFile }` per flattened component.
 *
 * deviceFile — the feeder's POLL target: `%03d_<deviceSafeName(name)>.png`,
 *   matching the Kotlin ScreenshotManager.saveScreenshot rule (dot DROPPED)
 *   so the poll waits for the filename the app actually wrote.
 * hostFile — the feeder's OUTPUT name (what it writes to --out):
 *   `%03d_<safe(name)>.png`, using the compare-pipeline sanitiser (dot KEPT)
 *   so the pulled PNG drops straight into inject-wpt-block's
 *   `endsWith('_' + safe(key) + '.png')` glob.
 *
 * For a component name containing a "." the two names DIFFER (the feeder pulls
 * the device file, renames to the host file) — closing the silent n/a-column
 * bug where the Android-written drop-dot name never matched inject's keep-dot
 * glob. For the common WPT name (no dot) deviceFile === hostFile. This mirrors
 * feed-ios.mjs's expectedCaptures exactly. Index is zero-padded to 3 digits to
 * match Kotlin's `String.format("%03d_%s.png", ...)`.
 */
export function expectedPngNames(doc) {
  const flat = flattenComponents(composeRoots(doc));
  return flat.map((c, i) => {
    const pad = String(i).padStart(3, '0');
    return {
      index: i,
      name: c.name,
      deviceFile: `${pad}_${deviceSafeName(c.name)}.png`, // poll target (device rule)
      hostFile: `${pad}_${safe(c.name)}.png`,             // compare-glob output name
    };
  });
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
 * `<NNNN>-` index prefix): strip the prefix + `.json`, then apply the shared
 * `safe()` class (`[^A-Za-z0-9._-] → _`, dot KEPT). For a WPT key this is the
 * identity + ".png". Both the poll target and the pulled host name use this
 * one name because the Android COMPOSED capture rule (TitanInbox.kt
 * composedPngName) also keeps the dot, so device and compare names agree.
 */
export function composedPngName(fixtureBasename) {
  const stem = String(fixtureBasename)
    .replace(/^\d+-/, '')      // drop the feeder's FIFO index prefix if present
    .replace(/\.json$/i, '');  // drop the .json extension
  return safe(stem) + '.png';  // shared compare-pipeline sanitiser (dot KEPT)
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
