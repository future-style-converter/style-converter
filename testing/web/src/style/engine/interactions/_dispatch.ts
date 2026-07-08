// _dispatch.ts — Phase-10 interactions long-tail dispatch (8 properties).
import type { CSSProperties } from 'react';
import { extractCursor } from './CursorExtractor';
import { applyCursor } from './CursorApplier';
import { extractPointerEvents } from './PointerEventsExtractor';
import { applyPointerEvents } from './PointerEventsApplier';
import { extractUserSelect } from './UserSelectExtractor';
import { applyUserSelect } from './UserSelectApplier';
import { extractTouchAction } from './TouchActionExtractor';
import { applyTouchAction } from './TouchActionApplier';
import { extractResize } from './ResizeExtractor';
import { applyResize } from './ResizeApplier';
import { extractInteractivity } from './InteractivityExtractor';
import { applyInteractivity } from './InteractivityApplier';
import { extractCaret } from './CaretExtractor';
import { applyCaret } from './CaretApplier';
import { extractCaretShape } from './CaretShapeExtractor';
import { applyCaretShape } from './CaretShapeApplier';
interface IRPropertyLike { type: string; data: unknown }
export function applyInteractionsPhase10(properties: IRPropertyLike[]): CSSProperties {
  const out: CSSProperties = {};
  Object.assign(out, applyCursor(extractCursor(properties)));
  Object.assign(out, applyPointerEvents(extractPointerEvents(properties)));
  Object.assign(out, applyUserSelect(extractUserSelect(properties)));
  Object.assign(out, applyTouchAction(extractTouchAction(properties)));
  Object.assign(out, applyResize(extractResize(properties)));
  Object.assign(out, applyInteractivity(extractInteractivity(properties)));
  Object.assign(out, applyCaret(extractCaret(properties)));
  Object.assign(out, applyCaretShape(extractCaretShape(properties)));
  return out;
}
