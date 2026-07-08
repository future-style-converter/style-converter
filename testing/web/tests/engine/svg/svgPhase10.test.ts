// svgPhase10.test.ts — Phase-10 long-tail smoke tests.
import { describe, it, expect } from 'vitest';
import { applySvgPhase10 } from '../../../src/style/engine/svg/_dispatch';

describe('applySvgPhase10', () => {
  it('empty input → empty output', () => {
    expect(applySvgPhase10([])).toEqual({});
  });
  it('FillRule → evenodd', () => {
    expect(applySvgPhase10([{ type: 'FillRule', data: 'EVENODD' }]))
      .toEqual({ fillRule: 'evenodd' });
  });
  it('StrokeLinecap → round', () => {
    expect(applySvgPhase10([{ type: 'StrokeLinecap', data: 'ROUND' }]))
      .toEqual({ strokeLinecap: 'round' });
  });
  it('StrokeLinejoin → bevel', () => {
    expect(applySvgPhase10([{ type: 'StrokeLinejoin', data: 'BEVEL' }]))
      .toEqual({ strokeLinejoin: 'bevel' });
  });
  it('ColorInterpolation → srgb', () => {
    expect(applySvgPhase10([{ type: 'ColorInterpolation', data: 'SRGB' }]))
      .toEqual({ colorInterpolation: 'srgb' });
  });
  it('ShapeRendering → crisp-edges', () => {
    expect(applySvgPhase10([{ type: 'ShapeRendering', data: 'CRISP_EDGES' }]))
      .toEqual({ shapeRendering: 'crisp-edges' });
  });
  it('VectorEffect → non-scaling-stroke', () => {
    expect(applySvgPhase10([{ type: 'VectorEffect', data: 'NON_SCALING_STROKE' }]))
      .toEqual({ vectorEffect: 'non-scaling-stroke' });
  });
  it('PaintOrder → stroke-fill', () => {
    expect(applySvgPhase10([{ type: 'PaintOrder', data: 'STROKE_FILL' }]))
      .toEqual({ paintOrder: 'stroke-fill' });
  });
});
