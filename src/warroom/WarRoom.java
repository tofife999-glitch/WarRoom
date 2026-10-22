package warroom;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.IntelDataAPI;
import com.fs.starfarer.api.campaign.comm.IntelInfoPlugin;
import com.fs.starfarer.api.campaign.comm.IntelManagerAPI;
import org.apache.log4j.Logger;
import warroom.track.Listeners;
import warroom.track.WarTracker;
import warroom.ui.FactionScreen;
import warroom.ui.Pages;

import java.util.ArrayList;

/**
 * Keeps War Room out of the save. The intel entry is created on every load; just before every save it is
 * taken out of the intel manager and out of the Intel screen's saved selection, and put back afterwards.
 * The listeners and the daily check are transient. What must survive saving is kept as plain values in
 * {@link SaveData}.
 */
public final class WarRoom {
    public static final String MOD_ID = "war_room";
    static final Logger log = Global.getLogger(WarRoom.class);

    private static WarRoomIntel intel;
    /** Set when War Room is added back to a save it was taken out of; the first check then only takes stock. */
    static boolean resumed;

    private WarRoom() {
    }

    /** The War Room intel entry of the game loaded, or null. */
    public static WarRoomIntel intel() {
        return intel;
    }

    static void onGameLoad(boolean newGame) {
        Demo.reset();
        SectorSnapshot.invalidate();
        Pages.reset();
        SaveData.load();
        IntelManagerAPI im = Global.getSector().getIntelManager();
        // None should exist; this only guards against a stray one.
        for (IntelInfoPlugin old : new ArrayList<>(im.getIntel(WarRoomIntel.class))) im.removeIntel(old);
        intel = new WarRoomIntel();
        addIntel(SaveData.selected);
        Listeners.register();
        Global.getSector().addTransientScript(new FactionScreen());
        boolean back = resumed;
        resumed = false;
        try {
            // Takes stock right away; in a new game the wars found now began with the game.
            WarTracker.check(newGame, back);
        } catch (Throwable t) {
            log.error("War Room: first war check failed; the daily check will try again", t);
        }
    }

    static void beforeGameSave() {
        try {
            if (intel != null) {
                IntelDataAPI ui = Global.getSector().getIntel();
                SaveData.selected = ui.getSelectedIntel() == intel;
                if (SaveData.selected) ui.setSelectedIntel(null);
                Global.getSector().getIntelManager().removeIntel(intel);
            }
        } finally {
            SaveData.store();
        }
    }

    static void afterGameSave() {
        addIntel(SaveData.selected);
    }

    private static void addIntel(boolean select) {
        if (intel == null) return;
        IntelManagerAPI im = Global.getSector().getIntelManager();
        if (!im.hasIntel(intel)) {
            intel.restoreImportant(SaveData.important);
            im.addIntel(intel, true);
            intel.setNew(false);
        }
        if (select) Global.getSector().getIntel().setSelectedIntel(intel);
    }
}
