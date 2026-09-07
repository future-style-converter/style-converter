/**
 * Value extractors for IR data.
 *
 * Provides utility functions to safely extract normalized values
 * from IR property data in various formats.
 *
 * LEGACY surface: only `StyleBuilder.ts` (the pre-migration monolith) and a
 * handful of the per-category `_shared.ts` helpers still import from here;
 * the migrated triplets use the `engine/core/types/` value twins instead.
 * The px / degrees / ms / int extractors that used to live here had ZERO
 * importers anywhere in the repo (finding A6#13) and were deleted rather
 * than left as a second, drifting copy of the engine-side converters
 * (the private `convertToPx` unit table went with them — `extractPx` was
 * its only caller).
 */

import { extractColor, extractOpacity } from '../colors/ColorMapper';

/**
 * Extract a length as CSS string (with unit).
 */
export function extractLength(data: unknown): string | null {
  if (data === null || data === undefined) return null;

  // Direct number (assumed px)
  if (typeof data === 'number') {
    return `${data}px`;
  }

  // String value
  if (typeof data === 'string') {
    return data;
  }

  if (typeof data === 'object') {
    const obj = data as Record<string, unknown>;

    // Check for px field
    if (typeof obj.px === 'number') return `${obj.px}px`;

    // Check for original string
    if (typeof obj.original === 'string') return obj.original;

    // Check for original object with {v, u} (e.g. borderRadius: {original: {v: 50, u: "PERCENT"}})
    if (typeof obj.original === 'object' && obj.original !== null) {
      const orig = obj.original as Record<string, unknown>;
      if (typeof orig.v === 'number') {
        const unit = (orig.u as string)?.toLowerCase() || 'px';
        return `${orig.v}${unit}`;
      }
    }

    // Check for value + unit
    if (typeof obj.v === 'number') {
      const unit = (obj.u as string)?.toLowerCase() || 'px';
      return `${obj.v}${unit}`;
    }
  }

  return null;
}



/**
 * Extract a keyword string from IR data.
 */
export function extractKeyword(data: unknown): string | null {
  if (data === null || data === undefined) return null;

  if (typeof data === 'string') {
    return data;
  }

  if (typeof data === 'object') {
    const obj = data as Record<string, unknown>;

    if (typeof obj.keyword === 'string') return obj.keyword;
    if (typeof obj.type === 'string') return obj.type;
    if (typeof obj.value === 'string') return obj.value;
  }

  return null;
}

/**
 * Extract a float value from IR data.
 */
export function extractFloat(data: unknown): number | null {
  if (data === null || data === undefined) return null;

  if (typeof data === 'number') {
    return data;
  }

  if (typeof data === 'object') {
    const obj = data as Record<string, unknown>;

    if (typeof obj.value === 'number') return obj.value;
    if (typeof obj.v === 'number') return obj.v;
  }

  return null;
}

// Re-export color functions
export { extractColor, extractOpacity };
