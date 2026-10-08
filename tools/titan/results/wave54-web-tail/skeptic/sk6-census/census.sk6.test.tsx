// Skeptic (wave 54 L6, sk6) own DOM census: every per-test IR document of the run AND every
// section's combined capture document (out/tmpOutput.json, split per data-capture-id canvas),
// rendered through THIS tree's real ComposedCaptureGallery with the capture URL's flags.
import { it, vi } from 'vitest';
import { renderToStaticMarkup } from 'react-dom/server';
import { createElement } from 'react';
import fs from 'node:fs';
import path from 'node:path';

it('sk6 census', async () => {
  const TREE = process.env.SK6_TREE!; const RUN = process.env.SK6_RUN!; const OUT = process.env.SK6_OUT!;
  (globalThis as any).window = { location: { search: '?wpt=1&wptComposed=1' } };
  vi.resetModules();
  const { ComposedCaptureGallery } = await import(path.join(TREE, 'apps/web-harness/src/ui/ComposedCaptureGallery.tsx'));
  for (const k of ['warn', 'error', 'log', 'info', 'debug'] as const) vi.spyOn(console, k).mockImplementation(() => {});
  const render = (doc: unknown) => { try { return renderToStaticMarkup(createElement(ComposedCaptureGallery, { document: doc })); } catch (e) { return 'THROW ' + (e as Error).message; } };
  const res: Record<string, Record<string, string>> = { perTest: {}, canvas: {} };
  const secDir = path.join(RUN, 'sections');
  for (const sec of fs.readdirSync(secDir).sort()) {
    const irDir = path.join(secDir, sec, 'per-test-ir');
    if (fs.existsSync(irDir)) for (const f of fs.readdirSync(irDir).filter((x) => x.endsWith('.json')).sort())
      res.perTest[`${sec}/${f.replace(/\.json$/, '')}`] = render(JSON.parse(fs.readFileSync(path.join(irDir, f), 'utf8')));
    const comb = path.join(secDir, sec, 'out', 'tmpOutput.json');
    if (!fs.existsSync(comb)) continue;
    const html = render(JSON.parse(fs.readFileSync(comb, 'utf8')));
    const re = /data-capture-id="([^"]*)"/g; const starts: Array<[number, string]> = []; let m;
    while ((m = re.exec(html))) starts.push([m.index, m[1]]);
    starts.forEach(([at, id], i) => { res.canvas[`${sec}/${id}`] = html.slice(at, i + 1 < starts.length ? starts[i + 1][0] : html.length); });
  }
  fs.writeFileSync(OUT, JSON.stringify(res));
}, 1_800_000);
