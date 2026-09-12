package client.nilore.modules.impl.movement;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

import client.nilore.modules.impl.player.AntiVoid;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.network.protocol.game.ServerGamePacketListener;
import net.minecraft.network.protocol.game.ServerboundAcceptTeleportationPacket;
import net.minecraft.network.protocol.game.ServerboundInteractPacket;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket;
import net.minecraft.network.protocol.game.ServerboundPongPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemPacket;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.BowlFoodItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import client.nilore.ClientBase;
import client.nilore.NiloreClient;
import client.nilore.event.impl.MotionEvent;
import client.nilore.event.impl.PacketEvent;
import client.nilore.event.impl.StrafeEvent;
import client.nilore.event.impl.TickEvent;
import client.nilore.event.impl.WorldChangeEvent;
import client.nilore.modules.Category;
import client.nilore.modules.Module;
import client.nilore.settings.impl.BooleanSetting;
import client.nilore.settings.impl.ModeSetting;
import client.nilore.settings.impl.NumberSetting;
import client.nilore.utils.misc.PacketUtil;
import client.nilore.utils.misc.ReflectionUtil;
import client.nilore.utils.rotation.Rotation;
import client.nilore.utils.rotation.RotationHandler;
import client.nilore.event.EventTarget;

/**
 * Stuck 模块，支持三种模式：
 * <ul>
 *   <li><b>Normal</b> - Rise Stuck 逻辑：冻结速度、拦截 Move 包、Rotate 允许纯旋转包、Test pulse、Pulse Ticks</li>
 *   <li><b>Delay</b> - 原 Nilore Delay 逻辑：拦截 Move/Pong/UseItem/PlayerAction、capturedPacket、pendingDisable</li>
 *   <li><b>Packet</b> - 原 Nilore Packet 逻辑：Move 包注入随机旋转、周期性释放 Pong</li>
 * </ul>
 */
public class Stuck extends Module {
    public static Stuck INSTANCE;

    // ======================================================================
    // MODE
    // ======================================================================
    public final ModeSetting modeSetting = new ModeSetting(
            "Mode", "Normal", "Delay", "Packet"
    ).withDefault("Normal");

    // ======================================================================
    // NORMAL MODE SETTINGS (来自第 1 个 Stuck)
    // ======================================================================
    public final BooleanSetting rotations = new BooleanSetting(
            "Rotate", false, () -> this.modeSetting.is("Normal"));
    public final BooleanSetting test = new BooleanSetting(
            "Test", false, () -> this.modeSetting.is("Normal"));
    public final NumberSetting pulseTicks = new NumberSetting(
            "Pulse Ticks", 0, 0, 30, 1, () -> this.modeSetting.is("Normal"));

    // ======================================================================
    // NORMAL MODE STATE (来自第 1 个 Stuck)
    // ======================================================================
    private Vec3 savedMotion;
    private boolean stuck;
    private int stuckTicks;
    private int refreezeTicks;
    private float lastYaw;
    private float lastPitch;
    private float lastHealth;
    private boolean sendYaw360;

    // ======================================================================
    // DELAY / PACKET MODE STATE (来自第 2 个 Stuck)
    // ======================================================================
    private int stuckState = 0;
    private Packet<?> capturedPacket;
    private float savedYaw;
    private float savedPitch;
    private boolean pendingDisable = false;
    private final Queue<ServerboundPongPacket> pongQueue = new ConcurrentLinkedQueue<>();

    // ======================================================================
    // CONSTRUCTOR
    // ======================================================================
    public Stuck() {
        super("Stuck", Category.PLAYER);
        INSTANCE = this;
        this.registerSetting(modeSetting, rotations, test, pulseTicks);
    }

    @Override
    public String getDisplayName() {
        String modeName = modeSetting.getValue();
        if (modeName == null || modeName.isEmpty()) {
            return "§fStuck";
        }
        return "§fStuck[" + modeName + "]";
    }

    @Override
    public String getModuleName() {
        return "Stuck";
    }

    @Override
    public String getSuffix() {
        String modeName = modeSetting.getValue();
        if (modeName == null || modeName.isEmpty()) {
            return null;
        }
        return "[" + modeName + "]";
    }

    // ======================================================================
    // LIFECYCLE
    // ======================================================================
    @Override
    public void onEnable() {
        if (this.modeSetting.is("Normal")) {
            // Normal 模式初始化（来自第 1 个 Stuck）
            this.stuck = false;
            this.stuckTicks = 0;
            this.refreezeTicks = 0;
            this.sendYaw360 = false;
            if (mc.player != null) {
                this.lastHealth = mc.player.getHealth();
                this.lastYaw = mc.player.getYRot();
                this.lastPitch = mc.player.getXRot();
            }
            this.freeze();
        } else {
            // Delay / Packet 模式初始化（来自第 2 个 Stuck）
            this.stuckState = 0;
            this.capturedPacket = null;
            if (RotationHandler.targetRotation != null) {
                this.savedYaw = RotationHandler.targetRotation.getYaw();
                this.savedPitch = RotationHandler.targetRotation.getPitch();
            }
            this.pendingDisable = false;
        }
        super.onEnable();
    }

    @Override
    public void onDisable() {
        if (this.modeSetting.is("Normal")) {
            this.stuckTicks = 0;
            this.refreezeTicks = 0;
            this.sendYaw360 = false;
            this.release();
        }
        super.onDisable();
    }

    @Override
    public void setEnabled(boolean enable) {
        if (mc.player == null) {
            return;
        }
        if (enable) {
            super.setEnabled(true);
        } else if (this.modeSetting.is("Delay")) {
            // Delay 模式：延迟关闭直到 stuckState == 3
            if (this.stuckState == 3) {
                super.setEnabled(false);
            } else {
                this.pendingDisable = true;
            }
        } else {
            super.setEnabled(false);
        }
    }

    @Override
    public void onTick() {

    }

    // ======================================================================
    // STRAFE EVENT
    // ======================================================================
    @EventTarget
    public void onStrafe(StrafeEvent event) {
        if (this.modeSetting.is("Normal")) {
            // Normal 模式（来自第 1 个 Stuck）
            if (!this.stuck) {
                return;
            }
            event.setForward(0.0F);
            event.setStrafe(0.0F);
            // 不在 onStrafe 里 setSprinting(false)，避免每 tick 发 STOP_SPRINTING
            return;
        }

        // Delay / Packet 模式（来自第 2 个 Stuck）
        event.setForward(0.0f);
        event.setStrafe(0.0f);
        event.setSprinting(false);
    }

    // ======================================================================
    // MOTION EVENT
    // ======================================================================
    @EventTarget
    public void onMotion(MotionEvent motionEvent) {
        if (this.modeSetting.is("Normal")) {
            // Normal 模式（来自第 1 个 Stuck）
            if (!this.stuck || mc.player == null || motionEvent.isPre()) {
                return;
            }
            mc.player.setDeltaMovement(Vec3.ZERO);
            return;
        }

        // Delay / Packet 模式（来自第 2 个 Stuck）
        Scaffold scaffold = Scaffold.INSTANCE;
        if (!this.isAntiVoidActive() && scaffold.isEnabled()) {
            scaffold.setEnabled(false);
            return;
        }
        if (mc.player == null) {
            return;
        }
        if (motionEvent.isPost()) {
            mc.player.setDeltaMovement(0.0, 0.0, 0.0);
            if (this.stuckState == 1) {
                this.stuckState = 2;
                float currentYaw = mc.player.getYRot();
                float currentPitch = mc.player.getXRot();
                if (this.shouldSendCapturedPacket() && (this.savedYaw != currentYaw || this.savedPitch != currentPitch)) {
                    PacketUtil.sendQueued(new ServerboundMovePlayerPacket.Rot(currentYaw, currentPitch, mc.player.onGround()));
                    while (!this.pongQueue.isEmpty()) {
                        PacketUtil.sendQueued((Packet<ServerGamePacketListener>) this.pongQueue.poll());
                    }
                    this.savedYaw = currentYaw;
                    this.savedPitch = currentPitch;
                }
                PacketUtil.sendQueued((Packet<ServerGamePacketListener>) this.capturedPacket);
            } else if (!this.isAntiVoidActive() && this.modeSetting.is("Packet") && mc.player.tickCount % 10 == 0) {
                while (!this.pongQueue.isEmpty()) {
                    PacketUtil.sendQueued((Packet<ServerGamePacketListener>) this.pongQueue.poll());
                }
            }
            if (this.pendingDisable) {
                if (this.modeSetting.is("Delay")) {
                    PacketUtil.sendQueued(new ServerboundMovePlayerPacket.Pos(mc.player.getX() + 1337.0, mc.player.getY(), mc.player.getZ() + 1337.0, mc.player.onGround()));
                } else {
                    PacketUtil.sendQueued(new ServerboundPlayerCommandPacket(mc.player, ServerboundPlayerCommandPacket.Action.START_FALL_FLYING));
                }
                while (!this.pongQueue.isEmpty()) {
                    PacketUtil.sendQueued((Packet<ServerGamePacketListener>) this.pongQueue.poll());
                }
                if (this.modeSetting.is("Packet")) {
                    for (int i = 1; i <= 4; ++i) {
                        ClientBase.delayPackets.add(() -> {});
                    }
                }
                this.stuckState = 3;
                this.pendingDisable = false;
            }
        }
    }

    // ======================================================================
    // TICK EVENT
    // ======================================================================
    @EventTarget
    public void onTick(TickEvent tickEvent) {
        if (this.modeSetting.is("Normal")) {
            // Normal 模式（来自第 1 个 Stuck）
            if (mc.player == null) {
                return;
            }

            // 受伤自动关闭
            if (mc.player.getHealth() < this.lastHealth) {
                this.setEnabled(false);
                return;
            }
            this.lastHealth = mc.player.getHealth();

            // 冻结时压制冲刺
            if (this.stuck && mc.player.isSprinting()) {
                mc.options.keySprint.setDown(false);
                mc.player.setSprinting(false);
            }

            int pulse = this.pulseTicks.getValue().intValue();

            if (pulse <= 0) {
                if (!this.stuck) {
                    this.freeze();
                }
                this.stuckTicks = 0;
                this.refreezeTicks = 0;
                return;
            }

            if (this.stuck) {
                if (++this.stuckTicks >= pulse) {
                    this.release();
                    this.stuckTicks = 0;
                    this.refreezeTicks = 1;
                }
                return;
            }

            if (this.refreezeTicks > 0 && --this.refreezeTicks <= 0) {
                this.freeze();
            }
            return;
        }

        // Delay / Packet 模式（来自第 2 个 Stuck）
        if (!this.modeSetting.is("Packet")) {
            return;
        }
        Scaffold scaffold = Scaffold.INSTANCE;
        if (scaffold.isEnabled()) {
            scaffold.setEnabled(false);
            return;
        }
        if (mc.player == null) {
            return;
        }
        if (!this.isAntiVoidActive()) {
            PacketUtil.sendQueued(new ServerboundPlayerCommandPacket(mc.player, ServerboundPlayerCommandPacket.Action.START_FALL_FLYING));
        }
    }

    // ======================================================================
    // PACKET EVENT
    // ======================================================================
    @EventTarget(value = 1)
    public void onPacket(PacketEvent event) {
        if (mc.player == null) {
            return;
        }

        if (this.modeSetting.is("Normal")) {
            // Normal 模式（来自第 1 个 Stuck）
            Packet<?> packet = event.getPacket();

            // 处理 S08（服务器位置修正）
            if (event.isIncoming()) {
                if (packet instanceof ClientboundPlayerPositionPacket s08) {
                    mc.player.setPos(s08.getX(), s08.getY(), s08.getZ());
                    mc.player.setYRot(s08.getYRot());
                    mc.player.setXRot(s08.getXRot());
                    mc.player.setDeltaMovement(Vec3.ZERO);
                    mc.getConnection().send(
                            new ServerboundAcceptTeleportationPacket(s08.getId())
                    );
                    event.setCancelled(true);
                }
                return;
            }

            // Rise Test：交互/动作包触发一次 pulse
            if (this.test.getValue()
                    && this.stuck
                    && isInteractionPacket(packet)) {
                this.pulse();
            }

            if (!this.stuck || !(packet instanceof ServerboundMovePlayerPacket)) {
                return;
            }

            // 注入一个 yaw+360 的旋转包（每个冻结周期只注入一次）
            if (!this.sendYaw360 && packet instanceof ServerboundMovePlayerPacket.PosRot) {
                sendYaw360 = true;
                mc.getConnection().send(new ServerboundMovePlayerPacket.Rot(
                        mc.player.getYRot() + 360.0F,
                        mc.player.getXRot(),
                        mc.player.onGround()
                ));
            }

            if (!this.rotations.getValue()) {
                event.setCancelled(true);
                return;
            }

            // Rotate 开启时：纯旋转包放行，位置/位置+旋转包拦截
            if (!(packet instanceof ServerboundMovePlayerPacket.Rot)) {
                event.setCancelled(true);
            }
            return;
        }

        // Delay / Packet 模式（来自第 2 个 Stuck）
        Object rawPacket = event.getPacket();
        if (rawPacket instanceof ServerboundMovePlayerPacket movePacket) {
            if (this.stuckState != 1 && this.modeSetting.is("Packet")) {
                Rotation jitterRotation = new Rotation(mc.player.getYRot() + (float)(Math.random() - 0.5), mc.player.getXRot());
                ReflectionUtil.setXRot(movePacket, jitterRotation.getPitch());
                ReflectionUtil.setYRot(movePacket, jitterRotation.getYaw());
            }
            event.setCancelled(true);
        } else if (event.getPacket() instanceof ServerboundPongPacket) {
            this.pongQueue.offer((ServerboundPongPacket)event.getPacket());
            event.setCancelled(true);
        } else if (event.getPacket() instanceof ServerboundUseItemPacket || event.getPacket() instanceof ServerboundPlayerActionPacket) {
            this.capturedPacket = event.getPacket();
            this.stuckState = 1;
            event.setCancelled(true);
        } else if (event.getPacket() instanceof ClientboundPlayerPositionPacket && this.modeSetting.is("Delay")) {
            while (!this.pongQueue.isEmpty()) {
                PacketUtil.sendQueued((Packet<ServerGamePacketListener>) this.pongQueue.poll());
            }
            this.stuckState = 3;
            this.setEnabled(false);
        }
    }

    // ======================================================================
    // WORLD CHANGE
    // ======================================================================
    @EventTarget
    public void onWorldChange(WorldChangeEvent worldChangeEvent) {
        if (this.modeSetting.is("Normal")) {
            // Normal 模式（来自第 1 个 Stuck）
            this.savedMotion = null;
            this.stuck = false;
            this.stuckTicks = 0;
            this.refreezeTicks = 0;
            this.sendYaw360 = false;

            if (this.isEnabled()) {
                super.setEnabled(false);
            }
            return;
        }

        // Delay / Packet 模式（来自第 2 个 Stuck）
        this.stuckState = 3;
        this.capturedPacket = null;
        this.setEnabled(false);
    }

    // ======================================================================
    // NORMAL MODE HELPERS (来自第 1 个 Stuck)
    // ======================================================================
    private boolean isInteractionPacket(Packet<?> packet) {
        return packet instanceof ServerboundInteractPacket
                || packet instanceof ServerboundPlayerActionPacket
                || packet instanceof ServerboundUseItemOnPacket
                || packet instanceof ServerboundUseItemPacket;
    }

    private void freeze() {
        if (this.stuck || mc.player == null) {
            return;
        }
        this.savedMotion = mc.player.getDeltaMovement();
        this.stuck = true;
        this.sendYaw360 = false;
        this.lastYaw = mc.player.getYRot();
        this.lastPitch = mc.player.getXRot();
        mc.player.setDeltaMovement(Vec3.ZERO);
    }

    private void release() {
        if (!this.stuck) {
            return;
        }
        if (mc.player != null && this.savedMotion != null) {
            mc.player.setDeltaMovement(this.savedMotion);
        }
        this.stuck = false;
    }

    private void pulse() {
        this.release();
        this.stuckTicks = 0;
        this.refreezeTicks = 1;
    }

    // ======================================================================
    // DELAY / PACKET MODE HELPERS (来自第 2 个 Stuck)
    // ======================================================================
    private boolean isAntiVoidActive() {
        return NiloreClient.isReady() && AntiVoid.INSTANCE != null && AntiVoid.INSTANCE.isEnabled()
                && !mc.player.onGround() && AntiVoid.INSTANCE.bufferingPackets;
    }

    private boolean shouldSendCapturedPacket() {
        if (this.capturedPacket instanceof ServerboundUseItemPacket useItemPacket) {
            ItemStack heldStack = mc.player.getItemInHand(useItemPacket.getHand());
            return !(heldStack.getItem() instanceof BowlFoodItem) && !(heldStack.getItem() instanceof BowItem);
        }
        if (this.capturedPacket instanceof ServerboundPlayerActionPacket actionPacket) {
            return actionPacket.getAction() == ServerboundPlayerActionPacket.Action.RELEASE_USE_ITEM
                    && mc.player.getUseItem().getItem() instanceof BowItem;
        }
        return false;
    }
}