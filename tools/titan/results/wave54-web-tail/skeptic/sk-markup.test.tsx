// Skeptic: dump the composed-gallery markup of chosen per-test docs in THIS tree.
import { it, vi } from 'vitest';
import { renderToStaticMarkup } from 'react-dom/server';
import { createElement } from 'react';
import fs from 'node:fs';
import path from 'node:path';
it('dump', async () => {
  (globalThis as any).window = { location: { search: '?wpt=1&wptComposed=1' } };
  vi.resetModules();
  const { ComposedCaptureGallery } = await import(path.join(process.env.SK_TREE!, 'apps/web-harness/src/ui/ComposedCaptureGallery.tsx'));
  vi.spyOn(console, 'warn').mockImplementation(() => {});
  for (const f of process.env.SK_DOCS!.split(',')) {
    const doc = JSON.parse(fs.readFileSync(f, 'utf8'));
    const html = renderToStaticMarkup(createElement(ComposedCaptureGallery, { document: doc }));
    fs.writeFileSync(path.join(process.env.SK_OUTDIR!, path.basename(f) + '.html'), html);
  }
}, 120000);
