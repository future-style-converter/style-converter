// ZoomExtractor.ts — IR `Zoom` → ZoomConfig.
//
// Mirrors the parser's value flavors exactly, one branch per sealed
// variant of `ZoomValue` in
// converter/src/main/kotlin/app/parsing/css/properties/longhands/rendering/
// ZoomPropertyParser.kt:
//
//   ZoomValue.Normal      → {"type":"normal"}
//   ZoomValue.Reset       → {"type":"reset"}
//   ZoomValue.Number      → {"type":"number","value":2}
//   ZoomValue.Percentage  → {"type":"percentage","value":150}
//
// Why this is NOT the shared `keywordOrRaw` fold any more (wave-36 lane
// M2): that helper reads `{value:<number>}` and stringifies it bare, so
// the PERCENTAGE variant came out as "150" — a 150× zoom instead of the
// authored 1.5×, i.e. a silently catastrophic value corruption — and the
// `reset` tag-only variant fell off its keyword allow-list (which admits
// only none/auto/normal) and was dropped without a trace. Both are exactly
// the "no silent fallthrough" failure mode CLAUDE.md forbids, so this
// property gets a dedicated fold that names every variant.

import { foldLast, type IRPropertyLike } from '../_phase10_shared';
import { logUnhandled } from '../PropertyTracker';
import type { ZoomConfig } from './ZoomConfig';

/**
 * One IR `Zoom` payload → the CSS token, or `undefined` when the payload
 * is a shape this extractor does not model (tracked, never silent).
 */
function zoomToken(data: unknown): string | undefined {
  // Defensive: a bare string payload (a hand-written fixture, or a future
  // wire that flattens tag-only variants) is already the CSS keyword.
  if (typeof data === 'string') return data.toLowerCase();
  if (typeof data === 'number') return String(data);
  if (data === null || typeof data !== 'object') return undefined;
  const o = data as Record<string, unknown>;
  switch (o.type) {
    case 'number':
      // `<number>` per css-viewport-1: 1 is unzoomed, 2 doubles used values.
      return typeof o.value === 'number' ? String(o.value) : undefined;
    case 'percentage':
      // `<percentage>`: the SUFFIX is load-bearing — `zoom: 150%` and
      // `zoom: 150` differ by two orders of magnitude.
      return typeof o.value === 'number' ? `${o.value}%` : undefined;
    case 'normal':
      // css-viewport-1: `normal` computes to the same used scale as `1`.
      return 'normal';
    case 'reset':
      // The legacy WebKit keyword ("do not inherit the ancestor's zoom").
      // Not in css-viewport-1, so most UAs reject the declaration — which
      // is the honest outcome: an invalid declaration is dropped by the
      // CSSOM and the element renders unzoomed, exactly as a UA without
      // the extension would. We do NOT rewrite it to `normal`: that would
      // invent a used value the author never wrote.
      return 'reset';
    default:
      // A variant the parser grew without this extractor — loud, per the
      // PropertyRegistry introspection contract.
      logUnhandled('Zoom', typeof o.type === 'string' ? o.type : 'unknown-shape');
      return undefined;
  }
}

export function extractZoom(properties: IRPropertyLike[]): ZoomConfig {
  // Last write wins, matching the cascade every other engine phase uses.
  return { value: foldLast(properties, 'Zoom', zoomToken) };
}
