package warroom.ui;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.util.Misc;
import warroom.Fmt;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

/** A sentence built from plain text and highlighted parts (faction names in their colours, numbers). */
final class Phrase {
    private final StringBuilder text = new StringBuilder();
    private final List<String> hl = new ArrayList<>();
    private final List<Color> hc = new ArrayList<>();

    Phrase add(String s) {
        if (s != null) text.append(s);
        return this;
    }

    Phrase hl(String s, Color c) {
        if (s == null || s.isEmpty()) return this;
        text.append(s);
        hl.add(s);
        hc.add(c);
        return this;
    }

    Phrase num(int n) {
        return hl(Fmt.num(n), Misc.getHighlightColor());
    }

    /** A faction's short name, for labels and fragments ("Hegemony holds the advantage"). */
    Phrase faction(String id) {
        FactionAPI f = id == null ? null : Global.getSector().getFaction(id);
        if (f == null) return hl(id == null ? "Unknown forces" : id, Misc.getGrayColor());
        return hl(Fmt.name(f), Ui.readable(f));
    }

    /** A faction as named in a sentence, with the game's article ("The Hegemony captured ..."). */
    Phrase the(String id, boolean cap) {
        FactionAPI f = id == null ? null : Global.getSector().getFaction(id);
        if (f == null) return hl(id == null ? (cap ? "Unknown forces" : "unknown forces") : id, Misc.getGrayColor());
        return hl(Fmt.theName(f, cap), Ui.readable(f));
    }

    String text() {
        return text.toString();
    }

    String[] highlights() {
        return hl.toArray(new String[0]);
    }

    Color[] colors() {
        return hc.toArray(new Color[0]);
    }
}
