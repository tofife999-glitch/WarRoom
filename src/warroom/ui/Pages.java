package warroom.ui;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.BaseCustomUIPanelPlugin;
import com.fs.starfarer.api.ui.Alignment;
import com.fs.starfarer.api.ui.ButtonAPI;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.IntelUIAPI;
import com.fs.starfarer.api.ui.LabelAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.ui.UIComponentAPI;
import com.fs.starfarer.api.util.Misc;
import warroom.Demo;
import warroom.Fmt;
import warroom.SectorSnapshot;
import warroom.data.WarData;
import warroom.data.WarRecord;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

/**
 * Builds the War Room page: the header (title, the report's source line and date, a summary line, the
 * tabs), then the tab's list or a war's page. The body is built into its own panel and only added when
 * complete; if it fails, a note with a way back takes its place instead of the game crashing.
 */
public final class Pages {
    private static final float HEADER_H = 116f;
    /**
     * For the touches that run once (sync sweep, new-war flash, title settle): the view last built, and when War
     * Room was last on screen (a heartbeat on the page notes it every frame). A page counts as opened when the
     * view changes or War Room comes back on screen, not on every click.
     */
    private static String lastView;
    private static long lastSeenAt;
    /** Whether the page being built has just been opened (not merely clicked on). */
    static boolean opening;
    private static final float TAB_W = 130f;

    private Pages() {
    }

    public static void build(CustomPanelAPI panel, float width, float height) {
        long now = Global.getSector().getClock().getTimestamp();
        SectorSnapshot s = null;
        try {
            s = SectorSnapshot.get();
        } catch (Throwable t) {
            Global.getLogger(Pages.class).error("War Room: could not take stock of the sector", t);
        }
        float top = HEADER_H + 8f;
        float bodyH = height - top;
        String viewKey = View.tab + "|" + View.page + "|" + View.archiveFaction + "|" + View.archiveAlliance;
        long wall = System.currentTimeMillis();
        boolean back = wall - lastSeenAt > 1500L;
        boolean opened = back || !viewKey.equals(lastView);
        lastView = viewKey;
        lastSeenAt = wall;
        opening = opened;
        if (back && !Demo.isOn()) {
            // Opening War Room: what is new is counted from the last time it was opened in this save.
            WarData wd = WarData.get();
            View.lookSince = wd.lastLook;
            wd.lastLook = now;
        }
        panel.addComponent(panel.createCustomPanel(1f, 1f, new Heartbeat())).inTL(0f, 0f);
        long t0 = System.nanoTime();
        body(panel, width, bodyH, top, s, now);
        long ms = (System.nanoTime() - t0) / 1000000L;
        if (Demo.isOn()) Global.getLogger(Pages.class).info("War Room demo: built " + View.tab + " " + View.page + " in " + ms + " ms");
        try {
            if (s != null) header(panel, width, s, now, ms, opened, back);
        } catch (Throwable t) {
            Global.getLogger(Pages.class).error("War Room: could not build the header", t);
        }
    }

    /** The tab's list or a war's page under the header; a note with a way back if it fails. */
    private static void body(CustomPanelAPI panel, float width, float bodyH, float top, SectorSnapshot s, long now) {
        try {
            if (s == null) throw new IllegalStateException("no snapshot of the sector");
            CustomPanelAPI body = panel.createCustomPanel(width, bodyH, null);
            if (!View.onList()) WarPage.build(body, width, bodyH, View.page, s, now);
            else if (View.tab == View.Tab.ARCHIVE) ArchiveView.build(body, width, bodyH, s, now);
            else if (View.tab == View.Tab.STANDINGS) StandingsView.build(body, width, bodyH, s, now);
            else if (View.tab == View.Tab.BRIEFING) BriefingView.build(body, width, bodyH);
            else ActiveView.build(body, width, bodyH, s, now);
            panel.addComponent(body).inTL(0f, top);
        } catch (Throwable t) {
            Global.getLogger(Pages.class).error("War Room: could not build the page " + View.tab + " " + View.page, t);
            TooltipMakerAPI err = panel.createUIElement(width, bodyH, false);
            err.addPara("War Room could not show this page: " + t + ". The details are in starsector.log.",
                    Misc.getNegativeHighlightColor(), 0f);
            if (!View.onList()) err.addButton("Back", View.Action.of(View.Action.Kind.BACK), 200f, 24f, 10f);
            panel.addUIElement(err).inTL(0f, top);
        }
    }

    private static void header(CustomPanelAPI panel, float width, SectorSnapshot s, long now, long builtMs, boolean opened,
                               boolean back) {
        WarData d = WarData.get();
        float buttonW = 160f;
        IntelUIAPI ui = panel.getIntelUI();
        boolean subset = ui != null && ui.isShowingCustomIntelSubset();
        // The right-hand column sits below the Show all intel button, so it can reach the right edge.
        float headW = width - 4f;
        TooltipMakerAPI head = panel.createUIElement(width, HEADER_H, false);
        head.setParaOrbitronLarge();
        LabelAPI title = head.addPara("War Room", Misc.getBasePlayerColor(), 0f);
        // Coming back on screen, the title flickers on like a projection.
        if (back && Flourish.on("Hologram")) Fx.settle(panel, head, title);
        float y = title.getPosition().getHeight() + 2f;
        // The report's frame, in small gray print: who sends it (the game's own sender name for your
        // intelligence assessments) and its number; on the right the date it stands at, who it is for and the
        // relay link.
        head.setParaSmallInsignia();
        String source = "Intelligence Assessment - Analysis";
        head.addPara(source, Misc.getGrayColor(), 2f);
        float line = Ui.lineHeight(head);
        if (Flourish.on("Serial")) {
            String no = "No. " + Flourish.serial(now);
            Ui.text(head, no, Flourish.SERIAL, head.computeStringWidth(source) + 14f, y, head.computeStringWidth(no) + 8f, 1,
                    Alignment.LMID);
        }
        List<String> right = new ArrayList<>();
        right.add("As of " + Fmt.date(now));
        if (Flourish.on("Addressee")) right.add(Flourish.addressee());
        boolean relay = Flourish.on("RelayLight");
        boolean link = relay && Flourish.relayLink();
        String via = link && Flourish.on("RelayName") ? Flourish.relayName() : null;
        if (relay) right.add(link ? "Relay link steady" + (via != null ? ": " + via : "") : "Relay link lost");
        float colW = 0f;
        for (String r : right) colW = Math.max(colW, head.computeStringWidth(r) + 8f);
        if (relay) colW += 12f;
        for (int i = 0; i < right.size(); i++) {
            String txt = right.get(i);
            float ry = y + i * line;
            Ui.text(head, txt, Misc.getGrayColor(), headW - colW, ry, colW, 1, Alignment.RMID);
            if (relay && i == right.size() - 1) {
                Fx.pulse(panel, head, link ? Flourish.RELAY_OK : Flourish.RELAY_LOST, headW - head.computeStringWidth(txt) - 12f,
                        ry + (line - 6f) / 2f, 6f, link ? 2.4f : 1.1f);
            }
        }
        head.setParaFontDefault();
        // The summary line (or the demo's), clear of the column on the right.
        Phrase p;
        Color base;
        if (Demo.isOn()) {
            p = new Phrase().hl("DEMO", Misc.getNegativeHighlightColor()).add(" (page built in " + builtMs + " ms): a made-up "
                    + "history; nothing of it is saved. Console: warroom_demo off");
            base = Misc.getHighlightColor();
        } else {
            int wars = warCounts(now)[0];
            int alliances = s.alliances.size();
            p = new Phrase().num(wars).add(wars == 1 ? " active war, " : " active wars, ").num(alliances)
                    .add(alliances == 1 ? " alliance. " : " alliances. ");
            Story.append(p, ActiveView.recent(d, now));
            base = Misc.getGrayColor();
        }
        Ui.text(head, p.text(), base, 0f, y + line + 4f, Math.max(120f, headW - colW - 20f), 1, Alignment.LMID,
                p.highlights(), p.colors());
        if (!Demo.isOn() && View.lookSince != 0) {
            // What happened since War Room was last opened; hovering it lists the events.
            Phrase since = sinceLook(d, View.lookSince, now);
            Ui.Placed sl = Ui.text(head, since.text(), Misc.getGrayColor(), 0f, y + line + 22f, Math.max(120f, headW - colW - 20f),
                    1, Alignment.LMID, since.highlights(), since.colors());
            Ui.tooltip(head, sl.comp, Tips.since(View.lookSince, now));
        }
        float tabY = HEADER_H - 26f;
        tab(head, View.Tab.HOSTILITIES, "Hostilities", "Wars under way.", 0, tabY);
        tab(head, View.Tab.ARCHIVE, "Archive", "Every war on record, and Sector news.", 1, tabY);
        tab(head, View.Tab.STANDINGS, "Standings", "How the factions compare: colonies, territory and wars.", 2, tabY);
        tab(head, View.Tab.BRIEFING, "Briefing", "How the figures in this report are worked out.", 3, tabY);
        if (Flourish.on("TensionTrace")) tension(panel, head, width, d, now, tabY);
        panel.addUIElement(head).inTL(0f, 0f);
        if (opened && Flourish.on("SyncSweep")) {
            TooltipMakerAPI line2 = panel.createUIElement(width, 4f, false);
            Fx.sweep(panel, line2, Misc.getBasePlayerColor(), 0f, 0f, width, 0.9f);
            panel.addUIElement(line2).inTL(0f, HEADER_H + 2f);
        }

        if (subset) {
            TooltipMakerAPI b = panel.createUIElement(buttonW, 30f, false);
            Ui.button(b, "Show all intel", View.Action.of(View.Action.Kind.ALL_INTEL), 0f, 0f, buttonW, 24f);
            Ui.tooltip(b, b.getPrev(), Tips.text("The intel list is filtered to War Room and what you opened from it. "
                    + "Click to restore the full list."));
            panel.addUIElement(b).inTR(0f, 4f);
        }
    }

    /**
     * Sector tension: the war score points of every war shown, month by month over the past 3 cycles (or since
     * records began, if later), as a thin trace at the right of the tabs, with a word for the trend.
     */
    private static void tension(CustomPanelAPI panel, TooltipMakerAPI head, float width, WarData d, long now, float y) {
        long full = 3L * 365L * WarData.DAY;
        long span = Math.min(full, now - d.since);
        if (span < 60L * WarData.DAY) return;
        int n = Math.max(2, Math.min(36, Math.round(span / (WarData.DAY * 365f / 12f))));
        float binDays = span / (float) WarData.DAY / n;
        float[] v = new float[n];
        List<WarRecord> all = new ArrayList<>(d.active.values());
        all.addAll(d.archive);
        for (WarRecord r : all) {
            if (r.pirate && !View.showPirates) continue;
            for (int k = 0; k < n; k++) {
                // The war's own day at the end of month k.
                float days = (now - r.start) / (float) WarData.DAY - (n - 1 - k) * binDays;
                if (days > 0f) v[k] += r.momentum.recent(days, binDays, 0) + r.momentum.recent(days, binDays, 1);
            }
        }
        float total = 0f;
        for (float x : v) total += x;
        String word;
        if (total < 1f) {
            word = "quiet";
        } else {
            int last = Math.min(3, n);
            float recent = 0f;
            for (int k = n - last; k < n; k++) recent += v[k];
            float ratio = (recent / last) / (total / n);
            word = ratio >= 1.3f ? "rising" : ratio <= 0.7f ? "easing" : "steady";
        }
        boolean cycles = span >= full;
        String text = "Sector tension, " + (cycles ? "past 3 cycles" : "since records began") + ": " + word;
        float tw = head.computeStringWidth(text) + 6f;
        float traceW = 120f;
        float tx = width - 4f - tw;
        float x0 = tx - 10f - traceW;
        if (x0 < 4f * (TAB_W + 6f) + 10f) return;
        Ui.rect(head, x0, y + 20f, traceW, 1f, new Color(27, 55, 65));
        Fx.trace(panel, head, Flourish.AMBER, x0, y + 3f, traceW, 16f, v);
        Ui.text(head, text, Misc.getGrayColor(), tx, y + 4f, tw, 1, Alignment.RMID, new String[]{word},
                new Color[]{Flourish.AMBER});
        UIComponentAPI hit = Ui.rect(head, x0, y, width - 4f - x0, 24f, new Color(0, 0, 0, 0));
        Ui.tooltip(head, hit, Tips.text("War score points of every war shown, month by month over the "
                + (cycles ? "past 3 cycles" : "time since records began") + ": fighting, captures, raids and bombardments. "
                + "Rising or easing compares the last 3 months with the average."));
    }

    /** "Since your last look, 12 days ago: 1 new war, 3 colonies changed hands." */
    private static Phrase sinceLook(WarData d, long since, long now) {
        int days = Fmt.days(since, now);
        Phrase p = new Phrase().add(days <= 0 ? "Since your last look earlier today: " : days == 1
                ? "Since your last look yesterday: " : "Since your last look, " + days + " days ago: ");
        int[] n = ActiveView.counts(d, since, now);
        return n[0] + n[1] + n[2] == 0 ? p.add("no wars begun or ended, no colonies taken.") : Story.append(p, ActiveView.countsPhrase(n));
    }

    /** Notes that War Room is on screen, every frame it is. */
    private static final class Heartbeat extends BaseCustomUIPanelPlugin {
        @Override
        public void advance(float amount) {
            lastSeenAt = System.currentTimeMillis();
        }
    }

    /** One tab button; the shown tab is ticked. */
    private static void tab(TooltipMakerAPI head, View.Tab t, String label, String tip, int i, float y) {
        ButtonAPI b = head.addAreaCheckbox(label, View.Action.of(View.Action.Kind.TAB, t.name()), Misc.getBasePlayerColor(),
                Misc.getDarkPlayerColor(), Misc.getBrightPlayerColor(), TAB_W, 24f, 0f);
        b.setChecked(View.tab == t);
        b.getPosition().inTL(i * (TAB_W + 6f), y);
        Ui.tooltip(head, b, Tips.text(tip));
    }

    /** {wars shown on the Hostilities tab, of them yours}, as the tab counts them. */
    public static int[] warCounts(long now) {
        List<Wars.Item> items = Wars.activeItems(WarData.get(), View.showPirates, now);
        int mine = 0;
        for (Wars.Item i : items) {
            if (i.mine()) mine++;
        }
        return new int[]{items.size(), mine};
    }

    /** Carries out a navigation button; the intel then rebuilds the page. */
    @SuppressWarnings("unchecked")
    public static void act(View.Action a) {
        switch (a.kind) {
            case OPEN: {
                List<String> walk = a.payload instanceof List ? (List<String>) a.payload
                        : View.onList() && View.tab == View.Tab.HOSTILITIES ? ActiveView.walk : null;
                View.open(a.target, walk, View.onList() ? View.scrollNow() : -1f);
                Flourish.sound("Report");
                break;
            }
            case INTERCEPT: {
                // The war of the event the intercept line shows at the moment of the click.
                String id = a.payload instanceof ActiveView.Cycle ? ((ActiveView.Cycle) a.payload).current() : null;
                if (id == null) break;
                View.open(id, View.onList() && View.tab == View.Tab.HOSTILITIES ? ActiveView.walk : null,
                        View.onList() ? View.scrollNow() : -1f);
                Flourish.sound("Report");
                break;
            }
            case BACK:
                View.goBack();
                break;
            case PREV:
                View.step(-1);
                break;
            case NEXT:
                View.step(1);
                break;
            case TAB: {
                View.Tab before = View.tab;
                try {
                    View.showTab(View.Tab.valueOf(a.target));
                } catch (IllegalArgumentException ex) {
                    View.showTab(View.Tab.HOSTILITIES);
                }
                if (View.tab != before) Flourish.sound("Tab");
                break;
            }
            case ZOOM:
                View.zoom = a.payload instanceof Integer ? (Integer) a.payload : 0;
                View.pendingScroll = View.scrollNow();
                break;
            case FILTER:
                View.chronicleFilter = a.payload instanceof Integer ? (Integer) a.payload : 0;
                View.chronicleLimit = 100;
                View.pendingScroll = View.scrollNow();
                break;
            case MORE:
                View.chronicleLimit += 100;
                View.pendingScroll = View.scrollNow();
                break;
            case ALL_FACTIONS:
                View.clearNarrowing();
                View.chronicleLimit = 100;
                break;
            case FACTION_ARCHIVE:
                if (a.target != null) View.openArchiveFromReport(a.target, null, null);
                Flourish.sound("Tab");
                break;
            case ALLIANCE_ARCHIVE:
                if (a.target != null) {
                    View.openArchiveFromReport(null, a.target, a.payload instanceof String ? (String) a.payload : null);
                }
                Flourish.sound("Tab");
                break;
            case ARCHIVE_BACK:
                View.archiveBack();
                break;
            case SORT:
                if (a.payload instanceof Integer) View.sort(a.target, (Integer) a.payload);
                View.pendingScroll = View.scrollNow();
                break;
            case STANDINGS_VIEW:
                View.standingsAlliances = Boolean.TRUE.equals(a.payload);
                break;
            case PIRATES:
                View.showPirates = !View.showPirates;
                View.pendingScroll = View.scrollNow();
                break;
            case MORE_FRONTS:
                if (!View.expanded.remove(a.target)) View.expanded.add(a.target);
                View.pendingScroll = View.scrollNow();
                break;
            case SHOW_ALL:
                View.showAll = true;
                View.pendingScroll = View.scrollNow();
                break;
            default:
                break;
        }
    }

    /** Forgets everything about the page shown; called when a save is loaded. */
    public static void reset() {
        lastView = null;
        View.reset();
        ActiveView.walk.clear();
    }
}
