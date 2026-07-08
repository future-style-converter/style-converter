// _mask_shared.ts — helpers specific to the mask-* tree.
// `emitMasked` centralises the Safari `-webkit-mask-*` prefix + `mask-box-image-*`
// legacy-prefix conventions so each Applier stays a one-liner.

import { extractLength, toCssLength } from '../../core/types/LengthValue';          // length parser

// Lowercase SNAKE_CASE dotted FQCN emitted by the Kotlin parser for some
// MaskMode / MaskRepeat / MaskComposite variants.  Trim the FQCN prefix and
// hyphenate: 'app.irmodels.properties.effects.MaskModeValue.MatchSource' ->
// 'match-source'.  Leaves simple keywords ('CONTENT_BOX') untouched so we can
// fall back to `kebab()` where appropriate.
export function tailKebab(raw: unknown): string | undefined {
  if (typeof raw !== 'string') return undefined;                                    // unknown shape
  const tail = raw.split('.').pop();                                                 // last path segment
  if (!tail) return undefined;
  // PascalCase → kebab-case (insert '-' between lower-upper and ALL-X boundaries).
  return tail
    .replace(/([a-z0-9])([A-Z])/g, '$1-$2')                                           // 'MatchSource' → 'Match-Source'
    .replace(/([A-Z]+)([A-Z][a-z])/g, '$1-$2')                                        // 'RGBValue' → 'RGB-Value'
    .toLowerCase();
}

// Emit the native `maskX` key *and* the `WebkitMaskX` prefix for Safari.
// (React styles accept Webkit-prefixed keys in PascalCase-ish form:
//  `WebkitMaskImage`, etc.  csstype covers them but we widen to Record for
//  mask-border-* which csstype has only partial support for.)
export function emitMasked(key: string, value: string): Record<string, string> {
  // `key` is a React-style camelCase key (e.g. 'maskImage').  The Webkit-prefixed
  // variant is the same with a capitalised first letter and 'Webkit' prepended.
  const webkitKey = 'Webkit' + key[0].toUpperCase() + key.slice(1);                  // 'WebkitMaskImage'
  return { [key]: value, [webkitKey]: value };                                        // both rules
}

// Convenience re-exports for mask-* triplets.
export { extractLength, toCssLength };
