// ReadingFlowConfig.ts — typed config for the CSS `readingFlow` property.
// Mirrors CSS Display L4 draft — widened: csstype has no `reading-flow` key.
// The IR emits the enum as a SHOUTY_SNAKE string; this config carries the
// already-kebabed CSS string so the applier stays a pure format step.

export interface ReadingFlowConfig {
  value?: string;                                                   // kebab-case keyword, or undefined = absent
}

// Name exported for tests + registry assertions (no magic strings).
export const READINGFLOW_PROPERTY_TYPE = 'ReadingFlow' as const;
export type ReadingFlowPropertyType = typeof READINGFLOW_PROPERTY_TYPE;
