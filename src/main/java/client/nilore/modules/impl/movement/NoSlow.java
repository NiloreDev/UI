package client.nilore.modules.impl.movement;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.datafixers.util.Pair;
import java.util.ArrayDeque;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.*;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import org.lwjgl.glfw.GLFW;
import client.nilore.event.EventTarget;
import client.nilore.event.impl.MotionEvent;
import client.nilore.event.impl.PacketEvent;
import client.nilore.event.impl.SlowdownEvent;
import client.nilore.event.impl.TickEvent;
import client.nilore.modules.Category;
import client.nilore.modules.Module;
import client.nilore.modules.impl.exploit.FastPlace;
import client.nilore.settings.impl.BooleanSetting;
import client.nilore.settings.impl.ModeSetting;
import client.nilore.settings.impl.NumberSetting;
import client.nilore.utils.animation.Timer;
import client.nilore.utils.misc.PacketUtil;

public class NoSlow extends Module {
    public static NoSlow INSTANCE;
    public static boolean releaseItemSent;

    public static boolean isActive() {
        return false;
    }

    // ==================== 状态枚举 ====================
    private enum Step { NONE, ARMED, EATING }

    // ==================== 主模式选择 ====================
    public final ModeSetting mode = new ModeSetting("Mode", "None", "GrimAC", "Grim", "LeakGrim", "Heypixel").withDefault("GrimAC");

    // ==================== GrimAC 模式专用设置 ====================
    public final BooleanSetting grimACBowNoSlow = new BooleanSetting("Bow", false, this::isGrimACMode);
    public final BooleanSetting grimACKeepSprinting = new BooleanSetting("Keep Sprinting", true, this::isGrimACMode);
    public final BooleanSetting grimACCrossbowNoSlow = new BooleanSetting("Crossbow", false, this::isGrimACMode);
    public final BooleanSetting grimACFoodNoSlow = new BooleanSetting("Food", true, this::isGrimACMode);
    public final BooleanSetting grimACPotionNoSlow = new BooleanSetting("Potion", true, this::isGrimACMode);
    public final BooleanSetting grimACShieldNoSlow = new BooleanSetting("Shield NoSlow", true, this::isGrimACMode);
    public final NumberSetting grimACUseItemTicks = new NumberSetting("Use Item Ticks", 1, 1, 20, 1,
            () -> this.isGrimACMode() && this.grimACBowNoSlow.getValue());

    // ==================== Grim 模式专用设置 ====================
    public final BooleanSetting grimBowNoSlow = new BooleanSetting("Bow", false, this::isGrimMode);
    public final BooleanSetting grimKeepSprinting = new BooleanSetting("Keep Sprinting", true, this::isGrimMode);
    public final BooleanSetting grimCrossbowNoSlow = new BooleanSetting("Crossbow", false, this::isGrimMode);
    public final BooleanSetting grimFoodNoSlow = new BooleanSetting("Food", true, this::isGrimMode);
    public final BooleanSetting grimPotionNoSlow = new BooleanSetting("Potion", true, this::isGrimMode);
    public final BooleanSetting grimShieldNoSlow = new BooleanSetting("Shield NoSlow", true, this::isGrimMode);
    public final NumberSetting grimUseItemTicks = new NumberSetting("Use Item Ticks", 1, 1, 20, 1,
            () -> this.isGrimMode() && this.grimBowNoSlow.getValue());

    // ==================== LeakGrim 模式专用设置 ====================
    public final BooleanSetting leakBlockSetting = new BooleanSetting("Block", false, this::isLeakGrimMode);
    public final BooleanSetting leakFoodSetting = new BooleanSetting("Food", true, this::isLeakGrimMode);
    public final BooleanSetting leakBowSetting = new BooleanSetting("Bow", false, this::isLeakGrimMode);
    public final BooleanSetting leakKeepSprinting = new BooleanSetting("Keep Sprinting", true, this::isLeakGrimMode);
    public final NumberSetting leakBowTick = new NumberSetting("BowTick", 20, 1, 20, 1, this::isLeakGrimMode);
    public final NumberSetting leakEatTick = new NumberSetting("EatTick", 32, 1, 32, 1, this::isLeakGrimMode);

    // ==================== Heypixel 模式专用设置 ====================
    public final BooleanSetting heypixelFood = new BooleanSetting("Food", true, this::isHeypixelMode);
    public final BooleanSetting heypixelBow = new BooleanSetting("Bow", true, this::isHeypixelMode);
    public final BooleanSetting heypixelCrossbow = new BooleanSetting("Crossbow", true, this::isHeypixelMode);
    public final BooleanSetting heypixelPotion = new BooleanSetting("Potion", true, this::isHeypixelMode);
    public final BooleanSetting heypixelKeepSprinting = new BooleanSetting("Keep Sprinting", true, this::isHeypixelMode);

    // ==================== 状态变量 ====================
    private final Timer timer = new Timer();
    private final Queue<Packet<ClientGamePacketListener>> inboundQueue = new ArrayDeque<>();
    private final Queue<Packet<?>> cached = new ConcurrentLinkedQueue<>();

    // LeakGrim 模式专用状态
    private InteractionHand leakCurrentHand = InteractionHand.MAIN_HAND;
    private InteractionHand leakOppositeHand = InteractionHand.MAIN_HAND;
    private boolean leakIsBlinking = false;
    private boolean leakNeedsRelease = false;
    private boolean leakSendingPackets = false;
    private boolean leakPendingUse = false;
    private boolean leakJustUsed = false;
    private InteractionHand leakPendingHand = InteractionHand.MAIN_HAND;
    private int leakUseSequence = 0;
    private int leakCooldownTicks = 0;
    private int leakTicksSinceSwap = 0;
    private long leakLastUseTime = 0;
    private int leakDelayedPacketsCount = 0;
    private int leakDelayedPacketsMax = 0;
    private boolean leakDelayingPackets = false;
    private final Timer leakTimer = new Timer();

    // GrimAC 模式状态
    private InteractionHand useHand = InteractionHand.MAIN_HAND;
    private InteractionHand lastUseHand = InteractionHand.MAIN_HAND;
    private InteractionHand pendingUseHand;
    private boolean didSwapHand;
    private boolean shouldReleaseItem;
    private int swapInitSlot;
    private int releaseTicksRemaining;
    private int pendingUseCount;
    private boolean isBlinking;
    private int blinkTicks;
    private int blinkDuration;
    private Step step = Step.NONE;
    private boolean hasSwapped = false;
    private boolean swapInArmed = false;
    private int noUseTicks = 0;

    // GrimAC-only local first-person hand alias
    private boolean grimACVisualLock = false;
    private ItemStack grimACVisualMain = ItemStack.EMPTY;
    private ItemStack grimACVisualOff = ItemStack.EMPTY;
    private InteractionHand grimACVisualUseHand = InteractionHand.MAIN_HAND;
    private int grimACVisualStableTicks = 0;

    // ==================== Heypixel 模式字段（来自第2个文件的 Default 逻辑） ====================
    private boolean bowActive;
    private boolean bowDelay;
    private boolean resFoodSwap;
    private boolean heypixelGrimFoodSwap;
    private InteractionHand heypixelOriginalHand = InteractionHand.MAIN_HAND;
    private boolean heypixelGrimSwapSent;



    public NoSlow() {
        super("NoSlow", Category.MOVEMENT);
        INSTANCE = this;
    }

    // ================================================================
    // 生命周期
    // ================================================================

    @Override
    public void onEnable() {
        releaseItemSent = false;
        this.releaseTicksRemaining = 0;
        this.reset();
        this.stopBlink();
        this.leakReset();
        this.heypixelReset();
        this.heypixelGrimFoodSwap = false;
        this.heypixelGrimSwapSent = false;
        this.clearGrimACVisualLock();
        super.onEnable();
    }

    @Override
    public void onDisable() {
        if (this.mode.is("LeakGrim")) {
            this.leakReleaseItem();
        }
        if (this.isHeypixelMode()) {
            this.heypixelRelease();
        }
        this.release();
        this.stopBlink();
        releaseItemSent = false;
        this.didSwapHand = false;
        this.shouldReleaseItem = false;
        this.pendingUseHand = null;
        this.releaseTicksRemaining = 0;
        this.bowActive = false;
        this.bowDelay = false;
        this.restoreUseKeyState();
        this.clearGrimACVisualLock();
        super.onDisable();
    }

    // ================================================================
    // 显示名称
    // ================================================================

    @Override
    public String getModuleName() {
        return "NoSlow";
    }

    @Override
    public String getSuffix() {
        String modeName = mode.getValue();
        if (modeName == null || modeName.isEmpty()) return null;
        return "[" + modeName + "]";
    }

    @Override
    public void onTick() {

    }

    @Override
    public String getDisplayName() {
        String modeName = mode.getValue();
        if (modeName == null || modeName.isEmpty()) {
            return "§fNoSlow";
        }
        return "§fNoSlow[" + modeName + "]";
    }

    // ================================================================
    // 模式判断
    // ================================================================

    private boolean isGrimACMode() {
        return this.mode.is("GrimAC");
    }

    // ==================== GrimAC-only first-person render API ====================

    public static boolean isGrimACVisualLockActive() {
        return INSTANCE != null
                && INSTANCE.isEnabled()
                && INSTANCE.isGrimACMode()
                && INSTANCE.grimACVisualLock;
    }

    public static ItemStack getGrimACVisualMainHand() {
        return INSTANCE != null ? INSTANCE.grimACVisualMain : ItemStack.EMPTY;
    }

    public static ItemStack getGrimACVisualOffHand() {
        return INSTANCE != null ? INSTANCE.grimACVisualOff : ItemStack.EMPTY;
    }

    public static InteractionHand getGrimACVisualUseHand() {
        return INSTANCE != null
                ? INSTANCE.grimACVisualUseHand
                : InteractionHand.MAIN_HAND;
    }

    private void beginGrimACVisualLock(InteractionHand originalHand) {
        if (!this.isGrimACMode() || mc.player == null) {
            return;
        }
        if (this.grimACVisualLock) {
            return;
        }
        this.grimACVisualMain = mc.player.getMainHandItem().copy();
        this.grimACVisualOff = mc.player.getOffhandItem().copy();
        this.grimACVisualUseHand = originalHand;
        this.grimACVisualStableTicks = 0;
        this.grimACVisualLock = true;
    }

    private void clearGrimACVisualLock() {
        this.grimACVisualLock = false;
        this.grimACVisualMain = ItemStack.EMPTY;
        this.grimACVisualOff = ItemStack.EMPTY;
        this.grimACVisualUseHand = InteractionHand.MAIN_HAND;
        this.grimACVisualStableTicks = 0;
    }

    private void tickGrimACVisualLock() {
        if (!this.grimACVisualLock) {
            return;
        }
        if (!this.isGrimACMode() || mc.player == null) {
            this.clearGrimACVisualLock();
            return;
        }
        if (this.step != Step.NONE || this.hasSwapped || this.swapInArmed) {
            this.grimACVisualStableTicks = 0;
            return;
        }
        boolean restored =
                ItemStack.matches(mc.player.getMainHandItem(), this.grimACVisualMain)
                        && ItemStack.matches(mc.player.getOffhandItem(), this.grimACVisualOff);
        if (restored) {
            if (++this.grimACVisualStableTicks >= 2) {
                this.clearGrimACVisualLock();
            }
        } else {
            this.grimACVisualStableTicks = 0;
        }
    }

    private boolean isGrimMode() {
        return this.mode.is("Grim");
    }

    private boolean isLeakGrimMode() {
        return this.mode.is("LeakGrim");
    }

    private boolean isHeypixelMode() {
        return this.mode.is("Heypixel");
    }

    private boolean areAllOff() {
        if (this.isHeypixelMode()) {
            // Heypixel 由独立开关控制
            return !this.heypixelFood.getValue()
                    && !this.heypixelBow.getValue()
                    && !this.heypixelCrossbow.getValue()
                    && !this.heypixelPotion.getValue();
        }
        if (this.isGrimACMode()) {
            return !this.grimACBowNoSlow.getValue() && !this.grimACKeepSprinting.getValue()
                    && !this.grimACCrossbowNoSlow.getValue() && !this.grimACFoodNoSlow.getValue()
                    && !this.grimACPotionNoSlow.getValue() && !this.grimACShieldNoSlow.getValue();
        }
        if (this.isGrimMode()) {
            return !this.grimBowNoSlow.getValue() && !this.grimKeepSprinting.getValue()
                    && !this.grimCrossbowNoSlow.getValue() && !this.grimFoodNoSlow.getValue()
                    && !this.grimPotionNoSlow.getValue() && !this.grimShieldNoSlow.getValue();
        }
        if (this.isLeakGrimMode()) {
            return !this.leakBowSetting.getValue() && !this.leakFoodSetting.getValue() && !this.leakBlockSetting.getValue();
        }
        return true;
    }

    // ================================================================
    // LeakGrim 模式核心逻辑
    // ================================================================

    private boolean leakHasDualItems() {
        if (mc.player == null) return false;
        ItemStack mainHand = mc.player.getMainHandItem();
        ItemStack offHand = mc.player.getOffhandItem();
        if (mainHand.isEmpty() || offHand.isEmpty()) return false;
        return mainHand.getItem() == offHand.getItem() ||
                (leakIsSlowingItem(mainHand.getUseAnimation()) && leakIsSlowingItem(offHand.getUseAnimation()));
    }

    private boolean leakIsSlowingItem(UseAnim anim) {
        return anim == UseAnim.EAT || anim == UseAnim.DRINK || anim == UseAnim.BOW;
    }

    private boolean leakIsProjectileItem(UseAnim anim) {
        return anim == UseAnim.BOW || anim == UseAnim.CROSSBOW || anim == UseAnim.SPEAR;
    }

    private boolean leakIsCrossbowCharged(ItemStack stack) {
        if (stack == null || !(stack.getItem() instanceof CrossbowItem)) return false;
        CompoundTag tag = stack.getTag();
        return tag != null && tag.getBoolean("Charged");
    }

    private void leakPerformGrimSwap(InteractionHand hand, int sequence) {
        if (mc.player == null) return;
        leakIsBlinking = true;
        leakNeedsRelease = false;
        leakOppositeHand = hand;
        leakTicksSinceSwap = mc.player.getInventory().selected;
        leakCurrentHand = leakOppositeHand == InteractionHand.MAIN_HAND ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;

        PacketUtil.sendQueued(new ServerboundPlayerActionPacket(
                ServerboundPlayerActionPacket.Action.SWAP_ITEM_WITH_OFFHAND,
                BlockPos.ZERO, Direction.DOWN));
        PacketUtil.sendQueued(new ServerboundUseItemPacket(leakCurrentHand, sequence));

        leakDelayedPacketsMax = ((Number) leakEatTick.getValue()).intValue();
        leakDelayedPacketsCount = 0;
        leakDelayingPackets = true;
    }

    private void leakReleaseItem() {
        leakNeedsRelease = false;
        if (mc.player == null || mc.getConnection() == null) return;

        leakDelayingPackets = false;
        mc.options.keyUse.setDown(false);

        if (leakIsBlinking) {
            if (leakCurrentHand != leakOppositeHand) {
                PacketUtil.sendQueued(new ServerboundPlayerActionPacket(
                        ServerboundPlayerActionPacket.Action.SWAP_ITEM_WITH_OFFHAND,
                        BlockPos.ZERO, Direction.DOWN));
            }
            leakIsBlinking = false;
            leakCooldownTicks = 3;
            leakTimer.reset();
        }
        PacketUtil.sendQueued(new ServerboundPlayerActionPacket(
                ServerboundPlayerActionPacket.Action.RELEASE_USE_ITEM,
                BlockPos.ZERO, Direction.DOWN));
    }

    private void leakSchedulePendingUse(InteractionHand hand, int sequence) {
        leakPendingUse = true;
        leakPendingHand = hand;
        leakUseSequence = sequence;
    }

    private void leakScheduleRelease() {
        leakSendingPackets = true;
    }

    private void leakClearUseKey() {
        mc.options.keyUse.setDown(false);
        while (mc.options.keyUse.consumeClick()) {
        }
    }

    private void leakReset() {
        leakIsBlinking = false;
        leakNeedsRelease = false;
        leakSendingPackets = false;
        leakPendingUse = false;
        leakJustUsed = false;
        leakCooldownTicks = 0;
        leakDelayingPackets = false;
        leakDelayedPacketsCount = 0;
        leakCurrentHand = InteractionHand.MAIN_HAND;
        leakOppositeHand = InteractionHand.MAIN_HAND;
        leakPendingHand = InteractionHand.MAIN_HAND;
        leakTimer.reset();
        mc.options.keyUse.setDown(false);
    }

    private void leakTick() {
        if (mc.player == null) return;

        if (leakCooldownTicks > 0) {
            leakClearUseKey();
            leakCooldownTicks--;
        }

        int maxTicks = leakIsProjectileItem(mc.player.getUseItem().getUseAnimation()) ?
                ((Number) leakBowTick.getValue()).intValue() :
                ((Number) leakEatTick.getValue()).intValue();

        if (leakDelayingPackets) {
            leakDelayedPacketsCount++;
            if (leakDelayedPacketsCount >= maxTicks) {
                leakScheduleRelease();
                leakDelayingPackets = false;
            }
        }

        if (leakNeedsRelease && mc.player.isUsingItem() && !leakHasDualItems() &&
                !leakIsCrossbowCharged(mc.player.getUseItem())) {
            leakPerformGrimSwap(mc.player.getUsedItemHand(), 0);
        }
    }

    // ================================================================
    // 原方法
    // ================================================================

    private boolean canSwapHands() {
        if (mc.player == null) return false;
        ItemStack mainHand = mc.player.getMainHandItem();
        ItemStack offHand = mc.player.getOffhandItem();
        if (mainHand.isEmpty() || offHand.isEmpty()) return true;
        if (mainHand.getItem() == Items.ENCHANTED_GOLDEN_APPLE && offHand.getItem() == Items.GOLDEN_APPLE) return false;
        if (offHand.getItem() == Items.ENCHANTED_GOLDEN_APPLE && mainHand.getItem() == Items.GOLDEN_APPLE) return false;
        return mainHand.getItem() != offHand.getItem();
    }

    public static boolean isBlocking(Minecraft minecraft) {
        return INSTANCE != null && INSTANCE.isBlockingInternal(minecraft);
    }

    private boolean isBlockingInternal(Minecraft minecraft) {
        if (!this.isEnabled() || !this.isGrimACMode()) return false;
        if (minecraft == null || minecraft.player == null || minecraft.hitResult == null) return false;
        if (minecraft.hitResult.getType() != HitResult.Type.BLOCK) return false;
        for (InteractionHand hand : InteractionHand.values()) {
            if (isUsable(minecraft.player.getItemInHand(hand).getUseAnimation())) return true;
        }
        return false;
    }

    private boolean isUsable(UseAnim action) {
        return action == UseAnim.EAT || action == UseAnim.DRINK || action == UseAnim.SPEAR;
    }

    private boolean isLookingAtInteractableBlock() {
        return isLookingAtInteractableBlock(mc);
    }

    public static boolean isLookingAtInteractableBlock(Minecraft minecraft) {
        if (minecraft == null || minecraft.player == null || minecraft.level == null) return false;
        if (!(minecraft.hitResult instanceof BlockHitResult blockHit)) return false;

        BlockPos pos = blockHit.getBlockPos();
        BlockState state = minecraft.level.getBlockState(pos);

        if (state.is(BlockTags.DOORS) || state.is(BlockTags.FENCE_GATES)
                || state.is(BlockTags.BUTTONS) || state.is(BlockTags.TRAPDOORS)
                || state.is(BlockTags.SHULKER_BOXES) || state.is(BlockTags.ANVIL)
                || state.is(BlockTags.BEDS) || state.is(BlockTags.CAMPFIRES)) {
            return true;
        }

        Block block = state.getBlock();
        return block == Blocks.CHEST || block == Blocks.TRAPPED_CHEST || block == Blocks.FURNACE
                || block == Blocks.BLAST_FURNACE || block == Blocks.SMOKER || block == Blocks.BARREL
                || block == Blocks.ENDER_CHEST || block == Blocks.BREWING_STAND || block == Blocks.HOPPER
                || block == Blocks.DISPENSER || block == Blocks.DROPPER || block == Blocks.JUKEBOX
                || block == Blocks.NOTE_BLOCK || block == Blocks.LEVER || block == Blocks.REPEATER
                || block == Blocks.COMPARATOR || block == Blocks.DAYLIGHT_DETECTOR || block == Blocks.CAKE
                || block == Blocks.COMPOSTER || block == Blocks.BEEHIVE || block == Blocks.BEE_NEST
                || block == Blocks.RESPAWN_ANCHOR || block == Blocks.GRINDSTONE || block == Blocks.STONECUTTER
                || block == Blocks.CARTOGRAPHY_TABLE || block == Blocks.LOOM || block == Blocks.SMITHING_TABLE
                || block == Blocks.LECTERN || block == Blocks.BELL || block == Blocks.SWEET_BERRY_BUSH
                || block == Blocks.CRAFTING_TABLE || block == Blocks.ENCHANTING_TABLE;
    }


    /**
     * Heypixel Food swap uses the same hand swap principle as GrimAC:
     * swap to offhand -> send use packet -> release -> swap back.
     */
    private void heypixelGrimFoodSwap(InteractionHand hand, int sequence) {
        if (mc.player == null || mc.getConnection() == null) return;

        this.heypixelOriginalHand = hand;
        this.heypixelGrimFoodSwap = true;
        this.heypixelGrimSwapSent = true;

        InteractionHand swapHand = hand == InteractionHand.MAIN_HAND
                ? InteractionHand.OFF_HAND
                : InteractionHand.MAIN_HAND;

        PacketUtil.sendQueued(new ServerboundPlayerActionPacket(
                ServerboundPlayerActionPacket.Action.SWAP_ITEM_WITH_OFFHAND,
                BlockPos.ZERO,
                Direction.DOWN
        ));

        PacketUtil.sendQueued(new ServerboundUseItemPacket(swapHand, sequence));
    }

    private void heypixelGrimFoodSwapBack() {
        if (!this.heypixelGrimFoodSwap || mc.getConnection() == null) return;

        PacketUtil.sendQueued(new ServerboundPlayerActionPacket(
                ServerboundPlayerActionPacket.Action.SWAP_ITEM_WITH_OFFHAND,
                BlockPos.ZERO,
                Direction.DOWN
        ));

        this.heypixelGrimFoodSwap = false;
        this.heypixelGrimSwapSent = false;
    }

    private boolean isEatOrDrink(ItemStack stack) {
        if (stack.isEmpty()) return false;
        UseAnim anim = stack.getUseAnimation();
        Item item = stack.getItem();
        return (anim == UseAnim.EAT || anim == UseAnim.DRINK)
                || item instanceof PotionItem;
    }

    private void sendSwapOffhand() {
        PacketUtil.sendQueued(new ServerboundPlayerActionPacket(
                ServerboundPlayerActionPacket.Action.SWAP_ITEM_WITH_OFFHAND, BlockPos.ZERO, Direction.DOWN));
    }

    private void startBlink(int duration) {
        if (!this.isBlinking) this.blinkTicks = 0;
        this.isBlinking = true;
        this.blinkDuration = duration;
    }

    private void stopBlink() {
        this.flushInboundQueue();
        this.isBlinking = false;
        this.blinkTicks = 0;
        this.blinkDuration = 0;
    }

    private void finishBlink() {
        this.shouldReleaseItem = false;
        if (!this.isBlinking || !this.didSwapHand || mc.player == null) return;
        if (this.useHand != this.lastUseHand) {
            this.sendSwapOffhand();
        }
        releaseItemSent = true;
        this.didSwapHand = false;
        this.timer.reset();
        this.releaseTicksRemaining = this.grimACUseItemTicks.getValue().intValue();
        this.releaseUseKey();
        PacketUtil.sendQueued(new ServerboundPlayerActionPacket(
                ServerboundPlayerActionPacket.Action.RELEASE_USE_ITEM, BlockPos.ZERO, Direction.DOWN));
    }

    private void startUseItem(InteractionHand hand, int count) {
        if (mc.player == null) return;
        if (isLookingAtInteractableBlock()) return;
        this.didSwapHand = true;
        this.lastUseHand = hand;
        this.swapInitSlot = mc.player.getInventory().selected;
        this.useHand = hand == InteractionHand.MAIN_HAND ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
        this.sendSwapOffhand();
        if (count > 0) {
            PacketUtil.sendQueued(new ServerboundUseItemPacket(this.useHand, count));
        } else {
            PacketUtil.sendPredictiveDirect(seq -> new ServerboundUseItemPacket(this.useHand, seq));
        }
        this.startBlink(2);
    }

    private void startUseItemDefault(InteractionHand hand) {
        this.startUseItem(hand, 0);
    }

    private void releaseUseKey() {
        mc.options.keyUse.setDown(false);
        while (mc.options.keyUse.consumeClick()) {
        }
    }

    private void restoreUseKeyState() {
        if (mc == null || mc.options == null || mc.getWindow() == null) return;
        InputConstants.Key key = InputConstants.getKey(mc.options.keyUse.saveString());
        long window = mc.getWindow().getWindow();
        boolean down = key.getType() == InputConstants.Type.MOUSE
                ? GLFW.glfwGetMouseButton(window, key.getValue()) == 1
                : InputConstants.isKeyDown(window, key.getValue());
        mc.options.keyUse.setDown(down);
    }

    private void reset() {
        step = Step.NONE;
        hasSwapped = false;
        swapInArmed = false;
        noUseTicks = 0;
        resFoodSwap = false;
        cached.clear();
    }

    private void release() {
        if (step == Step.NONE && cached.isEmpty() && !hasSwapped) return;
        step = Step.NONE;
        noUseTicks = 0;
        swapInArmed = false;
        resFoodSwap = false;
        if (mc.player == null || mc.getConnection() == null) {
            cached.clear();
            inboundQueue.clear();
            return;
        }
        while (!cached.isEmpty()) {
            mc.getConnection().send(cached.poll());
        }
        flushInboundQueue();
        if (hasSwapped) {
            mc.getConnection().send(new ServerboundPlayerActionPacket(
                    ServerboundPlayerActionPacket.Action.SWAP_ITEM_WITH_OFFHAND, BlockPos.ZERO, Direction.DOWN));
            hasSwapped = false;
        }
    }

    private void queueInboundPacket(Packet<?> packet) {
        @SuppressWarnings("unchecked")
        Packet<ClientGamePacketListener> typed = (Packet<ClientGamePacketListener>) packet;
        this.inboundQueue.add(typed);
    }

    private void flushInboundQueue() {
        if (mc == null || mc.getConnection() == null) {
            this.inboundQueue.clear();
            return;
        }
        while (!this.inboundQueue.isEmpty()) {
            Packet<ClientGamePacketListener> packet = this.inboundQueue.poll();
            try {
                packet.handle(mc.getConnection());
            } catch (Exception e) {
                this.inboundQueue.clear();
                logger.error("Failed to flush packet", e);
                return;
            }
        }
    }

    private boolean isBlinkablePacket(Packet<?> packet) {
        if (packet instanceof ClientboundKeepAlivePacket || packet instanceof ClientboundPingPacket) {
            return true;
        }
        if (packet instanceof ClientboundSetEntityMotionPacket motion) {
            return mc.player != null && motion.getId() == mc.player.getId();
        }
        if (packet instanceof ClientboundContainerSetSlotPacket slotPacket) {
            return slotPacket.getSlot() == 45 || slotPacket.getContainerId() == 0;
        }
        if (packet instanceof ClientboundSetEquipmentPacket equipmentPacket) {
            for (Pair<EquipmentSlot, ItemStack> slot : equipmentPacket.getSlots()) {
                if (slot.getFirst() == EquipmentSlot.OFFHAND) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean shouldQueuePacket(Packet<?> packet) {
        if ((!this.isBlinking && !this.bowDelay) || packet == null || mc.level == null || mc.getConnection() == null) return false;
        if (packet instanceof ClientboundPlayerPositionPacket
                || packet instanceof ClientboundLoginPacket
                || packet instanceof ClientboundRespawnPacket) {
            this.stopBlink();
            return false;
        }
        if (packet instanceof ClientboundEntityEventPacket evt) {
            Entity entity = evt.getEntity(mc.level);
            if (entity != null && (entity != mc.player || evt.getEventId() != 2)) return false;
        }
        if (!this.isBlinkablePacket(packet)) return false;
        this.queueInboundPacket(packet);
        return true;
    }

    // ================================================================
    // 主事件监听 - 入口分发
    // ================================================================

    @EventTarget
    public void onSlowdown(SlowdownEvent event) {
        if (areAllOff()) return;
        if (mc.player == null || !mc.player.isUsingItem()) return;
        ItemStack stack = mc.player.getUseItem();
        if (stack.isEmpty()) return;

        // ===== Heypixel 模式（来自第2个文件的 Default 逻辑） =====
        if (this.isHeypixelMode()) {
            // 食物/药水/盾牌: 不依赖 Bow 开关, 始终取消减速
            if (this.isEatOrDrink(stack) || stack.getUseAnimation() == UseAnim.BLOCK) {
                if (this.heypixelFood.getValue() || this.heypixelPotion.getValue()) {
                    event.setSlowDown(false);
                    if (this.heypixelKeepSprinting.getValue()) {
                        mc.player.setSprinting(true);
                    }
                    return;
                }
            }
            // 弓类使用中(bowActive)由状态机取消减速
            if (this.bowActive) {
                event.setSlowDown(false);
                if (this.heypixelKeepSprinting.getValue()) {
                    mc.player.setSprinting(true);
                }
            }
            return;
        }

        // ===== LeakGrim 模式 =====
        if (this.isLeakGrimMode()) {
            if (leakNeedsRelease) return;
            UseAnim useAnim = stack.getUseAnimation();
            boolean shouldCancel = false;
            switch (useAnim) {
                case BOW:
                case CROSSBOW:
                case SPEAR:
                    if (leakBowSetting.getValue()) shouldCancel = true;
                    break;
                case EAT:
                case DRINK:
                    if (leakFoodSetting.getValue()) shouldCancel = true;
                    break;
                case BLOCK:
                    if (leakBlockSetting.getValue()) shouldCancel = true;
                    break;
                default:
                    break;
            }
            if (shouldCancel) {
                event.setSlowDown(false);
                mc.player.setSprinting(true);
            }
            return;
        }

        // ===== GrimAC 模式 =====
        if (this.isGrimACMode()) {
            if (step == Step.EATING) {
                event.setSlowDown(false);
                if (this.grimACKeepSprinting.getValue() && mc.player != null) {
                    mc.player.setSprinting(true);
                }
                return;
            }
            if (!(Boolean) this.grimACBowNoSlow.getValue()) return;
            if (!this.canSwapHands()) return;
            UseAnim anim = stack.getUseAnimation();
            if (anim == UseAnim.BOW && this.grimACCrossbowNoSlow.getValue()
                    || anim == UseAnim.CROSSBOW && this.grimACFoodNoSlow.getValue()
                    || this.isEatOrDrink(stack) && this.grimACPotionNoSlow.getValue()
                    || anim == UseAnim.BLOCK && this.grimACShieldNoSlow.getValue()) {
                event.setSlowDown(false);
            }
            if (this.grimACKeepSprinting.getValue()) {
                mc.player.setSprinting(true);
            }
            return;
        }

        // ===== Grim 模式 =====
        if (this.isGrimMode()) {
            Item item = stack.getItem();
            boolean isBow = item instanceof BowItem;
            boolean isCrossbow = item instanceof CrossbowItem;
            boolean isEdible = stack.isEdible();
            boolean isPotion = item instanceof PotionItem;
            if (isBow && this.grimBowNoSlow.getValue()) {
                event.setSlowDown(mc.player.tickCount % 3 != 0);
            } else if (isCrossbow && this.grimBowNoSlow.getValue()) {
                event.setSlowDown(mc.player.tickCount % 3 != 0);
            } else if ((isEdible && this.grimFoodNoSlow.getValue())
                    || (isPotion && this.grimFoodNoSlow.getValue())) {
                event.setSlowDown(mc.player.getUseItemRemainingTicks() >= 1
                        || mc.player.tickCount % 3 != 0);
            }
            if (this.grimKeepSprinting.getValue()) {
                mc.player.setSprinting(true);
            }
        }
    }

    @EventTarget
    public void onTick(TickEvent event) {
        if (mc.player == null) {
            this.release();
            this.stopBlink();
            if (isLeakGrimMode()) {
                leakReleaseItem();
                leakReset();
            }
            if (isHeypixelMode()) {
                heypixelRelease();
                heypixelReset();
            }
            return;
        }

        // ===== Heypixel 模式 Tick（来自第2个文件的 Default 逻辑） =====
        if (isHeypixelMode()) {
            heypixelTick();
            return;
        }

        // ===== LeakGrim 模式 Tick =====
        if (isLeakGrimMode()) {
            leakTick();
            return;
        }

        this.tickGrimACVisualLock();

        // ===== GrimAC 模式 Tick =====
        if (this.isGrimACMode()) {
            if (this.isBlinking) {
                ++this.blinkTicks;
            }
            if (this.step != Step.NONE && !this.isGrimACMode()) {
                this.release();
            }
            if (this.isGrimACMode()) {
                if (step != Step.NONE && step != Step.EATING) {
                    mc.options.keyUse.setDown(false);
                }
                if (step == Step.NONE) {
                    if (mc.player.isUsingItem()
                            && mc.options.keyUse.isDown()
                            && isUsable(mc.player.getUseItem().getUseAnimation())) {
                        if (isLookingAtInteractableBlock()) return;
                        InteractionHand hand = mc.player.getUsedItemHand();
                        if (hand == InteractionHand.OFF_HAND
                                || (hand == InteractionHand.MAIN_HAND && mc.player.getOffhandItem().isEmpty())) {
                            this.beginGrimACVisualLock(hand);
                            step = Step.ARMED;
                            mc.options.keyUse.setDown(false);
                            if (mc.player.containerMenu != mc.player.inventoryMenu) {
                                mc.getConnection().send(
                                        new ServerboundContainerClosePacket(mc.player.containerMenu.containerId));
                            }
                        }
                    }
                } else if (step == Step.EATING) {
                    if (mc.player.isUsingItem()) {
                        noUseTicks = 0;
                    } else {
                        noUseTicks++;
                        if (noUseTicks >= 5) {
                            release();
                        }
                    }
                } else {
                    noUseTicks = 0;
                }
            }
            if (this.releaseTicksRemaining > 0) {
                this.releaseUseKey();
                --this.releaseTicksRemaining;
                if (this.releaseTicksRemaining == 0) {
                    this.restoreUseKeyState();
                }
            }
            if (this.pendingUseHand != null) {
                this.startUseItem(this.pendingUseHand, this.pendingUseCount);
                this.pendingUseHand = null;
                this.pendingUseCount = 0;
            }
            if (this.isBlinking && this.blinkTicks >= this.blinkDuration) {
                this.finishBlink();
                return;
            }
            if (this.isGrimACMode() && this.grimACBowNoSlow.getValue() && this.didSwapHand && !this.isBlinking) {
                if (this.useHand != this.lastUseHand) {
                    this.sendSwapOffhand();
                }
                releaseItemSent = true;
                this.didSwapHand = false;
                this.shouldReleaseItem = false;
                this.timer.reset();
                this.releaseTicksRemaining = this.grimACUseItemTicks.getValue().intValue();
                this.releaseUseKey();
                PacketUtil.sendQueued(new ServerboundPlayerActionPacket(
                        ServerboundPlayerActionPacket.Action.RELEASE_USE_ITEM, BlockPos.ZERO, Direction.DOWN));
                return;
            }
            if (this.isGrimACMode() && this.grimACBowNoSlow.getValue() && this.shouldReleaseItem
                    && mc.player.isUsingItem() && this.canSwapHands()) {
                this.shouldReleaseItem = false;
                this.startUseItemDefault(mc.player.getUsedItemHand());
            }
            return;
        }

        // ===== Grim 模式 Tick =====
        if (this.isGrimMode()) {
            if (this.isBlinking) {
                ++this.blinkTicks;
            }
            if (this.releaseTicksRemaining > 0) {
                this.releaseUseKey();
                --this.releaseTicksRemaining;
                if (this.releaseTicksRemaining == 0) {
                    this.restoreUseKeyState();
                }
            }
            if (this.pendingUseHand != null) {
                this.startUseItem(this.pendingUseHand, this.pendingUseCount);
                this.pendingUseHand = null;
                this.pendingUseCount = 0;
            }
            if (this.isBlinking && this.blinkTicks >= this.blinkDuration) {
                this.finishBlink();
                return;
            }
            if (this.isGrimMode() && this.grimBowNoSlow.getValue() && this.didSwapHand && !this.isBlinking) {
                if (this.useHand != this.lastUseHand) {
                    this.sendSwapOffhand();
                }
                releaseItemSent = true;
                this.didSwapHand = false;
                this.shouldReleaseItem = false;
                this.timer.reset();
                this.releaseTicksRemaining = this.grimUseItemTicks.getValue().intValue();
                this.releaseUseKey();
                PacketUtil.sendQueued(new ServerboundPlayerActionPacket(
                        ServerboundPlayerActionPacket.Action.RELEASE_USE_ITEM, BlockPos.ZERO, Direction.DOWN));
                return;
            }
            if (this.isGrimMode() && this.grimBowNoSlow.getValue() && this.shouldReleaseItem
                    && mc.player.isUsingItem() && this.canSwapHands()) {
                this.shouldReleaseItem = false;
                this.startUseItemDefault(mc.player.getUsedItemHand());
            }
        }
    }

    @EventTarget
    public void onMotion(MotionEvent event) {
        if (isHeypixelMode()) return;
        if (isLeakGrimMode()) return;
        if (event.isPre() && this.isBlinking && this.blinkTicks >= this.blinkDuration && !this.didSwapHand) {
            this.stopBlink();
        }
    }

    @EventTarget
    public void onPacket(PacketEvent event) {
        if (areAllOff()) return;
        if (FastPlace.INSTANCE != null && FastPlace.INSTANCE.isEnabled()) return;
        if (mc.player == null) return;

        // ===== Heypixel 模式包处理（来自第2个文件的 Default 逻辑） =====
        if (isHeypixelMode()) {
            heypixelOnPacket(event);
            return;
        }

        // ===== LeakGrim 模式包处理 =====
        if (isLeakGrimMode()) {
            leakOnPacket(event);
            return;
        }

        // ===== GrimAC 模式包处理 =====
        if (isGrimACMode()) {
            if (event.isIncoming() && this.shouldQueuePacket(event.getPacket())) {
                event.setCancelled(true);
                return;
            }
            if (this.isGrimACMode()) {
                Packet<?> p = event.getPacket();
                if (!event.isIncoming()) {
                    if (step == Step.EATING
                            && p instanceof ServerboundPlayerActionPacket action
                            && action.getAction() == ServerboundPlayerActionPacket.Action.RELEASE_USE_ITEM) {
                        release();
                    }
                } else {
                    if (step != Step.NONE && p instanceof ClientboundPingPacket) {
                        event.setCancelled(true);
                        queueInboundPacket(p);
                        if (step == Step.ARMED && !swapInArmed) {
                            if (!this.grimACVisualLock && mc.player != null) {
                                InteractionHand originalHand = mc.player.isUsingItem()
                                        ? mc.player.getUsedItemHand()
                                        : InteractionHand.MAIN_HAND;
                                this.beginGrimACVisualLock(originalHand);
                            }
                            swapInArmed = true;
                            hasSwapped = true;
                            mc.getConnection().send(new ServerboundPlayerActionPacket(
                                    ServerboundPlayerActionPacket.Action.SWAP_ITEM_WITH_OFFHAND, BlockPos.ZERO, Direction.DOWN));
                        }
                    }
                    if (step != Step.NONE && p instanceof ClientboundSetEntityMotionPacket motion
                            && mc.player != null && motion.getId() == mc.player.getId()) {
                        event.setCancelled(true);
                        queueInboundPacket(p);
                    }
                    if (step == Step.ARMED && swapInArmed && p instanceof ClientboundContainerSetSlotPacket) {
                        swapInArmed = false;
                        mc.options.keyUse.setDown(true);
                        step = Step.EATING;
                    }
                    if (step != Step.NONE && p instanceof ClientboundPlayerPositionPacket) {
                        release();
                    }
                }
            }
            if (event.getPacket() instanceof ServerboundPlayerActionPacket actionPacket
                    && actionPacket.getAction() == ServerboundPlayerActionPacket.Action.RELEASE_USE_ITEM) {
                this.blinkTicks = Math.max(this.blinkTicks, 1);
            }
            if (event.getPacket() instanceof ServerboundUseItemOnPacket useOnPacket
                    && this.didSwapHand
                    && useOnPacket.getHand() == this.useHand
                    && mc.player.getInventory().selected == this.swapInitSlot) {
                InteractionHand other = useOnPacket.getHand() == InteractionHand.MAIN_HAND
                        ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
                PacketUtil.sendQueued(new ServerboundUseItemOnPacket(other, useOnPacket.getHitResult(), useOnPacket.getSequence()));
            }
            if (event.getPacket() instanceof ServerboundUseItemPacket usePacket) {
                if (this.didSwapHand || this.releaseTicksRemaining > 0) {
                    event.setCancelled(true);
                } else if (this.isGrimACMode() && this.grimACBowNoSlow.getValue()) {
                    if (!this.timer.hasPassed(150.0f) && this.releaseTicksRemaining <= 0) {
                        event.setCancelled(true);
                    } else if (!this.canSwapHands()) {
                        this.shouldReleaseItem = true;
                    } else {
                        ItemStack handStack = mc.player.getItemInHand(usePacket.getHand());
                        UseAnim anim = handStack.getUseAnimation();
                        if ((anim == UseAnim.BOW && this.grimACCrossbowNoSlow.getValue())
                                || (anim == UseAnim.CROSSBOW && !CrossbowItem.isCharged(handStack) && this.grimACFoodNoSlow.getValue())) {
                            this.shouldReleaseItem = false;
                            this.startBlink(1);
                        } else if (this.isEatOrDrink(handStack)) {
                            this.shouldReleaseItem = false;
                            event.setCancelled(true);
                            this.pendingUseHand = usePacket.getHand();
                            this.pendingUseCount = usePacket.getSequence();
                        }
                    }
                }
            }
            return;
        }

        // ===== Grim 模式包处理 =====
        if (isGrimMode()) {
            if (event.isIncoming() && this.shouldQueuePacket(event.getPacket())) {
                event.setCancelled(true);
                return;
            }
            if (event.getPacket() instanceof ServerboundPlayerActionPacket actionPacket
                    && actionPacket.getAction() == ServerboundPlayerActionPacket.Action.RELEASE_USE_ITEM) {
                this.blinkTicks = Math.max(this.blinkTicks, 1);
            }
            if (event.getPacket() instanceof ServerboundUseItemOnPacket useOnPacket
                    && this.didSwapHand
                    && useOnPacket.getHand() == this.useHand
                    && mc.player.getInventory().selected == this.swapInitSlot) {
                InteractionHand other = useOnPacket.getHand() == InteractionHand.MAIN_HAND
                        ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
                PacketUtil.sendQueued(new ServerboundUseItemOnPacket(other, useOnPacket.getHitResult(), useOnPacket.getSequence()));
            }
            if (event.getPacket() instanceof ServerboundUseItemPacket usePacket) {
                if (this.didSwapHand || this.releaseTicksRemaining > 0) {
                    event.setCancelled(true);
                } else if (this.isGrimMode() && this.grimBowNoSlow.getValue()) {
                    if (!this.timer.hasPassed(150.0f) && this.releaseTicksRemaining <= 0) {
                        event.setCancelled(true);
                    } else if (!this.canSwapHands()) {
                        this.shouldReleaseItem = true;
                    } else {
                        ItemStack handStack = mc.player.getItemInHand(usePacket.getHand());
                        UseAnim anim = handStack.getUseAnimation();
                        if ((anim == UseAnim.BOW && this.grimBowNoSlow.getValue())
                                || (anim == UseAnim.CROSSBOW && !CrossbowItem.isCharged(handStack) && this.grimBowNoSlow.getValue())) {
                            this.shouldReleaseItem = false;
                            this.startBlink(1);
                        } else if (this.isEatOrDrink(handStack)) {
                            this.shouldReleaseItem = false;
                            event.setCancelled(true);
                            this.pendingUseHand = usePacket.getHand();
                            this.pendingUseCount = usePacket.getSequence();
                        }
                    }
                }
            }
        }
    }

    // ================================================================
    // Heypixel 模式核心（来自第2个文件的 Default 逻辑）
    // ================================================================

    private boolean heypixelIsEnabledUse(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        UseAnim anim = stack.getUseAnimation();
        Item item = stack.getItem();
        if (anim == UseAnim.EAT && heypixelFood.getValue()) return true;
        if ((anim == UseAnim.DRINK || item instanceof PotionItem) && heypixelPotion.getValue()) return true;
        if (anim == UseAnim.BOW && heypixelBow.getValue()) return true;
        if (anim == UseAnim.CROSSBOW && heypixelCrossbow.getValue()) return true;
        if (anim == UseAnim.SPEAR && heypixelBow.getValue()) return true;
        return false;
    }

    private void heypixelReset() {
        this.step = Step.NONE;
        this.hasSwapped = false;
        this.swapInArmed = false;
        this.noUseTicks = 0;
        this.resFoodSwap = false;
        this.bowActive = false;
        this.bowDelay = false;
        this.cached.clear();
    }

    private void heypixelTick() {
        if (mc.player == null) {
            this.release();
            this.stopBlink();
            return;
        }
        if ((step != Step.NONE || this.bowActive)
                && ((Scaffold.INSTANCE != null && Scaffold.INSTANCE.isEnabled())
                || mc.player.getMainHandItem().is(Items.ENDER_PEARL))) {
            this.release();
            return;
        }
        if (this.isBlinking) {
            ++this.blinkTicks;
        }
        if (this.bowActive && (mc.player.getUseItem().isEmpty()
                || !this.isBowLike(mc.player.getUseItem().getUseAnimation()))) {
            this.bowActive = false;
            this.bowDelay = false;
            this.flushInboundQueue();
        }
        if (step != Step.NONE && step != Step.EATING) {
            mc.options.keyUse.setDown(false);
        }

        if (step == Step.NONE) {
            if (mc.player.isUsingItem()
                    && mc.options.keyUse.isDown()
                    && isUsable(mc.player.getUseItem().getUseAnimation())) {
                if (isLookingAtInteractableBlock()) {
                    return;
                }
                InteractionHand hand = mc.player.getUsedItemHand();
                if (hand == InteractionHand.OFF_HAND
                        || (hand == InteractionHand.MAIN_HAND && mc.player.getOffhandItem().isEmpty())) {
                    step = Step.ARMED;
                    mc.options.keyUse.setDown(false);
                    if (mc.player.containerMenu != mc.player.inventoryMenu) {
                        mc.getConnection().send(
                                new ServerboundContainerClosePacket(mc.player.containerMenu.containerId));
                    }
                }
            }
        } else if (step == Step.EATING) {
            if (mc.player.isUsingItem()) {
                noUseTicks = 0;
            } else {
                noUseTicks++;
                if (noUseTicks >= 5) {
                    release();
                }
            }
        } else {
            noUseTicks = 0;
        }
        if (this.releaseTicksRemaining > 0) {
            this.releaseUseKey();
            --this.releaseTicksRemaining;
            if (this.releaseTicksRemaining == 0) {
                this.restoreUseKeyState();
            }
        }
        if (this.pendingUseHand != null) {
            this.startUseItem(this.pendingUseHand, this.pendingUseCount);
            this.pendingUseHand = null;
            this.pendingUseCount = 0;
        }
        if (this.isBlinking && this.blinkTicks >= this.blinkDuration) {
            this.finishBlink();
            return;
        }
        if (this.didSwapHand && !this.isBlinking) {
            if (this.useHand != this.lastUseHand) {
                this.sendSwapOffhand();
            }
            releaseItemSent = true;
            this.didSwapHand = false;
            this.shouldReleaseItem = false;
            this.releaseTicksRemaining = 1;
            this.releaseUseKey();
            PacketUtil.sendQueued(new ServerboundPlayerActionPacket(
                    ServerboundPlayerActionPacket.Action.RELEASE_USE_ITEM, BlockPos.ZERO, Direction.DOWN));
            return;
        }
        if (this.shouldReleaseItem
                && mc.player.isUsingItem() && this.canSwapHands()) {
            this.shouldReleaseItem = false;
            this.startUseItemDefault(mc.player.getUsedItemHand());
        }
    }

    private void heypixelOnPacket(PacketEvent event) {
        if (mc.player == null) {
            return;
        }
        if (event.isIncoming() && this.shouldQueuePacket(event.getPacket())) {
            event.setCancelled(true);
            return;
        }
        Packet<?> p = event.getPacket();

        if (!event.isIncoming()) {
            if (p instanceof ServerboundPlayerActionPacket action
                    && action.getAction() == ServerboundPlayerActionPacket.Action.RELEASE_USE_ITEM) {
                if (step == Step.EATING) {
                    release();
                }
                if (this.bowActive) {
                    this.bowActive = false;
                    this.bowDelay = false;
                    this.flushInboundQueue();
                }
            }
        } else {
            if (step != Step.NONE && p instanceof ClientboundPingPacket) {
                event.setCancelled(true);
                queueInboundPacket(p);
                if (step == Step.ARMED && !swapInArmed) {
                    swapInArmed = true;
                    hasSwapped = true;
                    mc.getConnection().send(new ServerboundPlayerActionPacket(
                            ServerboundPlayerActionPacket.Action.SWAP_ITEM_WITH_OFFHAND, BlockPos.ZERO, Direction.DOWN));
                }
            }
            if (step != Step.NONE && p instanceof ClientboundSetEntityMotionPacket motion
                    && mc.player != null && motion.getId() == mc.player.getId()) {
                event.setCancelled(true);
                queueInboundPacket(p);
            }
            if (step == Step.ARMED && swapInArmed && p instanceof ClientboundContainerSetSlotPacket) {
                swapInArmed = false;
                mc.options.keyUse.setDown(true);
                step = Step.EATING;
            }
            if (step != Step.NONE && p instanceof ClientboundPlayerPositionPacket) {
                release();
            }
        }
        if (event.getPacket() instanceof ServerboundPlayerActionPacket actionPacket
                && actionPacket.getAction() == ServerboundPlayerActionPacket.Action.RELEASE_USE_ITEM) {
            this.blinkTicks = Math.max(this.blinkTicks, 1);
        }
        if (event.getPacket() instanceof ServerboundUseItemOnPacket useOnPacket
                && this.didSwapHand
                && useOnPacket.getHand() == this.useHand
                && mc.player.getInventory().selected == this.swapInitSlot) {
            InteractionHand other = useOnPacket.getHand() == InteractionHand.MAIN_HAND
                    ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
            PacketUtil.sendQueued(new ServerboundUseItemOnPacket(other, useOnPacket.getHitResult(), useOnPacket.getSequence()));
        }
        if (event.getPacket() instanceof ServerboundUseItemPacket usePacket) {
            if (this.didSwapHand || this.releaseTicksRemaining > 0) {
                event.setCancelled(true);
            } else {
                ItemStack handStack = mc.player.getItemInHand(usePacket.getHand());
                if (this.shouldResSwapFood(usePacket.getHand(), handStack)) {
                    this.resFoodSwap = true;
                    event.setCancelled(true);
                    this.sendSwapOffhand();
                    PacketUtil.sendQueued(new ServerboundUseItemPacket(InteractionHand.OFF_HAND, usePacket.getSequence()));
                    this.step = Step.EATING;
                    this.hasSwapped = true;
                    this.noUseTicks = 0;
                    return;
                }
                if (this.isBowLike(handStack.getUseAnimation())) {
                    this.handleBowUseItem(handStack);
                }
            }
        }
    }

    private void heypixelRelease() {
        this.release();
        this.stopBlink();
        this.bowActive = false;
        this.bowDelay = false;
    }

    // ================================================================
    // Heypixel 模式辅助方法（来自第2个文件）
    // ================================================================

    private boolean isBowLike(UseAnim anim) {
        return anim == UseAnim.BOW || anim == UseAnim.CROSSBOW || anim == UseAnim.SPEAR;
    }

    private boolean isStew(ItemStack stack) {
        if (stack.isEmpty()) return false;
        Item item = stack.getItem();
        return item == Items.MUSHROOM_STEW || item == Items.RABBIT_STEW
                || item == Items.BEETROOT_SOUP || item == Items.SUSPICIOUS_STEW;
    }

    private void handleBowUseItem(ItemStack stack) {
        if (stack.getItem() instanceof CrossbowItem && CrossbowItem.isCharged(stack)) {
            this.shouldReleaseItem = false;
            return;
        }
        if (this.isStew(stack)) {
            this.shouldReleaseItem = false;
            return;
        }
        if (this.isLookingAtInteractableBlock()) {
            this.shouldReleaseItem = false;
            return;
        }
        this.shouldReleaseItem = false;
        this.bowActive = true;
        this.bowDelay = true;
    }

    private boolean shouldResSwapFood(InteractionHand hand, ItemStack stack) {
        if (hand != InteractionHand.MAIN_HAND) return false;
        if (this.resFoodSwap) return false;
        if (stack.isEmpty() || !this.isEatOrDrink(stack)) return false;
        UseAnim anim = stack.getUseAnimation();
        if (anim == UseAnim.BLOCK || this.isBowLike(anim)) return false;
        if (this.isStew(stack)) return false;
        if (this.isLookingAtInteractableBlock()) return false;
        if (!mc.player.getOffhandItem().isEmpty()) return false;
        return this.canSwapHands();
    }

    // ===== LeakGrim 模式包处理 =====
    private void leakOnPacket(PacketEvent event) {
        if (mc.player == null) return;

        Packet<?> packet = event.getPacket();

        if (packet instanceof ServerboundMovePlayerPacket) {
            if (leakJustUsed) return;
            if (leakSendingPackets || leakPendingUse) {
                event.setCancelled(true);
                leakJustUsed = true;
                try {
                    PacketUtil.sendQueued((Packet<ServerGamePacketListener>) packet);
                } finally {
                    leakJustUsed = false;
                }
                if (leakSendingPackets) {
                    leakReleaseItem();
                    leakSendingPackets = false;
                }
                if (leakPendingUse) {
                    leakPerformGrimSwap(leakPendingHand, leakUseSequence);
                    leakPendingUse = false;
                }
                return;
            }
        }

        if (packet instanceof ServerboundPlayerActionPacket action) {
            if (action.getAction() == ServerboundPlayerActionPacket.Action.RELEASE_USE_ITEM) {
                leakScheduleRelease();
            }
        }

        if (packet instanceof ServerboundUseItemPacket useItem) {
            ItemStack heldItem = mc.player.getItemInHand(useItem.getHand());
            int sequence = useItem.getSequence();

            if (leakIsCrossbowCharged(heldItem)) {
                leakNeedsRelease = false;
                return;
            }

            UseAnim useAnim = heldItem.getUseAnimation();

            if (leakPendingUse) {
                event.setCancelled(true);
                return;
            }

            if (leakIsBlinking && (useAnim == UseAnim.EAT || useAnim == UseAnim.DRINK)) {
                event.setCancelled(true);
                PacketUtil.sendQueued(new ServerboundUseItemPacket(useItem.getHand(), sequence));
                return;
            }

            if (!leakTimer.hasPassed(50L)) {
                event.setCancelled(true);
                PacketUtil.sendQueued(new ServerboundUseItemPacket(useItem.getHand(), sequence));
                return;
            }

            if (leakHasDualItems()) {
                leakNeedsRelease = true;
                return;
            }

            switch (useAnim) {
                case BOW:
                case CROSSBOW:
                case SPEAR:
                    if ((Boolean) this.leakBowSetting.getValue()) {
                        leakNeedsRelease = false;
                        leakDelayedPacketsMax = ((Number) leakBowTick.getValue()).intValue();
                        leakDelayedPacketsCount = 0;
                        leakDelayingPackets = true;
                    }
                    break;
                case EAT:
                case DRINK:
                    if ((Boolean) this.leakFoodSetting.getValue()) {
                        event.setCancelled(true);
                        leakSchedulePendingUse(useItem.getHand(), sequence);
                    }
                    break;
                default:
                    break;
            }
        }
    }
}