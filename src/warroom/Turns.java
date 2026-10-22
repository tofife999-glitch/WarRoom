package warroom;

import warroom.data.StoryEvent;
import warroom.data.WarRecord;

/**
 * Turning points: a war's log notes when the advantage on the war score passes to a side, or when the two are
 * evenly matched again. Between the even band and the advantage line nothing changes, so a war hovering around
 * either line does not fill its log.
 */
public final class Turns {
    /** Within this distance of an even share, the sides count as evenly matched again. */
    public static final float EVEN_BAND = 0.03f;

    private Turns() {
    }

    /**
     * The standing a war's log should record next, given side 0's share of the war score and the standing
     * recorded last (-1 evenly matched, 0 or 1 the side holding the advantage): the same one if nothing changed
     * enough.
     */
    public static int next(float share, int last) {
        if (Float.isNaN(share)) return last;
        if (share >= Score.AHEAD) return 0;
        if (share <= 1f - Score.AHEAD) return 1;
        if (Math.abs(share - 0.5f) <= EVEN_BAND) return -1;
        return last;
    }

    /** Logs a turning point if the war's standing has changed; true if one was logged. */
    public static boolean note(WarRecord r, float share, long when) {
        int now = next(share, r.standing);
        if (now == r.standing) return false;
        r.standing = now;
        r.addEvent(new StoryEvent(when, StoryEvent.Kind.SHIFT, now, null));
        return true;
    }

    /** The war's standing now, from its score, recorded without a log entry (for a save from before 0.8). */
    public static void settle(WarRecord r, float share) {
        r.standing = next(share, WarRecord.STANDING_EVEN);
    }
}
