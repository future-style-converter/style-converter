// OverflowExtractor.ts — enum-only.
import { enumExtractor } from '../effects/_shared';
import { OVERFLOW_PROPERTY_TYPE } from './OverflowConfig';
export const extractOverflow = enumExtractor(OVERFLOW_PROPERTY_TYPE);
