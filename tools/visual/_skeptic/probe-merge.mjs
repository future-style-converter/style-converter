import puppeteer from 'puppeteer';
const b = await puppeteer.launch({headless:'new', args:['--no-sandbox','--enable-experimental-web-platform-features','--force-device-scale-factor=1']});
const p = await b.newPage();
await p.setViewport({width:400,height:400,deviceScaleFactor:1});

// flex-gap-decorations-002's layout, but with column-over-row so the
// COLUMN rule paints last and its behaviour in the row gap is visible.
const html = (extra) => `<!DOCTYPE html><style>
body{margin:0;background:white}
.c{height:110px;width:110px;display:flex;column-gap:10px;row-gap:10px;
   column-rule-color:red;column-rule-style:solid;column-rule-width:10px;
   row-rule-color:blue;row-rule-style:solid;row-rule-width:10px;
   flex-wrap:wrap; ${extra}}
.i{background:white;width:50px;height:50px}
</style><div class="c"><div class=i></div><div class=i></div><div class=i></div><div class=i></div></div>`;

async function probe(extra, label) {
  await p.setContent(html(extra));
  const px = await p.evaluate(async () => {
    const cv = document.createElement('canvas');
    return null;
  });
  const shot = await p.screenshot({clip:{x:0,y:0,width:120,height:120}});
  const { PNG } = await import('pngjs');
  const png = PNG.sync.read(shot);
  const at = (x,y) => { const i=(png.width*y+x)<<2; return `${png.data[i]},${png.data[i+1]},${png.data[i+2]}`; };
  // Column band x50-60, row band y50-60. Sample INSIDE the crossing
  // square and just above/below it in the column band.
  console.log(label);
  console.log('  column band above crossing (55,25):', at(55,25));
  console.log('  CROSSING square       (55,55):', at(55,55));
  console.log('  row band left of cross(25,55):', at(25,55));
  console.log('  column band below     (55,85):', at(55,85));
}
await probe('', '--- default (row-over-column) ---');
await probe('rule-overlap: column-over-row;', '--- column-over-row ---');
await probe('rule-overlap: column-over-row; column-rule-width: 2px; row-rule-width: 2px;', '--- column-over-row, 2px rules (crossing partly bare) ---');
await b.close();
