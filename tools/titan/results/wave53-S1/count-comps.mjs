// S1 census helper: count every component (recursive) of a static extraction, pre vs HEAD tool trees.
const [tree, ...tests] = process.argv.slice(2);
const EF = await import(`${tree}/tools/titan/extract-fixture.mjs`);
const count = (m) => Object.values(m ?? {}).reduce((n, c) => n + 1 + count(c.children), 0);
for (const t of tests) {
  try { const { fixture } = await EF.extractFixture(t); console.log(`${t}\t${count(fixture.components)}`); }
  catch (e) { console.log(`${t}\tERROR ${e.message.slice(0, 120)}`); }
}
