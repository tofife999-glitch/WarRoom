package warroom.data;

import java.util.ArrayList;
import java.util.List;

/**
 * One step in a war's story. {@code side} is the record side that did it (0 = a, 1 = b, -1 = neither).
 * <ul>
 * <li>BEGAN: the war began (or tracking began, if the war was already on).</li>
 * <li>CAPTURE: side took colony {@code text} (size n0).</li>
 * <li>BATTLE: side won a large battle at {@code text}; n0/n1 ships lost by side/other, n2/n3 fleet points;
 * n4 = 1 for a fight Nexerelin settled far from you (reported, losses estimated).</li>
 * <li>BOMBARD: side bombarded colony {@code text}, which lost n0 sizes: from size n1 to size n2 (0: destroyed).
 * Events from 0.3.1 and before have only n0.</li>
 * <li>RAID: side's raid on {@code text} succeeded; n0 how: RAID_NEX, RAID_YOURS (your own raid, for
 * valuables or to disrupt) or RAID_TACTICAL (your tactical bombardment). 0.4.2 and before: always RAID_NEX.</li>
 * <li>REPELLED: side beat back an invasion of {@code text}.</li>
 * <li>GONE: side lost its last colony.</li>
 * <li>SHIFT: a turning point: side took the advantage on the war score, or (-1) the two were evenly matched
 * again.</li>
 * <li>ENDED: the war ended; n0 = WarRecord end reason, n1 = how peace came (WarRecord.PEACE_*; 0.4.2 and
 * before: always PEACE_UNKNOWN).</li>
 * </ul>
 */
public final class StoryEvent {
    public enum Kind {
        BEGAN("b"), CAPTURE("c"), BATTLE("f"), BOMBARD("x"), RAID("r"), REPELLED("v"), GONE("g"), ENDED("e"),
        SHIFT("s");

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
    }

    /** A raid in a Nexerelin offensive, your own raid, your tactical bombardment. */
    public static final int RAID_NEX = 0;
    public static final int RAID_YOURS = 1;
    public static final int RAID_TACTICAL = 2;

    public final long time;
    public final Kind kind;
    public final int side;
    public final String text;
    private final int[] n;

    public StoryEvent(long time, Kind kind, int side, String text, int... n) {
        this.time = time;
        this.kind = kind;
        this.side = side;
        this.text = text;
        this.n = n == null ? new int[0] : n;
    }

    public int n(int i) {
        return i < n.length ? n[i] : 0;
    }

    /** How much this event is worth keeping when a story is full; higher stays. */
    int weight() {
        switch (kind) {
            case BEGAN:
            case ENDED:
                return 100;
            case GONE:
                return 9;
            case CAPTURE:
                return 6 + Math.min(3, n(0) / 3);
            case BOMBARD:
            case SHIFT:
                return 5;
            case REPELLED:
                return 4;
            case RAID:
                return 3;
            case BATTLE:
                return Math.min(5, 1 + (n(2) + n(3)) / 150);
            default:
                return 1;
        }
    }

    String encode() {
        List<String> f = new ArrayList<>();
        f.add(String.valueOf(time));
        f.add(kind.code);
        f.add(String.valueOf(side));
        f.add(Codec.esc(text));
        for (int v : n) f.add(String.valueOf(v));
        return Codec.join(f, Codec.PART);
    }

    static StoryEvent decode(String s) {
        List<String> f = Codec.split(s, Codec.PART);
        if (f.size() < 4) return null;
        Kind k = Kind.of(f.get(1));
        if (k == null) return null;
        int[] n = new int[Math.max(0, f.size() - 4)];
        for (int i = 0; i < n.length; i++) n[i] = Codec.intAt(f, 4 + i, 0);
        return new StoryEvent(Codec.longAt(f, 0, 0L), k, Codec.intAt(f, 2, -1), Codec.textAt(f, 3), n);
    }
}
