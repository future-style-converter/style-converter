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
