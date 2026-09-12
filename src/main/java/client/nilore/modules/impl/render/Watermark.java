package client.nilore.modules.impl.render;

import client.nilore.NiloreClient;
import client.nilore.event.impl.GlRenderEvent;
import client.nilore.event.impl.Render2DEvent;
import client.nilore.hud.DynamicIsland;
import client.nilore.hud.LogoWatermark;
import client.nilore.hud.NeverloseWatermark;
import client.nilore.modules.Category;
import client.nilore.modules.Module;
import client.nilore.render.DrawContext;
import client.nilore.render.FontRenderer;
import client.nilore.render.FontPresets;
import client.nilore.render.Fonts;
import client.nilore.render.Paint;
import client.nilore.render.RoundedRectangle;
import client.nilore.settings.impl.BooleanSetting;
import client.nilore.settings.impl.ModeSetting;
import client.nilore.settings.impl.NumberSetting;
import client.nilore.event.EventTarget;
import client.nilore.utils.render.ColorUtil;
import client.nilore.utils.render.RenderUtil;
import net.minecraft.client.multiplayer.PlayerInfo;

import java.awt.Color;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class Watermark extends Module {
    final ModeSetting styleSetting = new ModeSetting("Style", "Neverlose", "DynamicIsland", "Simple", "Pharos", "Logo").withDefault("DynamicIsland");
    private final NumberSetting bgAlpha = new NumberSetting("BG Alpha", 160, 0, 255, 1, () -> this.styleSetting.is("Simple"));
    private final BooleanSetting glow = new BooleanSetting("Glow", false, () -> this.styleSetting.is("Simple"));
    private final NumberSetting glowRadius = new NumberSetting("Glow Radius", 12, 4, 40, 1, () -> this.styleSetting.is("Simple") && this.glow.getValue());
    private final NumberSetting glowAlpha = new NumberSetting("Glow Alpha", 120, 0, 255, 1, () -> this.styleSetting.is("Simple") && this.glow.getValue());

    // ================================================================
    // 文字颜色设置 - 仅当 Style 为 Simple 时显示
    // ================================================================
    private final ModeSetting textColorMode = new ModeSetting("Text Color", "White",
            "White", "Red", "Gold", "Yellow", "Green", "DarkGreen",
            "Aqua", "DarkAqua", "Blue", "DarkBlue", "Pink", "Purple",
            "Gray", "DarkGray", "DarkRed", "LightRed", "LightGreen",
            "LightAqua", "LightPink", "Orange", "Lime", "Cyan", "Magenta",
            "DoubleColor", "Rainbow")
            .withDefault("White")
            .withVisibility(() -> this.styleSetting.is("Simple"));

    private final DynamicIsland dynamicIsland = new DynamicIsland();
    private final LogoWatermark logoWatermark = new LogoWatermark();
    private final NeverloseWatermark neverloseWatermark = new NeverloseWatermark();

    // Pharos style (original Simple - client name + fps)
    private static final FontRenderer titleFont = Fonts.getRenderer("quicksand.ttf", 36.0f);
    private static final FontRenderer fpsFont = Fonts.getRenderer("quicksand.ttf", 20.0f);
    private static final float MARGIN = 8.0f;

    // Simple capsule style (centered, like LiquidBounce Normal)
    private static final FontRenderer iconFont = FontPresets.niloreIcon(24.0f);
    private static final FontRenderer textFont = FontPresets.pingfang(24.0f);
    private static final int TEXT_COLOR = 0xFFFFFFFF;
    private static final int INFO_COLOR = 0xCCFFFFFF;
    private static final float ICON_SIZE = 20.0f;
    private static final float PAD = 3.0f;
    private static final int BG_COLOR_BASE = 0x0A0A0A;

    // 完整日期时间格式
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    // ================================================================
    // 颜色映射表
    // ================================================================
    private static final java.util.Map<String, Integer> COLOR_MAP = new java.util.HashMap<>();
    static {
        COLOR_MAP.put("White", 0xFFFFFFFF);
        COLOR_MAP.put("Red", 0xFFFF5555);
        COLOR_MAP.put("Gold", 0xFFFFAA00);
        COLOR_MAP.put("Yellow", 0xFFFFFF55);
        COLOR_MAP.put("Green", 0xFF55FF55);
        COLOR_MAP.put("DarkGreen", 0xFF00AA00);
        COLOR_MAP.put("Aqua", 0xFF55FFFF);
        COLOR_MAP.put("DarkAqua", 0xFF00AAAA);
        COLOR_MAP.put("Blue", 0xFF5555FF);
        COLOR_MAP.put("DarkBlue", 0xFF0000AA);
        COLOR_MAP.put("Pink", 0xFFFF55FF);
        COLOR_MAP.put("Purple", 0xFFAA55AA);
        COLOR_MAP.put("Gray", 0xFFAAAAAA);
        COLOR_MAP.put("DarkGray", 0xFF555555);
        COLOR_MAP.put("DarkRed", 0xFFAA0000);
        COLOR_MAP.put("LightRed", 0xFFFF8888);
        COLOR_MAP.put("LightGreen", 0xFF88FF88);
        COLOR_MAP.put("LightAqua", 0xFF88FFFF);
        COLOR_MAP.put("LightPink", 0xFFFF88FF);
        COLOR_MAP.put("Orange", 0xFFFF8800);
        COLOR_MAP.put("Lime", 0xFF88FF00);
        COLOR_MAP.put("Cyan", 0xFF00FFFF);
        COLOR_MAP.put("Magenta", 0xFFFF00FF);
    }

    private long lastColorUpdate = 0;
    private float colorOffset = 0;

    public Watermark() {
        super("Watermark", Category.RENDER);
    }

    @EventTarget
    public void onRender2D(Render2DEvent render2DEvent) {
        if (!this.isEnabled()) {
            return;
        }
        switch (this.styleSetting.getValue()) {
            case "Neverlose":
                this.neverloseWatermark.onRender2D(render2DEvent);
                break;
            case "DynamicIsland":
                this.dynamicIsland.onRender2D(render2DEvent);
                break;
        }
    }

    @EventTarget
    public void onGlRender(GlRenderEvent glRenderEvent) {
        if (!this.isEnabled()) {
            return;
        }

        DrawContext ctx = glRenderEvent.drawContext();
        if (ctx == null) return;

        // 更新颜色动画
        updateColorAnimation();

        switch (this.styleSetting.getValue()) {
            case "Neverlose":
                this.neverloseWatermark.onGlRender(glRenderEvent);
                break;
            case "Pharos":
                this.renderPharos(ctx);
                break;
            case "Simple":
                this.renderSimple(ctx);
                break;
            case "Logo":
                this.logoWatermark.onGlRender(glRenderEvent);
                break;
        }
    }

    private void updateColorAnimation() {
        long now = System.currentTimeMillis();
        if (now - lastColorUpdate > 50) {
            lastColorUpdate = now;
            colorOffset += 0.02f;
            if (colorOffset > 1.0f) {
                colorOffset -= 1.0f;
            }
        }
    }

    // ================================================================
    // 获取当前文字颜色
    // ================================================================
    private int getCurrentColor(float offset, int charIndex) {
        String mode = this.textColorMode.getValue();

        if (mode.equals("Rainbow")) {
            // ================================================================
            // 修复彩虹模式：使用 colorOffset 控制速度，每秒变化 0.02
            // ================================================================
            float hue = (colorOffset + charIndex * 0.05f) % 1.0f;
            return Color.HSBtoRGB(hue, 0.9f, 1.0f);
        }

        if (mode.equals("DoubleColor")) {
            float progress = (colorOffset * 3) % 1.0f;
            int color1 = COLOR_MAP.getOrDefault("Red", 0xFFFF5555);
            int color2 = COLOR_MAP.getOrDefault("Blue", 0xFF5555FF);
            return ColorUtil.lerpColor(color1, color2, progress);
        }

        return COLOR_MAP.getOrDefault(mode, 0xFFFFFFFF);
    }

    // ================================================================
    // 绘制渐变文字（支持双色和彩虹）
    // ================================================================
    private void drawGradientText(DrawContext ctx, String text, float x, float y, FontRenderer font) {
        String mode = this.textColorMode.getValue();

        if (mode.equals("Rainbow")) {
            float charX = x;
            for (int i = 0; i < text.length(); i++) {
                String ch = String.valueOf(text.charAt(i));
                float hue = (colorOffset + i * 0.05f) % 1.0f;
                int color = Color.HSBtoRGB(hue, 0.9f, 1.0f);
                float w = font.getWidth(ch);
                ctx.drawString(ch, charX, y, font, new Paint().setColor(color));
                charX += w;
            }
            return;
        }

        if (mode.equals("DoubleColor")) {
            int color1 = COLOR_MAP.getOrDefault("Red", 0xFFFF5555);
            int color2 = COLOR_MAP.getOrDefault("Blue", 0xFF5555FF);
            float charX = x;
            for (int i = 0; i < text.length(); i++) {
                String ch = String.valueOf(text.charAt(i));
                float progress = (colorOffset * 3 + i * 0.03f) % 1.0f;
                int color = ColorUtil.lerpColor(color1, color2, progress);
                float w = font.getWidth(ch);
                ctx.drawString(ch, charX, y, font, new Paint().setColor(color));
                charX += w;
            }
            return;
        }

        // 单色
        int color = getCurrentColor(0, 0);
        ctx.drawString(text, x, y, font, new Paint().setColor(color));
    }

    private void renderSimple(DrawContext ctx) {
        if (mc.player == null) return;

        String icon = "N";
        String clientName = NiloreClient.CLIENT_NAME;
        String userName = NameProtect.getProtectedName();
        String fpsText = mc.getFps() + "fps";
        String pingText = getPing() + "ms";

        // ===== 完整日期时间 =====
        String timeText = LocalDateTime.now().format(TIME_FORMATTER);

        String infoText = " | " + userName + " | " + fpsText + " | " + pingText + " | " + timeText;

        float iconW = iconFont.getWidth(icon);
        float nameW = textFont.getWidth(clientName);
        float infoW = textFont.getWidth(infoText);

        float contentW = iconW + PAD + nameW + infoW;
        float lineH = Math.max(ICON_SIZE, textFont.getMetrics().height());
        float boxH = lineH + PAD * 2;
        float boxW = contentW + PAD * 2 + 6f;

        float screenW = mc.getWindow().getGuiScaledWidth();
        float screenH = mc.getWindow().getGuiScaledHeight();
        float x = (screenW - boxW) / 2;
        float y = screenH / 20;

        // Capsule background (full pill shape)
        float radius = boxH / 2;
        int bgColor = BG_COLOR_BASE | (bgAlpha.getValue().intValue() << 24);
        RoundedRectangle capsule = RoundedRectangle.ofXYWHR(x, y, boxW, boxH, radius);
        ctx.drawRoundedRect(capsule, new Paint().setColor(bgColor));

        // Glow behind background (like Armor module)
        if (glow.getValue()) {
            int gRadius = this.glowRadius.getValue().intValue();
            int gAlpha = this.glowAlpha.getValue().intValue();
            if (gAlpha > 0 && gRadius > 0) {
                RenderUtil.drawShadow(ctx.getPoseStack(), x, y, boxW, boxH, gRadius, (gAlpha << 24) | 0x000000);
                RenderUtil.enableBlend();
            }
        }

        float cursorX = x + PAD + 3f;
        float textY = y + PAD + (lineH - iconFont.getMetrics().height()) / 2 + 13f;

        // Icon "N" - 使用白色
        ctx.drawString(icon, cursorX, textY, iconFont, new Paint().setColor(TEXT_COLOR));
        cursorX += iconW + PAD;

        // Client name "Nebula" - 使用渐变颜色
        float nameY = y + PAD + (lineH - textFont.getMetrics().height()) / 2 + 10f;
        drawGradientText(ctx, clientName, cursorX, nameY, textFont);
        cursorX += nameW;

        // Info text - 使用渐变颜色（偏移不同，产生不同效果）
        float infoY = y + PAD + (lineH - textFont.getMetrics().height()) / 2 + 10f;
        drawGradientText(ctx, infoText, cursorX, infoY, textFont);
    }

    private void renderPharos(DrawContext ctx) {
        if (mc.player == null) return;

        Paint textPaint = new Paint().setColor(0xFFFFFFFF);
        Paint fpsPaint = new Paint().setColor(0xFFFFFFFB);

        float padX = 6.0f;
        float padY = 4.0f;
        float baseX = MARGIN - 5f;
        float baseY = MARGIN;

        float titleX = baseX + padX;
        float titleY = baseY + padY + titleFont.getMetrics().capHeight();
        ctx.drawString(NiloreClient.CLIENT_NAME, titleX, titleY, titleFont, textPaint);

        String fpsStr = String.valueOf(mc.getFps());
        float fpsX = baseX + padX;
        float fpsY = titleY + titleFont.getMetrics().capHeight() - 7.0f + fpsFont.getMetrics().capHeight();
        ctx.drawString(fpsStr, fpsX, fpsY, fpsFont, fpsPaint);
    }

    private int getPing() {
        if (mc.player == null || mc.player.connection == null) return 0;
        PlayerInfo playerInfo = mc.player.connection.getPlayerInfo(mc.player.getUUID());
        return playerInfo != null ? playerInfo.getLatency() : 0;
    }

    @Override
    public String getDisplayName() {
        return "";
    }

    @Override
    public String getModuleName() {
        return "";
    }

    @Override
    public void onTick() {

    }
}