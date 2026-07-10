// schema/conformance/run.test.mjs — unit tests for the conformance runner
// itself (node:test, run with `node --test schema/conformance/run.test.mjs`).
//
// Covers: both schemas compile; valid v1 AND v2 documents pass their own
// contract; each envelope-level mutation fails with a pointed error
// (including the v2 hard errors: children key, missing version pair,
// unknown slot/meta keys); the CLI exits 0 on the goldens.

import { test } from 'node:test';
import assert from 'node:assert/strict';
import { execFileSync } from 'node:child_process';
import { fileURLToPath } from 'node:url';
import { makeValidator, makeValidatorV2, validateDocument } from './run.mjs';

// One compiled validator per contract shared across tests (compilation is
// the slow part).
const validate = makeValidator();
const validateV2 = makeValidatorV2();

// A minimal-but-complete valid document exercising every envelope level.
function validDoc() {
  return {
    components: [
      {
        id: 'c-001',
        name: 'Card',
        properties: [
          { type: 'PaddingTop', data: { px: 16.0 } },
          { type: 'MarginLeft', data: 'auto' },
          { type: 'PaddingLeft', data: 10.0 },
        ],
        selectors: [{ condition: 'hover', properties: [{ type: 'Opacity', data: 0.5 }] }],
        media: [{ query: '(min-width: 768px)', properties: [] }],
        children: [{ id: 'c-002', name: 'child__0', properties: [] }],
        _text: 'hello',
        _role: 'body-root',
      },
    ],
  };
}

test('valid document passes', () => {
  const { ok, errors } = validateDocument(validate, validDoc());
  assert.equal(ok, true, JSON.stringify(errors));
});

test('empty components array is valid', () => {
  assert.equal(validateDocument(validate, { components: [] }).ok, true);
});

test('unknown top-level key fails', () => {
  const doc = { ...validDoc(), irVersion: 2 }; // v1 has NO version field (spec 05)
  const { ok, errors } = validateDocument(validate, doc);
  assert.equal(ok, false);
  assert.ok(errors.some((e) => e.message.includes('irVersion')));
});

test('unknown component key fails (unknown envelope keys are an error)', () => {
  const doc = validDoc();
  doc.components[0].surprise = true;
  const { ok, errors } = validateDocument(validate, doc);
  assert.equal(ok, false);
  assert.ok(errors.some((e) => e.path === '/components/0' && e.message.includes('surprise')));
});

test('property envelope missing "type" fails', () => {
  const doc = validDoc();
  doc.components[0].properties.push({ data: { px: 1 } });
  const { ok, errors } = validateDocument(validate, doc);
  assert.equal(ok, false);
  assert.ok(errors.some((e) => e.path.startsWith('/components/0/properties/3')));
});

test('property envelope missing "data" fails', () => {
  const doc = validDoc();
  doc.components[0].properties.push({ type: 'Width' });
  assert.equal(validateDocument(validate, doc).ok, false);
});

test('property envelope with extra key fails', () => {
  const doc = validDoc();
  doc.components[0].properties[0].comment = 'nope';
  assert.equal(validateDocument(validate, doc).ok, false);
});

test('children as a map (input shape) fails — the wire is array-out', () => {
  const doc = validDoc();
  doc.components[0].children = { 'child__0': { id: 'x', name: 'x', properties: [] } };
  assert.equal(validateDocument(validate, doc).ok, false);
});

test('empty children array fails — serializer omits when empty', () => {
  const doc = validDoc();
  doc.components[0].children = [];
  assert.equal(validateDocument(validate, doc).ok, false);
});

test('components as a map (authoring shape) fails at the document level', () => {
  assert.equal(validateDocument(validate, { components: { Card: {} } }).ok, false);
});

test('_text must be a string', () => {
  const doc = validDoc();
  doc.components[0]._text = 42;
  assert.equal(validateDocument(validate, doc).ok, false);
});

test('empty-string _text is legal (extracted-but-empty signal)', () => {
  const doc = validDoc();
  doc.components[0]._text = '';
  assert.equal(validateDocument(validate, doc).ok, true);
});

test('selector bucket keeps the no-colon condition contract strict', () => {
  const doc = validDoc();
  doc.components[0].selectors[0].selector = ':hover'; // wrong key name (input-side shape)
  assert.equal(validateDocument(validate, doc).ok, false);
});

test('data accepts every JSON kind (permissive leaves by design)', () => {
  const doc = validDoc();
  doc.components[0].properties = [
    { type: 'A', data: null },
    { type: 'B', data: true },
    { type: 'C', data: [] },
    { type: 'D', data: {} },
    { type: 'E', data: 'FLEX' },
    { type: 'F', data: 700 },
  ];
  const { ok, errors } = validateDocument(validate, doc);
  assert.equal(ok, true, JSON.stringify(errors));
});

// ---------------------------------------------------------------------------
// IR v2 — flat-list slot/placement contract (schema/ir-v2.schema.json)
// ---------------------------------------------------------------------------

// A minimal-but-complete valid v2 document exercising every envelope level:
// version pair, flat components, slot ref, renamed text/pseudos/meta fields.
function validDocV2() {
  return {
    irVersion: 2,
    minReaderVersion: 2,
    components: [
      {
        id: 'card-001',
        name: 'Card',
        properties: [
          { type: 'Display', data: 'GRID' },
          { type: 'PaddingTop', data: { px: 16.0 } },
        ],
        selectors: [{ condition: 'hover', properties: [{ type: 'Opacity', data: 0.5 }] }],
        media: [{ query: '(min-width: 768px)', properties: [] }],
        text: 'hello',
        meta: { sourceTag: 'section', role: 'body-root' },
      },
      {
        id: 'thumb-002',
        name: 'Thumb',
        // ITEM-scoped claims live on the child as plain property envelopes.
        properties: [
          { type: 'GridRowStart', data: 'media' },
          { type: 'ZIndex', data: 3 },
        ],
        slot: { parent: 'card-001' },
        pseudos: { before: { properties: [] } },
      },
    ],
  };
}

test('v2: valid flat document passes', () => {
  const { ok, errors } = validateDocument(validateV2, validDocV2());
  assert.equal(ok, true, JSON.stringify(errors));
});

test('v2: zero-slot document is valid (Mode B — composition supplied externally)', () => {
  const doc = validDocV2();
  delete doc.components[1].slot;
  assert.equal(validateDocument(validateV2, doc).ok, true);
});

test('v2: children key is a hard error (flat list only)', () => {
  const doc = validDocV2();
  doc.components[0].children = [{ id: 'x', name: 'x', properties: [] }];
  const { ok, errors } = validateDocument(validateV2, doc);
  assert.equal(ok, false);
  assert.ok(errors.some((e) => e.message.includes('children')));
});

test('v2: missing irVersion fails (version pair is mandatory)', () => {
  const doc = validDocV2();
  delete doc.irVersion;
  assert.equal(validateDocument(validateV2, doc).ok, false);
});

test('v2: missing minReaderVersion fails', () => {
  const doc = validDocV2();
  delete doc.minReaderVersion;
  assert.equal(validateDocument(validateV2, doc).ok, false);
});

test('v2: wrong irVersion value fails (const 2)', () => {
  const doc = validDocV2();
  doc.irVersion = 3;
  assert.equal(validateDocument(validateV2, doc).ok, false);
});

test('v2: slot without parent fails (parent is required inside the object)', () => {
  const doc = validDocV2();
  doc.components[1].slot = { name: 'content' };
  assert.equal(validateDocument(validateV2, doc).ok, false);
});

test('v2: unknown slot key fails (slot is a frozen structure)', () => {
  const doc = validDocV2();
  doc.components[1].slot = { parent: 'card-001', index: 0 };
  assert.equal(validateDocument(validateV2, doc).ok, false);
});

test('v2: explicit non-default slot name is valid (multi-slot reservation)', () => {
  const doc = validDocV2();
  doc.components[1].slot = { parent: 'card-001', name: 'header' };
  assert.equal(validateDocument(validateV2, doc).ok, true);
});

test('v2: legacy underscore names are rejected (_text/_role renamed)', () => {
  const doc = validDocV2();
  doc.components[0]._text = 'old name';
  const { ok, errors } = validateDocument(validateV2, doc);
  assert.equal(ok, false);
  assert.ok(errors.some((e) => e.message.includes('_text')));
});

test('v2: unknown meta key fails (meta is a frozen structure)', () => {
  const doc = validDocV2();
  doc.components[0].meta = { role: 'body-root', extra: true };
  assert.equal(validateDocument(validateV2, doc).ok, false);
});

test('v2: empty meta object fails (omit-when-empty ⇒ present is non-empty)', () => {
  const doc = validDocV2();
  doc.components[0].meta = {};
  assert.equal(validateDocument(validateV2, doc).ok, false);
});

test('v2: empty-string text is legal (extracted-but-empty signal, carried over)', () => {
  const doc = validDocV2();
  doc.components[0].text = '';
  assert.equal(validateDocument(validateV2, doc).ok, true);
});

test('v2: data accepts every JSON kind (leaves stay permissive by design)', () => {
  const doc = validDocV2();
  doc.components[0].properties = [
    { type: 'A', data: null },
    { type: 'B', data: true },
    { type: 'C', data: [] },
    { type: 'D', data: {} },
    { type: 'E', data: 'FLEX' },
    { type: 'F', data: 700 },
  ];
  const { ok, errors } = validateDocument(validateV2, doc);
  assert.equal(ok, true, JSON.stringify(errors));
});

test('v2: a v1-shaped document (no version pair, nested children) fails the v2 contract', () => {
  // The v1 validDoc() carries _text/_role/children — all three are v2
  // violations on top of the missing version pair.
  assert.equal(validateDocument(validateV2, validDoc()).ok, false);
});

test('CLI: default mode exits 0 on the golden fixtures', () => {
  const runner = fileURLToPath(new URL('./run.mjs', import.meta.url));
  // Throws on non-zero exit — that IS the assertion.
  const out = execFileSync(process.execPath, [runner], { encoding: 'utf8' });
  assert.match(out, /IR conformance: all documents valid \(v1 goldens \+ v2 goldens\)/);
});
