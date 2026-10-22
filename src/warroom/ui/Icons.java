package warroom.ui;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CustomUIPanelPlugin;
import com.fs.starfarer.api.graphics.SpriteAPI;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.PositionAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.ui.UIComponentAPI;
import com.fs.starfarer.api.util.Misc;

import java.awt.Color;
import java.util.List;

/**
 * War Room's own icons (graphics/warroom/icons, white outlines, listed in data/config/settings.json), drawn
 * in a colour: a native image can't be tinted, so a tiny panel draws the sprite through the game's sprite
 * API. If the sprite is missing, a small native marker takes its place; a drawing failure only hides it.
 */
final class Icons {
    enum Kind {
        CAPTURE("icon_capture"), BATTLE("icon_battle"), BOMBARD("icon_bombard"), CEASEFIRE("icon_ceasefire"),
        BEGAN("icon_began"), RAID("icon_raid"), REPELLED("icon_repelled"), ALLIANCE("icon_alliance"),
        ELIMINATED("icon_eliminated"), ARRIVED("icon_arrived"), AGE("icon_age"), ADVANTAGE("icon_advantage");

        final String key;

        Kind(String key) {
            this.key = key;
        }
    }

    private static final String CATEGORY = "warroom";

    private Icons() {
    }

    /**
     * Places an icon of {@code size} with its top left at (x, y) inside {@code in}, which belongs to
     * {@code host}. Returns the component, for a tooltip.
     */
    static UIComponentAPI place(CustomPanelAPI host, TooltipMakerAPI in, Kind kind, Color color, float x, float y, float size) {
        SpriteAPI sprite = sprite(kind);
        if (sprite == null) return Ui.rect(in, x + size / 2f - 2f, y + size / 2f - 2f, 4f, 4f, color);
        CustomPanelAPI p = host.createCustomPanel(size, size, new Plugin(sprite, color));
        UIComponentAPI c = in.addCustomDoNotSetPosition(p);
        c.getPosition().inTL(x, y);
        return c;
    }

    private static SpriteAPI sprite(Kind kind) {
        try {
            SpriteAPI s = Global.getSettings().getSprite(CATEGORY, kind.key, true);
            return s == null || s.getTextureId() == 0 || s.getWidth() <= 0f ? null : s;
        } catch (Throwable t) {
            return null;
        }
    }

    /** Draws one sprite, tinted, filling its panel. */
    private static final class Plugin implements CustomUIPanelPlugin {
        private final SpriteAPI sprite;
        private final Color color;
        private PositionAPI pos;
        private boolean broken;

        Plugin(SpriteAPI sprite, Color color) {
            this.sprite = sprite;
            this.color = color == null ? Misc.getTextColor() : color;
        }

        @Override
        public void positionChanged(PositionAPI position) {
            pos = position;
        }

        @Override
        public void renderBelow(float alphaMult) {
        }

        @Override
        public void render(float alphaMult) {
            if (broken || pos == null) return;
            try {
                sprite.setColor(color);
                sprite.setAlphaMult(alphaMult);
                sprite.setNormalBlend();
                sprite.setSize(pos.getWidth(), pos.getHeight());
                sprite.renderAtCenter(pos.getCenterX(), pos.getCenterY());
            } catch (Throwable t) {
                // Draw nothing from now on rather than fail every frame.
                broken = true;
                Global.getLogger(Icons.class).error("War Room: could not draw an icon", t);
            }
        }

        @Override
        public void advance(float amount) {
        }

        @Override
        public void processInput(List<InputEventAPI> events) {
        }

        @Override
        public void buttonPressed(Object buttonId) {
        }
    }
}
