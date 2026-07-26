# @style-converter/web — the web runtime

The web runtime: an npm package (workspace of the repo-root
`package.json`) that turns Style Converter IR into native DOM/CSS. Two
layers, one import:

- **Style engine** — one **Config / Extractor / Applier** triplet per
  property under `src/engine/<category>/` (the canonical 33-category
  tree shared with `runtimes/compose` and `runtimes/swiftui` — see the
  repo-root `CLAUDE.md` for the per-property contract). `src/core/`
  holds the IR models and the `StyleBuilder` dispatcher.
- **Renderer** (`src/renderer/`, issue #41) — a production React
  renderer for whole IR documents: `DocumentRenderer` (compose + render
  + stylesheet lifecycle), `NodeRenderer` (one composed node), the
  `Composer` (flat-wire `slot` refs → render tree, spec 03), the
  trusted `meta.sourceTag` element policy (`TagMapping.ts`), and the
  `useDocumentRules` mount/unmount hook. Deliberately JSX-free source.

Dynamic styling (`schema/spec/06-dynamic-styling.md`): base properties
stay inline via `buildStyles`, while selector/media buckets flow through
`src/core/renderer/RuleBuilder.ts` — per-component `sc-<id>` classes,
real `:hover`-style rules with `.force-<state>` twin selectors (the
spec 06 §6 capture hook), `@media` rules gated by the runtime-v1 grammar
(`MediaQueryV1.ts`), a `:root { color-scheme: light dark }` opt-in when
`light-dark()` values are present, and an idempotent managed `<style>`
mount (`mountRules`) plus an SSR string export (`buildStylesheet`).
Document `@keyframes` (spec 07 §1.2) ride the same rule list.

## Consume from a real app

```tsx
import { decodeIRDocument, DocumentRenderer } from '@style-converter/web';

// The wire document, e.g. fetched from your SDUI backend.
const doc = decodeIRDocument(await (await fetch('/api/screen')).json());

// Client render: composes slot refs, applies engine styles inline,
// mounts selector/media/@keyframes rules, cleans them up on unmount.
export function Screen() {
  return <DocumentRenderer document={doc} />;
}
```

Server-side (no DOM), the stylesheet comes as a string instead:

```tsx
import { renderToString } from 'react-dom/server';
import { buildStylesheet, DocumentRenderer } from '@style-converter/web';

const html = renderToString(<DocumentRenderer document={doc} />);
const css  = buildStylesheet(doc.components, doc.keyframes);
// ship `<style>${css}</style>` + html
```

Runnable version: `node runtimes/web/examples/ssr-smoke.mjs` renders a
token-themed document (CSS-variable tokens + keyframes + a real
`<button>` from `meta.sourceTag`) to static HTML and asserts the output.

The renderer implements **pure CSS semantics**. The capture harness
(`apps/web-harness/`) consumes the same core through an explicit
calibration skin (`RendererOptions` hooks: sizing floors, placeholder
labels, tag allowlist, display:none suppression, the deterministic img
placeholder) — the divergence ledger lives in
`src/renderer/RendererOptions.ts` and the harness wrapper
(`apps/web-harness/src/sdui/ComponentRenderer.tsx`); byte-parity with
the pre-refactor harness DOM is pinned by that app's
`RendererParity.test.tsx`.

```bash
# from the repo root (install once with `npm ci`)
npm -w runtimes/web run test        # vitest suite (1014 tests, tests/<category>/ + tests/renderer/)
npm -w runtimes/web run typecheck   # tsc --noEmit
node runtimes/web/examples/ssr-smoke.mjs   # standalone SSR smoke
```

Rendered and screenshot-tested by [`apps/web-harness/`](../../apps/web-harness/)
via `./test-all.sh`.
