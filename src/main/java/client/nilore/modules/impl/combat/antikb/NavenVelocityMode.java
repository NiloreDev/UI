package client.nilore.modules.impl.combat.antikb;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.awt.Color;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

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
import client.nilore.modules.impl.combat.KillAura;
import client.nilore.modules.impl.combat.Velocity;
import client.nilore.utils.rotation.Rotation;
import client.nilore.utils.render.RenderUtil;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Camera;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundAnimatePacket;
import net.minecraft.network.protocol.game.ClientboundContainerClosePacket;
import net.minecraft.network.protocol.game.ClientboundDisconnectPacket;
import net.minecraft.network.protocol.game.ClientboundHurtAnimationPacket;
import net.minecraft.network.protocol.game.ClientboundMoveEntityPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerChatPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerCombatKillPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.network.protocol.game.ClientboundSetHealthPacket;
import net.minecraft.network.protocol.game.ClientboundSetPlayerTeamPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.network.protocol.game.ClientboundSystemChatPacket;
import net.minecraft.network.protocol.game.ClientboundTeleportEntityPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * High-fidelity reconstruction of EdNaven 3.3 Velocity, mounted as Nilore Mode "Naven".
 *
 * Recovered state mapping:
 *   packetQueue        <- apc???
 *   entityPositions    <- ??hp?s?
 *   delayingPackets    <- s?jxxe?
 *   forwardState       <- pxaj
 *   attackCounter      <- xha?xj
 *   jumpPending        <- o??ch??
 *   correctionSeen     <- oa?pc
 *   delayTicks         <- ioeo
 *   jumpPendingTicks   <- xac?aoe
 *   failedRaycasts     <- ???c
 *   forwardResetAt     <- hpjs
 *   target             <- ixa
 *
 * The implementation follows the protected bytecode branch order rather than
 * inventing a simplified "delay N ticks then replay" algorithm.
 */
public final class NavenVelocityMode extends AntiKBMode {

    public static boolean handlingVelocity;
    private final Queue<Packet<?>> packetQueue = new ConcurrentLinkedQueue<>();
    private final Map<Entity, Vec3> entityPositions = new LinkedHashMap<>();

    private boolean delayingPackets;
    private boolean forwardState;
    private boolean jumpPending;
    private boolean correctionSeen;
    private boolean replaying;

    private int attackCounter;
    private int delayTicks;
    private int jumpPendingTicks;
    private int failedRaycasts;

    private long forwardResetAt = -1L;
    private Entity target;

    // Original has render interpolation/progress fields. They are kept so the
    // reconstructed state transitions match even if Nilore's renderer differs.
    private float progress = 0.0F;
    private float previousProgress = 0.0F;

    /**
     * ALINK HUD state reconstructed from the original visual behavior:
     * CHARGING while a Reduce/Both delay is active, SUCCESS briefly after
     * landing and releasing the queued packets.
     */
    private enum AlinkState {
        IDLE,
        CHARGING,
        SUCCESS
    }

    private AlinkState alinkState = AlinkState.IDLE;
    private int alinkSuccessTicks;

    // SUCCESS is intentionally short-lived, matching the toast-like original.
    private static final int ALINK_SUCCESS_DURATION_TICKS = 20;

    /**
     * Target ESP presets. The ESP is rendered in both first- and third-person.
     * The renderer also understands an optional
     * Velocity.INSTANCE.navenTargetESPColor setting (string/mode value) with
     * these names: White, Cyan, Red, Green, Purple.
     */
    private enum EspColorPreset {
        WHITE(1.00F, 1.00F, 1.00F),
        CYAN(0.20F, 0.95F, 1.00F),
        RED(1.00F, 0.25F, 0.25F),
        GREEN(0.25F, 1.00F, 0.35F),
        PURPLE(0.75F, 0.35F, 1.00F);

        final float r;
        final float g;
        final float b;

        EspColorPreset(float r, float g, float b) {
            this.r = r;
            this.g = g;
            this.b = b;
        }
    }

    // Fallbacks used when the matching Velocity settings are not present.
    // They are public setters below too, so another config layer can change
    // them without touching the packet-delay state machine.
    private int alinkHudY = 18;
    private EspColorPreset targetEspColor = EspColorPreset.WHITE;

    public NavenVelocityMode() {
        super("Naven");
    }

    @Override
    public String getName() {
        return "Naven";
    }

    @Override
    public boolean isActive() {
        return delayingPackets || attackCounter > 0 || jumpPending || forwardState;
    }

    @Override
    public void onEnable() {
        hardReset(false);
    }

    @Override
    public void onDisable() {
        resetOriginalStyle();
    }

    /**
     * Exact original reset ordering:
     * 1) schedule/replay queue if non-empty
     * 2) clear delaying/forward/jump state
     * 3) clear predicted positions and counters
     */
    private void resetOriginalStyle() {
        if (!packetQueue.isEmpty()) {
            scheduleFlush();
        }

        delayingPackets = false;
        forwardState = false;
        jumpPending = false;
        entityPositions.clear();
        forwardResetAt = -1L;
        failedRaycasts = 0;
        jumpPendingTicks = 0;
        attackCounter = 0;
        delayTicks = 0;
        correctionSeen = false;
        alinkState = AlinkState.IDLE;
        alinkSuccessTicks = 0;
        progress = 0.0F;
        previousProgress = 0.0F;
    }

    private void hardReset(boolean replayQueue) {
        if (replayQueue && !packetQueue.isEmpty()) {
            flushQueue();
        } else {
            packetQueue.clear();
        }

        delayingPackets = false;
        forwardState = false;
        jumpPending = false;
        correctionSeen = false;
        replaying = false;
        attackCounter = 0;
        delayTicks = 0;
        jumpPendingTicks = 0;
        failedRaycasts = 0;
        forwardResetAt = -1L;
        target = null;
        progress = 0.0F;
        previousProgress = 0.0F;
        alinkState = AlinkState.IDLE;
        alinkSuccessTicks = 0;
        entityPositions.clear();
    }

    private void scheduleFlush() {
        // Original MinecraftClient.execute(this::flush).
        mc.execute(this::flushQueue);
    }

    /**
     * Original replay behavior includes an important Both-only edge:
     * after each queued Velocity packet is applied, call the Velocity handler
     * again so Jump Reset is armed *after* Reduce releases.
     */
    @SuppressWarnings({"rawtypes", "unchecked"})
    private void flushQueue() {
        if (mc.getConnection() == null) {
            packetQueue.clear();
            return;
        }

        replaying = true;
        try {
            Packet packet;
            while ((packet = packetQueue.poll()) != null) {
                packet.handle(mc.getConnection());

                if (Velocity.INSTANCE.navenMode.is("Both")
                        && packet instanceof ClientboundSetEntityMotionPacket velocityPacket) {
                    handleVelocityPacket(velocityPacket);
                }
            }
        } catch (Throwable throwable) {
            throwable.printStackTrace();
        } finally {
            replaying = false;
        }
    }

    /**
     * Exact raycast predicate recovered from ecce??h():
     * current crosshair entity must be exactly the current target object.
     */
    private boolean isRaycastingTarget() {
        if (mc.player == null || target == null) return false;
        return mc.hitResult instanceof EntityHitResult hit
                && hit.getEntity() == target;
    }

    /**
     * Known portion of original xoeh?().
     *
     * Original also calls one global helper and checks one extra module class.
     * Their semantic names are still protected. We deliberately do not invent
     * replacements for those two checks.
     */
    private boolean isBlocked() {
        return mc.player == null
                || mc.player.isUsingItem()
                || correctionSeen
                || mc.getConnection() == null
                || target == null
                || unresolvedGlobalBlocker();
    }

    private boolean unresolvedGlobalBlocker() {
        // Bytecode has:
        //   helper.cha() == true
        //   OR moduleManager.get(<obfuscated class>).isEnabled()
        // Neither class has a proven semantic mapping yet.
        // Returning false preserves every *confirmed* branch without guessing.
        return false;
    }

    /**
     * This is the exact packet pass-through classifier from xaji().
     *
     * While delaying, packets returning FALSE here are queued/cancelled.
     * This list maps one-for-one to Nilore's own NoXZ Mojmap translation.
     */
    private boolean isPassThroughPacket(Packet<?> packet) {
        return packet instanceof ClientboundSetEntityMotionPacket
                || packet instanceof ClientboundSetHealthPacket
                || packet instanceof ClientboundPlayerPositionPacket
                || packet instanceof ClientboundSoundPacket
                || packet instanceof ClientboundPlayerChatPacket
                || packet instanceof ClientboundPlayerCombatKillPacket
                || packet instanceof ClientboundContainerClosePacket
                || packet instanceof ClientboundHurtAnimationPacket
                || packet instanceof ClientboundSetTitleTextPacket
                || packet instanceof ClientboundSetPlayerTeamPacket
                || packet instanceof ClientboundSystemChatPacket
                || packet instanceof ClientboundDisconnectPacket
                || packet instanceof ClientboundAnimatePacket animate
                && mc.player != null
                && animate.getId() != mc.player.getId();
    }

    /**
     * Recovered ??eco??(EntityVelocityUpdateS2CPacket).
     */
    private void handleVelocityPacket(ClientboundSetEntityMotionPacket packet) {
        if (mc.player == null || packet.getId() != mc.player.getId()) {
            return;
        }

        // Original auto-rotation branch exists ONLY in main mode Jump Reset.
        if (Velocity.INSTANCE.navenMode.is("Jump Reset") && Velocity.INSTANCE.navenAutoRotation.getValue()) {
            if (target == null) {
                float velocityYaw = (float) Math.toDegrees(
                        Math.atan2(packet.getXa() / 8000.0D,
                                -(packet.getZa() / 8000.0D)));

                setNiloreRotationCompat(velocityYaw, mc.player.getYRot());
            }

            // Exact bytecode sets pxaj only inside this branch.
            forwardState = true;
        }

        // Always armed for the local player's velocity packet.
        jumpPending = true;
        jumpPendingTicks = 0;
    }

    /**
     * Avoids assuming a specific Rotation constructor from a particular Nilore
     * commit. It populates Velocity.rotation when a common 2-number constructor
     * is present, which is how Nilore's old JumpReset path exposes rotation.
     */
    private void setNiloreRotationCompat(float yaw, float secondAngle) {
        try {
            for (Constructor<?> constructor : Rotation.class.getDeclaredConstructors()) {
                Class<?>[] p = constructor.getParameterTypes();
                if (p.length != 2) continue;

                Object a = convertNumber(yaw, p[0]);
                Object b = convertNumber(secondAngle, p[1]);
                if (a == null || b == null) continue;

                constructor.setAccessible(true);
                Velocity.rotation = (Rotation) constructor.newInstance(a, b);
                return;
            }
        } catch (Throwable ignored) {
        }
    }

    private Object convertNumber(float value, Class<?> type) {
        if (type == float.class || type == Float.class) return value;
        if (type == double.class || type == Double.class) return (double) value;
        return null;
    }

    @Override
    public void onReceivePacket(ReceivePacketEvent event) {
        if (mc.player == null || mc.level == null || replaying) return;

        Packet<?> packet = event.getPacket();

        // Exact packet-event branch: correction is only marked and allowed
        // through here; reset/flush happens through the later state checks.
        if (packet instanceof ClientboundPlayerPositionPacket) {
            correctionSeen = true;
            return;
        }

        // Jump Reset is a separate early branch in the original packet event.
        if (Velocity.INSTANCE.navenMode.is("Jump Reset")) {
            boolean killAuraRequirementFailed =
                    Velocity.INSTANCE.navenRequiresKillAura.getValue()
                            && (KillAura.INSTANCE == null || !KillAura.INSTANCE.isEnabled());

            if (correctionSeen || !mc.player.onGround() || killAuraRequirementFailed) {
                resetOriginalStyle();
                return;
            }

            if (packet instanceof ClientboundSetEntityMotionPacket velocityPacket
                    && velocityPacket.getId() == mc.player.getId()) {
                handleVelocityPacket(velocityPacket);
            }
            return;
        }

        // Reduce / Both.
        if (isBlocked()) {
            return;
        }

        if (packet instanceof ClientboundSetEntityMotionPacket velocityPacket
                && velocityPacket.getId() == mc.player.getId()) {
            if (!delayingPackets) {
                delayingPackets = true;
                delayTicks = 0;
                previousProgress = 0.0F;
                progress = 0.0F;
                alinkState = AlinkState.CHARGING;
                alinkSuccessTicks = 0;

                // Seed the target immediately so Target ESP is visible from
                // the first delayed frame instead of waiting for a move packet.
                if (target != null) {
                    entityPositions.putIfAbsent(target, target.position());
                }
            }

            event.setCancelled(true);
            packetQueue.add(packet);
            return;
        }

        // Exact original semantics:
        // pass-through packet OR not delaying => leave it alone.
        if (isPassThroughPacket(packet) || !delayingPackets) {
            return;
        }

        updatePredictedEntityPosition(packet);
        event.setCancelled(true);
        packetQueue.add(packet);
    }

    /**
     * Recovered entityPositions tracking:
     * - relative entity moves add delta/4096
     * - entity teleports replace with absolute packet position
     */
    private void updatePredictedEntityPosition(Packet<?> packet) {
        if (mc.level == null) return;

        if (packet instanceof ClientboundMoveEntityPacket move && move.hasPosition()) {
            Entity entity = move.getEntity(mc.level);
            if (entity != null) {
                Vec3 base = entityPositions.getOrDefault(entity, entity.position());
                entityPositions.put(entity, base.add(
                        move.getXa() / 4096.0D,
                        move.getYa() / 4096.0D,
                        move.getZa() / 4096.0D));
            }
            return;
        }

        if (packet instanceof ClientboundTeleportEntityPacket teleport) {
            Entity entity = mc.level.getEntity(teleport.getId());
            if (entity != null) {
                entityPositions.put(entity,
                        new Vec3(teleport.getX(), teleport.getY(), teleport.getZ()));
            }
        }
    }

    /**
     * Reduce/Both ALINK state machine.
     *
     * Reconstructed visual behavior:
     * - after the local velocity packet starts a delay, CHARGING is shown;
     * - progress is delayTicks / navenMaxDelayTicks, clamped to 100%;
     * - reaching 100% while airborne does NOT cancel the delay; it stays at
     *   100% until landing;
     * - landing on a valid KillAura/raycast target releases the queue and
     *   briefly shows ALINK SUCCESS.
     */
    @Override
    public void onTick(TickEvent event) {
        // Keep the last valid aura target for the whole ALINK delay.
        // KillAura.target can briefly become null between attack/rotation ticks;
        // clearing it here made Target ESP disappear even though the delay was active.
        Entity auraTarget = KillAura.target;
        if (!delayingPackets || auraTarget != null) {
            target = auraTarget;
        }

        if (jumpPending) {
            ++jumpPendingTicks;
        }

        if (jumpPendingTicks >= 5) {
            jumpPending = false;
        }

        if (alinkState == AlinkState.SUCCESS) {
            if (++alinkSuccessTicks >= ALINK_SUCCESS_DURATION_TICKS) {
                alinkState = AlinkState.IDLE;
                alinkSuccessTicks = 0;
                progress = 0.0F;
                previousProgress = 0.0F;
            }
        }

        // Original core immediately returns in Jump Reset mode.
        if (Velocity.INSTANCE.navenMode.is("Jump Reset")) {
            updateForwardResetTimer();
            return;
        }

        if (mc.player == null || isBlocked() || failedRaycasts >= 3) {
            resetOriginalStyle();
            return;
        }

        if (delayingPackets) {
            forwardState = true;
            attackCounter = 0;

            // Keep a baseline for ESP even if no target movement packet has
            // arrived yet. Subsequent delayed movement packets update it.
            if (target != null) {
                entityPositions.putIfAbsent(target, target.position());
            }

            ++delayTicks;
            previousProgress = progress;
            float maxDelay = Math.max(1.0F,
                    Velocity.INSTANCE.navenMaxDelayTicks.getValue().floatValue());
            progress = Math.max(0.0F, Math.min(1.0F, delayTicks / maxDelay));
            alinkState = AlinkState.CHARGING;

            /*
             * Important original-facing behavior: Max Delay controls how fast
             * CHARGING reaches 100%; it is not used here as an airborne hard
             * timeout. Once full, we continue holding packets until landing.
             */
            // Landing is the hard release gate. Once ALINK packet delay is
            // active, touching the ground releases immediately regardless of
            // the current CHARGING percentage, RayCast state, or sprint state.
            // This matches the original behavior where landing itself ends
            // the airborne delay.
            if (mc.player.onGround()) {

                attackCounter = Velocity.INSTANCE.navenMaxCounter.getValue().intValue();
                failedRaycasts = 0;
                delayingPackets = false;
                previousProgress = progress;

                // Keep the percentage reached at the moment of landing for
                // state bookkeeping; SUCCESS does not require 100% charge.
                alinkState = AlinkState.SUCCESS;
                alinkSuccessTicks = 0;
                scheduleFlush();
            }
        }

        // This is a second independent state after release.
        if (attackCounter > 0) {
            if (!isRaycastingTarget()) {
                ++failedRaycasts;
                debug("[N] Failed (RayCast) - Counter: " + attackCounter);
                return;
            }

            if (!mc.player.isSprinting()) {
                debug("[N] Failed (Sprint) - Counter: " + attackCounter);
                return;
            }

            attackTarget(target);
            --attackCounter;
        } else if (forwardState) {
            forwardResetAt = System.currentTimeMillis() + 10L;
        }

        updateForwardResetTimer();

        // Progress is already advanced explicitly during CHARGING. Keep the
        // old smoothing only outside the two visible ALINK states.
        if (alinkState == AlinkState.IDLE) {
            updateProgress();
        }
    }

    /**
     * The indy target for the attack helper was independently recovered:
     * interactionManager.attackEntity(player, target); swing MAIN_HAND.
     */
    private void attackTarget(Entity entity) {
        if (mc.player == null || mc.gameMode == null || entity == null) {
            return;
        }

        mc.gameMode.attack(mc.player, entity);
        mc.player.swing(InteractionHand.MAIN_HAND);
    }

    /**
     * Recovered move-input behavior.
     *
     * Jump Reset/Both:
     *   if forwardState + Auto Forwards => forward=1 AND strafe=0
     *
     * Reduce:
     *   if forwardState + Auto Forwards => forward=1 only
     *
     * Original also calls a movement prediction helper in the condition.
     * The proven part of that helper is approximated by movementGate().
     */
    @Override
    public void onStrafe(StrafeEvent event) {
        if (mc.player == null) return;

        boolean jumpFamily = Velocity.INSTANCE.navenMode.is("Jump Reset") || Velocity.INSTANCE.navenMode.is("Both");

        if (jumpFamily) {
            if (forwardState
                    && Velocity.INSTANCE.navenAutoForwards.getValue()
                    && (!delayingPackets || movementGate())) {
                event.setForward(1.0F);
                event.setStrafe(0.0F);
            }

            if (jumpPending && mc.player.isSprinting()) {
                // Original field_6235 > 9 is hurtTime > 9 in this version.
                if (mc.player.hurtTime > 9 || Velocity.INSTANCE.navenMode.is("Both")) {
                    // Closest Nilore/vanilla equivalent to setting the recovered
                    // movement-input event's jumping flag.
                    mc.player.input.jumping = true;
                    jumpPending = false;
                }

                if (Velocity.INSTANCE.navenMode.is("Jump Reset")) {
                    forwardResetAt = System.currentTimeMillis() + 10L;
                }
            }

            return;
        }

        // Reduce branch intentionally does NOT zero strafe in bytecode.
        if (forwardState
                && (!delayingPackets || movementGate())
                && Velocity.INSTANCE.navenAutoForwards.getValue()) {
            event.setForward(1.0F);
        }
    }

    /**
     * Recovered helper behavior:
     * - true on ground
     * - true while attackCounter != 0
     * - false while rising/falling with positive vertical motion/fallDistance
     * - final prediction probe is protected; conservative false fallback.
     */
    private boolean movementGate() {
        if (mc.player == null || mc.level == null) return false;
        if (mc.player.onGround() || attackCounter != 0) return true;
        if (mc.player.getDeltaMovement().y > 0.0D || mc.player.fallDistance > 0.0F) {
            return false;
        }

        // Protected prediction helper's last probe could not be semantically
        // named without executing native protection code.
        return false;
    }

    /**
     * Original jppp(cancellable event): cancel when attackCounter != 0.
     * The obfuscated event type is not proven to be Nilore SprintEvent, so use
     * reflection: if this event is cancellable in the local Nilore branch, the
     * exact behavior is applied without hard-coding a non-existent API.
     */
    @Override
    public void onSprint(SprintEvent event) {
        if (attackCounter == 0) return;

        try {
            Method m = event.getClass().getMethod("setCancelled", boolean.class);
            m.invoke(event, true);
        } catch (Throwable ignored) {
        }
    }

    /**
     * Original hpjs timer is serviced by another event:
     * clear forwardState when 10ms has elapsed, or early while the physical
     * forward key is held.
     */
    private void updateForwardResetTimer() {
        if (forwardResetAt == -1L) return;

        boolean timeReached = System.currentTimeMillis() >= forwardResetAt;
        boolean forwardKeyDown = mc.options != null && mc.options.keyUp.isDown();

        if (timeReached || forwardKeyDown) {
            forwardState = false;
            forwardResetAt = -1L;
        }
    }

    private void updateProgress() {
        previousProgress = progress;
        if (delayingPackets) {
            float max = Math.max(1.0F,
                    Velocity.INSTANCE.navenMaxDelayTicks.getValue().floatValue());
            progress = Math.max(0.0F, Math.min(1.0F, delayTicks / max));
        } else {
            progress *= 0.85F;
            if (progress < 0.001F) progress = 0.0F;
        }
    }

    private void debug(String message) {
        if (!Velocity.INSTANCE.navenDebug.getValue() || mc.player == null) {
            return;
        }

        mc.player.sendSystemMessage(Component.literal(message));
    }

    @Override
    public void onDisconnect(DisconnectEvent event) {
        hardReset(false);
    }

    @Override
    public void onGameTick(GameTickEvent event) {
        updateForwardResetTimer();
    }

    @Override
    public void onPreMotion(PreMotionEvent event) {
    }

    @Override
    public void onRotation(RotationEvent event) {
        // Rotation object is populated in handleVelocityPacket(). Nilore's
        // existing rotation pipeline may consume Velocity.rotation.
    }

    @Override
    public void onMotion(MotionEvent event) {
    }

    @Override
    public void onRender(RenderEvent event) {
        // Nilore's existing world ESPs (including KillAura Target ESP) render
        // from RenderEvent, not Render3DEvent. Use the exact same camera
        // transform path here so the box works in first- and third-person.
        if (!Velocity.INSTANCE.navenTargetESP.getValue()
                || !delayingPackets
                || target == null
                || mc.player == null
                || mc.level == null
                || mc.gameRenderer == null) {
            return;
        }

        PoseStack poseStack = event.poseStack();
        if (poseStack == null) {
            return;
        }

        // If no delayed move packet has arrived yet, the target's current
        // position is still the correct initial server-position baseline.
        Vec3 predicted = entityPositions.getOrDefault(target, target.position());

        // Move the entity's real bounding-box shape to the predicted/server
        // position rather than rebuilding it only from width/height.
        Vec3 currentPos = target.position();
        AABB box = target.getBoundingBox().move(
                predicted.x - currentPos.x,
                predicted.y - currentPos.y,
                predicted.z - currentPos.z
        ).inflate(0.025D);

        EspColorPreset preset = resolveTargetEspColor();
        Color color = new Color(
                Math.round(preset.r * 255.0F),
                Math.round(preset.g * 255.0F),
                Math.round(preset.b * 255.0F),
                255
        );

        poseStack.pushPose();
        try {
            // This is how KillAura's own Target ESP handles world coordinates:
            // translate the RenderEvent matrix by -camera position, then draw
            // the world-space AABB.
            Camera camera = mc.gameRenderer.getMainCamera();
            Vec3 cameraPos = camera.getPosition();
            poseStack.translate(-cameraPos.x, -cameraPos.y, -cameraPos.z);

            RenderUtil.drawColoredBox(box, poseStack, color, color);
        } finally {
            poseStack.popPose();
        }
    }

    /**
     * Kept only for source compatibility with the extra event that was added
     * during reconstruction. Actual Target ESP rendering is intentionally done
     * in RenderEvent above, matching Nilore's working KillAura renderer.
     */
    public void onRender3D(Render3DEvent event) {
        // no-op: rendering here caused event/camera mismatches in this client
    }

    @Override
    public void onRender2D(Render2DEvent event) {
        if (alinkState == AlinkState.IDLE) {
            return;
        }

        Object graphics = extractGuiGraphics(event);
        if (graphics == null || mc.font == null) {
            return;
        }

        int screenWidth = mc.getWindow().getGuiScaledWidth();
        int x = screenWidth / 2 - 55;
        int y = resolveAlinkHudY();
        int width = 110;
        int height = 18;

        // Dark translucent ALINK panel.
        guiFill(graphics, x, y, x + width, y + height, 0xB8181818);

        if (alinkState == AlinkState.CHARGING) {
            int percent = Math.max(0, Math.min(100, Math.round(progress * 100.0F)));
            String text = "Alink Charging " + percent + "%";

            // Thin progress bar at the bottom, duration controlled by Max Delay.
            int barX = x + 3;
            int barY = y + height - 3;
            int barWidth = width - 6;
            int filled = Math.round(barWidth * progress);
            guiFill(graphics, barX, barY, barX + barWidth, barY + 1, 0x70404040);
            if (filled > 0) {
                guiFill(graphics, barX, barY, barX + filled, barY + 1, 0xFFFFFFFF);
            }

            guiDrawCenteredComfortableString(graphics, text, screenWidth / 2, y + 5, 0xFFFFFFFF);
        } else {
            guiDrawCenteredComfortableString(graphics, "Alink Success", screenWidth / 2, y + 5, 0xFFFFFFFF);
        }
    }

    /**
     * Reads an optional Velocity setting named navenAlinkY. If the surrounding
     * module has not added that setting yet, the local fallback is used.
     */
    private int resolveAlinkHudY() {
        Object value = readOptionalVelocitySettingValue("navenAlinkY");
        if (value instanceof Number number) {
            return Math.max(0, number.intValue());
        }
        return Math.max(0, alinkHudY);
    }

    /**
     * Reads an optional Velocity setting named navenTargetESPColor. Supported
     * values are White, Cyan, Red, Green and Purple (case-insensitive).
     */
    private EspColorPreset resolveTargetEspColor() {
        Object value = readOptionalVelocitySettingValue("navenTargetESPColor");
        if (value != null) {
            String name = String.valueOf(value).trim().toUpperCase();
            if (name.contains("WHITE")) return EspColorPreset.WHITE;
            if (name.contains("CYAN")) return EspColorPreset.CYAN;
            if (name.contains("RED")) return EspColorPreset.RED;
            if (name.contains("GREEN")) return EspColorPreset.GREEN;
            if (name.contains("PURPLE")) return EspColorPreset.PURPLE;
        }
        return targetEspColor;
    }

    /**
     * Compatibility reader so this mode remains buildable even before the
     * optional GUI settings are added to Velocity.java. It supports common
     * setting APIs exposing getValue(), getMode() or getSelected().
     */
    private Object readOptionalVelocitySettingValue(String fieldName) {
        try {
            Field field = Velocity.INSTANCE.getClass().getField(fieldName);
            field.setAccessible(true);
            Object setting = field.get(Velocity.INSTANCE);
            if (setting == null) return null;

            for (String getter : new String[]{"getValue", "getMode", "getSelected", "get"}) {
                try {
                    Method method = setting.getClass().getMethod(getter);
                    method.setAccessible(true);
                    Object value = method.invoke(setting);
                    if (value != null) return value;
                } catch (Throwable ignored) {
                }
            }

            return setting;
        } catch (Throwable ignored) {
            return null;
        }
    }

    /** Runtime/config hooks when no dedicated Velocity setting exists yet. */
    public void setAlinkHudY(int y) {
        this.alinkHudY = Math.max(0, y);
    }

    public int getAlinkHudY() {
        return resolveAlinkHudY();
    }

    public void setTargetEspColor(String colorName) {
        if (colorName == null) return;
        String name = colorName.trim().toUpperCase();
        if (name.equals("WHITE")) targetEspColor = EspColorPreset.WHITE;
        else if (name.equals("CYAN")) targetEspColor = EspColorPreset.CYAN;
        else if (name.equals("RED")) targetEspColor = EspColorPreset.RED;
        else if (name.equals("GREEN")) targetEspColor = EspColorPreset.GREEN;
        else if (name.equals("PURPLE")) targetEspColor = EspColorPreset.PURPLE;
    }

    public String getTargetEspColor() {
        return resolveTargetEspColor().name();
    }

    /**
     * RenderEvent API names differ between Nilore branches. Reflection keeps
     * this mode source-compatible with the common getPoseStack/getMatrixStack
     * variants without inventing a hard dependency on one event revision.
     */
    private PoseStack extractPoseStack(Object event) {
        Object value = readMember(event,
                "getPoseStack", "getMatrixStack", "getStack", "getMatrices",
                "poseStack", "matrixStack", "stack", "matrices");
        return value instanceof PoseStack stack ? stack : null;
    }

    /** Same compatibility strategy for Render2DEvent's GuiGraphics/context. */
    private Object extractGuiGraphics(Object event) {
        return readMember(event,
                "getGuiGraphics", "getGraphics", "getContext", "getGuiContext",
                "guiGraphics", "graphics", "context", "guiContext");
    }

    private Object readMember(Object owner, String... names) {
        if (owner == null) return null;

        for (String name : names) {
            try {
                Method method = owner.getClass().getMethod(name);
                method.setAccessible(true);
                Object value = method.invoke(owner);
                if (value != null) return value;
            } catch (Throwable ignored) {
            }

            try {
                Field field = owner.getClass().getDeclaredField(name);
                field.setAccessible(true);
                Object value = field.get(owner);
                if (value != null) return value;
            } catch (Throwable ignored) {
            }
        }

        return null;
    }

    private void guiFill(Object graphics, int x1, int y1, int x2, int y2, int color) {
        try {
            Method fill = graphics.getClass().getMethod(
                    "fill", int.class, int.class, int.class, int.class, int.class);
            fill.invoke(graphics, x1, y1, x2, y2, color);
        } catch (Throwable ignored) {
        }
    }

    /**
     * ALINK text style: slightly smaller, lighter and without Minecraft's
     * heavy drop shadow. This gives the HUD a cleaner rounded-client look
     * closer to Nilore's module-list typography while keeping the built-in
     * font as a guaranteed fallback.
     */
    private void guiDrawCenteredComfortableString(Object graphics, String text, int centerX, int y, int color) {
        final float scale = 0.90F;
        PoseStack pose = null;

        // 1.20.1 GuiGraphics exposes pose(). Reflection keeps this compatible
        // with branches that renamed the accessor.
        Object poseValue = readMember(graphics, "pose", "getPose", "getPoseStack", "poseStack");
        if (poseValue instanceof PoseStack stack) {
            pose = stack;
        }

        if (pose != null) {
            pose.pushPose();
            try {
                pose.scale(scale, scale, 1.0F);

                int width = mc.font.width(text);
                int scaledCenterX = Math.round(centerX / scale);
                int scaledY = Math.round(y / scale);
                int x = scaledCenterX - width / 2;

                if (invokeGuiDrawString(graphics, text, x, scaledY, color, false)) {
                    return;
                }
            } finally {
                pose.popPose();
            }
        }

        // Fallback when the current GuiGraphics branch does not expose a pose.
        int width = mc.font.width(text);
        int x = centerX - width / 2;
        invokeGuiDrawString(graphics, text, x, y, color, false);
    }

    private boolean invokeGuiDrawString(Object graphics, String text, int x, int y, int color, boolean shadow) {
        // GuiGraphics#drawString(Font, String, int, int, int, boolean)
        for (Method method : graphics.getClass().getMethods()) {
            if (!method.getName().equals("drawString") || method.getParameterCount() != 6) {
                continue;
            }

            try {
                method.invoke(graphics, mc.font, text, x, y, color, shadow);
                return true;
            } catch (Throwable ignored) {
            }
        }
        return false;
    }

    public Vec3 getPredictedTargetPosition() {
        return target == null ? null : entityPositions.get(target);
    }

    public float getProgress() {
        return progress;
    }

    public float getPreviousProgress() {
        return previousProgress;
    }
}