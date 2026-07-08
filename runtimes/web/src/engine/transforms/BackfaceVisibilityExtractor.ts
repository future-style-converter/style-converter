// BackfaceVisibilityExtractor.ts — enum-only.
import { enumExtractor } from '../effects/_shared';
import { BACKFACE_VISIBILITY_PROPERTY_TYPE } from './BackfaceVisibilityConfig';
export const extractBackfaceVisibility = enumExtractor(BACKFACE_VISIBILITY_PROPERTY_TYPE);
