package client.nilore.modules.impl.combat;

import java.util.Arrays;
import java.util.Optional;
import client.nilore.event.impl.DisconnectEvent;
import client.nilore.event.impl.GameTickEvent;
import client.nilore.event.impl.MotionEvent;
import client.nilore.event.impl.PreMotionEvent;
import client.nilore.event.impl.ReceivePacketEvent;
import client.nilore.event.impl.Render2DEvent;
import client.nilore.event.impl.RenderEvent;
import client.nilore.event.impl.RotationEvent;
import client.nilore.event.impl.SprintEvent;
import client.nilore.event.impl.StrafeEvent;
import client.nilore.event.impl.TickEvent;
import client.nilore.modules.Category;
import client.nilore.modules.Module;
import client.nilore.modules.impl.combat.antikb.AntiKBMode;
import client.nilore.modules.impl.movement.FireballBlink;
import client.nilore.modules.impl.movement.Grimfly;
import client.nilore.modules.impl.movement.Scaffold;
import client.nilore.settings.impl.BooleanSetting;
import client.nilore.settings.impl.ModeSetting;
import client.nilore.settings.impl.NumberSetting;
import client.nilore.utils.animation.Timer;
import client.nilore.utils.rotation.Rotation;
import client.nilore.event.EventTarget;

public class Velocity extends Module {
    public static Velocity INSTANCE;
    public static Rotation rotation;
    public static ModeSetting mode;
    public static Object NoXZMode;

    public final BooleanSetting rotate = new BooleanSetting("Rotate", false, () -> mode.is("JumpReset") || mode.is("Mix"));
    public final BooleanSetting tryAttack = new BooleanSetting("Try Attack", false, () -> mode.is("Mix"));
    public final BooleanSetting movementOverride = new BooleanSetting("Movement Override", false, () -> mode.is("Mix"));
    public final BooleanSetting followDirection = new BooleanSetting("Follow Direction", false, () -> mode.is("JumpReset"));
    public final NumberSetting rotateTicks = new NumberSetting("Rotate Ticks", 12, 3, 20, 1, () -> mode.is("Jump Reset") && (this.rotate.getValue() != false || this.followDirection.getValue() != false));
    public final BooleanSetting autoAttackCount = new BooleanSetting("Auto Attack Count", true, () -> mode.is("NoXZ"));
    public final NumberSetting attackAmount = new NumberSetting("Attack amount", 5.0, 1.0, 20.0, 1, () -> mode.is("NoXZ") && !this.autoAttackCount.getValue());
    public final BooleanSetting instantAttack = new BooleanSetting("Instant Attack", false, () -> mode.is("NoXZ"));
    public final BooleanSetting sprintStateCheck = new BooleanSetting("Sprint state check", true, () -> mode.is("NoXZ"));
    public final BooleanSetting debugLog = new BooleanSetting("Debug Log", false);

    private final Timer grimSyncTimer = new Timer();

    public Velocity() {
        super("Velocity", Category.COMBAT);
        INSTANCE = this;
        AntiKBMode.initModes();
    }

    // ================================================================
    // getModuleName - 返回模块名称
    // ================================================================
    @Override
    public String getModuleName() {
        return "Velocity";
    }

    // ================================================================
    // getSuffix - 返回不带颜色代码的后缀（纯文本）
    // ================================================================
    @Override
    public String getSuffix() {
        String modeName = mode.getValue();
        if (modeName == null || modeName.isEmpty()) {
            return null;
        }
        // 返回不带颜色代码的后缀，颜色由 ModuleListHud 控制
        return "[" + modeName + "]";
    }

    // ================================================================
    // getDisplayName - 覆盖主题颜色，全部显示为白色
    // ================================================================
    @Override
    public String getDisplayName() {
        String modeName = mode.getValue();
        if (modeName == null || modeName.isEmpty()) {
            return "§fVelocity";
        }
        return "§fVelocity[" + modeName + "]";
    }

    @Override
    public void onEnable() {
        this.grimSyncTimer.reset();
        Optional<AntiKBMode> optional;
        rotation = null;
        if (!Arrays.stream(mode.getModes()).toList().contains(mode.getValue())) {
            mode.withDefault("NoXZ");
        }
        if ((optional = AntiKBMode.findMode(mode.getValue())).isEmpty()) {
            return;
        }
        optional.get().onEnable();
    }

    @Override
    public void onDisable() {
        rotation = null;
        Optional<AntiKBMode> optional = AntiKBMode.findMode(mode.getValue());
        if (optional.isEmpty()) {
            return;
        }
        optional.get().onDisable();
    }

    @EventTarget
    public void onGameTick(GameTickEvent gameTickEvent) {
        Optional<AntiKBMode> optional = AntiKBMode.findMode(mode.getValue());
        if (FireballBlink.INSTANCE.isEnabled() || Grimfly.INSTANCE.isEnabled() || Scaffold.INSTANCE.isEnabled() || optional.isEmpty()) {
            return;
        }
        optional.get().onGameTick(gameTickEvent);
    }

    @EventTarget
    public void onPreMotion(PreMotionEvent preMotionEvent) {
        Optional<AntiKBMode> optional = AntiKBMode.findMode(mode.getValue());
        if (FireballBlink.INSTANCE.isEnabled() || Grimfly.INSTANCE.isEnabled() || Scaffold.INSTANCE.isEnabled() || optional.isEmpty()) {
            return;
        }
        optional.get().onPreMotion(preMotionEvent);
    }

    @EventTarget
    public void onTick(TickEvent tickEvent) {
        Optional<AntiKBMode> optional = AntiKBMode.findMode(mode.getValue());
        if (FireballBlink.INSTANCE.isEnabled() || Grimfly.INSTANCE.isEnabled() || Scaffold.INSTANCE.isEnabled() || optional.isEmpty()) {
            return;
        }
        optional.get().onTick(tickEvent);
    }

    @EventTarget
    public void onSprint(SprintEvent sprintEvent) {
        Optional<AntiKBMode> optional = AntiKBMode.findMode(mode.getValue());
        if (FireballBlink.INSTANCE.isEnabled() || Grimfly.INSTANCE.isEnabled() || Scaffold.INSTANCE.isEnabled() || optional.isEmpty()) {
            return;
        }
        optional.get().onSprint(sprintEvent);
    }

    @EventTarget
    public void onRotation(RotationEvent rotationEvent) {
        Optional<AntiKBMode> optional = AntiKBMode.findMode(mode.getValue());
        if (FireballBlink.INSTANCE.isEnabled() || Grimfly.INSTANCE.isEnabled() || Scaffold.INSTANCE.isEnabled() || optional.isEmpty()) {
            return;
        }
        optional.get().onRotation(rotationEvent);
    }

    @EventTarget
    public void onMotion(MotionEvent motionEvent) {
        Optional<AntiKBMode> optional = AntiKBMode.findMode(mode.getValue());
        if (FireballBlink.INSTANCE.isEnabled() || Grimfly.INSTANCE.isEnabled() || Scaffold.INSTANCE.isEnabled() || optional.isEmpty()) {
            return;
        }
        optional.get().onMotion(motionEvent);
    }

    @EventTarget(value=1)
    public void onReceivePacket(ReceivePacketEvent receivePacketEvent) {
        Optional<AntiKBMode> optional = AntiKBMode.findMode(mode.getValue());
        if (FireballBlink.INSTANCE.isEnabled() || Grimfly.INSTANCE.isEnabled() || Scaffold.INSTANCE.isEnabled() || optional.isEmpty()) {
            return;
        }
        optional.get().onReceivePacket(receivePacketEvent);
    }

    @EventTarget
    public void onDisconnect(DisconnectEvent disconnectEvent) {
        Optional<AntiKBMode> optional = AntiKBMode.findMode(mode.getValue());
        if (optional.isEmpty()) {
            return;
        }
        optional.get().onDisconnect(disconnectEvent);
    }

    @EventTarget(value=3)
    public void onStrafe(StrafeEvent strafeEvent) {
        Optional<AntiKBMode> optional = AntiKBMode.findMode(mode.getValue());
        if (FireballBlink.INSTANCE.isEnabled() || Grimfly.INSTANCE.isEnabled() || Scaffold.INSTANCE.isEnabled() || optional.isEmpty()) {
            return;
        }
        optional.get().onStrafe(strafeEvent);
    }

    @EventTarget
    public void onRender(RenderEvent renderEvent) {
        Optional<AntiKBMode> optional = AntiKBMode.findMode(mode.getValue());
        if (FireballBlink.INSTANCE.isEnabled() || Grimfly.INSTANCE.isEnabled() || Scaffold.INSTANCE.isEnabled() || optional.isEmpty()) {
            return;
        }
        optional.get().onRender(renderEvent);
    }

    @EventTarget
    public void onRender2D(Render2DEvent render2DEvent) {
        Optional<AntiKBMode> optional = AntiKBMode.findMode(mode.getValue());
        if (FireballBlink.INSTANCE.isEnabled() || Grimfly.INSTANCE.isEnabled() || Scaffold.INSTANCE.isEnabled() || optional.isEmpty()) {
            return;
        }
        optional.get().onRender2D(render2DEvent);
    }

    static {
        mode = new ModeSetting("Mode", "JumpReset", "Mix", "NoXZ").withDefault("NoXZ");
    }
}