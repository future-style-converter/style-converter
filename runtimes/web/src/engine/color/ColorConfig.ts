// ColorConfig.ts — typed record for the CSS text `color` IR property.
// Mirrors BackgroundColorConfig but targets foreground text color.

import type { ColorValue } from '../core/types/ColorValue';

// Holder for text color — optional so absence is detectable.
export interface ColorConfig {
  color?: ColorValue;                                                 // parsed text color (foreground)
}

// IR property type name recognised by this module.
export const COLOR_PROPERTY_TYPE = 'Color' as const;
export type ColorPropertyType = typeof COLOR_PROPERTY_TYPE;
