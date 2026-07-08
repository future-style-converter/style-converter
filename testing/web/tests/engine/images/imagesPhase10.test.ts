// imagesPhase10.test.ts — Phase-10 long-tail smoke tests.
import { describe, it, expect } from 'vitest';
import { applyImagesPhase10 } from '../../../src/style/engine/images/_dispatch';

describe('applyImagesPhase10', () => {
  it('empty input → empty output', () => {
    expect(applyImagesPhase10([])).toEqual({});
  });
  it('ImageRendering → pixelated', () => {
    expect(applyImagesPhase10([{ type: 'ImageRendering', data: 'PIXELATED' }]))
      .toEqual({ imageRendering: 'pixelated' });
  });
  it('ObjectFit → cover', () => {
    expect(applyImagesPhase10([{ type: 'ObjectFit', data: 'COVER' }]))
      .toEqual({ objectFit: 'cover' });
  });
});
