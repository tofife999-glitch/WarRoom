package warroom;

import com.fs.starfarer.api.Global;
import warroom.data.WarData;
import warroom.track.WarTracker;

import java.util.HashMap;
import java.util.Map;

/**
 * Everything War Room keeps in a save, as plain values under one key in the sector's persistent data, so a
 * save never needs a War Room class to load. Written just before saving, read after loading.
 */
final class SaveData {
    static final String KEY = "war_room";

    /** Whether the player marked the War Room intel as important. */
    static boolean important;
    /** Whether War Room was the item selected in the Intel screen. */
    static boolean selected;

    private SaveData() {
    }

    static void load() {
        important = false;
        selected = false;
        Object o = Global.getSector().getPersistentData().get(KEY);
        Map<?, ?> m = o instanceof Map ? (Map<?, ?>) o : null;
        if (m != null) {
            important = Boolean.TRUE.equals(m.get("important"));
            selected = Boolean.TRUE.equals(m.get("selected"));
        }
        WarData.read(m, WarTracker.FACTIONS);
    }

    static void store() {
        HashMap<String, Object> m = new HashMap<>();
        m.put("v", WarData.FORMAT);
        if (important) m.put("important", Boolean.TRUE);
        if (selected) m.put("selected", Boolean.TRUE);
        // The real records, also while the demo is shown.
        WarData d = Demo.toSave();
        // Seen running until now: a later load that finds a long gap knows War Room was out of the save meanwhile.
        // Not while the demo is on: tracking is paused then, and that gap should show.
        if (!Demo.isOn() && d.since != 0) d.lastCheck = Global.getSector().getClock().getTimestamp();
        d.write(m);
        Global.getSector().getPersistentData().put(KEY, m);
    }
}
