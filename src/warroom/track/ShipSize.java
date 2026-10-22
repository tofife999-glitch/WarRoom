package warroom.track;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.FactionDoctrineAPI;
import com.fs.starfarer.api.campaign.LocationAPI;
import com.fs.starfarer.api.impl.campaign.ids.MemFlags;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import warroom.data.WarData;

import java.util.HashMap;
import java.util.Map;

/**
 * A faction's typical fleet points per ship, for turning estimated fleet points lost off-screen into an
 * estimated number of ships. Measured at most once a day from the faction's combat fleets in space,
 * smoothed, and kept for this session only; falls back to the faction's doctrine.
 */
final class ShipSize {
    static final float FALLBACK = 10f;
    private static final int MIN_SHIPS = 12;
    private static final int MIN_FLEETS = 2;
    /** By doctrine ship size 1 to 5. */
    private static final float[] DOCTRINE = {8f, 9f, 10f, 12f, 14f};

    private static final Map<String, Float> measured = new HashMap<>();
    private static long scannedDay = Long.MIN_VALUE;

    private ShipSize() {
    }

    static void reset() {
        measured.clear();
        scannedDay = Long.MIN_VALUE;
    }

    static float of(String factionId) {
        if (factionId == null) return FALLBACK;
        long day = Global.getSector().getClock().getTimestamp() / WarData.DAY;
        if (day != scannedDay) {
            scannedDay = day;
            scan();
        }
        Float v = measured.get(factionId);
        return v != null ? v : doctrine(factionId);
    }

    private static void scan() {
        Map<String, float[]> sums = new HashMap<>(); // fleet points, ships, fleets
        for (LocationAPI loc : Global.getSector().getAllLocations()) {
            for (CampaignFleetAPI f : loc.getFleets()) {
                if (f.isStationMode() || f.isPlayerFleet() || f.isDespawning() || f.getFaction() == null) continue;
                if (f.getFleetData() == null) continue;
                MemoryAPI mem = f.getMemoryWithoutUpdate();
                if (mem != null && (mem.getBoolean(MemFlags.MEMORY_KEY_TRADE_FLEET) || mem.getBoolean(MemFlags.MEMORY_KEY_SMUGGLER))) {
                    continue;
                }
                int ships = f.getFleetData().getNumMembers();
                if (ships <= 0) continue;
                float[] s = sums.computeIfAbsent(f.getFaction().getId(), k -> new float[3]);
                s[0] += f.getFleetPoints();
                s[1] += ships;
                s[2] += 1f;
            }
        }
        for (Map.Entry<String, float[]> e : sums.entrySet()) {
            float[] s = e.getValue();
            if (s[1] < MIN_SHIPS || s[2] < MIN_FLEETS) continue;
            float now = Math.max(3f, Math.min(40f, s[0] / s[1]));
            Float before = measured.get(e.getKey());
            measured.put(e.getKey(), before == null ? now : before * 0.7f + now * 0.3f);
        }
    }

    private static float doctrine(String factionId) {
        try {
            FactionAPI f = Global.getSector().getFaction(factionId);
            FactionDoctrineAPI d = f == null ? null : f.getDoctrine();
            if (d == null) return FALLBACK;
            int size = Math.max(1, Math.min(5, d.getShipSize()));
            return DOCTRINE[size - 1];
        } catch (RuntimeException ex) {
            return FALLBACK;
        }
    }
}
