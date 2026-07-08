// ColorValue.ts - Discriminated-union color type + extractor.
// Handles every shape documented in:
//   examples/primitives/colors-legacy.json  (hex, rgb/rgba, hsl/hsla -> original string/object)
//   examples/primitives/colors-modern.json  (hwb/lab/lch/oklab/oklch/color/color-mix/light-dark/relative)
//   examples/primitives/colors-named.json   (named colors + 'transparent' + 'currentColor')
// Quirk #5: Dynamic colors (color-mix, light-dark, relative, currentColor) have NO srgb key.
// Quirk #6: Alpha key is 'a' in legacy shapes, 'alpha' in modern shapes.

// Classification of dynamic (non-pre-resolved) color values.
export type DynamicKind =
  | 'currentColor'     // keyword; resolves to element's computed color at paint time
  | 'color-mix'        // mix of two colors in a given color space
  | 'light-dark'       // chooses between two colors based on color-scheme
  | 'relative'         // rgb(from red ...) relative color syntax
  | 'var';             // CSS variable reference — kept here for completeness

// Discriminated-union result of parsing a color shape.
export type ColorValue =
  | { kind: 'srgb'; r: number; g: number; b: number; a: number }   // pre-resolved sRGB (0..1 floats)
  | { kind: 'dynamic'; dynamicKind: DynamicKind; raw: unknown }     // needs runtime resolution; raw kept for CSS passthrough
  | { kind: 'unknown' };                                            // parse failure

// Extract the alpha channel from an sRGB object; default to fully opaque when missing/invalid.
function pickAlpha(srgb: Record<string, unknown>): number {
  if (typeof srgb.a === 'number') return srgb.a;                    // legacy 'a' key
  if (typeof srgb.alpha === 'number') return srgb.alpha;            // modern 'alpha' key (defensive)
  return 1.0;                                                        // no alpha -> opaque
}

// Decide which DynamicKind (if any) an 'original' shape represents.
// Returns null if 'original' doesn't look dynamic — caller should treat as unknown.
function classifyDynamic(original: unknown): DynamicKind | null {
  // String forms: 'currentColor' is the only dynamic bare string we recognise.
  if (typeof original === 'string') {
    if (original === 'currentColor' || original === 'currentcolor') return 'currentColor';
    return null;                                                    // named colors/hex are static even if srgb missing
  }
  // Object form: read the 'type' discriminator produced by the IR.
  if (original && typeof original === 'object') {
    const t = (original as Record<string, unknown>).type;
    if (t === 'color-mix') return 'color-mix';
    if (t === 'light-dark') return 'light-dark';
    if (t === 'relative') return 'relative';
    if (t === 'var') return 'var';                                  // defensive; not in fixtures yet
  }
  return null;
}

// Main entrypoint — never throws; returns {kind:'unknown'} on anything unparseable.
export function extractColor(data: unknown): ColorValue {
  if (data === null || data === undefined) return { kind: 'unknown' };// null-safety

  // Bare string — could occur when a property stores the raw representation only.
  // 'transparent' becomes zero-alpha black; 'currentColor' is dynamic; others we can't resolve here.
  if (typeof data === 'string') {
    if (data === 'transparent') return { kind: 'srgb', r: 0, g: 0, b: 0, a: 0 };
    if (data === 'currentColor' || data === 'currentcolor') {
      return { kind: 'dynamic', dynamicKind: 'currentColor', raw: data };
    }
    return { kind: 'unknown' };                                     // named/hex resolution happens upstream
  }

  if (typeof data !== 'object') return { kind: 'unknown' };         // numbers etc. make no sense here
  const obj = data as Record<string, unknown>;

  // Pre-resolved sRGB path — the common case for hex, named, rgb(), hsl(), hwb(), lab(), lch(), oklab(), oklch(), color().
  if (obj.srgb && typeof obj.srgb === 'object') {
    const srgb = obj.srgb as Record<string, unknown>;
    if (
      typeof srgb.r === 'number' &&
      typeof srgb.g === 'number' &&
      typeof srgb.b === 'number'
    ) {
      return { kind: 'srgb', r: srgb.r, g: srgb.g, b: srgb.b, a: pickAlpha(srgb) };
    }
  }

  // Dynamic path: no srgb, but 'original' carries enough info to know what flavour of dynamic this is.
  if ('original' in obj) {
    const dyn = classifyDynamic(obj.original);
    if (dyn) return { kind: 'dynamic', dynamicKind: dyn, raw: obj.original };
  }

  // Some IR shapes embed the dynamic marker on the top-level object (no 'original' wrapper).
  const topDyn = classifyDynamic(obj);
  if (topDyn) return { kind: 'dynamic', dynamicKind: topDyn, raw: obj };

  return { kind: 'unknown' };
}

// Convert a parsed ColorValue back to a CSS string. For dynamic colors we rebuild
// an approximation from the raw 'original' (color-mix/light-dark/relative are not
// fully expressible without extra IR context, so we fall back to 'currentColor' when unsure).
export function toCssColor(v: ColorValue): string {
  switch (v.kind) {
    case 'srgb': {                                                  // emit rgba(...) with 0..255 channels
      const to255 = (n: number) => Math.round(Math.max(0, Math.min(1, n)) * 255);
      return `rgba(${to255(v.r)}, ${to255(v.g)}, ${to255(v.b)}, ${v.a})`;
    }
    case 'dynamic':                                                 // passthrough: best effort for runtime-resolved colors
      return dynamicToCss(v.dynamicKind, v.raw);
    case 'unknown':
      return 'transparent';                                         // safe visible fallback
    default: {
      const _exhaustive: never = v;
      return _exhaustive;
    }
  }
}

// Build a CSS expression for a dynamic color from its raw IR payload.
// We don't attempt a full reconstruction — StyleBuilder will usually have the original CSS string.
function dynamicToCss(kind: DynamicKind, raw: unknown): string {
  if (kind === 'currentColor') return 'currentColor';               // direct CSS keyword
  if (typeof raw === 'string') return raw;                          // raw already a CSS fragment
  return 'currentColor';                                            // unknown complex shape: defer to element color
}
