package warroom.track;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.comm.IntelInfoPlugin;
import exerelin.campaign.DiplomacyManager;
import exerelin.campaign.DiplomacyManager.DiplomacyEventDef;
import exerelin.campaign.ExerelinReputationAdjustmentResult;
import exerelin.campaign.intel.diplomacy.DiplomacyIntel;
import warroom.data.WarData;
import warroom.data.WarRecord;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * Tells a ceasefire from a peace treaty. Nexerelin makes both as diplomacy events and files a diplomacy report
 * for each, kept as a running script even when your filters hide it. Each check notes the reports it has not
 * seen yet; only one that ended hostility counts (an offer accepted later files one too). If the war it ended
 * is still on record as going on, the report waits for that war to close (at this check or the next); if the
 * war was closed a moment before the report was filed (Nexerelin can hold an alliance vote first), the war is
 * given its kind now. Right after loading, only reports dated within the last few days count. Read-only.
 */
final class Peace {
    private static final long KEEP_DAYS = 3L;

    /** A peace report waiting for its war to close: its tags (which name both factions), the kind, its date. */
    private static final class Report {
        final Set<String> tags;
        final int kind;
        final long time;

        Report(Set<String> tags, int kind, long time) {
            this.tags = tags;
            this.kind = kind;
            this.time = time;
        }
    }

    private static final Map<Object, Boolean> seen = new WeakHashMap<>();
    private static final List<Report> waiting = new ArrayList<>();
    private static boolean primed;

    private Peace() {
    }

    static void reset() {
        seen.clear();
        waiting.clear();
        primed = false;
    }

    /** Notes the reports filed since the last call. */
    static void scan(WarData d, long now) {
        waiting.removeIf(r -> now - r.time > KEEP_DAYS * WarData.DAY);
        String treaty = eventName("peace_treaty");
        String ceasefire = eventName("ceasefire");
        if (treaty != null || ceasefire != null) {
            Set<Object> found = Collections.newSetFromMap(new IdentityHashMap<>());
            for (EveryFrameScript s : Global.getSector().getScripts()) {
                if (s instanceof DiplomacyIntel) found.add(s);
            }
            for (IntelInfoPlugin i : Global.getSector().getIntelManager().getIntel(DiplomacyIntel.class)) found.add(i);
            for (Object o : found) {
                if (seen.put(o, Boolean.TRUE) == null) note(d, (DiplomacyIntel) o, treaty, ceasefire, now, !primed);
            }
        }
        primed = true;
    }

    private static void note(WarData d, DiplomacyIntel report, String treaty, String ceasefire, long now,
                             boolean afterLoad) {
        ExerelinReputationAdjustmentResult rep = report.getReputation();
        if (rep == null || !rep.wasHostile || rep.isHostile) return;
        String name = report.getName();
        int kind = name == null ? WarRecord.PEACE_UNKNOWN
                : treaty != null && name.endsWith(treaty) ? WarRecord.PEACE_TREATY
                : ceasefire != null && name.endsWith(ceasefire) ? WarRecord.PEACE_CEASEFIRE : WarRecord.PEACE_UNKNOWN;
        if (kind == WarRecord.PEACE_UNKNOWN) return;
        Long ts = report.getPlayerVisibleTimestamp();
        if (afterLoad && (ts == null || now - ts > KEEP_DAYS * WarData.DAY)) return;
        long time = ts != null ? ts : now;
        Set<String> tags = report.getIntelTags(null);
        if (tags == null) return;
        for (WarRecord r : d.active.values()) {
            if (!tags.contains(r.a) || !tags.contains(r.b)) continue;
            if (time >= r.start) waiting.add(new Report(new HashSet<>(tags), kind, time));
            return;
        }
        WarRecord closed = justClosed(d, tags, time);
        if (closed != null) closed.setPeaceKind(kind);
    }

    /**
     * How the peace that closes this war came about, if a report for it is waiting; the report is used up
     * either way.
     */
    static int take(String a, String b, long warStart) {
        for (int i = waiting.size() - 1; i >= 0; i--) {
            Report r = waiting.get(i);
            if (r.tags.contains(a) && r.tags.contains(b)) {
                waiting.remove(i);
                return r.time >= warStart ? r.kind : WarRecord.PEACE_UNKNOWN;
            }
        }
        return WarRecord.PEACE_UNKNOWN;
    }

    /** The latest war between the report's factions that closed in peace around the report's date, kind not known. */
    private static WarRecord justClosed(WarData d, Set<String> tags, long time) {
        WarRecord best = null;
        for (WarRecord r : d.archive) {
            if (r.endReason != WarRecord.END_PEACE || r.peaceKind() != WarRecord.PEACE_UNKNOWN) continue;
            if (!tags.contains(r.a) || !tags.contains(r.b)) continue;
            if (time < r.start || Math.abs(time - r.end) > KEEP_DAYS * WarData.DAY) continue;
            if (best == null || r.end > best.end) best = r;
        }
        return best;
    }

    private static String eventName(String id) {
        DiplomacyEventDef def = DiplomacyManager.getEventByStage(id);
        return def == null || def.name == null || def.name.isEmpty() ? null : def.name;
    }
}
