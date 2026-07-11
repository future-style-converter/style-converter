// ssr-smoke.mjs — standalone real-app smoke for @style-converter/web
// (issue #41 VERIFY step): prove the PACKAGE renders a token-themed IR
// document to static HTML via react-dom/server renderToString, with no
// harness code, no vite dev server, no browser.
//
//   node runtimes/web/examples/ssr-smoke.mjs
//
// The package ships TypeScript source (exports "." → ./src/index.ts),
// so this script bundles the example entry with rolldown — the same
// bundler vite 8 uses, already present in the workspace root — into a
// temp file, imports it, renders, and asserts the load-bearing output.
// Exit 0 = smoke passed; non-zero + message otherwise.

import { rolldown } from 'rolldown';
import { mkdtemp, rm, writeFile } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { join, dirname } from 'node:path';
import { fileURLToPath, pathToFileURL } from 'node:url';

// Resolve everything relative to this script so cwd never matters.
const HERE = dirname(fileURLToPath(import.meta.url));

// Bundle the example app (entry imports '@style-converter/web', which
// node-resolves through the workspace symlink exactly like an installed
// dependency would in a consumer repo).
const build = await rolldown({
  input: join(HERE, 'ssr-app.ts'),
  // Bundle react + react-dom/server in too — the output must run with
  // ZERO ambient resolution context (the "standalone" part of the smoke).
  platform: 'node',
  logLevel: 'silent',
});
const { output } = await build.generate({ format: 'esm' });
await build.close();

// Write the bundle to a throwaway dir and import it as a real module.
const dir = await mkdtemp(join(tmpdir(), 'sc-web-ssr-smoke-'));
const bundlePath = join(dir, 'bundle.mjs');
await writeFile(bundlePath, output[0].code);
const { render } = await import(pathToFileURL(bundlePath).href);
const { page, html, css } = render();
await rm(dir, { recursive: true, force: true });

// Assertions — each one is a distinct package capability:
const checks = [
  // Slot composition (Mode A): the CTA nested inside the theme root.
  ['slot composition', html.indexOf('data-component-id="theme"') !== -1
    && html.indexOf('data-component-id="theme"') < html.indexOf('data-component-id="cta"')],
  // Token definitions land inline on the defining element.
  ['token definitions', html.includes('--brand:#e74c3c') && html.includes('--radius:8px')],
  // Token consumption via the var() pass-through.
  ['token consumption', html.includes('background-color:var(--brand)')],
  // Trusted sourceTag mapping: a REAL <button> element.
  ['sourceTag button', /<button[^>]*data-component-id="cta"/.test(html)],
  // Text content as a bare node (no placeholder machinery).
  ['text content', html.includes('Buy now')],
  // NO harness calibration: none of the capture defaults may appear.
  ['no harness defaults', !/fit-content|min-width:50px|min-height:30px/.test(html)],
  // Stylesheet string: wave-8 keyframes + spec-06 hover rule.
  ['keyframes rule', css.includes('@keyframes pulse')],
  ['hover rule', css.includes('.sc-cta:hover')],
  // The assembled page carries both halves.
  ['page assembly', page.includes('<style>') && page.includes('</body>')],
];

// Report and exit. Any failure prints the rendered output for triage.
const failed = checks.filter(([, ok]) => !ok);
for (const [name, ok] of checks) console.log(`${ok ? 'PASS' : 'FAIL'}  ${name}`);
if (failed.length > 0) {
  console.error('\n--- rendered html ---\n' + html + '\n--- stylesheet ---\n' + css);
  process.exit(1);
}
console.log('\nssr-smoke: all checks passed — package renders standalone.');
