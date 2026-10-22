package warroom;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.util.Misc;
import exerelin.campaign.alliances.Alliance;
import warroom.data.WarData;
import warroom.demo.DemoHistory;
import warroom.track.Alliances;
import warroom.track.WarTracker;
import warroom.ui.Pages;

import java.util.Random;

/**
 * Demo mode: War Room shows a made-up history built from the sector's real factions instead of its own
 * records, to try out the pages without months of play. Meanwhile the real records are set aside (they are
 * what a save gets) and tracking pauses. Nothing of the demo is ever saved; loading a save ends it.
 */
public final class Demo {
    /** The real records while the demo is shown; null when it is off. */
    private static WarData real;

    private Demo() {
    }

    public static boolean isOn() {
        return real != null;
    }

    /** Shows a new made-up history (also when one is already shown); {@code big} for a long, busy one. */
    public static WarData start(long seed, boolean big) {
        SectorSnapshot.invalidate();
        SectorSnapshot s = SectorSnapshot.get();
        WarData demo = DemoHistory.build(inputs(s), Global.getSector().getClock().getTimestamp(), new Random(seed), big);
        if (real == null) real = WarData.get();
        WarData.use(demo);
        Pages.reset();
        return demo;
    }

    /** Brings the real records back; false if the demo was not on. */
    public static boolean stop() {
        if (real == null) return false;
        WarData.use(real);
        real = null;
        try {
            // Takes stock right away, so the pause counts as a gap even if the game is saved at once.
            WarTracker.check();
        } catch (Throwable t) {
            WarRoom.log.error("War Room: check after the demo failed; the daily check will try again", t);
        }
        SectorSnapshot.invalidate();
        Pages.reset();
        return true;
    }

    /** The real records while the demo is shown; null when it is off. */
    public static WarData realRecords() {
        return real;
    }

    /** What a save should hold: the real records, also while the demo is shown. */
    static WarData toSave() {
        return real != null ? real : WarData.get();
    }

    /** Forgets the demo; called on every load, before the save's records are read. */
    static void reset() {
        real = null;
    }

    private static DemoHistory.Inputs inputs(SectorSnapshot s) {
        DemoHistory.Inputs in = new DemoHistory.Inputs();
        for (int i = 0; i < s.size(); i++) {
            FactionAPI f = s.get(i);
            in.factions.add(new DemoHistory.Inputs.Faction(f.getId(), Fmt.name(f), s.size[i], s.pirate[i]));
        }
        for (MarketAPI m : Global.getSector().getEconomy().getMarketsCopy()) {
            if (m.isHidden() || m.getFactionId() == null || s.indexOf(m.getFactionId()) < 0) continue;
            String system = m.getStarSystem() == null ? null : m.getStarSystem().getBaseName();
            in.colonies.add(new DemoHistory.Inputs.Colony(m.getName(), m.getFactionId(), m.getSize(), system));
        }
        for (Alliance a : s.alliances) {
            if (a == null || a.uuId == null) continue;
            in.alliances.add(new DemoHistory.Inputs.Alliance(a.uuId, a.getName(), Alliances.members(a.uuId)));
        }
        String employer = Misc.getCommissionFactionId();
        if (employer != null) in.friends.add(employer);
        Alliance mine = Alliances.of("player");
        if (mine != null) in.friends.addAll(Alliances.members(mine.uuId));
        return in;
    }
}
