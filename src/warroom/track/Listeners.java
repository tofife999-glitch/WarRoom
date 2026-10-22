package warroom.track;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.BaseCampaignEventListener;
import com.fs.starfarer.api.campaign.BattleAPI;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.listeners.ListenerManagerAPI;
import com.fs.starfarer.api.impl.campaign.intel.raid.RaidIntel;
import com.fs.starfarer.api.impl.campaign.rulecmd.salvage.Nex_MarketCMD.TempDataInvasion;
import com.fs.starfarer.api.util.IntervalUtil;
import exerelin.campaign.InvasionRound.InvasionRoundResult;
import exerelin.campaign.alliances.Alliance;
import exerelin.campaign.alliances.AllianceEventListener;
import exerelin.campaign.battle.NexWarSimScript;
import exerelin.campaign.battle.NexWarSimScriptListener;
import exerelin.campaign.intel.AllianceVoteIntel;
import exerelin.campaign.intel.fleets.RaidListener;
import exerelin.campaign.intel.groundbattle.GroundBattleCampaignListener;
import exerelin.campaign.intel.groundbattle.GroundBattleIntel;
import exerelin.utilities.InvasionListener;
import org.apache.log4j.Logger;
import warroom.Demo;
import warroom.data.WarData;

import java.util.List;

/**
 * The listeners and the daily check, all transient: registered again on every load and never saved.
 * A failure while recording is logged and never reaches the game.
 */
public final class Listeners {
    private static final Logger log = Global.getLogger(Listeners.class);
    /** Set when an alliance vote may have started or ended wars; the next frame checks again. */
    private static boolean rescan;

    private Listeners() {
    }

    public static void register() {
        rescan = false;
        Peace.reset();
        ShipSize.reset();
        WarTracker.resetBattles();
        SectorAPI sector = Global.getSector();
        ListenerManagerAPI lm = sector.getListenerManager();
        sector.addTransientListener(new Battles());
        lm.addListener(new NexEvents(), true);
        Bombardments b = new Bombardments();
        lm.addListener(b, true);
        sector.addTransientScript(b);
        try {
            // Its own class: older Nexerelin versions don't have this interface.
            lm.addListener(new SimBattles(), true);
        } catch (Throwable t) {
            log.warn("War Room: Nexerelin's off-screen fights can't be followed with this Nexerelin version", t);
        }
        sector.addTransientScript(new DailyCheck());
    }

    /** Runs a recording step; nothing is recorded while the demo is shown. */
    static void guard(Runnable r) {
        if (Demo.isOn()) return;
        try {
            r.run();
        } catch (Throwable t) {
            log.error("War Room: could not record an event", t);
        }
    }

    /**
     * Battles anywhere in the sector. Losses come once per autoresolve round (or once for a battle of yours),
     * the win once at the end.
     */
    private static final class Battles extends BaseCampaignEventListener {
        Battles() {
            super(false);
        }

        @Override
        public void reportBattleOccurred(CampaignFleetAPI primaryWinner, BattleAPI battle) {
            guard(() -> WarTracker.battleRound(battle));
        }

        @Override
        public void reportBattleFinished(CampaignFleetAPI primaryWinner, BattleAPI battle) {
            guard(() -> WarTracker.battleFinished(primaryWinner, battle));
        }
    }

    /** Fights Nexerelin settles off-screen. */
    private static final class SimBattles implements NexWarSimScriptListener {
        @Override
        public void reportRoundResolved(NexWarSimScript sim, int roundIndex) {
            guard(() -> WarTracker.simRound(sim));
        }

        @Override
        public void reportBattleResolved(NexWarSimScript sim, Boolean result) {
            guard(() -> WarTracker.offscreen(sim, result));
        }
    }

    /** Nexerelin's captures, raids and invasions, ground battles and alliances. */
    private static final class NexEvents implements InvasionListener, RaidListener, GroundBattleCampaignListener,
            AllianceEventListener {
        @Override
        public void reportMarketTransfered(MarketAPI market, FactionAPI newOwner, FactionAPI oldOwner,
                                           boolean playerInvolved, boolean isCapture, List<String> factionsToNotify,
                                           float repChangeStrength) {
            if (isCapture) guard(() -> WarTracker.capture(market, newOwner, oldOwner));
        }

        @Override
        public void reportRaidEnded(RaidIntel intel, FactionAPI attacker, FactionAPI defender, MarketAPI target,
                                    boolean success) {
            guard(() -> WarTracker.offensiveEnded(intel, attacker, defender, target));
        }

        @Override
        public void reportBattleEnded(GroundBattleIntel battle) {
            guard(() -> WarTracker.groundBattleEnded(battle));
        }

        /** Legacy invasions only; NPC ones are counted from reportRaidEnded, so only your own failures here. */
        @Override
        public void reportInvasionFinished(CampaignFleetAPI fleet, FactionAPI attackerFaction, MarketAPI market,
                                           float numRounds, boolean success) {
            if (success || fleet == null || !fleet.isPlayerFleet() || market == null || attackerFaction == null) return;
            guard(() -> WarTracker.repelled(attackerFaction, market.getFaction(), market));
        }

        @Override
        public void reportFactionJoinedAlliance(Alliance alliance, FactionAPI faction) {
            guard(() -> WarTracker.allianceJoined(alliance, faction));
        }

        @Override
        public void reportFactionLeftAlliance(Alliance alliance, FactionAPI faction) {
            guard(() -> WarTracker.allianceLeft(alliance, faction));
        }

        @Override
        public void reportAlliancesMerged(Alliance into, Alliance other) {
            if (Demo.isOn()) {
                // Recording pauses while the demo is shown, but this one can't be pieced together later.
                try {
                    WarTracker.noteMerge(Demo.realRecords(), into, other);
                } catch (Throwable t) {
                    log.error("War Room: could not record an alliance merge", t);
                }
                return;
            }
            guard(() -> WarTracker.alliancesMerged(into, other));
        }

        @Override
        public void reportAllianceDissolved(Alliance alliance) {
            guard(() -> WarTracker.allianceDissolved(alliance));
        }

        /** A vote can start or end wars for a whole alliance; check now and again once it has settled. */
        @Override
        public void reportAllianceVote(Alliance alliance, AllianceVoteIntel vote) {
            if (WarData.get().since == 0) return;
            guard(WarTracker::check);
            rescan = true;
        }

        // Never fired by Nexerelin; new alliances are noticed by the daily check.
        @Override
        public void reportAllianceFormed(Alliance alliance, FactionAPI faction1, FactionAPI faction2) {
        }

        // Not needed: legacy invasion loot and rounds, ground battle turns.

        @Override
        public void reportInvadeLoot(InteractionDialogAPI dialog, MarketAPI market, TempDataInvasion actionData,
                                     CargoAPI cargo) {
        }

        @Override
        public void reportInvasionRound(InvasionRoundResult result, CampaignFleetAPI fleet, MarketAPI defender,
                                        float atkStr, float defStr) {
        }

        @Override
        public void reportBattleStarted(GroundBattleIntel battle) {
        }

        @Override
        public void reportPlayerJoinedBattle(GroundBattleIntel battle) {
        }

        @Override
        public void reportBattleBeforeTurn(GroundBattleIntel battle, int turn) {
        }

        @Override
        public void reportBattleAfterTurn(GroundBattleIntel battle, int turn) {
        }
    }

    /** Wars and peace have no event: once a day every pair of factions with colonies is compared. */
    private static final class DailyCheck implements EveryFrameScript {
        private final IntervalUtil interval = new IntervalUtil(1f, 1f);

        @Override
        public boolean isDone() {
            return false;
        }

        @Override
        public boolean runWhilePaused() {
            return false;
        }

        @Override
        public void advance(float amount) {
            if (rescan) {
                rescan = false;
                guard(WarTracker::check);
            }
            interval.advance(Global.getSector().getClock().convertToDays(amount));
            if (interval.intervalElapsed()) guard(WarTracker::check);
        }
    }
}
