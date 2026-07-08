/**
 * Style Builder - Converts IR properties to CSS styles.
 *
 * This is the main entry point for converting IR properties to inline CSS.
 */

import type { IRProperty } from '../ir/IRModels';
import {
  extractLength,
  extractKeyword,
} from '../types/ValueExtractors';
// Phase-2 engine wiring — spacing properties now flow through per-family
// extractors + appliers instead of the legacy switch below.
import { isLegacyProperty } from '../../engine/PropertyRegistry';
import { extractPadding } from '../../engine/spacing/PaddingExtractor';
import { applyPadding } from '../../engine/spacing/PaddingApplier';
import { extractMargin } from '../../engine/spacing/MarginExtractor';
import { applyMargin } from '../../engine/spacing/MarginApplier';
import { extractGap } from '../../engine/spacing/GapExtractor';
import { applyGap } from '../../engine/spacing/GapApplier';
import { extractMarginTrim } from '../../engine/spacing/MarginTrimExtractor';
import { applyMarginTrim } from '../../engine/spacing/MarginTrimApplier';
// Phase-3 sizing engine — width/height/min-*/max-*/block-size/inline-size/aspect-ratio.
import { extractSize } from '../../engine/sizing/SizeExtractor';
import { applySize } from '../../engine/sizing/SizeApplier';
// Phase-4 color + background engine — see engine/color/*, engine/background/*,
// engine/effects/blend/*, engine/performance/*.
import { extractBackgroundColor } from '../../engine/color/BackgroundColorExtractor';
import { applyBackgroundColor }   from '../../engine/color/BackgroundColorApplier';
import { extractTextColor }       from '../../engine/color/ColorExtractor';
import { applyTextColor }         from '../../engine/color/ColorApplier';
import { extractOpacity }         from '../../engine/color/OpacityExtractor';
import { applyOpacity }           from '../../engine/color/OpacityApplier';
import { extractAccentColor }     from '../../engine/color/AccentColorExtractor';
import { applyAccentColor }       from '../../engine/color/AccentColorApplier';
import { extractCaretColor }      from '../../engine/color/CaretColorExtractor';
import { applyCaretColor }        from '../../engine/color/CaretColorApplier';
import { extractBackgroundImage }      from '../../engine/background/BackgroundImageExtractor';
import { applyBackgroundImage }        from '../../engine/background/BackgroundImageApplier';
import { extractBackgroundSize }       from '../../engine/background/BackgroundSizeExtractor';
import { applyBackgroundSize }         from '../../engine/background/BackgroundSizeApplier';
import { extractBackgroundPosition }   from '../../engine/background/BackgroundPositionExtractor';
import { applyBackgroundPosition }     from '../../engine/background/BackgroundPositionApplier';
import { extractBackgroundRepeat }     from '../../engine/background/BackgroundRepeatExtractor';
import { applyBackgroundRepeat }       from '../../engine/background/BackgroundRepeatApplier';
import { extractBackgroundClip }       from '../../engine/background/BackgroundClipExtractor';
import { applyBackgroundClip }         from '../../engine/background/BackgroundClipApplier';
import { extractBackgroundOrigin }     from '../../engine/background/BackgroundOriginExtractor';
import { applyBackgroundOrigin }       from '../../engine/background/BackgroundOriginApplier';
import { extractBackgroundAttachment } from '../../engine/background/BackgroundAttachmentExtractor';
import { applyBackgroundAttachment }   from '../../engine/background/BackgroundAttachmentApplier';
import { extractBlendMode } from '../../engine/effects/blend/BlendModeExtractor';
import { applyBlendMode }   from '../../engine/effects/blend/BlendModeApplier';
import { extractIsolation } from '../../engine/performance/IsolationExtractor';
import { applyIsolation }   from '../../engine/performance/IsolationApplier';
// Phase-5 borders engine — see engine/borders/* and engine/effects/shadow/*.
// Each triplet folds the relevant IRProperty entries into a typed Config and
// the Applier emits the native CSS key/value.  47 properties total.
import { applyBordersPhase5 } from '../../engine/borders/_dispatch';
import { applyBoxShadow } from '../../engine/effects/shadow/BoxShadowApplier';
import { extractBoxShadow } from '../../engine/effects/shadow/BoxShadowExtractor';
// Phase-6 typography engine — 109 properties (CaretColor migrated in Phase 4).
import { applyTypographyPhase6 } from '../../engine/typography/_dispatch';
// Phase-7 layout engine — 55 properties (flexbox, grid, position, advanced
// anchor + motion-path, plus root flow/top-layer keywords).
import { applyLayoutPhase7 } from '../../engine/layout/_dispatch';
// Phase-8 engines — transforms (10), effects/clip+filter+mask (22), visibility
// + overflow (6).  38 properties total.
import { applyTransformsPhase8 } from '../../engine/transforms/_dispatch';
import { applyEffectsPhase8 } from '../../engine/effects/_dispatch';
import { applyVisibilityPhase8 } from '../../engine/visibility/_dispatch';
// Phase-9 engines — animations + transitions + view-timeline + view-transition
// (26 props under engine/animations/) and scroll-timeline (3 props under
// engine/scrolling/).  29 properties total.
import { applyAnimationsPhase9 } from '../../engine/animations/_dispatch';
import { applyScrollingPhase9, applyScrollingPhase10 } from '../../engine/scrolling/_dispatch';
// Phase-10 long-tail engines — 22 categories covering ~170 remaining properties.
// Web pass-through for nearly all; csstype widening is category-local (see the
// individual appliers for MDN links + widening rationale).
import { applySvgPhase10 } from '../../engine/svg/_dispatch';
import { applySpeechPhase10 } from '../../engine/speech/_dispatch';
import { applyRenderingPhase10 } from '../../engine/rendering/_dispatch';
import { applyPrintPhase10 } from '../../engine/print/_dispatch';
import { applyRegionsPhase10 } from '../../engine/regions/_dispatch';
import { applyInteractionsPhase10 } from '../../engine/interactions/_dispatch';
import { applyPerformancePhase10 } from '../../engine/performance/_dispatch';
import { applyColumnsPhase10 } from '../../engine/columns/_dispatch';
import { applyPagingPhase10 } from '../../engine/paging/_dispatch';
import { applyTablePhase10 } from '../../engine/table/_dispatch';
import { applyShapesPhase10 } from '../../engine/shapes/_dispatch';
import { applyRhythmPhase10 } from '../../engine/rhythm/_dispatch';
import { applyNavigationPhase10 } from '../../engine/navigation/_dispatch';
import { applyImagesPhase10 } from '../../engine/images/_dispatch';
import { applyAppearancePhase10 } from '../../engine/appearance/_dispatch';
import { applyCountersPhase10 } from '../../engine/counters/_dispatch';
import { applyListsPhase10 } from '../../engine/lists/_dispatch';
import { applyContainerPhase10 } from '../../engine/container/_dispatch';
import { applyMathPhase10 } from '../../engine/math/_dispatch';
import { applyExperimentalPhase10 } from '../../engine/experimental/_dispatch';
import { applyContentPhase10 } from '../../engine/content/_dispatch';
import { applyGlobalPhase10 } from '../../engine/global/_dispatch';

export interface CSSStyles {
  [key: string]: string | number | undefined;
}

/**
 * Convert a list of IR properties to CSS styles.
 */
export function buildStyles(properties: IRProperty[]): CSSStyles {
  const styles: CSSStyles = {};

  // Phase-2 engine path — spacing properties are bucketed once and
  // handled by dedicated Config/Applier triplets.  The legacy switch
  // below skips any migrated type via `isLegacyProperty`.
  Object.assign(styles, applyPadding(extractPadding(properties)));
  Object.assign(styles, applyMargin(extractMargin(properties)));
  Object.assign(styles, applyGap(extractGap(properties)));
  const marginTrim = extractMarginTrim(properties);
  if (marginTrim) Object.assign(styles, applyMarginTrim(marginTrim));
  // Phase-3 sizing — width/height/min-*/max-*/block-size/inline-size/aspect-ratio.
  Object.assign(styles, applySize(extractSize(properties)));

  // Phase-4 color + background engine.  Each extractor is a single-pass fold
  // over `properties`; appliers emit CSS keys that the browser renders
  // natively (static + dynamic colors, gradients, blend modes, isolation).
  Object.assign(styles, applyBackgroundColor(extractBackgroundColor(properties)));
  Object.assign(styles, applyTextColor(extractTextColor(properties)));
  Object.assign(styles, applyOpacity(extractOpacity(properties)));
  Object.assign(styles, applyAccentColor(extractAccentColor(properties)));
  Object.assign(styles, applyCaretColor(extractCaretColor(properties)));
  Object.assign(styles, applyBackgroundImage(extractBackgroundImage(properties)));
  Object.assign(styles, applyBackgroundSize(extractBackgroundSize(properties)));
  Object.assign(styles, applyBackgroundPosition(extractBackgroundPosition(properties)));
  Object.assign(styles, applyBackgroundRepeat(extractBackgroundRepeat(properties)));
  Object.assign(styles, applyBackgroundClip(extractBackgroundClip(properties)));
  Object.assign(styles, applyBackgroundOrigin(extractBackgroundOrigin(properties)));
  Object.assign(styles, applyBackgroundAttachment(extractBackgroundAttachment(properties)));
  Object.assign(styles, applyBlendMode(extractBlendMode(properties)));
  Object.assign(styles, applyIsolation(extractIsolation(properties)));

  // Phase-5 borders engine — 46 per-side/corner/image/outline/misc
  // properties plus BoxShadow routed separately.
  Object.assign(styles, applyBordersPhase5(properties));
  Object.assign(styles, applyBoxShadow(extractBoxShadow(properties)));

  // Phase-6 typography engine — single fold handles all 109 typography props.
  Object.assign(styles, applyTypographyPhase6(properties));

  // Phase-7 layout engine — flexbox + grid + position + advanced anchor/
  // motion-path + flow/top-layer keywords.  Replaces the legacy Display/
  // Flex*/Justify*/Align*/Grid*/Position/Top/Right/Bottom/Left/ZIndex switch
  // cases below.
  Object.assign(styles, applyLayoutPhase7(properties));

  // Phase-8 engines — transforms/effects/visibility.  Replaces the legacy
  // Transform/TransformOrigin, Filter/BackdropFilter, ClipPath, Visibility,
  // Overflow/OverflowX/OverflowY switch cases below.
  Object.assign(styles, applyTransformsPhase8(properties));
  Object.assign(styles, applyEffectsPhase8(properties));
  Object.assign(styles, applyVisibilityPhase8(properties));

  // Phase-9 engines — animations/transitions/view-timeline/view-transition
  // (26) + scroll-timeline (3).  Replaces the legacy Transition* switch cases
  // below and brings the previously-missing Animation* family online.
  Object.assign(styles, applyAnimationsPhase9(properties));
  Object.assign(styles, applyScrollingPhase9(properties));

  // Phase-10 long tail — 22 categories flip from legacy switch / default
  // PascalCase→kebab fallback to explicit Config/Extractor/Applier triplets.
  // Removes the legacy Cursor / PointerEvents / UserSelect / ObjectFit /
  // ObjectPosition switch cases below.
  Object.assign(styles, applyScrollingPhase10(properties));
  Object.assign(styles, applySvgPhase10(properties));
  Object.assign(styles, applySpeechPhase10(properties));
  Object.assign(styles, applyRenderingPhase10(properties));
  Object.assign(styles, applyPrintPhase10(properties));
  Object.assign(styles, applyRegionsPhase10(properties));
  Object.assign(styles, applyInteractionsPhase10(properties));
  Object.assign(styles, applyPerformancePhase10(properties));
  Object.assign(styles, applyColumnsPhase10(properties));
  Object.assign(styles, applyPagingPhase10(properties));
  Object.assign(styles, applyTablePhase10(properties));
  Object.assign(styles, applyShapesPhase10(properties));
  Object.assign(styles, applyRhythmPhase10(properties));
  Object.assign(styles, applyNavigationPhase10(properties));
  Object.assign(styles, applyImagesPhase10(properties));
  Object.assign(styles, applyAppearancePhase10(properties));
  Object.assign(styles, applyCountersPhase10(properties));
  Object.assign(styles, applyListsPhase10(properties));
  Object.assign(styles, applyContainerPhase10(properties));
  Object.assign(styles, applyMathPhase10(properties));
  Object.assign(styles, applyExperimentalPhase10(properties));
  Object.assign(styles, applyContentPhase10(properties));
  Object.assign(styles, applyGlobalPhase10(properties));

  for (const prop of properties) {
    // Skip properties already served by the engine path above.
    if (!isLegacyProperty(prop.type)) continue;
    applyProperty(styles, prop);
  }

  return styles;
}

/**
 * Apply a single IR property to the styles object.
 */
function applyProperty(styles: CSSStyles, prop: IRProperty): void {
  const { type, data } = prop;

  switch (type) {
    // Colors/Opacity/AccentColor/CaretColor migrated in Phase 4 (engine/color/*).

    // ==================== Sizing ====================
    case 'Width': {
      const width = extractLength(data);
      if (width) styles.width = width;
      break;
    }
    case 'Height': {
      const height = extractLength(data);
      if (height) styles.height = height;
      break;
    }
    case 'MinWidth': {
      const minWidth = extractLength(data);
      if (minWidth) styles.minWidth = minWidth;
      break;
    }
    case 'MaxWidth': {
      const maxWidth = extractLength(data);
      if (maxWidth) styles.maxWidth = maxWidth;
      break;
    }
    case 'MinHeight': {
      const minHeight = extractLength(data);
      if (minHeight) styles.minHeight = minHeight;
      break;
    }
    case 'MaxHeight': {
      const maxHeight = extractLength(data);
      if (maxHeight) styles.maxHeight = maxHeight;
      break;
    }

    // ==================== Spacing ====================
    case 'PaddingTop': {
      const pt = extractLength(data);
      if (pt) styles.paddingTop = pt;
      break;
    }
    case 'PaddingRight': {
      const pr = extractLength(data);
      if (pr) styles.paddingRight = pr;
      break;
    }
    case 'PaddingBottom': {
      const pb = extractLength(data);
      if (pb) styles.paddingBottom = pb;
      break;
    }
    case 'PaddingLeft': {
      const pl = extractLength(data);
      if (pl) styles.paddingLeft = pl;
      break;
    }
    case 'MarginTop': {
      const mt = extractLength(data);
      if (mt) styles.marginTop = mt;
      break;
    }
    case 'MarginRight': {
      const mr = extractLength(data);
      if (mr) styles.marginRight = mr;
      break;
    }
    case 'MarginBottom': {
      const mb = extractLength(data);
      if (mb) styles.marginBottom = mb;
      break;
    }
    case 'MarginLeft': {
      const ml = extractLength(data);
      if (ml) styles.marginLeft = ml;
      break;
    }

    // ==================== Display & Layout ====================
    // Display / FlexDirection / FlexWrap / JustifyContent / AlignItems /
    // AlignContent / AlignSelf / FlexGrow / FlexShrink / FlexBasis / Order
    // migrated to engine/layout/flexbox/* in Phase 7 (applyLayoutPhase7).

    // ==================== Gap ====================
    case 'Gap': {
      const gap = extractLength(data);
      if (gap) styles.gap = gap;
      break;
    }
    case 'RowGap': {
      const rg = extractLength(data);
      if (rg) styles.rowGap = rg;
      break;
    }
    case 'ColumnGap': {
      const cg = extractLength(data);
      if (cg) styles.columnGap = cg;
      break;
    }

    // ==================== Grid ====================
    // GridTemplateColumns / GridTemplateRows / GridTemplateAreas,
    // GridAutoColumns / GridAutoRows / GridAutoFlow,
    // GridColumnStart / GridColumnEnd / GridRowStart / GridRowEnd,
    // JustifyItems / JustifySelf / AlignTracks / JustifyTracks / MasonryAutoFlow
    // migrated to engine/layout/grid/* in Phase 7.
    // The `GridColumn` / `GridRow` / `GridArea` shorthands are expanded by
    // the Kotlin parser before reaching us, so no legacy case is needed.

    // ==================== Position ====================
    // Position / Top / Right / Bottom / Left, Inset{Block,Inline}{Start,End},
    // and ZIndex migrated to engine/layout/position/* in Phase 7.

    // ==================== Borders ====================
    // Migrated to engine/borders/* in Phase 5 — all Border*Width/Style/Color,
    // every BorderRadius variant, BorderImage*, Outline*, BoxShadow,
    // BoxDecorationBreak, CornerShape, BorderBoundary flow through
    // applyBordersPhase5 above.  The legacy `BorderRadius` shorthand
    // (as opposed to the per-corner longhands) is expanded by the Kotlin
    // CSS parser before it reaches us, so no case here is needed.

    // ==================== Typography ====================
    // Migrated to engine/typography/* in Phase 6 — all font-*, line-*,
    // letter/word-spacing, text-*, white-space, word-break, overflow-wrap,
    // line-break, hyphens, direction, writing-mode, ruby-*, vertical-align,
    // text-shadow, text-overflow, line-clamp, quotes, hanging-punctuation,
    // initial-letter*, orphans, widows, text-rendering, kerning, line-grid,
    // line-snap, tab-size, etc. flow through `applyTypographyPhase6` above.

    // ==================== Overflow ====================
    // Migrated to engine/visibility/Overflow* in Phase 8 (applyVisibilityPhase8).

    // ==================== Transforms ====================
    // Migrated to engine/transforms/* in Phase 8 (applyTransformsPhase8).

    // ==================== Filters ====================
    // Migrated to engine/effects/filter/* in Phase 8 (applyEffectsPhase8).

    // ==================== Transitions & Animations ====================
    // Migrated to engine/animations/* + engine/scrolling/* in Phase 9 via
    // applyAnimationsPhase9 / applyScrollingPhase9.  Covers
    // animation-name/duration/delay/iteration-count/direction/fill-mode/
    // play-state/composition/timing-function/timeline/range/range-start/
    // range-end, transition-property/duration/delay/timing-function/behavior,
    // timeline-scope, view-timeline(-axis/-inset/-name),
    // view-transition-name/-class/-group, scroll-timeline(-name/-axis).

    // ==================== Box Shadow ====================
    // Migrated to engine/effects/shadow/BoxShadow* in Phase 5.

    // ==================== Text Shadow ====================
    // Migrated to engine/typography/TextShadow* in Phase 6.

    // ==================== Background Image ====================
    // BackgroundImage/Size/Position/Repeat/Clip/Origin/Attachment migrated
    // in Phase 4 (engine/background/*).

    // ==================== Clip Path ====================
    // Migrated to engine/effects/clip/* in Phase 8 (applyEffectsPhase8).

    // Cursor migrated to engine/interactions/Cursor* in Phase 10.

    // ==================== Visibility ====================
    // Migrated to engine/visibility/Visibility* in Phase 8.

    // ==================== Outline ====================
    // Migrated to engine/borders/outline/Outline* in Phase 5.

    // AspectRatio already handled by Phase-3 sizing engine above; no legacy case.
    // ObjectFit / ObjectPosition migrated to engine/images/* in Phase 10.
    // PointerEvents / UserSelect migrated to engine/interactions/* in Phase 10.

    // ==================== Mix Blend Mode ====================
    // MixBlendMode migrated in Phase 4 (engine/effects/blend/*).

    // Default: try to apply as-is for unknown properties
    default:
      // Convert PascalCase to kebab-case
      const cssProperty = type.replace(/([A-Z])/g, '-$1').toLowerCase().replace(/^-/, '');
      const value = extractKeyword(data) || extractLength(data);
      if (value) {
        styles[cssProperty] = typeof value === 'string' ? value.toLowerCase().replace('_', '-') : value;
      }
  }
}

// ==================== Helper Functions ====================

// extractBorderWidth removed — now handled by engine/borders/sides/_shared.ts

// Legacy typography extractors (FontSize/FontWeight/FontFamily/LineHeight)
// removed in Phase 6 — replaced by the per-property triplets under
// engine/typography/*.

// extractGridTemplate / extractGridSpan removed in Phase 7 — replaced by
// engine/layout/grid/_grid_shared.ts (trackSize / renderTrackList / gridLine).

// Legacy extractTransform / extractTransformOrigin / extractFilter removed in
// Phase 8 — replaced by the Config/Extractor/Applier triplets under
// engine/transforms/* and engine/effects/{filter,clip}/*.

// Legacy extractTransitionProperty / extractTransitionDuration /
// extractTimingFunction / extractSingleTimingFunction removed in Phase 9 —
// replaced by the Config/Extractor/Applier triplets under engine/animations/*.

// Legacy extractBoxShadow removed — now handled by engine/effects/shadow/BoxShadow*

// Legacy extractTextShadow removed in Phase 6 — see engine/typography/TextShadow*.

// Legacy extractClipPath removed in Phase 8 — see engine/effects/clip/ClipPath*.
// Legacy legacyExtractBackgroundPosition / extractAspectRatio removed in Phase 10 —
// ObjectPosition now goes through engine/images/ObjectPosition* and AspectRatio
// has been handled by Phase-3 sizing engine since migration.
