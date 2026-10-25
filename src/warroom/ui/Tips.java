package warroom.ui;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.ui.LabelAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI.TooltipCreator;
import com.fs.starfarer.api.util.Misc;
import warroom.Fmt;
import warroom.Score;
import warroom.SectorSnapshot;
import warroom.Settings;
import warroom.data.StoryEvent;
import warroom.data.WarData;
import warroom.data.WarRecord;
import warroom.track.Alliances;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

/** Hover tooltips: a faction's details, and how a war's score and numbers come about. */
final class Tips {
    private Tips() {
    }

    /** A tooltip whose content is built when shown; an error shows a line instead of crashing the game. */
    abstract static class Tip implements TooltipCreator {
        private final float width;

        Tip(float width) {
            this.width = width;
        }

        abstract void fill(TooltipMakerAPI t);

        @Override
        public final void createTooltip(TooltipMakerAPI tooltip, boolean expanded, Object tooltipParam) {
            try {
                fill(tooltip);
            } catch (Throwable t) {
                Global.getLogger(Tips.class).error("War Room: could not build a tooltip", t);
                tooltip.addPara("War Room could not show this; see starsector.log.", Misc.getNegativeHighlightColor(), 0f);
            }
        }

        @Override
        public boolean isTooltipExpandable(Object tooltipParam) {
            return false;
        }

        @Override
        public float getTooltipWidth(Object tooltipParam) {
            return width;
        }
    }

    /**
     * A paragraph from a phrase. The plain addPara does no String.format (checked in the game's code), so any
     * text is safe; the highlights are set afterwards.
     */
    static LabelAPI para(TooltipMakerAPI t, Phrase p, float pad) {
        LabelAPI l = t.addPara(p.text(), pad);
        String[] hl = p.highlights();
        if (hl.length > 0) {
            l.setHighlight(hl);
            l.setHighlightColors(p.colors());
        }
        return l;
    }

    /** A faction: long name, alliance, colonies. */
    static Tip faction(final String id, final SectorSnapshot s) {
        return faction(id, s, false);
    }

    /** With {@code archive}: the name or flag opens the faction's archive, and the tooltip says so. */
    static Tip faction(final String id, final SectorSnapshot s, final boolean archive) {
        return new Tip(360f) {
            @Override
            void fill(TooltipMakerAPI t) {
                FactionAPI f = Wars.faction(id);
                if (f == null) {
                    t.addPara(id, 0f);
                    return;
                }
                String longName = f.isPlayerFaction() ? Fmt.name(f) : f.getDisplayNameLong();
                t.addPara(longName == null || longName.isEmpty() ? Fmt.name(f) : longName, Ui.readable(f), 0f);
                int i = s.indexOf(id);
                if (i >= 0) {
                    para(t, new Phrase().num(s.colonies[i]).add(s.colonies[i] == 1 ? " colony" : " colonies")
                            .add(", total colony size ").num(s.size[i]).add("."), 6f);
                    int[] place = Flourish.on("RankTips") ? rank(s, i) : null;
                    if (place != null) {
                        para(t, new Phrase().hl(Fmt.ordinal(place[0]), Misc.getHighlightColor()).add(" of " + place[1]
                                + " in the Sector by colony size."), 3f).setColor(Misc.getGrayColor());
                    }
                    String al = s.alliance[i] == null ? null : s.alliance[i].getName();
                    t.addPara(al == null ? "No alliance." : "Member of " + al + ".", 3f);
                    // Its wars now; pirates are left out, as nearly everyone is hostile to them.
                    if (!s.pirate[i]) para(t, enemies(s, i), 3f);
                } else {
                    t.addPara("Holds no colonies.", Misc.getGrayColor(), 6f);
                }
                if (archive) t.addPara("Click for its Faction Archive.", Misc.getGrayColor(), 6f);
            }
        };
    }

    /**
     * {place, of how many}: a faction's place among those holding colonies, by colony size and then name, as
     * Standings ranks them; null if it holds none.
     */
    static int[] rank(SectorSnapshot s, int i) {
        if (i < 0 || s.colonies[i] <= 0) return null;
        String name = Wars.name(s.factions.get(i).getId()).toLowerCase();
        int place = 1;
        int of = 0;
        for (int k = 0; k < s.factions.size(); k++) {
            if (s.colonies[k] <= 0) continue;
            of++;
            if (k != i && (s.size[k] > s.size[i] || s.size[k] == s.size[i]
                    && Wars.name(s.factions.get(k).getId()).toLowerCase().compareTo(name) < 0)) place++;
        }
        return new int[]{place, of};
    }

    /** "At war with the Hegemony, Tri-Tachyon and 2 more." or "Not at war.", pirate factions left out. */
    static Phrase enemies(SectorSnapshot s, int i) {
        List<String> ids = new ArrayList<>();
        for (int j : s.enemies(i)) {
            if (!s.pirate[j]) ids.add(s.factions.get(j).getId());
        }
        Phrase p = new Phrase();
        if (ids.isEmpty()) return p.add("Not at war.");
        p.add("At war with ");
        int shown = Math.min(3, ids.size());
        for (int k = 0; k < shown; k++) {
            if (k > 0) p.add(k == shown - 1 && ids.size() == shown ? " and " : ", ");
            p.the(ids.get(k), false);
        }
        if (ids.size() > shown) p.add(" and " + (ids.size() - shown) + " more");
        return p.add(".");
    }

    /** The colonies each side captured in this war, from its log: name, size and date. */
    static Tip captures(final WarRecord r, final int left) {
        return new Tip(420f) {
            @Override
            void fill(TooltipMakerAPI t) {
                Color h = Misc.getHighlightColor();
                for (int k = 0; k < 2; k++) {
                    int side = k == 0 ? left : 1 - left;
                    int total = r.get(WarRecord.CAPTURED, side);
                    List<StoryEvent> taken = new ArrayList<>();
                    for (StoryEvent e : r.story) {
                        if (e.kind == StoryEvent.Kind.CAPTURE && e.side == side) taken.add(e);
                    }
                    para(t, new Phrase().the(r.id(side), true).add(total == 0 ? " captured no colonies."
                            : total == 1 ? " captured 1 colony:" : " captured " + total + " colonies:"), k == 0 ? 0f : 10f);
                    for (StoryEvent e : taken) {
                        para(t, new Phrase().add("    ").hl(e.text == null ? "a colony" : e.text, h).add(" (size ").num(e.n(0))
                                .add("), " + Fmt.date(e.time)), 2f);
                    }
                    if (taken.size() < total) {
                        t.addPara("    and " + (total - taken.size()) + " more, older than the war log keeps.", Misc.getGrayColor(), 2f);
                    }
                }
            }
        };
    }

    /** The colonies each alliance captured over all fronts of an alliance war: name, size, who took it, when. */
    static Tip captures(final Wars.AllianceWar g) {
        return new Tip(480f) {
            @Override
            void fill(TooltipMakerAPI t) {
                Color h = Misc.getHighlightColor();
                for (int k = 0; k < 2; k++) {
                    int total = g.sum(WarRecord.CAPTURED, k);
                    List<Object[]> taken = new ArrayList<>();
                    for (WarRecord r : g.fronts) {
                        int side = k == 0 ? g.leftSide(r) : 1 - g.leftSide(r);
                        for (StoryEvent e : r.story) {
                            if (e.kind == StoryEvent.Kind.CAPTURE && e.side == side) taken.add(new Object[]{e, r});
                        }
                    }
                    taken.sort((x, y) -> Long.compare(((StoryEvent) x[0]).time, ((StoryEvent) y[0]).time));
                    para(t, new Phrase().hl(g.name[k], h).add(total == 0 ? " captured no colonies."
                            : total == 1 ? " captured 1 colony:" : " captured " + total + " colonies:"), k == 0 ? 0f : 10f);
                    int shown = Math.min(20, taken.size());
                    for (int i = 0; i < shown; i++) {
                        StoryEvent e = (StoryEvent) taken.get(i)[0];
                        WarRecord r = (WarRecord) taken.get(i)[1];
                        para(t, new Phrase().add("    ").hl(e.text == null ? "a colony" : e.text, h).add(" (size ").num(e.n(0))
                                .add("), by ").the(r.id(e.side), false).add(", " + Fmt.date(e.time)), 2f);
                    }
                    if (shown < total) {
                        t.addPara("    and " + (total - shown) + " more" + (taken.size() > shown ? "." : ", older than the war logs keep."),
                                Misc.getGrayColor(), 2f);
                    }
                }
            }
        };
    }

    /** What the Chronicle logged since your last look, newest first (at most 15 entries). */
    static Tip since(final long from, final long now) {
        return new Tip(600f) {
            @Override
            void fill(TooltipMakerAPI t) {
                t.addPara("Since your last look, on " + Fmt.date(from) + ":", Misc.getGrayColor(), 0f);
                List<Phrase> lines = ArchiveView.since(WarData.get(), from, now, View.showPirates);
                if (lines.isEmpty()) {
                    t.addPara("Nothing logged.", Misc.getGrayColor(), 6f);
                    return;
                }
                int shown = Math.min(15, lines.size());
                for (int i = 0; i < shown; i++) para(t, lines.get(i), i == 0 ? 6f : 3f);
                if (lines.size() > shown) {
                    t.addPara("And " + (lines.size() - shown) + " more in the Archive's Chronicle.", Misc.getGrayColor(), 6f);
                }
            }
        };
    }

    /** How the war score behind the bar comes about, per side. */
    static Tip score(final WarRecord r, final int left) {
        return new Tip(440f) {
            @Override
            void fill(TooltipMakerAPI t) {
                t.addPara("War score: who holds the advantage", Misc.getBasePlayerColor(), 0f);
                for (int k = 0; k < 2; k++) {
                    int side = k == 0 ? left : 1 - left;
                    String id = r.id(side);
                    int pts = Math.round(Score.total(r, side));
                    Phrase p = new Phrase().faction(id).add(": ").num(pts).add(pts == 1 ? " point" : " points");
                    para(t, p, 8f);
                    int share = Math.round(100f * Score.territoryShare(r, side));
                    para(t, points(new Phrase().add("    - Colonies captured or bombarded: "), Score.territory(r, side))
                            .add(" (").num(share).add("% of enemy colony size at war start)"), 2f);
                    para(t, points(new Phrase().add("    - Enemy ships destroyed: "), Score.fleets(r, side))
                            .add(" (").num(r.fpLost(1 - side)).add(" FP)"), 2f);
                    para(t, points(new Phrase().add("    - Raids won, invasions repelled: "), Score.raids(r, side)), 2f);
                    para(t, points(new Phrase().add("    - Reported battles won: "), Score.engagements(r, side)), 2f);
                }
                t.addPara("Captured colonies count most, more so against smaller factions. Each side starts with "
                        + Math.round(Settings.scoreHeadStart()) + " hidden points, so one skirmish cannot swing the bar. "
                        + "See Briefing.", Misc.getGrayColor(), 10f);
                if (!r.startKnown) {
                    para(t, new Phrase().add("Counted from ").hl(Fmt.date(r.start), Misc.getHighlightColor())
                            .add(", when " + Wars.recordsWord(r) + "."), 6f);
                }
            }
        };
    }

    private static Phrase points(Phrase p, float v) {
        int n = Math.round(v);
        return p.num(n).add(n == 1 ? " point" : " points");
    }

    /** How the fight and loss numbers come about: seen battles, off-screen engagements, estimates. */
    static Tip fights(final WarRecord r, final int left) {
        return new Tip(440f) {
            @Override
            void fill(TooltipMakerAPI t) {
                int l = left;
                int rt = 1 - left;
                para(t, new Phrase().add("Confirmed battles won: ").faction(r.id(l)).add(" ").num(r.get(WarRecord.WON, l))
                        .add(", ").faction(r.id(rt)).add(" ").num(r.get(WarRecord.WON, rt)), 0f);
                para(t, new Phrase().add("Reported battles won: ").faction(r.id(l)).add(" ")
                        .num(r.get(WarRecord.ENGAGEMENTS, l)).add(", ").faction(r.id(rt)).add(" ")
                        .num(r.get(WarRecord.ENGAGEMENTS, rt)), 3f);
                t.addPara("Reported battles were fought away from your fleet and are known from comm chatter and news "
                        + "reports. Their losses are estimates, marked ~.", Misc.getGrayColor(), 3f);
                for (int k = 0; k < 2; k++) {
                    int side = k == 0 ? l : rt;
                    int ships = r.get(WarRecord.SHIPS, side);
                    para(t, new Phrase().the(r.id(side), true).add(" lost ").num(ships).add(ships == 1 ? " ship (" : " ships (")
                            .num(r.get(WarRecord.FP, side)).add(" FP) in confirmed battles and about ")
                            .num(Math.round(r.get(WarRecord.SHIPS_EST, side) / 10f)).add(" (")
                            .num(r.get(WarRecord.FP_EST, side)).add(" FP) in reported ones."), 8f);
                }
                for (int k = 0; k < 2; k++) {
                    int side = k == 0 ? l : rt;
                    int you = r.get(WarRecord.PLAYER_FP, side);
                    if (you > 0) {
                        para(t, new Phrase().add("Your fleet destroyed ").num(you).add(" FP of enemy ships fighting alongside ")
                                .the(r.id(side), false).add("."), 8f);
                    }
                }
            }
        };
    }

    /** Alliance strength now: colony size and Nexerelin reserves of each alliance's members. */
    static Tip strength(final String leftName, final String rightName, final float[] size,
                                   final float[] reserves) {
        return new Tip(400f) {
            @Override
            void fill(TooltipMakerAPI t) {
                t.addPara("Strength estimate: half colony size, half reserves", Misc.getBasePlayerColor(), 0f);
                Color h = Misc.getHighlightColor();
                t.addPara("Total colony size: %s vs %s", 8f, h, Fmt.num(size[0]), Fmt.num(size[1]));
                t.addPara("Reserves (fleet pool plus ground pool): %s vs %s", 3f, h, Fmt.compact(reserves[0]),
                        Fmt.compact(reserves[1]));
                if (reserves[0] + reserves[1] <= 0f) {
                    t.addPara("With no reserves on either side, colony size alone decides.", Misc.getGrayColor(), 3f);
                }
                t.addPara(leftName + " on the left, " + rightName + " on the right.", Misc.getGrayColor(), 8f);
            }
        };
    }

    static Tip text(final String text) {
        return new Tip(360f) {
            @Override
            void fill(TooltipMakerAPI t) {
                t.addPara(text, 0f);
            }
        };
    }

    /** An alliance: its members now. */
    static Tip alliance(final String allianceId, final String name) {
        return alliance(allianceId, name, new ArrayList<String>());
    }

    /** {@code fallback}: the members to list if Nexerelin doesn't know the alliance (the demo's made-up ones). */
    static Tip alliance(final String allianceId, final String name, final List<String> fallback) {
        return alliance(allianceId, name, fallback, false);
    }

    /** With {@code archive}: the name opens the alliance's archive, and the tooltip says so. */
    static Tip alliance(final String allianceId, final String name, final List<String> fallback, final boolean archive) {
        return new Tip(360f) {
            @Override
            void fill(TooltipMakerAPI t) {
                t.addPara(name, Misc.getBasePlayerColor(), 0f);
                Phrase p = new Phrase().add("Members: ");
                boolean first = true;
                List<String> members = Alliances.byId(allianceId) == null ? fallback : Alliances.members(allianceId);
                for (String m : members) {
                    if (!first) p.add(", ");
                    p.faction(m);
                    first = false;
                }
                if (first) p.add("none left");
                para(t, p.add("."), 6f);
                if (archive) t.addPara("Click for its Alliance Archive.", Misc.getGrayColor(), 6f);
            }
        };
    }
}
