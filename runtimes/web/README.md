# @style-converter/web — the web runtime

The web runtime style engine: an npm package (workspace of the repo-root
`package.json`) that turns Style Converter IR into CSS declarations on
real DOM, one **Config / Extractor / Applier** triplet per property under
`src/engine/<category>/` (the canonical 33-category tree shared with
`runtimes/compose` and `runtimes/swiftui` — see the repo-root `CLAUDE.md`
for the per-property contract). `src/core/` holds the IR models and the
`StyleBuilder` dispatcher; consumers get everything via `src/index.ts`.

Dynamic styling (`schema/spec/06-dynamic-styling.md`): base properties
stay inline via `buildStyles`, while selector/media buckets flow through
`src/core/renderer/RuleBuilder.ts` — per-component `sc-<id>` classes,
real `:hover`-style rules with `.force-<state>` twin selectors (the
spec 06 §6 capture hook), `@media` rules gated by the runtime-v1 grammar
(`MediaQueryV1.ts`), a `:root { color-scheme: light dark }` opt-in when
`light-dark()` values are present, and an idempotent managed `<style>`
mount (`mountRules`) plus an SSR string export (`buildStylesheet`).

```bash
# from the repo root (install once with `npm ci`)
npm -w runtimes/web run test        # vitest suite (904 tests, tests/<category>/)
npm -w runtimes/web run typecheck   # tsc --noEmit
```

Rendered and screenshot-tested by [`apps/web-harness/`](../../apps/web-harness/)
via `./test-all.sh`.
