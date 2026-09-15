# wave50-B12 — the pass column, hand-verified; and a displacement-aware successor to the novel-ink veto

Discharges `docs/BACKLOG.md` **next-wave obligations #3 and #4**. Zero runtime
edits. Everything here is re-derivable from the tree: the draw is a committed
script, the verdicts are a committed file, the instrument is a committed module
with a mutation test.

Contents of this directory:

| file | what it is |
|---|---|
| `draw-sample.mjs` | the committed draw — re-running it reproduces the identical 100 cells |
| `handverdicts.json` | the 100 per-cell verdicts, with the headline rate and its interval |
| `veto-measurement.json` | the probe's output on those 100 cells, joined to the verdicts |
| `inject-seam.patch` | the (unapplied) wiring seam for a later wave — see §5 |
| `README.md` | this file: method, seed, rate, mechanism table, pre-registration, result |

The instrument itself is `tools/titan/degenerate-veto-probe.mjs`
(+ `tools/titan/degenerate-veto-probe.test.mjs`).

---

## 1. Why this lane existed

`docs/BACKLOG.md` obligation #3: *"The pass column is ~35% degenerate and that
is the campaign's biggest known problem — and its per-cell evidence is LOST."*
Wave-49 lane I1 opened 78 randomly-drawn PASSING cells, called 27 visibly
wrong, and left the verdict file in a session scratchpad that no longer exists
(retro A10#0, A2#1). The 27/78 figure has been unverifiable ever since.

So the first act here is not instrument design. It is re-drawing the sample,
opening every capture against its frozen reference, and **committing the
verdicts**.

## 2. Method

**Population.** Every PASSING scored cell of `tools/titan/runs/wave49-final`:
all 30 sections, the scorer idiom verbatim (`typeof ssim === "number" &&
!scoreExcluded`), `wptPass === true`. That is **3 344 cells** out of 4 111
scored — the same 4 111 the corpus-v6.15 snapshot reports.

**Draw.** Simple random sample **without replacement**, n = 100, by a partial
Fisher–Yates shuffle over a deterministically-ordered population using
mulberry32 seeded with **20260914**. `node
tools/titan/results/wave50-B12/draw-sample.mjs --run wave49-final` reproduces
it exactly.

SRS — not stratification, not any rule over an image metric — is the point.
Obligation #4 needs a defect set *"NOT selected by any rule correlated with the
check's own bars"*; SRS is the only draw uncorrelated with every candidate
metric by construction, and the only one whose rate carries an honest binomial
interval. The cost is coverage: the draw lands in **28 of the 30 sections**
(`css-text-decor` and `css-ui` drew zero). Platform split fell out as web 36 /
iOS 38 / Android 26.

**Looking.** Every cell was rendered as a `[capture | reference]` composite at
full size, with a **magnified crop of the divergent bounding box** stacked
beneath it (integer upscale to ~370 px wide), and every one was opened. 23 of
them turned out to be pixel-identical to their reference within the pipeline's
own 8/255 tolerance, which settles those by construction; the other 77 were
read.

The 100 cells are only **84 distinct pictures** — the corpus repeats a small
number of boilerplate page shapes, and seven groups of cells are byte-identical
in *both* capture and reference (e.g. six cells share one picture). Twins carry
the twin's verdict and say so in their note. This matters for the interval: the
effective sample is smaller than 100, so the interval below is, if anything,
optimistic.

**Backing every verdict with a number.** Eyeballing a 390×600 composite is
error-prone, and it caught me out twice: I first read cell 032 as "a grey bar
present in the capture, collapsed in the reference" and cell 088 as "columns
shifted" — both wrong. A per-row ink-extent diff and a per-colour bounding-box
dump settled them (032 is a *missing 2 px hairline*; 088's marks are
extent-identical and only its prose antialiases differently). Every note in
`handverdicts.json` therefore quotes a measured quantity, not an impression.

**Severity rule** (fixed before scoring, applied mechanically):

- **major** — at least one of: (a) a mark present in one image and absent in
  the other, ≥ 4 px of ink; (b) a mark's *colour class* differs (not a weight
  or antialiasing difference); (c) a mark is displaced ≥ 4 px, or its size
  differs ≥ 4 px on an axis; (d) the text differs — different glyphs, different
  line breaking, a character or word added or dropped.
- **minor** — differences confined to antialiasing, font weight or face
  substitution, gradient interpolation, or sub-4 px geometry.
- **identical** — zero divergent pixels (a machine fact, cross-checked: the
  generator refuses to write the file if "identical" and "zero divergent
  pixels" ever disagree).

The headline counts **major only**. The minor bucket is reported separately and
is *not* nothing — it is dominated by one systematic harness artifact (below).

## 3. The headline

> **35 of 100 randomly-drawn PASSING cells are visibly wrong renders.**
> Rate 35.0 %, **95 % Wilson interval 26.4 % – 44.7 %**.

Two cells (032, 076) sit on the severity rule's 4 px / 4 px boundary and are
flagged `borderline`; dropping both gives **33 %, CI 24.6 % – 42.7 %**. The
conclusion does not turn on them.

Wave-49's lost figure was 27/78 = **34.6 %**, which sits almost exactly on this
estimate and comfortably inside the interval. **The campaign's "~35 %
degenerate" headline reproduces** — this time from a committed sample with
committed per-cell verdicts.

| severity | cells |
|---|---:|
| major (wrong render) | **35** |
| minor (visible but cosmetic) | 42 |
| identical (zero divergent pixels) | 23 |

| platform | sample | wrong | rate |
|---|---:|---:|---:|
| web | 36 | 11 | 30.6 % |
| iOS | 38 | 12 | 31.6 % |
| Android | 26 | 12 | 46.2 % |

The per-platform rates are within each other's intervals at n ≈ 30; the column
totals (web 87.4 % / iOS 79.1 % / Android 77.5 %) are *not* corrected by these
rates into anything trustworthy, because a degenerate pass and a real pass are
indistinguishable to the scorer by construction. What the table does say is
that no column is clean.

| verdict | cells |
|---|---:|
| wrong-colour | 11 |
| wrong-text | 8 |
| wrong-position | 7 |
| wrong-size | 5 |
| missing-content | 4 |

**Two further findings from the same pass**, both about the denominator rather
than the numerator:

- **Two cells cannot fail.** 053 is blank-on-blank — the reference paints
  nothing, the capture paints nothing, and a runtime that rendered *nothing at
  all* would score 1.0000. 057's reference paints only the test's prose line;
  its pass condition is the absence of a mark. Both are marked
  `"flags": ["absence-only"]`. At 2 / 100 that is ~67 cells of the passing
  column whose pass carries no information.
- **The minor bucket has one dominant systematic cause**, not many small ones:
  in 18 of the 42 minor cells the reference emphasises a fragment of its own
  prose and the capture renders that fragment at regular weight. It is a
  harness-typography artifact, identical across platforms, and it is why the
  minor bucket must *not* be folded into the headline.

**Dating caveat, stated up front.** `wave49-final`'s captures predate the
retrospective (PR #137), which changed rendering in transforms, flex/sizing,
typography, effects and iOS borders/clip *without* a device gate. Up to six of
the 35 wrong cells sit in families that overlap those changes — 074
(transforms), 006/007/054/064 (inline typography), 082 (effects/backdrop) — so
their verdicts describe the tree as it was captured, not necessarily the tree as
it is now. The *rate* is unaffected in expectation only if those changes were
net-neutral, which is exactly what wave 50's opening gate is for. Everything
outside those five families (29 of 35) is untouched by PR #137.

## 4. Mechanism families — the ranked table for wave 51

Each wrong cell is assigned **one** primary family (secondary defects are in
`handverdicts.json`'s `defects` array). Ranked by size.

| # | family | cells | where they sit | owning lane |
|---|---|---:|---|---|
| F1 | **Out-of-flow static position** — the abspos child's static position inside a grid/flex/block container is wrong by 5–25 px, or its containing block has its axes transposed | **7** | 000, 058, 076 (web) · 020, 032, 036 (iOS) · 045 (Android) | `layout/position` + the grid/flex abspos paths on all three runtimes |
| F2 | **The test's own failure-indicator ink reaches the canvas** — the element the test expects covered/clipped/sized-away is painted | **7** | 031 (web) · 038, 046, 068 (iOS) · 012, 091, 098 (Android) | split: orthogonal-flow/available-size sizing (012, 031, 068); fragmentation (046, 091); anchor-position (038); float/clear (098) |
| F3 | **Line-clamp / block-ellipsis** — the clamp is not applied, the ellipsis is not inserted, or it clamps a character early | **5** | 004, 083, 092 (web) · 037 (iOS) · 026 (Android) | `overflow/line-clamp` |
| F4 | **Inline run shaping** — runs merged or split wrongly: a word dropped, inter-run spaces lost, a word set one glyph per line, a space invented | **4** | 007 (web) · 006, 054, 064 (iOS) | typography / inline (the manifest's own `inline-run-merged` lossy class) |
| F5 | **Computed colour never reaches the text run** — a colour arriving via cascade, selector match or pseudo-element is dropped and the text paints in the inherited class | **3** | 087 (web) · 052, 055 (Android) | cascade / selectors / pseudo |
| F6 | **Clip/mask reference-box origin** — the mark is displaced by exactly the padding amount (8 px on both axes), ink mass identical | **2** | 099 (web) · 028 (Android) | masking |
| F7 | Block/float vertical placement (mark 10 px low, ink identical) | 1 | 011 (web) | layout/position |
| F8 | Gap-decoration row overruns the page padding | 1 | 018 (Android) | gaps / sizing |
| F9 | Manual break opportunity's hyphen not painted | 1 | 041 (Android) | typography |
| F10 | 3D perspective transform not applied (mark drawn axis-aligned, ~30 % small) | 1 | 074 (Android) | transforms |
| F11 | Intrinsic sizing under a calc-size track (mark 60 px wide, should be 100) | 1 | 090 (iOS) | sizing |
| F12 | Transition-group content dropped (one of two marks absent, the other in its place) | 1 | 061 (Android) | view-transitions |
| F13 | Filtered band's luminance inverted (52,52,52 where the reference is 204,204,204) | 1 | 082 (iOS) | effects / backdrop |

**Cross-cutting observation worth one lane on its own.** Three cells in two
different families (018 F8, 026 F3, 083 F3) share one visible signature: **the
block runs exactly 16 px wider than the reference, painting into the page's
right padding all the way to the canvas edge** (`capInk.x1 == 389`,
`refInk.x1 == 373`). Two platforms, three sections. If that is one root cause,
it is the cheapest three cells in this table.

**F1 + F6 + F7 = 10 of 35 cells are pure geometry with correct ink** — the mark
is the right shape and the right colour, in the wrong place. That is the single
largest thing the corpus's scorer cannot see, and it is also, as §5 shows, the
class a displacement-aware instrument is structurally worst at.

---

## 5. Obligation #4 — the successor instrument

`tools/titan/degenerate-veto-probe.mjs`, shipped **DISARMED**
(`TITAN_DEGENERATE_VETO=1` to arm; it is not armed, see §5.4).

### 5.1 The design

The disarmed novel-ink veto asks: *of the capture's paint inside the
disagreement, how much is in a colour the reference never uses?* — and divides
by all of that paint. Its measured miss mechanism (a) is renders that are
**wrong-colour AND displaced**: the divergent region then fills with the
reference's own palette, the numerator collapses, and the veto stays silent.
Obligation #4 names the fix: a **displacement-aware denominator** — exclude the
divergent pixels a rigid translation of reference ink already explains.

This probe implements exactly that, symmetrically:

- The **canvas** is *derived* as the reference's modal colour, never asserted —
  so nothing in the module names a hue, a channel or a test.
- A divergent pixel where the **capture painted** is *explained* when some
  translation of at most **R = 4 px** carries reference **ink of the same
  colour** (within the shared 8/255 tolerance) onto it.
- Symmetrically, a divergent pixel where the **reference painted** is *dropped*
  when no translation of at most R carries capture ink of that colour onto it.
- `unexplainedInkFraction` = unexplained ÷ divergent-capture-ink;
  `refDroppedFraction` = dropped ÷ divergent-reference-ink; both also reported
  as a percentage of the frame.

R = 4 px is four times the largest rasteriser jitter measured in this very
sample (the byte-identical-picture groups differ by ≤ 1 px of row extent).

### 5.2 Pre-registration (written before the probe was run on the sample)

**Rule v1 — FIRE iff the pair is judgeable AND**

```
( unexplainedInkFraction >= 0.50  AND  unexplainedInkPct >= 0.10 )
OR
( refDroppedFraction     >= 0.50  AND  refDroppedPct     >= 0.10 )
```

Both bars are **novel-ink's own published constants**
(`NOVEL_INK_FRACTION_MIN` 0.5, `NOVEL_INK_ABS_MIN_PCT` 0.1 %), reused verbatim
so the successor is comparable to the instrument it replaces and cannot be
accused of being tuned to this sample.

**Decision rule, fixed in advance:** ship armed only if **recall ≥ 95 %** on the
35 hand-`major` cells **and false-positive rate ≤ 2 %** on the 65 hand-`correct`
cells. Otherwise ship disarmed and say so.

**Prediction, recorded before measuring:** the rule will **fail**. Expected
fires 18–22 of 35 (recall 51–63 %); expected false positives 0–3 of 65
(0–4.6 %). Named predicted misses and why:

- **under the mass bar** (novel-ink's own miss mechanism (b), inherited
  deliberately rather than tuned away): 087 (22 px), 098 (100 px = 0.043 % of
  frame), 092 (46 px), 032 (24 px), 037, 054;
- **explained by construction** — a pure rigid translation is the one thing the
  model is built to excuse, so the whole of F6 and most of F1/F7 must be
  missed: 099, 028, 011, 000, 058, 076, 020.

That second bullet is the honest cost of obligation #4's named mechanism: it
buys recall on *recoloured-and-displaced* renders by spending recall on
*displaced-only* ones. It is worth writing down before the numbers arrive.

### 5.3 Measured — once, on the committed sample

Ran once, on the 100 committed cells:
`node tools/titan/degenerate-veto-probe.mjs --cells <sample> --json veto-measurement.json`
(1.2 s, no devices). Full per-cell output is `veto-measurement.json`.

|  | hand: wrong (35) | hand: correct (65) |
|---|---:|---:|
| probe fires | **13** | **1** |
| probe silent | 22 | 64 |

- **Recall 37.1 %** (13/35), 95 % Wilson **23.2 % – 53.7 %**.
- **False-positive rate 1.5 %** (1/65), 95 % Wilson **0.3 % – 8.2 %**.
- Precision when it fires **92.9 %** (13/14).

> **VERDICT: the pre-registered rule FAILS.** The recall bar was 95 %; the
> measurement is 37.1 % and the *upper* end of its interval is 53.7 %, so the
> failure is conclusive at n = 35, not a sample-size artifact. The
> false-positive bar (≤ 2 %) is met at the point estimate, but with one FP in 65
> it is *not established* — the interval reaches 8.2 %. **Shipped disarmed.**

My pre-registered prediction was 51–63 % recall; the truth is 37.1 %, below my
own range. I was wrong in the optimistic direction, which is the direction that
matters. Scoring the prediction honestly: **11 of the 13 cells I named as
misses did miss**, and the two 8 px rigid offsets (099, 028) fired instead,
landing on the 0.50 fraction bar rather than under it. The *mechanism* I
attributed was right for about half — 087 and 092 missed on the mass bar as
predicted, and 011/000/076 on the displacement model as predicted, but
098/032/037/054 had ample mass and missed on the fraction bar, while 020 and 083
had ample fraction and missed on the mass bar. I had the cells; I had the
reasons only half right.

**Why it misses — the structural finding, and the most useful thing this lane
produced.** Of the 22 missed defects, **15 bind on the FRACTION bar, not the
mass bar**: they are big, obvious, well over 0.1 % of the frame, and still
score 0.20–0.49 unexplained. Read that again in the model's own terms — *for a
typical wrong render in this corpus, the majority of the disagreeing pixels ARE
explained by a small rigid translation of reference ink.* A wrong render is
mostly a locally-explicable render with a minority of genuinely novel paint.
So the displacement-aware denominator does not separate the classes; it
sharpens precision and costs recall.

Concretely: 055 is a colour class missing from two of eight text lines
(0.71 % of the frame — seven times the mass bar) at fraction 0.49. 068 paints a
whole extra block in the test's failure class (0.47 % of frame) at fraction
0.42. 046, 031, 041, 064, 098, 026 are the same shape. Only four misses bind on
the mass bar alone (087, 092, 083, 020) — that is novel-ink's known miss
mechanism (b), inherited on purpose rather than tuned away.

**Post-hoc sweep — reported, NOT adopted** (the whole point of pre-registering
is that a rule chosen after seeing this table is not a measurement). Every
entry is in `veto-measurement.json`:

| rule | recall | FP | precision |
|---|---:|---:|---:|
| **shipped v1** (frac ≥ .5 **and** mass ≥ .1 %) | 37.1 % | 1.5 % | 92.9 % |
| fraction ≥ .5 **and** mass ≥ .01 % | 48.6 % | 1.5 % | 94.4 % |
| fraction ≥ .5 alone | 48.6 % | 4.6 % | 85.0 % |
| mass ≥ .3 % alone | 42.9 % | 10.8 % | 68.2 % |
| fraction ≥ .25 **and** mass ≥ .1 % | 68.6 % | 21.5 % | 63.2 % |
| mass ≥ .1 % alone | 80.0 % | 49.2 % | 46.7 % |
| fraction ≥ .2 **and** mass ≥ .01 % | 100.0 % | 61.5 % | 46.7 % |

**No operating point of this metric family reaches 95 % recall at a usable
false-positive rate.** The two rows that reach high recall have precision 46.7 %
— against a base rate of 35 %, that is barely better than firing at random.
The conclusion for wave 51 is not "retune the bars": it is that *unexplained
mass fraction* is the wrong axis, and the honest recommendation is to stop
looking for a single scalar veto over the pass column and instead spend the
same effort on the mechanism families in §4, which are small, named, and
fixable.

**Where the metric IS worth keeping.** Precision 92.9 % means a fire is almost
always a real defect. Stamping the block on every diff for triage — the shape
`novelInk` already has — turns it into a free worklist: on this sample it would
have surfaced 13 real degenerate passes with one gradient false alarm. That is
the use the seam patch enables, and it needs no arming.

**Known limitation, named not hidden.** The single false positive (023) is a
smooth colour ramp: no small translation of the reference carries an
interpolated sample onto its neighbour, so a correct gradient reads as
unexplained paint. `novel-ink.mjs` guards the same class with a palette-coverage
precondition; this probe has no equivalent. Adding one is the obvious first
improvement — and deliberately *not* done here, because adding it after seeing
which cell it would fix is tuning, not design.

**Denominator honesty.** The 65 "correct" cells include 23 that are
pixel-identical to their reference and therefore can never fire. Among the 42
cells that genuinely differ, the false-positive rate is 1/42 = **2.4 %**, not
1.5 %. Both numbers are in `veto-measurement.json`.

### 5.4 Wiring

The probe is a pure module, and the seam that consults it **IS APPLIED in this
tree** — `tools/titan/inject-wpt-block.mjs` carries it (`computeDegenerate` on
every `<platform>-ref` diff at line ~566, `degenerateVetoFailed` stamped at the
two diff sites, `degenerateVetoActive()` passed as `computeWptPass`'s seventh
argument). This paragraph used to say the seam was "not wired… committed here
unapplied"; that was true when B12 wrote it and is **false now** (skeptic S5
defect 7, rewritten by wave-50 fix lane F3 and again by F6 so the claim reads
correctly rather than being contradicted by a footnote).

**It ships DISARMED.** The block is computed and stored for triage on every
cell, but it can only change a verdict when `TITAN_DEGENERATE_VETO=1` — the
same shipped-disarmed shape the novel-ink veto already has. Verified on this
tree: `degenerateVetoActive(true)` is `false` with the variable unset and
`true` with `TITAN_DEGENERATE_VETO=1`, and a full re-score of the `css-tables`
section through the live `diffComposedVsRef` reproduces the manifest's pass
column on **144/144 cells** with zero drift on any stored metric key (the only
deltas are the three `scoreExcluded` rows of `absolute-tables-006`, whose
manifest `wptPass` is a downstream `null` stamp, not a scorer difference).

**Pinned**, so the disarm cannot rot: `degenerate veto is OFF unless
TITAN_DEGENERATE_VETO=1` and `computeWptPass: the seventh argument is a real
unconditional veto` in `tools/titan/inject-wpt-block.test.mjs` (fix lane F3),
mirroring the novel-ink pair and mutation-proved against `isArmed()` forced
true.

**`inject-seam.patch` stays in this directory as the PROVENANCE RECORD** — the
exact diff that was applied, against `inject-wpt-block.mjs` at sha
`517642a43a41f481ef89c15163828141147c3bd1`. It is no longer something to apply.

**Cost, measured — fine for the gate** (skeptic S5's
`degenerate-perf-probe.mjs.txt`, re-run by fix lane F6 on this tree):
`computeDegenerate` costs **~13.8 ms per cell** on S5's measurement; F6's own
timing over every one of the 144 `css-tables` cells (capture + frozen ref,
white-padded to a common canvas) averages **6.8 ms**, worst **35 ms** on that
section. The **worst cell measured anywhere** is the 390×9470
`css-text-decor/text-decoration-inset-025` android capture at **985 ms**
(3 692 121 divergent ink px) — ~1 s, and 98× `computeNovelInk` on the same
pair. End to end, a 144-cell `css-tables` re-score takes **24.4 s** (S5) /
**25.1 s** (F6, same probe, same tree). Nothing here needs optimising while the
seam is dark; if a later wave ARMS or widens it, the first optimisation is an
early-out once `divergentInkPx` already exceeds the threshold, since the
per-cell cost tracks that count almost exactly.

---

## 6. Reproduce

Everything here is device-free and runs in seconds. From the repo root, with
`tools/titan/runs/wave49-final` present (it is gitignored; regenerate it with
the corpus-v6.15 snapshot's own `reproduce` recipe):

```bash
# the draw — prints the identical 100 cells, seed and all. Write the
# regeneration BESIDE the committed artifact and diff it; the draw's shape is
# {runDir, seed, n, populationSize, sample} — a STRICT SUBSET of
# handverdicts.json, which also carries the hand verdicts — so `--json
# handverdicts.json` would delete this lane's evidence, not reproduce it.
node tools/titan/results/wave50-B12/draw-sample.mjs --run wave49-final \
  --json tools/titan/results/wave50-B12/draw.regen.json
node -e "const a=require('./tools/titan/results/wave50-B12/draw.regen.json').sample, \
  b=require('./tools/titan/results/wave50-B12/handverdicts.json').cells; \
  console.log(a.length===b.length && a.every((c,i)=>c.test===b[i].test&&c.platform===b[i].platform))"

# the instrument, on the committed verdict file (no run dir needed —
# --cells accepts handverdicts.json directly)
node tools/titan/degenerate-veto-probe.mjs --cells tools/titan/results/wave50-B12/handverdicts.json

# the instrument, over a whole run — same rule: the committed
# veto-measurement.json is the CURATED measurement over the 100-cell sample
# (bars, confusion, Wilson intervals, `rows`), not a whole-run sweep, so it is
# the diff target, never the write target.
node tools/titan/degenerate-veto-probe.mjs wave49-final \
  --json tools/titan/results/wave50-B12/run-sweep.regen.json

# the pins, including the mutation test
node --test tools/titan/degenerate-veto-probe.test.mjs
```

One ship-time consequence to hand over: `degenerate-veto-probe.test.mjs` adds
15 tests to the `node --test tools/visual/*.test.mjs tools/titan/*.test.mjs`
suite, which `tools/visual/doc-staleness-check.sh` pins against the count quoted
in `CLAUDE.md` and `docs/STATUS.md`. On this shared mid-wave tree the live count
is **2003** (2003 tests, 1999 pass, 0 fail, 4 skipped) against the documented
1979; other lanes contribute to that delta too, so the number to write in is
whatever the ship-time sweep derives, not this one.

Verified in this tree on 2026-09-14: the draw is byte-identical on re-run; the
probe's fire set is identical whether fed the sampler's output or the committed
verdict file; `degenerate-veto-probe.test.mjs` is 15 pass / 0 fail;
and `inject-seam.patch` — the provenance record of §5.4, not a pending change —
is the diff that was applied to `inject-wpt-block.mjs` at sha
`517642a43a41f481ef89c15163828141147c3bd1`, whose own suite (118 tests at B12's
time, plus F3's two seam pins since) passes with it in place. Both reproduce
commands above WRITE INTO THIS DIRECTORY: they regenerate the two committed
JSON artifacts in place, so a re-run either reproduces them byte for byte or
shows its own diff — there is no session-local `/tmp` path left to lose.

## 7. What this lane hands to wave 51

1. **Obligation #3 is discharged and its number stands.** 35 / 100, CI
   26.4–44.7 %. The evidence is `handverdicts.json`, in git, with a committed
   sampler. It should not be lost again.
2. **Obligation #4's named successor does not work as a veto**, and the reason
   is structural, not a threshold: for a typical wrong render in this corpus
   the *majority* of the disagreeing pixels are explained by a small rigid
   translation of reference ink. §5.3 has the post-hoc sweep showing no
   operating point of the family clears 95 % recall at usable precision. The
   recommendation is to stop looking for a scalar veto over the pass column.
3. **§4's ranked mechanism table is the alternative**, and it is small: 35
   wrong cells reduce to 13 families, of which the top six hold 28. F1
   (out-of-flow static position, 7 cells, all three platforms) and F2 (the
   test's own failure-indicator ink reaching the canvas, 7 cells) are worth a
   lane each; the 16 px page-padding overrun shared by 018/026/083 may be one
   root cause worth three cells.
4. **The absence-only class**: 2 / 100 sampled passing cells cannot fail
   (blank-on-blank, or a pass condition that is the absence of a mark).
   Extrapolated, ~67 cells of the passing column carry no information. That is
   a denominator problem the scorer cannot see and no veto can fix.
