// tools/titan/results/wave54-web-tail/vitest.census.config.mts — the lane's census runner config.
// Reuses the web-harness vite config (React plugin + aliases) so the gallery
// resolves exactly as in the harness suite, but includes ONLY this lane's
// census files, so no suite ever picks them up and they never pick up a suite.
import { defineConfig, mergeConfig } from 'vitest/config';
import harness from '../../../../apps/web-harness/vite.config';
import path from 'node:path';

// This directory, absolute (the include glob is matched against absolute paths).
const HERE = path.dirname(new URL(import.meta.url).pathname);

export default mergeConfig(harness, defineConfig({
  // Root at the harness so its node_modules / tsconfig paths resolve as usual.
  root: path.resolve(HERE, '../../../../apps/web-harness'),
  test: {
    // Only the census files of this lane directory.
    include: [path.join(HERE, '*.census.test.tsx')],
    // Allow files outside the root.
    dir: HERE,
  },
}));
