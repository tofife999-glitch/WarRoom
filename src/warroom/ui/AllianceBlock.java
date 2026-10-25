package warroom.ui;

import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.ui.Alignment;
import com.fs.starfarer.api.ui.ButtonAPI;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.ui.UIComponentAPI;
import com.fs.starfarer.api.util.Misc;
import warroom.Fmt;
import warroom.Score;
import warroom.SectorSnapshot;
import warroom.data.WarRecord;
import warroom.track.Alliances;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

/**
 * An alliance war on the Hostilities tab: a summary of the whole war (both alliances, "Advantage" from the
 * fronts' summed war scores, "Strength now", and totals counting each battle once), then its busiest
 * fronts as cards, the rest behind a button.
 */
final class AllianceBlock {
    static final float SUMMARY_H = 232f;
    static final float GAP = 10f;
    static final int FRONTS_SHOWN = 2;
    private static final float PAD = 10f;

    private AllianceBlock() {
    }

    /** Each alliance's colour: its largest member's; the right one switches if both come out alike. */
    static Color[] colors(Wars.AllianceWar g, SectorSnapshot s) {
        List<String> l = g.members(0, s);
        List<String> r = g.members(1, s);
        Color cl = color(l, 0);
        Color cr = color(r, 0);
        if (alike(cl, cr) && r.size() > 1) cr = color(r, 1);
        return new Color[]{cl, cr};
    }

    private static Color color(List<String> members, int i) {
        FactionAPI f = i < members.size() ? Wars.faction(members.get(i)) : null;
        return f == null ? Misc.getGrayColor() : Ui.readable(f);
    }

    private static boolean alike(Color a, Color b) {
        int d = Math.abs(a.getRed() - b.getRed()) + Math.abs(a.getGreen() - b.getGreen()) + Math.abs(a.getBlue() - b.getBlue());
        return d < 90;
    }

    /** {left, right} sums over the alliances' members now: total colony size and Nexerelin reserves. */
    static float[][] strength(Wars.AllianceWar g, SectorSnapshot s) {
        float[] size = new float[2];
        float[] reserves = new float[2];
        SectorSnapshot.Strength[] st = s.strength();
        for (int k = 0; k < 2; k++) {
            List<String> ids = new ArrayList<>(Alliances.members(g.allianceId[k]));
            // Plus whoever still fights on this side from outside the alliance; while the war goes on, not
            // those whose fronts have all ended (they may have left).
            boolean over = g.ended();
            for (WarRecord r : g.fronts) {
                if (r.ended() && !over) continue;
                int l = g.leftSide(r);
                String m = r.id(k == 0 ? l : 1 - l);
                if (!ids.contains(m)) ids.add(m);
            }
            for (String id : ids) {
                int i = s.indexOf(id);
                if (i < 0) continue;
                size[k] += s.size[i];
                if (!Float.isNaN(st[i].fleetPool)) reserves[k] += st[i].fleetPool;
                if (!Float.isNaN(st[i].troops)) reserves[k] += st[i].troops;
            }
            // A side in debt has no reserves; its members' debts still net against each other above.
            reserves[k] = Math.max(0f, reserves[k]);
        }
        return new float[][]{size, reserves};
    }

    /** Left's share of strength: half colony size, half reserves (reserves left out if nobody has any). */
    static float strengthShare(float[][] st) {
        float sizeSum = st[0][0] + st[0][1];
        float resSum = st[1][0] + st[1][1];
        float sizeShare = sizeSum > 0f ? st[0][0] / sizeSum : Float.NaN;
        float resShare = resSum > 0f ? st[1][0] / resSum : Float.NaN;
        if (Float.isNaN(sizeShare)) return resShare;
        if (Float.isNaN(resShare)) return sizeShare;
        return (sizeShare + resShare) / 2f;
    }

    static float height(int fronts, boolean expanded) {
        int shown = expanded ? fronts : Math.min(fronts, FRONTS_SHOWN);
        int rows = (shown + 1) / 2;
        float h = SUMMARY_H + rows * (Card.HEIGHT + GAP);
        if (fronts > FRONTS_SHOWN) h += 34f;
        return h;
    }

    static CustomPanelAPI build(CustomPanelAPI parent, float w, Wars.AllianceWar g, SectorSnapshot s, long now,
                                boolean expanded, String lastOpened) {
        float h = height(g.fronts.size(), expanded);
        CustomPanelAPI block = parent.createCustomPanel(w, h, null);
        TooltipMakerAPI in = block.createUIElement(w, SUMMARY_H, false);
        // The summary covers the whole war, ended fronts included, as on its page; the cards only the fronts going on.
        Wars.AllianceWar whole = WarPage.findGroup(g.key);
        summary(in, w, whole != null ? whole : g, s, now, false);
        block.addUIElement(in).inTL(0f, 0f);

        int shown = expanded ? g.fronts.size() : Math.min(g.fronts.size(), FRONTS_SHOWN);
        float cw = (w - GAP) / 2f;
        List<String> walk = new ArrayList<>();
        for (WarRecord r : g.fronts) walk.add(r.id());
        for (int k = 0; k < shown; k++) {
            WarRecord r = g.fronts.get(k);
            String marker = r.id().equals(lastOpened) ? "Last viewed" : (Wars.mine(r) ? "Your war" : "Front");
            CustomPanelAPI card = Card.build(block, cw, r, g.leftSide(r), s, now, false, marker,
                    View.Action.of(View.Action.Kind.OPEN, r.id(), walk));
            block.addComponent(card).inTL((k % 2) * (cw + GAP), SUMMARY_H + (k / 2) * (Card.HEIGHT + GAP));
        }
        if (g.fronts.size() > FRONTS_SHOWN) {
            TooltipMakerAPI more = block.createUIElement(w, 30f, false);
            int rest = g.fronts.size() - FRONTS_SHOWN;
            String label = expanded ? "Show only the most active fronts" : "Show " + rest + " more " + (rest == 1 ? "front" : "fronts");
            Ui.button(more, label, View.Action.of(View.Action.Kind.MORE_FRONTS, g.key), w - 260f, 2f, 260f, 24f);
            int rows = (shown + 1) / 2;
            block.addUIElement(more).inTL(0f, SUMMARY_H + rows * (Card.HEIGHT + GAP));
        }
        return block;
    }

    /**
     * The summary of an alliance war, SUMMARY_H tall. On the war's own page ({@code onPage}) there is no
     * Details button.
     */
    static void summary(TooltipMakerAPI in, float w, Wars.AllianceWar g, SectorSnapshot s, long now, boolean onPage) {
        Color[] c = colors(g, s);
        Ui.frame(in, 0f, 0f, w, SUMMARY_H, Misc.getBasePlayerColor().darker());
        float y = PAD;
        Phrase title = new Phrase().add("Alliance war: ").hl(g.name[0], c[0]).add(" vs ").hl(g.name[1], c[1]);
        Ui.text(in, title.text(), Misc.getTextColor(), PAD, y, w * 0.55f, 1, Alignment.LMID, title.highlights(), title.colors());
        long start = g.start();
        boolean over = g.ended();
        long until = over ? g.end() : now;
        int days = Fmt.days(start, until);
        int ended = g.endedFronts();
        int going = g.fronts.size() - ended;
        String fronts = over ? Fmt.plural(g.fronts.size(), "front", "fronts")
                : Fmt.plural(going, "active front", "active fronts") + (ended > 0 ? ", " + ended + " ended" : "");
        String info = fronts + " - " + (g.startUnknown() ? days + "+ days" : Fmt.plural(days, "day", "days"))
                + (over ? ", ended " + Fmt.date(until) : ", since " + Fmt.date(start));
        Ui.text(in, info, Misc.getGrayColor(), w * 0.55f + PAD, y, w * 0.45f - 2f * PAD, 1, Alignment.RMID);

        // Members: crests and names of each side.
        y += 26f;
        for (int k = 0; k < 2; k++) {
            List<String> members = g.members(k, s);
            float x = k == 0 ? PAD : w / 2f + PAD;
            float colW = w / 2f - 2f * PAD;
            // The alliance's name opens its archive, each member's crest that member's (flat buttons under them).
            float nw = Math.min(colW, in.computeStringWidth(g.name[k]) + 12f);
            ButtonAPI ab = Ui.flatButton(in, View.Action.of(View.Action.Kind.ALLIANCE_ARCHIVE, g.allianceId[k], g.name[k]),
                    k == 0 ? x - 4f : x + colW + 4f - nw, y - 3f, nw, 20f);
            Ui.tooltip(in, ab, Tips.alliance(g.allianceId[k], g.name[k], members, true));
            Ui.text(in, g.name[k], c[k], x, y, colW, 1, k == 0 ? Alignment.LMID : Alignment.RMID);
            float cx = k == 0 ? x : x + colW - members.size() * 24f;
            for (String id : members) {
                FactionAPI f = Wars.faction(id);
                if (f == null) continue;
                ButtonAPI cb = Ui.flatButton(in, View.Action.of(View.Action.Kind.FACTION_ARCHIVE, id), cx - 2f, y + 18f, 24f, 24f);
                Ui.tooltip(in, cb, Tips.faction(id, s, true));
                Ui.crest(in, f, cx, y + 20f, 20f);
                cx += 24f;
            }
            Phrase names = new Phrase();
            for (int i = 0; i < members.size(); i++) {
                if (i > 0) names.add(", ");
                names.faction(members.get(i));
            }
            Ui.text(in, names.text(), Misc.getTextColor(), x, y + 44f, colW, 1, k == 0 ? Alignment.LMID : Alignment.RMID,
                    names.highlights(), names.colors());
        }

        // War so far and strength now.
        y += 72f;
        float labelW = 110f;
        float share = Score.share(g.points(0), g.points(1));
        Phrase v = Wars.verdict(share, g.name[0], g.name[1], c[0], c[1], g.ended());
        float[][] st = strength(g, s);
        float ss = strengthShare(st);
        Phrase sv = strengthVerdict(ss, g.name[0], g.name[1], c[0], c[1]);
        // The verdict column is as wide as its longer line needs (alliance names run long), within limits.
        float need = Math.max(in.computeStringWidth(v.text()), in.computeStringWidth(sv.text())) + 8f;
        float verdictW = Math.max(170f, Math.min(need, w * 0.4f));
        float barW = w - 2f * PAD - labelW - verdictW - 20f;
        Ui.text(in, "Advantage", Misc.getGrayColor(), PAD, y - 2f, labelW, 1, Alignment.LMID);
        UIComponentAPI wb = Card.bar(in, PAD + labelW, y, barW, share, c[0], c[1]);
        Ui.tooltip(in, wb, Tips.text("War score of all fronts combined. Open a front for its breakdown."));
        Ui.text(in, v.text(), Misc.getTextColor(), PAD + labelW + barW + 20f, y - 2f, verdictW, 1, Alignment.LMID,
                v.highlights(), v.colors());

        // Who has gained ground lately over all fronts, under the bar as on a war card.
        y += 16f;
        int gl = g.gaining(now);
        Phrase mom = over ? new Phrase().add("Final assessment") : Wars.momentum(gl, g.name[0], g.name[1], c[0], c[1]);
        Ui.text(in, mom.text(), Misc.getGrayColor(), PAD + labelW, y, barW, 1, Alignment.MID, mom.highlights(), mom.colors());
        if (!over && gl >= 0) Flourish.momentumArrow(in, mom.text(), PAD + labelW, y, barW, gl == 1, c[gl]);

        y += 24f;
        Ui.text(in, "Strength", Misc.getGrayColor(), PAD, y - 2f, labelW, 1, Alignment.LMID);
        UIComponentAPI sb = Card.bar(in, PAD + labelW, y, barW, ss, c[0], c[1]);
        Ui.tooltip(in, sb, Tips.strength(g.name[0], g.name[1], st[0], st[1]));
        Ui.text(in, sv.text(), Misc.getTextColor(), PAD + labelW + barW + 20f, y - 2f, verdictW, 1, Alignment.LMID,
                sv.highlights(), sv.colors());

        // Totals over all fronts, each battle counted once.
        y += 30f;
        float mid = w / 2f;
        float lw = 200f;
        String[] labels = {"Battles won, all fronts", "Colonies captured", "Ships lost"};
        int[][] vals = {
                {g.sum(Wars.FIGHTS, 0), g.sum(Wars.FIGHTS, 1)},
                {g.sum(WarRecord.CAPTURED, 0), g.sum(WarRecord.CAPTURED, 1)},
                {g.sum(Wars.SHIPS, 0), g.sum(Wars.SHIPS, 1)}};
        for (int k = 0; k < 3; k++) {
            String a = (k == 2 && g.estimated(0) ? "~" : "") + Fmt.num(vals[k][0]);
            String b = (k == 2 && g.estimated(1) ? "~" : "") + Fmt.num(vals[k][1]);
            Color h = Misc.getHighlightColor();
            boolean bright = k < 2;
            Ui.Placed pa = Ui.text(in, a, bright && vals[k][0] > vals[k][1] ? h : Misc.getTextColor(), PAD, y,
                    mid - lw / 2f - PAD - 6f, 1, Alignment.RMID);
            Ui.Placed pl = Ui.text(in, labels[k], Misc.getGrayColor(), mid - lw / 2f, y, lw, 1, Alignment.MID);
            Ui.Placed pb = Ui.text(in, b, bright && vals[k][1] > vals[k][0] ? h : Misc.getTextColor(), mid + lw / 2f + 6f, y,
                    mid - lw / 2f - PAD - 6f, 1, Alignment.LMID);
            if (k == 1) {
                // The colonies taken on every front, by whom and when.
                Tips.Tip tip = Tips.captures(g);
                for (Ui.Placed pp : new Ui.Placed[]{pa, pl, pb}) Ui.tooltip(in, pp.comp, tip);
            }
            y += 18f;
        }
        if (!onPage) {
            Ui.button(in, "Full report", View.Action.of(View.Action.Kind.OPEN, "G:" + g.key), w - PAD - 110f,
                    SUMMARY_H - PAD - 24f, 110f, 24f);
        }
    }

    static Phrase strengthVerdict(float share, String ln, String rn, Color cl, Color cr) {
        Phrase p = new Phrase();
        if (Float.isNaN(share)) return p.add("No estimate");
        float lead = Math.max(share, 1f - share);
        if (lead < Score.AHEAD) return p.add("Evenly matched");
        boolean left = share >= 0.5f;
        p.hl(left ? ln : rn, left ? cl : cr);
        return p.add(lead >= Score.FAR ? " far superior" : " superior");
    }
}
