//
// tools/titan/woff-to-ttf.mjs — wave-42 lane W9: the WOFF→TTF HOST HOP.
//
// A HOST-SIDE pre-pass for the @font-face asset channel (wave-35 lane B2,
// feed-lib.mjs's hop banner): before the ANDROID feeder delivers a document's
// `fontFaces[].src` files, every WOFF1 among them is repackaged to the raw
// sfnt (TTF/OTF) it wraps, written as a corpus SIBLING, and the ANDROID copy
// of the wire is re-pointed at that sibling. Web and iOS are not touched —
// browsers parse WOFF natively, and CoreText accepts WOFF1 too (MEASURED,
// wave-35 device gate: the same LinLibertine face moved iOS ink
// 0.665% → 0.488%, see DocumentFontRegistry.kt's format-table comment).
//
// ── Why this hop exists ─────────────────────────────────────────────────────
//
// `android.graphics.Typeface.createFromFile` cannot parse a WOFF container —
// worse, it does not even FAIL: it answers the DEFAULT typeface, so the
// capture silently shapes from the wrong outlines (the wave-35 device gate
// measured 0 differing pixels vs a no-face control). The Compose
// DocumentFontRegistry therefore DECLINES every `.woff` by name — an honest
// decline whose own comment names this file as the fix: "Closing the gap for
// real means transcoding WOFF → TTF in the delivery hop". Wave-41 lane T2
// then measured the blast radius: after the asset-delivery fix, ALL 48
// wave-41 `fontFaces[].src` values in the corpus are `.woff`, so Android
// renders every font-dependent test in its bundled fallback face while the
// browser ref shapes with the author's outlines.
//
// ── Why a pure-node transcoder is enough ────────────────────────────────────
//
// WOFF 1.0 (https://www.w3.org/TR/WOFF/) is NOT a font format — it is a
// per-table zlib wrapper around the original sfnt: a 44-byte header, a
// 20-byte-per-table directory carrying each table's original length and
// checksum, and the table payloads either stored verbatim or zlib-compressed.
// Reconstructing the original font is: inflate each table (node's core
// `zlib.inflateSync` — no new dependency), rebuild the 12-byte sfnt header +
// 16-byte-per-table directory with recomputed offsets, and recompute the
// `head` table's checkSumAdjustment for the new layout (WOFF §"Converting a
// WOFF file back to an sfnt font file" prescribes exactly this). The transcode
// is LOSSLESS: every table's bytes are the original table's bytes, so — unlike
// svg-preraster.mjs's one-size raster — no glyph, metric or shaping behaviour
// can differ from what the original font file would have produced. That is why
// this hop could default ON with no A/B to justify it, where the lossy
// pre-raster defaulted OFF for three waves and only flipped ON in wave 44 once
// a measured A/B finally came out ahead. Both hops default ON today; only one
// of them had to prove it.
//
// WOFF2 is REJECTED LOUDLY: it is a different format (Brotli entropy coding
// plus a destructive glyf/loca transform that must be re-derived, not just
// inflated) and reimplementing it here would be a font engine, not a hop.
// A `.woff2` src keeps riding the wire and the runtime's own format decline
// fires, exactly as before this module existed.
//
// ── The naming contract ─────────────────────────────────────────────────────
//
// `<path>.woff` → `<path>.woff.ttf` (TrueType flavor) or `<path>.woff.otf`
// (CFF 'OTTO' flavor): the extension is APPENDED, never substituted — the
// same two load-bearing reasons as svg-preraster.mjs's `.svg` → `.svg.png`:
//   * the original stem stays legible in every log, on-device path and adb
//     listing, so an investigator reading `X.woff.otf` knows instantly that
//     it is a host transcode and of what;
//   * substitution (`X.ttf`) could COLLIDE with a real sibling the corpus
//     already ships and silently deliver the wrong font.
// The extension is chosen by the WOFF header's `flavor` field (the wrapped
// sfnt's own version tag) because the Compose DocumentFontRegistry gates by
// file extension (ANDROID_LOADABLE_EXTENSIONS = ttf/otf/ttc) and an honest
// name must state the real container — both flavors are admitted there, so
// the choice never gates delivery, only truthfulness.
//
// ── Where the rewrite lands, and what it must never touch ───────────────────
//
// The `fontFaces[].src` rewrite is applied to the ANDROID feeder's IN-MEMORY
// copy of the document only (the same discipline, via the same pushableFixture
// seam, as the svg pre-raster rewrite). The per-test IR on disk, the section
// fixture, the converter output, the web bundle and the iOS feeder's copy all
// keep the real `.woff` — this is an Android-side stand-in, not a corpus edit,
// and the other two platforms' scores must stay byte-identical. No Kotlin
// changes are needed on the runtime side: DocumentFontRegistry resolves
// `File(fontsDir, face.src)` verbatim, so the rewritten src ending `.ttf` /
// `.otf` passes its extension gate and loads through the ordinary path.
//

import { inflateSync } from 'node:zlib';
import { promises as fs } from 'node:fs';
import { existsSync } from 'node:fs';
import { dirname, resolve } from 'node:path';

// ── Container signatures (WOFF §3, WOFF2 §4.1) ──────────────────────────────

/** 'wOFF' — the WOFF 1.0 signature this module repackages. */
export const WOFF1_SIGNATURE = 0x774f4646;
/** 'wOF2' — the WOFF 2.0 signature this module REJECTS (Brotli + glyf
 *  transform; see the header banner for why that is out of scope). */
export const WOFF2_SIGNATURE = 0x774f3232;

/** The sfnt flavors this transcoder will emit, mapped to the HONEST file
 *  extension for the rebuilt container (naming contract above). A CLOSED
 *  table on purpose: an unknown flavor means the wrapped payload is not a
 *  font Android could parse anyway, so declining loudly beats inventing a
 *  name for it.
 *    0x00010000 — OpenType with TrueType outlines (OT spec "sfntVersion")
 *    0x74727565 — 'true', the legacy Apple TrueType tag (same glyf outlines)
 *    0x4f54544f — 'OTTO', OpenType with CFF outlines                       */
const FLAVOR_EXTENSIONS = new Map([
  [0x00010000, 'ttf'],
  [0x74727565, 'ttf'],
  [0x4f54544f, 'otf'],
]);

// ── The sfnt checksum (OpenType spec §"Calculating checksums") ──────────────

/** Sum a buffer as big-endian uint32s, zero-padding the tail to a 4-byte
 *  boundary, modulo 2^32. This is the ONE checksum rule the whole sfnt format
 *  uses — for per-table directory checksums and for the whole-file sum that
 *  `head.checkSumAdjustment` balances to 0xB1B0AFBA. */
export function sfntChecksum(buf) {
  let sum = 0;
  const whole = buf.length & ~3; // the full 4-byte words
  for (let i = 0; i < whole; i += 4) sum = (sum + buf.readUInt32BE(i)) >>> 0;
  if (whole < buf.length) {
    // Tail bytes are treated as if the buffer were zero-padded (OT spec).
    let last = 0;
    for (let i = whole; i < whole + 4; i++) {
      last = (last << 8) | (i < buf.length ? buf[i] : 0);
    }
    sum = (sum + (last >>> 0)) >>> 0;
  }
  return sum >>> 0;
}

/** The magic constant the whole-file checksum must total after adjustment —
 *  OpenType `head` spec: checkSumAdjustment = 0xB1B0AFBA − (file sum with the
 *  adjustment field zeroed), so the adjusted file sums to exactly this. */
export const SFNT_CHECKSUM_MAGIC = 0xb1b0afba;

/** Byte offset of checkSumAdjustment WITHIN the head table (after the 4-byte
 *  version and 4-byte fontRevision — OpenType `head` table layout). */
const HEAD_ADJUSTMENT_OFFSET = 8;

// ── The transcoder ──────────────────────────────────────────────────────────

/**
 * Repackage one WOFF 1.0 payload into the raw sfnt (TTF/OTF) it wraps.
 *
 * Throws on anything that is not a well-formed WOFF1 — and the WOFF2 throw is
 * deliberately its own, LOUD message so a `.woff2` in a future corpus reads
 * as the known scope limit rather than as corruption. A throw is always a
 * per-file DECLINE at the call sites: the src keeps riding the wire and the
 * runtime's own format decline fires, exactly the pre-hop behaviour.
 *
 * @param {Buffer} woff the WOFF file's bytes
 * @returns {{ font: Buffer, extension: string, flavor: number,
 *             numTables: number, checksumMismatches: string[],
 *             totalSfntSizeMatches: boolean }}
 *   font — the rebuilt sfnt; extension — 'ttf'/'otf' per the flavor (naming
 *   contract); checksumMismatches — tags whose WOFF-declared origChecksum
 *   does not match the inflated bytes (reported, not fatal: the directory
 *   carries the ORIGINAL font's declared values verbatim, and most platform
 *   parsers ignore them); totalSfntSizeMatches — whether the WOFF header's
 *   own totalSfntSize equals the rebuilt size (a WOFF §3 consistency check;
 *   informational for the same reason).
 */
export function woffToTtf(woff) {
  // ── Header (WOFF §3: 44 bytes, all fields big-endian) ────────────────────
  if (!Buffer.isBuffer(woff) || woff.length < 44) {
    throw new Error('not a WOFF container: shorter than the 44-byte WOFF header');
  }
  const signature = woff.readUInt32BE(0);
  if (signature === WOFF2_SIGNATURE) {
    // The LOUD scope-limit rejection the header banner promises: WOFF2 needs
    // Brotli plus the glyf/loca transform inversion — a font engine, not a
    // zlib hop. Never fall through to "corrupt file" for this case.
    throw new Error('WOFF2 container ("wOF2") — this transcoder handles WOFF1 only; ' +
      'WOFF2 needs Brotli decoding plus the glyf/loca transform inversion, which is out of scope');
  }
  if (signature !== WOFF1_SIGNATURE) {
    throw new Error(`not a WOFF container: signature 0x${signature.toString(16).padStart(8, '0')} is not 'wOFF'`);
  }
  const flavor = woff.readUInt32BE(4);         // the wrapped sfnt's version tag
  const numTables = woff.readUInt16BE(12);     // font table count
  const reserved = woff.readUInt16BE(14);      // WOFF §3: MUST be zero
  const totalSfntSize = woff.readUInt32BE(16); // declared rebuilt size (checked below)
  // WOFF §3 makes `reserved` a MUST-be-zero and instructs conforming user
  // agents to reject otherwise — honoring that here keeps this transcoder
  // from "fixing" files a browser (and the ref capture) would refuse.
  if (reserved !== 0) {
    throw new Error(`malformed WOFF: reserved header field is ${reserved}, spec requires 0`);
  }
  const extension = FLAVOR_EXTENSIONS.get(flavor);
  if (!extension) {
    // Closed flavor table (see FLAVOR_EXTENSIONS): an unknown tag means the
    // payload is not an sfnt Android could load, so decline it by name.
    throw new Error(`unsupported sfnt flavor 0x${flavor.toString(16).padStart(8, '0')} — not TrueType (0x00010000/'true') or CFF ('OTTO')`);
  }
  if (numTables === 0) {
    throw new Error('malformed WOFF: zero font tables');
  }
  // ── Table directory (WOFF §4: 20 bytes per entry, after the header) ──────
  const dirEnd = 44 + numTables * 20;
  if (woff.length < dirEnd) {
    throw new Error(`malformed WOFF: directory for ${numTables} tables overruns the ${woff.length}-byte file`);
  }
  const entries = [];
  for (let i = 0; i < numTables; i++) {
    const at = 44 + i * 20;
    entries.push({
      tag: woff.toString('latin1', at, at + 4),   // 4-char table tag, e.g. 'head'
      offset: woff.readUInt32BE(at + 4),          // payload offset in the WOFF
      compLength: woff.readUInt32BE(at + 8),      // stored (possibly compressed) length
      origLength: woff.readUInt32BE(at + 12),     // the original table's length
      origChecksum: woff.readUInt32BE(at + 16),   // the original directory checksum
    });
  }
  // ── Inflate every table back to its original bytes (WOFF §5) ─────────────
  const checksumMismatches = [];
  for (const e of entries) {
    // Bounds first: a directory pointing outside the file is corruption, and
    // Buffer.subarray would silently truncate rather than throw.
    if (e.offset + e.compLength > woff.length) {
      throw new Error(`malformed WOFF: table '${e.tag}' data (${e.offset}+${e.compLength}) overruns the ${woff.length}-byte file`);
    }
    // WOFF §5: compLength > origLength is invalid (compression MUST be
    // omitted when it does not help), so equal means STORED verbatim and
    // less means one zlib stream covering exactly this table.
    if (e.compLength > e.origLength) {
      throw new Error(`malformed WOFF: table '${e.tag}' compressed length ${e.compLength} exceeds original length ${e.origLength}`);
    }
    const raw = woff.subarray(e.offset, e.offset + e.compLength);
    if (e.compLength === e.origLength) {
      e.data = Buffer.from(raw); // stored table — copied so the output owns its bytes
    } else {
      let inflated;
      try {
        inflated = inflateSync(raw); // node core zlib — the whole reason no dependency is needed
      } catch (err) {
        throw new Error(`malformed WOFF: table '${e.tag}' failed zlib inflate (${err.message})`);
      }
      if (inflated.length !== e.origLength) {
        throw new Error(`malformed WOFF: table '${e.tag}' inflated to ${inflated.length} bytes, directory declared ${e.origLength}`);
      }
      e.data = inflated;
    }
    // VERIFY the declared checksum against the recovered bytes — for 'head'
    // with its checkSumAdjustment field zeroed first, because the OT spec
    // defines head's directory checksum over the zeroed field. A mismatch is
    // reported, never fatal: the directory below carries the DECLARED value
    // verbatim (faithful reconstruction), and platform parsers ignore it.
    let checkable = e.data;
    if (e.tag === 'head' && e.data.length >= HEAD_ADJUSTMENT_OFFSET + 4) {
      checkable = Buffer.from(e.data);
      checkable.writeUInt32BE(0, HEAD_ADJUSTMENT_OFFSET);
    }
    if (sfntChecksum(checkable) !== e.origChecksum) checksumMismatches.push(e.tag);
  }
  // ── Rebuild the sfnt (OT spec §"Organization of an OpenType font") ────────
  // Directory order is kept AS-IS from the WOFF: WOFF §4 requires its
  // directory sorted by tag, and the sfnt directory must be tag-sorted too,
  // so preserving order preserves conformance without re-sorting.
  const headerSize = 12 + numTables * 16; // sfnt header + 16-byte directory records
  let offset = headerSize;
  for (const e of entries) {
    e.newOffset = offset;
    // OT spec: each table is long-aligned; the pad bytes are zeros and are
    // covered by the FILE checksum but not by the table's own.
    offset += (e.origLength + 3) & ~3;
  }
  const font = Buffer.alloc(offset); // zero-filled ⇒ the inter-table pads are already correct
  font.writeUInt32BE(flavor, 0);     // sfntVersion (4 bytes, offsets 0–3) — the flavor the WOFF preserved
  font.writeUInt16BE(numTables, 4);  // numTables at offset 4 (OT spec "Table Directory" layout)
  // The three binary-search fields the sfnt header carries (OT spec formulas):
  // entrySelector = floor(log2(numTables)); searchRange = 16·2^entrySelector.
  const entrySelector = Math.floor(Math.log2(numTables));
  const searchRange = 16 * (1 << entrySelector);
  font.writeUInt16BE(searchRange, 6);
  font.writeUInt16BE(entrySelector, 8);
  font.writeUInt16BE(numTables * 16 - searchRange, 10); // rangeShift, ending the 12-byte header
  entries.forEach((e, i) => {
    const at = 12 + i * 16;
    font.write(e.tag, at, 4, 'latin1');            // tag
    font.writeUInt32BE(e.origChecksum, at + 4);    // the ORIGINAL declared checksum, verbatim
    font.writeUInt32BE(e.newOffset, at + 8);       // offset in the rebuilt file
    font.writeUInt32BE(e.origLength, at + 12);     // original (uncompressed) length
    e.data.copy(font, e.newOffset);                // the table bytes themselves
  });
  // ── Recompute head.checkSumAdjustment (WOFF §"Converting back to sfnt") ───
  // The adjustment balances the WHOLE-FILE sum, and the file layout just
  // changed (WOFF offsets → sfnt offsets), so the original value is stale by
  // construction. Zero the field, sum the file, then write the balance.
  const head = entries.find((e) => e.tag === 'head');
  if (head && head.origLength >= HEAD_ADJUSTMENT_OFFSET + 4) {
    font.writeUInt32BE(0, head.newOffset + HEAD_ADJUSTMENT_OFFSET);
    const adjustment = (SFNT_CHECKSUM_MAGIC - sfntChecksum(font)) >>> 0;
    font.writeUInt32BE(adjustment, head.newOffset + HEAD_ADJUSTMENT_OFFSET);
  }
  return {
    font,
    extension,
    flavor,
    numTables,
    checksumMismatches,
    // WOFF §3: totalSfntSize MUST equal the rebuilt size. Informational here
    // because the rebuilt font is self-consistent either way and some
    // authoring tools get the field wrong; the caller's log carries it.
    totalSfntSizeMatches: totalSfntSize === font.length,
  };
}

// ── The candidate gate + naming (mirrors svg-preraster's pure half) ─────────

/** Is this wire `src` a WOFF1 candidate this hop will transcode?
 *
 *  PURE and deliberately narrow: a corpus-relative path whose extension is
 *  `.woff` — NOT `.woff2`, which the transcoder rejects by content anyway but
 *  which this gate declines by name so a woff2 corpus never even reads the
 *  file. An absolute or protocol path is excluded for the same containment
 *  reason resolveFontFile excludes it (feed-lib.mjs). */
export function isWoffTranscodeCandidate(src) {
  if (typeof src !== 'string') return false;
  const v = src.trim();
  if (v === '') return false;
  if (v.startsWith('/') || /^[a-z][a-z0-9+.-]*:/i.test(v)) return false;
  return /\.woff$/i.test(v);
}

/** The transcode stand-in path for one `.woff` src (naming contract in the
 *  header banner): the flavor-honest extension is APPENDED. Pure string math
 *  — says nothing about whether the file exists. */
export function transcodedSrcFor(src, extension) {
  return `${src}.${extension}`;
}

/** Does this `src` name a HOST TRANSCODE (rather than an authored font)?
 *  The `.woff.ttf` / `.woff.otf` double extension is the marker, exactly as
 *  `.svg.png` marks a pre-raster — one string test any log reader (or a
 *  future runtime honesty stamp) can apply without a manifest. */
export function isTranscodedSrc(src) {
  return typeof src === 'string' && /\.woff\.(ttf|otf)$/i.test(src.trim());
}

/**
 * Rewrite a decoded IR document's `fontFaces[].src` onto their transcoded
 * stand-ins, IN PLACE, for the ANDROID copy only.
 *
 * `transcoded` is the map of woff `src` → sfnt `src` that [transcodeWoffSources]
 * actually PRODUCED. Only those are rewritten: a woff whose transcode failed
 * (or was never attempted) keeps its `.woff` on the wire so the runtime's
 * existing extension decline fires and the gap stays visible — rewriting to a
 * file that does not exist would swap the precise "Typeface cannot parse a
 * woff container" for a misleading "the feeder hop did not deliver it".
 *
 * Returns the `{ src, fontSrc }` pairs actually applied, in declaration order
 * and deduped (css-fonts-4 §4.1 lets many faces name one file), for the
 * feeder's log line.
 */
export function applyWoffTranscodeRewrite(doc, transcoded) {
  const applied = [];
  const seen = new Set();
  // fontFaces is a TOP-LEVEL document list (schema/spec/01-envelope.md §5) —
  // no component walk needed, unlike the replaced-image rewrite.
  for (const face of Array.isArray(doc?.fontFaces) ? doc.fontFaces : []) {
    const src = typeof face?.src === 'string' ? face.src.trim() : '';
    const fontSrc = src ? transcoded.get(src) : undefined;
    if (fontSrc) {
      face.src = fontSrc; // the runtime resolves File(fontsDir, src) verbatim
      if (!seen.has(src)) { seen.add(src); applied.push({ src, fontSrc }); }
    }
  }
  return applied;
}

/** Every distinct `.woff` font source across a batch of decoded documents, in
 *  first-seen order. Called ONCE per feeder run so each woff converts once
 *  (all 48 wave-41 font tests share ONE LinLibertine file). `srcsOf` is
 *  injected (feed-lib's documentFontSrcs) so this module never re-derives the
 *  fontFaces walk — one walker, one truth. */
export function collectWoffSources(docs, srcsOf) {
  const out = [];
  const seen = new Set();
  for (const doc of docs) {
    for (const src of srcsOf(doc)) {
      if (!isWoffTranscodeCandidate(src) || seen.has(src)) continue;
      seen.add(src);
      out.push(src);
    }
  }
  return out;
}

/**
 * The ONE batch entry point the Android feeder calls: read the per-test IR
 * files, find every `.woff` font source among them, and transcode each once.
 *
 * Lives here rather than inline in feed-android.mjs for the same reason
 * prerasterizeFixtures lives in svg-preraster.mjs: the conversion, the naming
 * and the rewrite must be one module with one unit suite, and the feeder only
 * wires them. An unreadable fixture is SKIPPED silently here — the feeder's
 * own loop reads the same file a moment later and reports it per-fixture.
 *
 * @param {string[]} fixturePaths per-test IR JSON paths (the feeder's --fixtures list)
 * @param {object}   opts         wptDir / log / force, plus `srcsOf`
 *                                (feed-lib's documentFontSrcs — injected)
 */
export async function transcodeWoffFixtures(fixturePaths, opts) {
  const docs = [];
  for (const fx of fixturePaths) {
    try { docs.push(JSON.parse(await fs.readFile(fx, 'utf8'))); }
    catch { /* the feeder's own loop reports this file */ }
  }
  const srcs = collectWoffSources(docs, opts.srcsOf);
  return transcodeWoffSources(srcs, opts);
}

/** Read the 4-byte `sfntVersion` tag every TTF/OTF opens with (OT spec, "Table
 *  Directory" — the same field this module WRITES at offset 0 of a rebuild).
 *  Returns null when the file cannot be opened or is shorter than those four
 *  bytes, which is itself proof that whatever sits there is not a font. Opens
 *  a handle and reads 4 bytes rather than readFile-ing the whole sibling: the
 *  check runs once per woff per feeder run and must not pay a ~300 KB read to
 *  learn something the first word already settles. */
async function readSfntVersion(abs) {
  let handle;
  try {
    handle = await fs.open(abs, 'r');
    const head = Buffer.alloc(4);
    const { bytesRead } = await handle.read(head, 0, 4, 0);
    return bytesRead === 4 ? head.readUInt32BE(0) : null;
  } catch {
    return null; // unreadable ⇒ unusable ⇒ re-transcode, same as absent
  } finally {
    if (handle) await handle.close().catch(() => {}); // a close failure must not mask the answer
  }
}

/**
 * Is an existing transcode still good for this woff — and if not, WHY?
 *
 * Freshness is mtime-based, same as svg-preraster's rasterIsFresh and for the
 * same reason: the corpus is a read-only mirror, a file only changes when the
 * pin moves, and a re-fetch rewrites mtimes. `>=` because a checkout can land
 * both files in the same second. `force` ignores the cache entirely (the
 * escape hatch for a transcoder bug fix, which changes the output without
 * touching the woff).
 *
 * But mtime alone TRUSTS the sibling's CONTENT, and that trust was misplaced:
 * an interrupted feeder run (SIGINT, a full disk, an OOM kill mid-write) left
 * a PARTIAL file at the sibling path whose mtime is newer than the woff's, so
 * every later run reported `CACHED` and shipped those truncated bytes to the
 * device — a silently wrong (or unparseable) typeface with nothing in any log
 * saying so. The write path below now renames into place so that state can no
 * longer be CREATED; this validation covers the siblings already on disk from
 * before that fix, plus any corruption arriving from outside this module
 * (a half-copied corpus mirror, a truncating filesystem).
 *
 * Two content checks, both cheap because the transcode has ALREADY been
 * computed by the time this is called (the cache only ever saved the write):
 *   * SIZE must equal the freshly rebuilt font's length — this is what catches
 *     truncation, the actual observed failure, since a partial write keeps the
 *     head bytes and loses the tail;
 *   * the first word must be a known sfntVersion (0x00010000 / 'true' /
 *     'OTTO' — FLAVOR_EXTENSIONS' keys, the only tags this module emits) —
 *     this catches a sibling whose bytes are the right length but not a font.
 * A full byte-compare would be strictly stronger but would cost a whole extra
 * read to decide a question whose only remaining answer is "rewrite the bytes
 * we are already holding" — the write is what the cache saves, so paying a
 * full read to skip a full write is not a cache at all.
 *
 * @returns {Promise<{reuse: boolean, reason: string|null}>} `reason` is set
 *   ONLY for a rejected-because-invalid sibling, so the caller can log that
 *   loudly and distinguish it from the ordinary absent/stale miss (silent).
 */
async function transcodeIsFresh(woffAbs, sfntAbs, expectedBytes, force) {
  if (force) return { reuse: false, reason: null };
  let woffStat, sfntStat;
  try {
    [woffStat, sfntStat] = await Promise.all([fs.stat(woffAbs), fs.stat(sfntAbs)]);
  } catch {
    return { reuse: false, reason: null }; // no sibling yet — the ordinary first run
  }
  if (sfntStat.mtimeMs < woffStat.mtimeMs) return { reuse: false, reason: null }; // ordinary staleness
  if (sfntStat.size !== expectedBytes) {
    return {
      reuse: false,
      reason: `cached sibling is ${sfntStat.size} bytes but the transcode is ${expectedBytes} — ` +
        'a partial or foreign write, not this transcode',
    };
  }
  const version = await readSfntVersion(sfntAbs);
  if (version === null) {
    return { reuse: false, reason: 'cached sibling could not be read for its sfntVersion tag' };
  }
  if (!FLAVOR_EXTENSIONS.has(version)) {
    return {
      reuse: false,
      reason: `cached sibling opens with 0x${version.toString(16).padStart(8, '0')}, ` +
        "which is not an sfnt version tag (0x00010000/'true'/'OTTO')",
    };
  }
  return { reuse: true, reason: null };
}

/**
 * Transcode every WOFF1 in `srcs` to its sfnt sibling under `wptDir`.
 *
 * Returns a Map of woff `src` → sfnt `src` for the ones that SUCCEEDED —
 * exactly the map [applyWoffTranscodeRewrite] consumes. A failure is a
 * per-file decline: logged, omitted from the map, and the woff keeps riding
 * the wire so the runtime's own extension decline fires. Nothing here is ever
 * fatal — failing the section would hide every other property its tests
 * measure (the same argument every asset hop makes about its declines).
 *
 * ── THE SWITCH, and why it defaults ON ────────────────────────────────────
 * `TITAN_WOFF_TRANSCODE=0` disables the hop (both feeder legs then behave
 * exactly as wave-41: the woff rides the wire, DocumentFontRegistry declines
 * it by name, the capture shapes in the bundled fallback). Default ON — the
 * SAME default svg-preraster carries since wave 44, but reached by a different
 * route, and the distinction is the point: this transcode is LOSSLESS (the
 * header banner: identical table bytes, only the container is rebuilt), so it
 * needed no evidence to default ON, whereas the pre-raster is lossy at one
 * frozen size, LOST its wave-41 and wave-43 A/Bs, and only earned its flip
 * once wave-44 lane U3 fixed the sizing and placement defects those A/Bs
 * named. The off-switch exists so an A/B stays cheap HERE too, should a
 * transcode defect ever demand one.
 *
 * `TITAN_WOFF_TRANSCODE=force` is the OTHER half of the same switch, wired by
 * the feeder into `opts.force`: rewrite every sibling regardless of the cache.
 * It exists because the cache keys on the WOFF's mtime, so a fix to THIS
 * transcoder changes the output while every input file stays untouched —
 * without a force knob the only way to roll such a fix across a materialised
 * corpus would be deleting siblings by hand, exactly the manual step a
 * wrong-bytes bug must not require.
 *
 * @param {string[]} srcs  corpus-relative `.woff` paths (from collectWoffSources)
 * @param {object}   opts
 * @param {string}   opts.wptDir  corpus root; a src that escapes it is declined
 * @param {Function} opts.log     one-line logger (the feeder's own)
 * @param {boolean} [opts.force]  ignore the mtime cache (transcoder fix rollout);
 *                                the feeder sets it from TITAN_WOFF_TRANSCODE=force
 */
export async function transcodeWoffSources(srcs, opts) {
  const transcoded = new Map();
  const log = opts?.log ?? (() => {});
  if (!Array.isArray(srcs) || srcs.length === 0) return transcoded;
  // The default-ON gate — see the banner above for why this hop never had to
  // earn that default (preraster now defaults ON too, but only by measurement).
  if (process.env.TITAN_WOFF_TRANSCODE === '0') {
    log(`woff transcode OFF (TITAN_WOFF_TRANSCODE=0) — ${srcs.length} woff(s) keep riding the wire ` +
      `and the Android runtime will decline them by name`);
    return transcoded;
  }
  if (!opts.wptDir) {
    log(`woff transcode SKIPPED for ${srcs.length} woff(s): no --wpt-dir, so nothing can be resolved`);
    return transcoded;
  }
  const root = resolve(opts.wptDir);
  for (const src of srcs) {
    const woffAbs = resolve(root, src);
    // Containment — the same check, for the same reason, as resolveFontFile:
    // a `..` chain must never let a fixture name a file outside the corpus
    // and get a transcode of it written next to it (or pushed to a sandbox).
    if (woffAbs !== root && !woffAbs.startsWith(root + '/')) {
      log(`woff transcode DECLINED ${src}: resolves outside the corpus root`);
      continue;
    }
    if (!existsSync(woffAbs)) {
      log(`woff transcode DECLINED ${src}: no such file on disk`);
      continue;
    }
    let bytes;
    try {
      bytes = await fs.readFile(woffAbs);
    } catch (err) {
      log(`woff transcode DECLINED ${src}: unreadable (${err.message})`);
      continue;
    }
    let result;
    try {
      result = woffToTtf(bytes); // throws per-file; each throw is a decline below
    } catch (err) {
      log(`woff transcode DECLINED ${src}: ${err.message} — the woff keeps riding the wire ` +
        `and the runtime will decline it by name`);
      continue;
    }
    const fontSrc = transcodedSrcFor(src, result.extension);
    const sfntAbs = resolve(root, fontSrc);
    const cache = await transcodeIsFresh(woffAbs, sfntAbs, result.font.length, opts.force);
    if (cache.reuse) {
      // LOUD even on a cache hit — the stamp is about the transcode path
      // being ENGAGED, not about work done this minute (svg-preraster's rule).
      log(`woff transcode CACHED  ${src} → ${fontSrc}`);
      transcoded.set(src, fontSrc);
      continue;
    }
    if (cache.reason) {
      // A sibling that LOOKED fresh but failed validation. Stamped loudly and
      // by name because it is evidence of a past interrupted run (or a damaged
      // mirror), and because the alternative — silently rewriting it — would
      // erase the only trace that a wrong font was ever being shipped. The run
      // continues: the rewrite below replaces it with a complete transcode.
      log(`woff transcode REJECTED cached ${fontSrc}: ${cache.reason} — re-transcoding`);
    }
    // WRITE TO A TEMP FILE, THEN RENAME. A direct writeFile onto the sibling
    // path is not atomic: interrupt it (SIGINT on a feeder run, ENOSPC, an OOM
    // kill) and a PARTIAL font is left sitting at the exact path every later
    // run consults, newer than the woff and therefore "fresh". rename(2) within
    // one directory is atomic on every filesystem this runs on, so the sibling
    // path only ever holds a complete transcode — the failure mode above cannot
    // be created at all, rather than merely being detected afterwards.
    // The temp name carries pid + timestamp so two concurrent feeders (the
    // campaign runs android and ios legs side by side) cannot collide, and its
    // `.tmp-…` tail keeps it outside isTranscodedSrc's `.woff.ttf|otf` match so
    // a stray scratch file can never be mistaken for a deliverable stand-in.
    // The residual cost, stated rather than hidden: a kill BETWEEN the write
    // and the rename leaves that `.tmp-…` file behind, since no handler can run.
    // It is inert litter in a gitignored mirror that nothing ever reads — a
    // strictly better failure than the corrupt DELIVERABLE it replaces.
    const tmpAbs = `${sfntAbs}.tmp-${process.pid}-${Date.now()}`;
    try {
      await fs.mkdir(dirname(sfntAbs), { recursive: true }); // sibling dir always exists, but mkdir -p is free
      await fs.writeFile(tmpAbs, result.font);
      await fs.rename(tmpAbs, sfntAbs);
    } catch (err) {
      // Best-effort scratch cleanup: a failed write must not litter the corpus
      // mirror. Swallowed because the DECLINE below is the reportable event —
      // a cleanup failure must never replace the real error message.
      await fs.rm(tmpAbs, { force: true }).catch(() => {});
      log(`woff transcode DECLINED ${src}: could not write ${fontSrc} (${err.message})`);
      continue;
    }
    transcoded.set(src, fontSrc);
    // THE LOUD STAMP: names the stand-in, the flavor-honest container, the
    // size, and any checksum oddity — everything an investigator reading the
    // feeder log needs to know these bytes are a HOST repackage.
    const notes = [];
    if (result.checksumMismatches.length > 0) {
      notes.push(`declared-checksum mismatch in ${result.checksumMismatches.join('/')}`);
    }
    if (!result.totalSfntSizeMatches) notes.push('header totalSfntSize was stale');
    log(`woff transcode ${src} → ${fontSrc} (${result.numTables} tables, ${result.font.length} bytes, lossless repackage` +
      `${notes.length ? '; ' + notes.join('; ') : ''})`);
  }
  return transcoded;
}
