#!/usr/bin/env node
// census-f2-uatags.mjs — lane L11: re-derive the brief's "17 post-load tests
// carry a UA-coloured tag" (a[href], button, input, select, textarea, mark)
// from the wave51-fix manifests + corpus sources, plus the `hr` L11 added to
// UA_COLOURED_TAGS. Read-only; prints counts and the test list as JSON.
import { readdirSync, readFileSync, existsSync, writeFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';
const HERE = dirname(fileURLToPath(import.meta.url));
const ROOT = join(HERE, '..', '..', '..', '..');
const RUN = join(ROOT, 'tools', 'titan', 'runs', 'wave51-fix', 'sections');
// The six brief tags (a needs href to be UA-coloured) and the added hr.
const SIX = /<a\s[^>]*href|<(button|input|select|textarea|mark)[\s>/]/i;
const HR = /<hr[\s>/]/i;
const post = [];
for (const sec of readdirSync(RUN)) {
  const m = join(RUN, sec, 'manifest.json');
  if (!existsSync(m)) continue;
  for (const [k, v] of Object.entries(JSON.parse(readFileSync(m, 'utf8')).wpt?.results ?? {})) if (v.postLoadExtracted) post.push(k);
}
const src = (k) => { try { return readFileSync(join(ROOT, 'tools', 'wpt', k), 'utf8'); } catch { return ''; } };
const six = post.filter((k) => SIX.test(src(k)));
const hr = post.filter((k) => HR.test(src(k)));
const out = { postLoadTests: post.length, withSixUaTags: six.length, withHr: hr.length, six, hr };
writeFileSync(join(HERE, 'census-f2-uatags.json'), JSON.stringify(out, null, 1) + '\n');
console.log(JSON.stringify({ postLoadTests: post.length, withSixUaTags: six.length, withHr: hr.length }));
