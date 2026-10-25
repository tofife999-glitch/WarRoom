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
import warroom.data.StoryEvent;
import warroom.data.WarRecord;

import java.awt.Color;

/**
 * One war's card: both flags and names facing each other with the war's age between them, the tug bar with
 * its verdict and the momentum line, three mirrored numbers, the latest event and a Details button. Fixed
 * height; long names wrap to two lines, and only a third line is shortened.
 */
final class Card {
    static final float HEIGHT = 236f;
    private static final float PAD = 10f;
    private static final float FLAG_H = 22f;
    private static final float AGE_W = 130f;
    private static final float LABEL_W = 150f;
    /** The clickable area of a side's flag and name (two lines of name). */
    private static final float NAME_H = 40f;

    private Card() {
    }

    /** @param left the record side shown on the left; {@code dim} for a war that has just ended */
    static CustomPanelAPI build(CustomPanelAPI parent, float w, WarRecord r, int left, SectorSnapshot s, long now,
                                boolean dim, String marker, Object detailsAction) {
        CustomPanelAPI card = parent.createCustomPanel(w, HEIGHT, null);
        TooltipMakerAPI in = card.createUIElement(w, HEIGHT, false);
        int right = 1 - left;
        FactionAPI fl = Wars.faction(r.id(left));
        FactionAPI fr = Wars.faction(r.id(right));
        // A war in its first 30 days has an amber frame, and a small line saying so at the bottom.
        boolean fresh = !dim && Flourish.on("NewWar") && Wars.isNew(r, now);
        Color frame = dim ? Misc.getGrayColor().darker() : fresh ? Flourish.NEW_FRAME : Misc.getDarkPlayerColor();
        Ui.frame(in, 0f, 0f, w, HEIGHT, frame);
        Color text = dim ? Misc.getGrayColor() : Misc.getTextColor();
        Color cl = fl == null ? Misc.getGrayColor() : Ui.readable(fl);
        Color cr = fr == null ? Misc.getGrayColor() : Ui.readable(fr);

        // Names and flags facing each other, the war's age between them.
        // Each side's flag and name open its Faction Archive (a flat button under both).
        float nameW = (w - 2f * PAD - AGE_W - 2f * (Ui.flagWidth(fl == null ? fr : fl, FLAG_H) + 8f)) / 2f;
        float y = PAD;
        if (fl != null) {
            float fw = Ui.flagWidth(fl, FLAG_H);
            float bw = fw + 8f + Math.min(nameW, in.computeStringWidth(Fmt.name(fl)) + 6f) + 8f;
            ButtonAPI b = Ui.flatButton(in, View.Action.of(View.Action.Kind.FACTION_ARCHIVE, fl.getId()), PAD - 4f, y - 4f,
                    bw, NAME_H);
            Ui.tooltip(in, b, Tips.faction(fl.getId(), s, true));
            Ui.flag(in, fl, PAD, y, FLAG_H);
            Ui.text(in, Fmt.name(fl), cl, PAD + fw + 8f, y + 2f, nameW, 2, Alignment.LMID);
        }
        if (fr != null) {
            float fw = Ui.flagWidth(fr, FLAG_H);
            float bw = fw + 8f + Math.min(nameW, in.computeStringWidth(Fmt.name(fr)) + 6f) + 8f;
            ButtonAPI b = Ui.flatButton(in, View.Action.of(View.Action.Kind.FACTION_ARCHIVE, fr.getId()),
                    w - PAD + 4f - bw, y - 4f, bw, NAME_H);
            Ui.tooltip(in, b, Tips.faction(fr.getId(), s, true));
            Ui.flag(in, fr, w - PAD - fw, y, FLAG_H);
            Ui.text(in, Fmt.name(fr), cr, w - PAD - fw - 8f - nameW, y + 2f, nameW, 2, Alignment.RMID);
        }
        int days = Fmt.days(r.start, r.ended() ? r.end : now);
        String age = days + (r.startKnown ? (days == 1 ? " day" : " days") : "+ days");
        Ui.text(in, age, text, w / 2f - AGE_W / 2f, y, AGE_W, 1, Alignment.MID);
        Ui.Placed since = Ui.text(in, (r.ended() ? "ended " + Fmt.date(r.end) : "since " + Fmt.date(r.start)),
                Misc.getGrayColor(), w / 2f - AGE_W / 2f, y + 16f, AGE_W, 1, Alignment.MID);
        if (!r.startKnown) {
            Ui.tooltip(in, since.comp, Tips.text("Hostilities were already under way when " + Wars.recordsWord(r) + " on "
                    + Fmt.date(r.start) + ". Figures count from then."));
        }

        // The tug bar.
        y = 54f;
        float barW = w - 2f * PAD;
        float pl = Score.total(r, left);
        float pr = Score.total(r, right);
        float share = Score.share(pl, pr);
        UIComponentAPI barComp = bar(in, PAD, y, barW, share, dim ? cl.darker() : cl, dim ? cr.darker() : cr);
        Ui.tooltip(in, barComp, Tips.score(r, left));
        y += 16f;
        String ln = fl == null ? "Unknown" : Fmt.name(fl);
        String rn = fr == null ? "Unknown" : Fmt.name(fr);
        Phrase verdict = Wars.verdict(share, ln, rn, cl, cr, r.ended());
        Ui.text(in, verdict.text(), text, PAD, y, barW, 1, Alignment.MID, verdict.highlights(), verdict.colors());
        y += 18f;
        int g = Score.gaining(r, now);
        int gl = g < 0 ? g : (g == left ? 0 : 1);
        Phrase mom = r.ended() ? ending(r) : Wars.momentum(gl, ln, rn, cl, cr);
        Ui.text(in, mom.text(), Misc.getGrayColor(), PAD, y, barW, 1, Alignment.MID, mom.highlights(), mom.colors());
        if (!r.ended() && gl >= 0) Flourish.momentumArrow(in, mom.text(), PAD, y, barW, gl == 1, gl == 0 ? cl : cr);

        // Three mirrored numbers.
        y += 24f;
        float mid = w / 2f;
        float labelW = LABEL_W;
        float numW = mid - labelW / 2f - PAD - 6f;
        String[] labels = {"Battles won", "Colonies captured", "Ships lost"};
        for (int k = 0; k < 3; k++) {
            String a;
            String b;
            boolean brightA;
            boolean brightB;
            if (k == 0) {
                int na = r.fightsWon(left);
                int nb = r.fightsWon(right);
                a = Fmt.num(na);
                b = Fmt.num(nb);
                brightA = na > nb;
                brightB = nb > na;
            } else if (k == 1) {
                int na = r.get(WarRecord.CAPTURED, left);
                int nb = r.get(WarRecord.CAPTURED, right);
                a = Fmt.num(na);
                b = Fmt.num(nb);
                brightA = na > nb;
                brightB = nb > na;
            } else {
                a = (r.lossesEstimated(left) ? "~" : "") + Fmt.num(r.shipsLost(left));
                b = (r.lossesEstimated(right) ? "~" : "") + Fmt.num(r.shipsLost(right));
                brightA = false;
                brightB = false;
            }
            Color h = Misc.getHighlightColor();
            Ui.Placed na = Ui.text(in, a, brightA && !dim ? h : text, PAD, y, numW, 1, Alignment.RMID);
            Ui.Placed lb = Ui.text(in, labels[k], Misc.getGrayColor(), mid - labelW / 2f, y, labelW, 1, Alignment.MID);
            Ui.Placed nb = Ui.text(in, b, brightB && !dim ? h : text, mid + labelW / 2f + 6f, y, numW, 1, Alignment.LMID);
            // The numbers explain themselves on hover; the captured colonies are listed by name.
            Tips.Tip tip = k == 1 ? Tips.captures(r, left) : Tips.fights(r, left);
            Ui.tooltip(in, na.comp, tip);
            Ui.tooltip(in, nb.comp, tip);
            if (k == 1) Ui.tooltip(in, lb.comp, tip);
            y += 18f;
        }

        // The latest event, at most two lines.
        y += 6f;
        StoryEvent e = r.latest();
        if (e != null) {
            Phrase p = Story.dated(r, e, true);
            Ui.text(in, p.text(), text, PAD, y, barW, 2, Alignment.TL, p.highlights(), p.colors());
        }

        // The new-conflict line, the marker, the engaged light, and Details.
        float by = HEIGHT - PAD - 24f;
        boolean engaged = !dim && !r.ended() && Flourish.on("Engaged") && engaged(r, now);
        float mw = barW - (engaged ? 200f : 120f);
        boolean marked = marker != null && !marker.isEmpty();
        if (fresh) {
            // In small print, the marker under it (a new war has not ended: days is its age).
            String when = days <= 0 ? "today" : days == 1 ? "yesterday" : days + " days ago";
            in.setParaSmallInsignia();
            float lh = Ui.lineHeight(in);
            float ty = by + (24f - (marked ? 2f : 1f) * lh) / 2f;
            Ui.text(in, "New conflict - started " + when, Misc.getGrayColor(), PAD, ty, mw, 1, Alignment.LMID,
                    new String[]{"New conflict"}, new Color[]{Flourish.AMBER});
            if (marked) Ui.text(in, marker, Misc.getGrayColor(), PAD, ty + lh, mw, 1, Alignment.LMID);
            in.setParaFontDefault();
        } else if (marked) {
            Ui.text(in, marker, Misc.getGrayColor(), PAD, by + 4f, mw, 1, Alignment.LMID);
        }
        if (engaged) {
            // A light that pulses on a war with fighting in the last 30 days.
            String word = "Engaged";
            float ew = in.computeStringWidth(word) + 4f;
            float ex = w - PAD - 110f - 10f - ew;
            Ui.text(in, word, new Color(233, 135, 123), ex, by + 4f, ew, 1, Alignment.LMID);
            Fx.pulse(card, in, Flourish.ALARM, ex - 11f, by + 9f, 6f, 1.6f);
        }
        Ui.button(in, "Full report", detailsAction, w - PAD - 110f, by, 110f, 24f);
        if (fresh && Pages.opening) {
            // As the page opens, a new war's frame lights up once before settling.
            Fx.flash(card, in, Flourish.AMBER, 0f, 0f, w, HEIGHT, 1.8f);
        }
        card.addUIElement(in).inTL(0f, 0f);
        return card;
    }

    /** Whether anything scored in the war in the last 30 days: a fight, a capture, a raid, losses. */
    private static boolean engaged(WarRecord r, long now) {
        float days = (now - r.start) / (float) warroom.data.WarData.DAY;
        float window = Wars.RECENT_DAYS;
        return r.momentum.recent(days, window, 0) + r.momentum.recent(days, window, 1) > 0f;
    }

    /**
     * "Ceasefire agreed", "Peace treaty signed" or "Hostilities ceased" (peace by other means, or not known), or
     * "Ended - X eliminated" when one side had held no colonies for 30 days.
     */
    static Phrase ending(WarRecord r) {
        if (r.endReason == WarRecord.END_PEACE) {
            int how = r.peaceKind();
            return new Phrase().add(how == WarRecord.PEACE_TREATY ? "Peace treaty signed"
                    : how == WarRecord.PEACE_CEASEFIRE ? "Ceasefire agreed" : "Hostilities ceased");
        }
        return new Phrase().add("Ended - ").faction(r.endReason == WarRecord.END_GONE_A ? r.a : r.b).add(" eliminated");
    }

    /**
     * A 10 px split bar: the left share in the left colour, a 2 px gap, the rest in the right colour, and a
     * 2 px tick at the centre for "even". Gray while nothing has been recorded. Returns a component covering
     * the bar, for its tooltip.
     */
    static UIComponentAPI bar(TooltipMakerAPI in, float x, float y, float w, float share, Color left, Color right) {
        if (Float.isNaN(share)) {
            Ui.rect(in, x, y + 1f, w, 10f, Misc.getGrayColor().darker().darker());
        } else {
            float lw = Math.max(0f, Math.min(w - 2f, (w - 2f) * share));
            Ui.rect(in, x, y + 1f, lw, 10f, left);
            Ui.rect(in, x + lw + 2f, y + 1f, w - lw - 2f, 10f, right);
        }
        Ui.rect(in, x + w / 2f - 1f, y - 2f, 2f, 16f, Misc.getTextColor());
        if (Flourish.on("GaugeTicks")) {
            // Quarter marks, like an instrument's gauge.
            Color tick = new Color(230, 240, 245, 120);
            Ui.rect(in, x + w * 0.25f, y - 1f, 1f, 14f, tick);
            Ui.rect(in, x + w * 0.75f, y - 1f, 1f, 14f, tick);
        }
        // An invisible rectangle on top, for the tooltip.
        return Ui.rect(in, x, y - 2f, w, 16f, new Color(0, 0, 0, 0));
    }
}
