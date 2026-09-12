package client.nilore.modules.impl.render;

import client.nilore.event.EventTarget;
import client.nilore.event.impl.Render3DEvent;
import client.nilore.modules.Category;
import client.nilore.modules.Module;
import client.nilore.settings.impl.NumberSetting;
import net.minecraft.world.entity.player.Player;

@ModuleInfo(name = "Camera", description = "修改视角或实现视角动画", category = Category.RENDER)
public class Camera extends Module {

    // 示例设置：视角偏移量
    private final NumberSetting offsetX = new NumberSetting("OffsetX", 0.0, -2.0, 2.0, 0.1);
    private final NumberSetting offsetY = new NumberSetting("OffsetY", 0.0, -2.0, 2.0, 0.1);
    private final NumberSetting offsetZ = new NumberSetting("OffsetZ", 0.0, -2.0, 2.0, 0.1);
    private final NumberSetting rotationSpeed = new NumberSetting("RotationSpeed", 1.0, 0.0, 5.0, 0.1);

    public Camera() {
        super("Camera", Category.RENDER);
    }

    @EventTarget
    public void onRender3D(Render3DEvent event) {
        if (mc.player == null) return;

        Player player = mc.player;

        // 在这里实现视角修改逻辑，例如：
        // float yaw = player.getYRot();
        // float pitch = player.getXRot();
        // player.setYRot(yaw + (float) offsetX.getValue());
        // player.setXRot(pitch + (float) offsetY.getValue());

        // 注意：直接修改玩家视角可能被服务器回弹，通常仅用于本地渲染效果
    }

    @Override
    public void onEnable() {
        // 模块启用时的逻辑
    }

    @Override
    public void onDisable() {
        // 模块禁用时恢复默认视角
        if (mc.player != null) {
            // 这里可以保存并恢复视角
        }
    }

    @Override
    public String getSuffix() {
        return String.format("X:%.1f Y:%.1f", offsetX.getValue(), offsetY.getValue());
    }

    @Override
    public String getDisplayName() {
        return "";
    }

    @Override
    public String getModuleName() {
        return "Camera";
    }

    @Override
    public void onTick() {
    }
}