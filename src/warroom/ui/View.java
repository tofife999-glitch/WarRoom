package warroom.ui;

import com.fs.starfarer.api.ui.ScrollPanelAPI;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * What the War Room page shows right now: the Active wars list or one war's page, and the way back.
 * Session-only plain values, reset whenever a save is loaded; never saved.
 */
public final class View {
    /** A button's job and its target (a war id, or "G:" plus an alliance war key). */
    public static final class Action {
        public enum Kind { OPEN, BACK, PREV, NEXT, PIRATES, MORE_FRONTS, SHOW_ALL, PROFILE, OPEN_INTEL, MAP, ALL_INTEL, TAB, ZOOM, FILTER, MORE, ALL_FACTIONS,
            FACTION_ARCHIVE, ALLIANCE_ARCHIVE, ARCHIVE_BACK, SORT, STANDINGS_VIEW, INTERCEPT }

        public final Kind kind;
        public final String target;
        public final Object payload;

        Action(Kind kind, String target, Object payload) {
            this.kind = kind;
            this.target = target;
            this.payload = payload;
        }

        static Action of(Kind kind) {
            return new Action(kind, null, null);
        }

        static Action of(Kind kind, String target) {
            return new Action(kind, target, null);
        }

        static Action of(Kind kind, String target, Object payload) {
            return new Action(kind, target, payload);
        }
    }

    /** The report's tabs. */
    enum Tab { HOSTILITIES, ARCHIVE, STANDINGS, BRIEFING }

    static Tab tab = Tab.HOSTILITIES;
    /** The Archive timeline's span in cycles; 0 for the smallest that covers the records, -1 for all. */
    static int zoom;
    /** The Chronicle's filter (see ArchiveView.FILTERS) and how many entries it shows. */
    static int chronicleFilter;
    static int chronicleLimit = 100;
    /** The faction or alliance the Archive is narrowed to (a Faction or Alliance Archive), or null for all. */
    static String archiveFaction;
    static String archiveAlliance;
    static String archiveAllianceName;
    /** The scroller of the tab's list while it is shown, to remember the scroll position when a war is opened. */
    static WeakReference<ScrollPanelAPI> scroller = new WeakReference<>(null);

    /** The page being shown: null for the tab's own list, else a war ref. */
    static String page;
    /** Pages to go back to (older first). */
    static final List<String> back = new ArrayList<>();
    /** The order Prev and Next walk through, set by the page the war was opened from. */
    static final List<String> order = new ArrayList<>();
    /** Scroll positions to restore: of the Active wars list, and of each page in the back list. */
    static float listScroll = -1f;
    static final List<Float> backScroll = new ArrayList<>();
    /** The walk order of each page in the back list, restored with it. */
    static final List<List<String>> backOrder = new ArrayList<>();
    /** Applied once by the next build of the list. */
    static float pendingScroll = -1f;
    static boolean showPirates;
    static boolean showAll;
    static final Set<String> expanded = new HashSet<>();
    /** The ref of the war last opened, highlighted on the list after coming back. */
    static String lastOpened;
    /**
     * The sorts: the Standings' column and the Hostilities tab's key (ActiveView.SORTS), -1 for the usual order;
     * {@code down} means least first.
     */
    static int standingsSort = -1;
    static boolean standingsDown;
    static int allianceSort = -1;
    static boolean allianceDown;
    static int warSort = -1;
    static boolean warDown;
    /** Whether Standings shows the alliances rather than the factions. */
    static boolean standingsAlliances;

    /** A view to come back to: the tab, the page and its ways back, the Archive's settings and the scroll position. */
    private static final class Spot {
        final Tab tab = View.tab;
        final String page = View.page;
        final List<String> back = new ArrayList<>(View.back);
        final List<Float> backScroll = new ArrayList<>(View.backScroll);
        final List<List<String>> backOrder = new ArrayList<>();
        final List<String> order = new ArrayList<>(View.order);
        final float listScroll = View.listScroll;
        final float scroll = scrollNow();
        final String faction = archiveFaction;
        final String alliance = archiveAlliance;
        final String allianceName = archiveAllianceName;
        final int zoom = View.zoom;
        final int chronicleFilter = View.chronicleFilter;
        final int chronicleLimit = View.chronicleLimit;

        Spot() {
            for (List<String> o : View.backOrder) backOrder.add(new ArrayList<>(o));
        }

        void restore() {
            View.tab = tab;
            View.page = page;
            View.back.clear();
            View.back.addAll(back);
            View.backScroll.clear();
            View.backScroll.addAll(backScroll);
            View.backOrder.clear();
            View.backOrder.addAll(backOrder);
            View.order.clear();
            View.order.addAll(order);
            View.listScroll = listScroll;
            View.pendingScroll = scroll;
            archiveFaction = faction;
            archiveAlliance = alliance;
            archiveAllianceName = allianceName;
            View.zoom = zoom;
            View.chronicleFilter = chronicleFilter;
            View.chronicleLimit = chronicleLimit;
        }
    }

    /** Where the Back button of a Faction or Alliance Archive opened from the report leads (latest last). */
    private static final List<Spot> returns = new ArrayList<>();

    private View() {
    }

    public static void reset() {
        tab = Tab.HOSTILITIES;
        zoom = 0;
        chronicleFilter = 0;
        chronicleLimit = 100;
        archiveFaction = null;
        archiveAlliance = null;
        archiveAllianceName = null;
        returns.clear();
        scroller = new WeakReference<>(null);
        page = null;
        back.clear();
        order.clear();
        listScroll = -1f;
        backScroll.clear();
        backOrder.clear();
        pendingScroll = -1f;
        showPirates = false;
        showAll = false;
        expanded.clear();
        lastOpened = null;
        standingsSort = -1;
        standingsDown = false;
        allianceSort = -1;
        allianceDown = false;
        warSort = -1;
        warDown = false;
        standingsAlliances = false;
    }

    /** A sort button clicked ("standings", "alliances" or "wars"): most first, then least first, then the usual order. */
    static void sort(String target, int key) {
        boolean factions = "standings".equals(target);
        boolean alliances = "alliances".equals(target);
        int cur = factions ? standingsSort : alliances ? allianceSort : warSort;
        boolean down = factions ? standingsDown : alliances ? allianceDown : warDown;
        int next = cur != key || !down ? key : -1;
        boolean nextDown = cur == key && !down;
        if (factions) {
            standingsSort = next;
            standingsDown = nextDown;
        } else if (alliances) {
            allianceSort = next;
            allianceDown = nextDown;
        } else {
            warSort = next;
            warDown = nextDown;
        }
    }

    static boolean onList() {
        return page == null;
    }

    static float scrollNow() {
        ScrollPanelAPI sp = scroller.get();
        return sp == null ? -1f : sp.getYOffset();
    }

    /** Switches tab: back to its list, at the top; the Archive shows all factions again. */
    static void showTab(Tab t) {
        if (t == null || t == tab && page == null && !narrowed()) return;
        tab = t;
        clearNarrowing();
        page = null;
        back.clear();
        backScroll.clear();
        backOrder.clear();
        order.clear();
        listScroll = -1f;
        pendingScroll = -1f;
    }

    /** Whether the Archive is narrowed to one faction or alliance. */
    static boolean narrowed() {
        return archiveFaction != null || archiveAlliance != null;
    }

    /** The whole Archive again, with no way back to where a narrowed one was opened from. */
    static void clearNarrowing() {
        archiveFaction = null;
        archiveAlliance = null;
        archiveAllianceName = null;
        returns.clear();
    }

    /** Opens a faction's archive from the game's Factions screen: at the top, nothing to go back to. */
    static void openFactionArchive(String factionId) {
        openArchive(factionId, null, null);
    }

    /** Opens a faction's or an alliance's archive from inside the report; its Back button returns here. */
    static void openArchiveFromReport(String factionId, String allianceId, String allianceName) {
        Spot here = new Spot();
        List<Spot> before = new ArrayList<>(returns);
        openArchive(factionId, allianceId, allianceName);
        returns.addAll(before);
        returns.add(here);
    }

    static boolean canGoBackFromArchive() {
        return !returns.isEmpty();
    }

    static void archiveBack() {
        if (!returns.isEmpty()) returns.remove(returns.size() - 1).restore();
    }

    /** The Archive narrowed to one faction or alliance, at the top, with its whole span and every kind of entry. */
    private static void openArchive(String factionId, String allianceId, String allianceName) {
        showTab(Tab.ARCHIVE);
        clearNarrowing();
        archiveFaction = factionId;
        archiveAlliance = allianceId;
        archiveAllianceName = allianceName;
        zoom = 0;
        chronicleFilter = 0;
        chronicleLimit = 100;
        pendingScroll = -1f;
    }

    /** Applies the remembered scroll position to the list just built, once. */
    static void restoreScroll(ScrollPanelAPI sp) {
        scroller = new WeakReference<>(sp);
        if (pendingScroll >= 0f && sp != null) sp.setYOffset(pendingScroll);
        pendingScroll = -1f;
    }

    /** Opens a war page from the list (or from another page), remembering how to come back. */
    static void open(String ref, List<String> walk, float currentScroll) {
        if (page == null) listScroll = currentScroll;
        back.add(page == null ? "" : page);
        backScroll.add(currentScroll);
        backOrder.add(new ArrayList<>(order));
        if (walk != null) {
            order.clear();
            order.addAll(walk);
        }
        page = ref;
        lastOpened = ref;
    }

    static void goBack() {
        if (back.isEmpty()) {
            page = null;
            pendingScroll = listScroll;
            return;
        }
        String prev = back.remove(back.size() - 1);
        float scroll = backScroll.isEmpty() ? -1f : backScroll.remove(backScroll.size() - 1);
        if (!backOrder.isEmpty()) {
            List<String> o = backOrder.remove(backOrder.size() - 1);
            order.clear();
            order.addAll(o);
        }
        page = prev.isEmpty() ? null : prev;
        pendingScroll = page == null ? listScroll : scroll;
    }

    /** Moves to the neighbouring war in the walk order, wrapping around. */
    static void step(int dir) {
        if (page == null || order.isEmpty()) return;
        int i = order.indexOf(page);
        if (i < 0) return;
        int n = order.size();
        page = order.get(((i + dir) % n + n) % n);
        lastOpened = page;
    }
}
