// _dispatch.ts — Phase-10 print long-tail dispatch (11 properties).
import type { CSSProperties } from 'react';
import { extractBleed } from './BleedExtractor';
import { applyBleed } from './BleedApplier';
import { extractBookmarkLabel } from './BookmarkLabelExtractor';
import { applyBookmarkLabel } from './BookmarkLabelApplier';
import { extractBookmarkLevel } from './BookmarkLevelExtractor';
import { applyBookmarkLevel } from './BookmarkLevelApplier';
import { extractBookmarkState } from './BookmarkStateExtractor';
import { applyBookmarkState } from './BookmarkStateApplier';
import { extractBookmarkTarget } from './BookmarkTargetExtractor';
import { applyBookmarkTarget } from './BookmarkTargetApplier';
import { extractFootnoteDisplay } from './FootnoteDisplayExtractor';
import { applyFootnoteDisplay } from './FootnoteDisplayApplier';
import { extractFootnotePolicy } from './FootnotePolicyExtractor';
import { applyFootnotePolicy } from './FootnotePolicyApplier';
import { extractLeader } from './LeaderExtractor';
import { applyLeader } from './LeaderApplier';
import { extractMarks } from './MarksExtractor';
import { applyMarks } from './MarksApplier';
import { extractPage } from './PageExtractor';
import { applyPage } from './PageApplier';
import { extractSize } from './SizeExtractor';
import { applySize } from './SizeApplier';
interface IRPropertyLike { type: string; data: unknown }
export function applyPrintPhase10(properties: IRPropertyLike[]): CSSProperties {
  const out: CSSProperties = {};
  Object.assign(out, applyBleed(extractBleed(properties)));
  Object.assign(out, applyBookmarkLabel(extractBookmarkLabel(properties)));
  Object.assign(out, applyBookmarkLevel(extractBookmarkLevel(properties)));
  Object.assign(out, applyBookmarkState(extractBookmarkState(properties)));
  Object.assign(out, applyBookmarkTarget(extractBookmarkTarget(properties)));
  Object.assign(out, applyFootnoteDisplay(extractFootnoteDisplay(properties)));
  Object.assign(out, applyFootnotePolicy(extractFootnotePolicy(properties)));
  Object.assign(out, applyLeader(extractLeader(properties)));
  Object.assign(out, applyMarks(extractMarks(properties)));
  Object.assign(out, applyPage(extractPage(properties)));
  Object.assign(out, applySize(extractSize(properties)));
  return out;
}
