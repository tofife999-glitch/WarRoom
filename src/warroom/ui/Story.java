package warroom.ui;

import com.fs.starfarer.api.util.Misc;
import warroom.Fmt;
import warroom.data.StoryEvent;
import warroom.data.WarRecord;

/**
 * The war log's sentences, in the voice of an intelligence report: past tense, the game's articles,
 * colony sizes always spelled out.
 */
final class Story {
    private Story() {
    }

    /** One logged event as a sentence, without its date. */
    static Phrase sentence(WarRecord r, StoryEvent e) {
        Phrase p = new Phrase();
        String side = e.side >= 0 ? r.id(e.side) : null;
        String other = e.side >= 0 ? r.id(1 - e.side) : null;
        java.awt.Color h = Misc.getHighlightColor();
        switch (e.kind) {
            case BEGAN:
                if (r.startKnown) return p.add("Hostilities began.");
                return p.add(Wars.recordsWord(r).equals("records began") ? "Records begin. Hostilities already under way."
                        : "First recorded. Hostilities already under way.");
            case CAPTURE:
                return p.the(side, true).add(" captured ").hl(place(e.text), h).add(" (size ").num(e.n(0)).add(") from ")
                        .the(other, false).add(".");
            case BATTLE: {
                // n0/n1: ships the winner/loser lost; n4 = 1: a reported fight, its losses estimated.
                boolean noKill = e.n(1) == 0;
                boolean reported = e.n(4) == 1;
                String about = reported ? "about " : "";
                p.the(side, true).add(noKill ? " drove off " : " defeated ").the(other, false);
                if (e.text == null) p.add(" in a major battle");
                else if (e.text.equalsIgnoreCase("hyperspace")) p.add(" in hyperspace");
                else p.add(" at ").hl(e.text, h);
                if (reported) p.add(" (reported)");
                if (noKill) {
                    return p.add(", losing " + about).num(e.n(0)).add(e.n(0) == 1 ? " ship" : " ships").add(" and destroying none.");
                }
                p.add(", destroying " + about).num(e.n(1)).add(e.n(1) == 1 ? " ship" : " ships");
                if (e.n(0) == 0) return p.add(" without loss.");
                return p.add(" for the loss of " + about).num(e.n(0)).add(".");
            }
            case BOMBARD:
                // n0: sizes lost; n1/n2: size before and after (missing in events from 0.3.1 and before; a
                // saturation bombardment lowers a colony by one size unless it destroys it, so more lost means destroyed).
                boolean legacy = e.n(1) <= 0;
                if ((!legacy && e.n(2) <= 0) || (legacy && e.n(0) >= 2)) {
                    return p.the(side, true).add(" bombarded ").hl(place(e.text), h).add(" (size ")
                            .num(legacy ? e.n(0) : e.n(1)).add("). Colony destroyed.");
                }
                p.the(side, true).add(" bombarded ").hl(place(e.text), h).add(". Colony size reduced ");
                if (!legacy) return p.add("from ").num(e.n(1)).add(" to ").num(e.n(2)).add(".");
                return p.add("by ").num(e.n(0)).add(".");
            case RAID:
                if (e.n(0) == StoryEvent.RAID_TACTICAL) {
                    return p.the(side, true).add(" carried out a tactical bombardment of ").hl(place(e.text), h).add(".");
                }
                return p.the(side, true).add(" raided ").hl(place(e.text), h).add(" successfully.");
            case REPELLED:
                return p.the(side, true).add(" repelled an invasion of ").hl(place(e.text), h).add(" by ")
                        .the(other, false).add(".");
            case GONE:
                return p.the(side, true).add(Fmt.plural(Wars.faction(side)) ? " lost their last colony." : " lost its last colony.");
            case ENDED:
                return ending(r, p);
            case SHIFT:
                if (e.side < 0) return p.the(r.id(r.left), true).add(" and ").the(r.id(1 - r.left), false).add(" were evenly matched again.");
                return p.the(side, true).add(" took the advantage over ").the(other, false).add(".");
            default:
                return p;
        }
    }

    private static Phrase ending(WarRecord r, Phrase p) {
        if (r.endReason == WarRecord.END_PEACE) {
            int how = r.peaceKind();
            if (how == WarRecord.PEACE_TREATY) return p.add("Both sides signed a peace treaty.");
            if (how == WarRecord.PEACE_CEASEFIRE) return p.add("Both sides agreed to a ceasefire.");
            return p.add("Hostilities ceased.");
        }
        String gone = r.endReason == WarRecord.END_GONE_A ? r.a : r.b;
        return p.add("Hostilities ended: ").the(gone, false).add(" had held no colonies for 30 days.");
    }

    private static String place(String name) {
        return name == null || name.isEmpty() ? "a colony" : name;
    }

    /** "2 Oct   The Hegemony captured Coatl (size 5) from Tri-Tachyon." */
    static Phrase dated(WarRecord r, StoryEvent e, boolean shortDate) {
        Phrase p = new Phrase().hl(shortDate ? Fmt.shortDate(e.time) : Fmt.date(e.time), Misc.getGrayColor()).add("   ");
        Phrase s = sentence(r, e);
        return append(p, s);
    }

    static Phrase append(Phrase into, Phrase s) {
        String text = s.text();
        String[] hl = s.highlights();
        java.awt.Color[] hc = s.colors();
        // Re-add the sentence piece by piece so its highlights carry over.
        int pos = 0;
        for (int i = 0; i < hl.length; i++) {
            int at = text.indexOf(hl[i], pos);
            if (at < 0) continue;
            into.add(text.substring(pos, at));
            into.hl(hl[i], hc[i]);
            pos = at + hl[i].length();
        }
        into.add(text.substring(pos));
        return into;
    }

    /** How the war started and how long it has lasted, for the war page's header. */
    static Phrase since(WarRecord r, long now) {
        Phrase p = new Phrase();
        int days = Fmt.days(r.start, r.ended() ? r.end : now);
        java.awt.Color h = Misc.getHighlightColor();
        if (r.ended()) {
            p.add("Lasted ").num(days).add(!r.startKnown ? "+ days. " : days == 1 ? " day. " : " days. ");
            if (r.endReason == WarRecord.END_PEACE) {
                int how = r.peaceKind();
                p.add(how == WarRecord.PEACE_TREATY ? "Peace treaty signed on "
                        : how == WarRecord.PEACE_CEASEFIRE ? "Ceasefire agreed on " : "Hostilities ceased on ");
                return p.hl(Fmt.date(r.end), h).add(".");
            }
            String gone = r.endReason == WarRecord.END_GONE_A ? r.a : r.b;
            return p.add("Ended on ").hl(Fmt.date(r.end), h).add(": ").the(gone, false).add(" eliminated.");
        }
        if (!r.startKnown) {
            return p.add("At war since before ").hl(Fmt.date(r.start), h).add(", when " + Wars.recordsWord(r) + " (")
                    .num(days).add("+ days).");
        }
        return p.add("At war since ").hl(Fmt.date(r.start), h).add(" (").num(days).add(days == 1 ? " day)." : " days).");
    }
}
