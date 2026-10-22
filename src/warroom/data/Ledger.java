package warroom.data;

import warroom.Score;

import java.util.List;

/**
 * A faction's (or an alliance's) wars summed up: how many, how they ended, and the wars' counters on its own
 * side ("us") and its enemies' side ("them"). The save keeps one per faction and alliance for the wars the
 * Archive no longer holds, so service records and standings still count them. New slots go at the end.
 */
public final class Ledger {
    /** Wars, of them ended, and how: a ceasefire, a peace treaty, peace otherwise; the enemy or this side gone. */
    public static final int WARS = 0;
    public static final int ENDED = 1;
    public static final int CEASEFIRE = 2;
    public static final int TREATY = 3;
    public static final int CEASED = 4;
    public static final int ENEMY_GONE = 5;
    public static final int GONE = 6;
    /** Ended wars by who held the advantage on the war score at the end: this side, nobody, the enemy. */
    public static final int AHEAD = 7;
    public static final int EVEN = 8;
    public static final int BEHIND = 9;
    private static final int OUTCOMES = 10;
    private static final int STATS = WarRecord.SLOTS / 2;

    private final int[] outcome = new int[OUTCOMES];
    private final int[] us = new int[STATS];
    private final int[] them = new int[STATS];

    /** Adds one war, as seen from {@code side}. */
    public void add(WarRecord r, int side) {
        outcome[WARS]++;
        if (r.ended()) {
            outcome[ENDED]++;
            if (r.endReason == WarRecord.END_PEACE) {
                int how = r.peaceKind();
                outcome[how == WarRecord.PEACE_TREATY ? TREATY : how == WarRecord.PEACE_CEASEFIRE ? CEASEFIRE : CEASED]++;
            } else if (r.endReason == WarRecord.END_GONE_A || r.endReason == WarRecord.END_GONE_B) {
                boolean self = (r.endReason == WarRecord.END_GONE_A) == (side == 0);
                outcome[self ? GONE : ENEMY_GONE]++;
            }
            float share = Score.share(Score.total(r, side), Score.total(r, 1 - side));
            Score.Verdict v = Score.verdict(share);
            if (v == Score.Verdict.AHEAD || v == Score.Verdict.FAR_AHEAD) outcome[share >= 0.5f ? AHEAD : BEHIND]++;
            else outcome[EVEN]++;
        }
        for (int s = 0; s < STATS; s++) {
            us[s] += r.get(2 * s, side);
            them[s] += r.get(2 * s, 1 - side);
        }
    }

    public void add(Ledger o) {
        if (o == null) return;
        for (int i = 0; i < OUTCOMES; i++) outcome[i] += o.outcome[i];
        for (int s = 0; s < STATS; s++) {
            us[s] += o.us[s];
            them[s] += o.them[s];
        }
    }

    public int outcome(int k) {
        return outcome[k];
    }

    /** A war counter (WarRecord.WON, SHIPS, ...) summed on this side. */
    public int us(int stat) {
        return us[stat / 2];
    }

    /** The same counter on the enemies' side. */
    public int them(int stat) {
        return them[stat / 2];
    }

    /** Battles won, seen and reported. */
    public int fightsWon(boolean ours) {
        return ours ? us(WarRecord.WON) + us(WarRecord.ENGAGEMENTS) : them(WarRecord.WON) + them(WarRecord.ENGAGEMENTS);
    }

    /** Ships lost, the off-screen estimates included. */
    public int shipsLost(boolean ours) {
        return ours ? us(WarRecord.SHIPS) + Math.round(us(WarRecord.SHIPS_EST) / 10f)
                : them(WarRecord.SHIPS) + Math.round(them(WarRecord.SHIPS_EST) / 10f);
    }

    /** Whether those losses include an estimate worth marking, as for a single war. */
    public boolean lossesEstimated(boolean ours) {
        return ours ? us(WarRecord.SHIPS_EST) >= 5 || us(WarRecord.FP_EST) > 0
                : them(WarRecord.SHIPS_EST) >= 5 || them(WarRecord.FP_EST) > 0;
    }

    String encode() {
        return Codec.ints(outcome) + Codec.FIELD + Codec.ints(us) + Codec.FIELD + Codec.ints(them);
    }

    static Ledger decode(String s) {
        Ledger l = new Ledger();
        try {
            List<String> f = Codec.split(s, Codec.FIELD);
            int[] o = Codec.ints(f.size() > 0 ? f.get(0) : "");
            int[] u = Codec.ints(f.size() > 1 ? f.get(1) : "");
            int[] t = Codec.ints(f.size() > 2 ? f.get(2) : "");
            System.arraycopy(o, 0, l.outcome, 0, Math.min(o.length, OUTCOMES));
            System.arraycopy(u, 0, l.us, 0, Math.min(u.length, STATS));
            System.arraycopy(t, 0, l.them, 0, Math.min(t.length, STATS));
        } catch (RuntimeException ex) {
            return null;
        }
        return l;
    }
}
