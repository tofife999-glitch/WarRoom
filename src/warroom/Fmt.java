package warroom;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignClockAPI;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.util.Misc;

import java.util.Locale;

/** Number, date and name formatting shared by all panels. */
public final class Fmt {
    /** Shown where Nexerelin doesn't track a value for a faction. */
    public static final String NONE = "-";

    private Fmt() {
    }

    /** Display name; your own faction is "Your faction" unless you have named it. */
    public static String name(FactionAPI f) {
        if (f.isPlayerFaction()) {
            String o = f.getDisplayNameOverride();
            return o == null || o.trim().isEmpty() ? "Your faction" : o;
        }
        return Misc.ucFirst(f.getDisplayName());
    }

    /**
     * The name as it reads inside a sentence, with the game's article: "the Hegemony", "Tri-Tachyon";
     * your own faction is "your faction" unless you have named it. {@code cap} for the start of a sentence.
     */
    public static String theName(FactionAPI f, boolean cap) {
        String n;
        if (f.isPlayerFaction()) {
            String o = f.getDisplayNameOverride();
            n = o == null || o.trim().isEmpty() ? "your faction" : o;
        } else {
            n = f.getDisplayNameWithArticle();
            if (n == null || n.trim().isEmpty()) n = f.getDisplayName();
        }
        return cap ? Misc.ucFirst(n) : n;
    }

    /** Whether the faction's name takes a plural verb ("the pirates are"). */
    public static boolean plural(FactionAPI f) {
        return f != null && !f.isPlayerFaction() && "are".equalsIgnoreCase(f.getDisplayNameIsOrAre());
    }

    /** Whole number with thousands separators. */
    public static String num(float v) {
        return Misc.getWithDGS(Math.round(v));
    }

    /** Whole number, or {@link #NONE} for NaN. Big numbers are shortened: 9,999 / 12.3k / 1.2M. */
    public static String compact(float v) {
        if (Float.isNaN(v)) return NONE;
        float a = Math.abs(v);
        if (a < 10000f) return num(v);
        if (a < 1000000f) return String.format(Locale.ROOT, "%.1fk", v / 1000f);
        return String.format(Locale.ROOT, "%.1fM", v / 1000000f);
    }

    /** "a / b", or {@link #NONE} when the first value is NaN. */
    public static String ofMax(float cur, float max) {
        if (Float.isNaN(cur)) return NONE;
        if (Float.isNaN(max)) return compact(cur);
        return compact(cur) + " / " + compact(max);
    }

    public static String pair(String a, String b) {
        return a + " / " + b;
    }

    /** Relationship from -100 to 100, with a sign. */
    public static String rel(float r) {
        int v = Math.round(r * 100f);
        return v > 0 ? "+" + v : String.valueOf(v);
    }

    /** Day, month and cycle, e.g. "14 Mar c206". */
    public static String date(long timestamp) {
        CampaignClockAPI c = Global.getSector().getClock().createClock(timestamp);
        return c.getDay() + " " + c.getShortMonthString() + " c" + c.getCycle();
    }

    /** Day and month only, e.g. "14 Mar", for lines where the cycle is obvious. */
    public static String shortDate(long timestamp) {
        CampaignClockAPI c = Global.getSector().getClock().createClock(timestamp);
        return c.getDay() + " " + c.getShortMonthString();
    }

    public static int days(long from, long to) {
        return Math.max(0, Math.round((to - from) / (float) (24L * 60L * 60L * 1000L)));
    }

    public static String plural(int n, String one, String many) {
        return n + " " + (n == 1 ? one : many);
    }

    /** 1st, 2nd, 3rd, 4th ... 11th, 12th, 13th ... 21st. */
    public static String ordinal(int n) {
        int h = n % 100;
        int t = n % 10;
        return n + (h >= 11 && h <= 13 ? "th" : t == 1 ? "st" : t == 2 ? "nd" : t == 3 ? "rd" : "th");
    }
}
