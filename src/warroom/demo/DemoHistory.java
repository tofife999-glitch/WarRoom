package warroom.demo;

import warroom.Score;
import warroom.Settings;
import warroom.Turns;
import warroom.data.LogEntry;
import warroom.data.Momentum;
import warroom.data.StoryEvent;
import warroom.data.WarData;
import warroom.data.WarRecord;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * A made-up war history for the demo mode, built from the sector's real factions, colonies, systems and
 * alliances: an alliance war with several fronts (one of them over), single wars of every kind (yours, a new
 * one, one older than the records, pirate wars seen and hidden) and ended wars, each with a plausible story.
 * Plain data in, plain data out, so it can be tried outside the game.
 */
public final class DemoHistory {
    private static final long DAY = WarData.DAY;

    private DemoHistory() {
    }

    /** What the history is made from. */
    public static final class Inputs {
        public static final class Faction {
            public final String id;
            public final String name;
            public final int size;
            public final boolean pirate;

            public Faction(String id, String name, int size, boolean pirate) {
                this.id = id;
                this.name = name;
                this.size = size;
                this.pirate = pirate;
            }
        }

        public static final class Colony {
            public final String name;
            public final String factionId;
            public final int size;
            public final String system;

            public Colony(String name, String factionId, int size, String system) {
                this.name = name;
                this.factionId = factionId;
                this.size = size;
                this.system = system;
            }
        }

        public static final class Alliance {
            public final String id;
            public final String name;
            public final List<String> members;

            public Alliance(String id, String name, List<String> members) {
                this.id = id;
                this.name = name;
                this.members = members;
            }
        }

        /** Factions with colonies, biggest first. */
        public final List<Faction> factions = new ArrayList<>();
        public final List<Colony> colonies = new ArrayList<>();
        public final List<Alliance> alliances = new ArrayList<>();
        /** Your faction's id ("player"), shown as one side of "your" war. */
        public String player = "player";
        /** Factions you can't be at war with: your employer and your alliance's members. */
        public final Set<String> friends = new HashSet<>();
    }

    /** Builds the history; {@code now} is the campaign clock's current timestamp. */
    public static WarData build(Inputs in, long now, Random rnd) {
        return build(in, now, rnd, false);
    }

    /**
     * With {@code big}: a long, busy history for trying the pages at their fullest: ten cycles of records,
     * many wars going on, the Archive full (older ended wars only counted in the factions' totals) and the
     * Sector news full.
     */
    public static WarData build(Inputs in, long now, Random rnd, boolean big) {
        Gen g = new Gen(in, now, rnd, big);
        return g.run();
    }

    private static final class Gen {
        final Inputs in;
        final long now;
        final Random rnd;
        final boolean big;
        final WarData d = new WarData();
        final Map<String, Inputs.Faction> byId = new LinkedHashMap<>();
        final Set<String> activePairs = new HashSet<>();
        /** The demo alliance each faction of the alliance war belongs to. */
        final Map<String, String> allianceOf = new LinkedHashMap<>();
        /** The real alliance of each member, so real allies are never put at war with each other. */
        final Map<String, String> realAlliance = new LinkedHashMap<>();
        /** Each pair's wars so far, {start, end}, so a pair is never at war twice at the same time. */
        final Map<String, List<long[]>> busy = new LinkedHashMap<>();
        /** Colonies already taken somewhere in the history; none is taken twice. */
        final Set<String> taken = new HashSet<>();
        final List<String> systems = new ArrayList<>();

        Gen(Inputs in, long now, Random rnd, boolean big) {
            this.in = in;
            this.now = now;
            this.rnd = rnd;
            this.big = big;
            for (Inputs.Faction f : in.factions) byId.put(f.id, f);
            for (Inputs.Colony c : in.colonies) {
                if (c.system != null && !systems.contains(c.system)) systems.add(c.system);
            }
            for (Inputs.Alliance a : in.alliances) {
                for (String m : a.members) realAlliance.put(m, a.id);
            }
        }

        WarData run() {
            d.since = now - (big ? 3650L : 420L) * DAY;
            d.lastCheck = now;
            d.alliancesPolled = true;
            for (Inputs.Faction f : in.factions) d.factions.add(f.id);

            List<String> majors = new ArrayList<>();
            List<String> pirates = new ArrayList<>();
            for (Inputs.Faction f : in.factions) {
                if (f.id.equals(in.player)) continue;
                (f.pirate ? pirates : majors).add(f.id);
            }

            Set<String> inAllianceWar = allianceWar(majors);

            // Single wars of every kind among the factions the alliance war leaves out (or any, if too few).
            List<String> free = new ArrayList<>();
            for (String id : majors) {
                if (!inAllianceWar.contains(id)) free.add(id);
            }
            if (free.size() < 4) free = new ArrayList<>(majors);
            int made = 0;
            int single = big ? 24 : 5;
            for (int attempt = 0; attempt < 80 * single / 5 && made < single && free.size() >= 2; attempt++) {
                String x = free.get(rnd.nextInt(free.size()));
                String y = free.get(rnd.nextInt(free.size()));
                if (x.equals(y) || activePairs.contains(WarRecord.pairKey(x, y)) || bothInAllianceWar(x, y)
                        || allied(x, y)) continue;
                long start;
                boolean known = true;
                if (made == 0) {
                    start = d.since; // older than the records
                    known = false;
                } else if (made == 1) {
                    start = now - (8 + rnd.nextInt(14)) * DAY; // a new war
                } else {
                    start = now - (40 + rnd.nextInt(big ? 3000 : 330)) * DAY;
                }
                // One lopsided war, so "far ahead" shows too.
                float bias = made == 3 ? 0.9f : 0.3f + 0.4f * rnd.nextFloat();
                active(x, y, start, known, rnd.nextBoolean() ? bias : 1f - bias, made == 2);
                made++;
            }

            // Your war, against one of the biggest factions that isn't your employer or ally.
            List<String> foes = new ArrayList<>();
            for (String id : majors) {
                if (!in.friends.contains(id) && !allied(in.player, id)) foes.add(id);
            }
            if (!foes.isEmpty()) {
                String enemy = foes.get(rnd.nextInt(Math.min(3, foes.size())));
                if (!activePairs.contains(WarRecord.pairKey(in.player, enemy))) {
                    active(in.player, enemy, now - (90 + rnd.nextInt(120)) * DAY, true, 0.62f, false);
                }
            }

            // Pirate wars: one with a recent capture (shown), one quiet (behind the pirate toggle).
            if (!pirates.isEmpty() && !majors.isEmpty()) {
                String p = pirates.get(0);
                String a = majors.get(rnd.nextInt(majors.size()));
                WarRecord seen = active(p, a, now - (200 + rnd.nextInt(150)) * DAY, true, 0.5f, false);
                if (seen != null) recentCapture(seen, seen.side(p));
                String b = majors.size() > 1 ? majors.get((majors.indexOf(a) + 1 + rnd.nextInt(majors.size() - 1)) % majors.size()) : null;
                String q = pirates.size() > 1 ? pirates.get(1) : p;
                if (b != null) quiet(active(q, b, now - (100 + rnd.nextInt(200)) * DAY, true, 0.5f, false));
            }

            endedWars(majors, pirates);
            news(majors);
            d.archive.sort(Comparator.comparingLong((WarRecord r) -> r.end));
            d.news.sort(Comparator.comparingLong((LogEntry e) -> e.time));
            // The Archive's caps, as in a real save: the oldest ended wars go into their factions' totals.
            d.trim();
            baseline();
            return d;
        }

        /** The alliance war: the two biggest real alliances, or two made-up ones from the biggest factions. */
        Set<String> allianceWar(List<String> majors) {
            Set<String> used = new HashSet<>();
            List<Inputs.Alliance> als = new ArrayList<>();
            for (Inputs.Alliance a : in.alliances) {
                List<String> m = new ArrayList<>();
                for (String id : a.members) {
                    if (byId.containsKey(id) && !id.equals(in.player) && !byId.get(id).pirate) m.add(id);
                }
                if (!m.isEmpty()) als.add(new Inputs.Alliance(a.id, a.name, m));
            }
            als.sort(Comparator.comparingInt((Inputs.Alliance a) -> -a.members.size()));
            Inputs.Alliance left;
            Inputs.Alliance right;
            if (als.size() >= 2 && als.get(0).members.size() + als.get(1).members.size() >= 3) {
                left = als.get(0);
                right = als.get(1);
            } else {
                if (majors.size() < 4) return used;
                List<String> l = new ArrayList<>();
                List<String> r = new ArrayList<>();
                // Members of the same real alliance stay on one side.
                Map<String, List<String>> sideOf = new LinkedHashMap<>();
                for (int i = 0; i < Math.min(6, majors.size()); i++) {
                    String id = majors.get(i);
                    String ra = realAlliance.get(id);
                    List<String> side = ra != null && sideOf.containsKey(ra) ? sideOf.get(ra) : (l.size() <= r.size() ? l : r);
                    side.add(id);
                    if (ra != null) sideOf.put(ra, side);
                }
                if (l.isEmpty() || r.isEmpty()) return used;
                left = new Inputs.Alliance("demo-alliance-1", "Sunward Compact", l);
                right = new Inputs.Alliance("demo-alliance-2", "Iron Concord", r);
            }
            d.knownAlliances.put(left.id, left.name);
            d.knownAlliances.put(right.id, right.name);
            String ka = left.id.compareTo(right.id) <= 0 ? left.id : right.id;
            String kb = ka.equals(left.id) ? right.id : left.id;
            long start = now - (260 + rnd.nextInt(80)) * DAY;
            String group = ka + "~" + kb + "~" + start;
            int fronts = 0;
            boolean endedOne = false;
            for (String x : left.members) {
                for (String y : right.members) {
                    if (fronts >= 4 || activePairs.contains(WarRecord.pairKey(x, y))) continue;
                    long s = start + rnd.nextInt(6) * DAY;
                    boolean end = !endedOne && fronts >= 1 && left.members.size() + right.members.size() > 3;
                    WarRecord r = record(x, y, s, true);
                    r.allianceIdA = r.a.equals(x) ? left.id : right.id;
                    r.allianceIdB = r.a.equals(x) ? right.id : left.id;
                    r.allianceA = r.a.equals(x) ? left.name : right.name;
                    r.allianceB = r.a.equals(x) ? right.name : left.name;
                    r.group = group;
                    allianceOf.put(x, left.id);
                    allianceOf.put(y, right.id);
                    float bias = 0.35f + 0.3f * rnd.nextFloat();
                    if (end) {
                        long e = now - (40 + rnd.nextInt(60)) * DAY;
                        simulate(r, e, r.a.equals(x) ? bias : 1f - bias, -1f);
                        close(r, e, WarRecord.END_PEACE);
                        mark(r, r.end);
                        endedOne = true;
                    } else {
                        simulate(r, now, r.a.equals(x) ? bias : 1f - bias, fronts == 0 ? 0.8f : -1f);
                        d.active.put(r.pairKey(), r);
                        activePairs.add(r.pairKey());
                        mark(r, now);
                    }
                    used.add(x);
                    used.add(y);
                    fronts++;
                }
            }
            return used;
        }

        /** A war going on; null if that pair already has one. */
        WarRecord active(String x, String y, long start, boolean known, float bias, boolean turns) {
            if (x.equals(y) || activePairs.contains(WarRecord.pairKey(x, y))) return null;
            WarRecord r = record(x, y, start, known);
            simulate(r, now, r.a.equals(x) ? bias : 1f - bias, turns ? 0.7f : -1f);
            d.active.put(r.pairKey(), r);
            activePairs.add(r.pairKey());
            mark(r, now);
            return r;
        }

        void mark(WarRecord r, long end) {
            busy.computeIfAbsent(r.pairKey(), k -> new ArrayList<>()).add(new long[]{r.start, end});
        }

        /** Whether the pair is at peace all through {@code from} to {@code to}. */
        boolean free(String x, String y, long from, long to) {
            List<long[]> wars = busy.get(WarRecord.pairKey(x, y));
            if (wars == null) return true;
            for (long[] w : wars) {
                if (from <= w[1] && w[0] <= to) return false;
            }
            return true;
        }

        boolean allied(String x, String y) {
            String a = realAlliance.get(x);
            return a != null && a.equals(realAlliance.get(y));
        }

        WarRecord record(String x, String y, long start, boolean known) {
            boolean pirate = isPirate(x) || isPirate(y);
            WarRecord r = new WarRecord(x, y, start, known, pirate, leftOf(x, y));
            r.startSize[0] = sizeOf(r.a);
            r.startSize[1] = sizeOf(r.b);
            r.addEvent(new StoryEvent(start, StoryEvent.Kind.BEGAN, -1, null));
            return r;
        }

        /** Turning points from the war's momentum: where its running war score crossed the lines. */
        void turns(WarRecord r, long until) {
            Momentum m = r.momentum;
            float c0 = 0f;
            float c1 = 0f;
            int last = WarRecord.STANDING_EVEN;
            for (int i = 0; i < Momentum.BUCKETS; i++) {
                long t = r.start + (i + 1) * m.bucketDays() * DAY;
                if (t > until) break;
                c0 += m.points(i, 0);
                c1 += m.points(i, 1);
                int next = Turns.next(Score.share(c0, c1), last);
                if (next != last) {
                    r.insertEvent(new StoryEvent(t, StoryEvent.Kind.SHIFT, next, null));
                    last = next;
                }
            }
            r.standing = last;
        }

        void close(WarRecord r, long end, int reason) {
            r.end = Math.max(end, latestEvent(r));
            r.endReason = reason;
            r.endSize[0] = Math.max(0, r.startSize[0] - r.get(WarRecord.CAPTURED_SIZE, 1) + r.get(WarRecord.CAPTURED_SIZE, 0));
            r.endSize[1] = Math.max(0, r.startSize[1] - r.get(WarRecord.CAPTURED_SIZE, 0) + r.get(WarRecord.CAPTURED_SIZE, 1));
            if (reason == WarRecord.END_GONE_A) r.endSize[0] = 0;
            if (reason == WarRecord.END_GONE_B) r.endSize[1] = 0;
            // Peace as Nexerelin makes it: 3 in 10 treaties, the rest ceasefires; a few by other means.
            float roll = rnd.nextFloat();
            int peace = reason != WarRecord.END_PEACE || roll < 0.1f ? WarRecord.PEACE_UNKNOWN
                    : roll < 0.37f ? WarRecord.PEACE_TREATY : WarRecord.PEACE_CEASEFIRE;
            r.addEvent(new StoryEvent(r.end, StoryEvent.Kind.ENDED, -1, null, reason, peace));
            d.archive.add(r);
        }

        void endedWars(List<String> majors, List<String> pirates) {
            List<String> all = new ArrayList<>(majors);
            if (all.size() < 2) return;
            int wars = big ? 140 : 14;
            for (int k = 0, attempt = 0; k < wars && attempt < wars * 15; attempt++) {
                String x;
                String y;
                if ((big ? k % 4 == 3 : k >= 11) && !pirates.isEmpty()) {
                    x = pirates.get(rnd.nextInt(pirates.size()));
                    y = all.get(rnd.nextInt(all.size()));
                } else {
                    x = all.get(rnd.nextInt(all.size()));
                    y = all.get(rnd.nextInt(all.size()));
                }
                if (x.equals(y) || allied(x, y)) continue;
                // Three ended in the last days ("Just ended"), the rest over the last year.
                long end = k < 3 ? now - (1 + rnd.nextInt(10)) * DAY : now - (20 + rnd.nextInt(big ? 3500 : 380)) * DAY;
                long start = Math.max(d.since - 200L * DAY, end - (30 + rnd.nextInt(260)) * DAY);
                boolean known = start >= d.since;
                if (!known) start = d.since;
                if (end <= start + 5 * DAY) continue;
                boolean gone = k == 3 || k == 7;
                if (!free(x, y, start, gone ? Math.min(now, end + 30L * DAY) : end)) continue;
                WarRecord r = record(x, y, start, known);
                float bias = 0.2f + 0.6f * rnd.nextFloat();
                simulate(r, end, bias, -1f);
                int reason = WarRecord.END_PEACE;
                if (gone) {
                    int lost = bias > 0.5f ? 1 : 0;
                    reason = lost == 0 ? WarRecord.END_GONE_A : WarRecord.END_GONE_B;
                    long goneAt = Math.max(latestEvent(r), end - 30L * DAY);
                    r.addEvent(new StoryEvent(goneAt, StoryEvent.Kind.GONE, lost, null));
                    end = Math.max(end, goneAt + 30L * DAY);
                    if (end > now) end = now;
                }
                close(r, end, reason);
                mark(r, r.end);
                k++;
            }
        }

        /**
         * Each faction's total colony size when records began: its size now, give or take what it would have
         * gained or lost; one faction only first held colonies later.
         */
        void baseline() {
            for (int i = 0; i < in.factions.size(); i++) {
                Inputs.Faction f = in.factions.get(i);
                int was = Math.max(1, f.size + rnd.nextInt(17) - 8);
                long when = i == in.factions.size() - 1 && i > 0 ? now - (30 + rnd.nextInt(200)) * DAY : d.since;
                d.baseline.put(f.id, new long[]{was, when, when == d.since ? WarData.BASE_START : WarData.BASE_LATER});
            }
        }

        /** Sector news: alliance changes and captures outside the wars, for completeness. */
        void news(List<String> majors) {
            if (big && majors.size() >= 2) {
                // A full news list over the years: alliance comings and goings, factions arriving.
                List<Long> times = new ArrayList<>();
                for (int i = 0; i < 70; i++) times.add(d.since + (long) (rnd.nextFloat() * (now - d.since)));
                Collections.sort(times);
                String[] names = {"Sunward Compact", "Iron Concord", "Free Worlds League", "Perihelion Accord"};
                for (long t : times) {
                    String x = majors.get(rnd.nextInt(majors.size()));
                    String al = names[rnd.nextInt(names.length)];
                    int roll = rnd.nextInt(3);
                    LogEntry.Kind kind = roll == 0 ? LogEntry.Kind.ALLIANCE_JOINED : roll == 1 ? LogEntry.Kind.ALLIANCE_LEFT
                            : LogEntry.Kind.ARRIVED;
                    d.addNews(new LogEntry(t, kind, x, null, kind == LogEntry.Kind.ARRIVED ? null : al, null));
                }
                return;
            }
            for (Inputs.Alliance a : in.alliances) {
                if (a.members.isEmpty()) continue;
                d.addNews(new LogEntry(now - (100 + rnd.nextInt(200)) * DAY, LogEntry.Kind.ALLIANCE_JOINED,
                        a.members.get(0), null, a.name, null));
            }
            if (!majors.isEmpty()) {
                d.addNews(new LogEntry(now - 5 * DAY, LogEntry.Kind.ARRIVED, majors.get(majors.size() - 1), null, null, null));
            }
        }

        /**
         * Fills a war's counters, story and momentum from its start up to {@code until}: fights about every
         * five days, side 0 winning with probability {@code bias0} (the other way round after {@code turnAt}
         * of the war, if that is not negative).
         */
        void simulate(WarRecord r, long until, float bias0, float turnAt) {
            float days = Math.max(1f, (until - r.start) / (float) DAY);
            int events = Math.max(2, Math.round(days / (4.5f + 2f * rnd.nextFloat())));
            if (r.pirate) events = Math.max(2, events / 2);
            List<Long> times = new ArrayList<>();
            for (int i = 0; i < events; i++) times.add(r.start + DAY + (long) (rnd.nextFloat() * (days - 1f) * DAY));
            Collections.sort(times);
            boolean yours = r.involves(in.player);
            int you = r.side(in.player);
            int captures = 0;
            for (long t : times) {
                float at = (t - r.start) / (days * DAY);
                float p0 = turnAt >= 0f && at > turnAt ? 1f - bias0 : bias0;
                int w = rnd.nextFloat() < p0 ? 0 : 1;
                int l = 1 - w;
                float roll = rnd.nextFloat();
                // A side without colonies can't be raided, invaded or bombarded: only fights in space then.
                if (roll >= 0.66f && !hasColonies(r.id(l))) roll = rnd.nextFloat() * 0.66f;
                if (roll < 0.40f) {
                    engagement(r, t, w);
                } else if (roll < 0.66f) {
                    battle(r, t, w, yours && w == you && rnd.nextBoolean(), you);
                } else if (roll < 0.82f) {
                    raid(r, t, w);
                } else if (roll < 0.90f && captures < 3) {
                    if (capture(r, t, w)) captures++;
                    else raid(r, t, w);
                } else if (roll < 0.96f) {
                    repelled(r, t, l, w);
                } else {
                    bombard(r, t, w);
                }
            }
            turns(r, until);
        }

        void engagement(WarRecord r, long t, int w) {
            int l = 1 - w;
            float lostL = 20f + rnd.nextFloat() * 90f;
            float lostW = lostL * (0.2f + 0.5f * rnd.nextFloat());
            r.add(WarRecord.ENGAGEMENTS, w, 1);
            r.add(WarRecord.FP_EST, l, Math.round(lostL));
            r.add(WarRecord.FP_EST, w, Math.round(lostW));
            r.add(WarRecord.SHIPS_EST, l, Math.round(10f * lostL / 11f));
            r.add(WarRecord.SHIPS_EST, w, Math.round(10f * lostW / 11f));
            r.addPoints(t, w, Settings.scoreEngagementWon() + lostL / Settings.scoreFpPerPoint());
            r.addPoints(t, l, lostW / Settings.scoreFpPerPoint());
        }

        void battle(WarRecord r, long t, int w, boolean withYou, int you) {
            int l = 1 - w;
            boolean big = rnd.nextFloat() < 0.35f;
            float lostL = big ? 160f + rnd.nextFloat() * 300f : 25f + rnd.nextFloat() * 110f;
            float lostW = lostL * (0.15f + 0.45f * rnd.nextFloat());
            int shipsL = Math.max(1, Math.round(lostL / (9f + 6f * rnd.nextFloat())));
            int shipsW = Math.round(lostW / (9f + 6f * rnd.nextFloat()));
            r.add(WarRecord.WON, w, 1);
            r.add(WarRecord.SHIPS, l, shipsL);
            r.add(WarRecord.SHIPS, w, shipsW);
            r.add(WarRecord.FP, l, Math.round(lostL));
            r.add(WarRecord.FP, w, Math.round(lostW));
            if (withYou) r.add(WarRecord.PLAYER_FP, you, Math.round(lostL * (0.3f + 0.5f * rnd.nextFloat())));
            r.addPoints(t, w, lostL / Settings.scoreFpPerPoint());
            r.addPoints(t, l, lostW / Settings.scoreFpPerPoint());
            if (lostL + lostW >= Settings.largeBattleFP()) {
                r.addEvent(new StoryEvent(t, StoryEvent.Kind.BATTLE, w, systemFor(r, l), shipsW, shipsL,
                        Math.round(lostW), Math.round(lostL)));
            }
        }

        void raid(WarRecord r, long t, int att) {
            r.add(WarRecord.RAIDS, att, 1);
            if (rnd.nextFloat() < 0.6f) {
                r.add(WarRecord.RAIDS_WON, att, 1);
                // Your faction's raids are partly your own, some of them tactical bombardments.
                int how = !r.id(att).equals(in.player) || rnd.nextFloat() < 0.4f ? StoryEvent.RAID_NEX
                        : rnd.nextFloat() < 0.4f ? StoryEvent.RAID_TACTICAL : StoryEvent.RAID_YOURS;
                r.addEvent(new StoryEvent(t, StoryEvent.Kind.RAID, att, colonyOf(r.id(1 - att), -1), how));
                r.addPoints(t, att, Settings.scoreRaidWon());
            }
        }

        boolean capture(WarRecord r, long t, int att) {
            int def = 1 - att;
            int left = r.startSize[def] - r.get(WarRecord.CAPTURED_SIZE, att) - r.get(WarRecord.BOMBARDED_SIZE, att);
            Inputs.Colony c = colonyRecord(r.id(def), left, true);
            if (c == null) return false;
            taken.add(c.name);
            int size = Math.max(3, Math.min(c.size, 7));
            r.add(WarRecord.INVASIONS, att, 1);
            r.add(WarRecord.INVASIONS_WON, att, 1);
            r.add(WarRecord.GROUND_WON, att, 1);
            r.add(WarRecord.CAPTURED, att, 1);
            r.add(WarRecord.CAPTURED_SIZE, att, size);
            r.addEvent(new StoryEvent(t, StoryEvent.Kind.CAPTURE, att, c.name, size));
            r.addPoints(t, att, size * Score.colonyWeight(r, att));
            return true;
        }

        void repelled(WarRecord r, long t, int def, int att) {
            r.add(WarRecord.INVASIONS, att, 1);
            r.add(WarRecord.REPELLED, def, 1);
            r.add(WarRecord.GROUND_WON, def, 1);
            r.addEvent(new StoryEvent(t, StoryEvent.Kind.REPELLED, def, colonyOf(r.id(def), -1)));
            r.addPoints(t, def, Settings.scoreInvasionRepelled());
        }

        void bombard(WarRecord r, long t, int att) {
            Inputs.Colony c = colonyRecord(r.id(1 - att), 99, false);
            int before = c == null ? 4 + rnd.nextInt(3) : Math.max(3, c.size);
            int lost = rnd.nextFloat() < 0.15f ? before : 1 + rnd.nextInt(2);
            r.add(WarRecord.BOMBARDED_SIZE, att, lost);
            r.addEvent(new StoryEvent(t, StoryEvent.Kind.BOMBARD, att, c == null ? null : c.name, lost, before,
                    Math.max(0, before - lost)));
            r.addPoints(t, att, lost * Score.colonyWeight(r, att));
        }

        /** A war that has gone quiet: nothing in its last 30 days, so it stays behind the pirate toggle. */
        void quiet(WarRecord r) {
            if (r == null) return;
            r.story.removeIf(e -> e.kind == StoryEvent.Kind.CAPTURE && now - e.time < 31L * DAY);
        }

        /** A capture by {@code side} in the last weeks, so a pirate war shows on the list. */
        void recentCapture(WarRecord r, int side) {
            Inputs.Colony c = colonyRecord(r.id(1 - side), 99, true);
            if (c != null) taken.add(c.name);
            String name = c == null ? null : c.name;
            int size = c == null ? 3 : Math.max(3, Math.min(c.size, 6));
            long t = Math.max(latestEvent(r) + DAY, now - (3 + rnd.nextInt(15)) * DAY);
            if (t > now) t = now;
            r.add(WarRecord.CAPTURED, side, 1);
            r.add(WarRecord.CAPTURED_SIZE, side, size);
            r.addEvent(new StoryEvent(t, StoryEvent.Kind.CAPTURE, side, name, size));
            r.addPoints(t, side, size * Score.colonyWeight(r, side));
        }

        String colonyOf(String factionId, int maxSize) {
            Inputs.Colony c = colonyRecord(factionId, maxSize < 0 ? 99 : maxSize, false);
            return c == null ? null : c.name;
        }

        /** One of the faction's colonies up to {@code maxSize}; with {@code untaken}, only ones not taken before. */
        Inputs.Colony colonyRecord(String factionId, int maxSize, boolean untaken) {
            List<Inputs.Colony> own = new ArrayList<>();
            for (Inputs.Colony c : in.colonies) {
                if (!factionId.equals(c.factionId) || c.size > Math.max(3, maxSize)) continue;
                if (untaken && taken.contains(c.name)) continue;
                own.add(c);
            }
            if (own.isEmpty()) return null;
            return own.get(rnd.nextInt(own.size()));
        }

        /** A system where one of the two has colonies, else any. */
        String systemFor(WarRecord r, int side) {
            List<String> mine = new ArrayList<>();
            for (Inputs.Colony c : in.colonies) {
                if (c.system != null && r.id(side).equals(c.factionId) && !mine.contains(c.system)) mine.add(c.system);
            }
            if (!mine.isEmpty()) return mine.get(rnd.nextInt(mine.size()));
            return systems.isEmpty() ? null : systems.get(rnd.nextInt(systems.size()));
        }

        boolean hasColonies(String factionId) {
            for (Inputs.Colony c : in.colonies) {
                if (factionId.equals(c.factionId)) return true;
            }
            return false;
        }

        long latestEvent(WarRecord r) {
            long t = r.start;
            for (StoryEvent e : r.story) t = Math.max(t, e.time);
            return t;
        }

        /** Wars between two alliance members would belong to the alliance war (or not happen at all). */
        boolean bothInAllianceWar(String x, String y) {
            return allianceOf.containsKey(x) && allianceOf.containsKey(y);
        }

        boolean isPirate(String id) {
            Inputs.Faction f = byId.get(id);
            return f != null && f.pirate;
        }

        int sizeOf(String id) {
            Inputs.Faction f = byId.get(id);
            return f == null ? 0 : f.size;
        }

        String leftOf(String x, String y) {
            Inputs.Faction fx = byId.get(x);
            Inputs.Faction fy = byId.get(y);
            String nx = x.equals(in.player) ? "Your faction" : fx == null ? x : fx.name;
            String ny = y.equals(in.player) ? "Your faction" : fy == null ? y : fy.name;
            int c = nx.compareToIgnoreCase(ny);
            if (c == 0) c = x.compareTo(y);
            return c <= 0 ? x : y;
        }
    }
}
