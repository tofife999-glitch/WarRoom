package warroom.data;

/**
 * War score points each side gained over time, in 24 equal buckets from the war's start. Buckets start 5
 * days wide; when the war outgrows them, neighbours merge and the width doubles. Points are stored in
 * tenths.
 */
public final class Momentum {
    public static final int BUCKETS = 24;
    private int bucketDays = 5;
    private final int[] tenths = new int[BUCKETS * 2];

    public int bucketDays() {
        return bucketDays;
    }

    /** Points side gained in bucket i. */
    public float points(int bucket, int side) {
        return tenths[bucket * 2 + side] / 10f;
    }

    /** Number of buckets that cover the first {@code days} days of the war. */
    public int used(float days) {
        return Math.max(1, Math.min(BUCKETS, (int) (days / bucketDays) + 1));
    }

    void add(float daysSinceStart, int side, float points) {
        if (points <= 0f || side < 0 || side > 1) return;
        int idx = (int) (Math.max(0f, daysSinceStart) / bucketDays);
        while (idx >= BUCKETS) {
            merge();
            idx = (int) (Math.max(0f, daysSinceStart) / bucketDays);
        }
        tenths[idx * 2 + side] += Math.round(points * 10f);
    }

    private void merge() {
        for (int i = 0; i < BUCKETS / 2; i++) {
            for (int s = 0; s < 2; s++) {
                tenths[i * 2 + s] = tenths[(2 * i) * 2 + s] + tenths[(2 * i + 1) * 2 + s];
            }
        }
        for (int i = BUCKETS / 2; i < BUCKETS; i++) {
            tenths[i * 2] = 0;
            tenths[i * 2 + 1] = 0;
        }
        bucketDays *= 2;
    }

    /** Points side gained in the last {@code window} days, counting partly covered buckets in proportion. */
    public float recent(float daysNow, float window, int side) {
        float from = daysNow - window;
        float sum = 0f;
        for (int i = 0; i < BUCKETS; i++) {
            float b0 = i * bucketDays;
            float b1 = b0 + bucketDays;
            float overlap = Math.min(b1, daysNow) - Math.max(b0, from);
            if (overlap <= 0f) continue;
            sum += points(i, side) * Math.min(1f, overlap / bucketDays);
        }
        return sum;
    }

    String encode() {
        return bucketDays + " " + Codec.ints(tenths);
    }

    static Momentum decode(String s) {
        Momentum m = new Momentum();
        int[] v = Codec.ints(s);
        if (v.length == 0) return m;
        m.bucketDays = Math.max(1, v[0]);
        System.arraycopy(v, 1, m.tenths, 0, Math.min(v.length - 1, m.tenths.length));
        return m;
    }
}
