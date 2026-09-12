package client.nilore.modules.impl.movement.openssng;

import client.nilore.utils.rotation.Rotation;
import client.nilore.utils.rotation.RotationHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

public final class OpenSSNGRotationUtils {
    private static final Minecraft mc = Minecraft.getInstance();
    private OpenSSNGRotationUtils() {}

    public static OpenSSNGRotation getServerRotation() {
        Rotation r = RotationHandler.targetRotation;
        if (r != null) return new OpenSSNGRotation(r.getYaw(), r.getPitch());
        return mc.player == null ? new OpenSSNGRotation(0,0) : new OpenSSNGRotation(mc.player.getYRot(), mc.player.getXRot());
    }

    public static OpenSSNGRotation getClosestToBlockFace(BlockPos pos, Direction face, float baseYaw, float basePitch) {
        if (mc.player == null) return null;
        Vec3 eye = mc.player.getEyePosition(1.0F);
        Vec3 center = Vec3.atCenterOf(pos).add(face.getStepX()*0.5D, face.getStepY()*0.5D, face.getStepZ()*0.5D);
        double dx=center.x-eye.x, dy=center.y-eye.y, dz=center.z-eye.z;
        double h=Math.sqrt(dx*dx+dz*dz);
        return new OpenSSNGRotation(Mth.wrapDegrees((float)Math.toDegrees(Math.atan2(dz,dx))-90F), Mth.wrapDegrees((float)-Math.toDegrees(Math.atan2(dy,h))));
    }

    public static double normalizeYawDiff(float a, float b) { return Math.abs(Mth.wrapDegrees(a-b)); }
    public static double yawDiffDirectly(float a, float b) { return Mth.wrapDegrees(a-b); }
    public static float smooth(float diff, float max) { return Mth.clamp(diff, -Math.abs(max), Math.abs(max)); }

    public static void setRotation(OpenSSNGRotation r) {
        if (r == null) return;
        RotationHandler.setTargetRotation(new Rotation(r.yaw, r.pitch));
        RotationHandler.isRotating = true;
    }

    /**
     * Applies the exact Scaffold rotation before a block interaction is sent.
     *
     * OpenSSNG places from TickEvent, while Nilore normally injects targetRotation
     * into the later MotionEvent. Without an explicit movement-rotation packet here,
     * the server can receive USE_ITEM_ON before the flying/rotation packet and Grim
     * reports RotationPlace (pre-flying).
     */
    public static boolean applyBeforePlace(OpenSSNGRotation r) {
        if (r == null || mc.player == null || mc.getConnection() == null) return false;

        Rotation next = new Rotation(r.yaw, r.pitch);
        RotationHandler.setTargetRotation(next);
        RotationHandler.isRotating = true;

        // Keep Nilore's sent-rotation state coherent with the packet we are about
        // to put on the wire. RotationAnimation/RayTrace hooks read these fields.
        RotationHandler.prevSentRotation = RotationHandler.sentRotation;
        RotationHandler.sentRotation = new Rotation(r.yaw, r.pitch);
        RotationHandler.prevRotation = new Rotation(r.yaw, r.pitch);

        // Silent rotation: do NOT write player YRot/XRot/head/body fields here.
        // Those fields drive the local camera. Only the server/Nilore rotation
        // state is updated, so mouse look remains completely under user control.

        // IMPORTANT: this packet must be sent before gameMode.useItemOn(...).
        mc.getConnection().send(new ServerboundMovePlayerPacket.Rot(
                r.yaw,
                r.pitch,
                mc.player.onGround()
        ));
        return true;
    }
}
