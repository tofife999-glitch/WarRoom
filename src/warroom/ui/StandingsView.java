package warroom.ui;

import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.ui.Alignment;
import com.fs.starfarer.api.ui.ButtonAPI;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.ui.UIComponentAPI;
import com.fs.starfarer.api.util.Misc;
import exerelin.campaign.alliances.Alliance;
import warroom.Fmt;
import warroom.SectorSnapshot;
import warroom.data.Ledger;
import warroom.data.WarData;
import warroom.data.WarRecord;
import warroom.track.Alliances;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * The Standings tab, for factions or for alliances. Factions: every faction holding colonies, by total colony
 * size, with how that size has changed since records began, the colony size it took and lost in the wars on
 * record (old ones the Archive no longer shows included), and its wars under way; factions that have lost all
 * their colonies follow, in gray. Alliances: the same for each alliance's members together, its fronts under
 * way instead of wars. A row opens the faction's or alliance's archive; a heading sorts by its column (most
 * first, then least first, names A to Z then Z to A, then the usual order).
 */
final class StandingsView {
    private static final float ROW_H = 26f;
    private static final Color LINE = new Color(15, 25, 32);
    private static final Color GAIN = new Color(110, 200, 120);
    private static final Color LOSS = new Color(225, 110, 95);

    private StandingsView() {
    }

    /** One line: a faction or an alliance. */
    private static final class Line {
        String id;
        String name;
        Color color;
        List<String> members;
        int colonies;
        int size;
        /** Change in total colony size since its baseline; null if there is none. */
        Integer change;
        /** For a faction, its baseline {size, when, kind}. */
        long[] base;
        int taken;
        int lost;
        int wars;

        boolean holds() {
            return colonies > 0;
        }
    }

    static void build(CustomPanelAPI body, float w, float h, SectorSnapshot s, long now) {
        TooltipMakerAPI t = body.createUIElement(w, h, true);
        float cw = w - 20f; // room for the scrollbar
        WarData d = WarData.get();
        boolean alliances = View.standingsAlliances;

        t.addSectionHeading("Sector standings", Alignment.MID, 0f);
        viewSwitch(body, t, cw, alliances);
        Phrase intro = new Phrase().add(alliances ? "Every alliance, its members' colonies together, by total colony size"
                : "Every faction holding colonies, by total colony size");
        if (d.since != 0) intro.add(". Records began ").hl(Fmt.date(d.since), Misc.getHighlightColor());
        Tips.para(t, intro.add("."), 6f).setColor(Misc.getGrayColor());

        List<Line> lines = alliances ? allianceLines(d, s) : factionLines(d, s);
        if (lines.isEmpty()) {
            t.addPara(alliances ? "There are no alliances in the Sector." : "No faction holds colonies.", Misc.getGrayColor(), 10f);
        } else {
            float[] col = alliances ? new float[]{0f, cw * 0.30f, cw * 0.40f, cw * 0.55f, cw * 0.70f, cw * 0.86f, cw}
                    : new float[]{0f, cw * 0.36f, cw * 0.52f, cw * 0.70f, cw * 0.86f, cw};
            headerRow(body, t, cw, col, alliances);
            int rank = 0;
            for (Line l : lines) row(body, t, cw, col, l, s, d, alliances, l.holds() ? ++rank : 0);
            if (Flourish.on("SectorTotal")) totalRow(body, t, cw, col, s, d, alliances);
        }
        Tips.para(t, new Phrase().add("Colony size throughout. Taken and lost count the colonies captured in the wars on "
                + "record, old ones the Archive no longer shows included. Click a" + (alliances ? "n alliance for its Alliance"
                : " faction for its Faction") + " Archive."), 12f).setColor(Misc.getGrayColor());
        Flourish.signOff(body, t, cw);
        t.addSpacer(20f);
        body.addUIElement(t).inTL(0f, 0f);
        View.restoreScroll(t.getExternalScroller());
    }

    /** Factions or alliances. */
    private static void viewSwitch(CustomPanelAPI body, TooltipMakerAPI t, float cw, boolean alliances) {
        CustomPanelAPI p = body.createCustomPanel(cw, 28f, null);
        TooltipMakerAPI in = p.createUIElement(cw, 28f, false);
        String[] labels = {"Factions", "Alliances"};
        for (int i = 0; i < 2; i++) {
            ButtonAPI b = in.addAreaCheckbox(labels[i], View.Action.of(View.Action.Kind.STANDINGS_VIEW, null, i == 1),
                    Misc.getBasePlayerColor(), Misc.getDarkPlayerColor(), Misc.getBrightPlayerColor(), 110f, 24f, 0f);
            b.setChecked(alliances == (i == 1));
            b.getPosition().inTL(i * 116f, 2f);
        }
        p.addUIElement(in).inTL(0f, 0f);
        t.addCustom(p, 8f);
    }

    /** The factions holding colonies, then those that held some since records began. */
    private static List<Line> factionLines(WarData d, SectorSnapshot s) {
        Set<String> ids = new LinkedHashSet<>();
        for (FactionAPI f : s.factions) ids.add(f.getId());
        ids.addAll(d.baseline.keySet());
        List<WarRecord> all = new ArrayList<>(d.active.values());
        all.addAll(d.archive);
        List<Line> out = new ArrayList<>();
        for (String id : ids) {
            FactionAPI f = Wars.faction(id);
            if (f == null) continue; // a faction no longer in the game
            int i = s.indexOf(id);
            Line l = new Line();
            l.id = id;
            l.name = Wars.name(id);
            l.color = Ui.readable(f);
            l.colonies = i >= 0 ? s.colonies[i] : 0;
            l.size = i >= 0 ? s.size[i] : 0;
            l.base = d.baseline.get(id);
            l.change = l.base == null ? null : l.size - (int) l.base[0];
            Ledger led = new Ledger();
            for (WarRecord r : all) {
                if (!r.involves(id)) continue;
                led.add(r, r.side(id));
                if (!r.ended()) l.wars++;
            }
            led.add(d.retired.get(id));
            l.taken = led.us(WarRecord.CAPTURED_SIZE);
            l.lost = led.them(WarRecord.CAPTURED_SIZE);
            out.add(l);
        }
        sort(out, View.standingsSort, View.standingsDown, false);
        return out;
    }

    /** Every alliance there is now, its members counted together. */
    private static List<Line> allianceLines(WarData d, SectorSnapshot s) {
        List<WarRecord> all = new ArrayList<>(d.active.values());
        all.addAll(d.archive);
        List<Line> out = new ArrayList<>();
        for (Alliance a : s.alliances) {
            if (a == null || a.uuId == null) continue;
            String id = d.canonAlliance(a.uuId);
            if (id == null) id = a.uuId;
            Line l = new Line();
            l.id = id;
            l.name = Alliances.name(a.uuId, a.getName());
            l.members = Alliances.members(a.uuId);
            int biggest = -1;
            for (String m : l.members) {
                int i = s.indexOf(m);
                if (i < 0) continue;
                l.colonies += s.colonies[i];
                l.size += s.size[i];
                if (biggest < 0 || s.size[i] > s.size[biggest]) biggest = i;
                long[] b = d.baseline.get(m);
                if (b != null) l.change = (l.change == null ? 0 : l.change) + s.size[i] - (int) b[0];
            }
            FactionAPI lead = biggest < 0 ? null : s.get(biggest);
            l.color = lead == null ? Misc.getBasePlayerColor() : Ui.readable(lead);
            Ledger led = new Ledger();
            for (WarRecord r : all) {
                int k = -1;
                for (int side = 0; side < 2 && k < 0; side++) {
                    if (id.equals(d.canonAlliance(r.allianceId(side)))) k = side;
                }
                if (k < 0) continue;
                led.add(r, k);
                if (!r.ended()) l.wars++;
            }
            led.add(d.retiredOfAlliance(id));
            l.taken = led.us(WarRecord.CAPTURED_SIZE);
            l.lost = led.them(WarRecord.CAPTURED_SIZE);
            out.add(l);
        }
        sort(out, View.allianceSort, View.allianceDown, true);
        return out;
    }

    /** By the chosen column; holders always before the rest, ties by name. */
    private static void sort(List<Line> lines, int key, boolean down, boolean alliances) {
        Comparator<Line> byName = Comparator.comparing((Line l) -> l.name.toLowerCase());
        // The alliance view has a members column after the name; the rest are the same.
        int k = alliances ? key : key <= 0 ? key : key + 1;
        Comparator<Line> by;
        switch (k) {
            case 0:
                by = byName;
                break;
            case 1:
                by = Comparator.comparingInt((Line l) -> l.members == null ? 0 : l.members.size());
                break;
            case 2:
                by = Comparator.comparingInt((Line l) -> l.colonies).thenComparingInt(l -> l.size);
                break;
            case 3:
                by = Comparator.comparingInt((Line l) -> l.change == null ? 0 : l.change);
                break;
            case 4:
                by = Comparator.comparingInt((Line l) -> l.taken).thenComparingInt(l -> -l.lost);
                break;
            case 5:
                by = Comparator.comparingInt((Line l) -> l.wars);
                break;
            default:
                by = Comparator.comparingInt((Line l) -> l.size);
                break;
        }
        // Numbers count down from the most unless "least first"; names run A to Z unless reversed.
        boolean reverse = k == 0 ? down : !down;
        if (reverse) by = by.reversed();
        lines.sort(Comparator.comparing((Line l) -> !l.holds()).thenComparing(by).thenComparing(byName));
    }

    private static void headerRow(CustomPanelAPI body, TooltipMakerAPI t, float cw, float[] col, boolean alliances) {
        CustomPanelAPI p = body.createCustomPanel(cw, 22f, null);
        TooltipMakerAPI in = p.createUIElement(cw, 22f, false);
        Color g = Misc.getGrayColor();
        String[] names = alliances ? new String[]{"Alliance", "Members", "Colonies", "Since records began", "Taken / lost in war",
                "Fronts under way"} : new String[]{"Faction", "Colonies", "Since records began", "Taken / lost in war", "Wars under way"};
        String[] tips = alliances ? new String[]{null, "Its member factions now.", "Its members' colonies now, and their total size.",
                "How its members' total colony size has changed since records began (each member since its own first figure).",
                "Colony size its members captured from their enemies, and lost to them, while in the alliance.",
                "Its members' wars going on now, one per member and enemy."}
                : new String[]{null, "Colonies held now, and their total size.",
                "How the faction's total colony size has changed since records began, or since it first held colonies after that.",
                "Colony size it captured from its enemies, and lost to them, in the wars on record.",
                "Its wars going on now, one per enemy."};
        String target = alliances ? "alliances" : "standings";
        int sort = alliances ? View.allianceSort : View.standingsSort;
        boolean down = alliances ? View.allianceDown : View.standingsDown;
        String sortHint = " Click to sort: most first, then least first, then the usual order.";
        for (int i = 0; i < names.length; i++) {
            float x = col[i] + (i == 0 ? 6f : 0f);
            float cwid = col[i + 1] - col[i] - 8f;
            boolean on = sort == i;
            ButtonAPI b = Ui.flatButton(in, View.Action.of(View.Action.Kind.SORT, target, i), col[i], 0f, col[i + 1] - col[i] - 2f, 21f);
            String tip = i == 0 ? "Click to sort by name: A to Z, then Z to A, then the usual order." : tips[i] + sortHint;
            Ui.tooltip(in, b, Tips.text(tip));
            Color c = on ? Misc.getHighlightColor() : g;
            Ui.text(in, names[i], c, x, 2f, cwid, 1, i == 0 ? Alignment.LMID : Alignment.RMID);
            if (on) {
                float tw = in.computeStringWidth(names[i]);
                float px = i == 0 ? x + tw + 6f : col[i + 1] - 8f - tw - 15f;
                Ui.pyramid(in, px, 8f, !down, c);
            }
        }
        Ui.rect(in, 0f, 21f, cw, 1f, Misc.getDarkPlayerColor());
        p.addUIElement(in).inTL(0f, 0f);
        t.addCustom(p, 10f);
    }

    private static void row(CustomPanelAPI body, TooltipMakerAPI t, float cw, float[] col, Line l, SectorSnapshot s,
                            WarData d, boolean alliances, int rank) {
        CustomPanelAPI p = body.createCustomPanel(cw, ROW_H, null);
        TooltipMakerAPI in = p.createUIElement(cw, ROW_H, false);
        boolean gone = !l.holds();
        Color tc = gone ? Misc.getGrayColor() : Misc.getTextColor();
        ButtonAPI b;
        if (alliances) {
            b = Ui.flatButton(in, View.Action.of(View.Action.Kind.ALLIANCE_ARCHIVE, l.id, l.name), 0f, 1f, cw, ROW_H - 2f);
            Ui.tooltip(in, b, Tips.alliance(l.id, l.name, l.members, true));
        } else {
            b = Ui.flatButton(in, View.Action.of(View.Action.Kind.FACTION_ARCHIVE, l.id), 0f, 1f, cw, ROW_H - 2f);
            Ui.tooltip(in, b, Tips.faction(l.id, s, true));
        }
        float x = 6f;
        if (Flourish.on("Ranks")) {
            // The row's place in the order shown; factions without colonies have none.
            if (rank > 0) Ui.text(in, String.format("%02d", rank), Misc.getGrayColor(), x, 4f, 22f, 1, Alignment.LMID);
            x += 26f;
        }
        Color nc = gone ? Misc.getGrayColor() : l.color;
        float nameEnd = col[1] - 8f;
        if (!alliances) {
            FactionAPI f = Wars.faction(l.id);
            float fw = Ui.flag(in, f, x, (ROW_H - 14f) / 2f, 14f);
            x += fw > 0f ? fw + 8f : 0f;
            Ui.text(in, l.name, nc, x, 4f, nameEnd - x, 1, Alignment.LMID);
            if (gone && Flourish.on("Struck")) {
                // Struck from the record: a line through the name of a faction that holds no colonies.
                float nw = Math.min(nameEnd - x, in.computeStringWidth(l.name));
                Ui.rect(in, x, ROW_H / 2f - 1f, nw, 1f, Flourish.STRUCK);
            }
        } else {
            float nw = Math.min(nameEnd - x, in.computeStringWidth(l.name) + 4f);
            Ui.text(in, l.name, nc, x, 4f, nw, 1, Alignment.LMID);
            // The members' crests after the name, as many as fit.
            float cx = x + nw + 8f;
            for (String m : l.members) {
                FactionAPI f = Wars.faction(m);
                if (f == null || cx + 16f > nameEnd) continue;
                Ui.crest(in, f, cx, (ROW_H - 16f) / 2f, 16f);
                cx += 18f;
            }
        }
        float y = 4f;
        int c0 = 1;
        if (alliances) {
            cell(in, Fmt.num(l.members.size()), tc, col, 1, y);
            c0 = 2;
        }
        cell(in, gone ? "none" : l.colonies + " (size " + Fmt.num(l.size) + ")", tc, col, c0, y);
        if (l.change != null) {
            int change = l.change;
            String txt = change > 0 ? "+" + Fmt.num(change) : change < 0 ? "-" + Fmt.num(-change) : "0";
            Color cc = change > 0 ? GAIN : change < 0 ? LOSS : Misc.getGrayColor();
            UIComponentAPI c = cell(in, txt, gone ? Misc.getGrayColor() : cc, col, c0 + 1, y);
            if (l.base != null) {
                int kind = l.base.length > 2 ? (int) l.base[2] : WarData.BASE_START;
                String when = kind == WarData.BASE_LATER ? ", when it first held colonies on record."
                        : kind == WarData.BASE_NOTED ? ", when War Room first noted colony sizes in this save." : ", when records began.";
                Ui.tooltip(in, c, Tips.text("Total colony size " + Fmt.num(l.size) + " now; " + Fmt.num((int) l.base[0])
                        + " on " + Fmt.date(l.base[1]) + when));
            }
        } else {
            cell(in, Fmt.NONE, Misc.getGrayColor(), col, c0 + 1, y);
        }
        cell(in, Fmt.num(l.taken) + " / " + Fmt.num(l.lost), tc, col, c0 + 2, y);
        cell(in, Fmt.num(l.wars), l.wars > 0 && !gone ? Misc.getHighlightColor() : tc, col, c0 + 3, y);
        Ui.rect(in, 0f, ROW_H - 1f, cw, 1f, LINE);
        p.addUIElement(in).inTL(0f, 0f);
        t.addCustom(p, 0f);
    }

    /** The whole Sector: every faction holding colonies, their change since records began, and every war. */
    private static void totalRow(CustomPanelAPI body, TooltipMakerAPI t, float cw, float[] col, SectorSnapshot s, WarData d,
                                 boolean alliances) {
        int colonies = 0;
        int size = 0;
        int change = 0;
        boolean anyBase = false;
        for (int i = 0; i < s.size(); i++) {
            colonies += s.colonies[i];
            size += s.size[i];
            long[] b = d.baseline.get(s.get(i).getId());
            if (b != null) {
                change += s.size[i] - (int) b[0];
                anyBase = true;
            }
        }
        // Factions gone since records began count with their whole baseline lost.
        for (java.util.Map.Entry<String, long[]> e : d.baseline.entrySet()) {
            if (s.indexOf(e.getKey()) < 0) {
                change -= (int) e.getValue()[0];
                anyBase = true;
            }
        }
        int taken = 0;
        List<WarRecord> all = new ArrayList<>(d.active.values());
        all.addAll(d.archive);
        for (WarRecord r : all) taken += r.get(WarRecord.CAPTURED_SIZE, 0) + r.get(WarRecord.CAPTURED_SIZE, 1);
        // A dropped war is in both its factions' ledgers, each side's captures once as "us".
        for (Ledger l : d.retired.values()) taken += l.us(WarRecord.CAPTURED_SIZE);
        CustomPanelAPI p = body.createCustomPanel(cw, ROW_H, null);
        TooltipMakerAPI in = p.createUIElement(cw, ROW_H, false);
        Color h = Misc.getHighlightColor();
        Ui.rect(in, 0f, 0f, cw, 1f, Misc.getDarkPlayerColor());
        UIComponentAPI name = Ui.text(in, "Sector", h, Flourish.on("Ranks") ? 32f : 6f, 5f, col[1] - 40f, 1, Alignment.LMID).comp;
        Ui.tooltip(in, name, Tips.text("The whole Sector: every faction holding colonies, its colony size changed hands in "
                + "the wars on record, and every war going on between two factions."));
        int c0 = 1;
        if (alliances) {
            cell(in, Fmt.num(s.size()), h, col, 1, 5f);
            c0 = 2;
        }
        cell(in, colonies + " (size " + Fmt.num(size) + ")", h, col, c0, 5f);
        cell(in, !anyBase ? Fmt.NONE : change > 0 ? "+" + Fmt.num(change) : change < 0 ? "-" + Fmt.num(-change) : "0", h, col,
                c0 + 1, 5f);
        cell(in, Fmt.num(taken), h, col, c0 + 2, 5f);
        cell(in, Fmt.num(d.active.size()), h, col, c0 + 3, 5f);
        p.addUIElement(in).inTL(0f, 0f);
        t.addCustom(p, 2f);
    }

    private static UIComponentAPI cell(TooltipMakerAPI in, String text, Color c, float[] col, int i, float y) {
        return Ui.text(in, text, c, col[i], y, col[i + 1] - col[i] - 8f, 1, Alignment.RMID).comp;
    }
}
