# Review notes — lane visual-test-035-069

Sheets reviewed: 87 (29 stems × web/iOS/Android). Stems 035, 036, 039, 042, 043, 045,
047–069; 037/038/040/041/044/046 excluded (other lane).

Method: Read every sheet (3-pane, 3× zoom), then a throwaway pngjs pass over the diff
pane (exact mask colours: green 0,200,0 / magenta 255,0,255 / red 255,0,0) and a raw
committed-vs-fresh pixel diff as a cross-check.

Findings — all 87 ok:
- magenta = 0 on every sheet; green = |P| on every sheet (full label, no truncation).
- raw committed-vs-fresh differing pixels = green + red on every sheet, i.e. the mask
  accounts for every change; nothing unexplained.
- red bbox is the old label row only: 7 px tall at the content-box origin (y32-38 for
  the plain 62-px stems, y37-43 / y42-48 / y47-53 / y27-33 / y22-28 where the fixture's
  padding/margin/translate moves the origin). Effects stems: 042 Transform_Origin red is
  the rotated ghost inside the diamond (x13-35, y53-71); 043 Zoom_150 / 045 Zoom_200 red
  is the zoomed ghost (11 px / 14 px tall); 047/049/050 clip-path red is the clipped
  fragment; 048 ClipPath_Ellipse red = 0 (old label fully clipped away). Android 035/036
  red < |P| because the old label was clipped by the filtered box (old-label footprint,
  not a defect).
- Fresh-pane label ink = (174,174,180) on all 87 sheets, all three platforms — identical
  brightness.
- Pre-existing, not a refresh regression: the 060–062 and 064–067 typography fixtures
  render no visible text on any platform (frame is empty apart from the label in both
  the committed and fresh panes).
