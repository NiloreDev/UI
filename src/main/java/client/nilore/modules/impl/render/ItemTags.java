package client.nilore.modules.impl.render;

import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import client.nilore.event.EventTarget;
import client.nilore.event.impl.Render2DEvent;
import client.nilore.event.impl.TickEvent;
import client.nilore.modules.Category;
import client.nilore.modules.Module;
import client.nilore.settings.impl.BooleanSetting;
import client.nilore.settings.impl.NumberSetting;
import client.nilore.utils.render.RenderUtil;

import java.util.*;

public class ItemTags extends Module {
    public static ItemTags INSTANCE;

    public final NumberSetting scale = new NumberSetting("Scale", 1.0, 0.25, 4.0, 0.1);
    public final NumberSetting renderOffsetX = new NumberSetting("Offset X", 0, -5, 5, 0.1);
    public final NumberSetting renderOffsetY = new NumberSetting("Offset Y", 0, -5, 5, 0.1);
    public final NumberSetting renderOffsetZ = new NumberSetting("Offset Z", 0, -5, 5, 0.1);
    public final BooleanSetting shulkerEnabled = new BooleanSetting("Shulker", false);

    private final List<ItemEntity> itemEntities = new ArrayList<>();

    public ItemTags() {
        super("ItemTags", Category.RENDER);
        INSTANCE = this;
    }

    @Override
    public String getModuleName() {
        return "ItemTags";
    }

    @Override
    public String getDisplayName() {
        return "§fItemTags";
    }

    @Override
    protected void onEnable() {
        super.onEnable();
        itemEntities.clear();
    }

    @Override
    protected void onDisable() {
        itemEntities.clear();
        super.onDisable();
    }

    @EventTarget
    public void onTick(TickEvent event) {
        if (mc.player == null || mc.level == null) return;
        itemEntities.clear();
        for (var entity : mc.level.entitiesForRendering()) {
            if (entity instanceof ItemEntity itemEntity) {
                itemEntities.add(itemEntity);
            }
        }
    }

    @EventTarget
    public void onRender2D(Render2DEvent event) {
        if (mc.player == null || mc.level == null) return;
        if (itemEntities.isEmpty()) return;

        float offsetX = renderOffsetX.getValue().floatValue();
        float offsetY = renderOffsetY.getValue().floatValue();
        float offsetZ = renderOffsetZ.getValue().floatValue();

        for (ItemEntity entity : itemEntities) {
            Vec3 pos = entity.position();
            Vec3 renderPos = pos.add(offsetX, offsetY, offsetZ);

            Vec3 screenPos = worldToScreen(renderPos);
            if (screenPos == null) continue;

            float x = (float) screenPos.x;
            float y = (float) screenPos.y;

            float sw = mc.getWindow().getGuiScaledWidth();
            float sh = mc.getWindow().getGuiScaledHeight();
            if (x < 0 || x > sw || y < 0 || y > sh) continue;

            ItemStack stack = entity.getItem();
            String name = stack.getHoverName().getString();
            String count = stack.getCount() > 1 ? " x" + stack.getCount() : "";
            String text = name + count;

            float textWidth = mc.font.width(text);
            float padding = 4;
            float boxW = textWidth + padding * 2;
            float boxH = mc.font.lineHeight + padding * 2;

            // 背景
            RenderUtil.drawFilledRect(null, x - boxW / 2, y - boxH / 2, boxW, boxH, 0x80000000);

            // ===== 修复：使用 drawInBatch =====
            var poseStack = new com.mojang.blaze3d.vertex.PoseStack();
            var bufferSource = mc.renderBuffers().bufferSource();
            mc.font.drawInBatch(text, x - textWidth / 2, y - boxH / 2 + padding, 0xFFFFFFFF, false,
                    poseStack.last().pose(), bufferSource, net.minecraft.client.gui.Font.DisplayMode.NORMAL, 0, 0xF000F0);
            bufferSource.endBatch();
        }
    }

    private Vec3 worldToScreen(Vec3 worldPos) {
        if (mc.player == null || mc.gameRenderer == null) return null;
        var camera = mc.gameRenderer.getMainCamera();
        if (camera == null) return null;

        Vec3 cameraPos = camera.getPosition();
        Vec3 relative = worldPos.subtract(cameraPos);

        double yaw = Math.toRadians(camera.getYRot());
        double pitch = Math.toRadians(camera.getXRot());

        double cosYaw = Math.cos(yaw);
        double sinYaw = Math.sin(yaw);
        double cosPitch = Math.cos(pitch);
        double sinPitch = Math.sin(pitch);

        double x = relative.x * cosYaw + relative.z * sinYaw;
        double y = relative.x * sinYaw * sinPitch + relative.y * cosPitch - relative.z * cosYaw * sinPitch;
        double z = -relative.x * sinYaw * cosPitch + relative.y * sinPitch + relative.z * cosYaw * cosPitch;

        if (z <= 0) return null;

        float sw = mc.getWindow().getGuiScaledWidth();
        float sh = mc.getWindow().getGuiScaledHeight();
        double fovScale = 1 / Math.tan(Math.toRadians(70 / 2));

        double screenX = sw / 2 + (x / z) * fovScale * sw / 2;
        double screenY = sh / 2 - (y / z) * fovScale * sh / 2;

        return new Vec3(screenX, screenY, 0);
    }
}