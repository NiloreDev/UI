package client.nilore.modules.impl.combat;

import java.util.Arrays;
import java.util.Optional;

import client.nilore.event.EventTarget;
import client.nilore.event.impl.DisconnectEvent;
import client.nilore.event.impl.GameTickEvent;
import client.nilore.event.impl.MotionEvent;
import client.nilore.event.impl.PreMotionEvent;
import client.nilore.event.impl.ReceivePacketEvent;
import client.nilore.event.impl.Render2DEvent;
import client.nilore.event.impl.RenderEvent;
import client.nilore.event.impl.Render3DEvent;
import client.nilore.event.impl.RotationEvent;
import client.nilore.event.impl.SprintEvent;
import client.nilore.event.impl.StrafeEvent;
import client.nilore.event.impl.TickEvent;
import client.nilore.modules.Category;
import client.nilore.modules.Module;
import client.nilore.modules.impl.combat.antikb.AntiKBMode;
import client.nilore.modules.impl.combat.antikb.NavenVelocityMode;
import client.nilore.modules.impl.movement.FireballBlink;
import client.nilore.modules.impl.movement.Grimfly;
import client.nilore.modules.impl.movement.Scaffold;
import client.nilore.settings.impl.BooleanSetting;
import client.nilore.settings.impl.ModeSetting;
import client.nilore.settings.impl.NumberSetting;
import client.nilore.utils.animation.SpringAnimation;
import client.nilore.utils.animation.Timer;
import client.nilore.utils.rotation.Rotation;

/**
 * Nilore original Velocity + bytecode-recovered Naven Velocity.
 *
 * IMPORTANT:
 * - Original Nilore modes/settings are intentionally retained.
 * - Naven is an additional top-level mode.
 * - Original default remains NoXZ to avoid silently changing existing configs.
 */
public class Velocity extends Module {
    public static Velocity INSTANCE;
    public static Rotation rotation;
    public static ModeSetting mode;
    public static Object NoXZMode;

    // ================================================================
    // Original Nilore Velocity settings 閳ワ拷 kept intact
    // ================================================================
    public final BooleanSetting rotate =
            new BooleanSetting("Rotate", false,
                    () -> mode.is("JumpReset") || mode.is("Mix"));

    public final BooleanSetting tryAttack =
            new BooleanSetting("Try Attack", false,
                    () -> mode.is("Mix"));

    public final BooleanSetting movementOverride =
            new BooleanSetting("Movement Override", false,
                    () -> mode.is("Mix"));

    public final BooleanSetting followDirection =
            new BooleanSetting("Follow Direction", false,
                    () -> mode.is("JumpReset"));

    public final NumberSetting rotateTicks =
            new NumberSetting("Rotate Ticks", 12, 3, 20, 1,
                    () -> mode.is("JumpReset")
                            && (this.rotate.getValue() || this.followDirection.getValue()));

    public final BooleanSetting autoAttackCount =
            new BooleanSetting("Auto Attack Count", true,
                    () -> mode.is("NoXZ"));

    public final NumberSetting attackAmount =
            new NumberSetting("Attack amount", 5.0, 1.0, 20.0, 1,
                    () -> mode.is("NoXZ") && !this.autoAttackCount.getValue());

    public final BooleanSetting instantAttack =
            new BooleanSetting("Instant Attack", false,
                    () -> mode.is("NoXZ"));

    public final BooleanSetting sprintStateCheck =
            new BooleanSetting("Sprint state check", true,
                    () -> mode.is("NoXZ"));

    public final BooleanSetting debugLog =
            new BooleanSetting("Debug Log", false);

    // ================================================================
    // Recovered Naven Velocity settings
    // Visible only while top-level Mode == Naven.
    // ================================================================

    public final ModeSetting navenMode =
            new ModeSetting("Naven Mode", "Reduce", "Jump Reset", "Both")
                    .withDefault("Reduce")
                    .withVisibility(() -> mode.is("Naven"));

    public final BooleanSetting navenAutoForwards =
            new BooleanSetting("Auto Forwards", true,
                    () -> mode.is("Naven"));

    // Screenshot + bytecode: these belong to Naven Jump Reset.
    public final BooleanSetting navenAutoRotation =
            new BooleanSetting("Auto Rotation", false,
                    () -> mode.is("Naven") && navenMode.is("Jump Reset"));

    public final BooleanSetting navenRequiresKillAura =
            new BooleanSetting("Requires KillAura", true,
                    () -> mode.is("Naven") && navenMode.is("Jump Reset"));

    // Screenshot + bytecode: these are Reduce-side settings.
    public final BooleanSetting navenTargetESP =
            new BooleanSetting("Target ESP", true,
                    () -> mode.is("Naven") && !navenMode.is("Jump Reset"));

    // Target ESP outline color. Visible only while Target ESP is enabled.
    public final ModeSetting navenTargetESPColor =
            new ModeSetting("Target ESP Color", "White", "Cyan", "Red", "Green", "Purple")
                    .withDefault("White")
                    .withVisibility(() -> mode.is("Naven")
                            && !navenMode.is("Jump Reset")
                            && navenTargetESP.getValue());

    // Vertical position of the ALINK CHARGING / ALINK SUCCESS HUD.
    public final NumberSetting navenAlinkY =
            new NumberSetting("ALINK Y", 18.0, 0.0, 500.0, 1.0,
                    () -> mode.is("Naven") && !navenMode.is("Jump Reset"));

    public final BooleanSetting navenDebug =
            new BooleanSetting("Debug", false,
                    () -> mode.is("Naven") && !navenMode.is("Jump Reset"));

    // Original bytecode constructor:
    // default 4.0, min 0.0, max 8.0, step 1.0
    public final NumberSetting navenMaxCounter =
            new NumberSetting("Max Counter", 4.0, 0.0, 8.0, 1.0,
                    () -> mode.is("Naven") && !navenMode.is("Jump Reset"));

    // Original bytecode constructor:
    // default 10.0, min 5.0, max 120.0, step 1.0
    public final NumberSetting navenMaxDelayTicks =
            new NumberSetting("Max Delay Ticks", 10.0, 5.0, 120.0, 1.0,
                    () -> mode.is("Naven") && !navenMode.is("Jump Reset"));

    public final ModeSetting navenReduceMode =
            new ModeSetting("Reduce Mode", "Normal", "Delay")
                    .withDefault("Normal")
                    .withVisibility(() -> mode.is("Naven") && !navenMode.is("Jump Reset"));

    private final Timer grimSyncTimer = new Timer();
    public SpringAnimation navenAirDelayToGround;
    public SpringAnimation sprintCheck;

    public Velocity() {
        super("Velocity", Category.COMBAT);
        INSTANCE = this;
        AntiKBMode.initModes();
    }

    @Override
    public String getModuleName() {
        return "Velocity";
    }

    @Override
    public String getSuffix() {
        String modeName = mode.getValue();
        if (modeName == null || modeName.isEmpty()) {
            return null;
        }

        if (mode.is("Naven")) {
            String sub = navenMode.getValue();
            if (sub != null && !sub.isEmpty()) {
                // Keep the internal mode value "Both" unchanged so the
                // existing Naven logic continues to work. Only change what
                // is shown in the module suffix.
                String displaySub = sub.equalsIgnoreCase("Both") ? "NoXZ" : sub;
                return "[" + displaySub + "]";
            }
        }

        return "[" + modeName + "]";
    }

    @Override
    public void onTick() {
    }

    @Override
    public String getDisplayName() {
        String modeName = mode.getValue();
        if (modeName == null || modeName.isEmpty()) {
            return "鎼俧Velocity";
        }

        if (mode.is("Naven")) {
            String sub = navenMode.getValue();
            if (sub != null && !sub.isEmpty()) {
                // Same display mapping as getSuffix(): Naven/Both -> NoXZ.
                String displaySub = sub.equalsIgnoreCase("Both") ? "NoXZ" : sub;
                return "鎼俧Velocity[" + displaySub + "]";
            }
        }

        return "鎼俧Velocity[" + modeName + "]";
    }

    @Override
    public void onEnable() {
        grimSyncTimer.reset();
        rotation = null;

        if (!Arrays.stream(mode.getModes()).toList().contains(mode.getValue())) {
            // Preserve Nilore's original fallback/default.
            mode.withDefault("NoXZ");
        }

        Optional<AntiKBMode> optional = AntiKBMode.findMode(mode.getValue());
        optional.ifPresent(AntiKBMode::onEnable);
    }

    @Override
    public void onDisable() {
        rotation = null;
        Optional<AntiKBMode> optional = AntiKBMode.findMode(mode.getValue());
        optional.ifPresent(AntiKBMode::onDisable);
    }

    private Optional<AntiKBMode> currentMode() {
        return AntiKBMode.findMode(mode.getValue());
    }

    private boolean conflictingMovementModuleEnabled() {
        return (FireballBlink.INSTANCE != null && FireballBlink.INSTANCE.isEnabled())
                || (Grimfly.INSTANCE != null && Grimfly.INSTANCE.isEnabled())
                || (Scaffold.INSTANCE != null && Scaffold.INSTANCE.isEnabled());
    }

    @EventTarget
    public void onGameTick(GameTickEvent event) {
        Optional<AntiKBMode> optional = currentMode();
        if (conflictingMovementModuleEnabled() || optional.isEmpty()) return;
        optional.get().onGameTick(event);
    }

    @EventTarget
    public void onPreMotion(PreMotionEvent event) {
        Optional<AntiKBMode> optional = currentMode();
        if (conflictingMovementModuleEnabled() || optional.isEmpty()) return;
        optional.get().onPreMotion(event);
    }

    @EventTarget
    public void onTick(TickEvent event) {
        Optional<AntiKBMode> optional = currentMode();
        if (conflictingMovementModuleEnabled() || optional.isEmpty()) return;
        optional.get().onTick(event);
    }

    @EventTarget
    public void onSprint(SprintEvent event) {
        Optional<AntiKBMode> optional = currentMode();
        if (conflictingMovementModuleEnabled() || optional.isEmpty()) return;
        optional.get().onSprint(event);
    }

    @EventTarget
    public void onRotation(RotationEvent event) {
        Optional<AntiKBMode> optional = currentMode();
        if (conflictingMovementModuleEnabled() || optional.isEmpty()) return;
        optional.get().onRotation(event);
    }

    @EventTarget
    public void onMotion(MotionEvent event) {
        Optional<AntiKBMode> optional = currentMode();
        if (conflictingMovementModuleEnabled() || optional.isEmpty()) return;
        optional.get().onMotion(event);
    }

    @EventTarget(value = 1)
    public void onReceivePacket(ReceivePacketEvent event) {
        Optional<AntiKBMode> optional = currentMode();
        if (conflictingMovementModuleEnabled() || optional.isEmpty()) return;
        optional.get().onReceivePacket(event);
    }

    @EventTarget
    public void onDisconnect(DisconnectEvent event) {
        Optional<AntiKBMode> optional = currentMode();
        if (optional.isEmpty()) return;
        optional.get().onDisconnect(event);
    }

    @EventTarget(value = 3)
    public void onStrafe(StrafeEvent event) {
        Optional<AntiKBMode> optional = currentMode();
        if (conflictingMovementModuleEnabled() || optional.isEmpty()) return;
        optional.get().onStrafe(event);
    }

    @EventTarget
    public void onRender(RenderEvent event) {
        Optional<AntiKBMode> optional = currentMode();
        if (conflictingMovementModuleEnabled() || optional.isEmpty()) return;
        optional.get().onRender(event);
    }

    @EventTarget
    public void onRender3D(Render3DEvent event) {
        Optional<AntiKBMode> optional = currentMode();
        if (conflictingMovementModuleEnabled() || optional.isEmpty()) return;

        // Render3DEvent is the real world-render event used by Nilore ESPs.
        // Route it directly to Naven without requiring an AntiKBMode API change.
        if (optional.get() instanceof NavenVelocityMode naven) {
            naven.onRender3D(event);
        }
    }

    @EventTarget
    public void onRender2D(Render2DEvent event) {
        Optional<AntiKBMode> optional = currentMode();
        if (conflictingMovementModuleEnabled() || optional.isEmpty()) return;
        optional.get().onRender2D(event);
    }

    static {
        // Original three options preserved in the same order.
        // Naven is appended; original default remains NoXZ.
        mode = new ModeSetting("Mode", "JumpReset", "Mix", "NoXZ", "Naven")
                .withDefault("NoXZ");
    }
}