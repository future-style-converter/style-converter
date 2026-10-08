// RootBackgroundUniformity.ts — wave-53 lane L3 (item A): the F2 "uniform BY
// CONSTRUCTION" predicates of the root canvas background, split out of
// RootBackgroundPropagation.ts at the wave-53 fix pass (PLAN §0 size rule;
// bodies moved verbatim). A stack may cover the 16-px capture frame only when
// every layer paints one sRGBA AND repeats on both axes
// (capture-browser-ref.mjs padColorFor :728 fills the frame with the ring
// colour only for a uniform ring). Twins: Compose
// background/RootBackgroundUniformity.kt, SwiftUI
// StyleEngine/background/RootBackgroundUniformity.swift.

/** Minimal IR property shape (decoupled from IRModels, like the extractors). */
export interface IRPropertyLike { type: string; data?: unknown }

/** F2 for a whole stack: every layer one colour and every layer repeating on both axes. */
export function stackIsUniform(layers: unknown[], props: IRPropertyLike[]): boolean {
  return layers.every(layerIsUniform) && repeatsBothAxes(props);          // no gap, no second colour
}

/** True for a `none` layer (bare string or `{type:'none'}`) — it paints nothing. */
export function isNoneLayer(l: unknown): boolean {
  return l === 'none' || (typeof l === 'object' && l !== null && (l as { type?: unknown }).type === 'none');  // both shapes
}

/** One stop colour as a comparable sRGBA key, or null when it is not static sRGB. */
function stopKey(stop: unknown): string | null | undefined {
  const c = (stop as { color?: unknown } | null)?.color;                  // the stop's colour leaf
  if (c === undefined || c === null) return undefined;                    // a hint / shape word: no colour
  const s = (c as { srgb?: Record<string, unknown> }).srgb;               // the reader's normalized sRGB
  if (!s || typeof s.r !== 'number') return null;                         // currentColor / var(): unknown
  return `${s.r},${s.g},${s.b},${typeof s.a === 'number' ? s.a : 1}`;     // alpha defaults to opaque
}

/** css-images-3 §3: a gradient whose every stop shares one sRGBA paints that colour everywhere. */
function gradientIsUniform(l: { stops?: unknown }): boolean {
  if (!Array.isArray(l.stops)) return false;                              // malformed → not uniform
  const keys = l.stops.map(stopKey).filter((k) => k !== undefined);       // drop colour-less entries
  return keys.length > 0 && keys.every((k) => k !== null && k === keys[0]);   // one static sRGBA
}

/** Decode a `data:` URI's payload bytes (base64 or percent-encoded), or null. */
function dataUriBytes(url: string): Uint8Array | null {
  const comma = url.indexOf(',');                                         // RFC 2397: header,payload
  if (!url.startsWith('data:') || comma < 0) return null;                 // not a data URI
  const header = url.slice(5, comma); const body = url.slice(comma + 1);  // mediatype[;base64] , payload
  try {                                                                   // atob throws on bad base64
    if (/;base64$/i.test(header)) return Uint8Array.from(atob(body), (ch) => ch.charCodeAt(0));
    // Percent-encoding: decode %XX per byte (a binary PNG is not valid UTF-8).
    const out: number[] = [];                                             // the raw bytes
    for (let i = 0; i < body.length; i++) {                               // %XX → one byte, else verbatim
      if (body[i] === '%' && i + 2 < body.length) { out.push(parseInt(body.slice(i + 1, i + 3), 16)); i += 2; }
      else out.push(body.charCodeAt(i) & 0xff);                         // a literal byte
    }
    return Uint8Array.from(out);                                          // the raw PNG bytes
  } catch { return null; }                                                // bad base64 → unknown
}

/** PNG (ISO 15948 §5.2/§11.2.2): signature, then IHDR width/height — true iff 1×1. */
export function dataPngIs1x1(url: string): boolean {
  if (!/^data:image\/png[;,]/i.test(url)) return false;                   // PNG data URIs only
  const b = dataUriBytes(url);                                            // RFC 2397 payload
  if (!b || b.length < 24) return false;                                  // no full IHDR
  const sig = [0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a];          // the PNG signature
  if (!sig.every((v, i) => b[i] === v)) return false;                     // not a PNG
  if (String.fromCharCode(b[12], b[13], b[14], b[15]) !== 'IHDR') return false;   // first chunk = IHDR
  const be32 = (o: number) => ((b[o] << 24) | (b[o + 1] << 16) | (b[o + 2] << 8) | b[o + 3]) >>> 0;  // big-endian
  return be32(16) === 1 && be32(20) === 1;                                // width, height
}

/** One layer paints a single colour across its tile (F2's per-layer test). */
function layerIsUniform(l: unknown): boolean {
  if (isNoneLayer(l)) return true;                                        // transparent everywhere
  if (typeof l !== 'object' || l === null) return false;                  // bare URL string: unknown pixels
  const o = l as { type?: unknown; url?: unknown; stops?: unknown };                    // url layer or gradient layer
  if (typeof o.url === 'string') return dataPngIs1x1(o.url);              // a 1×1 raster tile
  return typeof o.type === 'string' && /gradient$/.test(o.type) && gradientIsUniform(o);   // plain or repeating
}

/** §3.7: one raw repeat entry is `repeat` on both axes (string tokens or an {x,y} pair). */
function entryRepeatsBoth(e: unknown): boolean {
  const isRepeat = (v: unknown) => typeof v === 'string' && v.toLowerCase() === 'repeat';   // one axis keyword
  if (typeof e === 'string') return e.trim().split(/\s+/).every(isRepeat);  // 'repeat' / 'repeat repeat'
  const o = e as { x?: unknown; y?: unknown } | null;                     // any other keyword: a gap or one tile
  return !!o && typeof o === 'object' && isRepeat(o.x) && isRepeat(o.y);  // axis-pair shape
}

/** §3.7: every layer repeats on BOTH axes (absent leaf = initial `repeat`). */
function repeatsBothAxes(props: IRPropertyLike[]): boolean {
  const d = [...props].reverse().find((p) => p.type === 'BackgroundRepeat')?.data;   // e.g. ['no-repeat']
  const list = Array.isArray(d) ? d : d === undefined ? [] : [d];         // scalar wrap
  return list.every(entryRepeatsBoth);                                    // [] → initial repeat
}
