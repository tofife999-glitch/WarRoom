package warroom.ui;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignClockAPI;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.ui.Alignment;
import com.fs.starfarer.api.ui.ButtonAPI;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.LabelAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.ui.UIComponentAPI;
import com.fs.starfarer.api.util.Misc;
import warroom.Fmt;
import warroom.Score;
import warroom.SectorSnapshot;
import warroom.data.Ledger;
import warroom.data.LogEntry;
import warroom.data.StoryEvent;
import warroom.data.WarData;
import warroom.data.WarRecord;
import warroom.track.Alliances;
import warroom.track.WarTracker;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.GregorianCalendar;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The Archive: a timeline of every war on record (one row per pair of factions, or per alliance war; each
 * war a bar tinted by who held the advantage at the time, with icons for colonies captured, major battles,
 * bombardments and ceasefires sitting on it), then the Chronicle: every notable event of those wars and
 * the Sector news, newest first, by month. Narrowed to one faction (the Faction Archive, opened from the game's
 * Factions screen or a faction's flag or name in the report) it shows that faction's wars, one row per enemy,
 * and its events and news; narrowed to one alliance (the Alliance Archive, from an alliance's name), the wars its
 * members fought in it and its news.
 */
final class ArchiveView {
    private static final float ROW_H = 46f;
    private static final float ICON = 20f;
    private static final float ICON_Y = 3f;
    private static final float BAR_Y = 25f;
    private static final float BAR_H = 14f;
    /** Zoom presets in cycles; "All" is -1. */
    private static final int[] ZOOMS = {1, 3, 5, 10};
    private static final long CYCLE_DAYS = 365L;
    private static final long DAY = WarData.DAY;
    private static final Color GRID = new Color(24, 38, 46);
    /** The faint outline right around each bar. */
    private static final Color OUTLINE = new Color(220, 235, 240, 62);
    private static final Color PEACE = new Color(230, 238, 240);
    /** The cycle bands (barely there) and the now line. */
    private static final Color BAND = new Color(120, 170, 190, 6);
    private static final Color NOW = new Color(230, 196, 106, 170);
    /** The Chronicle's filters; the index is View.chronicleFilter. */
    static final String[] FILTERS = {"All", "Wars and turning points", "Colonies", "Major battles", "Alliances and factions"};

    private ArchiveView() {
    }

    /** One timeline row: all wars of one pair of factions, or one alliance war. */
    private static final class Row {
        final List<WarRecord> wars = new ArrayList<>();
        Wars.AllianceWar group;
        long first = Long.MAX_VALUE;
        long last = Long.MIN_VALUE;
        boolean live;

        String ref() {
            return group != null ? "G:" + group.key : wars.get(wars.size() - 1).id();
        }
    }

    /** What the Archive is narrowed to: one faction, or one alliance (followed through merges), or neither. */
    private static final class Focus {
        final String faction;
        final String alliance;
        final String allianceName;
        /** The names the alliance has gone by, for its news. */
        final Set<String> names = new HashSet<>();

        Focus(WarData d, String faction, String alliance, String allianceName) {
            this.faction = faction;
            this.alliance = alliance == null ? null : d.canonAlliance(alliance);
            String name = allianceName;
            if (this.alliance != null) {
                if (allianceName != null) names.add(allianceName);
                for (Map.Entry<String, String> e : d.knownAlliances.entrySet()) {
                    if (e.getValue() != null && this.alliance.equals(d.canonAlliance(e.getKey()))) names.add(e.getValue());
                }
                String known = d.knownAlliances.get(this.alliance);
                name = Alliances.name(this.alliance, allianceName != null ? allianceName : known != null ? known : "an alliance");
                names.add(name);
            }
            this.allianceName = name;
        }

        boolean any() {
            return faction != null || alliance != null;
        }

        /** The war's side that is this faction or alliance; -1 if the war is not its own. */
        int side(WarData d, WarRecord r) {
            if (faction != null) return r.involves(faction) ? r.side(faction) : -1;
            if (alliance != null) {
                for (int k = 0; k < 2; k++) {
                    if (alliance.equals(d.canonAlliance(r.allianceId(k)))) return k;
                }
            }
            return -1;
        }

        boolean covers(WarData d, WarRecord r) {
            return !any() || side(d, r) >= 0;
        }

        /** The wars the Archive has dropped, summed. */
        Ledger retired(WarData d) {
            if (faction != null) {
                Ledger l = new Ledger();
                l.add(d.retired.get(faction));
                return l;
            }
            return alliance != null ? d.retiredOfAlliance(alliance) : new Ledger();
        }

        boolean covers(LogEntry n) {
            if (faction != null) return faction.equals(n.a) || faction.equals(n.b);
            if (alliance != null) return n.kind.isAlliance() && (names.contains(n.text) || names.contains(n.text2));
            return true;
        }
    }

    static void build(CustomPanelAPI body, float w, float h, SectorSnapshot s, long now) {
        TooltipMakerAPI t = body.createUIElement(w, h, true);
        float cw = w - 20f; // room for the scrollbar
        WarData d = WarData.get();

        if (View.archiveFaction != null && Wars.faction(View.archiveFaction) == null) {
            View.archiveFaction = null; // a faction no longer in the game
        }
        final Focus focus = new Focus(d, View.archiveFaction, View.archiveAlliance, View.archiveAllianceName);
        List<WarRecord> all = new ArrayList<>(d.active.values());
        all.addAll(d.archive);
        // A pirate faction's own archive shows all its wars; the toggle would hide every one of them.
        boolean pirateView = focus.faction != null && WarTracker.FACTIONS.isPirate(focus.faction);
        boolean pirates = View.showPirates || pirateView;
        if (focus.any()) {
            all.removeIf(r -> !focus.covers(d, r));
            focusHead(body, t, cw, focus, all, d);
        }
        int[] hidden = new int[1];
        List<Row> rows = rows(all, now, hidden, pirates, focus.faction != null);

        if (!pirateView && (hidden[0] > 0 || View.showPirates)) {
            String label = View.showPirates ? "Showing wars with pirate factions" : "Show " + hidden[0] + " more "
                    + (hidden[0] == 1 ? "war" : "wars") + " with pirate factions";
            ButtonAPI b = t.addAreaCheckbox(label, View.Action.of(View.Action.Kind.PIRATES), Misc.getBasePlayerColor(),
                    Misc.getDarkPlayerColor(), Misc.getBrightPlayerColor(), 380f, 24f, 0f);
            b.setChecked(View.showPirates);
        }

        t.addSectionHeading("Timeline", Alignment.MID, 10f);
        long earliest = d.since != 0 ? d.since : now;
        for (Row r : rows) earliest = Math.min(earliest, r.first);
        long needDays = Math.max(30L, (now - earliest) / DAY + 1);
        int zoom = View.zoom;
        if (zoom == 0) {
            zoom = -1;
            for (int z : ZOOMS) {
                if (z * CYCLE_DAYS >= needDays) {
                    zoom = z;
                    break;
                }
            }
        }
        long spanDays = zoom > 0 ? zoom * CYCLE_DAYS : needDays;
        long ws = now - spanDays * DAY;
        zoomRow(body, t, cw, zoom);

        List<String> walk = new ArrayList<>();
        List<Row> inView = new ArrayList<>();
        for (Row r : rows) {
            if (r.last < ws) continue;
            inView.add(r);
            if (r.group != null) {
                walk.add(r.ref());
            } else {
                for (int i = r.wars.size() - 1; i >= 0; i--) { // latest first, as the name opens it
                    WarRecord war = r.wars.get(i);
                    if ((war.ended() ? war.end : now) >= ws) walk.add(war.id());
                }
            }
        }
        if (inView.isEmpty()) {
            Phrase none = new Phrase();
            if (!rows.isEmpty()) none.add("No wars in this period.");
            else if (hidden[0] > 0 && !pirates) none.add("No wars on record except with pirate factions.");
            else if (focus.any() && focus.retired(d).outcome(Ledger.WARS) > 0) {
                none.add("Its wars are older than the Archive keeps; the service record above still counts them.");
            }
            else if (focus.faction != null) none.add("No wars on record involving ").the(focus.faction, false).add(" yet.");
            else if (focus.alliance != null) none.add("No wars on record for " + focus.allianceName + " yet.");
            else none.add("No wars on record yet.");
            Tips.para(t, none, 10f).setColor(Misc.getGrayColor());
        } else {
            legend(body, t, cw);
            List<long[]> ticks = ticks(ws, now);
            List<long[]> bands = Flourish.on("CycleBands") ? bands(ws, now) : java.util.Collections.<long[]>emptyList();
            axis(body, t, cw, ws, spanDays, ticks);
            for (Row r : inView) row(body, t, cw, r, s, ws, spanDays, now, walk, ticks, bands, focus, d);
        }

        chronicle(body, t, cw, d, ws, now, focus, pirates);
        if (d.since != 0) {
            Tips.para(t, new Phrase().add("Records begin ").hl(Fmt.date(d.since), Misc.getHighlightColor())
                    .add(". Nothing earlier is on record."), 16f).setColor(Misc.getGrayColor());
        }
        Flourish.signOff(body, t, cw);
        t.addSpacer(20f);
        body.addUIElement(t).inTL(0f, 0f);
        View.restoreScroll(t.getExternalScroller());
    }

    // ------------------------------------------------------------------ the timeline

    /**
     * The rows that pass the pirate rule, wars going on first, then by when they last saw action. With
     * {@code pairsOnly} (one faction's archive) every war goes on its pair's row, alliance wars included.
     */
    private static List<Row> rows(List<WarRecord> all, long now, int[] hidden, boolean pirates, boolean pairsOnly) {
        Map<String, List<WarRecord>> byGroup = new LinkedHashMap<>();
        for (WarRecord r : all) {
            if (r.group != null && !pairsOnly) byGroup.computeIfAbsent(r.group, k -> new ArrayList<>()).add(r);
        }
        List<Row> rows = new ArrayList<>();
        Map<String, Row> byPair = new LinkedHashMap<>();
        for (WarRecord r : all) {
            List<WarRecord> fronts = r.group == null ? null : byGroup.get(r.group);
            if (fronts != null && fronts.size() >= 2) continue;
            byPair.computeIfAbsent(r.pairKey(), k -> new Row()).wars.add(r);
        }
        for (Map.Entry<String, List<WarRecord>> e : byGroup.entrySet()) {
            if (e.getValue().size() < 2) continue;
            Wars.AllianceWar g = Wars.group(e.getKey(), e.getValue());
            if (g == null) continue;
            Row row = new Row();
            row.group = g;
            row.wars.addAll(g.fronts);
            rows.add(row);
        }
        rows.addAll(byPair.values());
        List<Row> shown = new ArrayList<>();
        for (Row row : rows) {
            boolean visible = false;
            for (WarRecord r : row.wars) {
                row.first = Math.min(row.first, r.start);
                row.last = Math.max(row.last, r.ended() ? r.end : now);
                if (!r.ended()) row.live = true;
                if (Wars.shown(r, pirates, now)) visible = true;
            }
            row.wars.sort(Comparator.comparingLong((WarRecord r) -> r.start));
            if (visible) shown.add(row);
            else hidden[0] += row.group != null ? 1 : row.wars.size();
        }
        shown.sort(Comparator.comparing((Row r) -> !r.live).thenComparingLong((Row r) -> -latest(r))
                .thenComparing(Row::ref));
        return shown;
    }

    private static long latest(Row r) {
        long t = Long.MIN_VALUE;
        for (WarRecord w : r.wars) t = Math.max(t, r.live ? Wars.latestNotable(w) : (w.ended() ? w.end : w.start));
        return t;
    }

    private static void zoomRow(CustomPanelAPI body, TooltipMakerAPI t, float cw, int zoom) {
        CustomPanelAPI p = body.createCustomPanel(cw, 28f, null);
        TooltipMakerAPI in = p.createUIElement(cw, 28f, false);
        String[] labels = {"1 cycle", "3 cycles", "5 cycles", "10 cycles", "All"};
        int[] values = {1, 3, 5, 10, -1};
        float bw = 90f;
        for (int i = 0; i < labels.length; i++) {
            ButtonAPI b = in.addAreaCheckbox(labels[i], View.Action.of(View.Action.Kind.ZOOM, null, values[i]),
                    Misc.getBasePlayerColor(), Misc.getDarkPlayerColor(), Misc.getBrightPlayerColor(), bw, 24f, 0f);
            b.setChecked(values[i] == zoom);
            b.getPosition().inTL(i * (bw + 6f), 2f);
        }
        p.addUIElement(in).inTL(0f, 0f);
        t.addCustom(p, 8f);
    }

    private static float nameW(float cw) {
        return Math.min(240f, cw * 0.27f);
    }

    /**
     * The heading of a Faction or Alliance Archive: the name (a faction's in its colours), its flag or the crests of
     * the alliance's fighting members, and the ways back (Back to where it was opened from in the report).
     */
    private static void focusHead(CustomPanelAPI body, TooltipMakerAPI t, float cw, Focus focus, List<WarRecord> wars,
                                  WarData d) {
        FactionAPI f = focus.faction == null ? null : Wars.faction(focus.faction);
        if (f != null) {
            t.addSectionHeading("Faction Archive: " + Wars.name(focus.faction), f.getBaseUIColor(), f.getDarkUIColor(),
                    Alignment.MID, 0f);
        } else {
            t.addSectionHeading("Alliance Archive: " + focus.allianceName, Alignment.MID, 0f);
        }
        float h = 34f;
        CustomPanelAPI p = body.createCustomPanel(cw, h, null);
        TooltipMakerAPI in = p.createUIElement(cw, h, false);
        float bw = 130f;
        boolean back = View.canGoBackFromArchive();
        float buttonsW = back ? 2f * bw + 6f : bw;
        float x = 0f;
        Phrase about = new Phrase();
        if (f != null) {
            float fw = Ui.flag(in, f, 0f, 4f, 26f);
            x = fw > 0f ? fw + 10f : 0f;
            about.add("Every war on record involving ").the(focus.faction, false).add(", and its news.");
        } else {
            // The factions that fought for the alliance, as crests, as many as fit.
            List<String> fought = new ArrayList<>();
            for (WarRecord r : wars) {
                int k = focus.side(d, r);
                if (k >= 0 && !fought.contains(r.id(k))) fought.add(r.id(k));
            }
            for (String id : fought) {
                FactionAPI m = Wars.faction(id);
                if (m == null || x + 24f > cw * 0.3f) continue;
                Ui.crest(in, m, x, 6f, 22f);
                x += 26f;
            }
            if (x > 0f) x += 6f;
            about.add("Every war on record its members fought in the alliance, and its news.");
        }
        Ui.text(in, about.text(), Misc.getGrayColor(), x, 9f, Math.max(60f, cw - x - buttonsW - 10f), 1, Alignment.LMID,
                about.highlights(), about.colors());
        if (back) {
            ButtonAPI bb = Ui.button(in, "Back", View.Action.of(View.Action.Kind.ARCHIVE_BACK), cw - buttonsW, 5f, bw, 24f);
            Ui.tooltip(in, bb, Tips.text("Back to where you opened this archive."));
        }
        ButtonAPI b = Ui.button(in, "All factions", View.Action.of(View.Action.Kind.ALL_FACTIONS), cw - bw, 5f, bw, 24f);
        Ui.tooltip(in, b, Tips.text("Show the whole Archive again."));
        p.addUIElement(in).inTL(0f, 0f);
        t.addCustom(p, 6f);
        serviceRecord(body, t, cw, focus, wars, d);
        t.addSpacer(4f);
    }

    /**
     * A faction's or alliance's service record: its wars summed up, those the Archive no longer shows included.
     * First the figures of its own (how many wars, how they ended), then the counters mirrored against its
     * enemies, as in a war's military report.
     */
    private static void serviceRecord(CustomPanelAPI body, TooltipMakerAPI t, float cw, Focus focus, List<WarRecord> wars,
                                      WarData d) {
        Ledger led = new Ledger();
        for (WarRecord r : wars) {
            int k = focus.side(d, r);
            if (k >= 0) led.add(r, k);
        }
        led.add(focus.retired(d));
        boolean alliance = focus.faction == null;
        int all = led.outcome(Ledger.WARS);
        int ended = led.outcome(Ledger.ENDED);
        figures(body, t, cw, new String[]{alliance ? "Fronts on record" : "Wars on record", "Under way",
                        "Ended with the advantage", "Evenly matched", "At a disadvantage"},
                new int[]{all, all - ended, led.outcome(Ledger.AHEAD), led.outcome(Ledger.EVEN), led.outcome(Ledger.BEHIND)},
                new String[]{(alliance ? "Each war of one member against one enemy is a front" : "Each war against one "
                        + "enemy is one") + ", those the Archive no longer shows included.",
                        null, "Ended with this side holding the advantage on the war score.",
                        "Ended with neither side holding the advantage, or with nothing to report.",
                        "Ended with the enemy holding the advantage."});
        figures(body, t, cw, new String[]{"Ceasefires", "Peace treaties", "Hostilities ceased", "Enemies eliminated",
                        "Eliminated"},
                new int[]{led.outcome(Ledger.CEASEFIRE), led.outcome(Ledger.TREATY), led.outcome(Ledger.CEASED),
                        led.outcome(Ledger.ENEMY_GONE), led.outcome(Ledger.GONE)},
                new String[]{null, null, "Peace made some other way, such as by an alliance vote, or before War Room told "
                        + "a ceasefire from a treaty.", "The enemy held no colonies for 30 days.",
                        alliance ? "A member held no colonies for 30 days." : "It held no colonies for 30 days."});

        // The counters, mirrored against its enemies.
        String name = alliance ? focus.allianceName : Wars.name(focus.faction);
        Color nc = alliance ? Misc.getBasePlayerColor() : color(Wars.faction(focus.faction));
        float lw = Math.min(200f, cw * 0.4f);
        float nw = (cw - lw) / 2f - 6f;
        CustomPanelAPI hp = body.createCustomPanel(cw, 20f, null);
        TooltipMakerAPI hin = hp.createUIElement(cw, 20f, false);
        Ui.text(hin, name, nc, 0f, 0f, nw, 1, Alignment.RMID);
        Ui.text(hin, "Its enemies", Misc.getTextColor(), nw + lw + 12f, 0f, nw, 1, Alignment.LMID);
        hp.addUIElement(hin).inTL(0f, 0f);
        t.addCustom(hp, 10f);
        int wa = led.fightsWon(true);
        int wb = led.fightsWon(false);
        ledgerRow(body, t, cw, lw, nw, Fmt.num(wa), "Battles won", Fmt.num(wb), wa > wb, wb > wa);
        int ca = led.us(WarRecord.CAPTURED);
        int cb = led.them(WarRecord.CAPTURED);
        ledgerRow(body, t, cw, lw, nw, captured(ca, led.us(WarRecord.CAPTURED_SIZE)), "Colonies captured",
                captured(cb, led.them(WarRecord.CAPTURED_SIZE)), ca > cb, cb > ca);
        int ba = led.us(WarRecord.BOMBARDED_SIZE);
        int bb = led.them(WarRecord.BOMBARDED_SIZE);
        if (ba > 0 || bb > 0) ledgerRow(body, t, cw, lw, nw, Fmt.num(ba), "Colony size destroyed", Fmt.num(bb), false, false);
        ledgerRow(body, t, cw, lw, nw, (led.lossesEstimated(true) ? "~" : "") + Fmt.num(led.shipsLost(true)), "Ships lost",
                (led.lossesEstimated(false) ? "~" : "") + Fmt.num(led.shipsLost(false)), false, false);
        ledgerRow(body, t, cw, lw, nw, of(led.us(WarRecord.RAIDS_WON), led.us(WarRecord.RAIDS)), "Raids won",
                of(led.them(WarRecord.RAIDS_WON), led.them(WarRecord.RAIDS)), false, false);
        ledgerRow(body, t, cw, lw, nw, of(led.us(WarRecord.INVASIONS_WON), led.us(WarRecord.INVASIONS)), "Invasions won",
                of(led.them(WarRecord.INVASIONS_WON), led.them(WarRecord.INVASIONS)), false, false);
    }

    /** A row of figures: each number over its label, the label's explanation on hover. */
    private static void figures(CustomPanelAPI body, TooltipMakerAPI t, float cw, String[] labels, int[] values, String[] tips) {
        float h = 50f;
        float cell = cw / labels.length;
        CustomPanelAPI p = body.createCustomPanel(cw, h, null);
        TooltipMakerAPI in = p.createUIElement(cw, h, false);
        for (int i = 0; i < labels.length; i++) {
            float x = i * cell;
            in.setParaInsigniaLarge();
            Ui.Placed v = Ui.text(in, Fmt.num(values[i]), values[i] > 0 ? Misc.getHighlightColor() : Misc.getGrayColor(),
                    x, 0f, cell, 1, Alignment.MID);
            in.setParaFontDefault();
            Ui.Placed l = Ui.text(in, labels[i], Misc.getGrayColor(), x + 4f, v.height + 2f, cell - 8f, 2, Alignment.TMID);
            if (tips[i] != null) Ui.tooltip(in, l.comp, Tips.text(tips[i]));
        }
        p.addUIElement(in).inTL(0f, 0f);
        t.addCustom(p, 8f);
    }

    private static void ledgerRow(CustomPanelAPI body, TooltipMakerAPI t, float cw, float lw, float nw, String a, String label,
                                  String b, boolean brightA, boolean brightB) {
        CustomPanelAPI row = body.createCustomPanel(cw, 18f, null);
        TooltipMakerAPI in = row.createUIElement(cw, 18f, false);
        Color h = Misc.getHighlightColor();
        Ui.text(in, a, brightA ? h : Misc.getTextColor(), 0f, 0f, nw, 1, Alignment.RMID);
        Ui.text(in, label, Misc.getGrayColor(), nw + 6f, 0f, lw, 1, Alignment.MID);
        Ui.text(in, b, brightB ? h : Misc.getTextColor(), nw + lw + 12f, 0f, nw, 1, Alignment.LMID);
        row.addUIElement(in).inTL(0f, 0f);
        t.addCustom(row, 3f);
    }

    private static String captured(int count, int size) {
        return count == 0 ? "0" : Fmt.num(count) + " (size " + Fmt.num(size) + ")";
    }

    private static String of(int won, int launched) {
        return launched == 0 ? "0" : Fmt.num(won) + " of " + Fmt.num(launched);
    }

    /** What the icons and the bar colours mean, above the timeline. */
    private static void legend(CustomPanelAPI body, TooltipMakerAPI t, float cw) {
        CustomPanelAPI p = body.createCustomPanel(cw, 50f, null);
        TooltipMakerAPI in = p.createUIElement(cw, 50f, false);
        Color tc = Misc.getTextColor();
        Color g = Misc.getGrayColor();
        Object[][] items = {{Icons.Kind.CAPTURE, "Colony captured", tc}, {Icons.Kind.BATTLE, "Major battle won", tc},
                {Icons.Kind.BOMBARD, "Bombardment", tc}, {Icons.Kind.CEASEFIRE, "Ceasefire or peace treaty", PEACE}};
        float x = 0f;
        for (Object[] it : items) {
            Icons.place(p, in, (Icons.Kind) it[0], (Color) it[2], x, 2f, ICON);
            String label = (String) it[1];
            float lw = in.computeStringWidth(label) + 4f;
            Ui.text(in, label, tc, x + ICON + 5f, 4f, lw, 1, Alignment.LMID);
            x += ICON + 5f + lw + 18f;
        }
        // The bar's shades, drawn as the bars draw them, for two sample sides.
        float y = 30f;
        String bar = "Bar:";
        float bw = in.computeStringWidth(bar) + 4f;
        Ui.text(in, bar, g, 0f, y - 1f, bw, 1, Alignment.LMID);
        x = bw + 6f;
        Color[] sample = {new Color(232, 163, 58), new Color(74, 168, 255)};
        int[][] shades = {{-1}, {0}, {1, 2}};
        String[] words = {"nothing to report yet", "evenly matched", "advantage"};
        for (int i = 0; i < shades.length; i++) {
            for (int k : shades[i]) {
                swatch(in, x, y + 2f, tint(k, sample, false));
                x += 20f;
            }
            x += 4f;
            float ww = in.computeStringWidth(words[i]) + 4f;
            Ui.text(in, words[i], g, x, y - 1f, ww, 1, Alignment.LMID);
            x += ww + 16f;
        }
        p.addUIElement(in).inTL(0f, 0f);
        t.addCustom(p, 8f);
        t.addPara("A bar takes the colour of the side holding the advantage at that moment, on the war score since the "
                + "war began; it is dimmer once a war is over. Icons take the colour of the side that scored them. Hover "
                + "a bar or an icon for details; click a bar to open the war's report.", g, 2f);
    }

    private static void swatch(TooltipMakerAPI in, float x, float y, Color c) {
        Ui.rect(in, x, y, 18f, 10f, c);
        outline(in, x, y, 18f, 10f);
    }

    /** The faintest outline around a bar. */
    private static void outline(TooltipMakerAPI in, float x, float y, float w, float h) {
        Ui.rect(in, x - 1f, y - 1f, w + 2f, 1f, OUTLINE);
        Ui.rect(in, x - 1f, y + h, w + 2f, 1f, OUTLINE);
        Ui.rect(in, x - 1f, y, 1f, h, OUTLINE);
        Ui.rect(in, x + w, y, 1f, h, OUTLINE);
    }

    /**
     * Gridline dates: every 3 months over a cycle or less, every 6 over 3 cycles, else every cycle (every
     * second one past 10). Each is {timestamp, 1 for the start of a cycle}. The game's calendar is the
     * Gregorian one with the cycle as the year.
     */
    private static List<long[]> ticks(long ws, long now) {
        long days = (now - ws) / DAY;
        int step = days <= 400 ? 3 : days <= 1200 ? 6 : days <= 4000 ? 12 : 24;
        List<long[]> out = new ArrayList<>();
        GregorianCalendar c = new GregorianCalendar();
        c.setTimeInMillis(ws);
        c.set(Calendar.DAY_OF_MONTH, 1);
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        for (int guard = 0; guard < 12000; guard++) {
            c.add(Calendar.MONTH, 1);
            int month = c.get(Calendar.MONTH);
            int year = c.get(Calendar.YEAR);
            boolean on = step <= 12 ? month % step == 0 : month == 0 && year % 2 == 0;
            long ts = c.getTimeInMillis();
            if (ts > now) break;
            if (on && ts > ws) out.add(new long[]{ts, month == 0 ? 1 : 0});
        }
        return out;
    }

    /** Every other cycle within the span ({start, end}), for the faint cycle bands. */
    private static List<long[]> bands(long ws, long now) {
        List<long[]> out = new ArrayList<>();
        GregorianCalendar c = new GregorianCalendar();
        c.setTimeInMillis(ws);
        int year = c.get(Calendar.YEAR);
        for (int guard = 0; guard < 200; guard++, year++) {
            GregorianCalendar a = new GregorianCalendar(year, 0, 1);
            GregorianCalendar b = new GregorianCalendar(year + 1, 0, 1);
            long from = Math.max(ws, a.getTimeInMillis());
            long to = Math.min(now, b.getTimeInMillis());
            if (from >= now) break;
            if (year % 2 == 0 && to > from) out.add(new long[]{from, to});
        }
        return out;
    }

    private static void axis(CustomPanelAPI body, TooltipMakerAPI t, float cw, long ws, long spanDays, List<long[]> ticks) {
        CustomPanelAPI p = body.createCustomPanel(cw, 18f, null);
        TooltipMakerAPI in = p.createUIElement(cw, 18f, false);
        float x0 = nameW(cw) + 10f;
        float tw = cw - x0;
        float span = spanDays * (float) DAY;
        Color g = Misc.getGrayColor();
        float todayW = in.computeStringWidth("Today") + 4f;
        Ui.text(in, "Today", Flourish.on("NowLine") ? Flourish.AMBER : g, cw - todayW, 0f, todayW, 1, Alignment.RMID);
        for (long[] tk : ticks) {
            float x = x0 + (tk[0] - ws) / span * tw;
            CampaignClockAPI c = Global.getSector().getClock().createClock(tk[0]);
            String label = tk[1] == 1 ? "c" + c.getCycle() : c.getShortMonthString();
            float lw = in.computeStringWidth(label) + 4f;
            if (x + 3f + lw > cw - todayW - 4f) continue;
            Ui.text(in, label, tk[1] == 1 ? Misc.getTextColor() : g, x + 3f, 0f, lw, 1, Alignment.LMID);
        }
        p.addUIElement(in).inTL(0f, 0f);
        t.addCustom(p, 6f);
    }

    /** One rivalry or alliance war: its names (open its latest war), then each war as a bar (opens that war). */
    private static void row(CustomPanelAPI body, TooltipMakerAPI t, float cw, Row row, SectorSnapshot s, long ws,
                            long spanDays, long now, List<String> walk, List<long[]> ticks, List<long[]> bands, Focus focus,
                            WarData d) {
        CustomPanelAPI p = body.createCustomPanel(cw, ROW_H, null);
        TooltipMakerAPI in = p.createUIElement(cw, ROW_H, false);
        float nameW = nameW(cw);
        float x0 = nameW + 10f;
        float tw = cw - x0;
        float span = spanDays * (float) DAY;

        for (long[] bd : bands) {
            float bx = x0 + (bd[0] - ws) / span * tw;
            Ui.rect(in, bx, 0f, x0 + (bd[1] - ws) / span * tw - bx, ROW_H, BAND);
        }
        for (long[] tk : ticks) Ui.rect(in, x0 + (tk[0] - ws) / span * tw, 0f, 1f, ROW_H, GRID);
        Ui.rect(in, 0f, ROW_H - 1f, cw, 1f, new Color(15, 25, 32));

        Color[] c;
        FactionAPI fa;
        FactionAPI fb;
        String na;
        String nb;
        WarRecord latest = row.wars.get(row.wars.size() - 1);
        int left = row.group != null ? 0 : latest.left;
        if (row.group != null) {
            c = AllianceBlock.colors(row.group, s);
            List<String> ma = row.group.members(0, s);
            List<String> mb = row.group.members(1, s);
            fa = ma.isEmpty() ? null : Wars.faction(ma.get(0));
            fb = mb.isEmpty() ? null : Wars.faction(mb.get(0));
            na = row.group.name[0];
            nb = row.group.name[1];
        } else {
            fa = Wars.faction(latest.id(left));
            fb = Wars.faction(latest.id(1 - left));
            c = new Color[]{color(fa), color(fb)};
            na = Wars.name(latest.id(left));
            nb = Wars.name(latest.id(1 - left));
        }
        ButtonAPI name = Ui.flatButton(in, View.Action.of(View.Action.Kind.OPEN, row.ref(), walk), 0f, 1f, nameW, ROW_H - 2f);
        Ui.tooltip(in, name, Tips.text(row.group != null ? "Alliance war. Open its report for the fronts."
                : row.wars.size() == 1 ? "Open the report on this war." : "Open the report on the latest war; each bar is one war."));
        // In a faction's or alliance's own archive it is named first; the bar keeps the war's own sides.
        boolean flip;
        if (row.group != null) {
            flip = focus.alliance != null && focus.alliance.equals(d.canonAlliance(row.group.allianceId[1]))
                    && !focus.alliance.equals(d.canonAlliance(row.group.allianceId[0]));
        } else {
            int mine = focus.side(d, latest);
            flip = mine >= 0 && mine != left;
        }
        // A faction eliminated in the row's latest war is struck from the record here too.
        boolean struckA = false;
        boolean struckB = false;
        if (row.group == null && latest.ended() && Flourish.on("StruckTimeline")) {
            int gone = latest.endReason == WarRecord.END_GONE_A ? 0 : latest.endReason == WarRecord.END_GONE_B ? 1 : -1;
            struckA = gone >= 0 && gone == left;
            struckB = gone >= 0 && gone != left;
        }
        nameLine(in, flip ? fb : fa, new Phrase().hl(flip ? nb : na, c[flip ? 1 : 0]), 6f, 6f, nameW - 10f,
                (flip ? struckB : struckA) ? (flip ? nb : na) : null);
        nameLine(in, flip ? fa : fb, new Phrase().hl("vs ", Misc.getGrayColor()).hl(flip ? na : nb, c[flip ? 0 : 1]),
                6f, 25f, nameW - 10f, (flip ? struckA : struckB) ? (flip ? na : nb) : null);

        if (row.group != null) {
            Wars.AllianceWar g = row.group;
            segment(p, in, null, g, g.start(), g.ended() ? g.end() : now, ws, span, x0, tw, c, now, walk, ticks, bands);
        } else {
            for (WarRecord r : row.wars) {
                long b = r.ended() ? r.end : now;
                if (b < ws) continue;
                // The row's colours follow its latest war; an older war with its sides the other way round swaps them.
                Color[] cr = r.id(r.left).equals(latest.id(left)) ? c : new Color[]{c[1], c[0]};
                segment(p, in, r, null, r.start, b, ws, span, x0, tw, cr, now, walk, ticks, bands);
            }
        }
        // Today, through every row (over the bars).
        if (Flourish.on("NowLine")) Ui.rect(in, x0 + tw - 1f, 0f, 1f, ROW_H, NOW);
        p.addUIElement(in).inTL(0f, 0f);
        t.addCustom(p, 0f);
    }

    /**
     * A small flag and a name on one line; the name is shortened only if it can't fit. {@code struck}, if given,
     * is the part of the line to strike through.
     */
    private static void nameLine(TooltipMakerAPI in, FactionAPI f, Phrase name, float x, float y, float w, String struck) {
        float fw = 0f;
        if (f != null) fw = Ui.flag(in, f, x, y + 1f, 12f);
        float tx = x + (fw > 0f ? fw + 6f : 0f);
        Ui.Placed p = Ui.text(in, name.text(), Misc.getTextColor(), tx, y, Math.max(20f, w - (tx - x)), 1, Alignment.LMID,
                name.highlights(), name.colors());
        int at = struck == null ? -1 : name.text().indexOf(struck);
        if (at >= 0 && at < p.shown.length()) {
            float sx = tx + in.computeStringWidth(name.text().substring(0, at));
            float ex = Math.min(sx + in.computeStringWidth(struck), tx + in.computeStringWidth(p.shown));
            if (ex > sx) Ui.rect(in, sx, y + p.height / 2f, ex - sx, 1f, Flourish.STRUCK);
        }
    }

    private static Color color(FactionAPI f) {
        return f == null ? Misc.getGrayColor() : Ui.readable(f);
    }

    /** A marker: its icon, colour, place and the events it stands for (the first one shown). */
    private static final class Mark {
        final Icons.Kind kind;
        final Color color;
        final float x;
        final int weight;
        final List<Object[]> events = new ArrayList<>();

        Mark(Icons.Kind kind, Color color, float x, int weight) {
            this.kind = kind;
            this.color = color;
            this.x = x;
            this.weight = weight;
        }
    }

    /** A war's bar: a row-high flat button, the tint drawn over it with the faintest outline, icons on it. */
    private static void segment(CustomPanelAPI host, TooltipMakerAPI in, WarRecord r, Wars.AllianceWar g, long a, long b,
                                long ws, float span, float x0, float tw, Color[] c, long now, List<String> walk,
                                List<long[]> ticks, List<long[]> bands) {
        long from = Math.max(a, ws);
        float sx = x0 + (from - ws) / span * tw;
        float ex = Math.min(x0 + tw, x0 + (b - ws) / span * tw);
        if (ex - sx < 6f) ex = Math.min(x0 + tw, sx + 6f);
        if (ex - sx < 6f) sx = ex - 6f;
        boolean over = r != null ? r.ended() : g.ended();
        String ref = r != null ? r.id() : "G:" + g.key;
        Ui.flatButton(in, View.Action.of(View.Action.Kind.OPEN, ref, walk), sx, 1f, ex - sx, ROW_H - 2f);
        // The button is opaque: draw the cycle bands and the gridlines again over it.
        for (long[] bd : bands) {
            float bx0 = Math.max(sx, x0 + (bd[0] - ws) / span * tw);
            float bx1 = Math.min(ex, x0 + (bd[1] - ws) / span * tw);
            if (bx1 > bx0) Ui.rect(in, bx0, 1f, bx1 - bx0, ROW_H - 2f, BAND);
        }
        for (long[] tk : ticks) {
            float gx = x0 + (tk[0] - ws) / span * tw;
            if (gx > sx && gx < ex) Ui.rect(in, gx, 1f, 1f, ROW_H - 2f, GRID);
        }

        // The tint: which side held the advantage at each point, merged into runs of the same shade.
        int n = Math.max(1, Math.min(80, Math.round((ex - sx) / 5f)));
        float colW = (ex - sx) / n;
        int runStart = 0;
        int runKey = Integer.MIN_VALUE;
        for (int i = 0; i <= n; i++) {
            int key = i < n ? shade(r, g, from + (long) ((i + 1) / (double) n * (b - from))) : Integer.MAX_VALUE;
            if (key != runKey) {
                if (runKey != Integer.MIN_VALUE) {
                    Ui.rect(in, sx + runStart * colW, BAR_Y, (i - runStart) * colW, BAR_H, tint(runKey, c, over));
                }
                runStart = i;
                runKey = key;
            }
        }
        outline(in, sx, BAR_Y, ex - sx, BAR_H);
        // The war's summary on the bar only, so the icons above it keep their own tooltips (clicks pass through).
        UIComponentAPI hit = Ui.rect(in, sx, BAR_Y - 1f, ex - sx, ROW_H - BAR_Y, new Color(0, 0, 0, 0));
        Ui.tooltip(in, hit, summary(r, g, a, b, c, now));

        // Icons: colonies captured, major battles won, bombardments, and the ceasefire that ended it. Where they
        // would overlap, the weightier one shows and its tooltip lists the others too.
        List<WarRecord> fronts = r != null ? Collections.singletonList(r) : g.fronts;
        List<Mark> wanted = new ArrayList<>();
        for (WarRecord f : fronts) {
            int l = r != null ? r.left : g.leftSide(f);
            for (StoryEvent e : f.story) {
                if (e.time < from || e.time > b) continue;
                Icons.Kind k;
                int weight;
                Color col = sideColor(c, e.side, l);
                if (e.kind == StoryEvent.Kind.CAPTURE) {
                    k = Icons.Kind.CAPTURE;
                    weight = 4;
                } else if (e.kind == StoryEvent.Kind.BOMBARD) {
                    k = Icons.Kind.BOMBARD;
                    weight = 3;
                } else if (e.kind == StoryEvent.Kind.BATTLE) {
                    k = Icons.Kind.BATTLE;
                    weight = 2;
                } else if (e.kind == StoryEvent.Kind.ENDED && f.endReason == WarRecord.END_PEACE && (g == null || g.ended())) {
                    k = Icons.Kind.CEASEFIRE;
                    weight = 5;
                    col = PEACE;
                } else {
                    continue;
                }
                float x = Math.max(sx, Math.min(ex, x0 + (e.time - ws) / span * tw));
                Mark m = new Mark(k, col, x, weight);
                m.events.add(new Object[]{f, e});
                wanted.add(m);
            }
        }
        wanted.sort(Comparator.comparingInt((Mark m) -> -m.weight).thenComparingDouble(m -> -m.x));
        List<Mark> placed = new ArrayList<>();
        for (Mark m : wanted) {
            Mark near = null;
            for (Mark pm : placed) {
                if (Math.abs(pm.x - m.x) < ICON - 4f) {
                    near = pm;
                    break;
                }
            }
            if (near != null) near.events.addAll(m.events);
            else placed.add(m);
        }
        for (Mark m : placed) {
            float x = Math.max(x0 - ICON / 2f, Math.min(x0 + tw - ICON, m.x - ICON / 2f));
            m.events.sort(Comparator.comparingLong((Object[] o) -> ((StoryEvent) o[1]).time));
            Icons.place(host, in, m.kind, m.color, x, ICON_Y, ICON);
            // The events behind the icon on hover, from a clear area over it (so the hover never depends on the icon).
            UIComponentAPI iconHit = Ui.rect(in, x, ICON_Y, ICON, ICON, new Color(0, 0, 0, 0));
            Ui.tooltip(in, iconHit, events(m.events));
        }
    }

    /** A tooltip listing logged events, each with its date. */
    private static Tips.Tip events(final List<Object[]> list) {
        return new Tips.Tip(420f) {
            @Override
            void fill(TooltipMakerAPI tt) {
                float pad = 0f;
                for (Object[] o : list) {
                    Tips.para(tt, Story.dated((WarRecord) o[0], (StoryEvent) o[1], false), pad);
                    pad = 6f;
                }
            }
        };
    }

    private static Color sideColor(Color[] c, int side, int left) {
        if (side < 0) return Misc.getGrayColor();
        return side == left ? c[0] : c[1];
    }

    /** -1 nothing yet; 0 even; 1 the left side holds the advantage, 2 the right one. */
    private static int shade(WarRecord r, Wars.AllianceWar g, long at) {
        float pl = 0f;
        float pr = 0f;
        List<WarRecord> fronts = r != null ? Collections.singletonList(r) : g.fronts;
        for (WarRecord f : fronts) {
            if (at <= f.start) continue;
            float days = (at - f.start) / (float) DAY;
            int l = r != null ? r.left : g.leftSide(f);
            pl += f.momentum.recent(days, days + 1f, l);
            pr += f.momentum.recent(days, days + 1f, 1 - l);
        }
        float share = Score.share(pl, pr);
        Score.Verdict v = Score.verdict(share);
        if (v == Score.Verdict.NOTHING) return -1;
        if (v == Score.Verdict.EVEN) return 0;
        return share >= 0.5f ? 1 : 2;
    }

    private static Color tint(int key, Color[] c, boolean over) {
        Color base;
        int alpha;
        if (key < 0) {
            base = Misc.getGrayColor().darker();
            alpha = 150;
        } else if (key == 0) {
            base = Misc.getGrayColor();
            alpha = 200;
        } else {
            base = key == 1 ? c[0] : c[1];
            alpha = 255;
        }
        if (over) base = base.darker();
        return new Color(base.getRed(), base.getGreen(), base.getBlue(), alpha);
    }

    private static Tips.Tip summary(WarRecord r, Wars.AllianceWar g, long a, long b, Color[] c, long now) {
        Phrase p = new Phrase();
        boolean over;
        float share;
        String ln;
        String rn;
        if (r != null) {
            ln = Wars.name(r.id(r.left));
            rn = Wars.name(r.id(1 - r.left));
            over = r.ended();
            share = Score.share(Score.total(r, r.left), Score.total(r, 1 - r.left));
            p.faction(r.id(r.left)).add(" vs ").faction(r.id(1 - r.left)).add(". ");
        } else {
            ln = g.name[0];
            rn = g.name[1];
            over = g.ended();
            share = Score.share(g.points(0), g.points(1));
            p.hl(ln, c[0]).add(" vs ").hl(rn, c[1]).add(". ");
        }
        int days = Fmt.days(a, b);
        boolean unknown = r != null ? !r.startKnown : g.startUnknown();
        p.add(Fmt.date(a)).add(" to ").add(over ? Fmt.date(b) : "today").add(" (").num(days)
                .add(unknown ? "+ days). " : days == 1 ? " day). " : " days). ");
        Story.append(p, Wars.verdict(share, ln, rn, c[0], c[1], over)).add(".");
        final Phrase text = p;
        return new Tips.Tip(400f) {
            @Override
            void fill(TooltipMakerAPI tt) {
                Tips.para(tt, text, 0f);
            }
        };
    }

    // ------------------------------------------------------------------ the Chronicle

    /** One line of the Chronicle. */
    private static final class Entry {
        final long time;
        final Icons.Kind icon;
        final Color color;
        final Phrase text;
        /** The war it belongs to, or null for Sector news. */
        final String ref;
        final String link;
        final int filter;

        Entry(long time, Icons.Kind icon, Color color, Phrase text, String ref, String link, int filter) {
            this.time = time;
            this.icon = icon;
            this.color = color;
            this.text = text;
            this.ref = ref;
            this.link = link;
            this.filter = filter;
        }
    }

    private static void chronicle(CustomPanelAPI body, TooltipMakerAPI t, float cw, WarData d, long ws, long now,
                                  Focus focus, boolean pirates) {
        t.addSectionHeading("Chronicle", Alignment.MID, 18f);
        filters(body, t, cw);
        chronicleLegend(body, t, cw);

        List<Entry> entries = entries(d, ws, now, focus, pirates);
        List<Entry> shown = new ArrayList<>();
        for (Entry e : entries) {
            if (View.chronicleFilter == 0 || e.filter == View.chronicleFilter) shown.add(e);
        }
        shown.sort(Comparator.comparingLong((Entry e) -> -e.time));
        if (shown.isEmpty()) {
            t.addPara("Nothing to report in this period.", Misc.getGrayColor(), 8f);
            return;
        }
        int limit = Math.max(1, View.chronicleLimit);
        List<String> walk = new ArrayList<>();
        for (int i = 0; i < shown.size() && i < limit; i++) {
            String ref = shown.get(i).ref;
            if (ref != null && !walk.contains(ref)) walk.add(ref);
        }
        int month = Integer.MIN_VALUE;
        for (int i = 0; i < shown.size() && i < limit; i++) {
            Entry e = shown.get(i);
            CampaignClockAPI c = Global.getSector().getClock().createClock(e.time);
            int m = c.getCycle() * 12 + c.getMonth();
            if (m != month) {
                month = m;
                monthHeading(body, t, cw, c.getShortMonthString() + " c" + c.getCycle());
            }
            entryRow(body, t, cw, e, walk);
        }
        if (shown.size() > limit) {
            int more = Math.min(100, shown.size() - limit);
            t.addButton("Show " + more + " older", View.Action.of(View.Action.Kind.MORE), 200f, 24f, 10f);
        }
    }

    private static void filters(CustomPanelAPI body, TooltipMakerAPI t, float cw) {
        CustomPanelAPI p = body.createCustomPanel(cw, 28f, null);
        TooltipMakerAPI in = p.createUIElement(cw, 28f, false);
        float x = 0f;
        for (int i = 0; i < FILTERS.length; i++) {
            float bw = in.computeStringWidth(FILTERS[i]) + 26f;
            if (x + bw > cw) break;
            ButtonAPI b = in.addAreaCheckbox(FILTERS[i], View.Action.of(View.Action.Kind.FILTER, null, i),
                    Misc.getBasePlayerColor(), Misc.getDarkPlayerColor(), Misc.getBrightPlayerColor(), bw, 24f, 0f);
            b.setChecked(View.chronicleFilter == i);
            b.getPosition().inTL(x, 2f);
            x += bw + 6f;
        }
        p.addUIElement(in).inTL(0f, 0f);
        t.addCustom(p, 8f);
    }

    /** The icons only the Chronicle uses (the timeline's are explained above it). */
    private static void chronicleLegend(CustomPanelAPI body, TooltipMakerAPI t, float cw) {
        CustomPanelAPI p = body.createCustomPanel(cw, 24f, null);
        TooltipMakerAPI in = p.createUIElement(cw, 24f, false);
        Color g = Misc.getGrayColor();
        Object[][] items = {{Icons.Kind.BEGAN, "War begun"}, {Icons.Kind.ADVANTAGE, "Turning point"}, {Icons.Kind.RAID, "Raid"},
                {Icons.Kind.REPELLED, "Invasion repelled"},
                {Icons.Kind.ALLIANCE, "Alliance news"}, {Icons.Kind.ELIMINATED, "Lost its last colony"},
                {Icons.Kind.ARRIVED, "Now holds colonies"}};
        float x = 0f;
        for (Object[] it : items) {
            String label = (String) it[1];
            float lw = in.computeStringWidth(label) + 4f;
            if (x + ICON + 4f + lw > cw) break;
            Icons.place(p, in, (Icons.Kind) it[0], Misc.getTextColor(), x, 2f, ICON);
            Ui.text(in, label, g, x + ICON + 4f, 4f, lw, 1, Alignment.LMID);
            x += ICON + 4f + lw + 14f;
        }
        p.addUIElement(in).inTL(0f, 0f);
        t.addCustom(p, 4f);
    }

    /** A month's heading in the Chronicle: a date stamp, or (with that touch off) the month over a rule. */
    private static void monthHeading(CustomPanelAPI body, TooltipMakerAPI t, float cw, String label) {
        CustomPanelAPI p = body.createCustomPanel(cw, 22f, null);
        TooltipMakerAPI in = p.createUIElement(cw, 22f, false);
        if (Flourish.on("StampedMonths")) {
            Flourish.stamp(in, label, cw);
        } else {
            Ui.text(in, label, Misc.getBasePlayerColor(), 0f, 2f, cw, 1, Alignment.LMID);
            Ui.rect(in, 0f, 20f, cw, 1f, Misc.getDarkPlayerColor());
        }
        p.addUIElement(in).inTL(0f, 0f);
        t.addCustom(p, 10f);
    }

    /** One entry: its icon, the date, the sentence (wrapping) and a link to its war. */
    private static void entryRow(CustomPanelAPI body, TooltipMakerAPI t, float cw, Entry e, List<String> walk) {
        float dateX = 28f;
        float dateW = 52f;
        float linkW = Math.min(220f, cw * 0.26f);
        float textX = dateX + dateW + 6f;
        float textW = cw - textX - linkW - 12f;
        LabelAPI probe = t.createLabel(e.text.text(), Misc.getTextColor());
        probe.autoSizeToWidth(Math.max(10f, textW));
        float th = Math.min(probe.getPosition().getHeight(), 3f * Ui.lineHeight(t) + 1f);
        float rowH = Math.max(26f, th + 6f);
        CustomPanelAPI p = body.createCustomPanel(cw, rowH, null);
        TooltipMakerAPI in = p.createUIElement(cw, rowH, false);
        Icons.place(p, in, e.icon, e.color, 2f, (rowH - ICON) / 2f, ICON);
        Ui.text(in, Fmt.shortDate(e.time), Misc.getGrayColor(), dateX, (rowH - 15f) / 2f, dateW, 1, Alignment.LMID);
        Ui.text(in, e.text.text(), Misc.getTextColor(), textX, 3f, textW, 3, Alignment.TL, e.text.highlights(), e.text.colors());
        if (e.ref != null) {
            Ui.flatButton(in, View.Action.of(View.Action.Kind.OPEN, e.ref, walk), cw - linkW, 1f, linkW, rowH - 2f);
            Ui.text(in, e.link, Misc.getBasePlayerColor(), cw - linkW + 4f, (rowH - 15f) / 2f, linkW - 8f, 1, Alignment.RMID);
        } else {
            Ui.text(in, "Sector news", Misc.getGrayColor(), cw - linkW + 4f, (rowH - 15f) / 2f, linkW - 8f, 1, Alignment.RMID);
        }
        Ui.rect(in, 0f, rowH - 1f, cw, 1f, new Color(15, 25, 32));
        p.addUIElement(in).inTL(0f, 0f);
        t.addCustom(p, 0f);
    }

    /**
     * Every Chronicle entry since {@code ws}: the logged events of the wars shown, and the Sector news; for a
     * faction's or alliance's archive, only its wars and its news.
     */
    private static List<Entry> entries(WarData d, long ws, long now, Focus focus, boolean pirates) {
        List<Entry> out = new ArrayList<>();
        List<WarRecord> all = new ArrayList<>(d.active.values());
        all.addAll(d.archive);
        // A faction losing its last colony is logged in each of its wars and in the news; show it once (from the
        // news when that is shown).
        Set<String> gone = new HashSet<>();
        for (LogEntry n : d.news) {
            if (n.kind == LogEntry.Kind.ELIMINATED && n.a != null && focus.covers(n)) gone.add(n.a + "@" + n.time / DAY);
        }
        for (WarRecord r : all) {
            if (!focus.covers(d, r) || !Wars.shown(r, pirates, now)) continue;
            int l = r.left;
            String link = Wars.name(r.id(l)) + " vs " + Wars.name(r.id(1 - l));
            for (StoryEvent e : r.story) {
                if (e.time < ws) continue;
                Color side = e.side >= 0 ? color(Wars.faction(r.id(e.side))) : Misc.getTextColor();
                switch (e.kind) {
                    case BEGAN:
                        if (!r.startKnown) break;
                        out.add(new Entry(e.time, Icons.Kind.BEGAN, Misc.getTextColor(), new Phrase().add("Hostilities began between ")
                                .the(r.id(l), false).add(" and ").the(r.id(1 - l), false).add("."), r.id(), link, 1));
                        break;
                    case ENDED: {
                        int days = Fmt.days(r.start, r.end);
                        String after = r.startKnown ? (days == 1 ? " day." : " days.") : "+ days.";
                        if (r.endReason == WarRecord.END_PEACE) {
                            int how = r.peaceKind();
                            Phrase p = new Phrase();
                            if (how == WarRecord.PEACE_UNKNOWN) {
                                p.add("Hostilities between ").the(r.id(l), false).add(" and ").the(r.id(1 - l), false)
                                        .add(" ceased after ");
                            } else {
                                p.the(r.id(l), true).add(" and ").the(r.id(1 - l), false).add(how == WarRecord.PEACE_TREATY
                                        ? " signed a peace treaty after " : " agreed to a ceasefire after ");
                            }
                            p.num(days).add(after);
                            out.add(new Entry(e.time, Icons.Kind.CEASEFIRE, PEACE, p, r.id(), link, 1));
                        } else {
                            String g = r.endReason == WarRecord.END_GONE_A ? r.a : r.b;
                            Phrase p = new Phrase().add("Hostilities between ").the(r.id(l), false).add(" and ")
                                    .the(r.id(1 - l), false).add(" ended: ").the(g, false).add(" had held no colonies for 30 days.");
                            out.add(new Entry(e.time, Icons.Kind.ELIMINATED, color(Wars.faction(g)), p, r.id(), link, 1));
                        }
                        break;
                    }
                    case CAPTURE:
                        out.add(new Entry(e.time, Icons.Kind.CAPTURE, side, Story.sentence(r, e), r.id(), link, 2));
                        break;
                    case BOMBARD:
                        out.add(new Entry(e.time, Icons.Kind.BOMBARD, side, Story.sentence(r, e), r.id(), link, 2));
                        break;
                    case RAID:
                        out.add(new Entry(e.time, e.n(0) == StoryEvent.RAID_TACTICAL ? Icons.Kind.BOMBARD : Icons.Kind.RAID,
                                side, Story.sentence(r, e), r.id(), link, 2));
                        break;
                    case REPELLED:
                        out.add(new Entry(e.time, Icons.Kind.REPELLED, side, Story.sentence(r, e), r.id(), link, 2));
                        break;
                    case BATTLE:
                        out.add(new Entry(e.time, Icons.Kind.BATTLE, side, Story.sentence(r, e), r.id(), link, 3));
                        break;
                    case SHIFT:
                        out.add(new Entry(e.time, Icons.Kind.ADVANTAGE, e.side >= 0 ? side : Misc.getGrayColor(),
                                Story.sentence(r, e), r.id(), link, 1));
                        break;
                    case GONE: {
                        String who = e.side >= 0 ? r.id(e.side) : null;
                        if (who == null || !gone.add(who + "@" + e.time / DAY)) break;
                        out.add(new Entry(e.time, Icons.Kind.ELIMINATED, side, Story.sentence(r, e), null, null, 4));
                        break;
                    }
                    default:
                        break;
                }
            }
        }
        for (LogEntry n : d.news) {
            if (n.time < ws) continue;
            if (!focus.covers(n)) continue;
            Color ca = n.a == null ? Misc.getTextColor() : color(Wars.faction(n.a));
            Phrase p = sentence(n);
            switch (n.kind) {
                case CAPTURE:
                    out.add(new Entry(n.time, Icons.Kind.CAPTURE, ca, p, null, null, 2));
                    break;
                case ELIMINATED:
                    out.add(new Entry(n.time, Icons.Kind.ELIMINATED, ca, p, null, null, 4));
                    break;
                case ARRIVED:
                    out.add(new Entry(n.time, Icons.Kind.ARRIVED, ca, p, null, null, 4));
                    break;
                case WAR:
                    out.add(new Entry(n.time, Icons.Kind.BEGAN, Misc.getTextColor(), p, null, null, 1));
                    break;
                case PEACE:
                    out.add(new Entry(n.time, Icons.Kind.CEASEFIRE, PEACE, p, null, null, 1));
                    break;
                case BATTLE:
                    out.add(new Entry(n.time, Icons.Kind.BATTLE, ca, p, null, null, 3));
                    break;
                default:
                    if (n.kind.isAlliance()) out.add(new Entry(n.time, Icons.Kind.ALLIANCE, Misc.getTextColor(), p, null, null, 4));
                    break;
            }
        }
        return out;
    }

    /**
     * The newest entries of the whole Chronicle within the last cycle, at most {@code max}, newest first:
     * {"2 Oct   The Hegemony captured ...", the war it belongs to or null}. Named and shown once, as in the Chronicle.
     */
    static List<Object[]> latest(WarData d, long now, boolean pirates, int max) {
        List<Entry> es = entries(d, now - CYCLE_DAYS * DAY, now, new Focus(d, null, null, null), pirates);
        es.sort(Comparator.comparingLong((Entry e) -> -e.time));
        List<Object[]> out = new ArrayList<>();
        for (int i = 0; i < es.size() && i < max; i++) {
            Entry e = es.get(i);
            out.add(new Object[]{Story.append(new Phrase().hl(Fmt.shortDate(e.time), Misc.getGrayColor()).add("   "), e.text), e.ref});
        }
        return out;
    }

    /** The Chronicle's entries logged after {@code from}, newest first, each as "2 Nov c206  The Hegemony captured ...". */
    static List<Phrase> since(WarData d, long from, long now, boolean pirates) {
        List<Entry> es = entries(d, from, now, new Focus(d, null, null, null), pirates);
        es.removeIf(e -> e.time <= from);
        es.sort(Comparator.comparingLong((Entry e) -> -e.time));
        List<Phrase> out = new ArrayList<>();
        for (Entry e : es) out.add(Story.append(new Phrase().hl(Fmt.date(e.time), Misc.getGrayColor()).add("   "), e.text));
        return out;
    }

    /** A Sector news entry as a sentence. */
    static Phrase sentence(LogEntry e) {
        Phrase p = new Phrase();
        Color h = Misc.getHighlightColor();
        switch (e.kind) {
            case CAPTURE:
                return p.the(e.a, true).add(" captured ").hl(e.text == null ? "a colony" : e.text, h).add(" (size ")
                        .num(e.v(0)).add(") from ").the(e.b, false).add(".");
            case ALLIANCE_FORMED:
                p.the(e.a, true);
                if (e.b != null) p.add(" and ").the(e.b, false);
                return p.add(" formed ").hl(alliance(e.text), h).add(".");
            case ALLIANCE_JOINED:
                return p.the(e.a, true).add(" joined ").hl(alliance(e.text), h).add(".");
            case ALLIANCE_LEFT:
                return p.the(e.a, true).add(" left ").hl(alliance(e.text), h).add(".");
            case ALLIANCE_MERGED:
                return p.hl(alliance(e.text2), h).add(" merged into ").hl(alliance(e.text), h).add(".");
            case ALLIANCE_DISSOLVED:
                return p.hl(alliance(e.text), h).add(" dissolved.");
            case ELIMINATED:
                return p.the(e.a, true).add(Fmt.plural(Wars.faction(e.a)) ? " lost their last colony." : " lost its last colony.");
            case ARRIVED:
                return p.the(e.a, true).add(Fmt.plural(Wars.faction(e.a)) ? " now hold colonies." : " now holds colonies.");
            case WAR:
                return p.add("Hostilities began between ").the(e.a, false).add(" and ").the(e.b, false).add(".");
            case PEACE:
                return p.add("Hostilities between ").the(e.a, false).add(" and ").the(e.b, false).add(" ceased.");
            case BATTLE:
                return p.the(e.a, true).add(" defeated ").the(e.b, false).add(" in a major battle.");
            default:
                return p;
        }
    }

    private static String alliance(String name) {
        return name == null || name.isEmpty() ? "an alliance" : name;
    }
}
