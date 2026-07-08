// AspectRatioValue.ts — typed shape + extractor for the CSS `aspect-ratio`
// property.  The IR emits three distinct shapes (confirmed against the
// sizing/aspect-ratio.json fixture after running the Kotlin converter):
//   "auto"                                              -> { isAuto: true,  ratio: 0 }
//   { ratio:{w,h},          normalizedRatio: w/h }      -> { isAuto: false, ratio: w/h }
//   { ratio:{value:N},      normalizedRatio: N }        -> { isAuto: false, ratio: N }
//   { ratio:{auto,w,h},     normalizedRatio: w/h }      -> { isAuto: false, ratio: w/h }
// The last form corresponds to `aspect-ratio: auto 16/9` — CSS still applies
// the explicit ratio when height isn't otherwise constrained, so we treat it
// as a concrete ratio rather than auto.

// Output consumed by the SizeApplier to emit `aspectRatio: '<ratio>'` inline.
export interface AspectRatioValue {
  // 0 when isAuto is true (sentinel); width / height otherwise.
  ratio: number;
  // Set when the IR emitted the bare string "auto" (no explicit ratio).
  isAuto: boolean;
}

// Pure extractor — returns null for anything we don't recognise so the caller
// can simply skip emitting an aspectRatio style.  Never throws.
export function extractAspectRatio(data: unknown): AspectRatioValue | null {
  // Shape #1 — bare string "auto".
  if (data === 'auto') return { ratio: 0, isAuto: true };
  // Everything else must be an object envelope.
  if (typeof data !== 'object' || data === null) return null;
  // Cast once so subsequent field accesses stay readable.
  const d = data as Record<string, unknown>;
  // Preferred path — Kotlin already did the division for us, so trust it.
  if (typeof d.normalizedRatio === 'number') {
    return { ratio: d.normalizedRatio, isAuto: false };
  }
  // Fallback path — compute from the nested { w, h } pair if present.
  const r = d.ratio as Record<string, unknown> | undefined;
  if (r && typeof r === 'object') {
    // w/h pair takes precedence over the single-number `value` form.
    if (typeof r.w === 'number' && typeof r.h === 'number' && r.h !== 0) {
      return { ratio: (r.w as number) / (r.h as number), isAuto: false };
    }
    // Single numeric ratio (e.g. `aspect-ratio: 1.5`).
    if (typeof r.value === 'number') {
      return { ratio: r.value, isAuto: false };
    }
  }
  // Unrecognised shape — caller should not emit anything.
  return null;
}
