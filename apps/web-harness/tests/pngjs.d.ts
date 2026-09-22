// pngjs.d.ts — the minimal ambient declaration for `pngjs` (7.x ships no
// typings and @types/pngjs is not installed in this workspace). Scoped to the
// members the harness tests use (PNG.sync.read → width / height / RGBA data).
// The harness `tsc` (tsconfig include = ["src"]) never sees tests/, so a test
// that imports pngjs pulls this in itself with a triple-slash reference — the
// focused type check the wave-51 polish pass ran is what this exists for.
declare module 'pngjs' {
  /** A decoded (or to-be-encoded) PNG — pngjs's `PNG` class, 8-bit RGBA. */
  export class PNG {
    /** Image width in pixels. */
    width: number;
    /** Image height in pixels. */
    height: number;
    /** Row-major RGBA bytes, 4 per pixel, indexed `(y * width + x) * 4`. */
    data: Buffer;
    /** The synchronous codec: `read` decodes a PNG buffer, `write` encodes one. */
    static sync: {
      /** Decode a complete PNG file buffer into a PNG instance. */
      read(buffer: Buffer): PNG;
      /** Encode a PNG instance into a complete PNG file buffer. */
      write(png: PNG): Buffer;
    };
  }
}
