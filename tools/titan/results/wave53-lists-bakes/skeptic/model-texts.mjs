// tools/titan/results/wave53-lists-bakes/skeptic/model-texts.mjs — L1 skeptic: runs modelMarkerTexts (hunk M's string fallback) on counter-suffix's static fixture. Usage: node model-texts.mjs <tree-root> (a tree with seam-1 + the lane's files, tools/wpt and wpt-buckets.json reachable).
import { readFileSync } from 'node:fs';
const T = process.argv[2];
const { extractFixture } = await import(T + '/tools/titan/extract-fixture.mjs');
const { modelMarkerTexts } = await import(T + '/tools/titan/bidi-marker-bake.mjs');
const { fixtureStem } = await import(T + '/tools/titan/safe-name.mjs');
const rel = 'css/css-counter-styles/counter-suffix.html';
const { fixture } = await extractFixture(rel);
const html = readFileSync(T + '/tools/wpt/' + rel, 'utf8');
const cands = [[0,4,0],[0,4,1],[0,5,0],[0,5,1],[0,0,0],[0,2,1],[0,3,0]].map((p) => ({ key: p.join('.'), path: p }));
console.log('stem', fixtureStem(rel), JSON.stringify(modelMarkerTexts(fixture, html, fixtureStem(rel), cands)));
