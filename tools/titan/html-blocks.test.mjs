#!/usr/bin/env node
// Pins for tools/titan/html-blocks.mjs (wave 52): the index-based scanner that
// replaced the regex tag / comment filters CodeQL flagged. Two kinds of pin:
//   1. behaviour — the cases the regexes decided, decided the same way;
//   2. byte-equality with the regexes it replaced over EVERY WPT source the
//      gate reads (the 30 sections' tests.list + the reference pages), when
//      the vendored corpus is present (tools/wpt is gitignored; on a CI runner
//      without it that pin skips BY NAME and the behaviour pins still run).
// The three OLD_* regexes below are kept VERBATIM on purpose: they are the
// oracle the equality pin compares against, and nothing else in the tree
// uses them. CodeQL flags them (js/bad-tag-filter,
// js/incomplete-multi-character-sanitization — alerts 31–34 on PR #151); they
// are dismissed as "used in tests", which is what they are. Do not "fix" them:
// an oracle that no longer matches the replaced code proves nothing.
// Run via `node --test tools/titan/html-blocks.test.mjs`.
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { existsSync, readdirSync, readFileSync, statSync } from 'node:fs';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { scriptBlocks, stripBetween, stripScripts } from './html-blocks.mjs';

const REPO = resolve(dirname(fileURLToPath(import.meta.url)), '..', '..');
const WPT = join(REPO, 'tools', 'wpt');
// The regexes that were replaced, verbatim, so equality is against the real thing.
const OLD_STRIP_SCRIPTS = (html) => String(html ?? '').replace(/<script\b[^>]*>[\s\S]*?<\/script>/gi, '');
const OLD_SCRIPT_MATCH = (html) => [...String(html ?? '').matchAll(/<script\b([^>]*)>([\s\S]*?)<\/script>/gi)].map((m) => [m[1], m[2]]);
const OLD_STRIP_COMMENTS = (html) => String(html ?? '').replace(/<!--[\s\S]*?-->/g, '');

test('script blocks: attrs and body, case-insensitive, word boundary after the tag name', () => {
  const html = '<p>a</p><SCRIPT type="module">let x = "</scripts>";</SCRIPT><scripts>not a script</scripts><script>b</script>';
  const blocks = scriptBlocks(html);
  assert.equal(blocks.length, 2);
  assert.equal(blocks[0].attrs, ' type="module"');
  assert.equal(blocks[0].body, 'let x = "</scripts>";');   // `</scripts>` is not a closing tag
  assert.equal(blocks[1].body, 'b');
  assert.equal(stripScripts(html), '<p>a</p><scripts>not a script</scripts>');
});

test('an unclosed script block is not a block, and a tag without ">" stops the scan — regex parity', () => {
  for (const html of ['<script>never closed', '<script type="x" no-gt </script>', 'plain text', '']) {
    assert.deepEqual(scriptBlocks(html).map((b) => [b.attrs, b.body]), OLD_SCRIPT_MATCH(html), JSON.stringify(html));
    assert.equal(stripScripts(html), OLD_STRIP_SCRIPTS(html), JSON.stringify(html));
  }
});

test('offsets survive non-ASCII text whose lower-casing changes length', () => {
  // `İ` (U+0130) lower-cases to TWO code units; a scanner that searched a
  // lower-cased copy returned offsets one past the real ones here.
  const html = 'İstanbul <SCRIPT>x</SCRIPT> tail';
  assert.equal(stripScripts(html), 'İstanbul  tail');
  assert.deepEqual(scriptBlocks(html).map((b) => [b.start, b.end]), [[9, 27]]);
});

test('stripBetween removes closed spans only', () => {
  assert.equal(stripBetween('a<!-- x -->b<!-- y -->c', '<!--', '-->'), 'abc');
  assert.equal(stripBetween('a<!-- open', '<!--', '-->'), 'a<!-- open');       // unclosed stays, as the lazy regex left it
  assert.equal(stripBetween('', '<!--', '-->'), '');
  assert.equal(stripBetween(null, '<!--', '-->'), '');
});

// Every source the gate reads: the 30 sections' tests.list files name the tests;
// their reference pages sit beside them. Walk tools/wpt/css once.
function corpusSources() {
  const root = join(WPT, 'css');
  const out = [];
  const walk = (dir) => { for (const e of readdirSync(dir)) { const p = join(dir, e); const st = statSync(p); if (st.isDirectory()) walk(p); else if (/\.html?$/.test(e)) out.push(p); } };
  walk(root);
  return out;
}

test('byte-equality with the replaced regexes over every corpus source', { skip: !existsSync(join(WPT, 'css')) && 'tools/wpt corpus not present (gitignored; fetch with tools/titan/fetch-wpt.sh)' }, () => {
  const files = corpusSources();
  assert.ok(files.length > 1000, `corpus looks incomplete: ${files.length} html files`);
  let scripts = 0, comments = 0;
  for (const f of files) {
    const html = readFileSync(f, 'utf8');
    assert.equal(stripScripts(html), OLD_STRIP_SCRIPTS(html), f);
    assert.deepEqual(scriptBlocks(html).map((b) => [b.attrs, b.body]), OLD_SCRIPT_MATCH(html), f);
    assert.equal(stripBetween(html, '<!--', '-->'), OLD_STRIP_COMMENTS(html), f);
    if (html.includes('<script')) scripts++;
    if (html.includes('<!--')) comments++;
  }
  // The corpus must actually exercise both scanners, or the equality is vacuous.
  assert.ok(scripts > 100 && comments > 100, `too few carriers: ${scripts} with scripts, ${comments} with comments`);
});
