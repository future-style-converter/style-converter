// schema/conformance/run.test.mjs — unit tests for the conformance runner
// itself (node:test, run with `node --test schema/conformance/run.test.mjs`).
//
// Covers: schema compiles; a valid document passes; each envelope-level
// mutation fails with a pointed error; the CLI exits 0 on the goldens.

import { test } from 'node:test';
import assert from 'node:assert/strict';
import { execFileSync } from 'node:child_process';
import { fileURLToPath } from 'node:url';
import { makeValidator, validateDocument } from './run.mjs';

// One compiled validator shared across tests (compilation is the slow part).
const validate = makeValidator();

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

test('CLI: default mode exits 0 on the golden fixtures', () => {
  const runner = fileURLToPath(new URL('./run.mjs', import.meta.url));
  // Throws on non-zero exit — that IS the assertion.
  const out = execFileSync(process.execPath, [runner], { encoding: 'utf8' });
  assert.match(out, /IR v1 conformance: all documents valid/);
});
