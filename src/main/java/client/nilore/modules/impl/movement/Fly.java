package client.nilore.modules.impl.movement;

import net.minecraft.world.phys.Vec3;
import client.nilore.event.EventTarget;
import client.nilore.event.impl.GameTickEvent;
import client.nilore.modules.Category;
import client.nilore.modules.Module;
import client.nilore.settings.impl.NumberSetting;

public class Fly extends Module {
    public static Fly INSTANCE;

    // ================================================================
    // 速度设置
    // ================================================================
    public final NumberSetting horizontalSpeed = new NumberSetting("Horizontal Speed", 0.44, 0.1, 2.0, 0.01);
    public final NumberSetting verticalSpeed = new NumberSetting("Vertical Speed", 0.44, 0.1, 2.0, 0.01);

    public Fly() {
        super("Fly", Category.MOVEMENT);
        INSTANCE = this;
    }

    // ================================================================
    // getModuleName - 返回模块名称
    // ================================================================
    @Override
    public String getModuleName() {
        return "Fly";
    }

    // ================================================================
    // getDisplayName - 覆盖主题颜色，全部显示为白色
    // ================================================================
    @Override
    public String getDisplayName() {
        return "§fFly";
    }

    @EventTarget
    public void onGameTick(GameTickEvent event) {
        if (mc.player == null) return;

        boolean forward = mc.options.keyUp.isDown();
        boolean back = mc.options.keyDown.isDown();
        boolean left = mc.options.keyLeft.isDown();
        boolean right = mc.options.keyRight.isDown();
        boolean jump = mc.options.keyJump.isDown();
        boolean sneak = mc.options.keyShift.isDown();

        Vec3 lookVec = mc.player.getLookAngle();
        Vec3 forwardVec = new Vec3(lookVec.x, 0, lookVec.z).normalize();
        Vec3 rightVec = forwardVec.cross(new Vec3(0, 1, 0)).normalize();

        double moveForward = 0;
        double moveStrafe = 0;
        if (forward) moveForward += 1;
        if (back) moveForward -= 1;
        if (right) moveStrafe += 1;
        if (left) moveStrafe -= 1;

        double length = Math.sqrt(moveForward * moveForward + moveStrafe * moveStrafe);
        if (length > 1.0) {
            moveForward /= length;
            moveStrafe /= length;
        }

        // ===== 使用可调节的水平速度 =====
        double hSpeed = this.horizontalSpeed.getValue().doubleValue();
        Vec3 horizontalVelocity = new Vec3(0, 0, 0);
        if (moveForward != 0 || moveStrafe != 0) {
            Vec3 dir = forwardVec.scale(moveForward).add(rightVec.scale(moveStrafe));
            horizontalVelocity = dir.scale(hSpeed);
        }

        // ===== 使用可调节的垂直速度 =====
        double vSpeed = this.verticalSpeed.getValue().doubleValue();
        double yMotion;
        if (jump) {
            yMotion = vSpeed;
        } else if (sneak) {
            yMotion = -vSpeed;
        } else {
            yMotion = 0.0;
        }

        mc.player.setDeltaMovement(horizontalVelocity.x, yMotion, horizontalVelocity.z);
        mc.player.setOnGround(true);
        mc.player.fallDistance = 0;
    }
}