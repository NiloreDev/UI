package client.nilore.modules.impl.render;

import client.nilore.utils.render.ColorUtil;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.DyeableArmorItem;
import client.nilore.event.impl.GlRenderEvent;
import client.nilore.event.impl.Render2DEvent;
import client.nilore.hud.HudElement;
import client.nilore.render.DrawContext;
import client.nilore.render.Paint;
import client.nilore.render.RoundedRectangle;
import client.nilore.settings.impl.BooleanSetting;
import client.nilore.settings.impl.NumberSetting;
import client.nilore.utils.render.RenderUtil;

public class Armor extends HudElement {
    private static final float MIN_VISIBLE_EDGE = 4.0f;

    private final BooleanSetting backgroundSetting = new BooleanSetting("Background", true);
    private final NumberSetting bgAlphaSetting = new NumberSetting("Bg Alpha", 70, 0, 255, 1);
    private final BooleanSetting glowSetting = new BooleanSetting("Glow", false);
    private final NumberSetting glowRadiusSetting = new NumberSetting("Glow Radius", 12, 4, 40, 1);
    private final NumberSetting glowAlphaSetting = new NumberSetting("Glow Alpha", 120, 0, 255, 1);
    private final BooleanSetting durabilitySetting = new BooleanSetting("Durability", true);
    private final NumberSetting durabilityYOffset = new NumberSetting("Durability Y Offset", 2, 0, 10, 1);
    private final Paint bgPaint = new Paint();

    public Armor() {
        super("Armor");
        this.setX(10.0f);
        this.setY(120.0f);
        this.bgPaint.setAntialias(true);
    }

    @Override
    public void registerSettings() {
        this.registerSetting(backgroundSetting, bgAlphaSetting, glowSetting, glowRadiusSetting, glowAlphaSetting,
                durabilitySetting, durabilityYOffset);
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

    @Override
    public void onRender2D(Render2DEvent render2DEvent, float x, float y) {
    }

    @Override
    public void onGlRender(GlRenderEvent glRenderEvent, float x, float y) {
        if (mc.player == null) {
            return;
        }
        ItemStack[] armorItems = new ItemStack[]{
                mc.player.getItemBySlot(EquipmentSlot.HEAD),
                mc.player.getItemBySlot(EquipmentSlot.CHEST),
                mc.player.getItemBySlot(EquipmentSlot.LEGS),
                mc.player.getItemBySlot(EquipmentSlot.FEET)
        };

        // Skip rendering entirely when no armor is equipped
        boolean hasArmor = false;
        for (ItemStack stack : armorItems) {
            if (!stack.isEmpty()) {
                hasArmor = true;
                break;
            }
        }
        if (!hasArmor) {
            this.setWidth(0.0f);
            this.setHeight(0.0f);
            return;
        }

        DrawContext drawContext = glRenderEvent.drawContext();
        if (drawContext == null) {
            return;
        }

        float slotSize = 18.0f;
        float gap = 2.0f;
        float totalWidth = slotSize * 4.0f + gap * 3.0f;

        boolean showDurability = this.durabilitySetting.getValue();
        float durabilityTextHeight = showDurability ? 10.0f : 0.0f;
        float totalHeight = slotSize + durabilityTextHeight;

        float padding = 3.0f;
        float bgX = x - padding;
        float bgY = y - padding;
        float bgW = totalWidth + padding * 2.0f;
        float bgH = totalHeight + padding * 2.0f;
        float bgRadius = 4.5f;

        if (this.backgroundSetting.getValue()) {
            if (this.glowSetting.getValue()) {
                float gRadius = this.glowRadiusSetting.getValue().floatValue();
                int gAlpha = this.glowAlphaSetting.getValue().intValue();
                if (gAlpha > 0 && gRadius > 0.0f) {
                    RenderUtil.drawShadow(drawContext.getPoseStack(),
                            bgX, bgY, bgW, bgH,
                            (int) gRadius, (gAlpha << 24) | 0x000000);
                    RenderUtil.enableBlend();
                }
            }

            int alpha = this.bgAlphaSetting.getValue().intValue();
            this.bgPaint.setColor(ColorUtil.fromARGB(0, 0, 0, alpha));
            drawContext.drawRoundedRect(RoundedRectangle.ofXYWHR(bgX, bgY, bgW, bgH, bgRadius), this.bgPaint);
        }

        float durabilityYOffset = this.durabilityYOffset.getValue().floatValue();

        for (int i = 0; i < 4; ++i) {
            float itemX = x + (float)i * (slotSize + gap);
            ItemStack stack = armorItems[i];
            if (stack.isEmpty()) continue;

            // 渲染盔甲图标
            glRenderEvent.guiGraphics().renderItem(stack, (int)itemX, (int)y);
            glRenderEvent.guiGraphics().renderItemDecorations(mc.font, stack, (int)itemX, (int)y);

            // 渲染当前实时耐久度（在盔甲下方）
            if (showDurability && stack.isDamageableItem()) {
                int currentDurability = stack.getMaxDamage() - stack.getDamageValue(); // 当前剩余耐久

                // 只显示当前耐久值（不显示总耐久）
                String durabilityText = String.valueOf(currentDurability);

                // 根据耐久度设置颜色
                int maxDurability = stack.getMaxDamage();
                float durabilityPercent = (float) currentDurability / maxDurability;
                int color;
                if (durabilityPercent > 0.6f) {
                    color = 0xFF00FF00; // 绿色
                } else if (durabilityPercent > 0.3f) {
                    color = 0xFFFFFF00; // 黄色
                } else {
                    color = 0xFFFF0000; // 红色
                }

                // 计算文字位置（居中在盔甲图标下方）
                float textX = itemX + (slotSize - mc.font.width(durabilityText)) / 2.0f;
                float textY = y + slotSize + durabilityYOffset;

                // 绘制文字阴影
                drawContext.drawString(durabilityText, textX + 0.5f, textY + 0.5f, 0xCC000000);
                drawContext.drawString(durabilityText, textX, textY, color);
            }
        }

        this.setWidth(totalWidth);
        this.setHeight(totalHeight);
        this.clampToScreen(totalWidth, totalHeight);
    }

    @Override
    public void onSettings() {
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
            client.nilore.NiloreClient.getInstance().getConfigManager().saveAll();
        }
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
}