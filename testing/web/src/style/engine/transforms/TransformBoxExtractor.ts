// TransformBoxExtractor.ts — enum-only; uses the shared `enumExtractor` factory.
import { enumExtractor } from '../effects/_shared';
import { TRANSFORM_BOX_PROPERTY_TYPE } from './TransformBoxConfig';

export const extractTransformBox = enumExtractor(TRANSFORM_BOX_PROPERTY_TYPE);
