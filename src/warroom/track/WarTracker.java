package warroom.track;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.BattleAPI;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.LocationAPI;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.comm.IntelInfoPlugin;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.impl.campaign.fleets.RouteManager;
import com.fs.starfarer.api.impl.campaign.ids.MemFlags;
import com.fs.starfarer.api.impl.campaign.ids.Stats;
import com.fs.starfarer.api.impl.campaign.intel.raid.ActionStage;
import com.fs.starfarer.api.impl.campaign.intel.raid.RaidIntel;
import com.fs.starfarer.api.util.Misc;
import exerelin.campaign.PlayerFactionStore;
import exerelin.campaign.alliances.Alliance;
import exerelin.campaign.battle.NexWarSimScript;
import exerelin.campaign.battle.NexWarSimScript.FactionStrengthReport;
import exerelin.campaign.battle.NexWarSimScript.FactionStrengthReportEntry;
import exerelin.campaign.fleets.InvasionFleetManager;
import exerelin.campaign.intel.colony.ColonyExpeditionIntel;
import exerelin.campaign.intel.defensefleet.DefenseFleetIntel;
import exerelin.campaign.intel.fleets.OffensiveFleetIntel;
import exerelin.campaign.intel.fleets.OffensiveFleetIntel.OffensiveOutcome;
import exerelin.campaign.intel.groundbattle.GroundBattleIntel;
import exerelin.campaign.intel.groundbattle.GroundBattleIntel.BattleOutcome;
import exerelin.campaign.intel.invasion.InvasionIntel;
import exerelin.campaign.intel.raid.BaseStrikeIntel;
import exerelin.campaign.intel.raid.NexRaidIntel;
import exerelin.campaign.intel.satbomb.SatBombIntel;
import exerelin.utilities.NexUtilsFaction;
import exerelin.utilities.NexUtilsFleet;
import warroom.Demo;
import warroom.Fmt;
import warroom.Score;
import warroom.SectorSnapshot;
import warroom.SectorSnapshot.Rel;
import warroom.Settings;
import warroom.Turns;
import warroom.data.LogEntry;
import warroom.data.StoryEvent;
import warroom.data.WarData;
import warroom.data.WarRecord;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * Turns game events into war records, stories and momentum. Wars and peace have no event of their own,
 * so the daily {@link #check} compares every pair of factions with colonies against the known wars.
 * Events only count for pairs of factions that both have colonies (or already have a war going), so
 * fights with Remnants, derelicts and the like stay out. Everything here only reads the game.
 */
public final class WarTracker {
    /** A faction without colonies for this long is out of its wars. */
    static final long GONE_DAYS = 30L;

    /** What reading a save needs to know about factions, answered by the running game. */
    public static final WarData.Factions FACTIONS = new WarData.Factions() {
        @Override
        public boolean isPirate(String factionId) {
            try {
                return NexUtilsFaction.isPirateFaction(factionId);
            } catch (Throwable t) {
                return false;
            }
        }

        @Override
        public String name(String factionId) {
            FactionAPI f = Global.getSector().getFaction(factionId);
            return f == null ? factionId : Fmt.name(f);
        }
    };

    private WarTracker() {
    }

    private static long now() {
        return Global.getSector().getClock().getTimestamp();
    }

    // ------------------------------------------------------------------ the daily check

    /** Daily check. The first check of a save only takes stock; wars found then count from "before now". */
    public static void check() {
        check(false, false);
    }

    /**
     * @param newGame whether the first check happens at the start of a new game, so wars found then began with it
     * @param resumed whether War Room was taken out of this save for a while and is back now
     */
    public static void check(boolean newGame, boolean resumed) {
        if (Demo.isOn()) return; // the demo's made-up wars are not compared with the sector
        WarData d = WarData.get();
        SectorSnapshot.invalidate();
        SectorSnapshot s = SectorSnapshot.get();
        long now = now();
        boolean first = d.since == 0;
        if (first) d.since = now;
        // After a gap (War Room was out of this save), take stock like the first time: what changed meanwhile
        // has no date, so it is neither news nor new; wars that ended meanwhile end on the last day seen.
        boolean gap = !first && (resumed || (d.lastCheck != 0 && now - d.lastCheck > GAP_DAYS * WarData.DAY));
        boolean stock = first || gap;
        long lastSeen = gap && d.lastCheck != 0 ? Math.min(now, d.lastCheck) : now;
        try {
            Peace.scan(d, now);
        } catch (RuntimeException | LinkageError ex) {
            // without it, peace just reads "Hostilities ceased"
        }
        if (gap) d.alliancesPolled = false;
        pollAlliances(d);
        for (WarRecord r : d.active.values()) {
            if (!r.tagged) tagAlliances(d, r);
        }
        d.mergeGroups();

        Set<String> current = new LinkedHashSet<>();
        for (FactionAPI f : s.factions) current.add(f.getId());
        // A side without colonies that War Room never saw go (a 0.2 save kept only factions with colonies).
        for (WarRecord r : d.active.values()) {
            for (int side = 0; side < 2; side++) {
                String id = r.id(side);
                if (!current.contains(id) && !d.factions.contains(id) && !d.goneSince.containsKey(id)) {
                    d.goneSince.put(id, lastSeen);
                }
            }
        }
        for (String id : d.factions) {
            if (current.contains(id) || d.goneSince.containsKey(id)) continue;
            d.goneSince.put(id, lastSeen);
            if (gap) continue; // when it happened is not known
            d.addNews(new LogEntry(now, LogEntry.Kind.ELIMINATED, id, null, null, null));
            for (WarRecord r : d.active.values()) {
                if (r.involves(id)) r.addEvent(new StoryEvent(now, StoryEvent.Kind.GONE, r.side(id), null));
            }
        }
        for (String id : current) {
            boolean back = d.goneSince.remove(id) != null;
            if (!stock && (back || !d.factions.contains(id))) {
                d.addNews(new LogEntry(now, LogEntry.Kind.ARRIVED, id, null, null, null));
            }
        }
        // Factions that held colonies at the last check: a war with one that did not is first recorded, not begun.
        Set<String> before = new LinkedHashSet<>(d.factions);
        d.factions.clear();
        d.factions.addAll(current);
        // Each faction's total colony size when records began, or when it first held colonies later. A save
        // from before 0.6 has none: they come from the wars already on when records began, else from now.
        boolean seeding = d.baseline.isEmpty() && !first;
        if (seeding) seedBaseline(d);
        for (FactionAPI f : s.factions) {
            if (d.baseline.containsKey(f.getId())) continue;
            int kind = first ? WarData.BASE_START : seeding ? WarData.BASE_NOTED : WarData.BASE_LATER;
            d.baseline.put(f.getId(), new long[]{sizeOf(s, f.getId()), now, kind});
        }

        for (WarRecord r : new ArrayList<>(d.active.values())) {
            Long ga = d.goneSince.get(r.a);
            Long gb = d.goneSince.get(r.b);
            long when = gap ? lastSeen : now;
            if (ga != null && now - ga >= GONE_DAYS * WarData.DAY) end(d, r, WarRecord.END_GONE_A, 0, s, when);
            else if (gb != null && now - gb >= GONE_DAYS * WarData.DAY) end(d, r, WarRecord.END_GONE_B, 0, s, when);
        }

        for (int i = 0; i < s.size(); i++) {
            for (int j = i + 1; j < s.size(); j++) {
                String a = s.get(i).getId();
                String b = s.get(j).getId();
                WarRecord r = d.active(a, b);
                boolean war = s.rel[i][j] == Rel.WAR;
                if (war && r == null) {
                    create(d, a, b, now, newGame || (!stock && before.contains(a) && before.contains(b)), s);
                } else if (!war && r != null) {
                    // How peace came is only known for a peace made since the last check; a waiting report
                    // is used up either way.
                    int how = peaceKind(a, b, r.start);
                    end(d, r, WarRecord.END_PEACE, gap ? WarRecord.PEACE_UNKNOWN : how, s, lastSeen);
                }
            }
        }
        for (WarRecord r : d.active.values()) {
            for (int side = 0; side < 2; side++) {
                if (r.startSize[side] <= 0) r.startSize[side] = sizeOf(s, r.id(side));
            }
            // Turning points; after a gap (or for a war from before 0.8) the standing is only noted, undated.
            float share = Score.share(Score.total(r, 0), Score.total(r, 1));
            if (r.standing == WarRecord.STANDING_UNSET || stock) Turns.settle(r, share);
            else Turns.note(r, share, now);
        }
        d.lastCheck = now;
        try {
            rememberDamage(null);
        } catch (RuntimeException ex) {
            // only makes later off-screen estimates a little rougher
        }
    }

    private static void seedBaseline(WarData d) {
        List<WarRecord> all = new ArrayList<>(d.active.values());
        all.addAll(d.archive);
        for (WarRecord r : all) {
            if (r.start != d.since) continue;
            for (int side = 0; side < 2; side++) {
                if (r.startSize[side] > 0 && !d.baseline.containsKey(r.id(side))) {
                    d.baseline.put(r.id(side), new long[]{r.startSize[side], d.since, WarData.BASE_START});
                }
            }
        }
    }

    /** More days than this between two checks means War Room was not running in this save meanwhile. */
    static final long GAP_DAYS = 3L;

    private static int sizeOf(SectorSnapshot s, String factionId) {
        int i = s.indexOf(factionId);
        return i < 0 ? 0 : s.size[i];
    }

    private static WarRecord create(WarData d, String x, String y, long now, boolean known, SectorSnapshot s) {
        boolean pirate = FACTIONS.isPirate(x) || FACTIONS.isPirate(y);
        WarRecord r = new WarRecord(x, y, now, known, pirate, WarData.leftOf(x, y, FACTIONS));
        r.startSize[0] = sizeOf(s, r.a);
        r.startSize[1] = sizeOf(s, r.b);
        tagAlliances(d, r);
        r.addEvent(new StoryEvent(now, StoryEvent.Kind.BEGAN, -1, null));
        d.active.put(r.pairKey(), r);
        return r;
    }

    /** Ends a war at {@code when}, but never before its own last recorded event. */
    private static void end(WarData d, WarRecord r, int reason, int peace, SectorSnapshot s, long when) {
        long last = r.start;
        for (StoryEvent e : r.story) last = Math.max(last, e.time);
        d.end(r, Math.max(last, when), reason, peace, sizeOf(s, r.a), sizeOf(s, r.b));
    }

    private static int peaceKind(String a, String b, long warStart) {
        try {
            return Peace.take(a, b, warStart);
        } catch (RuntimeException | LinkageError ex) {
            return WarRecord.PEACE_UNKNOWN;
        }
    }

    /**
     * Notes both sides' alliances and finds the alliance war the record belongs to: wars between members of
     * the same two alliances share a group (alliances followed through merges).
     */
    private static void tagAlliances(WarData d, WarRecord r) {
        r.tagged = true;
        Alliance aa = Alliances.of(r.a);
        Alliance ab = Alliances.of(r.b);
        r.allianceIdA = aa == null ? null : aa.uuId;
        r.allianceIdB = ab == null ? null : ab.uuId;
        r.allianceA = aa == null ? null : aa.getName();
        r.allianceB = ab == null ? null : ab.getName();
        r.group = null;
        if (r.allianceIdA == null || r.allianceIdB == null) return;
        String ka = d.canonAlliance(r.allianceIdA);
        String kb = d.canonAlliance(r.allianceIdB);
        if (ka == null || kb == null || ka.equals(kb)) return;
        String pair = ka.compareTo(kb) <= 0 ? ka + "~" + kb : kb + "~" + ka;
        for (WarRecord o : d.active.values()) {
            if (o != r && o.group != null && pair.equals(d.canonPair(o.group))) {
                r.group = o.group;
                return;
            }
        }
        r.group = pair + "~" + r.start;
    }

    /** New alliances are news (Nexerelin never reports them); the first time, only take stock. */
    private static void pollAlliances(WarData d) {
        long now = now();
        for (Alliance al : Alliances.all()) {
            if (al == null || al.uuId == null) continue;
            if (!d.knownAlliances.containsKey(al.uuId) && d.alliancesPolled) {
                List<String> m = Alliances.members(al.uuId);
                d.addNews(new LogEntry(now, LogEntry.Kind.ALLIANCE_FORMED, m.size() > 0 ? m.get(0) : null,
                        m.size() > 1 ? m.get(1) : null, al.getName(), null));
            }
            d.knownAlliances.put(al.uuId, al.getName());
        }
        d.alliancesPolled = true;
    }

    /**
     * The war record of two factions. If there is none yet but both have colonies and are hostile, the war
     * began since the last daily check: it is recorded now. A side that held no colonies at that check (a
     * faction coming back, its first colony just taken; your own first colony) was hostile before War Room
     * could follow it, so its war is first recorded now rather than begun.
     */
    static WarRecord war(String x, String y) {
        if (x == null || y == null || x.equals(y)) return null;
        WarData d = WarData.get();
        WarRecord r = d.active(x, y);
        if (r != null) return r;
        if (d.since == 0 || !holds(d, x) || !holds(d, y)) return null;
        FactionAPI fx = Global.getSector().getFaction(x);
        FactionAPI fy = Global.getSector().getFaction(y);
        if (fx == null || fy == null || !fx.isHostileTo(fy)) return null;
        boolean known = d.factions.contains(x) && d.factions.contains(y);
        return create(d, x, y, now(), known, SectorSnapshot.get());
    }

    /** Held colonies at the last check, or holds one now (its first may have been taken since). */
    private static boolean holds(WarData d, String id) {
        if (d.factions.contains(id)) return true;
        for (MarketAPI m : Misc.getFactionMarkets(id)) {
            if (!m.isHidden() && !m.isPlanetConditionMarketOnly()) return true;
        }
        return false;
    }

    // ------------------------------------------------------------------ battles in space

    /** The faction a fleet fights for; your own fleet fights for the faction you serve, as in Nexerelin. */
    private static String factionOf(CampaignFleetAPI fleet) {
        if (fleet.isPlayerFleet()) return PlayerFactionStore.getPlayerFactionId();
        return fleet.getFaction() == null ? null : fleet.getFaction().getId();
    }

    /** Per faction on one side: {fleet points at the start, ships lost, fleet points lost}. */
    private static Map<String, float[]> sideLosses(List<CampaignFleetAPI> side) {
        Map<String, float[]> out = new LinkedHashMap<>();
        if (side == null) return out;
        for (CampaignFleetAPI fleet : side) {
            String id = factionOf(fleet);
            if (id == null || fleet.getFleetData() == null) continue;
            float[] l = out.computeIfAbsent(id, k -> new float[3]);
            List<FleetMemberAPI> before = fleet.getFleetData().getSnapshot();
            if (before != null) {
                for (FleetMemberAPI m : before) l[0] += m.getFleetPointCost();
            }
            for (FleetMemberAPI m : Misc.getSnapshotMembersLost(fleet)) {
                l[1]++;
                l[2] += m.getFleetPointCost();
            }
        }
        return out;
    }

    private static boolean hasPlayer(List<CampaignFleetAPI> side) {
        if (side == null) return false;
        for (CampaignFleetAPI f : side) {
            if (f.isPlayerFleet()) return true;
        }
        return false;
    }

    /**
     * Ships and fleet points lost so far in each battle not yet finished: {ships of side one, of side two,
     * fleet points of side one, of side two}. Weak keys, so a battle that never finishes is simply dropped.
     */
    private static final Map<BattleAPI, float[]> battles = new WeakHashMap<>();

    /** Off-screen fights under way: each route's strength when the fight began, as seen after each round. */
    private static final Map<NexWarSimScript, Map<RouteManager.RouteData, Float>> simStarts = new WeakHashMap<>();
    /** Each route's damage when War Room last looked, to tell a fight's own damage from older damage. */
    private static final Map<RouteManager.RouteData, Float> lastDamage = new WeakHashMap<>();

    static void resetBattles() {
        battles.clear();
        simStarts.clear();
        lastDamage.clear();
    }

    /**
     * One round of a battle: once per autoresolve round between AI fleets (each round's snapshot covers only
     * that round), once for a battle of yours. The losses are split over every pair at war across the two
     * sides, by each faction's share of its side's fleet points, and added to the battle's running total.
     */
    static void battleRound(BattleAPI battle) {
        if (battle == null || !battle.hasSnapshots()) return;
        List<CampaignFleetAPI> one = battle.getSnapshotSideOne();
        List<CampaignFleetAPI> two = battle.getSnapshotSideTwo();
        Map<String, float[]> l1 = sideLosses(one);
        Map<String, float[]> l2 = sideLosses(two);
        float[] t1 = total(l1);
        float[] t2 = total(l2);
        if (t1[1] + t2[1] == 0f) return; // nobody lost a ship this round
        float[] acc = battles.computeIfAbsent(battle, k -> new float[4]);
        acc[0] += t1[1];
        acc[1] += t2[1];
        acc[2] += t1[2];
        acc[3] += t2[2];

        long now = now();
        // Your part in what your side destroyed, as the game works it out for bounties and commissions.
        float frac = Math.max(0f, Math.min(1f, battle.getPlayerInvolvementFraction()));
        boolean player1 = hasPlayer(one);
        boolean player2 = hasPlayer(two);
        List<String> f1 = new ArrayList<>(l1.keySet());
        List<String> f2 = new ArrayList<>(l2.keySet());
        float[] share1 = shares(l1, f1, t1[0]);
        float[] share2 = shares(l2, f2, t2[0]);
        for (int j = 0; j < f2.size(); j++) {
            float[] y = l2.get(f2.get(j));
            // Whole ships, handed out so that the parts add up to what was lost.
            int[] shipsOf2 = apportion(Math.round(y[1]), share1);
            for (int i = 0; i < f1.size(); i++) {
                float[] x = l1.get(f1.get(i));
                WarRecord r = war(f1.get(i), f2.get(j));
                if (r == null) continue;
                int sx = r.side(f1.get(i));
                int sy = 1 - sx;
                int shipsOf1 = apportion(Math.round(x[1]), share2)[j];
                // What y lost, credited to x by x's share of its side, and the other way round.
                float lostY = y[2] * share1[i];
                float lostX = x[2] * share2[j];
                r.add(WarRecord.SHIPS, sy, shipsOf2[i]);
                r.add(WarRecord.SHIPS, sx, shipsOf1);
                r.add(WarRecord.FP, sy, Math.round(lostY));
                r.add(WarRecord.FP, sx, Math.round(lostX));
                if (player1) r.add(WarRecord.PLAYER_FP, sx, Math.round(lostY * frac));
                if (player2) r.add(WarRecord.PLAYER_FP, sy, Math.round(lostX * frac));
                r.addPoints(now, sx, lostY / Settings.scoreFpPerPoint());
                r.addPoints(now, sy, lostX / Settings.scoreFpPerPoint());
            }
        }
    }

    /**
     * The end of a battle with losses. The win goes to the main pair only (the two sides' leading fleets'
     * factions), so summing an alliance war's fronts counts each battle once.
     */
    static void battleFinished(CampaignFleetAPI winner, BattleAPI battle) {
        if (battle == null) return;
        float[] acc = battles.remove(battle);
        if (winner == null || acc == null || acc[0] + acc[1] == 0f) return; // nobody lost a ship: someone got away
        boolean snap = battle.hasSnapshots();
        List<CampaignFleetAPI> one = snap ? battle.getSnapshotSideOne() : battle.getSideOne();
        List<CampaignFleetAPI> two = snap ? battle.getSnapshotSideTwo() : battle.getSideTwo();
        boolean winOne;
        if (one != null && one.contains(winner)) winOne = true;
        else if (two != null && two.contains(winner)) winOne = false;
        else return;
        List<CampaignFleetAPI> win = winOne ? one : two;
        List<CampaignFleetAPI> lose = winOne ? two : one;
        CampaignFleetAPI loser = lose == null || lose.isEmpty() ? null : battle.getPrimary(lose);
        String mainW = factionOf(winner);
        String mainL = loser == null ? null : factionOf(loser);
        WarRecord main = mainW == null || mainL == null ? null : war(mainW, mainL);
        if (main == null && (winner.isPlayerFleet() || (loser != null && loser.isPlayerFleet()))) {
            // You lead your side in a fight you started; without a war of your own, take the largest other fleet.
            CampaignFleetAPI w2 = winner.isPlayerFleet() && win != null ? battle.getPrimary(win, true) : winner;
            CampaignFleetAPI l2 = loser != null && loser.isPlayerFleet() ? battle.getPrimary(lose, true) : loser;
            String fw = w2 == null ? null : factionOf(w2);
            String fl = l2 == null ? null : factionOf(l2);
            WarRecord alt = fw == null || fl == null ? null : war(fw, fl);
            if (alt != null) {
                main = alt;
                mainW = fw;
            }
        }
        if (main == null) return;
        int s = main.side(mainW);
        main.add(WarRecord.WON, s, 1);
        float wShips = winOne ? acc[0] : acc[1];
        float lShips = winOne ? acc[1] : acc[0];
        float wFp = winOne ? acc[2] : acc[3];
        float lFp = winOne ? acc[3] : acc[2];
        if (wFp + lFp >= Settings.largeBattleFP()) {
            main.addEvent(new StoryEvent(now(), StoryEvent.Kind.BATTLE, s, where(winner),
                    Math.round(wShips), Math.round(lShips), Math.round(wFp), Math.round(lFp)));
        }
    }

    private static float[] total(Map<String, float[]> byFaction) {
        float[] t = new float[3];
        for (float[] v : byFaction.values()) {
            for (int k = 0; k < 3; k++) t[k] += v[k];
        }
        return t;
    }

    /** Each faction's share of its side's fleet points at the start of the round (equal shares if unknown). */
    private static float[] shares(Map<String, float[]> byFaction, List<String> order, float total) {
        float[] out = new float[order.size()];
        for (int i = 0; i < out.length; i++) {
            out[i] = total > 0f ? byFaction.get(order.get(i))[0] / total : 1f / out.length;
        }
        return out;
    }

    /** {@code total} whole units split by {@code shares}, largest remainders first, so the parts add up. */
    public static int[] apportion(int total, float[] shares) {
        int[] out = new int[shares.length];
        if (total <= 0 || shares.length == 0) return out;
        float[] rest = new float[shares.length];
        int given = 0;
        for (int i = 0; i < shares.length; i++) {
            float exact = total * Math.max(0f, shares[i]);
            out[i] = (int) Math.floor(exact);
            rest[i] = exact - out[i];
            given += out[i];
        }
        for (int left = total - given; left > 0; left--) {
            int best = 0;
            for (int i = 1; i < rest.length; i++) {
                if (rest[i] > rest[best]) best = i;
            }
            out[best]++;
            rest[best] = -1f;
        }
        return out;
    }

    private static String where(CampaignFleetAPI fleet) {
        LocationAPI loc = fleet.getContainingLocation();
        if (loc == null) return null;
        if (loc instanceof StarSystemAPI) return ((StarSystemAPI) loc).getBaseName();
        return loc.getName();
    }

    // ------------------------------------------------------------------ fights Nexerelin settles off-screen

    /**
     * One of Nexerelin's off-screen fights (an invasion, raid or strike reaching its target while you are far
     * away). Side 1 is always the attacker. The winner gets an engagement; both sides get estimated losses:
     * fleet points from the damage the fight applied to the abstract (not spawned) fleets, ships from that
     * divided by the faction's typical ship size. Shown as estimates, never as counted battles.
     */
    static void offscreen(NexWarSimScript sim, Boolean result) {
        if (sim == null) return;
        Map<RouteManager.RouteData, Float> seen = simStarts.remove(sim);
        LocationAPI loc = sim.getLoc();
        try {
            if (result == null) return;
            FactionStrengthReport f1 = sim.getFsr1();
            FactionStrengthReport f2 = sim.getFsr2();
            FactionAPI s1 = sim.getSide1();
            FactionAPI s2 = sim.getSide2();
            if (f1 == null || f2 == null || s1 == null || s2 == null || loc == null) return;
            if (f1.startingStrength <= 0f || f2.startingStrength <= 0f) return; // nobody to fight
            WarRecord r = war(s1.getId(), s2.getId());
            if (r == null) return;
            long now = now();
            int side1 = r.side(s1.getId());
            int winner = Boolean.TRUE.equals(result) ? side1 : 1 - side1;
            r.add(WarRecord.ENGAGEMENTS, winner, 1);
            r.addPoints(now, winner, Settings.scoreEngagementWon());
            Map<String, Float> lost1 = routeFpLost(f1, s1, s2, loc, seen);
            Map<String, Float> lost2 = routeFpLost(f2, s2, s1, loc, seen);
            bookLosses(lost1, s2.getId(), now);
            bookLosses(lost2, s1.getId(), now);
            // A large one goes into the war log as a major battle, marked reported: its losses are estimates.
            float fp1 = 0f;
            float fp2 = 0f;
            int ships1 = 0;
            int ships2 = 0;
            for (Map.Entry<String, Float> e : lost1.entrySet()) {
                fp1 += Math.max(0f, e.getValue());
                ships1 += Math.round(Math.max(0f, e.getValue()) / ShipSize.of(e.getKey()));
            }
            for (Map.Entry<String, Float> e : lost2.entrySet()) {
                fp2 += Math.max(0f, e.getValue());
                ships2 += Math.round(Math.max(0f, e.getValue()) / ShipSize.of(e.getKey()));
            }
            if (fp1 + fp2 >= Settings.largeBattleFP()) {
                boolean firstWon = winner == side1;
                String place = loc instanceof StarSystemAPI ? ((StarSystemAPI) loc).getBaseName() : loc.getName();
                r.addEvent(new StoryEvent(now, StoryEvent.Kind.BATTLE, winner, place, firstWon ? ships1 : ships2,
                        firstWon ? ships2 : ships1, Math.round(firstWon ? fp1 : fp2), Math.round(firstWon ? fp2 : fp1), 1));
            }
        } finally {
            // After the split, so the next fight here tells its own damage from this one's.
            rememberDamage(loc);
        }
    }

    /** After each round of an off-screen fight: note the strength its routes began with, before any withdraw. */
    static void simRound(NexWarSimScript sim) {
        if (sim == null) return;
        Map<RouteManager.RouteData, Float> m = simStarts.computeIfAbsent(sim, k -> new IdentityHashMap<>());
        for (FactionStrengthReport fsr : new FactionStrengthReport[]{sim.getFsr1(), sim.getFsr2()}) {
            if (fsr == null) continue;
            for (FactionStrengthReportEntry e : new ArrayList<>(fsr.entries)) {
                if (e != null && e.fleet == null && e.route != null) m.putIfAbsent(e.route, e.startingStrength);
            }
        }
    }

    /** Notes the damage of every route in a location (all locations for null). */
    private static void rememberDamage(LocationAPI loc) {
        RouteManager rm = RouteManager.getInstance();
        if (rm == null) return;
        List<LocationAPI> where = new ArrayList<>();
        if (loc != null) where.add(loc);
        else where.addAll(Global.getSector().getAllLocations());
        for (LocationAPI l : where) {
            for (RouteManager.RouteData rt : new ArrayList<>(rm.getRoutesInLocation(l))) {
                RouteManager.OptionalFleetData x = rt.getExtra();
                if (x != null) lastDamage.put(rt, x.damage == null ? 0f : x.damage);
            }
        }
    }

    /**
     * Estimated losses of the factions that fought on one side (allies and others hostile to the enemy
     * join in), each in its own war with the enemy; a faction without one is left out.
     */
    private static void bookLosses(Map<String, Float> lost, String enemy, long now) {
        for (Map.Entry<String, Float> e : lost.entrySet()) {
            float fp = e.getValue();
            if (fp <= 0f) continue;
            WarRecord r = war(e.getKey(), enemy);
            if (r == null) continue;
            int side = r.side(e.getKey());
            estimate(r, side, e.getKey(), fp);
            r.addPoints(now, 1 - side, fp / Settings.scoreFpPerPoint());
        }
    }

    private static void estimate(WarRecord r, int side, String factionId, float fp) {
        if (fp <= 0f) return;
        r.add(WarRecord.FP_EST, side, Math.round(fp));
        // In tenths, so many small fights still add up to whole ships.
        r.add(WarRecord.SHIPS_EST, side, Math.round(10f * fp / ShipSize.of(factionId)));
    }

    /**
     * Fleet points one side's abstract fleets lost in the fight, per faction: the strength their routes lost
     * (start minus now, so withdrawals don't count) times the routes' fleet points per strength. Shared out
     * by what each route lost in this fight; a route that withdrew in the first round counts the damage it
     * took since War Room last looked at it.
     */
    private static Map<String, Float> routeFpLost(FactionStrengthReport fsr, FactionAPI side, FactionAPI enemy,
                                                  LocationAPI loc, Map<RouteManager.RouteData, Float> seen) {
        Map<String, Float> out = new LinkedHashMap<>();
        float fleetStart = 0f;
        // Each route's strength when this fight began, from the fight's own list (withdrawn routes leave it).
        Map<RouteManager.RouteData, Float> start = new IdentityHashMap<>();
        for (FactionStrengthReportEntry e : new ArrayList<>(fsr.entries)) {
            if (e == null) continue;
            if (e.fleet != null) fleetStart += e.startingStrength;
            else if (e.route != null) start.put(e.route, e.startingStrength);
        }
        // Routes that withdrew after the first round were seen in the rounds before.
        if (seen != null) {
            for (Map.Entry<RouteManager.RouteData, Float> e : seen.entrySet()) start.putIfAbsent(e.getKey(), e.getValue());
        }
        float routeStart = Math.max(0f, fsr.startingStrength - fleetStart);
        // Per faction: {strength now, lost in this fight, damage carried by routes that withdrew, fleet points,
        // full strength of the routes with fleet points}.
        Map<String, float[]> per = new LinkedHashMap<>();
        RouteManager rm = RouteManager.getInstance();
        if (rm == null) return out;
        for (RouteManager.RouteData rt : new ArrayList<>(rm.getRoutesInLocation(loc))) {
            RouteManager.OptionalFleetData x = rt.getExtra();
            if (x == null || x.strength == null || x.strength <= 0f || rt.getFactionId() == null) continue;
            FactionAPI rf = Global.getSector().getFaction(rt.getFactionId());
            if (rf == null || !NexWarSimScript.willFactionSideWithUs(rf, side, enemy)) continue;
            CampaignFleetAPI af = rt.getActiveFleet();
            if (af != null && af.getContainingLocation() == loc && !af.isStationMode() && !af.isPlayerFleet()
                    && af.getFaction() != null && !isTrade(af)
                    && NexWarSimScript.willFactionSideWithUs(af.getFaction(), side, enemy)) {
                continue; // the fight counted this route as a spawned fleet instead
            }
            float[] v = per.computeIfAbsent(rt.getFactionId(), k -> new float[5]);
            float strNow = x.getStrengthModifiedByDamage();
            v[0] += strNow;
            Float s0 = start.get(rt);
            if (s0 != null) {
                v[1] += Math.max(0f, s0 - strNow);
            } else {
                // Withdrew in the first round: its damage since War Room last looked, else all it carries.
                Float d0 = lastDamage.get(rt);
                float cur = x.damage == null ? 0f : x.damage;
                float before = d0 != null && d0 <= cur ? x.strength * Math.max(0f, 1f - d0) : x.strength;
                v[2] += Math.max(0f, before - strNow);
            }
            float base = NexUtilsFleet.getRouteFP(rt);
            if (base > 0f && !Float.isNaN(base) && !Float.isInfinite(base)) {
                v[3] += base * sizeMult(rt, rf);
                v[4] += x.strength;
            }
        }
        float routeEnd = 0f;
        float exact = 0f;
        float carried = 0f;
        float fp = 0f;
        float str = 0f;
        for (float[] v : per.values()) {
            routeEnd += v[0];
            exact += v[1];
            carried += v[2];
            fp += v[3];
            str += v[4];
        }
        float lost = Math.max(0f, routeStart - routeEnd);
        if (lost <= 0f) return out;
        // The withdrawn routes take what the others' own losses don't explain; the parts add up to the total.
        float rest = carried > 0f ? Math.max(0f, lost - exact) : 0f;
        float scale = exact > 0f ? (lost - rest) / exact : 0f;
        float fpPerStr = str > 0f ? fp / str : 0.5f;
        for (Map.Entry<String, float[]> e : per.entrySet()) {
            float[] v = e.getValue();
            float lostF = exact + carried > 0f
                    ? v[1] * scale + (carried > 0f ? rest * v[2] / carried : 0f)
                    : lost * (str > 0f ? v[4] / str : 1f / per.size());
            float own = v[4] > 0f ? v[3] / v[4] : fpPerStr;
            out.put(e.getKey(), lostF * own);
        }
        return out;
    }

    private static boolean isTrade(CampaignFleetAPI f) {
        MemoryAPI mem = f.getMemoryWithoutUpdate();
        return mem != null && (mem.getBoolean(MemFlags.MEMORY_KEY_TRADE_FLEET) || mem.getBoolean(MemFlags.MEMORY_KEY_SMUGGLER));
    }

    private static float sizeMult(RouteManager.RouteData r, FactionAPI rf) {
        try {
            boolean nex = r.getSpawner() != null && r.getSpawner().getClass().getName().startsWith("exerelin.");
            if (nex) return rf.getDoctrine() != null ? InvasionFleetManager.getFactionDoctrineFleetSizeMult(rf) : 1f;
            if (r.getMarket() != null) {
                return r.getMarket().getStats().getDynamic().getMod(Stats.COMBAT_FLEET_SIZE_MULT).computeEffective(0f);
            }
        } catch (RuntimeException ex) {
            // fall through
        }
        return 1f;
    }

    // ------------------------------------------------------------------ colonies

    /** A colony changed hands by force (invasion, rebellion or the like). */
    static void capture(MarketAPI market, FactionAPI newOwner, FactionAPI oldOwner) {
        if (market == null || newOwner == null || oldOwner == null || newOwner == oldOwner) return;
        long now = now();
        int size = market.getSize();
        WarRecord r = war(newOwner.getId(), oldOwner.getId());
        if (r == null) {
            WarData.get().addNews(new LogEntry(now, LogEntry.Kind.CAPTURE, newOwner.getId(), oldOwner.getId(),
                    market.getName(), null, size));
            return;
        }
        int side = r.side(newOwner.getId());
        r.add(WarRecord.CAPTURED, side, 1);
        r.add(WarRecord.CAPTURED_SIZE, side, size);
        r.addEvent(new StoryEvent(now, StoryEvent.Kind.CAPTURE, side, market.getName(), size));
        r.addPoints(now, side, size * Score.colonyWeight(r, side));
    }

    /**
     * A colony destroyed or shrunk by saturation bombardment.
     * @param before the colony's size before the bombardment; {@code lost} of it is gone (all if destroyed)
     */
    static void bombarded(MarketAPI m, String attacker, String defender, int lost, int before) {
        if (m == null || attacker == null || defender == null || attacker.equals(defender) || lost <= 0 || m.isHidden()) {
            return;
        }
        WarRecord r = war(attacker, defender);
        if (r == null) return;
        long now = now();
        int side = r.side(attacker);
        r.add(WarRecord.BOMBARDED_SIZE, side, lost);
        r.addEvent(new StoryEvent(now, StoryEvent.Kind.BOMBARD, side, m.getName(), lost, before,
                Math.max(0, before - lost)));
        r.addPoints(now, side, lost * Score.colonyWeight(r, side));
    }

    // ------------------------------------------------------------------ raids and invasions

    /**
     * Your own successful raid (StoryEvent.RAID_YOURS) or tactical bombardment (RAID_TACTICAL) on a colony,
     * for the faction you fight for. Nexerelin reports only its own fleets' raids, so these are counted here.
     */
    static void playerRaid(MarketAPI m, int how) {
        if (m == null || m.isHidden()) return;
        String attacker = PlayerFactionStore.getPlayerFactionId();
        String defender = m.getFactionId();
        if (attacker == null || defender == null || attacker.equals(defender)) return;
        WarRecord r = war(attacker, defender);
        if (r == null) return;
        long now = now();
        int side = r.side(attacker);
        r.add(WarRecord.RAIDS, side, 1);
        r.add(WarRecord.RAIDS_WON, side, 1);
        r.addEvent(new StoryEvent(now, StoryEvent.Kind.RAID, side, m.getName(), how));
        r.addPoints(now, side, Settings.scoreRaidWon());
    }

    /** What kind of attack a Nexerelin offensive is: 1 raid, 2 invasion, 0 neither (expeditions, defense). */
    static int offensiveKind(Object intel) {
        if (intel instanceof InvasionIntel) return 2;
        if (intel instanceof NexRaidIntel || intel instanceof SatBombIntel) return 1;
        return 0;
    }

    /** A Nexerelin raid or invasion ended. For an invasion, the ground battle (if any) decides the result. */
    static void offensiveEnded(RaidIntel raw, FactionAPI attacker, FactionAPI defender, MarketAPI target) {
        if (!(raw instanceof OffensiveFleetIntel) || attacker == null) return;
        OffensiveFleetIntel intel = (OffensiveFleetIntel) raw;
        int kind = offensiveKind(intel);
        if (kind == 0) return;
        OffensiveOutcome o = intel.getOutcome();
        long now = now();
        if (kind == 2) {
            InvasionIntel inv = (InvasionIntel) intel;
            FactionAPI owner = target == null ? null : target.getFaction();
            boolean hostileNow = owner != null && owner != attacker && attacker.isHostileTo(owner);
            FactionAPI vs = hostileNow ? owner : defender; // after a capture the colony is already the attacker's
            if (vs != null && o != OffensiveOutcome.NO_LONGER_HOSTILE && launched(inv)) {
                WarRecord r = war(attacker.getId(), vs.getId());
                if (r != null) r.add(WarRecord.INVASIONS, r.side(attacker.getId()), 1);
            }
            if (hostileNow) invasionEnded(inv, attacker, owner, target);
            return;
        }
        // A saturation bombardment is no raid: it is recorded (and scores) as the bombardment itself.
        if (intel instanceof SatBombIntel) return;
        boolean won = raidSucceeded(intel);
        if ((o == OffensiveOutcome.NO_LONGER_HOSTILE && !won) || defender == null || !launched(intel)) return;
        WarRecord r = war(attacker.getId(), defender.getId());
        if (r == null) return;
        int side = r.side(attacker.getId());
        r.add(WarRecord.RAIDS, side, 1);
        if (won) {
            r.add(WarRecord.RAIDS_WON, side, 1);
            r.addEvent(new StoryEvent(now, StoryEvent.Kind.RAID, side, target == null ? null : target.getName()));
            r.addPoints(now, side, Settings.scoreRaidWon());
        }
    }

    /** Reached the travel stage or later; false for aborts while organizing or assembling. */
    private static boolean launched(OffensiveFleetIntel intel) {
        ActionStage a = intel.getActionStage();
        if (a == null) return false;
        int act = intel.getStageIndex(a);
        return act > 0 && intel.getCurrentStage() >= act - 1;
    }

    private static boolean raidSucceeded(OffensiveFleetIntel intel) {
        if (intel instanceof BaseStrikeIntel) return intel.getOutcome() == OffensiveOutcome.SUCCESS;
        if (intel instanceof NexRaidIntel) return intel.isSucceeded();
        return false;
    }

    /** The end of an invasion that never landed troops: beaten in space at its target, or a failed legacy invasion. */
    private static void invasionEnded(InvasionIntel inv, FactionAPI attacker, FactionAPI owner, MarketAPI target) {
        if (inv.getGroundBattle() != null) return; // troops landed: the ground battle decides
        ActionStage a = inv.getActionStage();
        if (a == null) return;
        int act = inv.getStageIndex(a);
        if (act <= 0 || inv.getCurrentStage() < act) return;
        OffensiveOutcome o = inv.getOutcome();
        if (o == OffensiveOutcome.TASK_FORCE_DEFEATED
                || (o == OffensiveOutcome.FAIL && GroundBattleIntel.getOngoing(target) == null)) {
            repelled(attacker, owner, target);
        }
    }

    /** A ground battle ended; an attacker victory is a successful invasion, a defender victory a repelled one. */
    static void groundBattleEnded(GroundBattleIntel gb) {
        if (gb == null || !gb.isEndIfPeace()) return; // Nexerelin's tutorial battle
        BattleOutcome o = gb.getOutcome();
        if (o != BattleOutcome.ATTACKER_VICTORY && o != BattleOutcome.DEFENDER_VICTORY) return;
        FactionAPI att = gb.getSide(true).getFaction();
        FactionAPI def = gb.getSide(false).getFaction();
        if (att == null || def == null) return;
        WarRecord r = war(att.getId(), def.getId());
        if (r == null) return;
        int sa = r.side(att.getId());
        // Your own invasions have no Nexerelin invasion fleet; count them as launched here.
        if (gb.isPlayerInitiated()) r.add(WarRecord.INVASIONS, sa, 1);
        if (o == BattleOutcome.ATTACKER_VICTORY) {
            r.add(WarRecord.GROUND_WON, sa, 1);
            r.add(WarRecord.INVASIONS_WON, sa, 1);
        } else {
            r.add(WarRecord.GROUND_WON, 1 - sa, 1);
            repelledIn(r, 1 - sa, gb.getMarket());
        }
    }

    /** An invasion by {@code attacker} on {@code defender}'s colony was beaten back. */
    static void repelled(FactionAPI attacker, FactionAPI defender, MarketAPI target) {
        if (attacker == null || defender == null || attacker == defender) return;
        WarRecord r = war(attacker.getId(), defender.getId());
        if (r != null) repelledIn(r, r.side(defender.getId()), target);
    }

    private static void repelledIn(WarRecord r, int defenderSide, MarketAPI target) {
        long now = now();
        r.add(WarRecord.REPELLED, defenderSide, 1);
        r.addEvent(new StoryEvent(now, StoryEvent.Kind.REPELLED, defenderSide, target == null ? null : target.getName()));
        r.addPoints(now, defenderSide, Settings.scoreInvasionRepelled());
    }

    // ------------------------------------------------------------------ alliances

    static void allianceJoined(Alliance al, FactionAPI f) {
        if (al == null || f == null) return;
        WarData d = WarData.get();
        pollAlliances(d);
        d.addNews(new LogEntry(now(), LogEntry.Kind.ALLIANCE_JOINED, f.getId(), null, al.getName(), null));
    }

    static void allianceLeft(Alliance al, FactionAPI f) {
        if (al == null || f == null) return;
        WarData d = WarData.get();
        pollAlliances(d);
        d.addNews(new LogEntry(now(), LogEntry.Kind.ALLIANCE_LEFT, f.getId(), null, al.getName(), null));
    }

    static void allianceDissolved(Alliance al) {
        if (al == null) return;
        WarData d = WarData.get();
        if (al.uuId != null) d.knownAlliances.put(al.uuId, al.getName());
        d.addNews(new LogEntry(now(), LogEntry.Kind.ALLIANCE_DISSOLVED, null, null, al.getName(), null));
    }

    /**
     * {@code other} merged into {@code into}. Nexerelin first reports every member leaving {@code other} and
     * {@code other} dissolving; those entries are replaced by the one merge entry.
     */
    static void alliancesMerged(Alliance into, Alliance other) {
        if (into == null || other == null) return;
        WarData d = WarData.get();
        long now = now();
        noteMerge(d, into, other);
        String gone = other.getName();
        d.news.removeIf(e -> e.time == now && gone != null && gone.equals(e.text)
                && (e.kind == LogEntry.Kind.ALLIANCE_LEFT || e.kind == LogEntry.Kind.ALLIANCE_DISSOLVED));
        d.addNews(new LogEntry(now, LogEntry.Kind.ALLIANCE_MERGED, null, null, into.getName(), other.getName()));
    }

    /**
     * Notes in {@code d} that {@code other} merged into {@code into} and joins their alliance wars. Also used
     * for the real records while the demo is shown: a merge can't be pieced together later.
     */
    public static void noteMerge(WarData d, Alliance into, Alliance other) {
        if (d == null || into == null || other == null) return;
        if (other.uuId != null && into.uuId != null) d.mergedInto.put(other.uuId, into.uuId);
        d.mergeGroups();
        if (other.uuId != null) d.knownAlliances.put(other.uuId, other.getName());
    }

    // ------------------------------------------------------------------ live operations

    /** A raid or invasion the player knows about, being prepared or under way. */
    public static final class Operation {
        public final IntelInfoPlugin intel;
        public final boolean invasion;
        public final String attacker;
        public final String target;

        Operation(IntelInfoPlugin intel, boolean invasion, String attacker, String target) {
            this.intel = intel;
            this.invasion = invasion;
            this.attacker = attacker;
            this.target = target;
        }
    }

    /** Whether an intel item is shown in the player's intel list. */
    public static boolean visible(IntelInfoPlugin p) {
        return p != null && Global.getSector().getIntelManager().hasIntel(p) && !p.isHidden() && !p.isEnded()
                && p.getPlayerVisibleTimestamp() != null;
    }

    /** Raids and invasions between two factions that the player knows about and that are still going. */
    public static List<Operation> operations(String x, String y) {
        List<Operation> out = new ArrayList<>();
        for (IntelInfoPlugin p : Global.getSector().getIntelManager().getIntel()) {
            if (!(p instanceof OffensiveFleetIntel) || p instanceof DefenseFleetIntel || p instanceof ColonyExpeditionIntel) {
                continue;
            }
            int kind = offensiveKind(p);
            if (kind == 0 || !visible(p)) continue;
            OffensiveFleetIntel o = (OffensiveFleetIntel) p;
            if (o.isEnding() || o.getOutcome() != null || o.getFaction() == null) continue;
            String att = o.getFaction().getId();
            String def = o.getTargetFaction() == null ? null : o.getTargetFaction().getId();
            String owner = o.getTarget() == null || o.getTarget().getFaction() == null ? null : o.getTarget().getFaction().getId();
            boolean match = (att.equals(x) && (y.equals(def) || y.equals(owner)))
                    || (att.equals(y) && (x.equals(def) || x.equals(owner)));
            if (!match) continue;
            out.add(new Operation(p, kind == 2, att, o.getTarget() == null ? null : o.getTarget().getName()));
        }
        return out;
    }

    /** Raids and invasions under way per war, by pair key: {raids by a, raids by b, invasions by a, invasions by b}. */
    public static Map<String, int[]> activeOffensives() {
        Map<String, int[]> out = new HashMap<>();
        for (IntelInfoPlugin p : Global.getSector().getIntelManager().getIntel()) {
            int kind = offensiveKind(p);
            if (kind == 0) continue;
            OffensiveFleetIntel o = (OffensiveFleetIntel) p;
            if (o.isEnding() || o.isEnded() || o.getOutcome() != null) continue;
            FactionAPI att = o.getFaction();
            FactionAPI def = o.getTargetFaction();
            if (att == null || def == null || att == def) continue;
            int side = att.getId().compareTo(def.getId()) <= 0 ? 0 : 1; // as in WarRecord
            out.computeIfAbsent(WarRecord.pairKey(att.getId(), def.getId()), k -> new int[4])[(kind == 2 ? 2 : 0) + side]++;
        }
        return out;
    }
}
