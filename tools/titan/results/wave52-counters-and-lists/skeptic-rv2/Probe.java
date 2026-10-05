// rv2-l6 re-verifier probe: drives the COMPILED BorderSideApplier bytecode
// (the class Gradle just tested) by reflection — no tree edit — and prints the
// ink rows/cols of the two-line far-edge paths for the skeptic's probe rows,
// next to an independent transcription of dev (5d9ed628: extent - inset) and
// of the pre-R1 per-line clamp (max(inset, extent - inset)).
import java.lang.reflect.*;
import java.util.*;

public class Probe {
    static Object INST; static Class<?> A, SIDE, OFFSET, SIZEKT;
    static Method doubleLines, grooveLines, sideGeom, farEdge, innerEdge, doubleGeom;

    static Method find(String prefix) {
        for (Method m : A.getDeclaredMethods()) if (m.getName().startsWith(prefix)) { m.setAccessible(true); return m; }
        throw new RuntimeException("no method " + prefix);
    }
    static Object side(String n) { for (Object o : SIDE.getEnumConstants()) if (o.toString().equals(n)) return o; throw new RuntimeException(n); }
    static long size(float w, float h) throws Exception { return (long) SIZEKT.getMethod("Size", float.class, float.class).invoke(null, w, h); }
    static float[] xy(Object boxedOffset) throws Exception {
        long packed = (long) OFFSET.getMethod("unbox-impl").invoke(boxedOffset);
        float x = (float) OFFSET.getMethod("getX-impl", long.class).invoke(null, packed);
        float y = (float) OFFSET.getMethod("getY-impl", long.class).invoke(null, packed);
        return new float[]{x, y};
    }
    // Butt-capped stroke of width w centred on c: integer cells [floor(c-w/2), ceil(c+w/2)-1].
    static TreeSet<Integer> ink(float c, float w) {
        TreeSet<Integer> s = new TreeSet<>();
        for (int i = (int) Math.floor(c - w / 2f); i <= (int) Math.ceil(c + w / 2f) - 1; i++) s.add(i);
        return s;
    }
    static boolean horiz(String s) { return s.equals("TOP") || s.equals("BOTTOM"); }
    // cross-axis centre of a side line from the REAL bytecode
    static float cross(Object pair, String s) throws Exception {
        kotlin.Pair<?, ?> p = (kotlin.Pair<?, ?>) pair;
        float[] a = xy(p.getFirst()), b = xy(p.getSecond());
        float ca = horiz(s) ? a[1] : a[0], cb = horiz(s) ? b[1] : b[0];
        if (ca != cb) throw new RuntimeException("not axis aligned");
        return ca;
    }
    static List<Float> realDouble(String s, float w, float bw, float bh) throws Exception {
        List<?> l = (List<?>) doubleLines.invoke(INST, side(s), w, size(bw, bh));
        List<Float> out = new ArrayList<>(); for (Object p : l) out.add(cross(p, s)); return out;
    }
    static List<Float> realGroove(String s, float w, float bw, float bh) throws Exception {
        kotlin.Pair<?, ?> p = (kotlin.Pair<?, ?>) grooveLines.invoke(INST, side(s), w, size(bw, bh));
        return List.of(cross(p.getFirst(), s), cross(p.getSecond(), s));
    }
    // independent transcriptions
    static float dev(float e, float inset) { return e - inset; }
    static float preR1(float e, float inset) { return Math.max(inset, e - inset); }
    static float mine(float e, float w, float inset) { return Math.max(e, w) - inset; }

    static String fmt(List<Float> cs, float sw) { StringBuilder b = new StringBuilder("["); for (float c : cs) b.append(ink(c, sw)); return b.append("]").toString(); }

    static void probe(String id, String kind, String s, float w, float bw, float bh) throws Exception {
        float e = horiz(s) ? bh : bw;
        boolean dbl = kind.equals("double");
        float sw = dbl ? w / 3f : w / 2f;
        float[] insets = dbl ? new float[]{w / 6f, w - w / 6f} : new float[]{w / 4f, w / 4f + w / 2f};
        List<Float> real = dbl ? realDouble(s, w, bw, bh) : realGroove(s, w, bw, bh);
        List<Float> d = new ArrayList<>(), p = new ArrayList<>(), m = new ArrayList<>();
        for (float i : insets) { d.add(dev(e, i)); p.add(preR1(e, i)); m.add(mine(e, w, i)); }
        System.out.printf("%-3s %-6s %-6s w=%-4s box=%sx%s | FIXED(bytecode) outer,inner=%s | dev=%s | preR1=%s | myFormula==bytecode %s%n",
            id, kind, s, w, bw, bh, fmt(real, sw), fmt(d, sw), fmt(p, sw), m.equals(real));
    }

    public static void main(String[] a) throws Exception {
        A = Class.forName("com.styleconverter.runtime.borders.sides.BorderSideApplier");
        SIDE = Class.forName("com.styleconverter.runtime.borders.sides.BorderSideApplier$Side");
        OFFSET = Class.forName("androidx.compose.ui.geometry.Offset");
        SIZEKT = Class.forName("androidx.compose.ui.geometry.SizeKt");
        INST = A.getField("INSTANCE").get(null);
        doubleLines = find("doubleLines"); grooveLines = find("grooveRidgeLines"); sideGeom = find("sideGeometry");
        farEdge = find("farEdgeBandCentre"); innerEdge = find("innerEdgeStrokeCentre"); doubleGeom = find("doubleGeom");

        System.out.println("== skeptic probe rows (rows/cols per line, OUTER first) ==");
        probe("p1", "double", "BOTTOM", 3f, 100f, 3f);
        probe("p2", "double", "BOTTOM", 6f, 100f, 6f);
        probe("p3", "double", "BOTTOM", 6f, 100f, 9f);
        probe("p4", "double", "END", 3f, 3f, 40f);
        probe("p5", "groove", "BOTTOM", 4f, 100f, 4f);
        probe("p6", "groove", "BOTTOM", 4f, 100f, 0f);
        probe("p7", "double", "BOTTOM", 3f, 100f, 2f);
        probe("c1", "double", "BOTTOM", 3f, 100f, 50f);
        probe("c2", "double", "BOTTOM", 3f, 100f, 0f);
        // extra probes not in the builder's pins
        probe("x1", "groove", "END", 4f, 4f, 40f);
        probe("x2", "groove", "END", 4f, 0f, 40f);
        probe("x3", "double", "END", 6f, 9f, 40f);
        probe("x4", "groove", "BOTTOM", 10f, 100f, 12f);
        probe("x5", "double", "BOTTOM", 14f, 100f, 20f);
        probe("x6", "double", "BOTTOM", 9f, 100f, 5f);
        probe("x7", "groove", "BOTTOM", 2f, 100f, 1f);
        probe("x8", "double", "TOP", 6f, 100f, 0f);
        probe("x9", "groove", "START", 4f, 0f, 40f);

        // == invariant sweep over the real bytecode ==
        int n = 0, bad = 0, devDiff = 0;
        float[] ws = {2f, 2.5f, 3f, 3.3f, 3.75f, 4f, 5f, 6f, 7f, 9f, 10f, 12.5f, 14f, 20f};
        for (float w : ws) for (int k = 0; k <= 400; k++) {
            float e = k * 0.125f;                      // 0 .. 50 px, 1/8 px steps
            for (String s : new String[]{"BOTTOM", "END"}) {
                float bw = s.equals("END") ? e : 100f, bh = s.equals("BOTTOM") ? e : 100f;
                for (String kind : new String[]{"double", "groove"}) {
                    if (kind.equals("double") && w < 3f) continue;
                    boolean dbl = kind.equals("double");
                    float sw = dbl ? w / 3f : w / 2f;
                    float[] ins = dbl ? new float[]{w / 6f, w - w / 6f} : new float[]{w / 4f, w / 4f + w / 2f};
                    List<Float> r = dbl ? realDouble(s, w, bw, bh) : realGroove(s, w, bw, bh);
                    n++;
                    float far = Math.max(e, w);
                    // (1) outer line hugs the far edge: its stroke ends exactly at max(e, w)
                    boolean ok = r.get(0) + sw / 2f == far || Math.abs(r.get(0) + sw / 2f - far) < 1e-5f;
                    // (2) never above/left of the box: inner stroke starts >= 0 (band fits in [0, far))
                    ok &= r.get(1) - sw / 2f >= -1e-5f;
                    // (3) order: outer is farther than inner (no mirror) and the spacing is the band geometry
                    ok &= r.get(0) > r.get(1);
                    ok &= Math.abs((r.get(0) - r.get(1)) - (ins[1] - ins[0])) < 1e-5f;
                    // (4) e >= w → bit-identical to dev
                    if (e >= w) for (int i = 0; i < 2; i++) if (r.get(i) != dev(e, ins[i])) { devDiff++; ok = false; }
                    if (!ok) { bad++; if (bad < 10) System.out.println("BAD " + kind + " " + s + " w=" + w + " e=" + e + " " + r); }
                }
            }
        }
        System.out.println("== sweep: cases=" + n + " invariant failures=" + bad + " dev-mismatch(e>=w)=" + devDiff);

        // == single stroke: sideGeometry BOTTOM/END unchanged vs innerEdgeStrokeCentre, and farEdge(e,w,w/2) == innerEdge(e,w/2)
        int single = 0, singleBad = 0;
        for (float w : ws) for (int k = 0; k <= 400; k++) {
            float e = k * 0.125f; single++;
            float fe = (float) farEdge.invoke(INST, e, w, w / 2f);
            float ie = (float) innerEdge.invoke(INST, e, w / 2f);
            kotlin.Triple<?, ?, ?> t = (kotlin.Triple<?, ?, ?>) sideGeom.invoke(INST, side("BOTTOM"), w, size(100f, e));
            float sg = xy(t.getFirst())[1];
            if (fe != ie || sg != ie) singleBad++;
        }
        System.out.println("== single stroke: cases=" + single + " mismatches=" + singleBad);
    }
}
