package warroom.ui;

import com.fs.starfarer.api.ui.Alignment;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.LabelAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import warroom.Score;
import warroom.Settings;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

/**
 * The Briefing: how the figures in this report are worked out, in plain words with the numbers that
 * matter. The text is in {@link BriefingText}.
 */
final class BriefingView {
    private static final float MAX_W = 780f;

    private BriefingView() {
    }

    static void build(CustomPanelAPI body, float w, float h) {
        float cw = Math.min(MAX_W, w - 20f);
        TooltipMakerAPI t = body.createUIElement(cw + 20f, h, true);
        Color hc = Misc.getHighlightColor();
        boolean first = true;
        for (BriefingText.Section s : BriefingText.SECTIONS) {
            if (s.title != null) {
                t.addSectionHeading(s.title, Alignment.MID, first ? 0f : 18f);
            }
            boolean firstPara = true;
            String[] words = fill(s.highlights);
            for (String para : s.paragraphs) {
                String p = fill(para);
                boolean bullet = p.startsWith("- ");
                String text = bullet ? "    - " + p.substring(2) : p;
                float pad = bullet ? 3f : firstPara ? 8f : 6f;
                LabelAPI l = s.title == null ? t.addPara(text, Misc.getGrayColor(), pad) : t.addPara(text, pad);
                highlight(l, text, words, hc);
                firstPara = false;
            }
            first = false;
        }
        Flourish.signOff(body, t, cw);
        t.addSpacer(20f);
        body.addUIElement(t).inTL(Math.max(0f, (w - cw - 20f) / 2f), 0f);
        View.restoreScroll(t.getExternalScroller());
    }

    /** The figures that follow War Room's settings, filled in where the text names them: {days} and {quiet}. */
    private static String fill(String s) {
        if (s == null || s.indexOf('{') < 0) return s;
        return s.replace("{days}", String.valueOf(Settings.recentDays()))
                .replace("{quiet}", String.valueOf(Math.round(Score.quietBelow())));
    }

    private static String[] fill(String[] words) {
        if (words == null) return null;
        String[] out = new String[words.length];
        for (int i = 0; i < words.length; i++) out[i] = fill(words[i]);
        return out;
    }

    /** Highlights the section's key numbers and terms that this paragraph contains, in reading order. */
    private static void highlight(LabelAPI l, String text, String[] words, Color c) {
        if (words == null || words.length == 0) return;
        List<int[]> found = new ArrayList<>();
        for (int i = 0; i < words.length; i++) {
            String wd = words[i];
            if (wd == null || wd.isEmpty()) continue;
            int at = text.indexOf(wd);
            if (at >= 0) found.add(new int[]{at, i});
        }
        if (found.isEmpty()) return;
        found.sort((a, b) -> Integer.compare(a[0], b[0]));
        // Overlapping matches would confuse the label; keep the first of each overlap.
        List<String> keep = new ArrayList<>();
        int end = -1;
        for (int[] f : found) {
            if (f[0] < end) continue;
            keep.add(words[f[1]]);
            end = f[0] + words[f[1]].length();
        }
        Color[] cs = new Color[keep.size()];
        java.util.Arrays.fill(cs, c);
        l.setHighlight(keep.toArray(new String[0]));
        l.setHighlightColors(cs);
    }
}
