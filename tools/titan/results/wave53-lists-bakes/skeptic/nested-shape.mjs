// tools/titan/results/wave53-lists-bakes/skeptic/nested-shape.mjs — L1 skeptic: prints counter-reset-reversed-nested's static fixture tree (_text, _runs, ::before _text). Usage: node nested-shape.mjs <tree-root> (HEAD export = base; + seam-1 + lane counter-bake = fix).
const T = process.argv[2];
const { extractFixture } = await import(T + '/tools/titan/extract-fixture.mjs');
const { fixture } = await extractFixture('css/css-lists/counter-reset-reversed-nested.html');
const walk = (m, d = 0) => { for (const [k, c] of Object.entries(m ?? {})) {
  console.log('  '.repeat(d) + k, c._tag ?? '', JSON.stringify({ text: c._text, runs: c._runs, before: c._pseudo?.before?._text, props: c.properties }));
  walk(c.children, d + 1); } };
walk(fixture.components);
