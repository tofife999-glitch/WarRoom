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
