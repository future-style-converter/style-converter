// IRDecode.test.ts — unit tests for the irVersion gate corners that the
// golden-driven conformance suite doesn't reach: legacy-field translation
// details (_tag/_pseudo passthrough, id synthesis), slot.name handling on
// the v2 path, and malformed-envelope rejection.
import { describe, it, expect, vi, beforeAll, afterAll } from 'vitest';
import { decodeIRDocument } from '../../src/core/ir/IRDecode';

// The v1 path warns by design; silence it for readable output (the warn
// count itself is asserted in conformance.test.ts).
beforeAll(() => vi.spyOn(console, 'warn').mockImplementation(() => {}));
afterAll(() => vi.restoreAllMocks());

describe('decodeIRDocument — envelope validation', () => {
  it('rejects non-object input', () => {
    expect(() => decodeIRDocument(null)).toThrow(/JSON object/);
    expect(() => decodeIRDocument([1, 2])).toThrow(/JSON object/);
    expect(() => decodeIRDocument('nope')).toThrow(/JSON object/);
  });

  it('rejects a document without a components array', () => {
    expect(() => decodeIRDocument({ irVersion: 2 })).toThrow(/components array/);
  });

  it('accepts a newer irVersion when minReaderVersion is still satisfiable', () => {
    // Forward-compat rule: the writer may be newer as long as it declares
    // this reader can still consume the doc (spec 05).
    const doc = decodeIRDocument({ irVersion: 3, minReaderVersion: 2, components: [] });
    expect(doc.components).toEqual([]);
  });
});

describe('decodeIRDocument — v2 slot handling', () => {
  it('reconstructs the omitted default slot name "content"', () => {
    const doc = decodeIRDocument({
      irVersion: 2,
      minReaderVersion: 2,
      components: [
        { id: 'p-001', name: 'P', properties: [] },
        { id: 'c-002', name: 'C', properties: [], slot: { parent: 'p-001' } },
      ],
    });
    expect(doc.components[1].slot).toEqual({ parent: 'p-001', name: 'content' });
  });

  it('round-trips an explicit non-default slot name verbatim', () => {
    const doc = decodeIRDocument({
      irVersion: 2,
      minReaderVersion: 2,
      components: [
        { id: 'p-001', name: 'P', properties: [] },
        { id: 'c-002', name: 'C', properties: [], slot: { parent: 'p-001', name: 'header' } },
      ],
    });
    expect(doc.components[1].slot).toEqual({ parent: 'p-001', name: 'header' });
  });

  it('treats a malformed slot (no string parent) as a root, not a crash', () => {
    const doc = decodeIRDocument({
      irVersion: 2,
      minReaderVersion: 2,
      components: [{ id: 'x-001', name: 'X', properties: [], slot: { name: 'content' } }],
    });
    expect(doc.components[0].slot).toBeUndefined();
  });
});

describe('decodeIRDocument — v1 translation details', () => {
  it('moves _tag and _role into meta, forwards _pseudo verbatim as pseudos', () => {
    const doc = decodeIRDocument({
      components: [
        {
          id: 'li-001',
          name: 'Item',
          properties: [],
          selectors: [],
          media: [],
          children: null,
          _text: 'item text',
          _tag: 'li',
          _role: 'list-item',
          _pseudo: { marker: { id: 'm-1', properties: [], _text: '1.' } },
        },
      ],
    });
    const c = doc.components[0];
    expect(c.text).toBe('item text');
    expect(c.meta).toEqual({ sourceTag: 'li', role: 'list-item' });
    // The pseudo payload is extractor-owned: forwarded verbatim, inner
    // `_text` spelling preserved (the renderer tolerates both spellings).
    expect(c.pseudos?.marker?._text).toBe('1.');
  });

  it('drops empty selectors/media (v2 omit-when-empty) but keeps non-empty', () => {
    const doc = decodeIRDocument({
      components: [
        {
          id: 'a-001',
          name: 'A',
          properties: [],
          selectors: [{ condition: 'hover', properties: [] }],
          media: [],
          children: null,
        },
      ],
    });
    expect(doc.components[0].selectors).toHaveLength(1);
    expect(doc.components[0].media).toBeUndefined();
  });

  it('synthesizes stable ids for hand-authored children without ids', () => {
    const doc = decodeIRDocument({
      components: [
        {
          id: 'p-001',
          name: 'P',
          properties: [],
          children: [{ name: 'C', properties: [] }],
        },
      ],
    });
    // The child gets a deterministic id derived from its parent + index,
    // and its slot ref points at the real parent id.
    expect(doc.components[1].id).toBe('p-001-child-0');
    expect(doc.components[1].slot?.parent).toBe('p-001');
  });

  it('flattens deep nesting in pre-order with slot stamping at each level', () => {
    const doc = decodeIRDocument({
      components: [
        {
          id: 'r-001',
          name: 'Root',
          properties: [],
          children: [
            {
              id: 'a-002',
              name: 'A',
              properties: [],
              children: [{ id: 'x-003', name: 'X', properties: [] }],
            },
            { id: 'b-004', name: 'B', properties: [] },
          ],
        },
      ],
    });
    // Pre-order: root, A, X (A's child), then B (A's sibling).
    expect(doc.components.map((c) => c.id)).toEqual(['r-001', 'a-002', 'x-003', 'b-004']);
    expect(doc.components[2].slot?.parent).toBe('a-002');
    expect(doc.components[3].slot?.parent).toBe('r-001');
  });
});

describe('decodeIRDocument — keyframes (additive v2 key, spec 07 §1.2)', () => {
  // Minimal valid v2 envelope factory for the keyframes-focused pins.
  const v2 = (keyframes?: unknown) => ({
    irVersion: 2,
    minReaderVersion: 2,
    components: [{ id: 'c1', name: 'C1', properties: [] }],
    ...(keyframes !== undefined ? { keyframes } : {}),
  });

  it('passes a valid keyframes map through with offsets + typed stops intact', () => {
    const doc = decodeIRDocument(v2({
      fade: [
        { offset: 0, properties: [{ type: 'Opacity', data: { alpha: 0 } }] },
        { offset: 1, properties: [{ type: 'Opacity', data: { alpha: 1 } }] },
      ],
    }));
    expect(doc.keyframes).toBeDefined();
    expect(doc.keyframes!.fade.map((s) => s.offset)).toEqual([0, 1]);
    expect(doc.keyframes!.fade[0].properties[0].type).toBe('Opacity');
  });

  it('omits the key entirely when the wire had none (baseline byte-shape)', () => {
    const doc = decodeIRDocument(v2());
    expect('keyframes' in doc).toBe(false);
  });

  it('drops malformed stops (bad offsets / missing properties), whole sets when empty', () => {
    const doc = decodeIRDocument(v2({
      // out-of-range + non-numeric offsets and a property-less stop are
      // corruption (the converter pre-resolves offsets to [0,1] and the
      // schema pins minItems 1) — decode-side tolerance drops, not crashes.
      broken: [
        { offset: 1.5, properties: [] },
        { offset: 'from', properties: [] },
        { offset: 0.5 },
      ],
      ok: [{ offset: 0.5, properties: [] }],
    }));
    expect(doc.keyframes).toBeDefined();
    expect(Object.keys(doc.keyframes!)).toEqual(['ok']);
  });

  it('decode is idempotent with keyframes present (hot-reload path)', () => {
    const once = decodeIRDocument(v2({
      spin: [{ offset: 0, properties: [{ type: 'Transform', data: { type: 'functions', list: [] } }] }],
    }));
    expect(decodeIRDocument(once)).toEqual(once);
  });
});
