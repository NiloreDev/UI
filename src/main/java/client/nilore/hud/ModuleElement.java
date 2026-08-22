package client.nilore.hud;

import client.nilore.utils.render.ColorUtil;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import lombok.Generated;
import net.minecraft.client.gui.GuiGraphics;
import client.nilore.gui.NewClickGui;
import client.nilore.gui.newclickgui.BooleanSettingElement;
import client.nilore.gui.newclickgui.ModeSettingElement;
import client.nilore.gui.newclickgui.MultiSelectSettingElement;
import client.nilore.gui.newclickgui.NumberSettingElement;
import client.nilore.gui.newclickgui.SettingElement;
import client.nilore.gui.newclickgui.UIElement;
import client.nilore.modules.Module;
import client.nilore.render.FontStore;
import client.nilore.settings.Setting;
import client.nilore.settings.impl.BooleanSetting;
import client.nilore.settings.impl.ModeSetting;
import client.nilore.settings.impl.MultiSelectSetting;
import client.nilore.settings.impl.NumberSetting;
import client.nilore.utils.animation.SmoothAnimationTimer;
import client.nilore.utils.math.Easings;
import client.nilore.utils.misc.CursorUtil;
import client.nilore.utils.render.RenderHelper;
import client.nilore.utils.render.RenderUtil;

public class ModuleElement
        extends UIElement {
    public static final int BG_COLOR;
    @Getter
    private final List<SettingElement<?>> settingElements = new ArrayList<>();
    @Getter
    private final client.nilore.gui.newclickgui.ModuleElement parentPanel;
    @Getter
    private final Module module;
    @Getter
    private final SmoothAnimationTimer enabledTimer = new SmoothAnimationTimer();
    private final SmoothAnimationTimer hoveredTimer = new SmoothAnimationTimer();
    private final SmoothAnimationTimer expandTimer = new SmoothAnimationTimer();
    private final SmoothAnimationTimer settingsHeightTimer = new SmoothAnimationTimer();
    private float posX;
    private float posY;
    private float totalHeight = 20.0f;
    @Getter @Setter
    private float scrollOffset;
    @Getter @Setter
    private boolean isHovered;
    @Getter @Setter
    private boolean isExpanded;
    private static final String BUILD_TAG;

    public ModuleElement(client.nilore.gui.newclickgui.ModuleElement categoryPanel, Module module) {
        this.parentPanel = categoryPanel;
        this.module = module;
        for (Setting setting : module.getSettings()) {
            if (setting instanceof BooleanSetting booleanSetting) {
                this.settingElements.add(new BooleanSettingElement(categoryPanel.getParentPanel(), booleanSetting));
                continue;
            }
            if (setting instanceof ModeSetting modeSetting) {
                this.settingElements.add(new ModeSettingElement(categoryPanel.getParentPanel(), modeSetting));
                continue;
            }
            if (setting instanceof MultiSelectSetting multiSelectSetting) {
                this.settingElements.add(new MultiSelectSettingElement(categoryPanel.getParentPanel(), multiSelectSetting));
                continue;
            }
            if (!(setting instanceof NumberSetting numberSetting)) continue;
            this.settingElements.add(new NumberSettingElement(categoryPanel.getParentPanel(), numberSetting));
        }
    }

    @Override
    public void render(NewClickGui clickGui, GuiGraphics guiGraphics, PoseStack poseStack, int mouseX, int mouseY, float alpha, float partialTicks) {
        float titleY;
        float enabledAmount;
        float settingsTotalHeight = 0.0f;
        for (SettingElement settingElement : this.settingElements) {
            if (!settingElement.getSetting().getVisibility().displayable()) continue;
            settingsTotalHeight += settingElement.getHeight();
        }
        this.settingsHeightTimer.animate(settingsTotalHeight, 0.2, Easings.EASE_OUT_POW2);
        this.settingsHeightTimer.tick();
        this.parentPanel.setCollapsed(!this.settingsHeightTimer.isDone());
        this.hoveredTimer.animate(this.isHovered ? 1.0 : 0.0, 0.2, Easings.EASE_OUT_POW2);
        this.hoveredTimer.tick();
        this.enabledTimer.animate(this.module.isEnabled() ? 1.0 : 0.0, 0.3, Easings.EASE_OUT_POW2);
        this.enabledTimer.tick();
        this.expandTimer.animate(this.isExpanded ? 1.0 : 0.0, 0.2, Easings.EASE_OUT_POW3);
        this.expandTimer.tick();
        float expandAmount = this.expandTimer.getValueF();
        this.totalHeight = 20.0f + expandAmount * this.settingsHeightTimer.getValueF();
        this.isHovered = this.parentPanel.equals(NewClickGui.focusedPanel) && CursorUtil.isInBounds(mouseX, mouseY, this.posX, this.posY, 120.0f, this.totalHeight);
        RenderUtil.drawFilledRect(poseStack, this.posX + 1.0f, this.posY + 20.0f, 118.0f, this.totalHeight - 20.0f, ColorUtil.withAlpha(BG_COLOR, expandAmount * alpha));
        float hoverAmount = this.hoveredTimer.getValueF();
        if (hoverAmount > 0.0f) {
            RenderUtil.drawFilledRect(poseStack, this.posX + 0.5f, this.posY, 119.0f, 20.0f, ColorUtil.withAlpha(-1, 0.1f * alpha * hoverAmount));
        }

        // 拆分名字：所有模块 前面原色，后面灰色
        String fullName = this.module.getName();
        String[] parts = fullName.split(" ", 2);
        String mainName = parts[0];
        String suffix = parts.length > 1 ? " " + parts[1] : "";

        // 未激活状态
        if (1.0f - (enabledAmount = this.enabledTimer.getValueF()) > 0.0f) {
            float yText = this.posY + (20.0f - FontStore.AXIFORMA_REGULAR_16.getFontHeight()) / 2.0f;
            float wMain = FontStore.AXIFORMA_REGULAR_16.getStringWidth(mainName);
            float wSuffix = FontStore.AXIFORMA_REGULAR_16.getStringWidth(suffix);
            float totalW = wMain + wSuffix;
            float x = this.posX + 60.0f - totalW / 2.0f;

            // 主名：原来颜色
            FontStore.AXIFORMA_REGULAR_16.drawString(poseStack, mainName, x, yText,
                    ColorUtil.withAlpha(-1, alpha * (1.0f - enabledAmount) * 0.6f));

            // 后缀：灰色（所有模块都生效）
            if (!suffix.isEmpty()) {
                FontStore.AXIFORMA_REGULAR_16.drawString(poseStack, suffix, x + wMain, yText,
                        ColorUtil.withAlpha(0xFFAAAAAA, alpha * (1.0f - enabledAmount) * 0.6f));
            }
        }

        // 激活状态
        if (enabledAmount > 0.0f) {
            titleY = this.posY + (20.0f - FontStore.AXIFORMA_BOLD_16.getFontHeight()) / 2.0f;
            float wMain = FontStore.AXIFORMA_BOLD_16.getStringWidth(mainName);
            float wSuffix = FontStore.AXIFORMA_BOLD_16.getStringWidth(suffix);
            float totalW = wMain + wSuffix;
            float x = this.posX + 60.0f - totalW / 2.0f;

            RenderUtil.drawShadow(poseStack, x, titleY + FontStore.AXIFORMA_BOLD_16.getFontHeight() / 4.0f,
                    totalW, FontStore.AXIFORMA_BOLD_16.getFontHeight() / 2.0f, 12,
                    ColorUtil.withAlpha(-13768502, alpha * enabledAmount * 0.36f));

            // 主名：原来颜色
            FontStore.AXIFORMA_BOLD_16.drawString(poseStack, mainName, x, titleY,
                    ColorUtil.withAlpha(-13768502, alpha * enabledAmount));

            // 后缀：灰色（所有模块都生效）
            if (!suffix.isEmpty()) {
                FontStore.AXIFORMA_BOLD_16.drawString(poseStack, suffix, x + wMain, titleY,
                        ColorUtil.withAlpha(0xFFAAAAAA, alpha * enabledAmount));
            }
        }

        if (!this.module.getSettings().isEmpty()) {
            String arrowIcon = String.valueOf('\ueb4e');
            float arrowSize = FontStore.MATERIAL_20.getStringWidth(arrowIcon);
            float arrowX = this.posX + 120.0f - arrowSize - 6.0f;
            float arrowY = this.posY + (20.0f - FontStore.MATERIAL_20.getFontHeight()) / 2.0f + 1.0f;
            RenderHelper.pushRotateAround(poseStack, arrowX + arrowSize / 2.0f, arrowY + FontStore.MATERIAL_20.getFontHeight() / 2.0f - 1.0f, 180.0f * expandAmount);
            FontStore.MATERIAL_20.drawString(poseStack, arrowIcon, arrowX, arrowY, ColorUtil.withAlpha(-1, (0.8f - 0.3f * expandAmount) * alpha));
            RenderHelper.popPose(poseStack);
        }
        if (this.isExpanded) {
            float settingY = this.posY + 20.0f;
            for (SettingElement settingElement : this.settingElements) {
                settingElement.setX(this.posX);
                settingElement.setY(settingY);
                settingElement.render(clickGui, guiGraphics, poseStack, mouseX, mouseY, alpha * expandAmount, partialTicks);
                settingY += settingElement.getAnimatedHeight() * settingElement.getVisibilityTimer().getValueF();
            }
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!this.isHovered) {
            return false;
        }
        if (CursorUtil.isInBounds((float) mouseX, (float) mouseY, this.posX, this.posY, 120.0f, 20.0f)) {
            if (button == 0) {
                this.module.setEnabled(!this.module.isEnabled());
            } else if (button == 1 && !this.module.getSettings().isEmpty()) {
                this.isExpanded = !this.isExpanded;
            }
            return true;
        }
        if (CursorUtil.isInBounds((float) mouseX, (float) mouseY, this.posX, this.posY + 20.0f, 120.0f, this.totalHeight - 20.0f)) {
            Iterator<SettingElement<?>> iterator = this.settingElements.iterator();
            while (iterator.hasNext() && !iterator.next().mouseClicked(mouseX, mouseY, button)) {
            }
        }
        return this.isHovered;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (this.isHovered && CursorUtil.isInBounds((float) mouseX, (float) mouseY, this.posX, this.posY + 20.0f, 120.0f, this.totalHeight - 20.0f)) {
            Iterator<SettingElement<?>> iterator = this.settingElements.iterator();
            while (iterator.hasNext() && !iterator.next().mouseReleased(mouseX, mouseY, button)) {
            }
        }
        return this.isHovered;
    }

    @Override
    @Generated
    public SmoothAnimationTimer getAnimTimer() {
        return this.hoveredTimer;
    }

    @Generated
    public SmoothAnimationTimer getHoveredTimer() {
        return this.expandTimer;
    }

    @Generated
    public SmoothAnimationTimer getExpandTimer() {
        return this.settingsHeightTimer;
    }

    @Override
    @Generated
    public float getX() {
        return this.posX;
    }

    @Override
    @Generated
    public float getY() {
        return this.posY;
    }

    @Override
    @Generated
    public float getHeight() {
        return this.totalHeight;
    }

    @Override
    @Generated
    public void setX(float x) {
        this.posX = x;
    }

    @Override
    @Generated
    public void setY(float y) {
        this.posY = y;
    }

    @Override
    @Generated
    public void setHeight(float height) {
        this.totalHeight = height;
    }

    static {
        BUILD_TAG = "17";
        BG_COLOR = ColorUtil.fromRGB(32, 32, 32);
    }
}