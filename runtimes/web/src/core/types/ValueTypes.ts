/**
 * Core value types for IR normalization.
 *
 * These types mirror the Kotlin ValueTypes.kt, providing
 * normalized values that can be easily converted to CSS.
 */

// ==================== Colors ====================

/**
 * sRGB color with normalized 0-1 values.
 */
export interface SRGB {
  r: number;
  g: number;
  b: number;
  a?: number;
}

/**
 * IR Color with normalized sRGB and original representation.
 */
export interface IRColor {
  srgb: SRGB | null;
  original?: string | ColorRepresentation;
}

export type ColorRepresentation =
  | string
  | { type: 'hex'; value: string }
  | { type: 'rgb'; r: number; g: number; b: number; a?: number }
  | { type: 'hsl'; h: number; s: number; l: number; a?: number }
  | { type: 'named'; name: string }
  | { type: 'expression'; expr: string };

// ==================== Lengths ====================

export enum LengthUnit {
  PX = 'px',
  PT = 'pt',
  PC = 'pc',
  IN = 'in',
  CM = 'cm',
  MM = 'mm',
  Q = 'Q',
  EM = 'em',
  REM = 'rem',
  PERCENT = '%',
  VW = 'vw',
  VH = 'vh',
  VMIN = 'vmin',
  VMAX = 'vmax',
  CH = 'ch',
  EX = 'ex',
  FR = 'fr',
}

/**
 * IR Length with normalized pixels and original representation.
 */
export interface IRLength {
  /** Normalized to pixels (null for relative units like %, em, rem) */
  px?: number | null;
  /** Original value */
  v?: number;
  /** Original unit */
  u?: LengthUnit | string;
}

// ==================== Angles ====================

export enum AngleUnit {
  DEG = 'deg',
  RAD = 'rad',
  GRAD = 'grad',
  TURN = 'turn',
}

/**
 * IR Angle normalized to degrees.
 */
export interface IRAngle {
  /** Normalized to degrees */
  deg: number;
  /** Original value */
  v?: number;
  /** Original unit */
  u?: AngleUnit;
}

// ==================== Time ====================

export enum TimeUnit {
  MS = 'ms',
  S = 's',
}

/**
 * IR Time normalized to milliseconds.
 */
export interface IRTime {
  /** Normalized to milliseconds */
  ms: number;
  /** Original value */
  v?: number;
  /** Original unit */
  u?: TimeUnit;
}

// ==================== Percentage ====================

export interface IRPercentage {
  value: number;
}

// ==================== Shadows / Gradients / Timing functions ====================
//
// DELETED (finding A6#13): the `IRShadow` shape, the gradient cluster
// (`ColorStop`, `Linear`/`Radial`/`ConicGradient`, `Gradient`) and the
// timing-function cluster (`CubicBezier`, `Steps`, `TimingFunction`) had
// zero importers anywhere in the repo. The live decoders for those IR
// payloads are the engine triplets — `engine/effects/shadow/`,
// `engine/background/BackgroundImage*` and `engine/animations/` — which
// carry their own typed shapes; these were an unused parallel vocabulary.

// ==================== Border Width ====================

export interface BorderWidth {
  /** Normalized to pixels */
  px: number;
  /** Original keyword or value */
  original?: 'thin' | 'medium' | 'thick' | number;
}

// ==================== Font Weight ====================

export interface FontWeight {
  /** Normalized numeric value (100-900) */
  weight?: number;
  /** Original keyword or value */
  original?: string | number;
}

// ==================== Font Size ====================

export interface FontSize {
  /** Normalized to pixels (null for relative sizes) */
  px?: number | null;
  /** Original value */
  original?: string | IRLength;
}

// ==================== Line Height ====================

export interface LineHeight {
  /** Normalized multiplier or pixels */
  value?: number;
  unit?: 'multiplier' | 'px';
  original?: string | number;
}

// ==================== Opacity ====================

export interface Opacity {
  /** Normalized 0-1 */
  alpha?: number;
  original?: number | string;
}

// ==================== Z-Index ====================

export interface ZIndex {
  value?: number;
  original?: 'auto' | number | string;
}
