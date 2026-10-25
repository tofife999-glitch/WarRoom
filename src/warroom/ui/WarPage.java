package warroom.ui;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.comm.IntelInfoPlugin;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.ui.Alignment;
import com.fs.starfarer.api.ui.ButtonAPI;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.ui.UIComponentAPI;
import com.fs.starfarer.api.util.Misc;
import exerelin.campaign.DiplomacyManager;
import warroom.Fmt;
import warroom.Score;
import warroom.SectorSnapshot;
import warroom.data.Momentum;
import warroom.data.StoryEvent;
import warroom.data.WarData;
import warroom.data.WarRecord;
import warroom.track.WarTracker;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * One war's own page (a single war, or an alliance war with its fronts): navigation and jumps on top, the
 * story on the left, the numbers on the right. Past wars show what was recorded, with "at start" and "at
 * end" instead of live strength.
 */
final class WarPage {
    private static final float PAD = 10f;
    private static final float NAV_H = 28f;
    private static final float LEFT_SHARE = 0.58f;

    private WarPage() {
    }

    static void build(CustomPanelAPI body, float w, float h, String ref, SectorSnapshot s, long now) {
        if (ref.startsWith("G:")) {
            String key = ref.substring(2);
            Wars.AllianceWar g = findGroup(key);
            if (g == null) {
                // Its alliances merged, and its fronts went to an older alliance war's key.
                String now2 = WarData.get().groupNow(key);
                if (now2 != null && !now2.equals(key)) {
                    g = findGroup(now2);
                    if (g != null) {
                        View.page = "G:" + now2;
                        int i = View.order.indexOf(ref);
                        if (i >= 0) View.order.set(i, View.page); // keeps Prev and Next
                    }
                }
            }
            if (g != null) {
                alliancePage(body, w, h, g, s, now);
                return;
            }
        } else {
            WarRecord r = WarData.get().find(ref);
            if (r != null) {
                warPage(body, w, h, r, s, now);
                return;
            }
        }
        TooltipMakerAPI t = body.createUIElement(w, h, false);
        nav(t, w, null);
        t.addPara("No record of this war remains. The Archive keeps the last " + WarData.ARCHIVE_CAP + " ended wars, plus "
                + WarData.ARCHIVE_PIRATE_CAP + " with pirate factions.", Misc.getGrayColor(), NAV_H + 20f);
        body.addUIElement(t).inTL(0f, 0f);
    }

    /** An alliance war by key: its fronts going on and ended. */
    static Wars.AllianceWar findGroup(String key) {
        WarData d = WarData.get();
        List<WarRecord> fronts = new ArrayList<>();
        for (WarRecord r : d.active.values()) {
            if (key.equals(r.group)) fronts.add(r);
        }
        for (WarRecord r : d.archive) {
            if (key.equals(r.group)) fronts.add(r);
        }
        return fronts.isEmpty() ? null : Wars.group(key, fronts);
    }

    /** Back, Prev and Next on the left; returns nothing. */
    private static void nav(TooltipMakerAPI t, float w, Phrase info) {
        Ui.button(t, "Back", View.Action.of(View.Action.Kind.BACK), 0f, 0f, 80f, 24f);
        boolean walk = View.order.size() > 1 && View.order.contains(View.page);
        if (walk) {
            Ui.button(t, "Prev", View.Action.of(View.Action.Kind.PREV), 86f, 0f, 80f, 24f);
            Ui.button(t, "Next", View.Action.of(View.Action.Kind.NEXT), 172f, 0f, 80f, 24f);
        }
        if (info != null) {
            Ui.text(t, info.text(), Misc.getTextColor(), 270f, 4f, w - 270f - 130f, 2, Alignment.TL,
                    info.highlights(), info.colors());
        }
    }

    // ------------------------------------------------------------------ a single war

    private static void warPage(CustomPanelAPI body, float w, float h, WarRecord r, SectorSnapshot s, long now) {
        int left = leftSideFor(r);
        int right = 1 - left;
        FactionAPI fl = Wars.faction(r.id(left));
        FactionAPI fr = Wars.faction(r.id(right));
        Color cl = fl == null ? Misc.getGrayColor() : Ui.readable(fl);
        Color cr = fr == null ? Misc.getGrayColor() : Ui.readable(fr);
        String ln = Wars.name(r.id(left));
        String rn = Wars.name(r.id(right));

        float headH = 120f;
        TooltipMakerAPI head = body.createUIElement(w, headH, false);
        nav(head, w, Story.since(r, now));
        SectorEntityToken place = mapTarget(r);
        if (place != null) Ui.button(head, "Map", View.Action.of(View.Action.Kind.MAP, null, place), w - 110f, 0f, 110f, 24f);

        // Both sides, facing each other, with their Profile buttons; the bar between them.
        float y = NAV_H + 14f;
        float sideW = w * 0.28f;
        if (fl != null) side(head, fl, cl, 0f, y, sideW, true);
        if (fr != null) side(head, fr, cr, w - sideW, y, sideW, false);
        float barX = sideW + 20f;
        float barW = w - 2f * sideW - 40f;
        float share = Score.share(Score.total(r, left), Score.total(r, right));
        UIComponentAPI bar = Card.bar(head, barX, y + 4f, barW, share, cl, cr);
        Ui.tooltip(head, bar, Tips.score(r, left));
        Phrase v = Wars.verdict(share, ln, rn, cl, cr, r.ended());
        Ui.text(head, v.text(), Misc.getTextColor(), barX, y + 22f, barW, 1, Alignment.MID, v.highlights(), v.colors());
        int g = Score.gaining(r, now);
        int gl = g < 0 ? g : (g == left ? 0 : 1);
        Phrase mom = r.ended() ? new Phrase().add("Final assessment") : Wars.momentum(gl, ln, rn, cl, cr);
        Ui.text(head, mom.text(), Misc.getGrayColor(), barX, y + 40f, barW, 1, Alignment.MID, mom.highlights(), mom.colors());
        if (!r.ended() && gl >= 0) Flourish.momentumArrow(head, mom.text(), barX, y + 40f, barW, gl == 1, gl == 0 ? cl : cr);
        body.addUIElement(head).inTL(0f, 0f);

        float bodyH = h - headH;
        float lw = (w - PAD) * LEFT_SHARE;
        float rw = w - PAD - lw;
        TooltipMakerAPI story = body.createUIElement(lw, bodyH, true);
        story.addSectionHeading("War log", Alignment.MID, 0f);
        theatre(story, java.util.Collections.singletonList(r));
        List<StoryEvent> events = new ArrayList<>(r.story);
        for (int i = events.size() - 1; i >= 0; i--) {
            Tips.para(story, Story.dated(r, events.get(i), false), 8f);
        }
        if (r.story.size() >= (r.pirate ? WarRecord.STORY_CAP_PIRATE : WarRecord.STORY_CAP)) {
            story.addPara("Only the most significant events are kept.", Misc.getGrayColor(), 10f);
        }
        body.addUIElement(story).inTL(0f, headH);

        TooltipMakerAPI info = body.createUIElement(rw, bodyH, true);
        if (!r.ended()) happening(body, info, rw - 16f, r.id(left), r.id(right));
        info.addSectionHeading(r.ended() ? "After-action report" : "Military report", Alignment.MID, r.ended() ? 0f : 12f);
        soFar(body, info, rw - 16f, r, left, ln, rn, cl, cr);
        if (r.ended()) {
            info.addSectionHeading("Territory", Alignment.MID, 12f);
            startEnd(body, info, rw - 16f, r, left);
        } else {
            info.addSectionHeading("Strength comparison", Alignment.MID, 12f);
            strengthNow(body, info, rw - 16f, r.id(left), r.id(right), s);
        }
        info.addSectionHeading("Advantage over time", Alignment.MID, 12f);
        leadOverTime(body, info, rw - 16f, r, left, cl, cr, now);
        body.addUIElement(info).inTL(lw + PAD, headH);
    }

    /** The record side shown on the left: as in its alliance war if it has one, else its own fixed side. */
    static int leftSideFor(WarRecord r) {
        if (r.group != null) {
            Wars.AllianceWar g = findGroup(r.group);
            if (g != null && g.fronts.size() > 1) return g.leftSide(r);
        }
        return r.left;
    }

    private static void side(TooltipMakerAPI t, FactionAPI f, Color c, float x, float y, float w, boolean left) {
        float flagH = 30f;
        float fw = Ui.flagWidth(f, flagH);
        float nameW = w - fw - 10f;
        // The flag and name open the faction's archive (a flat button under both).
        float bw = Math.min(w, fw + 10f + Math.min(nameW, t.computeStringWidth(Fmt.name(f)) + 6f) + 8f);
        ButtonAPI fb = Ui.flatButton(t, View.Action.of(View.Action.Kind.FACTION_ARCHIVE, f.getId()),
                left ? x - 4f : x + w + 4f - bw, y - 4f, bw, 38f);
        Ui.tooltip(t, fb, Tips.faction(f.getId(), SectorSnapshot.get(), true));
        if (left) {
            Ui.flag(t, f, x, y, flagH);
            Ui.text(t, Fmt.name(f), c, x + fw + 10f, y + 2f, nameW, 2, Alignment.LMID);
        } else {
            Ui.flag(t, f, x + w - fw, y, flagH);
            Ui.text(t, Fmt.name(f), c, x, y + 2f, nameW, 2, Alignment.RMID);
        }
        IntelInfoPlugin profile = profileOf(f.getId());
        if (profile != null) {
            float bx = left ? x : x + w - 100f;
            ButtonAPI b = Ui.button(t, "Profile", View.Action.of(View.Action.Kind.PROFILE, f.getId(), profile), bx, y + 40f,
                    100f, 22f);
            Ui.tooltip(t, b, Tips.text("Opens the diplomacy profile of " + Fmt.theName(f, false) + ". The intel list is "
                    + "then filtered to War Room and the profile; Show all intel restores it."));
        }
    }

    /**
     * "Theatre: Askonia, Galatia, Hybrasil": the star systems where the logged events happened, the busiest first
     * (at most four). Battles name their system; a colony's system is looked up by its name, if it still exists.
     * With the distances touch on, each also says how far it is from your fleet: "Askonia (14 ly)".
     */
    private static void theatre(TooltipMakerAPI t, List<WarRecord> wars) {
        java.util.Map<String, String> systemOf = new java.util.HashMap<>();
        for (MarketAPI m : Global.getSector().getEconomy().getMarketsCopy()) {
            StarSystemAPI sys = m.getStarSystem();
            if (sys != null && m.getName() != null) systemOf.put(m.getName(), sys.getBaseName());
        }
        java.util.Map<String, Integer> count = new java.util.LinkedHashMap<>();
        for (WarRecord r : wars) {
            for (StoryEvent e : r.story) {
                String place;
                switch (e.kind) {
                    case BATTLE:
                        place = e.text;
                        break;
                    case CAPTURE:
                    case BOMBARD:
                    case RAID:
                    case REPELLED:
                        place = e.text == null ? null : systemOf.get(e.text);
                        break;
                    default:
                        place = null;
                        break;
                }
                if (place == null || place.isEmpty()) continue;
                if (place.equalsIgnoreCase("hyperspace")) place = "hyperspace";
                count.merge(place, 1, Integer::sum);
            }
        }
        if (count.isEmpty()) return;
        List<String> places = new ArrayList<>(count.keySet());
        places.sort(Comparator.comparingInt((String p) -> -count.get(p)));
        java.util.Map<String, StarSystemAPI> systems = new java.util.HashMap<>();
        if (Flourish.on("TheatreDistances")) {
            for (StarSystemAPI sys : Global.getSector().getStarSystems()) systems.put(sys.getBaseName(), sys);
        }
        Phrase p = new Phrase().add("Theatre: ");
        for (int i = 0; i < places.size() && i < 4; i++) {
            if (i > 0) p.add(", ");
            p.hl(places.get(i), Misc.getHighlightColor());
            String far = Flourish.distance(systems.get(places.get(i)));
            if (far != null) p.add(" (" + far + ")");
        }
        Tips.para(t, p, 6f).setColor(Misc.getGrayColor());
    }

    /** A faction's Nexerelin diplomacy profile, if the player can see it in the intel list. */
    static IntelInfoPlugin profileOf(String factionId) {
        try {
            DiplomacyManager dm = DiplomacyManager.getManager();
            IntelInfoPlugin p = dm == null ? null : dm.getDiplomacyProfile(factionId);
            if (p == null || p.isEnding()) return null;
            return WarTracker.visible(p) ? p : null;
        } catch (Throwable t) {
            return null;
        }
    }

    /** Raids and invasions between the two that the player knows about, each with Open. */
    private static void happening(CustomPanelAPI body, TooltipMakerAPI info, float w, String a, String b) {
        info.addSectionHeading("Operations under way", Alignment.MID, 0f);
        List<WarTracker.Operation> ops = WarTracker.operations(a, b);
        if (ops.isEmpty()) {
            info.addPara("No known operations under way.", Misc.getGrayColor(), 8f);
            return;
        }
        for (WarTracker.Operation op : ops) {
            CustomPanelAPI row = body.createCustomPanel(w, 28f, null);
            TooltipMakerAPI in = row.createUIElement(w, 28f, false);
            Phrase p = new Phrase().faction(op.attacker).add(op.invasion ? " invasion of " : " raid on ")
                    .hl(op.target == null ? "a colony" : op.target, Misc.getHighlightColor());
            Ui.text(in, p.text(), Misc.getTextColor(), 0f, 5f, w - 90f, 1, Alignment.LMID, p.highlights(), p.colors());
            Ui.button(in, "Open", View.Action.of(View.Action.Kind.OPEN_INTEL, null, op.intel), w - 80f, 2f, 80f, 22f);
            row.addUIElement(in).inTL(0f, 0f);
            info.addCustom(row, 6f);
        }
    }

    /** One mirrored line: left value | label | right value. */
    private static void mirrored(CustomPanelAPI body, TooltipMakerAPI info, float w, String a, String label, String b,
                                 boolean brightA, boolean brightB, Tips.Tip tip) {
        mirrored(body, info, w, a, label, b, brightA, brightB, tip, false);
    }

    /** {@code sub}: a gray sub-row of the line above it. */
    private static void mirrored(CustomPanelAPI body, TooltipMakerAPI info, float w, String a, String label, String b,
                                 boolean brightA, boolean brightB, Tips.Tip tip, boolean sub) {
        CustomPanelAPI row = body.createCustomPanel(w, 18f, null);
        TooltipMakerAPI in = row.createUIElement(w, 18f, false);
        float lw = Math.min(200f, w * 0.48f);
        float nw = (w - lw) / 2f - 6f;
        Color h = Misc.getHighlightColor();
        Color tc = sub ? Misc.getGrayColor() : Misc.getTextColor();
        Ui.text(in, a, brightA ? h : tc, 0f, 0f, nw, 1, Alignment.RMID);
        Ui.Placed l = Ui.text(in, label, Misc.getGrayColor(), nw + 6f, 0f, lw, 1, Alignment.MID);
        Ui.text(in, b, brightB ? h : tc, nw + lw + 12f, 0f, nw, 1, Alignment.LMID);
        if (tip != null) Ui.tooltip(in, l.comp, tip);
        row.addUIElement(in).inTL(0f, 0f);
        info.addCustom(row, 3f);
    }

    private static void soFar(CustomPanelAPI body, TooltipMakerAPI info, float w, WarRecord r, int left, String ln,
                              String rn, Color cl, Color cr) {
        int right = 1 - left;
        // The names over their columns, up to two lines each.
        CustomPanelAPI head = body.createCustomPanel(w, 34f, null);
        TooltipMakerAPI hin = head.createUIElement(w, 34f, false);
        float lw = Math.min(200f, w * 0.48f);
        float nw = (w - lw) / 2f - 6f;
        Ui.text(hin, ln, cl, 0f, 0f, nw, 2, Alignment.RMID);
        Ui.text(hin, rn, cr, nw + lw + 12f, 0f, nw, 2, Alignment.LMID);
        head.addUIElement(hin).inTL(0f, 0f);
        info.addCustom(head, 8f);
        Tips.Tip fights = Tips.fights(r, left);
        int wa = r.fightsWon(left);
        int wb = r.fightsWon(right);
        mirrored(body, info, w, Fmt.num(wa), "Battles won", Fmt.num(wb), wa > wb, wb > wa, fights);
        mirrored(body, info, w, Fmt.num(r.get(WarRecord.ENGAGEMENTS, left)), "of these, reported",
                Fmt.num(r.get(WarRecord.ENGAGEMENTS, right)), false, false, fights, true);
        int ca = r.get(WarRecord.CAPTURED, left);
        int cb = r.get(WarRecord.CAPTURED, right);
        mirrored(body, info, w, Fmt.num(ca), "Colonies captured", Fmt.num(cb), ca > cb, cb > ca, Tips.captures(r, left));
        counter(body, info, w, r, WarRecord.CAPTURED_SIZE, left, "Colony size captured", true, null);
        int ba = r.get(WarRecord.BOMBARDED_SIZE, left);
        int bb = r.get(WarRecord.BOMBARDED_SIZE, right);
        if (ba + bb > 0) {
            mirrored(body, info, w, Fmt.num(ba), "Colony size destroyed", Fmt.num(bb), false, false,
                    Tips.text("Colony size removed by this side's saturation bombardments. A colony cut from size 6 to "
                            + "5 counts 1; a destroyed size 4 colony counts 4."));
        }
        mirrored(body, info, w, est(r, left) + Fmt.num(r.shipsLost(left)), "Ships lost",
                est(r, right) + Fmt.num(r.shipsLost(right)), false, false, fights);
        mirrored(body, info, w, est(r, left) + Fmt.num(r.fpLost(left)), "Fleet points lost",
                est(r, right) + Fmt.num(r.fpLost(right)), false, false, fights);
        mirrored(body, info, w, launched(r, WarRecord.RAIDS, WarRecord.RAIDS_WON, left), "Raids won",
                launched(r, WarRecord.RAIDS, WarRecord.RAIDS_WON, right), false, false, null);
        mirrored(body, info, w, launched(r, WarRecord.INVASIONS, WarRecord.INVASIONS_WON, left), "Invasions won",
                launched(r, WarRecord.INVASIONS, WarRecord.INVASIONS_WON, right), false, false, null);
        counter(body, info, w, r, WarRecord.REPELLED, left, "Invasions repelled", true, null);
        counter(body, info, w, r, WarRecord.GROUND_WON, left, "Ground battles won", true, null);
        int pa = Math.round(Score.total(r, left));
        int pb = Math.round(Score.total(r, right));
        mirrored(body, info, w, Fmt.num(pa), "War score", Fmt.num(pb), pa > pb, pb > pa, Tips.score(r, left));
        // Where the figures come from; it also explains the ~ mark.
        boolean est = r.lossesEstimated(left) || r.lossesEstimated(right);
        info.addPara("Sources: after-action reports, comm chatter and news reports." + (est ? " ~ marks an estimate." : ""),
                Misc.getGrayColor(), 6f);
    }

    private static String est(WarRecord r, int side) {
        return r.lossesEstimated(side) ? "~" : "";
    }

    /** "2 of 3": won of launched. */
    private static String launched(WarRecord r, int launchedStat, int wonStat, int side) {
        return Fmt.num(r.get(wonStat, side)) + " of " + Fmt.num(r.get(launchedStat, side));
    }

    private static void counter(CustomPanelAPI body, TooltipMakerAPI info, float w, WarRecord r, int stat, int left,
                                String label, boolean higherIsBetter, Tips.Tip tip) {
        int a = r.get(stat, left);
        int b = r.get(stat, 1 - left);
        mirrored(body, info, w, Fmt.num(a), label, Fmt.num(b), higherIsBetter && a > b, higherIsBetter && b > a, tip);
    }

    private static void strengthNow(CustomPanelAPI body, TooltipMakerAPI info, float w, String a, String b, SectorSnapshot s) {
        int ia = s.indexOf(a);
        int ib = s.indexOf(b);
        SectorSnapshot.Strength[] st = s.strength();
        mirrored(body, info, w, ia < 0 ? "none" : Fmt.num(s.colonies[ia]), "Colonies",
                ib < 0 ? "none" : Fmt.num(s.colonies[ib]), false, false, null);
        mirrored(body, info, w, ia < 0 ? Fmt.NONE : Fmt.num(s.size[ia]), "Total colony size",
                ib < 0 ? Fmt.NONE : Fmt.num(s.size[ib]), false, false, null);
        mirrored(body, info, w, ia < 0 ? Fmt.NONE : Fmt.compact(st[ia].fleetPool), "Fleet pool",
                ib < 0 ? Fmt.NONE : Fmt.compact(st[ib].fleetPool), false, false,
                Tips.text("Fleet points held in reserve for patrols, raids, invasions and other fleets. Its colonies "
                        + "refill it. See Briefing."));
        mirrored(body, info, w, ia < 0 ? Fmt.NONE : Fmt.compact(st[ia].troops), "Ground pool",
                ib < 0 ? Fmt.NONE : Fmt.compact(st[ib].troops), false, false,
                Tips.text("Ground forces held in reserve for invasions, raids and garrisons. Its colonies refill it."));
        mirrored(body, info, w, ia < 0 || Float.isNaN(s.weariness[ia]) ? Fmt.NONE : Fmt.num(s.weariness[ia]), "War weariness",
                ib < 0 || Float.isNaN(s.weariness[ib]) ? Fmt.NONE : Fmt.num(s.weariness[ib]), false, false,
                Tips.text("Rises with every day at war and with losses. From 5,000 a faction may seek peace; above 7,500 "
                        + "it starts no new wars. See Briefing."));
    }

    private static void startEnd(CustomPanelAPI body, TooltipMakerAPI info, float w, WarRecord r, int left) {
        int right = 1 - left;
        mirrored(body, info, w, Fmt.num(r.startSize[left]), "Total colony size at start", Fmt.num(r.startSize[right]),
                false, false, null);
        mirrored(body, info, w, Fmt.num(r.endSize[left]), "Total colony size at end", Fmt.num(r.endSize[right]),
                false, false, null);
    }

    /**
     * The war in equal periods: each block in the colour of the side that gained more war score then,
     * brighter for busier periods, gray when nothing happened. Hover a block for its numbers.
     */
    private static void leadOverTime(CustomPanelAPI body, TooltipMakerAPI info, float w, WarRecord r, int left,
                                     Color cl, Color cr, long now) {
        Momentum m = r.momentum;
        long until = r.ended() ? r.end : now;
        float days = Score.days(r.start, until);
        // The stored periods only widen when points come in; after a quiet stretch, join them for display
        // so the strip still runs from the start to today.
        int bd = m.bucketDays();
        while (days >= Momentum.BUCKETS * bd) bd *= 2;
        int join = bd / m.bucketDays();
        int used = Math.max(1, Math.min(Momentum.BUCKETS, (int) (days / bd) + 1));
        float[][] pts = new float[used][2];
        for (int i = 0; i < used; i++) {
            for (int j = i * join; j < Math.min(Momentum.BUCKETS, (i + 1) * join); j++) {
                pts[i][0] += m.points(j, 0);
                pts[i][1] += m.points(j, 1);
            }
        }
        float max = 0f;
        for (int i = 0; i < used; i++) max = Math.max(max, pts[i][0] + pts[i][1]);
        CustomPanelAPI strip = body.createCustomPanel(w, 40f, null);
        TooltipMakerAPI in = strip.createUIElement(w, 40f, false);
        float bw = Math.max(4f, (w - (used - 1) * 2f) / used);
        for (int i = 0; i < used; i++) {
            float pl = pts[i][left];
            float pr = pts[i][1 - left];
            float sum = pl + pr;
            Color c;
            if (sum <= 0f) {
                c = Misc.getGrayColor().darker().darker();
            } else {
                Color base = pl >= pr ? cl : cr;
                float a = 0.35f + 0.65f * (max > 0f ? sum / max : 0f);
                c = new Color(base.getRed(), base.getGreen(), base.getBlue(), Math.round(255 * Math.min(1f, a)));
            }
            UIComponentAPI block = Ui.rect(in, i * (bw + 2f), 0f, bw, 14f, c);
            long from = r.start + (long) i * bd * WarData.DAY;
            long to = Math.min(until, from + (long) bd * WarData.DAY);
            String tip = Fmt.date(from) + " to " + Fmt.date(to) + ": war score " + Wars.name(r.id(left)) + " +"
                    + Math.round(pl) + ", " + Wars.name(r.id(1 - left)) + " +" + Math.round(pr) + ".";
            Ui.tooltip(in, block, Tips.text(tip));
        }
        Ui.text(in, Fmt.date(r.start), Misc.getGrayColor(), 0f, 18f, w / 2f, 1, Alignment.LMID);
        Ui.text(in, r.ended() ? Fmt.date(r.end) : "Today", Misc.getGrayColor(), w / 2f, 18f, w / 2f, 1, Alignment.RMID);
        strip.addUIElement(in).inTL(0f, 0f);
        info.addCustom(strip, 8f);
    }

    /** Where the war was last fought: the colony or system of its latest event that names one. */
    static SectorEntityToken mapTarget(WarRecord r) {
        for (int i = r.story.size() - 1; i >= 0; i--) {
            StoryEvent e = r.story.get(i);
            if (e.text == null) continue;
            if (e.kind == StoryEvent.Kind.BATTLE) {
                for (StarSystemAPI sys : Global.getSector().getStarSystems()) {
                    if (e.text.equals(sys.getBaseName()) && !Boolean.TRUE.equals(sys.getDoNotShowIntelFromThisLocationOnMap())) {
                        SectorEntityToken anchor = sys.getHyperspaceAnchor();
                        if (anchor != null) return anchor;
                    }
                }
            } else if (e.kind == StoryEvent.Kind.CAPTURE || e.kind == StoryEvent.Kind.RAID
                    || e.kind == StoryEvent.Kind.REPELLED || e.kind == StoryEvent.Kind.BOMBARD) {
                for (MarketAPI m : Global.getSector().getEconomy().getMarketsCopy()) {
                    if (!e.text.equals(m.getName()) || m.getPrimaryEntity() == null) continue;
                    if (m.isHidden() && m.getPrimaryEntity().isDiscoverable()) continue;
                    if (r.involves(m.getFactionId())) return m.getPrimaryEntity();
                }
            }
        }
        return null;
    }

    // ------------------------------------------------------------------ an alliance war

    private static void alliancePage(CustomPanelAPI body, float w, float h, Wars.AllianceWar g, SectorSnapshot s, long now) {
        float headH = NAV_H + 14f + AllianceBlock.SUMMARY_H;
        TooltipMakerAPI head = body.createUIElement(w, headH, false);
        long start = g.start();
        Phrase when = new Phrase();
        if (g.ended()) {
            int days = Fmt.days(start, g.end());
            when.add("Lasted ").num(days).add(g.startUnknown() ? "+ days. " : days == 1 ? " day. " : " days. ").add("Ended on ")
                    .hl(Fmt.date(g.end()), Misc.getHighlightColor()).add(".");
        } else {
            int days = Fmt.days(start, now);
            if (g.startUnknown()) {
                when.add("At war since before ").hl(Fmt.date(start), Misc.getHighlightColor()).add(", when records began (")
                        .num(days).add("+ days).");
            } else {
                when.add("At war since ").hl(Fmt.date(start), Misc.getHighlightColor()).add(" (").num(days)
                        .add(days == 1 ? " day)." : " days).");
            }
        }
        nav(head, w, when);
        body.addUIElement(head).inTL(0f, 0f);
        TooltipMakerAPI sum = body.createUIElement(w, AllianceBlock.SUMMARY_H, false);
        AllianceBlock.summary(sum, w, g, s, now, true);
        body.addUIElement(sum).inTL(0f, NAV_H + 14f);

        float bodyH = h - headH - PAD;
        float lw = (w - PAD) * LEFT_SHARE;
        float rw = w - PAD - lw;
        TooltipMakerAPI story = body.createUIElement(lw, bodyH, true);
        story.addSectionHeading("War log, all fronts", Alignment.MID, 0f);
        theatre(story, g.fronts);
        List<Object[]> events = new ArrayList<>();
        for (WarRecord r : g.fronts) {
            for (StoryEvent e : r.story) {
                if (e.kind == StoryEvent.Kind.BEGAN || e.kind == StoryEvent.Kind.ENDED) continue;
                events.add(new Object[]{r, e});
            }
        }
        events.sort(Comparator.comparingLong((Object[] o) -> -((StoryEvent) o[1]).time));
        for (int i = 0; i < events.size() && i < 60; i++) {
            Tips.para(story, Story.dated((WarRecord) events.get(i)[0], (StoryEvent) events.get(i)[1], false), 8f);
        }
        if (events.isEmpty()) story.addPara("Nothing to report since hostilities began.", Misc.getGrayColor(), 8f);
        body.addUIElement(story).inTL(0f, headH + PAD);

        TooltipMakerAPI info = body.createUIElement(rw, bodyH, true);
        info.addSectionHeading("Fronts", Alignment.MID, 0f);
        List<String> walk = new ArrayList<>();
        for (WarRecord r : g.fronts) walk.add(r.id());
        float fw = rw - 16f;
        Color[] c = AllianceBlock.colors(g, s);
        for (WarRecord r : g.fronts) {
            int l = g.leftSide(r);
            CustomPanelAPI row = body.createCustomPanel(fw, 50f, null);
            TooltipMakerAPI in = row.createUIElement(fw, 50f, false);
            Phrase p = new Phrase().faction(r.id(l)).add(" vs ").faction(r.id(1 - l));
            if (r.ended()) p.add(" (ended)");
            Color tc = r.ended() ? Misc.getGrayColor() : Misc.getTextColor();
            Ui.text(in, p.text(), tc, 0f, 0f, fw - 90f, 1, Alignment.LMID, p.highlights(), p.colors());
            float share = Score.share(Score.total(r, l), Score.total(r, 1 - l));
            UIComponentAPI bar = Card.bar(in, 0f, 24f, fw - 90f, share, r.ended() ? c[0].darker() : c[0],
                    r.ended() ? c[1].darker() : c[1]);
            Ui.tooltip(in, bar, Tips.score(r, l));
            Ui.button(in, "Open", View.Action.of(View.Action.Kind.OPEN, r.id(), walk), fw - 80f, 12f, 80f, 24f);
            row.addUIElement(in).inTL(0f, 0f);
            info.addCustom(row, 8f);
        }
        body.addUIElement(info).inTL(lw + PAD, headH + PAD);
    }
}
