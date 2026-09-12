package client.nilore.modules.impl.movement.openssng;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

public final class OpenSSNGFallingPlayer {
    private double x, y, z;
    private double motionX, motionY, motionZ;
    private final float yaw;
    private final float pitch;
    private final double eyeHeight;

    public OpenSSNGFallingPlayer(LocalPlayer p) {
        x = p.getX(); y = p.getY(); z = p.getZ();
        Vec3 v = p.getDeltaMovement();
        motionX = v.x; motionY = v.y; motionZ = v.z;
        yaw = p.getYRot(); pitch = p.getXRot(); eyeHeight = p.getEyeHeight();
    }

    public void calculate(int ticks) {
        for (int i=0;i<ticks;i++) {
            x += motionX; y += motionY; z += motionZ;
            motionY = (motionY - 0.08D) * 0.98D;
            motionX *= 0.91D; motionZ *= 0.91D;
        }
    }

    public Vec3 getEyePos() { return new Vec3(x, y + eyeHeight, z); }
    public double getY() { return y; }
    public float getYaw() { return Mth.wrapDegrees(yaw); }
    public float getPitch() { return Mth.clamp(pitch, -90F, 90F); }
}
