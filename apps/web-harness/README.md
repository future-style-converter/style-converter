# Web capture harness

Vite + React test harness for the web runtime. It loads an IR document
from `public/ir-components.json`, renders every component with
[`@style-converter/web`](../../runtimes/web/) (the runtime style engine),
and exposes each one on a chromeless canvas that
`capture-screenshots.mjs` (puppeteer) turns into per-component PNGs for
the 3-way SSIM comparison. Not a product — the runtime is; this app just
feeds the visual pipeline.

**Primary entry point: [`../../test-all.sh`](../../test-all.sh)** — it
converts the fixture, copies the IR here, builds, captures, and compares.

Run it standalone:

```bash
# from the repo root (npm workspaces — install once with `npm ci`)
./gradlew :converter:run --args="convert --from css --to ir -i fixtures/visual-test.json -o out"
npm -w apps/web-harness run copy-ir     # out/tmpOutput.json → public/ir-components.json
npm -w apps/web-harness run dev         # browse the rendered components
npm -w apps/web-harness run test        # vitest suite (276 tests: capture pipeline + UI)
```

Web-only smoke of the whole capture pipeline:
`bash tools/visual/smoke.sh --quick`.
