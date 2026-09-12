package client.nilore.modules.impl.movement.openssng;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.AirBlock;
import net.minecraft.world.level.block.BushBlock;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.function.Predicate;

public final class OpenSSNGClientRayTraceUtil {
    private static final Minecraft mc = Minecraft.getInstance();
    public static Vec3 eyePos;
    private OpenSSNGClientRayTraceUtil() {}

    public static void updateEyePos() {
        if (mc.player != null) eyePos = mc.player.getEyePosition(1.0F);
    }

    public static boolean didHitBlockFace(OpenSSNGRotation rotation, BlockPos targetPos, Direction expectedFace, boolean strict) {
        if (mc.player == null || rotation == null) return false;
        return didHitBlockFace(mc.player, rotation.yaw, rotation.pitch, targetPos, expectedFace, strict);
    }

    public static boolean didHitBlockFace(LocalPlayer player, float yaw, float pitch, BlockPos targetPos, Direction expectedFace, boolean strict) {
        BlockHitResult result = getFacedBlock(yaw, pitch, OpenSSNGClientRayTraceUtil::isIgnoredBlock);
        if (result == null || result.getType() != HitResult.Type.BLOCK || !result.getBlockPos().equals(targetPos)) return false;
        return !strict || result.getDirection() == expectedFace;
    }

    public static BlockHitResult getFacedBlock(float yaw, float pitch) {
        return getFacedBlock(yaw, pitch, OpenSSNGClientRayTraceUtil::isIgnoredBlock);
    }

    public static BlockHitResult getFacedBlock(float yaw, float pitch, Predicate<BlockState> ignored) {
        if (mc.player == null || mc.level == null) return null;
        Vec3 start = eyePos != null ? eyePos : mc.player.getEyePosition(1.0F);
        float cy = (float)Math.cos(-yaw * 0.017453292F - Math.PI);
        float sy = (float)Math.sin(-yaw * 0.017453292F - Math.PI);
        float cp = (float)-Math.cos(-pitch * 0.017453292F);
        float sp = (float)Math.sin(-pitch * 0.017453292F);
        Vec3 dir = new Vec3(sy * cp, sp, cy * cp);
        double reach = mc.gameMode != null ? mc.gameMode.getPickRange() : 4.5D;
        Vec3 end = start.add(dir.scale(reach));
        BlockHitResult hit = mc.level.clip(new ClipContext(start, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, mc.player));
        if (hit.getType() != HitResult.Type.BLOCK) return null;
        return ignored.test(mc.level.getBlockState(hit.getBlockPos())) ? null : hit;
    }

    public static boolean isIgnoredBlock(BlockState state) {
        var b = state.getBlock();
        return b instanceof AirBlock || b instanceof BushBlock || b instanceof LiquidBlock || state.getCollisionShape(mc.level, BlockPos.ZERO).isEmpty();
    }
}
