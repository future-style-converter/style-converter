// VisibilityExtractor.ts — enum-only; uses shared factory.
import { enumExtractor } from '../effects/_shared';
import { VISIBILITY_PROPERTY_TYPE } from './VisibilityConfig';
export const extractVisibility = enumExtractor(VISIBILITY_PROPERTY_TYPE);
