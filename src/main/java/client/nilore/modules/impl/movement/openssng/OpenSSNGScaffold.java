package client.nilore.modules.impl.movement.openssng;

import client.nilore.event.EventTarget;
import client.nilore.event.impl.TickEvent;
import client.nilore.modules.Category;
import client.nilore.modules.Module;
import client.nilore.settings.impl.BooleanSetting;
import client.nilore.settings.impl.ModeSetting;
import client.nilore.settings.impl.NumberSetting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ServerboundSwingPacket;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.Arrays;
import java.util.List;

/**
 * Port of OpenSSNG Scaffold/Clutch to NiloreDev/UI (MC 1.20.1 official mappings).
 * Southside/OneConfig/Mixin-only dependencies were translated to Nilore settings/events.
 */
public final class OpenSSNGScaffold extends Module {
    public final ModeSetting mode = new ModeSetting("Mode", "Telly", "Snap", "Normal").withDefault("Telly");

    // Common settings: these are genuinely used by every placement mode.
    public final BooleanSetting alwaysUpdateRot = new BooleanSetting("Always Update Rotation", false);
    public final BooleanSetting noSwing = new BooleanSetting("No Swing", false);
    public final BooleanSetting fixRotation = new BooleanSetting("Fix Rotation", true);
    public final ModeSetting blockSlotMode = new ModeSetting("Block Slot Mode", "Farthest", "Most Blocks").withDefault("Farthest");
    public final NumberSetting safeDistance = new NumberSetting("Clutch Safe Distance", 4.5D, 1D, 5D, 0.25D);

    // Telly-only settings. Nilore evaluates SettingVisibility dynamically, so
    // none of these appear in the GUI while Snap/Normal is selected.
    public final NumberSetting placeTick = new NumberSetting("Telly PlaceTick", 1, 1, 5, 1, () -> mode.is("Telly"));
    public final NumberSetting rotTick = new NumberSetting("Telly RotationTick", 1, 1, 5, 1, () -> mode.is("Telly"));
    public final BooleanSetting smoothed = new BooleanSetting("Telly Smooth Rotation", true, () -> mode.is("Telly"));
    public final BooleanSetting safeMode = new BooleanSetting("Telly Safe Mode", false, () -> mode.is("Telly"));
    public final BooleanSetting testOnGround = new BooleanSetting("Telly Test OnGround", false, () -> mode.is("Telly") && safeMode.getValue());
    public final ModeSetting jumpMode = new ModeSetting("Telly Jump Mode", "Parkour", "Normal", "None")
            .withDefault("Normal")
            .withVisibility(() -> mode.is("Telly"));
    public final BooleanSetting eagle = new BooleanSetting("Telly Eagle", false, () -> mode.is("Telly"));
    public final NumberSetting tellyEagleTick = new NumberSetting("Telly EagleTick", 1, 1, 5, 1,
            () -> mode.is("Telly") && eagle.getValue());
    public final NumberSetting keepEagleSneakTick = new NumberSetting("Telly KeepEagleTick", 1, 1, 5, 1,
            () -> mode.is("Telly") && eagle.getValue());

    // Snap/Normal behavior is intentionally parameter-free here. When either
    // mode is selected, Telly-only controls disappear instead of remaining as
    // dead settings. Their runtime paths are also completely separate below.


    private static final List<Block> INVALID_BLOCKS = Arrays.asList(
        Blocks.ENCHANTING_TABLE, Blocks.OAK_SIGN, Blocks.CHEST, Blocks.ENDER_CHEST, Blocks.TRAPPED_CHEST,
        Blocks.ANVIL, Blocks.SAND, Blocks.COBWEB, Blocks.TORCH, Blocks.CRAFTING_TABLE, Blocks.FURNACE,
        Blocks.DISPENSER, Blocks.STONE_PRESSURE_PLATE, Blocks.NOTE_BLOCK, Blocks.DROPPER, Blocks.TNT,
        Blocks.REDSTONE_TORCH, Blocks.DAYLIGHT_DETECTOR
    );

    private int oldSlot;
    private int selectedBlockSlot = -1;
    private int airTicks;
    private int groundTicks;
    private int tellyJumpTicks;
    private int placeCount;
    private double posY;
    private OpenSSNGBlockData blockData;
    private OpenSSNGRotation lastRotation;
    private boolean waitingForEagleSneak;

    public OpenSSNGScaffold() { super("OpenSSNG Scaffold", Category.MOVEMENT); }

    @Override public String getModuleName() { return "OpenSSNG Scaffold"; }
    @Override public String getDisplayName() { return "§fOpenSSNG Scaffold[" + mode.getValue() + "]"; }
    @Override public String getSuffix() { return "[" + mode.getValue() + "]"; }
    @Override public void onTick() {}

    @Override
    protected void onEnable() {
        if (mc.player == null) return;
        oldSlot = mc.player.getInventory().selected;
        selectedBlockSlot = oldSlot;
        posY = Math.floor(mc.player.getY() - 1.0D);
        airTicks = groundTicks = tellyJumpTicks = placeCount = 0;
        blockData = null;
        lastRotation = new OpenSSNGRotation(mc.player.getYRot(), mc.player.getXRot());
        waitingForEagleSneak = false;
        super.onEnable();
    }

    @Override
    protected void onDisable() {
        if (mc.player != null) {
            mc.player.getInventory().selected = oldSlot;
            mc.options.keyShift.setDown(false);
        }
        super.onDisable();
    }

    @EventTarget(value = 1)
    public void onClientTick(TickEvent event) {
        if (mc.player == null || mc.level == null || mc.gameMode == null) return;
        OpenSSNGClientRayTraceUtil.updateEyePos();
        updateGroundTicks();
        selectBlockSlot();
        if (selectedBlockSlot < 0) return;

        if (mc.player.onGround()) posY = Math.floor(mc.player.getY() - 1.0D);
        if (mc.options.keyJump.isDown()) posY = mc.player.blockPosition().getY() - 1.0D;

        if (mode.is("Telly")) handleJumpAndEagle();
        else mc.options.keyShift.setDown(false);
        OpenSSNGBlockData found = findPlacement(new BlockPos(Mth.floor(mc.player.getX()), Mth.floor(posY), Mth.floor(mc.player.getZ())));
        if (found != null) blockData = found;
        if (blockData == null) return;

        OpenSSNGFallingPlayer prediction = new OpenSSNGFallingPlayer(mc.player);
        prediction.calculate(2);
        OpenSSNGBlockData rescue = findPlacement(new BlockPos(Mth.floor(mc.player.getX()), mc.player.blockPosition().getY() - 1, Mth.floor(mc.player.getZ())));
        boolean forceRot = false;
        if (rescue != null) {
            double d = prediction.getEyePos().distanceTo(Vec3.atCenterOf(rescue.pos()));
            if (d >= safeDistance.getValue().doubleValue() || rescue.pos().getY() > prediction.getY()) {
                blockData = rescue;
                forceRot = true;
            }
        }

        OpenSSNGRotation rot = getBlockRotation(forceRot);
        if (rot == null) return;
        if (fixRotation.getValue()) rot.fixedSensitivity(mc.options.sensitivity().get());
        OpenSSNGRotationUtils.setRotation(rot);

        if (mode.is("Telly")) {
            handleTellyMode(rot);
        } else if (mode.is("Snap")) {
            handleSnapMode(rot);
        } else {
            handleNormalMode(rot);
        }
    }


    /** Telly owns all jump/timing/smoothing settings. */
    private void handleTellyMode(OpenSSNGRotation rot) {
        boolean canPlace = airTicks >= placeTick.getValue().intValue();
        if (safeMode.getValue() && testOnGround.getValue()
                && groundTicks == 1 && mc.options.keyJump.isDown()) {
            canPlace = true;
        }
        if (canPlace) place(rot);
    }

    /** Snap has its own fixed placement rule and never reads Telly settings. */
    private void handleSnapMode(OpenSSNGRotation rot) {
        if (isAirBelow(1)) place(rot);
    }

    /** Normal continuously places when a valid support face is available. */
    private void handleNormalMode(OpenSSNGRotation rot) {
        place(rot);
    }

    private void updateGroundTicks() {
        if (mc.player.onGround()) { groundTicks++; airTicks = 0; }
        else { airTicks++; groundTicks = 0; }
    }

    private void handleJumpAndEagle() {
        if (mode.is("Telly") && OpenSSNGMovementUtil.isMoving() && groundTicks > (safeMode.getValue() ? 1 : 0) && !mc.options.keyJump.isDown()) {
            if (jumpMode.is("Normal")) mc.player.jumpFromGround();
            else if (jumpMode.is("Parkour") && isAirAhead()) mc.player.jumpFromGround();
            if (eagle.getValue()) { waitingForEagleSneak = true; tellyJumpTicks = 0; }
        }
        if (waitingForEagleSneak) {
            tellyJumpTicks++;
            int start = tellyEagleTick.getValue().intValue();
            int keep = keepEagleSneakTick.getValue().intValue();
            if (tellyJumpTicks >= start && tellyJumpTicks < start + keep) mc.options.keyShift.setDown(true);
            else if (tellyJumpTicks >= start + keep) { mc.options.keyShift.setDown(false); waitingForEagleSneak = false; }
        }
    }

    private boolean isAirAhead() {
        double yaw = Math.toRadians(mc.player.getYRot());
        double fx = -Math.sin(yaw), fz = Math.cos(yaw);
        BlockPos one = BlockPos.containing(mc.player.getX()+fx, mc.player.getY()-0.1D, mc.player.getZ()+fz);
        BlockPos two = BlockPos.containing(mc.player.getX()+fx*2D, mc.player.getY()-0.1D, mc.player.getZ()+fz*2D);
        return mc.level.getBlockState(one).isAir() || mc.level.getBlockState(two).isAir();
    }

    private OpenSSNGRotation getBlockRotation(boolean force) {
        OpenSSNGRotation server = OpenSSNGRotationUtils.getServerRotation();
        OpenSSNGRotation rot = OpenSSNGRotationUtils.getClosestToBlockFace(blockData.pos(), blockData.facing(), server.yaw, server.pitch);
        if (rot == null) return lastRotation;
        if (mode.is("Telly") && smoothed.getValue() && !force && airTicks < rotTick.getValue().intValue()) {
            double diff = OpenSSNGRotationUtils.yawDiffDirectly(rot.yaw, server.yaw);
            rot.yaw = server.yaw + OpenSSNGRotationUtils.smooth((float)diff, airTicks == 1 ? 80F : 50F);
        }
        if (!alwaysUpdateRot.getValue() && lastRotation != null && OpenSSNGClientRayTraceUtil.didHitBlockFace(lastRotation, blockData.pos(), blockData.facing(), true)) return lastRotation;
        lastRotation = rot.copy();
        return rot;
    }

    private void place(OpenSSNGRotation rot) {
        if (!OpenSSNGClientRayTraceUtil.didHitBlockFace(rot, blockData.pos(), blockData.facing(), true)) return;

        // TickEvent runs before Nilore's normal MotionEvent rotation injection.
        // Force the exact yaw/pitch onto the wire first, then place. This keeps
        // Grim's rotation state synchronized with the following block interaction.
        if (!OpenSSNGRotationUtils.applyBeforePlace(rot)) return;

        // Re-check with the exact rotation that was just sent. Do not interact if
        // the final ray no longer lands on the expected support face.
        OpenSSNGClientRayTraceUtil.updateEyePos();
        BlockHitResult traced = OpenSSNGClientRayTraceUtil.getFacedBlock(rot.yaw, rot.pitch);
        if (traced == null
                || !traced.getBlockPos().equals(blockData.pos())
                || traced.getDirection() != blockData.facing()) return;

        mc.player.getInventory().selected = selectedBlockSlot;
        Vec3 hit = traced.getLocation();
        BlockHitResult bhr = new BlockHitResult(hit, traced.getDirection(), traced.getBlockPos(), traced.isInside());
        InteractionResult result = mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, bhr);
        if (result.consumesAction()) {
            placeCount++;
            if (noSwing.getValue()) {
                if (mc.getConnection() != null) mc.getConnection().send(new ServerboundSwingPacket(InteractionHand.MAIN_HAND));
            } else mc.player.swing(InteractionHand.MAIN_HAND);
        }
    }

    private void selectBlockSlot() {
        int slot = blockSlotMode.is("Most Blocks") ? getMostBlocksHotbarSlot() : getFarthestBlockSlot();
        if (slot < 0) {
            int inv = findInventoryBlock();
            if (inv >= 9) {
                OpenSSNGInventoryUtil.swapInventoryToHotbar(inv, oldSlot);
                slot = oldSlot;
            }
        }
        selectedBlockSlot = slot;
    }

    private int getFarthestBlockSlot() {
        int slot=-1;
        for(int i=0;i<9;i++) if (isValid(mc.player.getInventory().getItem(i))) slot=i;
        return slot;
    }

    private int getMostBlocksHotbarSlot() {
        int best=-1,count=-1;
        for(int i=0;i<9;i++) {
            ItemStack s=mc.player.getInventory().getItem(i);
            if(isValid(s) && s.getCount()>count){best=i;count=s.getCount();}
        }
        return best;
    }

    private int findInventoryBlock() {
        for(int i=9;i<36;i++) if(isValid(mc.player.getInventory().getItem(i))) return i;
        return -1;
    }

    private boolean isValid(ItemStack stack) {
        if (stack == null || stack.isEmpty() || !(stack.getItem() instanceof BlockItem bi)) return false;
        return !INVALID_BLOCKS.contains(bi.getBlock()) && OpenSSNGInventoryUtil.isFullBlock(stack);
    }

    public int getBlockCountHotbar() {
        int n=0; for(int i=0;i<9;i++){ItemStack s=mc.player.getInventory().getItem(i); if(isValid(s))n+=s.getCount();} return n;
    }

    public int getBlockCountInventory() {
        int n=0; for(int i=0;i<36;i++){ItemStack s=mc.player.getInventory().getItem(i); if(isValid(s))n+=s.getCount();} return n;
    }

    private OpenSSNGBlockData findPlacement(BlockPos target) {
        if (!mc.level.getBlockState(target).isAir()) return null;
        for (Direction side : new Direction[]{Direction.DOWN,Direction.NORTH,Direction.SOUTH,Direction.WEST,Direction.EAST,Direction.UP}) {
            BlockPos neighbour = target.relative(side);
            BlockState state = mc.level.getBlockState(neighbour);
            if (!state.isAir() && !state.getCollisionShape(mc.level, neighbour).isEmpty()) return new OpenSSNGBlockData(neighbour, side.getOpposite());
        }
        // radius-2 fallback, preserving OpenSSNG's clutch-oriented search style
        for(int r=1;r<=2;r++) for(int x=-r;x<=r;x++) for(int z=-r;z<=r;z++) {
            BlockPos p=target.offset(x,0,z);
            for(Direction side:Direction.values()) {
                BlockPos n=p.relative(side); BlockState s=mc.level.getBlockState(n);
                if(mc.level.getBlockState(p).isAir() && !s.isAir() && !s.getCollisionShape(mc.level,n).isEmpty()) return new OpenSSNGBlockData(n, side.getOpposite());
            }
        }
        return null;
    }

    private boolean isAirBelow(int down) { return mc.level.getBlockState(mc.player.blockPosition().below(down)).isAir(); }
}
