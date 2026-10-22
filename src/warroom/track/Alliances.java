package warroom.track;

import exerelin.campaign.AllianceManager;
import exerelin.campaign.alliances.Alliance;

import java.util.ArrayList;
import java.util.List;

/**
 * Read-only access to Nexerelin's alliances by their stable id ({@code Alliance.uuId}, which survives
 * renames; merged-away and dissolved alliances keep resolving, with no members). Never by name.
 */
public final class Alliances {
    private Alliances() {
    }

    /** The alliance a faction is in now; your own faction counts as your employer's. */
    public static Alliance of(String factionId) {
        try {
            if (factionId == null || AllianceManager.getManager() == null) return null;
            Alliance al = AllianceManager.getFactionAlliance(factionId);
            if (al == null && "player".equals(factionId)) al = AllianceManager.getPlayerAlliance(true);
            return al;
        } catch (Throwable t) {
            return null;
        }
    }

    static String idOf(String factionId) {
        Alliance al = of(factionId);
        return al == null ? null : al.uuId;
    }

    public static Alliance byId(String id) {
        try {
            return id == null || AllianceManager.getManager() == null ? null : AllianceManager.getAllianceByUUID(id);
        } catch (Throwable t) {
            return null;
        }
    }

    /** The alliance's current name, or the fallback if it can't be found. */
    public static String name(String id, String fallback) {
        Alliance al = byId(id);
        String n = al == null ? null : al.getName();
        return n == null || n.isEmpty() ? fallback : n;
    }

    /** Its members now, sorted the Nexerelin way; empty for a dissolved alliance. */
    public static List<String> members(String id) {
        Alliance al = byId(id);
        if (al == null) return new ArrayList<>();
        try {
            return al.getMembersSorted();
        } catch (Throwable t) {
            return new ArrayList<>(al.getMembersCopy());
        }
    }

    static List<Alliance> all() {
        try {
            if (AllianceManager.getManager() == null) return new ArrayList<>();
            return new ArrayList<>(AllianceManager.getAllianceList());
        } catch (Throwable t) {
            return new ArrayList<>();
        }
    }
}
