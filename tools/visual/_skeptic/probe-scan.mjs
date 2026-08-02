import puppeteer from 'puppeteer';
import { PNG } from 'pngjs';
const b = await puppeteer.launch({headless:'new', args:['--no-sandbox','--enable-experimental-web-platform-features','--force-device-scale-factor=1']});
const p = await b.newPage();
await p.setViewport({width:400,height:400,deviceScaleFactor:1});
console.log('UA:', await b.version());

const html = (extra, rw) => `<!DOCTYPE html><style>
body{margin:0;background:white}
.c{height:110px;width:110px;display:flex;column-gap:10px;row-gap:10px;
   column-rule-color:red;column-rule-style:solid;column-rule-width:10px;
   row-rule-color:blue;row-rule-style:solid;row-rule-width:${rw};
   flex-wrap:wrap; ${extra}}
.i{background:white;width:50px;height:50px}
</style><div class="c"><div class=i></div><div class=i></div><div class=i></div><div class=i></div></div>`;

async function scan(extra, rw, label) {
  await p.setContent(html(extra, rw));
  const png = PNG.sync.read(await p.screenshot({clip:{x:0,y:0,width:120,height:120}}));
  const at = (x,y) => { const i=(png.width*y+x)<<2; const d=png.data; return `${d[i]},${d[i+1]},${d[i+2]}`; };
  const col = [];
  for (let y=46; y<=64; y++) col.push(`y${y}:${at(55,y)}`);
  console.log(label, '\n  x=55 vertical scan through the row gap:\n   ', col.join('  '));
}
await scan('', '10px', '--- default, 10px row rule ---');
await scan('rule-overlap: column-over-row;', '2px', '--- column-over-row, 2px row rule ---');
await scan('column-rule-break: intersection;', '2px', '--- column-rule-break:intersection, 2px row rule ---');
await b.close();
