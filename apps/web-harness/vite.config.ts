import { defineConfig, type Plugin } from 'vite'
import react from '@vitejs/plugin-react'
import { resolve, sep } from 'path'
import { createReadStream, statSync } from 'fs'

// ── wave-34 lane F2: the /wpt-font/ static route ─────────────────────────────
//
// The IR's document-level `fontFaces` list (schema/spec/01-envelope.md §5)
// carries a PATH into the producing pipeline's corpus — `tools/wpt/…` for the
// titan extractor — not a payload, because a real webfont is 50–500 KB and a
// combined section document carries tens of tests. useFontFaces.ts turns each
// entry into an `@font-face` rule whose src is `/wpt-font/<that path>`; this
// middleware is the other half of that contract.
//
// A copy step was the alternative and is worse: the capture pipeline would
// have to know, before vite starts, which faces the section's tests declare,
// and every stale copy in public/ would then silently shadow a corpus update.
// Serving straight from the corpus has one source of truth.
//
// SECURITY POSTURE. The WPT mirror is third-party content and the path
// arrives from it, so the route is deliberately narrow:
//   * containment is checked on the RESOLVED absolute path, not on the
//     request string — `%2e%2e/` and friends decode before resolve() sees
//     them, and a prefix test on the raw text would miss that;
//   * only the font extensions the wire admits are served, so the route can
//     never become a general file reader for the repo;
//   * GET/HEAD only.
// Anything else falls through to vite's own 404.

/** Extension → Content-Type for the font formats spec 01 §5 admits. A
 *  CLOSED table: an extension absent here is not served at all, which is
 *  what keeps this route from being a general static-file endpoint. */
const FONT_CONTENT_TYPES: Record<string, string> = {
  woff2: 'font/woff2',
  woff: 'font/woff',
  ttf: 'font/ttf',
  otf: 'font/otf',
  ttc: 'font/collection',
  otc: 'font/collection',
}

/** The URL prefix, pinned against useFontFaces.ts's WPT_FONT_ROUTE by the
 *  harness unit test so a rename cannot half-land. */
const WPT_FONT_ROUTE = '/wpt-font/'

/** Serve font files out of the WPT corpus mirror under /wpt-font/. */
function wptFontRoute(corpusRoot: string): Plugin {
  return {
    name: 'sc-wpt-font-route',
    configureServer(server) {
      server.middlewares.use((req, res, next) => {
        const url = req.url ?? ''
        if (!url.startsWith(WPT_FONT_ROUTE)) return next()
        if (req.method !== 'GET' && req.method !== 'HEAD') return next()
        // Strip the query/hash vite's dev server may append, then decode:
        // the corpus has spaces and parentheses in some support paths.
        const rawPath = url.slice(WPT_FONT_ROUTE.length).split(/[?#]/)[0]
        let rel: string
        try {
          rel = decodeURIComponent(rawPath)
        } catch {
          return next() // malformed percent-escape — not a path we serve
        }
        if (!rel) return next()
        const abs = resolve(corpusRoot, rel)
        // Containment on the RESOLVED path. The trailing separator matters:
        // without it, a sibling directory whose name merely starts with the
        // corpus root's name would pass.
        if (abs !== corpusRoot && !abs.startsWith(corpusRoot + sep)) return next()
        const ext = /\.([A-Za-z0-9]+)$/.exec(abs)?.[1]?.toLowerCase()
        const type = ext ? FONT_CONTENT_TYPES[ext] : undefined
        if (!type) return next()
        let size: number
        try {
          const st = statSync(abs)
          if (!st.isFile()) return next()
          size = st.size
        } catch {
          return next() // absent on disk — vite's 404 is the honest answer
        }
        res.setHeader('Content-Type', type)
        res.setHeader('Content-Length', String(size))
        // The corpus is immutable within a run; caching keeps a multi-canvas
        // composed capture from re-fetching the same 261 KB face per test.
        res.setHeader('Cache-Control', 'public, max-age=3600')
        if (req.method === 'HEAD') {
          res.end()
          return
        }
        createReadStream(abs).pipe(res)
      })
    },
  }
}

// Repo root is three levels up from apps/web-harness/. The corpus mirror
// lives at tools/wpt/ (gitignored — tools/titan/fetch-wpt.sh populates it);
// WPT_DIR overrides it for the same reason extract-fixture.mjs honours that
// variable, so a relocated mirror stays servable.
const REPO_ROOT = resolve(__dirname, '..', '..')
const WPT_CORPUS_ROOT = resolve(process.env.WPT_DIR ?? resolve(REPO_ROOT, 'tools', 'wpt'))

export default defineConfig({
  plugins: [react(), wptFontRoute(WPT_CORPUS_ROOT)],
  resolve: {
    alias: {
      '@': resolve(__dirname, './src'),
      '@sdui': resolve(__dirname, './src/sdui'),
      '@ui': resolve(__dirname, './src/ui'),
    },
  },
  server: {
    port: 3000,
    open: true,
  },
})
