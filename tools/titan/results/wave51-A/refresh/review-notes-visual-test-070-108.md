# Review notes — lane visual-test-070-108 (wave 51 label-chrome baseline refresh)

Scope: 31 stems × 3 platforms = 93 sheets under
`tools/titan/runs/wave51-labels/visual-test/review/` with stem numbers 070–108,
excluding the 8 stems reviewed by the other lane (081, 082, 089, 093, 096, 098, 099, 106).

Verdict: **93 / 93 ok. No defects.**

## Method

1. Read every sheet with the Read tool (all 93), plus a per-stem 3× composite of the
   left 220 capture px of all three panes × three platforms (scratchpad only), and the
   full fresh+diff panes for the wider components (079, 094, 108).
2. Numeric cross-check on every sheet's diff pane (pane 3, x ≥ 2364 in sheet space,
   sampled at 1/3 to recover capture pixels), using the same glyph set P the sheet
   builder computes from `tools/visual/block-font.json` at origin (8,6):
   - green == |P| on all 93 (every expected label pixel appeared); magenta == 0 on all 93.
   - no red anywhere in band rows 0..14 outside P (no doubled / mispositioned / extra-glyph ink).
   - fresh-pane colour at every P position is exactly (174,174,180) on Android, iOS and web
     for every stem — the composited-byte contract, no LSB drift, no dimmer/brighter platform.
   - every red bounding box is exactly 7 rows tall, and red is a **strict subset** of P
     translated to the old in-box origin (`red_outside_Pold = 0` on all 93). Old origins
     observed: (34,32) for padding-10 stems, (24,22) for 100, (39,37) 079, (44,42) 083/087/088/108,
     (59,47) 086, (48,34) 090/091, (36,26) 092, (41,35) 094, (38,28) 095, (45,43) 097,
     (54,37) 101, (29,27) 103, (36,34) 105, (49,47) 107.
   - no (174,174,180)-coloured ink anywhere in the fresh pane outside P, and none at the old
     footprint (no residual in-box label).
   - source PNG dimensions fresh == committed on all 93 (S1 holds).
3. Stems where red < |P| are all explained by the OLD label having been clipped by the
   element's own box (overflow / blend layer / rounded corner), so those old-label pixels
   never carried ink and had nothing to change: 080 Android ("…ELLIPS"), 085 all three
   (Android "OVERFLOW H", iOS/web "OVERFLOW HIDDE"), 087/088 Android ("BLEN"), 091 Android/iOS
   (1 px), 100 all three ("…PERCENTRADIU", 15 px under the corner), 108 Android/iOS ("EDGE").

## Observations (not defects, recorded for the refresh owner)

- Android and iOS sheets are byte-identical for 070, 071, 072, 075, 076, 077, 078, 084, 102,
  103, 104; 079 Android == web; 087 iOS == web; 084 and 102 identical ×3. The source PNGs differ
  in bytes but decode to identical pixels. Explained by the fixture: these components carry no
  text content (an empty padded box, or nothing visible for visibility:hidden / opacity:0), so
  the deterministic block-glyph label is the only ink that could differ. Not a sheet-builder
  mix-up (checked `images/<platform>/` and `tools/visual/baseline/` per platform).
- Pre-existing per-platform divergences visible identically in committed and fresh panes
  (unchanged, so not refresh defects): the old label clipping differences listed above;
  107 Android paints a faint dark square halo behind the inset-shadow rounded shape.
- 077 / 078 (text-shadow stems): the old in-box label carried no visible shadow/glow on any
  platform (red == |P| exactly), so nothing beyond the glyph footprint changed.
