package warroom;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.LocationAPI;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import exerelin.campaign.AllianceManager;
import exerelin.campaign.DiplomacyManager;
import exerelin.campaign.SectorManager;
import exerelin.campaign.alliances.Alliance;
import exerelin.campaign.econ.FleetPoolManager;
import exerelin.campaign.econ.GroundPoolManager;
import exerelin.campaign.fleets.InvasionFleetManager;
import exerelin.utilities.NexUtilsFaction;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The sector's diplomatic state, read fresh for the panels and never saved. The factions are those with at
 * least one visible colony, in matrix order: alliance blocks first (largest alliance first), then the other
 * factions by total colony size, pirate factions last.
 * <p>
 * Nexerelin values are read without side effects. Several of its getters insert a default entry for a
 * faction they don't know yet (e.g. war weariness for independents), which would change how Nexerelin
 * treats that faction from then on, so those are only called for factions Nexerelin already manages.
 */
public final class SectorSnapshot {
    /** How two factions stand. Relations are symmetric, so rel[i][j] == rel[j][i]. */
    public enum Rel { SELF, WAR, ALLIANCE, HOSTILE, NEUTRAL, FRIENDLY }

    /** Nexerelin's war resources and the fleets in space; NaN where Nexerelin doesn't track a value. */
    public static final class Strength {
        public float invasionPoints = Float.NaN;
        public float invasionPointsMax = Float.NaN;
        public float fleetPool = Float.NaN;
        public float fleetPoolMax = Float.NaN;
        public float troops = Float.NaN;
        public float troopsMax = Float.NaN;
        public int fleets;
        public float fleetPoints;
    }

    private static WeakReference<SectorSnapshot> cached = new WeakReference<>(null);

    public final long timestamp;
    public final List<FactionAPI> factions;
    public final Rel[][] rel;
    public final Alliance[] alliance;
    public final int[] colonies;
    public final int[] size;
    public final boolean[] pirate;
    /** War weariness as Nexerelin's diplomacy profile shows it; NaN where Nexerelin doesn't track it. */
    public final float[] weariness;
    /** Number of wars per faction, counting only factions with colonies. */
    public final int[] wars;
    /** Every pair at war, as {i, j} with i before j in matrix order. */
    public final List<int[]> warPairs = new ArrayList<>();
    /** Alliances with at least one member that has colonies, largest first. */
    public final List<Alliance> alliances;
    private final Map<String, Integer> index = new HashMap<>();
    private Strength[] strength;

    private SectorSnapshot(long timestamp, List<FactionAPI> factions, List<Alliance> alliances, Map<String, int[]> stats) {
        this.timestamp = timestamp;
        this.factions = factions;
        this.alliances = alliances;
        int n = factions.size();
        rel = new Rel[n][n];
        alliance = new Alliance[n];
        colonies = new int[n];
        size = new int[n];
        pirate = new boolean[n];
        weariness = new float[n];
        wars = new int[n];
        for (int i = 0; i < n; i++) {
            String id = factions.get(i).getId();
            index.put(id, i);
            alliance[i] = AllianceManager.getFactionAlliance(id);
            colonies[i] = stats.get(id)[0];
            size[i] = stats.get(id)[1];
            pirate[i] = NexUtilsFaction.isPirateFaction(id);
            weariness[i] = readWeariness(id);
        }
        for (int i = 0; i < n; i++) {
            for (int j = i; j < n; j++) {
                Rel r = classify(i, j);
                rel[i][j] = r;
                rel[j][i] = r;
                if (r == Rel.WAR) {
                    warPairs.add(new int[]{i, j});
                    wars[i]++;
                    wars[j]++;
                }
            }
        }
    }

    /**
     * The current state. While the game clock stands still (as it does in the intel screen) it is reused.
     * It is only held weakly, so it can't keep a previous game in memory while another one loads.
     */
    public static SectorSnapshot get() {
        long now = Global.getSector().getClock().getTimestamp();
        SectorSnapshot s = cached.get();
        if (s == null || s.timestamp != now) {
            s = take(now);
            cached = new WeakReference<>(s);
        }
        return s;
    }

    /** Forgets the reused snapshot, e.g. when a save is loaded or the intel screen is opened. */
    public static void invalidate() {
        cached = new WeakReference<>(null);
    }

    private static SectorSnapshot take(long now) {
        SectorAPI sector = Global.getSector();
        final Map<String, int[]> stats = new HashMap<>();
        for (MarketAPI m : sector.getEconomy().getMarketsCopy()) {
            if (m.isHidden() || m.isPlanetConditionMarketOnly()) continue;
            int[] st = stats.computeIfAbsent(m.getFactionId(), k -> new int[2]);
            st[0]++;
            st[1] += m.getSize();
        }
        stats.remove(Factions.NEUTRAL);
        Comparator<FactionAPI> bySize = (a, b) -> {
            int c = Integer.compare(stats.get(b.getId())[1], stats.get(a.getId())[1]);
            return c != 0 ? c : Fmt.name(a).compareToIgnoreCase(Fmt.name(b));
        };

        // Alliance blocks, largest total size first.
        final Map<Alliance, List<FactionAPI>> blocks = new HashMap<>();
        final Map<Alliance, Integer> blockSize = new HashMap<>();
        Set<String> placed = new HashSet<>();
        for (Alliance al : AllianceManager.getAllianceList()) {
            List<FactionAPI> block = new ArrayList<>();
            int total = 0;
            for (String id : al.getMembersCopy()) {
                FactionAPI f = sector.getFaction(id);
                if (f == null || !stats.containsKey(id) || !placed.add(id)) continue;
                block.add(f);
                total += stats.get(id)[1];
            }
            if (block.isEmpty()) continue;
            block.sort(bySize);
            blocks.put(al, block);
            blockSize.put(al, total);
        }
        List<Alliance> alliances = new ArrayList<>(blocks.keySet());
        alliances.sort((a, b) -> {
            int c = Integer.compare(blockSize.get(b), blockSize.get(a));
            return c != 0 ? c : a.getName().compareToIgnoreCase(b.getName());
        });
        List<FactionAPI> ordered = new ArrayList<>();
        for (Alliance al : alliances) ordered.addAll(blocks.get(al));

        List<FactionAPI> others = new ArrayList<>();
        List<FactionAPI> pirates = new ArrayList<>();
        for (String id : stats.keySet()) {
            if (placed.contains(id)) continue;
            FactionAPI f = sector.getFaction(id);
            if (f == null) continue;
            (NexUtilsFaction.isPirateFaction(id) ? pirates : others).add(f);
        }
        others.sort(bySize);
        pirates.sort(bySize);
        ordered.addAll(others);
        ordered.addAll(pirates);
        return new SectorSnapshot(now, ordered, alliances, stats);
    }

    private Rel classify(int i, int j) {
        if (i == j) return Rel.SELF;
        FactionAPI a = factions.get(i);
        FactionAPI b = factions.get(j);
        if (a.isHostileTo(b)) return Rel.WAR;
        if (alliance[i] != null && alliance[i] == alliance[j]) return Rel.ALLIANCE;
        switch (a.getRelationshipLevel(b)) {
            case VENGEFUL:
            case HOSTILE:
                return Rel.WAR;
            case INHOSPITABLE:
            case SUSPICIOUS:
                return Rel.HOSTILE;
            case NEUTRAL:
                return Rel.NEUTRAL;
            default:
                return Rel.FRIENDLY;
        }
    }

    /** Only for factions whose war weariness Nexerelin keeps: alive, not pirates, not excluded from diplomacy. */
    private static float readWeariness(String id) {
        if (!SectorManager.isFactionAlive(id) || NexUtilsFaction.isPirateFaction(id)
                || DiplomacyManager.disallowedFactions.contains(id)) {
            return Float.NaN;
        }
        return DiplomacyManager.getWarWeariness(id, true);
    }

    public int size() {
        return factions.size();
    }

    public FactionAPI get(int i) {
        return factions.get(i);
    }

    /** Matrix index of a faction, or -1 if it has no colonies. */
    public int indexOf(String factionId) {
        Integer i = index.get(factionId);
        return i == null ? -1 : i;
    }

    public float relation(int i, int j) {
        return factions.get(i).getRelationship(factions.get(j).getId());
    }

    public boolean isPirateWar(int[] pair) {
        return pirate[pair[0]] || pirate[pair[1]];
    }

    public int pirateWarCount() {
        int c = 0;
        for (int[] p : warPairs) {
            if (isPirateWar(p)) c++;
        }
        return c;
    }

    /** Factions sharing i's alliance, without i itself. */
    public List<Integer> allies(int i) {
        List<Integer> out = new ArrayList<>();
        for (int j = 0; j < factions.size(); j++) {
            if (j != i && rel[i][j] == Rel.ALLIANCE) out.add(j);
        }
        return out;
    }

    /** Factions i is at war with. */
    public List<Integer> enemies(int i) {
        List<Integer> out = new ArrayList<>();
        for (int j = 0; j < factions.size(); j++) {
            if (rel[i][j] == Rel.WAR) out.add(j);
        }
        return out;
    }

    /** Invasion points, Nexerelin's resource pools and the fleets in space; computed on first use. */
    public Strength[] strength() {
        if (strength == null) strength = readStrength();
        return strength;
    }

    private Strength[] readStrength() {
        int n = factions.size();
        Strength[] out = new Strength[n];
        InvasionFleetManager ifm = InvasionFleetManager.getManager();
        // The map getter, unlike getSpawnCounter(id), doesn't insert entries for unknown factions.
        Map<String, Float> points = ifm == null ? null : ifm.getSpawnCounter();
        FleetPoolManager fleetPool = FleetPoolManager.getManager();
        GroundPoolManager groundPool = GroundPoolManager.getManager();
        for (int i = 0; i < n; i++) {
            FactionAPI f = factions.get(i);
            String id = f.getId();
            Strength st = new Strength();
            Float p = points == null ? null : points.get(id);
            if (p != null) {
                st.invasionPoints = p;
                st.invasionPointsMax = InvasionFleetManager.getMaxInvasionPoints(f);
            }
            // The pools tick for every live faction, so reading them there creates nothing new.
            if (SectorManager.isFactionAlive(id)) {
                if (fleetPool != null && fleetPool.isEnabled()) {
                    st.fleetPool = fleetPool.getCurrentPool(id);
                    st.fleetPoolMax = fleetPool.getMaxPool(id);
                }
                if (groundPool != null && groundPool.isEnabled()) {
                    st.troops = groundPool.getCurrentPool(id);
                    st.troopsMax = groundPool.getMaxPool(id);
                }
            }
            out[i] = st;
        }
        for (LocationAPI loc : Global.getSector().getAllLocations()) {
            for (CampaignFleetAPI fleet : loc.getFleets()) {
                if (fleet.isStationMode() || fleet.getFaction() == null) continue;
                Integer i = index.get(fleet.getFaction().getId());
                if (i == null) continue;
                out[i].fleets++;
                out[i].fleetPoints += fleet.getFleetPoints();
            }
        }
        return out;
    }
}
