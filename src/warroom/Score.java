package warroom;

import warroom.data.WarData;
import warroom.data.WarRecord;

/**
 * The war score behind the tug bar, worked out from a war's counters whenever it is shown (so changed
 * weights apply to every war at once), and the verdict and momentum lines built from it.
 */
public final class Score {
    /** Below this share of the points the war counts as even; at or above FAR the leader is far ahead. */
    public static final float AHEAD = 0.55f;
    public static final float FAR = 0.75f;
    /**
     * Momentum: the share of the recent points for "X gaining", and the least points per 30 days for anything
     * but "quiet". How far back it looks is a setting ({@link #momentumDays()}).
     */
    public static final float GAINING_SHARE = 0.6f;
    public static final float GAINING_MIN = 10f;

    public enum Verdict { NOTHING, EVEN, AHEAD, FAR_AHEAD }

    private Score() {
    }

    /** Colony size taken or bombarded, scaled by the enemy's total colony size when the war began. */
    public static float territory(WarRecord r, int side) {
        int taken = r.get(WarRecord.CAPTURED_SIZE, side) + r.get(WarRecord.BOMBARDED_SIZE, side);
        return taken * colonyWeight(r, side);
    }

    /** Points per colony size taken from the other side of this war. */
    public static float colonyWeight(WarRecord r, int side) {
        float ref = Settings.scoreSizeReference();
        int enemy = r.startSize[1 - side];
        float enemySize = Math.max(5f, enemy > 0 ? enemy : ref);
        return Settings.scoreColonySize() * ref / enemySize;
    }

    /** The share of the enemy's worlds (by size at war start) this side has taken, 0 to 1. */
    public static float territoryShare(WarRecord r, int side) {
        int enemy = r.startSize[1 - side];
        if (enemy <= 0) return 0f;
        int taken = r.get(WarRecord.CAPTURED_SIZE, side) + r.get(WarRecord.BOMBARDED_SIZE, side);
        return Math.min(1f, taken / (float) enemy);
    }

    /** Fleet points the other side lost, counted and estimated. */
    public static float fleets(WarRecord r, int side) {
        return r.fpLost(1 - side) / Settings.scoreFpPerPoint();
    }

    public static float raids(WarRecord r, int side) {
        return r.get(WarRecord.RAIDS_WON, side) * Settings.scoreRaidWon()
                + r.get(WarRecord.REPELLED, side) * Settings.scoreInvasionRepelled();
    }

    public static float engagements(WarRecord r, int side) {
        return r.get(WarRecord.ENGAGEMENTS, side) * Settings.scoreEngagementWon();
    }

    public static float total(WarRecord r, int side) {
        if (r == null) return 0f;
        return territory(r, side) + fleets(r, side) + raids(r, side) + engagements(r, side);
    }

    /**
     * Side 0's share of the points with the head start added, 0 to 1; NaN while nothing has scored. Works for
     * single wars and summed alliance wars alike.
     */
    public static float share(float p0, float p1) {
        if (p0 + p1 <= 0f) return Float.NaN;
        float hs = Settings.scoreHeadStart();
        return (p0 + hs) / (p0 + p1 + 2f * hs);
    }

    public static Verdict verdict(float share) {
        if (Float.isNaN(share)) return Verdict.NOTHING;
        float lead = Math.max(share, 1f - share);
        if (lead >= FAR) return Verdict.FAR_AHEAD;
        if (lead >= AHEAD) return Verdict.AHEAD;
        return Verdict.EVEN;
    }

    /** Who gained lately: side 0 or 1, -1 for an even exchange, -2 for quiet. */
    public static int gaining(WarRecord r, long now) {
        if (r == null) return -2;
        float days = days(r.start, r.ended() ? r.end : now);
        float window = momentumDays();
        float p0 = r.momentum.recent(days, window, 0);
        float p1 = r.momentum.recent(days, window, 1);
        return gaining(p0, p1);
    }

    public static int gaining(float p0, float p1) {
        float sum = p0 + p1;
        if (sum < quietBelow()) return -2;
        if (p0 / sum >= GAINING_SHARE) return 0;
        if (p1 / sum >= GAINING_SHARE) return 1;
        return -1;
    }

    /** The days the momentum line looks back (warRoom_recentDays, 90 unless set). */
    public static float momentumDays() {
        return Settings.recentDays();
    }

    /** The least points within that window for anything but "quiet": 10 per 30 days. */
    public static float quietBelow() {
        return GAINING_MIN * momentumDays() / 30f;
    }

    public static float days(long from, long to) {
        return Math.max(0f, (to - from) / (float) WarData.DAY);
    }
}
