//
// tools/titan/safe-name.mjs
//
// THE single source of truth for the TITAN compare-pipeline PNG-name
// sanitiser. Every host-side filename that the compare pipeline pairs on
// — what the feeders WRITE to their --out dirs and what
// inject-wpt-block.mjs GLOBS back out — flows through this one function so
// a producer and a consumer can never disagree on how a key is sanitised.
//
// Why a standalone, dependency-free module (rather than living in
// feed-lib.mjs): the web-harness capture drivers
// (apps/web-harness/capture-screenshots{,-hires}.mjs) also need it, and
// they run under a workspace that does NOT vendor pngjs. Importing feed-lib
// (which pulls in pngjs for PNG validation) from web-harness would risk an
// unresolved-dependency crash. This file imports NOTHING, so every codebase
// — tools/titan and apps/web-harness alike — can share it safely.
//
// Character class: `[^A-Za-z0-9._-] → _`. The dot is KEPT. This is the
// class the compare pipeline relies on: inject-wpt-block.mjs's diff globs
// (`endsWith('_' + safe(key) + '.png')` and `safe(testKey) + '.png'`), the
// web composed/per-component capture filenames, and the on-device COMPOSED
// capture rules (apps/android-harness .../TitanInbox.kt composedPngName;
// apps/ios-harness .../ScreenshotManager.swift safeCaptureName) ALL keep
// the dot. Picking a class that dropped the dot would sanitise a WPT test
// key containing a "." differently on the producer vs the consumer side and
// silently drop that platform's column (the exact n/a bug the WPT-fidelity
// campaign fought). Keeping the dot makes every one of those sites agree
// byte-for-byte.

/**
 * Sanitise a WPT test key / component name into a compare-pipeline-safe
 * filename stem. Coerces to String first so a numeric or nullish input can
 * never throw. Replaces every character outside `[A-Za-z0-9._-]` with `_`.
 */
export function safe(name) {
  return String(name).replace(/[^A-Za-z0-9._-]/g, '_');
}

/**
 * THE single source of truth for the per-test FIXTURE STEM — the filename
 * stem every stem-derived artifact of a WPT test uses:
 *
 *   fixtures/wpt/<section>/<stem>.json            (extract-fixture.mjs)
 *   fixtures/wpt/<section>/<stem>__ref.json       (extract-fixture.mjs)
 *   tools/wpt/refs/<sha>/<rev>/<section>/<stem>.png (capture-browser-ref.mjs)
 *   wpt__<section>__<stem>__<idx>                 (build-combined-fixture.mjs
 *                                                  component keys)
 *   wpt__<section>__<stem>                        (inject-wpt-block.mjs
 *                                                  composed testKey)
 *
 * WHY THIS EXISTS (wave-21 skeptic finding — 52 A+B-bucket collisions):
 * every one of those sites used to derive the stem as
 * `basename(testRel, '.html')`, silently FLATTENING the subdirectory part
 * of nested test paths. `css/css-break/flexbox/monolithic-overflow-001
 * .tentative.html` and `css/css-break/grid/monolithic-overflow-001
 * .tentative.html` both became `monolithic-overflow-001.tentative.json`
 * under fixtures/wpt/css-break/ — the second extraction OVERWROTE the
 * first, and the same stem collision repeated downstream in the combined
 * fixture's component keys and the browser-ref PNG cache (52 colliding
 * pairs across css-break/css-grid/css-values/selectors/css-layout-api).
 *
 * THE RULE: encode the subdirectory chain into the stem, joined with the
 * repo's `__` slot separator:
 *
 *   css/<section>/<name>.html            → <name>          (unchanged —
 *                                          zero churn for top-level tests)
 *   css/<section>/<sub>/<name>.html      → <sub>__<name>
 *   css/<section>/<a>/<b>/<name>.html    → <a>__<b>__<name>
 *
 * `__` is safe against real stems: the corpus (12 625 bucket-A+B paths at
 * the wave-21 pin) contains NO `__` in any path segment, and every segment
 * is already inside safe()'s `[A-Za-z0-9._-]` class — verified before this
 * helper landed, and each segment is passed through safe() anyway so a
 * future corpus re-pin cannot smuggle an unsafe character into a filename.
 * The `__ref` fixture suffix cannot collide either: that would need one
 * test's encoded stem to equal another's stem + '__ref', which was checked
 * against the full A+B corpus (zero hazards — no segment is named 'ref'
 * with a matching sibling).
 *
 * The testRel path (`css/<section>/<sub>/<name>.html`) REMAINS the identity
 * key everywhere (keyMap.test, manifest results, bucket lists) — this
 * helper only decides what the derived FILENAMES look like.
 *
 * Depth contract: `parts.length < 4` (i.e. `css/<section>/<name>.html` or
 * shallower) keeps the bare basename — byte-identical to the historical
 * `basename(testRel, '.html')` for every top-level test, so the existing
 * extracted corpus needs no re-extraction outside subdirectory tests.
 */
export function fixtureStem(testRel) {
  // Posix split — testRel keys are always forward-slash repo-relative
  // (the format wpt-buckets.json pins); String() guards nullish input.
  const parts = String(testRel).split('/');
  // Strip the exact '.html' suffix, mirroring the historical
  // `basename(testRel, '.html')` semantics (case-sensitive, suffix-only).
  const name = parts[parts.length - 1].replace(/\.html$/, '');
  // Top-level test (css/<section>/<name>.html) — bare stem, zero churn.
  if (parts.length < 4) return safe(name);
  // Nested test — everything between the section segment (index 1) and the
  // filename is the subdirectory chain; join with the `__` slot separator.
  // safe() per segment keeps the result filesystem-safe (a no-op for the
  // entire current corpus — see the doc block).
  return parts.slice(2, -1).concat(name).map(safe).join('__');
}
