// PropertyTracker.ts — web mirror of the Compose runtime's PropertyTracker
// (runtimes/compose/.../PropertyTracker.kt). CLAUDE.md per-property contract:
// "No silent fallthroughs. If a value variant isn't supported on this
// platform yet, log it via the PropertyTracker" — this is that logger.
//
// Semantics match the Kotlin object: markHandled / markUnhandled /
// logUnhandled / getReport / reset. One deliberate difference: logUnhandled
// warns ONCE per (type, context) pair — the web engine runs inside a React
// render loop, so unconditional warns would flood the console on every
// recomposition. The occurrence COUNTS still accumulate on every call, so
// the report stays an honest frequency record.

// Property types that were successfully converted into at least one CSS declaration.
const handled = new Set<string>();
// Property types that were seen but produced no CSS output (unsupported/unparseable).
const unhandled = new Set<string>();
// Per-type encounter counts (both handled and unhandled encounters).
const occurrences = new Map<string, number>();
// Keys already warned about — the log-once guard (type + optional context).
const warned = new Set<string>();

// Bump the encounter counter for a type — shared by both mark* paths.
function bump(propertyType: string): void {
  occurrences.set(propertyType, (occurrences.get(propertyType) ?? 0) + 1);
}

/** Mark a property type as successfully handled (moves it out of unhandled). */
export function markHandled(propertyType: string): void {
  handled.add(propertyType);                                        // record success
  unhandled.delete(propertyType);                                   // success anywhere clears the failure flag
  bump(propertyType);                                               // count the encounter
}

/** Mark a property type as unhandled — unless it was handled elsewhere. */
export function markUnhandled(propertyType: string): void {
  if (!handled.has(propertyType)) unhandled.add(propertyType);      // don't shadow a real success
  bump(propertyType);                                               // count the encounter
}

/**
 * Mark unhandled AND warn — but only ONCE per (type, context) pair so the
 * render loop can call this freely without spamming the console.
 */
export function logUnhandled(propertyType: string, context?: string): void {
  markUnhandled(propertyType);                                      // always update the sets/counts
  const key = context ? `${propertyType}::${context}` : propertyType;// log-once key
  if (warned.has(key)) return;                                      // already reported this one
  warned.add(key);                                                  // remember so we stay quiet next time
  // eslint-disable-next-line no-console — the warn IS the feature here.
  console.warn(
    context
      ? `[PropertyTracker] Unhandled property: ${propertyType} (in ${context})`
      : `[PropertyTracker] Unhandled property: ${propertyType}`,
  );
}

/** Shape of the coverage report — mirrors Compose's PropertyReport fields. */
export interface PropertyReport {
  handled: string[];                                                // sorted list of handled types
  unhandled: string[];                                              // sorted list of unhandled types
  coverage: number;                                                 // handled / (handled + unhandled), 1 when empty
  totalOccurrences: number;                                         // total property instances processed
  topUnhandled: Array<[string, number]>;                            // up to 10 most-frequent unhandled types
}

/** Build the coverage report from the current counters. */
export function getReport(): PropertyReport {
  const total = handled.size + unhandled.size;                      // distinct types seen
  return {
    handled: [...handled].sort(),                                   // stable, sorted output
    unhandled: [...unhandled].sort(),
    coverage: total > 0 ? handled.size / total : 1,                 // empty run counts as full coverage
    totalOccurrences: [...occurrences.values()].reduce((a, b) => a + b, 0),
    topUnhandled: [...occurrences.entries()]
      .filter(([t]) => unhandled.has(t))                            // only failures rank here
      .sort((a, b) => b[1] - a[1])                                  // most frequent first
      .slice(0, 10),                                                // cap like the Compose report
  };
}

/** True when the type has been seen as unhandled (and never handled). */
export function isUnhandled(propertyType: string): boolean {
  return unhandled.has(propertyType);
}

/** True when the type has been successfully handled at least once. */
export function isHandled(propertyType: string): boolean {
  return handled.has(propertyType);
}

/** Encounter count for a type (0 when never seen). */
export function getOccurrences(propertyType: string): number {
  return occurrences.get(propertyType) ?? 0;
}

/** Reset everything — used between test runs / fixture reloads. */
export function reset(): void {
  handled.clear();                                                  // wipe successes
  unhandled.clear();                                                // wipe failures
  occurrences.clear();                                              // wipe counters
  warned.clear();                                                   // re-arm the log-once guard
}
