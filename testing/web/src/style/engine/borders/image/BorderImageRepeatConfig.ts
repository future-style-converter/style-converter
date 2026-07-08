// BorderImageRepeatConfig.ts — typed record for the `border-image-repeat` IR property.
// Mirrors src/main/kotlin/app/parsing/css/properties/longhands/borders/image/BorderImageRepeatPropertyParser.kt.



// Single-field config — `repeat` is absent when the property isn't set.
export interface BorderImageRepeatConfig {
  repeat?: { horizontal: string; vertical: string };                                               // parsed IR value
}

// IR property type string — used by both extractor + registry.
export const BORDER_IMAGE_REPEAT_PROPERTY_TYPE = 'BorderImageRepeat' as const;
export type BorderImageRepeatPropertyType = typeof BORDER_IMAGE_REPEAT_PROPERTY_TYPE;
