// review-crops.mjs — wave 51 PR 3: build the PNG-review sheets for the label-chrome
// baseline refresh (design-record.md §5 step 2: "LOOK at every changed PNG"). For
// every stem of one archived measure run it writes ONE sheet per platform:
//   [ committed twin | fresh capture | diff mask ]  at 3× nearest-neighbour zoom,
// where the diff mask paints changed pixels red on the fresh capture, the expected
// label glyph set P green where it is NOT present, and leaves everything else as the
// fresh capture — so a reviewer sees at a glance whether every change is (i) the new
// band label and (ii) the old in-box footprint (label ink → underlying paint, plus
// that label's own shadow / blur / transform), and nothing else.
// usage: node review-crops.mjs <archived-run-dir> [--zoom 3] [--only stemA,stemB]
import { readFileSync, readdirSync, existsSync, writeFileSync, mkdirSync } from 'node:fs';
import path from 'node:path';
import { createRequire } from 'node:module';
const ROOT = path.resolve(path.dirname(new URL(import.meta.url).pathname), '../../../..');
const require = createRequire(path.join(ROOT, 'package.json'));
const { PNG } = require('pngjs');
const args = process.argv.slice(2);
const RUN = path.resolve(ROOT, args[0]);                                             // e.g. tools/titan/runs/wave51-labels/visual-test
const ZOOM = Number((args.indexOf('--zoom') >= 0 && args[args.indexOf('--zoom') + 1]) || 3);
const ONLY = args.indexOf('--only') >= 0 ? new Set(args[args.indexOf('--only') + 1].split(',')) : null;
const BASELINE = path.join(ROOT, 'tools/visual/baseline');
const OUTDIR = path.join(RUN, 'review'); mkdirSync(OUTDIR, { recursive: true });
const FONT = JSON.parse(readFileSync(path.join(ROOT, 'tools/visual/block-font.json'), 'utf8'));
const PLATFORMS = ['web', 'iOS', 'Android'];
const ORIGIN = { x: 8, y: 6 }, EDGE = 8;
const normalize = (s) => Array.from(s.toUpperCase()).map((ch) => (FONT.glyphs[ch] ? ch : '-')).join('');
const truncatedCount = (len, w) => { const b = w - ORIGIN.x - EDGE; return b <= 0 ? 0 : Math.min(len, Math.floor(b / FONT.advance)); };
function glyphPixels(name, w) { const chars = normalize(name.replace(/_/g, ' ')), n = truncatedCount(chars.length, w), P = new Set(); for (let i = 0; i < n; i++) { const rows = FONT.glyphs[chars[i]]; for (let r = 0; r < rows.length; r++) for (let c = 0; c < rows[r].length; c++) if (rows[r][c] === '1') P.add(`${ORIGIN.x + i * FONT.advance + c},${ORIGIN.y + r}`); } return P; }
const load = (f) => (existsSync(f) ? PNG.sync.read(readFileSync(f)) : null);
const at = (p, x, y) => { const i = (y * p.width + x) * 4; return [p.data[i], p.data[i + 1], p.data[i + 2]]; };
function blit(dst, src, ox, oy, colourAt) {                                          // nearest-neighbour zoomed copy with optional recolour
  for (let y = 0; y < src.height; y++) for (let x = 0; x < src.width; x++) {
    const c = colourAt ? colourAt(x, y) : at(src, x, y);
    for (let dy = 0; dy < ZOOM; dy++) for (let dx = 0; dx < ZOOM; dx++) { const i = ((oy + y * ZOOM + dy) * dst.width + (ox + x * ZOOM + dx)) * 4; dst.data[i] = c[0]; dst.data[i + 1] = c[1]; dst.data[i + 2] = c[2]; dst.data[i + 3] = 255; }
  }
}
const stems = readdirSync(path.join(RUN, 'images', 'web')).filter((f) => f.endsWith('.png')).sort();
let sheets = 0;
for (const file of stems) {
  const stem = file.replace(/\.png$/, ''); if (ONLY && !ONLY.has(stem)) continue;
  const P = glyphPixels(stem.replace(/^\d+_/, ''), 390);
  for (const p of PLATFORMS) {
    const fresh = load(path.join(RUN, 'images', p, file)), old = load(path.join(BASELINE, `${p}__${file}`));
    if (!fresh) continue;
    const w = Math.max(fresh.width, old ? old.width : 0), h = Math.max(fresh.height, old ? old.height : 0);
    const GAP = 4;                                                                   // white gutter between panes
    const sheet = new PNG({ width: (3 * w + 2 * GAP) * ZOOM, height: h * ZOOM });
    sheet.data.fill(255);
    if (old) blit(sheet, old, 0, 0);
    blit(sheet, fresh, (w + GAP) * ZOOM, 0);
    blit(sheet, fresh, (2 * w + 2 * GAP) * ZOOM, 0, (x, y) => {                      // the diff mask pane
      const a = at(fresh, x, y);
      const changed = old && x < old.width && y < old.height ? (() => { const b = at(old, x, y); return a[0] !== b[0] || a[1] !== b[1] || a[2] !== b[2]; })() : true;
      if (P.has(`${x},${y}`)) return changed ? [0, 200, 0] : [255, 0, 255];         // expected label pixel: green if it appeared, magenta if it did NOT change (missing label)
      return changed ? [255, 0, 0] : a;                                               // any other change: red
    });
    writeFileSync(path.join(OUTDIR, `${p}__${stem}.png`), PNG.sync.write(sheet)); sheets++;
  }
}
console.log(`${sheets} sheets → ${OUTDIR} (panes: committed | fresh | diff; red = changed outside P, green = label pixel appeared, magenta = expected label pixel unchanged)`);
