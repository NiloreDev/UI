package client.nilore.gui;

import client.nilore.NiloreClient;
import client.nilore.modules.Category;
import client.nilore.modules.Module;
import client.nilore.modules.impl.render.ClickGui;
import client.nilore.settings.Setting;
import client.nilore.settings.impl.BooleanSetting;
import client.nilore.settings.impl.ModeSetting;
import client.nilore.settings.impl.MultiSelectSetting;
import client.nilore.settings.impl.NumberSetting;
import client.nilore.settings.impl.TextSetting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * SuperSoft-style ClickGUI for Open Nilore (MC 1.20.1).
 *
 * This is a native Java/GuiGraphics recreation of the visual language from
 * Xiamo-vip/SuperSoftClient-Compose-for-MC-1.21.4.  It intentionally avoids
 * Compose/Skia so it can live inside Nilore's existing Java 17 runtime.
 */
public final class SuperSoftClickGui extends Screen {
    // -------------------------- palette --------------------------
    private static final int SCRIM = 0x60482814;
    private static final int PANEL = 0xF1151515;
    private static final int HEADER = 0xF4000000;
    private static final int ROW = 0xF3131313;
    private static final int SETTING = 0xF32A2A2A;
    private static final int SETTING_HOVER = 0xF33A3A3A;
    private static final int ACCENT = 0xFF7142EC;
    private static final int ACCENT_HOVER = 0xFF8055F4;
    private static final int TEXT = 0xFFF3F3F3;
    private static final int MUTED = 0xFFBFBFBF;
    private static final int TRACK = 0xFF545454;
    private static final int KNOB = 0xFFF4F4F4;

    // These values are Minecraft GUI-space pixels. On a 2x GUI scale they
    // visually match the proportions of the reference screenshot closely.
    private static final int PANEL_W = 120;
    private static final int HEADER_H = 24;
    private static final int MODULE_H = 16;
    private static final int SETTING_H = 20;
    private static final int TOP = 34;
    private static final int SIDE = 8;
    private static final int GAP_MIN = 14;

    private final List<Panel> panels = new ArrayList<>();
    private final Map<Module, Boolean> expanded = new IdentityHashMap<>();
    private final Map<ModeSetting, Boolean> modeExpanded = new IdentityHashMap<>();

    private Module keyListening;
    private NumberSetting sliderDragging;
    private int sliderX;
    private int sliderW;
    private TextSetting textEditing;

    private Panel draggingPanel;
    private int dragOffsetX;
    private int dragOffsetY;

    private long openedAt;
    private boolean closing;
    private long closingAt;

    public SuperSoftClickGui() {
        super(Component.literal("SuperSoft ClickGUI"));
    }

    @Override
    protected void init() {
        super.init();
        openedAt = System.currentTimeMillis();
        closing = false;
        closingAt = 0L;
        if (panels.isEmpty()) buildPanels();
        else layoutPanels(false);
    }

    private void buildPanels() {
        panels.clear();

        // SuperSoft layout: one panel for each visible category.
        panels.add(new Panel("Combat", Arrays.asList(Category.COMBAT)));
        panels.add(new Panel("Movement", Arrays.asList(Category.MOVEMENT)));
        panels.add(new Panel("Player", Arrays.asList(Category.PLAYER)));
        panels.add(new Panel("Render", Arrays.asList(Category.RENDER)));

        // Keep the old Category enum mapping:
        // EXPLOIT -> World, WORLD -> Misc, MISC -> Ghost
        panels.add(new Panel("World", Arrays.asList(Category.EXPLOIT)));
        panels.add(new Panel("Misc", Arrays.asList(Category.WORLD)));
        panels.add(new Panel("Ghost", Arrays.asList(Category.MISC)));

        layoutPanels(true);
    }

    private void layoutPanels(boolean force) {
        int usable = Math.max(1, width - SIDE * 2 - PANEL_W * 7);
        int gap = Math.max(GAP_MIN, usable / 6);
        int total = PANEL_W * 7 + gap * 6;
        int start = Math.max(SIDE, (width - total) / 2);
        for (int i = 0; i < panels.size(); i++) {
            Panel p = panels.get(i);
            if (force || !p.movedByUser) {
                p.x = start + i * (PANEL_W + gap);
                p.y = TOP;
            }
        }
    }

    private List<Module> modules(Panel panel) {
        List<Module> out = new ArrayList<>();
        for (Category cat : panel.categories) {
            out.addAll(NiloreClient.getInstance().getModuleManager().getModulesByCategory(cat));
        }
        out.removeIf(Module::isHiddenInModuleList);
        out.sort(Comparator.comparing(Module::getName, String.CASE_INSENSITIVE_ORDER));
        return out;
    }

    @Override
    public void render(@Nonnull GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        float a = animation();
        int scrimAlpha = (int) (((SCRIM >>> 24) & 0xFF) * a);
        g.fill(0, 0, width, height, (scrimAlpha << 24) | (SCRIM & 0xFFFFFF));

        g.pose().pushPose();
        float s = 0.965f + 0.035f * a;
        g.pose().translate(width / 2f, height / 2f, 0f);
        g.pose().scale(s, s, 1f);
        g.pose().translate(-width / 2f, -height / 2f, 0f);

        for (Panel p : panels) drawPanel(g, p, mouseX, mouseY, a);
        g.pose().popPose();

        if (closing && a <= 0.01f) {
            save();
            minecraft.setScreen(null);
        }
    }

    private void drawPanel(GuiGraphics g, Panel p, int mouseX, int mouseY, float a) {
        List<Module> mods = modules(p);
        int contentH = 0;
        for (Module m : mods) {
            contentH += MODULE_H;
            if (Boolean.TRUE.equals(expanded.get(m))) {
                contentH += SETTING_H; // KeyBind row
                for (Setting<?> setting : m.getSettings()) {
                    if (visible(setting)) contentH += settingHeight(setting);
                }
            }
        }
        int maxContent = Math.max(70, height - p.y - 18 - HEADER_H);
        p.viewportH = Math.min(contentH, maxContent);
        p.contentH = contentH;
        int maxScroll = Math.max(0, contentH - p.viewportH);
        p.scroll = clamp(p.scroll, 0, maxScroll);

        int bottom = p.y + HEADER_H + p.viewportH;
        fillA(g, p.x, p.y, p.x + PANEL_W, bottom, PANEL, a);
        fillA(g, p.x, p.y, p.x + PANEL_W, p.y + HEADER_H, HEADER, a);
        centered(g, p.title, p.x + PANEL_W / 2, p.y + 8, TEXT, a, 1.0f);

        p.hovered = inside(mouseX, mouseY, p.x, p.y, PANEL_W, HEADER_H + p.viewportH);

        int clipTop = p.y + HEADER_H;
        int clipBottom = bottom;
        if (p.viewportH <= 0) return;
        g.enableScissor(p.x, clipTop, p.x + PANEL_W, clipBottom);

        int y = clipTop - p.scroll;
        for (Module m : mods) {
            if (y + MODULE_H >= clipTop && y <= clipBottom) {
                drawModule(g, p, m, y, mouseX, mouseY, a);
            }
            y += MODULE_H;

            if (!Boolean.TRUE.equals(expanded.get(m))) continue;

            if (y + SETTING_H >= clipTop && y <= clipBottom) {
                drawKeybind(g, p.x, y, m, mouseX, mouseY, a);
            }
            y += SETTING_H;

            for (Setting<?> setting : m.getSettings()) {
                if (!visible(setting)) continue;
                int h = settingHeight(setting);
                if (y + h >= clipTop && y <= clipBottom) {
                    drawSetting(g, p.x, y, h, setting, mouseX, mouseY, a);
                }
                y += h;
            }
        }
        g.disableScissor();
    }

    private void drawModule(GuiGraphics g, Panel p, Module m, int y, int mx, int my, float a) {
        boolean hover = inside(mx, my, p.x, y, PANEL_W, MODULE_H);
        int bg = m.isEnabled() ? (hover ? ACCENT_HOVER : ACCENT) : ROW;
        fillA(g, p.x, y, p.x + PANEL_W, y + MODULE_H, bg, a);

        text(g, fit(m.getName(), PANEL_W - 28), p.x + 9, y + 4, TEXT, a, 0.86f);

        boolean hasChildren = !m.getSettings().isEmpty() || m.getKey() != 0;
        if (hasChildren) {
            String arrow = Boolean.TRUE.equals(expanded.get(m)) ? "▼" : "▶";
            right(g, arrow, p.x + PANEL_W - 8, y + 4, 0xFFE6E6E6, a, 0.78f);
        }
    }

    private void drawKeybind(GuiGraphics g, int x, int y, Module m, int mx, int my, float a) {
        boolean hover = inside(mx, my, x, y, PANEL_W, SETTING_H);
        fillA(g, x, y, x + PANEL_W, y + SETTING_H, hover ? SETTING_HOVER : SETTING, a);
        text(g, "KeyBind", x + 9, y + 6, MUTED, a, 0.80f);
        String value = keyListening == m ? "..." : keyName(m.getKey());
        right(g, value, x + PANEL_W - 8, y + 6, keyListening == m ? 0xFFFFFF78 : ACCENT, a, 0.80f);
    }

    private int settingHeight(Setting<?> setting) {
        // Native Nilore currently has no ColorSetting.  Existing setting types
        // use a single SuperSoft-style row. Multi-select uses two rows.
        if (setting instanceof ModeSetting s && Boolean.TRUE.equals(modeExpanded.get(s))) {
            return SETTING_H + (s.getModes().length * SETTING_H);
        }
        return setting instanceof MultiSelectSetting ? SETTING_H * 2 : SETTING_H;
    }

    private void drawSetting(GuiGraphics g, int x, int y, int h, Setting<?> setting,
                             int mx, int my, float a) {
        boolean hover = inside(mx, my, x, y, PANEL_W, h);
        fillA(g, x, y, x + PANEL_W, y + h, hover ? SETTING_HOVER : SETTING, a);

        if (setting instanceof BooleanSetting s) {
            text(g, setting.getName(), x + 9, y + 6, MUTED, a, 0.80f);
            drawSwitch(g, x + PANEL_W - 31, y + 6, Boolean.TRUE.equals(s.getValue()), a);
            return;
        }

        if (setting instanceof NumberSetting s) {
            text(g, fit(setting.getName(), 66), x + 9, y + 4, MUTED, a, 0.78f);
            right(g, pretty(s.getValue().doubleValue()), x + PANEL_W - 8, y + 4, ACCENT, a, 0.76f);
            int sx = x + 9;
            int sw = PANEL_W - 18;
            int sy = y + h - 5;
            double min = s.getMin().doubleValue();
            double max = s.getMax().doubleValue();
            double val = s.getValue().doubleValue();
            double t = max <= min ? 0.0 : (val - min) / (max - min);
            t = Math.max(0, Math.min(1, t));
            fillA(g, sx, sy, sx + sw, sy + 2, TRACK, a);
            fillA(g, sx, sy, sx + (int) Math.round(sw * t), sy + 2, ACCENT, a);
            int kx = sx + (int) Math.round(sw * t);
            fillA(g, kx - 3, sy - 2, kx + 3, sy + 4, ACCENT, a);
            return;
        }

        if (setting instanceof ModeSetting s) {
            text(g, fit(setting.getName(), 70), x + 9, y + 6, MUTED, a, 0.80f);
            text(g, String.valueOf(s.getValue()), x + 9, y + 20, ACCENT, a, 0.78f);
            right(g, Boolean.TRUE.equals(modeExpanded.get(s)) ? "▲" : "▼",
                    x + PANEL_W - 7, y + 6, MUTED, a, 0.72f);

            if (Boolean.TRUE.equals(modeExpanded.get(s))) {
                int yy = y + SETTING_H;
                for (String mode : s.getModes()) {
                    boolean selected = mode.equals(s.getValue());
                    fillA(g, x, yy, x + PANEL_W, yy + SETTING_H,
                            selected ? ACCENT : SETTING, a);
                    centered(g, mode, x + PANEL_W / 2, yy + 6,
                            selected ? TEXT : MUTED, a, 0.76f);
                    yy += SETTING_H;
                }
            }
            return;
        }

        if (setting instanceof MultiSelectSetting s) {
            text(g, fit(setting.getName(), 95), x + 9, y + 5, MUTED, a, 0.80f);
            String selected = String.join(", ", s.getValue());
            text(g, fit(selected.isEmpty() ? "None" : selected, PANEL_W - 18), x + 9, y + SETTING_H + 3,
                    ACCENT, a, 0.72f);
            return;
        }

        if (setting instanceof TextSetting s) {
            text(g, fit(setting.getName(), 62), x + 9, y + 6, MUTED, a, 0.80f);
            String value = s.getValue() == null ? "" : s.getValue();
            if (textEditing == s) value += "_";
            right(g, value, x + PANEL_W - 8, y + 6, ACCENT, a, 0.76f);
            return;
        }

        text(g, fit(setting.getName(), 64), x + 9, y + 6, MUTED, a, 0.80f);
        right(g, fit(String.valueOf(setting.getValue()), 48), x + PANEL_W - 8, y + 6, ACCENT, a, 0.76f);
    }

    private void drawSwitch(GuiGraphics g, int x, int y, boolean on, float a) {
        int track = on ? ACCENT : 0xFF555555;
        fillA(g, x, y + 1, x + 24, y + 9, track, a);
        int kx = on ? x + 15 : x + 2;
        fillA(g, kx, y, kx + 8, y + 10, KNOB, a);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // Top-most is the last panel in draw order.
        List<Panel> reverse = new ArrayList<>(panels);
        Collections.reverse(reverse);
        for (Panel p : reverse) {
            if (!p.hovered) continue;

            if (inside(mouseX, mouseY, p.x, p.y, PANEL_W, HEADER_H)) {
                if (button == 0) {
                    draggingPanel = p;
                    dragOffsetX = (int) mouseX - p.x;
                    dragOffsetY = (int) mouseY - p.y;
                    p.movedByUser = true;
                    panels.remove(p);
                    panels.add(p);
                    return true;
                }
            }

            int y = p.y + HEADER_H - p.scroll;
            for (Module m : modules(p)) {
                if (inside(mouseX, mouseY, p.x, y, PANEL_W, MODULE_H)) {
                    if (button == 0) {
                        m.toggle();
                    } else if (button == 1) {
                        expanded.put(m, !Boolean.TRUE.equals(expanded.get(m)));
                    } else if (button == 2) {
                        keyListening = m;
                    }
                    return true;
                }
                y += MODULE_H;
                if (!Boolean.TRUE.equals(expanded.get(m))) continue;

                if (inside(mouseX, mouseY, p.x, y, PANEL_W, SETTING_H)) {
                    keyListening = keyListening == m ? null : m;
                    return true;
                }
                y += SETTING_H;

                for (Setting<?> setting : m.getSettings()) {
                    if (!visible(setting)) continue;
                    int h = settingHeight(setting);
                    if (inside(mouseX, mouseY, p.x, y, PANEL_W, h)) {
                        return clickSetting(setting, p.x, y, mouseX, mouseY, button);
                    }
                    y += h;
                }
            }
        }
        textEditing = null;
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private boolean clickSetting(Setting<?> setting, int x, int y, double mouseX, double mouseY, int button) {
        if (setting instanceof BooleanSetting s) {
            s.setValue(!Boolean.TRUE.equals(s.getValue()));
            return true;
        }
        if (setting instanceof NumberSetting s) {
            sliderDragging = s;
            sliderX = x + 9;
            sliderW = PANEL_W - 18;
            updateSlider(mouseX);
            return true;
        }
        if (setting instanceof ModeSetting s) {
            boolean open = Boolean.TRUE.equals(modeExpanded.get(s));

            // Right click opens/closes the mode list.
            if (button == 1) {
                modeExpanded.put(s, !open);
                return true;
            }

            // Left click selects a mode from the expanded list.
            if (button == 0 && open) {
                int yy = y + SETTING_H;
                for (String mode : s.getModes()) {
                    if (inside(mouseX, mouseY, x, yy, PANEL_W, SETTING_H)) {
                        s.setValue(mode);
                        modeExpanded.put(s, false);
                        return true;
                    }
                    yy += SETTING_H;
                }
                return true;
            }

            return true;
        }
        if (setting instanceof MultiSelectSetting s) {
            if (s.getOptions().isEmpty()) return true;
            // Simple cycle behavior keeps this renderer dependency-free.
            // Left-click toggles the first currently-unselected option; if all
            // are selected it clears them. Right-click clears immediately.
            List<String> now = new ArrayList<>(s.getValue());
            if (button == 1) {
                now.clear();
            } else {
                String candidate = null;
                for (String option : s.getOptions()) {
                    if (!now.contains(option)) {
                        candidate = option;
                        break;
                    }
                }
                if (candidate == null) now.clear();
                else now.add(candidate);
            }
            s.setValue(now);
            return true;
        }
        if (setting instanceof TextSetting s) {
            textEditing = textEditing == s ? null : s;
            return true;
        }
        return true;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (draggingPanel != null) {
            draggingPanel.x = clamp((int) mouseX - dragOffsetX, 0, Math.max(0, width - PANEL_W));
            draggingPanel.y = clamp((int) mouseY - dragOffsetY, 0, Math.max(0, height - HEADER_H));
            return true;
        }
        if (sliderDragging != null) {
            updateSlider(mouseX);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        draggingPanel = null;
        sliderDragging = null;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        for (Panel p : panels) {
            if (!p.hovered) continue;
            int max = Math.max(0, p.contentH - p.viewportH);
            p.scroll = clamp(p.scroll - (int) Math.round(delta * 16.0), 0, max);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyListening != null) {
            keyListening.setKey(keyCode == GLFW.GLFW_KEY_ESCAPE ? 0 : keyCode);
            keyListening = null;
            save();
            return true;
        }

        if (textEditing != null) {
            if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_ESCAPE) {
                textEditing = null;
                save();
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_BACKSPACE) {
                String v = textEditing.getValue();
                if (v != null && !v.isEmpty()) textEditing.setValue(v.substring(0, v.length() - 1));
                return true;
            }
        }

        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            onClose();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (textEditing != null && !Character.isISOControl(codePoint)) {
            String v = textEditing.getValue();
            textEditing.setValue((v == null ? "" : v) + codePoint);
            return true;
        }
        return super.charTyped(codePoint, modifiers);
    }

    @Override
    public void onClose() {
        if (!closing) {
            closing = true;
            closingAt = System.currentTimeMillis();
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private void updateSlider(double mouseX) {
        if (sliderDragging == null) return;
        double t = Math.max(0.0, Math.min(1.0, (mouseX - sliderX) / (double) sliderW));
        double min = sliderDragging.getMin().doubleValue();
        double max = sliderDragging.getMax().doubleValue();
        double step = Math.max(0.0000001, sliderDragging.getStep().doubleValue());
        double value = min + (max - min) * t;
        value = Math.round(value / step) * step;
        value = Math.max(min, Math.min(max, value));
        sliderDragging.setValue(value);
    }

    private boolean visible(Setting<?> setting) {
        try {
            // Setting#isVisible() in current Nilore always returns false.
            return setting.getVisibility() == null || setting.getVisibility().displayable();
        } catch (Throwable ignored) {
            return true;
        }
    }

    private float animation() {
        long now = System.currentTimeMillis();
        float t;
        if (closing) {
            t = 1f - Math.min(1f, (now - closingAt) / 160f);
        } else {
            t = Math.min(1f, (now - openedAt) / 190f);
        }
        t = Math.max(0f, Math.min(1f, t));
        return 1f - (float) Math.pow(1f - t, 3);
    }

    private void save() {
        try {
            if (NiloreClient.getInstance() != null && NiloreClient.getInstance().getConfigManager() != null) {
                NiloreClient.getInstance().getConfigManager().saveAll();
            }
        } catch (Throwable ignored) {
        }
    }

    private void fillA(GuiGraphics g, int x1, int y1, int x2, int y2, int argb, float a) {
        int alpha = (argb >>> 24) & 0xFF;
        int aa = clamp((int) (alpha * a), 0, 255);
        g.fill(x1, y1, x2, y2, (aa << 24) | (argb & 0xFFFFFF));
    }

    private boolean useMinecraftFont() {
        try {
            client.nilore.modules.impl.render.ClickGui clickGui =
                    (client.nilore.modules.impl.render.ClickGui)
                            NiloreClient.getInstance().getModuleManager()
                                    .getModule(client.nilore.modules.impl.render.ClickGui.class);
            return false;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private void text(GuiGraphics g, String s, int x, int y, int color, float a, float scale) {
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        g.pose().scale(scale, scale, 1f);
        int alpha = clamp((int) (255f * a), 0, 255);
        if (useMinecraftFont()) {
            g.drawString(font, s, 0, 0, (alpha << 24) | (color & 0xFFFFFF), false);
        } else {
            g.drawString(font, s, 0, 0, (alpha << 24) | (color & 0xFFFFFF), false);
        }
        g.pose().popPose();
    }

    private void centered(GuiGraphics g, String s, int cx, int y, int color, float a, float scale) {
        int w = Math.round(font.width(s) * scale);
        text(g, s, cx - w / 2, y, color, a, scale);
    }

    private void right(GuiGraphics g, String s, int right, int y, int color, float a, float scale) {
        int w = Math.round(font.width(s) * scale);
        text(g, s, right - w, y, color, a, scale);
    }

    private String fit(String s, int maxPx) {
        if (s == null) return "";
        if (font.width(s) <= maxPx) return s;
        String out = s;
        while (!out.isEmpty() && font.width(out + "…") > maxPx) out = out.substring(0, out.length() - 1);
        return out + "…";
    }

    private static String pretty(double v) {
        if (Math.abs(v - Math.rint(v)) < 1.0E-8) return Integer.toString((int) Math.rint(v));
        return String.format(Locale.ROOT, "%.2f", v).replaceAll("0+$", "").replaceAll("\\.$", "");
    }

    private static String keyName(int key) {
        if (key == 0) return "None";
        String n = GLFW.glfwGetKeyName(key, 0);
        if (n != null && !n.isEmpty()) return n.toUpperCase(Locale.ROOT);
        return "K" + key;
    }

    private static boolean inside(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    private static int clamp(int n, int min, int max) {
        return Math.max(min, Math.min(max, n));
    }

    private static final class Panel {
        final String title;
        final List<Category> categories;
        int x;
        int y;
        int scroll;
        int contentH;
        int viewportH;
        boolean hovered;
        boolean movedByUser;

        Panel(String title, List<Category> categories) {
            this.title = title;
            this.categories = categories;
        }
    }
}