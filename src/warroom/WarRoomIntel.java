package warroom;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.comm.IntelInfoPlugin;
import com.fs.starfarer.api.impl.campaign.intel.BaseIntelPlugin;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.IntelUIAPI;
import com.fs.starfarer.api.ui.SectorMapAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import warroom.track.WarTracker;
import warroom.ui.Pages;
import warroom.ui.View;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * The War Room entry in the Intel screen, under its own tag: the Active wars list, or one war's page.
 * Created anew on every load and never saved (see {@link WarRoom}).
 */
public class WarRoomIntel extends BaseIntelPlugin {
    public static final String TAG = "War Room";

    @Override
    protected String getName() {
        return "War Room";
    }

    @Override
    public String getIcon() {
        return Global.getSettings().getSpriteName("intel", "hostilities");
    }

    @Override
    public Set<String> getIntelTags(SectorMapAPI map) {
        Set<String> tags = super.getIntelTags(map);
        tags.add(TAG);
        return tags;
    }

    @Override
    public IntelSortTier getSortTier() {
        return IntelSortTier.TIER_2;
    }

    @Override
    public String getSortString() {
        return getName();
    }

    @Override
    public boolean shouldRemoveIntel() {
        return false;
    }

    @Override
    public boolean hasSmallDescription() {
        return false;
    }

    @Override
    public boolean hasLargeDescription() {
        return true;
    }

    @Override
    public void setImportant(Boolean important) {
        super.setImportant(important);
        SaveData.important = isImportant();
    }

    /** Sets the important flag from the save data without writing it back. */
    void restoreImportant(boolean important) {
        super.setImportant(important ? Boolean.TRUE : null);
    }

    @Override
    public void notifyPlayerAboutToOpenIntelScreen() {
        SectorSnapshot.invalidate();
        try {
            WarTracker.check(); // so wars and peace since the last daily check show up right away
        } catch (Throwable t) {
            WarRoom.log.error("War Room: could not check for new wars", t);
        }
    }

    @Override
    public void createIntelInfo(TooltipMakerAPI info, ListInfoMode mode) {
        info.addPara(getName(), getTitleColor(mode), 0f);
        try {
            // Counted as the page counts them: an alliance war is one war.
            int[] n = Pages.warCounts(Global.getSector().getClock().getTimestamp());
            Color tc = getBulletColorForMode(mode);
            Color h = Misc.getHighlightColor();
            bullet(info);
            if (Demo.isOn()) info.addPara("Demo: made-up history", Misc.getNegativeHighlightColor(), 3f);
            info.addPara("%s", Demo.isOn() ? 0f : 3f, tc, h, Fmt.plural(n[0], "active war", "active wars"));
            if (n[1] > 0) info.addPara("%s involving you", 0f, tc, h, String.valueOf(n[1]));
            unindent(info);
        } catch (Throwable t) {
            WarRoom.log.error("War Room: could not describe the wars", t);
        }
    }

    @Override
    public void createLargeDescription(CustomPanelAPI panel, float width, float height) {
        Pages.build(panel, width, height);
    }

    @Override
    public void buttonPressConfirmed(Object buttonId, IntelUIAPI ui) {
        if (!(buttonId instanceof View.Action)) {
            super.buttonPressConfirmed(buttonId, ui);
            return;
        }
        View.Action a = (View.Action) buttonId;
        try {
            switch (a.kind) {
                case PROFILE:
                case OPEN_INTEL:
                    jump(ui, a.payload instanceof IntelInfoPlugin ? (IntelInfoPlugin) a.payload : null);
                    return;
                case MAP:
                    if (a.payload instanceof SectorEntityToken) ui.showOnMap((SectorEntityToken) a.payload);
                    return;
                case ALL_INTEL:
                    ui.updateIntelList(true);
                    ui.updateUIForItem(this);
                    return;
                default:
                    Pages.act(a);
                    ui.updateUIForItem(this);
            }
        } catch (Throwable t) {
            WarRoom.log.error("War Room: button failed", t);
        }
    }

    /**
     * Opens another intel item (a diplomacy profile, a raid or invasion) by showing just War Room and that
     * item in the intel list, so War Room stays one click away; "Show all intel" brings the rest back.
     */
    private void jump(IntelUIAPI ui, IntelInfoPlugin target) {
        if (target == null || target == this || !WarTracker.visible(target)) return;
        try {
            List<IntelInfoPlugin> show = new ArrayList<>();
            show.add(this);
            show.add(target);
            Global.getSector().getIntelManager().sortIntel(show);
            ui.updateIntelList(true, show);
            ui.selectItem(target);
        } catch (Throwable t) {
            WarRoom.log.error("War Room: could not open the other intel", t);
            try {
                ui.updateIntelList(true);
                ui.selectItem(this);
                ui.updateUIForItem(this);
            } catch (Throwable ignored) {
                // nothing more to do
            }
        }
    }
}
