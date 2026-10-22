package warroom.data;

import java.util.ArrayList;
import java.util.List;

/**
 * One war: two factions, when it began (and ended), what happened between them, its story and its
 * momentum. Side 0 is the faction with the smaller id ({@link #a}), side 1 the other ({@link #b}).
 * Counters have one slot per side; new counters go at the end, so older saves load with zeros.
 */
public final class WarRecord {
    /** Battles won that were seen (fought near the player). */
    public static final int WON = 0;
    /** Ships lost in seen battles. */
    public static final int SHIPS = 2;
    /** Fleet points lost in seen battles. */
    public static final int FP = 4;
    /** Ships lost off-screen, estimated from Nexerelin's simulated losses, in tenths of a ship. */
    public static final int SHIPS_EST = 6;
    /** Fleet points lost off-screen, estimated. */
    public static final int FP_EST = 8;
    /** Off-screen engagements won (Nexerelin's simulated fights). */
    public static final int ENGAGEMENTS = 10;
    /** Colonies taken from the other side. */
    public static final int CAPTURED = 12;
    /** Total size of the colonies taken. */
    public static final int CAPTURED_SIZE = 14;
    /** Colony size destroyed by bombardment. */
    public static final int BOMBARDED_SIZE = 16;
    /** Raids launched: Nexerelin raids, base strikes and saturation bombardments; your own raids and tactical
     * bombardments (always won). */
    public static final int RAIDS = 18;
    public static final int RAIDS_WON = 20;
    /** Invasions launched, your own included. */
    public static final int INVASIONS = 22;
    /** Invasions that took the colony. */
    public static final int INVASIONS_WON = 24;
    /** Invasions by the other side that this side beat back. */
    public static final int REPELLED = 26;
    public static final int GROUND_WON = 28;
    /** Fleet points your own fleet destroyed while fighting for this side. */
    public static final int PLAYER_FP = 30;
    static final int SLOTS = 32;

    public static final int END_NONE = 0;
    public static final int END_PEACE = 1;
    /** Side a lost its last colony (for 30 days). */
    public static final int END_GONE_A = 2;
    public static final int END_GONE_B = 3;

    /** How a war that ended in peace ended (n1 of its ENDED event): not known, a ceasefire, a peace treaty. */
    public static final int PEACE_UNKNOWN = 0;
    public static final int PEACE_CEASEFIRE = 1;
    public static final int PEACE_TREATY = 2;

    /** The standing last logged as a turning point: -1 evenly matched, 0 or 1 the side ahead; unset before 0.8. */
    public static final int STANDING_EVEN = -1;
    public static final int STANDING_UNSET = -2;

    /** Story events kept per war (pirate-type wars keep fewer). */
    public static final int STORY_CAP = 12;
    public static final int STORY_CAP_PIRATE = 4;

    public final String a;
    public final String b;
    public final long start;
    /** False for wars that were already going on when tracking began; start is then that moment. */
    public final boolean startKnown;
    /** A war with a pirate-type faction (pirates, Luddic Path and the like). */
    public final boolean pirate;
    /** Which record side is shown on the left, fixed when the war began (alphabetical by name). */
    public final int left;
    public final int[] startSize = new int[2];
    public final int[] endSize = new int[2];
    public long end;
    public int endReason = END_NONE;
    /** Each side's alliance name when the war began; null for none. Shown if the alliance is gone. */
    public String allianceA;
    public String allianceB;
    /** Each side's Nexerelin alliance id (Alliance.uuId) when the war began; null for none. */
    public String allianceIdA;
    public String allianceIdB;
    /**
     * The alliance war this war belongs to: wars whose sides were in the same two alliances share it; null for
     * a war outside any alliance war.
     */
    public String group;
    /** False for records read from a save that didn't have alliance ids yet (they get tagged on load). */
    public transient boolean tagged = true;
    public final Momentum momentum;
    public final List<StoryEvent> story = new ArrayList<>();
    /** Who holds the advantage as the war log last noted it (see {@link warroom.Turns}). */
    public int standing = STANDING_EVEN;
    private final int[] n = new int[SLOTS];

    public WarRecord(String x, String y, long start, boolean startKnown, boolean pirate, String leftId) {
        this(x, y, start, startKnown, pirate, leftId, new Momentum());
    }

    private WarRecord(String x, String y, long start, boolean startKnown, boolean pirate, String leftId, Momentum m) {
        boolean ordered = x.compareTo(y) <= 0;
        this.a = ordered ? x : y;
        this.b = ordered ? y : x;
        this.start = start;
        this.startKnown = startKnown;
        this.pirate = pirate;
        this.left = b.equals(leftId) ? 1 : 0;
        this.momentum = m;
    }

    public static String pairKey(String x, String y) {
        return x.compareTo(y) <= 0 ? x + "|" + y : y + "|" + x;
    }

    public String pairKey() {
        return a + "|" + b;
    }

    /** Unique id of this war: the pair and its start, so two wars of the same pair stay apart. */
    public String id() {
        return a + "|" + b + "|" + start;
    }

    public boolean ended() {
        return endReason != END_NONE;
    }

    /** 0 for {@link #a}, 1 for {@link #b}. */
    public int side(String factionId) {
        return a.equals(factionId) ? 0 : 1;
    }

    public String id(int side) {
        return side == 0 ? a : b;
    }

    public boolean involves(String factionId) {
        return a.equals(factionId) || b.equals(factionId);
    }

    public String alliance(int side) {
        return side == 0 ? allianceA : allianceB;
    }

    public String allianceId(int side) {
        return side == 0 ? allianceIdA : allianceIdB;
    }

    public int get(int stat, int side) {
        return n[stat + side];
    }

    public void add(int stat, int side, int amount) {
        if (side < 0 || side > 1) return;
        n[stat + side] += amount;
    }

    /** War score points a side just gained, for the momentum history. */
    public void addPoints(long now, int side, float points) {
        momentum.add((now - start) / (float) WarData.DAY, side, points);
    }

    /** Seen battles plus off-screen engagements. */
    public int fights() {
        return n[WON] + n[WON + 1] + n[ENGAGEMENTS] + n[ENGAGEMENTS + 1];
    }

    public int fightsWon(int side) {
        return get(WON, side) + get(ENGAGEMENTS, side);
    }

    public int shipsLost(int side) {
        return get(SHIPS, side) + Math.round(get(SHIPS_EST, side) / 10f);
    }

    public int fpLost(int side) {
        return get(FP, side) + get(FP_EST, side);
    }

    /** Whether a side's losses include an off-screen estimate worth marking (half a ship or more). */
    public boolean lossesEstimated(int side) {
        return get(SHIPS_EST, side) >= 5 || get(FP_EST, side) > 0;
    }

    /** Adds a story event in its place by time (for histories put together afterwards), then keeps the cap. */
    public void insertEvent(StoryEvent e) {
        int i = story.size();
        while (i > 0 && story.get(i - 1).time > e.time) i--;
        story.add(i, e);
        trimStory();
    }

    /** Adds a story event, dropping the least important middle events beyond the cap. */
    public void addEvent(StoryEvent e) {
        story.add(e);
        trimStory();
    }

    private void trimStory() {
        int cap = pirate ? STORY_CAP_PIRATE : STORY_CAP;
        while (story.size() > cap) {
            int firstCapture = -1;
            int lastCapture = -1;
            for (int i = 0; i < story.size(); i++) {
                if (story.get(i).kind != StoryEvent.Kind.CAPTURE) continue;
                if (firstCapture < 0) firstCapture = i;
                lastCapture = i;
            }
            int drop = -1;
            int worst = Integer.MAX_VALUE;
            for (int i = 1; i < story.size() - 1; i++) {
                int w = story.get(i).weight();
                if (i == firstCapture || i == lastCapture) w += 10;
                if (w < worst) {
                    worst = w;
                    drop = i;
                }
            }
            if (drop < 0) drop = story.size() > 2 ? 1 : 0;
            story.remove(drop);
        }
    }

    /** How the war's peace came about: PEACE_UNKNOWN (also for wars not ended in peace), _CEASEFIRE or _TREATY. */
    public int peaceKind() {
        for (int i = story.size() - 1; i >= 0; i--) {
            StoryEvent e = story.get(i);
            if (e.kind == StoryEvent.Kind.ENDED) return e.n(1);
        }
        return PEACE_UNKNOWN;
    }

    /** Notes how peace came about, once known (Nexerelin may report it a moment after the war was closed). */
    public void setPeaceKind(int kind) {
        for (int i = story.size() - 1; i >= 0; i--) {
            StoryEvent e = story.get(i);
            if (e.kind != StoryEvent.Kind.ENDED) continue;
            story.set(i, new StoryEvent(e.time, e.kind, e.side, e.text, e.n(0), kind));
            return;
        }
    }

    public StoryEvent latest() {
        for (int i = story.size() - 1; i >= 0; i--) {
            StoryEvent e = story.get(i);
            if (e.kind != StoryEvent.Kind.ENDED) return e;
        }
        return null;
    }

    String encode() {
        List<String> f = new ArrayList<>();
        f.add(Codec.esc(a));
        f.add(Codec.esc(b));
        f.add(String.valueOf(start));
        f.add(String.valueOf((startKnown ? 1 : 0) | (pirate ? 2 : 0)));
        f.add(String.valueOf(left));
        f.add(String.valueOf(end));
        f.add(String.valueOf(endReason));
        f.add(startSize[0] + " " + startSize[1]);
        f.add(endSize[0] + " " + endSize[1]);
        f.add(Codec.esc(allianceA));
        f.add(Codec.esc(allianceB));
        f.add(Codec.ints(n));
        f.add(momentum.encode());
        List<String> events = new ArrayList<>();
        for (StoryEvent e : story) events.add(e.encode());
        f.add(Codec.join(events, Codec.ITEM));
        f.add(Codec.esc(allianceIdA));
        f.add(Codec.esc(allianceIdB));
        f.add(Codec.esc(group));
        f.add(String.valueOf(standing));
        return Codec.join(f, Codec.FIELD);
    }

    /** Null if the line can't be read. */
    static WarRecord decode(String line) {
        try {
            List<String> f = Codec.split(line, Codec.FIELD);
            String x = Codec.textAt(f, 0);
            String y = Codec.textAt(f, 1);
            if (x == null || y == null) return null;
            int flags = Codec.intAt(f, 3, 0);
            int left = Codec.intAt(f, 4, 0);
            String leftId = left == 1 ? (x.compareTo(y) <= 0 ? y : x) : (x.compareTo(y) <= 0 ? x : y);
            WarRecord r = new WarRecord(x, y, Codec.longAt(f, 2, 0L), (flags & 1) != 0, (flags & 2) != 0, leftId,
                    Momentum.decode(f.size() > 12 ? f.get(12) : ""));
            r.end = Codec.longAt(f, 5, 0L);
            r.endReason = Codec.intAt(f, 6, END_NONE);
            int[] ss = Codec.ints(f.size() > 7 ? f.get(7) : "");
            int[] es = Codec.ints(f.size() > 8 ? f.get(8) : "");
            for (int s = 0; s < 2; s++) {
                if (s < ss.length) r.startSize[s] = ss[s];
                if (s < es.length) r.endSize[s] = es[s];
            }
            r.allianceA = Codec.textAt(f, 9);
            r.allianceB = Codec.textAt(f, 10);
            int[] v = Codec.ints(f.size() > 11 ? f.get(11) : "");
            System.arraycopy(v, 0, r.n, 0, Math.min(v.length, SLOTS));
            r.allianceIdA = Codec.textAt(f, 14);
            r.allianceIdB = Codec.textAt(f, 15);
            r.group = Codec.textAt(f, 16);
            r.standing = Codec.intAt(f, 17, STANDING_UNSET);
            r.tagged = f.size() > 14;
            if (f.size() > 13 && !f.get(13).isEmpty()) {
                for (String item : Codec.split(f.get(13), Codec.ITEM)) {
                    StoryEvent e = StoryEvent.decode(item);
                    if (e != null) r.story.add(e);
                }
            }
            return r;
        } catch (RuntimeException ex) {
            return null;
        }
    }

    /** Old slots of War Room 0.2, in its order: won, ships, fp, captured, raids, raids won, invasions, invasions won, ground won. */
    private static final int[] V2_SLOTS = {WON, SHIPS, FP, CAPTURED, RAIDS, RAIDS_WON, INVASIONS, INVASIONS_WON, GROUND_WON};

    /** A war line from a War Room 0.2 save ("a|b|start|known|counters"); null if it can't be read. */
    static WarRecord decodeV2(String line, boolean pirate, String leftId) {
        try {
            String[] f = line.split("\\|", -1);
            String x = Codec.unescV2(f[0]);
            String y = Codec.unescV2(f[1]);
            if (x == null || y == null) return null;
            WarRecord r = new WarRecord(x, y, Long.parseLong(f[2]), "1".equals(f[3]), pirate, leftId);
            r.tagged = false;
            int[] v = Codec.ints(f[4]);
            for (int k = 0; k < V2_SLOTS.length; k++) {
                for (int s = 0; s < 2; s++) {
                    int i = k * 2 + s;
                    if (i < v.length) r.n[V2_SLOTS[k] + s] = v[i];
                }
            }
            return r;
        } catch (RuntimeException ex) {
            return null;
        }
    }
}
