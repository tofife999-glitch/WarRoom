package warroom;

import com.fs.starfarer.api.BaseModPlugin;

/** Hooks War Room into loading and saving. A failure here is logged and never stops a load or a save. */
public class WarRoomModPlugin extends BaseModPlugin {
    /** Only called when War Room was not enabled for this save just before (new game, added, or added back). */
    @Override
    public void onEnabled(boolean wasEnabledBefore) {
        WarRoom.resumed = wasEnabledBefore;
    }

    @Override
    public void onGameLoad(boolean newGame) {
        try {
            WarRoom.onGameLoad(newGame);
        } catch (Throwable t) {
            WarRoom.log.error("War Room: could not set up after loading", t);
        }
    }

    @Override
    public void beforeGameSave() {
        try {
            WarRoom.beforeGameSave();
        } catch (Throwable t) {
            WarRoom.log.error("War Room: could not prepare for saving", t);
        }
    }

    @Override
    public void afterGameSave() {
        try {
            WarRoom.afterGameSave();
        } catch (Throwable t) {
            WarRoom.log.error("War Room: could not restore after saving", t);
        }
    }

    /** A failed save (e.g. out of memory) skips afterGameSave; put the intel entries back all the same. */
    @Override
    public void onGameSaveFailed() {
        try {
            WarRoom.afterGameSave();
        } catch (Throwable t) {
            WarRoom.log.error("War Room: could not restore after a failed save", t);
        }
    }
}
