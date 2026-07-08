// _dispatch.ts — single entrypoint for the borders engine.
// Calls every extract/apply pair in this category and Object.assign's the
// results onto the styles object.  Lives here (not in StyleBuilder) to keep
// the Phase-5 wiring local to the borders folder and keep StyleBuilder short.
//
// 46 border-family properties (BoxShadow is applied separately in StyleBuilder
// to keep the engine/effects/shadow/ layout independent).  Each line below
// mirrors one IR property → one CSS declaration.  Consumers spread the return
// value onto their CSSProperties accumulator.

import type { CSSProperties } from 'react';

// Sides — 24 properties (4 physical + 4 logical × width/style/color).
import { extractBorderTopWidth }     from './sides/BorderTopWidthExtractor';
import { applyBorderTopWidth }       from './sides/BorderTopWidthApplier';
import { extractBorderRightWidth }   from './sides/BorderRightWidthExtractor';
import { applyBorderRightWidth }     from './sides/BorderRightWidthApplier';
import { extractBorderBottomWidth }  from './sides/BorderBottomWidthExtractor';
import { applyBorderBottomWidth }    from './sides/BorderBottomWidthApplier';
import { extractBorderLeftWidth }    from './sides/BorderLeftWidthExtractor';
import { applyBorderLeftWidth }      from './sides/BorderLeftWidthApplier';
import { extractBorderBlockStartWidth }  from './sides/BorderBlockStartWidthExtractor';
import { applyBorderBlockStartWidth }    from './sides/BorderBlockStartWidthApplier';
import { extractBorderBlockEndWidth }    from './sides/BorderBlockEndWidthExtractor';
import { applyBorderBlockEndWidth }      from './sides/BorderBlockEndWidthApplier';
import { extractBorderInlineStartWidth } from './sides/BorderInlineStartWidthExtractor';
import { applyBorderInlineStartWidth }   from './sides/BorderInlineStartWidthApplier';
import { extractBorderInlineEndWidth }   from './sides/BorderInlineEndWidthExtractor';
import { applyBorderInlineEndWidth }     from './sides/BorderInlineEndWidthApplier';
import { extractBorderTopStyle }     from './sides/BorderTopStyleExtractor';
import { applyBorderTopStyle }       from './sides/BorderTopStyleApplier';
import { extractBorderRightStyle }   from './sides/BorderRightStyleExtractor';
import { applyBorderRightStyle }     from './sides/BorderRightStyleApplier';
import { extractBorderBottomStyle }  from './sides/BorderBottomStyleExtractor';
import { applyBorderBottomStyle }    from './sides/BorderBottomStyleApplier';
import { extractBorderLeftStyle }    from './sides/BorderLeftStyleExtractor';
import { applyBorderLeftStyle }      from './sides/BorderLeftStyleApplier';
import { extractBorderBlockStartStyle }  from './sides/BorderBlockStartStyleExtractor';
import { applyBorderBlockStartStyle }    from './sides/BorderBlockStartStyleApplier';
import { extractBorderBlockEndStyle }    from './sides/BorderBlockEndStyleExtractor';
import { applyBorderBlockEndStyle }      from './sides/BorderBlockEndStyleApplier';
import { extractBorderInlineStartStyle } from './sides/BorderInlineStartStyleExtractor';
import { applyBorderInlineStartStyle }   from './sides/BorderInlineStartStyleApplier';
import { extractBorderInlineEndStyle }   from './sides/BorderInlineEndStyleExtractor';
import { applyBorderInlineEndStyle }     from './sides/BorderInlineEndStyleApplier';
import { extractBorderTopColor }     from './sides/BorderTopColorExtractor';
import { applyBorderTopColor }       from './sides/BorderTopColorApplier';
import { extractBorderRightColor }   from './sides/BorderRightColorExtractor';
import { applyBorderRightColor }     from './sides/BorderRightColorApplier';
import { extractBorderBottomColor }  from './sides/BorderBottomColorExtractor';
import { applyBorderBottomColor }    from './sides/BorderBottomColorApplier';
import { extractBorderLeftColor }    from './sides/BorderLeftColorExtractor';
import { applyBorderLeftColor }      from './sides/BorderLeftColorApplier';
import { extractBorderBlockStartColor }  from './sides/BorderBlockStartColorExtractor';
import { applyBorderBlockStartColor }    from './sides/BorderBlockStartColorApplier';
import { extractBorderBlockEndColor }    from './sides/BorderBlockEndColorExtractor';
import { applyBorderBlockEndColor }      from './sides/BorderBlockEndColorApplier';
import { extractBorderInlineStartColor } from './sides/BorderInlineStartColorExtractor';
import { applyBorderInlineStartColor }   from './sides/BorderInlineStartColorApplier';
import { extractBorderInlineEndColor }   from './sides/BorderInlineEndColorExtractor';
import { applyBorderInlineEndColor }     from './sides/BorderInlineEndColorApplier';

// Radius — 8 corners (4 physical + 4 logical).
import { extractBorderTopLeftRadius }     from './radius/BorderTopLeftRadiusExtractor';
import { applyBorderTopLeftRadius }       from './radius/BorderTopLeftRadiusApplier';
import { extractBorderTopRightRadius }    from './radius/BorderTopRightRadiusExtractor';
import { applyBorderTopRightRadius }      from './radius/BorderTopRightRadiusApplier';
import { extractBorderBottomRightRadius } from './radius/BorderBottomRightRadiusExtractor';
import { applyBorderBottomRightRadius }   from './radius/BorderBottomRightRadiusApplier';
import { extractBorderBottomLeftRadius }  from './radius/BorderBottomLeftRadiusExtractor';
import { applyBorderBottomLeftRadius }    from './radius/BorderBottomLeftRadiusApplier';
import { extractBorderStartStartRadius }  from './radius/BorderStartStartRadiusExtractor';
import { applyBorderStartStartRadius }    from './radius/BorderStartStartRadiusApplier';
import { extractBorderStartEndRadius }    from './radius/BorderStartEndRadiusExtractor';
import { applyBorderStartEndRadius }      from './radius/BorderStartEndRadiusApplier';
import { extractBorderEndStartRadius }    from './radius/BorderEndStartRadiusExtractor';
import { applyBorderEndStartRadius }      from './radius/BorderEndStartRadiusApplier';
import { extractBorderEndEndRadius }      from './radius/BorderEndEndRadiusExtractor';
import { applyBorderEndEndRadius }        from './radius/BorderEndEndRadiusApplier';

// Image — 5 properties.
import { extractBorderImageSource } from './image/BorderImageSourceExtractor';
import { applyBorderImageSource }   from './image/BorderImageSourceApplier';
import { extractBorderImageSlice }  from './image/BorderImageSliceExtractor';
import { applyBorderImageSlice }    from './image/BorderImageSliceApplier';
import { extractBorderImageWidth }  from './image/BorderImageWidthExtractor';
import { applyBorderImageWidth }    from './image/BorderImageWidthApplier';
import { extractBorderImageOutset } from './image/BorderImageOutsetExtractor';
import { applyBorderImageOutset }   from './image/BorderImageOutsetApplier';
import { extractBorderImageRepeat } from './image/BorderImageRepeatExtractor';
import { applyBorderImageRepeat }   from './image/BorderImageRepeatApplier';

// Outline — 4 properties.
import { extractOutlineWidth }  from './outline/OutlineWidthExtractor';
import { applyOutlineWidth }    from './outline/OutlineWidthApplier';
import { extractOutlineStyle }  from './outline/OutlineStyleExtractor';
import { applyOutlineStyle }    from './outline/OutlineStyleApplier';
import { extractOutlineColor }  from './outline/OutlineColorExtractor';
import { applyOutlineColor }    from './outline/OutlineColorApplier';
import { extractOutlineOffset } from './outline/OutlineOffsetExtractor';
import { applyOutlineOffset }   from './outline/OutlineOffsetApplier';

// Misc — 3 properties at the top of the borders/ folder.
import { extractBoxDecorationBreak } from './BoxDecorationBreakExtractor';
import { applyBoxDecorationBreak }   from './BoxDecorationBreakApplier';
import { extractCornerShape }        from './CornerShapeExtractor';
import { applyCornerShape }          from './CornerShapeApplier';
import { extractBorderBoundary }     from './BorderBoundaryExtractor';
import { applyBorderBoundary }       from './BorderBoundaryApplier';

// Minimal IRProperty shape — matches what extractors expect.
interface IRPropertyLike { type: string; data: unknown; }

// Single entrypoint — fold-apply every border property in one call.  Returns
// a partial CSSProperties that can be spread into a parent styles object.
// Explicit function calls (rather than a loop) keep this dispatch trivially
// tree-shakeable and make stack traces meaningful during test failures.
export function applyBordersPhase5(properties: IRPropertyLike[]): Partial<CSSProperties> {
  const out: Partial<CSSProperties> = {};                                  // accumulator
  // Sides — widths
  Object.assign(out, applyBorderTopWidth(extractBorderTopWidth(properties)));
  Object.assign(out, applyBorderRightWidth(extractBorderRightWidth(properties)));
  Object.assign(out, applyBorderBottomWidth(extractBorderBottomWidth(properties)));
  Object.assign(out, applyBorderLeftWidth(extractBorderLeftWidth(properties)));
  Object.assign(out, applyBorderBlockStartWidth(extractBorderBlockStartWidth(properties)));
  Object.assign(out, applyBorderBlockEndWidth(extractBorderBlockEndWidth(properties)));
  Object.assign(out, applyBorderInlineStartWidth(extractBorderInlineStartWidth(properties)));
  Object.assign(out, applyBorderInlineEndWidth(extractBorderInlineEndWidth(properties)));
  // Sides — styles
  Object.assign(out, applyBorderTopStyle(extractBorderTopStyle(properties)));
  Object.assign(out, applyBorderRightStyle(extractBorderRightStyle(properties)));
  Object.assign(out, applyBorderBottomStyle(extractBorderBottomStyle(properties)));
  Object.assign(out, applyBorderLeftStyle(extractBorderLeftStyle(properties)));
  Object.assign(out, applyBorderBlockStartStyle(extractBorderBlockStartStyle(properties)));
  Object.assign(out, applyBorderBlockEndStyle(extractBorderBlockEndStyle(properties)));
  Object.assign(out, applyBorderInlineStartStyle(extractBorderInlineStartStyle(properties)));
  Object.assign(out, applyBorderInlineEndStyle(extractBorderInlineEndStyle(properties)));
  // Sides — colors
  Object.assign(out, applyBorderTopColor(extractBorderTopColor(properties)));
  Object.assign(out, applyBorderRightColor(extractBorderRightColor(properties)));
  Object.assign(out, applyBorderBottomColor(extractBorderBottomColor(properties)));
  Object.assign(out, applyBorderLeftColor(extractBorderLeftColor(properties)));
  Object.assign(out, applyBorderBlockStartColor(extractBorderBlockStartColor(properties)));
  Object.assign(out, applyBorderBlockEndColor(extractBorderBlockEndColor(properties)));
  Object.assign(out, applyBorderInlineStartColor(extractBorderInlineStartColor(properties)));
  Object.assign(out, applyBorderInlineEndColor(extractBorderInlineEndColor(properties)));
  // Radii — physical corners
  Object.assign(out, applyBorderTopLeftRadius(extractBorderTopLeftRadius(properties)));
  Object.assign(out, applyBorderTopRightRadius(extractBorderTopRightRadius(properties)));
  Object.assign(out, applyBorderBottomRightRadius(extractBorderBottomRightRadius(properties)));
  Object.assign(out, applyBorderBottomLeftRadius(extractBorderBottomLeftRadius(properties)));
  // Radii — logical corners
  Object.assign(out, applyBorderStartStartRadius(extractBorderStartStartRadius(properties)));
  Object.assign(out, applyBorderStartEndRadius(extractBorderStartEndRadius(properties)));
  Object.assign(out, applyBorderEndStartRadius(extractBorderEndStartRadius(properties)));
  Object.assign(out, applyBorderEndEndRadius(extractBorderEndEndRadius(properties)));
  // Image
  Object.assign(out, applyBorderImageSource(extractBorderImageSource(properties)));
  Object.assign(out, applyBorderImageSlice(extractBorderImageSlice(properties)));
  Object.assign(out, applyBorderImageWidth(extractBorderImageWidth(properties)));
  Object.assign(out, applyBorderImageOutset(extractBorderImageOutset(properties)));
  Object.assign(out, applyBorderImageRepeat(extractBorderImageRepeat(properties)));
  // Outline
  Object.assign(out, applyOutlineWidth(extractOutlineWidth(properties)));
  Object.assign(out, applyOutlineStyle(extractOutlineStyle(properties)));
  Object.assign(out, applyOutlineColor(extractOutlineColor(properties)));
  Object.assign(out, applyOutlineOffset(extractOutlineOffset(properties)));
  // Misc
  Object.assign(out, applyBoxDecorationBreak(extractBoxDecorationBreak(properties)));
  Object.assign(out, applyCornerShape(extractCornerShape(properties)));
  Object.assign(out, applyBorderBoundary(extractBorderBoundary(properties)));
  return out;
}
