// RootClipPathResolver.test.ts — wave 49 (lane A4) pins for the
// document-element clip (css-masking-1 §5 on the root element).
//
// The two positive payloads are copied VERBATIM out of the wave-48 gate's
// per-test IR — `tools/titan/runs/wave48-final/sections/css-masking/
// per-test-ir/wpt__css-masking__clip-path__clip-path-document-element.json`
// — so a converter wire change fails here before it silently un-clips the
// page again.

import { describe, it, expect } from 'vitest';
import type { IRDocument } from '../../../src/core/ir/IRModels';
import { resolveRootClipPath } from '../../../src/engine/effects/clip/RootClipPathResolver';

/** The verbatim `html { background: red; clip-path: polygon(…) }` bag. */
const BODY_ROOT_PROPERTIES = [
  { type: 'BackgroundColor', data: { srgb: { r: 1, g: 0, b: 0 }, original: 'red' } },
  {
    type: 'ClipPath',
    data: {
      type: 'polygon',
      points: [
        { x: { px: 50 }, y: { px: 50 } },
        { x: { px: 100 }, y: { px: 50 } },
        { x: { px: 100 }, y: { px: 100 } },
        { x: { px: 150 }, y: { px: 100 } },
        { x: { px: 150 }, y: { px: 150 } },
        { x: { px: 50 }, y: { px: 150 } },
      ],
    },
  },
];

/** The verbatim `div { width:500px; height:500px; background:green }` sibling. */
const GREEN_DIV_PROPERTIES = [
  { type: 'Width', data: { type: 'length', px: 500 } },
  { type: 'Height', data: { type: 'length', px: 500 } },
  { type: 'BackgroundColor', data: { srgb: { r: 0, g: 0.5019607843137255, b: 0 }, original: 'green' } },
];

function docWith(bodyRootProperties: unknown[]): IRDocument {
  return {
    irVersion: 2,
    minReaderVersion: 2,
    components: [
      {
        id: 'wpt__css-masking__clip-path__clip-path-document-element__0-047',
        name: 'wpt__css-masking__clip-path__clip-path-document-element__0',
        properties: bodyRootProperties,
        meta: { role: 'body-root' },
      },
      {
        id: 'wpt__css-masking__clip-path__clip-path-document-element__1-048',
        name: 'wpt__css-masking__clip-path__clip-path-document-element__1',
        properties: GREEN_DIV_PROPERTIES,
      },
    ],
  } as unknown as IRDocument;
}

describe('resolveRootClipPath', () => {
  it('serialises the document-element polygon exactly as the browser receives it', () => {
    // The L shape the WPT test declares; the ref renders it at image
    // [66,66]-[165,165] once the canvas's 16px frame is added.
    expect(resolveRootClipPath(docWith(BODY_ROOT_PROPERTIES)))
      .toBe('polygon(50px 50px, 100px 50px, 100px 100px, 150px 100px, 150px 150px, 50px 150px)');
  });

  it('is silent for a body-root that declares no clip-path', () => {
    // The shape of ~1433 of the 1435 corpus documents — this is what keeps
    // every other composed capture byte-identical.
    expect(resolveRootClipPath(docWith([BODY_ROOT_PROPERTIES[0]]))).toBeUndefined();
  });

  it('is silent for the initial `none` keyword', () => {
    expect(resolveRootClipPath(docWith([{ type: 'ClipPath', data: 'none' }]))).toBeUndefined();
  });

  it('is silent for a document with no body-root at all', () => {
    const doc = { irVersion: 2, minReaderVersion: 2, components: [
      { id: 'x', name: 'x', properties: GREEN_DIV_PROPERTIES },
    ] } as unknown as IRDocument;
    expect(resolveRootClipPath(doc)).toBeUndefined();
  });

  it('reads the ROOT bag only — a clip on an ordinary component is not the page clip', () => {
    const doc = { irVersion: 2, minReaderVersion: 2, components: [
      { id: 'r', name: 'r', properties: [], meta: { role: 'body-root' } },
      { id: 'c', name: 'c', properties: BODY_ROOT_PROPERTIES },
    ] } as unknown as IRDocument;
    expect(resolveRootClipPath(doc)).toBeUndefined();
  });

  it('is not blocked by containment — css-contain removes PROPAGATION, and a clip is not propagated', () => {
    // css-contain-2 §2 / css-contain-1 §2 take a contained root off the
    // background / writing-mode / direction propagation path. `clip-path` is
    // the root's own clip over its own subtree, so no containment clause
    // applies to it.
    const contained = [...BODY_ROOT_PROPERTIES, { type: 'Contain', data: ['LAYOUT'] }];
    expect(resolveRootClipPath(docWith(contained)))
      .toBe('polygon(50px 50px, 100px 50px, 100px 100px, 150px 100px, 150px 150px, 50px 150px)');
  });
});
