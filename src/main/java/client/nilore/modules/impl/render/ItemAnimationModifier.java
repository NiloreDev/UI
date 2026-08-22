package client.nilore.modules.impl.render;

import com.mojang.blaze3d.vertex.PoseStack;
import client.nilore.modules.Category;
import client.nilore.modules.Module;
import client.nilore.settings.impl.NumberSetting;

public class ItemAnimationModifier extends Module {
    public static ItemAnimationModifier INSTANCE;

    // ========== 设置 ==========
    private final NumberSetting speed = new NumberSetting("Speed", 1.0, 0.1, 5.0, 0.05);
    private final NumberSetting sizeX = new NumberSetting("Size X", 1.0, 0.1, 3.0, 0.05);
    private final NumberSetting sizeY = new NumberSetting("Size Y", 1.0, 0.1, 3.0, 0.05);
    private final NumberSetting sizeZ = new NumberSetting("Size Z", 1.0, 0.1, 3.0, 0.05);
    private final NumberSetting offsetX = new NumberSetting("Offset X", 0.0, -1.0, 1.0, 0.01);
    private final NumberSetting offsetY = new NumberSetting("Offset Y", 0.0, -1.0, 1.0, 0.01);
    private final NumberSetting offsetZ = new NumberSetting("Offset Z", 0.0, -1.0, 1.0, 0.01);

    public ItemAnimationModifier() {
        super("ItemAnimationModifier", Category.RENDER);
        INSTANCE = this;
    }

    @Override
    public String getDisplayName() {
        return "";
    }

    @Override
    public String getModuleName() {
        return "";
    }

    /**
     * 应用修改到 PoseStack（完整版，包含进度修改）
     */
    public void applyModifiers(PoseStack poseStack, float progress, float equipProgress, int hand) {
        // 1. 应用大小
        float sx = this.sizeX.getValue().floatValue();
        float sy = this.sizeY.getValue().floatValue();
        float sz = this.sizeZ.getValue().floatValue();
        poseStack.scale(sx, sy, sz);

        // 2. 应用偏移
        float ox = this.offsetX.getValue().floatValue();
        float oy = this.offsetY.getValue().floatValue();
        float oz = this.offsetZ.getValue().floatValue();
        poseStack.translate(ox, oy, oz);

        // 3. 应用速度（通过进度控制）
        float modifiedProgress = getModifiedProgress(progress);
        // 速度影响已经在 getModifiedProgress 中处理，这里不需要额外操作
    }

    /**
     * 获取修改后的进度值
     */
    public float getModifiedProgress(float progress) {
        return progress * this.speed.getValue().floatValue();
    }

    /**
     * 获取所有修改应用到 PoseStack（无进度参数版本，用于静态调用）
     */
    public void applyStaticModifiers(PoseStack poseStack) {
        float sx = this.sizeX.getValue().floatValue();
        float sy = this.sizeY.getValue().floatValue();
        float sz = this.sizeZ.getValue().floatValue();
        poseStack.scale(sx, sy, sz);

        float ox = this.offsetX.getValue().floatValue();
        float oy = this.offsetY.getValue().floatValue();
        float oz = this.offsetZ.getValue().floatValue();
        poseStack.translate(ox, oy, oz);
    }

    @Override
    public String getSuffix() {
        return String.format("%.1fx", this.speed.getValue().floatValue());
    }
}