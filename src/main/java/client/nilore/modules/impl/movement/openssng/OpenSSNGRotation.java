package client.nilore.modules.impl.movement.openssng;

import net.minecraft.util.Mth;

public final class OpenSSNGRotation {
    public float yaw;
    public float pitch;

    public OpenSSNGRotation(float yaw, float pitch) {
        this.yaw = yaw;
        this.pitch = pitch;
    }

    public OpenSSNGRotation copy() { return new OpenSSNGRotation(yaw, pitch); }

    public void fixedSensitivity(double sensitivity) {
        double f = sensitivity * 0.6D + 0.2D;
        double gcd = f * f * f * 8.0D * 0.15D;
        yaw -= (float)(yaw % gcd);
        pitch -= (float)(pitch % gcd);
        pitch = Mth.clamp(pitch, -90.0F, 90.0F);
    }
}
