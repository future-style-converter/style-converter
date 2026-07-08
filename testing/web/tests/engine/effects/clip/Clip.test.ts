// Clip.test.ts — Phase-8 clip-path / clip-rule / legacy clip / geometry-box.

import { describe, it, expect } from 'vitest';
import { extractClipPath } from '../../../../src/style/engine/effects/clip/ClipPathExtractor';
import { applyClipPath } from '../../../../src/style/engine/effects/clip/ClipPathApplier';
import { extractClipRule } from '../../../../src/style/engine/effects/clip/ClipRuleExtractor';
import { applyClipRule } from '../../../../src/style/engine/effects/clip/ClipRuleApplier';
import { extractClip } from '../../../../src/style/engine/effects/clip/ClipExtractor';
import { applyClip } from '../../../../src/style/engine/effects/clip/ClipApplier';
import { extractClipPathGeometryBox } from '../../../../src/style/engine/effects/clip/ClipPathGeometryBoxExtractor';
import { applyClipPathGeometryBox } from '../../../../src/style/engine/effects/clip/ClipPathGeometryBoxApplier';

const p = (type: string, data: unknown) => ({ type, data });

describe('ClipPath', () => {
  it("'none' bare string", () => {
    expect(applyClipPath(extractClipPath([p('ClipPath', 'none')])))
      .toEqual({ clipPath: 'none' });
  });
  it('SVG reference #id', () => {
    expect(applyClipPath(extractClipPath([p('ClipPath', '#clipShape')])))
      .toEqual({ clipPath: '#clipShape' });
  });
  it('inset(t r b l)', () => {
    expect(applyClipPath(extractClipPath([p('ClipPath',
      { type: 'inset', t: { px: 10 }, r: { px: 20 }, b: { px: 30 }, l: { px: 40 } })])))
      .toEqual({ clipPath: 'inset(10px 20px 30px 40px)' });
  });
  it('inset with round', () => {
    expect(applyClipPath(extractClipPath([p('ClipPath',
      { type: 'inset', t: { px: 10 }, r: { px: 10 }, b: { px: 10 }, l: { px: 10 }, round: { px: 20 } })])))
      .toEqual({ clipPath: 'inset(10px 10px 10px 10px round 20px)' });
  });
  it('circle with px radius', () => {
    expect(applyClipPath(extractClipPath([p('ClipPath', { type: 'circle', px: 50 })])).clipPath)
      .toContain('circle(50px');
  });
  it('ellipse with position', () => {
    expect(applyClipPath(extractClipPath([p('ClipPath',
      { type: 'ellipse', rx: { px: 60 }, ry: { px: 40 }, pos: { x: { px: 10 }, y: { px: 20 } } })])))
      .toEqual({ clipPath: 'ellipse(60px 40px at 10px 20px)' });
  });
  it('polygon emits percent-paired points', () => {
    const r = applyClipPath(extractClipPath([p('ClipPath',
      { type: 'polygon', points: [{ x: 50, y: 0 }, { x: 100, y: 100 }, { x: 0, y: 100 }] })]));
    expect(r).toEqual({ clipPath: 'polygon(50% 0%, 100% 100%, 0% 100%)' });
  });
  it('path("d")', () => {
    expect(applyClipPath(extractClipPath([p('ClipPath', { type: 'path', d: 'M 0 0 L 10 10 Z' })])))
      .toEqual({ clipPath: 'path("M 0 0 L 10 10 Z")' });
  });
  it('xywh with round', () => {
    expect(applyClipPath(extractClipPath([p('ClipPath',
      { type: 'xywh', x: { px: 10 }, y: { px: 20 }, w: { px: 120 }, h: { px: 80 }, round: { px: 12 } })])))
      .toEqual({ clipPath: 'xywh(10px 20px 120px 80px round 12px)' });
  });
  it('geometry-box alone', () => {
    expect(applyClipPath(extractClipPath([p('ClipPath', { 'geometry-box': 'border-box' })])))
      .toEqual({ clipPath: 'border-box' });
  });
  it('shape + geometry-box', () => {
    const r = applyClipPath(extractClipPath([p('ClipPath',
      { 'geometry-box': 'border-box', shape: { type: 'circle', px: 40 } })]));
    expect(r.clipPath).toContain('circle(40px');
    expect(r.clipPath).toContain('border-box');
  });
  it('empty on no properties', () => {
    expect(applyClipPath(extractClipPath([]))).toEqual({});
  });
});

describe('ClipRule', () => {
  it('NONZERO -> nonzero', () => {
    expect(applyClipRule(extractClipRule([p('ClipRule', 'NONZERO')]))).toEqual({ clipRule: 'nonzero' });
  });
  it('EVENODD -> evenodd', () => {
    expect(applyClipRule(extractClipRule([p('ClipRule', 'EVENODD')]))).toEqual({ clipRule: 'evenodd' });
  });
});

describe('Clip (legacy)', () => {
  it('auto', () => {
    expect(applyClip(extractClip([p('Clip', { type: 'auto' })]))).toEqual({ clip: 'auto' });
  });
  it('rect(T,R,B,L)', () => {
    expect(applyClip(extractClip([p('Clip',
      { type: 'rect', top: { px: 10 }, right: { px: 150 }, bottom: { px: 110 }, left: { px: 10 } })])))
      .toEqual({ clip: 'rect(10px, 150px, 110px, 10px)' });
  });
});

describe('ClipPathGeometryBox', () => {
  it('emits clipPath key with kebab keyword', () => {
    expect(applyClipPathGeometryBox(extractClipPathGeometryBox([p('ClipPathGeometryBox', 'PADDING_BOX')])))
      .toEqual({ clipPath: 'padding-box' });
  });
});
