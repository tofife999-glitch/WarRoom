package warroom.data;

import java.util.ArrayList;
import java.util.List;

/**
 * A sector news entry: things that belong to no single war (alliances, factions losing or regaining
 * colonies, captures outside any war). Wars keep their own stories ({@link StoryEvent}). The kinds WAR,
 * PEACE and BATTLE only occur in War Room 0.2 saves, which kept one shared log; they are sorted into the
 * wars when such a save is read.
 * <ul>
 * <li>CAPTURE: a took colony text (size v0) from b.</li>
 * <li>ALLIANCE_FORMED: a and b formed alliance text. ALLIANCE_JOINED / ALLIANCE_LEFT: a joined / left
 * text. ALLIANCE_MERGED: text2 merged into text. ALLIANCE_DISSOLVED: text was dissolved.</li>
 * <li>ELIMINATED: a lost its last colony. ARRIVED: a has colonies (again).</li>
 * <li>0.2 only: WAR (a and b went to war); PEACE (v = days, start known, battles won by a and b,
 * colonies captured by a and b, ships lost by a and b); BATTLE (a beat b at text; v = ships lost by a
 * and b, fleet points lost by a and b).</li>
 * </ul>
 */
public final class LogEntry {
    public enum Kind {
        WAR("w"), PEACE("p"), CAPTURE("c"), BATTLE("b"),
        ALLIANCE_FORMED("af"), ALLIANCE_JOINED("aj"), ALLIANCE_LEFT("al"), ALLIANCE_MERGED("am"),
        ALLIANCE_DISSOLVED("ad"), ELIMINATED("e"), ARRIVED("n");

        /** Short code in the save; never change one. */
        final String code;

        Kind(String code) {
            this.code = code;
        }

        static Kind of(String code) {
            for (Kind k : values()) {
                if (k.code.equals(code)) return k;
            }
            return null;
        }

        public boolean isAlliance() {
            return this == ALLIANCE_FORMED || this == ALLIANCE_JOINED || this == ALLIANCE_LEFT
                    || this == ALLIANCE_MERGED || this == ALLIANCE_DISSOLVED;
        }
    }

    public final long time;
    public final Kind kind;
    public final String a;
    public final String b;
    public final String text;
    public final String text2;
    private final int[] v;

    public LogEntry(long time, Kind kind, String a, String b, String text, String text2, int... v) {
        this.time = time;
        this.kind = kind;
        this.a = a;
        this.b = b;
        this.text = text;
        this.text2 = text2;
        this.v = v == null ? new int[0] : v;
    }

    /** The i-th number, 0 if missing. */
    public int v(int i) {
        return i < v.length ? v[i] : 0;
    }

    public boolean involves(String factionId) {
        return factionId != null && (factionId.equals(a) || factionId.equals(b));
    }

    String encode() {
        List<String> f = new ArrayList<>();
        f.add(String.valueOf(time));
        f.add(kind.code);
        f.add(Codec.esc(a));
        f.add(Codec.esc(b));
        f.add(Codec.esc(text));
        f.add(Codec.esc(text2));
        f.add(Codec.ints(v));
        return Codec.join(f, Codec.FIELD);
    }

    /** Null if the line can't be read or comes from a newer version with an unknown kind. */
    static LogEntry decode(String line) {
        try {
            List<String> f = Codec.split(line, Codec.FIELD);
            Kind k = Kind.of(f.get(1));
            if (k == null) return null;
            return new LogEntry(Long.parseLong(f.get(0)), k, Codec.textAt(f, 2), Codec.textAt(f, 3), Codec.textAt(f, 4),
                    Codec.textAt(f, 5), Codec.ints(f.size() > 6 ? f.get(6) : ""));
        } catch (RuntimeException ex) {
            return null;
        }
    }

    /** A log line from a War Room 0.2 save. */
    static LogEntry decodeV2(String line) {
        try {
            String[] f = line.split("\\|", -1);
            Kind k = Kind.of(f[1]);
            if (k == null) return null;
            return new LogEntry(Long.parseLong(f[0]), k, Codec.unescV2(f[2]), Codec.unescV2(f[3]), Codec.unescV2(f[4]),
                    Codec.unescV2(f[5]), Codec.ints(f[6]));
        } catch (RuntimeException ex) {
            return null;
        }
    }
}
