import puppeteer from 'puppeteer';
const b = await puppeteer.launch({headless:'new', args:['--no-sandbox','--enable-experimental-web-platform-features']});
const p = await b.newPage();
await p.setContent(`<div id=x></div>`);
const r = await p.evaluate(() => {
  const el = document.getElementById('x');
  const cs = getComputedStyle(el);
  const out = {};
  for (const prop of ['column-rule-break','row-rule-break','rule-overlap','column-rule-inset','row-rule-color','row-rule-style','row-rule-width']) {
    out[prop] = { initial: cs.getPropertyValue(prop), supports: {} };
  }
  const trials = {
    'column-rule-break': ['none','normal','intersection','spanning-item'],
    'rule-overlap': ['row-over-column','column-over-row'],
    'column-rule-inset': ['-2px','10px','-100%'],
    'row-rule-width': ['thin','medium','thick','10px'],
  };
  for (const [prop, vals] of Object.entries(trials)) {
    out[prop] = out[prop] || {supports:{}};
    out[prop].supports = {};
    for (const v of vals) out[prop].supports[v] = CSS.supports(prop, v);
  }
  return out;
});
console.log(JSON.stringify(r,null,1));
await b.close();
