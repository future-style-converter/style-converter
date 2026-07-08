// TransformsDispatch.test.ts — Phase-8 transforms tripwire coverage.
// Exercises the per-property triplet + the _dispatch fold.

import { describe, it, expect } from 'vitest';
import { applyTransformsPhase8 } from '../../../src/style/engine/transforms/_dispatch';
import { extractTransform } from '../../../src/style/engine/transforms/TransformExtractor';
import { applyTransform } from '../../../src/style/engine/transforms/TransformApplier';
import { extractRotate } from '../../../src/style/engine/transforms/RotateExtractor';
import { applyRotate } from '../../../src/style/engine/transforms/RotateApplier';
import { extractScale } from '../../../src/style/engine/transforms/ScaleExtractor';
import { applyScale } from '../../../src/style/engine/transforms/ScaleApplier';
import { extractTranslate } from '../../../src/style/engine/transforms/TranslateExtractor';
import { applyTranslate } from '../../../src/style/engine/transforms/TranslateApplier';
import { extractTransformOrigin } from '../../../src/style/engine/transforms/TransformOriginExtractor';
import { applyTransformOrigin } from '../../../src/style/engine/transforms/TransformOriginApplier';

const p = (type: string, data: unknown) => ({ type, data });

describe('Transform function list', () => {
  it('empty list => none', () => {
    expect(applyTransform(extractTransform([p('Transform', { type: 'functions', list: [] })])))
      .toEqual({ transform: 'none' });
  });
  it('translateX(20px)', () => {
    expect(applyTransform(extractTransform([p('Transform',
      { type: 'functions', list: [{ fn: 'translateX', x: { px: 20 } }] })])))
      .toEqual({ transform: 'translateX(20px)' });
  });
  it('translate(x,y)', () => {
    expect(applyTransform(extractTransform([p('Transform',
      { type: 'functions', list: [{ fn: 'translate', x: { px: 10 }, y: { px: 20 } }] })])))
      .toEqual({ transform: 'translate(10px, 20px)' });
  });
  it('translate3d', () => {
    expect(applyTransform(extractTransform([p('Transform',
      { type: 'functions', list: [{ fn: 'translate3d', x: { px: 1 }, y: { px: 2 }, z: { px: 3 } }] })])))
      .toEqual({ transform: 'translate3d(1px, 2px, 3px)' });
  });
  it('rotate(45deg)', () => {
    expect(applyTransform(extractTransform([p('Transform',
      { type: 'functions', list: [{ fn: 'rotate', a: { deg: 45 } }] })])))
      .toEqual({ transform: 'rotate(45deg)' });
  });
  it('rotate3d', () => {
    expect(applyTransform(extractTransform([p('Transform',
      { type: 'functions', list: [{ fn: 'rotate3d', x: 1, y: 0, z: 0, a: { deg: 90 } }] })])))
      .toEqual({ transform: 'rotate3d(1, 0, 0, 90deg)' });
  });
  it('scale(x,y) with y mirroring x', () => {
    expect(applyTransform(extractTransform([p('Transform',
      { type: 'functions', list: [{ fn: 'scale', x: 1.5 }] })])))
      .toEqual({ transform: 'scale(1.5, 1.5)' });
  });
  it('skew(x,y)', () => {
    expect(applyTransform(extractTransform([p('Transform',
      { type: 'functions', list: [{ fn: 'skew', x: { deg: 15 }, y: { deg: 5 } }] })])))
      .toEqual({ transform: 'skew(15deg, 5deg)' });
  });
  it('matrix with 6 numbers', () => {
    expect(applyTransform(extractTransform([p('Transform',
      { type: 'functions', list: [{ fn: 'matrix', a: 1, b: 0.2, c: -0.2, d: 1, e: 10, f: 20 }] })])))
      .toEqual({ transform: 'matrix(1, 0.2, -0.2, 1, 10, 20)' });
  });
  it('multi-fn chain preserves order', () => {
    const r = applyTransform(extractTransform([p('Transform',
      { type: 'functions', list: [
        { fn: 'translate', x: { px: 20 }, y: { px: 0 } },
        { fn: 'rotate', a: { deg: 45 } },
      ] })]));
    expect(r).toEqual({ transform: 'translate(20px, 0px) rotate(45deg)' });
  });
  it('perspective as inline transform-function', () => {
    expect(applyTransform(extractTransform([p('Transform',
      { type: 'functions', list: [{ fn: 'perspective', l: { px: 500 } }] })])))
      .toEqual({ transform: 'perspective(500px)' });
  });
  it('explicit none', () => {
    expect(applyTransform(extractTransform([p('Transform', { type: 'none' })])))
      .toEqual({ transform: 'none' });
  });
  it('last-write-wins', () => {
    const r = applyTransform(extractTransform([
      p('Transform', { type: 'functions', list: [{ fn: 'translateX', x: { px: 10 } }] }),
      p('Transform', { type: 'functions', list: [{ fn: 'scale', x: 2, y: 2 }] }),
    ]));
    expect(r.transform).toBe('scale(2, 2)');
  });
});

describe('Rotate longhand', () => {
  it('angle degrees', () => {
    expect(applyRotate(extractRotate([p('Rotate', { type: 'angle', deg: 45 })])))
      .toEqual({ rotate: '45deg' });
  });
  it('axis-angle 3D', () => {
    expect(applyRotate(extractRotate([p('Rotate',
      { type: 'axis-angle', x: 1, y: 0, z: 0, angle: { deg: 45 } })])))
      .toEqual({ rotate: '1 0 0 45deg' });
  });
  it('none keyword', () => {
    expect(applyRotate(extractRotate([p('Rotate', { type: 'none' })])))
      .toEqual({ rotate: 'none' });
  });
});

describe('Scale longhand', () => {
  it('uniform', () => {
    expect(applyScale(extractScale([p('Scale', { type: 'uniform', value: 1.5 })])))
      .toEqual({ scale: '1.5' });
  });
  it('2d', () => {
    expect(applyScale(extractScale([p('Scale', { type: '2d', x: 1.2, y: 0.8 })])))
      .toEqual({ scale: '1.2 0.8' });
  });
  it('3d', () => {
    expect(applyScale(extractScale([p('Scale', { type: '3d', x: 1.2, y: 0.9, z: 1.0 })])))
      .toEqual({ scale: '1.2 0.9 1' });
  });
  it('none', () => {
    expect(applyScale(extractScale([p('Scale', { type: 'none' })])))
      .toEqual({ scale: 'none' });
  });
});

describe('Translate longhand', () => {
  it('single length', () => {
    expect(applyTranslate(extractTranslate([p('Translate',
      { type: 'length', length: { px: 20 } })])))
      .toEqual({ translate: '20px' });
  });
  it('2d length+length', () => {
    expect(applyTranslate(extractTranslate([p('Translate',
      { type: '2d', x: { type: 'length', px: 20 }, y: { type: 'length', px: 10 } })])))
      .toEqual({ translate: '20px 10px' });
  });
  it('2d percentage', () => {
    expect(applyTranslate(extractTranslate([p('Translate',
      { type: '2d', x: { type: 'percentage', percentage: 10 }, y: { type: 'percentage', percentage: 5 } })])))
      .toEqual({ translate: '10% 5%' });
  });
});

describe('TransformOrigin', () => {
  it('two keywords', () => {
    expect(applyTransformOrigin(extractTransformOrigin([p('TransformOrigin',
      { x: { type: 'keyword', value: 'TOP' }, y: { type: 'keyword', value: 'LEFT' } })])))
      .toEqual({ transformOrigin: 'top left' });
  });
  it('percentages', () => {
    expect(applyTransformOrigin(extractTransformOrigin([p('TransformOrigin',
      { x: { type: 'percentage', percentage: 50 }, y: { type: 'percentage', percentage: 50 } })])))
      .toEqual({ transformOrigin: '50% 50%' });
  });
  it('lengths with z', () => {
    expect(applyTransformOrigin(extractTransformOrigin([p('TransformOrigin',
      { x: { type: 'length', px: 20 }, y: { type: 'length', px: 40 }, z: { px: 10 } })])))
      .toEqual({ transformOrigin: '20px 40px 10px' });
  });
});

describe('applyTransformsPhase8', () => {
  it('empty → empty object', () => {
    expect(applyTransformsPhase8([])).toEqual({});
  });
  it('folds Transform + TransformOrigin + BackfaceVisibility together', () => {
    const r = applyTransformsPhase8([
      p('Transform', { type: 'functions', list: [{ fn: 'scale', x: 2, y: 2 }] }),
      p('TransformOrigin', { x: { type: 'keyword', value: 'CENTER' }, y: { type: 'keyword', value: 'CENTER' } }),
      p('BackfaceVisibility', 'HIDDEN'),
    ]);
    expect(r).toEqual({ transform: 'scale(2, 2)', transformOrigin: 'center center', backfaceVisibility: 'hidden' });
  });
  it('folds Perspective + PerspectiveOrigin', () => {
    const r = applyTransformsPhase8([
      p('Perspective', { type: 'length', px: 500 }),
      p('PerspectiveOrigin', { x: { type: 'top' }, y: { type: 'left' } }),
    ]);
    expect(r).toEqual({ perspective: '500px', perspectiveOrigin: 'top left' });
  });
  it('Perspective none', () => {
    expect(applyTransformsPhase8([p('Perspective', { type: 'none' })])).toEqual({ perspective: 'none' });
  });
  it('TransformBox kebabs SHOUTY_SNAKE', () => {
    expect(applyTransformsPhase8([p('TransformBox', 'FILL_BOX')]))
      .toEqual({ transformBox: 'fill-box' });
  });
  it('TransformStyle', () => {
    expect(applyTransformsPhase8([p('TransformStyle', 'PRESERVE_3D')]))
      .toEqual({ transformStyle: 'preserve-3d' });
  });
});
