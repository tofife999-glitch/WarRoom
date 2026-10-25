package warroom.data;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * What War Room has recorded: the wars going on, the ended wars (with their stories), and sector news.
 * Lives in memory; in the save it is a few lists of strings (see {@link #write}), so a save never needs
 * War Room to load. Reads both its own format (3) and War Room 0.2's (format 2), sorting 0.2's shared
 * log into the wars it belongs to.
 */
public final class WarData {
    public static final int FORMAT = 3;
    public static final int ARCHIVE_CAP = 30;
    public static final int ARCHIVE_PIRATE_CAP = 20;
    public static final int NEWS_CAP = 60;
    /** Game time units per day (the campaign clock counts milliseconds of game time). */
    public static final long DAY = 24L * 60L * 60L * 1000L;

    /** What reading a save needs to know about factions; the game supplies it, tests can fake it. */
    public interface Factions {
        boolean isPirate(String factionId);

        /** The display name, for putting the alphabetically first side on the left. */
        String name(String factionId);
    }

    private static WarData current = new WarData();

    /**
     * When tracking began; 0 before the first check. Game timestamps are negative (the clock counts from
     * 1970), so 0 only ever means "not set", never "earliest".
     */
    public long since;
    /** When War Room last ran in this save (its last check, or the save); a long gap means it was taken out meanwhile. */
    public long lastCheck;
    /** When War Room was last opened in this save, for "since your last look"; 0 before the first time. */
    public long lastLook;
    /** Wars going on, by {@link WarRecord#pairKey}. Wars of a faction without colonies stay until they end. */
    public final Map<String, WarRecord> active = new LinkedHashMap<>();
    /** Ended wars, oldest first; at most ARCHIVE_CAP, plus ARCHIVE_PIRATE_CAP pirate-type ones. */
    public final List<WarRecord> archive = new ArrayList<>();
    /** Oldest first, at most NEWS_CAP entries. */
    public final List<LogEntry> news = new ArrayList<>();
    /** Factions that had colonies at the last check. */
    public final Set<String> factions = new LinkedHashSet<>();
    /** When a faction was last seen without colonies; cleared when it has colonies again. */
    public final Map<String, Long> goneSince = new LinkedHashMap<>();
    /** Alliances merged away: their id, to the id of the alliance they merged into. */
    public final Map<String, String> mergedInto = new LinkedHashMap<>();
    /** Every alliance seen so far, id to name (names of dead alliances stay here). */
    public final Map<String, String> knownAlliances = new LinkedHashMap<>();
    /** Whether the alliances have been taken stock of once (later new ones are news). */
    public boolean alliancesPolled;
    /**
     * The wars dropped from the Archive, summed per faction, and per alliance as "alliance|enemy alliance" (ids
     * when dropped; the enemy part empty if it had none), so a war between two alliances that later became one
     * still counts once.
     */
    public final Map<String, Ledger> retired = new LinkedHashMap<>();
    public final Map<String, Ledger> retiredAlliances = new LinkedHashMap<>();
    /** Each faction's total colony size and when it was noted: {size, when, BASE_*}. */
    public final Map<String, long[]> baseline = new LinkedHashMap<>();
    /** A baseline from when records began, from when the faction first held colonies later, or (a save from before
     * 0.6) from the first check that noted sizes. */
    public static final int BASE_START = 0;
    public static final int BASE_LATER = 1;
    public static final int BASE_NOTED = 2;

    public static WarData get() {
        return current;
    }

    /** Makes {@code d} the data everything reads and records into (the demo mode swaps it). */
    public static void use(WarData d) {
        current = d == null ? new WarData() : d;
    }

    /** The faction of the pair that sorts first by display name; it is shown on the left. */
    public static String leftOf(String x, String y, Factions info) {
        String nx = info.name(x);
        String ny = info.name(y);
        int c = nx == null || ny == null ? 0 : nx.compareToIgnoreCase(ny);
        if (c == 0) c = x.compareTo(y);
        return c <= 0 ? x : y;
    }

    public WarRecord active(String x, String y) {
        return active.get(WarRecord.pairKey(x, y));
    }

    /** A war by its id, going on or ended; null if unknown (e.g. trimmed from the archive). */
    public WarRecord find(String id) {
        if (id == null) return null;
        for (WarRecord r : active.values()) {
            if (r.id().equals(id)) return r;
        }
        for (WarRecord r : archive) {
            if (r.id().equals(id)) return r;
        }
        return null;
    }

    /** Ends a war: notes how and when, adds the last story event, and moves it to the archive. */
    public void end(WarRecord r, long now, int reason, int sizeA, int sizeB) {
        end(r, now, reason, WarRecord.PEACE_UNKNOWN, sizeA, sizeB);
    }

    /** Ends a war; {@code peace} is how peace came about (WarRecord.PEACE_*), if it did. */
    public void end(WarRecord r, long now, int reason, int peace, int sizeA, int sizeB) {
        active.remove(r.pairKey());
        r.end = now;
        r.endReason = reason;
        r.endSize[0] = sizeA;
        r.endSize[1] = sizeB;
        r.addEvent(new StoryEvent(now, StoryEvent.Kind.ENDED, -1, null, reason, peace));
        archive.add(r);
        trimArchive();
    }

    /** Applies the Archive's caps now (for made-up histories that fill the archive directly). */
    public void trim() {
        trimArchive();
    }

    /**
     * Drops the oldest ended wars beyond the caps, but never a front of an alliance war still going. What a
     * dropped war counted is kept in its factions' and alliances' ledgers.
     */
    private void trimArchive() {
        Set<String> live = new HashSet<>();
        for (WarRecord r : active.values()) {
            if (r.group != null) live.add(r.group);
        }
        int normal = 0;
        int pirate = 0;
        for (WarRecord r : archive) {
            if (r.pirate) pirate++;
            else normal++;
        }
        for (int i = 0; i < archive.size() && (normal > ARCHIVE_CAP || pirate > ARCHIVE_PIRATE_CAP); ) {
            WarRecord r = archive.get(i);
            boolean keep = r.group != null && live.contains(r.group);
            if (!keep && r.pirate && pirate > ARCHIVE_PIRATE_CAP) {
                retire(archive.remove(i));
                pirate--;
            } else if (!keep && !r.pirate && normal > ARCHIVE_CAP) {
                retire(archive.remove(i));
                normal--;
            } else {
                i++;
            }
        }
    }

    private void retire(WarRecord r) {
        String[] al = {canonAlliance(r.allianceId(0)), canonAlliance(r.allianceId(1))};
        for (int side = 0; side < 2; side++) {
            retired.computeIfAbsent(r.id(side), k -> new Ledger()).add(r, side);
            String mine = al[side];
            String theirs = al[1 - side];
            // Both sides already in one alliance (theirs merged): the war counts once, from its first side.
            if (mine == null || side == 1 && mine.equals(theirs)) continue;
            retiredAlliances.computeIfAbsent(mine + "|" + (theirs == null ? "" : theirs), k -> new Ledger()).add(r, side);
        }
    }

    /**
     * The dropped wars of one alliance (followed through merges), each counted once, also a war between two
     * alliances that have since become this one.
     */
    public Ledger retiredOfAlliance(String allianceId) {
        Ledger out = new Ledger();
        String f = canonAlliance(allianceId);
        if (f == null) return out;
        for (Map.Entry<String, Ledger> e : retiredAlliances.entrySet()) {
            String[] k = e.getKey().split("\\|", -1);
            if (!f.equals(canonAlliance(k[0]))) continue;
            String other = k.length > 1 && !k[1].isEmpty() ? k[1] : null;
            if (other != null && f.equals(canonAlliance(other)) && k[0].compareTo(other) > 0) continue;
            out.add(e.getValue());
        }
        return out;
    }

    public void addNews(LogEntry e) {
        news.add(e);
        if (news.size() > NEWS_CAP) news.subList(0, news.size() - NEWS_CAP).clear();
    }

    /** Replaces the current data with what a save holds (empty if it holds nothing). */
    public static void read(Map<?, ?> m, Factions info) {
        WarData d = new WarData();
        if (m != null) {
            Object since = m.get("since");
            if (since instanceof Number) d.since = ((Number) since).longValue();
            Object last = m.get("last");
            if (last instanceof Number) d.lastCheck = ((Number) last).longValue();
            Object look = m.get("look");
            if (look instanceof Number) d.lastLook = ((Number) look).longValue();
            Object v = m.get("v");
            int format = v instanceof Number ? ((Number) v).intValue() : 1;
            if (format < FORMAT) {
                d.readV2(m, info);
            } else {
                for (Object o : list(m.get("wars"))) {
                    WarRecord r = o instanceof String ? WarRecord.decode((String) o) : null;
                    if (r != null && !r.ended()) d.active.put(r.pairKey(), r);
                }
                for (Object o : list(m.get("archive"))) {
                    WarRecord r = o instanceof String ? WarRecord.decode((String) o) : null;
                    if (r != null && r.ended()) d.archive.add(r);
                }
                for (Object o : list(m.get("news"))) {
                    LogEntry e = o instanceof String ? LogEntry.decode((String) o) : null;
                    if (e != null) d.news.add(e);
                }
                Object gone = m.get("gone");
                if (gone instanceof Map) {
                    for (Map.Entry<?, ?> e : ((Map<?, ?>) gone).entrySet()) {
                        if (e.getKey() instanceof String && e.getValue() instanceof Number) {
                            d.goneSince.put((String) e.getKey(), ((Number) e.getValue()).longValue());
                        }
                    }
                }
                strings(m.get("merged"), d.mergedInto);
                strings(m.get("alliances"), d.knownAlliances);
                d.alliancesPolled = Boolean.TRUE.equals(m.get("alliancesPolled"));
                ledgers(m.get("retired"), d.retired);
                ledgers(m.get("retiredAl"), d.retiredAlliances);
                Map<String, String> base = new LinkedHashMap<>();
                strings(m.get("baseline"), base);
                for (Map.Entry<String, String> e : base.entrySet()) {
                    List<String> f = Codec.split(e.getValue(), Codec.PART);
                    if (f.size() < 2) continue;
                    long when = Codec.longAt(f, 1, 0L);
                    int kind = Codec.intAt(f, 2, when > d.since + DAY ? BASE_LATER : BASE_START);
                    d.baseline.put(e.getKey(), new long[]{Codec.intAt(f, 0, 0), when, kind});
                }
            }
            for (Object o : list(m.get("factions"))) {
                if (o instanceof String) d.factions.add((String) o);
            }
        }
        current = d;
    }

    /** War Room 0.2: war lines without stories, and one shared log. */
    private void readV2(Map<?, ?> m, Factions info) {
        for (Object o : list(m.get("wars"))) {
            if (!(o instanceof String)) continue;
            String[] f = ((String) o).split("\\|", -1);
            if (f.length < 2) continue;
            String x = Codec.unescV2(f[0]);
            String y = Codec.unescV2(f[1]);
            if (x == null || y == null) continue;
            WarRecord r = WarRecord.decodeV2((String) o, info.isPirate(x) || info.isPirate(y), leftOf(x, y, info));
            if (r == null) continue;
            r.standing = WarRecord.STANDING_UNSET;
            r.addEvent(new StoryEvent(r.start, StoryEvent.Kind.BEGAN, -1, null));
            active.put(r.pairKey(), r);
        }
        for (Object o : list(m.get("log"))) {
            LogEntry e = o instanceof String ? LogEntry.decodeV2((String) o) : null;
            if (e == null) continue;
            WarRecord r = e.a == null || e.b == null ? null : active(e.a, e.b);
            boolean inWar = r != null && e.time >= r.start;
            switch (e.kind) {
                case CAPTURE:
                    if (inWar) {
                        int side = r.side(e.a);
                        r.add(WarRecord.CAPTURED_SIZE, side, e.v(0));
                        r.addEvent(new StoryEvent(e.time, StoryEvent.Kind.CAPTURE, side, e.text, e.v(0)));
                    } else {
                        addNews(e);
                    }
                    break;
                case BATTLE:
                    if (inWar) {
                        r.addEvent(new StoryEvent(e.time, StoryEvent.Kind.BATTLE, r.side(e.a), e.text,
                                e.v(0), e.v(1), e.v(2), e.v(3)));
                    }
                    break;
                case PEACE:
                    archiveV2Peace(e, info);
                    break;
                case WAR:
                    break;
                default:
                    addNews(e);
            }
        }
    }

    /** A 0.2 peace entry becomes an ended war with the totals it carried (no story beyond start and end). */
    private void archiveV2Peace(LogEntry e, Factions info) {
        if (e.a == null || e.b == null) return;
        long start = e.time - e.v(0) * DAY;
        WarRecord r = new WarRecord(e.a, e.b, start, e.v(1) == 1, info.isPirate(e.a) || info.isPirate(e.b),
                leftOf(e.a, e.b, info));
        int sa = r.side(e.a);
        int sb = 1 - sa;
        r.add(WarRecord.WON, sa, e.v(2));
        r.add(WarRecord.WON, sb, e.v(3));
        r.add(WarRecord.CAPTURED, sa, e.v(4));
        r.add(WarRecord.CAPTURED, sb, e.v(5));
        r.add(WarRecord.SHIPS, sa, e.v(6));
        r.add(WarRecord.SHIPS, sb, e.v(7));
        r.addEvent(new StoryEvent(start, StoryEvent.Kind.BEGAN, -1, null));
        r.end = e.time;
        r.endReason = WarRecord.END_PEACE;
        r.addEvent(new StoryEvent(e.time, StoryEvent.Kind.ENDED, -1, null, WarRecord.END_PEACE));
        archive.add(r);
        trimArchive();
    }

    /** Puts the data into a save map, as plain strings and numbers only. */
    public void write(Map<String, Object> m) {
        m.put("since", since);
        m.put("last", lastCheck);
        if (lastLook != 0) m.put("look", lastLook);
        ArrayList<String> w = new ArrayList<>();
        for (WarRecord r : active.values()) w.add(r.encode());
        m.put("wars", w);
        ArrayList<String> ar = new ArrayList<>();
        for (WarRecord r : archive) ar.add(r.encode());
        m.put("archive", ar);
        ArrayList<String> nw = new ArrayList<>();
        for (LogEntry e : news) nw.add(e.encode());
        m.put("news", nw);
        m.put("factions", new ArrayList<>(factions));
        m.put("gone", new LinkedHashMap<>(goneSince));
        m.put("merged", new LinkedHashMap<>(mergedInto));
        m.put("alliances", new LinkedHashMap<>(knownAlliances));
        if (alliancesPolled) m.put("alliancesPolled", Boolean.TRUE);
        m.put("retired", encoded(retired));
        m.put("retiredAl", encoded(retiredAlliances));
        LinkedHashMap<String, String> base = new LinkedHashMap<>();
        for (Map.Entry<String, long[]> e : baseline.entrySet()) {
            long[] v = e.getValue();
            base.put(e.getKey(), v[0] + "," + v[1] + "," + (v.length > 2 ? v[2] : BASE_START));
        }
        m.put("baseline", base);
    }

    private static LinkedHashMap<String, String> encoded(Map<String, Ledger> ledgers) {
        LinkedHashMap<String, String> out = new LinkedHashMap<>();
        for (Map.Entry<String, Ledger> e : ledgers.entrySet()) out.put(e.getKey(), e.getValue().encode());
        return out;
    }

    private static void ledgers(Object o, Map<String, Ledger> into) {
        Map<String, String> raw = new LinkedHashMap<>();
        strings(o, raw);
        for (Map.Entry<String, String> e : raw.entrySet()) {
            Ledger l = Ledger.decode(e.getValue());
            if (l != null) into.put(e.getKey(), l);
        }
    }

    private static void strings(Object o, Map<String, String> into) {
        if (!(o instanceof Map)) return;
        for (Map.Entry<?, ?> e : ((Map<?, ?>) o).entrySet()) {
            if (e.getKey() instanceof String && e.getValue() instanceof String) {
                into.put((String) e.getKey(), (String) e.getValue());
            }
        }
    }

    /** The two alliances of an alliance war's key ("a~b~start") as they are now, sorted; null if unreadable. */
    public String canonPair(String groupKey) {
        if (groupKey == null) return null;
        String[] p = groupKey.split("~");
        if (p.length < 3) return null;
        String a = canonAlliance(p[0]);
        String b = canonAlliance(p[1]);
        if (a == null || b == null || a.equals(b)) return null;
        return a.compareTo(b) <= 0 ? a + "~" + b : b + "~" + a;
    }

    /** Alliance war keys {@link #mergeGroups} renamed this session, to the key they go by now. Never saved. */
    public final Map<String, String> renamedGroups = new LinkedHashMap<>();

    /** The key an alliance war goes by now, following this session's renames. */
    public String groupNow(String key) {
        String k = key;
        for (int i = 0; i < 16 && k != null && renamedGroups.containsKey(k); i++) k = renamedGroups.get(k);
        return k;
    }

    /**
     * Alliance wars going on whose alliances have merged into the same two are one war from then on: all
     * their fronts take the oldest key. Alliance wars that are over stay apart.
     */
    public void mergeGroups() {
        Map<String, String> kept = new LinkedHashMap<>();
        Map<String, String> rename = new LinkedHashMap<>();
        for (WarRecord r : active.values()) {
            String pair = canonPair(r.group);
            if (pair == null || rename.containsKey(r.group)) continue;
            String k = kept.get(pair);
            if (k == null || k.equals(r.group)) {
                kept.put(pair, r.group);
            } else if (olderKey(r.group, k)) {
                rename.put(k, r.group);
                for (Map.Entry<String, String> e : rename.entrySet()) {
                    if (e.getValue().equals(k)) e.setValue(r.group);
                }
                kept.put(pair, r.group);
            } else {
                rename.put(r.group, k);
            }
        }
        if (rename.isEmpty()) return;
        for (Map.Entry<String, String> e : renamedGroups.entrySet()) {
            String to = rename.get(e.getValue());
            if (to != null) e.setValue(to);
        }
        renamedGroups.putAll(rename);
        for (WarRecord r : active.values()) {
            if (r.group != null && rename.containsKey(r.group)) r.group = rename.get(r.group);
        }
        for (WarRecord r : archive) {
            if (r.group != null && rename.containsKey(r.group)) r.group = rename.get(r.group);
        }
    }

    private static boolean olderKey(String x, String y) {
        long sx = keyStart(x);
        long sy = keyStart(y);
        return sx != sy ? sx < sy : x.compareTo(y) < 0;
    }

    private static long keyStart(String key) {
        try {
            return Long.parseLong(key.substring(key.lastIndexOf('~') + 1));
        } catch (RuntimeException ex) {
            return Long.MAX_VALUE;
        }
    }

    /** An alliance id with merges followed to the alliance it ended up in. */
    public String canonAlliance(String id) {
        String cur = id;
        for (int i = 0; i < 16 && cur != null && mergedInto.containsKey(cur); i++) cur = mergedInto.get(cur);
        return cur;
    }

    private static List<?> list(Object o) {
        return o instanceof List ? (List<?>) o : new ArrayList<>();
    }
}
