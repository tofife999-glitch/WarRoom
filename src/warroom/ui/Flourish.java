package warroom.ui;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.LocationAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.impl.campaign.ids.Tags;
import com.fs.starfarer.api.ui.Alignment;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.LabelAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.ui.UIComponentAPI;
import com.fs.starfarer.api.util.Misc;
import warroom.Settings;

import java.awt.Color;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * The report's small screen touches. Each one has its own switch in data/config/settings.json
 * ("warRoom_flourish..."), and nothing else depends on them. Header: report number, addressee, relay light,
 * sync sweep, title settle, tension trace, relay name. Bottom of each tab: shipboard signature, closing rule.
 * Hostilities: intercept line (clickable), engaged light, gauge ticks, momentum arrow, new-war mark. War
 * reports: momentum arrow, theatre distances. Archive: now line, cycle bands, stamped months, struck names.
 * Standings: rank numbers, struck names, Sector total. Faction tooltips: rank. Everywhere: the game's
 * interface sounds on switching tabs and opening reports.
 */
final class Flourish {
    static final Color SERIAL = new Color(150, 172, 180);
    static final Color RELAY_OK = new Color(95, 194, 122);
    static final Color RELAY_LOST = new Color(230, 160, 70);
    static final Color ALARM = new Color(196, 87, 74);
    static final Color AMBER = new Color(230, 196, 106);
    /** The frame of a war in its first 30 days. */
    static final Color NEW_FRAME = new Color(230, 196, 106, 190);
    /** The line through a name struck from the record. */
    static final Color STRUCK = new Color(138, 74, 66);
    private static final Color RULE = new Color(27, 55, 65);

    private Flourish() {
    }

    /** Whether a touch is switched on (they all are unless settings.json turns one off). */
    static boolean on(String name) {
        return Settings.flourish(name);
    }

    /** The report number: the cycle and the day of the cycle, "206-306". */
    static String serial(long now) {
        GregorianCalendar c = new GregorianCalendar();
        c.setTimeInMillis(now);
        return c.get(Calendar.YEAR) + "-" + String.format("%03d", c.get(Calendar.DAY_OF_YEAR));
    }

    /** Who the report is for. */
    static String addressee() {
        PersonAPI p = Global.getSector().getPlayerPerson();
        String name = p == null ? null : p.getNameString();
        return name == null || name.trim().isEmpty() ? "Prepared for the fleet's captain" : "Prepared for Captain " + name.trim();
    }

    /**
     * The comm relay your fleet is linked through, by the game's own rule: a working relay in the system you are
     * in, or in hyperspace the nearest one ("Askonia relay"); null if none. Worked out at most every 2 seconds.
     */
    static String relayName() {
        long wall = System.currentTimeMillis();
        if (wall - relayAt < 2000L) return relayName;
        relayAt = wall;
        relayName = null;
        try {
            CampaignFleetAPI f = Global.getSector().getPlayerFleet();
            if (f == null) return null;
            if (!f.isInHyperspace()) {
                LocationAPI loc = f.getContainingLocation();
                if (loc instanceof StarSystemAPI && hasRelay(loc)) relayName = ((StarSystemAPI) loc).getBaseName() + " relay";
                return relayName;
            }
            float best = Float.MAX_VALUE;
            for (SectorEntityToken e : Global.getSector().getHyperspace().getEntitiesWithTag(Tags.COMM_RELAY)) {
                float d = Misc.getDistanceLY(f.getLocationInHyperspace(), e.getLocationInHyperspace());
                if (working(e) && d < best) {
                    best = d;
                    relayName = e.getName();
                }
            }
            for (StarSystemAPI sys : Global.getSector().getStarSystems()) {
                float d = Misc.getDistanceLY(f.getLocationInHyperspace(), sys.getLocation());
                if (d < best && hasRelay(sys)) {
                    best = d;
                    relayName = sys.getBaseName() + " relay";
                }
            }
        } catch (Throwable t) {
            relayName = null;
        }
        return relayName;
    }

    private static String relayName;
    private static long relayAt;

    private static boolean hasRelay(LocationAPI loc) {
        for (SectorEntityToken e : loc.getEntitiesWithTag(Tags.COMM_RELAY)) {
            if (working(e)) return true;
        }
        return false;
    }

    private static boolean working(SectorEntityToken e) {
        return !e.getMemoryWithoutUpdate().getBoolean("$objectiveNonFunctional");
    }

    /** Whether your fleet is within reach of a comm relay. */
    static boolean relayLink() {
        try {
            return Global.getSector().getIntelManager().isPlayerInRangeOfCommRelay();
        } catch (Throwable t) {
            return true;
        }
    }

    /** Plays one of the interface sounds ("Tab" or "Report", the game's own unless settings.json names others), if on. */
    static void sound(String which) {
        if (!on("Audio")) return;
        String id = null;
        try {
            id = Settings.text("warRoom_sound" + which, "Tab".equals(which) ? "ui_intel_minor_message"
                    : "ui_channel_intel_assessment");
            if (id != null && !id.isEmpty() && Global.getSoundPlayer().playUISound(id, 1f, 1f) == null
                    && SOUND_FAILED.add(id)) {
                Global.getLogger(Flourish.class).warn("War Room: the sound " + id + " did not play");
            }
        } catch (Throwable t) {
            // No sound, then; said once in starsector.log.
            if (SOUND_FAILED.add(String.valueOf(id))) Global.getLogger(Flourish.class).warn("War Room: could not play the sound " + id, t);
        }
    }

    /** Sounds that failed to play, each reported once. */
    private static final Set<String> SOUND_FAILED = new HashSet<>();

    /** A label in the small Orbitron font, centred in a box exactly as wide as its own text needs (plus a margin). */
    static float orbitronLabel(TooltipMakerAPI in, String text, Color c, float centreX, float y) {
        return orbitron(in, text, c, centreX, y, true);
    }

    /** The same with its left edge at x. */
    static float orbitronLabelAt(TooltipMakerAPI in, String text, Color c, float x, float y) {
        return orbitron(in, text, c, x, y, false);
    }

    private static float orbitron(TooltipMakerAPI in, String text, Color c, float x, float y, boolean centred) {
        in.setParaSmallOrbitron();
        LabelAPI l = in.createLabel(text, c);
        float w = l.computeTextWidth(text) + 10f;
        l.setAlignment(Alignment.MID);
        l.autoSizeToWidth(w);
        in.addCustomDoNotSetPosition((UIComponentAPI) l).getPosition().inTL(centred ? x - w / 2f : x, y);
        in.setParaFontDefault();
        return w;
    }

    /**
     * The momentum arrow: a small arrowhead in the gaining side's colour just before a momentum line (its text
     * centred in x to x + w at y), pointing to that side.
     */
    static void momentumArrow(TooltipMakerAPI in, String line, float x, float y, float w, boolean right, Color c) {
        if (!on("MomentumArrow")) return;
        float tw = Math.min(w, in.computeStringWidth(line));
        float ax = Math.max(0f, x + (w - tw) / 2f - 11f);
        Ui.arrowhead(in, ax, y + (Ui.lineHeight(in) - 9f) / 2f, right, c);
    }

    /** A Chronicle month as a date stamp: "NOV C206" in a thin box, a faint rule after it. */
    static void stamp(TooltipMakerAPI in, String label, float cw) {
        float[] b = stampBox(in, label, Misc.getBasePlayerColor(), Misc.scaleAlpha(Misc.getBasePlayerColor(), 0.5f), 0f, 2f);
        Ui.rect(in, b[0] + 6f, 2f + b[1] / 2f, Math.max(0f, cw - b[0] - 6f), 1f, RULE);
    }

    /** Words in small Orbitron capitals in a thin box, its top left at (x, y); returns the box's {width, height}. */
    static float[] stampBox(TooltipMakerAPI in, String words, Color text, Color box, float x, float y) {
        String s = words.toUpperCase(Locale.ROOT);
        in.setParaSmallOrbitron();
        float lh = Ui.lineHeight(in);
        LabelAPI l = in.createLabel(s, text);
        // Measured with the label's own font, as for the closing rule.
        float tw = l.computeTextWidth(s) + 10f;
        l.setAlignment(Alignment.MID);
        l.autoSizeToWidth(tw);
        in.addCustomDoNotSetPosition((UIComponentAPI) l).getPosition().inTL(x + 3f, y + 2f);
        in.setParaFontDefault();
        float[] b = {tw + 6f, lh + 4f};
        Ui.frame(in, x, y, b[0], b[1], box);
        return b;
    }

    /** How far a star system is from your fleet: "14 ly", "under 1 ly" or "here"; null if not known. */
    static String distance(StarSystemAPI sys) {
        if (sys == null) return null;
        try {
            CampaignFleetAPI f = Global.getSector().getPlayerFleet();
            if (f == null) return null;
            if (f.getContainingLocation() == sys) return "here";
            int ly = Math.round(Misc.getDistanceLY(f.getLocationInHyperspace(), sys.getLocation()));
            return ly < 1 ? "under 1 ly" : ly + " ly";
        } catch (Throwable t) {
            return null;
        }
    }

    /** The bottom of a tab: the shipboard signature and the closing rule, each if switched on. */
    static void signOff(CustomPanelAPI body, TooltipMakerAPI t, float cw) {
        boolean sign = on("Signature");
        if (sign) t.addPara(signature(), Misc.getGrayColor(), 16f);
        if (!on("EndRule")) return;
        CustomPanelAPI p = body.createCustomPanel(cw, 18f, null);
        TooltipMakerAPI in = p.createUIElement(cw, 18f, false);
        // Measured with the label's own font, so the words are never shortened.
        float tw = orbitronLabel(in, "END OF ASSESSMENT", Misc.getGrayColor(), cw / 2f, 1f);
        float side = Math.max(0f, (cw - tw) / 2f - 8f);
        Ui.rect(in, 0f, 8f, side, 1f, RULE);
        Ui.rect(in, cw - side, 8f, side, 1f, RULE);
        p.addUIElement(in).inTL(0f, 0f);
        t.addCustom(p, sign ? 8f : 16f);
    }

    /** "Compiled aboard the ISS Meridian, fleet intelligence section." */
    static String signature() {
        String ship = null;
        try {
            CampaignFleetAPI f = Global.getSector().getPlayerFleet();
            FleetMemberAPI m = f == null ? null : f.getFlagship();
            ship = m == null ? null : m.getShipName();
        } catch (Throwable ignored) {
            // no ship name, then
        }
        return ship == null || ship.trim().isEmpty() ? "Compiled by the fleet's intelligence section."
                : "Compiled aboard the " + ship.trim() + ", fleet intelligence section.";
    }
}
