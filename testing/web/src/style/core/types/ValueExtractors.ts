/**
 * Value extractors for IR data.
 *
 * Provides utility functions to safely extract normalized values
 * from IR property data in various formats.
 */

import { extractColor, extractOpacity } from '../colors/ColorMapper';

/**
 * Extract a length value as pixels from IR data.
 */
export function extractPx(data: unknown): number | null {
  if (data === null || data === undefined) return null;

  // Direct number (assumed px)
  if (typeof data === 'number') {
    return data;
  }

  if (typeof data === 'object') {
    const obj = data as Record<string, unknown>;

    // Check for px field
    if (typeof obj.px === 'number') return obj.px;

    // Check for pixels field
    if (typeof obj.pixels === 'number') return obj.pixels;

    // Check for value field
    if (typeof obj.value === 'number' && !obj.u) return obj.value;

    // Check for length object with v and u
    if (typeof obj.v === 'number') {
      const unit = (obj.u as string)?.toLowerCase() || 'px';
      return convertToPx(obj.v, unit);
    }
  }

  // String value (e.g., "10px")
  if (typeof data === 'string') {
    const match = data.match(/^(-?\d+(?:\.\d+)?)(px|pt|em|rem|%|vw|vh)?$/i);
    if (match) {
      const value = parseFloat(match[1]);
      const unit = match[2]?.toLowerCase() || 'px';
      return convertToPx(value, unit);
    }
  }

  return null;
}

/**
 * Convert a length value with unit to pixels.
 * Note: Relative units (em, rem, %, vw, vh) return null as they require context.
 */
function convertToPx(value: number, unit: string): number | null {
  switch (unit.toLowerCase()) {
    case 'px':
    case 'dp':
      return value;
    case 'pt':
      return value * 1.333; // 96/72
    case 'pc':
      return value * 16;
    case 'in':
      return value * 96;
    case 'cm':
      return value * 37.795;
    case 'mm':
      return value * 3.7795;
    case 'q':
      return value * 0.945;
    // Relative units - cannot convert without context
    case 'em':
    case 'rem':
    case '%':
    case 'percent':
    case 'vw':
    case 'vh':
    case 'vmin':
    case 'vmax':
    case 'ch':
    case 'ex':
    case 'fr':
      return null;
    default:
      return value;
  }
}

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
 * Extract angle in degrees from IR data.
 */
export function extractDegrees(data: unknown): number | null {
  if (data === null || data === undefined) return null;

  if (typeof data === 'number') {
    return data;
  }

  if (typeof data === 'object') {
    const obj = data as Record<string, unknown>;

    if (typeof obj.deg === 'number') return obj.deg;
    if (typeof obj.degrees === 'number') return obj.degrees;

    // Convert from other units
    if (typeof obj.v === 'number') {
      const unit = (obj.u as string)?.toLowerCase() || 'deg';
      switch (unit) {
        case 'deg':
          return obj.v;
        case 'rad':
          return obj.v * (180 / Math.PI);
        case 'grad':
          return obj.v * 0.9;
        case 'turn':
          return obj.v * 360;
        default:
          return obj.v;
      }
    }
  }

  return null;
}

/**
 * Extract time in milliseconds from IR data.
 */
export function extractMs(data: unknown): number | null {
  if (data === null || data === undefined) return null;

  if (typeof data === 'number') {
    return data;
  }

  if (typeof data === 'object') {
    const obj = data as Record<string, unknown>;

    if (typeof obj.ms === 'number') return obj.ms;
    if (typeof obj.milliseconds === 'number') return obj.milliseconds;

    // Convert from seconds
    if (typeof obj.v === 'number') {
      const unit = (obj.u as string)?.toLowerCase() || 'ms';
      return unit === 's' ? obj.v * 1000 : obj.v;
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

/**
 * Extract an integer value from IR data.
 */
export function extractInt(data: unknown): number | null {
  const value = extractFloat(data);
  return value !== null ? Math.round(value) : null;
}

// Re-export color functions
export { extractColor, extractOpacity };
