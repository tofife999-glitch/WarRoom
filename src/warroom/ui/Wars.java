package warroom.ui;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.util.Misc;
import warroom.Fmt;
import warroom.Score;
import warroom.SectorSnapshot;
import warroom.Settings;
import warroom.data.StoryEvent;
import warroom.data.WarData;
import warroom.data.WarRecord;
import warroom.track.Alliances;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * What the pages show, worked out from the recorded wars and the current state: single wars, and alliance
 * wars (pair wars whose sides were in the same two alliances) with their fronts. Never saved.
 */
final class Wars {
    /** Days a recently ended war stays on the Active wars page. */
    static final long JUST_ENDED_DAYS = 14L;
    /**
     * Days a war counts as new (its NEW tag and new-war flash), a war with fighting shows as engaged, and a
     * pirate war with a capture stays visible.
     */
    static final long RECENT_DAYS = 30L;

    private Wars() {
    }

    /** An alliance war: its two alliances (left first) and its fronts. */
    static final class AllianceWar {
        final String key;
        final String[] allianceId = new String[2];
        final String[] name = new String[2];
        final List<WarRecord> fronts = new ArrayList<>();

        AllianceWar(String key) {
            this.key = key;
        }

        /** Whether the war was already under way when records began (its first front's start is not known). */
        boolean startUnknown() {
            long s = start();
            for (WarRecord r : fronts) {
                if (!r.startKnown && r.start == s) return true;
            }
            return false;
        }

        long start() {
            long s = Long.MAX_VALUE;
            for (WarRecord r : fronts) s = Math.min(s, r.start);
            return s == Long.MAX_VALUE ? 0L : s;
        }

        boolean ended() {
            for (WarRecord r : fronts) {
                if (!r.ended()) return false;
            }
            return true;
        }

        /** The record side that belongs to the left alliance. */
        int leftSide(WarRecord r) {
            WarData d = WarData.get();
            // Each alliance's own part of its merge chain, up to where it meets the other's: this keeps the
            // sides apart even when the alliances later merged with each other.
            List<String> c0 = chain(d, allianceId[0]);
            List<String> c1 = chain(d, allianceId[1]);
            List<String> own0 = beforeMeet(c0, c1);
            List<String> own1 = beforeMeet(c1, c0);
            int a = sideOf(d, r.allianceIdA, own0, own1);
            if (a >= 0) return a;
            int b = sideOf(d, r.allianceIdB, own0, own1);
            if (b >= 0) return 1 - b;
            String ca = d.canonAlliance(r.allianceIdA);
            return ca != null && ca.equals(d.canonAlliance(allianceId[0])) ? 0 : 1;
        }

        /** {@code id} and the alliances it merged into, in order. */
        private static List<String> chain(WarData d, String id) {
            List<String> out = new ArrayList<>();
            for (String c = id; c != null && out.size() <= 16; c = d.mergedInto.get(c)) out.add(c);
            return out;
        }

        /** The part of {@code mine} before it reaches an alliance that is also on {@code other}. */
        private static List<String> beforeMeet(List<String> mine, List<String> other) {
            List<String> out = new ArrayList<>();
            for (String c : mine) {
                if (other.contains(c)) break;
                out.add(c);
            }
            return out;
        }

        /** 0 or 1 for the first alliance on {@code id}'s merge chain that belongs to one side only; -1 if none. */
        private static int sideOf(WarData d, String id, List<String> own0, List<String> own1) {
            String c = id;
            for (int i = 0; i <= 16 && c != null; i++) {
                if (own0.contains(c)) return 0;
                if (own1.contains(c)) return 1;
                c = d.mergedInto.get(c);
            }
            return -1;
        }

        /** When the last front ended (0 while any is going on). */
        long end() {
            long e = Long.MIN_VALUE;
            for (WarRecord r : fronts) {
                if (!r.ended()) return 0L;
                e = Math.max(e, r.end);
            }
            return e;
        }

        int endedFronts() {
            int n = 0;
            for (WarRecord r : fronts) {
                if (r.ended()) n++;
            }
            return n;
        }

        /** Summed war score of the left (0) or right (1) alliance over all fronts. */
        float points(int leftOrRight) {
            float p = 0f;
            for (WarRecord r : fronts) {
                int l = leftSide(r);
                p += Score.total(r, leftOrRight == 0 ? l : 1 - l);
            }
            return p;
        }

        /** Who has gained ground lately over all fronts: 0 (left), 1 (right), -1 an even exchange, -2 quiet. */
        int gaining(long now) {
            float window = Score.momentumDays();
            float[] p = new float[2];
            for (WarRecord r : fronts) {
                int l = leftSide(r);
                float days = Score.days(r.start, now);
                p[0] += r.momentum.recent(days, window, l);
                p[1] += r.momentum.recent(days, window, 1 - l);
            }
            return Score.gaining(p[0], p[1]);
        }

        int sum(int stat, int leftOrRight) {
            int n = 0;
            for (WarRecord r : fronts) {
                int l = leftSide(r);
                int side = leftOrRight == 0 ? l : 1 - l;
                if (stat == FIGHTS) n += r.fightsWon(side);
                else if (stat == SHIPS) n += r.shipsLost(side);
                else n += r.get(stat, side);
            }
            return n;
        }

        boolean estimated(int leftOrRight) {
            for (WarRecord r : fronts) {
                int l = leftSide(r);
                if (r.lossesEstimated(leftOrRight == 0 ? l : 1 - l)) return true;
            }
            return false;
        }

        /** The faction ids on one side across all fronts, biggest first. */
        List<String> members(int leftOrRight, SectorSnapshot s) {
            List<String> out = new ArrayList<>();
            for (WarRecord r : fronts) {
                int l = leftSide(r);
                String id = r.id(leftOrRight == 0 ? l : 1 - l);
                if (!out.contains(id)) out.add(id);
            }
            out.sort(Comparator.comparingInt((String id) -> -sizeOf(s, id)));
            return out;
        }
    }

    /** Pseudo stat ids for sums: fights won (battles plus engagements) and ships lost (counted plus estimated). */
    static final int FIGHTS = -1;
    static final int SHIPS = -2;

    /** One entry on the Active wars page: a single war, or an alliance war. */
    static final class Item {
        final WarRecord war;
        final AllianceWar group;

        Item(WarRecord war, AllianceWar group) {
            this.war = war;
            this.group = group;
        }

        /** Button and navigation id. */
        String ref() {
            return war != null ? war.id() : "G:" + group.key;
        }

        long latest() {
            if (war != null) return latestNotable(war);
            long t = Long.MIN_VALUE;
            for (WarRecord r : group.fronts) t = Math.max(t, latestNotable(r));
            return t;
        }

        boolean mine() {
            if (war != null) return Wars.mine(war);
            for (WarRecord r : group.fronts) {
                if (Wars.mine(r)) return true;
            }
            return false;
        }
    }

    /** Time of the war's latest capture, large battle, start or end; what the order of the cards follows. */
    static long latestNotable(WarRecord r) {
        long t = r.start;
        for (StoryEvent e : r.story) {
            switch (e.kind) {
                case CAPTURE:
                case BATTLE:
                case BEGAN:
                case ENDED:
                case BOMBARD:
                case GONE:
                    t = Math.max(t, e.time);
                    break;
                default:
                    break;
            }
        }
        return t;
    }

    /** Your own faction's or your employer's war. */
    static boolean mine(WarRecord r) {
        String employer = Misc.getCommissionFactionId();
        return r.involves("player") || (employer != null && r.involves(employer));
    }

    /** Pirate-type wars hide unless asked for, except your own and those with a capture lately. */
    static boolean shown(WarRecord r, boolean showPirates, long now) {
        if (!r.pirate || showPirates || mine(r)) return true;
        for (StoryEvent e : r.story) {
            if (e.kind == StoryEvent.Kind.CAPTURE && now - e.time < RECENT_DAYS * WarData.DAY) return true;
        }
        return false;
    }

    /**
     * "records began", or "first recorded" for a war found later (after War Room was out of the save for a
     * while), whose start is not when records began.
     */
    static String recordsWord(WarRecord r) {
        return r.start > WarData.get().since + WarData.DAY ? "first recorded" : "records began";
    }

    static boolean isNew(WarRecord r, long now) {
        WarData d = WarData.get();
        return r.startKnown && r.start > d.since && now - r.start < RECENT_DAYS * WarData.DAY && !r.ended();
    }

    /** Alliance wars with at least two fronts among the given records; key -> war. */
    static Map<String, AllianceWar> groups(List<WarRecord> records) {
        Map<String, List<WarRecord>> byKey = new LinkedHashMap<>();
        for (WarRecord r : records) {
            if (r.group != null) byKey.computeIfAbsent(r.group, k -> new ArrayList<>()).add(r);
        }
        Map<String, AllianceWar> out = new LinkedHashMap<>();
        for (Map.Entry<String, List<WarRecord>> e : byKey.entrySet()) {
            if (e.getValue().size() < 2) continue;
            AllianceWar g = group(e.getKey(), e.getValue());
            if (g != null) out.put(e.getKey(), g);
        }
        return out;
    }

    /** An alliance war from its key ("allianceA~allianceB~start") and fronts; alliances left by name. */
    static AllianceWar group(String key, List<WarRecord> fronts) {
        String[] parts = key.split("~");
        if (parts.length < 3) return null;
        WarRecord any = fronts.get(0);
        WarData d = WarData.get();
        // The alliances as they are now: one that merged into another is followed to it.
        String ca = d.canonAlliance(parts[0]);
        String cb = d.canonAlliance(parts[1]);
        if (ca != null && cb != null && !ca.equals(cb)) {
            parts[0] = ca;
            parts[1] = cb;
        }
        String[] names = new String[2];
        for (int k = 0; k < 2; k++) {
            String fallback = null;
            for (WarRecord r : fronts) {
                if (parts[k].equals(d.canonAlliance(r.allianceIdA))) fallback = r.allianceA;
                if (parts[k].equals(d.canonAlliance(r.allianceIdB))) fallback = r.allianceB;
            }
            String known = d.knownAlliances.get(parts[k]);
            names[k] = Alliances.name(parts[k], fallback != null ? fallback : known != null ? known : "an alliance");
        }
        AllianceWar g = new AllianceWar(key);
        int first = names[0].compareToIgnoreCase(names[1]) <= 0 ? 0 : 1;
        g.allianceId[0] = parts[first];
        g.allianceId[1] = parts[1 - first];
        g.name[0] = names[first];
        g.name[1] = names[1 - first];
        g.fronts.addAll(fronts);
        // Fronts going on first, busiest first.
        g.fronts.sort(Comparator.comparing(WarRecord::ended).thenComparingInt((WarRecord r) -> -(r.fights()
                + 5 * (r.get(WarRecord.CAPTURED, 0) + r.get(WarRecord.CAPTURED, 1)))).thenComparing((WarRecord r) -> r.pairKey()));
        return any == null ? null : g;
    }

    /** The items of the Active wars page that pass the pirate rule, latest notable event first. */
    static List<Item> activeItems(WarData d, boolean showPirates, long now) {
        List<WarRecord> shown = new ArrayList<>();
        for (WarRecord r : d.active.values()) {
            if (shown(r, showPirates, now)) shown.add(r);
        }
        Map<String, AllianceWar> groups = groups(shown);
        List<Item> items = new ArrayList<>();
        for (AllianceWar g : groups.values()) items.add(new Item(null, g));
        for (WarRecord r : shown) {
            if (r.group == null || !groups.containsKey(r.group)) items.add(new Item(r, null));
        }
        items.sort(Comparator.comparingLong((Item i) -> -i.latest()).thenComparing(Item::ref));
        return items;
    }

    /** Wars that ended in the last 14 days (pirate rule applied), latest first. */
    static List<WarRecord> justEnded(WarData d, boolean showPirates, long now) {
        List<WarRecord> out = new ArrayList<>();
        for (WarRecord r : d.archive) {
            if (now - r.end < JUST_ENDED_DAYS * WarData.DAY && shown(r, showPirates, now)) out.add(r);
        }
        out.sort(Comparator.comparingLong((WarRecord r) -> -r.end));
        return out;
    }

    /** Pirate-type wars the toggle would add to the page: going on, or just ended. */
    static int hiddenPirateWars(WarData d, long now) {
        int n = 0;
        for (WarRecord r : d.active.values()) {
            if (!shown(r, false, now)) n++;
        }
        for (WarRecord r : d.archive) {
            if (now - r.end < JUST_ENDED_DAYS * WarData.DAY && !shown(r, false, now)) n++;
        }
        return n;
    }

    static int sizeOf(SectorSnapshot s, String id) {
        int i = s.indexOf(id);
        return i < 0 ? 0 : s.size[i];
    }

    static FactionAPI faction(String id) {
        return id == null ? null : Global.getSector().getFaction(id);
    }

    static String name(String id) {
        FactionAPI f = faction(id);
        return f == null ? (id == null ? "Unknown" : id) : Fmt.name(f);
    }

    /**
     * "Hegemony holds the advantage", "... holds a clear advantage", "Evenly matched", "Nothing to report
     * yet"; for a war that is over, "held" and "Nothing to report".
     */
    static Phrase verdict(float shareLeft, String leftName, String rightName, java.awt.Color leftC, java.awt.Color rightC,
                          boolean over) {
        Score.Verdict v = Score.verdict(shareLeft);
        Phrase p = new Phrase();
        if (v == Score.Verdict.NOTHING) return p.add(over ? "Nothing to report" : "Nothing to report yet");
        if (v == Score.Verdict.EVEN) return p.add("Evenly matched");
        boolean leftLeads = shareLeft >= 0.5f;
        p.hl(leftLeads ? leftName : rightName, leftLeads ? leftC : rightC);
        String verb = over ? " held " : " holds ";
        return p.add(verb + (v == Score.Verdict.FAR_AHEAD ? "a clear advantage" : "the advantage"));
    }

    /** "Past 90 days": how far back the momentum line and the header's summary look (warRoom_recentDays). */
    static String recentLabel() {
        return "Past " + Settings.recentDays() + " days";
    }

    /** "Past 90 days: X gaining ground", "... even exchange", "... quiet". */
    static Phrase momentum(int gaining, String leftName, String rightName, java.awt.Color leftC, java.awt.Color rightC) {
        Phrase p = new Phrase().add(recentLabel() + ": ");
        if (gaining == -2) return p.add("quiet");
        if (gaining == -1) return p.add("even exchange");
        return p.hl(gaining == 0 ? leftName : rightName, gaining == 0 ? leftC : rightC).add(" gaining ground");
    }
}
