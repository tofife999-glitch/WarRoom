package warroom.ui;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.BaseCustomUIPanelPlugin;
import com.fs.starfarer.api.campaign.CustomUIPanelPlugin;
import com.fs.starfarer.api.graphics.SpriteAPI;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.LabelAPI;
import com.fs.starfarer.api.ui.PositionAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.ui.UIComponentAPI;

import java.awt.Color;
import java.util.List;

/**
 * War Room's few moving touches, drawn with the game's sprite API in a tiny panel each: a light that pulses
 * slowly, a line that runs once along an edge, a frame that lights up once, a thin trace through a series of
 * values, and a label that flickers on. If a sprite is missing, nothing is drawn.
 */
final class Fx {
    private Fx() {
    }

    /** A round light of {@code size} that pulses with the given period (seconds), its top left at (x, y). */
    static void pulse(CustomPanelAPI host, TooltipMakerAPI in, Color color, float x, float y, float size, float period) {
        SpriteAPI s = sprite("fx_dot");
        if (s == null) return;
        place(host, in, new Light(s, color, period), x, y, size, size);
    }

    /** A line running once from left to right along (x, y), {@code w} wide, over about {@code seconds}. */
    static void sweep(CustomPanelAPI host, TooltipMakerAPI in, Color color, float x, float y, float w, float seconds) {
        SpriteAPI s = sprite("fx_sweep");
        if (s == null) return;
        place(host, in, new Sweep(s, color, seconds), x, y, w, 2f);
    }

    /**
     * A frame around (x, y, w, h) that lights up once and fades over about {@code seconds}: four thin edges, each
     * its own tiny panel, so nothing inside the frame is covered.
     */
    static void flash(CustomPanelAPI host, TooltipMakerAPI in, Color color, float x, float y, float w, float h,
                      float seconds) {
        float k = 2f;
        float[][] edges = {{x, y, w, k}, {x, y + h - k, w, k}, {x, y + k, k, h - 2f * k}, {x + w - k, y + k, k, h - 2f * k}};
        for (float[] e : edges) {
            SpriteAPI s = sprite("fx_px");
            if (s == null) return;
            place(host, in, new Glow(s, color, seconds), e[0], e[1], e[2], e[3]);
        }
    }

    /** A thin line through {@code values} (drawn to fit, highest at the top) across (x, y, w, h), a dot at its end. */
    static void trace(CustomPanelAPI host, TooltipMakerAPI in, Color color, float x, float y, float w, float h, float[] values) {
        SpriteAPI line = sprite("fx_px");
        if (line == null || values == null || values.length < 2) return;
        place(host, in, new Trace(line, sprite("fx_dot"), color, values), x, y, w, h);
    }

    /** Makes a label flicker on and settle, like a projection coming on, in under half a second. */
    static void settle(CustomPanelAPI host, TooltipMakerAPI in, LabelAPI label) {
        label.setOpacity(0.15f);
        CustomPanelAPI p = host.createCustomPanel(1f, 1f, new Settle(label));
        in.addCustomDoNotSetPosition(p).getPosition().inTL(0f, 0f);
    }

    private static void place(CustomPanelAPI host, TooltipMakerAPI in, CustomUIPanelPlugin plugin, float x, float y, float w,
                              float h) {
        CustomPanelAPI p = host.createCustomPanel(w, h, plugin);
        UIComponentAPI c = in.addCustomDoNotSetPosition(p);
        c.getPosition().inTL(x, y);
    }

    private static SpriteAPI sprite(String key) {
        try {
            SpriteAPI s = Global.getSettings().getSprite("warroom", key, true);
            return s == null || s.getTextureId() == 0 || s.getWidth() <= 0f ? null : s;
        } catch (Throwable t) {
            return null;
        }
    }

    /** What the two have in common: their place, the time since they appeared, giving up if drawing fails. */
    private abstract static class Base implements CustomUIPanelPlugin {
        final SpriteAPI sprite;
        final Color color;
        PositionAPI pos;
        float time;
        boolean broken;

        Base(SpriteAPI sprite, Color color) {
            this.sprite = sprite;
            this.color = color;
        }

        abstract void draw(float alphaMult);

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
                sprite.setNormalBlend();
                draw(alphaMult);
            } catch (Throwable t) {
                broken = true;
                Global.getLogger(Fx.class).error("War Room: could not draw a screen touch", t);
            }
        }

        @Override
        public void advance(float amount) {
            time += Math.max(0f, Math.min(0.1f, amount));
        }

        @Override
        public void processInput(List<InputEventAPI> events) {
        }

        @Override
        public void buttonPressed(Object buttonId) {
        }
    }

    private static final class Light extends Base {
        private final float period;

        Light(SpriteAPI sprite, Color color, float period) {
            super(sprite, color);
            this.period = Math.max(0.5f, period);
        }

        @Override
        void draw(float alphaMult) {
            float wave = 0.5f + 0.5f * (float) Math.cos(2.0 * Math.PI * time / period);
            sprite.setAlphaMult(alphaMult * (0.25f + 0.75f * wave));
            sprite.setSize(pos.getWidth(), pos.getHeight());
            sprite.renderAtCenter(pos.getCenterX(), pos.getCenterY());
        }
    }

    private static final class Sweep extends Base {
        private final float seconds;

        Sweep(SpriteAPI sprite, Color color, float seconds) {
            super(sprite, color);
            this.seconds = Math.max(0.2f, seconds);
        }

        @Override
        void draw(float alphaMult) {
            float p = time / seconds;
            if (p >= 1f) return;
            float w = pos.getWidth();
            float seg = w * 0.38f;
            // The segment's leading end runs from the left edge to the right; it stays inside the panel.
            float lead = pos.getX() + (w + seg) * (1f - (1f - p) * (1f - p));
            float x0 = Math.max(pos.getX(), lead - seg);
            float x1 = Math.min(pos.getX() + w, lead);
            if (x1 - x0 < 1f) return;
            sprite.setAlphaMult(alphaMult * (p < 0.8f ? 1f : (1f - p) / 0.2f));
            sprite.setSize(x1 - x0, pos.getHeight());
            sprite.renderAtCenter((x0 + x1) / 2f, pos.getCenterY());
        }
    }

    private static final class Trace extends Base {
        private final SpriteAPI dot;
        private final float[] values;
        private final float max;

        Trace(SpriteAPI sprite, SpriteAPI dot, Color color, float[] values) {
            super(sprite, color);
            this.dot = dot;
            this.values = values.clone();
            float m = 0f;
            for (float v : values) m = Math.max(m, v);
            this.max = m;
        }

        @Override
        void draw(float alphaMult) {
            // Screen y runs upward: pos.getY() is the bottom edge.
            int n = values.length;
            float h = pos.getHeight() - 2f;
            float px = 0f;
            float py = 0f;
            sprite.setAlphaMult(alphaMult * 0.9f);
            for (int i = 0; i < n; i++) {
                float x = pos.getX() + pos.getWidth() * i / (n - 1);
                float y = pos.getY() + 1f + (max > 0f ? values[i] / max : 0f) * h;
                if (i > 0) {
                    float dx = x - px;
                    float dy = y - py;
                    sprite.setSize((float) Math.sqrt(dx * dx + dy * dy) + 1f, 1.5f);
                    sprite.setAngle((float) Math.toDegrees(Math.atan2(dy, dx)));
                    sprite.renderAtCenter((x + px) / 2f, (y + py) / 2f);
                }
                px = x;
                py = y;
            }
            sprite.setAngle(0f);
            if (dot != null) {
                dot.setColor(color);
                dot.setNormalBlend();
                dot.setAlphaMult(alphaMult);
                dot.setSize(5f, 5f);
                dot.renderAtCenter(px, py);
            }
        }
    }

    /** Steps through a short flicker, then leaves the label fully on. */
    private static final class Settle extends BaseCustomUIPanelPlugin {
        /** {time in seconds, opacity from then on}. */
        private static final float[][] KEYS = {{0f, 0.15f}, {0.08f, 0.9f}, {0.14f, 0.35f}, {0.22f, 1f}, {0.3f, 0.6f},
                {0.38f, 1f}};
        private final LabelAPI label;
        private float time;
        private boolean done;

        Settle(LabelAPI label) {
            this.label = label;
        }

        @Override
        public void advance(float amount) {
            if (done) return;
            try {
                time += Math.max(0f, Math.min(0.1f, amount));
                float a = 1f;
                for (float[] k : KEYS) {
                    if (time >= k[0]) a = k[1];
                }
                label.setOpacity(a);
                done = time >= KEYS[KEYS.length - 1][0];
            } catch (Throwable t) {
                done = true;
            }
        }
    }

    private static final class Glow extends Base {
        private final float seconds;

        Glow(SpriteAPI sprite, Color color, float seconds) {
            super(sprite, color);
            this.seconds = Math.max(0.8f, seconds);
        }

        @Override
        void draw(float alphaMult) {
            // Up in a moment, held briefly, then fading out.
            float a;
            if (time < 0.12f) a = time / 0.12f;
            else if (time < 0.4f) a = 1f;
            else a = (float) Math.pow(Math.max(0f, 1f - (time - 0.4f) / (seconds - 0.4f)), 2.0);
            if (a <= 0f) return;
            sprite.setAlphaMult(alphaMult * a);
            sprite.setSize(pos.getWidth(), pos.getHeight());
            sprite.renderAtCenter(pos.getCenterX(), pos.getCenterY());
        }
    }
}
