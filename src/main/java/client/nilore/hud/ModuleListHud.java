// client/nilore/hud/ModuleListHud.java
package client.nilore.hud;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import client.nilore.NiloreClient;
import client.nilore.utils.render.ColorUtil;
import client.nilore.utils.render.ShaderCompatUtil;
import net.minecraft.util.Mth;
import client.nilore.event.impl.GlRenderEvent;
import client.nilore.event.impl.Render2DEvent;
import client.nilore.modules.Module;
import client.nilore.modules.impl.render.Interface;
import client.nilore.render.DrawContext;
import client.nilore.render.FontRenderer;
import client.nilore.render.FontPresets;
import client.nilore.render.GlHelper;
import client.nilore.render.Paint;
import client.nilore.render.Renderer;
import client.nilore.render.RoundedRectangle;
import client.nilore.settings.impl.BooleanSetting;
import client.nilore.settings.impl.ModeSetting;
import client.nilore.settings.impl.NumberSetting;
import client.nilore.utils.animation.SmoothAnimationTimer;
import client.nilore.utils.math.Easings;
import client.nilore.utils.render.GradientTheme;
import client.nilore.utils.render.RenderUtil;

public
class ModuleListHud extends HudElement {
    public boolean isSuffixBracketsEnabled() {
        return false;
    }

    public boolean isSuffixColorEnabled() {
        return false;
    }

    public boolean isShowSuffix() {
        return false;
    }

    public boolean isSuffixLowercaseEnabled() {
        return false;
    }

    public String getSelectedSuffixColorCode() {
        return "";
    }

    public int getSelectedSuffixColorArgb() {
        return 0;
    }

    private enum Alignment {
        LEFT,
        RIGHT
    }

    private static final class AnimatedRow {
        private final SmoothAnimationTimer progressAnim = new SmoothAnimationTimer();
        private final String name;
        private String fullDisplayName;
        private float textWidth;
        private float rowWidth;
        private boolean targetVisible;
        private Module module;

        private AnimatedRow(Module module) {
            this.module = module;
            this.name = module.getName();
            this.progressAnim.setCurrentValue(0.0f);
            this.progressAnim.setToValue(0.0f);
        }

        private void updateMetrics(String displayName, float textWidth, float rowWidth) {
            this.fullDisplayName = displayName;
            this.textWidth = textWidth;
            this.rowWidth = rowWidth;
        }

        private void setTargetVisible(boolean visible) {
            if (this.targetVisible == visible) {
                return;
            }
            this.targetVisible = visible;
            double target = visible ? 1.0 : 0.0;
            float current = this.progressAnim.getValueF();
            this.progressAnim.setCurrentValue(current);
            this.progressAnim.setFromValue(current);
            this.progressAnim.setToValue(target);
            this.progressAnim.setStartTime(System.currentTimeMillis());
            this.progressAnim.setDuration(visible ? 240.0 : 180.0);
            this.progressAnim.setEasing(visible ? Easings.EASE_OUT_POW3 : Easings.EASE_IN_POW3);
        }

        private void tick() {
            this.progressAnim.tick();
        }

        private float progress() {
            return Mth.clamp(this.progressAnim.getValueF(), 0.0f, 1.0f);
        }

        private boolean isFinishedRemoving() {
            return !this.targetVisible && this.progress() <= 0.01f && this.progressAnim.isDone();
        }
    }

    private static final class RowRenderLayout {
        private final AnimatedRow row;
        private final int rowIndex;
        private final float x;
        private final float y;
        private final float width;
        private final float height;
        private final float fullHeight;
        private final float progress;
        private float linkHeight;

        private RowRenderLayout(AnimatedRow row, int rowIndex, float x, float y, float width, float height,
                                float fullHeight, float progress) {
            this.row = row;
            this.rowIndex = rowIndex;
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
            this.fullHeight = fullHeight;
            this.progress = progress;
        }

        private void linkTo(RowRenderLayout next) {
            this.linkHeight = Math.max(0.0f, next.y - (this.y + this.height));
        }

        private float visualHeight(boolean broken) {
            return this.height + (broken ? 0.0f : this.linkHeight);
        }
    }

    // ========== 20种后缀颜色定义 ==========
    private static final String[] SUFFIX_COLOR_NAMES = {
            "Red", "Gold", "Yellow", "Green", "DarkGreen",
            "Aqua", "DarkAqua", "Blue", "DarkBlue", "Pink",
            "Purple", "Gray", "DarkGray", "White", "DarkRed",
            "Black", "LightRed", "LightGreen", "LightAqua", "LightPink"
    };

    private static final String[] SUFFIX_TEXT = {
            " [Red]", " [Gold]", " [Yellow]", " [Green]", " [DarkGreen]",
            " [Aqua]", " [DarkAqua]", " [Blue]", " [DarkBlue]", " [Pink]",
            " [Purple]", " [Gray]", " [DarkGray]", " [White]", " [DarkRed]",
            " [Black]", " [LightRed]", " [LightGreen]", " [LightAqua]", " [LightPink]"
    };

    private static final int[] SUFFIX_ARGB = {
            0xFFFF5555, // Red
            0xFFFFAA00, // Gold
            0xFFFFFF55, // Yellow
            0xFF55FF55, // Green
            0xFF00AA00, // DarkGreen
            0xFF55FFFF, // Aqua
            0xFF00AAAA, // DarkAqua
            0xFF5555FF, // Blue
            0xFF0000AA, // DarkBlue
            0xFFFF55FF, // Pink
            0xFFAA55AA, // Purple
            0xFFAAAAAA, // Gray
            0xFF555555, // DarkGray
            0xFFFFFFFF, // White
            0xFFAA0000, // DarkRed
            0xFF000000, // Black
            0xFFFF8888, // LightRed
            0xFF88FF88, // LightGreen
            0xFF88FFFF, // LightAqua
            0xFFFF88FF  // LightPink
    };

    private static final float MIN_VISIBLE_EDGE = 4.0f;
    private static final float DEFAULT_ROW_HEIGHT = 15f;
    private static final float DEFAULT_PADDING_X = 3f;
    private static final float DEFAULT_PADDING_Y = 3.5f;
    private static final float DEFAULT_ROW_SPACING = 0.0f;
    private static final float DEFAULT_RADIUS = 2f;
    private static final float SLIDE_DISTANCE = 18.0f;

    // Layout settings
    private ModeSetting sideMode;
    private BooleanSetting breakEnabled;
    private BooleanSetting showSuffix;
    private BooleanSetting suffixColorEnabled;
    private BooleanSetting suffixLowercaseEnabled;
    private BooleanSetting important;
    private NumberSetting paddingX;
    private NumberSetting paddingY;
    private NumberSetting rowHeight;
    private NumberSetting rowSpacing;

    // ========== 后缀括号开关 ==========
    private BooleanSetting suffixBracketsEnabled;

    // ========== 后缀颜色选择设置 ==========
    private ModeSetting suffixColorMode;

    // Background settings
    private BooleanSetting backgroundEnabled;
    private NumberSetting backgroundRadius;
    private NumberSetting backgroundAlpha;

    // Glow settings (模块周围发光)
    private BooleanSetting glowEnabled;
    private NumberSetting glowRadius;
    private NumberSetting glowAlpha;

    // Side line settings
    private BooleanSetting sideLineEnabled;
    private ModeSetting sideLineMode;
    private NumberSetting sideLineWidth;

    // Text color settings
    private BooleanSetting useClientColor;
    private ModeSetting textColorMode;
    private ModeSetting gradientTheme;
    private NumberSetting rainbowSpeed;
    private NumberSetting rainbowSaturation;
    private NumberSetting rainbowBrightness;
    private NumberSetting rainbowOffset;
    private BooleanSetting useMinecraftFont;

    // ========== 字体发光已禁用 ==========
    private static final boolean FONT_GLOW_ENABLED = false;

    private final SmoothAnimationTimer widthAnim = new SmoothAnimationTimer();
    private final SmoothAnimationTimer heightAnim = new SmoothAnimationTimer();
    private final Map<Module, AnimatedRow> rowStates = new IdentityHashMap<>();
    private boolean animationReady;

    public ModuleListHud() {
        super("ModuleList");
        this.setX(4.0f);
        this.setY(18.0f);
        this.setWidth(0.0f);
        this.setHeight(0.0f);
        this.setEnabled(true);
    }

    @Override
    public void registerSettings() {
        this.sideMode = new ModeSetting("Side Mode", "Auto", "Auto", "Left", "Right").withDefault("Auto");
        this.breakEnabled = new BooleanSetting("Break", false);
        this.showSuffix = new BooleanSetting("Show Suffix", true);
        this.suffixColorEnabled = new BooleanSetting("Suffix Color", true);
        this.suffixLowercaseEnabled = new BooleanSetting("Suffix Lowercase", false);

        // ========== 后缀括号开关 ==========
        this.suffixBracketsEnabled = new BooleanSetting("Suffix Brackets", true);

        this.important = new BooleanSetting("Important", false);
        this.paddingX = new NumberSetting("Padding X", DEFAULT_PADDING_X, 0.0f, 12.0f, 0.25f);
        this.paddingY = new NumberSetting("Padding Y", DEFAULT_PADDING_Y, 0.0f, 8.0f, 0.25f);
        this.rowHeight = new NumberSetting("Row Height", DEFAULT_ROW_HEIGHT, 9.0f, 24.0f, 0.25f);
        this.rowSpacing = new NumberSetting("Row Spacing", DEFAULT_ROW_SPACING, 0.0f, 8.0f, 0.25f);

        // ========== 20种后缀颜色选择 ==========
        this.suffixColorMode = new ModeSetting("Suffix Color", "White",
                "Red", "Gold", "Yellow", "Green", "DarkGreen",
                "Aqua", "DarkAqua", "Blue", "DarkBlue", "Pink",
                "Purple", "Gray", "DarkGray", "White", "DarkRed",
                "Black", "LightRed", "LightGreen", "LightAqua", "LightPink");

        this.backgroundEnabled = new BooleanSetting("Background", true);
        this.backgroundRadius = new NumberSetting("Background Radius", DEFAULT_RADIUS, 0.0f, 10.0f, 0.25f);
        this.backgroundAlpha = new NumberSetting("Background Alpha", 80.0f, 0.0f, 255.0f, 1.0f);
        this.glowEnabled = new BooleanSetting("Glow", false);
        this.glowRadius = new NumberSetting("Glow Radius", 15.0f, 4.0f, 40.0f, 1.0f);
        this.glowAlpha = new NumberSetting("Glow Alpha", 150.0f, 0.0f, 255.0f, 1.0f);
        this.sideLineEnabled = new BooleanSetting("Side Line", false);
        this.sideLineMode = new ModeSetting("Side Line Mode", "Auto", "Auto", "Left", "Right").withDefault("Auto");
        this.sideLineWidth = new NumberSetting("Side Line Width", 0.8f, 0.5f, 5.0f, 0.25f);
        this.useClientColor = new BooleanSetting("Use Client Color", false);
        this.textColorMode = new ModeSetting("Text Color Mode", "Gradient", "Solid").withDefault("Gradient");

        // ================================================================
        // Gradient Theme - 40+ 种颜色主题
        // ================================================================
        this.gradientTheme = new ModeSetting("Gradient Theme", "Cotton Candy",
                // === 原有颜色 (10种) ===
                "Rainbow", "Aurora", "Sunset", "Ocean", "Cotton Candy",
                "Lavender", "Peach", "Mint", "Cyberpunk", "Drift",
                // === 新增颜色 (30+种) ===
                // 红色系
                "Crimson", "Ruby", "Rose", "Candy", "Hot Pink", "Burgundy",
                // 橙色系
                "Tangerine", "Pumpkin", "Coral", "Amber", "Mango",
                // 黄色系
                "Lemon", "Banana", "Honey", "Mustard", "Butter",
                // 绿色系
                "Forest", "Lime", "Olive", "Teal", "Emerald", "Sage",
                // 蓝色系
                "Sky", "Navy", "Azure", "Turquoise", "Indigo", "Sapphire", "Cerulean",
                // 紫色系
                "Plum", "Lilac", "Amethyst", "Magenta", "Orchid",
                // 粉色系
                "Blush", "Bubblegum", "Salmon", "Flamingo", "Rose Gold",
                // 中性色
                "Silver", "Champagne", "Pearl", "Onyx", "Platinum",
                // 特殊色
                "Neon", "Pastel", "Vintage", "Retro", "Galaxy"
        ).withDefault("Cotton Candy");

        this.rainbowSpeed = new NumberSetting("Rainbow Speed", 5.0f, 1.0f, 240.0f, 1.0f);
        this.rainbowSaturation = new NumberSetting("Rainbow Saturation", 90.0f, 0.0f, 100.0f, 1.0f);
        this.rainbowBrightness = new NumberSetting("Rainbow Brightness", 100.0f, 10.0f, 100.0f, 1.0f);
        this.rainbowOffset = new NumberSetting("Rainbow Offset", 85.0f, 0.0f, 90.0f, 1.0f);
        this.useMinecraftFont = new BooleanSetting("Minecraft Font", false);

        this.registerSetting(sideMode, breakEnabled, showSuffix, suffixColorEnabled, suffixBracketsEnabled,
                suffixColorMode, suffixLowercaseEnabled, important,
                paddingX, paddingY, rowHeight, rowSpacing, backgroundEnabled, backgroundRadius, backgroundAlpha,
                sideLineEnabled, sideLineMode, sideLineWidth,
                glowEnabled, glowRadius, glowAlpha,
                useClientColor, useMinecraftFont, textColorMode, gradientTheme, rainbowSpeed, rainbowSaturation, rainbowBrightness, rainbowOffset);
    }

    /**
     * 获取当前选中的后缀颜色索引
     */
    private int getSelectedSuffixColorIndex() {
        String selected = this.suffixColorMode.getValue();
        for (int i = 0; i < SUFFIX_COLOR_NAMES.length; i++) {
            if (SUFFIX_COLOR_NAMES[i].equals(selected)) {
                return i;
            }
        }
        return 0;
    }

    /**
     * 获取当前选中的后缀颜色ARGB值
     */
    private int getSelectedSuffixColor() {
        return SUFFIX_ARGB[getSelectedSuffixColorIndex()];
    }

    /**
     * 获取当前选中的后缀文本
     */
    private String getSelectedSuffixText() {
        return SUFFIX_TEXT[getSelectedSuffixColorIndex()];
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

    private List<AnimatedRow> updateRows() {
        FontRenderer font = FontPresets.pingfang(16.0f);
        boolean importantOnly = this.important.getValue();
        boolean useMcFont = this.useMinecraftFont.getValue();
        for (Module module : NiloreClient.getInstance().getModuleManager().getModules()) {
            if (module == this || module.getName().isEmpty() || module.isHiddenInModuleList()) {
                this.rowStates.remove(module);
                continue;
            }
            if (importantOnly && module.getCategory() == client.nilore.modules.Category.RENDER) {
                this.rowStates.remove(module);
                continue;
            }
            AnimatedRow row = this.rowStates.get(module);
            if (module.isEnabled()) {
                if (row == null) {
                    row = new AnimatedRow(module);
                    this.rowStates.put(module, row);
                }
                String displayName = this.displayName(module);
                float textWidth = useMcFont
                        ? mc.font.width(displayName)
                        : GlHelper.getStringWidth(displayName, font);
                row.updateMetrics(displayName, textWidth, this.rowWidth(textWidth));
                row.setTargetVisible(true);
            } else if (row != null) {
                row.setTargetVisible(false);
            }
        }
        this.rowStates.values().forEach(AnimatedRow::tick);
        this.rowStates.values().removeIf(AnimatedRow::isFinishedRemoving);

        List<AnimatedRow> rows = new ArrayList<>(this.rowStates.values());
        rows.sort((a, b) -> Float.compare(b.textWidth, a.textWidth));
        return rows;
    }

    // ================================================================
    // displayName - 所有模块统一
    // ================================================================
    private String displayName(Module module) {
        String name = module.getName();
        if (!this.showSuffix.getValue()) {
            return name;
        }
        String suffix = module.getSuffix();
        if (suffix == null || suffix.isBlank()) {
            return name;
        }
        suffix = suffix.trim();

        if (!this.suffixBracketsEnabled.getValue()) {
            suffix = suffix.replace("[", "").replace("]", "");
            suffix = suffix.trim();
        }

        if (this.suffixLowercaseEnabled.getValue()) {
            suffix = suffix.toLowerCase(Locale.ROOT);
        }
        return name + " " + suffix;
    }

    // ================================================================
    // getSuffixColor - 所有模块统一跟随 Suffix Color 设置
    // ================================================================
    private int getSuffixColor(Module module) {
        if (!this.suffixColorEnabled.getValue()) {
            return 0xFF888888;
        }
        // 直接返回选中的后缀颜色，不经过 ModuleSuffixColorManager
        return getSelectedSuffixColor();
    }

    @Override
    public void onRender2D(Render2DEvent event, float x, float y) {
        ShaderCompatUtil.beginUIRender();

        try {
            if (!this.shouldRender()) {
                return;
            }
            List<AnimatedRow> rows = this.updateRows();
            if (rows.isEmpty()) {
                this.setWidth(0.0f);
                this.setHeight(0.0f);
                return;
            }

            float targetWidth = this.measureWidth(rows);
            float targetHeight = this.measureHeight(rows);
            float previousWidth = this.getWidth();
            Alignment anchorBeforeResize = this.resolveAlignment(x, Math.max(previousWidth, targetWidth));
            this.updateSizeAnimation(targetWidth, targetHeight);

            float width = this.widthAnim.getValueF();
            float height = this.heightAnim.getValueF();
            if (anchorBeforeResize == Alignment.RIGHT && !this.isDragging() && previousWidth > 0.0f) {
                this.setX(this.getX() + previousWidth - width);
            }
            this.clampToScreen(width, height);
            this.setWidth(width);
            this.setHeight(height);

            float drawX = this.getX();
            float drawY = this.getY();
            Alignment alignment = this.resolveAlignment(drawX, width);
            Renderer.render(event.guiGraphics(), drawContext -> this.renderRows(drawContext, rows, drawX, drawY, width, alignment));
        } finally {
            ShaderCompatUtil.endUIRender();
        }
    }

    private boolean shouldRender() {
        if (!this.isEnabled()) {
            return false;
        }
        Interface interfaceModule = NiloreClient.getInstance().getModuleManager().getModule(Interface.class);
        return interfaceModule == null || interfaceModule.isEnabled();
    }

    private float rowWidth(float textWidth) {
        float lineReserve = this.sideLineEnabled.getValue() ? this.sideLineWidth.getValue().floatValue() : 0.0f;
        return textWidth + this.paddingX.getValue().floatValue() * 2.0f + lineReserve;
    }

    private float measureWidth(List<AnimatedRow> rows) {
        float maxWidth = 0.0f;
        for (AnimatedRow row : rows) {
            maxWidth = Math.max(maxWidth, row.rowWidth);
        }
        return maxWidth;
    }

    private float measureHeight(List<AnimatedRow> rows) {
        float rowHeightValue = this.rowHeight.getValue().floatValue();
        float spacing = this.rowSpacing.getValue().floatValue();
        float height = 0.0f;
        boolean hasVisibleRow = false;
        for (AnimatedRow row : rows) {
            float progress = row.progress();
            if (progress <= 0.01f) {
                continue;
            }
            if (hasVisibleRow) {
                height += spacing * progress;
            }
            height += rowHeightValue * progress;
            hasVisibleRow = true;
        }
        return height;
    }

    private void updateSizeAnimation(float targetWidth, float targetHeight) {
        if (!this.animationReady) {
            this.widthAnim.setCurrentValue(targetWidth);
            this.widthAnim.setToValue(targetWidth);
            this.heightAnim.setCurrentValue(targetHeight);
            this.heightAnim.setToValue(targetHeight);
            this.animationReady = true;
            return;
        }
        this.widthAnim.animate(targetWidth, 0.18, Easings.EASE_OUT_SINE);
        this.heightAnim.animate(targetHeight, 0.18, Easings.EASE_OUT_SINE);
        this.widthAnim.tick();
        this.heightAnim.tick();
    }

    private void renderRows(DrawContext drawContext, List<AnimatedRow> rows, float x, float y, float width, Alignment alignment) {
        List<RowRenderLayout> layouts = this.computeRowLayouts(rows, x, y, width, alignment);
        boolean broken = this.breakEnabled.getValue();
        for (int i = 0; i < layouts.size(); i++) {
            this.renderRow(drawContext, rows, layouts.get(i), broken, alignment, i, layouts.size(), layouts);
        }
    }

    private void renderRow(DrawContext drawContext, List<AnimatedRow> rows, RowRenderLayout layout,
                           boolean broken, Alignment alignment, int rowIndex, int rowCount,
                           List<RowRenderLayout> layouts) {
        RoundedRectangle bounds = this.rowBounds(layout, broken, alignment, rowIndex, rowCount, layouts);
        int rowColor = this.colorForPosition(layout.rowIndex, 0.5f, Math.max(1, rows.size() - 1));

        // ====================================================================
        // 增强的模块周围发光 - 多层径向光晕
        // ====================================================================
        if (this.glowEnabled.getValue()) {
            float gRadius = this.glowRadius.getValue().floatValue();
            int gAlpha = Math.round(this.glowAlpha.getValue().floatValue() * layout.progress);
            if (gAlpha > 0 && gRadius > 0.0f) {
                // 获取发光颜色（使用行颜色）
                int glowColor = rowColor;

                // 绘制多层发光，从外到内渐变
                int layers = 8;
                for (int i = layers; i >= 0; i--) {
                    float progress = (float) i / (float) layers;
                    float radius = gRadius * progress;
                    int alpha = Math.round(gAlpha * (0.1f + 0.9f * (1.0f - progress)));

                    if (alpha <= 0 || radius <= 0.0f) continue;

                    // 扩展绘制区域
                    float expand = radius;
                    RoundedRectangle glowBounds = RoundedRectangle.ofXYWHR(
                            bounds.x1 - expand,
                            bounds.y1 - expand,
                            bounds.getWidth() + expand * 2.0f,
                            bounds.getHeight() + expand * 2.0f,
                            this.backgroundRadius.getValue().floatValue() + expand
                    );

                    try (Paint paint = new Paint()) {
                        paint.setColor((alpha << 24) | (glowColor & 0x00FFFFFF));
                        drawContext.drawRoundedRect(glowBounds, paint);
                    }
                }
                RenderUtil.enableBlend();
            }
        }

        if (this.backgroundEnabled.getValue()) {
            try (Paint paint = new Paint()) {
                int alpha = Math.round(this.backgroundAlpha.getValue().floatValue() * layout.progress);
                paint.setColor((alpha << 24) | 0x000000);
                if (this.backgroundRadius.getValue().floatValue() <= 0.0f) {
                    drawContext.drawRectXYWH(layout.x, layout.y, layout.width, layout.height, paint);
                } else {
                    drawContext.drawRoundedRect(bounds, paint);
                }
            }
        }
        if (this.sideLineEnabled.getValue()) {
            this.drawSideLine(drawContext, bounds, Argb.withAlpha(rowColor, layout.progress), alignment, broken);
        }
        drawContext.save();
        drawContext.clipRoundedRect(bounds, true);
        this.drawModuleName(drawContext, layout.row, layout.x, layout.y, layout.width, layout.fullHeight,
                layout.rowIndex, rows.size(), layout.progress, alignment);
        drawContext.restore();
    }

    private List<RowRenderLayout> computeRowLayouts(List<AnimatedRow> rows, float x, float y, float width, Alignment alignment) {
        List<RowRenderLayout> layouts = new ArrayList<>();
        float cursorY = y;
        float rowHeightValue = this.rowHeight.getValue().floatValue();
        float spacing = this.rowSpacing.getValue().floatValue();
        boolean hasRenderedRow = false;
        for (int i = 0; i < rows.size(); i++) {
            AnimatedRow row = rows.get(i);
            float progress = row.progress();
            if (progress <= 0.01f) {
                continue;
            }
            if (hasRenderedRow) {
                cursorY += spacing * progress;
            }
            float animatedWidth = Math.max(0.1f, row.rowWidth * progress);
            float animatedHeight = Math.max(0.1f, rowHeightValue * progress);
            float rowX = alignment == Alignment.RIGHT ? x + width - animatedWidth : x;
            float slideOffset = (alignment == Alignment.RIGHT ? SLIDE_DISTANCE : -SLIDE_DISTANCE) * (1.0f - progress);
            layouts.add(new RowRenderLayout(row, i, rowX + slideOffset, cursorY,
                    animatedWidth, animatedHeight, rowHeightValue, progress));
            cursorY += rowHeightValue * progress;
            hasRenderedRow = true;
        }
        for (int i = 0; i < layouts.size() - 1; i++) {
            layouts.get(i).linkTo(layouts.get(i + 1));
        }
        return layouts;
    }

    private RoundedRectangle rowBounds(RowRenderLayout layout, boolean broken, Alignment alignment, int rowIndex, int rowCount,
                                       List<RowRenderLayout> layouts) {
        float radius = this.backgroundRadius.getValue().floatValue();
        if (broken || radius <= 0.0f) {
            return RoundedRectangle.ofXYWHR(layout.x, layout.y, layout.width, layout.height, radius);
        }
        float widthDiffRadius = radius;
        if (rowIndex < rowCount - 1) {
            float nextWidth = layouts.get(rowIndex + 1).width;
            float diff = Math.abs(layout.width - nextWidth);
            widthDiffRadius = Math.min(radius, diff);
        }

        float tl, tr, br, bl;
        if (alignment == Alignment.LEFT) {
            tl = 0; tr = 0; br = widthDiffRadius; bl = 0;
            if (rowIndex == 0) { tl = radius; tr = radius; }
            if (rowIndex == rowCount - 1) { bl = radius; br = radius; }
        } else {
            tl = 0; tr = 0; br = 0; bl = widthDiffRadius;
            if (rowIndex == 0) { tl = radius; tr = radius; }
            if (rowIndex == rowCount - 1) { br = radius; bl = radius; }
        }
        return RoundedRectangle.ofXYWHRadii(layout.x, layout.y, layout.width, layout.visualHeight(false),
                new float[]{tl, tr, br, bl});
    }

    private RoundedRectangle expandedGlowBounds(RoundedRectangle bounds, float spread) {
        return RoundedRectangle.ofXYWHR(
                bounds.x1 - spread,
                bounds.y1 - spread,
                bounds.getWidth() + spread * 2.0f,
                bounds.getHeight() + spread * 2.0f,
                this.backgroundRadius.getValue().floatValue() + spread);
    }

    private void drawSideLine(DrawContext drawContext, RoundedRectangle bounds, int color, Alignment rowAlignment, boolean broken) {
        float lineWidth = this.sideLineWidth.getValue().floatValue();
        Alignment lineAlignment = this.resolveLineAlignment(rowAlignment);
        float lineX = lineAlignment == Alignment.RIGHT ? bounds.x2 - lineWidth : bounds.x1;
        try (Paint paint = new Paint()) {
            paint.setColor(color);
            drawContext.drawRoundedRect(RoundedRectangle.ofXYWHR(lineX, bounds.y1, lineWidth, bounds.getHeight(),
                    broken ? Math.min(this.backgroundRadius.getValue().floatValue(), lineWidth) : 0.0f), paint);
        }
    }

    // ========================================================================
    // drawModuleName - 所有模块统一使用 getSuffixColor
    // ========================================================================
    private void drawModuleName(DrawContext drawContext, AnimatedRow row, float rowX, float rowY, float rowWidth, float rowHeight,
                                int rowIndex, int rowCount, float alpha, Alignment alignment) {
        boolean useMcFont = this.useMinecraftFont.getValue();
        String text = row.fullDisplayName;
        Module module = row.module;

        float textWidth;
        float textY;
        FontRenderer font = FontPresets.pingfang(16.0f);

        if (useMcFont) {
            textWidth = mc.font.width(text);
            textY = rowY + (rowHeight + mc.font.lineHeight) / 2.0f - 10.0f + this.paddingY.getValue().floatValue() * 0.25f;
        } else {
            textWidth = GlHelper.getStringWidth(text, font);
            textY = rowY + (rowHeight - (float) GlHelper.getFontAscent(font)) / 2.0f + this.paddingY.getValue().floatValue() * 0.25f;
        }

        float padding = this.paddingX.getValue().floatValue();
        float lineReserve = this.sideLineEnabled.getValue() ? this.sideLineWidth.getValue().floatValue() : 0.0f;
        float textX = alignment == Alignment.RIGHT
                ? rowX + rowWidth - padding - textWidth - (this.resolveLineAlignment(alignment) == Alignment.RIGHT ? lineReserve : 0.0f)
                : rowX + padding + (this.resolveLineAlignment(alignment) == Alignment.LEFT ? lineReserve : 0.0f);

        int color = this.colorForPosition(rowIndex, 0.5f, Math.max(1, rowCount - 1));
        int finalColor = Argb.withAlpha(color, alpha);

        // ============================================================
        // 所有模块统一：只用 getSuffixColor，不做任何硬编码
        // ============================================================
        String namePart = text;
        String suffixPart = "";
        int suffixColor = finalColor;

        if (this.showSuffix.getValue() && this.suffixColorEnabled.getValue() && text.contains(" ")) {
            int lastSpace = text.lastIndexOf(' ');
            namePart = text.substring(0, lastSpace);
            suffixPart = text.substring(lastSpace + 1);
            suffixColor = getSuffixColor(module);
            suffixColor = Argb.withAlpha(suffixColor, alpha);
        }

        if (useMcFont) {
            var bufferSource = drawContext.getGuiGraphics().bufferSource();
            if (suffixPart.length() > 0) {
                mc.font.drawInBatch(namePart, textX, textY, finalColor, false,
                        drawContext.getPoseStack().last().pose(),
                        bufferSource, net.minecraft.client.gui.Font.DisplayMode.NORMAL, 0, 0xF000F0);
                float suffixX = textX + mc.font.width(namePart) + 2.0f;
                mc.font.drawInBatch(suffixPart, suffixX, textY, suffixColor, false,
                        drawContext.getPoseStack().last().pose(),
                        bufferSource, net.minecraft.client.gui.Font.DisplayMode.NORMAL, 0, 0xF000F0);
            } else {
                mc.font.drawInBatch(text, textX, textY, finalColor, false,
                        drawContext.getPoseStack().last().pose(),
                        bufferSource, net.minecraft.client.gui.Font.DisplayMode.NORMAL, 0, 0xF000F0);
            }
            bufferSource.endBatch();
        } else {
            if (suffixPart.length() > 0) {
                GlHelper.drawText(namePart, textX, textY, font, finalColor);
                float suffixX = textX + GlHelper.getStringWidth(namePart, font) + 2.0f;
                GlHelper.drawText(suffixPart, suffixX, textY, font, suffixColor);
            } else {
                GlHelper.drawText(text, textX, textY, font, finalColor);
            }
        }
    }

    public int getThemeColor(int positionIndex, float positionProgress, int maxPositionIndex) {
        return this.colorForPosition(positionIndex, positionProgress, maxPositionIndex);
    }

    private int colorForPosition(int rowIndex, float charProgress, int maxRowIndex) {
        if (this.useClientColor.getValue()) {
            return ColorUtil.getRainbowColor(this.rainbowSpeed.getValue().intValue(),
                    rowIndex * this.rainbowOffset.getValue().intValue()).getRGB();
        }
        String mode = this.textColorMode.getValue();
        int speed = this.rainbowSpeed.getValue().intValue();
        int offset = rowIndex * this.rainbowOffset.getValue().intValue();
        float saturation = this.rainbowSaturation.getValue().floatValue();
        float brightness = this.rainbowBrightness.getValue().floatValue();

        if ("Rainbow".equals(mode)) {
            return ColorUtil.getRainbowColor(speed, offset).getRGB();
        }
        if ("Gradient".equals(mode)) {
            GradientTheme theme = GradientTheme.fromName(this.gradientTheme.getValue());
            double position = (double) ((System.currentTimeMillis() / (long) speed + (long) offset) % 1000L) / 1000.0;
            return theme.getColorAt(position, saturation, brightness).getRGB();
        }
        if ("Solid".equals(mode)) {
            return ColorUtil.getRainbowColor(speed, 0).getRGB();
        }
        return ColorUtil.getRainbowColor(speed, offset).getRGB();
    }

    private Alignment resolveAlignment(float x, float width) {
        if ("Left".equals(this.sideMode.getValue())) {
            return Alignment.LEFT;
        }
        if ("Right".equals(this.sideMode.getValue())) {
            return Alignment.RIGHT;
        }
        return x + width / 2.0f < (float) mc.getWindow().getGuiScaledWidth() / 2.0f
                ? Alignment.LEFT
                : Alignment.RIGHT;
    }

    private Alignment resolveLineAlignment(Alignment rowAlignment) {
        if ("Left".equals(this.sideLineMode.getValue())) {
            return Alignment.LEFT;
        }
        if ("Right".equals(this.sideLineMode.getValue())) {
            return Alignment.RIGHT;
        }
        return rowAlignment;
    }

    private void clampToScreen(float width, float height) {
        if (mc == null || mc.getWindow() == null) {
            return;
        }
        float screenWidth = mc.getWindow().getGuiScaledWidth();
        float screenHeight = mc.getWindow().getGuiScaledHeight();
        float maxX = Math.max(MIN_VISIBLE_EDGE, screenWidth - Math.min(width, screenWidth) - MIN_VISIBLE_EDGE);
        float maxY = Math.max(MIN_VISIBLE_EDGE, screenHeight - Math.min(height, screenHeight) - MIN_VISIBLE_EDGE);
        this.setX(Mth.clamp(this.getX(), MIN_VISIBLE_EDGE, maxX));
        this.setY(Mth.clamp(this.getY(), MIN_VISIBLE_EDGE, maxY));
    }

    @Override
    public void mouseDragged(int mouseX, int mouseY) {
        this.setX((float) mouseX - this.getDragOffsetX());
        this.setY((float) mouseY - this.getDragOffsetY());
        this.clampToScreen(Math.max(this.getWidth(), 1.0f), Math.max(this.getHeight(), 1.0f));
    }

    @Override
    public void stopDragging() {
        boolean wasDragging = this.isDragging();
        super.stopDragging();
        if (wasDragging) {
            NiloreClient.getInstance().getConfigManager().saveAll();
        }
    }

    private static class Argb {
        static int withAlpha(int color, float alpha) {
            int a = Math.round(alpha * 255);
            return (a << 24) | (color & 0x00FFFFFF);
        }
    }

    @Override
    public void onGlRender(GlRenderEvent glRenderEvent, float x, float y) {
        if (glRenderEvent == null || glRenderEvent.drawContext() == null) return;
    }

    @Override
    public void onSettings() {
    }
}

// ============================================================
// 模块后缀颜色管理器 - 用于在ModuleElement和ModuleListHud之间同步颜色
// ============================================================
class ModuleSuffixColorManager {
    private static final Map<Module, Integer> suffixColors = new IdentityHashMap<>();
    private static final Map<Module, Boolean> suffixEnabled = new IdentityHashMap<>();

    public static int getSuffixColor(Module module) {
        return suffixColors.getOrDefault(module, 0xFF888888);
    }

    public static void setSuffixColor(Module module, int color) {
        suffixColors.put(module, color);
    }

    public static boolean isSuffixEnabled(Module module) {
        return suffixEnabled.getOrDefault(module, true);
    }

    public static void setSuffixEnabled(Module module, boolean enabled) {
        suffixEnabled.put(module, enabled);
    }
}