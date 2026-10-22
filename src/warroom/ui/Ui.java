package warroom.ui;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.graphics.SpriteAPI;
import com.fs.starfarer.api.ui.Alignment;
import com.fs.starfarer.api.ui.ButtonAPI;
import com.fs.starfarer.api.ui.CutStyle;
import com.fs.starfarer.api.ui.LabelAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI.TooltipCreator;
import com.fs.starfarer.api.ui.TooltipMakerAPI.TooltipLocation;
import com.fs.starfarer.api.ui.UIComponentAPI;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

/**
 * Native building blocks, as tried with the 0.2.1 probe: rectangles are resized empty section headings,
 * text is a free label wrapped with autoSizeToWidth, flags keep their own proportions, and everything is
 * placed at fixed positions inside a non-scrolling TooltipMakerAPI.
 */
final class Ui {
    private Ui() {
    }

    /**
     * A solid rectangle; returns it, for a tooltip. (Not getPrev(): that is an invisible spacer the heading
     * leaves in the flow, somewhere else entirely.)
     */
    static UIComponentAPI rect(TooltipMakerAPI in, float x, float y, float w, float h, Color c) {
        LabelAPI l = in.addSectionHeading("", Color.BLACK, c, Alignment.MID, 0f);
        l.getPosition().setSize(Math.max(0f, w), Math.max(0f, h)).inTL(x, y);
        return (UIComponentAPI) l;
    }

    /** A 1 px frame. */
    static void frame(TooltipMakerAPI in, float x, float y, float w, float h, Color c) {
        rect(in, x, y, w, 1f, c);
        rect(in, x, y + h - 1f, w, 1f, c);
        rect(in, x, y, 1f, h, c);
        rect(in, x + w - 1f, y, 1f, h, c);
    }

    /** Height of one line of text in the current paragraph font. */
    static float lineHeight(TooltipMakerAPI in) {
        LabelAPI l = in.createLabel("Ag", Color.WHITE);
        l.autoSizeToWidth(1000f);
        return l.getPosition().getHeight();
    }

    /** A placed label: what was shown, its height, the component (for tooltips) and the label itself. */
    static final class Placed {
        final String shown;
        final float height;
        final UIComponentAPI comp;
        final LabelAPI label;

        Placed(String shown, float height, UIComponentAPI comp, LabelAPI label) {
            this.shown = shown;
            this.height = height;
            this.comp = comp;
            this.label = label;
        }

        boolean shortened(String full) {
            return !shown.equals(full);
        }
    }

    /**
     * Text wrapped to {@code w}, at most {@code maxLines} lines; only the last line is shortened with "...".
     * Highlights that the shortened text no longer contains are left out.
     */
    static Placed text(TooltipMakerAPI in, String s, Color c, float x, float y, float w, int maxLines, Alignment align,
                       String[] hl, Color[] hc) {
        float line = lineHeight(in);
        float maxH = line * maxLines + 1f;
        String shown = s;
        LabelAPI l = label(in, shown, c, w, align);
        if (l.getPosition().getHeight() > maxH) {
            int lo = 0;
            int hi = s.length();
            while (lo < hi) {
                int mid = (lo + hi + 1) / 2;
                LabelAPI probe = label(in, s.substring(0, mid).trim() + "...", c, w, align);
                if (probe.getPosition().getHeight() <= maxH) lo = mid;
                else hi = mid - 1;
            }
            shown = s.substring(0, lo).trim() + "...";
            l = label(in, shown, c, w, align);
        }
        if (hl != null && hc != null) {
            List<String> keep = new ArrayList<>();
            List<Color> keepC = new ArrayList<>();
            for (int i = 0; i < hl.length && i < hc.length; i++) {
                if (hl[i] != null && !hl[i].isEmpty() && shown.contains(hl[i])) {
                    keep.add(hl[i]);
                    keepC.add(hc[i]);
                }
            }
            if (!keep.isEmpty()) {
                l.setHighlight(keep.toArray(new String[0]));
                l.setHighlightColors(keepC.toArray(new Color[0]));
            }
        }
        float h = l.getPosition().getHeight();
        UIComponentAPI comp = in.addCustomDoNotSetPosition((UIComponentAPI) l);
        comp.getPosition().inTL(x, y);
        return new Placed(shown, h, comp, l);
    }

    static Placed text(TooltipMakerAPI in, String s, Color c, float x, float y, float w, int maxLines, Alignment align) {
        return text(in, s, c, x, y, w, maxLines, align, null, null);
    }

    private static LabelAPI label(TooltipMakerAPI in, String s, Color c, float w, Alignment align) {
        LabelAPI l = in.createLabel(s, c);
        l.setAlignment(align);
        l.autoSizeToWidth(Math.max(10f, w));
        return l;
    }

    /** A faction's flag at height {@code h}, as wide as its own proportions say; returns its width. */
    static float flag(TooltipMakerAPI in, FactionAPI f, float x, float y, float h) {
        String img = f.getLogo();
        if (img == null || img.isEmpty()) img = f.getCrest();
        if (img == null || img.isEmpty()) return 0f;
        float w = h * aspect(img);
        in.addImage(img, w, h, 0f);
        in.getPrev().getPosition().inTL(x, y);
        return w;
    }

    /** A faction's crest, square. */
    static void crest(TooltipMakerAPI in, FactionAPI f, float x, float y, float size) {
        String img = f.getCrest();
        if (img == null || img.isEmpty()) img = f.getLogo();
        if (img == null || img.isEmpty()) return;
        in.addImage(img, size, size, 0f);
        in.getPrev().getPosition().inTL(x, y);
    }

    static float flagWidth(FactionAPI f, float h) {
        String img = f.getLogo();
        if (img == null || img.isEmpty()) img = f.getCrest();
        return img == null || img.isEmpty() ? 0f : h * aspect(img);
    }

    private static float aspect(String img) {
        try {
            SpriteAPI s = Global.getSettings().getSprite(img);
            if (s != null && s.getWidth() > 0f && s.getHeight() > 0f) return s.getWidth() / s.getHeight();
        } catch (RuntimeException ex) {
            // fall back below
        }
        return 1.6f;
    }

    /** A native button at a fixed place, with the game's usual hover sound. */
    static ButtonAPI button(TooltipMakerAPI in, String text, Object data, float x, float y, float w, float h) {
        ButtonAPI b = in.addButton(text, data, w, h, 0f);
        b.getPosition().inTL(x, y);
        return b;
    }

    /**
     * Flat buttons: filled in the intel panel's own black, so only what is drawn over them shows; they glow
     * faintly on hover. No cut corners, no sound.
     */
    private static final Color FLAT_BASE = new Color(70, 110, 125);
    private static final Color FLAT_BG = new Color(0, 0, 0);

    /** An empty flat button covering an area, for clicking on what is drawn over it. */
    static ButtonAPI flatButton(TooltipMakerAPI in, Object data, float x, float y, float w, float h) {
        return flatButton(in, data, x, y, w, h, FLAT_BG);
    }

    /** The same, filled with {@code bg} (the colour of what it sits on). */
    static ButtonAPI flatButton(TooltipMakerAPI in, Object data, float x, float y, float w, float h, Color bg) {
        ButtonAPI b = in.addButton("", data, FLAT_BASE, bg, Alignment.MID, CutStyle.NONE, Math.max(1f, w), Math.max(1f, h), 0f);
        b.setMouseOverSound(null);
        b.getPosition().inTL(x, y);
        return b;
    }

    /** A tiny pixel pyramid, 9 px wide and 5 tall, pointing up (most first) or down (least first). */
    static void pyramid(TooltipMakerAPI in, float x, float y, boolean up, Color c) {
        for (int i = 0; i < 5; i++) {
            rect(in, x + 4f - i, y + (up ? i : 4 - i), 2f * i + 1f, 1f, c);
        }
    }

    /** The same on its side, 5 px wide and 9 tall, pointing right or left. */
    static void arrowhead(TooltipMakerAPI in, float x, float y, boolean right, Color c) {
        for (int i = 0; i < 5; i++) {
            int k = right ? 4 - i : i;
            rect(in, x + i, y + 4f - k, 1f, 2f * k + 1f, c);
        }
    }

    /** The same without a hover sound, for dense rows of buttons that would otherwise chatter. */
    static ButtonAPI quietButton(TooltipMakerAPI in, String text, Object data, float x, float y, float w, float h) {
        ButtonAPI b = button(in, text, data, x, y, w, h);
        b.setMouseOverSound(null);
        return b;
    }

    static void tooltip(TooltipMakerAPI in, UIComponentAPI comp, TooltipCreator tc) {
        if (comp != null && tc != null) in.addTooltipTo(tc, comp, TooltipLocation.BELOW);
    }

    /** A colour that reads on the dark UI: very dark faction colours are replaced by their bright UI colour. */
    static Color readable(FactionAPI f) {
        Color c = f.getBaseUIColor();
        float lum = (0.299f * c.getRed() + 0.587f * c.getGreen() + 0.114f * c.getBlue()) / 255f;
        return lum < 0.28f ? f.getBrightUIColor() : c;
    }
}
