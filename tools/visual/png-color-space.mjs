// png-color-space.mjs — assert that a capture is sRGB (or untagged) BEFORE
// any metric reads its bytes.
//
// ## Why this exists
//
// Nothing in the comparison pipeline is colour-managed, and three stages
// each have a DIFFERENT implicit policy:
//
//   * decode      — `pngjs` registers chunk handlers for IHDR/IEND/IDAT/
//                   PLTE/tRNS/gAMA only. `iCCP`, `sRGB` and `cICP` are
//                   skipped entirely: a profile is DISCARDED, never applied.
//   * iOS normalize — `normalize-pngs.mjs` re-encodes with pngjs, so the
//                   output carries only IHDR/IDAT/IEND. Any `iCCP` is
//                   physically stripped.
//   * web crop    — sharp's documented default is to convert to sRGB and
//                   strip all metadata including the ICC profile.
//
// So every metric operates on gamma-encoded bytes ASSUMED to be sRGB. That
// assumption is currently true — measured: all three platforms produce
// byte-identical fills for the tested colours — but it is an assumption,
// not an enforced invariant, and it fails SILENTLY:
//
//   * Chrome's `--force-color-profile=generic-rgb` shifts #ff6b6b by
//     ΔE00 4.63 and embeds a 277-byte `iCCP` chunk that pngjs ignores, so
//     the harness would compare converted values as if untouched.
//   * Apple's ImageIO tags a Display-P3 CGContext with BOTH `iCCP` and
//     `cICP`. `cICP` is a recent PNG addition most JS decoders have never
//     heard of. The day one iOS component renders `color(display-p3 …)`,
//     the tag is thrown away and P3 numbers are compared as sRGB.
//
// An sRGB↔P3 mis-tag costs ΔE00 1.3–4.1 on saturated colours and exactly
// 0.00 on neutrals. No other bug produces that signature — which is why it
// is worth detecting explicitly rather than hoping a similarity score
// notices.
//
// ## The policy
//
// Absent-or-sRGB passes. A profile chunk we cannot honour is a HARD ERROR,
// because the alternative (today's behaviour) is to silently produce wrong
// numbers. Verified against the 363 committed baselines: Android carries
// `sRGB` + `sBIT`, iOS carries nothing (normalized), web carries `pHYs`.
// Zero files carry `iCCP` or `cICP`, so this assertion starts green.

/** PNG file signature — the 8 bytes every PNG starts with. */
const PNG_SIGNATURE = Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]);

/**
 * Colour chunks that mean "this image is NOT plain sRGB, and honouring it
 * requires a conversion no stage of this pipeline performs".
 *
 *   iCCP — embedded ICC profile (Chrome's non-sRGB profiles, Apple's P3)
 *   cICP — coding-independent code points (Apple ImageIO on wide gamut)
 *
 * `sRGB` is deliberately NOT here: it asserts the very thing we want.
 * `sBIT`/`pHYs` are not colour-space claims and are ignored harmlessly.
 *
 * `gAMA` and `cHRM` used to be dismissed here as "not colour-space
 * claims" — factually wrong, as the pipeline hunt pointed out. gAMA
 * DEFINES the transfer function of the stored bytes: a capture tagged
 * gAMA 1.0 (linear light) scored as sRGB is off by ~127/255 at mid-grey,
 * and cHRM redefines the primaries the RGB triples mean. Both are
 * therefore VALUE-checked in assertSrgbOrUntagged below: the one benign
 * spelling each has (the sRGB-compatible value some encoders write
 * alongside an sRGB chunk) passes; anything else is the same hard error
 * as an ICC profile.
 */
export const DISQUALIFYING_COLOR_CHUNKS = Object.freeze(['iCCP', 'cICP']);

/** gAMA payload for sRGB-compatible 1/2.2 encoding: 45455 (per the PNG
 *  spec's own example for sRGB-ish gamma). Stored as a 4-byte BE uint of
 *  gamma × 100000. */
export const SRGB_COMPATIBLE_GAMA = 45455;

/** cHRM payload for the sRGB/BT.709 primaries + D65 white point, in the
 *  chunk's ×100000 fixed-point encoding, field order per the PNG spec:
 *  wx, wy, rx, ry, gx, gy, bx, by. */
export const SRGB_COMPATIBLE_CHRM = Object.freeze([31270, 32900, 64000, 33000, 30000, 60000, 15000, 6000]);

/**
 * Read one chunk's payload bytes, or null when absent/corrupt. Shares the
 * walk logic's bounds discipline with readPngChunkTypes.
 */
export function readPngChunkData(buf, wanted) {
  if (buf.length < 8 || !buf.subarray(0, 8).equals(PNG_SIGNATURE)) return null;
  let offset = 8;
  while (offset + 12 <= buf.length) {
    const length = buf.readUInt32BE(offset);
    const type = buf.toString('latin1', offset + 4, offset + 8);
    const next = offset + 12 + length;
    if (next <= offset || next > buf.length) return null;
    if (type === wanted) return buf.subarray(offset + 8, offset + 8 + length);
    if (type === 'IEND') return null;
    offset = next;
  }
  return null;
}

/**
 * Walk a PNG buffer's chunk table and return the chunk type strings in
 * file order (e.g. `['IHDR', 'sRGB', 'IDAT', 'IEND']`).
 *
 * Chunk layout after the 8-byte signature is, repeatedly:
 *   4 bytes big-endian length · 4 bytes ASCII type · <length> bytes data · 4 bytes CRC
 *
 * We never validate the CRC — this is a metadata scan on files we just
 * produced, not a hardening pass against hostile input. We DO bail out on
 * a malformed table rather than looping forever.
 *
 * @param {Buffer} buf raw PNG bytes
 * @returns {string[]} chunk types in file order
 */
export function readPngChunkTypes(buf) {
  // Not a PNG at all → no chunks to report. The decoder will raise its own,
  // better error a moment later; we do not want to pre-empt it with a worse one.
  if (buf.length < 8 || !buf.subarray(0, 8).equals(PNG_SIGNATURE)) return [];

  const types = [];
  let offset = 8;                                   // skip the signature

  // 12 = 4 (length) + 4 (type) + 4 (CRC); a chunk cannot be shorter than its header.
  while (offset + 12 <= buf.length) {
    const length = buf.readUInt32BE(offset);        // payload size, excludes header + CRC
    const type = buf.toString('latin1', offset + 4, offset + 8);
    types.push(type);
    if (type === 'IEND') break;                     // IEND terminates the stream by spec
    // A length that runs past the buffer means the table is corrupt. Stop
    // scanning rather than reading out of bounds or spinning.
    const next = offset + 12 + length;
    if (next <= offset || next > buf.length) break;
    offset = next;
  }

  return types;
}

/**
 * Throw when `buf` carries a colour chunk this pipeline cannot honour.
 *
 * Named `assert*` deliberately: the failure mode we are defending against
 * is not "wrong pixels", it is "plausible-looking numbers computed from
 * pixels in the wrong space". A loud throw converts an undetectable
 * corruption into a red build, which is the entire point.
 *
 * @param {Buffer} buf   raw PNG bytes
 * @param {string} label path or identifier, used in the error message
 * @returns {string[]}   the chunk types, so callers can log them if useful
 */
export function assertSrgbOrUntagged(buf, label) {
  const types = readPngChunkTypes(buf);
  const offenders = types.filter((t) => DISQUALIFYING_COLOR_CHUNKS.includes(t));
  // gAMA / cHRM: benign ONLY at their sRGB-compatible values (see the
  // constants above). A non-sRGB transfer function or primary set is the
  // same "pixels would be scored as sRGB" hazard as an ICC profile.
  if (types.includes('gAMA')) {
    const d = readPngChunkData(buf, 'gAMA');
    const gamma = d && d.length >= 4 ? d.readUInt32BE(0) : null;
    if (gamma !== SRGB_COMPATIBLE_GAMA) {
      offenders.push(`gAMA(${gamma ?? 'corrupt'} ≠ ${SRGB_COMPATIBLE_GAMA})`);
    }
  }
  if (types.includes('cHRM')) {
    const d = readPngChunkData(buf, 'cHRM');
    const vals = d && d.length >= 32
      ? Array.from({ length: 8 }, (_, i) => d.readUInt32BE(i * 4)) : null;
    const ok = vals && vals.every((v, i) => v === SRGB_COMPATIBLE_CHRM[i]);
    if (!ok) offenders.push(`cHRM(${vals ? vals.join(',') : 'corrupt'} ≠ sRGB primaries)`);
  }
  if (offenders.length > 0) {
    throw new Error(
      `${label}: PNG carries ${offenders.join(' + ')} colour chunk(s). ` +
      'This pipeline decodes with pngjs, which IGNORES colour profiles — the ' +
      'pixels would be compared as if they were sRGB, silently producing wrong ' +
      'ΔE / SSIM / pixel numbers. Fix the capture to emit untagged sRGB ' +
      '(web: --force-color-profile=srgb; iOS: render into an sRGB CGContext; ' +
      'Android: check the Bitmap colour space) rather than relaxing this check.',
    );
  }
  return types;
}
