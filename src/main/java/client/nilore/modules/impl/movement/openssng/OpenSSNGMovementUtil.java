package client.nilore.modules.impl.movement.openssng;

import net.minecraft.client.Minecraft;

public final class OpenSSNGMovementUtil {
    private static final Minecraft mc = Minecraft.getInstance();
    private OpenSSNGMovementUtil() {}

    public static boolean isMoving() {
        if (mc.player == null) return false;
        return Math.abs(mc.player.input.forwardImpulse) > 1.0E-3F || Math.abs(mc.player.input.leftImpulse) > 1.0E-3F;
    }

    public static boolean isOnEdge(double expand) {
        if (mc.player == null || mc.level == null) return false;
        return mc.player.onGround() && mc.level.noCollision(mc.player, mc.player.getBoundingBox().move(0.0D, -0.08D, 0.0D).deflate(expand, 0.0D, expand));
    }
}
