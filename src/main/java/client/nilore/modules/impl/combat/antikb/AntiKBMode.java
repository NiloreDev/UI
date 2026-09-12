package client.nilore.modules.impl.combat.antikb;

import java.util.HashMap;
import java.util.Optional;

import client.nilore.ClientBase;
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

/**
 * Original Nilore AntiKB mode registry + one additional Naven mode.
 */
public abstract class AntiKBMode extends ClientBase {
    public static boolean isAttacking;
    protected final String name;

    private static final HashMap<Class<? extends AntiKBMode>, AntiKBMode> modes =
            new HashMap<>();

    public AntiKBMode(String name) {
        this.name = name;
    }

    public static void initModes() {
        modes.clear();

        // Original Nilore modes — preserved.
        modes.put(JumpResetMode.class, new JumpResetMode());
        modes.put(MixMode.class, new MixMode());
        modes.put(NoXZMode.class, new NoXZMode());

        // Additional recovered Naven implementation.
        modes.put(NavenVelocityMode.class, new NavenVelocityMode());
    }

    public static Optional<AntiKBMode> findMode(String name) {
        return modes.values().stream()
                .filter(mode -> mode.name.equals(name))
                .findFirst();
    }

    public abstract void onEnable();
    public abstract void onDisable();
    public abstract String getName();

    public abstract void onRotation(RotationEvent event);
    public abstract void onReceivePacket(ReceivePacketEvent event);
    public abstract void onDisconnect(DisconnectEvent event);
    public abstract void onPreMotion(PreMotionEvent event);
    public abstract void onGameTick(GameTickEvent event);
    public abstract void onSprint(SprintEvent event);
    public abstract void onTick(TickEvent event);
    public abstract void onStrafe(StrafeEvent event);
    public abstract void onMotion(MotionEvent event);

    public void onRender(RenderEvent event) {
    }

    public void onRender2D(Render2DEvent event) {
    }

    public boolean isActive() {
        return false;
    }
}