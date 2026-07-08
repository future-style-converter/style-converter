#!/usr/bin/env node
// Tier 9 — Fetch real-world CSS samples from major sites for fidelity testing.
//
// Output: testing/real-pages/<site>.css (raw) and testing/real-pages/<site>.json
// (parsed IR per Style-Converter).

import { writeFileSync, mkdirSync, existsSync } from 'node:fs';

const SITES = [
  'https://github.com',
  'https://stripe.com',
  'https://www.apple.com',
  'https://tailwindcss.com',
  'https://developer.mozilla.org',
  'https://en.wikipedia.org/wiki/CSS',
  'https://www.cnn.com',
  'https://twitter.com',
  'https://medium.com',
  'https://www.producthunt.com',
];

const OUT_DIR = 'testing/real-pages';
if (!existsSync(OUT_DIR)) mkdirSync(OUT_DIR, { recursive: true });

const STYLE_RE = /<style[^>]*>([\s\S]*?)<\/style>/gi;
const LINK_RE = /<link[^>]+rel=["']?stylesheet["']?[^>]*href=["']([^"']+)["']/gi;

async function fetchCss(url) {
  const out = [];
  try {
    const res = await fetch(url, { headers: { 'User-Agent': 'Mozilla/5.0 Style-Converter-Audit' } });
    const html = await res.text();
    // Extract <style> blocks
    let m; while ((m = STYLE_RE.exec(html)) !== null) out.push(m[1]);
    // Extract first 10 stylesheets (was 3 — bumped in round 44 after the
    // Tier 9 investigator found Stripe's HDS design tokens
    // (`--hds-color-core-neutral-0` etc.) live in a separate token-only
    // stylesheet that ranked 4th+ in the page's <link rel=stylesheet>
    // order. With cap=3 the token sheet was never fetched, so 245 of
    // Stripe's `var(--hds-…)` refs were unresolvable. Cap=10 is generous
    // enough to catch design-system sheets on any major site we care about
    // without significantly increasing fetch volume.
    const sheets = []; let l;
    while ((l = LINK_RE.exec(html)) !== null && sheets.length < 10) {
      let href = l[1];
      if (href.startsWith('//')) href = 'https:' + href;
      else if (href.startsWith('/')) href = new URL(url).origin + href;
      else if (!href.startsWith('http')) continue;
      sheets.push(href);
    }
    for (const sheet of sheets) {
      try {
        const r = await fetch(sheet);
        out.push(await r.text());
      } catch (e) { out.push(`/* failed: ${sheet} — ${e.message} */`); }
    }
  } catch (e) {
    return `/* failed to fetch ${url}: ${e.message} */`;
  }
  return out.join('\n\n/* ========= */\n\n');
}

const summary = [];
for (const url of SITES) {
  const slug = new URL(url).hostname.replace(/\W+/g, '-');
  console.log(`fetching ${url}`);
  const css = await fetchCss(url);
  const path = `${OUT_DIR}/${slug}.css`;
  writeFileSync(path, css);
  summary.push({ url, slug, bytes: css.length });
  await new Promise(r => setTimeout(r, 500));
}

writeFileSync(`${OUT_DIR}/_summary.json`, JSON.stringify(summary, null, 2));
console.log('\nSummary:');
for (const s of summary) console.log(`  ${s.slug}: ${s.bytes} bytes`);
