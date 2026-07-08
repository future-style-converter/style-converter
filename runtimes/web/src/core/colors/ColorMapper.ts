/**
 * Maps IR colors to CSS color strings.
 *
 * Extracts color values from IR format and converts to CSS.
 */

import type { IRColor, SRGB } from '../types/ValueTypes';

export interface ExtractedColor {
  color: string;
  opacity?: number;
}

/**
 * Extract color from IR data format.
 */
export function extractColor(data: unknown): string | null {
  if (data === null || data === undefined) return null;

  // String primitive
  if (typeof data === 'string') {
    return data;
  }

  // Object with srgb
  if (typeof data === 'object') {
    const obj = data as Record<string, unknown>;

    // Check for srgb object
    if (obj.srgb) {
      const srgb = obj.srgb as SRGB;
      return srgbToString(srgb);
    }

    // Check for direct r, g, b properties
    if (typeof obj.r === 'number' && typeof obj.g === 'number' && typeof obj.b === 'number') {
      return srgbToString({
        r: obj.r,
        g: obj.g,
        b: obj.b,
        a: typeof obj.a === 'number' ? obj.a : undefined,
      });
    }

    // Check for hex value
    if (typeof obj.hex === 'string') {
      return obj.hex;
    }

    // Check for original string representation
    if (typeof obj.original === 'string') {
      return obj.original;
    }
  }

  return null;
}

/**
 * Convert sRGB object to CSS color string.
 */
export function srgbToString(srgb: SRGB): string {
  const r = Math.round(srgb.r * 255);
  const g = Math.round(srgb.g * 255);
  const b = Math.round(srgb.b * 255);
  const a = srgb.a ?? 1;

  if (a === 1) {
    return `rgb(${r}, ${g}, ${b})`;
  }
  return `rgba(${r}, ${g}, ${b}, ${a})`;
}

/**
 * Extract opacity from IR data.
 */
export function extractOpacity(data: unknown): number | null {
  if (data === null || data === undefined) return null;

  if (typeof data === 'number') {
    return data;
  }

  if (typeof data === 'object') {
    const obj = data as Record<string, unknown>;
    if (typeof obj.alpha === 'number') return obj.alpha;
    if (typeof obj.value === 'number') return obj.value;
  }

  return null;
}

/**
 * Parse a color value from IR property data.
 */
export function parseIRColor(data: unknown): IRColor | null {
  if (!data || typeof data !== 'object') return null;

  const obj = data as Record<string, unknown>;

  if (obj.srgb) {
    return {
      srgb: obj.srgb as SRGB,
      original: obj.original as string | undefined,
    };
  }

  // Direct sRGB properties
  if (typeof obj.r === 'number') {
    return {
      srgb: {
        r: obj.r as number,
        g: obj.g as number,
        b: obj.b as number,
        a: obj.a as number | undefined,
      },
    };
  }

  return null;
}
