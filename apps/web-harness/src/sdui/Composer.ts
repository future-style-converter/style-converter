/**
 * Composer — harness-side alias of the PACKAGE composition layer.
 *
 * The composeTree/findNode implementation was promoted into
 * @style-converter/web/renderer (issue #41) so real apps get the same
 * slot→tree composition the harness always used (Mode A slot refs,
 * Mode B zero-slot root lists, dangling-ref promotion, cycle breaking —
 * schema/spec/03-children.md). This file re-exports it so existing
 * harness imports (`./Composer`) and tests stay stable.
 */

export { composeTree, findNode } from '@style-converter/web/renderer/Composer';
export type { ComposedNode } from '@style-converter/web/renderer/Composer';
