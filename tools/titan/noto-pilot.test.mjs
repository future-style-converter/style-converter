#!/usr/bin/env node
//
// Unit tests for tools/titan/noto-pilot.mjs (wave-45 lane X6).
//
// The one property that matters most is BYTE-DISCIPLINE: with
// TITAN_NOTO_PILOT unset every export must collapse to identity/''/empty so
// the default pipeline cannot move. The flag-on shape is pinned second, and
// the CANVAS_REV integration (capture-browser-ref.mjs reads the suffix at
// module load) is pinned via a subprocess, because env-at-import cannot be
// exercised from an already-imported module graph.

import { test } from 'node:test';
import assert from 'node:assert/strict';
import { execFileSync } from 'node:child_process';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

import {
  notoPilotEnabled,
  notoPilotFontsDir,
  notoPilotRevSuffix,
  NOTO_PILOT_REV_SUFFIX,
  NOTO_PILOT_FACES,
  notoPilotStack,
  notoPilotFontFiles,
  notoPilotFontFaceCss,
  notoPilotWebCss,
} from './noto-pilot.mjs';
import { REF_FONT_STACK, CANVAS_REV, canvasFrameCss } from './capture-browser-ref.mjs';

const __dirname = dirname(fileURLToPath(import.meta.url));
const OFF = {};                       // no flag — the default pipeline
const ON  = { TITAN_NOTO_PILOT: '1' };

// ── flag OFF: every export is an identity ───────────────────────────────────

test('flag off: enabled=false for unset/other values, dir=null', () => {
  assert.equal(notoPilotEnabled(OFF), false);
  assert.equal(notoPilotEnabled({ TITAN_NOTO_PILOT: '0' }), false);
  assert.equal(notoPilotEnabled({ TITAN_NOTO_PILOT: 'true' }), false); // '1' only
  assert.equal(notoPilotFontsDir(OFF), null);
});

test('flag off: rev suffix is the EMPTY string (canvas rev byte-identical)', () => {
  assert.equal(notoPilotRevSuffix(OFF), '');
});

test('flag off: notoPilotStack returns the SAME string (identity, not a copy claim)', () => {
  assert.equal(notoPilotStack(REF_FONT_STACK, OFF), REF_FONT_STACK);
});

test('flag off: font-face css is empty and web css is empty', async () => {
  const io = { existsSync: () => { throw new Error('must not probe disk with the flag off'); },
               readFile: () => { throw new Error('must not read disk with the flag off'); } };
  assert.equal(await notoPilotFontFaceCss(OFF, io), '');
  assert.equal(await notoPilotWebCss(REF_FONT_STACK, OFF, io), '');
});

test('flag off: no files listed to push', () => {
  const r = notoPilotFontFiles(OFF, { existsSync: () => true });
  assert.deepEqual(r, { present: [], missing: [] });
});

test('flag off: the live CANVAS_REV and canvas frame carry no pilot trace', async () => {
  // This test file runs WITHOUT the env flag, so the imported constants are
  // the default pipeline's — pin them.
  assert.equal(CANVAS_REV, 'white-black-ink-font-lh-imgpad-htmlpins');
  const css = await canvasFrameCss();
  assert.ok(!css.includes('Noto Pilot'), 'default canvas frame must not mention pilot faces');
  assert.ok(css.includes(REF_FONT_STACK), 'default canvas frame keeps the pinned stack verbatim');
});

// ── flag ON: the pilot shape ────────────────────────────────────────────────

test('flag on: suffix + six faces with pilot-scoped family names', () => {
  assert.equal(notoPilotRevSuffix(ON), NOTO_PILOT_REV_SUFFIX);
  assert.equal(NOTO_PILOT_REV_SUFFIX, '-notopilot');
  assert.equal(NOTO_PILOT_FACES.length, 6);
  for (const f of NOTO_PILOT_FACES) {
    assert.match(f.family, /^Noto Pilot /, 'families are pilot-scoped so machine fonts cannot shadow them');
  }
  // CJK last — it also carries Latin/punctuation glyphs and must never win
  // a codepoint an earlier face can serve (module banner).
  assert.equal(NOTO_PILOT_FACES[NOTO_PILOT_FACES.length - 1].family, 'Noto Pilot CJK');
});

test('flag on: stack inserts the pilot families AFTER Inter, tail untouched', () => {
  const out = notoPilotStack(REF_FONT_STACK, ON);
  assert.ok(out.startsWith("'Inter', 'Noto Pilot Arabic', "), 'Inter stays FIRST');
  assert.ok(out.endsWith(REF_FONT_STACK.slice("'Inter', ".length)), 'pre-pilot tail is byte-identical');
  for (const f of NOTO_PILOT_FACES) assert.ok(out.includes(`'${f.family}'`), `${f.family} present`);
});

test('flag on: a stack not led by Inter throws (never guess an insertion point)', () => {
  assert.throws(() => notoPilotStack('serif', ON), /expected the font stack to start with/);
});

test('flag on: font-face css embeds present files, warns per missing file', async () => {
  const env = { ...ON, TITAN_NOTO_PILOT_FONTS: '/staged' };
  // Only the Arabic file "exists"; the other five must warn individually.
  const io = {
    existsSync: (p) => p.endsWith('noto_sans_arabic_regular.ttf'),
    readFile: async () => Buffer.from('FONTBYTES'),
  };
  const warnings = [];
  const css = await notoPilotFontFaceCss(env, io, (m) => warnings.push(m));
  assert.ok(css.includes("font-family: 'Noto Pilot Arabic'"), 'present face declared');
  assert.ok(css.includes(`data:font/ttf;base64,${Buffer.from('FONTBYTES').toString('base64')}`), 'payload is a data URI');
  assert.ok(css.includes('font-display: block'), 'no fallback-face first paint');
  assert.equal(warnings.length, 5, 'one LOUD warning per missing face');
  assert.ok(warnings.every((w) => w.includes('keeps the pre-pilot fallback')));
});

test('flag on: enabled without a fonts dir is all-missing (loud), never silent', async () => {
  const warnings = [];
  const css = await notoPilotFontFaceCss(ON, { existsSync: () => true, readFile: async () => Buffer.alloc(0) },
                                         (m) => warnings.push(m));
  assert.equal(css, '', 'nothing to embed');
  assert.equal(warnings.length, NOTO_PILOT_FACES.length, 'every face reported missing');
  assert.ok(warnings[0].includes('TITAN_NOTO_PILOT_FONTS unset'));
});

test('flag on: web css restates BOTH index.html font rules with the pilot stack', async () => {
  const env = { ...ON, TITAN_NOTO_PILOT_FONTS: '/staged' };
  const io = { existsSync: () => true, readFile: async () => Buffer.from('X') };
  const css = await notoPilotWebCss(REF_FONT_STACK, env, io, () => {});
  const stack = notoPilotStack(REF_FONT_STACK, ON);
  // The global html/body rule AND the wpt-stage re-pin — same selectors as
  // index.html so source order (not specificity) decides, and only where
  // the index.html rules already applied.
  assert.ok(css.includes(`html, body { font-family: ${stack}; }`));
  assert.ok(css.includes(`body.wpt-mode, body.wpt-mode #root, body.wpt-composed-mode, body.wpt-composed-mode #root { font-family: ${stack}; }`));
  assert.equal((css.match(/@font-face/g) || []).length, NOTO_PILOT_FACES.length);
});

// ── CANVAS_REV integration (env is read at module LOAD in the consumer) ─────

test('subprocess: TITAN_NOTO_PILOT=1 suffixes CANVAS_REV; unset does not', () => {
  const probe = (env) => execFileSync(process.execPath,
    ['-e', `import(${JSON.stringify(join(__dirname, 'capture-browser-ref.mjs'))}).then(m => console.log(m.CANVAS_REV))`],
    { encoding: 'utf8', env: { ...process.env, TITAN_NOTO_PILOT: '', ...env } }).trim();
  assert.equal(probe({}), 'white-black-ink-font-lh-imgpad-htmlpins');
  assert.equal(probe({ TITAN_NOTO_PILOT: '1' }), 'white-black-ink-font-lh-imgpad-htmlpins-notopilot');
});
