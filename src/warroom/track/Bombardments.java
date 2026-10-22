package warroom.track;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.listeners.ColonyDecivListener;
import com.fs.starfarer.api.campaign.listeners.ColonyPlayerHostileActListener;
import com.fs.starfarer.api.campaign.listeners.ColonySizeChangeListener;
import com.fs.starfarer.api.campaign.listeners.GroundRaidObjectivesListener;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.impl.campaign.graid.GroundRaidObjectivePlugin;
import com.fs.starfarer.api.impl.campaign.ids.MemFlags;
import com.fs.starfarer.api.impl.campaign.rulecmd.salvage.MarketCMD.RaidType;
import com.fs.starfarer.api.impl.campaign.rulecmd.salvage.MarketCMD.TempData;
import com.fs.starfarer.api.impl.campaign.rulecmd.salvage.Nex_MarketCMD.NexTempData;
import exerelin.campaign.PlayerFactionStore;
import exerelin.utilities.ColonyNPCHostileActListener;
import warroom.data.StoryEvent;

import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * Notices colonies destroyed or shrunk by saturation bombardment and credits the bombing faction. The
 * colony's owner and size are noted the moment it is about to be destroyed or shrinks; the bombardment
 * report (yours or a Nexerelin fleet's) then names who did it. Anything still unclaimed one frame later
 * is credited to the faction whose "recently bombarded" mark the game just set, if any. Your own raids (once
 * per raid, however many industries it disrupts) and tactical bombardments are counted as raids won. Read-only.
 */
final class Bombardments implements ColonyDecivListener, ColonySizeChangeListener, ColonyPlayerHostileActListener,
        ColonyNPCHostileActListener, GroundRaidObjectivesListener, EveryFrameScript {
    /** Colony -> {owner id, size before, destroyed}. */
    private final Map<MarketAPI, Object[]> pending = new IdentityHashMap<>();

    @Override
    public void reportColonyAboutToBeDecivilized(MarketAPI m, boolean fullyDestroyed) {
        Listeners.guard(() -> {
            if (m != null && fullyDestroyed) pending.put(m, new Object[]{m.getFactionId(), m.getSize(), Boolean.TRUE});
        });
    }

    @Override
    public void reportColonyDecivilized(MarketAPI m, boolean fullyDestroyed) {
    }

    @Override
    public void reportColonySizeChanged(MarketAPI m, int prevSize) {
        Listeners.guard(() -> {
            if (m == null || m.getSize() >= prevSize) return;
            if (m.isPlanetConditionMarketOnly() || !m.isInEconomy()) return;
            if (!pending.containsKey(m)) pending.put(m, new Object[]{m.getFactionId(), prevSize, Boolean.FALSE});
        });
    }

    @Override
    public void reportSaturationBombardmentFinished(InteractionDialogAPI dialog, MarketAPI m, TempData actionData) {
        Listeners.guard(() -> claim(m, PlayerFactionStore.getPlayerFactionId()));
    }

    @Override
    public void reportNPCSaturationBombardment(MarketAPI m, TempData actionData) {
        Listeners.guard(() -> {
            FactionAPI a = actionData instanceof NexTempData ? ((NexTempData) actionData).attackerFaction : null;
            if (a != null) claim(m, a.getId());
        });
    }

    private void claim(MarketAPI m, String attacker) {
        Object[] p = m == null ? null : pending.remove(m);
        if (p != null) record(m, attacker, p);
    }

    private static void record(MarketAPI m, String attacker, Object[] p) {
        boolean destroyed = (Boolean) p[2];
        int before = (Integer) p[1];
        int lost = destroyed ? before : before - m.getSize();
        WarTracker.bombarded(m, attacker, (String) p[0], lost, before);
    }

    @Override
    public void advance(float amount) {
        if (pending.isEmpty()) return;
        Listeners.guard(() -> {
            for (Map.Entry<MarketAPI, Object[]> e : pending.entrySet()) {
                String attacker = freshBombarder(e.getKey());
                if (attacker != null) record(e.getKey(), attacker, e.getValue());
            }
        });
        pending.clear();
    }

    /** The faction whose "recently bombarded" mark on this colony was set just now, or null. */
    private static String freshBombarder(MarketAPI m) {
        MemoryAPI mem = m.getMemoryWithoutUpdate();
        if (mem == null) return null;
        String prefix = MemFlags.RECENTLY_BOMBARDED + "_";
        String best = null;
        float bestExpire = 29.9f;
        for (String k : mem.getRequired(MemFlags.RECENTLY_BOMBARDED)) {
            if (k == null || !k.startsWith(prefix) || !mem.getBoolean(k)) continue;
            float x = mem.getExpire(k);
            if (x > bestExpire) {
                bestExpire = x;
                best = k.substring(prefix.length());
            }
        }
        return best;
    }

    @Override
    public boolean isDone() {
        return false;
    }

    @Override
    public boolean runWhilePaused() {
        return true;
    }

    /** Your raid on a colony, once per raid (for valuables or to disrupt). */
    @Override
    public void reportRaidObjectivesAchieved(RaidResultData data, InteractionDialogAPI dialog,
                                             Map<String, MemoryAPI> memoryMap) {
        Listeners.guard(() -> {
            if (data != null && data.market != null) WarTracker.playerRaid(data.market, StoryEvent.RAID_YOURS);
        });
    }

    @Override
    public void reportTacticalBombardmentFinished(InteractionDialogAPI dialog, MarketAPI market, TempData actionData) {
        Listeners.guard(() -> WarTracker.playerRaid(market, StoryEvent.RAID_TACTICAL));
    }

    // Not needed: raid objectives are not changed; your raids are counted once above (these come once per
    // industry disrupted); Nexerelin's own raids are counted when its offensive ends.

    @Override
    public void modifyRaidObjectives(MarketAPI market, SectorEntityToken entity, List<GroundRaidObjectivePlugin> objectives,
                                     RaidType type, int marineTokens, int priority) {
    }

    @Override
    public void reportRaidForValuablesFinishedBeforeCargoShown(InteractionDialogAPI dialog, MarketAPI market,
                                                               TempData actionData, CargoAPI cargo) {
    }

    @Override
    public void reportRaidToDisruptFinished(InteractionDialogAPI dialog, MarketAPI market, TempData actionData,
                                            Industry industry) {
    }

    @Override
    public void reportNPCGenericRaid(MarketAPI market, TempData actionData) {
    }

    @Override
    public void reportNPCIndustryRaid(MarketAPI market, TempData actionData, Industry industry) {
    }

    @Override
    public void reportNPCTacticalBombardment(MarketAPI market, TempData actionData) {
    }
}
