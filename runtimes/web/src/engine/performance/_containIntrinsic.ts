// _containIntrinsic.ts — the shared value bridge for the five
// contain-intrinsic-* extractors (wave-48 lane W5).
//
// css-sizing-4 §4.4 types every contain-intrinsic-* value as
//   none | <length> | auto <length> | auto none
// and the converter's ContainIntrinsicValue wire (ContainIntrinsicSizeProperty
// .kt + IRLengthSerializer + deepFlatten) emits:
//   {"type":"none"} / {"type":"auto"}          — keyword variants
//   {"type":"length","px":100}                 — absolute <length>
//   {"type":"length","original":{v,u}}         — runtime-dependent <length>
//   {"type":"auto-length","px":300}            — `auto 300px`
//
// The extractors used the generic keywordOrRaw helper, which knows none of
// the length shapes — so the whole declaration was DROPPED. Measured on
// wave48-cal css-contain/contain-inline-size-intrinsic: the IR carries
// `Contain ["INLINE_SIZE"]` + `Width "fit-content"` +
// `ContainIntrinsicInlineSize {"type":"length","px":100}`, and with the
// intrinsic size dropped a size-contained box's fit-content resolves to 0 —
// zero green pixels against a 100×100 green ref (web 0.9549,
// coverageRatioFailed, while both natives pass at 0.969).
//
// VERIFIED (wave-48 W5, section-runner web re-captures, run w48-w5-verify2):
// contain-inline-size-intrinsic 0.9549 F → 0.9990 P, and css-sizing
// aspect-ratio/abspos-014 (the other length-carrying cell) 0.9549 F →
// 0.9990 P. The same css-contain run also flipped the 8
// contain-{body,html}-w-m-001..004 cells (0.9101–0.9109 F → 1.0000 P) —
// that movement belongs to the SIBLING wave-48 fix, renderer/
// RootPseudoPlacement.ts's rootWritingModeSuppression, whose own VERIFIED
// note cross-references this file as the section's 9th flip; every
// remaining cell in both sections was byte-stable.
import { keywordOrRaw, lengthOrKeyword } from '../_phase10_shared';

/** One ContainIntrinsicValue wire → its CSS text, or undefined to drop. */
export function containIntrinsicCss(data: unknown): string | undefined {
  if (data && typeof data === 'object') {
    const o = data as Record<string, unknown>;
    if (o.type === 'length') {
      // Absolute px or runtime-dependent {original} — both are exactly what
      // lengthOrKeyword serializes; the discriminator itself carries no CSS.
      return lengthOrKeyword(o);
    }
    if (o.type === 'auto-length') {
      // css-sizing-4 §4.4 `auto <length>`: last-remembered-size behaviour
      // with the length as the fallback. The browser needs both tokens.
      const len = lengthOrKeyword({ ...o, type: 'length' });
      return len === undefined ? undefined : `auto ${len}`;
    }
  }
  // {"type":"none"} / {"type":"auto"} / defensive bare strings — the shapes
  // keywordOrRaw already served correctly.
  return keywordOrRaw(data);
}

/**
 * The two-value contain-intrinsic-size shorthand wire. TWO live shapes:
 *   {"width":{…},"height":{…}}  — both axes authored
 *   <ContainIntrinsicValue>     — height omitted; deepFlatten inlines the
 *                                 lone width field (pinned by the wave48-cal
 *                                 css-view-transitions content-visibility
 *                                 IR, {"type":"length","px":500})
 */
export function containIntrinsicSizeCss(data: unknown): string | undefined {
  if (data && typeof data === 'object') {
    const o = data as Record<string, unknown>;
    if (o.width !== undefined) {
      const w = containIntrinsicCss(o.width);
      if (w === undefined) return undefined;                 // half a shorthand is not a value
      const h = o.height !== undefined ? containIntrinsicCss(o.height) : undefined;
      return h === undefined ? w : `${w} ${h}`;              // one value = both axes (css-sizing-4 §4.4)
    }
  }
  return containIntrinsicCss(data);                          // flattened single-value form
}
