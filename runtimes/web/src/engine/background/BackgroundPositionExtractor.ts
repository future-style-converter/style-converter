// BackgroundPositionExtractor.ts — collects BackgroundPositionX + Y IR
// properties into one config.  Shapes observed in fixtures:
//   { type: 'keyword', value: 'LEFT'|'RIGHT'|'TOP'|'BOTTOM'|'CENTER' }
//   { type: 'percentage', percentage: N }
//   { type: 'length', px: N }
// Plus the SHORTHAND wire: the `background` shorthand emits a single
// `BackgroundPosition` whose data is a tagged PositionValue LIST — pinned
// against live converter output of `background: red url(…) right bottom`:
//   [{ "type": "two-value", "x": { "type": "right" }, "y": { "type": "bottom" } }]
// (EdgeValue keywords are encoded AS the discriminator tag, unlike the
// longhand's { type:'keyword', value:'…' } shape — see converter
// irmodels/properties/background/BackgroundPositionProperty.kt).

import type {
  BackgroundPositionConfig,
  BackgroundPositionPropertyType,
} from './BackgroundPositionConfig';
import {
  BACKGROUND_POSITION,
  BACKGROUND_POSITION_X,
  BACKGROUND_POSITION_Y,
  BACKGROUND_POSITION_BLOCK,
  BACKGROUND_POSITION_INLINE,
} from './BackgroundPositionConfig';

// Minimal IR property shape.
interface IRPropertyLike { type: string; data: unknown; }

// Registry predicate — matches either physical or logical axis.
export function isBackgroundPositionProperty(
  type: string,
): type is BackgroundPositionPropertyType {
  return type === BACKGROUND_POSITION           // combined shorthand product
      || type === BACKGROUND_POSITION_X
      || type === BACKGROUND_POSITION_Y
      || type === BACKGROUND_POSITION_BLOCK
      || type === BACKGROUND_POSITION_INLINE;
}

// Convert one shorthand-wire EdgeValue into a per-axis CSS token, or null on
// unknown shapes.  The tag itself IS the keyword for edge/center variants
// (kotlinx @SerialName encodes `data object Left` as {"type":"left"}), while
// length/percentage carry a payload field mirroring the longhand primitives.
function parseEdge(edge: unknown): string | null {
  if (edge === null || typeof edge !== 'object') return null;         // wire is always an object
  const obj = edge as Record<string, unknown>;
  switch (obj.type) {
    case 'left': case 'right':                                        // horizontal edge keywords…
    case 'top': case 'bottom':                                        // …vertical edge keywords…
    case 'center':                                                    // …and the shared centre keyword
      return obj.type;                                                // tag doubles as the CSS token
    case 'length':                                                    // IRLength — absolute px payload
      return typeof obj.px === 'number' ? `${obj.px}px` : null;       // e.g. {type:'length',px:10} → '10px'
    case 'percentage':                                                // IRPercentage payload
      return typeof obj.percentage === 'number' ? `${obj.percentage}%` : null; // e.g. 25 → '25%'
    default:
      return null;                                                    // unknown variant → caller drops axis
  }
}

// Fold the shorthand PositionValue list into the shared x/y config.  Only
// entry 0 is consumed for now — per-layer position lists (multiple background
// images) are a later step; entry 0 matches the single-layer fixtures.
function applyShorthandList(data: unknown, cfg: BackgroundPositionConfig): void {
  if (!Array.isArray(data) || data.length === 0) return;              // wire is a non-empty array
  const first = data[0] as Record<string, unknown> | null;            // layer 0 only (see above)
  if (first === null || typeof first !== 'object') return;            // defensive: entries are objects
  switch (first.type) {
    case 'center':                                                    // one-value `center` — both axes centred
      cfg.x = 'center'; cfg.y = 'center';                             // CSS: `center` ≡ `center center`
      break;
    case 'two-value': {                                               // explicit x + y pair (the common case)
      const x = parseEdge(first.x);                                   // horizontal component
      const y = parseEdge(first.y);                                   // vertical component
      if (x !== null) cfg.x = x;                                      // only overwrite on a parsed value…
      if (y !== null) cfg.y = y;                                      // …so unknown axes keep prior state
      break;
    }
    case 'keyword': {                                                 // one-value keyword, e.g. `left` / `top`
      const kw = typeof first.keyword === 'string' ? first.keyword.toLowerCase() : null;
      if (kw === null) break;                                         // malformed payload → drop
      // CSS one-value rule (css-backgrounds-3 §2.6): the missing axis is
      // `center`; top/bottom name the vertical axis, everything else horizontal.
      if (kw === 'top' || kw === 'bottom') { cfg.x = 'center'; cfg.y = kw; }
      else { cfg.x = kw; cfg.y = 'center'; }
      break;
    }
    default:
      // 'raw' (unresolvable var()/calc() text) and future variants have no
      // per-axis decomposition — drop and keep the comparison honest rather
      // than emit a possibly-wrong axis. TODO: thread raw through untouched
      // once the config grows a whole-declaration field.
      break;
  }
}

// Parse a single axis payload into a CSS token, or null on unknown shapes.
function parseAxis(data: unknown): string | null {
  if (data === null || data === undefined) return null;               // missing
  if (typeof data === 'string') return data.toLowerCase();            // defensive bare string
  if (typeof data === 'number') return `${data}%`;                    // bare number -> percentage
  if (typeof data !== 'object') return null;                          // other primitives rejected
  const obj = data as Record<string, unknown>;
  if (obj.type === 'keyword' && typeof obj.value === 'string') {      // LEFT/RIGHT/TOP/BOTTOM/CENTER
    return obj.value.toLowerCase();                                   // CSS expects lowercase keywords
  }
  if (obj.type === 'percentage' && typeof obj.percentage === 'number') {
    return `${obj.percentage}%`;                                      // explicit percentage payload
  }
  if (obj.type === 'percentage' && typeof obj.value === 'number') {
    return `${obj.value}%`;                                           // alternate 'value' key
  }
  if (obj.type === 'length' && typeof obj.px === 'number') {
    return `${obj.px}px`;                                             // canonical length
  }
  if (typeof obj.px === 'number') return `${obj.px}px`;               // bare {px:N}
  return null;                                                        // unknown shape
}

// Entry point — last X/Y write wins (CSS cascade).
export function extractBackgroundPosition(
  properties: IRPropertyLike[],
): BackgroundPositionConfig {
  const cfg: BackgroundPositionConfig = {};                           // blank accumulator
  for (const p of properties) {
    if (!isBackgroundPositionProperty(p.type)) continue;              // filter
    if (p.type === BACKGROUND_POSITION) {                             // shorthand tagged-list wire
      applyShorthandList(p.data, cfg);                                // folds both axes at once
      continue;                                                       // handled — skip axis parse
    }
    const token = parseAxis(p.data);                                  // parse one axis
    if (token === null) continue;                                     // unknown shape -> drop
    // Logical → physical fold: block→Y, inline→X (LTR horizontal mode).
    if (p.type === BACKGROUND_POSITION_X || p.type === BACKGROUND_POSITION_INLINE) cfg.x = token;
    else cfg.y = token;
  }
  return cfg;
}
