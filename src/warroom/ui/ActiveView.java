package warroom.ui;

import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.ui.Alignment;
import com.fs.starfarer.api.ui.ButtonAPI;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.LabelAPI;
import com.fs.starfarer.api.ui.PositionAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import warroom.Fmt;
import warroom.Score;
import warroom.SectorSnapshot;
import warroom.Settings;
import warroom.data.StoryEvent;
import warroom.data.WarData;
import warroom.data.WarRecord;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The Hostilities tab: the pirate toggle on top, then "Your wars", "Other wars" and "Recently ended". Single
 * wars are cards, two per row; alliance wars take the full width. At most MAX_ITEMS are built until "Show
 * all" is pressed. The sort buttons on each section's heading order every section alike: by war age, battles,
 * colonies captured or advantage, most first, then least first, then back to the usual order (latest news first).
 */
public final class ActiveView {
    static final int MAX_ITEMS = 20;
    static final float GAP = 10f;
    /** The order the list shows its wars in, for Prev and Next. */
    static final List<String> walk = new ArrayList<>();
    /** The sorts (View.warSort): their icons, what they sort by, and their two orders. */
    private static final Icons.Kind[] SORTS = {Icons.Kind.AGE, Icons.Kind.BATTLE, Icons.Kind.CAPTURE, Icons.Kind.ADVANTAGE};
    private static final String[] SORT_BY = {"war age", "battles fought", "colonies captured", "advantage"};
    private static final String[][] SORT_ORDER = {{"longest first", "newest first"}, {"most first", "fewest first"},
            {"most first", "fewest first"}, {"most one-sided first", "most evenly matched first"}};

    private ActiveView() {
    }

    static void build(CustomPanelAPI body, float w, float h, SectorSnapshot s, long now) {
        TooltipMakerAPI t = body.createUIElement(w, h, true);
        float cw = w - 20f; // leave room for the scrollbar
        WarData d = WarData.get();

        if (Flourish.on("Intercept")) intercept(body, t, cw, d, now);
        int hidden = Wars.hiddenPirateWars(d, now);
        if (hidden > 0 || View.showPirates) {
            String label = View.showPirates ? "Showing wars with pirate factions" : "Show " + hidden + " more "
                    + (hidden == 1 ? "war" : "wars") + " with pirate factions";
            ButtonAPI b = t.addAreaCheckbox(label, View.Action.of(View.Action.Kind.PIRATES), Misc.getBasePlayerColor(),
                    Misc.getDarkPlayerColor(), Misc.getBrightPlayerColor(), 380f, 24f, 0f);
            b.setChecked(View.showPirates);
        }

        List<Wars.Item> items = Wars.activeItems(d, View.showPirates, now);
        List<Wars.Item> mine = new ArrayList<>();
        List<Wars.Item> other = new ArrayList<>();
        for (Wars.Item i : items) (i.mine() ? mine : other).add(i);
        List<WarRecord> ended = Wars.justEnded(d, View.showPirates, now);
        if (View.warSort >= 0) {
            // Stable sorts: wars that tie keep the usual order.
            final int key = View.warSort;
            // Each worked out once.
            Map<Wars.Item, Double> vi = new IdentityHashMap<>();
            for (Wars.Item i : items) vi.put(i, value(i, key, now));
            Map<WarRecord, Double> vr = new IdentityHashMap<>();
            for (WarRecord r : ended) vr.put(r, value(r, key, now));
            Comparator<Wars.Item> items0 = Comparator.comparingDouble(vi::get);
            Comparator<WarRecord> wars0 = Comparator.comparingDouble(vr::get);
            mine.sort(View.warDown ? items0 : items0.reversed());
            other.sort(View.warDown ? items0 : items0.reversed());
            ended.sort(View.warDown ? wars0 : wars0.reversed());
        }

        walk.clear();
        for (Wars.Item i : mine) walk.add(i.ref());
        for (Wars.Item i : other) walk.add(i.ref());
        for (WarRecord r : ended) walk.add(r.id());

        if (items.isEmpty() && ended.isEmpty()) {
            t.addPara(hidden > 0 && !View.showPirates ? "No hostilities except with pirate factions." : "The Sector is at peace.",
                    Misc.getTextColor(), 20f);
            int past = 0;
            for (WarRecord r : d.archive) {
                if (Wars.shown(r, View.showPirates, now)) past++;
            }
            if (past > 0) {
                t.addPara(past + (past == 1 ? " war has" : " wars have") + " ended since records began. See Archive.",
                        Misc.getGrayColor(), 6f);
            }
        }

        int budget = View.showAll ? Integer.MAX_VALUE : MAX_ITEMS;
        budget = section(body, t, cw, "Your wars", mine, s, now, budget);
        budget = section(body, t, cw, "Other wars", other, s, now, budget);
        if (!ended.isEmpty() && budget > 0) {
            heading(body, t, cw, "Recently ended (" + ended.size() + ")", 16f);
            List<WarRecord> row = new ArrayList<>();
            for (WarRecord r : ended) {
                if (budget-- <= 0) break;
                row.add(r);
                if (row.size() == 2) {
                    endedRow(body, t, cw, row, s, now);
                    row.clear();
                }
            }
            if (!row.isEmpty()) endedRow(body, t, cw, row, s, now);
        }
        int total = items.size() + ended.size();
        if (!View.showAll && total > MAX_ITEMS) {
            t.addButton("Show all " + total + " wars", View.Action.of(View.Action.Kind.SHOW_ALL), 220f, 24f, 16f);
        }
        Flourish.signOff(body, t, cw);
        t.addSpacer(20f);

        body.addUIElement(t).inTL(0f, 0f);
        View.restoreScroll(t.getExternalScroller());
    }

    /** One section: its heading, then its items, cards two per row. Returns what is left of the budget. */
    private static int section(CustomPanelAPI body, TooltipMakerAPI t, float cw, String title, List<Wars.Item> items,
                               SectorSnapshot s, long now, int budget) {
        if (items.isEmpty() || budget <= 0) return budget;
        heading(body, t, cw, title + " (" + items.size() + ")", 16f);
        List<WarRecord> row = new ArrayList<>();
        for (Wars.Item i : items) {
            if (budget-- <= 0) break;
            if (i.group != null) {
                if (!row.isEmpty()) {
                    cardRow(body, t, cw, row, s, now);
                    row.clear();
                }
                boolean open = View.expanded.contains(i.group.key);
                t.addCustom(AllianceBlock.build(body, cw, i.group, s, now, open, View.lastOpened), GAP);
            } else {
                row.add(i.war);
                if (row.size() == 2) {
                    cardRow(body, t, cw, row, s, now);
                    row.clear();
                }
            }
        }
        if (!row.isEmpty()) cardRow(body, t, cw, row, s, now);
        return budget;
    }

    /**
     * "INTERCEPT" and the latest few logged events, one at a time, each fading in and out in turn; each line is
     * its own label (cut to one line), and a tiny panel's clock sets their opacity. With the clickable touch on,
     * the whole line opens the report of the war its current event belongs to.
     */
    private static void intercept(CustomPanelAPI body, TooltipMakerAPI t, float cw, WarData d, long now) {
        // The Chronicle's newest lines: both sides named, a faction's last colony lost shown once.
        List<Object[]> events = ArchiveView.latest(d, now, View.showPirates, 5);
        if (events.isEmpty()) return;
        Cycle cycle = new Cycle();
        CustomPanelAPI p = body.createCustomPanel(cw, 24f, cycle);
        TooltipMakerAPI in = p.createUIElement(cw, 24f, false);
        boolean click = Flourish.on("InterceptClick");
        String open = "Open report";
        float ow = click ? in.computeStringWidth(open) + 8f : 0f;
        if (click) {
            ButtonAPI b = Ui.flatButton(in, View.Action.of(View.Action.Kind.INTERCEPT, null, cycle), 0f, 0f, cw, 23f);
            Ui.tooltip(in, b, Tips.text("The line holds while the pointer is on it. Click to open the report of the war "
                    + "this event belongs to."));
        }
        // From the left edge, so the word is never cut off.
        float lw = Flourish.orbitronLabelAt(in, "INTERCEPT", Flourish.AMBER, 0f, 4f);
        float x = lw + 10f;
        for (int i = 0; i < events.size(); i++) {
            Phrase s = (Phrase) events.get(i)[0];
            Ui.Placed line = Ui.text(in, s.text(), Misc.getTextColor(), x, 4f, cw - x - ow - 8f, 1, Alignment.LMID,
                    s.highlights(), s.colors());
            line.label.setOpacity(i == 0 ? 1f : 0f);
            cycle.lines.add(line.label);
            cycle.wars.add((String) events.get(i)[1]);
        }
        if (click) Ui.text(in, open, Misc.getBasePlayerColor(), cw - ow, 4f, ow, 1, Alignment.RMID);
        Ui.rect(in, 0f, 23f, cw, 1f, new Color(27, 55, 65));
        p.addUIElement(in).inTL(0f, 0f);
        t.addCustom(p, 0f);
        t.addSpacer(6f);
    }

    /**
     * Shows the intercept's lines in turn: five seconds each, fading over half a second; knows whose line is up.
     * While the pointer is on the line it holds, so a click opens the war of the line that is shown.
     */
    static final class Cycle extends com.fs.starfarer.api.campaign.BaseCustomUIPanelPlugin {
        final List<LabelAPI> lines = new ArrayList<>();
        /** The war each line belongs to, for a click on the line. */
        final List<String> wars = new ArrayList<>();
        // Starts with the first line fully shown, not fading in.
        private float time = 0.5f;
        private int cur;
        private PositionAPI pos;
        private boolean held;

        /** The war of the line shown now. */
        String current() {
            return wars.isEmpty() ? null : wars.get(Math.min(cur, wars.size() - 1));
        }

        @Override
        public void positionChanged(PositionAPI position) {
            pos = position;
        }

        @Override
        public void processInput(List<InputEventAPI> events) {
            if (pos == null) return;
            for (InputEventAPI e : events) {
                if (e.isMouseEvent()) held = pos.containsEvent(e);
            }
        }

        @Override
        public void advance(float amount) {
            if (lines.size() < 2) return;
            float period = 5f;
            if (held) {
                // The line shown stays fully on, and gets its full time again once the pointer leaves.
                time = (float) Math.floor(time / period) * period + 0.5f;
                for (int i = 0; i < lines.size(); i++) lines.get(i).setOpacity(i == cur ? 1f : 0f);
                return;
            }
            time += Math.max(0f, Math.min(0.1f, amount));
            cur = (int) (time / period) % lines.size();
            float in = time % period;
            for (int i = 0; i < lines.size(); i++) {
                float a = 0f;
                if (i == cur) a = Math.min(1f, Math.min(in / 0.5f, (period - in) / 0.5f));
                lines.get(i).setOpacity(Math.max(0f, a));
            }
        }
    }

    /** A section's heading, with the sort buttons at its right end. */
    private static void heading(CustomPanelAPI body, TooltipMakerAPI t, float cw, String title, float pad) {
        CustomPanelAPI p = body.createCustomPanel(cw, 20f, null);
        TooltipMakerAPI in = p.createUIElement(cw, 20f, false);
        Color bar = Misc.getDarkPlayerColor();
        LabelAPI head = in.addSectionHeading(title, Misc.getBasePlayerColor(), bar, Alignment.MID, 0f);
        float hh = Math.max(16f, head.getPosition().getHeight());
        p.getPosition().setSize(cw, hh);
        float bw = 32f;
        float x = cw - SORTS.length * (bw + 2f) - 2f;
        for (int k = 0; k < SORTS.length; k++) {
            boolean on = View.warSort == k;
            ButtonAPI b = Ui.flatButton(in, View.Action.of(View.Action.Kind.SORT, "wars", k), x, 0f, bw, hh, bar);
            String now = !on ? "" : " Now " + SORT_ORDER[k][View.warDown ? 1 : 0] + ".";
            Ui.tooltip(in, b, Tips.text("Sort by " + SORT_BY[k] + ": " + SORT_ORDER[k][0] + ", then " + SORT_ORDER[k][1]
                    + ", then the usual order (latest news first)." + now));
            Color c = on ? Misc.getHighlightColor() : Misc.getBasePlayerColor();
            float icon = Math.min(16f, hh - 2f);
            Icons.place(p, in, SORTS[k], c, x + 4f, (hh - icon) / 2f, icon);
            if (on) Ui.pyramid(in, x + 22f, (hh - 5f) / 2f, !View.warDown, c);
            x += bw + 2f;
        }
        p.addUIElement(in).inTL(0f, 0f);
        t.addCustom(p, pad);
    }

    /** What a sort orders by, for one war: its age, its battles, its colonies captured, how one-sided it is. */
    private static double value(WarRecord r, int key, long now) {
        switch (key) {
            case 0:
                return (r.ended() ? r.end : now) - r.start;
            case 1:
                return r.fights();
            case 2:
                return r.get(WarRecord.CAPTURED, 0) + r.get(WarRecord.CAPTURED, 1);
            default:
                return lead(Score.total(r, 0), Score.total(r, 1));
        }
    }

    /** The same for an item: an alliance war sums its fronts (ended ones too, as its block does) and is as old as its first. */
    private static double value(Wars.Item i, int key, long now) {
        if (i.group == null) return value(i.war, key, now);
        Wars.AllianceWar whole = WarPage.findGroup(i.group.key);
        Wars.AllianceWar g = whole != null ? whole : i.group;
        if (key == 0) return (g.ended() ? g.end() : now) - g.start();
        double sum = 0;
        float pl = 0f;
        float pr = 0f;
        for (WarRecord r : g.fronts) {
            if (key == 3) {
                int l = g.leftSide(r);
                pl += Score.total(r, l);
                pr += Score.total(r, 1 - l);
            } else {
                sum += value(r, key, now);
            }
        }
        return key == 3 ? lead(pl, pr) : sum;
    }

    /** How far the stronger side's share of the war score is from even; 0 with nothing to report. */
    private static double lead(float a, float b) {
        float share = Score.share(a, b);
        return Float.isNaN(share) ? 0 : Math.abs(share - 0.5f);
    }

    private static void cardRow(CustomPanelAPI body, TooltipMakerAPI t, float cw, List<WarRecord> wars,
                                SectorSnapshot s, long now) {
        CustomPanelAPI row = body.createCustomPanel(cw, Card.HEIGHT, null);
        float w = (cw - GAP) / 2f;
        for (int k = 0; k < wars.size(); k++) {
            WarRecord r = wars.get(k);
            // Oriented as on its page: a front of an alliance war keeps its alliance's side.
            CustomPanelAPI card = Card.build(row, w, r, WarPage.leftSideFor(r), s, now, false, marker(r, now),
                    View.Action.of(View.Action.Kind.OPEN, r.id()));
            row.addComponent(card).inTL(k * (w + GAP), 0f);
        }
        t.addCustom(row, GAP);
    }

    private static void endedRow(CustomPanelAPI body, TooltipMakerAPI t, float cw, List<WarRecord> wars,
                                 SectorSnapshot s, long now) {
        CustomPanelAPI row = body.createCustomPanel(cw, Card.HEIGHT, null);
        float w = (cw - GAP) / 2f;
        for (int k = 0; k < wars.size(); k++) {
            WarRecord r = wars.get(k);
            CustomPanelAPI card = Card.build(row, w, r, WarPage.leftSideFor(r), s, now, true, marker(r, now),
                    View.Action.of(View.Action.Kind.OPEN, r.id()));
            row.addComponent(card).inTL(k * (w + GAP), 0f);
        }
        t.addCustom(row, GAP);
    }

    private static String marker(WarRecord r, long now) {
        List<String> parts = new ArrayList<>();
        if (r.id().equals(View.lastOpened)) parts.add("Last viewed");
        // With the new-war touch on, the card's own stamp says so instead.
        if (Wars.isNew(r, now) && !Flourish.on("NewWar")) parts.add("NEW");
        if (Wars.mine(r)) parts.add("Your war");
        if (r.pirate) parts.add("Pirate war");
        return String.join(", ", parts);
    }

    /** "Past 90 days: 1 new war, 1 ceasefire or treaty, 3 colonies changed hands." for the header. */
    static Phrase recent(WarData d, long now) {
        long window = Settings.recentDays() * WarData.DAY;
        Phrase p = new Phrase();
        if (now - d.since < window) {
            p.add("Since records began on ").hl(Fmt.date(d.since), Misc.getHighlightColor()).add(": ");
        } else {
            p.add(Wars.recentLabel() + ": ");
        }
        return Story.append(p, countsPhrase(counts(d, now - window, now)));
    }

    /**
     * {wars begun, wars ended, colonies captured} logged after {@code from}, of the wars Hostilities shows; the
     * fronts of an alliance war that began or ended on the same day count once, as the page counts it once.
     */
    static int[] counts(WarData d, long from, long now) {
        int[] n = new int[3];
        Set<String> once = new HashSet<>();
        List<WarRecord> all = new ArrayList<>(d.active.values());
        all.addAll(d.archive);
        for (WarRecord r : all) {
            if (!Wars.shown(r, View.showPirates, now)) continue;
            String who = r.group != null ? "G:" + r.group : r.id();
            for (StoryEvent e : r.story) {
                if (e.time <= from) continue;
                if (e.kind == StoryEvent.Kind.BEGAN && r.startKnown && r.start > d.since) {
                    if (once.add("b" + who + "@" + e.time / WarData.DAY)) n[0]++;
                } else if (e.kind == StoryEvent.Kind.ENDED) {
                    if (once.add("e" + who + "@" + e.time / WarData.DAY)) n[1]++;
                } else if (e.kind == StoryEvent.Kind.CAPTURE) {
                    n[2]++;
                }
            }
        }
        return n;
    }

    /** "1 new war, 1 war ended, 3 colonies changed hands." */
    static Phrase countsPhrase(int[] n) {
        return new Phrase().num(n[0]).add(n[0] == 1 ? " new war, " : " new wars, ").num(n[1])
                .add(n[1] == 1 ? " war ended, " : " wars ended, ")
                .num(n[2]).add(n[2] == 1 ? " colony changed hands." : " colonies changed hands.");
    }
}
