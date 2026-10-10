// tools/titan/results/wave54-rtl-marker-bake/marker-probe-checks.mjs — the PURE half-A acceptance of marker-probe.mjs
// (window [W1]), split out so marker-probe.selftest.mjs can prove the checks bite WITHOUT Chromium: they must pass on
// an offline-baked M′ fixture and fail on the wave-53 li-owned shape. `check(ok, what)` is the caller's reporter.
export function checkBakedCounterSuffix(fixture, outcome, check, log = () => {}) {
  check(outcome.status === 'baked' && outcome.roots === 2 && outcome.runs === 10, 'baked — 2 roots, 10 runs');
  // The two RTL bake roots: `<ol>` components stamped by the bake.
  const roots = [];
  const walk = (c) => { for (const k of Object.values(c.children ?? {})) {
    if (k._tag === 'ol' && (k._lossyReasons ?? []).includes('baked-bidi-visual-order')) roots.push(k); walk(k); } };
  for (const c of Object.values(fixture.components)) walk(c);
  check(roots.length === 2, `2 RTL bake roots (found ${roots.length})`);
  const want = [['.', '1', '.', '2'], ['א.', 'ב.']];
  roots.forEach((root, r) => {
    log(root.id, JSON.stringify(root.properties));
    check(root.properties.padding === '0' && !Object.keys(root.properties).some((k) => k.startsWith('padding-')), `${root.id} padding: 0 alone (unit P)`);
    const kids = Object.values(root.children ?? {});
    const items = kids.filter((k) => k._tag === 'li'), marks = kids.filter((k) => !k._tag);
    check(kids.slice(0, 2).every((k) => k._tag === 'li') && items.length === 2, `${root.id} children start with its 2 items`);
    check(JSON.stringify(marks.map((m) => m._text)) === JSON.stringify(want[r]), `${root.id} root-owned marker runs ${JSON.stringify(want[r])} (got ${JSON.stringify(marks.map((m) => m._text))})`);
    for (const li of items) {
      log('   ', li.id, JSON.stringify(li.properties), JSON.stringify(li._lossyReasons ?? []));
      check(li.properties['list-style-type'] === 'none', `${li.id} list-style-type none`);
      check(!(li._lossyReasons ?? []).some((x) => x.startsWith('marker-')), `${li.id} no marker-* stamp (measured, not modelled)`);
      check(Object.keys(li.children ?? {}).length === 1, `${li.id} keeps exactly ONE child (its text run)`);
    }
    // Each marker run shares its item's row: top = item top + 2 (±1); x inside the ref's ink band (frame = bake + 16).
    for (const m of marks) {
      log('    marker run', JSON.stringify(m._text), JSON.stringify(m.properties));
      const x0 = 16 + parseFloat(m.properties.left), x1 = x0 + parseFloat(m.properties.width);
      check(x0 >= 131 && x1 <= 148, `${root.id} ${JSON.stringify(m._text)} frame x${x0.toFixed(2)}-${x1.toFixed(2)} ⊂ [131,148]`);
      const top = parseFloat(m.properties.top);
      check(items.some((li) => Math.abs(parseFloat(li.properties.top) + 2 - top) <= 1), `${root.id} ${JSON.stringify(m._text)} top ${top} = an item's top + 2 (±1)`);
    }
  });
}
