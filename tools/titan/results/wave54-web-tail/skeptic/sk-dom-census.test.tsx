// Skeptic (wave 54 L6) independent DOM census: renders, through the REAL
// ComposedCaptureGallery of THIS export tree, (A) every section's COMBINED
// converter output (out/tmpOutput.json — the document the web capture actually
// loaded) and splits the markup per canvas (data-capture-id), and (B) every
// per-test IR document alone. Writes sha1 per canvas / per document.
import { it, vi } from 'vitest';
import { renderToStaticMarkup } from 'react-dom/server';
import { createElement } from 'react';
import { createHash } from 'node:crypto';
import fs from 'node:fs';
import path from 'node:path';

const RUNDIR = process.env.SK_RUNDIR!;
const OUT = process.env.SK_OUT!;
const TREE = process.env.SK_TREE!;
const sha = (s: string) => createHash('sha1').update(s).digest('hex');

it('census', async () => {
  (globalThis as any).window = { location: { search: '?wpt=1&wptComposed=1' } };
  vi.resetModules();
  const { ComposedCaptureGallery } = await import(path.join(TREE, 'apps/web-harness/src/ui/ComposedCaptureGallery.tsx'));
  vi.spyOn(console, 'warn').mockImplementation(() => {});
  vi.spyOn(console, 'error').mockImplementation(() => {});
  vi.spyOn(console, 'log').mockImplementation(() => {});
  const res: any = { section: {}, perTest: {}, canvasCount: {} };
  for (const sec of fs.readdirSync(path.join(RUNDIR, 'sections')).sort()) {
    const comb = path.join(RUNDIR, 'sections', sec, 'out/tmpOutput.json');
    if (fs.existsSync(comb)) {
      const doc = JSON.parse(fs.readFileSync(comb, 'utf8'));
      let html = '';
      try { html = renderToStaticMarkup(createElement(ComposedCaptureGallery, { document: doc })); }
      catch (e) { html = 'THROW ' + (e as Error).message; }
      // split at each canvas start
      const re = /data-capture-id="([^"]*)"/g; let m; const starts: Array<[number, string]> = [];
      while ((m = re.exec(html))) starts.push([m.index, m[1]]);
      res.canvasCount[sec] = starts.length;
      for (let i = 0; i < starts.length; i++) {
        const seg = html.slice(starts[i][0], i + 1 < starts.length ? starts[i + 1][0] : html.length);
        res.section[`${sec}/${starts[i][1]}`] = sha(seg);
      }
      if (starts.length === 0) res.section[`${sec}/__whole`] = sha(html);
    }
    const irDir = path.join(RUNDIR, 'sections', sec, 'per-test-ir');
    if (!fs.existsSync(irDir)) continue;
    for (const f of fs.readdirSync(irDir).filter((x) => x.endsWith('.json')).sort()) {
      const doc = JSON.parse(fs.readFileSync(path.join(irDir, f), 'utf8'));
      let html = '';
      try { html = renderToStaticMarkup(createElement(ComposedCaptureGallery, { document: doc })); }
      catch (e) { html = 'THROW ' + (e as Error).message; }
      res.perTest[`${sec}/${f}`] = sha(html);
    }
  }
  fs.writeFileSync(OUT, JSON.stringify(res));
}, 900_000);
