# @style-converter/web — the web runtime

The web runtime style engine: an npm package (workspace of the repo-root
`package.json`) that turns Style Converter IR into CSS declarations on
real DOM, one **Config / Extractor / Applier** triplet per property under
`src/engine/<category>/` (the canonical 33-category tree shared with
`runtimes/compose` and `runtimes/swiftui` — see the repo-root `CLAUDE.md`
for the per-property contract). `src/core/` holds the IR models and the
`StyleBuilder` dispatcher; consumers get everything via `src/index.ts`.

```bash
# from the repo root (install once with `npm ci`)
npm -w runtimes/web run test        # vitest suite (786 tests, tests/<category>/)
npm -w runtimes/web run typecheck   # tsc --noEmit
```

Rendered and screenshot-tested by [`apps/web-harness/`](../../apps/web-harness/)
via `./test-all.sh`.
