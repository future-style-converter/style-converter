import puppeteer from 'puppeteer';
import { PNG } from 'pngjs';
const b = await puppeteer.launch({headless:'new', args:['--no-sandbox','--enable-experimental-web-platform-features','--force-device-scale-factor=1']});
const p = await b.newPage();
await p.setViewport({width:400,height:400,deviceScaleFactor:1});
// DEFAULT rule-overlap (row-over-column). Both rules 2px, so the row
// rule cannot mask the column band inside the row gap.
await p.setContent(`<!DOCTYPE html><style>
body{margin:0;background:white}
.c{height:110px;width:110px;display:flex;column-gap:10px;row-gap:10px;
   column-rule-color:red;column-rule-style:solid;column-rule-width:2px;
   row-rule-color:blue;row-rule-style:solid;row-rule-width:2px;
   flex-wrap:wrap}
.i{background:white;width:50px;height:50px}
</style><div class="c"><div class=i></div><div class=i></div><div class=i></div><div class=i></div></div>`);
const png = PNG.sync.read(await p.screenshot({clip:{x:0,y:0,width:120,height:120}}));
const at = (x,y)=>{const i=(png.width*y+x)<<2;const d=png.data;return `${d[i]},${d[i+1]},${d[i+2]}`};
// 2px column rule is centred in the 50-60 gap → x54-56.
const out=[]; for(let y=46;y<=64;y++) out.push(`y${y}:${at(54,y)}`);
console.log('DEFAULT overlap, both rules 2px — x=54 scan:\n ', out.join('  '));
const out2=[]; for(let x=46;x<=64;x++) out2.push(`x${x}:${at(x,54)}`);
console.log('DEFAULT overlap, both rules 2px — y=54 scan:\n ', out2.join('  '));
await b.close();
