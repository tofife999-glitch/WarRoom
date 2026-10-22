package warroom.ui;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.BaseCustomUIPanelPlugin;
import com.fs.starfarer.api.campaign.CampaignUIAPI;
import com.fs.starfarer.api.campaign.CoreUITabId;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.comm.IntelInfoPlugin;
import com.fs.starfarer.api.ui.Alignment;
import com.fs.starfarer.api.ui.ButtonAPI;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.CutStyle;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.ui.UIComponentAPI;
import com.fs.starfarer.api.ui.UIPanelAPI;
import com.fs.starfarer.api.util.Misc;
import org.apache.log4j.Logger;
import warroom.WarRoom;

import java.lang.ref.WeakReference;
import java.util.List;

/**
 * A small "Faction Archive (War Room)" button on the game's own Intel > Factions screen, for the faction shown
 * there; it opens War Room's Archive for that faction. The game has no hook for that screen, so the button is
 * put into the screen's panel found by method and type names. If the screen is not as expected, nothing is
 * added; an error while finding it stops the script for the session. Transient; it only reads the screen and
 * adds the button.
 */
public final class FactionScreen implements EveryFrameScript {
    private static final Logger log = Global.getLogger(FactionScreen.class);
    private static final String LABEL = "Faction Archive (War Room)";
    private static final float BUTTON_H = 20f;
    private static final float PANEL_W = 260f;

    private boolean broken;
    /** The game's panel for the faction shown, which holds the button, and the button's own panel. */
    private WeakReference<Object> host = new WeakReference<>(null);
    private WeakReference<UIComponentAPI> added = new WeakReference<>(null);
    /** The faction whose archive was asked for; opened on the next frame, outside the click. */
    private String pending;

    @Override
    public boolean isDone() {
        return broken;
    }

    @Override
    public boolean runWhilePaused() {
        return true;
    }

    @Override
    public void advance(float amount) {
        if (broken) return;
        try {
            CampaignUIAPI ui = Global.getSector().getCampaignUI();
            if (ui == null || ui.getCurrentCoreTab() != CoreUITabId.INTEL) {
                pending = null;
                return;
            }
            Object core = coreUI();
            if (core == null) return;
            if (pending != null) {
                String id = pending;
                pending = null;
                try {
                    open(core, id);
                } catch (Throwable t) {
                    log.error("War Room: could not open the Faction Archive", t);
                }
                return;
            }
            Object tab = Reflect.call(core, "getCurrentTab");
            if (!Reflect.has(tab, "getFactionPanel")) return;
            Object panel = Reflect.call(tab, "getFactionPanel");
            Object info = panel == null ? null : Reflect.field(panel, "SelectedFactionInfo");
            if (!(info instanceof UIPanelAPI)) return;
            if (info == host.get() && present(info)) return;
            Object f = Reflect.field(info, FactionAPI.class);
            if (f instanceof FactionAPI) add((UIPanelAPI) info, ((FactionAPI) f).getId());
        } catch (Throwable t) {
            broken = true;
            log.warn("War Room: no Faction Archive button on this game version's Factions screen", t);
        }
    }

    /** The core UI on screen: an open interaction dialog's, else the campaign's own. */
    private static Object coreUI() throws Throwable {
        Object driver = Reflect.callStatic("com.fs.state.AppDriver", "getInstance");
        Object state = driver == null ? null : Reflect.call(driver, "getCurrentState");
        if (!Reflect.has(state, "getCore")) return null;
        Object core = null;
        if (Reflect.has(state, "getEncounterDialog")) {
            Object dialog = Reflect.call(state, "getEncounterDialog");
            if (Reflect.has(dialog, "getCoreUI")) core = Reflect.call(dialog, "getCoreUI");
        }
        return core != null ? core : Reflect.call(state, "getCore");
    }

    /** Whether the button is still on the panel (the game may rebuild the panel's contents). */
    private boolean present(Object info) throws Throwable {
        UIComponentAPI b = added.get();
        Object kids = b == null ? null : Reflect.call(info, "getChildrenCopy");
        return kids instanceof List && ((List<?>) kids).contains(b);
    }

    /** The button, right-aligned at the top of the faction's panel, on the line of the faction's name. */
    private void add(UIPanelAPI info, final String factionId) {
        CustomPanelAPI p = Global.getSettings().createCustom(PANEL_W, BUTTON_H, new BaseCustomUIPanelPlugin() {
            @Override
            public void buttonPressed(Object buttonId) {
                pending = factionId;
            }
        });
        TooltipMakerAPI in = p.createUIElement(PANEL_W, BUTTON_H, false);
        float w = Math.min(PANEL_W, in.computeStringWidth(LABEL) + 24f);
        ButtonAPI b = in.addButton(LABEL, factionId, Misc.getBasePlayerColor(), Misc.getDarkPlayerColor(),
                Alignment.MID, CutStyle.TL_BR, w, BUTTON_H, 0f);
        b.getPosition().inTL(PANEL_W - w, 0f);
        Ui.tooltip(in, b, Tips.text("Opens the War Room Archive for this faction: its wars on the timeline "
                + "and its events in the Chronicle."));
        p.addUIElement(in).inTL(0f, 0f);
        info.addComponent(p).inTR(0f, 0f);
        host = new WeakReference<>(info);
        added = new WeakReference<>(p);
    }

    /** Shows War Room's Archive for the faction: the Intel screen opened anew at War Room, as the game does. */
    private static void open(Object core, String factionId) throws Throwable {
        IntelInfoPlugin intel = WarRoom.intel();
        if (intel == null) return;
        View.openFactionArchive(factionId);
        try {
            Reflect.call(core, "setCurrentTab", CoreUITabId.INTEL, intel);
        } catch (NoSuchMethodException e) {
            Global.getSector().getCampaignUI().showCoreUITab(CoreUITabId.INTEL, intel);
        }
    }
}
