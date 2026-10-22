package warroom;

import com.fs.starfarer.api.Global;
import org.json.JSONObject;

/** Tunables from data/config/settings.json (merged into the game's settings), with defaults. */
public final class Settings {
    private Settings() {
    }

    /** Fleet points destroyed in one battle, both sides together, for a "large battle" story event. */
    public static int largeBattleFP() {
        return getInt("warRoom_largeBattleFP", 200, 1, 100000);
    }

    /** War score per colony size taken or bombarded, against an enemy of {@link #scoreSizeReference()}. */
    public static float scoreColonySize() {
        return getFloat("warRoom_scoreColonySize", 10f);
    }

    /**
     * Total colony size of a "typical" enemy. Colony size taken counts more against smaller enemies and less
     * against bigger ones, in proportion.
     */
    public static float scoreSizeReference() {
        return Math.max(1f, getFloat("warRoom_scoreSizeReference", 40f));
    }

    /** Fleet points destroyed per war score point. */
    public static float scoreFpPerPoint() {
        return Math.max(1f, getFloat("warRoom_scoreFpPerPoint", 10f));
    }

    public static float scoreRaidWon() {
        return getFloat("warRoom_scoreRaidWon", 5f);
    }

    public static float scoreInvasionRepelled() {
        return getFloat("warRoom_scoreInvasionRepelled", 5f);
    }

    public static float scoreEngagementWon() {
        return getFloat("warRoom_scoreEngagementWon", 3f);
    }

    /** Invisible points each side starts with, so one skirmish can't paint the bar 100:0. */
    public static float scoreHeadStart() {
        return Math.max(0f, getFloat("warRoom_scoreHeadStart", 20f));
    }

    /** Days the "Past 90 days" readings look back: who is gaining ground on each war, and the header's summary. */
    public static int recentDays() {
        return getInt("warRoom_recentDays", 90, 10, 720);
    }

    /** A text setting, or {@code def} if it is missing. */
    public static String text(String key, String def) {
        try {
            JSONObject json = Global.getSettings().getSettingsJSON();
            return json == null ? def : json.optString(key, def);
        } catch (Exception ex) {
            return def;
        }
    }

    /**
     * A screen flourish ("warRoom_flourish" + name); on unless settings.json sets it to false, or switches all of
     * them off ("warRoom_flourishes": false).
     */
    public static boolean flourish(String name) {
        try {
            JSONObject json = Global.getSettings().getSettingsJSON();
            return json == null || json.optBoolean("warRoom_flourishes", true) && json.optBoolean("warRoom_flourish" + name, true);
        } catch (Exception ex) {
            return true;
        }
    }

    private static int getInt(String key, int def, int min, int max) {
        try {
            JSONObject json = Global.getSettings().getSettingsJSON();
            int v = json == null ? def : json.optInt(key, def);
            return Math.max(min, Math.min(max, v));
        } catch (Exception ex) {
            return def;
        }
    }

    private static float getFloat(String key, float def) {
        try {
            JSONObject json = Global.getSettings().getSettingsJSON();
            double v = json == null ? def : json.optDouble(key, def);
            return Double.isNaN(v) || Double.isInfinite(v) ? def : (float) v;
        } catch (Exception ex) {
            return def;
        }
    }
}
